package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.StructureWork;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.part.PropellerDesign;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What putting a vehicle together in an assembly room takes (0.7): its room; the finished parts made on assembly
 * benches (engines, gearboxes, axles, propellers...), by design; for the structure built in the room (bodies, wings),
 * the components made in the workshop and the stock and filler of its skins and seams; the labour, the cure time; the
 * heaviest part to lift; the vehicle's size with its rotor and propeller discs; (0.8) the engines that burn fuel on a
 * vehicle without a tank; and (0.14) the parts that do not fit in their body's engine bay (Fitting) and the seat parts
 * that sit on a body's own seats or in its dashboard; (0.15, ChassisRules) chassis carrying more than they were designed
 * for, bodies made for a frame without one, axles on a chassis without a wheel arch over them, and the car's clearance;
 * (0.18) a truck's own problems (ChassisRules.truck).
 */
public record AssemblyPlan(Room room, List<Need> parts, List<Piece> pieces, Map<Alloy, Double> stockKg, List<StructureWork> structure,
                           double laborHours, double cureHours, double heaviestKg, Vec3d min, Vec3d max, List<String> tankless,
                           List<Fitting.Misfit> misfits, List<String> seatClashes, List<ChassisRules.Overload> overloads, List<String> bodiesNeedingFrame,
                           List<String> missingArches, dev.mcmod.aerocore.api.vehicle.Clearance.Result clearance, List<ChassisRules.TruckIssue> truck) {
    /** Finished part items of one design. */
    public record Need(PartDesign design, int count) {}
    /** Finished workpieces of a room-built design's component. */
    public record Piece(PartDesign design, Workpiece workpiece) {}

    /** Fitting a part: rigging, bolting, connecting, s, and per kg. */
    public static final double INSTALL_S = 900, INSTALL_S_PER_KG = 2;
    /** Parts heavier than this need an overhead crane, kg (more than two people lift). */
    public static final double LIFT_KG = 50;

    public double lengthM() { return max.x() - min.x(); }
    public double heightM() { return max.y() - min.y(); }
    public double widthM() { return max.z() - min.z(); }
    public boolean needsCrane() { return heaviestKg > LIFT_KG; }
    /** 0.17: the hours of the structure's rivets set by hand (StructurePlanner: a rivet drilled, deburred and set). */
    public double rivetingHours() {
        return structure.stream().filter(s -> s.joint() == dev.mcmod.hdfv.manufacture.Joint.RIVET)
                .mapToDouble(s -> s.fasteners() * dev.mcmod.hdfv.manufacture.Machining.roomRivetS(null)).sum() / 3600;
    }

    /** 0.17: the labour with a riveting machine of a head at the room (null: none, laborHours). */
    public double laborHours(dev.mcmod.hdfv.manufacture.Head riveter) {
        double hand = dev.mcmod.hdfv.manufacture.Machining.roomRivetS(null);
        return laborHours - rivetingHours() * (1 - dev.mcmod.hdfv.manufacture.Machining.roomRivetS(riveter) / hand);
    }

    public boolean needsWelding() { return structure.stream().anyMatch(s -> s.seamM() > 0 || s.fasteners() > 0); }

    public static AssemblyPlan of(VehicleBlueprint b) {
        Vehicle v = b.compile();
        Map<String, Need> needs = new LinkedHashMap<>();
        List<Piece> pieces = new ArrayList<>();
        Map<Alloy, Double> stock = new EnumMap<>(Alloy.class);
        List<StructureWork> structure = new ArrayList<>();
        double laborS = 0, cureS = 0, heaviest = 0;
        for (Vehicle.Installed p : v.installed()) {
            PartDesign d = p.part().design();
            PartPlan plan = Plans.of(d);
            if (plan.roomBuilt()) {
                // each copy is built (two identical floats are two bodies to build)
                for (Workpiece w : plan.workpieces()) { pieces.add(new Piece(d, w)); }
                for (StructureWork s : plan.structure()) {
                    structure.add(s);
                    stock.merge(s.alloy(), s.stockKg() + s.fillerKg(), Double::sum);
                    laborS += s.laborS();
                    cureS = Math.max(cureS, s.cureS());
                }
                laborS += plan.assembleS();
            } else {
                String json = Designs.write(d);
                Need n = needs.get(json);
                needs.put(json, new Need(d, n == null ? 1 : n.count() + 1));
                heaviest = Math.max(heaviest, p.part().mass().massKg());
                laborS += INSTALL_S + INSTALL_S_PER_KG * p.part().mass().massKg();
            }
        }
        // the size: contact spheres and the discs of rotors and propellers
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (CollisionSphere c : v.collision()) { grow(lo, hi, c.centerLocal(), new Vec3d(c.radiusM(), c.radiusM(), c.radiusM())); }
        for (Vehicle.Rotor r : v.rotors()) {
            double radius = switch (v.installed().get(r.part()).part().design()) {
                case RotorDesign rd -> rd.blades().radiusM();
                case AirPropellerDesign a -> 0.5 * a.blades().diameterM();
                case PropellerDesign s -> 0.5 * s.diameterM();
                default -> 0;
            };
            if (radius <= 0) { continue; }
            Vec3d a = r.axis();
            grow(lo, hi, r.position(), new Vec3d(radius * Math.sqrt(Math.max(0, 1 - a.x() * a.x())), radius * Math.sqrt(Math.max(0, 1 - a.y() * a.y())),
                    radius * Math.sqrt(Math.max(0, 1 - a.z() * a.z()))));
        }
        if (!Double.isFinite(lo[0])) { lo = new double[3]; hi = new double[3]; }
        // engines with nothing to burn: a vehicle without tanks would run them for nothing (AeroCore supplies a tankless vehicle)
        List<String> tankless = new ArrayList<>();
        if (v.tanks().isEmpty()) {
            for (Vehicle.Installed p : v.installed()) { if (dev.mcmod.hdfv.fluid.FuelGrades.takenBy(p.part().design()) != null) { tankless.add(p.id()); } }
        }
        return new AssemblyPlan(Room.of(b), List.copyOf(needs.values()), pieces, stock, structure, laborS / 3600, cureS / 3600, heaviest,
                new Vec3d(lo[0], lo[1], lo[2]), new Vec3d(hi[0], hi[1], hi[2]), List.copyOf(tankless), Fitting.misfits(b), Fitting.seatClashes(b),
                ChassisRules.overloads(b, v), ChassisRules.bodiesNeedingFrame(b), ChassisRules.missingArches(b, v), ChassisRules.clearance(b, v),
                ChassisRules.truck(b, v));
    }

    private static void grow(double[] lo, double[] hi, Vec3d c, Vec3d half) {
        lo[0] = Math.min(lo[0], c.x() - half.x()); hi[0] = Math.max(hi[0], c.x() + half.x());
        lo[1] = Math.min(lo[1], c.y() - half.y()); hi[1] = Math.max(hi[1], c.y() + half.y());
        lo[2] = Math.min(lo[2], c.z() - half.z()); hi[2] = Math.max(hi[2], c.z() + half.z());
    }
}
