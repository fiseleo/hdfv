"""hdfv 0.24: the new-player tutorial's data: the advancements under data/hdfv/advancement/tutorial (one tab, "High
Degree of Freedom Vehicles"), and the tutorial's words in both languages (the advancements' titles and descriptions,
the handbook's item and screen, the welcome on first joining, the server options, the new JEI pages), inserted after
their group's anchor as machining_assets.py does. The handbook's pages are written by hand under
assets/hdfv/handbook/<language>; zh_cn is made from zh_tw by tools/zh_cn.py. Files are rewritten only when they change;
advancement files this does not write are deleted.

    py -3 tools/tutorial.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import machining_assets as ma  # noqa: E402  (write_json, insert_lang, ASSETS, DATA)

ASSETS, DATA = ma.ASSETS, ma.DATA
FOLDER = os.path.join(DATA, 'hdfv', 'advancement', 'tutorial')

RAW_ORES = ['hdfv:bauxite', 'hdfv:raw_ilmenite', 'hdfv:raw_nickel', 'hdfv:chromite', 'hdfv:magnesite', 'hdfv:raw_tin', 'hdfv:raw_zinc', 'hdfv:raw_garnet']
GRADE_A = ['hdfv:steel_plate', 'hdfv:electrical_steel', 'hdfv:copper_wire', 'hdfv:bed_casting', 'hdfv:frame_casting', 'hdfv:cylinder_casting',
           'hdfv:cast_gear', 'hdfv:cast_pulley', 'hdfv:bronze_bushing', 'hdfv:forged_shaft', 'hdfv:forged_crank']


def has(*items):
    """Having any of the items."""
    return {'has': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': list(items) if len(items) > 1 else items[0]}]}}}


def has_each(*items):
    """Having every one of the items (one criterion each)."""
    return {'has_' + i.split(':')[1]: {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': i}]}} for i in items}


def milestone(event, kind=None, at_least=None):
    c = {'event': event}
    if kind is not None:
        c['kind'] = kind
    if at_least is not None:
        c['at_least'] = at_least
    return {event: {'trigger': 'hdfv:milestone', 'conditions': c}}


# path, parent, icon, frame, criteria, English (title, description), Chinese (title, description) [, hidden]
ADVANCEMENTS = [
    ('root', None, 'hdfv:handbook', 'task', has('hdfv:handbook', 'hdfv:design_table', 'hdfv:design_tablet', *RAW_ORES),
     ('High Degree of Freedom Vehicles', 'Design every part, machine it, build vehicles for land, sea and air. The handbook shows the way.'),
     ('高自由度載具', '每個零件自己設計、自己加工，造出陸海空的載具。手冊會帶你走。')),
    # the main line: ores to the first car
    ('ores', 'root', 'hdfv:bauxite', 'task', has(*RAW_ORES),
     ('New Minerals', 'Mine one of the new ores: bauxite, ilmenite, nickel, chromite, magnesite, tin, zinc or garnet.'),
     ('新的礦物', '挖到一種新礦：鋁土礦、鈦鐵礦、鎳、鉻鐵礦、菱鎂礦、錫、鋅或石榴石。')),
    ('alloy_furnace', 'ores', 'hdfv:alloy_furnace', 'task', has('hdfv:alloy_furnace'),
     ('Fire and Coke', 'Build an alloy furnace: coke from coal, steel from iron and coke.'),
     ('火與焦炭', '做一座合金爐：煤煉成焦炭，鐵加焦炭煉成鋼。')),
    ('steel', 'alloy_furnace', 'hdfv:stock', 'task', has('hdfv:stock'),
     ('Stock', 'Get your first stock: the metal the workshop cuts, an ingot of it is 10 kg.'),
     ('料件', '拿到第一份料件：工坊加工用的金屬，一錠 10 公斤。')),
    ('foundry', 'steel', 'hdfv:foundry', 'task', has_each('hdfv:foundry', 'hdfv:forging_press'),
     ('Cast and Forged', 'Build a foundry and a forging press: the first standard components are castings and forgings.'),
     ('鑄與鍛', '做出鑄造爐和鍛壓機：第一批標準件是鑄件和鍛件。')),
    ('assembly_bench', 'foundry', 'hdfv:assembly_bench', 'task', has('hdfv:assembly_bench'),
     ('The Fitter\'s Bench', 'Build an assembly bench: machines are fitted together from standard components there.'),
     ('鉗工的組裝桌', '做出組裝桌：機器在這裡用標準件組起來。')),
    ('component', 'assembly_bench', 'hdfv:bed_casting', 'task', has(*GRADE_A),
     ('Standard Parts', 'Make a grade A standard component: save its drawing, cast or forge it from stock.'),
     ('標準件', '做出一個 A 級標準件：存好它的圖紙，用料件鑄造或鍛造。')),
    ('treadle', 'assembly_bench', 'hdfv:treadle', 'task', milestone('pedalled'),
     ('Leg Power', 'Turn a line shaft with a treadle: about 75 W, enough to learn on.'),
     ('人力', '用踏板轉動天軸：大約 75 W，學東西夠用了。')),
    ('lathe', 'component', 'hdfv:lathe', 'task', milestone('formed', 'lathe'),
     ('The First Lathe', 'Form a lathe: its drive, a bed of one to six metres, the headstock on the drive.'),
     ('第一台車床', '讓車床成形：驅動座、一到六公尺的床身、床頭座在驅動座上。')),
    ('machined', 'lathe', 'hdfv:workpiece', 'task', milestone('machined'),
     ('Swarf', 'Take your first machined work out of a machine.'),
     ('切屑', '從機器拿出第一件加工好的東西。')),
    ('steam_engine', 'treadle', 'hdfv:steam_engine', 'task', has('hdfv:steam_engine'),
     ('Steam Power', 'Build a mill steam engine: 15 kW on the line shaft from coal, charcoal, coke or wood.'),
     ('蒸汽動力', '做出蒸汽機：燒煤、木炭、焦炭或木頭，在天軸上給 15 kW。')),
    ('electricity', 'steam_engine', 'hdfv:generator', 'task', has('hdfv:generator'),
     ('Current', 'Build a generator: the line shaft turns it, cables carry Forge Energy.'),
     ('電', '做出發電機：天軸帶動它，電線送出 Forge Energy。')),
    ('arc_furnace', 'electricity', 'hdfv:arc_furnace', 'task', has('hdfv:arc_furnace'),
     ('Electric Steel', 'Build an arc furnace: alloy and stainless steels, superalloys, titanium.'),
     ('電爐鋼', '做出電弧爐：合金鋼、不鏽鋼、超合金、鈦。')),
    ('aluminium', 'arc_furnace', 'hdfv:aluminium_ingot', 'task', has('hdfv:aluminium_ingot'),
     ('Light Metal', 'Win aluminium in the electrolysis cell: alumina, a carbon anode and a lot of electricity.'),
     ('輕金屬', '在電解槽煉出鋁：氧化鋁、碳陽極和大量的電。')),
    ('precision', 'machined', 'hdfv:cut_gear', 'task', has('hdfv:cut_gear'),
     ('Finer Work', 'Make a cut gear: grade C components need the milling machine, the grinders and the gear machines.'),
     ('更精細', '做出切削齒輪：C 級標準件要銑床、磨床和齒輪機。')),
    ('cnc', 'precision', 'hdfv:cnc_machining_centre', 'goal', milestone('formed', 'cnc_machining_centre'),
     ('Numerical Control', 'Form a CNC: the only machine for blades, injector orifices and cooling holes.'),
     ('數控', '讓 CNC 成形：葉片、噴油嘴孔、冷卻孔只有它做得出來。')),
    ('design', 'root', 'hdfv:blueprint', 'task', milestone('design_saved'),
     ('On the Drawing Board', 'Save a design onto a blueprint in the design screen.'),
     ('畫圖', '在設計畫面把一份設計存成藍圖。')),
    ('engine_design', 'design', 'hdfv:design_tablet', 'task', milestone('design_saved', 'engine'),
     ('Engine Designer', 'Save an engine design: piston, jet, turbofan, turboprop, turboshaft, ramjet, pulsejet or rocket.'),
     ('引擎設計師', '存一份引擎設計：活塞、渦噴、渦扇、渦槳、渦軸、衝壓、脈衝噴射或火箭。')),
    ('engine_stand', 'engine_design', 'hdfv:engine_stand', 'task', milestone('engine_running'),
     ('It Runs!', 'Start an engine you built on an engine stand.'),
     ('發動了！', '把自己造的引擎裝上引擎座並發動。')),
    ('car_factory', 'engine_stand', 'hdfv:car_factory_controller', 'task', has('hdfv:car_factory_controller'),
     ('Car Factory', 'Make the controller of a car factory: the room where cars are built.'),
     ('汽車廠', '做出汽車廠的控制台：造車的房間。')),
    ('first_car', 'car_factory', 'hdfv:vehicle_blueprint', 'goal', milestone('rolled_out', 'car'),
     ('Off the Line', 'Roll your first car out of a car factory.'),
     ('出廠', '讓第一台車開出汽車廠。')),
    ('fuelled', 'first_car', 'hdfv:jerry_can', 'task', milestone('fuelled'),
     ('Fill Her Up', 'Fuel a vehicle from a can or a fuel dispenser: it leaves the factory with empty tanks.'),
     ('加滿', '用油桶或加油機替載具加油：它出廠時油箱是空的。')),
    ('on_the_road', 'fuelled', 'hdfv:car_factory_controller', 'task', milestone('driven', 'car', 1000),
     ('On the Road', 'Drive a car you built for a kilometre.'),
     ('上路', '開自己造的車跑一公里。')),
    ('hundred', 'on_the_road', 'hdfv:car_factory_controller', 'task', milestone('speed', 'car', 100),
     ('Ton', 'Reach 100 km/h in a car you built.'),
     ('時速一百', '開自己造的車到時速 100 公里。')),
    ('coupled', 'on_the_road', 'hdfv:car_factory_controller', 'task', milestone('coupled'),
     ('Fifth Wheel', 'Couple a trailer to a tractor.'),
     ('第五輪', '把拖車掛上牽引車。')),
    ('refit', 'first_car', 'hdfv:refit_shop_controller', 'task', milestone('refit_started'),
     ('Tuner', 'Start a refit in a refit shop: shape, size, aero kit, rims, tuning, lamps.'),
     ('改裝', '在改裝廠開始一次改裝：造型、大小、空力套件、輪圈、調校、車燈。')),
    ('painted', 'first_car', 'hdfv:paint_can', 'task', milestone('painted'),
     ('Itasha', 'Start a paint job in a paint booth: colours and your own stickers.'),
     ('痛車', '在烤漆房開始一次塗裝：顏色和你自己的貼紙。')),
    # the branches
    ('hangar', 'engine_stand', 'hdfv:hangar_controller', 'task', has('hdfv:hangar_controller'),
     ('Hangar', 'Make the controller of a hangar: aircraft are built there.'),
     ('機庫', '做出機庫的控制台：飛機在這裡造。')),
    ('first_aircraft', 'hangar', 'hdfv:hangar_controller', 'goal', milestone('rolled_out', 'aircraft'),
     ('Rollout', 'Roll your first aircraft out of a hangar.'),
     ('首架出廠', '讓第一架飛機開出機庫。')),
    ('airborne', 'first_aircraft', 'hdfv:hangar_controller', 'task', milestone('height', 'aircraft', 50),
     ('Wheels Up', 'Fly an aircraft you built 50 m above the ground.'),
     ('離地', '駕駛自己造的飛機飛到離地 50 公尺。')),
    ('control_tower', 'first_aircraft', 'hdfv:control_tower', 'task', has('hdfv:control_tower'),
     ('Tower', 'Make a control tower: radar, ATIS, runway lights and clearances.'),
     ('塔台', '做出塔台：雷達、ATIS、跑道燈和起降許可。')),
    ('cleared', 'airborne', 'hdfv:control_tower', 'task', milestone('atc_cleared'),
     ('Cleared', 'Be cleared for takeoff or to land by a control tower.'),
     ('許可起飛', '從塔台得到起飛或落地許可。')),
    ('mach_one', 'airborne', 'hdfv:hangar_controller', 'challenge', milestone('mach', None, 1.0),
     ('Through the Barrier', 'Go faster than sound in a vehicle you built.'),
     ('突破音障', '開自己造的載具超過音速。'), True),
    ('shipyard', 'engine_stand', 'hdfv:shipyard_controller', 'task', has('hdfv:shipyard_controller'),
     ('Shipyard', 'Make the controller of a shipyard: boats are built on its slipway.'),
     ('造船廠', '做出造船廠的控制台：船在船台上造。')),
    ('first_boat', 'shipyard', 'hdfv:shipyard_controller', 'goal', milestone('rolled_out', 'boat'),
     ('Launched', 'Launch your first boat from a shipyard.'),
     ('下水', '讓第一艘船從造船廠下水。')),
    ('sailed', 'first_boat', 'hdfv:shipyard_controller', 'task', milestone('driven', 'boat', 1000),
     ('Sea Trials', 'Take a boat you built a kilometre.'),
     ('試航', '駕駛自己造的船航行一公里。')),
    ('prospected', 'electricity', 'hdfv:geophone', 'task', milestone('prospected', 'field'),
     ('Prospector', 'Find an oil field with the geophone.'),
     ('探勘', '用地震檢波探勘組找到油田。')),
    ('gusher', 'prospected', 'hdfv:drilling_rig', 'task', milestone('gusher'),
     ('Gusher', 'Strike oil with a drilling rig (be near it when it does).'),
     ('噴油了', '用鑽井架鑽到石油（那時要在附近）。')),
    ('refined', 'gusher', 'hdfv:distillation_column', 'task', milestone('refined'),
     ('Refinery', 'Be near a distillation column or process unit while it makes products.'),
     ('煉油', '在蒸餾塔或製程單元生產時待在旁邊。')),
]

# the other words: (anchor, [(key, English, Chinese)])
TEXT = [
    ('itemGroup.hdfv', [('item.hdfv.handbook', 'Vehicle Engineering Handbook', '載具工程手冊')]),
    ('hdfv.configuration.towerTickets', [
        ('hdfv.configuration.tutorial', 'New-player tutorial', '新手教學'),
        ('hdfv.configuration.giveHandbook', 'Give new players the handbook', '新玩家給手冊'),
        ('hdfv.configuration.welcome', 'Welcome new players in the chat', '新玩家的歡迎訊息')]),
    ('hdfv.jei.reed_set', [
        ('hdfv.jei.handbook', 'The handbook: right-click to read it. From ores and smelting through machine tools, CNC and engine design to '
                              'building, driving and flying; its entries tick off as you get the advancements. New players get one.',
         '手冊：右鍵閱讀。從礦石、冶煉、機床、CNC、設計引擎，一路到造車、開車、開飛機；拿到對應的進度就會打勾。新玩家會拿到一本。'),
        ('hdfv.jei.alloy_furnace', 'The alloy furnace: burns fuel to make coke from coal, steel and other stock from ingots, and the early '
                                   'chemicals. Hoppers can feed it and take its output.',
         '合金爐：燒燃料，把煤煉成焦炭、把錠煉成鋼與其他料件，也做早期的化學品。可以用漏斗進料與出料。'),
        ('hdfv.jei.smelter', 'An electric smelting machine: four inputs, the product and two by-products; it takes Forge Energy by cable '
                             'and spends the kWh each batch needs.',
         '電冶設備：四個投料格、一個成品、兩個副產物；用電線接 Forge Energy，每批用掉該批需要的電量。'),
        ('hdfv.jei.paint_booth', 'The paint booth\'s controller: set in the wall of a closed room, scans it like an assembly room. Drive a '
                                 'finished vehicle in, choose colours and stickers (pictures you upload), and it paints with paint cans and '
                                 'sticker film, taking time and electricity.',
         '烤漆房控制台：裝在封閉房間的牆上，像組裝室一樣掃描。把做好的載具開進去，選顏色和貼紙（你上傳的圖），它用油漆罐和貼紙膜施工，要花時間和電。'),
        ('hdfv.jei.refit_shop', 'The refit shop\'s controller: set in the wall of a closed room. Drive a finished car in and change its shape, '
                                'size, aero kit, rims, tuning and lamps; the shop checks it again, fits the new parts from its chests and '
                                'swaps the car where it stands, keeping its fuel.',
         '改裝廠控制台：裝在封閉房間的牆上。把做好的車開進去，改造型、大小、空力套件、輪圈、調校與車燈；改裝廠重新檢查，從箱子取新零件施工，原地換成新車，油量保留。'),
        ('hdfv.jei.control_tower', 'The control tower\'s controller: the tower is a multiblock that can be built taller (higher sees and '
                                   'reaches farther). On electricity it runs radar, ATIS, the runway lights and clearances; a controller '
                                   'villager works it, or a player takes over at its desk.',
         '塔台控制台：塔台是可以加高的多方塊（越高看得越遠、雷達越遠）。有電時運作雷達、ATIS、跑道燈與起降許可；管制員村民會自動管制，玩家也可以在管制台接手。'),
        ('hdfv.jei.more_in_handbook', 'More in the Vehicle Engineering Handbook.', '詳見《載具工程手冊》。')]),
    ('hdfv.direction.west', [
        ('hdfv.handbook.search', 'Search', '搜尋'),
        ('hdfv.handbook.section.main', 'Main line', '主線'),
        ('hdfv.handbook.section.branch', 'Branches', '分支'),
        ('hdfv.handbook.done', '✔ Done', '✔ 已完成'),
        ('hdfv.handbook.goal', 'Advancement: %s', '進度：%s'),
        ('hdfv.handbook.previous', '← Previous: %s', '← 上一篇：%s'),
        ('hdfv.handbook.next', 'Next: %s →', '下一篇：%s →'),
        ('hdfv.handbook.see', 'See: %s', '參見：%s'),
        ('hdfv.handbook.no_recipe', '(no recipe %s in this game)', '（這個遊戲沒有配方 %s）'),
        ('hdfv.handbook.empty', 'No handbook pages found (a resource pack may hide them).', '找不到手冊頁面（可能被資源包蓋掉了）。'),
        ('hdfv.welcome.title', 'Welcome to High Degree of Freedom Vehicles!', '歡迎來到高自由度載具！'),
        ('hdfv.welcome.handbook', 'You have the Vehicle Engineering Handbook: from your first ore to your first car, aircraft and boat. %s',
         '你拿到一本《載具工程手冊》：從第一塊礦石教到你的第一台車、飛機和船。%s'),
        ('hdfv.welcome.open', '[Open the handbook]', '［打開手冊］'),
        ('hdfv.welcome.open.hover', 'Opens the handbook (or right-click the book)', '打開手冊（也可以右鍵那本書）'),
        ('hdfv.welcome.advancements', 'The advancements (%s) have a High Degree of Freedom Vehicles tab: it shows the next step.',
         '進度畫面（%s）有「高自由度載具」分頁，會告訴你下一步。'),
        ('hdfv.welcome.keys', 'At the wheel: %s and %s shift up and down, %s is the handbrake, %s couples a trailer, %s calls the tower. '
                              'All keys: Options, Controls, Key Binds, High Degree of Freedom Vehicles.',
         '開車時：%s、%s 升降檔，%s 手煞車，%s 掛拖車，%s 呼叫塔台。所有按鍵在「選項 → 控制 → 按鍵設定 → 高自由度載具」。')]),
]


def lang_groups():
    groups = list(TEXT)
    adv = []
    for a in ADVANCEMENTS:
        path, en, zh = a[0], a[5], a[6]
        adv.append(('advancements.hdfv.tutorial.%s.title' % path, en[0], zh[0]))
        adv.append(('advancements.hdfv.tutorial.%s.description' % path, en[1], zh[1]))
    groups.append(('hdfv.handbook.empty', adv))
    return groups


def advancement(a):
    path, parent, icon, frame, criteria = a[:5]
    hidden = len(a) > 7 and a[7]
    display = {
        'icon': {'id': icon},
        'title': {'translate': 'advancements.hdfv.tutorial.%s.title' % path},
        'description': {'translate': 'advancements.hdfv.tutorial.%s.description' % path},
        'frame': frame,
        'show_toast': True,
        'announce_to_chat': frame != 'task',
        'hidden': bool(hidden),
    }
    if parent is None:
        display['background'] = 'hdfv:textures/block/assembly_floor.png'
    out = {}
    if parent is not None:
        out['parent'] = 'hdfv:tutorial/' + parent
    out['display'] = display
    out['criteria'] = criteria
    if len(criteria) > 1:
        out['requirements'] = [[k] for k in criteria]   # every one of them
    return out


def handbook_icon():
    """A blue book, its pages' edge on the right, a gold gear on the cover."""
    blue, spine, pages, gold = ma.rgb('#2f5d9a'), ma.rgb('#1d3c66'), ma.rgb('#e8e2cf'), ma.rgb('#e0b84a')
    c = ma.Canvas((0, 0, 0, 0))
    c.rect(3, 1, 13, 14, blue)
    c.rect(2, 1, 3, 14, spine)
    c.rect(13, 2, 14, 14, pages)
    c.rect(3, 14, 13, 14, pages)
    c.frame(2, 1, 13, 14, ma.shade(blue, 0.6))
    c.circle(8, 7, 2.6, gold)
    for x, y in ((8, 3), (8, 11), (4, 7), (12, 7)):
        c.set(x, y, gold)
    c.set(8, 7, gold)
    return c.im


def main():
    ma.write_png(os.path.join(ASSETS, 'textures', 'item', 'handbook.png'), handbook_icon())
    ma.write_json(os.path.join(ASSETS, 'models', 'item', 'handbook.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'hdfv:item/handbook'}})
    paths = set()
    known = {a[0] for a in ADVANCEMENTS}
    for a in ADVANCEMENTS:
        if a[1] is not None and a[1] not in known:
            sys.exit('no parent ' + a[1] + ' for ' + a[0])
        paths.add(a[0] + '.json')
        ma.write_json(os.path.join(FOLDER, a[0] + '.json'), advancement(a))
    for f in os.listdir(FOLDER):
        if f not in paths:
            os.remove(os.path.join(FOLDER, f))
            print('removed', f)
    saved = ma.lang_groups
    ma.lang_groups = lang_groups
    try:
        for lang in ('en', 'zh'):
            ma.insert_lang(os.path.join(ASSETS, 'lang', 'en_us.json' if lang == 'en' else 'zh_tw.json'), lang)
    finally:
        ma.lang_groups = saved
    print('tutorial:', len(ADVANCEMENTS), 'advancements,', ma.written, 'files written')


if __name__ == '__main__':
    main()
