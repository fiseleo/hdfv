package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.earthmoving.Asphalt;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 0.26: hot mix as a paver lays it. It cools in HEAT steps from laying (7) to the cessation temperature (0), on the time
 * a 50 mm mat laid at 150 C takes in still 15 C air on a 15 C base (api.earthmoving.Asphalt.secondsToK) at the
 * server's work hours (HdfvConfig.workSecondsPerTick). A roller's pass adds to PASSES (WorldWork); rolled PASSES_NEEDED
 * times while still above cessation it is finished asphalt pavement, and cold before that it crumbles to reclaimed
 * asphalt (un-compacted mix is not a road: a rule).
 */
public final class HotAsphaltBlock extends Block {
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 7), PASSES = IntegerProperty.create("passes", 0, 4);
    /** Roller passes to finish a mat (Caterpillar's 3 to 5 for a breakdown and intermediate roller on a 50 mm lift: a rule). */
    public static final int PASSES_NEEDED = 4;

    public HotAsphaltBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(HEAT, 7).setValue(PASSES, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(HEAT, PASSES); }

    /** Ticks between heat steps at this server's pace. */
    public static int stepTicks() {
        double seconds = Asphalt.secondsToK(0.05, Asphalt.LAYING_K, 288.15, 288.15, 0, 0, Asphalt.CESSATION_K);
        return (int) Math.max(1, Math.round(seconds / 7 / HdfvConfig.workSecondsPerTick()));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && old.getBlock() != this) { level.scheduleTick(pos, this, stepTicks()); }
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int heat = state.getValue(HEAT) - 1;
        if (heat <= 0) {
            level.setBlock(pos, (state.getValue(PASSES) >= PASSES_NEEDED ? dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.ASPHALT.get()
                    : HdfvWorksite.RECLAIMED_ASPHALT.get()).defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        level.setBlock(pos, state.setValue(HEAT, heat), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, stepTicks());
    }

    /** A roller's pass over it (WorldWork): finished once rolled enough while hot. */
    public static void rolled(ServerLevel level, BlockPos pos, BlockState state) {
        int passes = Math.min(state.getValue(PASSES) + 1, 4);
        if (passes >= PASSES_NEEDED) {
            level.setBlock(pos, dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.ASPHALT.get().defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, state.setValue(PASSES, passes), Block.UPDATE_CLIENTS);
        }
    }
}
