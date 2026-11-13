package dev.mcmod.hdfv.damage;

import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.GroundContact;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.damage.Wear;
import dev.mcmod.aerocore.api.ground.Ground;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.TestTrack;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.part.wheel.SampleWheels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: wear in use. The pace (what the default settings make of real lives: brake pads, tyres, a car's engine, a jet's
 * hot section); what a worn engine does (at none, half and all used); and that a vehicle whose conditions are all 0
 * steps to the last bit as one compiled with wear off, which takes the paths 0.24 took.
 */
class WearTest {
    static final FluidState AIR = dev.mcmod.aerocore.api.Atmosphere.atAltitude(0, Vec3d.ZERO).fluid(), WATER = FluidState.FRESH_WATER_20C;
    static final Ground FLAT = Ground.flat(0, GroundContact.Surface.TARMAC);
    static final double DT = TestTrack.DT;

    @BeforeAll static void register() { HdfvParts.register(); }
    @AfterEach void defaults() { DamagePace.reset(); }

    @Test void thePaceIsTheWorkClocks() {
        assertEquals(72, DamagePace.DEFAULT_COMPRESSION, 1e-9, "an hour of work in 1000 ticks of 0.05 s");
        assertEquals(72, DamagePace.wear(), 1e-9);
        assertEquals(72, DamagePace.corrosion(), 1e-9);
        assertEquals(3600.0 / 72, DamagePace.paced(3600), 1e-9);
        DamagePace.set(2000, 0.5, 2);
        assertEquals(18, DamagePace.wear(), 1e-9);
        assertEquals(72, DamagePace.corrosion(), 1e-9);
        // wear off: nothing wears, and the records AeroCore's parts take are still valid
        DamagePace.set(1000, 0, 0);
        assertEquals(0, DamagePace.wear(), 0);
        assertEquals(Double.POSITIVE_INFINITY, DamagePace.paced(1), 0);
        WheelCorner.Wearing off = Wearings.wheels(SampleWheels.carTyre(), SampleWheels.sedanFront().brake(), 2, 350);
        assertEquals(Double.POSITIVE_INFINITY, off.treadVolumeM3(), 0);
        assertEquals(0, Wear.tread(1e9, off.abradabilityM3PerJ(), 100, off.treadVolumeM3()), 0);
        assertEquals(Double.POSITIVE_INFINITY, Wearings.gearbox(dev.mcmod.hdfv.part.drive.SampleGearboxes.sixSpeedAutomatic()).gearLifeS(), 0);
        assertEquals(Double.POSITIVE_INFINITY, Wearings.motor((dev.mcmod.hdfv.part.MotorDesign) Designs.kind("electric_motor").fresh().get()).lifeS(), 0);
    }

    static double used(VehicleState st, String part, String condition) { return st.used(part, condition); }

