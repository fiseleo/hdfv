package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.earthmoving.Material;
import dev.mcmod.aerocore.api.ground.Terrain;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * 0.26: what each block is to a machine's tools (AeroCore's WorldTerrain asks; the rule is hdfv's). A block is ground
 * by its tags, hdfv's own (data/hdfv/tags/block/earth/*.json, which packs and other mods may extend) first, then
 * vanilla's and the common ones:
 * <ul>
 * <li>soil (dirt, grass, podzol, mycelium, moss, farmland, paths): loam; sand: dry sand; gravel; clay: stiff clay;
 *     mud: mud; snow (blocks and layers, powder snow): snow;</li>
 * <li>soft rock (sandstone, terracotta, netherrack, calcite, tuff, dripstone): 40 MPa; hard rock (stone, deepslate,
 *     granite, diorite, andesite, basalt, blackstone): 150 MPa, too strong for a bucket or a blade to cut intact (they
 *     stall: a ripper, a hammer, a mining shovel's teeth on broken rock, or a cutter drum takes it; earthmoving's
 *     choice); broken rock (cobblestone, cobbled deepslate, hdfv's blasted rock): rockfill a bucket digs; ores: ore;</li>
 * <li>hdfv's loose fill: loose loam; compacted base: compacted fill; asphalt and concrete pavement: asphalt, concrete.</li>
 * </ul>
 * Never dug (a wall to a tool): blocks with a block entity (chests, machines), bedrock and the other unbreakable
 * blocks, and the tag hdfv:earth/undiggable; anything else that is not ground is nothing to a tool (plants, wood,
 * glass: the machine's body meets them). A block partly dug (DigProgress) is that much less of a cell.
 */
public final class EarthMaterials {
    private EarthMaterials() {}

    public static TagKey<Block> tag(String path) { return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "earth/" + path)); }

    public static final TagKey<Block> SOIL = tag("soil"), SAND = tag("sand"), GRAVEL = tag("gravel"), CLAY = tag("clay"), MUD = tag("mud"),
            SNOW = tag("snow"), SOFT_ROCK = tag("soft_rock"), HARD_ROCK = tag("hard_rock"), BROKEN_ROCK = tag("broken_rock"), ORE = tag("ore"),
            UNDIGGABLE = tag("undiggable");

    /** The material of a block as ground, or null when it is not ground. */
    public static Material material(BlockState s) {
        Block b = s.getBlock();
        if (b == HdfvWorksite.LOOSE_FILL.get()) { return Material.LOOSE_FILL; }
        if (b == HdfvWorksite.COMPACTED_BASE.get()) { return Material.COMPACTED_FILL; }
        if (b == HdfvWorksite.BLASTED_ROCK.get()) { return Material.BLASTED_ROCK; }
        if (b == HdfvWorksite.HOT_ASPHALT.get() || b == dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.ASPHALT.get()) { return Material.ASPHALT; }
        if (b == dev.mcmod.hdfv.neoforge.airport.HdfvAirports.CONCRETE.get() || b == HdfvWorksite.FRESH_CONCRETE.get()) { return Material.CONCRETE; }
        if (s.is(SOIL) || s.is(BlockTags.DIRT) || b == Blocks.FARMLAND || b == Blocks.DIRT_PATH) { return Material.LOAM; }
        if (s.is(SAND) || s.is(BlockTags.SAND)) { return Material.DRY_SAND; }
        if (s.is(GRAVEL) || s.is(Tags.Blocks.GRAVELS)) { return Material.GRAVEL; }
        if (s.is(CLAY) || b == Blocks.CLAY) { return Material.STIFF_CLAY; }
        if (s.is(MUD) || b == Blocks.MUD || b == Blocks.MUDDY_MANGROVE_ROOTS) { return Material.MUD; }
        if (s.is(SNOW) || s.is(BlockTags.SNOW) || b == Blocks.SNOW || b == Blocks.POWDER_SNOW) { return Material.SNOW; }
        if (s.is(ORE) || s.is(Tags.Blocks.ORES)) { return Material.IRON_ORE; }
        if (s.is(BROKEN_ROCK) || s.is(Tags.Blocks.COBBLESTONES)) { return Material.BLASTED_ROCK; }
        if (s.is(SOFT_ROCK) || s.is(Tags.Blocks.SANDSTONE_BLOCKS) || s.is(BlockTags.TERRACOTTA) || s.is(Tags.Blocks.NETHERRACKS) || b == Blocks.CALCITE
                || b == Blocks.TUFF || b == Blocks.DRIPSTONE_BLOCK) { return Material.SOFT_ROCK; }
        if (s.is(HARD_ROCK) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Tags.Blocks.STONES) || b == Blocks.BASALT || b == Blocks.SMOOTH_BASALT
                || b == Blocks.BLACKSTONE) { return Material.HARD_ROCK; }
        return null;
    }

    /** Whether a tool must not dig the block at pos (a wall to it): unbreakable, a block entity, or tagged so. */
    public static boolean undiggable(Level level, BlockPos pos, BlockState s) {
        return s.is(UNDIGGABLE) || s.getDestroySpeed(level, pos) < 0 || s.hasBlockEntity();
    }

    /** AeroCore's resolver (WorldTerrain.register): the block's cell, partly dug as DigProgress says, or null. */
    public static Terrain.Cell resolve(Level level, BlockPos pos, BlockState s) {
        if (s.isAir() || s.liquid()) { return null; }
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if (undiggable(level, pos, s)) {
            // only solid blocks stand in a tool's way; what it could pass through (a torch, a flower) is not ground
            return s.isCollisionShapeFullBlock(level, pos) ? new Terrain.Cell(id, Material.HARD_ROCK, false, 1) : null;
        }
        Material m = material(s);
        if (m == null) { return null; }
        double left = 1;
        if (level instanceof net.minecraft.server.level.ServerLevel server) { left = 1 - DigProgress.of(server).dug(pos, s); }
        if (b(s) == Blocks.SNOW) { left = Math.min(left, s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) / 8.0); }
        return new Terrain.Cell(id, m, true, Math.clamp(left, 1e-3, 1));
    }

    private static Block b(BlockState s) { return s.getBlock(); }

    /**
     * What a block becomes when dug out and carried (the form it is poured back as): grass, paths, farmland and the
     * like are dirt, stone is cobblestone (deepslate cobbled deepslate), the rest itself (ore blocks stay ore).
     */
    public static Block dugForm(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL || b == Blocks.MYCELIUM || b == Blocks.DIRT_PATH || b == Blocks.FARMLAND || b == Blocks.ROOTED_DIRT
                || b == Blocks.MOSS_BLOCK) { return Blocks.DIRT; }
        if (b == Blocks.STONE) { return Blocks.COBBLESTONE; }
        if (b == Blocks.DEEPSLATE) { return Blocks.COBBLED_DEEPSLATE; }
        if (b == Blocks.POWDER_SNOW || b == Blocks.SNOW) { return Blocks.SNOW_BLOCK; }
        if (b == HdfvWorksite.HOT_ASPHALT.get() || b == dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.ASPHALT.get()) { return HdfvWorksite.RECLAIMED_ASPHALT.get(); }
        if (b == dev.mcmod.hdfv.neoforge.airport.HdfvAirports.CONCRETE.get() || b == HdfvWorksite.FRESH_CONCRETE.get()) { return HdfvWorksite.BLASTED_ROCK.get(); }
        if (b == HdfvWorksite.COMPACTED_BASE.get()) { return HdfvWorksite.LOOSE_FILL.get(); }
        return b;
    }

    /** What a ripper or a hammer leaves of a block it broke in place: soil loosened to fill, rock broken to blasted rock, or null (nothing to break up). */
    public static Block brokenForm(BlockState s) {
        Material m = material(s);
        if (m == null) { return null; }
        if (m == Material.HARD_ROCK || m == Material.SOFT_ROCK || m == Material.CONCRETE || m == Material.ASPHALT) { return HdfvWorksite.BLASTED_ROCK.get(); }
        if (m == Material.IRON_ORE) { return null; }   // the ore stays itself: a shovel digs it
        if (m == Material.LOAM || m == Material.STIFF_CLAY || m == Material.COMPACTED_FILL) { return HdfvWorksite.LOOSE_FILL.get(); }
        return null;
    }
}
