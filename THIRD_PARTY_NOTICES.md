# Third-Party Notices — Applied Crafted Energistics (ACE)

This mod is an addon for Applied Energistics 2 and interacts with Create: Crafted Beginning through its
public API. It does **not** bundle any code or assets from those mods beyond the derived textures listed
below.

## Applied Energistics 2 (AE2)

AE2 is distributed under **three separate licenses**, and they apply to different parts of the work:

| Part of AE2 | License | How it affects this mod |
|---|---|---|
| **AE2 API** (`appeng.api.*`, and the interfaces it declares) | **MIT** — (c) 2013–2020 AlgorithmX2 et al. | ACE only calls the public API, so the API's MIT terms apply and no copyleft is triggered for ACE's own code. |
| **AE2 mod code** (implementation, not API) | **LGPL-3.0** (the source headers grant "either version 3 … or any later version") | ACE does not copy AE2 implementation code. If any code in this repository is later found to be adapted from AE2 source, that part stays under **LGPL-3.0-or-later**, which is compatible with this project's license. |
| **Textures and Models** | **CC BY-NC-SA 3.0** — (c) 2020 Ridanisaurus Rid, (c) 2013–2020 AlgorithmX2 et al. | **Relevant: ACE ships derived textures (below).** |

### Derived textures (CC BY-NC-SA 3.0)

The following files are **derivative works** of AE2 textures. They are distributed under
**CC BY-NC-SA 3.0** (Attribution — NonCommercial — ShareAlike), with the modifications described:

| ACE file(s) | Derived from | Modification |
|---|---|---|
| `assets/appliedcraftedenergistics/textures/item/gas_cell_1k/4k/16k/64k/256k.png` | AE2 19.2 item/fluid storage-cell sprites | The 8-shade housing palette is remapped from AE2's white (item) / copper (fluid) ramps to a gas-specific cyan/teal ramp; the 4 tier-accent colours and the sprite geometry are unchanged. |
| `assets/appliedcraftedenergistics/textures/block/drive/drive_cells.png` | AE2 19.2 drive cell sheet | Same housing-palette remap for the five drive-cell slices. |
| `assets/appliedcraftedenergistics/textures/item/pressure_card.png`, `volume_card.png`, `type_expansion_card.png`, `relief_valve_card.png` | AE2's `item/card_void.png` (card body) and `item/advanced_card.png` (cyan connector pixels) | The right-hand connector is replaced with AE2's advanced-card cyan pixels; the central 8×8 engraving is redrawn per card; the frame, shading and bottom contacts are kept. |
| `assets/appliedcraftedenergistics/textures/item/slot_control_card.png`, `safety_valve_card.png`, `gas_pressure_card.png` | AE2 `item/card_void.png` (casing and basic gold connector) and `item/advanced_card.png` (advanced cyan connector) | New engraved per-slot slider, danger-triangle and pressure-setpoint symbols. Slot control and pressure use cyan advanced connectors; the safety card keeps the basic gold connector required by its recipe. |
| `assets/appliedcraftedenergistics/textures/block/me_gas_interface_{front,side,top}.png` and the interface-derived block/logo artwork in `docs/branding/` / `icon.png` | AE2 19.2 `textures/block/interface.png` | The three blue shades are remapped to the gas-cell cyan/teal ramp. All six block faces keep the same 16×16 pixel layout. The logo illustrates this same block, and size/background/title exports reuse it. |

The cell, drive, upgrade-card and interface textures are generated deterministically. Their generator scripts are part of the source distribution:
`tools/gen_cell_textures.py`, `tools/gen_card_textures.py` and `tools/prepare_interface_art.ps1`
(they read the AE2 jar from `libs/`, never the network). The logo master is retained alongside its
prompt; the preparation script reproduces its size, background and title exports. Because CC BY-NC-SA 3.0 is a **NonCommercial** licence, this mod (including these textures)
must not be sold or distributed in a commercial offering; free distribution is unaffected.

Attribution to include whenever the textures are reused:
> Textures derived from Applied Energistics 2 (c) 2020 Ridanisaurus Rid, (c) 2013–2020 AlgorithmX2 et al.,
> licensed CC BY-NC-SA 3.0; modified for Applied Crafted Energistics.

## Create: Crafted Beginning (CCB)

- License: **MIT** — (c) 2026 ty.
- ACE uses CCB's **public API** (`net.ty.createcraftedbeginning.api.*`) at runtime; CCB is a required
  dependency and is not bundled with ACE.
- CCB's own repository contains a `THIRD_PARTY_NOTICES.md` for Create-derived structures; none of that
  code is redistributed in this mod.

## Create (dependency of CCB)

Create is a runtime dependency of CCB. ACE does not bundle or copy Create code; any interaction is through
CCB's API types. Create's own licence applies to Create itself.

## Applied Pneumatics (design reference)

Applied Pneumatics is LGPL-3.0 and was used as a **design reference** for the ME Gas Interface interaction
model (supply-pressure setpoint, mode button). ACE's implementation is written against AE2's API; if any
snippet is found to be adapted from Applied Pneumatics, that snippet remains **LGPL-3.0-or-later**, which
is compatible with this project's licence.

## GuideME / NeoForge / Minecraft

- GuideME (the guidebook library AE2 uses) and NeoForge are runtime dependencies and are not bundled.
- Minecraft is proprietary; ACE is distributed as a mod under the terms of the Minecraft EULA.

## This mod's own licence

ACE's own source code, data files and original assets are licensed under
**LGPL-3.0-or-later** (see `LICENSE`). The derived textures listed above are additionally subject to
CC BY-NC-SA 3.0 as described in this file.
