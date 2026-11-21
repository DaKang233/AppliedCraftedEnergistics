// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.compat.jei;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.gas.GasKey;

import mezz.jei.api.ingredients.IIngredientType;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.compat.jei.CCBJEIPlugin;

import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/**
 * Optional bridge to AE2's JEI integration.
 *
 * <p>AE2's JEI pattern-transfer handler builds its generic pattern ingredients through
 * {@link IngredientConverters}. Registering a {@link GasStack} converter therefore makes the
 * normal JEI "+" button on any CCB recipe encode gas inputs and outputs into a processing
 * pattern, without a separate recipe transfer handler.</p>
 *
 * <p>This class directly references the optional ae2jeiintegration API. It is only loaded
 * reflectively from {@link AceJeiPlugin} when that mod is present, so JEI without AE2JEI
 * integration still works.</p>
 *
 * <p>Limitation: a pattern stores a gas key and an amount. CCB target pressure is a property
 * of the receiving vessel/machine, not part of the AE2 key, so pressure-dependent recipes
 * (for example the airtight forging press) can encode the gas requirement but cannot pin a
 * target pressure. Such recipes would need a dedicated pattern/automation format later.</p>
 */
public final class AceAe2JeiIntegration {
    private static boolean registered;

    private AceAe2JeiIntegration() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        IngredientConverters.register(new GasIngredientConverter());
        registered = true;
        AppliedCraftedEnergistics.LOGGER.info(
                "JEI integration: registered CCB gas ingredient converter for AE2 pattern transfer");
    }

    private static final class GasIngredientConverter implements IngredientConverter<GasStack> {
        @Override
        public IIngredientType<GasStack> getIngredientType() {
            return CCBJEIPlugin.GAS_STACK;
        }

        @Override
        public @Nullable GasStack getIngredientFromStack(GenericStack genericStack) {
            if (genericStack.what() instanceof GasKey key) {
                GasStack stack = key.toGasStack(Math.max(1, genericStack.amount()));
                return stack.isEmpty() ? null : stack;
            }
            return null;
        }

        @Override
        public @Nullable GenericStack getStackFromIngredient(GasStack gasStack) {
            if (gasStack.isEmpty()) {
                return null;
            }
            GasKey key = GasKey.of(gasStack.getGasType());
            return key == null ? null : new GenericStack(key, gasStack.getAmount());
        }
    }
}
