package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The exhaust the engines show for their plumes (0.11, Exhaust): three values after each part's own shown values,
 * zero while nothing burns, sensible while it does (temperature, Mach number, exit pressure), rounded so that a steady
 * engine shows the same numbers every tick (its clients get no new values to send).
 */
class ShownExhaustTest {
    static { HdfvParts.register(); }

    private static double shown(JetFamilyRuntimeTest.Stand s, PartDesign d, int which) { return s.state.shown("engine", Exhaust.first(d) + which); }

    private static void assertRounded(double value, double step, String what) {
        assertEquals(0, Math.abs(value / step - Math.rint(value / step)), 1e-6, what + " " + value + " is a multiple of " + step);
    }

    @Test void everyEngineShowsItsExhaustAfterItsOwnValues() {
        List<PartDesign> engines = List.of(SampleEngines.turbojet(), SampleEngines.modelTurbojet(), SampleJets.j79(), SampleEngines.turbofan(), SampleEngines.turboprop(),
                SampleEngines.turboshaft(), SampleJets.bomarcRamjet(), SampleJets.x51Scramjet(), SampleJets.argus(), SampleJets.lockwood(), SampleJets.knsbMotor(),
                SampleJets.apcpBooster(), SampleJets.j58());
        for (PartDesign d : engines) {
            assertTrue(Exhaust.first(d) >= 0, d.type());
            assertEquals(Exhaust.first(d) + Exhaust.COUNT, d.compile().shownValues(), d.type() + ": the exhaust comes last");
        }
        assertEquals(3, SampleEngines.inlineFour().compile().shownValues(), "a piston engine shows its load, whether it runs and (0.19) its boost");
        assertEquals(-1, Exhaust.first(SampleEngines.inlineFour()));
    }

    @Test void aBurningRamjetShowsAHotSupersonicJetAndAnUnlitOneNothing() {
        RamjetDesign r = SampleJets.bomarcRamjet();
        JetFamilyRuntimeTest.Stand s = new JetFamilyRuntimeTest.Stand(r).set("throttle", 1).set("ignition", 1);
        s.at(0.8, 0).run(0.3);
        assertEquals(0, shown(s, r, Exhaust.KELVIN), "unlit: nothing to draw");
        s.at(2.0, 10000).run(1);
        double k = shown(s, r, Exhaust.KELVIN), m = shown(s, r, Exhaust.MACH);
        assertTrue(k > 1500 && k < 2600, "combustor gas " + k + " K");
        assertTrue(m > 1, "a Mach 2 ramjet's jet is supersonic: " + m);
        assertEquals(1, shown(s, r, Exhaust.PRESSURE), 1e-9);
        assertRounded(k, 10, "temperature");
        assertRounded(m, 0.05, "Mach");
    }

    @Test void aBoosterShowsItsPropellantsFlameAndNozzle() {
        SolidRocketDesign b = SampleJets.apcpBooster();
        JetFamilyRuntimeTest.Stand s = new JetFamilyRuntimeTest.Stand(b);
        s.run(0.1);
        assertEquals(0, shown(s, b, Exhaust.KELVIN));
        s.set("fire", 1).run(0.2);
        assertEquals(Exhaust.round(b.performance().design().propellant().flameTemperatureK(), 10), shown(s, b, Exhaust.KELVIN), 1e-9);
        assertTrue(shown(s, b, Exhaust.MACH) > 2, "an expanding nozzle: Mach " + shown(s, b, Exhaust.MACH));
        double ratio = shown(s, b, Exhaust.PRESSURE);
        assertTrue(ratio >= 0.4 && ratio < 10, "exit over ambient " + ratio);
    }

    @Test void aSteadyTurbojetShowsTheSameExhaustEveryTick() {
        JetEngineDesign j = SampleEngines.turbojet();
        JetFamilyRuntimeTest.Stand s = new JetFamilyRuntimeTest.Stand(j).set("throttle", 1).set("ignition", 1);
        s.run(60);
        double k = shown(s, j, Exhaust.KELVIN), m = shown(s, j, Exhaust.MACH), p = shown(s, j, Exhaust.PRESSURE);
        assertTrue(k > 700 && k < 1300, "exhaust " + k + " K");
        assertTrue(m > 0.5 && m <= 1.05, "a convergent nozzle: Mach " + m);
        assertTrue(p >= 1 && p < 3, "exit over ambient " + p);
        for (int tick = 0; tick < 20; tick++) {
            s.run(0.05);
            assertEquals(k, shown(s, j, Exhaust.KELVIN), 1e-9, "tick " + tick);
            assertEquals(m, shown(s, j, Exhaust.MACH), 1e-9, "tick " + tick);
            assertEquals(p, shown(s, j, Exhaust.PRESSURE), 1e-9, "tick " + tick);
        }
    }
}
