package dev.mcmod.hdfv.part.engine;


/**
 * Example engine designs, to show the format and exercise the model: starting points a player would change, not
 * engines to sell. Dimensions are of the kind production engines use; they copy no particular engine.
 */
public final class SampleEngines {
    private SampleEngines() {}

    /** A 2.0 l four-valve twin-cam inline four with port injection and intake cam phasing. */
    public static PistonEngineDesign inlineFour() {
        double bore = 0.086, stroke = 0.086, rod = 0.139, compressionHeight = 0.030;
        double vd = Math.PI / 4 * bore * bore * stroke, vc = vd / (11 - 1), gasket = 0.0008;
        double chamber = vc - Math.PI / 4 * bore * bore * gasket;
        return new PistonEngineDesign("inline four 2.0",
                new EngineBlockDesign("block", EngineBlockDesign.Layout.INLINE, 4, 0, bore, 0.096, stroke / 2 + rod + compressionHeight, 5,
                        EngineMaterial.ALUMINIUM_A356, 0.006, 0.8, Double.NaN),
                new CylinderHeadDesign("head", CylinderHeadDesign.Layout.DOHC, 2, 2, CylinderHeadDesign.Chamber.DOME, chamber, 40, 0.87, 0.87, 0.7, 1, 0,
                        gasket, EngineMaterial.ALUMINIUM_A356, Double.NaN),
                new PistonDesign("pistons", bore - 0.00004, compressionHeight, 0, 0.007, 0.035, 0.022, 3, 0.8, EngineMaterial.ALUMINIUM_4032, Double.NaN),
                new ConnectingRodDesign("rods", rod, 0.04803, 0.020, 0.02201, 1.5e-4, EngineMaterial.STEEL_4340, Double.NaN),
                new CrankshaftDesign("crank", stroke, 0.050, 0.020, 0.048, 0.021, 0.7, 0.10, EngineMaterial.STEEL_4340, Double.NaN),
                new ValveDesign("valves", 0.035, 0.0055, 0.030, 0.0055, 0.105, EngineMaterial.VALVE_STEEL, EngineMaterial.VALVE_STEEL, Double.NaN),
                new CamshaftDesign("cams", 290, 0.011, 108, 285, 0.0095, 110, 3, 5, EngineMaterial.DUCTILE_IRON, Double.NaN),
                new SparkPlugDesign("plugs", 0.012, Double.NaN),
                new FuelInjectorDesign("injectors", 310, 3e5, FuelInjectorDesign.Kind.PORT, Double.NaN),
                new ValvetrainDesign("valvetrain", ValvetrainDesign.Mechanism.FINGER_FOLLOWER, true, 250, 45000, 0.04, ValvetrainDesign.Drive.CHAIN, 25, 0, Double.NaN),
                new FuelSystemDesign("fuel", "gasoline", 95, 85, FuelSystemDesign.Delivery.PORT_INJECTION, 1, 3e5, 0.88, 1.0, Double.NaN),
                new IgnitionDesign("ignition", true, 45, 7800, 800, 120, Double.NaN),
                new LubricationDesign("oil", "5W-20", 0.0045, LubricationDesign.Sump.WET, Double.NaN),
                new CoolingDesign("cooling", CoolingDesign.Kind.LIQUID, 0.35, 0.03, 0, 0.6, 0.006, 88, Double.NaN),
                new IntakeDesign("intake", 0.35, 0.040, 0.004, 0.062, 1, 1.0, Double.NaN),
                new ExhaustDesign("exhaust", 0.7, Double.NaN));
    }

