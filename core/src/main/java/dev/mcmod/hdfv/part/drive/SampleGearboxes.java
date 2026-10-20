package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.vehicle.part.TransferPart;

/**
 * Example gearboxes and transfer cases on the scale of real ones (figures in each), starting points for the design
 * screen and the sample vehicles.
 */
public final class SampleGearboxes {
    private SampleGearboxes() {}

    private static final SynchroDesign BRASS = new SynchroDesign("brass cones", 1, 0.1, 600, 0.07, 6.5);

    private static GearboxDesign box(String name, Gearboxes.Kind kind, GearboxLayoutDesign layout, GearsetDesign gears, ClutchDesign clutch, SynchroDesign synchro,
                                     ConverterDesign converter, ShiftScheduleDesign control, VariatorDesign variator, CvtControlDesign cvt) {
        GearboxDesign d = (GearboxDesign) GearboxDesign.read(kind, dev.mcmod.aerocore.api.vehicle.Fields.of(java.util.Map.of(), "sample"));
        return new GearboxDesign(name, kind, layout, gears != null ? gears : d.gears(), clutch != null ? clutch : d.clutch(), synchro != null ? synchro : d.synchro(),
                converter != null ? converter : d.converter(), control != null ? control : d.control(), variator != null ? variator : d.variator(),
                cvt != null ? cvt : d.cvtControl());
    }

    /**
     * A front-drive car's five-speed transaxle for 250 N m: 3.45, 1.94, 1.29, 0.97, 0.78, reverse -3.17; a 228 mm dry
     * single plate (150 mm inside, 6 kN clamp, mu 0.27: about 300 N m), single brass cones.
     */
    public static GearboxDesign fiveSpeedManual() {
        return box("five-speed manual", Gearboxes.Kind.MANUAL, new GearboxLayoutDesign("case", 250, true, Double.NaN),
                GearsetDesign.of(-3.17, 0.97, 3.45, 1.94, 1.29, 0.97, 0.78), new ClutchDesign("dry single plate", 2, 0.27, 6000, 0.150, 0.228), BRASS,
                null, null, null, null);
    }

    /**
     * A rear-drive six-speed for 400 N m (Tremec T-56 class: 2.66, 1.78, 1.30, 1.00, 0.74, 0.50, reverse -2.90), a 260 mm
     * plate (165 mm inside, 8.5 kN), brass cones of 75 mm.
     */
    public static GearboxDesign sixSpeedManual() {
        return box("six-speed manual", Gearboxes.Kind.MANUAL, new GearboxLayoutDesign("case", 400, false, Double.NaN),
                GearsetDesign.of(-2.90, 0.97, 2.66, 1.78, 1.30, 1.00, 0.74, 0.50), new ClutchDesign("dry single plate", 2, 0.27, 8500, 0.165, 0.260),
                new SynchroDesign("brass cones", 1, 0.1, 600, 0.075, 6.5), null, null, null, null);
    }

    /**
     * A six-speed automatic for 600 N m from the ZF 6HP26's Lepelletier gearing (simple set 37/71, Ravigneaux 31/38/85:
     * 4.171, 2.340, 1.521, 1.143, 0.867, 0.691, reverse -3.403), a 0.26 m converter of stall ratio 1.9 with a 700 N m
     * lock-up, wet shift elements (8 faces, mu 0.12, 12 kN, 120/170 mm), shifting in 0.4 s: up at 1800 rpm light and
     * 5600 rpm full throttle (below a big V8's 6200 rpm limit), down at 1100 and 3800, locked up from third above 1400 rpm.
     */
    public static GearboxDesign sixSpeedAutomatic() {
        GearsetDesign gears = new GearsetDesign("Lepelletier", GearsetDesign.Source.LEPELLETIER, new double[]{3.6, 2.1, 1.4, 1.0, 0.8}, -3.5, 31, 85, 72, 38, 37, 71, 0.95);
        return box("six-speed automatic", Gearboxes.Kind.AUTOMATIC, new GearboxLayoutDesign("case", 600, false, Double.NaN), gears,
                new ClutchDesign("wet shift elements", 8, 0.12, 12000, 0.120, 0.170), null, new ConverterDesign("converter", 0.26, 1.9, 700),
                new ShiftScheduleDesign("shift schedule", false, 0.4, 1800, 5600, 1100, 3800, 3, 1400, 800, 2200, 0.04), null, null);
    }

