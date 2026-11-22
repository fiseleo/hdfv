package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.earthmoving.Material;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.26: loose material poured or pushed onto the ground, piled block by block. A block placed is a cubic metre of it as
 * it lies loose (bank volume x swell), so a heap of dug loam takes about a quarter more blocks than the cut it came from;
 * what is short of a whole block waits for more at that column. It lands on the first solid block under the point and
 * slides down to a lower neighbour while it would stand more than a block above it (no steeper than a block a block:
 * 45 degrees, about every material's angle of repose; a rule). What it is placed as: soil as hdfv's loose fill, broken
 * rock as blasted rock (cobblestone as itself), sand, gravel, snow, ores as themselves, hot mix as hot asphalt, concrete
 * as fresh concrete.
 */
public final class Piles {
    private Piles() {}

    /** How far down a pour looks for ground, blocks. */
    public static final int REACH = 24;
    /** Loose m^3 waiting short of a block, by column (transient). */
    private static final Map<Long, Double> PENDING = new HashMap<>();

    /** The cell over the first solid block under a point (where something poured there lands), or null within REACH. */
    public static BlockPos landing(ServerLevel level, Vec3d point) {
        BlockPos p = BlockPos.containing(point.x(), point.y(), point.z());
        for (int i = 0; i < REACH; i++, p = p.below()) {
            if (!level.isLoaded(p)) { return null; }
            BlockState s = level.getBlockState(p.below());
            if (!s.isAir() && !s.liquid() && s.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) { return p; }
        }
        return null;
    }

    /** What a carried form is placed as on the ground. */
    public static Block placedAs(String form) {
        Block b = WorldWork.block(form);
        if (b == Blocks.DIRT || b == Blocks.CLAY || b == Blocks.MUD || b == Blocks.COARSE_DIRT || b == HdfvWorksite.COMPACTED_BASE.get()) {
            return HdfvWorksite.LOOSE_FILL.get();
        }
        if (b == Blocks.AIR) { return HdfvWorksite.LOOSE_FILL.get(); }
        return b;
    }

    /** Puts a load down at a point: as many whole blocks as it makes, the rest waiting there; takes what it placed from the load. */
    public static void deposit(ServerLevel level, PartVehicle by, Vec3d point, BulkCargo.Load load) {
        if (load.isEmpty()) { return; }
        BlockPos land = landing(level, point);
        if (land == null) { return; }
        String form = load.main();
        Material m = BulkCargo.material(form);
        Block placed = placedAs(form);
        // one block holds a loose cubic metre; ores and pavement keep a block per bank cubic metre (they are not loose here)
        double perBlock = placed == WorldWork.block(form) && (m.isRock() && m != Material.BLASTED_ROCK) ? 1 : 1 / Math.max(m.swell(), 1e-3);
        if (placed == HdfvWorksite.LOOSE_FILL.get()) { perBlock = 1 / Math.max(m.swell(), 1e-3); }
        long column = BlockPos.asLong(land.getX(), 0, land.getZ());
        double pending = PENDING.getOrDefault(column, 0.0), have = pending + load.bankM3();
        int blocks = (int) Math.floor(have / perBlock + 1e-9), placedCount = 0;
        for (int i = 0; i < blocks && i < 64; i++) {
            BlockPos at = slide(level, landing(level, new Vec3d(land.getX() + 0.5, land.getY() + 8, land.getZ() + 0.5)));
            if (at == null || !WorldWork.may(level, by, at, Blocks.AIR.defaultBlockState())) { break; }
            level.setBlock(at, placed.defaultBlockState(), Block.UPDATE_ALL);
            placedCount++;
        }
        // what is left waits at the column, at most a few blocks of it (more stays in the load: the ground there is taken)
        double left = have - placedCount * perBlock, keep = Math.min(left, 4 * perBlock);
        double giveBack = left - keep;
        PENDING.put(column, Math.max(keep, 0));
        load.take(Math.max(load.bankM3() - giveBack, 0));
        if (PENDING.size() > 1 << 14) { PENDING.clear(); }
    }

    /** Where a block put at a cell ends up: down to the lowest neighbour while it would stand more than a block above it. */
    static BlockPos slide(ServerLevel level, BlockPos at) {
        if (at == null) { return null; }
        for (int step = 0; step < 6; step++) {
            BlockPos best = null;
            int lowest = at.getY() - 1;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n = landing(level, new Vec3d(at.getX() + d.getStepX() + 0.5, at.getY() + 0.5, at.getZ() + d.getStepZ() + 0.5));
                if (n != null && n.getY() < lowest) { lowest = n.getY(); best = n; }
            }
            if (best == null) { return at; }
            at = best;
        }
        return at;
    }
}
