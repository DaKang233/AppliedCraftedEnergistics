// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-or-later

package com.appliedcraftedenergistics.device.gasinterface;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.util.ConfigInventory;

import com.appliedcraftedenergistics.device.GasInterfaceBlockEntity;
import com.appliedcraftedenergistics.gas.GasKey;
import com.appliedcraftedenergistics.gas.GasKeyType;
import com.appliedcraftedenergistics.init.ACECards;
import com.appliedcraftedenergistics.init.ACEDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.ty.createcraftedbeginning.api.gas.GasStack;

import org.jetbrains.annotations.Nullable;

/**
 * Stock management of the gas ME Interface: nine config slots (target {@link GenericStack}, amount =
 * target stock) and nine cache slots, refilled from / pushed back to the ME network.
 *
 * <p>This is a deliberately small re-implementation of AE2's {@code InterfaceLogic}: the AE2 class keeps
 * its upgrade inventory private and hard-codes one slot, while this machine needs six. The stocking
 * semantics follow AE2's plan: keep each cache slot at its configured amount, move everything that is
 * unconfigured or in excess back to the network.</p>
 */
public final class GasInterfaceLogic {
    public static final int SLOTS = GasInterfaceSettings.SLOTS;
    /** Per-slot stock ceiling in GU; matches the gas slot capacity registered with AE2 (64 bytes). */
    public static final long MAX_SLOT_AMOUNT = 64_000L;

    private static final String TAG_CONFIG = "config";
    private static final String TAG_STORAGE = "storage";
    private static final String TAG_UPGRADES = "upgrades";
    private static final String TAG_SETTINGS = "gas_interface";
    private static final String TAG_LEGACY_TANK = "tank";

    private final ConfigInventory config;
    private final ConfigInventory storage;
    private final IUpgradeInventory upgrades;
    private final GasInterfaceSettings settings = new GasInterfaceSettings();

    public GasInterfaceLogic(ItemLike machineItem, Runnable inventoryListener, Runnable upgradeListener) {
        this.config = ConfigInventory.configStacks(SLOTS)
                .supportedType(GasKeyType.TYPE)
                .slotFilter(key -> key instanceof GasKey)
                .allowOverstacking(false)
                .changeListener(inventoryListener)
                .build();
        this.storage = ConfigInventory.storage(SLOTS)
                .supportedType(GasKeyType.TYPE)
                .slotFilter(key -> key instanceof GasKey)
                .changeListener(inventoryListener)
                .build();
        this.upgrades = UpgradeInventories.forMachine(machineItem, GasInterfaceBlockEntity.UPGRADE_SLOTS, upgradeListener::run);
    }

    public ConfigInventory getConfig() {
        return config;
    }

    public ConfigInventory getStorage() {
        return storage;
    }

    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public GasInterfaceSettings getSettings() {
        return settings;
    }

    public boolean isSlotControlCardInstalled() {
        return findSlotControlCard() != null;
    }

    /**
     * Re-applies the per-slot modes stored on the installed slot-control card. A fresh card (no stored
     * modes) puts every slot back to bidirectional; a configured card restores its saved modes.
     */
    public void onUpgradesChanged() {
        var card = findSlotControlCard();
        if (card == null) {
            return;
        }
        var encoded = card.get(ACEDataComponents.SLOT_MODES.get());
        if (encoded == null || encoded.isBlank()) {
            setAllModes(GasSlotMode.BOTH);
            return;
        }
        var parts = encoded.split(",");
        for (int slot = 0; slot < SLOTS; slot++) {
            int ordinal = 0;
            if (slot < parts.length) {
                try {
                    ordinal = Integer.parseInt(parts[slot].trim());
                } catch (NumberFormatException ignored) {
                    ordinal = 0;
                }
            }
            settings.get(slot).setMode(GasSlotMode.byIndex(ordinal));
        }
    }

