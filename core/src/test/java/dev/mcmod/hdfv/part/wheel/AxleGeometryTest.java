package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.15: an axle's hardpoints, air springs and steering schedule; 0.14's axles stay as they were. */
class AxleGeometryTest {
    static { HdfvParts.register(); }

    static AxleDesign withHardpoints(AxleDesign a, HardpointsDesign h) {
        return new AxleDesign(a.name(), a.layout(), a.tyre(), a.wheel(), a.brake(), a.suspension(), a.differential(), h);
    }

    @Test void the014AxlesAreAsTheyWere() {
        for (AxleDesign a : List.of(SampleWheels.sedanFront(), SampleWheels.sedanRear(), SampleWheels.offRoadFront(), SampleWheels.offRoadRear())) {
            String json = Designs.write(a);
            assertFalse(json.contains("hardpoints") || json.contains("springKind") || json.contains("steerRatio"), a.name() + ": " + json);
            assertEquals(a, Designs.read(json));
            Part p = a.compile();
            assertTrue(p instanceof AxlePart, "not wrapped");
            assertNull(((AxlePart) p).left().spec().suspension().kinematics());
            assertNull(((AxlePart) p).levelling());
        }
    }

    @Test void hardpointsStartFromTheSimpleBehaviour() {
        AxleDesign front = SampleWheels.sedanFront();
        HardpointsDesign on = RecordEditor.with(front.hardpoints(), "mode", HardpointsDesign.Mode.HARDPOINTS);
        AxleDesign hp = withHardpoints(front, on);
        assertTrue(hp.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), hp.check().toString());
        var table = hp.kinematics();
        Suspension.Geometry g = Suspension.Geometry.of(front.suspension().layout(), front.layout().trackM(), front.tyre().tyre().radiusM(),
                front.suspension().swingArmM(), front.suspension().linkHeightM(), Math.toRadians(front.suspension().sweepDeg()));
        assertEquals(g.rollCentreHeightM(), table.rollCentreHeightM(), 1e-3);
        AxlePart p = (AxlePart) hp.compile();
        assertTrue(p.left().spec().suspension().kinematics() == table);
        assertEquals(hp, Designs.read(Designs.write(hp)), "the hardpoints come back");
        // a given point moves the wheel: the lower ball joint 3 cm lower changes the roll centre
        HardpointsDesign moved = RecordEditor.with(on, "lowerBall", new double[]{0, -0.16, -0.04});
        assertTrue(Math.abs(withHardpoints(front, moved).kinematics().rollCentreHeightM() - table.rollCentreHeightM()) > 0.005);
        // a lower arm too short for the travel binds
        HardpointsDesign stub = RecordEditor.with(on, "lowerFront", new double[]{0.01, -0.11, -0.07});
        stub = RecordEditor.with(stub, "lowerRear", new double[]{-0.01, -0.11, -0.07});
        AxleDesign bound = withHardpoints(front, stub);
        assertTrue(bound.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals("hardpoints.mode")), bound.check().toString());
    }

    @Test void aBlankHardpointShowsThePointItStandsFor() {
        AxleDesign front = SampleWheels.sedanFront();
        HardpointsDesign on = RecordEditor.with(front.hardpoints(), "mode", HardpointsDesign.Mode.HARDPOINTS);
        AxleDesign blank = withHardpoints(front, on);
        String hint = blank.hardpointHint("lowerBall");
        assertTrue(hint != null && hint.split(" ").length == 3, String.valueOf(hint));
        assertEquals("0.000 -0.160 -0.040", withHardpoints(front, RecordEditor.with(on, "lowerBall", new double[]{0, -0.16, -0.04})).hardpointHint("lowerBall"));
        assertNull(blank.hardpointHint("pivotA"), "a MacPherson strut has no trailing arm pivot");
        assertEquals(hint, dev.mcmod.hdfv.design.Designs.hint(blank, "hardpoints", "lowerBall"));
    }

    @Test void airSpringsCarryTheDesignLoadAndLevel() {
        AxleDesign rear = SampleWheels.sedanRear();
        SuspensionDesign air = RecordEditor.with(rear.suspension(), "springKind", SuspensionDesign.SpringKind.AIR);
        AxleDesign a = new AxleDesign(rear.name(), rear.layout(), rear.tyre(), rear.wheel(), rear.brake(), air, rear.differential());
        assertTrue(a.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), a.check().toString());
        Suspension.Spring s = air.spring();
        assertEquals(air.designLoadKg() * SuspensionDesign.G, s.force(air.designCompressionM()), 1e-6, "the air holds the design load at the design height");
        double hz = a.rideFrequencyHz(a.tyre().tyre());
        assertTrue(hz > 0.8 && hz < 2.5, "a car's ride: " + hz + " Hz");
        AxlePart p = (AxlePart) a.compile();
        assertTrue(p.inputs().contains("ride_height"));
        assertEquals(air.rideHighM(), p.levelling().highM(), 0);
        assertEquals(a, Designs.read(Designs.write(a)));
    }

    @Test void aRearAxleSteersAgainstTheFrontSlowlyAndWithItFast() {
        AxleDesign rear = SampleWheels.sedanRear();
        AxleLayoutDesign l = rear.layout();
        AxleLayoutDesign steered = new AxleLayoutDesign(l.name(), l.trackM(), l.hubDropM(), true, 6, 1, -1.2, l.antiRollNPerM(), -0.6, 0.15, 16);
        AxleDesign a = new AxleDesign(rear.name(), steered, rear.tyre(), rear.wheel(), rear.brake(), rear.suspension(), rear.differential());
        assertTrue(a.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), a.check().toString());
        SteerSchedule s = (SteerSchedule) a.compile();
        assertEquals(-0.6, s.ratio(0), 0);
        assertEquals(-0.6, s.ratio(16 - SteerSchedule.BLEND_MPS / 2), 1e-12);
        assertEquals(0.15, s.ratio(40), 1e-12);
        assertEquals(0.5 * (-0.6 + 0.15), s.ratio(16), 1e-12);
        assertTrue(s.inputs().contains("steer") && s.probes());
        assertEquals(s.inner(), SteerSchedule.axleOf(s));
        assertEquals(a, Designs.read(Designs.write(a)));
    }
}
