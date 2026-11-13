package dev.mcmod.hdfv.repair;

import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.refit.RefitRules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 0.25: what a repair shop needs (the user's 「修理廠」), scanned as the refit shop is (its own controller, any closed room:
 * the car factory's, the hangar's or the shipyard's sizes are not asked, the vehicle came in through the door): closed,
 * a door, RefitRules.LIGHTS work lights, power at its controller, exactly one vehicle, assembled (not still being
 * built), standing (not moving faster than MOVING_MPS) with nobody aboard, a welding station when a mend or a rebuild
 * welds, something to do; and the wreck rule (VehicleDamage: a body or chassis whose structure is used up takes every
 * engine's ancillaries with it, once, until the structure is mended): an engine is repaired only together with, or
 * after, the wrecked body or chassis, or the wreck would fail it again. Missing materials are the stores' (Staging),
 * said when the job is started. Problems are keys under hdfv.repair_shop.problem with their arguments.
 */
public final class RepairRules {
    private RepairRules() {}

    /** Faster than this (m/s) the vehicle is moving: it is put on stands first. */
    public static final double MOVING_MPS = 0.3;

    public record Problem(String key, List<Object> args) {
        public Problem(String key, Object... args) { this(key, List.of(args)); }
    }

    /**
     * A shop as found: its scan, work lights, vehicles in it, whether the one there is still being built, whether its
     * controller has power, how fast the vehicle moves (m/s) and how many ride it; the job; the wrecks the job leaves
     * standing while it repairs an engine (wrecks).
     */
    public record Shop(RoomScan.Scan scan, int lights, int vehicles, boolean building, boolean powered, double speedMps, int riders,
                       RepairJob job, List<String> wrecks) {}

    public static List<Problem> check(Shop b) {
        List<Problem> out = new ArrayList<>();
        RoomScan.Scan s = b.scan();
        if (!s.closed()) { out.add(new Problem("open", s.leakX(), s.leakY(), s.leakZ())); return out; }
        if (s.doors().isEmpty()) { out.add(new Problem("no_door")); }
        if (b.lights() < RefitRules.LIGHTS) { out.add(new Problem("few_lights", b.lights(), RefitRules.LIGHTS)); }
        if (b.vehicles() == 0) { out.add(new Problem("no_vehicle")); }
        else if (b.vehicles() > 1) { out.add(new Problem("vehicles", b.vehicles())); }
        if (b.building()) { out.add(new Problem("building")); }
        if (!b.powered()) { out.add(new Problem("no_power")); }
        if (b.vehicles() == 1 && b.speedMps() > MOVING_MPS) { out.add(new Problem("moving", String.format(java.util.Locale.ROOT, "%.1f", b.speedMps()))); }
        if (b.riders() > 0) { out.add(new Problem("riders", b.riders())); }
        if (b.job() != null && b.job().welding() && s.weldingStations() == 0) { out.add(new Problem("welding_station")); }
        if (b.vehicles() == 1 && (b.job() == null || b.job().empty())) { out.add(new Problem("nothing")); }
        for (String w : b.wrecks()) { out.add(new Problem("wreck_first", w)); }
        return out;
    }

    /**
     * The condition whose failure stops a part that drives the vehicle, the one the wreck rule fails (null for parts that
     * are not engines): its ancillaries; an electric motor's motor; a duct engine's or a booster's structure (a part
     * that burns fuel and has no ancillaries).
     */
    public static String engineCondition(Part part) {
        List<String> c = part.conditions();
        if (c.contains(Conditions.ANCILLARIES)) { return Conditions.ANCILLARIES; }
        if (part.design() instanceof MotorDesign && c.contains(Conditions.MOTOR)) { return Conditions.MOTOR; }
        if (c.contains(Conditions.STRUCTURE) && dev.mcmod.hdfv.fluid.FuelGrades.takenBy(part.design()) != null) { return Conditions.STRUCTURE; }
        return null;
    }

    /** Whether a part is what the wreck rule looks at: a body or a chassis. */
    public static boolean carrier(PartDesign d) { return d instanceof BodyDesign || d instanceof ChassisDesign; }

    /**
     * The wrecked bodies and chassis (structure used up) a job leaves as they are while it repairs an engine (its
     * condition the wreck fails, or the engine replaced whole): empty when the job may go ahead.
     */
    public static List<String> wrecks(Vehicle vehicle, RepairJob.Used used, RepairJob job) {
        Set<String> wrecked = new LinkedHashSet<>();
        for (Vehicle.Installed p : vehicle.installed()) {
            if (carrier(p.part().design()) && p.part().conditions().contains(Conditions.STRUCTURE) && used.of(p.id(), Conditions.STRUCTURE) >= 1) {
                wrecked.add(p.id());
            }
        }
        if (wrecked.isEmpty()) { return List.of(); }
        boolean engine = false;
        for (RepairJob.Task t : job.tasks()) {
            Vehicle.Installed p = vehicle.installed(t.part());
            if (p == null) { continue; }
            if (carrier(p.part().design()) && Conditions.STRUCTURE.equals(t.condition())) { wrecked.remove(p.id()); continue; }
            String kill = engineCondition(p.part());
            if (kill != null && (kill.equals(t.condition()) || t.wholePart())) { engine = true; }
        }
        return engine ? List.copyOf(wrecked) : List.of();
    }
}
