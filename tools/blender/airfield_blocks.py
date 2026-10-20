"""Models hdfv 0.20's airfield blocks (the light fittings, windsock mast, holding sign, apron floodlight) the way
machines.py models the single-block machines, with its conventions (block pixels, the front facing north, at most 800
faces) and its exporter: OBJ + MTL into assets/hdfv/models/block/machine, the block models, its textures (0.22: 128 px,
machines.TEX; 64 before). The lenses
are coloured glass (FieldLamps in AeroCore draws the light itself, from the server's list of fittings, out to 8 km).
Shapes after common airfield fittings: elevated lights on a frangible stem, inset lights flush with the pavement, the
PAPI box with its three lenses under a hood, approach and flashing heads on yellow frangible masts, an obstruction
globe, the rotating beacon's drum.
Run: scripts/blender.ps1 airfield_blocks"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import kitshapes  # noqa: E402
import machines as mc  # noqa: E402
from machines import Model, P  # noqa: E402


# ------------------------------------------------------------------------------------------------ textures

def lens(hexcode, seed, glow=0.0):
    """Moulded glass: a Fresnel ring pattern, a highlight; brighter toward the middle when it glows."""
    y, x = np.mgrid[0:mc.TEX, 0:mc.TEX]
    r = np.hypot(x - mc.TEX / 2 + 0.5, y - mc.TEX / 2 + 0.5) / (mc.TEX / 2)
    rings = 0.9 + 0.1 * np.cos(r * 18 * np.pi)
    v = rings * (1 + 0.03 * mc.periodic(seed, 8, 8)) * (1 + glow * np.clip(1 - r, 0, 1))
    img = mc.shade(mc.rgb(hexcode), v)
    hi = np.clip(1 - np.hypot(x - mc.TEX * 0.32, y - mc.TEX * 0.3) / (mc.TEX * 0.12), 0, 1) ** 2 * 0.5
    return np.clip(img + hi[..., None], 0, 1)


GLYPHS = {
    'R': ['1110', '1001', '1001', '1110', '1010', '1001', '1001'],
    'W': ['10001', '10001', '10001', '10101', '10101', '11011', '10001'],
    'Y': ['10001', '10001', '01010', '00100', '00100', '00100', '00100'],
}


def sign_hold(seed):
    """A runway holding position sign's face: white letters on red, a white edge."""
    img = mc.shade(mc.rgb('#b3161c'), 1 + 0.02 * mc.periodic(seed, 10, 10))
    white = mc.rgb('#f4f4f0')
    e = 2 * mc.S   # 0.22: laid out in a 64 px block's pixels, S texture pixels to one
    img[:e, :], img[-e:, :], img[:, :e], img[:, -e:] = white, white, white, white
    scale, x = 4 * mc.S, 10 * mc.S
    for ch in 'RWY':
        g = GLYPHS[ch]
        for row, bits in enumerate(g):
            for col, b in enumerate(bits):
                if b == '1':
                    y0, x0 = 18 * mc.S + row * scale, x + col * scale
                    img[y0:y0 + scale, x0:x0 + scale] = white
        x += (len(g[0]) + 1) * scale
    return img


NEW_TEXTURES = {
    'lens_white': lambda: lens('#f6f0dc', 61), 'lens_green': lambda: lens('#3fb860', 62), 'lens_red': lambda: lens('#c8261e', 63),
    'lens_blue': lambda: lens('#3a58d0', 64), 'lens_xenon': lambda: lens('#dce8ff', 65), 'lens_clear': lambda: lens('#c9d6dc', 66),
    'lens_lit': lambda: lens('#fff2c8', 67, glow=0.6), 'sign_hold': lambda: sign_hold(68),
}
mc.TEXTURE_RECIPES.update(NEW_TEXTURES)
mc.FIT.add('sign_hold')


# ------------------------------------------------------------------------------------------------ shapes

def dome(m, cx, cz, y0, radius, height, mat, sides=12, rings=3):
    kitshapes.dome(m.bm, radius * P, height * P, y0 * P, sides, rings, m.m(mat), offset=(cx * P, -cz * P))


