// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;
import net.ty.createcraftedbeginning.api.gas.GasPressure;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;
import net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler;
import net.ty.createcraftedbeginning.api.gas.handler.GasTransferSnapshot;
import net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;

/**
 * Lets Create: Crafted Beginning's own gas pipes and pumps interact with AE2's stock
 * <b>ME Interface</b> block ({@code ae2:interface}).
 *
 * <h2>The problem this solves</h2>
 * AE2's ME Interface already accepts gases into its 9 internal slots — {@code InterfaceLogic} builds
 * both its stocking requests and its storage from {@code AEKeyTypes.getAll()}, and this mod registers
 * the gas channel plus a per-slot capacity through {@link ACEGasIntegration}. What is missing is the
 * other direction: CCB's pipe solver only talks to blocks that expose {@code GasCapabilities.BLOCK},
 * and none of AE2's blocks do. Without this class, a player can put gas into an interface and nothing
 * will ever pull it out.
 *
 * <p>Registering CCB's block capability on the AE2 interface is what AppMek does for Mekanism
 * chemicals ("wrap AE2's {@code GENERIC_INTERNAL_INV} as a native handler"). A plain {@link GasHandler}
 * is not enough here, though:</p>
 *
 * <h2>Why a {@link GasStorageHandler} with real pressure compartments</h2>
 * CCB's solver discards an endpoint unless it is a {@code GasStorageHandler} whose
 * {@code getPressureCompartment(tank).getVolume() > 0}
 * ({@code gas/network/solver/endpoint/GasEndpointDiscovery.java#discoverStorageAccesses} in CCB 0.1.1:
 * {@code if (compartment.getVolume() <= 0) continue;}). A multi-tank {@code GasHandler} that is not a
 * {@code GasStorageHandler} therefore produces a pipe that visibly connects and moves nothing —
 * the canonical Addon integration bug. So every interface slot is presented as its own
 * {@link GasPressureCompartment} view:
 *
 * <ul>
 *   <li><b>model</b> — {@link GasPressureCompartment.PressureModel#FIXED}: the interface is a regulated
 *       vessel, not a rigid one. The solver treats fixed endpoints as "drain/fill up to the limit
 *       reported by the handler itself" instead of deriving a pressure from
 *       {@code amount / volume} (CCB {@code GasEndpointPlanner#createPressureEndpoint}).</li>
 *   <li><b>pressure</b> — {@value #SUPPLY_PRESSURE_KPA} kPa, i.e. CCB's reference pressure (1 atm).
 *       Deliberately hard-coded rather than taken from {@code Config.interfaceSupplyPressureKpa}: AE2's
 *       own interface must never become a high-pressure source that could push a player's sealed tank
 *       towards its rupture limit. A 1 atm source only ever flows into vessels below 1 atm, which is
 *       the least surprising behaviour and the one the AE interface's "small gas tank" role implies.</li>
 *   <li><b>volume</b> — chosen so that the compartment's {@code getMaxAmount()} equals the AE slot's
 *       gas capacity exactly: at the reference pressure 1 GU is exactly 1 litre-atmosphere, so a slot
 *       that holds {@code N} GU is presented as an {@code N} litre vessel. The two systems therefore
 *       agree on capacity, and CCB's own GUI/tooltip readouts show a consistent fill level.</li>
 * </ul>
 *
 * <h2>Drain-only, on purpose</h2>
 * {@code fill()} always returns {@code 0}, on both the handler and every compartment. Gas therefore
 * only ever flows <b>out of</b> the interface into the CCB network, never back in. That is deliberate:
 *
 * <ul>
 *   <li>AE2 owns those slots. It stocks them from the network according to the player's request and
 *       pushes anything it cannot hold back into the network ({@code InterfaceLogic#updatePlan}).</li>
 *   <li>If a CCB pipe could also insert, the two systems would fight over the same slot every tick:
 *       the pipe pushing gas in while the interface pushes the overflow back out, with the gas
 *       duplicating or vanishing whenever the two disagree about an intermediate state.</li>
 *   <li>Because we are a pure source, the interface can never be pressurised by a pipe, so no
 *       overpressure handling is required on this path at all.</li>
 * </ul>
 *
 * <p>Note that the <em>other</em> direction (AE network → CCB machine) is already covered by
 * {@link ACEGasIntegration}'s {@code StackExportStrategy}, which AE2's Pattern Provider and Export Bus
 * use to push gas into an adjacent {@code GasHandler}. This class only adds the pull path.</p>
 *
 * <h2>Why the Pattern Provider is deliberately not covered</h2>
 * In AE2 19.2.20 the only two blocks that expose {@code AECapabilities.GENERIC_INTERNAL_INV} are
 * {@code ae2:interface} and {@code ae2:pattern_provider}
 * ({@code appeng/init/InitCapabilityProviders.java:122-140}). The pattern provider's inventory is
 * {@code PatternProviderReturnInventory}, which overrides {@code canExtract()} to return {@code false}
 * with the comment <i>"Used to prevent injection through the handlers when we are pushing items out in
 * the network. Otherwise, a storage bus on the pattern provider could potentially void items."</i>
 * ({@code appeng/helpers/patternprovider/PatternProviderReturnInventory.java:38-46}). It is a transient
 * return queue, not storage, and AE2 intentionally refuses external extraction from it. Exposing it to
 * CCB would either move nothing (our extract calls would all return 0 — the exact failure mode we are
 * avoiding) or, if we bypassed the check, let a gas pipe steal gas mid-return. Pattern-provider gas
 * flow is therefore handled exclusively by the export strategy above.
 *
 * <h2>Scope: the block ME Interface only</h2>
 * The flat/cable {@code ae2:cable_interface} is a <em>part</em>. Its generic inventory is published via
 * {@code RegisterPartCapabilitiesEvent} ({@code InitCapabilityProviders.java:230-232}), i.e. at part
 * level, while CCB probes block-level capabilities and a cable position can host several parts on
 * different faces. Bridging parts would need a block-level proxy that AE2 does not provide for foreign
 * capabilities. Use the block ME Interface (or this mod's own ME Gas Interface device) where a CCB pipe
 * must connect.
 *
 * <h2>Grouping: one CCB endpoint per slot</h2>
 * {@code getCompartmentIdentity()} returns the per-slot view object, so CCB's
 * {@code GasEndpointPlanner.StorageEndpointGroup} (which groups accesses by
 * {@code compartmentIdentity == ...}) creates <b>one endpoint group per interface slot</b>. That is
 * deliberate: a group takes its "current gas" from its <em>first</em> access
 * ({@code physicalCompartment = usableAccesses.getFirst()}), so sharding by slot means every group's
 * first access is the slot that actually holds the gas. The alternative — giving all slots one shared
 * identity — would make an empty slot 0 hide gas sitting in slot 3 and nothing would ever move.
 *
 * <p>Consequences: an interface holding two different gases in two slots plans both independently in
 * the same solve (they are different {@code gasGroups}), and a slot reached from two pipe faces yields
 * two groups that both report the same drain limit. The executor drains for real, so the second drain
 * simply returns {@code 0} and the transfer is smaller than planned — never duplicated, never voided.</p>
 *
 * <h2>Wiring</h2>
 * {@link #registerCapabilities(RegisterCapabilitiesEvent)} must be added to the <b>mod</b> event bus with
 * {@code EventPriority.LOWEST} so that AE2 has already published {@code GENERIC_INTERNAL_INV}:
 *
 * <pre>{@code
 * modBus.addListener(EventPriority.LOWEST, AeInterfaceGasCompat::registerCapabilities);
 * }</pre>
 *
 * The registration itself is ordering-independent: the capability provider re-resolves the generic
 * inventory at query time and returns {@code null} when it is absent, so a wrong listener priority can
 * never register a permanently broken handler — at worst it logs a warning and works anyway.
 */
