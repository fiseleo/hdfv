package dev.mcmod.hdfv.neoforge.fluid;

import com.mojang.serialization.MapCodec;
import dev.mcmod.hdfv.fluid.HydrogenStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A tank block (0.8): a cubic metre; stacked, one tank. Buckets and cans fill and empty it; used empty-handed it tells
 * what is in it. 0.10: the same block in three stores (core fluid.HydrogenStorage): the ordinary tank, the cryogenic
 * tank (insulated, for liquid hydrogen) and the high-pressure cylinder bank (700 bar hydrogen only); each stacks only with
 * its own kind.
 */
public final class TankBlock extends BaseEntityBlock {
    private final HydrogenStorage.Store store;
    private final MapCodec<TankBlock> codec;

    public TankBlock(Properties p) { this(p, HydrogenStorage.Store.TANK); }

    public TankBlock(Properties p, HydrogenStorage.Store store) {
        super(p);
        this.store = store;
        this.codec = simpleCodec(q -> new TankBlock(q, store));
    }

    public HydrogenStorage.Store store() { return store; }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return codec; }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide || store == HydrogenStorage.Store.CYLINDERS ? null
                : createTickerHelper(type, HdfvFluidRegistry.TANK_ENTITY.get(), (l, p, st, be) -> be.tick());
    }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new TankBlockEntity(pos, s); }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.getFluidHandler(stack).isEmpty()) { return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
        return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection()) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.CONSUME;
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TankBlockEntity t) { player.displayClientMessage(contents(t.column()), true); }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** "Diesel: 2500 / 3000 l" or "Empty: 0 / 3000 l". */
    public static Component contents(IFluidHandler h) {
        FluidStack f = h.getFluidInTank(0);
        Component name = f.isEmpty() ? Component.translatable("hdfv.fluid.empty") : f.getHoverName();
        return Component.translatable("hdfv.fluid.contents", name, f.getAmount(), h.getTankCapacity(0));
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { PipeNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) { PipeNetworks.changed(level); }
        super.onRemove(s, level, pos, now, moved);
    }
}
