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
 * An air propeller's blades (槳葉): count, diameter, hub ratio, chord over diameter at the hub and the tip, geometric
 * pitch over diameter at 0.75 R (at blade setting 0), extra twist from hub to tip, thickness ratio at the hub and the
 * tip, material. Mass: solid blades (aluminium forgings, wood, or composite shells filled: 0.8 of the section's
 * envelope, an estimate), section area 0.685 t c (a NACA four-digit section's).
 */
public record PropellerBladeDesign(String name, int blades, double diameterM, double hubRatio, double rootChordRatio, double tipChordRatio,
                                   double pitchRatio, double twistDeg, double rootThickness, double tipThickness, EngineMaterial material,
                                   double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:propeller_blades";
    /** Share of a section's envelope that is material (an estimate). */
    public static final double FILL = 0.8;

    public PropellerBladeDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    public double radiusM() { return diameterM / 2; }
    public double chordRatio(double x) { return rootChordRatio + (tipChordRatio - rootChordRatio) * (x - hubRatio) / (1 - hubRatio); }
    public double thicknessAt(double x) { return rootThickness + (tipThickness - rootThickness) * (x - hubRatio) / (1 - hubRatio); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.count(issues, "blades", blades, 2, 8);
        EngineComponent.Rules.positive(issues, "diameterM", diameterM);
        if (!(hubRatio > 0.05 && hubRatio < 0.5)) { issues.add(DesignIssue.invalid("hubRatio", "The hub must be 5 to 50 % of the diameter")); }
        if (!(rootChordRatio > 0 && rootChordRatio < 0.5)) { issues.add(DesignIssue.invalid("rootChordRatio", "Chord must be up to half the diameter")); }
        if (!(tipChordRatio > 0 && tipChordRatio < 0.5)) { issues.add(DesignIssue.invalid("tipChordRatio", "Chord must be up to half the diameter")); }
        if (!(pitchRatio > 0.1 && pitchRatio < 4)) { issues.add(DesignIssue.invalid("pitchRatio", "Pitch ratio must be 0.1 to 4")); }
        else { DesignIssue.range(issues, "pitchRatio", pitchRatio, 0.5, 2.4, "NACA Report 640's blade angles (15 to 45 degrees at 0.75 R)"); }
        if (!(Math.abs(twistDeg) <= 30)) { issues.add(DesignIssue.invalid("twistDeg", "Extra twist must be within 30 degrees")); }
        if (!(rootThickness > 0.02 && rootThickness < 0.5)) { issues.add(DesignIssue.invalid("rootThickness", "Thickness must be 2 to 50 % of the chord")); }
        if (!(tipThickness > 0.02 && tipThickness < 0.5)) { issues.add(DesignIssue.invalid("tipThickness", "Thickness must be 2 to 50 % of the chord")); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** All blades' mass, kg: given, or the solid sections from hub to tip. */
    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        return blades * sectionIntegral(0) * material.densityKgPerM3() * FILL;
    }

    /** Integral of the section area times r^power along one blade, m^(3 + power). */
    double sectionIntegral(int power) {
        int n = 40;
        double r = radiusM(), sum = 0, dx = (1 - hubRatio) / n;
        for (int i = 0; i < n; i++) {
            double x = hubRatio + (i + 0.5) * dx, c = chordRatio(x) * diameterM;
            sum += 0.685 * thicknessAt(x) * c * c * Math.pow(x * r, power) * dx * r;
        }
        return sum;
    }

    /** Moment of inertia of the blades about the shaft, kg m^2. */
    public double spinInertia() {
        double scale = massKg() / Math.max(sectionIntegral(0), 1e-12);
        return scale * sectionIntegral(2);
    }

    /**
     * Centrifugal tensile stress at the blade root at a speed (rpm), Pa: the blade outboard of the hub pulling on the
     * root section.
     */
    public double rootStress(double rpm) {
        double omega = rpm * Math.PI / 30, density = material.densityKgPerM3() * FILL;
        double root = 0.685 * rootThickness * Math.pow(rootChordRatio * diameterM, 2);
        return density * omega * omega * sectionIntegral(1) / root;
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("blades", blades, "diameterM", diameterM, "hubRatio", hubRatio, "rootChordRatio", rootChordRatio, "tipChordRatio", tipChordRatio,
                "pitchRatio", pitchRatio, "twistDeg", twistDeg, "rootThickness", rootThickness, "tipThickness", tipThickness, "material", material.toJson(),
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static PropellerBladeDesign read(String name, Fields f) {
        return new PropellerBladeDesign(name, f.integer("blades", 3), f.number("diameterM"), f.number("hubRatio", 0.15), f.number("rootChordRatio", 0.08),
                f.number("tipChordRatio", 0.05), f.number("pitchRatio", 1.1), f.number("twistDeg", 0), f.number("rootThickness", 0.18), f.number("tipThickness", 0.06),
                EngineComponent.Rules.material(f, "material", EngineMaterial.ALUMINIUM_7075), EngineComponent.Rules.massOr(f));
    }
}
