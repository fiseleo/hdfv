"""Bakes hdfv's surface finishes (hdfv 0.11; 0.12 adds the vehicles' paints, chrome, tyre, trim and lenses): one seamless 256 x 256 texture per finish in finishes_spec.json, from
Blender's 4D noise and cell patterns on a torus mapping (so it tiles both ways), combined here into a neutral detail map
(mean brightness 0.82) with gloss in alpha, and the runtime list assets/hdfv/finishes.json that AeroCore reads.
0.20: a finish with "normalMap" also gets <id>_n.png, a tangent-space normal map from its recipe's height field (red x
along +u, green y along +v, blue a cavity term, as AeroCore 0.38's part shader reads it), and the opt-in keys "shading",
"glossScale", "normalStrength", "glass", "farGlass" pass into finishes.json.
0.22: every finish also gets a "beautify" object (AeroCore 0.39 lays it over the others while beautified shading is on):
"shading": 2, a normal map <id>_n.png from its recipe's relief (height: the same channels and patterns that colour it,
scaled to a slope, SLOPE or the spec's "slope") and the spec's own "beautify" keys (gloss, glass). The keys above it and
the colour textures are as in 0.21, so with beautified shading off a finish looks as it did.
Run: scripts/blender.ps1 textures [size]. Files are rewritten only when their bytes change."""
import hashlib
import json
import math
import os
import sys

import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # Blender does not add the script folder
import common  # noqa: E402

SIZE = int(common.args()[0]) if common.args() else 256
MEAN = 0.82   # Minecraft's white concrete, which part colours were tuned against


def noise(fu, fv, detail=4, roughness=0.55, distortion=0.0, offset=0.0):
    return ('noise', fu, fv, detail, roughness, distortion, offset)


def cells(fu, fv, offset=0.0, randomness=1.0):
    return ('voronoi', fu, fv, randomness, 'F1', offset)


# The channels each recipe bakes (three at most: one bake fills red, green and blue).
CHANNELS = {
    'brushed': [noise(2, 160, 6, 0.6), noise(4, 4, 3, 0.55, 0, 7.3), noise(48, 48, 4, 0.6, 0, 3.1)],
    'machined': [noise(1.5, 400, 4, 0.5), noise(3, 3, 3, 0.5, 0, 5.1), noise(30, 30, 4, 0.6, 0, 9.7)],
    'cast': [noise(40, 40, 6, 0.65), cells(60, 60, 2.2), noise(5, 5, 3, 0.5, 0, 4.4)],
    'forged': [noise(10, 10, 5, 0.6, 1.5), noise(3, 3, 3, 0.5, 0, 1.7), noise(60, 60, 3, 0.6, 0, 8.8)],
    'oxidised': [noise(2, 24, 5, 0.6, 1.0), noise(6, 6, 4, 0.6, 2.0, 2.2), noise(60, 60, 3, 0.6, 0, 5.0)],
    'smooth': [noise(8, 8, 3, 0.5), noise(2, 2, 2, 0.5, 0, 3.0), noise(40, 40, 2, 0.5, 0, 6.0)],
    'polished': [noise(2, 60, 4, 0.5), noise(5, 5, 4, 0.6, 1.0, 3.0), noise(40, 40, 3, 0.5, 0, 7.0)],
    'twill': [noise(1, 300, 3, 0.5), noise(300, 1, 3, 0.5, 0, 3.0), noise(8, 8, 2, 0.5, 0, 6.0)],
    'mat': [noise(6, 6, 6, 0.7, 4.0), noise(40, 40, 4, 0.6, 2.0, 5.0), noise(2, 2, 2, 0.5, 0, 9.0)],
    'stipple': [noise(200, 200, 2, 0.5), noise(6, 6, 3, 0.5, 0, 4.0), noise(50, 50, 2, 0.5, 0, 8.0)],
    'paint': [noise(30, 30, 3, 0.5), noise(4, 4, 3, 0.5, 0, 2.5), noise(120, 120, 2, 0.5, 0, 6.6)],
    'worn': [cells(14, 14), noise(6, 6, 4, 0.6, 2.0, 3.3), noise(40, 40, 3, 0.5, 0, 9.0)],
    'graphite': [noise(30, 30, 5, 0.6), noise(3, 3, 3, 0.5, 0, 3.0), noise(4, 120, 3, 0.5, 0, 6.0)],
    'speckle': [cells(90, 90), noise(20, 20, 4, 0.6, 0, 4.0), noise(3, 3, 2, 0.5, 0, 8.0)],
    'braid': [noise(40, 40, 3, 0.5)],
    'plastic': [noise(1, 80, 3, 0.5), noise(10, 10, 3, 0.5, 0, 3.0), noise(60, 60, 2, 0.5, 0, 6.0)],
    'flat': [],
    # 0.12 vehicles
    'flake': [cells(160, 160, 1.3), noise(6, 6, 3, 0.5, 0, 2.2), noise(60, 60, 2, 0.5, 0, 7.7)],
    'leather': [cells(40, 40, 4.1), noise(70, 70, 4, 0.6, 0, 1.9), noise(5, 5, 3, 0.5, 0, 8.3)],
    'weave': [noise(120, 120, 3, 0.5), noise(6, 6, 3, 0.5, 0, 4.7)],
    'carpet': [noise(90, 90, 5, 0.7, 2.0), cells(70, 70, 3.3), noise(8, 8, 3, 0.5, 0, 6.1)],
    'grain': [cells(60, 60, 5.2), noise(120, 120, 3, 0.6, 0, 2.4), noise(6, 6, 3, 0.5, 0, 9.4)],
    'wood': [noise(1, 60, 5, 0.6, 1.5), noise(4, 30, 4, 0.6, 0, 3.8), noise(40, 40, 3, 0.5, 0, 6.6)],
    'diamond': [noise(40, 40, 3, 0.5), noise(4, 4, 3, 0.5, 0, 5.5)],
    'skin': [noise(2, 180, 5, 0.6), noise(3, 3, 3, 0.5, 0, 4.2), noise(20, 20, 3, 0.6, 0, 7.1)],
    'prism': [noise(20, 20, 2, 0.5)],
    # 0.20: board-formed concrete (the tower): the boards' grain along u, a mottle, the pores
    'boards': [noise(1, 60, 4, 0.6), noise(8, 8, 3, 0.5, 0, 3.0), noise(70, 70, 3, 0.6, 0, 6.0)],
}


