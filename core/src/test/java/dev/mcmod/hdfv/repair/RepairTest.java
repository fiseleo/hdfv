package dev.mcmod.hdfv.repair;

import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.StructureWork;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.refit.RefitJob;
import dev.mcmod.hdfv.repair.RepairJob.Kind;
import dev.mcmod.hdfv.repair.RepairJob.Pick;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: the repairs (RepairJob, Repairs, RepairRules, FieldRepair) on the sample sedan: a mend takes stock of its own
 * alloy and labour in proportion to the wear, wear items take standard components, tyres are always new tyres, at
 * Conditions.REPLACE_FROM a part is replaced (a bench part by a new part item, a room-built one rebuilt, a wheel's
 * brakes by their pieces), what is replaced whole takes its other conditions with it; the shop's rules and the wreck
 * ordering; what a field kit can and cannot do.
 */
class RepairTest {
    static { HdfvParts.register(); }

    private static final Vehicle SEDAN = SampleBlueprints.sedan().compile();

    /** A vehicle's conditions as set here ("part/condition" to used), 0 for the rest. */
    private static final class State implements RepairJob.Used {
        final Map<String, Double> used = new HashMap<>();
        State set(String part, String condition, double u) { used.put(part + "/" + condition, u); return this; }
        @Override public double of(String part, String condition) { return used.getOrDefault(part + "/" + condition, 0.0); }
    }

    private static RepairJob job(State s, Pick... picks) { return RepairJob.of(SEDAN, s, List.of(picks)); }
    private static Pick pick(String part, String condition) { return new Pick(part, condition); }

    private static double steel(RepairJob j) { return j.stockKg().getOrDefault(Alloy.STEEL, 0.0); }

    @Test void aMendTakesStockOfItsAlloyAndLabourByTheWear() {
        RepairJob a = job(new State().set("body", Conditions.STRUCTURE, 0.2), pick("body", Conditions.STRUCTURE));
        RepairJob b = job(new State().set("body", Conditions.STRUCTURE, 0.4), pick("body", Conditions.STRUCTURE));
        assertEquals(Kind.MEND, b.tasks().get(0).kind());
        assertEquals("", b.tasks().get(0).reason());
        assertTrue(b.parts().isEmpty() && b.pieces().isEmpty() && b.components().isEmpty(), "Mended with stock only: " + b);
        // the shell's own steel: MEND_STOCK x used x the structure works' finished mass
        PartPlan plan = Plans.of(SEDAN.installed("body").part().design());
        double shell = plan.structure().stream().mapToDouble(StructureWork::finishedKg).sum();
        assertEquals(RepairJob.MEND_STOCK * 0.4 * shell, steel(b), 1e-6);
        assertEquals(2, steel(b) / steel(a), 1e-9, "Stock in proportion to the wear");
        // labour: the strip once, and a share of making the structure in proportion to the wear; the shop's power all the while
        double make = Plans.hours(new PartPlan(plan.type(), List.of(), plan.assembly(), 0, plan.structure()));
        assertEquals(RepairJob.STRIP_HOURS + RepairJob.MEND_LABOUR * 0.4 * make, b.laborHours(), 1e-6);
        assertEquals(RepairJob.SHOP_KW * b.laborHours(), b.kWh(), 1e-9);
        assertTrue(b.welding(), "The sedan's shell is welded: a welding station");
        assertTrue(b.salvageKg().isEmpty(), "Nothing comes off");
    }

