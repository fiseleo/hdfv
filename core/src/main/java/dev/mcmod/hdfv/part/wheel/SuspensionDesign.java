package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An axle's suspension (懸吊), the same on both sides: its layout (AeroCore Suspension.Type: double wishbone,
 * multi-link, MacPherson strut, trailing or semi-trailing arms, swing axle, twist beam, solid axle on a Panhard rod, a
 * Watt linkage or leaf springs), travel, spring and dampers.
 *
 * <ul>
 * <li>Spring: the wheel rate (N/m at the wheel) and the sprung mass each corner carries at the design compression
 *     (designLoadKg): the spring's preload is set to hold it there (its ride height).</li>
 * <li>Dampers: linear, bump and rebound (N s/m at the wheel; rebound is usually 2-3 times bump).</li>
 * <li>Geometry: the front-view swing arm's length (0: the layout's usual) and its instant centre's height, or a solid
 *     axle's link height (negative: the layout's usual), a semi-trailing arm's sweep; static camber and toe. AeroCore
 *     turns them into the roll centre and the camber change (Suspension.Geometry).</li>
 * <li>Mass of one corner's arms, knuckle, spring and damper: CORNER_KG of the layout for a 400 kg corner, scaling as
 *     the load to the 0.7 (estimates from published corner weights: a MacPherson strut and its arm and knuckle about
 *     11 kg, a double wishbone corner 14 kg, a leaf spring 12 kg). A solid axle's beam is the axle's (AxleDesign).</li>
 * </ul>
 */