def norm(a):
    lo, hi = np.percentile(a, 2), np.percentile(a, 98)
    return np.clip((a - lo) / max(hi - lo, 1e-6), 0, 1)


def centred(a):
    return 2 * norm(a) - 1


def recipe(name, ch, f, u, v):
    """(brightness relative to 1, gloss 0-1, tint mix 0-1 or None) for a recipe from its baked channels."""
    c, g = f['contrast'], f['gloss']
    if name == 'brushed':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (s + 0.4 * m + 0.25 * d), g + 0.12 * s - 0.1 * np.clip(m, 0, 1), None
    if name == 'machined':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        rings = np.sin(2 * math.pi * 64 * v)   # turning marks: a whole number across the tile
        return 1 + c * (0.8 * s + 0.15 * rings + 0.3 * m + 0.2 * d), g + 0.08 * s, None
    if name == 'cast':
        grain, mottle = centred(ch[0]), centred(ch[2])
        pit = np.clip(1 - norm(ch[1]) / 0.18, 0, 1)
        return 1 + c * (0.8 * grain + 0.4 * mottle) - 1.0 * c * pit, g + 0.1 * grain - 0.2 * pit, None
    if name == 'forged':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.9 * s + 0.4 * m + 0.3 * d), g + 0.15 * s, None
    if name == 'oxidised':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.7 * s + 0.5 * m + 0.2 * d), g + 0.1 * s - 0.1 * m, norm(ch[1])
    if name == 'smooth':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.5 * s + 0.5 * m + 0.3 * d), g + 0.05 * s, None
    if name == 'polished':
        s, d = centred(ch[0]), centred(ch[2])
        tarnish = np.clip((norm(ch[1]) - 0.55) / 0.3, 0, 1)
        return 1 + c * (0.4 * s + 0.3 * d) - 0.6 * c * tarnish, g - 0.35 * tarnish + 0.05 * s, tarnish
    if name == 'twill':
        n = f.get('tows', 8)
        iu, iv = np.floor(u * n), np.floor(v * n)
        fu, fv = u * n - iu, v * n - iv
        warp = np.mod(iu - iv, 4) < 2   # 2/2 twill: each tow over two, under two, the pattern stepping one a row
        across = np.where(warp, fv, fu)
        profile = np.sqrt(np.sin(math.pi * across))
        fibre = np.where(warp, centred(ch[0]), centred(ch[1]))
        sheen = np.where(warp, 0.12, -0.12)
        return 1 + c * (0.6 * (profile - 0.6) + 0.25 * fibre + sheen) + 0.1 * c * centred(ch[2]), g + 0.12 * (profile - 0.5) + np.where(warp, 0.05, -0.05), None
    if name == 'mat':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.7 * s + 0.4 * m + 0.2 * d), g + 0.08 * s, None
    if name == 'stipple':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.7 * s + 0.4 * m + 0.3 * d), g + 0.05 * m, None
    if name == 'paint':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.6 * s + 0.5 * m + 0.3 * d), g + 0.12 * m, None
    if name == 'worn':
        d = centred(ch[2])
        chip = np.clip((norm(ch[1]) - 0.74) / 0.05, 0, 1) * np.clip((norm(ch[0]) - 0.15) / 0.2, 0, 1)
        return 1 + c * 0.5 * d - 0.3 * chip, g + 0.25 * chip, None
    if name == 'graphite':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.6 * s + 0.4 * m + 0.4 * d), g + 0.05 * d, None
    if name == 'speckle':
        m, d = centred(ch[1]), centred(ch[2])
        spot = np.clip(1 - norm(ch[0]) / 0.25, 0, 1)
        return 1 + c * (0.5 * m + 0.3 * d) + 1.5 * c * (spot - spot.mean()), g + 0.05 * m, None
    if name == 'braid':
        n, wires = f.get('strands', 6), 4
        a, b = (u + v) * n, (u - v) * n
        over_a = np.mod(np.floor(a) + np.floor(b), 2) == 0
        across = np.where(over_a, b - np.floor(b), a - np.floor(a))
        profile = np.sqrt(np.abs(np.sin(math.pi * wires * across)))
        edge = np.sin(math.pi * across) ** 0.3   # strands dip where they pass under
        return 1 + c * ((profile - 0.6) * 1.2 + (edge - 0.8)) + 0.15 * c * centred(ch[0]), g + 0.2 * (profile - 0.5), None
    if name == 'plastic':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.6 * s + 0.4 * m + 0.2 * d), g + 0.04 * s, None
    if name == 'flat':
        return np.ones_like(u), np.full_like(u, g), None
    # 0.12 vehicles
    if name == 'flake':
        # metallic flakes: small bright cells catching the light, under a smooth lacquer
        flake = np.clip(1 - norm(ch[0]) / 0.22, 0, 1)
        m, d = centred(ch[1]), centred(ch[2])
        return 1 + c * (1.2 * (flake - flake.mean()) + 0.3 * m + 0.2 * d), g + 0.2 * (flake - flake.mean()), None
    if name == 'leather':
        pore = np.clip(1 - norm(ch[0]) / 0.12, 0, 1)
        s, m = centred(ch[1]), centred(ch[2])
        return 1 + c * (0.5 * s + 0.4 * m) - 0.8 * c * pore, g + 0.1 * m - 0.15 * pore, None
    if name == 'weave':
        n = f.get('threads', 12)
        iu, iv = np.floor(u * n), np.floor(v * n)
        warp = np.mod(iu + iv, 2) == 0   # plain weave: over one, under one
        across = np.where(warp, v * n - iv, u * n - iu)
        profile = np.sqrt(np.sin(math.pi * across))
        return 1 + c * (0.8 * (profile - 0.6) + 0.2 * centred(ch[0]) + 0.15 * centred(ch[1])), g + 0.05 * (profile - 0.5), None
    if name == 'carpet':
        s, loop, m = centred(ch[0]), norm(ch[1]), centred(ch[2])
        return 1 + c * (0.8 * s + 0.4 * (loop - 0.5) + 0.3 * m), g + 0.03 * s, None
    if name == 'grain':
        cell = np.clip(1 - norm(ch[0]) / 0.3, 0, 1)
        s, m = centred(ch[1]), centred(ch[2])
        return 1 + c * (0.7 * (cell - cell.mean()) + 0.3 * s + 0.2 * m), g + 0.06 * s, None
    if name == 'wood':
        n = f.get('planks', 10)
        across = u * n - np.floor(u * n)
        seam = np.clip(1 - np.minimum(across, 1 - across) / 0.035, 0, 1)   # dark caulking between planks
        figure = centred(ch[0]) + 0.5 * np.sin(2 * math.pi * (8 * v + 2 * centred(ch[1])))
        plank = (np.mod(np.floor(u * n) * 7.3, 1.0) - 0.5) * 0.4   # each plank a little lighter or darker
        return 1 + c * (0.6 * figure + plank + 0.2 * centred(ch[2])) - 1.6 * c * seam, g - 0.2 * seam + 0.05 * figure, None
    if name == 'diamond':
        n = f.get('cells', 16)
        a, b = (u + v) * n, (u - v) * n
        bump = np.minimum(np.abs(a - np.round(a)), np.abs(b - np.round(b)))   # raised diamonds between grooves
        top = np.clip(bump / 0.25, 0, 1)
        return 1 + c * (0.8 * (top - 0.5) + 0.2 * centred(ch[0]) + 0.2 * centred(ch[1])), g + 0.1 * (top - 0.5), None
    if name == 'skin':
        s, m, d = centred(ch[0]), centred(ch[1]), centred(ch[2])
        return 1 + c * (0.6 * s + 0.6 * m + 0.2 * d), g + 0.1 * s - 0.08 * m, None
    if name == 'prism':
        n = f.get('ridges', 8)
        ridge = np.abs(np.sin(math.pi * n * u)) * np.abs(np.sin(math.pi * n * v))   # a lens's prism pattern
        return 1 + c * (1.2 * (ridge - ridge.mean()) + 0.1 * centred(ch[0])), g - 0.05 * ridge, None
    if name == 'boards':
        h, seam, pores, ties, board = boards(ch, f, u, v)
        grain, mottle = centred(ch[0]), centred(ch[1])
        return (1 + c * (0.35 * grain + 0.5 * mottle + board) - 1.2 * c * seam - 0.8 * c * pores - 1.5 * c * ties,
                g + 0.03 * mottle - 0.05 * seam, None)
    raise ValueError('unknown recipe ' + name)


