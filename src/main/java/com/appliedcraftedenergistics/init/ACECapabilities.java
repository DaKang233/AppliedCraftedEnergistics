// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.init;

import com.appliedcraftedenergistics.device.GasInterfaceBlockEntity;

import appeng.api.AECapabilities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;

/**
 * Capability registrations: what other mods (and AE2 itself) are allowed to see on our blocks.
 *
 * <p>Three capabilities, three audiences:</p>
 * <ul>
 *   <li>{@code AECapabilities.IN_WORLD_GRID_NODE_HOST} — tells AE2 this block joins the ME network.
 *       Without it the block entity is just a box.</li>
 *   <li>{@code GasCapabilities.BLOCK} — tells Create: Crafted Beginning that our buffer is a normal gas
 *       vessel, which is the entire integration point with CCB's pipe/pump/gauge system. This is
 *       returned on every side so pipes can attach from any face.</li>
 *   <li>{@code Capabilities.ItemHandler.BLOCK} — the upgrade inventory (speed cards), so hoppers and
 *       other automation can insert/extract cards like they can on any AE2 machine.</li>
 * </ul>
 */
public final class ACECapabilities {
    private ACECapabilities() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ACECapabilities::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        var type = ACEBlockEntities.ME_GAS_INTERFACE.get();

        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                type,
                (be, side) -> be);

        // expose the physical buffer vessel to CCB; the block entity owns it and keeps it in sync
        event.registerBlockEntity(
                GasCapabilities.BLOCK,
                type,
                (be, side) -> be.getGasView());

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                type,
                (be, side) -> be.getUpgrades().toItemHandler());
    }
}
