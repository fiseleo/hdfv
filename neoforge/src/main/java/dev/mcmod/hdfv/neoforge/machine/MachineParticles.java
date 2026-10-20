package dev.mcmod.hdfv.neoforge.machine;

import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.HdfvParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * What working machines give off (0.12, M4), from their blocks' animateTick on the client (a few calls a second for a
 * block in view): a foundry's flue smokes and its mouth spits a spark now and then; the welding station showers sparks
 * from the torch under a little smoke; the forging press lets off steam at its cylinder and strikes sparks at the die;
 * the autoclave and the tyre press vent steam; the alloy furnace, lit, smokes from its stack and flames at its mouth.
 * Places in the block's pixels as tools/blender/machines.py models them, turned with the block's facing. Display only.
 */
public final class MachineParticles {
    private MachineParticles() {}

    private static final int SMOKE = 0x90585858, STEAM = 0x70f0f0f0, SPARK = 0xffffc070, ARC_SPARK = 0xffeef4ff;

    /** A machine of a station at work. */
    public static void working(Level level, BlockPos pos, Station station, Direction facing, RandomSource r) {
        switch (station) {
            case FOUNDRY -> {
                puff(level, pos, facing, 12, 16.2, 12, 0, 0.03, 0, HdfvParticles.SMOKE.get(), 0.3f, SMOKE, 3f);
                if (r.nextFloat() < 0.3f) { spark(level, pos, facing, 8, 6.5, 0.3, 0.06, r, SPARK); }
            }
            case WELDING_STATION -> {
                int n = 2 + r.nextInt(4);
                for (int i = 0; i < n; i++) { spark(level, pos, facing, 10, 10.3, 2.2, 0.09, r, r.nextBoolean() ? ARC_SPARK : SPARK); }
                if (r.nextFloat() < 0.4f) { puff(level, pos, facing, 10, 10.6, 2.4, 0, 0.02, 0, HdfvParticles.SMOKE.get(), 0.12f, 0x60a8a8a8, 1.5f); }
            }
            case FORGING_PRESS -> {
                if (r.nextFloat() < 0.3f) { puff(level, pos, facing, 8, 16.2, 6, 0, 0.02, 0, HdfvParticles.SMOKE.get(), 0.25f, STEAM, 1.5f); }
                if (r.nextFloat() < 0.25f) { spark(level, pos, facing, 8, 6.4, 4, 0.07, r, SPARK); }
            }
            case COMPOSITE_OVEN -> {
                if (r.nextFloat() < 0.35f) { puff(level, pos, facing, 8, 16.2, 3, 0, 0.025, 0, HdfvParticles.SMOKE.get(), 0.2f, STEAM, 1.8f); }
            }
            case VULCANIZER -> {
                double a = r.nextDouble() * 2 * Math.PI;
                puff(level, pos, facing, 8 + 5.6 * Math.cos(a), 7.5, 8 + 5.6 * Math.sin(a), 0.01 * Math.cos(a), 0.02, 0.01 * Math.sin(a),
                        HdfvParticles.SMOKE.get(), 0.18f, STEAM, 1.4f);
            }
            default -> { }
        }
    }

    /** The alloy furnace, lit. */
    public static void furnace(Level level, BlockPos pos, Direction facing, RandomSource r) {
        puff(level, pos, facing, 8, 16.2, 9, 0, 0.035, 0, HdfvParticles.SMOKE.get(), 0.3f, SMOKE, 3f);
        if (r.nextFloat() < 0.5f) {
            double[] at = place(pos, facing, 5.5 + 5 * r.nextDouble(), 2.2, 0.4);
            level.addParticle(ParticleTypes.SMALL_FLAME, at[0], at[1], at[2], 0, 0.004, 0);
        }
    }

    private static void puff(Level level, BlockPos pos, Direction facing, double x, double y, double z, double vx, double vy, double vz,
                             net.minecraft.core.particles.ParticleType<HdfvParticles.Options> type, float size, int colour, float life) {
        double[] at = place(pos, facing, x, y, z), v = turn(facing, vx, vz);
        add(level, new HdfvParticles.Options(type, size, colour, life), at, v[0], vy, v[1]);
    }

    private static void spark(Level level, BlockPos pos, Direction facing, double x, double y, double z, double speed, RandomSource r, int colour) {
        double[] at = place(pos, facing, x, y, z);
        double a = r.nextDouble() * 2 * Math.PI;
        add(level, new HdfvParticles.Options(HdfvParticles.SPARK.get(), 0.03f + 0.02f * r.nextFloat(), colour, 0.4f + 0.4f * r.nextFloat()), at,
                speed * Math.cos(a), speed * (0.5 + r.nextDouble()), speed * Math.sin(a));
    }

    private static void add(Level level, ParticleOptions o, double[] at, double vx, double vy, double vz) { level.addParticle(o, at[0], at[1], at[2], vx, vy, vz); }

    /** A point in the block's pixels (as modelled facing north) in the world, the block turned to its facing. */
    static double[] place(BlockPos pos, Direction facing, double x, double y, double z) {
        double ox = x / 16 - 0.5, oz = z / 16 - 0.5;
        double[] v = turn(facing, ox, oz);
        return new double[]{pos.getX() + 0.5 + v[0], pos.getY() + y / 16, pos.getZ() + 0.5 + v[1]};
    }

    /** A horizontal vector (x, z) of the north-facing model turned to a facing (clockwise from above, as the blockstates turn it). */
    static double[] turn(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[]{-z, x};
            case SOUTH -> new double[]{-x, -z};
            case WEST -> new double[]{z, -x};
            default -> new double[]{x, z};
        };
    }
}
