// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.init;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A single creative tab holding everything this mod registers.
 *
 * <p>The items are discovered by namespace rather than listed by hand, so the device and datagen
 * workstreams can add items without touching this file (and without merge conflicts).</p>
 */
public final class ACECreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            AppliedCraftedEnergistics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.appliedcraftedenergistics"))
                    .icon(() -> new ItemStack(ACEItems.GAS_CELL_1K.get()))
                    .displayItems((parameters, output) -> BuiltInRegistries.ITEM.stream()
                            .filter(item -> AppliedCraftedEnergistics.MODID
                                    .equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()))
                            .forEach(output::accept))
                    .build());

    private ACECreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
