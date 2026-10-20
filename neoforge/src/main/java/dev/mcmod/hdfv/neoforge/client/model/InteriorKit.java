package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Finish;
import dev.mcmod.aerocore.api.mesh.Mesh;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.MeshOps;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.look.BodyLayout;
import dev.mcmod.hdfv.look.BodySurface;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign.Family;
import dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A body's inside (0.12), from the interior kit (tools/blender/interior.py, DetailKit) placed by the body's layout:
 * <ul>
 * <li>seats: a car's buckets (a bench where three sit abreast), an aircraft's light seats (an airliner's in a cabin
 *     two metres wide or more), a boat's in white leather;</li>
 * <li>a car (left-hand drive): the dashboard from the firewall to 0.62 m ahead of the driver's seat, its instrument
 *     binnacle, a steering wheel 0.4 m ahead and 0.4 m up on a column raked 23 degrees, the brake and accelerator
 *     pedals, a console between two front seats with its gear lever, the centre stack over the transmission tunnel
 *     (0.14), and two wipers parked at the foot of the windscreen;</li>
 * <li>a cockpit: the instrument panel 0.95 m ahead of the front row up to the windscreen's lower edge, six gauges for
 *     each front seat, a glareshield; a yoke for each front seat (a stick for a single seat; a cyclic and a collective
 *     where the body carries a rotor mast), rudder pedals, a throttle quadrant between two pilots;</li>
 * <li>an open boat: a helm console ahead of the helmsman's seat with the wheel, gauges and the throttle.</li>
 * </ul>
 * The parts that move are pieces of the body's mesh, each with how it is posed from named values: the vehicle's
 * control channels ("steer" or else "rudder", "throttle", "brake", "pitch", "roll", "collective") and "wipe" (a
 * wiper's sweep, 0 parked to 1 at the top). Closed modules' insides are drawn into the cabin mesh (nearest only),
 * open ones' into the shown mesh (every level).
 */
final class InteriorKit {
    private InteriorKit() {}

    /** A moving part: its piece and how it is posed from named values. */
    record Moving(int piece, Function<Map<String, Double>, EngineModel.Placement> pose) {}

    private static final int DASH = 0xff26282b, CONSOLE = 0xff2d2f33, PANEL = 0xff2b2e33, GLARE = 0xff1d1f22, SCREEN = 0xff0d1116,
            HELM_CONSOLE = 0xffe9e6dd, WHITE_LEATHER = 0xffeeeae0, SLATE_LEATHER = 0xff4d5566;

    private record Context(BodyDesign d, BodyLayout l, BodySurface s, double wall, PartMesh cabin, PartMesh shown, List<Moving> moving, int[] next) {
        int piece() { return next[0]++; }
        PartMesh in(int module) { return d.modules().get(Math.min(module, d.modules().size() - 1)).open() ? shown : cabin; }
    }

    /** Builds a body's inside; the moving parts get pieces from firstPiece on. */
    static List<Moving> build(BodyDesign d, BodyLayout l, double wall, int firstPiece, PartMesh cabin, PartMesh shown) {
        Context c = new Context(d, l, l.surface, wall, cabin, shown, new ArrayList<>(), new int[]{firstPiece});
        seats(c);
        if (l.driver >= 0) {
            BodyDesign.Seat driver = l.seats.get(l.driver);
            BodyModuleDesign m = d.modules().get(Math.min(driver.module(), d.modules().size() - 1));
            Purpose purpose = d.settings().purpose();
            if (purpose == Purpose.CAR && !Double.isNaN(l.belt)) { car(c, driver); }
            else if (purpose == Purpose.AIRCRAFT && !m.open()) { cockpit(c, driver); }
            else if (m.open()) { helm(c, driver); }
        }
        return c.moving();
    }

    // ---------------------------------------------------------------- placing kit parts

    /** A kit part at a point, its +y along up and +x toward along, scaled (each axis of the kit's own frame). */
    private static Mesh part(DetailKit.Part part, Vec3d at, Vec3d up, Vec3d along, Vec3d scale) {
        return MeshOps.placed(DetailKit.mesh(part, true), scale, Details.basis(up, along), at);
    }

    /** A placed part added with the mesh's pens (its finishes kept). */
    private static void add(PartMesh out, MeshBuilder<?> m) {
        int tags = out.tags();
        for (MeshBuilder.Quad q : m.quads()) { out.quads().add(q.withTags(tags)); }
    }

    /** A mesh's quads of one finish given another finish and colour (a seat's cloth made white leather). */
    private static Mesh recoloured(Mesh m, Finish from, Finish to, int colour) {
        List<MeshBuilder.Quad> quads = m.quads();
        for (int i = 0; i < quads.size(); i++) {
            MeshBuilder.Quad q = quads.get(i);
            if (q.finish() == from) { quads.set(i, q.withColor(colour).withFinish(to)); }
        }
        return m;
    }

    private static Vec3d uniform(double k) { return new Vec3d(k, k, k); }

    /** Turning by an angle about an axis through a pivot. */
    static EngineModel.Placement about(Vec3d pivot, Vec3d axis, double angle) {
        Orientation r = Orientation.rotationVector(axis.normalized().scale(angle));
        return new EngineModel.Placement(pivot.subtract(r.rotate(pivot)), r);
    }

    private static double value(Map<String, Double> in, String channel) { return in.getOrDefault(channel, 0.0); }

    /** The floor (the liner's bottom) at x. */
    private static double floor(Context c, double x) { return c.s().point(x, 0).y() + c.wall(); }

    /**
     * The floor under a point across the cabin (x, z): an open module's sole (as BodyModel draws it: 0.45 m under the
     * open edge, at least 12 cm over the keel), else the liner's bottom where it is that far out (a V-bottom rises
     * toward the sides).
     */
    private static double floorAt(Context c, int module, double x, double z) {
        BodySurface s = c.s();
        BodyModuleDesign m = c.d().modules().get(Math.min(module, c.d().modules().size() - 1));
        if (m.open()) {
            double sill = c.d().shape().modules().get(Math.min(module, c.d().modules().size() - 1)).sillM(), keel = s.point(x, 0).y();
            return Math.min(Math.max(keel + 0.12, sill - 0.45), sill - 0.05);
        }
        double lo = 0, hi = Math.PI / 2, target = Math.abs(z);
        if (Math.abs(s.point(x, hi).z()) < target) { return s.point(x, hi).y() + c.wall(); }
        for (int it = 0; it < 30; it++) {
            double mid = 0.5 * (lo + hi);
            if (Math.abs(s.point(x, mid).z()) < target) { lo = mid; } else { hi = mid; }
        }
        return s.point(x, hi).y() + c.wall();
    }

    /** Where a seat's kit part stands: its cushion where the seat point's is (the kit's is 0.31 up), never under the floor. */
    private static Vec3d seatBase(Context c, BodyDesign.Seat seat, double halfWidth) {
        Vec3d at = seat.at();
        double floor = Math.max(floorAt(c, seat.module(), at.x() + 0.2, Math.abs(at.z()) + halfWidth), floorAt(c, seat.module(), at.x() - 0.24, Math.abs(at.z()) + halfWidth));
        return new Vec3d(at.x() + 0.03, Math.max(at.y() - 0.27, floor), at.z());
    }

    /** A cabin seat's half width and height over its base (the kit's), m. */
    private static final double SEAT_HALF = 0.24, SEAT_TALL = 0.8;

    /**
     * A seat's base moved toward the centreline as far as its back's top (half wide, tall over the base, 0.3 m behind
     * it) would be beyond the liner: a fuselage's sides curve in at shoulder height (the helicopter's outboard seats
     * reached 5 cm into the wall). Never past the centreline.
     */
    private static Vec3d inboard(Context c, Vec3d base, double halfWidth, double tall) {
        // the liner's inside at three heights up the back, at the seat and 0.3 m behind it; 3 cm more where the wall curves
        // (the liner is a wall in along the slanting normal: farther in across than the wall's thickness)
        double room = Double.POSITIVE_INFINITY;
        for (double up : new double[]{0.5, 0.75, 1.0}) {
            for (double back : new double[]{0, 0.3}) { room = Math.min(room, half(c, base.x() - back, base.y() + up * tall) - 0.03); }
        }
        double z = base.z(), excess = Math.abs(z) + halfWidth - room;
        if (!(excess > 0)) { return base; }
        double moved = Math.max(Math.abs(z) - excess, Math.min(Math.abs(z), halfWidth));
        return new Vec3d(base.x(), base.y(), Math.signum(z) * moved);
    }

    /** Half the cabin's width inside the liner at x and height y. */
    private static double half(Context c, double x, double y) {
        BodySurface s = c.s();
        return Math.max(0.1, Math.abs(s.point(x, s.portAngle(x, y)).z()) - c.wall() - 0.01);
    }

    // ---------------------------------------------------------------- seats

    /** Every seat from the kit: a row of three or more in a car is a bench. */
    private static void seats(Context c) {
        List<BodyDesign.Seat> seats = c.l().seats;
        boolean[] done = new boolean[seats.size()];
        for (int k = 0; k < seats.size(); k++) {
            if (done[k]) { continue; }
            BodyDesign.Seat seat = seats.get(k);
            BodyModuleDesign m = c.d().modules().get(Math.min(seat.module(), c.d().modules().size() - 1));
            Family family = m.kind().family;
            PartMesh out = c.in(seat.module());
            Vec3d at = seat.at();
            Vec3d base = seatBase(c, seat, 0.25);
            if (family == Family.CAR) {
                List<BodyDesign.Seat> row = new ArrayList<>();
                for (int j = k; j < seats.size(); j++) {
                    BodyDesign.Seat o = seats.get(j);
                    if (o.module() == seat.module() && Math.abs(o.at().x() - at.x()) < 1e-6) { row.add(o); done[j] = true; }
                }
                if (row.size() >= 3) {
                    double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
                    for (BodyDesign.Seat o : row) { lo = Math.min(lo, o.at().z()); hi = Math.max(hi, o.at().z()); }
                    double width = Math.min(hi - lo + 0.5, 2 * half(c, at.x(), at.y()));
                    Vec3d bench = seatBase(c, row.get(0), 0.5 * width - Math.abs(row.get(0).at().z()));
                    add(out, part(DetailKit.Part.BENCH_SEAT, new Vec3d(bench.x(), bench.y(), 0.5 * (lo + hi)), Vec3d.Y, Vec3d.X, new Vec3d(1, 1, width / 1.3)));
                } else {
                    for (BodyDesign.Seat o : row) { add(out, part(DetailKit.Part.CAR_SEAT, seatBase(c, o, 0.25), Vec3d.Y, Vec3d.X, uniform(1))); }
                }
                continue;
            }
            done[k] = true;
            // 0.12 (clip survey): an outboard seat whose back would reach into a wall curving in moves inboard to clear it
            if (!m.open()) { base = inboard(c, base, SEAT_HALF, SEAT_TALL); }
            Mesh mesh;
            if (family == Family.BOAT && m.open()) {
                mesh = recoloured(part(DetailKit.Part.CAR_SEAT, base, Vec3d.Y, Vec3d.X, uniform(1)), Finishes.FABRIC, Finishes.LEATHER, WHITE_LEATHER);
            } else if (m.kind() != BodyModuleDesign.Kind.COCKPIT && c.s().width(at.x()) >= 2.0) {
                mesh = part(DetailKit.Part.CABIN_SEAT, base, Vec3d.Y, Vec3d.X, uniform(1));
            } else {
                mesh = recoloured(part(DetailKit.Part.AIRCRAFT_SEAT, base, Vec3d.Y, Vec3d.X, uniform(1)), Finishes.LEATHER, Finishes.LEATHER, SLATE_LEATHER);
            }
            add(out, mesh);
        }
    }

    /** The seats of the driver's row (the frontmost seats in the driver's module). */
    private static List<BodyDesign.Seat> frontRow(Context c, BodyDesign.Seat driver) {
        List<BodyDesign.Seat> row = new ArrayList<>();
        for (BodyDesign.Seat s : c.l().seats) {
            if (s.module() == driver.module() && Math.abs(s.at().x() - driver.at().x()) < 1e-6) { row.add(s); }
        }
        return row;
    }

    // ---------------------------------------------------------------- a car

    private static void car(Context c, BodyDesign.Seat driver) {
        BodySurface s = c.s();
        PartMesh out = c.cabin();
        Vec3d at = driver.at();
        double belt = c.l().belt, floor = floor(c, at.x());
        // 0.14: where BodyLayout has it (the room's seat_clash rule reads the same box)
        BodyLayout.Dash dash = c.l().dash();
        if (dash == null) {
            double r = at.x() + 0.62;
            dash = new BodyLayout.Dash(r, Math.max(c.l().firewall(r + 0.2), r + 0.15), belt - 0.30, belt + 0.06);
        }
        double dashRear = dash.rearX(), dashFront = dash.frontX();
        double y0 = dash.bottomY(), y1 = belt + 0.02, y2 = dash.topY(), hwRear = half(c, dashRear, belt - 0.1), hwFront = half(c, dashFront, belt - 0.1);
        Finish pen = out.finish();
        // the dashboard: a slab from the firewall back, its top falling toward the driver, a rounded lip along it
        out.finish(Finishes.DASH_PLASTIC);
        out.hexahedron(new Vec3d[]{
                new Vec3d(dashRear, y0, -hwRear), new Vec3d(dashRear, y0, hwRear), new Vec3d(dashFront, y0, hwFront), new Vec3d(dashFront, y0, -hwFront),
                new Vec3d(dashRear, y1, -hwRear), new Vec3d(dashRear, y1, hwRear), new Vec3d(dashFront, y2, hwFront), new Vec3d(dashFront, y2, -hwFront)}, DASH);
        out.cylinder(new Vec3d(dashRear, y1 - 0.025, -hwRear), new Vec3d(dashRear, y1 - 0.025, hwRear), 0.03, 0.03, 10, DASH);
        // a screen in the middle of its face
        out.box(new Vec3d(dashRear - 0.006, y1 - 0.2, -0.13), new Vec3d(dashRear, y1 - 0.08, 0.13), SCREEN);
        out.finish(pen);
        add(out, part(DetailKit.Part.BINNACLE, new Vec3d(dashRear + 0.1, y1 - 0.005, at.z()), Vec3d.Y, Vec3d.X, uniform(1)));
        // the steering wheel on its column, raked 23 degrees, turning with the steering
        Vec3d toward = new Vec3d(-Math.cos(Math.toRadians(23)), Math.sin(Math.toRadians(23)), 0), wheel = new Vec3d(at.x() + 0.40, at.y() + 0.40, at.z());
        int piece = c.piece();
        out.piece(piece);
        add(out, part(DetailKit.Part.STEERING_WHEEL, wheel, toward, Vec3d.Y, uniform(1)));
        out.piece(0);
        c.moving().add(new Moving(piece, in -> about(wheel, toward, -Math.PI * 2.5 * steer(in))));
        // the pedals hanging from under the dash: brake and accelerator
        for (int k = 0; k < 2; k++) {
            boolean brake = k == 0;
            Vec3d pad = new Vec3d(at.x() + 0.78, floor + 0.06, at.z() + (brake ? 0.02 : 0.17)), pivot = pad.add(new Vec3d(0, 0.28, 0));
            int p = c.piece();
            out.piece(p);
            add(out, part(DetailKit.Part.PEDAL, pad, Vec3d.Y, Vec3d.X, new Vec3d(1, 1, brake ? 1.3 : 0.9)));
            out.piece(0);
            c.moving().add(new Moving(p, in -> about(pivot, Vec3d.Z, 0.25 * Math.clamp(value(in, brake ? "brake" : "throttle"), 0, 1))));
        }
        // a console between two front seats, its gear lever
        List<BodyDesign.Seat> row = frontRow(c, driver);
        if (row.size() == 2) {
            double z = 0.5 * (row.get(0).at().z() + row.get(1).at().z());
            out.finish(Finishes.DASH_PLASTIC);
            out.box(new Vec3d(at.x() - 0.15, floor, z - 0.11), new Vec3d(dashRear + 0.02, floor + 0.28, z + 0.11), CONSOLE);
            out.finish(pen);
            add(out, part(DetailKit.Part.GEAR_LEVER, new Vec3d(at.x() + 0.3, floor + 0.28, z), Vec3d.Y, Vec3d.X, uniform(1)));
        }
        // 0.14: the centre stack, from the console up to the dash and forward to the firewall, over the transmission
        // tunnel (an engine in the bay with its gearbox behind it reaches back under the dash: the sample sedan's bell
        // housing showed between the pedals and the passenger's feet)
        out.finish(Finishes.DASH_PLASTIC);
        out.box(new Vec3d(dashRear - 0.25, floor, -0.16), new Vec3d(dashFront, y0, 0.16), CONSOLE);
        out.finish(pen);
        parcelShelf(c, driver);
        wipers(c);
    }

    /**
     * Behind a car's back seats, where it has a rear window: a panel from the floor up to the belt behind the last row
     * and the parcel shelf from it back to the foot of the rear window (what is behind and under, the boot and the
     * rear suspension's towers, out of sight).
     */
    private static void parcelShelf(Context c, BodyDesign.Seat driver) {
        BodyLayout.Opening rear = null;
        for (BodyLayout.Opening o : c.l().openings) { if (o.type() == BodyLayout.Type.REAR_WINDOW) { rear = o; } }
        if (rear == null) { return; }
        double last = Double.POSITIVE_INFINITY;
        for (BodyDesign.Seat s : c.l().seats) { if (s.module() == driver.module()) { last = Math.min(last, s.at().x()); } }
        double back = last - 0.45, belt = c.l().belt, end = rear.x0() - 0.02;
        if (!(back > end + 0.05)) { return; }
        PartMesh out = c.cabin();
        Finish pen = out.finish();
        out.finish(Finishes.DASH_PLASTIC);
        double floor = floor(c, back), hb = half(c, back, 0.5 * (floor + belt));
        out.box(new Vec3d(back - 0.03, floor, -hb), new Vec3d(back, belt, hb), CONSOLE);
        double hs = Math.min(half(c, back, belt - 0.02), half(c, end, belt - 0.02));
        out.box(new Vec3d(end, belt - 0.02, -hs), new Vec3d(back, belt, hs), CONSOLE);
        out.finish(pen);
    }

    /** The steering: the "steer" channel where it is driven, else the rudder keys. */
    private static double steer(Map<String, Double> in) { return Math.clamp(in.containsKey("steer") ? value(in, "steer") : value(in, "rudder"), -1, 1); }

    /**
     * A car's two wipers parked along the foot of the windscreen, pivoting about the glass's normal: the driver's
     * from a quarter of the way across, the other's from the middle, both sweeping up by 95 degrees.
     */
    private static void wipers(Context c) {
        BodySurface s = c.s();
        BodyLayout.Opening screen = null;
        for (BodyLayout.Opening o : c.l().openings) { if (o.type() == BodyLayout.Type.WINDSHIELD && o.side() == 0) { screen = o; } }
        if (screen == null || screen.x1() - screen.x0() < 0.2) { return; }
        double x = screen.x1() - 0.06, lo = screen.loAt(x), hi = screen.hiAt(x);
        double width = Math.abs(s.point(x, hi).z() - s.point(x, lo).z());
        for (double share : new double[]{-0.25, 0.05}) {
            double t = across(s, x, lo, hi, share * width);
            Vec3d n = s.normal(x, t), pivot = s.point(x, t).add(n.scale(0.004));
            Vec3d along = Vec3d.Z.subtract(n.scale(n.z())).normalized();
            double length = Math.min(0.55, 0.45 * width);
            // which way up the glass the blade turns: the sign raising its tip
            Orientation probe = Orientation.rotationVector(n.scale(0.1));
            double sign = probe.rotate(along).y() > along.y() ? 1 : -1;
            int piece = c.piece();
            c.cabin().piece(piece).levels(0, 1).cabin(false);
            add(c.cabin(), part(DetailKit.Part.WIPER, pivot, n, along, new Vec3d(length / 0.55, 1, 1)));
            c.cabin().piece(0).levels(0, 0).cabin(true);
            c.moving().add(new Moving(piece, in -> about(pivot, n, sign * Math.toRadians(95) * Math.clamp(value(in, "wipe"), 0, 1))));
        }
    }

    /** The angle on a section across the top where it is at z (bisection between lo and hi). */
    private static double across(BodySurface s, double x, double lo, double hi, double z) {
        double a = lo, b = hi;
        boolean rising = s.point(x, hi).z() > s.point(x, lo).z();
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (a + b);
            if ((s.point(x, mid).z() < z) == rising) { a = mid; } else { b = mid; }
        }
        return 0.5 * (a + b);
    }

    // ---------------------------------------------------------------- a cockpit

    private static void cockpit(Context c, BodyDesign.Seat driver) {
        BodySurface s = c.s();
        PartMesh out = c.cabin();
        List<BodyDesign.Seat> row = frontRow(c, driver);
        Vec3d at = driver.at();
        int module = Math.min(driver.module(), c.d().modules().size() - 1);
        double front = c.d().shape().moduleFrontX(module);
        double xp = Math.min(at.x() + 0.95, front - 0.05), floor = floor(c, xp), eye = BodyLayout.eye(driver).y();
        // the panel's top: under the windscreen's lower edge where there is one over it
        double top = eye - 0.12;
        for (BodyLayout.Opening o : c.l().openings) {
            if (o.type() == BodyLayout.Type.WINDSHIELD && xp >= o.x0() && xp <= o.x1()) {
                double t = o.side() > 0 ? o.hiAt(xp) : o.loAt(xp);
                top = Math.min(top, s.point(xp, Math.min(t, 2 * Math.PI - t)).y() - 0.03);
            }
        }
        double bottom = floor + 0.35;
        if (top - bottom < 0.15) { top = bottom + 0.15; }
        double hw = half(c, xp, 0.5 * (top + bottom));
        Finish pen = out.finish();
        out.finish(Finishes.DASH_PLASTIC);
        out.box(new Vec3d(xp - 0.05, bottom, -hw), new Vec3d(xp, top, hw), PANEL);
        out.box(new Vec3d(xp - 0.13, top, -hw), new Vec3d(xp + 0.02, top + 0.03, hw), GLARE);
        out.finish(pen);
        boolean rotor = c.d().modules().stream().anyMatch(BodyModuleDesign::mast);
        for (BodyDesign.Seat seat : row) {
            Vec3d sat = seat.at();
            // six gauges facing the pilot
            for (int g = 0; g < 6; g++) {
                double gz = sat.z() + (g % 3 - 1) * 0.1, gy = top - 0.09 - (g / 3) * 0.1;
                if (gy < bottom + 0.04) { continue; }
                add(out, part(DetailKit.Part.GAUGE, new Vec3d(xp - 0.05, gy, gz), Vec3d.X.scale(-1), Vec3d.Z, uniform(1)));
            }
            // rudder pedals under the panel, turning with the rudder about their middle
            Vec3d pedals = new Vec3d(Math.min(sat.x() + 0.85, xp - 0.12), floor(c, sat.x() + 0.85), sat.z());
            int pp = c.piece();
            out.piece(pp);
            add(out, part(DetailKit.Part.RUDDER_PEDALS, pedals, Vec3d.Y, Vec3d.X, uniform(1)));
            out.piece(0);
            Vec3d pivot = pedals.add(new Vec3d(0.05, 0.05, 0));
            c.moving().add(new Moving(pp, in -> about(pivot, Vec3d.Y, -0.25 * Math.clamp(value(in, "rudder"), -1, 1))));
            if (rotor || row.size() == 1) {
                // a stick (a helicopter's cyclic) between the knees, pivoting at the floor
                Vec3d foot = new Vec3d(sat.x() + 0.35, floor(c, sat.x() + 0.35), sat.z());
                int ps = c.piece();
                out.piece(ps);
                add(out, part(DetailKit.Part.STICK, foot, Vec3d.Y, Vec3d.X, uniform(1)));
                out.piece(0);
                c.moving().add(new Moving(ps, in -> {
                    Orientation r = Orientation.rotationVector(Vec3d.Z.scale(0.25 * Math.clamp(value(in, "pitch"), -1, 1)))
                            .multiply(Orientation.rotationVector(Vec3d.X.scale(0.25 * Math.clamp(value(in, "roll"), -1, 1))));
                    return new EngineModel.Placement(foot.subtract(r.rotate(foot)), r);
                }));
            } else {
                // a yoke on its column out of the panel: pulled back with the pitch, turned with the roll
                Vec3d hub = new Vec3d(xp - 0.3, eye - 0.27, sat.z());
                int py = c.piece();
                out.piece(py);
                add(out, part(DetailKit.Part.YOKE, hub, Vec3d.X.scale(-1), Vec3d.Z, uniform(1)));
                out.piece(0);
                c.moving().add(new Moving(py, in -> {
                    EngineModel.Placement turned = about(hub, Vec3d.X, 1.05 * Math.clamp(value(in, "roll"), -1, 1));
                    return new EngineModel.Placement(turned.offset().add(new Vec3d(-0.06 * Math.clamp(value(in, "pitch"), -1, 1), 0, 0)), turned.rotation());
                }));
            }
            if (rotor) {
                // the collective by the seat's left side, raised with the collective
                Vec3d pivot2 = new Vec3d(sat.x() - 0.12, sat.y() - 0.05, sat.z() - 0.3);
                int pc = c.piece();
                out.piece(pc);
                add(out, part(DetailKit.Part.COLLECTIVE, pivot2, Vec3d.Y, Vec3d.X, uniform(1)));
                out.piece(0);
                c.moving().add(new Moving(pc, in -> about(pivot2, Vec3d.Z, 0.35 * Math.clamp(value(in, "collective"), 0, 1))));
            }
        }
        if (!rotor && row.size() >= 2) {
            // a throttle quadrant on a pedestal between the pilots
            double z = 0.5 * (row.get(0).at().z() + row.get(row.size() - 1).at().z()), px = xp - 0.2;
            out.finish(Finishes.DASH_PLASTIC);
            out.box(new Vec3d(px - 0.08, floor(c, px), z - 0.07), new Vec3d(px + 0.08, bottom + 0.05, z + 0.07), CONSOLE);
            out.finish(pen);
            add(out, part(DetailKit.Part.THROTTLE_QUADRANT, new Vec3d(px, bottom + 0.05, z), Vec3d.Y, Vec3d.X, uniform(1)));
        }
    }

    // ---------------------------------------------------------------- an open boat

    /** The helm console ahead of the helmsman: its wheel (turning with the rudder), gauges and throttle. */
    private static void helm(Context c, BodyDesign.Seat driver) {
        BodySurface s = c.s();
        PartMesh out = c.shown();
        Vec3d at = driver.at();
        double sole = floorAt(c, driver.module(), at.x() + 0.55, 0);
        double x0 = at.x() + 0.42, x1 = at.x() + 0.72, y1 = sole + 0.82;
        Finish pen = out.finish();
        out.finish(Finishes.GFRP);
        out.hexahedron(new Vec3d[]{
                new Vec3d(x0, sole, at.z() - 0.28), new Vec3d(x0, sole, at.z() + 0.28), new Vec3d(x1, sole, at.z() + 0.28), new Vec3d(x1, sole, at.z() - 0.28),
                new Vec3d(x0 + 0.08, y1, at.z() - 0.26), new Vec3d(x0 + 0.08, y1, at.z() + 0.26), new Vec3d(x1, y1 + 0.06, at.z() + 0.26), new Vec3d(x1, y1 + 0.06, at.z() - 0.26)},
                HELM_CONSOLE);
        out.finish(pen);
        int levels = out.tags();
        out.levels(0, 1);
        for (int g = -1; g <= 1; g += 2) { add(out, part(DetailKit.Part.GAUGE, new Vec3d(x0 + 0.13, y1 - 0.02, at.z() + 0.09 * g), new Vec3d(-0.6, 0.8, 0), Vec3d.Z, uniform(1))); }
        add(out, part(DetailKit.Part.GEAR_LEVER, new Vec3d(x0 + 0.22, y1 + 0.03, at.z() + 0.2), Vec3d.Y, Vec3d.X, uniform(0.8)));
        Vec3d toward = new Vec3d(-0.8, 0.6, 0).normalized(), wheel = new Vec3d(x0 + 0.02, sole + 0.72, at.z() - 0.05);
        int piece = c.piece();
        out.piece(piece);
        add(out, part(DetailKit.Part.HELM_WHEEL, wheel, toward, Vec3d.Y, uniform(1)));
        out.piece(0);
        out.pens(pen, levels);
        c.moving().add(new Moving(piece, in -> about(wheel, toward, -Math.PI * Math.clamp(value(in, "rudder"), -1, 1))));
    }
}
