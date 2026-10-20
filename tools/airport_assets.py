"""hdfv 0.20 (0.21: the blocks inside a tower): the assets and data of airfields, written from one spec: blockstates (the paintable pavement's quarters as a
multipart model), cube models of the tower's blocks, item models, loot tables, recipes, tags (tools, AeroCore's smooth
ground, the controller villager's job site, the biomes airports stand on), the airport's world generation, both
languages (new keys inserted after their group's anchor, the files' line order kept alike), the 16 px texture sources
of the tower's blocks, pavement and turf (into tools/blender/blocks16, redrawn at 64 px by `scripts/blender.ps1
machines`), the line paints' icons and the tower controller's villager clothes. The light fittings, windsock, sign
and floodlight are modelled by tools/blender/airfield_blocks.py. Needs Pillow; files are rewritten only when they
change. 0.23: recipes are written by tools/recipes.py, not here. Checked by AirportAssetsTest.

    py -3 tools/airport_assets.py
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
            if list(Image.open(path).convert('RGBA').getdata()) == list(im.getdata()):
                return
        except OSError:
            pass
    b = io.BytesIO()
    im.save(b, 'PNG', optimize=True)
    write(path, b.getvalue())


# ------------------------------------------------------------------------------------------------ the spec

I, S, G, P, R = 'minecraft:iron_ingot', 'minecraft:smooth_stone', 'minecraft:glass', 'minecraft:glass_pane', 'minecraft:redstone'
L, Q = 'minecraft:redstone_lamp', 'minecraft:quartz'

# the tower: id, English, Chinese, recipe (pattern, key, count)
TOWER = [
    ('control_tower', 'Control Tower', '機場塔台', (['SPS', 'RCR', 'SBS'], {'S': S, 'P': P, 'R': R, 'C': 'minecraft:compass', 'B': 'minecraft:iron_block'}, 1)),
    ('tower_base', 'Tower Base Wall', '塔台塔底牆', (['TTT', 'TIT', 'TTT'], {'T': 'minecraft:stone_bricks', 'I': I}, 8)),
    ('tower_shaft', 'Tower Shaft', '塔身', (['SSS', 'SPS', 'SSS'], {'S': S, 'P': P}, 8)),
    ('tower_cab_floor', 'Tower Cab Floor', '管制室地板', (['ICI', 'SSS'], {'I': I, 'C': 'minecraft:light_gray_concrete', 'S': S}, 6)),
    ('tower_glass', 'Tower Cab Glazing', '管制室玻璃', (['GIG', 'GGG', 'GIG'], {'G': G, 'I': I}, 8)),
    ('tower_roof', 'Tower Cab Roof', '管制室屋頂', (['ICI', 'III'], {'I': I, 'C': 'minecraft:copper_ingot'}, 4)),
    ('tower_console', 'Tower Console', '管制台', (['PRP', 'ICI'], {'P': P, 'R': R, 'I': I, 'C': 'minecraft:comparator'}, 1)),
    ('tower_radar', 'Surveillance Radar', '監視雷達', (['ICI', 'IRI', ' I '], {'I': I, 'C': 'minecraft:compass', 'R': R}, 1)),
    ('tower_mast', 'Tower Antenna Mast', '塔台天線桅', (['LTL', ' I ', ' I '], {'L': 'minecraft:lightning_rod', 'T': 'minecraft:redstone_torch', 'I': I}, 1)),
]

# light fittings: kind (Fixture.Kind lower case), English, Chinese, recipe
FIXTURES = [
    ('runway_edge', 'Runway Edge Light', '跑道邊燈', ([' P ', 'DLD', ' I '], {'P': P, 'D': 'minecraft:white_dye', 'L': L, 'I': I}, 4)),
    ('runway_centre', 'Runway Centreline Light', '跑道中心線燈', (['DPD', 'ILI'], {'D': 'minecraft:white_dye', 'P': P, 'L': L, 'I': I}, 4)),
    ('threshold', 'Runway Threshold and End Light', '跑道頭與跑道尾燈', ([' P ', 'DLE', ' I '], {'P': P, 'D': 'minecraft:green_dye', 'E': 'minecraft:red_dye', 'L': L, 'I': I}, 4)),
    ('papi', 'PAPI Unit', 'PAPI 進場滑降指示燈', (['PPP', 'ELE', 'I I'], {'P': P, 'E': 'minecraft:red_dye', 'L': L, 'I': I}, 1)),
    ('approach', 'Approach Light', '進場燈', (['PPP', ' L ', ' I '], {'P': P, 'L': L, 'I': I}, 2)),
    ('approach_flasher', 'Sequenced Flashing Light', '順序閃光燈', (['PQP', ' L ', ' I '], {'P': P, 'Q': Q, 'L': L, 'I': I}, 2)),
    ('taxi_edge', 'Taxiway Edge Light', '滑行道邊燈', ([' P ', 'DLD', ' I '], {'P': P, 'D': 'minecraft:blue_dye', 'L': L, 'I': I}, 4)),
    ('taxi_centre', 'Taxiway Centreline Light', '滑行道中心線燈', (['DPD', 'ILI'], {'D': 'minecraft:green_dye', 'P': P, 'L': L, 'I': I}, 4)),
    ('obstruction', 'Obstruction Light', '障礙燈', ([' D ', 'PLP', ' I '], {'D': 'minecraft:red_dye', 'P': P, 'L': L, 'I': I}, 2)),
    ('beacon', 'Aerodrome Beacon', '機場旋轉燈標', (['DPE', 'PLP', 'III'], {'D': 'minecraft:white_dye', 'E': 'minecraft:green_dye', 'P': P, 'L': L, 'I': I}, 1)),
]

# the ground and the small blocks: id, English, Chinese, recipe
GROUND = [
    ('apron_concrete', 'Apron Concrete', '停機坪混凝土', (['SCS', 'CGC', 'SCS'], {'S': S, 'C': 'minecraft:light_gray_concrete', 'G': 'minecraft:gravel'}, 8)),
    ('airfield_turf', 'Airfield Turf', '機場草皮', (['GGG', 'DDD'], {'G': 'minecraft:short_grass', 'D': 'minecraft:dirt'}, 3)),
    ('windsock', 'Windsock', '風向袋', (['IOW', ' I ', ' I '], {'I': I, 'O': 'minecraft:orange_wool', 'W': 'minecraft:white_wool'}, 1)),
    ('holding_sign', 'Runway Holding Position Sign', '跑道等待位置標誌', (['CWC', 'I I'], {'C': 'minecraft:red_concrete', 'W': 'minecraft:white_dye', 'I': I}, 2)),
    ('apron_floodlight', 'Apron Floodlight', '停機坪泛光燈', (['LLL', ' I ', ' I '], {'L': L, 'I': I}, 1)),
]
ASPHALT = 'asphalt'   # the refinery's (0.8), paintable since 0.20: only its models change

# 0.21: inside a tower (HdfvAirports.INSIDE): id, English, Chinese, recipe
B = 'minecraft:iron_bars'
INSIDE = [
    ('steel_stairs', 'Steel Grating Stairs', '鋼格柵樓梯', (['B  ', 'IB ', 'IIB'], {'B': B, 'I': I}, 4)),
    ('steel_column', 'Steel Column', '鋼柱', (['I', 'B', 'I'], {'I': I, 'B': B}, 4)),
    ('steel_grating', 'Steel Grating', '鋼格柵', (['BIB', 'I I', 'BIB'], {'B': B, 'I': I}, 8)),
    ('interior_lamp', 'Interior Lamp', '室內燈', (['IGI', ' D '], {'I': I, 'G': 'minecraft:glass_pane', 'D': 'minecraft:glowstone_dust'}, 2)),
    ('cable_duct', 'Floor Cable Duct', '地板電纜槽', (['SSS', 'CCC'], {'S': 'minecraft:smooth_stone_slab', 'C': 'hdfv:heavy_cable'}, 3)),
    ('tower_floor', 'Terrazzo Floor', '水磨石地板', (['CQC', 'QCQ', 'CQC'], {'C': 'minecraft:white_concrete', 'Q': Q}, 8)),
]

# line paints: id, English, Chinese, colour, band, recipe
PAINTS = [
    ('white_line_paint', 'White Line Paint', '白色標線漆', '#f2f2ee', None, ([' D ', 'ICI', ' I '], {'D': 'minecraft:white_dye', 'I': I, 'C': 'minecraft:clay_ball'}, 1)),
    ('yellow_line_paint', 'Yellow Line Paint', '黃色標線漆', '#f0c020', None, ([' D ', 'ICI', ' I '], {'D': 'minecraft:yellow_dye', 'I': I, 'C': 'minecraft:clay_ball'}, 1)),
    ('threshold_paint', 'Threshold Paint', '跑道頭標線漆', '#f2f2ee', '#30a050', ([' D ', 'ICI', ' E '], {'D': 'minecraft:white_dye', 'I': I, 'C': 'minecraft:clay_ball',
                                                                                                    'E': 'minecraft:green_dye'}, 1)),
]

SURFACES = ['asphalt', 'apron_concrete']
LINES = {'white': 'airfield_line_white', 'yellow': 'airfield_line_yellow'}


def lang_groups():
    """(anchor key, [(key, en, zh)]) in the order they go in."""
    g = []
    blocks = [('block.hdfv.' + t[0], t[1], t[2]) for t in TOWER] + [('block.hdfv.airfield_' + f[0], f[1], f[2]) for f in FIXTURES]
    blocks += [('block.hdfv.' + b[0], b[1], b[2]) for b in GROUND]
    blocks += [('block.hdfv.' + b[0], b[1], b[2]) for b in INSIDE]
    g.append(('block.hdfv.asphalt', blocks))
    g.append(('item.hdfv.bitumen', [('item.hdfv.' + p[0], p[1], p[2]) for p in PAINTS] + [('filled_map.hdfv.airport', 'Airport Map', '機場地圖')]))
    g.append(('entity.minecraft.villager.hdfv.welder', [('entity.minecraft.villager.hdfv.tower_controller', 'Tower Controller', '塔台管制員')]))
    g.append(('hdfv.configuration.maxSide', [
        ('hdfv.configuration.airfields', 'Airfields', '機場'),
        ('hdfv.configuration.generateAirports', 'Generate airports', '生成機場'),
        ('hdfv.configuration.generateInFlatWorlds', 'Airports in flat worlds too', '平坦世界也生成機場'),
        ('hdfv.configuration.largeAirports', 'Large airports', '大型機場'),
        ('hdfv.configuration.mediumAirports', 'Medium airports', '中型機場'),
        ('hdfv.configuration.smallAirports', 'Small airfields', '小型機場'),
        ('hdfv.configuration.towerTickets', 'Towers keep their chunk ticking', '塔台讓所在區塊持續運作'),
    ]))
    g.append(('hdfv.configuration.sound.far', [
        ('hdfv.configuration.aviation', 'Aviation', '航空'),
        ('hdfv.configuration.aviation.units', 'Tower and ATIS units', '塔台與 ATIS 的單位'),
        ('hdfv.configuration.aviation.radioChat', 'Write what the tower says in the chat', '塔台的話也寫在聊天欄'),
    ]))
    g.append(('key.hdfv.hazard', [
        ('key.hdfv.atc_call', 'Call the tower', '呼叫塔台'),
        ('key.hdfv.atc_cancel', 'Cancel / unable', '取消／無法遵照'),
        ('key.hdfv.atc_atis', 'Listen to the ATIS', '收聽 ATIS'),
    ]))
    atc = [
        ('hdfv.atc.no_tower', 'No tower in reach', '收不到塔台'),
        ('hdfv.atc.radio', '[Tower] %s', '［塔台］%s'),
        ('hdfv.atc.instruction.none', 'Standing by', '待命'),
        ('hdfv.atc.instruction.taxi_to_hold', 'Taxi to the holding point, runway %s', '滑行到跑道 %s 等待點'),
        ('hdfv.atc.instruction.hold_position', 'Hold short of runway %s', '在跑道 %s 外等待'),
        ('hdfv.atc.instruction.line_up_and_wait', 'Runway %s, line up and wait', '跑道 %s，進跑道等待'),
        ('hdfv.atc.instruction.cleared_takeoff', 'Runway %s, cleared for take-off', '跑道 %s，許可起飛'),
        ('hdfv.atc.instruction.continue_approach', 'Runway %s, number %s, continue approach', '跑道 %s，第 %s 順位，繼續進場'),
        ('hdfv.atc.instruction.cleared_land', 'Runway %s, cleared to land', '跑道 %s，許可落地'),
        ('hdfv.atc.instruction.go_around', 'Go around, runway %s', '重飛，跑道 %s'),
        ('hdfv.atc.instruction.taxi_to_apron', 'Vacate and taxi to the apron', '脫離跑道，滑行到停機坪'),
        ('hdfv.atc.instruction.takeoff_here', 'Cleared to lift off here', '許可原地起飛'),
        ('hdfv.atc.instruction.land_here', 'Cleared to land here', '許可原地降落'),
        ('hdfv.atc.instruction.self_announce', 'Nobody in the tower: announce your intentions, runway %s', '塔台無人：自行宣告，跑道 %s'),
        ('hdfv.atc.instruction.cancelled', 'Clearance cancelled', '許可取消'),
        ('hdfv.atc.warning.takeoff_without_clearance', 'Took off without clearance', '未經許可起飛'),
        ('hdfv.atc.warning.landed_without_clearance', 'Landed without clearance', '未經許可落地'),
        ('hdfv.atc.warning.runway_incursion', 'Runway incursion!', '跑道入侵！'),
        ('hdfv.atc.warning.wrong_runway', 'Wrong runway: another is in use', '用錯跑道：使用中的是另一條'),
        ('hdfv.atc.warning.strong_crosswind', 'Strong crosswind', '側風過強'),
        ('hdfv.atc.phase.ground', 'On the ground', '地面'),
        ('hdfv.atc.phase.holding', 'Holding', '等待'),
        ('hdfv.atc.phase.lined_up', 'Lined up', '已進跑道'),
        ('hdfv.atc.phase.takeoff_roll', 'Taking off', '起飛滾行'),
        ('hdfv.atc.phase.departed', 'Departed', '已離場'),
        ('hdfv.atc.phase.airborne', 'Airborne', '空中'),
        ('hdfv.atc.phase.final', 'On final', '最後進場'),
        ('hdfv.atc.phase.rollout', 'Landing roll', '落地滾行'),
        ('hdfv.atc.phase.vacated', 'Vacated', '已脫離跑道'),
        ('hdfv.atc.wind', 'wind %s° %s %s', '風 %s 度 %s %s'),
        ('hdfv.atc.wind.calm', 'wind calm', '靜風'),
        ('hdfv.atc.wind.gusting', ' gusting %s', '，陣風 %s'),
        ('hdfv.atc.unit.kt', 'kt', '節'),
        ('hdfv.atc.unit.mps', 'm/s', '公尺每秒'),
        ('hdfv.atc.visibility.ten', 'visibility 10 km or more', '能見度 10 公里以上'),
        ('hdfv.atc.visibility.km', 'visibility %s km', '能見度 %s 公里'),
        ('hdfv.atc.visibility.m', 'visibility %s m', '能見度 %s 公尺'),
        ('hdfv.atc.height.ft', '%s ft', '%s 呎'),
        ('hdfv.atc.height.m', '%s m', '%s 公尺'),
        ('hdfv.atc.speed.kt', '%s kt', '%s 節'),
        ('hdfv.atc.speed.kmh', '%s km/h', '%s 公里每小時'),
        ('hdfv.atc.distance.nm', '%s NM', '%s 海里'),
        ('hdfv.atc.distance.km', '%s km', '%s 公里'),
        ('hdfv.atc.atis', 'Information %s, runway %s', '資訊 %s，跑道 %s'),
        ('hdfv.atc.weather.thunder', 'thunderstorm', '雷雨'),
        ('hdfv.atc.weather.rain', 'rain', '雨'),
        ('hdfv.atc.temperature', 'temperature %s °C', '氣溫 %s 度'),
        ('hdfv.atc.qnh', 'QNH %s', '高度表撥定值 %s'),
        ('hdfv.atc.hud.tower', 'Tower', '塔台'),
        ('hdfv.atc.hud.unmanned', 'Nobody in the tower: self-announce', '塔台無人：自行宣告'),
        ('hdfv.atc.hud.phase', 'Phase: %s', '階段：%s'),
        ('hdfv.atc.hud.atis', 'ATIS %s  RWY %s  %s  QNH %s', 'ATIS %s　跑道 %s　%s　QNH %s'),
    ]
    tower = [
        ('hdfv.tower.page.traffic', 'Traffic', '航情'),
        ('hdfv.tower.page.field', 'Field', '機場'),
        ('hdfv.tower.take', 'Take control', '接手管制'),
        ('hdfv.tower.release', 'Release control', '交還管制'),
        ('hdfv.tower.taken', 'Someone else has the tower', '已有別人在管制'),
        ('hdfv.tower.not_in_control', 'Take control first', '先接手管制'),
        ('hdfv.tower.held_by', 'Controlled by %s', '管制員：%s'),
        ('hdfv.tower.manned', 'Controller villager on duty', '管制員村民值班中'),
        ('hdfv.tower.unmanned', 'Nobody on duty', '無人值班'),
        ('hdfv.tower.no_traffic', 'No traffic', '沒有航空器'),
        ('hdfv.tower.pick', 'Pick a strip to instruct', '點選進程單下指令'),
        ('hdfv.tower.conflict', 'Conflict: %s', '衝突：%s'),
        ('hdfv.tower.visual', 'Visual only: no radar', '只有目視：沒有雷達'),
        ('hdfv.tower.unpowered', 'No power: ATIS only', '沒電：只有 ATIS'),
        ('hdfv.tower.order.taxi_to_hold', 'Taxi', '滑行'),
        ('hdfv.tower.order.hold_position', 'Hold', '等待'),
        ('hdfv.tower.order.line_up_and_wait', 'Line up', '進跑道'),
        ('hdfv.tower.order.cleared_takeoff', 'Take-off', '許可起飛'),
        ('hdfv.tower.order.continue_approach', 'Continue', '繼續進場'),
        ('hdfv.tower.order.cleared_land', 'Land', '許可落地'),
        ('hdfv.tower.order.go_around', 'Go around', '重飛'),
        ('hdfv.tower.order.taxi_to_apron', 'To apron', '回停機坪'),
        ('hdfv.tower.order.cancelled', 'Cancel', '取消'),
        ('hdfv.tower.auto_on', 'Auto', '自動'),
        ('hdfv.tower.auto_off', 'Manual', '手動'),
        ('hdfv.tower.paint', 'Paint markings', '畫標線'),
        ('hdfv.tower.painted', 'Painted %s blocks', '畫了 %s 格'),
        ('hdfv.tower.status.unformed', 'Not formed', '未成形'),
        ('hdfv.tower.status.powered', 'Powered', '有電'),
        ('hdfv.tower.status.unpowered', 'No power', '沒電'),
        ('hdfv.tower.status.manned', 'Controlled', '有人管制'),
        ('hdfv.tower.status.unmanned', 'Unmanned', '無人管制'),
        ('hdfv.tower.power', '%s kW, %s / %s FE', '%s 千瓦，%s／%s FE'),
        ('hdfv.tower.heights', 'Cab eye %s, zone %s, %s', '管制室視高 %s，管制區 %s，%s'),
        ('hdfv.tower.radar', 'radar', '有雷達'),
        ('hdfv.tower.no_radar', 'no radar', '沒有雷達'),
        ('hdfv.tower.runways', 'Runways %s; in use %s', '跑道 %s；使用中 %s'),
        ('hdfv.tower.lights', 'Lights: %s fittings', '燈：%s 具'),
        ('hdfv.tower.circuit.runway', 'Runway', '跑道燈'),
        ('hdfv.tower.circuit.approach', 'Approach', '進場燈'),
        ('hdfv.tower.circuit.papi', 'PAPI', 'PAPI'),
        ('hdfv.tower.circuit.taxiway', 'Taxiway', '滑行道燈'),
        ('hdfv.tower.circuit.obstruction', 'Obstruction', '障礙燈'),
        ('hdfv.tower.circuit.beacon', 'Beacon', '燈標'),
        ('hdfv.tower.off', 'Off', '關'),
        ('hdfv.tower.log', 'Log', '紀錄'),
        ('hdfv.paint.threshold_set', 'Threshold: landing toward the %s', '跑道頭：朝%s落地'),
        ('hdfv.paint.threshold_cleared', 'Threshold mark taken off (landing toward the %s)', '拿掉跑道頭標記（朝%s落地）'),
        ('hdfv.direction.north', 'north', '北'),
        ('hdfv.direction.east', 'east', '東'),
        ('hdfv.direction.south', 'south', '南'),
        ('hdfv.direction.west', 'west', '西'),
    ]
    g.append(('hdfv.drive.hazard_off', atc + tower))
    return g


def insert_lang(path, lang):
    """Inserts the new keys after their anchors (the value of a key already there is set, the order kept)."""
    text = open(path, encoding='utf-8').read()
    lines = text.split('\n')
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


# ------------------------------------------------------------------------------------------------ data files

def block_drop(id_):
    return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hdfv:' + id_}],
                                                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}


def recipe(id_, pattern, key, count=1):
    return {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': pattern,
            'key': {k: {'item': v} for k, v in key.items()}, 'result': {'id': 'hdfv:' + id_, 'count': count}}


def recipe_signature(r):
    if r.get('type') != 'minecraft:crafting_shaped':
        return None
    key = {k: (v.get('item') or v.get('tag')) for k, v in r.get('key', {}).items()}
    return tuple(''.join('|' + key.get(ch, ' ') for ch in row) for row in r['pattern'])


def add_tag(path, values):
    obj = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {'values': []}
    for v in values:
        if v not in obj['values']:
            obj['values'].append(v)
    write_json(path, obj)


FACINGS = (('north', 0), ('east', 90), ('south', 180), ('west', 270))


def facing_variants(model, extra=''):
    v = {}
    for facing, y in FACINGS:
        m = {'model': model}
        if y:
            m['y'] = y
        v['facing=' + facing + extra] = m
    return v


def surface_multipart(id_):
    """The paintable pavement: its sides and bottom, and each quarter of its top either plain or painted (white for white
    and threshold paint, yellow), from the paint and the 4-bit mask (bit 0 the quarter at the smaller x and z, 1 larger
    x, 2 larger z)."""
    parts = [{'apply': {'model': 'hdfv:block/' + id_ + '_base'}}]
    for q in range(4):
        with_bit = '|'.join(str(m) for m in range(16) if m >> q & 1)
        without = '|'.join(str(m) for m in range(16) if not m >> q & 1)
        parts.append({'when': {'OR': [{'paint': 'none'}, {'mask': without}]}, 'apply': {'model': 'hdfv:block/' + id_ + '_q' + str(q)}})
        parts.append({'when': {'paint': 'white|threshold', 'mask': with_bit}, 'apply': {'model': 'hdfv:block/' + id_ + '_q' + str(q) + '_white'}})
        parts.append({'when': {'paint': 'yellow', 'mask': with_bit}, 'apply': {'model': 'hdfv:block/' + id_ + '_q' + str(q) + '_yellow'}})
    return {'multipart': parts}


def quarter(q, texture):
    x0, z0 = (q & 1) * 8, (q >> 1 & 1) * 8
    return {'parent': 'minecraft:block/block', 'textures': {'top': texture, 'particle': texture},
            'elements': [{'from': [x0, 16, z0], 'to': [x0 + 8, 16, z0 + 8],
                          'faces': {'up': {'uv': [x0, z0, x0 + 8, z0 + 8], 'texture': '#top', 'cullface': 'up'}}}]}


def surface_models(id_):
    models = os.path.join(ASSETS, 'models', 'block')
    tex = 'hdfv:block/' + id_
    write_json(os.path.join(models, id_ + '_base.json'), {'parent': 'minecraft:block/block', 'textures': {'side': tex, 'particle': tex},
        'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            'down': {'texture': '#side', 'cullface': 'down'}, 'north': {'texture': '#side', 'cullface': 'north'},
            'south': {'texture': '#side', 'cullface': 'south'}, 'west': {'texture': '#side', 'cullface': 'west'},
            'east': {'texture': '#side', 'cullface': 'east'}}}]})
    for q in range(4):
        write_json(os.path.join(models, '%s_q%d.json' % (id_, q)), quarter(q, tex))
        for colour, line in LINES.items():
            write_json(os.path.join(models, '%s_q%d_%s.json' % (id_, q, colour)), quarter(q, 'hdfv:block/' + line))
    # the item and the plain block: a cube
    write_json(os.path.join(models, id_ + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex}})


def data():
    recipes = {}
    blockstates, block_models, item_models = os.path.join(ASSETS, 'blockstates'), os.path.join(ASSETS, 'models', 'block'), os.path.join(ASSETS, 'models', 'item')
    loot = os.path.join(DATA, 'hdfv', 'loot_table', 'blocks')
    blocks_all = []
    # the tower: a controller facing its front, members formed or not
    ctrl = TOWER[0]
    write_json(os.path.join(blockstates, ctrl[0] + '.json'), {'variants': facing_variants('hdfv:block/' + ctrl[0])})
    write_json(os.path.join(block_models, ctrl[0] + '.json'), {'parent': 'minecraft:block/orientable', 'textures': {
        'top': 'hdfv:block/' + ctrl[0] + '_top', 'front': 'hdfv:block/' + ctrl[0] + '_front', 'side': 'hdfv:block/' + ctrl[0] + '_side'}})
    recipes[ctrl[0]] = recipe(ctrl[0], *ctrl[3])
    blocks_all.append(ctrl[0])
    for kind, en, zh, r in TOWER[1:]:
        write_json(os.path.join(blockstates, kind + '.json'), {'variants': {'formed=false': {'model': 'hdfv:block/' + kind},
                                                                            'formed=true': {'model': 'hdfv:block/' + kind + '_formed'}}})
        m = {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'side': 'hdfv:block/' + kind + '_side', 'top': 'hdfv:block/' + kind + '_top',
                                                                       'bottom': 'hdfv:block/' + kind + '_top'}}
        if kind == 'tower_glass':
            m['render_type'] = 'minecraft:translucent'
        write_json(os.path.join(block_models, kind + '.json'), m)
        write_json(os.path.join(block_models, kind + '_formed.json'), {'textures': {'particle': 'hdfv:block/' + kind + '_side'}, 'elements': []})
        recipes[kind] = recipe(kind, *r)
        blocks_all.append(kind)
    # light fittings (OBJ models from airfield_blocks.py, turned to their facing)
    for kind, en, zh, r in FIXTURES:
        id_ = 'airfield_' + kind
        write_json(os.path.join(blockstates, id_ + '.json'), {'variants': facing_variants('hdfv:block/' + id_)})
        recipes[id_] = recipe(id_, *r)
        blocks_all.append(id_)
    # the ground and the small blocks
    surface_models('apron_concrete')
    surface_models(ASPHALT)
    for s in SURFACES:
        write_json(os.path.join(blockstates, s + '.json'), surface_multipart(s))
    write_json(os.path.join(block_models, 'airfield_turf.json'), {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'side': 'hdfv:block/airfield_turf_side', 'top': 'hdfv:block/airfield_turf_top', 'bottom': 'minecraft:block/dirt'}})
    write_json(os.path.join(blockstates, 'airfield_turf.json'), {'variants': {'': {'model': 'hdfv:block/airfield_turf'}}})
    write_json(os.path.join(blockstates, 'windsock.json'), {'variants': {'': {'model': 'hdfv:block/windsock'}}})
    write_json(os.path.join(blockstates, 'holding_sign.json'), {'variants': facing_variants('hdfv:block/holding_sign')})
    v = facing_variants('hdfv:block/apron_floodlight', ',lit=false')
    v.update(facing_variants('hdfv:block/apron_floodlight_on', ',lit=true'))
    write_json(os.path.join(blockstates, 'apron_floodlight.json'), {'variants': v})
    for id_, en, zh, r in GROUND:
        recipes[id_] = recipe(id_, *r)
        blocks_all.append(id_)
    inside_models(blockstates, block_models)
    for id_, en, zh, r in INSIDE:
        recipes[id_] = recipe(id_, *r)
        blocks_all.append(id_)
    for id_ in blocks_all:
        write_json(os.path.join(item_models, id_ + '.json'), {'parent': 'hdfv:block/' + ITEM_MODEL.get(id_, id_)})
        write_json(os.path.join(loot, id_ + '.json'), block_drop(id_))
    for id_, en, zh, colour, band, r in PAINTS:
        write_json(os.path.join(item_models, id_ + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'hdfv:item/' + id_}})
        recipes[id_] = recipe(id_, *r)
    # tags
    pick = ['hdfv:' + b for b in blocks_all if b != 'airfield_turf']
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), pick)
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'shovel.json'), ['hdfv:airfield_turf'])
    add_tag(os.path.join(DATA, 'aerocore', 'tags', 'block', 'smooth.json'), ['hdfv:asphalt', 'hdfv:apron_concrete'])
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'stairs.json'), ['hdfv:steel_stairs'])
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'item', 'stairs.json'), ['hdfv:steel_stairs'])
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'point_of_interest_type', 'acquirable_job_site.json'), ['hdfv:tower_controller'])
    add_tag(os.path.join(DATA, 'hdfv', 'tags', 'worldgen', 'biome', 'has_airport.json'),
            ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:savanna', 'minecraft:desert', 'minecraft:snowy_plains'])
    # world generation: a feature in every overworld chunk, after the trees (it clears them)
    wg = os.path.join(DATA, 'hdfv', 'worldgen')
    write_json(os.path.join(wg, 'configured_feature', 'airport.json'), {'type': 'hdfv:airport', 'config': {}})
    write_json(os.path.join(wg, 'placed_feature', 'airport.json'), {'feature': 'hdfv:airport', 'placement': []})
    write_json(os.path.join(DATA, 'hdfv', 'neoforge', 'biome_modifier', 'airport.json'), {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': 'hdfv:airport', 'step': 'top_layer_modification'})
    # 0.23: the recipes are tools/recipes.py's (the spec's recipes above are the 0.20 ones, no longer written)
    return blocks_all


# the blocks inside a tower (0.21): the models whose items are not the block's own
ITEM_MODEL = {'steel_stairs': 'steel_stairs', 'interior_lamp': 'interior_lamp_ceiling', 'cable_duct': 'cable_duct'}


def stairs_variants(model):
    """A stair's blockstate (vanilla's rule): facing east 0, south 90, west 180, north 270; the left inner and outer
    corners a quarter less at the bottom, the right ones a quarter more at the top."""
    v = {}
    for facing, f in (('east', 0), ('south', 90), ('west', 180), ('north', 270)):
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                m = model + ('_inner' if shape.startswith('inner') else '_outer' if shape.startswith('outer') else '')
                y = f
                if half == 'bottom' and shape.endswith('left'):
                    y = f - 90
                if half == 'top' and shape.endswith('right'):
                    y = f + 90
                entry = {'model': m, 'uvlock': True}
                if half == 'top':
                    entry['x'] = 180
                if y % 360:
                    entry['y'] = y % 360
                v['facing=%s,half=%s,shape=%s' % (facing, half, shape)] = entry
    return v


def inside_models(blockstates, models):
    stairs = 'hdfv:block/steel_stairs'
    write_json(os.path.join(blockstates, 'steel_stairs.json'), {'variants': stairs_variants(stairs)})
    # 0.22: open-riser steps (vanilla's stair shapes, east the high side as theirs): a grating tread on each level and
    # side plates (stringers) along the outer edges, no risers, so from below the stair is treads and plates, not blocks
    tex = {'grating': 'hdfv:block/steel_grating', 'side': 'hdfv:block/steel_stairs_side', 'particle': 'hdfv:block/steel_stairs_side'}
    for name, highs in (('steel_stairs', [([8, 0], [16, 16])]), ('steel_stairs_inner', [([8, 0], [16, 16]), ([0, 8], [8, 16])]),
                        ('steel_stairs_outer', [([8, 8], [16, 16])])):
        els = [tread([0, 7, 0], [16, 8, 16])]
        for lo, hi in highs:
            els.append(tread([lo[0], 15, lo[1]], [hi[0], 16, hi[1]]))
        els += [plate([0, 0, 0], [16, 8, 1]), plate([0, 0, 15], [16, 8, 16])]
        for lo, hi in highs:
            if lo[1] == 0:
                els.append(plate([lo[0], 8, 0], [hi[0], 15, 1]))
            if hi[1] == 16:
                els.append(plate([lo[0], 8, 15], [hi[0], 15, 16]))
        write_json(os.path.join(models, name + '.json'), {'parent': 'minecraft:block/block', 'textures': tex, 'elements': els,
                                                          'render_type': 'minecraft:cutout'})
    # the column: a post with a foot and a cap plate
    col = 'hdfv:block/steel_column'
    write_json(os.path.join(blockstates, 'steel_column.json'), {'variants': {'': {'model': col}}})
    write_json(os.path.join(models, 'steel_column.json'), {'parent': 'minecraft:block/block', 'textures': {'post': col, 'particle': col}, 'elements': [
        box([6, 0, 6], [10, 16, 10], '#post'), box([5.5, 0, 5.5], [10.5, 1, 10.5], '#post'), box([5.5, 15, 5.5], [10.5, 16, 10.5], '#post')]})
    write_json(os.path.join(blockstates, 'steel_grating.json'), {'variants': {'': {'model': 'hdfv:block/steel_grating'}}})
    write_json(os.path.join(models, 'steel_grating.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'hdfv:block/steel_grating'},
                                                             'render_type': 'minecraft:cutout'})
    # the interior lamp: a ceiling panel or a wall lamp, its diffuser full bright when lit (NeoForge's face light data)
    for lit in (False, True):
        sfx = '_on' if lit else ''
        diff = {'texture': '#lens'}
        if lit:
            diff['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
        frame = 'hdfv:block/interior_lamp_frame'
        lens = 'hdfv:block/interior_lamp' + sfx
        ceiling = {'parent': 'minecraft:block/block', 'textures': {'frame': frame, 'lens': lens, 'particle': frame}, 'elements': [
            {'from': [2, 15, 2], 'to': [14, 16, 14], 'faces': {f: {'texture': '#frame'} for f in ('north', 'south', 'east', 'west', 'up')} | {
                'down': {'texture': '#frame'}}},
            {'from': [3, 14.5, 3], 'to': [13, 15, 13], 'faces': {'down': dict(diff, uv=[3, 3, 13, 13]), 'north': {'texture': '#frame'},
                                                                 'south': {'texture': '#frame'}, 'east': {'texture': '#frame'}, 'west': {'texture': '#frame'}}}]}
        wall = {'parent': 'minecraft:block/block', 'textures': {'frame': frame, 'lens': lens, 'particle': frame}, 'elements': [
            {'from': [3, 4, 15], 'to': [13, 12, 16], 'faces': {f: {'texture': '#frame'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
            {'from': [4, 5, 14.5], 'to': [12, 11, 15], 'faces': {'north': dict(diff, uv=[4, 5, 12, 11]), 'up': {'texture': '#frame'},
                                                                 'down': {'texture': '#frame'}, 'east': {'texture': '#frame'}, 'west': {'texture': '#frame'}}}]}
        write_json(os.path.join(models, 'interior_lamp_ceiling' + sfx + '.json'), ceiling)
        write_json(os.path.join(models, 'interior_lamp_wall' + sfx + '.json'), wall)
    v = {}
    for lit in ('false', 'true'):
        sfx = '_on' if lit == 'true' else ''
        v['facing=down,lit=' + lit] = {'model': 'hdfv:block/interior_lamp_ceiling' + sfx}
        v['facing=up,lit=' + lit] = {'model': 'hdfv:block/interior_lamp_ceiling' + sfx, 'x': 180}
        for facing, y in FACINGS:
            m = {'model': 'hdfv:block/interior_lamp_wall' + sfx}
            if y:
                m['y'] = y
            v['facing=%s,lit=%s' % (facing, lit)] = m
    write_json(os.path.join(blockstates, 'interior_lamp.json'), {'variants': v})
    # the duct: a whole floor block whatever it joins (the cable's six sides are in its state)
    write_json(os.path.join(blockstates, 'cable_duct.json'), {'multipart': [{'apply': {'model': 'hdfv:block/cable_duct'}}]})
    write_json(os.path.join(models, 'cable_duct.json'), {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'top': 'hdfv:block/cable_duct_top', 'side': 'hdfv:block/cable_duct_side', 'bottom': 'hdfv:block/cable_duct_side'}})
    write_json(os.path.join(blockstates, 'tower_floor.json'), {'variants': {'': {'model': 'hdfv:block/tower_floor'}}})
    write_json(os.path.join(models, 'tower_floor.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'hdfv:block/tower_floor'}})


def box(a, b, tex):
    return {'from': a, 'to': b, 'faces': {f: {'texture': tex} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}


# ------------------------------------------------------------------------------------------------ textures

def rgb(h, a=255):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


def noisy(base, seed, amount, size=16):
    """A flat colour with a little per-pixel variation."""
    rnd = random.Random(seed)
    im = Image.new('RGBA', (size, size), base)
    px = im.load()
    for y in range(size):
        for x in range(size):
            px[x, y] = shade(base, 1 + rnd.uniform(-amount, amount))
    return im


CONCRETE, DARK_CONCRETE, STEEL, DARK, GLASS = rgb('#a9a8a2'), rgb('#8d8c86'), rgb('#9aa0a6'), rgb('#3a3e43'), rgb('#7fa6bd', 110)


def board_formed(seed, joint=True):
    """Board-formed concrete: horizontal board marks every 4 px, a tie hole pair, the joints a shade darker."""
    im = noisy(CONCRETE, seed, 0.035)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if y % 4 == 3:
                px[x, y] = shade(px[x, y], 0.93)
    if joint:
        for x in (3, 12):
            px[x, 5] = DARK_CONCRETE
            px[x, 13] = DARK_CONCRETE
    return im


def block_textures():
    out = {}
    out['control_tower_side'] = board_formed(301)
    top = noisy(DARK_CONCRETE, 302, 0.03)
    out['control_tower_top'] = top
    front = board_formed(303, joint=False)
    px = front.load()
    for y in range(3, 16):
        for x in range(4, 12):
            px[x, y] = shade(STEEL, 0.75 if (x in (4, 11) or y == 3) else 0.95)
    for y in range(5, 9):
        for x in range(6, 10):
            px[x, y] = rgb('#3f7fbf')   # a status screen
    px[9, 11] = rgb('#40ff70')
    out['control_tower_front'] = front
    out['tower_base_side'] = board_formed(311)
    out['tower_base_top'] = noisy(DARK_CONCRETE, 312, 0.03)
    shaft = board_formed(321)
    px = shaft.load()
    for x in range(16):
        px[x, 0] = shade(CONCRETE, 0.88)
    out['tower_shaft_side'] = shaft
    out['tower_shaft_top'] = noisy(DARK_CONCRETE, 322, 0.03)
    floor = noisy(CONCRETE, 331, 0.03)
    px = floor.load()
    for x in range(16):
        for y in (0, 1, 14, 15):
            px[x, y] = shade(STEEL, 0.85)
    out['tower_cab_floor_side'] = floor
    out['tower_cab_floor_top'] = noisy(rgb('#6d7177'), 332, 0.03)
    glass = Image.new('RGBA', (16, 16), GLASS)
    px = glass.load()
    for i in range(16):
        for j in (0, 15):
            px[i, j] = DARK
            px[j, i] = DARK
    for k in range(3, 9):
        px[k, 12 - k] = rgb('#d8eaf4', 140)
    out['tower_glass_side'] = glass
    out['tower_glass_top'] = glass
    roof = noisy(rgb('#5d6268'), 341, 0.04)
    px = roof.load()
    for x in range(16):
        px[x, 15] = shade(STEEL, 1.1)
    out['tower_roof_side'] = roof
    out['tower_roof_top'] = noisy(rgb('#555a60'), 342, 0.04)
    console = noisy(rgb('#4c5157'), 351, 0.03)
    px = console.load()
    for y in range(3, 9):
        for x in range(2, 14):
            px[x, y] = rgb('#1c3a2a') if (x + y) % 5 else rgb('#5fe08a')
    out['tower_console_side'] = console
    ctop = noisy(rgb('#4c5157'), 352, 0.03)
    px = ctop.load()
    for y in range(4, 12):
        for x in range(3, 13):
            px[x, y] = rgb('#20242a')
    out['tower_console_top'] = ctop
    radar = noisy(rgb('#d9dcdf'), 361, 0.03)
    px = radar.load()
    for x in range(1, 15):
        px[x, 6] = DARK
        px[x, 9] = DARK
    out['tower_radar_side'] = radar
    out['tower_radar_top'] = noisy(rgb('#c9ccd0'), 362, 0.03)
    mast = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = mast.load()
    for y in range(16):
        for x in (6, 9):
            px[x, y] = rgb('#c23b2b') if (y // 4) % 2 else rgb('#eeeeea')
        if y % 4 == 0:
            for x in range(6, 10):
                px[x, y] = STEEL
    out['tower_mast_side'] = mast
    out['tower_mast_top'] = noisy(STEEL, 372, 0.03)
    # pavement and turf
    out['apron_concrete'] = slabs(381)
    out['airfield_turf_top'] = turf_top(391)
    out['airfield_turf_side'] = turf_side(392)
    out['airfield_line_white'] = paint_line(rgb('#efefe9'), 401)
    out['airfield_line_yellow'] = paint_line(rgb('#e9b51c'), 402)
    # 0.21: inside a tower
    out.update(inside_textures())
    for name, im in out.items():
        write_png(os.path.join(BLOCKS16, name + '.png'), im)
    return len(out)


def tread(a, b):
    """0.22: a grating tread plate of a stair (grating on top and below, plate edges round it)."""
    faces = {f: {'texture': '#side'} for f in ('north', 'south', 'east', 'west')}
    faces['up'] = {'texture': '#grating'}
    faces['down'] = {'texture': '#grating'}
    return {'from': a, 'to': b, 'faces': faces}


def plate(a, b):
    """0.22: a stair's side plate (stringer)."""
    return {'from': a, 'to': b, 'faces': {f: {'texture': '#side'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}


def inside_textures():
    """Steel grating (bars both ways, the holes see-through), the stair's side plate, the column, the lamp's frame and
    diffuser (lit: warm white), the duct's checker-plate cover over concrete, terrazzo."""
    out = {}
    steel, dark = rgb('#747b82'), rgb('#4b5157')   # 0.22: darker (0.21's #8d949b read near white in a lit cab)
    g = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = g.load()
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0 or x == 15 or y == 15:
                px[x, y] = shade(steel, 0.9 if (x + y) % 3 else 1.05)
    for i in range(16):
        px[i, 0] = px[0, i] = px[i, 15] = px[15, i] = dark
    out['steel_grating'] = g
    side = noisy(steel, 501, 0.04)
    px = side.load()
    for i in range(16):
        px[i, 0] = px[i, 15] = dark
        px[0, i] = px[15, i] = shade(steel, 0.8)
    for x in (4, 11):
        px[x, 8] = dark
    out['steel_stairs_side'] = side
    col = noisy(rgb('#6e757c'), 502, 0.04)
    px = col.load()
    for y in range(0, 16, 4):
        for x in range(16):
            px[x, y] = shade(px[x, y], 0.85)
    out['steel_column'] = col
    frame = noisy(rgb('#d6d8da'), 503, 0.02)
    out['interior_lamp_frame'] = frame
    lens = Image.new('RGBA', (16, 16), rgb('#e7e3d6'))
    px = lens.load()
    for y in range(16):
        for x in range(16):
            if x % 5 == 0 or y % 5 == 0:
                px[x, y] = rgb('#cfcbc0')
    out['interior_lamp'] = lens
    on = Image.new('RGBA', (16, 16), rgb('#fff6dc'))
    px = on.load()
    for y in range(16):
        for x in range(16):
            if x % 5 == 0 or y % 5 == 0:
                px[x, y] = rgb('#f3e2b8')
    out['interior_lamp_on'] = on
    top = noisy(rgb('#9aa1a7'), 504, 0.03)
    px = top.load()
    for y in range(16):
        for x in range(16):
            if (x // 2 + y // 2) % 2 == 0 and (x + y) % 2 == 0:
                px[x, y] = shade(px[x, y], 1.12)
    for i in range(16):
        px[i, 0] = px[i, 15] = rgb('#4c5157')
    out['cable_duct_top'] = top
    out['cable_duct_side'] = noisy(CONCRETE, 505, 0.03)
    rnd = random.Random(506)
    ter = noisy(rgb('#aaa498'), 506, 0.03)   # 0.22: a mid-tone terrazzo (0.21's #d9d6cf was near white)
    px = ter.load()
    for _ in range(26):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[x, y] = rgb(rnd.choice(['#8f8a80', '#b7a58b', '#6f7378', '#c9b9a0', '#9c9f99']))
    for i in range(16):
        px[i, 0] = px[0, i] = shade(rgb('#aaa498'), 0.88)
    out['tower_floor'] = ter
    return out


def slabs(seed):
    """Apron concrete: light, a sawn joint along two edges (slabs a block square), a few darker aggregate flecks."""
    im = noisy(rgb('#b9b7b0'), seed, 0.03)
    px = im.load()
    rnd = random.Random(seed + 1)
    for _ in range(9):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[x, y] = shade(px[x, y], 0.86)
    for i in range(16):
        px[i, 0] = shade(px[i, 0], 0.8)
        px[0, i] = shade(px[0, i], 0.8)
    return im


def turf_top(seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            g = rnd.uniform(0.85, 1.12)
            px[x, y] = shade(rgb('#6b8f3c' if rnd.random() > 0.15 else '#7da24a'), g)
    return im


def turf_side(seed):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            edge = 3 + (1 if rnd.random() > 0.6 else 0)
            c = rgb('#6b8f3c') if y < edge else rgb('#7a5a3c')
            px[x, y] = shade(c, rnd.uniform(0.88, 1.1))
    return im


def paint_line(colour, seed):
    """Road paint on asphalt's dark: worn a little (a few pixels of the dark showing)."""
    rnd = random.Random(seed)
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            worn = rnd.random() < 0.05
            px[x, y] = shade(rgb('#2d2d2f'), 1.1) if worn else shade(colour, rnd.uniform(0.94, 1.03))
    return im


def paint_icon(colour, band):
    """A line-marking paint tin (the style of items16's cans): grey tin, the paint's colour on the lid and a band, a spout."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    body, rim, handle = rgb('#a8aeb4'), rgb('#6b7077'), rgb('#3b3f45')
    c = rgb(colour)
    b = rgb(band) if band else c
    for y in range(5, 15):
        for x in range(3, 13):
            px[x, y] = b if 8 <= y <= 11 else body
            if x == 12 or y == 14:
                px[x, y] = shade(px[x, y], 0.75)
    for x in range(3, 13):
        px[x, 4] = rim
    for x in range(4, 12):
        px[x, 3] = c
    px[5, 2], px[6, 1], px[7, 1], px[8, 1], px[9, 1], px[10, 2] = handle, handle, handle, handle, handle, handle
    px[12, 6], px[13, 5] = rim, rim
    px[13, 6] = c   # a drip at the spout
    return im


def item_textures():
    for id_, en, zh, colour, band, r in PAINTS:
        write_png(os.path.join(ASSETS, 'textures', 'item', id_ + '.png'), paint_icon(colour, band))


def villager():
    """The tower controller's clothes over a villager: a navy waistcoat with a high-visibility band over a white shirt,
    navy trousers (over the robe), sleeves, and a headset (a band over the hat, ear cups, a microphone boom)."""
    navy, navy_dark, hivis, silver, shirt, black, grey = rgb('#22304f'), rgb('#18233b'), rgb('#e8e030'), rgb('#c8ccd0'), rgb('#ecebe6'), rgb('#18191b'), rgb('#55595e')
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = im.load()

    def fill(x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = c

    # the body (16, 20; 8 x 12 x 6): waistcoat all round, a band across, the shirt's collar and middle at the front
    fill(16, 26, 44, 38, navy)
    fill(22, 20, 30, 26, navy)
    for x in range(16, 44):
        px[x, 32] = hivis
        px[x, 33] = silver
    fill(25, 26, 27, 32, shirt)
    fill(24, 26, 28, 27, shirt)
    # the robe (0, 38; 8 x 20 x 6) as trousers and a coat's hem
    fill(0, 44, 28, 64, navy_dark)
    fill(6, 38, 14, 44, navy_dark)
    for y in range(44, 64):
        px[10, y] = shade(navy_dark, 0.8)   # the trouser legs' crease at the front
    # the crossed arms (44, 22 and 40, 38): sleeves, the hands left bare
    fill(44, 22, 60, 30, navy)
    fill(44, 38, 60, 42, navy)
    fill(44, 42, 52, 46, navy)
    for x in range(44, 60):
        px[x, 27] = hivis
    # the headset on the hat layer (32, 0; 8 x 10 x 8): over the top, down both sides, cups, the boom to the mouth
    fill(40, 3, 48, 5, black)
    fill(34, 8, 36, 13, black)
    fill(52, 8, 54, 13, black)
    fill(33, 12, 37, 15, grey)
    fill(51, 12, 55, 15, grey)
    for x in range(40, 44):
        px[x, 15] = black
    px[44, 15] = grey
    write_png(os.path.join(ASSETS, 'textures', 'entity', 'villager', 'profession', 'tower_controller.png'), im)
    write_png(os.path.join(ASSETS, 'textures', 'entity', 'zombie_villager', 'profession', 'tower_controller.png'), im)


def main():
    blocks = data()
    for lang in ('en', 'zh'):
        insert_lang(os.path.join(ASSETS, 'lang', 'en_us.json' if lang == 'en' else 'zh_tw.json'), lang)
    n = block_textures()
    item_textures()
    villager()
    print('airport assets:', len(blocks), 'blocks,', n, '16 px sources,', written, 'files written')


main()
