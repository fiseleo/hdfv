package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.aero.Airfoil;
import dev.mcmod.hdfv.part.air.PlanformDesign;
import dev.mcmod.hdfv.part.air.WingDesign;

/**
 * 0.13: a wing's surface in its part's frame, the one AirframeModel lofts (moved here from its private point so that
 * stickers are laid and picked on the same surface): at span fraction eta (0 the root, 1 the tip) the section blends
 * the root and tip airfoils linearly, its chord tapers linearly, it is turned by the incidence and twist about its
 * quarter chord, and the quarter-chord line is swept back and raised by the dihedral; s is the chord fraction from the
 * leading edge (+x), and a side's half lies along its z (-1 port, +1 starboard; a single wing, a fin, has the
 * starboard half alone).
 */
public final class WingSurface {
    /** Chordwise rays a surface is searched along before refining (cosine spaced: closer at the leading edge). */
    private static final int SEARCH = 64;

    public final PlanformDesign planform;
    public final Airfoil root, tip;
    /** One side's span, root to tip, m. */
    public final double half;
    private final double tanSweep, tanDihedral;

    public WingSurface(WingDesign w) { this(w.planform(), w.rootSection().airfoil(), w.tipSection().airfoil()); }

    public WingSurface(PlanformDesign p, Airfoil root, Airfoil tip) {
        this.planform = p;
        this.root = root;
        this.tip = tip;
        this.half = p.mirrored() ? p.spanM() / 2 : p.spanM();
        this.tanSweep = Math.tan(Math.toRadians(p.sweepDeg()));
        this.tanDihedral = Math.tan(Math.toRadians(p.dihedralDeg()));
    }

    /** The sides drawn: port and starboard when mirrored, else starboard alone. */
    public int[] sides() { return planform.mirrored() ? new int[]{-1, 1} : new int[]{1}; }

    /** A sticker's side as drawn (a single wing's sticker is on its one half). */
    public int side(Sticker.Side side) { return planform.mirrored() ? side.sign() : 1; }

    /** A point of the wing: span fraction, chord fraction s from the leading edge, on the upper (1), lower (0) or mean (0.5) surface, side. */
    public Vec3d point(double eta, double s, double surface, int side) {
        double y0 = eta * half;
        double chord = planform.rootChordM() + (planform.tipChordM() - planform.rootChordM()) * eta;
        double twist = Math.toRadians(planform.incidenceDeg() + planform.twistDeg() * eta);
        double xs = Math.clamp(s, 0, 1);
        double upper = (1 - eta) * root.upperAt(xs) + eta * tip.upperAt(xs), lower = (1 - eta) * root.lowerAt(xs) + eta * tip.lowerAt(xs);
        double h = lower + surface * (upper - lower);
        double dx = (0.25 - xs) * chord, dy = h * chord;
        double x = dx * Math.cos(twist) - dy * Math.sin(twist), y = dx * Math.sin(twist) + dy * Math.cos(twist);
        return new Vec3d(-y0 * tanSweep + x, y0 * tanDihedral + y, side * y0);
    }

    public Vec3d point(double eta, double s, Sticker.Surface surface, int side) { return point(eta, s, height(surface), side); }

    private static double height(Sticker.Surface surface) { return surface == Sticker.Surface.UPPER ? 1 : 0; }

    /**
     * The outward unit normal of a surface (from its tangents along the chord and the span), turned away from the mean
     * line a little inside the section (its middle chord: behind the leading edge, ahead of the trailing edge).
     */
    public Vec3d normal(double eta, double s, Sticker.Surface surface, int side) {
        double h = 1e-3, f = height(surface);
        double s0 = Math.max(0, s - h), s1 = Math.min(1, s + h), e0 = Math.max(0, eta - h), e1 = Math.min(1, eta + h);
        Vec3d along = point(eta, s1, f, side).subtract(point(eta, s0, f, side)), span = point(e1, s, f, side).subtract(point(e0, s, f, side));
        Vec3d at = point(eta, s, f, side), out = at.subtract(point(eta, Math.clamp(s, 0.1, 0.9), 0.5, side));
        if (out.lengthSquared() < 1e-18) {
            double twist = Math.toRadians(planform.incidenceDeg() + planform.twistDeg() * eta);
            out = new Vec3d(-Math.sin(twist), Math.cos(twist), 0).scale(f > 0.5 ? 1 : -1);
        }
        Vec3d n = along.cross(span);
        if (n.lengthSquared() < 1e-24) { n = out; }
        if (n.dot(out) < 0) { n = n.scale(-1); }
        return n.normalized();
    }

    /** 0.13: a place on the wing, as a sticker's (OnWing) is. */
    public record Location(double eta, double chord, Sticker.Surface surface, Sticker.Side side) {}

    /**
     * The place of the surface nearest p (a sticker's, from a click on the wing's mesh; a point of the surface gives
     * itself back to well under a millimetre): the side and span from p's z, on each surface the nearest of SEARCH
     * chordwise rays refined between its neighbours, the nearer surface; then the span refined about it, kept only
     * where it comes nearer.
     */
    public Location locate(Vec3d p) {
        Sticker.Side side = planform.mirrored() && p.z() < 0 ? Sticker.Side.PORT : Sticker.Side.STARBOARD;
        int sign = side(side);
        double eta = half > 0 ? Math.clamp(sign * p.z() / half, 0, 1) : 0;
        Location best = nearest(eta, side, p);
        double w = 0.05;
        double e1 = BodySurface.minimum(e -> point(e, best.chord(), best.surface(), sign).subtract(p).lengthSquared(), Math.max(0, eta - w), Math.min(1, eta + w));
        Location other = nearest(e1, side, p);
        return distance(other, p) < distance(best, p) ? other : best;
    }

    private double distance(Location l, Vec3d p) { return point(l.eta(), l.chord(), l.surface(), side(l.side())).subtract(p).lengthSquared(); }

    /** At a span fraction, the nearer of the two surfaces' nearest points. */
    private Location nearest(double eta, Sticker.Side side, Vec3d p) {
        int sign = side(side);
        Location best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Sticker.Surface surface : Sticker.Surface.values()) {
            double f = height(surface);
            int k0 = 0;
            double d0 = Double.POSITIVE_INFINITY;
            for (int k = 0; k <= SEARCH; k++) {
                double d = point(eta, ray(k), f, sign).subtract(p).lengthSquared();
                if (d < d0) { d0 = d; k0 = k; }
            }
            double s = BodySurface.minimum(c -> point(eta, c, f, sign).subtract(p).lengthSquared(), ray(Math.max(k0 - 1, 0)), ray(Math.min(k0 + 1, SEARCH)));
            double d = point(eta, s, f, sign).subtract(p).lengthSquared();
            if (d < nearest) { nearest = d; best = new Location(eta, s, surface, side); }
        }
        return best;
    }

    private static double ray(int k) { return 0.5 * (1 - Math.cos(Math.PI * k / SEARCH)); }
}
