package dev.mcmod.hdfv.recipe;

import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.component.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recipes of 0.23 (tools/recipes.py writes them): everything they make is reachable from the world, the stages go
 * in the order the user chose (the first lathe from castings and forgings, the machines' parts from the machines before
 * them, the CNC late), the bench's times follow the parts' rule, the assemblies weigh what goes into them.
 */
class RecipeRulesTest {
    static { HdfvParts.register(); }

    private static Map<String, TechTree.Stage> stages() { return TechTree.stages(RecipeFiles.ways(), RecipeFiles.world()); }

    private static int tier(Map<String, TechTree.Stage> s, String id) {
        TechTree.Stage t = s.get(id);
        assertNotNull(t, id + " is not reachable");
        return t.tier();
    }

    /** 0.25: the cargo loader (a motor-driven conveyor) comes with the electric motor unit (the steel locker's stage or later). */
    @Test void theCargoLoaderComesWithElectricMotors() {
        Map<String, TechTree.Stage> s = stages();
        int loader = tier(s, "hdfv:cargo_loader");
        System.out.println("hdfv cargo loader: tier " + loader + ", electric motor unit " + tier(s, "hdfv:electric_motor_unit") + ", steel locker " + tier(s, "hdfv:steel_locker"));
        assertTrue(loader >= tier(s, "hdfv:electric_motor_unit") && loader >= tier(s, "hdfv:steel_locker") && loader >= 2, "tier " + loader);
    }

    @Test void everythingTheRecipesMakeIsReachable() {
        Map<String, TechTree.Stage> s = stages();
        Set<String> lost = new TreeSet<>();
        for (RecipeFiles.Recipe r : RecipeFiles.all()) {
            if (r.json().containsKey("neoforge:conditions")) { continue; }
            for (String o : RecipeFiles.outputs(r)) { if (!s.containsKey(o)) { lost.add(o + " (" + r.path() + ")"); } }
            for (var e : RecipeFiles.ingredients(r)) {
                if (e.getKey().stream().noneMatch(s::containsKey) && e.getKey().stream().noneMatch(i -> i.startsWith("#"))) { lost.add(e.getKey() + " in " + r.path()); }
            }
        }
        for (Component c : Component.values()) { if (!s.containsKey("hdfv:" + c.id())) { lost.add("hdfv:" + c.id()); } }
        for (Intermediate i : Intermediate.values()) { if (!s.containsKey("hdfv:" + i.id())) { lost.add("hdfv:" + i.id()); } }
        assertTrue(lost.isEmpty(), "unreachable: " + lost);
    }

    @Test void theStagesGoInTheChosenOrder() {
        Map<String, TechTree.Stage> s = stages();
        StringBuilder all = new StringBuilder();
        for (String id : List.of("hdfv:alloy_furnace", "hdfv:foundry", "hdfv:forging_press", "hdfv:assembly_bench", "hdfv:carbon_tool_set",
                "hdfv:bed_casting", "hdfv:lathe", "hdfv:drill_press", "hdfv:planer", "hdfv:steam_engine", "hdfv:turned_shaft", "hdfv:milling_machine",
                "hdfv:boring_mill", "hdfv:generator", "hdfv:arc_furnace", "hdfv:electrolysis_cell", "hdfv:aluminium_ingot", "hdfv:chromium_ingot",
                "hdfv:hss_tool_set", "hdfv:cut_gear", "hdfv:ball_bearing", "hdfv:grinder", "hdfv:gear_cutter", "hdfv:threader", "hdfv:ball_screw",
                "hdfv:cnc_machining_centre", "hdfv:linear_guide", "hdfv:electro_machine", "hdfv:cutting_table", "hdfv:titanium_sponge", "hdfv:design_tablet")) {
            all.append(id.substring(5)).append(' ').append(tier(s, id)).append(", ");
        }
        String at = all.toString();
        // the furnaces and the bench by hand; the castings on them; the first machine tools of castings
        assertEquals(0, tier(s, "hdfv:foundry"), at);
        assertEquals(0, tier(s, "hdfv:forging_press"), at);
        assertEquals(0, tier(s, "hdfv:assembly_bench"), at);
        assertEquals(0, tier(s, "hdfv:carbon_tool_set"), at);
        assertEquals(1, tier(s, "hdfv:bed_casting"), at);
        assertEquals(1, tier(s, "hdfv:lathe"), at);
        assertEquals(1, tier(s, "hdfv:drill_press"), at);
        assertEquals(1, tier(s, "hdfv:planer"), at);
        assertEquals(1, tier(s, "hdfv:steam_engine"), at);
        // the lathe's parts make the mill and the boring mill, the generator; electricity the arc furnace's and the cell's metals
        assertEquals(2, tier(s, "hdfv:turned_shaft"), at);
        assertEquals(2, tier(s, "hdfv:milling_machine"), at);
        assertEquals(2, tier(s, "hdfv:generator"), at);
        assertEquals(2, tier(s, "hdfv:aluminium_ingot"), at);
        // the mill's and the grinders' parts; the CNC once its ball screws can be made; its parts after it
        assertTrue(tier(s, "hdfv:cut_gear") == 3 && tier(s, "hdfv:grinder") == 3, at);
        assertTrue(tier(s, "hdfv:ball_screw") > tier(s, "hdfv:threader"), at);
        assertTrue(tier(s, "hdfv:cnc_machining_centre") >= tier(s, "hdfv:ball_screw"), at);
        assertTrue(tier(s, "hdfv:linear_guide") > tier(s, "hdfv:cnc_machining_centre"), at);
        assertTrue(tier(s, "hdfv:electro_machine") >= tier(s, "hdfv:linear_guide") && tier(s, "hdfv:cutting_table") >= tier(s, "hdfv:linear_guide"), at);
        // where: the first lathe at the bench, its castings on the machines, aluminium in the cell
        assertEquals(TechTree.ASSEMBLING, s.get("hdfv:lathe").where());
        assertEquals(TechTree.MACHINE, s.get("hdfv:bed_casting").where());
        assertEquals(TechTree.CELL, s.get("hdfv:aluminium_ingot").where());
    }

