#!/usr/bin/env python3
"""tsun-markor: convert an OTF (CFF outlines) to a TTF (TrueType outlines).

Why: Chromium runs every web font through the OTS sanitizer, which rejects
Operator Mono's CFF table ("OTS parsing error: CFF : Failed to parse table").
TrueType-outline TTFs pass OTS. Android's own parser is lenient, which is why
the editor rendered the OTF fine while the preview silently fell back.

Usage: otf2ttf.py <input.otf> <output.ttf>
Requires: fontTools >= 4 (pip install --user fonttools)

Based on the standard fonttools otf2ttf recipe (cu2qu cubic->quadratic
conversion, the same pipeline Google Fonts uses). License: Apache 2.0.
"""
import sys

from fontTools.ttLib import TTFont, newTable
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen

MAX_ERR = 1.0  # conversion error tolerance (font units)
POST_FORMAT = 2.0


def glyphs_to_quadratic(glyph_set):
    quad_glyphs = {}
    for name in glyph_set.keys():
        glyph = glyph_set[name]
        tt_pen = TTGlyphPen(glyph_set)
        try:
            cu2qu_pen = Cu2QuPen(tt_pen, MAX_ERR, reverse_direction=True)
        except TypeError:
            # newer fontTools dropped the reverse_direction kwarg
            cu2qu_pen = Cu2QuPen(tt_pen, MAX_ERR)
        glyph.draw(cu2qu_pen)
        quad_glyphs[name] = tt_pen.glyph()
    return quad_glyphs


def otf_to_ttf(tt_font):
    glyph_order = tt_font.getGlyphOrder()
    tt_font["loca"] = newTable("loca")
    tt_font["glyf"] = glyf = newTable("glyf")
    glyf.glyphOrder = glyph_order
    glyf.glyphs = glyphs_to_quadratic(tt_font.getGlyphSet())
    for tag in ("CFF ", "VORG"):
        if tag in tt_font:
            del tt_font[tag]

    hmtx = tt_font["hmtx"]
    for glyph_name, glyph in glyf.glyphs.items():
        if getattr(glyph, "numberOfContours", 0) != 0 and not hasattr(glyph, "xMin"):
            # newer fontTools does not compute bounds during glyf.compile for
            # pen-generated glyphs - maxp.recalc needs xMin to fix the lsb
            glyph.recalcBounds(glyf)
        if hasattr(glyph, "xMin"):
            hmtx[glyph_name] = (hmtx[glyph_name][0], glyph.xMin)

    tt_font["maxp"] = maxp = newTable("maxp")
    maxp.tableVersion = 0x00010000
    maxp.maxZones = 1
    maxp.maxTwilightPoints = 0
    maxp.maxStorage = 0
    maxp.maxFunctionDefs = 0
    maxp.maxInstructionDefs = 0
    maxp.maxStackElements = 0
    maxp.maxSizeOfInstructions = 0
    maxp.maxComponentElements = max(
        (len(g.components) for g in glyf.glyphs.values() if hasattr(g, "components")),
        default=0,
    )
    maxp.compile(tt_font)

    post = tt_font["post"]
    post.formatType = POST_FORMAT
    post.extraNames = []
    post.mapping = {}
    post.glyphOrder = glyph_order

    tt_font.sfntVersion = "\000\001\000\000"
    tt_font["head"].indexToLocFormat = 0  # recomputed on compile


def main(argv):
    if len(argv) != 3:
        print(f"usage: {argv[0]} <input.otf> <output.ttf>", file=sys.stderr)
        return 2
    src, dst = argv[1], argv[2]
    font = TTFont(src)
    if "CFF " not in font:
        print(f"skip (already TrueType): {src}", file=sys.stderr)
        font.close()
        return 0
    otf_to_ttf(font)
    font.save(dst)
    font.close()
    print(f"converted {src} -> {dst}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
