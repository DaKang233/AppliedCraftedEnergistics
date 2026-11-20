// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Common configuration for Applied Crafted Energistics.
 *
 * <p>Note: the AE byte ↔ gas unit ratio is deliberately <b>not</b> configurable. It is a fixed part of
 * the mod's contract with existing worlds (see {@link com.appliedcraftedenergistics.gas.GasAmounts}); making it
 * configurable would silently rescale the contents of every existing storage cell.</p>
 *
 * <p>Since 0.2.0 the ME Gas Interface's cache slots are configured per slot in its GUI (target amount,
 * target pressure, mode), so the old single-buffer settings are gone. Only the idle drain and the gas
 * P2P vessel size remain configurable.</p>
 */
public final class Config {
    public static final Common COMMON;
    public static final ModConfigSpec SPEC;

    static {
        Pair<Common, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private Config() {
    }

    public static final class Common {
        public final ModConfigSpec.DoubleValue interfaceIdlePower;
        public final ModConfigSpec.IntValue gasP2pBufferVolumeLiters;

        Common(ModConfigSpec.Builder builder) {
            builder.comment("Settings for the ME Gas Interface").push("gas_interface");
            this.interfaceIdlePower = builder
                    .comment("Idle AE power drain of the interface, in AE/t.")
                    .defineInRange("idlePower", 8.0, 0.0, 10_000.0);
            builder.pop();

            builder.comment("Settings for the Gas P2P Tunnel").push("gas_p2p");
            this.gasP2pBufferVolumeLiters = builder
                    .comment("Volume in liters of the small pressure vessel each Gas P2P Tunnel presents",
                            "to the gas pipes on its face. It is rated to CCB's safe pressure (16 atm),",
                            "so a tunnel can never be the reason a player's tank builds up rupture stress.",
                            "The buffer is what lets CCB's solver move gas through the tunnel at all:",
                            "an endpoint without a real volume is connected but never transfers.")
                    .defineInRange("bufferVolumeLiters", 4_000, 100, 1_000_000);
            builder.pop();
        }
    }
}
