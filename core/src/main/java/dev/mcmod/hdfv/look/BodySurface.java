package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;

import java.util.List;

/**
 * A body's lofted surface for drawing: each station's outline met by rays from its middle (half its height up, on the
 * centre line) at an angle t, stretched to the station's width and height (an ellipse's parameter): t = 0 the bottom,
 * rising round the port side (z < 0) to the top at pi and down the starboard side. Between stations the surface is
 * interpolated linearly. A sharp corner (a chine) stays between the same two rays from station to station; sampled
 * along the girth instead, a tapering bow's chine fell between different samples at each station and the skin twisted
 * into a saw-tooth. Details (windows, stripes, lights) are laid on it by angle, or by height on one side.
 * <p>
 * 0.12: in core (pure Java) with BodyLayout, which places a body's openings on it; the model (BodyModel) cuts them.
 */
public final class BodySurface {
    /** Rays round each station for the skin. */
    public static final int RING = 64;

    public final BodyShape shape;
    public final List<BodyShape.Station> stations;
    public final double[] xs;
    private final double[] cy, ry, rz;
    /** The skin's points: RING rays per station. */
    public final Vec3d[][] rings;

    public BodySurface(BodyShape shape) {
        this.shape = shape;
        this.stations = shape.stations();
        int n = stations.size();
        xs = new double[n]; cy = new double[n]; ry = new double[n]; rz = new double[n];
        rings = new Vec3d[n][];
        for (int i = 0; i < n; i++) {
            BodyShape.Station s = stations.get(i);
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY, half = 0;
            for (double[] p : s.outline()) { low = Math.min(low, p[0]); high = Math.max(high, p[0]); half = Math.max(half, Math.abs(p[1])); }
            xs[i] = s.xM();
            cy[i] = 0.5 * (low + high);
            ry[i] = Math.max(0.5 * (high - low), 1e-6);
            rz[i] = Math.max(half, 1e-6);
            rings[i] = new Vec3d[RING];
            for (int k = 0; k < RING; k++) { rings[i][k] = hit(i, angle(k)); }
        }
    }

    public static double angle(int k) { return 2 * Math.PI * k / RING; }

    public double front() { return xs[xs.length - 1]; }
    public double rear() { return xs[0]; }

    /** Where the ray at angle t from station i's middle meets its outline (exactly once: the middle is inside a convex outline). */
    public Vec3d hit(int i, double t) {
        List<double[]> o = stations.get(i).outline();
        int n = o.size();
        double dy = -Math.cos(t) * ry[i], dz = -Math.sin(t) * rz[i], best = Double.POSITIVE_INFINITY;
        for (int e = 0; e < n; e++) {
            double[] a = o.get(e), b = o.get((e + 1) % n);
            double ey = b[0] - a[0], ez = b[1] - a[1], det = -dy * ez + dz * ey;
            if (Math.abs(det) < 1e-15) { continue; }
            double py = a[0] - cy[i], pz = a[1];
            double along = (-py * ez + pz * ey) / det, at = (dy * pz - dz * py) / det;
            if (along > 0 && at >= -1e-9 && at <= 1 + 1e-9) { best = Math.min(best, along); }
        }
        if (!Double.isFinite(best)) { best = 0; }
        return new Vec3d(xs[i], cy[i] + best * dy, best * dz);
    }

