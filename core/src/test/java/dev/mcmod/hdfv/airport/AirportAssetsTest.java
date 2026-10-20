package dev.mcmod.hdfv.airport;

import dev.mcmod.hdfv.structure.Structures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What tools/airport_assets.py and tools/blender/airfield_blocks.py made (0.20) is all there: every airfield block has
 * its blockstate (and the models it names), item model, loot table, recipe, tool tag and names; the paintable
 * pavement's quarters cover every paint and mask; the line paints have their icons; the world generation's files, the
 * biomes airports stand on, AeroCore's smooth ground and the controller villager's job site are in place; both
 * language files keep their keys on the same lines.
 */
class AirportAssetsTest {
    private static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    private static final Path RES = ROOT.resolve("neoforge/src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hdfv"), DATA = RES.resolve("data");

    static List<String> blocks() {
        List<String> out = new ArrayList<>(List.of(Structures.TOWER, Structures.TOWER_BASE, Structures.TOWER_SHAFT, Structures.TOWER_FLOOR, Structures.TOWER_GLASS,
                Structures.TOWER_ROOF, Structures.TOWER_CONSOLE, Structures.TOWER_RADAR, Structures.TOWER_MAST));
        for (Fixture.Kind k : Fixture.Kind.values()) { out.add(k.id()); }
        out.addAll(List.of("apron_concrete", "airfield_turf", "windsock", "holding_sign", "apron_floodlight", "asphalt"));
        return out;
    }

    @Test void everyAirfieldBlockHasItsFiles() throws IOException {
        String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8), zh = Files.readString(ASSETS.resolve("lang/zh_tw.json"), StandardCharsets.UTF_8);
        String pickaxe = Files.readString(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json")), shovel = Files.readString(DATA.resolve("minecraft/tags/block/mineable/shovel.json"));
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
            assertTrue((id.equals("airfield_turf") ? shovel : pickaxe).contains("\"hdfv:" + id + "\""), id + " has its tool");
            assertTrue(en.contains("\"block.hdfv." + id + "\"") && zh.contains("\"block.hdfv." + id + "\""), id + " named");
        }
        for (String id : List.of("white_line_paint", "yellow_line_paint", "threshold_paint")) {
            assertTrue(Files.exists(ASSETS.resolve("models/item/" + id + ".json")) && Files.exists(ASSETS.resolve("textures/item/" + id + ".png")), id + " icon");
            assertTrue(Files.exists(DATA.resolve("hdfv/recipe/" + id + ".json")), id + " recipe");
            assertTrue(en.contains("\"item.hdfv." + id + "\"") && zh.contains("\"item.hdfv." + id + "\""), id + " named");
        }
    }

    @Test void thePavementsQuartersCoverEveryPaintAndMask() throws IOException {
        for (String id : List.of("asphalt", "apron_concrete")) {
            String state = Files.readString(ASSETS.resolve("blockstates/" + id + ".json"));
            for (int q = 0; q < 4; q++) {
                for (String suffix : List.of("", "_white", "_yellow")) {
                    assertTrue(state.contains("\"hdfv:block/" + id + "_q" + q + suffix + "\""), id + " quarter " + q + suffix);
                }
            }
            // each quarter: plain when unpainted or its bit clear; painted white (white or threshold) or yellow when its bit is set
            for (int q = 0; q < 4; q++) {
                assertTrue(state.contains("\"mask\": \"" + with(q) + "\""), id + ": quarter " + q + " painted for the masks with its bit");
                assertTrue(state.contains("\"mask\": \"" + without(q) + "\""), id + ": quarter " + q + " plain for the masks without it");
            }
            assertTrue(state.contains("\"paint\": \"none\"") && state.contains("\"paint\": \"white|threshold\"") && state.contains("\"paint\": \"yellow\""), id + " paints");
        }
    }

    private static String with(int q) {
        StringBuilder s = new StringBuilder();
        for (int m = 0; m < 16; m++) { if ((m >> q & 1) != 0) { s.append(s.isEmpty() ? "" : "|").append(m); } }
        return s.toString();
    }

    private static String without(int q) {
        StringBuilder s = new StringBuilder();
        for (int m = 0; m < 16; m++) { if ((m >> q & 1) == 0) { s.append(s.isEmpty() ? "" : "|").append(m); } }
        return s.toString();
    }

    @Test void worldGenerationAndTagsAreInPlace() throws IOException {
        assertTrue(Files.readString(DATA.resolve("hdfv/worldgen/configured_feature/airport.json")).contains("\"hdfv:airport\""), "the configured feature");
        assertTrue(Files.readString(DATA.resolve("hdfv/worldgen/placed_feature/airport.json")).contains("\"hdfv:airport\""), "the placed feature");
        String modifier = Files.readString(DATA.resolve("hdfv/neoforge/biome_modifier/airport.json"));
        assertTrue(modifier.contains("#minecraft:is_overworld") && modifier.contains("top_layer_modification"), "added to every overworld biome, after the trees");
        String biomes = Files.readString(DATA.resolve("hdfv/tags/worldgen/biome/has_airport.json"));
        for (String b : List.of("plains", "savanna", "desert", "snowy_plains")) { assertTrue(biomes.contains("\"minecraft:" + b + "\""), "airports in " + b); }
        String smooth = Files.readString(DATA.resolve("aerocore/tags/block/smooth.json"));
        assertTrue(smooth.contains("\"hdfv:asphalt\"") && smooth.contains("\"hdfv:apron_concrete\""), "pavement is smooth ground for the wind");
        assertTrue(Files.readString(DATA.resolve("minecraft/tags/point_of_interest_type/acquirable_job_site.json")).contains("\"hdfv:tower_controller\""), "the controller's job site");
        for (String f : List.of("villager", "zombie_villager")) {
            assertTrue(Files.exists(ASSETS.resolve("textures/entity/" + f + "/profession/tower_controller.png")), "the controller's clothes as a " + f);
        }
    }

    @Test void theLanguageFilesKeepTheTowersKeysOnTheSameLines() throws IOException {
        List<String> en = Files.readAllLines(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8), zh = Files.readAllLines(ASSETS.resolve("lang/zh_tw.json"), StandardCharsets.UTF_8);
        assertEquals(en.size(), zh.size(), "as many lines");
        Pattern key = Pattern.compile("^\\s*\"([^\"]+)\":");
        for (int i = 0; i < en.size(); i++) {
            Matcher a = key.matcher(en.get(i)), b = key.matcher(zh.get(i));
            if (!a.find() || !b.find()) { continue; }
            String k = a.group(1);
            if (k.startsWith("hdfv.atc.") || k.startsWith("hdfv.tower.") || k.contains("airfield") || k.contains("tower") || k.contains("airport")) {
                assertEquals(k, b.group(1), "line " + (i + 1));
            }
        }
    }
}
