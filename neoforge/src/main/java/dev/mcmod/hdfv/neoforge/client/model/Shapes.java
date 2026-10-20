package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;

/** Shapes and finishes the engine models share: outlines, gears, belts, blades, colours of metals and heat. */
final class Shapes {
    private Shapes() {}

    static final int HIGHLIGHT = 0xffffa53a;

    /** The colour a material is drawn in, as cast or forged; machined surfaces are lighter (machined). */
    static int material(EngineMaterial m) {
        String n = m.name();
        if (n.startsWith("aluminium")) { return 0xffb9bdc2; }
        if (n.startsWith("magnesium")) { return 0xffa9ad9f; }
        if (n.equals("grey_iron")) { return 0xff5c5f63; }
        if (n.equals("ductile_iron")) { return 0xff676a6f; }
        if (n.startsWith("titanium")) { return 0xffa39d92; }
        if (n.startsWith("stainless")) { return 0xffaeb2b7; }
        if (n.startsWith("inconel")) { return 0xff80776c; }
        if (n.equals("cmsx_4")) { return 0xff8c7b68; }
        if (n.equals("valve_steel")) { return 0xffa9afb7; }
        if (n.startsWith("steel")) { return 0xff858b93; }
        return 0xff9a9a9a;
    }

    static int machined(int argb) { return mix(argb, 0xffe2e6ea, 0.45); }
    static int darker(int argb, double f) { return mix(argb, 0xff000000, f); }

    static int mix(int a, int b, double t) {
        int alpha = (int) ((a >>> 24) * (1 - t) + (b >>> 24) * t), r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return alpha << 24 | r << 16 | g << 8 | bl;
    }

    static int withAlpha(int argb, int alpha) { return alpha << 24 | (argb & 0xffffff); }

    /** Heat tint of steel along an exhaust from the hot end (0) to the cool end (1): blue-violet, bronze, straw, bare steel. */
    static int heat(double t) {
        int[] stops = {0xff4f4566, 0xff7a4f3a, 0xffa0703e, 0xffb59d6c, 0xff9ea2a7};
        double x = Math.clamp(t, 0, 1) * (stops.length - 1);
        int i = Math.min((int) x, stops.length - 2);
        return mix(stops[i], stops[i + 1], x - i);
    }

    static double positive(double v, double fallback) { return Double.isFinite(v) && v > 0 ? v : fallback; }
    static Orientation aboutX(double radians) { return Orientation.rotationVector(Vec3d.X.scale(radians)); }

    /** The rotation that turns +y onto a direction (shortest arc). */
    static Orientation yTo(Vec3d direction) {
        Vec3d d = direction.normalized(), y = Vec3d.Y;
        double c = y.dot(d);
        if (c > 1 - 1e-12) { return Orientation.IDENTITY; }
        if (c < -1 + 1e-12) { return Orientation.rotationVector(Vec3d.X.scale(Math.PI)); }
        Vec3d axis = y.cross(d).normalized();
        return Orientation.rotationVector(axis.scale(Math.acos(c)));
    }

