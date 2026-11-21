// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device.gasinterface;

import com.appliedcraftedenergistics.device.GasInterfaceBlockEntity;
import com.appliedcraftedenergistics.init.ACEMenus;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuLocators;
import appeng.menu.slot.InaccessibleSlot;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Per-slot settings submenu of the gas ME Interface.
 *
 * <p>Opened from the gear button above a marked config slot (or by middle-clicking the slot). It edits
 * three things:</p>
 * <ul>
 *   <li>the target stock amount (0–64,000 GU), using AE2's number-entry widget (Ctrl selects ×10
 *       steps, exactly like the stock ME Interface);</li>
 *   <li>the target supply pressure in atm (or "automatic", i.e. the default 1 atm);</li>
 *   <li>the per-slot transfer mode (Input / Output / Both / Off), available when the slot-control card
 *       is installed.</li>
 * </ul>
 */
public class GasSlotSettingsMenu extends AEBaseMenu implements ISubMenu {

    private static final String ACTION_SET_AMOUNT = "gasSlotSetAmount";
    private static final String ACTION_PRESSURE_DELTA = "gasSlotPressureDelta";
    private static final String ACTION_TOGGLE_PRESSURE = "gasSlotTogglePressure";
    private static final String ACTION_CYCLE_MODE = "gasSlotCycleMode";

    @GuiSync(110)
    public int initialAmount = -1;
    @GuiSync(111)
    public int maxAmount = -1;
    @GuiSync(112)
    public int pressureAtm = 1;
    @GuiSync(113)
    public boolean pressureExplicit;
    @GuiSync(114)
    public int modeOrdinal;
    @GuiSync(115)
    public boolean controlCardInstalled;

    private final GasInterfaceBlockEntity host;
    private final Slot stockedItem;
    private int slotIndex = -1;
    @Nullable
    private AEKey whatToStock;

    public GasSlotSettingsMenu(int id, Inventory playerInventory, GasInterfaceBlockEntity host) {
        super(ACEMenus.GAS_SLOT_SETTINGS.get(), id, playerInventory, host);
        this.host = host;
        this.stockedItem = new InaccessibleSlot(new AppEngInternalInventory(1), 0);
        addSlot(this.stockedItem, SlotSemantics.MACHINE_OUTPUT);

        registerClientAction(ACTION_SET_AMOUNT, Integer.class, this::applyAmount);
        registerClientAction(ACTION_PRESSURE_DELTA, Integer.class, this::applyPressureDelta);
        registerClientAction(ACTION_TOGGLE_PRESSURE, this::togglePressureExplicit);
        registerClientAction(ACTION_CYCLE_MODE, this::cycleMode);
    }

    public static void open(ServerPlayer player, GasInterfaceBlockEntity host, int slot) {
        MenuOpener.open(ACEMenus.GAS_SLOT_SETTINGS.get(), player,
                MenuLocators.forBlockEntity(host));
        if (player.containerMenu instanceof GasSlotSettingsMenu menu) {
            menu.init(slot);
            menu.broadcastChanges();
        }
    }

    /** Server-side initialisation after the submenu was opened for a slot. */
    public void init(int slot) {
        if (isClientSide() || slot < 0 || slot >= GasInterfaceLogic.SLOTS) {
            return;
        }
        var stack = host.getLogic().getConfig().getStack(slot);
        if (stack == null) {
            return;
        }
        this.slotIndex = slot;
        this.whatToStock = stack.what();
        this.initialAmount = (int) Math.min(Integer.MAX_VALUE, stack.amount());
        this.maxAmount = (int) GasInterfaceLogic.MAX_SLOT_AMOUNT;
        var settings = host.getSettings().get(slot);
        this.pressureAtm = settings.getTargetPressureAtm();
        this.pressureExplicit = settings.isPressureExplicit();
        this.modeOrdinal = settings.getMode().ordinal();
        this.controlCardInstalled = host.getLogic().isSlotControlCardInstalled();
        this.stockedItem.set(whatToStock.wrapForDisplayOrFilter());
    }

    @Override
    public GasInterfaceBlockEntity getHost() {
        return host;
    }

    public int getInitialAmount() {
        return initialAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public int getPressureAtm() {
        return pressureAtm;
    }

    public boolean isPressureExplicit() {
        return pressureExplicit;
    }

    public GasSlotMode getMode() {
        return GasSlotMode.byIndex(modeOrdinal);
    }

    public boolean isControlCardInstalled() {
        return controlCardInstalled;
    }

    @Nullable
    public AEKey getWhatToStock() {
        var stack = GenericStack.fromItemStack(stockedItem.getItem());
        return stack != null ? stack.what() : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Client -> server actions
    // ---------------------------------------------------------------------------------------------

    public void sendSetAmount(int amount) {
        sendClientAction(ACTION_SET_AMOUNT, amount);
    }

    public void sendPressureDelta(int delta) {
        sendClientAction(ACTION_PRESSURE_DELTA, delta);
    }

    public void sendTogglePressureExplicit() {
        sendClientAction(ACTION_TOGGLE_PRESSURE);
    }

    public void sendCycleMode() {
        sendClientAction(ACTION_CYCLE_MODE);
    }

    private void applyAmount(int amount) {
        if (isClientSide() || slotIndex < 0 || whatToStock == null) {
            return;
        }
        int clamped = Math.clamp(amount, 0, (int) GasInterfaceLogic.MAX_SLOT_AMOUNT);
        var config = host.getLogic().getConfig();
        if (!whatToStock.equals(config.getKey(slotIndex))) {
            return;
        }
        if (clamped <= 0) {
            config.setStack(slotIndex, null);
            this.initialAmount = 0;
        } else {
            config.setStack(slotIndex, new GenericStack(whatToStock, clamped));
            this.initialAmount = clamped;
        }
        host.setChanged();
        broadcastChanges();
        // Like the stock ME Interface, confirming the amount returns to the main interface screen.
        host.returnToMainMenu(getPlayer(), this);
    }

    private void applyPressureDelta(int delta) {
        if (isClientSide() || slotIndex < 0) {
            return;
        }
        var settings = host.getSettings().get(slotIndex);
        settings.setPressureExplicit(true);
        settings.setTargetPressureAtm(Math.clamp(settings.getEffectivePressureAtm() + delta, 1,
                GasSlotSettings.MAX_PRESSURE_ATM));
        this.pressureAtm = settings.getTargetPressureAtm();
        this.pressureExplicit = true;
        host.setChanged();
        broadcastChanges();
    }

    private void togglePressureExplicit() {
        if (isClientSide() || slotIndex < 0) {
            return;
        }
        var settings = host.getSettings().get(slotIndex);
        settings.setPressureExplicit(!settings.isPressureExplicit());
        this.pressureExplicit = settings.isPressureExplicit();
        host.setChanged();
        broadcastChanges();
    }

    private void cycleMode() {
        if (isClientSide() || slotIndex < 0 || !host.getLogic().isSlotControlCardInstalled()) {
            return;
        }
        var settings = host.getSettings().get(slotIndex);
        var modes = GasSlotMode.values();
        settings.setMode(modes[(settings.getMode().ordinal() + 1) % modes.length]);
        this.modeOrdinal = settings.getMode().ordinal();
        host.getLogic().saveModesToCard();
        host.setChanged();
        broadcastChanges();
    }
}
