// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device;

import com.appliedcraftedenergistics.ACEText;

import net.minecraft.network.chat.Component;

/**
 * The direction in which the ME Gas Interface moves gas between the ME network and its own pressure
 * buffer.
 *
 * <p>The buffer is a real Create: Crafted Beginning vessel (see {@code core.GasBufferTank}); CCB pipes
 * push gas into it and take gas out of it on their own. This enum only decides what the <b>AE side</b>
 * does with the buffer every tick:</p>
 *
 * <ul>
 *   <li>{@link #IMPORT} — drain the buffer into the ME network (the default: collect what CCB
 *       delivered).</li>
 *   <li>{@link #EXPORT} — fill the buffer from the ME network up to the configured supply pressure,
 *       so CCB pipes can carry it away.</li>
 *   <li>{@link #BOTH} — act as a pressure regulator: import while the buffer is above the supply
 *       pressure, export while it is below it.</li>
 *   <li>{@link #OFF} — do nothing; the buffer is still a normal CCB tank.</li>
 * </ul>
 */
public enum GasInterfaceMode {
    IMPORT(ACEText.MODE_IMPORT),
    EXPORT(ACEText.MODE_EXPORT),
    BOTH(ACEText.MODE_BOTH),
    OFF(ACEText.MODE_OFF);

    private final Component displayName;

    GasInterfaceMode(Component displayName) {
        this.displayName = displayName;
    }

    /** Translatable name shown in the GUI and used as the mode button label. */
    public Component getDisplayName() {
        return displayName;
    }

    /** True when the AE side may pull gas out of the buffer. */
    public boolean canImport() {
        return this == IMPORT || this == BOTH;
    }

    /** True when the AE side may push gas into the buffer. */
    public boolean canExport() {
        return this == EXPORT || this == BOTH;
    }

    /** Next mode in the GUI cycle order (server-side helper). */
    public GasInterfaceMode cycle() {
        GasInterfaceMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * Safe lookup for NBT/network input: unknown names fall back to {@link #IMPORT} instead of
     * throwing, so a corrupt or hand-edited save never breaks a block entity.
     */
    public static GasInterfaceMode byName(String name) {
        if (name != null) {
            for (GasInterfaceMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
        }
        return IMPORT;
    }
}
