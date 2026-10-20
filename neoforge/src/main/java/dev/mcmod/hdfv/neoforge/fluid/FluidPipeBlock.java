package dev.mcmod.hdfv.neoforge.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.HashMap;
import java.util.Map;

/**
 * A metre of DN50 steel pipe (0.8): joins the pipes and the fluid handlers (tanks, pumps, machines) beside it. Pipes
 * carry what a pump drives through them (PipeNetworks); a change to any of them makes the networks look again.
 */
public final class FluidPipeBlock extends Block {
    private static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5), Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 5, 11, 11), Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11), Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public FluidPipeBlock(Properties p) {
        super(p);
        BlockState s = stateDefinition.any();
        for (BooleanProperty b : SIDES.values()) { s = s.setValue(b, false); }
        registerDefaultState(s);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { SIDES.values().forEach(b::add); }

    /** Whether a pipe joins what is beside it: another pipe, or a block that holds fluid on that side. */
    static boolean joins(LevelAccessor level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        if (level.getBlockState(n).getBlock() instanceof FluidPipeBlock) { return true; }
        return level instanceof Level l && l.getCapability(Capabilities.FluidHandler.BLOCK, n, d.getOpposite()) != null;
    }

    private BlockState connected(LevelAccessor level, BlockPos pos, BlockState s) {
        for (Direction d : Direction.values()) { s = s.setValue(SIDES.get(d), joins(level, pos, d)); }
        return s;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return connected(c.getLevel(), c.getClickedPos(), defaultBlockState()); }

    @Override protected BlockState updateShape(BlockState s, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return s.setValue(SIDES.get(d), joins(level, pos, d));
    }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) {
        return shapes.computeIfAbsent(s, st -> {
            VoxelShape v = CORE;
            for (Direction d : Direction.values()) { if (st.getValue(SIDES.get(d))) { v = Shapes.or(v, ARMS.get(d)); } }
            return v;
        });
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { PipeNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) { PipeNetworks.changed(level); }
        super.onRemove(s, level, pos, now, moved);
    }
}
