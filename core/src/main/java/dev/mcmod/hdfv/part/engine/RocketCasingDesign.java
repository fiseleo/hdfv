package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.storage.PressureVessel;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A solid rocket's casing (殼體, 0.10): its wall's material and thickness, and the safety factor its burst pressure must
 * keep over the motor's highest chamber pressure (amateur practice 1.5, NAR/Tripoli; aerospace 1.25-1.4). Burst by the
 * thin-wall hoop stress at the material's strength (its yield here, which errs safe for metals; a laminate's strength
 * for CFRP and GFRP, AeroCore's PressureVessel). Mass: the cylinder over the grain plus 10 % for the liner and inhibitors
 * and two closures of the same wall, flat (an estimate).
 */
public record RocketCasingDesign(String name, EngineMaterial material, double wallThicknessM, double safetyFactor, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:rocket_casing";

    public RocketCasingDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    boolean composite() { return material.equals(EngineMaterial.CFRP) || material.equals(EngineMaterial.GFRP); }

    /** The casing's burst pressure over a grain of an outer diameter (Pa). */
    public double burstPa(double grainDiameterM) {
        double r = grainDiameterM / 2 + wallThicknessM / 2;
        return composite() ? PressureVessel.compositeCylinderBurstPa(wallThicknessM, r, material.yieldPa())
                : PressureVessel.metalCylinderBurstPa(wallThicknessM, r, material.yieldPa());
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(wallThicknessM > 0 && wallThicknessM < 0.1)) { issues.add(DesignIssue.invalid("wallThicknessM", "The wall is 0 to 100 mm thick")); }
        if (!(safetyFactor >= 1 && safetyFactor <= 4)) { issues.add(DesignIssue.invalid("safetyFactor", "The safety factor is 1 to 4")); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Mass for a grain's outer diameter and overall length, kg. */
    public double massFor(double grainDiameterM, double grainLengthM) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double d = grainDiameterM + wallThicknessM;
        return (1.1 * Math.PI * d * grainLengthM + 2 * Math.PI / 4 * d * d) * wallThicknessM * material.densityKgPerM3();
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("material", material.toJson(), "wallThicknessM", wallThicknessM, "safetyFactor", safetyFactor, "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static RocketCasingDesign read(String name, Fields f) {
        return new RocketCasingDesign(name, EngineComponent.Rules.material(f, "material", EngineMaterial.ALUMINIUM_2024), f.number("wallThicknessM", 0.0025),
                f.number("safetyFactor", 1.5), EngineComponent.Rules.massOr(f));
    }
}
