"""Models hdfv's interior kit (hdfv 0.12) and exports each part as OBJ + MTL into assets/hdfv/models/part/interior: what
a cabin, a cockpit and a helm hold, placed by the code (BodyModel) on a design's seats and modules: a car's bucket seat
and bench, an aircraft seat and an airliner's, a steering wheel, a yoke, a stick, a collective, a car's pedal, rudder
pedals, a gear lever, a throttle quadrant, a boat's wheel, a gauge and an instrument binnacle.

Conventions (as details.py): Blender z is the game's y. Seats stand on the floor at y = 0 facing +x, centred across.
A wheel, a yoke and a gauge have their axis along +y (toward whoever uses them) and their right along +x; a stick, a
lever and a pedal stand along +y from their pivot. Sizes are real (metres). Run: scripts/blender.ps1 interior"""
import math
import os
import sys

import bmesh
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import details  # noqa: E402
from details import box, cylinder, stack, torus  # noqa: E402
from kitshapes import dome, new_faces, prism, rotation, rounded_box, transform, tube_between  # noqa: E402

OUT = os.path.join(common.ASSETS, 'models', 'part', 'interior')


def seat(bm, fine, width, cushion_mat, trim_mat, bolsters, headrests, back_h, recline, base_z=0.03):
    """A seat facing +x: its base (from base_z: over rails, or on the floor), cushion (with side bolsters), reclined
    back and headrests (at their across positions)."""
    seg = 2 if fine else 0
    hw = width / 2
    rounded_box(bm, (-0.24, -hw + 0.02, base_z), (0.2, hw - 0.02, 0.2), 0.02, trim_mat, seg)
    rounded_box(bm, (-0.26, -hw, 0.2), (0.24, hw, 0.31), 0.045, cushion_mat, seg)
    if bolsters:
        for side in (-1, 1):
            y0, y1 = sorted((side * (hw - 0.06), side * (hw + 0.01)))
            rounded_box(bm, (-0.24, y0, 0.25), (0.2, y1, 0.36), 0.03, cushion_mat, seg)
    before = set(bm.faces)
    rounded_box(bm, (-0.07, -hw, 0), (0.04, hw, back_h), 0.04, cushion_mat, seg)
    if bolsters:
        for side in (-1, 1):
            y0, y1 = sorted((side * (hw - 0.06), side * (hw + 0.01)))
            rounded_box(bm, (-0.03, y0, 0.06), (0.08, y1, back_h - 0.12), 0.03, cushion_mat, seg)
    for y in headrests:
        rounded_box(bm, (-0.06, y - 0.13, back_h + 0.05), (0.03, y + 0.13, back_h + 0.24), 0.035, cushion_mat, seg)
        if fine:
            for dy in (-0.07, 0.07):
                cylinder(bm, back_h - 0.01, back_h + 0.06, 0.006, 0.006, 6, 2, offset=(-0.015, y + dy))
    # the back leans aft about its foot
    transform(bm, new_faces(bm, before), Matrix.Translation(Vector((-0.22, 0, 0.27))) @ Matrix.Rotation(math.radians(-recline), 4, 'Y'))


def car_seat(fine):
    """A car's bucket seat 0.5 m wide: rails on the floor, a cloth cushion and back with side bolsters, a headrest."""
    bm = bmesh.new()
    for y in (-0.18, 0.18):
        box(bm, (-0.25, y - 0.012, 0), (0.25, y + 0.012, 0.03), 2)
    seat(bm, fine, 0.5, 0, 1, True, [0.0], 0.62, 15)
    return bm, ['fabric', 'dash_plastic', 'stainless']


def bench_seat(fine):
    """A car's rear bench 1.3 m wide for three: one cushion and back, two headrests."""
    bm = bmesh.new()
    seat(bm, fine, 1.3, 0, 1, False, [-0.38, 0.38], 0.58, 20, base_z=0.0)
    return bm, ['fabric', 'dash_plastic', 'stainless']


def aircraft_seat(fine):
    """A light aircraft's seat 0.48 m wide on tube legs, leather, with armrests."""
    bm = bmesh.new()
    s = 8 if fine else 4
    for x in (-0.2, 0.18):
        for y in (-0.2, 0.2):
            cylinder(bm, 0, 0.2, 0.012, 0.012, s, 2, offset=(x, y))
    seat(bm, fine, 0.48, 0, 1, False, [0.0], 0.6, 18)
    if fine:
        for y in (-0.27, 0.27):
            rounded_box(bm, (-0.22, y - 0.03, 0.4), (0.12, y + 0.03, 0.45), 0.015, 1, 2)
    return bm, ['leather', 'dash_plastic', 'machined_steel']


