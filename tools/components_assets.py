"""hdfv 0.23: the assets of the standard components, the intermediates and the electric smelting machines, from one
spec: item models and their 16 px icons, the two machines' blockstates (facing, lit), item models, loot tables and tool
tags, the common tags other mods use (c:plates/steel, c:wires/copper, c:coal_coke, c:ingots/aluminum), and both
languages (keys inserted after their group's anchor, the files' line order kept alike, as machining_assets.py does).
The machines' block models are tools/blender/machines.py's. Needs Pillow; files are rewritten only when they change.

    py -3 tools/components_assets.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import machining_assets as ma  # noqa: E402  (its helpers: write, write_json, write_png, Canvas, rgb, shade; it has a main guard)

ASSETS, DATA = ma.ASSETS, ma.DATA
rgb, shade, Canvas = ma.rgb, ma.shade, ma.Canvas

# ------------------------------------------------------------------------------------------------ the spec

# the standard components (core Component): id, English, Chinese, icon
COMPONENTS = [
    ('steel_plate', 'Steel Plate', '鋼板', 'plate'), ('electrical_steel', 'Electrical Steel', '矽鋼片', 'laminations'),
    ('copper_wire', 'Copper Wire', '銅線', 'coil'), ('bed_casting', 'Bed Casting', '床身鑄件', 'bed'),
    ('frame_casting', 'Frame Casting', '機架鑄件', 'frame'), ('cylinder_casting', 'Cylinder Casting', '汽缸鑄件', 'cylinder_raw'),
    ('cast_gear', 'Cast Gear', '鑄齒輪', 'gear_cast'), ('cast_pulley', 'Cast Pulley', '皮帶輪', 'pulley'),
    ('bronze_bushing', 'Bronze Bushing', '青銅軸套', 'bushing'), ('forged_shaft', 'Forged Shaft', '鍛造軸', 'shaft_forged'),
    ('forged_crank', 'Forged Crank', '鍛造曲柄', 'crank'), ('turned_shaft', 'Turned Shaft', '車削軸', 'shaft'),
    ('leadscrew', 'Leadscrew', '導螺桿', 'screw'), ('fastener_kit', 'Fastener Kit', '螺栓組', 'bolts'),
    ('steel_tube', 'Steel Tube', '鋼管', 'tube'), ('planed_slideway', 'Planed Slideway', '刨削導軌', 'way'),
    ('valve_body', 'Valve Body', '閥體', 'valve'), ('bored_cylinder', 'Bored Cylinder', '搪孔汽缸', 'cylinder'),
    ('cut_gear', 'Cut Gear', '切削齒輪', 'gear'), ('ball_bearing', 'Ball Bearing', '滾珠軸承', 'bearing'),
    ('ground_slideway', 'Ground Slideway', '磨削導軌', 'way_ground'), ('spindle', 'Spindle', '主軸', 'spindle'),
    ('ball_screw', 'Ball Screw', '滾珠螺桿', 'ball_screw'), ('circuit_board', 'Circuit Board', '電路基板', 'pcb'),
    ('linear_guide', 'Linear Guide', '線性滑軌', 'rail'), ('precision_spindle', 'Precision Spindle', '精密主軸', 'spindle_fine'),
    ('friction_lining', 'Friction Lining', '來令片', 'lining'),   # 0.25: the repair shop relines brakes and clutches with it
]
# the intermediates (core Intermediate): id, English, Chinese, icon
INTERMEDIATES = [
    ('electric_motor_unit', 'Electric Motor Unit', '電動機組件', 'motor'), ('transformer', 'Transformer', '變壓器', 'transformer'),
    ('control_board', 'Control Board', '控制板', 'board'), ('servo_motor', 'Servo Motor', '伺服馬達', 'servo'),
    ('pump_unit', 'Pump Unit', '泵組', 'pump'), ('hydraulic_cylinder', 'Hydraulic Cylinder', '油壓缸', 'hydraulic'),
    ('gear_train', 'Gear Train', '齒輪組', 'gearbox'), ('heat_exchanger', 'Heat Exchanger', '熱交換器', 'exchanger'),
    ('alumina', 'Alumina', '氧化鋁', 'powder_white'), ('chromium_oxide', 'Chromium Oxide', '氧化鉻', 'powder_green'),
    ('magnesia', 'Magnesia', '氧化鎂', 'powder_cream'), ('ferrosilicon', 'Ferrosilicon', '矽鐵', 'lump_grey'),
    ('ferrochrome', 'Ferrochrome', '鉻鐵', 'lump_dark'), ('titanium_slag', 'Titanium Slag', '鈦渣', 'lump_black'),
    ('titanium_sponge', 'Titanium Sponge', '海綿鈦', 'sponge'), ('magnesium_chloride', 'Magnesium Chloride', '氯化鎂', 'flakes'),
    ('carbon_anode', 'Carbon Anode', '碳陽極', 'anode'),
]
GRADES = {
    'a': ('Grade A: the foundry and the forging press only (castings, forgings, rolled sheet, wire)', 'A 級：只要鑄造爐與鍛壓機（鑄件、鍛件、軋板、拉線）'),
    'b': ('Grade B: the lathe, the drill press and the planer', 'B 級：車床、鑽床、鉋床'),
    'c': ('Grade C: the milling machine, the boring mill, the grinders, the gear and thread machines, the composite oven',
          'C 級：銑床、搪床、磨床、齒輪機、螺紋機、複材爐'),
    'd': ('Grade D: the CNC', 'D 級：CNC'),
}
MACHINES = [('arc_furnace', 'Electric Arc Furnace', '電弧爐'), ('electrolysis_cell', 'Electrolysis Cell', '電解槽')]
SMELTER_STATES = [('idle', 'Idle', '待機'), ('no_recipe', 'Put in what a batch takes', '放入一批的材料'), ('no_power', 'No power', '沒電'),
                  ('output_full', 'Take out what it made', '取出產物'), ('working', 'Working', '運轉中')]
WHERE = [('crafting', 'the crafting table', '工作台'), ('assembling', 'the assembly bench', '組裝台'), ('alloying', 'the alloy furnace', '合金爐'),
         ('electric_furnace', 'the arc furnace', '電弧爐'), ('electrolysis', 'the electrolysis cell', '電解槽'),
         ('machine', 'the machines, from its drawing', '機器（照圖紙）'), ('world', 'the world', '世界中取得'), ('smelting', 'a furnace', '熔爐'),
         ('refinery', 'the refinery', '煉油廠'), ('other', 'elsewhere', '其他')]


def lang_groups():
    g = []
    items = [('item.hdfv.' + i, e, z) for i, e, z, _ in COMPONENTS + INTERMEDIATES]
    g.append(('item.hdfv.stock_bit', items))
    g.append(('block.hdfv.alloy_furnace', [('block.hdfv.' + i, e, z) for i, e, z in MACHINES]))
    g.append(('hdfv.category.other', [('hdfv.category.component', 'Standard components', '標準件')]))
    g.append(('hdfv.kind.seat', [('hdfv.kind.component_' + i, e, z) for i, e, z, _ in COMPONENTS]))
    g.append(('hdfv.param.design.massKg', [('hdfv.param.design.kind', 'Component', '標準件')]
              + [('hdfv.choice.design.kind.' + i, e, z) for i, e, z, _ in COMPONENTS]))
    g.append(('hdfv.sample.liquid_hydrogen_tank', [('hdfv.sample.' + i, e + ' drawing', z + '圖紙') for i, e, z, _ in COMPONENTS]))
    g.append(('hdfv.machine.time_left', [
        ('hdfv.machine.state.no_recipe', 'Put in a drawing, or the parts of a machine', '放入圖紙，或放齊一台機器的零件'),
        ('hdfv.machine.builds', 'Builds %s %s', '組裝：%s %s')]
        + [('hdfv.workpiece.' + i, e, z) for i, e, z, _ in COMPONENTS]))
    g.append(('hdfv.gas.argon', [('hdfv.smelter.state.' + k, e, z) for k, e, z in SMELTER_STATES]
              + [('hdfv.smelter.energy', '%s kWh a batch', '每批 %s kWh')]
              + [('hdfv.tier', 'Tech stage %s: made at %s', '科技階段 T%s：在%s做'), ('hdfv.tier.unreached', 'Not reachable by recipes', '無法由配方取得')]
              + [('hdfv.tier.where.' + k, e, z) for k, e, z in WHERE]))
    g.append(('hdfv.jei.blueprint', [
        ('hdfv.jei.assembling', 'Assembly bench', '組裝台'),
        ('hdfv.jei.labour', 'Fitting: %s h of work (a fitter at the bench)', '工時：%s 小時（要有人在組裝台）'),
        ('hdfv.jei.smelting', '%s kWh at %s kW: %s h of work', '%s kWh，%s kW：%s 小時'),
        ('hdfv.jei.component', 'A standard component: save its drawing from the design screen (Standard components), put it in the machines with stock; '
                               'the last machine of its route hands out the component.',
         '標準件：在設計畫面的「標準件」存成圖紙，連同料件放上機器照工序做，最後一台機器直接出這個零件。')]
        + [('hdfv.jei.component.' + k, e, z) for k, (e, z) in GRADES.items()]))
    g.append(('hdfv.configuration.consumables', [('hdfv.configuration.chipRecovery', 'Chips back as stock', '切屑回收')]))
    g.append(('hdfv.refinery.unit.reformer', [('hdfv.refinery.unit.methanol', 'Methanol (steam reforming and synthesis)', '甲醇（蒸汽重組與合成）')]))
    return g


def insert_lang(path, lang):
    """machining_assets.insert_lang with this file's groups."""
    saved = ma.lang_groups
    ma.lang_groups = lang_groups
    try:
        ma.insert_lang(path, lang)
    finally:
        ma.lang_groups = saved


