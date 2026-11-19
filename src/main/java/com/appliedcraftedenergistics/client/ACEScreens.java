// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.client;

import com.appliedcraftedenergistics.init.ACEMenus;

import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side screen registration.
 *
 * <p>This is the client-only half of the GUI: it is only ever class-loaded on the physical client
 * (guarded by {@link ACEDeviceClient}'s {@code Dist.CLIENT} subscriber), so the dedicated server never
 * touches a {@code net.minecraft.client} type.</p>
 */
public final class ACEScreens {
    /** Guards against double registration when both the annotated client class and a manual call run. */
    private static boolean registered;

    private ACEScreens() {
    }

    /** Registers all device screens. Safe to call more than once. */
    public static void register(IEventBus modBus) {
        modBus.addListener(ACEScreens::onRegisterMenuScreens);
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        bind(event);
    }

    static void bind(RegisterMenuScreensEvent event) {
        if (registered) {
            return;
        }
        registered = true;

        MenuScreens.ScreenConstructor<com.appliedcraftedenergistics.device.GasInterfaceMenu, GasInterfaceScreen> factory = GasInterfaceScreen::new;
        event.register(ACEMenus.ME_GAS_INTERFACE.get(), factory);
    }
}
