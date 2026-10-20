package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scramjet combustor and nozzle (超燃燃燒室與噴嘴, 0.10): the combustor's length, its exit over entry area (a diverging
 * duct), the walls' Fanning friction factor, the mixing-limited combustion efficiency, the fuel's axial injection speed
 * over the stream's; the nozzle's exit over the combustor's exit area and its velocity coefficient; whether the walls
 * are cooled by the fuel on its way in (regenerative), and their material.
 * <p>
 * Wall rule: an uncooled wall runs at the ram air's total temperature, which must not pass the material's service
 * temperature (above about Mach 5 no metal takes it). Fuel-cooled walls hold their material's temperature while the
 * fuel can take the heat: hydrogen (its heat sink from 20 K to 1000 K is some 14 MJ/kg) always here; a hydrocarbon
 * (about 3 MJ/kg, endothermic cracking included) up to a ram total temperature of 2200 K (the X-51A's JP-7 cooled its
 * walls to Mach 5.1).
 * Mass: combustor and nozzle walls as shells of the throat's width over their lengths (the nozzle as long as the
 * combustor), plus the cooling passages' manifold, a fifth more when cooled (an estimate).
 */
public record ScramCombustorDesign(String name, double lengthM, double areaRatio, double frictionFactor, double combustionEfficiency,
                                   double injectantVelocityRatio, double nozzleAreaRatio, double nozzleVelocityCoefficient, boolean fuelCooled,
                                   EngineMaterial material, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:scram_combustor";
    /** The ram total temperature up to which a hydrocarbon's heat sink cools the walls (K). */
    public static final double HYDROCARBON_COOLING_K = 2200;

    public ScramCombustorDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    /** Whether the walls survive a ram total temperature with a fuel (null fuel: unknown). */
    public boolean wallsHold(double ramTotalK, String fuel) {
        if (fuelCooled) { return fuel.equals("hydrogen") || ramTotalK <= HYDROCARBON_COOLING_K; }
        return ramTotalK <= material.serviceTemperatureK();
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        if (!(areaRatio >= 1 && areaRatio <= 6)) { issues.add(DesignIssue.invalid("areaRatio", "The combustor widens 1 to 6 times")); }
        else { DesignIssue.range(issues, "areaRatio", areaRatio, 1.5, 3, "scramjet combustors"); }
        if (!(frictionFactor >= 0 && frictionFactor <= 0.01)) { issues.add(DesignIssue.invalid("frictionFactor", "The friction factor is 0 to 0.01")); }
        if (!(combustionEfficiency > 0 && combustionEfficiency <= 1)) { issues.add(DesignIssue.invalid("combustionEfficiency", "Efficiency must be in (0, 1]")); }
        else { DesignIssue.range(issues, "combustionEfficiency", combustionEfficiency, 0.7, 0.95, "mixing-limited supersonic combustion"); }
        if (!(injectantVelocityRatio >= 0 && injectantVelocityRatio <= 1.5)) { issues.add(DesignIssue.invalid("injectantVelocityRatio", "The fuel's axial speed is 0 to 1.5 of the stream's")); }
        if (!(nozzleAreaRatio >= 1 && nozzleAreaRatio <= 20)) { issues.add(DesignIssue.invalid("nozzleAreaRatio", "The nozzle opens 1 to 20 times")); }
        if (!(nozzleVelocityCoefficient > 0.8 && nozzleVelocityCoefficient <= 1)) { issues.add(DesignIssue.invalid("nozzleVelocityCoefficient", "The velocity coefficient is 0.8 to 1")); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Mass for an isolator throat area, kg. */
    public double massFor(double throatAreaM2) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double w = Math.sqrt(throatAreaM2 * areaRatio);
        double walls = 4 * w * 2 * lengthM * EngineComponent.Rules.shell(Math.max(w, 0.05)) * material.densityKgPerM3();
        return walls * (fuelCooled ? 1.2 : 1);
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lengthM", lengthM, "areaRatio", areaRatio, "frictionFactor", frictionFactor, "combustionEfficiency", combustionEfficiency,
                "injectantVelocityRatio", injectantVelocityRatio, "nozzleAreaRatio", nozzleAreaRatio, "nozzleVelocityCoefficient", nozzleVelocityCoefficient,
                "fuelCooled", fuelCooled, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static ScramCombustorDesign read(String name, Fields f) {
        return new ScramCombustorDesign(name, f.number("lengthM", 1), f.number("areaRatio", 2.5), f.number("frictionFactor", 0.0015),
                f.number("combustionEfficiency", 0.9), f.number("injectantVelocityRatio", 0.5), f.number("nozzleAreaRatio", 3),
                f.number("nozzleVelocityCoefficient", 0.98), f.bool("fuelCooled", true), EngineComponent.Rules.material(f, "material", EngineMaterial.INCONEL_718),
                EngineComponent.Rules.massOr(f));
    }
}
