package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.hydraulic.Hydraulics;
import dev.mcmod.aerocore.api.hydraulic.Pump;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.HydraulicPumpPart;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A hydraulic pump (hdfv:hydraulic_pump, 0.26, 油壓泵): an open-circuit displacement pump on an engine's shaft (its rotor
 * "input" along the part's +x, the mounting flange at the origin, the body along +x) feeding a circuit by name ("main"
 * by default; the rams, slews and motors on the same circuit share its flow). Its displacement (displacementM3, shown
 * in cm^3 per revolution as catalogues give it), its control (FIXED, PRESSURE_COMPENSATED or POWER_LIMITED: a
 * horsepower controller holding p x Q to powerLimitW) and the pressure its relief or compensator holds (reliefPa,
 * named as the motors' and rams' relief). In the world it
 * is AeroCore's HydraulicPumpPart (flow at its shaft speed, torque on the engine).
 *
 * <p>Its kind (HydraulicUnit) gives the efficiencies: axial piston (the main pumps of excavators: 0.95 / 0.92 at 280
 * bar) or external gear (pilot, fan and steering pumps: 0.90 / 0.85 at 200 bar; a gear pump's displacement is fixed).
 * Its mass and its rotating group's inertia: MachineRules (the A10VO and AZPF data sheets). ratedSpeedRpm is the shaft
 * speed its figures are given at; above the self-priming speed (axial piston 3000 (V/28)^-0.19 rpm, a fit to the A10VO
 * data sheet's sizes 28 to 140; gear 3500 rpm) it is an extrapolation.
 *
 * <p>A 20 t excavator's main pumps: two of about 110 cm^3 (Kawasaki K3V112DT, a tandem of two 112 cm^3 swash-plate
 * pumps on one shaft) at 343 bar, power limited to the engine's 100 to 120 kW.
 */
public record HydraulicPumpDesign(String name, String circuit, HydraulicUnit pumpKind, Pump.Control control, double displacementM3, double reliefPa,
                                  double powerLimitW, double ratedSpeedRpm) implements PartDesign, RecordEditor.Shown {
    public static final String TYPE = "hdfv:hydraulic_pump";
    /** 0.26: the speed sensing's band, shares of the rated speed (a rule). */
    public static final double SENSE_LOW = 0.3, SENSE_HIGH = 0.6;
    /** The circuits a design screen offers (any name works in JSON): the main circuit, a second pump's, swing, travel, a pilot or auxiliary one. */
    public static final List<String> CIRCUITS = List.of("main", "second", "swing", "travel", "steering", "auxiliary", "pilot");

    public HydraulicPumpDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(pumpKind, "pumpKind"); Objects.requireNonNull(control, "control");
        circuit = circuit == null ? "main" : circuit;
    }

    @Override public String type() { return TYPE; }

    /** The power limit means something only to a power-limited pump. */
    @Override public boolean shows(String parameter) { return !parameter.equals("powerLimitW") || control == Pump.Control.POWER_LIMITED; }

    /** Displacement in cm^3 per revolution. */
    public double displacementCm3() { return displacementM3 / Hydraulics.CM3_M3; }

    /** The highest speed it primes itself at, rpm (see the class). */
    public double selfPrimingRpm() {
        return pumpKind == HydraulicUnit.AXIAL_PISTON ? 3000 * Math.pow(Math.max(displacementCm3(), 1e-3) / 28, -0.19) : 3500;
    }

    public double massKg() { return MachineRules.unitKg(pumpKind, true, displacementCm3()); }

    public double inertiaKgM2() { return MachineRules.unitInertiaKgM2(pumpKind, displacementCm3()); }

    /** Its size along the shaft, m (A10VO 45: about 0.23 m). */
    public double sizeM() { return 0.065 * Math.cbrt(Math.max(displacementCm3(), 1)); }

    public MassProperties mass() {
        double s = sizeM();
        return Components.box(massKg(), new Vec3d(0.5 * s, 0, 0), new Vec3d(s, 0.7 * s, 0.7 * s));
    }

    /** AeroCore's pump of this design. */
    public Pump pump() { return pumpKind.pump(displacementM3, control, reliefPa, powerLimitW); }

    /** Flow at the rated speed with no pressure, m^3/s. */
    public double ratedFlowM3PerS() { return pump().flowM3PerS(ratedSpeedRpm * Hydraulics.RPM_RAD_PER_S, 0); }

    /** Shaft torque at the rated speed and its full pressure (the controls' flow at that pressure), N m. */
    public double maxTorqueNm() { return pump().torqueNm(ratedSpeedRpm * Hydraulics.RPM_RAD_PER_S, reliefPa); }

    /** Corner power: full flow at the rated speed times the full pressure, W (what the engine would need without a power limit). */
    public double cornerPowerW() { return reliefPa * displacementM3 * ratedSpeedRpm / 60; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (circuit.isBlank()) { issues.add(DesignIssue.invalid("circuit", "A pump feeds a circuit: give it a name")); }
        EngineComponent.Rules.positive(issues, "displacementM3", displacementM3);
        EngineComponent.Rules.positive(issues, "reliefPa", reliefPa);
        EngineComponent.Rules.positive(issues, "ratedSpeedRpm", ratedSpeedRpm);
        if (control == Pump.Control.POWER_LIMITED) { EngineComponent.Rules.positive(issues, "powerLimitW", powerLimitW); }
        else { EngineComponent.Rules.nonnegative(issues, "powerLimitW", powerLimitW); }
        if (pumpKind == HydraulicUnit.GEAR && control != Pump.Control.FIXED) { issues.add(DesignIssue.invalid("control", "A gear pump's displacement is fixed: its control is fixed")); }
        if (!issues.isEmpty()) { return issues; }
        String sheets = "Bosch Rexroth A10VO / A4VSO and AZPF data sheets";
        DesignIssue.range(issues, "displacementM3", displacementM3, 1e-6, pumpKind == HydraulicUnit.AXIAL_PISTON ? 1e-3 : 2e-4, sheets);
        DesignIssue.range(issues, "reliefPa", reliefPa, 20e5, pumpKind.peakPa(), sheets);
        DesignIssue.range(issues, "ratedSpeedRpm", ratedSpeedRpm, 500, selfPrimingRpm(), sheets + ": self-priming speed");
        return issues;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        // 0.26: speed sensing (anti-stall): no stroke below SENSE_LOW of its rated speed, full from SENSE_HIGH (a rule after the
        // engine-speed-sensing power controls of excavator pumps): a lugged engine sheds the pump, a starting one turns it free
        double rated = ratedSpeedRpm * Math.PI / 30;
        return new HydraulicPumpPart(this, pump(), circuit, Vec3d.X, inertiaKgM2(), mass(), Components.extrapolations(issues), SENSE_LOW * rated, SENSE_HIGH * rated);
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("circuit", circuit, "pumpKind", pumpKind.name().toLowerCase(Locale.ROOT), "control", control.name().toLowerCase(Locale.ROOT),
                "displacementM3", displacementM3, "reliefPa", reliefPa);
        if (control == Pump.Control.POWER_LIMITED || powerLimitW != 0) { m.put("powerLimitW", powerLimitW); }
        m.put("ratedSpeedRpm", ratedSpeedRpm);
        return m;
    }

    public static PartDesign read(Fields f) {
        HydraulicPumpDesign d = standard();
        Pump.Control control = EngineComponent.Rules.choice(f, "control", Pump.Control.class, d.control);
        return new HydraulicPumpDesign(f.string("name", d.name), f.string("circuit", d.circuit), EngineComponent.Rules.choice(f, "pumpKind", HydraulicUnit.class, d.pumpKind),
                control, f.number("displacementM3", d.displacementM3), f.number("reliefPa", d.reliefPa),
                f.number("powerLimitW", control == Pump.Control.POWER_LIMITED ? d.powerLimitW : 0), f.number("ratedSpeedRpm", d.ratedSpeedRpm));
    }

    /** The standard pump: one of a 20 t excavator's main pumps (SampleMachinery.excavatorPump). */
    public static HydraulicPumpDesign standard() { return SampleMachinery.excavatorPump(); }
}
