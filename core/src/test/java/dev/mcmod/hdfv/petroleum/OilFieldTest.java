package dev.mcmod.hdfv.petroleum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Oil fields from the seed (0.8): the same every time, about as many as meant, in the ranges given; wells that flow, deplete and are pumped. */
class OilFieldTest {
    @Test void fieldsAreTheSameEveryTimeAndAsCommonAsMeant() {
        long seed = 1234567L;
        int n = 0, regions = 0, light = 0, flowing = 0;
        for (int rx = -20; rx < 20; rx++) {
            for (int rz = -20; rz < 20; rz++) {
                regions++;
                OilFields.Field f = OilFields.field(seed, rx, rz), again = OilFields.field(seed, rx, rz);
                assertEquals(f, again, "the same field from the same seed");
                if (f == null) { continue; }
                n++;
                if (f.light()) { light++; }
                if (f.flowsUntilM3() > 0) { flowing++; }
                // inside its region, ranges as documented
                assertEquals(rx, Math.floorDiv(f.centreX(), OilFields.REGION));
                assertTrue(f.centreX() - f.radiusM() >= rx * OilFields.REGION && f.centreX() + f.radiusM() < (rx + 1) * OilFields.REGION, "inside its region");
                assertTrue(f.depthM() >= 150 && f.depthM() <= 800);
                assertTrue(f.radiusM() >= 40 && f.radiusM() <= 150);
                assertTrue(f.reservoir().porosity() >= 0.1 && f.reservoir().porosity() <= 0.3);
                assertTrue(f.rockStrengthPa() >= 30e6 && f.rockStrengthPa() <= 150e6);
                assertSame(f, f, "");
                assertEquals(f.id(), OilFields.regionKey(f.centreX(), f.centreZ()));
                assertNotNull(OilFields.at(seed, f.centreX(), f.centreZ()), "found at its centre");
                assertNull(OilFields.at(seed, (int) (f.centreX() + f.radiusM() + 2), f.centreZ()), "not past its edge");
            }
        }
        double share = (double) n / regions;
        System.out.printf("fields: %d of %d regions (%.2f), %d light, %d flowing at first%n", n, regions, share, light, flowing);
        assertEquals(OilFields.FIELD_CHANCE, share, 0.05);
        assertEquals(0.6, (double) light / n, 0.08);
        assertTrue(flowing > 0.3 * n && flowing < n, "some flow by themselves, some need pumping from the start: " + flowing);
        int differ = 0;
        for (int rx = 0; rx < 20; rx++) { for (int rz = 0; rz < 20; rz++) { if (!java.util.Objects.equals(OilFields.field(1L, rx, rz), OilFields.field(2L, rx, rz))) { differ++; } } }
        assertTrue(differ > 100, "another seed, another world: " + differ);
    }

    @Test void aWellFlowsThenNeedsAPumpAndTheRigDrillsInHours() {
        // a light field 500 m down at 1.2 times the water column's pressure
        OilFields.Field f = OilFields.of(1, 0, 0, 100, 500, 15, 0.2, 0.75, 100, true, 1.2e4 * 500, 80e6);
        double q = f.naturalRate(0);
        System.out.printf("well: flows %.1f m3/day at first, until %.0f m3 of %.0f recoverable; pumped %.1f m3/day; drilling %.1f h%n",
                q * 86400, f.flowsUntilM3(), f.reservoir().recoverableM3(), Oilwell.jackRateM3PerS(f, f.flowsUntilM3()) * 86400, Oilwell.drillS(f) / 3600);
        assertTrue(q * 86400 > 5 && q * 86400 < 200, "a few to a couple of hundred cubic metres a day: " + q * 86400);
        assertTrue(f.naturalRate(0.5 * f.flowsUntilM3()) < q, "it falls as the field is drawn down");
        assertEquals(0, f.naturalRate(1.01 * f.flowsUntilM3()), 0, "then it stops flowing by itself");
        assertTrue(Oilwell.jackRateM3PerS(f, 1.01 * f.flowsUntilM3()) > 0, "and a pump jack still lifts it");
        assertEquals(29, Oilwell.jackDisplacementM3PerS() * 86400, 1.5, "the jack's 29 m3 a day");
        double w = Oilwell.jackW(f, Oilwell.jackDisplacementM3PerS());
        assertEquals(Oilwell.jackDisplacementM3PerS() * f.oilDensity() * 9.81 * 500 / 0.5, w, 1e-6 * w);
        // 75 kW at 80 MPa through a 0.2 m bit: about 34 m an hour (Teale: 0.35 x 75 kW / (0.0314 m2 x 80 MPa)), so 500 m in about 15 h
        double hours = Oilwell.drillS(f) / 3600;
        assertEquals(500 / (0.35 * 75e3 / (Math.PI / 4 * 0.04 * 80e6) * 3600), hours, 1e-6 * hours);
        assertTrue(hours > 5 && hours < 40, "hours of drilling: " + hours);
    }
}
