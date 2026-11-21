# Gas sample and machine upgrade-card artwork

The four items use native **16×16 RGBA** textures. The cards keep AE2's original silver casing,
engraved gray symbols and bottom contacts. Connector colours follow their actual recipes.

| Item | Symbol | Behaviour represented | Connector |
|---|---|---|---|
| `gas_sample` | Three cyan gas puffs with a white identity tag | Carries a gas ID for marking a gas configuration slot; it is a sample token rather than an upgrade card or stored gas container | None |
| `slot_control_card` | Three independent sliders with staggered knobs | Unlocks and remembers each interface slot's INPUT / OUTPUT / BOTH / OFF mode; the three sliders are a compact symbol for independent controls | Advanced cyan |
| `safety_valve_card` | Danger triangle with a red exclamation mark | Automatically releases real gas through CCB when an interface slot reaches the dangerous 20 atm threshold; this is not an ownership/security lock | Basic gold |
| `gas_pressure_card` | Pressure scale with an adjustable target marker | Sets a bus target of 1 / 2 / 4 / 8 / 12 / 16 atm: export stops at it, and import draws only above it | Advanced cyan |

The pressure-target glyph differs from the semicircular gauge on the older storage-cell
`pressure_card`. The danger triangle distinguishes the new interface safety card from the
older `relief_valve_card`, whose container-and-arrow symbol refers to storage-cell overfill.
The new symbols are static icons; existing tooltips still show actual settings.

Generate only these four textures and their preview:

```powershell
python tools/gen_card_textures.py --new-items-only
```

The command writes the four existing item texture paths under
`src/main/resources/assets/appliedcraftedenergistics/textures/item/` and
`docs/images/interface_items.png`. The original command without the flag continues to generate
only the original four cell cards and their existing preview.

Visual studies were created with built-in image generation; the final icons are exact native
pixel grids retained in `tools/gen_card_textures.py`. The full study prompts are in
`docs/artwork/new-items-prompts.json`. Reproduction uses Pillow and the supplied AE2 jar, without
network requests. The AE2-derived card casing retains the attribution and artwork terms in
`THIRD_PARTY_NOTICES.md`; the gas-sample drawing is original project artwork.

Validation checks native size, binary alpha, the unchanged card casing, recipe-appropriate
connectors, distinct symbols, reproducibility and packaged copies. The original four cell-card
outputs are checked to remain pixel-identical. No gameplay, model, recipe, component or tint
handling changes are included, and in-game visual acceptance remains pending.
