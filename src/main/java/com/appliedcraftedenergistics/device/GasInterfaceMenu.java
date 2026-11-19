// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device;

import org.jetbrains.annotations.Nullable;

import com.appliedcraftedenergistics.ACEText;
import com.appliedcraftedenergistics.init.ACEMenus;

import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.ty.createcraftedbeginning.api.gas.GasStack;

/**
 * Server-authoritative GUI for the ME Gas Interface.
 *
 * <p>AE2 menus mirror their block entity into {@code @GuiSync} fields every tick; the screen only ever
 * reads those mirrored fields, so it never touches block entity state directly. The mode button and the
 * supply-pressure buttons send client actions back to the server, which are the only ways that state can
 * change.</p>
 */
public class GasInterfaceMenu extends UpgradeableMenu<GasInterfaceBlockEntity> {
    /** Client -> server action id for the mode cycle button. */
    public static final String ACTION_CYCLE_MODE = "cycle_mode";
    /** Client -> server action id for the supply pressure +/- buttons; payload is the delta in kPa. */
    public static final String ACTION_ADJUST_SUPPLY_PRESSURE = "adjust_supply_pressure";

    // AE2 reserves the low sync ids for UpgradeableMenu's own fields (0, 1, 5, 6), so start at 10.
    @GuiSync(10)
    public GasInterfaceMode mode = GasInterfaceMode.IMPORT;
    @GuiSync(11)
    public long storedAmount;
    @GuiSync(12)
    public long maxAmount;
    @GuiSync(13)
    public long pressurePa;
    @GuiSync(14)
    public long maxPressurePa;
    /** Supply pressure setpoint in kPa, as shown by the GUI's supply pressure control. */
    @GuiSync(15)
    public int supplyPressureKpa;
    @GuiSync(16)
    public boolean atPressureCeiling;
    @GuiSync(17)
    public boolean supplyAboveSafe;
    /** Display name of the gas in the buffer, {@code null} when the buffer is empty. */
    @GuiSync(18)
    @Nullable
    public Component gasName;

    public GasInterfaceMenu(int id, Inventory playerInventory, GasInterfaceBlockEntity host) {
        super(ACEMenus.ME_GAS_INTERFACE.get(), id, playerInventory, host);
        registerClientAction(ACTION_CYCLE_MODE, this::cycleMode);
        registerClientAction(ACTION_ADJUST_SUPPLY_PRESSURE, Integer.class, this::adjustSupplyPressure);
    }

    /** Called by the screen's mode button. */
    public void sendCycleMode() {
        sendClientAction(ACTION_CYCLE_MODE);
    }

    /** Called by the screen's supply pressure buttons (delta in kPa, e.g. +/-100). */
    public void sendAdjustSupplyPressure(int deltaKpa) {
        sendClientAction(ACTION_ADJUST_SUPPLY_PRESSURE, deltaKpa);
    }

    private void cycleMode() {
        GasInterfaceBlockEntity host = getHost();
        if (host != null && !host.isRemoved()) {
            host.cycleMode();
        }
    }

    private void adjustSupplyPressure(int deltaKpa) {
        GasInterfaceBlockEntity host = getHost();
        if (host != null && !host.isRemoved()) {
            host.adjustSupplyPressureKpa(deltaKpa);
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            GasInterfaceBlockEntity host = getHost();
            this.mode = host.getMode();
            this.storedAmount = host.getStoredAmount();
            this.maxAmount = host.getMaxAmount();
            this.pressurePa = host.getPressurePa();
            this.maxPressurePa = host.getMaxPressurePa();
            this.supplyPressureKpa = host.getSupplyPressureKpa();
            this.atPressureCeiling = host.isAtPressureCeiling();
            this.supplyAboveSafe = host.isSupplyPressureAboveSafe();

            GasStack contents = host.getContents();
            this.gasName = contents.isEmpty() ? null : contents.getHoverName();
        }

        // Let AE2's base class do the actual field comparison/broadcast.
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return !getHost().isRemoved();
    }

    /** Exposed for the screen so it can show the menu title without knowing about ACEText. */
    public Component getTitle() {
        return ACEText.INTERFACE_TITLE;
    }
}
