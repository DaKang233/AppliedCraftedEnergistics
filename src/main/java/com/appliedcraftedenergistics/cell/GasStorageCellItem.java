// Copyright (c) 2026 DaKang233.
// SPDX-License-Identifier: LGPL-3.0-only

package com.appliedcraftedenergistics.cell;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.storage.BasicStorageCell;
import appeng.items.storage.StorageTier;
import appeng.util.Platform;
import com.appliedcraftedenergistics.ACEText;
import com.appliedcraftedenergistics.gas.GasAmounts;
import com.appliedcraftedenergistics.gas.GasKeyType;
import com.appliedcraftedenergistics.init.ACECards;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * An AE2 storage cell that stores Create: Crafted Beginning gases.
 *
 * <p>It is a plain {@link BasicStorageCell} on purpose: partitioning, ME drive support, cell workbench,
 * disassembly and the terminal UI all come from AE2 for free. The only gas-specific parts are the key
 * type ({@link GasKeyType}, which carries the byte ↔ GU ratio) and the upgrade cards below.</p>
 *
 * <h2>The cell as a pressure vessel</h2>
 * A gas cell is modelled as a virtual CCB vessel:
 * {@code capacity(GU) = volume(L) × ratedPressure(Pa) / 100,000}, with a base rating of 16 atm
 * (CCB's safe pressure). That keeps every number in the cell consistent with the gas pipes, and makes
 * the upgrade cards expressible in CCB's own terms:
 * <ul>
 *   <li><b>Volume card</b> — +50% virtual volume per card, ×4 max;</li>
 *   <li><b>Pressure card</b> — rated pressure 16 → 24 atm (CCB's hard ceiling), ×1.5 capacity, ×1 max;</li>
 *   <li><b>Type expansion card</b> — +4 storable gas types per card, ×2 max;</li>
 *   <li><b>Relief valve card</b> — when a card change shrinks the cell below its contents, the excess is
 *       vented instead of leaving the cell permanently over-filled.</li>
 * </ul>
 * AE2 still expresses capacity in <i>bytes</i>, so a carded cell reports more bytes than its tier;
 * the tooltip spells out the equivalent volume/pressure so this is not mistaken for a downgrade.
 */
public class GasStorageCellItem extends BasicStorageCell {
    /** Total gas types a cell can hold before any type expansion card. */
    public static final int BASE_TYPES = 5;
    /** Extra gas types per type expansion card. */
    public static final int TYPES_PER_CARD = 4;
    /** AE2's own cap on types per cell ({@code BasicCellInventory.MAX_ITEM_TYPES}). */
    public static final int MAX_TOTAL_TYPES = 63;

    public static final int MAX_PRESSURE_CARDS = 1;
    public static final int MAX_VOLUME_CARDS = 4;
    public static final int MAX_TYPE_CARDS = 2;
    public static final int UPGRADE_SLOTS = 5;

    /** Base rated pressure in atm: CCB's safe operating pressure. */
    public static final int BASE_PRESSURE_ATM = 16;
    /** Rated pressure with a pressure card: CCB's hard ceiling. */
    public static final int PRESSURIZED_ATM = 24;
    public static final double PRESSURE_CAPACITY_MULTIPLIER = (double) PRESSURIZED_ATM / BASE_PRESSURE_ATM;
    /** Capacity multiplier added by each volume card. */
    public static final double VOLUME_CARD_BONUS = 0.5;

    private final StorageTier tier;

    public GasStorageCellItem(Properties properties, StorageTier tier) {
        super(properties,
                tier.idleDrain(),
                tier.bytes() / 1024,
                tier.bytes() / 128,
                BASE_TYPES,
                GasKeyType.TYPE);
        this.tier = tier;
    }

    public StorageTier getTier() {
        return tier;
    }

    // ---------------------------------------------------------------------------------------------
    // Card-driven capacity
    // ---------------------------------------------------------------------------------------------

    /** Bytes of the bare tier, before any card. */
    public int getBaseBytes() {
        return tier.bytes();
    }

    public int getVolumeCards(ItemStack stack) {
        return getUpgrades(stack).getInstalledUpgrades(ACECards.VOLUME_CARD.get());
    }

    public int getPressureCards(ItemStack stack) {
        return getUpgrades(stack).getInstalledUpgrades(ACECards.PRESSURE_CARD.get());
    }

    public int getTypeCards(ItemStack stack) {
        return getUpgrades(stack).getInstalledUpgrades(ACECards.TYPE_EXPANSION_CARD.get());
    }

    public boolean hasReliefValve(ItemStack stack) {
        return getUpgrades(stack).isInstalled(ACECards.RELIEF_VALVE_CARD.get());
    }

    /** Rated pressure of this cell in atm (16 base, 24 with a pressure card). */
    public int getRatedPressureAtm(ItemStack stack) {
        return getPressureCards(stack) > 0 ? PRESSURIZED_ATM : BASE_PRESSURE_ATM;
    }

    /** Virtual vessel volume in liters implied by the current byte capacity and rated pressure. */
    public long getVirtualVolumeLiters(ItemStack stack) {
        return GasAmounts.volumeFor((long) getBytes(stack) * GasAmounts.GU_PER_BYTE, getRatedPressureAtm(stack) * 100_000L);
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        double multiplier = 1.0 + VOLUME_CARD_BONUS * Math.min(MAX_VOLUME_CARDS, getVolumeCards(cellItem));
        if (getPressureCards(cellItem) > 0) {
            multiplier *= PRESSURE_CAPACITY_MULTIPLIER;
        }
        return (int) Math.max(1L, Math.round(getBaseBytes() * multiplier));
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        int types = BASE_TYPES + TYPES_PER_CARD * Math.min(MAX_TYPE_CARDS, getTypeCards(cellItem));
        return Math.min(MAX_TOTAL_TYPES, types);
    }

    /**
     * Five upgrade slots (AE2 gives three to non-item cells) and the relief-valve hook: AE2 invokes this
     * callback whenever the cell's upgrade inventory changes in the cell workbench.
     */
    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, UPGRADE_SLOTS, (stack, inventory) -> onUpgradesChanged(stack));
    }

    private static void onUpgradesChanged(ItemStack stack) {
        if (!(stack.getItem() instanceof GasStorageCellItem cell) || !cell.hasReliefValve(stack)) {
            return;
        }
        ventOverfill(stack, cell);
    }

    /**
     * Vents the amount that no longer fits after an upgrade change shrank the cell. Without this the cell
     * would keep more GU than its byte capacity allows, which is confusing in the tooltip and blocks all
     * further inserts until manually emptied.
     */
    private static void ventOverfill(ItemStack stack, GasStorageCellItem cell) {
        StorageCell inventory = StorageCells.getCellInventory(stack, null);
        if (inventory == null) {
            return;
        }
        long capacity = (long) cell.getBytes(stack) * GasAmounts.GU_PER_BYTE;
        KeyCounter stored = inventory.getAvailableStacks();
        long total = 0;
        for (var entry : stored) {
            total += entry.getLongValue();
        }
        long excess = total - capacity;
        if (excess <= 0) {
            return;
        }
        for (var entry : stored) {
            if (excess <= 0) {
                break;
            }
            long taken = Math.min(excess, entry.getLongValue());
            if (taken <= 0) {
                continue;
            }
            long extracted = inventory.extract(entry.getKey(), taken, Actionable.MODULATE, IActionSource.empty());
            excess -= extracted;
        }
        inventory.persist();
    }

    // ---------------------------------------------------------------------------------------------
    // Display
    // ---------------------------------------------------------------------------------------------

    @Override
    public AEKeyType getKeyType() {
        return GasKeyType.TYPE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        if (!Platform.isClient()) {
            return;
        }
        long capacity = (long) getBytes(stack) * GasAmounts.GU_PER_BYTE;
        lines.add(ACEText.cellCapacity(capacity));
        lines.add(ACEText.cellVolume(capacity));
        lines.add(ACEText.cellRatedPressure(getRatedPressureAtm(stack)));
        lines.add(ACEText.cellVirtualVolume(getVirtualVolumeLiters(stack)));
        lines.add(ACEText.cellTypes(getTotalTypes(stack)));
        lines.add(ACEText.CELL_PRESSURE_NOTE);
        if (getPressureCards(stack) > 0 || getVolumeCards(stack) > 0 || getTypeCards(stack) > 0) {
            lines.add(ACEText.CELL_CARDED_NOTE);
        }
        if (!hasReliefValve(stack) && isOverfilled(stack, capacity)) {
            lines.add(ACEText.CELL_OVERFILLED_NOTE);
        }
    }

    private boolean isOverfilled(ItemStack stack, long capacity) {
        StorageCell inventory = StorageCells.getCellInventory(stack, null);
        if (inventory == null) {
            return false;
        }
        long total = 0;
        for (var entry : inventory.getAvailableStacks()) {
            total += entry.getLongValue();
        }
        return total > capacity;
    }
}
