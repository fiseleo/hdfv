package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.aero.Airfoil;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.look.WingSurface;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.air.WingSurfaceDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Wings, air propellers and rotors drawn from their designs, in their parts' frames: a wing lofted through its root and
 * tip sections' real outlines along the planform (twist about the quarter chord, sweep and dihedral of the quarter-chord
 * line), its flaps and ailerons separate pieces hinged on their hinge lines; a propeller's blades lofted along the radius
 * with their chord, blade angle and thickness, round a spinner; a rotor's blades with their twist and a little coning
 * on a hub with its mast. The pieces move with an angle: in the design screen it sweeps the control surfaces and turns
 * the blades; in the world the renderers pass the vehicle's inputs and rotor angle instead.
 */
public final class AirframeModel {
    private AirframeModel() {}

    private static final int SKIN = 0xffd7dbe0, CONTROL = 0xffc4cad1, DARK = 0xff4b5058, SPINNER = 0xffeceef0, BLADE = 0xff1d1f22, BLADE_FACE = 0xffaeb4ba,
            TIP = 0xffe8c32a;
    /** Chordwise points of a drawn section (from a 161-station outline). */
    private static final int CHORD_POINTS = 21, SPAN_STRIPS = 12;

    // ---------------------------------------------------------------- wings

    /** A deflection (rad, trailing edge down) of each control surface for a side (+1 starboard, -1 port). */
    public interface Deflections { double of(WingSurfaceDesign surface, int side); }

    /** The wing with its control surfaces at the deflections given (the world), the design screen sweeping them with the angle. */
    public static EngineModel build(WingDesign w, String highlight) {
        EngineModel m = new EngineModel(new PartMesh());
        wing(w, highlight, m.fixed, (surface, side, mesh, hingeA, hingeB) -> {
            Vec3d axis = hingeB.subtract(hingeA).normalized();
            m.piece(mesh, a -> {
                double sweep = Math.sin(a), input = surface.role() == WingSurfaceDesign.Role.FLAP ? 0.5 * (1 + sweep) : sweep;
                double delta = deflection(surface, surface.role() == WingSurfaceDesign.Role.AILERON && side > 0 ? -input : input);
                return new EngineModel.Placement(hingeA, Orientation.rotationVector(axis.scale(delta)));
            });
        });
        return m;
    }

    /** Trailing-edge-down deflection (rad) of a surface for an input -1..1. */
    public static double deflection(WingSurfaceDesign s, double input) {
        double x = Math.clamp(input, -1, 1);
        return Math.toRadians(x >= 0 ? x * s.downDeg() : x * s.upDeg());
    }

    /** What to do with a control surface's mesh (built relative to its hinge's first end): its surface, side and hinge line. */
    public interface SurfaceSink { void accept(WingSurfaceDesign surface, int side, PartMesh mesh, Vec3d hingeA, Vec3d hingeB); }

