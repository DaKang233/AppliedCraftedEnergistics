# Copyright (c) 2026 DaKang233.
# SPDX-License-Identifier: LGPL-3.0-or-later

"""Generate ACE's four 16x16 advanced upgrade-card textures.

Keep AE2's original card frame, shading and bottom contacts. Only the central
engraved symbol and the right-hand connector change: every card uses AE2's
advanced-card cyan connector, matching Applied Pneumatics' default card art.

Run: python tools/gen_card_textures.py
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


def template(jar: Path = AE2_JAR) -> Image.Image:
    with zipfile.ZipFile(jar) as archive:
        with archive.open("assets/ae2/textures/item/card_void.png") as source:
            image = Image.open(source).convert("RGBA")
        with archive.open("assets/ae2/textures/item/advanced_card.png") as source:
            advanced = Image.open(source).convert("RGBA")

    # Copy the cyan pixels at identical coordinates instead of approximating
    # the palette: AE2's connector has a slightly different final bottom shade.
    for y in range(16):
        for x in range(16):
            if image.getpixel((x, y))[:3] in GOLD and image.getpixel((x, y))[3]:
                image.putpixel((x, y), advanced.getpixel((x, y)))

    # Remove only the old glyph; preserve the frame and red bottom contacts.
    for y in range(2, 11):
        for x in range(3, 11):
            image.putpixel((x, y), BODY)
    return image


def make_card(base: Image.Image, glyph: tuple[str, ...]) -> Image.Image:
    if len(glyph) != 8 or any(len(row) != 8 or set(row) - set(".dm") for row in glyph):
        raise ValueError("Card glyphs must be 8x8 grids using only '.', 'd' and 'm'.")
    image = base.copy()
    strokes = {
        (ORIGIN[0] + x, ORIGIN[1] + y): DARK if value == "d" else MID
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


def preview(cards: list[Image.Image], path: Path) -> None:
    # Integer enlargement keeps every logical pixel sharp in the README.
    sheet = Image.new("RGB", (690, 180), (36, 39, 44))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=14)
    for index, (image, label) in enumerate(zip(cards, LABELS)):
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
    parser.add_argument("--preview", type=Path, default=PREVIEW)
    args = parser.parse_args()
    base = template(args.ae2_jar)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    cards = []
    for name, glyph in GLYPHS.items():
        image = make_card(base, glyph)
        image.save(args.output_dir / f"{name}.png")
        cards.append(image)
        print("wrote", args.output_dir / f"{name}.png")
    preview(cards, args.preview)
    print("wrote", args.preview)


if __name__ == "__main__":
    main()
