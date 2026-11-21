// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.init;

import appeng.api.parts.RegisterPartCapabilitiesEvent;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.device.p2p.GasP2PTunnelPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;

/**
 * Exposes the Gas P2P Tunnel's {@code GasHandler} as a <b>block</b> capability on the cable bus.
 *
 * <p>Create: Crafted Beginning's pipes probe block-level capabilities, while an AE2 part lives inside a
 * cable-bus block. AE2's {@link RegisterPartCapabilitiesEvent} exists exactly for this: whatever an
 * addon registers here is re-published as a block-entity capability on the part host, resolved per
 * face to the part on that side (see {@code RegisterPartCapabilitiesEventInternal}). Without this
 * registration a gas pipe would not even see the tunnel.</p>
 */
@EventBusSubscriber(modid = AppliedCraftedEnergistics.MODID)
public final class ACEPartCapabilities {
    private ACEPartCapabilities() {
    }

    @SubscribeEvent
    public static void registerPartCapabilities(RegisterPartCapabilitiesEvent event) {
        event.register(
                GasCapabilities.BLOCK,
                (part, side) -> part.getExposedApi(),
                GasP2PTunnelPart.class);
    }
}