    /**
     * A 5.7 l pushrod V8 of the American kind: one camshaft in the block's valley driving two valves per cylinder
     * through roller lifters, pushrods and 1.7 : 1 rocker arms, 90 degree bank angle, aluminium block and heads.
     */
    public static PistonEngineDesign vEightOhv() {
        double bore = 0.099, stroke = 0.092, rod = 0.1549, compressionHeight = 0.0333;
        double vd = Math.PI / 4 * bore * bore * stroke, vc = vd / (10.2 - 1), gasket = 0.0013;
        double chamber = vc - Math.PI / 4 * bore * bore * gasket;
        return new PistonEngineDesign("V8 5.7 OHV",
                new EngineBlockDesign("block", EngineBlockDesign.Layout.VEE, 8, 90, bore, 0.1118, stroke / 2 + rod + compressionHeight, 5,
                        EngineMaterial.ALUMINIUM_A356, 0.007, 1.0, Double.NaN),
                new CylinderHeadDesign("head", CylinderHeadDesign.Layout.OHV, 1, 1, CylinderHeadDesign.Chamber.DOME, chamber, 15, 0.87, 0.87, 0.7, 1, 0.01,
                        gasket, EngineMaterial.ALUMINIUM_A356, Double.NaN),
                new PistonDesign("pistons", bore - 0.00004, compressionHeight, 0, 0.008, 0.032, 0.0234, 3, 0.8, EngineMaterial.ALUMINIUM_4032, Double.NaN),
                new ConnectingRodDesign("rods", rod, 0.05385, 0.0236, 0.02341, 1.9e-4, EngineMaterial.STEEL_4340, Double.NaN),
                new CrankshaftDesign("crank", stroke, 0.0645, 0.024, 0.0538, 0.0476, 0.8, 0.15, EngineMaterial.STEEL_4340, Double.NaN),
                new ValveDesign("valves", 0.0508, 0.008, 0.0394, 0.008, 0.126, EngineMaterial.VALVE_STEEL, EngineMaterial.VALVE_STEEL, Double.NaN),
                new CamshaftDesign("cam", 270, 0.0127, 110, 278, 0.0124, 114, 3, 5, EngineMaterial.STEEL_1045, Double.NaN),
                new SparkPlugDesign("plugs", 0.014, Double.NaN),
                new FuelInjectorDesign("injectors", 330, 4e5, FuelInjectorDesign.Kind.PORT, Double.NaN),
                new ValvetrainDesign("valvetrain", ValvetrainDesign.Mechanism.ROCKER_ARM, true, 445, 60000, 0.12, ValvetrainDesign.Drive.CHAIN, 0, 0, Double.NaN),
                new FuelSystemDesign("fuel", "gasoline", 95, 85, FuelSystemDesign.Delivery.PORT_INJECTION, 1, 4e5, 0.88, 1.0, Double.NaN),
                new IgnitionDesign("ignition", true, 40, 6200, 650, 200, Double.NaN),
                new LubricationDesign("oil", "10W-30", 0.0057, LubricationDesign.Sump.WET, Double.NaN),
                new CoolingDesign("cooling", CoolingDesign.Kind.LIQUID, 0.45, 0.032, 0, 1.0, 0.011, 88, Double.NaN),
                new IntakeDesign("intake", 0.32, 0.05, 0.006, 0.078, 1, 1.0, Double.NaN),
                new ExhaustDesign("exhaust", 0.8, Double.NaN));
    }