public record SuspensionDesign(String name, Suspension.Type layout, double travelM, double designCompressionM, double springRateNPerM, double designLoadKg,
                               double bumpNsPerM, double reboundNsPerM, double swingArmM, double linkHeightM, double sweepDeg, double camberDeg, double toeDeg,
                               double massGivenKg, SpringKind springKind, double bellowsDiameterM, double airVolumeM3, double rideLowM, double rideHighM,
                               double levelRatePerS) implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:suspension";
    public static final double G = 9.80665, ATMOSPHERE_PA = 101325;
    /** 0.15: the levelling valve's deadband, m (rule: a car's air suspension holds its height to a few millimetres). */
    public static final double DEADBAND_M = 0.004;

    /** 0.15: a coil (torsion bar, leaf: the wheel rate given) or an air spring (a bellows on a levelling valve; its rate from its air). */
    public enum SpringKind { COIL, AIR }

    public SuspensionDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(layout, "layout"); Objects.requireNonNull(springKind, "springKind");
    }

    /** 0.14's suspension: a coil spring. */
    public SuspensionDesign(String name, Suspension.Type layout, double travelM, double designCompressionM, double springRateNPerM, double designLoadKg,
                            double bumpNsPerM, double reboundNsPerM, double swingArmM, double linkHeightM, double sweepDeg, double camberDeg, double toeDeg, double massGivenKg) {
        this(name, layout, travelM, designCompressionM, springRateNPerM, designLoadKg, bumpNsPerM, reboundNsPerM, swingArmM, linkHeightM, sweepDeg, camberDeg, toeDeg,
                massGivenKg, SpringKind.COIL, 0.1, 0.004, 0.04, 0.05, 0.15);
    }

    /** 0.15: an air spring's effective area, m^2. */
    public double bellowsAreaM2() { return Math.PI * bellowsDiameterM * bellowsDiameterM / 4; }

    /** 0.15: an air spring's absolute pressure at the design compression holding the design load, Pa. */
    public double airDesignPa() { return designLoadKg * G / bellowsAreaM2() + ATMOSPHERE_PA; }

    /** 0.15: the spring as AeroCore has it: a coil's rate and preload, or the air spring's droop pressure from its design one. */
    public Suspension.Spring spring() {
        if (springKind == SpringKind.COIL) { return Suspension.Spring.coil(springRateNPerM, preloadN()); }
        double a = bellowsAreaM2(), droop = airDesignPa() * Math.pow((airVolumeM3 - a * designCompressionM) / airVolumeM3, Suspension.AIR_POLYTROPIC);
        return Suspension.Spring.air(a, airVolumeM3, droop);
    }

    /** 0.15: the spring's rate at the design compression, N/m (an air spring's from its air). */
    public double springRateAtDesign() { return springKind == SpringKind.COIL ? springRateNPerM : spring().rate(designCompressionM); }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "swingArmM" -> switch (layout) { case DOUBLE_WISHBONE, MULTI_LINK, MACPHERSON, SEMI_TRAILING_ARM, SWING_AXLE -> true; default -> false; };
            case "linkHeightM" -> layout != Suspension.Type.TRAILING_ARM && layout != Suspension.Type.SWING_AXLE;
            case "sweepDeg" -> layout == Suspension.Type.SEMI_TRAILING_ARM;
            case "springRateNPerM" -> springKind == SpringKind.COIL;
            case "bellowsDiameterM", "airVolumeM3", "rideLowM", "rideHighM", "levelRatePerS" -> springKind == SpringKind.AIR;
            default -> true;
        };
    }

    /** The spring's force at full droop that holds the design load at the design compression, N. */
    public double preloadN() { return Math.max(designLoadKg * G - springRateNPerM * designCompressionM, 0); }

    /** AeroCore's suspension for an axle's track and wheel centre height (m). */
    public Suspension suspension(double trackM, double wheelCentreM) { return suspension(trackM, wheelCentreM, null); }

    /** 0.15: the same moving on a kinematic table (null: the simple geometry), its air spring levelled (Suspension.Levelling). */
    public Suspension suspension(double trackM, double wheelCentreM, dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable kinematics) {
        Suspension.Geometry g = Suspension.Geometry.of(layout, trackM, wheelCentreM, swingArmM, linkHeightM, Math.toRadians(sweepDeg));
        if (springKind == SpringKind.COIL && kinematics == null) {
            return new Suspension(g, Suspension.Spring.coil(springRateNPerM, preloadN()), Suspension.Damper.linear(bumpNsPerM, reboundNsPerM), travelM,
                    Math.toRadians(camberDeg), designCompressionM);
        }
        Suspension.Levelling valve = springKind == SpringKind.AIR ? new Suspension.Levelling(rideLowM, rideHighM, levelRatePerS, levelRatePerS, DEADBAND_M) : null;
        return new Suspension(g, spring(), Suspension.Damper.linear(bumpNsPerM, reboundNsPerM), travelM, Math.toRadians(camberDeg), designCompressionM, kinematics, valve);
    }

    /** One corner's mass for a 400 kg corner, kg (estimates). */
    static double cornerKg(Suspension.Type t) {
        return switch (t) {
            case DOUBLE_WISHBONE -> 14; case MULTI_LINK -> 16; case MACPHERSON -> 11; case TRAILING_ARM -> 9; case SEMI_TRAILING_ARM -> 10;
            case SWING_AXLE -> 8; case TWIST_BEAM -> 9; case SOLID_PANHARD -> 9; case SOLID_WATT -> 10; case SOLID_LEAF -> 12;
        };
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(travelM > 0.01 && travelM < 1.5)) { issues.add(DesignIssue.invalid("travelM", "The travel must be 10 mm to 1.5 m")); }
        if (!(designCompressionM > 0 && designCompressionM < travelM)) { issues.add(DesignIssue.invalid("designCompressionM", "The design compression must lie within the travel")); }
        if (springKind == SpringKind.COIL) { Rules.positive(issues, "springRateNPerM", springRateNPerM); }
        else {
            if (!(bellowsDiameterM > 0.03 && bellowsDiameterM < 1)) { issues.add(DesignIssue.invalid("bellowsDiameterM", "A bellows is 30 mm to 1 m across")); }
            Rules.positive(issues, "airVolumeM3", airVolumeM3);
            if (!(rideLowM >= 0 && rideLowM < travelM) ) { issues.add(DesignIssue.invalid("rideLowM", "The low level is within the travel")); }
            if (!(rideHighM >= 0 && rideHighM < travelM)) { issues.add(DesignIssue.invalid("rideHighM", "The high level is within the travel")); }
            Rules.positive(issues, "levelRatePerS", levelRatePerS);
            if (issues.isEmpty() && airVolumeM3 - bellowsAreaM2() * 1.5 * travelM < 0.02 * airVolumeM3) {
                issues.add(DesignIssue.invalid("airVolumeM3", "The bellows would close before the bump stop: give it more air or a smaller diameter"));
            }
        }
        Rules.positive(issues, "designLoadKg", designLoadKg);
        Rules.nonnegative(issues, "bumpNsPerM", bumpNsPerM);
        Rules.nonnegative(issues, "reboundNsPerM", reboundNsPerM);
        if (shows("swingArmM")) { Rules.nonnegative(issues, "swingArmM", swingArmM); }
        if (layout == Suspension.Type.SEMI_TRAILING_ARM && !(sweepDeg >= 0 && sweepDeg < 60)) { issues.add(DesignIssue.invalid("sweepDeg", "The sweep must be 0 to 60 degrees")); }
        if (!(Math.abs(camberDeg) <= 10)) { issues.add(DesignIssue.invalid("camberDeg", "The camber must be within 10 degrees")); }
        if (!(Math.abs(toeDeg) <= 3)) { issues.add(DesignIssue.invalid("toeDeg", "The toe must be within 3 degrees")); }
        Rules.mass(issues, massGivenKg);
        if (!issues.isEmpty()) { return issues; }
        if (springKind == SpringKind.COIL && designLoadKg * G < springRateNPerM * designCompressionM) {
            issues.add(DesignIssue.invalid("designLoadKg", "The spring alone pushes harder than the load at the design compression: soften it or load it more"));
        }
        DesignIssue.range(issues, "reboundOverBump", bumpNsPerM > 0 ? reboundNsPerM / bumpNsPerM : 1, 1, 4, "dampers rebound 1.5-3 times as hard as they bump (Milliken, Race Car Vehicle Dynamics)");
        DesignIssue.range(issues, "camberDeg", camberDeg, -4, 1, "road cars' static camber");
        return issues;
    }

    /** Mass of one corner, kg. */
    @Override public double massKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        return cornerKg(layout) * Math.pow(Math.max(designLoadKg, 1) / 400, 0.7);
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("layout", layout.name().toLowerCase(), "travelM", travelM, "designCompressionM", designCompressionM, "springRateNPerM", springRateNPerM,
                "designLoadKg", designLoadKg, "bumpNsPerM", bumpNsPerM, "reboundNsPerM", reboundNsPerM, "swingArmM", swingArmM, "linkHeightM", linkHeightM,
                "sweepDeg", sweepDeg, "camberDeg", camberDeg, "toeDeg", toeDeg, "massKg", Rules.mass(massGivenKg));
        // 0.15: an air spring's keys only when it is one (a coil suspension's JSON is 0.14's)
        if (springKind == SpringKind.AIR) {
            m.putAll(Fields.object("springKind", "air", "bellowsDiameterM", bellowsDiameterM, "airVolumeM3", airVolumeM3, "rideLowM", rideLowM, "rideHighM", rideHighM,
                    "levelRatePerS", levelRatePerS));
        }
        return m;
    }

    public static SuspensionDesign read(String name, Fields f) {
        return new SuspensionDesign(name, Rules.choice(f, "layout", Suspension.Type.class, Suspension.Type.MACPHERSON), f.number("travelM", 0.18),
                f.number("designCompressionM", 0.11), f.number("springRateNPerM", 25000), f.number("designLoadKg", 400), f.number("bumpNsPerM", 1500),
                f.number("reboundNsPerM", 3500), f.number("swingArmM", 0), f.number("linkHeightM", -1), f.number("sweepDeg", 20), f.number("camberDeg", -0.5),
                f.number("toeDeg", 0), Rules.massOr(f), Rules.choice(f, "springKind", SpringKind.class, SpringKind.COIL), f.number("bellowsDiameterM", 0.1),
                f.number("airVolumeM3", 0.004), f.number("rideLowM", 0.04), f.number("rideHighM", 0.05), f.number("levelRatePerS", 0.15));
    }
}
