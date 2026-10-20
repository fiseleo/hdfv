package dev.mcmod.hdfv.neoforge.petroleum;

import com.mojang.serialization.MapCodec;
import dev.mcmod.hdfv.neoforge.fluid.PipeNetworks;
import dev.mcmod.hdfv.neoforge.fluid.TankBlock;
import dev.mcmod.hdfv.neoforge.multiblock.PatternBlock;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A process unit's outlet (0.8): a draw-off nozzle with its drum. */
public final class UnitOutletBlock extends BaseEntityBlock implements PatternBlock {
    public static final MapCodec<UnitOutletBlock> CODEC = simpleCodec(UnitOutletBlock::new);

    public UnitOutletBlock(Properties p) { super(p); }

    @Override public String kind() { return Structures.UNIT_OUTLET; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new UnitOutletBlockEntity(pos, s); }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof UnitOutletBlockEntity o) { player.displayClientMessage(TankBlock.contents(o.handler()), true); }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) { if (!old.is(this)) { PipeNetworks.changed(level); } }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) {
            PipeNetworks.changed(level);
            if (level instanceof net.minecraft.server.level.ServerLevel server) { dev.mcmod.hdfv.neoforge.multiblock.Multiblocks.memberRemoved(server, pos); }
        }
        super.onRemove(s, level, pos, now, moved);
    }
}