# ------------------------------------------------------------------------------------------------ icons

STEEL, DARK, BRIGHT = rgb('#a8aeb4'), rgb('#3b3f45'), rgb('#d9dde1')
IRON, BRONZE, COPPER, GREEN, GOLD = rgb('#6a6e73'), rgb('#b8954a'), rgb('#c07040'), rgb('#3e7a46'), rgb('#e0b84a')


def icon(kind):
    c = Canvas((0, 0, 0, 0))
    if kind == 'plate':
        c.rect(2, 5, 13, 11, STEEL); c.frame(2, 5, 13, 11, shade(STEEL, 0.7)); c.rect(3, 6, 12, 6, BRIGHT)
    elif kind == 'laminations':
        for y in range(4, 13, 2):
            c.rect(3, y, 12, y, rgb('#7d8590')); c.rect(3, y + 1, 12, y + 1, rgb('#4d545c'))
    elif kind == 'coil':
        c.circle(8, 8, 6, shade(COPPER, 0.7), fill=COPPER); c.circle(8, 8, 2, shade(COPPER, 0.6), fill=(0, 0, 0, 0))
        for r in (3, 5):
            c.circle(8, 8, r, shade(COPPER, 1.2))
    elif kind == 'bed':
        c.rect(1, 6, 14, 10, IRON); c.rect(1, 6, 14, 6, shade(IRON, 1.3)); c.rect(3, 10, 4, 13, IRON); c.rect(11, 10, 12, 13, IRON)
    elif kind == 'frame':
        c.rect(3, 3, 12, 13, IRON); c.rect(5, 5, 10, 11, (0, 0, 0, 0)); c.frame(3, 3, 12, 13, shade(IRON, 0.7))
    elif kind in ('cylinder_raw', 'cylinder'):
        c.rect(4, 3, 11, 13, IRON); c.rect(4, 3, 11, 4, shade(IRON, 1.3))
        for y in (6, 8, 10):
            c.rect(3, y, 12, y, shade(IRON, 0.8))
        c.rect(6, 1, 9, 2, DARK if kind == 'cylinder' else IRON)
        if kind == 'cylinder':
            c.rect(6, 4, 9, 12, BRIGHT)
    elif kind in ('gear_cast', 'gear'):
        col = IRON if kind == 'gear_cast' else STEEL
        c.circle(8, 8, 5, shade(col, 0.7), fill=col)
        for x, y in ((8, 1), (8, 14), (1, 8), (14, 8), (3, 3), (12, 3), (3, 12), (12, 12)):
            c.rect(x, y, x + 1, y + 1, col)
        c.rect(7, 7, 8, 8, DARK)
    elif kind == 'pulley':
        c.circle(8, 8, 6, shade(IRON, 0.7), fill=IRON); c.circle(8, 8, 4, DARK); c.rect(7, 7, 8, 8, DARK)
    elif kind == 'bushing':
        c.circle(8, 8, 5, shade(BRONZE, 0.7), fill=BRONZE); c.circle(8, 8, 2, shade(BRONZE, 0.5), fill=(0, 0, 0, 0))
    elif kind in ('shaft_forged', 'shaft', 'spindle', 'spindle_fine'):
        col = {'shaft_forged': IRON, 'shaft': STEEL, 'spindle': BRIGHT, 'spindle_fine': rgb('#eef2f5')}[kind]
        for i in range(12):
            c.rect(2 + i, 12 - i, 3 + i, 13 - i, col)
        if kind.startswith('spindle'):
            c.rect(10, 3, 13, 6, shade(col, 0.8))
    elif kind == 'crank':
        c.rect(2, 7, 7, 9, IRON); c.rect(6, 4, 9, 12, IRON); c.rect(8, 7, 13, 9, IRON)
    elif kind in ('screw', 'ball_screw'):
        col = STEEL if kind == 'screw' else BRIGHT
        for i in range(12):
            c.rect(2 + i, 12 - i, 3 + i, 13 - i, col)
            if i % 2 == 0:
                c.set(3 + i, 13 - i, shade(col, 0.6))
        if kind == 'ball_screw':
            c.rect(7, 6, 10, 9, DARK)
    elif kind == 'bolts':
        for x, y in ((3, 3), (9, 4), (5, 9), (11, 10)):
            c.rect(x, y, x + 2, y + 1, STEEL); c.rect(x + 1, y + 2, x + 1, y + 4, shade(STEEL, 0.8))
    elif kind == 'tube':
        for i in range(11):
            c.rect(2 + i, 11 - i, 4 + i, 13 - i, STEEL)
        c.circle(13, 3, 1.5, DARK)
    elif kind in ('way', 'way_ground', 'rail'):
        col = {'way': IRON, 'way_ground': STEEL, 'rail': BRIGHT}[kind]
        c.rect(1, 7, 14, 10, col); c.rect(1, 7, 14, 7, shade(col, 1.3))
        if kind == 'rail':
            c.rect(5, 5, 9, 7, DARK)
    elif kind == 'valve':
        c.rect(3, 6, 12, 10, STEEL); c.rect(6, 3, 9, 6, shade(STEEL, 0.8)); c.rect(5, 1, 10, 2, rgb('#b33a2a'))
    elif kind == 'bearing':
        c.circle(8, 8, 6, shade(STEEL, 0.7), fill=STEEL); c.circle(8, 8, 3, DARK, fill=(0, 0, 0, 0))
        for x, y in ((8, 3), (13, 8), (8, 13), (3, 8)):
            c.set(x, y, BRIGHT)
    elif kind == 'pcb':
        c.rect(2, 3, 13, 12, GREEN); c.frame(2, 3, 13, 12, shade(GREEN, 0.7))
        for x, y in ((4, 5), (7, 5), (10, 8), (5, 9)):
            c.rect(x, y, x + 1, y, GOLD)
    elif kind == 'motor':
        c.rect(2, 4, 11, 12, rgb('#3e5f86')); c.rect(11, 7, 14, 9, STEEL)
        for x in (4, 6, 8):
            c.rect(x, 4, x, 12, shade(rgb('#3e5f86'), 0.7))
    elif kind == 'transformer':
        c.rect(2, 4, 13, 12, rgb('#4d545c')); c.rect(4, 6, 6, 10, COPPER); c.rect(9, 6, 11, 10, COPPER)
    elif kind == 'board':
        c.rect(2, 3, 13, 12, GREEN)
        c.rect(5, 5, 9, 9, DARK)
        for x, y in ((3, 4), (11, 4), (3, 11), (11, 11)):
            c.set(x, y, GOLD)
        c.rect(10, 7, 12, 8, rgb('#d23a2a'))
    elif kind == 'servo':
        c.rect(2, 4, 10, 12, rgb('#2b2d30')); c.rect(10, 7, 14, 9, STEEL); c.rect(3, 2, 7, 3, rgb('#d8a82a'))
    elif kind == 'pump':
        c.circle(7, 9, 4, shade(rgb('#2f5482'), 0.7), fill=rgb('#2f5482')); c.rect(10, 7, 14, 9, STEEL); c.rect(6, 2, 8, 5, STEEL)
    elif kind == 'hydraulic':
        c.rect(1, 6, 9, 10, rgb('#d8a82a')); c.rect(9, 7, 14, 9, BRIGHT); c.rect(3, 4, 4, 6, DARK)
    elif kind == 'gearbox':
        c.rect(2, 3, 13, 13, IRON); c.circle(6, 7, 2, STEEL, fill=STEEL); c.circle(10, 10, 2, STEEL, fill=STEEL)
    elif kind == 'exchanger':
        c.rect(1, 5, 14, 11, STEEL)
        for x in (3, 6, 9, 12):
            c.rect(x, 5, x, 11, shade(STEEL, 0.7))
        c.rect(2, 2, 3, 5, DARK); c.rect(12, 11, 13, 14, DARK)
    elif kind.startswith('powder'):
        col = {'powder_white': rgb('#ecebe6'), 'powder_green': rgb('#4f7a3a'), 'powder_cream': rgb('#e6dcc0')}[kind]
        for y in range(8, 14):
            w = (y - 7) * 1
            c.rect(8 - w, y, 7 + w, y, shade(col, 0.85 + 0.03 * (y % 3)))
        c.rect(6, 7, 9, 7, col)
    elif kind.startswith('lump'):
        col = {'lump_grey': rgb('#8a8f96'), 'lump_dark': rgb('#585c63'), 'lump_black': rgb('#2c2a2b')}[kind]
        c.rect(3, 6, 12, 12, col); c.rect(5, 4, 10, 6, col); c.rect(4, 6, 8, 7, shade(col, 1.3)); c.set(10, 9, shade(col, 0.7))
    elif kind == 'sponge':
        c.rect(3, 4, 12, 12, rgb('#9da2a8'))
        for x, y in ((4, 5), (7, 7), (10, 5), (5, 10), (9, 10), (11, 8)):
            c.set(x, y, rgb('#5d6268'))
    elif kind == 'flakes':
        for x, y in ((3, 4), (8, 3), (11, 6), (5, 8), (10, 10), (4, 12), (8, 12)):
            c.rect(x, y, x + 2, y + 1, rgb('#f2f0ea'))
    elif kind == 'lining':
        # a brake pad: its steel backing plate, the friction block moulded on it, two rivets
        c.rect(1, 5, 14, 11, STEEL); c.frame(1, 5, 14, 11, shade(STEEL, 0.7))
        c.rect(3, 6, 12, 10, rgb('#4a4038')); c.rect(3, 6, 12, 6, rgb('#6a5c50'))
        c.set(5, 8, BRIGHT); c.set(10, 8, BRIGHT)
    elif kind == 'anode':
        c.rect(4, 3, 11, 13, rgb('#262628')); c.rect(4, 3, 11, 4, rgb('#3c3c40')); c.rect(7, 1, 8, 3, STEEL)
    return c.im


