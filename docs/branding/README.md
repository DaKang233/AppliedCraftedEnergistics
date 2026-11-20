# Applied Crafted Energistics artwork

The logo represents an ME Gas Interface with **six identical connectable faces**.
It follows AE2 19.2's minimalist native interface artwork: white/silver casing,
purple-gray trim and a muted teal/cyan inset. There is no special front, top or service side.

| File | Size | Use |
|---|---|---|
| `ace-logo-1024.png` | 1024×1024, transparent | Encyclopedia entry and full-resolution square logo |
| `ace-logo-16x10.png` | 1600×1000, transparent | Text-free landscape logo; centred cube at its original pixel scale, with transparent margins |
| `ace-logo-512.png` | 512×512, transparent | General mod listings and README |
| `ace-logo-256.png` | 256×256, transparent | Mod-list icon; installed as `src/main/resources/icon.png` |
| `ace-logo-400.png` | 400×400, transparent | CurseForge project avatar (the platform requires exactly 400×400) |
| `ace-logo-64.png` | 64×64, transparent | Small-icon preview |
| `ace-logo-dark.png` | 1024×1024, opaque | Square logo on a dark background |
| `ace-logo-banner.png` | 1600×640, opaque | English title and Chinese name for an article header |
| `me-gas-interface-block.png` | 1024×1024, transparent | Exact projection of the actual game texture |
| `interface-textures-preview.png` | 864×328 | Confirms that front, side and top aliases are identical |
| `source/logo.png` | Original generation size | Retained promotional illustration master |
| `prompts.json` | Text | Logo prompt and the deterministic texture colour mapping |

The three native block texture aliases live in
`src/main/resources/assets/appliedcraftedenergistics/textures/block/me_gas_interface_{front,side,top}.png`.
They are byte-identical **16×16**, fully opaque, seven-colour PNGs. They preserve the AE2 interface's
white casing, trim and pixel geometry. Only its three blue shades are remapped:

| Original AE2 RGB | Gas RGB |
|---|---|
| `112, 140, 186` | `84, 140, 156` |
| `125, 169, 210` | `108, 164, 178` |
| `156, 211, 255` | `156, 204, 214` |

The existing model assigns these identical aliases to all six faces. Models, UVs, facing states,
block behaviour and connection logic are unchanged. The block projection is the literal texture
view; the logo is a promotional illustration with the same design repeated on each visible face.

To rebuild the textures and exports on Windows, from the repository root:

```powershell
pwsh -File tools/prepare_interface_art.ps1
```

The script reads the supplied `libs/appliedenergistics2-19.2.20.jar` and the retained logo master.
It uses Windows System.Drawing, makes no network requests, preserves logo transparency and fits
the visible cube within a consistent safe margin. The NeoForge `logoFile="icon.png"` registration uses the 256-pixel logo export (smaller jar; 1024 remains for articles).

The logo master was created with built-in image generation using the exact block projection and
native AE2 textures as references. The game texture itself is a deterministic recolour, not an
AI approximation. These AE2-derived textures and logo images retain **CC BY-NC-SA 3.0** terms and
the attribution in `THIRD_PARTY_NOTICES.md`. The preparation script follows the project's
LGPL-3.0-or-later code licence.

Validation covers native dimensions, the exact three-colour mapping, equality of all six face
references, true logo transparency, reproducible exports and the copies included in the mod jar.
No in-game visual acceptance has been performed for this artwork refresh.
