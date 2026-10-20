"""Models hdfv's machining kit (hdfv 0.17) and exports each part as OBJ + MTL into assets/hdfv/models/part/machine: the
detailed parts of the nine machine families (grinder, finisher, gear cutter, ram machine, saw, electrical machining,
cutting table, threader, riveter) that the code (client/machining) places on a formed machine by its size and head,
while it draws the stretching parts (beds, tables, tanks, columns) itself.

Conventions (as details.py): Blender z is the game's y (up); +y faces the operator (the machine's front); lengths run
along x. Sizes are real (metres); the code scales by Part.size. Parts that stand still stand on z = 0. Parts that
turn are centred on their axis at the origin and turn about their own x axis, or about z for a vertical spindle
(named in their docstring; a vertical tool hangs from z = 0 downward). Each part has a fine (lod0) and a coarse (lod1)
object. Materials are finish ids (finishes_spec.json): 'paint' is the machine's colour (the code tints it).
Run: scripts/blender.ps1 machining_kit"""
import math
import os
import sys

import bmesh
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import details  # noqa: E402
from details import box, cylinder, stack, torus  # noqa: E402
from kitshapes import dome, new_faces, prism, rounded_box, rounded_rect, transform, tube_between  # noqa: E402

OUT = os.path.join(common.ASSETS, 'models', 'part', 'machine')


# ------------------------------------------------------------------------------------------------ helpers

def xcyl(bm, x0, x1, r, sides, mat, y=0.0, z=0.0, caps=(True, True), r1=None):
    """A (truncated) cylinder along x from x0 to x1, its axis through (y, z)."""
    before = set(bm.faces)
    cylinder(bm, x0, x1, r, r if r1 is None else r1, sides, mat, caps)
    transform(bm, new_faces(bm, before), Matrix.Translation(Vector((0, y, z))) @ Matrix.Rotation(math.radians(90), 4, 'Y'))


def ycyl(bm, y0, y1, r, sides, mat, x=0.0, z=0.0, caps=(True, True), r1=None):
    """A (truncated) cylinder along y from y0 to y1, its axis through (x, z)."""
    before = set(bm.faces)
    cylinder(bm, y0, y1, r, r if r1 is None else r1, sides, mat, caps)
    transform(bm, new_faces(bm, before), Matrix.Translation(Vector((x, 0, z))) @ Matrix.Rotation(math.radians(-90), 4, 'X'))


def zcyl(bm, z0, z1, r, sides, mat, x=0.0, y=0.0, caps=(True, True), r1=None):
    """A (truncated) cylinder along z from z0 to z1, its axis through (x, y)."""
    cylinder(bm, z0, z1, r, r if r1 is None else r1, sides, mat, caps, offset=(x, y))


def teeth(bm, axis, radius, depth, width, count, mat, centre=(0.0, 0.0, 0.0), phase=0.0, fill=0.5):
    """Teeth round an axis ('x' or 'z') through a centre: count boxes from the radius out by a depth, a width along the
    axis, each a fill of the pitch thick."""
    t = 2 * math.pi * radius / count * fill
    c = Matrix.Translation(Vector(centre))
    for k in range(count):
        before = set(bm.faces)
        box(bm, (-width / 2, -t / 2, radius - 0.001), (width / 2, t / 2, radius + depth), mat)
        a = math.radians(360.0 * k / count + phase)
        if axis == 'x':
            m = Matrix.Rotation(a, 4, 'X')
        else:
            m = Matrix.Rotation(a, 4, 'Z') @ Matrix.Rotation(math.radians(90), 4, 'Y')
        transform(bm, new_faces(bm, before), c @ m)


def ridges(bm, axis_x0, axis_x1, r, height, pitch, sides, mat, y=0.0, z=0.0):
    """Rings standing out of a cylinder along x (a thread roll's or a hob's ridges), one a pitch."""
    x = axis_x0 + pitch / 2
    while x + pitch / 4 <= axis_x1:
        xcyl(bm, x - pitch / 4, x + pitch / 4, r + height, sides, mat, y, z)
        x += pitch


def screen(bm, x0, x1, z0, z1, y, glass, bezel, fine):
    """An operator's screen on a face at y (facing +y): a bezel and the glass over it."""
    box(bm, (x0, y - 0.004, z0), (x1, y + 0.008, z1), bezel)
    if fine:
        box(bm, (x0 + 0.015, y + 0.008, z0 + 0.015), (x1 - 0.015, y + 0.011, z1 - 0.015), glass)


def buttons(bm, x0, z, count, step, y, mat, r=0.012, fine=True):
    """A row of round push buttons on a face at y (facing +y)."""
    if not fine:
        return
    for k in range(count):
        ycyl(bm, y, y + 0.012, r, 8, mat, x=x0 + k * step, z=z)


# ------------------------------------------------------------------------------------------------ common parts

def control_pendant(fine):
    """A swinging control pendant 0.4 m tall on its arm: the arm rises from z = 0, the box with a screen, keys and a
    red emergency stop faces +y."""
    bm = bmesh.new()
    s = 12 if fine else 6
    zcyl(bm, 0, 0.36, 0.025, s, 1)
    rounded_box(bm, (-0.16, -0.05, 0.36), (0.16, 0.05, 0.76), 0.02, 0, 2 if fine else 0)
    screen(bm, -0.12, 0.12, 0.52, 0.71, 0.05, 2, 3, fine)
    buttons(bm, -0.1, 0.44, 5, 0.04, 0.05, 3, fine=fine)
    if fine:
        ycyl(bm, 0.05, 0.075, 0.02, 12, 4, x=0.11, z=0.44)
    return bm, ['paint', 'machined_steel', 'glass', 'dash_plastic', 'lamp_red']


def nc_cabinet(fine):
    """The numerical control (and an EDM's or a laser's power) cabinet, 0.6 x 0.45 x 1.15 m: a screen and keypad in
    front, vents on the sides, a three-colour signal tower on top."""
    bm = bmesh.new()
    rounded_box(bm, (-0.3, -0.225, 0), (0.3, 0.225, 1.15), 0.02, 0, 2 if fine else 0)
    screen(bm, -0.22, 0.22, 0.7, 1.02, 0.225, 1, 2, fine)
    if fine:
        for r in range(3):
            for c in range(6):
                box(bm, (-0.2 + c * 0.066, 0.225, 0.52 + r * 0.05), (-0.16 + c * 0.066, 0.237, 0.555 + r * 0.05), 2)
        for k in range(6):
            box(bm, (0.3, -0.15, 0.2 + 0.06 * k), (0.306, 0.15, 0.23 + 0.06 * k), 3)
        box(bm, (-0.25, 0.225, 0.08), (-0.2, 0.235, 0.4), 4)
    zcyl(bm, 1.15, 1.2, 0.03, 10 if fine else 6, 4, x=0.22, y=-0.15)
    for k, m in enumerate((5, 6, 7)):
        zcyl(bm, 1.2 + 0.06 * k, 1.26 + 0.06 * k, 0.03, 10 if fine else 6, m, x=0.22, y=-0.15)
    return bm, ['paint', 'glass', 'dash_plastic', 'cast_iron', 'machined_steel', 'lamp_red', 'lamp_amber', 'lamp_clear']