    /**
     * A seven-speed dual-clutch box for 300 N m (3.76, 2.27, 1.53, 1.12, 0.86, 0.67, 0.53, reverse -3.99), wet clutches
     * of 6 faces (mu 0.12, 9.5 kN, 120/170 mm), handing over in 0.25 s; up at 1800 / 6800 rpm, down at 1100 / 4500,
     * launching toward 2500 rpm, creeping on 4 % of a clutch.
     */
    public static GearboxDesign sevenSpeedDualClutch() {
        return box("seven-speed dual clutch", Gearboxes.Kind.DUAL_CLUTCH, new GearboxLayoutDesign("case", 300, false, Double.NaN),
                GearsetDesign.of(-3.99, 0.96, 3.76, 2.27, 1.53, 1.12, 0.86, 0.67, 0.53), new ClutchDesign("wet clutches", 6, 0.12, 9500, 0.120, 0.170), BRASS,
                null, new ShiftScheduleDesign("shift schedule", true, 0.25, 1800, 6800, 1100, 4500, 3, 1400, 800, 2500, 0.04), null, null);
    }

    /**
     * A CVT for 250 N m (Jatco CVT8 class: 2.63 to 0.38, a 7.0 spread; reverse set -1.0), a push belt of mu 0.095 under
     * 45 kN at a 30 mm running radius (about 260 N m), its ratio moving 0.6 a second; a 0.236 m converter of stall ratio
     * 1.9 with a 300 N m lock-up; the engine held at 1500 rpm off throttle and 5500 at full, locked up above 1100 rpm.
     */
    public static GearboxDesign cvt() {
        return box("CVT", Gearboxes.Kind.CVT, new GearboxLayoutDesign("case", 250, true, Double.NaN), null, null, null,
                new ConverterDesign("converter", 0.236, 1.9, 300), null, new VariatorDesign("push belt", 2.63, 0.38, -1.0, 0.6, 0.095, 45000, 0.030, 11),
                new CvtControlDesign("ratio control", 1500, 5500, 1100));
    }

    /** An off-roader's part-time case for 600 N m with a 2.72 low range (NP231 class). */
    public static TransferCaseDesign partTime() {
        return new TransferCaseDesign("part-time 4WD", TransferPart.Kind.PART_TIME, 2.72, 0.97, 0.4, TransferPart.Centre.OPEN, 3, 40, false, true, 15, 1200, 0.3, 1500,
                600, false, Double.NaN);
    }

    /** An SUV's full-time case for 600 N m: a Torsen centre differential, 40 % front, bias ratio 3, no low range. */
    public static TransferCaseDesign fullTimeTorsen() {
        return new TransferCaseDesign("full-time Torsen", TransferPart.Kind.FULL_TIME, 1, 0.97, 0.4, TransferPart.Centre.TORSEN, 3, 40, false, true, 15, 1200, 0.3,
                1500, 600, false, Double.NaN);
    }

    /**
     * An on-demand coupling for a front-drive car (Haldex class, 300 N m): the rear through 15 N m per rad/s of slip up to
     * 1200 N m, preloaded with 30 % of it times the throttle below 1500 propeller-shaft rpm.
     */
    public static TransferCaseDesign onDemand() {
        return new TransferCaseDesign("on-demand coupling", TransferPart.Kind.ON_DEMAND, 1, 0.97, 0.4, TransferPart.Centre.OPEN, 3, 40, false, true, 15, 1200, 0.3,
                1500, 300, true, Double.NaN);
    }
}