    /** A gear or sprocket outline in a plane: teeth between a root and a tip radius. */
    static double[][] gear(double root, double tip, int teeth) {
        int n = Math.max(6, teeth);
        double[][] out = new double[n * 4][];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n, w = 2 * Math.PI / n;
            double[] r = {root, tip, tip, root}, f = {0.0, 0.22, 0.5, 0.72};
            for (int k = 0; k < 4; k++) { double t = a + f[k] * w; out[i * 4 + k] = new double[]{r[k] * Math.cos(t), r[k] * Math.sin(t)}; }
        }
        return out;
    }

    /** A rounded rectangle outline (half width, half height, corner radius), centred. */
    static double[][] roundedRect(double hw, double hh, double radius, int perCorner) {
        double r = Math.min(radius, Math.min(hw, hh));
        List<double[]> pts = new ArrayList<>();
        double[][] centres = {{hw - r, hh - r}, {-hw + r, hh - r}, {-hw + r, -hh + r}, {hw - r, -hh + r}};
        for (int c = 0; c < 4; c++) {
            for (int k = 0; k <= perCorner; k++) {
                double a = Math.PI / 2 * c + Math.PI / 2 * k / perCorner;
                pts.add(new double[]{centres[c][0] + r * Math.cos(a), centres[c][1] + r * Math.sin(a)});
            }
        }
        return pts.toArray(new double[0][]);
    }

    /**
     * A closed belt or chain path round pulleys in the plane x = const: the convex hull of the pulley circles
     * (centres (y, z) and radii), counter-clockwise, as points.
     */
    static List<Vec3d> belt(double x, double[][] pulleys, int perCircle) {
        List<double[]> pts = new ArrayList<>();
        for (double[] p : pulleys) {
            for (int k = 0; k < perCircle; k++) {
                double a = 2 * Math.PI * k / perCircle;
                pts.add(new double[]{p[0] + p[2] * Math.cos(a), p[1] + p[2] * Math.sin(a)});
            }
        }
        // monotone chain convex hull
        pts.sort((u, v) -> u[0] != v[0] ? Double.compare(u[0], v[0]) : Double.compare(u[1], v[1]));
        List<double[]> hull = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = hull.size();
            for (int i = 0; i < pts.size(); i++) {
                double[] p = pts.get(pass == 0 ? i : pts.size() - 1 - i);
                while (hull.size() >= start + 2) {
                    double[] a = hull.get(hull.size() - 2), b = hull.get(hull.size() - 1);
                    if ((b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) <= 0) { hull.remove(hull.size() - 1); } else { break; }
                }
                hull.add(p);
            }
            hull.remove(hull.size() - 1);
        }
        // resample long straight runs so a tube follows them smoothly
        List<Vec3d> out = new ArrayList<>();
        for (int i = 0; i < hull.size(); i++) {
            double[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int n = Math.max(1, (int) Math.ceil(len / 0.01));
            for (int k = 0; k < n; k++) { double t = (double) k / n; out.add(new Vec3d(x, a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]))); }
        }
        return out;
    }

    /** A flat belt along a closed path in a plane x = const: boxes along each segment, a width across x and a thickness. */
    static void flatBelt(PartMesh out, List<Vec3d> path, double width, double thickness, int color) {
        for (int i = 0; i < path.size(); i++) {
            Vec3d a = path.get(i), b = path.get((i + 1) % path.size()), d = b.subtract(a);
            double len = d.length();
            if (len < 1e-6) { continue; }
            double angle = Math.atan2(d.z(), d.y());
            out.box(a.add(b).scale(0.5), new Vec3d(width / 2, len / 2 + 0.0005, thickness / 2), aboutX(angle), color);
        }
    }

    /**
     * A ring of thin twisted blades about the x axis: at axial position x, between hub and tip radii; chord along the
     * flow at the hub and tip, stagger (angle from the axis, rad) at hub and tip, a little camber. Both faces drawn.
     */
    static void bladeRow(PartMesh out, double x, double hub, double tip, int count, double chordHub, double chordTip,
                         double staggerHub, double staggerTip, double camber, int color) {
        bladeRow(out, x, hub, tip, count, chordHub, chordTip, staggerHub, staggerTip, camber, color, 4, true);
    }

    /** As many blades as a solidity (chord over pitch at mid span) asks for, at least 8, at most 96. */
    static int blades(double hub, double tip, double chord, double solidity) {
        return (int) Math.clamp(Math.round(Math.PI * (hub + tip) * solidity / Math.max(chord, 1e-6)), 8, 96);
    }

    /**
     * The same with a number of spanwise strips. 0.11: each blade is a closed airfoil, not a sheet: the camber line
     * (leading edge, the bent middle, trailing edge) thickened by the NACA four-digit distribution, 9 % of the chord
     * at the hub thinning to 5 % at the tip, lofted along the span and capped at the tip (the root stands on the hub);
     * so a blade has a front and a back, with no coincident faces to flicker. twoSided is no longer needed.
     */
    static void bladeRow(PartMesh out, double x, double hub, double tip, int count, double chordHub, double chordTip,
                         double staggerHub, double staggerTip, double camber, int color, int span, boolean twoSided) {
        if (tip <= hub || count <= 0) { return; }
        int strips = count > 24 ? 1 : Math.min(span, 2);   // a straight twist root to tip reads well on many small blades
        for (int k = 0; k < count; k++) {
            double phi = 2 * Math.PI * k / count;
            Vec3d radial = new Vec3d(0, Math.cos(phi), Math.sin(phi)), tangential = new Vec3d(0, -Math.sin(phi), Math.cos(phi));
            List<Vec3d[]> rings = new ArrayList<>();
            for (int st = 0; st <= strips; st++) {
                double f = (double) st / strips, r = hub + f * (tip - hub), chord = chordHub + f * (chordTip - chordHub), gamma = staggerHub + f * (staggerTip - staggerHub);
                Vec3d centre = new Vec3d(x, 0, 0).add(radial.scale(r));
                // the flow goes aft (-x); the chord turns from it by the stagger
                Vec3d dir = new Vec3d(-Math.cos(gamma), 0, 0).add(tangential.scale(Math.sin(gamma)));
                Vec3d bend = tangential.scale(Math.cos(gamma)).add(new Vec3d(Math.sin(gamma), 0, 0)).scale(camber * chord);
                Vec3d lead = centre.subtract(dir.scale(chord / 2)), trail = centre.add(dir.scale(chord / 2));
                Vec3d control = centre.add(bend).scale(2).subtract(lead.add(trail).scale(0.5));   // the curve passes through centre + bend
                Vec3d side = PartMesh.unit(radial.cross(dir));
                double tc = 0.09 + (0.05 - 0.09) * f;
                rings.add(airfoil(lead, control, trail, side, tc * chord));
            }
            for (int st = 0; st + 1 < rings.size(); st++) {
                Vec3d[] a = rings.get(st), b = rings.get(st + 1);
                for (int j = 0; j < a.length; j++) {
                    int n = (j + 1) % a.length;
                    outward(out, a[j], a[n], b[n], b[j], centroid(a).add(centroid(b)).scale(0.5), color);
                }
            }
            Vec3d[] end = rings.get(rings.size() - 1);
            Vec3d c = centroid(end), up = radial;
            for (int j = 0; j < end.length; j++) {
                int n = (j + 1) % end.length;
                Vec3d face = end[n].subtract(c).cross(end[j].subtract(c));
                if (face.dot(up) >= 0) { out.quad(c, end[n], end[j], end[j], color, up); } else { out.quad(c, end[j], end[n], end[n], color, up); }
            }
        }
    }

    /** Six points round an airfoil section: leading edge, two on the upper surface, trailing edge, two on the lower. */
    private static Vec3d[] airfoil(Vec3d lead, Vec3d control, Vec3d trail, Vec3d side, double thickness) {
        double[] at = {0.25, 0.6};
        Vec3d[] p = new Vec3d[6];
        p[0] = lead;
        p[3] = trail;
        for (int i = 0; i < 2; i++) {
            double t = at[i], u = 1 - t;
            Vec3d camber = lead.scale(u * u).add(control.scale(2 * u * t)).add(trail.scale(t * t));
            double half = 5 * thickness * (0.2969 * Math.sqrt(t) - 0.126 * t - 0.3516 * t * t + 0.2843 * t * t * t - 0.1036 * t * t * t * t);
            p[1 + i] = camber.add(side.scale(half));
            p[5 - i] = camber.subtract(side.scale(half));
        }
        return p;
    }

    private static Vec3d centroid(Vec3d[] ring) {
        Vec3d c = Vec3d.ZERO;
        for (Vec3d v : ring) { c = c.add(v); }
        return c.scale(1.0 / ring.length);
    }

    /** A quad wound to face away from a point inside the solid. */
    private static void outward(PartMesh out, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d inside, int color) {
        Vec3d n = c.subtract(a).cross(d.subtract(b));
        Vec3d mid = a.add(b).add(c).add(d).scale(0.25);
        if (n.dot(mid.subtract(inside)) >= 0) { out.quad(a, b, c, d, color, n); } else { out.quad(a, d, c, b, color, n.scale(-1)); }
    }

    /** Small cylinders on a circle about the x axis (bolts, studs, holes): at x, radius, count, along x by length. */
    static void boltCircle(PartMesh out, double x, double radius, int count, double boltRadius, double length, int color) {
        for (int k = 0; k < count; k++) {
            double a = 2 * Math.PI * k / count;
            Vec3d c = new Vec3d(x, radius * Math.cos(a), radius * Math.sin(a));
            out.cylinder(c, c.add(new Vec3d(length, 0, 0)), boltRadius, boltRadius, 6, color);
        }
    }
}
