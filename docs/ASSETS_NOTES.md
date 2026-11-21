# Applied Crafted Energistics — assets notes

Owner: assets-engineer. Everything below lives under `src/main/resources`. No Java, no Gradle.

Written by hand, **not** by datagen. Decision: `runData` needs a green build plus the shared Gradle
lock, and hand-written JSON is byte-for-byte what datagen emits for models/lang/recipes/loot. There is
no `src/main/java/com/appliedcraftedenergistics/datagen` package and no `src/generated/resources`.

## 1. Files

| Path | What |
|---|---|
| `assets/appliedcraftedenergistics/lang/en_us.json` | English strings |
| `assets/appliedcraftedenergistics/lang/zh_cn.json` | Simplified Chinese strings (same key set) |
| `assets/appliedcraftedenergistics/models/item/gas_cell_{1k,4k,16k,64k,256k}.json` | 5 cell item models |
| `assets/appliedcraftedenergistics/textures/item/gas_cell_{1k,4k,16k,64k,256k}.png` | 5 generated 16×16 cell textures |
| `assets/appliedcraftedenergistics/models/block/drive/cells/gas_cell_{1k,4k,16k,64k,256k}.json` | 5 ME Drive display models (required by AE2, see §2.1) |
| `assets/appliedcraftedenergistics/textures/block/drive/drive_cells.png` | 16×16 sheet the drive models sample |
| `assets/appliedcraftedenergistics/models/item/me_gas_interface.json` | block item model |
| `assets/appliedcraftedenergistics/models/block/me_gas_interface.json` | block model (full cube, custom faces) |
| `assets/appliedcraftedenergistics/blockstates/me_gas_interface.json` | 4 horizontal `facing` variants |
| `assets/appliedcraftedenergistics/textures/block/me_gas_interface_{front,side,top}.png` | identical native 16×16 gas-interface textures on all six faces |
| `data/appliedcraftedenergistics/loot_table/blocks/me_gas_interface.json` | drops itself |
| `data/appliedcraftedenergistics/recipe/gas_cell_{1k,4k,16k,64k,256k}.json` | 5 cell recipes (shapeless) |
| `data/appliedcraftedenergistics/recipe/me_gas_interface.json` | interface recipe (shaped) |

1.21.1 folder names were checked against AE2 19.2.20 itself: `data/<ns>/recipe/` (singular) and
`data/<ns>/loot_table/blocks/`. Both confirmed by listing the jar's entries
(`libs/appliedenergistics2-19.2.20.jar`).

## 2. Model / texture decisions

**Cells.** Mirrors AE2 19.2 and Applied Mekanistics
(`assets/appmek` / `ItemModelProvider.cell(...)`): `parent: minecraft:item/generated` with

* `layer0` — our own texture, `appliedcraftedenergistics:item/gas_cell_<tier>`
* `layer1` — `ae2:item/storage_cell_led` (present in the jar: `assets/ae2/textures/item/storage_cell_led.png`,
  117 bytes, a single bright LED pixel), so our cells blink exactly like native AE2 cells.

The `layer0` art follows AE2 19.2's *new* storage-cell icon style on purpose (this was an explicit
user request): AE2's 19.2 cell sprites are one 16×16 icon composed of an 8-shade housing ramp plus a
4-shade tier accent, where the housing says *what kind of cell* it is (item cells = white/silver,
fluid cells = copper) and the accent says *which tier* (1k blue → 256k green).

Rather than inventing a shape, `tools/gen_cell_textures.py` reads AE2's own item-cell sprites and the
ME-Drive sheet out of `libs/appliedenergistics2-19.2.20.jar` and remaps **only the housing palette**
to a gas-specific cyan/teal ramp. Tier accents are left byte-identical, so gas cells read as the same
family as AE2 item/fluid cells while being identifiable at a glance, and the tier colour convention
is unchanged:

| Housing | Meaning |
|---|---|
| white/silver | AE2 item cells |
| copper | AE2 fluid cells |
| **cyan/teal** | **Applied Crafted Energistics gas cells** |

The housing ramp is eight explicit RGB triples at the top of the generator script, ordered from the
dark outline to the bevel highlight; changing the colour is a one-line edit followed by re-running the
script. The script never downloads anything and never writes outside `src/main/resources`.

