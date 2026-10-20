"""Models hdfv's vehicle kit (hdfv 0.12) and exports each part as OBJ + MTL into assets/hdfv/models/part/vehicle: the
things a body carries outside, placed and scaled by the code on a design's sizes (VehicleKit in DetailKit, BodyModel):
lamps (round and projector headlamps, a tail lamp with its reversing light, a navigation light, a beacon), a door
mirror, a wiper, a door handle, a fuel flap, an antenna, a pitot tube, a cleat, a plate's frame, an exhaust tip, and a
damper (its body and its rod apart, so a suspension can telescope them).

Conventions (as details.py): Blender z is the game's y; a part sits on a surface at y = 0 and stands out along +y,
its length along x; sizes are real (metres) and the code scales by Part.size; lamp lenses are made of the lamp_*
finishes so the code can tag them as lamps. Run: scripts/blender.ps1 vehicle"""
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

OUT = os.path.join(common.ASSETS, 'models', 'part', 'vehicle')


def headlamp_round(fine):
    """A round headlamp 180 mm across: a chrome bezel round a domed clear lens, set into the body face."""
    bm = bmesh.new()
    s = 24 if fine else 10
    stack(bm, [(0, 0.012, 0.085, 0.085, s, 1)], seated=True)
    dome(bm, 0.085, 0.014, 0.012, s, 3 if fine else 1, 1)
    if fine:
        torus(bm, 0.088, 0.007, (0, 0, 0.012), s, 6, 0)
    return bm, ['chrome', 'lamp_clear']


def headlamp_projector(fine):
    """A modern headlamp unit 260 x 100 mm: a dark housing under a clear cover, a projector lens and a light strip."""
    bm = bmesh.new()
    prism(bm, rounded_rect(0.26, 0.10, 0.03, 4 if fine else 1), 0, 0.008, 0, caps=(False, True))
    prism(bm, rounded_rect(0.25, 0.09, 0.028, 4 if fine else 1), 0.008, 0.016, 2, caps=(False, True))
    if fine:
        dome(bm, 0.028, 0.01, 0.016, 16, 2, 1, offset=(0.06, 0.005))
        prism(bm, rounded_rect(0.18, 0.012, 0.006, 2), 0.016, 0.019, 1, offset=(-0.02, -0.032))
    return bm, ['dash_plastic', 'chrome', 'lamp_clear']


def tail_lamp(fine):
    """A tail lamp 280 x 100 mm: the red lens (tail and brake light) and a clear reversing light at its inner end."""
    bm = bmesh.new()
    corner = 3 if fine else 1
    prism(bm, rounded_rect(0.19, 0.10, 0.025, corner), 0, 0.014, 0, caps=(False, True), offset=(0.045, 0))
    prism(bm, rounded_rect(0.085, 0.10, 0.02, corner), 0, 0.014, 1, caps=(False, True), offset=(-0.0975, 0))
    return bm, ['lamp_red', 'lamp_clear']


def nav_light(fine):
    """A wingtip navigation light: a streamlined housing 120 mm long under a clear dome (the code colours the lens)."""
    bm = bmesh.new()
    s = 14 if fine else 6
    dome(bm, 0.04, 0.025, 0, s, 3 if fine else 1, 1, scale=(1.5, 1.0))
    prism(bm, [(-0.075, -0.035), (0.03, -0.035), (0.03, 0.035), (-0.075, 0.035)], 0, 0.006, 0, caps=(False, True))
    return bm, ['paint', 'lamp_clear']


def beacon(fine):
    """An anti-collision beacon: a base ring 120 mm across and a red dome 50 mm tall."""
    bm = bmesh.new()
    s = 16 if fine else 8
    stack(bm, [(0, 0.012, 0.06, 0.058, s, 0)])
    dome(bm, 0.05, 0.05, 0.012, s, 3 if fine else 1, 1)
    return bm, ['paint', 'lamp_red']


def door_mirror(fine):
    """A door mirror: a foot on the door, an arm out and the housing (220 x 120 mm) with its glass facing aft (-x)."""
    bm = bmesh.new()
    rounded_box(bm, (-0.05, -0.03, 0), (0.05, 0.03, 0.03), 0.012, 0, 2 if fine else 0)
    rounded_box(bm, (-0.035, -0.015, 0.02), (0.01, 0.015, 0.09), 0.01, 0, 2 if fine else 0)
    rounded_box(bm, (-0.02, -0.11, 0.06), (0.09, 0.11, 0.19), 0.035, 0, 3 if fine else 1)
    # the glass on the housing's aft face (x = -0.02), facing aft: the outline's long side across, its short side up
    before = set(bm.faces)
    prism(bm, rounded_rect(0.2, 0.11, 0.03, 3 if fine else 1), 0, 0.003, 1, caps=(False, True))
    transform(bm, new_faces(bm, before),
              Matrix.Translation(Vector((-0.02, 0, 0.125))) @ Matrix.Rotation(math.radians(-90), 4, 'Y') @ Matrix.Rotation(math.radians(90), 4, 'Z'))
    return bm, ['paint', 'chrome']


def wiper(fine):
    """A wiper 550 mm long lying on the glass: its pivot cap at the origin, the arm and the rubber blade along +x."""
    bm = bmesh.new()
    s = 12 if fine else 6
    stack(bm, [(0, 0.012, 0.014, 0.012, s, 0)])
    box(bm, (0, -0.006, 0.008), (0.45, 0.006, 0.014), 0)
    box(bm, (0.05, -0.004, 0.002), (0.55, 0.004, 0.009), 1)
    if fine:
        box(bm, (0.04, -0.007, 0.006), (0.56, 0.007, 0.010), 0)
    return bm, ['dash_plastic', 'rubber']


