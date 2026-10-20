package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Development (0.11): finds where a model's pieces pass through each other. The quads are grouped into pieces by
 * shared corners (a lathe, a tube, a box, a kit part placed whole each make one); then every edge of one piece is
 * tested against the triangles of the others it can reach (a grid of cells), and each pair that crosses is reported
 * with where the crossings lie and how far they spread compared with the smaller piece. A part sitting on a curved
 * surface crosses it over a little of its own size; a pipe through a case, over its whole girth twice.
 */
public final class ClipCheck {
    private ClipCheck() {}

    /**
     * Two pieces that cross: their colours and boxes, how many edge crossings, the box the crossings lie in, whether
     * either is tagged inside. A piece set into another at one end (a blade root, a bolt, a pipe ending at a case) crosses
     * it near that end only; one through or half sunk into another, along its length (through()).
     */
    public record Clash(int colourA, int colourB, Vec3d[] boxA, Vec3d[] boxB, int crossings, Vec3d centre, Vec3d[] crossed, boolean inside) {
        public double sizeA() { return boxA[1].subtract(boxA[0]).length(); }
        public double sizeB() { return boxB[1].subtract(boxB[0]).length(); }
        public double spread() { return crossed[1].subtract(crossed[0]).length(); }
        public double severity() { return spread() / Math.max(1e-9, Math.min(sizeA(), sizeB())); }

        /** The smaller piece's box. */
        public Vec3d[] smaller() { return sizeA() <= sizeB() ? boxA : boxB; }

        /** Where the crossings lie along the smaller piece's longest side (0 to 1 from its low end): {from, to}. */
        public double[] along() {
            Vec3d[] s = smaller();
            Vec3d e = s[1].subtract(s[0]);
            int axis = e.x() >= e.y() && e.x() >= e.z() ? 0 : e.y() >= e.z() ? 1 : 2;
            double lo = get(s[0], axis), ext = Math.max(1e-9, get(e, axis));
            return new double[]{(get(crossed[0], axis) - lo) / ext, (get(crossed[1], axis) - lo) / ext};
        }

        /** Whether the crossings reach past the smaller piece's end thirds (it passes through, or sits half sunk). */
        public boolean through() { double[] t = along(); return t[0] < 0.7 && t[1] > 0.3 && t[1] - t[0] > 0.15; }

        private static double get(Vec3d v, int axis) { return axis == 0 ? v.x() : axis == 1 ? v.y() : v.z(); }
    }

    private record Tri(int piece, Vec3d a, Vec3d b, Vec3d c) {}

