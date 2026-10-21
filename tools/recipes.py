"""hdfv 0.23: every recipe of the mod, written from this one spec (the user 2026-10-20: "先改合成配方" -- a tech tree,
more real materials and processes, one game scale, other mods' tags; machines built at the assembly bench from standard
components, which are made on the machines from their drawings; the electric arc furnace and the electrolysis cell).

    py -3 tools/recipes.py

The recipe folder is this script's: files it does not write are removed (machining_assets.py and airport_assets.py no
longer write recipes). It stops when two crafting recipes take the same grid (a shaped one mirrored too) or the same
ingredients (shapeless), when a recipe of before 0.23 is neither written nor listed as dropped, and when a block's metal
is outside its class's band (the game scale: about a tenth of the real mass, see GAME_SCALE).

Ingredient notation: 'hdfv:x' an item, '#c:x' a tag, ('stock', alloy) a stock of an alloy (10 kg), ('bit', alloy) a stock
bit (1 kg), [a, b] either of them. Checked by RecipeRulesTest (the tech tree, the assembly times, the intermediates'
masses), MetallurgyRecipesTest (the electricity against AeroCore) and the recipe-presence tests.
"""
import json
import math
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
DATA = os.path.join(ROOT, 'neoforge', 'src', 'main', 'resources', 'data')
RDIR = os.path.join(DATA, 'hdfv', 'recipe')

written = 0


def write_json(path, obj):
    global written
    data = (json.dumps(obj, indent=2, ensure_ascii=False) + '\n').encode('utf-8')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and open(path, 'rb').read() == data:
        return
    open(path, 'wb').write(data)
    written += 1


# ------------------------------------------------------------------------------------------------ masses (kg of metal)

# components (core Component) and intermediates (core Intermediate): their finished masses
COMPONENT_KG = {
    'steel_plate': 10, 'electrical_steel': 5, 'copper_wire': 2, 'bed_casting': 40, 'frame_casting': 15, 'cylinder_casting': 12, 'cast_gear': 3,
    'cast_pulley': 5, 'bronze_bushing': 0.5, 'forged_shaft': 6, 'forged_crank': 6, 'turned_shaft': 3, 'leadscrew': 3, 'fastener_kit': 1, 'steel_tube': 6,
    'planed_slideway': 15, 'valve_body': 4, 'bored_cylinder': 12, 'cut_gear': 2, 'ball_bearing': 0.5, 'ground_slideway': 10, 'spindle': 5,
    'ball_screw': 2.5, 'circuit_board': 0.3, 'linear_guide': 3, 'precision_spindle': 6,
}
INTERMEDIATE_KG = {
    'electric_motor_unit': 33, 'transformer': 31, 'control_board': 1.4, 'servo_motor': 35.4, 'pump_unit': 24, 'hydraulic_cylinder': 25,
    'gear_train': 31, 'heat_exchanger': 56, 'alumina': 10, 'chromium_oxide': 10, 'magnesia': 10, 'ferrosilicon': 10, 'ferrochrome': 10,
    'titanium_slag': 10, 'titanium_sponge': 10, 'magnesium_chloride': 39, 'carbon_anode': 5,
}
# vanilla items and tags (their metal, kg; 1 ingot = 10 kg; what is not here has none)
VANILLA_KG = {
    '#c:ingots/iron': 10, '#c:ingots/copper': 10, '#c:ingots/gold': 10, '#c:nuggets/iron': 1.1, '#c:nuggets/gold': 1.1,
    '#c:storage_blocks/iron': 90, '#c:chains': 1.2, 'minecraft:iron_bars': 0.4, 'minecraft:hopper': 50, 'minecraft:piston': 10,
    'minecraft:sticky_piston': 10, 'minecraft:compass': 40, 'minecraft:clock': 40, 'minecraft:minecart': 50, 'minecraft:rail': 0.4,
    'minecraft:lightning_rod': 30, 'minecraft:iron_trapdoor': 40, 'minecraft:flint_and_steel': 10, 'minecraft:shears': 20, 'minecraft:cauldron': 70,
    'minecraft:bucket': 30, 'minecraft:water_bucket': 30, 'minecraft:blast_furnace': 50, 'minecraft:lantern': 1, 'minecraft:redstone_lamp': 0,
    '#c:ingots/nickel': 10, '#c:ingots/tin': 10, '#c:ingots/zinc': 10, '#c:ingots/aluminium': 10, '#c:ingots/chromium': 10,
    '#c:ingots/magnesium': 10, '#c:ingots/titanium': 10, 'hdfv:grinding_wheel': 3,
}


def kg(ing):
    """Metal in one of an ingredient, kg (alternatives: the first's)."""
    if isinstance(ing, list):
        return kg(ing[0])
    if isinstance(ing, tuple):
        return 10 if ing[0] == 'stock' else 1
    if ing.startswith('hdfv:'):
        i = ing[5:]
        if i in COMPONENT_KG:
            return COMPONENT_KG[i]
        if i in INTERMEDIATE_KG:
            return INTERMEDIATE_KG[i]
    return VANILLA_KG.get(ing, 0)


# the game scale (metal a block or item of a class holds, kg; about a tenth of the real thing)
GAME_SCALE = {
    'machine': (40, 300),     # a machine, a controller with its working parts, a power plant
    'workbench': (10, 100),   # the assembly bench, the design table (light)
    'member': (5, 80),        # a multiblock's member block
    'head': (10, 120),        # a head, an accessory, a drive, a CNC module
    'electronic': (2, 80),    # a readout, a console, a tablet
    'container': (10, 150),   # tanks, cylinders, cans
    'building': (0.3, 25),    # walls, floors, doors, rails, grating
    'fixture': (0.1, 6),      # lights, cables, pipes, signs, paint
}


# ------------------------------------------------------------------------------------------------ ingredients and results

def ingredient(i):
    if isinstance(i, list):
        return [ingredient(x) for x in i]
    if isinstance(i, tuple):
        item = 'hdfv:stock' if i[0] == 'stock' else 'hdfv:stock_bit'
        return {'type': 'neoforge:components', 'items': item, 'components': {'hdfv:alloy': i[1]}}
    if i.startswith('#'):
        return {'tag': i[1:]}
    return {'item': i}


def sized(i, n):
    return {'ingredient': ingredient(i), 'count': n}


def result(r, n=1):
    if isinstance(r, tuple):
        return {'id': 'hdfv:stock' if r[0] == 'stock' else 'hdfv:stock_bit', 'count': n, 'components': {'hdfv:alloy': r[1]}}
    return {'id': r if ':' in r else 'hdfv:' + r, 'count': n}


def key_of(i):
    """A comparable key of an ingredient (for clashes)."""
    return json.dumps(ingredient(i), sort_keys=True)


RECIPES = {}      # path (under hdfv:) -> recipe json
CLASS = {}        # output id -> scale class
SHAPED, SHAPELESS = {}, {}


def add(path, obj, out=None, cls=None):
    if path in RECIPES:
        sys.exit('two recipes ' + path)
    RECIPES[path] = obj
    if out and cls:
        CLASS[out] = cls


def shaped(id_, pattern, key, count=1, cls=None, category='misc', path=None):
    grid = tuple(''.join('|' + (key_of(key[ch]) if ch != ' ' else ' ') for ch in row) for row in pattern)
    mirror = tuple(''.join('|' + (key_of(key[ch]) if ch != ' ' else ' ') for ch in reversed(row)) for row in pattern)
    for g in (grid, mirror):
        if g in SHAPED and SHAPED[g] != id_:
            sys.exit('the grid of ' + id_ + ' is ' + SHAPED[g] + "'s")
    SHAPED[grid] = id_
    metal = sum(kg(key[ch]) for row in pattern for ch in row if ch != ' ') / count
    add(path or id_, {'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern,
                      'key': {k: ingredient(v) for k, v in key.items()}, 'result': result('hdfv:' + id_, count)}, id_, cls)
    if cls:
        check_scale(id_, cls, metal)


