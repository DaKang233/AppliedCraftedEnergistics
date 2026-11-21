---
navigation:
  parent: index.md
  title: ME Gas Interface and Automation
  icon: appliedcraftedenergistics:me_gas_interface
  position: 5
item_ids:
  - appliedcraftedenergistics:me_gas_interface
---

# ME Gas Interface and Automation

<BlockImage id="me_gas_interface" scale="4" />

The **ME Gas Interface** is the main gas-to-ME boundary. It presents a real CCB pressure vessel to
the gas pipes and AE2's usual interface slots to the network, so gas can be imported, exported,
stored and used by autocrafting.

## Slots and per-slot settings

The interface keeps AE2's familiar two-column layout: nine config slots and nine storage/cache slots.
Every marked config slot has a settings button above it (middle-clicking the slot also works). The
settings dialog edits:

- **Target amount** — how much gas this slot should keep or supply;
- **Target pressure** — the pressure used by this slot's transfer;
- **Transfer mode** — INPUT, OUTPUT, BOTH or OFF.

The mode field requires the **Slot Control Card**; without it the slot uses the default mode. The
**Safety Card** vents dangerous overpressure through CCB's real gas release instead of letting the
interface over-pressurise its vessel.

## Stock AE2 interface, pattern provider and buses

ACE does not clone AE2's devices: it teaches the stock ones to handle gas keys.

- **ME Interface / Pattern Provider** — gas config slots are exposed to CCB gas pipes as fixed
  100 kPa vessels; pattern outputs can be pushed into an adjacent gas handler.
- **Import / Export Bus** — gas can be pulled from or pushed into an adjacent CCB gas handler. The
  **Gas Pressure Card** installed in the bus sets a target pressure: export stops at the target and
  import only drains above it.
- **Storage Bus** — an adjacent CCB gas container is exposed as ME storage, so its gas appears in the
  network like any other resource.

## Marking gas

Gas config slots can be marked by:

- right-clicking a **Gas Sample** (or a CCB virtual gas item);
- right-clicking an item containing gas (CCB canisters and similar containers);
- dragging a gas ingredient from JEI onto the slot.

AE2's native container marking is used, so this also works for AE2's own interface and bus config
slots.

## JEI pattern encoding

With **AE2-JEI-Integration** installed, press the JEI **+** button on a CCB processing recipe while
the pattern encoding terminal is open. Gas inputs and outputs are encoded as AE2 processing-pattern
`GenericStack`s (gas type and amount). A standard AE2 pattern has no pressure field: target pressure
is still determined by the machine or vessel.

## Save / reload behaviour

Gas keys use the registry gas object as their AE2 primary key. This keeps the crafting simulation's
key lookup stable after a world save and reload; gas autocrafting no longer loses its pattern outputs
after reopening a world.

## See Also

- [Gas Storage Cells](gas_cells.md)
- [Cell Upgrade Cards](upgrades.md)
- [Gas P2P Tunnel](gas_p2p_tunnel.md)
