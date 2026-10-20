package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A helicopter rotor's blades (旋翼槳葉): count, radius, root cutout (fraction of the radius without aerofoil), chord at
 * the cutout and the tip, linear twist from root to tip, thickness ratio at the root and the tip, material and how
 * much of each section's envelope is material (a spar and skin around a light core: 0.3 to 0.5). Mass from the
 * sections, 0.685 t c^2 each (a NACA four-digit section's area), from the cutout to the tip, or given.
 */
public record RotorBladeDesign(String name, int blades, double radiusM, double rootCutout, double rootChordM, double tipChordM, double twistDeg,
                               double rootThickness, double tipThickness, EngineMaterial material, double fill, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:rotor_blades";

    public RotorBladeDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    public double chordAt(double x) { return rootChordM + (tipChordM - rootChordM) * (x - rootCutout) / (1 - rootCutout); }
    public double thicknessAt(double x) { return rootThickness + (tipThickness - rootThickness) * (x - rootCutout) / (1 - rootCutout); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.count(issues, "blades", blades, 2, 8);
        EngineComponent.Rules.positive(issues, "radiusM", radiusM);
        if (!(rootCutout >= 0.05 && rootCutout < 0.5)) { issues.add(DesignIssue.invalid("rootCutout", "The root cutout must be 5 to 50 % of the radius")); }
        EngineComponent.Rules.positive(issues, "rootChordM", rootChordM);
        EngineComponent.Rules.positive(issues, "tipChordM", tipChordM);
        if (!(Math.abs(twistDeg) <= 30)) { issues.add(DesignIssue.invalid("twistDeg", "Twist must be within 30 degrees")); }
        else { DesignIssue.range(issues, "twistDeg", twistDeg, -20, 0, "helicopter blades (washed out 8 to 18 degrees)"); }
        if (!(rootThickness > 0.04 && rootThickness < 0.4)) { issues.add(DesignIssue.invalid("rootThickness", "Thickness must be 4 to 40 % of the chord")); }
        if (!(tipThickness > 0.04 && tipThickness < 0.4)) { issues.add(DesignIssue.invalid("tipThickness", "Thickness must be 4 to 40 % of the chord")); }
        if (!(fill > 0.05 && fill <= 1)) { issues.add(DesignIssue.invalid("fill", "The material's share of the section must be 5 to 100 %")); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Integral of section area times r^power from the cutout to the tip, one blade, m^(3 + power). */
    double sectionIntegral(int power) {
        int n = 40;
        double dx = (1 - rootCutout) / n, sum = 0;
        for (int i = 0; i < n; i++) {
            double x = rootCutout + (i + 0.5) * dx, c = chordAt(x);
            sum += 0.685 * thicknessAt(x) * c * c * Math.pow(x * radiusM, power) * dx * radiusM;
        }
        return sum;
    }

    /** One blade's mass, kg. */
    public double bladeMassKg() { return Double.isNaN(massGivenKg) ? sectionIntegral(0) * material.densityKgPerM3() * fill : massGivenKg / blades; }
    /** All blades, kg. */
    @Override public double massKg() { return blades * bladeMassKg(); }

    /** Centrifugal stress at the blade's root section at a speed (rpm), Pa. */
    public double rootStress(double rpm) {
        double omega = rpm * Math.PI / 30, rho = bladeMassKg() / Math.max(sectionIntegral(0), 1e-12);
        double root = 0.685 * rootThickness * rootChordM * rootChordM * fill;
        return rho * omega * omega * sectionIntegral(1) / root;
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("blades", blades, "radiusM", radiusM, "rootCutout", rootCutout, "rootChordM", rootChordM, "tipChordM", tipChordM,
                "twistDeg", twistDeg, "rootThickness", rootThickness, "tipThickness", tipThickness, "material", material.toJson(), "fill", fill,
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static RotorBladeDesign read(String name, Fields f) {
        return new RotorBladeDesign(name, f.integer("blades", 4), f.number("radiusM"), f.number("rootCutout", 0.2), f.number("rootChordM"), f.number("tipChordM"),
                f.number("twistDeg", -12), f.number("rootThickness", 0.12), f.number("tipThickness", 0.09),
                EngineComponent.Rules.material(f, "material", EngineMaterial.ALUMINIUM_2024), f.number("fill", 0.35), EngineComponent.Rules.massOr(f));
    }
}
