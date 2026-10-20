package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A landing-gear leg's strut (支柱): what carries the wheel and takes the landing.
 *
 * <ul>
 * <li>Oleo-pneumatic (Milwitzky and Cook, NACA TR-1154; Currey, Aircraft Landing Gear Design): gas over a piston of a
 *     diameter, at an absolute pressure when fully extended, its volume set by the compression ratio (extended over
 *     fully compressed: V0 = r A s / (r - 1)), oil forced through an orifice of a diameter (oil 850 kg/m^3, discharge
 *     coefficient 0.9), harder by a multiple in rebound; and a linear part for the seals' friction and the metering
 *     pin's bleed, a sixth of critical for the design load on the gas's stiffness where it stands (rule: without it a
 *     strut at rest, where the orifice's v^2 vanishes, would bob for seconds).</li>
 * <li>Spring steel (a Cessna's main legs): a rate at the wheel, unloaded at full droop, with a little damping.</li>
 * <li>Rigid: a tailskid's or a glider's wheel; the tyre takes everything (the strut gives 2 mm under its load).</li>
 * </ul>
 * designLoadKg is the mass the leg carries standing (its share of the aircraft): it sets the static position and the
 * checks. Mass of the strut and its fittings: oleo 2 kg + 2 % of the design load (more for a longer stroke, as its
 * square root over 0.15 m), spring steel 1.5 kg + 0.9 %, rigid 1 kg + 0.5 % (estimates: a light aircraft's nose oleo
 * 7-8 kg, a main spring-steel leg 5-6 kg).
 */
public record StrutDesign(String name, Kind kind, double strokeM, double designLoadKg, double pistonDiameterM, double extendedPressurePa,
                          double compressionRatio, double orificeDiameterM, double reboundMultiple, double springRateNPerM, double dampingNsPerM,
                          double massGivenKg) implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:strut";
    public static final double G = 9.80665, ATMOSPHERE = 101325, OIL = 850;
    /** The linear part's share of critical damping at the static position. */
    public static final double SEAL = 1.0 / 6;

    /** Strut kinds. */
    public enum Kind { OLEO, SPRING_STEEL, RIGID }

    public StrutDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "pistonDiameterM", "extendedPressurePa", "compressionRatio", "orificeDiameterM", "reboundMultiple" -> kind == Kind.OLEO;
            case "springRateNPerM", "dampingNsPerM" -> kind == Kind.SPRING_STEEL;
            case "strokeM" -> kind != Kind.RIGID;
            default -> true;
        };
    }

    /** The stroke the strut has (a rigid one's few millimetres). */
    public double stroke() { return kind == Kind.RIGID ? 0.01 : strokeM; }

    public double pistonAreaM2() { return Math.PI / 4 * pistonDiameterM * pistonDiameterM; }

    private Suspension.Spring spring() {
        return switch (kind) {
            case OLEO -> Suspension.Spring.oleo(pistonAreaM2(), compressionRatio * pistonAreaM2() * strokeM / (compressionRatio - 1), extendedPressurePa);
            case SPRING_STEEL -> Suspension.Spring.coil(springRateNPerM, 0);
            case RIGID -> Suspension.Spring.coil(designLoadKg * G / 0.002, 0);
        };
    }

    private Suspension.Damper damper() {
        return switch (kind) {
            case OLEO -> {
                double s = staticCompressionM(), k = spring().rate(Double.isNaN(s) ? strokeM : s);
                yield Suspension.Damper.orifice(pistonAreaM2(), Math.PI / 4 * orificeDiameterM * orificeDiameterM, OIL, reboundMultiple,
                        2 * SEAL * Math.sqrt(k * Math.max(designLoadKg, 1)));
            }
            case SPRING_STEEL -> Suspension.Damper.linear(dampingNsPerM, dampingNsPerM);
            case RIGID -> {
                double c = 0.4 * Math.sqrt(designLoadKg * designLoadKg * G / 0.002);
                yield Suspension.Damper.linear(c, c);
            }
        };
    }

    /** Where the strut stands under its design load, m of compression from full extension; NaN when the load bottoms it. */
    public double staticCompressionM() {
        Suspension.Spring s = spring();
        double load = designLoadKg * G, stroke = stroke();
        if (s.force(0) >= load) { return 0; }
        if (s.force(stroke) < load) { return Double.NaN; }
        double lo = 0, hi = stroke;
        for (int i = 0; i < 60; i++) { double mid = (lo + hi) / 2; if (s.force(mid) < load) { lo = mid; } else { hi = mid; } }
        return (lo + hi) / 2;
    }

    /** AeroCore's suspension of the leg: no roll centre or camber change, the strut's spring and damper over its stroke, the design compression its static one. */
    public Suspension suspension() {
        double s = staticCompressionM();
        return new Suspension(new Suspension.Geometry(0, 0, false), spring(), damper(), stroke(), 0, Double.isNaN(s) ? stroke() : s);
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        Rules.positive(issues, "designLoadKg", designLoadKg);
        Rules.mass(issues, massGivenKg);
        if (kind != Kind.RIGID && !(strokeM > 0.01 && strokeM < 2)) { issues.add(DesignIssue.invalid("strokeM", "The stroke must be 10 mm to 2 m")); }
        if (kind == Kind.OLEO) {
            if (!(pistonDiameterM > 0.01 && pistonDiameterM < 0.6)) { issues.add(DesignIssue.invalid("pistonDiameterM", "The piston must be 10 to 600 mm across")); }
            if (!(extendedPressurePa > ATMOSPHERE * 1.05 && extendedPressurePa < 20e6)) { issues.add(DesignIssue.invalid("extendedPressurePa", "The gas must be above the atmosphere's pressure, below 20 MPa (absolute)")); }
            if (!(compressionRatio > 1.2 && compressionRatio < 30)) { issues.add(DesignIssue.invalid("compressionRatio", "The compression ratio must be 1.2 to 30")); }
            if (!(orificeDiameterM > 0.001 && orificeDiameterM < pistonDiameterM)) { issues.add(DesignIssue.invalid("orificeDiameterM", "The orifice must be 1 mm across, smaller than the piston")); }
            if (!(reboundMultiple >= 1 && reboundMultiple <= 50)) { issues.add(DesignIssue.invalid("reboundMultiple", "Rebound damping must be 1 to 50 times the compression's")); }
        } else if (kind == Kind.SPRING_STEEL) {
            Rules.positive(issues, "springRateNPerM", springRateNPerM);
            Rules.nonnegative(issues, "dampingNsPerM", dampingNsPerM);
        }
        if (!issues.isEmpty()) { return issues; }
        double s = staticCompressionM();
        if (Double.isNaN(s)) { issues.add(DesignIssue.invalid("designLoadKg", "The strut bottoms under its own load standing")); }
        else if (kind == Kind.OLEO) { DesignIssue.range(issues, "staticStroke", s / strokeM, 0.5, 0.92, "oleo struts stand at 60-90 % of their stroke (Currey)"); }
        else if (kind == Kind.SPRING_STEEL) { DesignIssue.range(issues, "staticStroke", s / strokeM, 0.15, 0.6, "spring legs keep most of their deflection for the landing"); }
        return issues;
    }

    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double load = Math.max(designLoadKg, 0);
        return switch (kind) {
            case OLEO -> 2 + 0.02 * load * Math.sqrt(Math.max(strokeM, 0.01) / 0.15);
            case SPRING_STEEL -> 1.5 + 0.009 * load;
            case RIGID -> 1 + 0.005 * load;
        };
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("kind", kind.name().toLowerCase(), "strokeM", strokeM, "designLoadKg", designLoadKg, "pistonDiameterM", pistonDiameterM,
                "extendedPressurePa", extendedPressurePa, "compressionRatio", compressionRatio, "orificeDiameterM", orificeDiameterM, "reboundMultiple", reboundMultiple,
                "springRateNPerM", springRateNPerM, "dampingNsPerM", dampingNsPerM, "massKg", Rules.mass(massGivenKg));
    }

    public static StrutDesign read(String name, Fields f) {
        return new StrutDesign(name, Rules.choice(f, "kind", Kind.class, Kind.OLEO), f.number("strokeM", 0.15), f.number("designLoadKg", 300),
                f.number("pistonDiameterM", 0.05), f.number("extendedPressurePa", 0.6e6), f.number("compressionRatio", 4), f.number("orificeDiameterM", 0.007),
                f.number("reboundMultiple", 4), f.number("springRateNPerM", 60000), f.number("dampingNsPerM", 300), Rules.massOr(f));
    }
}
