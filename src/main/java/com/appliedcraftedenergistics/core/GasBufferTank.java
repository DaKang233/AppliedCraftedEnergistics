// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasPressure;
import net.ty.createcraftedbeginning.api.gas.GasPressureLimits;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;
import net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler;
import net.ty.createcraftedbeginning.api.gas.handler.GasTransferSnapshot;
import net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A real, physical pressure vessel used as the interchange buffer of the ME Gas Interface.
 *
 * <p>This is the answer to "the pressure problem": the ME network is a quantity-only store with no
 * volume or pressure, while Create: Crafted Beginning's gas system is a physical one with finite
 * volume and a hard pressure ceiling. Every transfer across the boundary therefore goes through this
 * tank, which:</p>
 *
 * <ul>
 *   <li>exposes a genuine CCB {@link GasHandler}/{@link GasStorageHandler} capability, so CCB pipes,
 *       pumps and gauges treat it as a normal gas vessel;</li>
 *   <li>enforces CCB's own pressure limits ({@value #DEFAULT_MAX_PRESSURE_ATM} atm by default, capped
 *       at CCB's hard ceiling), so pushing gas in is limited by the receiving vessel's headroom and
 *       never bypasses CCB overpressure rules;</li>
 *   <li>gives the AE side a simple "insert/extract amount" API that does not expose pressure at all.</li>
 * </ul>
 */
public class GasBufferTank implements GasStorageHandler, GasPressureCompartment {
    /**
     * The buffer is rated for CCB's <b>safe</b> operating pressure (16 atm) rather than the hard 24 atm
     * ceiling. Presenting a safe-rated vessel means an ME interface can never be the reason a player's
     * airtight tank builds up rupture stress, which is the failure mode the CCB pressure model warns about.
     */
    public static final int DEFAULT_MAX_PRESSURE_ATM = 16;
    private static final String TAG_TANK = "Tank";

    private long volumeLiters;
    private long maxPressurePa;
    private GasStack gas = GasStack.EMPTY;
    @Nullable
    private Runnable changeListener;

    public GasBufferTank(long volumeLiters, long maxPressurePa) {
        this.volumeLiters = Math.max(0, volumeLiters);
        this.maxPressurePa = clampPressure(maxPressurePa);
    }

    public GasBufferTank(long volumeLiters) {
        this(volumeLiters, DEFAULT_MAX_PRESSURE_ATM * GasPressure.REFERENCE_PRESSURE_PA);
    }

    private static long clampPressure(long pressurePa) {
        return Math.clamp(pressurePa, 0, GasPressureLimits.HARD_PRESSURE_PA);
    }

    // ---------------------------------------------------------------------------------------------
    // CCB GasHandler / GasStorageHandler contract
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean isGasValid(int tank, GasStack stack) {
        return tank == 0 && isGasValid(stack);
    }

    @Override
    public boolean isGasValid(GasStack stack) {
        return stack != null && !stack.isEmpty() && !stack.getGasType().isEmpty();
    }

