package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;
import dev.mcmod.aerocore.api.vehicle.PartTypes;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.design.Bench;
import dev.mcmod.hdfv.fluid.FuelGrades;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Diesel engines and turbochargers (0.8): checks, the samples' full-load curves against real engines, running on and running out of fuel. */
class DieselEngineTest {
    static { HdfvParts.register(); }

    private static final double DT = 0.005;
    private static final FluidState AIR = Atmosphere.atAltitude(0, Vec3d.ZERO).fluid(), WATER = FluidState.FRESH_WATER_20C;
    private static final VehicleEnvironment ENV = VehicleEnvironment.calm(-100, WATER, AIR);

    private static boolean invalid(PistonEngineDesign e, String parameter) {
        return e.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().startsWith(parameter));
    }
    private static void valid(PistonEngineDesign e) {
        assertTrue(e.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), e.name() + ": " + e.check());
    }

    @Test void theSamplesAreValidAndRoundTrip() {
        for (PistonEngineDesign e : List.of(SampleEngines.turboDiesel(), SampleEngines.generatorDiesel())) {
            valid(e);
            assertTrue(e.diesel());
            String json = Json.write(e.toJson());
            PartDesign back = PartTypes.read(Json.parse(json), "engine");
            assertEquals(e, back);
        }
        assertEquals(16.5, SampleEngines.turboDiesel().compressionRatio(), 1e-9);
        assertEquals(1.996e-3, SampleEngines.turboDiesel().displacement(), 1e-6);
        // an engine saved before 0.8 has no "turbo" and reads as without one
        PistonEngineDesign four = SampleEngines.inlineFour();
        Map<String, Object> old = new java.util.LinkedHashMap<>(four.toJson());
        assertNotNull(old.remove("turbo"));
        assertEquals(four, PartTypes.read(Json.parse(Json.write(old)), "engine"));
        assertFalse(four.turbo().fitted());
    }

    @Test void dieselsAndPetrolEnginesAreCheckedForWhatTheyAre() {
        PistonEngineDesign d = SampleEngines.turboDiesel();
        FuelSystemDesign f = d.fuelSystem();
        // a diesel injects directly at high pressure
        assertTrue(invalid(EngineDesignTest.with(d, "fuelSystem", new FuelSystemDesign("f", "diesel", 95, 85, FuelSystemDesign.Delivery.PORT_INJECTION, 1, 3e5,
                1.3, 1, Double.NaN)), "fuelSystem.delivery"));
        assertTrue(invalid(EngineDesignTest.with(d, "fuelSystem", new FuelSystemDesign("f", "diesel", 95, 85, f.delivery(), 1, 10e6, 1.3, 1, Double.NaN)),
                "fuelSystem.fuelPressurePa"));
        // a diesel has no spark plugs; a petrol engine needs one
        CylinderHeadDesign h = d.head();
        CylinderHeadDesign plugged = new CylinderHeadDesign("h", h.layout(), 2, 2, h.chamber(), h.chamberVolumeM3(), h.valveAngleDeg(), h.intakeThroatRatio(),
                h.exhaustThroatRatio(), h.portFlowCoefficient(), 1, 0, h.gasketThicknessM(), h.material(), Double.NaN);
        assertTrue(invalid(EngineDesignTest.with(d, "head", plugged), "head.sparkPlugs"));
        PistonEngineDesign four = SampleEngines.inlineFour();
        CylinderHeadDesign f4 = four.head();
        assertTrue(invalid(EngineDesignTest.with(four, "head", new CylinderHeadDesign("h", f4.layout(), 2, 2, f4.chamber(), f4.chamberVolumeM3(), f4.valveAngleDeg(),
                f4.intakeThroatRatio(), f4.exhaustThroatRatio(), f4.portFlowCoefficient(), 0, 0, f4.gasketThicknessM(), f4.material(), Double.NaN)), "head.sparkPlugs"));
        // the piston crown carries 180 bar
        PistonDesign p = d.piston();
        assertTrue(invalid(EngineDesignTest.with(d, "piston", new PistonDesign("p", p.diameterM(), p.compressionHeightM(), 0, 0.008, p.skirtLengthM(),
                p.pinDiameterM(), 3, 0.9, p.material(), Double.NaN)), "piston.crownThicknessM"));
        // the glow plugs and the turbocharger weigh
        assertEquals(d.turbo().massKg() > 5, true, "turbocharger and intercooler " + d.turbo().massKg());
        assertTrue(d.totalMassKg() > 130 && d.totalMassKg() < 220, "mass " + d.totalMassKg());
    }

    @Test void enginesTakeTheFuelsTheyWereMadeFor() {
        assertEquals(java.util.Set.of("diesel", "kerosene"), SampleEngines.turboDiesel().acceptedFluids());
        var petrol = SampleEngines.inlineFour().acceptedFluids();
        assertTrue(petrol.contains("gasoline") && petrol.contains("premium_gasoline"), petrol.toString());
        assertFalse(petrol.contains("diesel") || petrol.contains("naphtha") || petrol.contains("kerosene"), petrol.toString());
        assertEquals(java.util.Set.of("premium_gasoline"), FuelGrades.forSparkIgnition("gasoline", 98));
        assertEquals(java.util.Set.of("methanol"), FuelGrades.forSparkIgnition("methanol", 95));
        assertEquals(java.util.Set.of("lpg"), FuelGrades.forSparkIgnition("propane", 95));
        assertEquals(42.8e6, FuelGrades.of("diesel").fuel().lowerHeatingValueJPerKg(), 1);
    }

    @Test void theTurboDieselRunsLikeAProductionOne() {
        PistonEngineDesign e = SampleEngines.turboDiesel();
        long t0 = System.nanoTime();
        Bench.PistonCurve c = Bench.fullLoad(e);
        double seconds = (System.nanoTime() - t0) / 1e9;
        double bmep = c.peakTorque().torqueNm() * 4 * Math.PI / e.displacement();
        System.out.printf("turbo diesel (%.1f s): peak %.0f N m at %.0f rpm (BMEP %.1f bar), %.1f kW at %.0f rpm, best BSFC %.0f g/kWh%n", seconds,
                c.peakTorque().torqueNm(), c.peakTorque().rpm(), bmep / 1e5, c.peakPower().powerW() / 1000, c.peakPower().rpm(), c.bestBsfcGPerKWh());
        PistonEngineDesign.Performance perf = e.performance();
        for (double rpm : new double[]{1000, 1500, 2000, 3000, 4000}) {
            System.out.printf("  %4.0f rpm boost %.2f, peak %.0f bar, exhaust %.0f K, lambda %.2f%n", rpm, perf.maxBoost(rpm),
                    perf.fullLoad(rpm, dev.mcmod.aerocore.api.engine.EngineMap.Point::peakPressurePa) / 1e5,
                    perf.fullLoad(rpm, dev.mcmod.aerocore.api.engine.EngineMap.Point::exhaustTemperatureK), perf.fullLoad(rpm, dev.mcmod.aerocore.api.engine.EngineMap.Point::lambda));
        }
        // 2.0 l turbocharged common-rail car diesels: 280-400 N m (BMEP 18-25 bar), 80-150 kW, best BSFC 195-235 g/kWh (Heywood ch. 15; production engines)
        assertTrue(bmep > 17e5 && bmep < 25e5, "BMEP " + bmep);
        assertTrue(c.peakPower().powerW() > 80e3 && c.peakPower().powerW() < 150e3, "power " + c.peakPower().powerW());
        assertTrue(c.bestBsfcGPerKWh() > 190 && c.bestBsfcGPerKWh() < 240, "BSFC " + c.bestBsfcGPerKWh());
        assertTrue(perf.maxBoost(1000) < perf.maxBoost(2500), "the turbine holds more boost with more exhaust");
        assertEquals(e.turbo().maxBoostRatio(), perf.maxBoost(3000), 1e-6, "the wastegate's boost at speed");
        for (double rpm : new double[]{1500, 2500, 4000}) {
            assertTrue(perf.fullLoad(rpm, dev.mcmod.aerocore.api.engine.EngineMap.Point::peakPressurePa) <= PistonEngineDesign.PEAK_PRESSURE_LIMIT_PA * 1.01, "peak pressure at " + rpm);
        }
        assertTrue(c.points().stream().noneMatch(Bench.PistonPoint::knocking), "a diesel does not knock");
    }

    @Test void theGeneratorDieselRunsLikeAnIndustrialOne() {
        PistonEngineDesign e = SampleEngines.generatorDiesel();
        Bench.PistonCurve c = Bench.fullLoad(e);
        Bench.PistonPoint at1500 = c.points().stream().min(java.util.Comparator.comparingDouble(p -> Math.abs(p.rpm() - 1500))).orElseThrow();
        double bmep = at1500.torqueNm() * 4 * Math.PI / e.displacement();
        System.out.printf("generator diesel: %.0f rpm %.0f N m (BMEP %.1f bar) %.1f kW, BSFC %.0f g/kWh; best %.0f g/kWh, mass %.0f kg%n", at1500.rpm(), at1500.torqueNm(),
                bmep / 1e5, at1500.powerW() / 1000, at1500.bsfcGPerKWh(), c.bestBsfcGPerKWh(), e.totalMassKg());
        // naturally aspirated direct-injection diesels: BMEP 7-9.5 bar, BSFC 200-240 g/kWh (Heywood ch. 15)
        assertTrue(bmep > 6.5e5 && bmep < 10e5, "BMEP " + bmep);
        assertTrue(at1500.bsfcGPerKWh() > 195 && at1500.bsfcGPerKWh() < 245, "BSFC " + at1500.bsfcGPerKWh());
        assertEquals(1, e.performance().maxBoost(1500), 0, "no turbocharger");
    }

    @Test void aTurbochargerMakesAPetrolEngineStronger() {
        PistonEngineDesign na = SampleEngines.inlineFour();
        // with injectors for the boosted air (the stock ones would run it lean)
        PistonEngineDesign turbo = EngineDesignTest.with(EngineDesignTest.with(na, "turbo", new TurbochargerDesign("turbo", true, 0.048, 12, 0.76, 4e-4, 0.72, 1.8, 0.7, 1.5e-5, Double.NaN)),
                "injector", new FuelInjectorDesign("injectors", 600, 3e5, FuelInjectorDesign.Kind.PORT, Double.NaN));
        valid(turbo);
        Bench.PistonCurve a = Bench.fullLoad(na), b = Bench.fullLoad(turbo);
        System.out.printf("petrol 2.0: %.0f -> %.0f N m, %.0f -> %.0f kW; boost at 2000 %.2f, 5000 %.2f%n", a.peakTorque().torqueNm(), b.peakTorque().torqueNm(),
                a.peakPower().powerW() / 1000, b.peakPower().powerW() / 1000, turbo.performance().maxBoost(2000), turbo.performance().maxBoost(5000));
        // at compression 11 on 95 RON the boosted engine is knock-limited at speed (spark retarded), so it gains less power than torque
        assertTrue(b.peakTorque().torqueNm() > 1.3 * a.peakTorque().torqueNm(), "turbo torque");
        assertTrue(b.peakPower().powerW() > 1.15 * a.peakPower().powerW(), "turbo power");
        assertTrue(b.points().stream().anyMatch(Bench.PistonPoint::knocking) || b.peakPower().powerW() > 1.3 * a.peakPower().powerW(), "knock-limited, or not limited");
        assertTrue(turbo.totalMassKg() > na.totalMassKg() + 5);
    }

    // ---------------------------------------------------------------- running

    /** A test tank: a 10 kg block holding a fuel. */
    record TankPart(String name, String fuel, double kg) implements PartDesign {
        @Override public String type() { return "test:tank"; }
        @Override public List<DesignIssue> check() { return List.of(); }
        @Override public Map<String, Object> parameters() { return Map.of("fuel", fuel, "kg", kg); }
        @Override public Part compile() {
            PartDesign d = this;
            MassProperties m = MassProperties.of(10, Vec3d.ZERO, InertiaTensor.box(10, new Vec3d(0.5, 0.5, 0.5)));
            return new Part() {
                @Override public PartDesign design() { return d; }
                @Override public MassProperties mass() { return m; }
                @Override public List<CollisionSphere> collision() { return List.of(); }
                @Override public List<DesignIssue> issues() { return List.of(); }
                @Override public List<Tank> tanks() { return List.of(new Tank("main", 0.06, Vec3d.ZERO, fuel, kg)); }
                @Override public void loads(PartStep step) {}
            };
        }
    }

    private static Vehicle bench(PistonEngineDesign e, TankPart tank) {
        List<VehicleBlueprint.Entry> entries = new ArrayList<>(List.of(new VehicleBlueprint.Entry("engine", e, Mount.at(Vec3d.ZERO),
                List.of(Binding.of("throttle", "throttle"), Binding.of("ignition", "ignition")))));
        if (tank != null) { entries.add(new VehicleBlueprint.Entry("tank", tank, Mount.at(new Vec3d(-1, 0, 0)))); }
        Vehicle v = new VehicleBlueprint("bench", entries, List.of()).compile();
        v.warmUp();
        return v;
    }

    private static double run(Vehicle v, VehicleState st, Map<String, Double> controls, double seconds, String key, java.util.function.DoubleBinaryOperator fold, double start) {
        double acc = start;
        for (int i = 0; i < seconds / DT; i++) {
            VehicleDynamics.Result r = VehicleDynamics.step(v, st, ENV, controls, Vec3d.ZERO, List.of(), null, i * DT, DT);
            acc = fold.applyAsDouble(acc, r.diagnostics().get("engine").get(key));
        }
        return acc;
    }

    @Test void theTurboDieselStartsIdlesSpoolsUpAndRevs() {
        PistonEngineDesign e = SampleEngines.turboDiesel();
        Vehicle v = bench(e, null);
        VehicleState st = v.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        Map<String, Double> controls = new HashMap<>(Map.of("throttle", 0.0, "ignition", 1.0));
        double idle = run(v, st, controls, 4, "rpm", (a, x) -> x, 0);
        assertEquals(1, st.internal("engine", PistonEngineDesign.STATE_RUNNING), 0, "the starter got it running");
        assertEquals(e.ignition().idleRpm(), idle, 300, "it idles");
        controls.put("throttle", 1.0);
        double early = run(v, st, controls, 0.05, "boostKpa", (a, x) -> x, 0);
        double peakBoost = run(v, st, controls, 4, "boostKpa", Math::max, early);
        double peakRpm = run(v, st, controls, 1, "rpm", Math::max, 0);
        System.out.printf("turbo diesel idle %.0f rpm, boost %.0f kPa after 0.05 s, peak %.0f kPa; peak %.0f rpm%n", idle, early, peakBoost, peakRpm);
        assertTrue(early < 20, "the turbo lags: " + early);
        assertTrue(peakBoost > 50, "it spools up: " + peakBoost);
        assertTrue(peakRpm > 0.9 * e.ignition().revLimitRpm() && peakRpm < 1.05 * e.ignition().revLimitRpm(), "revs to the limiter: " + peakRpm);
        controls.put("ignition", 0.0);
        double stopped = run(v, st, controls, 6, "rpm", (a, x) -> x, 0);
        assertTrue(stopped < 50, "stopped: " + stopped);
    }

    @Test void enginesBurnTheTankDryAndStopAndRefuseTheWrongFuel() {
        // a diesel on two grams of diesel: runs, empties the tank at full throttle, stops, and the starter cannot bring it back
        Vehicle v = bench(SampleEngines.turboDiesel(), new TankPart("tank", "diesel", 0.002));
        VehicleState st = v.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        Map<String, Double> controls = new HashMap<>(Map.of("throttle", 0.0, "ignition", 1.0));
        double ran = run(v, st, controls, 3, "running", Math::max, 0);
        assertEquals(1, ran, 0, "it started on the tank");
        controls.put("throttle", 1.0);
        run(v, st, controls, 6, "rpm", (a, x) -> x, 0);
        assertEquals(0, st.tankKg(0), 1e-9, "the tank is dry");
        assertEquals(0, st.internal("engine", PistonEngineDesign.STATE_RUNNING), 0, "out of fuel it stops");
        assertEquals(0, run(v, st, controls, 2, "running", Math::max, 0), 0, "and does not restart");
        // a petrol engine will not run on diesel, and runs on petrol
        Vehicle wrong = bench(SampleEngines.inlineFour(), new TankPart("tank", "diesel", 1));
        VehicleState w = wrong.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        assertEquals(0, run(wrong, w, new HashMap<>(Map.of("throttle", 0.0, "ignition", 1.0)), 3, "running", Math::max, 0), 0, "diesel in a petrol engine");
        assertEquals(1, w.tankKg(0), 1e-12, "nothing drawn");
        Vehicle right = bench(SampleEngines.inlineFour(), new TankPart("tank", "gasoline", 1));
        VehicleState r = right.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        assertEquals(1, run(right, r, new HashMap<>(Map.of("throttle", 0.0, "ignition", 1.0)), 3, "running", Math::max, 0), 0, "petrol in a petrol engine");
        assertTrue(r.tankKg(0) < 1, "it burned some");
    }
}
