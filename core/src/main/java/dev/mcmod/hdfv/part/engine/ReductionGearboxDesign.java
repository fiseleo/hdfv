package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.Turbomachinery;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reduction gearbox (減速齒輪箱) between a power turbine and the output shaft: planetary (in line) or offset spur
 * stages, overall ratio (turbine speed over output speed; 1 with no meshes is a direct drive), meshes, rated output
 * torque, gear material. Each mesh runs at 98.5 % (Turbomachinery.gearboxEfficiency). One planetary stage reduces
 * by up to about 8, an offset spur stage by about 5; beyond 12 a mesh is not practical. The engine's design torque at
 * the output must stay within the rating. Mass 0.068 T^0.8 (0.6 + 0.2 meshes) kg for a rated torque T in N m, scaled
 * by the gears' density over steel's (estimate, about 32 kg for a PT6A's two-stage planetary box of 2,200 N m).
 */
public record ReductionGearboxDesign(String name, Kind kind, double ratio, int meshes, double ratedTorqueNm, EngineMaterial material, double massGivenKg)
        implements EngineComponent {
    public static final String TYPE = "hdfv:reduction_gearbox";

    public enum Kind { PLANETARY, OFFSET }

    public ReductionGearboxDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    public boolean direct() { return meshes == 0; }
    public double efficiency() { return Turbomachinery.gearboxEfficiency(meshes); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.count(issues, "meshes", meshes, 0, 4);
        if (!(ratio >= 1 && ratio <= 100)) { issues.add(DesignIssue.invalid("ratio", "The ratio must be 1 to 100")); }
        else if (meshes == 0 && ratio != 1) { issues.add(DesignIssue.invalid("meshes", "A reduction needs gear meshes")); }
        else if (meshes > 0) {
            double perMesh = Math.pow(ratio, 1.0 / meshes);
            if (perMesh > 12) { issues.add(DesignIssue.invalid("meshes", "Too few meshes for this ratio: " + Math.round(perMesh * 10) / 10.0 + " per mesh")); }
            else { DesignIssue.range(issues, "ratioPerMesh", perMesh, 1, kind == Kind.PLANETARY ? 8 : 5, "planetary stages to about 8, spur stages to about 5"); }
        }
        EngineComponent.Rules.positive(issues, "ratedTorqueNm", ratedTorqueNm);
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        return direct() ? 0 : 0.068 * Math.pow(ratedTorqueNm, 0.8) * (0.6 + 0.2 * meshes) * material.densityKgPerM3() / 7850;
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("kind", kind.name().toLowerCase(), "ratio", ratio, "meshes", meshes, "ratedTorqueNm", ratedTorqueNm, "material", material.toJson(),
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static ReductionGearboxDesign read(String name, Fields f) {
        return new ReductionGearboxDesign(name, EngineComponent.Rules.choice(f, "kind", Kind.class, Kind.PLANETARY), f.number("ratio", 1), f.integer("meshes", 0),
                f.number("ratedTorqueNm"), EngineComponent.Rules.material(f, "material", EngineMaterial.STEEL_4340), EngineComponent.Rules.massOr(f));
    }
}