    @Override
    public GasStack drain(GasStack resource, GasAction action) {
        if (resource == null || resource.isEmpty() || gas.isEmpty()) {
            return GasStack.EMPTY;
        }
        if (!GasStack.isSameGasSameComponents(gas, resource)) {
            return GasStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }

    @Override
    public GasStack drain(long maxDrain, GasAction action) {
        if (maxDrain <= 0 || gas.isEmpty()) {
            return GasStack.EMPTY;
        }
        long drained = Math.min(maxDrain, gas.getAmount());
        if (drained <= 0) {
            return GasStack.EMPTY;
        }
        GasStack result = gas.copyWithAmount(drained);
        if (action.execute()) {
            gas.shrink(drained);
            if (gas.getAmount() <= 0) {
                gas = GasStack.EMPTY;
            }
            onChanged();
        }
        return result;
    }

    @Override
    public GasStack getGasInTank(int tank) {
        return tank == 0 ? gas.copy() : GasStack.EMPTY;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public long fill(GasStack resource, GasAction action) {
        if (!isGasValid(resource)) {
            return 0;
        }
        long remaining = getRemainingCapacity();
        if (!gas.isEmpty() && !GasStack.isSameGasSameComponents(gas, resource)) {
            return 0;
        }
        long accepted = Math.min(remaining, resource.getAmount());
        if (accepted <= 0) {
            return 0;
        }
        if (action.execute()) {
            if (gas.isEmpty()) {
                gas = resource.copyWithAmount(accepted);
            } else {
                gas.grow(accepted);
            }
            onChanged();
        }
        return accepted;
    }

    @Override
    public GasStack getGasStack() {
        return gas.copy();
    }

    @Override
    public Object getCompartmentIdentity() {
        return this;
    }

    @Override
    public long getVolume() {
        return volumeLiters;
    }

    @Override
    public long getPressurePa() {
        return GasPressure.pressure(gas.getAmount(), volumeLiters);
    }

    @Override
    public long getMaxPressurePa() {
        return maxPressurePa;
    }

    @Override
    public long getMaxAmount() {
        return GasPressure.amount(volumeLiters, maxPressurePa);
    }

    @Override
    public long getStoredAmount() {
        return gas.getAmount();
    }

    @Override
    public boolean supportsExactDrainRecovery() {
        return true;
    }

    @Override
    public GasPressureCompartment getPressureCompartment(int tank) {
        if (tank != 0) {
            throw new IndexOutOfBoundsException("Gas buffer only has tank 0, got " + tank);
        }
        return this;
    }

    /**
     * CCB's pipe solver simulates, executes, and may roll back a transfer. Because this tank is the
     * transactional boundary between the physical gas network and the ME network (which cannot be
     * rolled back), it has to be able to restore itself.
     */
    @Override
    public GasTransferSnapshot captureTransferSnapshot() {
        GasStack saved = gas.copy();
        long savedVolume = volumeLiters;
        long savedMaxPressure = maxPressurePa;
        return new GasTransferSnapshot() {
            @Override
            public void restore() {
                gas = saved.copy();
                volumeLiters = savedVolume;
                maxPressurePa = savedMaxPressure;
            }

            @Override
            public void afterRestore() {
                onChanged();
            }
        };
    }

    @Override
    public PredictedTransferLimits predictTransferLimits(GasStack candidate, long storedAmount) {
        if (candidate == null || candidate.isEmpty() || !isGasValid(candidate)) {
            return new PredictedTransferLimits(0, 0);
        }
        if (!gas.isEmpty() && !GasStack.isSameGasSameComponents(gas, candidate)) {
            return new PredictedTransferLimits(0, 0);
        }
        long stored = Math.clamp(storedAmount, 0, getMaxAmount());
        return new PredictedTransferLimits(stored, Math.max(0, getMaxAmount() - stored));
    }

    @Override
    public GasHandler.AtomicFillResult tryFillAtomicallyFromPressure(List<GasStack> resources, long sourcePressurePa,
            GasAction action) {
        GasStack combined = GasStack.EMPTY;
        for (GasStack resource : resources) {
            if (resource == null || resource.isEmpty()) {
                continue;
            }
            if (combined.isEmpty()) {
                combined = resource.copy();
            } else if (GasStack.isSameGasSameComponents(combined, resource)) {
                combined.grow(resource.getAmount());
            } else {
                return AtomicFillResult.REJECTED;
            }
        }
        if (combined.isEmpty()) {
            return AtomicFillResult.SUCCESS;
        }
        if (!isGasValid(combined)) {
            return AtomicFillResult.REJECTED;
        }
        // Gas only flows from high to low pressure. A source below our current pressure cannot fill us.
        if (getStoredAmount() > 0 && sourcePressurePa < getPressurePa()) {
            return AtomicFillResult.REJECTED;
        }
        if (fill(combined, GasAction.SIMULATE) != combined.getAmount()) {
            return AtomicFillResult.REJECTED;
        }
        if (action.execute()) {
            fill(combined, GasAction.EXECUTE);
        }
        return AtomicFillResult.SUCCESS;
    }

    // ---------------------------------------------------------------------------------------------
    // AE-side API: quantity only, no pressure
    // ---------------------------------------------------------------------------------------------

    /** Inserts gas coming from the ME network. Returns the accepted amount. */
    public long insertFromNetwork(GasStack stack, boolean simulate) {
        return fill(stack, GasAction.get(!simulate));
    }

    /** Extracts gas towards the ME network. Returns the extracted stack. */
    public GasStack extractToNetwork(long maxAmount, boolean simulate) {
        return drain(maxAmount, GasAction.get(!simulate));
    }

    /** How much more can be stored at the configured pressure rating. */
    public long getRemainingCapacity() {
        return Math.max(0, getMaxAmount() - gas.getAmount());
    }

    public boolean isEmpty() {
        return gas.isEmpty();
    }

    public GasStack getContents() {
        return gas.copy();
    }

    public void setVolume(long volumeLiters) {
        if (this.volumeLiters != volumeLiters) {
            this.volumeLiters = Math.max(0, volumeLiters);
            onChanged();
        }
    }

    public void setMaxPressurePa(long maxPressurePa) {
        long clamped = clampPressure(maxPressurePa);
        if (this.maxPressurePa != clamped) {
            this.maxPressurePa = clamped;
            onChanged();
        }
    }

    public void setChangeListener(@Nullable Runnable listener) {
        this.changeListener = listener;
    }

    protected void onChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("volume", volumeLiters);
        tag.putLong("maxPressure", maxPressurePa);
        tag.put("gas", gas.saveOptional(registries));
        return tag;
    }

    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag.contains("volume")) {
            volumeLiters = Math.max(0, tag.getLong("volume"));
        }
        if (tag.contains("maxPressure")) {
            maxPressurePa = clampPressure(tag.getLong("maxPressure"));
        }
        GasStack loaded = GasStack.parseOptional(registries, tag.getCompound("gas"));
        long clamped = Math.min(loaded.getAmount(), getMaxAmount());
        gas = clamped <= 0 ? GasStack.EMPTY : loaded.copyWithAmount(clamped);
    }
}
