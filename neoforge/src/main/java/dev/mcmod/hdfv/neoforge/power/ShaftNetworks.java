package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.power.ShaftLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Line shaft networks (0.8): line shafts joined to one another, and the shaft ends (block entities implementing
 * ShaftEnd) beside them or beside one another (machines touching are belted together). Found by a breadth-first search
 * (at most MAX_BLOCKS), kept until a shaft or an end in the level comes or goes (an epoch); a network's speed carries
 * over to the network that replaces it. The first end to tick in a game tick runs the network's tick
 * (power.ShaftLine) for all of them.
 */
public final class ShaftNetworks {
    private ShaftNetworks() {}

    public static final int MAX_BLOCKS = 4096;
    public static final double TICK_S = 0.05;

    private static final class Cache {
        int epoch;
        final Map<Long, Network> byPos = new HashMap<>();
        final Map<Long, Double> speeds = new HashMap<>();
    }
    private static final Map<Level, Cache> CACHE = new WeakHashMap<>();
    private static final Map<Level, Integer> EPOCH = new WeakHashMap<>();

    /** A shaft or an end came or went. */
    public static synchronized void changed(Level level) { EPOCH.merge(level, 1, Integer::sum); }

    /** A network: its ends, how many shaft blocks, its speed (rpm). */
    public static final class Network {
        public final List<BlockPos> ends;
        public final int shafts;
        final long id;
        double rpm;
        long ticked = Long.MIN_VALUE;
        ShaftLine.Result last;

        Network(List<BlockPos> ends, int shafts, long id) { this.ends = ends; this.shafts = shafts; this.id = id; }

        public double rpm() { return rpm; }
        public ShaftLine.Result last() { return last; }

        void tick(Level level) {
            List<ShaftEnd> parts = new ArrayList<>();
            for (BlockPos p : ends) { if (level.getBlockEntity(p) instanceof ShaftEnd e && e.onShaft()) { parts.add(e); } }
            List<ShaftLine.End> line = new ArrayList<>();
            double stand = 0, standRated = 0;
            int stands = 0;
            for (ShaftEnd e : parts) {
                line.add(e.lineEnd(rpm));
                double s = e.standRpm();
                if (!Double.isNaN(s)) { stand += s; stands++; standRated += Math.max(e.standRatedW(), 1); }
            }
            ShaftLine.Result r = ShaftLine.step(rpm, line, shafts, TICK_S, stands > 0 ? stand / stands : Double.NaN);
            for (int i = 0; i < parts.size(); i++) {
                ShaftEnd e = parts.get(i);
                double share = !Double.isNaN(e.standRpm()) ? r.standsW() * Math.max(e.standRatedW(), 1) / standRated : 0;
                e.settle(r.rpm(), r.givenW()[i], r.gotW()[i], share);
            }
            rpm = r.rpm();
            last = r;
        }
    }

    /** The network an end is on (found or kept). */
    public static synchronized Network of(Level level, BlockPos end) {
        int epoch = EPOCH.getOrDefault(level, 0);
        Cache c = CACHE.computeIfAbsent(level, l -> new Cache());
        if (c.epoch != epoch) {
            for (Network n : new HashSet<>(c.byPos.values())) { c.speeds.put(n.id, n.rpm); }
            c.byPos.clear();
            c.epoch = epoch;
        }
        Network n = c.byPos.get(end.asLong());
        if (n == null) {
            n = scan(level, end);
            Double kept = c.speeds.get(n.id);
            if (kept != null) { n.rpm = kept; }
            for (BlockPos p : n.ends) { c.byPos.put(p.asLong(), n); }
        }
        return n;
    }

    /** Runs an end's network's tick, once a game tick. */
    public static void tick(Level level, BlockPos end) {
        Network n = of(level, end);
        long now = level.getGameTime();
        if (n.ticked != now) { n.ticked = now; n.tick(level); }
    }

    static boolean isShaft(Level level, BlockPos p) { return level.getBlockState(p).getBlock() instanceof ShaftBlock; }
    static boolean isEnd(Level level, BlockPos p) { return level.getBlockEntity(p) instanceof ShaftEnd e && e.onShaft(); }

    static Network scan(Level level, BlockPos start) {
        List<BlockPos> ends = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        int shafts = 0;
        long id = start.asLong();
        while (!queue.isEmpty() && seen.size() <= MAX_BLOCKS) {
            BlockPos p = queue.poll();
            if (isShaft(level, p)) { shafts++; } else if (isEnd(level, p)) { ends.add(p); }
            id = Math.min(id, p.asLong());
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d).immutable();
                if (seen.contains(n)) { continue; }
                if (isShaft(level, n) || isEnd(level, n)) { seen.add(n); queue.add(n); }
            }
        }
        if (!ends.contains(start)) { ends.add(0, start.immutable()); }
        return new Network(ends, shafts, id);
    }
}
