package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.HullDynamics;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
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
 * A single-spool turbojet assembled from designed components: inlet, compressor, combustor, turbine, nozzle and fuel
 * control. It points along the part's +X axis (inlet forward, jet aft) and pushes forward; its spool is a rotor about
 * +X, so spinning it up kicks the vehicle the other way.
 * <p>
 * The cycle (Turbojet) is sized at sea-level static from the compressor's pressure ratio and airflow and the control's
 * turbine inlet temperature; the spool's design speed is the compressor's. In flight the spool follows its fuel
 * (Turbojet.quasiSteady): thrust including ram drag, turbine and compressor power, surge margin used. Inputs:
 * "throttle" 0..1 and "ignition" (unbound = on). Internal states: fuel flow (kg/s), burner (0 out, 1 lit while the
 * starter still helps, 2 running on its own), surge time left (s), the start schedule's speed (fraction). Starting
 * follows JetControlDesign's rules (JetRuntime): tens of seconds, as full-size engines start.
 * <p>
 * An afterburner (0.10, AeroCore's Reheat) is an optional component: input "reheat" 0..1 sets its temperature from the
 * dry exhaust's to its largest; it lights only while the engine runs at 90 % of its top speed or more (a rule: real
 * afterburners are selected at military power) and goes out below 85 %. Internal state 4: lit.
 */