    /**
     * The wing's fixed skin into fixed and each control surface into the sink. 0.12: in the structure's paint and its
     * finish when they are given (blank and standard: the light grey as before, finished by its colour). 0.13: lofted on
     * core's WingSurface, where stickers are laid and picked.
     */
    public static void wing(WingDesign w, String highlight, PartMesh fixed, SurfaceSink sink) {
        WingSurface loft = new WingSurface(w);
        var structure = w.structure();
        // 0.13: the stickers, laid once: the fixed skin's share after the strips are known, each strip's in its mesh
        List<dev.mcmod.hdfv.look.Decals.Laid> stickers = dev.mcmod.hdfv.look.Decals.lay(loft, structure.stickers());
        List<dev.mcmod.hdfv.look.Decals.Strip> strips = new ArrayList<>();
        boolean painted = dev.mcmod.hdfv.design.RecordEditor.isColor(structure.paintColor(), false);
        int paint = painted ? dev.mcmod.hdfv.design.RecordEditor.argb(structure.paintColor()) : SKIN, control = painted ? paint : CONTROL;
        dev.mcmod.aerocore.api.mesh.Finish finish = painted || structure.paintFinish() != dev.mcmod.hdfv.part.body.BodySettingsDesign.PaintFinish.STANDARD
                ? structure.paintFinish().finish() : null;
        dev.mcmod.aerocore.api.mesh.Finish pen = fixed.finish();
        if (finish != null && highlight == null) { fixed.finish(finish); }
        int skin = "planform".equals(highlight) || "structure".equals(highlight) ? Shapes.HIGHLIGHT : paint;
        int[] sides = loft.sides();
        List<WingSurfaceDesign> surfaces = new ArrayList<>();
        for (WingSurfaceDesign s : List.of(w.trailingEdge(), w.aileron())) { if (s.active()) { surfaces.add(s); } }
        for (int side : sides) {
            // span stations: uniform, plus the surfaces' ends
            List<Double> etas = new ArrayList<>();
            for (int k = 0; k <= SPAN_STRIPS; k++) { etas.add((double) k / SPAN_STRIPS); }
            for (WingSurfaceDesign s : surfaces) { etas.add(s.fromSpan()); etas.add(s.toSpan()); }
            etas = etas.stream().distinct().sorted().toList();
            for (int k = 0; k < etas.size() - 1; k++) {
                double e0 = etas.get(k), e1 = etas.get(k + 1), em = 0.5 * (e0 + e1);
                WingSurfaceDesign cut = null;
                for (WingSurfaceDesign s : surfaces) { if (em > s.fromSpan() && em < s.toSpan()) { cut = s; } }
                double hinge = cut == null ? 1 : 1 - cut.chordFraction();
                // 0.22: the fixed skin stops a gap short of the hinge (12-20 mm), where the surface's nose turns
                Vec3d le = loft.point(em, 0, 0.5, side), aft = loft.point(em, 1, 0.5, side).subtract(le);
                double chord = aft.length(), gap = cut == null ? 0 : Math.clamp(0.012 + 0.004 * chord, 0.012, 0.02) / Math.max(chord, 0.05);
                List<Vec3d> a = outline(loft, e0, side, 0, hinge - gap), b = outline(loft, e1, side, 0, hinge - gap);
                int color = skin;
                if ("rootSection".equals(highlight) && e0 < 0.25 || "tipSection".equals(highlight) && e1 > 0.75) { color = Shapes.HIGHLIGHT; }
                skin(fixed, a, b, color);
                if (cut != null) {
                    // 0.22: the cove (the rear spar's face across the gap, dark) and, where the surface ends, the pocket's
                    // end walls (seen when it is deflected)
                    face(fixed, a.get(0), a.get(a.size() - 1), b.get(b.size() - 1), b.get(0), highlight == null ? DARK : color, aft);
                    Vec3d span = loft.point(e1, 0.5, 0.5, side).subtract(loft.point(e0, 0.5, 0.5, side));
                    if (Math.abs(e0 - cut.fromSpan()) < 1e-9) { pocketEnd(fixed, outline(loft, e0, side, hinge - gap, 1), color, span); }
                    if (Math.abs(e1 - cut.toSpan()) < 1e-9) { pocketEnd(fixed, outline(loft, e1, side, hinge - gap, 1), color, span.scale(-1)); }
                    // the control surface: hinge line from this strip's inboard end, its own loft relative to it
                    Vec3d hingeA = loft.point(e0, hinge, 0.5, side), hingeB = loft.point(e1, hinge, 0.5, side);
                    if (side < 0) { Vec3d t = hingeA; hingeA = hingeB; hingeB = t; }
                    List<Vec3d> ca = shifted(outline(loft, e0, side, hinge, 1), hingeA), cb = shifted(outline(loft, e1, side, hinge, 1), hingeA);
                    int c = highlight != null && (highlight.equals("trailingEdge") && cut == w.trailingEdge() || highlight.equals("aileron") && cut == w.aileron())
                            ? Shapes.HIGHLIGHT : control;
                    PartMesh mesh = new PartMesh();
                    // 0.22: an unpainted surface in the skin's finish (by its own colour it came out machined steel: a
                    // shiny blue next to a matte wing, once the surfaces were drawn where they belong)
                    if (highlight == null) { mesh.finish(finish != null ? finish : Looks.of(paint)); }
                    skin(mesh, ca, cb, c);
                    // 0.22: the surface's own ends capped where it ends
                    if (Math.abs(e0 - cut.fromSpan()) < 1e-9) { pocketEnd(mesh, ca, c, span.scale(-1)); }
                    if (Math.abs(e1 - cut.toSpan()) < 1e-9) { pocketEnd(mesh, cb, c, span); }
                    var strip = new dev.mcmod.hdfv.look.Decals.Strip(side, e0, e1, hinge);
                    strips.add(strip);
                    if (!stickers.isEmpty()) { dev.mcmod.hdfv.look.Decals.wingStrip(mesh, loft, stickers, strip, hingeA); }
                    sink.accept(cut, side, mesh, hingeA, hingeB);
                }
            }
            // tip cap
            List<Vec3d> end = outline(loft, 1, side, 0, 1);
            int n = end.size() / 2;
            cap(fixed, end.subList(0, n + 1), reversed(end.subList(n, end.size())), skin, new Vec3d(0, 0, side));
        }
        if (!stickers.isEmpty()) { dev.mcmod.hdfv.look.Decals.wingFixed(fixed, loft, stickers, strips); }
        fixed.finish(pen);
        if (highlight == null) { fittings(w, loft, fixed, skin); }
    }

