// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.device.gasinterface;

import net.minecraft.network.chat.Component;

/**
 * Per-slot transfer mode of the gas ME Interface.
 *
 * <p>All slots default to {@link #BOTH}. Changing them is only possible while the slot-control
 * upgrade card is installed; removing the card keeps the stored values but makes the interface treat
 * every slot as {@link #BOTH} again (so reinstalling restores the player's configuration).</p>
 */
public enum GasSlotMode {
    /** Only CCB → slot (the slot never supplies gas). */
    INPUT,
    /** Only slot → CCB (the slot never accepts gas). */
    OUTPUT,
    /** Both directions; the default. */
    BOTH,
    /** No CCB transfer at all (the slot still participates in ME stocking). */
    OFF;

    private static final GasSlotMode[] VALUES = values();

    public static GasSlotMode byIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            return BOTH;
        }
        return VALUES[index];
    }

    public boolean allowsFill() {
        return this == INPUT || this == BOTH;
    }

    public boolean allowsDrain() {
        return this == OUTPUT || this == BOTH;
    }

    public Component getDisplayName() {
        return Component.translatable("gui.appliedcraftedenergistics.slot_mode." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
