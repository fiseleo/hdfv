package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.part.StorageBoxDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.25: a storage box's looks (the design screen and the world share them; its origin the middle of its bottom, its
 * length along x, the front +x): a roof box's streamlined clamshell lofted from rounded sections (high and full behind
 * the middle, its nose rounded up from the bars), the seam of its lid, its lock and the clamps under it; a bed box's
 * ribbed steel sides, its lid with a lip and its latches; a container's corrugated sides and roof between its rails and
 * corner posts, its corner castings, the two doors at its rear end with their four lock rods and handles. Looks only.
 */
public final class StorageBoxModel {
    private StorageBoxModel() {}

    private static final int DARK = 0xff1b1c1e, STEEL = 0xff8a9096, FRAME = 0xff3c4046, ROD = 0xffb9bfc5, LIGHT = 0xffd9dde1;

    /** The box's mesh. */
    public static void mesh(StorageBoxDesign d, PartMesh out) {
        int c = colour(d.colour());
        switch (d.kind()) {
            case ROOF_BOX -> roofBox(d, out, c);
            case BED_BOX -> bedBox(d, out, c);
            case CONTAINER -> container(d, out, c);
        }
        out.finish(null);
    }

    static int colour(String hex) {
        try { return 0xff000000 | Integer.parseInt(hex.replace("#", ""), 16); } catch (NumberFormatException e) { return STEEL; }
    }