    /**
     * The sample sedan driven as a player on a keyboard drives: flat out to 100 km/h, a kilometre's cruise, a full stop;
     * three times. What each leg and a whole lap use of the pads, tyres, engine and gears.
     */
    @Test void aSedanDrivenHardWearsAtThePaceTheGameIsMeantToHave() {
        Vehicle v = SampleBlueprints.sedan().compile();
        v.warmUp();
        VehicleState st = TestTrack.settle(v, FLAT, AIR, new Vec3d(0, 0.02, 0), 0, Map.of("brake", 1.0, "selector", 1.0), 2);
        assertEquals(0, used(st, "front_axle", AxlePart.SUSPENSION), 0, "set down gently, the suspension is untouched");
        Map<String, Double> c = new HashMap<>(Map.of("selector", 1.0));
        int laps = 3;
        double start = st.origin().x(), cruiseEngine = 0, cruiseRear = 0, cruiseM = 0, cruiseS = 36;
        double[] before = {used(st, "front_axle", AxlePart.BRAKES), used(st, "front_axle", AxlePart.TYRES), used(st, "rear_axle", AxlePart.TYRES), used(st, "engine", Conditions.ENGINE)};
        for (int lap = 0; lap < laps; lap++) {
            c.put("brake", 0.0); c.put("throttle", 1.0);
            TestTrack.drive(v, st, FLAT, AIR, (t, s) -> c, 40, s -> s.body().velocity().x() > 100 / 3.6, 0);
            assertTrue(st.body().velocity().x() > 100 / 3.6, "it reaches 100 km/h");
            double e0 = used(st, "engine", Conditions.ENGINE), r0 = used(st, "rear_axle", AxlePart.TYRES), x0 = st.origin().x();
            TestTrack.drive(v, st, FLAT, AIR, (t, s) -> { c.put("throttle", Math.clamp(0.3 + 0.5 * (100 / 3.6 - s.body().velocity().x()), 0, 1)); return c; }, cruiseS, s -> false, 0);
            cruiseEngine += used(st, "engine", Conditions.ENGINE) - e0; cruiseRear += used(st, "rear_axle", AxlePart.TYRES) - r0; cruiseM += st.origin().x() - x0;
            c.put("throttle", 0.0); c.put("brake", 1.0);
            TestTrack.drive(v, st, FLAT, AIR, (t, s) -> c, 20, s -> s.body().velocity().length() < 0.2, 0);
        }
        double km = (st.origin().x() - start) / 1000 / laps;
        double pads = (used(st, "front_axle", AxlePart.BRAKES) - before[0]) / laps, front = (used(st, "front_axle", AxlePart.TYRES) - before[1]) / laps,
                rear = (used(st, "rear_axle", AxlePart.TYRES) - before[2]) / laps, engine = (used(st, "engine", Conditions.ENGINE) - before[3]) / laps;
        double cruiseHours = laps * cruiseS / cruiseEngine / 3600, cruiseTyreKm = cruiseM / 1000 / cruiseRear;
        System.out.printf("a lap of %.2f km (flat out to 100 km/h, a cruise, a full stop): front pads last %.0f stops, front tyres %.0f km, rear tyres %.0f km, the engine %.0f laps%n",
                km, 1 / pads, km / front, km / rear, 1 / engine);
        System.out.printf("cruising at 100 km/h: the engine lasts %.0f real hours, the driven tyres %.0f km; gears used a lap %.2e, shift elements %.2e%n", cruiseHours, cruiseTyreKm,
                used(st, "gearbox", "gears") / laps, used(st, "gearbox", "clutch") / laps);
        // a set of front pads: 150 to 400 hard stops from 100 km/h
        assertTrue(1 / pads > 150 && 1 / pads < 420, "front pads last " + 1 / pads + " stops");
        // tyres: a few hundred km of such driving, thousands of steady cruising
        assertTrue(km / front > 150 && km / front < 800, "front tyres last " + km / front + " km");
        assertTrue(km / rear > 150 && km / rear < 800, "rear tyres last " + km / rear + " km");
        assertTrue(cruiseTyreKm > 1500, "cruising, the driven tyres last " + cruiseTyreKm + " km");
        // a car engine: tens of real hours at a cruise
        assertTrue(cruiseHours > 30 && cruiseHours < 150, "the engine cruises for " + cruiseHours + " h");
        assertTrue(1 / engine > 500, "and survives many laps: " + 1 / engine);
        // nothing else wore that should not: the body, the suspension on a flat road
        assertEquals(0, used(st, "body", Conditions.STRUCTURE), 0);
        assertEquals(0, used(st, "rear_axle", AxlePart.SUSPENSION), 0);
        assertEquals(0, used(st, "engine", Conditions.ANCILLARIES), 0);
    }

