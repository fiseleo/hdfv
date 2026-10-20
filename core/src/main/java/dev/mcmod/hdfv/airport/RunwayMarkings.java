package dev.mcmod.hdfv.airport;

import java.util.HashMap;
import java.util.Map;

/**
 * The paint on a runway (0.20), after ICAO Annex 14 chapter 5.2, drawn on a 0.5 m grid (each pavement block is 2 x 2
 * cells; a block's mask says which are painted: bit 0 the cell at its smaller x and z, bit 1 larger x, bit 2 larger z,
 * bit 3 both):
 * <ul>
 *   <li>threshold: longitudinal stripes from 6 m to 36 m past each end, about 1.8 m wide, as many as the width calls
 *   for (Annex 14 5.2.4.6: 18 m 4, 23 m 6, 30 m 8, 45 m 12, 60 m 16), either side of a double space on the centre line
 *   and out to 3 m from the edges (at most 27 m from the centre line); the spaces shrink to fit on narrow runways.
 *   The whole patch is painted "threshold" (the blocks a tower finds a runway by);</li>
 *   <li>designation: the end's number (and L, C, R) 12 m past the stripes, digits 9 m long and 3 m wide, read from the
 *   approach;</li>
 *   <li>centre line: 30 m stripes with 20 m gaps between the two designations, 1 m wide;</li>
 *   <li>aiming point: a pair of broad stripes 45 m long, 150 m past the threshold on runways under 800 m, 250 m under
 *   1200 m, 300 m under 2400 m, else 400 m (Annex 14 table 5-1);</li>
 *   <li>side stripes: 1 m along each edge.</li>
 * </ul>
 * Everything is white. The 0.5 m grid rounds the real widths (0.9 m, 1.8 m) to its cells.
 */
public final class RunwayMarkings {
    private RunwayMarkings() {}

    public enum Paint { WHITE, YELLOW, THRESHOLD }

    /** A block's paint: what, which of its four cells, and (for a threshold) the way into the runway. */
    public record Mark(Paint paint, int mask, int dx, int dz) {}

    public static final double STRIPES_FROM_M = 6, STRIPES_TO_M = 36, STRIPE_M = 1.8, NUMBER_GAP_M = 12, DIGIT_LONG_M = 9,
            DIGIT_WIDE_M = 3, DIGIT_SPACE_M = 1.5, DASH_M = 30, GAP_M = 20, CENTRE_WIDE_M = 1, AIMING_LONG_M = 45, SIDE_M = 1;

    /** Threshold stripes for a runway width (m), Annex 14 5.2.4.6. */
    public static int stripes(double widthM) {
        if (widthM >= 60) { return 16; }
        if (widthM >= 45) { return 12; }
        if (widthM >= 30) { return 8; }
        if (widthM >= 23) { return 6; }
        return 4;
    }

    /** Where the aiming point begins past the threshold (m), Annex 14 table 5-1, by landing distance. */
    public static double aimingPointM(double lengthM) {
        if (lengthM < 800) { return 150; }
        if (lengthM < 1200) { return 250; }
        return lengthM < 2400 ? 300 : 400;
    }

