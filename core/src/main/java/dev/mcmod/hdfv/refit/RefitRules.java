package dev.mcmod.hdfv.refit;

import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.RoomRules;
import dev.mcmod.hdfv.assembly.RoomScan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 0.16: what a refit shop needs (the user's 「改裝廠」), scanned as an assembly room is: closed, with a door the vehicle
 * leaves by as it came and as it will be, room for it as it will be, at least LIGHTS work lights, power at its
 * controller, exactly one vehicle that is not still being built, a welding station when the refit welds; and the
 * refitted vehicle's own problems, the assembly room's (RoomRules.vehicle: tanks, engine bays, seats, frame, arches,
 * the underside over the ground) and a scale outside what Scaling is meant for. Problems are keys under
 * hdfv.refit_shop.problem (the vehicle's under hdfv.room.problem) with their arguments.
 */
public final class RefitRules {
    private RefitRules() {}

    public static final int LIGHTS = 2;

    public record Problem(String key, List<Object> args, boolean vehicle) {
        public Problem(String key, Object... args) { this(key, List.of(args), false); }
    }

    /**
     * A shop as found: its scan, work lights, vehicles in it, whether one is still being built, whether its controller has
     * power; the vehicle's plan before and after (null when there is no vehicle or it does not compile), the job and the
     * scale asked for.
     */
    public record Shop(RoomScan.Scan scan, int lights, int vehicles, boolean building, boolean powered,
                       AssemblyPlan before, AssemblyPlan after, RefitJob job, double scale) {}

    public static List<Problem> check(Shop b) {
        List<Problem> out = new ArrayList<>();
        RoomScan.Scan s = b.scan();
        if (!s.closed()) { out.add(new Problem("open", s.leakX(), s.leakY(), s.leakZ())); return out; }
        if (s.doors().isEmpty()) { out.add(new Problem("no_door")); }
        else if (b.after() != null) {
            for (AssemblyPlan p : new AssemblyPlan[]{b.before(), b.after()}) {
                if (p == null) { continue; }
                RoomScan.Door d = RoomRules.door(s, p);
                if (d.widthM() < p.widthM() - 1e-9 || d.heightM() < p.heightM() - 1e-9) {
                    out.add(new Problem("door_small", fmt(p.widthM()), fmt(p.heightM())));
                    break;
                }
            }
            AssemblyPlan p = b.after();
            int big = Math.max(s.lengthX(), s.widthZ()), small = Math.min(s.lengthX(), s.widthZ());
            if (big < p.lengthM() || small < p.widthM() || s.heightY() < p.heightM()) {
                out.add(new Problem("room_small", fmt(p.lengthM()), fmt(p.widthM()), fmt(p.heightM())));
            }
        }
        if (b.lights() < LIGHTS) { out.add(new Problem("few_lights", b.lights(), LIGHTS)); }
        if (b.vehicles() == 0) { out.add(new Problem("no_vehicle")); }
        else if (b.vehicles() > 1) { out.add(new Problem("vehicles", b.vehicles())); }
        if (b.building()) { out.add(new Problem("building")); }
        if (!b.powered()) { out.add(new Problem("no_power")); }
        if (b.job() != null && b.job().welding() && s.weldingStations() == 0) { out.add(new Problem("welding_station")); }
        if (!Scaling.validated(b.scale())) { out.add(new Problem("scale", fmt(b.scale()), fmt(Scaling.MIN), fmt(Scaling.MAX))); }
        if (b.vehicles() == 1 && b.after() == null) { out.add(new Problem("does_not_compile")); }
        if (b.after() != null) {
            for (RoomRules.Problem p : RoomRules.vehicle(b.after())) { out.add(new Problem(p.key(), p.args(), true)); }
        }
        return out;
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.1f", v); }
}
