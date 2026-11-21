// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.integration;

import java.util.ArrayList;
import java.util.List;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.helpers.InterfaceLogicHost;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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

/**
 * Restores gas support for AE2's stock <b>ME Interface</b> ({@code ae2:interface}) and for every other
 * machine that reuses AE2's interface logic.
 *
 * <p>AE2's interface already accepts gases into its internal slots: {@code InterfaceLogic} builds its
 * stocking requests and storage from all registered {@code AEKeyType}s, and this mod registers the gas
 * channel together with a per-slot capacity. What AE2 does not provide is a CCB capability: its pipes
 * and pumps only talk to blocks exposing {@code GasCapabilities.BLOCK}. This class exposes the
 * interface storage of any {@link InterfaceLogicHost} as a CCB gas handler, so gas can be inserted into
 * the interface from an adjacent pipe <b>and</b> extracted from it.</p>
 *
 * <h2>Coverage</h2>
 * <p>The provider is registered for every AE2 {@link AEBaseEntityBlock}, which includes:</p>
 * <ul>
 *   <li>AE2's own ME Interface block;</li>
 *   <li>AE2's cable bus, where each {@link appeng.parts.misc.InterfacePart} is exposed on its own
 *       side;</li>
 *   <li>addon interfaces that subclass AE2's {@code InterfaceBlockEntity} / implement
 *       {@link InterfaceLogicHost}, e.g. ExtendedAE's Extended/Oversize Interface and AE2 Lightning
 *       Tech's Overloaded Interface.</li>
 * </ul>
 * <p>Machines that reimplement interface behaviour without using {@code InterfaceLogicHost} cannot be
 * detected automatically and would need an explicit compat hook.</p>
 *
 * <h2>Pressure</h2>
 * The interface is presented as a regulated vessel at CCB's reference pressure (1 atm) using
 * {@link GasPressureCompartment.PressureModel#FIXED}. The pressure is intentionally not adjustable:
 * this integration exists so that AE2 automation (crafting, level emitters, pattern providers) can move
 * gas through the stock interface, not as a replacement for the adjustable gas ME Interface.
 */
public final class AeInterfaceGasCompat {
    /** Fixed supply pressure of an interface slot: CCB's reference pressure, 100 kPa (1 atm). */
    public static final long SUPPLY_PRESSURE_PA = GasPressure.REFERENCE_PRESSURE_PA;
    /** Same pressure, in kPa. */
    public static final long SUPPLY_PRESSURE_KPA = SUPPLY_PRESSURE_PA / 1_000L;

    /** Upper sanity bound for the derived per-slot volume (normally exactly the AE slot capacity). */
    private static final long MAX_SLOT_VOLUME_LITERS = 4_000_000L;

    private static boolean registered;

    private AeInterfaceGasCompat() {
    }

    /**
     * Registers CCB's block gas capability on all AE2-ecosystem blocks. The provider returns a handler
     * only for blocks/parts that expose an {@link InterfaceLogicHost}, so non-interface AE machines keep
     * their normal capabilities.
     */
    public static synchronized void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (registered) {
            return;
        }