**Provenance / licensing.** The cell and drive textures are recolours of Applied Energistics 2's own
art, used under AE2's asset terms (AE2 is distributed under LGPL-3.0 for code with assets under a
non-commercial share-alike licence). This mod is non-commercial and its own source is
LGPL-3.0-only; if you redistribute the *art* separately, keep the same non-commercial share-alike
terms and credit TeamAppliedEnergistics.

### 2.1 The ME Drive needs a *block* model per cell (easy to miss)

AE2 does not render the item model inside the ME Drive. It renders a model registered with
`appeng.api.client.StorageCellModels.registerModel(item, modelId)`, and `DriveModel` bakes every
registered model as a dependency, so the JSON **must** exist at `assets/<ns>/models/<modelId>.json`.
Without the registration an inserted gas cell simply does not draw — no crash, no log line, which is
exactly the kind of bug that survives a whole test pass.

So there are five extra models, `appliedcraftedenergistics:block/drive/cells/gas_cell_<tier>`, shaped exactly
like AE2's own (`assets/ae2/models/block/drive/cells/1k_item_cell.json`) and AppMek's: one 6×2×2 element
with `cullface: north`, `ambientocclusion: false`, sampling a 6×2 slice of
`appliedcraftedenergistics:block/drive/drive_cells`. That 16×16 sheet holds the five slices in a 6×10 strip at
(0,0), 2px apart (`uv_y` 0/2/4/6/8), matching AE2's and AppMek's convention.

**Java (implemented):** the five calls live in `client/ACEGasClient` on `FMLClientSetupEvent` →
`event.enqueueWork(...)`, client dist only:

```java
StorageCellModels.registerModel(ACEItems.GAS_CELL_1K.get(),   AppliedCraftedEnergistics.id("block/drive/cells/gas_cell_1k"));
StorageCellModels.registerModel(ACEItems.GAS_CELL_4K.get(),   AppliedCraftedEnergistics.id("block/drive/cells/gas_cell_4k"));
StorageCellModels.registerModel(ACEItems.GAS_CELL_16K.get(),  AppliedCraftedEnergistics.id("block/drive/cells/gas_cell_16k"));
StorageCellModels.registerModel(ACEItems.GAS_CELL_64K.get(),  AppliedCraftedEnergistics.id("block/drive/cells/gas_cell_64k"));
StorageCellModels.registerModel(ACEItems.GAS_CELL_256K.get(), AppliedCraftedEnergistics.id("block/drive/cells/gas_cell_256k"));
```

The item-colour handler for the LED layer (`BasicStorageCell::getColor`, mirroring AE2's
`InitItemColors`) is registered in the same class via `RegisterColorHandlersEvent.Item`.

The model ids above are exactly the files shipped here; a mismatch is a silent no-draw.

**ME Gas Interface.** All **six faces are connectable** and intentionally have the **same
texture**. The existing full-cube model and horizontal facing blockstates are kept for compatibility;
its `front`, `side` and `top` texture files are byte-identical. The model assigns those three aliases
across all six faces, with no visual distinction between an input, output, top or service side.

The artwork follows AE2 19.2's minimalist native **16×16** ME Interface: the white/silver casing,
purple-gray trim and all pixel geometry are preserved, and only the three blue shades become the
muted cyan/teal gas-cell ramp. The result has seven colours, matching the original's visual density.
There are no brass fittings, vents, gauges, fasteners or additional gas symbols.

`tools/prepare_interface_art.ps1` reads `assets/ae2/textures/block/interface.png` from the supplied
AE2 jar in `libs/` and applies the explicit three-colour mapping. It writes identical copies to
`me_gas_interface_{front,side,top}.png`, then makes an exact isometric block projection. The logo
illustration uses the same repeated face on every visible side; its master is retained in
`docs/branding/source/logo.png`. The script also prepares transparent logo sizes, a dark-background
version, a title banner and the packaged `icon.png`.

These are AE2-derived visuals, covered by the attribution and CC BY-NC-SA 3.0 terms in
`THIRD_PARTY_NOTICES.md`. File sizes and regeneration instructions are listed in
`docs/branding/README.md`. Block behaviour, models, UV coverage and connection logic are unchanged.

## 3. Ids used, and where each was verified