    /** A burnout: the driven pair of tyres sliding at 15 m/s under their load. As long as AeroCore's formula lets them last (see Wearings). */
    @Test void aBurnoutTakesAPairOfTyresInUnderAMinute() {
        WheelCorner.Wearing w = Wearings.wheels(SampleWheels.carTyre(), SampleWheels.sedanRear().brake(), 2, 330);
        double loadN = 2 * 330 * 9.80665, slide = 15, powerW = 1.0 * loadN * slide;
        double perSecond = Wear.tread(powerW, w.abradabilityM3PerJ(), Wear.treadSeverity(slide, w.slideReferenceMps()), w.treadVolumeM3());
        double realTyre = 2 * Wearings.treadVolumeM3(SampleWheels.carTyre());
        double real = Wear.tread(powerW, Wear.TREAD_M3_PER_J, Wear.treadSeverity(slide, WheelCorner.Wearing.SLIDE_REFERENCE_MPS), realTyre);
        System.out.printf("a burnout at %.0f kW: the pair lasts %.0f s (a real one by AeroCore's typical numbers %.0f s)%n", powerW / 1000, 1 / perSecond, 1 / real);
        assertTrue(1 / perSecond > 20 && 1 / perSecond < 90, "a burnout lasts " + 1 / perSecond + " s");
        assertTrue(1 / perSecond > 0.5 / real, "not much faster than a real one");
        // and gentle slip is paced: a sixth of 72
        assertEquals(12, realTyre / w.treadVolumeM3(), 1e-9);
    }

    @Test void aJetsHotSectionLastsHoursAtItsTakeOffTemperature() {
        double pace = DamagePace.wear(), rated = 1250;
        double atLimit = 1 / EngineWear.hotSection(3600 * pace, rated, rated, false);
        double cruise = 1 / EngineWear.hotSection(3600 * pace, rated, rated - 100, false);
        System.out.printf("a hot section at its limit lasts %.1f real hours, 100 K under it %.0f h, 50 K over it %.2f h%n", atLimit, cruise,
                1 / EngineWear.hotSection(3600 * pace, rated, rated + 50, false));
        assertEquals(EngineWear.HOT_SECTION_H / 72, atLimit, 1e-6);
        assertTrue(atLimit > 3 && atLimit < 10, "several real hours: " + atLimit);
        assertTrue(cruise > 20 * atLimit, "far longer below it");
        assertEquals(2, EngineWear.hotSection(1, rated, rated, true) / EngineWear.hotSection(1, rated, rated, false), 1e-9, "reheat costs twice");
        assertTrue(EngineWear.hotSection(1, rated, rated + 50, false) > 4 * EngineWear.hotSection(1, rated, rated, false), "over-temperature costs life steeply");
        assertEquals(0, EngineWear.hotSection(1, rated, 288, false), 0, "cold, nothing");
        assertTrue(EngineWear.hotSection(1e9, rated, 1e9, false) <= 1, "bounded");
        // a piston engine over its limiter
        assertTrue(EngineWear.piston(1, 1e6, 20, 1, 7700, 7000) > 3 * EngineWear.piston(1, 1e6, 20, 1, 7000, 7000));
        assertEquals(0, EngineWear.piston(1, 1e6, 0, 1, 0, 7000), 0, "standing still, nothing");
        assertTrue(EngineWear.piston(1e12, 1, 1e3, 1e3, 1e9, 1) <= 1, "bounded");
    }

    // ----- what worn engines do -----

    /** An engine alone, held at a speed: one step's diagnostics with its conditions set. */
    static Map<String, Double> pistonAt(Vehicle v, double rpm, double throttle, double engineUsed, double ancillariesUsed) {
        VehicleState st = v.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        VehicleEnvironment env = VehicleEnvironment.calm(-100, WATER, AIR);
        Map<String, Double> controls = Map.of("throttle", throttle, "ignition", 1.0);
        VehicleDynamics.Result r = null;
        for (int i = 0; i < 200; i++) {
            st.setTrainSpeed(0, rpm * Math.PI / 30);
            st.setUsed("engine", PistonEngineDesign.CONDITION_ENGINE, engineUsed);
            st.setUsed("engine", PistonEngineDesign.CONDITION_ANCILLARIES, ancillariesUsed);
            r = VehicleDynamics.step(v, st, env, controls, Vec3d.ZERO, List.of(), null, i * DT, DT);
        }
        return r.diagnostics().get("engine");
    }