def motor(fine):
    """A totally enclosed fan-cooled motor 0.4 m long on its feet (axis along x at z = 0.16): finned frame, fan cowl at
    -x, the shaft out at +x, the terminal box on top."""
    bm = bmesh.new()
    s = 20 if fine else 10
    xcyl(bm, -0.2, 0.15, 0.13, s, 0, z=0.16)
    xcyl(bm, -0.26, -0.2, 0.125, s, 0, z=0.16, r1=0.13)
    xcyl(bm, 0.15, 0.2, 0.06, s, 2, z=0.16)
    xcyl(bm, 0.2, 0.28, 0.019, 10 if fine else 6, 1, z=0.16)
    box(bm, (-0.16, -0.12, 0), (0.12, 0.12, 0.04), 0)
    box(bm, (-0.08, -0.06, 0.28), (0.06, 0.06, 0.34), 0)
    if fine:
        teeth(bm, 'x', 0.13, 0.012, 0.33, 24, 0, centre=(-0.025, 0, 0.16), fill=0.25)
        for k in range(10):
            a = 2 * math.pi * k / 10
            box(bm, (-0.265, 0.1 * math.cos(a) - 0.008, 0.16 + 0.1 * math.sin(a) - 0.008), (-0.26, 0.1 * math.cos(a) + 0.008, 0.16 + 0.1 * math.sin(a) + 0.008), 3)
    return bm, ['paint', 'machined_steel', 'cast_iron', 'dash_plastic']


def coolant_nozzle(fine):
    """A segmented coolant hose 0.3 m from its block on z = 0, bending toward +y and down, a flat nozzle at its end."""
    bm = bmesh.new()
    box(bm, (-0.03, -0.03, 0), (0.03, 0.03, 0.04), 1)
    pts = [(0, 0, 0.04), (0, 0.02, 0.12), (0, 0.07, 0.2), (0, 0.14, 0.24), (0, 0.21, 0.23), (0, 0.27, 0.18)]
    seg = len(pts) - 1 if fine else 2
    use = pts if fine else [pts[0], pts[2], pts[-1]]
    for a, b in zip(use, use[1:]):
        tube_between(bm, a, b, 0.012, 8 if fine else 5, 0)
        if fine:
            torus(bm, 0.012, 0.004, b, 8, 4, 0, axis='z')
    tube_between(bm, pts[-1], (0, 0.3, 0.13), 0.009, 6, 2)
    del seg
    return bm, ['plastic', 'brass', 'stainless']


def guard_window(fine):
    """A sliding guard door 1.0 x 0.6 m standing on z = 0, thin in y: a painted frame round a window, a handle in front."""
    bm = bmesh.new()
    box(bm, (-0.5, -0.02, 0), (0.5, 0.02, 0.06), 0)
    box(bm, (-0.5, -0.02, 0.54), (0.5, 0.02, 0.6), 0)
    box(bm, (-0.5, -0.02, 0.06), (-0.44, 0.02, 0.54), 0)
    box(bm, (0.44, -0.02, 0.06), (0.5, 0.02, 0.54), 0)
    box(bm, (-0.44, -0.004, 0.06), (0.44, 0.004, 0.54), 1)
    if fine:
        tube_between(bm, (0.36, 0.02, 0.2), (0.36, 0.07, 0.2), 0.008, 6, 2)
        tube_between(bm, (0.36, 0.02, 0.4), (0.36, 0.07, 0.4), 0.008, 6, 2)
        tube_between(bm, (0.36, 0.07, 0.2), (0.36, 0.07, 0.4), 0.01, 8, 2)
    return bm, ['paint', 'glass', 'chrome']


# ------------------------------------------------------------------------------------------------ grinder

def wheelhead(fine):
    """A grinder's wheelhead 0.5 m wide standing on z = 0: the slide, the spindle housing, the motor on top driving
    by a belt case, the wheel guard over the wheel's place (centre at y = 0.3, z = 0.3; the wheel is grinding_wheel)."""
    bm = bmesh.new()
    box(bm, (-0.25, -0.3, 0), (0.25, 0.15, 0.08), 0)
    rounded_box(bm, (-0.2, -0.25, 0.08), (0.2, 0.12, 0.4), 0.02, 0, 2 if fine else 0)
    xcyl(bm, -0.18, 0.1, 0.07, 16 if fine else 8, 1, y=0.3, z=0.3)
    rounded_box(bm, (0.1, 0.06, 0.02), (0.18, 0.56, 0.58), 0.03, 0, 2 if fine else 0)
    rounded_box(bm, (-0.16, 0.06, 0.4), (0.18, 0.56, 0.58), 0.05, 0, 2 if fine else 0)
    xcyl(bm, -0.2, 0.1, 0.09, 16 if fine else 8, 2, y=-0.1, z=0.5)
    rounded_box(bm, (-0.24, -0.2, 0.42), (-0.2, 0.0, 0.6), 0.01, 2, 0)
    if fine:
        box(bm, (-0.05, 0.12, 0.12), (0.05, 0.16, 0.2), 3)
        teeth(bm, 'x', 0.09, 0.008, 0.28, 16, 2, centre=(-0.05, -0.1, 0.5), fill=0.25)
    return bm, ['paint', 'machined_steel', 'cast_iron', 'brass']


