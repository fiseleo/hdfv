"""Checks that this Blender can do what hdfv's scripts need (hdfv 0.11): its version, a Cycles bake of 4D noise on the
torus mapping (seamless), our PNG writer, and an OBJ round trip. Run: scripts/blender.ps1 selftest"""
import os
import sys
import tempfile

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # Blender does not add the script folder
import common  # noqa: E402

common.log('blender', bpy.app.version_string)
common.reset()
channels = common.bake_channels([('noise', 4, 4, 3, 0.5, 0, 0.0)], 8)
values = channels[0]
assert len(values) == 64 and all(0 <= v <= 1.5 for v in values), 'bake values out of range'
assert max(values) - min(values) > 0.01, 'the bake came out flat'
common.log('bake ok, range', round(min(values), 3), round(max(values), 3))
# seamless: the torus mapping makes the left edge continue the right one about as smoothly as neighbours inside
size = 32
channels = common.bake_channels([('noise', 3, 3, 2, 0.5, 0, 1.0)], size)
v = channels[0]
inner = sum(abs(v[r * size + c] - v[r * size + c + 1]) for r in range(size) for c in range(size - 1)) / (size * (size - 1))
wrap = sum(abs(v[r * size + size - 1] - v[r * size]) for r in range(size)) / size
common.log('mean step inside', round(inner, 4), 'across the wrap', round(wrap, 4))
assert wrap < 3 * inner + 1e-3, 'the texture does not wrap'
png = common.png_rgba(2, 2, bytes([255, 0, 0, 255] * 4))
assert png.startswith(b'\x89PNG') and len(png) > 40
bpy.ops.mesh.primitive_cube_add(size=1)
path = os.path.join(tempfile.gettempdir(), 'hdfv_selftest.obj')
bpy.ops.wm.obj_export(filepath=path, export_selected_objects=True, export_materials=False, export_colors=False)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete()
bpy.ops.wm.obj_import(filepath=path)
faces = sum(len(o.data.polygons) for o in bpy.context.selected_objects)
assert faces == 6, 'OBJ round trip gave %d faces' % faces
os.remove(path)
common.log('selftest ok')
