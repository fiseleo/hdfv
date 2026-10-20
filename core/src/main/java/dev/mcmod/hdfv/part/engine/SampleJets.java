package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.SupersonicInlet;
import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.api.propellant.SolidRocket;

/**
 * Sample designs of the ramjet family, pulsejets, boosters and afterburning engines (0.10): formats and physics, not
 * balanced game content. Each names the engine class it follows; its checks against published numbers are in the tests.
 */
public final class SampleJets {
    private SampleJets() {}

    /**
     * A BOMARC-class ramjet (Marquardt RJ43 style): a 20-degree spike inlet for Mach 2.8, 0.25 m^2 of capture (a 0.71 m
     * combustor), V-gutter flameholders, kerosene, sized at Mach 2.8 and 18 km; boosted to Mach 1.5 before it lights.
     */
    public static RamjetDesign bomarcRamjet() {
        return new RamjetDesign("ramjet BOMARC class",
                new SupersonicInletDesign("inlet", SupersonicInlet.Kind.CONE, 2.8, 2, 20, 0.95, 0.25, 1.5, EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new RamCombustorDesign("combustor", 1.6, 1.5, 0.92, 2100, 2.0, EngineMaterial.HASTELLOY_X, Double.NaN),
                new JetNozzleDesign("nozzle", true, 1.6, 0.97, 0.6, EngineMaterial.HASTELLOY_X, Double.NaN),
                new RamControlDesign("control", "kerosene", 2.8, 18000, 2000, 1.05, 0.35, 1.5, Double.NaN));
    }

    /**
     * An X-51A-class scramjet: two ramps for Mach 6, 0.06 m^2 of capture, contraction 8 (1.2 of it internal: it starts by
     * itself from about Mach 4.5, within Kantrowitz's limit), a fuel-cooled diverging
     * combustor, JP-7-like kerosene with a pilot (the X-51A's ethylene start), its table from Mach 4 to 6 (its fuel cools the walls to about Mach 6).
     */
    public static ScramjetDesign x51Scramjet() {
        return new ScramjetDesign("scramjet X-51 class",
                new ScramInletDesign("inlet", 6, 2, 0.06, 8, 1.2, 0.97, 10, 1.6, EngineMaterial.INCONEL_718, Double.NaN),
                new ScramCombustorDesign("combustor", 1.0, 2.0, 0.0015, 0.85, 0.3, 2.5, 0.98, true, EngineMaterial.INCONEL_718, Double.NaN),
                new ScramControlDesign("control", "kerosene", 1, true, 4, 6, Double.NaN));
    }

    /** An X-43A-class hydrogen scramjet: ramps for Mach 7, a hydrogen-cooled combustor, a pilot, its table from Mach 5 to 10. */
    public static ScramjetDesign hydrogenScramjet() {
        return new ScramjetDesign("scramjet X-43 class",
                new ScramInletDesign("inlet", 7, 2, 0.05, 10, 1.3, 0.97, 10, 1.2, EngineMaterial.INCONEL_718, Double.NaN),
                new ScramCombustorDesign("combustor", 1.0, 2.5, 0.0015, 0.9, 0.5, 3, 0.98, true, EngineMaterial.INCONEL_718, Double.NaN),
                new ScramControlDesign("control", "hydrogen", 1, true, 5, 10, Double.NaN));
    }

    /**
     * The Argus As 014 (V-1): a 0.58 x 0.9 m chamber, a 1 m taper to a 0.38 x 1.75 m tailpipe of 2.5 mm mild steel; a
     * grid of spring-steel reeds (0.04 m^2 open, 36 mm long, 0.45 mm thick, 4 mm lift: about an hour's life); low-grade
     * gasoline up to 0.35 kg/s.
     */
    public static PulsejetDesign argus() {
        return new PulsejetDesign("pulsejet Argus As 014",
                new PulseTubeDesign("tube", 0.58, 0.9, 1.0, 0.38, 1.75, 0.0025, EngineMaterial.STEEL_1045, Double.NaN),
                new PulseValveDesign("valve", false, 0.04, 5000, 200, 0.036, 0.004, 0.00045, EngineMaterial.SPRING_STEEL, 0.08, 0.15, true, Double.NaN),
                new PulseControlDesign("control", "gasoline", 0.35, 0.003, 0.9, Double.NaN));
    }

    /** A hobby-size Lockwood-Hiller valveless pulsejet: 1.2 m, a 0.16 m chamber, its intake bent rearward, on propane. */
    public static PulsejetDesign lockwood() {
        return new PulsejetDesign("pulsejet Lockwood-Hiller",
                new PulseTubeDesign("tube", 0.16, 0.30, 0.15, 0.08, 0.60, 0.001, EngineMaterial.STAINLESS_321, Double.NaN),
                new PulseValveDesign("valve", true, 0.04, 5000, 200, 0.036, 0.004, 0.00045, EngineMaterial.SPRING_STEEL, 0.08, 0.15, true, Double.NaN),
                new PulseControlDesign("control", "propane", 0.012, 0.003, 0.9, Double.NaN));
    }

    /**
     * An amateur KNSB motor: three BATES segments (100 mm grain, 40 mm cores, 170 mm long), a 22 mm throat opening 8
     * times, an aluminium casing, a steel nozzle (it erodes: fine for its two seconds), reloaded after each burn.
     */
    public static SolidRocketDesign knsbMotor() {
        return new SolidRocketDesign("booster KNSB",
                new GrainDesign("grain", "knsb", SolidRocket.GrainKind.BATES, 0.1, 0.04, 0.17, 3),
                new RocketCasingDesign("casing", EngineMaterial.ALUMINIUM_2024, 0.003, 1.5, Double.NaN),
                new RocketNozzleDesign("nozzle", 0.022, 8, 12, 0.95, EngineMaterial.STEEL_1045, Double.NaN),
                new BoosterDesign("booster", false, Double.NaN));
    }

    /**
     * An APCP strap-on booster: four BATES segments (150 mm grain, 50 mm cores, 240 mm long), a graphite throat of 34
     * mm opening 8 times, a steel casing, cast off when burnt out.
     */
    public static SolidRocketDesign apcpBooster() {
        return new SolidRocketDesign("booster APCP",
                new GrainDesign("grain", "apcp", SolidRocket.GrainKind.BATES, 0.15, 0.05, 0.24, 4),
                new RocketCasingDesign("casing", EngineMaterial.STEEL_4340, 0.003, 1.5, Double.NaN),
                new RocketNozzleDesign("nozzle", 0.034, 8, 12, 0.95, EngineMaterial.GRAPHITE, Double.NaN),
                new BoosterDesign("booster", true, Double.NaN));
    }

    /**
     * A J79-GE-17-class afterburning turbojet: 17 stages to 13.5 at 77 kg/s and 7,685 rpm, an uncooled three-stage turbine
     * at 1210 K, an afterburner to 1950 K with a variable nozzle opening to 1.8 times.
     */
    public static JetEngineDesign j79() {
        return new JetEngineDesign("turbojet J79 class",
                new JetInletDesign("inlet", Turbojet.Inlet.PITOT, 0.5, 0.99, 0.3, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                new CompressorDesign("compressor", CompressorDesign.Kind.AXIAL, 17, 13.5, 77, 0.87, 0.2, 0.76, 0.4, 0.6, 7685, 0, EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CombustorDesign("combustor", CombustorDesign.Flow.STRAIGHT, 0.05, 0.98, 0.8, 0.95, EngineMaterial.INCONEL_718, Double.NaN),
                new TurbineDesign("turbine", 3, 0.88, 0.84, 0, 0, 0.99, EngineMaterial.INCONEL_713C, Double.NaN),
                new JetNozzleDesign("nozzle", false, 1, 0.98, 0.5, EngineMaterial.HASTELLOY_X, Double.NaN),
                new JetControlDesign("control", "kerosene", 1210, 0.6, 1.0, true, 0.3, 900, 0.12, 0.015, Double.NaN),
                new AfterburnerDesign("afterburner", true, 1950, 0.9, 0.25, 1.8, 2.0, EngineMaterial.HASTELLOY_X, Double.NaN));
    }

    /**
     * A J58-class turbo-ramjet: a 26-degree spike inlet for Mach 3.2 (1 m^2 capture), 9 stages to 8.5 at 148 kg/s, a
     * turbine at 1366 K, an afterburner to 2144 K, bleed bypass opening from Mach 2 to 3 to 35 % of the core's air.
     */
    public static TurboRamjetDesign j58() {
        return new TurboRamjetDesign("turbo-ramjet J58 class",
                new SupersonicInletDesign("inlet", SupersonicInlet.Kind.CONE, 3.2, 2, 26, 0.95, 1.0, 3.0, EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CompressorDesign("compressor", CompressorDesign.Kind.AXIAL, 9, 8.5, 148, 0.87, 0.2, 1.02, 0.4, 0.6, 7400, 0, EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CombustorDesign("combustor", CombustorDesign.Flow.STRAIGHT, 0.05, 0.98, 0.9, 1.2, EngineMaterial.INCONEL_718, Double.NaN),
                new TurbineDesign("turbine", 2, 0.88, 0.95, 0, 0, 0.99, EngineMaterial.CMSX_4, Double.NaN),
                new JetNozzleDesign("nozzle", true, 1.6, 0.98, 0.8, EngineMaterial.HASTELLOY_X, Double.NaN),
                new JetControlDesign("control", "kerosene", 1366, 0.6, 1.0, true, 0.3, 1500, 0.12, 0.015, Double.NaN),
                new AfterburnerDesign("afterburner", true, 2144, 0.9, 0.25, 2.2, 2.5, EngineMaterial.HASTELLOY_X, Double.NaN),
                new BypassDesign("bypass", 2.0, 3.0, 0.35, 0.4, 0.95, EngineMaterial.TITANIUM_6AL4V, Double.NaN));
    }
}