    /** The clashes of a mesh, its stand-ins and decals left out (they lie over or on the rest on purpose), largest spread first. */
    public static List<Clash> check(MeshBuilder<?> mesh, double cell) {
        // 0.12: what is drawn nearest only (a body's far version, its skin whole and windows painted on, overlaps the near one by design)
        List<MeshBuilder.Quad> quads = mesh.quads().stream().filter(q -> (q.tags() & (MeshBuilder.STAND_IN | MeshBuilder.DECAL)) == 0 && q.near() == 0).toList();
        int n = quads.size();
        // pieces: union of quads sharing a corner
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) { parent[i] = i; }
        Map<Long, Integer> corner = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < 4; c++) {
                Integer j = corner.putIfAbsent(key(quads.get(i).corner(c)), i);
                if (j != null) { union(parent, i, j); }
            }
        }
        int[] piece = new int[n];
        Map<Integer, Integer> ids = new HashMap<>();
        for (int i = 0; i < n; i++) { piece[i] = ids.computeIfAbsent(find(parent, i), k -> ids.size()); }
        int pieces = ids.size();
        double[][] lo = new double[pieces][], hi = new double[pieces][];
        int[] colour = new int[pieces];
        boolean[] inside = new boolean[pieces];
        List<Tri> tris = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            MeshBuilder.Quad q = quads.get(i);
            int p = piece[i];
            colour[p] = q.color();
            inside[p] |= (q.tags() & MeshBuilder.INTERIOR) != 0;
            for (int c = 0; c < 4; c++) {
                Vec3d v = q.corner(c);
                if (lo[p] == null) { lo[p] = new double[]{v.x(), v.y(), v.z()}; hi[p] = new double[]{v.x(), v.y(), v.z()}; }
                lo[p][0] = Math.min(lo[p][0], v.x()); lo[p][1] = Math.min(lo[p][1], v.y()); lo[p][2] = Math.min(lo[p][2], v.z());
                hi[p][0] = Math.max(hi[p][0], v.x()); hi[p][1] = Math.max(hi[p][1], v.y()); hi[p][2] = Math.max(hi[p][2], v.z());
            }
            tris.add(new Tri(p, q.a(), q.b(), q.c()));
            if (!q.d().equals(q.c())) { tris.add(new Tri(p, q.a(), q.c(), q.d())); }
        }
        // triangles into cells
        Map<Long, List<Tri>> grid = new HashMap<>();
        for (Tri t : tris) {
            double[] l = {Math.min(t.a.x(), Math.min(t.b.x(), t.c.x())), Math.min(t.a.y(), Math.min(t.b.y(), t.c.y())), Math.min(t.a.z(), Math.min(t.b.z(), t.c.z()))};
            double[] h = {Math.max(t.a.x(), Math.max(t.b.x(), t.c.x())), Math.max(t.a.y(), Math.max(t.b.y(), t.c.y())), Math.max(t.a.z(), Math.max(t.b.z(), t.c.z()))};
            for (long x = cellOf(l[0], cell); x <= cellOf(h[0], cell); x++) {
                for (long y = cellOf(l[1], cell); y <= cellOf(h[1], cell); y++) {
                    for (long z = cellOf(l[2], cell); z <= cellOf(h[2], cell); z++) { grid.computeIfAbsent(cellKey(x, y, z), k -> new ArrayList<>()).add(t); }
                }
            }
        }
        // each triangle's edges against the other pieces' triangles in the cells the edge passes
        Map<Long, double[]> pairs = new HashMap<>();   // sum x, y, z, count, then min and max of x, y, z
        for (Tri t : tris) {
            Vec3d[][] edges = {{t.a, t.b}, {t.b, t.c}, {t.c, t.a}};
            for (Vec3d[] e : edges) {
                java.util.Set<Tri> seen = new java.util.HashSet<>();
                for (long key : cellsAlong(e[0], e[1], cell)) {
                    List<Tri> in = grid.get(key);
                    if (in == null) { continue; }
                    for (Tri o : in) {
                        if (o.piece == t.piece || !seen.add(o)) { continue; }
                        Vec3d hit = cross(e[0], e[1], o);
                        if (hit == null) { continue; }
                        int a = Math.min(t.piece, o.piece), b = Math.max(t.piece, o.piece);
                        double[] s = pairs.computeIfAbsent(((long) a << 32) | b, k -> new double[]{0, 0, 0, 0, 1e9, 1e9, 1e9, -1e9, -1e9, -1e9});
                        s[0] += hit.x(); s[1] += hit.y(); s[2] += hit.z(); s[3]++;
                        s[4] = Math.min(s[4], hit.x()); s[5] = Math.min(s[5], hit.y()); s[6] = Math.min(s[6], hit.z());
                        s[7] = Math.max(s[7], hit.x()); s[8] = Math.max(s[8], hit.y()); s[9] = Math.max(s[9], hit.z());
                    }
                }
            }
        }
        List<Clash> out = new ArrayList<>();
        for (Map.Entry<Long, double[]> e : pairs.entrySet()) {
            int a = (int) (e.getKey() >> 32), b = (int) (long) e.getKey();
            double[] s = e.getValue();
            out.add(new Clash(colour[a], colour[b], box(lo[a], hi[a]), box(lo[b], hi[b]), (int) s[3], new Vec3d(s[0] / s[3], s[1] / s[3], s[2] / s[3]),
                    new Vec3d[]{new Vec3d(s[4], s[5], s[6]), new Vec3d(s[7], s[8], s[9])}, inside[a] || inside[b]));
        }
        out.sort((x, y) -> Double.compare(y.spread(), x.spread()));
        return out;
    }

    /** Where a segment crosses a triangle (Moller-Trumbore), its ends excluded; null where it does not. */
    private static Vec3d cross(Vec3d p, Vec3d q, Tri t) {
        Vec3d d = q.subtract(p), e1 = t.b.subtract(t.a), e2 = t.c.subtract(t.a);
        Vec3d h = d.cross(e2);
        double det = e1.dot(h);
        if (Math.abs(det) < 1e-14) { return null; }
        double inv = 1 / det;
        Vec3d s = p.subtract(t.a);
        double u = s.dot(h) * inv;
        if (u < 1e-6 || u > 1 - 1e-6) { return null; }
        Vec3d qv = s.cross(e1);
        double v = d.dot(qv) * inv;
        if (v < 1e-6 || u + v > 1 - 1e-6) { return null; }
        double k = e2.dot(qv) * inv;
        if (k < 1e-6 || k > 1 - 1e-6) { return null; }
        return p.add(d.scale(k));
    }

    private static List<Long> cellsAlong(Vec3d a, Vec3d b, double cell) {
        double length = b.subtract(a).length();
        int steps = Math.max(1, (int) Math.ceil(length / (0.5 * cell)));
        java.util.LinkedHashSet<Long> out = new java.util.LinkedHashSet<>();
        for (int i = 0; i <= steps; i++) {
            Vec3d p = a.add(b.subtract(a).scale((double) i / steps));
            out.add(cellKey(cellOf(p.x(), cell), cellOf(p.y(), cell), cellOf(p.z(), cell)));
        }
        return new ArrayList<>(out);
    }

    private static long cellOf(double v, double cell) { return (long) Math.floor(v / cell); }
    private static long cellKey(long x, long y, long z) { return ((x & 0x1fffff) << 42) | ((y & 0x1fffff) << 21) | (z & 0x1fffff); }
    private static long key(Vec3d v) {
        long x = Math.round(v.x() * 1e5), y = Math.round(v.y() * 1e5), z = Math.round(v.z() * 1e5);
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
    }
    private static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }
    private static void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }
    private static double sq(double v) { return v * v; }
    private static Vec3d[] box(double[] lo, double[] hi) { return new Vec3d[]{new Vec3d(lo[0], lo[1], lo[2]), new Vec3d(hi[0], hi[1], hi[2])}; }
}
