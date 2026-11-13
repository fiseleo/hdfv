package dev.mcmod.hdfv.design;

import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.Gearboxes;
import dev.mcmod.hdfv.part.drive.SampleGearboxes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: what the design screen's gearbox page draws (DriveBench.gearbox) is there for each of the four kinds: a
 * traction line a gear (a CVT's one line) falling from first gear to top under a rising resistance, a run from rest
 * that reaches 100 km/h in a car's time, and a converter's curve and stall speed for the automatic and the CVT only.
 */
class GearboxBenchTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    @Test void everyKindOfGearboxHasItsBench() {
        for (GearboxDesign box : List.of(SampleGearboxes.fiveSpeedManual(), SampleGearboxes.sixSpeedAutomatic(), SampleGearboxes.sevenSpeedDualClutch(), SampleGearboxes.cvt())) {
            DriveBench.GearboxBench b = DriveBench.gearbox(box);
            String name = box.name();
            boolean cvt = box.kind() == Gearboxes.Kind.CVT, converter = cvt || box.kind() == Gearboxes.Kind.AUTOMATIC;
            assertEquals(DriveBench.engineFor(box).name(), b.engine(), name);
            // the traction diagram
            assertEquals(cvt ? 1 : box.forwardGears(), b.traction().gears().size(), name + " lines");
            double before = Double.POSITIVE_INFINITY;
            for (DriveBench.TractionLine line : b.traction().gears()) {
                assertTrue(line.points().size() > 5, name + " gear " + line.gear() + ": " + line.points().size() + " points");
                double peak = DriveBench.peakN(line);
                assertTrue(peak > 500 && peak < before, name + " gear " + line.gear() + " pulls " + peak + " N, less than the gear below");
                before = peak;
            }
            List<double[]> resistance = b.traction().resistance();
            assertEquals(DriveBench.ROLLING * DriveBench.MASS * DriveBench.G, resistance.get(0)[1], 1e-6, name + ": rolling resistance at rest");
            assertTrue(resistance.get(resistance.size() - 1)[1] > 2 * resistance.get(0)[1], name + ": drag at the diagram's top speed");
            // the run from rest
            DriveBench.Run run = b.run();
            assertTrue(run.points().size() > 50, name + ": " + run.points().size() + " points of the run");
            assertTrue(run.to100S() > 3 && run.to100S() < 15, name + ": 0-100 km/h in " + run.to100S() + " s");
            assertTrue(run.quarterS() > run.to100S() * 0.8 && run.quarterS() < 22, name + ": 400 m in " + run.quarterS() + " s");
            assertTrue(run.topMps() > 100 / 3.6, name + ": reached " + run.topMps() + " m/s");
            for (int i = 1; i < run.points().size(); i++) { assertTrue(run.points().get(i).timeS() > run.points().get(i - 1).timeS(), name + ": time runs on"); }
            // the converter
            assertEquals(converter, !b.converter().isEmpty(), name + " converter curve");
            assertEquals(converter, Double.isFinite(b.stallRpm()), name + " stall speed");
            if (converter) {
                assertEquals(box.converter().stallTorqueRatio(), b.converter().get(0).torqueRatio(), 1e-6, name + ": the stall torque ratio at rest");
                assertEquals(0, b.converter().get(0).efficiency(), 1e-12, name + ": no power out at stall");
                double best = b.converter().stream().mapToDouble(DriveBench.ConverterCurvePoint::efficiency).max().orElse(0);
                assertTrue(best > 0.85 && best < 1, name + ": best efficiency " + best);
                assertTrue(b.stallRpm() > 1500 && b.stallRpm() < 3500, name + ": stalls at " + b.stallRpm() + " rpm");
            }
            assertFalse(b.traction().gears().get(0).points().isEmpty());
        }
    }
}