def elevated(lens_mat, stem=4.5):
    """An elevated edge light: a base flange bolted down, the frangible coupling, the stem, the lamp body, a glass dome."""
    m = Model()
    m.vcyl(8, 8, 0, 0.6, 2.6, 2.6, 12, 'dark_steel')
    for k in range(4):
        a = np.pi / 2 * k + np.pi / 4
        m.vcyl(8 + 1.9 * np.cos(a), 8 + 1.9 * np.sin(a), 0.6, 0.9, 0.35, 0.35, 6, 'steel')
    m.vcyl(8, 8, 0.6, 1.8, 1.0, 1.0, 10, 'paint_yellow')
    m.vcyl(8, 8, 1.8, stem, 0.75, 0.75, 10, 'paint_yellow')
    m.vcyl(8, 8, stem, stem + 1.0, 1.9, 1.9, 12, 'steel')
    dome(m, 8, 8, stem + 1.0, 1.8, 1.6, lens_mat)
    return m


def inset(lens_mat):
    """An inset light flush with the pavement: a cast disc, its bolt ring, two lens windows facing along the line."""
    m = Model()
    m.vcyl(8, 8, 0, 0.5, 3.0, 2.8, 16, 'steel')
    for k in range(6):
        a = np.pi / 3 * k
        m.vcyl(8 + 2.4 * np.cos(a), 8 + 2.4 * np.sin(a), 0.5, 0.65, 0.25, 0.25, 6, 'dark_steel')
    m.box((7.2, 0.5, 5.6), (8.8, 0.85, 6.6), lens_mat)
    m.box((7.2, 0.5, 9.4), (8.8, 0.85, 10.4), lens_mat)
    return m


def threshold():
    """A threshold and end light: green toward the approach (north), red toward the runway (south), on a stem."""
    m = Model()
    m.vcyl(8, 8, 0, 0.6, 2.6, 2.6, 12, 'dark_steel')
    m.vcyl(8, 8, 0.6, 1.8, 1.0, 1.0, 10, 'paint_yellow')
    m.vcyl(8, 8, 1.8, 4.3, 0.75, 0.75, 10, 'paint_yellow')
    m.box((5.6, 4.3, 6.2), (10.4, 6.6, 9.8), 'steel')
    m.box((5.9, 4.6, 5.9), (10.1, 6.3, 6.2), 'lens_green')
    m.box((5.9, 4.6, 9.8), (10.1, 6.3, 10.1), 'lens_red')
    m.box((5.4, 6.6, 6.0), (10.6, 7.0, 10.0), 'dark_steel')
    return m


def papi():
    """A PAPI unit: a box on four adjustable legs, three projector lenses facing the approach (north) under a hood."""
    m = Model()
    for x, z in ((3, 4), (13, 4), (3, 12), (13, 12)):
        m.tube((x, 0, z), (x, 3.2, z), 0.45, 6, 'dark_steel')
        m.vcyl(x, z, 0, 0.3, 1.0, 1.0, 8, 'dark_steel')
    m.box((2, 3, 3), (14, 8, 13), 'paint_grey')
    for x in (4.6, 8.0, 11.4):
        m.tube((x, 5.5, 3.0), (x, 5.5, 2.4), 1.45, 12, 'lens_white')
        m.tube((x, 5.5, 3.0), (x, 5.5, 2.2), 1.75, 12, 'dark_steel')
    m.box((2, 7.6, 1.4), (14, 8.0, 3.0), 'paint_grey')
    m.box((6, 8, 9), (10, 9, 12), 'vent')
    return m


def approach(head_mat, lens_mat, flasher=False):
    """An approach light: a concrete pad, a yellow frangible mast, the lamp head with its lens toward the approach."""
    m = Model()
    m.box((4, 0, 4), (12, 0.6, 12), 'concrete')
    m.vcyl(8, 8, 0.6, 1.6, 1.1, 1.1, 10, 'paint_yellow')
    m.vcyl(8, 8, 1.6, 10, 0.7, 0.7, 10, 'paint_yellow')
    m.box((5, 10, 6), (11, 13.4, 10.6), head_mat)
    m.tube((8, 11.7, 6.0), (8, 11.7, 5.4), 2.4, 14, lens_mat)
    m.tube((8, 11.7, 6.0), (8, 11.7, 5.2), 2.8, 14, 'dark_steel')
    if flasher:
        m.box((6, 9, 10.6), (10, 12, 12.4), 'paint_grey')   # its power supply
        m.box((6.4, 13.4, 7), (9.6, 13.8, 10), 'hazard')
    return m


def obstruction():
    """An obstruction light: a red globe on its housing."""
    m = Model()
    m.box((5, 0, 5), (11, 2, 11), 'paint_grey')
    m.vcyl(8, 8, 2, 2.6, 2.6, 2.6, 12, 'dark_steel')
    dome(m, 8, 8, 2.6, 2.5, 3.6, 'lens_red', sides=14, rings=4)
    return m


