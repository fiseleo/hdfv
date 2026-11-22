"""hdfv 0.26 (heavy machinery): the assets, data and names of what the machines bring, written from one spec:

- the worksite blocks (neoforge worksite/HdfvWorksite): loose fill, blasted rock, compacted base, hot asphalt, fresh
  concrete, reclaimed asphalt, pile, tunnel lining: blockstates, models, item models, loot tables, tool tags, the 16 px
  sources of their textures (into tools/blender/blocks16, redrawn at 128 px by `scripts/blender.ps1 machines`);
- the earth tags data/hdfv/tags/block/earth/*.json (EarthMaterials reads them before vanilla's and the common tags;
  packs and other mods extend them);
- both languages (new keys inserted after their anchor, the files' line order kept alike): the blocks, the work keys
  and messages (drive/WorkInputs, client/DriveControls), the client setting, and the design screen's names of the new
  part types (tools/machinery_lang_parts.py, tools/machinery_lang_tools.py, when present).

Files are rewritten only when they change.

    py -3 tools/machinery_assets.py
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/blender.ps1 machines     (the 128 px textures)
    py -3 tools/zh_cn.py                                                                 (the Simplified Chinese)
"""
import importlib
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import storage_assets as sa  # noqa: E402  (its writers, lang insertion and 16 px helpers)

from PIL import Image  # noqa: E402

# id, English, Chinese, tool (shovel / pickaxe), drop (None: itself, '': nothing, else the id it drops)
BLOCKS = [
    ('loose_fill', 'Loose Fill', '鬆土', 'shovel', None),
    ('blasted_rock', 'Blasted Rock', '爆破碎石', 'pickaxe', None),
    ('compacted_base', 'Compacted Base', '壓實路基', 'shovel', 'loose_fill'),
    ('hot_asphalt', 'Hot Asphalt Mat', '熱瀝青鋪層', 'shovel', 'reclaimed_asphalt'),
    ('fresh_concrete', 'Fresh Concrete', '新鮮混凝土', 'shovel', ''),
    ('reclaimed_asphalt', 'Reclaimed Asphalt', '刨除料', 'shovel', None),
    ('pile', 'Driven Pile', '基樁', 'pickaxe', None),
    ('tunnel_lining', 'Tunnel Lining Segment', '隧道襯砌環片', 'pickaxe', None),
]

JEI = {
    'loose_fill': ('Soil a machine has dug and dumped: lighter to dig again than the bank it came from, it settles '
                   'and slides like sand. Rolled, it becomes compacted base.',
                   '機器挖起又倒下的土：比原本的實方好挖，會像沙一樣沉落、滑動。碾壓後成為壓實路基。'),
    'blasted_rock': ('Broken rock: a bucket digs it, which it cannot do to solid stone (that takes a ripper, a hammer, '
                     'a cutter drum or a mining shovel\'s teeth).',
                     '破碎的岩石：鏟斗挖得動，整塊的石頭挖不動（要用鬆土器、液壓錘、刨刀鼓或採礦電鏟的斗齒）。'),
    'compacted_base': ('Fill a roller has passed over enough times: the base a road or an apron is laid on.',
                       '壓路機碾壓夠多遍的填土：道路或機坪鋪在上面的路基。'),
    'hot_asphalt': ('Hot mix as a paver lays it. It cools in the air; rolled enough while still hot it is asphalt '
                    'pavement, cold before that it crumbles to reclaimed asphalt.',
                    '鋪築機剛鋪下的熱拌料。在空氣中會變冷；趁熱碾壓夠多遍就成為瀝青路面，碾壓前就冷掉則碎成刨除料。'),
    'fresh_concrete': ('Concrete as a pump or a chute places it. It sets over the hours and becomes concrete pavement; '
                       'until then it can still be worked.',
                       '泵浦車或滑槽澆置的混凝土。經過幾個小時凝結後成為混凝土鋪面；在那之前還能整平。'),
    'reclaimed_asphalt': ('Milled-off pavement, or hot mix that went cold unrolled: a cold planer\'s load, back to the '
                          'asphalt plant.',
                          '刨除的舊路面，或沒碾壓就冷掉的熱拌料：冷刨機刨下的料，送回瀝青拌合廠。'),
    'pile': ('A pile a pile hammer has driven: each blow\'s set follows the Hiley formula from the hammer\'s energy '
             'and the ground.',
             '打樁機打入的基樁：每一擊的貫入量由錘的能量與地層照 Hiley 公式算。'),
    'tunnel_lining': ('The ring of segments a tunnel boring machine builds behind its cutterhead.',
                      '隧道鑽掘機在刀盤後方組立的環片。'),
}

