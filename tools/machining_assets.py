"""hdfv 0.17: the assets and data of the machine families (all the machining machines), written from one spec so a
hundred-odd blocks and items stay consistent: blockstates, block and item models, loot tables, recipes, tags, the ore's
worldgen, both languages (new keys inserted after their group's last existing key, the files' order kept) and the
16 px texture sources (block sides into tools/blender/blocks16, redrawn at 64 px by `scripts/blender.ps1 machines`;
ores and items straight into the mod's 16 px textures). Needs Pillow. Files are rewritten only when they change.
0.23: recipes are written by tools/recipes.py, not here. Checked by MachiningAssetsTest.

    py -3 tools/machining_assets.py
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
    """Writes bytes when they differ from the file's."""
    global written
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and open(path, 'rb').read() == data:
        return
    open(path, 'wb').write(data)
    written += 1


def write_json(path, obj):
    write(path, (json.dumps(obj, indent=2, ensure_ascii=False) + '\n').encode('utf-8'))


def write_png(path, im):
    b = io.BytesIO()
    im.save(b, 'PNG', optimize=True)
    if os.path.exists(path):
        try:
            if list(Image.open(path).convert('RGBA').getdata()) == list(im.getdata()):
                return
        except OSError:
            pass
    write(path, b.getvalue())


# ------------------------------------------------------------------------------------------------ the spec

# the families' controllers: id, English, Chinese, panel colour, icon, recipe (pattern, key)
I, B = 'minecraft:iron_ingot', 'minecraft:iron_block'
FAMILIES = [
    ('grinder', 'Grinder', '磨床', '#5f7f6c', 'wheel', (['GGP', 'IBI', 'I I'], {'G': 'minecraft:grindstone', 'P': 'minecraft:piston', 'I': I, 'B': B})),
    ('finisher', 'Finisher', '精整機', '#5f7f6c', 'hone', (['GSG', 'IBI', 'I I'], {'G': 'minecraft:grindstone', 'S': 'minecraft:smooth_stone', 'I': I, 'B': B})),
    ('gear_cutter', 'Gear Cutter', '齒輪加工機', '#5f7f6c', 'gear', (['CGP', 'IBI', 'I I'], {'C': 'hdfv:chromium_ingot', 'G': 'minecraft:grindstone', 'P': 'minecraft:piston', 'I': I, 'B': B})),
    ('ram_machine', 'Ram Machine', '往復機', '#5f7f6c', 'ram', (['PPP', 'IBI', 'I I'], {'P': 'minecraft:piston', 'I': I, 'B': B})),
    ('sawing_machine', 'Sawing Machine', '鋸床', '#5f7f6c', 'saw', (['SPS', 'IBI', 'I I'], {'S': 'minecraft:stonecutter', 'P': 'minecraft:piston', 'I': I, 'B': B})),
    ('electro_machine', 'Electrical Machining Centre', '電加工機', '#4f5f72', 'spark',
     (['RCR', 'IBI', 'I I'], {'R': 'minecraft:redstone_block', 'C': 'minecraft:copper_block', 'I': I, 'B': B})),
    ('cutting_table', 'Cutting Table', '切割台', '#4f5f72', 'beam', (['RDR', 'IBI', 'I I'], {'R': 'minecraft:redstone_block', 'D': 'minecraft:diamond', 'I': I, 'B': B})),
    ('threader', 'Threading Machine', '螺紋機', '#5f7f6c', 'thread', (['CPC', 'IBI', 'I I'], {'C': 'minecraft:chain', 'P': 'minecraft:piston', 'I': I, 'B': B})),
    ('riveter', 'Riveter', '鉚接機', '#5f7f6c', 'rivet', (['APA', 'IBI', 'I I'], {'A': 'minecraft:anvil', 'P': 'minecraft:piston', 'I': I, 'B': B})),
]

# members: id, English, Chinese, colour, icon, the recipe's middle item (["IXI", "III"])
MEMBERS = [
    ('grinder_bed', 'Grinder Bed', '磨床床身', '#5f7f6c', 'ways', 'minecraft:smooth_stone'),
    ('finisher_table', 'Finisher Table', '精整機工作台', '#5f7f6c', 'table', 'minecraft:polished_andesite'),
    ('finisher_column', 'Finisher Column', '精整機立柱', '#5f7f6c', 'column', 'minecraft:iron_bars'),
    ('finisher_top', 'Finisher Top', '精整機頂座', '#5f7f6c', 'motor', 'minecraft:observer'),
    ('gear_table', 'Gear Cutter Work Table', '齒輪機工作台', '#5f7f6c', 'table', 'minecraft:polished_diorite'),
    ('gear_column', 'Gear Cutter Column', '齒輪機立柱', '#5f7f6c', 'column', 'minecraft:chain'),
    ('gear_top', 'Gear Cutter Top', '齒輪機頂座', '#5f7f6c', 'motor', 'minecraft:comparator'),
    ('ram_bed', 'Ram Machine Bed', '往復機床身', '#5f7f6c', 'ways', 'minecraft:rail'),
    ('saw_bed', 'Saw Bed', '鋸床床台', '#5f7f6c', 'rollers', 'minecraft:smooth_stone_slab'),
    ('edm_tank', 'Work Tank', '工作槽', '#4f5f72', 'tank', 'minecraft:glass'),
    ('cutting_slats', 'Cutting Slats', '切割台格柵', '#4f5f72', 'slats', 'minecraft:iron_trapdoor'),
    ('threader_base', 'Threader Base', '螺紋機底座', '#5f7f6c', 'table', 'minecraft:polished_granite'),
    ('riveter_frame', 'Riveter Frame', '鉚接機機架', '#5f7f6c', 'frame', 'minecraft:heavy_weighted_pressure_plate'),
]

# heads: kind, English, Chinese, icon, family's ingredient A and the head's X (["AXA", "IRI"])
G_A, F_A, C_A, R_A, S_A, E_A, T_A, H_A, V_A = ('hdfv:bauxite', 'minecraft:smooth_stone', 'hdfv:chromium_ingot', 'minecraft:piston', 'minecraft:stonecutter',
                                              'minecraft:copper_ingot', 'minecraft:quartz', 'hdfv:nickel_ingot', 'minecraft:iron_block')