def beacon():
    """An aerodrome beacon: a pedestal, the drum with its white and green lenses on opposite sides, its cap."""
    m = Model()
    m.vcyl(8, 8, 0, 3, 4.6, 3.8, 14, 'paint_grey')
    m.vcyl(8, 8, 3, 3.6, 5.6, 5.6, 16, 'dark_steel')
    m.vcyl(8, 8, 3.6, 11.6, 5.2, 5.2, 16, 'dark_steel')
    m.tube((8, 7.6, 3.2), (8, 7.6, 2.6), 3.2, 14, 'lens_white')
    m.tube((8, 7.6, 12.8), (8, 7.6, 13.4), 3.2, 14, 'lens_green')
    m.vcyl(8, 8, 11.6, 13, 5.4, 3.8, 16, 'paint_grey')
    m.vcyl(8, 8, 13, 15, 1.0, 0.4, 8, 'steel')
    return m


def windsock():
    """The windsock's footing and the lower part of its mast (the rest, the frame and the sock: WindsockRenderer)."""
    m = Model()
    m.box((4.5, 0, 4.5), (11.5, 1.5, 11.5), 'concrete')
    m.vcyl(8, 8, 1.5, 2.6, 1.6, 1.6, 10, 'dark_steel')
    m.vcyl(8, 8, 2.6, 16, 0.8, 0.8, 10, 'paint_white')
    return m


def holding_sign():
    """A runway holding position sign: a red panel with white lettering both sides, on two frangible legs."""
    m = Model()
    for x in (2, 14):
        m.tube((x, 0, 8), (x, 4.2, 8), 0.5, 8, 'dark_steel')
        m.box((x - 1, 0, 7), (x + 1, 0.4, 9), 'concrete')
    m.box((0.5, 4, 7.3), (15.5, 11.5, 8.7), 'paint_red')
    m.box((0.8, 4.3, 7.1), (15.2, 11.2, 7.3), 'sign_hold')
    m.box((0.8, 4.3, 8.7), (15.2, 11.2, 8.9), 'sign_hold')
    return m


def floodlight(lit):
    """An apron floodlight: a grey mast, a cross arm, two lamp heads tilted down toward the apron (north)."""
    m = Model()
    m.box((5, 0, 5), (11, 1, 11), 'concrete')
    m.vcyl(8, 8, 1, 14.2, 1.0, 0.8, 10, 'paint_grey')
    m.box((2.5, 13.6, 7.2), (13.5, 14.6, 8.8), 'dark_steel')
    before = set(m.bm.faces)
    for x0 in (2.5, 8.7):
        m.box((x0, 12.4, 4.2), (x0 + 4.8, 15.6, 7.4), 'paint_grey')
        m.box((x0 + 0.3, 12.7, 3.9), (x0 + 4.5, 15.3, 4.2), 'lens_lit' if lit else 'lens_clear')
    heads = kitshapes.new_faces(m.bm, before)
    kitshapes.transform(m.bm, heads, kitshapes.rotation(-25, 'X', mc.B(8, 14, 7.4)))
    return m


BLOCKS = {
    'airfield_runway_edge': lambda: elevated('lens_white'),
    'airfield_runway_centre': lambda: inset('lens_white'),
    'airfield_threshold': threshold,
    'airfield_papi': papi,
    'airfield_approach': lambda: approach('steel', 'lens_white'),
    'airfield_approach_flasher': lambda: approach('paint_grey', 'lens_xenon', flasher=True),
    'airfield_taxi_edge': lambda: elevated('lens_blue', stem=3.5),
    'airfield_taxi_centre': lambda: inset('lens_green'),
    'airfield_obstruction': obstruction,
    'airfield_beacon': beacon,
    'windsock': windsock,
    'holding_sign': holding_sign,
    'apron_floodlight': lambda: floodlight(False),
    'apron_floodlight_on': lambda: floodlight(True),
}


def main():
    common.reset()
    os.makedirs(mc.OUT, exist_ok=True)
    written, used = 0, set()
    for name, build in BLOCKS.items():
        w, mats = mc.export_machine(name, build())
        written += w
        used |= mats
    for mat in sorted(used):
        written += common.write_if_changed(os.path.join(mc.TEXTURES, mat + '.png'), mc.png(mc.TEXTURE_RECIPES[mat]()))
    common.log('airfield blocks done:', len(BLOCKS), 'blocks,', len(used), 'textures,', written, 'files written')


main()
