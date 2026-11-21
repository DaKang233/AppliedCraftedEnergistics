// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.item;

import java.util.List;

import appeng.items.materials.UpgradeCardItem;

import com.appliedcraftedenergistics.init.ACEDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Upgrade card for AE2's import/export buses. Right-click (sneak-right-click goes backwards) cycles the
 * target pressure between 1 / 2 / 4 / 8 / 12 / 16 atm.
 *
 * <p>The installed card is read by the gas bus strategies: an export bus fills the adjacent vessel only
 * up to the target pressure, an import bus drains it only while it is above that pressure.</p>
 */
public class GasPressureCardItem extends UpgradeCardItem {
    private static final int[] PRESETS = { 1, 2, 4, 8, 12, 16 };

    public GasPressureCardItem(Properties properties) {
        super(properties);
    }

    public static int getAtm(ItemStack stack) {
        int value = stack.getOrDefault(ACEDataComponents.BUS_PRESSURE.get(), 1);
        return Math.clamp(value, 1, 16);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            int current = getAtm(stack);
            int next = cycle(current, player.isShiftKeyDown());
            stack.set(ACEDataComponents.BUS_PRESSURE.get(), next);
            player.displayClientMessage(
                    Component.translatable("item.appliedcraftedenergistics.gas_pressure_card.current", next), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static int cycle(int current, boolean backwards) {
        int index = 0;
        for (int i = 0; i < PRESETS.length; i++) {
            if (PRESETS[i] == current) {
                index = i;
                break;
            }
        }
        index = Math.floorMod(index + (backwards ? -1 : 1), PRESETS.length);
        return PRESETS[index];
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.appliedcraftedenergistics.gas_pressure_card.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.appliedcraftedenergistics.gas_pressure_card.current", getAtm(stack))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.appliedcraftedenergistics.gas_pressure_card.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
