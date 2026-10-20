package dev.mcmod.hdfv.power;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.Connection;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An engine stand's unit (0.8; the plan: a player-designed engine on a stand driving a line shaft): the engine part
 * itself on AeroCore's vehicle dynamics, geared down to the line (ratio governor speed over the line's nominal speed,
 * 97 %) to a load that takes the power the line asks; a governor (proportional-integral on the engine's speed) works
 * the throttle; the clutch engages once the engine first reaches 90 % of the governed speed and lets go when it stops;
 * an engine that stalls under load trips the stand (it stays stopped until started again).
 * Steps of DT (0.025 s; checked against 0.005 s by the tests) in real time; the fuel it burns is the engine's own
 * figure (kg/s), which the stand counts in work time.
 */
public final class EngineUnit {
    public static final double DT = 0.025, COUPLING_EFFICIENCY = 0.97, LINE_INERTIA = 5, ENGAGE_FRACTION = 0.9;
    /** The governor's gains: throttle per relative speed error, and per relative error-second. */
    public static final double KP = 4, KI = 3;
    private static final FluidState AIR = Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
    private static final RigidBodyState HELD = new RigidBodyState(Vec3d.ZERO, Vec3d.ZERO, Orientation.IDENTITY, Vec3d.ZERO);
    private static final VehicleEnvironment ENVIRONMENT = VehicleEnvironment.calm(-100, FluidState.FRESH_WATER_20C, AIR);

    /** The line's side of the coupling: a rotor of the line's inertia taking a power (input "loadW"). */
    public record StandLoad(double inertiaKgM2) implements PartDesign {
        @Override public String type() { return "hdfv:stand_load"; }
        @Override public String name() { return "line"; }
        @Override public List<DesignIssue> check() { return List.of(); }
        @Override public Map<String, Object> parameters() { return Map.of("inertiaKgM2", inertiaKgM2); }
        @Override public Part compile() {
            StandLoad design = this;
            MassProperties mass = MassProperties.point(1, Vec3d.ZERO);
            return new Part() {
                @Override public PartDesign design() { return design; }
                @Override public MassProperties mass() { return mass; }
                @Override public List<CollisionSphere> collision() { return List.of(); }
                @Override public List<DesignIssue> issues() { return List.of(); }
                @Override public List<Rotor> rotors() { return List.of(new Rotor(Vec3d.X, inertiaKgM2, false)); }
                @Override public List<String> inputs() { return List.of("loadW"); }
                @Override public void loads(PartStep step) {
                    double p = Math.max(step.input("loadW", 0), 0), w = step.rotorSpeed(0), safe = Math.max(Math.abs(w), 2), t = Math.tanh(w / 1);
                    step.rotorTorque(0, -p / safe * t, -p / (safe * safe) * (1 - t * t) * safe, true);
                }
            };
        }
    }

    public final PistonEngineDesign engine;
    public final double governorRpm, ratio, dt;
    private final Vehicle vehicle;
    private final VehicleState state;
    private final Map<String, Double> controls = new HashMap<>();
    private double integral, time, fuelKgPerS, lastPowerW;
    private boolean running, engaged, tripped;

    public EngineUnit(PistonEngineDesign engine, double governorRpm) { this(engine, governorRpm, DT); }

    /** With a step other than DT (the tests' check of it). */
    public EngineUnit(PistonEngineDesign engine, double governorRpm, double dt) {
        this.engine = engine;
        this.dt = dt;
        this.governorRpm = governorRpm;
        this.ratio = governorRpm / PowerPlant.NOMINAL_RPM;
        vehicle = new VehicleBlueprint("engine stand", List.of(
                new VehicleBlueprint.Entry("engine", engine, Mount.at(Vec3d.ZERO), List.of(Binding.of("throttle", "throttle"), Binding.of("ignition", "ignition"))),
                new VehicleBlueprint.Entry("line", new StandLoad(LINE_INERTIA), Mount.at(new Vec3d(-1, 0, 0)), List.of(Binding.of("loadW", "loadW")))),
                List.of(Connection.Shaft.ports("engine", null, "line", null, ratio, COUPLING_EFFICIENCY))).compile();
        state = vehicle.newState(Vec3d.ZERO, Orientation.IDENTITY, dt);
        controls.putAll(Map.of("throttle", 0.0, "ignition", 0.0, "loadW", 0.0));
    }

    /** Builds the engine's tables (seconds of work; off the game thread). */
    public void warmUp() { vehicle.warmUp(); }

    public void setRunning(boolean on) { running = on; if (on) { tripped = false; } }
    /** Whether it stalled under load and stopped. */
    public boolean tripped() { return tripped; }
    public boolean running() { return running; }
    /** Whether the engine fires (its own state). */
    public boolean firing() { return state.internal("engine", PistonEngineDesign.STATE_RUNNING) > 0.5; }
    public boolean engaged() { return engaged; }
    public double engineRpm() { return state.rotorSpeed(vehicle.installed("engine").firstRotor()) * 30 / Math.PI; }
    public double lineRpm() { return state.rotorSpeed(vehicle.installed("line").firstRotor()) * 30 / Math.PI; }
    public double throttle() { return controls.get("throttle"); }
    /** The engine's fuel flow over the last step, kg/s. */
    public double fuelKgPerS() { return fuelKgPerS; }
    /** Power the line took over the last step, W. */
    public double powerW() { return lastPowerW; }

    /** Runs a span of real time (s) with the line asking a power (W) once engaged. */
    public void run(double seconds, double loadW) {
        int n = Math.max(1, (int) Math.round(seconds / dt));
        double fuel = 0, power = 0;
        for (int i = 0; i < n; i++) {
            double rpm = engineRpm();
            if (!running || !firing() && rpm < 100) { engaged = engaged && running && firing(); }
            if (running && firing() && rpm >= ENGAGE_FRACTION * governorRpm) { engaged = true; }
            if (!running) { engaged = false; integral = 0; }
            double error = (governorRpm - rpm) / governorRpm;
            double throttle = 0;
            if (running && firing()) {
                integral = Math.clamp(integral + error * dt, -1 / KI, 1 / KI);
                throttle = Math.clamp(KP * error + KI * integral, 0, 1);
            }
            double load = engaged ? Math.max(loadW, 0) : 0;
            controls.put("throttle", throttle);
            controls.put("ignition", running ? 1.0 : 0.0);
            controls.put("loadW", load);
            VehicleDynamics.Result r = VehicleDynamics.step(vehicle, state, ENVIRONMENT, controls, Vec3d.ZERO, List.of(), null, time, dt);
            state.setBody(HELD);
            time += dt;
            Map<String, Double> d = r.diagnostics().getOrDefault("engine", Map.of());
            fuel += d.getOrDefault("fuelKgPerS", 0.0);
            power += load * Math.tanh(state.rotorSpeed(vehicle.installed("line").firstRotor()));
            if (engaged && !firing()) { engaged = false; running = false; tripped = true; }
        }
        fuelKgPerS = fuel / n;
        lastPowerW = power / n;
    }
}
