"""hdfv 0.25: the data and words of vehicle damage (core damage/Conditions, neoforge damage/VehicleDamage), written from
one spec: the damage type of a crash's injuries (data/hdfv/damage_type/crash.json) and its place among the types armour
does not stop (as flying into a wall), and both languages (the conditions' names, the status screen, the dashboard's
warnings, the admin command, the settings, the death messages; new keys inserted after their anchors, the files' line
order kept alike). Files are rewritten only when they change.

    py -3 tools/damage_assets.py
    py -3 tools/zh_cn.py              (the Simplified Chinese)
"""
import json
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
RES = os.path.join(ROOT, 'neoforge', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'hdfv')
DATA = os.path.join(RES, 'data')

written = 0

# the conditions (core Conditions.NAMES, in its order): id, English, Traditional Chinese
CONDITIONS = [
    ('structure', 'Structure', '結構'),
    ('engine', 'Engine', '引擎本體'),
    ('hot_section', 'Hot section', '熱段'),
    ('ancillaries', 'Ancillaries', '附件'),
    ('motor', 'Motor', '電動機'),
    ('gears', 'Gears', '齒輪'),
    ('clutch', 'Clutch', '離合器'),
    ('blades', 'Blades', '槳葉'),
    ('tyres', 'Tyres', '輪胎'),
    ('brakes', 'Brakes', '煞車'),
    ('suspension', 'Suspension', '懸吊'),
    ('strut', 'Strut', '支柱'),
    ('tank', 'Tank', '油箱'),
]


def write(path, data):
    global written
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and open(path, 'rb').read() == data:
        return
    open(path, 'wb').write(data)
    written += 1


def write_json(path, obj):
    write(path, (json.dumps(obj, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))


def lang_groups():
    """(anchor key, [(key, en, zh)]) in the order they go in."""
    conditions = [('hdfv.condition.' + c[0], c[1], c[2]) for c in CONDITIONS]
    status = [
        ('hdfv.status.title', 'Vehicle status', '載具狀態'),
        ('hdfv.status.part', '%s: %s', '%s：%s'),
        ('hdfv.status.mass', 'Mass %s kg', '質量 %s kg'),
        ('hdfv.status.fuel', 'Fuel %s / %s kg', '燃料 %s／%s kg'),
        ('hdfv.status.no_fuel', 'No fuel tank', '沒有油箱'),
        ('hdfv.status.tab.condition', 'Condition', '狀況'),
        ('hdfv.status.replace', 'replace', '需更換'),
        ('hdfv.status.nothing', 'Nothing on this vehicle wears or can be damaged.', '這台載具沒有會磨損或損壞的零件。'),
        ('hdfv.hud.warning.brakes', 'BRAKE', '煞車'),
        ('hdfv.hud.warning.tyres', 'TYRE', '爆胎'),
        ('hdfv.hud.warning.transmission', 'GEARBOX', '變速箱'),
        ('hdfv.hud.warning.structure', 'BODY', '車體'),
        ('hdfv.hud.warning.leak', 'FUEL LEAK', '漏油'),
        ('death.attack.hdfv.crash', '%1$s died in a crash', '%1$s 死於車禍'),
        ('death.attack.hdfv.crash.player', '%1$s died in a crash while fighting %2$s', '%1$s 在與 %2$s 戰鬥時死於車禍'),
    ]
    commands = [
        ('command.hdfv.vehicle.condition.none', 'There is no vehicle within 16 blocks.', '16 格內沒有載具。'),
        ('command.hdfv.vehicle.condition.header', '%s: the most used condition is %s %% used', '%s：用掉最多的項目已用掉 %s %%'),
        ('command.hdfv.vehicle.condition.part', '%s (%s): %s', '%s（%s）：%s'),
        ('command.hdfv.vehicle.condition.set', '%s: %s %s is now %s %% used', '%s：%s 的 %s 現在已用掉 %s %%'),
        ('command.hdfv.vehicle.condition.unknown', 'This vehicle has no %s %s. It has: %s', '這台載具沒有 %s 的 %s。它有：%s'),
        ('command.hdfv.vehicle.condition.repaired', '%s: %s condition(s) set back to new', '%s：%s 個項目恢復成全新'),
    ]
    config = [
        ('hdfv.configuration.damage', 'Damage', '損壞'),
        ('hdfv.configuration.enabled', 'Vehicles can be damaged', '載具會損壞'),
        ('hdfv.configuration.collisionScale', 'Collision damage scale', '碰撞損壞倍率'),
        ('hdfv.configuration.wearScale', 'Wear scale', '磨損倍率'),
        ('hdfv.configuration.attackScale', 'Attack, explosion and fire damage scale', '攻擊、爆炸與火的損壞倍率'),
        ('hdfv.configuration.corrosionScale', 'Corrosion and water damage scale', '腐蝕與進水的損壞倍率'),
        ('hdfv.configuration.occupantInjury', 'Crashes and fire hurt those aboard', '車禍與火會傷到乘員'),
        ('hdfv.configuration.explosions', 'Fuel can explode', '燃料會爆炸'),
    ]
    return [('hdfv.storage.mass.stack', conditions + status), ('command.hdfv.vehicle.io', commands), ('hdfv.configuration.itemMassScale', config)]


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


def add_tag(path, values):
    obj = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {'values': []}
    for v in values:
        if v not in obj['values']:
            obj['values'].append(v)
    write_json(path, obj)


def main():
    # a crash's injuries: no exhaustion, not scaled by difficulty (the physics set them), past armour as flying into a wall
    write_json(os.path.join(DATA, 'hdfv', 'damage_type', 'crash.json'), {'message_id': 'hdfv.crash', 'exhaustion': 0.0, 'scaling': 'never'})
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'damage_type', 'bypasses_armor.json'), ['hdfv:crash'])
    insert_lang(os.path.join(ASSETS, 'lang', 'en_us.json'), 'en')
    insert_lang(os.path.join(ASSETS, 'lang', 'zh_tw.json'), 'zh')
    print('damage assets:', len(CONDITIONS), 'conditions,', written, 'files written')


if __name__ == '__main__':
    main()
