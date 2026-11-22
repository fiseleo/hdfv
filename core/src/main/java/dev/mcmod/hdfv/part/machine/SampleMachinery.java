package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.hydraulic.Hydraulics;
import dev.mcmod.aerocore.api.hydraulic.Pump;
import dev.mcmod.aerocore.api.vehicle.part.DrumPart;
import dev.mcmod.aerocore.api.vehicle.part.TrackPart;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.part.machine.LinkDesign.Actuation;
import dev.mcmod.hdfv.part.machine.LinkDesign.JointAxis;
import dev.mcmod.hdfv.part.machine.LinkDesign.JointType;
import dev.mcmod.hdfv.part.machine.LinkDesign.LinkKind;

/**
 * Sample heavy-machinery parts (0.26): undercarriages, drums, hydraulics, work equipment, hooks and outriggers sized
 * after real machines of their class (each method says which). They show the formats and the physics, not balanced
 * game content (as SampleEngines and the other samples).
 */
public final class SampleMachinery {
    private SampleMachinery() {}

    private static final double BAR = Hydraulics.BAR_PA, LPM = Hydraulics.LITRE_PER_MIN_M3_PER_S, CC = Hydraulics.CM3_M3;

    // ---------------------------------------------------------------- undercarriages

    /**
     * A 20 t excavator's tracks (CAT 320 / Komatsu PC200 class: 2.38 m gauge, 3.65 m between the end rollers, 600 mm
     * triple-grouser shoes on a 190 mm pitch chain, seven road wheels a side; about 50 kPa at 22 t). Braked by its travel
     * motors; a spring-applied parking brake at each sprocket.
     */
    public static TrackDesign excavatorTracks20t() {
        return new TrackDesign("excavator tracks 20 t", 22000, 2.38, 3.65, 0.6, 7, 0.1, TrackPart.Shoe.STEEL, 0.026, 0.19, 0.3, 0.34, 0.04, 0, 0, 0.6, 0.05, 0, 35000);
    }

    /**
     * A D6-class dozer's tracks (Caterpillar D6: 1.88 m gauge, 2.61 m on the ground, 560 mm single-grouser shoes on a
     * 203 mm pitch, seven road wheels; about 70 kPa at 20.5 t), with steering brakes at its sprockets.
     */
    public static TrackDesign dozerTracksD6() {
        return new TrackDesign("dozer tracks D6", 20500, 1.88, 2.61, 0.56, 7, 0.12, TrackPart.Shoe.STEEL, 0.065, 0.203, 0.36, 0.4, 0.05, 0, 0, 0.6, 0.05, 60000, 60000);
    }

    /** A 1.7 t mini excavator's rubber tracks (230 mm belts on a 72 mm core pitch, 0.76 m gauge, 1.25 m on the ground: about 29 kPa). */
    public static TrackDesign miniExcavatorTracks() {
        return new TrackDesign("mini excavator tracks", 1700, 0.76, 1.25, 0.23, 4, 0.06, TrackPart.Shoe.RUBBER, 0.025, 0.072, 0.17, 0.17, 0.02, 0, 0, 0.6, 0.06, 0, 3000);
    }

    // ---------------------------------------------------------------- drums

    /**
     * An 11 t single-drum soil compactor's drum (Caterpillar CS56B: 2.13 m wide, 1.53 m across, 1.9 mm at 31 Hz on its
     * high amplitude; about 5.5 t on the drum).
     */
    public static DrumDesign rollerDrum11t() {
        return new DrumDesign("roller drum 11 t", 2.13, 1.53, 0.028, DrumPart.Shell.SMOOTH, 0.0019, 31, 5500, 0, 0, 0, 0, 15000, EngineMaterial.STEEL_1045);
    }

    /** The same drum with a padfoot shell for cohesive soils (CAT CP56B: 1.7 mm). */
    public static DrumDesign padfootDrum11t() {
        return new DrumDesign("padfoot drum 11 t", 2.13, 1.53, 0.028, DrumPart.Shell.PADFOOT, 0.0017, 31, 5800, 0, 0, 0, 0, 15000, EngineMaterial.STEEL_1045);
    }

    /** One drum of a 10 t tandem asphalt roller (Bomag BW 161 AD / CAT CB10 class: 1.68 m wide, 1.22 m across, 0.7 mm at 45 Hz, about 4.9 t a drum). */
    public static DrumDesign tandemDrum() {
        return new DrumDesign("tandem drum", 1.68, 1.22, 0.02, DrumPart.Shell.SMOOTH, 0.0007, 45, 4900, 0, 0, 0, 0, 10000, EngineMaterial.STEEL_1045);
    }

    // ---------------------------------------------------------------- hydraulics

