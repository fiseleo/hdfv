package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.Pulsejet;
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
 * A pulsejet (脈衝噴射引擎, 0.10): its tube, its reed valves or open intake, and its fuel system (AeroCore's Pulsejet:
 * the gas in the tube simulated to its cycle, run here from its table, Pulsejet.Map, built in the background over
 * airspeed and fuel). It points along the part's +X axis (front forward). Inputs "throttle" 0..1, "ignition" and
 * "start" (starting air and spark); rules in PulseControlDesign. Internal states: lit, fuel (kg/s), reed cycles used.
 * Shown values for the sound and the shaking (H5): the cycle's frequency (Hz) and pressure swing (Pa), lit.
 * <p>
 * Worn reeds (PulseValveDesign's life) leak: from 80 % of their life the thrust falls, to none at 120 %, where the
 * engine stops; new reeds (the game's) set the count back to none. The tube glows: its wall runs at some 1300 K (red
 * heat; the cycle's own wall temperature is on the bench), above the service temperature of plain steel, which then
 * scales away over hours (a warning, as the V-1's tube was mild steel).
 */
public record PulsejetDesign(String name, PulseTubeDesign tube, PulseValveDesign valve, PulseControlDesign control) implements PartDesign {
    public static final String TYPE = "hdfv:pulsejet";
    /** Top airspeed of the table (m/s): pulsejets stop near Mach 0.6. */
    public static final double TOP_SPEED = 250;
    /** The wall temperature a running pulsejet's tube is taken at for its material warning (K). */
    public static final double GLOWING_WALL_K = 1300;
    static final double FUEL_LAG = 0.1, WORN = 0.8, BROKEN = 1.2;

    public PulsejetDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{tube, valve, control}) { Objects.requireNonNull(o, "component"); }
    }

    public java.util.Set<String> acceptedFluids() { return control.acceptedFluids(); }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("tube", tube); m.put("valve", valve); m.put("control", control);
        return m;
    }

    public Pulsejet.Design cycle() {
        PulseValveDesign v = valve;
        return new Pulsejet.Design(v.valveless() ? Pulsejet.Kind.VALVELESS : Pulsejet.Kind.VALVED, v.intakeDiameterM(), v.intakeLengthM(), v.valveAreaM2(), v.openingPa(),
                v.reedFrequencyHz(), tube.chamberDiameterM(), tube.chamberLengthM(), tube.taperLengthM(), tube.tailDiameterM(), tube.tailLengthM(), v.intakeFacesRear(),
                control.mixingTimeS(), control.combustionEfficiency(), control.fuelModel());
    }

    public double length() { return cycle().length(); }
    public double diameter() { return tube.chamberDiameterM(); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        try { cycle(); } catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("cycle", e.getMessage())); return issues; }
        DesignIssue.range(issues, "tubeWallK", GLOWING_WALL_K, 0, tube.material().serviceTemperatureK(), "the tube's material (a running tube glows red; above it, it scales away over hours)");
        double nominal = cycle().nominalFuelKgPerS();
        DesignIssue.range(issues, "maxFuelOverNominal", control.maxFuelKgPerS() / nominal, 0.3, 2, "fuel valves sized for the tube's breathing");
        return issues;
    }

    public double totalMassKg() { return tube.massKg() + valve.massFor(tube.chamberDiameterM()) + control.massFor(); }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new PulsejetDesign(f.string("name", "pulsejet"), TurbofanDesign.component(f, "tube", PulseTubeDesign::read),
                TurbofanDesign.component(f, "valve", PulseValveDesign::read), TurbofanDesign.component(f, "control", PulseControlDesign::read));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new Compiled(this, issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    private static final Map<String, Pulsejet.Map> MODELS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Pulsejet.Map> eldest) { return size() > 16; }
    });

    /** The engine's table (a few seconds to build), shared by every vehicle and bench that runs it. */
    public Pulsejet.Map model() { return MODELS.computeIfAbsent(Json.write(toJson()), k -> Pulsejet.Map.build(cycle(), TOP_SPEED, control.maxFuelKgPerS())); }

    /** The share of the thrust worn reeds still give at a share of their life used. */
    public static double reedShare(double lifeUsed) {
        return lifeUsed < WORN ? 1 : lifeUsed < BROKEN ? 1 - (lifeUsed - WORN) / (BROKEN - WORN) : 0;
    }

    public static final int STATE_LIT = 0, STATE_FUEL = 1, STATE_CYCLES = 2;
    /** Shown values: frequency (Hz), pressure swing (Pa), lit. */
    public static final int SHOWN_FREQUENCY = 0, SHOWN_AMPLITUDE = 1, SHOWN_LIT = 2;

    private static final java.util.List<String> STRUCTURE = java.util.List.of(dev.mcmod.hdfv.damage.Conditions.STRUCTURE);

    private static final class Compiled implements Part {
        private final PulsejetDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        private final double halfLength, life;
        private volatile Pulsejet.Map model;

        Compiled(PulsejetDesign design, List<DesignIssue> issues) {
            this.design = design; this.issues = issues;
            double m = design.totalMassKg(), length = design.length(), d = design.diameter();
            mass = MassProperties.of(m, Vec3d.ZERO, InertiaTensor.box(m, new Vec3d(length, d, d)));
            halfLength = length / 2;
            life = design.valve().reedLifeCycles();
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return mass; }
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return issues; }
        @Override public List<String> inputs() { return List.of("throttle", "ignition", "start"); }
        @Override public int internalStates() { return 3; }
        @Override public int shownValues() { return 3 + Exhaust.COUNT; }
        /** 0.25: its tube (the reeds keep their own wear, STATE_CYCLES) (Conditions.STRUCTURE): damaged from outside only; less thrust, none at 1 (damage.EngineWear). */
        @Override public java.util.List<String> conditions() { return STRUCTURE; }
        @Override public void warmUp() { model(); }

        Pulsejet.Map model() {
            Pulsejet.Map m = model;
            if (m == null) { synchronized (this) { if (model == null) { model = design.model(); } m = model; } }
            return m;
        }

        @Override public void loads(PartStep step) {
            Pulsejet.Map map = model();
            PulseControlDesign c = design.control();
            double dt = step.dt();
            JetRuntime.Air air = JetRuntime.air(step, new Vec3d(halfLength, 0, 0));
            Turbojet.Flight flight = air.flight();
            double throttle = Math.clamp(step.input("throttle", 0), 0, 1), ignition = step.input("ignition", 1), start = step.input("start", 0);
            double used = step.used(0);   // 0.25: a failed tube does not run
            if (used >= 1) { ignition = 0; }
            double fuel = Math.max(0, step.internal(STATE_FUEL)), cycles = Math.max(0, step.internal(STATE_CYCLES));
            fuel += (throttle * c.maxFuelKgPerS() - fuel) * Math.min(1, dt / FUEL_LAG);
            double share = reedShare(cycles / life);
            boolean can = ignition >= 0.5 && JetRuntime.fed(step) && !air.drowned() && fuel > 0 && share > 0;
            boolean blown = start >= 0.5 || flight.speedMps() >= PulseControlDesign.AIRSTART_MPS;
            Pulsejet.Cycle cycle = map.at(flight, can ? fuel : 0);
            boolean lit = can && cycle.running() && (step.internal(STATE_LIT) > 0.5 || blown);
            if (!lit && can) { cycle = map.at(flight, 0); }
            double thrust = air.drowned() ? 0 : lit ? cycle.thrustN() * share : Math.min(0, cycle.thrustN());
            if (used > 0 && thrust > 0) { thrust *= dev.mcmod.hdfv.damage.EngineWear.structure(used); }
            JetRuntime.burn(step, c.acceptedFluids(), ignition, lit ? fuel : 0);
            step.force(new AppliedForce(AppliedForce.Kind.THRUST, step.offset(new Vec3d(-halfLength, 0, 0)), step.axis(Vec3d.X).scale(thrust)));
            if (lit) { cycles += cycle.frequencyHz() * dt; }
            step.setInternal(STATE_LIT, lit ? 1 : 0);
            step.setInternal(STATE_FUEL, fuel);
            step.setInternal(STATE_CYCLES, cycles);
            step.show(SHOWN_FREQUENCY, lit ? cycle.frequencyHz() : 0);
            step.show(SHOWN_AMPLITUDE, lit ? cycle.pressureAmplitudePa() : 0);
            step.show(SHOWN_LIT, lit ? 1 : 0);
            // 0.11: the exhaust for the flame (display estimates: the model's cycle gives no exit state): the rich charge of each
            // cycle still burning as it leaves the tail (about 1900 K, an orange flame), subsonic, at the ambient pressure
            Exhaust.show(step, 3, lit, 1900, 0.5, 1);
            step.diagnostic("thrustN", thrust);
            step.diagnostic("fuelKgPerS", lit ? fuel : 0);
            step.diagnostic("frequencyHz", lit ? cycle.frequencyHz() : 0);
            step.diagnostic("pressureSwingPa", lit ? cycle.pressureAmplitudePa() : 0);
            step.diagnostic("wallK", cycle.wallTemperatureK());
            step.diagnostic("reedLifeUsed", Double.isFinite(life) ? cycles / life : 0);
            step.diagnostic("lit", lit ? 1 : 0);
        }
    }
}
