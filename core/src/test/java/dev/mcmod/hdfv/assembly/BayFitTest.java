package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.drive.TestCars;
import dev.mcmod.hdfv.look.Envelope;
import dev.mcmod.hdfv.look.Envelopes;
import dev.mcmod.hdfv.part.SeatDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import dev.mcmod.hdfv.part.drive.SampleGearboxes;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.part.wheel.SampleWheels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.14: what goes in a body's engine bay must fit in it (Fitting), and Placing puts it where it does. */
class BayFitTest {
    /** Every box of an entry's envelope and how far it sticks out of its body, its boxes grown by a margin. */
    static String report(VehicleBlueprint b, String id, double grow) {
        VehicleBlueprint.Entry e = b.part(id);
        Fitting.InBody in = Fitting.inBody(b, e);
        StringBuilder out = new StringBuilder(id + " at " + in.placement().position() + ":");
        Vec3d g = new Vec3d(grow, grow, grow);
        for (Envelope.Box box : Envelopes.of(e.design()).boxes()) {
            BodyShape.Overhang o = in.shape().overhang(box.min().subtract(g), box.max().add(g), in.placement().rotation(), in.placement().position(), Fitting.STEP_M);
            if (o.maxM() > 0) { out.append(String.format(" %s %s %.1f cm;", box.name(), o.worst(), o.maxM() * 100)); }
        }
        return out.toString();
    }

    static Map<String, VehicleBlueprint> cars() {
        Map<String, VehicleBlueprint> out = new LinkedHashMap<>(SampleLayouts.all());
        out.put("manual sedan", TestCars.manualSedan());
        out.put("4x4", TestCars.fourByFour());
        out.put("6x6", TestCars.sixBySix());
        out.put("rear-steer sedan", TestCars.rearSteerSedan());
        return out;
    }

    @Test void everySampleAndTestCarFits() {
        for (var c : cars().entrySet()) {
            assertEquals(List.of(), Fitting.misfits(c.getValue()), c.getKey());
            assertEquals(List.of(), Fitting.seatClashes(c.getValue()), c.getKey());
            AssemblyPlan plan = AssemblyPlan.of(AutoWiring.wire(c.getValue()));
            assertEquals(List.of(), plan.misfits(), c.getKey());
            assertEquals(List.of(), plan.seatClashes(), c.getKey());
        }
    }

    @Test void theSedansEngineHasRoomRoundIt() {
        VehicleBlueprint sedan = SampleLayouts.sedan();
        Fitting.InBody in = Fitting.inBody(sedan, sedan.part("engine"));
        assertNotNull(in);
        BodyShape.Overhang o = Fitting.overhang(in.shape(), Envelopes.of(sedan.part("engine").design()), in.placement(), Fitting.CLEARANCE_M);
        assertEquals(0, o.maxM(), 1e-9, report(sedan, "engine", Fitting.CLEARANCE_M));
        // where Placing puts it; on the bay's point itself it would be closer than CLEARANCE_M under the hood
        assertEquals(-0.05, sedan.part("engine").mount().position().x(), 1e-9);
    }

    /** Where a part's drive goes out (its envelope's rear end where it names no face: a transverse box's). */
    static double out(VehicleBlueprint.Entry e) {
        Envelope env = Envelopes.of(e.design());
        return e.mount().position().x() + (Double.isNaN(env.outputX()) ? env.bounds().min().x() : env.outputX());
    }

    /** Where a part's drive comes in (its envelope's front end where it names no face). */
    static double in(VehicleBlueprint.Entry e) {
        Envelope env = Envelopes.of(e.design());
        return e.mount().position().x() + (Double.isNaN(env.inputX()) ? env.bounds().max().x() : env.inputX());
    }

    @Test void theGearboxBoltsOnTheFlywheelFaceAndTheTransferCaseOnTheTail() {
        VehicleBlueprint sedan = SampleLayouts.sedan();
        var engine = sedan.part("engine");
        var gearbox = sedan.part("gearbox");
        assertEquals(out(engine), gearbox.mount().position().x() + Envelopes.of(gearbox.design()).inputX(), 1e-9);
        assertEquals("engine_bay", gearbox.mount().attachPoint());
        VehicleBlueprint fourByFour = TestCars.fourByFour();
        var box = fourByFour.part("gearbox");
        var transfer = fourByFour.part("transfer");
        assertEquals(out(box), in(transfer), 1e-9);
        assertEquals(out(fourByFour.part("engine")), in(box), 1e-9);
        // parts added one by one in a room go the same way
        VehicleBlueprint built = new VehicleBlueprint("sedan", List.of(), List.of());
        built = Placing.add(built, SampleBodies.sedan());
        built = Placing.add(built, engine.design());
        built = Placing.add(built, SampleGearboxes.sixSpeedManual());
        built = Placing.add(built, SampleGearboxes.partTime());
        var e1 = built.part("piston_engine_1");
        var g1 = built.parts().get(2);
        var t1 = built.parts().get(3);
        assertFalse(Double.isNaN(Envelopes.of(g1.design()).inputX()), "a rear-drive box bolts on by its bell");
        assertEquals(out(e1), in(g1), 1e-9);
        assertEquals(out(g1), in(t1), 1e-9);
        assertEquals(List.of(), Fitting.misfits(built), report(built, "piston_engine_1", 0));
    }

