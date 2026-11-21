// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.init;

import appeng.api.features.P2PTunnelAttunement;
import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.device.p2p.GasP2PTunnelPart;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ty.createcraftedbeginning.api.canister.CanisterCapabilities;

/** Part (cable-attached) item registrations. */
public final class ACEParts {
    public static final DeferredRegister.Items PARTS = DeferredRegister.createItems(AppliedCraftedEnergistics.MODID);

    /**
     * The Gas P2P Tunnel part item.
     *
     * <p>AE2 19.2 has one tunnel item per transported resource type (item / fluid / energy / ...), so
     * an addon ships its own; {@code PartModels.registerModels} must run while the item is created so
     * the cable-bus model loader knows every model the part can use.</p>
     */
    public static final DeferredItem<PartItem<GasP2PTunnelPart>> GAS_P2P_TUNNEL = PARTS.register(
            "gas_p2p_tunnel",
            () -> {
                PartModels.registerModels(PartModelsHelper.createModels(GasP2PTunnelPart.class));
                return new PartItem<>(new Item.Properties(), GasP2PTunnelPart.class, GasP2PTunnelPart::new);
            });

    private ACEParts() {
    }

    public static void register(IEventBus modBus) {
        PARTS.register(modBus);
    }

    /**
     * Attunement: a Gas P2P Tunnel may be re-tuned by right-clicking it with any CCB gas canister, and
     * datapacks can add their own trigger items through the generated attunement tag.
     *
     * <p>Runs during common setup, after item registration but before any player interaction.</p>
     */
    public static void initializeAttunement() {
        P2PTunnelAttunement.registerAttunementTag(GAS_P2P_TUNNEL::get);
        P2PTunnelAttunement.registerAttunementApi(
                GAS_P2P_TUNNEL::get,
                CanisterCapabilities.ITEM,
                Component.translatable("gui.appliedcraftedenergistics.p2p_attunement_gas"));
    }
}
