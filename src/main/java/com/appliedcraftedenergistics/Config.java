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
        // --- ME Gas Interface -------------------------------------------------------------------
        public final ModConfigSpec.IntValue interfaceBufferVolumeLiters;
        public final ModConfigSpec.IntValue interfaceSupplyPressureKpa;
        public final ModConfigSpec.IntValue interfaceTransferRate;
        public final ModConfigSpec.DoubleValue interfaceEnergyPerKiloGasUnit;
        public final ModConfigSpec.DoubleValue interfaceIdlePower;
        public final ModConfigSpec.IntValue gasP2pBufferVolumeLiters;

        Common(ModConfigSpec.Builder builder) {
            builder.comment("Settings for the ME Gas Interface").push("gas_interface");
            this.interfaceBufferVolumeLiters = builder
                    .comment("Volume of the interface's internal pressure buffer in liters.",
                            "The buffer is a real vessel seen by Create: Crafted Beginning's gas pipes;",
                            "gas can only be moved while it fits inside this volume at the allowed pressure.")
                    .defineInRange("bufferVolumeLiters", 8_000, 100, 4_000_000);
            this.interfaceSupplyPressureKpa = builder
                    .comment("Pressure (in kPa) the interface fills its buffer to before pushing gas into",
                            "the adjacent gas network. The CCB reference pressure is 100 kPa, the safe",
                            "operating limit is 1,600 kPa and the hard physical limit is 2,400 kPa.")
                    .defineInRange("supplyPressureKpa", 100, 0, 2_400);
            this.interfaceTransferRate = builder
                    .comment("Maximum gas moved per tick between the ME network and the buffer, in GU.",
                            "1 kGU = 1 byte of AE storage.")
                    .defineInRange("transferRate", 1_000, 1, 1_000_000);
            this.interfaceEnergyPerKiloGasUnit = builder
                    .comment("AE energy spent per kGU moved between the network and the buffer.")
                    .defineInRange("energyPerKiloGasUnit", 1.0, 0.0, 1000.0);
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
