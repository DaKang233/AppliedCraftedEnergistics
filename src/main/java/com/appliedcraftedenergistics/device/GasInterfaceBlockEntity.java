// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device;

import java.util.EnumSet;
import java.util.List;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import com.appliedcraftedenergistics.Config;
import com.appliedcraftedenergistics.device.gasinterface.GasInterfaceLogic;
import com.appliedcraftedenergistics.device.gasinterface.GasInterfaceSettings;
import com.appliedcraftedenergistics.device.gasinterface.GasInterfaceStorageView;
import com.appliedcraftedenergistics.device.gasinterface.GasSafetyValve;
import com.appliedcraftedenergistics.init.ACEBlockEntities;
import com.appliedcraftedenergistics.init.ACEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The gas ME Interface: an AE2-interface-style machine with nine config slots (target amount + target
 * pressure + mode) and nine cache slots, plus a CCB-facing view of those cache slots as real gas
 * vessels.
 *
 * <p>Stock management lives in {@link GasInterfaceLogic}; the CCB capability is
 * {@link GasInterfaceStorageView}. This class is the block entity glue: node, ticking, NBT and drops.</p>
 */
public class GasInterfaceBlockEntity extends AENetworkedBlockEntity implements IUpgradeableObject {

    /** Upgrade slots: speed cards plus the slot-control and safety-valve cards. */
    public static final int UPGRADE_SLOTS = 6;
    /** Hard cap for speed cards, mirrors AE2's own bus convention. */
    public static final int MAX_SPEED_CARDS = 4;
    /** Supply-pressure adjustment granularity used by the GUI, in kPa. */
    public static final int SUPPLY_PRESSURE_STEP_KPA = 100;

    private static final String TAG_LEGACY_MODE = "mode";
    private static final String TAG_LEGACY_SUPPLY_PRESSURE = "supplyPressureKpa";

    private final IActionSource actionSource = IActionSource.ofMachine(this);
    private final GasInterfaceLogic logic;
    private final GasInterfaceStorageView gasView;

    public GasInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ACEBlockEntities.ME_GAS_INTERFACE.get(), pos, state);
        this.logic = new GasInterfaceLogic(ACEBlocks.ME_GAS_INTERFACE, this::setChanged);
        this.gasView = new GasInterfaceStorageView(logic);

        getMainNode()
                .setIdlePowerUsage(Config.COMMON.interfaceIdlePower.get())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    // ---------------------------------------------------------------------------------------------
    // Public state
    // ---------------------------------------------------------------------------------------------

    public GasInterfaceLogic getLogic() {
        return logic;
    }

    /** The CCB-facing view of the nine cache slots. */
    public GasInterfaceStorageView getGasView() {
        return gasView;
    }

    public GasInterfaceSettings getSettings() {
        return logic.getSettings();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return logic.getUpgrades();
    }

    // ---------------------------------------------------------------------------------------------
    // Ticking
    // ---------------------------------------------------------------------------------------------

    public void serverTick() {
        Level level = this.level;
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!getMainNode().isActive()) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        logic.tick(grid, actionSource);
        GasSafetyValve.tick(this, level, getBlockPos());
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence / drops
    // ---------------------------------------------------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        logic.save(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        logic.load(data, registries);
        // Legacy settings are intentionally not migrated: 0.2.0 replaces the single mode/supply
        // pressure pair with per-slot settings, whose defaults are the safe behaviour.
        data.remove(TAG_LEGACY_MODE);
        data.remove(TAG_LEGACY_SUPPLY_PRESSURE);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (int i = 0; i < logic.getUpgrades().size(); i++) {
            ItemStack stack = logic.getUpgrades().getStackInSlot(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.getConfig().clear();
        logic.getStorage().clear();
        logic.getUpgrades().clear();
    }
}
