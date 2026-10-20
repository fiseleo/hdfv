"""Reference renders (hdfv 0.11): renders a model the game exported (hdfvdev export, %USERPROFILE%/.mcmod/exports) or the
whole detail kit, in a studio (three area lights, a soft grey world), with the game's finish textures, gloss and
metalness, so it can be set beside photographs of the real thing.
Run: scripts/blender.ps1 review <model.obj | kit | kit:vehicle | kit:interior> <out.png>
The model's texture coordinates are tile repeats (ObjWriter); the kit's are metres."""
import json
import math
import os
import sys

import bpy
from mathutils import Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # Blender does not add the script folder
import common  # noqa: E402

FINISHES = json.load(open(os.path.join(common.ASSETS, 'finishes.json'), encoding='utf-8'))


def finish_for(material_name):
    """The finish id a material stands for: 'machined_steel' (kit) or 'hdfv_brass_b08d57' (an export)."""
    name = material_name.split('.')[0]
    for fid in FINISHES:
        short = fid.split(':')[1]
        if name == short or name.startswith(fid.replace(':', '_') + '_') or name.startswith(short + '_'):
            return fid
    return None


def shade(mat, metres):
    """Principled material: the MTL's diffuse colour times the finish texture; metalness and roughness from finishes.json."""
    base = tuple(mat.diffuse_color[:3]) if hasattr(mat, 'diffuse_color') else (0.8, 0.8, 0.8)
    try:
        mat.use_nodes = True
    except (AttributeError, TypeError):
        pass
    tree = mat.node_tree
    bsdf = next((n for n in tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    if bsdf is None:
        tree.nodes.clear()
        bsdf = tree.nodes.new('ShaderNodeBsdfPrincipled')
        out = tree.nodes.new('ShaderNodeOutputMaterial')
        tree.links.new(bsdf.outputs[0], out.inputs['Surface'])
    # the imported colour may sit in a texture-less Base Color: read it there
    colour = tuple(bsdf.inputs['Base Color'].default_value[:3])
    if sum(colour) < 0.01:
        colour = base
    fid = finish_for(mat.name)
    if fid is None:
        bsdf.inputs['Base Color'].default_value = (*colour, 1)
        bsdf.inputs['Roughness'].default_value = 0.6
        return
    f = FINISHES[fid]
    bsdf.inputs['Metallic'].default_value = float(f['metal'])
    bsdf.inputs['Roughness'].default_value = max(0.05, 1 - float(f['gloss']))
    texture = os.path.join(common.ASSETS, 'textures', 'part', fid.split(':')[1] + '.png')
    if os.path.exists(texture):
        img = bpy.data.images.load(texture, check_existing=True)
        tex = tree.nodes.new('ShaderNodeTexImage')
        tex.image = img
        coord = tree.nodes.new('ShaderNodeTexCoord')
        mapping = tree.nodes.new('ShaderNodeMapping')
        scale = 1 / float(f['tile']) if metres else 1.0
        mapping.inputs['Scale'].default_value = (scale, scale, scale)
        tree.links.new(coord.outputs['UV'], mapping.inputs['Vector'])
        tree.links.new(mapping.outputs['Vector'], tex.inputs['Vector'])
        mix = tree.nodes.new('ShaderNodeMix')
        mix.data_type = 'RGBA'
        mix.blend_type = 'MULTIPLY'
        mix.inputs['Factor'].default_value = 1.0
        mix.inputs['A'].default_value = (*colour, 1)
        tree.links.new(tex.outputs['Color'], mix.inputs['B'])
        tree.links.new(mix.outputs['Result'], bsdf.inputs['Base Color'])


def studio(scene, centre, size):
    world = bpy.data.worlds.new('studio')
    scene.world = world
    try:
        world.use_nodes = True
    except (AttributeError, TypeError):
        pass
    bg = world.node_tree.nodes.get('Background')
    if bg is not None:
        bg.inputs['Color'].default_value = (0.32, 0.34, 0.37, 1)
        bg.inputs['Strength'].default_value = 0.6
    for name, direction, power, colour in (('key', (-1.0, -1.2, 1.4), 900, (1.0, 0.96, 0.9)), ('fill', (1.3, -0.6, 0.5), 300, (0.85, 0.9, 1.0)),
                                           ('rim', (0.4, 1.5, 1.0), 600, (1.0, 1.0, 1.0))):
        light = bpy.data.lights.new(name, 'AREA')
        light.energy = power * (size ** 2)
        light.size = size * 1.2
        light.color = colour
        ob = bpy.data.objects.new(name, light)
        scene.collection.objects.link(ob)
        d = Vector(direction).normalized()
        ob.location = centre + d * size * 3
        ob.rotation_euler = (centre - ob.location).to_track_quat('-Z', 'Y').to_euler()
    # a floor that catches the shadow
    bpy.ops.mesh.primitive_plane_add(size=size * 20, location=(centre.x, centre.y, centre.z - size * 0.55))
    floor = bpy.context.active_object
    fm = bpy.data.materials.new('floor')
    try:
        fm.use_nodes = True
    except (AttributeError, TypeError):
        pass
    b = next(n for n in fm.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    b.inputs['Base Color'].default_value = (0.18, 0.19, 0.2, 1)
    b.inputs['Roughness'].default_value = 0.7
    floor.data.materials.append(fm)


def bounds(objects):
    lo = Vector((1e9, 1e9, 1e9)); hi = Vector((-1e9, -1e9, -1e9))
    for ob in objects:
        for c in ob.bound_box:
            w = ob.matrix_world @ Vector(c)
            lo = Vector((min(lo.x, w.x), min(lo.y, w.y), min(lo.z, w.z)))
            hi = Vector((max(hi.x, w.x), max(hi.y, w.y), max(hi.z, w.z)))
    return lo, hi


def render(scene, out, centre, size, views):
    cam_data = bpy.data.cameras.new('camera')
    cam_data.lens = 50
    cam = bpy.data.objects.new('camera', cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    for i, d in enumerate(views):
        direction = Vector(d).normalized()
        cam.location = centre + direction * size * 2.2
        cam.rotation_euler = (centre - cam.location).to_track_quat('-Z', 'Y').to_euler()
        scene.render.filepath = out if len(views) == 1 else out.replace('.png', '_%d.png' % i)
        bpy.ops.render.render(write_still=True)
        common.log('rendered', scene.render.filepath)


def main():
    args = common.args()
    if len(args) < 2:
        raise SystemExit('review.py <model.obj | kit> <out.png>')
    source, out = args[0], args[1]
    common.reset()
    scene = common.cycles(samples=48)
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y = 1280, 860
    scene.view_settings.view_transform = 'AgX' if 'AgX' in [v.identifier for v in scene.view_settings.bl_rna.properties['view_transform'].enum_items] else 'Filmic'
    # a kit: 'kit' (the detail kit) or 'kit:vehicle', 'kit:interior' (0.12)
    metres = source == 'kit' or source.startswith('kit:')
    objects = []
    if metres:
        kit = os.path.join(common.ASSETS, 'models', 'part', source.split(':', 1)[1] if ':' in source else 'detail')
        names = sorted(f for f in os.listdir(kit) if f.endswith('.obj'))
        cols = 7
        for k, name in enumerate(names):
            before = set(bpy.data.objects)
            bpy.ops.wm.obj_import(filepath=os.path.join(kit, name), forward_axis='NEGATIVE_Z', up_axis='Y')
            new = [o for o in bpy.data.objects if o not in before]
            for o in new:
                if o.name.startswith('lod1'):
                    bpy.data.objects.remove(o)
                    continue
                lo, hi = bounds([o])
                s = 0.8 / max((hi - lo).length, 1e-6)
                o.scale = (s, s, s)
                r, c = divmod(k, cols)
                o.location = (c * 1.0 - (cols - 1) / 2, 0, -r * 1.0)
                objects.append(o)
        views = [(0.0, -1.0, 0.25)]
    else:
        bpy.ops.wm.obj_import(filepath=source, forward_axis='NEGATIVE_Z', up_axis='Y')
        objects = list(bpy.context.selected_objects)
        views = [(-0.9, -1.2, 0.7), (1.1, -0.8, 0.45), (0.0, -1.0, 0.1)]
    for ob in objects:
        for slot in ob.material_slots:
            if slot.material is not None:
                shade(slot.material, metres)
    bpy.context.view_layer.update()
    lo, hi = bounds(objects)
    centre, size = (lo + hi) / 2, max((hi - lo).length * 0.5, 1e-3)
    studio(scene, centre, size)
    render(scene, out, centre, size * (1.1 if metres else 1.0), views)


main()
