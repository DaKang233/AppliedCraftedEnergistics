// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.gas;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.ty.createcraftedbeginning.api.gas.GasStack;

import javax.annotation.Nullable;

/** Conversion helpers between AE2's generic stack model and CCB's {@link GasStack}. */
public final class GasStacks {
    private GasStacks() {
    }

    /** Creates a gas stack for the given AE key, or {@code GasStack.EMPTY} for non-gas keys. */
    public static GasStack toGasStack(@Nullable AEKey key, long amount) {
        if (key instanceof GasKey gasKey) {
            return gasKey.toGasStack(amount);
        }
        return GasStack.EMPTY;
    }

    /** Creates a generic AE stack for the given gas stack, or {@code null} when empty. */
    @Nullable
    public static GenericStack toGeneric(GasStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return new GenericStack(new GasKey(stack.getGasType()), stack.getAmount());
    }

    /** The AE key matching the given gas stack, or {@code null} when empty. */
    @Nullable
    public static GasKey toKey(GasStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return new GasKey(stack.getGasType());
    }

    /** Amount of the given generic stack, or 0 when it is not a gas key. */
    public static long amountOf(@Nullable GenericStack stack) {
        if (stack != null && stack.what() instanceof GasKey) {
            return stack.amount();
        }
        return 0;
    }
}
