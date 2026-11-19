// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.integration;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.me.storage.ExternalStorageFacade;
import appeng.parts.automation.HandlerStrategy;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;
import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges AE2's transfer machinery to a Create: Crafted Beginning {@link GasHandler}.
 *
 * <p>Registration of this strategy is what makes the stock AE2 Import Bus, Export Bus, ME Interface
 * and Pattern Provider able to move gas — AE2 asks this class to translate "AEKey + amount" into a
 * capability call on the adjacent block.</p>
 */
public final class GasHandlerStrategy extends HandlerStrategy<GasHandler, GasStack> {
    public static final GasHandlerStrategy INSTANCE = new GasHandlerStrategy();

    private GasHandlerStrategy() {
        super(GasKeyType.TYPE);
    }

    @Override
    public boolean isSupported(AEKey what) {
        return what instanceof GasKey;
    }

    @Override
    public ExternalStorageFacade getFacade(GasHandler handler) {
        return new GasMachineExternalStorageFacade(handler);
    }

    @Nullable
    @Override
    public GasStack getStack(AEKey what, long amount) {
        if (what instanceof GasKey gasKey && amount > 0) {
            GasStack stack = gasKey.toGasStack(amount);
            return stack.isEmpty() ? null : stack;
        }
        return null;
    }

    @Override
    public long insert(GasHandler handler, AEKey what, long amount, Actionable mode) {
        if (!(what instanceof GasKey gasKey) || amount <= 0) {
            return 0;
        }
        GasStack stack = gasKey.toGasStack(amount);
        if (stack.isEmpty()) {
            return 0;
        }
        return handler.fill(stack, GasAction.get(mode == Actionable.MODULATE));
    }
}
