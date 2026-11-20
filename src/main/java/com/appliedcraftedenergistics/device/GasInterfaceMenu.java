// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device;

import com.appliedcraftedenergistics.device.gasinterface.GasInterfaceLogic;

import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;

import net.minecraft.world.entity.player.Inventory;

/**
 * Menu of the gas ME Interface: the same nine config + nine cache slots as AE2's ME Interface, plus the
 * six upgrade slots provided by {@link UpgradeableMenu}.
 *
 * <p>Both rows are AE2 generic-stack slots, so gases are rendered/edited through AE2's own
 * {@code ConfigMenuInventory} rather than a parallel item-inventory hack.</p>
 */
public class GasInterfaceMenu extends UpgradeableMenu<GasInterfaceBlockEntity> {

    public GasInterfaceMenu(int id, Inventory playerInventory, GasInterfaceBlockEntity host) {
        super(com.appliedcraftedenergistics.init.ACEMenus.ME_GAS_INTERFACE.get(), id, playerInventory, host);

        var config = host.getLogic().getConfig().createMenuWrapper();
        for (int i = 0; i < GasInterfaceLogic.SLOTS; i++) {
            addSlot(new FakeSlot(config, i), SlotSemantics.CONFIG);
        }

        var storage = host.getLogic().getStorage().createMenuWrapper();
        for (int i = 0; i < GasInterfaceLogic.SLOTS; i++) {
            addSlot(new AppEngSlot(storage, i), SlotSemantics.STORAGE);
        }
    }
}
