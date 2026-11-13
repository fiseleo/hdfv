"""hdfv 0.25: the assets and data of the road ramp blocks (core RampShape, neoforge road/HdfvRoads), written from one
spec: a smooth wedge for each part of each gradient (OBJ, neoforge:obj as the machines' models; one set shared by the
materials, the pavement's own texture named by each block model), blockstates (the wedge turned to its facing), item
models, loot tables, tags (the pickaxe, AeroCore's smooth ground) and both languages (new keys inserted after their
anchor, the files' line order kept alike). Files are rewritten only when they change. Recipes are tools/recipes.py's.
Checked by RoadAssetsTest.

    py -3 tools/road_assets.py
    py -3 tools/zh_cn.py          (after it: the Simplified Chinese)
"""
import json
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
RES = os.path.join(ROOT, 'neoforge', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'hdfv')
DATA = os.path.join(RES, 'data')

written = 0


def write(path, data):
    global written
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and open(path, 'rb').read() == data:
        return
    open(path, 'wb').write(data)
    written += 1


def write_json(path, obj):
    write(path, (json.dumps(obj, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))


# ------------------------------------------------------------------------------------------------ the spec (as core RampShape)

GRADIENTS = [2, 4, 8]
# material, its texture, English, Chinese
MATERIALS = [
    ('asphalt', 'hdfv:block/asphalt', 'Asphalt', '柏油'),
    ('concrete', 'hdfv:block/apron_concrete', 'Concrete', '混凝土'),
]
# the part an item shows: the one ending at half a block, so the three gradients show their slopes at one size
ITEM_PART = {2: 0, 4: 1, 8: 3}
FACINGS = (('north', 0), ('east', 90), ('south', 180), ('west', 270))


def ramp_id(material, gradient):
    return '%s_ramp_%d' % (material, gradient)


def ids():
    return [ramp_id(m[0], g) for m in MATERIALS for g in GRADIENTS]


def lang_groups():
    """(anchor key, [(key, en, zh)]) in the order they go in."""
    blocks = []
    for material, tex, en, zh in MATERIALS:
        for g in GRADIENTS:
            blocks.append(('block.hdfv.' + ramp_id(material, g), '%s Ramp (1:%d)' % (en, g), '%s坡道（1:%d）' % (zh, g)))
    info = [('hdfv.jei.road_ramp',
             'A road ramp: it rises one block over 2, 4 or 8 blocks of run (its name says which), a block a piece. Put the first piece down looking '
             'uphill; each piece put against the last continues the slope by itself, and a piece put a block higher beyond the top starts the next '
             'flight. Wheels and feet go up it as a smooth slope.',
             '路面坡道：每 2、4 或 8 格的水平距離升高一格（看名稱），一格一段。第一段朝上坡的方向放；接著貼著上一段放，就會自動接成下一段坡面；'
             '在坡頂前方、高一格的位置再放，會開始下一層。車輪和腳都能順著坡面上去。')]
    return [('block.hdfv.tower_floor', blocks), ('hdfv.jei.room_blocks', info)]


def insert_lang(path, lang):
    """Inserts the new keys after their anchors (the value of a key already there is set, the order kept)."""
    lines = open(path, encoding='utf-8').read().split('\n')
    idx = 1 if lang == 'en' else 2
    keys = {}
    for i, l in enumerate(lines):
        s = l.strip()
        if s.startswith('"') and '":' in s:
            keys[s[1:s.index('":')]] = i
    for anchor, entries in lang_groups():
        for key, *vals in entries:
            value = json.dumps(vals[idx - 1], ensure_ascii=False)
            if key in keys:
                i = keys[key]
                comma = ',' if lines[i].rstrip().endswith(',') else ''
                lines[i] = '  "%s": %s%s' % (key, value, comma)
                anchor = key
                continue
            if anchor not in keys:
                sys.exit('no anchor ' + anchor + ' in ' + path)
            a = keys[anchor]
            if not lines[a].rstrip().endswith(','):
                lines[a] = lines[a].rstrip() + ','
            lines.insert(a + 1, '  "%s": %s,' % (key, value))
            keys = {k: (v + 1 if v > a else v) for k, v in keys.items()}
            keys[key] = a + 1
            anchor = key
    close = max(i for i, l in enumerate(lines) if l.strip() == '}')
    last = max(i for i in range(close) if lines[i].strip())
    lines[last] = lines[last].rstrip().rstrip(',')
    out = '\n'.join(lines)
    json.loads(out)
    write(path, out.encode('utf-8'))


# ------------------------------------------------------------------------------------------------ the wedges

def wedge(gradient, part):
    """A part's wedge as an OBJ: rising toward north (-z) from part / gradient at z = 1 to (part + 1) / gradient at
    z = 0, the faces wound outward, the texture laid as a cube's (the top by x and z, the sides by their height)."""
    h0, h1 = part / gradient, (part + 1) / gradient
    verts, uvs, normals, faces = [], [], [], []

    def face(points, normal):
        normals.append(normal)
        refs = []
        for (x, y, z), (u, v) in points:
            verts.append((x, y, z))
            uvs.append((u, v))
            refs.append('%d/%d/%d' % (len(verts), len(uvs), len(normals)))
        faces.append('f ' + ' '.join(refs))

    slope = (h1 - h0) / (1 + (h1 - h0) ** 2) ** 0.5
    up = 1 / (1 + (h1 - h0) ** 2) ** 0.5
    # the slope, then the bottom, the high end (north), the low end (south, from part 1 on), west and east
    face([((0, h0, 1), (0, 0)), ((1, h0, 1), (1, 0)), ((1, h1, 0), (1, 1)), ((0, h1, 0), (0, 1))], (0, up, slope))
    face([((0, 0, 0), (0, 0)), ((1, 0, 0), (1, 0)), ((1, 0, 1), (1, 1)), ((0, 0, 1), (0, 1))], (0, -1, 0))
    face([((1, 0, 0), (0, 0)), ((0, 0, 0), (1, 0)), ((0, h1, 0), (1, h1)), ((1, h1, 0), (0, h1))], (0, 0, -1))
    if h0 > 0:
        face([((0, 0, 1), (0, 0)), ((1, 0, 1), (1, 0)), ((1, h0, 1), (1, h0)), ((0, h0, 1), (0, h0))], (0, 0, 1))
        face([((0, 0, 0), (0, 0)), ((0, 0, 1), (1, 0)), ((0, h0, 1), (1, h0)), ((0, h1, 0), (0, h1))], (-1, 0, 0))
        face([((1, 0, 1), (0, 0)), ((1, 0, 0), (1, 0)), ((1, h1, 0), (1, h1)), ((1, h0, 1), (0, h0))], (1, 0, 0))
    else:
        face([((0, 0, 0), (0, 0)), ((0, 0, 1), (1, 0)), ((0, h1, 0), (0, h1))], (-1, 0, 0))
        face([((1, 0, 1), (0, 0)), ((1, 0, 0), (1, 0)), ((1, h1, 0), (1, h1))], (1, 0, 0))
    out = ['mtllib ramp.mtl', 'o ramp_%d_%d' % (gradient, part)]
    out += ['v %.6f %.6f %.6f' % v for v in verts]
    out += ['vt %.6f %.6f' % t for t in uvs]
    out += ['vn %.6f %.6f %.6f' % n for n in normals]
    out += ['usemtl surface', 's off'] + faces
    return '\n'.join(out) + '\n'


MTL = 'newmtl surface\nKa 0.000000 0.000000 0.000000\nKd 1.000000 1.000000 1.000000\nd 1.000000\nmap_Kd #surface\n'


def block_drop(id_):
    return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hdfv:' + id_}],
                                                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}


