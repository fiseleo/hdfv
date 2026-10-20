package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.GroundContact;
import dev.mcmod.aerocore.api.wheel.Brake;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.aerocore.api.wheel.Tyre;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.BrakeDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import dev.mcmod.hdfv.part.wheel.TyreDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * Trial runs of wheels (0.6): a tyre's force curves, an axle's suspension and brakes, a landing-gear leg's drop test.
 * The numbers are AeroCore's own (Tyre, Suspension, Brake): what the part does in a vehicle.
 */
public final class WheelBench {
    private WheelBench() {}

    public static final double G = 9.80665;
    /** The speed the tyre curves are drawn at, m/s. */
    public static final double CURVE_SPEED = 20;

    /** A tyre's force at one slip (ratio, or angle in degrees) on dry and wet tarmac, N. */
    public record TyrePoint(double slip, double dryN, double wetN) {}

    /**
     * A tyre's curves at a load: braking force against slip ratio 0-1, cornering force against slip angle 0-20 degrees
     * (both at CURVE_SPEED), rolling resistance against speed (rows {m/s, N}) on tarmac; the dry peaks and the
     * hydroplaning speed.
     */
    public record TyreCurve(double loadN, List<TyrePoint> braking, List<TyrePoint> cornering, List<double[]> rolling, double peakSlip, double peakBrakingN,
                            double peakAngleDeg, double peakCorneringN, double hydroplaningMps) {}

