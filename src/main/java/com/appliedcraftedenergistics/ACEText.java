// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Central place for this mod's translatable text, so no literal strings leak into the UI. */
public final class ACEText {
    private ACEText() {
    }

    // Generic -----------------------------------------------------------------------------------
    public static final Component GASES = Component.translatable("appliedcraftedenergistics.key_type.gas");
    public static final Component EXTERNAL_GAS_STORAGE = Component
            .translatable("gui.appliedcraftedenergistics.external_gas_storage");

    // Storage cells -----------------------------------------------------------------------------
    public static final Component CELL_PRESSURE_NOTE = Component
            .translatable("tooltip.appliedcraftedenergistics.cell.pressure_note");
    public static final Component CELL_CARDED_NOTE = Component
            .translatable("tooltip.appliedcraftedenergistics.cell.carded_note");
    public static final Component CELL_OVERFILLED_NOTE = Component
            .translatable("tooltip.appliedcraftedenergistics.cell.overfilled_note");

    public static MutableComponent cellCapacity(long capacityGu) {
        return Component.translatable("tooltip.appliedcraftedenergistics.cell.capacity",
                com.appliedcraftedenergistics.gas.GasAmounts.format(capacityGu));
    }

    public static MutableComponent cellVolume(long capacityGu) {
        return Component.translatable("tooltip.appliedcraftedenergistics.cell.volume",
                com.appliedcraftedenergistics.gas.GasAmounts.formatVolume(capacityGu));
    }

    public static MutableComponent cellRatedPressure(int atm) {
        return Component.translatable("tooltip.appliedcraftedenergistics.cell.rated_pressure", atm);
    }

    public static MutableComponent cellVirtualVolume(long liters) {
        return Component.translatable("tooltip.appliedcraftedenergistics.cell.virtual_volume",
                String.format(java.util.Locale.ROOT, "%,d L", liters));
    }

    public static MutableComponent cellTypes(int types) {
        return Component.translatable("tooltip.appliedcraftedenergistics.cell.types", types);
    }

    // ME Gas Interface --------------------------------------------------------------------------
    public static final Component INTERFACE_TITLE = Component
            .translatable("block.appliedcraftedenergistics.me_gas_interface");
    public static final Component INTERFACE_BUFFER = Component.translatable("gui.appliedcraftedenergistics.interface.buffer");
    public static final Component INTERFACE_PRESSURE = Component
            .translatable("gui.appliedcraftedenergistics.interface.pressure");
    public static final Component INTERFACE_MODE = Component.translatable("gui.appliedcraftedenergistics.interface.mode");
    public static final Component INTERFACE_OVERPRESSURE = Component
            .translatable("gui.appliedcraftedenergistics.interface.overpressure");
    public static final Component INTERFACE_BLOCKED = Component
            .translatable("gui.appliedcraftedenergistics.interface.blocked");

    public static final Component MODE_IMPORT = Component.translatable("gui.appliedcraftedenergistics.mode.import");
    public static final Component MODE_EXPORT = Component.translatable("gui.appliedcraftedenergistics.mode.export");
    public static final Component MODE_BOTH = Component.translatable("gui.appliedcraftedenergistics.mode.both");
    public static final Component MODE_OFF = Component.translatable("gui.appliedcraftedenergistics.mode.off");

    public static final String MODE_KEY_IMPORT = "gui.appliedcraftedenergistics.mode.import";
    public static final String MODE_KEY_EXPORT = "gui.appliedcraftedenergistics.mode.export";
    public static final String MODE_KEY_BOTH = "gui.appliedcraftedenergistics.mode.both";
    public static final String MODE_KEY_OFF = "gui.appliedcraftedenergistics.mode.off";

    public static MutableComponent gu(long gasUnits) {
        return Component.literal(com.appliedcraftedenergistics.gas.GasAmounts.format(gasUnits));
    }
}
