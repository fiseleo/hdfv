"""Models hdfv 0.20's airfield kit and exports each part as OBJ + MTL (lod0 and lod1 objects) into
assets/hdfv/models/part/airfield: the detailed parts the control tower's and windsock's renderers place (client/model
AirfieldKit), while the code draws what stretches with the tower's height (the base, the shaft, the cab's floor,
glazing and roof).

Conventions (as machining_kit.py): Blender z is the game's y (up), +y faces the tower's front (toward the runway),
sizes are real (metres). Parts stand on z = 0, those that turn about the vertical centred on their axis; the windsock
head hangs its hoop below its swivel (named in the test). Materials are finish ids (finishes_spec.json); 'paint' is
tinted by the code.
Run: scripts/blender.ps1 airfield_kit"""
import math
import os
import sys

import bmesh
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import details  # noqa: E402
from details import box, torus  # noqa: E402
from kitshapes import dome, new_faces, rounded_box, transform, tube_between  # noqa: E402
from machining_kit import xcyl, ycyl, zcyl  # noqa: E402

OUT = os.path.join(common.ASSETS, 'models', 'part', 'airfield')


def turned(bm, before, angle_deg, pivot=(0, 0, 0)):
    """The faces made since before turned about the vertical through a pivot."""
    p = Vector(pivot)
    transform(bm, new_faces(bm, before), Matrix.Translation(p) @ Matrix.Rotation(math.radians(angle_deg), 4, 'Z') @ Matrix.Translation(-p))


def chair(bm, x, y, fine, mats):
    """An office chair facing +y: a five-star base on castors, the gas lift, the seat and back."""
    s = 10 if fine else 5
    for k in range(5 if fine else 3):
        a = 2 * math.pi * k / (5 if fine else 3)
        tube_between(bm, (x, y, 0.08), (x + 0.3 * math.cos(a), y + 0.3 * math.sin(a), 0.05), 0.02, 4, mats['plastic'])
        if fine:
            zcyl(bm, 0, 0.05, 0.03, 6, mats['rubber'], x=x + 0.3 * math.cos(a), y=y + 0.3 * math.sin(a))
    zcyl(bm, 0.05, 0.42, 0.025, s, mats['chrome'], x=x, y=y)
    rounded_box(bm, (x - 0.24, y - 0.24, 0.42), (x + 0.24, y + 0.22, 0.5), 0.03, mats['fabric'], 1 if fine else 0)
    rounded_box(bm, (x - 0.22, y - 0.3, 0.55), (x + 0.22, y - 0.24, 1.0), 0.03, mats['fabric'], 1 if fine else 0)
    box(bm, (x - 0.03, y - 0.27, 0.45), (x + 0.03, y - 0.24, 0.6), mats['plastic'])


