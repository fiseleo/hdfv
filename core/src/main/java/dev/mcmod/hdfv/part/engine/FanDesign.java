package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.api.engine.Turbomachinery;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fan (風扇) of a turbofan with its booster stages and bypass duct: bypass ratio and fan pressure ratio at design
 * (sea-level static; the same pressure ratio for bypass and core air), polytropic efficiency (fan and booster), tip
 * diameter, hub-to-tip ratio, design speed (the low-pressure spool's), blades, booster stages and their pressure ratio
 * (no stages: none), the bypass duct's and nozzle's total-pressure loss, material.
 * <p>
 * What the parameters must satisfy:
 * <ul>
 * <li>Work at the tip: a fan stage's work over its tip speed squared runs at about 0.25-0.45 (the CFM56's about 0.3;
 *   Dixon and Hall, Fluid Mechanics and Thermodynamics of Turbomachinery); beyond 0.8 no single stage does it.</li>
 * <li>Relative Mach number at the tip (tip speed and the face's axial inflow): transonic fans run at 1.3-1.6; beyond 2
 *   the shock losses are not a fan's any more.</li>
 * <li>The face (annulus of the hub-to-tip ratio) chokes at 241 kg/(s m^2) of corrected flow; fans are laid out for
 *   about Mach 0.6 there. Disc stress as for compressors.</li>
 * <li>Booster stages sit behind the fan's root at 0.6 of its tip diameter and the fan's speed; their stage loading as
 *   an axial compressor's.</li>
 * </ul>
 * Estimated mass: 0.03 rho D^3 for the fan stage, its case and the bypass duct, 0.03 rho (0.6 D)^3 per booster stage
 * (rough estimates; with TurbofanDesign's accessories and structure, the CFM56-7B-class sample comes within 15 % of its
 * 2,366 kg). The rotor: a third of
 * the fan's mass at a radius of gyration of 0.63 of the tip radius (the blades' mass is outboard), a fifth of each
 * booster stage's at 0.7.
 */
