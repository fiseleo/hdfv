package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.vehicle.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.12: the machines' OBJ block models (tools/blender/machines.py): each block model names an OBJ that exists, every
 * material of the OBJ's library is one of the model's textures and that texture is a 128 px PNG (0.22; 64 before), texture coordinates
 * stay inside the texture (a block's texture is a sprite in the atlas), the vertices inside the block (a little over
 * for flanges and handles), at most 800 faces, only triangles and quads.
 */
class BlockModelTest {
    private static final Path ASSETS = Path.of("..", "neoforge", "src", "main", "resources", "assets", "hdfv");
    private static final int MAX_FACES = 800;

    @Test void everyMachineModelIsWhole() throws IOException {
        List<Path> objs;
        try (Stream<Path> s = Files.list(ASSETS.resolve("models/block/machine"))) { objs = s.filter(p -> p.toString().endsWith(".obj")).sorted().toList(); }
        assertTrue(objs.size() >= 24, "machine models: " + objs.size());
        StringBuilder report = new StringBuilder();
        for (Path obj : objs) {
            String name = obj.getFileName().toString().replace(".obj", "");
            @SuppressWarnings("unchecked") Map<String, Object> model = (Map<String, Object>) Json.parse(Files.readString(ASSETS.resolve("models/block/" + name + ".json")));
            assertEquals("neoforge:obj", model.get("loader"), name);
            assertEquals("hdfv:models/block/machine/" + name + ".obj", model.get("model"), name);
            @SuppressWarnings("unchecked") Map<String, Object> textures = (Map<String, Object>) model.get("textures");
            Set<String> materials = new LinkedHashSet<>();
            for (String line : Files.readAllLines(obj.resolveSibling(name + ".mtl"))) {
                if (line.startsWith("newmtl ")) { materials.add(line.substring(7).trim()); }
                if (line.startsWith("map_Kd ")) { assertTrue(line.substring(7).trim().startsWith("#"), name + ": " + line); }
            }
            assertFalse(materials.isEmpty(), name);
            for (String m : materials) {
                Object t = textures.get(m);
                assertNotNull(t, name + " lacks the texture " + m);
                Path png = ASSETS.resolve("textures/" + t.toString().substring("hdfv:".length()) + ".png");
                assertTrue(Files.exists(png), png.toString());
                byte[] head = Files.readAllBytes(png);
                int w = ((head[16] & 255) << 24) | ((head[17] & 255) << 16) | ((head[18] & 255) << 8) | (head[19] & 255);
                int h = ((head[20] & 255) << 24) | ((head[21] & 255) << 16) | ((head[22] & 255) << 8) | (head[23] & 255);
                assertTrue(w == 128 && h == 128, png + ": " + w + " x " + h);
            }
            int faces = 0;
            List<String> used = new ArrayList<>();
            for (String line : Files.readAllLines(obj)) {
                String[] f = line.trim().split("\\s+");
                switch (f[0]) {
                    case "v" -> {
                        for (int i = 1; i <= 3; i++) {
                            double c = Double.parseDouble(f[i]);
                            assertTrue(c > -0.2 && c < 1.2, name + ": a vertex outside its block " + line);
                        }
                    }
                    case "vt" -> {
                        for (int i = 1; i <= 2; i++) {
                            double c = Double.parseDouble(f[i]);
                            assertTrue(c >= 0 && c <= 1, name + ": texture coordinates outside the texture " + line);
                        }
                    }
                    case "f" -> { faces++; assertTrue(f.length == 4 || f.length == 5, name + ": " + line); }
                    case "usemtl" -> used.add(f[1]);
                    default -> { }
                }
            }
            assertTrue(materials.containsAll(used), name + " uses " + used + " of " + materials);
            assertTrue(faces > 0 && faces <= MAX_FACES, name + ": " + faces + " faces");
            report.append(String.format("%n%s: %d faces, %d materials", name, faces, materials.size()));
        }
        System.out.println(report);
    }
}
