package dev.mcmod.hdfv.recipe;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arc furnace's and the electrolysis cell's recipes (0.23): each one of Metallurgy's batches, its kWh AeroCore's within 2 %. */
class MetallurgyRecipesTest {
    @Test void eachBatchTakesWhatAeroCoreSays() {
        Set<Metallurgy.Batch> seen = new HashSet<>();
        for (RecipeFiles.Recipe r : RecipeFiles.all()) {
            if (!r.type().equals("hdfv:electric_furnace") && !r.type().equals("hdfv:electrolysis")) { continue; }
            Metallurgy.Batch b = Metallurgy.of(r.path());
            assertNotNull(b, r.path() + " is one of Metallurgy's batches");
            assertEquals(b.arcFurnace, r.type().equals("hdfv:electric_furnace"), r.path() + ": its machine");
            double kwh = ((Number) r.json().get("kwh")).doubleValue();
            assertEquals(b.kwh(), kwh, 0.02 * b.kwh(), r.path() + ": " + kwh + " kWh, AeroCore " + b.kwh());
            seen.add(b);
        }
        assertEquals(Set.of(Metallurgy.Batch.values()), seen, "every batch has its recipe");
    }

    @Test void theBatchesAreWhatPlantsTake() {
        // aluminium 13 kWh/kg, steel 0.4, titanium's sponge 15 (and its magnesium 13 apart)
        assertEquals(130, Metallurgy.Batch.ALUMINIUM.kwh(), 2);
        assertEquals(0.40, Metallurgy.Batch.STEEL.kwh() / 80, 0.01);
        assertEquals(450, Metallurgy.Batch.TITANIUM_SPONGE.kwh(), 1e-9);
        assertTrue(Metallurgy.vacuumJPerKg(dev.mcmod.hdfv.manufacture.Alloy.TITANIUM) > Metallurgy.vacuumJPerKg(dev.mcmod.hdfv.manufacture.Alloy.INCONEL_718));
    }
}
