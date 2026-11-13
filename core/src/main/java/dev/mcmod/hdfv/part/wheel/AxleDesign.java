package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.GroundContact;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.aerocore.api.wheel.Tyre;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An axle (hdfv:axle, 車軸): two wheels, left at -z and right at +z of the part frame (x forward, y up), on the
 * suspension, steered or not, driven through the differential or not. In the world it is AeroCore's AxlePart.
 *
 * <ul>
 * <li>Frame: the origin is the mount point (a body's "mN_axle", "front_axle", "rear_axle"); at the design compression
 *     the hubs are the layout's hubDropM below it, half the track either side.</li>
 * <li>Inputs: "brake" (0..1), "steer" (-1..1, left positive) when steered, "handbrake" (0..1) when the brakes have a
 *     parking share, "lock" when the differential locks. Rotors: "input" (driven; a shaft from a gearbox joins it),
 *     "left", "right".</li>
 * <li>Each wheel's rotor inertia: the tyre's (AeroCore), the wheel's and the brake rotor's.</li>
 * <li>Checks: the components', the tyre on the rim (rim width 0.6-1.0 of the section width, ETRTO's approved range;
 *     outside 0.45-1.2 it cannot seat), the brake inside the wheel (a disc and caliper need 60 mm less than the rim's
 *     diameter, a drum 20 mm), the tyre's load (the corner's design load against its rated load), the ride frequency
 *     and damping ratio against what cars use.</li>
 * </ul>
 */
