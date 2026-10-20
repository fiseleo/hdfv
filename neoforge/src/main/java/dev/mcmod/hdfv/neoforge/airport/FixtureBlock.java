package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.hdfv.airport.Fixture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An airfield light fitting (0.20): what kind it is is all it knows. Where it stands decides its colours and aim
 * (AirfieldLighting), its tower switches and powers it; its light is drawn by AeroCore's FieldLamps from the server's
 * list of fittings (AirfieldData), far beyond the chunks a client has. No collision (aircraft roll over inset lights
 * and the wind does not see them), on solid ground. Facing only turns its model.
 */
public final class FixtureBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Fixture.Kind kind;
    private final VoxelShape shape;

    public FixtureBlock(Properties p, Fixture.Kind kind) {
        super(p);
        this.kind = kind;
        this.shape = switch (kind) {
            case RUNWAY_CENTRE, TAXI_CENTRE -> Block.box(5, 0, 5, 11, 1, 11);
            case PAPI -> Block.box(1, 0, 1, 15, 10, 15);
            case APPROACH, APPROACH_FLASHER -> Block.box(4, 0, 4, 12, 14, 12);
            case BEACON -> Block.box(2, 0, 2, 14, 15, 14);
            default -> Block.box(5, 0, 5, 11, 7, 11);
        };
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Fixture.Kind kind() { return kind; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return shape; }

    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return Shapes.empty(); }

    @Override protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        return kind == Fixture.Kind.BEACON || kind == Fixture.Kind.OBSTRUCTION || level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override protected BlockState updateShape(BlockState s, Direction d, BlockState other, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return d == Direction.DOWN && !canSurvive(s, level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : s;
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this) && level instanceof ServerLevel server) { AirfieldData.of(server).addFixture(pos, kind); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this) && level instanceof ServerLevel server) { AirfieldData.of(server).removeFixture(pos); }
        super.onRemove(s, level, pos, now, moved);
    }
}
