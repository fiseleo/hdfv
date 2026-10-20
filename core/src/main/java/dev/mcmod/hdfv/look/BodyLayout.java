package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign.Family;
import dev.mcmod.hdfv.part.body.BodyModuleDesign.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;

/**
 * Where a body's openings are (0.12): its windscreens, windows, portholes, doors, wheel arches and open tops, placed
 * from the design on the lofted surface (BodySurface), and where its people sit. An opening is an x span cut as rows:
 * at each row's x it covers the angles t from lo to hi (t as BodySurface's: 0 the bottom, pi the top, the port side
 * below pi), so between two rows it is a trapezoid in (x, t), convex, and the opening is their union, its outline
 * running forward along lo and back along hi. The model cuts these from the skin (AeroCore's SurfaceCut); the rules
 * for where they go are here, pure Java and tested:
 * <ul>
 * <li>cars (the glazed passenger cell): a windscreen from the cowl to the roof's front edge between the A pillars
 *     (straight lines in (x, t) from the belt line to the roof rail), a rear window between the C pillars, the side glass
 *     above the belt up to the rail between them; doors from the sill to the rail, each with its window, hinged at its
 *     front edge; a quarter window behind the last door; a wheel arch round each axle (doors keep clear of them);
 *     left-hand drive;</li>
 * <li>aircraft: a cockpit's two windscreen panes either side of a post where its top rises from the nose, and a side
 *     window each side behind them; a cabin's rows of windows; doors evenly along a module, alternately starboard and
 *     port; the pilot in the left seat;</li>
 * <li>boats: portholes high on a closed module's sides, above the waterline; doors as aircraft; the helm to starboard;</li>
 * <li>an open module: everything above its open edge (the sill).</li>
 * </ul>
 * A window crossing a door's edge is split: the part in the door (inside its frame) is the door's window, the rest the
 * body's. The proportions are the 0.5 to 0.11 drawing's (where windows and doors were painted on the skin).
 */
public final class BodyLayout {
    /** What an opening is. */
    public enum Type {
        WINDSHIELD, REAR_WINDOW, SIDE_WINDOW, CABIN_WINDOW, PORTHOLE, DOOR, ARCH, OPEN;

        /** Glass in it. */
        public boolean glazed() { return ordinal() <= PORTHOLE.ordinal(); }
    }

    /** A door's hinge: the axis from a to b (part frame); turning about it by swing times an angle opens the door outward. */
    public record Hinge(Vec3d a, Vec3d b, int swing) {}

    /**
     * An opening: its id (its index in the layout's list), type, module, side (-1 port, 1 starboard, 0 across the top
     * or all round), rows (x increasing; lo and hi the angles at each, lo up to hi), the id of the door it is a window
     * of (-1: none) and a door's hinge (null otherwise).
     */
    public record Opening(int id, Type type, int module, int side, double[] xs, double[] lo, double[] hi, int door, Hinge hinge) {
        public double x0() { return xs[0]; }

        public double x1() { return xs[xs.length - 1]; }

        /** lo at x between the rows (NaN outside the span). */
        public double loAt(double x) { return between(lo, x); }

        /** hi at x between the rows (NaN outside the span). */
        public double hiAt(double x) { return between(hi, x); }

        private double between(double[] v, double x) {
            if (x < xs[0] - 1e-12 || x > xs[xs.length - 1] + 1e-12) { return Double.NaN; }
            int i = 0;
            while (i + 2 < xs.length && xs[i + 1] < x) { i++; }
            double span = xs[i + 1] - xs[i], f = span > 0 ? Math.clamp((x - xs[i]) / span, 0, 1) : 0;
            return v[i] + f * (v[i + 1] - v[i]);
        }

        /** Whether (x, t) is in it (t as given: no wrapping). */
        public boolean contains(double x, double t) {
            double l = loAt(x), h = hiAt(x);
            return l <= t && t <= h;
        }

        /**
         * Its convex parts in (x, t): runs of rows as long as they stay convex (a rounded window is one part; a door
         * notched round an arch a few), each at least the trapezoid between two rows. Few large parts cut a cell into
         * few pieces: the trapezoids alone left forty in a cell a window crossed.
         */
        public List<double[][]> parts() {
            List<double[][]> out = new ArrayList<>();
            int r = 0, n = xs.length;
            while (r + 1 < n) {
                int end = r + 1;
                while (end + 1 < n && convex(r, end + 1)) { end++; }
                double[][] part = run(r, end);
                if (part.length >= 3) { out.add(part); }
                r = end;
            }
            return out;
        }

        /** Rows r to s as a polygon: forward along lo, back along hi, repeated corners dropped. */
        private double[][] run(int r, int s) {
            List<double[]> pts = new ArrayList<>();
            for (int i = r; i <= s; i++) { add(pts, xs[i], lo[i]); }
            for (int i = s; i >= r; i--) { add(pts, xs[i], hi[i]); }
            while (pts.size() > 1 && same(pts.get(0), pts.get(pts.size() - 1))) { pts.remove(pts.size() - 1); }
            return pts.toArray(new double[0][]);
        }

        private static void add(List<double[]> pts, double x, double t) {
            double[] p = {x, t};
            if (pts.isEmpty() || !same(pts.get(pts.size() - 1), p)) { pts.add(p); }
        }

