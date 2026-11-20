// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics;

import com.appliedcraftedenergistics.init.ACEItems;
import com.appliedcraftedenergistics.init.ACEDataComponents;
import com.appliedcraftedenergistics.init.ACECards;
import com.appliedcraftedenergistics.init.ACECreativeTabs;
import com.appliedcraftedenergistics.init.ACEBlocks;
import com.appliedcraftedenergistics.init.ACEBlockEntities;
import com.appliedcraftedenergistics.init.ACECapabilities;
import com.appliedcraftedenergistics.init.ACEMenus;
import com.appliedcraftedenergistics.init.ACEParts;
import com.appliedcraftedenergistics.integration.ACEGasIntegration;
import com.appliedcraftedenergistics.gas.GasKeyType;

import appeng.api.stacks.AEKeyType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applied Crafted Energistics — an Applied Energistics 2 addon that lets Create: Crafted Beginning gases
 * be stored, transported and crafted with through an ME network.
 */
@Mod(AppliedCraftedEnergistics.MODID)
public final class AppliedCraftedEnergistics {
    public static final String MODID = "appliedcraftedenergistics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public AppliedCraftedEnergistics(IEventBus modBus, ModContainer container) {
        // Register the gas storage channel as AE2 does its own: from the RegisterEvent for the
        // ae2:keytypes registry, while that registry is still open.
        modBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(AEKeyType.REGISTRY_KEY)) {
                ACEGasIntegration.registerKeyType(event.getRegistry(AEKeyType.REGISTRY_KEY));
            }
        });

        modBus.addListener(this::commonSetup);

        ACEItems.register(modBus);
        ACEDataComponents.register(modBus);
        ACECards.register(modBus);
        ACECreativeTabs.register(modBus);

        // Device subsystem
        ACEBlocks.register(modBus);
        ACEBlockEntities.register(modBus);
        ACEMenus.register(modBus);
        ACECapabilities.register(modBus);
        ACEParts.register(modBus);



        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ACEGasIntegration::registerStrategies);
        event.enqueueWork(ACEParts::initializeAttunement);
        event.enqueueWork(ACECards::registerUpgrades);
        LOGGER.info("Applied Crafted Energistics: gas storage channel {} registered", GasKeyType.TYPE.getId());
    }
}