    /**
     * One of a 20 t excavator's main pumps: 112 cm^3 swash-plate (Kawasaki K3V112DT, a tandem of two on one shaft), 343
     * bar, power limited to half the engine's 110 kW, rated at the engine's 1800 rpm.
     */
    public static HydraulicPumpDesign excavatorPump() {
        return new HydraulicPumpDesign("excavator main pump", "main", HydraulicUnit.AXIAL_PISTON, Pump.Control.POWER_LIMITED, 112 * CC, 343 * BAR, 55000, 1800);
    }

    /** A pilot pump: a 10 cm^3 gear pump at 40 bar for the joysticks' pilot circuit. */
    public static HydraulicPumpDesign pilotPump() {
        return new HydraulicPumpDesign("pilot pump", "pilot", HydraulicUnit.GEAR, Pump.Control.FIXED, 10 * CC, 40 * BAR, 0, 1800);
    }

    /**
     * A 20 t excavator's travel drive: a 110 cm^3 axial piston motor through a 55:1 planetary final drive (about 29 kN m
     * at the sprocket at 350 bar, 5.8 km/h on a 0.34 m sprocket at 2500 rpm), its parking brake 33 kN m at the output.
     * Its winch values are the hoist winch's (unused).
     */
    public static HydraulicMotorDesign travelMotor() {
        return new HydraulicMotorDesign("travel drive", "travel", HydraulicUnit.AXIAL_PISTON, 110 * CC, 350 * BAR, 2500, 55, 33000, false,
                0.45, 0.6, 0.022, 6, 1, 30, 5);
    }

    /**
     * A 40 t crane's hoist winch: a 107 cm^3 motor through 40:1 on a 0.45 m drum 0.6 m wide, 183 m of 22 mm rope reeved
     * over six falls of up to 30 m (about 70 kN line pull and 40 t at the hook on its middle layer, the hook at 0.33 m/s).
     */
    public static HydraulicMotorDesign hoistWinch() {
        return new HydraulicMotorDesign("hoist winch", "main", HydraulicUnit.AXIAL_PISTON, 107 * CC, 320 * BAR, 2800, 40, 24000, true,
                0.45, 0.6, 0.022, 6, 1, 30, 5);
    }

    // ---------------------------------------------------------------- work equipment

    /** A ram-driven link with the kind's joint. */
    static LinkDesign ram(String name, LinkKind kind, double length, double height, double width, double plate, EngineMaterial material,
                          double lowerDeg, double upperDeg, double startDeg, double lowerM, double upperM, double startM,
                          double bore, double rod, double stroke, int count, Vec3d base, Vec3d pin, double reliefBar, double holdBar, double litresPerMin, String circuit) {
        return new LinkDesign(name, kind, length, height, width, plate, material, JointType.AUTO, JointAxis.AUTO, false, lowerDeg, upperDeg, startDeg, lowerM, upperM, startM,
                Actuation.RAM, bore, rod, stroke, count, base.x(), base.y(), base.z(), pin.x(), pin.y(), pin.z(), reliefBar * BAR, holdBar * BAR, litresPerMin * LPM, circuit,
                0, 0, 0, 0);
    }

    /** A sliding link on a ram along its slide: the rod pin the ram's closed length from the base pin. */
    static LinkDesign slide(String name, LinkKind kind, double length, double height, double width, double plate, EngineMaterial material, double upperM,
                            double bore, double rod, double stroke, int count, Vec3d base, double reliefBar, double holdBar, double litresPerMin) {
        LinkDesign probe = ram(name, kind, length, height, width, plate, material, 0, 0, 0, 0, upperM, 0, bore, rod, stroke, count, base, base, reliefBar, holdBar, litresPerMin, "main");
        Vec3d pin = base.add(probe.axis().scale(MachineRules.closedLengthM(bore, stroke)));
        return ram(name, kind, length, height, width, plate, material, 0, 0, 0, 0, upperM, 0, bore, rod, stroke, count, base, pin, reliefBar, holdBar, litresPerMin, "main");
    }

    /** A link turned by a geared motor. */
    static LinkDesign slew(String name, LinkKind kind, double length, double height, double width, double plate, EngineMaterial material, boolean endless,
                           double lowerDeg, double upperDeg, double startDeg, double displacementCm3, double ratio, double brakeNm, double reliefBar, double litresPerMin,
                           String circuit) {
        return new LinkDesign(name, kind, length, height, width, plate, material, JointType.AUTO, JointAxis.AUTO, endless, lowerDeg, upperDeg, startDeg, 0, 0, 0,
                Actuation.SLEW, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, reliefBar * BAR, reliefBar * BAR, litresPerMin * LPM, circuit, displacementCm3 * CC, ratio, brakeNm, 0);
    }