def boards(ch, f, u, v):
    """Board-formed concrete's relief (0.20): the boards across the tile (horizontal, n of them), each a little proud or
    sunk, a groove where two met, the wood's grain printed in, pores, two pairs of form-tie holes; (height, seam, pores,
    ties, board's tone)."""
    n = f.get('boards', 8)
    across = v * n - np.floor(v * n)
    seam = np.clip(1 - np.minimum(across, 1 - across) / 0.035, 0, 1)
    board = (np.mod(np.floor(v * n) * 7.3, 1.0) - 0.5) * 0.3
    grain = centred(ch[0])
    pores = np.clip(1 - norm(ch[2]) / 0.1, 0, 1)
    ties = np.zeros_like(u)
    for tu in (0.25, 0.75):
        for tb in (1, 5):
            tv = (tb + 0.5) / n
            du = np.minimum(np.abs(u - tu), 1 - np.abs(u - tu))
            dv = np.minimum(np.abs(v - tv), 1 - np.abs(v - tv))
            ties = np.maximum(ties, np.clip(1 - np.hypot(du, dv) / 0.014, 0, 1) ** 0.5)
    height = 0.06 * grain + 1.5 * board - 2.5 * seam - 0.25 * pores - 2.5 * ties
    return height, seam, pores, ties, board


