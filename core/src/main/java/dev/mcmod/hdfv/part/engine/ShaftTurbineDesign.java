package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.FreeTurbine;
import dev.mcmod.aerocore.api.engine.Gas;
import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.api.engine.Turbomachinery;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A free-turbine shaft engine assembled from designed components: a turboprop (hdfv:turboprop, its output geared down
 * to a propeller's speed) or a turboshaft (hdfv:turboshaft, its power turbine governed at a constant speed, as a
 * helicopter's). Inlet, the gas generator's compressor, combustor and turbine, the free power turbine, the reduction
 * gearbox (a direct drive with no meshes), the exhaust and the fuel control. The output shaft lies along the part's +X
 * axis and is its first rotor, so a vehicle's shaft connection takes it to a propeller, a rotor or a gearbox; the gas
 * generator is the second rotor. The exhaust's residual thrust pushes along +X.
 * <p>
 * The cycle (AeroCore's FreeTurbine) is sized at sea-level static. In flight the gas generator follows its fuel, the
 * power turbine's torque (FreeTurbine.quasiSteady, at the output's speed: twice the design torque with the shaft held)
 * goes through the gearbox to the output. The control's idle and maximum speeds are the gas generator's (Ng). Control
 * rules (a schedule, not measured):
 * <ul>
 * <li>Turboprop: throttle is the power lever, setting the Ng target between idle and maximum; above 106 % power
 *   turbine speed the target drops to idle (the overspeed governor; a propeller governor holds the speed below it).</li>
 * <li>Turboshaft: the power turbine governor holds it at 100 %: its demand (0..1 of the Ng range above idle) is 4 times
 *   the speed error plus the error's integral at 2 per second; throttle caps the demand (0 ground idle, 1 fly).</li>
 * <li>Both: fuel follows the running line's at the Ng target plus a proportional correction, within the turbine
 *   temperature limit and the acceleration schedule, at least half the running line's; above the gearbox's rated
 *   torque the fuel is cut back in proportion. Starter, light-off and flame-out as JetEngineDesign's.</li>
 * </ul>
 * Starting as JetControlDesign says: the starter turns the gas generator, the start schedule limits its acceleration
 * below idle. Inputs "throttle" 0..1 and "ignition" (unbound = on). Internal states: fuel flow (kg/s), burner, surge
 * time left (s), the governor's integral, the start schedule's speed, then the matching's solution.
 */
public record ShaftTurbineDesign(String name, Output output, JetInletDesign inlet, CompressorDesign compressor, CombustorDesign combustor, TurbineDesign turbine,
                                 PowerTurbineDesign powerTurbine, ReductionGearboxDesign gearbox, JetNozzleDesign exhaust, JetControlDesign control)
        implements PartDesign {
    public static final String TURBOPROP = "hdfv:turboprop", TURBOSHAFT = "hdfv:turboshaft";
    /** Power turbine overspeed (fraction of design) at which a turboprop's fuel drops to idle; the turboshaft governor's gains (per unit error, per second). */
    public static final double OVERSPEED = 1.06, GOVERNOR_GAIN = 4, GOVERNOR_INTEGRAL = 2;

    /** What the output drives: a propeller through the gearbox, or a shaft held at constant speed. */
    public enum Output { PROPELLER, SHAFT }

    public ShaftTurbineDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(output, "output");
        for (Object o : new Object[]{inlet, compressor, combustor, turbine, powerTurbine, gearbox, exhaust, control}) { Objects.requireNonNull(o, "component"); }
    }

    /** The fluids it burns (0.8; its fuel control's). */
    public java.util.Set<String> acceptedFluids() { return control.acceptedFluids(); }

    @Override public String type() { return output == Output.PROPELLER ? TURBOPROP : TURBOSHAFT; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("inlet", inlet); m.put("compressor", compressor); m.put("combustor", combustor); m.put("turbine", turbine);
        m.put("powerTurbine", powerTurbine); m.put("gearbox", gearbox); m.put("exhaust", exhaust); m.put("control", control);
        return m;
    }

    public FreeTurbine.Design cycle() {
        Turbojet.Design gg = new Turbojet.Design(compressor.pressureRatio(), compressor.airflowKgPerS(), compressor.polytropicEfficiency(), control.turbineInletK(),
                1 - combustor.pressureLoss(), combustor.efficiency(), turbine.polytropicEfficiency(), turbine.mechanicalEfficiency(), turbine.coolingBleed(),
                exhaust.pressureRetention(), false, 1, inlet.recovery(), inlet.kind(), control.fuelModel());
        return new FreeTurbine.Design(gg, powerTurbine.polytropicEfficiency(), powerTurbine.designRpm(), powerTurbine.exhaustPressureRatio());
    }
    public FreeTurbine.Sizing sizing() { return FreeTurbine.size(cycle(), Turbojet.Flight.sea(0)); }

    /** Design speed of the output shaft, rpm. */
    public double outputRpm() { return powerTurbine.designRpm() / gearbox.ratio(); }
    public double gasGeneratorSpeed() { return compressor.designRpm() * Math.PI / 30; }
    /** Moment of inertia at the output shaft (the power turbine's through the gearbox, and the output gear), kg m^2. */
    public double outputInertia() {
        double r = gearboxDiameter() / 2;
        return powerTurbine.rotorInertia() * gearbox.ratio() * gearbox.ratio() + 0.05 * gearbox.massKg() * r * r;   // and the output gear's
    }
    /** Angular momentum at the output shaft per rad/s of its speed: the power turbine's times the ratio (it spins that much faster), and the output gear's, kg m^2. */
    public double outputMomentum() {
        double r = gearboxDiameter() / 2;
        return powerTurbine.rotorInertia() * gearbox.ratio() + 0.05 * gearbox.massKg() * r * r;
    }
    public double gasGeneratorInertia() { return compressor.rotorInertia() + turbine.rotorInertia(); }
    /** Output torque at design (sea-level static, the gearbox's losses taken), N m. */
    public double designOutputTorque(FreeTurbine.Point p) { return p.shaftTorqueNm() * gearbox.ratio() * gearbox.efficiency(); }
    /** Overall length, m: inlet, compressor, combustor, the turbines (0.1 diameters per stage), gearbox (0.3 case diameters per mesh, two at most), exhaust. */
    public double length() {
        return inlet.lengthM() + compressor.lengthM() + combustor.addedLengthM() + 0.1 * turbine.tipDiameterM() * turbine.stages()
                + 0.1 * powerTurbine.tipDiameterM() * powerTurbine.stages() + 0.6 * gearboxDiameter() * Math.min(gearbox.meshes(), 2) / 2 + exhaust.tailpipeLengthM();
    }
    /** Gearbox case diameter, m: from its rated torque (the gears' size goes with the cube root of torque; 0.32 m for 2,200 N m). */
    public double gearboxDiameter() { return gearbox.direct() ? 0 : 0.32 * Math.cbrt(gearbox.ratedTorqueNm() / 2200); }
    public double diameter() {
        return Math.max(Math.max(compressor.tipDiameterM(), turbine.tipDiameterM()), Math.max(combustor.casingDiameterM(), powerTurbine.tipDiameterM())) * 1.1;
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        FreeTurbine.Sizing s;
        try { s = sizing(); }
        catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("cycle", e.getMessage())); return issues; }
        FreeTurbine.Point p = s.point();
        double t3 = p.tt3();
        if (turbine.metalTemperature(control.turbineInletK(), t3) > turbine.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("control.turbineInletK", "The turbine blades would run above their material's service temperature (cooled, they take up to "
                    + Math.round(turbine.maxInletTemperature(t3)) + " K)"));
        }
        if (p.tt45() > powerTurbine.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("powerTurbine.material", "The power turbine cannot take the " + Math.round(p.tt45()) + " K gas between the turbines"));
        }
        if (p.tt5() > exhaust.material().serviceTemperatureK()) { issues.add(DesignIssue.invalid("exhaust.material", "The exhaust cannot take the " + Math.round(p.tt5()) + " K gas")); }
        double flux = compressor.airflowKgPerS() / inlet.captureAreaM2();
        if (flux > Turbomachinery.CHOKED_FLUX) { issues.add(DesignIssue.invalid("inlet.captureAreaM2", "The inlet is too small: it chokes below the design airflow")); }
        else { DesignIssue.range(issues, "inletFlux", flux, 0, Turbomachinery.MACH_07_FLUX, "inlets sized for Mach 0.5-0.7 at the face"); }
        double air = compressor.airflowKgPerS();
        TurbofanDesign.turbine(issues, "turbine", turbine, p.turbinePowerW() / (turbine.mechanicalEfficiency() * air * (1 - turbine.coolingBleed())), compressor.designRpm());
        double ut = powerTurbine.tipSpeed(), loading = Turbomachinery.stageLoading(p.shaftPowerW() / (turbine.mechanicalEfficiency() * air), powerTurbine.stages(), ut);
        if (loading > JetEngineDesign.TURBINE_LOADING_LIMIT) { issues.add(DesignIssue.invalid("powerTurbine.stages", "Too few power turbine stages or too slow a turbine: stage loading " + loading)); }
        else { DesignIssue.range(issues, "powerTurbineStageLoading", loading, 0, JetEngineDesign.TURBINE_LOADING, "turbine stage loadings, Smith's chart"); }
        double stress = powerTurbine.discStress();
        if (stress > powerTurbine.material().yieldPa()) { issues.add(DesignIssue.invalid("powerTurbine.tipDiameterM", "The power turbine would yield at this tip speed")); }
        else { DesignIssue.range(issues, "powerTurbineDiscStressOverYield", stress / powerTurbine.material().yieldPa(), 0, 2.0 / 3, "rotors kept under two thirds of yield"); }
        double torque = designOutputTorque(p);
        if (torque > gearbox.ratedTorqueNm()) { issues.add(DesignIssue.invalid("gearbox.ratedTorqueNm", "The gearbox is rated below the engine's " + Math.round(torque) + " N m")); }
        else { DesignIssue.range(issues, "gearboxTorqueOverRating", torque / gearbox.ratedTorqueNm(), 0.5, 1, "gearboxes sized to their engine"); }
        if (output == Output.PROPELLER) { DesignIssue.range(issues, "outputRpm", outputRpm(), 1000, 3000, "propeller speeds of turboprops"); }
        return issues;
    }

    /**
     * Accessories and structure (accessory gearbox, starter-generator, fuel and oil systems, an inlet particle separator,
     * frames and bearing sumps) of a shaft engine, kg: the control's given mass, or 23 g per kW of the core's gas power
     * (airflow times the enthalpy rise from 288 K to the turbine inlet), less for model-size cores
     * (times 1 - exp(-(w / 1.5 kg/s)^2)). Set so that the PT6A-27- and T700-class samples come within 15 % of their 131
     * and 207 kg; shaft engines carry far more of these than a turbojet of the same airflow.
     */
    public double accessoriesMassKg() {
        if (!Double.isNaN(control.massGivenKg())) { return control.massGivenKg(); }
        double w = compressor.airflowKgPerS(), gasPower = w * (Gas.AIR.enthalpy(control.turbineInletK()) - Gas.AIR.enthalpy(Turbojet.T_REF));
        return 23e-6 * gasPower * (1 - Math.exp(-(w / 1.5) * (w / 1.5)));
    }

    public double totalMassKg() {
        double a8 = sizing().exhaustAreaM2();
        return inlet.massKg() + compressor.massKg() + combustor.massKg() + turbine.massKg() + powerTurbine.massKg() + gearbox.massKg() + exhaust.massFor(a8)
                + accessoriesMassKg();
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign readTurboprop(Fields f) { return read(f, Output.PROPELLER, "turboprop"); }
    public static PartDesign readTurboshaft(Fields f) { return read(f, Output.SHAFT, "turboshaft"); }
    private static PartDesign read(Fields f, Output output, String name) {
        return new ShaftTurbineDesign(f.string("name", name), output, TurbofanDesign.component(f, "inlet", JetInletDesign::read),
                TurbofanDesign.component(f, "compressor", CompressorDesign::read), TurbofanDesign.component(f, "combustor", CombustorDesign::read),
                TurbofanDesign.component(f, "turbine", TurbineDesign::read), TurbofanDesign.component(f, "powerTurbine", PowerTurbineDesign::read),
                TurbofanDesign.component(f, "gearbox", ReductionGearboxDesign::read), TurbofanDesign.component(f, "exhaust", JetNozzleDesign::read),
                TurbofanDesign.component(f, "control", JetControlDesign::read));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new Compiled(this, issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    public static final int STATE_FUEL = 0, STATE_LIT = 1, STATE_SURGE = 2, STATE_GOVERNOR = 3, STATE_SCHEDULE = 4, STATE_SOLUTION = 5;

    /** The sized cycle and its running lines. */
    public record Model(FreeTurbine.Sizing sizing, FreeTurbine.RunningLines lines) {}
    private static final Map<String, Model> MODELS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Model> eldest) { return size() > 32; }
    });

    public Model model() {
        String key = Json.write(toJson());
        Model m = MODELS.get(key);
        if (m == null) {
            FreeTurbine.Sizing s = sizing();
            m = new Model(s, FreeTurbine.runningLines(s));
            MODELS.put(key, m);
        }
        return m;
    }

    /**
     * The gas generator's speed target (fraction of design) for the throttle, the power turbine's speed and the
     * governor's demand (turboshaft).
     */
    public double target(double throttle, double powerTurbineSpeed, double demand) {
        double idle = control.idleFraction(), range = control.maxSpeedFraction() - idle;
        if (output == Output.PROPELLER) { return powerTurbineSpeed > OVERSPEED ? idle : idle + throttle * range; }
        return idle + range * Math.min(throttle, demand);
    }

    /** The turboshaft governor's demand (0..1) from its integral and the power turbine's speed. */
    public static double demand(double integral, double powerTurbineSpeed) { return Math.clamp(integral + GOVERNOR_GAIN * (1 - powerTurbineSpeed), 0, 1); }

    /**
     * The fuel the control asks for at a quasi-steady point with a gas generator speed target: the running line's fuel at
     * the target plus a proportional correction, within the turbine temperature limit, the acceleration schedule and the
     * gearbox's rated torque, at least half the running line's fuel.
     */
    public double command(Model m, Turbojet.Flight flight, FreeTurbine.Point p, double target) {
        FreeTurbine.Sizing s = m.sizing();
        double n = p.gasGeneratorSpeedFraction();
        double here = FreeTurbine.lineFuel(s, m.lines(), flight, n);
        double command = FreeTurbine.lineFuel(s, m.lines(), flight, target) + JetRuntime.SPEED_GAIN * (target - n) * s.point().fuelKgPerS();
        double max = Double.POSITIVE_INFINITY;
        if (p.airflowKgPerS() > 0) {
            max = FreeTurbine.fuelForTurbineInlet(s, p, control.turbineInletK());
            if (control.accelerationSchedule()) { max = Math.min(max, FreeTurbine.fuelForSurgeMargin(s, m.lines(), flight, p, compressor.surgeMargin() * (1 - control.surgeReserve()))); }
            double torque = designOutputTorque(p);
            if (torque > gearbox.ratedTorqueNm()) { max = Math.min(max, p.fuelKgPerS() * gearbox.ratedTorqueNm() / torque); }
        }
        command = Math.clamp(command, 0.5 * here, Math.max(0.5 * here, max));
        if (n > control.maxSpeedFraction() * 1.01) { command = 0.5 * here; }
        return command;
    }

    private static final class Compiled implements Part {
        private final ShaftTurbineDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        private final double outputInertia, outputMomentum, ggInertia, ggOmega, ptOmega, ratio, gearEfficiency, halfLength;
        private volatile Model model;

        Compiled(ShaftTurbineDesign design, List<DesignIssue> issues) {
            this.design = design; this.issues = issues;
            double m = design.totalMassKg(), length = design.length(), d = design.diameter();
            mass = MassProperties.of(m, Vec3d.ZERO, InertiaTensor.box(m, new Vec3d(length, d, d)));
            outputInertia = design.outputInertia(); outputMomentum = design.outputMomentum(); ggInertia = design.gasGeneratorInertia();
            ggOmega = design.gasGeneratorSpeed(); ptOmega = design.powerTurbine().designOmega();
            ratio = design.gearbox().ratio(); gearEfficiency = design.gearbox().efficiency();
            halfLength = length / 2;
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return mass; }
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return issues; }
        @Override public List<Rotor> rotors() { return List.of(new Rotor(Vec3d.X, outputInertia, true, outputMomentum), new Rotor(Vec3d.X, ggInertia, true)); }
        @Override public List<String> inputs() { return List.of("throttle", "ignition"); }
        @Override public int internalStates() { return STATE_SOLUTION + JetRuntime.SOLUTION; }
        /** 0.11: the exhaust for the plume (Exhaust). */
        @Override public int shownValues() { return Exhaust.COUNT; }
        @Override public void warmUp() { model(); }

        Model model() {
            Model m = model;
            if (m == null) {
                synchronized (this) {
                    if (model == null) { model = design.model(); }
                    m = model;
                }
            }
            return m;
        }

        @Override public void loads(PartStep step) {
            Model m = model();
            JetControlDesign c = design.control();
            double dt = step.dt();
            // the inlet is at the gas generator's end, away from the output (at +X)
            JetRuntime.Air air = JetRuntime.air(step, new Vec3d(-halfLength, 0, 0));
            Turbojet.Flight flight = air.flight();
            double omegaOut = step.rotorSpeed(0), omegaG = step.rotorSpeed(1);
            double nP = Math.max(0, omegaOut * ratio / ptOmega), nG = Math.max(0, omegaG / ggOmega);
            double throttle = Math.clamp(step.input("throttle", 0), 0, 1), ignition = step.input("ignition", 1);
            double fuel = Math.max(0, step.internal(STATE_FUEL)), surge = Math.max(0, step.internal(STATE_SURGE) - dt);
            double cutout = JetRuntime.cutout(c.idleFraction());
            int burner = JetRuntime.burner(step.internal(STATE_LIT), ignition, JetRuntime.fed(step), air.drowned(), nG, c.lightOffFraction(), cutout);
            boolean lit = burner > 0, running = burner > 1;
            double schedule = JetRuntime.schedule(step.internal(STATE_SCHEDULE), nG, c.startRate(), dt, lit);
            if (!lit) { fuel = 0; }
            FreeTurbine.Point p = FreeTurbine.quasiSteady(m.sizing(), m.lines(), flight, nG, nP, air.drowned() ? 0 : fuel, JetRuntime.solution(step, STATE_SOLUTION));
            JetRuntime.burn(step, c.acceptedFluids(), ignition, air.drowned() ? 0 : fuel);
            if (lit && p.surgeMarginUsed() > design.compressor().surgeMargin()) { surge = JetRuntime.SURGE_TIME; }
            double share = surge > 0 ? JetRuntime.SURGE_SHARE : 1;
            double wG = Math.max(omegaG, 0.05 * ggOmega);
            double turbinePower = p.turbinePowerW() * share, compressorPower = p.compressorPowerW() * share;
            boolean starter = ignition >= 0.5 && !running && nG < cutout;
            step.rotorTorque(1, Turbomachinery.spoolTorque(turbinePower, compressorPower, wG) + (starter ? JetRuntime.starter(c.starterTorqueNm(), nG, cutout) : 0),
                    -(turbinePower + compressorPower) / (wG * wG), false);
            // the power turbine through the gearbox: its torque falls linearly with speed (to none at twice the best speed ratio)
            double torque = p.shaftTorqueNm() * ratio * gearEfficiency * share;
            double stall = nP < 1.9 ? torque / (1 - nP / 2) : torque;
            step.rotorTorque(0, torque, -stall * ratio / (2 * ptOmega), false);
            double thrust = air.drowned() ? 0 : p.thrustN() * share;
            step.force(new AppliedForce(AppliedForce.Kind.THRUST, step.offset(new Vec3d(-halfLength, 0, 0)), step.axis(Vec3d.X).scale(thrust)));
            // the governor and the fuel control, for the next step
            double integral = step.internal(STATE_GOVERNOR), command = 0;
            if (design.output() == Output.SHAFT) {
                double demand = demand(integral, nP);
                if (demand > 0 && demand < 1) { integral = Math.clamp(integral + GOVERNOR_INTEGRAL * (1 - nP) * dt, -1, 1); }
            }
            if (lit) {
                double target = design.target(throttle, nP, demand(integral, nP));
                if (nG < c.idleFraction()) { target = Math.min(target, schedule); }   // the start schedule below idle
                command = design.command(m, flight, p, target);
            }
            fuel += (command - fuel) * Math.min(1, dt / JetRuntime.FUEL_LAG);
            step.setInternal(STATE_FUEL, lit ? fuel : 0);
            step.setInternal(STATE_LIT, burner);
            step.setInternal(STATE_SURGE, surge);
            step.setInternal(STATE_GOVERNOR, integral);
            step.setInternal(STATE_SCHEDULE, schedule);
            JetRuntime.keep(step, STATE_SOLUTION, p.solution());
            // 0.11: the exhaust: a shaft engine's stacks let the gas out slowly at the ambient pressure (Mach 0.3 a display estimate)
            Exhaust.show(step, 0, lit && !air.drowned(), p.tt5(), 0.3, 1);
            step.diagnostic("speedFraction", nG);
            step.diagnostic("powerTurbineSpeed", nP);
            step.diagnostic("rpm", omegaG * 30 / Math.PI);
            step.diagnostic("outputRpm", omegaOut * 30 / Math.PI);
            step.diagnostic("torqueNm", torque);
            step.diagnostic("shaftPowerW", torque * omegaOut);
            step.diagnostic("thrustN", thrust);
            step.diagnostic("fuelKgPerS", p.fuelKgPerS());
            step.diagnostic("turbineInletK", p.tt4());
            step.diagnostic("interTurbineK", p.tt45());
            step.diagnostic("exhaustK", p.tt5());
            step.diagnostic("surgeMarginUsed", p.surgeMarginUsed());
            step.diagnostic("lit", lit ? 1 : 0);
            step.diagnostic("starter", starter ? 1 : 0);
            step.diagnostic("surging", surge > 0 ? 1 : 0);
        }
    }
}
