// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.device.p2p;

import java.util.List;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.Config;
import com.appliedcraftedenergistics.core.GasBufferTank;
import com.appliedcraftedenergistics.gas.GasAmounts;
import com.appliedcraftedenergistics.gas.GasKeyType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasPressure;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;
import net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler;
import net.ty.createcraftedbeginning.api.gas.handler.GasTransferSnapshot;
import net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment;
import org.jetbrains.annotations.Nullable;

/**
 * A P2P tunnel that carries Create: Crafted Beginning gas.
 *
 * <h2>How it behaves physically</h2>
 * CCB's pipe solver only moves gas with an endpoint whose handler is a {@link GasStorageHandler}
 * with a compartment of {@code volume > 0}, so "just forward the stack" is not enough: each tunnel
 * presents a real (small) pressure vessel to its adjacent gas network.
 *
 * <ul>
 *   <li><b>Input tunnel</b> — the vessel is its own buffer. CCB pipes push gas in while their line
 *       pressure is above the buffer's; the tunnel refuses to be drained from this side, so the gas can
 *       only leave through the linked outputs (AE2's input/output semantics).</li>
 *   <li><b>Output tunnel</b> — the same buffer is presented under the output part, so CCB pipes on the
 *       output side drain it as if the tank were right there. Each output draws straight from the
 *       input buffer on demand; a buffer is never copied, so there is no duplication window.</li>
 * </ul>
 *
 * Gas therefore crosses the tunnel at the pressure it arrived with, and CCB's own solver keeps
 * enforcing its pressure limits at both ends. AE energy is charged per gas unit actually delivered to
 * an output, via {@link P2PTunnelPart#deductTransportCost}.
 */
public class GasP2PTunnelPart extends P2PTunnelPart<GasP2PTunnelPart> {
    private static final P2PModels MODELS = new P2PModels(AppliedCraftedEnergistics.id("part/p2p/p2p_tunnel_gas"));

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    /** The input tunnel's buffer; outputs read from their input part's buffer. */
    private final GasBufferTank buffer;
    /** Stable capability instance handed to CCB (and, through the part host, to CCB pipes). */
    private final TankView view;

