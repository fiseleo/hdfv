package dev.mcmod.hdfv.road;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: the road ramps. Their steps (RampShape) are the slope at each step's middle, rise evenly and join from one
 * part to the next; and what tools/road_assets.py and tools/recipes.py made is all
 * there: every ramp's blockstate (each facing and part, the wedge of that part), the wedges themselves (inside the
 * block, their high edge where the part ends, texture coordinates inside the texture), item model, loot table,
 * recipe, tool tag, AeroCore's smooth ground and names in the three languages.
 */
class RoadAssetsTest {
    private static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    private static final Path RES = ROOT.resolve("neoforge/src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/hdfv"), DATA = RES.resolve("data");

    @Test void theStepsFollowTheSlope() {
        assertEquals(List.of("asphalt_ramp_2", "asphalt_ramp_4", "asphalt_ramp_8", "concrete_ramp_2", "concrete_ramp_4", "concrete_ramp_8"), RampShape.ids());
        for (int gradient : RampShape.GRADIENTS) {
            double rise = 1.0 / (RampShape.STEPS * gradient), before = -rise / 2;
            for (int part = 0; part < gradient; part++) {
                double[] steps = RampShape.steps(gradient, part);
                assertEquals(RampShape.STEPS, steps.length);
                for (int i = 0; i < steps.length; i++) {
                    // under the slope at the step's high edge, over it at its low edge, by half a step's rise
                    assertEquals(RampShape.height(gradient, part, (i + 1.0) / RampShape.STEPS) - rise / 2, steps[i], 1e-12, "1:" + gradient + " part " + part + " step " + i);
                    assertEquals(rise, steps[i] - before, 1e-12, "1:" + gradient + " part " + part + " step " + i + " rises evenly");
                    before = steps[i];
                }
            }
            // the last step is half a rise under the block above: the run ends a block up
            assertEquals(1.0 - rise / 2, before, 1e-12, "1:" + gradient + " ends a block up");
            assertEquals(1.0, RampShape.height(gradient, gradient - 1, 1), 1e-12);
            // a step is at most a 32nd of a block high (1:2): well under a 1/16 m riser, which is a kerb to a wheel
            assertTrue(rise <= 1.0 / 32, "1:" + gradient + " rises " + rise + " a step");
            assertThrows(IllegalArgumentException.class, () -> RampShape.steps(gradient, gradient));
        }
    }

