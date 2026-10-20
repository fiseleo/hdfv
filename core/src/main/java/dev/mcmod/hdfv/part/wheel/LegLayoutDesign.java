package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.part.LegPart;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A landing-gear leg's layout (起落架佈局): how long it is (the mount point to the axle, fully extended), which side it
 * stands on (a main leg's side gives its tyre's camber sign; a nose or tail wheel stands on the centreline), and how
 * the wheel is steered: fixed, by the "steer" input up to an angle, or castering freely behind its pivot by a trail.
 * Its structure is the strut's mass.
 */
public record LegLayoutDesign(String name, double lengthM, Side side, LegPart.Steer steer, double maxSteerDeg, double trailM, double retractTimeS, Retract retract)
        implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:leg_layout";

    /**
     * 0.12: which way a retractable leg folds (it swings 90 degrees about its mount): back, forward, in toward the
     * fuselage or out (a centre leg folds back for in and out).
     */
    public enum Retract { AFT, FORWARD, INBOARD, OUTBOARD }

    /** 0.11 and before: a fixed leg. */
    public LegLayoutDesign(String name, double lengthM, Side side, LegPart.Steer steer, double maxSteerDeg, double trailM) {
        this(name, lengthM, side, steer, maxSteerDeg, trailM, 0, Retract.AFT);
    }

    /** 0.12: whether it folds away (a retraction time over zero). */
    public boolean retractable() { return retractTimeS > 0; }

    /** 0.12: the axis it folds about through its mount (part frame: x forward, y up, z to starboard), for an angle of +90 degrees. */
    public dev.mcmod.aerocore.api.Vec3d retractAxis() {
        int s = side.sign;
        return switch (s == 0 && (retract == Retract.INBOARD || retract == Retract.OUTBOARD) ? Retract.AFT : retract) {
            // the wheel (at -y) goes aft: about -z; forward: about +z; in toward z = 0 or out away from it: about x
            case AFT -> new dev.mcmod.aerocore.api.Vec3d(0, 0, -1);
            case FORWARD -> new dev.mcmod.aerocore.api.Vec3d(0, 0, 1);
            case INBOARD -> new dev.mcmod.aerocore.api.Vec3d(-s, 0, 0);
            case OUTBOARD -> new dev.mcmod.aerocore.api.Vec3d(s, 0, 0);
        };
    }

    /** Which side the leg stands on. */
    public enum Side {
        LEFT(-1), CENTRE(0), RIGHT(1);

        public final int sign;

        Side(int sign) { this.sign = sign; }
    }

    public LegLayoutDesign {
        Objects.requireNonNull(name, "name"); Objects.requireNonNull(side, "side"); Objects.requireNonNull(steer, "steer"); Objects.requireNonNull(retract, "retract");
    }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "maxSteerDeg" -> steer != LegPart.Steer.FIXED;
            case "trailM" -> steer == LegPart.Steer.CASTERING;
            case "retract" -> retractTimeS > 0;
            default -> true;
        };
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(lengthM > 0.05 && lengthM < 6)) { issues.add(DesignIssue.invalid("lengthM", "The leg must be 50 mm to 6 m long")); }
        if (steer != LegPart.Steer.FIXED && !(maxSteerDeg > 0 && maxSteerDeg <= 180)) { issues.add(DesignIssue.invalid("maxSteerDeg", "The largest angle must be 0 to 180 degrees")); }
        if (steer == LegPart.Steer.CASTERING && !(trailM > 0.005 && trailM < 1)) { issues.add(DesignIssue.invalid("trailM", "The trail must be 5 mm to 1 m")); }
        if (!(retractTimeS >= 0 && retractTimeS <= 60)) { issues.add(DesignIssue.invalid("retractTimeS", "The retraction takes 0 (a fixed leg) to 60 s")); }
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lengthM", lengthM, "side", side.name().toLowerCase(), "steer", steer.name().toLowerCase(), "maxSteerDeg", maxSteerDeg, "trailM", trailM,
                "retractTimeS", retractTimeS, "retract", retract.name().toLowerCase());
    }

    public static LegLayoutDesign read(String name, Fields f) {
        return new LegLayoutDesign(name, f.number("lengthM", 0.6), Rules.choice(f, "side", Side.class, Side.CENTRE), Rules.choice(f, "steer", LegPart.Steer.class, LegPart.Steer.FIXED),
                f.number("maxSteerDeg", 30), f.number("trailM", 0.05), f.number("retractTimeS", 0), Rules.choice(f, "retract", Retract.class, Retract.AFT));
    }
}