        private static boolean same(double[] a, double[] b) { return Math.abs(a[0] - b[0]) < 1e-12 && Math.abs(a[1] - b[1]) < 1e-12; }

        /**
         * Whether rows r to s make a convex polygon: every corner turning left (counter-clockwise), or right by under
         * 0.1 degree (a window's curves sampled through the surface's angles wobble that much; the cut is then off
         * by well under a millimetre).
         */
        private boolean convex(int r, int s) { return convex(run(r, s)); }

        /** Whether a polygon turns left at every corner, or right by under 0.1 degree. */
        public static boolean convex(double[][] p) {
            int m = p.length;
            if (m < 3) { return true; }
            for (int i = 0; i < m; i++) {
                double[] a = p[i], b = p[(i + 1) % m], c = p[(i + 2) % m];
                double ex = b[0] - a[0], et = b[1] - a[1], fx = c[0] - b[0], ft = c[1] - b[1];
                double cross = ex * ft - et * fx, lengths = Math.sqrt((ex * ex + et * et) * (fx * fx + ft * ft));
                if (cross < -2e-3 * lengths) { return false; }
            }
            return true;
        }

        /** Its outline in (x, t), counter-clockwise: forward along lo, back along hi. */
        public double[][] outline() {
            int n = xs.length;
            double[][] out = new double[2 * n][];
            for (int r = 0; r < n; r++) {
                out[r] = new double[]{xs[r], lo[r]};
                out[2 * n - 1 - r] = new double[]{xs[r], hi[r]};
            }
            return out;
        }

        /** Its area in (x, t) (m rad). */
        public double area() {
            double a = 0;
            for (int r = 0; r + 1 < xs.length; r++) { a += 0.5 * (xs[r + 1] - xs[r]) * ((hi[r] - lo[r]) + (hi[r + 1] - lo[r + 1])); }
            return a;
        }

        /** Its middle in (x, t): halfway along, halfway between lo and hi there. */
        public double[] middle() {
            double x = 0.5 * (x0() + x1());
            return new double[]{x, 0.5 * (loAt(x) + hiAt(x))};
        }

        /** Its middle on the surface. */
        public Vec3d centre(BodySurface s) {
            double[] m = middle();
            return s.point(m[0], m[1]);
        }

        Opening withIds(int newId, int newDoor) { return new Opening(newId, type, module, side, xs, lo, hi, newDoor, hinge); }

        /**
         * The same moved in from every edge by a gap (metres along the surface): a door's panel within its opening.
         * Null where nothing is left.
         */
        public Opening inset(double gap, BodySurface s) {
            double a = x0() + gap, b = x1() - gap;
            if (!(b - a > 1e-3)) { return null; }
            List<Double> rows = new ArrayList<>();
            rows.add(a);
            for (double x : xs) { if (x > a + 1e-6 && x < b - 1e-6) { rows.add(x); } }
            rows.add(b);
            int n = rows.size();
            double[] x = new double[n], l = new double[n], h = new double[n];
            for (int r = 0; r < n; r++) {
                x[r] = rows.get(r);
                double lo0 = loAt(x[r]), hi0 = hiAt(x[r]);
                l[r] = lo0 + gap / speed(s, x[r], lo0);
                h[r] = hi0 - gap / speed(s, x[r], hi0);
                if (h[r] < l[r]) { double m = 0.5 * (l[r] + h[r]); l[r] = m; h[r] = m; }
            }
            return new Opening(id, type, module, side, x, l, h, door, hinge);
        }
    }

    /** How wide a car's A and C pillars are (m), and a door's frame over its glass. */
    public static final double PILLAR_M = 0.07, DOOR_FRAME_M = 0.035;
    /** A wheel arch's radius about the hub (m): a family car's 205/55R16 and a hand's breadth. */
    public static final double ARCH_RADIUS_M = 0.37;
    /** The frame left round a window that falls in an aircraft's or a boat's door (m). */
    public static final double WINDOW_IN_DOOR_FRAME_M = 0.05;
    private static final int[] SIDES = {-1, 1};

    public final BodySurface surface;
    /** Windows first (those in doors among them), then doors, arches, open tops: the order holes are cut, innermost first. */
    public final List<Opening> openings;
    public final List<BodyDesign.Seat> seats;
    /** The driver's (pilot's, helmsman's) seat in seats, or -1. */
    public final int driver;
    /** Per module: whether its inside is seen through a window or a door (it gets a liner and a floor). */
    public final boolean[] lined;
    /** The boats' waterline (the boat modules' depth times 0.38 up from their lowest bottom), or -infinity. */
    public final double waterline;
    /** A car's belt line (the glazed passenger cell's), or NaN. */
    public final double belt;

    private final BodyDesign d;
    private final BodyShape shape;
    private final BodySurface s;
    private double carBelt = Double.NaN;

    public static BodyLayout of(BodyDesign d) {
        BodyShape shape = d.shape();
        return new BodyLayout(d, shape, new BodySurface(shape));
    }

