// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.init;

import appeng.items.storage.StorageTier;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.cell.GasStorageCellItem;
import com.appliedcraftedenergistics.cell.GasStorageTiers;
import com.appliedcraftedenergistics.item.GasPressureCardItem;
import com.appliedcraftedenergistics.item.GasSampleItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/** Item registrations. */
public final class ACEItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedCraftedEnergistics.MODID);

    public static final DeferredItem<GasStorageCellItem> GAS_CELL_1K = cell("gas_cell_1k", StorageTier.SIZE_1K);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_4K = cell("gas_cell_4k", StorageTier.SIZE_4K);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_16K = cell("gas_cell_16k", StorageTier.SIZE_16K);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_64K = cell("gas_cell_64k", StorageTier.SIZE_64K);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_256K = cell("gas_cell_256k", StorageTier.SIZE_256K);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_1M = cell("gas_cell_1m", GasStorageTiers.SIZE_1M);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_4M = cell("gas_cell_4m", GasStorageTiers.SIZE_4M);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_16M = cell("gas_cell_16m", GasStorageTiers.SIZE_16M);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_64M = cell("gas_cell_64m", GasStorageTiers.SIZE_64M);
    public static final DeferredItem<GasStorageCellItem> GAS_CELL_256M = cell("gas_cell_256m", GasStorageTiers.SIZE_256M);

    /** Bus upgrade card: per-bus target pressure (right-click cycles the preset). */
    public static final DeferredItem<GasPressureCardItem> GAS_PRESSURE_CARD = ITEMS.registerItem("gas_pressure_card",
            properties -> new GasPressureCardItem(properties.stacksTo(1)));

    /** Marking item: carries a gas id, used to set gas config slots by hand. */
    public static final DeferredItem<GasSampleItem> GAS_SAMPLE = ITEMS.registerItem("gas_sample",
            properties -> new GasSampleItem(properties.stacksTo(1)));

    public static final List<DeferredItem<GasStorageCellItem>> GAS_CELLS = List.of(
            GAS_CELL_1K, GAS_CELL_4K, GAS_CELL_16K, GAS_CELL_64K, GAS_CELL_256K,
            GAS_CELL_1M, GAS_CELL_4M, GAS_CELL_16M, GAS_CELL_64M, GAS_CELL_256M);

    private ACEItems() {
    }

    private static DeferredItem<GasStorageCellItem> cell(String name, StorageTier tier) {
        return ITEMS.registerItem(name, properties -> new GasStorageCellItem(properties.stacksTo(1), tier));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
