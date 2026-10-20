package dev.mcmod.hdfv.neoforge.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A work light (0.8): a floodlight on a short stand for the assembly rooms and workshops, as bright as the game lets a block be. */
public final class WorkLightBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public WorkLightBlock(Properties p) { super(p); }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
}