public record FanDesign(String name, double bypassRatio, double pressureRatio, double polytropicEfficiency, double tipDiameterM, double hubTipRatio,
                        double designRpm, int blades, int boosterStages, double boosterPressureRatio, double bypassPressureLoss, EngineMaterial material,
                        double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:fan";
    /** Booster tip diameter over the fan's. */
    public static final double BOOSTER_DIAMETER = 0.6;

    public FanDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    public double tipSpeed() { return Turbomachinery.tipSpeed(tipDiameterM, designRpm); }
    public double boosterTipDiameter() { return BOOSTER_DIAMETER * tipDiameterM; }
    /** The booster's pressure ratio, 1 without stages. */
    public double boosterRatio() { return boosterStages > 0 ? boosterPressureRatio : 1; }
    /** Fan work per kilogram at design (sea-level static), J/kg. */
    public double designWork() { return Turbomachinery.compressorWork(pressureRatio, polytropicEfficiency, Turbojet.T_REF); }
    /** Fan exit temperature at design (sea-level static), K. */
    public double exitTemperature() { return Turbomachinery.compressorExitTemperature(pressureRatio, polytropicEfficiency, Turbojet.T_REF); }
    /** Booster work per kilogram at design, J/kg. */
    public double boosterWork() { return Turbomachinery.compressorWork(boosterRatio(), polytropicEfficiency, exitTemperature()); }
    /** Core air total temperature behind the booster at design, K. */
    public double boosterExitTemperature() { return Turbomachinery.compressorExitTemperature(boosterRatio(), polytropicEfficiency, exitTemperature()); }
    public double faceArea() { return Turbomachinery.faceArea(false, tipDiameterM, hubTipRatio); }
    /** Fan stage axial length, m (a quarter of its diameter), and the booster's (0.06 of its diameter per stage). */
    public double lengthM() { return 0.25 * tipDiameterM + 0.06 * boosterTipDiameter() * boosterStages; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(bypassRatio >= 0 && bypassRatio <= 20)) { issues.add(DesignIssue.invalid("bypassRatio", "Bypass ratio must be 0 to 20")); }
        else { DesignIssue.range(issues, "bypassRatio", bypassRatio, 0.3, 12, "turbofans from low-bypass military fans to modern airliner fans"); }
        if (!(pressureRatio > 1 && pressureRatio < 4)) { issues.add(DesignIssue.invalid("pressureRatio", "A fan stage raises the pressure by 1 to 4 times")); }
        else { DesignIssue.range(issues, "pressureRatio", pressureRatio, 1.3, 2.0, "single-stage fans"); }
        if (!(polytropicEfficiency > 0.5 && polytropicEfficiency < 1)) { issues.add(DesignIssue.invalid("polytropicEfficiency", "Efficiency must be between 0.5 and 1")); }
        else { DesignIssue.range(issues, "polytropicEfficiency", polytropicEfficiency, 0.82, 0.93, "fans"); }
        EngineComponent.Rules.positive(issues, "tipDiameterM", tipDiameterM);
        EngineComponent.Rules.positive(issues, "designRpm", designRpm);
        if (!(hubTipRatio >= 0.15 && hubTipRatio < 0.8)) { issues.add(DesignIssue.invalid("hubTipRatio", "Hub-to-tip ratio must be in [0.15, 0.8)")); }
        EngineComponent.Rules.count(issues, "blades", blades, 8, 60);
        EngineComponent.Rules.count(issues, "boosterStages", boosterStages, 0, 6);
        if (boosterStages > 0 && !(boosterPressureRatio > 1)) { issues.add(DesignIssue.invalid("boosterPressureRatio", "Booster stages must raise the pressure")); }
        if (!(bypassPressureLoss >= 0 && bypassPressureLoss < 0.3)) { issues.add(DesignIssue.invalid("bypassPressureLoss", "Bypass pressure loss must be in [0, 0.3)")); }
        else { DesignIssue.range(issues, "bypassPressureLoss", bypassPressureLoss, 0.01, 0.05, "bypass ducts and nozzles"); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        double u = tipSpeed(), loading = designWork() / (u * u);
        if (loading > 0.8) { issues.add(DesignIssue.invalid("designRpm", "One fan stage cannot do this work at this tip speed: loading " + loading)); }
        else { DesignIssue.range(issues, "fanLoading", loading, 0.2, 0.5, "fan stage loadings at the tip (work / tip speed^2)"); }
        if (boosterStages > 0) {
            double b = Turbomachinery.stageLoading(boosterWork(), boosterStages, Turbomachinery.tipSpeed(boosterTipDiameter(), designRpm));
            if (b > 1) { issues.add(DesignIssue.invalid("boosterStages", "Too few booster stages for their pressure ratio at the fan's speed: stage loading " + b)); }
            else { DesignIssue.range(issues, "boosterLoading", b, 0, 0.45, "axial stage loadings (work / tip speed^2)"); }
        }
        double stress = Turbomachinery.discStress(material.densityKgPerM3(), u);
        if (stress > material.yieldPa()) { issues.add(DesignIssue.invalid("designRpm", "The fan would yield at this tip speed")); }
        else { DesignIssue.range(issues, "discStressOverYield", stress / material.yieldPa(), 0, 2.0 / 3, "rotors kept under two thirds of yield"); }
        return issues;
    }

    /** The checks that need the engine's total airflow (kg/s, sea-level static): the face's flux and the relative tip Mach number. */
    public void checkFlow(List<DesignIssue> issues, double totalAirflowKgPerS) {
        double flux = totalAirflowKgPerS / faceArea();
        if (flux > Turbomachinery.CHOKED_FLUX) { issues.add(DesignIssue.invalid("fan.tipDiameterM", "The fan face is too small: it chokes below the design airflow")); return; }
        DesignIssue.range(issues, "fanFaceFlux", flux, 0, Turbomachinery.MACH_07_FLUX, "fan faces laid out for Mach 0.5-0.7");
        double mach = Turbomachinery.relativeTipMach(tipSpeed(), Turbomachinery.faceMach(totalAirflowKgPerS, faceArea()), Turbojet.T_REF);
        if (mach > 2) { issues.add(DesignIssue.invalid("fan.designRpm", "The fan's tips would meet the air at Mach " + Math.round(mach * 100) / 100.0)); }
        else { DesignIssue.range(issues, "fanTipMach", mach, 0, 1.7, "transonic fan tips, Mach 1.3-1.6"); }
    }

    private double fanMass() { return 0.03 * material.densityKgPerM3() * Math.pow(tipDiameterM, 3); }
    private double boosterStageMass() { return 0.03 * material.densityKgPerM3() * Math.pow(boosterTipDiameter(), 3); }

    @Override public double massKg() { return Double.isNaN(massGivenKg) ? fanMass() + boosterStages * boosterStageMass() : massGivenKg; }

    /** Moment of inertia of the fan and booster rotors, kg m^2 (estimate; a given mass scales it). */
    public double rotorInertia() {
        double r = tipDiameterM / 2, rb = boosterTipDiameter() / 2, estimate = fanMass() + boosterStages * boosterStageMass();
        double inertia = fanMass() / 3 * 0.4 * r * r + boosterStages * 0.2 * boosterStageMass() * 0.5 * rb * rb;
        return inertia * massKg() / estimate;
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("bypassRatio", bypassRatio, "pressureRatio", pressureRatio, "polytropicEfficiency", polytropicEfficiency, "tipDiameterM", tipDiameterM,
                "hubTipRatio", hubTipRatio, "designRpm", designRpm, "blades", blades, "boosterStages", boosterStages, "boosterPressureRatio", boosterPressureRatio,
                "bypassPressureLoss", bypassPressureLoss, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static FanDesign read(String name, Fields f) {
        return new FanDesign(name, f.number("bypassRatio"), f.number("pressureRatio"), f.number("polytropicEfficiency", 0.89), f.number("tipDiameterM"),
                f.number("hubTipRatio", 0.3), f.number("designRpm"), f.integer("blades", 24), f.integer("boosterStages", 0), f.number("boosterPressureRatio", 1),
                f.number("bypassPressureLoss", 0.025), EngineComponent.Rules.material(f, "material", EngineMaterial.TITANIUM_6AL4V), EngineComponent.Rules.massOr(f));
    }
}
