package dev.mcmod.hdfv.drive;

import dev.mcmod.hdfv.drive.WorkInputs.Function;
import dev.mcmod.hdfv.drive.WorkInputs.Kit;
import dev.mcmod.hdfv.drive.WorkInputs.Layout;
import dev.mcmod.hdfv.drive.WorkInputs.Pattern;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 0.26: a machine operator's work keys (WorkInputs): the layouts, ISO and SAE, the valves opening, auto-idle, travel. */
class WorkInputsTest {
    static Kit kit(boolean travel, boolean floats, Function... f) { return new Kit(new LinkedHashSet<>(List.of(f)), travel, floats); }

    static final Kit EXCAVATOR = kit(true, false, Function.BOOM, Function.STICK, Function.BUCKET, Function.SWING, Function.BLADE_LIFT);
    static final Kit DOZER = kit(false, true, Function.BLADE_LIFT, Function.BLADE_TILT, Function.RIPPER);
    static final Kit CRANE = kit(false, false, Function.BOOM, Function.SWING, Function.HOIST, Function.TELESCOPE, Function.OUTRIGGERS);

    static WorkInputs.Keys keys(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down, boolean rl, boolean rr) {
        return new WorkInputs.Keys(forward, back, left, right, up, down, rl, rr, false, false, 0, 0, 0);
    }

    static final WorkInputs.Keys MODE = new WorkInputs.Keys(false, false, false, false, false, false, false, false, false, false, 0, 1, 0);

    @Test void anExcavatorsSticksInIsoAndSae() {
        Layout iso = Layout.of(EXCAVATOR, Pattern.ISO), sae = Layout.of(EXCAVATOR, Pattern.SAE);
        assertEquals(Function.STICK, iso.leftY().function(), "ISO: the stick on the left stick");
        assertEquals(Function.BOOM, iso.rightY().function(), "ISO: the boom on the right");
        assertEquals(Function.BOOM, sae.leftY().function(), "SAE: the boom on the left");
        assertEquals(Function.STICK, sae.rightY().function());
        assertEquals(Function.SWING, iso.leftX().function());
        assertEquals(Function.BUCKET, iso.rightX().function());
        assertEquals(List.of(Function.BLADE_LIFT), iso.selectable(), "the dozer blade is picked with the select key");

        WorkInputs in = new WorkInputs();
        in.adopt(EXCAVATOR, Map.of());
        in.tick(EXCAVATOR, MODE, Pattern.ISO);
        assertTrue(in.working());
        // right stick back raises the boom (+), left stick forward puts the stick out (-), right on the right stick dumps (-), left on the left swings left (+)
        Map<String, Double> out = in.tick(EXCAVATOR, keys(true, false, true, false, false, true, false, true), Pattern.ISO);
        assertTrue(out.get("boom") > 0 && out.get("stick") < 0 && out.get("bucket") < 0 && out.get("swing") > 0, out.toString());
        out = in.tick(EXCAVATOR, keys(true, false, false, false, false, false, false, false), Pattern.SAE);
        assertTrue(out.get("boom") < 0 && out.get("stick") == 0, "SAE: left stick forward lowers the boom: " + out);
    }

    @Test void theValveOpensTheLongerItIsHeldAndClosesWhenLetGo() {
        WorkInputs in = new WorkInputs();
        in.adopt(EXCAVATOR, Map.of());
        in.tick(EXCAVATOR, MODE, Pattern.ISO);
        WorkInputs.Keys boomUp = keys(false, false, false, false, false, true, false, false);
        double first = in.tick(EXCAVATOR, boomUp, Pattern.ISO).get("boom");
        assertEquals(WorkInputs.OPEN_START, first, 1e-9, "a tap opens it a little");
        double last = first;
        for (int i = 0; i < 10; i++) {
            double v = in.tick(EXCAVATOR, boomUp, Pattern.ISO).get("boom");
            assertTrue(v > last || v == 1, "it keeps opening: " + v);
            last = v;
        }
        for (int i = 0; i < 20; i++) { last = in.tick(EXCAVATOR, boomUp, Pattern.ISO).get("boom"); }
        assertEquals(1, last, 1e-9, "fully open after about a second");
        assertEquals(0, in.tick(EXCAVATOR, WorkInputs.Keys.NONE, Pattern.ISO).get("boom"), 1e-12, "sprung back to the middle");
        double reversed = in.tick(EXCAVATOR, keys(false, false, false, false, true, false, false, false), Pattern.ISO).get("boom");
        assertEquals(-WorkInputs.OPEN_START, reversed, 1e-9);
    }

    @Test void driveModeTravelsTheTracksAndWorkModeHoldsThem() {
        WorkInputs in = new WorkInputs();
        in.adopt(EXCAVATOR, Map.of());
        Map<String, Double> out = in.tick(EXCAVATOR, keys(true, false, false, false, false, false, false, false), Pattern.ISO);
        assertTrue(out.get("travel_left") > 0 && out.get("travel_right") > 0 && out.get("travel_brake") == 0, out.toString());
        assertEquals(0, out.get("boom"), "in drive mode the work functions are still");
        out = in.tick(EXCAVATOR, keys(false, false, true, false, false, false, false, false), Pattern.ISO);
        assertTrue(out.get("travel_left") < 0 && out.get("travel_right") > 0, "A alone counter-rotates (a pivot turn left): " + out);
        out = in.tick(EXCAVATOR, keys(true, false, true, false, false, false, false, false), Pattern.ISO);
        assertTrue(out.get("travel_left") == 0 && out.get("travel_right") > 0, "W and A: the left track stops (a gradual turn): " + out);
        in.tick(EXCAVATOR, MODE, Pattern.ISO);
        out = in.tick(EXCAVATOR, keys(true, false, false, false, false, false, false, false), Pattern.ISO);
        assertEquals(0, out.get("travel_left"), 1e-12, "in work mode W is the stick, not travel");
        assertEquals(1, out.get("travel_brake"), 1e-12);
        assertTrue(in.ownsMovementKeys(EXCAVATOR));
    }