HEIGHTS = {'boards': lambda ch, f, u, v: boards(ch, f, u, v)[0]}


def height(name, ch, f, u, v):
    """0.22: the relief of a recipe's surface (any units: the normal map is scaled to a slope), from the same channels and
    patterns that colour it, so bumps and colour agree; None for a flat one."""
    if name == 'flat':
        return None
    if name == 'boards':
        return boards(ch, f, u, v)[0]
    if name == 'brushed':
        return 0.8 * centred(ch[0]) + 0.15 * centred(ch[2])   # the brush lines
    if name == 'machined':
        return 0.4 * centred(ch[0]) + 0.6 * np.sin(2 * math.pi * 64 * v)   # turning marks
    if name == 'cast':
        pit = np.clip(1 - norm(ch[1]) / 0.18, 0, 1)
        return 0.6 * centred(ch[0]) + 0.3 * centred(ch[2]) - 2.2 * pit   # sand grain, pits
    if name == 'forged':
        return 0.9 * centred(ch[0]) + 0.4 * centred(ch[1]) + 0.2 * centred(ch[2])   # hammer marks
    if name == 'oxidised':
        return 0.6 * centred(ch[0]) + 0.5 * centred(ch[2]) - 0.5 * norm(ch[1])
    if name in ('smooth', 'polished'):
        return 0.5 * centred(ch[0]) + 0.2 * centred(ch[2])
    if name == 'twill':
        n = f.get('tows', 8)
        iu, iv = np.floor(u * n), np.floor(v * n)
        warp = np.mod(iu - iv, 4) < 2
        across = np.where(warp, v * n - iv, u * n - iu)
        return np.sqrt(np.sin(math.pi * across)) + np.where(warp, 0.15, -0.15) + 0.1 * np.where(warp, centred(ch[0]), centred(ch[1]))
    if name in ('mat', 'skin'):
        return 0.8 * centred(ch[0]) + 0.5 * centred(ch[1])
    if name == 'stipple':
        return centred(ch[0]) + 0.3 * centred(ch[2])
    if name == 'paint':
        return 0.7 * centred(ch[0]) + 0.3 * centred(ch[2])   # orange peel
    if name == 'worn':
        chip = np.clip((norm(ch[1]) - 0.74) / 0.05, 0, 1) * np.clip((norm(ch[0]) - 0.15) / 0.2, 0, 1)
        return 0.3 * centred(ch[2]) - 1.5 * chip   # chips through the paint
    if name == 'graphite':
        return 0.5 * centred(ch[0]) + 0.6 * centred(ch[2])
    if name == 'speckle':
        return -np.clip(1 - norm(ch[0]) / 0.25, 0, 1) + 0.3 * centred(ch[1])
    if name == 'braid':
        n, wires = f.get('strands', 6), 4
        a, b = (u + v) * n, (u - v) * n
        over_a = np.mod(np.floor(a) + np.floor(b), 2) == 0
        across = np.where(over_a, b - np.floor(b), a - np.floor(a))
        return np.sqrt(np.abs(np.sin(math.pi * wires * across))) * np.sin(math.pi * across) ** 0.3
    if name == 'plastic':
        return 0.5 * centred(ch[0]) + 0.3 * centred(ch[2])
    if name == 'flake':
        return np.clip(1 - norm(ch[0]) / 0.22, 0, 1) + 0.2 * centred(ch[1])   # the flakes, a little proud under the lacquer
    if name == 'leather':
        pore = np.clip(1 - norm(ch[0]) / 0.12, 0, 1)
        return 0.4 * centred(ch[1]) + 0.3 * centred(ch[2]) - 1.5 * pore
    if name == 'weave':
        n = f.get('threads', 12)
        iu, iv = np.floor(u * n), np.floor(v * n)
        warp = np.mod(iu + iv, 2) == 0
        across = np.where(warp, v * n - iv, u * n - iu)
        return np.sqrt(np.sin(math.pi * across)) + 0.15 * centred(ch[0])
    if name == 'carpet':
        return centred(ch[0]) + 0.6 * (norm(ch[1]) - 0.5)   # the pile, its loops
    if name == 'grain':
        return np.clip(1 - norm(ch[0]) / 0.3, 0, 1) + 0.3 * centred(ch[1])
    if name == 'wood':
        n = f.get('planks', 10)
        across = u * n - np.floor(u * n)
        seam = np.clip(1 - np.minimum(across, 1 - across) / 0.035, 0, 1)
        figure = centred(ch[0]) + 0.5 * np.sin(2 * math.pi * (8 * v + 2 * centred(ch[1])))
        return 0.3 * figure - 2.5 * seam   # the grain, the caulked seams sunk
    if name == 'diamond':
        n = f.get('cells', 16)
        a, b = (u + v) * n, (u - v) * n
        return np.clip(np.minimum(np.abs(a - np.round(a)), np.abs(b - np.round(b))) / 0.25, 0, 1)   # raised diamonds
    if name == 'prism':
        n = f.get('ridges', 8)
        return np.abs(np.sin(math.pi * n * u)) * np.abs(np.sin(math.pi * n * v))
    raise ValueError('no relief for recipe ' + name)


