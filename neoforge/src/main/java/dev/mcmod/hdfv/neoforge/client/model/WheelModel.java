package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.aerocore.api.wheel.Tyre;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.BrakeDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.part.wheel.RimDesign;
import dev.mcmod.hdfv.part.wheel.StrutDesign;
import dev.mcmod.hdfv.part.wheel.TyreDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * Wheels, axles and landing gear drawn from their designs (0.6), in their parts' frames.
 *
 * <ul>
 * <li>A wheel in its own frame (axle along +z, outboard toward +z, centre at the origin): the tyre turned on its
 *     section (bead, bulging sidewall, rounded shoulders, the tread's grooves, slots, sipes or lugs by its pattern),
 *     the wheel (rolled lip, barrel, spokes or a pressed steel disc with its holes, hub, lug nuts, centre cap), the
 *     brake's disc on its hat (a vented one's vanes at the edge) or drum; what stays still: a caliper over the disc
 *     (more pistons, a bigger and brighter caliper) or a drum's backing plate.</li>
 * <li>An axle: both wheels at the hubs, and for each side at its compression the suspension's links as its layout
 *     has them (MacPherson strut and lower arm, wishbones, multi-links, trailing and semi-trailing arms, swing half
 *     shafts, a twist beam, a solid axle on coils and a Panhard rod or Watt linkage or on leaf springs), coil springs
 *     and dampers, half shafts with their boots and the differential, the steering rack and tie rods, the anti-roll
 *     bar.</li>
 * <li>A landing-gear leg: an oleo's cylinder and chromed piston sliding with the compression, its torque links and
 *     fork; a spring-steel leg's tapered bar; a rigid fork; the wheel with its disc beside it.</li>
 * </ul>
 */
public final class WheelModel {
    private WheelModel() {}

    static final int RUBBER = 0xff1b1c1e, SIDEWALL = 0xff232527, GROOVE = 0xff0a0a0b, LETTER = 0xff3a3d40, STEEL_WHEEL = 0xff4f5358, ALLOY = 0xffc4c9cf,
            FORGED = 0xffd5d9de, MAG = 0xffb79c55, RIM_CHROME = 0xffe8ebee, RIM_SILVER = 0xffc3c8ce, RIM_BLACK = 0xff1e2023, RIM_GOLD = 0xffc9a04a, BARREL = 0xff8c9196, NUT = 0xffd8dcdf, CAP = 0xff2c2f33, DISC = 0xffa3a6a9, DISC_HAT = 0xff55585c,
            VANES = 0xff3c3e41, DRUM = 0xff46484b, CALIPER = 0xff55595e, CALIPER_RED = 0xffb3241d, CALIPER_GOLD = 0xffc89b2c, BACKING = 0xff2e3033,
            ARM = 0xff34373b, KNUCKLE = 0xff6a6e73, SPRING = 0xff2f62a6, DAMPER = 0xff26292d, ROD = 0xffd9dde1, BOOT = 0xff111214, SHAFT = 0xff5c6066,
            HOUSING = 0xff4b4e53, LEAF = 0xff2a2c2f, RACK = 0xff7b8086, BAR = 0xff222427, OLEO = 0xffc9cdd2, CHROME = 0xffe7eaed, LEG_PAINT = 0xffe9e9e6,
            FORK = 0xffb7bcc2;

    // ---------------------------------------------------------------- wheels

    /** Where the wheel's face (the spokes' outer face) and the brake disc sit along its axle, m. */
    record Layout(double tyreR, double rimR, double width, double rimWidth, double face, double disc) {
        static Layout of(TyreDesign t, RimDesign w, BrakeDesign b, boolean beside) {
            double r = Shapes.positive(t.tyre().radiusM(), 0.3), rim = t.rimDiameterM() / 2, rw = Math.max(w.widthM(), 0.05);
            // 0.16: a deeper lip sets the face further in (the look; the track is the axle's)
            double face = 0.5 * rw - 0.035 - w.lipDepthM(), thick = b.kind() == BrakeDesign.Kind.NONE ? 0.02 : b.rotorThicknessM();
            double disc = beside ? -0.5 * rw - 0.5 * thick - 0.015 : face - 0.07 - 0.5 * thick;
            return new Layout(r, rim, t.sectionWidthM(), rw, face, disc);
        }
    }

    /** What turns with a wheel (tyre, wheel, disc or drum), in its frame; the brake beside the wheel for an aircraft's. */
    public static PartMesh turning(TyreDesign t, RimDesign w, BrakeDesign b, String highlight, boolean beside) {
        Layout l = Layout.of(t, w, b, beside);
        PartMesh out = new PartMesh();
        tyre(out, t, l, "tyre".equals(highlight));
        rim(out, w, l, "wheel".equals(highlight), t.construction() == Tyre.Construction.AIRCRAFT);
        brakeRotor(out, b, l, "brake".equals(highlight));
        return out;
    }

    /** What stays with the knuckle: the caliper or a drum's backing plate. */
    public static PartMesh still(TyreDesign t, RimDesign w, BrakeDesign b, String highlight, boolean beside) {
        Layout l = Layout.of(t, w, b, beside);
        PartMesh out = new PartMesh();
        boolean lit = "brake".equals(highlight);
        if (b.kind() == BrakeDesign.Kind.NONE) { return out; }
        double ro = b.rotorDiameterM() / 2;
        if (b.kind().drum()) {
            double z = l.disc() - 0.5 * b.rotorThicknessM() - 0.006;
            annulus(out, new Vec3d(0, 0, z - 0.004), new Vec3d(0, 0, z), 0.06, ro + 0.008, 32, lit ? Shapes.HIGHLIGHT : BACKING);
            out.cylinder(new Vec3d(-0.03, ro * 0.75, z - 0.004), new Vec3d(-0.03, ro * 0.75, z - 0.03), 0.008, 0.008, 8, ROD);
            return out;
        }
        int color = lit ? Shapes.HIGHLIGHT : b.pistons() >= 4 ? CALIPER_RED : b.pistons() >= 2 ? CALIPER_GOLD : CALIPER;
        double arc = 0.09 + 0.03 * (b.pistons() - 1), mid = ro - 0.02, span = arc / mid;
        double depth = 0.5 * b.rotorThicknessM() + 0.02 + 0.006 * b.pistons(), rin = ro - 0.055, rout = ro + 0.014;
        double centre = Math.toRadians(160);
        int segs = 6;
        for (int i = 0; i < segs; i++) {
            double a0 = centre - span / 2 + span * i / segs, a1 = centre - span / 2 + span * (i + 1) / segs;
            out.hexahedron(new Vec3d[]{
                    polar(rin, a0, l.disc() - depth), polar(rout, a0, l.disc() - depth), polar(rout, a1, l.disc() - depth), polar(rin, a1, l.disc() - depth),
                    polar(rin, a0, l.disc() + depth), polar(rout, a0, l.disc() + depth), polar(rout, a1, l.disc() + depth), polar(rin, a1, l.disc() + depth)}, color);
        }
        // the bleed screw and the bracket toward the knuckle
        Vec3d top = polar(rout, centre - span / 2, l.disc());
        out.cylinder(top, top.add(new Vec3d(0, 0.02, 0)), 0.004, 0.004, 6, NUT);
        out.box(polar(rin - 0.02, centre, l.disc() - depth), polar(rin, centre + 0.25, l.disc() - depth + 0.02), color);
        return out;
    }

    private static Vec3d polar(double r, double angle, double z) { return new Vec3d(r * Math.cos(angle), r * Math.sin(angle), z); }

    /** An annulus between two radii from one point to another along an axis (a disc with a hole). */
    static void annulus(PartMesh out, Vec3d from, Vec3d to, double inner, double outer, int sides, int color) {
        out.lathe(from, to, new double[]{0, 0, 1, 1, 0}, new double[]{inner, outer, outer, inner, inner}, null, sides, color, true);
    }

