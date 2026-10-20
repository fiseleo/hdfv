package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A turbo-ramjet's bleed bypass (旁通管, 0.10; the J58's): from a stage part-way up the compressor (its share of the
 * pressure ratio, log), ducts round the core into the afterburner; the bypass opens between two Mach numbers to a
 * largest share of the core's airflow, and the ducts keep a share of the total pressure. Mass: six ducts each a tenth
 * of the compressor's diameter across, along the core, as shells of the material (an estimate).
 */
public record BypassDesign(String name, double openMach, double fullMach, double maxRatio, double bleedShare, double ductRecovery, EngineMaterial material,
                           double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:bypass";

    public BypassDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(openMach >= 0 && fullMach > openMach && fullMach <= 6)) { issues.add(DesignIssue.invalid("fullMach", "The bypass opens from a Mach number to a higher one, at most 6")); }
        if (!(maxRatio >= 0 && maxRatio <= 2)) { issues.add(DesignIssue.invalid("maxRatio", "The bypass takes 0 to 2 times the core's air")); }
        else { DesignIssue.range(issues, "maxRatio", maxRatio, 0.2, 0.6, "the J58's bleed bypass (20-40 % above Mach 2)"); }
        if (!(bleedShare > 0 && bleedShare < 1)) { issues.add(DesignIssue.invalid("bleedShare", "The bleed comes from part-way up the compressor: a share in (0, 1)")); }
        if (!(ductRecovery > 0.5 && ductRecovery <= 1)) { issues.add(DesignIssue.invalid("ductRecovery", "The ducts keep 0.5 to 1 of the pressure")); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Mass for a compressor diameter and the core's length, kg. */
    public double massFor(double compressorDiameterM, double coreLengthM) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double d = 0.1 * compressorDiameterM;
        return 6 * Math.PI * d * coreLengthM * EngineComponent.Rules.shell(d) * material.densityKgPerM3();
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("openMach", openMach, "fullMach", fullMach, "maxRatio", maxRatio, "bleedShare", bleedShare, "ductRecovery", ductRecovery,
                "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static BypassDesign read(String name, Fields f) {
        return new BypassDesign(name, f.number("openMach", 2), f.number("fullMach", 3), f.number("maxRatio", 0.35), f.number("bleedShare", 0.4),
                f.number("ductRecovery", 0.9), EngineComponent.Rules.material(f, "material", EngineMaterial.TITANIUM_6AL4V), EngineComponent.Rules.massOr(f));
    }
}
