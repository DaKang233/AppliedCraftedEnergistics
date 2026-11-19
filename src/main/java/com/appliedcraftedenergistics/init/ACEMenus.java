// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.init;

import com.appliedcraftedenergistics.ACEText;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.device.GasInterfaceBlockEntity;
import com.appliedcraftedenergistics.device.GasInterfaceMenu;

import appeng.menu.implementations.MenuTypeBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu (container) type registrations. */
public final class ACEMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU,
            AppliedCraftedEnergistics.MODID);

    /**
     * The ME Gas Interface menu.
     *
     * <p>{@code buildUnregistered} is deliberate: AE2's {@code build(ResourceLocation)} also queues the
     * type for registration in AE2's own registry pass, which would collide with our
     * {@link DeferredRegister}. {@code buildUnregistered} still installs the opener, so
     * {@code MenuOpener.open(...)} works normally.</p>
     */
    public static final DeferredHolder<MenuType<?>, MenuType<GasInterfaceMenu>> ME_GAS_INTERFACE = MENU_TYPES
            .register("me_gas_interface", () -> MenuTypeBuilder
                    .create(GasInterfaceMenu::new, GasInterfaceBlockEntity.class)
                    .withMenuTitle(host -> ACEText.INTERFACE_TITLE)
                    .buildUnregistered(AppliedCraftedEnergistics.id("me_gas_interface")));

    private ACEMenus() {
    }

    public static void register(IEventBus modBus) {
        MENU_TYPES.register(modBus);
    }
}
