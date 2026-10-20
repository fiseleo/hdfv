package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A pulsejet's front (脈衝噴射的閥或進氣管, 0.10): a grid of reed valves (Argus), or for a valveless engine an open intake
 * pipe (Lockwood-Hiller), bent rearward or not.
 * <p>
 * Reeds: the grid's open area, the pressure difference that opens them fully, their natural frequency, and each reed's
 * free length, lift (its stop plate's gap), thickness and material. Their life (a rule after Basquin's law, b = -0.1):
 * N = 1e7 (0.6 S_f / S_a)^10 cycles, S_f the material's fatigue strength (0.6 of it: they work hot, at the chamber's
 * front), S_a the bending stress of a cantilever bent to its lift, 3 E t lift / (2 L^2), plus the impact stress of
 * slamming onto the seat, rho c v (c = sqrt(E / rho), v = pi f lift, the tip's speed at half its natural swing). The
 * Argus's reeds lasted about an hour (some 160 000 cycles); worn reeds leak, so past 80 % of their life the engine
 * loses thrust, and past 120 % it stops (PulsejetDesign). Mass: the grid's plate, 5 mm thick over twice the open
 * area, steel, plus the reeds (an estimate); for a valveless engine the intake pipe's wall.
 */
public record PulseValveDesign(String name, boolean valveless, double valveAreaM2, double openingPa, double reedFrequencyHz, double reedLengthM,
                               double reedLiftM, double reedThicknessM, EngineMaterial reedMaterial, double intakeDiameterM, double intakeLengthM,
                               boolean intakeFacesRear, double massGivenKg) implements EngineComponent, dev.mcmod.hdfv.design.RecordEditor.Shown {
    public static final String TYPE = "hdfv:pulse_valve";

    /** A valveless engine's page shows its intake pipe; a valved one's its reeds. */
    @Override public boolean shows(String parameter) {
        boolean intake = parameter.startsWith("intake");
        boolean reeds = parameter.startsWith("valve") && !parameter.equals("valveless") || parameter.startsWith("reed") || parameter.equals("openingPa");
        return valveless ? !reeds : !intake;
    }

    public PulseValveDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(reedMaterial, "reedMaterial"); }

    @Override public String type() { return TYPE; }

    /** The reeds' stress amplitude: bending to their lift plus the seat's impact, Pa. */
    public double reedStressPa() {
        EngineMaterial m = reedMaterial;
        double bending = 3 * m.modulusPa() * reedThicknessM * reedLiftM / (2 * reedLengthM * reedLengthM);
        double c = Math.sqrt(m.modulusPa() / m.densityKgPerM3()), v = Math.PI * reedFrequencyHz * reedLiftM;
        return bending + m.densityKgPerM3() * c * v;
    }

    /** The reeds' life in cycles (infinite for a valveless engine). */
    public double reedLifeCycles() {
        if (valveless) { return Double.POSITIVE_INFINITY; }
        return 1e7 * Math.pow(0.6 * reedMaterial.fatiguePa() / reedStressPa(), 10);
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (valveless) {
            EngineComponent.Rules.positive(issues, "intakeDiameterM", intakeDiameterM);
            EngineComponent.Rules.positive(issues, "intakeLengthM", intakeLengthM);
        } else {
            EngineComponent.Rules.positive(issues, "valveAreaM2", valveAreaM2);
            EngineComponent.Rules.positive(issues, "openingPa", openingPa);
            EngineComponent.Rules.positive(issues, "reedFrequencyHz", reedFrequencyHz);
            EngineComponent.Rules.positive(issues, "reedLengthM", reedLengthM);
            EngineComponent.Rules.positive(issues, "reedLiftM", reedLiftM);
            EngineComponent.Rules.positive(issues, "reedThicknessM", reedThicknessM);
            reedMaterial.check(issues, "reedMaterial");
            if (issues.isEmpty()) {
                DesignIssue.range(issues, "openingPa", openingPa, 1000, 20000, "pulsejet reed valves");
                DesignIssue.range(issues, "reedFrequencyHz", reedFrequencyHz, 100, 600, "reeds a few times faster than the engine");
                if (reedStressPa() > reedMaterial.yieldPa()) { issues.add(DesignIssue.invalid("reedLiftM", "The reeds would bend past yield at this lift")); }
            }
        }
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Mass for a chamber diameter, kg. */
    public double massFor(double chamberDiameterM) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        if (valveless) { return Math.PI * intakeDiameterM * intakeLengthM * Math.max(0.001, 0.003 * intakeDiameterM / 0.1) * EngineMaterial.STEEL_1045.densityKgPerM3(); }
        double plate = 0.005 * Math.max(2 * valveAreaM2, Math.PI / 4 * chamberDiameterM * chamberDiameterM) * EngineMaterial.STEEL_1045.densityKgPerM3();
        return plate + valveAreaM2 * reedThicknessM * reedMaterial.densityKgPerM3();
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("valveless", valveless, "valveAreaM2", valveAreaM2, "openingPa", openingPa, "reedFrequencyHz", reedFrequencyHz, "reedLengthM", reedLengthM,
                "reedLiftM", reedLiftM, "reedThicknessM", reedThicknessM, "reedMaterial", reedMaterial.toJson(), "intakeDiameterM", intakeDiameterM,
                "intakeLengthM", intakeLengthM, "intakeFacesRear", intakeFacesRear, "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static PulseValveDesign read(String name, Fields f) {
        return new PulseValveDesign(name, f.bool("valveless", false), f.number("valveAreaM2", 0.04), f.number("openingPa", 5000), f.number("reedFrequencyHz", 200),
                f.number("reedLengthM", 0.04), f.number("reedLiftM", 0.004), f.number("reedThicknessM", 0.0004),
                EngineComponent.Rules.material(f, "reedMaterial", EngineMaterial.SPRING_STEEL), f.number("intakeDiameterM", 0.08), f.number("intakeLengthM", 0.15),
                f.bool("intakeFacesRear", true), EngineComponent.Rules.massOr(f));
    }
}
