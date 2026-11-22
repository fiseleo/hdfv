package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.hydraulic.Hydraulics;
import dev.mcmod.aerocore.api.hydraulic.Pump;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.HangPart;
import dev.mcmod.aerocore.api.vehicle.part.HydraulicMotorPart;
import dev.mcmod.aerocore.api.vehicle.part.HydraulicPumpPart;
import dev.mcmod.aerocore.api.vehicle.part.LinkPart;
import dev.mcmod.hdfv.design.RecordEditor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.26: the hydraulics and work equipment (HydraulicPumpDesign, HydraulicMotorDesign, LinkDesign, HookDesign) against
 * catalogue parts and real machines: masses, torques, a 20 t excavator's front whose rams reach their joints' ranges,
 * a crane's hook and winch; their JSON, their compiled AeroCore parts and the checks. Built directly (no part registry).
 */
class WorkEquipmentDesignTest {
    private static final double BAR = Hydraulics.BAR_PA, CC = Hydraulics.CM3_M3;

    private static boolean invalid(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(parameter));
    }

    private static boolean invalid(PartDesign d) { return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID); }

    private static PartDesign again(PartDesign d, Function<Fields, PartDesign> reader) {
        return reader.apply(Fields.of(Json.parse(Json.write(d.toJson())), "design"));
    }

    private static List<String> shown(Record r) { return RecordEditor.parameters(r).stream().map(RecordEditor.Parameter::name).toList(); }

    // ---------------------------------------------------------------- pumps and motors

    @Test void pumpsAreWhatCataloguePumpsAre() {
        HydraulicPumpDesign main = SampleMachinery.excavatorPump(), pilot = SampleMachinery.pilotPump();
        for (HydraulicPumpDesign d : List.of(main, pilot)) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            assertEquals(d, again(d, HydraulicPumpDesign::read), "JSON round trip");
            HydraulicPumpPart p = assertInstanceOf(HydraulicPumpPart.class, d.compile());
            assertEquals(d.circuit(), p.circuit());
            assertEquals("input", p.rotors().get(0).name());
            assertEquals(1, p.rotors().get(0).axis().x(), 1e-12, "its shaft along +x");
            assertEquals(d.massKg(), p.mass().massKg(), 1e-9);
        }
        // Rexroth A10VO 45: about 21 kg; size 140 about 60 kg
        HydraulicPumpDesign a45 = RecordEditor.with(main, "displacementM3", 45 * CC), a140 = RecordEditor.with(main, "displacementM3", 140 * CC);
        assertTrue(a45.massKg() > 21 / 1.5 && a45.massKg() < 21 * 1.5, "A10VO 45: " + a45.massKg());
        assertTrue(a140.massKg() > 60 / 1.5 && a140.massKg() < 60 * 1.5, "A10VO 140: " + a140.massKg());
        // the main pump: 112 cm^3 at 1800 rpm is about 200 L/min, 115 kW at its full pressure, held to 55 kW
        assertEquals(201.6, main.displacementM3() * main.ratedSpeedRpm() * 1000, 0.1);
        assertEquals(115.2e3, main.cornerPowerW(), 0.5e3);
        Pump pump = ((HydraulicPumpPart) main.compile()).pump();
        assertEquals(Pump.Control.POWER_LIMITED, pump.control());
        double w = main.ratedSpeedRpm() * Hydraulics.RPM_RAD_PER_S;
        assertTrue(pump.hydraulicPowerW(w, 300 * BAR) <= 55000 * (1 + 1e-9), "the power limit holds");
        // a power limit shows only on a power-limited pump; a gear pump's displacement is fixed
        assertTrue(shown(main).contains("powerLimitW"));
        assertFalse(shown(pilot).contains("powerLimitW"));
        assertTrue(invalid(RecordEditor.with(pilot, "control", Pump.Control.PRESSURE_COMPENSATED), "control"));
        assertTrue(invalid(RecordEditor.with(main, "powerLimitW", 0.0), "powerLimitW"));
        assertTrue(invalid(RecordEditor.with(main, "displacementM3", -1e-6), "displacementM3"));
        assertTrue(invalid(RecordEditor.with(main, "circuit", " "), "circuit"));
        assertThrows(DesignException.class, () -> RecordEditor.with(main, "reliefPa", 0.0).compile());
    }

    @Test void motorsDriveTracksAndWinches() {
        HydraulicMotorDesign travel = SampleMachinery.travelMotor(), winch = SampleMachinery.hoistWinch();
        for (HydraulicMotorDesign d : List.of(travel, winch)) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            assertEquals(d, again(d, HydraulicMotorDesign::read), "JSON round trip");
            System.out.printf(Locale.ROOT, "hdfv motor %s: %.0f kg, %.1f kN m and %.1f rpm at the output%n", d.name(), d.massKg(), d.maxTorqueNm() / 1000,
                    d.maxOutputRadPerS() / Hydraulics.RPM_RAD_PER_S);
        }
        // Rexroth A2FM 107 without a reducer: about 34 kg
        HydraulicMotorDesign a107 = RecordEditor.with(RecordEditor.with(travel, "displacementM3", 107 * CC), "reductionRatio", 1.0);
        assertTrue(a107.massKg() > 34 / 1.5 && a107.massKg() < 34 * 1.5, "A2FM 107: " + a107.massKg());
        // a 20 t excavator's travel drive: about 30 kN m at the sprocket, 5.6 km/h on a 0.34 m sprocket, 150 to 300 kg
        assertEquals(29.5e3, travel.maxTorqueNm(), 0.5e3);
        assertEquals(5.8, travel.maxOutputRadPerS() * 0.34 * 3.6, 0.2);
        assertTrue(travel.massKg() > 150 && travel.massKg() < 300, "travel drive " + travel.massKg());
        HydraulicMotorPart t = assertInstanceOf(HydraulicMotorPart.class, travel.compile());
        assertNull(t.drum());
        assertEquals(travel.maxTorqueNm(), t.maxTorqueNm(), 1e-6, "the reducer folded into the output's motor");
        assertEquals(travel.reductionRatio() * travel.displacementM3(), t.motor().displacementM3PerRev(), 1e-15);
        assertEquals(List.of("command", "brake"), t.inputs());
        // the hoist winch: 183 m of 22 mm rope in five layers, about 70 kN line pull and 40 t on six falls
        assertEquals(5, winch.ropeLayers());
        assertEquals(40.4e3 * MachineRules.G, winch.hookLoadN(), 0.03 * 40.4e3 * MachineRules.G);
        assertEquals(0.34, winch.hookSpeedMps(), 0.02);
        HydraulicMotorPart w = assertInstanceOf(HydraulicMotorPart.class, winch.compile());
        assertNotNull(w.drum());
        assertEquals(6, w.drum().partsOfLine());
        assertEquals(winch.ropeStartM(), w.drum().initialLengthM());
        assertEquals(List.of("command", "brake", "tension"), w.inputs());
        assertEquals(winch.massKg(), w.mass().massKg(), 1e-6);
        // the drum's values show only on a winch
        assertTrue(shown(winch).contains("ropeDiameterM"));
        assertFalse(shown(travel).contains("ropeDiameterM"));
        // checks
        assertTrue(invalid(RecordEditor.with(winch, "ropeStartM", 40.0), "ropeStartM"), "a start past the longest fall");
        assertTrue(invalid(RecordEditor.with(winch, "drumWidthM", 0.01), "drumWidthM"));
        assertTrue(invalid(RecordEditor.with(winch, "ropeMaxM", 5000.0), "ropeMaxM"), "the drum cannot hold 30 km of rope");
        assertTrue(invalid(RecordEditor.with(travel, "reductionRatio", 0.0), "reductionRatio"));
        assertFalse(invalid(RecordEditor.with(travel, "ropeStartM", 40.0)), "a travel drive's unused winch values are not checked");
        assertTrue(RecordEditor.with(winch, "drumDiameterM", 0.2).check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION && i.parameter().equals("drumDiameterM")),
                "ISO 4308-1 asks 16 rope diameters");
    }

    // ---------------------------------------------------------------- links

    @Test void anExcavatorsFrontReachesItsRange() {
        LinkDesign boom = LinkDesign.standard(), stick = SampleMachinery.excavatorStick();
        for (LinkDesign d : List.of(boom, stick)) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            LinkPart p = assertInstanceOf(LinkPart.class, d.compile());
            LinkPart.Ram ram = assertInstanceOf(LinkPart.Ram.class, p.actuator());
            Part.Joint j = p.joint();
            assertEquals(Part.Joint.Kind.REVOLUTE, j.kind());
            assertEquals(1, j.axis().z(), 1e-12, "turning about z");
            // AeroCore's ram length agrees with the design's, and stays within the cylinder's closed and open lengths over the range
            for (int i = 0; i <= 20; i++) {
                double q = j.minimum() + (j.maximum() - j.minimum()) * i / 20;
                double l = p.ramLength(ram, q);
                assertEquals(d.ramLengthM(q), l, 1e-12);
                assertTrue(l >= d.closedLengthM() - 1e-3 && l <= d.openLengthM() + 1e-3, d.name() + " at " + Math.toDegrees(q) + " deg: " + l);
                assertTrue(Math.abs(p.lever(ram, q)) > LinkDesign.MIN_LEVER, "never at its dead centre");
            }
            System.out.printf(Locale.ROOT, "hdfv link %s: %.0f kg, ram %.3f..%.3f m, %.0f kN push, %.0f kN m at the least lever%n", d.name(), d.massKg(),
                    d.closedLengthM(), d.openLengthM(), d.cylinder().maxPushN(d.reliefPa()) / 1000, d.ramEffort() / 1000);
            assertEquals(d.massKg(), p.mass().massKg(), 1e-6);
            assertTrue(p.mountPoints().containsKey("tip"));
            assertEquals(d.lengthM(), p.mountPoints().get("tip").position().x(), 1e-12);
            assertFalse(p.collision().isEmpty());
            assertEquals(List.of("command", "float"), p.inputs());
        }
        // CAT 320 / PC200 class: two 120 mm boom cylinders at 350 bar push about 750 kN; the boom with them about 1.5 t, the stick about 0.8 t
        assertEquals(752e3, boom.cylinder().maxPushN(boom.reliefPa()), 2e3);
        assertTrue(boom.massKg() > 1500 / 1.5 && boom.massKg() < 1500 * 1.5, "boom " + boom.massKg());
        assertTrue(stick.massKg() > 800 / 1.5 && stick.massKg() < 800 * 1.5, "stick " + stick.massKg());
        // a boom cylinder of 120/85 x 1335 mm: about 170 kg (130 to 180 kg in that class)
        assertEquals(170, MachineRules.cylinderKg(0.12, 0.085, 1.335, 350 * BAR), 10);
        // a ram too short for the range, one too long, one over its dead centre
        assertTrue(invalid(RecordEditor.with(boom, "ramStrokeM", 0.8), "ramStrokeM"), "a 0.8 m stroke cannot reach -40..55 degrees");
        assertTrue(invalid(RecordEditor.with(boom, "upperDeg", 85.0), "ramStrokeM"), "past its open length");
        assertTrue(invalid(RecordEditor.with(RecordEditor.with(boom, "rodPinYM", 0.0), "basePinYM", 0.0), "basePinXM"), "pins on the joint's line: over centre");
        assertTrue(invalid(RecordEditor.with(boom, "holdPa", 300 * BAR), "holdPa"));
        assertTrue(invalid(RecordEditor.with(boom, "ramRodM", 0.15), "ramRodM"));
        assertTrue(invalid(RecordEditor.with(boom, "plateM", 0.3), "plateM"));
        assertTrue(invalid(RecordEditor.with(boom, "startDeg", 70.0), "startDeg"));
        assertTrue(invalid(RecordEditor.with(boom, "endless", true), "endless"), "a ram does not turn a joint round");
        assertThrows(DesignException.class, () -> RecordEditor.with(boom, "lengthM", 0.0).compile());
    }

    @Test void everyKindOfLinkIsAWorkingLink() {
        List<LinkDesign> links = List.of(SampleMachinery.excavatorBoom(), SampleMachinery.excavatorStick(), SampleMachinery.loaderArm(), SampleMachinery.craneBoomSection(),
                SampleMachinery.latticeBoom(), SampleMachinery.mastStage(), SampleMachinery.swingRing(), SampleMachinery.articulationHinge(),
                SampleMachinery.platformBoom(), SampleMachinery.placingBoom(), SampleMachinery.outriggerBeam());
        for (LinkDesign.LinkKind kind : LinkDesign.LinkKind.values()) {
            LinkDesign d = LinkDesign.standard(kind);
            assertEquals(kind, d.linkKind());
            assertTrue(links.contains(d), "a sample of every kind");
        }
        for (LinkDesign d : links) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            assertEquals(d, again(d, LinkDesign::read), d.name() + ": JSON round trip");
            LinkPart p = assertInstanceOf(LinkPart.class, d.compile());
            assertEquals(d.massKg(), p.mass().massKg(), 1e-6 * d.massKg());
            System.out.printf(Locale.ROOT, "hdfv link %s (%s): %.0f kg, %s%n", d.name(), d.linkKind(), d.massKg(), p.mountPoints().keySet());
        }
        // sliding kinds slide along their length, their ram along the slide
        LinkDesign section = SampleMachinery.craneBoomSection(), mast = SampleMachinery.mastStage(), beam = SampleMachinery.outriggerBeam();
        assertEquals(Part.Joint.Kind.PRISMATIC, section.joint().kind());
        assertEquals(1, section.joint().axis().x(), 1e-12);
        assertEquals(1, mast.joint().axis().y(), 1e-12);
        assertEquals(1, beam.joint().axis().z(), 1e-12);
        assertEquals(1, section.leverM(3), 1e-9);
        assertEquals(section.closedLengthM(), section.ramLengthM(0), 1e-9);
        assertEquals(section.openLengthM(), section.ramLengthM(section.upperM()), 1e-9);
        assertTrue(((LinkPart) section.compile()).mountPoints().containsKey("nest"), "the next section nests in it");
        assertTrue(invalid(RecordEditor.with(section, "upperM", 9.5), "upperM"), "sliding out of its sleeve");
        // the swing ring: endless about y, its house on "upper", 62 kN m and 13 rpm
        LinkDesign ring = SampleMachinery.swingRing();
        Part.Joint j = ring.joint();
        assertEquals(1, j.axis().y(), 1e-12);
        assertTrue(Double.isInfinite(j.minimum()) && Double.isInfinite(j.maximum()));
        assertTrue(((LinkPart) ring.compile()).mountPoints().containsKey("upper"));
        assertInstanceOf(LinkPart.Slew.class, ((LinkPart) ring.compile()).actuator());
        assertEquals(62e3, ring.slewTorqueNm(), 2e3);
        assertEquals(13.0, ring.slewRateRadPerS() / Hydraulics.RPM_RAD_PER_S, 1.0);
        assertTrue(invalid(RecordEditor.with(section, "actuation", LinkDesign.Actuation.SLEW), "actuation"), "a slew does not slide");
        // the articulation: about y on its front frame's point
        LinkDesign hinge = SampleMachinery.articulationHinge();
        assertEquals(1, hinge.joint().axis().y(), 1e-12);
        assertTrue(((LinkPart) hinge.compile()).mountPoints().containsKey("front"));
        // a held joint and a free pin take no command
        LinkDesign held = RecordEditor.with(SampleMachinery.latticeBoom(), "actuation", LinkDesign.Actuation.HELD);
        assertNull(((LinkPart) held.compile()).actuator());
        assertTrue(held.compile().inputs().isEmpty());
        assertInstanceOf(LinkPart.Free.class, ((LinkPart) RecordEditor.with(held, "actuation", LinkDesign.Actuation.FREE).compile()).actuator());
        // the screen: a slew's link hides the ram's values, a ram's hides the slew's
        assertFalse(shown(ring).contains("ramBoreM"));
        assertTrue(shown(ring).contains("slewRatio"));
        assertFalse(shown(SampleMachinery.excavatorBoom()).contains("slewRatio"));
        assertFalse(shown(ring).contains("lowerDeg"), "an endless joint has no limits");
        assertTrue(shown(section).contains("upperM") && !shown(section).contains("upperDeg"));
    }

    // ---------------------------------------------------------------- hooks

    @Test void aHookBlockHangsOnItsFalls() {
        HookDesign d = SampleMachinery.craneHook40t();
        assertTrue(d.check().isEmpty(), d.check().toString());
        assertEquals(d, again(d, HookDesign::read), "JSON round trip");
        System.out.printf(Locale.ROOT, "hdfv hook %s: %.0f kg (%.2f %% of its rating), %d sheaves of %.2f m, the rope carries %.1f t%n", d.name(), d.massKg(),
                100 * d.massKg() / d.ratedLoadKg(), d.sheaves(), d.sheaveDiameterM(), d.ropeCapacityN() / MachineRules.G / 1000);
        // hook blocks weigh 0.5 to 1.5 % of their rating (a 40 t three-sheave block some 400 kg)
        assertTrue(d.massKg() > 0.005 * d.ratedLoadKg() && d.massKg() < 0.015 * d.ratedLoadKg(), "block " + d.massKg());
        assertEquals(3, d.sheaves());
        assertTrue(d.ropeCapacityN() > (d.ratedLoadKg() + d.massKg()) * MachineRules.G, "six falls of 22 mm carry 40 t");
        HangPart p = assertInstanceOf(HangPart.class, d.compile());
        assertTrue(p.mountPoints().containsKey("rope") && p.mountPoints().containsKey("load"));
        assertEquals(-d.heightM(), p.mountPoints().get("load").position().y(), 1e-12);
        assertTrue(p.mass().centre().y() < 0, "it hangs under its rope point");
        assertEquals(d.massKg(), p.mass().massKg(), 1e-9);
        assertFalse(p.collision().isEmpty());
        assertTrue(p.inputs().contains("rope_length"));
        // the falls' E A: six times 100 kN/mm^2 on 0.586 of the 22 mm circle
        assertEquals(6 * 100e9 * 0.586 * Math.PI / 4 * 0.022 * 0.022, d.stiffnessN(), 1e-3);
        // a rope that breaks below its rating is no hook block; one below its working load limit is an extrapolation
        assertTrue(invalid(RecordEditor.with(d, "ropeDiameterM", 0.008), "ropeDiameterM"));
        HookDesign two = RecordEditor.with(d, "partsOfLine", 2);
        assertFalse(invalid(two));
        assertTrue(two.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION && i.parameter().equals("hookLoadN")), two.check().toString());
        assertTrue(invalid(RecordEditor.with(d, "partsOfLine", 0), "partsOfLine"));
        assertTrue(RecordEditor.with(d, "hookKind", HookDesign.HookKind.RAMSHORN).massKg() > d.massKg());
        assertThrows(DesignException.class, () -> RecordEditor.with(d, "ratedLoadKg", 0.0).compile());
    }
}