HEADS = [
    ('cylindrical_grinding_head', 'Cylindrical Grinding Head', '外圓磨頭', 'wheel', G_A, 'minecraft:grindstone'),
    ('internal_grinding_head', 'Internal Grinding Head', '內圓磨頭', 'quill', G_A, 'minecraft:lightning_rod'),
    ('surface_grinding_head', 'Surface Grinding Head', '平面磨頭', 'wheel_flat', G_A, 'minecraft:smooth_stone_slab'),
    ('centreless_grinding_head', 'Centreless Grinding Head', '無心磨頭', 'two_wheels', G_A, 'hdfv:synthetic_rubber'),
    ('tool_grinding_head', 'Tool and Cutter Grinding Head', '工具磨頭', 'cup', G_A, 'minecraft:diamond'),
    ('honing_head', 'Honing Head', '珩磨頭', 'hone', F_A, 'hdfv:bauxite'),
    ('lapping_head', 'Lapping Head', '精研頭', 'lap', F_A, 'minecraft:clay_ball'),
    ('polishing_head', 'Polishing Head', '拋光頭', 'buff', F_A, 'minecraft:white_wool'),
    ('deburring_head', 'Deburring Head', '去毛邊頭', 'bowl', F_A, 'minecraft:gravel'),
    ('hobbing_head', 'Hobbing Head', '滾齒頭', 'hob', C_A, 'minecraft:iron_block'),
    ('gear_shaping_head', 'Gear Shaping Head', '插齒頭', 'gear', C_A, 'minecraft:piston'),
    ('shaving_head', 'Gear Shaving Head', '刮齒頭', 'gear_lines', C_A, 'minecraft:shears'),
    ('gear_grinding_head', 'Gear Grinding Head', '磨齒頭', 'gear_wheel', C_A, 'minecraft:grindstone'),
    ('chamfering_head', 'Gear Chamfering Head', '齒輪倒角頭', 'chamfer', C_A, 'minecraft:flint'),
    ('shaper_head', 'Shaper Ram', '牛頭鉋頭', 'ram', R_A, 'minecraft:flint'),
    ('slotter_head', 'Slotter Ram', '插床頭', 'ram_down', R_A, 'minecraft:iron_bars'),
    ('broaching_head', 'Broaching Ram', '拉床頭', 'broach', R_A, 'minecraft:chain'),
    ('band_saw_head', 'Band Saw', '帶鋸頭', 'band', S_A, 'minecraft:chain'),
    ('cold_saw_head', 'Cold Saw', '圓鋸頭', 'saw', S_A, 'minecraft:shears'),
    ('hacksaw_head', 'Power Hacksaw', '弓鋸頭', 'hacksaw', S_A, 'minecraft:stick'),
    ('sinker_edm_head', 'Sinker EDM Head', '成形放電頭', 'spark', E_A, 'hdfv:coke'),
    ('wire_edm_head', 'Wire EDM Head', '線切割頭', 'wire', E_A, 'minecraft:string'),
    ('hole_edm_head', 'EDM Hole Drilling Head', '放電鑽孔頭', 'tube', E_A, 'minecraft:lightning_rod'),
    ('ecm_head', 'Electrochemical Machining Head', '電化學加工頭', 'drop', E_A, 'minecraft:water_bucket'),
    ('laser_head', 'Laser Cutting Head', '雷射切割頭', 'beam', T_A, 'minecraft:diamond'),
    ('plasma_head', 'Plasma Torch', '電漿割炬', 'arc', T_A, 'minecraft:blaze_rod'),
    ('waterjet_head', 'Waterjet Cutting Head', '水刀頭', 'jet', T_A, 'minecraft:water_bucket'),
    ('thread_rolling_head', 'Thread Rolling Head', '滾牙頭', 'rolls', H_A, 'minecraft:iron_block'),
    ('flat_die_head', 'Flat Die Thread Roller', '搓絲頭', 'dies', H_A, 'minecraft:heavy_weighted_pressure_plate'),
    ('tapping_head', 'Tapping Head', '攻牙頭', 'tap', H_A, 'minecraft:lever'),
    ('thread_milling_head', 'Thread Milling Head', '銑牙頭', 'thread', H_A, 'minecraft:diamond'),
    ('squeeze_riveter_head', 'Squeeze Riveter', '壓鉚頭', 'squeeze', V_A, 'minecraft:piston'),
    ('impact_riveter_head', 'Impact Riveter', '衝擊鉚頭', 'hammer', V_A, 'minecraft:sticky_piston'),
    ('drill_riveter_head', 'Automatic Drilling and Riveting Head', '自動鑽鉚頭', 'drill_rivet', V_A, 'minecraft:observer'),
]

# new accessories: kind, English, Chinese, icon, recipe (pattern, key), JEI text (en, zh)
ACCESSORIES = [
    ('diamond_dresser', 'Diamond Dresser', '鑽石修整器', 'diamond', (['D', 'I'], {'D': 'minecraft:diamond', 'I': I}),
     'Diamond dresser (a grinder\'s accessory cell): dresses the wheel as it works: 10 % less grinding time, half the wheel lost to dressing.',
     '鑽石修整器（磨床的附件格）：邊磨邊修整砂輪：磨削時間少 10 %，修整用掉的砂輪減半。'),
    ('magnetic_chuck', 'Magnetic Chuck', '磁性夾頭', 'chuck', (['IRI', 'III'], {'I': I, 'R': 'minecraft:redstone'}),
     'Magnetic chuck (a grinder\'s accessory cell): holds iron and steel for the surface grinding head; without it they cannot be surface-ground.',
     '磁性夾頭（磨床的附件格）：平面磨頭磨鐵與鋼要用它吸住工件；沒有它就不能平面磨。'),
    ('auto_loader', 'Automatic Loader', '自動上下料', 'loader', (['H', 'P'], {'H': 'minecraft:hopper', 'P': 'minecraft:piston'}),
     'Automatic loader (behind a gear cutter\'s column): halves the clamping of each piece.',
     '自動上下料（齒輪機立柱後方）：每件的裝夾時間減半。'),
    ('bar_feeder', 'Bar Feeder', '送料機', 'feeder', (['R', 'P'], {'R': 'minecraft:rail', 'P': 'minecraft:piston'}),
     'Bar feeder (a saw\'s or threader\'s accessory cell): feeds the stock: the clamping cut to 0.3 and 30 % less sawing or threading.',
     '送料機（鋸床或螺紋機的附件格）：自動送料：裝夾時間剩 0.3、鋸切或螺紋時間少 30 %。'),
    ('mitre_vise', 'Mitre Vise', '角度虎鉗', 'vise', (['ILI', 'III'], {'I': I, 'L': 'minecraft:lever'}),
     'Mitre vise (a saw\'s accessory cell): holds tube at an angle; tube is cut only with it (a space frame\'s mitres).',
     '角度虎鉗（鋸床的附件格）：斜角夾住管料；管料（管架車架的斜切）要有它才能鋸。'),
    ('electrode_changer', 'Electrode Changer', '自動換電極', 'changer', (['C', 'H'], {'C': 'minecraft:chest', 'H': 'minecraft:hopper'}),
     'Electrode changer (an electrical machine\'s accessory cell): every face in one setup.',
     '自動換電極（電加工機的附件格）：所有的面一次裝夾做完。'),
    ('fume_extractor', 'Fume Extractor', '排煙機', 'fan', (['B', 'H'], {'B': 'minecraft:iron_bars', 'H': 'minecraft:hopper'}),
     'Fume extractor (behind a cutting table\'s drive): the laser and the plasma torch cut only under extraction.',
     '排煙機（切割台驅動格後方）：雷射與電漿要排煙才能切割。'),
    ('bevel_head', 'Bevel Head', '斜角頭', 'bevel', (['L', 'D'], {'L': 'minecraft:lightning_rod', 'D': 'minecraft:diamond'}),
     'Bevel head (behind a cutting table\'s drive): tilts the head: angled edges and holes (five axes).',
     '斜角頭（切割台驅動格後方）：切割頭可以傾斜：斜邊與斜孔（五軸）。'),
]

