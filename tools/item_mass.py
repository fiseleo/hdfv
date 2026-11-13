"""hdfv 0.25: the default masses of items, the data map hdfv:item_mass (data/hdfv/data_maps/item/item_mass.json; read by
neoforge storage/ItemMass after hdfv's own rules; data packs change or add entries: {"values": {"#tag or item": {"kg": n}}}).

    py -3 tools/item_mass.py

The scale is the workshop's game scale, not the real world's:
  * an ingot, a raw ore or a stock is 10 kg (Pace.KG_PER_INGOT), a nugget a ninth of it, a block of nine 90 kg;
  * coal 1 kg, a log 4 kg, a plank 1 kg, a stick 0.1 kg (PowerPlant.FuelItem: what the steam engine burns them as);
  * so a block of loose or solid material is tens of kilograms (stone 20, sand, gravel and dirt 15, glass 10): about a
    hundredth of a real cubic metre, as a block of iron (90 kg) is of real iron;
  * a tool or a piece of armour is the ingots (or gems, planks, stones) it is made of;
  * fluids are not on that scale: hdfv counts a millibucket a litre, so ItemMass adds what a bucket or a can holds at its
    real density (a bucket of water is its 30 kg of iron and 998 kg of water). The entries here are the empty containers
    (milk is a fluid only when a mod makes it one; else its bucket weighs as an empty one).

Two parts, written in this order (a later entry replaces an earlier one for the same item, so tags come before the
tags and items that refine them):
  1. DEFAULTS: vanilla's and other mods' items by tag or id.
  2. hdfv's blocks and items that have no rule in ItemMass: each the sum of what its crafting or bench recipe takes
     (tools/recipes.py's spec, read without writing it) over what it makes, or its entry in HDFV.
Items ItemMass has a rule for (stock, ingots and raw ores, parts, workpieces, standard components, intermediates, coke,
the refinery's solids, propellant grains, filled gas cylinders: RULED) are never written. Checked by StorageAssetsTest
(the scale's anchors against core's constants) and StorageGameTests (in the game).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recipes  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
OUT = os.path.join(ROOT, 'neoforge', 'src', 'main', 'resources', 'data', 'hdfv', 'data_maps', 'item', 'item_mass.json')

# ------------------------------------------------------------------------------------------------ 1. vanilla and other mods

# key ('#tag' or 'item id') -> kg each; broad tags first, then the tags and the items that refine them
DEFAULTS = [
    # the broadest first
    ('#minecraft:slabs', 10), ('#minecraft:stairs', 15), ('#minecraft:walls', 20),
    # metals and minerals
    ('#c:ingots', 10), ('#c:raw_materials', 10), ('#c:nuggets', 1.1), ('#c:storage_blocks', 90), ('#c:ores', 20),
    ('#c:plates', 10), ('#c:gears', 10), ('#c:wires', 2), ('#c:dusts', 1), ('#c:gems', 2), ('#c:rods', 1), ('#c:chains', 1.2),
    ('#c:storage_blocks/coal', 9), ('#c:storage_blocks/redstone', 9), ('#c:storage_blocks/lapis', 18), ('#c:storage_blocks/diamond', 18),
    ('#c:storage_blocks/emerald', 18), ('#c:storage_blocks/wheat', 1.8), ('#c:storage_blocks/slime', 4.5), ('#c:storage_blocks/bone_meal', 0.9),
    ('#c:storage_blocks/dried_kelp', 0.9),
    ('minecraft:coal', 1), ('minecraft:charcoal', 1), ('minecraft:flint', 0.5), ('minecraft:clay_ball', 4.5), ('minecraft:brick', 4.5),
    ('minecraft:nether_brick', 4.5), ('minecraft:netherite_scrap', 10), ('minecraft:ancient_debris', 30),
    # wood
    ('#minecraft:logs', 4), ('#minecraft:planks', 1), ('#c:rods/wooden', 0.1), ('#minecraft:wooden_slabs', 0.5), ('#minecraft:wooden_stairs', 0.75),
    ('#minecraft:wooden_doors', 2), ('#minecraft:wooden_trapdoors', 3), ('#minecraft:wooden_fences', 1.4), ('#minecraft:fence_gates', 2.2),
    ('#minecraft:wooden_pressure_plates', 2), ('#minecraft:wooden_buttons', 1), ('#minecraft:signs', 2), ('#minecraft:hanging_signs', 1.2),
    ('#minecraft:boats', 5), ('#minecraft:saplings', 0.5), ('#minecraft:leaves', 1), ('#c:chests/wooden', 8), ('#c:barrels/wooden', 7),
    ('#c:player_workstations/crafting_tables', 4), ('#minecraft:bamboo_blocks', 2), ('minecraft:bamboo', 0.2), ('minecraft:ladder', 0.25),
    ('minecraft:bowl', 0.2), ('minecraft:bookshelf', 7.5), ('minecraft:chiseled_bookshelf', 7.5), ('minecraft:scaffolding', 0.3),
    # stone, earth, glass
    ('#c:stones', 20), ('#c:cobblestones', 20), ('#minecraft:stone_bricks', 20), ('#c:sandstone/blocks', 15), ('#c:sands', 15), ('#c:gravels', 15),
    ('#minecraft:dirt', 15), ('#c:obsidians', 25), ('#c:netherracks', 15), ('#c:end_stones', 20), ('#minecraft:terracotta', 18), ('#c:concretes', 20),
    ('#c:concrete_powders', 15), ('#c:glass_blocks', 10), ('#c:glass_panes', 4), ('minecraft:ice', 10), ('minecraft:packed_ice', 10), ('minecraft:blue_ice', 10),
    ('minecraft:snow_block', 3),
    ('minecraft:smooth_stone', 20), ('minecraft:smooth_stone_slab', 10), ('minecraft:deepslate', 22), ('minecraft:cobbled_deepslate', 22),
    ('minecraft:bricks', 18), ('minecraft:clay', 18), ('minecraft:nether_bricks', 18), ('minecraft:basalt', 22), ('minecraft:blackstone', 22),
    ('minecraft:tuff', 18), ('minecraft:calcite', 20), ('minecraft:dripstone_block', 20), ('minecraft:soul_sand', 15), ('minecraft:soul_soil', 15),
    ('minecraft:glowstone', 4), ('minecraft:sea_lantern', 6), ('minecraft:quartz_block', 8), ('minecraft:amethyst_block', 8),
    # cloth, plants, animals, food
    ('#minecraft:wool', 1), ('#minecraft:wool_carpets', 0.7), ('#minecraft:beds', 6), ('#minecraft:banners', 6), ('#c:dyes', 0.1), ('#c:strings', 0.05),
    ('#c:feathers', 0.02), ('#c:leathers', 0.5), ('#c:bones', 0.3), ('#c:gunpowders', 0.2), ('#c:slime_balls', 0.5), ('#c:eggs', 0.06),
    ('#c:ender_pearls', 0.2), ('#c:seeds', 0.02), ('#c:crops', 0.2), ('#c:foods', 0.3), ('#minecraft:flowers', 0.05), ('#minecraft:candles', 0.2),
    ('minecraft:short_grass', 0.05), ('minecraft:paper', 0.01), ('minecraft:book', 0.5), ('minecraft:writable_book', 0.5), ('minecraft:written_book', 0.5),
    ('minecraft:enchanted_book', 0.5), ('minecraft:sugar', 0.2), ('minecraft:sugar_cane', 0.2), ('minecraft:cactus', 2), ('minecraft:pumpkin', 4),
    ('minecraft:melon', 4), ('minecraft:hay_block', 1.8), ('minecraft:torch', 0.1), ('minecraft:redstone_torch', 0.2), ('minecraft:lever', 0.5),
    # tools and armour by what they are made of: iron's and gold's by kind, then the lighter and the heavier ones
    ('#minecraft:pickaxes', 30), ('#minecraft:axes', 30), ('#minecraft:shovels', 10), ('#minecraft:hoes', 20), ('#minecraft:swords', 20),
    ('#minecraft:head_armor', 50), ('#minecraft:chest_armor', 80), ('#minecraft:leg_armor', 70), ('#minecraft:foot_armor', 40),
    ('minecraft:wooden_pickaxe', 3), ('minecraft:wooden_axe', 3), ('minecraft:wooden_shovel', 1), ('minecraft:wooden_hoe', 2), ('minecraft:wooden_sword', 2),
    ('minecraft:stone_pickaxe', 6), ('minecraft:stone_axe', 6), ('minecraft:stone_shovel', 2), ('minecraft:stone_hoe', 4), ('minecraft:stone_sword', 4),
    ('minecraft:diamond_pickaxe', 6), ('minecraft:diamond_axe', 6), ('minecraft:diamond_shovel', 2), ('minecraft:diamond_hoe', 4), ('minecraft:diamond_sword', 4),
    ('minecraft:netherite_pickaxe', 16), ('minecraft:netherite_axe', 16), ('minecraft:netherite_shovel', 12), ('minecraft:netherite_hoe', 14),
    ('minecraft:netherite_sword', 14),
    ('minecraft:leather_helmet', 2.5), ('minecraft:leather_chestplate', 4), ('minecraft:leather_leggings', 3.5), ('minecraft:leather_boots', 2),
    ('minecraft:chainmail_helmet', 15), ('minecraft:chainmail_chestplate', 25), ('minecraft:chainmail_leggings', 20), ('minecraft:chainmail_boots', 12),
    ('minecraft:diamond_helmet', 10), ('minecraft:diamond_chestplate', 16), ('minecraft:diamond_leggings', 14), ('minecraft:diamond_boots', 8),
    ('minecraft:netherite_helmet', 20), ('minecraft:netherite_chestplate', 26), ('minecraft:netherite_leggings', 24), ('minecraft:netherite_boots', 18),
    ('minecraft:turtle_helmet', 2.5), ('minecraft:elytra', 2), ('minecraft:shield', 16), ('minecraft:bow', 0.3), ('minecraft:crossbow', 11),
    ('minecraft:fishing_rod', 0.3), ('minecraft:shears', 20), ('minecraft:flint_and_steel', 10), ('minecraft:arrow', 0.1), ('minecraft:trident', 20),
    # iron goods and redstone (their ingots, and what else is in them)
    ('#c:buckets', 30), ('#minecraft:anvil', 310), ('#minecraft:rails', 0.5), ('minecraft:rail', 0.4), ('minecraft:iron_bars', 0.4),
    ('minecraft:iron_door', 20), ('minecraft:iron_trapdoor', 40), ('minecraft:heavy_weighted_pressure_plate', 20), ('minecraft:light_weighted_pressure_plate', 20),
    ('minecraft:cauldron', 70), ('minecraft:hopper', 58), ('minecraft:minecart', 50), ('minecraft:chest_minecart', 58), ('minecraft:hopper_minecart', 108),
    ('minecraft:furnace_minecart', 210), ('minecraft:tnt_minecart', 66), ('minecraft:compass', 41), ('minecraft:clock', 41), ('minecraft:lantern', 1),
    ('minecraft:soul_lantern', 1), ('minecraft:lightning_rod', 30), ('minecraft:furnace', 160), ('minecraft:blast_furnace', 270), ('minecraft:smoker', 176),
    ('minecraft:piston', 94), ('minecraft:sticky_piston', 94.5), ('minecraft:dispenser', 141), ('minecraft:dropper', 141), ('minecraft:observer', 124),
    ('minecraft:repeater', 62), ('minecraft:comparator', 63), ('minecraft:redstone_lamp', 8), ('minecraft:tnt', 61), ('minecraft:bell', 60),
    ('minecraft:beacon', 130), ('minecraft:ender_chest', 200), ('minecraft:enchanting_table', 105), ('minecraft:brewing_stand', 61),
    ('minecraft:oak_pressure_plate', 2), ('minecraft:oak_boat', 5), ('minecraft:powder_snow_bucket', 33),
    ('minecraft:glass_bottle', 0.3), ('minecraft:potion', 0.6), ('minecraft:splash_potion', 0.6), ('minecraft:lingering_potion', 0.6),
    ('minecraft:honey_bottle', 0.7), ('minecraft:experience_bottle', 0.4), ('minecraft:bundle', 0.5), ('#c:shulker_boxes', 9),
]

# ------------------------------------------------------------------------------------------------ 2. hdfv

# items neoforge ItemMass has a rule for: never in the map (their masses for the recipes' sums are below)
RULED = dict(recipes.COMPONENT_KG)
RULED.update(recipes.INTERMEDIATE_KG)
RULED.update({
    'stock': 10, 'stock_bit': 1, 'part': None, 'workpiece': None,
    'aluminium_ingot': 10, 'titanium_ingot': 10, 'nickel_ingot': 10, 'chromium_ingot': 10, 'magnesium_ingot': 10, 'tin_ingot': 10, 'zinc_ingot': 10,
    'bauxite': 10, 'raw_ilmenite': 10, 'raw_nickel': 10, 'chromite': 10, 'magnesite': 10, 'raw_tin': 10, 'raw_zinc': 10, 'raw_garnet': 10,
    'coke': 1,                                                                                         # PowerPlant.COKE
    'bitumen': 10, 'polyethylene': 5, 'polypropylene': 5, 'synthetic_rubber': 5, 'epoxy_resin': 4, 'carbon_fibre': 3,   # Refining.ITEM_KG
    'knsb_grain': 1, 'apcp_grain': 1,                                                                  # SolidRocketDesign.GRAIN_ITEM_KG
    'oxygen_cylinder': None, 'nitrogen_cylinder': None, 'argon_cylinder': None,                         # the empty cylinder and GasSupply's fill
})

# hdfv's items with no crafting or bench recipe to sum, or whose recipe's sum is not their mass (why)
HDFV = {
    'coal_tar': 1,                # two coal give a coke and a tar
    'glass_fibre': 3,             # as carbon fibre: two fibres and an epoxy a 10 kg stock (Refining.ITEM_KG)
    'ammonium_perchlorate': 1,    # 14 of it, 3 kg of aluminium and 3 kg of rubber make 20 grains of 1 kg
    'blueprint': 0.01,            # a sheet, as the blank it is drawn on
    'electrolyte': 10,            # a 10 kg can (Wear.ELECTROLYTE_KG); its recipe's bucket comes back
    'garnet_abrasive': 2.5,       # a raw garnet crushed into four bags
    'lapping_compound': 0.25,     # a tin (Wear.COMPOUND_KG)
    'grinding_wheel': 12,         # a 400 x 50 mm vitrified wheel (Consumable), not the ore and clay it is fired from
    'honing_stones': 1,           # a set of six small stones
    'brass_wire_spool': 5,        # a 5 kg spool (Consumable)
    'oil_seep': 15, 'creative_power': 10,
}
for _ore in ('bauxite', 'ilmenite', 'nickel', 'chromite', 'magnesite', 'tin', 'zinc', 'garnet'):
    HDFV[_ore + '_ore'] = 20
for _ore in ('ilmenite', 'nickel', 'chromite', 'tin', 'zinc', 'garnet'):
    HDFV['deepslate_' + _ore + '_ore'] = 22

# vanilla items the recipes take by id whose mass comes from a tag in the game
VIA_TAG = {
    'minecraft:oak_planks': '#minecraft:planks', 'minecraft:oak_log': '#minecraft:logs', 'minecraft:spruce_log': '#minecraft:logs',
    'minecraft:stone_bricks': '#minecraft:stone_bricks', 'minecraft:white_wool': '#minecraft:wool', 'minecraft:orange_wool': '#minecraft:wool',
    'minecraft:white_concrete': '#c:concretes', 'minecraft:light_gray_concrete': '#c:concretes', 'minecraft:red_concrete': '#c:concretes',
    'minecraft:dirt': '#minecraft:dirt', 'minecraft:bucket': '#c:buckets', 'minecraft:water_bucket': '#c:buckets', 'minecraft:iron_nugget': '#c:nuggets',
    'minecraft:quartz': '#c:gems', 'minecraft:redstone': '#c:dusts', 'minecraft:glass': '#c:glass_blocks', 'minecraft:glass_pane': '#c:glass_panes',
}

TABLE = dict(DEFAULTS)


def default_kg(key):
    """A vanilla ingredient's mass by the defaults: its own entry, else its tag's nearest parent ('#c:ingots/iron' -> '#c:ingots')."""
    key = VIA_TAG.get(key, key)
    while True:
        if key in TABLE:
            return TABLE[key]
        if key.startswith('#') and '/' in key:
            key = key[:key.rindex('/')]
            continue
        return None


UNKNOWN = set()
DERIVED = {}


def ingredient_kg(ing):
    """One of a recipe's ingredient (its JSON), kg."""
    if isinstance(ing, list):
        return ingredient_kg(ing[0])
    if ing.get('type') == 'neoforge:components':
        return 10 if ing['items'] == 'hdfv:stock' else 1
    key = '#' + ing['tag'] if 'tag' in ing else ing['item']
    if key.startswith('hdfv:'):
        return hdfv_kg(key[5:])
    kg = default_kg(key)
    if kg is None:
        UNKNOWN.add(key)
        return 0
    return kg


