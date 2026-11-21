// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.integration;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.storage.MEStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Makes the stock AE2 Storage Bus able to read an adjacent CCB gas handler.
 *
 * <p>This is also what lets an {@code External Storage} style ME Interface view a gas tank: the
 * interface looks for a storage adapter, and this strategy supplies one for gases.</p>
 */
public final class GasExternalStorageStrategy implements ExternalStorageStrategy {
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction side;

    public GasExternalStorageStrategy(ServerLevel level, BlockPos pos, Direction side) {
        this.level = level;
        this.pos = pos;
        this.side = side;
    }

    @Nullable
    @Override
    public MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback) {
        GasHandler handler = level.getCapability(GasCapabilities.BLOCK, pos, side);
        if (handler == null) {
            return null;
        }
        GasMachineExternalStorageFacade facade = new GasMachineExternalStorageFacade(handler);
        facade.setExtractableOnly(extractableOnly);
        facade.setChangeListener(injectOrExtractCallback);
        return facade;
    }
}