def cab_console(fine):
    """The cab's control desk under the front windows (2.6 m wide, its working top 0.76 m up, the controllers on its -y
    side facing +y): a straight middle and two wings turned 30 degrees, a radar display and the strip board set in the
    top, four monitors on a rail, a radio panel; two chairs."""
    bm = bmesh.new()
    names = ['dash_plastic', 'plastic', 'glass', 'chrome', 'fabric', 'rubber', 'brushed_steel']
    mats = {n: i for i, n in enumerate(names)}
    for side in (-1, 0, 1):
        before = set(bm.faces)
        w = 1.4 if side == 0 else 0.7
        box(bm, (-w / 2, -0.35, 0), (w / 2, 0.35, 0.72), mats['dash_plastic'])
        rounded_box(bm, (-w / 2 - 0.01, -0.4, 0.72), (w / 2 + 0.01, 0.38, 0.76), 0.012, mats['plastic'], 1 if fine else 0)
        if side == 0:
            # the radar display (a big flat screen let into the top) and the strip board
            box(bm, (-0.3, -0.25, 0.76), (0.3, 0.15, 0.8), mats['plastic'])
            box(bm, (-0.27, -0.22, 0.8), (0.27, 0.12, 0.805), mats['glass'])
            for k in range(4 if fine else 0):
                box(bm, (0.36, -0.3 + 0.07 * k, 0.76), (0.62, -0.25 + 0.07 * k, 0.775), mats['chrome'])
        # the monitor rail and its monitors
        box(bm, (-w / 2 + 0.05, 0.22, 0.76), (w / 2 - 0.05, 0.3, 0.84), mats['brushed_steel'])
        count = 2 if side == 0 else 1
        for k in range(count):
            cx = (-0.32 + 0.64 * k) if side == 0 else 0.0
            zcyl(bm, 0.84, 0.98, 0.02, 8 if fine else 4, mats['brushed_steel'], x=cx, y=0.26)
            box(bm, (cx - 0.27, 0.22, 0.95), (cx + 0.27, 0.27, 1.27), mats['plastic'])
            if fine:
                box(bm, (cx - 0.25, 0.215, 0.97), (cx + 0.25, 0.22, 1.25), mats['glass'])
        if side != 0:
            # the radio panel on each wing: a row of knobs
            box(bm, (-0.3, -0.36, 0.45), (0.3, -0.35, 0.68), mats['plastic'])
            for k in range(6 if fine else 0):
                ycyl(bm, -0.37, -0.36, 0.015, 8, mats['chrome'], x=-0.22 + 0.088 * k, z=0.6)
            # turned and moved to its place: the wing's inner end meets the middle's end
            turned(bm, before, -30 * side)
            transform(bm, new_faces(bm, before), Matrix.Translation(Vector((side * (0.7 + 0.35 * math.cos(math.radians(30))), -0.35 * math.sin(math.radians(30)), 0))))
    for x in (-0.45, 0.45):
        chair(bm, x, -0.85, fine, mats)
    return bm, names


def radar_antenna(fine):
    """A surveillance radar's antenna turning about z (its rotary joint on z = 0): a shallow curved reflector 4 m wide
    and 1.3 m high facing +y on a back frame, the feed horn on its arm in front."""
    bm = bmesh.new()
    names = ['paint', 'machined_steel', 'plastic']
    zcyl(bm, 0, 0.28, 0.18, 16 if fine else 8, 1)
    zcyl(bm, 0.28, 0.4, 0.26, 16 if fine else 8, 1)
    # the reflector: a cylindrical sheet bent about z (radius 4 m) with its edge rolled, made of quads
    cols, rows = (16, 4) if fine else (6, 2)
    r, half, z0, z1, depth = 4.0, 2.0, 0.55, 1.85, 0.05
    verts = []
    for side in (0, 1):
        layer = []
        for j in range(rows + 1):
            row = []
            z = z0 + (z1 - z0) * j / rows
            # a little lean back: the beam lifts a few degrees
            lean = (z - z0) * 0.12
            for i in range(cols + 1):
                x = -half + 2 * half * i / cols
                y = r - math.sqrt(r * r - x * x) - lean - depth * side
                row.append(bm.verts.new((x, 0.35 + y, z)))
            layer.append(row)
        verts.append(layer)
    for j in range(rows):
        for i in range(cols):
            f = bm.faces.new((verts[0][j][i], verts[0][j][i + 1], verts[0][j + 1][i + 1], verts[0][j + 1][i]))
            f.material_index = 0
            g = bm.faces.new((verts[1][j][i], verts[1][j + 1][i], verts[1][j + 1][i + 1], verts[1][j][i + 1]))
            g.material_index = 0
    for j in range(rows):
        for i in (0, cols):
            f = bm.faces.new((verts[0][j][i], verts[0][j + 1][i], verts[1][j + 1][i], verts[1][j][i]) if i == 0 else
                             (verts[0][j][i], verts[1][j][i], verts[1][j + 1][i], verts[0][j + 1][i]))
            f.material_index = 0
    for i in range(cols):
        for j, flip in ((0, False), (rows, True)):
            q = (verts[0][j][i], verts[1][j][i], verts[1][j][i + 1], verts[0][j][i + 1])
            f = bm.faces.new(q if not flip else tuple(reversed(q)))
            f.material_index = 0
    # the back frame: a beam along the back and struts down to the joint
    box(bm, (-1.9, 0.2, 1.1), (1.9, 0.3, 1.25), 1)
    for x in (-1.2, 1.2):
        tube_between(bm, (0, 0, 0.4), (x, 0.25, 1.15), 0.04, 6 if fine else 4, 1)
    tube_between(bm, (0, 0, 0.4), (0, 0.25, 1.15), 0.05, 6 if fine else 4, 1)
    # the feed horn on its arm out in front
    tube_between(bm, (0, 0.3, 0.6), (0, 1.5, 0.9), 0.04, 6 if fine else 4, 1)
    box(bm, (-0.12, 1.45, 0.85), (0.12, 1.62, 1.08), 2)
    return bm, names


