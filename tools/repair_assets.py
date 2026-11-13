"""hdfv 0.25: the assets, data and words of repairs (core repair/RepairJob, RepairRules, FieldRepair; neoforge repair/HdfvRepair),
written from one spec: the repair shop controller's blockstate (facing), its cube model (minecraft:block/orientable over the
room controllers' sides and top, a front of its own), item models, loot table and tool tag; the two field kits' item models
and 16 px icons; the 16 px source of the controller's front (into tools/blender/blocks16, redrawn at 128 px by
`scripts/blender.ps1 machines`); and both languages (new keys inserted after their anchors, the files' line order kept
alike). Files are rewritten only when they change. Recipes are tools/recipes.py's, the friction lining's assets
tools/components_assets.py's, the item masses tools/item_mass.py's.

    py -3 tools/repair_assets.py
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/blender.ps1 machines     (the 128 px front)
    py -3 tools/zh_cn.py                                                                 (the Simplified Chinese)
"""
import io
import json
import os
import sys

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
RES = os.path.join(ROOT, 'neoforge', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'hdfv')
DATA = os.path.join(RES, 'data')
BLOCKS16 = os.path.join(ROOT, 'tools', 'blender', 'blocks16')

written = 0

CONTROLLER = 'repair_shop_controller'
KITS = ['field_repair_kit', 'tyre_patch_kit']   # core FieldRepair.Kit


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


def add_tag(path, values):
    obj = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {'values': []}
    for v in values:
        if v not in obj['values']:
            obj['values'].append(v)
    write_json(path, obj)


# ------------------------------------------------------------------------------------------------ words

JEI_SHOP = (
    "The repair shop's controller: set in the wall of a closed room with a door, two work lights and power, as the refit shop's. Drive a "
    "damaged vehicle in, pick on its screen what to repair, and start. What is used less than 80 % is mended with stock and standard "
    "components (friction linings for brakes and clutches, bearings, wire and tube); tyres are always new; from 80 % a part is replaced by "
    "a new one of its exact design (an engine or a gearbox as a part item, a body or a chassis rebuilt in the room, a wheel's brakes or "
    "suspension and a clutch as new pieces made from the part's design). It takes what it needs from storage at the room, works on its "
    "power for the job's hours, and gives the old metal back. In a wreck the engines are repaired with the body or after it.",
    "修理廠的控制台：和改裝廠一樣，裝在有門、兩盞工作燈和電的封閉房間牆上。把受損的載具開進去，在畫面上勾選要修的項目，再按開工。"
    "用掉不到 80 % 的用料件與標準件修理（煞車與離合器用來令片，另有軸承、電線、鋼管）；輪胎一律換新；用掉 80 % 以上的零件換成同一設計的"
    "新零件（引擎、變速箱要成品零件，車體與底盤在房間裡重建，車輪的煞車、懸吊與離合器換成用零件設計做的新件）。材料從房間旁的倉庫取，"
    "用電施工到工時結束，拆下的舊金屬退回倉庫。全毀的車，引擎要和車體一起修，或在車體之後修。")
JEI_KIT = (
    "Repairs on the spot: look at the part and hold use on the vehicle (it stops if you look away or step back). A use mends the "
    "structure, the ancillaries or a tank of the nearest part it can by up to 15 % and never past half worn. Nothing used 80 % or more, "
    "and no brakes, clutch, gears, engine internals or suspension: those need a repair shop. 8 uses.",
    "就地急修：看著要修的零件，對載具按住使用鍵（移開視線或走開就會中止）。每用一次，替最近、修得了的零件修補結構、附件或油箱，"
    "最多 15 %，而且最多只修到用掉一半。用掉 80 % 以上的，以及煞車、離合器、齒輪、引擎內部、懸吊，都不能就地修，要到修理廠。可用 8 次。")
JEI_PATCH = (
    "Hold use on a vehicle by a flat tyre (a puncture or a burst): the tyre is plugged and pumped up to half its life, enough to reach a "
    "repair shop for new tyres. Worn tread cannot be patched. 4 uses.",
    "對著載具漏氣的輪胎（刺破或爆胎）按住使用鍵：輪胎塞補後打氣到剩一半，足以開到修理廠換新輪胎。胎紋磨損不能補。可用 4 次。")


