// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.gas;

import net.ty.createcraftedbeginning.api.gas.GasPressure;
import net.ty.createcraftedbeginning.api.gas.GasPressureLimits;

/**
 * The single source of truth for the AE byte ↔ Create: Crafted Beginning gas unit (GU) conversion.
 *
 * <h2>The rule</h2>
 * <b>1 AE byte stores {@value #GU_PER_BYTE} GU</b> — that is 1,000 liters of gas at CCB's reference
 * pressure of 100 kPa (approximately 1 m³ of gas at one atmosphere).
 *
 * <p>This mirrors AE2's own fluid ratio, where one byte stores 8,000 mB (8 buckets). Because CCB
 * defines 1 GU as "one liter at 100 kPa", 1,000 GU is exactly one cubic meter at reference pressure,
 * which makes the numbers easy to reason about in-game.</p>
 *
 * <h2>Why it is not configurable</h2>
 * Storage cells persist their contents in bytes. Changing the ratio would silently rescale every cell
 * in every existing world, so it is a fixed part of the mod contract. Server owners who want more or
 * less storage should pick a different cell tier instead.
 */
public final class GasAmounts {
    /** Gas units per AE byte. Fixed — see the class documentation. */
    public static final int GU_PER_BYTE = 1_000;
    /** Terminal display unit. 1 unit = 1,000 GU, displayed as "kGU". */
    public static final int GU_PER_UNIT = 1_000;
    /** Amount moved in one AE2 operation (buses, IO port, crafting). */
    public static final int GU_PER_OPERATION = 1_000;

    /** CCB's reference pressure: 100 kPa. */
    public static final long REFERENCE_PRESSURE_PA = GasPressure.REFERENCE_PRESSURE_PA;
    /** CCB's safe operating pressure: 16 atm. */
    public static final long SAFE_PRESSURE_PA = GasPressureLimits.SAFE_PRESSURE_PA;
    /** CCB's hard physical ceiling: 24 atm. Nothing may exceed it. */
    public static final long HARD_PRESSURE_PA = GasPressureLimits.HARD_PRESSURE_PA;

    private GasAmounts() {
    }

    /** Gas storage capacity of a cell with the given number of bytes. */
    public static long capacityForBytes(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        if (bytes > Long.MAX_VALUE / GU_PER_BYTE) {
            return Long.MAX_VALUE;
        }
        return bytes * GU_PER_BYTE;
    }

    /** How many bytes the given amount of gas occupies (rounded up). */
    public static long bytesForAmount(long gasUnits) {
        if (gasUnits <= 0) {
            return 0;
        }
        return (gasUnits + GU_PER_BYTE - 1) / GU_PER_BYTE;
    }

    /** Gas amount that fills a vessel of the given volume at the given pressure. */
    public static long amountFor(long volumeLiters, long pressurePa) {
        return GasPressure.amount(volumeLiters, pressurePa);
    }

    /** Pressure of the given amount of gas inside a vessel of the given volume. */
    public static long pressureFor(long gasUnits, long volumeLiters) {
        return GasPressure.pressure(gasUnits, volumeLiters);
    }

    /** Maximum amount a vessel can hold before exceeding its rated pressure. */
    public static long maxAmountFor(long volumeLiters, long maxPressurePa) {
        return GasPressure.amount(volumeLiters, maxPressurePa);
    }

    /**
     * Volume (liters) a vessel would need to hold {@code gasUnits} at the given pressure.
     * Inverse of {@link #amountFor(long, long)}; used to show a gas cell's virtual vessel volume.
     */
    public static long volumeFor(long gasUnits, long pressurePa) {
        if (gasUnits <= 0 || pressurePa <= 0) {
            return 0;
        }
        if (gasUnits > Long.MAX_VALUE / REFERENCE_PRESSURE_PA) {
            return Long.MAX_VALUE;
        }
        return gasUnits * REFERENCE_PRESSURE_PA / pressurePa;
    }

    /**
     * How much more gas can be pushed into a vessel that currently holds {@code stored} GU.
     * This is the "pressure problem" in one line: an AE network can only inject what the receiving
     * vessel has pressure headroom for.
     */
    public static long headroomFor(long stored, long volumeLiters, long maxPressurePa) {
        long max = maxAmountFor(volumeLiters, maxPressurePa);
        return Math.max(0, max - Math.max(0, stored));
    }

    /** Human readable gas amount, e.g. {@code "1,024 kGU"}. */
    public static String format(long gasUnits) {
        if (gasUnits < GU_PER_UNIT) {
            return gasUnits + " GU";
        }
        long units = gasUnits / GU_PER_UNIT;
        long remainder = gasUnits % GU_PER_UNIT;
        if (remainder == 0) {
            return String.format(java.util.Locale.ROOT, "%,d kGU", units);
        }
        return String.format(java.util.Locale.ROOT, "%,d.%03d kGU", units, remainder);
    }

    /** Human readable volume at reference pressure, e.g. {@code "1,024 m³ @100 kPa"}. */
    public static String formatVolume(long gasUnits) {
        if (gasUnits < GU_PER_UNIT) {
            return gasUnits + " L @100 kPa";
        }
        return String.format(java.util.Locale.ROOT, "%,d m³ @100 kPa", gasUnits / GU_PER_UNIT);
    }

    /** Human readable pressure, delegating to CCB's own formatter. */
    public static String formatPressure(long pressurePa) {
        return GasPressure.format(pressurePa);
    }

    /** True if the given pressure is above CCB's safe rating (but still physically representable). */
    public static boolean isOverpressure(long pressurePa) {
        return pressurePa > SAFE_PRESSURE_PA;
    }

    /** Clamps a pressure to CCB's hard physical ceiling. */
    public static long clampPressure(long pressurePa) {
        return Math.clamp(pressurePa, 0, HARD_PRESSURE_PA);
    }
}
