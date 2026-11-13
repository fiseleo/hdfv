package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.CrankKinematics;
import dev.mcmod.aerocore.api.engine.DieselMap;
import dev.mcmod.aerocore.api.engine.Dyno;
import dev.mcmod.aerocore.api.engine.EngineCycle;
import dev.mcmod.aerocore.api.engine.EngineMap;
import dev.mcmod.aerocore.api.engine.EngineOil;
import dev.mcmod.aerocore.api.engine.EngineRuntime;
import dev.mcmod.aerocore.api.engine.Friction;
import dev.mcmod.aerocore.api.engine.Fuel;
import dev.mcmod.aerocore.api.engine.PistonGeometry;
import dev.mcmod.aerocore.api.engine.Turbocharger;
import dev.mcmod.aerocore.api.engine.ValveFlow;
import dev.mcmod.aerocore.api.engine.ValveLift;
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
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * A four-stroke piston engine assembled from designed components (block, head, pistons, rods, crankshaft, valves,
 * camshaft, spark plugs, injectors, valve train, fuel, ignition, lubrication, cooling, intake, exhaust and, 0.8, a
 * turbocharger). Its crank turns about the part's +X axis and drives a shaft (Connection.Shaft) to whatever it powers.
 * It fires by spark, or by compression when its fuel is diesel (0.8): then the head has no spark plugs (the plug
 * component is its glow plugs, one a cylinder), the fuel is injected directly at high pressure, there is no throttle
 * (the fuel is the load, up to the smoke-limited full-load lambda), the "ignition" component's advance is the latest
 * start of injection, and the peak cylinder pressure is held to PEAK_PRESSURE_LIMIT_PA.
 * <p>
 * What the components decide, rather than a parameter: the compression ratio (chamber, gasket, deck clearance and
 * piston crown), the flame's travel (plug count and position over the bore), breathing (valves, ports, cam, runners,
 * throttle), knock (octane, compression, chamber, spark control), friction (bearings, rings, valve train, oil),
 * inertia (crank, flywheel, rods, pistons), mass, and the limits (valve float, piston speed, rod fatigue, crown
 * strength, injector flow, cooling).
 * <p>
 * Performance comes from the crank-angle cycle simulation (EngineCycle, calibrated per point by Dyno) on a map of
 * speed and manifold pressure (EngineMap; a diesel's of speed, boost and load, DieselMap), built off the game thread
 * (warmUp); friction (Friction) is applied at run time with the oil's viscosity at the engine's temperature, so a cold
 * engine is weaker. A turbocharger (Turbocharger) raises the pressure before the throttle up to the most its turbine
 * holds at the speed (with the wastegate shut), following its target with the rotor's lag; its turbine is the
 * exhaust's back pressure in the map. The engine asks the vehicle's tanks for the fluids its fuel system takes
 * (FuelGrades; also while cranking) and stops when they give less than half. Inputs: "throttle" 0..1, "ignition"
 * (1 runs, 0 stops; unbound = on) and "starter" (0.6: held at 1 the starter cranks a stopped engine; unbound, it cranks
 * whenever the ignition is on and the engine is not running, as before, so a stalled engine restarts by itself).
 * Internal states: manifold pressure over ambient, engine temperature (K), running, boost (compressor outlet over ambient).
 */
public record PistonEngineDesign(String name, EngineBlockDesign block, CylinderHeadDesign head, PistonDesign piston, ConnectingRodDesign rod,
                                 CrankshaftDesign crank, ValveDesign valves, CamshaftDesign camshaft, SparkPlugDesign sparkPlug,
                                 FuelInjectorDesign injector, ValvetrainDesign valvetrain, FuelSystemDesign fuelSystem, IgnitionDesign ignition,
                                 LubricationDesign lubrication, CoolingDesign cooling, IntakeDesign intake, ExhaustDesign exhaust,
                                 TurbochargerDesign turbo) implements PartDesign {
    public static final String TYPE = "hdfv:piston_engine";
    /** Wall margin around a valve seat and the bridge between seats, m (typical seat-insert walls; rules of the packing check). */
    public static final double SEAT_MARGIN = 0.0015, VALVE_BRIDGE = 0.003;
    /** Highest mean piston speed in production and racing engines, about 25 m/s (Formula One); beyond it is extrapolation. */
    public static final double PISTON_SPEED_LIMIT = 25;
    /** Highest cylinder pressure a diesel is calibrated to, Pa: 180 bar, a passenger-car and light-truck design limit (Mollenhauer and Tschoeke, Handbook of Diesel Engines: 160-200 bar). */
    public static final double PEAK_PRESSURE_LIMIT_PA = 18e6;
    /** Fraction of a diesel's fuel that burns (Heywood: combustion inefficiency of diesels under 2 %), a typical value. */
    public static final double DIESEL_COMBUSTION_EFFICIENCY = 0.985;
    /** Crank degrees a diesel's injection may last at full load (Heywood: 20-40), and the compressed air it injects against, Pa. */
    public static final double INJECTION_WINDOW_DEG = 35, DIESEL_CYLINDER_PA = 6e6;

    /** The pre-0.8 constructor: no turbocharger. */
    public PistonEngineDesign(String name, EngineBlockDesign block, CylinderHeadDesign head, PistonDesign piston, ConnectingRodDesign rod,
                              CrankshaftDesign crank, ValveDesign valves, CamshaftDesign camshaft, SparkPlugDesign sparkPlug,
                              FuelInjectorDesign injector, ValvetrainDesign valvetrain, FuelSystemDesign fuelSystem, IgnitionDesign ignition,
                              LubricationDesign lubrication, CoolingDesign cooling, IntakeDesign intake, ExhaustDesign exhaust) {
        this(name, block, head, piston, rod, crank, valves, camshaft, sparkPlug, injector, valvetrain, fuelSystem, ignition, lubrication, cooling,
                intake, exhaust, TurbochargerDesign.none());
    }

    public PistonEngineDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{block, head, piston, rod, crank, valves, camshaft, sparkPlug, injector, valvetrain, fuelSystem, ignition,
                lubrication, cooling, intake, exhaust, turbo}) { Objects.requireNonNull(o, "component"); }
    }

    @Override public String type() { return TYPE; }

    /** The components with their JSON keys, in order. */
    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("block", block); m.put("head", head); m.put("piston", piston); m.put("rod", rod); m.put("crank", crank); m.put("valves", valves);
        m.put("camshaft", camshaft); m.put("sparkPlug", sparkPlug); m.put("injector", injector); m.put("valvetrain", valvetrain);
        m.put("fuelSystem", fuelSystem); m.put("ignition", ignition); m.put("lubrication", lubrication); m.put("cooling", cooling);
        m.put("intake", intake); m.put("exhaust", exhaust); m.put("turbo", turbo);
        return m;
    }

    // ---------------------------------------------------------------- derived geometry

    public double bore() { return block.boreM(); }
    public double stroke() { return crank.strokeM(); }
    public double pistonArea() { return Math.PI * bore() * bore() / 4; }
    /** Swept volume of one cylinder, m^3. */
    public double cylinderDisplacement() { return pistonArea() * stroke(); }
    /** Swept volume of the engine, m^3. */
    public double displacement() { return cylinderDisplacement() * block.cylinders(); }
    /** Distance from the piston crown at TDC down from the deck, m (negative: it stands proud of the deck). */
    public double deckClearance() { return PistonGeometry.deckClearance(block.deckHeightM(), stroke(), rod.lengthM(), piston.compressionHeightM()); }
    /** Clearance volume of one cylinder: chamber + gasket + deck clearance - crown, m^3. */
    public double clearanceVolume() {
        return PistonGeometry.clearanceVolume(head.chamberVolumeM3(), bore(), head.gasketThicknessM(), deckClearance(), piston.crownVolumeM3());
    }
    public double compressionRatio() { return PistonGeometry.compressionRatio(cylinderDisplacement(), clearanceVolume()); }

    /** Whether it fires by compression (its fuel is diesel). */
    public boolean diesel() { return fuelSystem.diesel(); }
    /** Spark plugs per cylinder, or a diesel's one glow plug (both the plug component). */
    public int plugsPerCylinder() { return diesel() ? 1 : head.sparkPlugs(); }
    /** The fluids it runs on (FuelGrades). */
    public Set<String> acceptedFluids() { return fuelSystem.acceptedFluids(); }

    // ---------------------------------------------------------------- checks

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (!diesel() && head.sparkPlugs() == 0) { issues.add(DesignIssue.invalid("head.sparkPlugs", "A spark-ignition engine needs a spark plug")); }
        if (diesel() && head.sparkPlugs() > 0) { issues.add(DesignIssue.invalid("head.sparkPlugs", "A diesel fires by compression: no spark plugs (the plug component is its glow plugs)")); }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        double bore = bore();
        double clearance = bore - piston.diameterM();
        if (!(clearance > 0)) { issues.add(DesignIssue.invalid("piston.diameterM", "The piston is not smaller than the bore")); }
        else if (clearance > 0.0005) { issues.add(DesignIssue.invalid("piston.diameterM", "The piston is more than 0.5 mm smaller than the bore: it would rock and blow by")); }
        else { DesignIssue.range(issues, "pistonClearanceM", clearance, 0.00002, 0.00015, "running clearances of aluminium pistons"); }
        double bigEnd = rod.bigEndBoreM() - crank.pinDiameterM();
        if (bigEnd < 0 || bigEnd > 0.0002) { issues.add(DesignIssue.invalid("rod.bigEndBoreM", "The rod's big end does not fit the crankpin (0-0.2 mm bearing clearance)")); }
        double smallEnd = rod.smallEndBoreM() - piston.pinDiameterM();
        if (smallEnd < 0 || smallEnd > 0.0001) { issues.add(DesignIssue.invalid("rod.smallEndBoreM", "The rod's small end does not fit the piston pin")); }
        int rodsPerPin = block.layout() == EngineBlockDesign.Layout.VEE ? 2 : 1;
        if (rodsPerPin * rod.bigEndWidthM() > crank.pinWidthM() + 1e-9) { issues.add(DesignIssue.invalid("rod.bigEndWidthM", "The rods are wider than the crankpin")); }
        if (deckClearance() < -head.gasketThicknessM()) { issues.add(DesignIssue.invalid("block.deckHeightM", "The piston rises past the gasket into the head")); }
        if (!(clearanceVolume() > 0)) { issues.add(DesignIssue.invalid("piston.crownVolumeM3", "No clearance volume left: the piston hits the head")); }
        else if (diesel()) { DesignIssue.range(issues, "compressionRatio", compressionRatio(), 14, 24, "direct-injection diesels; below about 14 they will not light cold"); }
        else { DesignIssue.range(issues, "compressionRatio", compressionRatio(), 6, 14, "spark-ignition engines; knock and burn correlations fitted near 9-12"); }
        if (!valvesFit()) { issues.add(DesignIssue.invalid("valves", "The valve heads do not fit in the bore")); }
        if (diesel() && piston.crownPressureLimit() < PEAK_PRESSURE_LIMIT_PA) {
            issues.add(DesignIssue.invalid("piston.crownThicknessM", "The crown is too thin for a diesel's 180 bar peak pressure"));
        }
        if (sparkPlug.threadDiameterM() > bore / 4) { issues.add(DesignIssue.invalid("sparkPlug.threadDiameterM", "The plug is too large for the bore")); }
        DesignIssue.range(issues, "intakeLiftOverDiameter", camshaft.intakeLiftM() / valves.intakeHeadM(), 0.15, 0.35, "lift beyond a third of the valve diameter flows no more (port limited)");
        DesignIssue.range(issues, "exhaustLiftOverDiameter", camshaft.exhaustLiftM() / valves.exhaustHeadM(), 0.15, 0.35, "lift beyond a third of the valve diameter flows no more (port limited)");
        if (fuelSystem.delivery() == FuelSystemDesign.Delivery.DIRECT_INJECTION && injector.kind() != FuelInjectorDesign.Kind.DIRECT) {
            issues.add(DesignIssue.invalid("injector.kind", "Direct injection needs direct injectors"));
        }
        if (fuelSystem.delivery() == FuelSystemDesign.Delivery.PORT_INJECTION && injector.kind() != FuelInjectorDesign.Kind.PORT) {
            issues.add(DesignIssue.invalid("injector.kind", "Port injection needs port injectors"));
        }
        if (intake.throttleBodies() > block.cylinders()) { issues.add(DesignIssue.invalid("intake.throttleBodies", "More throttle bodies than cylinders")); }
        DesignIssue.range(issues, "meanPistonSpeedAtRevLimit", 2 * stroke() * ignition.revLimitRpm() / 60, 0, PISTON_SPEED_LIMIT, "production and racing engines");
        DesignIssue.range(issues, "rodRatio", rod.lengthM() / stroke(), 1.4, 2.2, "production engines");
        return issues;
    }

    /** Whether the valve heads fit in the bore (PistonGeometry.valvesFit with SEAT_MARGIN and VALVE_BRIDGE). */
    public boolean valvesFit() {
        return PistonGeometry.valvesFit(bore(), head.intakeValves(), valves.intakeHeadM(), head.exhaustValves(), valves.exhaustHeadM(),
                head.valveAngleDeg(), SEAT_MARGIN, VALVE_BRIDGE);
    }

    // ---------------------------------------------------------------- mass

    /** Mass of the whole engine with its systems, kg. */
    public double totalMassKg() {
        int n = block.cylinders();
        int camshafts = head.layout() == CylinderHeadDesign.Layout.OHV ? 1 : block.banks() * (head.layout() == CylinderHeadDesign.Layout.DOHC ? 2 : 1);
        int valvesPerCylinder = head.intakeValves() + head.exhaustValves();
        double m = block.massKg() + head.massFor(block) + n * (piston.massKg() + rod.massKg()) + crank.massFor(block)
                + n * (head.intakeValves() * valves.intakeValveMassKg() + head.exhaustValves() * valves.exhaustValveMassKg())
                + camshafts * camshaft.massFor(block, head.layout() == CylinderHeadDesign.Layout.DOHC ? Math.max(head.intakeValves(), head.exhaustValves()) : valvesPerCylinder)
                + n * plugsPerCylinder() * sparkPlug.massKg()
                + (fuelSystem.delivery() == FuelSystemDesign.Delivery.CARBURETTOR ? 0 : n * fuelSystem.injectorsPerCylinder() * injector.massKg())
                + valvetrain.massFor(n * valvesPerCylinder) + fuelSystem.massKg() + ignition.massKg() + lubrication.massKg() + cooling.massKg()
                + intake.massFor(n) + exhaust.massKg() + turbo.massKg();
        return m;
    }

    /** Reciprocating mass per cylinder (piston with pin and rings plus the rod's small-end share), kg. */
    public double reciprocatingMass() { return piston.massKg() + rod.reciprocatingFraction() * rod.massKg(); }

    /** Moment of inertia of everything turning with the crank, kg m^2: crank, flywheel, rods' big ends and, averaged over a turn, half the reciprocating mass at the crank radius. */
    public double crankInertia() {
        return PistonGeometry.crankSystemInertia(crank.rotatingInertia(block), block.cylinders(), rod.massKg(), rod.reciprocatingFraction(),
                reciprocatingMass(), stroke() / 2);
    }

    // ---------------------------------------------------------------- the cycle model

    /** The fuel. */
    public Fuel fuel() { return fuelSystem.fuelModel(); }

    /** EngineCycle spec of one cylinder at a lambda and cam phases (crank degrees later). */
    public EngineCycle.Spec cylinderSpec(double lambda, double intakePhaseDeg, double exhaustPhaseDeg) {
        CrankKinematics k = new CrankKinematics(bore(), stroke(), rod.lengthM(), clearanceVolume());
        ValveLift in = camshaft.intakeLift(intakePhaseDeg), ex = camshaft.exhaustLift(exhaustPhaseDeg);
        double headFactor = head.chamberAreaFactor(bore());
        boolean ci = diesel();
        return new EngineCycle.Spec(k, head.intakeValves(), valves.intakeHeadM(), head.intakeThroatRatio() * valves.intakeHeadM(), valves.intakeStemM(),
                head.portFlowCoefficient(), in, head.exhaustValves(), valves.exhaustHeadM(), head.exhaustThroatRatio() * valves.exhaustHeadM(),
                valves.exhaustStemM(), head.portFlowCoefficient(), ex, intake.runnerLengthM(), intake.runnerDiameterM(), fuel(), lambda,
                ci ? DIESEL_COMBUSTION_EFFICIENCY : COMBUSTION_EFFICIENCY, fuelSystem.delivery() == FuelSystemDesign.Delivery.DIRECT_INJECTION,
                ci ? bore() / 2 : head.flameTravel(bore()), cooling.wallTemperatureK(), headFactor, 1,
                ci ? new EngineCycle.CompressionIgnition(PEAK_PRESSURE_LIMIT_PA) : null);
    }
    /** Fraction of the fuel that burns in a warm engine at lambda >= 1 (Heywood: 95-98 % for spark-ignition engines; crevices and quench), a typical value. */
    public static final double COMBUSTION_EFFICIENCY = 0.97;

    /** The friction model's view of this engine. */
    public Friction.Engine frictionModel() {
        int camshafts = head.layout() == CylinderHeadDesign.Layout.OHV ? 1 : block.banks() * (head.layout() == CylinderHeadDesign.Layout.DOHC ? 2 : 1);
        return new Friction.Engine(bore(), stroke(), block.cylinders(), compressionRatio(), block.mainBearings(),
                crank.mainDiameterM(), crank.mainWidthM(), crank.pinDiameterM(), rod.bigEndWidthM(), camshafts * camshaft.bearingsPerShaft(),
                block.cylinders() * (head.intakeValves() + head.exhaustValves()), Math.max(camshaft.intakeLiftM(), camshaft.exhaustLiftM()),
                valvetrain.friction(head.layout()), valvetrain.rollerFollowers(), piston.ringTensionRatio(), block.linerRoughness());
    }

    /** Relative air-fuel ratio the fuel system targets at a manifold pressure ratio: part-load value up to 0.7, full-load value at 1. */
    public double targetLambda(double pressureRatio) {
        double f = Math.clamp((pressureRatio - 0.7) / 0.3, 0, 1);
        return fuelSystem.partLoadLambda() + f * (fuelSystem.fullLoadLambda() - fuelSystem.partLoadLambda());
    }

    /**
     * Most fuel the injectors deliver per cylinder per cycle at a speed (rpm), kg; infinite for a carburettor. A
     * diesel's injectors spray against about 6 MPa of compressed air within INJECTION_WINDOW_DEG of crank angle.
     */
    public double injectorLimit(double rpm, double manifoldPa) {
        if (fuelSystem.delivery() == FuelSystemDesign.Delivery.CARBURETTOR) { return Double.POSITIVE_INFINITY; }
        if (diesel()) {
            double window = INJECTION_WINDOW_DEG / (6 * Math.max(rpm, 1));
            return fuelSystem.injectorsPerCylinder() * injector.flowM3PerS(Math.max(0, fuelSystem.fuelPressurePa() - DIESEL_CYLINDER_PA)) * fuel().liquidDensityKgPerM3() * window;
        }
        double dp = fuelSystem.delivery() == FuelSystemDesign.Delivery.DIRECT_INJECTION ? fuelSystem.fuelPressurePa() - 2e6 : fuelSystem.fuelPressurePa();
        double cycle = 120 / rpm;
        return fuelSystem.injectorsPerCylinder() * injector.flowM3PerS(dp) * fuel().liquidDensityKgPerM3() * FuelInjectorDesign.MAX_DUTY * cycle;
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new PistonEngineDesign(f.string("name", "engine"),
                component(f, "block", EngineBlockDesign::read), component(f, "head", CylinderHeadDesign::read), component(f, "piston", PistonDesign::read),
                component(f, "rod", ConnectingRodDesign::read), component(f, "crank", CrankshaftDesign::read), component(f, "valves", ValveDesign::read),
                component(f, "camshaft", CamshaftDesign::read), component(f, "sparkPlug", SparkPlugDesign::read), component(f, "injector", FuelInjectorDesign::read),
                component(f, "valvetrain", ValvetrainDesign::read), component(f, "fuelSystem", FuelSystemDesign::read), component(f, "ignition", IgnitionDesign::read),
                component(f, "lubrication", LubricationDesign::read), component(f, "cooling", CoolingDesign::read), component(f, "intake", IntakeDesign::read),
                component(f, "exhaust", ExhaustDesign::read),
                f.has("turbo") ? component(f, "turbo", TurbochargerDesign::read) : TurbochargerDesign.none());   // engines saved before 0.8 have none
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

    // ---------------------------------------------------------------- the map

    /** Speeds (rpm) and manifold pressure ratios of the map. */
    public double[] mapSpeeds() {
        double top = ignition.revLimitRpm() * 1.08, low = 500;
        int n = 40;   // a tuned runner's ram peaks are a few hundred rpm wide
        double[] rpms = new double[n];
        for (int i = 0; i < n; i++) { rpms[i] = low + (top - low) * i / (n - 1); }
        return rpms;
    }
    public static final double[] MAP_PRESSURES = {0.12, 0.2, 0.3, 0.42, 0.56, 0.7, 0.85, 1.0};
    /** Standard ambient the map is computed at (pressure Pa, temperature K); run time scales flows by the actual air density. */
    public static final double AMBIENT_PA = 101325, AMBIENT_K = 298.15, INTAKE_HEATING_K = 10;
    /** Exhaust temperature for the first guess of a turbine's back pressure, K: spark-ignition and diesel at full load (Heywood ch. 6.8). */
    public static final double SI_EXHAUST_K = 1100, DIESEL_EXHAUST_K = 850;
    /** Volumetric efficiency for the first guess of a turbocharged engine's air flow (a typical value). */
    public static final double GUESS_VOLUMETRIC_EFFICIENCY = 0.9;
    /** A diesel map's loads (fuel-air equivalence ratios), lightest to the full load, and its idle governor's base load. */
    public static final double[] DIESEL_LOADS = {0.09, 0.15, 0.22, 0.3, 0.42, 0.55, 0.68, 0.8};   // lambda 11 (AeroCore takes up to 12) to 1.25
    public static final double DIESEL_IDLE_PHI = 0.06;

    /** The manifold pressure ratios of a spark-ignition map: MAP_PRESSURES, then four steps up to the wastegate's boost when turbocharged. */
    public double[] mapPressures() {
        if (!turbo.fitted()) { return MAP_PRESSURES.clone(); }
        double[] out = java.util.Arrays.copyOf(MAP_PRESSURES, MAP_PRESSURES.length + 4);
        for (int k = 1; k <= 4; k++) { out[MAP_PRESSURES.length + k - 1] = 1 + (turbo.maxBoostRatio() - 1) * k / 4; }
        return out;
    }
    /** A diesel map's speeds (rpm): 16 from 600 to 5 % past the rev limit. */
    public double[] dieselSpeeds() {
        double top = ignition.revLimitRpm() * 1.05, low = 600;
        int n = 16;
        double[] rpms = new double[n];
        for (int i = 0; i < n; i++) { rpms[i] = low + (top - low) * i / (n - 1); }
        return rpms;
    }
    /** A diesel map's boosts: 1, or four steps up to the wastegate's boost. */
    public double[] dieselBoosts() {
        if (!turbo.fitted()) { return new double[]{1}; }
        double b = turbo.maxBoostRatio();
        return new double[]{1, 1 + (b - 1) / 3, 1 + 2 * (b - 1) / 3, b};
    }
    /** A diesel map's loads: DIESEL_LOADS, the last raised to the full load when that is richer. */
    public double[] dieselLoads() {
        double[] out = DIESEL_LOADS.clone();
        out[out.length - 1] = Math.min(1, Math.max(out[out.length - 1], fullLoadPhi()));
        return out;
    }
    /** A diesel's full-load fuel-air equivalence ratio (1 / the full-load lambda). */
    public double fullLoadPhi() { return 1 / fuelSystem.fullLoadLambda(); }

    /** Charge temperature after the turbocharger's compressor and intercooler (cooled toward the ambient air) at a pressure ratio, K. */
    public double chargeTemperature(double ambientK, double ratio) {
        return !turbo.fitted() || ratio <= 1 ? ambientK : turbo.model().chargeK(ratio, ambientK, ambientK);
    }

    /**
     * The exhaust port pressure with a turbocharger, Pa: the turbine passing the engine's exhaust (air kg/s, with a
     * fuel-air ratio, at a temperature) into the exhaust system's pressure, opening the wastegate to give the
     * compressor just its power at a pressure ratio; without the turbocharger the exhaust system's pressure.
     */
    public double turbineInletPa(double outletPa, double airKgPerS, double ratio, double exhaustK, double fuelPerAir) {
        if (!turbo.fitted()) { return outletPa; }
        Turbocharger t = turbo.model();
        double power = t.compressorPowerW(airKgPerS, ratio, AMBIENT_K);
        return t.match(power, airKgPerS * (1 + fuelPerAir), exhaustK, outletPa).turbineInletPa();
    }
    /** A first guess of the air flow at an intake pressure and temperature and a speed, kg/s. */
    public double guessAirKgPerS(double rpm, double intakePa, double intakeK) {
        return GUESS_VOLUMETRIC_EFFICIENCY * displacement() * rpm / 120 * intakePa / (287.05 * intakeK);
    }

    /**
     * What the engine does over its speeds: a spark-ignition map over manifold pressure (EngineMap) or a diesel's over
     * boost and load (DieselMap); the most boost the turbocharger holds at each speed (1 without one); a diesel's
     * full-load equivalence ratio.
     */
    public record Performance(EngineMap spark, DieselMap diesel, double[] rpms, double[] maxBoost, double fullLoadPhi) {
        public boolean isDiesel() { return diesel != null; }
        /** The most boost at a speed (linear between the map's speeds). */
        public double maxBoost(double rpm) {
            if (rpm <= rpms[0]) { return maxBoost[0]; }
            for (int i = 1; i < rpms.length; i++) {
                if (rpm <= rpms[i]) { return maxBoost[i - 1] + (maxBoost[i] - maxBoost[i - 1]) * (rpm - rpms[i - 1]) / (rpms[i] - rpms[i - 1]); }
            }
            return maxBoost[maxBoost.length - 1];
        }
        /** A quantity at full load at a speed: wide-open throttle at the boost the turbocharger holds, or a diesel's full-load fuel. */
        public double fullLoad(double rpm, ToDoubleFunction<EngineMap.Point> q) {
            double b = maxBoost(rpm);
            return diesel != null ? diesel.value(rpm, b, fullLoadPhi, q) : spark.value(rpm, b, q);
        }
    }

    /**
     * Builds the performance (seconds of work; the speeds in parallel). Spark ignition: EngineMap at the fuel
     * system's target lambda, leaner where the injectors run out (at most lambda 2.5), cam phases from the valve
     * train's phasers, spark from the ignition. Diesel: DieselMap with injection up to the ignition's advance. Both at
     * standard ambient with 10 K of intake heating, the exhaust's back pressure behind the turbocharger's turbine.
     */
    public Performance buildPerformance() { return diesel() ? buildDiesel() : buildSpark(); }

    private Performance buildSpark() {
        double fuelPerAirFull = 1 / (fuelSystem.fullLoadLambda() * fuel().stoichiometricAirFuel());
        EngineMap.Engine engine = new EngineMap.Engine() {
            @Override public EngineCycle.Spec spec(double lambda, double intakePhaseDeg, double exhaustPhaseDeg) { return cylinderSpec(lambda, intakePhaseDeg, exhaustPhaseDeg); }
            @Override public double exhaustPressure(double ambientPa, double manifoldPa, double rpm) {
                double outlet = exhaust.backPressure(ambientPa, manifoldPa, 2 * stroke() * rpm / 60);
                if (!turbo.fitted()) { return outlet; }
                double ratio = manifoldPa / ambientPa, chargeK = intakeTemperature(AMBIENT_K + INTAKE_HEATING_K, ratio);
                return turbineInletPa(outlet, guessAirKgPerS(rpm, manifoldPa, chargeK), ratio, SI_EXHAUST_K, 1 / (targetLambda(ratio) * fuel().stoichiometricAirFuel()));
            }
            @Override public double fuelLimitKg(double rpm, double manifoldPa) { return injectorLimit(rpm, manifoldPa); }
            @Override public double lambda(double pressureRatio) { return targetLambda(pressureRatio); }
            @Override public double intakeTemperature(double calibrationK, double pressureRatio) {
                return pressureRatio <= 1 ? calibrationK : chargeTemperature(AMBIENT_K, pressureRatio) + INTAKE_HEATING_K;
            }
        };
        EngineMap.Calibration calibration = new EngineMap.Calibration(mapSpeeds(), mapPressures(), new Dyno.SparkControl(ignition.knockControl(), ignition.maxAdvanceDeg(), 1),
                AMBIENT_PA, AMBIENT_K + INTAKE_HEATING_K, valvetrain.intakePhaseRangeDeg(), valvetrain.exhaustPhaseRangeDeg(), 2.5);
        EngineMap m = EngineMap.build(engine, calibration);
        double[] boost = new double[m.rpms().length];
        int n = block.cylinders();
        for (int i = 0; i < boost.length; i++) {
            double rpm = m.rpms()[i];
            boost[i] = !turbo.fitted() ? 1 : turbo.model().maxBoost(r -> n * rpm / 120 * m.value(rpm, r, EngineMap.Point::airKg),
                    r -> m.value(rpm, r, EngineMap.Point::exhaustTemperatureK), fuelPerAirFull, AMBIENT_PA, AMBIENT_K);
        }
        return new Performance(m, null, m.rpms(), boost, 1);
    }

    private Performance buildDiesel() {
        double phi = fullLoadPhi(), fuelPerAir = phi / fuel().stoichiometricAirFuel();
        DieselMap.Engine engine = new DieselMap.Engine() {
            @Override public EngineCycle.Spec spec(double lambda) { return cylinderSpec(lambda, 0, 0); }
            @Override public double intakeTemperature(double ambientK, double boostRatio) { return chargeTemperature(ambientK, boostRatio) + INTAKE_HEATING_K; }
            @Override public double exhaustPressure(double ambientPa, double intakePa, double rpm, double airKgPerS, double exhaustK) {
                double outlet = exhaust.backPressure(ambientPa, intakePa, 2 * stroke() * rpm / 60);
                if (!turbo.fitted()) { return outlet; }
                double ratio = intakePa / ambientPa;
                if (!(airKgPerS > 0)) { airKgPerS = guessAirKgPerS(rpm, intakePa, intakeTemperature(AMBIENT_K, ratio)); exhaustK = DIESEL_EXHAUST_K; }
                return turbineInletPa(outlet, airKgPerS, ratio, exhaustK, fuelPerAir);
            }
            @Override public double fuelLimitKg(double rpm) { return injectorLimit(rpm, AMBIENT_PA); }
        };
        int n = block.cylinders();
        DieselMap m = DieselMap.build(engine, new DieselMap.Calibration(dieselSpeeds(), dieselBoosts(), dieselLoads(), ignition.maxAdvanceDeg(), AMBIENT_PA, AMBIENT_K, n));
        double[] boost = new double[m.rpms().length];
        for (int i = 0; i < boost.length; i++) {
            double rpm = m.rpms()[i];
            boost[i] = !turbo.fitted() ? 1 : turbo.model().maxBoost(r -> n * rpm / 120 * m.value(rpm, r, phi, EngineMap.Point::airKg),
                    r -> m.value(rpm, r, phi, EngineMap.Point::exhaustTemperatureK), fuelPerAir, AMBIENT_PA, AMBIENT_K);
        }
        return new Performance(null, m, m.rpms(), boost, phi);
    }

    /** A spark-ignition engine's map, built afresh (null for a diesel). */
    public EngineMap buildMap() { return diesel() ? null : buildSpark().spark(); }

    /** The performance, built once per design (by its JSON) and shared by every vehicle and bench that runs it: seconds of work the first time. */
    public Performance performance() {
        String key = Json.write(toJson());
        Performance m = PERFORMANCE.get(key);
        if (m == null) { m = buildPerformance(); PERFORMANCE.put(key, m); }
        return m;
    }
    /** A spark-ignition engine's map (performance().spark(); null for a diesel). */
    public EngineMap map() { return performance().spark(); }

    // ---------------------------------------------------------------- compiled part

    /** Indices of the internal states. */
    public static final int STATE_MANIFOLD = 0, STATE_TEMPERATURE = 1, STATE_RUNNING = 2, STATE_BOOST = 3;

    /**
     * Shown values (0.11): the load (a spark engine's manifold pressure over ambient, a diesel's fuel over its full
     * load's; 0 when not firing, to 0.05) and whether it runs (0 or 1); 0.19 adds the boost (the compressor's
     * pressure over ambient, 1 without a turbocharger, to 0.05), for the turbo's whistle and blow-off in its sound.
     */
    public static final int SHOWN_LOAD = 0, SHOWN_RUNNING = 1, SHOWN_BOOST = 2;

    /**
     * 0.25: the engine's conditions (Part.conditions; rules in damage.EngineWear): "engine" wears with running (mean piston
     * speed squared and load against the hours to overhaul, far faster over the rev limit), loses compression and gains
     * friction, and has seized at 1; "ancillaries" (cooling, ignition or injection, electrics) are damaged from outside
     * only, cost power from half used and at 1 the engine neither runs nor starts.
     */
    public static final int CONDITION_ENGINE = 0, CONDITION_ANCILLARIES = 1;
    private static final List<String> CONDITIONS = List.of(dev.mcmod.hdfv.damage.Conditions.ENGINE, dev.mcmod.hdfv.damage.Conditions.ANCILLARIES);

    private static final class Compiled implements Part {
        private final PistonEngineDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        /** 0.25: seconds of wear a second of running is (DamagePace as it stood when this was compiled) and the life to overhaul, s. */
        private final double wearPace, lifeS;
        private final double inertia, displacement, plenumVolume, closedAngle = Math.toRadians(5);
        private final Friction.Engine friction;
        private final EngineOil oil;
        private final double heatCapacity;
        private final Set<String> fluids;
        private volatile Performance performance;

        Compiled(PistonEngineDesign design, List<DesignIssue> issues) {
            this.design = design; this.issues = issues;
            double m = design.totalMassKg();
            EngineBlockDesign b = design.block();
            double length = b.length() + 0.2;
            double width = b.layout() == EngineBlockDesign.Layout.INLINE ? b.boreM() + 0.25
                    : 2 * (b.deckHeightM() + design.head().castingHeight()) * Math.sin(Math.toRadians(b.effectiveBankAngle()) / 2) + b.boreM() + 0.1;
            double height = b.deckHeightM() + design.head().castingHeight() + 0.15;
            mass = MassProperties.of(m, Vec3d.ZERO, InertiaTensor.box(m, new Vec3d(length, height, width)));
            inertia = design.crankInertia();
            displacement = design.displacement();
            plenumVolume = design.intake().plenumVolumeM3() + b.cylinders() * Math.PI * Math.pow(design.intake().runnerDiameterM(), 2) / 4 * design.intake().runnerLengthM();
            friction = design.frictionModel();
            oil = design.lubrication().oil();
            double metal = 0;
            metal += b.massKg() * b.material().specificHeatJPerKgK() + design.head().massFor(b) * design.head().material().specificHeatJPerKgK();
            metal += (m - b.massKg() - design.head().massFor(b)) * 500;   // the rest, mostly steel and aluminium, at a middling 500 J/(kg K)
            heatCapacity = metal + design.cooling().coolantVolumeM3() * CoolingDesign.COOLANT_DENSITY * CoolingDesign.COOLANT_SPECIFIC_HEAT
                    + design.lubrication().oilVolumeM3() * LubricationDesign.OIL_DENSITY * LubricationDesign.OIL_SPECIFIC_HEAT;
            fluids = design.acceptedFluids();
            wearPace = dev.mcmod.hdfv.damage.DamagePace.wear();
            lifeS = 3600 * (design.diesel() ? dev.mcmod.hdfv.damage.EngineWear.DIESEL_OVERHAUL_H : dev.mcmod.hdfv.damage.EngineWear.SPARK_OVERHAUL_H);
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return mass; }
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return issues; }
        @Override public List<Rotor> rotors() { return List.of(new Rotor(Vec3d.X, inertia, true)); }
        @Override public List<String> inputs() { return List.of("throttle", "ignition", "starter"); }
        @Override public int internalStates() { return 4; }
        @Override public double[] initialInternals() { return new double[]{1, 0, 0, 1}; }
        /** 0.11: the load and whether it runs (SHOWN_LOAD, SHOWN_RUNNING), for its exhaust's smoke and its manifolds' glow; 0.19 the boost. */
        @Override public int shownValues() { return 3; }
        @Override public List<String> conditions() { return CONDITIONS; }

        @Override public void warmUp() { performance(); }
        Performance performance() {
            Performance m = performance;
            if (m == null) {
                synchronized (this) {
                    if (performance == null) { performance = design.performance(); }
                    m = performance;
                }
            }
            return m;
        }

        @Override public void loads(PartStep step) {
            Performance perf = performance();
            double dt = step.dt();
            FluidState air = step.environment().point(step.point(Vec3d.ZERO)).air();
            double airT = air.speedOfSoundMps() * air.speedOfSoundMps() / (1.4 * 287.05), ambient = air.densityKgPerM3() * 287.05 * airT;
            double densityScale = ambient / AMBIENT_PA * AMBIENT_K / airT;
            double omega = step.rotorSpeed(0), rpm = Math.max(0, omega * 30 / Math.PI);
            double throttle = Math.clamp(step.input("throttle", 0), 0, 1), ignitionOn = step.input("ignition", 1);
            double topBoost = design.turbo().fitted() ? design.turbo().maxBoostRatio() : 1;
            double ratio = Math.clamp(step.internal(STATE_MANIFOLD), 0.02, topBoost + 0.2);
            double boost = Math.clamp(step.internal(STATE_BOOST), 1, topBoost);
            double temperature = step.internal(STATE_TEMPERATURE);
            if (!(temperature > 0)) { temperature = airT; }
            boolean running = step.internal(STATE_RUNNING) > 0.5;
            // 0.25: condition. A new engine, and one whose wear does not show yet, takes none of the branches below and steps as before.
            double usedEngine = step.used(CONDITION_ENGINE), usedAncillaries = step.used(CONDITION_ANCILLARIES);
            boolean tired = usedEngine >= dev.mcmod.hdfv.damage.EngineWear.SHOWS_FROM;
            boolean worn = tired || usedAncillaries > dev.mcmod.hdfv.damage.EngineWear.ANCILLARIES_KNEE, seized = usedEngine >= 1;
            double workShare = 1, frictionRise = 1;
            if (worn) {
                if (tired) { workShare = dev.mcmod.hdfv.damage.EngineWear.compression(usedEngine); frictionRise = dev.mcmod.hdfv.damage.EngineWear.friction(usedEngine); }
                workShare *= dev.mcmod.hdfv.damage.EngineWear.ancillaries(usedAncillaries);
                if (seized || usedAncillaries >= 1) { ignitionOn = 0; }   // neither runs nor starts
            }
            final double work = workShare, rub = frictionRise;
            IgnitionDesign ign = design.ignition();
            // run state: the starter cranks until the engine fires; below 300 rpm it stalls; it stops when the tanks run dry
            boolean fed = step.fuelSupply() >= 0.5;
            if (ignitionOn < 0.5 || !fed) { running = false; }
            else if (!running && rpm >= 400) { running = true; }
            else if (running && rpm < 300) { running = false; }
            double starter = step.input("starter", -1);
            boolean cranking = ignitionOn >= 0.5 && !running && (starter < 0 || starter >= 0.5);
            boolean fuelCut = !running || rpm > ign.revLimitRpm() || (throttle < 0.02 && rpm > ign.idleRpm() + 600);
            int n = design.block().cylinders();
            double cycles = n * rpm / 120;
            double fullArea = design.intake().throttleArea();
            double rho = air.densityKgPerM3(), rAir = 287.05;
            double scaleTorque = EngineRuntime.torqueFromMep(1, displacement);
            double viscosity = oil.frictionScaling(temperature - 273.15);
            final double manifold;
            final java.util.function.DoubleUnaryOperator imepAt;
            double fuelPerCycle, wallPerCycle, target, load;
            if (perf.isDiesel()) {
                // no throttle: the intake is at the compressor's pressure; the pedal sets the fuel up to the full load, an idle governor below
                DieselMap d = perf.diesel();
                double phiFull = perf.fullLoadPhi();
                double flow = cycles * d.value(rpm, Math.max(1, ratio), DIESEL_IDLE_PHI, EngineMap.Point::airKg) * densityScale * Math.min(1, ratio);
                double filterDrop = EngineRuntime.filterDrop(design.intake().filterLoss(), flow, rho, fullArea);
                ratio = Math.max(0.5, (boost * ambient - filterDrop) / ambient);
                manifold = ratio;
                double governor = Math.clamp(DIESEL_IDLE_PHI + 0.8 * (ign.idleRpm() - rpm) / ign.idleRpm(), 0, phiFull);
                double phi = fuelCut ? 0 : Math.max(throttle * phiFull, governor);
                double scale = densityScale * Math.min(1, manifold);
                imepAt = w -> {
                    double r = Math.max(0, w * 30 / Math.PI);
                    return phi > 0 ? dieselImep(d, r, manifold, phi) * scale : -d.value(r, Math.max(1, manifold), DIESEL_IDLE_PHI, EngineMap.Point::pumpingMepPa);
                };
                double fed0 = phi > 0 ? phi : cranking ? DIESEL_IDLE_PHI : 0;   // the injectors spray while cranking
                fuelPerCycle = fed0 > 0 ? dieselPerFuel(d, rpm, manifold, fed0, EngineMap.Point::fuelKg) * scale : 0;
                wallPerCycle = phi > 0 ? dieselPerFuel(d, rpm, manifold, phi, EngineMap.Point::wallHeatJ) * scale : 0;
                target = running ? 1 + (perf.maxBoost(rpm) - 1) * Math.clamp(phi / phiFull, 0, 1) : 1;
                load = phi / phiFull;
            } else {
                EngineMap m = perf.spark();
                // idle control: below the idle speed the ECU opens an air bypass (proportional, a control rule)
                double bypass = running ? Math.clamp(0.04 + 0.4 * (ign.idleRpm() - rpm) / ign.idleRpm(), 0, 0.12) : 0.04;
                double opening = Math.max(throttle, bypass);
                double angle = closedAngle + opening * (Math.PI / 2 - closedAngle);
                double throttleArea = 0.8 * design.intake().throttleBodies() * ValveFlow.throttleArea(design.intake().throttleDiameterM(), closedAngle, angle);   // C_D 0.8
                // manifold pressure: implicit balance of throttle inflow (from the compressor) and engine draw over the step
                double previousFlow = cycles * m.value(rpm, ratio, EngineMap.Point::airKg) * densityScale;
                double filterDrop = EngineRuntime.filterDrop(design.intake().filterLoss(), previousFlow, rho, fullArea);
                double upstream = Math.max(0.5 * ambient, boost * ambient - filterDrop);
                double capacity = plenumVolume / (rAir * airT);
                final double finalRpm = rpm;
                ratio = EngineRuntime.manifoldRatio(ratio, dt, capacity, ambient, upstream, throttleArea, airT, rAir, 1.4,
                        x -> cycles * m.value(finalRpm, x, EngineMap.Point::airKg) * densityScale);
                manifold = ratio;
                boolean cut = fuelCut;
                imepAt = w -> {
                    double r = Math.max(0, w * 30 / Math.PI);
                    return cut ? -m.value(r, manifold, EngineMap.Point::pumpingMepPa) : m.value(r, manifold, EngineMap.Point::netImepPa) * densityScale;
                };
                fuelPerCycle = !fuelCut || cranking ? m.value(rpm, ratio, EngineMap.Point::fuelKg) * densityScale : 0;
                wallPerCycle = fuelCut ? 0 : m.value(rpm, ratio, EngineMap.Point::wallHeatJ) * densityScale;
                // the ECU asks the wastegate for boost with the pedal past 30 %; the turbine gives at most what it holds at the speed
                target = running ? 1 + (perf.maxBoost(rpm) - 1) * Math.clamp((throttle - 0.3) / 0.6, 0, 1) : 1;
                load = manifold;
            }
            // the turbocharger follows its target with the rotor's lag
            if (design.turbo().fitted()) { boost += (target - boost) * (1 - Math.exp(-dt / design.turbo().lagSeconds(rpm))); }
            // torque
            boolean motoring = fuelCut;
            java.util.function.DoubleUnaryOperator torqueAt = w -> {
                double r = Math.max(0, w * 30 / Math.PI);
                double imep = imepAt.applyAsDouble(w);
                double fmep = Friction.fmep(friction, Math.max(r, 50), manifold * ambient, ambient, viscosity).totalPa();
                if (worn) {
                    if (imep > 0) { imep *= work; }
                    fmep *= rub;
                }
                double spin = Math.tanh(w / 2);   // friction and pumping oppose the rotation; smooth through standstill
                double t = motoring ? (imep - fmep) * scaleTorque * spin : (imep - fmep * spin) * scaleTorque;
                if (cranking) { t += ign.starterTorqueNm() * Math.clamp((500 - r) / 200, 0, 1); }
                return t;
            };
            double torque = torqueAt.applyAsDouble(omega);
            double dw = Math.max(1, 1e-3 * Math.abs(omega));
            double slope = (torqueAt.applyAsDouble(omega + dw) - torqueAt.applyAsDouble(omega - dw)) / (2 * dw);
            if (seized) {
                // the crank is held: it stops within about EngineWear.SEIZE_S whatever turns it
                slope = -inertia / dev.mcmod.hdfv.damage.EngineWear.SEIZE_S;
                torque = slope * omega;
            }
            step.rotorTorque(0, torque, slope, false);
            // 0.25: wear while it turns under its own power
            if (running && wearPace > 0) {
                step.wear(CONDITION_ENGINE, dev.mcmod.hdfv.damage.EngineWear.piston(dt * wearPace, lifeS, 2 * design.stroke() * rpm / 60, load, rpm, ign.revLimitRpm()));
            }
            // fuel and heat
            double fuelFlow = cycles * fuelPerCycle;
            step.burn(fluids, fuelFlow);
            double wallHeat = cycles * wallPerCycle;
            double frictionPower = Math.abs(Friction.fmep(friction, Math.max(rpm, 50), ratio * ambient, ambient, viscosity).totalPa() * scaleTorque * omega);
            double airspeed = step.velocity(Vec3d.ZERO).subtract(air.velocity()).length();
            double conductivity = 0.0263 * Math.pow(airT / 300, 0.8);   // air, W/(m K), approximate temperature dependence
            double rejected = design.cooling().rejection(temperature, airT, air.densityKgPerM3(), air.dynamicViscosityPaS(), conductivity, airspeed);
            double nextT = temperature + dt * (wallHeat + frictionPower - rejected) / heatCapacity;
            step.setInternal(STATE_MANIFOLD, ratio);
            step.setInternal(STATE_TEMPERATURE, nextT);
            step.setInternal(STATE_RUNNING, running ? 1 : 0);
            step.setInternal(STATE_BOOST, boost);
            step.show(SHOWN_LOAD, running && !fuelCut ? Exhaust.round(Math.clamp(load, 0, 3), 0.05) : 0);
            step.show(SHOWN_RUNNING, running ? 1 : 0);
            step.show(SHOWN_BOOST, Exhaust.round(boost, 0.05));
            step.diagnostic("rpm", rpm);
            step.diagnostic("throttle", throttle);
            step.diagnostic("manifoldKpa", ratio * ambient / 1000);
            if (design.turbo().fitted()) { step.diagnostic("boostKpa", (boost - 1) * ambient / 1000); }
            step.diagnostic("torqueNm", torque);
            step.diagnostic("powerW", torque * omega);
            step.diagnostic("fuelKgPerS", running ? fuelFlow : 0);
            step.diagnostic("engineC", nextT - 273.15);
            step.diagnostic("running", running ? 1 : 0);
            step.diagnostic("knocking", !fuelCut && !perf.isDiesel() && perf.spark().value(rpm, ratio, p -> p.knocking() ? 1 : 0) > 0.5 ? 1 : 0);
        }
    }

    /** A diesel's net IMEP at a speed, boost and load; below the map's lightest load, between motoring and it. */
    static double dieselImep(DieselMap d, double rpm, double boost, double phi) {
        double lo = d.loads()[0], b = Math.max(1, boost);
        if (phi >= lo) { return d.value(rpm, b, phi, EngineMap.Point::netImepPa); }
        double f = phi / lo;
        return f * d.value(rpm, b, lo, EngineMap.Point::netImepPa) - (1 - f) * d.value(rpm, b, lo, EngineMap.Point::pumpingMepPa);
    }
    /** A quantity that goes with a diesel's fuel (fuel, heat) at a speed, boost and load; below the lightest load, in proportion. */
    static double dieselPerFuel(DieselMap d, double rpm, double boost, double phi, ToDoubleFunction<EngineMap.Point> q) {
        double lo = d.loads()[0], b = Math.max(1, boost);
        return phi >= lo ? d.value(rpm, b, phi, q) : phi / lo * d.value(rpm, b, lo, q);
    }

    /** Built performance by design JSON (a design's is the same for every vehicle that carries it). */
    private static final Map<String, Performance> PERFORMANCE = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Performance> eldest) { return size() > 32; }
    });
}
