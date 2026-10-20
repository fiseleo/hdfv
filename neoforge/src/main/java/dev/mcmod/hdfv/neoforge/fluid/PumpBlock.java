package dev.mcmod.hdfv.neoforge.fluid;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

/** An electric pump (0.8): draws from behind it and delivers in front (FACING, away from whoever placed it). */
public final class PumpBlock extends BaseEntityBlock {
    public static final MapCodec<PumpBlock> CODEC = simpleCodec(PumpBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public PumpBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getNearestLookingDirection()); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new PumpBlockEntity(pos, s); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HdfvFluidRegistry.PUMP_ENTITY.get(), (l, p, st, be) -> be.tick());
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PumpBlockEntity be) {
            player.displayClientMessage(Component.translatable("hdfv.pump.status", Component.translatable("hdfv.pump.state." + be.state().name().toLowerCase(java.util.Locale.ROOT)),
                    String.format(java.util.Locale.ROOT, "%.2f", be.lastFlowM3PerS() * 1000), String.format(java.util.Locale.ROOT, "%.1f", be.lastPowerW() / 1000)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
