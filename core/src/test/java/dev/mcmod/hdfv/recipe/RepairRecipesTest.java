package dev.mcmod.hdfv.recipe;

import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: where repairs come in the tech tree (tools/recipes.py writes their recipes): the repair shop at the refit
 * shop's stage, the field kits early, the friction lining (a B-grade standard component) no later than the shop that
 * relines with it.
 */
class RepairRecipesTest {
    static { HdfvParts.register(); }

    @Test void theRepairShopComesWithTheRefitShopAndTheKitsEarly() {
        Map<String, TechTree.Stage> s = TechTree.stages(RecipeFiles.ways(), RecipeFiles.world());
        int shop = tier(s, "hdfv:repair_shop_controller"), refit = tier(s, "hdfv:refit_shop_controller"), kit = tier(s, "hdfv:field_repair_kit"),
                patch = tier(s, "hdfv:tyre_patch_kit"), lining = tier(s, "hdfv:friction_lining");
        String at = "repair shop " + shop + " (" + s.get("hdfv:repair_shop_controller").where() + "), refit shop " + refit + ", field kit " + kit + ", patch kit " + patch
                + ", friction lining " + lining + " (" + s.get("hdfv:friction_lining").where() + ")";
        System.out.println("hdfv repair stages: " + at);
        assertEquals(refit, shop, at);
        assertEquals(TechTree.ASSEMBLING, s.get("hdfv:repair_shop_controller").where(), at);
        assertTrue(kit <= 2 && patch <= 2, at);
        assertTrue(lining <= shop, at);
        assertEquals(TechTree.MACHINE, s.get("hdfv:friction_lining").where(), at);
    }

    private static int tier(Map<String, TechTree.Stage> s, String id) {
        TechTree.Stage t = s.get(id);
        assertNotNull(t, id + " is not reachable");
        return t.tier();
    }
}