def shapeless(id_, items, count=1, cls=None, category='misc', path=None, out=None):
    sig = tuple(sorted(key_of(i) for i in items))
    if sig in SHAPELESS and SHAPELESS[sig] != (path or id_):
        sys.exit('the ingredients of ' + (path or id_) + ' are ' + SHAPELESS[sig] + "'s")
    SHAPELESS[sig] = path or id_
    metal = sum(kg(i) for i in items) / count
    add(path or id_, {'type': 'minecraft:crafting_shapeless', 'category': category, 'ingredients': [ingredient(i) for i in items],
                      'result': out if out else result('hdfv:' + id_, count)}, id_, cls)
    if cls:
        check_scale(id_, cls, metal)


def labour_s(parts):
    """The fitting time of an assembly, work seconds: the parts' rule (Plans.assembleS: 300 s, a minute a piece, 3 s a kg)."""
    pieces = sum(parts.values())
    mass = sum(kg(i) * n for i, n in parts.items())
    return int(math.floor(300 + 60 * pieces + 3 * mass + 0.5))   # half up, as Java's Math.round


def bench(id_, parts, count=1, cls=None):
    """Assembled at the assembly bench (hdfv:assembling) from parts {ingredient: count}."""
    if len(parts) > 18:
        sys.exit(id_ + ': more than 18 kinds of part')
    metal = sum(kg(i) * n for i, n in parts.items()) / count
    add(id_, {'type': 'hdfv:assembling', 'ingredients': [sized(i, n) for i, n in parts.items()], 'result': result('hdfv:' + id_, count),
              'seconds': labour_s(parts)}, id_, cls)
    if cls:
        check_scale(id_, cls, metal)
    return metal


def alloy(path, ingredients, res, ticks, byproduct=None):
    if len(ingredients) > 4:
        sys.exit(path + ': more than four ingredients')
    r = {'type': 'hdfv:alloying', 'ingredients': [sized(i, n) for i, n in ingredients], 'result': result(*res), 'ticks': ticks}
    if byproduct:
        r['byproduct'] = result(*byproduct)
    add('alloying/' + path, r)


def electric(kind, path, ingredients, res, kwh, byproducts=()):
    if len(ingredients) > 4 or len(byproducts) > 2:
        sys.exit(path + ': four ingredients and two by-products at most')
    r = {'type': 'hdfv:' + kind, 'ingredients': [sized(i, n) for i, n in ingredients], 'result': result(*res), 'kwh': kwh}
    if byproducts:
        r['byproducts'] = [result(*b) for b in byproducts]
    add(kind + '/' + path, r)


SCALE_ERRORS = []


def check_scale(id_, cls, metal):
    lo, hi = GAME_SCALE[cls]
    if not lo <= metal <= hi:
        SCALE_ERRORS.append('%s (%s): %.1f kg of metal, not %s-%s' % (id_, cls, metal, lo, hi))


# ------------------------------------------------------------------------------------------------ short names

H = lambda s: 'hdfv:' + s  # noqa: E731
PLATE, ESTEEL, CWIRE, BED, FRAME, CYL, CGEAR, PULLEY, BUSH, FSHAFT, CRANK = map(H, (
    'steel_plate', 'electrical_steel', 'copper_wire', 'bed_casting', 'frame_casting', 'cylinder_casting', 'cast_gear', 'cast_pulley', 'bronze_bushing',
    'forged_shaft', 'forged_crank'))
TSHAFT, LSCREW, BOLTS, TUBE, PWAY, VALVE, BCYL = map(H, ('turned_shaft', 'leadscrew', 'fastener_kit', 'steel_tube', 'planed_slideway', 'valve_body',
                                                       'bored_cylinder'))
GEAR, BEARING, GWAY, SPINDLE, BSCREW, PCB, LGUIDE, PSPINDLE = map(H, ('cut_gear', 'ball_bearing', 'ground_slideway', 'spindle', 'ball_screw',
                                                                   'circuit_board', 'linear_guide', 'precision_spindle'))
MOTOR, XFMR, BOARD, SERVO, PUMPU, HCYL, GTRAIN, HX = map(H, ('electric_motor_unit', 'transformer', 'control_board', 'servo_motor', 'pump_unit',
                                                          'hydraulic_cylinder', 'gear_train', 'heat_exchanger'))
STEEL, IRON, COPPER, GOLD, NUGGET, GNUGGET = ('stock', 'steel'), '#c:ingots/iron', '#c:ingots/copper', '#c:ingots/gold', '#c:nuggets/iron', '#c:nuggets/gold'
REDSTONE, QUARTZ, DIAMOND, PANE, GLASS = '#c:dusts/redstone', '#c:gems/quartz', '#c:gems/diamond', '#c:glass_panes', '#c:glass_blocks'
PLANKS, STICK, STRING, GUNPOWDER, SLIME, CHAIN = '#minecraft:planks', '#c:rods/wooden', '#c:strings', '#c:gunpowders', '#c:slime_balls', '#c:chains'
NICKEL, TIN, ZINC, AL, CR, MG, TI = ('#c:ingots/nickel', '#c:ingots/tin', '#c:ingots/zinc', '#c:ingots/aluminium', '#c:ingots/chromium',
                                     '#c:ingots/magnesium', '#c:ingots/titanium')
RUBBER_OR_PP = [H('synthetic_rubber'), H('polypropylene')]
# the metals as results (a result is an item, not a tag)
AL_I, MG_I, CR_I, NUGGET_I = H('aluminium_ingot'), H('magnesium_ingot'), H('chromium_ingot'), 'minecraft:iron_nugget'


def dye(c):
    return '#c:dyes/' + c


# ------------------------------------------------------------------------------------------------ T0: the crafting table and the furnaces

def t0():
    shaped('design_table', ['PPP', 'WCW', 'W W'], {'P': 'minecraft:paper', 'W': PLANKS, 'C': '#c:player_workstations/crafting_tables'}, cls=None)
    shapeless('blank_blueprint', ['minecraft:paper', 'minecraft:paper', dye('blue')], 2)
    # 0.24: the tutorial's handbook (new players get one; a lost one is a book and a blank blueprint)
    shapeless('handbook', ['minecraft:book', H('blank_blueprint')])
    # the tablet is a computer: a control board behind its screen
    shaped('design_tablet', ['G', 'B', 'P'], {'G': PANE, 'B': BOARD, 'P': PLATE}, cls='electronic')
    shaped('vehicle_blueprint', ['BB', 'BB'], {'B': H('blank_blueprint')})
    shaped('alloy_furnace', ['BBB', 'BFB', 'BIB'], {'B': 'minecraft:bricks', 'F': 'minecraft:blast_furnace', 'I': IRON}, cls='machine')
    shaped('foundry', ['BBB', 'BCB', 'BIB'], {'B': 'minecraft:bricks', 'C': 'minecraft:cauldron', 'I': IRON}, cls='machine')
    shaped('forging_press', ['SPS', 'S S', 'SBS'], {'S': STEEL, 'P': 'minecraft:piston', 'B': '#c:storage_blocks/iron'}, cls='machine')
    shaped('assembly_bench', ['SSS', 'WCW', 'W W'], {'S': STEEL, 'W': PLANKS, 'C': '#c:player_workstations/crafting_tables'}, cls='workbench')
    shaped('treadle', ['  S', 'SPS', 'WWW'], {'S': STICK, 'P': 'minecraft:oak_pressure_plate', 'W': 'minecraft:oak_planks'})
    shapeless('stock_oak', ['minecraft:oak_log', 'minecraft:oak_log'], out=result(('stock', 'oak')), path='stock_oak')
    shapeless('stock_spruce', ['minecraft:spruce_log', 'minecraft:spruce_log'], out=result(('stock', 'spruce')), path='stock_spruce')
    shapeless('stock_plywood', [PLANKS, PLANKS, PLANKS, PLANKS, SLIME], out=result(('stock', 'plywood')), path='stock_plywood')
    add('stock_split', {'type': 'hdfv:stock_split', 'category': 'misc'})
    # the first tools: plain carbon steel (case-hardened with charcoal or coal)
    shapeless('carbon_tool_set', [('bit', 'steel'), ('bit', 'steel'), ['minecraft:coal', 'minecraft:charcoal']])
    shaped('grinding_wheel', ['BCB', 'CNC', 'BCB'], {'B': H('bauxite'), 'C': 'minecraft:clay_ball', 'N': NUGGET})
    shapeless('honing_stones', [H('bauxite'), H('bauxite'), 'minecraft:clay_ball', 'minecraft:clay_ball'])
    shapeless('lapping_compound', [H('bauxite'), SLIME], 2)
    shapeless('garnet_abrasive', [H('raw_garnet')], 4)
    shaped('electrode', ['KK', 'KK'], {'K': H('coke')})
    shapeless('electrolyte', [GUNPOWDER, 'minecraft:water_bucket'], 4)
    shapeless('steel_rivets', [('bit', 'steel'), ('bit', 'steel')])
    shapeless('aluminium_rivets', [('bit', 'aluminium_cast'), ('bit', 'aluminium_cast')])
    shapeless('diamond_dresser', [DIAMOND, ('bit', 'steel')])