    public static TyreCurve tyreCurve(TyreDesign design, double loadN) {
        Tyre t = design.tyre();
        List<TyrePoint> braking = new ArrayList<>(), cornering = new ArrayList<>();
        double peakSlip = 0, peakX = 0, peakAngle = 0, peakY = 0;
        for (int i = 0; i <= 50; i++) {
            double s = i / 50.0;
            double dry = Math.abs(t.forces(GroundContact.Surface.TARMAC, loadN, -s, 0, 0, CURVE_SPEED).longitudinalN());
            double wet = Math.abs(t.forces(GroundContact.Surface.WET_TARMAC, loadN, -s, 0, 0, CURVE_SPEED).longitudinalN());
            braking.add(new TyrePoint(s, dry, wet));
            if (dry > peakX) { peakX = dry; peakSlip = s; }
        }
        for (int i = 0; i <= 40; i++) {
            double a = i * 0.5, sy = Math.tan(Math.toRadians(a));
            double dry = Math.abs(t.forces(GroundContact.Surface.TARMAC, loadN, 0, sy, 0, CURVE_SPEED).lateralN());
            double wet = Math.abs(t.forces(GroundContact.Surface.WET_TARMAC, loadN, 0, sy, 0, CURVE_SPEED).lateralN());
            cornering.add(new TyrePoint(a, dry, wet));
            if (dry > peakY) { peakY = dry; peakAngle = a; }
        }
        List<double[]> rolling = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            double v = 2.5 * i;
            rolling.add(new double[]{v, t.rollingResistanceN(GroundContact.Surface.TARMAC, loadN, v)});
        }
        return new TyreCurve(loadN, braking, cornering, rolling, peakSlip, peakX, peakAngle, peakY, t.hydroplaningSpeedMps());
    }

    /** One point of the travel: compression from droop (m), camber against the body (deg), wheel rate (N/m), spring force (N). */
    public record TravelPoint(double compressionM, double camberDeg, double rateNPerM, double forceN) {}

    /** One stop of the fade test: its number, the rotor's hottest temperature (C), lining friction there and the brake's torque at full demand over cold. */
    public record FadeStop(int stop, double peakC, double mu, double torqueShare) {}

    /**
     * An axle's bench: its tyre's curves at the corner's design load, the travel (camber and spring over it), and a fade
     * test of its brakes (FADE_STOPS hard stops from 100 km/h at 0.8 g back to back, the corner's design load on each
     * brake, 20 s at 100 km/h between them as the car speeds up again; after the magazines' ten-stop tests), empty
     * without brakes.
     */
    public record AxleBench(TyreCurve tyre, List<TravelPoint> travel, double designCompressionM, double rideFrequencyHz, double dampingRatio,
                            double rollStiffnessNmPerDeg, double rollCentreM, double lockRatio, List<FadeStop> fade, List<KinematicPoint> kinematics,
                            dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable.SteeringAxis axis) {}

    /**
     * 0.15: one point of a hardpoint axle's kinematics: compression from droop (m), camber and toe against the body
     * (deg; toe in positive), the spring's motion ratio and the side-view angle of the contact patch's path (deg, its
     * top rearward positive: what anti-dive and anti-squat come from).
     */
    public record KinematicPoint(double compressionM, double camberDeg, double toeDeg, double motionRatio, double sideAngleDeg) {}

    public static final int FADE_STOPS = 10;
    public static final double FADE_FROM = 100 / 3.6, FADE_DECELERATION = 0.8 * G, FADE_COOL_S = 20;

    public static AxleBench axle(AxleDesign d) {
        Tyre t = d.tyre().tyre();
        Suspension s = d.builtSuspension(t);
        List<TravelPoint> travel = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            double c = s.travelM() * i / 20;
            travel.add(new TravelPoint(c, Math.toDegrees(s.camberToBody(c)), s.rate(c), s.force(c)));
        }
        List<FadeStop> fade = d.brake().kind() == BrakeDesign.Kind.NONE ? List.of() : fade(d.brake().brake(), d.suspension().designLoadKg());
        // 0.15: the hardpoints' table over the travel (none in the simple mode: no toe change, no anti)
        List<KinematicPoint> kinematics = new ArrayList<>();
        var table = d.kinematics();
        double rollCentre = s.geometry().rollCentreHeightM();
        if (table != null) {
            for (int i = 0; i <= 40; i++) {
                double c = s.travelM() * i / 40;
                var k = table.at(c);
                kinematics.add(new KinematicPoint(c, Math.toDegrees(k.camberRad()), Math.toDegrees(k.toeRad()), k.motionRatio(), Math.toDegrees(Math.atan(k.sideTanContact()))));
            }
            rollCentre = table.rollCentreHeightM();
        }
        return new AxleBench(tyreCurve(d.tyre(), d.cornerLoadN()), travel, s.designCompressionM(), d.rideFrequencyHz(t), d.dampingRatio(),
                d.rollStiffnessNmPerDeg(t), rollCentre, d.lockRatio(t), fade, kinematics, table == null ? null : table.steeringAxis());
    }

    /** The fade test of one brake stopping a mass (kg). */
    public static List<FadeStop> fade(Brake brake, double massKg) {
        List<FadeStop> out = new ArrayList<>();
        double temperature = WheelCorner.AIR_K, dt = 0.05, air = WheelCorner.AIR_K;
        for (int n = 1; n <= FADE_STOPS; n++) {
            double peak = temperature;
            for (double v = FADE_FROM; v > 0; v -= FADE_DECELERATION * dt) {
                temperature = brake.temperatureK(temperature, massKg * FADE_DECELERATION * v, v, air, dt);
                peak = Math.max(peak, temperature);
            }
            double mu = Brake.liningFriction(peak);
            out.add(new FadeStop(n, peak - 273.15, mu, brake.torqueNm(1, peak) / brake.maxTorqueNm()));
            for (double time = 0; time < FADE_COOL_S; time += dt) { temperature = brake.temperatureK(temperature, 0, FADE_FROM, air, dt); }
        }
        return out;
    }

    /** One instant of a drop: time (s), strut compression (m), tyre deflection (m), force on the aircraft (N). */
    public record DropPoint(double timeS, double strokeM, double tyreM, double forceN) {}

    /**
     * A drop test of a leg: its design load falling onto level ground at a sink rate, wing lift carrying LIFT of its
     * weight through the stroke (FAR 23.473(e)/23.725: lift not above two thirds of the weight). FAR 23.725's free drop
     * h = 3.6 sqrt(W/S) in (W/S in lb/ft^2, 9.2-18.7 in) gives the limit sink: 2.6 m/s for a light aircraft's 14 lb/ft^2
     * (FAR23_SINK), 3.05 m/s at the top. The largest force over the weight is the gear's load factor; bottomed when the
     * strut runs out of stroke.
     */
    public record DropTest(double sinkMps, List<DropPoint> points, double peakForceN, double loadFactor, double maxStrokeM, double maxTyreM, boolean bottomed) {}

    public static final double LIFT = 2.0 / 3, FAR23_SINK = Math.sqrt(2 * G * 3.6 * Math.sqrt(14) * 0.0254);

    public static DropTest drop(LandingGearDesign d, double sinkMps) {
        Tyre t = d.tyre().tyre();
        Suspension s = d.strut().suspension();
        double m = d.strut().designLoadKg(), weight = m * G, g = G * (1 - LIFT), dt = 2e-4;
        // the hub at full droop starts with the tyre touching: penetration 0
        double y = t.radiusM(), v = -Math.abs(sinkMps), c = 0, peak = 0, maxC = 0, maxTyre = 0;
        List<DropPoint> points = new ArrayList<>();
        boolean touched = false;
        for (int i = 0; i < 1.5 / dt; i++) {
            double p = t.radiusM() - y;
            Suspension.Step st = s.step(t, p, -v, c, 0, dt);
            // the body's velocity at the step's end, the force's slope taken implicitly
            double v1 = (m * v / dt + st.forceN() - m * g) / (m / dt + st.slopeNsPerM());
            double force = st.forceN() + st.slopeNsPerM() * (-v1);
            c = st.compression(-v1, dt);
            y += v1 * dt;
            v = v1;
            peak = Math.max(peak, force);
            maxC = Math.max(maxC, c);
            maxTyre = Math.max(maxTyre, st.tyreDeflection());
            if (i % 25 == 0) { points.add(new DropPoint(i * dt, c, Math.max(st.tyreDeflection(), 0), Math.max(force, 0))); }
            if (force > 0) { touched = true; }
            if (touched && v > 0 && force <= 0) { break; }
        }
        return new DropTest(sinkMps, points, peak, peak / weight, maxC, maxTyre, maxC > s.travelM());
    }
}