def add_tag(path, values):
    obj = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {'values': []}
    for v in values:
        if v not in obj['values']:
            obj['values'].append(v)
    write_json(path, obj)


def main():
    blockstates, models, items = os.path.join(ASSETS, 'blockstates'), os.path.join(ASSETS, 'models', 'block'), os.path.join(ASSETS, 'models', 'item')
    loot = os.path.join(DATA, 'hdfv', 'loot_table', 'blocks')
    write(os.path.join(models, 'ramp', 'ramp.mtl'), MTL.encode('utf-8'))
    for g in GRADIENTS:
        for k in range(g):
            write(os.path.join(models, 'ramp', 'ramp_%d_%d.obj' % (g, k)), wedge(g, k).encode('utf-8'))
    for material, tex, en, zh in MATERIALS:
        for g in GRADIENTS:
            id_ = ramp_id(material, g)
            variants = {}
            for k in range(g):
                write_json(os.path.join(models, '%s_%d.json' % (id_, k)), {
                    'parent': 'minecraft:block/block', 'loader': 'neoforge:obj', 'model': 'hdfv:models/block/ramp/ramp_%d_%d.obj' % (g, k),
                    'flip_v': True, 'textures': {'particle': tex, 'surface': tex}})
                for facing, y in FACINGS:
                    m = {'model': 'hdfv:block/%s_%d' % (id_, k)}
                    if y:
                        m['y'] = y
                    variants['facing=%s,part=%d' % (facing, k)] = m
            write_json(os.path.join(blockstates, id_ + '.json'), {'variants': variants})
            write_json(os.path.join(items, id_ + '.json'), {'parent': 'hdfv:block/%s_%d' % (id_, ITEM_PART[g])})
            write_json(os.path.join(loot, id_ + '.json'), block_drop(id_))
    all_ids = ['hdfv:' + i for i in ids()]
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), all_ids)
    add_tag(os.path.join(DATA, 'aerocore', 'tags', 'block', 'smooth.json'), all_ids)
    insert_lang(os.path.join(ASSETS, 'lang', 'en_us.json'), 'en')
    insert_lang(os.path.join(ASSETS, 'lang', 'zh_tw.json'), 'zh')
    print('road assets:', len(all_ids), 'blocks,', written, 'files written')


if __name__ == '__main__':
    main()
