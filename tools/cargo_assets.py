"""hdfv 0.25 (vehicle cargo): the assets and data of the cargo loader (neoforge storage/HdfvCargo) and the names of
everything vehicle cargo brings (the cargo screens, the status screen's tab, the storage box part in the design screen,
the body's hold figures), written from one spec: the loader's blockstate (its front turned to its facing), cube model
(minecraft:block/orientable), item model, loot table, pickaxe tag, the 16 px sources of its textures (into
tools/blender/blocks16, redrawn at 128 px by `scripts/blender.ps1 machines`) and both languages (new keys inserted
after their anchor, the files' line order kept alike). Files are rewritten only when they change. The recipe is
tools/recipes.py's, the mass tools/item_mass.py's. Checked by CargoAssetsTest.

    py -3 tools/cargo_assets.py
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/blender.ps1 machines     (the 128 px textures)
    py -3 tools/zh_cn.py                                                                 (the Simplified Chinese)
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import storage_assets as sa  # noqa: E402  (its writers, lang insertion and 16 px helpers)

from PIL import Image  # noqa: E402

ID = 'cargo_loader'

JEI_EN = ('Placed beside where vehicles park, its front toward them. On every side it is the hold of the vehicle standing still in '
          'front of it: of the holds within 4 m of its front face, the one whose middle is nearest. Hoppers, pipes and the rooms fill '
          'or empty it as they do a chest; when the vehicle drives off there is nothing to fill. Used by hand, it says which hold it serves.')
JEI_ZH = ('放在車輛停靠處旁，正面朝向車輛。它的每一面都等於停在前方、靜止不動的那台車的貨艙：在它正面 4 m 內的貨艙中，中心最近的那一個。'
          '漏斗、管線和各種廠房可以像用箱子一樣放入、取出；車開走就沒有東西可裝。空手使用會顯示它正在服務哪個貨艙。')


def lang_groups():
    """(anchor key, [(key, en, zh)]) in the order they go in."""
    block = [('block.hdfv.' + ID, 'Cargo Loader', '貨物裝卸機')]
    jei = [('hdfv.jei.' + ID, JEI_EN, JEI_ZH)]
    cargo = [
        ('hdfv.cargo.title', 'Cargo: %s', '貨物：%s'),
        ('hdfv.cargo.unavailable', "This vehicle's holds cannot be used now", '這台車的貨艙現在不能使用'),
        ('hdfv.cargo.no_hold', 'There is no such hold', '沒有這個貨艙'),
        ('hdfv.cargo.too_far', 'Too far from the hold', '離貨艙太遠'),
        ('hdfv.cargo.tab.none', 'No holds: give a body module a hold volume, or fit a storage box', '沒有貨艙：給車體模組貨艙容積，或裝上儲物箱'),
        ('hdfv.cargo.tab.slots', '%s/%s slots', '%s/%s 格'),
        ('hdfv.cargo.tab.slots_unknown', '%s slots', '%s 格'),
        ('hdfv.cargo.tab.mass', '%s kg', '%s kg'),
        ('hdfv.cargo.tab.mass_limit', '%s / %s kg', '%s / %s kg'),
        ('hdfv.cargo.tab.open', 'Open', '打開'),
        ('hdfv.cargo_loader.none', 'No vehicle is standing at the loader', '裝卸機前沒有停著的車輛'),
        ('hdfv.cargo_loader.serving', '%s: %s, %s/%s slots, %s kg', '%s：%s，%s/%s 格，%s kg'),
    ]
    tab = [('hdfv.status.tab.cargo', 'Cargo', '貨物')]
    dash = [('hdfv.drive.cargo', 'Cargo %s kg', '貨物 %s kg')]
    category = [('hdfv.category.storage', 'Storage', '儲物')]
    kind = [('hdfv.kind.storage_box', 'Storage box', '儲物箱')]
    samples = [
        ('hdfv.sample.roof_box', 'Roof box (450 L)', '車頂行李箱（450 L）'),
        ('hdfv.sample.bed_box', 'Pickup bed box', '貨卡車斗工具箱'),
        ('hdfv.sample.container_20ft', '20 ft container', '20 呎貨櫃'),
    ]
    params = [
        ('hdfv.param.design.boxKind', 'Kind of box', '箱型'),
        ('hdfv.param.design.paintColor', "Colour (blank: the kind's)", '顏色（空白＝箱型預設）'),
    ]
    choices = [
        ('hdfv.choice.roof_box', 'Roof box', '車頂箱'),
        ('hdfv.choice.bed_box', 'Bed box', '車斗箱'),
        ('hdfv.choice.container', 'Container', '貨櫃'),
    ]
    figures = [
        ('hdfv.figure.holdVolume', 'Hold volume', '貨艙容積'),
        ('hdfv.figure.holdSlots', 'Hold slots', '貨艙格數'),
        ('hdfv.figure.designPayload', 'Design payload (holds full)', '設計載重（貨艙全滿）'),
    ]
    rated = [('hdfv.figure.ratedLoad', 'Rated load', '額定載重')]
    derived = [('hdfv.derived.roofLoadKg', 'Box mass on the roof', '車頂上的箱子重量')]
    # the storage box's pieces (manufacture PartPlanner.storageBox, StructurePlanner.container)
    pieces = [
        ('hdfv.workpiece.roof_box_shell', 'Roof box shell half', '車頂箱殼半片'),
        ('hdfv.workpiece.bed_box_body', 'Bed box body', '車斗箱箱體'),
        ('hdfv.workpiece.bed_box_lid', 'Bed box lid', '車斗箱箱蓋'),
        ('hdfv.workpiece.storage_box_fittings', 'Storage box fittings (hinges, latches, clamps)', '儲物箱五金（鉸鏈、扣鎖、夾具）'),
        ('hdfv.workpiece.container_corner', 'Container corner casting', '貨櫃角件'),
        ('hdfv.workpiece.container_side_rail', 'Container side rail', '貨櫃側樑'),
        ('hdfv.workpiece.container_end_rail', 'Container end rail', '貨櫃端樑'),
        ('hdfv.workpiece.container_post', 'Container corner post', '貨櫃角柱'),
        ('hdfv.workpiece.container_cross_member', 'Container floor cross member', '貨櫃底橫樑'),
        ('hdfv.workpiece.container_walls', 'Container walls and roof (corrugated)', '貨櫃壁板與頂板（波紋板）'),
        ('hdfv.workpiece.container_floor', 'Container floor (plywood)', '貨櫃地板（合板）'),
    ]
    return [('block.hdfv.shipping_container', block), ('hdfv.jei.shipping_container', jei), ('hdfv.storage.mass.stack', cargo),
            ('hdfv.status.tab.repair', tab), ('hdfv.drive.shift_down', dash), ('hdfv.category.land', category), ('hdfv.kind.fuel_tank', kind),
            ('hdfv.sample.liquid_hydrogen_tank', samples), ('hdfv.param.design.fill', params), ('hdfv.choice.cryogenic', choices),
            ('hdfv.figure.loadedMass', figures), ('hdfv.figure.capacity', rated), ('hdfv.derived.meanPistonSpeedAtRevLimit', derived), ('hdfv.workpiece.fuel_tank_shell', pieces)]


# ------------------------------------------------------------------------------------------------ the 16 px sources

STEEL, STEEL_DARK, STEEL_LIGHT, CHUTE = sa.rgb('#5f6a73'), sa.rgb('#3f474e'), sa.rgb('#7f8b95'), sa.rgb('#202326')
YELLOW, BLACK, ROLLER, MOTOR = sa.rgb('#e2b229'), sa.rgb('#26282b'), sa.rgb('#a9b0b6'), sa.rgb('#3d5f87')


def front(seed):
    """The chute: a dark opening framed in steel, rollers across its floor, a hazard-striped lip."""
    im = sa.canvas(STEEL, seed, 0.03)
    px = im.load()
    sa.frame(px, STEEL_DARK)
    sa.rect(px, 2, 2, 13, 10, CHUTE)
    for y in (5, 8):
        sa.rect(px, 3, y, 12, y, sa.shade(CHUTE, 1.25))
    for x in range(3, 13, 3):
        sa.rect(px, x, 9, x + 1, 10, ROLLER)
    for x in range(1, 15):
        px[x, 12] = YELLOW if (x // 2) % 2 == 0 else BLACK
        px[x, 13] = BLACK if (x // 2) % 2 == 0 else YELLOW
    for x in range(1, 15):
        px[x, 1] = STEEL_LIGHT
    return im


def side(seed):
    """A steel panel with the conveyor's motor housing and its vents."""
    im = sa.canvas(STEEL, seed, 0.03)
    px = im.load()
    sa.frame(px, STEEL_DARK)
    sa.rect(px, 3, 4, 9, 11, MOTOR)
    sa.frame(px, sa.shade(MOTOR, 0.7), 3, 4, 9, 11)
    for y in (6, 8, 10):
        sa.rect(px, 4, y, 8, y, sa.shade(MOTOR, 0.75))
    sa.rect(px, 11, 4, 12, 11, STEEL_DARK)
    px[11, 6] = px[12, 6] = sa.rgb('#4caf50')
    px[11, 8] = px[12, 8] = sa.rgb('#c94a3a')
    for x in range(1, 15):
        px[x, 1] = STEEL_LIGHT
    return im


