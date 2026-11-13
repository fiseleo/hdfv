package dev.mcmod.hdfv.neoforge.storage;

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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 0.25: the cargo loader (CargoLoaderBlockEntity): its chute faces the way the player placing it looks from, toward the
 * parking place in front of them. Used by hand it says which vehicle's hold it serves, if any.
 */
public final class CargoLoaderBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<CargoLoaderBlock> CODEC = simpleCodec(CargoLoaderBlock::new);

    public CargoLoaderBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CargoLoaderBlockEntity(pos, state); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CargoLoaderBlockEntity be) {
            VehicleCargo.Hold h = be.target();
            player.displayClientMessage(h == null ? Component.translatable("hdfv.cargo_loader.none")
                    : Component.translatable("hdfv.cargo_loader.serving", h.vehicle.blueprint() == null ? "?" : h.vehicle.blueprint().name(),
                    VehicleCargo.name(h.vehicle.vehicle(), h.index), h.used(), h.getContainerSize(), dev.mcmod.hdfv.storage.CargoMass.text(h.massKg())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
