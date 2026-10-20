"""Models hdfv's detail kit (hdfv 0.11) and exports each part as OBJ + MTL into assets/hdfv/models/part/detail:
bolts, nuts, clamps, fittings, hose ends, connectors, igniters, spark plugs, sensors, actuators, rod ends, nozzle petals,
hinges, rivets, lifting eyes, mount lugs, solenoids, data plates. Every part has a fine object (lod0) and a coarse one
(lod1). The code places and scales them on a design's sizes (DetailKit, Details).

Conventions (the game's frame after export: x along the part, y up): each part stands on y = 0 with its axis along +y
(a bolt's seat is at y = 0, its head above, its shank below); sizes are a reference size in metres (a bolt is M10) the
code scales; texture coordinates are a cube projection in metres (the finishes tile at their own scale); a flat end is
a fan of quads (no faces of more than four corners); faces nobody can see are left out (the underside of a part that
sits on a surface, the end of a part hidden under a wider one); materials are named by finish (machined_steel, ...).
Fasteners go round flanges by the hundred, so their coarse object is a bare hex head of eight faces.
Run: scripts/blender.ps1 details"""
import math
import os
import sys

import bmesh
import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # Blender does not add the script folder
import common  # noqa: E402

OUT = os.path.join(common.ASSETS, 'models', 'part', 'detail')
FINISHES = ['machined_steel', 'stainless', 'forged_steel', 'brass', 'copper', 'rubber', 'plastic', 'ceramic', 'cast_aluminium', 'paint', 'braided_hose']


def material(name):
    m = bpy.data.materials.get(name)
    return m if m is not None else bpy.data.materials.new(name)


def obj_from(bm, name, mats):
    """A Blender object from a bmesh whose faces carry material indices into mats."""
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for f in mats:
        me.materials.append(material(f))
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def cylinder(bm, z0, z1, r0, r1, sides, mat, caps=(True, True), offset=(0.0, 0.0)):
    """A (truncated) cylinder along z from z0 to z1 (Blender z becomes the game's y)."""
    ring0, ring1 = [], []
    for k in range(sides):
        a = 2 * math.pi * (k + 0.5) / sides
        ring0.append(bm.verts.new((offset[0] + r0 * math.cos(a), offset[1] + r0 * math.sin(a), z0)))
        ring1.append(bm.verts.new((offset[0] + r1 * math.cos(a), offset[1] + r1 * math.sin(a), z1)))
    for k in range(sides):
        n = (k + 1) % sides
        f = bm.faces.new((ring0[k], ring0[n], ring1[n], ring1[k]))
        f.material_index = mat
    if caps[0] and r0 > 0:
        cap(bm, ring0, mat, up=False)
    if caps[1] and r1 > 0:
        cap(bm, ring1, mat, up=True)
    return ring0, ring1


def cap(bm, ring, mat, up):
    """A flat polygon over a ring (counter-clockwise about +z) as a fan of quads, a triangle last when the count is odd."""
    v = ring if up else list(reversed(ring))
    k = 1
    while k + 1 < len(v):
        if k + 2 < len(v):
            f = bm.faces.new((v[0], v[k], v[k + 1], v[k + 2]))
            k += 2
        else:
            f = bm.faces.new((v[0], v[k], v[k + 1]))
            k += 1
        f.material_index = mat


def inscribed(r, sides):
    return r * math.cos(math.pi / sides)


def stack(bm, parts, seated=True, open_top=False):
    """Frustums along z, each (z0, z1, r0, r1, sides, material) standing on the one before: an end is capped only where
    it shows past its neighbour (none under a part that sits on a surface, none at an open top)."""
    for i, (z0, z1, r0, r1, sides, mat) in enumerate(parts):
        below = parts[i - 1] if i > 0 else None
        above = parts[i + 1] if i + 1 < len(parts) else None
        if below is None:
            bottom = not seated
        elif abs(below[1] - z0) > 1e-9:
            bottom = True
        elif below[4] == sides:
            bottom = r0 > below[3] + 1e-9
        else:
            bottom = r0 > inscribed(below[3], below[4]) + 1e-9
        if above is None:
            top = not open_top
        elif abs(above[0] - z1) > 1e-9:
            top = True
        elif above[4] == sides:
            top = r1 > above[2] + 1e-9
        else:
            top = r1 > inscribed(above[2], above[4]) + 1e-9
        cylinder(bm, z0, z1, r0, r1, sides, mat, caps=(bottom, top))


