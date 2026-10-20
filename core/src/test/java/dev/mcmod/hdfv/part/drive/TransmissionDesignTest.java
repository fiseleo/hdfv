package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.DriveBench;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransmissionDesignTest {
    static final double DT = DriveBench.DT;

    @BeforeAll static void register() { HdfvParts.register(); }

    private static List<DesignIssue> invalids(PartDesign d) { return d.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList(); }

    @Test void samplesAreValidReadBackAndCompile() {
        for (PartDesign d : List.of(SampleGearboxes.fiveSpeedManual(), SampleGearboxes.sixSpeedManual(), SampleGearboxes.sixSpeedAutomatic(),
                SampleGearboxes.sevenSpeedDualClutch(), SampleGearboxes.cvt(), SampleGearboxes.partTime(), SampleGearboxes.fullTimeTorsen(), SampleGearboxes.onDemand())) {
            assertTrue(invalids(d).isEmpty(), d.name() + ": " + invalids(d));
            assertEquals(d, Designs.read(Designs.write(d)), d.name());
            d.compile();
        }
        // what each kind asks of the driver
        assertEquals(List.of("gear", "clutch", "auto_clutch", "throttle"), SampleGearboxes.fiveSpeedManual().compile().inputs());   // 0.14: the automatic clutch
        assertEquals(List.of("selector", "shift", "throttle"), SampleGearboxes.sixSpeedAutomatic().compile().inputs());
        assertEquals(List.of("mode"), SampleGearboxes.partTime().compile().inputs());
        assertEquals(List.of("throttle"), SampleGearboxes.onDemand().compile().inputs());
    }

    @Test void planetaryTeethGiveThePublishedRatios() {
        // ZF 6HP26 from its Lepelletier tooth counts
        GearboxDesign zf = SampleGearboxes.sixSpeedAutomatic();
        assertArrayEquals(new double[]{4.171, 2.340, 1.521, 1.143, 0.867, 0.691}, zf.ratios(), 0.0005);
        assertEquals(-3.403, zf.reverseRatio(), 0.0005);
        // a planet needs room: a ring only 20 teeth bigger than its sun is refused
        assertTrue(invalids(Designs.set(zf, "gears", "ringTeeth", "50")).stream().anyMatch(i -> i.parameter().equals("gears.ringTeeth")));
    }

    @Test void clutchesCarryByUniformWearAndAreCheckedAgainstTheRating() {
        // LuK's clutch course: 134/190 mm, 3500 N, mu 0.27 gives 153 N m
        assertEquals(153, new ClutchDesign("c", 2, 0.27, 3500, 0.134, 0.190).capacityNm(), 1);
        GearboxDesign five = SampleGearboxes.fiveSpeedManual();
        assertEquals(306, five.clutch().capacityNm(), 1);
        assertTrue(invalids(Designs.set(five, "clutch", "clampForceN", "4000")).stream().anyMatch(i -> i.parameter().equals("clutch.clampForceN")), "slips at 250 N m");
        // a manual weighs what a five-speed for 250 N m does: 30-40 kg
        assertEquals(35, five.totalMassKg(), 6);
    }

    @Test void anAutomaticShiftsOnItsScheduleAndLaunchesThroughItsConverter() {
        GearboxDesign box = SampleGearboxes.sixSpeedAutomatic();
        PistonEngineDesign v8 = SampleEngines.vEightOhv();
        // a 0.26 m converter behind a 5.7 l V8 stalls at 2000-3000 rpm (stall speeds of such pairings 2000-2600 rpm)
        double stall = DriveBench.stallRpm(box.converter(), v8);
        assertTrue(stall > 2000 && stall < 3000, "stall " + stall + " rpm");
        DriveBench.Rig rig = DriveBench.rig(box, v8, false);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        rig.controls.put("selector", 1.0);
        rig.controls.put("throttle", 1.0);
        List<Double> shiftRpm = new ArrayList<>();
        double gear = 0, to100 = Double.NaN;
        for (int i = 0; i < 25 / DT; i++) {
            double before = rig.rotor("gearbox", "output") * box.ratios()[(int) Math.max(gear, 1) - 1] * 30 / Math.PI;
            rig.step();
            if (rig.gear() > gear && gear >= 1) { shiftRpm.add(before); }
            gear = rig.gear();
            if (Double.isNaN(to100) && rig.speedMps() > 100 / 3.6) { to100 = rig.time; }
        }
        assertTrue(shiftRpm.size() >= 3, "shifts " + shiftRpm);
        for (double r : shiftRpm) { assertEquals(5600, r, 0.05 * 5600, "up-shifts at the full-throttle line: " + shiftRpm); }
        // a 5.7 l V8 in 1400 kg with no wheel spin on the rig (the tyres' limit is the whole car's test): 3-8 s
        assertTrue(to100 - 2 > 3 && to100 - 2 < 8, "0-100 km/h in " + (to100 - 2) + " s");
        // easing off: light throttle at speed locks the converter up
        rig.controls.put("throttle", 0.3);
        for (int i = 0; i < 4 / DT; i++) { rig.step(); }
        assertEquals(rig.rotor("gearbox", "input"), rig.rotor("gearbox", "turbine"), 0.01 * rig.rotor("gearbox", "input"), "locked up");
        // P holds: slowed to a crawl (the rig has no brakes) and put in P, the pawl stops it at once
        rig.controls.put("throttle", 0.0);
        rig.controls.put("selector", 0.0);
        for (int i = 0; i < 0.5 / DT; i++) { rig.step(); }
        var road = rig.vehicle.rotors().get(rig.vehicle.installed("road").firstRotor());
        rig.state.setTrainSpeed(road.train(), 0.2 / DriveBench.TYRE_RADIUS / road.ratio());
        rig.controls.put("selector", -2.0);
        for (int i = 0; i < 0.5 / DT; i++) { rig.step(); }
        assertEquals(0, rig.speedMps(), 0.01, "parked");
    }

    @Test void aDualClutchBoxHandsOverInItsShiftTime() {
        GearboxDesign box = SampleGearboxes.sevenSpeedDualClutch();
        DriveBench.Rig rig = DriveBench.rig(box, SampleEngines.inlineFour(), false);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        // in D with the brake off it creeps
        rig.controls.put("selector", 1.0);
        for (int i = 0; i < 3 / DT; i++) { rig.step(); }
        assertTrue(rig.speedMps() > 0.3 && rig.engineRpm() > 600, "creeping at " + rig.speedMps() + " m/s, " + rig.engineRpm() + " rpm");
        rig.controls.put("throttle", 1.0);
        // a handover starts when the shown gear changes and lasts while the box reports shifting (the freed shaft then
        // synchronises its next gear separately)
        List<Double> handovers = new ArrayList<>();
        double gear = rig.gear(), timing = -1;
        for (int i = 0; i < 20 / DT; i++) {
            rig.step();
            if (rig.gear() > gear && gear >= 1) { timing = 0; }
            gear = rig.gear();
            if (timing >= 0) {
                if (rig.shiftState() > 0.5) { timing += DT; }
                else { handovers.add(timing); timing = -1; }
            }
        }
        assertTrue(handovers.size() >= 3, "handovers " + handovers);
        // each takes the box's 0.25 s (the next gear was ready on the other shaft)
        for (double h : handovers) { assertEquals(0.25, h, 0.03, "handovers " + handovers); }
        assertTrue(rig.gear() >= 4, "in " + rig.gear());
    }

    @Test void aCvtHoldsTheEngineOnItsTarget() {
        GearboxDesign box = SampleGearboxes.cvt();
        DriveBench.Rig rig = DriveBench.rig(box, SampleEngines.inlineFour(), false);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        rig.controls.put("selector", 1.0);
        rig.controls.put("throttle", 0.5);
        double target = box.cvtControl().targetRpm(0.5), worst = 0;
        int counted = 0;
        for (int i = 0; i < 30 / DT; i++) {
            rig.step();
            double ratio = rig.state.shown("gearbox", 2);
            // where the variator is between its stops and has caught up with the run-up
            if (rig.time > 6 && ratio < 0.95 * box.variator().lowRatio() && ratio > 1.05 * box.variator().highRatio()) {
                worst = Math.max(worst, Math.abs(rig.engineRpm() - target) / target);
                counted++;
            }
        }
        assertTrue(counted > 100, "steps between the stops: " + counted);
        assertTrue(worst < 0.05, "engine off its " + target + " rpm target by " + worst);
    }

    @Test void droppingAManualsClutchStallsTheEngineAndTheStarterRestartsIt() {
        DriveBench.Rig rig = DriveBench.rig(SampleGearboxes.fiveSpeedManual(), SampleEngines.inlineFour(), true);
        rig.controls.put("starter", 1.0);
        for (int i = 0; i < 3 / DT && !rig.running(); i++) { rig.step(); }
        rig.controls.put("starter", 0.0);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        assertTrue(rig.running() && rig.engineRpm() > 500, "idling at " + rig.engineRpm());
        // pedal down, first gear, pedal dropped at idle: the car's inertia stalls it
        rig.controls.put("clutch", 1.0);
        rig.controls.put("gear", 1.0);
        for (int i = 0; i < 1 / DT; i++) { rig.step(); }
        assertEquals(1, rig.gear(), 0, "in first");
        rig.controls.put("clutch", 0.0);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        assertFalse(rig.running(), "stalled at " + rig.engineRpm() + " rpm");
        // neutral, key turned: it starts again and idles
        rig.controls.put("gear", 0.0);
        rig.controls.put("starter", 1.0);
        int cranked = 0;
        for (; cranked < 4 / DT && !rig.running(); cranked++) { rig.step(); }
        rig.controls.put("starter", 0.0);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        assertTrue(cranked * DT < 3 && rig.running() && rig.engineRpm() > 500, "restarted after " + cranked * DT + " s, " + rig.engineRpm() + " rpm");
        // and a proper launch (slipping the clutch) moves the car off without stalling
        DriveBench.Run run = DriveBench.accelerate(rig, SampleEngines.inlineFour(), 15);
        assertTrue(run.to100S() > 6 && run.to100S() < 13, "0-100 km/h in " + run.to100S() + " s");
        assertTrue(rig.running(), "still running");
    }

    /** 0.14: a manual's automatic clutch (a driver on a keyboard): the lever alone moves the car off and through the gears. */
    @Test void aManualsAutomaticClutchMovesOffAndShiftsWithoutStalling() {
        DriveBench.Rig rig = DriveBench.rig(SampleGearboxes.fiveSpeedManual(), SampleEngines.inlineFour(), true);
        rig.controls.put("auto_clutch", 1.0);
        rig.controls.put("starter", 1.0);
        for (int i = 0; i < 3 / DT && !rig.running(); i++) { rig.step(); }
        rig.controls.put("starter", 0.0);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        assertTrue(rig.running(), "idling at " + rig.engineRpm());
        // first gear with no throttle: in, and the engine runs on (the clutch was pressed for the shift)
        rig.controls.put("gear", 1.0);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        assertEquals(1, rig.gear(), 0, "in first");
        assertTrue(rig.running(), "not stalled: " + rig.engineRpm() + " rpm");
        // some throttle: it moves off
        rig.controls.put("throttle", 0.5);
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4 / DT; i++) { rig.step(); lowest = Math.min(lowest, rig.engineRpm()); }
        assertTrue(rig.running() && lowest > 600, "moved off without stalling (lowest " + lowest + " rpm)");
        assertTrue(rig.speedMps() > 3, "moving at " + rig.speedMps() + " m/s");
        // the lever alone into second: it shifts in without grinding, the clutch let in after
        rig.controls.put("gear", 2.0);
        double worst = 0;
        for (int i = 0; i < 1.5 / DT; i++) { rig.step(); worst = Math.max(worst, rig.shiftState()); }
        assertEquals(2, rig.gear(), 0, "in second");
        assertTrue(worst < 2, "no grinding");
        var gearbox = rig.vehicle.installed("gearbox");
        double plate = rig.state.rotorSpeed(gearbox.firstRotor()), disc = rig.state.rotorSpeed(gearbox.firstRotor() + 1);
        assertEquals(plate, disc, 0.02 * Math.abs(plate), "the clutch is in");
        // stopping in gear (no throttle, the car slowed by hand): the clutch goes down before the engine stalls
        rig.controls.put("throttle", 0.0);
        var road = rig.vehicle.rotors().get(rig.vehicle.installed("road").firstRotor());
        for (int i = 0; i < 3 / DT; i++) {
            rig.state.setTrainSpeed(road.train(), rig.state.trainSpeed(road.train()) * 0.97);
            rig.step();
        }
        assertTrue(rig.running(), "still running at a standstill in second: " + rig.engineRpm() + " rpm");
    }

    /** 0.14: an automatic in M shifts once for each press of "shift" (the paddles), up and down. */
    @Test void anAutomaticInManualModeShiftsOnThePresses() {
        DriveBench.Rig rig = DriveBench.rig(SampleGearboxes.sixSpeedAutomatic(), SampleEngines.vEightOhv(), false);
        for (int i = 0; i < 2 / DT; i++) { rig.step(); }
        rig.controls.put("selector", 2.0);
        rig.controls.put("throttle", 0.25);
        for (int i = 0; i < 3 / DT; i++) { rig.step(); }
        assertEquals(1, rig.gear(), 0, "M holds first");
        // a press held a whole second is one shift
        rig.controls.put("shift", 1.0);
        for (int i = 0; i < 1 / DT; i++) { rig.step(); }
        rig.controls.put("shift", 0.0);
        for (int i = 0; i < 1 / DT; i++) { rig.step(); }
        assertEquals(2, rig.gear(), 0, "one up");
        for (int k = 0; k < 2; k++) {
            rig.controls.put("shift", 1.0);
            for (int i = 0; i < 0.1 / DT; i++) { rig.step(); }
            rig.controls.put("shift", 0.0);
            for (int i = 0; i < 1 / DT; i++) { rig.step(); }
        }
        assertEquals(4, rig.gear(), 0, "two more presses, two more gears");
        rig.controls.put("shift", -1.0);
        for (int i = 0; i < 0.1 / DT; i++) { rig.step(); }
        rig.controls.put("shift", 0.0);
        for (int i = 0; i < 1 / DT; i++) { rig.step(); }
        assertEquals(3, rig.gear(), 0, "one down");
    }
}
