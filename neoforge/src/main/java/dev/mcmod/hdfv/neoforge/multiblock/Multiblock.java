package dev.mcmod.hdfv.neoforge.multiblock;

import dev.mcmod.hdfv.structure.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * A controller's multiblock (0.8), held by its block entity: the pattern, and its match while formed. The controller
 * looks again every CHECK_TICKS and when used (cheap: a few dozen block reads); forming marks its members FORMED and
 * tells Multiblocks who they belong to, turning the controller to the way the machine faces; a member taken away
 * dissolves it at once. Nothing else is kept: a loaded controller looks again on its first tick, and its own work
 * (progress, items) stays with it while it is dissolved.
 */
public final class Multiblock {
    public static final int CHECK_TICKS = 40;
    private final Pattern pattern;
    private final BlockPos controller;
    private Pattern.Match match;
    private boolean looked;
    private int clock;

    public Multiblock(Pattern pattern, BlockPos controller) { this.pattern = pattern; this.controller = controller.immutable(); }

    public Pattern pattern() { return pattern; }
    public Pattern.Match match() { return match; }
    public boolean formed() { return match != null; }

    /** Each server tick: looks on the first and every CHECK_TICKS after. */
    public void tick(ServerLevel level) {
        if (!looked || ++clock >= CHECK_TICKS) { clock = 0; check(level); }
    }

    /** Looks now: forms, keeps or dissolves; whether it is formed. */
    public boolean check(ServerLevel level) {
        looked = true;
        BlockState cs = level.getBlockState(controller);
        boolean faces = cs.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
        int facing = faces ? cs.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue() : 0;
        Pattern.Match m = pattern.matchAnyFacing(Multiblocks.world(level), controller.getX(), controller.getY(), controller.getZ(), facing);
        if (m == null) {
            dissolve(level);
            return false;
        }
        if (match != null && match.cells().equals(m.cells())) {
            match = m;
            return true;
        }
        if (match != null) { unmark(level, match, positions(m)); }
        match = m;
        for (Pattern.Cell c : m.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (p.equals(controller)) { continue; }
            Multiblocks.put(level, p, controller);
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(MemberBlock.FORMED) && !s.getValue(MemberBlock.FORMED)) { level.setBlock(p, s.setValue(MemberBlock.FORMED, true), Block.UPDATE_CLIENTS); }
        }
        if (faces && m.facing() != facing) { level.setBlock(controller, cs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(m.facing())), Block.UPDATE_ALL); }
        return true;
    }

    /** Dissolves it (a member gone, the controller broken): members no longer FORMED. */
    public void dissolve(ServerLevel level) {
        if (match == null) { return; }
        Pattern.Match was = match;
        match = null;
        unmark(level, was, Set.of());
    }

    private Set<BlockPos> positions(Pattern.Match m) {
        Set<BlockPos> out = new HashSet<>();
        for (Pattern.Cell c : m.cells()) { out.add(new BlockPos(c.x(), c.y(), c.z())); }
        return out;
    }

    private void unmark(ServerLevel level, Pattern.Match m, Set<BlockPos> keep) {
        for (Pattern.Cell c : m.cells()) {
            BlockPos p = new BlockPos(c.x(), c.y(), c.z());
            if (p.equals(controller) || keep.contains(p)) { continue; }
            Multiblocks.remove(level, p, controller);
            BlockState s = level.getBlockState(p);
            if (s.hasProperty(MemberBlock.FORMED) && s.getValue(MemberBlock.FORMED)) { level.setBlock(p, s.setValue(MemberBlock.FORMED, false), Block.UPDATE_CLIENTS); }
        }
    }
}
