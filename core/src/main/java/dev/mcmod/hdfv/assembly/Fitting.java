package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.look.Envelope;
import dev.mcmod.hdfv.look.Envelopes;
import dev.mcmod.hdfv.part.body.BodyDesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 0.14 (the user: 「加入引擎的大小要符合大小才能裝車」): whether what goes in a body's engine bay fits in it. Parts attached
 * straight to a body's bay point ("engine_bay", "mN_engine") that have an envelope (engines of every kind, motors,
 * gearboxes, transfer cases) must lie inside the body: each of their envelope's boxes, placed by the point and the
 * part's own offset, is checked against the body's shape (BodyShape.overhang: the bay is open underneath, so a sump may
 * hang below the body's bottom line). A box sticking out more than TOLERANCE_M through the top, a side or an end is a
 * misfit; an assembly room will not start such a vehicle (RoomRules "part_does_not_fit"). Parts on other points (a
 * nose's "engine", a "mast", a "transom") or free are not checked: an aircraft's or a boat's engines stand where they will.
 */
public final class Fitting {
    private Fitting() {}

    /** Sticking out by more than this is a misfit, m (a skin's thickness and the sampling). */
    public static final double TOLERANCE_M = 0.005;
    /** The grid the boxes' faces are sampled on, m. */
    public static final double STEP_M = 0.05;
    /** The room Placing leaves round a part it slides into a bay, m. */
    public static final double CLEARANCE_M = 0.01;
    /** The bay points. */
    public static final Pattern BAY = Pattern.compile("engine_bay|m\\d+_engine");

    /** A part's box sticking out of its body: the part, the box and how far through each side. */
    public record Misfit(String part, String box, BodyShape.Overhang out) {
        /** The side it sticks out farthest ("top", "port", ...). */
        public String side() { return out.worst(); }
        /** How far, cm, rounded up. */
        public int cm() { return (int) Math.ceil(out.maxM() * 100 - 1e-9); }
    }

    /** A part in its body's frame: the body's design and shape, and where the part is (its point, then its own offset). */
    public record InBody(String body, BodyDesign design, BodyShape shape, Mount placement) {}

    /**
     * Where an entry sits in its body when it is on a bay point of one (null otherwise); 0.15: or on a chassis's bay
     * ("engine_bay") with a body on the same chassis, in that body's frame; 0.18: not a cab-over cab (the engine and
     * gearbox under it run back under the frame past it; ChassisRules.truck's engine_in_cab checks it).
     */
    public static InBody inBody(VehicleBlueprint b, VehicleBlueprint.Entry e) {
        Mount m = e.mount();
        if (!m.attached() || !BAY.matcher(m.attachPoint()).matches()) { return null; }
        VehicleBlueprint.Entry host = b.part(m.attachPart());
        if (host == null) { return null; }
        if (host.design() instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign c) {
            Mount point = c.mountPoints().get(m.attachPoint());
            if (point == null) { return null; }
            for (VehicleBlueprint.Entry x : b.parts()) {
                if (!(x.design() instanceof BodyDesign d) || !host.id().equals(x.mount().attachPart()) || Placing.cab(d)) { continue; }
                Mount seat = c.mountPoints().get(x.mount().attachPoint());
                if (seat == null) { continue; }
                return new InBody(x.id(), d, d.shape(), inverse(seat.then(x.mount().detached())).then(point.then(m.detached())));
            }
            return null;
        }
        if (!(host.design() instanceof BodyDesign d)) { return null; }
        BodyShape shape = d.shape();
        Mount point = d.mountPoints(shape).get(m.attachPoint());
        return point == null ? null : new InBody(host.id(), d, shape, point.then(m.detached()));
    }

    /** The placement that undoes one (0.15). */
    static Mount inverse(Mount m) {
        dev.mcmod.aerocore.api.Orientation q = m.rotation().inverse();
        return Mount.of(q.rotate(m.position()).scale(-1), q);
    }

    /** How far an envelope placed in a body sticks out (each side the most of any box), its boxes grown by a margin. */
    public static BodyShape.Overhang overhang(BodyShape shape, Envelope env, Mount placement, double grow) {
        BodyShape.Overhang worst = BodyShape.Overhang.NONE;
        Vec3d g = new Vec3d(grow, grow, grow);
        for (Envelope.Box box : env.boxes()) {
            worst = worst.max(shape.overhang(box.min().subtract(g), box.max().add(g), placement.rotation(), placement.position(), STEP_M));
        }
        return worst;
    }

    /** Every part of a layout that does not fit in its body: its worst box. */
    public static List<Misfit> misfits(VehicleBlueprint b) {
        List<Misfit> out = new ArrayList<>();
        for (VehicleBlueprint.Entry e : b.parts()) {
            InBody in = inBody(b, e);
            Envelope env = in == null ? null : Envelopes.of(e.design());
            if (env == null) { continue; }
            Misfit worst = null;
            for (Envelope.Box box : env.boxes()) {
                BodyShape.Overhang o = in.shape().overhang(box.min(), box.max(), in.placement().rotation(), in.placement().position(), STEP_M);
                if (o.maxM() > TOLERANCE_M && (worst == null || o.maxM() > worst.out().maxM())) { worst = new Misfit(e.id(), box.name(), o); }
            }
            if (worst != null) { out.add(worst); }
        }
        return out;
    }

    /** How near a seat part may come to a body's own seat, m. */
    public static final double SEAT_CLEARANCE_M = 0.4;

    /**
     * 0.14: the seat parts (SeatDesign) on a body with seats of its own that sit on one of them (within
     * SEAT_CLEARANCE_M) or in its dashboard (the seat's room, 0.35 m ahead of its point to 0.30 behind, 0.27 under to
     * 0.60 over, 0.25 either side, in the dash's box): the sample sedan carried two in its windscreen module from 0.7 to
     * 0.13, their backs standing out of the dash in front of the driver.
     */
    public static List<String> seatClashes(VehicleBlueprint b) {
        List<String> out = new ArrayList<>();
        for (VehicleBlueprint.Entry e : b.parts()) {
            if (!(e.design() instanceof dev.mcmod.hdfv.part.SeatDesign) || !e.mount().attached()) { continue; }
            VehicleBlueprint.Entry body = null;
            for (VehicleBlueprint.Entry x : b.parts()) { if (x.id().equals(e.mount().attachPart())) { body = x; } }
            if (body == null || !(body.design() instanceof BodyDesign d)) { continue; }
            BodyShape shape = d.shape();
            Mount point = d.mountPoints(shape).get(e.mount().attachPoint());
            if (point == null) { continue; }
            Mount at = point.then(e.mount().detached());
            boolean clash = false;
            for (BodyDesign.Seat s : d.seats(shape)) { clash |= s.at().subtract(at.position()).length() < SEAT_CLEARANCE_M; }
            dev.mcmod.hdfv.look.BodyLayout.Dash dash = dev.mcmod.hdfv.look.BodyLayout.of(d).dash();
            if (dash != null && !clash) {
                // the seat's room (the box round its turned corners) against the dash's (its x and its height: it spans the body)
                double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
                for (int k = 0; k < 8; k++) {
                    Vec3d c = at.apply(new Vec3d((k & 1) == 0 ? -0.30 : 0.35, (k & 2) == 0 ? -0.27 : 0.60, (k & 4) == 0 ? -0.25 : 0.25));
                    x0 = Math.min(x0, c.x()); x1 = Math.max(x1, c.x()); y0 = Math.min(y0, c.y()); y1 = Math.max(y1, c.y());
                }
                clash = x1 > dash.rearX() && x0 < dash.frontX() && y1 > dash.bottomY() && y0 < dash.topY();
            }
            if (clash) { out.add(e.id()); }
        }
        return out;
    }

    /** The misfits by part id. */
    public static Map<String, Misfit> byPart(List<Misfit> misfits) {
        Map<String, Misfit> out = new LinkedHashMap<>();
        for (Misfit m : misfits) { out.put(m.part(), m); }
        return out;
    }
}
