package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;
import dev.mcmod.hdfv.part.body.BodySettingsDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.16: where a body's lamps are (LampSpec): the player's own (BodySettingsDesign.lamps), or, with none, the kit's as
 * 0.12 placed them (auto): a car's headlamps and tail lamps on its blunt ends, an aircraft's beacons above and below
 * each cabin and its white tail light, a boat's sidelights, masthead light and stern light (COLREGS rule 21). Each lamp
 * placed is its point, outward direction (the face's, the skin's normal there, turned by its aim), the direction its
 * housing lies along, its size and colours, and the light the renderer gives it (a beam or a point and its sector),
 * all in the body frame. BodyModel draws them; the refit shop starts a player's list from auto.
 */
public final class LampLayout {
    private LampLayout() {}

    /** Light colours (ARGB): a headlight's warm white, a tail light's red, white, navigation red and green, amber. */
    public static final int HEAD = 0xfffff1dc, RED = 0xffff2a1a, WHITE = 0xfff4f6ff, NAV_RED = 0xffff2015, NAV_GREEN = 0xff2bff5a, AMBER = 0xffffa020;

    /**
     * One lamp placed: its style and role, where its housing stands (at, its +y along out, its length along along), its
     * size, light colour and housing colour (-1: the kit's own), and its light: from the lens along axis, a beam (beamDeg
     * wide, rangeM long) or a point (sectorDeg round); a dipped headlamp also has the main beam (second), a tail lamp
     * cluster its reversing light; stand: a pole from the skin to it (a masthead light), m. 0.18: the spec it came from
     * and whether it is its mirror image (a CLUSTER lamp's lens: FrontLayout.cluster).
     */
    public record Placed(LampSpec.Style style, LampRules.Role role, Vec3d at, Vec3d out, Vec3d along, double sizeM, int light, int housing,
                         Vec3d axis, double radiusM, boolean beam, double spreadDeg, double rangeM, double standM, Vec3d foot, LampSpec spec, boolean mirror) {}

    /** A role's light colour, and its light: radius, beam (true) or point, beam width or sector (deg), range (m). */
    static int colour(LampRules.Role r) {
        return switch (r) {
            case HEAD_LOW, HEAD_HIGH -> HEAD;
            case TAIL_BRAKE -> RED;
            case NAV_RED, SIDE_RED, BEACON -> NAV_RED;
            case NAV_GREEN, SIDE_GREEN -> NAV_GREEN;
            default -> WHITE;
        };
    }

    /** The default sector or beam width of a role, deg. */
    static double spread(LampRules.Role r) {
        return switch (r) {
            case HEAD_LOW -> 22;
            case FOG -> 70;
            case HEAD_HIGH -> 9;
            case TAIL_BRAKE -> 180;
            case REVERSE -> 160;
            case NAV_RED, NAV_GREEN -> 110;
            case NAV_WHITE -> 140;
            case SIDE_RED, SIDE_GREEN -> 112.5;
            case MASTHEAD -> 225;
            case STERN -> 135;
            default -> 360;
        };
    }

    /** Which way a role's lamp faces when it stands on a roof, hood or underside: 1 ahead, -1 astern, 0 its skin's way (plumb). */
    static int facing(LampRules.Role r) {
        return switch (r) {
            case HEAD_LOW, HEAD_HIGH, FOG -> 1;
            case TAIL_BRAKE, REVERSE -> -1;
            default -> 0;
        };
    }

    /**
     * How tall a lamp's housing is across its face, m (BodyModel.housing draws the square lamp, strip and bar to it; the
     * kit's lamps are about as tall as they are wide).
     */
    public static double housingHeight(LampSpec.Style style, double sizeM) {
        return switch (style) {
            case STRIP -> 0.12 * sizeM;
            case BAR -> 0.16 * sizeM;
            case SQUARE -> 0.6 * sizeM;
            case CLUSTER -> FrontLayout.CLUSTER_ASPECT * sizeM;
            default -> sizeM;
        };
    }

