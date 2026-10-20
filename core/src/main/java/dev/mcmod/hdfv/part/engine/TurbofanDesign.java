package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.Turbofan;
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
 * A two-spool turbofan assembled from designed components: inlet, fan (with booster stages and the bypass duct), the
 * core's compressor, combustor and high-pressure turbine, the low-pressure turbine that drives the fan, a mixer (or
 * none: separate jets), the core (or mixed) nozzle and the fuel control. It points along the part's +X axis and pushes
 * forward; its two spools are rotors about +X (fan first, core second).
 * <p>
 * The cycle (AeroCore's Turbofan) is sized at sea-level static from the compressors' pressure ratios, the core's
 * airflow, the bypass ratio and the control's turbine inlet temperature. In flight both spools follow their fuel
 * (Turbofan.quasiSteady). The control's idle and maximum speeds are the fan's (N1), its light-off speed the core's
 * (N2): throttle sets the fan speed target; fuel follows the running line's fuel there plus a proportional correction,
 * limited by the turbine temperature and the acceleration schedule (the core compressor's surge margin) and below by
 * half the running line's fuel. The starter turns the core; below the core's idle speed the start schedule limits the
 * core's acceleration (JetRuntime, JetControlDesign). Inputs "throttle" 0..1 and "ignition" (unbound = on). Internal
 * states: fuel flow (kg/s), burner (0 out, 1 lit while the starter helps, 2 running), surge time left (s), the start
 * schedule's core speed, then the matching's solution, then (0.10) the afterburner lit.
 * <p>
 * An afterburner (0.10, AeroCore's Reheat on the mixed stream) is an optional component, for mixed exhausts only:
 * input "reheat" 0..1 as on the turbojet (JetEngineDesign), lit from 90 % of the fan's top speed, out below 85 %.
 */
public record TurbofanDesign(String name, JetInletDesign inlet, FanDesign fan, CompressorDesign compressor, CombustorDesign combustor,
                             TurbineDesign hpTurbine, TurbineDesign lpTurbine, MixerDesign mixer, JetNozzleDesign nozzle, JetControlDesign control,
                             AfterburnerDesign afterburner)
        implements PartDesign {
    public static final String TYPE = "hdfv:turbofan";

    public TurbofanDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{inlet, fan, compressor, combustor, hpTurbine, lpTurbine, mixer, nozzle, control, afterburner}) { Objects.requireNonNull(o, "component"); }
    }

    /** Without an afterburner (as before 0.10). */
    public TurbofanDesign(String name, JetInletDesign inlet, FanDesign fan, CompressorDesign compressor, CombustorDesign combustor, TurbineDesign hpTurbine,
                          TurbineDesign lpTurbine, MixerDesign mixer, JetNozzleDesign nozzle, JetControlDesign control) {
        this(name, inlet, fan, compressor, combustor, hpTurbine, lpTurbine, mixer, nozzle, control, AfterburnerDesign.none());
    }

    /** The fluids it burns (0.8; its fuel control's). */
    public java.util.Set<String> acceptedFluids() { return control.acceptedFluids(); }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("inlet", inlet); m.put("fan", fan); m.put("compressor", compressor); m.put("combustor", combustor); m.put("hpTurbine", hpTurbine);
        m.put("lpTurbine", lpTurbine); m.put("mixer", mixer); m.put("nozzle", nozzle); m.put("control", control); m.put("afterburner", afterburner);
        return m;
    }

    /** The cycle these components make: the compressor's airflow is the core's. */
    public Turbofan.Design cycle() {
        Turbojet.Design core = new Turbojet.Design(compressor.pressureRatio(), compressor.airflowKgPerS(), compressor.polytropicEfficiency(), control.turbineInletK(),
                1 - combustor.pressureLoss(), combustor.efficiency(), hpTurbine.polytropicEfficiency(), hpTurbine.mechanicalEfficiency(), hpTurbine.coolingBleed(),
                nozzle.pressureRetention(), nozzle.convergentDivergent(), nozzle.convergentDivergent() ? nozzle.exitAreaRatio() : 1, inlet.recovery(), inlet.kind(),
                control.fuelModel());
        return new Turbofan.Design(core, fan.bypassRatio(), fan.pressureRatio(), fan.boosterRatio(), fan.polytropicEfficiency(), lpTurbine.polytropicEfficiency(),
                1 - fan.bypassPressureLoss(), mixer.mixes() ? Turbofan.Exhaust.MIXED : Turbofan.Exhaust.SEPARATE, 1 - mixer.pressureLoss());
    }
    /** The cycle sized at sea-level static. */
    public Turbofan.Sizing sizing() { return Turbofan.size(cycle(), Turbojet.Flight.sea(0)); }

    public double totalAirflowKgPerS() { return compressor.airflowKgPerS() * (1 + fan.bypassRatio()); }
    /** Design speeds of the fan's and the core's spools, rad/s. */
    public double lowSpeed() { return fan.designRpm() * Math.PI / 30; }
    public double highSpeed() { return compressor.designRpm() * Math.PI / 30; }
    /** Moments of inertia of the fan spool (fan, booster, LP turbine) and the core spool, kg m^2. */
    public double lowInertia() { return fan.rotorInertia() + lpTurbine.rotorInertia(); }
    public double highInertia() { return compressor.rotorInertia() + hpTurbine.rotorInertia(); }
    /** Overall length, m: inlet, fan, compressor, combustor, the turbines (0.1 diameters per stage), mixer, tailpipe. */
    public double length() {
        return inlet.lengthM() + fan.lengthM() + compressor.lengthM() + combustor.addedLengthM() + 0.1 * hpTurbine.tipDiameterM() * hpTurbine.stages()
                + 0.1 * lpTurbine.tipDiameterM() * lpTurbine.stages() + mixer.addedLengthM() + nozzle.tailpipeLengthM();
    }
    /** Nacelle diameter, m (1.1 fan diameters). */
    public double diameter() { return 1.1 * fan.tipDiameterM(); }

    /** Core air's total temperature and pressure behind the booster at design (sea-level static), K and Pa: the core compressor's inlet. */
    public double[] coreInlet() {
        return new double[]{fan.boosterExitTemperature(), Turbojet.P_REF * inlet.recovery() * fan.pressureRatio() * fan.boosterRatio()};
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            if (e.getValue() == compressor) { continue; }
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        boolean fanValid = issues.stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().startsWith("fan."));
        double[] in = fanValid ? coreInlet() : new double[]{Turbojet.T_REF, Turbojet.P_REF};
        for (DesignIssue i : compressor.check(in[0], in[1])) { issues.add(i.under("compressor")); }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        if (lpTurbine.coolingBleed() > 0) { issues.add(DesignIssue.invalid("lpTurbine.coolingBleed", "The low-pressure turbine's cooling air is not modelled: give it none")); }
        Turbofan.Sizing s;
        try { s = sizing(); }
        catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("cycle", e.getMessage())); return issues; }
        Turbofan.Point p = s.point();
        double t3 = p.tt3();
        if (hpTurbine.metalTemperature(control.turbineInletK(), t3) > hpTurbine.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("control.turbineInletK", "The high-pressure turbine blades would run above their material's service temperature (cooled, they take up to "
                    + Math.round(hpTurbine.maxInletTemperature(t3)) + " K)"));
        }
        if (lpTurbine.metalTemperature(p.tt45(), t3) > lpTurbine.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("lpTurbine.material", "The low-pressure turbine cannot take the " + Math.round(p.tt45()) + " K gas between the turbines"));
        }
        if (p.tt5() > nozzle.material().serviceTemperatureK()) { issues.add(DesignIssue.invalid("nozzle.material", "The nozzle cannot take the " + Math.round(p.tt5()) + " K core exhaust")); }
        if (mixer.mixes() && p.tt5() > mixer.material().serviceTemperatureK()) { issues.add(DesignIssue.invalid("mixer.material", "The mixer cannot take the " + Math.round(p.tt5()) + " K core exhaust")); }
        if (afterburner.fitted()) {
            if (!mixer.mixes()) { issues.add(DesignIssue.invalid("afterburner.fitted", "An afterburner burns the mixed stream: the engine needs a mixer")); }
            double liner = afterburner.linerTemperature(afterburner.maxTemperatureK(), p.tt5());
            if (liner > afterburner.material().serviceTemperatureK()) {
                issues.add(DesignIssue.invalid("afterburner.material", "The film-cooled liner would run at " + Math.round(liner) + " K, above its material's service temperature"));
            }
            if (liner > nozzle.material().serviceTemperatureK()) {
                issues.add(DesignIssue.invalid("nozzle.material", "The variable nozzle's petals would run at " + Math.round(liner) + " K with the afterburner lit"));
            }
        }
        double flux = totalAirflowKgPerS() / inlet.captureAreaM2();
        if (flux > Turbomachinery.CHOKED_FLUX) { issues.add(DesignIssue.invalid("inlet.captureAreaM2", "The inlet is too small: it chokes below the design airflow")); }
        else { DesignIssue.range(issues, "inletFlux", flux, 0, Turbomachinery.MACH_07_FLUX, "inlets sized for Mach 0.5-0.7 at the face"); }
        fan.checkFlow(issues, totalAirflowKgPerS());
        double core = compressor.airflowKgPerS() * (1 - hpTurbine.coolingBleed());
        turbine(issues, "hpTurbine", hpTurbine, p.highTurbinePowerW() / (hpTurbine.mechanicalEfficiency() * core), compressor.designRpm());
        turbine(issues, "lpTurbine", lpTurbine, p.lowTurbinePowerW() / (hpTurbine.mechanicalEfficiency() * compressor.airflowKgPerS()), fan.designRpm());
        return issues;
    }

    /** A turbine's stage loading and disc stress at its spool's speed. */
    static void turbine(List<DesignIssue> issues, String key, TurbineDesign t, double workJPerKg, double rpm) {
        double ut = Turbomachinery.tipSpeed(t.tipDiameterM(), rpm), loading = Turbomachinery.stageLoading(workJPerKg, t.stages(), ut);
        if (loading > JetEngineDesign.TURBINE_LOADING_LIMIT) { issues.add(DesignIssue.invalid(key + ".stages", "Too few turbine stages or too slow a spool: stage loading " + loading)); }
        else { DesignIssue.range(issues, key + "StageLoading", loading, 0, JetEngineDesign.TURBINE_LOADING, "turbine stage loadings, Smith's chart"); }
        double stress = t.discStress(ut);
        if (stress > t.material().yieldPa()) { issues.add(DesignIssue.invalid(key + ".tipDiameterM", "The turbine rotor would yield at this tip speed")); }
        else { DesignIssue.range(issues, key + "DiscStressOverYield", stress / t.material().yieldPa(), 0, 2.0 / 3, "rotors kept under two thirds of yield (cold strength)"); }
    }

    /**
     * Accessories and structure (FADEC, fuel and oil systems, starter, accessory gearbox, frames, bearing sumps, mounts,
     * fan containment), kg: the control's given mass, or 85 kg (1 - exp(-(w_core / 3 kg/s)^2)) plus 0.6 kg per kg/s of
     * total airflow. The fixed part is what every certified engine carries whatever its size; the size ramp leaves it out
     * of model-size cores. Set so that the CFM56-7B-class and FJ44-1A-class samples come within 15 % of their 2,366 and
     * 202 kg (statistical engine weight equations fall far below the small one: Raymer's gives 133 kg).
     */
    public double accessoriesMassKg() {
        if (!Double.isNaN(control.massGivenKg())) { return control.massGivenKg(); }
        double core = compressor.airflowKgPerS() / 3;
        return 85 * (1 - Math.exp(-core * core)) + 0.6 * totalAirflowKgPerS();
    }

    /** Mass of the engine, kg: the components, the nozzle and mixer for the sized throat, accessories and structure. */
    public double totalMassKg() {
        double a8 = sizing().nozzleThroatM2(), d8 = Math.sqrt(4 * a8 / Math.PI);
        return inlet.massKg() + fan.massKg() + compressor.massKg() + combustor.massKg() + hpTurbine.massKg() + lpTurbine.massKg() + mixer.massFor(1.4 * d8)
                + nozzle.massFor(a8) + accessoriesMassKg() + afterburner.massFor(a8);
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new TurbofanDesign(f.string("name", "turbofan"), component(f, "inlet", JetInletDesign::read), component(f, "fan", FanDesign::read),
                component(f, "compressor", CompressorDesign::read), component(f, "combustor", CombustorDesign::read), component(f, "hpTurbine", TurbineDesign::read),
                component(f, "lpTurbine", TurbineDesign::read), component(f, "mixer", MixerDesign::read), component(f, "nozzle", JetNozzleDesign::read),
                component(f, "control", JetControlDesign::read),
                f.has("afterburner") ? component(f, "afterburner", AfterburnerDesign::read) : AfterburnerDesign.none());
    }
    static <T> T component(Fields f, String key, java.util.function.BiFunction<String, Fields, T> reader) {
        Fields c = f.child(key);
        return reader.apply(c.string("name", key), c.has("parameters") ? c.child("parameters") : c);
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new Compiled(this, issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    public static final int STATE_FUEL = 0, STATE_LIT = 1, STATE_SURGE = 2, STATE_SCHEDULE = 3, STATE_SOLUTION = 4, STATE_REHEAT = STATE_SOLUTION + JetRuntime.SOLUTION;

    /** The sized cycle and its running lines. */
    public record Model(Turbofan.Sizing sizing, Turbofan.RunningLines lines) {}
    private static final Map<String, Model> MODELS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Model> eldest) { return size() > 32; }
    });

    /** The sized cycle and its running lines, built once per design (by its JSON) and shared by every vehicle and bench that runs it. */
    public Model model() {
        String key = Json.write(toJson());
        Model m = MODELS.get(key);
        if (m == null) {
            Turbofan.Sizing s = sizing();
            m = new Model(s, Turbofan.runningLines(s));
            MODELS.put(key, m);
        }
        return m;
    }

    /**
     * The fuel the control asks for at a quasi-steady point with the fan's speed target (fractions of design): the running
     * line's fuel at the target plus a proportional correction, within the turbine temperature limit and the acceleration
     * schedule, at least half the running line's fuel at the fan's speed (the bench runs the same rules).
     */
    public double command(Model m, Turbojet.Flight flight, Turbofan.Point p, double target) {
        Turbofan.Sizing s = m.sizing();
        double here = Turbofan.lineFuel(s, m.lines(), flight, p.lowSpeedFraction());
        double command = Turbofan.lineFuel(s, m.lines(), flight, target) + JetRuntime.SPEED_GAIN * (target - p.lowSpeedFraction()) * s.point().fuelKgPerS();
        return limited(m, flight, p, command, here);
    }

    /**
     * The fuel the control asks for while starting, below the core's idle: the running line's fuel at the start
     * schedule's core speed plus a correction on the core's speed error (the fan barely turns yet, so its speed says
     * nothing), within the same limits, at least half the running line's fuel at the core's speed.
     */
    public double startCommand(Model m, Turbojet.Flight flight, Turbofan.Point p, double coreTarget) {
        Turbofan.Sizing s = m.sizing();
        double here = Turbofan.lineFuel(s, m.lines(), flight, Turbofan.lineLowSpeed(m.lines(), flight, p.highSpeedFraction()));
        double command = Turbofan.lineFuel(s, m.lines(), flight, Turbofan.lineLowSpeed(m.lines(), flight, coreTarget))
                + JetRuntime.SPEED_GAIN * (coreTarget - p.highSpeedFraction()) * s.point().fuelKgPerS();
        return limited(m, flight, p, command, here);
    }

    private double limited(Model m, Turbojet.Flight flight, Turbofan.Point p, double command, double here) {
        Turbofan.Sizing s = m.sizing();
        double max = Double.POSITIVE_INFINITY;
        if (p.coreAirflowKgPerS() > 0) {
            max = Turbofan.fuelForTurbineInlet(s, p, control.turbineInletK());
            if (control.accelerationSchedule()) { max = Math.min(max, Turbofan.fuelForSurgeMargin(s, m.lines(), flight, p, compressor.surgeMargin() * (1 - control.surgeReserve()))); }
        }
        command = Math.clamp(command, 0.5 * here, Math.max(0.5 * here, max));
        if (p.lowSpeedFraction() > control.maxSpeedFraction() * 1.01) { command = 0.5 * here; }
        return command;
    }

    private static final class Compiled implements Part {
        private final TurbofanDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        private final double lowInertia, highInertia, lowOmega, highOmega, halfLength;
        private volatile Model model;

        Compiled(TurbofanDesign design, List<DesignIssue> issues) {
            this.design = design; this.issues = issues;
            double m = design.totalMassKg(), length = design.length(), d = design.diameter();
            mass = MassProperties.of(m, Vec3d.ZERO, InertiaTensor.box(m, new Vec3d(length, d, d)));
            lowInertia = design.lowInertia(); highInertia = design.highInertia();
            lowOmega = design.lowSpeed(); highOmega = design.highSpeed();
            halfLength = length / 2;
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return mass; }
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return issues; }
        @Override public List<Rotor> rotors() { return List.of(new Rotor(Vec3d.X, lowInertia, true), new Rotor(Vec3d.X, highInertia, true)); }
        /** Without an afterburner, the inputs and states of a turbofan before 0.10. */
        @Override public List<String> inputs() { return design.afterburner().fitted() ? List.of("throttle", "ignition", "reheat") : List.of("throttle", "ignition"); }
        @Override public int internalStates() { return design.afterburner().fitted() ? STATE_REHEAT + 1 : STATE_REHEAT; }
        /** With an afterburner (0.10): the reheat's share and the fan's speed (JetEngineDesign.SHOWN_REHEAT, SHOWN_SPEED). */
        @Override public int shownValues() { return (design.afterburner().fitted() ? 2 : 0) + Exhaust.COUNT; }
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
            JetRuntime.Air air = JetRuntime.air(step, new Vec3d(halfLength, 0, 0));
            Turbojet.Flight flight = air.flight();
            double omegaL = step.rotorSpeed(0), omegaH = step.rotorSpeed(1);
            double nL = Math.max(0, omegaL / lowOmega), nH = Math.max(0, omegaH / highOmega);
            double throttle = Math.clamp(step.input("throttle", 0), 0, 1), ignition = step.input("ignition", 1);
            double fuel = Math.max(0, step.internal(STATE_FUEL)), surge = Math.max(0, step.internal(STATE_SURGE) - dt);
            double coreIdle = Turbofan.lineHighSpeed(m.lines(), flight, c.idleFraction()), cutout = JetRuntime.cutout(coreIdle);
            int burner = JetRuntime.burner(step.internal(STATE_LIT), ignition, JetRuntime.fed(step), air.drowned(), nH, c.lightOffFraction(), cutout);
            boolean lit = burner > 0, running = burner > 1;
            double schedule = JetRuntime.schedule(step.internal(STATE_SCHEDULE), nH, c.startRate(), dt, lit);
            if (!lit) { fuel = 0; }
            Turbofan.Point p = Turbofan.quasiSteady(m.sizing(), m.lines(), flight, nL, nH, air.drowned() ? 0 : fuel, JetRuntime.solution(step, STATE_SOLUTION));
            // the afterburner (0.10)
            dev.mcmod.aerocore.api.engine.Reheat.Design reheat = design.afterburner().reheat();
            double reheatInput = Math.clamp(step.input("reheat", 0), 0, 1);
            boolean reheatLit = reheat != null && running && !air.drowned() && reheatInput > 0.02 && p.airflowKgPerS() > 0
                    && nL >= (step.internal(STATE_REHEAT) > 0.5 ? JetEngineDesign.REHEAT_OFF : JetEngineDesign.REHEAT_ON) * c.maxSpeedFraction();
            dev.mcmod.aerocore.api.engine.Reheat.Point rp = null;
            if (reheatLit) {
                rp = dev.mcmod.aerocore.api.engine.Reheat.turbofan(m.sizing(), p, flight, reheat, p.tt5() + reheatInput * (reheat.maxTemperatureK() - p.tt5()));
            }
            double reheatFuel = rp == null ? 0 : rp.fuelKgPerS();
            JetRuntime.burn(step, c.acceptedFluids(), ignition, air.drowned() ? 0 : fuel + reheatFuel);
            if (lit && p.surgeMarginUsed() > design.compressor().surgeMargin()) { surge = JetRuntime.SURGE_TIME; }
            double share = surge > 0 ? JetRuntime.SURGE_SHARE : 1;
            // the spools
            double wL = Math.max(omegaL, 0.05 * lowOmega), wH = Math.max(omegaH, 0.05 * highOmega);
            double lowTurbine = p.lowTurbinePowerW() * share, fanPower = p.fanPowerW() * share;
            double highTurbine = p.highTurbinePowerW() * share, compressorPower = p.compressorPowerW() * share;
            boolean starter = ignition >= 0.5 && !running && nH < cutout;
            step.rotorTorque(0, Turbomachinery.spoolTorque(lowTurbine, fanPower, wL), -(lowTurbine + fanPower) / (wL * wL), false);
            step.rotorTorque(1, Turbomachinery.spoolTorque(highTurbine, compressorPower, wH) + (starter ? JetRuntime.starter(c.starterTorqueNm(), nH, cutout) : 0),
                    -(highTurbine + compressorPower) / (wH * wH), false);
            double thrust = air.drowned() ? 0 : (rp != null ? rp.thrustN() : p.thrustN()) * share;
            step.force(new AppliedForce(AppliedForce.Kind.THRUST, step.offset(new Vec3d(-halfLength, 0, 0)), step.axis(Vec3d.X).scale(thrust)));
            if (reheat != null) {
                step.setInternal(STATE_REHEAT, reheatLit ? 1 : 0);
                step.show(JetEngineDesign.SHOWN_REHEAT, reheatLit ? reheatInput : 0);
                step.show(JetEngineDesign.SHOWN_SPEED, nL);
            }
            // 0.11: the exhaust for the plume; the model does not give the nozzles' exit, so the core's is taken as a convergent
            // nozzle at about the fan's total pressure (the low-pressure turbine expands the core gas to roughly that)
            double[] nozzle = Exhaust.convergent(p.fanPressureRatio() * p.pt2() / flight.pressurePa());
            Exhaust.show(step, Exhaust.first(design), lit && !air.drowned(), rp != null ? rp.temperatureK() : p.tt5(), nozzle[0], nozzle[1]);
            step.diagnostic("reheatK", rp == null ? 0 : rp.temperatureK());
            step.diagnostic("reheatFuelKgPerS", reheatFuel);
            // the fuel control, for the next step
            double command = 0;
            if (lit) {
                double target = c.idleFraction() + throttle * (c.maxSpeedFraction() - c.idleFraction());
                // below the core's idle the start schedule sets the core's speed
                command = nH < coreIdle ? design.startCommand(m, flight, p, Math.min(schedule, coreIdle)) : design.command(m, flight, p, target);
            }
            fuel += (command - fuel) * Math.min(1, dt / JetRuntime.FUEL_LAG);
            step.setInternal(STATE_FUEL, lit ? fuel : 0);
            step.setInternal(STATE_LIT, burner);
            step.setInternal(STATE_SURGE, surge);
            step.setInternal(STATE_SCHEDULE, schedule);
            JetRuntime.keep(step, STATE_SOLUTION, p.solution());
            step.diagnostic("speedFraction", nL);
            step.diagnostic("coreSpeedFraction", nH);
            step.diagnostic("rpm", omegaL * 30 / Math.PI);
            step.diagnostic("coreRpm", omegaH * 30 / Math.PI);
            step.diagnostic("thrustN", thrust);
            step.diagnostic("fuelKgPerS", p.fuelKgPerS());
            step.diagnostic("turbineInletK", p.tt4());
            step.diagnostic("exhaustK", p.tt5());
            step.diagnostic("bypassRatio", p.bypassRatio());
            step.diagnostic("pressureRatio", p.overallPressureRatio());
            step.diagnostic("airflowKgPerS", p.airflowKgPerS());
            step.diagnostic("surgeMarginUsed", p.surgeMarginUsed());
            step.diagnostic("lit", lit ? 1 : 0);
            step.diagnostic("starter", starter ? 1 : 0);
            step.diagnostic("surging", surge > 0 ? 1 : 0);
        }
    }
}
