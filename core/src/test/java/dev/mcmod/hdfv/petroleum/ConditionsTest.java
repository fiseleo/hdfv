package dev.mcmod.hdfv.petroleum;

import dev.mcmod.aerocore.api.petroleum.Conversion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The refinery's and oil field's operating conditions (0.9): the nominal ones are 0.8's, the others keep the mass and move the products. */
class ConditionsTest {
    private static double share(Refining.Yield y, String p) { return y.products().getOrDefault(p, 0.0); }

    @Test void cutPointsMoveTheDrawOffs() {
        for (String crude : new String[]{"crude_oil", "heavy_crude_oil"}) {
            for (boolean vacuum : new boolean[]{false, true}) {
                assertEquals(Refining.distillation(crude, vacuum), Refining.distillation(crude, vacuum, Refining.Cuts.NOMINAL), "nominal is 0.8's");
                // the same cuts given another way round (not the NOMINAL object) come to the same yields
                Refining.Yield same = Refining.distillation(crude, vacuum, new Refining.Cuts(180, 240, 350.0000001));
                for (String p : Refining.distillation(crude, vacuum).products().keySet()) {
                    assertEquals(share(Refining.distillation(crude, vacuum), p), share(same, p), 1e-6, crude + " " + p);
                }
                Refining.Yield wide = Refining.distillation(crude, vacuum, new Refining.Cuts(200, 240, 380));
                assertEquals(0, wide.imbalance(), 1e-12, "mass kept");
                assertTrue(share(wide, "naphtha") > share(same, "naphtha") && share(wide, "kerosene") < share(same, "kerosene"), "naphtha cut heavier");
                assertTrue(share(wide, "diesel") > share(same, "diesel"), "diesel cut heavier");
                assertTrue(wide.burnt() > same.burnt() - 1e-12, "a hotter flash zone burns no less");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new Refining.Cuts(250, 240, 350));
    }

    @Test void theUnitsFollowTheirConditions() {
        assertSame(Refining.FCC, Refining.fcc(Conversion.NOMINAL_RISER_C));
        Refining.Yield distillate = Refining.fcc(500), olefins = Refining.fcc(550);
        assertEquals(0, distillate.imbalance(), 1e-9);
        assertTrue(share(distillate, "diesel") > share(Refining.FCC, "diesel"), "maximum distillate: more light cycle oil");
        assertTrue(share(olefins, "lpg") > share(Refining.FCC, "lpg"), "maximum olefins: more LPG");
        assertSame(Refining.REFORMER, Refining.reformer(98));
        Refining.Yield regular = Refining.reformer(95), hard = Refining.reformer(102);
        assertTrue(share(regular, "gasoline") > share(Refining.REFORMER, "premium_gasoline") && share(regular, "premium_gasoline") == 0, "under 98: regular gasoline");
        assertTrue(share(hard, "premium_gasoline") < share(Refining.REFORMER, "premium_gasoline"), "more octane, less of it");
        assertEquals(0, hard.imbalance(), 1e-9);
        assertSame(Refining.NAPHTHA_CRACKER, Refining.cracker("naphtha", Conversion.NOMINAL_COIL_C));
        assertTrue(share(Refining.cracker("naphtha", 860), "ethylene") > share(Refining.NAPHTHA_CRACKER, "ethylene"));
        assertTrue(share(Refining.cracker("lpg", 800), "propylene") > share(Refining.LPG_CRACKER, "propylene"));
        assertEquals("lpg_cracker", Refining.cracker("lpg", 810).name());
    }

    @Test void theJackAndTheChoke() {
        assertEquals(Oilwell.jackDisplacementM3PerS(), Oilwell.Jack.NOMINAL.displacementM3PerS(), 1e-15);
        Oilwell.Jack twice = new Oilwell.Jack(20 / 2.0 * 1.5, Oilwell.STROKE_M, Oilwell.PLUNGER_M);
        assertEquals(1.5 * Oilwell.jackDisplacementM3PerS(), twice.displacementM3PerS(), 1e-12, "half as many strokes again, half as much again");
        assertThrows(IllegalArgumentException.class, () -> new Oilwell.Jack(30, Oilwell.STROKE_M, Oilwell.PLUNGER_M));
        // a field that flows: a choke holds it back, none gives its natural rate; a jack lifts no more than the inflow
        OilFields.Field f = null;
        for (int rx = 0; f == null && rx < 200; rx++) {
            OilFields.Field g = OilFields.field(12345L, rx, 7);
            if (g != null && g.naturalRate(0) > 0) { f = g; }
        }
        assertNotNull(f);
        assertEquals(f.naturalRate(0), f.chokedRate(0, 0), 0);
        assertTrue(f.chokedRate(16 * 0.0254 / 64, 0) < f.naturalRate(0));
        Oilwell.Jack big = new Oilwell.Jack(15, Oilwell.Jack.STROKES[3], Oilwell.Jack.PLUNGERS[3]);
        assertTrue(Oilwell.jackRateM3PerS(f, 0, big) <= f.pumpedInflow(0) + 1e-15);
    }
}