def radar_pedestal(fine):
    """The antenna's pedestal (its top, where the antenna's joint sits, 0.9 m up): a square plinth, the drive housing
    with its motor and gearbox, a guard rail round a small platform."""
    bm = bmesh.new()
    names = ['paint', 'machined_steel', 'brushed_steel']
    box(bm, (-0.6, -0.6, 0), (0.6, 0.6, 0.15), 2)
    zcyl(bm, 0.15, 0.75, 0.42, 16 if fine else 8, 0)
    zcyl(bm, 0.75, 0.9, 0.3, 16 if fine else 8, 1)
    box(bm, (0.35, -0.2, 0.25), (0.75, 0.2, 0.6), 0)
    xcyl(bm, 0.75, 1.0, 0.13, 12 if fine else 6, 1, y=0, z=0.42)
    if fine:
        for x, y in ((-0.55, -0.55), (0.55, -0.55), (-0.55, 0.55), (0.55, 0.55)):
            zcyl(bm, 0.15, 1.0, 0.02, 6, 2, x=x, y=y)
        for z in (0.55, 1.0):
            for a, b in (((-0.55, -0.55), (0.55, -0.55)), ((0.55, -0.55), (0.55, 0.55)), ((0.55, 0.55), (-0.55, 0.55)), ((-0.55, 0.55), (-0.55, -0.55))):
                tube_between(bm, (a[0], a[1], z), (b[0], b[1], z), 0.018, 6, 2)
    return bm, names


def anemometer_mast(fine):
    """The roof mast: a galvanised tube 4 m high, two cross arms (the anemometer's at 3.4 m on +x, the wind vane's on
    -x), a red obstruction light on its top and a lightning rod."""
    bm = bmesh.new()
    names = ['brushed_steel', 'machined_steel', 'lamp_red', 'paint']
    s = 12 if fine else 6
    box(bm, (-0.25, -0.25, 0), (0.25, 0.25, 0.05), 1)
    zcyl(bm, 0.05, 4.0, 0.05, s, 0)
    tube_between(bm, (-0.6, 0, 3.4), (0.6, 0, 3.4), 0.025, 6 if fine else 4, 0)
    zcyl(bm, 3.4, 3.55, 0.02, 6, 1, x=0.6)
    # the vane on its arm's end
    zcyl(bm, 3.4, 3.6, 0.02, 6, 1, x=-0.6)
    box(bm, (-0.62, -0.25, 3.58), (-0.58, 0.05, 3.72), 3)
    tube_between(bm, (-0.6, 0.05, 3.62), (-0.6, 0.3, 3.62), 0.012, 4, 1)
    # the light and the rod
    zcyl(bm, 4.0, 4.08, 0.09, s, 1)
    dome(bm, 0.08, 0.14, 4.08, 12 if fine else 6, 3 if fine else 1, 2)
    zcyl(bm, 4.0, 4.6, 0.008, 4, 1, x=0.07)
    return bm, names


def anemometer_cups(fine):
    """The anemometer's three cups on their spider, turning about z (the hub on z = 0)."""
    bm = bmesh.new()
    names = ['plastic', 'machined_steel']
    zcyl(bm, 0, 0.06, 0.025, 8 if fine else 4, 1)
    for k in range(3):
        a = 2 * math.pi * k / 3
        cx, cy = 0.14 * math.cos(a), 0.14 * math.sin(a)
        tube_between(bm, (0, 0, 0.05), (cx, cy, 0.05), 0.005, 4, 1)
        before = set(bm.faces)
        dome(bm, 0.035, 0.035, 0, 10 if fine else 5, 2 if fine else 1, 0)
        transform(bm, new_faces(bm, before), Matrix.Translation(Vector((cx, cy, 0.05))) @ Matrix.Rotation(a + math.pi / 2, 4, 'Z') @ Matrix.Rotation(math.radians(90), 4, 'X'))
    return bm, names


