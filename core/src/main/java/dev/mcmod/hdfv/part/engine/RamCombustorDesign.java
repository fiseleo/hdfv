package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ramjet combustor (衝壓燃燒室, 0.10): its cross-section over the inlet's capture area, the flameholders' loss in dynamic
 * heads (V-gutters about 1-2), the burner's efficiency, its largest temperature, its length and liner material. The
 * liner is film-cooled by the inlet's air: its metal runs at the ram air's total temperature plus 0.3 of the way to
 * the combustor's (AfterburnerDesign.LINER), which must not pass the material's service temperature. Mass: casing and
 * liner as two shells of the combustor's diameter over its length, the flameholders 10 % of that (an estimate).
 */
public record RamCombustorDesign(String name, double areaRatio, double flameholderLoss, double efficiency, double maxTemperatureK, double lengthM,
                                 EngineMaterial material, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:ram_combustor";

    public RamCombustorDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    /** The liner's metal temperature at a combustor temperature with the ram air at its total temperature. */
    public double linerTemperature(double combustorK, double ramAirK) { return ramAirK + AfterburnerDesign.LINER * (combustorK - ramAirK); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(areaRatio > 0.5 && areaRatio <= 4)) { issues.add(DesignIssue.invalid("areaRatio", "The combustor is 0.5 to 4 times the capture area")); }
        else { DesignIssue.range(issues, "areaRatio", areaRatio, 1.2, 2.5, "ramjet combustors"); }
        if (!(flameholderLoss >= 0 && flameholderLoss <= 5)) { issues.add(DesignIssue.invalid("flameholderLoss", "The flameholders lose 0 to 5 dynamic heads")); }
        else { DesignIssue.range(issues, "flameholderLoss", flameholderLoss, 0.5, 2.5, "V-gutter flameholders"); }
        if (!(efficiency > 0 && efficiency <= 1)) { issues.add(DesignIssue.invalid("efficiency", "Efficiency must be in (0, 1]")); }
        else { DesignIssue.range(issues, "efficiency", efficiency, 0.85, 0.98, "ramjet burners"); }
        if (!(maxTemperatureK > 800 && maxTemperatureK < 2800)) { issues.add(DesignIssue.invalid("maxTemperatureK", "The combustor runs at 800 to 2800 K")); }
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Mass for a capture area, kg. */
    public double massFor(double captureAreaM2) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double d = Math.sqrt(4 * areaRatio * captureAreaM2 / Math.PI);
        return 1.1 * 2 * Math.PI * d * lengthM * EngineComponent.Rules.shell(d) * material.densityKgPerM3();
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("areaRatio", areaRatio, "flameholderLoss", flameholderLoss, "efficiency", efficiency, "maxTemperatureK", maxTemperatureK,
                "lengthM", lengthM, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static RamCombustorDesign read(String name, Fields f) {
        return new RamCombustorDesign(name, f.number("areaRatio", 1.6), f.number("flameholderLoss", 1.5), f.number("efficiency", 0.92), f.number("maxTemperatureK", 2100),
                f.number("lengthM", 1.5), EngineComponent.Rules.material(f, "material", EngineMaterial.HASTELLOY_X), EngineComponent.Rules.massOr(f));
    }
}