    /** 0.22: an end wall over a section outline from a chord fraction to the trailing edge (upper and lower paired), facing a way. */
    private static void pocketEnd(PartMesh out, List<Vec3d> section, int color, Vec3d outward) {
        int n = section.size() / 2;
        cap(out, section.subList(0, n + 1), reversed(section.subList(n, section.size())), color, outward);
    }

    /**
     * 0.22: what hangs under a wing: a pitot on a wing with ailerons (the port half's when it has two, at 62 % of the
     * span: the kit's, the nearest two levels), and on a wing whose flaps are 1.8 m of chord or more, two canoe fairings
     * over each flap's tracks (8-sided, the nearest three levels).
     */
    private static void fittings(WingDesign w, WingSurface loft, PartMesh fixed, int skin) {
        int[] sides = loft.sides();
        int tags = fixed.tags();
        dev.mcmod.aerocore.api.mesh.Finish pen = fixed.finish();
        if (w.aileron().active()) {
            int side = sides[0];
            double eta = 0.62;
            Vec3d le = loft.point(eta, 0, 0.5, side), forward = le.subtract(loft.point(eta, 1, 0.5, side));
            double chord = forward.length();
            Details.place(fixed, DetailKit.Part.PITOT, loft.point(eta, 0.18, 0, side), new Vec3d(0, -1, 0), forward, Math.clamp(0.1 * chord, 0.12, 0.3), 2, true);
        }
        WingSurfaceDesign flap = w.trailingEdge();
        if (flap.active() && flap.role() == WingSurfaceDesign.Role.FLAP) {
            fixed.levels(0, 2);
            for (int side : sides) {
                for (double k : new double[]{1 / 3.0, 2 / 3.0}) {
                    double eta = flap.fromSpan() + (flap.toSpan() - flap.fromSpan()) * k, hinge = 1 - flap.chordFraction();
                    Vec3d le = loft.point(eta, 0, 0.5, side), te = loft.point(eta, 1, 0.5, side), aft = te.subtract(le);
                    double chord = aft.length(), r = 0.05 * chord;
                    if (chord < 1.8) { continue; }
                    Vec3d down = new Vec3d(0, -1, 0), from = loft.point(eta, hinge - 0.12, 0, side).add(down.scale(0.4 * r));
                    Vec3d to = te.add(aft.scale(0.22)).add(down.scale(0.9 * r));
                    fixed.lathe(from, to, new double[]{0, 0.12, 0.4, 0.75, 1}, new double[]{0, 0.75 * r, r, 0.8 * r, 0}, null, 8, skin, false);
                }
            }
        }
        fixed.pens(pen, tags);
    }

