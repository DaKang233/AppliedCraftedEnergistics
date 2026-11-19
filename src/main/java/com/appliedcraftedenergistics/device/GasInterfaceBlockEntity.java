// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.appliedcraftedenergistics.Config;
import com.appliedcraftedenergistics.core.GasBufferTank;
import com.appliedcraftedenergistics.gas.GasAmounts;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.init.ACEBlockEntities;
import com.appliedcraftedenergistics.init.ACEBlocks;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;

/**
 * The ME Gas Interface block entity: an AE2 network endpoint on one side and a real Create: Crafted
 * Beginning pressure vessel on the other.
 *
 * <h2>Why a buffer tank at all</h2>
 * The ME network stores gas as a quantity with no volume and no pressure, while CCB's gas system is
 * physical: a vessel can hold {@code volume × pressure} and bursts above its rating. Every transfer
 * therefore passes through {@link GasBufferTank}, which is registered as a plain CCB
 * {@link GasHandler}. CCB pipes, pumps and gauges see a normal tank and keep enforcing their own
 * overpressure rules; the ME side only ever asks "insert/extract N GU".
 *
 * <h2>Per-tick behaviour</h2>
 * The mode ({@link GasInterfaceMode}) decides which direction gas moves; the rate limit is
 * {@code Config.interfaceTransferRate} multiplied by installed speed cards, and the energy cost is
 * {@code Config.interfaceEnergyPerKiloGasUnit} AE per 1,000 GU via AE2's
 * {@link StorageHelper#poweredInsert}/{@link StorageHelper#poweredExtraction}. If the grid has no
 * channel, no power, or cannot pay for the move, nothing is transferred and nothing is pulled out of
 * the buffer (no side effects).
 *
 * <p>All the arithmetic on amounts lives in {@link GasAmounts}; this class never invents a conversion
 * factor of its own.</p>
 */
