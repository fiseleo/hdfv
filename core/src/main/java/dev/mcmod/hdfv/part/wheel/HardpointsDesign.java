package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.aerocore.api.wheel.kinematics.Hardpoints;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An axle's hardpoints (0.15, the "hardpoints" page; the user: 「懸吊硬點」): SIMPLE moves the wheel by the suspension
 * page's swing arm and instant centre as before; HARDPOINTS solves it on its links (AeroCore's KinematicTable: camber,
 * bump steer, roll centre, anti-dive and anti-squat from where the arms are). Each point is x, y, z (m) from the right
 * wheel's hub at the design compression: x forward, y up, z outboard; a blank one is where the simple geometry would put
 * it (Hardpoints.fromSimple), so switching to HARDPOINTS starts from the simple suspension's behaviour. Which points a
 * layout has (the axle mirrors its suspension's layout here):
 * <ul>
 * <li>double wishbone: both arms' front and rear pivots and ball joints, the tie rod's ends, the spring from the body
 *     to the lower arm; multi-link the same with each rear link's own point on the upright;</li>
 * <li>MacPherson: the strut's top and a point on its axis on the upright, the lower arm, the tie rod;</li>
 * <li>trailing, semi-trailing, swing axle, twist beam: the arm's two pivots and the spring;</li>
 * <li>solid axles: the locator's height (the roll centre, m above the ground), the upper and lower trailing links' ends
 *     on the body and the axle, the spring.</li>
 * </ul>
 */
public record HardpointsDesign(String name, Suspension.Type layout, Mode mode, double[] lowerFront, double[] lowerRear, double[] lowerBall, double[] lowerRearBall,
                               double[] upperFront, double[] upperRear, double[] upperBall, double[] upperRearBall, double[] strutTop, double[] strutLower,
                               double[] tieInner, double[] tieOuter, double[] springBody, double[] springLower, double[] pivotA, double[] pivotB, double locatorHeightM,
                               double[] upperLinkBody, double[] upperLinkAxle, double[] lowerLinkBody, double[] lowerLinkAxle)
        implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:hardpoints";
    /** The point fields, in order. */
    static final List<String> POINTS = List.of("lowerFront", "lowerRear", "lowerBall", "lowerRearBall", "upperFront", "upperRear", "upperBall", "upperRearBall",
            "strutTop", "strutLower", "tieInner", "tieOuter", "springBody", "springLower", "pivotA", "pivotB", "upperLinkBody", "upperLinkAxle", "lowerLinkBody",
            "lowerLinkAxle");

    public enum Mode { SIMPLE, HARDPOINTS }

    public HardpointsDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(layout, "layout"); Objects.requireNonNull(mode, "mode");
    }

    /** As the simple geometry (every point blank). */
    public static HardpointsDesign simple(Suspension.Type layout) {
        double[] e = new double[0];
        return new HardpointsDesign("hardpoints", layout, Mode.SIMPLE, e, e, e, e, e, e, e, e, e, e, e, e, e, e, e, e, Double.NaN, e, e, e, e);
    }

    @Override public String type() { return TYPE; }

    /** The same for another layout (the axle keeps its suspension's here). */
    public HardpointsDesign withLayout(Suspension.Type t) {
        return t == layout ? this : new HardpointsDesign(name, t, mode, lowerFront, lowerRear, lowerBall, lowerRearBall, upperFront, upperRear, upperBall, upperRearBall,
                strutTop, strutLower, tieInner, tieOuter, springBody, springLower, pivotA, pivotB, locatorHeightM, upperLinkBody, upperLinkAxle, lowerLinkBody, lowerLinkAxle);
    }

    /** The points a layout has. */
    static List<String> pointsOf(Suspension.Type t) {
        return switch (t) {
            case DOUBLE_WISHBONE -> List.of("lowerFront", "lowerRear", "lowerBall", "upperFront", "upperRear", "upperBall", "tieInner", "tieOuter", "springBody", "springLower");
            case MULTI_LINK -> List.of("lowerFront", "lowerRear", "lowerBall", "lowerRearBall", "upperFront", "upperRear", "upperBall", "upperRearBall", "tieInner", "tieOuter",
                    "springBody", "springLower");
            case MACPHERSON -> List.of("strutTop", "strutLower", "lowerFront", "lowerRear", "lowerBall", "tieInner", "tieOuter");
            case TRAILING_ARM, SEMI_TRAILING_ARM, SWING_AXLE, TWIST_BEAM -> List.of("pivotA", "pivotB", "springBody", "springLower");
            case SOLID_PANHARD, SOLID_WATT, SOLID_LEAF -> List.of("upperLinkBody", "upperLinkAxle", "lowerLinkBody", "lowerLinkAxle", "springBody", "springLower");
        };
    }

    @Override public boolean shows(String parameter) {
        if (parameter.equals("layout")) { return false; }
        if (parameter.equals("mode")) { return true; }
        if (mode == Mode.SIMPLE) { return false; }
        if (parameter.equals("locatorHeightM")) { return layout.solid(); }
        return pointsOf(layout).contains(parameter);
    }

    /** The point fields' values by name. */
    Map<String, double[]> points() {
        Map<String, double[]> m = new LinkedHashMap<>();
        double[][] v = {lowerFront, lowerRear, lowerBall, lowerRearBall, upperFront, upperRear, upperBall, upperRearBall, strutTop, strutLower, tieInner, tieOuter,
                springBody, springLower, pivotA, pivotB, upperLinkBody, upperLinkAxle, lowerLinkBody, lowerLinkAxle};
        for (int i = 0; i < POINTS.size(); i++) { m.put(POINTS.get(i), v[i]); }
        return m;
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (mode == Mode.SIMPLE) { return issues; }
        for (String p : pointsOf(layout)) {
            double[] v = points().get(p);
            if (v.length == 0) { continue; }
            if (v.length != 3 || Arrays.stream(v).anyMatch(x -> !Double.isFinite(x) || Math.abs(x) > 3)) {
                issues.add(DesignIssue.invalid(p, "A point is three numbers, x y z from the hub, within 3 m (or blank for the simple geometry's)"));
            }
        }
        if (layout.solid() && !Double.isNaN(locatorHeightM) && !(locatorHeightM >= 0 && locatorHeightM <= 2)) {
            issues.add(DesignIssue.invalid("locatorHeightM", "The locator is 0 to 2 m above the ground (blank: the simple geometry's)"));
        }
        return issues;
    }

    @Override public double massKg() { return 0; }

    /** 0.15: where a named point field is in resolved hardpoints (its own or the simple geometry's), null when they have none of that name. */
    static Vec3d resolved(String name, Hardpoints h) {
        return switch (h) {
            case Hardpoints.DoubleWishbone d -> switch (name) {
                case "lowerFront" -> d.lowerFront(); case "lowerRear" -> d.lowerRear(); case "lowerBall" -> d.lowerBall();
                case "upperFront" -> d.upperFront(); case "upperRear" -> d.upperRear(); case "upperBall" -> d.upperBall();
                case "tieInner" -> d.tieInner(); case "tieOuter" -> d.tieOuter(); case "springBody" -> d.springBody(); case "springLower" -> d.springArm();
                default -> null;
            };
            case Hardpoints.MultiLink m -> switch (name) {
                case "lowerFront" -> m.links().get(0).body(); case "lowerBall" -> m.links().get(0).upright();
                case "lowerRear" -> m.links().get(1).body(); case "lowerRearBall" -> m.links().get(1).upright();
                case "upperFront" -> m.links().get(2).body(); case "upperBall" -> m.links().get(2).upright();
                case "upperRear" -> m.links().get(3).body(); case "upperRearBall" -> m.links().get(3).upright();
                case "tieInner" -> m.links().get(4).body(); case "tieOuter" -> m.links().get(4).upright();
                case "springBody" -> m.springBody(); case "springLower" -> m.springUpright();
                default -> null;
            };
            case Hardpoints.MacPherson m -> switch (name) {
                case "strutTop" -> m.strutTop(); case "strutLower" -> m.strutLower(); case "lowerFront" -> m.lowerFront(); case "lowerRear" -> m.lowerRear();
                case "lowerBall" -> m.lowerBall(); case "tieInner" -> m.tieInner(); case "tieOuter" -> m.tieOuter();
                default -> null;
            };
            case Hardpoints.TrailingArm t -> switch (name) {
                case "pivotA" -> t.pivotA(); case "pivotB" -> t.pivotB(); case "springBody" -> t.springBody(); case "springLower" -> t.springArm();
                default -> null;
            };
            case Hardpoints.SolidAxle a -> switch (name) {
                case "upperLinkBody" -> a.upperBody(); case "upperLinkAxle" -> a.upperAxle(); case "lowerLinkBody" -> a.lowerBody(); case "lowerLinkAxle" -> a.lowerAxle();
                case "springBody" -> a.springBody(); case "springLower" -> a.springAxle();
                default -> null;
            };
        };
    }

    /** A given point at the hub, or the simple geometry's. */
    private static Vec3d at(double[] given, Vec3d hub, Vec3d simple) { return given.length == 3 ? hub.add(new Vec3d(given[0], given[1], given[2])) : simple; }

    /**
     * The right wheel's hardpoints in the axle's frame for a hub at the design compression, the tyre's radius and the
     * suspension's simple geometry (its blank points' source).
     */
    public Hardpoints hardpoints(Vec3d hub, double trackM, double tyreRadiusM, SuspensionDesign s) {
        Suspension.Type base = layout == Suspension.Type.MULTI_LINK ? Suspension.Type.DOUBLE_WISHBONE : layout;
        Hardpoints simple = Hardpoints.fromSimple(base, trackM, hub, tyreRadiusM, s.swingArmM(), s.linkHeightM(), Math.toRadians(s.sweepDeg()));
        return switch (simple) {
            case Hardpoints.DoubleWishbone d -> {
                Vec3d lf = at(lowerFront, hub, d.lowerFront()), lr = at(lowerRear, hub, d.lowerRear()), lb = at(lowerBall, hub, d.lowerBall());
                Vec3d uf = at(upperFront, hub, d.upperFront()), ur = at(upperRear, hub, d.upperRear()), ub = at(upperBall, hub, d.upperBall());
                Vec3d ti = at(tieInner, hub, d.tieInner()), to = at(tieOuter, hub, d.tieOuter()), sb = at(springBody, hub, d.springBody()), sl = at(springLower, hub, d.springArm());
                if (layout != Suspension.Type.MULTI_LINK) { yield new Hardpoints.DoubleWishbone(hub, lf, lr, lb, uf, ur, ub, ti, to, sb, sl); }
                Vec3d lrb = at(lowerRearBall, hub, lb), urb = at(upperRearBall, hub, ub);
                yield new Hardpoints.MultiLink(hub, List.of(new Hardpoints.Link(lf, lb), new Hardpoints.Link(lr, lrb), new Hardpoints.Link(uf, ub), new Hardpoints.Link(ur, urb),
                        new Hardpoints.Link(ti, to)), sb, sl);
            }
            case Hardpoints.MacPherson m -> new Hardpoints.MacPherson(hub, at(strutTop, hub, m.strutTop()), at(strutLower, hub, m.strutLower()),
                    at(lowerFront, hub, m.lowerFront()), at(lowerRear, hub, m.lowerRear()), at(lowerBall, hub, m.lowerBall()), at(tieInner, hub, m.tieInner()),
                    at(tieOuter, hub, m.tieOuter()));
            case Hardpoints.TrailingArm t -> new Hardpoints.TrailingArm(hub, at(pivotA, hub, t.pivotA()), at(pivotB, hub, t.pivotB()), at(springBody, hub, t.springBody()),
                    at(springLower, hub, t.springArm()));
            case Hardpoints.SolidAxle a -> new Hardpoints.SolidAxle(hub, Double.isNaN(locatorHeightM) ? a.locatorY() : hub.y() - tyreRadiusM + locatorHeightM,
                    at(upperLinkBody, hub, a.upperBody()), at(upperLinkAxle, hub, a.upperAxle()), at(lowerLinkBody, hub, a.lowerBody()), at(lowerLinkAxle, hub, a.lowerAxle()),
                    at(springBody, hub, a.springBody()), at(springLower, hub, a.springAxle()));
            case Hardpoints.MultiLink ml -> ml;
        };
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("layout", layout.name().toLowerCase(java.util.Locale.ROOT), "mode", mode.name().toLowerCase(java.util.Locale.ROOT));
        for (Map.Entry<String, double[]> e : points().entrySet()) {
            if (e.getValue().length == 0) { continue; }
            List<Object> l = new ArrayList<>();
            for (double v : e.getValue()) { l.add(v); }
            m.put(e.getKey(), l);
        }
        if (!Double.isNaN(locatorHeightM)) { m.put("locatorHeightM", locatorHeightM); }
        return m;
    }

    private static double[] point(Fields f, String key) {
        List<Object> l = f.list(key, List.of());
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) { v[i] = ((Number) l.get(i)).doubleValue(); }
        return v;
    }

    public static HardpointsDesign read(String name, Fields f) {
        return new HardpointsDesign(name, Rules.choice(f, "layout", Suspension.Type.class, Suspension.Type.MACPHERSON), Rules.choice(f, "mode", Mode.class, Mode.SIMPLE),
                point(f, "lowerFront"), point(f, "lowerRear"), point(f, "lowerBall"), point(f, "lowerRearBall"), point(f, "upperFront"), point(f, "upperRear"),
                point(f, "upperBall"), point(f, "upperRearBall"), point(f, "strutTop"), point(f, "strutLower"), point(f, "tieInner"), point(f, "tieOuter"),
                point(f, "springBody"), point(f, "springLower"), point(f, "pivotA"), point(f, "pivotB"), f.number("locatorHeightM", Double.NaN),
                point(f, "upperLinkBody"), point(f, "upperLinkAxle"), point(f, "lowerLinkBody"), point(f, "lowerLinkAxle"));
    }

    private Object[] values() {
        List<Object> v = new ArrayList<>(List.of(name, layout, mode, locatorHeightM));
        v.addAll(points().values());
        return v.toArray();
    }

    @Override public boolean equals(Object o) { return o instanceof HardpointsDesign h && Arrays.deepEquals(values(), h.values()); }
    @Override public int hashCode() { return Arrays.deepHashCode(values()); }
    @Override public String toString() { return "HardpointsDesign[" + layout + ", " + mode + "]"; }
}
