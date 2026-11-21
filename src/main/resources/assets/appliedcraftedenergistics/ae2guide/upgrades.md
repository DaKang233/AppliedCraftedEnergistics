---
navigation:
  parent: index.md
  title: Cell Upgrade Cards
  icon: appliedcraftedenergistics:pressure_card
  position: 20
item_ids:
  - appliedcraftedenergistics:pressure_card
  - appliedcraftedenergistics:volume_card
  - appliedcraftedenergistics:type_expansion_card
  - appliedcraftedenergistics:relief_valve_card
---

# Cell Upgrade Cards

<Row>
  <ItemImage id="pressure_card" scale="4" />
  <ItemImage id="volume_card" scale="4" />
  <ItemImage id="type_expansion_card" scale="4" />
  <ItemImage id="relief_valve_card" scale="4" />
</Row>

Gas storage cells accept **five** upgrade cards, installed in the **Cell Workbench**. Three of them are
AE2's own storage-cell cards; four are specific to gas.

## AE2's universal cards (work on gas cells too)

| Card | Effect on a gas cell |
|---|---|
| <ItemLink id="ae2:inverter_card" /> | Flips the partition list between whitelist and blacklist |
| <ItemLink id="ae2:equal_distribution_card" /> | Splits the cell's capacity evenly between the stored gas types |
| <ItemLink id="ae2:void_card" /> | Once the cell is full, extra gas is destroyed instead of backing up the network |

(The fuzzy card is deliberately **not** supported: gas keys have no fuzzy value, exactly like AE2's own
fluid cells.)

## Gas-specific cards

A gas cell behaves like a virtual pressure vessel: `capacity(GU) = volume(L) × ratedPressure(Pa) / 100,000`.
Every card below modifies one of those terms, so all numbers stay directly comparable with the gas pipes.

### High-Pressure Card

<ItemImage id="pressure_card" scale="2" />

Raises the cell's **rated pressure from 16 atm (CCB safe) to 24 atm (CCB hard limit)** — the same ceiling
CCB itself enforces. Capacity ×1.5. Only one fits per cell, because 24 atm is the physical
limit of the gas system.

1k cell: 1,024,000 GU → **1,536,000 GU** (= 64,000 L at 24 atm).

### Volume Card

<ItemImage id="volume_card" scale="2" />

Adds **50% virtual vessel volume** per card, up to 4 cards (×3 at four cards). Combines multiplicatively
with the High-Pressure Card.

1k cell: 1,024,000 GU → 1,536,000 GU (1 card) → 3,072,000 GU (4 cards, no pressure card).

### Type Expansion Card

<ItemImage id="type_expansion_card" scale="2" />

Each card allows **4 more gas types** (base 5 → 9 → 13). Useful because
Create: Crafted Beginning has twelve gases; without the card a busy network needs three separate cells
just for type count.

### Relief Valve Card

<ItemImage id="relief_valve_card" scale="2" />

If an upgrade change makes the cell **smaller than what it already holds** (for example removing a
Volume Card), the excess gas is vented instead of leaving the cell permanently over-filled. Without the
card the cell still works but refuses new gas until it is emptied by hand, and its tooltip warns about
the overfill.

## How capacity is displayed

Capacity is ultimately AE2 byte accounting, so a carded cell reports more bytes than its tier. The
tooltip lists the equivalent view in gas terms:

* capacity in GU (= bytes × 1,000),
* equivalent volume at reference pressure,
* rated pressure (atm),
* virtual vessel volume in litres,
* storable gas types.

## Recipe

<RecipeFor id="appliedcraftedenergistics:pressure_card" />
<RecipeFor id="appliedcraftedenergistics:volume_card" />
<RecipeFor id="appliedcraftedenergistics:type_expansion_card" />
<RecipeFor id="appliedcraftedenergistics:relief_valve_card" />

## Interface and bus cards

- **Slot Control Card** — unlocks per-slot INPUT / OUTPUT / BOTH / OFF control on the ME Gas Interface.
- **Safety Card** — vents dangerous overpressure from the ME Gas Interface through CCB's real gas release.
- **Gas Pressure Card** — installs in an AE2 import/export bus and sets the gas target pressure.

<RecipeFor id="appliedcraftedenergistics:slot_control_card" />
<RecipeFor id="appliedcraftedenergistics:safety_valve_card" />
<RecipeFor id="appliedcraftedenergistics:gas_pressure_card" />
