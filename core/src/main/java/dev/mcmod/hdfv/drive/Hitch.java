package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Coupling;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.truck.FifthWheelDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;

import java.util.List;

/**
 * A semi-trailer coupled to a tractor (0.18), the rules the game's hitching follows: where a tractor's fifth wheel
 * jaw and a trailer's kingpin are (each in its vehicle's frame), whether the two may couple where they stand, and the
 * AeroCore Coupling to make.
 * <ul>
 *   <li>The kingpin must be in the jaw: within SIDE_M of its middle across, at most the fifth wheel's captureM behind
 *   it and AHEAD_M past it; the trailer's plate 0 to BELOW_M under the fifth wheel's (its ramps lift the trailer onto
 *   it; a plate above it would ride over the jaw); the trailer turned at most MAX_ANGLE_RAD from the tractor; the
 *   kingpin moving at most MAX_SPEED_MPS against the jaw (backing under slowly); the same kingpin size; the trailer's
 *   load on its kingpin standing alone no more than the fifth wheel's rating.</li>
 *   <li>The jackknife: the trailer may swing until its front corners reach the tractor's cab (the cab's back wall,
 *   over the kingpin's swing), at most a right angle. The pitch: the plate's own, less where the trailer's underside
 *   behind the kingpin would come down on the tractor's rear tyres.</li>
 *   <li>The trailer follows the tractor's brake, handbrake, lights and selector (reversing lamps) (FORWARDED).</li>
 *   <li>A trailer's kingpin is 2" unless its frame is made for 45 t or more (3.5").</li>
 * </ul>
 */
public final class Hitch {
    private Hitch() {}

    public static final double SIDE_M = 0.1, AHEAD_M = 0.05, BELOW_M = 0.10, ABOVE_M = 0.02, MAX_ANGLE_RAD = Math.toRadians(30), MAX_SPEED_MPS = 0.5;
    /** The AeroCore coupling's own capture (the jaw closes over the gaps the rules above allow), m. */
    public static final double CAPTURE_M = 0.3;
    /** The trailers that take a 3.5" kingpin, kg of design gross mass. */
    public static final double HEAVY_KINGPIN_KG = 45000;
    /** The least pitch the plate keeps, rad. */
    public static final double MIN_PITCH_RAD = Math.toRadians(3);
    /** The channels a trailer takes from its tractor. */
    public static final List<String> FORWARDED = List.of("brake", "handbrake", "lights", "selector");

    /** What check found. */
    public enum Result { OK, NO_FIFTH_WHEEL, NO_KINGPIN, SIDEWAYS, NOT_UNDER, TOO_HIGH, TOO_LOW, ANGLE, TOO_FAST, KINGPIN_SIZE, OVERLOAD }

    /** A tractor's fifth wheel: its part, design and jaw (the vehicle's frame). */
    public record Jaw(String part, FifthWheelDesign design, Vec3d point) {}

    /** A trailer's kingpin: its chassis, the point (the vehicle's frame), its size and the load on it standing alone, kg. */
    public record Kingpin(String chassis, Vec3d point, FifthWheelDesign.Kingpin size, double staticLoadKg) {}

    /** The vehicle's fifth wheel (the first), or null. */
    public static Jaw jaw(VehicleBlueprint b, Vehicle v) {
        for (Vehicle.Installed i : v.installed()) {
            if (i.part().design() instanceof FifthWheelDesign f) { return new Jaw(i.id(), f, i.mount().apply(f.jaw())); }
        }
        return null;
    }

    /** The vehicle's kingpin (on the first chassis that has one), or null. */
    public static Kingpin kingpin(VehicleBlueprint b, Vehicle v) {
        for (Vehicle.Installed i : v.installed()) {
            if (!(i.part().design() instanceof ChassisDesign c)) { continue; }
            Mount pin = c.mountPoints().get("kingpin");
            if (pin == null) { continue; }
            Vec3d at = i.mount().apply(pin.position());
            // standing on its kingpin and its axles: the kingpin's share of the weight by the lever about the axles' middle
            double axles = 0;
            int n = 0;
            for (Vehicle.Installed a : v.installed()) { if (a.part().design() instanceof AxleDesign) { axles += a.mount().position().x(); n++; } }
            double com = v.properties().centerOfMassLocal().x(), mass = v.properties().massKg();
            for (Vehicle.Tank t : v.tanks()) { mass += t.tank().initialKg(); }
            double load = n == 0 ? mass : mass * Math.clamp((com - axles / n) / (at.x() - axles / n), 0, 1);
            FifthWheelDesign.Kingpin size = c.frame().designGrossKg() >= HEAVY_KINGPIN_KG ? FifthWheelDesign.Kingpin.THREE_AND_HALF_INCH : FifthWheelDesign.Kingpin.TWO_INCH;
            return new Kingpin(i.id(), at, size, load);
        }
        return null;
    }

    /** Where a trailer stands against a tractor's jaw: the kingpin's offset in the tractor's frame (m), the angle between them (rad), the closing speed (m/s). */
    public record Stance(Vec3d offset, double angleRad, double speedMps) {}

