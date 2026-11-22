package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.hydraulic.Hydraulics;
import dev.mcmod.aerocore.api.hydraulic.Motor;
import dev.mcmod.aerocore.api.lifting.Reeving;
import dev.mcmod.aerocore.api.lifting.WireRope;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.HydraulicMotorPart;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A hydraulic motor (hdfv:hydraulic_motor, 0.26, 油壓馬達) on a circuit, with its planetary reducer of reductionRatio
 * (1: none) and a spring-applied holding brake: a crawler's travel drive (motor and final drive in one, its output on a
 * sprocket), a winch, a cutter's drive. Its valve asks the circuit for the flow to turn it at "command" x its top speed
 * (maxSpeedRpm, the motor's own); the line's relief caps its torque.
 *
 * <p>In the world it is AeroCore's HydraulicMotorPart whose rotor "output" (about the part's -z, so forward or paying out
 * is positive) is the reducer's output: the motor through an ideal reducer is a motor of ratio x its displacement, its
 * hydromechanical efficiency times the reducer's REDUCTION_EFFICIENCY (three planetary stages of about 0.98), its drag
 * ratio^2 x the motor's and its top speed over the ratio; the rotating inertia at the output ratio^2 x the motor's
 * rotating group. The holding brake (brakeNm) is given at the output.
 *
 * <p>With a winch drum (winch): a drum of drumDiameterM (at the rope's first layer: groove bottom) and drumWidthM
 * between its flanges spools a six-strand IWRC rope of ropeDiameterM, reeved over partsOfLine to the hook: the hook's
 * fall (the rope paid out over the parts) runs from ropeMinM to ropeMaxM, ropeStartM for a new machine
 * (HydraulicMotorPart.Drum at the rope's middle layer). The drum stores parts x the longest fall and Reeving.DEAD_WRAPS
 * turns; its layers (ropeLayers) nest LAYER_RISE rope diameters each. The drum's mass: its barrel (DRUM_WALL rope
 * diameters thick), two flanges FLANGE_FREEBOARD rope diameters over the top layer and FLANGE_WALL thick, and the rope
 * (WireRope.kgPerM). ISO 4308-1 asks a drum of 16 rope diameters (M4) or more: less is an extrapolation, as more than
 * MAX_LAYERS layers are.
 *
 * <p>Mass of the motor and reducer: MachineRules (Rexroth A2FM; reducers 4.5 g per N m of output torque). A 20 t
 * excavator's travel drive (a 110 cm^3 motor through about 55:1, 30 kN m at the sprocket at 350 bar) comes to about
 * 170 kg; travel devices of that class weigh 150 to 300 kg.
 */
public record HydraulicMotorDesign(String name, String circuit, HydraulicUnit motorKind, double displacementM3, double reliefPa, double maxSpeedRpm,
                                   double reductionRatio, double brakeNm, boolean winch, double drumDiameterM, double drumWidthM, double ropeDiameterM,
                                   int partsOfLine, double ropeMinM, double ropeMaxM, double ropeStartM) implements PartDesign, RecordEditor.Shown {
    public static final String TYPE = "hdfv:hydraulic_motor";
    /** A planetary reducer's efficiency (three stages of about 0.98; 1 without one). */
    public static final double REDUCTION_EFFICIENCY = 0.95;
    /** Winch drums: barrel and flange walls in rope diameters, the flanges' freeboard over the top layer, each layer's rise (rope diameters). */
    public static final double DRUM_WALL = 1.0, FLANGE_WALL = 1.5, FLANGE_FREEBOARD = 2, LAYER_RISE = 0.87;
    /** More layers than this is an extrapolation (spooling over many layers crushes the lower ones); more than MOST_LAYERS cannot be. */
    public static final int MAX_LAYERS = 8, MOST_LAYERS = 64;
    /** The rope: six strands, IWRC (WireRope). */
    public static final WireRope.Construction ROPE = WireRope.Construction.SIX_STRAND_IWRC;

    public HydraulicMotorDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(motorKind, "motorKind");
        circuit = circuit == null ? "main" : circuit;
    }

    @Override public String type() { return TYPE; }

    /** The drum's parameters mean something only on a winch. */
    @Override public boolean shows(String parameter) {
        return winch || !List.of("drumDiameterM", "drumWidthM", "ropeDiameterM", "partsOfLine", "ropeMinM", "ropeMaxM", "ropeStartM").contains(parameter);
    }

    public double displacementCm3() { return displacementM3 / Hydraulics.CM3_M3; }

    /**
     * The most speed catalogue motors of its displacement turn, rpm: axial piston 6300 (V/28)^-0.25 (a fit to the
     * A2FM's sizes 28 to 180: 6300, 5000, 4000 and 3550 rpm), gear 4000.
     */
    public double mostSpeedRpm() {
        return motorKind == HydraulicUnit.AXIAL_PISTON ? 6300 * Math.pow(Math.max(displacementCm3(), 1e-3) / 28, -0.25) : 4000;
    }

    double gearEfficiency() { return reductionRatio > 1 ? REDUCTION_EFFICIENCY : 1; }

    /** AeroCore's motor as its output sees it (the reducer folded in; see the class). */
    public Motor motor() {
        Motor m = motorKind.motor(displacementM3);
        double i = reductionRatio;
        return new Motor(i * displacementM3, m.volumetricEfficiency(), m.hydromechanicalEfficiency() * gearEfficiency(), m.ratedPressurePa(), m.dragNmPerRadPerS() * i * i);
    }

    /** The output's top speed, rad/s. */
    public double maxOutputRadPerS() { return maxSpeedRpm * Hydraulics.RPM_RAD_PER_S / reductionRatio; }

    /** The most torque at the output, N m (at the relief). */
    public double maxTorqueNm() { return motor().maxTorqueNm(reliefPa); }

    /** The flow it takes at its top speed, m^3/s. */
    public double maxFlowM3PerS() { return motorKind.motor(displacementM3).flowM3PerS(maxSpeedRpm * Hydraulics.RPM_RAD_PER_S, reliefPa); }

    // ---------------------------------------------------------------- winch drum

    /** The rope the drum stores: the parts of the longest fall and the dead wraps, m. */
    public double storedRopeM() { return partsOfLine * ropeMaxM + Reeving.DEAD_WRAPS * Math.PI * (drumDiameterM + ropeDiameterM); }

    /** Wraps a layer holds. */
    int wrapsPerLayer() { return (int) Math.max(1, Math.floor(drumWidthM / ropeDiameterM)); }

    /** The layers the stored rope takes on the drum (MOST_LAYERS + 1 when it does not fit). */
    public int ropeLayers() {
        double left = storedRopeM();
        for (int k = 0; k <= MOST_LAYERS; k++) {
            left -= wrapsPerLayer() * Math.PI * (drumDiameterM + ropeDiameterM + 2 * k * LAYER_RISE * ropeDiameterM);
            if (left <= 0) { return k + 1; }
        }
        return MOST_LAYERS + 1;
    }

    /** Radius to the rope's middle layer, m. */
    public double middleRadiusM() { return 0.5 * (drumDiameterM + ropeDiameterM + (Math.min(ropeLayers(), MOST_LAYERS) - 1) * LAYER_RISE * ropeDiameterM); }

    /** The flanges' diameter, m. */
    public double flangeDiameterM() { return drumDiameterM + 2 * (Math.min(ropeLayers(), MOST_LAYERS) * LAYER_RISE + FLANGE_FREEBOARD) * ropeDiameterM; }

    public double ropeKg() { return winch ? WireRope.kgPerM(ROPE, ropeDiameterM) * storedRopeM() : 0; }

    public double drumKg() {
        if (!winch) { return 0; }
        double rho = MachineRules.STEEL_KG_PER_M3, t = DRUM_WALL * ropeDiameterM, df = flangeDiameterM();
        double barrel = Math.PI * (drumDiameterM - t) * drumWidthM * t;
        double flanges = 2 * Math.PI / 4 * (df * df - drumDiameterM * drumDiameterM) * FLANGE_WALL * ropeDiameterM;
        return rho * (barrel + flanges);
    }

    /** The hook's speed at the top speed (middle layer), m/s. */
    public double hookSpeedMps() { return maxOutputRadPerS() * middleRadiusM() / partsOfLine; }

    /** The hook load the winch lifts at its relief (middle layer, over sheaves on rolling bearings), N. */
    public double hookLoadN() { return Reeving.hookLoadN(partsOfLine, maxTorqueNm() / middleRadiusM(), Reeving.SHEAVE_ROLLING, 0); }

    // ---------------------------------------------------------------- mass

    public double motorKg() { return MachineRules.unitKg(motorKind, false, displacementCm3()); }

    public double reducerKg() { return reductionRatio > 1 ? MachineRules.REDUCER_KG_PER_NM * maxTorqueNm() : 0; }

    public double massKg() { return motorKg() + reducerKg() + drumKg() + ropeKg(); }

    /** The rotating inertia at the output, kg m^2: the rotating group through the reducer, and the drum and rope as rings. */
    public double inertiaKgM2() {
        double j = MachineRules.unitInertiaKgM2(motorKind, displacementCm3()) * reductionRatio * reductionRatio;
        if (winch) {
            double r = 0.5 * drumDiameterM, rm = middleRadiusM();
            j += drumKg() * r * r + ropeKg() * rm * rm;
        }
        return j;
    }

    public MassProperties mass() {
        double s = 0.065 * Math.cbrt(Math.max(displacementCm3(), 1)) * (reductionRatio > 1 ? 1.8 : 1);
        MassProperties unit = Components.box(motorKg() + reducerKg(), Vec3d.ZERO, new Vec3d(s, s, s));
        if (!winch) { return unit; }
        double df = flangeDiameterM();
        MassProperties drum = Components.box(drumKg() + ropeKg(), Vec3d.ZERO, new Vec3d(0.8 * df, 0.8 * df, drumWidthM));
        return MassProperties.combine(List.of(unit.at(new Vec3d(0, 0, 0.5 * (drumWidthM + s))), drum));
    }

    // ---------------------------------------------------------------- checks

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (circuit.isBlank()) { issues.add(DesignIssue.invalid("circuit", "A motor runs on a circuit: give it a name")); }
        EngineComponent.Rules.positive(issues, "displacementM3", displacementM3);
        EngineComponent.Rules.positive(issues, "reliefPa", reliefPa);
        EngineComponent.Rules.positive(issues, "maxSpeedRpm", maxSpeedRpm);
        EngineComponent.Rules.positive(issues, "reductionRatio", reductionRatio);
        EngineComponent.Rules.nonnegative(issues, "brakeNm", brakeNm);
        if (winch) {
            EngineComponent.Rules.positive(issues, "drumDiameterM", drumDiameterM);
            EngineComponent.Rules.positive(issues, "drumWidthM", drumWidthM);
            EngineComponent.Rules.positive(issues, "ropeDiameterM", ropeDiameterM);
            EngineComponent.Rules.count(issues, "partsOfLine", partsOfLine, 1, 64);
            EngineComponent.Rules.nonnegative(issues, "ropeMinM", ropeMinM);
            EngineComponent.Rules.positive(issues, "ropeMaxM", ropeMaxM);
            EngineComponent.Rules.nonnegative(issues, "ropeStartM", ropeStartM);
        }
        if (!issues.isEmpty()) { return issues; }
        if (winch) {
            if (!(drumWidthM >= ropeDiameterM)) { issues.add(DesignIssue.invalid("drumWidthM", "The drum is narrower than its rope")); }
            if (!(ropeMinM <= ropeMaxM)) { issues.add(DesignIssue.invalid("ropeMinM", "The shortest fall is longer than the longest")); }
            else if (!(ropeStartM >= ropeMinM && ropeStartM <= ropeMaxM)) { issues.add(DesignIssue.invalid("ropeStartM", "The starting fall lies outside the shortest and longest")); }
            if (issues.isEmpty() && ropeLayers() > MOST_LAYERS) { issues.add(DesignIssue.invalid("ropeMaxM", "The drum cannot hold the rope")); }
            if (!issues.isEmpty()) { return issues; }
        }
        String sheets = "Bosch Rexroth A2FM / A6VM and AZMF data sheets";
        DesignIssue.range(issues, "displacementM3", displacementM3, 5e-6, motorKind == HydraulicUnit.AXIAL_PISTON ? 1e-3 : 2e-4, sheets);
        DesignIssue.range(issues, "reliefPa", reliefPa, 20e5, motorKind.peakPa(), sheets);
        DesignIssue.range(issues, "maxSpeedRpm", maxSpeedRpm, 50, mostSpeedRpm(), sheets + ": the most speed");
        DesignIssue.range(issues, "reductionRatio", reductionRatio, 1, 300, "planetary track, swing and winch drives (Rexroth GFT, Brevini, Bonfiglioli)");
        if (winch) {
            double least = WireRope.minDrumDiameterM(WireRope.Group.M4, ROPE, ropeDiameterM);
            DesignIssue.range(issues, "drumDiameterM", drumDiameterM, least, Math.max(least, 60 * ropeDiameterM), "ISO 4308-1 M4: a drum of 16 rope diameters");
            DesignIssue.range(issues, "ropeLayers", ropeLayers(), 1, MAX_LAYERS, "winch drums: up to about 8 layers");
            DesignIssue.range(issues, "ropeDiameterM", ropeDiameterM, 0.006, 0.08, "EN 12385-4 crane ropes");
        }
        return issues;
    }

    // ---------------------------------------------------------------- compiling

    /** The winch's drum as AeroCore's motor sees it, or null. */
    public HydraulicMotorPart.Drum drum() {
        return winch ? new HydraulicMotorPart.Drum(middleRadiusM(), partsOfLine, ropeStartM, ropeMinM, ropeMaxM) : null;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new HydraulicMotorPart(this, motor(), circuit, reliefPa, maxOutputRadPerS(), brakeNm, Math.max(inertiaKgM2(), 1e-6), Vec3d.Z.scale(-1), drum(), mass(),
                Components.extrapolations(issues));
    }

    // ---------------------------------------------------------------- JSON

    @Override public Map<String, Object> parameters() {
        return Fields.object("circuit", circuit, "motorKind", motorKind.name().toLowerCase(Locale.ROOT), "displacementM3", displacementM3, "reliefPa", reliefPa,
                "maxSpeedRpm", maxSpeedRpm, "reductionRatio", reductionRatio, "brakeNm", brakeNm, "winch", winch, "drumDiameterM", drumDiameterM,
                "drumWidthM", drumWidthM, "ropeDiameterM", ropeDiameterM, "partsOfLine", partsOfLine, "ropeMinM", ropeMinM, "ropeMaxM", ropeMaxM, "ropeStartM", ropeStartM);
    }

    public static PartDesign read(Fields f) {
        boolean winch = f.bool("winch", false);
        HydraulicMotorDesign d = winch ? SampleMachinery.hoistWinch() : standard();
        return new HydraulicMotorDesign(f.string("name", d.name), f.string("circuit", d.circuit), EngineComponent.Rules.choice(f, "motorKind", HydraulicUnit.class, d.motorKind),
                f.number("displacementM3", d.displacementM3), f.number("reliefPa", d.reliefPa), f.number("maxSpeedRpm", d.maxSpeedRpm),
                f.number("reductionRatio", d.reductionRatio), f.number("brakeNm", d.brakeNm), winch, f.number("drumDiameterM", d.drumDiameterM),
                f.number("drumWidthM", d.drumWidthM), f.number("ropeDiameterM", d.ropeDiameterM), f.integer("partsOfLine", d.partsOfLine), f.number("ropeMinM", d.ropeMinM),
                f.number("ropeMaxM", d.ropeMaxM), f.number("ropeStartM", d.ropeStartM));
    }

    /** The standard motor: a 20 t excavator's travel drive (SampleMachinery.travelMotor). */
    public static HydraulicMotorDesign standard() { return SampleMachinery.travelMotor(); }
}
