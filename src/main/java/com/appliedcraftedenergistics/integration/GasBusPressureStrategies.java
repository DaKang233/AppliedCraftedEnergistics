// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.integration;

import java.util.List;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.networking.CableBusBlockEntity;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.gas.GasAmounts;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;
import com.appliedcraftedenergistics.init.ACEItems;
import com.appliedcraftedenergistics.item.GasPressureCardItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gas.GasCapabilities;
import net.ty.createcraftedbeginning.api.gas.GasStack;
import net.ty.createcraftedbeginning.api.gas.handler.GasHandler;
import net.ty.createcraftedbeginning.api.gas.handler.GasStorageHandler;
import net.ty.createcraftedbeginning.api.gas.pressure.GasPressureCompartment;

import org.jetbrains.annotations.Nullable;

/**
 * Import/export strategies for AE2 buses that honour a per-bus target pressure.
 *
 * <p>When a gas pressure card is installed in the bus, the adjacent vessel is wrapped in a
 * pressure-limited handler: an export bus only fills up to the target pressure, and an import bus only
 * drains the amount above the target pressure (i.e. it keeps the vessel at least at that pressure).
 * Without a card, the normal AE2 behaviour is used unchanged.</p>
 */
public final class GasBusPressureStrategies {
    private GasBusPressureStrategies() {
    }

