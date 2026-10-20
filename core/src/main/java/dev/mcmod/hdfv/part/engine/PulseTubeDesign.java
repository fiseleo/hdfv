package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A pulsejet's tube (脈衝噴射管, 0.10): the combustion chamber's diameter and length, the taper to the tailpipe, the
 * tailpipe's diameter and length, the wall's thickness and material. The tube glows: its hottest wall (from the cycle)
 * must not pass the material's service temperature (checked by the engine). Mass: the walls of chamber, taper and
 * tailpipe at their thickness, plus 10 % for flanges and mounts (an estimate).
 */
public record PulseTubeDesign(String name, double chamberDiameterM, double chamberLengthM, double taperLengthM, double tailDiameterM, double tailLengthM,
                              double wallThicknessM, EngineMaterial material, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:pulse_tube";

    public PulseTubeDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "chamberDiameterM", chamberDiameterM);
        EngineComponent.Rules.positive(issues, "chamberLengthM", chamberLengthM);
        EngineComponent.Rules.nonnegative(issues, "taperLengthM", taperLengthM);
        EngineComponent.Rules.positive(issues, "tailDiameterM", tailDiameterM);
        EngineComponent.Rules.positive(issues, "tailLengthM", tailLengthM);
        if (tailDiameterM > chamberDiameterM) { issues.add(DesignIssue.invalid("tailDiameterM", "The tailpipe is no wider than the chamber")); }
        if (chamberDiameterM > 0 && tailLengthM > 0) {
            DesignIssue.range(issues, "tailLengthOverChamberDiameter", tailLengthM / chamberDiameterM, 2, 8, "pulsejets that run (Argus 3.0, hobby engines 3-6)");
        }
        if (!(wallThicknessM > 0 && wallThicknessM < 0.05)) { issues.add(DesignIssue.invalid("wallThicknessM", "The wall is 0 to 50 mm thick")); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double taper = Math.PI * 0.5 * (chamberDiameterM + tailDiameterM) * taperLengthM;
        double area = Math.PI * chamberDiameterM * chamberLengthM + taper + Math.PI * tailDiameterM * tailLengthM + Math.PI / 4 * chamberDiameterM * chamberDiameterM;
        return 1.1 * area * wallThicknessM * material.densityKgPerM3();
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("chamberDiameterM", chamberDiameterM, "chamberLengthM", chamberLengthM, "taperLengthM", taperLengthM, "tailDiameterM", tailDiameterM,
                "tailLengthM", tailLengthM, "wallThicknessM", wallThicknessM, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static PulseTubeDesign read(String name, Fields f) {
        return new PulseTubeDesign(name, f.number("chamberDiameterM", 0.58), f.number("chamberLengthM", 0.9), f.number("taperLengthM", 1.0),
                f.number("tailDiameterM", 0.38), f.number("tailLengthM", 1.75), f.number("wallThicknessM", 0.0012),
                EngineComponent.Rules.material(f, "material", EngineMaterial.STEEL_1045), EngineComponent.Rules.massOr(f));
    }
}
