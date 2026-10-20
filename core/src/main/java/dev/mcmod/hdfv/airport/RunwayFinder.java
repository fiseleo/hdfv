package dev.mcmod.hdfv.airport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds runways from their threshold markings (0.20). A threshold is a patch of pavement blocks painted as one (the
 * runway marking blocks with paint "threshold"), each facing into the runway; a single row across the runway is
 * enough. Touching blocks with the same facing make one threshold: its extent across is the runway's width, its
 * middle the centre line, its outer edge (behind its facing) the runway's end. Two thresholds facing each other on
 * the same axis, their centre lines within {@link #ALIGN_M} and widths within {@link #WIDTH_M}, make a runway; the
 * nearest such pair is taken first. Parallel runways with the same numbers get L, C, R.
 */
public final class RunwayFinder {
    private RunwayFinder() {}

    /** How far apart two thresholds' centre lines and widths may be (m) and still be one runway's ends. */
    public static final double ALIGN_M = 2.5, WIDTH_M = 4;
    /** The longest runway looked for (m). */
    public static final double MAX_LENGTH_M = 6_000;

    /** A threshold-painted block: where it is and which way it faces (dx, dz a unit step along X or Z). */
    public record Cell(int x, int y, int z, int dx, int dz) {
        public Cell {
            if (Math.abs(dx) + Math.abs(dz) != 1) { throw new IllegalArgumentException("A threshold faces along X or Z"); }
        }
    }

    /** One threshold patch: its axis and facing (+1 or -1 along the axis), extent across and along (block edges), surface height. */
    record Patch(Runway.Axis axis, int facing, double crossMin, double crossMax, double alongMin, double alongMax, double y) {
        double cross() { return 0.5 * (crossMin + crossMax); }
        double width() { return crossMax - crossMin; }
        /** The runway's end: the edge behind the facing. */
        double outer() { return facing > 0 ? alongMin : alongMax; }
    }

    public static List<Runway> find(Iterable<Cell> cells) {
        List<Patch> patches = patches(cells);
        List<Patch> lows = new ArrayList<>(), highs = new ArrayList<>();
        for (Patch p : patches) { (p.facing() > 0 ? lows : highs).add(p); }
        // every facing pair that could be a runway, shortest first
        record Pair(Patch low, Patch high, double length) {}
        List<Pair> pairs = new ArrayList<>();
        for (Patch a : lows) {
            for (Patch b : highs) {
                if (a.axis() != b.axis() || b.outer() <= a.outer()) { continue; }
                double length = b.outer() - a.outer();
                if (length > MAX_LENGTH_M || Math.abs(a.cross() - b.cross()) > ALIGN_M || Math.abs(a.width() - b.width()) > WIDTH_M) { continue; }
                pairs.add(new Pair(a, b, length));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::length));
        List<Runway> out = new ArrayList<>();
        java.util.Set<Patch> used = new java.util.HashSet<>();
        for (Pair p : pairs) {
            if (used.contains(p.low()) || used.contains(p.high())) { continue; }
            used.add(p.low());
            used.add(p.high());
            out.add(new Runway(p.low().axis(), 0.5 * (p.low().cross() + p.high().cross()), p.low().outer(), p.high().outer(),
                    Math.max(p.low().y(), p.high().y()), Math.max(p.low().width(), p.high().width()), ""));
        }
        return lettered(out);
    }

    /** How far apart (m, across) parallel runways can be and still be one airfield's (lettered together). */
    static final double PARALLEL_M = 1_500;

    /**
     * Parallel runways of one airfield (same axis, overlapping along, within {@link #PARALLEL_M} across): L, C, R from the
     * left of a take-off from the LOW end. (0.20 lettered every parallel runway of the world together: two airports
     * kilometres apart became 09L and 09R.)
     */
    static List<Runway> lettered(List<Runway> runways) {
        List<Runway> out = new ArrayList<>();
        for (Runway r : runways) {
            List<Runway> same = new ArrayList<>();
            for (Runway o : runways) {
                if (o.axis() == r.axis() && Math.abs(o.cross() - r.cross()) <= PARALLEL_M && o.low() < r.high() && r.low() < o.high()) { same.add(o); }
            }
            if (same.size() < 2 || same.size() > 3) { out.add(r); continue; }
            // left of a take-off from LOW: X runways (heading east) to the north, smaller z; Z runways (heading south) to the east, larger x
            List<Runway> order = new ArrayList<>(same);
            order.sort(Comparator.comparingDouble(x -> x.axis() == Runway.Axis.X ? x.cross() : -x.cross()));
            int i = order.indexOf(r);
            String letter = same.size() == 2 ? (i == 0 ? "L" : "R") : new String[]{"L", "C", "R"}[i];
            out.add(new Runway(r.axis(), r.cross(), r.low(), r.high(), r.y(), r.widthM(), letter));
        }
        return out;
    }

    /** Touching cells with the same facing (and within a block of height) make patches. */
    static List<Patch> patches(Iterable<Cell> cells) {
        Map<Long, Cell> byColumn = new HashMap<>();
        for (Cell c : cells) { byColumn.put(key(c.x(), c.z(), c.dx(), c.dz()), c); }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        List<Patch> out = new ArrayList<>();
        for (Map.Entry<Long, Cell> e : byColumn.entrySet()) {
            if (!seen.add(e.getKey())) { continue; }
            Cell first = e.getValue();
            int minX = first.x(), maxX = first.x(), minZ = first.z(), maxZ = first.z(), top = first.y();
            Deque<Cell> queue = new ArrayDeque<>();
            queue.add(first);
            while (!queue.isEmpty()) {
                Cell c = queue.poll();
                minX = Math.min(minX, c.x()); maxX = Math.max(maxX, c.x()); minZ = Math.min(minZ, c.z()); maxZ = Math.max(maxZ, c.z());
                top = Math.max(top, c.y());
                for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long k = key(c.x() + n[0], c.z() + n[1], first.dx(), first.dz());
                    Cell next = byColumn.get(k);
                    if (next != null && Math.abs(next.y() - c.y()) <= 1 && seen.add(k)) { queue.add(next); }
                }
            }
            boolean alongX = first.dx() != 0;
            out.add(alongX
                    ? new Patch(Runway.Axis.X, first.dx(), minZ, maxZ + 1, minX, maxX + 1, top + 1)
                    : new Patch(Runway.Axis.Z, first.dz(), minX, maxX + 1, minZ, maxZ + 1, top + 1));
        }
        return out;
    }

    private static long key(int x, int z, int dx, int dz) {
        int facing = dx > 0 ? 0 : dx < 0 ? 1 : dz > 0 ? 2 : 3;
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | ((long) facing << 52);
    }
}