    private static int shade(int c, double f) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * f), g = (int) Math.min(255, ((c >> 8) & 255) * f), b = (int) Math.min(255, (c & 255) * f);
        return 0xff000000 | r << 16 | g << 8 | b;
    }

    // ---------------------------------------------------------------- roof box

    /** Its top's height over the bottom at s along it (0 rear, 1 front), as a share of its height. */
    static double top(double s) { return s < 0.45 ? 0.55 + 0.45 * Math.sin(Math.PI / 2 * s / 0.45) : 0.35 + 0.65 * Math.sqrt(Math.max(0, 1 - Math.pow((s - 0.45) / 0.55, 2))); }

    /** Its underside's rise at s (the nose curls up), as a share of the height. */
    static double under(double s) { return s < 0.78 ? 0 : 0.3 * Math.pow((s - 0.78) / 0.22, 2); }

    /** Its half width at s, as a share of the half width (the nose narrows in plan). */
    static double half(double s) { return s < 0.7 ? (s < 0.08 ? 0.9 + 0.1 * s / 0.08 : 1) : Math.sqrt(1 - 0.6 * Math.pow((s - 0.7) / 0.3, 2)); }

    private static void roofBox(StorageBoxDesign d, PartMesh out, int c) {
        double l = d.lengthM(), w = d.widthM(), h = d.heightM();
        int stations = 18, around = 24;
        List<Vec3d[]> rings = new ArrayList<>();
        for (int i = 0; i <= stations; i++) {
            double s = (double) i / stations, x = -l / 2 + s * l, y0 = under(s) * h, y1 = Math.max(top(s) * h, y0 + 0.02), hz = half(s) * w / 2;
            Vec3d[] ring = new Vec3d[around];
            for (int k = 0; k < around; k++) {
                double a = 2 * Math.PI * k / around, ca = Math.cos(a), sa = Math.sin(a);
                double z = hz * Math.signum(ca) * Math.pow(Math.abs(ca), 0.5), y = 0.5 * (y0 + y1) + 0.5 * (y1 - y0) * Math.signum(sa) * Math.pow(Math.abs(sa), 0.5);
                ring[k] = new Vec3d(x, y, z);
            }
            rings.add(ring);
        }
        out.finish(Finishes.PAINT_GLOSS);
        loft(out, rings, c);
        // the lid's seam a little under the middle of the sides, the lock on the right side, the hinge line along the left
        out.finish(Finishes.PLASTIC);
        double seam = 0.38 * h;
        for (int side : new int[]{-1, 1}) {
            out.box(new Vec3d(-0.4 * l, seam - 0.004, side * (w / 2) - 0.004), new Vec3d(0.3 * l, seam + 0.004, side * (w / 2) + 0.004), DARK);
        }
        out.box(new Vec3d(-0.04, seam - 0.03, w / 2 - 0.002), new Vec3d(0.04, seam + 0.01, w / 2 + 0.012), shade(c, 1.6));
        out.finish(Finishes.BRUSHED_STEEL).cylinder(new Vec3d(0, seam - 0.01, w / 2 + 0.012), new Vec3d(0, seam - 0.01, w / 2 + 0.016), 0.008, 0.008, 10, LIGHT);
        // the clamps on the bars under it
        out.finish(Finishes.RUBBER);
        for (double x : new double[]{-0.25 * l, 0.22 * l}) {
            for (int side : new int[]{-1, 1}) {
                out.box(new Vec3d(x - 0.04, -0.02, side * 0.32 * w - 0.03), new Vec3d(x + 0.04, 0.002, side * 0.32 * w + 0.03), DARK);
            }
        }
    }

    /** Quads between rings of points (each ring the same count, round the x axis), its ends fanned shut; faces outward. */
    static void loft(PartMesh out, List<Vec3d[]> rings, int c) {
        for (int i = 0; i + 1 < rings.size(); i++) {
            Vec3d[] a = rings.get(i), b = rings.get(i + 1);
            for (int k = 0; k < a.length; k++) {
                int j = (k + 1) % a.length;
                quad(out, a[k], b[k], b[j], a[j], c, centre(a).add(centre(b)).scale(0.5));
            }
        }
        cap(out, rings.get(0), c);
        cap(out, rings.get(rings.size() - 1), c);
    }

    private static Vec3d centre(Vec3d[] ring) {
        Vec3d sum = Vec3d.ZERO;
        for (Vec3d p : ring) { sum = sum.add(p); }
        return sum.scale(1.0 / ring.length);
    }

    private static void cap(PartMesh out, Vec3d[] ring, int c) {
        Vec3d m = centre(ring);
        Vec3d inner = m.add(new Vec3d(m.x() < 0 ? 1 : -1, 0, 0));
        for (int k = 0; k < ring.length; k++) { quad(out, m, ring[k], ring[(k + 1) % ring.length], ring[(k + 1) % ring.length], c, inner); }
    }

    /** A quad facing away from a point inside (its normal from the diagonals; the corners reversed when it faced in). */
    static void quad(PartMesh out, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int colour, Vec3d inside) {
        Vec3d n = c.subtract(a).cross(d.subtract(b));
        if (n.lengthSquared() < 1e-18) { return; }
        Vec3d mid = a.add(b).add(c).add(d).scale(0.25);
        if (n.dot(mid.subtract(inside)) < 0) { out.quad(d, c, b, a, colour, n.scale(-1)); }
        else { out.quad(a, b, c, d, colour, n); }
    }

    // ---------------------------------------------------------------- bed box

    private static void bedBox(StorageBoxDesign d, PartMesh out, int c) {
        double l = d.lengthM(), w = d.widthM(), h = d.heightM(), lid = 0.06, rib = 0.012;
        out.finish(Finishes.PAINT);
        out.box(new Vec3d(-l / 2, 0, -w / 2), new Vec3d(l / 2, h - lid, w / 2), c);
        // ribs pressed into the long sides and the front and back faces
        int ribs = Math.max(2, (int) Math.floor(l / StorageBoxDesign.RIB_PITCH_M));
        for (int i = 1; i < ribs; i++) {
            double x = -l / 2 + l * i / ribs;
            for (int side : new int[]{-1, 1}) {
                out.box(new Vec3d(x - 0.02, 0.05, side * w / 2 - (side < 0 ? rib : 0)), new Vec3d(x + 0.02, h - lid - 0.05, side * w / 2 + (side > 0 ? rib : 0)), shade(c, 0.85));
            }
        }
        for (int end : new int[]{-1, 1}) {
            out.box(new Vec3d(end * l / 2 - (end < 0 ? rib : 0), 0.05, -w / 2 + 0.08), new Vec3d(end * l / 2 + (end > 0 ? rib : 0), h - lid - 0.05, w / 2 - 0.08), shade(c, 0.9));
        }
        // the lid, a lip a little over the body all round, its two latches at the front face and the hinge at the back
        out.box(new Vec3d(-l / 2 - 0.01, h - lid, -w / 2 - 0.01), new Vec3d(l / 2 + 0.01, h, w / 2 + 0.01), shade(c, 1.08));
        out.box(new Vec3d(-l / 2 + 0.05, h - 0.004, -w / 2 + 0.05), new Vec3d(l / 2 - 0.05, h + 0.004, w / 2 - 0.05), shade(c, 1.15));
        out.finish(Finishes.STAINLESS);
        for (double x : new double[]{-0.3 * l, 0.3 * l}) { out.box(new Vec3d(x - 0.05, h - lid - 0.05, w / 2), new Vec3d(x + 0.05, h - lid + 0.02, w / 2 + 0.018), LIGHT); }
        out.box(new Vec3d(-0.45 * l, h - lid - 0.015, -w / 2 - 0.016), new Vec3d(0.45 * l, h - lid + 0.005, -w / 2), ROD);
        out.finish(Finishes.RUBBER).box(new Vec3d(-l / 2 + 0.04, -0.01, -w / 2 + 0.04), new Vec3d(l / 2 - 0.04, 0.001, w / 2 - 0.04), DARK);
    }

    // ---------------------------------------------------------------- container

    private static void container(StorageBoxDesign d, PartMesh out, int c) {
        double l = d.lengthM(), w = d.widthM(), h = d.heightM(), rail = 0.16, post = 0.12, depth = 0.036, pitch = 0.278;
        out.finish(Finishes.PAINT);
        // the core box inset by the corrugations' depth, and the corrugations standing out of it on the sides and the roof
        out.box(new Vec3d(-l / 2 + post, rail, -w / 2 + depth), new Vec3d(l / 2 - post, h - rail, w / 2 - depth), shade(c, 0.92));
        int n = Math.max(1, (int) Math.floor((l - 2 * post) / pitch));
        double step = (l - 2 * post) / n;
        for (int i = 0; i < n; i++) {
            double x0 = -l / 2 + post + i * step + 0.3 * step, x1 = x0 + 0.45 * step;
            for (int side : new int[]{-1, 1}) {
                out.box(new Vec3d(x0, rail, side < 0 ? -w / 2 : w / 2 - depth), new Vec3d(x1, h - rail, side < 0 ? -w / 2 + depth : w / 2), c);
            }
            out.box(new Vec3d(x0, h - rail, -w / 2 + post), new Vec3d(x1, h - 0.02, w / 2 - post), c);
        }
        // the front end corrugated across
        int m = Math.max(1, (int) Math.floor((w - 2 * post) / pitch));
        double across = (w - 2 * post) / m;
        for (int i = 0; i < m; i++) {
            double z0 = -w / 2 + post + i * across + 0.3 * across;
            out.box(new Vec3d(l / 2 - post, rail, z0), new Vec3d(l / 2 - post + depth, h - rail, z0 + 0.45 * across), c);
        }
        // the frame: bottom and top rails along and across, the corner posts, the corner castings
        out.box(new Vec3d(-l / 2, 0, -w / 2), new Vec3d(l / 2, rail, -w / 2 + post), shade(c, 0.8));
        out.box(new Vec3d(-l / 2, 0, w / 2 - post), new Vec3d(l / 2, rail, w / 2), shade(c, 0.8));
        out.box(new Vec3d(-l / 2, h - rail, -w / 2), new Vec3d(l / 2, h, -w / 2 + post), shade(c, 0.85));
        out.box(new Vec3d(-l / 2, h - rail, w / 2 - post), new Vec3d(l / 2, h, w / 2), shade(c, 0.85));
        for (int end : new int[]{-1, 1}) {
            double x0 = end < 0 ? -l / 2 : l / 2 - post, x1 = x0 + post;
            out.box(new Vec3d(x0, 0, -w / 2), new Vec3d(x1, rail, w / 2), shade(c, 0.8));
            out.box(new Vec3d(x0, h - rail, -w / 2), new Vec3d(x1, h, w / 2), shade(c, 0.85));
            for (int side : new int[]{-1, 1}) {
                double z0 = side < 0 ? -w / 2 : w / 2 - post;
                out.box(new Vec3d(x0, rail, z0), new Vec3d(x1, h - rail, z0 + post), shade(c, 0.88));
            }
        }
        out.finish(Finishes.CAST_IRON);
        for (int end : new int[]{-1, 1}) for (int side : new int[]{-1, 1}) for (int up : new int[]{0, 1}) {
            double x0 = end < 0 ? -l / 2 - 0.002 : l / 2 - 0.178, z0 = side < 0 ? -w / 2 - 0.002 : w / 2 - 0.162, y0 = up == 0 ? -0.002 : h - 0.118;
            out.box(new Vec3d(x0, y0, z0), new Vec3d(x0 + 0.18, y0 + 0.12, z0 + 0.164), FRAME);
        }
        // the doors at the rear end (-x): two leaves, their seam, four lock rods with their cams and handles
        double doorX = -l / 2 + post - 0.01;
        out.finish(Finishes.PAINT).box(new Vec3d(doorX - 0.02, rail, -w / 2 + post), new Vec3d(doorX, h - rail, w / 2 - post), shade(c, 0.97));
        out.box(new Vec3d(doorX - 0.026, rail, -0.006), new Vec3d(doorX - 0.02, h - rail, 0.006), shade(c, 0.6));
        out.finish(Finishes.BRUSHED_STEEL);
        for (double z : new double[]{-0.36 * w, -0.14 * w, 0.14 * w, 0.36 * w}) {
            out.cylinder(new Vec3d(doorX - 0.05, rail - 0.04, z), new Vec3d(doorX - 0.05, h - rail + 0.04, z), 0.014, 0.014, 8, ROD);
            double toward = z < 0 ? 0.18 : -0.18, y = Math.min(1.05, 0.45 * h);
            out.box(new Vec3d(doorX - 0.06, y, Math.min(z - 0.01, z + toward)), new Vec3d(doorX - 0.035, y + 0.07, Math.max(z + 0.01, z + toward)), LIGHT);
        }
        out.finish(null);
    }
}
