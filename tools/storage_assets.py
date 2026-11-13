"""hdfv 0.25: the assets and data of the storage blocks (core StorageKind, neoforge storage/HdfvStorage), written from one
spec: blockstates (the front turned to its facing), cube models (minecraft:block/orientable: a front, the other sides,
top and bottom alike), item models, loot tables (the block, keeping its name as a chest does; its contents are dropped
by the block), the tool tags (an axe for the crate, a pickaxe for the steel ones), the item tag of what the parts rack
takes (hdfv:parts_rack), the 16 px sources of their textures (into tools/blender/blocks16, redrawn at 128 px by
`scripts/blender.ps1 machines`) and both languages (new keys inserted after their anchor, the files' line order kept
alike). Files are rewritten only when they change. Recipes are tools/recipes.py's, the masses tools/item_mass.py's.
Checked by StorageAssetsTest.

    py -3 tools/storage_assets.py
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/blender.ps1 machines     (the 128 px textures)
    py -3 tools/zh_cn.py                                                                 (the Simplified Chinese)
"""
import io
import json
import os
import random
import sys

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
RES = os.path.join(ROOT, 'neoforge', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'hdfv')
DATA = os.path.join(RES, 'data')
BLOCKS16 = os.path.join(ROOT, 'tools', 'blender', 'blocks16')

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


def write_png(path, im):
    if os.path.exists(path):
        try:
            if Image.open(path).convert('RGBA').tobytes() == im.tobytes():
                return
        except OSError:
            pass
    b = io.BytesIO()
    im.save(b, 'PNG', optimize=True)
    write(path, b.getvalue())


# ------------------------------------------------------------------------------------------------ the spec (as core StorageKind)

# id, slots, wooden, English, Chinese, the JEI line (English, Chinese)
STORES = [
    ('storage_crate', 27, True, 'Storage Crate', '儲物木箱',
     'A nailed wooden crate holding 27 stacks. Its screen shows how many slots are used and what its contents weigh. Hoppers and pipes fill and empty '
     'it from any side, and an assembly room, the refit shop or the paint booth takes its materials from one at the room as from a chest. Broken, it '
     'drops what it holds.',
     '釘成的木箱，可放 27 組物品。畫面上會顯示已用格數與內容物的重量。漏斗和管線可以從任何一面放入、取出；組裝室、改裝廠與烤漆房會像用箱子一樣'
     '從房間旁的木箱取料。打掉時裡面的東西會掉出來。'),
    ('steel_locker', 54, False, 'Steel Locker', '鋼製置物櫃',
     'A steel cabinet holding 54 stacks, twice a crate, in one block. Hoppers, pipes and the rooms use it as they use a crate.',
     '鋼板做的櫃子，一格方塊可放 54 組物品，是木箱的兩倍。漏斗、管線和各種廠房用它的方式和木箱一樣。'),
    ('parts_rack', 36, False, 'Parts Rack', '零件架',
     'A rack of 36 slots for the workshop\'s own things only: finished parts, workpieces, standard components, bench assemblies and drawings (the '
     'item tag hdfv:parts_rack). A hopper cannot put anything else in it either, so it sorts parts out of a mixed line.',
     '36 格的架子，只收工坊自己的東西：成品零件、工件、標準件、組裝件和圖紙（物品標籤 hdfv:parts_rack）。漏斗也放不進別的東西，'
     '所以可以用它從混在一起的輸送線裡把零件分出來。'),
    ('shipping_container', 108, False, 'Shipping Container', '貨櫃',
     'A steel container holding 108 stacks on two pages: turn them with the buttons at the top right of its screen or with the mouse wheel. '
     'Hoppers, pipes and the rooms reach every slot.',
     '鋼製貨櫃，可放 108 組物品，分成兩頁：用畫面右上角的按鈕或滑鼠滾輪換頁。漏斗、管線和各種廠房每一格都拿得到。'),
]
FACINGS = (('north', 0), ('east', 90), ('south', 180), ('west', 270))
# what the parts rack takes (the standard components and the bench assemblies are read from tools/recipes.py's tables)
RACK_ITEMS = ['part', 'workpiece', 'blueprint', 'blank_blueprint', 'vehicle_blueprint']


def ids():
    return [s[0] for s in STORES]


