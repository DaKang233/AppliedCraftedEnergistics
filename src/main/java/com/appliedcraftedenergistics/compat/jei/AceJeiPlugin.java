// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.implementations.IOBusScreen;
import appeng.client.gui.implementations.InterfaceScreen;
import appeng.client.gui.implementations.StorageBusScreen;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.FakeSlot;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.client.GasInterfaceScreen;
import com.appliedcraftedenergistics.device.GasInterfaceMenu;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.item.GasSampleItem;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.content.airtights.gasfilter.VirtualGasItems;
import org.jetbrains.annotations.Nullable;

/**
 * JEI integration: drag a CCB gas ingredient or a CCB virtual gas item onto an AE2 config/filter slot
 * to mark the gas.
 *
 * <p>AE2's container marking (left-click item / right-click contained gas) is provided globally by
 * {@code GasContainerItemStrategy}; this plugin only completes the JEI drag path. Explicit handlers are
 * registered for the stock interface and bus screens, plus the ExtendedAE and AE2 Lightning Tech
 * interface screens, so they take precedence over other addons' item-only ghost handlers.</p>
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

        addGasGhostHandler(registration, InterfaceScreen.class);
        addGasGhostHandler(registration, IOBusScreen.class);
        addGasGhostHandler(registration, StorageBusScreen.class);

        // Optional addon screens: loaded reflectively so the mod has no hard dependency on them.
        addGasGhostHandler(registration, "com.glodblock.github.extendedae.client.gui.GuiExInterface");
        addGasGhostHandler(registration, "com.moakiee.ae2lt.client.OverloadedInterfaceScreen");

        AppliedCraftedEnergistics.LOGGER.info(
                "JEI integration: gas ingredients can be dragged onto ME interface and bus config slots");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void addGasGhostHandler(IGuiHandlerRegistration registration, Class<?> screenClass) {
        if (screenClass == null) {
            return;
        }
        registration.addGhostIngredientHandler((Class) screenClass,
                (IGhostIngredientHandler) new FakeSlotGasGhostHandler());
    }

    private static void addGasGhostHandler(IGuiHandlerRegistration registration, String screenClassName) {
        try {
            addGasGhostHandler(registration, Class.forName(screenClassName));
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Optional mod not installed.
        }
    }

    @Nullable
    private static GasKey gasKeyOf(ITypedIngredient<?> ingredient) {
        Object value = ingredient.getIngredient();
        if (value instanceof GasStack gas && !gas.isEmpty()) {
            return GasKey.of(gas.getGasType());
        }
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isPresent()) {
            ItemStack item = stack.get();
            GasStack virtualGas = VirtualGasItems.readGasSample(item);
            if (!virtualGas.isEmpty()) {
                return GasKey.of(virtualGas.getGasType());
            }
            var sample = GasSampleItem.gasOf(item);
            if (sample != null) {
                return GasKey.of(sample);
            }
        }
        return null;
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

        @Override
        public void onComplete() {
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
    }

    /**
     * Generic handler using AE2's own {@link FakeSlot#setFilterTo(ItemStack)} packet. Only active slots
     * that accept the wrapped gas stack are offered, so locked bus slots are no longer highlighted.
     */
    private static final class FakeSlotGasGhostHandler
            implements IGhostIngredientHandler<AbstractContainerScreen<?>> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(AbstractContainerScreen<?> gui, ITypedIngredient<I> ingredient,
                boolean doStart) {
            GasKey key = gasKeyOf(ingredient);
            if (key == null) {
                return List.of();
            }
            ItemStack wrapped = GenericStack.wrapInItemStack(new GenericStack(key, 1));

            List<Target<I>> targets = new ArrayList<>();
            for (Slot slot : gui.getMenu().slots) {
                if (!(slot instanceof FakeSlot fakeSlot) || !fakeSlot.isActive()
                        || !fakeSlot.canSetFilterTo(wrapped)) {
                    continue;
                }
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 16, 16);
                    }

                    @Override
                    public void accept(I value) {
                        fakeSlot.setFilterTo(wrapped);
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }
}
