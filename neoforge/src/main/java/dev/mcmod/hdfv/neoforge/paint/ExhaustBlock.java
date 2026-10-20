package dev.mcmod.hdfv.neoforge.paint;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A paint booth's extraction fan (0.13): a block of the booth's wall or roof, its grille facing the one who places it
 * (into the room). Solid, so a room closes with it.
 */
public final class ExhaustBlock extends DirectionalBlock {
    public static final MapCodec<ExhaustBlock> CODEC = simpleCodec(ExhaustBlock::new);

    public ExhaustBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getNearestLookingDirection().getOpposite()); }
}
