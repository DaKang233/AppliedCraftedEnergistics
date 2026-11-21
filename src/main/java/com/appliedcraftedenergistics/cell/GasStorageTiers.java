// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.cell;

import appeng.items.storage.StorageTier;
import net.minecraft.world.item.Items;

/**
 * Storage tiers above AE2's native 256k ceiling.
 *
 * <p>AE2 only ships {@link StorageTier#SIZE_1K} through {@link StorageTier#SIZE_256K}.
 * The gas cells continue the same byte-based progression at 1M / 4M / 16M / 64M / 256M
 * bytes. These tiers only provide bytes and idle drain; the cell item still uses AE2's
 * {@code BasicStorageCell} inventory, so partitioning, cell workbench and upgrades keep
 * working unchanged.</p>
 */
public final class GasStorageTiers {
    public static final StorageTier SIZE_1M = tier(6, "1m", 1 << 20, 3.0);
    public static final StorageTier SIZE_4M = tier(7, "4m", 4 << 20, 3.5);
    public static final StorageTier SIZE_16M = tier(8, "16m", 16 << 20, 4.0);
    public static final StorageTier SIZE_64M = tier(9, "64m", 64 << 20, 4.5);
    public static final StorageTier SIZE_256M = tier(10, "256m", 256 << 20, 5.0);

    private GasStorageTiers() {
    }

    private static StorageTier tier(int index, String name, int bytes, double idleDrain) {
        return new StorageTier(index, name, bytes, idleDrain, () -> Items.AIR);
    }
}
