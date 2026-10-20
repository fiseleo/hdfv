"""Shapes shared by the vehicle and interior kits (hdfv 0.12), on top of details.py's: rounded boxes, extruded outlines,
domes and moving a part of a bmesh. Blender z is the game's y (up, or out of the surface a part sits on)."""
import math

import bmesh
from mathutils import Matrix, Vector

from details import cylinder, cap  # noqa: F401  (re-exported for the kits)


def new_faces(bm, before):
    return [f for f in bm.faces if f not in before]


def rounded_box(bm, lo, hi, radius, mat, segments=2):
    """A box from lo to hi with its edges rounded (radius, segments; segments 0: sharp).

    The box is bevelled in a bmesh of its own and copied over in order: a bevel frees the box's faces, and what is
    built afterwards would take those free slots in an order that follows memory addresses, so the OBJ's face order
    (and whether it is rewritten) changed from run to run."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    own = bmesh.new()
    v = [own.verts.new(p) for p in [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]]
    for f in [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]:
        own.faces.new([v[i] for i in f])
    if segments > 0 and radius > 0:
        own.edges.index_update()
        own.verts.index_update()
        edges = sorted(own.edges, key=lambda e: e.index)
        r = min(radius, 0.49 * min(x1 - x0, y1 - y0, z1 - z0))
        bmesh.ops.bevel(own, geom=edges + sorted(own.verts, key=lambda vv: vv.index), offset=r, segments=segments, affect='EDGES', profile=0.5)
    copied = {}
    for vv in own.verts:
        copied[vv] = bm.verts.new(vv.co)
    faces = []
    for f in own.faces:
        g = bm.faces.new([copied[vv] for vv in f.verts])
        g.material_index = mat
        faces.append(g)
    own.free()
    return faces


def rounded_rect(w, h, r, corner=4):
    """A rounded rectangle's outline (counter-clockwise), w by h, centred at the origin."""
    r = min(r, 0.49 * min(w, h))
    pts = []
    for cx, cy, a0 in ((w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90), (-w / 2 + r, -h / 2 + r, 180), (w / 2 - r, -h / 2 + r, 270)):
        for i in range(corner + 1):
            a = math.radians(a0 + 90 * i / corner)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def prism(bm, outline, z0, z1, mat, caps=(True, True), offset=(0.0, 0.0)):
    """An outline (convex, counter-clockwise in x, y) extruded along z from z0 to z1, its ends capped as fans."""
    ring0 = [bm.verts.new((offset[0] + x, offset[1] + y, z0)) for x, y in outline]
    ring1 = [bm.verts.new((offset[0] + x, offset[1] + y, z1)) for x, y in outline]
    n = len(outline)
    for k in range(n):
        f = bm.faces.new((ring0[k], ring0[(k + 1) % n], ring1[(k + 1) % n], ring1[k]))
        f.material_index = mat
    if caps[0]:
        cap(bm, ring0, mat, up=False)
    if caps[1]:
        cap(bm, ring1, mat, up=True)
    return ring0, ring1


def dome(bm, radius, height, z0, sides, rings, mat, offset=(0.0, 0.0), scale=(1.0, 1.0)):
    """A dome (half an ellipsoid) standing on z0, its rim a circle of a radius (scaled in x, y), closed at its top."""
    prev = None
    for i in range(rings + 1):
        t = math.pi / 2 * i / rings
        r, z = radius * math.cos(t), z0 + height * math.sin(t)
        if i == rings:
            top = bm.verts.new((offset[0], offset[1], z))
            for k in range(sides):
                f = bm.faces.new((prev[k], prev[(k + 1) % sides], top))
                f.material_index = mat
            break
        ring = [bm.verts.new((offset[0] + scale[0] * r * math.cos(2 * math.pi * k / sides), offset[1] + scale[1] * r * math.sin(2 * math.pi * k / sides), z))
                for k in range(sides)]
        if prev is not None:
            for k in range(sides):
                f = bm.faces.new((prev[k], prev[(k + 1) % sides], ring[(k + 1) % sides], ring[k]))
                f.material_index = mat
        prev = ring


def transform(bm, faces, matrix):
    """Moves the vertices of some faces by a 4 x 4 matrix (a rotation about a pivot, a translation)."""
    verts = list({v for f in faces for v in f.verts})
    bmesh.ops.transform(bm, matrix=matrix, verts=verts)


def rotation(angle_deg, axis, pivot=(0.0, 0.0, 0.0)):
    p = Vector(pivot)
    return Matrix.Translation(p) @ Matrix.Rotation(math.radians(angle_deg), 4, axis) @ Matrix.Translation(-p)


def tube_between(bm, a, b, radius, sides, mat):
    """A capped cylinder from point a to point b."""
    before = set(bm.faces)
    length = (Vector(b) - Vector(a)).length
    cylinder(bm, 0, length, radius, radius, sides, mat)
    faces = new_faces(bm, before)
    d = (Vector(b) - Vector(a)).normalized()
    rot = Vector((0, 0, 1)).rotation_difference(d).to_matrix().to_4x4()
    transform(bm, faces, Matrix.Translation(Vector(a)) @ rot)
    return faces