    /** Metal (kg) in one of an ingredient, as tools/recipes.py counts it (its first alternative). */
    static double kg(Set<String> ingredient) {
        String i = ingredient.iterator().next();
        if (i.startsWith("hdfv:stock#")) { return 10; }
        if (i.startsWith("hdfv:stock_bit#")) { return 1; }
        Component c = Component.ofId(i.substring(i.indexOf(':') + 1));
        if (i.startsWith("hdfv:") && c != null) { return c.kg; }
        for (Intermediate m : Intermediate.values()) { if (i.equals("hdfv:" + m.id())) { return m.kg; } }
        return VANILLA.getOrDefault(i, 0.0);
    }

    /** The vanilla things' metal the bench's recipes name (the tags the mod writes are its items, so their ingots). */
    static final Map<String, Double> VANILLA = Map.ofEntries(Map.entry("#c:ingots/iron", 10.0), Map.entry("#c:ingots/copper", 10.0),
            Map.entry("#c:ingots/gold", 10.0), Map.entry("#c:nuggets/iron", 1.1), Map.entry("#c:nuggets/gold", 1.1), Map.entry("minecraft:hopper", 50.0),
            Map.entry("minecraft:minecart", 50.0), Map.entry("minecraft:compass", 40.0), Map.entry("minecraft:flint_and_steel", 10.0),
            Map.entry("hdfv:nickel_ingot", 10.0), Map.entry("hdfv:grinding_wheel", 3.0),
            Map.entry("minecraft:iron_bars", 0.4), Map.entry("minecraft:bucket", 30.0));   // 0.26: the tower crane's and the concrete plant's

    @Test void theBenchsTimesAreThePartsRuleAndTheAssembliesWeighTheirParts() {
        List<String> wrong = new ArrayList<>();
        for (RecipeFiles.Recipe r : RecipeFiles.all()) {
            if (!r.type().equals("hdfv:assembling")) { continue; }
            int pieces = 0;
            double mass = 0;
            for (var e : RecipeFiles.ingredients(r)) {
                pieces += e.getValue();
                mass += kg(e.getKey()) * e.getValue();
            }
            long expected = Math.round(300 + 60 * pieces + 3 * mass);
            long seconds = ((Number) r.json().get("seconds")).longValue();
            if (seconds != expected) { wrong.add(r.path() + " " + seconds + " s, the rule " + expected); }
            String out = RecipeFiles.outputs(r).get(0);
            for (Intermediate m : Intermediate.values()) {
                if (m.group == Intermediate.Group.ASSEMBLY && out.equals("hdfv:" + m.id()) && Math.abs(m.kg - mass) > 0.15) {
                    wrong.add(m + " weighs " + m.kg + " kg, its parts " + mass);
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("; ", wrong));
    }
}