    /** The paint of a runway: block (x, z) packed by {@link #key} to its mark. */
    public static Map<Long, Mark> of(Runway r) {
        Painter p = new Painter(r);
        double half = r.widthM() / 2, length = r.lengthM();
        for (Runway.End end : Runway.End.values()) {
            // the threshold patch (all of it "threshold"), its stripes white
            p.zone(end, 0, STRIPES_TO_M);
            // n stripes a side out to 3 m from the edge (at most 27 m from the centre line), the spaces between them
            // shrinking to fit as they must on narrow runways, the one on the centre line twice as wide
            int n = stripes(r.widthM()) / 2;
            double span = Math.min(half - 3, 27), wide = Math.min(STRIPE_M, span / n * 0.6), space = (span - n * wide) / n;
            for (int i = 0; i < n; i++) {
                double inner = space + i * (wide + space);
                p.rect(end, STRIPES_FROM_M, STRIPES_TO_M, inner, inner + wide, Paint.WHITE);
                p.rect(end, STRIPES_FROM_M, STRIPES_TO_M, -inner - wide, -inner, Paint.WHITE);
            }
            // the designation, read from the approach: the digits' bottoms toward the threshold
            String name = r.designator(end);
            String digits = name.substring(0, 2), letter = name.length() > 2 ? name.substring(2) : "";
            double base = STRIPES_TO_M + NUMBER_GAP_M;
            p.text(end, digits, base);
            if (!letter.isEmpty()) { p.text(end, letter, base + DIGIT_LONG_M + 6); }
            // the aiming point
            double aim = aimingPointM(length);
            if (aim + AIMING_LONG_M < length / 2) {
                double inner = r.widthM() < 30 ? 3 : 9, broad = r.widthM() < 30 ? 3 : 6;
                p.rect(end, aim, aim + AIMING_LONG_M, inner, inner + broad, Paint.WHITE);
                p.rect(end, aim, aim + AIMING_LONG_M, -inner - broad, -inner, Paint.WHITE);
            }
        }
        // the centre line between the two designations (with the letter's room), dashes from the LOW end
        double start = STRIPES_TO_M + NUMBER_GAP_M + DIGIT_LONG_M + NUMBER_GAP_M + (r.suffix().isEmpty() ? 0 : DIGIT_LONG_M + 6);
        for (double a = start; a + DASH_M <= length - start; a += DASH_M + GAP_M) {
            p.rect(Runway.End.LOW, a, a + DASH_M, -CENTRE_WIDE_M / 2, CENTRE_WIDE_M / 2, Paint.WHITE);
        }
        // side stripes along both edges, past the thresholds' stripes
        p.rect(Runway.End.LOW, 0, length, half - SIDE_M, half, Paint.WHITE);
        p.rect(Runway.End.LOW, 0, length, -half, -half + SIDE_M, Paint.WHITE);
        return p.marks();
    }

    public static long key(int x, int z) { return ((long) x & 0xFFFFFFFFL) | ((long) z << 32); }

    public static int keyX(long key) { return (int) key; }

    public static int keyZ(long key) { return (int) (key >> 32); }

    /** 3 x 9 glyphs (rows from the top), drawn twice as large: 3 m by 9 m. */
    private static final Map<Character, String[]> GLYPHS = Map.ofEntries(
            Map.entry('0', new String[]{"###", "#.#", "#.#", "#.#", "#.#", "#.#", "#.#", "#.#", "###"}),
            Map.entry('1', new String[]{".#.", "##.", ".#.", ".#.", ".#.", ".#.", ".#.", ".#.", "###"}),
            Map.entry('2', new String[]{"###", "..#", "..#", "..#", "###", "#..", "#..", "#..", "###"}),
            Map.entry('3', new String[]{"###", "..#", "..#", "..#", ".##", "..#", "..#", "..#", "###"}),
            Map.entry('4', new String[]{"#.#", "#.#", "#.#", "#.#", "###", "..#", "..#", "..#", "..#"}),
            Map.entry('5', new String[]{"###", "#..", "#..", "#..", "###", "..#", "..#", "..#", "###"}),
            Map.entry('6', new String[]{"###", "#..", "#..", "#..", "###", "#.#", "#.#", "#.#", "###"}),
            Map.entry('7', new String[]{"###", "..#", "..#", "..#", ".#.", ".#.", ".#.", ".#.", ".#."}),
            Map.entry('8', new String[]{"###", "#.#", "#.#", "#.#", "###", "#.#", "#.#", "#.#", "###"}),
            Map.entry('9', new String[]{"###", "#.#", "#.#", "#.#", "###", "..#", "..#", "..#", "###"}),
            Map.entry('L', new String[]{"#..", "#..", "#..", "#..", "#..", "#..", "#..", "#..", "###"}),
            Map.entry('C', new String[]{"###", "#..", "#..", "#..", "#..", "#..", "#..", "#..", "###"}),
            Map.entry('R', new String[]{"##.", "#.#", "#.#", "#.#", "##.", "#.#", "#.#", "#.#", "#.#"}));

    /** Paint on the 0.5 m grid, in runway terms (along from an end, offset to the right seen from that end). */
    private static final class Painter {
        private final Runway r;
        private final Map<Long, Paint> cells = new HashMap<>();
        private final Map<Long, int[]> zones = new HashMap<>();

        Painter(Runway r) { this.r = r; }

        /** World (x, z) of a point along from an end and offset to the right seen from it. */
        private double[] world(Runway.End end, double along, double offset) {
            double a = end == Runway.End.LOW ? r.low() + along : r.high() - along;
            double o = end == Runway.End.LOW ? offset : -offset;
            return r.axis() == Runway.Axis.X ? new double[]{a, r.cross() + o} : new double[]{r.cross() - o, a};
        }

