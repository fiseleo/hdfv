package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManufactureTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    /** The mass a design counts for itself (what its plan must add up to). */
    static double designKg(PartDesign d) {
        return switch (d) {
            case PistonEngineDesign e -> e.totalMassKg();
            case JetEngineDesign e -> e.totalMassKg();
            case TurbofanDesign e -> e.totalMassKg();
            case ShaftTurbineDesign e -> e.totalMassKg();
            case dev.mcmod.hdfv.part.engine.RamjetDesign e -> e.totalMassKg();
            case dev.mcmod.hdfv.part.engine.ScramjetDesign e -> e.totalMassKg();
            case dev.mcmod.hdfv.part.engine.PulsejetDesign e -> e.totalMassKg();
            case dev.mcmod.hdfv.part.engine.SolidRocketDesign e -> e.dryMassKg();   // the grain is cast and loaded as an item
            case dev.mcmod.hdfv.part.engine.TurboRamjetDesign e -> e.totalMassKg();
            case WingDesign w -> w.totalMassKg();
            case AirPropellerDesign a -> a.totalMassKg();
            case RotorDesign r -> r.totalMassKg();
            case BodyDesign b -> b.structureKg();
            case AxleDesign a -> a.totalMassKg();
            case LandingGearDesign l -> l.totalMassKg();
            case GearboxDesign g -> g.totalMassKg();
            case TransferCaseDesign t -> t.totalMassKg();
            default -> d.compile().mass().massKg();
        };
    }

    static List<PartDesign> everything() {
        List<PartDesign> out = new ArrayList<>();
        for (Designs.Kind k : Designs.KINDS) { out.add(k.fresh().get()); }
        for (Designs.Sample s : Designs.SAMPLES) { out.add(s.design().get()); }
        return out;
    }

    @Test void everyDesignHasAPlanThatAddsUpToItsMass() {
        for (PartDesign d : everything()) {
            PartPlan p = Plans.of(d);
            assertFalse(p.workpieces().isEmpty() && p.structure().isEmpty(), d.name());
            double kg = designKg(d);
            assertEquals(kg, p.finishedKg(), 1e-6 * Math.max(kg, 1), d.name() + " (" + d.type() + ")");
            for (Workpiece w : p.workpieces()) {
                assertTrue(w.stockKg() >= w.finishedKg() - 1e-9, d.name() + " " + w.key());
                assertFalse(w.route().isEmpty() && w.form() != Form.TIMBER, d.name() + " " + w.key() + " has no steps");
                for (Operation op : w.route()) {
                    boolean somewhere = false;
                    // 0.17: a family's heads and the new processes take the workpiece (its shape, its stock)
                    for (Station s : Station.values()) { somewhere |= Double.isFinite(Plans.seconds(op, s, w)); }
                    assertTrue(somewhere, d.name() + " " + w.key() + " " + op + " has no station");
                }
            }
            double hours = Plans.hours(p);
            assertTrue(hours > 0 && Double.isFinite(hours), d.name() + " " + hours);
        }
    }

    @Test void everyMaterialHasStock() {
        for (EngineMaterial m : EngineMaterial.STANDARD) { Alloy.of(m); }
        for (var m : dev.mcmod.aerocore.api.vehicle.Material.STANDARD) { Alloy.of(m); }
        for (Alloy a : Alloy.values()) { assertEquals(a, Alloy.byId(a.id())); }
        // a custom material goes to the metal of the nearest density
        assertEquals(Alloy.TITANIUM, Alloy.of(new EngineMaterial("unobtainium", 4500, 1e9, 5e8, 1e11, 10, 500, 700)));
    }

    @Test void theSampleEnginesNeedACncAndAnOldSchoolOneDoesNot() {
        PistonEngineDesign four = SampleEngines.inlineFour();
        PartPlan modern = Plans.of(four);
        // port injectors' orifices are CNC work
        Workpiece injector = modern.workpieces().stream().filter(w -> w.key().equals("injector")).findFirst().orElseThrow();
        assertFalse(injector.manual());
        // a 40 micrometre piston clearance on an 86 mm bore: each part to IT5, honed (the boring mill reaches IT5 honing)
        assertEquals(5, EnginePlanner.fit(0.086, 40e-6));
        // carburetted, with the wider fits of older engines (80 micrometre pistons, 60 micrometre big ends, 20 micrometre floating
        // pins): every step on manual machines
        PartDesign old = Designs.set(four, "fuelSystem", "delivery", "carburettor");
        old = Designs.set(old, "piston", "diameterM", "85.92");
        old = Designs.set(old, "rod", "bigEndBoreM", "48.06");
        old = Designs.set(old, "rod", "smallEndBoreM", "22.02");
        PartPlan plan = Plans.of(old);
        List<String> cnc = new ArrayList<>();
        for (Workpiece w : plan.workpieces()) { for (Operation op : w.cncOnly()) { cnc.add(w.key() + " " + op.process() + " IT" + op.grade()); } }
        assertTrue(cnc.isEmpty(), "CNC-only steps left: " + cnc);
        // a mechanic's machining and assembly of an inline four: some tens of hours on manual machines
        double hours = Plans.hours(plan);
        System.out.printf("inline four, manual: %.1f h of work, %d workpieces, %.0f kg finished%n", hours, plan.workpieces().size(), plan.finishedKg());
        System.out.printf("inline four, modern: %.1f h, CNC-only steps in %s%n", Plans.hours(modern),
                modern.workpieces().stream().filter(w -> !w.manual()).map(Workpiece::key).toList());
        assertTrue(hours > 5 && hours < 80, "inline four " + hours + " h");
    }

    @Test void turbineBladesAndCoolingHolesAreCncOnly() {
        PartPlan jet = Plans.of(SampleEngines.turbojet());
        assertFalse(jet.workpieces().stream().filter(w -> w.key().equals("compressor_blades")).findFirst().orElseThrow().manual());
        assertFalse(jet.workpieces().stream().filter(w -> w.key().equals("combustor")).findFirst().orElseThrow().manual());
        assertFalse(jet.manual());
    }

    @Test void aSteelCarBodyIsSpotWeldedLikeARealOne() {
        PartPlan body = Plans.of(SampleBodies.sedan());
        assertTrue(body.roomBuilt());
        int spots = body.structure().stream().filter(s -> s.joint() == Joint.SPOT_WELD).mapToInt(StructureWork::fasteners).sum();
        // a car's body-in-white has some 3000-6000 spot welds
        System.out.printf("sedan body: %d spot welds, %.1f h, %.0f kg; turbojet %.1f h%n", spots, Plans.hours(body), body.finishedKg(),
                Plans.hours(Plans.of(SampleEngines.turbojet())));
        assertTrue(spots > 1500 && spots < 9000, "spot welds " + spots);
        // an aluminium semi-monocoque fuselage is riveted
        PartPlan fuselage = Plans.of(SampleBodies.seaplaneFuselage());
        assertTrue(fuselage.structure().stream().anyMatch(s -> s.joint() == Joint.RIVET), fuselage.structure().toString());
    }

    @Test void timeScales() {
        assertEquals(1000, Pace.ticks(3600, Pace.TICKS_PER_WORK_HOUR));
        assertEquals(1, Pace.ticks(0.1, Pace.TICKS_PER_WORK_HOUR));
        assertEquals(10, Pace.nuggets(10, Pace.KG_PER_INGOT));
        assertEquals(1, Pace.nuggets(0.4, Pace.KG_PER_INGOT));
        // a lathe takes longer than a CNC on the same cut, and a CNC can do what a lathe cannot
        Operation fine = Operation.cut(Process.TURN, 1e-4, 0.05, 0.05, 5, false);
        assertTrue(Double.isInfinite(Plans.seconds(fine, Station.LATHE, Alloy.STEEL)));
        Operation rough = Operation.cut(Process.TURN, 1e-4, 0.05, 0.05, 8, false);
        assertTrue(Plans.seconds(rough, Station.LATHE, Alloy.STEEL) > Plans.seconds(rough, Station.CNC, Alloy.STEEL));
        assertTrue(Plans.seconds(rough, Station.LATHE, Alloy.ALUMINIUM_CAST) < Plans.seconds(rough, Station.LATHE, Alloy.TITANIUM));
    }
}