def recipe_kg(r):
    """What a crafting or bench recipe's ingredients weigh over what it makes; None for other kinds of recipe."""
    count = r.get('result', {}).get('count', 1)
    if r['type'] == 'minecraft:crafting_shaped':
        return sum(ingredient_kg(r['key'][ch]) for row in r['pattern'] for ch in row if ch != ' ') / count
    if r['type'] == 'minecraft:crafting_shapeless':
        return sum(ingredient_kg(i) for i in r['ingredients']) / count
    if r['type'] == 'hdfv:assembling':
        return sum(ingredient_kg(i['ingredient']) * i['count'] for i in r['ingredients']) / count
    return None


BY_RESULT = {}


def hdfv_kg(id_):
    if RULED.get(id_) is not None:
        return RULED[id_]
    if id_ in HDFV:
        return HDFV[id_]
    if id_ in DERIVED:
        return DERIVED[id_]
    if id_ not in BY_RESULT:
        UNKNOWN.add('hdfv:' + id_)
        return 0
    DERIVED[id_] = 0   # a recipe that takes its own product (none does) would not go round
    DERIVED[id_] = recipe_kg(BY_RESULT[id_])
    return DERIVED[id_]


def rounded(kg):
    """Three significant figures (and no less than a gram)."""
    if kg <= 0:
        return 0
    digits = 3 - len(str(int(kg))) if kg >= 1 else 3
    v = round(kg, max(digits, 0)) if kg >= 1 else float('%.2g' % kg)
    return int(v) if v == int(v) else v