def door_handle(fine):
    """A pull handle 130 mm long on its base, standing 18 mm off the door."""
    bm = bmesh.new()
    rounded_box(bm, (-0.07, -0.018, 0), (0.07, 0.018, 0.004), 0.008, 1, 2 if fine else 0)
    rounded_box(bm, (-0.065, -0.012, 0.004), (0.065, 0.012, 0.018), 0.009, 0, 2 if fine else 0)
    return bm, ['chrome', 'dash_plastic']


def fuel_flap(fine):
    """A round fuel flap 100 mm across, a shut line round it (the code paints it the body's colour)."""
    bm = bmesh.new()
    stack(bm, [(0, 0.002, 0.05, 0.05, 24 if fine else 10, 0)])
    if fine:
        torus(bm, 0.052, 0.0015, (0, 0, 0.0005), 24, 4, 1)
    return bm, ['paint', 'dash_plastic']


def antenna(fine):
    """A whip antenna 600 mm tall on a rubber base."""
    bm = bmesh.new()
    s = 10 if fine else 5
    stack(bm, [(0, 0.03, 0.014, 0.008, s, 0), (0.03, 0.6, 0.003, 0.0015, 6 if fine else 4, 1)])
    return bm, ['rubber', 'dash_plastic']


def pitot(fine):
    """A pitot tube: a mast 80 mm tall and the tube 150 mm long pointing forward (+x) from its top."""
    bm = bmesh.new()
    prism(bm, [(-0.04, -0.006), (0.02, -0.006), (0.03, 0), (0.02, 0.006), (-0.04, 0.006)], 0, 0.08, 0, caps=(False, True))
    tube_between(bm, (-0.03, 0, 0.08), (0.15, 0, 0.08), 0.006, 10 if fine else 5, 0)
    return bm, ['stainless']


def cleat(fine):
    """A deck cleat 200 mm long: its base and the two horns."""
    bm = bmesh.new()
    rounded_box(bm, (-0.04, -0.02, 0), (0.04, 0.02, 0.035), 0.008, 0, 2 if fine else 0)
    s = 10 if fine else 5
    tube_between(bm, (-0.1, 0, 0.035), (0.1, 0, 0.035), 0.012, s, 0)
    return bm, ['stainless']


def plate_frame(fine):
    """A number plate's frame 540 x 130 mm and the plate on it (white; the code letters it)."""
    bm = bmesh.new()
    corner = 3 if fine else 1
    prism(bm, rounded_rect(0.54, 0.13, 0.012, corner), 0, 0.006, 0, caps=(False, True))
    prism(bm, rounded_rect(0.52, 0.11, 0.006, corner), 0.006, 0.008, 1, caps=(False, True))
    return bm, ['dash_plastic', 'paint']


def exhaust_tip(fine):
    """An exhaust tip 70 mm across, 150 mm long along +y (its axis), rolled at its end, dark inside."""
    bm = bmesh.new()
    s = 18 if fine else 8
    stack(bm, [(0, 0.15, 0.035, 0.035, s, 0)], open_top=True)
    # the dark inside, seen down the open end
    prism(bm, [(0.034 * math.cos(2 * math.pi * k / s), 0.034 * math.sin(2 * math.pi * k / s)) for k in range(s)], 0.12, 0.1205, 1, caps=(False, True))
    if fine:
        torus(bm, 0.033, 0.003, (0, 0, 0.15), s, 6, 0)
    return bm, ['chrome', 'cast_iron']


def damper_body(fine):
    """A damper's body 600 mm from its lower eye (at the origin, about x) up its axis (+y), for a 1 m damper."""
    bm = bmesh.new()
    s = 14 if fine else 6
    torus(bm, 0.022, 0.009, (0, 0, 0.0), 12 if fine else 6, 6 if fine else 4, 1, axis='x')
    stack(bm, [(0.025, 0.06, 0.02, 0.028, s, 0), (0.06, 0.58, 0.028, 0.028, s, 0), (0.58, 0.6, 0.028, 0.012, s, 1)], seated=False)
    return bm, ['paint', 'machined_steel']


def damper_rod(fine):
    """A damper's rod 500 mm down from its top eye (at the origin, about x), along -y: it slides in the body."""
    bm = bmesh.new()
    s = 12 if fine else 6
    torus(bm, 0.022, 0.009, (0, 0, 0.0), 12 if fine else 6, 6 if fine else 4, 1, axis='x')
    stack(bm, [(-0.5, -0.03, 0.009, 0.009, s, 0), (-0.03, -0.015, 0.012, 0.016, s, 1)], seated=False)
    return bm, ['chrome', 'machined_steel']


PARTS = {
    'headlamp_round': headlamp_round, 'headlamp_projector': headlamp_projector, 'tail_lamp': tail_lamp, 'nav_light': nav_light,
    'beacon': beacon, 'door_mirror': door_mirror, 'wiper': wiper, 'door_handle': door_handle, 'fuel_flap': fuel_flap,
    'antenna': antenna, 'pitot': pitot, 'cleat': cleat, 'plate_frame': plate_frame, 'exhaust_tip': exhaust_tip,
    'damper_body': damper_body, 'damper_rod': damper_rod,
}

if __name__ == '__main__':
    details.export(PARTS, OUT, 'vehicle')
