// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device;

import com.appliedcraftedenergistics.device.gasinterface.GasInterfaceLogic;
import com.appliedcraftedenergistics.device.gasinterface.GasSlotMode;
import com.appliedcraftedenergistics.device.gasinterface.GasSlotSettings;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.item.GasSampleItem;

import appeng.api.stacks.GenericStack;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;

import net.ty.createcraftedbeginning.api.canister.CanisterCapabilities;
import net.ty.createcraftedbeginning.api.gas.Gas;
import net.ty.createcraftedbeginning.api.gas.GasStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.ty.createcraftedbeginning.content.airtights.gasfilter.VirtualGasItems;

/**
 * Menu of the gas ME Interface: nine config slots (target gas + amount), nine cache slots and the six
 * upgrade slots.
 *
 * <p>AE2 has no generic per-slot "pressure/mode" payload, so the interface's nine packed settings are
 * synchronised with {@link GuiSync}. Player edits are sent as client actions and validated/clamped on
 * the server before they are written to the block entity, so a crafted packet cannot exceed the
 * device's limits.</p>
 */
public class GasInterfaceMenu extends UpgradeableMenu<GasInterfaceBlockEntity> {

    private static final String ACTION_SET_PRESSURE = "setGasPressure";
    private static final String ACTION_CYCLE_MODE = "cycleGasMode";
    private static final String ACTION_MARK_SLOT = "markGasSlot";
    private static final String ACTION_MARK_GAS_ID = "markGasSlotId";
    private static final String ACTION_ADJUST_AMOUNT = "adjustGasAmount";

    // Packed per-slot settings: mode (2 bit) | explicit (1 bit) << 2 | effective atm (5 bit) << 3.
    @GuiSync(100)
    public int slotSettings0;
    @GuiSync(101)
    public int slotSettings1;
    @GuiSync(102)
    public int slotSettings2;
    @GuiSync(103)
    public int slotSettings3;
    @GuiSync(104)
    public int slotSettings4;
    @GuiSync(105)
    public int slotSettings5;
    @GuiSync(106)
    public int slotSettings6;
    @GuiSync(107)
    public int slotSettings7;
    @GuiSync(108)
    public int slotSettings8;

    public GasInterfaceMenu(int id, Inventory playerInventory, GasInterfaceBlockEntity host) {
        super(com.appliedcraftedenergistics.init.ACEMenus.ME_GAS_INTERFACE.get(), id, playerInventory, host);

        var config = host.getLogic().getConfig().createMenuWrapper();
        for (int i = 0; i < GasInterfaceLogic.SLOTS; i++) {
            addSlot(new FakeSlot(config, i), SlotSemantics.CONFIG);
        }

        var storage = host.getLogic().getStorage().createMenuWrapper();
        for (int i = 0; i < GasInterfaceLogic.SLOTS; i++) {
            addSlot(new AppEngSlot(storage, i), SlotSemantics.STORAGE);
        }

        registerClientAction(ACTION_SET_PRESSURE, String.class, this::onSetPressure);
        registerClientAction(ACTION_CYCLE_MODE, String.class, this::onCycleMode);
        registerClientAction(ACTION_MARK_SLOT, String.class, this::onMarkSlot);
        registerClientAction(ACTION_MARK_GAS_ID, String.class, this::onMarkGasId);
        registerClientAction(ACTION_ADJUST_AMOUNT, String.class, this::onAdjustAmount);
    }

    private static int pack(GasSlotSettings settings) {
        return settings.getMode().ordinal()
                | (settings.isPressureExplicit() ? 1 : 0) << 2
                | settings.getEffectivePressureAtm() << 3;
    }

    public static GasSlotMode modeOf(int packed) {
        return GasSlotMode.byIndex(packed & 0b11);
    }

    public static boolean explicitOf(int packed) {
        return (packed & 0b100) != 0;
    }

    public static int pressureAtmOf(int packed) {
        return (packed >> 3) & 0b11111;
    }

    @Override
    public void broadcastChanges() {
        if (!isClientSide()) {
            for (int slot = 0; slot < GasInterfaceLogic.SLOTS; slot++) {
                setSynced(slot, pack(getHost().getSettings().get(slot)));
            }
        }
        super.broadcastChanges();
    }

    private void setSynced(int slot, int packed) {
        switch (slot) {
            case 0 -> slotSettings0 = packed;
            case 1 -> slotSettings1 = packed;
            case 2 -> slotSettings2 = packed;
            case 3 -> slotSettings3 = packed;
            case 4 -> slotSettings4 = packed;
            case 5 -> slotSettings5 = packed;
            case 6 -> slotSettings6 = packed;
            case 7 -> slotSettings7 = packed;
            case 8 -> slotSettings8 = packed;
            default -> {
            }
        }
    }

