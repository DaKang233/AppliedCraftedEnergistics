// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

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

    /**
     * Per-slot transfer modes stored on a slot-control card ("INPUT,OUTPUT,..." one ordinal per slot).
     * Removing the card makes every slot bidirectional; installing a configured card re-applies these.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SLOT_MODES = COMPONENTS
            .register("slot_modes", () -> DataComponentType.<String>builder()
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
                    .build());

    /** Target pressure in atm stored on a gas bus pressure card (1–16). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BUS_PRESSURE = COMPONENTS
            .register("bus_pressure", () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());

    private ACEDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
