package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.drive.TorqueConverter;
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
import dev.mcmod.hdfv.part.drive.ConverterDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.Gearboxes;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trial runs of gearboxes (0.6): the gear chart, Wong's traction diagram (tractive force in each gear against road
 * speed, with the road's resistance), the converter's curves and stall speed, and a run from rest on a road-load rig:
 * an engine, the gearbox and the car as one flywheel through the final drive (its mass at the tyre's radius, rolling
 * resistance and air drag as torques). The rig runs the parts themselves (the engine's map, the gearbox's hardware and
 * hdfv's rules), so the shifts and the clutch are what a car does; wheel slip and weight transfer are not in it (the
 * whole car's tests are).
 */
public final class DriveBench {
    private DriveBench() {}

    public static final double DT = 0.005, G = 9.80665, AIR_DENSITY = 1.225;
    /** The car the benches pull: tyre rolling radius (m), final drive, mass (kg), rolling resistance, drag area (m^2). */
    public static final double TYRE_RADIUS = 0.31, FINAL_RATIO = 3.7, MASS = 1400, ROLLING = 0.012, DRAG_AREA = 0.65;

    /**
     * The car on a road-load rig: one rotor at the wheels' speed (the car's mass at the tyre's radius, plus its wheels),
     * held back by rolling resistance (smoothed through zero over 0.3 rad/s) and drag (both as torques at the wheels).
     */
    public record RoadLoad(double massKg, double tyreRadiusM) implements PartDesign {
        @Override public String type() { return "hdfv:bench_road"; }
        @Override public String name() { return "road"; }
        @Override public List<DesignIssue> check() { return List.of(); }
        @Override public Map<String, Object> parameters() { return Map.of("massKg", massKg, "tyreRadiusM", tyreRadiusM); }

        @Override public Part compile() {
            RoadLoad design = this;
            double inertia = massKg * tyreRadiusM * tyreRadiusM, rolling = ROLLING * massKg * G * tyreRadiusM;
            double aero = 0.5 * AIR_DENSITY * DRAG_AREA * Math.pow(tyreRadiusM, 3);
            MassProperties mass = MassProperties.point(1, Vec3d.ZERO);
            return new Part() {
                @Override public PartDesign design() { return design; }
                @Override public MassProperties mass() { return mass; }
                @Override public List<CollisionSphere> collision() { return List.of(); }
                @Override public List<DesignIssue> issues() { return List.of(); }
                @Override public List<Rotor> rotors() { return List.of(new Rotor(Vec3d.X, inertia, false)); }
                @Override public void loads(PartStep step) {
                    double w = step.rotorSpeed(0), t = Math.tanh(w / 0.3);
                    double torque = -rolling * t - aero * w * Math.abs(w);
                    double slope = -rolling * (1 - t * t) / 0.3 - 2 * aero * Math.abs(w);
                    step.rotorTorque(0, torque, slope, true);
                }
            };
        }
    }

    /** An engine, a gearbox and the road on a rig held still: step it with its controls. */
    public static final class Rig {
        private static final FluidState AIR = Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
        private static final RigidBodyState HELD = new RigidBodyState(Vec3d.ZERO, Vec3d.ZERO, Orientation.IDENTITY, Vec3d.ZERO);
        public final Vehicle vehicle;
        public final VehicleState state;
        public final Map<String, Double> controls = new HashMap<>();
        public final GearboxDesign box;
        public final double finalRatio, tyreRadiusM;
        private final VehicleEnvironment environment = VehicleEnvironment.calm(-100, FluidState.FRESH_WATER_20C, AIR);
        private Map<String, Map<String, Double>> last = Map.of();
        public double time;

        Rig(GearboxDesign box, PistonEngineDesign engine, double finalRatio, double tyreRadiusM, double massKg, boolean starter) {
            this.box = box; this.finalRatio = finalRatio; this.tyreRadiusM = tyreRadiusM;
            List<Binding> engineBindings = new ArrayList<>(List.of(Binding.of("throttle", "throttle"), Binding.of("ignition", "ignition")));
            if (starter) { engineBindings.add(Binding.of("starter", "starter")); }
            List<Binding> boxBindings = new ArrayList<>();
            for (String role : box.compile().inputs()) { boxBindings.add(Binding.of(role, role)); }
            vehicle = new VehicleBlueprint("drive bench", List.of(
                    new VehicleBlueprint.Entry("engine", engine, Mount.at(Vec3d.ZERO), engineBindings),
                    new VehicleBlueprint.Entry("gearbox", box, Mount.at(new Vec3d(-0.5, 0, 0)), boxBindings),
                    new VehicleBlueprint.Entry("road", new RoadLoad(massKg, tyreRadiusM), Mount.at(new Vec3d(-1.5, 0, 0)))),
                    List.of(Connection.Shaft.ports("engine", null, "gearbox", "input", 1, 1), Connection.Shaft.ports("gearbox", "output", "road", null, finalRatio, 0.96)))
                    .compile();
            vehicle.warmUp();
            state = vehicle.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
            controls.putAll(Map.of("throttle", 0.0, "ignition", 1.0, "starter", 0.0, "gear", 0.0, "clutch", 0.0, "selector", 0.0, "shift", 0.0, "brake", 0.0));
        }

        public Map<String, Map<String, Double>> step() {
            VehicleDynamics.Result r = VehicleDynamics.step(vehicle, state, environment, controls, Vec3d.ZERO, List.of(), null, time, DT);
            state.setBody(HELD);
            time += DT;
            last = r.diagnostics();
            return last;
        }

        public double rotor(String id, String port) {
            Vehicle.Installed i = vehicle.installed(id);
            return state.rotorSpeed(i.firstRotor() + i.part().port(port));
        }
        public double engineRpm() { return state.rotorSpeed(vehicle.installed("engine").firstRotor()) * 30 / Math.PI; }
        public double speedMps() { return state.rotorSpeed(vehicle.installed("road").firstRotor()) * tyreRadiusM; }
        /** The gearbox's shown gear (its hardware's), state and selector. */
        public double gear() { return state.shown("gearbox", 0); }
        public double shiftState() { return state.shown("gearbox", 1); }
        public boolean running() { return state.internal("engine", PistonEngineDesign.STATE_RUNNING) > 0.5; }
    }

    public static Rig rig(GearboxDesign box, PistonEngineDesign engine, boolean starter) { return new Rig(box, engine, FINAL_RATIO, TYRE_RADIUS, MASS, starter); }

    public static Rig rig(GearboxDesign box, PistonEngineDesign engine, double finalRatio, double tyreRadiusM, double massKg, boolean starter) {
        return new Rig(box, engine, finalRatio, tyreRadiusM, massKg, starter);
    }

    /** One instant of a run: time (s), road speed (m/s), engine speed (rpm), gear (a CVT's ratio). */
    public record RunPoint(double timeS, double speedMps, double engineRpm, double gear) {}

    /** A run from rest at full throttle: its trace and the times to 100 km/h and over 400 m (NaN when not reached in the run). */
    public record Run(List<RunPoint> points, double to100S, double quarterS, double topMps) {}

    /**
     * A run from rest at full throttle for up to some seconds, as a driver would: an automatic, dual-clutch box or CVT
     * in D; a manual launched by slipping the clutch to hold the engine near the speed of its peak torque, and shifted
     * at 95 % of the rev limit (clutch down and throttle off, lever to the next gear, clutch back over 0.3 s).
     */
    public static Run accelerate(Rig rig, PistonEngineDesign engine, double seconds) {
        boolean manual = rig.box.kind() == Gearboxes.Kind.MANUAL;
        int gears = rig.box.forwardGears();
        double limit = engine.ignition().revLimitRpm(), launch = Math.min(Bench.fullLoad(engine).peakTorque().rpm(), 0.6 * limit);
        // warm and idling
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        rig.controls.put("selector", (double) Gearboxes.DRIVE);
        if (manual) { rig.controls.put("clutch", 1.0); rig.controls.put("gear", 1.0); }
        for (int i = 0; i < 0.5 / DT; i++) { rig.step(); }
        List<RunPoint> points = new ArrayList<>();
        double t0 = rig.time, distance = 0, to100 = Double.NaN, quarter = Double.NaN, top = 0;
        int gear = 1;
        double phase = 0;   // manual: 0 launching, 1 caught, 2 declutched for a shift, 3 letting the clutch back
        double phaseT = 0;
        for (int i = 0; i < seconds / DT; i++) {
            double rpm = rig.engineRpm();
            if (manual) {
                double primary = rig.rotor("gearbox", "primary"), input = rig.rotor("gearbox", "input");
                switch ((int) phase) {
                    case 0 -> {
                        rig.controls.put("throttle", 1.0);
                        rig.controls.put("clutch", Math.clamp((launch + 600 - rpm) / 1200, 0, 1));
                        if (Math.abs(input - primary) < 5 && rpm > 1500) { phase = 1; rig.controls.put("clutch", 0.0); }
                    }
                    case 1 -> {
                        if (rpm > 0.95 * limit && gear < gears) { phase = 2; phaseT = 0; rig.controls.put("clutch", 1.0); rig.controls.put("throttle", 0.0); gear++; rig.controls.put("gear", (double) gear); }
                    }
                    case 2 -> {
                        phaseT += DT;
                        if (rig.gear() == gear && phaseT > 0.1) { phase = 3; phaseT = 0; }
                    }
                    case 3 -> {
                        phaseT += DT;
                        rig.controls.put("clutch", Math.max(0, 1 - phaseT / 0.3));
                        rig.controls.put("throttle", Math.min(1, phaseT / 0.3));
                        if (phaseT > 0.3) { phase = 1; }
                    }
                    default -> { }
                }
            } else {
                rig.controls.put("throttle", 1.0);
            }
            rig.step();
            double v = rig.speedMps(), t = rig.time - t0;
            distance += v * DT;
            top = Math.max(top, v);
            if (Double.isNaN(to100) && v >= 100 / 3.6) { to100 = t; }
            if (Double.isNaN(quarter) && distance >= 400) { quarter = t; }
            if (i % 10 == 0) { points.add(new RunPoint(t, v, rpm, rig.box.kind() == Gearboxes.Kind.CVT ? rig.state.shown("gearbox", 2) : rig.gear())); }
            if (!Double.isNaN(quarter) && t > quarter + 1) { break; }
        }
        return new Run(points, to100, quarter, top);
    }

    // ---------------------------------------------------------------- static curves

    /** The engine's full-load torque (N m) at a speed (rpm), interpolated on its curve, 0 outside it. */
    public static double torqueAt(Bench.PistonCurve curve, double rpm) {
        List<Bench.PistonPoint> p = curve.points();
        if (p.isEmpty() || rpm < p.get(0).rpm() || rpm > p.get(p.size() - 1).rpm()) { return 0; }
        for (int i = 1; i < p.size(); i++) {
            if (rpm <= p.get(i).rpm()) {
                double f = (rpm - p.get(i - 1).rpm()) / (p.get(i).rpm() - p.get(i - 1).rpm());
                return p.get(i - 1).torqueNm() + f * (p.get(i).torqueNm() - p.get(i - 1).torqueNm());
            }
        }
        return p.get(p.size() - 1).torqueNm();
    }

    /** A converter's point with an engine at full load: engine speed (rpm), turbine torque (N m), speed ratio. */
    public record ConverterPoint(double engineRpm, double turbineNm, double speedRatio) {}

    /** Where an engine at full load settles on a converter whose turbine turns at a speed (rad/s). */
    public static ConverterPoint converterPoint(TorqueConverter c, Bench.PistonCurve curve, double turbineRadPerS, double revLimitRpm) {
        double first = curve.points().isEmpty() ? 1000 : curve.points().get(0).rpm();
        double lo = Math.max(turbineRadPerS, first * Math.PI / 30), hi = revLimitRpm * Math.PI / 30;
        java.util.function.DoubleUnaryOperator excess = w -> torqueAt(curve, w * 30 / Math.PI) - c.pumpTorque(w, Math.max(turbineRadPerS, 0) / w);
        double w;
        if (lo >= hi) { w = hi; }
        else if (excess.applyAsDouble(hi) > 0) { w = hi; }
        else if (excess.applyAsDouble(lo) < 0) { w = lo; }
        else {
            for (int i = 0; i < 60; i++) { double mid = 0.5 * (lo + hi); if (excess.applyAsDouble(mid) > 0) { lo = mid; } else { hi = mid; } }
            w = 0.5 * (lo + hi);
        }
        double nu = Math.clamp(turbineRadPerS / w, 0, 1);
        return new ConverterPoint(w * 30 / Math.PI, c.torqueRatio(nu) * c.pumpTorque(w, nu), nu);
    }

    /** The stall speed: the engine at full load against a held turbine, rpm. */
    public static double stallRpm(ConverterDesign c, PistonEngineDesign engine) {
        return converterPoint(c.converter(), Bench.fullLoad(engine), 0, engine.ignition().revLimitRpm()).engineRpm();
    }

    /** A converter's curve point: speed ratio, torque ratio, capacity factor K (rpm per sqrt(N m)), efficiency. */
    public record ConverterCurvePoint(double speedRatio, double torqueRatio, double capacityRpm, double efficiency) {}

    public static List<ConverterCurvePoint> converterCurve(ConverterDesign d) {
        TorqueConverter c = d.converter();
        List<ConverterCurvePoint> out = new ArrayList<>();
        for (int i = 0; i <= 97; i++) {
            double nu = i / 100.0, mu = c.torqueRatio(nu);
            out.add(new ConverterCurvePoint(nu, mu, c.capacity(nu) * 30 / Math.PI, mu * nu));
        }
        return out;
    }

    /** One gear's line on the traction diagram: rows {road speed m/s, tractive force N, engine rpm}. */
    public record TractionLine(String gear, List<double[]> points) {}

    /**
     * Wong's traction diagram for a gearbox behind an engine at full load: each gear's tractive force over its road
     * speeds (an automatic's and a CVT's through the converter, unlocked; a CVT's along the ratio that keeps the engine
     * at its full-throttle target), and the road's resistance (rolling and drag) for the bench car.
     */
    public record Traction(List<TractionLine> gears, List<double[]> resistance) {}

    public static Traction traction(GearboxDesign box, PistonEngineDesign engine, double finalRatio, double tyreRadiusM, double massKg) {
        Bench.PistonCurve curve = Bench.fullLoad(engine);
        double limit = engine.ignition().revLimitRpm(), eff = box.efficiency() * 0.96;
        boolean converter = box.kind() == Gearboxes.Kind.AUTOMATIC || box.kind() == Gearboxes.Kind.CVT;
        TorqueConverter c = converter ? box.converter().converter() : null;
        List<TractionLine> lines = new ArrayList<>();
        double topSpeed = 0;
        if (box.kind() == Gearboxes.Kind.CVT) {
            double low = box.variator().lowRatio(), high = box.variator().highRatio(), target = box.cvtControl().targetRpm(1);
            List<double[]> pts = new ArrayList<>();
            for (int i = 1; i <= 80; i++) {
                double v = i * 1.0, wheel = v / tyreRadiusM;
                double ratio = Math.clamp(target * Math.PI / 30 / (wheel * finalRatio), high, low), turbine = wheel * finalRatio * ratio;
                ConverterPoint p = converterPoint(c, curve, turbine, limit);
                double force = p.turbineNm() * ratio * finalRatio * eff / tyreRadiusM;
                if (p.engineRpm() >= limit - 1 && turbine * 30 / Math.PI >= limit) { break; }
                pts.add(new double[]{v, force, p.engineRpm()});
                topSpeed = v;
            }
            lines.add(new TractionLine("D", pts));
        } else {
            double[] r = box.ratios();
            for (int g = 0; g < r.length; g++) {
                List<double[]> pts = new ArrayList<>();
                for (int i = 1; i <= 200; i++) {
                    double v = i * 0.5, wheel = v / tyreRadiusM, input = wheel * finalRatio * r[g], inputRpm = input * 30 / Math.PI;
                    if (inputRpm > limit) { break; }
                    double torque, rpm;
                    if (converter) { ConverterPoint p = converterPoint(c, curve, input, limit); torque = p.turbineNm(); rpm = p.engineRpm(); }
                    else { torque = torqueAt(curve, inputRpm); rpm = inputRpm; }
                    if (torque <= 0) { continue; }
                    pts.add(new double[]{v, torque * r[g] * finalRatio * eff / tyreRadiusM, rpm});
                    topSpeed = Math.max(topSpeed, v);
                }
                lines.add(new TractionLine(String.valueOf(g + 1), pts));
            }
        }
        List<double[]> resistance = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            double v = topSpeed * 1.05 * i / 40;
            resistance.add(new double[]{v, ROLLING * massKg * G + 0.5 * AIR_DENSITY * DRAG_AREA * v * v});
        }
        return new Traction(lines, resistance);
    }

    /** A gearbox's bench: its traction diagram and run from rest behind an engine on the bench car, and a converter's curves and stall speed. */
    public record GearboxBench(String engine, Traction traction, Run run, List<ConverterCurvePoint> converter, double stallRpm) {}

    /** The engine the bench pairs a gearbox with: the sample nearest its rating (a 2.0 l four below 350 N m, the 5.7 l V8 above). */
    public static PistonEngineDesign engineFor(GearboxDesign box) {
        return box.gearbox().torqueRatingNm() < 350 ? dev.mcmod.hdfv.part.engine.SampleEngines.inlineFour() : dev.mcmod.hdfv.part.engine.SampleEngines.vEightOhv();
    }

    public static GearboxBench gearbox(GearboxDesign box) {
        PistonEngineDesign engine = engineFor(box);
        Traction traction = traction(box, engine, FINAL_RATIO, TYRE_RADIUS, MASS);
        Run run = accelerate(rig(box, engine, false), engine, 25);
        boolean converter = box.kind() == Gearboxes.Kind.AUTOMATIC || box.kind() == Gearboxes.Kind.CVT;
        return new GearboxBench(engine.name(), traction, run, converter ? converterCurve(box.converter()) : List.of(), converter ? stallRpm(box.converter(), engine) : Double.NaN);
    }

    // ---------------------------------------------------------------- transfer cases (0.25)

    /** A torque on a shaft (N m): the gearbox's output on the transfer rig. */
    public record TorqueSource(double torqueNm, boolean transverse) implements PartDesign {
        @Override public String type() { return "hdfv:bench_torque"; }
        @Override public String name() { return "torque"; }
        @Override public List<DesignIssue> check() { return List.of(); }
        @Override public Map<String, Object> parameters() { return Map.of("torqueNm", torqueNm, "transverse", transverse); }

        @Override public Part compile() {
            TorqueSource design = this;
            MassProperties mass = MassProperties.point(1, Vec3d.ZERO);
            return new Part() {
                @Override public PartDesign design() { return design; }
                @Override public MassProperties mass() { return mass; }
                @Override public List<CollisionSphere> collision() { return List.of(); }
                @Override public List<DesignIssue> issues() { return List.of(); }
                @Override public List<Rotor> rotors() { return List.of(new Rotor(transverse ? Vec3d.Z.scale(-1) : Vec3d.X, 0.01, false)); }
                @Override public void loads(PartStep step) { step.rotorTorque(0, torqueNm, 0, false); }
            };
        }
    }

    /**
     * A dynamometer on a propeller shaft: it holds the shaft at the speed on its "speed" input (rad/s) whatever the
     * torque, and reports the torque it takes from the shaft (diagnostic "torqueNm", positive when driven).
     */
    public record Dyno(String name) implements PartDesign {
        @Override public String type() { return "hdfv:bench_dyno"; }
        @Override public List<DesignIssue> check() { return List.of(); }
        @Override public Map<String, Object> parameters() { return Map.of(); }

        @Override public Part compile() {
            Dyno design = this;
            MassProperties mass = MassProperties.point(1, Vec3d.ZERO);
            return new Part() {
                @Override public PartDesign design() { return design; }
                @Override public MassProperties mass() { return mass; }
                @Override public List<CollisionSphere> collision() { return List.of(); }
                @Override public List<DesignIssue> issues() { return List.of(); }
                @Override public List<Rotor> rotors() { return List.of(new Rotor(Vec3d.X, 0.05, false)); }
                @Override public List<String> inputs() { return List.of("speed"); }
                @Override public void loads(PartStep step) {
                    step.row(new int[]{0}, new double[]{1}, step.input("speed", 0), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1);
                }
                @Override public void after(dev.mcmod.aerocore.api.vehicle.PartAfter step) { step.diagnostic("torqueNm", -step.rowTorque(0)); }
            };
        }
    }

    /**
     * A transfer case between a torque on its input and a dynamometer on each propeller shaft, held still: the case is
     * the part itself (AeroCore's TransferPart under hdfv's rule), so the split is what a vehicle gets. Controls: the
     * case's own ("mode", "range", "lock", "throttle") and the shafts' speeds "front_speed", "rear_speed" (rad/s).
     */
    public static final class TransferRig {
        private static final FluidState AIR = Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
        private static final RigidBodyState HELD = new RigidBodyState(Vec3d.ZERO, Vec3d.ZERO, Orientation.IDENTITY, Vec3d.ZERO);
        public final Vehicle vehicle;
        public final VehicleState state;
        public final Map<String, Double> controls = new HashMap<>();
        private final VehicleEnvironment environment = VehicleEnvironment.calm(-100, FluidState.FRESH_WATER_20C, AIR);
        private Map<String, Map<String, Double>> last = Map.of();
        public double time;

        TransferRig(dev.mcmod.hdfv.part.drive.TransferCaseDesign design, double inputNm) {
            List<Binding> bindings = new ArrayList<>();
            for (String role : design.compile().inputs()) { bindings.add(Binding.of(role, role)); }
            vehicle = new VehicleBlueprint("transfer bench", List.of(
                    new VehicleBlueprint.Entry("source", new TorqueSource(inputNm, design.transverse()), Mount.at(Vec3d.ZERO)),
                    new VehicleBlueprint.Entry("transfer", design, Mount.at(new Vec3d(-0.5, 0, 0)), bindings),
                    new VehicleBlueprint.Entry("front", new Dyno("front"), Mount.at(new Vec3d(1, 0, 0)), List.of(Binding.of("speed", "front_speed"))),
                    new VehicleBlueprint.Entry("rear", new Dyno("rear"), Mount.at(new Vec3d(-2, 0, 0)), List.of(Binding.of("speed", "rear_speed")))),
                    List.of(Connection.Shaft.ports("source", null, "transfer", "input", 1, 1), Connection.Shaft.ports("transfer", "front", "front", null, 1, 1),
                            Connection.Shaft.ports("transfer", "rear", "rear", null, 1, 1)))
                    .compile();
            vehicle.warmUp();
            state = vehicle.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
            controls.putAll(Map.of("mode", 0.0, "range", 0.0, "lock", 0.0, "throttle", 0.0, "front_speed", 0.0, "rear_speed", 0.0));
        }

        public void step() {
            VehicleDynamics.Result r = VehicleDynamics.step(vehicle, state, environment, controls, Vec3d.ZERO, List.of(), null, time, DT);
            state.setBody(HELD);
            time += DT;
            last = r.diagnostics();
        }

        /** The torque the front or the rear dynamometer took in the last step, N m. */
        public double torque(String shaft) { return last.getOrDefault(shaft, Map.of()).getOrDefault("torqueNm", 0.0); }
        /** The case's range (1 high, 2 low) and its second shown value (mode, lock or the coupling's share of its largest torque). */
        public double range() { return state.shown("transfer", 0); }
        public double second() { return state.shown("transfer", 1); }
    }

    public static TransferRig transferRig(dev.mcmod.hdfv.part.drive.TransferCaseDesign design, double inputNm) { return new TransferRig(design, inputNm); }

    /** One point of a split: the front shaft's speed less the rear's (rad/s), the torque each shaft gets (N m). */
    public record SplitPoint(double differenceRadPerS, double frontNm, double rearNm) {
        /** The front's share of what the two shafts get together; NaN when they get nothing. */
        public double frontShare() { return frontNm + rearNm == 0 ? Double.NaN : frontNm / (frontNm + rearNm); }
    }

    /**
     * One mode's split over the speed difference. Modes: "two_high" and "four_high" (part-time), "open", "torsen",
     * "viscous" and "locked" (full-time), "slip" and "preload" (on-demand: the coupling on slip alone, and preloaded by
     * the rule at full throttle and low speed). locked: the shafts are geared together, so their speeds cannot differ;
     * the points are then the limits (all of the torque to the rear when the front would run ahead, to the front
     * otherwise: whichever axle grips takes it).
     */
    public record Split(String mode, boolean locked, List<SplitPoint> points) {}

    /**
     * A transfer case's bench: the engine and gearbox it sits behind, the torque on its input (the engine's largest in
     * first gear), each mode's split, the largest tractive force of the bench car in high and in low range (the best gear
     * at each road speed; low null without a low range) with the road's resistance, and the crawl ratio (first gear, low
     * range and final drive together).
     */
    public record TransferBench(String engine, String gearbox, double inputNm, List<Split> splits, TractionLine high, TractionLine low, List<double[]> resistance,
                                double crawlRatio) {}

    /** The split is swept to this speed difference either way (rad/s; about 570 rpm between the shafts), at SPLIT_POINTS points a side. */
    public static final double SPLIT_DIFFERENCE = 60, SPLIT_SPEED = 100;
    public static final int SPLIT_POINTS = 20;

    /** The gearbox the bench puts before a transfer case: the five-speed behind the 2.0 l four below 350 N m, the six-speed behind the V8 above. */
    public static GearboxDesign gearboxFor(dev.mcmod.hdfv.part.drive.TransferCaseDesign d) {
        return d.torqueRatingNm() < 350 ? dev.mcmod.hdfv.part.drive.SampleGearboxes.fiveSpeedManual() : dev.mcmod.hdfv.part.drive.SampleGearboxes.sixSpeedManual();
    }

    /**
     * A mode's split on the rig: the shafts held SPLIT_SPEED apart by each difference in turn (an on-demand case's
     * primary shaft kept at the reference speed, the other moved; otherwise both about it), the torques averaged once
     * settled. Zero difference is left out: a limited-slip or preloaded case holds there, and the split is whatever
     * the axles' grip makes it.
     */
    public static List<SplitPoint> split(dev.mcmod.hdfv.part.drive.TransferCaseDesign d, double inputNm, Map<String, Double> controls, double referenceRadPerS) {
        TransferRig rig = new TransferRig(d, inputNm);
        rig.controls.putAll(controls);
        boolean onDemand = d.kind() == dev.mcmod.aerocore.api.vehicle.part.TransferPart.Kind.ON_DEMAND;
        List<SplitPoint> out = new ArrayList<>();
        double step = SPLIT_DIFFERENCE / SPLIT_POINTS;
        for (int j = -SPLIT_POINTS; j < SPLIT_POINTS; j++) {
            double difference = (j + 0.5) * step, front, rear;
            if (onDemand) {
                front = d.frontPrimary() ? referenceRadPerS : referenceRadPerS + difference;
                rear = d.frontPrimary() ? referenceRadPerS - difference : referenceRadPerS;
            } else {
                front = referenceRadPerS + difference / 2;
                rear = referenceRadPerS - difference / 2;
            }
            rig.controls.put("front_speed", front);
            rig.controls.put("rear_speed", rear);
            double f = 0, r = 0;
            for (int i = 0; i < 40; i++) {
                rig.step();
                if (i >= 30) { f += rig.torque("front") / 10; r += rig.torque("rear") / 10; }
            }
            out.add(new SplitPoint(difference, f, r));
        }
        return out;
    }

    /** The limits of shafts geared together: the torque to the rear when the front would run ahead, to the front otherwise. */
    private static List<SplitPoint> lockedSplit(double outputNm) {
        List<SplitPoint> out = new ArrayList<>();
        double step = SPLIT_DIFFERENCE / SPLIT_POINTS;
        for (int j = -SPLIT_POINTS; j < SPLIT_POINTS; j++) {
            double difference = (j + 0.5) * step;
            out.add(new SplitPoint(difference, difference < 0 ? outputNm : 0, difference < 0 ? 0 : outputNm));
        }
        return out;
    }

    /** The largest tractive force over the gears at each road speed (rows as a TractionLine's), times an efficiency. */
    private static TractionLine envelope(String label, Traction traction, double efficiency) {
        java.util.TreeMap<Double, double[]> best = new java.util.TreeMap<>();
        for (TractionLine line : traction.gears()) {
            for (double[] p : line.points()) {
                double[] was = best.get(p[0]);
                if (was == null || p[1] * efficiency > was[1]) { best.put(p[0], new double[]{p[0], p[1] * efficiency, p[2]}); }
            }
        }
        return new TractionLine(label, new ArrayList<>(best.values()));
    }

    /** The largest force of a line, N (0 for an empty one). */
    public static double peakN(TractionLine line) {
        double peak = 0;
        for (double[] p : line.points()) { peak = Math.max(peak, p[1]); }
        return peak;
    }

    public static TransferBench transferCase(dev.mcmod.hdfv.part.drive.TransferCaseDesign d) {
        GearboxDesign box = gearboxFor(d);
        PistonEngineDesign engine = engineFor(box);
        double first = box.ratios()[0], inputNm = Bench.fullLoad(engine).peakTorque().torqueNm() * first * box.efficiency();
        List<Split> splits = new ArrayList<>();
        switch (d.kind()) {
            case PART_TIME -> {
                splits.add(new Split("two_high", false, split(d, inputNm, Map.of("mode", 0.0), SPLIT_SPEED)));
                splits.add(new Split("four_high", true, lockedSplit(inputNm * d.efficiency())));
            }
            case FULL_TIME -> {
                splits.add(new Split(d.centre().name().toLowerCase(java.util.Locale.ROOT), false, split(d, inputNm, Map.of("lock", 0.0), SPLIT_SPEED)));
                if (d.lockable()) { splits.add(new Split("locked", true, lockedSplit(inputNm * d.efficiency()))); }
            }
            case ON_DEMAND -> {
                splits.add(new Split("slip", false, split(d, inputNm, Map.of("throttle", 0.0), SPLIT_SPEED)));
                if (d.preloadShare() > 0 && d.preloadBelowRpm() > 0) {
                    // the rule preloads below a propeller-shaft speed: the primary shaft held at half of it
                    double reference = Math.min(SPLIT_SPEED, 0.5 * d.preloadBelowRpm() * Math.PI / 30);
                    splits.add(new Split("preload", false, split(d, inputNm, Map.of("throttle", 1.0), reference)));
                }
            }
        }
        Traction high = traction(box, engine, FINAL_RATIO, TYRE_RADIUS, MASS);
        TractionLine low = d.lowRatio() > 1 ? envelope("L", traction(box, engine, FINAL_RATIO * d.lowRatio(), TYRE_RADIUS, MASS), d.efficiency()) : null;
        return new TransferBench(engine.name(), box.name(), inputNm, splits, envelope("H", high, d.efficiency()), low, high.resistance(),
                first * d.lowRatio() * FINAL_RATIO);
    }
}
