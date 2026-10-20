package dev.mcmod.hdfv.manufacture;

import dev.mcmod.hdfv.structure.Structures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What tools/machining_assets.py made (0.17) is all there: every family's controller, member, head and accessory, the NC
 * drive and the air separation unit has its blockstate (and the models it names), item model, loot table, recipe,
 * pickaxe tag and names; every supply its item model, texture, names and (but the filled gas cylinders) recipe.
 */
class MachiningAssetsTest {
    private static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    private static final Path RES = ROOT.resolve("neoforge/src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hdfv"), DATA = RES.resolve("data");

    static List<String> blocks() {
        List<String> out = new ArrayList<>();
        for (Station s : ToolConfig.STATIONS) { if (s.family) { out.add(s.name().toLowerCase(Locale.ROOT)); } }
        for (Head h : Head.values()) { out.add(h.kind); }
        out.addAll(List.of(Structures.GRINDER_BED, Structures.FINISHER_TABLE, Structures.FINISHER_COLUMN, Structures.FINISHER_TOP, Structures.GEAR_TABLE,
                Structures.GEAR_COLUMN, Structures.GEAR_TOP, Structures.RAM_BED, Structures.SAW_BED, Structures.EDM_TANK, Structures.CUTTING_SLATS,
                Structures.THREADER_BASE, Structures.RIVETER_FRAME, Structures.NC_DRIVE, "air_separation_unit"));
        for (ToolConfig.Accessory a : ToolConfig.Accessory.values()) { out.add(a.kind); }
        return out;
    }

    @Test void everyMachiningBlockHasItsFiles() throws IOException {
        String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8), zh = Files.readString(ASSETS.resolve("lang/zh_tw.json"), StandardCharsets.UTF_8);
        String pickaxe = Files.readString(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json"));
        for (String id : blocks()) {
            Path state = ASSETS.resolve("blockstates/" + id + ".json");
            assertTrue(Files.exists(state), id + " blockstate");
            Matcher m = Pattern.compile("\"model\"\\s*:\\s*\"hdfv:block/([a-z0-9_/]+)\"").matcher(Files.readString(state));
            int models = 0;
            while (m.find()) { models++; assertTrue(Files.exists(ASSETS.resolve("models/block/" + m.group(1) + ".json")), id + " model " + m.group(1)); }
            assertTrue(models > 0, id + " names its models");
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + id + ".json")), id + " item model");
            assertTrue(Files.exists(DATA.resolve("hdfv/loot_table/blocks/" + id + ".json")), id + " loot table");
            assertTrue(Files.exists(DATA.resolve("hdfv/recipe/" + id + ".json")), id + " recipe");
            assertTrue(pickaxe.contains("\"hdfv:" + id + "\""), id + " is mined with a pickaxe");
            assertTrue(en.contains("\"block.hdfv." + id + "\"") && zh.contains("\"block.hdfv." + id + "\""), id + " named");
        }
    }

    @Test void everySupplyHasItsFiles() throws IOException {
        String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8), zh = Files.readString(ASSETS.resolve("lang/zh_tw.json"), StandardCharsets.UTF_8);
        List<String> items = new ArrayList<>();
        for (Consumable.Supply s : Consumable.Supply.values()) { items.add(s.id); }
        items.add("empty_gas_cylinder");
        items.add("raw_garnet");
        for (String id : items) {
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + id + ".json")), id + " item model");
            assertTrue(Files.exists(ASSETS.resolve("textures/item/" + id + ".png")), id + " texture");
            assertTrue(en.contains("\"item.hdfv." + id + "\"") && zh.contains("\"item.hdfv." + id + "\""), id + " named");
            if (id.equals("raw_garnet")) { continue; }   // mined
            boolean filled = java.util.Arrays.stream(GasSupply.Product.values()).anyMatch(p -> p.supply().id.equals(id));
            assertEquals(!filled, Files.exists(DATA.resolve("hdfv/recipe/" + id + ".json")), id + (filled ? " is filled at the air separation unit" : " recipe"));
        }
        // what wears stacks to one: its recipe makes one (Minecraft refuses the recipe otherwise)
        for (Consumable.Supply s : Consumable.Supply.values()) {
            Path r = DATA.resolve("hdfv/recipe/" + s.id + ".json");
            if (s.durable && Files.exists(r)) { assertTrue(Files.readString(r).replaceAll("\\s", "").contains("\"count\":1"), s.id + " makes one"); }
        }
        for (Consumable c : Consumable.values()) { assertTrue(en.contains("\"hdfv.consumable." + c.id() + "\"") && zh.contains("\"hdfv.consumable." + c.id() + "\""), c + " named"); }
        for (String ore : List.of("garnet_ore", "deepslate_garnet_ore")) {
            assertTrue(Files.exists(ASSETS.resolve("blockstates/" + ore + ".json")) && Files.exists(DATA.resolve("hdfv/loot_table/blocks/" + ore + ".json")), ore);
        }
        assertTrue(Files.readString(DATA.resolve("hdfv/neoforge/biome_modifier/ores.json")).contains("garnet"), "garnet ore is generated");
    }

    @Test void everyProcessAndFeatureIsNamed() throws IOException {
        for (String file : List.of("en_us", "zh_tw")) {
            String lang = Files.readString(ASSETS.resolve("lang/" + file + ".json"), StandardCharsets.UTF_8);
            for (Process p : Process.values()) { assertTrue(lang.contains("\"hdfv.process." + p.name().toLowerCase(Locale.ROOT) + "\""), file + ": " + p); }
            for (Head h : Head.values()) {
                for (Head.Ability a : h.abilities()) {
                    for (Feature f : a.features()) {
                        if (f == Feature.NONE) { continue; }
                        String k = "hdfv.process." + a.process().name().toLowerCase(Locale.ROOT) + "." + f.name().toLowerCase(Locale.ROOT);
                        assertTrue(lang.contains("\"" + k + "\""), file + ": " + k + " (" + h + ")");
                    }
                }
            }
            // and every step of the samples' routes (the machine screen names it so)
            for (var e : RouteSnapshot.designs().entrySet()) {
                for (Workpiece w : Plans.of(e.getValue()).workpieces()) {
                    for (Operation op : w.route()) {
                        if (op.feature() == Feature.NONE) { continue; }
                        String k = "hdfv.process." + op.process().name().toLowerCase(Locale.ROOT) + "." + op.feature().name().toLowerCase(Locale.ROOT);
                        assertTrue(lang.contains("\"" + k + "\""), file + ": " + k + " (" + e.getKey() + " " + w.key() + ")");
                    }
                }
            }
        }
    }
}
