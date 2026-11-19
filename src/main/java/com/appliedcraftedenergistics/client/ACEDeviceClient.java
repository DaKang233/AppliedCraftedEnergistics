// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.client;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only bootstrap for the device subsystem.
 *
 * <p>The {@code @EventBusSubscriber} annotation (with {@code value = Dist.CLIENT}) is what keeps every
 * {@code net.minecraft.client} reference in this package off the dedicated server: FML simply never
 * loads this class there. No main-class wiring is needed for it.</p>
 */
@EventBusSubscriber(modid = AppliedCraftedEnergistics.MODID, value = Dist.CLIENT)
public final class ACEDeviceClient {
    private ACEDeviceClient() {
    }

    /** Binds menu types to screens. */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        ACEScreens.bind(event);
    }
}