public final class AeInterfaceGasCompat {
    /** Registry id of AE2's full-block ME Interface. */
    public static final ResourceLocation AE2_INTERFACE_ID = ResourceLocation.fromNamespaceAndPath("ae2", "interface");

    /** Fixed supply pressure of an interface slot: CCB's reference pressure, 100 kPa (1 atm). */
    public static final long SUPPLY_PRESSURE_PA = GasPressure.REFERENCE_PRESSURE_PA;

    /** Same pressure, in kPa, for logging/documentation. */
    public static final long SUPPLY_PRESSURE_KPA = SUPPLY_PRESSURE_PA / 1_000L;

    /**
     * Upper sanity bound for the derived per-slot volume. The virtual volume is normally exactly the
     * AE slot capacity (64,000 L at 1 atm with the current ratio); this bound only protects against a
     * foreign inventory reporting an absurd {@code getCapacity()} such as {@link Long#MAX_VALUE}.
     */
    private static final long MAX_SLOT_VOLUME_LITERS = 4_000_000L;

    private static boolean registered;

    private AeInterfaceGasCompat() {
    }

    /**
     * Registers CCB's block gas capability on AE2's ME Interface.
     *
     * <p>Call from a {@code RegisterCapabilitiesEvent} listener added with
     * {@code EventPriority.LOWEST}. Safe to call more than once; subsequent calls are no-ops.</p>
     */
    public static synchronized void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (registered) {
            return;
        }

