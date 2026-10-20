package dev.mcmod.hdfv.airport;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.light.FieldLamp;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Airfield lights, ATIS, units and the tower's ranges and power. */
class AirfieldTest {
    /** Runway 09/27: 1500 m along X from x 0, 30 m wide, the centre line at z 0, the surface at y 64. */
    private static final Runway RUNWAY = new Runway(Runway.Axis.X, 0, 0, 1500, 64, 30, "");

    private static Vec3d eye(double x, double y, double z) { return new Vec3d(x, y, z); }

    /** The colour a lamp shows to an eye at a point (0 when its beam does not reach there). */
    private static int seen(List<FieldLamp> lamps, Vec3d eye) {
        for (FieldLamp l : lamps) {
            Vec3d to = eye.subtract(l.at());
            if (l.coverage(to) > 0.99) { return l.colour(to, 0); }
        }
        return 0;
    }

    @Test void edgeLightsTurnYellowTowardTheEndAhead() {
        // an edge light 300 m from the east end: yellow to traffic heading east (toward that end), white heading west
        List<FieldLamp> lamps = AirfieldLighting.lamps(List.of(RUNWAY), List.of(new Fixture(Fixture.Kind.RUNWAY_EDGE, 1200, 64, 15)));
        assertEquals(2, lamps.size());
        assertEquals(AirfieldLighting.YELLOW, seen(lamps, eye(900, 66, 0)), "from the west, rolling east");
        assertEquals(AirfieldLighting.WHITE, seen(lamps, eye(1400, 66, 0)), "from the east, rolling west");
        // one in the middle: white both ways
        lamps = AirfieldLighting.lamps(List.of(RUNWAY), List.of(new Fixture(Fixture.Kind.RUNWAY_EDGE, 750, 64, -16)));
        assertEquals(AirfieldLighting.WHITE, seen(lamps, eye(500, 66, 0)));
        assertEquals(AirfieldLighting.WHITE, seen(lamps, eye(1000, 66, 0)));
    }

    @Test void thresholdLightsAreGreenToTheApproachAndRedToTheRunway() {
        List<FieldLamp> lamps = AirfieldLighting.lamps(List.of(RUNWAY), List.of(new Fixture(Fixture.Kind.THRESHOLD, 0, 64, 5)));
        assertEquals(AirfieldLighting.GREEN, seen(lamps, eye(-2000, 160, 0)), "on the approach from the west");
        assertEquals(AirfieldLighting.RED, seen(lamps, eye(800, 66, 0)), "rolling west toward the end");
    }

    @Test void centreLineLightsColourTheLastKilometre() {
        List<Fixture> f = new ArrayList<>();
        for (int x = 15; x < 1500; x += 15) { f.add(new Fixture(Fixture.Kind.RUNWAY_CENTRE, x, 64, 0)); }
        List<FieldLamp> lamps = AirfieldLighting.lamps(List.of(RUNWAY), f);
        // the colours seen heading east, by distance from the east end
        int red = 0, white = 0;
        for (FieldLamp l : lamps) {
            if (l.axis().x() > 0) { continue; }   // the halves seen by east-bound traffic face west
            double left = 1500 - l.at().x();
            if (left < 300) { assertEquals(AirfieldLighting.RED, l.argb(), "the last 300 m red"); red++; }
            else if (left >= 900) { assertEquals(AirfieldLighting.WHITE, l.argb()); white++; }
        }
        assertTrue(red >= 19 && white >= 39, red + " red, " + white + " white");
    }

    @Test void aPapiOnTheLeftShowsTheGlidePath() {
        // four units 300 m in, left of a landing toward the east (north of the runway: smaller z), 10 m apart
        List<Fixture> units = new ArrayList<>();
        for (int i = 0; i < 4; i++) { units.add(new Fixture(Fixture.Kind.PAPI, 300, 64, -25 - 9 * i)); }
        List<FieldLamp> lamps = AirfieldLighting.lamps(List.of(RUNWAY), units);
        assertEquals(4, lamps.size());
        double[] settings = AirfieldLighting.papiSettingsDeg(4);
        assertEquals(3.5, settings[0], 1e-12);
        assertEquals(2.5, settings[3], 1e-12);
        for (double angle : new double[]{2.0, 2.7, 3.0, 3.3, 4.0}) {
            int whites = 0, reds = 0;
            Vec3d nearest = null;
            for (FieldLamp l : lamps) {
                double d = 1500;
                Vec3d e = eye(l.at().x() - d, l.at().y() + d * Math.tan(Math.toRadians(angle)), -60);
                int c = l.colour(e.subtract(l.at()), 0);
                if (c == AirfieldLighting.WHITE) { whites++; } else if (c == AirfieldLighting.RED) { reds++; }
                if (nearest == null || Math.abs(l.at().z()) < Math.abs(nearest.z())) { nearest = l.at(); }
            }
            int expected = angle < 2.5 ? 0 : angle < 2.84 ? 1 : angle < 3.16 ? 2 : angle < 3.5 ? 3 : 4;
            assertEquals(expected, whites, "whites at " + angle + " deg");
            assertEquals(4 - expected, reds);
        }
        // on the path the units nearest the runway are the red ones
        FieldLamp inner = lamps.stream().min(java.util.Comparator.comparingDouble(l -> Math.abs(l.at().z()))).orElseThrow();
        Vec3d on = eye(inner.at().x() - 1500, inner.at().y() + 1500 * Math.tan(Math.toRadians(3)), -60);
        assertEquals(AirfieldLighting.RED, inner.colour(on.subtract(inner.at()), 0));
        assertEquals(0, lamps.get(0).coverage(eye(2000, 70, -30).subtract(lamps.get(0).at())), 1e-12, "not seen from beyond the runway");
    }