def prism(bm, z0, z1, radius, sides, mat, chamfer=0.0):
    """A regular prism (a hex head is six sides) along z, its top edge chamfered."""
    if chamfer > 0:
        stack(bm, [(z0, z1 - chamfer, radius, radius, sides, mat), (z1 - chamfer, z1, radius, radius * 0.86, sides, mat)], seated=False)
    else:
        cylinder(bm, z0, z1, radius, radius, sides, mat)


HEX16 = 0.008 / math.cos(math.pi / 6)   # the corner radius of a 16 mm hex


def torus(bm, major, minor, centre, segments, sides, mat, arc=2 * math.pi, axis='z'):
    """A ring of a tube about an axis (x, y or z, Blender's) through a centre; an arc less than a turn is left open."""
    cx, cy, cz = centre
    closed = arc >= 2 * math.pi - 1e-9
    rings = []
    for i in range(segments if closed else segments + 1):
        u = arc * i / segments
        ring = []
        for j in range(sides):
            v = 2 * math.pi * j / sides
            radial, along = major + minor * math.cos(v), minor * math.sin(v)
            if axis == 'z':
                p = (cx + radial * math.cos(u), cy + radial * math.sin(u), cz + along)
            elif axis == 'y':
                p = (cx + radial * math.cos(u), cy + along, cz + radial * math.sin(u))
            else:
                p = (cx + along, cy + radial * math.cos(u), cz + radial * math.sin(u))
            ring.append(bm.verts.new(p))
        rings.append(ring)
    for i in range(segments):
        a, b = rings[i], rings[(i + 1) % len(rings)]
        for j in range(sides):
            n = (j + 1) % sides
            f = bm.faces.new((a[j], b[j], b[n], a[n]))
            f.material_index = mat


