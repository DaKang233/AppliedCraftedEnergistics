// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.init;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components used by ACE items. */
public final class ACEDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister
            .create(Registries.DATA_COMPONENT_TYPE, AppliedCraftedEnergistics.MODID);

    /** Gas id carried by a gas sample item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> GAS_ID = COMPONENTS
            .register("gas_id", () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .build());

    private ACEDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