public record JetEngineDesign(String name, JetInletDesign inlet, CompressorDesign compressor, CombustorDesign combustor, TurbineDesign turbine,
                              JetNozzleDesign nozzle, JetControlDesign control, AfterburnerDesign afterburner) implements PartDesign {
    public static final String TYPE = "hdfv:jet_engine";
    /** Turbine stage loading (work over tip speed squared) of practical turbines (Smith's chart) and where none works. */
    public static final double TURBINE_LOADING = 2.5, TURBINE_LOADING_LIMIT = 4;

    public JetEngineDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{inlet, compressor, combustor, turbine, nozzle, control, afterburner}) { Objects.requireNonNull(o, "component"); }
    }

    /** Without an afterburner (as before 0.10). */
    public JetEngineDesign(String name, JetInletDesign inlet, CompressorDesign compressor, CombustorDesign combustor, TurbineDesign turbine,
                           JetNozzleDesign nozzle, JetControlDesign control) {
        this(name, inlet, compressor, combustor, turbine, nozzle, control, AfterburnerDesign.none());
    }

    /** The fluids it burns (0.8; its fuel control's). */
    public java.util.Set<String> acceptedFluids() { return control.acceptedFluids(); }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("inlet", inlet); m.put("compressor", compressor); m.put("combustor", combustor); m.put("turbine", turbine);
        m.put("nozzle", nozzle); m.put("control", control); m.put("afterburner", afterburner);
        return m;
    }

    /** The cycle these components make. */
    public Turbojet.Design cycle() {
        return new Turbojet.Design(compressor.pressureRatio(), compressor.airflowKgPerS(), compressor.polytropicEfficiency(), control.turbineInletK(),
                1 - combustor.pressureLoss(), combustor.efficiency(), turbine.polytropicEfficiency(), turbine.mechanicalEfficiency(), turbine.coolingBleed(),
                nozzle.pressureRetention(), nozzle.convergentDivergent(), nozzle.convergentDivergent() ? nozzle.exitAreaRatio() : 1, inlet.recovery(),
                inlet.kind(), control.fuelModel());
    }
    /** The cycle sized at sea-level static. */
    public Turbojet.Sizing sizing() { return Turbojet.size(cycle(), Turbojet.Flight.sea(0)); }

    /** Design spool speed, rad/s. */
    public double designSpeed() { return compressor.designRpm() * Math.PI / 30; }
    public double turbineTipSpeed() { return Turbomachinery.tipSpeed(turbine.tipDiameterM(), compressor.designRpm()); }
    /** Spool moment of inertia (compressor and turbine rotors), kg m^2. */
    public double spoolInertia() { return compressor.rotorInertia() + turbine.rotorInertia(); }
    /** Overall length, m: inlet, compressor (0.06 diameters per axial stage, 0.4 per centrifugal), combustor, turbine (0.1 diameters per stage), tailpipe. */
    public double length() {
        double c = compressor.lengthM();
        return inlet.lengthM() + c + combustor.addedLengthM() + 0.1 * turbine.tipDiameterM() * turbine.stages() + nozzle.tailpipeLengthM();
    }
    public double diameter() { return Math.max(Math.max(compressor.tipDiameterM(), turbine.tipDiameterM()), combustor.casingDiameterM()) * 1.1; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        Turbojet.Sizing s;
        try { s = sizing(); }
        catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("cycle", e.getMessage())); return issues; }
        Turbojet.Point p = s.point();
        double t3 = p.tt3();
        if (turbine.metalTemperature(control.turbineInletK(), t3) > turbine.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("control.turbineInletK", "The turbine blades would run above their material's service temperature (cooled, they take up to "
                    + Math.round(turbine.maxInletTemperature(t3)) + " K)"));
        }
        if (p.tt5() > nozzle.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("nozzle.material", "The tailpipe material cannot take the " + Math.round(p.tt5()) + " K exhaust"));
        }
        if (afterburner.fitted()) {
            double liner = afterburner.linerTemperature(afterburner.maxTemperatureK(), p.tt5());
            if (liner > afterburner.material().serviceTemperatureK()) {
                issues.add(DesignIssue.invalid("afterburner.material", "The film-cooled liner would run at " + Math.round(liner) + " K, above its material's service temperature"));
            }
            if (liner > nozzle.material().serviceTemperatureK()) {
                issues.add(DesignIssue.invalid("nozzle.material", "The variable nozzle's petals would run at " + Math.round(liner) + " K with the afterburner lit"));
            }
        }
        double flux = compressor.airflowKgPerS() / inlet.captureAreaM2();
        if (flux > Turbomachinery.CHOKED_FLUX) { issues.add(DesignIssue.invalid("inlet.captureAreaM2", "The inlet is too small: it chokes below the design airflow")); }
        else { DesignIssue.range(issues, "inletFlux", flux, 0, Turbomachinery.MACH_07_FLUX, "inlets sized for Mach 0.5-0.7 at the face"); }
        double ut = turbineTipSpeed(), wt = p.turbinePowerW() / (turbine.mechanicalEfficiency() * p.airflowKgPerS() * (1 - turbine.coolingBleed()));
        double loading = Turbomachinery.stageLoading(wt, turbine.stages(), ut);
        if (loading > TURBINE_LOADING_LIMIT) { issues.add(DesignIssue.invalid("turbine.stages", "Too few turbine stages or too slow a spool: stage loading " + loading)); }
        else { DesignIssue.range(issues, "turbineStageLoading", loading, 0, TURBINE_LOADING, "turbine stage loadings, Smith's chart"); }
        double stress = turbine.discStress(ut);
        if (stress > turbine.material().yieldPa()) { issues.add(DesignIssue.invalid("turbine.tipDiameterM", "The turbine rotor would yield at this tip speed")); }
        else { DesignIssue.range(issues, "turbineDiscStressOverYield", stress / turbine.material().yieldPa(), 0, 2.0 / 3, "rotors kept under two thirds of yield (cold strength)"); }
        return issues;
    }

    /** Mass of the engine, kg. */
    public double totalMassKg() {
        double a8 = sizing().nozzleThroatM2();
        return inlet.massKg() + compressor.massKg() + combustor.massKg() + turbine.massKg() + nozzle.massFor(a8) + control.massFor(compressor.airflowKgPerS())
                + afterburner.massFor(a8);
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new JetEngineDesign(f.string("name", "jet"), component(f, "inlet", JetInletDesign::read), component(f, "compressor", CompressorDesign::read),
                component(f, "combustor", CombustorDesign::read), component(f, "turbine", TurbineDesign::read), component(f, "nozzle", JetNozzleDesign::read),
                component(f, "control", JetControlDesign::read),
                f.has("afterburner") ? component(f, "afterburner", AfterburnerDesign::read) : AfterburnerDesign.none());
    }
    private static <T> T component(Fields f, String key, java.util.function.BiFunction<String, Fields, T> reader) {
        Fields c = f.child(key);
        return reader.apply(c.string("name", key), c.has("parameters") ? c.child("parameters") : c);
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new Compiled(this, issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    public static final int STATE_FUEL = 0, STATE_LIT = 1, STATE_SURGE = 2, STATE_SCHEDULE = 3, STATE_REHEAT = 4;
    /** Shown values with an afterburner (0.10): reheat share, spool speed (fraction of design). */
    public static final int SHOWN_REHEAT = 0, SHOWN_SPEED = 1;
    /** The afterburner lights from this share of the top speed and goes out below the second (a rule). */
    public static final double REHEAT_ON = 0.9, REHEAT_OFF = 0.85;
    /** Fuel valve lag, s; how long a surge lasts, s; fuel gain per unit speed error (rules of the control, as JetRuntime's). */
    static final double FUEL_LAG = JetRuntime.FUEL_LAG, SURGE_TIME = JetRuntime.SURGE_TIME, SPEED_GAIN = JetRuntime.SPEED_GAIN;

    /** The sized cycle and its running lines. */
    public record Model(Turbojet.Sizing sizing, Turbojet.RunningLines lines) {}
    private static final Map<String, Model> MODELS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Model> eldest) { return size() > 32; }
    });

    /** The sized cycle and its running lines, built once per design (by its JSON) and shared by every vehicle and bench that runs it. */
    public Model model() {
        String key = Json.write(toJson());
        Model m = MODELS.get(key);
        if (m == null) {
            Turbojet.Sizing s = sizing();
            m = new Model(s, Turbojet.runningLines(s));
            MODELS.put(key, m);
        }
        return m;
    }

    private static final class Compiled implements Part {
        private final JetEngineDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        private final double inertia, omegaDesign, halfLength;
        private volatile Model model;

        Compiled(JetEngineDesign design, List<DesignIssue> issues) {
            this.design = design; this.issues = issues;
            double m = design.totalMassKg(), length = design.length(), d = design.diameter();
            mass = MassProperties.of(m, Vec3d.ZERO, InertiaTensor.box(m, new Vec3d(length, d, d)));
            inertia = design.spoolInertia();
            omegaDesign = design.designSpeed();
            halfLength = length / 2;
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return mass; }
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return issues; }
        @Override public List<Rotor> rotors() { return List.of(new Rotor(Vec3d.X, inertia, true)); }
        /** Without an afterburner, the inputs and states of a turbojet before 0.10. */
        @Override public List<String> inputs() { return design.afterburner().fitted() ? List.of("throttle", "ignition", "reheat") : List.of("throttle", "ignition"); }
        @Override public int internalStates() { return design.afterburner().fitted() ? STATE_REHEAT + 1 : STATE_REHEAT; }
        /** With an afterburner (0.10): the reheat's share (0 out) and the spool's speed, for its flame and sound; then (0.11) the exhaust (Exhaust). */
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
            Turbojet.Sizing s = m.sizing();
            JetControlDesign c = design.control();
            double dt = step.dt();
            Vec3d inletPoint = new Vec3d(halfLength, 0, 0), axis = step.axis(Vec3d.X);
            HullDynamics.Water sample = step.environment().point(step.point(inletPoint));
            FluidState air = sample.air();
            boolean drowned = sample.hasWater() && step.point(inletPoint).y() < sample.surfaceY();
            double t0 = air.speedOfSoundMps() * air.speedOfSoundMps() / (1.4 * Gas.AIR.gasConstant());
            double p0 = air.densityKgPerM3() * Gas.AIR.gasConstant() * t0;
            double v0 = Math.max(0, step.velocity(inletPoint).subtract(air.velocity()).dot(axis));
            Turbojet.Flight flight = new Turbojet.Flight(t0, p0, v0);
            double omega = step.rotorSpeed(0), n = Math.max(0, omega / omegaDesign);
            double throttle = Math.clamp(step.input("throttle", 0), 0, 1), ignition = step.input("ignition", 1);
            double fuel = Math.max(0, step.internal(STATE_FUEL));
            double surge = Math.max(0, step.internal(STATE_SURGE) - dt), cutout = JetRuntime.cutout(c.idleFraction());
            int burner = JetRuntime.burner(step.internal(STATE_LIT), ignition, JetRuntime.fed(step), drowned, n, c.lightOffFraction(), cutout);
            boolean lit = burner > 0, running = burner > 1;
            double schedule = JetRuntime.schedule(step.internal(STATE_SCHEDULE), n, c.startRate(), dt, lit);
            // the fuel control
            Turbojet.RunningLines lines = m.lines();
            double command = 0;
            if (lit) {
                double target = c.idleFraction() + throttle * (c.maxSpeedFraction() - c.idleFraction());
                if (n < c.idleFraction()) { target = Math.min(target, schedule); }   // the start schedule below idle
                double here = Turbojet.lineFuel(s, lines, flight, n);
                command = Turbojet.lineFuel(s, lines, flight, target) + SPEED_GAIN * (target - n) * s.point().fuelKgPerS();
                double maxFuel = Turbojet.fuelForTurbineInlet(s, lines, flight, n, c.turbineInletK());
                if (c.accelerationSchedule()) {
                    double allowed = design.compressor().surgeMargin() * (1 - c.surgeReserve());
                    maxFuel = Math.min(maxFuel, Turbojet.fuelForSurgeMargin(s, lines, flight, n, allowed));
                }
                command = Math.clamp(command, 0.5 * here, Math.max(0.5 * here, maxFuel));
                if (n > c.maxSpeedFraction() * 1.01) { command = 0.5 * here; }
            }
            fuel += (command - fuel) * Math.min(1, dt / FUEL_LAG);
            if (!lit) { fuel = 0; }
            Turbojet.Point p = Turbojet.quasiSteady(s, lines, flight, n, drowned ? 0 : fuel);
            // the afterburner (0.10): lit at military power and above, its temperature between the dry exhaust's and its largest
            dev.mcmod.aerocore.api.engine.Reheat.Design reheat = design.afterburner().reheat();
            double reheatInput = Math.clamp(step.input("reheat", 0), 0, 1), top = c.maxSpeedFraction();
            boolean reheatLit = reheat != null && running && !drowned && reheatInput > 0.02
                    && n >= (step.internal(STATE_REHEAT) > 0.5 ? REHEAT_OFF : REHEAT_ON) * top;
            dev.mcmod.aerocore.api.engine.Reheat.Point rp = null;
            if (reheatLit) { rp = dev.mcmod.aerocore.api.engine.Reheat.turbojet(s, p, flight, reheat, p.tt5() + reheatInput * (reheat.maxTemperatureK() - p.tt5())); }
            double reheatFuel = rp == null ? 0 : rp.fuelKgPerS();
            JetRuntime.burn(step, c.acceptedFluids(), ignition, drowned ? 0 : fuel + reheatFuel);
            if (lit && p.surgeMarginUsed() > design.compressor().surgeMargin()) { surge = SURGE_TIME; }
            double share = surge > 0 ? 0.3 : 1;   // a surging compressor loses most of its flow (a rule for the stall's effect)
            double turbine = p.turbinePowerW() * share, compressor = p.compressorPowerW() * share;
            double w = Math.max(omega, 0.05 * omegaDesign);
            double torque = Turbomachinery.spoolTorque(turbine, compressor, w);
            boolean starter = ignition >= 0.5 && !running && n < cutout;   // turns the spool to light-off and helps it up to cut-out
            if (starter) { torque += JetRuntime.starter(c.starterTorqueNm(), n, cutout); }
            double slope = -(turbine + compressor) / (w * w);
            step.rotorTorque(0, torque, slope, false);
            double thrust = drowned ? 0 : (rp != null ? rp.thrustN() : p.thrustN()) * share;
            step.force(new AppliedForce(AppliedForce.Kind.THRUST, step.offset(new Vec3d(-halfLength, 0, 0)), axis.scale(thrust)));
            step.setInternal(STATE_FUEL, fuel);
            step.setInternal(STATE_LIT, burner);
            step.setInternal(STATE_SURGE, surge);
            step.setInternal(STATE_SCHEDULE, schedule);
            if (reheat != null) {
                step.setInternal(STATE_REHEAT, reheatLit ? 1 : 0);
                step.show(SHOWN_REHEAT, reheatLit ? reheatInput : 0);
                step.show(SHOWN_SPEED, n);
            }
            // 0.11: the exhaust for the plume: the gas leaving the nozzle (reheated when lit), its exit pressure the dry nozzle's
            double exitK = rp != null ? rp.temperatureK() : p.tt5(), exitSpeed = rp != null ? rp.exitSpeedMps() : p.exitSpeedMps();
            Exhaust.show(step, Exhaust.first(design), lit && !drowned, exitK, Exhaust.machFromSpeed(exitSpeed, exitK), p.exitPressurePa() / p0);
            step.diagnostic("reheatK", rp == null ? 0 : rp.temperatureK());
            step.diagnostic("reheatFuelKgPerS", reheatFuel);
            step.diagnostic("speedFraction", n);
            step.diagnostic("rpm", omega * 30 / Math.PI);
            step.diagnostic("thrustN", thrust);
            step.diagnostic("fuelKgPerS", fuel);
            step.diagnostic("turbineInletK", p.tt4());
            step.diagnostic("exhaustK", p.tt5());
            step.diagnostic("pressureRatio", p.pressureRatio());
            step.diagnostic("airflowKgPerS", p.airflowKgPerS());
            step.diagnostic("surgeMarginUsed", p.surgeMarginUsed());
            step.diagnostic("lit", lit ? 1 : 0);
            step.diagnostic("starter", starter ? 1 : 0);
            step.diagnostic("surging", surge > 0 ? 1 : 0);
        }
    }
}
