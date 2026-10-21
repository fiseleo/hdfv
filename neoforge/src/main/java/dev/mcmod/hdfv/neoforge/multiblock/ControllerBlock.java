package dev.mcmod.hdfv.neoforge.multiblock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.function.Supplier;

/** A multiblock machine's controller block (0.8): facing whoever placed it, its kind in the pattern, its block entity ticking on the server. */
public final class ControllerBlock extends BaseEntityBlock implements PatternBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final String kind;
    private final Supplier<? extends BlockEntityType<? extends ControllerBlockEntity>> type;
    private final MapCodec<ControllerBlock> codec;

    public ControllerBlock(Properties p, String kind, Supplier<? extends BlockEntityType<? extends ControllerBlockEntity>> type) {
        super(p);
        this.kind = kind;
        this.type = type;
        this.codec = simpleCodec(q -> new ControllerBlock(q, kind, type));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override public String kind() { return kind; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return type.get().create(pos, s); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> t) {
        return level.isClientSide ? null : (l, p, st, be) -> { if (be instanceof ControllerBlockEntity c) { c.tick(); } };
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) { return ItemInteractionResult.SUCCESS; }
        if (level.getBlockEntity(pos) instanceof ControllerBlockEntity be && be.use(player, hand, stack)) {
            player.displayClientMessage(be.status(), true);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            be.multiblock().check(server);
            if (be.multiblock().formed() && player instanceof net.minecraft.server.level.ServerPlayer sp) {   // 0.24: the tutorial's "formed" milestone
                dev.mcmod.hdfv.neoforge.tutorial.Milestones.reach(sp, "formed", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
            }
            // a controller with its own screen (0.20: the control tower)
            if (be instanceof Screened screened && !player.isSecondaryUseActive() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                screened.openScreen(sp);
                return InteractionResult.CONSUME;
            }
            // a petroleum block's screen (0.9); sneaking keeps the quick use (start, stop) and the status line
            if (be instanceof dev.mcmod.hdfv.neoforge.petroleum.Configurable && !player.isSecondaryUseActive()
                    && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                dev.mcmod.hdfv.neoforge.petroleum.PetroleumMenu.open(sp, be, s.getBlock().getName());
                return InteractionResult.CONSUME;
            }
            be.use(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.displayClientMessage(be.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this) && level.getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            for (ItemStack d : be.drops()) { Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), d); }
            if (level instanceof ServerLevel server) { be.multiblock().dissolve(server); be.broken(server); }
            dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(level);
        }
        super.onRemove(s, level, pos, now, moved);
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(level); }
    }

    /** A controller's energy handler (its buffer). */
    public static IEnergyStorage energy(ControllerBlockEntity be) { return be.energy(); }
}
