package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.Reheat;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Afterburner (後燃器, 0.10; AeroCore's Reheat): whether one is fitted, the largest reheat temperature, the burner's
 * efficiency, the Mach number of the gas entering it (the duct's size: slower, less Rayleigh loss, a fatter duct), the
 * largest the variable nozzle's petals open over the dry throat, the duct's length and its liner's material. The liner
 * is film-cooled by the gas from the turbine: its metal runs at the turbine exhaust temperature plus 0.3 of the way to
 * the reheat temperature (a rule after film-cooled liners: the J58 ran its Hastelloy X liner at 2144 K reheat), which must
 * not pass the material's service temperature. Mass: the duct and liner as two shells of 1.4 times the nozzle throat's
 * diameter over the length, plus the variable nozzle's petals and actuators, 25 % of that (an estimate; the J79's
 * afterburner and nozzle are about a fifth of its 1750 kg).
 */
public record AfterburnerDesign(String name, boolean fitted, double maxTemperatureK, double efficiency, double inletMach, double maxThroatRatio, double lengthM,
                                EngineMaterial material, double massGivenKg) implements EngineComponent, dev.mcmod.hdfv.design.RecordEditor.Shown {
    public static final String TYPE = "hdfv:afterburner";

    /** Without one fitted, only the switch. */
    @Override public boolean shows(String parameter) { return fitted || parameter.equals("fitted"); }
    /** How far from the coolant (turbine exhaust) to the reheat temperature the film-cooled liner's metal runs (a rule). */
    public static final double LINER = 0.3;

    public AfterburnerDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    /** No afterburner (what blueprints from before 0.10 read as). */
    public static AfterburnerDesign none() { return new AfterburnerDesign("afterburner", false, 2000, 0.9, 0.25, 1.6, 1.5, EngineMaterial.INCONEL_713C, Double.NaN); }

    @Override public String type() { return TYPE; }

    /** AeroCore's reheat design, or null when none is fitted. */
    public Reheat.Design reheat() { return fitted ? new Reheat.Design(maxTemperatureK, efficiency, inletMach, maxThroatRatio) : null; }

    /** The liner's metal temperature at a reheat temperature over a turbine exhaust at exhaustK. */
    public double linerTemperature(double reheatK, double exhaustK) { return exhaustK + LINER * (reheatK - exhaustK); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!fitted) { return issues; }
        if (!(maxTemperatureK > 1000 && maxTemperatureK < 2600)) { issues.add(DesignIssue.invalid("maxTemperatureK", "Reheat runs between 1000 and 2600 K (stoichiometric)")); }
        else { DesignIssue.range(issues, "maxTemperatureK", maxTemperatureK, 1600, 2200, "afterburners in service"); }
        if (!(efficiency > 0 && efficiency <= 1)) { issues.add(DesignIssue.invalid("efficiency", "Efficiency must be in (0, 1]")); }
        else { DesignIssue.range(issues, "efficiency", efficiency, 0.85, 0.95, "afterburner combustion efficiencies"); }
        if (!(inletMach > 0 && inletMach < 0.6)) { issues.add(DesignIssue.invalid("inletMach", "The gas enters the afterburner at Mach 0 to 0.6")); }
        else { DesignIssue.range(issues, "inletMach", inletMach, 0.15, 0.3, "afterburner ducts"); }
        if (!(maxThroatRatio >= 1)) { issues.add(DesignIssue.invalid("maxThroatRatio", "The variable nozzle opens to at least the dry throat")); }
        else { DesignIssue.range(issues, "maxThroatRatio", maxThroatRatio, 1.3, 2.2, "variable nozzles"); }
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Mass for a dry nozzle throat area, kg (none when not fitted). */
    public double massFor(double throatAreaM2) {
        if (!fitted) { return 0; }
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double d = 1.4 * Math.sqrt(4 * throatAreaM2 / Math.PI);
        double shells = 2 * Math.PI * d * lengthM * EngineComponent.Rules.shell(d) * material.densityKgPerM3();
        return 1.25 * shells;
    }
    @Override public double massKg() { return fitted && !Double.isNaN(massGivenKg) ? massGivenKg : 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("fitted", fitted, "maxTemperatureK", maxTemperatureK, "efficiency", efficiency, "inletMach", inletMach, "maxThroatRatio", maxThroatRatio,
                "lengthM", lengthM, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static AfterburnerDesign read(String name, Fields f) {
        AfterburnerDesign d = none();
        return new AfterburnerDesign(name, f.bool("fitted", false), f.number("maxTemperatureK", d.maxTemperatureK()), f.number("efficiency", d.efficiency()),
                f.number("inletMach", d.inletMach()), f.number("maxThroatRatio", d.maxThroatRatio()), f.number("lengthM", d.lengthM()),
                EngineComponent.Rules.material(f, "material", d.material()), EngineComponent.Rules.massOr(f));
    }
}