def lang_groups():
    """(anchor key, [(key, en, zh)]) in the order they go in."""
    blocks = [('block.hdfv.' + s[0], s[3], s[4]) for s in STORES]
    info = [('hdfv.jei.' + s[0], s[5], s[6]) for s in STORES]
    screen = [
        ('hdfv.storage.footer', '%s/%s · %s kg', '%s/%s · %s kg'),
        ('hdfv.storage.footer.limit', '%s/%s · %s / %s kg', '%s/%s · %s / %s kg'),
        ('hdfv.storage.page', '%s/%s', '%s/%s'),
        ('hdfv.storage.mass', '%s kg', '%s kg'),
        ('hdfv.storage.mass.stack', '%s × %s kg = %s kg', '%s × %s kg = %s kg'),
    ]
    config = [
        ('hdfv.configuration.storage', 'Storage', '儲物'),
        ('hdfv.configuration.itemMassScale', 'Item mass scale', '物品重量倍率'),
        ('hdfv.configuration.bucketLitres', 'Litres in a bucket', '一桶的公升數'),
    ]
    return [('block.hdfv.concrete_ramp_8', blocks), ('hdfv.fluid.can_use', screen), ('hdfv.jei.road_ramp', info), ('hdfv.configuration.welcome', config)]


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


def block_drop(id_):
    """The block itself, its custom name kept (a chest's table); the contents are dropped by the block when it goes."""
    return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{
        'type': 'minecraft:item', 'name': 'hdfv:' + id_,
        'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:custom_name']}]}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}


def add_tag(path, values):
    obj = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {'values': []}
    for v in values:
        if v not in obj['values']:
            obj['values'].append(v)
    write_json(path, obj)


# ------------------------------------------------------------------------------------------------ the 16 px sources

def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


def canvas(base, seed, amount):
    """A flat colour with a little variation a pixel."""
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16), base)
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(base, 1 + rnd.uniform(-amount, amount))
    return im


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = c


def frame(px, c, x0=0, y0=0, x1=15, y1=15):
    for i in range(x0, x1 + 1):
        px[i, y0] = px[i, y1] = c
    for i in range(y0, y1 + 1):
        px[x0, i] = px[x1, i] = c


WOOD, WOOD_SEAM, BATTEN, BATTEN_EDGE, NAIL = rgb('#b48c56'), rgb('#876539'), rgb('#9c7745'), rgb('#6b4f2c'), rgb('#4e5257')


def crate(seed, vertical=False, brace=False, latch=False):
    """Boards (a seam every 4 px), a batten frame nailed at its corners, and a diagonal brace or a latch and a label."""
    im = canvas(WOOD, seed, 0.05)
    px = im.load()
    for i in range(16):
        for j in (3, 7, 11):
            x, y = (j, i) if vertical else (i, j)
            px[x, y] = shade(WOOD_SEAM, 1.0 if (i * 7 + j) % 5 else 1.1)
    if brace:
        for i in range(2, 14):
            for d in (0, 1):
                if 2 <= i + d <= 13:
                    px[i, i + d] = shade(BATTEN, 0.97 if d else 1.04)
    for k in range(2):
        frame(px, BATTEN if k == 0 else shade(BATTEN, 0.93), k, k, 15 - k, 15 - k)
    for i in range(16):
        px[i, 15] = px[15, i] = BATTEN_EDGE
        px[i, 2] = shade(px[i, 2], 0.9) if 2 <= i <= 13 else px[i, 2]
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[x, y] = NAIL
    if latch:
        rect(px, 7, 2, 8, 5, rgb('#565b61'))
        px[7, 5] = px[8, 5] = rgb('#33373b')
        rect(px, 5, 8, 10, 11, rgb('#e2d9bf'))
        for x in (6, 7, 8, 9):
            px[x, 9] = rgb('#6d6552')
        for x in (6, 7, 8):
            px[x, 10] = rgb('#8a8168')
    return im


LOCKER, LOCKER_DARK, LOCKER_LIGHT, GAP = rgb('#6c7f93'), rgb('#4d5c6c'), rgb('#8799ab'), rgb('#262b31')


