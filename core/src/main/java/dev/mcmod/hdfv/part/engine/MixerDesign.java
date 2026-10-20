package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exhaust mixer (混合器) of a turbofan: none (the bypass air leaves through its own nozzle), or a lobed mixer that
 * joins the bypass air to the core stream before a common nozzle; its total-pressure loss (lobed mixers lose about
 * 1-3 %), lobes, length, material. The mixing is at constant static pressure (Turbofan): it gains a little thrust when
 * the loss is small. Mass: the lobed sheet, twice a cone's area of the core nozzle's diameter over its length, 0.4 % of
 * that diameter thick, at least 0.3 mm (estimate).
 */
public record MixerDesign(String name, Kind kind, double pressureLoss, int lobes, double lengthM, EngineMaterial material, double massGivenKg)
        implements EngineComponent {
    public static final String TYPE = "hdfv:mixer";

    public enum Kind { NONE, LOBED }

    public MixerDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    public boolean mixes() { return kind != Kind.NONE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(pressureLoss >= 0 && pressureLoss < 0.2)) { issues.add(DesignIssue.invalid("pressureLoss", "Pressure loss must be in [0, 0.2)")); }
        else if (mixes()) { DesignIssue.range(issues, "pressureLoss", pressureLoss, 0.005, 0.04, "lobed mixers"); }
        if (mixes()) { EngineComponent.Rules.count(issues, "lobes", lobes, 4, 40); }
        EngineComponent.Rules.nonnegative(issues, "lengthM", lengthM);
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Length the mixer adds to the engine, m. */
    public double addedLengthM() { return mixes() ? lengthM : 0; }

    /** Mass for a core nozzle diameter, kg. */
    public double massFor(double diameterM) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        return mixes() ? 2 * Math.PI * diameterM / 2 * lengthM * EngineComponent.Rules.shell(diameterM) * material.densityKgPerM3() : 0;
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("kind", kind.name().toLowerCase(), "pressureLoss", pressureLoss, "lobes", lobes, "lengthM", lengthM, "material", material.toJson(),
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static MixerDesign read(String name, Fields f) {
        return new MixerDesign(name, EngineComponent.Rules.choice(f, "kind", Kind.class, Kind.NONE), f.number("pressureLoss", 0.02), f.integer("lobes", 12),
                f.number("lengthM", 0.3), EngineComponent.Rules.material(f, "material", EngineMaterial.INCONEL_718), EngineComponent.Rules.massOr(f));
    }
}