    /**
     * A 1.6 l single-cam sixteen-valve inline four: one camshaft driving all four valves of each cylinder through
     * rocker arms on two rocker shafts, cam belt, port injection.
     */
    public static PistonEngineDesign inlineFourSohc() {
        double bore = 0.075, stroke = 0.090, rod = 0.137, compressionHeight = 0.030;
        double vd = Math.PI / 4 * bore * bore * stroke, vc = vd / (9.6 - 1), gasket = 0.0008;
        double chamber = vc - Math.PI / 4 * bore * bore * gasket;
        return new PistonEngineDesign("inline four 1.6 SOHC",
                new EngineBlockDesign("block", EngineBlockDesign.Layout.INLINE, 4, 0, bore, 0.084, stroke / 2 + rod + compressionHeight, 5,
                        EngineMaterial.ALUMINIUM_A356, 0.006, 0.9, Double.NaN),
                new CylinderHeadDesign("head", CylinderHeadDesign.Layout.SOHC, 2, 2, CylinderHeadDesign.Chamber.DOME, chamber, 44, 0.87, 0.87, 0.72, 1, 0,
                        gasket, EngineMaterial.ALUMINIUM_A356, Double.NaN),
                new PistonDesign("pistons", bore - 0.00004, compressionHeight, 0, 0.006, 0.032, 0.019, 3, 0.8, EngineMaterial.ALUMINIUM_4032, Double.NaN),
                new ConnectingRodDesign("rods", rod, 0.04504, 0.020, 0.01901, 1.3e-4, EngineMaterial.STEEL_4340, Double.NaN),
                new CrankshaftDesign("crank", stroke, 0.050, 0.019, 0.045, 0.021, 0.7, 0.09, EngineMaterial.DUCTILE_IRON, Double.NaN),
                new ValveDesign("valves", 0.0295, 0.0055, 0.0255, 0.0055, 0.107, EngineMaterial.VALVE_STEEL, EngineMaterial.VALVE_STEEL, Double.NaN),
                new CamshaftDesign("cam", 256, 0.0085, 108, 252, 0.0080, 112, 3, 5, EngineMaterial.DUCTILE_IRON, Double.NaN),
                new SparkPlugDesign("plugs", 0.012, Double.NaN),
                new FuelInjectorDesign("injectors", 240, 3e5, FuelInjectorDesign.Kind.PORT, Double.NaN),
                new ValvetrainDesign("valvetrain", ValvetrainDesign.Mechanism.ROCKER_ARM, false, 200, 35000, 0.05, ValvetrainDesign.Drive.BELT, 0, 0, Double.NaN),
                new FuelSystemDesign("fuel", "gasoline", 95, 85, FuelSystemDesign.Delivery.PORT_INJECTION, 1, 3e5, 0.88, 1.0, Double.NaN),
                new IgnitionDesign("ignition", true, 40, 7200, 750, 100, Double.NaN),
                new LubricationDesign("oil", "5W-20", 0.0035, LubricationDesign.Sump.WET, Double.NaN),
                new CoolingDesign("cooling", CoolingDesign.Kind.LIQUID, 0.3, 0.03, 0, 0.5, 0.005, 88, Double.NaN),
                new IntakeDesign("intake", 0.32, 0.036, 0.0035, 0.055, 1, 1.0, Double.NaN),
                new ExhaustDesign("exhaust", 0.8, Double.NaN));
    }

