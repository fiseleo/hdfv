package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.acoustics.PistonEngineNoise;
import dev.mcmod.aerocore.api.acoustics.Spectrum;
import dev.mcmod.aerocore.api.acoustics.Voice;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** A vehicle's sound from its parts (0.19): the engines' firing, the parts that sound, the levels a listener hears. */
class VehicleSoundTest {
    static { HdfvParts.register(); }

    private static double[] gaps(double[] angles) {
        double[] a = angles.clone();
        Arrays.sort(a);
        double[] g = new double[a.length];
        for (int i = 0; i < a.length; i++) { g[i] = (i + 1 < a.length ? a[i + 1] : a[0] + 720) - a[i]; }
        return g;
    }

    @Test void theSampleEnginesFireAsTheirCranksAllow() {
        PistonEngineNoise.Spec four = SoundProfiles.piston(SampleEngines.inlineFour(), 1);
        for (double g : gaps(four.openingDeg())) { assertEquals(180, g, 1e-9, "inline four"); }
        assertEquals(1, four.banks());
        assertTrue(four.displacementL() > 1.5 && four.displacementL() < 2.5, "about two litres: " + four.displacementL());
        PistonEngineNoise.Spec v8 = SoundProfiles.piston(SampleEngines.vEightOhv(), 1);
        assertEquals(2, v8.banks());
        for (double g : gaps(v8.openingDeg())) { assertEquals(90, g, 1e-9, "V8"); }
        PistonEngineNoise.Spec diesel = SoundProfiles.piston(SampleEngines.turboDiesel(), 1);
        assertTrue(diesel.diesel() && diesel.turboBlades() > 0 && !diesel.blowOffValve(), "a turbo diesel surges, it has no blow-off valve");
    }

    private static Vehicle sedan() { return SampleBlueprints.sedan().compile(); }

    @Test void theSedanSoundsFromItsEngineTyresAndBody() {
        VehicleSound s = VehicleSound.of(sedan(), 5);
        assertNotNull(s);
        long engines = s.sources().stream().filter(x -> x.group() == VehicleSound.Group.ENGINES).count();
        long tyres = s.sources().stream().filter(x -> x.voice() instanceof dev.mcmod.aerocore.api.acoustics.TyreNoise).count();
        long wind = s.sources().stream().filter(x -> x.voice() instanceof dev.mcmod.aerocore.api.acoustics.FlowNoise).count();
        assertEquals(1, engines);
        assertEquals(4, tyres, "four tyres");
        assertEquals(1, wind, "and the wind");
        assertTrue(s.sources().stream().anyMatch(x -> x.partId().equals("horn") && x.voice() instanceof dev.mcmod.aerocore.api.acoustics.Horn), "a horn");
        assertTrue(s.sources().stream().anyMatch(x -> x.partId().equals("relay") && x.inside()), "the indicators' relay, heard inside");
        assertTrue(s.cabin().gain() < 0.1, "a closed cabin takes 20 dB or more off: " + s.cabin());
        assertFalse(s.openBody());
    }

    @Test void itSingsAtItsFiringFrequencyAndIsAsLoudAsACar() {
        Vehicle car = sedan();
        float[] at3000 = SoundBench.render(car, new SoundBench.Point(3000, 0.8, 0.6, true, 1, 0, 0), 7.5, false, 3, 2);
        Spectrum.Power p = Spectrum.welch(at3000, Voice.RATE, 16384);
        double on = 0, off = 0;
        for (int k = 1; k <= 5; k++) { on += p.band(100 * k - 2, 100 * k + 2); off += p.band(100 * k + 48, 100 * k + 52); }
        assertTrue(10 * Math.log10(on / off) > 8, "lines at 100 Hz multiples (four cylinders, 3000 rpm)");
        VehicleSound s = VehicleSound.of(car, 2);
        s.update(SoundBench.inputs(car, SoundBench.Point.idle(800)));
        double idle = s.levelAt(7.5, false);
        assertTrue(idle > 40 && idle < 65, "idling at 7.5 m: " + idle + " dB");
        s.update(SoundBench.inputs(car, new SoundBench.Point(4000, 1, 1, true, 1, 17, 0)));
        double pulling = s.levelAt(7.5, false);
        assertTrue(pulling > 68 && pulling < 85, "full throttle at 60 km/h, 7.5 m: " + pulling + " dB");
        // inside, the engine comes through the walls
        s.update(SoundBench.inputs(car, SoundBench.Point.idle(800)));
        assertTrue(s.levelAt(1.5, true) < s.levelAt(1.5, false) - 15, "the cabin keeps the idle out");
        float[] again = SoundBench.render(car, new SoundBench.Point(3000, 0.8, 0.6, true, 1, 0, 0), 7.5, false, 3, 2);
        assertArrayEquals(at3000, again, "the same vehicle seed sounds the same");
    }
}