| Id | Kind | Source / verification |
|---|---|---|
| `ae2:item/storage_cell_led` | texture | AE2 jar `assets/ae2/textures/item/storage_cell_led.png` |
| `ae2:item_cell_housing` | item | AE2 jar `data/ae2/recipe/network/cells/item_cell_housing.json` result + `assets/ae2/lang/en_us.json` `item.ae2.item_cell_housing` |
| `ae2:cell_component_1k` / `4k` / `16k` / `64k` / `256k` | item | AE2 jar `data/ae2/recipe/network/cells/item_storage_components_cell_*_part.json` results + `models/item/cell_component_*.json` |
| `ae2:annihilation_core`, `ae2:formation_core` | item | AE2 jar `models/item/{annihilation,formation}_core.json` + `data/ae2/recipe/network/blocks/interfaces_interface.json` |
| `createcraftedbeginning:airtight_sheet` | item | CCB jar `models/item/airtight_sheet.json`, `lang` `item.createcraftedbeginning.airtight_sheet` = "Airtight Sheet", produced by `recipe/sequenced_assembly/airtight_sheet.json` |
| `createcraftedbeginning:gas_canister` | item | CCB jar `models/item/gas_canister.json`, `lang` `item.createcraftedbeginning.gas_canister` = "Gas Canister", `recipe/crafting/airtights/gas_canister.json` |
| `createcraftedbeginning:airtight_tank` | block item | CCB jar `blockstates/airtight_tank.json`, `lang` `block.createcraftedbeginning.airtight_tank` = "Airtight Tank", `recipe/crafting/airtights/airtight_tank.json` |
| `c:ingots/iron`, `c:glass_blocks/cheap` | tags | used by AE2's own `interfaces_interface.json`; resolved by NeoForge's common tags at runtime |
| `appliedcraftedenergistics:gas_cell_*`, `appliedcraftedenergistics:me_gas_interface` | our items | `ACEItems.java`, plus the block id reported by device-engineer |

## 4. Recipes (deliberately cheap; balance is out of scope)

* Cells are **shapeless**: `ae2:item_cell_housing` + `ae2:cell_component_<tier>` +
  `createcraftedbeginning:airtight_sheet` for 1k/4k/16k, and `…:gas_canister` for 64k/256k (bigger cells
  get a real canister instead of a flat sheet). Only the AE2 component tier changes, so the progression
  reads exactly like AE2's own cell ladder.
