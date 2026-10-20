package dev.mcmod.hdfv.neoforge.fluid;

import com.mojang.serialization.MapCodec;
import dev.mcmod.hdfv.neoforge.multiblock.PatternBlock;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/** A storage tank's valve (0.8): the controller of the storage tank, facing out of its first course. */
public final class StorageValveBlock extends BaseEntityBlock implements PatternBlock {
    public static final MapCodec<StorageValveBlock> CODEC = simpleCodec(StorageValveBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public StorageValveBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override public String kind() { return Structures.TANK_VALVE; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new StorageValveBlockEntity(pos, s); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HdfvFluidRegistry.VALVE_ENTITY.get(), (l, p, st, be) -> be.tick());
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.getFluidHandler(stack).isEmpty()) { return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection()) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.CONSUME;
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof StorageValveBlockEntity be) {
            boolean formed = be.multiblock().check(server);
            player.displayClientMessage(formed ? TankBlock.contents(be.handler()) : Component.translatable("hdfv.fluid.not_formed"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { PipeNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) {
            PipeNetworks.changed(level);
            if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof StorageValveBlockEntity be) { be.multiblock().dissolve(server); }
        }
        super.onRemove(s, level, pos, now, moved);
    }
}
