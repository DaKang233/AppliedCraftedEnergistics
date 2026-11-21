# New mechanics since 0.1.1

This page documents the player-visible systems added after the public 0.1.1 release. The in-game
GuideME pages are the primary reference; this file is the repository-side summary.

## Startup guide

In-game pages:

```text
assets/appliedcraftedenergistics/ae2guide/gas_cells.md
assets/appliedcraftedenergistics/ae2guide/gas_interface.md
assets/appliedcraftedenergistics/ae2guide/upgrades.md
assets/appliedcraftedenergistics/ae2guide/gas_p2p_tunnel.md
```

All ten gas storage cells are registered in the GuideME item index. Hover a cell and press the
GuideME hotkey (**G** by default) to open its page. The ME Gas Interface page is likewise reachable
from the main guide index or by its item link.

## ME Gas Interface per-slot settings

The ME Gas Interface keeps nine config slots and nine cache slots. Every marked config slot has a
settings button (middle-click works too). The dialog controls:

- target amount;
- target pressure;
- transfer mode: INPUT, OUTPUT, BOTH, OFF.

The mode control needs the **Slot Control Card**. The **Safety Card** vents overpressure through CCB's
real gas release.

## New cards

| Card | Target | Effect |
|---|---|---|
| Slot Control Card | ME Gas Interface | Unlocks per-slot INPUT/OUTPUT/BOTH/OFF |
| Safety Card | ME Gas Interface | Vents dangerous overpressure through CCB |
| Gas Pressure Card | AE2 import/export bus | Sets a gas target pressure; export stops there, import drains above it |

## Stock AE2 devices

ACE teaches AE2's stock devices about gas keys instead of adding parallel machines:

- **ME Interface / Pattern Provider**: gas config slots are exposed to CCB pipes as fixed 100 kPa
  vessels, and pattern outputs can be pushed to an adjacent gas handler.
- **Import / Export Bus**: gas can be pulled from or pushed to an adjacent CCB gas handler; the Gas
  Pressure Card refines the target.
- **Storage Bus**: an adjacent CCB gas container becomes ME storage.

## Gas marking

Gas config slots can be marked with a Gas Sample, a CCB virtual gas item, an item containing gas, or
by dragging a gas ingredient from JEI. The implementation uses AE2's native container-marking path, so
AE2's own interface and bus slots behave the same way.

## JEI pattern encoding

With AE2-JEI-Integration installed, pressing JEI's **+** on a CCB processing recipe encodes gas inputs
and outputs as processing-pattern `GenericStack`s (gas type and amount). Standard patterns have no
pressure field; target pressure still comes from the machine or vessel.

## Save / reload stability

Gas keys use the registry gas object as their AE2 primary key. This keeps crafting-plan key lookup
stable across a world save and reload, fixing the post-reload "can't find created items" failure.

## 1M–256M cells and MEGA Cells

The high-tier cells are registered in all installs. Their default recipes use MEGA Cells components
and a `neoforge:mod_loaded` condition, so **the default recipes only load/display when MEGA Cells is
installed**.

Config option:

```toml
[recipes]
	enable_mega_cell_recipes = true
```

Setting it to `false` removes the default MEGA-gated recipes even when MEGA Cells is present. Packs can
then supply their own recipes through a datapack.

See [GAS_CELLS.md](GAS_CELLS.md) for the recipe paths and a non-MEGA override example.

## Config reference

| Key | Default | Meaning |
|---|---|---|
| `gas_interface.idlePower` | 8.0 | Interface idle drain, AE/t |
| `gas_p2p.bufferVolumeLiters` | 4000 | Per-face P2P pressure-vessel volume, litres at 16 atm |
| `recipes.enable_mega_cell_recipes` | true | Load the default MEGA-gated 1M–256M recipes |
