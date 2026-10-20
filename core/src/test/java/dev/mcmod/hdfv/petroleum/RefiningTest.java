package dev.mcmod.hdfv.petroleum;

import dev.mcmod.hdfv.fluid.HdfvFluids;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The refinery's and chemical plant's yields (0.8): mass kept, the heater's fuel, what FCC does for gasoline, every product something hdfv has. */
class RefiningTest {
    @Test void everyUnitKeepsItsMassAndMakesThingsThatExist() {
        for (Refining.Yield y : Refining.all()) {
            assertEquals(0, y.imbalance(), 1e-9, y.name() + " keeps its mass");
            for (String p : y.products().keySet()) { assertTrue(HdfvFluids.of(p) != null || Refining.item(p), y.name() + " makes " + p); }
            // water (the methanol unit's, 0.23) is the game's own fluid
            for (String f : y.feeds().keySet()) { assertTrue(HdfvFluids.of(f) != null || HdfvFluids.WATER.id().equals(f), y.name() + " takes " + f); }
            assertTrue(y.burnt() >= 0 && y.gridJPerKg() > 0, y.name());
        }
    }

    @Test void theCrudeHeaterBurnsOneToTwoPercentAndTheVacuumSectionMakesBitumen() {
        for (String crude : new String[]{"crude_oil", "heavy_crude_oil"}) {
            Refining.Yield atm = Refining.distillation(crude, false), vac = Refining.distillation(crude, true);
            System.out.printf("%s: %s burnt %.3f; vacuum %s%n", crude, atm.products(), atm.burnt(), vac.products());
            assertTrue(atm.burnt() >= 0.01 && atm.burnt() <= 0.03, crude + " heater and flare " + atm.burnt());
            assertTrue(vac.products().get("bitumen") > 0 && !vac.products().containsKey("fuel_oil"));
            assertEquals(atm.products().get("fuel_oil"), vac.products().get("gas_oil") + vac.products().get("bitumen"), 1e-12, "the residue split");
        }
        assertTrue(Refining.distillation("heavy_crude_oil", true).products().get("bitumen") > 2 * Refining.distillation("crude_oil", true).products().get("bitumen"),
                "heavy crude, more residue");
    }

    @Test void fccTurnsGasOilIntoGasolineAndTheCrackersIntoOlefins() {
        Map<String, Double> crude = Refining.distillation("crude_oil", true).products();
        double straight = 0, withFcc = crude.get("gas_oil") * Refining.FCC.products().get("gasoline");
        assertEquals(0.5, Refining.FCC.products().get("gasoline"), 1e-12);
        assertTrue(withFcc > straight + 0.1, "FCC adds a tenth of the crude or more as gasoline: " + withFcc);
        assertEquals(0.30, Refining.NAPHTHA_CRACKER.products().get("ethylene"), 1e-12);
        assertEquals(0.42, Refining.LPG_CRACKER.products().get("ethylene"), 1e-12);
        assertEquals(0.85, Refining.REFORMER.products().get("premium_gasoline"), 1e-12);
        // the cracker burns its methane and hydrogen for its heat: about 26 GJ a tonne of ethylene needs 7.8 MJ a kg of naphtha
        assertTrue(Refining.NAPHTHA_CRACKER.burnt() * 45e6 > 0.30 * 26e6 * 0.9, "its own fuel heats it");
    }

    /** 0.23: the reformer's unit makes methanol of LPG and water (C3H8 + 3 H2O -> 3 CH3OH + H2), firing itself with more LPG. */
    @Test void theReformerMakesMethanolOfLpgAndWater() {
        Refining.Yield y = Refining.METHANOL;
        assertEquals(java.util.Set.of("lpg", "water"), y.feeds().keySet());
        double methanol = y.products().get("methanol");
        assertEquals(0.4587 * (1 + Refining.METHANOL_FIRED), y.feeds().get("lpg") / methanol, 0.002, "LPG a kg of methanol, the fired share too");
        assertEquals(0.5622, y.feeds().get("water") / methanol, 0.002, "water a kg of methanol");
        assertEquals(0.021, y.products().get("hydrogen") / methanol, 0.001, "hydrogen left over a kg");
        // about 31 GJ of LPG a tonne of methanol (plants: 29-32 GJ/t of natural gas)
        assertEquals(30.8e6, y.feeds().get("lpg") / methanol * 46.35e6, 0.5e6);
    }
}
