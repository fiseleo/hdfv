package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.GroundContact;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.earthmoving.Material;
import dev.mcmod.aerocore.api.ground.Ground;
import dev.mcmod.aerocore.api.ground.Terrain;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.Connection;
import dev.mcmod.aerocore.api.vehicle.TestTrack;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.assembly.Room;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 0.26: the test machines (TestMachines) wired, standing, moving their joints on their hydraulics, digging and travelling offline. */
class MachineDriveTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    static final FluidState AIR = dev.mcmod.aerocore.api.Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
    static final Ground FLAT = Ground.flat(0, GroundContact.Surface.SOIL);

    /** Steps a machine for some seconds with fixed controls on a terrain; returns its cuts' bank m^3 by hold name. */
    static Map<String, Double> run(Vehicle v, VehicleState st, Map<String, Double> controls, double seconds, Terrain terrain) {
        VehicleEnvironment env = VehicleEnvironment.land(FLAT, AIR).withTerrain(terrain);
        Map<String, Double> cut = new HashMap<>();
        int steps = (int) Math.round(seconds / TestTrack.DT);
        for (int i = 0; i < steps; i++) {
            VehicleDynamics.step(v, st, env, controls, Vec3d.Y.scale(-TestTrack.G), List.of(), null, i * TestTrack.DT, TestTrack.DT);
            for (VehicleState.Cut c : st.takeCuts()) { cut.merge(c.hold(), c.m3(), Double::sum); }
        }
        return cut;
    }

    static VehicleState settled(Vehicle v) {
        return TestTrack.settle(v, FLAT, AIR, new Vec3d(0, 0.5, 0), 0, Map.of("travel_brake", 1.0), 3);
    }

    @Test void theExcavatorIsWiredAsAMachine() {
        VehicleBlueprint b = TestMachines.excavator();
        assertEquals(Room.HEAVY_YARD, Room.of(b));
        Map<String, List<Binding>> bind = AutoWiring.bindings(b);
        assertTrue(bind.get("boom").contains(Binding.of("command", "boom")), bind.get("boom").toString());
        assertTrue(bind.get("stick").contains(new Binding("command", "stick", -1, 0)), bind.get("stick").toString());
        assertTrue(bind.get("bucket").contains(Binding.of("command", "bucket")), bind.get("bucket").toString());
        assertTrue(bind.get("swing").contains(Binding.of("command", "swing")), bind.get("swing").toString());
        assertTrue(bind.get("travel_left").contains(Binding.of("command", "travel_left")), bind.get("travel_left").toString());
        assertTrue(bind.get("travel_right").contains(Binding.of("command", "travel_right")), bind.get("travel_right").toString());
        assertTrue(bind.get("bucket").contains(Binding.of("full", "bucket.full")), "the game tells the bucket it is full: " + bind.get("bucket"));
        long pumps = b.connections().stream().filter(c -> c instanceof Connection.Shaft s && s.from().equals("engine")).count();
        assertEquals(4, pumps, "the diesel turns its four pumps (main, swing, travel, auxiliary): " + b.connections());
        assertTrue(b.connections().stream().anyMatch(c -> c instanceof Connection.Shaft s && s.from().equals("travel_left") && "left".equals(s.toPort())), b.connections().toString());
        WorkInputs.Kit kit = WorkInputs.Kit.of(b);
        assertTrue(kit.slews() && kit.travel(), kit.toString());
        assertTrue(CarKit.of(b).car(), "a crawler is driven with the car keys");
    }

    @Test void theExcavatorStandsOnItsTracksAndWeighsTwentyTonnes() {
        Vehicle v = TestMachines.excavator().compile();
        double kg = v.properties().massKg();
        assertTrue(kg > 15_000 && kg < 26_000, "a 20 t class excavator: " + kg + " kg");
        VehicleState st = settled(v);
        double y = st.origin().y();
        run(v, st, Map.of("travel_brake", 1.0), 2, Terrain.NONE);
        assertEquals(y, st.origin().y(), 0.03, "it stands still on its tracks");
        assertTrue(st.body().velocity().length() < 0.05, "and does not creep: " + st.body().velocity());
    }

    @Test void theBoomRisesAndTheHouseSlewsOnTheirHydraulics() {
        Vehicle v = TestMachines.excavator().compile();
        VehicleState st = settled(v);
        int boom = st.joint("boom"), swing = st.joint("swing");
        double boom0 = st.jointValue(boom), swing0 = st.jointValue(swing);
        run(v, st, Map.of("throttle", 1.0, "boom", 1.0, "travel_brake", 1.0), 3, Terrain.NONE);
        assertTrue(st.jointValue(boom) > boom0 + 0.1, "the boom rises: " + boom0 + " -> " + st.jointValue(boom) + "; main circuit: share " + st.hydraulicShare("main")
                + ", pressure " + st.hydraulicPressure("main") + ", flows " + java.util.Arrays.toString(st.hydraulicFlows("main")) + ", rate " + st.jointRate(boom)
                + ", effort " + st.jointEffort(boom) + ", load " + st.jointLoad(boom) + ", engine " + st.diagnostics().get("engine") + ", pump " + st.diagnostics().get("pump") + ", boom " + st.diagnostics().get("boom"));
        run(v, st, Map.of("throttle", 1.0, "swing", 1.0, "travel_brake", 1.0), 4, Terrain.NONE);
        assertTrue(st.jointValue(swing) > swing0 + 0.3, "the house slews left: " + swing0 + " -> " + st.jointValue(swing));
        assertTrue(st.hydraulicPressure("main") >= 0, "a circuit pressure");
    }

    @Test void itTravelsOnItsTrackMotors() {
        Vehicle v = TestMachines.excavator().compile();
        VehicleState st = settled(v);
        double x = st.origin().x();
        run(v, st, Map.of("throttle", 1.0, "travel_left", 1.0, "travel_right", 1.0), 6, Terrain.NONE);
        assertTrue(st.origin().x() - x > 1, "both tracks forward drive it forward: " + (st.origin().x() - x) + " m");
    }

    @Test void theBucketDigsLoamIntoItsHold() {
        Vehicle v = TestMachines.excavator().compile();
        VehicleState st = settled(v);
        Map<Long, Terrain.Cell> cells = new HashMap<>();
        for (int x = -12; x < 14; x++) for (int z = -10; z < 10; z++) for (int y = -4; y < 0; y++) { cells.put(Terrain.key(x, y, z), Terrain.Cell.of("minecraft:dirt", Material.LOAM)); }
        Terrain ground = Terrain.grid(cells);
        // stick out and boom down into the ground, then crowd the stick and curl the bucket
        run(v, st, Map.of("throttle", 1.0, "stick", -1.0, "travel_brake", 1.0), 2.5, ground);
        Map<String, Double> cut = new HashMap<>(run(v, st, Map.of("throttle", 1.0, "boom", -1.0, "travel_brake", 1.0), 3, ground));
        run(v, st, Map.of("throttle", 1.0, "stick", 1.0, "bucket", 1.0, "travel_brake", 1.0), 4, ground).forEach((k, m3) -> cut.merge(k, m3, Double::sum));
        double bulk = cut.getOrDefault("bucket.bulk", 0.0) + cut.getOrDefault("bulk", 0.0);
        assertTrue(bulk > 0.05, "the bucket takes loam in: " + cut);
    }

    @Test void everyTestMachineIsWiredAndCompilesForTheHeavyYard() {
        assertTrue(TestMachines.ALL.size() >= 32, "a test machine of every kind: " + TestMachines.ALL.size());
        StringBuilder bad = new StringBuilder();
        for (Map.Entry<String, java.util.function.Supplier<VehicleBlueprint>> e : TestMachines.ALL.entrySet()) {
            try {
                VehicleBlueprint b = e.getValue().get();
                Vehicle v = b.compile();
                if (!(v.properties().massKg() > 1000)) { bad.append(e.getKey()).append(": ").append(v.properties().massKg()).append(" kg; "); }
                if (Room.of(b) != Room.HEAVY_YARD) { bad.append(e.getKey()).append(" not for the heavy yard; "); }
                if (!WorkInputs.Kit.of(b).machine()) { bad.append(e.getKey()).append(" has no work keys; "); }
                VehicleState st = v.newState(new Vec3d(0, 2, 0), dev.mcmod.aerocore.api.Orientation.IDENTITY, TestTrack.DT);
                run(v, st, Map.of("travel_brake", 1.0, "brake", 1.0), 0.5, Terrain.NONE);
                if (!Double.isFinite(st.origin().y()) || st.origin().y() < -5) { bad.append(e.getKey()).append(" falls through: ").append(st.origin()).append("; "); }
            } catch (RuntimeException x) {
                bad.append(e.getKey()).append(": ").append(x).append("; ");
            }
        }
        assertEquals("", bad.toString());
    }

    @Test void pressingTheBucketIntoTheGroundNeverThrowsTheMachine() {
        // the bucket held down on the ground (the boom lifts the tracks), then the boom raised: the contacts only resist
        // the joints (JointDrive), so nothing flings the excavator (it did in the world before: 28 m/s into the sky)
        Vehicle v = TestMachines.excavator().compile();
        VehicleState st = settled(v);
        double y0 = st.origin().y(), most = 0;
        for (String[] phase : new String[][]{{"boom", "-1"}, {"boom", "1"}, {"stick", "-1"}, {"boom", "-1"}, {"boom", "1"}}) {
            run(v, st, Map.of("throttle", 1.0, "travel_brake", 1.0, phase[0], Double.parseDouble(phase[1])), 2, Terrain.NONE);
            most = Math.max(most, st.body().velocity().length());
            assertTrue(st.origin().y() < y0 + 3, "it stays on the ground: " + st.origin().y() + " from " + y0);
        }
        assertTrue(most < 2, "no fling: " + most + " m/s");
        for (Vehicle.Joint j : v.joints()) { assertTrue(Math.abs(st.jointRate(j.index())) < 3, j.id() + " turns at " + st.jointRate(j.index())); }
    }
}