def top(seed):
    """The hopper mouth: a grating over the funnel."""
    im = sa.canvas(STEEL, seed, 0.03)
    px = im.load()
    sa.frame(px, STEEL_DARK)
    sa.rect(px, 3, 3, 12, 12, CHUTE)
    for i in range(3, 13, 3):
        sa.rect(px, i, 3, i, 12, ROLLER)
        sa.rect(px, 3, i, 12, i, ROLLER)
    return im


def textures():
    out = {ID + '_front': front(741), ID + '_side': side(742), ID + '_top': top(743)}
    for name, im in out.items():
        sa.write_png(os.path.join(sa.BLOCKS16, name + '.png'), im)
    return len(out)


def main():
    blockstates, models, items = os.path.join(sa.ASSETS, 'blockstates'), os.path.join(sa.ASSETS, 'models', 'block'), os.path.join(sa.ASSETS, 'models', 'item')
    sa.write_json(os.path.join(models, ID + '.json'), {'parent': 'minecraft:block/orientable', 'textures': {
        'top': 'hdfv:block/%s_top' % ID, 'front': 'hdfv:block/%s_front' % ID, 'side': 'hdfv:block/%s_side' % ID}})
    variants = {}
    for facing, y in sa.FACINGS:
        m = {'model': 'hdfv:block/' + ID}
        if y:
            m['y'] = y
        variants['facing=' + facing] = m
    sa.write_json(os.path.join(blockstates, ID + '.json'), {'variants': variants})
    sa.write_json(os.path.join(items, ID + '.json'), {'parent': 'hdfv:block/' + ID})
    sa.write_json(os.path.join(sa.DATA, 'hdfv', 'loot_table', 'blocks', ID + '.json'), {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'hdfv:' + ID}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    sa.add_tag(os.path.join(sa.DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), ['hdfv:' + ID])
    n = textures()
    sa.lang_groups = lang_groups   # the same insertion, these keys
    sa.insert_lang(os.path.join(sa.ASSETS, 'lang', 'en_us.json'), 'en')
    sa.insert_lang(os.path.join(sa.ASSETS, 'lang', 'zh_tw.json'), 'zh')
    print('cargo assets: 1 block,', n, '16 px sources,', sa.written, 'files written')


if __name__ == '__main__':
    main()