    /**
     * A 20 t excavator's mono boom (CAT 320 / Komatsu PC200: 5.7 m pin to pin) on two 120/85 mm cylinders of 1.335 m
     * stroke at 350 bar, from -40 degrees (digging deep) to +55; 400 L/min of the two pumps.
     */
    public static LinkDesign excavatorBoom() {
        return ram("excavator boom", LinkKind.BOOM, 5.7, 0.75, 0.48, 0.012, EngineMaterial.STEEL_1045, -40, 55, 10, 0, 0, 0,
                0.12, 0.085, 1.335, 2, new Vec3d(0.2, -0.8, 0), new Vec3d(2.6, -0.3, 0), 350, 380, 400, "main");
    }

    /**
     * Its 2.9 m stick on a 135/95 mm cylinder of 1.49 m from the boom's top to the stick's 0.8 m tail, from 150 degrees
     * folded under the boom to 35 (q against the boom's line, mounted on the boom's "tip").
     */
    public static LinkDesign excavatorStick() {
        return ram("excavator stick", LinkKind.STICK, 2.9, 0.6, 0.4, 0.012, EngineMaterial.STEEL_1045, -150, -35, -100, 0, 0, 0,
                0.135, 0.095, 1.49, 1, new Vec3d(-2.7, 0.9, 0), new Vec3d(-0.8, 0.1, 0), 350, 380, 400, "main");
    }

    /** An 18 t wheel loader's lift arms (CAT 950 class: 3 m, two 130 mm cylinders of 0.8 m at 280 bar), -45 to +45 degrees. */
    public static LinkDesign loaderArm() {
        return ram("loader lift arm", LinkKind.LOADER_ARM, 3.0, 0.6, 0.5, 0.02, EngineMaterial.STEEL_1045, -45, 45, -35, 0, 0, 0,
                0.13, 0.09, 0.8, 2, new Vec3d(0.1, -0.5, 0), new Vec3d(1.8, -0.1, 0), 280, 310, 300, "main");
    }

    /** A 40 t all-terrain crane's telescopic section: 9 m of 6 mm high-strength plate, 7.5 m out on a 140 mm telescoping cylinder. */
    public static LinkDesign craneBoomSection() {
        return slide("crane boom section", LinkKind.TELESCOPIC, 9.0, 0.7, 0.6, 0.006, EngineMaterial.STEEL_4340, 7.5, 0.14, 0.1, 7.5, 1, new Vec3d(0.3, 0, 0), 300, 330, 150);
    }

    /**
     * A 100 t crawler crane's 30 m lattice boom (2 m square, chords of 140 mm x 12 mm), luffed 0 to 82 degrees by its
     * boom hoist: a 250 cm^3 motor through 4500:1 (reducer and luffing tackle), about 5 MN m.
     */
    public static LinkDesign latticeBoom() {
        return slew("lattice boom 30 m", LinkKind.LATTICE, 30, 2.0, 2.0, 0.012, EngineMaterial.STEEL_4340, false, 0, 82, 30, 250, 4500, 6e6, 350, 250, "main");
    }

    /** A 2.5 t forklift's inner mast stage: 3 m, lifted 1.5 m by two 50 mm cylinders at 200 bar. */
    public static LinkDesign mastStage() {
        return slide("mast stage", LinkKind.MAST, 3.0, 0.1, 0.8, 0.008, EngineMaterial.STEEL_1045, 1.5, 0.05, 0.035, 1.5, 2, new Vec3d(0, 0.1, 0), 200, 220, 60);
    }

    /**
     * A 20 t excavator's swing: a 1.2 m slewing ring, a 130 cm^3 swing motor through 130:1 (reducer and ring) at 280 bar,
     * about 62 kN m and 13 rpm on 220 L/min, endless; its swing brake 60 kN m.
     */
    public static LinkDesign swingRing() {
        return slew("swing ring", LinkKind.SLEW_RING, 1.2, 0.11, 0.14, 0.02, EngineMaterial.STEEL_4340, true, -180, 180, 0, 130, 130, 60000, 280, 220, "swing");
    }

    /** An 18 t wheel loader's centre hinge: +-40 degrees on two 90 mm steering cylinders of 0.45 m at 240 bar. */
    public static LinkDesign articulationHinge() {
        return ram("articulation hinge", LinkKind.ARTICULATION, 0.4, 0.9, 0.5, 0.04, EngineMaterial.STEEL_1045, -40, 40, 0, 0, 0, 0,
                0.09, 0.05, 0.45, 2, new Vec3d(-1.1, 0, 0.2), new Vec3d(0, 0, 0.3), 240, 260, 120, "steering");
    }

