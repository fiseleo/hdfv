package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.Turbomachinery;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Free power turbine (動力渦輪) of a turboprop or turboshaft: stages, polytropic efficiency (at its best speed), tip
 * diameter, design speed, the exhaust's total pressure over ambient at design (how far it expands the gas: a little
 * above 1 in a turboshaft, more in a turboprop that keeps some jet thrust), material (uncooled: its service
 * temperature must take the gas between the turbines). Off its design speed its power falls (FreeTurbine; an impulse
 * stage's fall below it, flatter above): none with the shaft held, where it gives twice the torque. Stage loading and disc stress as for
 * turbines; mass 0.04 rho D^3 per stage, the rotor half of it (TurbineDesign's estimates).
 */
public record PowerTurbineDesign(String name, int stages, double polytropicEfficiency, double tipDiameterM, double designRpm, double exhaustPressureRatio,
                                 EngineMaterial material, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:power_turbine";

    public PowerTurbineDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    public double tipSpeed() { return Turbomachinery.tipSpeed(tipDiameterM, designRpm); }
    public double designOmega() { return designRpm * Math.PI / 30; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.count(issues, "stages", stages, 1, 6);
        if (!(polytropicEfficiency > 0.5 && polytropicEfficiency < 1)) { issues.add(DesignIssue.invalid("polytropicEfficiency", "Efficiency must be between 0.5 and 1")); }
        else { DesignIssue.range(issues, "polytropicEfficiency", polytropicEfficiency, 0.8, 0.93, "power turbines"); }
        EngineComponent.Rules.positive(issues, "tipDiameterM", tipDiameterM);
        EngineComponent.Rules.positive(issues, "designRpm", designRpm);
        if (!(exhaustPressureRatio > 1 && exhaustPressureRatio < 3)) { issues.add(DesignIssue.invalid("exhaustPressureRatio", "The exhaust must leave above ambient pressure, at most 3 times it")); }
        else { DesignIssue.range(issues, "exhaustPressureRatio", exhaustPressureRatio, 1.02, 1.3, "exhausts of turboshafts and turboprops"); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    public double discStress() { return Turbomachinery.discStress(material.densityKgPerM3(), tipSpeed()); }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0.04 * material.densityKgPerM3() * Math.pow(tipDiameterM, 3) * stages : massGivenKg; }
    public double rotorInertia() { return 0.5 * massKg() * 0.5 * Math.pow(tipDiameterM / 2, 2); }

    @Override public Map<String, Object> parameters() {
        return Fields.object("stages", stages, "polytropicEfficiency", polytropicEfficiency, "tipDiameterM", tipDiameterM, "designRpm", designRpm,
                "exhaustPressureRatio", exhaustPressureRatio, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static PowerTurbineDesign read(String name, Fields f) {
        return new PowerTurbineDesign(name, f.integer("stages", 2), f.number("polytropicEfficiency", 0.87), f.number("tipDiameterM"), f.number("designRpm"),
                f.number("exhaustPressureRatio", 1.05), EngineComponent.Rules.material(f, "material", EngineMaterial.INCONEL_713C), EngineComponent.Rules.massOr(f));
    }
}
