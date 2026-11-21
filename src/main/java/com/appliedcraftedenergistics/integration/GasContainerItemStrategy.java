// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.integration;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.item.GasSampleItem;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.ty.createcraftedbeginning.api.canister.CanisterCapabilities;
import net.ty.createcraftedbeginning.api.gas.Gas;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.content.airtights.gasfilter.VirtualGasItems;

import org.jetbrains.annotations.Nullable;

/**
 * Teaches AE2 that a CCB gas container (or virtual gas item) contains a gas, using AE2's own
 * {@link ContainerItemStrategy} mechanism — the same mechanism Applied Mekanistics uses for Mekanism
 * chemicals.
 *
 * <p>With this registered, every AE2 config slot (ME Interface, addon interfaces, interface parts,
 * input/output/storage buses, ...) automatically gets:</p>
 * <ul>
 *   <li>the standard "left-click marks the item / right-click marks the contained gas" tooltip;</li>
 *   <li>right-click gas marking without any addon-specific GUI code.</li>
 * </ul>
 */
public final class GasContainerItemStrategy implements ContainerItemStrategy<GasKey, ItemStack> {
    @Override
    @Nullable
    public GenericStack getContainedStack(ItemStack stack) {
        GasStack gas = gasIn(stack);
        if (gas == null || gas.isEmpty() || gas.getGasType().isEmpty()) {
            return null;
        }
        return new GenericStack(GasKey.of(gas.getGasType()), Math.max(1, gas.getAmount()));
    }

    @Override
    @Nullable
    public ItemStack findCarriedContext(Player player, AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        return getContainedStack(carried) != null ? carried : null;
    }

    @Override
    @Nullable
    public ItemStack findPlayerSlotContext(Player player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        return getContainedStack(stack) != null ? stack : null;
    }

    @Override
    public long extract(ItemStack context, GasKey what, long amount, Actionable mode) {
        // Container items are not used as an input/output endpoint by the gas interface; marking is
        // handled by getContainedStack. Keep this conservative so AE2 never voids contents.
        return 0;
    }

    @Override
    public long insert(ItemStack context, GasKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public void playFillSound(Player player, GasKey what) {
    }

    @Override
    public void playEmptySound(Player player, GasKey what) {
    }

    @Override
    @Nullable
    public GenericStack getExtractableContent(ItemStack context) {
        return getContainedStack(context);
    }

    @Nullable
    private static GasStack gasIn(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        GasStack virtualGas = VirtualGasItems.readGasSample(stack);
        if (!virtualGas.isEmpty()) {
            return virtualGas;
        }
        Gas sample = GasSampleItem.gasOf(stack);
        if (sample != null) {
            return new GasStack(sample, 1);
        }
        var canister = stack.getCapability(CanisterCapabilities.ITEM);
        if (canister != null) {
            for (int tank = 0; tank < canister.getTanks(); tank++) {
                GasStack content = canister.getGasInTank(tank);
                if (!content.isEmpty()) {
                    return content;
                }
            }
        }
        return null;
    }
}
