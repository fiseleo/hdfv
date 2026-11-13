package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.mesh.Finish;
import dev.mcmod.aerocore.api.mesh.Mesh;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.SurfaceCut;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.look.BodyLayout;
import dev.mcmod.hdfv.look.BodyLayout.Opening;
import dev.mcmod.hdfv.look.BodySurface;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.look.LampRules;
import dev.mcmod.hdfv.look.Lettering;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;
import dev.mcmod.hdfv.part.body.BodyModuleDesign.Family;
import dev.mcmod.hdfv.part.body.BodyModuleDesign.Kind;
import dev.mcmod.hdfv.part.body.BodySettingsDesign.Stripes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

/**
 * A body drawn from its design, in its part frame (x from the rear end forward, y up from the reference line), its
 * skin lofted through the stations along BodySurface's rays so the modules' tapers, a step in the bottom and the joins
 * come out as they are computed. The skin is shaded smooth, keeping a sharp edge where faces meet at more than 38
 * degrees (chines, steps, a transom). Over it, laid on the surface, in the design's paint (each module's main colour,
 * its own or the body's, and the body's accent and lower colours):
 * <ul>
 * <li>aircraft: the main colour over a belly in the lower colour, a cheat line in the accent with a gold pin stripe
 *     under the windows (by default white, grey and blue); panel seams at the module joins, red beacons and a whip
 *     antenna on a cabin;</li>
 * <li>boats: topsides in the main colour, a boot stripe in the accent on the waterline (38 % of the boat modules' depth
 *     up), the lower colour (antifouling) below, a light grey deck, a rub rail along the sheer, cleats;</li>
 * <li>cars: the main colour over sills in the lower colour, dark bumpers and underside, the cabin's roof in the accent
 *     (two-tone when it differs), a chrome strip along the belt, mirrors, head and tail lights and a grille on blunt ends;</li>
 * <li>an open module: no skin above its open edge, a gunwale capping it, inner walls, a floor (sole) 0.45 m lower,
 *     bulkheads against the closed modules either side, its seats in view; a boat's has a windscreen behind a closed bow.</li>
 * </ul>
 * 0.12: the openings are real (BodyLayout places them, AeroCore's SurfaceCut cuts them from the skin). Nearest (level
 * 0) the skin has its windows, doors and wheel arches cut out; inside it a liner 4 to 5 cm in (headlining, trim,
 * carpet) cut with the same holes, so every window and door has its reveal; glass 12 mm in from the skin (see-through,
 * one face, drawn unculled) with a rubber gasket round it; each door a panel of its own, 4 mm clear of its opening, with its inner
 * panel, its edge and its window, a moving piece (piece 1 + its index among the doors) hinged at its front edge; a
 * wheel house behind each arch; bulkheads closing the lined cabin; the seats of closed modules. All of the inside is
 * tagged cabin (kept nearest though shut in, left out farther), the glass clear. Farther (levels 1-3) the skin is
 * whole but for open tops, the windows painted on dark and the doors as seams, as before 0.12.
 * <p>
 * 0.12, the looks the body settings give (none change what the body computes): the paint's finish on every painted
 * face; the glass's tint and how opaque it is (the far pane a dark shade of the tint); the stripes, rows of the skin's
 * cells in the stripe colour (so a door's panel carries its share), automatic being the aircraft's cheat line and the
 * boat's boot stripe as before; the registration in lettering (Lettering, decals from the glyph atlas, nearest two
 * levels only): an aircraft's on each side of the rear fuselage aft of its openings, 3 to 12 inches high (14 CFR
 * 45.29), a boat's name on each side of the bow and across the transom (at least 3 inches, 33 CFR 174.15), a car's on
 * number plates front and rear (the kit's frame, 520 x 110 mm); and the radius of a car's wheel arches.
 * <p>
 * The module being edited is painted in the highlight colour. Cut away (the design screen), the near half is left out
 * and the inside shows: fuel tanks (blue) and holds (brown) of their volumes; the design screen also marks the
 * mount points.
 */
public final class BodyModel {
    private BodyModel() {}

    // fallbacks for a colour that is not valid, and the colours that are not paint
    private static final int AIR_BELLY = 0xffb3bac2, AIR_STRIPE = 0xff1f3b73, AIR_PIN = 0xffc8962a, SEAM = 0xffaab1b9,
            DOOR_SEAM = 0xff5b626b, BEACON = 0xffd81e1e, ANTENNA = 0xff202326;
    private static final int DECK = 0xffd3d0c6, RUB_RAIL = 0xff34383d,
            BOAT_INSIDE = 0xffe9e6dd, BOAT_SOLE = 0xffb9b2a2, CLEAT = 0xffc9cdd2;
    private static final int BUMPER = 0xff2a2d31, UNDER = 0xff151618, CHROME = 0xffcfd3d8,
            HEADLIGHT = 0xffeef3f7, LAMP_RIM = 0xff8d96a0, TAIL_LIGHT = 0xffb3161c, GRILLE = 0xff121314, ARCH = 0xff0e0e0f;
    private static final int FRAME = 0xff24282e, AIR_SEAT = 0xff3b4c6b, CAR_SEAT = 0xff2a2c30, BOAT_SEAT = 0xffeeeae0,
            DARK_INSIDE = 0xff34373c, DARK_SOLE = 0xff2b2d31, FUEL = 0xff3b6fb3, CARGO = 0xff8a6a43, MARK = 0xffffd23a, MARK_ENGINE = 0xffff7a2e,
            MARK_AXLE = 0xff35c8e0;
    /** 0.12: glass (a light tint, about a third opaque: the inside shows), and the dark pane painted on far away. */
    private static final int GLASS = 0x5a283846, FAR_GLASS = 0xff1c232b;
    /**
     * 0.22, beautified (reflective glass, AeroCore 0.39): a lighter tint shown at 0.7 of the design's opacity (the sky's
     * reflection now lies on top, so the glass no longer has to be dark to read as glass), and a far pane as light.
     */
    private static final int GLASS_BEAUTIFIED = 0x5a46586a, FAR_GLASS_BEAUTIFIED = 0xff34404a;
    private static final double GLASS_SHOWN = 0.7;
    /** 0.12: the inside: a car's headlining, its trim and carpet; an aircraft's cabin lining and carpet; a boat cabin's floor; door jambs. */
    private static final int HEADLINING = 0xffb7b3aa, TRIM = 0xff2e3034, CARPET = 0xff2a2b2e, AIR_LINING = 0xffd9d7d1, AIR_CARPET = 0xff3e4249,
            TEAK_SOLE = 0xff9b6b3d, JAMB = 0xff3a3d42, WHEEL_HOUSE = 0xff111213;
    /** 0.12: a number plate's face and its letters; lettering dark on light paint, white on dark. */
    private static final int PLATE = 0xfff1f1ec, PLATE_TEXT = 0xff15161a, TEXT_DARK = 0xff17181b, TEXT_LIGHT = 0xfff4f4f2;

    private static final int RING = BodySurface.RING;
    /** Faces meeting at more than this keep a sharp edge; gentler ones are shaded smooth. */
    private static final double CREASE = Math.cos(Math.toRadians(38));
    /** How far details stand off the skin (m), enough not to flicker at a distance. */
    private static final double LAYER1 = 0.006, LAYER2 = 0.011;
    /** 0.12: a door's gap round its panel, the glass's set back from the skin, a wheel house's depth (m). */
    private static final double GAP_M = 0.004, GLASS_INSET_M = 0.012, WHEEL_HOUSE_M = 0.30;

    /** The finish of each colour the model uses that is not paint (paint, any colour the design gives, is PAINT). */
    private static final Map<Integer, Finish> NOT_PAINT = new HashMap<>();

    static {
        put(Finishes.RUBBER, DOOR_SEAM, RUB_RAIL, FRAME);
        put(Finishes.LAMP_RED, BEACON, TAIL_LIGHT);
        put(Finishes.LAMP_CLEAR, HEADLIGHT);
        put(Finishes.PLASTIC, ANTENNA, BUMPER, UNDER, GRILLE, ARCH, DARK_INSIDE, WHEEL_HOUSE, AIR_LINING);
        put(Finishes.NONSKID, DECK, BOAT_SOLE);
        put(Finishes.GFRP, BOAT_INSIDE);
        put(Finishes.STAINLESS, CLEAT);
        put(Finishes.CHROME, CHROME, LAMP_RIM);
        put(Finishes.GLASS, GLASS, FAR_GLASS, GLASS_BEAUTIFIED, FAR_GLASS_BEAUTIFIED);
        put(Finishes.FABRIC, AIR_SEAT, CAR_SEAT, HEADLINING);
        put(Finishes.LEATHER, BOAT_SEAT);
        put(Finishes.DASH_PLASTIC, DARK_SOLE, TRIM, JAMB);
        put(Finishes.CARPET, CARPET, AIR_CARPET);
        put(Finishes.TEAK, TEAK_SOLE);
    }

    private static void put(Finish f, int... colours) { for (int c : colours) { NOT_PAINT.put(c, f); } }

    /**
     * What every part of the drawing shares: each module's open edge (absolute height; infinite when closed) and main
     * colour, the body's accent and lower colours, the boats' waterline, the lowest bottom, the openings.
     */
    private record Context(BodyDesign d, BodyShape shape, BodySurface s, BodyLayout layout, int highlight, double[] sill, int[] main, int accent, int lower,
                           double waterline, double bottom, Finish paint, int glass, int farGlass, Stripes stripes, int stripe,
                           dev.mcmod.hdfv.look.FrontLayout.Pieces front) {
        BodyModuleDesign module(int i) { return d.modules().get(i); }
        Family family(int i) { return module(i).kind().family; }
        int modules() { return d.modules().size(); }
    }

    /** The body, highlighting the module of a page ("module3"), cut away or whole, its mount points marked or not. */
    public static EngineModel build(BodyDesign d, String page, boolean cutaway, boolean marks) {
        BodyShape shape = d.shape();
        BodySurface s = new BodySurface(shape);
        BodyLayout layout = new BodyLayout(d, shape, s);
        Context c = context(d, shape, s, layout, BodyDesign.index(page));
        PartMesh hull = new PartMesh(), shown = new PartMesh(), inside = new PartMesh(), cabin = new PartMesh();
        Cells cells = cells(c);
        cap(c, hull, 0, -1);
        cap(c, hull, s.xs.length - 1, 1);
        boolean openings = layout.openings.stream().anyMatch(o -> o.type() != BodyLayout.Type.OPEN);
        if (openings) {
            hull.levels(1, 3);
            far(c, cells, hull);
            hull.levels(0, 0);
            near(c, cells, hull);
            hull.levels(0, 3);
        } else {
            SurfaceCut.cut(cells.grid(), holes(layout.openings, o -> true), hull);
        }
        for (int i = 0; i < c.modules(); i++) {
            if (c.family(i) == Family.AIRCRAFT) { aircraft(c, i, hull); }
            if (c.module(i).open()) { open(c, i, hull); }
        }
        boatHull(c, hull);
        car(c, hull);
        lamps(c, hull);   // 0.16: every lamp (LampLayout)
        kit(c, hull);     // 0.16: the aero kit (KitLayout)
        lettering(c, hull);
        // 0.13: the stickers (core's Decals: over a door, its share on the door's piece at the nearest level)
        dev.mcmod.hdfv.look.Decals.body(hull, s, layout, d.settings().stickers(), GAP_M);
        // the inside from the interior kit (an open module's seats from it nearer, as boxes farther); its moving
        // parts are pieces after the doors
        cabin.cabin(true).levels(0, 0);
        shown.levels(0, 1);
        int doorCount = (int) layout.openings.stream().filter(o -> o.type() == BodyLayout.Type.DOOR).count();
        List<InteriorKit.Moving> moving = InteriorKit.build(d, layout, wall(c), doorCount + 1, cabin, shown);
        shown.levels(2, 3);
        for (BodyDesign.Seat seat : d.seats(shape)) {
            int i = Math.min(seat.module(), c.modules() - 1);
            if (c.module(i).open()) { seat(shown, seat.at(), c.family(i)); }
        }
        shown.levels(0, 3);
        for (int i = 0; i < c.modules(); i++) {
            BodyModuleDesign m = c.module(i);
            double front = shape.moduleFrontX(i), rear = shape.moduleRearX(i), x = 0.5 * (front + rear), len = 0.8 * (front - rear);
            double bottom = shape.bottomAt(x), h = shape.topAt(x) - bottom, w = 0.7 * shape.widthAt(x);
            if (m.fuelM3() > 0) { tank(inside, m.fuelM3(), x - 0.25 * len, bottom + 0.05 * h, 0.5 * len, 0.35 * h, w, FUEL); }
            // 0.25: the hold where the body's Part.Hold is (BodyDesign.cargoBox: its cargo rides at its middle)
            Vec3d[] hold = d.cargoBox(shape, i);
            if (hold != null) { inside.box(hold[0], hold[1], CARGO); }
        }
        for (PartMesh m : new PartMesh[]{hull, shown, inside, cabin}) { finish(m, c); }
        EngineModel model = new EngineModel(new PartMesh());
        if (cutaway) {
            model.section(hull, Vec3d.ZERO, Vec3d.Z);
            model.fixed.add(inside, Orientation.IDENTITY, Vec3d.ZERO);
        } else {
            model.fixed.add(hull, Orientation.IDENTITY, Vec3d.ZERO);
        }
        model.fixed.add(shown, Orientation.IDENTITY, Vec3d.ZERO);
        model.fixed.add(cabin, Orientation.IDENTITY, Vec3d.ZERO);
        // how the pieces move: the doors about their hinges (70 degrees open), the inside's controls and wipers
        for (Opening o : layout.openings) {
            if (o.type() != BodyLayout.Type.DOOR || o.hinge() == null) { continue; }
            BodyLayout.Hinge h = o.hinge();
            Vec3d axis = h.b().subtract(h.a());
            if (axis.lengthSquared() < 1e-12) { continue; }
            model.posed.put(doorPiece(layout, o.id()), in -> InteriorKit.about(h.a(), axis, h.swing() * Math.toRadians(70) * Math.clamp(in.getOrDefault("doors", 0.0), 0, 1)));
        }
        for (InteriorKit.Moving m : moving) { model.posed.put(m.piece(), m.pose()); }
        if (marks) {
            // small diamonds at the mount points: engines orange, axles cyan, the rest yellow
            for (Map.Entry<String, Mount> e : d.mountPoints(shape).entrySet()) {
                Vec3d m = e.getValue().position();
                String name = e.getKey();
                int color = name.endsWith("_engine") || name.equals("engine_bay") || name.equals("engine") ? MARK_ENGINE : name.endsWith("_axle") ? MARK_AXLE : MARK;
                model.fixed.add(diamond(0.035, color), Orientation.IDENTITY, m);
            }
            // a car's wheels in its arches, as a family car's (the axles themselves are designed apart)
            if (c.family(0) == Family.CAR || c.family(c.modules() - 1) == Family.CAR) { ghostWheels(c, model.fixed); }
        }
        return model;
    }

