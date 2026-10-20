package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.Connection;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssemblyTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    static Connection.Shaft shaft(VehicleBlueprint b, String from, String to) {
        for (Connection c : b.connections()) { if (c instanceof Connection.Shaft s && s.from().equals(from) && s.to().equals(to)) { return s; } }
        return null;
    }

    /** The wiring of a stripped sample matches the sample's own: shafts (ratio within 1 %, efficiency within 0.012), slipstreams and bindings. */
    static void sameWiring(VehicleBlueprint sample) {
        VehicleBlueprint wired = AutoWiring.wire(AutoWiring.stripped(sample));
        assertEquals(sample.connections().size(), wired.connections().size(), sample.name() + ": " + wired.connections());
        for (Connection c : sample.connections()) {
            if (c instanceof Connection.Shaft s) {
                Connection.Shaft w = shaft(wired, s.from(), s.to());
                assertNotNull(w, sample.name() + " lacks " + s + " in " + wired.connections());
                assertEquals(s.ratio(), w.ratio(), 0.01 * Math.abs(s.ratio()), sample.name() + " " + s.from() + "-" + s.to() + " ratio");
                assertEquals(s.efficiency(), w.efficiency(), 0.012, sample.name() + " " + s.from() + "-" + s.to() + " efficiency");
                assertEquals(s.freewheel(), w.freewheel(), sample.name() + " " + s.from() + "-" + s.to() + " freewheel");
            } else {
                assertTrue(wired.connections().contains(c), sample.name() + " lacks " + c);
            }
        }
        Map<String, List<Binding>> want = AutoWiring.bindings(sample), got = AutoWiring.bindings(wired);
        for (String id : want.keySet()) { assertEquals(new HashSet<>(want.get(id)), new HashSet<>(got.get(id)), sample.name() + " " + id + " bindings"); }
        wired.compile();
    }

    @Test void autoWiringRebuildsTheSamplesWiring() {
        sameWiring(SampleBlueprints.seaplane());
        sameWiring(SampleBlueprints.helicopter());
        // and wiring a wired blueprint changes nothing
        VehicleBlueprint heli = SampleBlueprints.helicopter();
        assertEquals(heli.connections(), AutoWiring.wire(heli).connections());
        assertEquals(AutoWiring.bindings(heli), AutoWiring.bindings(AutoWiring.wire(heli)));
    }

    @Test void aBoatsScrewIsGearedToItsTipSpeedAndItsRudderInItsRace() {
        VehicleBlueprint launch = AutoWiring.wire(SampleLayouts.launch());
        Connection.Shaft s = shaft(launch, "motor", "screw");
        assertNotNull(s, launch.connections().toString());
        // a 4000 rpm motor to a 0.35 m screw at 35 m/s at its tips (1910 rpm)
        assertEquals(4000 / (35 / (Math.PI * 0.35) * 60), s.ratio(), 1e-9);
        assertTrue(launch.connections().contains(new Connection.Slipstream("screw", "rudder")));
        assertEquals(List.of(Binding.of("throttle", "throttle")), launch.part("motor").bindings());
        assertEquals(List.of(Binding.of("deflection", "rudder")), launch.part("rudder").bindings());
        assertEquals(Room.SHIPYARD, Room.of(launch));
    }

    @Test void aCarIsWiredFromItsEngineThroughTheGearboxToItsDrivenAxle() {
        VehicleBlueprint sedan = AutoWiring.wire(SampleLayouts.sedan());
        assertEquals(Room.CAR_FACTORY, Room.of(sedan));
        Connection.Shaft in = shaft(sedan, "engine", "gearbox"), out = shaft(sedan, "gearbox", "rear_axle");
        assertNotNull(in, sedan.connections().toString());
        assertNotNull(out, sedan.connections().toString());
        assertEquals("input", in.toPort());
        assertEquals("output", out.fromPort());
        assertEquals("input", out.toPort());
        assertEquals(2, sedan.connections().size(), "the front axle is not driven: " + sedan.connections());
        assertTrue(sedan.part("front_axle").bindings().contains(Binding.of("steer", "steer")));
        // the automatic's selector, and the accelerator its shift schedule reads
        assertTrue(sedan.part("gearbox").bindings().containsAll(List.of(Binding.of("selector", "selector"), Binding.of("throttle", "throttle"))),
                sedan.part("gearbox").bindings().toString());
        sedan.compile();
    }

    @Test void assemblyPlansSayWhatEachRoomNeeds() {
        AssemblyPlan sea = AssemblyPlan.of(AutoWiring.wire(SampleLayouts.seaplane()));
        assertEquals(Room.HANGAR, sea.room());
        // bench-made: engine, propeller, tail surfaces' and rudders' parts; room-built: the fuselage, floats and wing
        assertTrue(sea.parts().stream().anyMatch(n -> n.design().type().equals("hdfv:turboprop")), sea.parts().toString());
        assertFalse(sea.parts().stream().anyMatch(n -> n.design().type().equals("hdfv:body")), "bodies are built in the room");
        assertTrue(sea.parts().stream().anyMatch(n -> n.count() == 2), "two water rudders of one design");
        assertTrue(sea.needsCrane() && sea.needsWelding(), "a turboprop needs the crane, the riveted fuselage a welding station");
        // the Turbo Otter's 17.7 m span (and the wing tips' contact spheres) and 12.8 m length (with the propeller's disc)
        System.out.printf("seaplane: %.1f x %.1f x %.1f m, %.0f h of room work, heaviest %.0f kg, %d pieces%n", sea.lengthM(), sea.widthM(), sea.heightM(),
                sea.laborHours(), sea.heaviestKg(), sea.pieces().size());
        assertTrue(sea.widthM() >= 17.7 && sea.widthM() < 19.5, "span " + sea.widthM());
        assertTrue(sea.lengthM() > 11 && sea.lengthM() < 15, "length " + sea.lengthM());
        AssemblyPlan car = AssemblyPlan.of(AutoWiring.wire(SampleLayouts.sedan()));
        assertEquals(Room.CAR_FACTORY, car.room());
        assertTrue(car.needsWelding() && car.needsCrane(), "a spot-welded body; the engine is lifted in");
        System.out.printf("sedan: %.1f x %.1f x %.1f m, %.0f h, heaviest %.0f kg%n", car.lengthM(), car.widthM(), car.heightM(), car.laborHours(), car.heaviestKg());
        assertTrue(car.lengthM() > 4 && car.lengthM() < 5.5 && car.widthM() > 1.6 && car.widthM() < 2.1, "a sedan's size");
    }

    // ---------------------------------------------------------------- rooms

    /** A world of cells: ground below y = 1, air above, and what a test builds. */
    static final class World implements RoomScan.Grid {
        final Map<List<Integer>, RoomScan.Cell> cells = new HashMap<>();
        @Override public RoomScan.Cell at(int x, int y, int z) { return cells.getOrDefault(List.of(x, y, z), y < 1 ? RoomScan.Cell.WALL : RoomScan.Cell.AIR); }
        void set(int x, int y, int z, RoomScan.Cell c) { cells.put(List.of(x, y, z), c); }
        void fill(int x0, int y0, int z0, int x1, int y1, int z1, RoomScan.Cell c) {
            for (int x = x0; x <= x1; x++) { for (int y = y0; y <= y1; y++) { for (int z = z0; z <= z1; z++) { set(x, y, z, c); } } }
        }
    }

    /**
     * A room L long (x), W wide (z) and H high inside, its floor at y = 0 and the inside from y = 1: walls round it, a
     * roof with a crane rail down the middle, a door across the +x end (door wide, door high, centred) and the
     * controller in the -x wall facing in. A welding station stands in a corner.
     */
    static World room(int l, int w, int h, int doorW, int doorH, RoomScan.Cell floor) {
        World g = new World();
        g.fill(-1, 0, -1, l, h + 1, w, RoomScan.Cell.WALL);
        g.fill(0, 1, 0, l - 1, h, w - 1, RoomScan.Cell.AIR);
        g.fill(0, 0, 0, l - 1, 0, w - 1, floor);
        g.fill(0, h + 1, w / 2, l - 1, h + 1, w / 2, RoomScan.Cell.CRANE);
        int z0 = (w - doorW) / 2;
        g.fill(l, 1, z0, l, doorH, z0 + doorW - 1, RoomScan.Cell.DOOR);
        g.set(-1, 1, w / 2, RoomScan.Cell.CONTROLLER);
        g.set(0, 1, 0, RoomScan.Cell.WELDING_STATION);
        return g;
    }

    static RoomScan.Scan scan(World g, int w) { return RoomScan.scan(g, -1, 1, w / 2, 1, 0); }

    static List<String> problems(Room room, World g, int w, AssemblyPlan p) {
        List<String> out = new ArrayList<>();
        for (RoomRules.Problem pr : RoomRules.check(room, scan(g, w), p)) { out.add(pr.key()); }
        return out;
    }

    @Test void aHangarBigEnoughBuildsTheSeaplaneAndFaultsAreFound() {
        AssemblyPlan sea = AssemblyPlan.of(AutoWiring.wire(SampleLayouts.seaplane()));
        int l = 16, w = 22, h = 8;
        World ok = room(l, w, h, 20, 7, RoomScan.Cell.FLOOR);
        RoomScan.Scan s = scan(ok, w);
        assertTrue(s.closed());
        assertEquals(l * w * h - 1, s.cells(), "the room's air, less the welding station");
        assertEquals(1, s.doors().size());
        assertEquals(20, s.doors().get(0).widthM());
        assertEquals(1, s.doors().get(0).outX());
        assertEquals(List.of(), problems(Room.HANGAR, ok, w, sea));
        // a hole in the wall
        World leak = room(l, w, h, 20, 7, RoomScan.Cell.FLOOR);
        leak.set(5, 3, -1, RoomScan.Cell.AIR);
        assertEquals(List.of("open"), problems(Room.HANGAR, leak, w, sea));
        // a patch of bare ground
        World bare = room(l, w, h, 20, 7, RoomScan.Cell.FLOOR);
        bare.set(6, 0, 6, RoomScan.Cell.WALL);
        assertTrue(problems(Room.HANGAR, bare, w, sea).contains("floor"));
        // a door too narrow for the wing
        assertTrue(problems(Room.HANGAR, room(l, w, h, 12, 7, RoomScan.Cell.FLOOR), w, sea).contains("door_small"));
        // a room too short
        assertTrue(problems(Room.HANGAR, room(10, w, h, 20, 7, RoomScan.Cell.FLOOR), w, sea).contains("room_small"));
        // a tree in front of the door
        World blocked = room(l, w, h, 20, 7, RoomScan.Cell.FLOOR);
        blocked.set(l + 3, 2, w / 2, RoomScan.Cell.WALL);
        assertEquals(List.of("apron"), problems(Room.HANGAR, blocked, w, sea));
        // no crane, no welding station
        World bareRoof = room(l, w, h, 20, 7, RoomScan.Cell.FLOOR);
        bareRoof.fill(0, h + 1, w / 2, l - 1, h + 1, w / 2, RoomScan.Cell.WALL);
        bareRoof.set(0, 1, 0, RoomScan.Cell.AIR);
        assertEquals(List.of("crane", "welding_station"), problems(Room.HANGAR, bareRoof, w, sea));
        // a seaplane is not built in a shipyard
        assertTrue(problems(Room.SHIPYARD, ok, w, sea).contains("wrong_room"));
    }

    @Test void aShipyardNeedsASlipwayAndWaterAndACarFactoryAGroundLevelDoor() {
        AssemblyPlan launch = AssemblyPlan.of(AutoWiring.wire(SampleLayouts.launch()));
        int l = 10, w = 6, h = 5;
        World dry = room(l, w, h, 4, 4, RoomScan.Cell.SLIPWAY);
        assertEquals(List.of("water"), problems(Room.SHIPYARD, dry, w, launch));
        World wet = room(l, w, h, 4, 4, RoomScan.Cell.SLIPWAY);
        wet.fill(l + 1, -2, -2, l + 6, 0, w + 2, RoomScan.Cell.WATER);
        assertEquals(List.of(), problems(Room.SHIPYARD, wet, w, launch));
        World plain = room(l, w, h, 4, 4, RoomScan.Cell.FLOOR);
        plain.fill(l + 1, -2, -2, l + 6, 0, w + 2, RoomScan.Cell.WATER);
        assertEquals(List.of("slipway"), problems(Room.SHIPYARD, plain, w, launch));
        AssemblyPlan sedan = AssemblyPlan.of(AutoWiring.wire(SampleLayouts.sedan()));
        int cw = 5;
        World garage = room(8, cw, 4, 3, 3, RoomScan.Cell.FLOOR);
        assertEquals(List.of(), problems(Room.CAR_FACTORY, garage, cw, sedan));
        // a door with its sill a block up
        World high = room(8, cw, 4, 3, 3, RoomScan.Cell.FLOOR);
        high.fill(8, 1, 1, 8, 1, 3, RoomScan.Cell.WALL);
        high.fill(8, 4, 1, 8, 4, 3, RoomScan.Cell.DOOR);
        assertTrue(problems(Room.CAR_FACTORY, high, cw, sedan).contains("door_ground"), problems(Room.CAR_FACTORY, high, cw, sedan).toString());
        // 0.14: an engine that does not fit in the body, and seat parts in the body's seats, stop the room
        assertEquals(List.of("part_does_not_fit", "seat_clash"), problems(Room.CAR_FACTORY, garage, cw, AssemblyPlan.of(AutoWiring.wire(BayFitTest.oldSedan()))));
    }

    @Test void partsAddedOneByOneFindTheirPlaces() {
        VehicleBlueprint sedan = SampleLayouts.sedan();
        VehicleBlueprint built = new VehicleBlueprint("sedan", List.of(), List.of());
        for (String id : List.of("body", "engine", "gearbox", "front_axle", "rear_axle")) { built = Placing.add(built, sedan.part(id).design()); }
        assertEquals(List.of("body_1", "piston_engine_1", "automatic_1", "axle_1", "axle_2"), built.parts().stream().map(VehicleBlueprint.Entry::id).toList());
        assertEquals("engine_bay", built.part("piston_engine_1").mount().attachPoint());
        assertEquals("engine_bay", built.part("automatic_1").mount().attachPoint());
        // 0.14: where the sample has them (the engine fits on the bay's point, the gearbox on its flywheel face)
        assertEquals(sedan.part("engine").mount().position().x(), built.part("piston_engine_1").mount().position().x(), 1e-9);
        assertEquals(sedan.part("gearbox").mount().position().x(), built.part("automatic_1").mount().position().x(), 1e-9);
        assertEquals("front_axle", built.part("axle_1").mount().attachPoint());
        assertEquals("rear_axle", built.part("axle_2").mount().attachPoint());
        VehicleBlueprint wired = AutoWiring.wire(built);
        assertNotNull(shaft(wired, "piston_engine_1", "automatic_1"), wired.connections().toString());
        assertNotNull(shaft(wired, "automatic_1", "axle_2"), wired.connections().toString());
        // the body cannot go while the others hang on it; nudged and turned, an offset moves
        VehicleBlueprint b = built;
        assertThrows(IllegalArgumentException.class, () -> Placing.remove(b, "body_1"));
        VehicleBlueprint moved = Placing.nudge(built, "piston_engine_1", new dev.mcmod.aerocore.api.Vec3d(0.1, 0, 0), 190, 0, 0);
        assertEquals(built.part("piston_engine_1").mount().position().x() + 0.1, moved.part("piston_engine_1").mount().position().x(), 1e-12);
        assertEquals(-170, moved.part("piston_engine_1").mount().yawDeg(), 1e-9);
        assertEquals(4, Placing.remove(built, "axle_2").parts().size());
        assertFalse(Placing.attach(built, "axle_1", null, null).part("axle_1").mount().attached());
    }
}
