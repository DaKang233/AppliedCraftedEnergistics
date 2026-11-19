// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.gas;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.ty.createcraftedbeginning.api.gas.Gas;
import net.ty.createcraftedbeginning.api.gas.GasRegistries;
import net.ty.createcraftedbeginning.api.gas.GasStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * An AE key identifying one Create: Crafted Beginning gas type.
 *
 * <p>Gas data components are intentionally ignored for v0.1: a key is exactly "which gas", so two
 * stacks of the same gas always aggregate in the ME network. This is the same simplification the
 * reference addons make for their resources.</p>
 */
public final class GasKey extends AEKey {
    public static final String TAG_GAS = "gas";

    public static final MapCodec<GasKey> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf(TAG_GAS).forGetter(GasKey::getGasId))
            .apply(instance, GasKey::new));

    private final ResourceLocation gasId;

    public GasKey(ResourceLocation gasId) {
        this.gasId = Objects.requireNonNull(gasId, "gasId");
    }

    public GasKey(Gas gas) {
        this(gas.getResourceLocation());
    }

    public static GasKey of(Gas gas) {
        return new GasKey(gas);
    }

    /** Looks a key up by its gas registry id, returning {@code null} for unknown gases. */
    @Nullable
    public static GasKey of(ResourceLocation gasId) {
        if (gasId == null || GasRegistries.GAS_REGISTRY.get(gasId).isEmpty()) {
            return null;
        }
        return new GasKey(gasId);
    }

    public ResourceLocation getGasId() {
        return gasId;
    }

    /** The gas this key refers to. Falls back to CCB's empty gas for unknown ids. */
    public Gas getGas() {
        return GasRegistries.GAS_REGISTRY.get(gasId);
    }

    public Holder<Gas> getGasHolder() {
        return getGas().getHolder();
    }

    public boolean isKnown() {
        return !getGas().isEmpty();
    }

    /** Builds a gas stack of the given amount, or {@code GasStack.EMPTY} when the gas is unknown. */
    public GasStack toGasStack(long amount) {
        if (amount <= 0 || !isKnown()) {
            return GasStack.EMPTY;
        }
        return new GasStack(getGasHolder(), amount);
    }

    @Override
    public AEKeyType getType() {
        return GasKeyType.TYPE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_GAS, gasId.toString());
        return tag;
    }

    @Override
    public Object getPrimaryKey() {
        return gasId;
    }

    @Override
    public ResourceLocation getId() {
        return gasId;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        data.writeResourceLocation(gasId);
    }

    @Override
    protected Component computeDisplayName() {
        Gas gas = getGas();
        if (!isKnown()) {
            return Component.literal(gasId.toString());
        }
        return gas.getName(new GasStack(gas.getHolder(), 1));
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // Gases have no item form in CCB that we can hand out, so nothing drops.
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GasKey key && key.gasId.equals(gasId);
    }

    @Override
    public int hashCode() {
        return gasId.hashCode();
    }

    @Override
    public String toString() {
        return "GasKey[" + gasId + "]";
    }

    /** Convenience for logging/debugging. */
    public String describe() {
        return AppliedCraftedEnergistics.MODID + ":" + gasId;
    }
}
