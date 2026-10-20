package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.vehicle.Connection;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.drive.CarKit;
import dev.mcmod.hdfv.drive.Dashboard;
import dev.mcmod.hdfv.drive.DriverInputs;
import dev.mcmod.hdfv.drive.TestCars;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.chassis.ChassisStationDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.SampleWheels;
import dev.mcmod.hdfv.part.wheel.SteerSchedule;
import dev.mcmod.hdfv.part.wheel.SuspensionDesign;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.15: a chassis in an assembly room: where parts go on it, how they are wired, and what the room checks. */
class ChassisAssemblyTest {
    static { HdfvParts.register(); }

    static List<VehicleBlueprint.Entry> of(VehicleBlueprint b, Class<?> kind) {
        List<VehicleBlueprint.Entry> out = new ArrayList<>();
        for (VehicleBlueprint.Entry e : b.parts()) { if (kind.isInstance(e.design())) { out.add(e); } }
        return out;
    }

    @Test void aSixBySixGoesTogetherOnItsFrame() {
        VehicleBlueprint b = TestCars.sixBySix();
        VehicleBlueprint.Entry chassis = of(b, ChassisDesign.class).getFirst(), body = of(b, BodyDesign.class).getFirst();
        assertEquals(Mount.on(chassis.id(), "body"), body.mount(), "the shell on the frame's body mount");
        Set<String> axlePoints = new TreeSet<>();
        for (VehicleBlueprint.Entry a : of(b, AxleDesign.class)) {
            assertEquals(chassis.id(), a.mount().attachPart(), a.id());
            axlePoints.add(a.mount().attachPoint());
        }
        assertEquals(Set.of("axle_1", "axle_2", "axle_3"), axlePoints, "one axle a station, none twice");
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (e.design() instanceof AxleDesign || e == chassis || e == body) { continue; }
            assertEquals(Mount.on(chassis.id(), "engine_bay").attachPoint(), e.mount().attachPoint(), e.id() + " in the frame's engine bay");
        }
        Vehicle v = b.compile();
        assertTrue(v.issues().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), v.issues().toString());
        // the transfer case drives the front axle on its front output and both of the tandem on its rear
        Map<String, String> outputs = new java.util.HashMap<>();
        for (Connection c : b.connections()) {
            if (c instanceof Connection.Shaft s && s.from().startsWith("transfer")) { outputs.put(s.to(), s.fromPort()); }
        }
        assertEquals(3, outputs.size(), outputs.toString());
        for (VehicleBlueprint.Entry a : of(b, AxleDesign.class)) {
            assertEquals(a.mount().attachPoint().equals("axle_1") ? "front" : "rear", outputs.get(a.id()), a.id() + " on " + a.mount().attachPoint());
        }
        // the room: the engine fits under the shell, the frame carries it, the arches are over the wheels, it clears the ground
        AssemblyPlan plan = AssemblyPlan.of(b);
        assertEquals(Room.CAR_FACTORY, Room.of(b));
        assertEquals(List.of(), plan.misfits());
        assertEquals(List.of(), plan.overloads());
        assertEquals(List.of(), plan.bodiesNeedingFrame());
        assertEquals(List.of(), plan.missingArches());
        assertNotNull(plan.clearance());
        double clear = plan.clearance().minClearanceM();
        assertTrue(clear > 0.15 && clear < 0.4, "an off-roader's clearance: " + clear + " m");
        assertEquals(3, plan.clearance().breakoversRad().length + 1, "a breakover angle between each pair of axles");
    }

    @Test void theRoomFindsWhatIsWrongWithACarOnAFrame() {
        VehicleBlueprint b = TestCars.sixBySix();
        VehicleBlueprint.Entry chassis = of(b, ChassisDesign.class).getFirst(), body = of(b, BodyDesign.class).getFirst();
        ChassisDesign frame = (ChassisDesign) chassis.design();
        // designed for less than it carries
        ChassisDesign light = new ChassisDesign(frame.name(), RecordEditor.with(frame.frame(), "designGrossKg", 1000.0), frame.stations());
        VehicleBlueprint overloaded = swap(b, chassis.id(), light);
        assertEquals(1, AssemblyPlan.of(overloaded).overloads().size());
        // the axles hung 0.4 m higher: the frame's rails in the ground
        List<ChassisStationDesign> raised = new ArrayList<>();
        for (ChassisStationDesign s : frame.stations()) {
            raised.add(s.kind() == ChassisStationDesign.Kind.AXLE ? RecordEditor.with(s, "heightM", s.heightM() + 0.4) : s);
        }
        AssemblyPlan sunk = AssemblyPlan.of(swap(b, chassis.id(), new ChassisDesign(frame.name(), frame.frame(), raised)));
        assertTrue(sunk.clearance().minClearanceM() < 0, "rails " + sunk.clearance().minClearanceM() + " m");
        // the middle axle station moved 0.6 m forward, away from the arch
        List<ChassisStationDesign> moved = new ArrayList<>(frame.stations());
        for (int i = 0; i < moved.size(); i++) {
            ChassisStationDesign s = moved.get(i);
            if (s.kind() == ChassisStationDesign.Kind.AXLE && i == 2) { moved.set(i, RecordEditor.with(s, "xM", s.xM() + 0.6)); }
        }
        assertEquals(1, AssemblyPlan.of(swap(b, chassis.id(), new ChassisDesign(frame.name(), frame.frame(), moved))).missingArches().size());
        // the shell made for a frame, alone
        VehicleBlueprint alone = new VehicleBlueprint("shell", List.of(new VehicleBlueprint.Entry("body", body.design(), Mount.ORIGIN)), List.of());
        assertEquals(List.of("body"), ChassisRules.bodiesNeedingFrame(alone));
        assertEquals(List.of(), ChassisRules.bodiesNeedingFrame(b));
    }

    @Test void theSixBySixsSoftAndStiffFramesHoldAndTwistApart() {
        double[] perDeg = new double[TestCars.Frame.values().length];
        for (TestCars.Frame f : TestCars.Frame.values()) {
            VehicleBlueprint b = TestCars.sixBySix(f);
            ChassisDesign c = (ChassisDesign) of(b, ChassisDesign.class).getFirst().design();
            assertTrue(c.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), f + ": " + c.check());
            perDeg[f.ordinal()] = c.analysis().endTwistNmPerDeg();
            System.out.printf("6x6 %s frame: %.0f kg, %.2f kN m/deg, usage %.2f; the car %.0f kg%n", f, c.massKg(), perDeg[f.ordinal()] / 1000, c.analysis().usage(),
                    b.compile().properties().massKg());
            assertEquals(List.of(), AssemblyPlan.of(b).overloads(), f.toString());
        }
        assertTrue(perDeg[TestCars.Frame.STIFF.ordinal()] > 5 * perDeg[TestCars.Frame.SOFT.ordinal()], java.util.Arrays.toString(perDeg));
    }

    @Test void aCarFactoryBuildsTheSixBySixAndRefusesWhatIsWrong() {
        VehicleBlueprint b = TestCars.sixBySix();
        int cw = 5;
        AssemblyTest.World garage = AssemblyTest.room(10, cw, 4, 3, 3, RoomScan.Cell.FLOOR);
        assertEquals(List.of(), AssemblyTest.problems(Room.CAR_FACTORY, garage, cw, AssemblyPlan.of(b)));
        // the shell made for a frame without one; the frame's rails in the ground; an axle without an arch; too heavy
        VehicleBlueprint.Entry chassis = of(b, ChassisDesign.class).getFirst(), body = of(b, BodyDesign.class).getFirst();
        VehicleBlueprint shell = new VehicleBlueprint("shell", List.of(new VehicleBlueprint.Entry("body", body.design(), Mount.ORIGIN)), List.of());
        assertTrue(AssemblyTest.problems(Room.CAR_FACTORY, garage, cw, AssemblyPlan.of(shell)).contains("body_needs_frame"));
        ChassisDesign frame = (ChassisDesign) chassis.design();
        List<ChassisStationDesign> stations = new ArrayList<>();
        for (ChassisStationDesign s : frame.stations()) { stations.add(s.kind() == ChassisStationDesign.Kind.AXLE ? RecordEditor.with(s, "heightM", s.heightM() + 0.4) : s); }
        ChassisDesign low = new ChassisDesign(frame.name(), RecordEditor.with(frame.frame(), "designGrossKg", 1000.0), stations);
        List<String> problems = AssemblyTest.problems(Room.CAR_FACTORY, garage, cw, AssemblyPlan.of(swap(b, chassis.id(), low)));
        assertTrue(problems.containsAll(List.of("touches_ground", "frame_overloaded")), problems.toString());
    }

    static VehicleBlueprint swap(VehicleBlueprint b, String id, dev.mcmod.aerocore.api.vehicle.PartDesign d) {
        List<VehicleBlueprint.Entry> parts = new ArrayList<>();
        for (VehicleBlueprint.Entry e : b.parts()) { parts.add(e.id().equals(id) ? new VehicleBlueprint.Entry(id, d, e.mount(), e.bindings()) : e); }
        return new VehicleBlueprint(b.name(), parts, b.connections());
    }

    @Test void aThirdAxleOnABodyGoesToItsOwnArch() {
        // the 6x6's shell has arches at its hood, its trunk's front and its trunk's end; its hood module flagged too
        BodyDesign shell = (BodyDesign) of(TestCars.sixBySix(), BodyDesign.class).getFirst().design();
        var modules = new ArrayList<>(shell.modules());
        modules.set(0, RecordEditor.with(modules.getFirst(), "axle", true));
        BodyDesign unibody = new BodyDesign("three-axle shell", RecordEditor.with(shell.settings(), "onFrame", false), modules);
        VehicleBlueprint b = new VehicleBlueprint("three", List.of(new VehicleBlueprint.Entry("body", unibody, Mount.ORIGIN)), List.of());
        for (int i = 0; i < 3; i++) { b = Placing.add(b, SampleWheels.offRoadRear()); }
        Set<String> points = new TreeSet<>();
        for (VehicleBlueprint.Entry a : of(b, AxleDesign.class)) { points.add(a.mount().attachPoint()); }
        assertEquals(Set.of("front_axle", "rear_axle", "m5_axle"), points, "not the hood's m1_axle on top of front_axle");
    }

    @Test void theHardpointSedanCompilesWithItsTables() {
        VehicleBlueprint b = TestCars.hardpointSedan();
        Vehicle v = b.compile();
        assertTrue(v.issues().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), v.issues().toString());
        for (String id : List.of("front_axle", "rear_axle")) { assertNotNull(((AxleDesign) b.part(id).design()).kinematics(), id); }
    }

    @Test void theRearSteerSedanSchedulesItsRearAxle() {
        VehicleBlueprint b = TestCars.rearSteerSedan();
        VehicleBlueprint.Entry rear = b.part("rear_axle");
        assertTrue(rear.design().compile() instanceof SteerSchedule);
        assertTrue(rear.bindings().stream().anyMatch(x -> x.role().equals("steer")), rear.bindings().toString());
        Vehicle v = b.compile();
        assertTrue(v.issues().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), v.issues().toString());
    }

    @Test void theRideHeightKeyStepsAnAirCarsLevel() {
        AxleDesign r = SampleWheels.sedanRear();
        SuspensionDesign air = RecordEditor.with(r.suspension(), "springKind", SuspensionDesign.SpringKind.AIR);
        AxleDesign rear = new AxleDesign(r.name(), r.layout(), r.tyre(), r.wheel(), r.brake(), air, r.differential());
        // a new axle comes unwired (AutoWiring keeps the bindings a part already has)
        List<VehicleBlueprint.Entry> parts = new ArrayList<>();
        for (VehicleBlueprint.Entry e : TestCars.manualSedan().parts()) {
            parts.add(e.id().equals("rear_axle") ? new VehicleBlueprint.Entry(e.id(), rear, e.mount(), List.of()) : e);
        }
        VehicleBlueprint b = AutoWiring.wire(new VehicleBlueprint("air sedan", parts, TestCars.manualSedan().connections()));
        var binding = b.part("rear_axle").bindings().stream().filter(x -> x.role().equals("ride_height")).findFirst().orElseThrow();
        assertEquals("ride_height", binding.channel());
        assertEquals(0.5, binding.offset(), 0, "nobody driving: the design height");
        CarKit kit = CarKit.of(b);
        assertTrue(kit.rideHeight());
        assertFalse(CarKit.of(TestCars.manualSedan()).rideHeight());
        DriverInputs in = new DriverInputs();
        in.adopt(kit, Map.of());
        DriverInputs.Keys press = new DriverInputs.Keys(false, false, false, false, false, false, 0, 0, 0, 0, 0, 1);
        assertEquals(0.0, in.tick(kit, DriverInputs.Keys.NONE, 0, false).get("ride_height"));
        assertEquals(0.5, in.tick(kit, press, 0, false).get("ride_height"));
        assertEquals("hdfv.drive.ride_height.high", in.message().key());
        assertEquals(-0.5, in.tick(kit, press, 0, false).get("ride_height"));
        assertEquals(0.0, in.tick(kit, press, 0, false).get("ride_height"));
        // the lamp: HI and LO, nothing at the design height
        var readings = new Dashboard.Readings(0, 0, null, null, Map.of("ride_height", 0.5), 1, true);
        assertTrue(Dashboard.lamps(kit, readings).stream().anyMatch(l -> l.lamp() == Dashboard.Lamp.RIDE_HEIGHT && l.label().equals("HI")));
        assertTrue(Dashboard.lamps(kit, new Dashboard.Readings(0, 0, null, null, Map.of(), 1, true)).stream().noneMatch(l -> l.lamp() == Dashboard.Lamp.RIDE_HEIGHT));
        // a car without air: the key says so and sends nothing
        DriverInputs plain = new DriverInputs();
        CarKit coil = CarKit.of(TestCars.manualSedan());
        plain.adopt(coil, Map.of());
        assertNull(plain.tick(coil, press, 0, false).get("ride_height"));
        assertEquals("hdfv.drive.no_air", plain.message().key());
    }
}
