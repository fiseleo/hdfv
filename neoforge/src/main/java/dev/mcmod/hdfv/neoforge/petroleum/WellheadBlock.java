package dev.mcmod.hdfv.neoforge.petroleum;

import com.mojang.serialization.MapCodec;
import dev.mcmod.hdfv.neoforge.multiblock.PatternBlock;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A wellhead (0.8): the Christmas tree on top of a well; a pump jack's head goes over it. */
public final class WellheadBlock extends BaseEntityBlock implements PatternBlock {
    public static final MapCodec<WellheadBlock> CODEC = simpleCodec(WellheadBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public WellheadBlock(Properties p) { super(p); }

    @Override public String kind() { return Structures.WELLHEAD; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new WellheadBlockEntity(pos, s); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HdfvPetroleum.WELLHEAD_ENTITY.get(), (l, p, st, be) -> be.tick());
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WellheadBlockEntity w) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp && !player.isSecondaryUseActive()) { PetroleumMenu.open(sp, w, s.getBlock().getName()); }
            else { player.displayClientMessage(w.status(), true); }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) { dev.mcmod.hdfv.neoforge.fluid.PipeNetworks.changed(level); }
    }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) { dev.mcmod.hdfv.neoforge.fluid.PipeNetworks.changed(level); }
        super.onRemove(s, level, pos, now, moved);
    }
}