def box(bm, lo, hi, mat, bottom=True):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    v = [bm.verts.new(p) for p in [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]]
    for f in ([(0, 3, 2, 1)] if bottom else []) + [(4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]:
        face = bm.faces.new([v[i] for i in f])
        face.material_index = mat


# ------------------------------------------------------------------------------------------------ the parts

def hex_bolt(fine):
    """An M10 hex bolt seated at 0: a washer 2 mm thick, the head 6.4 mm tall and 16 mm across flats (coarse: the head alone)."""
    bm = bmesh.new()
    if fine:
        stack(bm, [(0, 0.002, 0.0105, 0.0105, 16, 0), (0.002, 0.0076, HEX16, HEX16, 6, 0), (0.0076, 0.0084, HEX16, HEX16 * 0.86, 6, 0)])
    else:
        stack(bm, [(0, 0.0084, HEX16, HEX16, 6, 0)])
    return bm, ['machined_steel']


def hex_nut(fine):
    """An M10 nut on a washer, the stud's end showing 4 mm above it (coarse: the nut alone)."""
    bm = bmesh.new()
    if fine:
        stack(bm, [(0, 0.002, 0.0105, 0.0105, 16, 0), (0.002, 0.0092, HEX16, HEX16, 6, 0), (0.0092, 0.010, HEX16, HEX16 * 0.86, 6, 0),
                   (0.010, 0.014, 0.005, 0.0042, 10, 1)])
        return bm, ['machined_steel', 'forged_steel']
    stack(bm, [(0, 0.010, HEX16, HEX16, 6, 0)])
    return bm, ['machined_steel']


def socket_cap(fine):
    """An M10 socket head cap screw: a 16 mm round head 10 mm tall, a dark hex socket just proud of its top (coarse: no socket)."""
    bm = bmesh.new()
    if fine:
        stack(bm, [(0, 0.0095, 0.008, 0.008, 16, 0), (0.0095, 0.010, 0.008, 0.0072, 16, 0)])
        ring = [bm.verts.new((0.004 / math.cos(math.pi / 6) * math.cos(2 * math.pi * (k + 0.5) / 6),
                              0.004 / math.cos(math.pi / 6) * math.sin(2 * math.pi * (k + 0.5) / 6), 0.01005)) for k in range(6)]
        cap(bm, ring, 1, up=True)
        return bm, ['forged_steel', 'plastic']
    stack(bm, [(0, 0.010, 0.008, 0.0074, 8, 0)])
    return bm, ['forged_steel']


def p_clamp(fine):
    """A cushioned P-clamp holding a 10 mm tube (its centre at y = 8 mm, along x), its tab bolted at the origin."""
    bm = bmesh.new()
    s = 20 if fine else 8
    # the rubber cushion round the tube and the band over it
    for i, (r, mat) in enumerate([(0.0062, 1), (0.0072, 0)]):
        ring0, ring1 = [], []
        for k in range(s):
            a = 2 * math.pi * k / s
            ring0.append(bm.verts.new((-0.004 - 0.001 * i, 0.012 + r * math.cos(a), 0.008 + r * math.sin(a))))
            ring1.append(bm.verts.new((0.004 + 0.001 * i, 0.012 + r * math.cos(a), 0.008 + r * math.sin(a))))
        for k in range(s):
            n = (k + 1) % s
            f = bm.faces.new((ring0[k], ring1[k], ring1[n], ring0[n]))
            f.material_index = mat
    box(bm, (-0.005, -0.008, 0.0), (0.005, 0.006, 0.0012), 0)
    cylinder(bm, 0.0012, 0.0048, 0.0042, 0.0042, 6, 2)
    return bm, ['stainless', 'rubber', 'machined_steel']


def hose_clamp(fine):
    """A worm-drive clamp band for a 25 mm hose (about the y axis: the hose along y), the screw housing on +x."""
    bm = bmesh.new()
    s = 32 if fine else 12
    cylinder(bm, -0.005, 0.005, 0.0135, 0.0135, s, 0, caps=(False, False))
    cylinder(bm, -0.005, 0.005, 0.0128, 0.0128, s, 0, caps=(False, False))
    box(bm, (0.012, -0.006, -0.006), (0.020, 0.006, 0.006), 0)
    cylinder(bm, 0, 0.007, 0.0028, 0.0028, 6, 1, offset=(0.016, 0.0))
    return bm, ['stainless', 'machined_steel']


def an_fitting(fine):
    """An AN -6 flare fitting: a B-nut (hex 15.9 mm) on a body hex, the tube leaving along +y."""
    bm = bmesh.new()
    s = 12 if fine else 6
    h1 = 0.0095 / math.cos(math.pi / 6)
    body = [(0, 0.0064, h1, h1, 6, 0), (0.0064, 0.007, h1, h1 * 0.9, 6, 0)] if fine else [(0, 0.007, h1, h1, 6, 0)]
    stack(bm, body + [(0.007, 0.010, 0.0062, 0.0062, s, 1), (0.010, 0.018, HEX16, HEX16, 6, 1), (0.018, 0.026, 0.0048, 0.0048, s, 0)])
    return bm, ['brass', 'stainless']


def hose_end(fine):
    """A crimped hose end: a ferrule 22 mm long with three crimp bands, the hose (braid) going on along +y."""
    bm = bmesh.new()
    s = 12 if fine else 6
    parts = [(0, 0.004, 0.0065, 0.0065, s, 1)]
    if fine:
        for i in range(3):
            z = 0.004 + i * 0.006
            parts += [(z, z + 0.004, 0.0075, 0.0075, s, 0), (z + 0.004, z + 0.006, 0.0071, 0.0071, s, 0)]
    else:
        parts.append((0.004, 0.022, 0.0074, 0.0074, s, 0))
    stack(bm, parts + [(0.022, 0.034, 0.0068, 0.0068, s, 2)], open_top=True)   # the hose goes on over the top
    return bm, ['stainless', 'machined_steel', 'braided_hose']


def connector(fine):
    """A round MIL connector: a knurled coupling ring 20 mm across, the shell and a cable boot leaving along +y."""
    bm = bmesh.new()
    s = 16 if fine else 8
    stack(bm, [(0, 0.012, 0.0095, 0.0095, s, 0), (0.012, 0.024, 0.0078, 0.0062, s, 1), (0.024, 0.040, 0.0055, 0.0042, s, 2)])
    if fine:
        for k in range(8):
            a = 2 * math.pi * (k + 0.25) / 8
            box(bm, (0.0095 * math.cos(a) - 0.0006, 0.0095 * math.sin(a) - 0.0006, 0.001), (0.0095 * math.cos(a) + 0.0006, 0.0095 * math.sin(a) + 0.0006, 0.011), 0)
    return bm, ['cast_aluminium', 'paint', 'rubber']


def igniter(fine):
    """A turbine igniter plug: hex body (22 mm) in a boss, a long body and the lead's coupling nut along +y."""
    bm = bmesh.new()
    s = 14 if fine else 6
    h11, h9 = 0.011 / math.cos(math.pi / 6), 0.009 / math.cos(math.pi / 6)
    stack(bm, [(0, 0.006, 0.014, 0.014, s, 0), (0.006, 0.016, h11, h11, 6, 1), (0.016, 0.050, 0.0075, 0.0075, s, 1), (0.050, 0.060, h9, h9, 6, 0),
               (0.060, 0.085, 0.0055, 0.0055, s, 2)])
    return bm, ['inconel', 'stainless', 'braided_hose']


def spark_plug(fine):
    """A spark plug standing in its well with the boot over it: hex 16 mm, white ceramic, a rubber boot along +y."""
    bm = bmesh.new()
    s = 14 if fine else 6
    stack(bm, [(0, 0.008, HEX16, HEX16, 6, 0), (0.008, 0.022, 0.0055, 0.005, s, 1), (0.022, 0.050, 0.0085, 0.0075, s, 2)])
    if fine:
        for i in range(3):
            z = 0.0115 + i * 0.0035
            cylinder(bm, z, z + 0.0012, 0.0062, 0.0062, s, 1, caps=(False, False))
        torus(bm, 0.0078, 0.0016, (0, 0, 0.050), s, 4, 2)
    return bm, ['machined_steel', 'ceramic', 'rubber']


def sensor(fine):
    """A pressure or temperature sensor: hex 22 mm screwed into a boss, a body and its connector along +y."""
    bm = bmesh.new()
    s = 14 if fine else 6
    h11 = 0.011 / math.cos(math.pi / 6)
    stack(bm, [(0, 0.008, h11, h11, 6, 0), (0.008, 0.030, 0.0085, 0.0085, s, 0), (0.030, 0.042, 0.0075, 0.0068, s, 1)])
    return bm, ['stainless', 'plastic']


def thermocouple(fine):
    """An exhaust thermocouple: a small boss and probe nut, the sheathed lead bending away along +y."""
    bm = bmesh.new()
    s = 12 if fine else 6
    h55 = 0.0055 / math.cos(math.pi / 6)
    stack(bm, [(0, 0.004, 0.008, 0.008, s, 0), (0.004, 0.011, h55, h55, 6, 0), (0.011, 0.040, 0.0018, 0.0018, 6, 1)])
    return bm, ['inconel', 'stainless']


def actuator(fine):
    """A hydraulic actuator 1 m between pin centres (scaled to its length along +y): barrel, gland, rod and two eyes."""
    bm = bmesh.new()
    s = 14 if fine else 6
    torus(bm, 0.03, 0.012, (0, 0, 0.0), 12 if fine else 6, 6 if fine else 4, 1, axis='x')
    stack(bm, [(0.03, 0.08, 0.025, 0.04, s, 0), (0.08, 0.56, 0.04, 0.04, s, 0), (0.56, 0.60, 0.035, 0.03, s, 1), (0.60, 0.95, 0.018, 0.018, s, 1)], seated=False)
    torus(bm, 0.03, 0.012, (0, 0, 1.0), 12 if fine else 6, 6 if fine else 4, 1, axis='x')
    return bm, ['paint', 'machined_steel']


def rod_end(fine):
    """A spherical rod end (clevis eye) for a 10 mm pin: the eye about the z axis at y = 0, its shank along -y."""
    bm = bmesh.new()
    s = 14 if fine else 8
    torus(bm, 0.009, 0.004, (0, 0, 0), s, 6 if fine else 4, 0, axis='y')
    cylinder(bm, -0.035, -0.012, 0.005, 0.006, s // 2, 0)
    cylinder(bm, -0.004, 0.004, 0.005, 0.005, s // 2, 1)
    return bm, ['machined_steel', 'forged_steel']


def nozzle_petal(fine):
    """A convergent nozzle petal 1 m long (scaled), 0.3 wide at the hinge narrowing to 0.22, a hinge knuckle at y = 0."""
    bm = bmesh.new()
    rows = 6 if fine else 2
    cols = 4 if fine else 2
    sheets = []
    for side, lift in ((0, 0.0), (1, -0.012)):
        verts = []
        for i in range(rows + 1):
            t = i / rows
            y = t
            w = 0.15 - 0.04 * t
            curve = 0.06 * t * t   # bends inward along its length
            row = []
            for j in range(cols + 1):
                x = -w + 2 * w * j / cols
                z = -curve - 0.03 * (x / w) ** 2 + lift
                row.append(bm.verts.new((x, z, y)))
            verts.append(row)
        for i in range(rows):
            for j in range(cols):
                quad = (verts[i][j], verts[i][j + 1], verts[i + 1][j + 1], verts[i + 1][j])
                f = bm.faces.new(quad if side == 0 else tuple(reversed(quad)))
                f.material_index = 0
        sheets.append(verts)
    top, under = sheets
    for i in range(rows):   # the two long edges
        for j in (0, cols):
            quad = (top[i][j], top[i + 1][j], under[i + 1][j], under[i][j])
            f = bm.faces.new(quad if j == 0 else tuple(reversed(quad)))
            f.material_index = 0
    for j in range(cols):   # the trailing edge
        f = bm.faces.new((top[rows][j], top[rows][j + 1], under[rows][j + 1], under[rows][j]))
        f.material_index = 0
    # a stiffening rib along the middle and the hinge knuckle
    for i in range(rows):
        t0, t1 = i / rows, (i + 1) / rows
        box(bm, (-0.008, -0.06 * t0 * t0, t0), (0.008, -0.06 * t0 * t0 + 0.025, t1), 0)
    cylinder(bm, -0.02, 0.02, 0.025, 0.025, 12 if fine else 6, 1)
    return bm, ['inconel', 'machined_steel']


def hinge(fine):
    """A hinge: three knuckles about the x axis on a 10 mm pin, 60 mm wide, at the origin."""
    bm = bmesh.new()
    s = 14 if fine else 6
    for i, (x0, x1) in enumerate([(-0.03, -0.011), (-0.009, 0.009), (0.011, 0.03)]):
        ring0, ring1 = [], []
        for k in range(s):
            a = 2 * math.pi * k / s
            ring0.append(bm.verts.new((x0, 0.008 * math.cos(a), 0.008 * math.sin(a))))
            ring1.append(bm.verts.new((x1, 0.008 * math.cos(a), 0.008 * math.sin(a))))
        for k in range(s):
            n = (k + 1) % s
            f = bm.faces.new((ring0[k], ring1[k], ring1[n], ring0[n]))
            f.material_index = 0
        cap(bm, ring0, 1, up=False)
        cap(bm, ring1, 1, up=True)
    return bm, ['forged_steel', 'machined_steel']


def rivet(fine):
    """A round-head rivet 5 mm across: a low dome on the surface."""
    bm = bmesh.new()
    s = 10 if fine else 6
    rings = 3 if fine else 1
    top = bm.verts.new((0, 0, 0.0016))
    prev = None
    for i in range(rings, 0, -1):
        a = (math.pi / 2) * i / (rings + 0.0001) if rings > 1 else math.pi / 2
        r = 0.0025 * math.sin(math.pi / 2 * i / rings)
        z = 0.0016 * math.cos(math.pi / 2 * i / rings)
        ring = [bm.verts.new((r * math.cos(2 * math.pi * k / s), r * math.sin(2 * math.pi * k / s), z)) for k in range(s)]
        if prev is None:
            for k in range(s):
                f = bm.faces.new((top, ring[k], ring[(k + 1) % s]))
                f.material_index = 0
        else:
            for k in range(s):
                n = (k + 1) % s
                f = bm.faces.new((prev[k], ring[k], ring[n], prev[n]))
                f.material_index = 0
        prev = ring
    return bm, ['machined_steel']


def lifting_eye(fine):
    """A lifting eye bolted to a casing: a forged loop 60 mm across standing up, its base pad 50 x 30 mm."""
    bm = bmesh.new()
    s = 16 if fine else 8
    box(bm, (-0.025, -0.015, 0), (0.025, 0.015, 0.008), 0, bottom=False)
    torus(bm, 0.022, 0.006, (0, 0, 0.035), s, 8 if fine else 4, 0, arc=math.pi, axis='y')
    return bm, ['forged_steel']


def mount_lug(fine):
    """An engine mount lug: a 60 mm pad and a tapered ear with a 20 mm bore, standing 50 mm up."""
    bm = bmesh.new()
    box(bm, (-0.03, -0.02, 0), (0.03, 0.02, 0.01), 0, bottom=False)
    box(bm, (-0.012, -0.018, 0.01), (0.012, 0.018, 0.04), 0, bottom=False)
    s = 16 if fine else 8
    torus(bm, 0.016, 0.006, (0, 0, 0.048), s, 6 if fine else 4, 0, axis='y')
    return bm, ['forged_steel']


def solenoid(fine):
    """A starter solenoid: a can 45 mm across, 80 mm long along +y, two terminal studs on its end."""
    bm = bmesh.new()
    s = 16 if fine else 8
    stack(bm, [(0, 0.08, 0.0225, 0.0225, s, 0), (0.08, 0.092, 0.018, 0.015, s, 1)])
    for x in (-0.008, 0.008):
        cylinder(bm, 0.092, 0.104, 0.003, 0.003, 6, 2, offset=(x, 0.0))
    return bm, ['paint', 'plastic', 'copper']


def data_plate(fine):
    """A riveted data plate 80 x 50 mm, 1 mm proud, a rivet at each corner."""
    bm = bmesh.new()
    box(bm, (-0.04, -0.025, 0), (0.04, 0.025, 0.001), 0, bottom=False)
    for x in (-0.035, 0.035):
        for y in (-0.02, 0.02):
            cylinder(bm, 0.001, 0.0018, 0.002, 0.0015, 8 if fine else 4, 1, caps=(False, True), offset=(x, y))
    return bm, ['stainless', 'machined_steel']


PARTS = {
    'hex_bolt': hex_bolt, 'hex_nut': hex_nut, 'socket_cap': socket_cap, 'p_clamp': p_clamp, 'hose_clamp': hose_clamp,
    'an_fitting': an_fitting, 'hose_end': hose_end, 'connector': connector, 'igniter': igniter, 'spark_plug': spark_plug,
    'sensor': sensor, 'thermocouple': thermocouple, 'actuator': actuator, 'rod_end': rod_end, 'nozzle_petal': nozzle_petal,
    'hinge': hinge, 'rivet': rivet, 'lifting_eye': lifting_eye, 'mount_lug': mount_lug, 'solenoid': solenoid, 'data_plate': data_plate,
}


def finish_object(ob):
    """Normals out, faces of more than four corners triangulated, smooth by angle, a metre cube projection for UVs."""
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
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.cube_project(cube_size=1.0, correct_aspect=False, clip_to_bounds=False, scale_to_bounds=False)
    bpy.ops.object.mode_set(mode='OBJECT')
    try:
        bpy.ops.object.shade_smooth_by_angle(angle=math.radians(35))
    except (AttributeError, RuntimeError):
        bpy.ops.object.shade_smooth()


def export(parts, out, what='detail'):
    """Models every part of a kit (name: build(fine)) and writes its OBJ + MTL (lod0 and lod1 objects) into a folder
    (0.12: vehicle.py and interior.py use it for their kits)."""
    common.reset()
    os.makedirs(out, exist_ok=True)
    written = 0
    for name, build in parts.items():
        for o in list(bpy.data.objects):
            bpy.data.objects.remove(o)
        objects = []
        for lod, fine in (('lod0', True), ('lod1', False)):
            bm, mats = build(fine)
            ob = obj_from(bm, lod, mats)
            finish_object(ob)
            objects.append(ob)
        bpy.ops.object.select_all(action='DESELECT')
        for ob in objects:
            ob.select_set(True)
        tmp = os.path.join(os.environ.get('TEMP', out), 'hdfv_' + what + '_' + name + '.obj')
        bpy.ops.wm.obj_export(filepath=tmp, export_selected_objects=True, export_uv=True, export_normals=True, export_colors=False,
                              export_materials=True, export_triangulated_mesh=False, forward_axis='NEGATIVE_Z', up_axis='Y',
                              export_object_groups=False, path_mode='STRIP')
        obj_text = open(tmp, encoding='utf-8').read()
        mtl_text = open(tmp[:-4] + '.mtl', encoding='utf-8').read()
        # deterministic text: no tool header or comment lines, the library named after the part
        obj_lines = [l for l in obj_text.splitlines() if not l.startswith('#')]
        obj_lines = ['mtllib ' + name + '.mtl' if l.startswith('mtllib') else l for l in obj_lines]
        mtl_lines = [l for l in mtl_text.splitlines() if not l.startswith('#')]
        mtl_lines = ['Ka 0.000000 0.000000 0.000000' if l.startswith('Ka ') else l for l in mtl_lines]
        if common.write_if_changed(os.path.join(out, name + '.obj'), ('\n'.join(obj_lines) + '\n').encode('utf-8')):
            written += 1
        if common.write_if_changed(os.path.join(out, name + '.mtl'), ('\n'.join(mtl_lines) + '\n').encode('utf-8')):
            written += 1
        common.log(what, name, sum(len(o.data.polygons) for o in objects[:1]), 'faces at lod0,', len(objects[1].data.polygons), 'at lod1')
    common.log(what + ' kit done:', len(parts), 'parts,', written, 'files written')


if __name__ == '__main__':
    export(PARTS, OUT)