def cabin_seat(fine):
    """An airliner's seat 0.45 m wide (drawn by the dozen: few faces): cushion, a tall back with its shell, armrests."""
    bm = bmesh.new()
    seg = 1 if fine else 0
    box(bm, (-0.2, -0.2, 0), (0.15, 0.2, 0.2), 1)
    rounded_box(bm, (-0.24, -0.225, 0.2), (0.22, 0.225, 0.3), 0.03, 0, seg)
    before = set(bm.faces)
    rounded_box(bm, (-0.07, -0.225, 0), (0.04, 0.225, 0.78), 0.035, 0, seg)
    transform(bm, new_faces(bm, before), Matrix.Translation(Vector((-0.22, 0, 0.27))) @ Matrix.Rotation(math.radians(-12), 4, 'Y'))
    for y in (-0.235, 0.235):
        box(bm, (-0.22, y - 0.02, 0.3), (0.12, y + 0.02, 0.36), 1)
    return bm, ['fabric', 'dash_plastic']


def steering_wheel(fine):
    """A car's steering wheel 380 mm across: a leather rim, three spokes, the hub and the column behind (-y)."""
    bm = bmesh.new()
    s, t = (28, 8) if fine else (12, 4)
    torus(bm, 0.19, 0.014, (0, 0, 0), s, t, 0)
    stack(bm, [(-0.035, 0.012, 0.07, 0.06, 16 if fine else 8, 1)], seated=False)
    for angle in (0, 180, 270):
        before = set(bm.faces)
        box(bm, (0.055, -0.016, -0.008), (0.185, 0.016, 0.006), 1)
        transform(bm, new_faces(bm, before), Matrix.Rotation(math.radians(angle), 4, 'Z'))
    cylinder(bm, -0.3, -0.035, 0.03, 0.035, 10 if fine else 6, 1)
    return bm, ['leather', 'dash_plastic']


def yoke(fine):
    """An aircraft's yoke 300 mm across: the column into the panel (-y), the hub and two horns with grips."""
    bm = bmesh.new()
    s = 10 if fine else 5
    cylinder(bm, -0.35, 0, 0.02, 0.02, s, 1)
    rounded_box(bm, (-0.06, -0.025, -0.03), (0.06, 0.025, 0.02), 0.01, 1, 2 if fine else 0)
    tube_between(bm, (-0.15, -0.02, 0), (0.15, -0.02, 0), 0.014, s, 1)
    for side in (-1, 1):
        tube_between(bm, (side * 0.15, -0.02, 0), (side * 0.16, 0.11, 0), 0.018, s, 0)
    return bm, ['rubber', 'dash_plastic']


def stick(fine):
    """A control stick 440 mm tall from its pivot: a boot, the shaft and a grip."""
    bm = bmesh.new()
    s = 12 if fine else 6
    stack(bm, [(0, 0.05, 0.05, 0.015, s, 0), (0.05, 0.32, 0.01, 0.01, s, 1), (0.32, 0.43, 0.018, 0.016, s, 0), (0.43, 0.44, 0.016, 0.01, s, 2)])
    return bm, ['rubber', 'machined_steel', 'dash_plastic']


def collective(fine):
    """A helicopter's collective lever 580 mm long along +x from its pivot, rising a little, its twist grip at the end."""
    bm = bmesh.new()
    s = 10 if fine else 5
    rounded_box(bm, (-0.03, -0.03, -0.03), (0.03, 0.03, 0.03), 0.01, 1, 2 if fine else 0)
    tube_between(bm, (0, 0, 0), (0.45, 0, 0.06), 0.014, s, 1)
    tube_between(bm, (0.44, 0, 0.059), (0.58, 0, 0.078), 0.022, s, 0)
    return bm, ['rubber', 'machined_steel']


def pedal(fine):
    """A car's pedal: its pad (rubber, facing -x) at the bottom of an arm hanging 280 mm from its pivot at the top."""
    bm = bmesh.new()
    rounded_box(bm, (-0.012, -0.04, 0), (0.0, 0.04, 0.09), 0.006, 0, 2 if fine else 0)
    box(bm, (0.0, -0.006, 0.05), (0.012, 0.006, 0.28), 1)
    return bm, ['rubber', 'forged_steel']


def rudder_pedals(fine):
    """An aircraft's rudder pedals: a cross bar on two brackets on the floor and two pedals 240 mm apart, leaning back
    toward the pilot (-x)."""
    bm = bmesh.new()
    s = 8 if fine else 4
    tube_between(bm, (0.05, -0.2, 0.05), (0.05, 0.2, 0.05), 0.012, s, 1)
    for y in (-0.19, 0.19):
        rounded_box(bm, (0.03, y - 0.008, 0), (0.07, y + 0.008, 0.065), 0.004, 1, 1 if fine else 0)
    for y in (-0.12, 0.12):
        before = set(bm.faces)
        rounded_box(bm, (-0.01, y - 0.05, 0), (0.0, y + 0.05, 0.16), 0.008, 0, 2 if fine else 0)
        transform(bm, new_faces(bm, before), rotation(-25, 'Y', (0, 0, 0.05)))
    return bm, ['rubber', 'machined_steel']


