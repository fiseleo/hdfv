package dev.mcmod.hdfv.refit;

import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.assembly.RoomScan;
import dev.mcmod.hdfv.assembly.SampleLayouts;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 0.16: the refit shop's adjustables, edits, job and checks (Refit, RefitJob, RefitRules). */
class RefitTest {
    static { HdfvParts.register(); }

    private static VehicleBlueprint sedan() { return AutoWiring.wire(SampleLayouts.sedan()); }

    @Test void whatTheShopMayChange() {
        VehicleBlueprint b = sedan();
        var body = Refit.adjustables(b.part("body").design());
        assertTrue(body.contains(new Refit.Adjustable("module1", "lengthM", Refit.Category.SIZE)), body.toString());
        assertTrue(body.contains(new Refit.Adjustable("settings", "noseBluntness", Refit.Category.SHAPE)), body.toString());
        var engine = Refit.adjustables(b.part("engine").design(), Refit.Category.TUNE);
        assertTrue(engine.contains(new Refit.Adjustable("ignition", "revLimitRpm", Refit.Category.TUNE)), engine.toString());
        assertTrue(Refit.adjustables(b.part("front_axle").design()).contains(new Refit.Adjustable("suspension", "springRateNPerM", Refit.Category.TUNE)),
                Refit.adjustables(b.part("front_axle").design()).toString());
        assertTrue(Refit.adjustables(b.part("gearbox").design()).stream().anyMatch(a -> a.page().equals("control")), Refit.adjustables(b.part("gearbox").design()).toString());
        // an engine's insides and a body's skin are the assembly room's
        assertThrows(IllegalArgumentException.class, () -> Refit.set(b, "engine", "block", "boreM", "90"));
        assertThrows(IllegalArgumentException.class, () -> Refit.set(b, "body", "module2", "skinM", "3"));
        // an edit that leaves a part INVALID is refused
        assertThrows(IllegalArgumentException.class, () -> Refit.set(b, "front_axle", "layout", "trackM", "9000"));
    }

    @Test void tuningTakesOnlyHours() {
        VehicleBlueprint b = sedan();
        PistonEngineDesign e = (PistonEngineDesign) b.part("engine").design();
        VehicleBlueprint tuned = Refit.set(b, "engine", "ignition", "revLimitRpm", String.valueOf(e.ignition().revLimitRpm() - 500));
        tuned = Refit.set(tuned, "front_axle", "suspension", "camberDeg", "-1.5");
        assertTrue(Refit.sameVehicle(b, tuned));
        assertEquals(List.of("engine", "front_axle"), Refit.changed(b, tuned));
        RefitJob job = RefitJob.of(b, tuned);
        assertEquals(List.of("engine", "front_axle"), job.adjusted());
        assertEquals(List.of(), job.rebuilt());
        assertEquals(List.of(), job.pieces(), "the same components: nothing to make");
        assertEquals(RefitJob.ADJUST_HOURS + RefitJob.ALIGN_HOURS, job.laborHours(), 1e-9);
        assertEquals(0, job.cans());
        assertEquals(RefitJob.SHOP_KW * job.laborHours(), job.kWh(), 1e-9);
    }

    @Test void stifferSpringsAreAnAdjustment() {
        VehicleBlueprint b = sedan();
        var axle = (dev.mcmod.hdfv.part.wheel.AxleDesign) b.part("front_axle").design();
        double rate = axle.suspension().springRateNPerM(), compression = axle.suspension().designCompressionM();
        // a stiffer spring sits less compressed under the same load: the perch is set first, then the spring changed
        VehicleBlueprint stiff = Refit.set(b, "front_axle", "suspension", "designCompressionM", String.valueOf(compression / 1.5 * 1000));
        stiff = Refit.set(stiff, "front_axle", "suspension", "springRateNPerM", String.valueOf(rate * 1.5 / 1000));
        // the planner sizes the suspension's components by the axle's geometry and load, not by the rate: the same
        // components, so a spring swap is the shop's work alone
        RefitJob job = RefitJob.of(b, stiff);
        assertEquals(List.of("front_axle"), job.adjusted());
        assertEquals(List.of(), job.pieces());
    }

    @Test void aWiderTyreIsNewComponentsOnly() {
        VehicleBlueprint b = sedan();
        var axle = (dev.mcmod.hdfv.part.wheel.AxleDesign) b.part("rear_axle").design();
        VehicleBlueprint wider = Refit.set(b, "rear_axle", "tyre", "sectionWidthM", String.valueOf((axle.tyre().sectionWidthM() + 0.02) * 1000));
        RefitJob job = RefitJob.of(b, wider);
        assertEquals(List.of("rear_axle"), job.rebuilt());
        int all = Plans.of(wider.part("rear_axle").design()).workpieces().size();
        List<String> keys = job.pieces().stream().map(p -> p.workpiece().key()).toList();
        assertFalse(keys.isEmpty());
        assertTrue(keys.size() < all, "only the changed components: " + keys);
        assertTrue(job.pieces().stream().allMatch(p -> p.design() == wider.part("rear_axle").design()), "made from the refitted design");
        assertEquals(keys, job.returned().stream().map(p -> p.workpiece().key()).toList(), "the old ones come off");
        assertTrue(job.stockKg().isEmpty() && job.repaintM2() == 0);
        // the planner sizes the final drive by the axle's given masses, not its ratio: a new ratio is the shop's work alone
        VehicleBlueprint shorter = Refit.set(b, "rear_axle", "differential", "finalRatio", String.valueOf(axle.differential().finalRatio() * 1.1));
        assertEquals(List.of("rear_axle"), RefitJob.of(b, shorter).adjusted());
    }