    @Test void aWornPistonEngineLosesTorqueAndAFailedOneDoesNotRun() {
        Vehicle v = new VehicleBlueprint("bench", List.of(new VehicleBlueprint.Entry("engine", SampleEngines.inlineFour(), Mount.at(Vec3d.ZERO),
                List.of(Binding.of("throttle", "throttle"), Binding.of("ignition", "ignition")))), List.of()).compile();
        v.warmUp();
        assertEquals(List.of(Conditions.ENGINE, Conditions.ANCILLARIES), v.installed().get(0).part().conditions());
        double fresh = pistonAt(v, 4000, 1, 0, 0).get("torqueNm");
        double half = pistonAt(v, 4000, 1, 0.5, 0).get("torqueNm"), nearly = pistonAt(v, 4000, 1, 0.99, 0).get("torqueNm");
        System.out.printf("inline four at 4000 rpm, full throttle: new %.1f N m; engine half used %.1f; nearly all %.1f; ancillaries at 0.5 %.1f, 0.75 %.1f%n", fresh, half, nearly,
                pistonAt(v, 4000, 1, 0, 0.5).get("torqueNm"), pistonAt(v, 4000, 1, 0, 0.75).get("torqueNm"));
        assertTrue(fresh > 100, "a healthy engine pulls: " + fresh);
        assertTrue(half < 0.93 * fresh && half > 0.8 * fresh, "half used: " + half + " of " + fresh);
        assertTrue(nearly < 0.7 * fresh && nearly > 0.45 * fresh, "nearly used up: a quarter of the work gone and a quarter more friction, about 40 % of the torque: " + nearly + " of " + fresh);
        // ancillaries: nothing to half, then less and less
        assertEquals(fresh, pistonAt(v, 4000, 1, 0, 0.5).get("torqueNm"), 0, "ancillaries half used change nothing");
        double limping = pistonAt(v, 4000, 1, 0, 0.75).get("torqueNm");
        assertTrue(limping < 0.75 * fresh && limping > 0.4 * fresh, "limping: " + limping);
        // failed: seized, or dead
        Map<String, Double> seized = pistonAt(v, 4000, 1, 1, 0), dead = pistonAt(v, 4000, 1, 0, 1);
        assertEquals(0, seized.get("running"), 0);
        assertTrue(seized.get("torqueNm") < -5 * fresh, "a seized crank is held: " + seized.get("torqueNm"));
        assertEquals(0, dead.get("running"), 0);
        assertTrue(dead.get("torqueNm") < 0, "a dead engine only drags: " + dead.get("torqueNm"));
        // neither starts: the starter does not turn them
        for (double[] u : new double[][]{{1, 0}, {0, 1}}) {
            VehicleState st = v.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
            st.setUsed("engine", 0, u[0]); st.setUsed("engine", 1, u[1]);
            for (int i = 0; i < 3 / DT; i++) { VehicleDynamics.step(v, st, VehicleEnvironment.calm(-100, WATER, AIR), Map.of("throttle", 0.2, "ignition", 1.0), Vec3d.ZERO, List.of(), null, i * DT, DT); }
            assertEquals(0, st.internal("engine", PistonEngineDesign.STATE_RUNNING), 0, "does not start");
            assertTrue(Math.abs(st.rotorSpeed(0)) < 1, "is not even cranked: " + st.rotorSpeed(0));
        }
        // a healthy one does, and running wears it
        VehicleState st = v.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        for (int i = 0; i < 3 / DT; i++) { VehicleDynamics.step(v, st, VehicleEnvironment.calm(-100, WATER, AIR), Map.of("throttle", 0.2, "ignition", 1.0), Vec3d.ZERO, List.of(), null, i * DT, DT); }
        assertEquals(1, st.internal("engine", PistonEngineDesign.STATE_RUNNING), 0);
        assertTrue(st.used("engine", Conditions.ENGINE) > 0 && st.used("engine", Conditions.ENGINE) < 1e-3, "worn a little: " + st.used("engine", Conditions.ENGINE));
        assertEquals(0, st.used("engine", Conditions.ANCILLARIES), 0, "the ancillaries do not wear by themselves");
    }