def lang_groups():
    """(anchor key, [(key, en, zh)]) in the order they go in."""
    shop = [
        ('block.hdfv.repair_shop_controller', 'Repair Shop Controller', '修理廠控制台'),
        ('item.hdfv.field_repair_kit', 'Field Repair Kit', '急修工具包'),
        ('item.hdfv.tyre_patch_kit', 'Tyre Patch Kit', '補胎工具組'),
        ('hdfv.repair.kit.field_repair_kit.tip', 'Hold use on a vehicle: patches the structure, ancillaries or a tank of the part you look at, a step at a time, '
                                                 'no better than half worn (8 uses)',
         '對著載具按住使用鍵：修補你看著的零件的結構、附件或油箱，每次一小步，最多修到用掉一半（可用 8 次）'),
        ('hdfv.repair.kit.tyre_patch_kit.tip', 'Hold use on a vehicle by a flat tyre: plugs it and pumps it up to half (4 uses)',
         '對著載具漏氣的輪胎按住使用鍵：塞補並打氣到剩一半（可用 4 次）'),
        ('hdfv.repair_shop.all', 'All damaged', '所有損傷'),
        ('hdfv.repair_shop.none', 'None', '全不選'),
        ('hdfv.repair_shop.scan', 'Scan', '掃描'),
        ('hdfv.repair_shop.door', 'Door', '開關門'),
        ('hdfv.repair_shop.start', 'Start', '開工'),
        ('hdfv.repair_shop.cancel', 'Cancel', '取消'),
        ('hdfv.repair_shop.vehicle', 'Vehicle: %s', '載具：%s'),
        ('hdfv.repair_shop.kind.mend', 'mend', '修理'),
        ('hdfv.repair_shop.kind.mend.tip', 'Mended in place, back to new (%s): stock or standard components, and work',
         '就地修回全新（%s）：用料件或標準件，加上工時'),
        ('hdfv.repair_shop.kind.new_pieces', 'new pieces', '換新件'),
        ('hdfv.repair_shop.kind.new_pieces.tip', 'The worn pieces made new from the part\'s own design (%s): make them on the machines from its blueprint',
         '用零件自己的設計（%s）重做磨損的那幾件：拿它的藍圖上機器做'),
        ('hdfv.repair_shop.kind.new_part', 'new part', '換新零件'),
        ('hdfv.repair_shop.kind.new_part.tip', 'Replaced by a new part of its exact design (%s), taken from the stores',
         '換成同一設計（%s）的新零件，從倉庫取'),
        ('hdfv.repair_shop.kind.rebuild', 'rebuild', '重建'),
        ('hdfv.repair_shop.kind.rebuild.tip', 'Built again where it stands (%s): its stock and pieces, as an assembly room builds it',
         '原地重建（%s）：照組裝室的做法，用它的料件與工件'),
        ('hdfv.repair_shop.reason.worn_out', 'Used %s %% or more: past repair, it is replaced', '已用掉 %s %% 以上：不能修，只能更換'),
        ('hdfv.repair_shop.reason.never_mended', 'Worn tread is not mended: new tyres', '胎紋磨損不能修：換新輪胎'),
        ('hdfv.repair_shop.reason.with_part', 'New with its part\'s replacement', '隨整個零件更換而全新'),
        ('hdfv.repair_shop.job.time', '%s h of work (%s min), %s kWh', '工時 %s 小時（%s 分鐘），%s kWh'),
        ('hdfv.repair_shop.job.need', '%s × %s (have %s)', '%s × %s（現有 %s）'),
        ('hdfv.repair_shop.job.salvage', 'Back to the stores: %s', '退回倉庫：%s'),
        ('hdfv.repair_shop.job.none', 'Pick what to repair on the left', '在左邊勾選要修的項目'),
        ('hdfv.repair_shop.working', 'Working: %s %%', '施工中：%s %%'),
        ('hdfv.repair_shop.waiting_power', 'Waiting for power: %s %%', '等待電力：%s %%'),
        ('hdfv.repair_shop.energy', 'Energy: %s FE', '電量：%s FE'),
        ('hdfv.repair_shop.more', '… and %s more', '……還有 %s 項'),
        ('hdfv.repair_shop.done', 'Repaired: %s condition(s) as new', '修好了：%s 個項目恢復全新'),
        ('hdfv.repair_shop.cancelled', 'Cancelled', '已取消'),
        ('hdfv.repair_shop.left', 'The vehicle left the shop: the job stopped', '載具離開了修理廠：工作中止'),
        ('hdfv.repair_shop.busy', 'A job is going', '正在施工'),
        ('hdfv.repair_shop.missing', 'Missing in the stores at the room:', '房間旁的倉庫缺少：'),
        ('hdfv.repair_shop.no_such', 'This vehicle has no %s', '這台載具沒有 %s'),
        ('hdfv.repair_shop.problem.open', 'The shop is not closed: an opening near %s %s %s', '修理廠沒有封閉：%s %s %s 附近有開口'),
        ('hdfv.repair_shop.problem.no_door', 'The shop has no door', '修理廠沒有門'),
        ('hdfv.repair_shop.problem.few_lights', '%s of %s work lights', '工作燈 %s／%s 盞'),
        ('hdfv.repair_shop.problem.no_vehicle', 'No vehicle in the shop', '修理廠裡沒有載具'),
        ('hdfv.repair_shop.problem.vehicles', '%s vehicles in the shop: one at a time', '修理廠裡有 %s 台載具：一次只修一台'),
        ('hdfv.repair_shop.problem.building', 'The vehicle is still being built', '載具還在組裝中'),
        ('hdfv.repair_shop.problem.no_power', 'No power at the controller', '控制台沒有電'),
        ('hdfv.repair_shop.problem.moving', 'The vehicle is moving (%s m/s)', '載具在移動（%s m/s）'),
        ('hdfv.repair_shop.problem.riders', '%s aboard: everyone out first', '車上有 %s 名乘員：請先下車'),
        ('hdfv.repair_shop.problem.welding_station', 'The job welds: a welding station in the shop', '這項工作要焊接：修理廠裡要有焊接台'),
        ('hdfv.repair_shop.problem.nothing', 'Nothing picked to repair', '沒有勾選要修的項目'),
        ('hdfv.repair_shop.problem.wreck_first', '%s is a wreck: repair its structure with the engines or before them',
         '%s 已經全毀：要和引擎一起修它的結構，或先修它'),
    ]
    tab = [
        ('hdfv.status.tab.repair', 'Repair', '修理'),
        ('hdfv.repair.tab.row', '%s %s %%: %s', '%s 剩 %s %%：%s'),
        ('hdfv.repair.tab.field', 'on the spot with a %s (to %s %%)', '可用%s就地修到 %s %%'),
        ('hdfv.repair.tab.mend', 'repair shop: mend', '修理廠：修理'),
        ('hdfv.repair.tab.pieces', 'repair shop: new pieces', '修理廠：換新件'),
        ('hdfv.repair.tab.part', 'replace: a new %s', '更換：新的%s'),
        ('hdfv.repair.tab.rebuild', 'repair shop: rebuild', '修理廠：重建'),
        ('hdfv.repair.tab.nothing', 'Nothing is worn or damaged.', '沒有磨損或損壞的地方。'),
        ('hdfv.repair.tab.wreck', 'A wreck: repair the body or chassis with the engines or before them', '已經全毀：車體或底盤要和引擎一起修，或先修'),
    ]
    field = [
        ('hdfv.repair.field.done', '%s, %s: %s %% → %s %%', '%s的%s：%s %% → %s %%'),
        ('hdfv.repair.field.replace', '%s, %s: %s %% left, past repair on the spot: replace it in a repair shop', '%s的%s：只剩 %s %%，不能就地修，要到修理廠更換'),
        ('hdfv.repair.field.enough', '%s, %s: %s %% left, as good as a repair on the spot makes it', '%s的%s：剩 %s %%，就地修理只能修到這樣'),
        ('hdfv.repair.field.not_flat', '%s, %s: %s %% left and not flat: worn tread needs new tyres', '%s的%s：剩 %s %%，沒有漏氣：胎紋磨損要換新輪胎'),
        ('hdfv.repair.field.nothing', 'Nothing here a %s can work on', '這裡沒有%s修得了的東西'),
        ('hdfv.repair.field.away', 'Keep looking at the vehicle within reach: the repair stopped', '要一直看著伸手可及的載具：修理中止'),
    ]
    jei = [
        ('hdfv.jei.repair_shop',) + JEI_SHOP,
        ('hdfv.jei.field_repair_kit',) + JEI_KIT,
        ('hdfv.jei.tyre_patch_kit',) + JEI_PATCH,
    ]
    return [('hdfv.refit_shop.lamp.face.rear', shop), ('hdfv.status.nothing', tab), ('hdfv.service.no_reeds', field), ('hdfv.jei.refit_shop', jei)]


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