        Block target = findBlock(AE2_INTERFACE_ID);
        if (target == null) {
            // AE2 is a hard dependency, so this only happens if the block id ever changes.
            AppliedCraftedEnergistics.LOGGER.warn(
                    "Applied Crafted Energistics: AE2 interface block '{}' not found; CCB pipes cannot pull gas out of AE interfaces",
                    AE2_INTERFACE_ID);
            return;
        }

        // Another addon (or a previous run) already claimed this block; never double-register, NeoForge
        // rejects duplicate providers for the same block/capability pair.
        if (event.isBlockRegistered(GasCapabilities.BLOCK, target)) {
            registered = true;
            return;
        }

        if (!event.isBlockRegistered(AECapabilities.GENERIC_INTERNAL_INV, target)) {
            // Only possible when the listener was added above LOWEST priority. The provider is
            // runtime-safe, so registration still works; the warning exists to catch a wiring mistake.
            AppliedCraftedEnergistics.LOGGER.warn(
                    "Applied Crafted Energistics: ae2:interface has no generic internal inventory yet; "
                            + "add AeInterfaceGasCompat::registerCapabilities with EventPriority.LOWEST");
        }

        event.registerBlock(GasCapabilities.BLOCK, AeInterfaceGasCompat::getGasHandler, target);
        registered = true;
        AppliedCraftedEnergistics.LOGGER.info(
                "Applied Crafted Energistics: CCB gas pipes can now drain gas from AE2's ME Interface "
                        + "({} kPa fixed per slot)",
                SUPPLY_PRESSURE_KPA);
    }

    /** True once {@link #registerCapabilities(RegisterCapabilitiesEvent)} has registered the provider. */
    public static synchronized boolean isRegistered() {
        return registered;
    }

    /**
     * Capability provider body. Returns a fresh handler per query; CCB calls this once per endpoint face
     * per solver pass, so allocation is negligible and there is no stale cache to invalidate when the
     * interface block entity is replaced.
     */
    private static GasHandler getGasHandler(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity,
            Direction side) {
        GenericInternalInventory inventory = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, pos, state,
                blockEntity, side);
        if (inventory == null || !inventory.canExtract() || !inventory.isSupportedType(GasKeyType.TYPE)) {
            return null;
        }
        return new InterfaceGasHandler(inventory);
    }

    /** Finds a block by registry id without going through {@code DefaultedRegistry#get} (which returns air). */
    private static Block findBlock(ResourceLocation id) {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (id.equals(BuiltInRegistries.BLOCK.getKey(block))) {
                return block;
            }
        }
        return null;
    }

    /**
     * The interface as one CCB vessel per AE slot.
     *
     * <p>Every method either delegates to a {@link SlotCompartment} or aggregates across slots. Nothing
     * here inserts gas: see the class documentation's "Drain-only, on purpose" section.</p>
     */
    static final class InterfaceGasHandler implements GasStorageHandler {
        private final GenericInternalInventory inventory;
        private final SlotCompartment[] compartments;

        InterfaceGasHandler(GenericInternalInventory inventory) {
            this.inventory = inventory;
            long volumeLiters = slotVolumeLiters(inventory);
            int slots = Math.max(0, inventory.size());
            this.compartments = new SlotCompartment[slots];
            for (int slot = 0; slot < slots; slot++) {
                this.compartments[slot] = new SlotCompartment(inventory, slot, volumeLiters);
            }
        }

        /** Volume (litres) presented for one slot, sized so that {@code getMaxAmount() == slot capacity}. */
        private static long slotVolumeLiters(GenericInternalInventory inventory) {
            long capacity;
            try {
                capacity = inventory.getCapacity(GasKeyType.TYPE);
            } catch (RuntimeException ignored) {
                // A foreign/partial GenericInternalInventory implementation; fall through to the default.
                capacity = 0;
            }
            if (capacity <= 0 || capacity > MAX_SLOT_VOLUME_LITERS) {
                capacity = ACEGasIntegration.INTERFACE_SLOT_CAPACITY;
            }
            return Math.clamp(capacity, 1L, MAX_SLOT_VOLUME_LITERS);
        }

        @Override
        public int getTanks() {
            return compartments.length;
        }

        @Override
        public GasPressureCompartment getPressureCompartment(int tank) {
            if (tank < 0 || tank >= compartments.length) {
                throw new IndexOutOfBoundsException(
                        "AE interface adapter has " + compartments.length + " tanks, requested " + tank);
            }
            return compartments[tank];
        }

        @Override
        public GasStack getGasInTank(int tank) {
            if (tank < 0 || tank >= compartments.length) {
                return GasStack.EMPTY;
            }
            return compartments[tank].getGasStack();
        }

        @Override
        public boolean isGasValid(int tank, GasStack stack) {
            return tank >= 0 && tank < compartments.length && compartments[tank].isGasValid(stack);
        }

        /**
         * Drain-only. A CCB pipe must never be able to insert into an AE interface slot; see the class
         * documentation.
         */
        @Override
        public long fill(GasStack resource, GasAction action) {
            return 0;
        }

        /** Drains the requested gas across all slots, in slot order. */
        @Override
        public GasStack drain(GasStack resource, GasAction action) {
            if (resource == null || resource.isEmpty()) {
                return GasStack.EMPTY;
            }

            long total = 0;
            long remaining = resource.getAmount();
            for (SlotCompartment compartment : compartments) {
                if (remaining <= 0) {
                    break;
                }
                GasStack simulated = compartment.drain(remaining, GasAction.SIMULATE);
                if (simulated.isEmpty() || !GasStack.isSameGasSameComponents(simulated, resource)) {
                    continue;
                }
                total += simulated.getAmount();
                remaining -= simulated.getAmount();
            }
            if (total <= 0) {
                return GasStack.EMPTY;
            }

            if (action.execute()) {
                long left = total;
                for (SlotCompartment compartment : compartments) {
                    if (left <= 0) {
                        break;
                    }
                    left -= compartment.drain(left, GasAction.EXECUTE).getAmount();
                }
            }
            return resource.copyWithAmount(total);
        }

        /** Generic drain without a gas type: take from the first non-empty slot. */
        @Override
        public GasStack drain(long maxDrain, GasAction action) {
            if (maxDrain <= 0) {
                return GasStack.EMPTY;
            }
            for (SlotCompartment compartment : compartments) {
                if (compartment.getStoredAmount() > 0) {
                    return compartment.drain(maxDrain, action);
                }
            }
            return GasStack.EMPTY;
        }

        /**
         * Best-effort rollback of a solver step that failed after we already mutated the AE inventory.
         * CCB's solver may simulate, execute and then roll back; AE storage has no transaction of its
         * own, so the only honest option is to write the previous slot contents back.
         */
        @Override
        public GasTransferSnapshot captureTransferSnapshot() {
            final GenericStack[] saved = new GenericStack[compartments.length];
            for (int slot = 0; slot < saved.length; slot++) {
                saved[slot] = inventory.getStack(slot);
            }
            return () -> {
                for (int slot = 0; slot < saved.length; slot++) {
                    GenericStack stack = saved[slot];
                    inventory.setStack(slot, stack == null ? null : new GenericStack(stack.what(), stack.amount()));
                }
            };
        }
    }

    /**
     * One AE interface slot seen as a fixed-pressure CCB vessel.
     *
     * <p>Reads and writes go straight through {@code GenericInternalInventory}, so AE2 always sees the
     * true contents and its own listeners/network updates run as normal. Extraction is
     * {@link Actionable#SIMULATE}-pure.</p>
     */
    static final class SlotCompartment implements GasPressureCompartment {
        private final GenericInternalInventory inventory;
        private final int slot;
        private final long volumeLiters;

        SlotCompartment(GenericInternalInventory inventory, int slot, long volumeLiters) {
            this.inventory = inventory;
            this.slot = slot;
            this.volumeLiters = volumeLiters;
        }

        /** The gas key currently held in this slot, or {@code null}. */
        private GasKey gasKey() {
            AEKey key = inventory.getKey(slot);
            return key instanceof GasKey gasKey ? gasKey : null;
        }

        private long storedAmount() {
            return gasKey() == null ? 0 : Math.max(0, inventory.getAmount(slot));
        }

        @Override
        public GasStack getGasStack() {
            GasKey key = gasKey();
            if (key == null) {
                return GasStack.EMPTY;
            }
            long amount = storedAmount();
            return amount <= 0 ? GasStack.EMPTY : key.toGasStack(amount);
        }

        @Override
        public long getStoredAmount() {
            return storedAmount();
        }

        @Override
        public long getVolume() {
            return volumeLiters;
        }

        /** Fixed supply pressure: this compartment is a regulator, not a rigid tank. */
        @Override
        public long getPressurePa() {
            return SUPPLY_PRESSURE_PA;
        }

        @Override
        public long getMaxPressurePa() {
            return SUPPLY_PRESSURE_PA;
        }

        @Override
        public PressureModel getPressureModel() {
            return PressureModel.FIXED;
        }

        @Override
        public boolean isGasValid(GasStack stack) {
            return stack != null && !stack.isEmpty() && !stack.getGasType().isEmpty();
        }

        /** Drain-only; see {@link AeInterfaceGasCompat}. */
        @Override
        public long fill(GasStack resource, GasAction action) {
            return 0;
        }

        @Override
        public GasStack drain(GasStack resource, GasAction action) {
            if (resource == null || resource.isEmpty()) {
                return GasStack.EMPTY;
            }
            GasKey key = gasKey();
            if (key == null) {
                return GasStack.EMPTY;
            }
            // Same gas *and* same components, i.e. exactly what the slot holds.
            GasStack stored = key.toGasStack(1);
            if (stored.isEmpty() || !GasStack.isSameGasSameComponents(stored, resource)) {
                return GasStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public GasStack drain(long maxDrain, GasAction action) {
            GasKey key = gasKey();
            if (key == null || maxDrain <= 0) {
                return GasStack.EMPTY;
            }
            long requested = Math.min(maxDrain, storedAmount());
            if (requested <= 0) {
                return GasStack.EMPTY;
            }
            Actionable mode = action.execute() ? Actionable.MODULATE : Actionable.SIMULATE;
            long extracted = inventory.extract(slot, key, requested, mode);
            return extracted <= 0 ? GasStack.EMPTY : key.toGasStack(extracted);
        }

        @Override
        public Object getCompartmentIdentity() {
            return this;
        }

        /**
         * We can put drained gas back, so CCB may safely use its "restore after a failed pooled
         * transfer" path instead of treating the amount as unconfirmed.
         */
        @Override
        public boolean supportsExactDrainRecovery() {
            return true;
        }

        @Override
        public long restoreDrainedGas(GasStack resource, GasAction action) {
            if (resource == null || resource.isEmpty()) {
                return 0;
            }
            Actionable mode = action.execute() ? Actionable.MODULATE : Actionable.SIMULATE;
            return inventory.insert(slot, GasKey.of(resource.getGasType()), resource.getAmount(), mode);
        }

        /**
         * Exact, because the slot is drain-only and its contents are readable synchronously: whatever
         * {@code storedAmount} says can be drained, and nothing can ever be filled.
         */
        @Override
        public PredictedTransferLimits predictTransferLimits(GasStack gas, long storedAmount) {
            if (gas == null || gas.isEmpty() || !isGasValid(gas)) {
                return new PredictedTransferLimits(0, 0);
            }
            GasKey held = gasKey();
            if (held != null && !held.getGasId().equals(gas.getGasType().getResourceLocation())) {
                // A different gas occupies the slot, so this candidate cannot move in or out.
                return new PredictedTransferLimits(0, 0);
            }
            return new PredictedTransferLimits(Math.max(0L, storedAmount), 0);
        }

        @Override
        public GasTransferSnapshot captureTransferSnapshot() {
            final GenericStack saved = inventory.getStack(slot);
            return () -> inventory.setStack(slot, saved == null ? null : new GenericStack(saved.what(), saved.amount()));
        }
    }
}