    @Test void aWornTurbojetLosesThrustAndAFailedOneGoesOut() {
        JetEngineDesign e = SampleEngines.modelTurbojet();
        Vehicle v = new VehicleBlueprint("bench", List.of(new VehicleBlueprint.Entry("jet", e, Mount.at(Vec3d.ZERO),
                List.of(Binding.of("throttle", "throttle"), Binding.of("ignition", "ignition")))), List.of()).compile();
        v.warmUp();
        assertEquals(List.of(Conditions.HOT_SECTION, Conditions.ANCILLARIES), v.installed().get(0).part().conditions());
        VehicleState st = v.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        VehicleEnvironment env = VehicleEnvironment.calm(-100, WATER, AIR);
        RigidBodyState held = new RigidBodyState(Vec3d.ZERO, Vec3d.ZERO, Orientation.IDENTITY, Vec3d.ZERO);
        Map<String, Double> controls = Map.of("throttle", 1.0, "ignition", 1.0);
        VehicleDynamics.Result r = null;
        double t = 0;
        for (int i = 0; i < 60 / DT; i++, t += DT) { r = VehicleDynamics.step(v, st, env, controls, Vec3d.ZERO, List.of(), null, t, DT); st.setBody(held); }
        double worn = st.used("jet", Conditions.HOT_SECTION), fresh = r.diagnostics().get("jet").get("thrustN");
        assertEquals(1, r.diagnostics().get("jet").get("lit"), 0, "it runs");
        assertTrue(fresh > 20, "at full throttle: " + fresh + " N");
        assertTrue(worn > 0 && worn < 0.05, "running hot wore it a little: " + worn);
        st.setUsed("jet", 0, 0.5);
        r = VehicleDynamics.step(v, st, env, controls, Vec3d.ZERO, List.of(), null, t, DT);
        double half = r.diagnostics().get("jet").get("thrustN");
        assertEquals(EngineWear.hotSection(0.5), half / fresh, 0.01, "half used: " + half + " of " + fresh);
        assertTrue(EngineWear.hotSection(0.5) < 0.95 && EngineWear.hotSection(1) < 0.7);
        st.setUsed("jet", 0, 0); st.setUsed("jet", 1, 0.75);
        r = VehicleDynamics.step(v, st, env, controls, Vec3d.ZERO, List.of(), null, t, DT);
        assertEquals(EngineWear.ancillaries(0.75), r.diagnostics().get("jet").get("thrustN") / fresh, 0.01, "ancillaries three quarters used");
        st.setUsed("jet", 1, 0.5);
        r = VehicleDynamics.step(v, st, env, controls, Vec3d.ZERO, List.of(), null, t, DT);
        assertEquals(1, r.diagnostics().get("jet").get("thrustN") / fresh, 0.01, "ancillaries half used change nothing");
        // failed either way: it goes out and does not light again
        for (int condition = 0; condition < 2; condition++) {
            VehicleState dead = VehicleState.fromJson(v, Json.parse(Json.write(st.toJson())), DT);
            dead.setUsed("jet", 0, condition == 0 ? 1 : 0); dead.setUsed("jet", 1, condition == 1 ? 1 : 0);
            for (int i = 0; i < 5 / DT; i++) { r = VehicleDynamics.step(v, dead, env, controls, Vec3d.ZERO, List.of(), null, t + i * DT, DT); dead.setBody(held); }
            assertEquals(0, r.diagnostics().get("jet").get("lit"), 0, "out");
            assertTrue(r.diagnostics().get("jet").get("thrustN") < 0.05 * fresh, "no thrust: " + r.diagnostics().get("jet").get("thrustN"));
            assertEquals(0, r.diagnostics().get("jet").get("starter"), 0, "and the starter leaves it alone");
        }
        // the rules' ends
        assertEquals(1, EngineWear.ancillaries(0.5), 0);
        assertEquals(0, EngineWear.ancillaries(1), 0);
        assertEquals(0, EngineWear.structure(1), 0);
        assertEquals(0.75, EngineWear.structure(0.5), 1e-12);
        assertEquals(0, EngineWear.leakPerS(0.5), 0);
        assertTrue(EngineWear.leakPerS(0.75) > 0 && EngineWear.leakPerS(0.75) < EngineWear.leakPerS(0.99));
        assertEquals(1, EngineWear.leakPerS(1), 0);
    }

