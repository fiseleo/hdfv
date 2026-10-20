package dev.mcmod.hdfv.neoforge.airport;

import com.mojang.logging.LogUtils;
import dev.mcmod.hdfv.airport.AirportLayout;
import dev.mcmod.hdfv.airport.AirportPlanner;
import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.airport.RunwayMarkings;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
import dev.mcmod.hdfv.neoforge.power.BatteryBlockEntity;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * World-generated airports (0.20): each overworld chunk asks its region's plan (AirportWorldgen) and places its own
 * slice of the airport, so an airport of a kilometre and more is built a chunk at a time and every chunk agrees with the
 * others. Writes only its own 16 x 16 columns:
 * <ul>
 *   <li>levels the ground (cuts away to the sky, fills down to the old ground; an 8 m slope round the airport meets the
 *   land), paves it (asphalt with the paint, apron concrete) and turfs it (airfield turf: not dirt, so no tree takes
 *   root on it);</li>
 *   <li>places the light fittings, windsocks, signs, floodlights, the tower's blocks (unformed: it forms itself when it
 *   first ticks; none at a small airfield), what is in it (0.21, AirportLayout.towerInside: the batteries (charged) on
 *   cable ducts, floor tiles, lamps, the bed, the spiral stair and its column, the hatch's grating), the hangar's walls,
 *   door and roof, and the controller villager.</li>
 * </ul>
 * The airport's lights, runways and tower are registered with the level once, when the plan is made (AirportWorldgen).
 */
public final class AirportFeature extends Feature<NoneFeatureConfiguration> {
    private static final Logger LOGGER = LogUtils.getLogger();

    public AirportFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!HdfvConfig.airports() || level.getLevel().dimension() != Level.OVERWORLD) { return false; }
        if (context.chunkGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource && !HdfvConfig.airportsInFlatWorlds()) { return false; }
        ChunkPos chunk = new ChunkPos(context.origin());
        AirportPlanner.Plan p;
        try {
            p = AirportWorldgen.plan(level, context.chunkGenerator(), chunk);
        } catch (RuntimeException e) {
            LOGGER.error("Airport planning failed near chunk {}; no airport there", chunk, e);
            return false;
        }
        if (p == null) { return false; }
        AirportLayout.Bounds f = AirportLayout.footprint(p);
        if (!f.meets(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), chunk.getMaxBlockZ())) { return false; }
        try {
            slice(level, p, chunk);
        } catch (RuntimeException e) {
            LOGGER.error("Placing an airport's slice failed at chunk {}", chunk, e);
        }
        return true;
    }

    /** Whether a column is in a chunk (null: everywhere). */
    private static boolean in(ChunkPos c, int x, int z) { return c == null || x >> 4 == c.x && z >> 4 == c.z; }

    /** The tower and what is in it (no ground work, no runway): dev commands and GameTests. A small airfield has none. */
    public static void placeTower(WorldGenLevel level, AirportPlanner.Plan p) {
        AirportLayout.TowerSite t = AirportLayout.tower(p);
        if (t == null) { return; }
        tower(level, p, null);
        placeNear(level, p, t.x(), t.z(), 8);
    }

    /** The things (AirportLayout.things) within a square round a point, whole: GameTests. */
    public static void placeNear(WorldGenLevel level, AirportPlanner.Plan p, int x, int z, int r) {
        for (AirportLayout.Thing th : AirportLayout.things(p)) {
            if (Math.abs(th.x() - x) <= r && Math.abs(th.z() - z) <= r) { thing(level, th, null); }
        }
    }

    /** A light fitting's state: facing out along the runway toward its nearer end's approach (lenses toward the pilots). */
    static BlockState fixture(AirportPlanner.Plan p, Fixture fx) {
        dev.mcmod.hdfv.airport.Runway r = p.runway();
        boolean alongX = p.axis() == dev.mcmod.hdfv.airport.Runway.Axis.X;
        double along = alongX ? fx.x() + 0.5 : fx.z() + 0.5;
        boolean low = along < (r.low() + r.high()) / 2;
        Direction d = alongX ? (low ? Direction.WEST : Direction.EAST) : (low ? Direction.NORTH : Direction.SOUTH);
        return HdfvAirports.FIXTURES.get(fx.kind()).get().defaultBlockState().setValue(FixtureBlock.FACING, d);
    }

    /** Places a plan's part in a chunk (world generation; the dev command, a chunk a time). */
    public static void slice(WorldGenLevel level, AirportPlanner.Plan p, ChunkPos chunk) {
        Map<Long, RunwayMarkings.Mark> marks = AirportLayout.marks(p);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int y = p.surfaceY();
        // first how each column is to be levelled, and the trunks that will go (the rest of their trees is found while
        // what joins it to them is still there)
        int[] tops = new int[256], grounds = new int[256], targets = new int[256];
        java.util.Arrays.fill(targets, Integer.MIN_VALUE);
        List<BlockPos> trunks = new ArrayList<>();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                AirportLayout.Ground g = AirportLayout.ground(p, x, z);
                if (g == AirportLayout.Ground.NONE && AirportLayout.distanceToLevelled(p, x, z) >= AirportLayout.SLOPE_M) { continue; }
                // WORLD_SURFACE: the feature step keeps it up to date (the _WG one is the ground before the trees: clearing up to it left trees floating)
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                // under the levelled ground a pond or a river is filled from its bed; on the slopes water is left alone
                int ground = naturalGround(level, at, x, z, top, g != AirportLayout.Ground.NONE);
                if (g == AirportLayout.Ground.NONE && !level.getFluidState(at.set(x, ground - 1, z)).isEmpty()) { continue; }
                int i = (x & 15) << 4 | (z & 15);
                tops[i] = top;
                grounds[i] = ground;
                targets[i] = g == AirportLayout.Ground.NONE ? AirportLayout.gradedY(p, x, z, ground) : y;
                for (int k = targets[i]; k <= top; k++) {
                    if (level.getBlockState(at.set(x, k, z)).is(BlockTags.LOGS)) { trunks.add(at.immutable()); }
                }
            }
        }
        Map<Long, Integer> canopies = canopies(level, chunk, trunks);
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                int i = (x & 15) << 4 | (z & 15), target = targets[i], top = tops[i], ground = grounds[i];
                if (target == Integer.MIN_VALUE) { continue; }
                // clear: the column above the new surface, trees and all
                for (int k = target; k < Math.max(top, target) + 1; k++) { set(level, at.set(x, k, z), Blocks.AIR.defaultBlockState()); }
                // fill: from the old ground up to just under the surface
                for (int k = Math.min(ground, target - 1) - 1; k < target - 1; k++) {
                    set(level, at.set(x, k, z), k < target - 3 ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                }
                set(level, at.set(x, target - 1, z), surface(AirportLayout.ground(p, x, z), marks.get(RunwayMarkings.key(x, z))));
            }
        }
        dropUnheld(level, chunk, canopies);
        for (Fixture fx : AirportLayout.fixtures(p)) {
            if (!in(chunk, fx.x(), fx.z())) { continue; }
            set(level, at.set(fx.x(), fx.y(), fx.z()), fixture(p, fx));
        }
        hangar(level, p, chunk, at);
        tower(level, p, chunk);
        for (AirportLayout.Thing t : AirportLayout.things(p)) {
            // a bed's head may be in the next chunk: each chunk places its own half
            if (!in(chunk, t.x(), t.z()) && t.kind() != AirportLayout.ThingKind.BED) { continue; }
            thing(level, t, chunk);
        }
    }

    /** How far (steps through leaves) a trunk holds leaves up, as leaf decay counts. */
    private static final int LEAF_REACH = 6;

    /**
     * What of the trees whose trunks will be taken away is left round them (found before the columns are cleared: what
     * joins a trunk to the rest of its tree is in them): their logs (through logs, diagonals too: an acacia's trunk
     * steps sideways) and the leaves within a leaf's reach of those. A tree's crown spills into the chunks round it: a
     * tree in the next chunk, generated after this one was levelled, left its crown floating over the runway when its
     * own chunk took the trunk. Looks only within the chunks next to this one (all that world generation lets it touch).
     */
    private static Map<Long, Integer> canopies(WorldGenLevel level, ChunkPos chunk, List<BlockPos> trunks) {
        Map<Long, Integer> near = new java.util.HashMap<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        for (BlockPos t : trunks) { near.put(t.asLong(), 0); queue.add(t); }
        while (!queue.isEmpty()) {
            BlockPos b = queue.poll();
            int steps = near.get(b.asLong());
            boolean log = steps == 0;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                if (axes == 0) { continue; }
                BlockPos n = b.offset(dx, dy, dz);
                if (!nearby(level, chunk, n) || near.containsKey(n.asLong())) { continue; }
                BlockState s = level.getBlockState(n);
                if (log && s.is(BlockTags.LOGS)) { near.put(n.asLong(), 0); queue.add(n); }
                else if (axes == 1 && steps < LEAF_REACH && wildLeaves(s)) { near.put(n.asLong(), steps + 1); queue.add(n); }
            }
        }
        for (BlockPos t : trunks) { near.remove(t.asLong()); }
        return near;
    }

    /**
     * Takes away what is left of those trees (after the columns were cleared) that no tree still rooted holds: logs not
     * joined to a log standing on the ground, leaves out of a leaf's reach of a rooted log (as leaf decay would judge;
     * world generation does not run it), and snow lying on them.
     */
    private static void dropUnheld(WorldGenLevel level, ChunkPos chunk, Map<Long, Integer> canopies) {
        List<BlockPos> left = new ArrayList<>();
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (long k : canopies.keySet()) {
            BlockPos b = BlockPos.of(k);
            BlockState s = level.getBlockState(b);
            if (!s.is(BlockTags.LOGS) && !wildLeaves(s)) { continue; }
            left.add(b);
            lo[0] = Math.min(lo[0], b.getX()); lo[1] = Math.min(lo[1], b.getY()); lo[2] = Math.min(lo[2], b.getZ());
            hi[0] = Math.max(hi[0], b.getX()); hi[1] = Math.max(hi[1], b.getY()); hi[2] = Math.max(hi[2], b.getZ());
        }
        if (left.isEmpty()) { return; }
        // the rooted logs: those standing on the ground, and the logs joined to them
        Map<Long, Integer> held = new java.util.HashMap<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = lo[0] - LEAF_REACH; x <= hi[0] + LEAF_REACH; x++) {
            for (int z = lo[2] - LEAF_REACH; z <= hi[2] + LEAF_REACH; z++) {
                for (int k = lo[1] - 32; k <= hi[1] + LEAF_REACH; k++) {   // down to the bases of tall trees beside
                    if (!nearby(level, chunk, at.set(x, k, z)) || !level.getBlockState(at).is(BlockTags.LOGS)) { continue; }
                    BlockState below = level.getBlockState(at.below());
                    if (below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES) || !below.blocksMotion()) { continue; }
                    BlockPos b = at.immutable();
                    held.put(b.asLong(), 0);
                    queue.add(b);
                }
            }
        }
        java.util.ArrayDeque<BlockPos> logs = new java.util.ArrayDeque<>(queue);
        while (!logs.isEmpty()) {
            BlockPos b = logs.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = b.offset(dx, dy, dz);
                if (!nearby(level, chunk, n) || held.containsKey(n.asLong()) || !level.getBlockState(n).is(BlockTags.LOGS)) { continue; }
                held.put(n.asLong(), 0);
                logs.add(n);
                queue.add(n);
            }
        }
        // and the leaves they hold, through leaves
        while (!queue.isEmpty()) {
            BlockPos b = queue.poll();
            int steps = held.get(b.asLong());
            if (steps >= LEAF_REACH) { continue; }
            for (Direction d : Direction.values()) {
                BlockPos n = b.relative(d);
                if (!nearby(level, chunk, n) || held.containsKey(n.asLong()) || !wildLeaves(level.getBlockState(n))) { continue; }
                held.put(n.asLong(), steps + 1);
                queue.add(n);
            }
        }
        for (BlockPos b : left) {
            if (held.containsKey(b.asLong())) { continue; }
            set(level, b, Blocks.AIR.defaultBlockState());
            if (level.getBlockState(b.above()).is(Blocks.SNOW)) { set(level, b.above(), Blocks.AIR.defaultBlockState()); }
        }
    }

    /** Whether a block is in a chunk next to (or in) the one being placed, and in the world's height. */
    private static boolean nearby(WorldGenLevel level, ChunkPos chunk, BlockPos b) {
        return Math.abs((b.getX() >> 4) - chunk.x) <= 1 && Math.abs((b.getZ() >> 4) - chunk.z) <= 1
                && b.getY() >= level.getMinBuildHeight() && b.getY() < level.getMaxBuildHeight();
    }

    /** Leaves a tree grew (not placed by a player). */
    private static boolean wildLeaves(BlockState s) {
        return s.is(BlockTags.LEAVES) && !(s.hasProperty(BlockStateProperties.PERSISTENT) && s.getValue(BlockStateProperties.PERSISTENT));
    }

    /** The old ground under a column: down from the top through trees, plants and snow, and through water too if asked (else water counts as ground). */
    private static int naturalGround(WorldGenLevel level, BlockPos.MutableBlockPos at, int x, int z, int top, boolean throughWater) {
        int k = top - 1;
        for (int i = 0; i < 48 && k > level.getMinBuildHeight(); i++, k--) {
            BlockState s = level.getBlockState(at.set(x, k, z));
            boolean water = !s.getFluidState().isEmpty();
            if (s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || (s.canBeReplaced() && !water) || (throughWater && water)) { continue; }
            break;
        }
        return k + 1;
    }

    private static BlockState surface(AirportLayout.Ground g, RunwayMarkings.Mark mark) {
        return switch (g) {
            case RUNWAY, TAXIWAY -> mark == null ? HdfvAirports.ASPHALT.get().defaultBlockState() : HdfvAirports.ASPHALT.get().marked(mark);
            case APRON, HANGAR, TOWER -> mark == null ? HdfvAirports.CONCRETE.get().defaultBlockState() : HdfvAirports.CONCRETE.get().marked(mark);
            case TURF -> HdfvAirports.TURF.get().defaultBlockState();
            case NONE -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState s) { level.setBlock(pos, s, Block.UPDATE_CLIENTS); }

    private static Direction facing(int index) { return Direction.from2DDataValue(index); }

    private static void hangar(WorldGenLevel level, AirportPlanner.Plan p, ChunkPos chunk, BlockPos.MutableBlockPos at) {
        AirportLayout.HangarSite h = AirportLayout.hangar(p);
        int y = p.surfaceY();
        BlockState wall = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), roof = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState door = dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly.DOORS.get("hangar_door").get().defaultBlockState();
        for (int x = h.minX(); x <= h.maxX(); x++) {
            for (int z = h.minZ(); z <= h.maxZ(); z++) {
                if (!in(chunk, x, z)) { continue; }
                boolean edge = x == h.minX() || x == h.maxX() || z == h.minZ() || z == h.maxZ();
                // the door side: the one facing the apron (its outward normal is the door's direction)
                boolean doorSide = h.doorDz() < 0 && z == h.minZ() || h.doorDz() > 0 && z == h.maxZ() || h.doorDx() < 0 && x == h.minX() || h.doorDx() > 0 && x == h.maxX();
                boolean corner = (x == h.minX() || x == h.maxX()) && (z == h.minZ() || z == h.maxZ());
                for (int k = 0; k < h.height(); k++) {
                    BlockState s = !edge ? Blocks.AIR.defaultBlockState() : doorSide && !corner && k < h.height() - 2 ? door : wall;
                    set(level, at.set(x, y + k, z), s);
                }
                set(level, at.set(x, y + h.height(), z), roof);
            }
        }
    }

    private static void tower(WorldGenLevel level, AirportPlanner.Plan p, ChunkPos chunk) {
        AirportLayout.TowerSite t = AirportLayout.tower(p);
        if (t == null) { return; }   // a small airfield: no tower
        Direction facing = facing(t.facing());
        for (Pattern.Cell c : Structures.TOWER_PATTERN.cells(t.x(), t.y(), t.z(), t.facing(), t.courses())) {
            if (!in(chunk, c.x(), c.z())) { continue; }
            BlockPos at = new BlockPos(c.x(), c.y(), c.z());
            if (c.symbol() == '.') { set(level, at, Blocks.AIR.defaultBlockState()); continue; }
            BlockState s = HdfvMultiblocks.block(c.kind()).defaultBlockState();
            if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) { s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing); }
            set(level, at, s);
        }
    }

    /** Places a thing (only what of it is in a chunk; null: all of it). */
    private static void thing(WorldGenLevel level, AirportLayout.Thing t, ChunkPos chunk) {
        BlockPos pos = new BlockPos(t.x(), t.y(), t.z());
        Direction f = t.facing() == 4 ? Direction.DOWN : facing(t.facing());
        switch (t.kind()) {
            case WINDSOCK -> set(level, pos, HdfvAirports.WINDSOCK.get().defaultBlockState());
            case HOLD_SIGN -> set(level, pos, HdfvAirports.HOLD_SIGN.get().defaultBlockState().setValue(AirfieldBlocks.Sign.FACING, f));
            case FLOODLIGHT -> set(level, pos, HdfvAirports.FLOODLIGHT.get().defaultBlockState().setValue(AirfieldBlocks.Floodlight.FACING, f));
            case STAIR -> set(level, pos, HdfvAirports.STEEL_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, f)
                    .setValue(StairBlock.HALF, Half.BOTTOM).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT));
            case COLUMN -> set(level, pos, HdfvAirports.STEEL_COLUMN.get().defaultBlockState());
            case GRATING -> set(level, pos, HdfvAirports.STEEL_GRATING.get().defaultBlockState());
            case LAMP -> set(level, pos, HdfvAirports.INTERIOR_LAMP.get().defaultBlockState().setValue(AirfieldBlocks.InteriorLamp.FACING, f));
            case DUCT -> set(level, pos, HdfvAirports.CABLE_DUCT.get().defaultBlockState());
            case FLOOR -> set(level, pos, HdfvAirports.TOWER_FLOOR.get().defaultBlockState());
            case BED -> {
                BlockPos head = pos.relative(f);
                if (in(chunk, pos.getX(), pos.getZ())) { set(level, pos, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, f).setValue(BedBlock.PART, BedPart.FOOT)); }
                if (in(chunk, head.getX(), head.getZ())) { set(level, head, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, f).setValue(BedBlock.PART, BedPart.HEAD)); }
            }
            case BATTERY -> {
                set(level, pos, HdfvPower.POWER.get(PowerBlock.Kind.BATTERY).get().defaultBlockState());
                if (level.getBlockEntity(pos) instanceof BatteryBlockEntity b) { b.setStoredJ(Double.MAX_VALUE); }
            }
            case VILLAGER -> {
                Villager v = EntityType.VILLAGER.create(level.getLevel());
                if (v == null) { return; }
                v.moveTo(t.x() + 0.5, t.y(), t.z() + 0.5, 0, 0);
                v.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
                v.setVillagerData(v.getVillagerData().setProfession(dev.mcmod.hdfv.neoforge.villager.HdfvVillagers.TOWER_CONTROLLER.get()).setLevel(2));
                v.setVillagerXp(10);
                v.setPersistenceRequired();
                level.addFreshEntityWithPassengers(v);
            }
        }
    }
}