    /** The last station at or behind x, so that x lies between it and the next (clamped to the body). */
    public int index(double x) {
        int lo = 0, hi = xs.length - 2;
        if (hi < 0) { return 0; }
        if (x <= xs[0]) { return 0; }
        if (x >= xs[hi + 1]) { return hi; }
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (xs[mid] <= x) { lo = mid; } else { hi = mid - 1; }
        }
        return lo;
    }

    /** The surface at x (clamped to the body) and angle t. */
    public Vec3d point(double x, double t) {
        int i = index(x);
        if (xs.length == 1) { return hit(0, t); }
        double span = xs[i + 1] - xs[i], f = span > 1e-9 ? Math.clamp((x - xs[i]) / span, 0, 1) : 1;
        Vec3d a = hit(i, t), b = hit(i + 1, t);
        return a.add(b.subtract(a).scale(f));
    }

    /** The middle (half the height up, on the centre line) at x. */
    public double middle(double x) {
        int i = index(x);
        if (xs.length == 1) { return cy[0]; }
        double span = xs[i + 1] - xs[i], f = span > 1e-9 ? Math.clamp((x - xs[i]) / span, 0, 1) : 1;
        return cy[i] + f * (cy[i + 1] - cy[i]);
    }

    /** The outward unit normal at x and angle t (from the tangents along the body and round it). */
    public Vec3d normal(double x, double t) {
        double hx = 0.02, ht = 0.02;
        double x0 = Math.max(rear(), x - hx), x1 = Math.min(front(), x + hx);
        Vec3d along = x1 > x0 ? point(x1, t).subtract(point(x0, t)) : Vec3d.X;
        Vec3d round = point(x, t + ht).subtract(point(x, t - ht));
        Vec3d p = point(x, t), out = p.subtract(new Vec3d(x, middle(x), 0));
        Vec3d n = round.cross(along);
        if (n.lengthSquared() < 1e-18) { n = out.lengthSquared() > 1e-18 ? out : Vec3d.Y; }
        if (n.dot(out) < 0) { n = n.scale(-1); }
        return n.normalized();
    }

    /** The angle on the port side (0..pi) where the surface at x is at height y (clamped to the body there). */
    public double portAngle(double x, double y) {
        double lo = 0, hi = Math.PI;
        if (y <= point(x, lo).y()) { return lo; }
        if (y >= point(x, hi).y()) { return hi; }
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (lo + hi);
            if (point(x, mid).y() < y) { lo = mid; } else { hi = mid; }
        }
        return 0.5 * (lo + hi);
    }

    /** The same on station i itself. */
    public double portAngleAt(int i, double y) {
        double lo = 0, hi = Math.PI;
        if (y <= hit(i, lo).y()) { return lo; }
        if (y >= hit(i, hi).y()) { return hi; }
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (lo + hi);
            if (hit(i, mid).y() < y) { lo = mid; } else { hi = mid; }
        }
        return 0.5 * (lo + hi);
    }

    /** The angle at height y on one side: port as portAngle, starboard its mirror (2 pi - t). */
    public double sideAngle(double x, double y, boolean starboard) {
        double t = portAngle(x, y);
        return starboard ? 2 * Math.PI - t : t;
    }

    /** 0.13: a place on the surface, x along the body and the angle t (0 to 2 pi). */
    public record Location(double x, double t) {}

    /**
     * 0.13: the place of the surface nearest p (a sticker's, from a click on the body's mesh; a point of the surface gives
     * itself back to well under a millimetre). The nearest of the RING rays at p's x (clamped to the body), refined
     * between its neighbours; then x refined about it within a station's spacing, kept only where it comes nearer (a
     * skin sloping along the body, such as a nose's, has its nearest point off p's station).
     */
    public Location locate(Vec3d p) {
        double x = Math.clamp(p.x(), rear(), front()), t = angleNear(x, p);
        if (xs.length > 1) {
            int i = index(x);
            double w = xs[i + 1] - xs[i], t0 = t;
            double x1 = minimum(v -> point(v, t0).subtract(p).lengthSquared(), Math.max(rear(), x - w), Math.min(front(), x + w));
            double t1 = angleNear(x1, p);
            if (point(x1, t1).subtract(p).lengthSquared() < point(x, t).subtract(p).lengthSquared()) { x = x1; t = t1; }
        }
        double turn = 2 * Math.PI;
        t -= turn * Math.floor(t / turn);
        return new Location(x, t < turn ? t : 0);
    }

    /** The angle round the section at x nearest p: the nearest ray's, refined between its neighbours. */
    private double angleNear(double x, Vec3d p) {
        int best = 0;
        double nearest = Double.POSITIVE_INFINITY;
        for (int k = 0; k < RING; k++) {
            double d = point(x, angle(k)).subtract(p).lengthSquared();
            if (d < nearest) { nearest = d; best = k; }
        }
        double step = 2 * Math.PI / RING;
        return minimum(t -> point(x, t).subtract(p).lengthSquared(), angle(best) - step, angle(best) + step);
    }

    /** Where f is least between lo and hi (golden-section search to about 1e-10 of the interval; f unimodal there). WingSurface's too. */
    static double minimum(java.util.function.DoubleUnaryOperator f, double lo, double hi) {
        double g = (Math.sqrt(5) - 1) / 2, a = lo, b = hi;
        double c = b - g * (b - a), d = a + g * (b - a), fc = f.applyAsDouble(c), fd = f.applyAsDouble(d);
        for (int it = 0; it < 48; it++) {
            if (fc <= fd) { b = d; d = c; fd = fc; c = b - g * (b - a); fc = f.applyAsDouble(c); }
            else { a = c; c = d; fc = fd; d = a + g * (b - a); fd = f.applyAsDouble(d); }
        }
        return 0.5 * (a + b);
    }

    public double bottom(double x) { return shape.bottomAt(x); }
    public double top(double x) { return shape.topAt(x); }
    public double width(double x) { return shape.widthAt(x); }
}
