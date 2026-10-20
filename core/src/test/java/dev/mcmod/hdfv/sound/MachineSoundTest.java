package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.acoustics.ProcessNoise;
import dev.mcmod.aerocore.api.acoustics.Spectrum;
import dev.mcmod.aerocore.api.acoustics.Voice;
import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.manufacture.Process;
import dev.mcmod.hdfv.manufacture.Station;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How the workshop sounds (0.19): every head and station has its process, loops are seamless, events are made. */
class MachineSoundTest {
    @Test void everyHeadAndStationHasItsSound() {
        for (Head h : Head.values()) {
            ProcessNoise.Process p = MachineSound.process(h.station, h, h.abilities().getFirst().process());
            assertNotEquals(ProcessNoise.Process.HUM, p, h.name());
            MachineSound.Hum hum = MachineSound.machine(h.station, h, h.abilities().getFirst().process(), h.ratedW(), 1, true);
            assertTrue(hum.levelDb() > 60 && hum.levelDb() < 125, h + ": " + hum.levelDb() + " dB");
        }
        assertEquals(ProcessNoise.Process.TURNING, MachineSound.process(Station.LATHE, null, Process.TURN));
        assertEquals(ProcessNoise.Process.BURNER, MachineSound.process(Station.FOUNDRY, null, Process.CAST));
        assertEquals(ProcessNoise.Process.PRESS, MachineSound.process(Station.FORGING_PRESS, null, Process.FORGE));
        assertEquals(ProcessNoise.Process.PLASMA, MachineSound.process(Station.CUTTING_TABLE, Head.PLASMA, Process.PROFILE));
        assertEquals(ProcessNoise.Process.RIVET_IMPACT, MachineSound.process(Station.RIVETER, Head.IMPACT_RIVETER, Process.RIVET));
        for (Process p : Process.values()) { assertNotNull(MachineSound.process(Station.CNC, null, p), p.name()); }
        // a plasma table is far louder than a laser
        double plasma = MachineSound.machine(Station.CUTTING_TABLE, Head.PLASMA, Process.PROFILE, Head.PLASMA.ratedW(), 1, true).levelDb();
        double laser = MachineSound.machine(Station.CUTTING_TABLE, Head.LASER, Process.PROFILE, Head.LASER.ratedW(), 1, true).levelDb();
        assertTrue(plasma > laser + 15, plasma + " / " + laser);
    }

    @Test void aSyncedHumChangesOnlyWhenItMatters() {
        MachineSound.Hum a = MachineSound.machine(Station.LATHE, null, Process.TURN, 7500, 1, true);
        assertFalse(a.differs(MachineSound.machine(Station.LATHE, null, Process.TURN, 7600, 0.95, true)), "small changes are not sent");
        assertTrue(a.differs(MachineSound.machine(Station.LATHE, null, Process.TURN, 7500, 0.5, true)), "the load halved");
        assertTrue(a.differs(MachineSound.machine(Station.LATHE, null, Process.GRIND, 7500, 1, true)), "another process");
        assertTrue(a.differs(null));
    }

    @Test void aLoopIsSeamlessAndAtItsLevel() {
        MachineSound.Hum mill = MachineSound.machine(Station.MILLING_MACHINE, null, Process.MILL, 7500, 1, true);
        float[] loop = MachineSound.bake(mill, 2, 7);
        assertEquals(2 * Voice.RATE, loop.length);
        double sum = 0, step = 0;
        for (int i = 0; i < loop.length; i++) {
            sum += loop[i] * loop[i];
            if (i > 0) { step += Math.abs(loop[i] - loop[i - 1]); }
        }
        double rms = Math.sqrt(sum / loop.length), meanStep = step / (loop.length - 1);
        assertEquals(0.25, rms, 0.01);
        assertTrue(Math.abs(loop[0] - loop[loop.length - 1]) < 6 * meanStep + 0.05, "no click where it loops");
        // the mill's teeth: 4 flutes at 10 rev/s, 40 Hz lines
        Spectrum.Power p = Spectrum.welch(loop, Voice.RATE, 16384);
        assertTrue(p.band(1500, 1700) > p.band(9000, 9200), "the cutter rings near 1.6 kHz");
        assertArrayEquals(loop, MachineSound.bake(mill, 2, 7), "the same seed, the same loop");
    }

    @Test void eventsAreMade() {
        for (MachineSound.Event e : MachineSound.Event.values()) {
            MachineSound.Sound s = MachineSound.event(e, 3, 1);
            assertTrue(s.samples().length > Voice.RATE / 4, e.name());
            float peak = 0;
            for (float v : s.samples()) { peak = Math.max(peak, Math.abs(v)); assertTrue(Float.isFinite(v)); }
            assertTrue(peak > 0.05 && peak < 4, e + " peak " + peak);
            assertTrue(s.levelDb() > 80, e.name());
        }
        assertTrue(MachineSound.event(MachineSound.Event.DOOR, 6, 1).samples().length > MachineSound.event(MachineSound.Event.DOOR, 2, 1).samples().length, "a taller door runs longer");
    }

    @Test void anEngineOnAStandSoundsAlone() {
        VehicleSound s = VehicleSound.engine(dev.mcmod.hdfv.part.engine.SampleEngines.inlineFour(), 3);
        assertEquals(1, s.sources().size());
        assertTrue(s.openBody());
    }
}