    /** A car end's edge rolled over by its radius (BodyModel's caps): its radius at a station, 0 for other ends. */
    public static double rollOver(BodyDesign d, BodyShape.Station st) {
        int module = Math.min(st.module(), d.modules().size() - 1);
        BodyModuleDesign m = d.modules().get(module);
        if (m.kind().family != BodyModuleDesign.Family.CAR || m.open() || st.areaM2() < 0.05) { return 0; }
        return Math.min(0.09, 0.18 * Math.min(0.5 * st.widthM(), st.topM() - st.bottomM()));
    }

    /** How far back from a pointed car end its lamps go on the skin, m (0.16). */
    static final double POINTED_BACK_M = 0.3;

    /**
     * The least share of the frontal area a car end's face needs for its lamps, grille and plate (0.16; 0.12 to 0.15
     * asked for 15 %, so a nose blunted to 0.45 of the hood's section, 0.8 m by 0.35 m, had none).
     */
    public static final double FACE_SHARE = 0.06;

    /**
     * The kit's lamps as 0.12 put them, as specs (the refit shop starts a player's list from these). 0.16: a car end too
     * pointed for a face of its own (under FACE_SHARE of the frontal area) has its pair on the skin just behind it,
     * turned toward the way the end faces as far as a lamp turns; 0.12 to 0.15 gave such a car no lamps there.
     */
    public static List<LampSpec> auto(BodyDesign d, BodySurface s, double waterline) {
        List<LampSpec> out = new ArrayList<>();
        BodyShape shape = s.shape;
        int last = s.xs.length - 1;
        double area = shape.particulars().frontalAreaM2();
        // a car's blunt ends: dipped (and main) headlamps 58 % up, 72 % out; tail clusters 62 % up. 0.18: a car's front
        // end's lamps are FrontLayout's (its headlamps by the front page's style, its fog lamps)
        for (int end : new int[]{last, 0}) {
            BodyShape.Station st = s.stations.get(end);
            BodyModuleDesign m = d.modules().get(Math.min(st.module(), d.modules().size() - 1));
            if (m.kind().family != BodyModuleDesign.Family.CAR) { continue; }
            boolean front = end == last;
            if (front && (st.areaM2() >= FACE_SHARE * area || m.kind() == BodyModuleDesign.Kind.DECK)) { out.addAll(FrontLayout.lamps(d, s)); continue; }
            LampRules.Role role = front ? LampRules.Role.HEAD_LOW : LampRules.Role.TAIL_BRAKE;
            LampSpec.Style style = front ? LampSpec.Style.PROJECTOR : LampSpec.Style.TAIL;
            double up = front ? 0.58 : 0.62;
            if (st.areaM2() >= FACE_SHARE * area) {
                double hw = half(d, s, end, st.bottomM() + up * (st.topM() - st.bottomM()));
                out.add(new LampSpec(role, style, front ? LampSpec.Face.FRONT : LampSpec.Face.REAR, 0, 0, -0.72, up, 0, true, (front ? 0.4 : 0.38) * hw, "", "", 0, 0));
                continue;
            }
            double x = Math.clamp(front ? s.front() - POINTED_BACK_M : s.rear() + POINTED_BACK_M, s.rear(), s.front());
            double y = s.bottom(x) + up * (s.top(x) - s.bottom(x)), t = s.sideAngle(x, y, false);
            Vec3d n = s.normal(x, t);
            // the yaw that turns the port lamp's outward direction toward the end (BodySurface's normal, place's turn)
            double from = Math.toDegrees(Math.atan2(-n.z(), n.x()));
            double yaw = Math.clamp(front ? -from : 180 - from, -60, 60);
            out.add(new LampSpec(role, style, LampSpec.Face.SIDE, x, t, 0, 0, 0, true, Math.clamp(0.25 * s.width(x), 0.06, 0.2), "", "", yaw, 0));
        }
        // an aircraft's cabins: a red beacon above and below, a fifth of the way along; the white tail light
        for (int i = 0; i < d.modules().size(); i++) {
            BodyModuleDesign m = d.modules().get(i);
            if (m.kind().family != BodyModuleDesign.Family.AIRCRAFT) { continue; }
            if (m.kind() == BodyModuleDesign.Kind.CABIN && !m.open()) {
                double x = shape.moduleRearX(i) + 0.2 * (shape.moduleFrontX(i) - shape.moduleRearX(i));
                out.add(new LampSpec(LampRules.Role.BEACON, LampSpec.Style.BEACON, LampSpec.Face.SIDE, x, Math.PI, 0, 0, 0, false, 0.12, "", "", 0, 0));
                out.add(new LampSpec(LampRules.Role.BEACON, LampSpec.Style.BEACON, LampSpec.Face.SIDE, x, 0, 0, 0, 0, false, 0.12, "", "", 0, 0));
            }
            if (i == 0) {
                BodyShape.Station st = s.stations.get(0);
                double h = st.topM() - st.bottomM(), up = h > 1e-9 ? (st.centroidYM() - st.bottomM()) / h : 0.5;
                out.add(new LampSpec(LampRules.Role.NAV_WHITE, LampSpec.Style.NAV, LampSpec.Face.REAR, 0, 0, 0, up, 0, false, 0.1, "", "", 0, 0));
            }
        }
        // a boat: sidelights at the bow's sheer, a masthead light on a pole on the foredeck, a stern light on the transom
        if (d.settings().purpose() == BodySettingsDesign.Purpose.BOAT && waterline > Double.NEGATIVE_INFINITY) {
            double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < d.modules().size(); i++) {
                if (d.modules().get(i).kind().family == BodyModuleDesign.Family.BOAT) { x0 = Math.min(x0, shape.moduleRearX(i)); x1 = Math.max(x1, shape.moduleFrontX(i)); }
            }
            if (x1 > x0) {
                double xb = Math.max(x0 + 0.2, x1 - 0.7);
                double tp = s.sideAngle(xb, s.top(xb) - 0.06, false);
                out.add(new LampSpec(LampRules.Role.SIDE_RED, LampSpec.Style.NAV, LampSpec.Face.SIDE, xb, tp, 0, 0, 0, false, 0.12, "", "", 0, 0));
                out.add(new LampSpec(LampRules.Role.SIDE_GREEN, LampSpec.Style.NAV, LampSpec.Face.SIDE, xb, 2 * Math.PI - tp, 0, 0, 0, false, 0.12, "", "", 0, 0));
                double xm = Math.max(x0 + 0.3, x1 - 1.0);
                out.add(new LampSpec(LampRules.Role.MASTHEAD, LampSpec.Style.NAV, LampSpec.Face.SIDE, xm, Math.PI, 0, 0, 0.8, false, 0.1, "", "", 0, 0));
                BodyShape.Station st = s.stations.get(0);
                double h = st.topM() - st.bottomM(), y = s.top(x0 + 0.05) - 0.05;
                out.add(new LampSpec(LampRules.Role.STERN, LampSpec.Style.NAV, LampSpec.Face.REAR, 0, 0, 0, h > 1e-9 ? Math.clamp((y - st.bottomM()) / h, 0, 1) : 0.5,
                        0, false, 0.1, "", "", 0, 0));
            }
        }
        return out;
    }

    /** Half the width of an end face at a height, inside its rolled edge (at least 5 cm). */
    static double half(BodyDesign d, BodySurface s, int end, double y) {
        double roll = rollOver(d, s.stations.get(end));
        return Math.max(Math.abs(s.hit(end, s.portAngleAt(end, y)).z()) - roll, 0.05);
    }

    /** The lamps of a body, placed: its own, or with none the kit's (auto). */
    public static List<Placed> of(BodyDesign d, BodySurface s, double waterline) {
        List<LampSpec> specs = d.settings().ownLamps() ? d.settings().lamps() : auto(d, s, waterline);
        List<Placed> out = new ArrayList<>();
        for (LampSpec l : specs) {
            out.add(place(d, s, l, false));
            if (l.pair()) { out.add(place(d, s, l, true)); }
        }
        return out;
    }

    /** One lamp (or its mirror image) placed. */
    public static Placed place(BodyDesign d, BodySurface s, LampSpec l, boolean mirror) {
        Vec3d at, out;
        int side;
        switch (l.face()) {
            case FRONT, REAR -> {
                boolean front = l.face() == LampSpec.Face.FRONT;
                int end = front ? s.xs.length - 1 : 0;
                BodyShape.Station st = s.stations.get(end);
                double across = mirror ? -l.across() : l.across(), y = st.bottomM() + l.up() * (st.topM() - st.bottomM());
                double roll = rollOver(d, st);
                at = new Vec3d(st.xM() + (front ? roll : -roll), y, across * half(d, s, end, y));
                out = new Vec3d(front ? 1 : -1, 0, 0);
                // 0.18: a lamp on the front's bumper sits on the bumper's face, standing out from the body's
                if (front) {
                    FrontLayout.Face f = FrontLayout.face(d, s);
                    if (f != null && f.bumper() && y <= f.bumperTop() + 1e-9) { at = at.add(new Vec3d(f.bumperOut(), 0, 0)); }
                }
                side = across < 0 ? -1 : across > 0 ? 1 : 0;
            }
            default -> {
                double t = mirror ? 2 * Math.PI - l.t() : l.t();
                at = s.point(l.x(), t);
                out = s.normal(l.x(), t);
                int way = facing(l.role());
                if (Math.abs(out.y()) > 0.7 && way != 0) {
                    // a lamp that lights the way (a headlamp, a light bar, a reversing or brake lamp) on a roof, hood or
                    // underside stands on the skin and faces along the vehicle, not into the sky
                    at = at.add(new Vec3d(0, Math.signum(out.y()) * 0.5 * housingHeight(l.style(), l.sizeM()), 0));
                    out = new Vec3d(way, 0, 0);
                }
                // a lamp straight up or down (a beacon, a masthead light) stands plumb
                if (Math.abs(out.y()) > 0.97) { out = new Vec3d(0, Math.signum(out.y()), 0); }
                if (Math.abs(out.z()) > 0.97) { out = new Vec3d(0, 0, Math.signum(out.z())); }
                side = at.z() < -0.01 ? -1 : at.z() > 0.01 ? 1 : 0;
            }
        }
        Vec3d foot = at;
        if (l.standM() > 0) { at = at.add(Vec3d.Y.scale(l.standM())); out = Vec3d.Y; }
        // the aim: yaw about y, pitch about the horizontal across the outward direction (a mirror image yaws the other way)
        double yaw = Math.toRadians(mirror ? -l.yawDeg() : l.yawDeg()), pitch = Math.toRadians(l.pitchDeg());
        if (yaw != 0 || pitch != 0) {
            Vec3d across = Vec3d.Y.cross(out);
            if (across.lengthSquared() < 1e-9) { across = Vec3d.Z; }
            out = Orientation.rotationVector(Vec3d.Y.scale(yaw)).rotate(Orientation.rotationVector(across.normalized().scale(pitch)).rotate(out)).normalized();
        }
        Vec3d along = Math.abs(out.x()) > 0.9
                ? (l.role() == LampRules.Role.NAV_WHITE || l.role() == LampRules.Role.STERN ? Vec3d.Y : l.style() == LampSpec.Style.TAIL ? new Vec3d(0, 0, side == 0 ? 1 : side) : Vec3d.Z)
                : Vec3d.X;
        int light = l.lightColor().isEmpty() ? colour(l.role()) : dev.mcmod.hdfv.design.RecordEditor.argb(l.lightColor());
        int housing = l.housingColor().isEmpty() ? -1 : dev.mcmod.hdfv.design.RecordEditor.argb(l.housingColor());
        boolean beam = l.role() == LampRules.Role.HEAD_LOW || l.role() == LampRules.Role.HEAD_HIGH || l.role() == LampRules.Role.FOG;
        Vec3d axis;
        double radius;
        switch (l.role()) {
            case HEAD_LOW -> { axis = out.add(Vec3d.Y.scale(-0.07)); radius = 0.07; }
            case HEAD_HIGH -> { axis = out.add(Vec3d.Y.scale(-0.015)); radius = 0.06; }
            case FOG -> { axis = out.add(Vec3d.Y.scale(-0.12)); radius = 0.05; }
            case SIDE_RED, SIDE_GREEN -> { axis = bearing(56.25, side == 0 ? (l.role() == LampRules.Role.SIDE_RED ? -1 : 1) : side); radius = 0.05; }
            case BEACON, STROBE -> { axis = out; radius = 0.07; }
            case REVERSE -> { axis = out; radius = 0.04; }
            case MASTHEAD -> { axis = Vec3d.X; radius = 0.05; }
            default -> { axis = out; radius = 0.05; }
        }
        double range = l.role() == LampRules.Role.HEAD_HIGH ? 110 : l.role() == LampRules.Role.FOG ? 20 : beam ? 45 : 0;
        return new Placed(l.style(), l.role(), at, out, along, l.sizeM(), light, housing, axis, radius, beam, spread(l.role()), range, l.standM(), foot, l, mirror);
    }

    /**
     * A lamp moved to a point of the body (body frame; a click on its mesh): on its front or rear face when the point is
     * on that blunt end (across and up as shares of the face), else on its side (BodySurface.locate).
     */
    public static LampSpec placeAt(BodyDesign d, BodySurface s, Vec3d p, LampSpec lamp) {
        int last = s.xs.length - 1;
        for (int end : new int[]{last, 0}) {
            BodyShape.Station st = s.stations.get(end);
            boolean front = end == last;
            if (st.areaM2() < 0.02 || (front ? p.x() < st.xM() - 0.02 : p.x() > st.xM() + 0.02)) { continue; }
            double h = st.topM() - st.bottomM(), up = h > 1e-9 ? Math.clamp((p.y() - st.bottomM()) / h, 0, 1) : 0.5;
            double across = Math.clamp(p.z() / half(d, s, end, p.y()), -1, 1);
            return lamp.moved(front ? LampSpec.Face.FRONT : LampSpec.Face.REAR, 0, 0, across, up);
        }
        BodySurface.Location l = s.locate(p);
        return lamp.moved(LampSpec.Face.SIDE, l.x(), l.t(), 0, 0);
    }

    /** A direction a number of degrees from dead ahead toward a side (-1 port), level (VehicleLamps.bearing's). */
    public static Vec3d bearing(double degrees, int side) {
        double a = Math.toRadians(degrees);
        return new Vec3d(Math.cos(a), 0, side * Math.sin(a));
    }

    /**
     * Warnings about a body's own lamps (EXTRAPOLATION): navigation and side lights on the wrong side (red is to port,
     * COLREGS and 14 CFR 23.1385), paired red or green lights, a light under a skin it is not on.
     */
    public static void check(List<DesignIssue> issues, String parameter, BodyDesign d, BodySurface s) {
        if (!d.settings().ownLamps()) { return; }
        List<LampSpec> lamps = d.settings().lamps();
        for (int i = 0; i < lamps.size(); i++) {
            LampSpec l = lamps.get(i);
            boolean red = l.role() == LampRules.Role.NAV_RED || l.role() == LampRules.Role.SIDE_RED;
            boolean green = l.role() == LampRules.Role.NAV_GREEN || l.role() == LampRules.Role.SIDE_GREEN;
            if (!red && !green) { continue; }
            if (l.pair()) {
                issues.add(new DesignIssue(DesignIssue.Kind.EXTRAPOLATION, parameter + "." + i + ".pair", 1, 0, 0, "COLREGS rule 21",
                        "A " + (red ? "red" : "green") + " navigation light on both sides"));
                continue;
            }
            Placed p = place(d, s, l, false);
            if (red && p.at().z() > 0.02 || green && p.at().z() < -0.02) {
                issues.add(new DesignIssue(DesignIssue.Kind.EXTRAPOLATION, parameter + "." + i + ".t", p.at().z(), red ? Double.NEGATIVE_INFINITY : 0, red ? 0 : Double.POSITIVE_INFINITY,
                        "COLREGS rule 21", red ? "A red light to starboard: red is to port" : "A green light to port: green is to starboard"));
            }
        }
    }
}
