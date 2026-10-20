package dev.mcmod.hdfv.neoforge.design;

import dev.mcmod.hdfv.neoforge.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The design table: opens the design screen, with the blueprint in hand if there is one. */
public final class DesignTableBlock extends Block {
    public DesignTableBlock(Properties properties) { super(properties); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) { ClientAccess.openDesignScreen(player); }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
