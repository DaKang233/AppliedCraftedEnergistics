// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.implementations.AESubScreen;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.NumberEntryWidget;

import com.appliedcraftedenergistics.device.gasinterface.GasSlotSettingsMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the per-slot settings submenu: target amount (AE2 number-entry widget), target pressure and
 * the input/output mode.
 */
public class GasSlotSettingsScreen extends AEBaseScreen<GasSlotSettingsMenu> {

    private final NumberEntryWidget amount;
    private final AE2Button pressureAuto;
    private final AE2Button modeButton;
    private boolean amountInitialized;

    public GasSlotSettingsScreen(GasSlotSettingsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, StyleManager.loadStyleDoc("/screens/me_gas_slot_settings.json"));

        widgets.addButton("save", Component.translatable("gui.appliedcraftedenergistics.slot.set"),
                this::applyAmount);
        AESubScreen.addBackButton(menu, "back", widgets);

        this.amount = widgets.addNumberEntryWidget("amountToStock", NumberEntryType.UNITLESS);
        this.amount.setLongValue(1);
        this.amount.setTextFieldStyle(style.getWidget("amountToStockInput"));
        this.amount.setMinValue(0);
        this.amount.setHideValidationIcon(true);
        // Show the gas unit after the field, like AE2 item quantities show their unit.
        this.amount.setType(new NumberEntryType(1, "GU"));
        this.amount.setOnConfirm(this::applyAmount);

        widgets.addButton("pressure_down", Component.literal("-"), () -> menu.sendPressureDelta(-1));
        widgets.addButton("pressure_up", Component.literal("+"), () -> menu.sendPressureDelta(1));
        this.pressureAuto = widgets.addButton("pressure_auto",
                Component.translatable("gui.appliedcraftedenergistics.slot.auto"), menu::sendTogglePressureExplicit);
        this.modeButton = widgets.addButton("mode_button", Component.empty(), menu::sendCycleMode);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        if (!amountInitialized && menu.getWhatToStock() != null) {
            this.amount.setLongValue(menu.getInitialAmount());
            this.amount.setMaxValue(menu.getMaxAmount());
            this.amountInitialized = true;
        }

        var mode = menu.getMode();
        setTextContent("pressure_value", Component.literal(menu.isPressureExplicit()
                ? menu.getPressureAtm() + " atm"
                : Component.translatable("gui.appliedcraftedenergistics.slot.pressure_default").getString()));
        this.pressureAuto.setMessage(Component.translatable(menu.isPressureExplicit()
                ? "gui.appliedcraftedenergistics.slot.auto"
                : "gui.appliedcraftedenergistics.slot.manual"));

        setTextContent("mode_value", mode.getDisplayName());
        this.modeButton.setMessage(mode.getDisplayName());
        this.modeButton.active = menu.isControlCardInstalled();
    }

    private void applyAmount() {
        this.amount.getIntValue().ifPresent(menu::sendSetAmount);
    }
}