# ------------------------------------------------------------------------------------------------ the assemblies

def assemblies():
    bench('electric_motor_unit', {FRAME: 1, TSHAFT: 1, ESTEEL: 2, CWIRE: 2, BUSH: 2})
    bench('transformer', {ESTEEL: 2, CWIRE: 3, FRAME: 1})
    bench('control_board', {PCB: 1, QUARTZ: 2, GNUGGET: 1, REDSTONE: 4})
    bench('servo_motor', {MOTOR: 1, BOARD: 1, BEARING: 2})
    bench('pump_unit', {BCYL: 1, VALVE: 2, TSHAFT: 1, BUSH: 2})
    bench('hydraulic_cylinder', {BCYL: 1, TSHAFT: 1, VALVE: 1, TUBE: 1})
    bench('gear_train', {GEAR: 4, TSHAFT: 2, BEARING: 4, FRAME: 1})
    bench('heat_exchanger', {TUBE: 6, PLATE: 2})


# ------------------------------------------------------------------------------------------------ the workshop (0.7) and the manual machine tools (0.9)

def workshop():
    bench('composite_oven', {PLATE: 4, FRAME: 1, CWIRE: 2, 'minecraft:white_wool': 2}, cls='machine')
    bench('vulcanizer', {PLATE: 3, FRAME: 1, CYL: 1, CWIRE: 1}, cls='machine')
    bench('welding_station', {XFMR: 1, CWIRE: 2, PLATE: 1, 'minecraft:flint_and_steel': 1}, cls='machine')
    bench('lathe', {BED: 1, FRAME: 1, FSHAFT: 2, CGEAR: 4, BUSH: 2, PLATE: 1}, cls='machine')
    bench('drill_press', {FRAME: 1, FSHAFT: 1, CGEAR: 2, PULLEY: 1, BUSH: 2, PLATE: 1}, cls='machine')
    bench('planer', {BED: 2, FRAME: 1, FSHAFT: 2, CGEAR: 2, CRANK: 1, BUSH: 2}, cls='machine')
    bench('milling_machine', {BED: 1, FRAME: 1, PWAY: 2, LSCREW: 2, CGEAR: 4, TSHAFT: 1, BUSH: 4}, cls='machine')
    bench('boring_mill', {BED: 1, FRAME: 2, PWAY: 1, LSCREW: 2, TSHAFT: 1, CGEAR: 4}, cls='machine')
    # members
    shapeless('lathe_bed', [BED], 2, cls='member')
    shapeless('planer_bed', [BED, PLATE], 2, cls='member')
    shapeless('planer_upright', [FRAME, FRAME], 2, cls='member')
    shapeless('cross_rail', [FRAME, FSHAFT], 2, cls='member')
    shapeless('drill_column', [FRAME, FSHAFT, PLATE], 2, cls='member')
    shapeless('drill_table', [FRAME, PLATE], 2, cls='member')
    shapeless('mill_table', [PWAY, FRAME], 2, cls='member')
    shapeless('boring_column', [FRAME, PWAY, FSHAFT], 2, cls='member')
    shapeless('boring_table', [PWAY, PLATE], 2, cls='member')
    # heads of the 0.9 tools
    bench('mill_head', {FRAME: 1, TSHAFT: 2, CGEAR: 2, BUSH: 2}, cls='head')
    bench('boring_head', {FRAME: 1, TSHAFT: 1, CGEAR: 2, BUSH: 2}, cls='head')
    bench('drill_head', {FRAME: 1, FSHAFT: 1, CGEAR: 2, BUSH: 2}, cls='head')
    bench('drill_spindle', {FSHAFT: 1, BUSH: 2, PULLEY: 1}, cls='head')
    # accessories
    bench('lathe_tailstock', {FRAME: 1, FSHAFT: 1, BUSH: 2}, cls='head')
    bench('steady_rest', {FRAME: 1, BUSH: 3}, cls='head')
    bench('toolpost_grinder', {MOTOR: 1, H('grinding_wheel'): 1}, cls='head')
    bench('quick_change_post', {FRAME: 1, BOLTS: 2}, cls='head')
    bench('second_tool_head', {FRAME: 1, LSCREW: 1, BOLTS: 1}, cls='head')
    bench('side_head', {FRAME: 1, LSCREW: 1, CGEAR: 2}, cls='head')
    bench('dividing_head', {FRAME: 1, CGEAR: 2, TSHAFT: 1, BUSH: 2}, cls='head')
    bench('mill_rotary_table', {FRAME: 1, CGEAR: 2, LSCREW: 1}, cls='head')
    bench('boring_rotary_table', {FRAME: 1, CGEAR: 2, PWAY: 1}, cls='head')
    bench('facing_head', {FRAME: 1, LSCREW: 1, CGEAR: 1}, cls='head')
    bench('multi_spindle_head', {FRAME: 1, TSHAFT: 3, CGEAR: 3}, cls='head')
    shapeless('jig_plate', [PLATE, BOLTS], cls='head')
    bench('power_feed', {MOTOR: 1, CGEAR: 2, LSCREW: 1}, cls='head')
    bench('digital_readout', {BOARD: 1, PANE: 1, CWIRE: 1}, cls='electronic')
    # drives
    bench('pulley_drive', {PULLEY: 1, FSHAFT: 1, BUSH: 2, FRAME: 1}, cls='head')
    bench('motor_drive', {MOTOR: 1, FRAME: 1, CWIRE: 2}, cls='head')
    bench('nc_drive', {SERVO: 1, BOARD: 1, FRAME: 1}, cls='head')


# ------------------------------------------------------------------------------------------------ the CNC (0.8) and the machining families (0.17)

