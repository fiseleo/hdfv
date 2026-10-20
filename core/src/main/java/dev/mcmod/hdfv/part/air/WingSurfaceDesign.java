package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.aero.SectionPolar;
import dev.mcmod.aerocore.api.aero.Wing;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A trailing-edge control surface on a wing (控制面): what it does (a flap, an elevator, a rudder, an aileron, or none),
 * its kind (plain, split, slotted, Fowler), chord fraction, span range (fraction of the half span from the root) and
 * how far its trailing edge goes down and up. Its input role is the role's name ("flap", "elevator", "rudder",
 * "aileron"); an aileron moves the two sides oppositely. No mass of its own (part of the wing's skin).
 */
public record WingSurfaceDesign(String name, Role role, SectionPolar.FlapType kind, double chordFraction, double fromSpan, double toSpan,
                                double downDeg, double upDeg) implements EngineComponent {
    public static final String TYPE = "hdfv:wing_surface";

    public enum Role { NONE, FLAP, ELEVATOR, RUDDER, AILERON }

    public WingSurfaceDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(role, "role"); Objects.requireNonNull(kind, "kind"); }

    public static WingSurfaceDesign none(String name) { return new WingSurfaceDesign(name, Role.NONE, SectionPolar.FlapType.PLAIN, 0.25, 0.6, 0.95, 20, 20); }

    @Override public String type() { return TYPE; }

    public boolean active() { return role != Role.NONE; }
    public String input() { return role.name().toLowerCase(Locale.ROOT); }

    /** The surface for AeroCore's Wing, or null when there is none. */
    public Wing.ControlSurface surface() {
        return active() ? new Wing.ControlSurface(input(), kind, chordFraction, fromSpan, toSpan, downDeg, upDeg, role == Role.AILERON) : null;
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!active()) { return issues; }
        if (!(chordFraction > 0 && chordFraction <= 0.6)) { issues.add(DesignIssue.invalid("chordFraction", "The surface's chord must be up to 60 % of the wing's")); }
        else { DesignIssue.range(issues, "chordFraction", chordFraction, 0.15, 0.4, "flaps and control surfaces (DATCOM)"); }
        if (!(fromSpan >= 0 && toSpan <= 1 && toSpan > fromSpan + 0.02)) { issues.add(DesignIssue.invalid("toSpan", "The surface must span part of the wing (0 root, 1 tip)")); }
        if (!(downDeg >= 0 && downDeg <= 80)) { issues.add(DesignIssue.invalid("downDeg", "Travel down must be 0 to 80 degrees")); }
        if (!(upDeg >= 0 && upDeg <= 80)) { issues.add(DesignIssue.invalid("upDeg", "Travel up must be 0 to 80 degrees")); }
        if (issues.isEmpty()) { DesignIssue.range(issues, "downDeg", downDeg, 0, kind == SectionPolar.FlapType.PLAIN ? 30 : 45, "flap and control deflections of DATCOM's data"); }
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("role", input(), "kind", kind.name().toLowerCase(Locale.ROOT), "chordFraction", chordFraction, "fromSpan", fromSpan, "toSpan", toSpan,
                "downDeg", downDeg, "upDeg", upDeg);
    }

    static WingSurfaceDesign read(String name, Fields f) {
        return new WingSurfaceDesign(name, EngineComponent.Rules.choice(f, "role", Role.class, Role.NONE),
                EngineComponent.Rules.choice(f, "kind", SectionPolar.FlapType.class, SectionPolar.FlapType.PLAIN), f.number("chordFraction", 0.25),
                f.number("fromSpan", 0.6), f.number("toSpan", 0.95), f.number("downDeg", 20), f.number("upDeg", 20));
    }
}
