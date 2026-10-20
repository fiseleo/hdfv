package dev.mcmod.hdfv.neoforge.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Pipe networks (0.8): the pipes joined from a pump's side (a breadth-first search, at most MAX_PIPES) and the fluid
 * handlers they reach. A pump keeps what it found until a pipe or a tank in the level changes (an epoch per level) or
 * RESCAN_TICKS pass (another mod's block may have come or gone).
 */
public final class PipeNetworks {
    private PipeNetworks() {}

    public static final int MAX_PIPES = 4096, RESCAN_TICKS = 100;
    private static final Map<Level, Integer> EPOCH = new WeakHashMap<>();

    /** A pipe or a fluid block came or went. */
    public static synchronized void changed(Level level) { EPOCH.merge(level, 1, Integer::sum); }

    public static synchronized int epoch(Level level) { return EPOCH.getOrDefault(level, 0); }

    /** A fluid handler a network reaches: where, from which side, its height. */
    public record End(BlockPos pos, Direction side) {
        public IFluidHandler handler(Level level) { return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side); }
    }

    /** What a side of a block reaches: the pipes (how many) and the handlers at their ends. */
    public record Network(int pipes, List<End> ends) {
        public double meanY() { return ends.stream().mapToInt(e -> e.pos().getY()).average().orElse(0); }
    }

    /** The network on a side of a block (from is the block's own position, side the way out of it). */
    public static Network scan(Level level, BlockPos from, Direction side) {
        BlockPos first = from.relative(side);
        List<End> ends = new ArrayList<>();
        if (!(level.getBlockState(first).getBlock() instanceof FluidPipeBlock)) {
            if (level.getCapability(Capabilities.FluidHandler.BLOCK, first, side.getOpposite()) != null) { ends.add(new End(first, side.getOpposite())); }
            return new Network(0, ends);
        }
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> endsSeen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(first);
        seen.add(first);
        while (!queue.isEmpty() && seen.size() <= MAX_PIPES) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.equals(from) || seen.contains(n)) { continue; }
                if (level.getBlockState(n).getBlock() instanceof FluidPipeBlock) {
                    seen.add(n);
                    queue.add(n);
                } else if (!endsSeen.contains(n) && level.getCapability(Capabilities.FluidHandler.BLOCK, n, d.getOpposite()) != null) {
                    endsSeen.add(n);
                    ends.add(new End(n.immutable(), d.getOpposite()));
                }
            }
        }
        return new Network(seen.size(), ends);
    }
}