    /**
     * A 2.0 l turbocharged common-rail diesel four (0.8): four valves a cylinder parallel to the bore, the combustion
     * bowl in the piston (taken as a flat chamber of its volume), compression 16.5, 180 MPa rail, an intercooled
     * wastegated turbocharger to 2.3 bar absolute, iron block, aluminium head; full load at lambda 1.3.
     */
    public static PistonEngineDesign turboDiesel() {
        double bore = 0.082, stroke = 0.0945, rod = 0.144, compressionHeight = 0.048;
        double vd = Math.PI / 4 * bore * bore * stroke, vc = vd / (16.5 - 1), gasket = 0.0013;
        double bowl = vc - Math.PI / 4 * bore * bore * gasket;
        return new PistonEngineDesign("turbo diesel 2.0",
                new EngineBlockDesign("block", EngineBlockDesign.Layout.INLINE, 4, 0, bore, 0.091, stroke / 2 + rod + compressionHeight, 5,
                        EngineMaterial.GREY_IRON, 0.007, 0.8, Double.NaN),
                new CylinderHeadDesign("head", CylinderHeadDesign.Layout.DOHC, 2, 2, CylinderHeadDesign.Chamber.FLAT, bowl, 0, 0.87, 0.87, 0.65, 0, 0,
                        gasket, EngineMaterial.ALUMINIUM_A356, Double.NaN),
                new PistonDesign("pistons", bore - 0.00006, compressionHeight, 0, 0.019, 0.045, 0.028, 3, 0.9, EngineMaterial.ALUMINIUM_4032, Double.NaN),
                new ConnectingRodDesign("rods", rod, 0.05004, 0.024, 0.02802, 2.4e-4, EngineMaterial.STEEL_4340, Double.NaN),
                new CrankshaftDesign("crank", stroke, 0.058, 0.022, 0.050, 0.025, 0.7, 0.18, EngineMaterial.STEEL_4340, Double.NaN),
                new ValveDesign("valves", 0.027, 0.006, 0.0245, 0.006, 0.10, EngineMaterial.VALVE_STEEL, EngineMaterial.VALVE_STEEL, Double.NaN),
                new CamshaftDesign("cams", 230, 0.0085, 108, 232, 0.0080, 110, 3, 5, EngineMaterial.DUCTILE_IRON, Double.NaN),
                new SparkPlugDesign("glow plugs", 0.010, Double.NaN),
                new FuelInjectorDesign("injectors", 1000, 10e6, FuelInjectorDesign.Kind.DIRECT, Double.NaN),
                new ValvetrainDesign("valvetrain", ValvetrainDesign.Mechanism.FINGER_FOLLOWER, true, 220, 40000, 0.04, ValvetrainDesign.Drive.BELT, 0, 0, Double.NaN),
                new FuelSystemDesign("fuel", "diesel", 95, 85, FuelSystemDesign.Delivery.DIRECT_INJECTION, 1, 180e6, 1.3, 1.0, Double.NaN),
                new IgnitionDesign("injection timing", false, 30, 4600, 800, 160, Double.NaN),
                new LubricationDesign("oil", "5W-40", 0.0052, LubricationDesign.Sump.WET, Double.NaN),
                new CoolingDesign("cooling", CoolingDesign.Kind.LIQUID, 0.40, 0.032, 0, 0.7, 0.008, 88, Double.NaN),
                new IntakeDesign("intake", 0.25, 0.038, 0.003, 0.060, 1, 1.0, Double.NaN),
                new ExhaustDesign("exhaust", 0.9, Double.NaN),
                new TurbochargerDesign("turbo", true, 0.046, 12, 0.76, 3.2e-4, 0.72, 2.3, 0.75, 1.5e-5, Double.NaN));
    }

    /**
     * A large naturally aspirated diesel six for a generator set (0.8): 130 x 150 mm, 11.9 l, compression 16, pushrods
     * driving four valves a cylinder by gears, pump-line-nozzle injection at 120 MPa, iron block and heads, a heavy
     * flywheel; governed at 1500 rpm (50 Hz), full load at lambda 1.5.
     */
    public static PistonEngineDesign generatorDiesel() {
        double bore = 0.130, stroke = 0.150, rod = 0.250, compressionHeight = 0.085;
        double vd = Math.PI / 4 * bore * bore * stroke, vc = vd / (16 - 1), gasket = 0.0015;
        double bowl = vc - Math.PI / 4 * bore * bore * gasket;
        return new PistonEngineDesign("generator diesel 11.9",
                new EngineBlockDesign("block", EngineBlockDesign.Layout.INLINE, 6, 0, bore, 0.152, stroke / 2 + rod + compressionHeight, 7,
                        EngineMaterial.GREY_IRON, 0.010, 0.8, Double.NaN),
                new CylinderHeadDesign("head", CylinderHeadDesign.Layout.OHV, 2, 2, CylinderHeadDesign.Chamber.FLAT, bowl, 0, 0.87, 0.87, 0.65, 0, 0,
                        gasket, EngineMaterial.GREY_IRON, Double.NaN),
                new PistonDesign("pistons", bore - 0.00012, compressionHeight, 0, 0.030, 0.080, 0.050, 3, 0.9, EngineMaterial.ALUMINIUM_4032, Double.NaN),
                new ConnectingRodDesign("rods", rod, 0.09006, 0.040, 0.05003, 6e-4, EngineMaterial.STEEL_4340, Double.NaN),
                new CrankshaftDesign("crank", stroke, 0.105, 0.040, 0.090, 0.042, 0.7, 2.5, EngineMaterial.STEEL_4340, Double.NaN),
                new ValveDesign("valves", 0.044, 0.010, 0.041, 0.010, 0.16, EngineMaterial.VALVE_STEEL, EngineMaterial.VALVE_STEEL, Double.NaN),
                new CamshaftDesign("cam", 240, 0.013, 110, 240, 0.013, 112, 3, 7, EngineMaterial.STEEL_1045, Double.NaN),
                new SparkPlugDesign("glow plugs", 0.012, Double.NaN),
                new FuelInjectorDesign("injectors", 900, 10e6, FuelInjectorDesign.Kind.DIRECT, Double.NaN),
                new ValvetrainDesign("valvetrain", ValvetrainDesign.Mechanism.ROCKER_ARM, true, 500, 80000, 0.15, ValvetrainDesign.Drive.GEAR, 0, 0, Double.NaN),
                new FuelSystemDesign("fuel", "diesel", 95, 85, FuelSystemDesign.Delivery.DIRECT_INJECTION, 1, 120e6, 1.5, 1.0, Double.NaN),
                new IgnitionDesign("injection timing", false, 25, 1800, 700, 900, Double.NaN),
                new LubricationDesign("oil", "15W-40", 0.030, LubricationDesign.Sump.WET, Double.NaN),
                new CoolingDesign("cooling", CoolingDesign.Kind.LIQUID, 1.0, 0.05, 0, 3.0, 0.040, 82, Double.NaN),
                new IntakeDesign("intake", 0.20, 0.060, 0.012, 0.120, 1, 1.0, Double.NaN),
                new ExhaustDesign("exhaust", 0.8, Double.NaN));
    }

