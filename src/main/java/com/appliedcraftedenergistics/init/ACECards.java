// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.init;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.cell.GasStorageCellItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Upgrade cards for the gas storage cells and the gas ME Interface.
 *
 * <p>Two kinds live here:</p>
 * <ul>
 *   <li>the four gas-specific cell cards — volume, pressure, type expansion and a relief valve;</li>
 *   <li>two interface cards — per-slot transfer control and a safety valve that vents overpressure
 *       through CCB's real gas release;</li>
 *   <li>the registration that lets AE2's own storage-cell cards (inverter, equal distribution,
 *       overflow destruction) work on our cells. AE2 only registers those for its own cell items, so
 *       without this block the cell workbench would reject them.</li>
 * </ul>
 */
public final class ACECards {
    public static final DeferredRegister.Items CARDS = DeferredRegister.createItems(AppliedCraftedEnergistics.MODID);

    /** Increases the cell's rated pressure from 16 atm to CCB's hard ceiling, 24 atm (×1.5 capacity). */
    public static final DeferredItem<Item> PRESSURE_CARD = card("pressure_card");
    /** Adds 50% virtual vessel volume per card (up to 4). */
    public static final DeferredItem<Item> VOLUME_CARD = card("volume_card");
    /** Adds 4 storable gas types per card (up to 2). */
    public static final DeferredItem<Item> TYPE_EXPANSION_CARD = card("type_expansion_card");
    /** Vents the overfill when an upgrade change shrinks the cell below its contents. */
    public static final DeferredItem<Item> RELIEF_VALVE_CARD = card("relief_valve_card");
    /** Unlocks per-slot INPUT/OUTPUT/BOTH/OFF control on the gas ME Interface. */
    public static final DeferredItem<Item> SLOT_CONTROL_CARD = card("slot_control_card");
    /** Vents overpressure from the gas ME Interface through CCB's real gas release. */
    public static final DeferredItem<Item> SAFETY_VALVE_CARD = card("safety_valve_card");

    /** Shared upgrade group so that card tooltips aggregate across all gas cell tiers. */
    public static final String GROUP = AppliedCraftedEnergistics.MODID + ".gas_cell";

    private ACECards() {
    }

    private static DeferredItem<Item> card(String name) {
        return CARDS.register(name, () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    }

    public static void register(IEventBus modBus) {
        CARDS.register(modBus);
    }

    /** Runs during common setup, after items exist. */
    public static void registerUpgrades() {
        // Per-bus target pressure card: registered on the AE2 import/export bus part items.
        Upgrades.add(ACEItems.GAS_PRESSURE_CARD, AEParts.IMPORT_BUS, 1);
        Upgrades.add(ACEItems.GAS_PRESSURE_CARD, AEParts.EXPORT_BUS, 1);

        for (var cell : ACEItems.GAS_CELLS) {
            var item = cell.get();

            // AE2's universal storage-cell upgrades (the same set AE2 registers for its fluid cells).
            Upgrades.add(AEItems.INVERTER_CARD, item, 1, GROUP);
            Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, item, 1, GROUP);
            Upgrades.add(AEItems.VOID_CARD, item, 1, GROUP);

            // Gas-specific cards.
            Upgrades.add(PRESSURE_CARD, item, GasStorageCellItem.MAX_PRESSURE_CARDS, GROUP);
            Upgrades.add(VOLUME_CARD, item, GasStorageCellItem.MAX_VOLUME_CARDS, GROUP);
            Upgrades.add(TYPE_EXPANSION_CARD, item, GasStorageCellItem.MAX_TYPE_CARDS, GROUP);
            Upgrades.add(RELIEF_VALVE_CARD, item, 1, GROUP);
        }
    }
}