public class GasInterfaceBlockEntity extends AENetworkedBlockEntity
        implements ServerTickingBlockEntity, IUpgradeableObject {

    /** Upgrade slots: speed cards only (see {@link #getTransferRate()}). */
    public static final int UPGRADE_SLOTS = 4;
    /** Hard cap for speed cards, mirrors AE2's own bus convention. */
    public static final int MAX_SPEED_CARDS = 4;

    /** Supply pressure adjustment granularity shown in the GUI, in kPa. */
    public static final int SUPPLY_PRESSURE_STEP_KPA = 100;
    /**
     * Maximum supply pressure a player can dial in, in kPa: CCB's safe operating limit (16 atm).
     * The vessel itself is rated higher (24 atm hard ceiling), but the interface must never be used to
     * push a pipe network past the safe range by accident.
     */
    public static final int MAX_SUPPLY_PRESSURE_KPA = (int) (GasAmounts.SAFE_PRESSURE_PA / 1_000L);

    private static final String TAG_TANK = "tank";
    private static final String TAG_MODE = "mode";
    private static final String TAG_SUPPLY_PRESSURE = "supplyPressureKpa";
    private static final String TAG_UPGRADES = "upgrades";

    /** AE2 1.21.1 action-source hint marking these transfers as machine-driven. */
    private final IActionSource actionSource = IActionSource.ofMachine(this);

    private final GasBufferTank tank;
    private final IUpgradeInventory upgrades;

    private GasInterfaceMode mode = GasInterfaceMode.IMPORT;
    /** Supply pressure setpoint in kPa; see {@link #getSupplyPressureKpa()}. */
    private int supplyPressureKpa = snapSupplyPressureKpa(Config.COMMON.interfaceSupplyPressureKpa.get());

    public GasInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ACEBlockEntities.ME_GAS_INTERFACE.get(), pos, state);

        // The vessel itself: volume from config, rated to CCB's hard ceiling (24 atm). Anything lower
        // would silently cap how much gas the interface can ever hold.
        this.tank = new GasBufferTank(Config.COMMON.interfaceBufferVolumeLiters.get(),
                GasAmounts.HARD_PRESSURE_PA);
        this.tank.setChangeListener(this::setChanged);

        this.upgrades = UpgradeInventories.forMachine(ACEBlocks.ME_GAS_INTERFACE, UPGRADE_SLOTS,
                this::onUpgradesChanged);

        getMainNode()
                .setIdlePowerUsage(Config.COMMON.interfaceIdlePower.get())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    // ---------------------------------------------------------------------------------------------
    // Public state (read by the menu, the capability registrations and CCB)
    // ---------------------------------------------------------------------------------------------

    /** The buffer vessel as CCB sees it. */
    public GasBufferTank getTank() {
        return tank;
    }

    public GasInterfaceMode getMode() {
        return mode;
    }

    public void setMode(GasInterfaceMode mode) {
        if (mode != null && this.mode != mode) {
            this.mode = mode;
            setChanged();
        }
    }

    /** Server-side helper for the GUI's mode button. */
    public void cycleMode() {
        setMode(mode.cycle());
    }

    /** The gas currently held (empty stack when the vessel is empty). */
    public GasStack getContents() {
        return tank.getContents();
    }

    public long getStoredAmount() {
        return tank.getStoredAmount();
    }

    public long getMaxAmount() {
        return tank.getMaxAmount();
    }

    public long getPressurePa() {
        return tank.getPressurePa();
    }

    /** Rated pressure of the vessel (24 atm, CCB's hard ceiling). */
    public long getMaxPressurePa() {
        return tank.getMaxPressurePa();
    }

    public long getVolume() {
        return tank.getVolume();
    }

    /** Pressure the AE side fills the buffer to; the per-block target set from the GUI. */
    public long getSupplyPressurePa() {
        return Math.clamp(getSupplyPressureKpa() * 1_000L, 0L, tank.getMaxPressurePa());
    }

    /**
     * The per-block supply pressure setpoint, in kPa. Initialised from
     * {@code Config.COMMON.interfaceSupplyPressureKpa} (which may preselect a value above the safe range,
     * up to the vessel's hard rating) and then adjustable in the GUI within {@code 0..1600 kPa} in
     * {@value #SUPPLY_PRESSURE_STEP_KPA} kPa steps.
     */
    public int getSupplyPressureKpa() {
        return supplyPressureKpa;
    }

    /**
     * Sets the supply pressure setpoint from the GUI, snapping it to the
     * {@value #SUPPLY_PRESSURE_STEP_KPA} kPa grid and clamping it to CCB's safe range. A value chosen
     * here can never push a pipe network into the unsafe range.
     */
    public void setSupplyPressureKpa(int kpa) {
        int snapped = Math.round(kpa / (float) SUPPLY_PRESSURE_STEP_KPA) * SUPPLY_PRESSURE_STEP_KPA;
        int clamped = Math.clamp(snapped, 0, MAX_SUPPLY_PRESSURE_KPA);
        if (clamped != supplyPressureKpa) {
            supplyPressureKpa = clamped;
            setChanged();
        }
    }

    /** Server-side helper for the GUI's +/- buttons (step in kPa). */
    public void adjustSupplyPressureKpa(int deltaKpa) {
        setSupplyPressureKpa(supplyPressureKpa + deltaKpa);
    }

    /** Snaps to the GUI's kPa grid and clamps to the vessel's hard pressure rating. */
    private static int snapSupplyPressureKpa(int kpa) {
        int snapped = Math.round(kpa / (float) SUPPLY_PRESSURE_STEP_KPA) * SUPPLY_PRESSURE_STEP_KPA;
        return Math.clamp(snapped, 0, (int) (GasAmounts.HARD_PRESSURE_PA / 1_000L));
    }

    /** Gas amount that corresponds to {@link #getSupplyPressurePa()} inside this vessel. */
    public long getSupplyAmount() {
        return Math.min(GasAmounts.amountFor(tank.getVolume(), getSupplyPressurePa()), tank.getMaxAmount());
    }

    /** True when the buffer is at (or within one tick of) its rated pressure ceiling. */
    public boolean isAtPressureCeiling() {
        return tank.getStoredAmount() >= tank.getMaxAmount();
    }

    /**
     * True when the active supply pressure setpoint is above CCB's safe operating limit (16 atm). The GUI
     * itself cannot dial in such a value, but the config default may prescribe one (up to the vessel's
     * hard rating), and in that case the GUI reports it so nobody pressurises a pipe network into the
     * unsafe range by accident.
     */
    public boolean isSupplyPressureAboveSafe() {
        return GasAmounts.isOverpressure(getSupplyPressurePa());
    }

    /** GU moved per tick, including installed speed cards. */
    public long getTransferRate() {
        long base = Math.max(1, Config.COMMON.interfaceTransferRate.get());
        int speedCards = Math.clamp(getInstalledUpgrades(AEItems.SPEED_CARD), 0, MAX_SPEED_CARDS);
        return base * (1 + speedCards);
    }

    /** Energy price of moving {@code amount} GU, in AE — for documentation and future logging only. */
    public double getEnergyCost(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return amount * Config.COMMON.interfaceEnergyPerKiloGasUnit.get() / GasAmounts.GU_PER_BYTE;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private void onUpgradesChanged() {
        // Only the rate depends on upgrades, so a change just needs a save; the rate is read lazily
        // every tick.
        setChanged();
    }

    // ---------------------------------------------------------------------------------------------
    // Ticking / the AE <-> buffer exchange
    // ---------------------------------------------------------------------------------------------

    @Override
    public void serverTick() {
        Level level = this.level;
        if (level == null || level.isClientSide()) {
            return;
        }
        if (mode == GasInterfaceMode.OFF) {
            return;
        }
        // isActive() is false without a channel or without power, which gives us the "do nothing when
        // the network is unpowered" rule without touching the buffer.
        if (!getMainNode().isActive()) {
            return;
        }

        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        IStorageService storageService = grid.getStorageService();
        IEnergyService energyService = grid.getEnergyService();
        if (storageService == null || energyService == null) {
            return;
        }

        switch (mode) {
            case IMPORT -> importFromBuffer(storageService.getInventory(), energyService);
            case EXPORT -> exportToBuffer(storageService.getInventory(), energyService);
            case BOTH -> {
                // Behave like a pressure regulator: above the supply pressure the surplus goes to the
                // network, below it the network tops the vessel up.
                if (tank.getPressurePa() > getSupplyPressurePa()) {
                    importFromBuffer(storageService.getInventory(), energyService);
                } else {
                    exportToBuffer(storageService.getInventory(), energyService);
                }
            }
            case OFF -> {
                // handled above
            }
        }
    }

    /**
     * Buffer -> ME network. This is the default mode: whatever CCB pushed into our vessel is collected
     * into the network. Extraction is simulated on both sides before anything is committed, so a
     * network that cannot accept the gas (or cannot pay for the energy) is never left with gas
     * removed from the vessel.
     */
    private void importFromBuffer(MEStorage storage, IEnergyService energy) {
        GasStack contents = tank.getContents();
        if (contents.isEmpty()) {
            return;
        }
        GasKey key = GasKey.of(contents.getGasType());
        if (key == null || !key.isKnown()) {
            return;
        }

        long wanted = Math.min(getTransferRate(), contents.getAmount());
        if (wanted <= 0) {
            return;
        }

        // 1. How much would the network accept, ignoring energy?
        long accepted = storage.insert(key, wanted, Actionable.SIMULATE, actionSource);
        if (accepted <= 0) {
            return;
        }
        // 2. How much can the network's energy budget pay for?
        accepted = Math.min(accepted,
                StorageHelper.poweredInsert(energy, storage, key, accepted, actionSource, Actionable.SIMULATE));
        if (accepted <= 0) {
            return;
        }
        // 3. How much is actually in the vessel right now?
        GasStack drained = tank.extractToNetwork(accepted, true);
        long toMove = Math.min(accepted, drained.getAmount());
        if (toMove <= 0) {
            return;
        }

        // 4. Commit: network first (it charges the energy), then remove exactly that much from the
        // vessel. If the network turns out to take less, only what it took leaves the vessel.
        long inserted = StorageHelper.poweredInsert(energy, storage, key, toMove, actionSource,
                Actionable.MODULATE);
        if (inserted > 0) {
            tank.extractToNetwork(inserted, false);
        }
    }

    /**
     * ME network -> buffer. Fills the vessel up to {@link #getSupplyPressurePa()}, never above its
     * rated pressure, so CCB pipes can then carry the gas away at a physical pressure.
     */
    private void exportToBuffer(MEStorage storage, IEnergyService energy) {
        long target = getSupplyAmount();
        long headroom = target - tank.getStoredAmount();
        if (headroom <= 0) {
            return;
        }
        long wanted = Math.min(getTransferRate(), headroom);
        if (wanted <= 0) {
            return;
        }

        GasKey key = findExportGas();
        if (key == null) {
            // Nothing to export: the vessel is empty and no adjacent gas network suggests a gas type.
            return;
        }

        // 1. How much can the network give, including energy?
        long available = StorageHelper.poweredExtraction(energy, storage, key, wanted, actionSource,
                Actionable.SIMULATE);
        if (available <= 0) {
            return;
        }
        // 2. How much does the vessel accept (it enforces its own pressure rating)?
        GasStack probe = key.toGasStack(available);
        long accepted = tank.insertFromNetwork(probe, true);
        long toMove = Math.min(available, accepted);
        if (toMove <= 0) {
            return;
        }

        // 3. Commit: take exactly what the vessel accepted out of the network.
        long extracted = StorageHelper.poweredExtraction(energy, storage, key, toMove, actionSource,
                Actionable.MODULATE);
        if (extracted > 0) {
            tank.insertFromNetwork(key.toGasStack(extracted), false);
        }
    }

    /**
     * The gas the interface exports.
     *
     * <p>Gas keys carry no item form, so a player cannot put a "gas sample" into a filter slot the way
     * a bucket filters a fluid. The interface therefore exports the gas it already holds; when the
     * vessel is empty it asks the adjacent gas network what it is working with. That way an export
     * interface hooked to a CCB pipe network keeps matching the gas already in that network instead of
     * needing a UI to pick it.</p>
     */
    @Nullable
    private GasKey findExportGas() {
        GasStack contents = tank.getContents();
        if (!contents.isEmpty()) {
            GasKey key = GasKey.of(contents.getGasType());
            return key != null && key.isKnown() ? key : null;
        }
        Level level = this.level;
        if (level == null) {
            return null;
        }
        BlockPos pos = this.worldPosition;
        for (Direction side : Direction.values()) {
            BlockPos neighbourPos = pos.relative(side);
            if (level.isOutsideBuildHeight(neighbourPos)) {
                continue;
            }
            GasHandler handler = level.getCapability(GasCapabilities.BLOCK, neighbourPos, side.getOpposite());
            if (handler == null) {
                continue;
            }
            for (int i = 0; i < handler.getTanks(); i++) {
                GasStack candidate = handler.getGasInTank(i);
                if (candidate != null && !candidate.isEmpty()) {
                    GasKey key = GasKey.of(candidate.getGasType());
                    if (key != null && key.isKnown()) {
                        return key;
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_TANK, tank.save(registries));
        tag.putString(TAG_MODE, mode.name());
        tag.putInt(TAG_SUPPLY_PRESSURE, supplyPressureKpa);
        upgrades.writeToNBT(tag, TAG_UPGRADES, registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.contains(TAG_TANK)) {
            tank.load(registries, tag.getCompound(TAG_TANK));
        }
        mode = GasInterfaceMode.byName(tag.getString(TAG_MODE));
        // Older saves have no setpoint: they fall back to the configured default.
        if (tag.contains(TAG_SUPPLY_PRESSURE)) {
            supplyPressureKpa = snapSupplyPressureKpa(tag.getInt(TAG_SUPPLY_PRESSURE));
        }
        upgrades.readFromNBT(tag, TAG_UPGRADES, registries);
        // The persisted volume is authoritative: it is the physical vessel the player built, and
        // clamping here keeps a config change from making an existing vessel over-pressure.
        tank.setVolume(Math.max(1, tank.getVolume()));
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, java.util.List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
    }
}
