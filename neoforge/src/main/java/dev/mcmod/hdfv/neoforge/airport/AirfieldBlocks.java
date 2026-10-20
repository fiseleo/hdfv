package dev.mcmod.hdfv.neoforge.airport;

import com.mojang.serialization.MapCodec;
import dev.mcmod.hdfv.neoforge.multiblock.MemberBlock;
import dev.mcmod.hdfv.neoforge.multiblock.Multiblocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The airfield's small blocks (0.20): the cab console, the windsock, the holding sign, the apron floodlight. */
public final class AirfieldBlocks {
    private AirfieldBlocks() {}

    /** A tower's cab console: a member of the tower; used, it opens its tower's screen. */
    public static final class Console extends MemberBlock {
        public Console(Properties p, String kind) { super(p, kind, true); }

        @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
                BlockPos controller = Multiblocks.controllerOf(server, pos);
                if (controller != null && server.getBlockEntity(controller) instanceof TowerBlockEntity tower) { tower.openScreen(sp); }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** A windsock on its mast: its renderer turns and fills it with the wind. */
    public static final class Windsock extends BaseEntityBlock {
        private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);
        private static final MapCodec<Windsock> CODEC = simpleCodec(Windsock::new);

        public Windsock(Properties p) { super(p); }

        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new WindsockBlockEntity(pos, s); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
    }

    /** A holding position sign, facing the way it reads. */
    public static final class Sign extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape NS = Block.box(0, 0, 6, 16, 12, 10), EW = Block.box(6, 0, 0, 10, 12, 16);

        public Sign(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) {
            return s.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
        }
    }

    /** An apron floodlight on its mast: lit (block light 15) when its tower turns the apron lights on. */
    /** A steel column (0.21: the spiral stair's): a post, solid only where it is. */
    public static final class Column extends Block {
        private static final VoxelShape SHAPE = Block.box(5.5, 0, 5.5, 10.5, 16, 10.5);

        public Column(Properties p) { super(p); }

        @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
    }

    /**
     * An interior lamp (0.21): a panel on whatever face it is put on, shining away from it (FACING: down on a ceiling, a
     * wall lamp's out of its wall). Its tower lights it while the tower has power (TowerBlockEntity); no collision.
     */
    public static final class InteriorLamp extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.FACING;
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        private static final java.util.Map<Direction, VoxelShape> SHAPES = java.util.Map.of(
                Direction.DOWN, Block.box(2, 14.5, 2, 14, 16, 14), Direction.UP, Block.box(2, 0, 2, 14, 1.5, 14),
                Direction.NORTH, Block.box(3, 4, 14.5, 13, 12, 16), Direction.SOUTH, Block.box(3, 4, 0, 13, 12, 1.5),
                Direction.WEST, Block.box(14.5, 4, 3, 16, 12, 13), Direction.EAST, Block.box(0, 4, 3, 1.5, 12, 13));

        public InteriorLamp(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(LIT, false)); }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getClickedFace()); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPES.get(s.getValue(FACING)); }
    }

    public static final class Floodlight extends Block {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

        public Floodlight(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false)); }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }

        @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (!old.is(this) && level instanceof ServerLevel server) { AirfieldData.of(server).addFloodlight(pos); }
        }

        @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
            if (!now.is(this) && level instanceof ServerLevel server) { AirfieldData.of(server).removeFloodlight(pos); }
            super.onRemove(s, level, pos, now, moved);
        }
    }
}