    public BodyLayout(BodyDesign d, BodyShape shape, BodySurface s) {
        this.d = d; this.shape = shape; this.s = s; this.surface = s;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (BodyShape.Station st : s.stations) {
            if (module(Math.min(st.module(), d.modules().size() - 1)).kind().family == Family.BOAT) {
                low = Math.min(low, st.bottomM());
                high = Math.max(high, st.topM());
            }
        }
        waterline = high > low ? low + 0.38 * (high - low) : Double.NEGATIVE_INFINITY;
        List<Opening> windows = new ArrayList<>(), doors = new ArrayList<>(), arches = new ArrayList<>(), open = new ArrayList<>();
        for (int i = 0; i < d.modules().size(); i++) {
            BodyModuleDesign m = module(i);
            switch (m.kind().family) {
                case AIRCRAFT -> {
                    if (!m.open() && m.windowShare() > 0) {
                        if (m.kind() == Kind.COCKPIT) { cockpit(i, windows); }
                        else if (m.kind() != Kind.NOSE && m.kind() != Kind.TAIL_CONE && m.kind() != Kind.TAIL_BOOM) { cabinWindows(i, windows); }
                    }
                    if (!m.open()) { doors(i, doors); }
                }
                case BOAT -> {
                    if (!m.open()) { portholes(i, windows); doors(i, doors); }
                }
                case CAR -> { }
            }
            if (m.open()) { Opening o = openTop(i); if (o != null) { open.add(o); } }
        }
        car(windows, doors, arches);
        // windows across a door's edge split (the car's are made in their doors already)
        List<Opening> placed = new ArrayList<>();
        for (Opening w : windows) {
            if (w.door() >= 0) { placed.add(w); continue; }
            List<Opening> pieces = List.of(w);
            for (int k = 0; k < doors.size(); k++) {
                List<Opening> next = new ArrayList<>();
                for (Opening p : pieces) { next.addAll(p.door() >= 0 ? List.of(p) : split(p, doors.get(k), k)); }
                pieces = next;
            }
            placed.addAll(pieces);
        }
        List<Opening> all = new ArrayList<>();
        int w = placed.size();
        for (Opening o : placed) { all.add(o.withIds(all.size(), o.door() >= 0 ? w + o.door() : -1)); }
        for (Opening o : doors) { all.add(o.withIds(all.size(), -1)); }
        for (Opening o : arches) { all.add(o.withIds(all.size(), -1)); }
        for (Opening o : open) { all.add(o.withIds(all.size(), -1)); }
        openings = List.copyOf(all);
        belt = carBelt;
        lined = new boolean[d.modules().size()];
        for (int i = 0; i < lined.length; i++) {
            if (module(i).open()) { continue; }
            double rear = shape.moduleRearX(i), front = shape.moduleFrontX(i);
            for (Opening o : openings) {
                if ((o.type().glazed() || o.type() == Type.DOOR) && Math.min(front, o.x1()) - Math.max(rear, o.x0()) > 0.02) { lined[i] = true; }
            }
        }
        seats = List.copyOf(d.seats(shape));
        driver = driver();
    }

    private BodyModuleDesign module(int i) { return d.modules().get(i); }

    // ---------------------------------------------------------------- strips

    /**
     * An opening of rows from x0 to x1 (at most step apart, and at the knots given: where a curve jumps) between two
     * angle curves given on the port side (t up to pi; mirrored for starboard; side 0 as given), where hi is above lo,
     * its ends found where they cross. Null where it is shut throughout or under a millimetre long; where it shuts and
     * opens again, the longest open stretch.
     */
    private Opening strip(Type type, int module, int side, double x0, double x1, double step, DoubleUnaryOperator lo, DoubleUnaryOperator hi,
                          int door, Hinge hinge, double... knots) {
        Opening best = null;
        for (Opening o : strips(type, module, side, x0, x1, step, lo, hi, door, knots)) {
            if (best == null || o.x1() - o.x0() > best.x1() - best.x0()) { best = o; }
        }
        return best == null || hinge == null ? best : new Opening(best.id(), type, module, side, best.xs(), best.lo(), best.hi(), door, hinge);
    }

    /** As strip, every open stretch (each an opening of its own). */
    private List<Opening> strips(Type type, int module, int side, double x0, double x1, double step, DoubleUnaryOperator lo, DoubleUnaryOperator hi,
                                 int door, double... knots) {
        x0 = Math.max(x0, s.rear());
        x1 = Math.min(x1, s.front());
        List<Opening> out = new ArrayList<>();
        if (!(x1 - x0 > 1e-3)) { return out; }
        int n0 = Math.max(2, (int) Math.ceil((x1 - x0) / step) + 1);
        List<Double> at = new ArrayList<>();
        for (int i = 0; i < n0; i++) { at.add(x0 + (x1 - x0) * i / (n0 - 1)); }
        for (double k : knots) { if (k > x0 + 1e-6 && k < x1 - 1e-6) { at.add(k); } }
        at.sort(Double::compare);
        List<Double> xsList = new ArrayList<>();
        for (double x : at) { if (xsList.isEmpty() || x - xsList.get(xsList.size() - 1) > 1e-7) { xsList.add(x); } }
        int n = xsList.size();
        double[] x = new double[n], l = new double[n], h = new double[n];
        boolean[] open = new boolean[n];
        for (int i = 0; i < n; i++) {
            x[i] = xsList.get(i);
            l[i] = lo.applyAsDouble(x[i]);
            h[i] = hi.applyAsDouble(x[i]);
            open[i] = h[i] - l[i] > 1e-9;
        }
        for (int i = 0; i < n; i++) {
            if (!open[i] || (i > 0 && open[i - 1])) { continue; }
            int last = i;
            while (last + 1 < n && open[last + 1]) { last++; }
            List<double[]> rows = new ArrayList<>();
            if (i > 0) { rows.add(cross(x[i - 1], x[i], lo, hi)); }
            for (int r = i; r <= last; r++) { rows.add(new double[]{x[r], l[r], h[r]}); }
            if (last < n - 1) { rows.add(cross(x[last + 1], x[last], lo, hi)); }
            rows.sort((a, b) -> Double.compare(a[0], b[0]));
            List<double[]> kept = new ArrayList<>();
            for (double[] r : rows) { if (kept.isEmpty() || r[0] - kept.get(kept.size() - 1)[0] > 1e-9) { kept.add(r); } }
            if (kept.size() < 2 || kept.get(kept.size() - 1)[0] - kept.get(0)[0] < 1e-3) { continue; }
            int m = kept.size();
            double[] xs = new double[m], los = new double[m], his = new double[m];
            for (int k = 0; k < m; k++) {
                double[] r = kept.get(k);
                xs[k] = r[0];
                los[k] = side > 0 ? 2 * Math.PI - r[2] : r[1];
                his[k] = side > 0 ? 2 * Math.PI - r[1] : r[2];
            }
            out.add(new Opening(-1, type, module, side, xs, los, his, door, null));
        }
        return out;
    }