def entrance_canopy(fine):
    """The canopy over the tower's door: a slab 3.2 m wide reaching 1.8 m out (+y) from the wall (y = 0), its underside
    on z = 0, a steel fascia, two tie rods back to the wall, two downlights."""
    bm = bmesh.new()
    names = ['paint', 'brushed_steel', 'lamp_clear']
    box(bm, (-1.6, 0, 0), (1.6, 1.8, 0.18), 0)
    box(bm, (-1.62, 1.78, -0.04), (1.62, 1.84, 0.24), 1)
    for x in (-1.3, 1.3):
        tube_between(bm, (x, 1.7, 0.2), (x, 0.02, 1.3), 0.02, 6 if fine else 4, 1)
        box(bm, (x - 0.06, 0, 1.24), (x + 0.06, 0.03, 1.38), 1)
    for x in (-0.7, 0.7):
        zcyl(bm, -0.012, 0, 0.09, 12 if fine else 6, 2, x=x, y=1.0)
    return bm, names


def railing(fine):
    """A metre of the cab's catwalk railing along x from x = 0: a post at x = 0, the top rail at 1.1 m, a middle rail, a
    kick plate."""
    bm = bmesh.new()
    names = ['paint', 'stainless']
    box(bm, (-0.025, -0.025, 0), (0.025, 0.025, 1.1), 0)
    tube_between(bm, (0, 0, 1.1), (1, 0, 1.1), 0.022, 8 if fine else 4, 1)
    tube_between(bm, (0, 0, 0.55), (1, 0, 0.55), 0.012, 6 if fine else 4, 0)
    box(bm, (0, -0.005, 0), (1, 0.005, 0.12), 0)
    return bm, names


def windsock_head(fine):
    """The windsock's head on its mast's top (z = 0): the swivel, an arm out along +x, the hoop the sock hangs from
    (radius 0.36 m, its centre 0.3 m out and 0.12 m down, facing +x), a small counter-vane on -x."""
    bm = bmesh.new()
    names = ['brushed_steel', 'machined_steel', 'paint']
    zcyl(bm, 0, 0.16, 0.045, 10 if fine else 5, 1)
    tube_between(bm, (0, 0, 0.12), (0.3, 0, 0.24), 0.018, 6 if fine else 4, 0)
    torus(bm, 0.36, 0.014, (0.3, 0, -0.12), 24 if fine else 10, 6 if fine else 3, 0, axis='x')
    tube_between(bm, (0, 0, 0.12), (-0.25, 0, 0.12), 0.012, 4, 0)
    box(bm, (-0.38, -0.004, 0.04), (-0.22, 0.004, 0.2), 2)
    return bm, names


# ---------------------------------------------------------------- 0.21: inside the tower

def monitor(bm, cx, y, fine, mats):
    """A monitor on a stand on a desk top at 0.76 m, its screen facing -y (toward who sits at the desk)."""
    zcyl(bm, 0.76, 0.96, 0.02, 8 if fine else 4, mats['brushed_steel'], x=cx, y=y)
    box(bm, (cx - 0.08, y - 0.06, 0.76), (cx + 0.08, y + 0.06, 0.775), mats['brushed_steel'])
    box(bm, (cx - 0.27, y - 0.03, 0.93), (cx + 0.27, y + 0.02, 1.25), mats['plastic'])
    if fine:
        box(bm, (cx - 0.25, y - 0.035, 0.95), (cx + 0.25, y - 0.03, 1.23), mats['glass'])