        int registeredBlocks = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof AEBaseEntityBlock<?>)) {
                continue;
            }
            // Never double-register when another addon already claimed this block/capability pair.
            if (event.isBlockRegistered(GasCapabilities.BLOCK, block)) {
                continue;
            }
            event.registerBlock(GasCapabilities.BLOCK, AeInterfaceGasCompat::getGasHandler, block);
            registeredBlocks++;
        }

        registered = registeredBlocks > 0;
        if (registered) {
            AppliedCraftedEnergistics.LOGGER.info(
                    "Applied Crafted Energistics: interface gas bridge active on {} AE blocks ({} kPa fixed per slot, both directions)",
                    registeredBlocks, SUPPLY_PRESSURE_KPA);
        } else {
            AppliedCraftedEnergistics.LOGGER.warn(
                    "Applied Crafted Energistics: no AE interface-compatible blocks found; CCB pipes cannot move gas through interfaces");
        }
    }

    /** True once {@link #registerCapabilities(RegisterCapabilitiesEvent)} has registered the provider. */
    public static synchronized boolean isRegistered() {
        return registered;
    }

    private static GasHandler getGasHandler(Level level, BlockPos pos, BlockState state,
            BlockEntity blockEntity, Direction side) {
        // Full-block interfaces (AE2 and addons that subclass InterfaceBlockEntity) implement
        // InterfaceLogicHost directly.
        InterfaceLogicHost host = null;
        if (blockEntity instanceof InterfaceLogicHost interfaceHost) {
            host = interfaceHost;
        } else if (blockEntity instanceof CableBusBlockEntity cableBus && side != null
                && cableBus.getPart(side) instanceof InterfaceLogicHost partHost) {
            // Interface parts (AE2's own InterfacePart, ExtendedAE's PartExInterface, ...) live on the
            // cable bus; expose the part on the queried side.
            host = partHost;
        }

        if (host == null) {
            return null;
        }

        var logic = host.getInterfaceLogic();
        if (logic == null) {
            return null;
        }
        var inventory = logic.getStorage();
        if (inventory == null || !inventory.isSupportedType(GasKeyType.TYPE)) {
            return null;
        }
        return new InterfaceGasHandler(inventory);
    }

    /** The interface as one CCB vessel per AE slot; both fill and drain are allowed. */
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

        /** Volume (litres) presented for one slot, sized so that the slot capacity equals the vessel. */
        private static long slotVolumeLiters(GenericInternalInventory inventory) {
            long capacity;
            try {
                capacity = inventory.getCapacity(GasKeyType.TYPE);
            } catch (RuntimeException ignored) {
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

        /** Fills slots in order: empty slots first, then slots already holding the same gas. */
        @Override
        public long fill(GasStack resource, GasAction action) {
            if (resource == null || resource.isEmpty()) {
                return 0;
            }
            long remaining = resource.getAmount();
            long accepted = 0;
            for (SlotCompartment compartment : compartments) {
                if (remaining <= 0) {
                    break;
                }
                long moved = compartment.fill(resource.copyWithAmount(remaining), GasAction.SIMULATE);
                if (moved > 0) {
                    accepted += moved;
                    remaining -= moved;
                }
            }
            if (accepted <= 0 || !action.execute()) {
                return accepted;
            }
            long left = accepted;
            for (SlotCompartment compartment : compartments) {
                if (left <= 0) {
                    break;
                }
                left -= compartment.fill(resource.copyWithAmount(left), GasAction.EXECUTE);
            }
            return accepted;
        }

        @Override
        public GasStack drain(GasStack resource, GasAction action) {
            if (resource == null || resource.isEmpty()) {
                return GasStack.EMPTY;
            }
            long remaining = resource.getAmount();
            long total = 0;
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

        @Override
        public GasTransferSnapshot captureTransferSnapshot() {
            final List<GenericStack> saved = new ArrayList<>(compartments.length);
            for (int slot = 0; slot < compartments.length; slot++) {
                GenericStack stack = inventory.getStack(slot);
                saved.add(stack == null ? null : new GenericStack(stack.what(), stack.amount()));
            }
            return () -> {
                for (int slot = 0; slot < saved.size(); slot++) {
                    inventory.setStack(slot, saved.get(slot));
                }
            };
        }
    }

    /** One AE interface slot seen as a fixed-pressure CCB vessel. */
    static final class SlotCompartment implements GasPressureCompartment {
        private final GenericInternalInventory inventory;
        private final int slot;
        private final long volumeLiters;

        SlotCompartment(GenericInternalInventory inventory, int slot, long volumeLiters) {
            this.inventory = inventory;
            this.slot = slot;
            this.volumeLiters = volumeLiters;
        }

        private GasKey gasKey() {
            AEKey key = inventory.getKey(slot);
            return key instanceof GasKey gasKey ? gasKey : null;
        }

        private long storedAmount() {
            return gasKey() == null ? 0 : Math.max(0, inventory.getAmount(slot));
        }

        private long capacity() {
            return slotCapacity(inventory);
        }

        private static long slotCapacity(GenericInternalInventory inventory) {
            long capacity;
            try {
                capacity = inventory.getCapacity(GasKeyType.TYPE);
            } catch (RuntimeException ignored) {
                capacity = 0;
            }
            if (capacity <= 0 || capacity > MAX_SLOT_VOLUME_LITERS) {
                capacity = ACEGasIntegration.INTERFACE_SLOT_CAPACITY;
            }
            return Math.clamp(capacity, 1L, MAX_SLOT_VOLUME_LITERS);
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
            if (stack == null || stack.isEmpty() || stack.getGasType().isEmpty()) {
                return false;
            }
            GasKey held = gasKey();
            return held == null || held.getGasId().equals(stack.getGasType().getResourceLocation());
        }

        @Override
        public long fill(GasStack resource, GasAction action) {
            if (resource == null || resource.isEmpty() || !isGasValid(resource)) {
                return 0;
            }
            long remaining = capacity() - storedAmount();
            long accepted = Math.min(remaining, resource.getAmount());
            if (accepted <= 0) {
                return 0;
            }
            Actionable mode = action.execute() ? Actionable.MODULATE : Actionable.SIMULATE;
            return inventory.insert(slot, GasKey.of(resource.getGasType()), accepted, mode);
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

        @Override
        public PredictedTransferLimits predictTransferLimits(GasStack gas, long storedAmount) {
            if (gas == null || gas.isEmpty() || gas.getGasType().isEmpty()) {
                return new PredictedTransferLimits(0, 0);
            }
            GasKey held = gasKey();
            if (held != null && !held.getGasId().equals(gas.getGasType().getResourceLocation())) {
                return new PredictedTransferLimits(0, 0);
            }
            long stored = Math.clamp(storedAmount, 0, capacity());
            long drainLimit = stored;
            long fillLimit = Math.max(0, capacity() - stored);
            return new PredictedTransferLimits(drainLimit, fillLimit);
        }

        @Override
        public GasTransferSnapshot captureTransferSnapshot() {
            final GenericStack saved = inventory.getStack(slot);
            return () -> inventory.setStack(slot, saved == null ? null : new GenericStack(saved.what(), saved.amount()));
        }
    }
}
