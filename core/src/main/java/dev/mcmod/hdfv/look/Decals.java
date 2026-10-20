package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Finish;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.13: stickers (痛車貼紙) laid on a body's skin or a wing's surface as meshes. A sticker is cut into cells of at most
 * CELL_M (at most MOST_CELLS a side) in its picture, each corner carried onto the surface through a chart about the
 * sticker's middle: on a body x along it and the arc length round its section there (so a big sticker bends round the
 * body without stretching), on a wing the span and chord fractions. The picture stands upright where the surface is
 * upright (its up the world's up laid on the surface) and points forward where the surface faces up or down; it is
 * turned counter-clockwise seen from outside by its rotation and mirrored left to right when flipped. A pair is the
 * sticker's mirror image: a body's other side, a mirrored wing's other half, a single wing's (a fin's) other face.
 * <p>
 * The polygons are kept in the surface's parameters ((x, t) on a body, (eta, s) on a wing) with the picture's
 * coordinates at each corner (u to the right, v down, 0 to 1), and cut there: at the body's ends, the wing's edges and
 * root and tip, round a door (its panel's share on the door's piece, the seam's gap left bare) and round a control
 * surface (its share in its own mesh, relative to its hinge). They become quads only when written: lifted LIFT_M off
 * the surface, LAYER_M more each layer (more than the 6 mm within which the occlusion pass takes a skin quad for
 * covered, and the layers do not fight), with the sticker's picture as their finish (Finishes.decal), tagged decal.
 * Written only for the nearest two levels: farther away a sticker is a few pixels.
 */
public final class Decals {
    private Decals() {}

    /** Lift off the surface (m), and more for each layer. */
    public static final double LIFT_M = 0.007, LAYER_M = 0.00025;
    /** The largest cell of a sticker (m), and the most cells along a side (so at most 400 cells a sticker). */
    public static final double CELL_M = 0.05;
    public static final int MOST_CELLS = 20;
    private static final int WHITE = 0xffffffff;
    private static final double SMALL = 1e-12;

    /** A corner of a laid sticker: the surface's parameters (p, q) and the picture's coordinates (u to the right, v down, 0 to 1). */
    public record Corner(double p, double q, double u, double v) {}

    /**
     * A sticker as laid (or its pair): on a body its polygons in (x, t); on a wing in (eta, s) on a surface and side
     * (WingSurface's: -1 port, +1 starboard).
     */
    public record Laid(Sticker sticker, Sticker.Surface surface, int side, List<List<Corner>> polygons) {}

    // ---------------------------------------------------------------- bodies

    /**
     * A body's stickers into out: where the body has doors, the nearest level split by them (a door's share on its piece,
     * 1 plus its index among the layout's doors as BodyModel numbers them; the seam's gap bare) and level 1 whole on piece
     * 0; without doors, levels 0 to 1 whole. Out's pens are restored.
     */
    public static void body(MeshBuilder<?> out, BodySurface s, BodyLayout layout, List<Sticker> stickers, double gapM) {
        List<Laid> laid = lay(s, stickers);
        if (laid.isEmpty()) { return; }
        List<BodyLayout.Opening> doors = layout.openings.stream().filter(o -> o.type() == BodyLayout.Type.DOOR).toList();
        Finish pen = out.finish();
        int pens = out.tags();
        for (Laid l : laid) {
            double lift = lift(l.sticker());
            if (doors.isEmpty()) {
                out.pens(pen, pens).levels(0, 1).piece(0);
                writeBody(out, s, l, l.polygons(), lift);
                continue;
            }
            out.pens(pen, pens).levels(1, 1).piece(0);
            writeBody(out, s, l, l.polygons(), lift);
            List<List<Corner>> rest = new ArrayList<>(l.polygons());
            for (int k = 0; k < doors.size(); k++) {
                BodyLayout.Opening door = doors.get(k);
                BodyLayout.Opening panel = door.inset(gapM, s);
                double middle = door.middle()[1];
                List<List<Corner>> on = new ArrayList<>(), off = new ArrayList<>();
                for (List<Corner> poly : rest) {
                    List<Corner> near = nearAngle(poly, middle);
                    if (panel != null) { for (double[][] part : panel.parts()) { addIfAny(on, clipInside(near, part)); } }
                    List<List<Corner>> left = List.of(near);
                    for (double[][] part : door.parts()) {
                        List<List<Corner>> next = new ArrayList<>();
                        for (List<Corner> q : left) { next.addAll(subtract(q, part)); }
                        left = next;
                    }
                    off.addAll(left);
                }
                if (!on.isEmpty()) {
                    out.pens(pen, pens).levels(0, 0).piece(k + 1);
                    writeBody(out, s, l, on, lift);
                }
                rest = off;
            }
            out.pens(pen, pens).levels(0, 0).piece(0);
            writeBody(out, s, l, rest, lift);
        }
        out.pens(pen, pens);
    }

    /** A body's stickers (and their pairs) laid in (x, t), cut at the body's ends. Stickers placed on a wing are skipped. */
    public static List<Laid> lay(BodySurface s, List<Sticker> stickers) {
        List<Laid> out = new ArrayList<>();
        for (Sticker st : stickers) {
            if (!(st.place() instanceof Sticker.OnBody b)) { continue; }
            out.add(layBody(s, st, b.x(), b.t(), st.flip(), st.rotationDeg()));
            // the pair: mirrored through the body's middle plane (t to 2 pi - t), so mirrored and turned the other way
            if (st.pair()) { out.add(layBody(s, st, b.x(), 2 * Math.PI - b.t(), !st.flip(), -st.rotationDeg())); }
        }
        return out;
    }

    private static Laid layBody(BodySurface s, Sticker st, double x0, double t0, boolean flip, double rotationDeg) {
        x0 = Math.clamp(x0, s.rear(), s.front());
        // the arc length round the section at x0 from t0, half a turn each way: s(t), and t(s) from it
        int n = 720;
        double[] ts = new double[n + 1], arc = new double[n + 1];
        double dt = 2 * Math.PI / n;
        for (int k = 0; k <= n; k++) { ts[k] = t0 - Math.PI + k * dt; }
        int mid = n / 2;
        for (int k = mid + 1; k <= n; k++) { arc[k] = arc[k - 1] + 0.5 * dt * (BodyLayout.speed(s, x0, ts[k - 1]) + BodyLayout.speed(s, x0, ts[k])); }
        for (int k = mid - 1; k >= 0; k--) { arc[k] = arc[k + 1] - 0.5 * dt * (BodyLayout.speed(s, x0, ts[k + 1]) + BodyLayout.speed(s, x0, ts[k])); }
        // the chart's axes at the middle: per metre of x, per metre round
        double h = 1e-3, xa = Math.max(s.rear(), x0 - h), xb = Math.min(s.front(), x0 + h);
        Vec3d along = xb > xa ? s.point(xb, t0).subtract(s.point(xa, t0)).scale(1 / (xb - xa)) : Vec3d.X;
        Vec3d round = s.point(x0, t0 + h).subtract(s.point(x0, t0 - h)).scale(1 / (2 * h * BodyLayout.speed(s, x0, t0)));
        double[][] m = picture(s.normal(x0, t0), along, round, flip, rotationDeg);
        double x0f = x0;
        List<List<Corner>> polys = cells(st, (a, b) -> {
            double dx = m[0][0] * a + m[0][1] * b, ds = m[1][0] * a + m[1][1] * b;
            return new double[]{x0f + dx, angleAt(ts, arc, ds)};
        });
        List<List<Corner>> clipped = new ArrayList<>();
        for (List<Corner> poly : polys) {
            List<Corner> c = clipHalf(poly, 1, 0, -s.rear());          // x >= rear
            c = clipHalf(c, -1, 0, s.front());                          // x <= front
            addIfAny(clipped, c);
        }
        return new Laid(st, null, 0, clipped);
    }

    /** t at an arc length from the table (s monotonic), held at the ends (half a turn either way). */
    private static double angleAt(double[] ts, double[] arc, double length) {
        int n = arc.length - 1;
        if (length <= arc[0]) { return ts[0]; }
        if (length >= arc[n]) { return ts[n]; }
        int lo = 0, hi = n;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (arc[mid] <= length) { lo = mid; } else { hi = mid; }
        }
        double f = (length - arc[lo]) / (arc[hi] - arc[lo]);
        return ts[lo] + f * (ts[hi] - ts[lo]);
    }

    /** The polygon's angles moved by whole turns to lie about the given angle (a door's), so that it can be cut against the door. */
    private static List<Corner> nearAngle(List<Corner> poly, double about) {
        double mean = 0;
        for (Corner c : poly) { mean += c.q(); }
        mean /= poly.size();
        double shift = 2 * Math.PI * Math.round((about - mean) / (2 * Math.PI));
        if (shift == 0) { return poly; }
        List<Corner> out = new ArrayList<>(poly.size());
        for (Corner c : poly) { out.add(new Corner(c.p(), c.q() + shift, c.u(), c.v())); }
        return out;
    }

    private static void writeBody(MeshBuilder<?> out, BodySurface s, Laid l, List<List<Corner>> polys, double lift) {
        out.finish(Finishes.decal(l.sticker().image())).decal(true);
        for (List<Corner> poly : polys) {
            List<Vec3d> at = new ArrayList<>(poly.size()), normals = new ArrayList<>(poly.size());
            for (Corner c : poly) {
                Vec3d n = s.normal(c.p(), c.q());
                at.add(s.point(c.p(), c.q()).add(n.scale(lift)));
                normals.add(n);
            }
            write(out, poly, at, normals, Vec3d.ZERO);
        }
    }

    // ---------------------------------------------------------------- wings

    /** A wing's control surface's span strip, for stickers: its side, span fractions, hinge chord fraction. */
    public record Strip(int side, double eta0, double eta1, double hinge) {
        boolean holds(Laid l) { return l.side() == side; }
    }

    /** A wing's stickers (and their pairs) laid in (eta, s), cut at its root, tip and edges. Stickers placed on a body are skipped. */
    public static List<Laid> lay(WingSurface w, List<Sticker> stickers) {
        List<Laid> out = new ArrayList<>();
        for (Sticker st : stickers) {
            if (!(st.place() instanceof Sticker.OnWing on)) { continue; }
            int side = w.side(on.side());
            out.add(layWing(w, st, on.eta(), on.chord(), on.surface(), side, st.flip(), st.rotationDeg()));
            if (st.pair()) {
                // a mirrored wing's other half, a single wing's other face: mirrored, turned the other way
                boolean mirrored = w.planform.mirrored();
                Sticker.Surface surface = mirrored ? on.surface() : on.surface() == Sticker.Surface.UPPER ? Sticker.Surface.LOWER : Sticker.Surface.UPPER;
                out.add(layWing(w, st, on.eta(), on.chord(), surface, mirrored ? -side : side, !st.flip(), -st.rotationDeg()));
            }
        }
        return out;
    }

    private static Laid layWing(WingSurface w, Sticker st, double eta0, double s0, Sticker.Surface surface, int side, boolean flip, double rotationDeg) {
        double h = 1e-3, ea = Math.max(0, eta0 - h), eb = Math.min(1, eta0 + h), sa = Math.max(0, s0 - h), sb = Math.min(1, s0 + h);
        Vec3d span = w.point(eb, s0, surface, side).subtract(w.point(ea, s0, surface, side)).scale(1 / (eb - ea));
        Vec3d chord = w.point(eta0, sb, surface, side).subtract(w.point(eta0, sa, surface, side)).scale(1 / (sb - sa));
        double[][] m = picture(w.normal(eta0, s0, surface, side), span, chord, flip, rotationDeg);
        List<List<Corner>> polys = cells(st, (a, b) -> new double[]{eta0 + m[0][0] * a + m[0][1] * b, s0 + m[1][0] * a + m[1][1] * b});
        List<List<Corner>> clipped = new ArrayList<>();
        for (List<Corner> poly : polys) {
            List<Corner> c = clipHalf(poly, 1, 0, 0);                   // eta >= 0
            c = clipHalf(c, -1, 0, 1);                                  // eta <= 1
            c = clipHalf(c, 0, 1, 0);                                   // s >= 0
            c = clipHalf(c, 0, -1, 1);                                  // s <= 1
            addIfAny(clipped, c);
        }
        return new Laid(st, surface, side, clipped);
    }

    /** A wing's fixed share of its stickers (off every control surface's strip) into out, at levels 0 to 1; out's pens are restored. */
    public static void wingFixed(MeshBuilder<?> out, WingSurface w, List<Laid> laid, List<Strip> strips) {
        Finish pen = out.finish();
        int pens = out.tags();
        for (Laid l : laid) {
            List<List<Corner>> rest = l.polygons();
            for (Strip strip : strips) {
                if (!strip.holds(l)) { continue; }
                double[][] box = {{strip.eta0(), strip.hinge()}, {strip.eta1(), strip.hinge()}, {strip.eta1(), 1 + 1e-9}, {strip.eta0(), 1 + 1e-9}};
                List<List<Corner>> next = new ArrayList<>();
                for (List<Corner> poly : rest) { next.addAll(subtract(poly, box)); }
                rest = next;
            }
            out.pens(pen, pens).levels(0, 1);
            writeWing(out, w, l, rest, Vec3d.ZERO);
        }
        out.pens(pen, pens);
    }

    /**
     * A control surface strip's share of a wing's stickers into its mesh (built relative to its hinge: shift is
     * subtracted from every point), at levels 0 to 1; out's pens are restored.
     */
    public static void wingStrip(MeshBuilder<?> out, WingSurface w, List<Laid> laid, Strip strip, Vec3d shift) {
        Finish pen = out.finish();
        int pens = out.tags();
        double[][] box = {{strip.eta0(), strip.hinge()}, {strip.eta1(), strip.hinge()}, {strip.eta1(), 1 + 1e-9}, {strip.eta0(), 1 + 1e-9}};
        for (Laid l : laid) {
            if (!strip.holds(l)) { continue; }
            List<List<Corner>> on = new ArrayList<>();
            for (List<Corner> poly : l.polygons()) { addIfAny(on, clipInside(poly, box)); }
            if (on.isEmpty()) { continue; }
            out.pens(pen, pens).levels(0, 1);
            writeWing(out, w, l, on, shift);
        }
        out.pens(pen, pens);
    }

    private static void writeWing(MeshBuilder<?> out, WingSurface w, Laid l, List<List<Corner>> polys, Vec3d shift) {
        out.finish(Finishes.decal(l.sticker().image())).decal(true);
        double lift = lift(l.sticker());
        for (List<Corner> poly : polys) {
            List<Vec3d> at = new ArrayList<>(poly.size()), normals = new ArrayList<>(poly.size());
            for (Corner c : poly) {
                Vec3d n = w.normal(c.p(), c.q(), l.surface(), l.side());
                at.add(w.point(c.p(), c.q(), l.surface(), l.side()).add(n.scale(lift)));
                normals.add(n);
            }
            write(out, poly, at, normals, shift);
        }
    }

    // ---------------------------------------------------------------- shared

    /** How far a sticker stands off its surface. */
    public static double lift(Sticker st) { return LIFT_M + LAYER_M * Math.clamp(st.layer(), 0, Sticker.LAYERS - 1); }

    /**
     * The picture's frame on the surface as a map from its (right, up) metres to the chart's two parameters: up is the
     * world's up laid on the surface (forward, +x, where the surface faces up or down), right is up crossed with the
     * normal (so right, up and the normal turn as x, y, z: the picture reads from outside); flipped, right is mirrored;
     * then turned counter-clockwise about the normal. The chart's axes (a and b, per unit of each parameter) need not
     * be square: the picture's axes are written in them by least squares.
     */
    private static double[][] picture(Vec3d normal, Vec3d axisA, Vec3d axisB, boolean flip, double rotationDeg) {
        Vec3d n = normal.normalized();
        Vec3d up = Math.abs(n.y()) < 0.7 ? Vec3d.Y : Vec3d.X;
        up = up.subtract(n.scale(n.dot(up))).normalized();
        Vec3d right = up.cross(n).normalized();
        // a picture point (a, b) lies at a' r0 + b u0 with a' = -a when flipped, (r0, u0) the frame turned by the rotation
        double th = Math.toRadians(rotationDeg), c = Math.cos(th), sn = Math.sin(th);
        Vec3d r = right.scale(c).add(up.scale(sn)), u = up.scale(c).subtract(right.scale(sn));
        if (flip) { r = r.scale(-1); }
        double[] rc = solve(axisA, axisB, r), uc = solve(axisA, axisB, u);
        return new double[][]{{rc[0], uc[0]}, {rc[1], uc[1]}};
    }

    /** v as alpha a + beta b, least squares. */
    private static double[] solve(Vec3d a, Vec3d b, Vec3d v) {
        double aa = a.dot(a), ab = a.dot(b), bb = b.dot(b), av = a.dot(v), bv = b.dot(v), det = aa * bb - ab * ab;
        if (Math.abs(det) < 1e-30) { return new double[]{0, 0}; }
        return new double[]{(av * bb - ab * bv) / det, (aa * bv - ab * av) / det};
    }

    private interface Chart { double[] at(double right, double up); }

    /** The sticker's cells (corners counter-clockwise in the picture: left-top, right-top, right-bottom... as right and up), each carried by the chart. */
    private static List<List<Corner>> cells(Sticker st, Chart chart) {
        double w = st.widthM(), h = st.widthM() * st.aspect();
        int na = Math.clamp((int) Math.ceil(w / CELL_M - 1e-9), 1, MOST_CELLS), nb = Math.clamp((int) Math.ceil(h / CELL_M - 1e-9), 1, MOST_CELLS);
        Corner[][] grid = new Corner[na + 1][nb + 1];
        for (int i = 0; i <= na; i++) {
            for (int j = 0; j <= nb; j++) {
                double u = (double) i / na, v = (double) j / nb;
                double[] pq = chart.at(-w / 2 + w * u, h / 2 - h * v);
                grid[i][j] = new Corner(pq[0], pq[1], u, v);
            }
        }
        List<List<Corner>> out = new ArrayList<>(na * nb);
        for (int i = 0; i < na; i++) {
            for (int j = 0; j < nb; j++) { out.add(List.of(grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j], grid[i][j])); }
        }
        return out;
    }

    /** The part of a polygon where a p + b q + c >= 0 (Sutherland-Hodgman; the picture's coordinates interpolated). */
    static List<Corner> clipHalf(List<Corner> poly, double a, double b, double c) {
        if (poly.isEmpty()) { return poly; }
        List<Corner> out = new ArrayList<>(poly.size() + 2);
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Corner p = poly.get(i), q = poly.get((i + 1) % n);
            double fp = a * p.p() + b * p.q() + c, fq = a * q.p() + b * q.q() + c;
            if (fp >= 0) { out.add(p); }
            if ((fp >= 0) != (fq >= 0)) { out.add(lerp(p, q, fp / (fp - fq))); }
        }
        return out;
    }

    private static Corner lerp(Corner p, Corner q, double f) {
        return new Corner(p.p() + f * (q.p() - p.p()), p.q() + f * (q.q() - p.q()), p.u() + f * (q.u() - p.u()), p.v() + f * (q.v() - p.v()));
    }

    /** The half-plane left of the edge a to b (a convex polygon counter-clockwise in (p, q) is the meet of its edges' left sides). */
    private static List<Corner> left(List<Corner> poly, double[] from, double[] to, boolean keepLeft) {
        double ex = to[0] - from[0], eq = to[1] - from[1];
        // left: ex (q - from_q) - eq (p - from_p) >= 0
        double a = -eq, b = ex, c = eq * from[0] - ex * from[1];
        return keepLeft ? clipHalf(poly, a, b, c) : clipHalf(poly, -a, -b, -c);
    }

    /** A polygon within a convex polygon (counter-clockwise in (p, q)). */
    static List<Corner> clipInside(List<Corner> poly, double[][] convex) {
        List<Corner> out = poly;
        for (int i = 0; i < convex.length && !out.isEmpty(); i++) { out = left(out, convex[i], convex[(i + 1) % convex.length], true); }
        return out;
    }

    /** A polygon less a convex polygon (counter-clockwise): pieces, each outside one edge and inside the edges before it. */
    static List<List<Corner>> subtract(List<Corner> poly, double[][] convex) {
        List<List<Corner>> out = new ArrayList<>();
        List<Corner> inside = poly;
        for (int i = 0; i < convex.length && !inside.isEmpty(); i++) {
            double[] a = convex[i], b = convex[(i + 1) % convex.length];
            if (Math.hypot(b[0] - a[0], b[1] - a[1]) < SMALL) { continue; }
            addIfAny(out, left(inside, a, b, false));
            inside = left(inside, a, b, true);
        }
        return out;
    }

    private static void addIfAny(List<List<Corner>> out, List<Corner> poly) {
        if (poly.size() < 3) { return; }
        double area = 0;
        for (int i = 0; i < poly.size(); i++) {
            Corner p = poly.get(i), q = poly.get((i + 1) % poly.size());
            area += p.p() * q.q() - q.p() * p.q();
        }
        if (Math.abs(area) > SMALL) { out.add(poly); }
    }

    /**
     * A polygon as quads (a fan, a triangle's last corner doubled), wound counter-clockwise about its normals (seen from
     * outside), white, with each corner's normal and the picture's coordinates (the finish's tile is 1, so these are
     * texture coordinates).
     */
    private static void write(MeshBuilder<?> out, List<Corner> poly, List<Vec3d> at, List<Vec3d> normals, Vec3d shift) {
        int n = poly.size();
        Vec3d facing = Vec3d.ZERO, mean = Vec3d.ZERO;
        for (int i = 0; i < n; i++) {
            Vec3d p = at.get(i), q = at.get((i + 1) % n);
            facing = facing.add(new Vec3d((p.y() - q.y()) * (p.z() + q.z()), (p.z() - q.z()) * (p.x() + q.x()), (p.x() - q.x()) * (p.y() + q.y())));
            mean = mean.add(normals.get(i));
        }
        int[] order = new int[n];
        boolean reverse = facing.dot(mean) < 0;
        for (int i = 0; i < n; i++) { order[i] = reverse ? n - 1 - i : i; }
        for (int k = 1; k + 1 < n; k += 2) {
            int i0 = order[0], i1 = order[k], i2 = order[k + 1], i3 = order[Math.min(k + 2, n - 1)];
            if (k + 2 > n - 1) { i3 = i2; }
            int[] idx = {i0, i1, i2, i3};
            Vec3d[] corners = new Vec3d[4], ns = new Vec3d[4];
            float[] uv = new float[8];
            Vec3d sum = Vec3d.ZERO;
            for (int j = 0; j < 4; j++) {
                corners[j] = at.get(idx[j]).subtract(shift);
                ns[j] = normals.get(idx[j]);
                sum = sum.add(ns[j]);
                uv[2 * j] = (float) poly.get(idx[j]).u();
                uv[2 * j + 1] = (float) poly.get(idx[j]).v();
            }
            out.quad(corners[0], corners[1], corners[2], corners[3], WHITE, sum, ns, new int[]{WHITE, WHITE, WHITE, WHITE}, uv);
        }
    }
}