    public GasP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
        this.buffer = new GasBufferTank(Config.COMMON.gasP2pBufferVolumeLiters.get(),
                GasAmounts.SAFE_PRESSURE_PA);
        this.buffer.setChangeListener(this::onBufferChanged);
        this.view = new TankView();
    }

    /** The gas handler exposed on this tunnel's face. */
    public GasHandler getExposedApi() {
        return view;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    protected float getPowerDrainPerTick() {
        // P2P tunnels are 1 AE/t; gas tunnels also pay per unit transferred.
        return 1.0f;
    }

    // ---------------------------------------------------------------------------------------------
    // Buffer plumbing
    // ---------------------------------------------------------------------------------------------

    /** The vessel this tunnel presents: own buffer when input, the linked input's buffer when output. */
    @Nullable
    private GasBufferTank storage() {
        if (!isOutput()) {
            return buffer;
        }
        GasP2PTunnelPart input = getInput();
        return input != null ? input.buffer : null;
    }

    private void onBufferChanged() {
        BlockEntity be = getBlockEntity();
        if (be != null && be.getLevel() != null && !be.getLevel().isClientSide()) {
            be.setChanged();
        }
    }

    @Override
    public void onTunnelNetworkChange() {
        // The set of linked peers (and with it the pressure a face reports) changed; let CCB re-query.
        invalidateOwnCapabilities();
        if (!isOutput()) {
            for (GasP2PTunnelPart output : getOutputs()) {
                output.invalidateOwnCapabilities();
            }
        }
    }

    @Override
    public void onTunnelConfigChange() {
        onTunnelNetworkChange();
    }

    private void invalidateOwnCapabilities() {
        BlockEntity be = getBlockEntity();
        if (be != null) {
            be.invalidateCapabilities();
        }
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        if (data.contains("gas_buffer")) {
            buffer.load(registries, data.getCompound("gas_buffer"));
        }
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.put("gas_buffer", buffer.save(registries));
    }

    // ---------------------------------------------------------------------------------------------
    // The capability view
    // ---------------------------------------------------------------------------------------------

    /**
     * Presents the tunnel's vessel to Create: Crafted Beginning.
     *
     * <p>This class is both the {@link GasStorageHandler} and the {@link GasPressureCompartment} view:
     * returning one object keeps the identity stable, which CCB groups endpoints by.</p>
     */
    private final class TankView implements GasStorageHandler, GasPressureCompartment {
        @Nullable
        private GasBufferTank tank() {
            return storage();
        }

        // --- reads -------------------------------------------------------------------------------
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public GasStack getGasInTank(int tank) {
            GasBufferTank t = tank();
            return tank == 0 && t != null ? t.getContents() : GasStack.EMPTY;
        }

        @Override
        public GasStack getGasStack() {
            GasBufferTank t = tank();
            return t != null ? t.getContents() : GasStack.EMPTY;
        }

        @Override
        public long getStoredAmount() {
            GasBufferTank t = tank();
            return t != null ? t.getStoredAmount() : 0;
        }

        @Override
        public long getVolume() {
            GasBufferTank t = tank();
            return t != null ? t.getVolume() : 0;
        }

        @Override
        public long getPressurePa() {
            GasBufferTank t = tank();
            return t != null ? t.getPressurePa() : GasPressure.VACUUM_PA;
        }

        @Override
        public long getMaxPressurePa() {
            GasBufferTank t = tank();
            return t != null ? t.getMaxPressurePa() : GasPressure.VACUUM_PA;
        }

        @Override
        public long getMaxAmount() {
            GasBufferTank t = tank();
            return t != null ? t.getMaxAmount() : 0;
        }

        @Override
        public boolean isGasValid(GasStack stack) {
            return stack != null && !stack.isEmpty() && !stack.getGasType().isEmpty();
        }

        @Override
        public boolean isGasValid(int tank, GasStack stack) {
            return tank == 0 && isGasValid(stack);
        }

        @Override
        public Object getCompartmentIdentity() {
            return this;
        }

        @Override
        public GasPressureCompartment getPressureCompartment(int tank) {
            if (tank != 0) {
                throw new IndexOutOfBoundsException("Gas P2P has one tank, requested " + tank);
            }
            return this;
        }

        // --- transfers ---------------------------------------------------------------------------
        /** Input side accepts; output side never accepts (AE2 P2P directionality). */
        @Override
        public long fill(GasStack resource, GasAction action) {
            GasBufferTank t = tank();
            if (isOutput() || t == null) {
                return 0;
            }
            return t.fill(resource, action);
        }

        /** Input side never gives; output side drains the linked input buffer. */
        @Override
        public GasStack drain(long maxDrain, GasAction action) {
            GasBufferTank t = tank();
            if (!isOutput() || t == null || maxDrain <= 0) {
                return GasStack.EMPTY;
            }
            GasStack drained = t.drain(maxDrain, action);
            if (action.execute() && !drained.isEmpty()) {
                // The gas physically left the tunnel here, so that is where the AE cost belongs.
                deductTransportCost(drained.getAmount(), GasKeyType.TYPE);
            }
            return drained;
        }

        @Override
        public GasStack drain(GasStack resource, GasAction action) {
            GasBufferTank t = tank();
            if (!isOutput() || t == null || resource == null || resource.isEmpty()) {
                return GasStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public PredictedTransferLimits predictTransferLimits(GasStack gas, long storedAmount) {
            if (gas == null || gas.isEmpty() || !isGasValid(gas)) {
                return new PredictedTransferLimits(0, 0);
            }
            GasBufferTank t = tank();
            if (t == null) {
                return new PredictedTransferLimits(0, 0);
            }
            long stored = Math.clamp(storedAmount, 0, t.getMaxAmount());
            long fillLimit = isOutput() ? 0 : Math.max(0, t.getMaxAmount() - stored);
            return new PredictedTransferLimits(stored, fillLimit);
        }

        @Override
        public GasTransferSnapshot captureTransferSnapshot() {
            // CCB may execute and then roll back; the buffer is our own state, so it can be restored.
            GasBufferTank t = tank();
            return t != null ? t.captureTransferSnapshot() : null;
        }
    }
}
