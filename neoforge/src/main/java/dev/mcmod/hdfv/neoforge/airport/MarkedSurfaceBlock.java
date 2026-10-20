package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.hdfv.airport.RunwayMarkings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Pavement (0.20): asphalt or concrete, painted on a 0.5 m grid (four cells a block: mask bit 0 the cell at the
 * smaller x and z, 1 the larger x, 2 the larger z, 3 both) in white or yellow, or painted as a runway threshold (white
 * where painted; facing into the runway: a tower finds runways by these, RunwayFinder). Not a mob spawn.
 * A threshold block registers itself with the level's AirfieldData when placed and goes when removed.
 */
public final class MarkedSurfaceBlock extends Block {
    public enum Paint implements StringRepresentable {
        NONE, WHITE, YELLOW, THRESHOLD;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }

        public static Paint of(RunwayMarkings.Paint p) {
            return switch (p) { case WHITE -> WHITE; case YELLOW -> YELLOW; case THRESHOLD -> THRESHOLD; };
        }
    }

    public static final EnumProperty<Paint> PAINT = EnumProperty.create("paint", Paint.class);
    public static final IntegerProperty MASK = IntegerProperty.create("mask", 0, 15);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MarkedSurfaceBlock(Properties p) {
        super(p.isValidSpawn((s, level, pos, entity) -> false));   // no mob spawns on pavement
        registerDefaultState(stateDefinition.any().setValue(PAINT, Paint.NONE).setValue(MASK, 0).setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(PAINT, MASK, FACING); }

    /** The state with a runway paint mark. */
    public BlockState marked(RunwayMarkings.Mark m) {
        Direction facing = m.dx() > 0 ? Direction.EAST : m.dx() < 0 ? Direction.WEST : m.dz() > 0 ? Direction.SOUTH : Direction.NORTH;
        return defaultBlockState().setValue(PAINT, Paint.of(m.paint())).setValue(MASK, m.mask()).setValue(FACING, facing);
    }


    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!(level instanceof ServerLevel server)) { return; }
        boolean was = old.is(this) && old.getValue(PAINT) == Paint.THRESHOLD, is = s.getValue(PAINT) == Paint.THRESHOLD;
        if (is) {
            Direction f = s.getValue(FACING);
            AirfieldData.of(server).addThreshold(pos, f.getStepX(), f.getStepZ());
        } else if (was) {
            AirfieldData.of(server).removeThreshold(pos);
        }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this) && level instanceof ServerLevel server && s.getValue(PAINT) == Paint.THRESHOLD) { AirfieldData.of(server).removeThreshold(pos); }
        super.onRemove(s, level, pos, now, moved);
    }
}