# the families' JEI pages: (en, zh)
FAMILY_PAGES = {
    'grinder': ('Grinder (0.17 multiblock): the control column (this block) over its drive, the bed a metre a segment (1 to 4) to the right with an accessory cell over each, the head\'s cell over the bed\'s end: cylindrical, internal, surface, centreless or tool and cutter grinding. /hdfv dev place grinder cylindrical places one.',
                '磨床（0.17 多方塊）：控制柱（這個方塊）在驅動上方，床身往右每段 1 公尺（1～4 段），每段上方一格附件，床身末端上方是頭格：外圓、內圓、平面、無心或工具磨。/hdfv dev place grinder cylindrical 放出範例。'),
    'finisher': ('Finisher (0.17 multiblock): the base (this block) with its drive behind, the table and the work\'s room over it, the column a course at a time (1 to 3), the top with the head\'s cell over the table: honing, lapping, polishing or deburring. /hdfv dev place finisher honing.',
                 '精整機（0.17 多方塊）：底座（這個方塊）後方是驅動，旁邊是工作台與加工空間，立柱每段 1 格（1～3 段），頂座旁的頭格在工作台上方：珩磨、精研、拋光或去毛邊。/hdfv dev place finisher honing。'),
    'gear_cutter': ('Gear cutter (0.17 multiblock): the base, table and column as the finisher\'s (1 to 3 courses: gears 0.3 m across a course more), the head\'s cell over the table: hobbing, shaping, shaving, grinding or chamfering. /hdfv dev place gear_cutter hobbing.',
                    '齒輪加工機（0.17 多方塊）：底座、工作台與立柱同精整機（1～3 段，每段多 0.3 公尺的齒輪直徑），頭格在工作台上方：滾齒、插齒、刮齒、磨齒或倒角。/hdfv dev place gear_cutter hobbing。'),
    'ram_machine': ('Ram machine (0.17 multiblock): the column (this block) over its drive, the bed a segment of stroke at a time (1 to 3) with a dividing head\'s cell over each, the head\'s cell at its end: shaper, slotter or broach. /hdfv dev place ram_machine broaching.',
                    '往復機（0.17 多方塊）：立柱（這個方塊）在驅動上方，床身每段一段行程（1～3 段），每段上方可放分度頭，末端是頭格：牛頭鉋、插床或拉床。/hdfv dev place ram_machine broaching。'),
    'sawing_machine': ('Sawing machine (0.17 multiblock): the control box (this block) over its drive, the stock bed a metre a segment (1 to 3) with a bar feeder\'s or mitre vise\'s cell over each, the head\'s cell at its end: band, cold or hack saw. /hdfv dev place sawing_machine band_saw.',
                       '鋸床（0.17 多方塊）：控制箱（這個方塊）在驅動上方，料台每段 1 公尺（1～3 段），每段上方可放送料機或角度虎鉗，末端是頭格：帶鋸、圓鋸或弓鋸。/hdfv dev place sawing_machine band_saw。'),
    'electro_machine': ('Electrical machining centre (0.17 multiblock, numerically controlled, electricity only): the generator (this block) over its motor seat or NC drive, the work tank a segment at a time (1 to 3), the head\'s cell over its end: sinker EDM, wire EDM, EDM hole drilling or ECM. /hdfv dev place electro_machine wire_edm.',
                        '電加工機（0.17 多方塊，數控、只吃電）：電源（這個方塊）在電動機座或數控驅動上方，工作槽每段 1 格（1～3 段），末端上方是頭格：成形放電、線切割、放電鑽孔或電化學。/hdfv dev place electro_machine wire_edm。'),
    'cutting_table': ('Cutting table (0.17 multiblock, numerically controlled): two rows of slats a metre a segment (1 to 6; plate 2 m wide), the drive in front with a fume extractor\'s or bevel head\'s cell behind it, the console (this block) over the drive and the head\'s cell over the table\'s end: laser (and its electricity), plasma (electricity) or waterjet. /hdfv dev place cutting_table laser.',
                      '切割台（0.17 多方塊，數控）：兩排格柵每段 1 公尺（1～6 段，板寬 2 公尺），驅動在前、後方可放排煙機或斜角頭，操作台（這個方塊）在驅動上方，末端上方是頭格：雷射（只吃電）、電漿（只吃電）或水刀。/hdfv dev place cutting_table laser。'),
    'threader': ('Threading machine (0.17 multiblock): the controller (this block) over its drive, the base beside it with the head\'s cell on it and a bar feeder\'s cell at its end: thread rolling, flat dies, tapping or thread milling. /hdfv dev place threader tapping.',
                 '螺紋機（0.17 多方塊）：控制方塊（這個方塊）在驅動上方，旁邊是底座，底座上方是頭格，末端可放送料機：滾牙、搓絲、攻牙或銑牙。/hdfv dev place threader tapping。'),
    'riveter': ('Riveter (0.17 multiblock): the controller (this block) over its drive, the frame a segment of throat at a time (1 to 3), the head\'s cell at its end: squeeze, impact or automatic drilling and riveting. /hdfv dev place riveter squeeze_riveter.',
                '鉚接機（0.17 多方塊）：控制方塊（這個方塊）在驅動上方，機架每段一段喉深（1～3 段），末端是頭格：壓鉚、衝擊鉚或自動鑽鉚。/hdfv dev place riveter squeeze_riveter。'),
}

