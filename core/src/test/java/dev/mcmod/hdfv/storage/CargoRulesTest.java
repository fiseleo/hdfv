package dev.mcmod.hdfv.storage;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.25: vehicle cargo's rules (CargoRules) and a body's holds: every module with a hold's volume is a Part.Hold of
 * that volume at the middle of the box BodyModel draws for it; slots from the volume; the ratings; and no double
 * counting: the compiled body has no design cargo in its mass (its holds carry what is loaded), while the design's
 * laden figures (mass(), the bench, the structure's check) still have it.
 */
class CargoRulesTest {
    static { HdfvParts.register(); }

    @Test void slotsFollowTheVolume() {
        assertEquals(0, CargoRules.slots(0, CargoRules.MAX_SLOTS));
        assertEquals(0, CargoRules.slots(Double.NaN, CargoRules.MAX_SLOTS));
        assertEquals(9, CargoRules.slots(0.1, CargoRules.MAX_SLOTS), "a small hold is still a row");
        assertEquals(12, CargoRules.slots(0.45, CargoRules.MAX_SLOTS), "a saloon's boot");
        assertEquals(27, CargoRules.slots(1, CargoRules.MAX_SLOTS), "a cubic metre is a chest");
        assertEquals(32, CargoRules.slots(1.2, CargoRules.MAX_SLOTS));
        assertEquals(216, CargoRules.slots(10, CargoRules.MAX_SLOTS), "four double chests at most");
        assertEquals(324, CargoRules.slots(33, CargoRules.MAX_CONTAINER_SLOTS), "a container's six pages");
        assertEquals("m5_cargo", CargoRules.holdName(4));
        assertEquals(4, CargoRules.moduleOf("m5_cargo"));
        assertEquals(-1, CargoRules.moduleOf("hold"));
        assertEquals(-1, CargoRules.moduleOf("mx_cargo"));
        assertTrue(CargoRules.overloaded(91, 90));
        assertFalse(CargoRules.overloaded(90, 90));
        assertFalse(CargoRules.overloaded(1e6, 0), "no rating, never over");
    }

    @Test void aBodysHoldsAreItsModulesHoldsWhereTheyAreDrawn() {
        BodyDesign sedan = SampleBodies.sedan();
        BodyShape shape = sedan.shape();
        List<Part.Hold> holds = sedan.holds(shape);
        assertEquals(1, holds.size());
        Part.Hold boot = holds.get(0);
        assertEquals("m5_cargo", boot.name());
        assertEquals(0.45, boot.capacityM3(), 1e-12);
        Vec3d[] box = sedan.cargoBox(shape, 4);
        assertEquals(box[0].add(box[1]).scale(0.5), boot.centre());
        assertTrue(boot.centre().x() > shape.moduleRearX(4) && boot.centre().x() < shape.moduleFrontX(4), "in the trunk along: " + boot.centre());
        assertTrue(boot.centre().y() > shape.bottomAt(boot.centre().x()) && boot.centre().y() < shape.topAt(boot.centre().x()), "in the trunk up: " + boot.centre());
        assertEquals(0, boot.centre().z(), 1e-12);
        double drawn = (box[1].x() - box[0].x()) * (box[1].y() - box[0].y()) * (box[1].z() - box[0].z());
        assertTrue(drawn <= 0.45 + 1e-9, "the drawn box is never more than the hold: " + drawn);
        // the compiled body carries them; other modules have none
        assertEquals(holds, sedan.compile().holds());
        assertTrue(sedan.cargoBox(shape, 2) == null);
        assertEquals(List.of("m4_cargo"), SampleBodies.helicopterFuselage().holds(SampleBodies.helicopterFuselage().shape()).stream().map(Part.Hold::name).toList());
        assertTrue(SampleBodies.lowLoaderDeck().compile().holds().isEmpty(), "a flat deck has no hold (a storage box goes on it)");
        assertTrue(SampleBodies.seaplaneFuselage().compile().holds().isEmpty());
        // in a vehicle: "body.m5_cargo" at the boot
        var v = SampleBlueprints.sedan().compile();
        assertEquals(1, v.holds().size());
        assertEquals("body.m5_cargo", v.holds().get(0).id());
        // the ratings: full at the design's cargo density
        assertEquals(0.45 * 200, sedan.holdLimitKg(4), 1e-9);
        assertEquals(90, CargoRules.limitKg(sedan, boot), 1e-9);
        assertEquals(90, sedan.payloadKg(), 1e-9);
        assertEquals(0, sedan.holdLimitKg(0), 1e-12);
    }

    @Test void theDesignCargoIsNotInTheCompiledBody() {
        for (BodyDesign b : List.of(SampleBodies.sedan(), SampleBodies.hatchback(), SampleBodies.helicopterFuselage(), SampleBodies.lowLoaderDeck())) {
            double cargo = 0, fuel = 0;
            for (int i = 0; i < b.modules().size(); i++) { cargo += b.cargoKg(i); fuel += b.fuelKg(i); }
            assertEquals(b.mass().massKg() - fuel - cargo, b.compile().mass().massKg(), 1e-6, b.name() + ": the compiled body is dry and empty");
            assertEquals(b.dryMass().massKg(), b.compile().mass().massKg(), 1e-9, b.name());
        }
        // the sedan: its 0.45 m3 boot half full at 200 kg/m3 was 45 kg of its body until 0.24; the design's laden mass keeps it
        BodyDesign sedan = SampleBodies.sedan();
        assertEquals(45, sedan.cargoKg(4), 1e-9);
        Map<String, Double> f = new java.util.HashMap<>();
        for (Designs.Figure x : Designs.figures(sedan)) { f.put(x.key(), x.value()); }
        assertEquals(sedan.mass().massKg(), f.get("loadedMass"), 1e-9, "the laden figure still has the design cargo");
        assertEquals(0.45, f.get("holdVolume"), 1e-12);
        assertEquals(12, f.get("holdSlots"), 1e-12);
        assertEquals(90, f.get("designPayload"), 1e-9);
        var bench = dev.mcmod.hdfv.design.Bench.body(sedan, 30);
        assertEquals(sedan.mass().massKg(), bench.massKg(), 1e-6, "the bench weighs it laden");
        // no hold, no hold figures
        assertFalse(Designs.figures(SampleBodies.lowLoaderDeck()).stream().anyMatch(x -> x.key().equals("holdVolume")));
    }

    private static final java.nio.file.Path ROOT = java.nio.file.Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    private static final java.nio.file.Path ASSETS = ROOT.resolve("neoforge/src/main/resources/assets/hdfv"), DATA = ROOT.resolve("neoforge/src/main/resources/data");

    /** What tools/cargo_assets.py, tools/recipes.py and tools/item_mass.py made for the cargo loader, and the cargo screens' names. */
    @Test void theCargoLoaderHasItsFiles() throws java.io.IOException {
        String id = "cargo_loader";
        String state = java.nio.file.Files.readString(ASSETS.resolve("blockstates/" + id + ".json"));
        for (String facing : List.of("north", "east", "south", "west")) { assertTrue(state.contains("\"facing=" + facing + "\""), facing); }
        String model = java.nio.file.Files.readString(ASSETS.resolve("models/block/" + id + ".json"));
        assertTrue(model.contains("\"minecraft:block/orientable\""));
        for (String face : List.of("front", "side", "top")) {
            assertTrue(java.nio.file.Files.exists(ASSETS.resolve("textures/block/" + id + "_" + face + ".png")), face);
            assertTrue(java.nio.file.Files.exists(ROOT.resolve("tools/blender/blocks16/" + id + "_" + face + ".png")), face + "'s 16 px source");
        }
        assertTrue(java.nio.file.Files.exists(ASSETS.resolve("models/item/" + id + ".json")));
        assertTrue(java.nio.file.Files.readString(DATA.resolve("hdfv/loot_table/blocks/" + id + ".json")).contains("\"hdfv:" + id + "\""));
        assertTrue(java.nio.file.Files.readString(DATA.resolve("minecraft/tags/block/mineable/pickaxe.json")).contains("\"hdfv:" + id + "\""));
        String recipe = java.nio.file.Files.readString(DATA.resolve("hdfv/recipe/" + id + ".json"));
        assertTrue(recipe.contains("\"hdfv:electric_motor_unit\"") && recipe.contains("\"minecraft:hopper\""), recipe);
        assertTrue(java.nio.file.Files.readString(DATA.resolve("hdfv/data_maps/item/item_mass.json")).contains("\"hdfv:" + id + "\""), "a mass");
        for (String lang : List.of("en_us", "zh_tw", "zh_cn")) {
            String text = java.nio.file.Files.readString(ASSETS.resolve("lang/" + lang + ".json"), java.nio.charset.StandardCharsets.UTF_8);
            for (String key : List.of("block.hdfv." + id, "hdfv.jei." + id, "hdfv.cargo.title", "hdfv.cargo.too_far", "hdfv.cargo.no_hold", "hdfv.cargo.unavailable",
                    "hdfv.cargo.tab.none", "hdfv.cargo.tab.slots", "hdfv.cargo.tab.slots_unknown", "hdfv.cargo.tab.mass", "hdfv.cargo.tab.mass_limit", "hdfv.cargo.tab.open",
                    "hdfv.cargo_loader.none", "hdfv.cargo_loader.serving", "hdfv.status.tab.cargo", "hdfv.derived.roofLoadKg")) {
                assertTrue(text.contains("\"" + key + "\""), lang + " " + key);
            }
        }
    }
}
