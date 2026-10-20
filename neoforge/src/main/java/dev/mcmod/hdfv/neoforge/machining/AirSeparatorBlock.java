package dev.mcmod.hdfv.neoforge.machining;

import com.mojang.serialization.MapCodec;
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

import java.util.Locale;

/**
 * The air separation unit's block (0.17): cables join it; used with empty cylinders it takes them, empty-handed it tells
 * its state and gives a full cylinder, sneaking it switches the gas.
 */
public final class AirSeparatorBlock extends BaseEntityBlock {
    public static final MapCodec<AirSeparatorBlock> CODEC = simpleCodec(AirSeparatorBlock::new);

    public AirSeparatorBlock(Properties p) { super(p); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new AirSeparatorBlockEntity(pos, s); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HdfvMachining.AIR_SEPARATOR_ENTITY.get(), (l, p, st, be) -> be.tick());
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!held.is(HdfvMachining.EMPTY_CYLINDER.get())) { return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AirSeparatorBlockEntity be) {
            ItemStack left = be.handler().insertItem(AirSeparatorBlockEntity.EMPTIES, held.copy(), false);
            if (!player.getAbilities().instabuild) { held.setCount(left.getCount()); }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AirSeparatorBlockEntity be) {
            if (player.isShiftKeyDown()) { be.switchGas(); }
            ItemStack full = be.handler().extractItem(AirSeparatorBlockEntity.FULLS, 16, false);
            if (!full.isEmpty() && !player.getInventory().add(full)) { player.drop(full, false); }
            player.displayClientMessage(Component.translatable("hdfv.air_separator.status",
                    Component.translatable("hdfv.air_separator.state." + be.state().name().toLowerCase(Locale.ROOT)),
                    Component.translatable("hdfv.gas." + be.product().id()),
                    String.format(Locale.ROOT, "%.1f", be.filledKg()), String.format(Locale.ROOT, "%.1f", be.product().cylinderKg()),
                    be.items().getItem(AirSeparatorBlockEntity.EMPTIES).getCount(), be.energy().getEnergyStored()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) {
            if (level.getBlockEntity(pos) instanceof AirSeparatorBlockEntity be) { net.minecraft.world.Containers.dropContents(level, pos, be.items()); }
            dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(level);
        }
        super.onRemove(s, level, pos, now, moved);
    }
}
