package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.SampleGearboxes;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 0.14: the room parts take (Envelope): the compact piston engines, gearboxes, transfer cases. */
class EngineEnvelopeTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    static List<PistonEngineDesign> pistons() {
        return Designs.SAMPLES.stream().map(s -> s.design().get()).filter(d -> d instanceof PistonEngineDesign).map(d -> (PistonEngineDesign) d).toList();
    }

    @Test void theInlineFourIsCompact() {
        Envelope.Box b = PistonEngineLayout.of(SampleEngines.inlineFour()).envelope().bounds();
        Vec3d size = b.size();
        assertEquals(0.73, size.y(), 0.03, "about 0.73 m tall (0.87 before 0.14): " + b);
        assertTrue(b.max().y() < 0.45, "nothing above the coils: " + b);
        assertTrue(b.min().x() > -0.28 && b.max().x() < 0.41, "from the starter to the radiator: " + b);
        assertTrue(b.min().z() > -0.43 && b.max().z() < 0.42, "the air box and the radiator's tanks the widest: " + b);
    }

    @Test void everyPistonSampleKeepsItsRunnersAndItsExhaustForward() {
        assertFalse(pistons().isEmpty());
        for (PistonEngineDesign e : pistons()) {
            PistonEngineLayout l = PistonEngineLayout.of(e);
            Envelope env = l.envelope();
            double coilsTop = env.boxes().stream().filter(x -> x.name().equals("coils")).mapToDouble(x -> x.max().y()).max().orElseThrow();
            for (int b = 0; b < l.banks; b++) {
                for (int i = 0; i < l.per; i++) {
                    List<Vec3d> runner = l.runner(b, i);
                    assertEquals(PistonEngineLayout.positive(e.intake().runnerLengthM(), 0.3), PistonEngineLayout.length(runner), 0.01 * e.intake().runnerLengthM(), e.name() + " runner length");
                    if (l.compactIntake()) {
                        for (Vec3d p : runner) { assertTrue(p.y() + l.runnerRadius() <= coilsTop + 1e-6, e.name() + " runner under the coils' top"); }
                    }
                }
                for (Vec3d p : l.downPipe(b)) { assertTrue(p.x() > -l.front, e.name() + ": nothing of the exhaust behind the block (a gearbox's bell is there)"); }
            }
            if (l.liquid()) {
                double radiator = env.boxes().stream().filter(x -> x.name().equals("radiator")).mapToDouble(x -> x.max().x()).max().orElseThrow();
                assertEquals(env.bounds().max().x(), radiator, 1e-9, e.name() + ": the radiator is the front-most");
            }
            assertEquals(-l.front, env.outputX(), 1e-12, "a gearbox bolts to the block's rear face");
        }
    }

    @Test void gearboxesAndTransferCases() {
        GearboxDesign manual = SampleGearboxes.fiveSpeedManual(), auto = SampleGearboxes.sixSpeedAutomatic();
        assertTrue(manual.gearbox().transverse(), "the sample five-speed is transverse");
        Envelope.Box t = Envelopes.of(manual).bounds();
        assertTrue(t.size().z() > t.size().x(), "a transverse box lies across: " + t);
        Envelope a = Envelopes.of(auto);
        assertEquals(0.32 * Math.cbrt(auto.gearbox().scale()), a.inputX(), 1e-12, "the bell's face");
        assertTrue(a.outputX() < 0 && a.bounds().size().x() > 0.9, "a 600 N m automatic about a metre long: " + a.bounds());
        TransferCaseDesign pt = SampleGearboxes.partTime();
        Envelope c = Envelopes.of(pt);
        assertTrue(c.inputX() > 0 && c.outputX() < 0, "input in front, output behind");
        assertTrue(c.boxes().stream().anyMatch(x -> x.name().equals("lever")), "a part-time case has its lever");
    }

    @Test void everySampleThatGoesInABodyHasAnEnvelope() {
        for (Designs.Sample s : Designs.SAMPLES) {
            PartDesign d = s.design().get();
            boolean expected = d instanceof PistonEngineDesign || d instanceof GearboxDesign || d instanceof TransferCaseDesign;
            if (expected) { assertNotNull(Envelopes.of(d), s.id()); }
        }
    }
}