    @Test void wearItemsTakeStandardComponents() {
        State s = new State().set("front_axle", Conditions.BRAKES, 0.3).set("gearbox", Conditions.CLUTCH, 0.4).set("gearbox", Conditions.GEARS, 0.5)
                .set("engine", Conditions.ENGINE, 0.5).set("engine", Conditions.ANCILLARIES, 0.3);
        RepairJob j = job(s, pick("front_axle", Conditions.BRAKES), pick("gearbox", Conditions.CLUTCH), pick("gearbox", Conditions.GEARS),
                pick("engine", Conditions.ENGINE), pick("engine", Conditions.ANCILLARIES));
        assertTrue(j.tasks().stream().allMatch(t -> t.kind() == Kind.MEND), "All mended: " + j.tasks());
        // a lining for each front brake and one for the automatic's clutch packs; half the gearbox's six bearings; an
        // inline four's eight shells at used up, four at half, and its head's bolts and gaskets; a hose and a wire run
        assertEquals(3, j.components().get(Component.FRICTION_LINING));
        assertEquals(3, j.components().get(Component.BALL_BEARING));
        assertEquals(4, j.components().get(Component.BRONZE_BUSHING));
        assertEquals(1, j.components().get(Component.FASTENER_KIT));
        assertEquals(1, j.components().get(Component.COPPER_WIRE));
        assertEquals(1, j.components().get(Component.STEEL_TUBE));
        assertTrue(j.stockKg().isEmpty() && j.parts().isEmpty() && j.pieces().isEmpty(), "Only components: " + j);
        // labour: three parts stripped, the components fitted (a minute and 3 s a kg each), and the wear's share of making the pieces
        double fitted = 0;
        for (var e : j.components().entrySet()) { fitted += e.getValue() * (RefitJob.PIECE_S + RefitJob.PIECE_S_PER_KG * e.getKey().kg); }
        assertTrue(j.laborHours() > 3 * RepairJob.STRIP_HOURS + fitted / 3600, "More than stripping and fitting: " + j.laborHours());
        // and less than the wear's share of making the whole parts: only the condition's pieces are worked on
        double whole = 0;
        for (RepairJob.Task t : j.tasks()) { whole += RepairJob.MEND_LABOUR * t.used() * Plans.hours(Plans.of(SEDAN.installed(t.part()).part().design())); }
        assertTrue(j.laborHours() < 3 * RepairJob.STRIP_HOURS + fitted / 3600 + whole, "Only the pieces of each condition: " + j.laborHours() + " h");
    }

    @Test void tyresAreAlwaysNewTyres() {
        RepairJob j = job(new State().set("rear_axle", Conditions.TYRES, 0.1), pick("rear_axle", Conditions.TYRES));
        RepairJob.Task t = j.tasks().get(0);
        assertEquals(Kind.NEW_PIECES, t.kind());
        assertEquals(RepairJob.NEVER_MENDED, t.reason());
        assertEquals(1, j.pieces().size());
        AssemblyPlan.Piece p = j.pieces().get(0);
        assertEquals("tyre", p.workpiece().key());
        assertEquals(2, p.workpiece().count());
        assertEquals(Designs.write(SEDAN.installed("rear_axle").part().design()), Designs.write(p.design()), "The axle's own tyres");
        assertTrue(j.salvageKg().isEmpty(), "Rubber gives nothing back");
        assertEquals(RepairJob.STRIP_HOURS + 2 * (RefitJob.PIECE_S + RefitJob.PIECE_S_PER_KG * p.workpiece().finishedKg()) / 3600, j.laborHours(), 1e-9);
    }