def cab_console_side(fine):
    """A straight 2 m console for the cab's sides (its working top 0.76 m up, who works at it on its -y side facing +y,
    the windows): two monitors, a keyboard each, a radio panel of knobs and a handset, one chair."""
    bm = bmesh.new()
    names = ['dash_plastic', 'plastic', 'glass', 'chrome', 'fabric', 'rubber', 'brushed_steel']
    mats = {n: i for i, n in enumerate(names)}
    box(bm, (-1.0, -0.33, 0), (1.0, 0.33, 0.72), mats['dash_plastic'])
    rounded_box(bm, (-1.01, -0.38, 0.72), (1.01, 0.36, 0.76), 0.012, mats['plastic'], 1 if fine else 0)
    box(bm, (-0.95, 0.2, 0.76), (0.95, 0.3, 0.8), mats['brushed_steel'])
    for cx in (-0.48, 0.48):
        monitor(bm, cx, 0.24, fine, mats)
        box(bm, (cx - 0.22, -0.22, 0.76), (cx + 0.22, -0.08, 0.78), mats['plastic'])   # a keyboard
    # the radio panel at the front edge, its knobs and a handset in its cradle
    box(bm, (-0.3, -0.36, 0.45), (0.3, -0.33, 0.68), mats['plastic'])
    for k in range(6 if fine else 0):
        ycyl(bm, -0.37, -0.36, 0.015, 8, mats['chrome'], x=-0.22 + 0.088 * k, z=0.6)
    rounded_box(bm, (0.7, -0.3, 0.76), (0.92, -0.2, 0.81), 0.015, mats['rubber'], 1 if fine else 0)
    chair(bm, 0.0, -0.85, fine, mats)
    return bm, names


def supervisor_desk(fine):
    """The watch supervisor's desk at the back of the cab: 1.5 m, a monitor and a telephone, a chair on its -y side."""
    bm = bmesh.new()
    names = ['dash_plastic', 'plastic', 'glass', 'chrome', 'fabric', 'rubber', 'brushed_steel']
    mats = {n: i for i, n in enumerate(names)}
    rounded_box(bm, (-0.75, -0.38, 0.72), (0.75, 0.38, 0.76), 0.012, mats['plastic'], 1 if fine else 0)
    for x in (-0.7, 0.7):
        box(bm, (x - 0.04, -0.34, 0), (x + 0.04, 0.34, 0.72), mats['dash_plastic'])
    box(bm, (0.25, -0.34, 0), (0.66, 0.34, 0.6), mats['dash_plastic'])   # a drawer pedestal
    box(bm, (-0.66, 0.3, 0.2), (0.66, 0.34, 0.72), mats['dash_plastic'])  # the modesty panel
    monitor(bm, -0.15, 0.2, fine, mats)
    rounded_box(bm, (0.35, -0.05, 0.76), (0.6, 0.15, 0.82), 0.02, mats['plastic'], 1 if fine else 0)   # the telephone
    chair(bm, -0.1, -0.75, fine, mats)
    return bm, names


def ceiling_light(fine):
    """A round downlight let into the ceiling: its trim on z = 0, hanging down (named in the test), the lens beneath."""
    bm = bmesh.new()
    names = ['chrome', 'lamp_clear']
    s = 16 if fine else 8
    zcyl(bm, -0.012, 0.0, 0.15, s, 0)
    zcyl(bm, -0.018, -0.012, 0.115, s, 1)
    return bm, names


def distribution_board(fine):
    """The base's electrical distribution board hung on a wall (its back on y = 0, its door facing +y; standing on z = 0
    where it is hung): a steel box with a door, a window over rows of breakers, a label, conduits up out of its top."""
    bm = bmesh.new()
    names = ['paint', 'brushed_steel', 'glass', 'plastic', 'machined_steel', 'lamp_red']
    mats = {n: i for i, n in enumerate(names)}
    box(bm, (-0.6, 0.0, 0.0), (0.6, 0.2, 0.7), mats['paint'])
    box(bm, (-0.58, 0.2, 0.02), (0.58, 0.205, 0.68), mats['paint'])   # the door
    box(bm, (-0.45, 0.205, 0.32), (0.45, 0.21, 0.6), mats['glass'])
    for row in range(3 if fine else 0):
        for k in range(12):
            box(bm, (-0.42 + 0.07 * k, 0.19, 0.36 + 0.08 * row), (-0.37 + 0.07 * k, 0.2, 0.42 + 0.08 * row), mats['plastic'])
    box(bm, (-0.2, 0.205, 0.12), (0.2, 0.21, 0.2), mats['brushed_steel'])   # the label plate
    if fine:
        ycyl(bm, 0.205, 0.22, 0.012, 8, mats['lamp_red'], x=0.5, z=0.65)   # a pilot lamp
        ycyl(bm, 0.205, 0.225, 0.02, 8, mats['plastic'], x=0.52, z=0.35)   # the door's handle
    for x in (-0.4, -0.1, 0.25):
        zcyl(bm, 0.7, 1.0, 0.025, 8 if fine else 4, mats['machined_steel'], x=x, y=0.1)
    return bm, names