# ------------------------------------------------------------------------------------------------ pictures

def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = c


FRAME, EDGE, SCREEN, SCREEN_EDGE, KEY, KEY_LIT = rgb('#80858b'), rgb('#404245'), rgb('#10161c'), rgb('#44474b'), rgb('#6b6f74'), rgb('#c9ced3')
ORANGE, STEEL, BRIGHT, GREEN, RED = rgb('#d0782a'), rgb('#a8aeb4'), rgb('#d9dde1'), rgb('#6fd16b'), rgb('#d84a3a')


def controller_front():
    """The room controllers' face (car_factory_controller_front's layout): an orange header, a screen with a wrench on it, keys and two lamps."""
    im = Image.new('RGBA', (16, 16), FRAME)
    px = im.load()
    for i in range(16):
        px[15, i] = EDGE
        px[i, 15] = EDGE
    rect(px, 1, 1, 14, 2, ORANGE)
    rect(px, 1, 3, 1, 14, SCREEN_EDGE)
    rect(px, 2, 3, 13, 3, shade(SCREEN_EDGE, 1.05))
    rect(px, 14, 3, 14, 10, rgb('#282a2c'))
    rect(px, 2, 4, 13, 9, SCREEN)
    rect(px, 2, 10, 13, 10, rgb('#252729'))
    # a combination spanner across the screen: a ring on the left, the handle, an open jaw on the right
    rect(px, 2, 5, 4, 8, BRIGHT)
    rect(px, 3, 6, 3, 7, SCREEN)
    rect(px, 5, 6, 10, 7, BRIGHT)
    rect(px, 11, 5, 13, 8, BRIGHT)
    rect(px, 12, 6, 13, 7, SCREEN)
    for x in range(2, 14):
        if px[x, 8] == BRIGHT:
            px[x, 8] = STEEL
    rect(px, 5, 7, 10, 7, STEEL)
    # the keys and the lamps
    rect(px, 2, 11, 13, 14, KEY)
    for x in (3, 5, 7, 9, 11):
        px[x, 12] = KEY_LIT
        px[x, 13] = shade(KEY, 0.45)
    px[12, 14] = GREEN
    px[13, 14] = RED
    return im