        /** Paints the cells whose centres lie in a rectangle (along a0-a1, offset o0-o1 from an end), clipped to the runway. */
        void rect(Runway.End end, double a0, double a1, double o0, double o1, Paint paint) {
            double[] p = world(end, a0, o0), q = world(end, a1, o1);
            int sx0 = (int) Math.floor(Math.min(p[0], q[0]) * 2), sx1 = (int) Math.ceil(Math.max(p[0], q[0]) * 2);
            int sz0 = (int) Math.floor(Math.min(p[1], q[1]) * 2), sz1 = (int) Math.ceil(Math.max(p[1], q[1]) * 2);
            double minX = Math.min(p[0], q[0]), maxX = Math.max(p[0], q[0]), minZ = Math.min(p[1], q[1]), maxZ = Math.max(p[1], q[1]);
            for (int sx = sx0; sx < sx1; sx++) {
                for (int sz = sz0; sz < sz1; sz++) {
                    double cx = (sx + 0.5) / 2, cz = (sz + 0.5) / 2;
                    if (cx < minX || cx >= maxX || cz < minZ || cz >= maxZ || !r.over(cx, cz, 0)) { continue; }
                    cells.merge(key(sx, sz), paint, (old, now) -> old == Paint.THRESHOLD || now == Paint.THRESHOLD ? Paint.THRESHOLD : now);
                }
            }
        }

        /** Marks the blocks of a threshold patch (from the end, across the whole width), facing into the runway. */
        void zone(Runway.End end, double a0, double a1) {
            double[] p = world(end, a0, -r.widthM() / 2), q = world(end, a1, r.widthM() / 2);
            int x0 = (int) Math.floor(Math.min(p[0], q[0]) + 1e-9), x1 = (int) Math.ceil(Math.max(p[0], q[0]) - 1e-9);
            int z0 = (int) Math.floor(Math.min(p[1], q[1]) + 1e-9), z1 = (int) Math.ceil(Math.max(p[1], q[1]) - 1e-9);
            dev.mcmod.aerocore.api.Vec3d d = r.direction(end);
            for (int x = x0; x < x1; x++) for (int z = z0; z < z1; z++) { zones.put(key(x, z), new int[]{(int) d.x(), (int) d.z()}); }
        }

        /** A word along the centre line from an end, its glyphs' bottoms at along base, each glyph 3 m wide. */
        void text(Runway.End end, String word, double base) {
            double total = word.length() * DIGIT_WIDE_M + (word.length() - 1) * DIGIT_SPACE_M;
            for (int i = 0; i < word.length(); i++) {
                String[] g = GLYPHS.get(word.charAt(i));
                if (g == null) { continue; }
                // left edge as seen from the approach (left is a negative offset)
                double left = -total / 2 + i * (DIGIT_WIDE_M + DIGIT_SPACE_M);
                for (int row = 0; row < 9; row++) {
                    for (int col = 0; col < 3; col++) {
                        if (g[row].charAt(col) != '#') { continue; }
                        double top = base + DIGIT_LONG_M - row, o0 = left + col;
                        rect(end, top - 1, top, o0, o0 + 1, Paint.WHITE);
                    }
                }
            }
        }

        Map<Long, Mark> marks() {
            Map<Long, Mark> out = new HashMap<>();
            Map<Long, Integer> masks = new HashMap<>();
            for (Map.Entry<Long, Paint> c : cells.entrySet()) {
                int sx = keyX(c.getKey()), sz = keyZ(c.getKey());
                long block = key(Math.floorDiv(sx, 2), Math.floorDiv(sz, 2));
                int bit = (Math.floorMod(sx, 2)) | (Math.floorMod(sz, 2) << 1);
                masks.merge(block, 1 << bit, (a, b) -> a | b);
            }
            for (Map.Entry<Long, Integer> m : masks.entrySet()) { out.put(m.getKey(), new Mark(Paint.WHITE, m.getValue(), 0, 0)); }
            for (Map.Entry<Long, int[]> z : zones.entrySet()) {
                Mark old = out.get(z.getKey());
                out.put(z.getKey(), new Mark(Paint.THRESHOLD, old == null ? 0 : old.mask(), z.getValue()[0], z.getValue()[1]));
            }
            return out;
        }
    }
}
