# Copyright (c) 2026 DaKang233.
# SPDX-License-Identifier: LGPL-3.0-or-later

"""Generate the 1M-256M gas cell icons and ME Drive sheet.

The base shape and the teal housing are our existing 256k gas-cell art, so the
new tiers stay in the mod's palette. Only AE2's green tier-accent pixels are
recoloured, using accent ramps inspired by MEGA Cells' high tiers:
1M gold, 4M orange, 16M red, 64M pink, 256M magenta.

Usage:
    python tools/gen_large_gas_cell_textures.py
"""

from __future__ import annotations

import os
from collections import Counter

from PIL import Image

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(PROJECT, "src", "main", "resources", "assets",
                        "appliedcraftedenergistics", "textures", "item")
DRIVE_DIR = os.path.join(PROJECT, "src", "main", "resources", "assets",
                         "appliedcraftedenergistics", "textures", "block", "drive")

# Existing 256k icon uses these three green accent shades.
OLD_ACCENTS = {
    (78, 192, 78),
    (112, 226, 89),
    (141, 243, 90),
}

# Ordered dark -> mid -> light, sampled from MEGA Cells' large-cell side art.
NEW_ACCENTS = {
    "1m": [(255, 207, 64), (255, 243, 115), (250, 255, 191)],
    "4m": [(217, 91, 37), (229, 129, 57), (242, 167, 61)],
    "16m": [(170, 33, 43), (215, 62, 66), (240, 118, 101)],
    "64m": [(255, 128, 191), (250, 179, 199), (251, 202, 213)],
    "256m": [(167, 75, 142), (213, 113, 172), (230, 158, 203)],
}


def recolour(image: Image.Image, ramp: list[tuple[int, int, int]]) -> Image.Image:
    """Replace the three green accent shades, preserving the teal housing."""
    old_order = [(78, 192, 78), (112, 226, 89), (141, 243, 90)]
    mapping = dict(zip(old_order, ramp))
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a and (r, g, b) in mapping:
                nr, ng, nb = mapping[(r, g, b)]
                pixels[x, y] = (nr, ng, nb, a)
    return out


def main() -> None:
    base = Image.open(os.path.join(ITEM_DIR, "gas_cell_256k.png")).convert("RGBA")
    for tier, ramp in NEW_ACCENTS.items():
        recolour(base, ramp).save(os.path.join(ITEM_DIR, f"gas_cell_{tier}.png"))
        print("wrote", os.path.join("textures", "item", f"gas_cell_{tier}.png"))

    # The existing sheet is 16x16 with one 6x2 slice every two rows for 1k..256k.
    # Extend it to 16x32 and append one new slice per high tier.
    sheet_path = os.path.join(DRIVE_DIR, "drive_cells.png")
    sheet = Image.open(sheet_path).convert("RGBA")
    assert sheet.size == (16, 16), sheet.size
    extended = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    extended.alpha_composite(sheet, (0, 0))

    # The 256k slice is at y=8..10; use it as the template for the new rows.
    template = sheet.crop((0, 8, 6, 10))
    for index, (tier, ramp) in enumerate(NEW_ACCENTS.items()):
        y = 10 + index * 2
        extended.paste(recolour(template, ramp), (0, y))
        print("drive slice", tier, "at y", y)

    extended.save(sheet_path)
    print("wrote", os.path.join("textures", "block", "drive", "drive_cells.png"))


if __name__ == "__main__":
    main()