    /** The tyre: its section swept round, the tread's pattern on it. */
    private static void tyre(PartMesh out, TyreDesign t, Layout l, boolean lit) {
        double r = l.tyreR(), rim = l.rimR(), h = r - rim, w = l.width();
        int rubber = lit ? Shapes.HIGHLIGHT : RUBBER, side = lit ? Shapes.HIGHLIGHT : SIDEWALL;
        List<double[]> profile = new ArrayList<>();   // {z over the width, radius, colour}
        double[][] wall = {{-0.42, rim + 0.02 * h}, {-0.47, rim + 0.2 * h}, {-0.5, rim + 0.45 * h}, {-0.49, rim + 0.66 * h}, {-0.465, rim + 0.83 * h},
                {-0.44, r - 0.07 * h}, {-0.415, r - 0.018 * h}};
        for (double[] p : wall) { profile.add(new double[]{p[0], p[1], p[1] > r - 0.1 * h ? rubber : side}); }
        double[] grooves = switch (t.tread()) {
            case ROAD, WINTER -> new double[]{-0.27, -0.09, 0.09, 0.27};
            case PERFORMANCE -> new double[]{-0.24, 0.0, 0.2};
            case ALL_TERRAIN, MUD_TERRAIN -> new double[]{-0.12, 0.12};
            case AIRCRAFT_RIB -> new double[]{-0.2, 0.0, 0.2};
            case SLICK -> new double[]{};
        };
        double gw = t.tread() == Tyre.Tread.MUD_TERRAIN ? 0.07 : 0.035, depth = Math.min(0.08 * h, 0.009);
        profile.add(new double[]{-0.39, r, rubber});
        for (double g : grooves) {
            profile.add(new double[]{g - gw / 2, r, rubber});
            profile.add(new double[]{g - gw / 2 + 0.004, r - depth, GROOVE});
            profile.add(new double[]{g + gw / 2 - 0.004, r - depth, GROOVE});
            profile.add(new double[]{g + gw / 2, r, rubber});
        }
        profile.add(new double[]{0.39, r, rubber});
        for (int i = wall.length - 1; i >= 0; i--) { profile.add(new double[]{-wall[i][0], wall[i][1], wall[i][1] > r - 0.1 * h ? rubber : side}); }
        // 0.22: the nearest two levels turned in 64 sides with the grooves; farther (levels 2-3) in 24, the tread plain
        out.levels(0, 1);
        turn(out, profile, w, 64, rubber);
        List<double[]> plain = new ArrayList<>();
        for (double[] p : wall) { plain.add(new double[]{p[0], p[1], p[1] > r - 0.1 * h ? rubber : side}); }
        plain.add(new double[]{-0.39, r, rubber});
        plain.add(new double[]{0.39, r, rubber});
        for (int i = wall.length - 1; i >= 0; i--) { plain.add(new double[]{-wall[i][0], wall[i][1], wall[i][1] > r - 0.1 * h ? rubber : side}); }
        out.levels(2, 3);
        turn(out, plain, w, 24, rubber);
        out.levels(0, 3);
        // 0.16: the player's lettering round each sidewall (the glyph atlas), else a raised band as before
        if (!t.sideText().isEmpty() && !lit) {
            sideLettering(out, t, rim, h, w);
        } else {
            out.levels(0, 1);   // 0.22: the raised bands the nearest two levels only
            for (int s : new int[]{-1, 1}) {
                for (int k = 0; k < 6; k++) {
                    double a0 = 2 * Math.PI * k / 6 + 0.15, a1 = a0 + 0.55;
                    double z = s * (0.5 * w * 0.995 + 0.0015);
                    sideQuad(out, rim + 0.48 * h, rim + 0.6 * h, a0, a1, z, s, lit ? Shapes.HIGHLIGHT : LETTER);
                }
            }
            out.levels(0, 3);
        }
        // the tread's cross pattern
        double pitch = switch (t.tread()) { case WINTER -> 0.022; case MUD_TERRAIN -> 0.075; case ALL_TERRAIN -> 0.05; case PERFORMANCE -> 0.045; default -> 0.035; };
        int count = (int) Math.round(2 * Math.PI * r / pitch);
        if (t.tread() == Tyre.Tread.SLICK || t.tread() == Tyre.Tread.AIRCRAFT_RIB) { return; }
        out.levels(0, 1);   // 0.22: the pattern the nearest two levels only (a mud tyre's lugs were 600 quads at every level)
        for (int k = 0; k < count; k++) {
            double a = 2 * Math.PI * k / count;
            switch (t.tread()) {
                case ALL_TERRAIN, MUD_TERRAIN -> {
                    // lugs standing proud, staggered across the tread
                    double lug = t.tread() == Tyre.Tread.MUD_TERRAIN ? 0.012 : 0.006, len = pitch * (t.tread() == Tyre.Tread.MUD_TERRAIN ? 0.55 : 0.7);
                    double da = len / r, stagger = k % 2 == 0 ? 0 : 0.06;
                    for (double[] zr : new double[][]{{-0.39, -0.17 + stagger}, {-0.08 + stagger, 0.08 + stagger}, {0.17 - stagger, 0.39}}) {
                        double z0 = zr[0] * w, z1 = zr[1] * w;
                        out.hexahedron(new Vec3d[]{polar(r - 0.002, a, z0), polar(r - 0.002, a + da, z0), polar(r - 0.002, a + da, z1), polar(r - 0.002, a, z1),
                                polar(r + lug, a, z0), polar(r + lug, a + da, z0), polar(r + lug, a + da, z1), polar(r + lug, a, z1)}, rubber);
                    }
                }
                default -> {
                    double slot = t.tread() == Tyre.Tread.WINTER ? 0.0025 : 0.005, da = slot / r;
                    // shoulder slots (and the winter tyre's sipes across the ribs)
                    treadQuad(out, r + 0.0012, a, a + da, -0.39 * w, (grooves.length > 0 ? grooves[0] - 0.02 : -0.2) * w, GROOVE);
                    treadQuad(out, r + 0.0012, a + 0.5 * da, a + 1.5 * da, (grooves.length > 0 ? grooves[grooves.length - 1] + 0.02 : 0.2) * w, 0.39 * w, GROOVE);
                    if (t.tread() == Tyre.Tread.WINTER && grooves.length > 1) {
                        for (int g = 0; g + 1 < grooves.length; g++) { treadQuad(out, r + 0.0012, a + 0.3 * da, a + 1.1 * da, (grooves[g] + 0.03) * w, (grooves[g + 1] - 0.03) * w, GROOVE); }
                    }
                }
            }
        }
        out.levels(0, 3);
    }