# 0.22: how steep each recipe's relief is (the root mean square of the normal map's slope), a finish's "slope" overriding it
SLOPE = {'brushed': 0.12, 'machined': 0.15, 'cast': 0.35, 'forged': 0.3, 'oxidised': 0.2, 'smooth': 0.04, 'polished': 0.08, 'twill': 0.35,
         'mat': 0.2, 'stipple': 0.3, 'paint': 0.04, 'worn': 0.25, 'graphite': 0.2, 'speckle': 0.15, 'braid': 0.4, 'plastic': 0.1, 'flake': 0.05,
         'leather': 0.35, 'weave': 0.4, 'carpet': 0.45, 'grain': 0.3, 'wood': 0.3, 'diamond': 0.5, 'skin': 0.06, 'prism': 0.35}


def slope_normal_map(h, slope):
    """0.22: a tiling normal map of a height field scaled to a root mean square slope; blue a cavity term (low = darker)
    as deep as the relief is steep (0.7 of the slope, at most 0.25: a paint's 0.03, a casting's 0.25; a flat 0.3 for
    every finish made a smooth paint look like cloth)."""
    du = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) / 2
    dv = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) / 2
    rms = math.sqrt(float(np.mean(du * du + dv * dv)))
    k = slope / max(rms, 1e-9)
    lo, hi = np.percentile(h, 1), np.percentile(h, 99)
    depth = min(0.25, 0.7 * slope)
    cavity = 1 - depth * (1 - np.clip((h - lo) / max(hi - lo, 1e-6), 0, 1))
    nx, ny, nz = -k * du, -k * dv, np.ones_like(h)
    length = np.sqrt(nx * nx + ny * ny + nz * nz)
    rgb = np.stack([0.5 + 0.5 * nx / length, 0.5 + 0.5 * ny / length, cavity], axis=2)
    out = np.concatenate([rgb, np.ones(h.shape + (1,))], axis=2)
    return np.clip(np.round(out * 255), 0, 255).astype(np.uint8)


