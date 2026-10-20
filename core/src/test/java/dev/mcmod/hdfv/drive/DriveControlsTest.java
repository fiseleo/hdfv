package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.drive.Gearboxes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** 0.14: what a car has (CarKit), its driver's keys turned into channels (DriverInputs) and its dashboard (Dashboard). */
class DriveControlsTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    static VehicleBlueprint fourByFour() { return TestCars.fourByFour(); }

    @Test void theSedansKit() {
        CarKit kit = CarKit.of(SampleBlueprints.sedan());
        assertTrue(kit.car() && kit.steers() && kit.handbrake(), kit.toString());
        assertEquals(Gearboxes.Kind.AUTOMATIC, kit.gearbox());
        assertEquals(6, kit.forwardGears());
        assertTrue(kit.selector() && kit.manualMode() && !kit.autoClutch());
        assertNull(kit.transfer());
        assertTrue(kit.modes().isEmpty());
        assertEquals(0, kit.diffLocks());
        assertEquals("engine", kit.engine());
        assertTrue(kit.piston() && kit.idleRpm() > 500 && kit.redlineRpm() > 5000, kit.toString());
        assertTrue(kit.fullScaleRpm() > kit.redlineRpm() && kit.fullScaleRpm() % 1000 == 0, "the tachometer to the next 1000 rpm above the red line");
        assertTrue(kit.topSpeedMps() * 3.6 > 150 && kit.topSpeedMps() * 3.6 < 320, "the red line in fourth (the last gear not an overdrive): " + kit.topSpeedMps() * 3.6 + " km/h");
        assertTrue(kit.speedScaleKmh() >= kit.topSpeedMps() * 3.6, "the speed dial reaches it: " + kit.speedScaleKmh());
        assertSame(CarKit.NONE, CarKit.of(SampleBlueprints.seaplane()), "an aircraft has no car keys");
    }

    @Test void aWiredFourByFoursKitAndChannels() {
        VehicleBlueprint bp = fourByFour();
        Map<String, List<Binding>> b = AutoWiring.bindings(bp);
        assertTrue(b.get("rear_axle").contains(new Binding("lock", "diff_lock", 1, 0)), "the rear locker at 1: " + b.get("rear_axle"));
        assertTrue(b.get("front_axle").contains(new Binding("lock", "diff_lock", 1, -1)), "the front locker at 2: " + b.get("front_axle"));
        assertTrue(b.get("gearbox").contains(Binding.of("auto_clutch", "auto_clutch")), "the manual's automatic clutch: " + b.get("gearbox"));
        assertTrue(b.get("transfer").contains(Binding.of("mode", "mode")), b.get("transfer").toString());
        CarKit kit = CarKit.of(bp);
        assertEquals(Gearboxes.Kind.MANUAL, kit.gearbox());
        assertTrue(kit.autoClutch() && !kit.selector());
        assertEquals(TransferPart.Kind.PART_TIME, kit.transfer());
        assertEquals(List.of(DriveMode.TWO_HIGH, DriveMode.FOUR_HIGH, DriveMode.FOUR_LOW), kit.modes());
        assertEquals(2, kit.diffLocks());
        assertFalse(kit.lock());
    }

    static DriverInputs.Keys keys(boolean left, boolean right) { return new DriverInputs.Keys(left, right, false, false, false, false, 0, 0, 0, 0, 0); }
    static DriverInputs.Keys clicks(int up, int down, int selector, int mode, int lock) { return new DriverInputs.Keys(false, false, false, false, false, false, up, down, selector, mode, lock); }
    static DriverInputs.Keys handbrake(boolean held) { return new DriverInputs.Keys(false, false, false, false, held, false, 0, 0, 0, 0, 0); }

    @Test void steeringIsSmoothAndLessAtSpeed() {
        CarKit kit = CarKit.of(SampleBlueprints.sedan());
        DriverInputs in = new DriverInputs();
        in.adopt(kit, Map.of());
        Map<String, Double> out = in.tick(kit, keys(true, false), 0, false);
        assertEquals(DriverInputs.STEER_RATE * DriverInputs.TICK, out.get("steer"), 1e-9, "a step at a time, left positive");
        for (int i = 0; i < 20; i++) { out = in.tick(kit, keys(true, false), 0, false); }
        assertEquals(1, out.get("steer"), 1e-9, "full lock when slow");
        for (int i = 0; i < 3; i++) { out = in.tick(kit, keys(false, false), 0, false); }
        assertEquals(1 - 3 * DriverInputs.STEER_RETURN * DriverInputs.TICK, out.get("steer"), 1e-9, "back toward the middle faster");
        for (int i = 0; i < 20; i++) { out = in.tick(kit, keys(false, false), 0, false); }
        assertEquals(0, out.get("steer"), 1e-9);
        for (int i = 0; i < 40; i++) { out = in.tick(kit, keys(false, true), DriverInputs.STEER_HALF_SPEED, false); }
        assertEquals(-0.5, out.get("steer"), 1e-9, "half the lock at 90 km/h");
        assertEquals(0.0, in.tick(kit, new DriverInputs.Keys(false, false, true, false, false, false, 0, 0, 0, 0, 0), 0, false).get("brake") - 1, 1e-9, "back is the brake");
    }

    @Test void theHandbrakeLatchesOnATapAndHoldsWhileHeld() {
        CarKit kit = CarKit.of(SampleBlueprints.sedan());
        DriverInputs in = new DriverInputs();
        in.adopt(kit, Map.of());
        assertEquals(0, in.tick(kit, handbrake(false), 0, false).get("handbrake"));
        assertEquals(1, in.tick(kit, handbrake(true), 0, false).get("handbrake"), "on as soon as pressed");
        assertEquals(1, in.tick(kit, handbrake(false), 0, false).get("handbrake"), "a tap leaves it pulled up");
        assertEquals("hdfv.drive.handbrake_on", in.message().key());
        in.tick(kit, handbrake(true), 0, false);
        assertEquals(0, in.tick(kit, handbrake(false), 0, false).get("handbrake"), "a second tap lets it down");
        for (int i = 0; i < DriverInputs.HANDBRAKE_HOLD_TICKS + 4; i++) { assertEquals(1, in.tick(kit, handbrake(true), 0, false).get("handbrake"), "held"); }
        assertEquals(0, in.tick(kit, handbrake(false), 0, false).get("handbrake"), "held long, let go: off (a slide)");
        // a car without a handbrake is sent none
        assertFalse(in.tick(new CarKit(true, true, false, null, null, 0, false, null, null, false, null, 0, false, null, false, 0, 0, 0, 0), handbrake(true), 0, false)
                .containsKey("handbrake"));
    }

    @Test void theSelectorAndThePaddles() {
        CarKit kit = CarKit.of(SampleBlueprints.sedan());
        DriverInputs in = new DriverInputs();
        in.adopt(kit, Map.of("selector", -2.0));
        assertEquals(-2, in.tick(kit, DriverInputs.Keys.NONE, 0, false).get("selector"), "adopted P");
        Map<String, Double> out = in.tick(kit, clicks(1, 0, 0, 0, 0), 0, false);
        assertEquals(-2, out.get("selector"), "no paddles in P");
        assertEquals("hdfv.drive.shift_in_drive", in.message().key());
        out = in.tick(kit, clicks(0, 0, 3, 0, 0), 0, false);
        assertEquals(Gearboxes.DRIVE, out.get("selector"), 0, "P, R, N, D");
        assertEquals("hdfv.flight.selector.D", in.message().key());
        // a paddle in D: M and a pulse of PULSE_TICKS, a tick at 0, then the queued second press
        List<Double> shift = new ArrayList<>();
        out = in.tick(kit, clicks(2, 0, 0, 0, 0), 0, false);
        assertEquals(Gearboxes.MANUAL_MODE, out.get("selector"), 0);
        shift.add(out.get("shift"));
        for (int i = 0; i < 6; i++) { shift.add(in.tick(kit, DriverInputs.Keys.NONE, 0, false).get("shift")); }
        assertEquals(List.of(1.0, 1.0, 0.0, 1.0, 1.0, 0.0, 0.0), shift, "two presses, two pulses");
        out = in.tick(kit, clicks(0, 1, 0, 0, 0), 0, false);
        assertEquals(-1, out.get("shift"), "down");
        out = in.tick(kit, clicks(0, 0, 1, 0, 0), 0, false);
        assertEquals(Gearboxes.DRIVE, out.get("selector"), 0, "the selector key from M: back to D");
    }

    @Test void aManualsLeverClutchAndReverse() {
        CarKit kit = CarKit.of(fourByFour());
        DriverInputs in = new DriverInputs();
        in.adopt(kit, Map.of());
        Map<String, Double> out = in.tick(kit, DriverInputs.Keys.NONE, 0, true);
        assertEquals(0, out.get("gear"));
        assertEquals(1, out.get("auto_clutch"), "the player's choice");
        assertFalse(out.containsKey("selector"));
        out = in.tick(kit, clicks(2, 0, 0, 0, 0), 0, true);
        assertEquals(2, out.get("gear"));
        for (int i = 0; i < 9; i++) { out = in.tick(kit, clicks(1, 0, 0, 0, 0), 0, true); }
        assertEquals(5, out.get("gear"), "a five-speed stops at fifth");
        out = in.tick(kit, clicks(0, 0, 1, 0, 0), 0, true);
        assertEquals(0, out.get("gear"), "the selector key: neutral");
        out = in.tick(kit, clicks(0, 1, 0, 0, 0), 5, true);
        assertEquals(0, out.get("gear"), "not into reverse at 18 km/h");
        assertEquals("hdfv.drive.reverse_stop", in.message().key());
        out = in.tick(kit, clicks(0, 1, 0, 0, 0), 0.5, true);
        assertEquals(-1, out.get("gear"), "reverse when nearly stopped");
        out = in.tick(kit, new DriverInputs.Keys(false, false, false, false, false, true, 0, 0, 0, 0, 0), 0, false);
        assertEquals(1, out.get("clutch"));
        assertEquals(0, out.get("auto_clutch"));
    }

    @Test void driveModesAndLockers() {
        CarKit kit = CarKit.of(fourByFour());
        DriverInputs in = new DriverInputs();
        in.adopt(kit, Map.of("mode", 1.0, "diff_lock", 1.0));
        assertEquals(DriveMode.FOUR_HIGH, in.mode(kit), "adopted 4H");
        List<Double> modes = new ArrayList<>(), locks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Double> out = in.tick(kit, clicks(0, 0, 0, 1, 1), 0, false);
            modes.add(out.get("mode"));
            locks.add(out.get("diff_lock"));
        }
        assertEquals(List.of(2.0, 0.0, 1.0), modes, "4L, 2H, 4H");
        assertEquals(List.of(2.0, 0.0, 1.0), locks, "both, off, rear");
        // a full-time case with a centre lock and a low range
        CarKit full = new CarKit(true, true, true, null, null, 0, false, "tc", TransferPart.Kind.FULL_TIME, true, "centre_lock", 1, false, null, false, 0, 0, 0, 0);
        in.adopt(full, Map.of());
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Double> out = in.tick(full, clicks(0, 0, 0, 1, 0), 0, false);
            seen.add(out.get("range") + "/" + out.get("centre_lock"));
        }
        assertEquals(List.of("0.0/1.0", "1.0/1.0", "0.0/0.0"), seen, "H locked, L locked, H");
        // nothing to switch
        CarKit sedan = CarKit.of(SampleBlueprints.sedan());
        in.adopt(sedan, Map.of());
        Map<String, Double> out = in.tick(sedan, clicks(0, 0, 0, 1, 0), 0, false);
        assertFalse(out.containsKey("mode") || out.containsKey("range"));
        assertEquals("hdfv.drive.no_modes", in.message().key());
        in.tick(sedan, clicks(0, 0, 0, 0, 1), 0, false);
        assertEquals("hdfv.drive.no_locker", in.message().key());
    }

    @Test void theDashboard() {
        CarKit sedan = CarKit.of(SampleBlueprints.sedan());
        Map<String, Double> c = new HashMap<>(Map.of("handbrake", 1.0));
        Dashboard.Face f = Dashboard.of(sedan, new Dashboard.Readings(20, 3000, new Dashboard.Box(3, 0, 1), null, c, 0.05, false), false);
        assertEquals("D3", f.gear().text());
        assertEquals("PRNDM", f.gear().letters());
        assertEquals(3, f.gear().lit());
        assertEquals(72, f.speed(), 1e-9, "km/h");
        assertEquals(List.of(Dashboard.Lamp.HANDBRAKE, Dashboard.Lamp.LOW_FUEL, Dashboard.Lamp.ENGINE), f.lamps().stream().map(Dashboard.Lit::lamp).toList());
        assertEquals("M2", Dashboard.gear(sedan, new Dashboard.Readings(0, 0, new Dashboard.Box(2, 1, 2), null, Map.of(), 1, true)).text());
        assertTrue(Dashboard.gear(sedan, new Dashboard.Readings(0, 0, new Dashboard.Box(2, 1, 2), null, Map.of(), 1, true)).changing());
        assertEquals("P", Dashboard.gear(sedan, new Dashboard.Readings(0, 0, new Dashboard.Box(-2, 0, -2), null, Map.of(), 1, true)).text());
        Dashboard.Face mph = Dashboard.of(sedan, new Dashboard.Readings(20, 3000, new Dashboard.Box(3, 0, 1), null, Map.of(), 1, true), true);
        assertEquals(44.74, mph.speed(), 0.01);
        assertEquals("mph", mph.speedUnit());

        CarKit four = CarKit.of(fourByFour());
        Map<String, Double> asked = Map.of("mode", 2.0, "diff_lock", 2.0, "auto_clutch", 1.0);
        Dashboard.Face g = Dashboard.of(four, new Dashboard.Readings(0, 900, new Dashboard.Box(0, 2, 3), new Dashboard.Transfer(1, 1), asked, 0.5, true), false);
        assertEquals("3", g.gear().text());
        assertTrue(g.gear().grinding());
        Dashboard.Lit mode = g.lamps().stream().filter(l -> l.lamp() == Dashboard.Lamp.MODE).findFirst().orElseThrow();
        assertEquals("4L", mode.label());
        assertTrue(mode.blinking(), "4L asked, still in high range");
        assertTrue(g.lamps().stream().anyMatch(l -> l.lamp() == Dashboard.Lamp.FRONT_LOCK) && g.lamps().stream().anyMatch(l -> l.lamp() == Dashboard.Lamp.REAR_LOCK));
        assertTrue(g.lamps().stream().anyMatch(l -> l.lamp() == Dashboard.Lamp.AUTO_CLUTCH));
        assertFalse(Dashboard.of(four, new Dashboard.Readings(0, 900, new Dashboard.Box(1, 0, 1), new Dashboard.Transfer(2, 1), asked, 0.5, true), false)
                .lamps().stream().anyMatch(Dashboard.Lit::blinking), "in low: steady");
    }

    static DriverInputs.Keys indicate(int left, int right, int hazard, boolean steerLeft, boolean steerRight, boolean horn) {
        return new DriverInputs.Keys(steerLeft, steerRight, false, false, false, false, 0, 0, 0, 0, 0, 0, 0, left, right, hazard, horn);
    }

    @Test void indicatorsSwitchAndCancelThemselvesAndTheHornIsHeld() {
        CarKit kit = CarKit.of(SampleBlueprints.sedan());
        DriverInputs in = new DriverInputs();
        in.adopt(kit, Map.of());
        Map<String, Double> out = in.tick(kit, indicate(1, 0, 0, false, false, false), 0, false);
        assertEquals(DriverInputs.INDICATE_LEFT, out.get("indicators"), 1e-9, "left on");
        assertEquals(0, out.get("horn"), 1e-9);
        assertEquals(1, in.tick(kit, indicate(0, 0, 0, false, false, true), 0, false).get("horn"), 1e-9, "the horn while held");
        out = in.tick(kit, indicate(0, 1, 0, false, false, false), 0, false);
        assertEquals(DriverInputs.INDICATE_RIGHT, out.get("indicators"), 1e-9, "the other key switches over");
        out = in.tick(kit, indicate(0, 1, 0, false, false, false), 0, false);
        assertEquals(0, out.get("indicators"), 1e-9, "the same key turns it off");
        // the hazards over an indicator, and back to it
        in.tick(kit, indicate(1, 0, 0, false, false, false), 0, false);
        assertEquals(DriverInputs.HAZARD, in.tick(kit, indicate(0, 0, 1, false, false, false), 0, false).get("indicators"), 1e-9);
        assertEquals(DriverInputs.INDICATE_LEFT, in.tick(kit, indicate(0, 0, 1, false, false, false), 0, false).get("indicators"), 1e-9, "back to left");
        // a left turn: the wheel turns left past the cam, comes back, the indicator goes off
        for (int i = 0; i < 6; i++) { out = in.tick(kit, indicate(0, 0, 0, true, false, false), 0, false); }
        assertEquals(DriverInputs.INDICATE_LEFT, out.get("indicators"), 1e-9, "still on while turning");
        for (int i = 0; i < 10; i++) { out = in.tick(kit, indicate(0, 0, 0, false, false, false), 0, false); }
        assertEquals(0, out.get("indicators"), 1e-9, "cancelled as the wheel came back");
        // turning the other way does not cancel it
        in.tick(kit, indicate(1, 0, 0, false, false, false), 0, false);
        for (int i = 0; i < 6; i++) { in.tick(kit, indicate(0, 0, 0, false, true, false), 0, false); }
        for (int i = 0; i < 10; i++) { out = in.tick(kit, indicate(0, 0, 0, false, false, false), 0, false); }
        assertEquals(DriverInputs.INDICATE_LEFT, out.get("indicators"), 1e-9, "a right swerve leaves the left indicator on");
        // the dashboard's arrows, first in the row
        Function<Double, List<Dashboard.Lamp>> arrows = v -> Dashboard.lamps(kit, new Dashboard.Readings(0, 800, null, null, Map.of("indicators", v), 1, true))
                .stream().map(Dashboard.Lit::lamp).filter(l -> l == Dashboard.Lamp.TURN_LEFT || l == Dashboard.Lamp.TURN_RIGHT).toList();
        assertEquals(List.of(Dashboard.Lamp.TURN_LEFT), arrows.apply(-1.0));
        assertEquals(List.of(Dashboard.Lamp.TURN_RIGHT), arrows.apply(1.0));
        assertEquals(List.of(Dashboard.Lamp.TURN_LEFT, Dashboard.Lamp.TURN_RIGHT), arrows.apply(2.0), "the hazards");
        assertEquals(List.of(), arrows.apply(0.0));
    }
}
