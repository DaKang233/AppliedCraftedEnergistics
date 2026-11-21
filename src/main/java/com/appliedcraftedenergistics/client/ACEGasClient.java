// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.client.StorageCellModels;
import appeng.items.storage.BasicStorageCell;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;
import com.appliedcraftedenergistics.init.ACEItems;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client-only wiring for the gas storage channel and the gas storage cells. */
@EventBusSubscriber(modid = AppliedCraftedEnergistics.MODID, value = Dist.CLIENT)
public final class ACEGasClient {
    private ACEGasClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ACEGasClient::registerRenderers);
    }

    private static void registerRenderers() {
        // AE2 throws when rendering a channel that has no render handler, so this is not optional.
        AEKeyRendering.register(GasKeyType.TYPE, GasKey.class, new GasKeyRenderHandler());

        // ME Drives and ME Chests draw storage cells from a separately registered 3D model, not from
        // the item model. Without this the cell is inserted correctly but renders as nothing at all.
        registerCellModel("gas_cell_1k", ACEItems.GAS_CELL_1K.get());
        registerCellModel("gas_cell_4k", ACEItems.GAS_CELL_4K.get());
        registerCellModel("gas_cell_16k", ACEItems.GAS_CELL_16K.get());
        registerCellModel("gas_cell_64k", ACEItems.GAS_CELL_64K.get());
        registerCellModel("gas_cell_256k", ACEItems.GAS_CELL_256K.get());
        registerCellModel("gas_cell_1m", ACEItems.GAS_CELL_1M.get());
        registerCellModel("gas_cell_4m", ACEItems.GAS_CELL_4M.get());
        registerCellModel("gas_cell_16m", ACEItems.GAS_CELL_16M.get());
        registerCellModel("gas_cell_64m", ACEItems.GAS_CELL_64M.get());
        registerCellModel("gas_cell_256m", ACEItems.GAS_CELL_256M.get());
    }

    private static void registerCellModel(String itemPath, net.minecraft.world.item.Item item) {
        StorageCellModels.registerModel(item,
                AppliedCraftedEnergistics.id("block/drive/cells/" + itemPath));
    }

    /**
     * AE2 tints its storage-cell item model's second layer (the status LED) through a registered item
     * colour handler; addon cells like ours have to opt in explicitly, exactly like AE2's own cells do
     * in {@code InitItemColors}. Without this the LED would render a flat white pixel instead of the
     * cell's fill-level colour.
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> FastColor.ARGB32.opaque(BasicStorageCell.getColor(stack, tintIndex)),
                ACEItems.GAS_CELL_1K.get(),
                ACEItems.GAS_CELL_4K.get(),
                ACEItems.GAS_CELL_16K.get(),
                ACEItems.GAS_CELL_64K.get(),
                ACEItems.GAS_CELL_256K.get(),
                ACEItems.GAS_CELL_1M.get(),
                ACEItems.GAS_CELL_4M.get(),
                ACEItems.GAS_CELL_16M.get(),
                ACEItems.GAS_CELL_64M.get(),
                ACEItems.GAS_CELL_256M.get());
    }
}
