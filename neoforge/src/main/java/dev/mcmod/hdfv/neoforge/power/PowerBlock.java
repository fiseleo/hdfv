package dev.mcmod.hdfv.neoforge.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
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

/** The power blocks (0.8): a battery, generator, motor, steam engine, treadle, engine stand or creative source, by kind. */
public final class PowerBlock extends BaseEntityBlock {
    public enum Kind { BATTERY, GENERATOR, ELECTRIC_MOTOR, STEAM_ENGINE, TREADLE, ENGINE_STAND, CREATIVE_POWER }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final Kind kind;
    private final MapCodec<PowerBlock> codec;

    public PowerBlock(Properties p, Kind kind) {
        super(p);
        this.kind = kind;
        this.codec = simpleCodec(q -> new PowerBlock(q, kind));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) {
        return switch (kind) {
            case BATTERY -> new BatteryBlockEntity(pos, s);
            case GENERATOR -> new GeneratorBlockEntity(pos, s);
            case ELECTRIC_MOTOR -> new MotorBlockEntity(pos, s);
            case STEAM_ENGINE -> new SteamEngineBlockEntity(pos, s);
            case TREADLE -> new TreadleBlockEntity(pos, s);
            case ENGINE_STAND -> new EngineStandBlockEntity(pos, s);
            case CREATIVE_POWER -> new CreativePowerBlockEntity(pos, s);
        };
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, st, be) -> { if (be instanceof PowerBlockEntity pbe) { pbe.tick(); } };
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) { return ItemInteractionResult.SUCCESS; }
        if (level.getBlockEntity(pos) instanceof PowerBlockEntity be && be.use(player, hand, stack)) {
            player.displayClientMessage(be.status(), true);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PowerBlockEntity be) {
            be.use(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.displayClientMessage(be.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { EnergyNetworks.changed(level); ShaftNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) {
            EnergyNetworks.changed(level);
            ShaftNetworks.changed(level);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof EngineStandBlockEntity stand && !stand.engine().isEmpty()) { Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stand.engine()); }
            if (be instanceof SteamEngineBlockEntity steam) { Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), steam.items().getStackInSlot(0)); }
        }
        super.onRemove(s, level, pos, now, moved);
    }
}
