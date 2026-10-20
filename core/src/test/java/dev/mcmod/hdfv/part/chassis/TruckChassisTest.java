package dev.mcmod.hdfv.part.chassis;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.look.ChassisLayout;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 0.18: a tractor's fifth wheel, a semi-trailer's kingpin, landing legs and gooseneck on the chassis. */
class TruckChassisTest {
    static { HdfvParts.register(); }

    static List<DesignIssue> invalid(ChassisDesign d) { return d.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList(); }
    static void at(Vec3d expected, Mount m, String what) { assertEquals(0, m.position().subtract(expected).length(), 1e-9, what + ": " + m.position()); }

    @Test void aTractorCarriesItsFifthWheelAndItsCab() {
        ChassisDesign d = SampleChassis.tractor6x4();
        assertEquals(List.of(), invalid(d));
        Map<String, Mount> m = d.mountPoints();
        at(new Vec3d(1.875, 0.3 + 0.03, 0), m.get("fifth_wheel"), "the fifth wheel's base on its plate over the rails");
        at(new Vec3d(5.9, 0.3, 0), m.get("cab"), "the cab on the rails at the front");
        assertTrue(m.containsKey("axle_1") && m.containsKey("axle_2") && m.containsKey("axle_3") && !m.containsKey("axle_4"));
        assertEquals(5.175, m.get("axle_1").position().x(), 1e-9, "the front axle first");
        ChassisDesign.Analysis a = d.analysis();
        assertNull(a.failure());
        assertTrue(a.usage() > 0 && a.usage() < 1, "standard cases: " + a.usage());
    }

    @Test void aLowLoaderStandsOnItsKingpinAndHasItsNeckAndLegs() {
        ChassisDesign d = SampleChassis.lowLoader3();
        assertEquals(List.of(), invalid(d));
        ChassisSettingsDesign f = d.frame();
        assertEquals(0.45, d.railTopAt(5), 1e-9, "the deck's rails");
        assertEquals(0.45 + 0.6, d.railTopAt(12), 1e-9, "the gooseneck 0.6 m higher");
        assertEquals(0.3, ChassisLayout.lift(f, 10.2), 1e-9, "half way up the step");
        Map<String, Mount> m = d.mountPoints();
        at(new Vec3d(12.7, 0.6 - 0.08, 0), m.get("kingpin"), "the kingpin's plate 8 cm under the neck");
        double half = 0.45 + ChassisDesign.LEG_OUTSIDE_M;
        at(new Vec3d(11.2, 0.5, -half), m.get("legs_1"), "the port leg");
        at(new Vec3d(11.2, 0.5, half), m.get("legs_2"), "the starboard leg");
        ChassisDesign.Analysis a = d.analysis();
        assertNull(a.failure());
        assertTrue(a.usage() > 0 && a.usage() < 1, "on its axles and kingpin: " + a.usage());
        // without the kingpin the frame cannot stand on its tridem alone (it would tip onto its nose)
        List<ChassisStationDesign> stations = new ArrayList<>(d.stations());
        for (int i = 0; i < stations.size(); i++) {
            ChassisStationDesign s = stations.get(i);
            if (s.kind() == ChassisStationDesign.Kind.KINGPIN) {
                stations.set(i, new ChassisStationDesign(s.name(), ChassisStationDesign.Kind.CROSSMEMBER, s.xM(), 0, s.crossSection(), s.crossHeightM(),
                        s.crossWidthM(), s.crossWallM(), s.brace(), s.bayHeightM(), s.bayTopWidthM(), s.openTop()));
            }
        }
        ChassisDesign unpinned = new ChassisDesign(d.name(), f, stations);
        assertNotNull(unpinned.analysis().failure());
        assertTrue(invalid(unpinned).stream().anyMatch(i -> i.message().contains("does not hold together")), unpinned.check().toString());
        // the neck in its JSON, read back the same
        String json = Designs.write(d);
        assertTrue(json.contains("neckM") && json.contains("neckX"));
        assertEquals(d, Designs.read(json));
    }

    @Test void framesWithoutANeckWriteAsBefore() {
        for (ChassisDesign d : List.of(SampleChassis.ladderPickup(), SampleChassis.ladderTruck(), SampleChassis.backbone(), SampleChassis.tubeFrame(), SampleChassis.tractor6x4())) {
            String json = Designs.write(d);
            assertFalse(json.contains("neckM"), d.name());
            assertEquals(d, Designs.read(json));
        }
    }
}
