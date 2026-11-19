# Gas P2P Tunnel — design & verification notes

## 1. What was added

| Piece | Path |
|---|---|
| Part class | `src/main/java/com/appliedcraftedenergistics/device/p2p/GasP2PTunnelPart.java` |
| Part item registration | `src/main/java/com/appliedcraftedenergistics/init/ACEParts.java` |
| Block-level capability bridge | `src/main/java/com/appliedcraftedenergistics/init/ACEPartCapabilities.java` |
| Config | `Config.COMMON.gasP2pBufferVolumeLiters` (`gas_p2p.bufferVolumeLiters`, default 4,000 L) |
| Models | `assets/appliedcraftedenergistics/models/part/p2p/p2p_tunnel_gas.json`, `models/item/gas_p2p_tunnel.json` |
| Texture | `assets/appliedcraftedenergistics/textures/part/p2p_tunnel_gas.png` (16×16, AE2 P2P "type" layout, gas palette) |
| Recipe | `data/appliedcraftedenergistics/recipe/gas_p2p_tunnel.json` |
| Lang | `item.appliedcraftedenergistics.gas_p2p_tunnel` (+ `.desc`), `gui.appliedcraftedenergistics.p2p_attunement_gas` |
| GuideME pages | `assets/appliedcraftedenergistics/ae2guide/{index,gas_p2p_tunnel}.md` + `_zh_cn/` |

## 2. The one non-obvious constraint

Create: Crafted Beginning's pipe solver **does not transfer with a plain `GasHandler`**. Per
`docs/CCB_GAS_API.md` §3.1, a face becomes a live endpoint only if the handler is a
`GasStorageHandler` whose `getPressureCompartment(t).getVolume() > 0` (or a single-tank
`GasPressureBoundary`). A plain handler yields a pipe that visually connects and moves nothing.

That is why the tunnel is not a "stack forwarder": each tunnel face presents **a real small pressure
vessel** (`GasBufferTank`, 4,000 L, rated to CCB's safe 16 atm). This also makes the pressure semantics
honest:

* **Input part** — the vessel is its own buffer. `fill` accepts gas from the adjacent line while
  `linePressure > bufferPressure`; `drain` returns nothing, so gas cannot leave through the input side
  (AE2's P2P directionality).
* **Output parts** — they present the *input part's buffer* (`getInput().buffer`) at their own face.
  The adjacent line drains it exactly as if the tank were there, and each output takes only what its own
  network accepts. Nothing is copied: outputs drain one shared vessel, so there is no duplication
  window even when several outputs drain in the same tick.
* `captureTransferSnapshot()` delegates to the buffer, so CCB's simulate/execute/rollback model cannot
  leave the buffer in a state the network did not actually deliver.
* AE cost: 1 AE/t idle (AE2 P2P default) + `deductTransportCost(amount, GasKeyType.TYPE)` per gas unit
  actually delivered on an output.

Because the vessel is rated to **16 atm (SAFE)**, a tunnel can never push a player's airtight tank
towards its rupture limit; CCB's own solver still does all pressure arithmetic on both ends. If the
output side is blocked, the buffer simply fills to 16 atm and the input stops accepting — no loss.

## 3. Why the part capability needs a bridge

CCB pipes probe **block-level** capabilities, while an AE2 part lives inside the cable-bus block.
`ACEPartCapabilities` registers `GasCapabilities.BLOCK` in AE2's `RegisterPartCapabilitiesEvent`;
AE2 itself re-publishes part capabilities as block-entity capabilities on the part host, resolving the
part by face (`RegisterPartCapabilitiesEventInternal`). This is the same mechanism AppMek uses for its
chemical P2P tunnel and AppliedPneumatics for its air tunnel.

## 4. Getting the tunnel

* Craft (recipe mirrors AE2's P2P tunnel recipe, with CCB `airtight_sheet` as the body).
* Attune any P2P tunnel by right-clicking it with a CCB gas canister:
  `P2PTunnelAttunement.registerAttunementApi(GAS_P2P_TUNNEL, CanisterCapabilities.ITEM, …)` plus
  `registerAttunementTag(...)` so datapacks can extend the trigger list.

Linking is AE2's standard memory-card flow: shift-right-click = new frequency (input), right-click =
join as output.

## 5. GuideME

AE2 19.2's guidebook scans every mod's `assets/<mod>/ae2guide/` folder (verified: AppliedSoul,
ExtendedAE, ae2wtlib and ae2omnicells all ship pages there). Our pages:

* `ae2guide/index.md` — addon root (navigation entry "Applied Crafted Energistics", icon `gas_cell_1k`).
* `ae2guide/gas_p2p_tunnel.md` — the mechanics page: linking, the vessel/pressure model, energy,
  limitations, recipe.
* `_zh_cn/` mirrors both, so the Chinese guide shows the same content.

The page uses the same Markdown/front-matter dialect as AE2's own `p2p_tunnels.md`
(`navigation.parent`, `icon`, `position`, `item_ids`, `<RecipeFor>`), so it renders in-game.

## 6. Verification

* `gradlew build` → **BUILD SUCCESSFUL**, jar 107,764 B,
  sha256 `2C96209DD7F2D257FE9715987D7BB2FFB9E6B223D3410D693F9558EC7BEC43E2` (entries grew with
  the P2P classes, guide pages, and the later ACE rename).
* Jar inspected: `device/p2p/GasP2PTunnelPart`, its `$TankView`, part/item models, texture, recipe and
  all four guide files are present.
* Full 180-mod client launch on the hardlinked pack copy (`docs/run-logs/client-06.*`, run 6 in
  `docs/RUNTIME_VERIFICATION.md`): `Applied Crafted Energistics 0.1.0` in the Mod List, both runtime INFO
  lines present, main menu reached in 98.7 s, **zero** ERROR/WARN attributable to
  `appliedcraftedenergistics`, no crash report. GuideME completed its page indexing (only other mods'
  index conflicts were logged), i.e. our pages parsed.
* Installed at the time: the real instance `mods/` held this exact sha (107,764 B); the old-named jar and the stale
  `config/appliedaerostatics-common.toml` were removed.
* Superseded by **R5** (38E67CB7…, 122,204 B, cell upgrade cards) — installed and verified in run 8.

### Still needs a manual in-game pass (cannot be automated here)

1. Craft / attune the tunnel and place an input + output on two cable faces.
2. Feed the input from a CCB tank/pump and check that gas appears on the output side.
3. Check that the input refuses to be drained and that a blocked output fills the buffer and stops the
   input (no gas loss).
4. Open the AE2 guide and confirm the "Gas P2P Tunnel" page renders (icon, recipe, links).