    @Test void aLongerHoodIsBodyworkAndPaint() {
        VehicleBlueprint b = sedan();
        var body = (dev.mcmod.hdfv.part.body.BodyDesign) b.part("body").design();
        double length = body.modules().get(0).lengthM();
        VehicleBlueprint longer = Refit.set(b, "body", "module1", "lengthM", String.valueOf(length + 0.2));
        RefitJob job = RefitJob.of(b, longer);
        assertEquals(List.of("body"), job.rebuilt());
        assertFalse(job.stockKg().isEmpty(), "new skin");
        assertFalse(job.refundKg().isEmpty(), "the old skin's metal");
        assertTrue(job.repaintM2() > 5 && job.cans() > 0, "repainted: " + job.repaintM2());
        assertTrue(job.welding());
        assertTrue(job.paintHours() > 0 && job.hours() > job.laborHours());
    }

    @Test void aWholeScaleRebuildsEverything() {
        VehicleBlueprint b = sedan(), big = Refit.rescale(b, 1, 1.2);
        assertTrue(Refit.sameVehicle(b, big));
        RefitJob job = RefitJob.of(b, big);
        assertEquals(List.of("body", "engine", "gearbox", "front_axle", "rear_axle"), job.rebuilt());
        assertEquals(List.of(), job.adjusted());
        // scaling back is the inverse (to rounding)
        VehicleBlueprint back = Refit.rescale(big, 1.2, 1);
        assertEquals(b.part("front_axle").design().toJson().keySet(), back.part("front_axle").design().toJson().keySet());
        assertEquals(((dev.mcmod.hdfv.part.wheel.AxleDesign) b.part("front_axle").design()).layout().trackM(),
                ((dev.mcmod.hdfv.part.wheel.AxleDesign) back.part("front_axle").design()).layout().trackM(), 1e-12);
    }

    /** A closed 10 x 4 x 6 m room with a 3 x 3 door and a welding station. */
    private static RoomScan.Scan room(boolean welding) {
        RoomScan.Door door = new RoomScan.Door(0, 0, 0, 0, 2, 2, -1, 0, true, false, true);
        return new RoomScan.Scan(true, 0, 0, 0, 240, 0, 0, 0, 9, 3, 5, 0, List.of(door), 0, 0, welding ? List.of(new int[]{1, 0, 1}) : List.of(), List.of());
    }

    @Test void theShopSaysWhatIsMissing() {
        VehicleBlueprint b = sedan();
        VehicleBlueprint longer = Refit.set(b, "body", "module1", "lengthM", "1.4");
        AssemblyPlan before = AssemblyPlan.of(b), after = AssemblyPlan.of(longer);
        RefitJob job = RefitJob.of(b, longer);
        var ok = RefitRules.check(new RefitRules.Shop(room(true), 2, 1, false, true, before, after, job, 1));
        assertEquals(List.of(), ok, "a proper shop");
        var bad = RefitRules.check(new RefitRules.Shop(room(false), 1, 1, false, false, before, after, job, 2.5));
        assertEquals(List.of("few_lights", "no_power", "welding_station", "scale"), bad.stream().map(RefitRules.Problem::key).toList());
        // a body too low for its engine: the assembly room's own check, as the vehicle's problem
        VehicleBlueprint low = Refit.set(b, "body", "module1", "heightM", "0.45");
        var fit = RefitRules.check(new RefitRules.Shop(room(true), 2, 1, false, true, before, AssemblyPlan.of(low), RefitJob.of(b, low), 1));
        assertTrue(fit.stream().anyMatch(p -> p.vehicle() && p.key().equals("part_does_not_fit")), fit.toString());
    }

    @Test void aLampOfAnotherStyleIsANewUnitAMovedOneIsNot() {
        VehicleBlueprint b = sedan();
        var body = (dev.mcmod.hdfv.part.body.BodyDesign) b.part("body").design();
        List<dev.mcmod.hdfv.look.LampSpec> kit = Refit.lamps(body);
        var head = kit.get(0);
        // moved and re-aimed: the same units
        var moved = new java.util.ArrayList<>(kit);
        moved.set(0, new dev.mcmod.hdfv.look.LampSpec(head.role(), head.style(), head.face(), 0, 0, -0.6, 0.5, 0, true, head.sizeM(), "", "", 10, 0));
        RefitJob job = RefitJob.of(b, Refit.withLamps(b, "body", moved));
        assertEquals(0, job.lampUnits());
        assertEquals(0, job.lampsBack());
        assertEquals(List.of("body"), job.adjusted(), "only the shop's hours");
        // square headlamps for the projectors: two new units, two back
        var square = new java.util.ArrayList<>(kit);
        square.set(0, new dev.mcmod.hdfv.look.LampSpec(head.role(), dev.mcmod.hdfv.look.LampSpec.Style.SQUARE, head.face(), 0, 0, head.across(), head.up(), 0, true, 0.2, "", "", 0, 0));
        job = RefitJob.of(b, Refit.withLamps(b, "body", square));
        assertEquals(2, job.lampUnits());
        assertEquals(2, job.lampsBack());
        // all taken off: six back (0.18: the clusters, fog lamps and tail lamps)
        job = RefitJob.of(b, Refit.withLamps(b, "body", List.of()));
        assertEquals(0, job.lampUnits());
        assertEquals(6, job.lampsBack());
    }
}