def main():
    recipes.build()
    for path, r in recipes.RECIPES.items():
        res = r.get('result', {})
        rid = res.get('id', '')
        if not rid.startswith('hdfv:') or 'components' in res:
            continue
        if r['type'] in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'hdfv:assembling'):
            BY_RESULT.setdefault(rid[5:], r)
    values = {}
    for key, kg in DEFAULTS:
        if key in values:
            sys.exit('twice in DEFAULTS: ' + key)
        values[key] = {'kg': kg}
    made = {}
    for id_ in BY_RESULT:
        if id_ in RULED:
            continue
        made[id_] = hdfv_kg(id_)
    for id_, kg in HDFV.items():
        made[id_] = kg
    if UNKNOWN:
        sys.exit('no mass for: ' + ', '.join(sorted(UNKNOWN)))
    for id_ in sorted(made):
        if not made[id_] > 0:
            sys.exit('hdfv:' + id_ + ' weighs nothing')
        values['hdfv:' + id_] = {'kg': rounded(made[id_])}
    data = (json.dumps({'values': values}, indent=2, ensure_ascii=False) + '\n').encode('utf-8')
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    changed = not (os.path.exists(OUT) and open(OUT, 'rb').read() == data)
    if changed:
        open(OUT, 'wb').write(data)
    print('item masses:', len(DEFAULTS), 'defaults,', len(made), 'of hdfv,', 'written' if changed else 'unchanged')


if __name__ == '__main__':
    main()