public record AxleDesign(String name, AxleLayoutDesign layout, TyreDesign tyre, RimDesign wheel, BrakeDesign brake, SuspensionDesign suspension,
                         DifferentialDesign differential, HardpointsDesign hardpoints) implements PartDesign {
    public static final String TYPE = "hdfv:axle";
    public static final double G = 9.80665;

    public AxleDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{layout, tyre, wheel, brake, suspension, differential}) { Objects.requireNonNull(o, "component"); }
        // 0.15: the hardpoints follow the suspension's layout (their page shows that layout's points)
        hardpoints = hardpoints == null ? HardpointsDesign.simple(suspension.layout()) : hardpoints.withLayout(suspension.layout());
    }

    /** 0.14's axle: the simple geometry. */
    public AxleDesign(String name, AxleLayoutDesign layout, TyreDesign tyre, RimDesign wheel, BrakeDesign brake, SuspensionDesign suspension, DifferentialDesign differential) {
        this(name, layout, tyre, wheel, brake, suspension, differential, null);
    }

    /** 0.15: the right wheel's hub at the design compression in the axle's frame. */
    public Vec3d designHub() { return new Vec3d(0, -layout.hubDropM(), layout.trackM() / 2); }

    private static final Map<AxleDesign, dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable> TABLES = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<AxleDesign, dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable> e) { return size() > 64; }
            });

    /**
     * 0.15: the kinematic table its hardpoints give (null in the simple mode); kept per design. Throws
     * LinkageLockException when the links bind within the travel.
     */
    public dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable kinematics() {
        if (hardpoints.mode() != HardpointsDesign.Mode.HARDPOINTS) { return null; }
        return TABLES.computeIfAbsent(this, a -> {
            double r = a.tyre.tyre().radiusM();
            return dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable.of(a.hardpoints.hardpoints(a.designHub(), a.layout.trackM(), r, a.suspension), r,
                    a.suspension.designCompressionM(), a.suspension.travelM());
        });
    }

    /**
     * 0.15: where a hardpoint field's point is now, from the hub (m, "x y z"): the design screen's hint for a blank one
     * (the simple geometry's); null for other fields or when the points cannot be worked out.
     */
    public String hardpointHint(String name) {
        try {
            var h = hardpoints.hardpoints(designHub(), layout.trackM(), tyre.tyre().radiusM(), suspension);
            var p = HardpointsDesign.resolved(name, h);
            if (p == null) { return null; }
            var r = p.subtract(designHub());
            return String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f", r.x(), r.y(), r.z());
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("layout", layout); m.put("tyre", tyre); m.put("wheel", wheel); m.put("brake", brake); m.put("suspension", suspension); m.put("differential", differential);
        m.put("hardpoints", hardpoints);
        return m;
    }

    public boolean solid() { return suspension.layout().solid(); }

    /** The suspension as built for this axle's track and tyre (0.15: on its hardpoints' kinematics in that mode). */
    public Suspension builtSuspension(Tyre t) { return suspension.suspension(layout.trackM(), t.radiusM(), kinematics()); }

    /** One wheel's rotor inertia without the tyre: wheel and brake rotor, kg m^2. */
    public double rimInertiaKgM2() { return wheel.inertiaKgM2(tyre.rimDiameterM()) + brake.rotorInertiaKgM2(); }

    /** One corner's mass: tyre, wheel, brake and suspension, kg. */
    public double cornerKg() { return tyre.massKg() + wheel.massKg(tyre.rimDiameterM()) + brake.massKg() + suspension.massKg(); }

    /** The axle's mass, kg. */
    public double totalMassKg() { return 2 * cornerKg() + layout.massKg() + differential.massKg(layout.trackM(), solid()); }

    /** The tyre's load at the design compression, N. */
    public double cornerLoadN() { return suspension.designLoadKg() * G; }

    /** The spring and tyre in series about the design load, N/m (the ride rate). */
    public double rideRateNPerM(Tyre t) {
        double kt = t.verticalStiffness(t.deflectionM(cornerLoadN())), ks = suspension.springRateAtDesign();
        return ks * kt / (ks + kt);
    }

    /** The corner's natural frequency on its ride rate, Hz. */
    public double rideFrequencyHz(Tyre t) { return Math.sqrt(rideRateNPerM(t) / suspension.designLoadKg()) / (2 * Math.PI); }

    /** The dampers' mean coefficient over critical for the corner, on the spring alone (the tyre does not damp). */
    public double dampingRatio() {
        double c = (suspension.bumpNsPerM() + suspension.reboundNsPerM()) / 2;
        return c / (2 * Math.sqrt(suspension.springRateAtDesign() * suspension.designLoadKg()));
    }

    /** Roll stiffness of the axle (springs with the tyres and the anti-roll bar), N m per degree. */
    public double rollStiffnessNmPerDeg(Tyre t) {
        double track = layout.trackM();
        return (rideRateNPerM(t) / 2 + layout.antiRollNPerM()) * track * track * Math.PI / 180;
    }

    /**
     * How far the brake can lock the wheel: the brake's cold torque over the torque the tyre's peak grip on dry tarmac
     * holds at the design load (above 1 the brake can lock it; ABS or not).
     */
    public double lockRatio(Tyre t) {
        if (brake.kind() == BrakeDesign.Kind.NONE) { return 0; }
        double load = cornerLoadN();
        return brake.maxTorqueNm() / (t.peakFriction(GroundContact.Surface.TARMAC, load, 20) * load * t.effectiveRadiusM(load));
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = Components.issues(components());
        if (Components.invalid(issues)) { return issues; }
        Tyre t = tyre.tyre();
        if (layout.trackM() < tyre.sectionWidthM() + 0.05) { issues.add(DesignIssue.invalid("layout.trackM", "The wheels would overlap: the track is narrower than a tyre")); }
        double seat = wheel.widthM() / tyre.sectionWidthM();
        if (seat < 0.45 || seat > 1.2) { issues.add(DesignIssue.invalid("wheel.rimWidthIn", "The tyre cannot seat on a rim this wide")); }
        else { DesignIssue.range(issues, "rimOverSection", seat, 0.6, 1.0, "ETRTO approved rim widths, 0.6-1.0 of the section width"); }
        if (brake.kind() != BrakeDesign.Kind.NONE) {
            double room = tyre.rimDiameterM() - (brake.kind().drum() ? 0.02 : 0.06);
            if (brake.rotorDiameterM() > room) { issues.add(DesignIssue.invalid("brake.rotorDiameterM", "The brake does not fit inside the wheel")); }
        }
        double load = cornerLoadN();
        if (load > t.maxLoadN()) { issues.add(DesignIssue.invalid("suspension.designLoadKg", "The tyre's rated load is " + Math.round(t.maxLoadN()) + " N; this corner carries " + Math.round(load) + " N")); }
        else { DesignIssue.range(issues, "tyreLoad", load / t.nominalLoadN(), 0.3, 1.2, "tyres run at 30-120 % of the load their curves are measured at"); }
        DesignIssue.range(issues, "rideFrequency", rideFrequencyHz(t), 0.8, 3.5, "ride frequencies (Milliken: cars 1.0-1.5 Hz, sporty 1.5-2.0, racing 3-5)");
        DesignIssue.range(issues, "dampingRatio", dampingRatio(), 0.15, 0.8, "damping ratios (Milliken: 0.2-0.4 for comfort, 0.5-0.7 for control)");
        // 0.15: links that bind within the travel are no suspension
        try { kinematics(); } catch (dev.mcmod.aerocore.api.wheel.kinematics.LinkageLockException | IllegalArgumentException e) {
            issues.add(DesignIssue.invalid("hardpoints.mode", "The links cannot move the wheel through its travel: " + e.getMessage()));
        }
        return issues;
    }

    /** Mass at the hubs and the axle's middle, at the design compression. */
    public MassProperties mass() {
        double half = layout.trackM() / 2, y = -layout.hubDropM(), corner = cornerKg();
        List<MassProperties> parts = new ArrayList<>();
        parts.add(Components.box(corner, new Vec3d(0, y, -half), new Vec3d(2 * tyre.tyre().radiusM(), 2 * tyre.tyre().radiusM(), tyre.sectionWidthM())));
        parts.add(Components.box(corner, new Vec3d(0, y, half), new Vec3d(2 * tyre.tyre().radiusM(), 2 * tyre.tyre().radiusM(), tyre.sectionWidthM())));
        double middle = layout.massKg() + differential.massKg(layout.trackM(), solid());
        if (middle > 0) { parts.add(Components.box(middle, new Vec3d(0, y, 0), new Vec3d(0.3, 0.25, layout.trackM() * 0.6))); }
        return MassProperties.combine(parts);
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        Tyre t = tyre.tyre();
        Suspension s = builtSuspension(t);
        double half = layout.trackM() / 2, droop = -layout.hubDropM() - suspension.designCompressionM(), toe = Math.toRadians(suspension.toeDeg());
        WheelCorner left = new WheelCorner(new WheelCorner.Spec(new Vec3d(0, droop, -half), -1, layout.trackM(), t, rimInertiaKgM2(), s, brake.brake(), toe));
        WheelCorner right = new WheelCorner(new WheelCorner.Spec(new Vec3d(0, droop, half), 1, layout.trackM(), t, rimInertiaKgM2(), s, brake.brake(), toe));
        AxlePart.Steering steering = layout.steered() ? new AxlePart.Steering(Math.toRadians(layout.maxSteerDeg()), layout.ackermann(), layout.wheelbaseM()) : null;
        // 0.25: the wheels wear (tyres, brakes, suspension), by numbers worked out from the tyre, the brake and the design load
        AxlePart axle = new AxlePart(this, left, right, differential.drive(), steering, layout.antiRollNPerM(), brake.handbrakeShare() * brake.maxTorqueNm(), brake.control(),
                mass(), Components.extrapolations(issues)).wearing(dev.mcmod.hdfv.damage.Wearings.wheels(tyre, brake, 2, suspension.designLoadKg()));
        // 0.15: a steering share that is not the whole, or that changes with speed (a rear axle, a second front axle)
        return layout.scheduled() ? new SteerSchedule(this, axle, layout.steerRatio(), layout.steerRatioHigh(), layout.steerCrossoverMps()) : axle;
    }

    /** The pages' JSON (0.15: the hardpoints' only in that mode, so a simple axle's JSON and its workpieces' hashes are 0.14's). */
    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Components.parameters(components());
        if (hardpoints.mode() == HardpointsDesign.Mode.SIMPLE) { m.remove("hardpoints"); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new AxleDesign(f.string("name", "axle"), Components.read(f, "layout", AxleLayoutDesign::read), Components.read(f, "tyre", TyreDesign::read),
                Components.read(f, "wheel", RimDesign::read), Components.read(f, "brake", BrakeDesign::read), Components.read(f, "suspension", SuspensionDesign::read),
                Components.read(f, "differential", DifferentialDesign::read),
                f.has("hardpoints") ? Components.read(f, "hardpoints", HardpointsDesign::read) : null);
    }
}