    @Test void everyRampHasItsFiles() throws IOException {
        String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8), zh = Files.readString(ASSETS.resolve("lang/zh_tw.json"), StandardCharsets.UTF_8),
                cn = Files.readString(ASSETS.resolve("lang/zh_cn.json"), StandardCharsets.UTF_8);
        String pickaxe = Files.readString(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json")), smooth = Files.readString(DATA.resolve("aerocore/tags/block/smooth.json"));
        for (String material : RampShape.MATERIALS) {
            String texture = material.equals("asphalt") ? "hdfv:block/asphalt" : "hdfv:block/apron_concrete";
            assertTrue(Files.exists(ASSETS.resolve("textures/" + texture.substring(5) + ".png")), texture);
            for (int gradient : RampShape.GRADIENTS) {
                String id = RampShape.id(material, gradient);
                String state = Files.readString(ASSETS.resolve("blockstates/" + id + ".json"));
                for (int part = 0; part < gradient; part++) {
                    for (String facing : List.of("north", "east", "south", "west")) {
                        int at = state.indexOf("\"facing=" + facing + ",part=" + part + "\"");
                        assertTrue(at >= 0, id + " facing " + facing + " part " + part);
                        String entry = state.substring(at, state.indexOf('}', at));
                        assertTrue(entry.contains("\"hdfv:block/" + id + "_" + part + "\""), id + " " + facing + " part " + part + " draws its part: " + entry);
                        // the wedge rises toward north as modelled; the others are it turned about y
                        int y = switch (facing) { case "east" -> 90; case "south" -> 180; case "west" -> 270; default -> 0; };
                        assertEquals(y != 0, entry.contains("\"y\": " + y), id + " " + facing + ": " + entry);
                    }
                    String model = Files.readString(ASSETS.resolve("models/block/" + id + "_" + part + ".json"));
                    assertTrue(model.contains("\"neoforge:obj\"") && model.contains("\"hdfv:models/block/ramp/ramp_" + gradient + "_" + part + ".obj\""), id + " part " + part + ": " + model);
                    assertTrue(model.contains("\"surface\": \"" + texture + "\"") && model.contains("\"particle\": \"" + texture + "\""), id + " part " + part + " textures");
                }
                assertTrue(Files.readString(ASSETS.resolve("models/item/" + id + ".json")).contains("\"hdfv:block/" + id + "_"), id + " item model");
                assertTrue(Files.readString(DATA.resolve("hdfv/loot_table/blocks/" + id + ".json")).contains("\"hdfv:" + id + "\""), id + " drops itself");
                String recipe = Files.readString(DATA.resolve("hdfv/recipe/" + id + ".json"));
                assertTrue(recipe.contains(material.equals("asphalt") ? "\"hdfv:asphalt\"" : "\"hdfv:apron_concrete\""), id + " is made of its pavement: " + recipe);
                assertTrue(pickaxe.contains("\"hdfv:" + id + "\""), id + " has its tool");
                assertTrue(smooth.contains("\"hdfv:" + id + "\""), id + " is smooth ground to the wind");
                for (String lang : List.of(en, zh, cn)) { assertTrue(lang.contains("\"block.hdfv." + id + "\""), id + " named"); }
            }
        }
        for (String lang : List.of(en, zh, cn)) { assertTrue(lang.contains("\"hdfv.jei.road_ramp\""), "the ramps' JEI line"); }
    }

    @Test void theWedgesAreTheSlope() throws IOException {
        assertTrue(Files.readString(ASSETS.resolve("models/block/ramp/ramp.mtl")).contains("map_Kd #surface"));
        for (int gradient : RampShape.GRADIENTS) {
            for (int part = 0; part < gradient; part++) {
                double low = RampShape.height(gradient, part, 0), high = RampShape.height(gradient, part, 1), top = 0, north = 0, south = 0;
                int faces = 0;
                for (String line : Files.readAllLines(ASSETS.resolve("models/block/ramp/ramp_" + gradient + "_" + part + ".obj"))) {
                    String[] f = line.trim().split("\\s+");
                    switch (f[0]) {
                        case "v" -> {
                            double x = Double.parseDouble(f[1]), y = Double.parseDouble(f[2]), z = Double.parseDouble(f[3]);
                            assertTrue(x >= 0 && x <= 1 && y >= 0 && y <= 1 && z >= 0 && z <= 1, "inside the block: " + line);
                            // under the slope everywhere
                            assertTrue(y <= low + (high - low) * (1 - z) + 1e-6, "1:" + gradient + " part " + part + " above its slope: " + line);
                            top = Math.max(top, y);
                            if (z == 0) { north = Math.max(north, y); }
                            if (z == 1) { south = Math.max(south, y); }
                        }
                        case "vt" -> { for (int i = 1; i <= 2; i++) { double c = Double.parseDouble(f[i]); assertTrue(c >= 0 && c <= 1, "inside the texture: " + line); } }
                        case "f" -> { faces++; assertTrue(f.length == 4 || f.length == 5, line); }
                        case "usemtl" -> assertEquals("surface", f[1]);
                        default -> { }
                    }
                }
                assertEquals(high, top, 1e-6, "1:" + gradient + " part " + part + " top");
                assertEquals(high, north, 1e-6, "the high edge to the north (the way the block faces)");
                assertEquals(low, south, 1e-6, "the low edge to the south");
                assertEquals(part == 0 ? 5 : 6, faces, "the slope, the bottom, the high end, the sides (and the low end from part 1 on)");
            }
        }
    }
}
