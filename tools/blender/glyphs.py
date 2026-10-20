"""Draws hdfv's lettering atlas (hdfv 0.12): the capital letters, digits and the hyphen of registration marks, boat names
and number plates, as plain block strokes of one width on a 4 x 6 grid (our own stroke font: no font file, so nothing
to license), into an 8 x 6 grid of cells of a 1024 x 1024 atlas (assets/hdfv/textures/part/glyphs.png) and its entry
in assets/hdfv/finishes.json (hdfv:glyphs, cut out where alpha is low). White strokes, so the vertex colour paints
them; gloss in alpha (0.5 + 0.5 g) inside a stroke, 0 outside. The order of the cells is Lettering.ORDER in the code.
Pure numpy (runs in Blender's Python like fx.py). Run: scripts/blender.ps1 glyphs"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

ORDER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-'
SIZE, COLS, ROWS = 1024, 8, 6
CELL_W, CELL_H = SIZE // COLS, SIZE // ROWS
HALF = 0.42          # half the stroke's width, in grid units (the grid is 4 wide, 6 tall)
GLOSS = 0.6


def arc(cx, cy, rx, ry, d0, d1, n=None):
    """Points on an ellipse from one angle to another (degrees, counter-clockwise when d1 > d0)."""
    n = n or max(6, int(abs(d1 - d0) / 10))
    return [(cx + rx * math.cos(math.radians(d0 + (d1 - d0) * i / n)), cy + ry * math.sin(math.radians(d0 + (d1 - d0) * i / n))) for i in range(n + 1)]


def flipped(strokes):
    return [[(4 - x, 6 - y) for x, y in s] for s in strokes]


P = [[(0, 0), (0, 6), (2.6, 6)] + arc(2.6, 4.4, 1.4, 1.6, 90, -90) + [(2.6, 2.8), (0, 2.8)]]
SIX = [arc(2.6, 2.2, 2.6, 3.8, 70, 180), arc(2, 1.9, 2, 1.9, 180, 540)]
GLYPHS = {
    'A': [[(0, 0), (2, 6), (4, 0)], [(0.75, 2.2), (3.25, 2.2)]],
    'B': [[(0, 0), (0, 6), (2.6, 6)] + arc(2.6, 4.55, 1.4, 1.45, 90, -90) + [(2.6, 3.1), (0, 3.1)],
          [(2.7, 3.1)] + arc(2.7, 1.55, 1.3, 1.55, 90, -90) + [(2.7, 0), (0, 0)]],
    'C': [arc(2, 3, 2, 3, 45, 315)],
    'D': [[(0, 0), (0, 6), (1.8, 6)] + arc(1.8, 3, 2.2, 3, 90, -90) + [(1.8, 0), (0, 0)]],
    'E': [[(4, 6), (0, 6), (0, 0), (4, 0)], [(0, 3), (3, 3)]],
    'F': [[(4, 6), (0, 6), (0, 0)], [(0, 3), (3, 3)]],
    'G': [arc(2, 3, 2, 3, 45, 360) + [(2.2, 3)]],
    'H': [[(0, 0), (0, 6)], [(4, 0), (4, 6)], [(0, 3), (4, 3)]],
    'I': [[(2, 0), (2, 6)], [(1, 6), (3, 6)], [(1, 0), (3, 0)]],
    'J': [[(4, 6), (4, 1.6)] + arc(2, 1.6, 2, 1.6, 0, -180)],
    'K': [[(0, 0), (0, 6)], [(4, 6), (0, 2.2)], [(1.4, 3.4), (4, 0)]],
    'L': [[(0, 6), (0, 0), (4, 0)]],
    'M': [[(0, 0), (0, 6), (2, 2.5), (4, 6), (4, 0)]],
    'N': [[(0, 0), (0, 6), (4, 0), (4, 6)]],
    'O': [arc(2, 3, 2, 3, 0, 360)],
    'P': P,
    'Q': [arc(2, 3, 2, 3, 0, 360), [(2.4, 1.4), (4.1, -0.3)]],
    'R': P + [[(2.0, 2.8), (4, 0)]],
    'S': [arc(2, 4.5, 2, 1.5, 15, 270) + arc(2, 1.5, 2, 1.5, 90, -165)],
    'T': [[(0, 6), (4, 6)], [(2, 6), (2, 0)]],
    'U': [[(0, 6), (0, 2)] + arc(2, 2, 2, 2, 180, 360) + [(4, 2), (4, 6)]],
    'V': [[(0, 6), (2, 0), (4, 6)]],
    'W': [[(0, 6), (1, 0), (2, 3.5), (3, 0), (4, 6)]],
    'X': [[(0, 6), (4, 0)], [(0, 0), (4, 6)]],
    'Y': [[(0, 6), (2, 3), (4, 6)], [(2, 3), (2, 0)]],
    'Z': [[(0, 6), (4, 6), (0, 0), (4, 0)]],
    '0': [arc(2, 3, 1.7, 3, 0, 360)],
    '1': [[(0.8, 4.6), (2.2, 6), (2.2, 0)], [(0.8, 0), (3.6, 0)]],
    '2': [arc(2, 4.3, 1.9, 1.7, 160, -30) + [(0, 0), (4, 0)]],
    '3': [arc(2, 4.5, 1.8, 1.5, 150, -90) + arc(2, 1.55, 2, 1.55, 90, -150)],
    '4': [[(3, 0), (3, 6), (0, 1.6), (4, 1.6)]],
    '5': [[(3.8, 6), (0.4, 6), (0.2, 3.4)] + arc(2, 1.9, 2, 1.9, 125, -155)],
    '6': SIX,
    '7': [[(0, 6), (4, 6), (1.4, 0)]],
    '8': [arc(2, 4.55, 1.7, 1.45, -90, 270), arc(2, 1.55, 2, 1.55, 90, 450)],
    '9': flipped(SIX),
    '-': [[(0.6, 3), (3.4, 3)]],
}


def distance_to_strokes(px, py, strokes):
    """The distance from every pixel's point (grid units) to the nearest stroke segment."""
    best = np.full(px.shape, np.inf)
    for s in strokes:
        for (ax, ay), (bx, by) in zip(s[:-1], s[1:]):
            dx, dy = bx - ax, by - ay
            length2 = dx * dx + dy * dy
            t = np.clip(((px - ax) * dx + (py - ay) * dy) / length2, 0, 1) if length2 > 0 else 0
            d = np.hypot(px - (ax + t * dx), py - (ay + t * dy))
            best = np.minimum(best, d)
    return best