def cnc_and_families():
    bench('cnc_machining_centre', {BED: 1, GWAY: 2, BSCREW: 3, SERVO: 3, SPINDLE: 1, BOARD: 2, PLATE: 4, MOTOR: 1}, cls='machine')
    shapeless('machine_bed', [BED, GWAY, PLATE], 2, cls='member')
    shapeless('machine_frame', [FRAME, FRAME, TUBE], 2, cls='member')
    shapeless('machine_guard', [PLATE, PANE], 2, cls='building')
    bench('spindle_head', {SPINDLE: 1, MOTOR: 1, FRAME: 1, BEARING: 2}, cls='head')
    bench('rotary_table', {SERVO: 1, GEAR: 2, FRAME: 1, BEARING: 2}, cls='head')
    bench('trunnion', {H('rotary_table'): 1, SERVO: 1, FRAME: 1}, cls='head')
    # a turning spindle of a ground spindle (the precision spindle needs one to be turned: no circle)
    bench('turning_spindle', {SPINDLE: 1, MOTOR: 1, FRAME: 1, BEARING: 2}, cls='head')
    bench('sub_spindle', {H('turning_spindle'): 1, SERVO: 1, LGUIDE: 2}, cls='head')
    bench('turret', {SERVO: 1, FRAME: 1, GEAR: 2, LGUIDE: 1}, cls='head')
    bench('y_turret', {H('turret'): 1, LGUIDE: 2, SERVO: 1}, cls='head')
    bench('b_axis_head', {H('spindle_head'): 1, SERVO: 1, GEAR: 2}, cls='head')
    bench('auto_loader', {SERVO: 1, '#c:chests/wooden': 1, PLATE: 1}, cls='head')
    bench('bar_feeder', {LGUIDE: 1, MOTOR: 1, TUBE: 1}, cls='head')
    # the families' controllers
    bench('grinder', {BED: 1, GWAY: 2, SPINDLE: 1, MOTOR: 1, LSCREW: 2, PLATE: 1}, cls='machine')
    bench('finisher', {FRAME: 2, GWAY: 1, SPINDLE: 1, MOTOR: 1, LSCREW: 1}, cls='machine')
    bench('gear_cutter', {BED: 1, FRAME: 1, GEAR: 4, SPINDLE: 1, LSCREW: 2, MOTOR: 1}, cls='machine')
    bench('ram_machine', {BED: 1, FRAME: 1, CRANK: 1, GEAR: 2, PWAY: 1, MOTOR: 1}, cls='machine')
    bench('sawing_machine', {BED: 1, FRAME: 1, GEAR: 2, PULLEY: 2, MOTOR: 1}, cls='machine')
    bench('electro_machine', {FRAME: 2, LGUIDE: 2, SERVO: 2, BOARD: 2, XFMR: 1}, cls='machine')
    bench('cutting_table', {BED: 1, LGUIDE: 2, SERVO: 2, BOARD: 1, PLATE: 2}, cls='machine')
    bench('threader', {FRAME: 2, GEAR: 2, SPINDLE: 1, MOTOR: 1}, cls='machine')
    bench('riveter', {FRAME: 2, HCYL: 1, PUMPU: 1}, cls='machine')
    # their members
    shapeless('grinder_bed', [BED, GWAY], 2, cls='member')
    shapeless('finisher_column', [FRAME, GWAY], 2, cls='member')
    shapeless('finisher_table', [FRAME, GWAY, PLATE], 2, cls='member')
    shapeless('finisher_top', [FRAME, TSHAFT, PLATE], 2, cls='member')
    shapeless('gear_column', [FRAME, GWAY, TSHAFT], 2, cls='member')
    shapeless('gear_table', [FRAME, GEAR, GEAR], 2, cls='member')
    shapeless('gear_top', [FRAME, GEAR, TSHAFT], 2, cls='member')
    shapeless('ram_bed', [BED, PWAY], 2, cls='member')
    shapeless('saw_bed', [BED, PULLEY], 2, cls='member')
    shapeless('edm_tank', [PLATE, PLATE, PLATE, GLASS], cls='member')
    shapeless('cutting_slats', [PLATE, PLATE], 2, cls='member')
    shapeless('threader_base', [BED, FRAME], 2, cls='member')
    shapeless('riveter_frame', [FRAME, FRAME, PLATE], 2, cls='member')
    # their accessories
    shapeless('magnetic_chuck', [PLATE, CWIRE, CWIRE, REDSTONE], cls='head')
    shapeless('mitre_vise', [FRAME, LSCREW], cls='head')
    bench('electrode_changer', {SERVO: 1, '#c:chests/wooden': 1, PLATE: 1, BOARD: 1}, cls='head')
    shapeless('fume_extractor', [MOTOR, TUBE], cls='head')
    bench('bevel_head', {SERVO: 1, GEAR: 2, FRAME: 1}, cls='head')
    # heads: grinders
    bench('cylindrical_grinding_head', {SPINDLE: 1, H('grinding_wheel'): 1, FRAME: 1, BEARING: 2}, cls='head')
    bench('internal_grinding_head', {SPINDLE: 1, H('grinding_wheel'): 1, FRAME: 1, BEARING: 2, TSHAFT: 1}, cls='head')
    bench('surface_grinding_head', {SPINDLE: 1, H('grinding_wheel'): 1, FRAME: 1, BEARING: 2, PLATE: 1}, cls='head')
    bench('centreless_grinding_head', {SPINDLE: 2, H('grinding_wheel'): 2, FRAME: 1, H('synthetic_rubber'): 1}, cls='head')
    bench('tool_grinding_head', {SPINDLE: 1, H('grinding_wheel'): 1, FRAME: 1, DIAMOND: 1, BEARING: 2}, cls='head')
    # finishing
    bench('honing_head', {SPINDLE: 1, H('honing_stones'): 1, FRAME: 1, HCYL: 1}, cls='head')
    bench('lapping_head', {FRAME: 1, GEAR: 2, PLATE: 1, H('lapping_compound'): 1}, cls='head')
    bench('polishing_head', {MOTOR: 1, 'minecraft:white_wool': 2, FRAME: 1}, cls='head')
    bench('deburring_head', {MOTOR: 1, FRAME: 1, '#c:sands': 1}, cls='head')
    # gears
    bench('hobbing_head', {SPINDLE: 1, GEAR: 2, FRAME: 1}, cls='head')
    bench('gear_shaping_head', {CRANK: 1, SPINDLE: 1, FRAME: 1}, cls='head')
    bench('shaving_head', {SPINDLE: 1, GEAR: 1, FRAME: 1, BEARING: 2}, cls='head')
    bench('gear_grinding_head', {SPINDLE: 1, H('grinding_wheel'): 1, GEAR: 1, FRAME: 1}, cls='head')
    bench('chamfering_head', {SPINDLE: 1, FRAME: 1, PLATE: 1}, cls='head')
    # reciprocating
    bench('shaper_head', {CRANK: 1, FRAME: 1, PWAY: 1}, cls='head')
    bench('slotter_head', {CRANK: 1, FRAME: 1, TSHAFT: 2}, cls='head')
    bench('broaching_head', {HCYL: 1, FRAME: 1, GWAY: 1}, cls='head')
    # saws
    bench('band_saw_head', {PULLEY: 2, FRAME: 1, H('saw_blade'): 1}, cls='head')
    bench('cold_saw_head', {MOTOR: 1, FRAME: 1, GEAR: 2}, cls='head')
    bench('hacksaw_head', {CRANK: 1, FRAME: 1, PLATE: 1}, cls='head')
    # electrical machining
    bench('sinker_edm_head', {SERVO: 1, XFMR: 1, FRAME: 1, LGUIDE: 1}, cls='head')
    bench('wire_edm_head', {SERVO: 2, LGUIDE: 2, FRAME: 1}, cls='head')
    bench('hole_edm_head', {SERVO: 1, XFMR: 1, PSPINDLE: 1}, cls='head')
    bench('ecm_head', {PUMPU: 1, XFMR: 1, FRAME: 1, LGUIDE: 1}, cls='head')
    # cutting table
    bench('laser_head', {BOARD: 1, XFMR: 1, LGUIDE: 1, DIAMOND: 1, QUARTZ: 2}, cls='head')
    bench('plasma_head', {XFMR: 2, BOARD: 1, LGUIDE: 1, CWIRE: 2}, cls='head')
    bench('waterjet_head', {PUMPU: 2, HCYL: 1, LGUIDE: 1, DIAMOND: 1}, cls='head')
    # threads
    bench('thread_rolling_head', {GEAR: 2, SPINDLE: 1, FRAME: 1, HCYL: 1}, cls='head')
    bench('flat_die_head', {FRAME: 1, GWAY: 2, HCYL: 1}, cls='head')
    bench('tapping_head', {SPINDLE: 1, FRAME: 1, GEAR: 1}, cls='head')
    bench('thread_milling_head', {SPINDLE: 1, SERVO: 1, FRAME: 1}, cls='head')
    # rivets
    bench('squeeze_riveter_head', {HCYL: 1, FRAME: 1}, cls='head')
    bench('impact_riveter_head', {FRAME: 1, BCYL: 1, VALVE: 1}, cls='head')
    bench('drill_riveter_head', {SERVO: 1, SPINDLE: 1, HCYL: 1, BOARD: 1}, cls='head')
    # the air separation unit (its compressor and the cold box's heat exchangers)
    bench('air_separation_unit', {HX: 2, MOTOR: 1, FRAME: 1}, cls='machine')
    # supplies made of the furnaces' metals
    shapeless('hss_tool_set', [CR, ('bit', 'alloy_steel'), ('bit', 'alloy_steel')])
    shapeless('cermet_tool_set', [TI, H('coke'), NICKEL])
    shapeless('saw_blade', [('bit', 'alloy_steel'), ('bit', 'alloy_steel'), ('bit', 'alloy_steel')])
    shapeless('brass_wire_spool', [CWIRE, ZINC, STICK])
    shapeless('empty_gas_cylinder', [TUBE, VALVE], cls='container')