def grinding_wheel(fine):
    """A vitrified grinding wheel 0.4 m across and 50 mm wide between its flanges, turning about x."""
    bm = bmesh.new()
    s = 36 if fine else 14
    xcyl(bm, -0.025, 0.025, 0.2, s, 0)
    xcyl(bm, -0.035, -0.025, 0.08, s // 2, 1)
    xcyl(bm, 0.025, 0.035, 0.08, s // 2, 1)
    if fine:
        xcyl(bm, -0.045, 0.045, 0.03, 10, 2)
        for k in range(6):
            a = 2 * math.pi * k / 6
            xcyl(bm, 0.035, 0.042, 0.007, 6, 2, y=0.055 * math.cos(a), z=0.055 * math.sin(a))
    return bm, ['ceramic', 'machined_steel', 'forged_steel']


def regulating_wheel(fine):
    """A centreless grinder's rubber-bonded regulating wheel 0.3 m across and 0.1 m wide, turning about x."""
    bm = bmesh.new()
    s = 28 if fine else 12
    xcyl(bm, -0.05, 0.05, 0.15, s, 0)
    xcyl(bm, -0.06, -0.05, 0.07, s // 2, 1)
    xcyl(bm, 0.05, 0.06, 0.07, s // 2, 1)
    return bm, ['rubber', 'machined_steel']


def centreless_frame(fine):
    """A centreless grinder's frame 0.9 m along y: the base, the grinding wheel's guard at -y and the regulating
    wheel's housing at +y (wheel centres at y = -0.22 and +0.22, z = 0.55), the work rest blade between them."""
    bm = bmesh.new()
    rounded_box(bm, (-0.3, -0.45, 0), (0.3, 0.45, 0.32), 0.02, 0, 2 if fine else 0)
    rounded_box(bm, (-0.12, -0.5, 0.32), (0.12, -0.02, 0.8), 0.06, 0, 2 if fine else 0)
    rounded_box(bm, (-0.12, 0.08, 0.32), (0.12, 0.45, 0.72), 0.04, 0, 2 if fine else 0)
    box(bm, (-0.25, -0.012, 0.32), (0.25, 0.012, 0.52), 1)
    box(bm, (-0.25, -0.03, 0.32), (0.25, 0.03, 0.42), 2)
    if fine:
        xcyl(bm, 0.12, 0.2, 0.05, 12, 1, y=-0.22, z=0.55)
        xcyl(bm, 0.12, 0.2, 0.04, 12, 1, y=0.22, z=0.55)
        tube_between(bm, (0, 0.2, 0.72), (0, 0.2, 0.85), 0.012, 8, 3)
        tube_between(bm, (0, 0.2, 0.85), (0, -0.05, 0.85), 0.012, 8, 3)
    return bm, ['paint', 'machined_steel', 'cast_iron', 'plastic']


def internal_spindle(fine):
    """An internal grinding spindle 0.6 m long standing on z = 0: its housing and the slender quill out along +x with a
    small wheel (30 mm) at its tip, the axis at z = 0.15."""
    bm = bmesh.new()
    s = 16 if fine else 8
    box(bm, (-0.3, -0.12, 0), (0.05, 0.12, 0.05), 0)
    xcyl(bm, -0.3, 0.05, 0.1, s, 0, z=0.15)
    xcyl(bm, 0.05, 0.22, 0.04, s, 1, z=0.15, r1=0.025)
    xcyl(bm, 0.22, 0.28, 0.01, 8, 1, z=0.15)
    xcyl(bm, 0.28, 0.3, 0.015, 12 if fine else 6, 2, z=0.15)
    if fine:
        xcyl(bm, -0.36, -0.3, 0.06, 12, 3, z=0.15)
    return bm, ['paint', 'machined_steel', 'ceramic', 'cast_iron']


def magnetic_chuck(fine):
    """A surface grinder's magnetic chuck 0.6 x 0.3 x 0.08 m: steel top with brass pole lines, the switch at +y."""
    bm = bmesh.new()
    box(bm, (-0.3, -0.15, 0), (0.3, 0.15, 0.075), 0)
    box(bm, (-0.29, -0.14, 0.075), (0.29, 0.14, 0.08), 1)
    if fine:
        for k in range(12):
            x = -0.27 + k * 0.049
            box(bm, (x, -0.14, 0.0755), (x + 0.008, 0.14, 0.0805), 2)
        ycyl(bm, 0.15, 0.17, 0.02, 10, 3, x=0.22, z=0.04)
        box(bm, (0.215, 0.165, 0.04), (0.225, 0.2, 0.05), 3)
    return bm, ['cast_iron', 'machined_steel', 'brass', 'plastic']


def centre_stock(fine):
    """A grinder's (or lathe-like) centre stock 0.3 m long standing on z = 0, its dead centre pointing +x at z = 0.2:
    the base, the body, the quill and the centre (mirror it for the other end)."""
    bm = bmesh.new()
    s = 16 if fine else 8
    box(bm, (-0.15, -0.1, 0), (0.12, 0.1, 0.06), 0)
    rounded_box(bm, (-0.15, -0.08, 0.06), (0.08, 0.08, 0.28), 0.02, 0, 2 if fine else 0)
    xcyl(bm, 0.08, 0.14, 0.035, s, 1, z=0.2)
    xcyl(bm, 0.14, 0.17, 0.035, s, 1, z=0.2, r1=0.0)
    if fine:
        xcyl(bm, -0.21, -0.15, 0.018, 8, 1, z=0.2)
        xcyl(bm, -0.23, -0.21, 0.05, 6, 2, z=0.2)
    return bm, ['paint', 'machined_steel', 'cast_iron']


def diamond_dresser(fine):
    """A wheel dresser 0.12 m tall on z = 0: a block, the angled holder and the diamond at its tip (toward +y)."""
    bm = bmesh.new()
    box(bm, (-0.03, -0.03, 0), (0.03, 0.03, 0.04), 0)
    tube_between(bm, (0, 0, 0.04), (0, 0.06, 0.11), 0.008, 8 if fine else 5, 1)
    if fine:
        dome(bm, 0.006, 0.006, 0.0, 8, 2, 2, offset=(0, 0))
        transform(bm, list(bm.faces)[-16:], Matrix.Translation(Vector((0, 0.062, 0.112))))
    return bm, ['cast_iron', 'machined_steel', 'glass']


def tool_grinder_head(fine):
    """A tool and cutter grinder's head 0.5 m tall on z = 0: the swivel base, the column and the spindle carrying a cup
    wheel toward +y (centre at y = 0.22, z = 0.38)."""
    bm = bmesh.new()
    s = 20 if fine else 10
    zcyl(bm, 0, 0.06, 0.2, s, 0)
    rounded_box(bm, (-0.12, -0.15, 0.06), (0.12, 0.1, 0.5), 0.02, 0, 2 if fine else 0)
    ycyl(bm, 0.1, 0.16, 0.06, s, 1, z=0.38)
    ycyl(bm, 0.16, 0.22, 0.08, s, 2, z=0.38, r1=0.09)
    if fine:
        ycyl(bm, 0.21, 0.225, 0.05, s, 1, z=0.38)
        zcyl(bm, 0.5, 0.56, 0.05, 12, 3, y=-0.05)
    return bm, ['paint', 'machined_steel', 'ceramic', 'cast_iron']


# ------------------------------------------------------------------------------------------------ finisher

def honing_head(fine):
    """A vertical honing head 0.5 m tall standing on z = 0: the stroking slide, the spindle housing and the motor on
    top; the tool hangs from the spindle nose at (0, 0.15, 0) (honing_tool)."""
    bm = bmesh.new()
    s = 16 if fine else 8
    rounded_box(bm, (-0.15, -0.15, 0.08), (0.15, 0.25, 0.42), 0.02, 0, 2 if fine else 0)
    zcyl(bm, 0.0, 0.08, 0.05, s, 1, y=0.15)
    zcyl(bm, 0.42, 0.62, 0.11, s, 0, y=0.0)
    if fine:
        teeth(bm, 'z', 0.11, 0.01, 0.18, 16, 0, centre=(0, 0, 0.52), fill=0.3)
        box(bm, (-0.16, -0.12, 0.12), (-0.15, 0.2, 0.38), 2)
    return bm, ['paint', 'machined_steel', 'cast_iron']


def honing_tool(fine):
    """A honing tool 0.3 m long hanging from z = 0 (to z = -0.3), turning about z: the body and six abrasive stones."""
    bm = bmesh.new()
    zcyl(bm, -0.3, 0, 0.03, 12 if fine else 6, 0)
    zcyl(bm, -0.06, 0, 0.022, 10 if fine else 6, 0)
    n = 6 if fine else 3
    for k in range(n):
        a = 2 * math.pi * k / n
        before = set(bm.faces)
        box(bm, (0.028, -0.006, -0.28), (0.04, 0.006, -0.12), 1)
        transform(bm, new_faces(bm, before), Matrix.Rotation(a, 4, 'Z'))
    return bm, ['machined_steel', 'ceramic']


def lapping_plate(fine):
    """A lapping machine's cast iron plate 0.7 m across, 50 mm thick on z = 0, turning about z, with its grooves."""
    bm = bmesh.new()
    s = 40 if fine else 16
    zcyl(bm, 0, 0.05, 0.35, s, 0)
    zcyl(bm, -0.06, 0, 0.08, 12, 1)
    if fine:
        for k in range(-4, 5):
            box(bm, (k * 0.07 - 0.003, -0.33, 0.05), (k * 0.07 + 0.003, 0.33, 0.052), 2)
            box(bm, (-0.33, k * 0.07 - 0.003, 0.05), (0.33, k * 0.07 + 0.003, 0.052), 2)
    return bm, ['cast_iron', 'machined_steel', 'graphite']


def lap_ring(fine):
    """A conditioning ring 0.24 m across on z = 0 that rides the lapping plate, turning about z."""
    bm = bmesh.new()
    s = 24 if fine else 10
    stack(bm, [(0, 0.06, 0.12, 0.12, s, 0)], seated=True, open_top=False)
    if fine:
        zcyl(bm, 0.06, 0.07, 0.09, s, 1)
    return bm, ['cast_iron', 'machined_steel']


def polishing_head(fine):
    """A double-ended buffing head 0.7 m wide standing on z = 0: the pedestal, the motor with its spindle along x at
    z = 0.95 and the dust hoods over both buffs' places (x = -0.3 and +0.3; the buffs are buff_wheel)."""
    bm = bmesh.new()
    s = 18 if fine else 8
    zcyl(bm, 0, 0.05, 0.22, s, 0)
    zcyl(bm, 0.05, 0.8, 0.08, s, 0)
    xcyl(bm, -0.16, 0.16, 0.12, s, 0, z=0.95)
    xcyl(bm, -0.34, 0.34, 0.02, 8, 1, z=0.95)
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.22, side * 0.38))
        rounded_box(bm, (x0, -0.2, 0.95), (x1, 0.05, 1.15), 0.05, 2, 2 if fine else 0)
        if fine:
            tube_between(bm, (side * 0.3, -0.2, 1.05), (side * 0.3, -0.3, 1.25), 0.05, 10, 2)
    return bm, ['paint', 'machined_steel', 'cast_iron']


def buff_wheel(fine):
    """A stitched cotton buff 0.3 m across and 50 mm wide, turning about x."""
    bm = bmesh.new()
    s = 28 if fine else 12
    xcyl(bm, -0.025, 0.025, 0.15, s, 0)
    if fine:
        for r in (0.06, 0.1, 0.13):
            xcyl(bm, -0.026, 0.026, r, s, 1, caps=(False, False))
    xcyl(bm, -0.035, 0.035, 0.03, 10 if fine else 6, 1)
    return bm, ['fabric', 'machined_steel']


def vibratory_bowl(fine):
    """A vibratory deburring bowl 0.8 m across on z = 0: its base on springs and the bowl of ceramic media."""
    bm = bmesh.new()
    s = 28 if fine else 12
    zcyl(bm, 0, 0.12, 0.35, s, 0)
    if fine:
        for k in range(8):
            a = 2 * math.pi * k / 8
            torus(bm, 0.02, 0.006, (0.3 * math.cos(a), 0.3 * math.sin(a), 0.15), 8, 4, 1)
            torus(bm, 0.02, 0.006, (0.3 * math.cos(a), 0.3 * math.sin(a), 0.19), 8, 4, 1)
    stack(bm, [(0.22, 0.5, 0.36, 0.4, s, 2)], seated=False, open_top=True)
    zcyl(bm, 0.22, 0.5, 0.12, s // 2, 2)
    zcyl(bm, 0.45, 0.47, 0.39, s, 3, caps=(False, True))
    return bm, ['paint', 'machined_steel', 'rubber', 'ceramic']


# ------------------------------------------------------------------------------------------------ gear cutter

def hob_slide(fine):
    """A hobbing machine's hob head 0.5 m tall standing on z = 0: the swivel plate facing +y, the slide and the arbor
    supports either side of the hob's place (axis along x at y = 0.2, z = 0.3; the hob is hob)."""
    bm = bmesh.new()
    s = 24 if fine else 10
    box(bm, (-0.25, -0.15, 0), (0.25, 0.05, 0.6), 0)
    ycyl(bm, 0.05, 0.1, 0.24, s, 0, z=0.3)
    box(bm, (-0.22, 0.1, 0.18), (-0.14, 0.26, 0.42), 1)
    box(bm, (0.14, 0.1, 0.18), (0.22, 0.26, 0.42), 1)
    xcyl(bm, -0.14, 0.14, 0.012, 8, 2, y=0.2, z=0.3)
    if fine:
        torus(bm, 0.22, 0.008, (0, 0.1, 0.3), 24, 4, 2, axis='y')
        for k in range(8):
            a = 2 * math.pi * k / 8
            ycyl(bm, 0.1, 0.11, 0.008, 6, 2, x=0.2 * math.cos(a), z=0.3 + 0.2 * math.sin(a))
    return bm, ['paint', 'machined_steel', 'forged_steel']


def hob(fine):
    """A single-start hob 80 mm across and 120 mm long, TiN coated, turning about x: the body and its gashed rows of
    teeth (staggered as the thread runs)."""
    bm = bmesh.new()
    xcyl(bm, -0.06, 0.06, 0.032, 16 if fine else 8, 0)
    rows = 10 if fine else 4
    for k in range(rows):
        x = -0.054 + k * 0.012
        teeth(bm, 'x', 0.032, 0.008, 0.007, 12 if fine else 6, 0, centre=(x, 0, 0), phase=k * 30.0, fill=0.55)
    xcyl(bm, -0.07, 0.07, 0.012, 8, 1)
    return bm, ['brass', 'machined_steel']


def shaper_cutter(fine):
    """A gear shaper's pinion cutter 0.12 m across, 30 mm thick, turning (and stroking) about z, its face at z = 0 and
    its spindle above."""
    bm = bmesh.new()
    zcyl(bm, 0, 0.03, 0.055, 24 if fine else 10, 0)
    teeth(bm, 'z', 0.055, 0.007, 0.03, 26 if fine else 10, 0, centre=(0, 0, 0.015), fill=0.5)
    zcyl(bm, 0.03, 0.2, 0.025, 12 if fine else 6, 1)
    return bm, ['brass', 'machined_steel']


def gear_work(fine):
    """A gear being cut, 0.2 m across and 40 mm wide, turning about z on its arbor: 30 teeth round a webbed blank."""
    bm = bmesh.new()
    zcyl(bm, -0.02, 0.02, 0.092, 30 if fine else 12, 0)
    teeth(bm, 'z', 0.092, 0.008, 0.04, 30 if fine else 12, 0, fill=0.5)
    zcyl(bm, -0.04, 0.06, 0.02, 10 if fine else 6, 1)
    if fine:
        zcyl(bm, 0.02, 0.025, 0.06, 16, 0)
        zcyl(bm, -0.025, -0.02, 0.06, 16, 0)
    return bm, ['forged_steel', 'machined_steel']


# ------------------------------------------------------------------------------------------------ ram machine

def shaper_ram(fine):
    """A shaper's ram 0.9 m long standing on z = 0, stroking along x: the ram, the clapper box and tool at its +x end."""
    bm = bmesh.new()
    rounded_box(bm, (-0.45, -0.15, 0), (0.4, 0.15, 0.22), 0.02, 0, 2 if fine else 0)
    box(bm, (0.4, -0.12, -0.08), (0.48, 0.12, 0.22), 0)
    box(bm, (0.42, -0.06, -0.2), (0.47, 0.06, -0.08), 1)
    box(bm, (0.435, -0.012, -0.3), (0.455, 0.012, -0.2), 2)
    if fine:
        ycyl(bm, 0.12, 0.16, 0.05, 12, 1, x=0.44, z=0.1)
        box(bm, (-0.45, -0.16, 0.06), (0.4, -0.15, 0.1), 1)
        box(bm, (-0.45, 0.15, 0.06), (0.4, 0.16, 0.1), 1)
    return bm, ['paint', 'machined_steel', 'forged_steel']


def slotter_ram(fine):
    """A slotter's vertical ram 0.8 m tall hanging down from z = 0 (to z = -0.8), stroking along z, its tool at the
    bottom facing +y."""
    bm = bmesh.new()
    rounded_box(bm, (-0.12, -0.12, -0.7), (0.12, 0.12, 0), 0.02, 0, 2 if fine else 0)
    box(bm, (-0.06, -0.02, -0.8), (0.06, 0.1, -0.7), 1)
    box(bm, (-0.012, 0.06, -0.86), (0.012, 0.14, -0.8), 2)
    if fine:
        box(bm, (-0.13, -0.05, -0.65), (-0.12, 0.05, -0.05), 1)
        box(bm, (0.12, -0.05, -0.65), (0.13, 0.05, -0.05), 1)
    return bm, ['paint', 'machined_steel', 'forged_steel']


def broach(fine):
    """A pull broach 0.8 m long along x, 40 mm across, its pull end at -x: the shank and rows of rising teeth."""
    bm = bmesh.new()
    xcyl(bm, -0.4, -0.25, 0.012, 10 if fine else 6, 0)
    xcyl(bm, -0.25, 0.38, 0.016, 12 if fine else 6, 0)
    rows = 30 if fine else 6
    for k in range(rows):
        x = -0.22 + k * 0.6 / rows
        h = 0.001 + 0.004 * k / rows
        xcyl(bm, x, x + 0.006, 0.016 + h, 12 if fine else 6, 0)
    if fine:
        xcyl(bm, -0.4, -0.37, 0.016, 10, 1)
    return bm, ['machined_steel', 'brass']


# ------------------------------------------------------------------------------------------------ saw

def band_saw_bow(fine):
    """A horizontal band saw's bow 1.1 m long, hinged at the origin and reaching along +x (it lowers about x): the
    wheel covers at both ends, the beam between them, the blade under it with its guides, a blade guard."""
    bm = bmesh.new()
    s = 20 if fine else 8
    ycyl(bm, -0.08, 0.04, 0.2, s, 0, x=0.12, z=0.1)
    ycyl(bm, -0.08, 0.04, 0.2, s, 0, x=0.98, z=0.1)
    box(bm, (0.12, -0.08, 0.2), (0.98, 0.02, 0.3), 0)
    box(bm, (0.12, -0.002, -0.11), (0.98, 0.002, -0.08), 1)
    for x in (0.3, 0.8):
        box(bm, (x - 0.03, -0.04, -0.1), (x + 0.03, 0.04, 0.2), 2)
    if fine:
        ycyl(bm, 0.04, 0.12, 0.06, 12, 2, x=0.12, z=0.1)
        tube_between(bm, (1.0, -0.04, 0.3), (1.0, -0.04, 0.45), 0.012, 8, 2)
    return bm, ['paint', 'machined_steel', 'cast_iron']


def circular_saw_blade(fine):
    """A cold saw's blade 350 mm across, 3 mm thick, turning about x: the plate and its teeth."""
    bm = bmesh.new()
    xcyl(bm, -0.0015, 0.0015, 0.17, 36 if fine else 14, 0)
    teeth(bm, 'x', 0.17, 0.006, 0.003, 60 if fine else 16, 0, fill=0.45)
    xcyl(bm, -0.012, 0.012, 0.04, 12 if fine else 6, 1)
    return bm, ['machined_steel', 'forged_steel']


def cold_saw_head(fine):
    """A cold saw's gearhead 0.5 m long standing on z = 0, pivoting at its -x end: the gearbox, the motor and the blade
    guard over the blade's place (centre at x = 0.3, y = 0.12, z = 0.0; circular_saw_blade)."""
    bm = bmesh.new()
    s = 16 if fine else 8
    rounded_box(bm, (-0.2, -0.15, 0.0), (0.3, 0.08, 0.22), 0.02, 0, 2 if fine else 0)
    xcyl(bm, -0.36, -0.2, 0.1, s, 0, z=0.12)
    rounded_box(bm, (0.1, 0.09, -0.02), (0.5, 0.15, 0.2), 0.05, 0, 2 if fine else 0)
    if fine:
        tube_between(bm, (0.2, 0.08, 0.22), (0.05, 0.08, 0.36), 0.015, 8, 1)
        box(bm, (0.0, 0.03, 0.36), (0.12, 0.13, 0.4), 2)
    return bm, ['paint', 'machined_steel', 'rubber']


def hacksaw_frame(fine):
    """A power hacksaw's frame 0.6 m long, stroking along x: the bow (a C of tube) with its blade along the bottom at
    z = 0, tensioned between two pins."""
    bm = bmesh.new()
    s = 8 if fine else 5
    tube_between(bm, (-0.3, 0, 0.0), (-0.3, 0, 0.2), 0.012, s, 0)
    tube_between(bm, (0.3, 0, 0.0), (0.3, 0, 0.2), 0.012, s, 0)
    tube_between(bm, (-0.3, 0, 0.2), (0.3, 0, 0.2), 0.015, s, 0)
    box(bm, (-0.3, -0.001, -0.02), (0.3, 0.001, 0.01), 1)
    if fine:
        for x in (-0.3, 0.3):
            ycyl(bm, -0.01, 0.01, 0.01, 8, 2, x=x, z=0.0)
    return bm, ['paint', 'machined_steel', 'brass']


def bar_vise(fine):
    """A saw's (or threader's) vise 0.3 m wide on z = 0: the fixed and moving jaws across y and the screw with its handle at +y."""
    bm = bmesh.new()
    box(bm, (-0.15, -0.15, 0), (0.15, 0.15, 0.04), 0)
    box(bm, (-0.15, -0.15, 0.04), (0.15, -0.1, 0.16), 0)
    box(bm, (-0.15, 0.05, 0.04), (0.15, 0.1, 0.16), 0)
    ycyl(bm, 0.1, 0.25, 0.012, 8, 1, z=0.1)
    if fine:
        tube_between(bm, (-0.08, 0.25, 0.1), (0.08, 0.25, 0.1), 0.008, 6, 1)
        for y in (-0.1, 0.05):
            box(bm, (-0.14, y - 0.001, 0.06), (0.14, y + 0.001, 0.15), 2)
    return bm, ['cast_iron', 'machined_steel', 'forged_steel']


# ------------------------------------------------------------------------------------------------ electrical machining

def edm_quill(fine):
    """A die-sinking EDM's quill head 0.5 m tall standing on z = 0: the head, the servo motor on top and the ram guide;
    the ram (edm_ram) hangs from (0, 0.12, 0)."""
    bm = bmesh.new()
    rounded_box(bm, (-0.18, -0.2, 0.0), (0.18, 0.2, 0.42), 0.03, 0, 2 if fine else 0)
    zcyl(bm, 0.42, 0.56, 0.08, 16 if fine else 8, 1)
    box(bm, (-0.08, 0.04, -0.04), (0.08, 0.2, 0.0), 1)
    if fine:
        screen(bm, -0.12, 0.12, 0.24, 0.36, 0.2, 2, 3, fine)
    return bm, ['paint', 'cast_iron', 'glass', 'dash_plastic']


def edm_ram(fine):
    """A die-sinking EDM's ram 0.45 m long hanging from z = 0, stroking along z: the square ram, the chuck and a
    graphite electrode at its foot."""
    bm = bmesh.new()
    box(bm, (-0.05, -0.05, -0.36), (0.05, 0.05, 0.0), 0)
    box(bm, (-0.07, -0.07, -0.4), (0.07, 0.07, -0.36), 1)
    box(bm, (-0.04, -0.03, -0.45), (0.04, 0.03, -0.4), 2)
    return bm, ['machined_steel', 'stainless', 'graphite']


def wire_head(fine):
    """A wire EDM's head 0.8 m tall standing on z = 0 behind the tank: the C of upper and lower arms reaching toward +y
    with their guides (the wire runs between y = 0.45 at z = 0.12 and 0.62), the wire spool on top."""
    bm = bmesh.new()
    box(bm, (-0.15, -0.2, 0), (0.15, 0.1, 0.8), 0)
    box(bm, (-0.06, 0.1, 0.05), (0.06, 0.45, 0.11), 1)
    box(bm, (-0.06, 0.1, 0.63), (0.06, 0.45, 0.7), 1)
    zcyl(bm, 0.11, 0.13, 0.02, 10 if fine else 6, 2, y=0.45)
    zcyl(bm, 0.61, 0.63, 0.02, 10 if fine else 6, 2, y=0.45)
    zcyl(bm, 0.13, 0.61, 0.0013, 4, 3, y=0.45)
    xcyl(bm, -0.08, 0.08, 0.09, 16 if fine else 8, 4, y=-0.05, z=0.9)
    if fine:
        xcyl(bm, -0.1, -0.08, 0.11, 16, 2, y=-0.05, z=0.9)
        xcyl(bm, 0.08, 0.1, 0.11, 16, 2, y=-0.05, z=0.9)
        box(bm, (-0.02, -0.05, 0.8), (0.02, -0.01, 0.82), 2)
    return bm, ['paint', 'stainless', 'ceramic', 'brass', 'copper']


def hole_edm_head(fine):
    """A hole-drilling EDM's head 0.4 m tall standing on z = 0: the head, the spindle turning the tube electrode, the
    ceramic guide near the work at (0, 0.12, -0.1)."""
    bm = bmesh.new()
    rounded_box(bm, (-0.1, -0.1, 0.0), (0.1, 0.1, 0.4), 0.02, 0, 2 if fine else 0)
    zcyl(bm, -0.05, 0.0, 0.03, 12 if fine else 6, 1, y=0.12)
    box(bm, (-0.03, 0.08, -0.04), (0.03, 0.15, 0.06), 1)
    zcyl(bm, -0.1, -0.05, 0.008, 8, 2, y=0.12)
    zcyl(bm, -0.14, 0.2, 0.0008, 4, 3, y=0.12)
    return bm, ['paint', 'machined_steel', 'ceramic', 'brass']


def ecm_cell(fine):
    """An electrochemical machining cell 0.7 m wide standing on z = 0: the closed work chamber with its window, the
    cathode's feed above it and the electrolyte pipes and filter at the back."""
    bm = bmesh.new()
    box(bm, (-0.35, -0.25, 0), (0.35, 0.25, 0.5), 0)
    box(bm, (-0.25, 0.25, 0.12), (0.25, 0.258, 0.4), 1)
    zcyl(bm, 0.5, 0.75, 0.06, 12 if fine else 6, 2)
    box(bm, (-0.12, -0.12, 0.75), (0.12, 0.12, 0.9), 0)
    tube_between(bm, (-0.25, -0.25, 0.3), (-0.25, -0.4, 0.3), 0.03, 10 if fine else 6, 3)
    tube_between(bm, (0.25, -0.25, 0.1), (0.25, -0.4, 0.1), 0.03, 10 if fine else 6, 3)
    if fine:
        zcyl(bm, 0.0, 0.45, 0.08, 14, 3, x=0.0, y=-0.45)
        tube_between(bm, (-0.25, -0.4, 0.3), (0.0, -0.4, 0.3), 0.03, 10, 3)
    return bm, ['paint', 'glass', 'stainless', 'plastic']


# ------------------------------------------------------------------------------------------------ cutting table

def gantry_beam(fine):
    """A cutting table's gantry 2.2 m across y, travelling along x on rails at z = 0: the end carriages with their
    drives and the box beam between them at z = 0.45 to 0.65; the head's carriage runs along its front (y faces +x)."""
    bm = bmesh.new()
    for y in (-1.05, 1.05):
        rounded_box(bm, (-0.18, y - 0.08, 0), (0.18, y + 0.08, 0.65), 0.02, 0, 2 if fine else 0)
        if fine:
            ycyl(bm, y + (0.08 if y > 0 else -0.14), y + (0.14 if y > 0 else -0.08), 0.06, 12, 1, x=0, z=0.35)
    box(bm, (-0.1, -1.1, 0.45), (0.1, 1.1, 0.65), 0)
    if fine:
        box(bm, (0.1, -1.0, 0.6), (0.115, 1.0, 0.62), 2)
        box(bm, (0.1, -1.0, 0.48), (0.115, 1.0, 0.5), 2)
        tube_between(bm, (-0.1, -1.0, 0.67), (-0.1, 1.0, 0.67), 0.02, 6, 3)
    return bm, ['paint', 'cast_iron', 'machined_steel', 'plastic']


def laser_head(fine):
    """A fibre laser's cutting head 0.4 m tall standing on z = 0 at its carriage: the Z slide, the head tube with its
    lens housing and the copper nozzle at the bottom (its tip at (0, 0.1, -0.12))."""
    bm = bmesh.new()
    box(bm, (-0.08, -0.04, 0.0), (0.08, 0.04, 0.4), 0)
    zcyl(bm, -0.05, 0.3, 0.035, 14 if fine else 8, 1, y=0.1)
    zcyl(bm, -0.1, -0.05, 0.03, 14 if fine else 8, 1, y=0.1, r1=0.035)
    zcyl(bm, -0.12, -0.1, 0.012, 10 if fine else 6, 2, y=0.1, r1=0.02)
    if fine:
        tube_between(bm, (0, 0.1, 0.3), (0, 0.1, 0.45), 0.01, 8, 3)
        box(bm, (-0.045, 0.05, 0.12), (0.045, 0.14, 0.2), 1)
    return bm, ['paint', 'stainless', 'copper', 'braided_hose']


def plasma_torch(fine):
    """A plasma torch 0.35 m tall standing on z = 0 at its lifter: the lifter, the torch body and shield cup (its tip
    at (0, 0.1, -0.1)), the lead going up."""
    bm = bmesh.new()
    box(bm, (-0.06, -0.04, 0.0), (0.06, 0.04, 0.3), 0)
    zcyl(bm, -0.06, 0.2, 0.025, 12 if fine else 6, 1, y=0.1)
    zcyl(bm, -0.1, -0.06, 0.015, 12 if fine else 6, 2, y=0.1, r1=0.025)
    tube_between(bm, (0, 0.1, 0.2), (0, 0.1, 0.4), 0.018, 8 if fine else 5, 3)
    return bm, ['paint', 'plastic', 'copper', 'rubber']


def waterjet_head(fine):
    """An abrasive waterjet's cutting head 0.45 m tall standing on z = 0: the on/off valve, the high pressure coil, the
    mixing chamber with the abrasive feed and the carbide focusing tube (its tip at (0, 0.1, -0.14))."""
    bm = bmesh.new()
    box(bm, (-0.06, -0.04, 0.0), (0.06, 0.04, 0.35), 0)
    zcyl(bm, 0.2, 0.3, 0.03, 12 if fine else 6, 1, y=0.1)
    zcyl(bm, -0.04, 0.2, 0.012, 10 if fine else 6, 1, y=0.1)
    zcyl(bm, -0.06, -0.04, 0.022, 12 if fine else 6, 1, y=0.1)
    zcyl(bm, -0.14, -0.06, 0.005, 8, 2, y=0.1)
    tube_between(bm, (0, 0.1, -0.05), (0.12, 0.1, 0.1), 0.006, 6, 3)
    zcyl(bm, 0.1, 0.25, 0.04, 10 if fine else 6, 3, x=0.12, y=0.1, r1=0.06)
    if fine:
        torus(bm, 0.05, 0.005, (0, 0.1, 0.42), 16, 6, 1, axis='z')
        torus(bm, 0.05, 0.005, (0, 0.1, 0.44), 16, 6, 1, axis='z')
    return bm, ['paint', 'stainless', 'ceramic', 'plastic']


def fume_hood(fine):
    """A cutting table's fume extraction 0.8 m long standing on z = 0 beside the table: the duct rising and the hood."""
    bm = bmesh.new()
    box(bm, (-0.4, -0.1, 0), (0.4, 0.1, 0.3), 0)
    ycyl(bm, -0.3, -0.1, 0.1, 14 if fine else 6, 1, x=0.0, z=0.15)
    zcyl(bm, 0.0, 0.9, 0.1, 14 if fine else 6, 1, y=-0.3)
    if fine:
        for z in (0.3, 0.6):
            torus(bm, 0.1, 0.008, (0, -0.3, z), 16, 4, 1, axis='z')
    return bm, ['paint', 'stainless']


# ------------------------------------------------------------------------------------------------ threader

def thread_rolling_head(fine):
    """A two-die thread rolling machine's head 0.6 m wide standing on z = 0: the frame and the two die slides with the
    rolls' places (axes along x at y = -0.11 and +0.11, z = 0.4; the rolls are thread_roll)."""
    bm = bmesh.new()
    rounded_box(bm, (-0.3, -0.35, 0), (0.3, 0.35, 0.3), 0.02, 0, 2 if fine else 0)
    for y in (-0.25, 0.25):
        box(bm, (-0.12, y - 0.07, 0.3), (0.12, y + 0.07, 0.55), 0)
    for y in (-0.11, 0.11):
        box(bm, (-0.16, y - 0.02, 0.32), (-0.12, y + 0.02, 0.48), 1)
        box(bm, (0.12, y - 0.02, 0.32), (0.16, y + 0.02, 0.48), 1)
    if fine:
        ycyl(bm, -0.42, -0.35, 0.07, 14, 2, z=0.2)
        box(bm, (-0.05, -0.02, 0.3), (0.05, 0.02, 0.36), 1)
    return bm, ['paint', 'machined_steel', 'cast_iron']


def thread_roll(fine):
    """A thread rolling die 180 mm across and 120 mm long, turning about x, ridged at a 2 mm pitch."""
    bm = bmesh.new()
    xcyl(bm, -0.06, 0.06, 0.088, 24 if fine else 10, 0)
    if fine:
        ridges(bm, -0.06, 0.06, 0.088, 0.002, 0.008, 24, 0)
    xcyl(bm, -0.075, 0.075, 0.025, 10 if fine else 6, 1)
    return bm, ['machined_steel', 'forged_steel']


def flat_die(fine):
    """A flat thread rolling die 0.25 m long, 60 mm tall on z = 0, grooved on its +y face (the moving die is a copy)."""
    bm = bmesh.new()
    box(bm, (-0.125, -0.03, 0), (0.125, 0.03, 0.06), 0)
    if fine:
        for k in range(12):
            z = 0.004 + k * 0.0045
            box(bm, (-0.125, 0.03, z), (0.125, 0.032, z + 0.002), 0)
    return bm, ['machined_steel']


def tapping_arm(fine):
    """An articulated tapping arm 1 m long from its pillar on z = 0: two parallel links reaching toward +y and the
    tapping head hanging at the end (its tap at (0, 0.95, 0.4))."""
    bm = bmesh.new()
    s = 12 if fine else 6
    zcyl(bm, 0, 0.05, 0.12, s, 0)
    zcyl(bm, 0.05, 0.75, 0.04, s, 0)
    tube_between(bm, (0, 0, 0.75), (0, 0.5, 0.8), 0.025, s, 0)
    tube_between(bm, (0, 0.5, 0.8), (0, 0.95, 0.75), 0.025, s, 0)
    zcyl(bm, 0.55, 0.75, 0.05, s, 1, y=0.95)
    zcyl(bm, 0.45, 0.55, 0.02, s, 2, y=0.95)
    zcyl(bm, 0.4, 0.45, 0.005, 6, 2, y=0.95)
    if fine:
        zcyl(bm, 0.75, 0.8, 0.06, s, 1)
        zcyl(bm, 0.75, 0.82, 0.04, s, 1, y=0.5)
    return bm, ['paint', 'machined_steel', 'forged_steel']


# ------------------------------------------------------------------------------------------------ riveter

def c_frame_riveter(fine):
    """A squeeze riveter's C-frame 0.9 m tall standing on z = 0, its throat opening toward +y: the frame, the hydraulic
    cylinder at the top with the ram's place (0, 0.45, 0.62) and the anvil below at (0, 0.45, 0.32)."""
    bm = bmesh.new()
    box(bm, (-0.08, -0.15, 0), (0.08, 0.05, 0.9), 0)
    box(bm, (-0.08, 0.05, 0.7), (0.08, 0.55, 0.85), 0)
    box(bm, (-0.08, 0.05, 0.12), (0.08, 0.55, 0.3), 0)
    zcyl(bm, 0.85, 1.0, 0.07, 14 if fine else 6, 1, y=0.45)
    zcyl(bm, 0.3, 0.33, 0.02, 10 if fine else 6, 2, y=0.45)
    if fine:
        tube_between(bm, (0.07, 0.45, 0.95), (0.07, -0.1, 0.95), 0.008, 6, 3)
        tube_between(bm, (0.07, -0.1, 0.95), (0.07, -0.1, 0.2), 0.008, 6, 3)
    return bm, ['paint', 'machined_steel', 'forged_steel', 'braided_hose']


def rivet_ram(fine):
    """A squeeze riveter's ram 0.1 m long hanging from z = 0 with its rivet set, stroking along z."""
    bm = bmesh.new()
    zcyl(bm, -0.08, 0, 0.03, 12 if fine else 6, 0)
    zcyl(bm, -0.1, -0.08, 0.012, 10 if fine else 6, 1)
    return bm, ['machined_steel', 'forged_steel']


def rivet_gun(fine):
    """A pneumatic rivet hammer 0.3 m long on its balancer: the body along y (the set at +y), the grip and trigger
    below it, the air hose, the balancer's cable going up from z = 0.2."""
    bm = bmesh.new()
    ycyl(bm, -0.12, 0.1, 0.03, 12 if fine else 6, 0, z=0.1)
    ycyl(bm, 0.1, 0.16, 0.012, 8, 1, z=0.1)
    tube_between(bm, (0, -0.1, 0.07), (0, -0.14, -0.02), 0.018, 8 if fine else 5, 2)
    tube_between(bm, (0, -0.14, -0.02), (0, -0.25, -0.05), 0.008, 6, 3)
    zcyl(bm, 0.13, 0.5, 0.002, 4, 1, y=0.0)
    return bm, ['paint', 'machined_steel', 'rubber', 'braided_hose']


def drill_rivet_head(fine):
    """An automatic drilling and riveting head 0.6 m tall standing on z = 0: the head with the drill spindle and the
    riveting ram side by side over the work at (0, 0.2, -0.05), the rivet feed hopper and tube."""
    bm = bmesh.new()
    rounded_box(bm, (-0.2, -0.12, 0.0), (0.2, 0.12, 0.45), 0.02, 0, 2 if fine else 0)
    zcyl(bm, -0.05, 0.1, 0.03, 12 if fine else 6, 1, x=-0.07, y=0.2)
    zcyl(bm, -0.05, 0.1, 0.03, 12 if fine else 6, 1, x=0.07, y=0.2)
    box(bm, (-0.12, 0.12, 0.1), (0.12, 0.25, 0.3), 0)
    zcyl(bm, 0.45, 0.6, 0.09, 14 if fine else 6, 2, x=0.1, y=-0.02, r1=0.12)
    tube_between(bm, (0.1, -0.02, 0.45), (0.07, 0.2, 0.1), 0.008, 6, 3)
    return bm, ['paint', 'machined_steel', 'plastic', 'braided_hose']


PARTS = {
    'control_pendant': control_pendant, 'nc_cabinet': nc_cabinet, 'motor': motor, 'coolant_nozzle': coolant_nozzle, 'guard_window': guard_window,
    'wheelhead': wheelhead, 'grinding_wheel': grinding_wheel, 'regulating_wheel': regulating_wheel, 'centreless_frame': centreless_frame,
    'internal_spindle': internal_spindle, 'magnetic_chuck': magnetic_chuck, 'centre_stock': centre_stock, 'diamond_dresser': diamond_dresser,
    'tool_grinder_head': tool_grinder_head,
    'honing_head': honing_head, 'honing_tool': honing_tool, 'lapping_plate': lapping_plate, 'lap_ring': lap_ring, 'polishing_head': polishing_head,
    'buff_wheel': buff_wheel, 'vibratory_bowl': vibratory_bowl,
    'hob_slide': hob_slide, 'hob': hob, 'shaper_cutter': shaper_cutter, 'gear_work': gear_work,
    'shaper_ram': shaper_ram, 'slotter_ram': slotter_ram, 'broach': broach,
    'band_saw_bow': band_saw_bow, 'circular_saw_blade': circular_saw_blade, 'cold_saw_head': cold_saw_head, 'hacksaw_frame': hacksaw_frame,
    'bar_vise': bar_vise,
    'edm_quill': edm_quill, 'edm_ram': edm_ram, 'wire_head': wire_head, 'hole_edm_head': hole_edm_head, 'ecm_cell': ecm_cell,
    'gantry_beam': gantry_beam, 'laser_head': laser_head, 'plasma_torch': plasma_torch, 'waterjet_head': waterjet_head, 'fume_hood': fume_hood,
    'thread_rolling_head': thread_rolling_head, 'thread_roll': thread_roll, 'flat_die': flat_die, 'tapping_arm': tapping_arm,
    'c_frame_riveter': c_frame_riveter, 'rivet_ram': rivet_ram, 'rivet_gun': rivet_gun, 'drill_rivet_head': drill_rivet_head,
}


if __name__ == '__main__':
    details.export(PARTS, OUT, 'machine')
