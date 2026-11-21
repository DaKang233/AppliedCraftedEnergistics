// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.integration;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.stacks.AEKeyType;
import appeng.parts.automation.StorageExportStrategy;
import appeng.parts.automation.StorageImportStrategy;
import com.appliedcraftedenergistics.gas.GasAmounts;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;

/**
 * Registers the gas storage channel and teaches AE2's own devices how to move gases.
 *
 * <p>Nothing here creates custom AE2 parts: once these strategies are registered, the standard
 * AE2 Import Bus, Export Bus, Storage Bus, ME Interface and Pattern Provider all handle gases, which
 * is exactly the behaviour we want — the addon extends AE2 rather than cloning it.</p>
 */
public final class ACEGasIntegration {
    /** Capacity of one interface/pattern-provider slot when it holds gas, in GU (64 bytes). */
    public static final long INTERFACE_SLOT_CAPACITY = 64L * GasAmounts.GU_PER_BYTE;

    private static boolean keyTypeRegistered;
    private static boolean strategiesRegistered;

    private ACEGasIntegration() {
    }

    /**
     * Registers the gas storage channel in AE2's {@code ae2:keytypes} registry.
     *
     * <p>This must run from the {@code RegisterEvent} <b>for that registry</b> (the same way AE2
     * registers its own item/fluid channels). Registering from the mod constructor, a
     * {@code DeferredRegister} or common setup would race the registry freeze.</p>
     */
    public static synchronized void registerKeyType(Registry<AEKeyType> registry) {
        if (keyTypeRegistered) {
            return;
        }
        Registry.register(registry, GasKeyType.TYPE.getId(), GasKeyType.TYPE);
        keyTypeRegistered = true;
    }

    /** True once {@link #registerKeyType(Registry)} has run. */
    public static boolean isKeyTypeRegistered() {
        return keyTypeRegistered;
    }

    /** Runs during common setup; safe to call once. */
    public static synchronized void registerStrategies() {
        if (strategiesRegistered) {
            return;
        }
        strategiesRegistered = true;

        // Storage Bus: expose an adjacent CCB gas handler as ME storage.
        ExternalStorageStrategy.register(GasKeyType.TYPE, GasExternalStorageStrategy::new);

        // Import Bus: pull gas out of an adjacent CCB handler into the network.
        StackImportStrategy.register(GasKeyType.TYPE, ACEGasIntegration::createImportStrategy);

        // Export Bus + ME Interface/Pattern Provider push: insert gas into an adjacent CCB handler.
        StackExportStrategy.register(GasKeyType.TYPE, ACEGasIntegration::createExportStrategy);

        // Lets ME Interfaces and Pattern Providers hold gas in their pattern/slot inventory.
        GenericSlotCapacities.register(GasKeyType.TYPE, INTERFACE_SLOT_CAPACITY);

        // AE2-native container marking: left-click marks the item, right-click marks the gas.
        // This is the same mechanism Applied Mekanistics uses for Mekanism chemicals.
        ContainerItemStrategy.register(GasKeyType.TYPE, GasKey.class, new GasContainerItemStrategy());
    }

    private static StackImportStrategy createImportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return GasBusPressureStrategies.createImportStrategy(level, fromPos, fromSide);
    }

    private static StackExportStrategy createExportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return GasBusPressureStrategies.createExportStrategy(level, fromPos, fromSide);
    }
}
