// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.client;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.SlotSemantics;

import com.appliedcraftedenergistics.device.GasInterfaceMenu;
import com.appliedcraftedenergistics.gas.GasAmounts;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for the gas ME Interface.
 *
 * <p>It reuses the stock ME Interface layout (background, the "Config" / "Stored Items" headers and
 * the same slot positions) so the two machines feel identical. Every marked config slot gets the
 * familiar gear button above it; clicking it (or middle-clicking the slot) opens the slot settings
 * submenu, which edits the target amount, the target pressure and, when the slot-control card is
 * installed, the input/output mode.</p>
 */
public class GasInterfaceScreen extends UpgradeableScreen<GasInterfaceMenu> {

    private final List<Button> amountButtons = new ArrayList<>();

    public GasInterfaceScreen(GasInterfaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, StyleManager.loadStyleDoc("/screens/me_gas_interface.json"));

        var configSlots = menu.getSlots(SlotSemantics.CONFIG);
        for (int i = 0; i < configSlots.size(); i++) {
            var button = new SetAmountButton(btn -> {
                var idx = amountButtons.indexOf(btn);
                if (idx >= 0) {
                    menu.sendOpenSlotSettings(idx);
                }
            });
            button.setDisableBackground(true);
            button.setMessage(Component.translatable("gui.appliedcraftedenergistics.slot.set_amount"));
            widgets.add("amtButton" + (1 + i), button);
            amountButtons.add(button);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var configSlots = menu.getSlots(SlotSemantics.CONFIG);
        for (int i = 0; i < amountButtons.size() && i < configSlots.size(); i++) {
            amountButtons.get(i).visible = !configSlots.get(i).getItem().isEmpty();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int configSlot = findConfigSlot(mouseX, mouseY);
        if (configSlot >= 0) {
            // Gear equivalent: middle-click also opens the slot settings.
            if (button == 2) {
                menu.sendOpenSlotSettings(configSlot);
                return true;
            }

        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));

        if (this.hoveredSlot != null && this.menu.getSlotSemantic(this.hoveredSlot) == SlotSemantics.CONFIG) {
            var configSlots = this.menu.getSlots(SlotSemantics.CONFIG);
            int slot = configSlots.indexOf(this.hoveredSlot);
            if (slot >= 0) {
                int packed = this.menu.getPackedSettings(slot);
                var generic = GenericStack.unwrapItemStack(this.hoveredSlot.getItem());
                long amount = generic == null ? 0 : generic.amount();
                lines.add(Component.translatable("gui.appliedcraftedenergistics.slot.target",
                        GasAmounts.format(amount)).withStyle(ChatFormatting.GRAY));
                if (GasInterfaceMenu.explicitOf(packed)) {
                    lines.add(Component.translatable("gui.appliedcraftedenergistics.slot.pressure",
                            GasInterfaceMenu.pressureAtmOf(packed)).withStyle(ChatFormatting.GRAY));
                } else {
                    lines.add(Component.translatable("gui.appliedcraftedenergistics.slot.pressure_default")
                            .withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("gui.appliedcraftedenergistics.slot.mode",
                        GasInterfaceMenu.modeOf(packed).getDisplayName()).withStyle(ChatFormatting.GRAY));
            }
        }

        return lines;
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

    private static final class SetAmountButton extends IconButton {
        private SetAmountButton(OnPress onPress) {
            super(onPress);
        }

        @Override
        protected Icon getIcon() {
            return isHoveredOrFocused() ? Icon.COG : Icon.COG_DISABLED;
        }
    }
}
