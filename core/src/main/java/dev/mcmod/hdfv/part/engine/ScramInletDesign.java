package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.SupersonicInlet;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scramjet inlet and isolator (超燃進氣道與隔離段, 0.10; AeroCore's Scramjet): two-dimensional ramps at Oswatitsch's angles
 * for the design Mach number (shocks on the lip), the capture area, the contraction (capture over isolator throat
 * area), the internal contraction (cowl entrance over throat: within Kantrowitz's limit it starts by itself), the
 * compression's kinetic energy efficiency (Heiser and Pratt: 0.95-0.98), the isolator's length in duct heights (ten
 * hold a normal shock's pressure rise), the ramp's length and material. The ramps must take the ram air's total
 * temperature at the top of the engine's range (checked by the engine). Mass: the ramp, cowl and isolator as shells of
 * the capture width over the length (an estimate).
 */
public record ScramInletDesign(String name, double designMach, int ramps, double captureAreaM2, double contractionRatio, double internalContraction,
                               double kineticEnergyEfficiency, double isolatorLengthOverHeight, double lengthM, EngineMaterial material, double massGivenKg)
        implements EngineComponent {
    public static final String TYPE = "hdfv:scram_inlet";

    public ScramInletDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    public double[] angles() { return SupersonicInlet.optimalRamps(designMach, Math.max(1, ramps)); }
    public double throatAreaM2() { return captureAreaM2 / contractionRatio; }
    /** The duct's width, m: a square capture. */
    public double widthM() { return Math.sqrt(captureAreaM2); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(designMach >= 3 && designMach <= 15)) { issues.add(DesignIssue.invalid("designMach", "A scramjet's inlet is designed for Mach 3 to 15")); }
        else { DesignIssue.range(issues, "designMach", designMach, 5, 10, "scramjets flown (X-43A, X-51A, HIFiRE)"); }
        EngineComponent.Rules.count(issues, "ramps", ramps, 1, 4);
        EngineComponent.Rules.positive(issues, "captureAreaM2", captureAreaM2);
        if (!(contractionRatio > 1.5 && contractionRatio <= 40)) { issues.add(DesignIssue.invalid("contractionRatio", "The contraction is 1.5 to 40")); }
        else { DesignIssue.range(issues, "contractionRatio", contractionRatio, 5, 20, "scramjet inlets"); }
        if (!(internalContraction >= 1 && internalContraction < contractionRatio)) { issues.add(DesignIssue.invalid("internalContraction", "The internal contraction is 1 up to the whole contraction")); }
        if (!(kineticEnergyEfficiency > 0.8 && kineticEnergyEfficiency <= 1)) { issues.add(DesignIssue.invalid("kineticEnergyEfficiency", "The kinetic energy efficiency is 0.8 to 1")); }
        else { DesignIssue.range(issues, "kineticEnergyEfficiency", kineticEnergyEfficiency, 0.95, 0.98, "Heiser and Pratt's inlets"); }
        if (!(isolatorLengthOverHeight >= 0 && isolatorLengthOverHeight <= 30)) { issues.add(DesignIssue.invalid("isolatorLengthOverHeight", "The isolator is 0 to 30 duct heights")); }
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double w = widthM(), isolator = isolatorLengthOverHeight * Math.sqrt(throatAreaM2());
        return 4 * w * (lengthM + isolator) * EngineComponent.Rules.shell(w) * material.densityKgPerM3();
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("designMach", designMach, "ramps", ramps, "captureAreaM2", captureAreaM2, "contractionRatio", contractionRatio,
                "internalContraction", internalContraction, "kineticEnergyEfficiency", kineticEnergyEfficiency, "isolatorLengthOverHeight", isolatorLengthOverHeight,
                "lengthM", lengthM, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static ScramInletDesign read(String name, Fields f) {
        return new ScramInletDesign(name, f.number("designMach", 7), (int) f.number("ramps", 2), f.number("captureAreaM2", 0.05), f.number("contractionRatio", 10),
                f.number("internalContraction", 1.5), f.number("kineticEnergyEfficiency", 0.97), f.number("isolatorLengthOverHeight", 10), f.number("lengthM", 2),
                EngineComponent.Rules.material(f, "material", EngineMaterial.INCONEL_718), EngineComponent.Rules.massOr(f));
    }
}
