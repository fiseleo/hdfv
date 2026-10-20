package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.Ramjet;
import dev.mcmod.aerocore.api.engine.Turbojet;
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
 * A ramjet (衝壓引擎, 0.10) assembled from a supersonic inlet, a combustor with flameholders, a nozzle and its fuel
 * control (AeroCore's Ramjet: the throat sized at the design point, the inlet matched supercritical, critical,
 * subcritical or buzzing off design). It points along the part's +X axis and pushes forward; it has no moving parts and
 * gives nothing standing still: it is boosted (a rocket, a turbojet, a dive) to its light-off Mach number. Inputs
 * "throttle" 0..1 and "ignition" (unbound = on); the control's rules are RamControlDesign's. Internal states: lit, the
 * combustor temperature (K).
 */
public record RamjetDesign(String name, SupersonicInletDesign inlet, RamCombustorDesign combustor, JetNozzleDesign nozzle, RamControlDesign control)
        implements PartDesign {
    public static final String TYPE = "hdfv:ramjet";
    /** Below this Mach number the duct gives nothing (the model's choked nozzle means nothing there). */
    static final double DEAD_MACH = 0.3;
    /** The fuel's lag, s, and how far over the ram air's total temperature the leanest throttle sets the combustor (K). */
    static final double FUEL_LAG = 0.2, IDLE_RISE = 300;

    public RamjetDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{inlet, combustor, nozzle, control}) { Objects.requireNonNull(o, "component"); }
    }

    public java.util.Set<String> acceptedFluids() { return control.acceptedFluids(); }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("inlet", inlet); m.put("combustor", combustor); m.put("nozzle", nozzle); m.put("control", control);
        return m;
    }

    /** The cycle these components make. */
    public Ramjet.Design cycle() {
        return new Ramjet.Design(inlet.design(), inlet.captureAreaM2(), combustor.areaRatio() * inlet.captureAreaM2(), combustor.flameholderLoss(), combustor.efficiency(),
                combustor.maxTemperatureK(), nozzle.pressureRetention(), nozzle.convergentDivergent(), nozzle.convergentDivergent() ? nozzle.exitAreaRatio() : 1,
                control.supercriticalMargin(), control.fuelModel());
    }

    /** The design flight condition: the control's Mach number at its altitude (standard atmosphere). */
    public Turbojet.Flight designFlight() { return flight(control.designMach(), control.designAltitudeM()); }

    public static Turbojet.Flight flight(double mach, double altitudeM) {
        Atmosphere.Sample a = Atmosphere.atAltitude(altitudeM, Vec3d.ZERO);
        return new Turbojet.Flight(a.temperatureKelvin(), a.pressurePa(), mach * a.fluid().speedOfSoundMps());
    }

    public Ramjet.Sizing sizing() { return Ramjet.size(cycle(), designFlight(), control.designTemperatureK()); }

    public double length() { return inlet.lengthM() + combustor.lengthM() + nozzle.tailpipeLengthM(); }
    public double diameter() { return 1.1 * Math.sqrt(4 * combustor.areaRatio() * inlet.captureAreaM2() / Math.PI); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        if (control.designTemperatureK() > combustor.maxTemperatureK()) {
            issues.add(DesignIssue.invalid("control.designTemperatureK", "The design point runs the combustor above its largest temperature"));
        }
        Turbojet.Flight design = designFlight();
        double ram = design.totalTemperature();
        if (ram > inlet.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("inlet.material", "The inlet's skin cannot take the " + Math.round(ram) + " K ram air at the design point"));
        }
        double liner = combustor.linerTemperature(combustor.maxTemperatureK(), ram);
        if (liner > combustor.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("combustor.material", "The film-cooled liner would run at " + Math.round(liner) + " K, above its material's service temperature"));
        }
        if (liner > nozzle.material().serviceTemperatureK()) {
            issues.add(DesignIssue.invalid("nozzle.material", "The nozzle would run at " + Math.round(liner) + " K, above its material's service temperature"));
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        try { sizing(); }
        catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("cycle", e.getMessage())); }
        return issues;
    }

    /** Mass of the engine, kg. */
    public double totalMassKg() {
        Ramjet.Sizing s = sizing();
        return inlet.massKg() + combustor.massFor(inlet.captureAreaM2()) + nozzle.massFor(s.throatAreaM2()) + control.massFor(s.point().airflowKgPerS());
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new RamjetDesign(f.string("name", "ramjet"), TurbofanDesign.component(f, "inlet", SupersonicInletDesign::read),
                TurbofanDesign.component(f, "combustor", RamCombustorDesign::read), TurbofanDesign.component(f, "nozzle", JetNozzleDesign::read),
                TurbofanDesign.component(f, "control", RamControlDesign::read));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new Compiled(this, issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    private static final Map<String, Ramjet.Sizing> MODELS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Ramjet.Sizing> eldest) { return size() > 32; }
    });

    /** The sized cycle, built once per design (by its JSON) and shared by every vehicle and bench that runs it. */
    public Ramjet.Sizing model() { return MODELS.computeIfAbsent(Json.write(toJson()), k -> sizing()); }

    /** The engine at a flight condition and combustor temperature, or none below DEAD_MACH (and where the model has no answer). */
    public static Ramjet.Point point(Ramjet.Sizing s, Turbojet.Flight flight, double temperatureK) {
        if (flight.mach() < DEAD_MACH) { return null; }
        try { return Ramjet.at(s, flight, temperatureK); } catch (IllegalArgumentException e) { return null; }
    }

    /** The combustor temperature the control sets for a throttle at a flight condition. */
    public double scheduledTemperature(Turbojet.Flight flight, double throttle) {
        double tt0 = flight.totalTemperature(), low = Math.min(tt0 + IDLE_RISE, combustor.maxTemperatureK());
        return low + Math.clamp(throttle, 0, 1) * (combustor.maxTemperatureK() - low);
    }

    public static final int STATE_LIT = 0, STATE_TEMPERATURE = 1;
    /** Shown value (0.10): the flame's share, 0 to 1 (also the scramjet's and the booster's first shown value). */
    public static final int SHOWN_FLAME = 0;

    private static final class Compiled implements Part {
        private final RamjetDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        private final double halfLength;
        private volatile Ramjet.Sizing model;

        Compiled(RamjetDesign design, List<DesignIssue> issues) {
            this.design = design; this.issues = issues;
            double m = design.totalMassKg(), length = design.length(), d = design.diameter();
            mass = MassProperties.of(m, Vec3d.ZERO, InertiaTensor.box(m, new Vec3d(length, d, d)));
            halfLength = length / 2;
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return mass; }
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return issues; }
        @Override public List<String> inputs() { return List.of("throttle", "ignition"); }
        @Override public int internalStates() { return 2; }
        /** The flame: the combustor's rise over its largest (0 unlit), for its flame and sound. */
        @Override public int shownValues() { return 1 + Exhaust.COUNT; }
        @Override public void warmUp() { model(); }

        Ramjet.Sizing model() {
            Ramjet.Sizing m = model;
            if (m == null) { synchronized (this) { if (model == null) { model = design.model(); } m = model; } }
            return m;
        }

        @Override public void loads(PartStep step) {
            Ramjet.Sizing s = model();
            RamControlDesign c = design.control();
            double dt = step.dt();
            JetRuntime.Air air = JetRuntime.air(step, new Vec3d(halfLength, 0, 0));
            Turbojet.Flight flight = air.flight();
            double mach = flight.mach(), tt0 = flight.totalTemperature();
            double throttle = Math.clamp(step.input("throttle", 0), 0, 1), ignition = step.input("ignition", 1);
            boolean lit = step.internal(STATE_LIT) > 0.5;
            boolean can = ignition >= 0.5 && JetRuntime.fed(step) && !air.drowned() && throttle > 0;
            lit = can && mach >= c.lightMach() * (lit ? 0.9 : 1);
            double t = Math.max(tt0, step.internal(STATE_TEMPERATURE));
            double target = lit ? design.scheduledTemperature(flight, throttle) : tt0;
            t += (target - t) * Math.min(1, dt / FUEL_LAG);
            Ramjet.Point p = point(s, flight, lit ? t : tt0);
            if (lit) {
                // lean blowout: the mixture the control schedules (the combustor follows it within FUEL_LAG) is below the flame's limit
                Ramjet.Point aim = point(s, flight, target);
                if (aim == null || aim.equivalenceRatio() < c.leanLimit()) { lit = false; t = tt0; p = point(s, flight, tt0); }
            }
            double fuel = lit && p != null ? p.fuelKgPerS() : 0;
            JetRuntime.burn(step, c.acceptedFluids(), ignition, fuel);
            double thrust = air.drowned() || p == null ? 0 : p.thrustN();
            step.force(new AppliedForce(AppliedForce.Kind.THRUST, step.offset(new Vec3d(-halfLength, 0, 0)), step.axis(Vec3d.X).scale(thrust)));
            step.setInternal(STATE_LIT, lit ? 1 : 0);
            step.setInternal(STATE_TEMPERATURE, lit ? t : tt0);
            step.show(SHOWN_FLAME, lit ? Math.clamp((t - tt0) / Math.max(design.combustor().maxTemperatureK() - tt0, 1), 0, 1) : 0);
            // 0.11: the exhaust: the combustor's gas at the nozzle's exit speed, expanded to the ambient (the nozzle's exit pressure is not modelled)
            Exhaust.show(step, 1, lit && p != null && !air.drowned(), p == null ? 0 : p.tt4(), p == null ? 0 : Exhaust.machFromSpeed(p.exitSpeedMps(), p.tt4()), 1);
            step.diagnostic("thrustN", thrust);
            step.diagnostic("fuelKgPerS", fuel);
            step.diagnostic("mach", mach);
            step.diagnostic("combustorK", p == null ? tt0 : p.tt4());
            step.diagnostic("airflowKgPerS", p == null ? 0 : p.airflowKgPerS());
            step.diagnostic("recovery", p == null ? 0 : p.recovery());
            step.diagnostic("equivalenceRatio", p == null ? 0 : p.equivalenceRatio());
            step.diagnostic("spillageDragN", p == null ? 0 : p.spillageDragN());
            step.diagnostic("inletMode", p == null ? -1 : p.mode().ordinal());
            step.diagnostic("lit", lit ? 1 : 0);
        }
    }
}
