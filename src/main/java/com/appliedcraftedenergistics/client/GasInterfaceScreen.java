// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.client;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.StyleManager;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.IOptionalSlot;

import com.appliedcraftedenergistics.device.GasInterfaceMenu;
import com.appliedcraftedenergistics.gas.GasAmounts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for the gas ME Interface: nine config slots (top row), nine cache slots (bottom row) and six
 * upgrade slots.
 *
 * <p>Controls on a config slot:</p>
 * <ul>
 *   <li>middle click — cycle the per-slot mode (needs the slot-control card);</li>
 *   <li>right click with a gas sample or a gas container — mark the slot with that gas;</li>
 *   <li>scroll — adjust the target amount by 1,000 GU;</li>
 *   <li>shift + scroll — adjust the target pressure by 1 atm (scroll down to 0 = back to the default).</li>
 * </ul>
 *
 * <p>All edits are sent as server-validated client actions; the tooltip shows the values the server
 * actually stored.</p>
 */
public class GasInterfaceScreen extends UpgradeableScreen<GasInterfaceMenu> {

    public GasInterfaceScreen(GasInterfaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, StyleManager.loadStyleDoc("/screens/me_gas_interface.json"));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int configSlot = findConfigSlot(mouseX, mouseY);
        if (configSlot >= 0) {
            if (button == 2) {
                menu.sendCycleMode(configSlot);
                return true;
            }
            if (button == 1 && !menu.getCarried().isEmpty()) {
                menu.sendMarkSlot(configSlot);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int configSlot = findConfigSlot(mouseX, mouseY);
        if (configSlot >= 0 && scrollY != 0) {
            if (hasShiftDown()) {
                int current = GasInterfaceMenu.pressureAtmOf(menu.getPackedSettings(configSlot));
                if (!GasInterfaceMenu.explicitOf(menu.getPackedSettings(configSlot))) {
                    current = GasInterfaceMenu.pressureAtmOf(menu.getPackedSettings(configSlot));
                }
                int next = current + (scrollY > 0 ? 1 : -1);
                menu.sendSetPressure(configSlot, next < 1 ? -1 : next);
            } else {
                menu.sendAdjustAmount(configSlot, (long) (scrollY > 0 ? 1_000 : -1_000));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);

        for (Slot slot : this.menu.slots) {
            if (slot.x < 0 || slot.y < 0 || slot instanceof IOptionalSlot) {
                continue;
            }
            if (this.menu.getSlotSemantic(slot) == SlotSemantics.UPGRADE) {
                continue;
            }
            Icon.SLOT_BACKGROUND.getBlitter()
                    .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                    .blit(guiGraphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        if (this.hoveredSlot == null || this.menu.getSlotSemantic(this.hoveredSlot) != SlotSemantics.CONFIG) {
            return;
        }
        int slot = this.menu.getSlots(SlotSemantics.CONFIG).indexOf(this.hoveredSlot);
        if (slot < 0) {
            return;
        }
        int packed = this.menu.getPackedSettings(slot);

        List<Component> lines = new ArrayList<>();
        var stack = GenericStack.unwrapItemStack(this.hoveredSlot.getItem());
        long amount = stack == null ? 0 : stack.amount();
        lines.add(Component.translatable("gui.appliedcraftedenergistics.slot.target", GasAmounts.format(amount)));
        if (GasInterfaceMenu.explicitOf(packed)) {
            lines.add(Component.translatable("gui.appliedcraftedenergistics.slot.pressure",
                    GasInterfaceMenu.pressureAtmOf(packed)));
        } else {
            lines.add(Component.translatable("gui.appliedcraftedenergistics.slot.pressure_default"));
        }
        lines.add(Component.translatable("gui.appliedcraftedenergistics.slot.mode",
                GasInterfaceMenu.modeOf(packed).getDisplayName()));
        drawTooltip(guiGraphics, mouseX, mouseY, lines);
    }

    private int findConfigSlot(double mouseX, double mouseY) {
        var configSlots = this.menu.getSlots(SlotSemantics.CONFIG);
        for (int i = 0; i < configSlots.size(); i++) {
            if (isHovering(configSlots.get(i), mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }
}
