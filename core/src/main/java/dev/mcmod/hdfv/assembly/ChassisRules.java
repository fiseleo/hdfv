package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.vehicle.Clearance;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.look.ChassisLayout;
import dev.mcmod.hdfv.look.Envelope;
import dev.mcmod.hdfv.look.Envelopes;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign;
import dev.mcmod.hdfv.part.chassis.ChassisStationDesign;
import dev.mcmod.hdfv.part.truck.FifthWheelDesign;
import dev.mcmod.hdfv.part.truck.LandingLegsDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.15: what an assembly room checks of a vehicle on a chassis (and of any car's underside):
 * <ul>
 * <li>a chassis carrying more than its design gross mass (its load cases were worked out at that): frame_overloaded;</li>
 * <li>a body made to sit on a frame (onFrame) that is not on a chassis's "body" point: body_needs_frame;</li>
 * <li>an axle on a chassis under a body with no wheel arch there (an axle point of the body's within ARCH_M along x):
 *     no_wheel_arch (the body's modules' axle flags put its arches); 0.18: only an axle under the body that comes down
 *     over its wheel (within its length, its bottom lower than the tyre's top and ARCH_CLEAR_M), so a short cab's tandem
 *     behind it and a deck over the tyres need none;</li>
 * <li>the underside (a body's bottom line, a frame's rails and tubes, the engines', gearboxes' and transfer cases'
 *     envelopes) below the ground under the wheels at the design compression: touches_ground (AeroCore's Clearance).</li>
 * <li>0.18, trucks (truck): a fifth wheel not on a chassis's "fifth_wheel": fifth_wheel_off_frame; a chassis with a
 *     kingpin and no landing legs on it (a trailer that cannot stand alone): kingpin_without_legs; an engine under a
 *     cab-over cab whose top rises more than ENGINE_IN_CAB_M over the cab's floor or into a seat: engine_in_cab.</li>
 * </ul>
 * A body on a chassis's "cab" (a cab-over) counts as on the frame.
 */
public final class ChassisRules {
    private ChassisRules() {}

    /** How near along x a body's arch must be to an axle on a chassis, m. */
    public static final double ARCH_M = 0.15;
    /** 0.18: how far over a tyre's top a body's bottom must stay to need no arch, m. */
    public static final double ARCH_CLEAR_M = 0.05;
    /** 0.18: how far an engine under a cab-over may rise over the cab's floor (its tunnel), m. */
    public static final double ENGINE_IN_CAB_M = 0.45;

    /** 0.18: a truck's problem: its key (hdfv.room.problem.KEY) and the parts it names. */
    public record TruckIssue(String key, String parts) {}

    /** A chassis carrying more than it was designed for: its id, the vehicle's mass and its design gross mass, kg. */
    public record Overload(String part, double vehicleKg, double designKg) {}

    /** The bodies on a chassis: by chassis id, the body entries attached to its "body" points. */
    static Map<String, List<VehicleBlueprint.Entry>> bodiesOn(VehicleBlueprint b) {
        Map<String, List<VehicleBlueprint.Entry>> out = new java.util.LinkedHashMap<>();
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (!(e.design() instanceof BodyDesign) || !e.mount().attached()) { continue; }
            VehicleBlueprint.Entry host = b.part(e.mount().attachPart());
            if (host != null && host.design() instanceof ChassisDesign && (e.mount().attachPoint().startsWith("body") || e.mount().attachPoint().equals("cab"))) {
                out.computeIfAbsent(host.id(), k -> new ArrayList<>()).add(e);
            }
        }
        return out;
    }

    /** Every chassis that carries more than its design gross mass (the vehicle with its tanks full). */
    public static List<Overload> overloads(VehicleBlueprint b, Vehicle v) {
        List<Overload> out = new ArrayList<>();
        double mass = v.properties().massKg();
        for (Vehicle.Tank t : v.tanks()) { mass += t.tank().initialKg(); }
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (e.design() instanceof ChassisDesign c && mass > c.frame().designGrossKg() * 1.001) { out.add(new Overload(e.id(), mass, c.frame().designGrossKg())); }
        }
        return out;
    }

    /** Bodies made to sit on a frame that are not on a chassis. */
    public static List<String> bodiesNeedingFrame(VehicleBlueprint b) {
        List<String> out = new ArrayList<>();
        Map<String, List<VehicleBlueprint.Entry>> on = bodiesOn(b);
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (e.design() instanceof BodyDesign body && body.settings().onFrame() && on.values().stream().noneMatch(l -> l.contains(e))) { out.add(e.id()); }
        }
        return out;
    }

    /** Axles on a chassis with a body on it that has no arch over them. */
    public static List<String> missingArches(VehicleBlueprint b, Vehicle v) {
        List<String> out = new ArrayList<>();
        Map<String, List<VehicleBlueprint.Entry>> on = bodiesOn(b);
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (!(e.design() instanceof AxleDesign) || !e.mount().attached() || !on.containsKey(e.mount().attachPart())) { continue; }
            double x = v.installed(e.id()).mount().position().x();
            AxleDesign axle = (AxleDesign) e.design();
            double tyreTop = v.installed(e.id()).mount().apply(new Vec3d(0, -axle.layout().hubDropM(), 0)).y() + axle.tyre().tyre().radiusM();
            boolean arched = false, under = false;
            for (VehicleBlueprint.Entry body : on.get(e.mount().attachPart())) {
                BodyDesign d = (BodyDesign) body.design();
                Vehicle.Installed at = v.installed(body.id());
                BodyShape s = d.shape();
                double bx = x - at.mount().position().x();
                if (bx >= 0 && bx <= s.lengthM() && at.mount().apply(new Vec3d(bx, s.bottomAt(bx), 0)).y() < tyreTop + ARCH_CLEAR_M) { under = true; }
                for (Map.Entry<String, Mount> p : d.mountPoints(d.shape()).entrySet()) {
                    String k = p.getKey();
                    if (!(k.equals("front_axle") || k.equals("rear_axle") || k.matches("m\\d+_axle"))) { continue; }
                    arched |= Math.abs(at.mount().apply(p.getValue().position()).x() - x) <= ARCH_M;
                }
            }
            if (under && !arched) { out.add(e.id()); }
        }
        return out;
    }

    /** 0.18: a truck's problems (see the class). */
    public static List<TruckIssue> truck(VehicleBlueprint b, Vehicle v) {
        List<TruckIssue> out = new ArrayList<>();
        List<String> off = new ArrayList<>(), legless = new ArrayList<>(), inCab = new ArrayList<>();
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (e.design() instanceof FifthWheelDesign) {
                VehicleBlueprint.Entry host = e.mount().attached() ? b.part(e.mount().attachPart()) : null;
                if (host == null || !(host.design() instanceof ChassisDesign) || !"fifth_wheel".equals(e.mount().attachPoint())) { off.add(e.id()); }
            }
            if (e.design() instanceof ChassisDesign c && c.stations().stream().anyMatch(s -> s.kind() == ChassisStationDesign.Kind.KINGPIN)
                    && b.parts().stream().noneMatch(p -> p.design() instanceof LandingLegsDesign && e.id().equals(p.mount().attachPart()))) { legless.add(e.id()); }
        }
        Map<String, List<VehicleBlueprint.Entry>> on = bodiesOn(b);
        for (Vehicle.Installed i : v.installed()) {
            if (!AutoWiring.engine(i.part().design())) { continue; }
            VehicleBlueprint.Entry entry = b.part(i.id());
            Envelope env = Envelopes.of(i.part().design());
            if (env == null || entry == null || !entry.mount().attached() || !on.containsKey(entry.mount().attachPart())) { continue; }
            Envelope.Box box = env.bounds();
            Vec3d top = i.mount().apply(new Vec3d(0.5 * (box.min().x() + box.max().x()), box.max().y(), 0));
            double halfLength = 0.5 * (box.max().x() - box.min().x());
            for (VehicleBlueprint.Entry body : on.get(entry.mount().attachPart())) {
                if (!(body.design() instanceof BodyDesign d) || !Placing.cab(d)) { continue; }
                Vehicle.Installed at = v.installed(body.id());
                BodyShape s = d.shape();
                double bx = top.x() - at.mount().position().x();
                if (bx < 0 || bx > s.lengthM()) { continue; }
                double floor = at.mount().apply(new Vec3d(bx, s.bottomAt(bx), 0)).y();
                boolean seat = false;
                for (BodyDesign.Seat st : d.seats(s)) {
                    Vec3d w = at.mount().apply(st.at());
                    seat |= Math.abs(w.x() - top.x()) < halfLength + 0.2 && top.y() > w.y() - 0.1;
                }
                if (top.y() > floor + ENGINE_IN_CAB_M || seat) { inCab.add(i.id()); break; }
            }
        }
        if (!off.isEmpty()) { out.add(new TruckIssue("fifth_wheel_off_frame", String.join(", ", off))); }
        if (!legless.isEmpty()) { out.add(new TruckIssue("kingpin_without_legs", String.join(", ", legless))); }
        if (!inCab.isEmpty()) { out.add(new TruckIssue("engine_in_cab", String.join(", ", inCab))); }
        return out;
    }

    /**
     * The car's clearance over the ground at the design compression (null without two or more axles): the underside of
     * its bodies, frames, engines, gearboxes and transfer cases in side view over its wheels.
     */
    public static Clearance.Result clearance(VehicleBlueprint b, Vehicle v) {
        List<Clearance.Wheel> wheels = new ArrayList<>();
        List<double[]> under = new ArrayList<>();
        for (Vehicle.Installed i : v.installed()) {
            var d = i.part().design();
            Mount m = i.mount();
            if (d instanceof AxleDesign a) {
                Vec3d hub = m.apply(new Vec3d(0, -a.layout().hubDropM(), 0));
                var t = a.tyre().tyre();
                double r = t.radiusM() - t.deflectionM(a.cornerLoadN());
                wheels.add(new Clearance.Wheel(hub.x(), hub.y(), r));
            } else if (d instanceof BodyDesign body) {
                BodyShape s = body.shape();
                for (int k = 0; k <= 40; k++) {
                    double x = s.lengthM() * k / 40;
                    Vec3d p = m.apply(new Vec3d(x, s.bottomAt(x), 0));
                    under.add(new double[]{p.x(), p.y()});
                }
            } else if (d instanceof ChassisDesign c) {
                ChassisLayout l = c.analysis().layout();
                double half = c.frame().kind() == ChassisSettingsDesign.Kind.SPACE_FRAME ? c.frame().tubeDiameterM() / 2 : l.railHeightM / 2;
                for (Vec3d n : l.frame.nodes()) {
                    Vec3d p = m.apply(new Vec3d(n.x(), n.y() - half, n.z()));
                    under.add(new double[]{p.x(), p.y()});
                }
            } else {
                Envelope e = Envelopes.of(d);
                if (e == null) { continue; }
                Envelope.Box box = e.bounds();
                for (double x : new double[]{box.min().x(), box.max().x()}) {
                    Vec3d p = m.apply(new Vec3d(x, box.min().y(), 0));
                    under.add(new double[]{p.x(), p.y()});
                }
            }
        }
        if (wheels.size() < 2 || under.isEmpty()) { return null; }
        return Clearance.of(under, wheels);
    }
}