    /** 0.22: a tyre's section ({z over the width, radius, colour} from one bead to the other) turned in a number of sides. */
    private static void turn(PartMesh out, List<double[]> profile, double w, int sides, int rubber) {
        int n = profile.size();
        double[] along = new double[n], radius = new double[n];
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) { along[i] = profile.get(i)[0] + 0.5; radius[i] = profile.get(i)[1]; colors[i] = (int) (long) profile.get(i)[2]; }
        out.lathe(new Vec3d(0, 0, -w / 2), new Vec3d(0, 0, w / 2), along, radius, colors, sides, rubber, false);
    }

    /**
     * 0.16: a tyre's lettering round both sidewalls, read from outside, centred over the top of the wheel, capitals a
     * fifth of the sidewall's height on its middle (decals of the glyph atlas, the nearest two levels).
     */
    private static void sideLettering(PartMesh out, TyreDesign t, double rim, double h, double w) {
        String text = dev.mcmod.hdfv.look.Lettering.clean(t.sideText());
        int colour = t.sideTextColor().isEmpty() ? 0xfff4f4f2 : dev.mcmod.hdfv.design.RecordEditor.argb(t.sideTextColor());
        double rm = rim + 0.54 * h, cap = 0.2 * h, cell = cap * dev.mcmod.hdfv.look.Lettering.cellHeight();
        double width = dev.mcmod.hdfv.look.Lettering.width(text) * cap;
        if (!(width > 0) || width > 1.6 * Math.PI * rm) { return; }
        dev.mcmod.aerocore.api.mesh.Finish pen = out.finish();
        out.finish(dev.mcmod.hdfv.look.Finishes.GLYPHS);
        out.levels(0, 1);
        for (int s : new int[]{-1, 1}) {
            double z = s * (0.5 * w * 0.995 + 0.002);
            Vec3d normal = Vec3d.Z.scale(s);
            for (dev.mcmod.hdfv.look.Lettering.Glyph g : dev.mcmod.hdfv.look.Lettering.layout(text)) {
                // along the arc clockwise seen from this side, from the left of the top
                double a0 = Math.PI / 2 + s * (0.5 * width - g.x0() * cap) / rm, a1 = Math.PI / 2 + s * (0.5 * width - g.x1() * cap) / rm;
                double r0 = rm - 0.5 * cell, r1 = rm + 0.5 * cell;
                Vec3d[] p = {polar(r0, a0, z), polar(r0, a1, z), polar(r1, a1, z), polar(r1, a0, z)};
                BodyModel.glyph(out, p, new Vec3d[]{normal, normal, normal, normal}, g.u0(), g.u1(), g.v1(), g.v0(), colour);
            }
        }
        out.levels(0, 3);
        out.finish(pen);
    }

    /** A thin patch on the tread at a radius between two angles and two axial positions, facing out. */
    private static void treadQuad(PartMesh out, double r, double a0, double a1, double z0, double z1, int color) {
        Vec3d p = polar(r, a0, z0), q = polar(r, a1, z0), s = polar(r, a1, z1), u = polar(r, a0, z1);
        Vec3d outward = polar(1, 0.5 * (a0 + a1), 0);
        face(out, p, q, s, u, color, outward);
    }

    /** A patch on a sidewall (a plane z = const) between two radii and angles, facing toward the side (sign of z). */
    private static void sideQuad(PartMesh out, double r0, double r1, double a0, double a1, double z, int sign, int color) {
        int seg = 4;
        for (int i = 0; i < seg; i++) {
            double b0 = a0 + (a1 - a0) * i / seg, b1 = a0 + (a1 - a0) * (i + 1) / seg;
            face(out, polar(r0, b0, z), polar(r0, b1, z), polar(r1, b1, z), polar(r1, b0, z), color, Vec3d.Z.scale(sign));
        }
    }

    /** One quad facing outward (wound counter-clockwise seen from outside). */
    static void face(PartMesh out, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color, Vec3d outward) {
        Vec3d n = c.subtract(a).cross(d.subtract(b));
        if (n.lengthSquared() < 1e-24) { return; }
        if (n.dot(outward) >= 0) { out.quad(a, b, c, d, color, n); }
        else { out.quad(a, d, c, b, color, n.scale(-1)); }
    }

    /** The wheel: lip, barrel, the face (spokes or a pressed disc), hub, nuts and cap. */
    private static void rim(PartMesh out, RimDesign w, Layout l, boolean lit, boolean aircraft) {
        int start = out.quads().size();
        int color = lit ? Shapes.HIGHLIGHT : !w.rimColor().isEmpty() ? dev.mcmod.hdfv.design.RecordEditor.argb(w.rimColor()) : switch (w.rimFinish()) {
            case AUTO -> switch (w.material()) { case STEEL -> STEEL_WHEEL; case CAST_ALLOY -> ALLOY; case FORGED_ALLOY -> FORGED; case MAGNESIUM -> MAG; };
            case CHROME -> RIM_CHROME;
            case SILVER -> RIM_SILVER;
            case BLACK -> RIM_BLACK;
            case GOLD -> RIM_GOLD;
        };
        rimFace(out, w, l, color, lit, aircraft);
        // a chosen finish: the face, lips and hub in it (the barrel, nuts and cap as they are)
        dev.mcmod.aerocore.api.mesh.Finish look = lit ? null : switch (w.rimFinish()) {
            case AUTO -> w.rimColor().isEmpty() ? null : Finishes.PAINT_GLOSS;   // 0.16: a colour given is paint
            case CHROME -> Finishes.CHROME;
            case SILVER, GOLD -> Finishes.PAINT_METALLIC;
            case BLACK -> Finishes.PAINT_GLOSS;
        };
        if (look == null) { return; }
        for (int i = start; i < out.quads().size(); i++) {
            var q = out.quads().get(i);
            if (q.color() == color && q.finish() == null) { out.quads().set(i, q.withFinish(look)); }
        }
    }

    private static void rimFace(PartMesh out, RimDesign w, Layout l, int color, boolean lit, boolean aircraft) {
        double rr = l.rimR(), rw = l.rimWidth(), face = l.face();
        // the rolled lips either side, the barrel seen through the spokes
        annulus(out, new Vec3d(0, 0, 0.5 * rw - 0.012), new Vec3d(0, 0, 0.5 * rw + 0.003), rr - 0.022, rr + 0.012, 48, color);
        annulus(out, new Vec3d(0, 0, -0.5 * rw - 0.003), new Vec3d(0, 0, -0.5 * rw + 0.012), rr - 0.022, rr + 0.012, 48, color);
        out.lathe(new Vec3d(0, 0, -0.5 * rw + 0.01), new Vec3d(0, 0, 0.5 * rw - 0.01), new double[]{1, 0}, new double[]{rr - 0.02, rr - 0.02}, null, 48,
                lit ? Shapes.HIGHLIGHT : BARREL, true);
        double boss = Math.max(0.33 * rr, 0.035);
        // 0.16: the face's style (automatic: a pressed disc for steel and aircraft wheels, straight spokes otherwise)
        RimDesign.Style style = w.rimStyle() != RimDesign.Style.AUTO ? w.rimStyle()
                : w.material() == RimDesign.Material.STEEL || aircraft ? RimDesign.Style.STEEL : RimDesign.Style.SPOKE;
        boolean disc = style == RimDesign.Style.STEEL;
        if (style != RimDesign.Style.STEEL && style != RimDesign.Style.SPOKE) {
            styledFace(out, w, style, rr, boss, face, color);
        } else if (disc) {
            // a pressed disc dished outward, holes round it (a split aircraft wheel's flat face and its through bolts)
            out.lathe(new Vec3d(0, 0, face - 0.012), new Vec3d(0, 0, face + 0.008), new double[]{0, 0, 0.6, 1, 1, 0},
                    new double[]{0, rr - 0.018, rr - 0.02, 0.9 * boss, 0, 0}, null, 40, color, false);
            int holes = aircraft ? 6 : Math.max(w.spokes(), 6);
            for (int k = 0; k < holes; k++) {
                double a = 2 * Math.PI * (k + 0.5) / holes;
                if (aircraft) {
                    Vec3d at = polar(0.62 * rr, a, face + 0.008);
                    out.cylinder(at, at.add(new Vec3d(0, 0, 0.008)), 0.007, 0.006, 6, NUT);
                } else {
                    double rc = 0.66 * rr, hr = 0.075 * rr;
                    sideDisc(out, polar(rc, a, face + 0.0095), hr, 1, GROOVE);
                }
            }
        } else {
            int k = Math.max(w.spokes(), 3);
            double hubHalf = Math.max(0.24 * Math.PI * boss / k, 0.01), rimHalf = Math.max(0.3 * Math.PI * (rr - 0.02) / k, 0.015);
            for (int i = 0; i < k; i++) {
                double a = 2 * Math.PI * i / k;
                Vec3d dir = polar(1, a, 0), across = polar(1, a + Math.PI / 2, 0);
                Vec3d in = dir.scale(0.9 * boss), outer = dir.scale(rr - 0.02);
                double zf = face + 0.004, zb = face - 0.022, dish = 0.012;
                out.hexahedron(new Vec3d[]{
                        in.add(across.scale(-hubHalf)).add(Vec3d.Z.scale(zb)), in.add(across.scale(hubHalf)).add(Vec3d.Z.scale(zb)),
                        in.add(across.scale(hubHalf)).add(Vec3d.Z.scale(zf)), in.add(across.scale(-hubHalf)).add(Vec3d.Z.scale(zf)),
                        outer.add(across.scale(-rimHalf)).add(Vec3d.Z.scale(zb + dish)), outer.add(across.scale(rimHalf)).add(Vec3d.Z.scale(zb + dish)),
                        outer.add(across.scale(rimHalf)).add(Vec3d.Z.scale(zf + dish)), outer.add(across.scale(-rimHalf)).add(Vec3d.Z.scale(zf + dish))}, color);
            }
        }
        // hub boss, lug nuts on their circle, centre cap
        out.lathe(new Vec3d(0, 0, face - 0.03), new Vec3d(0, 0, face + 0.012), new double[]{0, 0, 0.8, 1, 1}, new double[]{0, boss, boss, 0.92 * boss, 0}, null, 32, color, true);
        int nuts = rr < 0.18 ? 4 : rr > 0.22 ? 6 : 5;
        for (int i = 0; i < nuts; i++) {
            Vec3d at = polar(0.7 * boss, 2 * Math.PI * i / nuts + 0.3, face + 0.012);
            out.cylinder(at, at.add(new Vec3d(0, 0, 0.012)), 0.0085, 0.007, 6, NUT);
        }
        out.lathe(new Vec3d(0, 0, face + 0.012), new Vec3d(0, 0, face + 0.024), new double[]{0, 0, 0.6, 1}, new double[]{0, 0.35 * boss, 0.3 * boss, 0}, null, 20,
                lit ? Shapes.HIGHLIGHT : CAP, false);
    }

    /**
     * 0.16: the other faces: twin spokes (a pair each), many thin spokes, a mesh (two crossing sets), turbine fins (spokes
     * swept round), a flat disc with a ring of small vents, a beadlock (spokes and a bolted ring at the lip).
     */
    private static void styledFace(PartMesh out, RimDesign w, RimDesign.Style style, double rr, double boss, double face, int color) {
        int k = Math.max(w.spokes(), 3);
        switch (style) {
            case TWIN_SPOKE -> {
                for (int i = 0; i < k; i++) {
                    double a = 2 * Math.PI * i / k, gap = 0.18 * Math.PI / k;
                    spoke(out, a - gap, a - 0.6 * gap, rr, boss, face, 0.55, color);
                    spoke(out, a + gap, a + 0.6 * gap, rr, boss, face, 0.55, color);
                }
            }
            case MULTI_SPOKE -> {
                int n = Math.max(k, 10);
                for (int i = 0; i < n; i++) { double a = 2 * Math.PI * i / n; spoke(out, a, a, rr, boss, face, 0.45 * Math.min(1, 10.0 / n) * n / k, color); }
            }
            case MESH -> {
                int n = 12;
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n;
                    spoke(out, a, a + 0.35, rr, boss, face, 0.35, color);
                    spoke(out, a, a - 0.35, rr, boss, face - 0.004, 0.35, color);
                }
            }
            case TURBINE -> {
                for (int i = 0; i < k; i++) { double a = 2 * Math.PI * i / k; spoke(out, a, a + 0.6, rr, boss, face, 0.7, color); }
            }
            case DISC -> {
                out.lathe(new Vec3d(0, 0, face - 0.01), new Vec3d(0, 0, face + 0.006), new double[]{0, 0, 1, 1, 0}, new double[]{0, rr - 0.018, rr - 0.018, 0, 0},
                        null, 40, color, false);
                for (int i = 0; i < 2 * k; i++) { sideDisc(out, polar(0.8 * rr, 2 * Math.PI * i / (2 * k), face + 0.0075), 0.035 * rr, 1, GROOVE); }
            }
            case BEADLOCK -> {
                for (int i = 0; i < k; i++) { double a = 2 * Math.PI * i / k; spoke(out, a, a, rr, boss, face, 1.2, color); }
                annulus(out, new Vec3d(0, 0, face + 0.004), new Vec3d(0, 0, face + 0.016), rr - 0.06, rr - 0.012, 48, color);
                int bolts = 16;
                for (int i = 0; i < bolts; i++) {
                    Vec3d at = polar(rr - 0.036, 2 * Math.PI * i / bolts, face + 0.016);
                    out.cylinder(at, at.add(new Vec3d(0, 0, 0.007)), 0.006, 0.005, 6, NUT);
                }
            }
            default -> { }
        }
    }

    /**
     * One spoke from the hub (angle aIn) to the rim (angle aOut, a swept spoke when they differ), its width a share of
     * the plain spoke's, dished out toward the rim.
     */
    private static void spoke(PartMesh out, double aIn, double aOut, double rr, double boss, double face, double share, int color) {
        double hubHalf = Math.max(0.24 * Math.PI * boss / 5 * share, 0.004), rimHalf = Math.max(0.3 * Math.PI * (rr - 0.02) / 5 * share, 0.006);
        Vec3d dirIn = polar(1, aIn, 0), acrossIn = polar(1, aIn + Math.PI / 2, 0), dirOut = polar(1, aOut, 0), acrossOut = polar(1, aOut + Math.PI / 2, 0);
        Vec3d in = dirIn.scale(0.9 * boss), outer = dirOut.scale(rr - 0.02);
        double zf = face + 0.004, zb = face - 0.022, dish = 0.012;
        out.hexahedron(new Vec3d[]{
                in.add(acrossIn.scale(-hubHalf)).add(Vec3d.Z.scale(zb)), in.add(acrossIn.scale(hubHalf)).add(Vec3d.Z.scale(zb)),
                in.add(acrossIn.scale(hubHalf)).add(Vec3d.Z.scale(zf)), in.add(acrossIn.scale(-hubHalf)).add(Vec3d.Z.scale(zf)),
                outer.add(acrossOut.scale(-rimHalf)).add(Vec3d.Z.scale(zb + dish)), outer.add(acrossOut.scale(rimHalf)).add(Vec3d.Z.scale(zb + dish)),
                outer.add(acrossOut.scale(rimHalf)).add(Vec3d.Z.scale(zf + dish)), outer.add(acrossOut.scale(-rimHalf)).add(Vec3d.Z.scale(zf + dish))}, color);
    }

    /** A flat disc facing +z (sign 1) or -z at a point. */
    private static void sideDisc(PartMesh out, Vec3d at, double r, int sign, int color) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            face(out, at, at.add(polar(r, a0, 0)), at.add(polar(r, a1, 0)), at.add(polar(r, a1, 0)), color, Vec3d.Z.scale(sign));
        }
    }

    /** The disc on its hat (a vented one's vanes dark at its edge) or the drum with its fins. */
    private static void brakeRotor(PartMesh out, BrakeDesign b, Layout l, boolean lit) {
        if (b.kind() == BrakeDesign.Kind.NONE) { return; }
        double ro = b.rotorDiameterM() / 2, t = b.rotorThicknessM(), z = l.disc();
        if (b.kind().drum()) {
            out.lathe(new Vec3d(0, 0, z - t / 2), new Vec3d(0, 0, z + t / 2), new double[]{0, 0, 0.2, 0.25, 0.45, 0.5, 0.7, 0.75, 1, 1},
                    new double[]{0, ro, ro, ro + 0.006, ro + 0.006, ro, ro, ro + 0.006, ro + 0.006, 0}, null, 40, lit ? Shapes.HIGHLIGHT : DRUM, true);
            return;
        }
        int band = lit ? Shapes.HIGHLIGHT : DISC;
        double ri = 0.55 * ro;
        if (b.kind() == BrakeDesign.Kind.VENTED_DISC) {
            double plate = 0.3 * t;
            annulus(out, new Vec3d(0, 0, z - t / 2), new Vec3d(0, 0, z - t / 2 + plate), ri, ro, 48, band);
            annulus(out, new Vec3d(0, 0, z + t / 2 - plate), new Vec3d(0, 0, z + t / 2), ri, ro, 48, band);
            // 0.22: the dark gap between the plates set in from the edge, and up close its 36 vanes (curved back a little)
            double rv = ro - Math.min(0.012, 0.08 * ro);
            annulus(out, new Vec3d(0, 0, z - t / 2 + plate), new Vec3d(0, 0, z + t / 2 - plate), ri, rv, 48, lit ? Shapes.HIGHLIGHT : VANES);
            int tags = out.tags();
            out.levels(0, 0);
            double z0 = z - t / 2 + plate, z1 = z + t / 2 - plate, half = 0.0022;
            for (int k = 0; k < 36; k++) {
                double a = 2 * Math.PI * k / 36, sweep = 0.12;
                double ai = a, ao = a + sweep, hi = half / (ri + 0.004), ho = half / (ro - 0.001);
                out.hexahedron(new Vec3d[]{
                        polar(ri + 0.004, ai - hi, z0), polar(ro - 0.001, ao - ho, z0), polar(ro - 0.001, ao + ho, z0), polar(ri + 0.004, ai + hi, z0),
                        polar(ri + 0.004, ai - hi, z1), polar(ro - 0.001, ao - ho, z1), polar(ro - 0.001, ao + ho, z1), polar(ri + 0.004, ai + hi, z1)}, band);
            }
            out.pens(out.finish(), tags);
        } else {
            annulus(out, new Vec3d(0, 0, z - t / 2), new Vec3d(0, 0, z + t / 2), ri, ro, 48, band);
        }
        // the hat out to the wheel's face
        out.lathe(new Vec3d(0, 0, z + t / 2), new Vec3d(0, 0, l.face() - 0.028), new double[]{0, 0, 1, 1}, new double[]{0, ri + 0.004, ri * 0.9, 0}, null, 32,
                lit ? Shapes.HIGHLIGHT : DISC_HAT, true);
    }

    /** 0.22: the quads of a mesh drawn at the nearest level (for drawing it whole, unbaked: no far variant over them). */
    public static PartMesh nearest(PartMesh m) {
        PartMesh out = new PartMesh();
        for (var q : m.quads()) { if (q.near() == 0) { out.quads().add(q); } }
        return out;
    }

    /** A mesh mirrored through z (a left wheel from a right one), its windings, normals and colours kept right. */
    /** The mirror image across z = 0 (0.11: AeroCore's, which keeps texture coordinates, finishes and tags). */
    public static PartMesh mirroredZ(PartMesh m) {
        PartMesh out = new PartMesh();
        out.quads().addAll(dev.mcmod.aerocore.api.mesh.MeshOps.mirror(m, 2).quads());
        return out;
    }

    private static Vec3d flip(Vec3d v) { return new Vec3d(v.x(), v.y(), -v.z()); }

    // ---------------------------------------------------------------- axles

    /** The hub of a side (-1 left, +1 right) at a compression (m from droop), part frame. */
    public static Vec3d hub(AxleDesign d, int side, double compression) {
        return new Vec3d(0, -d.layout().hubDropM() - d.suspension().designCompressionM() + compression, side * d.layout().trackM() / 2);
    }

    /** The axle on the design screen: everything at the design compression, the wheels turning with the angle. */
    public static EngineModel build(AxleDesign d, String highlight) {
        EngineModel m = new EngineModel(new PartMesh());
        double c = d.suspension().designCompressionM();
        m.fixed.add(fixed(d, highlight), Orientation.IDENTITY, Vec3d.ZERO);
        for (int side : new int[]{-1, 1}) {
            // 0.15: an axle in the hardpoints mode with its links where its hardpoints are
            PartMesh links = WheelRig.hardpointLinks(d, side, highlight);
            m.fixed.add(links != null ? links : corner(d, side, c, c, 0, highlight), Orientation.IDENTITY, Vec3d.ZERO);
            PartMesh still = still(d.tyre(), d.wheel(), d.brake(), highlight, false), turning = turning(d.tyre(), d.wheel(), d.brake(), highlight, false);
            if (side < 0) { still = mirroredZ(still); turning = mirroredZ(turning); }
            Vec3d h = hub(d, side, c);
            m.fixed.add(still, Orientation.IDENTITY, h);
            m.piece(turning, a -> new EngineModel.Placement(h, Orientation.rotationVector(Vec3d.Z.scale(-a))));
        }
        return m;
    }

    /** What does not move with the wheels: the differential, the steering rack, the anti-roll bar's middle. */
    public static PartMesh fixed(AxleDesign d, String highlight) {
        PartMesh out = new PartMesh();
        double y0 = -d.layout().hubDropM(), half = d.layout().trackM() / 2;
        boolean solid = d.suspension().layout().solid();
        int housing = "differential".equals(highlight) ? Shapes.HIGHLIGHT : HOUSING;
        if (d.differential().driven() && !solid) {
            if (d.differential().transverse()) {
                out.cylinder(new Vec3d(0, y0, -0.1), new Vec3d(0, y0, 0.1), 0.07, 0.07, 20, housing);
            } else {
                out.lathe(new Vec3d(-0.12, y0, 0), new Vec3d(0.14, y0, 0), new double[]{0, 0, 0.15, 0.5, 0.85, 1, 1}, new double[]{0, 0.1, 0.13, 0.135, 0.11, 0.06, 0},
                        null, 28, housing, false);
                out.cylinder(new Vec3d(0.14, y0, 0), new Vec3d(0.2, y0, 0), 0.045, 0.045, 16, housing);
                out.cylinder(new Vec3d(0.2, y0, 0), new Vec3d(0.215, y0, 0), 0.055, 0.055, 16, KNUCKLE);
                for (int s : new int[]{-1, 1}) { out.box(new Vec3d(-0.05, y0 + 0.08, s * 0.08 - 0.03), new Vec3d(0.05, y0 + 0.16, s * 0.08 + 0.03), housing); }
            }
        }
        if (d.layout().steered()) {
            int rack = "layout".equals(highlight) ? Shapes.HIGHLIGHT : RACK;
            double x = -0.14, y = y0 + 0.05, end = Math.max(half - 0.42, 0.2);
            out.cylinder(new Vec3d(x, y, -end), new Vec3d(x, y, end), 0.025, 0.025, 14, rack);
            for (int s : new int[]{-1, 1}) { out.cylinder(new Vec3d(x, y, s * end), new Vec3d(x, y, s * (end + 0.09)), 0.03, 0.018, 12, BOOT); }
            out.cylinder(new Vec3d(x, y, 0.1), new Vec3d(x + 0.12, y + 0.25, 0.12), 0.012, 0.012, 8, SHAFT);
        }
        if (d.layout().antiRollNPerM() > 0) {
            double x = solid ? -0.25 : 0.18, inner = Math.max(half - 0.45, 0.2);
            out.cylinder(new Vec3d(x, y0 - 0.06, -inner), new Vec3d(x, y0 - 0.06, inner), 0.012, 0.012, 10, "layout".equals(highlight) ? Shapes.HIGHLIGHT : BAR);
        }
        if (highlight == null) { mudflaps(d, out, y0, half); }
        return out;
    }

    /**
     * 0.22: a heavy axle's mud flaps (not steered, tyres of 0.42 m radius or more: a truck's or a trailer's): a rubber
     * flap behind each wheel from over the hub to 18 cm off the ground, a little wider than the tyre, on a bar from the
     * frame's side; the nearest three levels. They hang from the body, so they stay put as the wheel rides up.
     */
    private static void mudflaps(AxleDesign d, PartMesh out, double y0, double half) {
        double r = Shapes.positive(d.tyre().tyre().radiusM(), 0.3), w = d.tyre().sectionWidthM();
        if (d.layout().steered() || r < 0.42) { return; }
        int tags = out.tags();
        var pen = out.finish();
        out.levels(0, 2);
        double x = -(r + 0.1), top = y0 + 0.15, bottom = y0 - r + 0.18;
        for (int s : new int[]{-1, 1}) {
            double z0 = s * (half - 0.55 * w - 0.04), z1 = s * (half + 0.55 * w + 0.04), inner = s * Math.max(half - 0.55 * w - 0.3, 0.3);
            out.finish(Finishes.RUBBER).box(new Vec3d(x - 0.004, bottom, Math.min(z0, z1)), new Vec3d(x + 0.004, top, Math.max(z0, z1)), RUBBER);
            out.finish(Finishes.FORGED_STEEL).box(new Vec3d(x - 0.02, top - 0.03, Math.min(inner, z1)), new Vec3d(x + 0.02, top + 0.01, Math.max(inner, z1)), ARM);
        }
        out.pens(pen, tags);
    }

    /**
     * The links of one side (-1 left, +1 right) at its compression (and the other side's, for a solid axle's beam), the
     * knuckle turned by a steering angle (rad).
     */
    public static PartMesh corner(AxleDesign d, int side, double compression, double otherCompression, double steer, String highlight) {
        PartMesh out = new PartMesh();
        int arm = "suspension".equals(highlight) ? Shapes.HIGHLIGHT : ARM, spring = "suspension".equals(highlight) ? Shapes.HIGHLIGHT : SPRING;
        double half = d.layout().trackM() / 2, y0 = -d.layout().hubDropM(), s = side;
        Vec3d hub = hub(d, side, compression);
        double rw = d.wheel().widthM(), zk = s * (half - 0.5 * rw + 0.03 - 0.06);
        Suspension.Type type = d.suspension().layout();
        Vec3d k = new Vec3d(0, hub.y(), zk);
        // the knuckle (upright) and its stub to the hub
        if (!type.solid()) {
            out.box(new Vec3d(-0.04, hub.y() - 0.13, zk - 0.03), new Vec3d(0.04, hub.y() + 0.15, zk + 0.03), KNUCKLE);
        }
        switch (type) {
            case MACPHERSON -> {
                Vec3d low = k.add(new Vec3d(0, -0.12, 0)), base = k.add(new Vec3d(-0.01, 0.12, -s * 0.02)), top = new Vec3d(-0.03, y0 + 0.62, s * (half - 0.24));
                aArm(out, low, new Vec3d(0.18, y0 - 0.1, s * (half - 0.42)), new Vec3d(-0.2, y0 - 0.1, s * (half - 0.42)), 0.016, arm);
                coilover(out, base, top, 0.36, 0.04, 0.075, spring);
            }
            case DOUBLE_WISHBONE, MULTI_LINK -> {
                Vec3d lowK = k.add(new Vec3d(0, -0.12, 0)), upK = k.add(new Vec3d(0, 0.14, 0));
                Vec3d lf = new Vec3d(0.2, y0 - 0.1, s * (half - 0.46)), lr = new Vec3d(-0.2, y0 - 0.1, s * (half - 0.46));
                Vec3d uf = new Vec3d(0.14, y0 + 0.2, s * (half - 0.38)), ur = new Vec3d(-0.14, y0 + 0.2, s * (half - 0.38));
                if (type == Suspension.Type.MULTI_LINK) {
                    for (Vec3d p : new Vec3d[]{lf, lr, uf, ur}) { out.cylinder(p.y() > y0 ? upK : lowK, p, 0.011, 0.011, 8, arm); }
                    out.cylinder(k.add(new Vec3d(-0.12, -0.04, 0)), new Vec3d(-0.24, y0 - 0.02, s * (half - 0.44)), 0.009, 0.009, 8, arm);
                } else {
                    aArm(out, lowK, lf, lr, 0.016, arm);
                    aArm(out, upK, uf, ur, 0.013, arm);
                }
                Vec3d seat = lowK.add(lf.add(lr).scale(0.5).subtract(lowK).scale(0.35));
                coilover(out, seat, new Vec3d(0, y0 + 0.42, s * (half - 0.4)), 0.3, 0.035, 0.06, spring);
            }
            case TRAILING_ARM, SEMI_TRAILING_ARM -> {
                Vec3d pivot = type == Suspension.Type.TRAILING_ARM ? new Vec3d(0.45, y0 + 0.04, s * (half - 0.2)) : new Vec3d(0.38, y0 + 0.04, s * (half - 0.48));
                Vec3d end = k.add(new Vec3d(0, -0.02, 0));
                beam(out, end, pivot, 0.05, 0.035, arm);
                Vec3d seat = end.add(pivot.subtract(end).scale(0.25));
                coilover(out, seat, new Vec3d(seat.x() - 0.03, y0 + 0.38, seat.z()), 0.28, 0.035, 0.06, spring);
            }
            case SWING_AXLE -> {
                out.cylinder(new Vec3d(0, y0, s * 0.09), k, 0.03, 0.035, 12, arm);
                coilover(out, k.add(new Vec3d(0, 0.06, -s * 0.05)), new Vec3d(0, y0 + 0.42, s * (half - 0.2)), 0.28, 0.035, 0.065, spring);
            }
            case TWIST_BEAM -> {
                Vec3d pivot = new Vec3d(0.45, y0 + 0.04, s * (half - 0.22)), end = k.add(new Vec3d(0, -0.02, 0));
                beam(out, end, pivot, 0.05, 0.035, arm);
                Vec3d onArm = end.add(pivot.subtract(end).scale(0.3));
                double otherArmY = onArm.y() - hub.y() + hub(d, -side, otherCompression).y();
                Vec3d middle = new Vec3d(onArm.x(), 0.5 * (onArm.y() + otherArmY), 0);
                beam(out, onArm, middle, 0.045, 0.06, arm);
                coilover(out, end.add(new Vec3d(-0.02, 0.04, -s * 0.06)), new Vec3d(-0.05, y0 + 0.36, s * (half - 0.3)), 0.26, 0.035, 0.06, spring);
            }
            case SOLID_PANHARD, SOLID_WATT, SOLID_LEAF -> {
                Vec3d mine = hub, other = hub(d, -side, otherCompression), centre = mine.add(other).scale(0.5);
                // half the beam, from this hub to the middle (the differential's housing there when driven)
                out.cylinder(mine.add(new Vec3d(0, 0, -s * 0.06)), centre, 0.04, 0.045, 14, d.differential().driven() ? HOUSING : arm);
                if (d.differential().driven() && side > 0) {
                    out.lathe(centre.add(new Vec3d(-0.13, 0, 0)), centre.add(new Vec3d(0.15, 0, 0)), new double[]{0, 0, 0.2, 0.5, 0.8, 1, 1},
                            new double[]{0, 0.12, 0.15, 0.16, 0.12, 0.06, 0}, null, 28, "differential".equals(highlight) ? Shapes.HIGHLIGHT : HOUSING, false);
                }
                double zs = s * (half - 0.32);
                Vec3d onAxle = new Vec3d(0, mine.y() + (centre.y() - mine.y()) * (half - Math.abs(zs)) / half, zs);
                if (type == Suspension.Type.SOLID_LEAF) {
                    leafPack(out, onAxle, y0, spring);
                    out.cylinder(onAxle.add(new Vec3d(0.12, 0.02, 0)), new Vec3d(0.16, y0 + 0.3, zs * 0.95), 0.022, 0.022, 10, DAMPER);
                } else {
                    coilover(out, onAxle.add(new Vec3d(0, 0.04, 0)), new Vec3d(0, y0 + 0.38, zs), 0.3, 0.04, 0.075, spring);
                    // trailing links forward to the body
                    out.cylinder(onAxle.add(new Vec3d(0, -0.05, 0)), new Vec3d(0.55, y0 - 0.02, zs), 0.016, 0.016, 8, arm);
                    if (side > 0) {
                        if (type == Suspension.Type.SOLID_PANHARD) {
                            out.cylinder(new Vec3d(-0.08, onAxle.y() + 0.05, -zs), new Vec3d(-0.08, y0 + 0.1, zs + s * 0.05), 0.015, 0.015, 8, arm);
                        } else {
                            Vec3d pivot = new Vec3d(-0.15, centre.y(), 0);
                            out.cylinder(pivot.add(new Vec3d(0, 0.08, 0)), pivot.add(new Vec3d(0, -0.08, 0)), 0.02, 0.02, 8, arm);
                            out.cylinder(pivot.add(new Vec3d(0, 0.08, 0)), new Vec3d(-0.15, y0 + 0.08, -zs), 0.013, 0.013, 8, arm);
                            out.cylinder(pivot.add(new Vec3d(0, -0.08, 0)), new Vec3d(-0.15, y0 - 0.08, zs), 0.013, 0.013, 8, arm);
                        }
                    }
                }
            }
        }
        // the half shaft from the differential to the hub, with its boots
        if (d.differential().driven() && !type.solid()) {
            Vec3d inner = new Vec3d(0, y0, s * (d.differential().transverse() ? 0.1 : 0.14)), outer = new Vec3d(0, hub.y(), zk - s * 0.02);
            out.cylinder(inner, outer, 0.014, 0.014, 10, SHAFT);
            Vec3d dir = outer.subtract(inner).normalized();
            out.cylinder(inner, inner.add(dir.scale(0.1)), 0.045, 0.02, 12, BOOT);
            out.cylinder(outer, outer.subtract(dir.scale(0.1)), 0.045, 0.02, 12, BOOT);
        }
        // a tie rod from the rack to the steering arm, which turns with the wheel
        if (d.layout().steered()) {
            double end = Math.max(half - 0.42, 0.2) + 0.09;
            Vec3d armEnd = k.add(Orientation.rotationVector(Vec3d.Y.scale(steer)).rotate(new Vec3d(-0.14, 0.02, -s * 0.03)));
            out.cylinder(new Vec3d(-0.14, y0 + 0.05, s * end), armEnd, 0.011, 0.011, 8, ROD);
        }
        // the anti-roll bar's arm and drop link
        if (d.layout().antiRollNPerM() > 0) {
            double x = type.solid() ? -0.25 : 0.18, inner = Math.max(half - 0.45, 0.2);
            Vec3d bend = new Vec3d(x, y0 - 0.06, s * inner), tip = new Vec3d(x - 0.2 * Math.signum(x + 1e-9), y0 - 0.06, s * inner);
            out.cylinder(bend, tip, 0.012, 0.012, 8, BAR);
            out.cylinder(tip, new Vec3d(tip.x(), hub.y() - 0.08, s * (inner + 0.03)), 0.008, 0.008, 8, ROD);
        }
        return out;
    }

    /** A wishbone: two tubes from the knuckle's ball joint to the body's two pivots, a bushing at each. */
    static void aArm(PartMesh out, Vec3d joint, Vec3d front, Vec3d rear, double r, int color) {
        out.cylinder(joint, front, r, r, 8, color);
        out.cylinder(joint, rear, r, r, 8, color);
        for (Vec3d p : new Vec3d[]{front, rear}) { out.cylinder(p.add(new Vec3d(-0.025, 0, 0)), p.add(new Vec3d(0.025, 0, 0)), 1.7 * r, 1.7 * r, 10, BOOT); }
        out.add(new PartMesh().sphere(1.6 * r, color), Orientation.IDENTITY, joint);
    }

    /** A box-section arm from one point to another, wide at the first and narrower at the second. */
    static void beam(PartMesh out, Vec3d a, Vec3d b, double wa, double wb, int color) {
        Vec3d along = b.subtract(a).normalized(), across = along.cross(Vec3d.Y).normalized(), up = across.cross(along).normalized();
        double h = 0.6;
        out.hexahedron(new Vec3d[]{
                a.add(across.scale(-wa / 2)).add(up.scale(-wa * h / 2)), a.add(across.scale(wa / 2)).add(up.scale(-wa * h / 2)),
                a.add(across.scale(wa / 2)).add(up.scale(wa * h / 2)), a.add(across.scale(-wa / 2)).add(up.scale(wa * h / 2)),
                b.add(across.scale(-wb / 2)).add(up.scale(-wb * h / 2)), b.add(across.scale(wb / 2)).add(up.scale(-wb * h / 2)),
                b.add(across.scale(wb / 2)).add(up.scale(wb * h / 2)), b.add(across.scale(-wb / 2)).add(up.scale(wb * h / 2))}, color);
    }

    /**
     * A damper from its lower eye up to the body with a coil spring round its upper part: the body (dark) over the
     * lower share of its length, the chromed rod above, the spring between two seats.
     */
    static void coilover(PartMesh out, Vec3d low, Vec3d top, double bodyShare, double bodyR, double coilR, int spring) {
        Vec3d axis = top.subtract(low);
        double len = axis.length();
        if (len < 0.05) { return; }
        Vec3d dir = axis.scale(1 / len);
        Vec3d bodyTop = low.add(dir.scale(Math.max(len * bodyShare, Math.min(0.2, 0.7 * len))));
        out.cylinder(low, bodyTop, bodyR, bodyR, 14, DAMPER);
        out.cylinder(bodyTop, top, 0.45 * bodyR, 0.45 * bodyR, 10, ROD);
        Vec3d seat0 = low.add(dir.scale(0.3 * len)), seat1 = top.subtract(dir.scale(0.05 * len));
        out.cylinder(seat0, seat0.add(dir.scale(0.008)), coilR * 1.15, coilR * 1.15, 18, DAMPER);
        out.cylinder(seat1.subtract(dir.scale(0.008)), seat1, coilR * 1.15, coilR * 1.15, 18, DAMPER);
        double coilLen = seat1.subtract(seat0).length();
        if (coilLen > 0.03) { out.tube(PartMesh.helix(seat0, dir, coilR, coilLen, 6, 14), 0.0075, 6, spring); }
    }

    /** A leaf spring's pack along x under (or over) the axle at a point: four leaves, each shorter, arched, with its eyes and shackle. */
    static void leafPack(PartMesh out, Vec3d onAxle, double y0, int color) {
        double len = 1.2, eyeY = y0 + 0.02, seat = onAxle.y() - 0.07, thick = 0.008;
        for (int leaf = 0; leaf < 4; leaf++) {
            double l = len * (1 - 0.2 * leaf);
            List<Vec3d> path = new ArrayList<>();
            for (int i = 0; i <= 12; i++) {
                double xr = (-0.5 + (double) i / 12) * l;
                // the main leaf's arch from the eyes down through the axle's seat, each leaf under the one above
                double y = eyeY + (seat - eyeY) * (1 - Math.pow(2 * xr / len, 2)) - leaf * (thick + 0.002);
                path.add(new Vec3d(onAxle.x() + xr, y, onAxle.z()));
            }
            for (int i = 0; i + 1 < path.size(); i++) {
                Vec3d a = path.get(i), b = path.get(i + 1);
                out.hexahedron(new Vec3d[]{a.add(new Vec3d(0, -thick / 2, -0.03)), b.add(new Vec3d(0, -thick / 2, -0.03)), b.add(new Vec3d(0, -thick / 2, 0.03)),
                        a.add(new Vec3d(0, -thick / 2, 0.03)), a.add(new Vec3d(0, thick / 2, -0.03)), b.add(new Vec3d(0, thick / 2, -0.03)),
                        b.add(new Vec3d(0, thick / 2, 0.03)), a.add(new Vec3d(0, thick / 2, 0.03))}, leaf == 0 ? color : LEAF);
            }
        }
        // the U-bolts round the axle, the eyes and the rear shackle
        out.box(onAxle.add(new Vec3d(-0.05, -0.11, -0.04)), onAxle.add(new Vec3d(0.05, -0.06, 0.04)), HOUSING);
        out.cylinder(new Vec3d(onAxle.x() + len / 2, eyeY, onAxle.z() - 0.035), new Vec3d(onAxle.x() + len / 2, eyeY, onAxle.z() + 0.035), 0.018, 0.018, 10, BOOT);
        out.cylinder(new Vec3d(onAxle.x() - len / 2, eyeY, onAxle.z()), new Vec3d(onAxle.x() - len / 2 - 0.03, eyeY + 0.08, onAxle.z()), 0.012, 0.012, 8, ARM);
    }

    // ---------------------------------------------------------------- landing gear

    /** The leg on the design screen: at its static compression, the wheel turning with the angle. */
    public static EngineModel build(LandingGearDesign d, String highlight) {
        EngineModel m = new EngineModel(new PartMesh());
        double c = d.strut().staticCompressionM();
        if (!Double.isFinite(c)) { c = 0; }
        m.fixed.add(leg(d, c, highlight), Orientation.IDENTITY, Vec3d.ZERO);
        Vec3d hub = new Vec3d(0, -d.leg().lengthM() + c, 0);
        PartMesh still = still(d.tyre(), d.wheel(), d.brake(), highlight, true), turning = turning(d.tyre(), d.wheel(), d.brake(), highlight, true);
        // the brake on the wheel's -z side: inboard for a right leg, mirrored for a left one
        if (d.leg().side().sign < 0) { still = mirroredZ(still); turning = mirroredZ(turning); }
        m.fixed.add(still, Orientation.IDENTITY, hub);
        PartMesh spin = turning;
        m.piece(spin, a -> new EngineModel.Placement(hub, Orientation.rotationVector(Vec3d.Z.scale(-a))));
        return m;
    }

    /**
     * The leg's structure at a compression (m): an oleo's cylinder and piston, links and fork; a spring-steel bar; a
     * rigid fork. The wheel's own inboard side is -z for a left or centre leg, +z for a right one (its brake disc there).
     */
    public static PartMesh leg(LandingGearDesign d, double compression, String highlight) {
        PartMesh out = new PartMesh();
        StrutDesign s = d.strut();
        double len = d.leg().lengthM(), r = Shapes.positive(d.tyre().tyre().radiusM(), 0.2), w = d.tyre().sectionWidthM();
        // toward the fuselage: +z for a left leg, -z for a right one
        int side = d.leg().side().sign, inboard = side > 0 ? -1 : 1;
        Vec3d hub = new Vec3d(0, -len + compression, 0);
        double zc = side == 0 ? 0 : inboard * (0.5 * w + 0.05);
        boolean lit = "strut".equals(highlight) || "leg".equals(highlight);
        switch (s.kind()) {
            case OLEO -> {
                double pr = Math.max(0.5 * s.pistonDiameterM(), 0.012), cr = pr + 0.012, bottom = -(len - r - 0.04 - s.strokeM());
                double crown = hub.y() + r + 0.04;
                out.cylinder(new Vec3d(0, 0.06, zc), new Vec3d(0, bottom, zc), cr, cr, 20, lit ? Shapes.HIGHLIGHT : OLEO);
                out.cylinder(new Vec3d(0, bottom - 0.004, zc), new Vec3d(0, bottom - 0.02, zc), cr + 0.006, cr + 0.006, 20, DAMPER);
                out.cylinder(new Vec3d(0, bottom, zc), new Vec3d(0, crown, zc), pr, pr, 18, CHROME);
                // the torque links: a scissor in front from the cylinder's foot to the fork's crown, folding as it strokes
                Vec3d a = new Vec3d(cr, bottom - 0.01, zc), b = new Vec3d(cr, crown + 0.02, zc);
                double link = 0.55 * Math.max(s.strokeM(), 0.08) + 0.02, gap = Math.max(b.y() - a.y(), 0);
                Vec3d mid = new Vec3d(cr + Math.sqrt(Math.max(link * link - 0.25 * gap * gap, 0)), 0.5 * (a.y() + b.y()), zc);
                out.cylinder(a, mid, 0.008, 0.008, 6, FORK);
                out.cylinder(mid, b, 0.008, 0.008, 6, FORK);
                if (side == 0) { fork(out, hub, crown, w, 0, lit); }
                else {
                    bar(out, new Vec3d(0, crown, zc), new Vec3d(0, hub.y(), zc), 0.05, 0.04, 0.02, 0.018, lit ? Shapes.HIGHLIGHT : FORK);
                    out.cylinder(new Vec3d(0, hub.y(), zc), new Vec3d(0, hub.y(), 0), 0.018, 0.018, 10, CHROME);
                }
                if (d.leg().steer() != dev.mcmod.aerocore.api.vehicle.part.LegPart.Steer.FIXED) {
                    out.cylinder(new Vec3d(0, -0.08, zc), new Vec3d(0, -0.12, zc), cr + 0.015, cr + 0.015, 16, DAMPER);
                }
            }
            case SPRING_STEEL -> {
                // a tapered bar from inboard at the fuselage, curving down and out to the axle's stub
                Vec3d root = new Vec3d(0, 0.04, inboard * 0.35 * len), end = new Vec3d(0, hub.y() + 0.04, inboard * (0.5 * w + 0.04));
                Vec3d c1 = root.add(new Vec3d(0, -0.35 * len, 0)), c2 = end.add(new Vec3d(0, 0.25 * len, inboard * 0.1 * len));
                List<Vec3d> path = PartMesh.bezier(root, c1, c2, end, 12);
                for (int i = 0; i + 1 < path.size(); i++) {
                    double f0 = (double) i / (path.size() - 1), f1 = (double) (i + 1) / (path.size() - 1);
                    bar(out, path.get(i), path.get(i + 1), 0.07 - 0.03 * f0, 0.07 - 0.03 * f1, 0.026 - 0.01 * f0, 0.026 - 0.01 * f1, lit ? Shapes.HIGHLIGHT : LEG_PAINT);
                }
                out.cylinder(end, new Vec3d(0, hub.y(), 0), 0.018, 0.018, 10, FORK);
            }
            case RIGID -> {
                double crown = hub.y() + r + 0.04;
                out.cylinder(new Vec3d(0, 0.04, 0), new Vec3d(0, crown, 0), 0.025, 0.025, 14, lit ? Shapes.HIGHLIGHT : FORK);
                fork(out, hub, crown, w, 0, lit);
            }
        }
        return out;
    }

    /** A fork from its crown down either side of the tyre to the axle (a main leg's one-sided, inboard). */
    static void fork(PartMesh out, Vec3d hub, double crown, double tyreWidth, int side, boolean lit) {
        int color = lit ? Shapes.HIGHLIGHT : FORK;
        double z = 0.5 * tyreWidth + 0.025;
        out.box(new Vec3d(-0.03, crown - 0.015, -z), new Vec3d(0.03, crown + 0.015, z), color);
        for (int s : new int[]{-1, 1}) {
            bar(out, new Vec3d(0, crown, s * z), new Vec3d(0, hub.y(), s * z), 0.05, 0.035, 0.016, 0.014, color);
            out.cylinder(new Vec3d(0, hub.y(), s * z), new Vec3d(0, hub.y(), s * (z - 0.03)), 0.016, 0.016, 10, CHROME);
        }
    }

    /** A flat bar from one point to another, its width (in x) and thickness tapering. */
    static void bar(PartMesh out, Vec3d a, Vec3d b, double wa, double wb, double ta, double tb, int color) {
        Vec3d along = b.subtract(a).normalized(), across = Vec3d.X.subtract(along.scale(along.dot(Vec3d.X)));
        if (across.lengthSquared() < 1e-9) { across = Vec3d.Z; }
        across = across.normalized();
        Vec3d thick = along.cross(across).normalized();
        out.hexahedron(new Vec3d[]{
                a.add(across.scale(-wa / 2)).add(thick.scale(-ta / 2)), a.add(across.scale(wa / 2)).add(thick.scale(-ta / 2)),
                a.add(across.scale(wa / 2)).add(thick.scale(ta / 2)), a.add(across.scale(-wa / 2)).add(thick.scale(ta / 2)),
                b.add(across.scale(-wb / 2)).add(thick.scale(-tb / 2)), b.add(across.scale(wb / 2)).add(thick.scale(-tb / 2)),
                b.add(across.scale(wb / 2)).add(thick.scale(tb / 2)), b.add(across.scale(-wb / 2)).add(thick.scale(tb / 2))}, color);
    }
}
