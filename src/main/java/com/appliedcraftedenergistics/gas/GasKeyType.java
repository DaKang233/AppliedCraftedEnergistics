// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.gas;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.ty.createcraftedbeginning.api.gas.GasRegistries;

import java.util.stream.Stream;

/**
 * The AE storage channel for Create: Crafted Beginning gases.
 *
 * <p>All numbers here come from {@link GasAmounts}, which is the documented contract:
 * 1 byte = 1,000 GU, terminal display in kGU, one operation moves 1,000 GU.</p>
 */
public final class GasKeyType extends AEKeyType {
    public static final Component DESCRIPTION = Component.translatable("appliedcraftedenergistics.key_type.gas");

    public static final GasKeyType TYPE = new GasKeyType();

    private GasKeyType() {
        super(AppliedCraftedEnergistics.id("gas"), GasKey.class, DESCRIPTION);
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return GasKey.MAP_CODEC;
    }

    @Override
    public AEKey loadKeyFromTag(HolderLookup.Provider registries, CompoundTag tag) {
        return GasKey.fromTag(registries, tag);
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
        return new GasKey(input.readResourceLocation());
    }

    @Override
    public int getAmountPerOperation() {
        return GasAmounts.GU_PER_OPERATION;
    }

    @Override
    public int getAmountPerByte() {
        return GasAmounts.GU_PER_BYTE;
    }

    @Override
    public int getAmountPerUnit() {
        return GasAmounts.GU_PER_UNIT;
    }

    @Override
    public String getUnitSymbol() {
        return "kGU";
    }

    @Override
    public Stream<TagKey<?>> getTagNames() {
        return GasRegistries.GAS_REGISTRY.getTagNames().map(tag -> (TagKey<?>) tag);
    }
}
