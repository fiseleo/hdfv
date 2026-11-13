package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.TurboRamjet;
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
 * A turbo-ramjet (渦輪衝壓引擎, 0.10; AeroCore's TurboRamjet, the J58's way): a turbojet core behind a supersonic inlet,
 * with an afterburner and a bleed bypass that above a Mach number sends part of the air round the core into the
 * afterburner, which at Mach 3 then works as a ramjet's combustor. It points along the part's +X axis; its spool is a
 * rotor about +X. The core runs by the turbojet's rules (JetEngineDesign, JetRuntime: starting, idle, the fuel
 * control); "reheat" 0..1 sets the afterburner's temperature from the gas it gets to its largest, lit from 90 % of top
 * speed. Inputs "throttle", "ignition", "reheat". Internal states as the turbojet's.
 */
public record TurboRamjetDesign(String name, SupersonicInletDesign inlet, CompressorDesign compressor, CombustorDesign combustor, TurbineDesign turbine,
                                JetNozzleDesign nozzle, JetControlDesign control, AfterburnerDesign afterburner, BypassDesign bypass) implements PartDesign {
    public static final String TYPE = "hdfv:turbo_ramjet";

    public TurboRamjetDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{inlet, compressor, combustor, turbine, nozzle, control, afterburner, bypass}) { Objects.requireNonNull(o, "component"); }
    }

    public java.util.Set<String> acceptedFluids() { return control.acceptedFluids(); }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("inlet", inlet); m.put("compressor", compressor); m.put("combustor", combustor); m.put("turbine", turbine); m.put("nozzle", nozzle);
        m.put("control", control); m.put("afterburner", afterburner); m.put("bypass", bypass);
        return m;
    }

    /** The core turbojet: its face fed through a multi-shock inlet at the subsonic diffuser's recovery. */
    public Turbojet.Design core() {
        return new Turbojet.Design(compressor.pressureRatio(), compressor.airflowKgPerS(), compressor.polytropicEfficiency(), control.turbineInletK(),
                1 - combustor.pressureLoss(), combustor.efficiency(), turbine.polytropicEfficiency(), turbine.mechanicalEfficiency(), turbine.coolingBleed(),
                nozzle.pressureRetention(), nozzle.convergentDivergent(), nozzle.convergentDivergent() ? nozzle.exitAreaRatio() : 1, inlet.subsonicRecovery(),
                Turbojet.Inlet.RAMP, control.fuelModel());
    }

    public TurboRamjet.Design cycle() {
        AfterburnerDesign a = afterburner;
        return new TurboRamjet.Design(core(), new dev.mcmod.aerocore.api.engine.Reheat.Design(a.maxTemperatureK(), a.efficiency(), a.inletMach(), a.maxThroatRatio()),
                inlet.design(), inlet.captureAreaM2(), bypass.openMach(), bypass.fullMach(), bypass.maxRatio(), bypass.bleedShare(), bypass.ductRecovery());
    }

    public TurboRamjet.Sizing sizing() { return TurboRamjet.size(cycle()); }

    public double designSpeed() { return compressor.designRpm() * Math.PI / 30; }
    public double spoolInertia() { return compressor.rotorInertia() + turbine.rotorInertia(); }
    public double length() {
        return inlet.lengthM() + compressor.lengthM() + combustor.addedLengthM() + 0.1 * turbine.tipDiameterM() * turbine.stages() + afterburner.lengthM() + nozzle.tailpipeLengthM();
    }
    public double diameter() { return Math.max(Math.max(compressor.tipDiameterM(), turbine.tipDiameterM()), inlet.captureDiameterM()) * 1.15; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (!afterburner.fitted()) { issues.add(DesignIssue.invalid("afterburner.fitted", "A turbo-ramjet burns its bypass air in its afterburner: it needs one")); }
        if (!nozzle.convergentDivergent()) { DesignIssue.range(issues, "nozzleConvergentDivergent", 0, 1, 1, "turbo-ramjets (a convergent nozzle wastes the high pressure ratio)"); }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        TurboRamjet.Sizing s;
        try { s = sizing(); } catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("cycle", e.getMessage())); return issues; }
        Turbojet.Point p = s.core().point();
        if (turbine.metalTemperature(control.turbineInletK(), p.tt3()) > turbine.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("control.turbineInletK", "The turbine blades would run above their material's service temperature"));
        }
        double ram = RamjetDesign.flight(inlet.designMach(), 24000).totalTemperature();
        if (ram > inlet.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("inlet.material", "The inlet's skin cannot take the " + Math.round(ram) + " K ram air at its design Mach number"));
        }
        if (ram > compressor.material().serviceTemperatureK()) {
            DesignIssue.range(issues, "compressorInletK", ram, 0, compressor.material().serviceTemperatureK(), "the compressor's material at the design Mach number");
        }
        double liner = afterburner.linerTemperature(afterburner.maxTemperatureK(), p.tt5());
        if (liner > afterburner.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("afterburner.material", "The film-cooled liner would run at " + Math.round(liner) + " K, above its material's service temperature"));
        }
        if (liner > nozzle.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("nozzle.material", "The variable nozzle's petals would run at " + Math.round(liner) + " K with the afterburner lit"));
        }
        return issues;
    }

    /** The core's length from the compressor face to the turbine's exit, m (what the bypass ducts run along). */
    public double coreLengthM() { return compressor.lengthM() + combustor.addedLengthM() + 0.1 * turbine.tipDiameterM() * turbine.stages(); }

    public double totalMassKg() {
        double a8 = sizing().core().nozzleThroatM2();
        return inlet.massKg() + compressor.massKg() + combustor.massKg() + turbine.massKg() + nozzle.massFor(a8) + control.massFor(compressor.airflowKgPerS())
                + afterburner.massFor(a8) + bypass.massFor(compressor.tipDiameterM(), coreLengthM());
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new TurboRamjetDesign(f.string("name", "turbo-ramjet"), TurbofanDesign.component(f, "inlet", SupersonicInletDesign::read),
                TurbofanDesign.component(f, "compressor", CompressorDesign::read), TurbofanDesign.component(f, "combustor", CombustorDesign::read),
                TurbofanDesign.component(f, "turbine", TurbineDesign::read), TurbofanDesign.component(f, "nozzle", JetNozzleDesign::read),
                TurbofanDesign.component(f, "control", JetControlDesign::read), TurbofanDesign.component(f, "afterburner", AfterburnerDesign::read),
                TurbofanDesign.component(f, "bypass", BypassDesign::read));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new Compiled(this, issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    /** The sized engine and its core's running lines. */
    public record Model(TurboRamjet.Sizing sizing, Turbojet.RunningLines lines) {}
    private static final Map<String, Model> MODELS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Model> eldest) { return size() > 16; }
    });

    public Model model() {
        return MODELS.computeIfAbsent(Json.write(toJson()), k -> {
            TurboRamjet.Sizing s = sizing();
            return new Model(s, Turbojet.runningLines(s.core()));
        });
    }

    public static final int STATE_FUEL = JetEngineDesign.STATE_FUEL, STATE_LIT = JetEngineDesign.STATE_LIT, STATE_SURGE = JetEngineDesign.STATE_SURGE,
            STATE_SCHEDULE = JetEngineDesign.STATE_SCHEDULE, STATE_REHEAT = JetEngineDesign.STATE_REHEAT;

    private static final class Compiled implements Part {
        private final TurboRamjetDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        private final double inertia, omegaDesign, halfLength;
        /** 0.25: seconds of wear a second of running is (DamagePace when this was compiled). */
        private final double wearPace = dev.mcmod.hdfv.damage.DamagePace.wear();
        private volatile Model model;

        Compiled(TurboRamjetDesign design, List<DesignIssue> issues) {
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
        @Override public List<String> inputs() { return List.of("throttle", "ignition", "reheat"); }
        @Override public int internalStates() { return 5; }
        /** The reheat's share and the spool's speed (JetEngineDesign.SHOWN_REHEAT, SHOWN_SPEED), for its flame and sound. */
        @Override public int shownValues() { return 2 + Exhaust.COUNT; }
        @Override public java.util.List<String> conditions() { return JetRuntime.CONDITIONS; }
        @Override public void warmUp() { model(); }

        Model model() {
            Model m = model;
            if (m == null) { synchronized (this) { if (model == null) { model = design.model(); } m = model; } }
            return m;
        }

        @Override public void loads(PartStep step) {
            Model m = model();
            TurboRamjet.Sizing s = m.sizing();
            Turbojet.Sizing core = s.core();
            JetControlDesign c = design.control();
            double dt = step.dt();
            JetRuntime.Air air = JetRuntime.air(step, new Vec3d(halfLength, 0, 0));
            Turbojet.Flight flight = air.flight();
            boolean drowned = air.drowned();
            double omega = step.rotorSpeed(0), n = Math.max(0, omega / omegaDesign);
            double throttle = Math.clamp(step.input("throttle", 0), 0, 1), ignition = step.input("ignition", 1);
            // 0.25: condition (a dead engine is as one switched off; a new one takes no branch)
            JetRuntime.Health health = JetRuntime.health(step);
            if (health.dead()) { ignition = 0; }
            double fuel = Math.max(0, step.internal(STATE_FUEL)), surge = Math.max(0, step.internal(STATE_SURGE) - dt), cutout = JetRuntime.cutout(c.idleFraction());
            int burner = JetRuntime.burner(step.internal(STATE_LIT), ignition, JetRuntime.fed(step), drowned, n, c.lightOffFraction(), cutout);
            boolean lit = burner > 0, running = burner > 1;
            double schedule = JetRuntime.schedule(step.internal(STATE_SCHEDULE), n, c.startRate(), dt, lit);
            double command = 0;
            if (lit) {
                double target = c.idleFraction() + throttle * (c.maxSpeedFraction() - c.idleFraction());
                if (n < c.idleFraction()) { target = Math.min(target, schedule); }
                double here = Turbojet.lineFuel(core, m.lines(), flight, n);
                command = Turbojet.lineFuel(core, m.lines(), flight, target) + JetRuntime.SPEED_GAIN * (target - n) * core.point().fuelKgPerS();
                double maxFuel = Turbojet.fuelForTurbineInlet(core, m.lines(), flight, n, c.turbineInletK());
                if (c.accelerationSchedule()) { maxFuel = Math.min(maxFuel, Turbojet.fuelForSurgeMargin(core, m.lines(), flight, n, design.compressor().surgeMargin() * (1 - c.surgeReserve()))); }
                command = Math.clamp(command, 0.5 * here, Math.max(0.5 * here, maxFuel));
                if (n > c.maxSpeedFraction() * 1.01) { command = 0.5 * here; }
            }
            fuel += (command - fuel) * Math.min(1, dt / JetRuntime.FUEL_LAG);
            if (!lit) { fuel = 0; }
            double reheatInput = Math.clamp(step.input("reheat", 0), 0, 1);
            boolean reheatLit = running && !drowned && reheatInput > 0.02
                    && n >= (step.internal(STATE_REHEAT) > 0.5 ? JetEngineDesign.REHEAT_OFF : JetEngineDesign.REHEAT_ON) * c.maxSpeedFraction();
            double maxReheat = design.afterburner().maxTemperatureK();
            TurboRamjet.Point p = TurboRamjet.quasiSteady(s, m.lines(), flight, n, drowned ? 0 : fuel, 0);
            if (reheatLit) {
                double entry = p.core().tt5();
                p = TurboRamjet.quasiSteady(s, m.lines(), flight, n, fuel, entry + reheatInput * (maxReheat - entry));
            }
            JetRuntime.burn(step, c.acceptedFluids(), ignition, drowned ? 0 : p.fuelKgPerS());
            Turbojet.Point cp = p.core();
            if (lit && cp.surgeMarginUsed() > design.compressor().surgeMargin()) { surge = JetRuntime.SURGE_TIME; }
            double share = surge > 0 ? JetRuntime.SURGE_SHARE : 1;
            double turbinePower = cp.turbinePowerW() * share, compressorPower = cp.compressorPowerW() * share;
            double w = Math.max(omega, 0.05 * omegaDesign);
            double torque = Turbomachinery.spoolTorque(turbinePower, compressorPower, w);
            boolean starter = ignition >= 0.5 && !running && n < cutout;
            if (starter) { torque += JetRuntime.starter(c.starterTorqueNm(), n, cutout); }
            step.rotorTorque(0, torque, -(turbinePower + compressorPower) / (w * w), false);
            double thrust = drowned ? 0 : p.thrustN() * share;
            if (health.share() < 1 && thrust > 0) { thrust *= health.share(); }
            JetRuntime.wear(step, wearPace, lit && !drowned, c.turbineInletK(), cp.tt4(), reheatLit);
            step.force(new AppliedForce(AppliedForce.Kind.THRUST, step.offset(new Vec3d(-halfLength, 0, 0)), step.axis(Vec3d.X).scale(thrust)));
            step.setInternal(STATE_FUEL, fuel);
            step.setInternal(STATE_LIT, burner);
            step.setInternal(STATE_SURGE, surge);
            step.setInternal(STATE_SCHEDULE, schedule);
            step.setInternal(STATE_REHEAT, reheatLit ? 1 : 0);
            step.show(JetEngineDesign.SHOWN_REHEAT, reheatLit ? reheatInput : 0);
            step.show(JetEngineDesign.SHOWN_SPEED, n);
            // 0.11: the exhaust for the plume: the core's (reheated when lit) at its exit speed, the dry nozzle's exit pressure
            double exitK = reheatLit ? p.reheat().temperatureK() : cp.tt5(), exitSpeed = reheatLit ? p.reheat().exitSpeedMps() : cp.exitSpeedMps();
            Exhaust.show(step, 2, lit && !drowned, exitK, Exhaust.machFromSpeed(exitSpeed, exitK), cp.exitPressurePa() / flight.pressurePa());
            step.diagnostic("speedFraction", n);
            step.diagnostic("rpm", omega * 30 / Math.PI);
            step.diagnostic("thrustN", thrust);
            step.diagnostic("fuelKgPerS", p.fuelKgPerS());
            step.diagnostic("bypassKgPerS", p.bypassAirflowKgPerS());
            step.diagnostic("coreKgPerS", p.coreAirflowKgPerS());
            step.diagnostic("reheatK", reheatLit ? p.reheat().temperatureK() : 0);
            step.diagnostic("mach", flight.mach());
            step.diagnostic("lit", lit ? 1 : 0);
            step.diagnostic("starter", starter ? 1 : 0);
            step.diagnostic("surging", surge > 0 ? 1 : 0);
        }
    }
}