    public static Stance stance(Vehicle tractor, RigidBodyState t, Jaw jaw, Vehicle trailer, RigidBodyState r, Kingpin pin) {
        Vec3d j = t.worldPoint(tractor.properties(), jaw.point()), k = r.worldPoint(trailer.properties(), pin.point());
        Vec3d offset = t.orientation().inverse().rotate(k.subtract(j));
        Vec3d ft = t.orientation().rotate(Vec3d.X), fr = r.orientation().rotate(Vec3d.X);
        double angle = Math.atan2(ft.cross(fr).y(), ft.x() * fr.x() + ft.z() * fr.z());
        Vec3d vj = t.velocity().add(t.angularVelocity(tractor.properties()).cross(j.subtract(t.position())));
        Vec3d vk = r.velocity().add(r.angularVelocity(trailer.properties()).cross(k.subtract(r.position())));
        return new Stance(offset, angle, vk.subtract(vj).length());
    }

    /** Whether a trailer may couple to a tractor where they stand (see the class). */
    public static Result check(Jaw jaw, Kingpin pin, Stance s) {
        if (jaw == null) { return Result.NO_FIFTH_WHEEL; }
        if (pin == null) { return Result.NO_KINGPIN; }
        Vec3d o = s.offset();
        if (Math.abs(o.z()) > SIDE_M) { return Result.SIDEWAYS; }
        if (o.x() < -jaw.design().captureM() || o.x() > AHEAD_M) { return Result.NOT_UNDER; }
        if (o.y() > ABOVE_M) { return Result.TOO_HIGH; }
        if (o.y() < -BELOW_M) { return Result.TOO_LOW; }
        if (Math.abs(s.angleRad()) > MAX_ANGLE_RAD) { return Result.ANGLE; }
        if (s.speedMps() > MAX_SPEED_MPS) { return Result.TOO_FAST; }
        if (pin.size() != jaw.design().kingpin()) { return Result.KINGPIN_SIZE; }
        if (pin.staticLoadKg() > jaw.design().ratedLoadKg()) { return Result.OVERLOAD; }
        return Result.OK;
    }

    /** The coupling for a tractor and trailer: the fifth wheel's, with the jackknife and pitch their shapes allow and the forwarded channels. */
    public static Coupling coupling(VehicleBlueprint tractorBlueprint, Vehicle tractor, Jaw jaw, VehicleBlueprint trailerBlueprint, Vehicle trailer, Kingpin pin) {
        double jackknife = jackknifeRad(tractor, jaw, trailer, pin);
        Coupling c = jaw.design().coupling(jaw.point(), pin.point(), jackknife).withCapture(CAPTURE_M).withForwarded(FORWARDED);
        return c.withLimits(pitchRad(tractor, jaw, trailer, pin), jackknife);
    }

    /**
     * How far the trailer may swing: until a front corner (its bodies' and frames' front ends, at their half width)
     * reaches the tractor's cab's back wall ahead of the jaw; a right angle when it never does.
     */
    public static double jackknifeRad(Vehicle tractor, Jaw jaw, Vehicle trailer, Kingpin pin) {
        double wall = Double.POSITIVE_INFINITY;
        for (Vehicle.Installed i : tractor.installed()) {
            if (i.part().design() instanceof BodyDesign b && dev.mcmod.hdfv.assembly.Placing.cab(b)) { wall = Math.min(wall, i.mount().position().x() - jaw.point().x()); }
        }
        double front = 0, half = 0;
        for (Vehicle.Installed i : trailer.installed()) {
            if (i.part().design() instanceof BodyDesign b) {
                var shape = b.shape();
                front = Math.max(front, i.mount().apply(new Vec3d(shape.lengthM(), 0, 0)).x() - pin.point().x());
                half = Math.max(half, 0.5 * shape.widthAt(shape.lengthM() - 0.05));
            } else if (i.part().design() instanceof ChassisDesign c) {
                front = Math.max(front, i.mount().apply(new Vec3d(c.frame().lengthM(), 0, 0)).x() - pin.point().x());
                half = Math.max(half, 0.5 * c.frame().spacingAt(c.frame().lengthM()) + 0.1);
            }
        }
        double reach = Math.hypot(front, half);
        if (!(wall < reach)) { return Math.PI / 2; }
        if (front >= wall) { return Math.toRadians(5); }
        // the outer corner at (front, half) turned by a reaches front cos a + half sin a = reach cos(a - phi)
        double phi = Math.atan2(half, front);
        return Math.clamp(phi - Math.acos(wall / reach), Math.toRadians(5), Math.PI / 2);
    }

    /** The pitch the trailer may take: the plate's, less where its underside behind the kingpin meets the tractor's rear tyres. */
    public static double pitchRad(Vehicle tractor, Jaw jaw, Vehicle trailer, Kingpin pin) {
        double plate = Math.toRadians(jaw.design().pitchLimitDeg()), best = plate;
        for (Vehicle.Installed i : tractor.installed()) {
            if (!(i.part().design() instanceof AxleDesign a)) { continue; }
            Vec3d hub = i.mount().apply(new Vec3d(0, -a.layout().hubDropM(), 0));
            double r = a.tyre().tyre().radiusM(), behind = jaw.point().x() - (hub.x() - r);
            if (behind <= 0.05) { continue; }
            double clearance = jaw.point().y() - (hub.y() + r);
            best = Math.min(best, Math.atan(Math.max(clearance, 0) / behind));
        }
        return Math.max(best, MIN_PITCH_RAD);
    }
}