    @Test void flashersRunTowardTheThreshold() {
        List<Fixture> f = new ArrayList<>();
        for (int i = 1; i <= 10; i++) { f.add(new Fixture(Fixture.Kind.APPROACH_FLASHER, -30 * i, 64, 0)); }
        java.util.Collections.shuffle(f, new java.util.Random(3));
        List<FieldLamp> lamps = AirfieldLighting.lamps(List.of(RUNWAY), f);
        // each fires once in 0.5 s, the farthest first
        lamps.sort(java.util.Comparator.comparingDouble(l -> l.signal().startS()));
        for (int i = 1; i < lamps.size(); i++) { assertTrue(lamps.get(i).at().x() > lamps.get(i - 1).at().x(), "nearer the threshold fires later"); }
        assertEquals(-300.0 + 0.5, lamps.get(0).at().x(), 1e-9);
        assertTrue(lamps.get(0).axis().x() < 0, "aimed back up the approach");
    }

    @Test void atisLettersMoveOnWithSignificantChanges() {
        Atis a = Atis.update(null, Atis.of('?', "27", 268, 5, 6, 20_000, false, false, 288.15, 101_325));
        assertEquals('A', a.letter());
        assertEquals(270, a.windFromDeg());
        assertEquals(0, a.gustMps(), "a gust under 10 kt above the mean is not reported");
        assertEquals(1013, a.qnhHpa());
        assertEquals(15, a.temperatureC());
        assertSame(a, Atis.update(a, Atis.of('?', "27", 275, 6, 7, 18_000, false, false, 288.15, 101_325)), "small changes: the same report");
        Atis b = Atis.update(a, Atis.of('?', "09", 95, 5, 6, 20_000, false, false, 288.15, 101_325));
        assertEquals('B', b.letter(), "another runway");
        Atis c = Atis.update(b, Atis.of('?', "09", 95, 5, 12, 20_000, false, false, 288.15, 101_325));
        assertEquals('C', c.letter(), "a gust 13 kt over the mean appears");
        assertEquals(12, c.gustMps(), 1e-12);
        assertEquals('D', Atis.update(c, Atis.of('?', "09", 95, 5, 12, 2_500, true, false, 288.15, 101_325)).letter(), "rain, 2.5 km");
        assertEquals('A', Atis.after('Z'));
    }

    @Test void unitsAsTheyAreSpoken() {
        assertEquals(360, AviationUnits.windFrom(3, 5), "north is 360");
        assertEquals(10, AviationUnits.windFrom(5, 5));
        assertEquals(0, AviationUnits.windFrom(200, 0.2), "calm");
        assertEquals(1013, AviationUnits.hectopascals(101_325), "rounded down");
        assertEquals(1000, AviationUnits.height(305, AviationUnits.System.AVIATION), "305 m is 1000 ft");
        assertEquals(305, AviationUnits.height(305, AviationUnits.System.METRIC));
        assertEquals(97, AviationUnits.speed(50, AviationUnits.System.AVIATION));
        assertEquals(180, AviationUnits.speed(50, AviationUnits.System.METRIC));
        assertEquals(4_300, AviationUnits.visibility(4_321));
        assertEquals(10_000, AviationUnits.visibility(20_000));
        assertEquals(360, AviationUnits.heading(0));
        assertEquals(AircraftKind.GROUND, AircraftKind.of(new dev.mcmod.aerocore.api.vehicle.VehicleBlueprint("empty", List.of(), List.of())));
        assertEquals(WakeCategory.LIGHT, WakeCategory.of(1_200));
        assertEquals(WakeCategory.MEDIUM, WakeCategory.of(70_000));
        assertEquals(WakeCategory.HEAVY, WakeCategory.of(300_000));
    }

    @Test void aTallerTowerSeesFartherAndLightsCostPower() {
        assertTrue(TowerRules.radarRangeM(40) > TowerRules.radarRangeM(12));
        assertEquals(6_000, TowerRules.radarRangeM(1_000), 1e-9, "capped");
        assertEquals(1_500 + 60 * 30, TowerRules.visualRangeM(30, 20_000), 1e-9);
        assertEquals(1_500, TowerRules.visualRangeM(30, 1_500), 1e-9, "never beyond the visibility");
        List<Fixture> edges = new ArrayList<>();
        for (int i = 0; i < 50; i++) { edges.add(new Fixture(Fixture.Kind.RUNWAY_EDGE, i * 60, 64, 15)); }
        double[] full = new double[AirfieldLighting.CIRCUITS], dim = new double[AirfieldLighting.CIRCUITS];
        full[AirfieldLighting.RUNWAY] = 1;
        dim[AirfieldLighting.RUNWAY] = 0.1;
        assertEquals(10_000, TowerRules.lightingW(edges, full), 1e-9, "50 high-intensity edge lights at 200 W");
        assertEquals(10_000 * Math.pow(0.1, 2.0 / 3), TowerRules.lightingW(edges, dim), 1e-6, "a tenth of the light for about a fifth of the power");
        assertEquals(0, TowerRules.lightingW(edges, new double[AirfieldLighting.CIRCUITS]), 1e-12);
        assertEquals(10, TowerRules.hours(50 * 3.6e6, 5_000), 1e-9, "50 kWh at 5 kW");
        assertEquals(120, Separation.departureInterval(WakeCategory.HEAVY, WakeCategory.LIGHT), 1e-12);
        assertEquals(60, Separation.departureInterval(WakeCategory.LIGHT, WakeCategory.HEAVY), 1e-12);
    }
}