    @Test void fromTheLineAPartIsReplacedByANewOneOfItsDesign() {
        var gearbox = SEDAN.installed("gearbox");
        State s = new State().set("gearbox", Conditions.GEARS, 0.79).set("gearbox", Conditions.CLUTCH, 0.3);
        RepairJob below = job(s, pick("gearbox", Conditions.GEARS), pick("gearbox", Conditions.CLUTCH));
        assertEquals(Kind.MEND, below.task(pick("gearbox", Conditions.GEARS)).kind(), "Just under the line it is mended");
        s.set("gearbox", Conditions.GEARS, Conditions.REPLACE_FROM);
        RepairJob at = job(s, pick("gearbox", Conditions.GEARS), pick("gearbox", Conditions.CLUTCH));
        RepairJob.Task gears = at.task(pick("gearbox", Conditions.GEARS)), clutch = at.task(pick("gearbox", Conditions.CLUTCH));
        assertEquals(Kind.NEW_PART, gears.kind());
        assertEquals(RepairJob.WORN_OUT, gears.reason());
        assertEquals(Kind.NEW_PART, clutch.kind(), "The clutch comes with the new gearbox");
        assertEquals(RepairJob.WITH_PART, clutch.reason());
        assertEquals(1, at.parts().size());
        assertEquals(Designs.write(gearbox.part().design()), Designs.write(at.parts().get(0).design()), "A gearbox of its exact design");
        assertEquals(1, at.parts().get(0).count());
        assertTrue(at.components().isEmpty() && at.stockKg().isEmpty() && at.pieces().isEmpty(), "No linings for a clutch that is replaced: " + at);
        // out and in as the assembly room fits a part, and set up
        double kg = gearbox.part().mass().massKg();
        assertEquals(2 * (AssemblyPlan.INSTALL_S + AssemblyPlan.INSTALL_S_PER_KG * kg) / 3600 + RefitJob.ADJUST_HOURS, at.laborHours(), 1e-9);
        // the old one back as half its metal
        double metal = 0;
        for (Workpiece w : Plans.of(gearbox.part().design()).workpieces()) { if (w.alloy().family.metal()) { metal += w.count() * w.finishedKg(); } }
        assertEquals(RepairJob.SALVAGE * metal, at.salvageKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
        assertNull(RepairJob.kindOf(gearbox.part().design(), Conditions.GEARS, 0), "Nothing to do with a new condition");
    }

    @Test void aWheelsWornOutBrakesAreNewPiecesNotANewAxle() {
        RepairJob j = job(new State().set("front_axle", Conditions.BRAKES, 0.85).set("front_axle", Conditions.SUSPENSION, 0.2),
                pick("front_axle", Conditions.BRAKES), pick("front_axle", Conditions.SUSPENSION));
        assertEquals(Kind.NEW_PIECES, j.task(pick("front_axle", Conditions.BRAKES)).kind());
        assertEquals(Kind.MEND, j.task(pick("front_axle", Conditions.SUSPENSION)).kind(), "The suspension is mended beside it");
        assertTrue(j.parts().isEmpty(), "No new axle");
        assertEquals(List.of("brake_disc", "caliper"), j.pieces().stream().map(p -> p.workpiece().key()).toList());
        assertTrue(j.salvageKg().get(Alloy.GREY_IRON) > 0 && j.salvageKg().get(Alloy.DUCTILE_IRON) > 0, "The old discs and calipers' iron back: " + j.salvageKg());
        assertTrue(steel(j) > 0, "The suspension's steel: " + j.stockKg());
    }

    @Test void aRoomBuiltPartIsRebuiltWhereItStands() {
        RepairJob j = job(new State().set("body", Conditions.STRUCTURE, 0.95), pick("body", Conditions.STRUCTURE));
        assertEquals(Kind.REBUILD, j.tasks().get(0).kind());
        PartPlan plan = Plans.of(SEDAN.installed("body").part().design());
        double stock = plan.structure().stream().mapToDouble(s -> s.stockKg() + s.fillerKg()).sum();
        assertEquals(stock, steel(j), 1e-6, "Its whole structure's stock and filler");
        assertEquals(plan.workpieces().size(), j.pieces().size(), "And its components");
        double labour = plan.structure().stream().mapToDouble(StructureWork::laborS).sum() + plan.assembleS() + 2 * AssemblyPlan.INSTALL_S;
        assertEquals(labour / 3600, j.laborHours(), 1e-9);
        assertTrue(j.welding() && j.parts().isEmpty());
        assertTrue(j.salvageKg().get(Alloy.STEEL) > 0.4 * plan.finishedKg(), "Half the old shell's steel back: " + j.salvageKg());
    }

    @Test void theWreckRuleWantsTheBodyWithItsEngines() {
        State s = new State().set("body", Conditions.STRUCTURE, 1).set("engine", Conditions.ANCILLARIES, 1);
        RepairJob engine = job(s, pick("engine", Conditions.ANCILLARIES));
        assertEquals(List.of("body"), RepairRules.wrecks(SEDAN, s, engine), "An engine alone in a wreck would fail again");
        RepairJob both = job(s, pick("engine", Conditions.ANCILLARIES), pick("body", Conditions.STRUCTURE));
        assertTrue(RepairRules.wrecks(SEDAN, s, both).isEmpty(), "Together they may");
        assertTrue(RepairRules.wrecks(SEDAN, s, job(s, pick("body", Conditions.STRUCTURE))).isEmpty(), "The body first may");
        RepairJob tyres = job(s.set("front_axle", Conditions.TYRES, 1), pick("front_axle", Conditions.TYRES));
        assertTrue(RepairRules.wrecks(SEDAN, s, tyres).isEmpty(), "What is not an engine may be repaired in a wreck");
        assertEquals(Conditions.ANCILLARIES, RepairRules.engineCondition(SEDAN.installed("engine").part()));
        assertNull(RepairRules.engineCondition(SEDAN.installed("gearbox").part()));
        // the shop says so
        List<RepairRules.Problem> p = RepairRules.check(new RepairRules.Shop(room(true, 1), 2, 1, false, true, 0, 0, engine, RepairRules.wrecks(SEDAN, s, engine)));
        assertEquals(List.of(new RepairRules.Problem("wreck_first", "body")), p);
    }

    /** A closed room of 8 x 5 x 4 with a door, a welding station or none. */
    private static RoomScan.Scan room(boolean closed, int welders) {
        List<int[]> stations = new java.util.ArrayList<>();
        for (int i = 0; i < welders; i++) { stations.add(new int[]{0, 0, 0}); }
        return new RoomScan.Scan(closed, 0, 0, 0, 160, 0, 0, 0, 7, 3, 4, 0,
                List.of(new RoomScan.Door(8, 0, 1, 8, 2, 3, 1, 0, true, false, true)), 0, 0, stations, List.of());
    }

    @Test void theShopsRules() {
        RepairJob body = job(new State().set("body", Conditions.STRUCTURE, 0.4), pick("body", Conditions.STRUCTURE));
        assertTrue(RepairRules.check(new RepairRules.Shop(room(true, 1), 2, 1, false, true, 0, 0, body, List.of())).isEmpty(), "A ready shop");
        List<String> keys = RepairRules.check(new RepairRules.Shop(room(true, 0), 1, 2, true, false, 1.5, 1, body, List.of())).stream().map(RepairRules.Problem::key).toList();
        assertEquals(List.of("few_lights", "vehicles", "building", "no_power", "riders", "welding_station"), keys);
        assertEquals(List.of("moving"), RepairRules.check(new RepairRules.Shop(room(true, 1), 2, 1, false, true, 1.5, 0, body, List.of())).stream().map(RepairRules.Problem::key).toList());
        assertEquals(List.of("open"), RepairRules.check(new RepairRules.Shop(room(false, 1), 2, 1, false, true, 0, 0, body, List.of())).stream().map(RepairRules.Problem::key).toList());
        // nothing picked, or only what is new: nothing to do
        RepairJob none = job(new State(), pick("body", Conditions.STRUCTURE));
        assertTrue(none.empty());
        assertEquals(List.of("nothing"), RepairRules.check(new RepairRules.Shop(room(true, 1), 2, 1, false, true, 0, 0, none, List.of())).stream().map(RepairRules.Problem::key).toList());
        // all damaged: what is used, nothing else
        State s = new State().set("body", Conditions.STRUCTURE, 0.1).set("rear_axle", Conditions.BRAKES, 0.2);
        assertEquals(List.of(pick("body", Conditions.STRUCTURE), pick("rear_axle", Conditions.BRAKES)), RepairJob.damaged(SEDAN, s));
        assertEquals(pick("rear_axle", Conditions.BRAKES), Pick.of("rear_axle/brakes"));
    }

    @Test void aFieldKitMendsAStepAndNoFurtherThanHalf() {
        FieldRepair.Kit kit = FieldRepair.Kit.FIELD_REPAIR_KIT;
        assertEquals(0.55, FieldRepair.use(kit, Conditions.STRUCTURE, 0.7).after(), 1e-12, "A step");
        assertEquals(FieldRepair.FLOOR, FieldRepair.use(kit, Conditions.ANCILLARIES, 0.6).after(), 1e-12, "Not past the floor");
        assertEquals(FieldRepair.ENOUGH, FieldRepair.use(kit, Conditions.STRUCTURE, 0.5).refusal(), "At the floor it does no more");
        assertEquals(FieldRepair.ENOUGH, FieldRepair.use(kit, Conditions.TANK, 0.2).refusal());
        assertEquals(FieldRepair.REPLACE, FieldRepair.use(kit, Conditions.STRUCTURE, Conditions.REPLACE_FROM).refusal(), "Past repair is the shop's");
        for (String c : List.of(Conditions.BRAKES, Conditions.CLUTCH, Conditions.GEARS, Conditions.ENGINE, Conditions.MOTOR, Conditions.SUSPENSION, Conditions.TYRES)) {
            assertEquals(FieldRepair.KIND, FieldRepair.use(kit, c, 0.7).refusal(), c + " needs the shop");
        }
        // the patch kit plugs a flat tyre and does nothing for tread
        FieldRepair.Kit patch = FieldRepair.Kit.TYRE_PATCH_KIT;
        assertEquals(FieldRepair.FLOOR, FieldRepair.use(patch, Conditions.TYRES, 1).after(), 1e-12);
        assertEquals(FieldRepair.NOT_FLAT, FieldRepair.use(patch, Conditions.TYRES, 0.7).refusal());
        assertEquals(FieldRepair.KIND, FieldRepair.use(patch, Conditions.STRUCTURE, 0.7).refusal());
        assertEquals(8, kit.uses);
        assertEquals(4, patch.uses);
        assertEquals(patch, FieldRepair.kitFor(Conditions.TYRES, 1));
        assertNull(FieldRepair.kitFor(Conditions.BRAKES, 0.7));
    }

    @Test void aKitWorksOnTheNearestPartItCanHelp() {
        FieldRepair.Kit kit = FieldRepair.Kit.FIELD_REPAIR_KIT;
        FieldRepair.Candidate axle = new FieldRepair.Candidate("front_axle", 0.2, List.of(Conditions.TYRES, Conditions.BRAKES, Conditions.SUSPENSION), List.of(0.0, 0.9, 0.7));
        FieldRepair.Candidate engine = new FieldRepair.Candidate("engine", 0.6, List.of(Conditions.ENGINE, Conditions.ANCILLARIES), List.of(0.7, 0.65));
        FieldRepair.Candidate body = new FieldRepair.Candidate("body", 1.4, List.of(Conditions.STRUCTURE), List.of(0.75));
        FieldRepair.Choice c = FieldRepair.choose(kit, List.of(axle, engine, body));
        assertEquals("engine", c.part(), "The nearest it can do something for (the axle needs the shop)");
        assertEquals(Conditions.ANCILLARIES, c.condition());
        assertEquals(0.5, c.outcome().after(), 1e-12);
        // only what it cannot help: the nearest one's reason
        FieldRepair.Candidate wrecked = new FieldRepair.Candidate("body", 0.3, List.of(Conditions.STRUCTURE), List.of(0.9));
        FieldRepair.Choice r = FieldRepair.choose(kit, List.of(axle, wrecked));
        assertEquals("body", r.part());
        assertEquals(FieldRepair.REPLACE, r.outcome().refusal());
        assertNull(FieldRepair.choose(FieldRepair.Kit.TYRE_PATCH_KIT, List.of(engine)).part(), "No tyre anywhere");
        FieldRepair.Choice flat = FieldRepair.choose(FieldRepair.Kit.TYRE_PATCH_KIT, List.of(new FieldRepair.Candidate("rear_axle", 1, List.of(Conditions.TYRES), List.of(1.0)), axle));
        assertEquals("rear_axle", flat.part(), "The flat one, though the other axle is nearer");
    }
}