    @Test void theEngineIdlesWhenNothingMovesForFourSeconds() {
        WorkInputs in = new WorkInputs();
        in.adopt(EXCAVATOR, Map.of());
        assertEquals(0, in.tick(EXCAVATOR, WorkInputs.Keys.NONE, Pattern.ISO).get("throttle"), 1e-12, "a machine got into idles");
        assertEquals(1, in.tick(EXCAVATOR, keys(true, false, false, false, false, false, false, false), Pattern.ISO).get("throttle"), 1e-12);
        int ticks = 0;
        while (in.tick(EXCAVATOR, WorkInputs.Keys.NONE, Pattern.ISO).get("throttle") > 0.5) { ticks++; }
        assertEquals(WorkInputs.AUTO_IDLE_S / WorkInputs.TICK, ticks, 1.5, "auto-idle after 4 s");
        assertFalse(DOZER.autoIdle(), "a dozer's throttle stays the pedal's");
        assertNull(new WorkInputs().tick(DOZER, WorkInputs.Keys.NONE, Pattern.ISO).get("throttle"));
    }

    @Test void aDozerDrivesOnTheLeftStickAndWorksTheBladeOnTheRight() {
        Layout l = Layout.of(DOZER, Pattern.ISO);
        assertNull(l.leftX());
        assertNull(l.leftY());
        assertEquals(Function.BLADE_LIFT, l.rightY().function());
        assertEquals(Function.BLADE_TILT, l.rightX().function());
        assertEquals(List.of(Function.RIPPER), l.selectable());
        WorkInputs in = new WorkInputs();
        in.adopt(DOZER, Map.of());
        in.tick(DOZER, MODE, Pattern.ISO);
        assertFalse(in.ownsMovementKeys(DOZER), "AeroCore's throttle and the car keys still drive it");
        // the ripper on R and F, the float on space
        Map<String, Double> out = in.tick(DOZER, new WorkInputs.Keys(false, false, false, false, false, false, false, false, true, false, 0, 0, 1), Pattern.ISO);
        assertTrue(out.get("ripper") > 0, out.toString());
        assertEquals(1, out.get("float"), 1e-12);
        DriverInputs.Message m = in.message();
        assertEquals("hdfv.work.float_on", m.key());
    }

    @Test void aCraneHoistsOnTheRightStickAndTelescopesOnTheLeft() {
        Layout l = Layout.of(CRANE, Pattern.ISO);
        assertEquals(Function.SWING, l.leftX().function());
        assertEquals(Function.TELESCOPE, l.leftY().function());
        assertEquals(Function.HOIST, l.rightY().function());
        assertEquals(Function.BOOM, l.rightX().function());
        assertEquals(List.of(Function.OUTRIGGERS), l.selectable());
        WorkInputs in = new WorkInputs();
        in.adopt(CRANE, Map.of());
        in.tick(CRANE, MODE, Pattern.ISO);
        Map<String, Double> out = in.tick(CRANE, keys(false, false, false, false, false, true, true, false), Pattern.ISO);
        assertTrue(out.get("hoist") > 0 && out.get("boom") > 0, "back hoists up, left luffs up: " + out);
    }

    @Test void togglesAreSwitchedOnAndOffWithTheSelectedFunction() {
        Kit planer = kit(false, false, Function.CUTTER, Function.CONVEYOR, Function.SCREED);
        Layout l = Layout.of(planer, Pattern.ISO);
        assertEquals(Function.SCREED, l.rightY().function(), "the drum's height on the right stick");
        assertEquals(List.of(Function.CUTTER, Function.CONVEYOR), l.selectable());
        WorkInputs in = new WorkInputs();
        in.adopt(planer, Map.of("conveyor", 1.0));
        in.tick(planer, MODE, Pattern.ISO);
        WorkInputs.Keys on = new WorkInputs.Keys(false, false, false, false, false, false, false, false, true, false, 0, 0, 0);
        Map<String, Double> out = in.tick(planer, on, Pattern.ISO);
        assertEquals(1, out.get("cutter"), 1e-12, "R switches the drum on");
        assertEquals(1, out.get("conveyor"), 1e-12, "the conveyor as the server had it");
        in.tick(planer, new WorkInputs.Keys(false, false, false, false, false, false, false, false, false, false, 1, 0, 0), Pattern.ISO);
        assertEquals(Function.CONVEYOR, in.selected(planer, Pattern.ISO));
        out = in.tick(planer, new WorkInputs.Keys(false, false, false, false, false, false, false, false, false, true, 0, 0, 0), Pattern.ISO);
        assertEquals(0, out.get("conveyor"), 1e-12, "F switches the picked one off");
        assertEquals(1, out.get("cutter"), 1e-12, "the other stays on");
    }

    @Test void aVehicleWithNoWorkChannelsIsNotAMachine() {
        assertFalse(Kit.NONE.machine());
        assertTrue(new WorkInputs().tick(Kit.NONE, MODE, Pattern.ISO).isEmpty());
        assertTrue(Kit.of(dev.mcmod.hdfv.part.SampleBlueprints.sedan()).functions().isEmpty());
        Set<Function> all = Set.of(Function.values());
        for (Function f : all) { assertSame(f, Function.of(f.channel)); }
    }
}