# the earth tags (EarthMaterials): hdfv's own first; vanilla's and the common ones are read after them
EARTH = {
    'soil': ['minecraft:moss_block', 'minecraft:rooted_dirt', 'minecraft:farmland', 'minecraft:dirt_path'],
    'sand': ['minecraft:suspicious_sand'],
    'gravel': ['minecraft:suspicious_gravel'],
    'clay': ['minecraft:clay'],
    'mud': ['minecraft:mud', 'minecraft:muddy_mangrove_roots', 'minecraft:packed_mud'],
    'snow': ['minecraft:snow', 'minecraft:snow_block', 'minecraft:powder_snow'],
    'soft_rock': ['minecraft:calcite', 'minecraft:tuff', 'minecraft:dripstone_block', 'minecraft:netherrack', 'minecraft:soul_soil'],
    'hard_rock': ['minecraft:basalt', 'minecraft:smooth_basalt', 'minecraft:blackstone', 'minecraft:end_stone'],
    'broken_rock': ['hdfv:blasted_rock', 'minecraft:cobblestone', 'minecraft:cobbled_deepslate', 'minecraft:mossy_cobblestone'],
    'ore': [],
    'undiggable': ['minecraft:obsidian', 'minecraft:crying_obsidian', 'minecraft:reinforced_deepslate', 'minecraft:respawn_anchor'],
}

FUNCTIONS = [
    ('boom', 'Boom', '動臂'), ('lift', 'Lift arm', '舉升臂'), ('stick', 'Stick', '斗桿'), ('bucket', 'Bucket', '鏟斗'), ('swing', 'Swing', '迴轉'),
    ('blade_lift', 'Blade lift', '推土鏟升降'), ('blade_tilt', 'Blade tilt', '推土鏟傾斜'), ('blade_angle', 'Blade angle', '推土鏟角度'),
    ('ripper', 'Ripper', '鬆土器'), ('hoist', 'Hoist', '吊鉤升降'), ('telescope', 'Telescope', '伸縮臂'), ('mast', 'Mast', '門架'),
    ('tilt', 'Tilt', '前後傾'), ('dump', 'Dump body', '車斗舉升'), ('aux', 'Attachment', '附件'), ('rotate', 'Rotate', '旋轉'),
    ('outriggers', 'Outriggers', '支腿'), ('jib', 'Jib', '副臂'), ('bowl', 'Bowl', '鏟斗升降'), ('apron', 'Apron', '斗門'),
    ('ejector', 'Ejector', '推板'), ('screed', 'Screed / drum height', '熨平板／刨刀鼓高度'), ('chute', 'Chute', '滑槽'),
    ('erector', 'Segment erector', '環片組立機'), ('cutter', 'Cutter drive', '刀具驅動'), ('conveyor', 'Conveyor', '輸送帶'),
    ('vibration', 'Vibration', '振動'), ('spray', 'Spray', '噴灑'), ('hammer', 'Hammer', '破碎錘'), ('broom', 'Broom', '掃刷'),
    ('mixer', 'Mixer drum', '攪拌筒'), ('pump', 'Concrete pump', '混凝土泵'), ('thrust', 'Thrust cylinders', '推進油缸'), ('jacks', 'Outrigger jacks', '支腿千斤頂'), ('hook', 'Hook latch', '吊鉤掛放'),
]


