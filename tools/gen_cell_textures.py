"""Generate Applied Aerostatics storage-cell art in AE2 19.2's new cell style.

The 19.2 cell icons are a single 16x16 sprite with two palettes:
  * 8 housing shades (item cells = white/silver, fluid cells = copper)
  * 4 tier-accent shades (1k blue -> 256k green, identical for every cell)

This script re-uses AE2's own sprite and drive-sheet pixels verbatim (they are the
canonical shape) and remaps ONLY the housing palette to a gas-specific colour ramp,
so gas cells look like part of the same family as item/fluid cells while being
identifiable at a glance. Tier accents are left untouched, which keeps AE2's
"tier = colour" convention intact.

Source art is read from the shipped AE2 jar, never from the internet.

Usage:
    python tools/gen_cell_textures.py [--preview docs/_texref/cell_preview.png]
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import tempfile
import zipfile

from PIL import Image

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AE2_JAR = os.path.join(PROJECT, "libs", "appliedenergistics2-19.2.20.jar")
OUT_ITEM = os.path.join(PROJECT, "src", "main", "resources", "assets", "appliedaerostatics", "textures", "item")
OUT_BLOCK = os.path.join(PROJECT, "src", "main", "resources", "assets", "appliedaerostatics", "textures", "block", "drive")

TIERS = ["1k", "4k", "16k", "64k", "256k"]

# AE2's item-cell housing palette, ordered dark -> light. This is the exact set of
# colours in assets/ae2/textures/item/item_cell_housing.png.
AE2_ITEM_HOUSING = [
    (65, 63, 84),
    (77, 77, 103),
    (105, 109, 136),
    (135, 143, 165),
    (154, 159, 180),
    (203, 204, 212),
    (222, 223, 227),
    (242, 242, 242),
]

# Gas housing ramp: same eight lightness steps, cyan/teal hue ("air / gas").
# Keep the ordering and the relative lightness of AE2's ramp so the pixel shading
# (outline, body, bevel highlight) survives the recolour unchanged.
GAS_HOUSING = [
    (30, 58, 70),
    (40, 76, 90),
    (58, 108, 124),
    (84, 140, 156),
    (108, 164, 178),
    (156, 204, 214),
    (192, 226, 232),
    (232, 246, 248),
]

# The three housing shades used by the in-drive 6x2 cell slices of AE2's sheet.
AE2_DRIVE_HOUSING = {
    (222, 223, 227): GAS_HOUSING[6],  # light body
    (203, 204, 212): GAS_HOUSING[5],  # body
    (179, 182, 198): GAS_HOUSING[4],  # side/bevel (B3B6C6)
}

MAPPING = dict(zip(AE2_ITEM_HOUSING, GAS_HOUSING))


def remap(image: Image.Image, mapping: dict[tuple[int, int, int], tuple[int, int, int]]) -> Image.Image:
    out = image.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            new = mapping.get((r, g, b))
            if new is not None:
                px[x, y] = (new[0], new[1], new[2], a)
    return out


def read_jar_entry(jar: str, name: str) -> Image.Image:
    with zipfile.ZipFile(jar) as zf:
        with zf.open(name) as fh:
            return Image.open(fh).convert("RGBA").copy()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", default="")
    args = parser.parse_args()

    os.makedirs(OUT_ITEM, exist_ok=True)
    os.makedirs(OUT_BLOCK, exist_ok=True)

    if not os.path.exists(AE2_JAR):
        raise SystemExit(f"AE2 jar not found: {AE2_JAR}")

    written = []

    # --- inventory / hand icons: AE2's own 16x16 cell sprite, housing recoloured ---
    cells = {}
    for tier in TIERS:
        src = read_jar_entry(AE2_JAR, f"assets/ae2/textures/item/item_storage_cell_{tier}.png")
        out = remap(src, MAPPING)
        path = os.path.join(OUT_ITEM, f"gas_cell_{tier}.png")
        out.save(path)
        cells[tier] = out
        written.append(path)

    # --- ME Drive sheet: 6x2 slice per tier at x=0, y=2*i, same layout AE2 uses ---
    sheet_src = read_jar_entry(AE2_JAR, "assets/ae2/textures/block/drive/drive_cells.png")
    sheet = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for index, tier in enumerate(TIERS):
        y = index * 2
        slice_img = sheet_src.crop((0, y, 6, y + 2))
        slice_img = remap(slice_img, AE2_DRIVE_HOUSING)
        sheet.paste(slice_img, (0, y))
    sheet_path = os.path.join(OUT_BLOCK, "drive_cells.png")
    sheet.save(sheet_path)
    written.append(sheet_path)

    for path in written:
        print("wrote", os.path.relpath(path, PROJECT))

    if args.preview:
        # Contact sheet: AE2 item cells, AE2 fluid cell, then our gas cells, at 8x.
        scale = 8
        gap = 4
        cell = 16 * scale
        rows = [
            ("AE2 item", [read_jar_entry(AE2_JAR, f"assets/ae2/textures/item/item_storage_cell_{t}.png") for t in TIERS]),
            ("AE2 fluid", [read_jar_entry(AE2_JAR, "assets/ae2/textures/item/fluid_storage_cell_1k.png")]),
            ("our gas", [cells[t] for t in TIERS]),
        ]
        width = max(len(r[1]) for r in rows) * (cell + gap) + 120
        height = len(rows) * (cell + gap) + gap
        canvas = Image.new("RGBA", (width, height), (24, 24, 28, 255))
        for row_index, (label, images) in enumerate(rows):
            y = gap + row_index * (cell + gap)
            for column, image in enumerate(images):
                x = 110 + column * (cell + gap)
                canvas.alpha_composite(image.resize((cell, cell), Image.NEAREST), (x, y))
        # drive sheet preview at the far right of the first row
        canvas.alpha_composite(
            sheet.resize((16 * scale // 2, 16 * scale // 2), Image.NEAREST),
            (110 + len(TIERS) * (cell + gap) + 8, gap + 8),
        )
        canvas.save(args.preview)
        print("preview", os.path.relpath(args.preview, PROJECT))


if __name__ == "__main__":
    main()
