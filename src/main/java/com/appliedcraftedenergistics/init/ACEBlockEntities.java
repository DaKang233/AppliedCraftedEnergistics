// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.init;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.device.GasInterfaceBlockEntity;

import appeng.blockentity.AEBaseBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity type registrations. */
public final class ACEBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, AppliedCraftedEnergistics.MODID);

    /**
     * The ME Gas Interface block entity.
     *
     * <p>Registration follows AE2's own pattern: inside the registration supplier the block is told
     * about its (just created) block entity type and its ticker, and the type is associated with the
     * block item — AE2 uses that association for the memory card and for tooltips.</p>
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasInterfaceBlockEntity>> ME_GAS_INTERFACE = BLOCK_ENTITY_TYPES
            .register("me_gas_interface", () -> {
                var block = ACEBlocks.ME_GAS_INTERFACE.get();

                BlockEntityType<GasInterfaceBlockEntity> type = BlockEntityType.Builder
                        .of(GasInterfaceBlockEntity::new, block)
                        .build(null);

                // AE2 needs to know which item represents this block entity (memory card settings,
                // "usable in" tooltips, wrench behaviour).
                AEBaseBlockEntity.registerBlockEntityItem(type, block.asItem());

                // AE2 dispatches ticking itself for block entities that implement its marker
                // interfaces, which keeps our block class free of ticking glue.
                BlockEntityTicker<GasInterfaceBlockEntity> serverTicker = (level, pos, state,
                        be) -> be.serverTick();
                block.setBlockEntity(GasInterfaceBlockEntity.class, type, null, serverTicker);

                return type;
            });

    private ACEBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITY_TYPES.register(modBus);
    }
}