def lang_groups():
    """(anchor key, [(key, en, zh)]) in the order they go in."""
    blocks = [('block.hdfv.' + i, en, zh) for i, en, zh, _, _ in BLOCKS]
    jei = [('hdfv.jei.' + i, JEI[i][0], JEI[i][1]) for i, *_ in BLOCKS]
    keys = [
        ('key.hdfv.work_mode', 'Drive / work mode (machines)', '行駛／作業模式（機械）'),
        ('key.hdfv.work_stick_forward', 'Right joystick forward', '右搖桿往前'),
        ('key.hdfv.work_stick_back', 'Right joystick back', '右搖桿往後'),
        ('key.hdfv.work_stick_left', 'Right joystick left', '右搖桿往左'),
        ('key.hdfv.work_stick_right', 'Right joystick right', '右搖桿往右'),
        ('key.hdfv.work_raise', 'Picked function up / on', '所選功能上升／開'),
        ('key.hdfv.work_lower', 'Picked function down / off', '所選功能下降／關'),
        ('key.hdfv.work_select', 'Pick the next function', '選下一個功能'),
        ('key.hdfv.work_float', 'Blade float', '推土鏟浮動'),
    ]
    messages = [
        ('hdfv.work.mode_work', 'Work mode: the movement keys and the arrows are the joysticks', '作業模式：移動鍵與方向鍵是搖桿'),
        ('hdfv.work.mode_drive', 'Drive mode', '行駛模式'),
        ('hdfv.work.selected', 'R / F: %s', 'R／F：%s'),
        ('hdfv.work.on', '%s on', '%s 開'),
        ('hdfv.work.off', '%s off', '%s 關'),
        ('hdfv.work.nothing_to_select', 'Nothing else to pick: the joysticks have every function', '沒有別的功能可選：搖桿已經控制全部功能'),
        ('hdfv.work.no_float', 'This machine has no blade that floats', '這台機器沒有能浮動的推土鏟'),
        ('hdfv.work.float_on', 'Blade float on', '推土鏟浮動 開'),
        ('hdfv.work.float_off', 'Blade float off', '推土鏟浮動 關'),
    ] + [('hdfv.work.function.' + c, en, zh) for c, en, zh in FUNCTIONS]
    workpieces = [('hdfv.workpiece.motor_housing', 'Motor housing', '馬達殼體'), ('hdfv.workpiece.swing_reducer', 'Swing reducer housing', '迴轉減速機殼體')]
    config = [('hdfv.configuration.joystickPattern', 'Excavator joystick pattern', '挖土機搖桿操作方式')]
    yard_block = [('block.hdfv.heavy_yard_controller', 'Heavy Yard Controller', '重機組裝場控制台'),
                  ('block.hdfv.asphalt_plant_controller', 'Asphalt Plant Controller', '瀝青拌合廠控制台'),
                  ('block.hdfv.concrete_plant_controller', 'Concrete Plant Controller', '混凝土拌合廠控制台'),
                  ('block.hdfv.tower_crane_controller', 'Tower Crane Controller', '塔式起重機控制台'),
                  ('block.hdfv.tower_crane_mast', 'Tower Crane Mast Section', '塔吊標準節'),
                  ('block.hdfv.tower_crane_top', 'Tower Crane Slewing Unit', '塔吊迴轉機構')]
    plant = [
        ('hdfv.plant.asphalt_status', 'Asphalt plant: %s m³ of hot mix in the silo, %s batches; %s', '瀝青拌合廠：料倉有 %s m³ 熱拌料，已拌 %s 批；%s'),
        ('hdfv.plant.concrete_status', 'Concrete plant: %s m³ of concrete in the silo, %s batches; %s', '混凝土拌合廠：料倉有 %s m³ 混凝土，已拌 %s 批；%s'),
        ('hdfv.plant.working', 'working', '運轉中'),
        ('hdfv.plant.not_formed', 'not built: see its handbook page', '結構未完成：見手冊'),
        ('hdfv.plant.no_power', 'no power', '沒有電力'),
        ('hdfv.plant.no_aggregate', 'needs 14 aggregate (gravel, sand, cobblestone, blasted rock, reclaimed asphalt) a batch', '每批需要 14 個骨材（礫石、沙、圓石、爆破碎石、刨除料）'),
        ('hdfv.plant.no_bitumen', 'needs a bitumen a batch', '每批需要 1 個瀝青'),
        ('hdfv.plant.no_fuel', "needs coal, charcoal or coke for the drum's burner", '烘乾滾筒的燃燒器需要煤、木炭或焦炭'),
        ('hdfv.plant.no_premix', 'needs 15 concrete powder a batch', '每批需要 15 個混凝土粉'),
        ('hdfv.plant.no_water', "needs water: fill the cauldron, or use a water bucket on the controller", '需要水：把鍋釜加滿，或對控制台使用水桶'),
        ('hdfv.tower_crane.status', 'Tower crane: trolley at %s m, %s t there, slewed %s°; %s', '塔吊：小車在 %s m，該處可吊 %s t，迴轉 %s°；%s'),
        ('hdfv.tower_crane.working', 'ready: ride its cab and press the work mode key', '就緒：坐進駕駛室，按作業模式鍵'),
        ('hdfv.tower_crane.not_formed', 'not built: a base, 4 to 60 mast sections and the slewing unit', '結構未完成：底座、4～60 節標準節與迴轉機構'),
        ('hdfv.tower_crane.no_power', 'no power', '沒有電力'),
        ('hdfv.tower_crane.wind', 'stopped: the wind at the jib is too strong', '停止：吊臂高度的風太強'),
        ('hdfv.tower_crane.limit', 'load moment limiter: no hoisting up or trolley out', '力矩限制器作動：不能再起升或外伸'),
    ]
    tower_jei = [('hdfv.jei.tower_crane_controller', 'A flat-top tower crane: a 3 x 3 base of smooth stone, stone or apron concrete with this controller in the '
                  'middle of its front, 4 to 60 mast sections stacked over the middle of the base, the slewing unit on top. Powered by cables (40 kW). '
                  'Formed, its cab sits at the top: ride it, press the work mode key, and the sticks slew the jib, run the trolley and hoist; the hook latch '
                  'takes what is under the hook. It lifts 6 t close in and 1.5 t at its 30 m tip; the load moment limiter stops it beyond the chart, and '
                  'it stops in strong wind.',
                  '平頭塔式起重機：3 × 3 的底座（平滑石、石頭或機坪混凝土），這個控制台放在底座正面中間；底座中央往上疊 4～60 節標準節，'
                  '最上面放迴轉機構。以電線供電（40 kW）。結構完成後駕駛室在頂端：坐進去按作業模式鍵，搖桿控制吊臂迴轉、小車變幅與起升；'
                  '吊鉤掛放會吊起吊鉤下方的東西。近處可吊 6 t，30 m 臂端 1.5 t；超過起重量表時力矩限制器會停止，風太大也會停止。')]
    plant_jei = [
        ('hdfv.jei.asphalt_plant_controller', 'A 3 x 3 x 3 multiblock of iron blocks: this controller in the middle of the bottom row\'s front, a blast furnace behind it '
         '(the drum\'s burner), a hopper above it (the discharge). Powered by cables (75 kW). A batch of 0.1 m³ of hot mix takes 14 aggregate, a bitumen '
         'and a coal every fourth batch; it is poured into the bulk hold of a vehicle standing in front of it. The mix cools in the truck: lay it soon.',
         '3 × 3 × 3 的鐵磚多方塊：這個控制台放在最下層正面中間，後面放高爐（烘乾滾筒的燃燒器），上面放漏斗（卸料口）。'
         '以電線供電（75 kW）。每批 0.1 m³ 熱拌料用 14 個骨材、1 個瀝青，每 4 批用 1 個煤；倒進停在前方的車輛的散料貨艙。熱拌料在車上會變冷，要盡快鋪。'),
        ('hdfv.jei.concrete_plant_controller', 'A 3 x 3 x 3 multiblock of iron blocks: this controller in the middle of the bottom row\'s front, a hopper above it '
         '(the discharge), a cauldron behind the hopper (the water). Powered by cables (30 kW). A batch of 0.1 m³ of concrete takes 15 concrete powder and '
         'a level of water; it is poured into a mixer drum or a body standing in front. Concrete sets after about four hours: pour it before.',
         '3 × 3 × 3 的鐵磚多方塊：這個控制台放在最下層正面中間，上面放漏斗（卸料口），漏斗後面放鍋釜（水）。以電線供電（30 kW）。'
         '每批 0.1 m³ 混凝土用 15 個混凝土粉與一格水；倒進停在前方的攪拌筒或車斗。混凝土大約四小時後初凝，要在那之前澆置。'),
    ]
    yard_room = [('hdfv.room.heavy_yard', 'a heavy yard', '重機組裝場')]
    yard_problems = [
        ('hdfv.room.problem.yard_floor', 'No yard: put assembly floor in front of the controller, open to the sky',
         '沒有組裝場：在控制台前方鋪組裝地坪，上方要露天'),
        ('hdfv.room.problem.yard_roof', '%s m² of the yard are under a roof: a heavy yard is open to the sky (gantry crane rails may run over it)',
         '組裝場有 %s m² 在屋頂下：重機組裝場要露天（上方可以有門式起重機的軌道）'),
        ('hdfv.room.problem.yard_crane', 'A %s kg part needs a gantry crane over the yard or a crane parked beside it (these lift %s kg)',
         '%s kg 的零件需要組裝場上方的門式起重機，或停在旁邊的起重機（現在能吊 %s kg）'),
    ]
    yard_jei = [('hdfv.jei.controller.heavy_yard', 'Set at the edge of an open yard of assembly floor, its front toward the yard. Scans the yard (the '
                 'floor joined to the cell in front, open to the sky); lays out a machine (tracks, drums, booms, work tools, hooks, hydraulics) '
                 'from part blueprints and builds it there. Heavy parts are lifted by gantry crane rails over the yard or by a crane vehicle '
                 'parked within 32 m (half its hook\'s rating counts: a rule). A machine of 10 t or less may also be built in a car factory.',
                 '放在露天組裝地坪的邊上，正面朝向組裝場。掃描組裝場（與正前方那格相連、上方露天的地坪）；用零件藍圖排出機械'
                 '（履帶、鋼輪、動臂、工作裝置、吊鉤、液壓）並在場上組裝。重零件由組裝場上方的門式起重機軌道，或停在 32 m 內的起重機吊'
                 '（算吊鉤額定的一半：規則）。10 噸以下的機械也能在車輛組裝室組裝。')]
    groups = [('block.hdfv.repair_shop_controller', blocks), ('hdfv.jei.road_ramp', jei), ('key.hdfv.atc_atis', keys),
              ('hdfv.drive.hazard_off', messages), ('hdfv.workpiece.track_shoe', workpieces), ('hdfv.configuration.autoClutch', config),
              ('block.hdfv.car_factory_controller', yard_block), ('hdfv.room.car_factory', yard_room), ('hdfv.room.problem.welding_station', plant),
              ('hdfv.jei.controller.car_factory', plant_jei + tower_jei),
              ('hdfv.room.problem.welding_station', yard_problems), ('hdfv.jei.controller.car_factory', yard_jei)]
    for module in ('machinery_lang_parts', 'machinery_lang_tools'):
        try:
            groups += importlib.import_module(module).groups()
        except ModuleNotFoundError:
            pass
    return groups


