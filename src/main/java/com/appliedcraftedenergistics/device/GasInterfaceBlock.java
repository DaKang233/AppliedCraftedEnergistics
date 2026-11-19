// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device;

import java.util.List;

import com.appliedcraftedenergistics.init.ACEMenus;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The ME Gas Interface: one AE2 machine face and one Create: Crafted Beginning gas vessel in a single
 * block.
 *
 * <p>Mechanically this is a plain AE networked block (the block entity does all the work). The only
 * block-level concerns are orientation and opening the GUI.</p>
 *
 * <h2>Block states (asset contract)</h2>
 * Exactly one property, {@code BlockStateProperties.HORIZONTAL_FACING} named {@code facing}, with the
 * four horizontal values. It comes from AE2's {@link OrientationStrategies#horizontalFacing()}
 * strategy: AE2's {@link appeng.block.AEBaseBlock} adds the strategy's properties in
 * {@code createBlockStateDefinition} and handles placement in {@code getStateForPlacement}, so the
 * block state contract is identical to declaring {@code HORIZONTAL_FACING} by hand, but the AE
 * orientation helpers ({@code BlockOrientation}) also agree with it.
 *
 * <p>The front face is cosmetic only: the machine works from any side, and CCB pipes may connect to
 * any face.</p>
 */
public class GasInterfaceBlock extends AEBaseEntityBlock<GasInterfaceBlockEntity> {
    public GasInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        // Sneaking bypasses the GUI, which lets a wrench/other mods interact with the block normally.
        if (!level.isClientSide() && !player.isShiftKeyDown()) {
            if (level.getBlockEntity(pos) instanceof GasInterfaceBlockEntity be) {
                MenuOpener.open(ACEMenus.ME_GAS_INTERFACE.get(), player, MenuLocators.forBlockEntity(be));
            }
        }
        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }

    /**
     * The block item. Its only job is the one-line description under the item name; everything else
     * (name, model, tooltip of the contained gas) follows from the block.
     */
    public static class Item extends BlockItem {
        public Item(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            tooltip.add(Component.translatable("block.appliedcraftedenergistics.me_gas_interface.desc")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
