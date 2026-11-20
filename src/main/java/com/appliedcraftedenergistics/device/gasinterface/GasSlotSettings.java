// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device.gasinterface;

import net.minecraft.nbt.CompoundTag;

/**
 * The per-slot extras of the gas ME Interface that AE2's own interface config does not have:
 * the target supply pressure and the transfer mode.
 *
 * <p>The target <b>amount</b> is not stored here: it is the {@code GenericStack.amount} of the
 * interface's config slot, which already has AE2's full "set amount" workflow. This class only adds
 * the two extra knobs and the data that must survive a world save.</p>
 */
public final class GasSlotSettings {
    /** Pressure used for a slot that has no explicit pressure: CCB's reference pressure. */
    public static final int DEFAULT_PRESSURE_ATM = 1;
    /** Upper bound a player can dial in; CCB's safe operating pressure (the hard ceiling is 24 atm). */
    public static final int MAX_PRESSURE_ATM = 16;

    private static final String TAG_MODE = "mode";
    private static final String TAG_PRESSURE = "pressure";
    private static final String TAG_EXPLICIT = "explicit";

    private GasSlotMode mode = GasSlotMode.BOTH;
    private int targetPressureAtm = DEFAULT_PRESSURE_ATM;
    private boolean pressureExplicit;

    public GasSlotSettings copy() {
        var copy = new GasSlotSettings();
        copy.mode = mode;
        copy.targetPressureAtm = targetPressureAtm;
        copy.pressureExplicit = pressureExplicit;
        return copy;
    }

    public GasSlotMode getMode() {
        return mode;
    }

    public void setMode(GasSlotMode mode) {
        this.mode = mode == null ? GasSlotMode.BOTH : mode;
    }

    public int getTargetPressureAtm() {
        return targetPressureAtm;
    }

    public void setTargetPressureAtm(int atm) {
        this.targetPressureAtm = Math.clamp(atm, 0, MAX_PRESSURE_ATM);
    }

    public boolean isPressureExplicit() {
        return pressureExplicit;
    }

    public void setPressureExplicit(boolean explicit) {
        this.pressureExplicit = explicit;
    }

    /** Pressure actually presented to CCB: the explicit value, or the default when disabled. */
    public int getEffectivePressureAtm() {
        return pressureExplicit ? targetPressureAtm : DEFAULT_PRESSURE_ATM;
    }

    public boolean isSameAs(GasSlotSettings other) {
        return other != null
                && mode == other.mode
                && targetPressureAtm == other.targetPressureAtm
                && pressureExplicit == other.pressureExplicit;
    }

    public void write(CompoundTag tag) {
        tag.putInt(TAG_MODE, mode.ordinal());
        tag.putInt(TAG_PRESSURE, targetPressureAtm);
        tag.putBoolean(TAG_EXPLICIT, pressureExplicit);
    }

    public static GasSlotSettings read(CompoundTag tag) {
        var settings = new GasSlotSettings();
        settings.setMode(GasSlotMode.byIndex(tag.getInt(TAG_MODE)));
        settings.setTargetPressureAtm(tag.contains(TAG_PRESSURE) ? tag.getInt(TAG_PRESSURE) : DEFAULT_PRESSURE_ATM);
        settings.setPressureExplicit(tag.getBoolean(TAG_EXPLICIT));
        return settings;
    }
}
