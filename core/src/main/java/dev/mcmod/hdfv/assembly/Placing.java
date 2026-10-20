package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.ControlSurfaceDesign;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.PropellerDesign;
import dev.mcmod.hdfv.part.SeatDesign;
import dev.mcmod.hdfv.look.Envelope;
import dev.mcmod.hdfv.look.Envelopes;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import dev.mcmod.hdfv.part.truck.FifthWheelDesign;
import dev.mcmod.hdfv.part.truck.LandingLegsDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Laying out a vehicle in an assembly room (0.7): where a new part goes first (the mount point of its kind on a part
 * already there: an engine in the engine bay or the nose, a gearbox behind it, axles at the axle points, a wing on the
 * wing mount and the next one on the tail, a rotor on the mast, a screw at the transom, a rudder at the rudder point,
 * seats on the floor) and the ids of parts.
 * <p>
 * 0.14: an engine put in a body's bay slides along it until it fits with CLEARANCE_M round it (Fitting); a gearbox goes
 * behind the engine on the same point with its bell on the block's face, a transfer case behind the gearbox's tail
 * (their envelopes' inputX and outputX); a seat part goes free (not on the floor of module 2) when a body already has
 * seats of its own.
 * <p>
 * 0.18: a fifth wheel on a chassis's "fifth_wheel", landing legs in pairs on its "legs_N" (adding one adds the pair),
 * a cab-over cab (a body with a CAB_OVER module) on its "cab" with its front there.
 */
public final class Placing {
    private Placing() {}

    /** Mount point names a kind of part looks for, in order (regular expressions). */
    static List<String> points(PartDesign d) {
        return switch (d) {
            case PistonEngineDesign e -> List.of("engine_bay", "engine", "m\\d+_engine", "transom");
            case ShaftTurbineDesign e -> List.of("engine", "mast", "m\\d+_engine", "engine_bay");
            case JetEngineDesign e -> List.of("m\\d+_engine", "engine", "m\\d+_top");
            case TurbofanDesign e -> List.of("m\\d+_engine", "engine", "m\\d+_top");
            case MotorDesign m -> List.of("engine_bay", "engine", "m\\d+_engine", "transom");
            case GearboxDesign g -> List.of("engine_bay", "m\\d+_engine");
            case TransferCaseDesign t -> List.of("engine_bay", "m\\d+_engine");
            case AxleDesign a -> List.of("axle_\\d+", "front_axle", "rear_axle", "m\\d+_axle");
            case LandingGearDesign l -> List.of("m\\d+_bottom");
            case FifthWheelDesign f -> List.of("fifth_wheel");
            case LandingLegsDesign l -> List.of("legs_\\d+");
            case WingDesign w -> List.of("wing", "tail");
            case RotorDesign r -> List.of("mast", "tail_rotor");
            case AirPropellerDesign a -> List.of("engine");
            case PropellerDesign p -> List.of("transom");
            case ControlSurfaceDesign c -> List.of("rudder", "tail");
            case SeatDesign s -> List.of("m2_bottom", "m\\d+_bottom");
            case BodyDesign b -> cab(b) ? List.of("cab", "body") : List.of("body");
            default -> List.of();
        };
    }

    /**
     * 0.18: how high a cab-over cab's lowest bottom stands over the rails, m: just clear of them (its lower panels come down
     * over the frame and its arch over the front tyre; with 0.4 m it stood on stilts, its bumper 1.4 m up).
     */
    public static final double CAB_RAISE_M = 0.05;

    /** 0.18: a body's lowest bottom over its origin, m (a cab's floor). */
    static double lowestBottom(BodyDesign b) {
        var shape = b.shape();
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k <= 40; k++) { low = Math.min(low, shape.bottomAt(shape.lengthM() * k / 40)); }
        return low;
    }

    /** 0.18: a cab-over cab: a body with a CAB_OVER module (it stands on a chassis's "cab" with its front there). */
    public static boolean cab(BodyDesign b) {
        return b.modules().stream().anyMatch(m -> m.kind() == dev.mcmod.hdfv.part.body.BodyModuleDesign.Kind.CAB_OVER);
    }

    /** Offsets on a shared point: a gearbox behind the engine in the bay, a transfer case behind that. */
    static Mount offset(PartDesign d) {
        return switch (d) {
            case GearboxDesign g -> Mount.at(new Vec3d(-0.55, 0, 0));
            case TransferCaseDesign t -> Mount.at(new Vec3d(-1.0, -0.1, 0));
            case SeatDesign s -> Mount.at(new Vec3d(0.2, 0.35, 0));
            case BodyDesign b when cab(b) -> Mount.at(new Vec3d(-b.shape().lengthM(), CAB_RAISE_M - lowestBottom(b), 0));
            default -> Mount.ORIGIN;
        };
    }

    /**
     * Where a new part goes in a layout: on the first point of its kind that no part of the same kind holds yet (on the
     * part that has it), else at the origin. 0.15: a part's points in name order, numbers by value ("axle_2" before
     * "axle_10"; parts hand them over in no order); an axle skips a point within AXLE_APART_M along x of one an axle
     * already holds on the same part (a body's "m1_axle" where its "front_axle" is).
     */
    public static Mount suggest(VehicleBlueprint layout, PartDesign d, Map<String, Map<String, Mount>> pointsByPart) {
        if (d instanceof SeatDesign && bodySeats(layout)) { return Mount.ORIGIN; }
        for (String pattern : points(d)) {
            Pattern p = Pattern.compile(pattern);
            for (var host : pointsByPart.entrySet()) {
                List<String> names = new ArrayList<>(host.getValue().keySet());
                names.sort(Placing::byName);
                for (String point : names) {
                    if (!p.matcher(point).matches() || taken(layout, d, host.getKey(), point)) { continue; }
                    if (d instanceof AxleDesign && axleNear(layout, host.getKey(), host.getValue(), point)) { continue; }
                    return Mount.on(host.getKey(), point, inBay(layout, host.getKey(), point, d));
                }
            }
        }
        return Mount.ORIGIN;
    }

    /** How far apart along x two axle points must be for both to take an axle (0.15), m. */
    static final double AXLE_APART_M = 0.3;

    /** Whether an axle on the part already sits within AXLE_APART_M of a point along x. */
    private static boolean axleNear(VehicleBlueprint layout, String part, Map<String, Mount> points, String point) {
        double x = points.get(point).position().x();
        for (VehicleBlueprint.Entry e : layout.parts()) {
            if (!(e.design() instanceof AxleDesign) || !part.equals(e.mount().attachPart())) { continue; }
            Mount at = points.get(e.mount().attachPoint());
            if (at != null && Math.abs(at.position().x() - x) < AXLE_APART_M) { return true; }
        }
        return false;
    }

    /** Names in order with their numbers by value. */
    static int byName(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int i2 = i, j2 = j;
                while (i2 < a.length() && Character.isDigit(a.charAt(i2))) { i2++; }
                while (j2 < b.length() && Character.isDigit(b.charAt(j2))) { j2++; }
                int c = new java.math.BigInteger(a.substring(i, i2)).compareTo(new java.math.BigInteger(b.substring(j, j2)));
                if (c != 0) { return c; }
                i = i2;
                j = j2;
            } else {
                if (x != y) { return Character.compare(x, y); }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** Whether a body in the layout has seats of its own. */
    static boolean bodySeats(VehicleBlueprint layout) {
        for (VehicleBlueprint.Entry e : layout.parts()) {
            if (e.design() instanceof BodyDesign b && b.modules().stream().anyMatch(m -> m.seats() > 0)) { return true; }
        }
        return false;
    }

    /** A part's offset on a point: in a body's bay, fitted or behind what is there (0.14); elsewhere the fixed offsets. */
    static Mount inBay(VehicleBlueprint layout, String host, String point, PartDesign d) {
        VehicleBlueprint.Entry h = layout.part(host);
        if (h == null || !(h.design() instanceof BodyDesign || h.design() instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign)
                || !Fitting.BAY.matcher(point).matches() || Envelopes.of(d) == null) { return offset(d); }
        VehicleBlueprint.Entry engine = null, gearbox = null;
        for (VehicleBlueprint.Entry e : layout.parts()) {
            if (!host.equals(e.mount().attachPart()) || !point.equals(e.mount().attachPoint()) || Envelopes.of(e.design()) == null) { continue; }
            if (e.design() instanceof GearboxDesign) { gearbox = e; }
            else if (!(e.design() instanceof TransferCaseDesign)) { engine = e; }
        }
        if (d instanceof GearboxDesign) { return engine == null ? offset(d) : behind(engine.design(), engine.mount().detached(), d); }
        if (d instanceof TransferCaseDesign) { return gearbox == null ? offset(d) : behind(gearbox.design(), gearbox.mount().detached(), d); }
        if (h.design() instanceof BodyDesign body) { return slideToFit(body, point, d); }
        // 0.15: on a chassis's bay, against the body on the chassis (none yet: where the bay is)
        VehicleBlueprint probe = Placing.withPart(layout, new VehicleBlueprint.Entry("?probe", d, Mount.on(host, point)));
        Fitting.InBody in = Fitting.inBody(probe, probe.part("?probe"));
        return in == null ? Mount.ORIGIN : slideToFit(in.shape(), in.placement(), Double.POSITIVE_INFINITY, d);
    }

    /**
     * 0.14: the offset on a point that puts a part's input face against the output face of the part ahead of it (at
     * aheadAt on the same point), along the point's x at the same height and side: a gearbox's bell on an engine's
     * flywheel face, a transfer case on a gearbox's tail. Where an envelope names no face, its end (the rear of the one
     * ahead, the front of this one); so too an output facing forward (an electric motor's shaft), which nothing behind takes.
     */
    public static Mount behind(PartDesign ahead, Mount aheadAt, PartDesign d) {
        Envelope a = Envelopes.of(ahead), b = Envelopes.of(d);
        if (a == null || b == null) { return offset(d); }
        Envelope.Box ab = a.bounds(), bb = b.bounds();
        double out = Double.isNaN(a.outputX()) || a.outputX() > 0.5 * (ab.min().x() + ab.max().x()) ? ab.min().x() : a.outputX();
        double in = Double.isNaN(b.inputX()) ? bb.max().x() : b.inputX();
        return Mount.at(aheadAt.position().add(new Vec3d(out - in, 0, 0)));
    }

    /** Offsets an engine is tried at along a bay, m: back first (toward the firewall), then forward. */
    static final double[] SLIDES = {0, -0.05, -0.1, -0.15, -0.2, -0.25, -0.3, -0.35, -0.4, -0.45, -0.5, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3};

    /** 0.14: an engine slid along a body's bay to where it fits with CLEARANCE_M round it (where it sticks out least when it fits nowhere). */
    public static Mount slideToFit(BodyDesign body, String point, PartDesign d) {
        dev.mcmod.aerocore.api.body.BodyShape shape = body.shape();
        Mount at = body.mountPoints(shape).get(point);
        if (at == null) { return Mount.ORIGIN; }
        double limit = Double.POSITIVE_INFINITY;
        for (int i = 0; i < body.modules().size(); i++) {
            if (at.position().x() >= shape.moduleRearX(i) - 1e-9 && at.position().x() <= shape.moduleFrontX(i) + 1e-9) { limit = 0.5 * (shape.moduleFrontX(i) - shape.moduleRearX(i)); }
        }
        return slideToFit(shape, at, limit, d);
    }

    /** The same for a bay point at a placement in a body's shape, within a distance either way (0.15: a chassis's bay under a body). */
    static Mount slideToFit(dev.mcmod.aerocore.api.body.BodyShape shape, Mount at, double limit, PartDesign d) {
        Envelope env = Envelopes.of(d);
        if (env == null) { return Mount.ORIGIN; }
        Mount best = Mount.ORIGIN;
        double least = Double.POSITIVE_INFINITY;
        for (double dx : SLIDES) {
            if (Math.abs(dx) > limit + 1e-9) { continue; }
            Mount m = Mount.at(new Vec3d(dx, 0, 0));
            double out = Fitting.overhang(shape, env, at.then(m), Fitting.CLEARANCE_M).maxM();
            if (out <= 0) { return m; }
            if (out < least) { least = out; best = m; }
        }
        return best;
    }

    private static boolean taken(VehicleBlueprint layout, PartDesign d, String part, String point) {
        for (VehicleBlueprint.Entry e : layout.parts()) {
            if (part.equals(e.mount().attachPart()) && point.equals(e.mount().attachPoint()) && e.design().getClass() == d.getClass()) { return true; }
        }
        return false;
    }

    /** A new part id: its type's name and the first free number ("piston_engine_1"). */
    public static String id(VehicleBlueprint layout, PartDesign d) {
        String base = d.type().replaceFirst("^[a-z0-9_]+:", "");
        for (int i = 1; ; i++) {
            String id = base + "_" + i;
            if (layout.part(id) == null) { return id; }
        }
    }

    /** The mount points of a layout's parts (compiled one by one), by part id. */
    public static Map<String, Map<String, Mount>> points(VehicleBlueprint layout) {
        Map<String, Map<String, Mount>> out = new java.util.LinkedHashMap<>();
        for (VehicleBlueprint.Entry e : layout.parts()) {
            try {
                Map<String, Mount> m = e.design().compile().mountPoints();
                if (!m.isEmpty()) { out.put(e.id(), m); }
            } catch (RuntimeException ignored) {
                // a part that does not compile offers no points
            }
        }
        return out;
    }

    /** A layout with an entry added as it is (0.15: a probe for where a part would sit). */
    static VehicleBlueprint withPart(VehicleBlueprint layout, VehicleBlueprint.Entry e) {
        List<VehicleBlueprint.Entry> parts = new ArrayList<>(layout.parts());
        parts.add(e);
        return new VehicleBlueprint(layout.name(), parts, List.of());
    }

    /** A layout with a part added where it goes (0.18: a landing leg with its pair, when a second legs point is free). */
    public static VehicleBlueprint add(VehicleBlueprint layout, PartDesign d) {
        List<VehicleBlueprint.Entry> parts = new ArrayList<>(layout.parts());
        Map<String, Map<String, Mount>> points = points(layout);
        parts.add(new VehicleBlueprint.Entry(id(layout, d), d, suggest(layout, d, points)));
        VehicleBlueprint next = new VehicleBlueprint(layout.name(), parts, List.of());
        if (d instanceof LandingLegsDesign && parts.getLast().mount().attached()) {
            Mount pair = suggest(next, d, points);
            if (pair.attached()) {
                parts.add(new VehicleBlueprint.Entry(id(next, d), d, pair));
                next = new VehicleBlueprint(layout.name(), parts, List.of());
            }
        }
        return next;
    }

    /** A layout without a part; refused (IllegalArgumentException) while other parts hang on it. */
    public static VehicleBlueprint remove(VehicleBlueprint layout, String id) {
        for (VehicleBlueprint.Entry e : layout.parts()) {
            if (id.equals(e.mount().attachPart())) { throw new IllegalArgumentException(e.id()); }
        }
        return new VehicleBlueprint(layout.name(), layout.parts().stream().filter(e -> !e.id().equals(id)).toList(), List.of());
    }

    /** A layout with a part moved onto another part's mount point (part null: free, its offset from the vehicle's origin). */
    public static VehicleBlueprint attach(VehicleBlueprint layout, String id, String part, String point) {
        return replace(layout, id, e -> part == null ? e.mount().detached() : Mount.on(part, point, e.mount().detached()));
    }

    /** A layout with a part's offset moved (m, in its point's frame) and turned (degrees). */
    public static VehicleBlueprint nudge(VehicleBlueprint layout, String id, Vec3d by, double yawDeg, double pitchDeg, double rollDeg) {
        return replace(layout, id, e -> {
            Mount m = e.mount();
            return new Mount(m.position().add(by), wrap(m.yawDeg() + yawDeg), wrap(m.pitchDeg() + pitchDeg), wrap(m.rollDeg() + rollDeg), m.attachPart(), m.attachPoint());
        });
    }

    private static double wrap(double deg) { return ((deg + 180) % 360 + 360) % 360 - 180; }

    private static VehicleBlueprint replace(VehicleBlueprint layout, String id, java.util.function.Function<VehicleBlueprint.Entry, Mount> mount) {
        List<VehicleBlueprint.Entry> parts = new ArrayList<>();
        for (VehicleBlueprint.Entry e : layout.parts()) {
            parts.add(e.id().equals(id) ? new VehicleBlueprint.Entry(e.id(), e.design(), mount.apply(e)) : e);
        }
        return new VehicleBlueprint(layout.name(), parts, List.of());
    }
}
