package dev.mcmod.hdfv.storage;

import dev.mcmod.hdfv.manufacture.Pace;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.petroleum.Refining;
import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.recipe.Intermediate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: storage. The kinds' sizes and pages (StorageKind), the rules of mass that need no game (CargoMass), what
 * tools/storage_assets.py and tools/recipes.py made for the four blocks (blockstate, model and its textures, item
 * model, loot table keeping the name, recipe, tool tag, names in the three languages, the parts rack's item tag), and
 * the default masses tools/item_mass.py wrote: the scale's anchors are core's constants (an ingot, the steam engine's
 * fuels), later entries refine earlier ones, and nothing ItemMass has its own rule for is listed.
 */
class StorageAssetsTest {
    private static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    private static final Path RES = ROOT.resolve("neoforge/src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hdfv"), DATA = RES.resolve("data");

    @Test void theKindsHoldWhatTheySay() {
        assertEquals(List.of("storage_crate", "steel_locker", "parts_rack", "shipping_container"), StorageKind.ids());
        assertEquals(27, StorageKind.STORAGE_CRATE.slots);
        assertEquals(54, StorageKind.STEEL_LOCKER.slots);
        assertEquals(36, StorageKind.PARTS_RACK.slots);
        assertEquals(108, StorageKind.SHIPPING_CONTAINER.slots);
        assertEquals(List.of(3, 6, 4, 6), List.of(StorageKind.STORAGE_CRATE.rows(), StorageKind.STEEL_LOCKER.rows(), StorageKind.PARTS_RACK.rows(), StorageKind.SHIPPING_CONTAINER.rows()));
        assertEquals(List.of(1, 1, 1, 2), List.of(StorageKind.STORAGE_CRATE.pages(), StorageKind.STEEL_LOCKER.pages(), StorageKind.PARTS_RACK.pages(), StorageKind.SHIPPING_CONTAINER.pages()));
        for (StorageKind k : StorageKind.values()) {
            assertEquals(k, StorageKind.ofId(k.id()));
            assertEquals(k == StorageKind.PARTS_RACK, k.partsOnly, k.id());
            assertEquals(k == StorageKind.STORAGE_CRATE, k.wooden, k.id());
            assertTrue(k.rows() * StorageKind.COLUMNS * k.pages() >= k.slots && k.rows() <= StorageKind.ROWS_SHOWN, k.id());
        }
        // any container's menu: a part row is a row, a seventh row a second page
        assertEquals(1, StorageKind.rows(5));
        assertEquals(2, StorageKind.rows(18));
        assertEquals(1, StorageKind.pages(54));
        assertEquals(2, StorageKind.pages(55));
        assertEquals(3, StorageKind.pages(120));
    }

    @Test void massNeedsNoGame() {
        // a workpiece: its stock after the first step, its finished mass after the last, evenly between
        assertEquals(12, CargoMass.workpieceKg(12, 8, 1, 5), 1e-12);
        assertEquals(11, CargoMass.workpieceKg(12, 8, 2, 5), 1e-12);
        assertEquals(9, CargoMass.workpieceKg(12, 8, 4, 5), 1e-12);
        assertEquals(8, CargoMass.workpieceKg(12, 8, 5, 5), 1e-12);
        assertEquals(8, CargoMass.workpieceKg(12, 8, 9, 5), 1e-12);
        assertEquals(8, CargoMass.workpieceKg(12, 8, 1, 1), 1e-12);
        assertEquals(12, CargoMass.workpieceKg(12, 8, 0, 5), 1e-12);
        // the footer's mass: tenths of a kilogram through two 16 bit halves (data slots are sent as shorts)
        for (double kg : new double[]{0, 0.04, 0.05, 12.34, 3276.7, 3276.8, 6553.6, 123456.7, 2.0e7}) {
            int tenths = CargoMass.tenths(kg);
            int low = (short) CargoMass.low(tenths), high = (short) CargoMass.high(tenths);
            assertEquals(tenths, CargoMass.join(low, high), "through shorts: " + kg);
            assertEquals(kg, CargoMass.join(low, high) / 10.0, 0.05 + 1e-9, "kg " + kg);
        }
        assertEquals(0, CargoMass.tenths(-3));
        assertEquals(0, CargoMass.tenths(Double.NaN));
        assertEquals(Integer.MAX_VALUE, CargoMass.tenths(1e12));
        assertEquals("0", CargoMass.text(0));
        assertEquals("0.02", CargoMass.text(0.02));
        assertEquals("0.5", CargoMass.text(0.5));
        assertEquals("1", CargoMass.text(1));
        assertEquals("12.5", CargoMass.text(12.5));
        assertEquals("12.5", CargoMass.text(12.54));
        assertEquals("64", CargoMass.text(64));
        assertEquals("100", CargoMass.text(99.96));
        assertEquals("1,028", CargoMass.text(1028.4));
        assertEquals(10.0, CargoMass.FALLBACK_BLOCK_KG);
        assertEquals(1.0, CargoMass.FALLBACK_ITEM_KG);
        assertTrue(CargoMass.MAX_DEPTH >= 2);
    }

    @Test void everyStorageBlockHasItsFiles() throws IOException {
        String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8), zh = Files.readString(ASSETS.resolve("lang/zh_tw.json"), StandardCharsets.UTF_8),
                cn = Files.readString(ASSETS.resolve("lang/zh_cn.json"), StandardCharsets.UTF_8);
        String pickaxe = Files.readString(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json")), axe = Files.readString(DATA.resolve("minecraft/tags/block/mineable/axe.json"));
        for (StorageKind kind : StorageKind.values()) {
            String id = kind.id();
            String state = Files.readString(ASSETS.resolve("blockstates/" + id + ".json"));
            for (String facing : List.of("north", "east", "south", "west")) {
                int at = state.indexOf("\"facing=" + facing + "\"");
                assertTrue(at >= 0, id + " facing " + facing);
                String entry = state.substring(at, state.indexOf('}', at));
                assertTrue(entry.contains("\"hdfv:block/" + id + "\""), id + " " + facing + ": " + entry);
                int y = switch (facing) { case "east" -> 90; case "south" -> 180; case "west" -> 270; default -> 0; };
                assertEquals(y != 0, entry.contains("\"y\": " + y), id + " " + facing + ": " + entry);
            }
            String model = Files.readString(ASSETS.resolve("models/block/" + id + ".json"));
            assertTrue(model.contains("\"minecraft:block/orientable\""), id + ": " + model);
            for (String face : List.of("front", "side", "top")) {
                assertTrue(model.contains("\"" + face + "\": \"hdfv:block/" + id + "_" + face + "\""), id + " " + face + ": " + model);
                assertTrue(Files.exists(ASSETS.resolve("textures/block/" + id + "_" + face + ".png")), id + "_" + face + ".png");
                assertTrue(Files.exists(ROOT.resolve("tools/blender/blocks16/" + id + "_" + face + ".png")), id + "_" + face + "'s 16 px source");
            }
            assertTrue(Files.readString(ASSETS.resolve("models/item/" + id + ".json")).contains("\"hdfv:block/" + id + "\""), id + " item model");
            String loot = Files.readString(DATA.resolve("hdfv/loot_table/blocks/" + id + ".json"));
            assertTrue(loot.contains("\"hdfv:" + id + "\"") && loot.contains("\"minecraft:copy_components\"") && loot.contains("\"minecraft:custom_name\""), id + " drops itself named: " + loot);
            assertFalse(loot.contains("minecraft:container"), id + " drops its contents itself, not in its item");
            assertTrue(Files.exists(DATA.resolve("hdfv/recipe/" + id + ".json")), id + " has a recipe");
            assertEquals(kind.wooden, axe.contains("\"hdfv:" + id + "\""), id + " and the axe");
            assertEquals(!kind.wooden, pickaxe.contains("\"hdfv:" + id + "\""), id + " and the pickaxe");
            for (String lang : List.of(en, zh, cn)) {
                assertTrue(lang.contains("\"block.hdfv." + id + "\""), id + " named");
                assertTrue(lang.contains("\"hdfv.jei." + id + "\""), id + " described");
            }
        }
        for (String lang : List.of(en, zh, cn)) {
            for (String key : List.of("hdfv.storage.footer", "hdfv.storage.footer.limit", "hdfv.storage.page", "hdfv.storage.mass", "hdfv.storage.mass.stack",
                    "hdfv.configuration.storage", "hdfv.configuration.itemMassScale")) {
                assertTrue(lang.contains("\"" + key + "\""), key);
            }
        }
        // the crate is planks and nails; the steel ones plate, tube and fasteners
        assertTrue(Files.readString(DATA.resolve("hdfv/recipe/storage_crate.json")).contains("\"minecraft:planks\""));
        for (String id : List.of("steel_locker", "parts_rack", "shipping_container")) {
            String recipe = Files.readString(DATA.resolve("hdfv/recipe/" + id + ".json"));
            assertTrue(recipe.contains("\"hdfv:steel_plate\"") && recipe.contains("\"hdfv:fastener_kit\""), id + ": " + recipe);
        }
        // what the parts rack takes: parts, workpieces, drawings, every standard component and bench assembly; no metallurgy
        String rack = Files.readString(DATA.resolve("hdfv/tags/item/parts_rack.json"));
        for (String id : List.of("part", "workpiece", "blueprint", "blank_blueprint", "vehicle_blueprint")) { assertTrue(rack.contains("\"hdfv:" + id + "\""), id); }
        for (Component c : Component.values()) { assertTrue(rack.contains("\"hdfv:" + c.id() + "\""), c.id()); }
        for (Intermediate i : Intermediate.values()) {
            assertEquals(i.group == Intermediate.Group.ASSEMBLY, rack.contains("\"hdfv:" + i.id() + "\""), i.id());
        }
        assertFalse(rack.contains("\"hdfv:stock\""), "stock is not a part");
    }

    /** The data map's entries in their order: key ("#tag" or item id) to kg. */
    private static Map<String, Double> masses() throws IOException {
        String json = Files.readString(DATA.resolve("hdfv/data_maps/item/item_mass.json"), StandardCharsets.UTF_8);
        Map<String, Double> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([^\"]+)\":\\s*\\{\\s*\"kg\":\\s*([0-9.eE+-]+)\\s*}").matcher(json);
        while (m.find()) { assertEquals(null, out.put(m.group(1), Double.parseDouble(m.group(2))), m.group(1) + " twice"); }
        return out;
    }

    @Test void theDefaultMassesAreOnTheWorkshopsScale() throws IOException {
        Map<String, Double> kg = masses();
        assertTrue(kg.size() >= 300, "entries: " + kg.size());
        // the anchors: an ingot, a nugget a ninth, a block nine; the steam engine's fuels
        assertEquals(Pace.KG_PER_INGOT, kg.get("#c:ingots"));
        assertEquals(Pace.KG_PER_INGOT, kg.get("#c:raw_materials"));
        assertEquals(Pace.KG_PER_INGOT / 9, kg.get("#c:nuggets"), 0.02);
        assertEquals(9 * Pace.KG_PER_INGOT, kg.get("#c:storage_blocks"));
        assertEquals(PowerPlant.COAL.kg(), kg.get("minecraft:coal"));
        assertEquals(PowerPlant.CHARCOAL.kg(), kg.get("minecraft:charcoal"));
        assertEquals(PowerPlant.COAL_BLOCK.kg(), kg.get("#c:storage_blocks/coal"));
        assertEquals(PowerPlant.LOG.kg(), kg.get("#minecraft:logs"));
        assertEquals(PowerPlant.PLANK.kg(), kg.get("#minecraft:planks"));
        assertEquals(PowerPlant.STICK.kg(), kg.get("#c:rods/wooden"));
        // a later entry replaces an earlier one for the same item: the narrower after the wider
        List<String> order = List.copyOf(kg.keySet());
        for (String[] pair : new String[][]{{"#c:storage_blocks", "#c:storage_blocks/coal"}, {"#minecraft:slabs", "#minecraft:wooden_slabs"}, {"#minecraft:stairs", "#minecraft:wooden_stairs"},
                {"#c:rods", "#c:rods/wooden"}, {"#minecraft:pickaxes", "minecraft:wooden_pickaxe"}, {"#minecraft:chest_armor", "minecraft:leather_chestplate"},
                {"#c:stones", "minecraft:deepslate"}, {"#c:ores", "hdfv:bauxite_ore"}, {"#c:buckets", "minecraft:powder_snow_bucket"}}) {
            assertTrue(order.indexOf(pair[0]) >= 0 && order.indexOf(pair[0]) < order.indexOf(pair[1]), pair[0] + " before " + pair[1]);
        }
        int firstOwn = -1, lastOther = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).startsWith("hdfv:")) { if (firstOwn < 0) { firstOwn = i; } } else { lastOther = i; }
            assertTrue(kg.get(order.get(i)) > 0 && kg.get(order.get(i)) < 5000, order.get(i) + ": " + kg.get(order.get(i)));
        }
        assertTrue(firstOwn > lastOther, "hdfv's own entries come last (after every tag)");
        // nothing ItemMass has a rule for is listed (one scale, in one place)
        for (Component c : Component.values()) { assertFalse(kg.containsKey("hdfv:" + c.id()), c.id()); }
        for (Intermediate i : Intermediate.values()) { assertFalse(kg.containsKey("hdfv:" + i.id()), i.id()); }
        for (String id : Refining.ITEM_KG.keySet()) { assertFalse(kg.containsKey("hdfv:" + id), id); }
        for (String id : List.of("stock", "stock_bit", "part", "workpiece", "coke", "knsb_grain", "apcp_grain", "aluminium_ingot", "bauxite", "raw_garnet", "oxygen_cylinder",
                "nitrogen_cylinder", "argon_cylinder")) {
            assertFalse(kg.containsKey("hdfv:" + id), id);
        }
        assertEquals(1.0, SolidRocketDesign.GRAIN_ITEM_KG);
        // hdfv's blocks from their recipes: the storage blocks (the crate six planks and two nuggets, the locker five plates and a fastener kit...)
        assertEquals(6 * PowerPlant.PLANK.kg() + 2 * kg.get("#c:nuggets"), kg.get("hdfv:storage_crate"), 1e-9);
        assertEquals(5 * Component.STEEL_PLATE.kg + Component.FASTENER_KIT.kg, kg.get("hdfv:steel_locker"), 1e-9);
        assertEquals(6 * Component.STEEL_TUBE.kg + 2 * Component.STEEL_PLATE.kg + Component.FASTENER_KIT.kg, kg.get("hdfv:parts_rack"), 1e-9);
        assertEquals(8 * Component.STEEL_PLATE.kg + 4 * Component.STEEL_TUBE.kg + 2 * Component.FASTENER_KIT.kg, kg.get("hdfv:shipping_container"), 1e-9);
        // every hdfv block or item with a model and no rule has an entry (the fallback is for other mods' things)
        try (var items = Files.list(ASSETS.resolve("models/item"))) {
            for (Path p : items.toList()) {
                String id = p.getFileName().toString().replace(".json", "");
                if (Component.ofId(id) != null || ruled(id)) { continue; }
                assertNotNull(kg.get("hdfv:" + id), "hdfv:" + id + " has neither a rule nor a mass");
            }
        }
    }

    private static boolean ruled(String id) {
        for (Intermediate i : Intermediate.values()) { if (i.id().equals(id)) { return true; } }
        return Refining.ITEM_KG.containsKey(id) || id.endsWith("_ingot") || List.of("stock", "stock_bit", "part", "workpiece", "coke", "knsb_grain", "apcp_grain", "bauxite",
                "raw_ilmenite", "raw_nickel", "chromite", "magnesite", "raw_tin", "raw_zinc", "raw_garnet", "oxygen_cylinder", "nitrogen_cylinder", "argon_cylinder").contains(id);
    }
}
