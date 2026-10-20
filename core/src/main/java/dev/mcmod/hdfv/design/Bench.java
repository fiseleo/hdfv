package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.engine.EngineMap;
import dev.mcmod.aerocore.api.engine.FreeTurbine;
import dev.mcmod.aerocore.api.engine.Friction;
import dev.mcmod.aerocore.api.engine.Turbofan;
import dev.mcmod.aerocore.api.engine.Turbojet;
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
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trial runs of an engine design (the design screen's bench; seconds of work, so off the game thread). The numbers come
 * from what the engine runs on in a vehicle: a piston engine's map (EngineMap) and friction (Friction); a jet's sized
 * cycle and running lines (Turbojet, Turbofan, FreeTurbine) under its own fuel control; the start and run-up step the
 * compiled part itself on a held stand (a shaft engine driving a load that takes power as a propeller does, with the
 * speed cubed, sized to absorb its full power at its design output speed).
 */
public final class Bench {
    private Bench() {}

    /** Oil temperature of a warm engine on the bench, C. */
    public static final double WARM_OIL_C = 90;
    /** Time step of the stand, s (as the engine tests). */
    public static final double DT = 0.005;

    // ---------------------------------------------------------------- piston engine

    /** One full-load point: speed (rpm), brake torque (N m), power (W), brake specific fuel consumption (g/kWh), knock-limited. */
    public record PistonPoint(double rpm, double torqueNm, double powerW, double bsfcGPerKWh, boolean knocking) {}

    /** A full-load curve and its peaks. */
    public record PistonCurve(List<PistonPoint> points, PistonPoint peakTorque, PistonPoint peakPower, double bestBsfcGPerKWh) {}

    /**
     * The full-load curve (wide-open throttle at the boost the turbocharger holds, or a diesel's full-load fuel;
     * standard sea-level air, warm oil) from 1000 rpm up to the rev limit.
     */
    public static PistonCurve fullLoad(PistonEngineDesign e) { return fullLoad(e, e.performance()); }

    public static PistonCurve fullLoad(PistonEngineDesign e, PistonEngineDesign.Performance perf) {
        Friction.Engine friction = e.frictionModel();
        double viscosity = e.lubrication().oil().frictionScaling(WARM_OIL_C), scale = e.displacement() / (4 * Math.PI);
        int cylinders = e.block().cylinders();
        List<PistonPoint> points = new ArrayList<>();
        PistonPoint peakTorque = null, peakPower = null;
        double bestBsfc = Double.NaN;
        for (double rpm : perf.rpms()) {
            if (rpm < 1000 || rpm > e.ignition().revLimitRpm()) { continue; }
            double intake = perf.maxBoost(rpm) * PistonEngineDesign.AMBIENT_PA;
            double fmep = Friction.fmep(friction, rpm, intake, PistonEngineDesign.AMBIENT_PA, viscosity).totalPa();
            double torque = (perf.fullLoad(rpm, EngineMap.Point::netImepPa) - fmep) * scale, power = torque * rpm * Math.PI / 30;
            double fuel = perf.fullLoad(rpm, EngineMap.Point::fuelKg);
            double bsfc = power > 0 ? cylinders * fuel * rpm / 120 / power * 3.6e9 : Double.NaN;
            boolean knocking = !perf.isDiesel() && perf.fullLoad(rpm, p -> p.knocking() ? 1 : 0) > 0.5;
            PistonPoint point = new PistonPoint(rpm, torque, power, bsfc, knocking);
            points.add(point);
            if (peakTorque == null || torque > peakTorque.torqueNm()) { peakTorque = point; }
            if (peakPower == null || power > peakPower.powerW()) { peakPower = point; }
            if (bsfc > 0 && !(bsfc >= bestBsfc)) { bestBsfc = bsfc; }
        }
        return new PistonCurve(points, peakTorque, peakPower, bestBsfc);
    }

    // ---------------------------------------------------------------- jet engine

    /** One steady point of a jet: throttle, spool speed (fraction of design) and the cycle's values there. */
    public record JetPoint(double throttle, double speedFraction, Turbojet.Point point) {
        public double thrustN() { return point.thrustN(); }
        /** Thrust-specific fuel consumption, g/(kN s); NaN without thrust. */
        public double tsfcGPerKNs() { return point.thrustN() > 0 ? point.fuelKgPerS() / point.thrustN() * 1e6 : Double.NaN; }
    }

    /** Standard air at an altitude (m) and a flight speed (m/s). */
    public static Turbojet.Flight flight(double altitudeM, double speedMps) {
        Atmosphere.Sample a = Atmosphere.atAltitude(altitudeM, Vec3d.ZERO);
        return new Turbojet.Flight(a.temperatureKelvin(), a.pressurePa(), speedMps);
    }

    /**
     * Steady thrust against throttle (points from 0 to 1) at a flight condition. The fuel control sets the target speed
     * between idle and maximum; the spool settles there on the running line, or lower where the running line would pass
     * the control's turbine inlet temperature (the temperature limiter holds it).
     */
    public static List<JetPoint> throttleSweep(JetEngineDesign e, Turbojet.Flight flight, int points) {
        JetEngineDesign.Model m = e.model();
        double idle = e.control().idleFraction(), max = e.control().maxSpeedFraction(), limit = e.control().turbineInletK();
        List<JetPoint> out = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double throttle = points == 1 ? 1 : (double) i / (points - 1);
            double target = idle + throttle * (max - idle);
            double n = target;
            if (onLine(m, flight, target).tt4() > limit) {
                double lo = 0.2, hi = target;
                for (int k = 0; k < 50; k++) {
                    double mid = 0.5 * (lo + hi);
                    if (onLine(m, flight, mid).tt4() > limit) { hi = mid; } else { lo = mid; }
                }
                n = lo;
            }
            out.add(new JetPoint(throttle, n, onLine(m, flight, n)));
        }
        return out;
    }

    private static Turbojet.Point onLine(JetEngineDesign.Model m, Turbojet.Flight flight, double n) {
        return Turbojet.quasiSteady(m.sizing(), m.lines(), flight, n, Turbojet.lineFuel(m.sizing(), m.lines(), flight, n));
    }

    /**
     * A start and run-up on a held stand at sea level: ignition on at zero throttle, then full throttle once it idles.
     * Times in s from ignition (NaN if it did not get there in the time allowed); samples every 0.05 s.
     */
    public record JetRun(double lightOffS, double idleS, double throttleS, double fullThrustS, double fullThrustN,
                         double[] timeS, double[] speedFraction, double[] thrustN, double[] turbineInletK) {}

    /** Seconds allowed for the start and for the run-up. */
    public static final double START_LIMIT_S = 120, RUN_UP_LIMIT_S = 30;

    public static JetRun startAndRunUp(JetEngineDesign e) {
        return run(new Stand(e, "jet", null), e.control().idleFraction(), throttleSweep(e, Turbojet.Flight.sea(0), 1).get(0).thrustN(), JetEngineDesign.STATE_LIT);
    }

    /** A turbofan's start and run-up (as a turbojet's; its speed is the fan's). */
    public static JetRun startAndRunUp(TurbofanDesign e) {
        return run(new Stand(e, "jet", null), e.control().idleFraction(), throttleSweep(e, Turbojet.Flight.sea(0), 1).get(0).thrustN(), TurbofanDesign.STATE_LIT);
    }

    private static JetRun run(Stand stand, double idle, double fullThrust, int litState) {
        List<double[]> samples = new ArrayList<>();
        double lightOff = Double.NaN, idled = Double.NaN, full = Double.NaN, throttleAt = Double.NaN;
        int every = (int) Math.round(0.05 / DT);
        for (int i = 0; stand.t < START_LIMIT_S + RUN_UP_LIMIT_S; i++) {
            Map<String, Double> d = stand.step();
            if (i % every == 0) { samples.add(new double[]{stand.t, d.get("speedFraction"), d.get("thrustN"), d.get("turbineInletK")}); }
            if (Double.isNaN(lightOff) && d.get("lit") > 0.5) { lightOff = stand.t; }
            boolean running = stand.state.internal("jet", litState) > 1.5;
            if (Double.isNaN(idled)) {
                if (running && d.get("speedFraction") >= idle - 0.02) { idled = stand.t; throttleAt = stand.t + 1; }
                else if (stand.t > START_LIMIT_S) { break; }
            } else if (stand.t >= throttleAt) {
                stand.controls.put("throttle", 1.0);
                if (d.get("thrustN") >= 0.95 * fullThrust) { full = stand.t; break; }
                if (stand.t > throttleAt + RUN_UP_LIMIT_S) { break; }
            }
        }
        int n = samples.size();
        double[] t = new double[n], s = new double[n], f = new double[n], k = new double[n];
        for (int i = 0; i < n; i++) { double[] x = samples.get(i); t[i] = x[0]; s[i] = x[1]; f[i] = x[2]; k[i] = x[3]; }
        return new JetRun(lightOff, idled, throttleAt, full, fullThrust, t, s, f, k);
    }

    // ---------------------------------------------------------------- turbofan

    /** One steady point of a turbofan: throttle, fan and core speeds (fractions of design) and the cycle's values there. */
    public record FanPoint(double throttle, double fanSpeed, double coreSpeed, Turbofan.Point point) {
        public double thrustN() { return point.thrustN(); }
        public double tsfcGPerKNs() { return point.thrustN() > 0 ? point.fuelKgPerS() / point.thrustN() * 1e6 : Double.NaN; }
    }

    /** Steady thrust against throttle at a flight condition: the fan speed target between idle and maximum, held down by the turbine temperature limit. */
    public static List<FanPoint> throttleSweep(TurbofanDesign e, Turbojet.Flight flight, int points) {
        TurbofanDesign.Model m = e.model();
        double idle = e.control().idleFraction(), max = e.control().maxSpeedFraction(), limit = e.control().turbineInletK();
        List<FanPoint> out = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double throttle = points == 1 ? 1 : (double) i / (points - 1);
            double n = limited(idle + throttle * (max - idle), x -> onLine(m, flight, x).tt4() > limit);
            out.add(new FanPoint(throttle, n, Turbofan.lineHighSpeed(m.lines(), flight, n), onLine(m, flight, n)));
        }
        return out;
    }

    private static Turbofan.Point onLine(TurbofanDesign.Model m, Turbojet.Flight flight, double n) {
        return Turbofan.quasiSteady(m.sizing(), m.lines(), flight, n, Turbofan.lineHighSpeed(m.lines(), flight, n), Turbofan.lineFuel(m.sizing(), m.lines(), flight, n), null);
    }

    /** The target, or the highest speed below it that a limit (true: over it) allows. */
    private static double limited(double target, java.util.function.DoublePredicate over) {
        if (!over.test(target)) { return target; }
        double lo = 0.1, hi = target;
        for (int k = 0; k < 50; k++) {
            double mid = 0.5 * (lo + hi);
            if (over.test(mid)) { hi = mid; } else { lo = mid; }
        }
        return lo;
    }

    // ---------------------------------------------------------------- turboprop and turboshaft

    /**
     * One steady point of a shaft engine driving the bench's load: throttle, gas generator and power turbine speeds
     * (fractions), the output's power (W) and torque (N m) after the gearbox, the cycle's values.
     */
    public record ShaftPoint(double throttle, double gasGeneratorSpeed, double powerTurbineSpeed, double powerW, double torqueNm, FreeTurbine.Point point) {
        public double psfcGPerKWh() { return powerW > 0 ? point.fuelKgPerS() / powerW * 3.6e9 : Double.NaN; }
    }

    /** The bench load's coefficient: torque k w^2 at the output, taking the design point's output power at the design output speed. */
    public static double loadCoefficient(ShaftTurbineDesign e) {
        double omega = e.outputRpm() * Math.PI / 30;
        return e.designOutputTorque(e.sizing().point()) / (omega * omega);
    }

    /**
     * Steady output power against throttle (the gas generator's speed target between idle and maximum) at a flight
     * condition, driving the bench's load (a propeller's law, sized to take the design power at the design output speed):
     * the power turbine settles where its torque meets the load's, so at idle it turns slowly, as a propeller at ground
     * idle does; held down by the turbine temperature limit and the gearbox's rated torque.
     */
    public static List<ShaftPoint> powerSweep(ShaftTurbineDesign e, Turbojet.Flight flight, int points) {
        ShaftTurbineDesign.Model m = e.model();
        double idle = e.control().idleFraction(), max = e.control().maxSpeedFraction(), limit = e.control().turbineInletK(), rated = e.gearbox().ratedTorqueNm();
        double k = loadCoefficient(e), omega = e.outputRpm() * Math.PI / 30;
        List<ShaftPoint> out = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double throttle = points == 1 ? 1 : (double) i / (points - 1);
            double n = limited(idle + throttle * (max - idle), x -> {
                FreeTurbine.Point p = loaded(e, m, flight, x, k, omega);
                return p.tt4() > limit || e.designOutputTorque(p) > rated;
            });
            FreeTurbine.Point p = loaded(e, m, flight, n, k, omega);
            double torque = e.designOutputTorque(p);
            out.add(new ShaftPoint(throttle, n, p.powerTurbineSpeedFraction(), torque * p.powerTurbineSpeedFraction() * omega, torque, p));
        }
        return out;
    }

    /** The gas generator on its running line at a speed, the power turbine at the speed where its output torque meets the load's k w^2. */
    private static FreeTurbine.Point loaded(ShaftTurbineDesign e, ShaftTurbineDesign.Model m, Turbojet.Flight flight, double n, double k, double omega) {
        double fuel = FreeTurbine.lineFuel(m.sizing(), m.lines(), flight, n), lo = 0, hi = 1.6;
        FreeTurbine.Point p = null;
        double[] warm = null;
        for (int it = 0; it < 40; it++) {
            double np = 0.5 * (lo + hi);
            p = FreeTurbine.quasiSteady(m.sizing(), m.lines(), flight, n, np, fuel, warm);
            if (p.solution() != null) { warm = p.solution(); }
            double w = np * omega;
            if (e.designOutputTorque(p) > k * w * w) { lo = np; } else { hi = np; }
        }
        return p;
    }

    /** Output torque (N m) and power (W) at an output speed (fraction of design). */
    public record TorquePoint(double outputSpeed, double torqueNm, double powerW) {}

    /**
     * The free turbine's characteristic: output torque against output speed from the shaft held to 1.5 times design,
     * the gas generator kept at its full-power speed and fuel.
     */
    public static List<TorquePoint> torqueCurve(ShaftTurbineDesign e, Turbojet.Flight flight, int points) {
        ShaftTurbineDesign.Model m = e.model();
        ShaftPoint full = powerSweep(e, flight, 1).get(0);
        double omega = e.outputRpm() * Math.PI / 30;
        List<TorquePoint> out = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double n = 1.5 * i / Math.max(1, points - 1);
            FreeTurbine.Point p = FreeTurbine.quasiSteady(m.sizing(), m.lines(), flight, full.gasGeneratorSpeed(), n, full.point().fuelKgPerS(), full.point().solution());
            double torque = e.designOutputTorque(p);
            out.add(new TorquePoint(n, torque, torque * n * omega));
        }
        return out;
    }

    /**
     * A shaft engine's start and run-up on the stand, driving the bench load: ignition on at zero throttle (idle), then
     * full throttle once it idles. Times in s from ignition (NaN if not reached); samples every 0.05 s.
     */
    public record ShaftRun(double lightOffS, double idleS, double throttleS, double fullPowerS, double fullPowerW, double[] timeS,
                           double[] gasGeneratorSpeed, double[] powerTurbineSpeed, double[] powerW, double[] turbineInletK) {}

    /** The bench load's moment of inertia over the engine's at its output (a propeller's, about). */
    public static final double LOAD_INERTIA = 4;

    public static ShaftRun startAndRunUp(ShaftTurbineDesign e) {
        double fullPower = powerSweep(e, Turbojet.Flight.sea(0), 1).get(0).powerW();
        Stand stand = new Stand(e, "jet", new Load(LOAD_INERTIA * e.outputInertia(), loadCoefficient(e)));
        double idle = e.control().idleFraction();
        List<double[]> samples = new ArrayList<>();
        double lightOff = Double.NaN, idled = Double.NaN, full = Double.NaN, throttleAt = Double.NaN;
        int every = (int) Math.round(0.05 / DT);
        for (int i = 0; stand.t < START_LIMIT_S + RUN_UP_LIMIT_S; i++) {
            Map<String, Double> d = stand.step();
            double power = d.get("shaftPowerW");
            if (i % every == 0) { samples.add(new double[]{stand.t, d.get("speedFraction"), d.get("powerTurbineSpeed"), power, d.get("turbineInletK")}); }
            if (Double.isNaN(lightOff) && d.get("lit") > 0.5) { lightOff = stand.t; }
            boolean running = stand.state.internal("jet", ShaftTurbineDesign.STATE_LIT) > 1.5;
            if (Double.isNaN(idled)) {
                if (running && d.get("speedFraction") >= idle - 0.02) { idled = stand.t; throttleAt = stand.t + 1; }
                else if (stand.t > START_LIMIT_S) { break; }
            } else if (stand.t >= throttleAt) {
                stand.controls.put("throttle", 1.0);
                if (power >= 0.95 * fullPower) { full = stand.t; break; }
                if (stand.t > throttleAt + RUN_UP_LIMIT_S) { break; }
            }
        }
        int n = samples.size();
        double[][] cols = new double[5][n];
        for (int i = 0; i < n; i++) { for (int k = 0; k < 5; k++) { cols[k][i] = samples.get(i)[k]; } }
        return new ShaftRun(lightOff, idled, throttleAt, full, fullPower, cols[0], cols[1], cols[2], cols[3], cols[4]);
    }

    /** The bench's load on a shaft engine's output: a flywheel taking torque k w^2 (a propeller's law). */
    private record Load(double inertiaKgM2, double coefficient) implements PartDesign {
        @Override public String type() { return "hdfv:bench_load"; }
        @Override public String name() { return "load"; }
        @Override public List<DesignIssue> check() { return List.of(); }
        @Override public Map<String, Object> parameters() { return Map.of("inertiaKgM2", inertiaKgM2, "coefficient", coefficient); }   // compiled parts are cached by their JSON
        @Override public Part compile() {
            Load design = this;
            MassProperties mass = MassProperties.of(1, Vec3d.ZERO, InertiaTensor.box(1, new Vec3d(0.1, 0.1, 0.1)));
            return new Part() {
                @Override public PartDesign design() { return design; }
                @Override public MassProperties mass() { return mass; }
                @Override public List<CollisionSphere> collision() { return List.of(); }
                @Override public List<DesignIssue> issues() { return List.of(); }
                @Override public List<Rotor> rotors() { return List.of(new Rotor(Vec3d.X, inertiaKgM2, false)); }
                @Override public void loads(PartStep step) {
                    double w = step.rotorSpeed(0);
                    step.rotorTorque(0, -coefficient * w * Math.abs(w), -2 * coefficient * Math.abs(w), false);
                }
            };
        }
    }

    /** An engine on a stand that holds it still, in still sea-level air (its inputs "throttle" and "ignition"), with a load on its first rotor or none. */
    private static final class Stand {
        private static final FluidState AIR = Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
        private static final RigidBodyState HELD = new RigidBodyState(Vec3d.ZERO, Vec3d.ZERO, Orientation.IDENTITY, Vec3d.ZERO);
        final Vehicle vehicle;
        final VehicleState state;
        final String id;
        final VehicleEnvironment environment = VehicleEnvironment.calm(-100, FluidState.FRESH_WATER_20C, AIR);
        final Map<String, Double> controls = new HashMap<>(Map.of("throttle", 0.0, "ignition", 1.0));
        double t;

        Stand(PartDesign engine, String id, PartDesign load) {
            this.id = id;
            List<VehicleBlueprint.Entry> entries = new ArrayList<>(List.of(new VehicleBlueprint.Entry(id, engine, Mount.at(Vec3d.ZERO),
                    List.of(Binding.of("throttle", "throttle"), Binding.of("ignition", "ignition")))));
            List<Connection> connections = new ArrayList<>();
            if (load != null) {
                entries.add(new VehicleBlueprint.Entry("load", load, Mount.at(new Vec3d(1, 0, 0)), List.of()));
                connections.add(new Connection.Shaft(id, "load", 1, 1));
            }
            vehicle = new VehicleBlueprint("bench", entries, connections).compile();
            vehicle.warmUp();
            state = vehicle.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        }

        Map<String, Double> step() {
            VehicleDynamics.Result r = VehicleDynamics.step(vehicle, state, environment, controls, Vec3d.ZERO, List.of(), null, t, DT);
            state.setBody(HELD);
            t += DT;
            return r.diagnostics().get(id);
        }
    }

    // ---------------------------------------------------------------- airframes

    /** Sea-level air for the airframe benches. */
    private static final dev.mcmod.aerocore.api.aero.Wing.Air SEA_LEVEL = dev.mcmod.aerocore.api.aero.Wing.Air.SEA_LEVEL;

    /** A wing's lift and drag coefficients (on its plan area) at an angle of attack (deg). */
    public record WingPoint(double alphaDeg, double cl, double cd) {}

    /** A wing's curves at a speed: clean and with its trailing-edge surface fully down (null without one), the clean maximum and best lift over drag. */
    public record WingCurve(double speedMps, List<WingPoint> clean, List<WingPoint> deflected, WingPoint maximum, double bestLiftOverDrag, double deflectedMaximum) {}

    /** Lift and drag against angle of attack, -6 to 24 deg, at a speed at sea level (the lifting line with 12 strips a side). */
    public static WingCurve wingCurve(dev.mcmod.hdfv.part.air.WingDesign w, double speedMps) {
        dev.mcmod.aerocore.api.aero.Wing wing = w.wing(12);
        double area = w.planform().planform().areaM2(), q = 0.5 * SEA_LEVEL.densityKgM3() * speedMps * speedMps;
        List<WingPoint> clean = wingSweep(wing, speedMps, area, q, Map.of());
        List<WingPoint> deflected = null;
        double deflectedMax = Double.NaN;
        if (w.trailingEdge().active()) {
            deflected = wingSweep(wing, speedMps, area, q, Map.of(w.trailingEdge().input(), 1.0));
            deflectedMax = deflected.stream().mapToDouble(WingPoint::cl).max().orElse(Double.NaN);
        }
        WingPoint max = clean.get(0);
        double bestLd = 0;
        for (WingPoint p : clean) {
            if (p.cl() > max.cl()) { max = p; }
            if (p.cd() > 0) { bestLd = Math.max(bestLd, p.cl() / p.cd()); }
        }
        return new WingCurve(speedMps, clean, deflected, max, bestLd, deflectedMax);
    }

    private static List<WingPoint> wingSweep(dev.mcmod.aerocore.api.aero.Wing wing, double speed, double area, double q, Map<String, Double> inputs) {
        List<WingPoint> out = new ArrayList<>();
        double[] previous = null;
        // each angle continued from the last, as a wind tunnel run with rising angle (one branch past the stall)
        for (int d = -6; d <= 24; d++) {
            double a = Math.toRadians(d);
            var s = wing.solve(speed, a, 0, SEA_LEVEL, inputs, previous);
            previous = s.strength();
            Vec3d f = s.force();
            double lift = f.x() * Math.sin(a) + f.y() * Math.cos(a), drag = -(f.x() * Math.cos(a) - f.y() * Math.sin(a));
            out.add(new WingPoint(d, lift / (q * area), drag / (q * area)));
        }
        return out;
    }

    /** A propeller's coefficients at an advance ratio: C_T = T / (rho n^2 D^4), C_P = P / (rho n^3 D^5), efficiency. */
    public record PropellerPoint(double advanceRatio, double ct, double cp, double efficiency) {}

    /** At the hub's rated speed: the curves at the fine and coarse stops, static thrust and power at the fine stop, the best efficiency at either. */
    public record PropellerCurve(double rpm, List<PropellerPoint> fine, List<PropellerPoint> coarse, double staticThrustN, double staticPowerW,
                                 double peakEfficiency, double peakAdvanceRatio) {}

    /** Coefficients against advance ratio until the thrust is gone, at the rated speed and sea level. */
    public static PropellerCurve propellerCurve(dev.mcmod.hdfv.part.air.AirPropellerDesign d) {
        var prop = new dev.mcmod.aerocore.api.aero.AirPropeller(d.geometry());
        double rpm = d.hub().maxRpm(), n = rpm / 60, diameter = d.blades().diameterM(), tipSpeed = Math.PI * n * diameter;
        List<PropellerPoint> fine = propellerSweep(prop, Math.toRadians(d.hub().fineDeg()), tipSpeed);
        List<PropellerPoint> coarse = d.hub().variable() ? propellerSweep(prop, Math.toRadians(d.hub().coarseDeg()), tipSpeed) : List.of();
        PropellerPoint best = fine.get(0);
        for (List<PropellerPoint> l : List.of(fine, coarse)) { for (PropellerPoint p : l) { if (p.efficiency() > best.efficiency()) { best = p; } } }
        double rho = SEA_LEVEL.densityKgM3();
        PropellerPoint s = fine.get(0);
        return new PropellerCurve(rpm, fine, coarse, s.ct() * rho * n * n * Math.pow(diameter, 4), s.cp() * rho * n * n * n * Math.pow(diameter, 5),
                best.efficiency(), best.advanceRatio());
    }

    private static List<PropellerPoint> propellerSweep(dev.mcmod.aerocore.api.aero.AirPropeller prop, double setting, double tipSpeed) {
        List<PropellerPoint> out = new ArrayList<>();
        for (double j = 0; j <= 4; j += 0.05) {
            double[] c = prop.classic(j, setting, tipSpeed, SEA_LEVEL);
            out.add(new PropellerPoint(j, c[0], c[1], c[2]));
            if (c[0] < 0) { break; }
        }
        return out;
    }

    /** A hovering rotor (out of ground effect, sea level, design speed) at a collective (deg). */
    public record RotorPoint(double collectiveDeg, double thrustN, double powerW, double figureOfMerit) {}

    /** Hover from the lower to the upper collective stop, and the point at the blade loading C_T/sigma = 0.08 (a typical design hover). */
    public record RotorCurve(double rpm, List<RotorPoint> points, RotorPoint design) {}

    public static RotorCurve rotorHover(dev.mcmod.hdfv.part.air.RotorDesign d) {
        var rotor = new dev.mcmod.aerocore.api.aero.HelicopterRotor(d.geometry());
        double rpm = d.controls().designRpm(), omega = rpm * Math.PI / 30, r = d.blades().radiusM(), area = Math.PI * r * r, rho = SEA_LEVEL.densityKgM3();
        List<RotorPoint> points = new ArrayList<>();
        double lo = d.controls().minCollectiveDeg(), hi = d.controls().maxCollectiveDeg();
        int steps = Math.max(4, (int) Math.ceil(hi - lo));
        for (int k = 0; k <= steps; k++) {
            double c = lo + (hi - lo) * k / steps;
            var l = rotor.trimmed(Vec3d.ZERO, omega, SEA_LEVEL, new dev.mcmod.aerocore.api.aero.HelicopterRotor.Controls(Math.toRadians(c), 0, 0), Double.POSITIVE_INFINITY);
            double ideal = l.thrustN() > 0 ? Math.pow(l.thrustN(), 1.5) / Math.sqrt(2 * rho * area) : 0;
            points.add(new RotorPoint(c, l.thrustN(), l.powerW(), l.powerW() > 0 ? ideal / l.powerW() : 0));
        }
        double target = 0.08 * rotor.geometry().solidity() * rho * area * Math.pow(omega * r, 2);
        RotorPoint design = null;
        for (int k = 1; k < points.size(); k++) {
            RotorPoint a = points.get(k - 1), b = points.get(k);
            if (a.thrustN() <= target && b.thrustN() >= target) {
                double t = (target - a.thrustN()) / (b.thrustN() - a.thrustN());
                design = new RotorPoint(a.collectiveDeg() + t * (b.collectiveDeg() - a.collectiveDeg()), target, a.powerW() + t * (b.powerW() - a.powerW()),
                        a.figureOfMerit() + t * (b.figureOfMerit() - a.figureOfMerit()));
                break;
            }
        }
        return new RotorCurve(rpm, points, design);
    }

    // ---------------------------------------------------------------- bodies

    /** A body in the air at an angle of attack (deg, nose up): lift and drag (N) and the pitching moment about its centre of mass (N m, nose up). */
    public record BodyAirPoint(double alphaDeg, double liftN, double dragN, double momentNm) {}

    /** A body moving level at a speed: drag (N), lift (N, up) and the power its drag takes (W). */
    public record BodyRoadPoint(double speedMps, double dragN, double liftN, double powerW) {}

    /**
     * A body's bench at its loaded mass: in the air at a speed (sea level) the drag area and coefficient on its frontal
     * area, and lift, drag and moment against angle of attack; level from 10 to 50 m/s; on the water (a boat's purpose,
     * or any boat module) towed free to heave and trim up to its design speed, and its righting arms to 90 deg heel
     * (AeroCore's TowingTank), with its draft at rest (the bottom's lowest point). On the water it carries the mass its
     * settings say it carries for other parts, as ballast where they say, at the body's mid-height there; the tow stops
     * at the first lost run (it dived or reared up).
     */
    public record BodyBench(dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose purpose, double speedMps, double massKg, double dragAreaM2, double dragCoefficient,
                            List<BodyAirPoint> air, List<BodyRoadPoint> level, List<dev.mcmod.aerocore.api.vehicle.TowingTank.Run> water,
                            List<dev.mcmod.aerocore.api.vehicle.TowingTank.Heel> heel, double draftM) {
        /** The tow at the highest resistance below the design speed (the hump), or null without one. */
        public dev.mcmod.aerocore.api.vehicle.TowingTank.Run hump() {
            if (water == null) { return null; }
            for (int i = 1; i + 1 < water.size() && !water.get(i + 1).lost(); i++) {
                if (water.get(i).resistanceN() > water.get(i - 1).resistanceN() && water.get(i).resistanceN() > water.get(i + 1).resistanceN()) { return water.get(i); }
            }
            return null;
        }
    }

    public static BodyBench body(dev.mcmod.hdfv.part.body.BodyDesign d, double speedMps) {
        double speed = Math.max(speedMps, 1);
        var part = (dev.mcmod.aerocore.api.vehicle.part.BodyPart) d.compile();
        var aero = part.aerodynamics();
        Vec3d cg = d.mass().centre();
        List<BodyAirPoint> air = new ArrayList<>();
        for (int a = -10; a <= 20; a += 1) {
            double r = Math.toRadians(a);
            var l = aero.loads(new Vec3d(speed * Math.cos(r), -speed * Math.sin(r), 0), Vec3d.ZERO, SEA_LEVEL);
            double moment = l.moment().subtract(cg.cross(l.force())).z();
            air.add(new BodyAirPoint(a, l.liftN(), l.dragN(), moment));
        }
        double q = 0.5 * SEA_LEVEL.densityKgM3() * speed * speed, area = aero.zeroLiftDragAreaM2(speed, SEA_LEVEL);
        double frontal = d.shape().particulars().frontalAreaM2();
        List<BodyRoadPoint> level = new ArrayList<>();
        for (int v = 10; v <= 50; v += 2) {
            var l = aero.loads(new Vec3d(v, 0, 0), Vec3d.ZERO, SEA_LEVEL);
            level.add(new BodyRoadPoint(v, l.dragN(), l.liftN(), l.dragN() * v));
        }
        List<dev.mcmod.aerocore.api.vehicle.TowingTank.Run> water = null;
        List<dev.mcmod.aerocore.api.vehicle.TowingTank.Heel> heel = null;
        double draft = Double.NaN;
        boolean boat = d.settings().purpose() == dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose.BOAT
                || d.modules().stream().anyMatch(m -> m.kind().family == dev.mcmod.hdfv.part.body.BodyModuleDesign.Family.BOAT);
        if (boat) {
            List<dev.mcmod.aerocore.api.vehicle.VehicleBlueprint.Entry> entries = new ArrayList<>(List.of(new dev.mcmod.aerocore.api.vehicle.VehicleBlueprint.Entry("body", d,
                    dev.mcmod.aerocore.api.vehicle.Mount.at(Vec3d.ZERO), List.of())));
            if (d.settings().supportedKg() > 0) {
                double x = d.settings().supportedAt() * d.shape().lengthM();
                entries.add(new dev.mcmod.aerocore.api.vehicle.VehicleBlueprint.Entry("carried", new dev.mcmod.hdfv.part.BallastDesign("carried", d.settings().supportedKg()),
                        dev.mcmod.aerocore.api.vehicle.Mount.at(new Vec3d(x, d.shape().centreAt(x), 0)), List.of()));
            }
            var vehicle = new dev.mcmod.aerocore.api.vehicle.VehicleBlueprint("bench", entries, List.of()).compile();
            var waterState = dev.mcmod.aerocore.api.FluidState.FRESH_WATER_20C;
            var airState = dev.mcmod.aerocore.api.Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
            Vec3d gravity = new Vec3d(0, -9.80665, 0);
            var rest = dev.mcmod.aerocore.api.vehicle.TowingTank.rest(vehicle, waterState, airState, gravity);
            // the draft: the lowest point of the bottom below the surface
            for (var st : d.shape().stations()) {
                draft = Math.max(Double.isNaN(draft) ? Double.NEGATIVE_INFINITY : draft, -rest.worldPoint(vehicle.properties(), new Vec3d(st.xM(), d.shape().bottomAt(st.xM()), 0)).y());
            }
            double top = Math.max(d.settings().designSpeedMps(), 4);
            double[] speeds = new double[16];
            for (int i = 0; i < speeds.length; i++) { speeds[i] = top * (i + 1) / speeds.length; }
            water = new ArrayList<>();
            for (double v : speeds) {
                var run = dev.mcmod.aerocore.api.vehicle.TowingTank.run(vehicle, rest, waterState, airState, gravity, v);
                water.add(run);
                if (run.lost()) { break; }
            }
            heel = dev.mcmod.aerocore.api.vehicle.TowingTank.heel(vehicle, waterState, airState, gravity, 0, 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90);
        }
        return new BodyBench(d.settings().purpose(), speed, d.mass().massKg() + (boat ? d.settings().supportedKg() : 0), area, frontal > 0 ? area / frontal : Double.NaN, air, level, water, heel, draft);
    }

    // ---------------------------------------------------------------- 0.10: the ramjet family, pulsejets, boosters, afterburners

    /**
     * One point of an engine flying at a Mach number (0.10): thrust (N; negative is the drag of an unlit duct), specific
     * impulse (s; NaN unlit), whether it burns, and the bypass air over the core's (a turbo-ramjet; else 0).
     */
    public record MachPoint(double mach, double thrustN, double specificImpulseS, boolean lit, double bypassRatio) {}

    /** Mach numbers from a to b in n steps. */
    private static double[] machs(double from, double to, int n) {
        double[] m = new double[n];
        for (int i = 0; i < n; i++) { m[i] = from + (to - from) * i / (n - 1); }
        return m;
    }

    /** A ramjet at full throttle at an altitude from Mach 0.5 to its inlet's design Mach plus one: lit above its light-up Mach, else the cold duct's drag. */
    public static List<MachPoint> machSweep(dev.mcmod.hdfv.part.engine.RamjetDesign r, double altitudeM) {
        var s = r.model();
        List<MachPoint> out = new ArrayList<>();
        for (double m : machs(0.5, r.inlet().designMach() + 1, 31)) {
            Turbojet.Flight f = dev.mcmod.hdfv.part.engine.RamjetDesign.flight(m, altitudeM);
            boolean lit = m >= r.control().lightMach();
            var p = dev.mcmod.hdfv.part.engine.RamjetDesign.point(s, f, lit ? r.scheduledTemperature(f, 1) : f.totalTemperature());
            if (p == null) { out.add(new MachPoint(m, Double.NaN, Double.NaN, false, 0)); continue; }
            lit &= p.equivalenceRatio() >= r.control().leanLimit() || !lit;
            out.add(new MachPoint(m, p.thrustN(), lit && p.fuelKgPerS() > 0 ? p.specificImpulse() : Double.NaN, lit, 0));
        }
        return out;
    }

    /** A scramjet at its richest mixture across its table's Mach range at an altitude, started (as after a boost) and lit where its fuel lights. */
    public static List<MachPoint> machSweep(dev.mcmod.hdfv.part.engine.ScramjetDesign x, double altitudeM) {
        var map = x.model();
        var c = x.control();
        List<MachPoint> out = new ArrayList<>();
        for (double m : machs(c.minMach(), c.maxMach(), 21)) {
            Turbojet.Flight f = dev.mcmod.hdfv.part.engine.RamjetDesign.flight(m, altitudeM);
            var run = x.run(map, f, c.maxEquivalenceRatio(), true, true, true);
            var p = run.point();
            out.add(new MachPoint(m, p.thrustN(), run.lit() && p.fuelKgPerS() > 0 ? p.specificImpulse() : Double.NaN, run.lit(), 0));
        }
        return out;
    }

    /** A turbo-ramjet at its turbine temperature with full reheat from standstill to Mach 3.5 at an altitude (its core in equilibrium; the bypass by its schedule). */
    public static List<MachPoint> machSweep(dev.mcmod.hdfv.part.engine.TurboRamjetDesign t, double altitudeM) {
        var s = t.model().sizing();
        List<MachPoint> out = new ArrayList<>();
        for (double m : machs(0, 3.5, 29)) {
            Turbojet.Flight f = dev.mcmod.hdfv.part.engine.RamjetDesign.flight(Math.max(m, 0.001), altitudeM);
            try {
                var p = dev.mcmod.aerocore.api.engine.TurboRamjet.at(s, f, t.control().turbineInletK(), t.afterburner().maxTemperatureK());
                double isp = p.fuelKgPerS() > 0 ? p.thrustN() / (p.fuelKgPerS() * 9.80665) : Double.NaN;
                out.add(new MachPoint(m, p.thrustN(), isp, true, p.coreAirflowKgPerS() > 0 ? p.bypassAirflowKgPerS() / p.coreAirflowKgPerS() : 0));
            } catch (IllegalArgumentException e) {
                out.add(new MachPoint(m, Double.NaN, Double.NaN, false, 0));
            }
        }
        return out;
    }

    /** A pulsejet's point at a flight speed (m/s) at full fuel: thrust (N), fuel (kg/s), frequency (Hz), whether it runs. */
    public record PulsePoint(double speedMps, double thrustN, double fuelKgPerS, double frequencyHz, boolean running) {}

    /** A pulsejet's bench: thrust and frequency from standstill to its table's top speed, and one cycle of its chamber pressure at standstill. */
    public record PulseBench(List<PulsePoint> sweep, double[] pressureTracePa, double frequencyHz, double altitudeM) {}

    public static PulseBench pulsejet(dev.mcmod.hdfv.part.engine.PulsejetDesign d, double altitudeM) {
        var map = d.model();
        double fuel = d.control().maxFuelKgPerS();
        List<PulsePoint> sweep = new ArrayList<>();
        for (int i = 0; i <= 25; i++) {
            double v = dev.mcmod.hdfv.part.engine.PulsejetDesign.TOP_SPEED * i / 25;
            var c = map.at(flight(altitudeM, v), fuel);
            sweep.add(new PulsePoint(v, c.running() ? c.thrustN() : Math.min(0, c.thrustN()), c.running() ? fuel : 0, c.running() ? c.frequencyHz() : 0, c.running()));
        }
        var rest = map.at(flight(altitudeM, 0), fuel);
        return new PulseBench(sweep, rest.pressureTrace(), rest.frequencyHz(), altitudeM);
    }

    /** A booster's firing at an altitude: thrust (N) and chamber pressure (Pa) against time (s), its impulse (N s) and specific impulse (s). */
    public record RocketBench(double[] timeS, double[] thrustN, double[] chamberPa, double totalImpulse, double specificImpulseS, double burnS, double altitudeM) {}

    public static RocketBench rocket(dev.mcmod.hdfv.part.engine.SolidRocketDesign d, double altitudeM) {
        var p = d.performance();
        double pa = flight(altitudeM, 0).pressurePa(), burn = p.burnTimeS();
        int n = 81;
        double[] t = new double[n], thrust = new double[n], chamber = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = burn * 1.05 * i / (n - 1);
            thrust[i] = p.thrustAt(t[i], pa);
            chamber[i] = t[i] < burn ? p.pressureAt(t[i]) : 0;
        }
        return new RocketBench(t, thrust, chamber, p.totalImpulse(pa), p.specificImpulse(pa), burn, altitudeM);
    }

    /** An afterburning turbojet at full throttle at a flight condition, its afterburner lit to its largest temperature: thrust (N), fuel (kg/s). */
    public record WetPoint(double thrustN, double fuelKgPerS) {}

    public static WetPoint wet(JetEngineDesign e, Turbojet.Flight flight) {
        if (!e.afterburner().fitted()) { return null; }
        var m = e.model();
        double top = e.control().maxSpeedFraction();
        Turbojet.Point dry = Turbojet.quasiSteady(m.sizing(), m.lines(), flight, top, Turbojet.fuelForTurbineInlet(m.sizing(), m.lines(), flight, top, e.control().turbineInletK()));
        var r = dev.mcmod.aerocore.api.engine.Reheat.turbojet(m.sizing(), dry, flight, e.afterburner().reheat(), e.afterburner().maxTemperatureK());
        return new WetPoint(r.thrustN(), dry.fuelKgPerS() + r.fuelKgPerS());
    }
}
