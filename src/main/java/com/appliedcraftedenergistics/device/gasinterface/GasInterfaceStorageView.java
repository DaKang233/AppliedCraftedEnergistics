// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device.gasinterface;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

import com.appliedcraftedenergistics.gas.GasAmounts;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasStacks;

import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasTransferSnapshot;
import net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler;
import net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The CCB-facing view of the gas ME Interface: nine cache slots presented as real CCB vessels.
 *
 * <p>Each slot's volume is {@code targetAmount / targetPressureAtm} (see
 * {@link GasInterfaceLogic#getSlotVolumeLiters(int)}), so a full slot is exactly at its configured
 * pressure and a half-full one is below it. The gas itself is stored in the interface's cache
 * inventory; this class only translates between AE2's {@link GenericStack} and CCB's {@link GasStack},
 * applies the per-slot mode gates and supplies the transaction snapshot CCB needs for rollback.</p>
 */
public final class GasInterfaceStorageView implements GasStorageHandler {
    private final GasInterfaceLogic logic;
    private final SlotCompartment[] compartments;

    public GasInterfaceStorageView(GasInterfaceLogic logic) {
        this.logic = logic;
        this.compartments = new SlotCompartment[GasInterfaceLogic.SLOTS];
        for (int i = 0; i < compartments.length; i++) {
            compartments[i] = new SlotCompartment(i);
        }
    }

    @Override
    public int getTanks() {
        return compartments.length;
    }

    @Override
    public GasPressureCompartment getPressureCompartment(int tank) {
        if (tank < 0 || tank >= compartments.length) {
            throw new IndexOutOfBoundsException("gas interface has " + compartments.length + " tanks, asked " + tank);
        }
        return compartments[tank];
    }

    @Override
    public boolean isGasValid(int tank, GasStack stack) {
        return tank >= 0 && tank < compartments.length && compartments[tank].isGasValid(stack);
    }

    @Override
    public GasStack getGasInTank(int tank) {
        if (tank < 0 || tank >= compartments.length) {
            return GasStack.EMPTY;
        }
        return compartmentGas(tank);
    }

    /** Fills slots in order, honouring the per-slot mode and the configured gas of each slot. */
    @Override
    public long fill(GasStack resource, GasAction action) {
        if (resource == null || resource.isEmpty()) {
            return 0;
        }
        long remaining = resource.getAmount();
        long filled = 0;
        for (int slot = 0; slot < compartments.length && remaining > 0; slot++) {
            long moved = compartments[slot].fill(resource.copyWithAmount(remaining), action);
            filled += moved;
            remaining -= moved;
        }
        return filled;
    }

    /** Drains the requested gas, preferring the first matching slot (filtered pipes hit their slot). */
    @Override
    public GasStack drain(GasStack resource, GasAction action) {
        if (resource == null || resource.isEmpty()) {
            return GasStack.EMPTY;
        }
        long remaining = resource.getAmount();
        long drained = 0;
        for (var compartment : compartments) {
            if (remaining <= 0) {
                break;
            }
            var result = compartment.drain(resource.copyWithAmount(remaining), action);
            if (!result.isEmpty() && GasStack.isSameGasSameComponents(result, resource)) {
                drained += result.getAmount();
                remaining -= result.getAmount();
            }
        }
        if (drained <= 0) {
            return GasStack.EMPTY;
        }
        return resource.copyWithAmount(drained);
    }

    /** Plain-pipe behaviour: take from the first slot that can supply, in slot order. */
    @Override
    public GasStack drain(long maxDrain, GasAction action) {
        long remaining = maxDrain;
        for (var compartment : compartments) {
            if (remaining <= 0) {
                break;
            }
            var result = compartment.drain(remaining, action);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return GasStack.EMPTY;
    }

    @Override
    @Nullable
    public GasTransferSnapshot captureTransferSnapshot() {
        var saved = new GenericStack[compartments.length];
        for (int i = 0; i < saved.length; i++) {
            var stack = logic.getStorage().getStack(i);
            saved[i] = stack == null ? null : new GenericStack(stack.what(), stack.amount());
        }
        return () -> {
            for (int i = 0; i < saved.length; i++) {
                logic.getStorage().setStack(i, saved[i]);
            }
        };
    }

    private GasStack compartmentGas(int slot) {
        var stack = logic.getStorageStack(slot);
        return stack == null ? GasStack.EMPTY : GasStacks.toGasStack(stack.what(), stack.amount());
    }

    /** One cache slot seen as a CCB pressure vessel. */
    final class SlotCompartment implements GasPressureCompartment {
        private final int slot;

        SlotCompartment(int slot) {
            this.slot = slot;
        }

        private GasInterfaceLogic logic() {
            return logic;
        }

        @Override
        public Object getCompartmentIdentity() {
            return this;
        }

        @Override
        public long getVolume() {
            return logic.getSlotVolumeLiters(slot);
        }

        @Override
        public long getPressurePa() {
            return GasAmounts.pressureFor(getStoredAmount(), getVolume());
        }

        @Override
        public long getMaxPressurePa() {
            // Rated to CCB's hard ceiling so overfill can be detected and vented by the safety valve.
            return GasAmounts.HARD_PRESSURE_PA;
        }

        @Override
        public long getMaxAmount() {
            return GasAmounts.maxAmountFor(getVolume(), getMaxPressurePa());
        }

        @Override
        public long getStoredAmount() {
            var stack = logic.getStorage().getStack(slot);
            return stack == null ? 0 : stack.amount();
        }

        @Override
        public GasStack getGasStack() {
            return compartmentGas(slot);
        }

        @Override
        public boolean isGasValid(GasStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            var target = logic.getConfig().getStack(slot);
            if (target == null || !(target.what() instanceof GasKey targetKey)) {
                return true; // unconfigured slots accept any gas (the network collects it)
            }
            return targetKey.getGasId().equals(stack.getGasType().getResourceLocation());
        }

        @Override
        public long fill(GasStack resource, GasAction action) {
            if (!logic.getEffectiveMode(slot).allowsFill() || !isGasValid(resource)) {
                return 0;
            }
            long remainingCapacity = getMaxAmount() - getStoredAmount();
            long accepted = Math.min(remainingCapacity, resource.getAmount());
            if (accepted <= 0) {
                return 0;
            }
            return logic.getStorage().insert(slot, GasStacks.toKey(resource), accepted,
                    action.execute() ? Actionable.MODULATE : Actionable.SIMULATE);
        }

        @Override
        public GasStack drain(GasStack resource, GasAction action) {
            if (!isGasValid(resource)) {
                return GasStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public GasStack drain(long maxDrain, GasAction action) {
            if (!logic.getEffectiveMode(slot).allowsDrain() || maxDrain <= 0) {
                return GasStack.EMPTY;
            }
            var stored = getGasStack();
            if (stored.isEmpty()) {
                return GasStack.EMPTY;
            }
            long amount = Math.min(maxDrain, stored.getAmount());
            long extracted = logic.getStorage().extract(slot, GasStacks.toKey(stored), amount,
                    action.execute() ? Actionable.MODULATE : Actionable.SIMULATE);
            return extracted <= 0 ? GasStack.EMPTY : stored.copyWithAmount(extracted);
        }

        @Override
        public PredictedTransferLimits predictTransferLimits(GasStack gas, long storedAmount) {
            if (gas == null || gas.isEmpty() || !isGasValid(gas)) {
                return new PredictedTransferLimits(0, 0);
            }
            long stored = Math.clamp(storedAmount, 0, getMaxAmount());
            long drainLimit = logic.getEffectiveMode(slot).allowsDrain() ? stored : 0;
            long fillLimit = logic.getEffectiveMode(slot).allowsFill()
                    ? Math.max(0, getMaxAmount() - stored)
                    : 0;
            return new PredictedTransferLimits(drainLimit, fillLimit);
        }

        @Override
        public PressureModel getPressureModel() {
            return PressureModel.VARIABLE;
        }

        @Override
        public boolean supportsExactDrainRecovery() {
            return true;
        }

        @Override
        public long restoreDrainedGas(GasStack resource, GasAction action) {
            if (resource == null || resource.isEmpty()) {
                return 0;
            }
            return logic.getStorage().insert(slot, GasStacks.toKey(resource), resource.getAmount(),
                    action.execute() ? Actionable.MODULATE : Actionable.SIMULATE);
        }

        @Override
        public GasTransferSnapshot captureTransferSnapshot() {
            var saved = logic.getStorage().getStack(slot);
            var copy = saved == null ? null : new GenericStack(saved.what(), saved.amount());
            return () -> logic.getStorage().setStack(slot, copy);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public boolean isGasValid(int tank, GasStack stack) {
            return tank == 0 && isGasValid(stack);
        }

        @Override
        public GasStack getGasInTank(int tank) {
            return tank == 0 ? getGasStack() : GasStack.EMPTY;
        }

    }
}