    /**
     * A J85-class single-spool turbojet: eight axial stages to pressure ratio 8 at 20 kg/s, turbine inlet 1250 K, two
     * lightly cooled turbine stages, convergent nozzle.
     */
    public static JetEngineDesign turbojet() {
        return new JetEngineDesign("turbojet 20 kg/s",
                new JetInletDesign("inlet", dev.mcmod.aerocore.api.engine.Turbojet.Inlet.PITOT, 0.10, 0.98, 0.1, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                new CompressorDesign("compressor", CompressorDesign.Kind.AXIAL, 8, 8, 20, 0.87, 0.2, 0.42, 0.45, 0.6, 16500, 0, EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CombustorDesign("combustor", CombustorDesign.Flow.STRAIGHT, 0.05, 0.98, 0.45, 0.45, EngineMaterial.INCONEL_718, Double.NaN),
                new TurbineDesign("turbine", 2, 0.88, 0.44, 0.2, 0.03, 0.99, EngineMaterial.INCONEL_713C, Double.NaN),
                new JetNozzleDesign("nozzle", false, 1, 0.98, 0.25, EngineMaterial.STAINLESS_321, Double.NaN),
                new JetControlDesign("control", "kerosene", 1250, 0.55, 1.02, true, 0.3, 135, 0.15, 0.018, Double.NaN));
    }

    /**
     * A CFM56-7B-class airliner turbofan: a 1.55 m fan (pressure ratio 1.62, bypass ratio 5.1) and three booster stages on
     * the fan's spool at 5,175 rpm, nine core compressor stages to 11.5 at 14,460 rpm (overall 32.6), 58 kg/s through the
     * core (355 kg/s in all), T4 1630 K on cooled single-crystal blades, two core turbine stages (the real engine has one;
     * this keeps its disc within the rule of two thirds of yield), four fan turbine stages, separate jets.
     */
    public static TurbofanDesign turbofan() {
        return new TurbofanDesign("turbofan CFM56 class",
                new JetInletDesign("inlet", dev.mcmod.aerocore.api.engine.Turbojet.Inlet.PITOT, 1.8, 0.995, 1.0, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                new FanDesign("fan", 5.1, 1.62, 0.90, 1.55, 0.3, 5175, 24, 3, 1.75, 0.025, EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CompressorDesign("compressor", CompressorDesign.Kind.AXIAL, 9, 11.5, 355 / 6.1, 0.905, 0.2, 0.5, 0.55, 0.6, 14460, 0,
                        EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CombustorDesign("combustor", CombustorDesign.Flow.STRAIGHT, 0.05, 0.995, 0.5, 0.85, EngineMaterial.INCONEL_718, Double.NaN),
                new TurbineDesign("hpTurbine", 2, 0.89, 0.53, 0.45, 0.10, 0.99, EngineMaterial.CMSX_4, Double.NaN),
                new TurbineDesign("lpTurbine", 4, 0.91, 0.9, 0, 0, 0.99, EngineMaterial.INCONEL_713C, Double.NaN),
                new MixerDesign("mixer", MixerDesign.Kind.NONE, 0, 12, 0, EngineMaterial.INCONEL_718, Double.NaN),
                new JetNozzleDesign("nozzle", false, 1, 0.99, 0.5, EngineMaterial.STAINLESS_321, Double.NaN),
                new JetControlDesign("control", "kerosene", 1630, 0.22, 1.02, true, 0.3, 360, 0.2, 0.009, Double.NaN));
    }

    /**
     * A small business-jet turbofan (FJ44-1A class, 8.5 kN): a 0.53 m fan (1.55, bypass ratio 3.3) and one booster stage at
     * 15,500 rpm, two axial stages and an impeller to 6.0 at 36,000 rpm, an uncooled core turbine at 1320 K, two fan turbine
     * stages, a reverse-flow combustor and a lobed mixer before one nozzle.
     */
    public static TurbofanDesign businessTurbofan() {
        return new TurbofanDesign("turbofan FJ44 class",
                new JetInletDesign("inlet", dev.mcmod.aerocore.api.engine.Turbojet.Inlet.PITOT, 0.15, 0.99, 0.3, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                new FanDesign("fan", 3.3, 1.55, 0.87, 0.53, 0.35, 15500, 22, 1, 1.28, 0.03, EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CompressorDesign("compressor", CompressorDesign.Kind.AXIAL_CENTRIFUGAL, 2, 6.0, 28 / 4.3, 0.86, 0.2, 0.30, 0.5, 0.6, 36000, 24,
                        EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CombustorDesign("combustor", CombustorDesign.Flow.REVERSE, 0.05, 0.99, 0.25, 0.38, EngineMaterial.INCONEL_718, Double.NaN),
                new TurbineDesign("hpTurbine", 1, 0.85, 0.21, 0, 0, 0.99, EngineMaterial.CMSX_4, Double.NaN),
                new TurbineDesign("lpTurbine", 2, 0.86, 0.36, 0, 0, 0.99, EngineMaterial.INCONEL_713C, Double.NaN),
                new MixerDesign("mixer", MixerDesign.Kind.LOBED, 0.02, 12, 0.3, EngineMaterial.INCONEL_718, Double.NaN),
                new JetNozzleDesign("nozzle", false, 1, 0.98, 0.15, EngineMaterial.STAINLESS_321, Double.NaN),
                new JetControlDesign("control", "kerosene", 1320, 0.25, 1.02, true, 0.3, 23, 0.15, 0.013, Double.NaN));
    }

    /**
     * A PT6A-27-class turboprop: three axial stages and an impeller to 6.7 at 38,100 rpm, 2.95 kg/s, a reverse-flow
     * combustor, an uncooled turbine at 1200 K, a two-stage free power turbine at 33,000 rpm and a two-stage planetary
     * gearbox of 15 to a 2,200 rpm propeller shaft.
     */
    public static ShaftTurbineDesign turboprop() {
        return new ShaftTurbineDesign("turboprop PT6A class", ShaftTurbineDesign.Output.PROPELLER,
                new JetInletDesign("inlet", dev.mcmod.aerocore.api.engine.Turbojet.Inlet.PITOT, 0.022, 0.97, 0.2, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                new CompressorDesign("compressor", CompressorDesign.Kind.AXIAL_CENTRIFUGAL, 3, 6.7, 2.95, 0.84, 0.2, 0.25, 0.4, 0.6, 38100, 26,
                        EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CombustorDesign("combustor", CombustorDesign.Flow.REVERSE, 0.05, 0.98, 0.3, 0.42, EngineMaterial.INCONEL_718, Double.NaN),
                new TurbineDesign("turbine", 1, 0.85, 0.19, 0, 0, 0.99, EngineMaterial.INCONEL_713C, Double.NaN),
                new PowerTurbineDesign("powerTurbine", 2, 0.86, 0.22, 33000, 1.05, EngineMaterial.INCONEL_713C, Double.NaN),
                new ReductionGearboxDesign("gearbox", ReductionGearboxDesign.Kind.PLANETARY, 15, 2, 2400, EngineMaterial.STEEL_4340, Double.NaN),
                new JetNozzleDesign("exhaust", false, 1, 0.97, 0.15, EngineMaterial.STAINLESS_321, Double.NaN),
                new JetControlDesign("control", "kerosene", 1200, 0.52, 1.02, true, 0.3, 14, 0.12, 0.014, Double.NaN));
    }

    /**
     * A T700-GE-701C-class turboshaft: five axial stages and an impeller to 17 at 44,700 rpm, 4.9 kg/s, T4 1530 K on cooled
     * single-crystal blades in two stages, a two-stage power turbine driving the output directly at 20,900 rpm.
     */
    public static ShaftTurbineDesign turboshaft() {
        return new ShaftTurbineDesign("turboshaft T700 class", ShaftTurbineDesign.Output.SHAFT,
                new JetInletDesign("inlet", dev.mcmod.aerocore.api.engine.Turbojet.Inlet.PITOT, 0.03, 0.98, 0.2, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                new CompressorDesign("compressor", CompressorDesign.Kind.AXIAL_CENTRIFUGAL, 5, 17, 4.9, 0.85, 0.2, 0.24, 0.33, 0.75, 44700, 30,
                        EngineMaterial.TITANIUM_6AL4V, Double.NaN),
                new CombustorDesign("combustor", CombustorDesign.Flow.STRAIGHT, 0.05, 0.995, 0.2, 0.40, EngineMaterial.INCONEL_718, Double.NaN),
                new TurbineDesign("turbine", 2, 0.85, 0.17, 0.35, 0.08, 0.99, EngineMaterial.CMSX_4, Double.NaN),
                new PowerTurbineDesign("powerTurbine", 2, 0.87, 0.30, 20900, 1.04, EngineMaterial.INCONEL_713C, Double.NaN),
                new ReductionGearboxDesign("gearbox", ReductionGearboxDesign.Kind.PLANETARY, 1, 0, 700, EngineMaterial.STEEL_4340, Double.NaN),
                new JetNozzleDesign("exhaust", false, 1, 0.98, 0.2, EngineMaterial.STAINLESS_321, Double.NaN),
                new JetControlDesign("control", "kerosene", 1530, 0.62, 1.02, true, 0.3, 19, 0.12, 0.013, Double.NaN));
    }

    /**
     * A model turbojet of the kind in radio-controlled jets: one centrifugal stage to pressure ratio 2.2 at 0.23 kg/s
     * and 112000 rpm, an uncooled single-stage turbine at 950 K, a short convergent tailpipe.
     */
    public static JetEngineDesign modelTurbojet() {
        return new JetEngineDesign("model turbojet",
                new JetInletDesign("inlet", dev.mcmod.aerocore.api.engine.Turbojet.Inlet.PITOT, 0.0025, 0.97, 0.05, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                new CompressorDesign("impeller", CompressorDesign.Kind.CENTRIFUGAL, 1, 2.2, 0.23, 0.75, 0.15, 0.066, 0.3, 0.6, 112000, 12, EngineMaterial.ALUMINIUM_2618, Double.NaN),
                new CombustorDesign("combustor", CombustorDesign.Flow.STRAIGHT, 0.06, 0.95, 0.12, 0.11, EngineMaterial.STAINLESS_321, Double.NaN),
                new TurbineDesign("turbine", 1, 0.80, 0.062, 0, 0, 0.98, EngineMaterial.INCONEL_713C, Double.NaN),
                new JetNozzleDesign("nozzle", false, 1, 0.98, 0.1, EngineMaterial.STAINLESS_321, Double.NaN),
                new JetControlDesign("control", "kerosene", 950, 0.35, 1.0, true, 0.3, 0.075, 0.12, 0.008, Double.NaN));
    }
}
