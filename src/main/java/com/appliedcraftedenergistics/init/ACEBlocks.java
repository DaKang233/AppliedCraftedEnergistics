// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.init;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.device.GasInterfaceBlock;

import appeng.api.upgrades.Upgrades;
import appeng.block.AEBaseBlock;
import appeng.core.definitions.AEItems;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block (and block item) registrations for Applied Crafted Energistics. */
public final class ACEBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AppliedCraftedEnergistics.MODID);
    /** Block items live in the item registry, but are declared here next to their blocks. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedCraftedEnergistics.MODID);

    /**
     * The ME Gas Interface. AE2's metal properties (hardness/resistance/sound) keep it consistent with
     * the machines it sits next to.
     */
    public static final DeferredBlock<GasInterfaceBlock> ME_GAS_INTERFACE = BLOCKS.register("me_gas_interface",
            () -> new GasInterfaceBlock(AEBaseBlock.metalProps()));

    /**
     * Block item with the one-line description tooltip. It intentionally has no item model of its own:
     * {@code models/item/me_gas_interface.json} just parents the block model.
     */
    public static final DeferredItem<GasInterfaceBlock.Item> ME_GAS_INTERFACE_ITEM = ITEMS.register("me_gas_interface",
            () -> new GasInterfaceBlock.Item(ME_GAS_INTERFACE.get(), new Item.Properties()));

    private ACEBlocks() {
    }

    /** Convenience accessor used by the block entity/item code. */
    public static GasInterfaceBlock getMeGasInterface() {
        return ME_GAS_INTERFACE.get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);

        // Upgrade cards have to be associated with the machine before the first tooltip or slot check
        // runs, which happens after common setup.
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(ACEBlocks::registerUpgrades));
    }

    /**
     * Only speed cards are supported: they raise the transfer rate ({@link GasInterfaceBlockEntity}
     * reads them lazily). Pressure limits are not upgradable on purpose — the vessel is rated to CCB's
     * hard ceiling and the supply pressure is a config/design decision, not something a card should be
     * able to push past.
     */
    private static void registerUpgrades() {
        Upgrades.add(AEItems.SPEED_CARD, ME_GAS_INTERFACE, com.appliedcraftedenergistics.device.GasInterfaceBlockEntity.MAX_SPEED_CARDS);
    }
}