# supplies: id, English, Chinese, icon, recipe (pattern, key, count) or None (filled at the air separation unit)
SUPPLIES = [
    ('hss_tool_set', 'High-Speed Steel Tool Set', '高速鋼刀具組', 'tools_hss', (['CI', 'IC'], {'C': 'hdfv:chromium_ingot', 'I': I}, 1)),
    ('cermet_tool_set', 'Cermet Tool Set', '金屬陶瓷刀具組', 'tools_cermet', (['TK', 'NT'], {'T': 'hdfv:titanium_ingot', 'K': 'hdfv:coke', 'N': 'hdfv:nickel_ingot'}, 1)),
    ('grinding_wheel', 'Grinding Wheel', '砂輪', 'wheel_item', (['BCB', 'CIC', 'BCB'], {'B': 'hdfv:bauxite', 'C': 'minecraft:clay_ball', 'I': I}, 1)),
    ('honing_stones', 'Honing Stones', '油石組', 'stones', (['BC', 'CB'], {'B': 'hdfv:bauxite', 'C': 'minecraft:clay_ball'}, 1)),
    ('lapping_compound', 'Lapping Compound', '研磨膏', 'tin', (['BS'], {'B': 'hdfv:bauxite', 'S': 'minecraft:slime_ball'}, 2)),
    ('saw_blade', 'Saw Blade', '鋸條', 'blade', (['CCC', 'III'], {'C': 'hdfv:chromium_ingot', 'I': I}, 1)),
    ('electrode', 'Electrode', '電極', 'electrode', (['KK', 'KK'], {'K': 'hdfv:coke'}, 1)),
    ('brass_wire_spool', 'Brass Wire Spool', '黃銅線捲', 'spool', (['CZC', ' S '], {'C': 'minecraft:copper_ingot', 'Z': 'hdfv:zinc_ingot', 'S': 'minecraft:stick'}, 1)),
    ('electrolyte', 'Electrolyte', '電解液', 'can', (['G', 'W'], {'G': 'minecraft:gunpowder', 'W': 'minecraft:water_bucket'}, 4)),
    ('garnet_abrasive', 'Garnet Abrasive', '石榴石磨料', 'bag', (['R'], {'R': 'hdfv:raw_garnet'}, 4)),
    ('oxygen_cylinder', 'Oxygen Cylinder', '氧氣瓶', 'cyl_oxygen', None),
    ('nitrogen_cylinder', 'Nitrogen Cylinder', '氮氣瓶', 'cyl_nitrogen', None),
    ('argon_cylinder', 'Argon Cylinder', '氬氣瓶', 'cyl_argon', None),
    ('steel_rivets', 'Steel Rivets', '鋼鉚釘', 'rivets_steel', (['II'], {'I': I}, 1)),
    ('aluminium_rivets', 'Aluminium Rivets', '鋁鉚釘', 'rivets_al', (['AA'], {'A': 'hdfv:aluminium_ingot'}, 1)),
    # 0.23: plain carbon steel, the first lathes' tools (its recipe is tools/recipes.py's, as all now)
    ('carbon_tool_set', 'Carbon Steel Tool Set', '碳鋼刀具組', 'tools_carbon', None),
    ('empty_gas_cylinder', 'Empty Gas Cylinder', '空鋼瓶', 'cyl_empty', (['ICI', 'I I', 'III'], {'I': I, 'C': 'minecraft:copper_ingot'}, 1)),
]

NC_DRIVE = ('nc_drive', 'NC Drive', '數控驅動座', (['ICI', 'CKC', 'ICI'], {'I': I, 'C': 'minecraft:copper_ingot', 'K': 'minecraft:comparator'}))
ASU = ('air_separation_unit', 'Air Separation Unit', '空氣分離機',
       (['IGI', 'CBC', 'IRI'], {'I': I, 'G': 'minecraft:glass', 'C': 'minecraft:copper_ingot', 'B': B, 'R': 'minecraft:redstone_block'}))
GARNET = [('garnet_ore', 'Garnet Ore', '石榴石礦石'), ('deepslate_garnet_ore', 'Deepslate Garnet Ore', '深板岩石榴石礦石')]
RAW_GARNET = ('raw_garnet', 'Raw Garnet', '石榴石原礦')

PROCESSES = [('saw', 'Sawing', '鋸切'), ('lap', 'Lapping', '精研'), ('polish', 'Polishing', '拋光'), ('deburr', 'Deburring', '去毛邊'),
             ('tap', 'Tapping', '攻牙'), ('thread', 'Threading', '螺紋'), ('profile', 'Profile cutting', '外形切割'), ('rivet', 'Riveting', '鉚接')]
FEATURE_PAIRS = [('grind.bore', 'Honing', '珩磨'), ('mill.gear_teeth', 'Gear cutting', '切齒'), ('grind.gear_teeth', 'Gear finishing', '齒面精加工'),
                 ('deburr.gear_teeth', 'Gear chamfering', '齒輪倒角'), ('mill.spline', 'Splines and keyways', '花鍵與鍵槽'), ('bore.spline', 'Broached bore', '拉孔'),
                 ('mill.fir_tree', 'Blade slots', '樅樹槽'), ('drill.orifice', 'Orifices', '噴孔'), ('drill.cooling_hole', 'Cooling holes', '冷卻孔'),
                 ('mill.aerofoil', 'Aerofoil machining', '葉型加工'), ('grind.aerofoil', 'Aerofoil grinding', '葉型研磨'), ('lap.gear_teeth', 'Gear lapping', '齒輪精研'),
                 ('polish.aerofoil', 'Aerofoil polishing', '葉型拋光')]
CONSUMABLES = [('cutting_tools', 'cutting tools', '刀具'), ('grinding_wheel', 'a grinding wheel', '砂輪'), ('honing_stones', 'honing stones', '油石'),
               ('lapping_compound', 'lapping compound', '研磨膏'), ('saw_blade', 'a saw blade', '鋸條'), ('electrodes', 'electrodes', '電極'),
               ('brass_wire', 'EDM wire', '放電銅線'), ('electrolyte', 'electrolyte', '電解液'), ('garnet', 'garnet abrasive', '石榴石磨料'),
               ('assist_gas', 'assist gas', '輔助氣體'), ('rivets', 'rivets', '鉚釘')]


def lang_groups():
    """(anchor key, [(key, en, zh)]) in the order they go in."""
    g = []
    g.append(('block.hdfv.deepslate_zinc_ore', [('block.hdfv.' + i, e, z) for i, e, z in GARNET]))
    g.append(('item.hdfv.raw_zinc', [('item.hdfv.' + RAW_GARNET[0], RAW_GARNET[1], RAW_GARNET[2])]))
    g.append(('block.hdfv.cnc_machining_centre', [('block.hdfv.' + f[0], f[1], f[2]) for f in FAMILIES]))
    g.append(('block.hdfv.digital_readout', [('block.hdfv.' + m[0], m[1], m[2]) for m in MEMBERS] + [('block.hdfv.' + h[0], h[1], h[2]) for h in HEADS]
              + [('block.hdfv.' + a[0], a[1], a[2]) for a in ACCESSORIES]))
    g.append(('block.hdfv.pulley_drive', [('block.hdfv.' + NC_DRIVE[0], NC_DRIVE[1], NC_DRIVE[2])]))
    g.append(('hdfv.machine.no_accessories', [
        ('hdfv.machine.family', '%s, %s m, %s; %s', '%s，%s 公尺，%s；%s'),
        ('hdfv.machine.family_high', '%s, %s courses high, %s; %s', '%s，高 %s 段，%s；%s'),
        ('hdfv.machine.no_head', 'Fit one head (one kind only)', '要裝一個頭（只能一種）'),
        ('hdfv.machine.consumables', 'Consumables', '耗材'),
        ('hdfv.machine.needs_consumable', 'Needs %s', '缺：%s'),
        ('hdfv.machine.state.no_tool', 'Out of a consumable', '缺耗材'),
        ('hdfv.machine.state.needs_electric', 'This head runs on electricity only: a motor seat or NC drive', '這個頭只能用電：換電動機座或數控驅動座'),
    ]))
    g.append(('hdfv.drive.shaft', [('hdfv.drive.numerical', 'NC drive', '數控驅動')]))
    g.append(('hdfv.process.assemble', [('hdfv.process.' + p, e, z) for p, e, z in PROCESSES] + [('hdfv.process.' + p, e, z) for p, e, z in FEATURE_PAIRS]))
    g.append(('hdfv.workpiece.step', [('hdfv.workpiece.older', 'Made on an older route: a machine moves it on to the new one',
                                       '舊路線的工件：放進有藍圖的機器就改到新路線')]))
    jei = [('hdfv.jei.tool.' + k, v[0], v[1]) for k, v in FAMILY_PAGES.items()] + [('hdfv.jei.accessory.' + a[0], a[5], a[6]) for a in ACCESSORIES]
    jei += [('hdfv.jei.machining', 'Machining', '機加工製程'), ('hdfv.jei.machining.grade', '%s to IT%s', '%s：IT%s'),
            ('hdfv.jei.machining.nc', 'Numerically controlled', '數控'), ('hdfv.jei.machining.manual', 'Needs an operator (an NC drive runs it alone)', '要人操作（裝數控驅動座就不用）'),
            ('hdfv.jei.machining.uses', 'Uses: %s', '耗材：%s'), ('hdfv.jei.machining.electric', 'Electricity only', '只吃電')]
    g.append(('hdfv.jei.accessory.digital_readout', jei))
    items = [('item.hdfv.' + s[0], s[1], s[2]) for s in SUPPLIES] + [('block.hdfv.' + ASU[0], ASU[1], ASU[2])]
    items += [('hdfv.consumable.' + c, e, z) for c, e, z in CONSUMABLES]
    items += [('hdfv.consumable.for', 'For %s', '用途：%s'), ('hdfv.consumable.regrinds', 'Reground %s of %s times', '已重磨 %s／%s 次'),
              ('hdfv.air_separator.status', '%s: %s, %s/%s kg in the cylinder, %s empty cylinders, %s FE', '%s：%s，鋼瓶 %s／%s 公斤，空鋼瓶 %s 支，%s FE'),
              ('hdfv.air_separator.state.idle', 'Idle', '待機'), ('hdfv.air_separator.state.no_power', 'No power', '沒電'),
              ('hdfv.air_separator.state.no_cylinder', 'No empty cylinder', '沒有空鋼瓶'), ('hdfv.air_separator.state.full', 'Take the full cylinder', '取出滿的鋼瓶'),
              ('hdfv.air_separator.state.running', 'Filling', '灌裝中'), ('hdfv.gas.oxygen', 'oxygen', '氧氣'), ('hdfv.gas.nitrogen', 'nitrogen', '氮氣'),
              ('hdfv.gas.argon', 'argon', '氬氣')]
    g.append(('hdfv.jei.machining.electric', items))
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
    # the last entry before the closing brace has no comma
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