# ------------------------------------------------------------------------------------------------ power, fluids, fuel (0.8, 0.10)

def power_and_fluids():
    shapeless('line_shaft', [FSHAFT], 3, cls='fixture')
    shapeless('cable', [CWIRE, CWIRE, RUBBER_OR_PP], 8, cls='fixture')
    shapeless('heavy_cable', [CWIRE, CWIRE, CWIRE, CWIRE, RUBBER_OR_PP], 4, cls='fixture')
    bench('generator', {MOTOR: 2, FRAME: 1, PULLEY: 1, PLATE: 1}, cls='machine')
    bench('electric_motor', {MOTOR: 1, FRAME: 1, PULLEY: 1}, cls='machine')
    bench('battery', {PLATE: 6, NICKEL: 6, CWIRE: 2, H('polypropylene'): 1}, cls='machine')
    bench('steam_engine', {CYL: 1, CRANK: 1, FRAME: 1, PULLEY: 1, BUSH: 2, PLATE: 3}, cls='machine')
    bench('engine_stand', {FRAME: 2, FSHAFT: 1, PLATE: 4}, cls='machine')
    shapeless('fluid_pipe', [TUBE], 4, cls='fixture')
    shapeless('fluid_tank', [PLATE, PLATE, PLATE, PLATE, VALVE], cls='container')
    bench('pump', {PUMPU: 1, MOTOR: 1, FRAME: 1}, cls='machine')
    shapeless('storage_tank_wall', [PLATE], 2, cls='building')
    shapeless('storage_tank_roof', [PLATE, PLATE, PLATE], 4, cls='building')
    shapeless('storage_tank_valve', [VALVE, PLATE, PLATE], cls='member')
    shaped('jerry_can', ['P', 'B'], {'P': PLATE, 'B': '#c:buckets/empty'}, cls='container')
    bench('fuel_dispenser', {PUMPU: 1, PLATE: 2, PANE: 1, VALVE: 1}, cls='machine')
    shapeless('cryogenic_tank', [H('fluid_tank'), NICKEL, NICKEL, NICKEL, NICKEL, H('glass_fibre'), H('glass_fibre'), H('glass_fibre'), H('glass_fibre')],
              cls='container')
    shapeless('hydrogen_cylinders', [H('carbon_fibre'), H('carbon_fibre'), H('carbon_fibre'), H('carbon_fibre'), H('epoxy_resin'), H('epoxy_resin'),
                                     H('polyethylene'), H('polyethylene'), VALVE])
    bench('hydrogen_liquefier', {HX: 1, PUMPU: 1, H('cryogenic_tank'): 1, NICKEL: 2, MOTOR: 1}, cls='machine')
    shapeless('reed_set', [('bit', 'alloy_steel'), ('bit', 'alloy_steel'), ('bit', 'alloy_steel'), ('bit', 'alloy_steel')])


# ------------------------------------------------------------------------------------------------ petroleum (0.8)

def petroleum():
    bench('geophone', {BOARD: 1, CWIRE: 1, PLATE: 1}, cls='electronic')
    shapeless('derrick', [TUBE, TUBE], 6, cls='building')
    bench('crown_block', {PULLEY: 4, FRAME: 1}, cls='member')
    shapeless('rig_floor', [PLATE, TUBE], 6, cls='building')
    bench('rig_rotary', {GTRAIN: 1, FRAME: 1, PLATE: 1}, cls='member')
    bench('drilling_rig', {GTRAIN: 1, MOTOR: 2, PULLEY: 2, FRAME: 1, PLATE: 2}, cls='machine')
    shapeless('samson_post', [TUBE, TUBE, PLATE], 2, cls='member')
    shapeless('walking_beam', [PLATE, TUBE, TUBE, TUBE], 3, cls='member')
    shapeless('horsehead', [PLATE, PLATE, PULLEY], cls='member')
    shapeless('jack_base', [BED, PLATE, PLATE], 3, cls='member')
    bench('jack_crank', {CRANK: 2, GTRAIN: 1}, cls='member')
    bench('pump_jack', {GTRAIN: 1, MOTOR: 1, CRANK: 2, FRAME: 1, PULLEY: 1}, cls='machine')
    shapeless('wellhead', [VALVE, VALVE, VALVE, TUBE], cls='member')
    bench('distillation_column', {HX: 1, PUMPU: 1, BOARD: 1, PLATE: 4, VALVE: 2}, cls='machine')
    shapeless('column_section', [PLATE, PLATE, TUBE, VALVE], 2, cls='member')
    shapeless('column_top', [PLATE, VALVE, TUBE], cls='member')
    bench('vacuum_column', {H('column_section'): 1, PUMPU: 1, HX: 1}, cls='member')
    bench('process_unit', {BOARD: 2, PUMPU: 1, HX: 1, PLATE: 2, VALVE: 2}, cls='machine')
    shapeless('unit_outlet', [VALVE, PLATE], 2, cls='member')
    bench('fired_heater', {TUBE: 4, 'minecraft:bricks': 4, PLATE: 2, VALVE: 1}, cls='member')
    bench('fcc_reactor', {PLATE: 4, 'minecraft:bricks': 2, TUBE: 2, VALVE: 1}, cls='member')
    bench('reformer_reactor', {PLATE: 4, TUBE: 2, GNUGGET: 2, VALVE: 1}, cls='member')
    bench('cracking_furnace', {TUBE: 6, 'minecraft:bricks': 4, VALVE: 1}, cls='member')
    bench('polymer_reactor', {PLATE: 3, MOTOR: 1, VALVE: 2, HX: 1}, cls='machine')
    bench('lube_extractor', {PLATE: 3, PUMPU: 1, VALVE: 1}, cls='member')
    shaped('asphalt', ['GGG', 'GBG', 'GGG'], {'G': '#c:gravels', 'B': H('bitumen')}, 8, category='building')


