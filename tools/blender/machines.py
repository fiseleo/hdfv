"""Models hdfv's single-block machines (hdfv 0.12, M1) and writes each as OBJ + MTL into assets/hdfv/models/block/machine,
its block model (NeoForge's OBJ loader) into assets/hdfv/models/block/<id>.json and its textures (64 px a block) into
assets/hdfv/textures/block/machine.

Conventions: shapes are written in the game's block pixels (0-16, x east, y up, z south; the front faces north, as a
block facing north is drawn without turning); Blender's frame is the exporter's (game = Blender x, z, -y). Texture
coordinates: most materials are projected from the face's position in the block (one block is one texture, 64 px,
so neighbouring faces and blocks meet without a seam); decal materials (gauge, panel, vent, hazard, label, screen,
blueprint, core) are stretched over their own face. Faces of more than four corners are triangulated. At most 800 faces
a block. 0.22: 128 px a block (TEX; the recipes are still written in a 64 px block's pixels, grid() and box()). Every texture is drawn here from fixed seeds (numpy), every file written only when its bytes change.

M2: the multiblocks' member blocks, controllers, drives, refinery vessels and connectors keep their cube models; their
16 px textures (kept as sources in tools/blender/blocks16) are redrawn at 64 px into assets/hdfv/textures/block: each
pixel four, relief from the picture's brightness (raised panels lit from the upper left, their edges shaded), a fine
grain over all. The pictures and colours stay theirs; models' texture coordinates are unchanged (they are in pixels of
16 whatever the size).
Run: scripts/blender.ps1 machines"""
import math
import os
import sys

import bmesh
import bpy
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # Blender does not add the script folder
import common  # noqa: E402
import details  # noqa: E402
import kitshapes  # noqa: E402

OUT = os.path.join(common.ASSETS, 'models', 'block', 'machine')
MODELS = os.path.join(common.ASSETS, 'models', 'block')
TEXTURES = os.path.join(common.ASSETS, 'textures', 'block', 'machine')
P = 1 / 16
TEX = 128   # 0.22 (64 before): a block's texture; the recipes below are written in a 64 px block's pixels
S = TEX // 64
FIT = {'gauge', 'panel', 'vent', 'hazard', 'label', 'screen', 'blueprint', 'core'}
MAX_FACES = 800


# ------------------------------------------------------------------------------------------------ textures

def periodic(seed, su, sv, roughness=0.0, size=TEX):
    """Tiling noise (mean 0, deviation 1): random phases under exp(-(ku/su)^2 - (kv/sv)^2), some grain from roughness."""
    rng = np.random.default_rng(seed)
    k = np.fft.fftfreq(size, 1.0 / size)
    kv, ku = np.meshgrid(k, k, indexing='ij')
    env = np.exp(-(ku / su) ** 2 - (kv / sv) ** 2) + roughness / (1 + ku ** 2 + kv ** 2)
    env[0, 0] = 0
    img = np.real(np.fft.ifft2(env * (rng.normal(size=(size, size)) + 1j * rng.normal(size=(size, size)))))
    return (img - img.mean()) / max(img.std(), 1e-12)


def rgb(hexcode):
    return np.array([int(hexcode[i:i + 2], 16) for i in (1, 3, 5)], dtype=float) / 255


def shade(base, value):
    """An image of a base colour scaled by a value field."""
    return np.clip(base[None, None, :] * value[..., None], 0, 1)


def png(img):
    a = np.concatenate([img, np.ones(img.shape[:2] + (1,))], axis=2)
    return common.png_rgba(img.shape[1], img.shape[0], (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8).tobytes())


def painted(hexcode, seed):
    """Paint on steel: a faint orange peel, a few lighter scuffs."""
    v = 1 + 0.035 * periodic(seed, 10, 10) + 0.02 * periodic(seed + 1, 30, 30, 0.2)
    scuff = np.clip(periodic(seed + 2, 4, 24) - 1.9, 0, 1) * 0.25
    return shade(rgb(hexcode), v + scuff)


def metal(hexcode, seed, brushed=True):
    v = 1 + 0.06 * (periodic(seed, 30, 1.5) if brushed else periodic(seed, 6, 6)) + 0.03 * periodic(seed + 1, 30, 30, 0.3)
    return shade(rgb(hexcode), v)


def cast(hexcode, seed):
    pits = np.clip(periodic(seed + 3, 28, 28, 0.5) - 1.6, 0, 2) * 0.12
    return shade(rgb(hexcode), 1 + 0.08 * periodic(seed, 8, 8) + 0.05 * periodic(seed + 1, 28, 28, 0.5) - pits)


def bricks(seed):
    y, x = grid()
    row = y // 16
    xs = (x + (row % 2) * 16) % 32
    mortar = (y % 16 < 2) | (xs < 2)
    tone = 1 + 0.12 * periodic(seed, 3, 3) + 0.05 * periodic(seed + 1, 30, 30)
    brick = shade(rgb('#a8643a'), tone)
    return np.where(mortar[..., None], shade(rgb('#8c8072'), 1 + 0.05 * periodic(seed + 2, 20, 20)), brick)


def wood(seed):
    y, x = grid()
    grain = 1 + 0.1 * periodic(seed, 1.5, 16) + 0.04 * periodic(seed + 1, 30, 30)
    gap = (y % 16) < 1
    return np.where(gap[..., None], rgb('#4a3420')[None, None, :], shade(rgb('#9a7046'), grain))


def concrete(seed):
    return shade(rgb('#9c9b95'), 1 + 0.05 * periodic(seed, 10, 10) + 0.06 * periodic(seed + 1, 32, 32, 0.6))


def insulation(seed):
    y, x = grid()
    seam = ((y % 32) < 1) | ((y % 32) >= 31)
    base = shade(rgb('#e3e4e0'), 1 + 0.025 * periodic(seed, 8, 8))
    return np.where(seam[..., None], shade(rgb('#a9aba8'), np.ones((TEX, TEX))), base)


def frost(seed):
    f = np.clip(0.5 + 0.35 * periodic(seed, 6, 6) + 0.2 * periodic(seed + 1, 30, 30, 0.5), 0, 1)
    return np.clip(rgb('#dfe8ee')[None, None, :] * (0.9 + 0.1 * f[..., None]), 0, 1)


def coil(seed):
    y, x = grid()
    band = 0.75 + 0.25 * np.cos(2 * np.pi * y / 4)
    return shade(rgb('#b8703c'), band * (1 + 0.05 * periodic(seed, 30, 2)))


def glow(seed):
    f = np.clip(0.5 + 0.3 * periodic(seed, 5, 5), 0, 1)[..., None]
    return np.clip(rgb('#ff6a12')[None, None, :] * (1 - f) + rgb('#ffd060')[None, None, :] * f, 0, 1)


def glass(seed):
    y, x = grid()
    streak = np.clip(1 - np.abs((x + y) % 48 - 12) / 6, 0, 1) * 0.25
    return np.clip(shade(rgb('#26343e'), np.ones((TEX, TEX))) + streak[..., None] * rgb('#9fb6c4')[None, None, :], 0, 1)


def grid():
    """0.22: (y, x) over the texture in a 64 px block's pixels (pixel corners, S pixels to one)."""
    y, x = np.mgrid[0:TEX, 0:TEX]
    return y / S, x / S


def box(img, y0, y1, x0, x1, colour):
    """0.22: fills rows y0-y1 and columns x0-x1 given in a 64 px block's pixels."""
    img[int(y0 * S):int(y1 * S), int(x0 * S):int(x1 * S)] = colour


def disc(cx, cy, r):
    y, x = grid()
    return np.hypot(x + 0.5 / S - cx, y + 0.5 / S - cy) < r


def gauge(seed):
    img = np.tile(rgb('#2a2c30'), (TEX, TEX, 1))
    img[disc(32, 32, 30)] = rgb('#c9ccd0')
    img[disc(32, 32, 26)] = rgb('#f2f1ea')
    y, x = grid()
    y, x = y + 0.5 / S, x + 0.5 / S
    a = np.arctan2(y - 32, x - 32)
    r = np.hypot(x - 32, y - 32)
    ticks = (r > 20) & (r < 25) & (np.abs(((a + np.pi) % (np.pi / 6)) - np.pi / 12) < 0.05)
    img[ticks] = rgb('#202020')
    red = (r > 20) & (r < 25) & (a > -0.6) & (a < 0.2)
    img[red] = rgb('#c0281e')
    # the needle from the centre toward upper right
    t = np.clip(((x - 32) * 0.7 + (y - 32) * -0.7) / 22, 0, 1)
    d = np.abs((x - 32) * 0.7 + (y - 32) * 0.7)
    img[(d < 1.3) & (t > 0) & (t < 1) & (r < 23)] = rgb('#b01818')
    img[disc(32, 32, 3)] = rgb('#202020')
    return img


