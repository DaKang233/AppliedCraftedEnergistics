# Copyright (c) 2026 DaKang233.
# SPDX-License-Identifier: LGPL-3.0-or-later

"""Generate ACE's native 16x16 upgrade-card and gas-sample textures.

Keep AE2's original card frame, shading and bottom contacts. Only the central
engraved symbol and the right-hand connector change: every card uses AE2's
advanced-card cyan connector, matching Applied Pneumatics' default card art.
The new safety card instead keeps the basic gold connector required by its recipe.

Run: python tools/gen_card_textures.py
New interface/bus cards and sample: python tools/gen_card_textures.py --new-items-only
Requires Pillow and the AE2 jar already supplied in libs/.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import zipfile

from PIL import Image, ImageDraw, ImageFont


PROJECT = Path(__file__).resolve().parent.parent
AE2_JAR = PROJECT / "libs" / "appliedenergistics2-19.2.20.jar"
OUT = PROJECT / "src/main/resources/assets/appliedcraftedenergistics/textures/item"
PREVIEW = PROJECT / "docs/images/upgrade_cards.png"

BODY = (165, 167, 172, 255)
DARK = (97, 102, 109, 255)
MID = (121, 126, 132, 255)
HIGHLIGHT = (193, 195, 199, 255)
GOLD = {(178, 100, 17), (220, 150, 19), (233, 177, 21), (250, 214, 74)}
ORIGIN = (3, 3)

# Eight logical pixels across and down. A dot leaves the original face visible;
# d/m are the two AE2 engraving shades. A one-pixel highlight is added below
# strokes where it does not cover another stroke or extend past the card face.
GLYPHS = {
    # Semicircular gauge, flat base; needle rises from the pivot to the upper right.
    "pressure_card": (
        "..dddd..",
        ".d....d.",
        "d....d.d",
        "d...d..d",
        "d..dd..d",
        "dddddddd",
        "........",
        "........",
    ),
    # Hollow central square with four independent outward-facing corner arrows.
    "volume_card": (
        "dd...dd.",
        "d.....d.",
        "..ddd...",
        "..d.d...",
        "..ddd...",
        "d.....d.",
        "dd...dd.",
        "........",
    ),
    # Four separate 2x2 tiles, with two untouched pixels between their columns.
    "type_expansion_card": (
        "........",
        ".dd..dd.",
        ".mm..mm.",
        "........",
        "........",
        ".dd..dd.",
        ".mm..mm.",
        "........",
    ),
    # Upward vent arrow, a gap, then a lower open-topped container.
    "relief_valve_card": (
        "...dd...",
        "..dddd..",
        ".dddddd.",
        "...dd...",
        "........",
        ".d....d.",
        ".dddddd.",
        "........",
    ),
}

LABELS = ("Pressure", "Volume", "Types", "Relief Valve")

# New machine cards are separate from the original four cell cards. No existing
# output is overwritten when --new-items-only is used.
NEW_GLYPHS = {
    # Three independent slider rails with knobs at different heights.
    "slot_control_card": (
        "m..m..m.",
        "dd.m..m.",
        "dd.m..m.",
        "m..dd.m.",
        "m..dd.m.",
        "m..m..dd",
        "m..m..dd",
        "........",
    ),
    # Danger triangle with a red exclamation mark; not a lock/security card.
    "safety_valve_card": (
        "...d....",
        "..d.d...",
        "..drd...",
        ".d.r.d..",
        ".d...d..",
        "d..r..d.",
        "ddddddd.",
        "........",
    ),
    # Adjustable pressure setpoint: top marker, low/high scale and three ticks.
    "gas_pressure_card": (
        "..ddd...",
        "...d....",
        "...d....",
        "mmmmdddd",
        "m..d...d",
        "...d....",
        "........",
        "........",
    ),
}
NEW_ITEM_NAMES = ("gas_sample", "slot_control_card", "safety_valve_card", "gas_pressure_card")
NEW_LABELS = ("Gas Sample", "Slot Control", "Safety", "Gas Pressure")
NEW_PREVIEW = PROJECT / "docs/images/interface_items.png"
RED = (170, 33, 43, 255)

# A gas identity token, rather than a gas-filled canister or a fourth upgrade
# card. Three stepped puffs carry an overlapping white identification tag.
SAMPLE_PIXELS = (
    '................',
    '......oooo......',
    '.....ollllo.....',
    '....olhhlllo....',
    '....olhllllo....',
    '....ollllllo....',
    '...ooolllllo....',
    '..olllolllo.....',
    '.olhllloooo.....',
    '.olllllommmo....',
    '.ollllommwwwwdo.',
    '..olllommwddwwo.',
    '...oooommwwwwwo.',
    '.......omwdddwo.',
    '........o.oooo..',
    '................',
)

SAMPLE_PALETTE = {
    "o": (58, 108, 124, 255),
    "l": (156, 204, 214, 255),
    "h": (232, 246, 248, 255),
    "m": (108, 164, 178, 255),
    "d": DARK,
    "w": (222, 223, 226, 255),
}


def make_gas_sample() -> Image.Image:
    if len(SAMPLE_PIXELS) != 16 or any(len(row) != 16 for row in SAMPLE_PIXELS):
        raise ValueError("Gas sample must be a native 16x16 pixel grid.")
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(SAMPLE_PIXELS):
        for x, value in enumerate(row):
            if value != ".":
                image.putpixel((x, y), SAMPLE_PALETTE[value])
    return image



def template(jar: Path = AE2_JAR, is_advanced: bool = True) -> Image.Image:
    with zipfile.ZipFile(jar) as archive:
        with archive.open("assets/ae2/textures/item/card_void.png") as source:
            image = Image.open(source).convert("RGBA")
        with archive.open("assets/ae2/textures/item/advanced_card.png") as source:
            advanced = Image.open(source).convert("RGBA")

    # Copy the cyan pixels at identical coordinates instead of approximating
    # the palette: AE2's connector has a slightly different final bottom shade.
    for y in range(16):
        for x in range(16):
            if is_advanced and image.getpixel((x, y))[:3] in GOLD and image.getpixel((x, y))[3]:
                image.putpixel((x, y), advanced.getpixel((x, y)))

    # Remove only the old glyph; preserve the frame and red bottom contacts.
    for y in range(2, 11):
        for x in range(3, 11):
            image.putpixel((x, y), BODY)
    return image


def make_card(base: Image.Image, glyph: tuple[str, ...]) -> Image.Image:
    if len(glyph) != 8 or any(len(row) != 8 or set(row) - set(".dmr") for row in glyph):
        raise ValueError("Card glyphs must be 8x8 grids using only '.', 'd', 'm' and 'r'.")
    image = base.copy()
    strokes = {
        (ORIGIN[0] + x, ORIGIN[1] + y): {"d": DARK, "m": MID, "r": RED}[value]
        for y, row in enumerate(glyph)
        for x, value in enumerate(row)
        if value != "."
    }
    for x, y in strokes:
        if y < 10 and (x, y + 1) not in strokes:
            image.putpixel((x, y + 1), HIGHLIGHT)
    for position, color in strokes.items():
        image.putpixel(position, color)
    return image


def preview(cards: list[Image.Image], path: Path, labels: tuple[str, ...] = LABELS) -> None:
    # Integer enlargement keeps every logical pixel sharp in the README.
    sheet = Image.new("RGB", (690, 180), (36, 39, 44))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=14)
    for index, (image, label) in enumerate(zip(cards, labels)):
        x = 18 + index * 172
        enlarged = image.resize((128, 128), Image.Resampling.NEAREST)
        sheet.paste(enlarged, (x, 12), enlarged)
        draw.text((x + 64, 153), label, font=font, fill=(225, 227, 231), anchor="mt")
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ae2-jar", type=Path, default=AE2_JAR)
    parser.add_argument("--output-dir", type=Path, default=OUT)
    parser.add_argument("--preview", type=Path)
    parser.add_argument("--new-items-only", action="store_true",
                        help="Generate only gas sample and the three new interface/bus cards.")
    args = parser.parse_args()
    base = template(args.ae2_jar)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    cards = []
    names = NEW_ITEM_NAMES if args.new_items_only else tuple(GLYPHS)
    for name in names:
        if name == "gas_sample":
            image = make_gas_sample()
        elif args.new_items_only:
            card_base = template(args.ae2_jar, is_advanced=False) if name == "safety_valve_card" else base
            image = make_card(card_base, NEW_GLYPHS[name])
        else:
            image = make_card(base, GLYPHS[name])
        image.save(args.output_dir / f"{name}.png")
        cards.append(image)
        print("wrote", args.output_dir / f"{name}.png")
    preview_path = args.preview or (NEW_PREVIEW if args.new_items_only else PREVIEW)
    preview(cards, preview_path, NEW_LABELS if args.new_items_only else LABELS)
    print("wrote", preview_path)


if __name__ == "__main__":
    main()