# ------------------------------------------------------------------------------------------------ data

def blockstate(id_):
    v = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for lit in ('false', 'true'):
            m = {'model': 'hdfv:block/' + id_ + ('_on' if lit == 'true' else '')}
            if y:
                m['y'] = y
            v['facing=%s,lit=%s' % (facing, lit)] = m
    return {'variants': v}


def main():
    item_models = os.path.join(ASSETS, 'models', 'item')
    for i, e, z, ic in COMPONENTS + INTERMEDIATES:
        ma.write_json(os.path.join(item_models, i + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'hdfv:item/' + i}})
        ma.write_png(os.path.join(ASSETS, 'textures', 'item', i + '.png'), icon(ic))
    loot = os.path.join(DATA, 'hdfv', 'loot_table', 'blocks')
    for i, e, z in MACHINES:
        ma.write_json(os.path.join(ASSETS, 'blockstates', i + '.json'), blockstate(i))
        ma.write_json(os.path.join(item_models, i + '.json'), {'parent': 'hdfv:block/' + i})
        ma.write_json(os.path.join(loot, i + '.json'), ma.block_drop(i))
    ma.add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), ['hdfv:' + m[0] for m in MACHINES])
    ma.add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'needs_stone_tool.json'), ['hdfv:' + m[0] for m in MACHINES])
    # the common tags (0.23, other mods' compatibility)
    tags = os.path.join(DATA, 'c', 'tags', 'item')
    ma.add_tag(os.path.join(tags, 'plates', 'steel.json'), ['hdfv:steel_plate'])
    ma.add_tag(os.path.join(tags, 'plates.json'), ['#c:plates/steel'])
    ma.add_tag(os.path.join(tags, 'wires', 'copper.json'), ['hdfv:copper_wire'])
    ma.add_tag(os.path.join(tags, 'wires.json'), ['#c:wires/copper'])
    ma.add_tag(os.path.join(tags, 'coal_coke.json'), ['hdfv:coke'])
    ma.add_tag(os.path.join(tags, 'ingots', 'aluminum.json'), ['hdfv:aluminium_ingot'])
    ma.add_tag(os.path.join(tags, 'ingots.json'), ['#c:ingots/aluminum'])
    ma.add_tag(os.path.join(tags, 'raw_materials', 'garnet.json'), ['hdfv:raw_garnet'])
    ma.add_tag(os.path.join(tags, 'raw_materials.json'), ['#c:raw_materials/garnet'])
    ma.add_tag(os.path.join(tags, 'gears.json'), ['hdfv:cut_gear', 'hdfv:cast_gear'])
    # the fluids under the common names other mods use (HdfvFluidRegistry.COMMON reads theirs the other way)
    fluids = {'crude_oil': 'crude_oil', 'oil': 'crude_oil', 'heavy_oil': 'heavy_crude_oil', 'lpg': 'lpg', 'naphtha': 'naphtha',
              'gasoline': 'gasoline', 'kerosene': 'kerosene', 'diesel': 'diesel', 'fuel_oil': 'fuel_oil', 'lubricant': 'lubricating_oil',
              'methanol': 'methanol', 'ethylene': 'ethylene', 'propylene': 'propylene', 'hydrogen': 'hydrogen'}
    for tag, fluid in fluids.items():
        values = ['hdfv:' + fluid, 'hdfv:' + fluid + '_flowing']
        if tag == 'gasoline':
            values += ['hdfv:premium_gasoline', 'hdfv:premium_gasoline_flowing']
        ma.add_tag(os.path.join(DATA, 'c', 'tags', 'fluid', tag + '.json'), values)
    for lang in ('en', 'zh'):
        insert_lang(os.path.join(ASSETS, 'lang', 'en_us.json' if lang == 'en' else 'zh_tw.json'), lang)
    print('components assets:', len(COMPONENTS), 'components,', len(INTERMEDIATES), 'intermediates,', len(MACHINES), 'machines,', ma.written, 'files written')


if __name__ == '__main__':
    main()
