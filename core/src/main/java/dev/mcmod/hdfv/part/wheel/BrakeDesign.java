package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.wheel.Brake;
import dev.mcmod.aerocore.api.wheel.BrakeControl;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A wheel brake (煞車), one per wheel: a disc (solid or vented) under a caliper, or a drum around its shoes, or none.
 *
 * <ul>
 * <li>Torque at full demand with cold linings (mu 0.40): the line pressure at full pedal on the pistons' area gives
 *     the actuating force F; a disc's drag is 2 mu F (both pads) at the pads' effective radius, 0.41 of the disc's
 *     diameter (a 288 mm disc's pads centre about 118 mm out); a drum's is its brake factor (AeroCore Brake.Type,
 *     DARCOM-P 706-358) times F at the drum's radius. pistons are the pistons pressing one face of the disc (a floating
 *     caliper's, or one side of an opposed one), or the wheel cylinders of a drum.</li>
 * <li>Rotor (cast iron, 7200 kg/m^3): a disc is an annulus out to its diameter from 0.55 of it, its thickness solid
 *     (a vented disc 0.65 of it, the vanes' gaps), plus 15 % for its hat; cooled over both faces. A drum's wall is
 *     3.5 % of its diameter thick over its width, with a back as thick; cooled over its outside and back.</li>
 * <li>Mass of one brake: the rotor plus a caliper of 2 kg and 1 kg a piston, or a drum's shoes and backing plate (3 kg)
 *     (estimates: a floating caliper with its bracket 3-4 kg); given, it replaces the estimate.</li>
 * <li>handbrakeShare (axles only): the parking brake's torque on each wheel as a share of the service brake's cold
 *     maximum (a cable's pull is far below a hydraulic pedal's; about a third holds a car on a 30 % slope).</li>
 * <li>abs: anti-lock braking on each wheel (AntiLock).</li>
 * </ul>
 */
public record BrakeDesign(String name, Kind kind, double rotorDiameterM, double rotorThicknessM, double pistonDiameterM, int pistons, double maxPressurePa,
                          double handbrakeShare, boolean abs, double massGivenKg) implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:brake";
    /** Cast iron's density, kg/m^3; the pads' effective radius over the disc's diameter. */
    public static final double IRON = 7200, PAD_RADIUS = 0.41;

    /** Brake kinds (none: a wheel that only rolls). */
    public enum Kind {
        NONE(null), DISC(Brake.Type.DISC), VENTED_DISC(Brake.Type.VENTED_DISC), DRUM_LEADING_TRAILING(Brake.Type.DRUM_LEADING_TRAILING),
        DRUM_TWO_LEADING(Brake.Type.DRUM_TWO_LEADING), DRUM_DUO_SERVO(Brake.Type.DRUM_DUO_SERVO);

        public final Brake.Type type;

        Kind(Brake.Type type) { this.type = type; }

        public boolean drum() { return type != null && name().startsWith("DRUM"); }
    }

    public BrakeDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) { return kind != Kind.NONE || parameter.equals("kind"); }

    /** The pistons' area, m^2. */
    public double pistonAreaM2() { return pistons * Math.PI / 4 * pistonDiameterM * pistonDiameterM; }

    /** Torque at full demand with cold linings, N m (0 without a brake). */
    public double maxTorqueNm() {
        if (kind == Kind.NONE) { return 0; }
        double force = maxPressurePa * pistonAreaM2();
        return kind.drum() ? kind.type.factor(Brake.COLD_MU) * force * rotorDiameterM / 2 : 2 * Brake.COLD_MU * force * PAD_RADIUS * rotorDiameterM;
    }

    /** The rotor's (disc's or drum's) mass, kg. */
    public double rotorMassKg() {
        if (kind == Kind.NONE) { return 0; }
        double d = rotorDiameterM;
        if (kind.drum()) {
            double wall = 0.035 * d;
            return IRON * (Math.PI * d * rotorThicknessM * wall + Math.PI / 4 * d * d * wall);
        }
        double annulus = Math.PI / 4 * (d * d - Math.pow(0.55 * d, 2));
        return 1.15 * IRON * annulus * rotorThicknessM * (kind == Kind.VENTED_DISC ? 0.65 : 1);
    }

    /** The rotor's cooled area, m^2. */
    public double cooledAreaM2() {
        double d = rotorDiameterM;
        return kind.drum() ? Math.PI * d * rotorThicknessM + Math.PI / 4 * d * d : 2 * Math.PI / 4 * (d * d - Math.pow(0.55 * d, 2));
    }

    /** The rotor's moment of inertia about the axle, kg m^2 (a disc's annulus, a drum's wall at its radius). */
    public double rotorInertiaKgM2() {
        if (kind == Kind.NONE) { return 0; }
        double r = rotorDiameterM / 2;
        return kind.drum() ? rotorMassKg() * r * r * 0.8 : rotorMassKg() * (r * r + Math.pow(0.55 * r, 2)) / 2;
    }

    /** AeroCore's brake, or null for none. */
    public Brake brake() {
        return kind == Kind.NONE ? null : new Brake(kind.type, maxTorqueNm(), rotorMassKg(), rotorDiameterM, cooledAreaM2());
    }

    /** The anti-lock rule, or null without one. */
    public BrakeControl control() { return abs && kind != Kind.NONE ? new AntiLock() : null; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        Rules.mass(issues, massGivenKg);
        if (kind == Kind.NONE) { return issues; }
        if (!(rotorDiameterM > 0.05 && rotorDiameterM < 2)) { issues.add(DesignIssue.invalid("rotorDiameterM", "The disc or drum must be 50 mm to 2 m across")); }
        if (!(rotorThicknessM > 0.003 && rotorThicknessM < 0.3)) { issues.add(DesignIssue.invalid("rotorThicknessM", "The disc's thickness or drum's width must be 3 to 300 mm")); }
        if (!(pistonDiameterM > 0.005 && pistonDiameterM < 0.15)) { issues.add(DesignIssue.invalid("pistonDiameterM", "The pistons must be 5 to 150 mm across")); }
        Rules.count(issues, "pistons", pistons, 1, kind.drum() ? 2 : 6);
        if (!(maxPressurePa > 0.3e6 && maxPressurePa < 25e6)) { issues.add(DesignIssue.invalid("maxPressurePa", "The line pressure must be 0.3 to 25 MPa")); }
        if (!(handbrakeShare >= 0 && handbrakeShare <= 1)) { issues.add(DesignIssue.invalid("handbrakeShare", "The parking brake's share must be 0 to 1")); }
        if (!issues.isEmpty()) { return issues; }
        DesignIssue.range(issues, "maxPressurePa", maxPressurePa, 4e6, 16e6, "hydraulic brake line pressures at a full stop (Limpert: 6-10 MPa for cars, up to 16 MPa)");
        if (kind == Kind.VENTED_DISC) { DesignIssue.range(issues, "rotorThicknessM", rotorThicknessM, 0.018, 0.040, "vented discs 18-40 mm thick"); }
        else if (kind == Kind.DISC) { DesignIssue.range(issues, "rotorThicknessM", rotorThicknessM, 0.005, 0.016, "solid discs (aircraft 5-8 mm, cars 9-13 mm)"); }
        return issues;
    }

    /** Mass of one brake, kg. */
    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        if (kind == Kind.NONE) { return 0; }
        return rotorMassKg() + (kind.drum() ? 3 : 2 + pistons);
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("kind", kind.name().toLowerCase(), "rotorDiameterM", rotorDiameterM, "rotorThicknessM", rotorThicknessM, "pistonDiameterM", pistonDiameterM,
                "pistons", pistons, "maxPressurePa", maxPressurePa, "handbrakeShare", handbrakeShare, "abs", abs, "massKg", Rules.mass(massGivenKg));
    }

    public static BrakeDesign read(String name, Fields f) {
        return new BrakeDesign(name, Rules.choice(f, "kind", Kind.class, Kind.VENTED_DISC), f.number("rotorDiameterM", 0.28), f.number("rotorThicknessM", 0.025),
                f.number("pistonDiameterM", 0.054), f.integer("pistons", 1), f.number("maxPressurePa", 8e6), f.number("handbrakeShare", 0),
                f.bool("abs", false), Rules.massOr(f));
    }

    /** No brake at all. */
    public static BrakeDesign none() { return new BrakeDesign("brake", Kind.NONE, 0.2, 0.012, 0.04, 1, 8e6, 0, false, Double.NaN); }
}