def cable_tray(fine):
    """A metre of ladder cable tray on wall brackets (the wall at y = 0, the tray out to 0.3 m), three cables in it."""
    bm = bmesh.new()
    names = ['machined_steel', 'rubber']
    for x in (-0.45, 0.45):
        box(bm, (x - 0.02, 0.0, 0.0), (x + 0.02, 0.31, 0.01), 0)   # the brackets
    for y in (0.02, 0.3):
        box(bm, (-0.5, y - 0.01, 0.01), (0.5, y + 0.01, 0.07), 0)
    for x in [-0.375, -0.125, 0.125, 0.375] if fine else [-0.25, 0.25]:
        box(bm, (x - 0.012, 0.02, 0.01), (x + 0.012, 0.3, 0.022), 0)
    for k in range(3):
        xcyl(bm, -0.5, 0.5, 0.018, 8 if fine else 4, 1, y=0.08 + 0.08 * k, z=0.04)
    return bm, names


def notice_board(fine):
    """A cork notice board on a wall (its back on y = 0, facing +y): an aluminium frame, a few papers pinned up."""
    bm = bmesh.new()
    names = ['brushed_steel', 'leather', 'paint_matte', 'plastic']
    box(bm, (-0.45, 0.0, 0.0), (0.45, 0.02, 0.6), 0)
    box(bm, (-0.43, 0.02, 0.02), (0.43, 0.025, 0.58), 1)
    papers = [(-0.35, 0.3, 0.2, 0.25), (-0.1, 0.35, 0.22, 0.18), (0.18, 0.12, 0.2, 0.28), (-0.32, 0.05, 0.25, 0.2)]
    for x, z, w, h in papers if fine else papers[:2]:
        box(bm, (x, 0.025, z), (x + w, 0.028, z + h), 2)
        if fine:
            ycyl(bm, 0.028, 0.034, 0.008, 6, 3, x=x + w / 2, z=z + h - 0.02)
    return bm, names


def wall_clock(fine):
    """A round wall clock (its back on y = 0, its face toward +y, its centre 0.17 m up)."""
    bm = bmesh.new()
    names = ['plastic', 'paint_matte']
    s = 20 if fine else 10
    box(bm, (-0.03, 0.0, 0.0), (0.03, 0.03, 0.02), 0)   # its hook's plate under it (the part stands on z = 0)
    ycyl(bm, 0.0, 0.04, 0.17, s, 0, z=0.17)
    ycyl(bm, 0.04, 0.042, 0.155, s, 1, z=0.17)
    box(bm, (-0.006, 0.042, 0.17), (0.006, 0.047, 0.29), 0)   # the minute hand
    box(bm, (0.0, 0.042, 0.164), (0.09, 0.047, 0.176), 0)     # the hour hand
    return bm, names


PARTS = {
    'cab_console': cab_console, 'radar_antenna': radar_antenna, 'radar_pedestal': radar_pedestal, 'anemometer_mast': anemometer_mast,
    'anemometer_cups': anemometer_cups, 'entrance_canopy': entrance_canopy, 'railing': railing, 'windsock_head': windsock_head,
    'cab_console_side': cab_console_side, 'supervisor_desk': supervisor_desk, 'ceiling_light': ceiling_light,
    'distribution_board': distribution_board, 'cable_tray': cable_tray, 'notice_board': notice_board, 'wall_clock': wall_clock,
}


if __name__ == '__main__':
    details.export(PARTS, OUT, 'airfield')