def locker(seed, doors=False, top=False):
    """Painted sheet steel: a folded rim; the front two doors with louvres, card holders and handles at the middle."""
    im = canvas(LOCKER, seed, 0.025)
    px = im.load()
    frame(px, LOCKER_DARK)
    for i in range(1, 15):
        px[i, 1] = px[1, i] = LOCKER_LIGHT
    if doors:
        rect(px, 7, 1, 8, 14, GAP)
        for x0 in (2, 10):
            for y in (3, 5):
                rect(px, x0, y, x0 + 3, y, LOCKER_DARK)
                rect(px, x0, y + 1, x0 + 3, y + 1, LOCKER_LIGHT)
            rect(px, x0 + (0 if x0 < 8 else 1), 8, x0 + (2 if x0 < 8 else 3), 9, rgb('#d9dde1'))
            px[x0 + 1, 9] = px[x0 + 2, 9] = rgb('#8d949b')
        for x in (6, 9):
            rect(px, x, 10, x, 12, rgb('#b4bbc2'))
            px[x, 13] = rgb('#3b424a')
        rect(px, 1, 14, 14, 14, LOCKER_DARK)
    elif top:
        rect(px, 3, 3, 12, 12, shade(LOCKER, 0.95))
        frame(px, LOCKER_DARK, 3, 3, 12, 12)
    else:
        frame(px, shade(LOCKER, 0.9), 3, 3, 12, 12)
        for i in range(4, 12):
            px[i, 4] = px[4, i] = shade(LOCKER, 1.08)
    return im


RACK, RACK_DARK, RACK_BACK, SHELF = rgb('#737b83'), rgb('#4a5158'), rgb('#2b3035'), rgb('#9aa2aa')


def rack_front(seed):
    """Open shelving: slotted uprights, four shelves, and what lies on them (bins, a gear, a shaft, rolled drawings)."""
    im = canvas(RACK_BACK, seed, 0.04)
    px = im.load()
    for x in (0, 15):
        for y in range(16):
            px[x, y] = RACK if y % 2 else RACK_DARK
    for y in (0, 5, 10, 15):
        rect(px, 0, y, 15, y, SHELF)
        if y < 15:
            rect(px, 1, y + 1, 14, y + 1, shade(RACK_BACK, 0.7))
    # the top shelf: two bins and rolled drawings
    rect(px, 2, 2, 5, 4, rgb('#3f6fa8'))
    rect(px, 2, 2, 5, 2, rgb('#5587c2'))
    rect(px, 7, 3, 9, 4, rgb('#b5532f'))
    rect(px, 7, 3, 9, 3, rgb('#d06a40'))
    for x in (11, 12, 13):
        rect(px, x, 2, x, 4, rgb('#dfe6ee') if x != 12 else rgb('#7fa3cf'))
    # the middle shelf: a gear, a shaft, a bronze bushing
    rect(px, 2, 7, 5, 9, rgb('#8d949b'))
    px[2, 7] = px[5, 7] = RACK_BACK
    px[3, 8] = px[4, 8] = rgb('#3a3f45')
    rect(px, 7, 8, 12, 8, rgb('#c3c9cf'))
    rect(px, 7, 9, 12, 9, rgb('#868d94'))
    rect(px, 13, 8, 14, 9, rgb('#b07a3c'))
    # the bottom shelf: a casting and a crate of fasteners
    rect(px, 2, 12, 7, 14, rgb('#5f6266'))
    rect(px, 3, 12, 6, 12, rgb('#787c81'))
    rect(px, 9, 12, 13, 14, rgb('#9c7745'))
    rect(px, 9, 13, 13, 13, rgb('#6b4f2c'))
    return im


def rack_side(seed):
    """The rack's end: two slotted uprights, a sheet between them and its cross braces."""
    im = canvas(shade(RACK, 0.82), seed, 0.03)
    px = im.load()
    for i in range(16):
        for d in (0,):
            px[i, i + d] = RACK_DARK
            px[15 - i, i + d] = RACK_DARK
    for x in (0, 1, 14, 15):
        for y in range(16):
            px[x, y] = RACK if x in (1, 14) else shade(RACK, 1.1)
    for x in (1, 14):
        for y in range(1, 16, 3):
            px[x, y] = RACK_BACK
    rect(px, 0, 0, 15, 0, SHELF)
    rect(px, 0, 15, 15, 15, RACK_DARK)
    return im


def rack_top(seed):
    im = canvas(SHELF, seed, 0.03)
    px = im.load()
    frame(px, RACK_DARK)
    for x in range(3, 13, 3):
        for y in range(3, 13, 3):
            px[x, y] = shade(SHELF, 0.8)
    return im


BOX, BOX_DARK, BOX_LIGHT, ROD = rgb('#9a4a2f'), rgb('#6e3220'), rgb('#b5603f'), rgb('#b9bfc5')


