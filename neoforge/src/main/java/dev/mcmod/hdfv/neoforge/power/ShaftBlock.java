package dev.mcmod.hdfv.neoforge.power;

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

import java.util.HashMap;
import java.util.Map;

/**
 * A metre of 60 mm line shaft in its hangers (0.8), joining shafts (turning a corner through bevel gears) and what is
 * belted to them (ShaftEnd).
 */
public final class ShaftBlock extends Block {
    private static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6), Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16),
            Direction.WEST, Block.box(0, 6, 6, 6, 10, 10), Direction.EAST, Block.box(10, 6, 6, 16, 10, 10),
            Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10), Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public ShaftBlock(Properties p) {
        super(p);
        BlockState s = stateDefinition.any();
        for (BooleanProperty b : SIDES.values()) { s = s.setValue(b, false); }
        registerDefaultState(s);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { SIDES.values().forEach(b::add); }

    static boolean joins(LevelAccessor level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        return level.getBlockState(n).getBlock() instanceof ShaftBlock || level.getBlockEntity(n) instanceof ShaftEnd;
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

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) { if (!old.is(this)) { ShaftNetworks.changed(level); } }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) { ShaftNetworks.changed(level); }
        super.onRemove(s, level, pos, now, moved);
    }
}