def ore_drop(id_, raw):
    return {'type': 'minecraft:block', 'pools': [{'bonus_rolls': 0.0, 'rolls': 1.0, 'entries': [{'type': 'minecraft:alternatives', 'children': [
        {'type': 'minecraft:item', 'name': 'hdfv:' + id_, 'conditions': [{'condition': 'minecraft:match_tool', 'predicate': {'predicates': {
            'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}]},
        {'type': 'minecraft:item', 'name': 'hdfv:' + raw, 'functions': [
            {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
            {'function': 'minecraft:explosion_decay'}]}]}]}], 'random_sequence': 'hdfv:blocks/' + id_}


def recipe(id_, pattern, key, count=1):
    return {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
            'key': {k: {'item': v} for k, v in key.items()}, 'result': {'id': 'hdfv:' + id_, 'count': count}}


def recipe_signature(r):
    """A shaped recipe's pattern with its items in place of its symbols (two recipes of one signature clash)."""
    if r.get('type') != 'minecraft:crafting_shaped':
        return None
    key = {k: (v.get('item') or v.get('tag')) for k, v in r.get('key', {}).items()}
    return tuple(''.join('|' + key.get(ch, ' ') for ch in row) for row in r['pattern'])


def member_blockstate(kind):
    return {'variants': {'formed=false': {'model': 'hdfv:block/' + kind}, 'formed=true': {'model': 'hdfv:block/' + kind + '_formed'}}}


def controller_blockstate(id_):
    v = {}
    for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        v['facing=%s,formed=false' % facing] = {'model': 'hdfv:block/' + id_} if y == 0 else {'model': 'hdfv:block/' + id_, 'y': y}
        v['facing=%s,formed=true' % facing] = {'model': 'hdfv:block/' + id_ + '_formed'}
    return {'variants': v}


def cube_bottom_top(kind):
    return {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'side': 'hdfv:block/' + kind + '_side', 'top': 'hdfv:block/' + kind + '_top',
                                                                     'bottom': 'hdfv:block/' + kind + '_top'}}


def formed(kind, texture):
    return {'textures': {'particle': 'hdfv:block/' + texture}, 'elements': []}


def add_tag(path, values):
    """Adds values to a tag file's list (kept in its order; the file made when missing)."""
    obj = json.load(open(path, encoding='utf-8')) if os.path.exists(path) else {'values': []}
    for v in values:
        if v not in obj['values']:
            obj['values'].append(v)
    write_json(path, obj)


def data():
    recipes = {}
    blockstates, block_models, item_models = os.path.join(ASSETS, 'blockstates'), os.path.join(ASSETS, 'models', 'block'), os.path.join(ASSETS, 'models', 'item')
    loot = os.path.join(DATA, 'hdfv', 'loot_table', 'blocks')
    blocks_all = []
    for id_, en, zh, colour, icon, (pattern, key) in FAMILIES:
        write_json(os.path.join(blockstates, id_ + '.json'), controller_blockstate(id_))
        write_json(os.path.join(block_models, id_ + '.json'), {'parent': 'minecraft:block/orientable', 'textures': {
            'top': 'hdfv:block/' + id_ + '_top', 'front': 'hdfv:block/' + id_ + '_front', 'side': 'hdfv:block/' + id_ + '_side'}})
        write_json(os.path.join(block_models, id_ + '_formed.json'), formed(id_, id_ + '_side'))
        recipes[id_] = recipe(id_, pattern, key)
        blocks_all.append(id_)
    members = [(m[0], m[5]) for m in MEMBERS]
    for kind, x in members:
        recipes[kind] = recipe(kind, ['IXI', 'III'], {'I': I, 'X': x})
    for kind, en, zh, icon, a, x in HEADS:
        recipes[kind] = recipe(kind, ['AXA', 'IRI'], {'A': a, 'X': x, 'I': I, 'R': 'minecraft:redstone'})
    for kind, en, zh, icon, (pattern, key), *_ in ACCESSORIES:
        recipes[kind] = recipe(kind, pattern, key)
    for kind in [m[0] for m in MEMBERS] + [h[0] for h in HEADS] + [a[0] for a in ACCESSORIES]:
        write_json(os.path.join(blockstates, kind + '.json'), member_blockstate(kind))
        write_json(os.path.join(block_models, kind + '.json'), cube_bottom_top(kind))
        write_json(os.path.join(block_models, kind + '_formed.json'), formed(kind, kind + '_side'))
        blocks_all.append(kind)
    nc, nc_en, nc_zh, (nc_pattern, nc_key) = NC_DRIVE
    write_json(os.path.join(blockstates, nc + '.json'), {'variants': {'': {'model': 'hdfv:block/' + nc}}})
    write_json(os.path.join(block_models, nc + '.json'), cube_bottom_top(nc))
    recipes[nc] = recipe(nc, nc_pattern, nc_key)
    blocks_all.append(nc)
    asu, asu_en, asu_zh, (asu_pattern, asu_key) = ASU
    write_json(os.path.join(blockstates, asu + '.json'), {'variants': {'': {'model': 'hdfv:block/' + asu}}})
    recipes[asu] = recipe(asu, asu_pattern, asu_key)
    blocks_all.append(asu)
    for id_ in blocks_all:
        write_json(os.path.join(item_models, id_ + '.json'), {'parent': 'hdfv:block/' + id_})
        write_json(os.path.join(loot, id_ + '.json'), block_drop(id_))
    for id_, en, zh, icon, r in SUPPLIES:
        write_json(os.path.join(item_models, id_ + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'hdfv:item/' + id_}})
        if r:
            recipes[id_] = recipe(id_, r[0], r[1], r[2])
    # the ore
    for id_, en, zh in GARNET:
        write_json(os.path.join(blockstates, id_ + '.json'), {'variants': {'': {'model': 'hdfv:block/' + id_}}})
        write_json(os.path.join(block_models, id_ + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'hdfv:block/' + id_}})
        write_json(os.path.join(item_models, id_ + '.json'), {'parent': 'hdfv:block/' + id_})
        write_json(os.path.join(loot, id_ + '.json'), ore_drop(id_, RAW_GARNET[0]))
    write_json(os.path.join(item_models, RAW_GARNET[0] + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'hdfv:item/' + RAW_GARNET[0]}})
    wg = os.path.join(DATA, 'hdfv', 'worldgen')
    write_json(os.path.join(wg, 'configured_feature', 'ore_garnet.json'), {'type': 'minecraft:ore', 'config': {'discard_chance_on_air_exposure': 0.0, 'size': 7, 'targets': [
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'}, 'state': {'Name': 'hdfv:garnet_ore'}},
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'}, 'state': {'Name': 'hdfv:deepslate_garnet_ore'}}]}})
    write_json(os.path.join(wg, 'placed_feature', 'ore_garnet.json'), {'feature': 'hdfv:ore_garnet', 'placement': [
        {'type': 'minecraft:count', 'count': 4}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': -48}, 'max_inclusive': {'absolute': 48}}},
        {'type': 'minecraft:biome'}]})
    mod = os.path.join(DATA, 'hdfv', 'neoforge', 'biome_modifier', 'ores.json')
    obj = json.load(open(mod, encoding='utf-8'))
    if 'hdfv:ore_garnet' not in obj['features']:
        obj['features'].append('hdfv:ore_garnet')
    write_json(mod, obj)
    ores = ['hdfv:garnet_ore', 'hdfv:deepslate_garnet_ore']
    add_tag(os.path.join(DATA, 'c', 'tags', 'block', 'ores', 'garnet.json'), ores)
    add_tag(os.path.join(DATA, 'c', 'tags', 'item', 'ores', 'garnet.json'), ores)
    add_tag(os.path.join(DATA, 'c', 'tags', 'block', 'ores.json'), ['#c:ores/garnet'])
    add_tag(os.path.join(DATA, 'c', 'tags', 'item', 'ores.json'), ['#c:ores/garnet'])
    add_tag(os.path.join(DATA, 'c', 'tags', 'block', 'ores_in_ground', 'stone.json'), ['hdfv:garnet_ore'])
    add_tag(os.path.join(DATA, 'c', 'tags', 'block', 'ores_in_ground', 'deepslate.json'), ['hdfv:deepslate_garnet_ore'])
    pick = ['hdfv:' + b for b in blocks_all] + ores
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), pick)
    add_tag(os.path.join(DATA, 'minecraft', 'tags', 'block', 'needs_stone_tool.json'),
            ores + ['hdfv:' + k for k in [m[0] for m in MEMBERS] + [h[0] for h in HEADS] + [a[0] for a in ACCESSORIES] + [nc]])
    # 0.23: the recipes are tools/recipes.py's (the spec's recipes above are the 0.17 ones, no longer written)
    return blocks_all


# ------------------------------------------------------------------------------------------------ textures

def rgb(h, a=255):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (c[3],)


STEEL, DARK, BRIGHT, BRASS, RED, COPPER, BLUE, GLOW = (rgb('#9aa0a6'), rgb('#2e3135'), rgb('#c9ced3'), rgb('#b08d3c'), rgb('#b3261e'),
                                                       rgb('#b87333'), rgb('#3a6ea5'), rgb('#7fe0ff'))


class Canvas:
    def __init__(self, base):
        self.im = Image.new('RGBA', (16, 16), base)
        self.px = self.im.load()
        self.base = base

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            self.set(x, y0, c)
            self.set(x, y1, c)
        for y in range(y0, y1 + 1):
            self.set(x0, y, c)
            self.set(x1, y, c)

    def circle(self, cx, cy, r, c, fill=None):
        for y in range(16):
            for x in range(16):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if fill is not None and d < r - 0.5:
                    self.set(x, y, fill)
                elif abs(d - r) < 0.55:
                    self.set(x, y, c)

    def panel(self):
        """The machines' panel: a darker rim, rivets at the corners (as the 0.9 members)."""
        rim = shade(self.base, 0.72)
        self.frame(0, 0, 15, 15, rim)
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            self.set(x, y, shade(self.base, 0.6))
        return self


def icon(c, name):
    """A pictogram in the panel's middle."""
    b = c.base
    if name in ('wheel', 'wheel_item'):
        c.circle(8, 8, 5, DARK, fill=rgb('#d8d0c0'))
        c.rect(7, 7, 8, 8, STEEL)
    elif name == 'wheel_flat':
        c.rect(3, 4, 12, 7, rgb('#d8d0c0')); c.frame(3, 4, 12, 7, DARK); c.rect(2, 10, 13, 12, STEEL)
    elif name == 'two_wheels':
        c.circle(5, 8, 3, DARK, fill=rgb('#d8d0c0')); c.circle(11, 8, 3, DARK, fill=rgb('#3b3f45')); c.rect(7, 11, 9, 12, BRIGHT)
    elif name == 'quill':
        c.rect(2, 6, 8, 10, STEEL); c.rect(9, 7, 13, 9, BRIGHT); c.rect(13, 6, 14, 10, rgb('#d8d0c0'))
    elif name == 'cup':
        c.rect(5, 3, 10, 9, STEEL); c.rect(4, 10, 11, 12, rgb('#d8d0c0')); c.frame(4, 10, 11, 12, DARK)
    elif name in ('hone',):
        c.rect(7, 2, 8, 13, STEEL)
        for y in (5, 8, 11):
            c.rect(5, y, 6, y + 1, rgb('#d8d0c0')); c.rect(9, y, 10, y + 1, rgb('#d8d0c0'))
    elif name == 'lap':
        c.rect(2, 9, 13, 11, rgb('#3b3f45')); c.circle(8, 6, 2, DARK, fill=STEEL)
    elif name == 'buff':
        c.circle(8, 8, 5, rgb('#e8e2d0'), fill=rgb('#f4f0e6')); c.rect(7, 7, 8, 8, STEEL)
    elif name == 'bowl':
        for x in range(3, 13):
            c.set(x, 12, DARK)
        for y in range(7, 12):
            c.set(3, y, DARK); c.set(12, y, DARK)
        c.rect(4, 8, 11, 11, rgb('#d8d0c0'))
    elif name in ('gear', 'gear_lines', 'gear_wheel', 'chamfer', 'hob'):
        c.circle(8, 8, 4, DARK, fill=STEEL)
        for x, y in ((8, 2), (8, 13), (2, 8), (13, 8), (4, 4), (11, 4), (4, 11), (11, 11)):
            c.set(x, y, DARK)
        if name == 'gear_lines':
            c.rect(6, 6, 9, 9, BRIGHT)
        if name == 'gear_wheel':
            c.rect(10, 2, 14, 5, rgb('#d8d0c0'))
        if name == 'chamfer':
            c.set(7, 7, BRASS); c.set(8, 8, BRASS)
        if name == 'hob':
            c.rect(2, 1, 13, 2, BRASS)
    elif name in ('ram', 'ram_down', 'broach'):
        if name == 'ram_down':
            c.rect(6, 2, 9, 10, STEEL); c.rect(7, 11, 8, 13, DARK)
        else:
            c.rect(2, 6, 11, 9, STEEL); c.rect(12, 7, 13, 11, DARK)
        if name == 'broach':
            for x in range(3, 13, 2):
                c.set(x, 11, DARK); c.set(x, 4, DARK)
    elif name in ('saw', 'band', 'hacksaw'):
        if name == 'saw':
            c.circle(8, 8, 5, DARK, fill=BRIGHT)
            for x, y in ((8, 2), (14, 8), (8, 14), (2, 8)):
                c.set(x, y, DARK)
        else:
            c.rect(2, 9, 13, 9, BRIGHT)
            for x in range(2, 14, 2):
                c.set(x, 10, DARK)
            if name == 'band':
                c.circle(4, 5, 2, DARK); c.circle(11, 5, 2, DARK)
            else:
                c.rect(2, 4, 2, 9, DARK); c.rect(13, 4, 13, 9, DARK); c.rect(2, 4, 13, 4, DARK)
    elif name in ('spark', 'wire', 'tube'):
        if name == 'wire':
            c.rect(7, 1, 7, 14, COPPER)
        elif name == 'tube':
            c.rect(7, 1, 8, 10, COPPER)
        else:
            c.rect(5, 2, 10, 7, rgb('#3b3f45'))
        for x, y in ((8, 10), (7, 11), (9, 12), (8, 13), (6, 12), (10, 10)):
            c.set(x, y, GLOW)
    elif name == 'drop':
        c.circle(8, 9, 3, DARK, fill=BLUE); c.set(8, 4, BLUE); c.set(8, 5, BLUE)
    elif name in ('beam', 'arc', 'jet'):
        c.rect(6, 2, 9, 6, STEEL); c.rect(7, 7, 8, 7, COPPER)
        col = {'beam': rgb('#ff3b30'), 'arc': rgb('#c8e6ff'), 'jet': rgb('#9fd3ff')}[name]
        c.rect(7, 8, 8, 13, col)
        c.rect(3, 14, 12, 14, DARK)
    elif name in ('thread', 'rolls', 'dies', 'tap'):
        if name == 'rolls':
            c.circle(5, 8, 3, DARK, fill=STEEL); c.circle(11, 8, 3, DARK, fill=STEEL)
        elif name == 'dies':
            c.rect(2, 4, 13, 6, STEEL); c.rect(2, 9, 13, 11, STEEL)
        else:
            c.rect(6, 2, 9, 13, STEEL)
            for y in range(3, 13, 2):
                c.set(6, y, DARK); c.set(9, y + 1, DARK)
            if name == 'tap':
                c.rect(5, 1, 10, 2, DARK)
    elif name in ('rivet', 'squeeze', 'hammer', 'drill_rivet'):
        c.rect(4, 5, 11, 6, STEEL); c.rect(7, 7, 8, 11, BRIGHT); c.rect(5, 12, 10, 12, STEEL)
        if name == 'hammer':
            c.rect(6, 1, 9, 4, DARK)
        if name == 'drill_rivet':
            c.rect(12, 2, 13, 9, BRIGHT)
    elif name == 'ways':
        c.rect(1, 4, 14, 5, BRIGHT); c.rect(1, 10, 14, 11, BRIGHT)
    elif name == 'table':
        for x in range(2, 14, 3):
            c.rect(x, 2, x, 13, shade(b, 0.8))
    elif name == 'column':
        c.rect(5, 0, 10, 15, shade(b, 0.88)); c.rect(5, 0, 5, 15, shade(b, 0.7))
    elif name == 'motor':
        c.rect(3, 4, 12, 11, shade(b, 0.85))
        for x in range(4, 12, 2):
            c.rect(x, 4, x, 11, shade(b, 0.65))
    elif name == 'rollers':
        for y in (3, 7, 11):
            c.rect(1, y, 14, y + 1, BRIGHT)
    elif name == 'tank':
        c.rect(2, 4, 13, 12, rgb('#1c2a35')); c.rect(3, 5, 12, 7, rgb('#2f4f66'))
    elif name == 'slats':
        for x in range(2, 15, 3):
            c.rect(x, 1, x, 14, DARK)
    elif name == 'frame':
        c.rect(3, 2, 5, 13, shade(b, 0.8)); c.rect(5, 2, 12, 4, shade(b, 0.8)); c.rect(5, 11, 12, 13, shade(b, 0.8))
    elif name == 'diamond':
        c.rect(6, 3, 9, 8, STEEL); c.set(7, 9, rgb('#bff6ff')); c.set(8, 9, rgb('#bff6ff')); c.set(7, 10, rgb('#e6fdff'))
    elif name == 'chuck':
        c.rect(2, 5, 13, 11, DARK)
        for x in range(3, 13, 2):
            c.rect(x, 6, x, 10, BRASS)
    elif name in ('loader', 'feeder', 'changer'):
        c.rect(3, 3, 12, 6, STEEL); c.rect(7, 7, 8, 12, DARK); c.rect(5, 12, 10, 13, BRASS)
    elif name == 'vise':
        c.rect(2, 9, 13, 12, STEEL); c.rect(3, 4, 5, 9, DARK); c.rect(10, 4, 12, 9, DARK)
    elif name == 'fan':
        c.circle(8, 8, 5, DARK)
        for x, y in ((8, 5), (11, 8), (8, 11), (5, 8), (8, 8)):
            c.set(x, y, BRIGHT)
    elif name == 'bevel':
        for i in range(10):
            c.set(3 + i, 12 - i, BRIGHT)
        c.rect(3, 12, 12, 13, STEEL)
    return c


def block_textures():
    """The 16 px sources of every new block's faces (side and top; a controller's front, side and top)."""
    out = {}
    for id_, en, zh, colour, ic, r in FAMILIES:
        base = rgb(colour)
        f = Canvas(base).panel()
        f.rect(3, 2, 12, 6, rgb('#2b2f33')); f.rect(4, 3, 11, 5, rgb('#6fd16b') if colour == '#5f7f6c' else rgb('#7fc8ff'))
        f.rect(3, 9, 12, 13, shade(base, 0.85))
        f.rect(4, 10, 5, 11, RED); f.rect(10, 10, 11, 11, BRASS)
        out[id_ + '_front'] = f.im
        s = Canvas(base).panel()
        for y in range(4, 12, 2):
            s.rect(4, y, 11, y, shade(base, 0.7))
        out[id_ + '_side'] = s.im
        t = Canvas(shade(base, 1.06)).panel()
        icon(t, ic)
        out[id_ + '_top'] = t.im
    for kind, en, zh, colour, ic, x in MEMBERS:
        base = rgb(colour)
        out[kind + '_side'] = icon(Canvas(base).panel(), ic).im
        out[kind + '_top'] = icon(Canvas(shade(base, 1.06)).panel(), ic if ic in ('table', 'slats', 'tank', 'rollers', 'ways') else 'table').im
    head_colour = rgb('#8c9196')
    for kind, en, zh, ic, a, x in HEADS:
        out[kind + '_side'] = icon(Canvas(head_colour).panel(), ic).im
        out[kind + '_top'] = Canvas(shade(head_colour, 1.08)).panel().im
    for kind, en, zh, ic, *_ in ACCESSORIES:
        out[kind + '_side'] = icon(Canvas(rgb('#7d8389')).panel(), ic).im
        out[kind + '_top'] = Canvas(rgb('#868c92')).panel().im
    nc = Canvas(rgb('#2f3d52')).panel()
    nc.rect(3, 3, 12, 8, rgb('#1a1d21')); nc.rect(4, 4, 11, 7, rgb('#7fc8ff'))
    for x in (4, 7, 10):
        nc.rect(x, 10, x + 1, 11, BRIGHT)
    out['nc_drive_side'] = nc.im
    out['nc_drive_top'] = Canvas(rgb('#2f3d52')).panel().im
    return out


def ore_textures():
    """Garnet in stone and deepslate: noise of greys with dark red crystals (fixed seeds)."""
    out = {}
    for id_, light, dark in (('garnet_ore', (125, 125, 125), (105, 105, 105)), ('deepslate_garnet_ore', (80, 80, 86), (58, 58, 64))):
        rnd = random.Random(id_)
        im = Image.new('RGBA', (16, 16))
        px = im.load()
        for y in range(16):
            for x in range(16):
                t = rnd.random()
                px[x, y] = (light if t > 0.35 else dark) + (255,) if t > 0.1 else tuple(v - 12 for v in dark) + (255,)
        for cx, cy in ((4, 4), (11, 5), (6, 11), (12, 12)):
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                px[cx + dx, cy + dy] = (128, 20, 32, 255) if (dx + dy) % 2 == 0 else (176, 38, 52, 255)
            px[cx, cy] = (214, 84, 96, 255)
        out[id_] = im
    return out


def item_textures():
    out = {}
    for id_, en, zh, ic, r in SUPPLIES:
        c = Canvas((0, 0, 0, 0))
        if ic in ('tools_hss', 'tools_cermet', 'tools_carbon'):
            c.rect(2, 6, 13, 12, rgb('#3b3f45')); c.frame(2, 6, 13, 12, DARK)
            col = BRIGHT if ic == 'tools_hss' else rgb('#c9a24a') if ic == 'tools_cermet' else rgb('#6b6f75')
            for x in (4, 7, 10):
                c.rect(x, 3, x + 1, 9, col)
        elif ic == 'wheel_item':
            c.circle(8, 8, 6, shade(rgb('#d8d0c0'), 0.8), fill=rgb('#d8d0c0')); c.rect(7, 7, 8, 8, STEEL)
        elif ic == 'stones':
            for x in (3, 7, 11):
                c.rect(x, 3, x + 2, 13, rgb('#e2dac4')); c.rect(x + 2, 3, x + 2, 13, rgb('#b9b09a'))
        elif ic in ('tin', 'can'):
            body = rgb('#a8aeb4')
            c.rect(3, 4, 12, 14, body); c.rect(3, 3, 12, 3, DARK)
            c.rect(3, 7, 12, 10, rgb('#d8c070') if ic == 'tin' else BLUE)
        elif ic == 'blade':
            c.rect(1, 7, 14, 9, BRIGHT)
            for x in range(1, 15, 2):
                c.set(x, 10, DARK)
        elif ic == 'electrode':
            c.rect(5, 2, 10, 13, rgb('#3a3a3e')); c.rect(5, 2, 6, 13, rgb('#55555c'))
        elif ic == 'spool':
            c.rect(3, 3, 12, 4, DARK); c.rect(3, 11, 12, 12, DARK); c.rect(4, 5, 11, 10, rgb('#c8a050'))
        elif ic == 'bag':
            c.rect(3, 5, 12, 14, rgb('#b8a07a')); c.rect(5, 3, 10, 4, rgb('#8a7458'))
            for x, y in ((6, 8), (9, 10), (7, 12)):
                c.set(x, y, rgb('#8a1f2a'))
        elif ic.startswith('cyl_'):
            col = {'cyl_oxygen': rgb('#e8e8ea'), 'cyl_nitrogen': rgb('#2b2b2e'), 'cyl_argon': rgb('#3d7a3d'), 'cyl_empty': rgb('#8c9196')}[ic]
            c.rect(5, 4, 10, 14, col); c.rect(5, 4, 5, 14, shade(col, 0.8)); c.rect(6, 2, 9, 3, BRASS); c.rect(7, 1, 8, 1, DARK)
        elif ic.startswith('rivets'):
            col = STEEL if ic == 'rivets_steel' else rgb('#c9ced3')
            for x, y in ((3, 4), (8, 3), (11, 8), (5, 10), (10, 12)):
                c.rect(x, y, x + 2, y, col); c.set(x + 1, y + 1, shade(col, 0.8)); c.set(x + 1, y + 2, shade(col, 0.8))
        out[id_] = c.im
    c = Canvas((0, 0, 0, 0))
    for (x, y), col in (((5, 5), (128, 20, 32)), ((8, 4), (176, 38, 52)), ((6, 8), (150, 26, 40)), ((9, 8), (214, 84, 96)), ((7, 11), (128, 20, 32))):
        c.rect(x, y, x + 2, y + 2, col + (255,))
    out[RAW_GARNET[0]] = c.im
    return out


def textures():
    for name, im in block_textures().items():
        write_png(os.path.join(BLOCKS16, name + '.png'), im)
    for name, im in ore_textures().items():
        write_png(os.path.join(ASSETS, 'textures', 'block', name + '.png'), im)
    for name, im in item_textures().items():
        write_png(os.path.join(ASSETS, 'textures', 'item', name + '.png'), im)


if __name__ == '__main__':
    blocks = data()
    for lang in ('en', 'zh'):
        insert_lang(os.path.join(ASSETS, 'lang', 'en_us.json' if lang == 'en' else 'zh_tw.json'), lang)
    textures()
    print('machining assets:', len(blocks), 'blocks,', len(SUPPLIES) + 1, 'items,', written, 'files written')