def container(seed, doors=False, roof=False):
    """Corten corrugations between a top and a bottom rail and corner posts; the front its doors, four lock rods and their handles."""
    im = canvas(BOX, seed, 0.03)
    px = im.load()
    for y in range(16):
        for x in range(16):
            k = (y if roof else x) % 4
            px[x, y] = shade(px[x, y], 1.12 if k == 0 else 0.84 if k == 2 else 1.0)
    if roof:
        frame(px, BOX_DARK)
        for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
            rect(px, x, y, x + 1, y + 1, rgb('#3c4046'))
        return im
    rect(px, 0, 0, 15, 1, BOX_DARK)
    rect(px, 0, 14, 15, 15, BOX_DARK)
    rect(px, 0, 0, 0, 15, BOX_DARK)
    rect(px, 15, 0, 15, 15, BOX_DARK)
    rect(px, 1, 1, 14, 1, BOX_LIGHT)
    if doors:
        rect(px, 1, 2, 14, 13, shade(BOX, 0.97))
        rect(px, 7, 2, 8, 13, BOX_DARK)
        for x in (3, 5, 10, 12):
            rect(px, x, 2, x, 13, ROD)
            px[x, 2] = px[x, 13] = rgb('#4b5056')
        for x0 in (3, 10):
            rect(px, x0, 8, x0 + 2, 8, rgb('#dfe3e7'))
            px[x0 + 1, 9] = rgb('#4b5056')
        for y in (3, 12):
            px[1, y] = px[14, y] = rgb('#3c4046')
        rect(px, 2, 4, 2, 5, rgb('#e6e1c8'))
    return im


def textures():
    out = {
        'storage_crate_front': crate(701, latch=True), 'storage_crate_side': crate(702, brace=True), 'storage_crate_top': crate(703, vertical=True),
        'steel_locker_front': locker(711, doors=True), 'steel_locker_side': locker(712), 'steel_locker_top': locker(713, top=True),
        'parts_rack_front': rack_front(721), 'parts_rack_side': rack_side(722), 'parts_rack_top': rack_top(723),
        'shipping_container_front': container(731, doors=True), 'shipping_container_side': container(732), 'shipping_container_top': container(733, roof=True),
    }
    for name, im in out.items():
        write_png(os.path.join(BLOCKS16, name + '.png'), im)
    return len(out)


# ------------------------------------------------------------------------------------------------ main

def rack_tag():
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import recipes
    assemblies = ['electric_motor_unit', 'transformer', 'control_board', 'servo_motor', 'pump_unit', 'hydraulic_cylinder', 'gear_train', 'heat_exchanger']
    for a in assemblies:
        if a not in recipes.INTERMEDIATE_KG:
            sys.exit('not an intermediate: ' + a)
    return ['hdfv:' + i for i in RACK_ITEMS + list(recipes.COMPONENT_KG) + assemblies]


def main():
    blockstates, models, items = os.path.join(ASSETS, 'blockstates'), os.path.join(ASSETS, 'models', 'block'), os.path.join(ASSETS, 'models', 'item')
    loot = os.path.join(DATA, 'hdfv', 'loot_table', 'blocks')
    for id_, slots, wooden, en, zh, info_en, info_zh in STORES:
        write_json(os.path.join(models, id_ + '.json'), {'parent': 'minecraft:block/orientable', 'textures': {
            'top': 'hdfv:block/%s_top' % id_, 'front': 'hdfv:block/%s_front' % id_, 'side': 'hdfv:block/%s_side' % id_}})
        variants = {}
        for facing, y in FACINGS:
            m = {'model': 'hdfv:block/' + id_}
            if y:
                m['y'] = y
            variants['facing=' + facing] = m
        write_json(os.path.join(blockstates, id_ + '.json'), {'variants': variants})
        write_json(os.path.join(items, id_ + '.json'), {'parent': 'hdfv:block/' + id_})
        write_json(os.path.join(loot, id_ + '.json'), block_drop(id_))
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'axe.json'), ['hdfv:' + s[0] for s in STORES if s[2]])
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), ['hdfv:' + s[0] for s in STORES if not s[2]])
    write_json(os.path.join(DATA, 'hdfv', 'tags', 'item', 'parts_rack.json'), {'values': rack_tag()})
    n = textures()
    insert_lang(os.path.join(ASSETS, 'lang', 'en_us.json'), 'en')
    insert_lang(os.path.join(ASSETS, 'lang', 'zh_tw.json'), 'zh')
    print('storage assets:', len(STORES), 'blocks,', n, '16 px sources,', written, 'files written')


if __name__ == '__main__':
    main()
