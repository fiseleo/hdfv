package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.power.PowerPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.HashMap;
import java.util.Map;

/**
 * A metre of cable (0.8): light (6 mm^2) or heavy (95 mm^2) copper, joining cables and Forge Energy handlers beside it.
 * 0.21: a floor duct is a cable laid in a whole floor block under a cover plate (its model ignores the joins).
 */
public final class CableBlock extends Block {
    private static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    public final PowerPlant.CableKind kind;
    /** A whole floor block (0.21's duct), not a cable in the air. */
    public final boolean duct;
    private final VoxelShape core;
    private final Map<Direction, VoxelShape> arms = new HashMap<>();
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public CableBlock(Properties p, PowerPlant.CableKind kind) { this(p, kind, false); }

    public CableBlock(Properties p, PowerPlant.CableKind kind, boolean duct) {
        super(p);
        this.kind = kind;
        this.duct = duct;
        double a = kind == PowerPlant.CableKind.HEAVY ? 5.5 : 6.5, b = 16 - a;
        core = Block.box(a, a, a, b, b, b);
        arms.put(Direction.NORTH, Block.box(a, a, 0, b, b, a)); arms.put(Direction.SOUTH, Block.box(a, a, b, b, b, 16));
        arms.put(Direction.WEST, Block.box(0, a, a, a, b, b)); arms.put(Direction.EAST, Block.box(b, a, a, 16, b, b));
        arms.put(Direction.DOWN, Block.box(a, 0, a, b, a, b)); arms.put(Direction.UP, Block.box(a, b, a, b, 16, b));
        BlockState s = stateDefinition.any();
        for (BooleanProperty x : SIDES.values()) { s = s.setValue(x, false); }
        registerDefaultState(s);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { SIDES.values().forEach(b::add); }

    static boolean joins(LevelAccessor level, BlockPos pos, Direction d) {
        BlockPos n = pos.relative(d);
        if (level.getBlockState(n).getBlock() instanceof CableBlock) { return true; }
        return level instanceof Level l && l.getCapability(Capabilities.EnergyStorage.BLOCK, n, d.getOpposite()) != null;
    }

    private BlockState connected(LevelAccessor level, BlockPos pos, BlockState s) {
        for (Direction d : Direction.values()) { s = s.setValue(SIDES.get(d), joins(level, pos, d)); }
        return s;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return connected(c.getLevel(), c.getClickedPos(), defaultBlockState()); }

    @Override protected BlockState updateShape(BlockState s, Direction d, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return s.setValue(SIDES.get(d), joins(level, pos, d));
    }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext c) {
        if (duct) { return Shapes.block(); }
        return shapes.computeIfAbsent(s, st -> {
            VoxelShape v = core;
            for (Direction d : Direction.values()) { if (st.getValue(SIDES.get(d))) { v = Shapes.or(v, arms.get(d)); } }
            return v;
        });
    }

    @Override protected void onPlace(BlockState s, Level level, BlockPos pos, BlockState old, boolean moved) { if (!old.is(this)) { EnergyNetworks.changed(level); } }

    @Override protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!now.is(this)) { EnergyNetworks.changed(level); }
        super.onRemove(s, level, pos, now, moved);
    }
}