def field_repair_kit():
    """A red steel toolbox: its lid a shade darker, a handle on top, a latch, a wrench tied on its side."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    red, dark, handle = rgb('#b8312a'), rgb('#7e1f1a'), rgb('#3b3f45')
    rect(px, 5, 2, 10, 2, handle)
    px[5, 3] = handle
    px[10, 3] = handle
    rect(px, 1, 4, 14, 6, dark)
    rect(px, 1, 7, 14, 14, red)
    rect(px, 14, 7, 14, 14, shade(red, 0.78))
    rect(px, 1, 14, 14, 14, shade(red, 0.78))
    rect(px, 7, 6, 8, 8, STEEL)
    for x, y in ((3, 12), (4, 11), (5, 10), (6, 10), (2, 13)):
        px[x, y] = BRIGHT
    return im


def tyre_patch_kit():
    """A small tyre on its rim, a T-handled plug tool stuck into its tread from above and a plug strip in its eye."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    rubber, tread, plug, handle = rgb('#2f2f33'), rgb('#18181a'), rgb('#c8a24a'), rgb('#d84a3a')
    cx, cy = 9.5, 9.5
    for y in range(16):
        for x in range(16):
            r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 3.6 <= r <= 6.4:
                px[x, y] = tread if r > 5.6 else rubber
            elif 1.5 <= r < 3.6:
                px[x, y] = STEEL if x + y < 20 else shade(STEEL, 0.75)
    rect(px, 1, 1, 7, 2, handle)
    rect(px, 1, 2, 7, 2, shade(handle, 0.75))
    for y in range(3, 7):
        px[4, y] = BRIGHT if y % 2 else STEEL
    px[3, 4] = plug
    px[5, 5] = plug
    return im


# ------------------------------------------------------------------------------------------------ data

def main():
    facings = (('north', 0), ('east', 90), ('south', 180), ('west', 270))
    variants = {}
    for f, y in facings:
        m = {'model': 'hdfv:block/' + CONTROLLER}
        if y:
            m['y'] = y
        variants['facing=' + f] = m
    write_json(os.path.join(ASSETS, 'blockstates', CONTROLLER + '.json'), {'variants': variants})
    write_json(os.path.join(ASSETS, 'models', 'block', CONTROLLER + '.json'), {'parent': 'minecraft:block/orientable', 'textures': {
        'top': 'hdfv:block/room_controller_top', 'front': 'hdfv:block/' + CONTROLLER + '_front', 'side': 'hdfv:block/room_controller_side'}})
    write_json(os.path.join(ASSETS, 'models', 'item', CONTROLLER + '.json'), {'parent': 'hdfv:block/' + CONTROLLER})
    write_png(os.path.join(BLOCKS16, CONTROLLER + '_front.png'), controller_front())
    write_json(os.path.join(DATA, 'hdfv', 'loot_table', 'blocks', CONTROLLER + '.json'), {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hdfv:' + CONTROLLER}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), ['hdfv:' + CONTROLLER])
    for k, draw in (('field_repair_kit', field_repair_kit), ('tyre_patch_kit', tyre_patch_kit)):
        write_json(os.path.join(ASSETS, 'models', 'item', k + '.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'hdfv:item/' + k}})
        write_png(os.path.join(ASSETS, 'textures', 'item', k + '.png'), draw())
    insert_lang(os.path.join(ASSETS, 'lang', 'en_us.json'), 'en')
    insert_lang(os.path.join(ASSETS, 'lang', 'zh_tw.json'), 'zh')
    print('repair assets:', written, 'files written')


if __name__ == '__main__':
    main()
