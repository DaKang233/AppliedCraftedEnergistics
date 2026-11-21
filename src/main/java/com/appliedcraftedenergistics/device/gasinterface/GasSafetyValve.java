// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.device.gasinterface;

import com.appliedcraftedenergistics.AppliedCraftedEnergistics;
import com.appliedcraftedenergistics.device.GasInterfaceBlockEntity;
import com.appliedcraftedenergistics.gas.GasAmounts;
import com.appliedcraftedenergistics.init.ACECards;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.ty.createcraftedbeginning.api.gas.GasAction;
import net.ty.createcraftedbeginning.api.gasreleasehandlers.GasReleaseCause;
import net.ty.createcraftedbeginning.gas.release.GasReleaseRequest;
import net.ty.createcraftedbeginning.gas.release.GasReleaseService;

/**
 * Safety-valve behaviour: when a cache slot's real pressure reaches the danger threshold, vent its gas
 * through Create: Crafted Beginning's own release service.
 *
 * <p>The release is a genuine CCB release (radial, {@link GasReleaseCause#OVERFLOW}), so the game gets
 * the gas-specific environmental effects, including pushing entities. Gas is only removed from the
 * slot <b>after</b> the release call succeeded, so a failed release can never silently delete gas.</p>
 *
 * <p>The threshold sits between CCB's safe line (16 atm) and hard ceiling (24 atm), which is also the
 * maximum pressure the slot vessel accepts. A slot configured for the normal supply pressure therefore
 * never oscillates between restocking and venting.</p>
 */
public final class GasSafetyValve {
    /** Vent above this pressure (20 atm). */
    public static final long VENT_THRESHOLD_PA = 20L * GasAmounts.REFERENCE_PRESSURE_PA;
    /** Gas released per attempt; matches CCB's default release effect interval. */
    public static final long VENT_AMOUNT = 1_000L;

    private GasSafetyValve() {
    }

    public static void tick(GasInterfaceBlockEntity host, Level level, BlockPos pos) {
        if (!host.getLogic().getUpgrades().isInstalled(ACECards.SAFETY_VALVE_CARD)) {
            return;
        }

        var view = host.getGasView();
        for (int slot = 0; slot < GasInterfaceLogic.SLOTS; slot++) {
            var compartment = view.getPressureCompartment(slot);
            long pressure = compartment.getPressurePa();
            if (pressure < VENT_THRESHOLD_PA) {
                continue;
            }
            var gas = compartment.getGasStack();
            if (gas.isEmpty()) {
                continue;
            }

            long amount = Math.min(VENT_AMOUNT, gas.getAmount());
            var drained = compartment.drain(amount, GasAction.SIMULATE);
            if (drained.isEmpty()) {
                continue;
            }

            try {
                var result = GasReleaseService.release(level,
                        GasReleaseRequest.radial(drained, pos, GasReleaseCause.OVERFLOW, pressure));
                if (result.released()) {
                    // Commit only what CCB actually released; never delete gas on a failed call.
                    compartment.drain(drained.getAmount(), GasAction.EXECUTE);
                }
            } catch (RuntimeException e) {
                AppliedCraftedEnergistics.LOGGER.warn(
                        "Safety valve failed to release {} GU of gas at {} (pressure {} Pa)",
                        drained.getAmount(), pos, pressure, e);
            }
        }
    }
}