    /** The sedan as it was from 0.7 to 0.13: the engine 0.1 m forward of the bay's point, two seat parts in the windscreen module. */
    static VehicleBlueprint oldSedan() {
        return new VehicleBlueprint("sedan", List.of(
                new VehicleBlueprint.Entry("body", SampleBodies.sedan(), Mount.ORIGIN),
                new VehicleBlueprint.Entry("engine", SampleEngines.inlineFour(), Mount.on("body", "engine_bay", Mount.at(new Vec3d(0.1, 0, 0)))),
                new VehicleBlueprint.Entry("gearbox", SampleGearboxes.sixSpeedAutomatic(), Mount.on("body", "engine_bay", Mount.at(new Vec3d(-0.55, 0, 0)))),
                new VehicleBlueprint.Entry("front_axle", SampleWheels.sedanFront(), Mount.on("body", "front_axle")),
                new VehicleBlueprint.Entry("rear_axle", SampleWheels.sedanRear(), Mount.on("body", "rear_axle")),
                new VehicleBlueprint.Entry("driver", new SeatDesign("driver", 15), Mount.on("body", "m2_bottom", Mount.at(new Vec3d(0.2, 0.35, -0.38)))),
                new VehicleBlueprint.Entry("passenger", new SeatDesign("passenger", 15), Mount.on("body", "m2_bottom", Mount.at(new Vec3d(0.2, 0.35, 0.38))))),
                List.of());
    }

    @Test void theOldSedanIsRefused() {
        VehicleBlueprint old = oldSedan();
        List<Fitting.Misfit> misfits = Fitting.misfits(old);
        assertEquals(List.of("engine"), misfits.stream().map(Fitting.Misfit::part).toList(), report(old, "engine", 0));
        assertTrue(misfits.get(0).cm() >= 1, misfits.toString());
        assertEquals(List.of("driver", "passenger"), Fitting.seatClashes(old));
        AssemblyPlan plan = AssemblyPlan.of(AutoWiring.wire(old));
        assertEquals(misfits, plan.misfits());
        assertEquals(List.of("driver", "passenger"), plan.seatClashes());
    }

    @Test void aGeneratorDieselDoesNotGoInTheSedan() {
        List<VehicleBlueprint.Entry> parts = new ArrayList<>();
        for (VehicleBlueprint.Entry e : SampleLayouts.sedan().parts()) {
            parts.add(e.id().equals("engine") ? new VehicleBlueprint.Entry("engine", SampleEngines.generatorDiesel(), e.mount()) : e);
        }
        VehicleBlueprint b = new VehicleBlueprint("sedan", parts, List.of());
        List<Fitting.Misfit> misfits = Fitting.misfits(b);
        assertFalse(misfits.isEmpty());
        assertEquals("engine", misfits.get(0).part());
        // nor slid anywhere along the bay
        Mount best = Placing.slideToFit(SampleBodies.sedan(), "engine_bay", SampleEngines.generatorDiesel());
        Fitting.InBody in = Fitting.inBody(b, b.part("engine"));
        assertTrue(Fitting.overhang(in.shape(), Envelopes.of(SampleEngines.generatorDiesel()), in.placement().then(best), 0).maxM() > Fitting.TOLERANCE_M);
    }

    @Test void partsOffTheBayAreNotChecked() {
        // a free engine, and an aircraft's engine on its nose: no misfits however big
        for (VehicleBlueprint b : SampleLayouts.all().values()) {
            for (VehicleBlueprint.Entry e : b.parts()) {
                if (Fitting.inBody(b, e) == null) { continue; }
                assertTrue(Fitting.BAY.matcher(e.mount().attachPoint()).matches(), e.id());
            }
        }
        List<VehicleBlueprint.Entry> parts = new ArrayList<>(SampleLayouts.sedan().parts());
        parts.add(new VehicleBlueprint.Entry("loose", SampleEngines.generatorDiesel(), Mount.at(new Vec3d(0, 5, 0))));
        parts.add(new VehicleBlueprint.Entry("bolted", SampleEngines.generatorDiesel(), Mount.on("body", "front_axle")));
        assertEquals(List.of(), Fitting.misfits(new VehicleBlueprint("sedan", parts, List.of())));
    }

    @Test void aSeatPartGoesFreeInABodyWithSeats() {
        VehicleBlueprint built = Placing.add(Placing.add(new VehicleBlueprint("x", List.of(), List.of()), SampleBodies.sedan()), new SeatDesign("seat", 10));
        assertFalse(built.parts().get(1).mount().attached());
    }
}