* Interface is **shaped**, AE2's interface pattern with the empty middle filled by a real CCB tank:
  ```
  iron  glass  iron
  anni  TANK   form
  iron  glass  iron
  ```
  (`annihilation_core` / `formation_core` = import / export half, matching AE2's own interface recipe.)

## 5. Lang

Every key in `ACEText.java` and `GasKeyType.java` is present in both files, plus
`itemGroup.appliedcraftedenergistics`, the 5 cell names, `block.appliedcraftedenergistics.me_gas_interface` and
`.desc`, and `gui.appliedcraftedenergistics.interface.supply_pressure` (the adjustable-supply-pressure row the
device added later — en "Supply pressure", zh "供给压力"). 24 keys per file, identical sets.

The device's `assets/ae2/screens/me_gas_interface.json` was also cross-checked: every `translate` value
in that layout exists in both lang files (dialog title, buffer / pressure / supply-pressure / mode
labels). Missing ones would render as raw keys in the GUI.

Note the tooltip args are pre-formatted by `GasAmounts.format()` / `formatVolume()`, which
already append their units (`"1,024 kGU"`, `"1,024 m³ @100 kPa"`), so the lang strings deliberately do
**not** repeat a unit: `"Capacity: %s"`, `"Equivalent volume: %s"`.

Chinese mod name is 应用机巧能源 (per `docs/PLAN.md` §1); the previous placeholder said 应用空气动力学
("aerodynamics"), which is wrong for this mod.

## 6. Verification performed

* All 22 JSON files parse with PowerShell `ConvertFrom-Json` (0 failures).
* Cross-reference validator (Python, run from outside the repo) resolved every model `parent`, every
  `textures` entry of every model face, every blockstate model ref, every recipe ingredient and result,
  and the loot table entry: 0 errors. Blockstate variants are exactly the four `facing=` values, and all
  five ME Drive display models plus their sheet are present.
* `HORIZONTAL_FACING` was verified against the AE2 jar, not just the device's word:
  `appeng/api/orientation/HorizontalFacingStrategy.class` references
  `BlockStateProperties.HORIZONTAL_FACING` (and a `DirectionProperty`), so `horizontalFacing()` yields
  exactly one property named `facing` with the four horizontal values.
* Lang key sets of `en_us` and `zh_cn` are identical, and every `Component.translatable("…")` literal in
  `src/main/java` has an entry.
* No `.gitkeep`, `*.tmp`, `*.bak` or other stray files under `assets/` or `data/`.

## 7. Block contract — confirmed by device-engineer

Locked, no longer assumed:

1. Block id **`appliedcraftedenergistics:me_gas_interface`**, block item id the same. The device class uses
   AE2's `OrientationStrategies.horizontalFacing()`, i.e. exactly one BlockState property —
   `BlockStateProperties.HORIZONTAL_FACING` (north/east/south/west). **No** `spin`, `active`, `powered`
   or `waterlogged`. The four-variant blockstate above matches it exactly, so it is final.
2. No AE2 parts are registered (no gas import/export bus in v0.1) → no part models or part textures.
3. No new translation keys — the device reuses `ACEText.java` only; the screen adds no PNG art (AE2's
   procedurally drawn `generatedBackground` + `AE2Button`). The device does add
   `assets/ae2/screens/me_gas_interface.json` (AE2 screen layout JSON, AE2's namespace, not ours).
4. `models/item/me_gas_interface.json` **is** required even though the device registers the block item
   with a subclass: Minecraft resolves a block item's model from `assets/<ns>/models/item/<id>.json`,
   not from `models/block/`. Every vanilla and AE2 block item ships one (`ae2:item/interface` →
   `ae2:block/interface`), and CCB does the same (`createcraftedbeginning:item/airtight_tank` →
   `createcraftedbeginning:block/airtight_tanks/vertical`). Ours parents our block model, so the
   inventory icon is the directional cube.

### Still open

5. **Five `StorageCellModels.registerModel(...)` calls** are still missing from `src/main/java` (see
   §2.1). The JSON and the sheet are shipped; until the registration exists, gas cells will not draw
   inside the ME Drive. This is the only known remaining gap on the cell resource side, and it is Java,
   so it is not in my write scope.
6. Whether the block entity holds gas that should survive breaking. The loot table currently drops a
   bare block (no `copy_components`), which is the normal AE2 behaviour for simple machines; a
   fluid-tank-style "keep contents" block would need a `minecraft:copy_components` function instead.
7. `assets/ae2/screens/me_gas_interface.json` is device-owned; if it ever references a lang key or a
   texture that does not exist, the symptom is a missing screen label, not a crash. Re-grep
   `src/main/java` for `Component.translatable(` before release as a final sweep.

## 8. Not done / deliberately skipped

* No item tags. Checked AE2 19.2.20 for a storage-cell tag to join — there is none
  (`data/ae2/tags/item/` has `interface.json` and `pattern_provider.json`, which list AE2's own
  `ae2:interface` / `ae2:cable_interface` and provider counterparts, but nothing about cells), and
  Create/CCB do not consume one either. A `c:` tag for a private cell type would be invented rather
  than consumed; add one only when another mod needs to match them.
* No GUI textures: the gas-channel icon is drawn programmatically by
  `client/GasKeyRenderHandler.java`, and the interface screen is expected to use AE2/vanilla widgets.
* No `gas` key-type icon texture: AE2 renders channel icons through the registered `AEKeyRenderHandler`.
* No JEI/EMI, no Ponder scenes.


## New gas sample and machine-card icons

`gas_sample`, `slot_control_card`, `safety_valve_card` and `gas_pressure_card` now have distinct
native 16×16 textures instead of sharing the high-pressure cell-card placeholder. They use a
gas-puff identity token, independent slider controls, a red-exclamation danger triangle and a
pressure setpoint scale. The safety card uses a gold basic connector, as required by its recipe;
the two other cards use advanced cyan connectors. Existing models and item behaviour are unchanged.

Run `python tools/gen_card_textures.py --new-items-only` to reproduce these four files and
`docs/images/interface_items.png`. Details, source behaviour and provenance are recorded in
`docs/NEW_ITEM_ARTWORK.md`; original four-card generation remains the default.