    /** The piece a door is drawn as (1 + its index among the layout's doors), by opening id; -1 for what is not a door. */
    public static int doorPiece(BodyLayout layout, int openingId) {
        int k = 0;
        for (Opening o : layout.openings) {
            if (o.type() != BodyLayout.Type.DOOR) { continue; }
            k++;
            if (o.id() == openingId) { return k; }
        }
        return -1;
    }

    /** Quads without a finish given theirs by colour: the glass and the fixed colours their own, paint (any other) the body's paint finish. */
    private static void finish(PartMesh m, Context c) {
        List<MeshBuilder.Quad> quads = m.quads();
        for (int i = 0; i < quads.size(); i++) {
            MeshBuilder.Quad q = quads.get(i);
            if (q.finish() != null) { continue; }
            int colour = q.color();
            quads.set(i, q.withFinish(colour == c.glass() || colour == c.farGlass() ? Finishes.GLASS : NOT_PAINT.getOrDefault(colour, c.paint())));
        }
    }

    private static Context context(BodyDesign d, BodyShape shape, BodySurface s, BodyLayout layout, int highlight) {
        double[] sill = new double[d.modules().size()];
        for (int i = 0; i < sill.length; i++) { sill[i] = d.modules().get(i).open() ? shape.modules().get(i).sillM() : Double.POSITIVE_INFINITY; }
        double bottom = Double.POSITIVE_INFINITY;
        for (BodyShape.Station st : s.stations) { bottom = Math.min(bottom, st.bottomM()); }
        int[] main = new int[d.modules().size()];
        for (int i = 0; i < main.length; i++) {
            String own = d.modules().get(i).paintColor();
            main[i] = paint(own.isEmpty() ? d.settings().paintColor() : own, 0xff808080);
        }
        var settings = d.settings();
        int accent = paint(settings.accentColor(), AIR_STRIPE);
        // the glass: its tint (blank: the usual) at its opacity; far away a dark pane of the tint
        boolean pretty = Looks.beautify();
        int plain = pretty ? GLASS_BEAUTIFIED : GLASS, plainFar = pretty ? FAR_GLASS_BEAUTIFIED : FAR_GLASS;
        int tint = settings.glassColor().isEmpty() ? plain & 0xffffff : paint(settings.glassColor(), plain) & 0xffffff;
        int glass = (int) Math.clamp(Math.round(settings.glassOpacity() * (pretty ? GLASS_SHOWN : 1) * 256), 13, 255) << 24 | tint;
        int far = settings.glassColor().isEmpty() ? plainFar : mix(plainFar, 0xff000000 | tint, 0.35);
        return new Context(d, shape, s, layout, highlight, sill, main, accent, paint(settings.lowerColor(), AIR_BELLY), layout.waterline, bottom,
                settings.paintFinish().finish(), glass, far, settings.stripes(), settings.stripeColor().isEmpty() ? accent : paint(settings.stripeColor(), accent),
                dev.mcmod.hdfv.look.FrontLayout.of(d, s));
    }