def normal_map(height, strength, cavity):
    """A tangent-space normal map of a height field that tiles (slopes by wrapped central differences), blue a cavity term."""
    du = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) / 2
    dv = (np.roll(height, -1, 0) - np.roll(height, 1, 0)) / 2
    nx, ny, nz = -strength * du, -strength * dv, np.ones_like(height)
    length = np.sqrt(nx * nx + ny * ny + nz * nz)
    rgb = np.stack([0.5 + 0.5 * nx / length, 0.5 + 0.5 * ny / length, cavity], axis=2)
    out = np.concatenate([rgb, np.ones(height.shape + (1,))], axis=2)
    return np.clip(np.round(out * 255), 0, 255).astype(np.uint8)


def luminance(rgb):
    return 0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]


def compose(f, ch, u, v):
    lum, gloss, mix = recipe(f['recipe'], ch, f, u, v)
    tint = f.get('tint')
    rgb = np.repeat(lum[..., None], 3, axis=2)
    if tint is not None and mix is not None:
        t = (mix * f.get('tintStrength', 0.3))[..., None]
        rgb = rgb * (np.array(tint[0]) * (1 - t) + np.array(tint[1]) * t)
    for _ in range(3):   # mean brightness to MEAN, within 0.2-1
        rgb = np.clip(rgb * (MEAN / max(luminance(rgb).mean(), 1e-6)), 0.2, 1.0)
    alpha = 0.5 + 0.5 * np.clip(gloss, 0, 1)
    out = np.concatenate([rgb, alpha[..., None]], axis=2)
    return np.clip(np.round(out * 255), 0, 255).astype(np.uint8), float(np.clip(gloss, 0, 1).mean())


