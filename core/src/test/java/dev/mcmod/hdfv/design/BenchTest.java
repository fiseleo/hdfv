package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchTest {
    static { HdfvParts.register(); }

    @Test void theSampleFourHasAProductionLikeFullLoadCurve() {
        PistonEngineDesign e = SampleEngines.inlineFour();
        Bench.PistonCurve c = Bench.fullLoad(e);
        assertSame(e.map(), SampleEngines.inlineFour().map(), "the map is built once per design");
        assertTrue(c.points().size() > 20);
        assertTrue(c.points().stream().allMatch(p -> p.rpm() >= 1000 && p.rpm() <= e.ignition().revLimitRpm()));
        // as EngineDesignTest: 10-13.5 bar BMEP, 45-80 kW per litre, 240-320 g/kWh
        double bmep = c.peakTorque().torqueNm() * 4 * Math.PI / e.displacement();
        assertTrue(bmep > 10e5 && bmep < 13.5e5, "BMEP " + bmep);
        assertTrue(c.peakPower().powerW() / 2000 > 45 && c.peakPower().powerW() / 2000 < 80, "kW/l " + c.peakPower().powerW() / 2000);
        assertTrue(c.bestBsfcGPerKWh() > 240 && c.bestBsfcGPerKWh() < 320, "BSFC " + c.bestBsfcGPerKWh());
        assertTrue(c.peakPower().rpm() > c.peakTorque().rpm());
        for (Bench.PistonPoint p : c.points()) { assertEquals(p.torqueNm() * p.rpm() * Math.PI / 30, p.powerW(), 1e-9 * p.powerW() + 1e-9); }
    }

    @Test void thePushrodVeeEightAndTheSingleCamFourMakeTheirKindOfPower() {
        // a 5.7 l pushrod V8 (LS1 class: about 470 N m, 260 kW) and a 1.6 l single-cam four (about 140 N m, 80 kW): within the usual BMEP of
        // naturally aspirated petrol engines, 9.5-13.5 bar, and power per litre 35-80 kW
        for (PistonEngineDesign e : List.of(SampleEngines.vEightOhv(), SampleEngines.inlineFourSohc())) {
            Bench.PistonCurve c = Bench.fullLoad(e);
            double bmep = c.peakTorque().torqueNm() * 4 * Math.PI / e.displacement(), perLitre = c.peakPower().powerW() / 1000 / (e.displacement() * 1000);
            assertTrue(bmep > 9.5e5 && bmep < 13.5e5, e.name() + " BMEP " + bmep);
            assertTrue(perLitre > 35 && perLitre < 80, e.name() + " kW/l " + perLitre);
        }
    }

    @Test void aJetsThrottleSweepEndsAtItsDesignThrustAndFallsWithAltitude() {
        for (JetEngineDesign e : List.of(SampleEngines.turbojet(), SampleEngines.modelTurbojet())) {
            List<Bench.JetPoint> sea = Bench.throttleSweep(e, Bench.flight(0, 0), 11);
            assertEquals(11, sea.size());
            assertEquals(e.control().idleFraction(), sea.get(0).speedFraction(), 1e-9, e.name());
            double design = e.sizing().point().thrustN();
            // maximum speed is above design (1.02 for the J85 class), but the turbine temperature limiter holds it at design
            assertEquals(design, sea.get(10).thrustN(), 0.01 * design, e.name());
            assertTrue(sea.get(10).point().tt4() <= e.control().turbineInletK() + 1, e.name());
            for (int i = 1; i < sea.size(); i++) { assertTrue(sea.get(i).thrustN() >= sea.get(i - 1).thrustN() - 1e-6, e.name() + " monotonic at " + i); }
            double high = Bench.throttleSweep(e, Bench.flight(6000, 0), 1).get(0).thrustN();
            assertTrue(high < 0.75 * design && high > 0.35 * design, e.name() + ": thrust at 6 km " + high / design);
        }
        Turbojet.Flight cruise = Bench.flight(10000, 250);
        Bench.JetPoint p = Bench.throttleSweep(SampleEngines.turbojet(), cruise, 1).get(0);
        assertTrue(p.thrustN() > 0 && p.tsfcGPerKNs() > Bench.throttleSweep(SampleEngines.turbojet(), Bench.flight(0, 0), 1).get(0).tsfcGPerKNs(),
                "ram drag raises TSFC in flight");
    }

    @Test void bothSampleJetsStartIdleAndRunUpOnTheStand() {
        for (JetEngineDesign e : List.of(SampleEngines.turbojet(), SampleEngines.modelTurbojet())) {
            Bench.JetRun r = Bench.startAndRunUp(e);
            assertTrue(r.lightOffS() > 0 && r.lightOffS() < r.idleS(), e.name() + " light-off " + r.lightOffS() + " idle " + r.idleS());
            // as real engines start: tens of seconds (the start schedule holds the core's acceleration to 1-2 % per second)
            assertTrue(r.idleS() > 5 && r.idleS() < 60, e.name() + " idle " + r.idleS());
            assertEquals(r.idleS() + 1, r.throttleS(), 1e-9);
            double runUp = r.fullThrustS() - r.throttleS();
            // the acceleration schedule takes seconds, not instantly and not forever
            assertTrue(runUp > 0.3 && runUp < 15, e.name() + " run-up " + runUp);
            assertEquals(r.timeS().length, r.thrustN().length);
            assertTrue(r.timeS().length > 10);
        }
    }
}
