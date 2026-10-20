package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A solid rocket's nozzle (噴嘴, 0.10): the throat's diameter, the expansion ratio (exit over throat area), the
 * divergence half-angle (its loss, (1 + cos a)/2), the characteristic-velocity efficiency (combustion: 0.9-0.98) and the
 * material. Its throat sees the gas at about 0.9 of the flame temperature; above the material's service temperature it
 * erodes (fine for a short burn: a warning). Mass: a converging and a diverging cone at a wall of a fifth of the throat's
 * diameter (at least 3 mm), the converging section as long as the throat is wide (an estimate).
 */
public record RocketNozzleDesign(String name, double throatDiameterM, double expansionRatio, double divergenceHalfAngleDeg, double characteristicVelocityEfficiency,
                                 EngineMaterial material, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:rocket_nozzle";

    public RocketNozzleDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "throatDiameterM", throatDiameterM);
        if (!(expansionRatio >= 1 && expansionRatio <= 200)) { issues.add(DesignIssue.invalid("expansionRatio", "The expansion ratio is 1 to 200")); }
        else { DesignIssue.range(issues, "expansionRatio", expansionRatio, 4, 16, "boosters at low altitude"); }
        if (!(divergenceHalfAngleDeg >= 0 && divergenceHalfAngleDeg < 45)) { issues.add(DesignIssue.invalid("divergenceHalfAngleDeg", "The divergence is 0 to 45 degrees")); }
        if (!(characteristicVelocityEfficiency > 0 && characteristicVelocityEfficiency <= 1)) { issues.add(DesignIssue.invalid("characteristicVelocityEfficiency", "Efficiency must be in (0, 1]")); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double dt = throatDiameterM, de = dt * Math.sqrt(expansionRatio), wall = Math.max(0.003, dt / 5);
        double divergent = (de - dt) / 2 / Math.tan(Math.toRadians(Math.max(divergenceHalfAngleDeg, 5)));
        double area = Math.PI * 0.5 * (2 * dt + dt) * dt + Math.PI * 0.5 * (dt + de) * Math.hypot(divergent, (de - dt) / 2);
        return area * wall * material.densityKgPerM3();
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("throatDiameterM", throatDiameterM, "expansionRatio", expansionRatio, "divergenceHalfAngleDeg", divergenceHalfAngleDeg,
                "characteristicVelocityEfficiency", characteristicVelocityEfficiency, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static RocketNozzleDesign read(String name, Fields f) {
        return new RocketNozzleDesign(name, f.number("throatDiameterM", 0.02), f.number("expansionRatio", 8), f.number("divergenceHalfAngleDeg", 12),
                f.number("characteristicVelocityEfficiency", 0.95), EngineComponent.Rules.material(f, "material", EngineMaterial.GRAPHITE), EngineComponent.Rules.massOr(f));
    }
}
