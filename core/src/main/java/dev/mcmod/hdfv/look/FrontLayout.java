package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyFrontDesign;
import dev.mcmod.hdfv.part.body.BodyFrontDesign.Bumper;
import dev.mcmod.hdfv.part.body.BodyFrontDesign.Fog;
import dev.mcmod.hdfv.part.body.BodyFrontDesign.Grille;
import dev.mcmod.hdfv.part.body.BodyFrontDesign.Headlamps;
import dev.mcmod.hdfv.part.body.BodyFrontDesign.Intake;
import dev.mcmod.hdfv.part.body.BodyModuleDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.18: where a car's front end (BodyFrontDesign) goes, from the body's shape (body frame: x from the rear end, y up, z
 * across; port is -z). Everything about the front's geometry is here; BodyModel only draws it.
 * <ul>
 * <li>resolve: the page's AUTO choices and blank numbers made concrete from the front's shape (the share of the frontal
 *   area its flat end has: a rounded nose like the sample saloon's, a square front like a small box's).</li>
 * <li>Face: the flat front (the nose's end station inside its rolled edge, LampLayout.rollOver) and the bumper on it
 *   (how high its crease is, how far it stands out, back to the front wheel's arch).</li>
 * <li>The pieces as KitLayout.Panels: the bumper's shell round the nose's lower edge (KitLayout.Edge, the same line the
 *   kit's dam hangs from), the grille between the headlamps (its dark recess, slats or a mesh, its surround, a badge),
 *   the intake and the fog lamps' recesses in the bumper, a bulge on the hood.</li>
 * <li>The lamps as LampSpecs (headlamps and fog lamps; LampLayout.auto takes them for a car's front) and a wrap-round
 *   cluster's lens and housing round a CLUSTER lamp (cluster): from the front's inner edge round the rolled corner and
 *   back along the side.</li>
 * </ul>
 */
public final class FrontLayout {
    private FrontLayout() {}

    /** A front at least this share of the frontal area is square (a small box's) rather than rounded (a saloon's). */
    public static final double BOXY_SHARE = 0.45;
    /** A wrap-round cluster: its height as a share of its length along the front and round the corner; the bulb's place in it. */
    public static final double CLUSTER_ASPECT = 0.25, CLUSTER_BULB = 0.35;
    /** A number plate's size, m (EU). */
    public static final double PLATE_W = 0.52, PLATE_H = 0.11;

    /** The page's choices and numbers with nothing automatic left: shares of the front as BodyFrontDesign says. */
    public record Resolved(Grille grille, double grilleWidth, double grilleHeight, int slats, int grilleColour, boolean emblem,
                           Headlamps headlamps, double lampWidth, double lampHeight, double wrapM,
                           Bumper bumper, double bumperHeight, double bumperOutM, int bumperColour,
                           Intake intake, double intakeHeightM, Fog fog, double bulgeM, boolean face, boolean boxy, boolean cab) {}

    /** Whether the body's nose is a car's (its first module a hood, a passenger cell, a trunk or a cab; not a deck): only those have a front end. */
    public static boolean car(BodyDesign d, BodySurface s) {
        BodyShape.Station st = s.stations.get(s.xs.length - 1);
        BodyModuleDesign.Kind k = d.modules().get(Math.min(st.module(), d.modules().size() - 1)).kind();
        return k.family == BodyModuleDesign.Family.CAR && k != BodyModuleDesign.Kind.DECK;
    }

    /** The page made concrete, or null for a body without a car's nose. */
    public static Resolved resolve(BodyDesign d, BodySurface s) {
        if (!car(d, s)) { return null; }
        BodyFrontDesign f = d.front();
        BodyShape.Station st = s.stations.get(s.xs.length - 1);
        double share = st.areaM2() / Math.max(s.shape.particulars().frontalAreaM2(), 1e-9);
        BodyModuleDesign nose = d.modules().get(Math.min(st.module(), d.modules().size() - 1));
        boolean cab = nose.kind() == BodyModuleDesign.Kind.CAB_OVER;
        boolean face = share >= LampLayout.FACE_SHARE, boxy = cab || share >= BOXY_SHARE;
        Grille grille = f.grille() != Grille.AUTO ? f.grille() : !face ? Grille.NONE : cab ? Grille.PANEL : boxy ? Grille.FULL_WIDTH : Grille.SLATS;
        Headlamps lamps = f.headlamps() != Headlamps.AUTO ? f.headlamps() : !face ? Headlamps.NONE : boxy ? Headlamps.SQUARE : Headlamps.WRAP;
        Bumper bumper = f.bumper() != Bumper.AUTO ? f.bumper() : cab ? Bumper.STEP : boxy ? Bumper.BLACK : Bumper.BODY;
        Intake intake = f.intake() != Intake.AUTO ? f.intake() : !face || cab ? Intake.NONE : boxy ? Intake.SLOT : Intake.WIDE;
        Fog fog = f.fog() != Fog.AUTO ? f.fog() : !face ? Fog.NONE : boxy ? Fog.SQUARE : Fog.ROUND;
        double grilleWidth = or(f.grilleWidth(), switch (grille) { case FULL_WIDTH -> 1.0; case PANEL -> 0.9; default -> 0.92; });
        double grilleHeight = or(f.grilleHeight(), switch (grille) { case FULL_WIDTH, PANEL -> 1.0; case SPLIT -> 0.95; default -> 0.85; });
        int slats = f.grilleSlats() > 0 ? f.grilleSlats() : switch (grille) { case FULL_WIDTH -> 4; case PANEL -> 7; case SPLIT -> 5; default -> 3; };
        double lampWidth = or(f.lampWidth(), switch (lamps) { case WRAP -> 0.55; case TWIN_ROUND -> 0.42; default -> 0.3; });
        double lampHeight = or(f.lampHeight(), lamps == Headlamps.WRAP ? 0.9 : 0.6);
        double wrap = or(f.lampWrapM(), lamps == Headlamps.WRAP ? 0.22 : 0);
        double bumperHeight = or(f.bumperHeight(), cab ? 0.16 : boxy ? 0.38 : 0.48);
        double out = or(f.bumperOutM(), switch (bumper) { case BLACK -> 0.045; case CHROME -> 0.035; case STEP -> 0.08; default -> 0.03; });
        double intakeH = or(f.intakeHeightM(), switch (intake) { case SLOT -> 0.035; case MESH -> 0.12; default -> 0.10; });
        int grilleColour = f.grilleColor().isEmpty() ? -1 : RecordEditor.argb(f.grilleColor());
        int bumperColour = f.bumperColor().isEmpty() ? -1 : RecordEditor.argb(f.bumperColor());
        return new Resolved(grille, grilleWidth, grilleHeight, slats, grilleColour, f.emblem(), lamps, lampWidth, lampHeight, wrap, bumper, bumperHeight,
                out, bumperColour, intake, intakeH, fog, f.bulgeM(), face, boxy, cab);
    }

    private static double or(double v, double fallback) { return Double.isNaN(v) ? fallback : v; }

    /**
     * The flat front and the bumper on it: the end station's index, the flat face's plane (x) and the rolled edge's
     * radius, the end's bottom and top, the face's usable bottom and top (inside the roll), the bumper's crease (y), how
     * far it stands out (0 without one) and how far back along the sides it reaches (x, ahead of the front arch).
     */
    public record Face(int end, double x, double roll, double bottom, double top, double faceBottom, double faceTop, double bumperTop, double bumperOut,
                       double bumperBack) {
        public boolean bumper() { return bumperOut > 0; }
        /** Half the flat face's width at a height (inside its rolled edge). */
        public double half(BodyDesign d, BodySurface s, double y) { return LampLayout.half(d, s, end, Math.clamp(y, bottom, top)); }
    }

    /** The face of a car's front, or null without one. */
    public static Face face(BodyDesign d, BodySurface s) { return face(d, s, resolve(d, s)); }

    static Face face(BodyDesign d, BodySurface s, Resolved r) {
        if (r == null) { return null; }
        int end = s.xs.length - 1;
        BodyShape.Station st = s.stations.get(end);
        double roll = LampLayout.rollOver(d, st), b = st.bottomM(), t = st.topM();
        double crease = b + r.bumperHeight() * (t - b);
        List<Vec3d> axles = BodyLayout.axles(d, s.shape);
        double back = axles.isEmpty() ? s.front() - 0.4 : axles.get(0).x() + d.settings().archRadiusM() + 0.04;
        back = Math.clamp(back, s.front() - 1.0, s.front() - 0.1);
        double out = r.bumper() == Bumper.NONE ? 0 : r.bumperOutM();
        return new Face(end, st.xM() + roll, roll, b, t, b + roll, t - roll, crease, out, back);
    }

    // ---------------------------------------------------------------- the pieces

    /**
     * The front's pieces (KitLayout.Panels: bumper, bumper_edge, grille_recess, slat_chrome, slat_dark, grille_frame,
     * emblem, intake, intake_bar, fog_recess, bulge), the plate's centre (null: none) and the face; empty for a body
     * without a car's nose.
     */
    public record Pieces(List<KitLayout.Panel> panels, Vec3d plate, Face face, Resolved resolved) {
        public static final Pieces NONE = new Pieces(List.of(), null, null, null);
    }

    public static Pieces of(BodyDesign d, BodySurface s) {
        Resolved r = resolve(d, s);
        if (r == null) { return Pieces.NONE; }
        Face f = face(d, s, r);
        List<KitLayout.Panel> panels = new ArrayList<>();
        Vec3d plate = null;
        if (f.bumper()) {
            KitLayout.Edge e = KitLayout.Edge.of(d, s, f.bumperBack());
            bumper(d, s, f, e, panels);
            if (r.face()) {
                double yPlate = Math.max(f.bottom() + 0.6 * (f.bumperTop() - f.bottom()), f.faceBottom() + 0.5 * PLATE_H);
                plate = new Vec3d(f.x() + f.bumperOut(), Math.min(yPlate, f.bumperTop() - 0.5 * PLATE_H - 0.008), 0);
                intake(d, s, f, r, plate, panels);
                fogRecesses(d, s, f, r, panels);
            }
        }
        if (r.cab()) { windscreen(d, s, f, panels); }
        if (f.bumper() && r.bumper() == Bumper.STEP) { stepPads(d, s, f, panels); }
        if (r.cab()) { sideSteps(d, s, panels); }
        if (r.face() && r.grille() != Grille.NONE) { grille(d, s, f, r, panels); }
        if (r.bulgeM() > 0) { bulge(d, s, r.bulgeM(), panels); }
        return new Pieces(panels, plate, f, r);
    }

    /**
     * The bumper: a shell standing bumperOut off the skin from the nose's lower edge (KitLayout.Edge: round the tip and
     * back along each side to ahead of the front arch) up to its crease, closed by a ledge along the crease, one along
     * its foot and a cap at each end.
     */
    static void bumper(BodyDesign d, BodySurface s, Face f, KitLayout.Edge e, List<KitLayout.Panel> panels) {
        int n = e.points().size(), rows = 4;
        Vec3d[][] skin = new Vec3d[n][rows + 1], shell = new Vec3d[n][rows + 1];
        boolean[] on = new boolean[n];
        for (int i = 0; i < n; i++) {
            Vec3d p0 = e.at(i), o = e.out(i);
            boolean front = p0.x() > s.front() + 1e-9;
            double y0 = p0.y(), y1 = Math.min(f.bumperTop(), front ? f.faceTop() : s.top(Math.min(p0.x(), s.front())) - 0.05);
            on[i] = y1 > y0 + 0.01;
            if (!on[i]) { continue; }
            double h0 = front ? Math.max(f.half(d, s, Math.max(y0, f.faceBottom())), 0.02) : 1;
            for (int k = 0; k <= rows; k++) {
                double y = y0 + (y1 - y0) * k / rows;
                Vec3d p;
                if (front) {
                    double scale = f.half(d, s, Math.max(y, f.faceBottom())) / h0;
                    p = new Vec3d(f.x(), y, p0.z() * scale);
                } else {
                    p = KitLayout.skin(s, p0.x(), y, p0.z() < 0 ? -1 : 1);
                }
                skin[i][k] = p;
                shell[i][k] = p.add(o.scale(f.bumperOut()));
            }
        }
        List<Vec3d[]> face = new ArrayList<>(), edge = new ArrayList<>();
        for (int i = 0; i + 1 < n; i++) {
            if (!on[i] || !on[i + 1]) { continue; }
            for (int k = 0; k < rows; k++) { face.add(new Vec3d[]{shell[i][k], shell[i + 1][k], shell[i + 1][k + 1], shell[i][k + 1]}); }
            edge.add(new Vec3d[]{skin[i][rows], skin[i + 1][rows], shell[i + 1][rows], shell[i][rows]});
            edge.add(new Vec3d[]{skin[i][0], shell[i][0], shell[i + 1][0], skin[i + 1][0]});
        }
        for (int i : new int[]{first(on), last(on)}) {
            if (i < 0) { continue; }
            for (int k = 0; k < rows; k++) { edge.add(new Vec3d[]{skin[i][k], skin[i][k + 1], shell[i][k + 1], shell[i][k]}); }
        }
        panels.add(new KitLayout.Panel("bumper", face));
        panels.add(new KitLayout.Panel("bumper_edge", edge));
    }

    private static int first(boolean[] on) { for (int i = 0; i < on.length; i++) { if (on[i]) { return i; } } return -1; }
    private static int last(boolean[] on) { for (int i = on.length - 1; i >= 0; i--) { if (on[i]) { return i; } } return -1; }

    /** The intake low in the bumper's front, under the plate: a dark mouth with bars, or a slot. */
    static void intake(BodyDesign d, BodySurface s, Face f, Resolved r, Vec3d plate, List<KitLayout.Panel> panels) {
        if (r.intake() == Intake.NONE) { return; }
        double x = f.x() + f.bumperOut() + 0.002, top = plate.y() - 0.5 * PLATE_H - 0.012, bottom = Math.max(f.faceBottom() - 0.5 * f.roll(), top - r.intakeHeightM());
        if (!(top - bottom > 0.01)) { return; }
        double hw = (r.intake() == Intake.SLOT ? 0.55 : 0.75) * f.half(d, s, bottom);
        List<Vec3d[]> mouth = new ArrayList<>(), bars = new ArrayList<>();
        mouth.add(flat(x, -hw, hw, bottom, top));
        if (r.intake() == Intake.MESH || r.intake() == Intake.WIDE) {
            int count = r.intake() == Intake.MESH ? 6 : 2;
            for (int k = 1; k <= count; k++) {
                double y = bottom + (top - bottom) * k / (count + 1);
                bars.addAll(box(new Vec3d(x, y - 0.003, -hw), new Vec3d(x + 0.008, y + 0.003, hw)));
            }
            if (r.intake() == Intake.MESH) {
                for (int k = 1; k < 10; k++) {
                    double z = -hw + 2 * hw * k / 10;
                    bars.addAll(box(new Vec3d(x, bottom, z - 0.002), new Vec3d(x + 0.006, top, z + 0.002)));
                }
            }
        }
        panels.add(new KitLayout.Panel("intake", mouth));
        if (!bars.isEmpty()) { panels.add(new KitLayout.Panel("intake_bar", bars)); }
    }

    /** The fog lamps' recesses in the bumper's front corners (the lamps themselves are LampSpecs: lamps). */
    static void fogRecesses(BodyDesign d, BodySurface s, Face f, Resolved r, List<KitLayout.Panel> panels) {
        if (r.fog() == Fog.NONE) { return; }
        Vec3d at = fogAt(d, s, f);
        double w = r.fog() == Fog.ROUND ? 0.085 : 0.11, h = r.fog() == Fog.ROUND ? 0.085 : 0.07, x = f.x() + f.bumperOut() + 0.002;
        List<Vec3d[]> recess = new ArrayList<>();
        for (int side : new int[]{-1, 1}) {
            double z = side * Math.abs(at.z());
            recess.add(flat(x, z - 0.5 * w - 0.015, z + 0.5 * w + 0.015, at.y() - 0.5 * h - 0.012, at.y() + 0.5 * h + 0.012));
        }
        panels.add(new KitLayout.Panel("fog_recess", recess));
    }

    /** Where the port fog lamp's centre is: low in the bumper's front, near its corner. */
    static Vec3d fogAt(BodyDesign d, BodySurface s, Face f) {
        double y = Math.max(f.faceBottom(), f.bottom() + 0.4 * (f.bumperTop() - f.bottom()));
        return new Vec3d(f.x() + f.bumperOut(), y, -0.78 * f.half(d, s, y));
    }

    /** The headlamps' band on the face above the bumper: its bottom and top. */
    static double[] lampBand(Face f, Resolved r) {
        double lo = Math.max(f.bumperTop(), f.faceBottom()) + 0.012, hi = Math.max(lo + 0.04, f.faceTop() - 0.005);
        return new double[]{lo, lo + r.lampHeight() * (hi - lo)};
    }

    /**
     * A cab-over cab's windscreen on its flat front (0.18): from its belt (the cab's beltFraction, less 5 cm) to 12 cm
     * under the top, inside A pillars 8 % of the front's half each side; dark glass with a rubber surround and two wipers
     * parked at its foot. Drawn on the front, not cut through it (seen from inside the cab the front is a back face).
     */
    static double[] windscreenBand(BodyDesign d, BodySurface s, Face f) {
        BodyShape.Station st = s.stations.get(f.end());
        BodyModuleDesign nose = d.modules().get(Math.min(st.module(), d.modules().size() - 1));
        double y0 = Math.max(f.faceBottom() + 0.1, f.bottom() + nose.beltFraction() * (f.top() - f.bottom()) - 0.05), y1 = f.faceTop() - 0.12;
        return new double[]{y0, Math.max(y1, y0 + 0.2)};
    }

    static void windscreen(BodyDesign d, BodySurface s, Face f, List<KitLayout.Panel> panels) {
        double[] band = windscreenBand(d, s, f);
        int rows = 8;
        double x = f.x() + 0.004;
        List<Vec3d[]> glass = new ArrayList<>(), rubber = new ArrayList<>(), wipers = new ArrayList<>();
        for (int k = 0; k < rows; k++) {
            double ya = band[0] + (band[1] - band[0]) * k / rows, yb = band[0] + (band[1] - band[0]) * (k + 1) / rows;
            double ha = 0.92 * f.half(d, s, ya), hb = 0.92 * f.half(d, s, yb);
            glass.add(new Vec3d[]{new Vec3d(x, ya, -ha), new Vec3d(x, ya, ha), new Vec3d(x, yb, hb), new Vec3d(x, yb, -hb)});
            for (int side : new int[]{-1, 1}) {
                rubber.add(new Vec3d[]{new Vec3d(x + 0.002, ya, side * ha), new Vec3d(x + 0.002, ya, side * (ha + 0.03)),
                        new Vec3d(x + 0.002, yb, side * (hb + 0.03)), new Vec3d(x + 0.002, yb, side * hb)});
            }
        }
        double h0 = 0.92 * f.half(d, s, band[0]), h1 = 0.92 * f.half(d, s, band[1]);
        rubber.add(flat(x + 0.002, -h0 - 0.03, h0 + 0.03, band[0] - 0.03, band[0]));
        rubber.add(flat(x + 0.002, -h1 - 0.03, h1 + 0.03, band[1], band[1] + 0.03));
        for (int side : new int[]{-1, 1}) {
            double z0 = side * 0.08 * h0, z1 = side * 0.85 * h0;
            wipers.addAll(box(new Vec3d(x + 0.004, band[0] + 0.03, Math.min(z0, z1)), new Vec3d(x + 0.016, band[0] + 0.045, Math.max(z0, z1))));
        }
        panels.add(new KitLayout.Panel("windscreen", glass));
        panels.add(new KitLayout.Panel("windscreen_frame", rubber));
        panels.add(new KitLayout.Panel("wiper", wipers));
    }

    /** A truck's stepped bumper: a tread let into its front each side. */
    static void stepPads(BodyDesign d, BodySurface s, Face f, List<KitLayout.Panel> panels) {
        double y = f.bottom() + 0.45 * (f.bumperTop() - f.bottom()), x = f.x() + f.bumperOut() + 0.002, half = f.half(d, s, y);
        List<Vec3d[]> treads = new ArrayList<>();
        for (int side : new int[]{-1, 1}) {
            double zc = side * 0.42 * half;
            treads.add(flat(x, zc - 0.17, zc + 0.17, y - 0.04, y + 0.04));
        }
        panels.add(new KitLayout.Panel("step", treads));
    }

    /** A cab's steps under its doors, behind the front wheel: two treads hanging under the cab's floor each side. */
    static void sideSteps(BodyDesign d, BodySurface s, List<KitLayout.Panel> panels) {
        List<Vec3d> axles = BodyLayout.axles(d, s.shape);
        if (axles.isEmpty()) { return; }
        double r = d.settings().archRadiusM(), x1 = axles.get(0).x() - r - 0.06, x0 = Math.max(x1 - 0.42, s.rear());
        if (!(x1 - x0 > 0.15)) { return; }
        double floor = s.bottom(0.5 * (x0 + x1));
        List<Vec3d[]> treads = new ArrayList<>();
        for (int side : new int[]{-1, 1}) {
            double skin = 0.5 * s.width(0.5 * (x0 + x1)) - 0.04;
            for (int k = 1; k <= 2; k++) {
                double y = floor - 0.3 * k;
                treads.addAll(box(new Vec3d(x0, y, side * (skin - 0.02)), new Vec3d(x1, y + 0.04, side * (skin - 0.02 - 0.26 + 0.06 * k))));
                treads.addAll(box(new Vec3d(x0, y, side * (skin - 0.02)), new Vec3d(x0 + 0.03, floor, side * (skin - 0.05))));
                treads.addAll(box(new Vec3d(x1 - 0.03, y, side * (skin - 0.02)), new Vec3d(x1, floor, side * (skin - 0.05))));
            }
        }
        panels.add(new KitLayout.Panel("step", treads));
    }

    /** The grille between the headlamps, above the bumper: a dark recess, slats or a mesh over it, a surround, a badge. */
    static void grille(BodyDesign d, BodySurface s, Face f, Resolved r, List<KitLayout.Panel> panels) {
        double[] band = lampBand(f, r);
        double y0 = band[0] + 0.004, y1 = band[0] + r.grilleHeight() * (band[1] - band[0]);
        if (r.grille() == Grille.PANEL) {
            // a cab's: from over the bumper to under the windscreen (or half the front without one)
            y0 = Math.max(f.bumperTop(), f.faceBottom()) + 0.03;
            double top = r.cab() ? windscreenBand(d, s, f)[0] - 0.06 : y0 + 0.5 * (f.faceTop() - y0);
            y1 = y0 + r.grilleHeight() * (top - y0);
        }
        double inner = innerEdge(d, s, f, r, 0.5 * (y0 + y1));
        double hw = Math.min(inner - 0.01, f.half(d, s, y0) - 0.02) * (r.grille() == Grille.FULL_WIDTH ? 1 : r.grilleWidth());
        if (!(hw > 0.04 && y1 - y0 > 0.02)) { return; }
        double x = f.x();
        List<Vec3d[]> recess = new ArrayList<>(), slats = new ArrayList<>(), frame = new ArrayList<>(), emblem = new ArrayList<>();
        boolean chrome = r.grille() != Grille.FULL_WIDTH && r.grille() != Grille.PANEL;
        if (r.grille() == Grille.SPLIT) {
            double gap = 0.025;
            for (int side : new int[]{-1, 1}) {
                double z0 = side < 0 ? -hw : gap, z1 = side < 0 ? -gap : hw;
                recess.add(flat(x + 0.001, z0, z1, y0, y1));
                for (int k = 1; k < r.slats(); k++) {
                    double z = z0 + (z1 - z0) * k / r.slats();
                    slats.addAll(box(new Vec3d(x, y0, z - 0.003), new Vec3d(x + 0.01, y1, z + 0.003)));
                }
                frame.addAll(surround(x, z0, z1, y0, y1, 0.008));
            }
        } else {
            recess.add(flat(x + 0.001, -hw, hw, y0, y1));
            if (r.grille() == Grille.MESH) {
                for (int k = 1; k < 5; k++) {
                    double y = y0 + (y1 - y0) * k / 5;
                    slats.addAll(box(new Vec3d(x, y - 0.002, -hw), new Vec3d(x + 0.008, y + 0.002, hw)));
                }
                for (int k = 1; k < 16; k++) {
                    double z = -hw + 2 * hw * k / 16;
                    slats.addAll(box(new Vec3d(x, y0, z - 0.002), new Vec3d(x + 0.008, y1, z + 0.002)));
                }
            } else {
                double pitch = (y1 - y0) / (r.slats() + 1), t = Math.min(0.012, 0.35 * pitch);
                for (int k = 1; k <= r.slats(); k++) {
                    double y = y0 + pitch * k;
                    slats.addAll(box(new Vec3d(x, y - 0.5 * t, -hw + 0.006), new Vec3d(x + 0.012, y + 0.5 * t, hw - 0.006)));
                }
            }
            if (r.grille() != Grille.PANEL) { frame.addAll(surround(x, -hw, hw, y0, y1, 0.009)); }
        }
        if (r.emblem() && r.grille() != Grille.PANEL) {
            double rad = Math.min(0.045, 0.42 * (y1 - y0)), yc = 0.5 * (y0 + y1);
            int seg = 12;
            for (int k = 0; k < seg; k++) {
                double a0 = 2 * Math.PI * k / seg, a1 = 2 * Math.PI * (k + 1) / seg;
                Vec3d c = new Vec3d(x + 0.018, yc, 0);
                emblem.add(new Vec3d[]{c, c.add(new Vec3d(0, rad * Math.sin(a0), rad * Math.cos(a0))), c.add(new Vec3d(0, rad * Math.sin(a1), rad * Math.cos(a1))), c});
            }
        }
        panels.add(new KitLayout.Panel("grille_recess", recess));
        panels.add(new KitLayout.Panel(chrome ? "slat_chrome" : "slat_dark", slats));
        if (!frame.isEmpty()) { panels.add(new KitLayout.Panel(chrome ? "grille_frame" : "slat_dark", frame)); }
        if (!emblem.isEmpty()) { panels.add(new KitLayout.Panel("emblem", emblem)); }
    }

    /** How far the grille may reach across: to the headlamps' inner edge (or most of the face without lamps on it). */
    static double innerEdge(BodyDesign d, BodySurface s, Face f, Resolved r, double y) {
        double half = f.half(d, s, y);
        return switch (r.headlamps()) {
            case WRAP, SQUARE, ROUND, TWIN_ROUND -> half * (1 - r.lampWidth());
            default -> 0.8 * half;
        };
    }

    /** A bulge on the hood, rising from its skin to heightM at its middle (the body's colour). */
    static void bulge(BodyDesign d, BodySurface s, double heightM, List<KitLayout.Panel> panels) {
        BodyShape shape = s.shape;
        double x0 = shape.moduleRearX(0) + 0.15, x1 = shape.moduleFrontX(0) - 0.2;
        if (!(x1 - x0 > 0.2)) { return; }
        int nx = 10, nz = 8;
        Vec3d[][] g = new Vec3d[nx + 1][nz + 1];
        for (int i = 0; i <= nx; i++) {
            double x = x0 + (x1 - x0) * i / nx, w = 0.32 * s.width(x);
            for (int j = 0; j <= nz; j++) {
                double v = -1 + 2.0 * j / nz, z = v * w;
                double rise = heightM * Math.sin(Math.PI * i / nx) * Math.pow(Math.cos(0.5 * Math.PI * v), 2);
                g[i][j] = new Vec3d(x, KitLayout.top(s, x, z) + rise - 0.002, z);
            }
        }
        List<Vec3d[]> q = new ArrayList<>();
        for (int i = 0; i < nx; i++) { for (int j = 0; j < nz; j++) { q.add(new Vec3d[]{g[i][j], g[i][j + 1], g[i + 1][j + 1], g[i + 1][j]}); } }
        panels.add(new KitLayout.Panel("bulge", q));
    }

    // ---------------------------------------------------------------- the lamps

    /**
     * The front's lamps as specs (port lamps, each a pair): dipped headlamps (they cast the main beam too) by the page's
     * style above the bumper, fog lamps in the bumper's corners. Empty without a face for them (a pointed nose's lamps
     * are LampLayout's on its skin).
     */
    public static List<LampSpec> lamps(BodyDesign d, BodySurface s) {
        Resolved r = resolve(d, s);
        List<LampSpec> out = new ArrayList<>();
        if (r == null || !r.face()) { return out; }
        Face f = face(d, s, r);
        double h = f.top() - f.bottom();
        double[] band = lampBand(f, r);
        double yc = 0.5 * (band[0] + band[1]), up = (yc - f.bottom()) / h, half = f.half(d, s, yc), lampH = band[1] - band[0];
        if (r.cab() && f.bumper()) {
            // a cab-over's lamps sit in its bumper's corners (the front above is windscreen and grille)
            double yb = f.bottom() + 0.55 * (f.bumperTop() - f.bottom()), hb = f.half(d, s, yb);
            out.add(spec(LampRules.Role.HEAD_LOW, LampSpec.Style.SQUARE, -0.84, (yb - f.bottom()) / h, Math.min(0.24, 0.25 * hb)));
            if (r.fog() != Fog.NONE) {
                out.add(spec(LampRules.Role.FOG, r.fog() == Fog.ROUND ? LampSpec.Style.ROUND : LampSpec.Style.SQUARE, -0.66, (yb - f.bottom()) / h, 0.1));
            }
            return out;
        }
        switch (r.headlamps()) {
            case WRAP -> {
                double inner = half * (1 - r.lampWidth()), size = (half - inner) + 0.5 * Math.PI * f.roll() + r.wrapM();
                size = Math.max(size, lampH / CLUSTER_ASPECT * 0.6);
                double bulb = Math.min(inner + CLUSTER_BULB * size, 0.9 * half);
                out.add(spec(LampRules.Role.HEAD_LOW, LampSpec.Style.CLUSTER, -bulb / half, up, Math.min(size, lampH / CLUSTER_ASPECT)));
            }
            case SQUARE -> {
                double w = r.lampWidth() * half;
                out.add(spec(LampRules.Role.HEAD_LOW, LampSpec.Style.SQUARE, -(1 - 0.5 * r.lampWidth()), up, Math.min(w, lampH / 0.6)));
            }
            case ROUND -> {
                double w = Math.min(r.lampWidth() * half, lampH);
                out.add(spec(LampRules.Role.HEAD_LOW, LampSpec.Style.ROUND, -(1 - 0.5 * r.lampWidth()), up, w));
            }
            case TWIN_ROUND -> {
                double w = Math.min(0.5 * r.lampWidth() * half, lampH);
                out.add(spec(LampRules.Role.HEAD_LOW, LampSpec.Style.ROUND, -(1 - 0.25 * r.lampWidth()), up, w));
                out.add(spec(LampRules.Role.HEAD_HIGH, LampSpec.Style.ROUND, -(1 - 0.75 * r.lampWidth()), up, w));
            }
            default -> { }
        }
        if (f.bumper() && r.fog() != Fog.NONE) {
            Vec3d at = fogAt(d, s, f);
            double across = at.z() / f.half(d, s, at.y());
            out.add(spec(LampRules.Role.FOG, r.fog() == Fog.ROUND ? LampSpec.Style.ROUND : LampSpec.Style.SQUARE, across, (at.y() - f.bottom()) / h,
                    r.fog() == Fog.ROUND ? 0.085 : 0.11));
        }
        return out;
    }

    private static LampSpec spec(LampRules.Role role, LampSpec.Style style, double across, double up, double size) {
        return new LampSpec(role, style, LampSpec.Face.FRONT, 0, 0, Math.clamp(across, -1, 1), Math.clamp(up, 0, 1), 0, true,
                Math.clamp(size, LampSpec.MIN_SIZE_M, LampSpec.MAX_SIZE_M), "", "", 0, 0);
    }

    /**
     * A wrap-round cluster's housing (its back on the skin and its rim), lens (standing off the skin, a little domed) and
     * bulbs: round a CLUSTER lamp on a front or rear face from CLUSTER_BULB of its length inboard of the lamp to the rest
     * outboard, round the rolled corner and back along the side; CLUSTER_ASPECT of its length tall. On a side, a patch of
     * the skin along the body. Points in the body frame.
     */
    public record Cluster(List<Vec3d[]> housing, List<Vec3d[]> lens, List<Vec3d> bulbs, Vec3d out) {}

    public static Cluster cluster(BodyDesign d, BodySurface s, LampSpec l, boolean mirror) {
        int ns = 12, ny = 3;
        Vec3d[][] back = new Vec3d[ns + 1][ny + 1], glass = new Vec3d[ns + 1][ny + 1];
        List<Vec3d> bulbs = new ArrayList<>();
        Vec3d out;
        double size = l.sizeM(), tall = CLUSTER_ASPECT * size;
        if (l.face() == LampSpec.Face.SIDE) {
            double t = mirror ? 2 * Math.PI - l.t() : l.t();
            Vec3d c = s.point(l.x(), t);
            int side = c.z() < 0 ? -1 : 1;
            for (int i = 0; i <= ns; i++) {
                double x = Math.clamp(l.x() - 0.5 * size + size * i / ns, s.rear(), s.front());
                for (int k = 0; k <= ny; k++) {
                    double y = c.y() - 0.5 * tall + tall * k / ny;
                    Vec3d p = KitLayout.skin(s, x, y, side), n = horizontal(s.normal(x, s.sideAngle(x, y, side > 0)));
                    back[i][k] = p.add(n.scale(0.003));
                    glass[i][k] = p.add(n.scale(0.014 + 0.005 * Math.sin(Math.PI * i / ns) * Math.sin(Math.PI * k / ny)));
                }
            }
            out = horizontal(s.normal(l.x(), t));
            bulbs.add(c.add(out.scale(0.006)));
        } else {
            boolean front = l.face() == LampSpec.Face.FRONT;
            int end = front ? s.xs.length - 1 : 0, sign = front ? 1 : -1;
            BodyShape.Station st = s.stations.get(end);
            double roll = LampLayout.rollOver(d, st), yc = st.bottomM() + l.up() * (st.topM() - st.bottomM());
            double across = mirror ? -l.across() : l.across();
            int side = across < 0 ? -1 : 1;
            double halfC = LampLayout.half(d, s, end, yc), sb = Math.abs(across) * halfC;
            double sA = Math.max(0, sb - CLUSTER_BULB * size), sB = sb + (1 - CLUSTER_BULB) * size;
            double y0 = Math.max(yc - 0.5 * tall, st.bottomM() + roll), y1 = Math.min(yc + 0.5 * tall, st.topM() - roll);
            for (int i = 0; i <= ns; i++) {
                double sp = sA + (sB - sA) * i / ns, dome = Math.sin(Math.PI * i / ns);
                for (int k = 0; k <= ny; k++) {
                    double y = y0 + (y1 - y0) * k / ny;
                    // the lens's top runs a little lower toward its outer end (a cluster tapers round the corner)
                    if (k == ny) { y = y1 - 0.25 * (y1 - y0) * Math.max(0, (sp - halfC) / Math.max(sB - halfC, 1e-6)); }
                    Vec3d[] pn = around(d, s, end, sign, roll, y, sp, side);
                    back[i][k] = pn[0].add(pn[1].scale(0.003));
                    glass[i][k] = pn[0].add(pn[1].scale(0.014 + 0.006 * dome * Math.sin(Math.PI * k / ny)));
                }
            }
            out = new Vec3d(sign, 0, 0);
            Vec3d bulb = new Vec3d(st.xM() + sign * roll, yc, side * sb);
            bulbs.add(bulb.add(out.scale(0.006)));
            double second = sb + 0.32 * size;
            if (second < halfC - 0.02 && l.role() == LampRules.Role.HEAD_LOW) { bulbs.add(new Vec3d(st.xM() + sign * roll, yc, side * second).add(out.scale(0.006))); }
        }
        List<Vec3d[]> housing = new ArrayList<>(), lens = new ArrayList<>();
        for (int i = 0; i < ns; i++) {
            for (int k = 0; k < ny; k++) {
                housing.add(new Vec3d[]{back[i][k], back[i + 1][k], back[i + 1][k + 1], back[i][k + 1]});
                lens.add(new Vec3d[]{glass[i][k], glass[i + 1][k], glass[i + 1][k + 1], glass[i][k + 1]});
            }
        }
        // the rim between the back and the lens, all round
        for (int i = 0; i < ns; i++) {
            housing.add(new Vec3d[]{back[i][0], back[i + 1][0], glass[i + 1][0], glass[i][0]});
            housing.add(new Vec3d[]{back[i][ny], glass[i][ny], glass[i + 1][ny], back[i + 1][ny]});
        }
        for (int k = 0; k < ny; k++) {
            housing.add(new Vec3d[]{back[0][k], glass[0][k], glass[0][k + 1], back[0][k + 1]});
            housing.add(new Vec3d[]{back[ns][k], back[ns][k + 1], glass[ns][k + 1], glass[ns][k]});
        }
        return new Cluster(housing, lens, bulbs, out);
    }

    /**
     * A point and its outward direction along an end's corner path at height y, sp along it from the face's middle: on
     * the flat face to its edge, round the rolled edge (a quarter circle), then back along the side's skin.
     */
    static Vec3d[] around(BodyDesign d, BodySurface s, int end, int sign, double roll, double y, double sp, int side) {
        BodyShape.Station st = s.stations.get(end);
        double hw = LampLayout.half(d, s, end, y), arc = 0.5 * Math.PI * roll;
        if (sp <= hw) { return new Vec3d[]{new Vec3d(st.xM() + sign * roll, y, side * sp), new Vec3d(sign, 0, 0)}; }
        if (sp <= hw + arc && roll > 1e-6) {
            double th = (sp - hw) / roll;
            return new Vec3d[]{new Vec3d(st.xM() + sign * roll * Math.cos(th), y, side * (hw + roll * Math.sin(th))),
                    new Vec3d(sign * Math.cos(th), 0, side * Math.sin(th))};
        }
        double x = Math.clamp(st.xM() - sign * (sp - hw - arc), s.rear(), s.front());
        Vec3d p = KitLayout.skin(s, x, y, side);
        return new Vec3d[]{p, horizontal(s.normal(x, s.sideAngle(x, y, side > 0)))};
    }

    private static Vec3d horizontal(Vec3d n) {
        Vec3d h = new Vec3d(n.x(), 0, n.z());
        return h.lengthSquared() > 1e-12 ? h.normalized() : n;
    }

    // ---------------------------------------------------------------- small shapes

    /** A flat rectangle across x (facing +x). */
    static Vec3d[] flat(double x, double z0, double z1, double y0, double y1) {
        return new Vec3d[]{new Vec3d(x, y0, z0), new Vec3d(x, y0, z1), new Vec3d(x, y1, z1), new Vec3d(x, y1, z0)};
    }

    /** A rectangle's surround: four thin bars standing proud of the face. */
    static List<Vec3d[]> surround(double x, double z0, double z1, double y0, double y1, double t) {
        List<Vec3d[]> q = new ArrayList<>();
        q.addAll(box(new Vec3d(x, y0 - t, z0 - t), new Vec3d(x + 0.014, y0, z1 + t)));
        q.addAll(box(new Vec3d(x, y1, z0 - t), new Vec3d(x + 0.014, y1 + t, z1 + t)));
        q.addAll(box(new Vec3d(x, y0, z0 - t), new Vec3d(x + 0.014, y1, z0)));
        q.addAll(box(new Vec3d(x, y0, z1), new Vec3d(x + 0.014, y1, z1 + t)));
        return q;
    }

    /** An axis-aligned box's six faces. */
    static List<Vec3d[]> box(Vec3d a, Vec3d b) {
        double x0 = Math.min(a.x(), b.x()), x1 = Math.max(a.x(), b.x()), y0 = Math.min(a.y(), b.y()), y1 = Math.max(a.y(), b.y());
        double z0 = Math.min(a.z(), b.z()), z1 = Math.max(a.z(), b.z());
        List<Vec3d[]> q = new ArrayList<>();
        q.add(new Vec3d[]{new Vec3d(x1, y0, z0), new Vec3d(x1, y0, z1), new Vec3d(x1, y1, z1), new Vec3d(x1, y1, z0)});
        q.add(new Vec3d[]{new Vec3d(x0, y0, z0), new Vec3d(x0, y1, z0), new Vec3d(x0, y1, z1), new Vec3d(x0, y0, z1)});
        q.add(new Vec3d[]{new Vec3d(x0, y1, z0), new Vec3d(x1, y1, z0), new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1)});
        q.add(new Vec3d[]{new Vec3d(x0, y0, z0), new Vec3d(x0, y0, z1), new Vec3d(x1, y0, z1), new Vec3d(x1, y0, z0)});
        q.add(new Vec3d[]{new Vec3d(x0, y0, z1), new Vec3d(x0, y1, z1), new Vec3d(x1, y1, z1), new Vec3d(x1, y0, z1)});
        q.add(new Vec3d[]{new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0), new Vec3d(x1, y1, z0), new Vec3d(x0, y1, z0)});
        return q;
    }
}