    /** An articulating work platform's 5 m knuckle section, unfolding 0 to 120 degrees on a 100 mm cylinder at 210 bar. */
    public static LinkDesign platformBoom() {
        return ram("platform boom section", LinkKind.PLATFORM_BOOM, 5.0, 0.35, 0.25, 0.006, EngineMaterial.STEEL_4340, 0, 120, 0, 0, 0, 0,
                0.1, 0.07, 1.0, 1, new Vec3d(-1.6, -1.0, 0), new Vec3d(0.2, 0.4, 0), 210, 230, 40, "main");
    }

    /** A truck-mounted concrete pump's 8 m placing-boom section, unfolding 0 to 120 degrees on a 160 mm cylinder at 350 bar. */
    public static LinkDesign placingBoom() {
        return ram("placing boom section", LinkKind.PLACING_BOOM, 8.0, 0.55, 0.4, 0.008, EngineMaterial.STEEL_4340, 0, 120, 0, 0, 0, 0,
                0.16, 0.11, 1.6, 1, new Vec3d(-2.6, -1.6, 0), new Vec3d(-0.4, -0.6, 0), 350, 380, 120, "main");
    }

    /** A 40 t all-terrain crane's outrigger beam: 2.5 m, slid out 2 m by an 80 mm cylinder inside it. */
    public static LinkDesign outriggerBeam() {
        return slide("outrigger beam", LinkKind.OUTRIGGER_BEAM, 2.5, 0.3, 0.3, 0.012, EngineMaterial.STEEL_4340, 2.0, 0.08, 0.05, 2.0, 1, new Vec3d(0, 0, -0.2), 250, 280, 40);
    }

    // ---------------------------------------------------------------- hooks and outriggers

    /** A 40 t hook block: six falls of 22 mm rope over three sheaves, a single hook. */
    public static HookDesign craneHook40t() { return new HookDesign("crane hook 40 t", 40000, 6, 0.022, HookDesign.HookKind.SINGLE, 0.05); }

    /** An all-terrain crane's outrigger jack: 1 m let down, 0.6 m of stroke in 8 s, a 0.5 m pad, 40 t. */
    public static OutriggerDesign outrigger() { return new OutriggerDesign("outrigger jack", 1.0, 0.6, 8, 0.5, 40000); }

    // ---------------------------------------------------------------- operator cabs

    /** A 20 t excavator's cab (CAT 320 / Komatsu PC200 class): 1.5 x 1.0 m, 1.65 m tall, its front leaning back 8 degrees, ROPS (ISO 12117-2), the door on the left. */
    public static OperatorCabDesign excavatorCab() {
        return new OperatorCabDesign("excavator cab", 1.5, 1.0, 1.65, 8, OperatorCabDesign.Protection.ROPS, OperatorCabDesign.DoorSide.LEFT);
    }

    /** A 1.7 t mini excavator's cab: 1.15 x 0.85 m, 1.45 m tall, not rated, the door on the left. */
    public static OperatorCabDesign miniExcavatorCab() {
        return new OperatorCabDesign("mini excavator cab", 1.15, 0.85, 1.45, 5, OperatorCabDesign.Protection.CAB, OperatorCabDesign.DoorSide.LEFT);
    }

    /** An 18 t wheel loader's cab (CAT 950 class): 1.7 x 1.4 m, 1.9 m tall, upright, ROPS and FOPS, the door on the left. */
    public static OperatorCabDesign loaderCab() {
        return new OperatorCabDesign("loader cab", 1.7, 1.4, 1.9, 0, OperatorCabDesign.Protection.ROPS_FOPS, OperatorCabDesign.DoorSide.LEFT);
    }

    // ---------------------------------------------------------------- engine hoods (2026-11-22)

    /**
     * A 20 t excavator's hood (CAT 320 / Komatsu PC200 class): over the engine, pumps and counterweight behind the cab,
     * 2.6 x 2.5 m, 1.3 m tall, its tail rounded by 0.9 m, louvred doors on both sides, a 0.45 m stack.
     */
    public static EngineHoodDesign excavatorHood() {
        return new EngineHoodDesign("excavator hood", 2.6, 2.5, 1.3, 0, 0.9, EngineHoodDesign.Grille.SIDES, 0.45);
    }

    /** A 1.7 t mini excavator's hood: 0.9 x 1.0 m, 0.75 m tall, its tail round (a short tail swing), side louvres, no stack. */
    public static EngineHoodDesign miniExcavatorHood() {
        return new EngineHoodDesign("mini excavator hood", 0.9, 1.0, 0.75, 0, 0.45, EngineHoodDesign.Grille.SIDES, 0);
    }

    /** An 18 t wheel loader's hood (CAT 950 class): 1.9 x 1.5 m, 1.35 m tall falling 0.25 m to the rear grille, a 0.5 m stack. */
    public static EngineHoodDesign loaderHood() {
        return new EngineHoodDesign("loader hood", 1.9, 1.5, 1.35, 0.25, 0.15, EngineHoodDesign.Grille.REAR, 0.5);
    }
}
