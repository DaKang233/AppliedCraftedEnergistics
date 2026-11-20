// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device.gasinterface;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The nine per-slot settings of the gas ME Interface (target pressure + mode). Serialised under a
 * single NBT key so that worlds saved before 0.2.0 simply load the defaults.
 *
 * <p>When the slot-control upgrade card is not installed, {@link #getEffectiveMode(int)} returns
 * {@link GasSlotMode#BOTH} for every slot, but the stored per-slot values are kept untouched.</p>
 */
public final class GasInterfaceSettings {
    /** Number of config/cache slots of the interface, matching AE2's own interface. */
    public static final int SLOTS = 9;

    private static final String TAG_SLOT = "Slot";
    private static final String TAG_SLOTS = "slots";

    private final GasSlotSettings[] slots = new GasSlotSettings[SLOTS];

    public GasInterfaceSettings() {
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = new GasSlotSettings();
        }
    }

    public GasSlotSettings get(int slot) {
        return slots[slot];
    }

    /** Mode the CCB side should see right now; gated by the slot-control card. */
    public GasSlotMode getEffectiveMode(int slot, boolean controlCardInstalled) {
        return controlCardInstalled ? slots[slot].getMode() : GasSlotMode.BOTH;
    }

    public void write(CompoundTag parent) {
        var list = new ListTag();
        for (var slot : slots) {
            var tag = new CompoundTag();
            slot.write(tag);
            list.add(tag);
        }
        parent.put(TAG_SLOTS, list);
    }

    public void read(CompoundTag parent) {
        if (!parent.contains(TAG_SLOTS, Tag.TAG_LIST)) {
            return; // pre-0.2.0 world: keep defaults
        }
        var list = parent.getList(TAG_SLOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < SLOTS && i < list.size(); i++) {
            slots[i] = GasSlotSettings.read(list.getCompound(i));
        }
    }
}