    /**
     * A loft's skin between two section outlines of the same layout (upper surface from the trailing edge forward, then
     * the lower one back, so point k faces point size - 1 - k across the section), one face per quad lit by its normal
     * turned away from the section's inside. Two-sided sheets put two faces in one place and, drawn without culling,
     * whichever came last showed: the inward one on the mirrored half, which went dark.
     */
    private static void skin(PartMesh out, List<Vec3d> rowA, List<Vec3d> rowB, int color) {
        int m = Math.min(rowA.size(), rowB.size());
        for (int i = 0; i + 1 < m; i++) {
            Vec3d a = rowA.get(i), b = rowA.get(i + 1), c = rowB.get(i + 1), d = rowB.get(i);
            Vec3d inside = Vec3d.ZERO;
            for (List<Vec3d> row : List.of(rowA, rowB)) {
                inside = inside.add(row.get(i)).add(row.get(i + 1)).add(row.get(m - 1 - i)).add(row.get(Math.max(m - 2 - i, 0)));
            }
            face(out, a, b, c, d, color, a.add(b).add(c).add(d).scale(0.25).subtract(inside.scale(0.125)));
        }
    }

    /** A flat end between two rows, its face lit toward outward. */
    private static void cap(PartMesh out, List<Vec3d> rowA, List<Vec3d> rowB, int color, Vec3d outward) {
        int m = Math.min(rowA.size(), rowB.size());
        for (int i = 0; i + 1 < m; i++) { face(out, rowA.get(i), rowA.get(i + 1), rowB.get(i + 1), rowB.get(i), color, outward); }
    }

    /** One quad facing outward: its corners wound counter-clockwise seen from outside (vehicles are drawn culled) and lit by that normal. */
    private static void face(PartMesh out, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color, Vec3d outward) {
        // the diagonals' cross product: the counter-clockwise normal of any quad, also one with two corners the same
        Vec3d n = c.subtract(a).cross(d.subtract(b));
        if (n.lengthSquared() < 1e-24) { return; }
        if (n.dot(outward) >= 0) { out.quad(a, b, c, d, color, n); }
        else { out.quad(a, d, c, b, color, n.scale(-1)); }
    }

    private static List<Vec3d> shifted(List<Vec3d> pts, Vec3d by) {
        List<Vec3d> out = new ArrayList<>(pts.size());
        for (Vec3d v : pts) { out.add(v.subtract(by)); }
        return out;
    }

    private static List<Vec3d> reversed(List<Vec3d> l) { List<Vec3d> r = new ArrayList<>(l); java.util.Collections.reverse(r); return r; }

    /**
     * The section outline at a span fraction between chord fractions s0 and s1: along the upper surface from s1 to s0,
     * back along the lower one (a closed loop when s0 = 0, s1 = 1).
     */
    private static List<Vec3d> outline(WingSurface loft, double eta, int side, double s0, double s1) {
        List<Vec3d> up = new ArrayList<>(), down = new ArrayList<>();
        for (int i = 0; i < CHORD_POINTS; i++) {
            double f = (double) i / (CHORD_POINTS - 1), s = s0 + (s1 - s0) * 0.5 * (1 - Math.cos(Math.PI * f));
            up.add(loft.point(eta, s, 1, side));
            down.add(loft.point(eta, s, 0, side));
        }
        List<Vec3d> out = new ArrayList<>(reversed(up));
        out.addAll(down.subList(1, down.size()));
        return out;
    }

    // ---------------------------------------------------------------- propellers

    /** The propeller: spinner fixed, blades turning about +X with the angle. */
    public static EngineModel build(AirPropellerDesign d, String highlight) {
        EngineModel m = new EngineModel(new PartMesh());
        m.piece(propeller(d, highlight, 0), a -> new EngineModel.Placement(Vec3d.ZERO, Shapes.aboutX(a)));
        return m;
    }

    /**
     * Spinner and blades in the propeller's frame (axis +X; blade 0 along +Y) at a blade setting (rad). The spinner is a
     * smooth ogive on a backplate with its screws, the blades come out of it on round shanks and are lofted smooth:
     * their forward face bare metal, the face toward the cockpit matte black against glare, the tips painted (the
     * usual warning stripe), the tip rounded in planform.
     */
    public static PartMesh propeller(AirPropellerDesign d, String highlight, double setting) {
        var b = d.blades();
        PartMesh out = propellerHub(d, highlight), blade = propellerBlade(d, highlight, setting);
        for (int k = 0; k < b.blades(); k++) { out.add(blade, Shapes.aboutX(2 * Math.PI * k / b.blades()), Vec3d.ZERO); }
        return out;
    }