def panel(seed):
    img = shade(rgb('#3a3d42'), 1 + 0.03 * periodic(seed, 20, 20))
    box(img, 4, 22, 6, 58, rgb('#101418'))
    box(img, 6, 20, 8, 56, rgb('#1f6a46') * 0.6)
    box(img, 9, 11, 10, 40, rgb('#66e0a0'))
    box(img, 14, 16, 10, 30, rgb('#66e0a0'))
    for i, c in enumerate(['#d23a2a', '#e9b02e', '#3fa64a', '#3a6fd0']):
        cx = 12 + 13 * i
        img[disc(cx, 36, 4.5)] = rgb('#1b1c1e')
        img[disc(cx, 36, 3.5)] = rgb(c)
    img[disc(16, 52, 7)] = rgb('#1b1c1e')
    img[disc(16, 52, 5.5)] = rgb('#b8bcc2')
    box(img, 46, 58, 30, 58, rgb('#c9cbcf'))
    box(img, 49, 51, 33, 55, rgb('#555555'))
    box(img, 53, 55, 33, 48, rgb('#555555'))
    return img


def vent(seed):
    y, x = grid()
    slot = (y % 8 < 4) & (x > 4) & (x < 60) & (y > 4) & (y < 60)
    base = shade(rgb('#6f757b'), 1 + 0.03 * periodic(seed, 20, 20))
    return np.where(slot[..., None], shade(rgb('#1c1e21'), 1 - 0.3 * (y % 8)[..., None][..., 0] / 4), base)


