// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.integration;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;
import com.appliedcraftedenergistics.ACEText;
import com.appliedcraftedenergistics.gas.GasAmounts;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;
import com.appliedcraftedenergistics.gas.GasStacks;
import net.minecraft.network.chat.Component;
import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Presents an adjacent CCB gas handler (tank, pipe endpoint, machine) as ME storage, so the standard
 * AE2 Storage Bus can index it. Each CCB tank becomes one ME "slot".
 */
public final class GasMachineExternalStorageFacade extends ExternalStorageFacade {
    private final GasHandler handler;

    public GasMachineExternalStorageFacade(GasHandler handler) {
        this.handler = handler;
    }

    @Override
    public int getSlots() {
        return handler.getTanks();
    }

    @Nullable
    @Override
    public GenericStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= handler.getTanks()) {
            return null;
        }
        return GasStacks.toGeneric(handler.getGasInTank(slot));
    }

    @Override
    public AEKeyType getKeyType() {
        return GasKeyType.TYPE;
    }

    @Override
    protected int insertExternal(AEKey what, int amount, Actionable mode) {
        if (!(what instanceof GasKey gasKey) || amount <= 0) {
            return 0;
        }
        GasStack stack = gasKey.toGasStack(amount);
        if (stack.isEmpty()) {
            return 0;
        }
        long filled = handler.fill(stack, GasAction.get(mode == Actionable.MODULATE));
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, filled));
    }

    @Override
    protected int extractExternal(AEKey what, int amount, Actionable mode) {
        if (!(what instanceof GasKey gasKey) || amount <= 0) {
            return 0;
        }
        GasStack request = gasKey.toGasStack(amount);
        if (request.isEmpty()) {
            return 0;
        }
        GasStack drained = handler.drain(request, GasAction.get(mode == Actionable.MODULATE));
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, drained.getAmount()));
    }

    @Override
    public boolean containsAnyFuzzy(Set<AEKey> keys) {
        for (AEKey key : keys) {
            if (key instanceof GasKey) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            GasStack stack = handler.getGasInTank(tank);
            if (stack.isEmpty()) {
                continue;
            }
            GasKey key = new GasKey(stack.getGasType());
            out.add(key, stack.getAmount());
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return false;
    }

    @Override
    public Component getDescription() {
        return ACEText.EXTERNAL_GAS_STORAGE;
    }
}