# ------------------------------------------------------------------------------------------------ assembly rooms, paint, refit (0.7, 0.13, 0.16)

def rooms():
    bench('car_factory_controller', {PLATE: 2, CWIRE: 1, 'minecraft:comparator': 1, 'minecraft:minecart': 1}, cls='electronic')
    bench('hangar_controller', {PLATE: 2, CWIRE: 1, 'minecraft:comparator': 1, '#c:feathers': 1}, cls='electronic')
    bench('shipyard_controller', {PLATE: 2, CWIRE: 1, 'minecraft:comparator': 1, 'minecraft:oak_boat': 1}, cls='electronic')
    shaped('assembly_floor', ['SSS', 'SYS', 'SSS'], {'S': 'minecraft:smooth_stone', 'Y': dye('yellow')}, 8, category='building')
    shapeless('crane_rail', [PLATE, CHAIN, dye('yellow')], 4, cls='building')
    shapeless('roller_shutter', [PLATE, PLATE, CHAIN], 6, cls='building')
    shapeless('hangar_door', [PLATE, PLATE, PLATE, TUBE], 6, cls='building')
    shapeless('dock_gate', [PLATE, PLATE, PLANKS, PLANKS, PLANKS], 6, cls='building')
    shaped('slipway', ['R R', 'WWW'], {'R': 'minecraft:rail', 'W': PLANKS}, 6, category='building')
    shapeless('work_light', [CWIRE, '#c:dusts/glowstone', PANE, ('bit', 'steel')], 2, cls='fixture')
    bench('paint_booth_controller', {PLATE: 2, CWIRE: 1, PANE: 1, REDSTONE: 1, 'minecraft:hopper': 1}, cls='electronic')
    bench('paint_booth_exhaust', {MOTOR: 1, PLATE: 1}, 2, cls='head')
    shapeless('paint_can', [H('epoxy_resin'), '#c:dyes', NUGGET], 2)
    shapeless('sticker_film', [H('polyethylene'), H('polyethylene')], 4)
    bench('refit_shop_controller', {PLATE: 2, CWIRE: 1, PANE: 1, REDSTONE: 1, HCYL: 1}, cls='machine')
    shapeless('lamp_unit', [PANE, COPPER, REDSTONE, NUGGET], 2, cls='fixture')


# ------------------------------------------------------------------------------------------------ airports (0.20, 0.21)

def airports():
    bench('control_tower', {BOARD: 2, PLATE: 2, CWIRE: 2, PANE: 1, 'minecraft:compass': 1}, cls='machine')
    shaped('tower_base', ['SSS', 'SPS', 'SSS'], {'S': 'minecraft:stone_bricks', 'P': PLATE}, 8, cls='building', category='building')
    shaped('tower_shaft', ['SSS', 'SGS', 'SSS'], {'S': 'minecraft:smooth_stone', 'G': PANE}, 8, category='building')
    shapeless('tower_cab_floor', [PLATE, 'minecraft:light_gray_concrete', 'minecraft:smooth_stone', 'minecraft:smooth_stone', 'minecraft:smooth_stone'],
              6, cls='building', category='building')
    shaped('tower_glass', ['GGG', 'GTG', 'GGG'], {'G': GLASS, 'T': ('bit', 'steel')}, 8, category='building')
    shapeless('tower_roof', [PLATE, PLATE, CWIRE], 4, cls='building', category='building')
    shapeless('tower_mast', [TUBE, 'minecraft:lightning_rod', 'minecraft:redstone_torch'], cls='member', category='building')
    bench('tower_radar', {SERVO: 1, BOARD: 2, PLATE: 2, CWIRE: 2}, cls='head')
    bench('tower_console', {BOARD: 2, PANE: 2, PLATE: 1}, cls='electronic')
    lamp = 'minecraft:redstone_lamp'
    shaped('airfield_runway_edge', ['WGW', ' L ', ' S '], {'W': dye('white'), 'G': PANE, 'L': lamp, 'S': ('bit', 'steel')}, 4, cls='fixture', category='building')
    shaped('airfield_runway_centre', ['WGW', 'SLS'], {'W': dye('white'), 'G': PANE, 'L': lamp, 'S': ('bit', 'steel')}, 4, cls='fixture', category='building')
    shaped('airfield_taxi_edge', ['BGB', ' L ', ' S '], {'B': dye('blue'), 'G': PANE, 'L': lamp, 'S': ('bit', 'steel')}, 4, cls='fixture', category='building')
    shaped('airfield_taxi_centre', ['NGN', 'SLS'], {'N': dye('green'), 'G': PANE, 'L': lamp, 'S': ('bit', 'steel')}, 4, cls='fixture', category='building')
    shaped('airfield_threshold', ['NGR', ' L ', ' S '], {'N': dye('green'), 'R': dye('red'), 'G': PANE, 'L': lamp, 'S': ('bit', 'steel')}, 4, cls='fixture',
           category='building')
    shaped('airfield_approach', ['GGG', ' L ', ' S '], {'G': PANE, 'L': lamp, 'S': ('bit', 'steel')}, 2, cls='fixture', category='building')
    shaped('airfield_approach_flasher', ['GQG', ' L ', ' S '], {'G': PANE, 'Q': QUARTZ, 'L': lamp, 'S': ('bit', 'steel')}, 2, cls='fixture', category='building')
    shaped('airfield_obstruction', ['GRG', ' L ', ' S '], {'G': PANE, 'R': dye('red'), 'L': lamp, 'S': ('bit', 'steel')}, 2, cls='fixture', category='building')
    shaped('airfield_papi', ['GGG', 'RLR', 'SSS'], {'G': PANE, 'R': dye('red'), 'L': lamp, 'S': ('bit', 'steel')}, cls='fixture', category='building')
    shaped('airfield_beacon', ['WGN', 'GLG', 'SSS'], {'W': dye('white'), 'N': dye('green'), 'G': PANE, 'L': lamp, 'S': ('bit', 'steel')}, cls='fixture',
           category='building')
    shaped('apron_floodlight', ['LLL', ' T ', ' T '], {'L': lamp, 'T': TUBE}, cls='member', category='building')
    shaped('airfield_turf', ['GGG', 'DDD'], {'G': 'minecraft:short_grass', 'D': 'minecraft:dirt'}, 3, category='building')
    shaped('apron_concrete', ['SCS', 'CGC', 'SCS'], {'S': 'minecraft:smooth_stone', 'C': 'minecraft:light_gray_concrete', 'G': '#c:gravels'}, 8,
           category='building')
    shaped('holding_sign', ['RWR', ' S '], {'R': 'minecraft:red_concrete', 'W': dye('white'), 'S': ('bit', 'steel')}, 2, cls='fixture', category='building')
    shapeless('white_line_paint', [dye('white'), H('epoxy_resin'), 'minecraft:clay_ball', 'minecraft:bucket'], category='building')
    shapeless('yellow_line_paint', [dye('yellow'), H('epoxy_resin'), 'minecraft:clay_ball', 'minecraft:bucket'], category='building')
    shapeless('threshold_paint', [dye('white'), dye('green'), H('epoxy_resin'), 'minecraft:clay_ball', 'minecraft:bucket'], category='building')
    shaped('windsock', ['TOW', 'T  '], {'T': TUBE, 'O': 'minecraft:orange_wool', 'W': 'minecraft:white_wool'}, cls='member', category='building')
    shapeless('interior_lamp', [PANE, '#c:dusts/glowstone', ('bit', 'steel'), ('bit', 'steel')], 2, cls='fixture', category='building')
    shapeless('steel_stairs', [PLATE, PLATE, TUBE], 4, cls='building', category='building')
    shapeless('steel_column', [TUBE, ('bit', 'steel')], 4, cls='building', category='building')
    shapeless('steel_grating', [PLATE, 'minecraft:iron_bars'], 8, cls='building', category='building')
    shapeless('cable_duct', ['minecraft:smooth_stone_slab', 'minecraft:smooth_stone_slab', 'minecraft:smooth_stone_slab', H('heavy_cable')], 3,
              category='building')
    shaped('tower_floor', ['CQC', 'QCQ', 'CQC'], {'C': 'minecraft:white_concrete', 'Q': QUARTZ}, 8, category='building')


