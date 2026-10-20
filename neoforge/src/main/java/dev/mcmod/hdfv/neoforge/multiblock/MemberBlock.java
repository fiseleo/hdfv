package dev.mcmod.hdfv.neoforge.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A block a multiblock is built of (0.8): frames, beds, guards and modules. FORMED while it is part of a formed
 * machine (its controller's renderer may then draw the whole machine over it: such a member has no model then and hides
 * no neighbour's faces); taking one away dissolves the machine it was in (Multiblocks).
 */
public class MemberBlock extends Block implements PatternBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    private final String kind;
    private final boolean drawnWhenFormed;

    public MemberBlock(Properties p, String kind) { this(p, kind, false); }

    /** A member its controller draws while the machine is formed (a CNC's, a pump jack's). */
    public MemberBlock(Properties p, String kind, boolean drawnWhenFormed) {
        super(p);
        this.kind = kind;
        this.drawnWhenFormed = drawnWhenFormed;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override public String kind() { return kind; }

    @Override protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(BlockState s, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return drawnWhenFormed && s.getValue(FORMED) ? net.minecraft.world.phys.shapes.Shapes.empty() : super.getOcclusionShape(s, level, pos);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FORMED); }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) { Multiblocks.memberRemoved(server, pos); }
        super.onRemove(state, level, pos, newState, moved);
    }
}