    /** Saves the current per-slot modes onto the installed slot-control card. */
    public void saveModesToCard() {
        var card = findSlotControlCard();
        if (card == null) {
            return;
        }
        var builder = new StringBuilder();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (slot > 0) {
                builder.append(',');
            }
            builder.append(settings.get(slot).getMode().ordinal());
        }
        card.set(ACEDataComponents.SLOT_MODES.get(), builder.toString());
    }

    @Nullable
    private ItemStack findSlotControlCard() {
        for (int slot = 0; slot < upgrades.size(); slot++) {
            var stack = upgrades.getStackInSlot(slot);
            if (stack.is(ACECards.SLOT_CONTROL_CARD)) {
                return stack;
            }
        }
        return null;
    }

    private void setAllModes(GasSlotMode mode) {
        for (int slot = 0; slot < SLOTS; slot++) {
            settings.get(slot).setMode(mode);
        }
    }

    /** The mode CCB should see for a slot right now (all BOTH without the slot-control card). */
    public GasSlotMode getEffectiveMode(int slot) {
        return settings.getEffectiveMode(slot, isSlotControlCardInstalled());
    }

    /** Target stock amount of a slot, clamped to the interface's per-slot ceiling. */
    public long getTargetAmount(int slot) {
        var target = config.getStack(slot);
        if (target == null || !(target.what() instanceof GasKey)) {
            return 0;
        }
        return Math.clamp(target.amount(), 0, MAX_SLOT_AMOUNT);
    }

    /**
     * Volume presented to CCB for a slot: {@code targetAmount / targetPressureAtm} litres, so that a
     * full slot sits at exactly the configured pressure. Unconfigured slots present the full slot
     * capacity at the default pressure so that CCB pipes can hand gas in (the network then picks it up).
     */
    public long getSlotVolumeLiters(int slot) {
        long amount = getTargetAmount(slot);
        if (amount <= 0) {
            amount = MAX_SLOT_AMOUNT;
        }
        int atm = Math.max(1, settings.get(slot).getEffectivePressureAtm());
        return Math.max(1, amount / atm);
    }

    public int getSlotPressureAtm(int slot) {
        return Math.max(1, settings.get(slot).getEffectivePressureAtm());
    }

    /** Keeps every cache slot at its configured amount; pushes unconfigured/excess gas to the network. */
    public void tick(IGrid grid, IActionSource source) {
        var storageService = grid.getStorageService();
        var network = storageService.getInventory();
        IEnergyService energy = grid.getEnergyService();

        for (int slot = 0; slot < SLOTS; slot++) {
            var target = config.getStack(slot);
            AEKey targetKey = target != null ? target.what() : null;
            long targetAmount = targetKey instanceof GasKey ? Math.clamp(target.amount(), 0, MAX_SLOT_AMOUNT) : 0;

            // 1) Move out anything that is not the configured gas, or is in excess of the target.
            var current = storage.getStack(slot);
            if (current != null) {
                boolean sameKey = targetKey != null && current.what().equals(targetKey);
                long keep = sameKey ? Math.min(current.amount(), targetAmount) : 0;
                if (current.amount() > keep) {
                    long excess = current.amount() - keep;
                    long extracted = storage.extract(slot, current.what(), excess, Actionable.MODULATE);
                    if (extracted > 0) {
                        StorageHelper.poweredInsert(energy, network, current.what(), extracted, source,
                                Actionable.MODULATE);
                    }
                }
            }

            // 2) Restock the configured gas from the network.
            if (targetKey instanceof GasKey && targetAmount > 0) {
                var after = storage.getStack(slot);
                long have = after != null && after.what().equals(targetKey) ? after.amount() : 0;
                long need = targetAmount - have;
                if (need > 0) {
                    long available = StorageHelper.poweredExtraction(energy, network, targetKey, need, source,
                            Actionable.SIMULATE);
                    long accepted = storage.insert(slot, targetKey, Math.min(need, available), Actionable.SIMULATE);
                    long move = Math.min(need, Math.min(available, accepted));
                    if (move > 0) {
                        long extracted = StorageHelper.poweredExtraction(energy, network, targetKey, move, source,
                                Actionable.MODULATE);
                        long inserted = storage.insert(slot, targetKey, extracted, Actionable.MODULATE);
                        if (inserted < extracted) {
                            // Never void gas: return whatever the cache refused.
                            StorageHelper.poweredInsert(energy, network, targetKey, extracted - inserted, source,
                                    Actionable.MODULATE);
                        }
                    }
                }
            }
        }
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        config.writeToChildTag(tag, TAG_CONFIG, registries);
        storage.writeToChildTag(tag, TAG_STORAGE, registries);
        upgrades.writeToNBT(tag, TAG_UPGRADES, registries);
        var settingsTag = new CompoundTag();
        settings.write(settingsTag);
        tag.put(TAG_SETTINGS, settingsTag);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        config.readFromChildTag(tag, TAG_CONFIG, registries);
        storage.readFromChildTag(tag, TAG_STORAGE, registries);
        upgrades.readFromNBT(tag, TAG_UPGRADES, registries);
        if (tag.contains(TAG_SETTINGS, Tag.TAG_COMPOUND)) {
            settings.read(tag.getCompound(TAG_SETTINGS));
        }
        migrateLegacyTank(tag, registries);
    }

    /**
     * 0.1.x stored a single 8 kL buffer under {@code tank}. Move its contents into the first free cache
     * slot so that upgrading a world does not lose gas; if all nine slots are occupied, the gas is
     * dropped with a log entry (the same rule as a full interface).
     */
    private void migrateLegacyTank(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_LEGACY_TANK, Tag.TAG_COMPOUND)) {
            return;
        }
        var tankTag = tag.getCompound(TAG_LEGACY_TANK);
        GasStack gas = GasStack.parseOptional(registries, tankTag.getCompound("gas"));
        if (gas.isEmpty()) {
            return;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (storage.getStack(slot) == null) {
                storage.setStack(slot, new GenericStack(new GasKey(gas.getGasType()), gas.getAmount()));
                com.appliedcraftedenergistics.AppliedCraftedEnergistics.LOGGER.info(
                        "Migrated {} GU of legacy interface buffer gas into slot {}", gas.getAmount(), slot);
                return;
            }
        }
        com.appliedcraftedenergistics.AppliedCraftedEnergistics.LOGGER.warn(
                "Legacy ME gas interface buffer held {} GU but all nine slots are full; the gas was dropped",
                gas.getAmount());
    }

    @Nullable
    public GenericStack getStorageStack(int slot) {
        return storage.getStack(slot);
    }
}