# ------------------------------------------------------------------------------------------------ the furnaces: alloys, ores, chemicals

def furnaces():
    # 0.7's that stay (crucible steel, cast irons, bronze, copper, the light alloys melted together, composites and rubber stock)
    alloy('coke', [('minecraft:coal', 2)], ('hdfv:coke', 1), 400, ('hdfv:coal_tar', 1))
    alloy('stock_steel', [(IRON, 4), (H('coke'), 1)], (STEEL, 4), 400)
    alloy('stock_grey_iron', [(IRON, 4), ('minecraft:coal', 1)], (('stock', 'grey_iron'), 4), 400)
    alloy('stock_ductile_iron', [(IRON, 8), ('minecraft:coal', 1), (MG, 1)], (('stock', 'ductile_iron'), 9), 900)
    alloy('stock_bronze', [(COPPER, 3), (TIN, 1)], (('stock', 'bronze'), 4), 400)
    alloy('stock_copper', [(COPPER, 1)], (('stock', 'copper'), 1), 100)
    alloy('stock_aluminium_cast', [(AL, 9), (QUARTZ, 1)], (('stock', 'aluminium_cast'), 9), 900)
    alloy('stock_aluminium_piston', [(AL, 8), (QUARTZ, 1), (COPPER, 1)], (('stock', 'aluminium_piston'), 10), 1000)
    alloy('stock_aluminium_2024', [(AL, 9), (COPPER, 1)], (('stock', 'aluminium_2024'), 10), 1000)
    alloy('stock_aluminium_2618', [(AL, 8), (COPPER, 1), (MG, 1)], (('stock', 'aluminium_2618'), 10), 1000)
    alloy('stock_aluminium_7075', [(AL, 8), (ZINC, 1), (MG, 1)], (('stock', 'aluminium_7075'), 10), 1000)
    alloy('stock_magnesium', [(MG, 9), (AL, 1)], (('stock', 'magnesium'), 10), 1000)
    alloy('stock_cfrp', [(H('carbon_fibre'), 2), (H('epoxy_resin'), 1)], (('stock', 'cfrp'), 1), 100)
    alloy('stock_gfrp', [(H('glass_fibre'), 2), (H('epoxy_resin'), 1)], (('stock', 'gfrp'), 1), 100)
    alloy('stock_rubber', [(H('synthetic_rubber'), 2)], (('stock', 'rubber'), 1), 100)
    for a in ('alloy_steel', 'aluminium_2024', 'aluminium_2618', 'aluminium_7075', 'aluminium_cast', 'aluminium_piston', 'bronze', 'cfrp', 'cmsx_4', 'copper',
              'ductile_iron', 'gfrp', 'grey_iron', 'inconel_713c', 'inconel_718', 'magnesium', 'oak', 'plywood', 'rubber', 'spruce', 'stainless_steel', 'steel',
              'titanium', 'valve_steel'):
        alloy('remelt_' + a, [(('bit', a), 10)], (('stock', a), 1), 100)
    # 0.23: the ores' first steps (Bayer-like calcining, roasting), the reductions that run in a fuel furnace
    alloy('alumina', [(H('bauxite'), 4)], (H('alumina'), 2), 400)
    alloy('chromium_oxide', [(H('chromite'), 2)], (H('chromium_oxide'), 1), 400, (NUGGET_I, 3))
    alloy('magnesia', [(H('magnesite'), 2)], (H('magnesia'), 1), 400)
    alloy('carbon_anode', [(H('coke'), 2), (H('coal_tar'), 1)], (H('carbon_anode'), 1), 300)
    alloy('magnesium', [(H('magnesia'), 2), (H('ferrosilicon'), 1)], (MG_I, 1), 600)                # Pidgeon (magnesia and ferrosilicon under vacuum)
    alloy('chromium', [(H('chromium_oxide'), 3), (AL, 1)], (CR_I, 2), 200, (H('alumina'), 2))       # aluminothermic: it heats itself
    # coal tar's chemicals: kept (0.8), dearer than the petrochemical plant's
    alloy('epoxy_resin', [(H('coal_tar'), 3)], (H('epoxy_resin'), 1), 300)
    alloy('synthetic_rubber', [(H('coal_tar'), 3), (GUNPOWDER, 1)], (H('synthetic_rubber'), 2), 400)
    alloy('glass_fibre', [('#c:sands', 4)], (H('glass_fibre'), 2), 200)
    # propellants: 1 kg grains, mass for mass (KNSB 65/35 saltpetre and sugar; APCP 70 % AP, 15 % aluminium, 15 % binder)
    alloy('knsb_grain', [(GUNPOWDER, 2), ('minecraft:sugar', 1)], (H('knsb_grain'), 3), 200)
    alloy('apcp_grain', [(H('ammonium_perchlorate'), 14), (('bit', 'aluminium_cast'), 3), (('bit', 'rubber'), 3)], (H('apcp_grain'), 20), 600)
    # the furnace's ingots of the concentrates (as 0.7)
    for m in ('nickel', 'tin', 'zinc'):
        add('smelting/%s_ingot' % m, {'type': 'minecraft:smelting', 'category': 'misc', 'ingredient': {'item': 'hdfv:raw_' + m},
                                      'result': {'id': 'hdfv:%s_ingot' % m}, 'experience': 0.7, 'cookingtime': 200})
        add('blasting/%s_ingot' % m, {'type': 'minecraft:blasting', 'category': 'misc', 'ingredient': {'item': 'hdfv:raw_' + m},
                                      'result': {'id': 'hdfv:%s_ingot' % m}, 'experience': 0.7, 'cookingtime': 100})