    public int getPackedSettings(int slot) {
        return switch (slot) {
            case 0 -> slotSettings0;
            case 1 -> slotSettings1;
            case 2 -> slotSettings2;
            case 3 -> slotSettings3;
            case 4 -> slotSettings4;
            case 5 -> slotSettings5;
            case 6 -> slotSettings6;
            case 7 -> slotSettings7;
            case 8 -> slotSettings8;
            default -> 0;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Client -> server edits
    // ---------------------------------------------------------------------------------------------

    public void sendSetPressure(int slot, int atm) {
        sendClientAction(ACTION_SET_PRESSURE, slot + ":" + atm);
    }

    public void sendCycleMode(int slot) {
        sendClientAction(ACTION_CYCLE_MODE, Integer.toString(slot));
    }

    public void sendMarkSlot(int slot) {
        sendClientAction(ACTION_MARK_SLOT, Integer.toString(slot));
    }

    /** Ghost-drag marking: the client passes the gas id from the JEI ingredient. */
    public void sendMarkGasId(int slot, String gasId) {
        sendClientAction(ACTION_MARK_GAS_ID, slot + ":" + gasId);
    }

    public void sendAdjustAmount(int slot, long delta) {
        sendClientAction(ACTION_ADJUST_AMOUNT, slot + ":" + delta);
    }

    private void onSetPressure(String payload) {
        if (isClientSide()) {
            return;
        }
        var parts = payload.split(":");
        if (parts.length != 2) {
            return;
        }
        int slot = parseInt(parts[0], -1);
        int atm = parseInt(parts[1], Integer.MIN_VALUE);
        if (slot < 0 || slot >= GasInterfaceLogic.SLOTS || atm == Integer.MIN_VALUE) {
            return;
        }
        var settings = getHost().getSettings().get(slot);
        if (atm < 0) {
            settings.setPressureExplicit(false);
        } else {
            // Always validate on the server: never trust the packet's range.
            settings.setTargetPressureAtm(Math.clamp(atm, 0, GasSlotSettings.MAX_PRESSURE_ATM));
            settings.setPressureExplicit(true);
        }
        getHost().setChanged();
        broadcastChanges();
    }

    private void onCycleMode(String payload) {
        if (isClientSide() || !getHost().getLogic().isSlotControlCardInstalled()) {
            return;
        }
        int slot = parseInt(payload, -1);
        if (slot < 0 || slot >= GasInterfaceLogic.SLOTS) {
            return;
        }
        var settings = getHost().getSettings().get(slot);
        var modes = GasSlotMode.values();
        settings.setMode(modes[(settings.getMode().ordinal() + 1) % modes.length]);
        getHost().setChanged();
        broadcastChanges();
    }

    private void onAdjustAmount(String payload) {
        if (isClientSide()) {
            return;
        }
        var parts = payload.split(":");
        if (parts.length != 2) {
            return;
        }
        int slot = parseInt(parts[0], -1);
        long delta = parseLong(parts[1], 0);
        if (slot < 0 || slot >= GasInterfaceLogic.SLOTS || delta == 0) {
            return;
        }
        var config = getHost().getLogic().getConfig();
        var current = config.getStack(slot);
        if (current == null) {
            return;
        }
        long newAmount = Math.clamp(current.amount() + delta, 0, GasInterfaceLogic.MAX_SLOT_AMOUNT);
        if (newAmount <= 0) {
            config.setStack(slot, null);
        } else {
            config.setStack(slot, new GenericStack(current.what(), newAmount));
        }
        getHost().setChanged();
        broadcastChanges();
    }

    private void onMarkSlot(String payload) {
        if (isClientSide()) {
            return;
        }
        int slot = parseInt(payload, -1);
        if (slot < 0 || slot >= GasInterfaceLogic.SLOTS) {
            return;
        }

        Gas gas = null;
        ItemStack held = getCarried();
        if (held.isEmpty()) {
            held = getPlayerInventory().player.getMainHandItem();
        }
        if (!held.isEmpty()) {
            gas = GasSampleItem.gasOf(held);
            if (gas == null) {
                var canister = held.getCapability(CanisterCapabilities.ITEM);
                if (canister != null) {
                    for (int tank = 0; tank < canister.getTanks() && gas == null; tank++) {
                        GasStack content = canister.getGasInTank(tank);
                        if (!content.isEmpty()) {
                            gas = content.getGasType();
                        }
                    }
                }
            }
            if (gas == null) {
                // CCB's own virtual gas item (also what JEI hands out for gas ingredients).
                GasStack virtualGas = VirtualGasItems.readGasSample(held);
                if (!virtualGas.isEmpty()) {
                    gas = virtualGas.getGasType();
                }
            }
        }
        applyGasMark(slot, gas);
    }

    /** Server-side validation and application of a gas mark (amount is preserved when unchanged). */
    private void applyGasMark(int slot, Gas gas) {
        if (isClientSide() || gas == null || gas.isEmpty()) {
            return;
        }
        var config = getHost().getLogic().getConfig();
        var current = config.getStack(slot);
        long amount = GasInterfaceLogic.MAX_SLOT_AMOUNT;
        if (current != null && current.what() instanceof GasKey key && key.getGasId().equals(gas.getResourceLocation())) {
            amount = current.amount();
        }
        config.setStack(slot, new GenericStack(new GasKey(gas), amount));
        getHost().setChanged();
        broadcastChanges();
    }

    /** Ghost-drag from JEI: the payload is {@code slot:gasId}. */
    private void onMarkGasId(String payload) {
        if (isClientSide()) {
            return;
        }
        int separator = payload.indexOf(':');
        if (separator <= 0) {
            return;
        }
        int slot = parseInt(payload.substring(0, separator), -1);
        var id = ResourceLocation.tryParse(payload.substring(separator + 1));
        if (slot < 0 || slot >= GasInterfaceLogic.SLOTS || id == null) {
            return;
        }
        var gas = Gas.findById(id);
        if (gas == null) {
            return;
        }
        applyGasMark(slot, gas);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
