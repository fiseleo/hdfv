package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.acoustics.FlowNoise;
import dev.mcmod.aerocore.api.acoustics.PistonEngineNoise;
import dev.mcmod.aerocore.api.acoustics.TyreNoise;
import dev.mcmod.hdfv.look.PistonEngineLayout;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;
import dev.mcmod.hdfv.part.engine.EngineBlockDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.wheel.TyreDesign;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sound sources from part designs (0.19): what AeroCore's sound models need, read from what the player designed.
 * Rules (not physics) where the design has nothing to say:
 * <ul>
 * <li>A piston engine's cylinders fire as their throws put them (PistonEngineLayout.throwAngle, each bank turned by its
 *     bank angle); each bank has its own pipe, of 1.2 m plus a metre per square-root litre (the designs have no exhaust
 *     length); the exhaust's back-pressure factor stands for its silencer: a road system (1) is the reference, a
 *     free-flowing one (0.5) is 6 dB louder and brighter, open headers (0.1) 20 dB. The airbox rings at the Helmholtz
 *     frequency of the plenum and one runner. A petrol turbo engine has a blow-off valve; a diesel surges instead.</li>
 * <li>A tyre's tread is its design's; its rated load 24 kN per metre of section width.</li>
 * <li>A body's frontal area is its largest module section (width by height, 0.85 full); its edges a few centimetres.</li>
 * </ul>
 * Results are cached by design (designs are values).
 */
public final class SoundProfiles {
    private SoundProfiles() {}

    private static final Map<Object, PistonEngineNoise.Spec> PISTON = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Object, PistonEngineNoise.Spec> e) { return size() > 64; }
    };

    /** A piston engine's sound (the seed set per vehicle, so two of the same engine are not identical). */
    public static PistonEngineNoise.Spec piston(PistonEngineDesign d, long seed) {
        PistonEngineNoise.Spec base;
        synchronized (PISTON) { base = PISTON.computeIfAbsent(d, k -> pistonSpec(d)); }
        return new PistonEngineNoise.Spec(base.cylinders(), base.openingDeg(), base.bank(), base.banks(), base.displacementL(), base.boreM(),
                base.pipeM(), base.mufflerHz(), base.mufflerDb(), base.intakeHz(), base.diesel(), base.valvesPerCylinder(),
                base.turboBlades(), base.turboTipM(), base.blowOffValve(), seed);
    }

    private static PistonEngineNoise.Spec pistonSpec(PistonEngineDesign d) {
        EngineBlockDesign b = d.block();
        PistonEngineLayout layout = PistonEngineLayout.of(d);
        int n = b.cylinders(), banks = Math.max(1, b.banks()), per = Math.max(1, b.cylindersPerBank());
        double[] tdc = new double[n];
        int[] bank = new int[n];
        for (int c = 0; c < n; c++) {
            int k = Math.min(banks - 1, c / per), i = c - k * per;
            tdc[c] = Math.toDegrees(layout.throwAngle(i)) + k * b.effectiveBankAngle();
            bank[c] = k;
        }
        double litres = d.displacement() * 1000;
        double back = Math.clamp(d.exhaust().backPressureFactor(), 0.05, 2);
        double mufflerDb = Math.clamp(-20 * Math.log10(back), -6, 26);
        double mufflerHz = Math.clamp(400 / Math.pow(back, 0.7), 150, 5000);
        double runnerArea = Math.PI * Math.pow(Math.max(0.01, d.intake().runnerDiameterM()), 2) / 4;
        double helmholtz = 343 / (2 * Math.PI) * Math.sqrt(runnerArea / (Math.max(1e-4, d.intake().plenumVolumeM3()) * Math.max(0.05, d.intake().runnerLengthM())));
        boolean diesel = d.fuelSystem().diesel();
        boolean turbo = d.turbo().fitted();
        return new PistonEngineNoise.Spec(n, PistonEngineNoise.firingAngles(tdc), bank, banks, litres, b.boreM(), 1.2 + Math.sqrt(Math.max(0.05, litres)),
                mufflerHz, mufflerDb, Math.clamp(helmholtz, 30, 600), diesel, d.head().intakeValves() + d.head().exhaustValves(),
                turbo ? Math.max(1, d.turbo().compressorBlades()) : 0, turbo ? d.turbo().compressorTipM() : 0, turbo && !diesel, 0);
    }

    /** A tyre's sound. */
    public static TyreNoise.Spec tyre(TyreDesign t, long seed) {
        TyreNoise.Tread tread;
        try { tread = TyreNoise.Tread.valueOf(t.tread().name()); } catch (IllegalArgumentException e) { tread = TyreNoise.Tread.ROAD; }
        return new TyreNoise.Spec(tread, tyreRadius(t), t.sectionWidthM(), seed);
    }

    /** A tyre's rolling radius, m (rim plus sidewall). */
    public static double tyreRadius(TyreDesign t) { return t.rimDiameterIn() * 0.0254 / 2 + t.sectionWidthM() * t.aspectRatio(); }

    public static double ratedLoadN(TyreDesign t) { return 24_000 * Math.max(0.05, t.sectionWidthM()); }

    /** A body's wind. */
    public static FlowNoise.Spec flow(BodyDesign b, long seed) {
        double area = 0;
        for (BodyModuleDesign m : b.modules()) { area = Math.max(area, 0.85 * m.widthM() * m.heightM()); }
        return new FlowNoise.Spec(Math.max(0.2, area), 0.04, seed);
    }

    /** Whether any module of a body is open to the sky (a convertible, an open cockpit or boat). */
    public static boolean open(BodyDesign b) {
        for (BodyModuleDesign m : b.modules()) { if (m.open()) { return true; } }
        return false;
    }
}
