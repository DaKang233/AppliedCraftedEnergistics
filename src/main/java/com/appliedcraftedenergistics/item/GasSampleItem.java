// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.item;

import com.appliedcraftedenergistics.init.ACEDataComponents;
import com.appliedcraftedenergistics.init.ACEItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.ty.createcraftedbeginning.api.gas.Gas;
import net.ty.createcraftedbeginning.api.gas.GasStack;

import org.jetbrains.annotations.Nullable;

/**
 * A small "sample" item that carries a gas id, used to mark gas slots of the ME Gas Interface and
 * other AE2 machines by hand (and as the item form a JEI ghost handler can drag).
 */
public class GasSampleItem extends Item {
    public GasSampleItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Gas gas) {
        var stack = new ItemStack(ACEItems.GAS_SAMPLE.get());
        stack.set(ACEDataComponents.GAS_ID.get(), gas.getResourceLocation());
        return stack;
    }

    /** The gas carried by a sample stack, or {@code null} when it is not a sample / has no gas. */
    @Nullable
    public static Gas gasOf(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ACEItems.GAS_SAMPLE.get())) {
            return null;
        }
        var id = stack.get(ACEDataComponents.GAS_ID.get());
        if (id == null) {
            return null;
        }
        var gas = Gas.findById(id);
        return gas == null || gas.isEmpty() ? null : gas;
    }

    /** Convenience for containers: the first non-empty gas stack of a canister/container item. */
    @Nullable
    public static GasStack firstGasOf(Object container) {
        if (container instanceof net.ty.createcraftedbeginning.api.canister.GasCanisterContainer canister) {
            for (int tank = 0; tank < canister.getTanks(); tank++) {
                var gas = canister.getGasInTank(tank);
                if (!gas.isEmpty()) {
                    return gas;
                }
            }
        }
        return null;
    }
}
