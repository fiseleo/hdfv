package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.DrumPart;
import dev.mcmod.aerocore.api.vehicle.part.LegPart;
import dev.mcmod.aerocore.api.vehicle.part.TrackPart;
import dev.mcmod.hdfv.design.RecordEditor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.26: the undercarriage designs (TrackDesign, DrumDesign, OutriggerDesign) against the real machines their samples
 * are: ground pressures, masses, a drum's centrifugal force; their JSON, their compiled AeroCore parts and the checks
 * that stop what is none. Built directly (no part registry).
 */
class UndercarriageDesignTest {
    private static boolean invalid(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(parameter));
    }

    private static boolean extrapolated(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION && i.parameter().equals(parameter));
    }

    private static PartDesign again(PartDesign d, Function<Fields, PartDesign> reader) {
        return reader.apply(Fields.of(Json.parse(Json.write(d.toJson())), "design"));
    }

    // ---------------------------------------------------------------- tracks

    @Test void tracksBearAsRealCrawlersDo() {
        TrackDesign excavator = SampleMachinery.excavatorTracks20t(), dozer = SampleMachinery.dozerTracksD6(), mini = SampleMachinery.miniExcavatorTracks();
        for (TrackDesign d : List.of(excavator, dozer, mini)) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            double kPa = d.groundPressurePa() / 1000;
            System.out.printf(Locale.ROOT, "hdfv tracks %s: %.1f kPa, %.0f kg (%.2f of the machine), %d shoes a side, frame plate %.1f mm, %.0f N/mm a roller%n",
                    d.name(), kPa, d.massKg(), d.massKg() / d.designMassKg(), d.shoesPerSide(), d.framePlateM() * 1000, d.stationStiffnessNPerM() / 1000);
            // crawlers' ground pressure: 25 kPa (mini excavators) to some 150 kPa (large dozers)
            assertTrue(kPa >= 25 && kPa <= 150, d.name() + " " + kPa + " kPa");
            // a crawler's lower structure (frames, rollers, two track groups) is about a third of its operating weight
            assertTrue(d.massKg() > 0.2 * d.designMassKg() && d.massKg() < 0.5 * d.designMassKg(), d.name() + " " + d.massKg() + " kg");
            assertEquals(d, again(d, TrackDesign::read), "JSON round trip");
            // the auto stiffness: the design weight over all road wheels sinks STATIC_SINK_M
            assertEquals(d.designMassKg() * MachineRules.G, 2 * d.roadWheels() * d.stationStiffnessNPerM() * TrackDesign.STATIC_SINK_M, 1e-6 * d.designMassKg());
        }
        // CAT 320 class: 22 t on 600 mm x 3.65 m, about 50 kPa; D6: 20.5 t on 560 mm x 2.61 m, about 70 kPa
        assertEquals(49.3, excavator.groundPressurePa() / 1000, 0.5);
        assertEquals(68.8, dozer.groundPressurePa() / 1000, 0.5);
        // a 190 mm pitch's steel shoe stands about 140 mm under the rollers (link, plate, grouser)
        assertEquals(0.143, excavator.shoeThicknessM(), 0.005);
        // a skid turn on the spot meets mu_t W L / 4
        assertEquals(0.6 * 22000 * MachineRules.G * 3.65 / 4, excavator.turningMomentNm(), 1e-6);
        // a given stiffness is kept
        assertEquals(1e6, RecordEditor.with(excavator, "rollerStiffnessNPerM", 1e6).stationStiffnessNPerM(), 0);
    }

    @Test void tracksCompileToAWearingTrackPart() {
        TrackDesign d = TrackDesign.standard();
        Part p = d.compile();
        TrackPart t = assertInstanceOf(TrackPart.class, p);
        assertEquals(d.massKg(), p.mass().massKg(), 1e-6 * d.massKg(), "its mass is its estimate");
        assertEquals(0, p.mass().centre().z(), 1e-9, "the two sides balance");
        assertEquals(List.of("left", "right"), p.rotors().stream().map(Part.Rotor::name).toList());
        assertEquals(List.of(TrackPart.TRACKS), p.conditions(), "its tracks wear");
        TrackPart.Spec s = t.spec();
        assertEquals(d.gaugeM(), s.gaugeM());
        assertEquals(d.shoeThicknessM(), s.shoeThicknessM());
        assertEquals(d.stationStiffnessNPerM(), s.stationStiffnessNPerM());
        assertEquals(d.sideInertiaKgM2(), s.sideInertiaKgM2());
        assertEquals(d.roadWheels() + 2, t.rollers(0).size(), "road wheels, idler, sprocket");
        assertTrue(p.issues().isEmpty());
        // an extrapolation compiles and is carried
        TrackDesign heavy = RecordEditor.with(d, "designMassKg", 200000.0);
        assertTrue(extrapolated(heavy, "groundPressurePa"), "200 t on a 20 t machine's tracks: " + heavy.check());
        assertFalse(heavy.compile().issues().isEmpty());
    }

    @Test void trackChecksStopWhatIsNoUndercarriage() {
        TrackDesign d = TrackDesign.standard();
        assertTrue(invalid(RecordEditor.with(d, "gaugeM", 0.5), "gaugeM"), "tracks overlapping");
        assertTrue(invalid(RecordEditor.with(d, "roadWheels", 1), "roadWheels"));
        assertTrue(invalid(RecordEditor.with(d, "roadWheels", 30), "roadWheels"), "30 road wheels of 0.1 m on 3.65 m overlap");
        assertTrue(invalid(RecordEditor.with(d, "contactLengthM", -1.0), "contactLengthM"));
        assertTrue(invalid(RecordEditor.with(d, "shoeWidthM", 0.0), "shoeWidthM"));
        assertTrue(invalid(RecordEditor.with(d, "rollerStiffnessNPerM", -5.0), "rollerStiffnessNPerM"));
        assertTrue(invalid(RecordEditor.with(d, "parkNm", Double.NaN), "parkNm"));
        assertThrows(DesignException.class, () -> RecordEditor.with(d, "pitchM", 0.0).compile());
        // out of real crawlers' range: still computed
        TrackDesign narrow = RecordEditor.with(d, "shoeWidthM", 0.15);
        assertTrue(extrapolated(narrow, "shoeWidthM"));
        assertDoesNotThrow(narrow::compile);
    }

    // ---------------------------------------------------------------- drums

    @Test void drumsVibrateAsRealRollersDo() {
        DrumDesign cs56 = SampleMachinery.rollerDrum11t(), pad = SampleMachinery.padfootDrum11t(), tandem = SampleMachinery.tandemDrum();
        for (DrumDesign d : List.of(cs56, pad, tandem)) {
            assertTrue(d.check().isEmpty(), d.name() + ": " + d.check());
            System.out.printf(Locale.ROOT, "hdfv drum %s: %.0f kg, m e %.2f kg m, %.0f kN at %.0f Hz, %.1f N/mm, %d slices%n", d.name(), d.massKg(), d.eccentricKgM(),
                    d.dynamicForceN() / 1000, d.frequencyHz(), d.linearLoadNPerM() / 1000, d.slices());
            assertEquals(d, again(d, DrumDesign::read), "JSON round trip");
        }
        // CS56B: 266 kN at 1.9 mm and 31 Hz (the high amplitude); the nominal amplitude over the drum's own mass gives it within 15 %
        assertEquals(266e3, cs56.dynamicForceN(), 0.15 * 266e3);
        // an 11 t roller's drum (a 28 mm shell, its end plates and vibrator) weighs about 3.5 t of the 5.5 t on its drum module
        assertTrue(cs56.massKg() > 3500 / 1.5 && cs56.massKg() < 3500 * 1.5, "drum " + cs56.massKg());
        assertTrue(pad.massKg() > cs56.massKg() + 500, "padfoot pads add some 0.9 t");
        // the eccentric is the amplitude times the vibrating mass
        assertEquals(cs56.amplitudeM() * cs56.massKg(), cs56.eccentricKgM(), 1e-12);
        // compiled: AeroCore's drum of its spec
        DrumPart p = assertInstanceOf(DrumPart.class, cs56.compile());
        assertEquals(cs56.diameterM() / 2, p.spec().radiusM(), 1e-12);
        assertEquals(cs56.dynamicForceN(), p.spec().dynamicForceN(), 1e-6);
        assertEquals(9, p.spec().slices());
        assertEquals(cs56.massKg(), p.mass().massKg(), 1e-6);
        assertEquals(List.of("drum"), p.rotors().stream().map(Part.Rotor::name).toList());
        assertEquals(DrumPart.Shell.PADFOOT, ((DrumPart) pad.compile()).spec().shell());
    }

    @Test void drumChecksStopWhatIsNoDrum() {
        DrumDesign d = DrumDesign.standard();
        assertTrue(invalid(RecordEditor.with(d, "shellM", 0.8), "shellM"), "a shell thicker than the drum's radius");
        assertTrue(invalid(RecordEditor.with(d, "widthM", 0.0), "widthM"));
        assertTrue(invalid(RecordEditor.with(d, "amplitudeM", -0.001), "amplitudeM"));
        assertTrue(invalid(RecordEditor.with(d, "designLoadKg", 0.0), "designLoadKg"));
        assertThrows(DesignException.class, () -> RecordEditor.with(d, "diameterM", -1.0).compile());
        // a static drum (no vibrator) is a drum
        DrumDesign rigid = RecordEditor.with(RecordEditor.with(d, "amplitudeM", 0.0), "frequencyHz", 0.0);
        assertTrue(rigid.check().isEmpty(), rigid.check().toString());
        assertEquals(0, ((DrumPart) rigid.compile()).spec().dynamicForceN());
        assertTrue(extrapolated(RecordEditor.with(d, "amplitudeM", 0.004), "amplitudeM"));
        assertTrue(extrapolated(RecordEditor.with(d, "designLoadKg", 20000.0), "linearLoadNPerM"));
    }

    // ---------------------------------------------------------------- outriggers

    @Test void outriggersCarryTheirRatingOnATelescopingLeg() {
        OutriggerDesign d = SampleMachinery.outrigger();
        assertTrue(d.check().isEmpty(), d.check().toString());
        assertEquals(d, again(d, OutriggerDesign::read), "JSON round trip");
        System.out.printf(Locale.ROOT, "hdfv outrigger %s: %.0f kg, jack bore %.0f mm, %.2f MPa on the pad%n", d.name(), d.massKg(), d.jackBoreM() * 1000, d.padPressurePa() / 1e6);
        // an all-terrain crane's 40 t jack with its pad: some 250 kg
        assertTrue(d.massKg() > 250 / 1.5 && d.massKg() < 250 * 1.5, "jack " + d.massKg());
        assertEquals(40000 * MachineRules.G / (Math.PI / 4 * 0.25), d.padPressurePa(), 1e-6);
        LegPart p = assertInstanceOf(LegPart.class, d.compile());
        assertNotNull(p.telescope());
        assertEquals(d.strokeM(), p.telescope().strokeM());
        assertEquals(d.extensionTimeS(), p.telescope().timeS());
        assertTrue(p.inputs().contains("retract"));
        assertEquals(d.massKg(), p.mass().massKg(), 1e-9);
        assertTrue(invalid(RecordEditor.with(d, "strokeM", 1.5), "strokeM"), "a stroke longer than the jack");
        assertTrue(invalid(RecordEditor.with(d, "padDiameterM", 0.1), "padDiameterM"));
        assertTrue(invalid(RecordEditor.with(d, "ratedLoadKg", -1.0), "ratedLoadKg"));
        assertTrue(extrapolated(RecordEditor.with(d, "padDiameterM", 0.3), "padPressurePa"), "40 t on a 0.3 m pad: 5.5 MPa");
    }
}
