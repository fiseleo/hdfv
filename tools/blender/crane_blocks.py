"""Models hdfv 0.26's tower crane blocks the way machines.py models the single-block machines, with its conventions
(block pixels, at most 800 faces) and its exporter: OBJ + MTL into assets/hdfv/models/block/machine, the block models
(replacing machinery_assets.py's cubes) and their textures. Shapes laid out in Blender with MCP for Blender against the
crane's cab vehicle (MachineLooks.towerCrane):
- tower_crane_mast, a standard section: four square-tube legs at the corners with a flange at their foot, a ring of
  braces near the bottom of each face and one diagonal a face, the four winding round the mast (stacked, the sections
  read as one lattice tower);
- tower_crane_top, the slewing unit's fixed half: the same legs and lacing up to a head plate, the slewing ring's lower
  race on it (the turning half, with the jibs and cab, is the cab vehicle's).
Run: scripts/blender.ps1 crane_blocks"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
import machines as mc  # noqa: E402
from machines import Model  # noqa: E402

LEGS = ((0.5, 0.5), (13.5, 0.5), (13.5, 13.5), (0.5, 13.5))   # each leg's corner nearest the origin (2 px square tubes)


def lattice(m, top):
    """The legs, their flanges, the bottom braces and a diagonal on each face, winding round (north, east, south, west)."""
    for x, z in LEGS:
        m.box((x, 0, z), (x + 2, top, z + 2), 'paint_yellow')
        m.box((x - 0.25, 0, z - 0.25), (x + 2.25, 0.8, z + 2.25), 'paint_yellow')
    # braces between the legs near the bottom, set into each face
    m.box((2.5, 0.8, 0.8), (13.5, 1.8, 1.8), 'paint_yellow')
    m.box((2.5, 0.8, 14.2), (13.5, 1.8, 15.2), 'paint_yellow')
    m.box((0.8, 0.8, 2.5), (1.8, 1.8, 13.5), 'paint_yellow')
    m.box((14.2, 0.8, 2.5), (15.2, 1.8, 13.5), 'paint_yellow')
    y0, y1 = 1.8, top - 0.4
    m.tube((2.5, y0, 1.3), (13.5, y1, 1.3), 0.55, 6, 'paint_yellow')       # north: west to east, rising
    m.tube((14.7, y0, 2.5), (14.7, y1, 13.5), 0.55, 6, 'paint_yellow')     # east: north to south
    m.tube((13.5, y0, 14.7), (2.5, y1, 14.7), 0.55, 6, 'paint_yellow')     # south: east to west
    m.tube((1.3, y0, 13.5), (1.3, y1, 2.5), 0.55, 6, 'paint_yellow')       # west: south to north


def mast():
    m = Model()
    lattice(m, 16)
    return m


def top():
    m = Model()
    lattice(m, 10.5)
    m.box((0, 10.5, 0), (16, 13, 16), 'paint_yellow')
    m.vcyl(8, 8, 13, 15.6, 7.6, 7.6, 24, 'dark_steel')
    m.vcyl(8, 8, 15.6, 16, 7.0, 7.0, 24, 'steel')
    return m


BLOCKS = {'tower_crane_mast': mast, 'tower_crane_top': top}


def main():
    common.reset()
    os.makedirs(mc.OUT, exist_ok=True)
    written, used = 0, set()
    for name, build in BLOCKS.items():
        w, mats = mc.export_machine(name, build())
        written += w
        used |= mats
    for mat in sorted(used):
        written += common.write_if_changed(os.path.join(mc.TEXTURES, mat + '.png'), mc.png(mc.TEXTURE_RECIPES[mat]()))
    common.log('crane blocks done:', len(BLOCKS), 'blocks,', len(used), 'textures,', written, 'files written')


main()
