package dev.mcmod.hdfv.part.chassis;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.part.FramePart;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.SampleBodies;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The chassis part (0.15): samples like real frames, its stations' editing, what it compiles to, a unibody's twist. */
class ChassisTest {
    static { HdfvParts.register(); }

    @Test void samplesWeighAndTwistLikeRealFrames() {
        // a mid-size pickup's ladder frame weighs 180-260 kg and twists 3-8 kN m per degree; a backbone ~100 kg
        ChassisDesign pickup = SampleChassis.ladderPickup();
        assertEquals(220, pickup.massKg(), 40);
        assertEquals(5.5, pickup.analysis().endTwistNmPerDeg() / 1000, 2.5);
        assertTrue(SampleChassis.backbone().analysis().endTwistNmPerDeg() > pickup.analysis().endTwistNmPerDeg(), "a closed backbone is stiffer");
        for (ChassisDesign d : List.of(pickup, SampleChassis.ladderTruck(), SampleChassis.backbone(), SampleChassis.tubeFrame())) {
            assertTrue(d.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), d.name() + ": " + d.check());
            assertEquals(d, Designs.read(Designs.write(d)), d.name());
            assertTrue(d.analysis().usage() > 0.05 && d.analysis().usage() <= 1, d.name() + " usage " + d.analysis().usage());
        }
    }

    @Test void itCompilesToATwistingFrameWithItsMounts() {
        ChassisDesign truck = SampleChassis.ladderTruck();
        FramePart p = (FramePart) truck.compile();
        assertEquals(3, p.frame().stations(), "one station an axle");
        assertEquals(java.util.Set.of("axle_1", "axle_2", "axle_3", "engine_bay", "body", "cab"), p.mountPoints().keySet());
        assertTrue(p.mountPoints().get("axle_1").position().x() > p.mountPoints().get("axle_2").position().x(), "axle_1 is the frontmost");
        assertEquals(truck.massKg(), p.mass().massKg(), 1e-6);
        assertEquals(3, p.internalStates());
        // its twist matrix: rows summing to nothing, the end axles' series stiffness as the figure says
        double[][] k = p.frame().twistNmPerRad();
        for (double[] row : k) { double s = 0; for (double v : row) { s += v; } assertEquals(0, s, 1e-6 * Math.abs(k[0][0])); }
        assertEquals(truck.analysis().endTwistNmPerDeg(), Math.toRadians(1) / p.frame().endToEndFlexibility(), 1e-6 * truck.analysis().endTwistNmPerDeg());
    }

    @Test void anXMemberStiffensALadder() {
        ChassisDesign braced = SampleChassis.ladderPickup();
        int x = -1;
        for (int i = 0; i < braced.stations().size(); i++) { if (braced.stations().get(i).brace() == ChassisStationDesign.Brace.X) { x = i; } }
        ChassisStationDesign s = braced.stations().get(x);
        ChassisDesign plain = braced.withStation(x, new ChassisStationDesign(s.name(), s.kind(), s.xM(), s.heightM(), s.crossSection(), s.crossHeightM(), s.crossWidthM(),
                s.crossWallM(), ChassisStationDesign.Brace.NONE, s.bayHeightM(), s.bayTopWidthM(), s.openTop()));
        assertTrue(braced.analysis().endTwistNmPerDeg() > 1.2 * plain.analysis().endTwistNmPerDeg(),
                braced.analysis().endTwistNmPerDeg() + " against " + plain.analysis().endTwistNmPerDeg());
        assertTrue(braced.massKg() > plain.massKg());
    }

    @Test void stationsAreAddedMovedCopiedAndRemoved() {
        ChassisDesign d = SampleChassis.backbone();
        ChassisDesign added = d.added(0, ChassisStationDesign.Kind.CROSSMEMBER);
        assertEquals(d.items() + 1, added.items());
        double between = 0.5 * (d.stations().get(0).xM() + d.stations().get(1).xM());
        assertEquals(between, added.stations().get(1).xM(), 1e-12, "halfway to the next one along");
        assertEquals("station2", added.listPage(1));
        assertEquals(1, added.listIndex("station2"));
        assertEquals(d.stations().get(0), d.moved(0, 1).stations().get(1));
        assertEquals(d.stations().get(0).xM() + 0.3, d.duplicated(0).stations().get(1).xM(), 1e-12);
        ChassisDesign two = d.removed(1).removed(1);
        assertEquals(2, two.items());
        assertEquals(two, two.removed(0), "it keeps two stations");
        // fewer than two axles is no chassis
        assertTrue(d.removed(0).check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID));
    }

    @Test void aUnibodyTwistsAndABodyOnAFrameDoesNot() {
        var sedan = SampleBodies.sedan();
        double perDeg = sedan.twistNmPerDeg() / 1000;
        assertTrue(perDeg > 10 && perDeg < 40, "a saloon's shell: " + perDeg + " kN m per degree");
        assertNotNull(sedan.twist(sedan.shape()));
        var onFrame = sedan.withSettings(dev.mcmod.hdfv.design.RecordEditor.with(sedan.settings(), "onFrame", true));
        assertNull(onFrame.twist(onFrame.shape()));
        assertNull(SampleBodies.seaplaneFuselage().twist(SampleBodies.seaplaneFuselage().shape()), "aircraft stay rigid");
    }
}