    /** 0.12: the spinner, its backplate and screws (what turns with the blades but does not change pitch). */
    public static PartMesh propellerHub(AirPropellerDesign d, String highlight) {
        var b = d.blades();
        double r = b.diameterM() / 2, hub = b.hubRatio() * r;
        PartMesh out = new PartMesh();
        int spinner = "hub".equals(highlight) ? Shapes.HIGHLIGHT : SPINNER;
        // the ogive: a smooth nose ahead of the blades, the backplate behind them
        double length = 2.6 * hub, back = -0.55 * hub;
        int n = 14;
        double[] along = new double[n + 1], radius = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, rr = hub * 1.06;
            along[i] = t;
            // a tangent ogive from the base (radius R) to the point: r(t) = R sqrt(1 - t^1.6)
            radius[i] = i == n ? 0 : rr * Math.sqrt(Math.max(0, 1 - Math.pow(t, 1.6)));
        }
        out.lathe(new Vec3d(back, 0, 0), new Vec3d(back + length, 0, 0), along, radius, null, 48, spinner, false);
        out.lathe(new Vec3d(back - 0.05 * hub, 0, 0), new Vec3d(back, 0, 0), new double[]{0, 0, 1, 1}, new double[]{0, hub * 1.1, hub * 1.1, 0}, null, 48,
                "hub".equals(highlight) ? Shapes.HIGHLIGHT : DARK, true);
        for (int k = 0; k < 8; k++) {
            double a = 2 * Math.PI * (k + 0.5) / 8;
            Vec3d at = new Vec3d(back + 0.18 * hub, Math.cos(a) * hub * 1.04, Math.sin(a) * hub * 1.04);
            out.cylinder(at, at.add(new Vec3d(0, Math.cos(a), Math.sin(a)).scale(0.012 * hub + 0.002)), 0.035 * hub + 0.002, 0.035 * hub + 0.002, 8, DARK);
        }
        return out;
    }

    /**
     * 0.12: one blade along +Y at a blade setting (rad added to the design's angles); turning it about +Y changes its
     * pitch (the world draws one blade N times, each turned by the propeller's shown setting).
     */
    public static PartMesh propellerBlade(AirPropellerDesign d, String highlight, double setting) {
        var b = d.blades();
        var g = d.geometry();
        double r = b.diameterM() / 2;
        boolean bladesLit = "blades".equals(highlight) || "section".equals(highlight);
        return blade(u -> b.hubRatio() * 0.55 + (1 - b.hubRatio() * 0.55) * u, x -> b.chordRatio(Math.max(x, b.hubRatio())) * b.diameterM(),
                x -> g.bladeAngle(Math.max(x, b.hubRatio())) + setting, x -> b.thicknessAt(Math.max(x, b.hubRatio())), r, d.section().airfoil(0.12),
                bladesLit ? Shapes.HIGHLIGHT : BLADE_FACE, bladesLit ? Shapes.HIGHLIGHT : BLADE, true, b.hubRatio());
    }

    /**
     * One blade along +Y from the root to the tip, lofted smooth: at radius fraction x its chord, angle from the plane
     * of rotation (leading edge toward +Z and forward along +X: a propeller turning positively about +X), thickness
     * ratio. A propeller's blade grows out of a round shank inside the spinner (radius fraction below shankEnd), its
     * tip rounds off in planform over the last 15 % and is painted over the last 5 %; its forward (suction) face is
     * faceColor, the other backColor. A rotor blade keeps a square tip with a cap.
     */
    private static PartMesh blade(DoubleUnaryOperator radiusOf, DoubleUnaryOperator chordOf, DoubleUnaryOperator angleOf, DoubleUnaryOperator thicknessOf,
                                  double radius, Airfoil shape, int faceColor, int backColor, boolean propeller, double shankEnd) {
        int stations = propeller ? 30 : 24, pts = CHORD_POINTS * 2 - 1;
        Vec3d[][] grid = new Vec3d[stations + 1][pts];
        int[][] colour = new int[stations + 1][pts];
        double baseThickness = Math.max(shape.thickness(), 1e-3), first = radiusOf.applyAsDouble(0);
        for (int i = 0; i <= stations; i++) {
            // denser toward the root's blend and the tip's rounding
            double u = 0.5 * (1 - Math.cos(Math.PI * i / stations));
            double x = radiusOf.applyAsDouble(u), c = chordOf.applyAsDouble(x), beta = angleOf.applyAsDouble(x);
            double scale = thicknessOf.applyAsDouble(x) / baseThickness;
            if (propeller && u > 0.85) { c *= Math.max(Math.sqrt(Math.max(0, 1 - Math.pow((u - 0.85) / 0.15, 2))), 0.08); }
            // blend from a round shank (a circle of a sixth of the chord at the root) to the airfoil by the blade's own start
            double shank = propeller ? Math.clamp((shankEnd - x) / Math.max(shankEnd - first, 1e-6), 0, 1) : 0;
            shank = shank * shank * (3 - 2 * shank);
            Vec3d lead = propeller ? new Vec3d(Math.sin(beta), 0, Math.cos(beta)) : new Vec3d(0, Math.sin(beta), -Math.cos(beta));
            Vec3d normal = propeller ? new Vec3d(Math.cos(beta), 0, -Math.sin(beta)) : new Vec3d(0, Math.cos(beta), Math.sin(beta));
            Vec3d radial = propeller ? new Vec3d(0, x * radius, 0) : new Vec3d(x * radius, 0, 0);
            double rootChord = chordOf.applyAsDouble(shankEnd), shankR = rootChord / 6;
            for (int k = 0; k < pts; k++) {
                boolean upperSide = k < CHORD_POINTS;
                double f = upperSide ? 1 - (double) k / (CHORD_POINTS - 1) : (double) (k - CHORD_POINTS + 1) / (CHORD_POINTS - 1);
                double sc = 0.5 * (1 - Math.cos(Math.PI * f));
                double h = (upperSide ? shape.upperAt(sc) : shape.lowerAt(sc)) * scale;
                Vec3d foil = lead.scale((0.25 - sc) * c).add(normal.scale(h * c));
                // the same point of a circle: round from the trailing edge over the face and back under
                double theta = upperSide ? Math.PI * f : Math.PI * (2 - f);
                Vec3d circle = lead.scale(-Math.cos(theta) * shankR).add(normal.scale(Math.sin(theta) * shankR));
                grid[i][k] = radial.add(foil.scale(1 - shank).add(circle.scale(shank)));
                boolean tip = propeller && u > 0.95;
                colour[i][k] = tip ? TIP : propeller && shank > 0.5 ? DARK : upperSide ? faceColor : backColor;
            }
        }
        PartMesh out = new PartMesh();
        // corner normals from the grid's neighbours, turned away from the section's middle
        Vec3d[][] normals = new Vec3d[stations + 1][pts];
        for (int i = 0; i <= stations; i++) {
            Vec3d mid = Vec3d.ZERO;
            for (Vec3d v : grid[i]) { mid = mid.add(v.scale(1.0 / pts)); }
            for (int k = 0; k < pts; k++) {
                Vec3d dk = grid[i][Math.min(k + 1, pts - 1)].subtract(grid[i][Math.max(k - 1, 0)]);
                Vec3d di = grid[Math.min(i + 1, stations)][k].subtract(grid[Math.max(i - 1, 0)][k]);
                Vec3d nrm = dk.cross(di);
                if (nrm.dot(grid[i][k].subtract(mid)) < 0) { nrm = nrm.scale(-1); }
                double l = nrm.length();
                normals[i][k] = l > 1e-12 ? nrm.scale(1 / l) : grid[i][k].subtract(mid).normalized();
            }
        }
        for (int i = 0; i < stations; i++) {
            for (int k = 0; k + 1 < pts; k++) {
                Vec3d a = grid[i][k], b2 = grid[i][k + 1], c2 = grid[i + 1][k + 1], d2 = grid[i + 1][k];
                Vec3d[] ns = {normals[i][k], normals[i][k + 1], normals[i + 1][k + 1], normals[i + 1][k]};
                int[] cs = {colour[i][k], colour[i][k + 1], colour[i + 1][k + 1], colour[i + 1][k]};
                Vec3d face = c2.subtract(a).cross(d2.subtract(b2)), outward = ns[0].add(ns[1]).add(ns[2]).add(ns[3]);
                if (face.lengthSquared() < 1e-24) { continue; }
                if (face.dot(outward) >= 0) { out.quad(a, b2, c2, d2, ns, cs); }
                else { out.quad(a, d2, c2, b2, new Vec3d[]{ns[0], ns[3], ns[2], ns[1]}, new int[]{cs[0], cs[3], cs[2], cs[1]}); }
            }
        }
        // the tip: a rotor's square end capped, a propeller's rounded one closed by its last small section
        List<Vec3d> last = java.util.Arrays.asList(grid[stations]);
        int half = pts / 2;
        cap(out, last.subList(0, half + 1), reversed(last.subList(half, pts)), propeller ? TIP : TIP, propeller ? Vec3d.Y : Vec3d.X);
        return out;
    }

    // ---------------------------------------------------------------- rotors

    /** The rotor: mast and hub fixed, blades turning about +Y (counter-clockwise seen from above, or clockwise) with the angle. */
    public static EngineModel build(RotorDesign d, String highlight) {
        EngineModel m = new EngineModel(mast(d, highlight));
        double sense = d.hub().clockwise() ? -1 : 1;
        m.piece(rotor(d, highlight, Math.toRadians(2.5)), a -> new EngineModel.Placement(Vec3d.ZERO, Orientation.rotationVector(Vec3d.Y.scale(sense * a))));
        return m;
    }

    /** The mast below the hub. */
    public static PartMesh mast(RotorDesign d, String highlight) {
        double r = d.blades().radiusM();
        PartMesh out = new PartMesh();
        out.cylinder(new Vec3d(0, -0.12 * r, 0), new Vec3d(0, -0.01 * r, 0), 0.012 * r, 0.012 * r, 16, DARK);
        return out;
    }

    /**
     * 0.12: the rotor's hub (what turns with the blades but does not flap or pitch) and one blade along +X with its
     * root grip, flat and at no collective (its twist only): the world turns each blade by its flapping about +Z and
     * its pitch about +X (the other way for a clockwise rotor's mirrored blade).
     */
    public static PartMesh rotorHub(RotorDesign d, String highlight) {
        var b = d.blades();
        double hubR = Math.max(b.rootCutout() * b.radiusM() * 0.6, 0.05);
        int hubColor = "hub".equals(highlight) || "controls".equals(highlight) ? Shapes.HIGHLIGHT : DARK;
        return new PartMesh().cylinder(new Vec3d(0, -0.04 * hubR, 0), new Vec3d(0, 0.35 * hubR, 0), hubR, hubR * 0.8, 20, hubColor);
    }

    /** 0.12: one rotor blade and its grip (see rotorHub). */
    public static PartMesh rotorBlade(RotorDesign d, String highlight) {
        var b = d.blades();
        double r = b.radiusM(), cutout = b.rootCutout(), hubR = Math.max(cutout * r * 0.6, 0.05);
        int bladeColor = "blades".equals(highlight) || "section".equals(highlight) ? Shapes.HIGHLIGHT : BLADE;
        int hubColor = "hub".equals(highlight) || "controls".equals(highlight) ? Shapes.HIGHLIGHT : DARK;
        PartMesh blade = blade(n -> cutout + (1 - cutout) * n, x -> b.chordAt(x), x -> Math.toRadians(b.twistDeg()) * (x - 0.75) / (1 - cutout),
                x -> b.thicknessAt(x), r, d.section().airfoil(0.1), bladeColor, bladeColor, false, 0);
        if (d.hub().clockwise()) { blade = mirrored(blade); }
        blade.cylinder(Vec3d.X.scale(hubR * 0.9), Vec3d.X.scale(cutout * r), 0.03 * b.chordAt(cutout) + 0.02, 0.15 * b.chordAt(cutout), 10, hubColor);
        return blade;
    }

    /**
     * 0.12: the swashplate on the mast under the hub: a lower ring (tilted by the cyclic, raised by the collective) and
     * its pitch links' stubs. In the rotor's frame at rest.
     */
    public static PartMesh swashplate(RotorDesign d) {
        double r = d.blades().radiusM(), y = -0.06 * r, ring = 0.03 * r + 0.04;
        PartMesh out = new PartMesh();
        out.cylinder(new Vec3d(0, y - 0.012, 0), new Vec3d(0, y + 0.012, 0), ring, ring, 20, DARK);
        out.cylinder(new Vec3d(0, y + 0.012, 0), new Vec3d(0, y + 0.02, 0), ring * 0.8, ring * 0.8, 20, BLADE);
        return out;
    }

    /** Hub and blades in the rotor's frame at a coning angle (rad); blade 0 along +X. */
    public static PartMesh rotor(RotorDesign d, String highlight, double coning) {
        var b = d.blades();
        double r = b.radiusM(), cutout = b.rootCutout();
        boolean clockwise = d.hub().clockwise();
        int bladeColor = "blades".equals(highlight) || "section".equals(highlight) ? Shapes.HIGHLIGHT : BLADE;
        int hubColor = "hub".equals(highlight) || "controls".equals(highlight) ? Shapes.HIGHLIGHT : DARK;
        PartMesh out = new PartMesh();
        double hubR = Math.max(cutout * r * 0.6, 0.05);
        out.cylinder(new Vec3d(0, -0.04 * hubR, 0), new Vec3d(0, 0.35 * hubR, 0), hubR, hubR * 0.8, 20, hubColor);
        double collective = Math.toRadians(0.5 * (d.controls().minCollectiveDeg() + d.controls().maxCollectiveDeg()));
        for (int k = 0; k < b.blades(); k++) {
            double psi = 2 * Math.PI * k / b.blades();
            Orientation turn = Orientation.rotationVector(Vec3d.Y.scale(psi)).multiply(Orientation.rotationVector(Vec3d.Z.scale(coning)));
            PartMesh blade = blade(n -> cutout + (1 - cutout) * n, x -> b.chordAt(x), x -> collective + Math.toRadians(b.twistDeg()) * (x - 0.75) / (1 - cutout),
                    x -> b.thicknessAt(x), r, d.section().airfoil(0.1), bladeColor, bladeColor, false, 0);
            if (clockwise) { blade = mirrored(blade); }
            out.add(blade, turn, Vec3d.ZERO);
            // the root grip from the hub to the cutout
            Vec3d along = turn.rotate(Vec3d.X);
            out.cylinder(along.scale(hubR * 0.9), along.scale(cutout * r), 0.03 * b.chordAt(cutout) + 0.02, 0.15 * b.chordAt(cutout), 10, hubColor);
        }
        return out;
    }

    /** A blade mirrored through z (a rotor turning the other way: its leading edge faces +z), its corner normals and colours kept. */
    private static PartMesh mirrored(PartMesh m) {
        PartMesh out = new PartMesh();
        for (PartMesh.Quad q : m.quads()) {
            if (q.normals() == null) { out.quad(flip(q.d()), flip(q.c()), flip(q.b()), flip(q.a()), q.color(), flip(q.normal())); continue; }
            Vec3d[] n = q.normals();
            int[] c = q.colors() == null ? new int[]{q.color(), q.color(), q.color(), q.color()} : q.colors();
            out.quad(flip(q.d()), flip(q.c()), flip(q.b()), flip(q.a()), new Vec3d[]{flip(n[3]), flip(n[2]), flip(n[1]), flip(n[0])}, new int[]{c[3], c[2], c[1], c[0]});
        }
        return out;
    }
    private static Vec3d flip(Vec3d v) { return new Vec3d(v.x(), v.y(), -v.z()); }
}