# the arc furnace's and the cell's batches: kWh from AeroCore (core recipe.Metallurgy; MetallurgyRecipesTest within 2 %):
#   steel 0.3989 kWh/kg (Smelting.steelJPerKg); vacuum melt (Casting.meltJ to Tm + 100 K at 65 %): Inconel 718 0.4027, 713C 0.3857,
#   CMSX-4 0.3906, titanium 0.5589 kWh/kg; 75 % ferrosilicon 8.261, ferrochrome 3.546, titanium slag 1.945 kWh/kg; Kroll 15 kWh/kg (a rule);
#   pitch carbon fibre 25 MJ/kg; Hall-Heroult 12.998, MgCl2 12.973, ammonium perchlorate 8.087 kWh/kg
def electric_batches():
    arc, cell, argon, empty = 'electric_furnace', 'electrolysis', H('argon_cylinder'), (H('empty_gas_cylinder'), 1)
    electric(arc, 'steel', [(IRON, 8), (H('coke'), 1)], (STEEL, 8), 31.91)
    electric(arc, 'alloy_steel', [(IRON, 8), (NICKEL, 1), (H('ferrochrome'), 1), (H('coke'), 1)], (('stock', 'alloy_steel'), 10), 39.89)
    electric(arc, 'stainless_steel', [(IRON, 6), (H('ferrochrome'), 3), (NICKEL, 1)], (('stock', 'stainless_steel'), 10), 39.89)
    electric(arc, 'valve_steel', [(IRON, 6), (H('ferrochrome'), 2), (NICKEL, 1), (H('coke'), 1)], (('stock', 'valve_steel'), 9), 35.90)
    electric(arc, 'inconel_718', [(NICKEL, 5), (CR, 2), (IRON, 2)], (('stock', 'inconel_718'), 9), 36.25)
    electric(arc, 'inconel_713c', [(NICKEL, 7), (CR, 1), (AL, 1)], (('stock', 'inconel_713c'), 9), 34.71)
    electric(arc, 'cmsx_4', [(NICKEL, 6), (CR, 1), (AL, 1), ('minecraft:netherite_scrap', 1)], (('stock', 'cmsx_4'), 8), 31.25)
    electric(arc, 'titanium', [(H('titanium_sponge'), 9), (AL, 1), (argon, 1)], (('stock', 'titanium'), 10), 55.89, [empty])
    electric(arc, 'titanium_ingot', [(H('titanium_sponge'), 1), (argon, 1)], (H('titanium_ingot'), 1), 5.589, [empty])
    electric(arc, 'ferrosilicon', [('#c:sands', 2), (IRON, 1), (H('coke'), 3)], (H('ferrosilicon'), 1), 82.61)
    electric(arc, 'ferrochrome', [(H('chromite'), 3), (H('coke'), 2)], (H('ferrochrome'), 1), 35.46)
    electric(arc, 'titanium_slag', [(H('raw_ilmenite'), 2), (H('coke'), 1)], (H('titanium_slag'), 1), 19.45, [(NUGGET_I, 6)])
    electric(arc, 'titanium_sponge', [(H('titanium_slag'), 5), (MG, 3), (H('coke'), 2), (argon, 1)], (H('titanium_sponge'), 3), 450.0,
             [(H('magnesium_chloride'), 3), empty])
    electric(arc, 'carbon_fibre', [(H('coal_tar'), 4)], (H('carbon_fibre'), 1), 20.83)
    electric(cell, 'aluminium', [(H('alumina'), 2), (H('carbon_anode'), 1)], (AL_I, 1), 129.98)
    electric(cell, 'magnesium', [(H('magnesium_chloride'), 1)], (MG_I, 1), 129.73)
    electric(cell, 'ammonium_perchlorate', [(GUNPOWDER, 1), (REDSTONE, 1)], (H('ammonium_perchlorate'), 1), 8.087)
    # the machines themselves
    bench('arc_furnace', {XFMR: 2, PLATE: 6, 'minecraft:bricks': 4, CWIRE: 4, H('electrode'): 1}, cls='machine')
    bench('electrolysis_cell', {XFMR: 2, PLATE: 4, H('carbon_anode'): 2, CWIRE: 4, 'minecraft:bricks': 2}, cls='machine')


# ------------------------------------------------------------------------------------------------ other mods

def compat():
    # other mods' steel and bronze ingots are hdfv's stock (1 ingot = 1 stock); coke of other mods burns and alloys as hdfv's
    shapeless('stock_from_steel_ingot', ['#c:ingots/steel'], path='stock_from_steel_ingot', out=result(STEEL))
    shapeless('stock_from_bronze_ingot', ['#c:ingots/bronze'], path='stock_from_bronze_ingot', out=result(('stock', 'bronze')))
    # Create (only loaded with it): crushing the ores, pressing stock into plate, mixing bronze over heat
    cond = [{'type': 'neoforge:mod_loaded', 'modid': 'create'}]
    xp = {'id': 'create:experience_nugget', 'chance': 0.75}
    ores = {'bauxite': 'bauxite', 'ilmenite': 'raw_ilmenite', 'nickel': 'raw_nickel', 'chromite': 'chromite', 'magnesite': 'magnesite',
            'tin': 'raw_tin', 'zinc': 'raw_zinc', 'garnet': 'raw_garnet'}
    for ore, raw in ores.items():
        add('create/crushing/%s_ore' % ore, {'neoforge:conditions': cond, 'type': 'create:crushing', 'ingredients': [{'tag': 'c:ores/' + ore}],
                                              'processing_time': 250, 'results': [{'id': 'hdfv:' + raw}, {'id': 'hdfv:' + raw, 'chance': 0.75}, xp]})
    add('create/crushing/raw_garnet', {'neoforge:conditions': cond, 'type': 'create:crushing', 'ingredients': [{'item': 'hdfv:raw_garnet'}],
                                       'processing_time': 150, 'results': [{'id': 'hdfv:garnet_abrasive', 'count': 4}, {'id': 'hdfv:garnet_abrasive', 'chance': 0.5}]})
    add('create/pressing/steel_plate', {'neoforge:conditions': cond, 'type': 'create:pressing', 'ingredients': [ingredient(STEEL)],
                                        'results': [{'id': 'hdfv:steel_plate'}]})
    add('create/mixing/bronze', {'neoforge:conditions': cond, 'type': 'create:mixing', 'heat_requirement': 'heated',
                                 'ingredients': [{'tag': 'c:ingots/copper'}, {'tag': 'c:ingots/copper'}, {'tag': 'c:ingots/copper'}, {'tag': 'c:ingots/tin'}],
                                 'results': [{'id': 'hdfv:stock', 'count': 4, 'components': {'hdfv:alloy': 'bronze'}}]})


# ------------------------------------------------------------------------------------------------ check and write

# recipes of before 0.23 that are gone, and why
DROPPED = {
    'alloying/aluminium': 'electrolysis/aluminium (alumina and a carbon anode in the cell)',
    'alloying/chromium': 'alloying/chromium (aluminothermic) from chromium oxide',
    'alloying/magnesium': 'alloying/magnesium (Pidgeon) and electrolysis/magnesium',
    'alloying/titanium': 'electric_furnace/titanium_slag, titanium_sponge (Kroll), titanium',
    'alloying/carbon_fibre': 'electric_furnace/carbon_fibre (pitch fibre) and the polymer plant',
    'alloying/ammonium_perchlorate': 'electrolysis/ammonium_perchlorate',
    'alloying/stock_alloy_steel': 'electric_furnace/alloy_steel', 'alloying/stock_stainless_steel': 'electric_furnace/stainless_steel',
    'alloying/stock_valve_steel': 'electric_furnace/valve_steel', 'alloying/stock_inconel_718': 'electric_furnace/inconel_718',
    'alloying/stock_inconel_713c': 'electric_furnace/inconel_713c', 'alloying/stock_cmsx_4': 'electric_furnace/cmsx_4',
    'alloying/stock_titanium': 'electric_furnace/titanium',
}


def main():
    t0()
    assemblies()
    workshop()
    cnc_and_families()
    power_and_fluids()
    petroleum()
    rooms()
    airports()
    furnaces()
    electric_batches()
    compat()
    if SCALE_ERRORS:
        sys.exit('off the game scale:\n  ' + '\n  '.join(SCALE_ERRORS))
    # every recipe of before is here or dropped
    old = set()
    for dp, dn, fn in os.walk(RDIR):
        for f in fn:
            if f.endswith('.json'):
                old.add(os.path.relpath(os.path.join(dp, f), RDIR).replace('\\', '/')[:-5])
    lost = sorted(p for p in old if p not in RECIPES and p not in DROPPED and not p.startswith(('create/', 'electric_furnace/', 'electrolysis/')))
    if lost:
        sys.exit('recipes neither written nor dropped: ' + ', '.join(lost))
    for path, obj in RECIPES.items():
        write_json(os.path.join(RDIR, *path.split('/')) + '.json', obj)
    removed = 0
    for p in sorted(old - set(RECIPES)):
        os.remove(os.path.join(RDIR, *p.split('/')) + '.json')
        removed += 1
    for dp, dn, fn in sorted(os.walk(RDIR, topdown=False)):
        if dp != RDIR and not os.listdir(dp):
            os.rmdir(dp)
    print('recipes:', len(RECIPES), 'in the spec,', written, 'written,', removed, 'removed')


if __name__ == '__main__':
    main()