    public static StackImportStrategy createImportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return new BusImportStrategy(level, fromPos, fromSide, targetPressurePa(level, fromPos, fromSide));
    }

    public static StackExportStrategy createExportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        return new BusExportStrategy(level, fromPos, fromSide, targetPressurePa(level, fromPos, fromSide));
    }

    /** Target pressure from the bus's installed card, or 0 when there is no card. */
    private static long targetPressurePa(ServerLevel level, BlockPos fromPos, Direction fromSide) {
        BlockPos busPos = fromPos.relative(fromSide);
        if (!(level.getBlockEntity(busPos) instanceof CableBusBlockEntity cableBus)) {
            return 0;
        }
        var part = cableBus.getPart(fromSide.getOpposite());
        if (!(part instanceof IUpgradeableObject upgradeable)) {
            return 0;
        }
        var upgrades = upgradeable.getUpgrades();
        for (int slot = 0; slot < upgrades.size(); slot++) {
            var stack = upgrades.getStackInSlot(slot);
            if (stack.is(ACEItems.GAS_PRESSURE_CARD.get())) {
                return GasPressureCardItem.getAtm(stack) * GasAmounts.REFERENCE_PRESSURE_PA;
            }
        }
        return 0;
    }

    private static GasHandler wrap(GasHandler handler, long targetPressurePa) {
        return targetPressurePa > 0 ? new PressureLimitedGasHandler(handler, targetPressurePa) : handler;
    }

    private static final class BusImportStrategy implements StackImportStrategy {
        private final BlockCapabilityCache<GasHandler, Direction> cache;
        private final long targetPressurePa;

        private BusImportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide, long targetPressurePa) {
            this.cache = BlockCapabilityCache.create(GasCapabilities.BLOCK, level, fromPos, fromSide);
            this.targetPressurePa = targetPressurePa;
        }

        @Override
        public boolean transfer(StackTransferContext context) {
            if (!context.isKeyTypeEnabled(GasKeyType.TYPE)) {
                return false;
            }
            var adjacentHandler = cache.getCapability();
            if (adjacentHandler == null) {
                return false;
            }

            var facade = new GasMachineExternalStorageFacade(wrap(adjacentHandler, targetPressurePa));
            long remainingTransferAmount = context.getOperationsRemaining()
                    * (long) GasKeyType.TYPE.getAmountPerOperation();
            var inv = context.getInternalStorage();

            for (int i = 0; i < facade.getSlots() && remainingTransferAmount > 0; i++) {
                var resource = facade.getStackInSlot(i);
                if (resource == null || context.isInFilter(resource.what()) == context.isInverted()) {
                    continue;
                }

                var amountForThisResource = inv.getInventory().insert(resource.what(), remainingTransferAmount,
                        Actionable.SIMULATE, context.getActionSource());
                if (amountForThisResource <= 0) {
                    continue;
                }
                var amount = facade.extract(resource.what(), amountForThisResource, Actionable.MODULATE,
                        context.getActionSource());
                if (amount > 0) {
                    var inserted = inv.getInventory().insert(resource.what(), amount, Actionable.MODULATE,
                            context.getActionSource());
                    if (inserted < amount) {
                        long leftover = amount - inserted;
                        leftover -= facade.insert(resource.what(), leftover, Actionable.MODULATE,
                                context.getActionSource());
                        if (leftover > 0) {
                            AppliedCraftedEnergistics.LOGGER.warn(
                                    "Gas import bus voided {} because the network refused the insert", leftover);
                        }
                    }
                    var opsUsed = Math.max(1, inserted / GasKeyType.TYPE.getAmountPerOperation());
                    context.reduceOperationsRemaining(opsUsed);
                    remainingTransferAmount -= inserted;
                }
            }
            return false;
        }
    }

    private static final class BusExportStrategy implements StackExportStrategy {
        private final BlockCapabilityCache<GasHandler, Direction> cache;
        private final long targetPressurePa;

        private BusExportStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide, long targetPressurePa) {
            this.cache = BlockCapabilityCache.create(GasCapabilities.BLOCK, level, fromPos, fromSide);
            this.targetPressurePa = targetPressurePa;
        }

        @Override
        public long transfer(StackTransferContext context, AEKey what, long amount) {
            if (!(what instanceof GasKey) || amount <= 0) {
                return 0;
            }
            var adjacentHandler = cache.getCapability();
            if (adjacentHandler == null) {
                return 0;
            }
            var facade = new GasMachineExternalStorageFacade(wrap(adjacentHandler, targetPressurePa));
            var inv = context.getInternalStorage().getInventory();
            long extracted = StorageHelper.poweredExtraction(context.getEnergySource(), inv, what, amount,
                    context.getActionSource(), Actionable.SIMULATE);
            long accepted = facade.insert(what, extracted, Actionable.SIMULATE, context.getActionSource());
            if (accepted <= 0) {
                return 0;
            }
            extracted = StorageHelper.poweredExtraction(context.getEnergySource(), inv, what, accepted,
                    context.getActionSource(), Actionable.MODULATE);
            long inserted = facade.insert(what, extracted, Actionable.MODULATE, context.getActionSource());
            if (inserted < extracted) {
                StorageHelper.poweredInsert(context.getEnergySource(), inv, what, extracted - inserted,
                        context.getActionSource(), Actionable.MODULATE);
            }
            return inserted;
        }

        @Override
        public long push(AEKey what, long amount, Actionable mode) {
            if (!(what instanceof GasKey) || amount <= 0) {
                return 0;
            }
            var adjacentHandler = cache.getCapability();
            if (adjacentHandler == null) {
                return 0;
            }
            var facade = new GasMachineExternalStorageFacade(wrap(adjacentHandler, targetPressurePa));
            return facade.insert(what, amount, mode, IActionSource.empty());
        }
    }

    /**
     * Wraps a CCB handler with target-pressure limits. Only handlers that know their own pressure
     * (i.e. {@link GasStorageHandler}) can be limited; other handlers keep their normal behaviour.
     */
    private static final class PressureLimitedGasHandler implements GasHandler {
        private final GasHandler delegate;
        private final long targetPressurePa;

        private PressureLimitedGasHandler(GasHandler delegate, long targetPressurePa) {
            this.delegate = delegate;
            this.targetPressurePa = targetPressurePa;
        }

        @Override
        public int getTanks() {
            return delegate.getTanks();
        }

        @Override
        public GasStack getGasInTank(int tank) {
            return delegate.getGasInTank(tank);
        }

        @Override
        public boolean isGasValid(int tank, GasStack stack) {
            return delegate.isGasValid(tank, stack);
        }

        @Override
        public long fill(GasStack resource, GasAction action) {
            if (!(delegate instanceof GasStorageHandler storage) || resource == null || resource.isEmpty()) {
                return delegate.fill(resource, action);
            }
            long remaining = resource.getAmount();
            long filled = 0;
            for (int tank = 0; tank < storage.getTanks() && remaining > 0; tank++) {
                var compartment = storage.getPressureCompartment(tank);
                if (!compartment.isGasValid(resource)) {
                    continue;
                }
                long limit = fillLimit(compartment);
                if (limit <= 0) {
                    continue;
                }
                long moved = compartment.fill(resource.copyWithAmount(Math.min(limit, remaining)), action);
                filled += moved;
                remaining -= moved;
            }
            return filled;
        }

        @Override
        public GasStack drain(GasStack resource, GasAction action) {
            if (!(delegate instanceof GasStorageHandler storage) || resource == null || resource.isEmpty()) {
                return delegate.drain(resource, action);
            }
            long remaining = resource.getAmount();
            long drained = 0;
            for (int tank = 0; tank < storage.getTanks() && remaining > 0; tank++) {
                var compartment = storage.getPressureCompartment(tank);
                long limit = drainLimit(compartment);
                if (limit <= 0) {
                    continue;
                }
                var drainedStack = compartment.drain(Math.min(limit, remaining), action);
                if (!drainedStack.isEmpty() && GasStack.isSameGasSameComponents(drainedStack, resource)) {
                    drained += drainedStack.getAmount();
                    remaining -= drainedStack.getAmount();
                }
            }
            return drained <= 0 ? GasStack.EMPTY : resource.copyWithAmount(drained);
        }

        @Override
        public GasStack drain(long maxDrain, GasAction action) {
            if (!(delegate instanceof GasStorageHandler storage) || maxDrain <= 0) {
                return delegate.drain(maxDrain, action);
            }
            long remaining = maxDrain;
            long drained = 0;
            GasStack sample = GasStack.EMPTY;
            for (int tank = 0; tank < storage.getTanks() && remaining > 0; tank++) {
                var compartment = storage.getPressureCompartment(tank);
                long limit = drainLimit(compartment);
                if (limit <= 0) {
                    continue;
                }
                var drainedStack = compartment.drain(Math.min(limit, remaining), action);
                if (!drainedStack.isEmpty()) {
                    if (sample.isEmpty()) {
                        sample = drainedStack;
                    }
                    drained += drainedStack.getAmount();
                    remaining -= drainedStack.getAmount();
                }
            }
            return drained <= 0 || sample.isEmpty() ? GasStack.EMPTY : sample.copyWithAmount(drained);
        }

        private long fillLimit(GasPressureCompartment compartment) {
            long maxAmount = compartment.getMaxAmount();
            long stored = compartment.getStoredAmount();
            long capacityLeft = Math.max(0, maxAmount - stored);
            if (compartment.getPressureModel() == GasPressureCompartment.PressureModel.FIXED) {
                return compartment.getPressurePa() >= targetPressurePa ? 0 : capacityLeft;
            }
            long amountAtTarget = GasAmounts.amountFor(compartment.getVolume(), targetPressurePa);
            return Math.min(capacityLeft, Math.max(0, amountAtTarget - stored));
        }

        private long drainLimit(GasPressureCompartment compartment) {
            long stored = compartment.getStoredAmount();
            if (compartment.getPressureModel() == GasPressureCompartment.PressureModel.FIXED) {
                return compartment.getPressurePa() > targetPressurePa ? stored : 0;
            }
            long amountAtTarget = GasAmounts.amountFor(compartment.getVolume(), targetPressurePa);
            return Math.max(0, stored - amountAtTarget);
        }
    }
}