def main():
    assert set(GLYPHS) == set(ORDER), 'a glyph for every character'
    rgba = np.zeros((SIZE, SIZE, 4), dtype=np.float64)
    rgba[..., :3] = 1.0
    # the glyph box (4 x 6 units with its stroke's width round it) fills the cell less a margin
    scale = min((CELL_W - 16) / (4 + 2 * HALF), (CELL_H - 16) / (6 + 2 * HALF))
    y, x = np.mgrid[0:CELL_H, 0:CELL_W]
    for i, ch in enumerate(ORDER):
        col, row = i % COLS, i // COLS
        # pixel centres to grid units: the box centred in the cell, y up
        gx = (x + 0.5 - CELL_W / 2) / scale + 2
        gy = (CELL_H / 2 - (y + 0.5)) / scale + 3
        d = distance_to_strokes(gx, gy, GLYPHS[ch])
        edge = 1.0 / scale   # a pixel, in grid units
        cover = np.clip((HALF - d) / edge + 0.5, 0, 1)
        alpha = np.where(cover > 0, (0.5 + 0.5 * GLOSS) * cover, 0.0)
        rgba[row * CELL_H:(row + 1) * CELL_H, col * CELL_W:(col + 1) * CELL_W, 3] = alpha
    pixels = np.clip(np.round(rgba * 255), 0, 255).astype(np.uint8)
    changed = common.write_if_changed(os.path.join(common.ASSETS, 'textures', 'part', 'glyphs.png'), common.png_rgba(SIZE, SIZE, pixels.tobytes()))
    path = os.path.join(common.ASSETS, 'finishes.json')
    runtime = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {}
    runtime['hdfv:glyphs'] = {'texture': 'hdfv:textures/part/glyphs.png', 'tile': 1.0, 'gloss': GLOSS, 'metal': 0, 'cutout': 0.25}
    changed += common.write_json_if_changed(path, runtime)
    common.log('glyphs done:', len(ORDER), 'glyphs,', changed, 'files written')


main()
