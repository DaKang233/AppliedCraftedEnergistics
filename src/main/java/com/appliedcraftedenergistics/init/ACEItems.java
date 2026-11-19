// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.init;

import appeng.items.storage.StorageTier;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.cell.GasStorageCellItem;
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

    public static final List<DeferredItem<GasStorageCellItem>> GAS_CELLS = List.of(
            GAS_CELL_1K, GAS_CELL_4K, GAS_CELL_16K, GAS_CELL_64K, GAS_CELL_256K);

    private ACEItems() {
    }

    private static DeferredItem<GasStorageCellItem> cell(String name, StorageTier tier) {
        return ITEMS.registerItem(name, properties -> new GasStorageCellItem(properties.stacksTo(1), tier));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
