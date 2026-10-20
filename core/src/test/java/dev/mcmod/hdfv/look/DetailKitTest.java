package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Finish;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.MeshOps;
import dev.mcmod.aerocore.api.mesh.ObjReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The detail kit made in Blender (0.11, tools/blender/details.py): every part reads with AeroCore's OBJ reader, has
 * a fine (lod0) and a coarser (lod1) object, only quads and triangles, materials that are hdfv finishes, normals of
 * unit length that agree with the winding, stands on y = 0 within a sensible size, and keeps to a face budget (a
 * fastener's coarse object at most 12 faces). 0.12: the vehicle kit (tools/blender/vehicle.py) and the interior kit
 * (interior.py) are held to the same, each with its own budget and the parts that hang from a pivot rather than stand.
 */
class DetailKitTest {
    private static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    private static final Path MODELS = ROOT.resolve("neoforge/src/main/resources/assets/hdfv/models/part");

    @Test void everyDetailPartReadsAndKeepsToItsBudget() throws IOException {
        check("detail", 20, 400, 1.3, List.of("actuator", "rod_end", "nozzle", "hinge"));
    }

    @Test void everyVehicleKitPartReadsAndKeepsToItsBudget() throws IOException {
        // a damper's body and rod hang from their eyes; a fuel flap's shut line is let into the panel
        check("vehicle", 16, 400, 1.3, List.of("damper_", "fuel_flap"));
    }

    @Test void everyInteriorKitPartReadsAndKeepsToItsBudget() throws IOException {
        // wheels and the yoke stand off the dash on their columns, a lever turns about its pivot, a gauge is let in
        check("interior", 15, 600, 2.0, List.of("steering_wheel", "yoke", "helm_wheel", "collective", "gauge"));
        // an airliner's seats are drawn by the dozen
        ObjReader.Result cabin = read(MODELS.resolve("interior/cabin_seat.obj"), "cabin_seat.obj");
        assertTrue(cabin.group("lod0").quads().size() <= 120 && cabin.group("lod1").quads().size() <= 40, "a cabin seat keeps to few faces");
    }

    @Test void everyMachineKitPartReadsAndKeepsToItsBudget() throws IOException {
        // 0.17 (machining_kit.py): what turns is centred on its axis, what hangs from a ram or a head hangs below it
        check("machine", 51, 800, 2.6, List.of("band_saw_bow", "broach", "buff_wheel", "circular_saw_blade", "cold_saw_head", "drill_rivet_head",
                "edm_quill", "edm_ram", "gear_work", "grinding_wheel", "hacksaw_frame", "hob", "hole_edm_head", "honing_tool", "lapping_plate",
                "laser_head", "plasma_torch", "regulating_wheel", "rivet_gun", "rivet_ram", "shaper_ram", "slotter_ram", "thread_roll", "waterjet_head"));
    }

    @Test void everyAirfieldKitPartReadsAndKeepsToItsBudget() throws IOException {
        // 0.20 (airfield_kit.py): the windsock's hoop hangs below its swivel; the radar antenna is 4 m wide, the mast 4.6 m tall
        check("airfield", 15, 800, 5.0, List.of("windsock_head", "entrance_canopy", "ceiling_light"));
    }

    private static ObjReader.Result read(Path obj, String name) throws IOException {
        return ObjReader.read(Files.readString(obj), material -> {
            Finish f = Finishes.ALL.stream().filter(x -> x.id().equals("hdfv:" + material)).findFirst()
                    .orElseThrow(() -> new AssertionError(name + ": " + material + " is not a finish"));
            return new ObjReader.Material(f, 0xffffffff);
        }, 1);
    }

    /** Every part of a kit's folder: read, budgeted (fine faces), sized (largest diagonal), standing on y = 0 unless named. */
    private static void check(String folder, int count, int budget, double maxSize, List<String> notStanding) throws IOException {
        Path kit = MODELS.resolve(folder);
        List<Path> parts;
        try (Stream<Path> s = Files.list(kit)) { parts = s.filter(p -> p.toString().endsWith(".obj")).sorted().toList(); }
        assertTrue(parts.size() >= count, "the " + folder + " kit has its parts: " + parts.size());
        for (Path obj : parts) {
            String name = obj.getFileName().toString();
            assertTrue(Files.exists(kit.resolve(name.replace(".obj", ".mtl"))), name + " has its material library");
            ObjReader.Result r = read(obj, name);
            assertEquals(0, r.polygons(), name + " has faces of more than four corners");
            assertFalse(r.vertexColours() || r.missingMaterial(), name);
            int fine = r.group("lod0").quads().size(), coarse = r.group("lod1").quads().size();
            assertTrue(fine > 0 && coarse > 0 && coarse <= fine, name + " lod0 " + fine + ", lod1 " + coarse);
            assertTrue(fine <= budget, name + " over budget: " + fine);
            if (List.of("hex_bolt.obj", "hex_nut.obj", "socket_cap.obj", "rivet.obj").contains(name)) {
                // fasteners go round flanges by the hundred: their coarse object is a bare head
                assertTrue(coarse <= 12, name + " coarse over budget: " + coarse);
            }
            Vec3d[] b = r.group("lod0").bounds();
            double size = b[1].subtract(b[0]).length();
            assertTrue(size > 0.003 && size < maxSize, name + " size " + size);
            assertTrue(b[0].y() <= 1e-6 || notStanding.stream().anyMatch(name::startsWith), name + " stands on y = 0 (bottom " + b[0].y() + ")");
            int agree = 0, total = 0;
            for (MeshBuilder.Quad q : r.mesh().quads()) {
                for (int c = 0; c < 4; c++) { assertEquals(1, q.normalAt(c).length(), 1e-3, name + " normal length"); }
                Vec3d face = MeshOps.faceNormal(q);
                if (face.lengthSquared() == 0) { continue; }
                total++;
                if (face.dot(q.normalAt(0).add(q.normalAt(1)).add(q.normalAt(2)).add(q.normalAt(3))) > 0) { agree++; }
            }
            assertTrue(agree >= 0.97 * total, name + ": winding and normals disagree on " + (total - agree) + " of " + total + " faces");
        }
    }
}
