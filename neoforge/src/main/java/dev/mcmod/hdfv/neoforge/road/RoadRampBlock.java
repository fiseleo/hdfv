package dev.mcmod.hdfv.neoforge.road;

import dev.mcmod.hdfv.road.RampShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A road ramp (0.25): a slope of pavement rising a block over 2, 4 or 8 blocks of run, a block a part (core
 * RampShape). FACING is the way uphill, PART the block's place in the run (0 at the foot). Its shape is sixteen steps
 * a sixteenth of a block long, each as high as the slope at its middle (wheels' rays meet them and find their edges,
 * AeroCore's WheelCorner; players walk up them); its model is the smooth wedge.
 *
 * <p>Placing: a ramp put where a run of the same block goes on takes the next part by itself (behind a lower part
 * uphill of it, or before a higher one downhill); put a block higher beyond a run's top, or a block lower before its
 * foot, it starts the next flight the same way round; anywhere else it is part 0 rising the way the player looks.
 * No mob spawns on it; it holds water like a stair.
 */
public abstract class RoadRampBlock extends Block implements SimpleWaterloggedBlock, dev.mcmod.aerocore.neoforge.vehicle.SlopedGround {
    /**
     * The slope itself for wheels (AeroCore 0.42's SlopedGround): steps are treads and risers to a wheel's ray, each riser
     * a small kerb, and a car coasting up the steps of a 1:4 run lost far more than the height's energy. {the top's
     * height at the corner with the lowest x and z, its rise a block along +x, along +z}.
     */
    @Override public double[] groundSlope(BlockState s) {
        int n = gradient(), k = s.getValue(part());
        double rise = 1.0 / n, low = RampShape.height(n, k, 0), high = RampShape.height(n, k, 1);
        return switch (s.getValue(FACING)) {
            case EAST -> new double[]{low, rise, 0};
            case WEST -> new double[]{high, -rise, 0};
            case SOUTH -> new double[]{low, 0, rise};
            default -> new double[]{high, 0, -rise};
        };
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty PART_2 = IntegerProperty.create("part", 0, 1), PART_4 = IntegerProperty.create("part", 0, 3),
            PART_8 = IntegerProperty.create("part", 0, 7);

    /** By the facing's horizontal index and the part. */
    private final VoxelShape[][] shapes;

    protected RoadRampBlock(Properties p) {
        super(p.isValidSpawn((s, level, pos, entity) -> false));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(part(), 0).setValue(WATERLOGGED, false));
        int n = gradient();
        shapes = new VoxelShape[4][n];
        for (Direction f : Direction.Plane.HORIZONTAL) { for (int k = 0; k < n; k++) { shapes[f.get2DDataValue()][k] = shape(f, n, k); } }
    }

    /** The block's part property (its values 0 .. gradient - 1); a constant of the class: the state definition is made in Block's constructor. */
    public abstract IntegerProperty part();

    /** Blocks of run to a block of rise. */
    public final int gradient() { return part().getPossibleValues().size(); }

    /** A 1:2 ramp. */
    public static final class Two extends RoadRampBlock {
        public Two(Properties p) { super(p); }
        @Override public IntegerProperty part() { return PART_2; }
    }

    /** A 1:4 ramp. */
    public static final class Four extends RoadRampBlock {
        public Four(Properties p) { super(p); }
        @Override public IntegerProperty part() { return PART_4; }
    }

    /** A 1:8 ramp. */
    public static final class Eight extends RoadRampBlock {
        public Eight(Properties p) { super(p); }
        @Override public IntegerProperty part() { return PART_8; }
    }

    public static RoadRampBlock of(int gradient, Properties p) {
        return switch (gradient) {
            case 2 -> new Two(p);
            case 4 -> new Four(p);
            case 8 -> new Eight(p);
            default -> throw new IllegalArgumentException("No 1:" + gradient + " ramp");
        };
    }

    /** The steps of a part rising toward facing: step i lies i sixteenths in from the low edge. */
    static VoxelShape shape(Direction facing, int gradient, int part) {
        double[] heights = RampShape.steps(gradient, part);
        VoxelShape out = Shapes.empty();
        for (int i = 0; i < heights.length; i++) {
            double a = i, b = i + 1, h = 16 * heights[i];
            out = Shapes.or(out, switch (facing) {
                case SOUTH -> Block.box(0, 0, a, 16, h, b);
                case EAST -> Block.box(a, 0, 0, b, h, 16);
                case WEST -> Block.box(16 - b, 0, 0, 16 - a, h, 16);
                default -> Block.box(0, 0, 16 - b, 16, h, 16 - a);
            });
        }
        return out.optimize();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, part(), WATERLOGGED); }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) {
        return shapes[s.getValue(FACING).get2DDataValue()][s.getValue(part())];
    }

    @Override protected boolean useShapeForLightOcclusion(BlockState s) { return true; }

    private boolean is(BlockState s, Direction facing) { return s.is(this) && s.getValue(FACING) == facing; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        Level level = c.getLevel();
        BlockPos pos = c.getClickedPos();
        BlockState base = defaultBlockState().setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
        int last = gradient() - 1;
        Direction look = c.getHorizontalDirection();
        Direction[] order = {look, look.getOpposite(), look.getClockWise(), look.getCounterClockWise()};
        // a run going on at this level: after the part downhill of here, or before the part uphill
        for (Direction f : order) {
            BlockState downhill = level.getBlockState(pos.relative(f.getOpposite())), uphill = level.getBlockState(pos.relative(f));
            if (is(downhill, f) && downhill.getValue(part()) < last) { return base.setValue(FACING, f).setValue(part(), downhill.getValue(part()) + 1); }
            if (is(uphill, f) && uphill.getValue(part()) > 0) { return base.setValue(FACING, f).setValue(part(), uphill.getValue(part()) - 1); }
        }
        // the next flight: a block above a run's top and beyond it, or a block below its foot and before it
        for (Direction f : order) {
            BlockState top = level.getBlockState(pos.relative(f.getOpposite()).below()), foot = level.getBlockState(pos.relative(f).above());
            if (is(top, f) && top.getValue(part()) == last) { return base.setValue(FACING, f); }
            if (is(foot, f) && foot.getValue(part()) == 0) { return base.setValue(FACING, f).setValue(part(), last); }
        }
        return base.setValue(FACING, look);
    }

    @Override protected BlockState updateShape(BlockState s, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (s.getValue(WATERLOGGED)) { level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level)); }
        return super.updateShape(s, side, neighbour, level, pos, neighbourPos);
    }

    @Override protected FluidState getFluidState(BlockState s) { return s.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(s); }

    @Override protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }

    @Override protected BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
}
