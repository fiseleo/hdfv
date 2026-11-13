package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.TransmissionPart;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A gearbox (變速箱) of one of four kinds, each its own part type: a manual (hdfv:manual_gearbox: case, gears, dry
 * clutch, synchronisers), a torque-converter automatic (hdfv:automatic: case, gears, shift elements, converter,
 * control), a dual-clutch box (hdfv:dual_clutch: case, gears, wet clutches, synchronisers, control) and a CVT
 * (hdfv:cvt: case, variator, converter, control). In the world it is AeroCore's TransmissionPart (the hardware)
 * inside hdfv's rules (ShiftRules): what the driver's inputs mean for each kind.
 *
 * <ul>
 * <li>Rotors and ports: "input" (a shaft from the engine joins it), the inner shafts, "output" (to an axle's or a
 *     transfer case's "input"). Along +x, or across (along -z) when transverse.</li>
 * <li>Inputs: a manual's "gear" (the lever: -1 reverse, 0 neutral, 1..n) and "clutch" (the pedal, 1 pressed); the
 *     others' "selector" (-2 P, -1 R, 0 N, 1 D, 2 M), "shift" (+1 up, -1 down, in M), "throttle" (0..1, the same channel
 *     as the engine's) and, for a dual-clutch box, "brake" (no creep while braking).</li>
 * <li>Inertias (rules, at a 300 N m rating and scaling with it): a manual's input side (pressure plate and cover)
 *     0.02 kg m^2, its clutch disc and primary shaft 0.008, output 0.01; a dual-clutch box's input (the double clutch)
 *     0.03, each inner shaft 0.008; an automatic's or CVT's the converter's pump and turbine (ConverterDesign).</li>
 * <li>Checks: the components'; the clutch (or shift elements, belt, lock-up) able to carry the rated torque (a clutch's
 *     reserve 1.2-2, LuK); a synchroniser's time to bring 2nd's gear to speed from 6000 rpm in 1st.</li>
 * </ul>
 */
