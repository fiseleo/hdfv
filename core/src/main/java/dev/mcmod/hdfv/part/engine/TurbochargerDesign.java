package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.Turbocharger;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turbocharger (渦輪增壓器, 0.8): fitted or not; the compressor's impeller tip diameter, blade count and polytropic
 * efficiency; the turbine's effective flow area and efficiency; the wastegate's boost (most pressure ratio); the
 * intercooler's effectiveness (0 for none); the rotor's inertia (its lag). The physics is AeroCore's Turbocharger.
 * Mass when not given: about 7 kg for a 50 mm impeller, with the square of its size, and an intercooler of about 6 kg
 * at 0.7 effectiveness (estimates).
 */
public record TurbochargerDesign(String name, boolean fitted, double compressorTipM, int compressorBlades, double compressorEfficiency,
                                 double turbineAreaM2, double turbineEfficiency, double maxBoostRatio, double intercoolerEffectiveness,
                                 double rotorInertiaKgM2, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:turbocharger";
    /** Turbine shaft and bearings: about 95 % mechanical efficiency (Watson and Janota). */
    public static final double MECHANICAL_EFFICIENCY = 0.95;

    public TurbochargerDesign { Objects.requireNonNull(name, "name"); }

    /** No turbocharger (what engines saved before 0.8 read as). */
    public static TurbochargerDesign none() { return new TurbochargerDesign("turbo", false, 0.05, 12, 0.75, 4e-4, 0.70, 2.0, 0.7, 2e-5, Double.NaN); }

    @Override public String type() { return TYPE; }

    /** AeroCore's turbocharger of this design. */
    public Turbocharger model() {
        return new Turbocharger(compressorTipM, compressorBlades, compressorEfficiency, turbineAreaM2, turbineEfficiency, MECHANICAL_EFFICIENCY,
                maxBoostRatio, intercoolerEffectiveness, rotorInertiaKgM2);
    }

    /** Seconds the boost takes to follow its target at a speed (rpm): the rotor's inertia, slower on little exhaust (a rule). */
    public double lagSeconds(double rpm) { return (0.3 + 25000 * rotorInertiaKgM2) * Math.max(1, 3000 / Math.max(rpm, 300)); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!fitted) { return issues; }
        EngineComponent.Rules.positive(issues, "compressorTipM", compressorTipM);
        EngineComponent.Rules.count(issues, "compressorBlades", compressorBlades, 4, 20);
        EngineComponent.Rules.positive(issues, "turbineAreaM2", turbineAreaM2);
        EngineComponent.Rules.positive(issues, "rotorInertiaKgM2", rotorInertiaKgM2);
        if (!(compressorEfficiency > 0 && compressorEfficiency <= 1)) { issues.add(DesignIssue.invalid("compressorEfficiency", "Efficiency must be in (0, 1]")); }
        if (!(turbineEfficiency > 0 && turbineEfficiency <= 1)) { issues.add(DesignIssue.invalid("turbineEfficiency", "Efficiency must be in (0, 1]")); }
        if (!(intercoolerEffectiveness >= 0 && intercoolerEffectiveness < 1)) { issues.add(DesignIssue.invalid("intercoolerEffectiveness", "Effectiveness must be in [0, 1)")); }
        if (!(maxBoostRatio > 1 && maxBoostRatio <= 5)) { issues.add(DesignIssue.invalid("maxBoostRatio", "Boost ratio must be in (1, 5]")); }
        else { DesignIssue.range(issues, "maxBoostRatio", maxBoostRatio, 1.2, 3.5, "single-stage automotive and industrial turbochargers"); }
        if (compressorTipM > 0) { DesignIssue.range(issues, "compressorTipM", compressorTipM, 0.03, 0.15, "car to truck turbochargers"); }
        if (compressorEfficiency > 0) { DesignIssue.range(issues, "compressorEfficiency", compressorEfficiency, 0.65, 0.82, "centrifugal compressors of turbochargers"); }
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    @Override public double massKg() {
        if (!fitted) { return 0; }
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        return 7 * Math.pow(compressorTipM / 0.05, 2) + 6 * intercoolerEffectiveness / 0.7;
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("fitted", fitted, "compressorTipM", compressorTipM, "compressorBlades", compressorBlades, "compressorEfficiency", compressorEfficiency,
                "turbineAreaM2", turbineAreaM2, "turbineEfficiency", turbineEfficiency, "maxBoostRatio", maxBoostRatio,
                "intercoolerEffectiveness", intercoolerEffectiveness, "rotorInertiaKgM2", rotorInertiaKgM2, "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static TurbochargerDesign read(String name, Fields f) {
        TurbochargerDesign d = none();
        return new TurbochargerDesign(name, f.bool("fitted", false), f.number("compressorTipM", d.compressorTipM()), f.integer("compressorBlades", d.compressorBlades()),
                f.number("compressorEfficiency", d.compressorEfficiency()), f.number("turbineAreaM2", d.turbineAreaM2()), f.number("turbineEfficiency", d.turbineEfficiency()),
                f.number("maxBoostRatio", d.maxBoostRatio()), f.number("intercoolerEffectiveness", d.intercoolerEffectiveness()),
                f.number("rotorInertiaKgM2", d.rotorInertiaKgM2()), EngineComponent.Rules.massOr(f));
    }
}
