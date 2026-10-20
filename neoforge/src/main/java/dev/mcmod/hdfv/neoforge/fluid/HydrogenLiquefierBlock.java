package dev.mcmod.hdfv.neoforge.fluid;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The hydrogen liquefier's block (0.10): pipes and cables join it; used empty-handed it tells its state and what it holds. */
public final class HydrogenLiquefierBlock extends BaseEntityBlock {
    public static final MapCodec<HydrogenLiquefierBlock> CODEC = simpleCodec(HydrogenLiquefierBlock::new);

    public HydrogenLiquefierBlock(Properties p) { super(p); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new HydrogenLiquefierBlockEntity(pos, s); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HdfvFluidRegistry.LIQUEFIER_ENTITY.get(), (l, p, st, be) -> be.tick());
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HydrogenLiquefierBlockEntity be) {
            player.displayClientMessage(Component.translatable("hdfv.liquefier.status",
                    Component.translatable("hdfv.liquefier.state." + be.state().name().toLowerCase(java.util.Locale.ROOT)),
                    be.gas().getAmount(), be.liquid().getAmount(), be.energy().getEnergyStored()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { PipeNetworks.changed(level); dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) { PipeNetworks.changed(level); dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(level); }
        super.onRemove(s, level, pos, now, moved);
    }
}
