package dev.mcmod.hdfv.design;

import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.SampleJets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The design screen's bench for the 0.10 engines: Mach sweeps, the pulsejet's sweep and cycle, a booster's firing, the afterburner's wet thrust. */
class JetFamilyBenchTest {
    static { HdfvParts.register(); }

    @Test void theRamjetLightsPastItsLightUpMachAndOnlyDragsBelow() {
        List<Bench.MachPoint> s = Bench.machSweep(SampleJets.bomarcRamjet(), 10000);
        assertTrue(s.stream().filter(p -> p.mach() < 1.5).noneMatch(Bench.MachPoint::lit));
        assertTrue(s.stream().filter(p -> p.mach() > 1.6 && p.mach() < 3.5).allMatch(p -> p.lit() && p.thrustN() > 0), s.toString());
        assertTrue(s.stream().filter(p -> p.mach() > 0.6 && p.mach() < 1.4).allMatch(p -> !(p.thrustN() > 0)), "unlit it drags");
        // its thrust has a maximum in Mach (the ideal ramjet's)
        double best = s.stream().mapToDouble(p -> Double.isFinite(p.thrustN()) ? p.thrustN() : 0).max().orElseThrow();
        assertTrue(best > s.get(s.size() - 1).thrustN(), "past its best Mach the thrust falls");
    }

    @Test void theScramjetGivesThrustAcrossItsTable() {
        List<Bench.MachPoint> s = Bench.machSweep(SampleJets.x51Scramjet(), 20000);
        assertEquals(21, s.size());
        // the kerosene lights (with its pilot) from about Mach 4.7; below, the duct only drags
        assertTrue(s.stream().filter(p -> p.mach() >= 4.8).allMatch(p -> p.lit() && p.thrustN() > 0 && p.specificImpulseS() > 500), s.toString());
        assertTrue(s.stream().filter(p -> p.mach() < 4.45).noneMatch(Bench.MachPoint::lit));
    }

    @Test void theTurboRamjetOpensItsBypassFast() {
        List<Bench.MachPoint> s = Bench.machSweep(SampleJets.j58(), 20000);
        assertEquals(0, s.get(0).bypassRatio(), 1e-12);
        Bench.MachPoint m3 = s.stream().filter(p -> Math.abs(p.mach() - 3) < 0.07).findFirst().orElseThrow();
        assertTrue(m3.bypassRatio() > 0.2 && m3.thrustN() > 0, m3.toString());
    }

    @Test void thePulsejetRunsFromRestAndRingsAtItsFrequency() {
        Bench.PulseBench b = Bench.pulsejet(SampleJets.argus(), 0);
        assertTrue(b.sweep().get(0).running() && b.sweep().get(0).thrustN() > 1500, b.sweep().get(0).toString());
        assertEquals(64, b.pressureTracePa().length);
        assertEquals(45, b.frequencyHz(), 15);
        double lo = Double.POSITIVE_INFINITY, hi = 0;
        for (double p : b.pressureTracePa()) { lo = Math.min(lo, p); hi = Math.max(hi, p); }
        assertTrue(hi > 101325 && lo < 101325, "the chamber swings about the air's pressure: " + lo + " to " + hi);
    }

    @Test void aBoosterFiresForItsBurnTime() {
        var k = SampleJets.knsbMotor();
        Bench.RocketBench b = Bench.rocket(k, 0);
        assertEquals(k.performance().burnTimeS(), b.burnS(), 1e-12);
        assertEquals(k.performance().totalImpulse(101325), b.totalImpulse(), 1e-9);
        assertEquals(0, b.thrustN()[b.thrustN().length - 1], 0, "burnt out at the end");
        assertTrue(b.thrustN()[10] > 0 && b.chamberPa()[10] > 1e6);
    }

    @Test void theJ79sAfterburnerAddsHalfAgain() {
        Bench.WetPoint w = Bench.wet(SampleJets.j79(), Bench.flight(0, 0));
        assertEquals(79.6e3, w.thrustN(), 0.12 * 79.6e3);
        assertNull(Bench.wet(dev.mcmod.hdfv.part.engine.SampleEngines.turbojet(), Bench.flight(0, 0)), "none without one");
    }
}
