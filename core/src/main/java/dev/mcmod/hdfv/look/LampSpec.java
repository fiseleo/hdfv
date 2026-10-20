package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 0.16: one lamp a player placed on a body (the user's 「車燈也是要自由擺放」; a body's lamps, BodySettingsDesign.lamps):
 * <ul>
 * <li>Its role (LampRules.Role: what lights it: dipped or main beams, tail and brake, reversing, navigation, beacon,
 *   strobe, a boat's side, masthead and stern lights) and its style (the kit's round or projector headlamp, tail lamp
 *   cluster, navigation light or beacon; a square lamp, an LED strip, a light bar).</li>
 * <li>Where: on the body's side (its skin at x along the body and the angle t round it, as a sticker is placed), or on
 *   its front or rear face (across: -1 port edge to 1 starboard edge, up: 0 its bottom to 1 its top), so it stays on
 *   the body when the body is reshaped; standM out from there (a masthead light on its pole). A pair has a second lamp
 *   mirrored across the centreline.</li>
 * <li>Its size, light colour (blank: the role's), housing colour (blank: the kit's own), and aim (yaw and pitch from
 *   the surface's outward direction).</li>
 * </ul>
 * Looks only: lamps weigh nothing in the physics. LampLayout places them.
 */
public record LampSpec(LampRules.Role role, Style style, Face face, double x, double t, double across, double up, double standM, boolean pair,
                       double sizeM, String lightColor, String housingColor, double yawDeg, double pitchDeg) {
    /** 0.18: CLUSTER, a lens wrapping round a front's or rear's corner (FrontLayout.cluster). */
    public enum Style { ROUND, PROJECTOR, TAIL, SQUARE, STRIP, BAR, NAV, BEACON, CLUSTER }
    public enum Face { SIDE, FRONT, REAR }

    /** The most lamps a body may have (a pair counts two). */
    public static final int MAX = 32;

    /** A lamp's size, m: a 2 cm marker light to a 3 m light bar or LED strip (a truck's, a rescaled car's). */
    public static final double MIN_SIZE_M = 0.02, MAX_SIZE_M = 3;

    public LampSpec {
        Objects.requireNonNull(role, "role"); Objects.requireNonNull(style, "style"); Objects.requireNonNull(face, "face");
        if (lightColor == null) { lightColor = ""; }
        if (housingColor == null) { housingColor = ""; }
        // only the place its face uses (what is written)
        if (face == Face.SIDE) { across = 0; up = 0; } else { x = 0; t = 0; }
    }

    /** How many lamps it is (a pair is two). */
    public int count() { return pair ? 2 : 1; }

    public LampSpec moved(Face f, double nx, double nt, double nAcross, double nUp) {
        return new LampSpec(role, style, f, nx, nt, nAcross, nUp, standM, pair, sizeM, lightColor, housingColor, yawDeg, pitchDeg);
    }

    /** Problems of a body's lamps (paths under parameter: "lamps.3.sizeM"). */
    public static void check(List<DesignIssue> issues, String parameter, List<LampSpec> lamps) {
        int n = 0;
        for (int i = 0; i < lamps.size(); i++) {
            LampSpec l = lamps.get(i);
            String p = parameter + "." + i;
            n += l.count();
            if (!(l.sizeM >= MIN_SIZE_M && l.sizeM <= MAX_SIZE_M)) { issues.add(DesignIssue.invalid(p + ".sizeM", "A lamp is 2 cm to 3 m")); }
            if (!(Math.abs(l.yawDeg) <= 60 && Math.abs(l.pitchDeg) <= 60)) { issues.add(DesignIssue.invalid(p + ".yawDeg", "A lamp aims within 60 degrees of its surface")); }
            if (!(l.standM >= 0 && l.standM <= 3)) { issues.add(DesignIssue.invalid(p + ".standM", "A lamp stands 0 to 3 m out")); }
            if (!Double.isFinite(l.x) || !Double.isFinite(l.t) || !(Math.abs(l.across) <= 1) || !(l.up >= 0 && l.up <= 1)) {
                issues.add(DesignIssue.invalid(p + ".x", "Not a place on the body"));
            }
            if (!RecordEditor.isColor(l.lightColor, true) || !RecordEditor.isColor(l.housingColor, true)) {
                issues.add(DesignIssue.invalid(p + ".lightColor", "A colour is #RRGGBB, or blank"));
            }
            if (l.role == LampRules.Role.PANEL) { issues.add(DesignIssue.invalid(p + ".role", "Instrument lighting is the dashboard's")); }
        }
        if (n > MAX) { issues.add(DesignIssue.invalid(parameter, "At most " + MAX + " lamps (a pair is two): " + n)); }
    }

    public Object toJson() {
        var m = Fields.object("role", role.name().toLowerCase(Locale.ROOT), "style", style.name().toLowerCase(Locale.ROOT),
                "face", face.name().toLowerCase(Locale.ROOT), "sizeM", sizeM);
        if (face == Face.SIDE) { m.put("x", x); m.put("t", t); } else { m.put("across", across); m.put("up", up); }
        if (standM != 0) { m.put("standM", standM); }
        if (pair) { m.put("pair", true); }
        if (!lightColor.isEmpty()) { m.put("lightColor", lightColor); }
        if (!housingColor.isEmpty()) { m.put("housingColor", housingColor); }
        if (yawDeg != 0) { m.put("yawDeg", yawDeg); }
        if (pitchDeg != 0) { m.put("pitchDeg", pitchDeg); }
        return m;
    }

    public static List<Object> toJson(List<LampSpec> lamps) {
        List<Object> out = new ArrayList<>();
        for (LampSpec l : lamps) { out.add(l.toJson()); }
        return out;
    }

    static <E extends Enum<E>> E choice(Fields f, String key, Class<E> type, E fallback) {
        if (!f.has(key)) { return fallback; }
        String s = f.string(key);
        for (E e : type.getEnumConstants()) { if (e.name().equalsIgnoreCase(s)) { return e; } }
        throw new IllegalArgumentException(f.path() + "." + key + ": unknown value " + s);
    }

    /** A body's lamps from its settings' JSON (null when the key is missing: the kit's; an empty list is none). */
    public static List<LampSpec> list(Fields f, String key) {
        if (!f.has(key)) { return null; }
        List<LampSpec> out = new ArrayList<>();
        for (Fields l : f.objects(key)) {
            out.add(new LampSpec(choice(l, "role", LampRules.Role.class, LampRules.Role.HEAD_LOW), choice(l, "style", Style.class, Style.ROUND),
                    choice(l, "face", Face.class, Face.SIDE), l.number("x", 0), l.number("t", 0), l.number("across", 0), l.number("up", 0.5),
                    l.number("standM", 0), l.bool("pair", false), l.number("sizeM", 0.15), l.string("lightColor", ""), l.string("housingColor", ""),
                    l.number("yawDeg", 0), l.number("pitchDeg", 0)));
        }
        if (out.size() > MAX) { throw new IllegalArgumentException(f.path() + "." + key + ": more than " + MAX + " lamps"); }
        return List.copyOf(out);
    }
}