    // ----- bitwise when new -----

    /** A blueprint's copy whose designs carry other names: AeroCore compiles them anew (it keeps compiled parts by their JSON). */
    @SuppressWarnings("unchecked")
    static VehicleBlueprint renamed(VehicleBlueprint b) {
        Map<String, Object> json = (Map<String, Object>) Json.parse(Json.write(b.toJson()));
        for (Object part : (List<Object>) json.get("parts")) {
            Map<String, Object> design = (Map<String, Object>) ((Map<String, Object>) part).get("design");
            design.put("name", design.get("name") + " (wear off)");
        }
        return VehicleBlueprint.fromJson(json);
    }

    /**
     * Each sample vehicle twice: compiled with wear off (nothing wears and every condition stays 0: the paths 0.24 took)
     * and as the game compiles it, its conditions set back to 0 after every step. The two states stay the same to the
     * last bit, while the second, left alone, does wear.
     */
    @Test void aVehicleWithEverythingNewStepsAsOneThatCannotWear() {
        Map<String, Double> controls = Map.of("throttle", 1.0, "selector", 1.0, "collective", 0.6, "rpm", 1.0, "start", 1.0, "steer", 0.3, "ignition", 1.0);
        for (String name : List.of("sedan", "seaplane", "helicopter", "turbineboat", "pulsejetboat", "fanboat")) {
            VehicleBlueprint b = SampleBlueprints.all().get(name);
            DamagePace.set(1000, 0, 0);
            Vehicle off = renamed(b).compile();
            DamagePace.reset();
            Vehicle on = b.compile();
            off.warmUp(); on.warmUp();
            boolean land = name.equals("sedan") || name.equals("helicopter");
            VehicleEnvironment env = land ? VehicleEnvironment.land(FLAT, AIR) : VehicleEnvironment.calm(0, WATER, AIR);
            Vec3d origin = new Vec3d(0, land ? 0.3 : 0.2, 0), gravity = Vec3d.Y.scale(-TestTrack.G);
            VehicleState a = off.newState(origin, Orientation.IDENTITY, DT), z = on.newState(origin, Orientation.IDENTITY, DT), free = on.newState(origin, Orientation.IDENTITY, DT);
            assertEquals(off.conditions(), on.conditions(), name);
            assertTrue(on.conditions() > 0, name + " has conditions");
            double[] zeros = new double[on.conditions()];
            int steps = (int) (4 / DT);
            for (int i = 0; i < steps; i++) {
                VehicleDynamics.step(off, a, env, controls, gravity, List.of(), null, i * DT, DT);
                VehicleDynamics.step(on, z, env, controls, gravity, List.of(), null, i * DT, DT);
                VehicleDynamics.step(on, free, env, controls, gravity, List.of(), null, i * DT, DT);
                z.setUsedValues(zeros);
                if (i % 50 == 49 || i == steps - 1) {
                    assertEquals(0, a.worstUsed(), 0, name + ": with wear off nothing wears");
                    assertEquals(Json.write(a.toJson()), Json.write(z.toJson()), name + " at step " + i);
                }
            }
            List<String> worn = new ArrayList<>();
            for (Vehicle.Installed p : on.installed()) { for (String c : p.part().conditions()) { if (free.used(p.id(), c) > 0) { worn.add(p.id() + "." + c); } } }
            System.out.println(name + ", 4 s flat out: the same to the last bit while new; left alone it wore " + worn);
            if (!name.equals("pulsejetboat")) { assertFalse(worn.isEmpty(), name + " wears when left alone"); }   // a pulsejet's tube and a rudder do not wear by themselves
        }
    }

    /** A design's JSON is what it was: no wear settings in it (they are worked out when it is compiled). */
    @Test void designsCarryNoWearSettings() {
        for (Designs.Sample s : Designs.SAMPLES) {
            PartDesign d = s.design().get();
            String json = Json.write(d.toJson());
            for (String word : List.of("wear", "Wear", "condition", "used", "toughness")) { assertFalse(json.contains("\"" + word), s.id() + " has " + word + " in its JSON"); }
        }
    }
}
