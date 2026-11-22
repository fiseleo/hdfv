package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.earthmoving.Concrete;
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
 * 0.26: concrete as a pump or a chute places it. It sets by its maturity (api.earthmoving.Concrete, Nurse-Saul at the
 * biome's temperature as 15 C here: a rule), in AGE steps to the final set (7), at the server's work hours
 * (HdfvConfig.workSecondsPerTick), then is concrete pavement (the airfields' apron concrete). Until its initial set
 * (AGE 5 of 7) it can still be worked: a screed or a vibrator plate passing over does nothing harmful, a block placed on
 * it sinks (vanilla's own rules for a soft block).
 */
public final class FreshConcreteBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 7);
    /** The curing temperature taken, K (15 C). */
    public static final double CURING_K = 288.15;

    public FreshConcreteBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AGE); }

    /** Ticks between ageing steps at this server's pace. */
    public static int stepTicks() {
        double seconds = Concrete.secondsTo(Concrete.FINAL_SET_KS, CURING_K);
        return (int) Math.max(1, Math.round(seconds / 7 / HdfvConfig.workSecondsPerTick()));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && old.getBlock() != this) { level.scheduleTick(pos, this, stepTicks()); }
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE) + 1;
        if (age >= 7) {
            level.setBlock(pos, dev.mcmod.hdfv.neoforge.airport.HdfvAirports.CONCRETE.get().defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        level.setBlock(pos, state.setValue(AGE, age), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, stepTicks());
    }
}
