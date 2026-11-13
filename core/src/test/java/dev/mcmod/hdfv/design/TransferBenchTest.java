package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.drive.SampleGearboxes;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: the transfer case's bench (DriveBench.transferCase): the rig's split (AeroCore's TransferPart between a torque
 * and two dynamometers) against what each mechanism gives by hand, and the tractive force in high and low range.
 */
class TransferBenchTest {
    static final double T = 500, EFF = 0.97;

    @BeforeAll static void register() { HdfvParts.register(); }

    private static TransferCaseDesign fullTime(TransferPart.Centre centre, double frontShare, boolean lockable) {
        return new TransferCaseDesign("full-time", TransferPart.Kind.FULL_TIME, 1, EFF, frontShare, centre, 3, 40, lockable, true, 15, 1200, 0.3, 1500, 600, false, Double.NaN);
    }

    private static List<DriveBench.SplitPoint> split(TransferCaseDesign d, Map<String, Double> controls) { return DriveBench.split(d, T, controls, DriveBench.SPLIT_SPEED); }

    @Test void theSweepCoversBothSignsAndLeavesOutZero() {
        List<DriveBench.SplitPoint> points = split(fullTime(TransferPart.Centre.OPEN, 0.4, false), Map.of());
        assertEquals(2 * DriveBench.SPLIT_POINTS, points.size());
        assertEquals(-DriveBench.SPLIT_DIFFERENCE + 1.5, points.get(0).differenceRadPerS(), 1e-9);
        assertEquals(DriveBench.SPLIT_DIFFERENCE - 1.5, points.get(points.size() - 1).differenceRadPerS(), 1e-9);
        for (DriveBench.SplitPoint p : points) { assertTrue(Math.abs(p.differenceRadPerS()) > 1, "no point at zero difference"); }
    }

    /** An open centre differential divides by its gearing whatever the speeds: 40 % front, the case's efficiency lost. */
    @Test void anOpenCentreDifferentialSplitsByItsGearing() {
        for (DriveBench.SplitPoint p : split(fullTime(TransferPart.Centre.OPEN, 0.4, false), Map.of())) {
            assertEquals(0.4 * T * EFF, p.frontNm(), 0.5, "front at " + p.differenceRadPerS());
            assertEquals(0.6 * T * EFF, p.rearNm(), 0.5, "rear at " + p.differenceRadPerS());
            assertEquals(0.4, p.frontShare(), 0.001);
        }
    }

    /** A Torsen sends its bias to the slower shaft: an even one of bias ratio 3 gives it three times the faster shaft's torque. */
    @Test void aTorsenBiasesToTheSlowerShaft() {
        for (DriveBench.SplitPoint p : split(fullTime(TransferPart.Centre.TORSEN, 0.5, false), Map.of())) {
            double slow = p.differenceRadPerS() > 0 ? p.rearNm() : p.frontNm(), fast = p.differenceRadPerS() > 0 ? p.frontNm() : p.rearNm();
            assertEquals(3, slow / fast, 0.03, "bias at " + p.differenceRadPerS());
            assertEquals(T * EFF, slow + fast, 0.5, "nothing lost but the case's efficiency");
        }
        // the 40/60 sample: its friction (bias 3: a quarter of the input) moves the split by 25 points either way
        for (DriveBench.SplitPoint p : split(SampleGearboxes.fullTimeTorsen(), Map.of())) {
            assertEquals(p.differenceRadPerS() > 0 ? 0.15 : 0.65, p.frontShare(), 0.01, "front share at " + p.differenceRadPerS());
        }
    }

    /** A viscous centre moves its coefficient times the speed difference from the faster shaft to the slower. */
    @Test void aViscousCentreTransfersWithTheSpeedDifference() {
        for (DriveBench.SplitPoint p : split(fullTime(TransferPart.Centre.VISCOUS, 0.4, false), Map.of())) {
            assertEquals(0.4 * T * EFF - 40 * p.differenceRadPerS(), p.frontNm(), 1, "front at " + p.differenceRadPerS());
            assertEquals(0.6 * T * EFF + 40 * p.differenceRadPerS(), p.rearNm(), 1, "rear at " + p.differenceRadPerS());
        }
    }

    /** A part-time case in two-wheel drive leaves the front shaft free. */
    @Test void twoWheelHighDrivesTheRearOnly() {
        for (DriveBench.SplitPoint p : split(SampleGearboxes.partTime(), Map.of("mode", 0.0))) {
            assertEquals(0, p.frontNm(), 0.5, "front at " + p.differenceRadPerS());
            assertEquals(T * EFF, p.rearNm(), 0.5, "rear at " + p.differenceRadPerS());
        }
    }

