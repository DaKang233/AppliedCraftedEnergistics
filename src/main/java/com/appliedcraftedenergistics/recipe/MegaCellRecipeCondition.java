// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.recipe;

import com.appliedcraftedenergistics.Config;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Recipe condition controlled by {@code recipes.enable_mega_cell_recipes}.
 *
 * <p>The 1M-256M gas cell recipes also carry a {@code neoforge:mod_loaded} condition for
 * MEGA Cells. This second condition lets a pack remove the default MEGA-gated recipes without
 * editing the jar, so a datapack can supply a non-MEGA progression instead. The condition
 * only affects recipe loading; the high-tier cell items remain registered.</p>
 */
public record MegaCellRecipeCondition() implements ICondition {
    public static final MapCodec<MegaCellRecipeCondition> CODEC = MapCodec.unit(MegaCellRecipeCondition::new);

    @Override
    public boolean test(IContext context) {
        return Config.COMMON.enableMegaCellRecipes.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