def gear_lever(fine):
    """A gear lever 210 mm tall: the surround on the console, a rubber boot, the chrome shaft and a leather knob."""
    bm = bmesh.new()
    s = 12 if fine else 6
    rounded_box(bm, (-0.07, -0.05, 0), (0.07, 0.05, 0.006), 0.012, 3, 2 if fine else 0)
    stack(bm, [(0.006, 0.06, 0.045, 0.012, s, 1), (0.06, 0.17, 0.006, 0.006, 8 if fine else 4, 2)])
    dome(bm, 0.026, 0.024, 0.19, s, 3 if fine else 1, 0)
    dome(bm, 0.026, -0.02, 0.19, s, 2 if fine else 1, 0)
    return bm, ['leather', 'rubber', 'chrome', 'dash_plastic']


def throttle_quadrant(fine):
    """An aircraft's throttle quadrant: a box 120 mm wide and two levers with knobs."""
    bm = bmesh.new()
    rounded_box(bm, (-0.05, -0.06, 0), (0.05, 0.06, 0.06), 0.012, 0, 2 if fine else 0)
    s = 10 if fine else 5
    for y in (-0.025, 0.025):
        box(bm, (-0.006, y - 0.004, 0.05), (0.006, y + 0.004, 0.15), 1)
        cylinder(bm, 0.15, 0.18, 0.014, 0.014, s, 0, offset=(0, y))
    return bm, ['dash_plastic', 'machined_steel']


def helm_wheel(fine):
    """A boat's wheel 400 mm across: a stainless rim, five spokes and a hub, its shaft behind (-y)."""
    bm = bmesh.new()
    s, t = (28, 6) if fine else (12, 4)
    torus(bm, 0.2, 0.012, (0, 0, 0), s, t, 0)
    stack(bm, [(-0.03, 0.02, 0.04, 0.035, 14 if fine else 6, 0)], seated=False)
    for k in range(5):
        a = 2 * math.pi * k / 5 + math.pi / 2
        tube_between(bm, (0.035 * math.cos(a), 0.035 * math.sin(a), 0), (0.2 * math.cos(a), 0.2 * math.sin(a), 0), 0.008, 6 if fine else 4, 0)
    cylinder(bm, -0.25, -0.03, 0.02, 0.02, 8 if fine else 4, 0)
    return bm, ['stainless']


def gauge(fine):
    """A round gauge 80 mm across: a chrome bezel, a dark face and a white needle."""
    bm = bmesh.new()
    s = 24 if fine else 10
    prism(bm, [(0.038 * math.cos(2 * math.pi * k / s), 0.038 * math.sin(2 * math.pi * k / s)) for k in range(s)], -0.006, 0.0, 1, caps=(False, True))
    if fine:
        torus(bm, 0.04, 0.004, (0, 0, 0.0), s, 6, 0)
    box(bm, (-0.003, -0.001, 0.0005), (0.03, 0.001, 0.0015), 2)
    return bm, ['chrome', 'dash_plastic', 'paint']


def binnacle(fine):
    """A car's instrument binnacle on the dash top: a hood 320 mm wide and two gauge faces looking aft (-x)."""
    bm = bmesh.new()
    rounded_box(bm, (-0.06, -0.16, 0), (0.05, 0.16, 0.13), 0.03, 0, 2 if fine else 0)
    s = 20 if fine else 8
    for y in (-0.075, 0.075):
        before = set(bm.faces)
        prism(bm, [(0.045 * math.cos(2 * math.pi * k / s), 0.045 * math.sin(2 * math.pi * k / s)) for k in range(s)], 0, 0.002, 1, caps=(False, True))
        if fine:
            torus(bm, 0.047, 0.003, (0, 0, 0.001), s, 4, 2)
        # stood on the hood's aft face, facing aft
        transform(bm, new_faces(bm, before), Matrix.Translation(Vector((-0.061, y, 0.07))) @ Matrix.Rotation(math.radians(-90), 4, 'Y'))
    return bm, ['dash_plastic', 'dash_plastic', 'chrome']


PARTS = {
    'car_seat': car_seat, 'bench_seat': bench_seat, 'aircraft_seat': aircraft_seat, 'cabin_seat': cabin_seat,
    'steering_wheel': steering_wheel, 'yoke': yoke, 'stick': stick, 'collective': collective, 'pedal': pedal,
    'rudder_pedals': rudder_pedals, 'gear_lever': gear_lever, 'throttle_quadrant': throttle_quadrant, 'helm_wheel': helm_wheel,
    'gauge': gauge, 'binnacle': binnacle,
}

if __name__ == '__main__':
    details.export(PARTS, OUT, 'interior')