    /**
     * An on-demand coupling (front primary, 15 N m per rad/s to 1200 N m): on slip alone the rear gets the slope times
     * the front's lead (and drags when it leads); preloaded with 30 % of its largest torque at full throttle below the
     * rule's shaft speed, it carries 360 N m until the slip's own torque passes that.
     */
    @Test void anOnDemandCouplingCarriesItsSlipAndItsPreload() {
        TransferCaseDesign d = SampleGearboxes.onDemand();
        for (DriveBench.SplitPoint p : split(d, Map.of("throttle", 0.0))) {
            assertEquals(15 * p.differenceRadPerS(), p.rearNm(), 1, "rear at " + p.differenceRadPerS());
            assertEquals(T * EFF, p.frontNm() + p.rearNm(), 0.5, "the two shafts share the input");
        }
        double reference = 0.5 * d.preloadBelowRpm() * Math.PI / 30;
        for (DriveBench.SplitPoint p : DriveBench.split(d, T, Map.of("throttle", 1.0), reference)) {
            double carried = Math.max(0.3 * 1200, 15 * Math.abs(p.differenceRadPerS()));
            assertEquals(Math.signum(p.differenceRadPerS()) * carried, p.rearNm(), 1, "rear at " + p.differenceRadPerS());
        }
        // above the rule's shaft speed there is no preload, whatever the throttle
        DriveBench.TransferRig rig = DriveBench.transferRig(d, T);
        double fast = 1.2 * d.preloadBelowRpm() * Math.PI / 30;
        rig.controls.putAll(Map.of("throttle", 1.0, "front_speed", fast, "rear_speed", fast - 3));
        for (int i = 0; i < 40; i++) { rig.step(); }
        assertEquals(45, rig.torque("rear"), 1, "slip alone");
    }

    @Test void theBenchListsEachKindsModesAndRanges() {
        // part-time (the 600 N m sample behind the V8 and the six-speed): two-wheel high on the rig, four-wheel high locked
        DriveBench.TransferBench part = DriveBench.transferCase(SampleGearboxes.partTime());
        assertEquals(List.of("two_high", "four_high"), part.splits().stream().map(DriveBench.Split::mode).toList());
        assertEquals("six-speed manual", part.gearbox());
        assertTrue(part.inputNm() > 800 && part.inputNm() < 2000, "the V8's peak torque in first gear: " + part.inputNm());
        DriveBench.Split locked = part.splits().get(1);
        assertTrue(locked.locked());
        for (DriveBench.SplitPoint p : locked.points()) {
            // geared together: all of it to the axle that would be left behind
            assertEquals(p.differenceRadPerS() < 0 ? part.inputNm() * 0.97 : 0, p.frontNm(), 1e-6);
            assertEquals(p.differenceRadPerS() < 0 ? 0 : part.inputNm() * 0.97, p.rearNm(), 1e-6);
        }
        // the low range multiplies the force by its ratio, and ends where top gear reaches the rev limit (the diagram's
        // high range stops at its 100 m/s edge before the sixth gear does)
        assertNotNull(part.low());
        double high = DriveBench.peakN(part.high()), low = DriveBench.peakN(part.low());
        assertEquals(2.72, low / high, 0.05, "low over high: " + low + " / " + high);
        double topHigh = part.high().points().get(part.high().points().size() - 1)[0], topLow = part.low().points().get(part.low().points().size() - 1)[0];
        double limit = dev.mcmod.hdfv.part.engine.SampleEngines.vEightOhv().ignition().revLimitRpm() * Math.PI / 30;
        assertEquals(limit / (0.50 * 2.72 * DriveBench.FINAL_RATIO) * DriveBench.TYRE_RADIUS, topLow, 1.5, "low range's top speed, m/s");
        assertTrue(topHigh > 2 * topLow, "top speeds " + topHigh + " and " + topLow);
        assertEquals(2.66 * 2.72 * DriveBench.FINAL_RATIO, part.crawlRatio(), 1e-9);
        // first gear's peak force by hand: the input torque through the case and the final drive at the tyre
        assertEquals(part.inputNm() * 0.97 * DriveBench.FINAL_RATIO * 0.96 / DriveBench.TYRE_RADIUS, high, 0.02 * high);
        for (int i = 1; i < part.high().points().size(); i++) { assertTrue(part.high().points().get(i)[0] > part.high().points().get(i - 1)[0], "speeds rise"); }
        assertFalse(part.resistance().isEmpty());

        // full-time Torsen without a low range or a lock
        DriveBench.TransferBench full = DriveBench.transferCase(SampleGearboxes.fullTimeTorsen());
        assertEquals(List.of("torsen"), full.splits().stream().map(DriveBench.Split::mode).toList());
        assertNull(full.low());
        assertEquals(List.of("open", "locked"), DriveBench.transferCase(fullTime(TransferPart.Centre.OPEN, 0.4, true)).splits().stream().map(DriveBench.Split::mode).toList());

        // on-demand (the 300 N m sample behind the four and the five-speed): on slip alone, and preloaded
        DriveBench.TransferBench demand = DriveBench.transferCase(SampleGearboxes.onDemand());
        assertEquals(List.of("slip", "preload"), demand.splits().stream().map(DriveBench.Split::mode).toList());
        assertEquals("five-speed manual", demand.gearbox());
        assertNull(demand.low());
    }
}
