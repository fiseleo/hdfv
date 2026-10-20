"""Shared helpers for hdfv's Blender scripts (hdfv 0.11).

Run through scripts/blender.ps1 (Blender in the background, factory settings). Everything here is deterministic:
fixed noise coordinates and seeds, PNGs written by our own encoder (no time stamps or metadata), files written only
when their bytes change, so running a script again without changes leaves git clean.
"""
import json
import math
import os
import struct
import sys
import zlib

import bpy

ROOT = os.environ.get('HDFV_ROOT') or os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'neoforge', 'src', 'main', 'resources', 'assets', 'hdfv')
TOOLS = os.path.join(ROOT, 'tools', 'blender')


def log(*parts):
    print('hdfv', *parts, flush=True)


def args():
    """The arguments after '--' on Blender's command line."""
    return sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def cycles(samples=16):
    """Cycles on the CPU (baking works on any machine), a few samples per pixel so fine patterns are antialiased."""
    import addon_utils
    addon_utils.enable('cycles', default_set=True)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = samples
    scene.cycles.use_denoising = False
    return scene


def write_if_changed(path, data):
    """Writes bytes unless the file already holds exactly them; returns whether it wrote."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path):
        with open(path, 'rb') as f:
            if f.read() == data:
                return False
    with open(path, 'wb') as f:
        f.write(data)
    return True


def png_rgba(width, height, rgba):
    """A PNG (8-bit RGBA, no filter, no metadata) from a flat sequence of 0-255 values, rows top to bottom."""
    raw = bytearray()
    stride = 4 * width
    for y in range(height):
        raw.append(0)
        raw.extend(rgba[y * stride:(y + 1) * stride])

    def chunk(tag, body):
        return struct.pack('>I', len(body)) + tag + body + struct.pack('>I', zlib.crc32(tag + body) & 0xffffffff)

    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))


def write_json_if_changed(path, value):
    text = json.dumps(value, indent=2, ensure_ascii=False, sort_keys=False) + '\n'
    return write_if_changed(path, text.replace('\r\n', '\n').encode('utf-8'))


# ------------------------------------------------------------------------------------------------ material nodes

def new_material(name):
    mat = bpy.data.materials.new(name)
    try:
        mat.use_nodes = True   # always on (and deprecated) in Blender 5
    except (AttributeError, TypeError):
        pass
    mat.node_tree.nodes.clear()
    return mat


def node(tree, kind, **props):
    n = tree.nodes.new(kind)
    for k, v in props.items():
        setattr(n, k, v)
    return n


def link(tree, out_socket, in_socket):
    tree.links.new(out_socket, in_socket)


def math_node(tree, op, a, b=None):
    """A math node: a and b are sockets or numbers."""
    n = node(tree, 'ShaderNodeMath', operation=op)
    for i, v in enumerate((a, b)):
        if v is None:
            continue
        if isinstance(v, (int, float)):
            n.inputs[i].default_value = v
        else:
            link(tree, v, n.inputs[i])
    return n.outputs[0]


def torus(tree, uv_socket, fu, fv):
    """
    Seamless 4D coordinates of a unit UV square: u and v each go once round a circle whose circumference is the
    number of noise units across the tile (fu along u, fv along v), so noise sampled there tiles in both directions.
    Returns (vector socket, w socket).
    """
    sep = node(tree, 'ShaderNodeSeparateXYZ')
    link(tree, uv_socket, sep.inputs[0])
    au = math_node(tree, 'MULTIPLY', sep.outputs[0], 2 * math.pi)
    av = math_node(tree, 'MULTIPLY', sep.outputs[1], 2 * math.pi)
    ru, rv = fu / (2 * math.pi), fv / (2 * math.pi)
    x = math_node(tree, 'MULTIPLY', math_node(tree, 'COSINE', au), ru)
    y = math_node(tree, 'MULTIPLY', math_node(tree, 'SINE', au), ru)
    z = math_node(tree, 'MULTIPLY', math_node(tree, 'COSINE', av), rv)
    w = math_node(tree, 'MULTIPLY', math_node(tree, 'SINE', av), rv)
    comb = node(tree, 'ShaderNodeCombineXYZ')
    link(tree, x, comb.inputs[0])
    link(tree, y, comb.inputs[1])
    link(tree, z, comb.inputs[2])
    return comb.outputs[0], w


def channel(tree, uv_socket, spec):
    """
    One grey channel from a spec: ('noise', fu, fv, detail, roughness, distortion, offset) is 4D fractal noise;
    ('voronoi', fu, fv, randomness, feature, offset) a 4D cell pattern's distance (F1 or DISTANCE_TO_EDGE). The
    offset moves the sample (different patterns from the same frequencies). Returns the output socket.
    """
    kind = spec[0]
    if kind == 'noise':
        _, fu, fv, detail, roughness, distortion, offset = spec
        vec, w = torus(tree, uv_socket, fu, fv)
        n = node(tree, 'ShaderNodeTexNoise')
        n.noise_dimensions = '4D'
        try:
            n.normalize = True
        except AttributeError:
            pass
        link(tree, vec, n.inputs['Vector'])
        link(tree, math_node(tree, 'ADD', w, offset), n.inputs['W'])
        n.inputs['Scale'].default_value = 1.0
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = roughness
        n.inputs['Distortion'].default_value = distortion
        return n.outputs['Fac']
    if kind == 'voronoi':
        _, fu, fv, randomness, feature, offset = spec
        vec, w = torus(tree, uv_socket, fu, fv)
        n = node(tree, 'ShaderNodeTexVoronoi')
        n.voronoi_dimensions = '4D'
        n.feature = feature
        link(tree, vec, n.inputs['Vector'])
        link(tree, math_node(tree, 'ADD', w, offset), n.inputs['W'])
        n.inputs['Scale'].default_value = 1.0
        n.inputs['Randomness'].default_value = randomness
        return n.outputs['Distance']
    raise ValueError('unknown channel ' + str(kind))


_plane = None


def bake_channels(specs, size):
    """
    Bakes up to three grey channels (specs as for channel) into the red, green and blue of a float image of a size
    (Cycles, emission), on a unit plane whose UV square is the tile. Returns a list of per-channel lists of
    size*size floats, rows from the top.
    """
    global _plane
    scene = cycles()
    if _plane is None or _plane.name not in bpy.data.objects:
        bpy.ops.mesh.primitive_plane_add(size=1)
        _plane = bpy.context.active_object
    obj = _plane
    mat = new_material('bake')
    tree = mat.node_tree
    coord = node(tree, 'ShaderNodeTexCoord')
    combine = node(tree, 'ShaderNodeCombineColor')
    for i, spec in enumerate(specs[:3]):
        link(tree, channel(tree, coord.outputs['UV'], spec), combine.inputs[i])
    emission = node(tree, 'ShaderNodeEmission')
    link(tree, combine.outputs[0], emission.inputs['Color'])
    emission.inputs['Strength'].default_value = 1.0
    output = node(tree, 'ShaderNodeOutputMaterial')
    link(tree, emission.outputs[0], output.inputs['Surface'])
    image = bpy.data.images.new('bake', size, size, alpha=False, float_buffer=True)
    try:
        image.colorspace_settings.name = 'Non-Color'
    except TypeError:
        pass
    target = node(tree, 'ShaderNodeTexImage')
    target.image = image
    tree.nodes.active = target
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    scene.render.bake.margin = 0
    bpy.ops.object.bake(type='EMIT', margin=0, use_clear=True)
    pixels = list(image.pixels[:])   # rows from the bottom, RGBA floats
    out = [[0.0] * (size * size) for _ in specs[:3]]
    for row in range(size):
        src = (size - 1 - row) * size * 4
        for col in range(size):
            for i in range(len(specs[:3])):
                out[i][row * size + col] = pixels[src + col * 4 + i]
    bpy.data.images.remove(image)
    bpy.data.materials.remove(mat)
    return out
