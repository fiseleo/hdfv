package dev.mcmod.hdfv.neoforge.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * A door of an assembly room (0.7): a hangar door, roller shutter or dock gate, any number of blocks making one opening.
 * Closed it is wall; open it is rolled up into the head at its top and lets a vehicle through. Using one opens or closes
 * every door block joined to it.
 * <p>
 * 0.8: it moves. OPEN is where it is going; PHASE (0 shut to 4 open) is where it is, a step every STEP_TICKS, the same in
 * every block of the door: it lets things through only when fully open.
 * <p>
 * It is one curtain: only the top row carries the head (the shutter's coil, the panels' or the gate's housing), and the
 * curtain's foot rises through the rows below into it. What a block shows follows from its PHASE and its place in its
 * column ({@link #looks}): TOP, EDGE (where the foot is in this block) and AXIS (the way the door spans), kept up to date
 * when the block is placed, when a neighbour changes and at every step.
 */
public final class RoomDoorBlock extends Block {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 4);
    /** Whether this block is the top of its column, which carries the head. */
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    /**
     * Where the curtain's foot is in this block: 0 below it (the curtain fills the block), 1 to 4 at 0, 4, 8, 12 px in a
     * lower row (1 to 3 at 0, 4, 9 px in the top row, whose upper 6 px are the head: 3 is rolled up), 5 above it (none here).
     */
    public static final IntegerProperty EDGE = IntegerProperty.create("edge", 0, 5);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final int STEP_TICKS = 8;
    /** Most blocks one door is (a 16 by 16 m hangar door). */
    public static final int MAX = 256;
    /** Where the head begins in the top row, px. */
    public static final int HEAD = 10;
    private static final int[] FOOT = {0, 0, 4, 8, 12, -1}, TOP_FOOT = {0, 0, 4, 9, 9, 9};
    private static final int COLUMN = 32;
    private final VoxelShape[] shapes = new VoxelShape[2 * 2 * 6];

    /** @param curtainPx how thick the curtain is and @param headPx how deep the head, both across the door */
    public RoomDoorBlock(Properties p, int curtainPx, int headPx) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(PHASE, 0).setValue(TOP, true).setValue(EDGE, 1)
                .setValue(AXIS, Direction.Axis.X));
        for (int a = 0; a < 2; a++) {
            for (int t = 0; t < 2; t++) {
                for (int e = 0; e <= 5; e++) {
                    int foot = t == 1 ? TOP_FOOT[e] : FOOT[e], top = t == 1 ? HEAD : 16;
                    VoxelShape s = foot >= 0 && foot < top ? box(a, curtainPx, foot, top) : Shapes.empty();
                    shapes[(a * 2 + t) * 6 + e] = t == 1 ? Shapes.or(s, box(a, headPx, HEAD, 16)) : s;
                }
            }
        }
    }

    private static VoxelShape box(int axis, int depth, int y0, int y1) {
        double z0 = 8 - depth / 2.0, z1 = 8 + depth / 2.0;
        return axis == 0 ? Block.box(0, y0, z0, 16, y1, z1) : Block.box(z0, y0, 0, z1, y1, 16);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(OPEN, PHASE, TOP, EDGE, AXIS); }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) {
        return shapes[((s.getValue(AXIS) == Direction.Axis.X ? 0 : 1) * 2 + (s.getValue(TOP) ? 1 : 0)) * 6 + s.getValue(EDGE)];
    }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return s.getValue(PHASE) == 4 ? Shapes.empty() : Shapes.block(); }
    @Override protected boolean propagatesSkylightDown(BlockState s, BlockGetter level, BlockPos pos) { return s.getValue(PHASE) == 4; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return looks(defaultBlockState().setValue(AXIS, c.getHorizontalDirection().getClockWise().getAxis()), c.getLevel(), c.getClickedPos());
    }

    /** Placed any way (a player, /setblock, a structure): it takes its place in its column. */
    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) {
            BlockState looks = looks(s, level, pos);
            if (looks != s) { level.setBlock(pos, looks, Block.UPDATE_ALL); }
        }
    }

    @Override protected BlockState updateShape(BlockState s, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos from) {
        return looks(s, level, pos);
    }

    /** Doors saved before they kept their looks (0.8 worlds), or placed without updates, take their place in a minute or two. */
    @Override protected boolean isRandomlyTicking(BlockState s) { return true; }
    @Override protected void randomTick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState looks = looks(s, level, pos);
        if (looks != s) { level.setBlock(pos, looks, Block.UPDATE_CLIENTS); }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) { setOpen(level, pos, !state.getValue(OPEN)); }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** One step toward where it is going. */
    @Override protected void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        int phase = s.getValue(PHASE), target = s.getValue(OPEN) ? 4 : 0;
        if (phase == target) { return; }
        int next = phase + Integer.signum(target - phase);
        level.setBlock(pos, looks(s.setValue(PHASE, next), level, pos), Block.UPDATE_ALL);
        if (next != target) { level.scheduleTick(pos, this, STEP_TICKS); }
    }

    /**
     * What a block shows at its PHASE. Its column is the door blocks straight above and below it; the curtain's foot
     * travels 4 quarters a row below the top and 2 into the top row (to just under the head), the whole way in the
     * door's 4 steps.
     */
    static BlockState looks(BlockState s, BlockGetter level, BlockPos pos) {
        int below = 0, above = 0;
        while (below < COLUMN && isDoor(level, pos.below(below + 1))) { below++; }
        while (above < COLUMN && isDoor(level, pos.above(above + 1))) { above++; }
        int quarters = 4 * (below + above) + 2, foot = (s.getValue(PHASE) * quarters + 2) / 4 - 4 * below;
        int edge = foot < 0 ? 0 : foot <= 3 ? foot + 1 : 5;
        return s.setValue(TOP, above == 0).setValue(EDGE, edge).setValue(AXIS, axis(level, pos, s.getValue(AXIS)));
    }

    /** The way the door spans: along its other blocks, else between the walls it stands in, else as it was. */
    static Direction.Axis axis(BlockGetter level, BlockPos pos, Direction.Axis was) {
        int doorsX = 0, doorsZ = 0, wallsX = 0, wallsZ = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            boolean door = isDoor(level, n), wall = !door && level.getBlockState(n).isFaceSturdy(level, n, d.getOpposite());
            if (d.getAxis() == Direction.Axis.X) { doorsX += door ? 1 : 0; wallsX += wall ? 1 : 0; }
            else { doorsZ += door ? 1 : 0; wallsZ += wall ? 1 : 0; }
        }
        if (doorsX != doorsZ) { return doorsX > doorsZ ? Direction.Axis.X : Direction.Axis.Z; }
        if (wallsX != wallsZ) { return wallsX > wallsZ ? Direction.Axis.X : Direction.Axis.Z; }
        return was;
    }

    private static boolean isDoor(BlockGetter level, BlockPos p) { return level.getBlockState(p).getBlock() instanceof RoomDoorBlock; }

    /** The blocks of the door a block belongs to. */
    public static Set<BlockPos> door(Level level, BlockPos start) {
        Set<BlockPos> door = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (level.getBlockState(start).getBlock() instanceof RoomDoorBlock) { door.add(start); queue.add(start); }
        while (!queue.isEmpty() && door.size() < MAX) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!door.contains(n) && level.getBlockState(n).getBlock() instanceof RoomDoorBlock) { door.add(n); queue.add(n); }
            }
        }
        return door;
    }

    /** Sets the whole door a block belongs to opening or closing; returns its blocks. */
    public static Set<BlockPos> setOpen(Level level, BlockPos start, boolean open) {
        Set<BlockPos> door = door(level, start);
        for (BlockPos p : door) {
            BlockState s = level.getBlockState(p);
            if (s.getValue(OPEN) != open) {
                level.setBlock(p, looks(s.setValue(OPEN, open), level, p), Block.UPDATE_ALL);
                level.scheduleTick(p, s.getBlock(), STEP_TICKS);
            }
        }
        if (!door.isEmpty()) {
            // the whole door runs together: four steps (PHASE 0 to 4)
            dev.mcmod.hdfv.neoforge.sound.SoundSync.event(level, net.minecraft.world.phys.Vec3.atCenterOf(start), dev.mcmod.hdfv.sound.MachineSound.Event.DOOR, 4 * STEP_TICKS / 20.0);
        }
        return door;
    }

    /** Whether every block of a door is fully open. */
    public static boolean fullyOpen(Level level, BlockPos start) {
        Set<BlockPos> door = door(level, start);
        if (door.isEmpty()) { return false; }
        for (BlockPos p : door) { if (level.getBlockState(p).getValue(PHASE) != 4) { return false; } }
        return true;
    }
}