    /** Where hi - lo crosses zero between a shut x and an open one: a row of no width there. */
    private static double[] cross(double shut, double open, DoubleUnaryOperator lo, DoubleUnaryOperator hi) {
        double a = shut, b = open;
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (a + b);
            if (hi.applyAsDouble(mid) - lo.applyAsDouble(mid) > 1e-9) { b = mid; } else { a = mid; }
        }
        double t = 0.5 * (lo.applyAsDouble(a) + hi.applyAsDouble(a));
        return new double[]{a, t, t};
    }

    /** How far the surface moves per radian of t at (x, t) (m/rad): turns a width in metres into one in angle. */
    public static double speed(BodySurface s, double x, double t) {
        double v = s.point(x, t + 1e-3).subtract(s.point(x, t - 1e-3)).length() / 2e-3;
        return Math.max(v, 1e-3);
    }

    /** An angle width (rad) of so many metres at (x, t). */
    private double across(double x, double t, double metres) { return metres / speed(s, x, t); }

    /** A rounded rectangle on the port side, centred at x and height y, its length along x and its height, corner radius r. */
    private Opening box(Type type, int module, int side, double xc, double yc, double len, double h, double r, double step) {
        double hl = len / 2, hh = h / 2;
        return strip(type, module, side, xc - hl, xc + hl, step,
                x -> s.portAngle(x, yc - rounded(x - xc, hl, hh, r)), x -> s.portAngle(x, yc + rounded(x - xc, hl, hh, r)), -1, null);
    }

    /** Half the height of a rounded rectangle at dx from its middle (half length hl, half height hh, corner radius r). */
    public static double rounded(double dx, double hl, double hh, double r) {
        r = Math.min(r, Math.min(hl, hh));
        double ax = Math.abs(dx), straight = hl - r;
        if (ax <= straight) { return hh; }
        double e = Math.min(ax - straight, r);
        return hh - r + Math.sqrt(Math.max(0, r * r - e * e));
    }

    /** 1 in the middle of a span (half length hl) falling round to 0 at its ends over the outer `share` of it. */
    public static double bump(double dx, double hl, double share) {
        double r = Math.max(share * hl, 1e-9), straight = hl - r, ax = Math.abs(dx);
        if (ax <= straight) { return 1; }
        double e = Math.min(ax - straight, r);
        return Math.sqrt(Math.max(0, r * r - e * e)) / r;
    }

    /** The top's slope along the body at x (rise per metre forward). */
    public static double slope(BodySurface s, double x) { return (s.top(Math.min(x + 0.02, s.front())) - s.top(Math.max(x - 0.02, s.rear()))) / 0.04; }

    /** The farthest x from x0 in a direction (+1 forward, -1 aft) up to which a test holds, in 1 cm steps within the body. */
    public static double scan(BodySurface s, double x0, int dir, DoublePredicate holds) {
        double x = x0;
        while (true) {
            double next = x + dir * 0.01;
            if (next > s.front() || next < s.rear() || !holds.test(next)) { return x; }
            x = next;
        }
    }

    /**
     * A window clear of a door, or split by it: the part over the door within its frame becomes the door's window, the
     * rest (ahead of it, behind it and above it, a frame's width clear of the door) stays the body's. Pieces under 8 cm
     * long are dropped.
     */
    private List<Opening> split(Opening w, Opening door, int doorIndex) {
        if (w.side() != door.side() || w.side() == 0) { return List.of(w); }
        double f = WINDOW_IN_DOOR_FRAME_M, a = Math.max(w.x0(), door.x0() - f), b = Math.min(w.x1(), door.x1() + f);
        boolean touches = false;
        for (int k = 0; k <= 16 && b > a; k++) {
            double x = a + (b - a) * k / 16, xd = Math.clamp(x, door.x0(), door.x1()), dl = door.loAt(xd), dh = door.hiAt(xd);
            touches |= w.loAt(x) <= dh + across(x, dh, f) && w.hiAt(x) >= dl - across(x, dl, f);
        }
        if (!touches) { return List.of(w); }
        int side = w.side();
        // as port-side curves again (strip mirrors starboard back)
        DoubleUnaryOperator lo = x -> side > 0 ? 2 * Math.PI - w.hiAt(x) : w.loAt(x), hi = x -> side > 0 ? 2 * Math.PI - w.loAt(x) : w.hiAt(x);
        DoubleUnaryOperator dLo = x -> {
            double xd = Math.clamp(x, door.x0(), door.x1());
            return side > 0 ? 2 * Math.PI - door.hiAt(xd) : door.loAt(xd);
        };
        DoubleUnaryOperator dHi = x -> {
            double xd = Math.clamp(x, door.x0(), door.x1());
            return side > 0 ? 2 * Math.PI - door.loAt(xd) : door.hiAt(xd);
        };
        double e0 = door.x0() - f, e1 = door.x1() + f;
        DoubleUnaryOperator restLo = x -> {
            double l = lo.applyAsDouble(x);
            if (x < e0 || x > e1) { return l; }
            double top = dHi.applyAsDouble(x);
            return Math.max(l, top + across(x, top, f));
        };
        List<Opening> out = new ArrayList<>();
        for (Opening o : strips(w.type(), w.module(), side, w.x0(), w.x1(), 0.02, restLo, hi, -1, e0 - 1e-5, e0 + 1e-5, e1 - 1e-5, e1 + 1e-5)) {
            if (o.x1() - o.x0() >= 0.08) { out.add(o); }
        }
        Opening in = strip(w.type(), w.module(), side, Math.max(w.x0(), door.x0() + f), Math.min(w.x1(), door.x1() - f), 0.02,
                x -> Math.max(lo.applyAsDouble(x), dLo.applyAsDouble(x) + across(x, dLo.applyAsDouble(x), f)),
                x -> Math.min(hi.applyAsDouble(x), dHi.applyAsDouble(x) - across(x, dHi.applyAsDouble(x), f)), doorIndex, null);
        if (in != null && in.x1() - in.x0() >= 0.08) { out.add(in); }
        return out;
    }

    // ---------------------------------------------------------------- aircraft and boats

    /**
     * A cockpit's windscreen (two panes either side of a 7 cm post, 0.07 rad) over its forward part, where its top
     * rises from the nose (a third to three fifths of its length), the lower edge at 60 % of the height rising toward
     * the ends; and a side window each side behind it at 67 % of the height.
     */
    private void cockpit(int i, List<Opening> out) {
        double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), len = front - rear;
        double x1 = front - 0.03, steep = scan(s, front - 0.02, -1, x -> slope(s, x) < -0.18);
        double x0 = Math.clamp(steep, front - 0.6 * len, front - 0.35 * len), xc = 0.5 * (x0 + x1), hl = 0.5 * (x1 - x0);
        DoubleUnaryOperator edge = x -> {
            double b = s.bottom(x), h = s.top(x) - b;
            return b + (0.6 + 0.28 * (1 - bump(x - xc, hl, 0.45))) * h;
        };
        for (int side : SIDES) {
            Opening pane = strip(Type.WINDSHIELD, i, side, x0, x1, 0.03, x -> s.portAngle(x, edge.applyAsDouble(x)), x -> Math.PI - 0.035, -1, null);
            if (pane != null) { out.add(pane); }
        }
        double sx0 = rear + 0.08 * len, sx1 = x0 - 0.06;
        if (sx1 - sx0 > 0.15) {
            double xm = 0.5 * (sx0 + sx1), b = s.bottom(xm), h = s.top(xm) - b;
            for (int side : SIDES) {
                Opening o = box(Type.SIDE_WINDOW, i, side, xm, b + 0.67 * h, sx1 - sx0, 0.32 * h, 0.08, 0.025);
                if (o != null) { out.add(o); }
            }
        }
    }

    /** A cabin's windows: a row each side at 64 % of the height, about 0.55 m apart, their size from the window share. */
    private void cabinWindows(int i, List<Opening> out) {
        BodyModuleDesign m = module(i);
        double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), len = front - rear;
        double x0 = rear + 0.1 * len, usable = 0.8 * len;
        if (usable < 0.3) { return; }
        int count = Math.max(1, (int) Math.floor(usable / 0.55));
        double pitch = usable / count, share = Math.min(m.windowShare(), 0.6);
        double w = Math.clamp(pitch * (0.35 + 1.1 * share), 0.16, Math.max(0.16, pitch - 0.1));
        for (int k = 0; k < count; k++) {
            double xc = x0 + (k + 0.5) * pitch, b = s.bottom(xc), h = s.top(xc) - b, wh = Math.clamp(0.24 * h + 0.06, 0.16, 0.5);
            for (int side : SIDES) {
                Opening o = box(Type.CABIN_WINDOW, i, side, xc, b + 0.64 * h, w, wh, 0.35 * Math.min(w, wh), Math.min(0.025, w / 10));
                if (o != null) { out.add(o); }
            }
        }
    }

    /** A closed boat module's portholes, high on its sides (76 % up), their size from the window share, above the waterline. */
    private void portholes(int i, List<Opening> out) {
        BodyModuleDesign m = module(i);
        if (m.windowShare() <= 0) { return; }
        double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), len = front - rear;
        double dia = Math.clamp(0.14 + 0.4 * m.windowShare(), 0.14, 0.4);
        int count = Math.max(1, (int) Math.floor(0.8 * len / (2.2 * dia)));
        double pitch = 0.8 * len / count, x0 = rear + 0.1 * len;
        for (int k = 0; k < count; k++) {
            double xc = x0 + (k + 0.5) * pitch, b = s.bottom(xc), h = s.top(xc) - b, yc = b + 0.76 * h;
            if (yc - dia / 2 < waterline + 0.12) { continue; }
            for (int side : SIDES) {
                Opening o = box(Type.PORTHOLE, i, side, xc, yc, dia, dia, dia / 2, dia / 14);
                if (o != null) { out.add(o); }
            }
        }
    }

    /**
     * Doors evenly along a module, alternately starboard and port (the first to starboard), from 7 % of the height up
     * by the door's height, rounded at their corners, hinged at their front edges.
     */
    private void doors(int i, List<Opening> out) {
        BodyModuleDesign m = module(i);
        if (m.doors() <= 0) { return; }
        double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), len = front - rear;
        for (int side : new int[]{1, -1}) {
            int perSide = side > 0 ? (m.doors() + 1) / 2 : m.doors() / 2;
            for (int k = 0; k < perSide; k++) {
                double xc = front - (k + 0.5) / perSide * len, w = Math.min(m.doorWidthM(), 0.9 * len / perSide);
                double b = s.bottom(xc), h = s.top(xc) - b, y0 = b + 0.07 * h, y1 = Math.min(y0 + m.doorHeightM(), s.top(xc) - 0.06 * h);
                if (y1 - y0 < 0.2 || w < 0.2) { continue; }
                double yc = 0.5 * (y0 + y1), hh = 0.5 * (y1 - y0);
                Opening o = strip(Type.DOOR, i, side, xc - w / 2, xc + w / 2, 0.03,
                        x -> s.portAngle(x, yc - rounded(x - xc, w / 2, hh, 0.06)), x -> s.portAngle(x, yc + rounded(x - xc, w / 2, hh, 0.06)), -1, null);
                if (o != null) { out.add(withHinge(o, y0 + 0.05, y1 - 0.05)); }
            }
        }
    }

    /** A door hinged along its front edge between two heights, opening outward. */
    private Opening withHinge(Opening o, double yLow, double yHigh) {
        double x = o.x1() - 0.01;
        boolean sb = o.side() > 0;
        Hinge h = new Hinge(s.point(x, s.sideAngle(x, yLow, sb)), s.point(x, s.sideAngle(x, yHigh, sb)), sb ? 1 : -1);
        return new Opening(o.id(), o.type(), o.module(), o.side(), o.xs(), o.lo(), o.hi(), o.door(), h);
    }

    /** An open module's top: everything above its open edge, row by row at its stations. */
    private Opening openTop(int i) {
        double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), sill = shape.modules().get(i).sillM();
        List<double[]> rows = new ArrayList<>();
        for (int j = 0; j < s.xs.length; j++) {
            double x = s.xs[j];
            if (x < rear - 1e-6 || x > front + 1e-6) { continue; }
            if (!rows.isEmpty() && x - rows.get(rows.size() - 1)[0] < 1e-6) { continue; }
            double t = s.portAngleAt(j, sill);
            rows.add(new double[]{x, t, 2 * Math.PI - t});
        }
        if (rows.size() < 2) { return null; }
        double[] xs = new double[rows.size()], lo = new double[rows.size()], hi = new double[rows.size()];
        for (int r = 0; r < rows.size(); r++) { xs[r] = rows.get(r)[0]; lo[r] = rows.get(r)[1]; hi[r] = rows.get(r)[2]; }
        return new Opening(-1, Type.OPEN, i, 0, xs, lo, hi, -1, null);
    }

    // ---------------------------------------------------------------- cars

    /** The car's axles: each distinct axle mount point (the hubs' height), front to rear. */
    public static List<Vec3d> axles(BodyDesign d, BodyShape shape) {
        List<Vec3d> out = new ArrayList<>();
        for (Map.Entry<String, Mount> e : d.mountPoints(shape).entrySet()) {
            if (!e.getKey().endsWith("_axle")) { continue; }
            Vec3d p = e.getValue().position();
            if (out.stream().noneMatch(q -> Math.abs(q.x() - p.x()) < 0.05)) { out.add(p); }
        }
        out.sort((a, b) -> Double.compare(b.x(), a.x()));
        return out;
    }

    private int moduleAt(double x) {
        for (int i = 0; i < d.modules().size(); i++) {
            if (x >= shape.moduleRearX(i) - 1e-9 && x <= shape.moduleFrontX(i) + 1e-9) { return i; }
        }
        return d.modules().size() - 1;
    }

    /** Where a car's side meets its underside at x (port): past the bottom's flat and 80 % of the way out. */
    private double lowerSide(double x) {
        double half = 0.5 * s.width(x), lo = 0, hi = Math.PI / 2;
        if (Math.abs(s.point(x, hi).z()) < 0.8 * half) { return s.portAngle(x, s.bottom(x) + 0.002); }
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (lo + hi);
            if (Math.abs(s.point(x, mid).z()) < 0.8 * half) { lo = mid; } else { hi = mid; }
        }
        return Math.max(hi, s.portAngle(x, s.bottom(x) + 0.002));
    }

    private void car(List<Opening> windows, List<Opening> doors, List<Opening> arches) {
        boolean any = false;
        for (BodyModuleDesign m : d.modules()) { any |= m.kind().family == Family.CAR; }
        if (!any) { return; }
        double bottom = Double.POSITIVE_INFINITY;
        for (BodyShape.Station st : s.stations) { bottom = Math.min(bottom, st.bottomM()); }
        // the wheel arches: from the side's foot up round the hub
        List<Opening> portArches = new ArrayList<>();
        double r = d.settings().archRadiusM();
        for (Vec3d axle : axles(d, shape)) {
            double xa = axle.x(), yc = axle.y();
            for (int side : SIDES) {
                Opening o = strip(Type.ARCH, moduleAt(xa), side, xa - r, xa + r, 0.025, this::lowerSide,
                        x -> s.portAngle(x, yc + Math.sqrt(Math.max(0, r * r - (x - xa) * (x - xa)))), -1, null);
                if (o == null) { continue; }
                arches.add(o);
                if (side < 0) { portArches.add(o); }
            }
        }
        // the passenger cell: the glazed cabin module with the most seats (its belt and doors); the crest of the roof
        // over all the glazed cabin modules
        int cell = -1;
        double cf = Double.NEGATIVE_INFINITY, cr = Double.POSITIVE_INFINITY;
        for (int i = 0; i < d.modules().size(); i++) {
            BodyModuleDesign m = module(i);
            if (m.kind().family != Family.CAR || m.section() != BodyModuleDesign.Section.CABIN || m.windowShare() <= 0 || m.open()) { continue; }
            if (cell < 0 || m.seats() > module(cell).seats()) { cell = i; }
            cf = Math.max(cf, shape.moduleFrontX(i));
            cr = Math.min(cr, shape.moduleRearX(i));
        }
        if (cell < 0) { return; }
        BodyModuleDesign m = module(cell);
        double belt = m.bottomM() + m.beltFraction() * m.heightM();
        carBelt = belt;
        double crest = 0.5 * (cf + cr), roof = 0;
        for (int k = 0; k <= 80; k++) {
            double x = cr + (cf - cr) * k / 80;
            if (s.top(x) > roof) { roof = s.top(x); crest = x; }
        }
        // the glasshouse where the body stands above the belt; its roof where the top is gentle round the crest (the
        // windscreen ahead and the rear window behind are where it falls steeply), at least 0.3 m of it
        // 0.14: within the glazed cabin modules (a notchback's trunk deck stands above the belt too: the rear window ran on
        // over the trunk lid, and the trunk read as an open bed)
        double gF = Math.min(scan(s, crest, 1, x -> s.top(x) >= belt + 0.10), cf), gR = Math.max(scan(s, crest, -1, x -> s.top(x) >= belt + 0.10), cr);
        if (gF - gR < 0.6) { return; }
        double rF = Math.clamp(scan(s, crest, 1, x -> slope(s, x) > -0.3), Math.min(crest + 0.15, gF - 0.15), gF - 0.15);
        double rR = Math.clamp(scan(s, crest, -1, x -> slope(s, x) < 0.3), gR + 0.15, Math.max(crest - 0.15, gR + 0.15));
        double cm = 0.5 * (rF + rR);
        DoubleUnaryOperator rail = x -> belt + 0.84 * (s.top(x) - belt);
        // the A pillar: from the cowl at the belt (aF) to the roof's front edge at the rail (aT), a line in (x, t);
        // the C pillar the same from the rear deck to the roof's rear edge
        double aF = gF - 0.03, aT = Math.min(rF + 0.02, aF - 0.05);
        double tA0 = s.portAngle(aF, belt + 0.06), tA1 = s.portAngle(aT, rail.applyAsDouble(aT));
        DoubleUnaryOperator a = x -> tA0 + (tA1 - tA0) * (aF - x) / (aF - aT);
        double cR = gR + 0.03, cT = Math.max(rR - 0.02, cR + 0.05);
        double tC0 = s.portAngle(cR, belt + 0.06), tC1 = s.portAngle(cT, rail.applyAsDouble(cT));
        DoubleUnaryOperator c = x -> tC0 + (tC1 - tC0) * (x - cR) / (cT - cR);
        if (gF - rF > 0.1) {
            Opening o = strip(Type.WINDSHIELD, cell, 0, aT, aF, 0.04, a, x -> 2 * Math.PI - a.applyAsDouble(x), -1, null);
            if (o != null) { windows.add(o); }
        }
        if (rR - gR > 0.1) {
            Opening o = strip(Type.REAR_WINDOW, cell, 0, cR, cT, 0.04, c, x -> 2 * Math.PI - c.applyAsDouble(x), -1, null);
            if (o != null) { windows.add(o); }
        }
        // the side glass: above the belt up to the rail, behind the A pillar, ahead of the C pillar (their lines run
        // on past the cowl and the deck, so the glass closes to a point there)
        DoubleUnaryOperator glassLo = x -> s.portAngle(x, belt + 0.03);
        DoubleUnaryOperator glassHi = x -> {
            double t = s.portAngle(x, rail.applyAsDouble(x));
            if (x >= aT) { double ta = a.applyAsDouble(x); t = Math.min(t, ta - across(x, ta, PILLAR_M)); }
            if (x <= cT) { double tc = c.applyAsDouble(x); t = Math.min(t, tc - across(x, tc, PILLAR_M)); }
            return t;
        };
        double sillY = bottom + 0.17;
        boolean split = m.doors() >= 4 || (gF - 0.06) - (gR + 0.1) > 1.9;
        List<double[]> spans = new ArrayList<>();
        if (m.doors() > 0) {
            if (split && m.doors() >= 4) { spans.add(new double[]{cm + 0.02, gF - 0.02}); spans.add(new double[]{rR + 0.08, cm - 0.02}); }
            else { spans.add(new double[]{Math.max(gF - 1.15, gR + 0.1), gF - 0.02}); }
        }
        for (int side : SIDES) {
            double rearmost = Double.POSITIVE_INFINITY;
            for (double[] span : spans) {
                // the door: from the sill (clear of an arch) up to its frame over the glass
                DoubleUnaryOperator doorLo = x -> {
                    double t = s.portAngle(x, sillY);
                    for (Opening arch : portArches) {
                        double ah = arch.hiAt(x);
                        if (!Double.isNaN(ah)) { t = Math.max(t, ah + across(x, ah, 0.03)); }
                    }
                    return t;
                };
                DoubleUnaryOperator doorHi = x -> { double t = glassHi.applyAsDouble(x); return t + across(x, t, DOOR_FRAME_M); };
                Opening door = strip(Type.DOOR, cell, side, span[0], span[1], 0.03, doorLo, doorHi, -1, null);
                if (door == null) { continue; }
                doors.add(withHinge(door, sillY + 0.05, belt));
                int index = doors.size() - 1;
                Opening glass = strip(Type.SIDE_WINDOW, cell, side, span[0] + DOOR_FRAME_M, span[1] - 0.01, 0.03, glassLo, glassHi, index, null);
                if (glass != null && glass.x1() - glass.x0() > 0.1) { windows.add(glass); }
                rearmost = Math.min(rearmost, door.x0());
            }
            if (spans.isEmpty()) {
                double[][] panes = split ? new double[][]{{gR + 0.1, cm - 0.05}, {cm + 0.05, gF - 0.06}} : new double[][]{{gR + 0.1, gF - 0.06}};
                for (double[] p : panes) {
                    Opening o = strip(Type.SIDE_WINDOW, cell, side, p[0], p[1], 0.03, glassLo, glassHi, -1, null);
                    if (o != null && o.x1() - o.x0() > 0.1) { windows.add(o); }
                }
            } else if (rearmost < Double.POSITIVE_INFINITY) {
                // a quarter window behind the last door, up to the C pillar
                Opening o = strip(Type.SIDE_WINDOW, cell, side, gR + 0.05, rearmost - 0.06, 0.03, glassLo, glassHi, -1, null);
                if (o != null && o.x1() - o.x0() > 0.1) { windows.add(o); }
            }
        }
    }

    // ---------------------------------------------------------------- people

    /** The driver's (pilot's, helmsman's) seat (BodyDesign.driverSeat: the first seat of the compiled body); -1 without seats. */
    private int driver() { return d.driverSeat(seats); }

    /**
     * 0.14: a car's dashboard, from the driver's seat (InteriorKit draws it here): its rear face 0.62 m ahead of the
     * seat's point, its front at the firewall (the frontmost lined module's front, less a centimetre) and at least 0.15
     * m deep; from 0.30 under the belt to 0.06 over it, the body's width. Null when the body has no car driver. 0.18: never
     * past the body's front (a cab-over has no hood ahead of its seats).
     */
    public record Dash(double rearX, double frontX, double bottomY, double topY) {
        /** Whether a point is inside it (with a margin, m). */
        public boolean contains(Vec3d p, double margin) {
            return p.x() >= rearX - margin && p.x() <= frontX + margin && p.y() >= bottomY - margin && p.y() <= topY + margin;
        }
    }

    public Dash dash() {
        if (driver < 0 || Double.isNaN(belt)) { return null; }
        BodyDesign.Seat st = seats.get(driver);
        if (module(st.module()).kind().family != Family.CAR) { return null; }
        double rear = st.at().x() + 0.62, front = Math.min(Math.max(firewall(rear + 0.2), rear + 0.15), shape.lengthM() - 0.02);
        return new Dash(Math.min(rear, front - 0.15), front, belt - 0.30, belt + 0.06);
    }

    /** The frontmost lined module's front (a car's firewall), or x when there is none ahead. */
    public double firewall(double x) {
        double front = x;
        for (int i = 0; i < d.modules().size(); i++) { if (lined[i] && shape.moduleFrontX(i) > front) { front = shape.moduleFrontX(i); } }
        return front - 0.01;
    }

    /** Where a seat's occupant's eyes are: 0.65 m over the seat's point (its cushion), a little behind it. */
    public static Vec3d eye(BodyDesign.Seat seat) { return seat.at().add(new Vec3d(-0.08, 0.65, 0)); }

    /** The glazed opening nearest a seat's eyes on its side (a windscreen on either), or null. */
    public Opening view(int seat) {
        BodyDesign.Seat st = seats.get(seat);
        Vec3d eye = eye(st);
        Opening best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Opening o : openings) {
            if (!o.type().glazed()) { continue; }
            if (o.side() != 0 && Math.abs(st.at().z()) > 0.05 && Math.signum(st.at().z()) != o.side()) { continue; }
            double dist = o.centre(s).subtract(eye).length();
            if (dist < bestDistance) { bestDistance = dist; best = o; }
        }
        return best;
    }
}