def hazard(seed):
    y, x = grid()
    stripe = ((x + y) // 11) % 2 == 0
    return np.where(stripe[..., None], rgb('#e0b020')[None, None, :], rgb('#1d1d1f')[None, None, :]) * (1 + 0.03 * periodic(seed, 20, 20))[..., None]


def label(seed):
    img = np.tile(rgb('#c9ccd0'), (TEX, TEX, 1))
    b = 3 * S
    img[:b], img[-b:], img[:, :b], img[:, -b:] = rgb('#7d8288'), rgb('#7d8288'), rgb('#7d8288'), rgb('#7d8288')
    for i, w in enumerate([46, 30, 40, 22, 36]):
        box(img, 9 + i * 10, 12 + i * 10, 8, 8 + w, rgb('#3a3d42'))
    return img


def screen(seed):
    img = np.tile(rgb('#14181b'), (TEX, TEX, 1))
    box(img, 6, 28, 6, 58, rgb('#0b2a1a'))
    for i in range(6):
        box(img, 10, 24, 9 + i * 8, 14 + i * 8, rgb('#58e08a'))
    box(img, 34, 58, 6, 58, rgb('#0b2a1a'))
    for i in range(4):
        box(img, 40, 52, 12 + i * 11, 19 + i * 11, rgb('#e8b84a'))
    return img


def blueprint(seed):
    img = shade(rgb('#22528c'), 1 + 0.03 * periodic(seed, 20, 20))
    y, x = grid()
    lines = ((x % 8) < 1) | ((y % 8) < 1)
    img[lines] = img[lines] * 0.8 + 0.2 * rgb('#9cc0e6')
    ring = np.abs(np.hypot(x - 36, y - 30) - 14) < 0.9
    img[ring] = rgb('#e8f0ff')
    img[(np.abs(y - 30) < 0.6) & (x > 10) & (x < 58)] = rgb('#e8f0ff')
    box(img, 50, 52, 8, 40, rgb('#e8f0ff'))
    return img


def core(seed):
    y, x = grid()
    r = np.hypot(x + 0.5 / S - 32, y + 0.5 / S - 32) / 32
    swirl = 0.5 + 0.5 * np.sin(8 * np.arctan2(y - 32, x - 32) + 10 * r)
    c = rgb('#4a1a7a')[None, None, :] * (1 - r[..., None]) + rgb('#c070ff')[None, None, :] * (swirl * np.clip(1 - r, 0, 1))[..., None]
    return np.clip(c + 0.25 * np.clip(1 - 3 * r, 0, 1)[..., None], 0, 1)


BLOCKS16 = os.path.join(common.TOOLS, 'blocks16')
BLOCK_TEXTURES = os.path.join(common.ASSETS, 'textures', 'block')


def read_png(path):
    """A PNG's pixels (rows top first, RGBA 0-1, as stored: no colour conversion)."""
    img = bpy.data.images.load(path, check_existing=False)
    img.colorspace_settings.name = 'Non-Color'
    w, h = img.size
    px = np.array(img.pixels[:], dtype=float).reshape(h, w, 4)[::-1]
    bpy.data.images.remove(img)
    return px


def blur(a):
    """A [1 2 1] / 4 blur both ways, wrapping (textures tile)."""
    a = (np.roll(a, 1, 0) + 2 * a + np.roll(a, -1, 0)) / 4
    return (np.roll(a, 1, 1) + 2 * a + np.roll(a, -1, 1)) / 4


def enhance(src, seed):
    """A 16 px picture at TEX px (0.22: 128; 64 before): each pixel k by k, relief from its brightness lit from the
    upper left, a fine grain. 0.22: the relief is of the opaque pixels only (a hole is not a dark pit: in 0.21 every
    bar of a grating was lit full white along its edges) and fades at a hole's edge, the light stays within 0.82-1.18
    (0.72-1.28 before: whole panels came out washed out), and the mean brightness of the opaque pixels is the picture's."""
    k = TEX // src.shape[0]
    rgb16, a16 = src[..., :3], src[..., 3]
    up = np.kron(rgb16, np.ones((k, k, 1)))
    alpha = np.kron(a16, np.ones((k, k)))
    solid = (alpha >= 0.5).astype(float)
    lum = np.kron(rgb16 @ np.array([0.2126, 0.7152, 0.0722]), np.ones((k, k)))
    h, w = lum * solid, solid.copy()
    for _ in range(max(3, 3 * k // 4)):   # as far as 0.21's three passes at 64 px
        h, w = blur(h), blur(w)
    h = h / np.maximum(w, 1e-6)
    gx, gy = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) / 2, (np.roll(h, -1, 0) - np.roll(h, 1, 0)) / 2
    light = np.clip(1 + 1.6 * (k / 4) * (gx + gy) * np.clip(2 * w - 0.5, 0, 1), 0.82, 1.18)
    grain = 1 + 0.035 * periodic(seed, 20, 20, 0.3) + 0.02 * periodic(seed + 1, 6, 6)
    out = np.clip(up * (light * grain)[..., None], 0, 1)
    if solid.sum() > 0:   # the opaque pixels' mean brightness as the picture's
        lum_up, lum_out = (up @ np.array([0.2126, 0.7152, 0.0722])), (out @ np.array([0.2126, 0.7152, 0.0722]))
        ratio = (lum_up * solid).sum() / max((lum_out * solid).sum(), 1e-6)
        out = np.clip(out * ratio, 0, 1)
    rgba = np.concatenate([out, alpha[..., None]], axis=2)
    return common.png_rgba(TEX, TEX, (np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8).tobytes())


def members():
    """M2: every 16 px source in blocks16 redrawn at TEX px (0.22: 128; 64 before) over the block texture of its name; returns files written."""
    written = 0
    names = sorted(f[:-4] for f in os.listdir(BLOCKS16) if f.endswith('.png'))
    for n in names:
        written += common.write_if_changed(os.path.join(BLOCK_TEXTURES, n + '.png'), enhance(read_png(os.path.join(BLOCKS16, n + '.png')), 1000 + sum(n.encode())))
    common.log('member textures:', len(names), 'redrawn at', TEX, 'px,', written, 'written')
    return written


TEXTURE_RECIPES = {
    'paint_green': lambda: painted('#4f6e5c', 11), 'paint_grey': lambda: painted('#7d8389', 12), 'paint_blue': lambda: painted('#2f5482', 13),
    'paint_red': lambda: painted('#9e3328', 14), 'paint_yellow': lambda: painted('#d8a82a', 15), 'paint_cream': lambda: painted('#d6cfbb', 16),
    'paint_white': lambda: painted('#e4e5e2', 17), 'paint_purple': lambda: painted('#5b2f86', 18),
    'steel': lambda: metal('#a8aeb4', 21), 'dark_steel': lambda: metal('#3b3f45', 22, brushed=False), 'cast_iron': lambda: cast('#56595d', 23),
    'brass': lambda: metal('#b8954a', 24), 'copper': lambda: metal('#b86b3e', 25), 'rubber': lambda: shade(rgb('#1d1e20'), 1 + 0.04 * periodic(26, 12, 12)),
    'plastic': lambda: shade(rgb('#26282b'), 1 + 0.03 * periodic(27, 16, 16)), 'firebrick': lambda: bricks(28), 'soot': lambda: shade(rgb('#1b1817'), 1 + 0.1 * periodic(29, 8, 8)),
    'glow': lambda: glow(30), 'wood': lambda: wood(31), 'concrete': lambda: concrete(32), 'glass': lambda: glass(33), 'insulation': lambda: insulation(34),
    'frost': lambda: frost(35), 'coil': lambda: coil(36),
    'gauge': lambda: gauge(41), 'panel': lambda: panel(42), 'vent': lambda: vent(43), 'hazard': lambda: hazard(44), 'label': lambda: label(45),
    'screen': lambda: screen(46), 'blueprint': lambda: blueprint(47), 'core': lambda: core(48),
}


# ------------------------------------------------------------------------------------------------ shapes in block pixels

def B(x, y, z):
    """A point in block pixels as Blender's (the exporter turns Blender x, y, z into the game's x, z, -y)."""
    return (x * P, -z * P, y * P)


class Model:
    """A machine being modelled: one bmesh, materials by name."""

    def __init__(self):
        self.bm = bmesh.new()
        self.mats = []

    def m(self, name):
        if name not in self.mats:
            self.mats.append(name)
        return self.mats.index(name)

    def box(self, lo, hi, mat, bottom=True):
        details.box(self.bm, B(lo[0], lo[1], hi[2]), B(hi[0], hi[1], lo[2]), self.m(mat), bottom)

    def rbox(self, lo, hi, r, mat, seg=1):
        kitshapes.rounded_box(self.bm, B(lo[0], lo[1], hi[2]), B(hi[0], hi[1], lo[2]), r * P, self.m(mat), seg)

    def vcyl(self, cx, cz, y0, y1, r0, r1, sides, mat, caps=(True, True)):
        """A (truncated) cylinder standing at (cx, cz) from y0 to y1."""
        details.cylinder(self.bm, y0 * P, y1 * P, r0 * P, r1 * P, sides, self.m(mat), caps=caps, offset=(cx * P, -cz * P))

    def tube(self, a, b, r, sides, mat):
        kitshapes.tube_between(self.bm, B(*a), B(*b), r * P, sides, self.m(mat))

    def ring(self, centre, major, minor, axis, segments, sides, mat):
        """A ring about the game's x, y or z axis."""
        details.torus(self.bm, major * P, minor * P, B(*centre), segments, sides, self.m(mat), axis={'x': 'x', 'y': 'z', 'z': 'y'}[axis])

    def wheel(self, centre, axis, radius, rim, spokes, mat, hub_mat=None):
        """A spoked wheel (a flywheel, a handwheel) about the game's x or z axis."""
        self.ring(centre, radius, rim, axis, 20, 6, mat)
        cx, cy, cz = centre
        d = (0.5, 0, 0) if axis == 'x' else (0, 0, 0.5)
        self.tube((cx - d[0], cy, cz - d[2]), (cx + d[0], cy, cz + d[2]), max(0.8, radius * 0.18), 10, hub_mat or mat)
        for k in range(spokes):
            a = 2 * math.pi * k / spokes
            if axis == 'x':
                end = (cx, cy + radius * math.sin(a), cz + radius * math.cos(a))
            else:
                end = (cx + radius * math.cos(a), cy + radius * math.sin(a), cz)
            self.tube(centre, end, rim * 0.55, 5, mat)

    def legs(self, x0, z0, x1, z1, y0, y1, w, mat):
        for x, z in ((x0, z0), (x1 - w, z0), (x0, z1 - w), (x1 - w, z1 - w)):
            self.box((x, y0, z), (x + w, y1, z + w), mat)


# ------------------------------------------------------------------------------------------------ the machines

def foundry(lit=False):
    """A crucible foundry: a refractory furnace in a grey shell on a cast plinth, its mouth and spout to the front, a flue."""
    m = Model()
    m.box((0, 0, 0), (16, 2, 16), 'cast_iron')
    m.rbox((1, 2, 1), (15, 11, 15), 1.5, 'paint_grey')
    for y in (4.5, 9):
        m.box((0.7, y, 0.7), (15.3, y + 0.6, 15.3), 'dark_steel')
    m.vcyl(8, 8, 11, 12.5, 6.6, 6.6, 20, 'cast_iron')
    m.vcyl(8, 8, 12.5, 13.2, 6.6, 5.6, 20, 'cast_iron')
    m.tube((5.5, 13.6, 8), (10.5, 13.6, 8), 0.4, 6, 'steel')
    m.box((5.5, 13, 7.6), (6, 13.6, 8.4), 'steel')
    m.box((10, 13, 7.6), (10.5, 13.6, 8.4), 'steel')
    m.box((4, 3.4, 0.6), (12, 9.6, 1), 'firebrick')
    m.box((5, 4.4, 0.4), (11, 8.6, 1), 'glow' if lit else 'soot')
    m.box((7, 8.6, 0), (9, 9.3, 1), 'steel')
    m.vcyl(12, 12, 13, 16, 1.8, 1.6, 12, 'dark_steel')
    m.vcyl(12, 12, 15.4, 16, 2.1, 2.1, 12, 'dark_steel')
    m.box((13, 6.5, 0.6), (15, 8.5, 1), 'gauge')
    m.box((15, 3, 4), (15.6, 8, 10), 'panel')
    return m


def forging_press():
    """A C-frame hydraulic press: cast base, green frame, ram and dies, the cylinder on top, hoses and a control box."""
    m = Model()
    m.box((1, 0, 1), (15, 3, 15), 'cast_iron')
    m.box((1, 1, 0.6), (15, 2.6, 1), 'hazard')
    m.box((3, 3, 2), (13, 5, 10), 'dark_steel')
    m.box((5, 5, 4), (11, 6, 8), 'steel')
    m.box((3, 3, 10), (13, 15, 14), 'paint_green')
    m.box((3, 12, 2), (13, 15, 10), 'paint_green')
    m.box((3.5, 5, 9.6), (12.5, 12, 10), 'label')
    m.vcyl(8, 6, 15, 16, 3, 3, 16, 'steel')
    m.vcyl(8, 6, 7.5, 12, 1.6, 1.6, 12, 'steel', caps=(True, False))
    m.box((5, 7, 3.5), (11, 8, 8.5), 'dark_steel')
    m.box((5.5, 6.2, 4), (10.5, 7, 8), 'steel')
    m.tube((11, 15.5, 6), (14, 13, 12), 0.5, 6, 'rubber')
    m.tube((5, 15.5, 6), (2, 13, 12), 0.5, 6, 'rubber')
    m.box((13.4, 6, 3), (15.6, 10, 6), 'paint_grey')
    m.box((13.4, 6.3, 2.6), (15.4, 9.7, 3), 'panel')
    return m


def composite_oven():
    """An autoclave: an insulated pressure vessel on saddles, its door with a locking ring and handwheel to the front."""
    m = Model()
    for z in (2.5, 11.5):
        m.box((3, 0, z), (13, 3, z + 2), 'paint_grey')
    m.tube((8, 8.5, 1.5), (8, 8.5, 15.5), 6.5, 16, 'insulation')
    m.tube((8, 8.5, 0.6), (8, 8.5, 1.5), 7, 16, 'paint_grey')
    m.ring((8, 8.5, 1.1), 6.6, 0.5, 'z', 20, 6, 'steel')
    m.wheel((8, 8.5, 0.3), 'z', 2.2, 0.3, 4, 'dark_steel')
    m.tube((5, 15.2, 4), (5, 15.2, 14), 0.6, 8, 'copper')
    m.tube((11, 15.2, 4), (11, 15.2, 14), 0.6, 8, 'copper')
    m.box((6.5, 14.6, 2.2), (9.5, 16, 4), 'paint_grey')
    m.box((6.5, 14.6, 1.8), (9.5, 16, 2.2), 'gauge')
    m.box((14.6, 3, 5), (16, 8, 11), 'paint_grey')
    m.box((15.6, 3.4, 5.5), (16, 7.6, 10.5), 'panel')
    return m


def vulcanizer():
    """A tyre press: platens on four columns, the two halves of a tyre mould between them, its cylinder and steam pipes."""
    m = Model()
    m.box((1, 0, 1), (15, 2, 15), 'cast_iron')
    for x, z in ((3, 3), (13, 3), (3, 13), (13, 13)):
        m.vcyl(x, z, 2, 15, 0.9, 0.9, 10, 'steel', caps=(False, False))
    m.box((2, 2, 2), (14, 5, 14), 'paint_red')
    m.vcyl(8, 8, 5, 7, 5.5, 5.5, 20, 'dark_steel', caps=(False, True))
    m.vcyl(8, 8, 8, 10, 5.5, 5.5, 20, 'dark_steel', caps=(True, False))
    m.box((2, 10, 2), (14, 13, 14), 'paint_red')
    m.box((2, 15, 2), (14, 16, 14), 'paint_red')
    m.vcyl(8, 8, 13, 15, 2.5, 2.5, 16, 'steel', caps=(False, False))
    m.tube((14, 4, 4), (14, 11, 4), 0.5, 6, 'copper')
    m.tube((14, 11, 4), (14, 11, 12), 0.5, 6, 'copper')
    m.box((5.5, 2.5, 1.6), (10.5, 4.5, 2), 'hazard')
    m.box((6.5, 10.5, 1.6), (9.5, 12.5, 2), 'gauge')
    return m


def welding_station():
    """A welding bench: a steel table, the power source under its back, a gas cylinder in the corner, the torch on its hose."""
    m = Model()
    m.box((0.5, 8.8, 0.5), (15.5, 10, 10), 'steel')
    m.legs(1, 1, 15, 10, 0, 8.8, 1.2, 'dark_steel')
    m.box((1, 2, 1), (15, 2.6, 10), 'dark_steel')
    m.box((1, 0, 10.5), (9, 9, 15.5), 'paint_blue')
    m.box((1.6, 3.5, 10.1), (8.4, 8.4, 10.5), 'panel')
    m.box((1.6, 0.5, 10.1), (8.4, 3, 10.5), 'vent')
    m.vcyl(12.5, 13, 0, 13, 2, 2, 14, 'paint_green', caps=(False, False))
    m.vcyl(12.5, 13, 13, 14.2, 2, 1.1, 14, 'paint_green', caps=(False, False))
    m.vcyl(12.5, 13, 14.2, 15.4, 0.8, 0.8, 8, 'brass')
    m.box((11.6, 14.6, 12), (13.4, 15.8, 13.4), 'brass')
    m.tube((12.5, 15.2, 12), (9, 10.5, 10.4), 0.35, 6, 'rubber')
    m.tube((5, 9.2, 10.2), (6, 10.4, 6), 0.45, 6, 'rubber')
    m.tube((6, 10.4, 6), (9, 10.4, 3), 0.45, 6, 'dark_steel')
    m.tube((9, 10.4, 3), (10, 10.2, 2.2), 0.3, 6, 'brass')
    m.box((2, 10, 2), (6, 10.6, 5), 'dark_steel')
    m.box((11, 10, 6), (14, 10.4, 8.5), 'steel')
    return m


def assembly_bench():
    """A fitter's bench: an oak top on legs with a shelf, a vice on the front corner, a tool board at the back with its
    tools, a red tool case; 2026-11-22 (redrawn): a bank of three drawers under the top on the right with their pulls,
    and a bench grinder (motor and two wheels in their guards) at the back right."""
    m = Model()
    m.box((0, 11, 0.5), (16, 13, 12.5), 'wood')
    m.legs(1, 1.5, 15, 12.5, 0, 11, 1.5, 'wood')
    m.box((1, 2, 1.5), (15, 3, 12.5), 'wood')
    m.box((0.5, 13, 12.5), (15.5, 16, 14), 'wood')
    for x, h in ((2, 2.4), (5, 1.8), (8.5, 2.6)):
        m.box((x, 15.6 - h, 12), (x + 1, 15.6, 12.5), 'steel')
    m.box((1.5, 13, 0.5), (5.5, 14.5, 3.5), 'paint_blue')
    m.box((2, 13, 0), (5, 14.2, 0.5), 'dark_steel')
    m.tube((1.5, 13.6, -0.2), (5.5, 13.6, -0.2), 0.25, 6, 'steel')
    m.box((6.5, 13, 3), (10, 13.6, 6), 'paint_red')
    m.box((6.8, 13.6, 3.3), (9.7, 14.2, 5.7), 'paint_red')
    # the drawers under the top on the right, their pulls
    m.box((9.5, 4.5, 1.6), (14.5, 10.8, 11), 'paint_grey')
    for y in (5, 7, 9):
        m.box((9.8, y, 1.4), (14.2, y + 1.7, 1.6), 'paint_grey')
        m.box((11.3, y + 0.7, 1.1), (12.7, y + 1.0, 1.4), 'steel')
    # the bench grinder: its motor between two wheels in their guards
    m.box((11, 13, 9), (15, 13.6, 11.5), 'dark_steel')
    m.tube((11.5, 14.8, 10.2), (14.5, 14.8, 10.2), 1.2, 10, 'paint_green')
    for x in (10.6, 15.4):
        m.tube((x - 0.35, 14.8, 10.2), (x + 0.35, 14.8, 10.2), 1.5, 12, 'cast_iron')
        m.box((x - 0.5, 14.2, 9.1), (x + 0.5, 16, 11.3), 'paint_green')
    return m


def alloy_furnace(lit=False):
    """A brick furnace bound with iron bands, its arched mouth to the front, a tapering stack; 2026-11-22 (redrawn): the
    mouth's iron frame and its hinged door swung open beside it, the blower on the right blowing into the hearth through
    its duct, a fuel hopper on the left."""
    m = Model()
    m.rbox((1, 0, 1), (15, 12, 15), 1, 'firebrick')
    for y in (3.5, 9):
        m.box((0.6, y, 0.6), (15.4, y + 0.7, 15.4), 'dark_steel')
    mouth = 'glow' if lit else 'soot'
    m.box((4, 1.5, 0.5), (12, 6.5, 1), mouth)
    m.tube((8, 6.5, 0.5), (8, 6.5, 1), 4, 16, mouth)
    m.box((3, 1, 0.6), (13, 1.5, 1), 'dark_steel')
    for x in (3.3, 12.7):
        m.box((x - 0.4, 1, 0.3), (x + 0.4, 10.5, 0.9), 'dark_steel')
    m.box((2.9, 10.3, 0.3), (13.1, 11, 0.9), 'dark_steel')
    m.box((0.4, 1.5, -0.6), (0.9, 7.5, 3.5), 'cast_iron')
    m.tube((13, 5, 0.6), (13.6, 5, 0.6), 0.35, 6, 'steel')
    # the blower on the right and its duct, the fuel hopper on the left
    m.tube((15.2, 3.2, 10), (16, 3.2, 10), 2.2, 12, 'paint_grey')
    m.box((15.1, 0, 8.5), (16, 1.2, 11.5), 'dark_steel')
    m.tube((15.2, 3.2, 10), (15.2, 3.2, 6), 0.8, 8, 'dark_steel')
    m.box((0, 8, 5), (1, 11.5, 11), 'dark_steel')
    m.box((-0.0, 11.5, 4.5), (1.2, 12.5, 11.5), 'dark_steel')
    m.vcyl(8, 9, 12, 15.4, 4.6, 2.6, 16, 'firebrick', caps=(False, False))
    m.vcyl(8, 9, 15.4, 16, 3, 3, 16, 'dark_steel')
    return m


def battery():
    """A battery cabinet: a grey case with a charge gauge and data plate, terminals on top; 2026-11-22 (redrawn): on its
    plinth, two doors (the seam between them, a vent and a handle each), the hazard strip, the cooling fan's grille on
    the right, each terminal's cable to the gland at the back."""
    m = Model()
    m.box((1, 0, 2), (15, 1, 15), 'dark_steel')
    m.rbox((1, 1, 2), (15, 15, 15), 0.5, 'paint_grey')
    m.box((7.85, 1.5, 1.6), (8.15, 14, 2), 'dark_steel')
    for x0 in (2, 8.6):
        m.box((x0, 2, 1.6), (x0 + 5.4, 7, 2), 'vent')
    for x in (6.8, 9.2):
        m.box((x - 0.3, 7.5, 1.2), (x + 0.3, 10, 1.6), 'steel')
    m.box((3, 10.5, 1.6), (6, 13.5, 2), 'gauge')
    m.box((9.5, 11, 1.6), (13.5, 13, 2), 'label')
    m.box((1, 14.2, 1.6), (15, 14.8, 2), 'hazard')
    m.box((15, 5.5, 6), (15.3, 10.5, 11), 'vent')
    m.ring((15.25, 8, 8.5), 2.6, 0.3, 'x', 16, 4, 'dark_steel')
    for x, mat in ((5, 'paint_red'), (11, 'plastic')):
        m.vcyl(x, 8.5, 15, 15.6, 1.4, 1.4, 10, mat)
        m.vcyl(x, 8.5, 15.6, 16, 0.6, 0.6, 8, 'copper')
    m.vcyl(8, 13.5, 15, 15.8, 1.2, 1.2, 10, 'dark_steel')
    m.tube((5, 15.8, 8.5), (7.2, 15.6, 13.3), 0.3, 6, 'paint_red')
    m.tube((11, 15.8, 8.5), (8.8, 15.6, 13.3), 0.3, 6, 'rubber')
    m.tube((8, 15.8, 13.5), (8, 15.8, 15.6), 0.45, 6, 'rubber')
    return m


def rotating_machine(m, length, radius, body, ribbed, shaft_front):
    """An electric machine along z on a skid: its frame (ribbed or plain), end bells, feet, terminal box, shaft."""
    z0, z1 = (16 - length) / 2 + (1 if shaft_front else -1), (16 + length) / 2 + (1 if shaft_front else -1)
    cy = radius + 2
    m.box((2, 0, 2), (4, 1.5, 14), 'dark_steel')
    m.box((12, 0, 2), (14, 1.5, 14), 'dark_steel')
    m.box((4, 0.5, z0 + 1), (12, 1.5, z0 + 2.5), 'dark_steel')
    m.box((4, 0.5, z1 - 2.5), (12, 1.5, z1 - 1), 'dark_steel')
    m.tube((8, cy, z0), (8, cy, z1), radius, 16, body)
    if ribbed:
        for k in range(1, 5):
            m.ring((8, cy, z0 + (z1 - z0) * k / 5), radius, 0.45, 'z', 16, 4, body)
    m.tube((8, cy, z0 - 1.2), (8, cy, z0), radius * 0.82, 16, 'cast_iron')
    m.tube((8, cy, z1), (8, cy, z1 + 1.2), radius * 0.82, 16, 'cast_iron')
    for x in (4.5, 11.5):
        m.box((x - 1, 1.5, z0 + 1), (x + 1, cy - radius * 0.6, z0 + 2.5), body)
        m.box((x - 1, 1.5, z1 - 2.5), (x + 1, cy - radius * 0.6, z1 - 1), body)
    m.box((5.5, cy + radius - 0.6, 6.5), (10.5, cy + radius + 2, 10.5), body)
    m.box((5.9, cy + radius - 0.4, 6.1), (10.1, cy + radius + 1.6, 6.5), 'label')
    if shaft_front:
        m.tube((8, cy, 0), (8, cy, z0 - 1.2), 1, 10, 'steel')
    else:
        m.tube((8, cy, z1 + 1.2), (8, cy, 16), 1.2, 10, 'steel')
    return cy


def generator():
    """A generator on a skid: a blue ribbed frame along the block, the coupling at the back, its terminal box on top."""
    m = Model()
    rotating_machine(m, 10, 5.2, 'paint_blue', True, False)
    m.box((3, 2, 0.6), (13, 4, 1), 'hazard')
    return m


def electric_motor():
    """A TEFC motor on a base: grey ribbed frame, fan cover at the back, the shaft out of the front."""
    m = Model()
    cy = rotating_machine(m, 8, 4.4, 'paint_grey', True, True)
    m.tube((8, cy, 13.2), (8, cy, 15.5), 4, 16, 'vent')
    return m


def steam_engine():
    """A vertical boiler and a horizontal engine: boiler with its stack, cylinder, crosshead, crank and flywheel."""
    m = Model()
    m.box((0.5, 0, 0.5), (15.5, 1.5, 15.5), 'cast_iron')
    m.vcyl(4.5, 10, 1.5, 12.5, 3.8, 3.8, 16, 'paint_green', caps=(False, False))
    m.vcyl(4.5, 10, 12.5, 13.5, 3.8, 2, 16, 'paint_green', caps=(False, False))
    for y in (3, 8, 12):
        m.ring((4.5, y, 10), 3.85, 0.25, 'y', 16, 4, 'brass')
    m.vcyl(4.5, 10, 13.5, 16, 1.2, 1.2, 10, 'dark_steel', caps=(False, True))
    m.box((3, 2.5, 6.1), (6, 5.5, 6.5), 'soot')
    m.box((2.6, 2.1, 6.3), (6.4, 2.5, 6.6), 'dark_steel')
    m.box((2.5, 8, 5.7), (5.5, 10.5, 6.4), 'gauge')
    m.box((9, 1.5, 2), (13, 3, 14), 'cast_iron')
    m.tube((11, 5, 2.5), (11, 5, 8), 2, 14, 'steel')
    m.tube((11, 5, 8), (11, 5, 10.5), 0.4, 6, 'steel')
    m.box((10, 3, 9), (12, 4, 11.5), 'dark_steel')
    m.box((10.4, 4, 10.3), (11.6, 6, 11.3), 'steel')
    m.tube((11, 5, 10.8), (13.6, 8.5, 12), 0.5, 6, 'steel')
    m.box((12, 3, 11), (15, 9, 13), 'cast_iron')
    m.wheel((14.3, 9, 12), 'x', 3.8, 0.6, 6, 'cast_iron', 'steel')
    m.tube((6.5, 11.5, 10), (11, 7, 4), 0.5, 6, 'copper')
    m.tube((8, 4, 10), (8, 1.5, 10), 0.4, 6, 'copper')
    return m


def treadle():
    """A foot treadle: oak frame and pedal board, a cast flywheel on its crank, a pulley above."""
    m = Model()
    for x in (1, 14):
        m.box((x, 0, 2), (x + 1, 1.5, 14), 'wood')
        m.box((x, 1.5, 7), (x + 1, 12, 9), 'wood')
    m.box((2, 0.5, 3), (14, 1.5, 4), 'wood')
    m.box((2, 0.5, 12), (14, 1.5, 13), 'wood')
    m.box((3, 1.8, 2.5), (13, 2.6, 11), 'wood')
    m.tube((2, 2.2, 10.5), (14, 2.2, 10.5), 0.4, 6, 'dark_steel')
    m.wheel((8, 8, 8), 'x', 4.5, 0.6, 6, 'cast_iron', 'dark_steel')
    m.tube((2, 8, 8), (14, 8, 8), 0.5, 8, 'steel')
    m.tube((7.5, 2.6, 4), (7.5, 6, 8), 0.35, 6, 'wood')
    m.tube((14.5, 8, 8), (15.5, 8, 8), 2, 12, 'cast_iron')
    return m


def engine_stand():
    """A test bed: a red frame of rails and posts, a slotted bed plate on top (the engine stands on it), an instrument post."""
    m = Model()
    m.box((0.5, 0, 0.5), (15.5, 1.5, 15.5), 'concrete')
    for x in (1.5, 13):
        m.box((x, 1.5, 1.5), (x + 1.5, 14, 14.5), 'paint_red')
    m.box((3, 4, 1.5), (13, 5.5, 3), 'paint_red')
    m.box((3, 4, 13), (13, 5.5, 14.5), 'paint_red')
    m.box((1, 14, 1), (15, 16, 15), 'dark_steel')
    for z in (4, 8, 12):
        m.box((1.5, 15.6, z - 0.4), (14.5, 16.05, z + 0.4), 'soot')
    m.box((4, 6, 0.6), (12, 12, 1), 'panel')
    m.box((4, 6, 1), (12, 12, 1.5), 'paint_grey')
    m.box((2, 1.5, 7), (13, 2.5, 9), 'hazard')
    # 2026-11-22 (redrawn): rubber mounts on the bed plate, the cable tray along the right rail, the fuel and coolant lines
    # up the back post with their quick couplings, the cooling water's hose reel at the back
    for x in (3.5, 12.5):
        for z in (3.5, 12.5):
            m.vcyl(x, z, 16, 16.6, 0.9, 0.7, 8, 'rubber')
    m.box((14.5, 9, 2), (15.8, 10, 14), 'dark_steel')
    m.box((14.6, 10, 2), (15.7, 10.3, 14), 'paint_yellow')
    for z, mat in ((13.6, 'paint_red'), (14.4, 'paint_blue')):
        m.tube((8, 1.5, z), (8, 14, z), 0.3, 6, mat)
        m.vcyl(8, z, 14, 14.6, 0.5, 0.5, 6, 'brass')
    m.ring((4.5, 8, 15.2), 2.2, 0.6, 'z', 14, 6, 'paint_green')
    m.tube((4.5, 8, 14.6), (4.5, 8, 15.8), 0.6, 8, 'dark_steel')
    return m


def creative_power():
    """A creative power source: a dark case framing a purple core."""
    m = Model()
    m.rbox((0.5, 0, 0.5), (15.5, 16, 15.5), 1, 'paint_purple')
    for face in range(4):
        lo, hi = [((3, 3, 0.1), (13, 13, 0.5)), ((3, 3, 15.5), (13, 13, 15.9)), ((0.1, 3, 3), (0.5, 13, 13)), ((15.5, 3, 3), (15.9, 13, 13))][face]
        m.box(lo, hi, 'core')
    m.box((3, 15.9, 3), (13, 16.1, 13), 'core')
    return m


def pump():
    """An inline pump between flanges front and back: volute, its motor above, base plate."""
    m = Model()
    for z0, z1 in ((0, 1.2), (14.8, 16)):
        m.tube((8, 8, z0), (8, 8, z1), 4, 12, 'paint_blue')
    m.tube((8, 8, 1.2), (8, 8, 3.5), 3, 12, 'paint_blue')
    m.tube((8, 8, 12.5), (8, 8, 14.8), 3, 12, 'paint_blue')
    m.tube((8, 8, 3.5), (8, 8, 12.5), 5.5, 18, 'paint_blue')
    m.box((3.5, 0, 4), (12.5, 2.5, 12), 'dark_steel')
    m.box((6, 2.5, 6), (10, 3, 10), 'dark_steel')
    m.vcyl(8, 8, 13, 16, 3.4, 3.4, 14, 'paint_grey', caps=(False, True))
    m.box((5.8, 13.4, 5), (10.2, 15, 6.2), 'label')
    return m


def tank(kind):
    """A vertical tank filling its block (stacked tanks make a column): a shell, a ring at each end (fluid_tank); a
    vacuum-jacketed white shell with frost bands and a relief valve (cryogenic_tank); a bundle of four bottles in a
    frame (hydrogen_cylinders)."""
    m = Model()
    if kind == 'hydrogen_cylinders':
        for x, z in ((4.5, 4.5), (11.5, 4.5), (4.5, 11.5), (11.5, 11.5)):
            m.vcyl(x, z, 0, 15, 3.3, 3.3, 14, 'paint_red', caps=(False, False))
            m.vcyl(x, z, 15, 16, 3.3, 1.2, 14, 'paint_red', caps=(False, True))
        for y in (2, 13):
            m.box((0.5, y, 0.5), (15.5, y + 1, 1.2), 'dark_steel')
            m.box((0.5, y, 14.8), (15.5, y + 1, 15.5), 'dark_steel')
            m.box((0.5, y, 1.2), (1.2, y + 1, 14.8), 'dark_steel')
            m.box((14.8, y, 1.2), (15.5, y + 1, 14.8), 'dark_steel')
        m.tube((4.5, 15.6, 4.5), (11.5, 15.6, 4.5), 0.4, 6, 'steel')
        m.tube((4.5, 15.6, 11.5), (11.5, 15.6, 11.5), 0.4, 6, 'steel')
        m.tube((8, 15.6, 4.5), (8, 15.6, 11.5), 0.4, 6, 'steel')
        m.box((6, 6, 0.4), (10, 9, 0.6), 'label')
        return m
    shell = 'insulation' if kind == 'cryogenic_tank' else 'paint_grey'
    m.vcyl(8, 8, 0, 16, 7.6, 7.6, 24, shell, caps=(False, False))
    m.vcyl(8, 8, 0.01, 1.2, 7.9, 7.9, 24, 'dark_steel', caps=(True, False))
    m.vcyl(8, 8, 14.8, 15.99, 7.9, 7.9, 24, 'dark_steel', caps=(False, True))
    if kind == 'cryogenic_tank':
        for y in (4, 8, 12):
            m.ring((8, y, 8), 7.65, 0.3, 'y', 24, 4, 'frost')
        m.box((10, 12, 0.2), (13, 14, 0.6), 'gauge')
        m.tube((3, 14, 2.8), (2, 16.2, 2.6), 0.4, 6, 'brass')
    else:
        m.box((6, 6, 0.3), (10, 9, 0.5), 'label')
        m.tube((12.5, 1.2, 2.5), (12.5, 14.8, 2.5), 0.3, 6, 'dark_steel')
        m.tube((14, 1.2, 4), (14, 14.8, 4), 0.3, 6, 'dark_steel')
        for y in (3, 6, 9, 12):
            m.tube((12.5, y, 2.5), (14, y, 4), 0.25, 4, 'dark_steel')
    return m


def hydrogen_liquefier():
    """A cold box: insulated white panels, the compressor on its skid, a heat exchanger column, piping and a panel;
    2026-11-22 (redrawn): the skid's beams, the compressor's motor and flywheel guard, a valve with its handwheel on
    the line to the column, the column's frosted bands, the ladder up the cold box's side, its vent stack and the
    relief valve on top."""
    m = Model()
    m.box((0.5, 0, 0.5), (15.5, 0.6, 15.5), 'dark_steel')
    for z in (1, 14):
        m.box((0.5, 0.6, z), (15.5, 1.2, z + 1), 'paint_yellow')
    m.box((1, 1.2, 6), (15, 15, 15), 'insulation')
    for y in (5, 10):
        m.box((0.8, y, 5.8), (15.2, y + 0.4, 15.2), 'steel')
    m.vcyl(4, 3.5, 1.2, 9, 2.5, 2.5, 14, 'paint_blue')
    m.vcyl(4, 3.5, 9, 9.8, 1.6, 1.6, 12, 'dark_steel')
    m.tube((4, 5, 1.2), (4, 5, 6), 1.2, 10, 'paint_blue')
    m.box((0.6, 1.2, 0.6), (2.2, 5.5, 3.5), 'paint_grey')
    m.vcyl(11, 3.5, 1.2, 14, 1.8, 1.8, 12, 'frost')
    for y in (4, 8, 12):
        m.vcyl(11, 3.5, y, y + 0.5, 2.0, 2.0, 12, 'steel')
    m.tube((4, 9.4, 3.5), (11, 12, 3.5), 0.5, 6, 'steel')
    m.box((7, 10.2, 3.0), (8, 11.2, 4.0), 'paint_red')
    m.wheel((7.5, 11.9, 3.5), 'x', 0.9, 0.18, 3, 'paint_red')
    m.tube((11, 14, 3.5), (11, 14, 6), 0.5, 6, 'steel')
    m.box((6, 2, 5.6), (10, 6, 6), 'panel')
    m.box((2, 10, 5.6), (5, 13, 6), 'gauge')
    # the ladder up the right side, the vent stack and the relief valve on top
    for z in (9, 11.5):
        m.tube((15.4, 1.2, z), (15.4, 15.5, z), 0.2, 5, 'steel')
    for k in range(5):
        y = 3 + 2.6 * k
        m.tube((15.4, y, 9), (15.4, y, 11.5), 0.15, 5, 'steel')
    m.vcyl(3, 13, 15, 16, 0.5, 0.5, 8, 'steel')
    m.vcyl(13, 8, 15, 15.6, 0.7, 0.7, 8, 'paint_red')
    return m


def fuel_dispenser():
    """A pump island: base, cabinet with its display and price window, a nozzle on its holster and hose."""
    m = Model()
    m.box((2, 0, 2), (14, 1, 14), 'concrete')
    m.rbox((3, 1, 4), (13, 15, 12), 0.6, 'paint_red')
    m.box((3.5, 9, 3.6), (12.5, 14, 4), 'screen')
    m.box((3.5, 2, 3.6), (12.5, 8, 4), 'paint_white')
    m.box((4, 15, 4), (12, 16, 12), 'paint_white')
    m.box((12.8, 5, 6), (14, 9, 9), 'dark_steel')
    m.tube((13.6, 8.6, 7.5), (13.6, 6.5, 5.6), 0.5, 6, 'plastic')
    m.tube((13.6, 6.5, 5.6), (13.9, 5.6, 4.6), 0.3, 6, 'steel')
    m.tube((13.4, 8.4, 9), (13.8, 3, 12), 0.45, 6, 'rubber')
    m.tube((13.8, 3, 12), (13, 1.2, 13.5), 0.45, 6, 'rubber')
    # 2026-11-22 (redrawn): the other side's nozzle and hose, the lit sign on top, the kerb's hazard edge, the door seam
    m.box((2, 5, 7), (3.2, 9, 10), 'dark_steel')
    m.tube((2.4, 8.6, 8.5), (2.4, 6.5, 10.4), 0.5, 6, 'plastic')
    m.tube((2.4, 6.5, 10.4), (2.1, 5.6, 11.4), 0.3, 6, 'steel')
    m.tube((2.6, 8.4, 7), (2.2, 3, 4), 0.45, 6, 'rubber')
    m.tube((2.2, 3, 4), (3, 1.2, 2.5), 0.45, 6, 'rubber')
    m.box((4.5, 16, 6), (11.5, 16.01, 10), 'label')
    m.box((2, 0.8, 2), (14, 1, 2.3), 'hazard')
    m.box((7.9, 2.2, 3.55), (8.1, 7.8, 3.65), 'dark_steel')
    return m


def wellhead():
    """A wellhead (Christmas tree): casing head on the ground, master valves with handwheels, the wing outlet, a cap."""
    m = Model()
    m.vcyl(8, 8, 0, 2, 4.8, 4.8, 16, 'paint_grey')
    m.vcyl(8, 8, 2, 3, 3.2, 3.2, 14, 'paint_grey')
    for y in (3, 7):
        m.vcyl(8, 8, y, y + 0.6, 3.6, 3.6, 14, 'dark_steel')
        m.box((5.6, y + 0.6, 5.6), (10.4, y + 3.4, 10.4), 'paint_red')
        m.vcyl(8, 8, y + 3.4, y + 4, 3.6, 3.6, 14, 'dark_steel')
        m.tube((10.4, y + 2, 8), (12.2, y + 2, 8), 0.4, 6, 'steel')
        m.wheel((12.6, y + 2, 8), 'x', 1.6, 0.25, 4, 'paint_yellow')
    m.tube((8, 9, 5.6), (8, 9, 3.2), 1.2, 10, 'paint_red')
    m.tube((8, 9, 3.2), (8, 9, 2.6), 1.8, 10, 'dark_steel')
    m.vcyl(8, 8, 11, 13, 2, 2, 12, 'paint_grey')
    m.box((6.5, 13, 6.5), (9.5, 14, 9.5), 'gauge')
    return m


def work_light():
    """A work light on a tripod: three legs, the mast and a lamp head with its lens."""
    m = Model()
    for a in (90, 210, 330):
        r = math.radians(a)
        m.tube((8, 8, 8), (8 + 6 * math.cos(r), 0, 8 + 6 * math.sin(r)), 0.35, 6, 'dark_steel')
    m.tube((8, 7, 8), (8, 13, 8), 0.45, 8, 'steel')
    m.box((5, 12.5, 6.5), (11, 16, 9.5), 'paint_yellow')
    m.box((5.4, 12.9, 6.1), (10.6, 15.6, 6.5), 'glow')
    m.box((5.4, 12.9, 9.5), (10.6, 15.6, 9.9), 'vent')
    return m


def design_table():
    """A drafting table: steel legs, a tilted oak board with a blueprint, a lamp on an arm."""
    m = Model()
    m.legs(1.5, 2, 14.5, 14, 0, 11, 1.2, 'dark_steel')
    m.box((1.5, 3, 2), (14.5, 4, 14), 'dark_steel')
    top = set(m.bm.faces)
    m.box((0.5, 11, 1), (15.5, 12.2, 15), 'wood')
    m.box((2, 12.2, 3), (13, 12.4, 12), 'blueprint')
    tilt = kitshapes.new_faces(m.bm, top)
    kitshapes.transform(m.bm, tilt, kitshapes.rotation(-12, 'X', B(8, 11.5, 8)))
    m.box((0.5, 10, 1), (15.5, 11, 2.5), 'wood')
    m.tube((14.5, 12, 14), (14.5, 15.5, 12), 0.3, 6, 'steel')
    m.tube((14.5, 15.5, 12), (11, 15.5, 9), 0.3, 6, 'steel')
    m.vcyl(11, 9, 14.5, 15.8, 0.6, 1.6, 10, 'paint_green', caps=(True, False))
    return m


def paint_booth_controller():
    """0.13: a paint booth's control cabinet: a grey cabinet on a plinth, its screen and buttons in front, an air filter
    on top, the spray gun hung on its right side over a coiled hose, a hazard strip along the plinth."""
    m = Model()
    m.box((0, 0, 0), (16, 1.5, 16), 'dark_steel')
    m.box((0.5, 1.5, 0.6), (15.5, 2.3, 1.2), 'hazard')
    m.rbox((1, 1.5, 1.5), (15, 14.5, 15), 1, 'paint_grey')
    m.box((3, 8, 1.1), (13, 13, 1.5), 'screen')
    m.box((3, 3.5, 1.1), (8, 7, 1.5), 'panel')
    m.box((9, 3.5, 1.1), (13, 7, 1.5), 'gauge')
    m.box((2, 14.5, 3), (14, 15.4, 13), 'vent')
    m.ring((15.4, 6.5, 9), 2.6, 0.45, 'x', 16, 6, 'rubber')
    m.box((15, 10, 8.4), (15.6, 11, 9.6), 'steel')
    m.box((15.2, 9, 8.7), (15.8, 12.5, 9.3), 'dark_steel')
    m.tube((15.5, 9, 9), (15.5, 7.5, 7), 0.25, 6, 'rubber')
    m.box((15.1, 12.5, 8.6), (15.8, 13.6, 9.4), 'paint_red')
    return m


def refit_shop_controller():
    """0.16: a refit shop's control cabinet: a blue tool chest on a plinth, its screen and a row of drawers in front, a
    trolley jack on its left side, a torque wrench hung on its right, a lamp on top, a hazard strip along the plinth."""
    m = Model()
    m.box((0, 0, 0), (16, 1.5, 16), 'dark_steel')
    m.box((0.5, 1.5, 0.6), (15.5, 2.3, 1.2), 'hazard')
    m.rbox((1.5, 1.5, 1.5), (14.5, 14, 15), 1, 'paint_blue')
    m.box((3, 9, 1.1), (13, 13, 1.5), 'screen')
    for i in range(3):
        y = 2.5 + 2 * i
        m.box((2.5, y, 1.1), (13.5, y + 1.6, 1.5), 'dark_steel')
        m.box((6.5, y + 0.6, 0.8), (9.5, y + 1, 1.1), 'steel')
    m.box((0.2, 1.5, 4), (1.5, 3, 12), 'paint_red')
    m.tube((0.8, 3, 6), (0.8, 6.5, 10), 0.35, 6, 'dark_steel')
    m.box((14.5, 6, 7.5), (15.4, 13, 8.3), 'steel')
    m.box((14.4, 12.2, 7), (15.6, 13.8, 8.8), 'dark_steel')
    m.box((5, 14, 6), (11, 15.4, 10), 'paint_yellow')
    m.box((5.4, 14.4, 5.6), (10.6, 15, 6), 'glow')
    return m


def paint_booth_exhaust():
    """0.13: a paint booth's extraction fan for a wall or the roof: a square housing, a filter grille in front, the fan's
    ring and hub behind it, a motor at the back."""
    m = Model()
    m.box((0, 0, 4), (16, 16, 16), 'paint_grey')
    m.box((0.5, 0.5, 3.4), (15.5, 15.5, 4), 'dark_steel')
    m.box((1.5, 1.5, 3), (14.5, 14.5, 3.4), 'vent')
    m.ring((8, 8, 3.2), 6, 0.5, 'z', 24, 6, 'steel')
    m.tube((8, 8, 2.6), (8, 8, 3.2), 1.2, 10, 'dark_steel')
    m.box((0, 0, 4), (16, 1, 16), 'hazard')
    return m


def air_separation_unit():
    """hdfv 0.17: a small air separation unit: the insulated cold box (the distillation column inside) with frost on its
    pipes, the air compressor on its skid, and the filling manifold with three cylinders (white oxygen, black nitrogen,
    green argon), its panel and gauges."""
    m = Model()
    m.box((0.5, 0, 0.5), (15.5, 1, 15.5), 'dark_steel')
    m.box((8.5, 1, 7), (15, 16, 15), 'insulation')
    m.vcyl(4, 12, 1, 6, 2.6, 2.6, 14, 'paint_blue')
    m.tube((4, 4, 9.4), (4, 4, 14.5), 1.0, 10, 'paint_blue')
    m.tube((6.6, 4, 12), (8.5, 4, 12), 0.6, 8, 'steel')
    m.tube((8.5, 13, 9), (6, 13, 9), 0.5, 6, 'frost')
    m.tube((6, 13, 9), (6, 7, 9), 0.5, 6, 'frost')
    for i, mat in enumerate(('paint_white', 'dark_steel', 'paint_green')):
        x = 2.5 + 2.6 * i
        m.vcyl(x, 3.5, 1, 9.5, 1.1, 1.1, 10, mat)
        m.vcyl(x, 3.5, 9.5, 10.5, 1.1, 0.5, 10, mat)
        m.tube((x, 10.5, 3.5), (x, 11.5, 3.5), 0.3, 6, 'brass')
    m.tube((2.5, 11.5, 3.5), (7.7, 11.5, 3.5), 0.35, 6, 'steel')
    m.box((9, 3, 6.6), (14, 7, 7), 'panel')
    m.box((10, 9, 6.6), (13, 12, 7), 'gauge')
    # 2026-11-22 (redrawn): the cold box's frame bands, its ladder on the left face, the relief valve on its front, a
    # valve with its handwheel on the frosted line, the cylinders' chain bar
    for y in (5, 10.5):
        m.box((8.3, y, 6.8), (15.2, y + 0.4, 15.2), 'steel')
    for z in (9, 11):
        m.tube((8.1, 1, z), (8.1, 15.5, z), 0.18, 5, 'steel')
    for k in range(5):
        m.tube((8.1, 3 + 2.6 * k, 9), (8.1, 3 + 2.6 * k, 11), 0.14, 5, 'steel')
    m.tube((13.5, 14.5, 7), (13.5, 14.5, 6.2), 0.35, 6, 'steel')
    m.vcyl(13.5, 6.2, 14.5, 15.6, 0.5, 0.5, 8, 'paint_red')
    m.box((5.5, 9.5, 8.5), (6.5, 10.5, 9.5), 'brass')
    m.wheel((6, 10.5, 8), 'z', 0.8, 0.16, 3, 'paint_red')
    m.tube((1.3, 7.5, 2.2), (8.5, 7.5, 2.2), 0.12, 4, 'steel')
    return m



def arc_furnace(lit=False):
    """hdfv 0.23: an electric arc furnace: a squat steel shell on its tilting rockers, the refractory roof with three graphite
    electrodes through it held by their arms from the mast behind, the slag door (glowing while it works), the tapping
    spout, the furnace transformer beside it."""
    m = Model()
    m.box((0, 0, 0), (16, 1.5, 16), 'dark_steel')
    m.box((2, 1.5, 3), (4, 3.5, 13), 'cast_iron')
    m.box((12, 1.5, 3), (14, 3.5, 13), 'cast_iron')
    m.vcyl(8, 8, 3, 9.5, 6.4, 6.4, 24, 'paint_grey')
    for y in (4.5, 7.5):
        m.vcyl(8, 8, y, y + 0.6, 6.7, 6.7, 24, 'dark_steel')
    m.vcyl(8, 8, 9.5, 11, 6.5, 4.6, 24, 'firebrick')
    for x, z in ((8, 6), (6.3, 9), (9.7, 9)):
        m.vcyl(x, z, 10.5, 16, 0.8, 0.8, 10, 'soot')
        m.box((x - 0.5, 14, z - 0.5), (x + 0.5, 14.8, 15), 'copper')
    m.box((6.5, 1.5, 14), (9.5, 16, 15.6), 'dark_steel')
    m.box((4.8, 4.2, 1.4), (11.2, 8, 1.9), 'firebrick')
    m.box((5.6, 4.8, 1.2), (10.4, 7.4, 1.7), 'glow' if lit else 'soot')
    m.box((7, 3, 0.2), (9, 3.8, 2), 'steel')
    m.box((13, 1.5, 0.5), (16, 6.5, 4.5), 'paint_blue')
    m.box((13.6, 4, 0.3), (15.4, 5.6, 0.5), 'gauge')
    return m


def electrolysis_cell(lit=False):
    """hdfv 0.23: a Hall-Heroult pot: the steel potshell with its ribs, the bath under its crust (glowing while it works), six
    carbon anodes hanging on copper rods from the anode beam, the busbars along both sides, the panel at the front."""
    m = Model()
    m.box((1, 0, 1), (15, 6, 15), 'dark_steel')
    for x in (1, 5, 9, 13):
        m.box((x, 0, 0.6), (x + 1, 6.4, 15.4), 'steel')
    m.box((2, 6, 2), (14, 6.4, 14), 'glow' if lit else 'soot')
    for i in range(3):
        for j in range(2):
            x0, z0 = 2.6 + i * 3.8, 3.2 + j * 5.2
            m.box((x0, 6.2, z0), (x0 + 3, 9, z0 + 4.2), 'soot')
            m.vcyl(x0 + 1.5, z0 + 2.1, 9, 13, 0.45, 0.45, 8, 'copper')
    m.box((1.5, 13, 7), (14.5, 14.4, 9), 'steel')
    m.box((1, 6, 7.3), (2, 14.4, 8.7), 'steel')
    m.box((14, 6, 7.3), (15, 14.4, 8.7), 'steel')
    m.box((0, 2, 2), (1, 3.2, 14), 'copper')
    m.box((15, 2, 2), (16, 3.2, 14), 'copper')
    m.box((6, 2.5, 0.6), (10, 5, 1), 'panel')
    return m


MACHINES = {
    'foundry': lambda: foundry(), 'forging_press': forging_press, 'composite_oven': composite_oven, 'vulcanizer': vulcanizer,
    'welding_station': welding_station, 'assembly_bench': assembly_bench, 'alloy_furnace': lambda: alloy_furnace(False),
    'alloy_furnace_on': lambda: alloy_furnace(True), 'battery': battery, 'generator': generator, 'electric_motor': electric_motor,
    'steam_engine': steam_engine, 'treadle': treadle, 'engine_stand': engine_stand, 'creative_power': creative_power, 'pump': pump,
    'fluid_tank': lambda: tank('fluid_tank'), 'cryogenic_tank': lambda: tank('cryogenic_tank'), 'hydrogen_cylinders': lambda: tank('hydrogen_cylinders'),
    'hydrogen_liquefier': hydrogen_liquefier, 'fuel_dispenser': fuel_dispenser, 'wellhead': wellhead, 'work_light': work_light, 'design_table': design_table,
    'paint_booth_controller': paint_booth_controller, 'paint_booth_exhaust': paint_booth_exhaust, 'refit_shop_controller': refit_shop_controller,
    'air_separation_unit': air_separation_unit,
    'arc_furnace': lambda: arc_furnace(False), 'arc_furnace_on': lambda: arc_furnace(True),
    'electrolysis_cell': lambda: electrolysis_cell(False), 'electrolysis_cell_on': lambda: electrolysis_cell(True),
}


# ------------------------------------------------------------------------------------------------ export

def uv_by_position(ob, mats):
    """Texture coordinates: a face's position in the block projected along its main axis (decals: stretched over the face)."""
    me = ob.data
    bm = bmesh.new()
    bm.from_mesh(me)
    layer = bm.loops.layers.uv.verify()
    for f in bm.faces:
        f.normal_update()
        n = f.normal
        gn = (n.x, n.z, -n.y)
        axis = max(range(3), key=lambda i: abs(gn[i]))
        coords = []
        for loop in f.loops:
            co = loop.vert.co
            gx, gy, gz = co.x, co.z, -co.y
            if axis == 0:
                u, v = ((1 - gz) if gn[0] > 0 else gz), gy
            elif axis == 1:
                u, v = gx, ((1 - gz) if gn[1] > 0 else gz)
            else:
                u, v = (gx if gn[2] > 0 else 1 - gx), gy
            coords.append((u, v))
        if mats[f.material_index] in FIT:
            u0, u1 = min(c[0] for c in coords), max(c[0] for c in coords)
            v0, v1 = min(c[1] for c in coords), max(c[1] for c in coords)
            coords = [((u - u0) / max(u1 - u0, 1e-9), (v - v0) / max(v1 - v0, 1e-9)) for u, v in coords]
        for loop, (u, v) in zip(f.loops, coords):
            loop[layer].uv = (min(max(u, 0.0), 1.0), min(max(v, 0.0), 1.0))
    bm.to_mesh(me)
    bm.free()


def tidy(ob):
    """Doubles merged, normals out, faces of more than four corners triangulated, smooth by angle."""
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True)
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.remove_doubles(threshold=1e-6)
    bpy.ops.mesh.normals_make_consistent(inside=False)
    bpy.ops.mesh.select_all(action='DESELECT')
    bpy.ops.mesh.select_face_by_sides(number=4, type='GREATER')
    bpy.ops.mesh.quads_convert_to_tris(quad_method='BEAUTY', ngon_method='BEAUTY')
    bpy.ops.object.mode_set(mode='OBJECT')
    try:
        bpy.ops.object.shade_smooth_by_angle(angle=math.radians(35))
    except (AttributeError, RuntimeError):
        bpy.ops.object.shade_smooth()


def export_machine(name, model):
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o)
    ob = details.obj_from(model.bm, name, model.mats)
    tidy(ob)
    uv_by_position(ob, model.mats)
    faces = len(ob.data.polygons)
    if faces > MAX_FACES:
        raise RuntimeError(f'{name}: {faces} faces (at most {MAX_FACES})')
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True)
    tmp = os.path.join(os.environ.get('TEMP', OUT), 'hdfv_machine_' + name + '.obj')
    bpy.ops.wm.obj_export(filepath=tmp, export_selected_objects=True, export_uv=True, export_normals=True, export_colors=False,
                          export_materials=True, export_triangulated_mesh=False, forward_axis='NEGATIVE_Z', up_axis='Y',
                          export_object_groups=False, path_mode='STRIP')
    obj_lines = [l for l in open(tmp, encoding='utf-8').read().splitlines() if not l.startswith('#')]
    obj_lines = ['mtllib ' + name + '.mtl' if l.startswith('mtllib') else l for l in obj_lines]
    # the material library: each material's texture is the block model's texture of its name
    mtl = []
    for mat in model.mats:
        mtl += ['newmtl ' + mat, 'Ka 0.000000 0.000000 0.000000', 'Kd 1.000000 1.000000 1.000000', 'd 1.000000', 'map_Kd #' + mat, '']
    written = common.write_if_changed(os.path.join(OUT, name + '.obj'), ('\n'.join(obj_lines) + '\n').encode('utf-8'))
    written += common.write_if_changed(os.path.join(OUT, name + '.mtl'), ('\n'.join(mtl)).encode('utf-8'))
    textures = {'particle': 'hdfv:block/machine/' + model.mats[0]}
    for mat in model.mats:
        textures[mat] = 'hdfv:block/machine/' + mat
    block = {'parent': 'minecraft:block/block', 'loader': 'neoforge:obj', 'model': 'hdfv:models/block/machine/' + name + '.obj',
             'flip_v': True, 'textures': textures}
    written += common.write_json_if_changed(os.path.join(MODELS, name + '.json'), block)
    common.log('machine', name, faces, 'faces,', len(model.mats), 'materials')
    return written, set(model.mats)


def main():
    common.reset()
    os.makedirs(OUT, exist_ok=True)
    written, used = 0, set()
    for name, build in MACHINES.items():
        w, mats = export_machine(name, build())
        written += w
        used |= mats
    for mat in sorted(used):
        written += common.write_if_changed(os.path.join(TEXTURES, mat + '.png'), png(TEXTURE_RECIPES[mat]()))
    written += members()
    common.log('machines done:', len(MACHINES), 'blocks,', len(used), 'textures,', written, 'files written')


if __name__ == "__main__":
    main()
