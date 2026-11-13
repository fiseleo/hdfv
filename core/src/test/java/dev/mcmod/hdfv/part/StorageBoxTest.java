package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.RoadAero;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartTypes;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.Placing;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.StructureWork;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyKitDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.storage.CargoRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.25: the storage box part (StorageBoxDesign): the three samples' volumes, tares and ratings against the real
 * things, its checks, its compiled part (one hold of its inside, its mass, its condition, its drag), its manufacture
 * (the routes' masses add up to its tare; a container is built in the room), and where an assembly room puts one.
 */
class StorageBoxTest {
    static { HdfvParts.register(); }

    private static boolean invalid(StorageBoxDesign d) { return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID); }

    private static boolean extrapolated(StorageBoxDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION && i.parameter().equals(parameter));
    }

    @Test void theSamplesAreWhatTheirRealOnesAre() {
        StorageBoxDesign roof = StorageBoxDesign.roofBox(), bed = StorageBoxDesign.bedBox(), box = StorageBoxDesign.container20();
        for (StorageBoxDesign d : List.of(roof, bed, box)) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            System.out.printf(java.util.Locale.ROOT, "hdfv storage box %s: %.1f l, %.1f kg tare, rated %.1f kg, CdA %.3f m2%n", d.name(), d.capacityM3() * 1000, d.massKg(),
                    d.ratedLoadKg(), d.frontalAreaM2() * d.dragCoefficient());
        }
        // a 450 l roof box of some 20-30 kg (Thule Motion XT L: 450 l, 19 kg; glass fibre is heavier than ABS), rated for what the roof leaves
        assertEquals(0.45, roof.capacityM3(), 0.02, "roof box volume");
        assertTrue(roof.massKg() > 20 && roof.massKg() < 32, "roof box tare " + roof.massKg());
        assertEquals(StorageBoxDesign.ROOF_LOAD_KG - roof.massKg(), roof.ratedLoadKg(), 1e-9, "the roof's 75 kg less the box");
        // a crossover bed box: about 0.39 m3, 35-50 kg of steel, rated for some hundreds of kg on its ribbed floor
        assertEquals(0.37, bed.capacityM3(), 0.03, "bed box volume");
        assertTrue(bed.massKg() > 35 && bed.massKg() < 50, "bed box tare " + bed.massKg());
        assertTrue(bed.ratedLoadKg() > 200 && bed.ratedLoadKg() < 600, "bed box rating " + bed.ratedLoadKg());
        // ISO 668 1CC: 33.2 m3 inside, about 2.3 t tare, 30 480 kg gross
        assertEquals(33.2, box.capacityM3(), 0.1, "container volume");
        assertTrue(box.massKg() > 2200 && box.massKg() < 2500, "container tare " + box.massKg());
        assertEquals(30480 - box.massKg(), box.ratedLoadKg(), 1e-6, "container payload");
        // their slots: a roof box a dozen, a bed box ten, the container capped at the container's six pages
        assertEquals(12, CargoRules.slots(roof.capacityM3(), CargoRules.maxSlots(roof)));
        assertEquals(10, CargoRules.slots(bed.capacityM3(), CargoRules.maxSlots(bed)));
        assertEquals(CargoRules.MAX_CONTAINER_SLOTS, CargoRules.slots(box.capacityM3(), CargoRules.maxSlots(box)));
        // the design screen's figures and its kind
        Map<String, Double> f = new java.util.HashMap<>();
        for (Designs.Figure x : Designs.figures(box)) { f.put(x.key(), x.value()); }
        assertEquals(box.capacityM3() * 1000, f.get("capacity"), 1e-6);
        assertEquals(324, f.get("holdSlots"), 1e-9);
        assertEquals(box.ratedLoadKg(), f.get("ratedLoad"), 1e-9);
        assertEquals("storage_box", Designs.kindOf(bed).id());
        assertEquals(Designs.Category.STORAGE, Designs.kind("storage_box").category());
    }

    @Test void itsChecksStopWhatIsNoBox() {
        StorageBoxDesign roof = StorageBoxDesign.roofBox();
        assertTrue(invalid(RecordEditor.with(roof, "wallM", 0.0)));
        assertTrue(invalid(RecordEditor.with(roof, "heightM", -1.0)));
        assertTrue(invalid(RecordEditor.with(roof, "wallM", 0.2)), "walls that leave no room");
        assertTrue(invalid(RecordEditor.with(roof, "paintColor", "red")));
        assertFalse(invalid(RecordEditor.with(roof, "paintColor", "#C0FFEE")));
        // a steel roof box is a box, but too heavy for a roof: it leaves less than 20 kg of the roof's 75
        StorageBoxDesign steel = RecordEditor.with(RecordEditor.with(roof, "material", EngineMaterial.STEEL_1045), "wallM", 0.002);
        assertFalse(invalid(steel));
        assertTrue(extrapolated(steel, "roofLoadKg"), "roof load: " + steel.check());
        assertEquals(0, RecordEditor.with(steel, "wallM", 0.0035).ratedLoadKg(), 1e-9, "heavier than a roof carries: rated for nothing");
        // out of what real boxes are, still computed
        assertTrue(extrapolated(RecordEditor.with(StorageBoxDesign.container20(), "lengthM", 20.0), "lengthM"));
        assertTrue(extrapolated(RecordEditor.with(StorageBoxDesign.bedBox(), "wallM", 0.006), "wallM"));
        // a 40 ft container is rated for the same gross, its tare more
        StorageBoxDesign forty = RecordEditor.with(StorageBoxDesign.container20(), "lengthM", 12.192);
        assertEquals(30480, forty.isoGrossKg(), 1e-9);
        assertTrue(forty.massKg() > 1.7 * StorageBoxDesign.container20().massKg() && forty.capacityM3() > 2 * 33);
        // JSON: only the colour is left out when blank; it reads back
        for (StorageBoxDesign d : List.of(roof, StorageBoxDesign.bedBox(), StorageBoxDesign.container20(), RecordEditor.with(roof, "paintColor", "#123456"))) {
            assertEquals(d, PartTypes.read(Json.parse(Json.write(d.toJson())), "design"));
        }
        assertFalse(Json.write(roof.toJson()).contains("paintColor"));
    }

    @Test void itCompilesToAHoldOfItsInsideThatWearsAndDrags() {
        StorageBoxDesign box = StorageBoxDesign.container20();
        Part p = box.compile();
        assertEquals(box.massKg(), p.mass().massKg(), 1e-6);
        assertEquals(box.heightM() / 2, p.mass().centre().y(), 1e-9, "its mass at its middle (its origin is the middle of its bottom)");
        assertEquals(1, p.holds().size());
        Part.Hold h = p.holds().get(0);
        assertEquals("hold", h.name());
        assertEquals(box.capacityM3(), h.capacityM3(), 1e-12);
        assertEquals(StorageBoxDesign.FLOOR_INSET_M + 0.5 * (box.heightM() - StorageBoxDesign.FLOOR_INSET_M - StorageBoxDesign.ROOF_INSET_M), h.centre().y(), 1e-9);
        assertEquals(List.of(Conditions.STRUCTURE), p.conditions());
        assertNotNull(Conditions.of(p), "in the damage table as a shell");
        assertFalse(p.collision().isEmpty());
        for (var s : p.collision()) { assertTrue(s.centerLocal().y() - s.radiusM() >= -1e-9, "no collision sphere under its bottom"); }
        // the air: its frontal area at a bluff body's coefficient, at 20 m/s about 0.5 rho v^2 A C_D
        RoadAero air = ((StorageBoxDesign.Compiled) p).air();
        RoadAero.Loads l = air.loads(new Vec3d(-20, 0, 0), 1.225, x -> Double.POSITIVE_INFINITY);
        assertEquals(0.5 * 1.225 * 400 * box.widthM() * box.heightM() * RoadAero.CD_BLUFF, l.dragN(), 1e-6);
        RoadAero.Loads roof = ((StorageBoxDesign.Compiled) StorageBoxDesign.roofBox().compile()).air().loads(new Vec3d(-20, 0, 0), 1.225, x -> Double.POSITIVE_INFINITY);
        assertTrue(roof.dragN() > 30 && roof.dragN() < 50, "a roof box at 72 km/h: " + roof.dragN() + " N (CdA about 0.13 m2)");
    }

    @Test void itIsMadeAtTheBenchOrInTheRoomOfWhatItWeighs() {
        for (StorageBoxDesign d : List.of(StorageBoxDesign.roofBox(), StorageBoxDesign.bedBox(), StorageBoxDesign.container20(),
                RecordEditor.with(StorageBoxDesign.roofBox(), "material", EngineMaterial.ALUMINIUM_2024))) {
            PartPlan plan = Plans.of(d);
            double kg = 0;
            for (Workpiece w : plan.workpieces()) { kg += w.count() * w.finishedKg(); }
            for (StructureWork s : plan.structure()) { kg += s.finishedKg(); }
            assertEquals(d.massKg(), kg, 1e-6 * d.massKg(), d.name() + " " + d.material().name() + ": its pieces weigh its tare");
            assertEquals(d.kind() == StorageBoxDesign.Kind.CONTAINER, plan.roomBuilt(), d.name());
            assertFalse(plan.workpieces().isEmpty(), d.name());
        }
    }

    @Test void anAssemblyRoomPutsOneOnTheRoofOrTheBed() {
        // a roof box on the sedan's roof (its cabin), over the bars of a roof rack when there is one
        VehicleBlueprint sedan = dev.mcmod.hdfv.assembly.SampleLayouts.sedan();
        VehicleBlueprint withBox = Placing.add(sedan, StorageBoxDesign.roofBox());
        Mount m = withBox.parts().getLast().mount();
        assertEquals("body", m.attachPart());
        assertEquals("m3_top", m.attachPoint(), "the roof: the cabin's top");
        assertEquals(0, m.position().y(), 1e-9, "without a rack it sits on the roof");
        BodyDesign racked = SampleBodies.sedan().withKit(RecordEditor.with(BodyKitDesign.NONE, "roofRack", BodyKitDesign.RoofRack.BARS));
        VehicleBlueprint onRack = new VehicleBlueprint("racked", sedan.parts().stream()
                .map(e -> e.id().equals("body") ? new VehicleBlueprint.Entry("body", racked, e.mount()) : e).toList(), List.of());
        Mount r = Placing.add(onRack, StorageBoxDesign.roofBox()).parts().getLast().mount();
        assertEquals("m3_top", r.attachPoint());
        assertTrue(r.position().y() > 0.06 && r.position().y() < 0.2, "on the bars: " + r.position());
        // a bed box goes in the boot, a container on the low-loader's well deck (its lowest deck)
        assertEquals("m5_top", Placing.add(sedan, StorageBoxDesign.bedBox()).parts().getLast().mount().attachPoint());
        VehicleBlueprint trailer = new VehicleBlueprint("decks", List.of(new VehicleBlueprint.Entry("body", SampleBodies.lowLoaderDeck(), Mount.ORIGIN)), List.of());
        assertEquals("m3_top", Placing.add(trailer, StorageBoxDesign.container20()).parts().getLast().mount().attachPoint());
        // and it carries a hold in the vehicle: the sedan's boot and the box's, the box's at its middle on the roof
        Vehicle v = withBox.compile();
        assertEquals(2, v.holds().size());
        assertEquals("body.m5_cargo", v.holds().get(0).id());
        assertTrue(v.holds().get(1).id().endsWith(".hold"));
        assertTrue(v.holds().get(1).centre().y() > v.holds().get(0).centre().y() + 1, "the roof box's hold is up on the roof: " + v.holds().get(1).centre());
    }
}
