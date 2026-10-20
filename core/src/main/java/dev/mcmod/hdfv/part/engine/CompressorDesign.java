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
 * Compressor (壓縮機) of a jet engine: axial, centrifugal, or axial stages ahead of one centrifugal last stage (as in
 * small turboprops and turboshafts), stages (for the last kind, the axial ones), design pressure ratio and airflow
 * (sea-level static; behind a fan, the core's), polytropic efficiency, surge margin at the design point, tip diameter
 * (for the last kind, the impeller's), hub-to-tip ratio at the face, the axial stages' tip diameter over the impeller's
 * (the last kind only; about the eye's 0.6, more for a large flow at a high speed), design speed, impeller blades
 * (centrifugal), material. Behind a fan or booster the engine checks it at its own inlet
 * state (check(inletK, inletPa)): the work for a pressure ratio grows with the inlet temperature, the face passes
 * corrected flow.
 * <p>
 * What the parameters must satisfy (from the turbomachinery, not balance):
 * <ul>
 * <li>Work per stage: an axial stage's work over the square of its tip speed (stage loading) is about 0.2-0.45 in
 *   practice (Dixon and Hall, Fluid Mechanics and Thermodynamics of Turbomachinery); beyond 1 no blading turns the
 *   flow that far. A centrifugal impeller with radial blades and no inlet swirl gives at most sigma U^2, with Stanitz's
 *   slip factor sigma = 1 - 0.63 pi / Z.</li>
 * <li>Flow through the face (annulus, or a centrifugal impeller's eye, taken as 0.6 of its tip diameter): it chokes at
 *   Mach 1, about 241 kg/(s m^2) of corrected flow; faces are designed for Mach 0.5-0.7.</li>
 * <li>Axial stages ahead of an impeller: the impeller takes up to three quarters of its slip-limited work, the axial
 *   stages the rest, at their own (smaller) tip speed.</li>
 * <li>A rotating solid disc carries (3 + nu) / 8 rho U^2 at its centre (Roark, rotating discs, nu = 0.3); blades add
 *   rim load, so up to two thirds of the yield strength is treated as normal.</li>
 * </ul>
 * Estimated mass: 0.03 rho D^3 per axial stage, 0.08 rho D^3 per centrifugal stage (rotor, stators and casing; rough
 * estimates, set with the turbine's and the accessories' so that a J85-GE-17A-like and a JetCat P80-like engine weigh
 * within 25 % of their 181 kg and 1.32 kg); the rotor (discs, drum and blades) is taken as 40 % of it, with the
 * inertia of a disc of the tip radius.
 */
public record CompressorDesign(String name, Kind kind, int stages, double pressureRatio, double airflowKgPerS, double polytropicEfficiency,
                               double surgeMargin, double tipDiameterM, double hubTipRatio, double axialTipRatio, double designRpm, int blades,
                               EngineMaterial material, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:compressor";

    public enum Kind { AXIAL, CENTRIFUGAL, AXIAL_CENTRIFUGAL }
    /** Share of its slip-limited work the last-stage impeller of an axial-centrifugal compressor is taken to do. */
    public static final double IMPELLER_SHARE = 0.75;

    public CompressorDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(material, "material"); }

    @Override public String type() { return TYPE; }

    public double tipSpeed() { return Turbomachinery.tipSpeed(tipDiameterM, designRpm); }
    /** Tip diameter of the axial stages, m: the compressor's, or ahead of an impeller the given share of its. */
    public double axialTipDiameter() { return kind == Kind.AXIAL_CENTRIFUGAL ? axialTipRatio * tipDiameterM : tipDiameterM; }
    /** Length, m: 0.06 tip diameters per axial stage, 0.4 per centrifugal stage. */
    public double lengthM() {
        return switch (kind) {
            case AXIAL -> 0.06 * tipDiameterM * stages;
            case CENTRIFUGAL -> 0.4 * tipDiameterM * stages;
            case AXIAL_CENTRIFUGAL -> 0.06 * axialTipDiameter() * stages + 0.4 * tipDiameterM;
        };
    }
    /** Axial stages (none in a centrifugal compressor). */
    public int axialStages() { return kind == Kind.CENTRIFUGAL ? 0 : stages; }
    /** Work per kilogram at the design point (sea-level static), J/kg. */
    public double designWork() { return designWork(Turbojet.T_REF); }
    /** Work per kilogram at the design point from an inlet temperature, J/kg. */
    public double designWork(double inletK) { return Turbomachinery.compressorWork(pressureRatio, polytropicEfficiency, inletK); }
    /** Flow area of the face, m^2 (the axial stages' annulus ahead of an impeller). */
    public double faceArea() {
        return kind == Kind.AXIAL_CENTRIFUGAL ? Turbomachinery.faceArea(false, axialTipDiameter(), hubTipRatio)
                : Turbomachinery.faceArea(kind == Kind.CENTRIFUGAL, tipDiameterM, hubTipRatio);
    }
    public double slipFactor() { return Turbomachinery.slipFactor(blades); }
    public double discStress() { return Turbomachinery.discStress(material.densityKgPerM3(), tipSpeed()); }

    @Override public List<DesignIssue> check() { return check(Turbojet.T_REF, Turbojet.P_REF); }

    /** Checks at an inlet total temperature (K) and pressure (Pa) of the design point. */
    public List<DesignIssue> check(double inletK, double inletPa) {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.count(issues, "stages", stages, 1, kind == Kind.CENTRIFUGAL ? 3 : kind == Kind.AXIAL ? 30 : 10);
        if (!(pressureRatio > 1)) { issues.add(DesignIssue.invalid("pressureRatio", "A compressor must raise the pressure")); }
        EngineComponent.Rules.positive(issues, "airflowKgPerS", airflowKgPerS);
        if (!(polytropicEfficiency > 0.5 && polytropicEfficiency < 1)) { issues.add(DesignIssue.invalid("polytropicEfficiency", "Efficiency must be between 0.5 and 1")); }
        else { DesignIssue.range(issues, "polytropicEfficiency", polytropicEfficiency, 0.75, 0.92, "compressors from model engines to modern core compressors"); }
        if (!(surgeMargin > 0 && surgeMargin < 1)) { issues.add(DesignIssue.invalid("surgeMargin", "Surge margin must be between 0 and 1")); }
        else { DesignIssue.range(issues, "surgeMargin", surgeMargin, 0.1, 0.3, "usual design surge margins"); }
        EngineComponent.Rules.positive(issues, "tipDiameterM", tipDiameterM);
        EngineComponent.Rules.positive(issues, "designRpm", designRpm);
        if (!(hubTipRatio >= 0 && hubTipRatio < 0.9)) { issues.add(DesignIssue.invalid("hubTipRatio", "Hub-to-tip ratio must be in [0, 0.9)")); }
        if (kind == Kind.AXIAL_CENTRIFUGAL && !(axialTipRatio > 0.3 && axialTipRatio <= 1)) {
            issues.add(DesignIssue.invalid("axialTipRatio", "The axial stages' diameter must be 0.3 to 1 of the impeller's"));
        }
        if (kind != Kind.AXIAL) { EngineComponent.Rules.count(issues, "blades", blades, 3, 60); }
        material.check(issues, "material");
        EngineComponent.Rules.mass(issues, massGivenKg);
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        double u = tipSpeed(), w = designWork(inletK);
        if (kind == Kind.AXIAL) {
            axialLoading(issues, Turbomachinery.stageLoading(w, stages, u));
        } else if (kind == Kind.CENTRIFUGAL) {
            double limit = Turbomachinery.slipLimitedWork(stages, blades, u);
            if (w > limit) { issues.add(DesignIssue.invalid("designRpm", "The impeller cannot do this work at this tip speed (slip-limited work " + limit + " J/kg)")); }
            else { DesignIssue.range(issues, "impellerWorkFraction", w / limit, 0, 0.9, "work against the slip-limited maximum"); }
        } else {
            double axial = w - Math.min(w, IMPELLER_SHARE * Turbomachinery.slipLimitedWork(1, blades, u));
            axialLoading(issues, Turbomachinery.stageLoading(axial, stages, Turbomachinery.tipSpeed(axialTipDiameter(), designRpm)));
        }
        double flux = airflowKgPerS * Math.sqrt(inletK / Turbojet.T_REF) * Turbojet.P_REF / inletPa / faceArea();
        if (flux > Turbomachinery.CHOKED_FLUX) { issues.add(DesignIssue.invalid("tipDiameterM", "The face is too small: it chokes below the design airflow")); }
        else { DesignIssue.range(issues, "faceFlux", flux, 0, Turbomachinery.MACH_07_FLUX, "faces designed for Mach 0.5-0.7"); }
        double stress = discStress();
        if (stress > material.yieldPa()) { issues.add(DesignIssue.invalid("designRpm", "The rotor would yield at this tip speed")); }
        else { DesignIssue.range(issues, "discStressOverYield", stress / material.yieldPa(), 0, 2.0 / 3, "rotors kept under two thirds of yield"); }
        return issues;
    }

    private static void axialLoading(List<DesignIssue> issues, double loading) {
        if (loading > 1) { issues.add(DesignIssue.invalid("stages", "Too few stages or too slow a rotor for this pressure ratio: stage loading " + loading)); }
        else { DesignIssue.range(issues, "stageLoading", loading, 0, 0.45, "axial stage loadings (work / tip speed^2), Dixon and Hall"); }
    }

    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double d3 = tipDiameterM * tipDiameterM * tipDiameterM, rho = material.densityKgPerM3();
        return switch (kind) {
            case AXIAL -> 0.03 * rho * d3 * stages;
            case CENTRIFUGAL -> 0.08 * rho * d3 * stages;
            case AXIAL_CENTRIFUGAL -> 0.03 * rho * Math.pow(axialTipDiameter(), 3) * stages + 0.08 * rho * d3;
        };
    }
    /** Moment of inertia of the rotor, kg m^2 (estimate). */
    public double rotorInertia() { return 0.4 * massKg() * 0.5 * Math.pow(tipDiameterM / 2, 2); }

    @Override public Map<String, Object> parameters() {
        return Fields.object("kind", kind.name().toLowerCase(), "stages", stages, "pressureRatio", pressureRatio, "airflowKgPerS", airflowKgPerS,
                "polytropicEfficiency", polytropicEfficiency, "surgeMargin", surgeMargin, "tipDiameterM", tipDiameterM, "hubTipRatio", hubTipRatio,
                "axialTipRatio", axialTipRatio, "designRpm", designRpm, "blades", blades, "material", material.toJson(), "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static CompressorDesign read(String name, Fields f) {
        return new CompressorDesign(name, EngineComponent.Rules.choice(f, "kind", Kind.class, Kind.AXIAL), f.integer("stages"), f.number("pressureRatio"),
                f.number("airflowKgPerS"), f.number("polytropicEfficiency", 0.87), f.number("surgeMargin", 0.2), f.number("tipDiameterM"),
                f.number("hubTipRatio", 0.45), f.number("axialTipRatio", Turbomachinery.EYE_RATIO), f.number("designRpm"), f.integer("blades", 16),
                EngineComponent.Rules.material(f, "material", EngineMaterial.TITANIUM_6AL4V), EngineComponent.Rules.massOr(f));
    }
}