    /** Two opaque colours mixed, a share of the second. */
    private static int mix(int a, int b, double share) {
        int out = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            double ca = (a >> shift) & 255, cb = (b >> shift) & 255;
            out |= (int) Math.round(ca + (cb - ca) * share) << shift;
        }
        return out;
    }

    /** A stored colour (#RRGGBB) as ARGB, or a fallback for one that is not valid. */
    private static int paint(String value, int fallback) {
        return dev.mcmod.hdfv.design.RecordEditor.isColor(value, false) ? dev.mcmod.hdfv.design.RecordEditor.argb(value) : fallback;
    }

    // ---------------------------------------------------------------- skin

    /**
     * The skin's cells between the stations (strip j between stations j and j + 1, ray k): corners as SurfaceCut wants
     * them, normals shaded smooth within the crease angle, the paint and its finish; and every station point's normal
     * over all the faces round it (to set a liner or the glass in along).
     */
    private record Cells(double[] u, double[] v, Vec3d[][][] corner, Vec3d[][][] normal, int[][] colour, Finish[][] finish, int[] module, Vec3d[][] vertex) {
        SurfaceCut.Grid grid() { return new SurfaceCut.Grid(u, v, true, corner, normal, colour, finish); }
    }

    private static Cells cells(Context c) {
        BodySurface s = c.s();
        int strips = Math.max(s.xs.length - 1, 0);
        double[] v = new double[RING + 1];
        for (int k = 0; k <= RING; k++) { v[k] = BodySurface.angle(k); }
        Vec3d[][][] corner = new Vec3d[strips][RING][], normal = new Vec3d[strips][RING][];
        Vec3d[][] face = new Vec3d[strips][RING];
        int[][] colour = new int[strips][RING];
        Finish[][] finish = new Finish[strips][RING];
        int[] module = new int[strips];
        for (int j = 0; j < strips; j++) {
            int i = j + 1;
            module[j] = Math.min(s.xs[i] - s.xs[i - 1] > 2e-3 ? s.stations.get(i).module() : s.stations.get(i - 1).module(), c.modules() - 1);
            double axisY = 0.5 * (s.stations.get(i).centroidYM() + s.stations.get(i - 1).centroidYM());
            for (int k = 0; k < RING; k++) {
                int k1 = (k + 1) % RING;
                Vec3d[] q = {s.rings[i - 1][k], s.rings[i - 1][k1], s.rings[i][k1], s.rings[i][k]};
                Vec3d n = q[2].subtract(q[0]).cross(q[3].subtract(q[1]));
                if (n.lengthSquared() < 1e-14) { continue; }
                Vec3d mid = q[0].add(q[1]).add(q[2]).add(q[3]).scale(0.25);
                if (n.dot(new Vec3d(0, mid.y() - axisY, mid.z())) < 0) { n = n.scale(-1); }
                corner[j][k] = q;
                face[j][k] = n.normalized();
            }
        }
        for (int j = 0; j < strips; j++) {
            for (int k = 0; k < RING; k++) {
                Vec3d[] q = corner[j][k];
                if (q == null) { continue; }
                Vec3d n = face[j][k];
                Vec3d[] ns = new Vec3d[4];
                for (int p = 0; p < 4; p++) { ns[p] = smooth(corner, face, strips, j, k, q[p], n); }
                normal[j][k] = ns;
                Vec3d mid = q[0].add(q[1]).add(q[2]).add(q[3]).scale(0.25);
                colour[j][k] = base(c, module[j], mid, n, k);
                finish[j][k] = skinFinish(c, module[j], mid, colour[j][k]);
            }
        }
        // each station point's normal: the faces round it, every one (a liner set in along it stays closed at creases)
        Vec3d[][] vertex = new Vec3d[s.xs.length][RING];
        for (int i = 0; i < s.xs.length; i++) {
            for (int k = 0; k < RING; k++) {
                Vec3d sum = Vec3d.ZERO;
                for (int j = i - 1; j <= i; j++) {
                    if (j < 0 || j >= strips) { continue; }
                    for (int kk = k - 1; kk <= k; kk++) {
                        Vec3d f = face[j][Math.floorMod(kk, RING)];
                        if (f != null) { sum = sum.add(f); }
                    }
                }
                vertex[i][k] = sum.lengthSquared() > 1e-18 ? sum.normalized() : Vec3d.Y;
            }
        }
        return new Cells(s.xs.clone(), v, corner, normal, colour, finish, module, vertex);
    }

    /** A corner's normal: the faces round it (its own among them) that share the corner and meet it within the crease angle. */
    private static Vec3d smooth(Vec3d[][][] corner, Vec3d[][] normal, int strips, int j, int k, Vec3d at, Vec3d own) {
        Vec3d sum = Vec3d.ZERO;
        for (int dj = -1; dj <= 1; dj++) {
            int jj = j + dj;
            if (jj < 0 || jj >= strips) { continue; }
            for (int dk = -1; dk <= 1; dk++) {
                int kk = Math.floorMod(k + dk, RING);
                Vec3d[] q = corner[jj][kk];
                if (q == null || normal[jj][kk].dot(own) < CREASE) { continue; }
                for (Vec3d v : q) {
                    if (v.subtract(at).lengthSquared() < 1e-12) { sum = sum.add(normal[jj][kk]); break; }
                }
            }
        }
        return sum.lengthSquared() > 1e-18 ? sum.normalized() : own;
    }

    /** The skin's finish at a face: a boat's deck non-skid and its bottom antifouling, a car's bumpers and underside plastic, the rest paint. */
    private static Finish skinFinish(Context c, int module, Vec3d mid, int colour) {
        if (module == c.highlight()) { return Finishes.PAINT; }
        return switch (c.family(module)) {
            case BOAT -> colour == DECK ? Finishes.NONSKID : mid.y() < c.waterline() ? Finishes.ANTIFOULING : c.paint();
            case CAR -> colour == BUMPER || colour == UNDER ? Finishes.PLASTIC : c.paint();
            case AIRCRAFT -> c.paint();
        };
    }

    /** The holes of some openings, all taken out. */
    private static List<SurfaceCut.Hole> holes(List<Opening> openings, java.util.function.Predicate<Opening> which) {
        List<SurfaceCut.Hole> out = new ArrayList<>();
        for (Opening o : openings) { if (which.test(o)) { out.add(hole(o, SurfaceCut.Kind.DISCARD)); } }
        return out;
    }

    private static SurfaceCut.Hole hole(Opening o, SurfaceCut.Kind kind) { return new SurfaceCut.Hole(o.id(), kind, o.parts(), o.outline()); }

    /**
     * Farther away (levels 1-3): the skin whole but for open tops, the windows painted on (a dark frame, a dark pane)
     * and the doors as seams with their handles; a car's wheel arches dark.
     */
    private static void far(Context c, Cells cells, PartMesh out) {
        BodySurface s = c.s();
        SurfaceCut.cut(cells.grid(), holes(c.layout().openings, o -> o.type() == BodyLayout.Type.OPEN), out);
        for (Opening o : c.layout().openings) {
            switch (o.type()) {
                case DOOR -> {
                    seams(out, s, o, 0.012, DOOR_SEAM, LAYER1);
                    if (c.family(Math.min(o.module(), c.modules() - 1)) != Family.CAR) {
                        double x = o.x0() + 0.13;
                        handle(out, s, o, s.point(x, 0.5 * (o.loAt(x) + o.hiAt(x))), LAYER2);
                    }
                }
                case ARCH -> painted(out, s, o, 0, ARCH, LAYER1);
                case OPEN -> { }
                default -> {
                    painted(out, s, o, o.type() == BodyLayout.Type.WINDSHIELD || o.type() == BodyLayout.Type.REAR_WINDOW ? 0.025 : 0.02, FRAME, LAYER1);
                    painted(out, s, o, 0, c.farGlass(), LAYER2);
                }
            }
        }
    }

    /**
     * Nearest (level 0): the skin with every opening cut out; the doors' panels, the glass and the liner cut to the same
     * outlines, the reveals between them, the wheel houses behind the arches, bulkheads closing the lined cabin.
     */
    private static void near(Context c, Cells cells, PartMesh out) {
        BodyLayout l = c.layout();
        BodySurface s = c.s();
        int pens = out.tags();
        Finish pen = out.finish();
        SurfaceCut.Grid skin = cells.grid();
        // the skin less every opening (a door's whole opening; its panel, a gap smaller, is cut apart below)
        List<SurfaceCut.Hole> body = holes(l.openings, o -> o.door() < 0);
        SurfaceCut.Result outer = SurfaceCut.cut(skin, body, out);
        // the doors' panels: each door's opening less the gap, less the windows in it (innermost first)
        List<SurfaceCut.Hole> panels = new ArrayList<>();
        for (Opening door : l.openings) {
            if (door.type() != BodyLayout.Type.DOOR) { continue; }
            for (Opening w : l.openings) { if (w.door() == door.id()) { panels.add(hole(w, SurfaceCut.Kind.DISCARD)); } }
            Opening panel = door.inset(GAP_M, s);
            if (panel != null) { panels.add(hole(panel, SurfaceCut.Kind.PLUG)); }
        }
        SurfaceCut.Result doors = SurfaceCut.cut(skin, panels, new Mesh());
        // the glass, set in from the skin along the station points' normals
        SurfaceCut.Result glass = SurfaceCut.cut(offset(cells, GLASS_INSET_M, false, j -> true, (j, n, mid) -> c.glass(), (j, n, mid) -> Finishes.GLASS),
                l.openings.stream().filter(o -> o.type().glazed()).map(o -> hole(o, SurfaceCut.Kind.PLUG)).toList(), new Mesh());
        // the liner, set in by the wall, facing in, over the lined modules
        double wall = wall(c);
        SurfaceCut.Grid linerGrid = offset(cells, wall, true, j -> l.lined[cells.module()[j]], (j, n, mid) -> lining(c, cells.module()[j], n, mid),
                (j, n, mid) -> NOT_PAINT.get(lining(c, cells.module()[j], n, mid)));
        out.cabin(true);
        SurfaceCut.Result liner = SurfaceCut.cut(linerGrid, body, out);
        SurfaceCut.Result inner = SurfaceCut.cut(linerGrid, panels, new Mesh());
        out.cabin(false);
        // the doors: panel, inner panel, edge, each on its piece; the opening's jamb on the body
        for (Opening door : l.openings) {
            if (door.type() != BodyLayout.Type.DOOR) { continue; }
            Vec3d centre = door.centre(s), into = centre.subtract(s.normal(door.middle()[0], door.middle()[1]).scale(0.5 * wall));
            out.piece(doorPiece(l, door.id()));
            Mesh panel = doors.plugs().get(door.id()), back = inner.plugs().get(door.id());
            if (panel != null) { addAs(out, panel, out.tags()); }
            out.cabin(true);
            if (back != null) { addAs(out, back, out.tags()); }
            reveal(out, doors.loops().get(door.id()), inner.loops().get(door.id()), c.main()[Math.min(door.module(), c.modules() - 1)], c.paint(),
                    q -> q.subtract(into));
            out.piece(0);
            reveal(out, outer.loops().get(door.id()), liner.loops().get(door.id()), JAMB, Finishes.DASH_PLASTIC, q -> into.subtract(q));
            out.cabin(false);
        }
        // the glass (both faces) and its reveals: a rubber gasket from the skin to the glass, trim from the glass to the liner
        for (Opening o : l.openings) {
            if (!o.type().glazed()) { continue; }
            out.piece(o.door() >= 0 ? doorPiece(l, o.door()) : 0);
            Mesh pane = glass.plugs().get(o.id());
            if (pane != null) {
                int tags = out.clear(true).tags();
                // one face: glass is drawn without culling (both faces doubled its opacity and the late queue's vertices)
                for (MeshBuilder.Quad q : pane.quads()) { out.quads().add(q.withTags(tags)); }
                out.clear(false);
            }
            SurfaceCut.Loop skinLoop = o.door() >= 0 ? doors.loops().get(o.id()) : outer.loops().get(o.id());
            SurfaceCut.Loop linerLoop = o.door() >= 0 ? inner.loops().get(o.id()) : liner.loops().get(o.id());
            Vec3d into = o.centre(s).subtract(s.normal(o.middle()[0], o.middle()[1]).scale(0.5 * wall));
            int trim = trim(c, o.module());
            out.cabin(true);
            reveal(out, skinLoop, glass.loops().get(o.id()), FRAME, Finishes.RUBBER, q -> into.subtract(q));
            reveal(out, glass.loops().get(o.id()), linerLoop, trim, NOT_PAINT.get(trim), q -> into.subtract(q));
            out.cabin(false).piece(0);
        }
        // the wheel houses behind the arches
        for (Opening o : l.openings) {
            if (o.type() == BodyLayout.Type.ARCH) { wheelHouse(o, outer.loops().get(o.id()), out); }
        }
        // bulkheads where the lined cabin ends
        out.cabin(true);
        bulkheads(c, cells, wall, out);
        out.pens(pen, pens);
    }

    /** The wall's thickness (m): a car's door and an aircraft's frames 5 cm, a boat's 4 cm. */
    private static double wall(Context c) {
        for (int i = 0; i < c.modules(); i++) { if (c.layout().lined[i] && c.family(i) == Family.BOAT) { return 0.04; } }
        return 0.05;
    }

    /** The liner's colour at a cell (facing in along n): a floor carpeted (a boat's teak), a car's headlining over its belt and trim under it, an aircraft's lining. */
    private static int lining(Context c, int module, Vec3d inward, Vec3d mid) {
        Family f = c.family(module);
        if (inward.y() > 0.6) { return f == Family.BOAT ? TEAK_SOLE : f == Family.CAR ? CARPET : AIR_CARPET; }
        return switch (f) {
            case CAR -> Double.isNaN(c.layout().belt) || mid.y() > c.layout().belt ? HEADLINING : TRIM;
            case AIRCRAFT -> AIR_LINING;
            case BOAT -> BOAT_INSIDE;
        };
    }

    /** The trim round a window inside: a car's dark, an aircraft's lining, a boat's cabin white. */
    private static int trim(Context c, int module) {
        return switch (c.family(Math.min(module, c.modules() - 1))) { case CAR -> TRIM; case AIRCRAFT -> AIR_LINING; case BOAT -> BOAT_INSIDE; };
    }

    private interface CellColour { int at(int strip, Vec3d facing, Vec3d mid); }

    private interface CellFinish { Finish at(int strip, Vec3d facing, Vec3d mid); }

    /**
     * The skin's grid set in by a depth along the station points' normals, over the strips a test keeps: facing out
     * (glass) or in (a liner), coloured and finished by cell.
     */
    private static SurfaceCut.Grid offset(Cells cells, double depth, boolean inward, java.util.function.IntPredicate strips, CellColour colour, CellFinish finish) {
        int n = cells.corner().length;
        Vec3d[][][] corner = new Vec3d[n][RING][], normal = new Vec3d[n][RING][];
        int[][] colours = new int[n][RING];
        Finish[][] finishes = new Finish[n][RING];
        for (int j = 0; j < n; j++) {
            if (!strips.test(j)) { continue; }
            for (int k = 0; k < RING; k++) {
                if (cells.corner()[j][k] == null) { continue; }
                int k1 = (k + 1) % RING;
                Vec3d[] vn = {cells.vertex()[j][k], cells.vertex()[j][k1], cells.vertex()[j + 1][k1], cells.vertex()[j + 1][k]};
                Vec3d[] q = new Vec3d[4], ns = new Vec3d[4];
                for (int p = 0; p < 4; p++) {
                    q[p] = cells.corner()[j][k][p].subtract(vn[p].scale(depth));
                    ns[p] = inward ? vn[p].scale(-1) : cells.normal()[j][k][p];
                }
                corner[j][k] = q;
                normal[j][k] = ns;
                Vec3d mid = q[0].add(q[1]).add(q[2]).add(q[3]).scale(0.25), facing = ns[0].add(ns[1]).add(ns[2]).add(ns[3]);
                facing = facing.lengthSquared() > 1e-18 ? facing.normalized() : Vec3d.Y;
                colours[j][k] = colour.at(j, facing, mid);
                finishes[j][k] = finish.at(j, facing, mid);
            }
        }
        return new SurfaceCut.Grid(cells.u(), cells.v(), true, corner, normal, colours, finishes);
    }

    /** A mesh's quads added with other tags (their finishes kept). */
    private static void addAs(PartMesh out, MeshBuilder<?> mesh, int tags) {
        for (MeshBuilder.Quad q : mesh.quads()) { out.quads().add(q.withTags(tags)); }
    }

    /** A quad seen from its back (the other winding, normals turned). */
    private static MeshBuilder.Quad back(MeshBuilder.Quad q) {
        Vec3d[] n = q.normals();
        Vec3d[] ns = n == null ? null : new Vec3d[]{n[0].scale(-1), n[3].scale(-1), n[2].scale(-1), n[1].scale(-1)};
        int[] cs = q.colors() == null ? null : new int[]{q.colors()[0], q.colors()[3], q.colors()[2], q.colors()[1]};
        float[] uv = q.uv();
        float[] us = uv == null ? null : new float[]{uv[0], uv[1], uv[6], uv[7], uv[4], uv[5], uv[2], uv[3]};
        return new MeshBuilder.Quad(q.a(), q.d(), q.c(), q.b(), q.color(), q.normal().scale(-1), ns, cs, us, q.finish(), q.tags());
    }

    /** The band between two outlines of one opening (as many points: cut on grids alike), facing as given; nothing where they differ. */
    private static void reveal(PartMesh out, SurfaceCut.Loop a, SurfaceCut.Loop b, int colour, Finish finish, Function<Vec3d, Vec3d> facing) {
        if (a == null || b == null || a.points().size() < 3 || a.points().size() != b.points().size()) { return; }
        Finish pen = out.finish();
        out.finish(finish);
        SurfaceCut.strip(out, a, b, colour, facing);
        out.finish(pen);
    }

    /**
     * A wheel house behind an arch: from the arch's curved edge (the outline's points above its foot) straight in by
     * WHEEL_HOUSE_M, and a wall closing it there, dark.
     */
    private static void wheelHouse(Opening arch, SurfaceCut.Loop loop, PartMesh out) {
        if (loop == null || loop.points().size() < 4) { return; }
        List<Vec3d> p = loop.points();
        int n = p.size();
        double foot = Double.POSITIVE_INFINITY;
        for (Vec3d v : p) { foot = Math.min(foot, v.y()); }
        boolean[] up = new boolean[n];
        for (int i = 0; i < n; i++) { up[i] = p.get(i).y() > foot + 0.03; }
        int start = -1;
        for (int i = 0; i < n; i++) { if (up[i] && !up[Math.floorMod(i - 1, n)]) { start = i; break; } }
        if (start < 0) { return; }
        List<Vec3d> run = new ArrayList<>();
        for (int i = start; up[i % n] && run.size() < n; i++) { run.add(p.get(i % n)); }
        if (run.size() < 2) { return; }
        double dz = -arch.side() * WHEEL_HOUSE_M, xa = 0;
        for (Vec3d v : run) { xa += v.x() / run.size(); }
        List<Vec3d> in = new ArrayList<>();
        for (Vec3d v : run) { in.add(v.add(new Vec3d(0, 0, dz))); }
        for (int i = 0; i + 1 < run.size(); i++) {
            Vec3d a = run.get(i), b = run.get(i + 1), cc = in.get(i + 1), d = in.get(i);
            Vec3d towards = new Vec3d(xa, foot, 0.5 * (a.z() + d.z())).subtract(a.add(b).add(cc).add(d).scale(0.25));
            emitFlat(out, new Vec3d[]{a, b, cc, d}, towards, WHEEL_HOUSE);
        }
        Vec3d centre = new Vec3d(xa, foot, in.get(0).z()), outward = new Vec3d(0, 0, arch.side());
        for (int i = 0; i + 1 < in.size(); i++) { emitFlat(out, new Vec3d[]{centre, in.get(i), in.get(i + 1), centre}, outward, WHEEL_HOUSE); }
    }

    /** Bulkheads closing each run of lined strips at both ends, a centimetre inside, facing into the cabin. */
    private static void bulkheads(Context c, Cells cells, double wall, PartMesh out) {
        int strips = cells.corner().length;
        for (int j = 0; j < strips; j++) {
            if (!c.layout().lined[cells.module()[j]]) { continue; }
            boolean before = j > 0 && c.layout().lined[cells.module()[j - 1]], after = j + 1 < strips && c.layout().lined[cells.module()[j + 1]];
            if (!before) { bulkhead(c, cells, j, j, wall, 1, out); }
            if (!after) { bulkhead(c, cells, j, j + 1, wall, -1, out); }
        }
    }

    private static void bulkhead(Context c, Cells cells, int strip, int station, double wall, int facing, PartMesh out) {
        BodySurface s = c.s();
        Vec3d[] ring = new Vec3d[RING];
        Vec3d centre = Vec3d.ZERO;
        for (int k = 0; k < RING; k++) {
            ring[k] = s.rings[station][k].subtract(cells.vertex()[station][k].scale(wall)).add(new Vec3d(0.01 * facing, 0, 0));
            centre = centre.add(ring[k].scale(1.0 / RING));
        }
        int colour = trim(c, cells.module()[strip]);
        Vec3d n = new Vec3d(facing, 0, 0);
        for (int k = 0; k < RING; k++) { emitFlat(out, new Vec3d[]{centre, ring[k], ring[(k + 1) % RING], centre}, n, colour); }
    }

    /** An opening painted on the skin (far away): its area grown by some metres all round, lifted. */
    private static void painted(PartMesh out, BodySurface s, Opening o, double grow, int colour, double lift) {
        double x0 = Math.max(o.x0() - grow, s.rear()), x1 = Math.min(o.x1() + grow, s.front());
        double widest = 0;
        for (int r = 0; r < o.xs().length; r++) { widest = Math.max(widest, o.hi()[r] - o.lo()[r]); }
        // far away: coarse (a window's corners round enough at a few rows)
        int rows = Math.max(3, (int) Math.ceil((x1 - x0) / 0.07)), cols = Math.clamp((int) Math.ceil(widest * 0.6 / 0.1), 2, 12);
        DoubleUnaryOperator lo = x -> { double xc = Math.clamp(x, o.x0(), o.x1()), t = o.loAt(xc); return t - grow / BodyLayout.speed(s, xc, t); };
        DoubleUnaryOperator hi = x -> { double xc = Math.clamp(x, o.x0(), o.x1()), t = o.hiAt(xc); return t + grow / BodyLayout.speed(s, xc, t); };
        patch(out, s, x0, x1, rows, cols, lo, hi, colour, lift);
    }

    /** An opening's seams (far away): a thin band just inside its outline. */
    private static void seams(PartMesh out, BodySurface s, Opening o, double width, int colour, double lift) {
        int rows = Math.max(3, (int) Math.ceil((o.x1() - o.x0()) / 0.05));
        DoubleUnaryOperator lo = o::loAt, hi = o::hiAt;
        patch(out, s, o.x0(), o.x1(), rows, 1, lo, x -> Math.min(o.hiAt(x), o.loAt(x) + width / BodyLayout.speed(s, x, o.loAt(x))), colour, lift);
        patch(out, s, o.x0(), o.x1(), rows, 1, x -> Math.max(o.loAt(x), o.hiAt(x) - width / BodyLayout.speed(s, x, o.hiAt(x))), hi, colour, lift);
        patch(out, s, o.x0(), o.x0() + width, 1, 6, lo, hi, colour, lift);
        patch(out, s, o.x1() - width, o.x1(), 1, 6, lo, hi, colour, lift);
    }

    /** A door's pull handle at a point on its side: a short chrome bar on the surface. */
    private static void handle(PartMesh out, BodySurface s, Opening door, Vec3d at, double lift) {
        double x = at.x(), t = s.sideAngle(x, at.y(), door.side() > 0);
        double dt = 0.0175 / BodyLayout.speed(s, x, t);
        patch(out, s, x - 0.075, x + 0.075, 4, 1, xx -> t - dt, xx -> t + dt, CHROME, lift);
    }

    /**
     * How far a car's blunt ends roll over (m): the drawing rounds the edge between the sides and the end face with a
     * quarter circle of this radius standing out beyond the end station (looks only; the body's shape is the station's).
     */
    static double rollOver(Context c, int station) {
        BodyShape.Station st = c.s().stations.get(station);
        int module = Math.min(st.module(), c.modules() - 1);
        if (c.family(module) != Family.CAR || c.module(module).open() || st.areaM2() < 0.05) { return 0; }
        return Math.min(0.09, 0.18 * Math.min(0.5 * st.widthM(), st.topM() - st.bottomM()));
    }

    /**
     * A blunt end (a flat nose, a transom, a car's front and tail): a fan to the outline's centre, below an open edge;
     * a car's end first rolls over its edge (rollOver) so the front and tail are not cut off square.
     */
    private static void cap(Context c, PartMesh out, int station, int sign) {
        BodyShape.Station st = c.s().stations.get(station);
        if (st.areaM2() < 1e-4) { return; }
        Vec3d[] ring = c.s().rings[station];
        Vec3d centre = new Vec3d(st.xM(), st.centroidYM(), 0), n = new Vec3d(sign, 0, 0);
        int module = Math.min(st.module(), c.modules() - 1);
        double f = rollOver(c, station);
        if (f > 0) {
            int steps = 5;
            Vec3d[] inward = new Vec3d[RING];
            for (int k = 0; k < RING; k++) {
                Vec3d d = new Vec3d(0, centre.y() - ring[k].y(), -ring[k].z());
                inward[k] = d.lengthSquared() > 1e-12 ? d.normalized() : Vec3d.Y;
            }
            Vec3d[] previous = ring;
            for (int m = 1; m <= steps; m++) {
                double p0 = Math.PI / 2 * (m - 1) / steps, p1 = Math.PI / 2 * m / steps;
                Vec3d[] next = new Vec3d[RING];
                for (int k = 0; k < RING; k++) { next[k] = ring[k].add(inward[k].scale(f * (1 - Math.cos(p1)))).add(n.scale(f * Math.sin(p1))); }
                for (int k = 0; k < RING; k++) {
                    int k1 = (k + 1) % RING;
                    Vec3d a = previous[k], b = previous[k1], cc = next[k1], d = next[k];
                    Vec3d na = inward[k].scale(-Math.cos(p0)).add(n.scale(Math.sin(p0))), nb = inward[k1].scale(-Math.cos(p0)).add(n.scale(Math.sin(p0)));
                    Vec3d nc = inward[k1].scale(-Math.cos(p1)).add(n.scale(Math.sin(p1))), nd = inward[k].scale(-Math.cos(p1)).add(n.scale(Math.sin(p1)));
                    Vec3d face = na.add(nb).add(nc).add(nd).normalized();
                    if (Math.max(a.y(), b.y()) > c.sill()[module] + 1e-6) { continue; }
                    emit(out, new Vec3d[]{a, b, cc, d}, new Vec3d[]{na, nb, nc, nd}, base(c, module, a.add(cc).scale(0.5), face, k), face);
                }
                previous = next;
            }
            ring = previous;
            centre = centre.add(n.scale(f));
        }
        for (int k = 0; k < RING; k++) {
            Vec3d a = ring[k], b = ring[(k + 1) % RING];
            if (Math.max(a.y(), b.y()) > c.sill()[module] + 1e-6) { continue; }
            Vec3d mid = centre.add(a).add(b).scale(1.0 / 3);
            emit(out, new Vec3d[]{centre, a, b, centre}, new Vec3d[]{n, n, n, n}, base(c, module, mid, n, k), n);
        }
    }

    /**
     * How many rows of the skin's cells from the bottom a car end's bumper takes (0.16): those whose middle at the end
     * itself is under 0.42 m (front) or 0.45 m (rear) above the floor. Its edge then runs along a row of BodySurface's
     * rays, smooth over a rising nose, where testing each face's own height stepped it from cell to cell.
     */
    private static int bumperRows(Context c, boolean front) {
        Vec3d[] ring = c.s().rings[front ? c.s().xs.length - 1 : 0];
        double limit = c.bottom() + (front ? 0.42 : 0.45);
        int rows = 0;
        while (rows < RING / 2 && 0.5 * (ring[rows].y() + ring[rows + 1].y()) < limit) { rows++; }
        return rows;
    }

    /**
     * The paint of the skin at a face: by family, and the stripes by the face's ray (rows of cells, so they run smooth
     * along the body and a door's panel carries its share).
     */
    private static int base(Context c, int module, Vec3d mid, Vec3d n, int segment) {
        if (module == c.highlight()) { return Shapes.HIGHLIGHT; }
        int k = segment < RING / 2 ? segment : RING - 1 - segment;
        return switch (c.family(module)) {
            case AIRCRAFT -> {
                if (k < RING / 8) { yield c.lower(); }
                int stripe = stripe(c, Family.AIRCRAFT, k);
                yield stripe != 0 ? stripe : c.main()[module];
            }
            case BOAT -> {
                if (n.y() > 0.65 && mid.y() > c.waterline() + 0.12) { yield DECK; }
                if (mid.y() < c.waterline()) { yield c.lower(); }
                int stripe = mid.y() > c.waterline() + 0.05 ? stripe(c, Family.BOAT, k) : 0;
                yield stripe != 0 ? stripe : c.main()[module];
            }
            case CAR -> {
                if (n.y() < -0.55) { yield UNDER; }
                // 0.18: a front with its own bumper (FrontLayout) is painted under it; the band only on fronts without one
                boolean bumper = c.front().face() != null && c.front().face().bumper();
                if (!bumper && mid.x() > c.s().front() - 0.16 && k < bumperRows(c, true)) { yield BUMPER; }
                if (mid.x() < c.s().rear() + 0.16 && k < bumperRows(c, false)) { yield BUMPER; }
                if (mid.y() < c.bottom() + 0.15) { yield c.lower(); }
                int stripe = stripe(c, Family.CAR, k);
                if (stripe != 0) { yield stripe; }
                if (n.y() > 0.8 && c.module(module).kind() == Kind.PASSENGER_CELL) { yield c.accent(); }
                yield c.main()[module];
            }
        };
    }

    /**
     * 0.12: the stripe's colour on a row of rays (k counted up from the bottom on either side), or 0 off the stripes.
     * The rows: an aircraft's about its side's middle (the cheat line under the windows), a car's below the belt, a
     * boat's on the topsides. Automatic: an aircraft's cheat line in the accent with the gold pin stripe; a boat's boot
     * stripe (boatHull); a car none.
     */
    private static int stripe(Context c, Family f, int k) {
        Stripes kind = c.stripes() == Stripes.AUTO ? (f == Family.AIRCRAFT ? Stripes.CHEAT : Stripes.NONE) : c.stripes();
        int row = switch (f) { case AIRCRAFT -> RING / 4 - 1; case CAR -> RING / 4 - 4; case BOAT -> RING / 4 + 2; };
        int colour = c.stripes() == Stripes.AUTO ? c.accent() : c.stripe();
        return switch (kind) {
            case AUTO, NONE -> 0;
            case SIDE -> k == row ? colour : 0;
            case TWIN -> k == row - 1 || k == row + 1 ? colour : 0;
            case CHEAT -> k == row - 1 ? AIR_PIN : k == row || k == row + 1 ? colour : 0;
        };
    }

    /** A quad facing along n, wound counter-clockwise seen from that side (vehicles are drawn with back faces culled). */
    private static void emit(PartMesh out, Vec3d[] q, Vec3d[] ns, int color, Vec3d n) {
        int[] colors = {color, color, color, color};
        if (q[2].subtract(q[0]).cross(q[3].subtract(q[1])).dot(n) >= 0) { out.quad(q[0], q[1], q[2], q[3], ns, colors); }
        else { out.quad(q[0], q[3], q[2], q[1], new Vec3d[]{ns[0], ns[3], ns[2], ns[1]}, colors); }
    }

    private static void emitFlat(PartMesh out, Vec3d[] q, Vec3d n, int color) { emit(out, q, new Vec3d[]{n, n, n, n}, color, n); }

    // ---------------------------------------------------------------- laying things on the surface

    /** A patch laid on the surface over x0..x1, between the angles lo(x) and hi(x), standing `lift` off it. */
    private static void patch(PartMesh out, BodySurface s, double x0, double x1, int rows, int cols, DoubleUnaryOperator lo,
                              DoubleUnaryOperator hi, int color, double lift) {
        if (!(x1 > x0) || rows < 1 || cols < 1) { return; }
        Vec3d[][] p = new Vec3d[rows + 1][cols + 1], n = new Vec3d[rows + 1][cols + 1];
        boolean[] some = new boolean[rows + 1];
        for (int r = 0; r <= rows; r++) {
            double x = x0 + (x1 - x0) * r / rows, a = lo.applyAsDouble(x), b = hi.applyAsDouble(x);
            some[r] = b > a + 1e-9;
            if (!some[r]) { b = a; }
            for (int q = 0; q <= cols; q++) {
                double t = a + (b - a) * q / cols;
                Vec3d nn = s.normal(x, t);
                n[r][q] = nn;
                p[r][q] = s.point(x, t).add(nn.scale(lift));
            }
        }
        for (int r = 0; r < rows; r++) {
            if (!some[r] && !some[r + 1]) { continue; }
            for (int q = 0; q < cols; q++) {
                Vec3d[] quad = {p[r][q], p[r][q + 1], p[r + 1][q + 1], p[r + 1][q]};
                if (quad[2].subtract(quad[0]).cross(quad[3].subtract(quad[1])).lengthSquared() < 1e-16) { continue; }
                Vec3d[] ns = {n[r][q], n[r][q + 1], n[r + 1][q + 1], n[r + 1][q]};
                emit(out, quad, ns, color, ns[0].add(ns[1]).add(ns[2]).add(ns[3]));
            }
        }
    }

    /** A band on one side between the heights low(x) and high(x) (empty where high is not above low). */
    private static void sideBand(PartMesh out, BodySurface s, boolean starboard, double x0, double x1, int rows, int cols,
                                 DoubleUnaryOperator low, DoubleUnaryOperator high, int color, double lift) {
        DoubleUnaryOperator lo = x -> {
            double yl = low.applyAsDouble(x), yh = high.applyAsDouble(x), a = s.sideAngle(x, yl, starboard);
            return yh <= yl ? a : Math.min(a, s.sideAngle(x, yh, starboard));
        };
        DoubleUnaryOperator hi = x -> {
            double yl = low.applyAsDouble(x), yh = high.applyAsDouble(x), a = s.sideAngle(x, yl, starboard);
            return yh <= yl ? a : Math.max(a, s.sideAngle(x, yh, starboard));
        };
        patch(out, s, x0, x1, rows, cols, lo, hi, color, lift);
    }

    /** A rounded rectangle flat across x (a lamp or a grille on a blunt end), facing along x by sign. */
    private static void flatBox(PartMesh out, double x, int sign, double zc, double yc, double hw, double hh, double r, int color) {
        r = Math.min(r, Math.min(hw, hh));
        List<Vec3d> rim = new ArrayList<>();
        double[][] corners = {{hw - r, hh - r, 0}, {-(hw - r), hh - r, 90}, {-(hw - r), -(hh - r), 180}, {hw - r, -(hh - r), 270}};
        for (double[] k : corners) {
            for (int a = 0; a <= 4; a++) {
                double ang = Math.toRadians(k[2] + 22.5 * a);
                rim.add(new Vec3d(x, yc + k[1] + r * Math.sin(ang), zc + k[0] + r * Math.cos(ang)));
            }
        }
        Vec3d centre = new Vec3d(x, yc, zc), n = new Vec3d(sign, 0, 0);
        for (int i = 0; i < rim.size(); i++) { emitFlat(out, new Vec3d[]{centre, rim.get(i), rim.get((i + 1) % rim.size()), centre}, n, color); }
    }

    // ---------------------------------------------------------------- aircraft

    /** An aircraft module's panel seam where it meets the module behind; a cabin's beacons and antenna. */
    private static void aircraft(Context c, int i, PartMesh out) {
        BodySurface s = c.s();
        BodyModuleDesign m = c.module(i);
        double front = c.shape().moduleFrontX(i), rear = c.shape().moduleRearX(i), len = front - rear;
        if (i + 1 < c.modules() && c.family(i + 1) == Family.AIRCRAFT && !m.open()) {
            patch(out, s, rear - 0.006, rear + 0.006, 1, RING, x -> 0, x -> 2 * Math.PI, SEAM, LAYER1);
        }
        if (m.kind() == Kind.CABIN && !m.open()) {
            double x = rear + 0.2 * len;
            // the beacons above and below are LampLayout's (0.16)
            Vec3d mast = s.point(front - 0.15 * len, Math.PI);
            out.box(mast.add(new Vec3d(-0.012, -0.01, -0.012)), mast.add(new Vec3d(0.012, 0.32, 0.012)), ANTENNA);
        }
    }

    // ---------------------------------------------------------------- boats

    /** Over the boat modules: the boot stripe on the waterline, a rub rail along the sheer (or an open module's gunwale), cleats. */
    private static void boatHull(Context c, PartMesh out) {
        if (!(c.waterline() > Double.NEGATIVE_INFINITY)) { return; }
        BodySurface s = c.s();
        double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < c.modules(); i++) {
            if (c.family(i) == Family.BOAT) { x0 = Math.min(x0, c.shape().moduleRearX(i)); x1 = Math.max(x1, c.shape().moduleFrontX(i)); }
        }
        if (!(x1 > x0)) { return; }
        double wl = c.waterline(), bx0 = Double.POSITIVE_INFINITY, bx1 = Double.NEGATIVE_INFINITY;
        for (BodyShape.Station st : s.stations) {
            if (st.xM() < x0 - 1e-6 || st.xM() > x1 + 1e-6) { continue; }
            if (st.bottomM() < wl - 0.03 && st.topM() > wl + 0.06) { bx0 = Math.min(bx0, st.xM()); bx1 = Math.max(bx1, st.xM()); }
        }
        for (boolean sb : new boolean[]{false, true}) {
            if (bx1 > bx0 && c.stripes() == Stripes.AUTO) {
                sideBand(out, s, sb, bx0, bx1, Math.max(4, (int) Math.ceil((bx1 - bx0) / 0.06)), 2, x -> wl - 0.025, x -> wl + 0.045, c.accent(), LAYER1);
            }
            List<Vec3d> rail = new ArrayList<>();
            for (int j = 0; j < s.xs.length; j++) {
                BodyShape.Station st = s.stations.get(j);
                if (st.xM() < x0 - 1e-6 || st.xM() > x1 + 1e-6) { continue; }
                double y = Math.min(st.topM(), c.sill()[Math.min(st.module(), c.modules() - 1)]) - 0.035, t = s.portAngleAt(j, y);
                if (sb) { t = 2 * Math.PI - t; }
                rail.add(s.hit(j, t).add(s.normal(st.xM(), t).scale(0.012)));
            }
            if (rail.size() >= 2) { out.tube(rail, 0.028, 8, RUB_RAIL); }
        }
        // cleats on the deck near the bow and the stern, where it is closed; 0.12: standing on the deck where they are
        // (a cambered deck is lower out there than on the centreline: they went through it into the cabin)
        // 0.22: the kit's cleat (a base and two horns) the nearest three levels, not a box
        for (double x : new double[]{x1 - 0.45, x0 + 0.3}) {
            int i = moduleAt(c, x);
            if (i < 0 || c.module(i).open() || c.family(i) != Family.BOAT) { continue; }
            double z = 0.22 * s.width(x), y = deckAt(s, x, z) + 0.001;   // on the deck (a lined cabin's ceiling is just under it)
            for (int side : new int[]{-1, 1}) {
                Details.place(out, DetailKit.Part.CLEAT, new Vec3d(x, y, side * z), Vec3d.Y, Vec3d.X, 0.2, 1, true);
            }
        }
        bowRail(c, out, x0, x1);
    }

    /**
     * 0.22: a bow rail (pulpit) round a closed foredeck: a stainless tube half a metre over the deck, 6 cm in from its
     * edge, from up to 1.6 m back round to just short of the stem, on stanchions; 8-sided the nearest two levels, 4-sided
     * the next, none farther.
     */
    private static void bowRail(Context c, PartMesh out, double x0, double x1) {
        BodySurface s = c.s();
        double tip = x1 - 0.1, back = Math.max(x0 + 0.2, tip - Math.min(1.6, 0.3 * (x1 - x0))), h = 0.5;
        int module = moduleAt(c, 0.5 * (back + tip));
        if (!(tip - back > 0.5) || module < 0 || c.module(module).open() || c.family(module) != Family.BOAT || moduleAt(c, back) != module) { return; }
        int n = 8;
        List<Vec3d> rail = new ArrayList<>(), feet = new ArrayList<>();
        for (int side : new int[]{-1, 1}) {
            for (int k = 0; k <= n; k++) {
                if (side > 0 && k == 0) { continue; }   // the stem's point once: aft to it along port, back along starboard
                double f = side < 0 ? (double) k / n : 1 - (double) k / n, x = back + (tip - back) * f;
                double z = side * Math.max(0, 0.5 * s.width(x) - 0.06) * (1 - 0.6 * f * f * f);   // drawn in toward the stem
                Vec3d foot = new Vec3d(x, deckAt(s, x, z), z);
                rail.add(foot.add(new Vec3d(0, h, 0)));
                if (k % 4 == 0) { feet.add(foot); }
            }
        }
        int tags = out.tags();
        Finish pen = out.finish();
        out.finish(Finishes.STAINLESS);
        for (int[] lv : new int[][]{{0, 1, 8}, {2, 2, 4}}) {
            out.levels(lv[0], lv[1]);
            out.tube(rail, 0.0125, lv[2], CLEAT);
            for (Vec3d f : feet) { out.cylinder(f, f.add(new Vec3d(0, h, 0)), 0.011, 0.011, lv[2], CLEAT); }
        }
        out.pens(pen, tags);
    }

    /** The top of the skin at x, a distance out from the centreline (the upper half's ray that far out). */
    private static double deckAt(BodySurface s, double x, double z) {
        double lo = Math.PI / 2, hi = Math.PI, target = Math.abs(z);
        if (Math.abs(s.point(x, lo).z()) < target) { return s.point(x, lo).y(); }
        for (int it = 0; it < 30; it++) {
            double mid = 0.5 * (lo + hi);
            if (Math.abs(s.point(x, mid).z()) > target) { lo = mid; } else { hi = mid; }
        }
        return s.point(x, lo).y();
    }

    private static int moduleAt(Context c, double x) {
        for (int i = 0; i < c.modules(); i++) {
            if (x >= c.shape().moduleRearX(i) - 1e-9 && x <= c.shape().moduleFrontX(i) + 1e-9) { return i; }
        }
        return -1;
    }

    // ---------------------------------------------------------------- open modules

    /** An open module: inner walls and a floor 0.45 m below the open edge, a gunwale, bulkheads against closed neighbours. */
    private static void open(Context c, int i, PartMesh out) {
        BodySurface s = c.s();
        double front = c.shape().moduleFrontX(i), rear = c.shape().moduleRearX(i), sill = c.sill()[i];
        List<Integer> idx = new ArrayList<>();
        double low = Double.POSITIVE_INFINITY;
        for (int j = 0; j < s.xs.length; j++) {
            if (s.xs[j] >= rear - 1e-6 && s.xs[j] <= front + 1e-6) { idx.add(j); low = Math.min(low, s.stations.get(j).bottomM()); }
        }
        if (idx.size() < 2 || !(sill > low + 0.1)) { return; }
        double floor = Math.min(Math.max(low + 0.12, sill - 0.45), sill - 0.05);
        boolean boat = c.family(i) == Family.BOAT;
        int wall = boat ? BOAT_INSIDE : DARK_INSIDE, sole = boat ? BOAT_SOLE : DARK_SOLE;
        for (int a = 0; a + 1 < idx.size(); a++) {
            int j0 = idx.get(a), j1 = idx.get(a + 1);
            if (s.xs[j1] - s.xs[j0] < 1e-4) { continue; }
            double f0 = s.portAngleAt(j0, floor), f1 = s.portAngleAt(j1, floor), g0 = s.portAngleAt(j0, sill), g1 = s.portAngleAt(j1, sill);
            Vec3d p0 = s.hit(j0, f0), p1 = s.hit(j1, f1);
            emitFlat(out, new Vec3d[]{p0, mirror(p0), mirror(p1), p1}, Vec3d.Y, sole);
            for (boolean sb : new boolean[]{false, true}) {
                for (int r = 0; r < 3; r++) {
                    Vec3d[] q = {inner(s, j0, f0 + (g0 - f0) * r / 3, sb), inner(s, j0, f0 + (g0 - f0) * (r + 1) / 3, sb),
                            inner(s, j1, f1 + (g1 - f1) * (r + 1) / 3, sb), inner(s, j1, f1 + (g1 - f1) * r / 3, sb)};
                    emitFlat(out, q, new Vec3d(0, 0, sb ? -1 : 1), wall);
                }
            }
        }
        // the gunwale along the open edge
        int capping = c.main()[i];
        for (boolean sb : new boolean[]{false, true}) {
            List<Vec3d> path = new ArrayList<>();
            for (int j : idx) {
                double t = s.portAngleAt(j, sill);
                Vec3d p = s.hit(j, sb ? 2 * Math.PI - t : t);
                path.add(p.add(new Vec3d(0, 0, -Math.signum(p.z()) * 0.01)));
            }
            out.tube(path, 0.03, 8, capping);
        }
        if (i > 0 && !c.module(i - 1).open()) { bulkhead(c, idx.get(idx.size() - 1), floor, out, wall); }
        if (i + 1 < c.modules() && !c.module(i + 1).open()) { bulkhead(c, idx.get(0), floor, out, wall); }
        if (boat && i > 0 && !c.module(i - 1).open()) { windscreen(c, front, sill, out); }
    }

    private static Vec3d mirror(Vec3d p) { return new Vec3d(p.x(), p.y(), -p.z()); }

    /** A point of an inner wall: the skin at station j and port angle t (mirrored to starboard), 2 cm in. */
    private static Vec3d inner(BodySurface s, int j, double t, boolean starboard) {
        Vec3d p = s.hit(j, t);
        if (starboard) { p = mirror(p); }
        return p.add(new Vec3d(0, 0, -Math.signum(p.z()) * 0.02));
    }

    /** A closed module's end wall seen from an open one: the station's outline above the floor, drawn from both sides. */
    private static void bulkhead(Context c, int station, double floor, PartMesh out, int color) {
        Vec3d[] ring = c.s().rings[station];
        List<Vec3d> poly = new ArrayList<>();
        for (int k = 0; k < RING; k++) {
            Vec3d a = ring[k], b = ring[(k + 1) % RING];
            boolean ain = a.y() >= floor, bin = b.y() >= floor;
            if (ain) { poly.add(a); }
            if (ain != bin) { double t = (floor - a.y()) / (b.y() - a.y()); poly.add(a.add(b.subtract(a).scale(t))); }
        }
        if (poly.size() < 3) { return; }
        Vec3d centre = Vec3d.ZERO;
        for (Vec3d v : poly) { centre = centre.add(v.scale(1.0 / poly.size())); }
        for (int k = 0; k < poly.size(); k++) {
            Vec3d[] q = {centre, poly.get(k), poly.get((k + 1) % poly.size()), centre};
            emitFlat(out, q, Vec3d.X, color);
            emitFlat(out, q, Vec3d.X.scale(-1), color);
        }
    }

    /** A boat's windscreen on the deck just ahead of its open module: a curved pane leaning aft, framed in chrome. */
    private static void windscreen(Context c, double x, double sill, PartMesh out) {
        BodySurface s = c.s();
        double xb = x + 0.04, deck = s.top(xb), side = Math.max(sill + 0.02, deck - 0.06);
        if (deck - sill < 0.04) { side = deck - 0.04; }
        double ta = s.portAngle(xb, side);
        int cols = 16;
        List<Vec3d> bottom = new ArrayList<>(), top = new ArrayList<>();
        for (int q = 0; q <= cols; q++) {
            double t = ta + (2 * Math.PI - 2 * ta) * q / cols;
            Vec3d p = s.point(xb, t);
            bottom.add(p);
            top.add(new Vec3d(p.x() - 0.2, p.y() + 0.34, p.z() * 0.9));
        }
        out.clear(true);
        for (int q = 0; q < cols; q++) {
            Vec3d[] quad = {bottom.get(q), bottom.get(q + 1), top.get(q + 1), top.get(q)};
            Vec3d n = quad[2].subtract(quad[0]).cross(quad[3].subtract(quad[1]));
            emitFlat(out, quad, n, c.glass());
        }
        out.clear(false);
        out.tube(top, 0.015, 6, CHROME);
        out.tube(List.of(bottom.get(0), top.get(0)), 0.015, 6, CHROME);
        out.tube(List.of(bottom.get(cols), top.get(cols)), 0.015, 6, CHROME);
    }

    // ---------------------------------------------------------------- cars

    /**
     * Over the car modules: lamps and grille, a lip round each wheel arch, the chrome strip along the belt, the door
     * handles and the mirrors (on the doors: their pieces nearest, painted on farther).
     */
    private static void car(Context c, PartMesh out) {
        boolean any = false;
        for (int i = 0; i < c.modules(); i++) { any |= c.family(i) == Family.CAR; }
        if (!any) { return; }
        BodySurface s = c.s();
        if (c.front().resolved() != null) { front(c, out); } else { grille(c, out); }
        double r = c.d().settings().archRadiusM();
        for (Vec3d axle : BodyLayout.axles(c.d(), c.shape())) {
            double xa = axle.x(), yc = axle.y();
            for (boolean sb : new boolean[]{false, true}) {
                sideBand(out, s, sb, xa - r - 0.02, xa + r + 0.02, 24, 1, x -> yc + Math.sqrt(Math.max(0, r * r - (x - xa) * (x - xa))),
                        x -> yc + Math.sqrt(Math.max(0, Math.pow(r + 0.025, 2) - (x - xa) * (x - xa))), BUMPER, LAYER1);
            }
        }
        BodyLayout l = c.layout();
        double belt = l.belt;
        if (Double.isNaN(belt)) { return; }
        // the side glass's extent along the body
        double gx0 = Double.POSITIVE_INFINITY, gx1 = Double.NEGATIVE_INFINITY;
        for (Opening o : l.openings) { if (o.type() == BodyLayout.Type.SIDE_WINDOW) { gx0 = Math.min(gx0, o.x0()); gx1 = Math.max(gx1, o.x1()); } }
        if (!(gx1 > gx0)) { return; }
        Finish pen = out.finish();
        int pens = out.tags();
        for (int side : new int[]{-1, 1}) {
            boolean sb = side > 0;
            // the belt's chrome: on the body between the doors, on each door's panel nearest (painted on farther)
            List<Opening> doors = new ArrayList<>();
            for (Opening o : l.openings) { if (o.type() == BodyLayout.Type.DOOR && o.side() == side) { doors.add(o); } }
            doors.sort((a, b) -> Double.compare(a.x0(), b.x0()));
            double x = gx0 - 0.06;
            for (Opening door : doors) {
                if (door.x0() > x) { belt(out, s, sb, x, door.x0() - GAP_M, belt); }
                double hx = door.x0() + 0.16;
                Vec3d handleAt = s.point(hx, s.sideAngle(hx, belt - 0.07, sb));
                out.levels(0, 0).piece(doorPiece(l, door.id()));
                belt(out, s, sb, door.x0() + GAP_M, door.x1() - GAP_M, belt);
                handle(out, s, door, handleAt, LAYER2);
                out.levels(1, 3).piece(0);
                belt(out, s, sb, door.x0(), door.x1(), belt);
                handle(out, s, door, handleAt, LAYER2);
                out.pens(pen, pens);
                x = door.x1() + GAP_M;
            }
            if (gx1 + 0.06 > x) { belt(out, s, sb, x, gx1 + 0.06, belt); }
            // a mirror at the foot of the A pillar: on the front door where there is one
            double mx = gx1 - 0.01;
            Opening front = null;
            for (Opening o : doors) { if (mx >= o.x0() && mx <= o.x1()) { front = o; } }
            Vec3d at = s.point(mx, s.sideAngle(mx, belt + 0.06, sb));
            double sign = sb ? 1 : -1;
            int module = Math.max(0, moduleAt(c, mx));
            if (front != null) {
                out.levels(0, 0).piece(doorPiece(l, front.id()));
                doorMirror(out, at, sign, c.main()[module], true);
                out.levels(1, 3).piece(0);
                doorMirror(out, at, sign, c.main()[module], false);
                out.pens(pen, pens);
            } else {
                doorMirror(out, at, sign, c.main()[module], true);
            }
        }
    }

    /**
     * 0.14: a door mirror at a point of the skin (the A pillar's foot): a sail from the skin out 0.13 m, and on it the
     * vehicle kit's mirror (its foot, stalk and housing with the glass facing aft; the housing reaching 0.11 m either
     * side of the stalk, so its inner edge clears the side glass), painted the body's colour. Until 0.13 a painted box
     * standing off the skin.
     */
    private static void doorMirror(PartMesh out, Vec3d at, double sign, int color, boolean fine) {
        double z0 = at.z() - sign * 0.01, z1 = at.z() + sign * 0.13;
        out.box(new Vec3d(at.x() - 0.05, at.y(), Math.min(z0, z1)), new Vec3d(at.x() + 0.05, at.y() + 0.03, Math.max(z0, z1)), color);
        double k = 0.22 / DetailKit.Part.DOOR_MIRROR.size;
        Mesh m = dev.mcmod.aerocore.api.mesh.MeshOps.placed(DetailKit.mesh(DetailKit.Part.DOOR_MIRROR, fine), new Vec3d(k, k, k), Details.basis(Vec3d.Y, Vec3d.X),
                new Vec3d(at.x(), at.y() + 0.02, at.z() + sign * 0.12));
        for (MeshBuilder.Quad q : m.quads()) { out.quads().add((q.finish() == Finishes.PAINT ? q.withColor(color) : q).withTags(out.tags())); }
    }

    /** The belt's chrome strip on one side from x0 to x1. */
    private static void belt(PartMesh out, BodySurface s, boolean sb, double x0, double x1, double belt) {
        if (!(x1 - x0 > 0.01)) { return; }
        sideBand(out, s, sb, x0, x1, Math.max(2, (int) Math.ceil((x1 - x0) / 0.04)), 1, x -> belt - 0.012, x -> belt + 0.004, CHROME, LAYER2);
    }

    /** A family car's wheels (205/55R16 on cast wheels, vented discs) at the axles, their faces near flush with the sides. */
    private static void ghostWheels(Context c, PartMesh out) {
        var axle = dev.mcmod.hdfv.part.wheel.SampleWheels.sedanFront();
        PartMesh right = WheelModel.turning(axle.tyre(), axle.wheel(), axle.brake(), null, false), left = WheelModel.mirroredZ(right);
        PartMesh stillR = WheelModel.still(axle.tyre(), axle.wheel(), axle.brake(), null, false), stillL = WheelModel.mirroredZ(stillR);
        for (Vec3d a : BodyLayout.axles(c.d(), c.shape())) {
            double z = 0.5 * c.shape().widthAt(a.x()) - 0.11;
            out.add(right, Orientation.IDENTITY, new Vec3d(a.x(), a.y(), z));
            out.add(stillR, Orientation.IDENTITY, new Vec3d(a.x(), a.y(), z));
            out.add(left, Orientation.IDENTITY, new Vec3d(a.x(), a.y(), -z));
            out.add(stillL, Orientation.IDENTITY, new Vec3d(a.x(), a.y(), -z));
        }
    }

    /** A small octahedron (a mount point's mark) about the origin. */
    private static PartMesh diamond(double r, int color) {
        PartMesh out = new PartMesh();
        Vec3d[] ring = {new Vec3d(r, 0, 0), new Vec3d(0, 0, r), new Vec3d(-r, 0, 0), new Vec3d(0, 0, -r)};
        for (int i = 0; i < 4; i++) {
            Vec3d a = ring[i], b = ring[(i + 1) % 4];
            for (Vec3d tip : new Vec3d[]{new Vec3d(0, r * 1.4, 0), new Vec3d(0, -r * 1.4, 0)}) {
                Vec3d n = a.add(b).add(tip);
                Vec3d face = b.subtract(a).cross(tip.subtract(a));
                if (face.dot(n) >= 0) { out.quad(a, b, tip, tip, color, face); } else { out.quad(a, tip, b, b, color, face.scale(-1)); }
            }
        }
        return out;
    }

    /**
     * 0.18: a car's front end (FrontLayout: the bumper's shell, the grille's recess, slats or mesh, surround and badge,
     * the intake and the fog lamps' recesses, a bulge on the hood) in its colours: the bumper the body's, black plastic or
     * chrome (or the page's colour), the grille's chrome or dark (or the page's colour).
     */
    private static void front(Context c, PartMesh out) {
        var r = c.front().resolved();
        int bumper = r.bumperColour() >= 0 ? r.bumperColour() : switch (r.bumper()) {
            case BLACK, STEP -> BUMPER;
            case CHROME -> CHROME;
            default -> c.main()[0];
        };
        for (dev.mcmod.hdfv.look.KitLayout.Panel panel : c.front().panels()) {
            int colour = switch (panel.role()) {
                case "bumper", "bumper_edge" -> bumper;
                case "grille_recess" -> GRILLE;
                case "slat_chrome", "grille_frame", "emblem" -> r.grilleColour() >= 0 ? r.grilleColour() : CHROME;
                case "slat_dark", "intake_bar" -> r.grilleColour() >= 0 ? r.grilleColour() : KIT_DARK;
                case "intake", "fog_recess", "windscreen_frame", "wiper" -> UNDER;
                case "windscreen" -> c.farGlass();
                case "step" -> LAMP_RIM;
                default -> c.main()[0];
            };
            for (Vec3d[] q : panel.quads()) { sheet(out, q, colour); }
        }
    }

    /** A car's grille on a blunt front, between and below its headlamps (0.16: the lamps are LampLayout's; 0.18: only without FrontLayout). */
    private static void grille(Context c, PartMesh out) {
        BodySurface s = c.s();
        int end = s.xs.length - 1;
        BodyShape.Station st = s.stations.get(end);
        if (c.family(Math.min(st.module(), c.modules() - 1)) != Family.CAR || st.areaM2() < dev.mcmod.hdfv.look.LampLayout.FACE_SHARE * c.shape().particulars().frontalAreaM2()) { return; }
        double b = st.bottomM(), h = st.topM() - b, roll = rollOver(c, end);
        DoubleUnaryOperator half = y -> Math.max(Math.abs(s.hit(end, s.portAngleAt(end, y)).z()) - roll, 0.05);
        double ex = st.xM() + roll, yg = b + 0.36 * h;
        flatBox(out, ex + LAYER1, 1, 0, yg, 0.42 * half.applyAsDouble(yg), 0.08 * h, 0.02, GRILLE);
    }

    // ---------------------------------------------------------------- lamps (0.16)

    /**
     * 0.18: a wrap-round cluster (FrontLayout.cluster): its housing, the vehicle kit's projector at each bulb, and its lens
     * standing off the skin round the corner, lit with the lamp (tagged as its lens). The first bulb's lens is the light's.
     */
    private static Vec3d cluster(Context c, PartMesh out, dev.mcmod.hdfv.look.LampLayout.Placed p, int id, boolean red) {
        var cl = dev.mcmod.hdfv.look.FrontLayout.cluster(c.d(), c.s(), p.spec(), p.mirror());
        int housing = p.housing() >= 0 ? p.housing() : LAMP_BODY;
        for (Vec3d[] q : cl.housing()) { sheet(out, q, housing); }
        Finish glass = red ? Finishes.LAMP_RED : Finishes.LAMP_CLEAR;
        double tall = dev.mcmod.hdfv.look.FrontLayout.CLUSTER_ASPECT * p.sizeM();
        Vec3d light = null;
        for (Vec3d b : cl.bulbs()) {
            Vec3d l = VehicleLamps.place(out, DetailKit.Part.HEADLAMP_PROJECTOR, b, cl.out(), Vec3d.Z, Math.min(0.8 * tall, 0.12), glass, id, p.light(), null);
            if (light == null) { light = l; }
        }
        Finish before = out.finish();
        out.finish(glass);
        int from = out.quads().size();
        for (Vec3d[] q : cl.lens()) { sheet(out, q, p.light()); }
        out.finish(before);
        for (int q = from; q < out.quads().size(); q++) {
            MeshBuilder.Quad k = out.quads().get(q);
            out.quads().set(q, k.withTags(VehicleLamps.lensTags(k.tags(), id)));
        }
        return light != null ? light : p.at().add(p.out().scale(0.016));
    }

    /** A lamp housing's own colour when the kit's has none (the procedural styles), an LED strip's dark backing. */
    private static final int LAMP_BODY = LAMP_RIM, STRIP_BACK = BUMPER;

    /**
     * The body's lamps (LampLayout: the player's own, else the kit's as 0.12 placed them): the kit's lamp or a square,
     * strip or bar housing, its lens lit by LampRules; a dipped headlamp also casts the main beam, a tail lamp cluster's
     * clear lens is the reversing light; a lamp on a stand (a masthead light) on its pole.
     */
    private static void lamps(Context c, PartMesh out) {
        for (dev.mcmod.hdfv.look.LampLayout.Placed p : dev.mcmod.hdfv.look.LampLayout.of(c.d(), c.s(), c.waterline())) { lamp(c, out, p); }
    }

    static void lamp(Context c, PartMesh out, dev.mcmod.hdfv.look.LampLayout.Placed p) {
        if (p.standM() > 0) { out.cylinder(p.foot(), p.at(), 0.012, 0.012, 8, ANTENNA); }
        int id = VehicleLamps.next(out), from = out.quads().size();
        DetailKit.Part part = switch (p.style()) {
            case ROUND -> DetailKit.Part.HEADLAMP_ROUND;
            case PROJECTOR -> DetailKit.Part.HEADLAMP_PROJECTOR;
            case TAIL -> DetailKit.Part.TAIL_LAMP;
            case NAV -> DetailKit.Part.NAV_LIGHT;
            case BEACON -> DetailKit.Part.BEACON;
            default -> null;
        };
        boolean redLight = ((p.light() >> 16) & 0xff) > 2 * ((p.light() >> 8) & 0xff);
        Vec3d lens;
        if (p.style() == dev.mcmod.hdfv.look.LampSpec.Style.CLUSTER) {
            lens = cluster(c, out, p, id, redLight);
        } else if (part != null) {
            Finish glass = part == DetailKit.Part.TAIL_LAMP || part == DetailKit.Part.BEACON ? Finishes.LAMP_RED : Finishes.LAMP_CLEAR;
            Finish recolour = glass == Finishes.LAMP_CLEAR && redLight ? Finishes.LAMP_RED : null;
            lens = VehicleLamps.place(out, part, p.at(), p.out(), p.along(), p.sizeM(), glass, id, p.light(), recolour);
            if (p.housing() >= 0) {
                for (int q = from; q < out.quads().size(); q++) {
                    MeshBuilder.Quad k = out.quads().get(q);
                    if (k.lamp() < 0 && k.finish() != Finishes.LAMP_CLEAR && k.finish() != Finishes.LAMP_RED) { out.quads().set(q, k.withColor(p.housing())); }
                }
            }
        } else {
            lens = housing(out, p, id, redLight);
        }
        if (p.beam()) {
            VehicleLamps.beam(out, id, lens, p.axis(), p.radiusM(), p.light(), p.spreadDeg(), p.rangeM(), p.role());
            if (p.role() == LampRules.Role.HEAD_LOW) {
                VehicleLamps.beam(out, VehicleLamps.next(out), lens.add(new Vec3d(0, 0.01, 0)), p.out().add(new Vec3d(0, -0.015, 0)), 0.06, p.light(), 9, 110,
                        LampRules.Role.HEAD_HIGH);
            }
        } else {
            VehicleLamps.point(out, id, lens, p.axis(), p.radiusM(), p.light(), p.spreadDeg(), p.role());
        }
        if (p.role() == LampRules.Role.TAIL_BRAKE && p.style() == dev.mcmod.hdfv.look.LampSpec.Style.TAIL) {
            int reverse = VehicleLamps.next(out);
            Vec3d white = VehicleLamps.retag(out, from, Finishes.LAMP_CLEAR, reverse, VehicleLamps.WHITE);
            if (white != null) { VehicleLamps.point(out, reverse, white, p.out(), 0.04, VehicleLamps.WHITE, 160, LampRules.Role.REVERSE); }
        }
        indicator(c, out, p, lens);
    }

    /**
     * 0.19: a car's headlamps and tail lamps carry its amber indicators (in the same unit: a lamp unit of the refit
     * shop is still one): a small lens just outboard of the lamp, flashing by LampRules on the side it is on
     * (port, -z, is left).
     */
    private static void indicator(Context c, PartMesh out, dev.mcmod.hdfv.look.LampLayout.Placed p, Vec3d lens) {
        if (c.d().settings().purpose() != dev.mcmod.hdfv.part.body.BodySettingsDesign.Purpose.CAR) { return; }
        if (p.role() != LampRules.Role.HEAD_LOW && p.role() != LampRules.Role.TAIL_BRAKE) { return; }
        if (Math.abs(p.at().z()) < 0.05) { return; }
        int side = p.at().z() < 0 ? -1 : 1;
        Vec3d outward = p.along().z() * side >= 0 ? p.along() : p.along().scale(-1);
        if (Math.abs(outward.z()) < 0.3) { outward = new Vec3d(0, 0, side); }
        double reach = 0.5 * p.sizeM() + 0.035;
        Vec3d at = p.at().add(outward.normalized().scale(reach));
        int id = VehicleLamps.next(out);
        Vec3d l = VehicleLamps.place(out, DetailKit.Part.NAV_LIGHT, at, p.out(), outward, Math.clamp(0.25 * p.sizeM(), 0.04, 0.07), Finishes.LAMP_CLEAR, id,
                dev.mcmod.hdfv.look.LampLayout.AMBER, null);
        VehicleLamps.point(out, id, l, p.out(), 0.035, dev.mcmod.hdfv.look.LampLayout.AMBER, 160, side < 0 ? LampRules.Role.TURN_LEFT : LampRules.Role.TURN_RIGHT);
    }

    /**
     * A square lamp, an LED strip or a light bar: a housing box standing on the surface (its depth along out) and its
     * lens on the face; returns the lens's middle.
     */
    private static Vec3d housing(PartMesh out, dev.mcmod.hdfv.look.LampLayout.Placed p, int id, boolean red) {
        Vec3d n = p.out().normalized(), a = p.along().subtract(n.scale(p.along().dot(n))).normalized();
        if (!(a.lengthSquared() > 0.5)) { a = Math.abs(n.y()) > 0.9 ? Vec3d.X : Vec3d.Y.cross(n).normalized(); }
        Vec3d b = n.cross(a).normalized();
        double w = p.sizeM(), h = dev.mcmod.hdfv.look.LampLayout.housingHeight(p.style(), w);
        double depth = switch (p.style()) {
            case STRIP -> 0.04 * w;
            case BAR -> 0.2 * w;
            default -> 0.12 * w;
        };
        // the long side across the vehicle for a lamp on an end face, along it for one on the side or roof
        Vec3d along = a, up = b;
        if (Math.abs(n.x()) > 0.9) { along = Vec3d.Z; up = n.cross(along).normalized(); }
        else if (Math.abs(n.y()) > 0.9) { along = Vec3d.Z; up = Vec3d.X; }
        Vec3d centre = p.at().add(n.scale(0.5 * depth));
        int housing = p.housing() >= 0 ? p.housing() : p.style() == dev.mcmod.hdfv.look.LampSpec.Style.STRIP ? STRIP_BACK : LAMP_BODY;
        Vec3d e1 = along.scale(0.5 * w), e2 = up.scale(0.5 * h), e3 = n.scale(0.5 * depth);
        Vec3d[] c = new Vec3d[8];
        for (int k = 0; k < 8; k++) { c[k] = centre.add(e1.scale((k & 1) == 0 ? -1 : 1)).add(e2.scale((k & 2) == 0 ? -1 : 1)).add(e3.scale((k & 4) == 0 ? -1 : 1)); }
        int[][] faces = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}};
        for (int[] f : faces) {
            Vec3d q0 = c[f[0]], q1 = c[f[1]], q2 = c[f[2]], q3 = c[f[3]];
            Vec3d normal = q1.subtract(q0).cross(q2.subtract(q0));
            if (normal.dot(q0.subtract(centre)) < 0) { out.quad(q0, q3, q2, q1, housing, normal.scale(-1)); } else { out.quad(q0, q1, q2, q3, housing, normal); }
        }
        // the lens: 90 % of the face, just proud of it
        Vec3d face = centre.add(n.scale(0.5 * depth + 0.002));
        Vec3d l1 = e1.scale(0.9), l2 = e2.scale(p.style() == dev.mcmod.hdfv.look.LampSpec.Style.STRIP ? 0.7 : 0.85);
        Finish glass = red ? Finishes.LAMP_RED : Finishes.LAMP_CLEAR;
        Finish before = out.finish();
        out.finish(glass);
        int at = out.quads().size();
        Vec3d q0 = face.subtract(l1).subtract(l2), q1 = face.add(l1).subtract(l2), q2 = face.add(l1).add(l2), q3 = face.subtract(l1).add(l2);
        if (q1.subtract(q0).cross(q2.subtract(q0)).dot(n) < 0) { out.quad(q0, q3, q2, q1, p.light(), n); } else { out.quad(q0, q1, q2, q3, p.light(), n); }
        out.finish(before);
        MeshBuilder.Quad lensQuad = out.quads().get(at);
        out.quads().set(at, lensQuad.withTags(VehicleLamps.lensTags(lensQuad.tags(), id)));
        return face;
    }

    // ---------------------------------------------------------------- the aero kit (0.16)

    /** The kit's dark pieces (a splitter, the diffuser, roof bars), carbon where it is carbon, the tips' chrome. */
    private static final int KIT_DARK = 0xff1b1c1f, KIT_CARBON = 0xff26282b;

    static {
        put(Finishes.PLASTIC, KIT_DARK);
        put(Finishes.CARBON_WEAVE, KIT_CARBON);
    }

    /** The kit (KitLayout): its pieces in the kit's colour (the body's main colour when blank), the structural ones dark. */
    private static void kit(Context c, PartMesh out) {
        if (!c.d().kitted() || c.d().kit().none()) { return; }
        dev.mcmod.hdfv.look.KitLayout k = dev.mcmod.hdfv.look.KitLayout.of(c.d(), c.shape());
        dev.mcmod.hdfv.part.body.BodyKitDesign design = c.d().kit();
        boolean carbon = design.material() == dev.mcmod.hdfv.part.engine.EngineMaterial.CFRP;
        boolean own = !design.paintColor().isEmpty();
        int paint = own ? dev.mcmod.hdfv.design.RecordEditor.argb(design.paintColor()) : c.main()[0];
        int dark = carbon ? KIT_CARBON : KIT_DARK;
        for (dev.mcmod.hdfv.look.KitLayout.Box b : k.boxes) {
            int colour = switch (b.role()) {
                case "splitter", "upright", "foot", "basket", "basket_floor", "bar" -> dark;
                default -> paint;
            };
            out.box(b.min(), b.max(), colour);
        }
        // the pieces on the skin; with no colour of its own a piece takes the body's where it sits (base's): the bumper's
        // under the nose, the lower band's along the sills
        for (dev.mcmod.hdfv.look.KitLayout.Panel p : k.panels) {
            int colour = switch (p.role()) {
                case "splitter" -> dark;
                case "scoop_mouth" -> UNDER;
                case "dam", "lip" -> own ? paint : BUMPER;
                case "skirt" -> own ? paint : c.lower();
                default -> paint;
            };
            for (Vec3d[] q : p.quads()) { sheet(out, q, colour); }
        }
        if (k.wing != null) {
            dev.mcmod.hdfv.look.KitLayout.Wing w = k.wing;
            double c0 = w.chordM(), angle = -Math.toRadians(w.angleDeg());
            // the plane about its mid-chord, its trailing edge raised by the angle (an inverted wing)
            Vec3d mid = w.quarterChord().add(new Vec3d(-0.25 * c0 * Math.cos(angle), -0.25 * c0 * Math.sin(angle), 0));
            Orientation tilt = Orientation.rotationVector(new Vec3d(0, 0, angle));
            out.box(mid, new Vec3d(0.5 * c0, 0.06 * c0, 0.5 * w.spanM()), tilt, carbon ? KIT_CARBON : paint);
            if (w.endplateM() > 0) {
                for (int side : new int[]{-1, 1}) {
                    out.box(mid.add(new Vec3d(0, 0.1 * w.endplateM(), side * 0.5 * w.spanM())), new Vec3d(0.55 * c0, 0.5 * w.endplateM(), 0.004), Orientation.IDENTITY, dark);
                }
            }
        }
        if (k.diffuser != null) {
            dev.mcmod.hdfv.look.KitLayout.Diffuser d = k.diffuser;
            double rise = (d.frontX() - d.rearX()) * Math.tan(Math.toRadians(d.angleDeg())), hw = 0.5 * d.widthM();
            Vec3d a = new Vec3d(d.frontX(), d.floorY() - 0.004, -hw), bb = new Vec3d(d.frontX(), d.floorY() - 0.004, hw),
                    cc = new Vec3d(d.rearX(), d.floorY() + rise - 0.004, hw), dd = new Vec3d(d.rearX(), d.floorY() + rise - 0.004, -hw);
            out.quad(a, dd, cc, bb, dark, new Vec3d(0, -1, 0));
            for (int s4 = 0; s4 < 4; s4++) {
                double z = -hw + (s4 + 0.5) * d.widthM() / 4;
                out.box(new Vec3d(d.rearX(), d.floorY() - 0.05, z - 0.004), new Vec3d(d.frontX(), d.floorY(), z + 0.004), dark);
            }
        }
        for (dev.mcmod.hdfv.look.KitLayout.Tip t : k.tips) {
            out.cylinder(t.at().add(new Vec3d(0.15, 0, 0)), t.at(), 0.5 * t.diameterM(), 0.5 * t.diameterM(), 12, CHROME);
            out.cylinder(t.at(), t.at().add(new Vec3d(0.002, 0, 0)), 0.42 * t.diameterM(), 0.42 * t.diameterM(), 12, UNDER);
        }
    }

    /** A sheet's quad seen from both sides (the world's vehicle meshes cull back faces). */
    private static void sheet(PartMesh out, Vec3d[] q, int colour) {
        Vec3d n = q[2].subtract(q[0]).cross(q[3].subtract(q[1]));
        if (n.lengthSquared() < 1e-14) { return; }
        n = n.normalized();
        out.quad(q[0], q[1], q[2], q[3], colour, n);
        out.quad(q[0], q[3], q[2], q[1], colour, n.scale(-1));
    }

    // ---------------------------------------------------------------- lettering

    /**
     * 0.12: the registration (Lettering): an aircraft's on each side of the rear fuselage, a boat's name on each side
     * of the bow and across its transom, a car's number plates. Decals from the glyph atlas, the nearest two levels.
     */
    private static void lettering(Context c, PartMesh out) {
        String text = Lettering.clean(c.d().settings().registrationText());
        if (text.isEmpty()) { return; }
        Finish pen = out.finish();
        int pens = out.tags();
        out.levels(0, 1);
        int pensNear = out.tags();
        BodySurface s = c.s();
        double air0 = Double.POSITIVE_INFINITY, air1 = Double.NEGATIVE_INFINITY, boat0 = Double.POSITIVE_INFINITY, boat1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < c.modules(); i++) {
            double r = c.shape().moduleRearX(i), f = c.shape().moduleFrontX(i);
            if (c.family(i) == Family.AIRCRAFT) { air0 = Math.min(air0, r); air1 = Math.max(air1, f); }
            if (c.family(i) == Family.BOAT) { boat0 = Math.min(boat0, r); boat1 = Math.max(boat1, f); }
        }
        if (air1 > air0) {
            // aft of every window and door, on the rear fuselage
            double first = air1;
            for (Opening o : c.layout().openings) {
                if (o.type() != BodyLayout.Type.ARCH && o.x1() > air0 && o.x0() < air1) { first = Math.min(first, o.x0()); }
            }
            double len = air1 - air0, x0 = air0 + 0.08 * len, x1 = first < air1 ? first - 0.15 : air0 + 0.45 * len;
            sides(c, out, text, x0, x1, 0.076, 0.305, 0.66, 0.26);
        }
        if (boat1 > boat0) {
            // on the bow, clear of the sidelights; across a transom
            double len = boat1 - boat0, x1 = boat1 - Math.max(0.75, 0.12 * len), x0 = Math.max(boat0 + 0.4 * len, x1 - 3);
            sides(c, out, text, x0, x1, 0.076, 0.3, Double.NaN, 0.3);
            BodyShape.Station st = s.stations.get(0);
            if (Math.abs(boat0 - s.rear()) < 1e-6 && st.areaM2() > 0.05 && st.topM() > c.waterline() + 0.25) {
                double y = c.waterline() + 0.45 * (st.topM() - c.waterline()), half = Math.abs(s.hit(0, s.portAngleAt(0, y)).z());
                double h = Math.min(Math.clamp(0.3 * (st.topM() - c.waterline()), 0.076, 0.2), 1.4 * half / Math.max(Lettering.width(text), 1e-9));
                if (h >= 0.05) {
                    out.finish(Finishes.GLYPHS).decal(true);
                    endText(out, st.xM() - LAYER2, -1, 0, y, h, text, contrast(c.main()[Math.max(0, moduleAt(c, boat0 + 0.01))]));
                    out.pens(pen, pensNear);
                }
            }
        }
        plates(c, out, text, pensNear);
        out.pens(pen, pens);
    }

    /**
     * A line of lettering on both sides between x0 and x1 (centred there), capitals from lo to hi metres high and at
     * most a share of the body's height, its middle at a share of the height up (NaN: halfway between a boat's
     * waterline and its sheer); left out where it does not fit.
     */
    private static void sides(Context c, PartMesh out, String text, double x0, double x1, double lo, double hi, double up, double heightShare) {
        if (!(x1 - x0 > 0.3)) { return; }
        BodySurface s = c.s();
        double xc = 0.5 * (x0 + x1), bottom = s.bottom(xc), top = s.top(xc);
        double y = Double.isNaN(up) ? 0.5 * (Math.max(c.waterline() + 0.08, bottom) + top) : bottom + up * (top - bottom);
        double room = Double.isNaN(up) ? top - Math.max(c.waterline() + 0.08, bottom) : top - bottom;
        double h = Math.min(Math.min(hi, heightShare * room), 0.92 * (x1 - x0) / Math.max(Lettering.width(text), 1e-9));
        if (h < lo * 0.66) { return; }
        double half = 0.5 * Lettering.width(text) * h, cell = h * Lettering.cellHeight();
        // the line must stay on the side all along it
        for (double x : new double[]{xc - half, xc + half}) {
            if (!(s.top(x) - 0.03 > y + 0.5 * cell && s.bottom(x) + 0.03 < y - 0.5 * cell)) { return; }
        }
        int module = Math.max(0, moduleAt(c, xc));
        int colour = contrast(c.main()[module]);
        Finish pen = out.finish();
        int pens = out.tags();
        out.finish(Finishes.GLYPHS).decal(true);
        sideText(out, s, false, xc + half, -1, y, h, text, colour);
        sideText(out, s, true, xc - half, 1, y, h, text, colour);
        out.pens(pen, pens);
    }

    /** Lettering that shows on paint: dark on light, white on dark. */
    private static int contrast(int paint) {
        double luma = (0.2126 * ((paint >> 16) & 255) + 0.7152 * ((paint >> 8) & 255) + 0.0722 * (paint & 255)) / 255;
        return luma > 0.5 ? TEXT_DARK : TEXT_LIGHT;
    }

    /**
     * A line on one side read from outside (port toward the nose, -x; starboard toward the tail, +x): from xStart along
     * dir, centred on height yc, capitals h high, each glyph in rows following the surface.
     */
    private static void sideText(PartMesh out, BodySurface s, boolean starboard, double xStart, int dir, double yc, double h, String text, int colour) {
        double cell = h * Lettering.cellHeight(), y0 = yc - 0.5 * cell;
        int rows = 3;
        for (Lettering.Glyph g : Lettering.layout(text)) {
            double xa = xStart + dir * g.x0() * h, xb = xStart + dir * g.x1() * h;
            for (int r = 0; r < rows; r++) {
                double ya = y0 + cell * r / rows, yb = y0 + cell * (r + 1) / rows;
                float va = (float) (g.v1() + (g.v0() - g.v1()) * r / rows), vb = (float) (g.v1() + (g.v0() - g.v1()) * (r + 1) / rows);
                double[][] at = {{xa, ya}, {xb, ya}, {xb, yb}, {xa, yb}};
                Vec3d[] p = new Vec3d[4], n = new Vec3d[4];
                for (int q = 0; q < 4; q++) {
                    double t = s.sideAngle(at[q][0], at[q][1], starboard);
                    n[q] = s.normal(at[q][0], t);
                    p[q] = s.point(at[q][0], t).add(n[q].scale(LAYER2));
                }
                glyph(out, p, n, g.u0(), g.u1(), va, vb, colour);
            }
        }
    }

    /** A line on a flat end facing along x by sign (read from outside: the front's toward -z, the rear's toward +z), centred on (zc, yc). */
    private static void endText(PartMesh out, double x, int sign, double zc, double yc, double h, String text, int colour) {
        double cell = h * Lettering.cellHeight(), half = 0.5 * Lettering.width(text) * h;
        int dir = sign > 0 ? -1 : 1;
        double z0 = zc - dir * half;
        Vec3d n = new Vec3d(sign, 0, 0);
        for (Lettering.Glyph g : Lettering.layout(text)) {
            double za = z0 + dir * g.x0() * h, zb = z0 + dir * g.x1() * h;
            Vec3d[] p = {new Vec3d(x, yc - 0.5 * cell, za), new Vec3d(x, yc - 0.5 * cell, zb), new Vec3d(x, yc + 0.5 * cell, zb), new Vec3d(x, yc + 0.5 * cell, za)};
            glyph(out, p, new Vec3d[]{n, n, n, n}, g.u0(), g.u1(), g.v1(), g.v0(), colour);
        }
    }

    /** A glyph's quad: corners bottom left, bottom right, top right, top left as read, its atlas cell from u0 to u1 and v (bottom) to v (top). */
    static void glyph(PartMesh out, Vec3d[] p, Vec3d[] n, float u0, float u1, float vBottom, float vTop, int colour) {
        Vec3d face = n[0].add(n[1]).add(n[2]).add(n[3]);
        int[] colours = {colour, colour, colour, colour};
        float[] uv = {u0, vBottom, u1, vBottom, u1, vTop, u0, vTop};
        if (p[2].subtract(p[0]).cross(p[3].subtract(p[1])).dot(face) >= 0) {
            out.quad(p[0], p[1], p[2], p[3], colour, face, n, colours, uv);
        } else {
            out.quad(p[0], p[3], p[2], p[1], colour, face, new Vec3d[]{n[0], n[3], n[2], n[1]}, colours, new float[]{u0, vBottom, u0, vTop, u1, vTop, u1, vBottom});
        }
    }

    /** A car's number plates on its blunt ends: the kit's frame (520 x 110 mm plate) under the grille and between the tail lamps, lettered. */
    private static void plates(Context c, PartMesh out, String text, int pens) {
        BodySurface s = c.s();
        int last = s.xs.length - 1;
        double area = c.shape().particulars().frontalAreaM2();
        Finish pen = out.finish();
        for (int end : new int[]{last, 0}) {
            BodyShape.Station st = s.stations.get(end);
            if (c.family(Math.min(st.module(), c.modules() - 1)) != Family.CAR || st.areaM2() < dev.mcmod.hdfv.look.LampLayout.FACE_SHARE * area) { continue; }
            int sign = end == last ? 1 : -1;
            double b = st.bottomM(), h = st.topM() - b, ex = st.xM() + sign * rollOver(c, end);
            double y = b + (sign > 0 ? 0.2 : 0.4) * h;
            // 0.18: the front's plate in its bumper (FrontLayout)
            if (sign > 0 && c.front().plate() != null) { ex = c.front().plate().x(); y = c.front().plate().y(); }
            Vec3d at = new Vec3d(ex + sign * LAYER1, y, 0), outward = new Vec3d(sign, 0, 0);
            double k = 0.54 / DetailKit.Part.PLATE_FRAME.size;
            Mesh frame = dev.mcmod.aerocore.api.mesh.MeshOps.placed(DetailKit.mesh(DetailKit.Part.PLATE_FRAME, true), new Vec3d(k, k, k), Details.basis(outward, Vec3d.Z), at);
            for (MeshBuilder.Quad q : frame.quads()) { out.quads().add((q.finish() == Finishes.PAINT ? q.withColor(PLATE) : q).withTags(out.tags())); }
            double cap = Math.min(0.075, 0.49 / Math.max(Lettering.width(text), 1e-9));
            out.finish(Finishes.GLYPHS).decal(true);
            endText(out, ex + sign * (LAYER1 + 0.0095 * k), sign, 0, y, cap, text, PLATE_TEXT);
            out.pens(pen, pens);
        }
    }

    // ---------------------------------------------------------------- inside

    /** A seat at its point (forward is +x): a cushion, a backrest leaning back, a headrest but on a boat's bench. */
    private static void seat(PartMesh out, Vec3d at, Family family) {
        int color = switch (family) { case AIRCRAFT -> AIR_SEAT; case CAR -> CAR_SEAT; case BOAT -> BOAT_SEAT; };
        double x = at.x(), y = at.y(), z = at.z();
        out.box(new Vec3d(x - 0.2, y - 0.1, z - 0.22), new Vec3d(x + 0.24, y + 0.04, z + 0.22), color);
        out.hexahedron(new Vec3d[]{
                new Vec3d(x - 0.14, y + 0.02, z - 0.21), new Vec3d(x - 0.14, y + 0.02, z + 0.21), new Vec3d(x - 0.25, y + 0.02, z + 0.21), new Vec3d(x - 0.25, y + 0.02, z - 0.21),
                new Vec3d(x - 0.27, y + 0.62, z - 0.2), new Vec3d(x - 0.27, y + 0.62, z + 0.2), new Vec3d(x - 0.37, y + 0.62, z + 0.2), new Vec3d(x - 0.37, y + 0.62, z - 0.2)}, color);
        if (family != Family.BOAT) { out.box(new Vec3d(x - 0.39, y + 0.64, z - 0.12), new Vec3d(x - 0.29, y + 0.82, z + 0.12), color); }
    }

    /** A box (a tank or hold) of a volume within its room: centred at x, from its bottom up, its sides scaled to hold it. */
    private static void tank(PartMesh out, double volume, double x, double bottom, double len, double h, double w, int color) {
        double room = Math.max(len * h * w, 1e-6), f = Math.min(Math.cbrt(volume / room), 1);
        double l = len * f, hh = h * f, ww = w * f;
        out.box(new Vec3d(x - l / 2, bottom, -ww / 2), new Vec3d(x + l / 2, bottom + hh, ww / 2), color);
    }
}