# ------------------------------------------------------------------------------------------------ the 16 px sources

def speckled(base, flecks, seed, amount=0.08, share=0.18):
    """A ground colour with flecks of others (soil, broken rock, mix)."""
    rnd = random.Random(seed)
    im = sa.canvas(sa.rgb(base), seed, amount)
    px = im.load()
    cols = [sa.rgb(c) for c in flecks]
    for y in range(16):
        for x in range(16):
            if rnd.random() < share:
                px[x, y] = sa.shade(rnd.choice(cols), 1 + rnd.uniform(-0.06, 0.06))
    return im


def rocks(seed):
    """Angular grey pieces with dark gaps between them."""
    rnd = random.Random(seed)
    im = sa.canvas(sa.rgb('#3d3b39'), seed, 0.1)
    px = im.load()
    for _ in range(14):
        x0, y0 = rnd.randrange(0, 14), rnd.randrange(0, 14)
        w, h = rnd.randrange(2, 6), rnd.randrange(2, 5)
        c = sa.shade(sa.rgb(rnd.choice(['#8c8a86', '#7a7874', '#9a958e', '#6f6d6a'])), 1 + rnd.uniform(-0.05, 0.05))
        for y in range(y0, min(16, y0 + h)):
            for x in range(x0, min(16, x0 + w - (y - y0) // 2)):
                px[x, y] = c
        px[min(15, x0), y0] = sa.shade(c, 1.2)
    return im


def segment(seed):
    """Precast segments: grey concrete, a joint every half block, bolt pockets."""
    im = sa.canvas(sa.rgb('#9a9893'), seed, 0.05)
    px = im.load()
    joint, pocket = sa.rgb('#5d5b58'), sa.rgb('#4a4846')
    for i in range(16):
        px[i, 0] = px[i, 8] = joint
    for x in (0, 7, 15):
        for y in range(16):
            if (y < 8) == (x != 7):
                px[x, y] = joint
    for x, y in ((3, 4), (11, 4), (3, 12), (11, 12)):
        sa.rect(px, x, y, x + 1, y, pocket)
    return im


def pile_top(seed):
    im = sa.canvas(sa.rgb('#8f8d88'), seed, 0.04)
    px = im.load()
    ring = sa.rgb('#5e5c58')
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 5.5 <= r < 6.6:
                px[x, y] = ring
            elif r >= 6.6:
                px[x, y] = sa.shade(sa.rgb('#6b5a45'), 1 + random.Random(seed + x * 31 + y).uniform(-0.08, 0.08))
    return im


def pile_side(seed):
    im = sa.canvas(sa.rgb('#8f8d88'), seed, 0.05)
    px = im.load()
    for y in (5, 11):
        for x in range(16):
            px[x, y] = sa.rgb('#77756f')
    return im


def wet(base, seed, gloss):
    """A smooth surface with a few light streaks (fresh concrete, a hot mat)."""
    rnd = random.Random(seed)
    im = sa.canvas(sa.rgb(base), seed, 0.04)
    px = im.load()
    for _ in range(6):
        y, x0 = rnd.randrange(16), rnd.randrange(12)
        for x in range(x0, min(16, x0 + rnd.randrange(3, 6))):
            px[x, y] = sa.shade(px[x, y], gloss)
    return im


def yard_front():
    """The car factory controller's face with a yellow and black header (the yard's colours)."""
    im = Image.open(os.path.join(sa.BLOCKS16, 'car_factory_controller_front.png')).convert('RGBA')
    px = im.load()
    header = px[8, 1]
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if abs(c[0] - header[0]) + abs(c[1] - header[1]) + abs(c[2] - header[2]) < 60 and y < 6:
                px[x, y] = sa.rgb('#e8b10e') if (x + y) % 4 < 2 else sa.rgb('#26231f')
    return im


def plant_front(colour):
    """The yard controller's face with a header of a plant's colour."""
    im = Image.open(os.path.join(sa.BLOCKS16, 'car_factory_controller_front.png')).convert('RGBA')
    px = im.load()
    header = px[8, 1]
    for y in range(16):
        for x in range(16):
            c = px[x, y]
            if abs(c[0] - header[0]) + abs(c[1] - header[1]) + abs(c[2] - header[2]) < 60 and y < 6:
                px[x, y] = sa.rgb(colour)
    return im


def textures():
    out = {
        'tower_crane_controller_front': plant_front('#e0a82e'),
        'asphalt_plant_controller_front': plant_front('#2b2b2b'),
        'concrete_plant_controller_front': plant_front('#9a9a95'),
        'heavy_yard_controller_front': yard_front(),
        'loose_fill': speckled('#7a5a3c', ['#5e4430', '#8d6a48', '#6b6158'], 761),
        'blasted_rock': rocks(762),
        'compacted_base': speckled('#8a7258', ['#7b6450', '#9a8266', '#6e6a62'], 763, amount=0.04, share=0.12),
        'hot_asphalt': wet('#2b2a29', 764, 1.6),
        'fresh_concrete': wet('#8e8c87', 765, 1.12),
        'reclaimed_asphalt': speckled('#33312f', ['#4a4744', '#25231f', '#6b665e'], 766, amount=0.1, share=0.3),
        'pile_top': pile_top(767),
        'pile_side': pile_side(768),
        'tunnel_lining': segment(769),
    }
    for name, im in out.items():
        sa.write_png(os.path.join(sa.BLOCKS16, name + '.png'), im)
    return len(out)


def main():
    blockstates, models, items = os.path.join(sa.ASSETS, 'blockstates'), os.path.join(sa.ASSETS, 'models', 'block'), os.path.join(sa.ASSETS, 'models', 'item')
    shovel, pickaxe = [], []
    for id_, en, zh, tool, drop in BLOCKS:
        if id_ == 'pile':
            sa.write_json(os.path.join(models, id_ + '.json'), {'parent': 'minecraft:block/cube_column', 'textures': {
                'end': 'hdfv:block/pile_top', 'side': 'hdfv:block/pile_side'}})
        else:
            sa.write_json(os.path.join(models, id_ + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'hdfv:block/' + id_}})
        sa.write_json(os.path.join(blockstates, id_ + '.json'), {'variants': {'': {'model': 'hdfv:block/' + id_}}})
        sa.write_json(os.path.join(items, id_ + '.json'), {'parent': 'hdfv:block/' + id_})
        if drop == '':
            loot = {'type': 'minecraft:block', 'pools': []}
        else:
            loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hdfv:' + (drop or id_)}],
                                                          'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
        sa.write_json(os.path.join(sa.DATA, 'hdfv', 'loot_table', 'blocks', id_ + '.json'), loot)
        (shovel if tool == 'shovel' else pickaxe).append('hdfv:' + id_)
    # the heavy yard's controller (assembly/HdfvAssembly makes one for each Room)
    yard = 'heavy_yard_controller'
    sa.write_json(os.path.join(models, yard + '.json'), {'parent': 'minecraft:block/orientable', 'textures': {
        'top': 'hdfv:block/room_controller_top', 'front': 'hdfv:block/%s_front' % yard, 'side': 'hdfv:block/room_controller_side'}})
    variants = {}
    for facing, y in sa.FACINGS:
        m = {'model': 'hdfv:block/' + yard}
        if y:
            m['y'] = y
        variants['facing=' + facing] = m
    sa.write_json(os.path.join(blockstates, yard + '.json'), {'variants': variants})
    sa.write_json(os.path.join(items, yard + '.json'), {'parent': 'hdfv:block/' + yard})
    sa.write_json(os.path.join(sa.DATA, 'hdfv', 'loot_table', 'blocks', yard + '.json'), {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hdfv:' + yard}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
        'random_sequence': 'hdfv:blocks/' + yard})
    pickaxe.append('hdfv:' + yard)
    for member in ('tower_crane_mast', 'tower_crane_top'):
        # their block models (OBJ: a lattice section, the slewing unit's base) come from tools/blender/crane_blocks.py
        sa.write_json(os.path.join(blockstates, member + '.json'), {'variants': {'': {'model': 'hdfv:block/' + member}}})
        sa.write_json(os.path.join(items, member + '.json'), {'parent': 'hdfv:block/' + member})
        sa.write_json(os.path.join(sa.DATA, 'hdfv', 'loot_table', 'blocks', member + '.json'), {'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hdfv:' + member}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': 'hdfv:blocks/' + member})
        pickaxe.append('hdfv:' + member)
    for plant_id in ('asphalt_plant_controller', 'concrete_plant_controller', 'tower_crane_controller'):
        sa.write_json(os.path.join(models, plant_id + '.json'), {'parent': 'minecraft:block/orientable', 'textures': {
            'top': 'hdfv:block/room_controller_top', 'front': 'hdfv:block/%s_front' % plant_id, 'side': 'hdfv:block/room_controller_side'}})
        pv = {}
        for facing, y in sa.FACINGS:
            m = {'model': 'hdfv:block/' + plant_id}
            if y:
                m['y'] = y
            pv['facing=' + facing] = m
        sa.write_json(os.path.join(blockstates, plant_id + '.json'), {'variants': pv})
        sa.write_json(os.path.join(items, plant_id + '.json'), {'parent': 'hdfv:block/' + plant_id})
        sa.write_json(os.path.join(sa.DATA, 'hdfv', 'loot_table', 'blocks', plant_id + '.json'), {'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hdfv:' + plant_id}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': 'hdfv:blocks/' + plant_id})
        pickaxe.append('hdfv:' + plant_id)
    sa.add_tag(os.path.join(sa.DATA, 'minecraft', 'tags', 'block', 'mineable', 'shovel.json'), shovel)
    sa.add_tag(os.path.join(sa.DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), pickaxe)
    for name, values in EARTH.items():
        sa.write_json(os.path.join(sa.DATA, 'hdfv', 'tags', 'block', 'earth', name + '.json'),
                      {'replace': False, 'values': [{'id': v, 'required': False} for v in values]})
    n = textures()
    sa.lang_groups = lang_groups   # the same insertion, these keys
    sa.insert_lang(os.path.join(sa.ASSETS, 'lang', 'en_us.json'), 'en')
    sa.insert_lang(os.path.join(sa.ASSETS, 'lang', 'zh_tw.json'), 'zh')
    print('machinery assets:', len(BLOCKS), 'blocks,', len(EARTH), 'earth tags,', n, '16 px sources,', sa.written, 'files written')


if __name__ == '__main__':
    main()
