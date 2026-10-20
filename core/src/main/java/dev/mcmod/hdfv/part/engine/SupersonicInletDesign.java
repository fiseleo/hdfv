package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.SupersonicInlet;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Supersonic inlet (超音速進氣道, 0.10; AeroCore's SupersonicInlet): a pitot (one normal shock), two-dimensional ramps
 * (as many as given, at Oswatitsch's equal-strength angles for the design Mach number) or an axisymmetric cone (its
 * half-angle), the design Mach number (the shocks on the lip), the subsonic diffuser's total-pressure recovery, the
 * capture area, the duct's length and material. Its skin must take the ram air's total temperature at the design point
 * (checked by the engine). Mass: the duct as a shell of the capture diameter over its length, plus a quarter of that for
 * the ramps or cone and the lip (an estimate).
 */
public record SupersonicInletDesign(String name, SupersonicInlet.Kind kind, double designMach, int ramps, double coneHalfAngleDeg, double subsonicRecovery,
                                    double captureAreaM2, double lengthM, EngineMaterial material, double massGivenKg)
        implements EngineComponent, dev.mcmod.hdfv.design.RecordEditor.Shown {
    public static final String TYPE = "hdfv:supersonic_inlet";

    /** Ramps for a ramp inlet, the cone's angle for a cone inlet. */
    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "ramps" -> kind == SupersonicInlet.Kind.RAMPS;
            case "coneHalfAngleDeg" -> kind == SupersonicInlet.Kind.CONE;
            default -> true;
        };
    }

    public SupersonicInletDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    /** The compression's deflections, rad: Oswatitsch's ramps, the cone, or none. */
    public double[] angles() {
        return switch (kind) {
            case PITOT -> new double[0];
            case RAMPS -> SupersonicInlet.optimalRamps(designMach, Math.max(1, ramps));
            case CONE -> new double[]{Math.toRadians(coneHalfAngleDeg)};
        };
    }

    public SupersonicInlet.Design design() { return new SupersonicInlet.Design(kind, designMach, angles(), subsonicRecovery); }

    public double captureDiameterM() { return Math.sqrt(4 * captureAreaM2 / Math.PI); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(designMach > 1 && designMach <= 12)) { issues.add(DesignIssue.invalid("designMach", "A supersonic inlet is designed for Mach 1 to 12")); }
        else { DesignIssue.range(issues, "designMach", designMach, 1.2, 6, "external-compression inlets"); }
        if (kind == SupersonicInlet.Kind.RAMPS) { EngineComponent.Rules.count(issues, "ramps", ramps, 1, 6); }
        if (kind == SupersonicInlet.Kind.CONE && !(coneHalfAngleDeg > 2 && coneHalfAngleDeg < 45)) { issues.add(DesignIssue.invalid("coneHalfAngleDeg", "The cone's half-angle must be 2 to 45 degrees")); }
        if (kind == SupersonicInlet.Kind.PITOT) { DesignIssue.range(issues, "designMach", designMach, 1, 2, "pitot inlets (one normal shock)"); }
        if (!(subsonicRecovery > 0.5 && subsonicRecovery <= 1)) { issues.add(DesignIssue.invalid("subsonicRecovery", "Recovery must be in (0.5, 1]")); }
        else { DesignIssue.range(issues, "subsonicRecovery", subsonicRecovery, 0.9, 0.99, "subsonic diffusers"); }
        EngineComponent.Rules.positive(issues, "captureAreaM2", captureAreaM2);
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        if (issues.stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) {
            try { design(); } catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid(kind == SupersonicInlet.Kind.CONE ? "coneHalfAngleDeg" : "designMach", e.getMessage())); }
        }
        return issues;
    }

    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double d = captureDiameterM();
        return 1.25 * Math.PI * d * lengthM * EngineComponent.Rules.shell(d) * material.densityKgPerM3();
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("kind", kind.name().toLowerCase(java.util.Locale.ROOT), "designMach", designMach, "ramps", ramps, "coneHalfAngleDeg", coneHalfAngleDeg,
                "subsonicRecovery", subsonicRecovery, "captureAreaM2", captureAreaM2, "lengthM", lengthM, "material", material.toJson(),
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static SupersonicInletDesign read(String name, Fields f) {
        return new SupersonicInletDesign(name, EngineComponent.Rules.choice(f, "kind", SupersonicInlet.Kind.class, SupersonicInlet.Kind.CONE), f.number("designMach", 2.5),
                (int) f.number("ramps", 2), f.number("coneHalfAngleDeg", 15), f.number("subsonicRecovery", 0.95), f.number("captureAreaM2", 0.1),
                f.number("lengthM", 1), EngineComponent.Rules.material(f, "material", EngineMaterial.TITANIUM_6AL4V), EngineComponent.Rules.massOr(f));
    }
}
