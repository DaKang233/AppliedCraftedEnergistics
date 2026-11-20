// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import appeng.menu.SlotSemantics;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.client.GasInterfaceScreen;
import com.appliedcraftedenergistics.device.GasInterfaceMenu;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.content.airtights.gasfilter.VirtualGasItems;

/**
 * JEI integration: drag a gas (Crafted Beginning registers gases as a JEI ingredient) or a CCB gas
 * virtual item onto a config slot of the gas ME Interface to mark that slot.
 *
 * <p>JEI is optional; this plugin is only instantiated by JEI when it is installed.</p>
 */
@JeiPlugin
public final class AceJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = AppliedCraftedEnergistics.id("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(GasInterfaceScreen.class, new GasSlotGhostHandler());
        AppliedCraftedEnergistics.LOGGER.info(
                "JEI integration: gas ingredients can be dragged onto the gas ME Interface's config slots");
    }

    private static final class GasSlotGhostHandler implements IGhostIngredientHandler<GasInterfaceScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(GasInterfaceScreen gui, ITypedIngredient<I> ingredient,
                boolean doStart) {
            String gasId = gasIdOf(ingredient);
            if (gasId == null) {
                return List.of();
            }

            GasInterfaceMenu menu = gui.getMenu();
            var configSlots = menu.getSlots(SlotSemantics.CONFIG);
            List<Target<I>> targets = new ArrayList<>(configSlots.size());
            for (int index = 0; index < configSlots.size(); index++) {
                Slot slot = configSlots.get(index);
                int slotIndex = index;
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 16, 16);
                    }

                    @Override
                    public void accept(I value) {
                        menu.sendMarkGasId(slotIndex, gasId);
                    }
                });
            }
            return targets;
        }

        private static String gasIdOf(ITypedIngredient<?> ingredient) {
            Object value = ingredient.getIngredient();
            if (value instanceof GasStack gas && !gas.isEmpty()) {
                return gas.getGasType().getResourceLocation().toString();
            }
            Optional<ItemStack> stack = ingredient.getItemStack();
            if (stack.isPresent()) {
                GasStack virtualGas = VirtualGasItems.readGasSample(stack.get());
                if (!virtualGas.isEmpty()) {
                    return virtualGas.getGasType().getResourceLocation().toString();
                }
            }
            return null;
        }

        @Override
        public void onComplete() {
            // Nothing to clean up: the interface stores the mark immediately.
        }
    }
}