def main():
    spec = json.load(open(os.path.join(common.TOOLS, 'finishes_spec.json'), encoding='utf-8'))
    common.reset()
    j, i = np.meshgrid((np.arange(SIZE) + 0.5) / SIZE, (np.arange(SIZE) + 0.5) / SIZE)
    u, v = j, i   # u across, v down the image (texture v grows downward in Minecraft)
    runtime, manifest, changed = {}, {'blender': bpy.app.version_string, 'size': SIZE, 'textures': {}}, 0
    for f in spec['finishes']:
        specs = CHANNELS[f['recipe']]
        baked = common.bake_channels(specs, SIZE) if specs else []
        ch = [np.array(b, dtype=np.float64).reshape(SIZE, SIZE) for b in baked]
        pixels, gloss = compose(f, ch, u, v)
        png = common.png_rgba(SIZE, SIZE, pixels.tobytes())
        path = os.path.join(common.ASSETS, 'textures', 'part', f['id'] + '.png')
        if common.write_if_changed(path, png):
            changed += 1
        runtime['hdfv:' + f['id']] = {'texture': 'hdfv:textures/part/' + f['id'] + '.png', 'tile': f['tile'], 'gloss': round(gloss, 3), 'metal': f['metal']}
        for key in ('clearcoat', 'flip', 'cutout', 'fresnel', 'shading', 'glossScale', 'normalStrength', 'glass', 'farGlass'):   # 0.12, 0.20: the part shader's settings
            if key in f:
                runtime['hdfv:' + f['id']][key] = f[key]
        if f.get('normalMap'):
            h = HEIGHTS[f['recipe']](ch, f, u, v)
            lo, hi = np.percentile(h, 1), np.percentile(h, 99)
            cavity = 0.55 + 0.45 * np.clip((h - lo) / max(hi - lo, 1e-6), 0, 1)
            npng = common.png_rgba(SIZE, SIZE, normal_map(h, f.get('relief', 6.0), cavity).tobytes())
            if common.write_if_changed(os.path.join(common.ASSETS, 'textures', 'part', f['id'] + '_n.png'), npng):
                changed += 1
            runtime['hdfv:' + f['id']]['normal'] = 'hdfv:textures/part/' + f['id'] + '_n.png'
            manifest['textures'][f['id'] + '_n'] = hashlib.sha256(npng).hexdigest()[:16]
        # 0.22: the beautified look (AeroCore 0.39 lays it over the keys above while "beautified shading" is on): shading 2,
        # a normal map from the recipe's relief (unless the finish already has one), the spec's "beautify" keys
        beautify = {'shading': 2}
        h = None if f.get('normalMap') else height(f['recipe'], ch, f, u, v)
        if h is not None:
            npng = common.png_rgba(SIZE, SIZE, slope_normal_map(h, f.get('slope', SLOPE[f['recipe']])).tobytes())
            if common.write_if_changed(os.path.join(common.ASSETS, 'textures', 'part', f['id'] + '_n.png'), npng):
                changed += 1
            beautify['normal'] = 'hdfv:textures/part/' + f['id'] + '_n.png'
            beautify['normalStrength'] = 1.0
            manifest['textures'][f['id'] + '_n'] = hashlib.sha256(npng).hexdigest()[:16]
        beautify.update(f.get('beautify', {}))
        runtime['hdfv:' + f['id']]['beautify'] = beautify
        manifest['textures'][f['id']] = hashlib.sha256(png).hexdigest()[:16]
        common.log('finish', f['id'], 'mean', round(float(luminance(pixels[..., :3] / 255.0).mean()), 3), 'gloss', round(gloss, 2))
    # fx.py's effect finishes (hdfv:fx_*) and glyphs.py's lettering (hdfv:glyphs) live in the same file: keep them
    old = os.path.join(common.ASSETS, 'finishes.json')
    if os.path.exists(old):
        for key, value in json.load(open(old, encoding='utf-8')).items():
            if key.startswith('hdfv:fx_') or key == 'hdfv:glyphs':
                runtime[key] = value
    if common.write_json_if_changed(os.path.join(common.ASSETS, 'finishes.json'), runtime):
        changed += 1
    common.write_json_if_changed(os.path.join(common.TOOLS, 'textures_manifest.json'), manifest)
    common.log('textures done:', len(spec['finishes']), 'finishes,', changed, 'files written')


main()
