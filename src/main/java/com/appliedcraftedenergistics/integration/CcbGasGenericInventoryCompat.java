// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.integration;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;

import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;
import net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler;
import net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment;

/**
 * Exposes blocks that provide a CCB gas handler as an <b>AE2 generic internal inventory</b>.
 *
 * <p>AE2 addons (most notably AE2 Lightning Tech's Overloaded Interface) resolve import/export
 * targets, including wireless targets, by querying {@code AECapabilities.GENERIC_INTERNAL_INV}.
 * A CCB gas tank only exposes CCB's own gas capability, so those machines cannot see gas at all.
 * This bridge presents every CCB gas handler as a generic inventory whose slots are its tanks and
 * whose keys are gas keys, which lets AE2 automation move gas through its normal storage machinery
 * (including AE2LT's wireless distribution and import scanning).</p>
 *
 * <p>AE2's own machines already register this capability, so they are left untouched.</p>
 */
public final class CcbGasGenericInventoryCompat {
    private static boolean registered;

    private CcbGasGenericInventoryCompat() {
    }

    public static synchronized void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (registered) {
            return;
        }
        int count = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().hasBlockEntity()) {
                continue;
            }
            if (event.isBlockRegistered(AECapabilities.GENERIC_INTERNAL_INV, block)) {
                continue;
            }
            event.registerBlock(AECapabilities.GENERIC_INTERNAL_INV, CcbGasGenericInventoryCompat::getInventory, block);
            count++;
        }
        registered = count > 0;
        if (registered && count > 0) {
            com.appliedcraftedenergistics.AppliedCraftedEnergistics.LOGGER.info(
                    "Applied Crafted Energistics: CCB gas containers exposed as AE generic inventories on {} blocks",
                    count);
        }
    }

    private static GenericInternalInventory getInventory(Level level, BlockPos pos, BlockState state,
            BlockEntity blockEntity, Direction side) {
        GasHandler handler = level.getCapability(GasCapabilities.BLOCK, pos, state, blockEntity, side);
        if (handler == null || handler.getTanks() <= 0) {
            return null;
        }
        return new GasHandlerInventory(handler);
    }

    /** Generic inventory view over a CCB gas handler; one slot per tank. */
    static final class GasHandlerInventory implements GenericInternalInventory {
        private final GasHandler handler;

        GasHandlerInventory(GasHandler handler) {
            this.handler = handler;
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public GenericStack getStack(int slot) {
            GasStack stack = handler.getGasInTank(slot);
            return stack.isEmpty() ? null : new GenericStack(GasKey.of(stack.getGasType()), stack.getAmount());
        }

        @Override
        public AEKey getKey(int slot) {
            GasStack stack = handler.getGasInTank(slot);
            return stack.isEmpty() ? null : GasKey.of(stack.getGasType());
        }

        @Override
        public long getAmount(int slot) {
            GasStack stack = handler.getGasInTank(slot);
            return stack.isEmpty() ? 0 : stack.getAmount();
        }

        @Override
        public long getMaxAmount(AEKey key) {
            return getCapacity(key.getType());
        }

        @Override
        public long getCapacity(AEKeyType type) {
            if (!(type == GasKeyType.TYPE)) {
                return 0;
            }
            if (handler instanceof GasStorageHandler storage && storage.getTanks() > 0) {
                long max = 0;
                for (int tank = 0; tank < storage.getTanks(); tank++) {
                    max = Math.max(max, storage.getPressureCompartment(tank).getMaxAmount());
                }
                if (max > 0) {
                    return max;
                }
            }
            return ACEGasIntegration.INTERFACE_SLOT_CAPACITY;
        }

        @Override
        public boolean canInsert() {
            return true;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public void setStack(int slot, GenericStack stack) {
            if (slot < 0 || slot >= size()) {
                return;
            }
            // Best-effort absolute set: clear the tank, then fill the requested gas.
            if (handler instanceof GasStorageHandler storage) {
                var compartment = storage.getPressureCompartment(slot);
                long stored = compartment.getStoredAmount();
                if (stored > 0) {
                    compartment.drain(stored, GasAction.EXECUTE);
                }
                if (stack != null && stack.what() instanceof GasKey key) {
                    compartment.fill(key.toGasStack(stack.amount()), GasAction.EXECUTE);
                }
                return;
            }
            if (stack == null) {
                GasStack current = handler.getGasInTank(slot);
                if (!current.isEmpty()) {
                    handler.drain(current.getAmount(), GasAction.EXECUTE);
                }
            }
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return type == GasKeyType.TYPE;
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey key) {
            if (!(key instanceof GasKey gasKey)) {
                return false;
            }
            return handler.isGasValid(slot, gasKey.toGasStack(1));
        }

        @Override
        public long insert(int slot, AEKey key, long amount, Actionable mode) {
            if (!(key instanceof GasKey gasKey) || amount <= 0 || slot < 0 || slot >= size()) {
                return 0;
            }
            GasAction action = mode == Actionable.MODULATE ? GasAction.EXECUTE : GasAction.SIMULATE;
            if (handler instanceof GasStorageHandler storage) {
                return storage.getPressureCompartment(slot).fill(gasKey.toGasStack(amount), action);
            }
            return handler.fill(gasKey.toGasStack(amount), action);
        }

        @Override
        public long extract(int slot, AEKey key, long amount, Actionable mode) {
            if (!(key instanceof GasKey gasKey) || amount <= 0 || slot < 0 || slot >= size()) {
                return 0;
            }
            GasAction action = mode == Actionable.MODULATE ? GasAction.EXECUTE : GasAction.SIMULATE;
            if (handler instanceof GasStorageHandler storage) {
                var drained = storage.getPressureCompartment(slot).drain(amount, action);
                return drained.isEmpty() ? 0 : drained.getAmount();
            }
            var current = handler.getGasInTank(slot);
            if (current.isEmpty() || !GasKey.of(current.getGasType()).equals(gasKey)) {
                return 0;
            }
            var drained = handler.drain(gasKey.toGasStack(amount), action);
            return drained.isEmpty() ? 0 : drained.getAmount();
        }

        @Override
        public void beginBatch() {
        }

        @Override
        public void endBatch() {
        }

        @Override
        public void endBatchSuppressed() {
        }

        @Override
        public void onChange() {
        }
    }
}