public record GearboxDesign(String name, Gearboxes.Kind kind, GearboxLayoutDesign gearbox, GearsetDesign gears, ClutchDesign clutch, SynchroDesign synchro,
                            ConverterDesign converter, ShiftScheduleDesign control, VariatorDesign variator, CvtControlDesign cvtControl) implements PartDesign {
    public static final String MANUAL = "hdfv:manual_gearbox", AUTOMATIC = "hdfv:automatic", DUAL_CLUTCH = "hdfv:dual_clutch", CVT = "hdfv:cvt";

    public GearboxDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind");
        for (Object o : new Object[]{gearbox, gears, clutch, synchro, converter, control, variator, cvtControl}) { Objects.requireNonNull(o, "component"); }
        // the pages a kind does not have are kept at their defaults (so a design reads back equal), the control's kind with it
        Defaults d = Defaults.FOR;
        if (kind == Gearboxes.Kind.CVT) { gears = d.gears; clutch = d.clutch; synchro = d.synchro; control = d.control; }
        else { variator = d.variator; cvtControl = d.cvtControl; }
        if (kind == Gearboxes.Kind.MANUAL) { converter = d.converter; control = d.control; }
        if (kind == Gearboxes.Kind.AUTOMATIC) { synchro = d.synchro; }
        if (kind == Gearboxes.Kind.DUAL_CLUTCH) { converter = d.converter; }
        if (control.dualClutch() != (kind == Gearboxes.Kind.DUAL_CLUTCH)) {
            control = new ShiftScheduleDesign(control.name(), kind == Gearboxes.Kind.DUAL_CLUTCH, control.shiftTimeS(), control.upLightRpm(), control.upFullRpm(),
                    control.downLightRpm(), control.downFullRpm(), control.lockupGear(), control.lockupRpm(), control.idleRpm(), control.launchRpm(), control.creepShare());
        }
    }

    /** The components a kind has no use for, as read from nothing. */
    private record Defaults(GearsetDesign gears, ClutchDesign clutch, SynchroDesign synchro, ConverterDesign converter, ShiftScheduleDesign control,
                            VariatorDesign variator, CvtControlDesign cvtControl) {
        static final Defaults FOR = new Defaults(GearsetDesign.read("gears", Fields.of(Map.of(), "gears")), ClutchDesign.read("clutch", Fields.of(Map.of(), "clutch")),
                SynchroDesign.read("synchro", Fields.of(Map.of(), "synchro")), ConverterDesign.read("converter", Fields.of(Map.of(), "converter")),
                ShiftScheduleDesign.read("control", Fields.of(Map.of(), "control")), VariatorDesign.read("variator", Fields.of(Map.of(), "variator")),
                CvtControlDesign.read("cvtControl", Fields.of(Map.of(), "cvtControl")));
    }

    @Override public String type() { return typeOf(kind); }

    public static String typeOf(Gearboxes.Kind kind) {
        return switch (kind) { case MANUAL -> MANUAL; case AUTOMATIC -> AUTOMATIC; case DUAL_CLUTCH -> DUAL_CLUTCH; case CVT -> CVT; };
    }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("gearbox", gearbox);
        switch (kind) {
            case MANUAL -> { m.put("gears", gears); m.put("clutch", clutch); m.put("synchro", synchro); }
            case AUTOMATIC -> { m.put("gears", gears); m.put("clutch", clutch); m.put("converter", converter); m.put("control", control); }
            case DUAL_CLUTCH -> { m.put("gears", gears); m.put("clutch", clutch); m.put("synchro", synchro); m.put("control", control); }
            case CVT -> { m.put("variator", variator); m.put("converter", converter); m.put("cvtControl", cvtControl); }
        }
        return m;
    }

    /** Forward ratios (a CVT's lowest and highest). */
    public double[] ratios() { return kind == Gearboxes.Kind.CVT ? new double[]{variator.lowRatio(), variator.highRatio()} : gears.ratios(); }
    public double reverseRatio() { return kind == Gearboxes.Kind.CVT ? variator.reverseRatio() : gears.reverse(); }
    public int forwardGears() { return kind == Gearboxes.Kind.CVT ? 1 : ratios().length; }
    public double efficiency() { return kind == Gearboxes.Kind.CVT ? 0.88 : gears.efficiency(); }

    /** What carries the drive's torque: a clutch's, the shift elements', the belt's capacity, N m. */
    public double carryingNm() { return kind == Gearboxes.Kind.CVT ? variator.beltNm() : clutch.capacityNm(); }

    public double inputInertiaKgM2() {
        return switch (kind) {
            case MANUAL -> 0.02 * gearbox.scale();
            case DUAL_CLUTCH -> 0.03 * gearbox.scale();
            case AUTOMATIC, CVT -> converter.pumpInertiaKgM2();
        };
    }
    public double shaftInertiaKgM2() {
        return switch (kind) { case MANUAL, DUAL_CLUTCH -> 0.008 * gearbox.scale(); case AUTOMATIC, CVT -> converter.turbineInertiaKgM2(); };
    }
    public double outputInertiaKgM2() { return 0.01 * gearbox.scale(); }

    /** Time the synchroniser takes to bring 2nd gear to speed in an up-shift from 6000 rpm in 1st, s. */
    public double synchroTimeS() {
        double[] r = ratios();
        if (r.length < 2) { return 0; }
        double w = 6000 * Math.PI / 30, dw = w * (1 - r[1] / r[0]);
        return shaftInertiaKgM2() * dw / synchro.torqueNm();
    }

    /** AeroCore's gearbox. */
    public TransmissionPart.Spec spec() {
        boolean conv = kind == Gearboxes.Kind.AUTOMATIC || kind == Gearboxes.Kind.CVT;
        double shift = kind == Gearboxes.Kind.MANUAL || kind == Gearboxes.Kind.CVT ? 0.3 : control.shiftTimeS();
        return new TransmissionPart.Spec(kind.hardware, ratios(), reverseRatio(), efficiency(), kind == Gearboxes.Kind.CVT ? 0 : clutch.capacityNm(),
                kind == Gearboxes.Kind.MANUAL || kind == Gearboxes.Kind.DUAL_CLUTCH ? synchro.torqueNm() : 0, conv ? converter.converter() : null,
                conv ? converter.lockupNm() : 0, shift, kind == Gearboxes.Kind.CVT ? variator.ratioRatePerS() : 0, kind == Gearboxes.Kind.CVT ? variator.beltNm() : 0,
                inputInertiaKgM2(), shaftInertiaKgM2(), outputInertiaKgM2(), gearbox.transverse());
    }

    public double totalMassKg() { return gearbox.massKg(kind, forwardGears()); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = Components.issues(components());
        if (Components.invalid(issues)) { return issues; }
        double rating = gearbox.torqueRatingNm(), carry = carryingNm();
        String where = kind == Gearboxes.Kind.CVT ? "variator.clampForceN" : "clutch.clampForceN";
        if (carry < rating) { issues.add(DesignIssue.invalid(where, "It slips below the rated torque: carries " + Math.round(carry) + " N m of " + Math.round(rating))); }
        else if (kind == Gearboxes.Kind.MANUAL || kind == Gearboxes.Kind.DUAL_CLUTCH) {
            DesignIssue.range(issues, "clutchReserve", carry / rating, 1.2, 2.0, "clutch reserve over the engine's torque (LuK: cars 1.2-1.5, up to 2)");
        }
        if ((kind == Gearboxes.Kind.AUTOMATIC || kind == Gearboxes.Kind.CVT) && converter.lockupNm() < rating) {
            issues.add(DesignIssue.invalid("converter.lockupNm", "The lock-up clutch slips below the rated torque"));
        }
        if (kind == Gearboxes.Kind.AUTOMATIC && control.lockupGear() > forwardGears()) { issues.add(DesignIssue.invalid("control.lockupGear", "There is no such gear")); }
        if (kind == Gearboxes.Kind.MANUAL || kind == Gearboxes.Kind.DUAL_CLUTCH) {
            DesignIssue.range(issues, "synchroTime", synchroTimeS(), 0.05, 0.6, "synchronising times (Naunheimer: 0.15-0.35 s at a driver's shift)");
        }
        return issues;
    }

    public MassProperties mass() {
        double m = totalMassKg();
        Vec3d size = gearbox.transverse() ? new Vec3d(0.35, 0.35, 0.45) : new Vec3d(0.6, 0.3, 0.3);
        return Components.box(m, Vec3d.ZERO, size.scale(Math.cbrt(gearbox.scale())));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        // 0.25: the gears and the friction elements wear
        return ShiftRules.gearbox(this, new TransmissionPart(this, spec(), mass(), Components.extrapolations(issues)).wearing(dev.mcmod.hdfv.damage.Wearings.gearbox(this)));
    }

    @Override public Map<String, Object> parameters() { return Components.parameters(components()); }

    private static <T> T part(Fields f, String key, java.util.function.BiFunction<String, Fields, T> reader) {
        return f.has(key) ? Components.read(f, key, reader) : reader.apply(key, Fields.of(Map.of(), f.path() + "." + key));
    }

    public static PartDesign read(Gearboxes.Kind kind, Fields f) {
        return new GearboxDesign(f.string("name", "gearbox"), kind, part(f, "gearbox", GearboxLayoutDesign::read), part(f, "gears", GearsetDesign::read),
                part(f, "clutch", ClutchDesign::read), part(f, "synchro", SynchroDesign::read), part(f, "converter", ConverterDesign::read),
                part(f, "control", ShiftScheduleDesign::read), part(f, "variator", VariatorDesign::read), part(f, "cvtControl", CvtControlDesign::read));
    }
}
