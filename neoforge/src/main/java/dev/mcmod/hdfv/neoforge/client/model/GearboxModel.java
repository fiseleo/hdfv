package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.Gearboxes;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;

/**
 * Gearboxes and transfer cases drawn from their designs (0.6), in their parts' frames (the input toward +x, or toward
 * +z across the car): a cast aluminium case with its ribs, the bell housing over the clutch or converter, the tail
 * and its output flange (a transaxle's two drive flanges), an automatic's oil pan and cooler lines, a dual-clutch box's
 * control unit, a CVT's pulley housings, a manual's shift tower and lever; cut away, the gears inside on their shafts
 * (each pair's pitch circles in the ratio of the gear), an automatic's planetary sets and converter, a CVT's pulleys and
 * belt.
 */
public final class GearboxModel {
    private GearboxModel() {}

    static final int CASE = 0xffb9bec4, CASE_DARK = 0xff8e949b, PAN = 0xff4a4e53, GEAR = 0xffc8ccd1, SHAFT = 0xff8a8f95, FLANGE = 0xff5a5e63, KNOB = 0xff1a1b1d,
            LINE = 0xff2b2e31, UNIT = 0xff22252a, BELT = 0xff9da2a8, CONVERTER = 0xffa0a5ab;

    /** The gearbox on the design screen (cut away to show its gears when asked), its shafts turning with the angle. */
    public static EngineModel build(GearboxDesign d, String highlight, boolean cutaway) {
        EngineModel m = new EngineModel(new PartMesh());
        PartMesh casing = new PartMesh(), inside = new PartMesh();
        body(d, highlight, casing, inside);
        Orientation turn = d.gearbox().transverse() ? Orientation.rotationVector(Vec3d.Y.scale(-Math.PI / 2)) : Orientation.IDENTITY;
        PartMesh c = new PartMesh().add(casing, turn, Vec3d.ZERO), in = new PartMesh().add(inside, turn, Vec3d.ZERO);
        if (cutaway) {
            m.section(c, Vec3d.ZERO, d.gearbox().transverse() ? Vec3d.X : Vec3d.Z);
            m.fixed.add(in, Orientation.IDENTITY, Vec3d.ZERO);
        } else {
            m.fixed.add(c, Orientation.IDENTITY, Vec3d.ZERO);
        }
        return m;
    }

    /** The gearbox as one mesh (a vehicle in the world). */
    public static PartMesh mesh(GearboxDesign d) {
        PartMesh casing = new PartMesh(), inside = new PartMesh();
        body(d, null, casing, inside);
        Orientation turn = d.gearbox().transverse() ? Orientation.rotationVector(Vec3d.Y.scale(-Math.PI / 2)) : Orientation.IDENTITY;
        return new PartMesh().add(casing, turn, Vec3d.ZERO);
    }

    /** Case and insides along +x (input forward), sized by the rating. */
    private static void body(GearboxDesign d, String highlight, PartMesh casing, PartMesh inside) {
        double s = Math.cbrt(d.gearbox().scale());
        Gearboxes.Kind kind = d.kind();
        int color = "gearbox".equals(highlight) ? Shapes.HIGHLIGHT : CASE;
        boolean converter = kind == Gearboxes.Kind.AUTOMATIC || kind == Gearboxes.Kind.CVT;
        double bell = (converter ? 0.19 : 0.17) * s, front = 0.32 * s, caseR = 0.12 * s, rear = kind == Gearboxes.Kind.MANUAL ? -0.38 * s : -0.48 * s;
        casing(casing, kind, color, highlight, s, bell, front, caseR, rear);
        // inside: the clutch or converter in the bell, then the gears
        int gears = "gears".equals(highlight) ? Shapes.HIGHLIGHT : GEAR;
        if (converter) {
            int conv = "converter".equals(highlight) ? Shapes.HIGHLIGHT : CONVERTER;
            double cr = 0.5 * d.converter().diameterM() * 0.95;
            inside.lathe(new Vec3d(front - 0.02 * s, 0, 0), new Vec3d(front - 0.14 * s, 0, 0), new double[]{0, 0, 0.3, 0.7, 1, 1},
                    new double[]{0, cr * 0.7, cr, cr, cr * 0.75, 0}, null, 28, conv, false);
        } else {
            int clutch = "clutch".equals(highlight) ? Shapes.HIGHLIGHT : FLANGE;
            double cr = 0.5 * d.clutch().outerDiameterM();
            WheelModel.annulus(inside, new Vec3d(front - 0.04 * s, 0, 0), new Vec3d(front - 0.06 * s, 0, 0), 0.5 * d.clutch().innerDiameterM(), cr, 28, clutch);
            if (kind == Gearboxes.Kind.DUAL_CLUTCH) { WheelModel.annulus(inside, new Vec3d(front - 0.08 * s, 0, 0), new Vec3d(front - 0.1 * s, 0, 0), 0.4 * cr, 0.8 * cr, 28, clutch); }
        }
        double x0 = front - 0.18 * s, x1 = rear + 0.08 * s, spacing = 0.05 * s;
        if (kind == Gearboxes.Kind.CVT) {
            int belt = "variator".equals(highlight) ? Shapes.HIGHLIGHT : BELT;
            for (double[] p : new double[][]{{0.0, 0.07, 0.09}, {-0.2, -0.06, 0.1}}) {
                Vec3d c = new Vec3d(p[0] * s, p[1] * s, 0);
                inside.cylinder(c.add(new Vec3d(0, 0, -0.03 * s)), c, p[2] * s, 0.03 * s, 24, belt);
                inside.cylinder(c.add(new Vec3d(0, 0, 0.03 * s)), c, p[2] * s, 0.03 * s, 24, belt);
            }
            return;
        }
        double[] r = d.ratios();
        double centre = 0.085 * s;
        if (kind == Gearboxes.Kind.AUTOMATIC) {
            // planetary sets: a ring round its planets round a sun, one set for every two gears
            int sets = Math.max(2, (r.length + 1) / 2);
            for (int i = 0; i < sets; i++) {
                double x = x0 - i * (x0 - x1) / Math.max(sets, 1) - 0.02 * s, ring = 0.09 * s;
                WheelModel.annulus(inside, new Vec3d(x, 0, 0), new Vec3d(x - 0.04 * s, 0, 0), ring * 0.85, ring, 32, gears);
                inside.cylinder(new Vec3d(x, 0, 0), new Vec3d(x - 0.04 * s, 0, 0), ring * 0.3, ring * 0.3, 20, SHAFT);
                for (int k = 0; k < 3; k++) {
                    double a = 2 * Math.PI * k / 3;
                    Vec3d c = new Vec3d(x, Math.cos(a) * ring * 0.57, Math.sin(a) * ring * 0.57);
                    inside.cylinder(c, c.add(new Vec3d(-0.04 * s, 0, 0)), ring * 0.24, ring * 0.24, 14, gears);
                }
            }
            inside.cylinder(new Vec3d(front, 0, 0), new Vec3d(rear, 0, 0), 0.015 * s, 0.015 * s, 10, SHAFT);
            return;
        }
        // a layshaft box: the main shaft on the axis, the countershaft below, a pair of gears per ratio
        inside.cylinder(new Vec3d(front, 0, 0), new Vec3d(rear, 0, 0), 0.016 * s, 0.016 * s, 10, SHAFT);
        inside.cylinder(new Vec3d(x0 + 0.02 * s, -centre, 0), new Vec3d(x1, -centre, 0), 0.014 * s, 0.014 * s, 10, SHAFT);
        for (int i = 0; i < r.length; i++) {
            double x = x0 - (i + 1) * Math.min(spacing, (x0 - x1) / (r.length + 1)), main = centre * r[i] / (1 + r[i]), lay = centre - main;
            gear(inside, new Vec3d(x, 0, 0), main, 0.022 * s, gears);
            gear(inside, new Vec3d(x, -centre, 0), lay, 0.022 * s, gears);
        }
    }

    private static final int BOLT = 0xff5d636b, RUBBER = 0xff18191b, PLUG = 0xff2b2d31;
    private static final Vec3d X = Vec3d.X, Y = Vec3d.Y, Z = Vec3d.Z;

    private static Vec3d v(double x, double y, double z) { return new Vec3d(x, y, z); }

    /** The case's outside radius at x along its profile (the lathe's). */
    private static double radiusAt(double x, double front, double rear, double bell, double caseR) {
        double[] along = {0, 0.04, 0.22, 0.3, 0.75, 0.85, 1}, rad = {bell, bell * 1.02, bell * 0.8, caseR * 1.1, caseR, 0.6 * caseR, 0.45 * caseR};
        double f = (front - x) / (front - rear);
        for (int i = 0; i + 1 < along.length; i++) {
            if (f <= along[i + 1]) { return rad[i] + (f - along[i]) / Math.max(along[i + 1] - along[i], 1e-9) * (rad[i + 1] - rad[i]); }
        }
        return rad[rad.length - 1];
    }

    /** A ring round the x axis at x, from 0.9 of a radius out to the radius plus a lip, a width wide. */
    private static void ring(PartMesh out, double x, double width, double r, double lip, int color) {
        out.lathe(v(x + width / 2, 0, 0), v(x - width / 2, 0, 0), new double[]{0, 0, 1, 1, 0}, new double[]{0.9 * r, r + lip, r + lip, 0.9 * r, 0.9 * r}, null, 32, color, true);
    }

    /**
     * 2026-11-22 (redrawn in Blender): a gearbox's casing: the bell housing with its bolted face flange and the starter's
     * pocket, the main case with girth and long ribs, the split flange behind the bell, the tail with its seal and the
     * output flange's studs, the rear mount on its rubber block, the drain and fill plugs, the breather, the speed sensor
     * and its plug; then the kind's own: a manual's shift tower, boot and lever, an automatic's pan (its bolted lip and
     * ribs) with the cooler lines and the harness's plug, a dual-clutch box's ribbed control unit and its plug, a CVT's
     * bolted pulley covers.
     */
    private static void casing(PartMesh out, Gearboxes.Kind kind, int color, String highlight, double s, double bell, double front, double caseR, double rear) {
        double len = front - rear, xs = front - 0.27 * len, rs = radiusAt(xs, front, rear, bell, caseR);
        out.lathe(v(front, 0, 0), v(rear, 0, 0), new double[]{0, 0, 0.04, 0.22, 0.3, 0.75, 0.85, 1, 1},
                new double[]{0, bell, bell * 1.02, bell * 0.8, caseR * 1.1, caseR, 0.6 * caseR, 0.45 * caseR, 0}, null, 32, color, false);
        ring(out, front - 0.008 * s, 0.024 * s, bell / 0.9 * 0.9, 0.06 * bell, color);
        Vec3d starter = new Vec3d(0, Math.cos(Math.toRadians(215)), Math.sin(Math.toRadians(215)));
        out.cylinder(v(front - 0.01 * s, 0, 0).add(starter.scale(0.95 * bell)), v(front - 0.12 * s, 0, 0).add(starter.scale(0.85 * bell)), 0.05 * s, 0.045 * s, 14, color);
        ring(out, xs, 0.016 * s, rs, 0.012 * s, color);
        for (int i = 1; i < 6; i++) {
            double x = xs - i * 0.07 * s;
            if (x < rear + 0.2 * len) { break; }
            double r = radiusAt(x, front, rear, bell, caseR);
            out.lathe(v(x + 0.004 * s, 0, 0), v(x - 0.004 * s, 0, 0), new double[]{0, 0, 1, 1, 0}, new double[]{0.95 * r, r + 0.008 * s, r + 0.008 * s, 0.95 * r, 0.95 * r}, null, 28, color, true);
        }
        double xa = xs - 0.01 * s, xb = rear + 0.22 * len, ra = radiusAt(xa, front, rear, bell, caseR), rb = radiusAt(xb, front, rear, bell, caseR);
        for (int k = 0; k < 6; k++) {
            double a = 2 * Math.PI * k / 6 + Math.PI / 6;
            Vec3d d = v(0, Math.cos(a), Math.sin(a)), side = d.cross(X).normalized().scale(0.004 * s);
            Vec3d a0 = v(xa, 0, 0).add(d.scale(0.95 * ra)), a1 = v(xa, 0, 0).add(d.scale(ra + 0.01 * s)), b1 = v(xb, 0, 0).add(d.scale(rb + 0.006 * s)), b0 = v(xb, 0, 0).add(d.scale(0.95 * rb));
            out.hexahedron(new Vec3d[]{a0.subtract(side), a1.subtract(side), b1.subtract(side), b0.subtract(side), a0.add(side), a1.add(side), b1.add(side), b0.add(side)}, color);
        }
        out.detail(1);
        for (int k = 0; k < 10; k++) {
            double a = 2 * Math.PI * (k + 0.5) / 10;
            Vec3d c = v(front - 0.035 * s, 0, 0).add(v(0, Math.cos(a), Math.sin(a)).scale(1.07 * bell));
            out.cylinder(c.add(X.scale(0.04 * s)), c.subtract(X.scale(0.01 * s)), 0.014 * s, 0.014 * s, 8, color);
            out.cylinder(c.add(X.scale(0.042 * s)), c.add(X.scale(0.05 * s)), 0.008 * s, 0.008 * s, 6, BOLT);
        }
        out.detail(0);
        // the tail: seal, the output flange and its four studs; the rear mount on its rubber block under the tail
        Vec3d tail = v(rear, 0, 0);
        out.cylinder(tail, tail.subtract(X.scale(0.012 * s)), 0.035 * s, 0.035 * s, 16, RUBBER);
        out.cylinder(tail.subtract(X.scale(0.012 * s)), tail.subtract(X.scale(0.03 * s)), 0.06 * s, 0.06 * s, 16, FLANGE);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + Math.PI / 2 * k;
            Vec3d c = tail.subtract(X.scale(0.03 * s)).add(v(0, Math.cos(a), Math.sin(a)).scale(0.042 * s));
            out.cylinder(c, c.subtract(X.scale(0.015 * s)), 0.006 * s, 0.006 * s, 6, BOLT);
        }
        double xm = rear + 0.18 * len, rm = radiusAt(xm, front, rear, bell, caseR);
        out.box(v(xm - 0.03 * s, -rm - 0.02 * s, -0.07 * s), v(xm + 0.03 * s, -rm + 0.01 * s, 0.07 * s), CASE_DARK);
        out.box(v(xm - 0.025 * s, -rm - 0.05 * s, -0.04 * s), v(xm + 0.025 * s, -rm - 0.02 * s, 0.04 * s), RUBBER);
        out.box(v(xm - 0.035 * s, -rm - 0.058 * s, -0.11 * s), v(xm + 0.035 * s, -rm - 0.05 * s, 0.11 * s), PAN);
        // the drain and fill plugs on the right, the breather on top, the speed sensor and its plug on the left
        for (double[] p : new double[][]{{xs - 0.08 * s, -0.75}, {xs - 0.05 * s, -0.1}}) {
            double r = radiusAt(p[0], front, rear, bell, caseR), a = Math.asin(p[1]);
            Vec3d d = v(0, Math.sin(a), Math.cos(a));
            out.cylinder(v(p[0], 0, 0).add(d.scale(0.98 * r)), v(p[0], 0, 0).add(d.scale(r + 0.014 * s)), 0.013 * s, 0.013 * s, 6, PLUG);
        }
        double xv = xs - 0.1 * s, rv = radiusAt(xv, front, rear, bell, caseR);
        out.cylinder(v(xv, 0.97 * rv, 0), v(xv, rv + 0.03 * s, 0), 0.006 * s, 0.006 * s, 6, PLUG);
        out.cylinder(v(xv, rv + 0.03 * s, 0), v(xv, rv + 0.04 * s, 0), 0.012 * s, 0.009 * s, 8, RUBBER);
        double xe = rear + 0.25 * len, re = radiusAt(xe, front, rear, bell, caseR);
        out.cylinder(v(xe, 0.3 * re, -0.95 * re), v(xe, 0.3 * re, -re - 0.03 * s), 0.011 * s, 0.011 * s, 8, UNIT);
        out.box(v(xe - 0.012 * s, 0.3 * re - 0.01 * s, -re - 0.05 * s), v(xe + 0.012 * s, 0.3 * re + 0.012 * s, -re - 0.03 * s), UNIT);
        switch (kind) {
            case MANUAL -> {
                int lever = "gears".equals(highlight) ? Shapes.HIGHLIGHT : KNOB;
                Vec3d tower = v(-0.18 * s, caseR * 0.95, 0);
                out.box(tower.add(v(-0.05 * s, 0, -0.04 * s)), tower.add(v(0.05 * s, 0.06 * s, 0.04 * s)), CASE_DARK);
                for (double sx : new double[]{-1, 1}) for (double sz : new double[]{-1, 1}) {
                    Vec3d c = tower.add(v(sx * 0.04 * s, 0.06 * s, sz * 0.03 * s));
                    out.cylinder(c, c.add(Y.scale(0.008 * s)), 0.006 * s, 0.006 * s, 6, BOLT);
                }
                out.cylinder(tower.add(Y.scale(0.06 * s)), tower.add(v(-0.012 * s, 0.13 * s, 0)), 0.035 * s, 0.012 * s, 12, RUBBER);
                out.cylinder(tower.add(Y.scale(0.06 * s)), tower.add(v(-0.03 * s, 0.3 * s, 0)), 0.008, 0.007, 10, SHAFT);
                out.lathe(tower.add(v(-0.03 * s, 0.3 * s, 0)), tower.add(v(-0.032 * s, 0.35 * s, 0)), new double[]{0, 0, 0.3, 0.7, 1, 1}, new double[]{0, 0.015, 0.024, 0.024, 0.016, 0}, null, 12, lever, false);
            }
            case AUTOMATIC -> {
                double y0 = -caseR * 0.75;
                out.box(v(-0.3 * s, -caseR * 1.25, -caseR * 0.8), v(0.12 * s, y0, caseR * 0.8), PAN);
                out.box(v(-0.31 * s, y0 - 0.008 * s, -caseR * 0.86), v(0.13 * s, y0, caseR * 0.86), PAN);
                for (int i = 0; i < 4; i++) { double x = -0.25 * s + 0.1 * s * i; out.box(v(x - 0.003 * s, -caseR * 1.27, -caseR * 0.7), v(x + 0.003 * s, -caseR * 1.25, caseR * 0.7), PAN); }
                out.detail(1);
                for (int i = 0; i < 7; i++) {
                    double x = -0.29 * s + 0.4 * s * i / 6;
                    for (double z : new double[]{-caseR * 0.83, caseR * 0.83}) { out.cylinder(v(x, y0 - 0.008 * s, z), v(x, y0 - 0.016 * s, z), 0.006 * s, 0.006 * s, 6, BOLT); }
                }
                out.detail(0);
                // the cooler lines run forward along the case to the bell's face (0.14: they ran 0.6 s on, into the engine)
                for (int z : new int[]{-1, 1}) {
                    java.util.List<Vec3d> line = PartMesh.bezier(v(0.05 * s, -0.02, z * caseR), v(0.15 * s, -0.02, z * caseR * 1.6), v(0.25 * s, 0.03, z * caseR * 1.6), v(0.3 * s, 0.05, z * caseR * 1.3), 10);
                    out.tube(line, 0.006, 8, LINE);
                    out.cylinder(line.get(0).add(v(0, 0, -z * 0.01)), line.get(0).add(v(0, 0, z * 0.012)), 0.01, 0.01, 6, BOLT);
                }
                Vec3d plug = v(-0.05 * s, 0.2 * caseR, caseR + 0.03 * s);
                out.cylinder(v(-0.05 * s, 0.2 * caseR, 0.98 * caseR), plug, 0.02 * s, 0.02 * s, 10, UNIT);
                out.tube(java.util.List.of(plug, plug.add(v(0, 0.03 * s, 0.03 * s)), v(0.1 * s, 0.6 * caseR, caseR + 0.07 * s), v(0.25 * s, 0.9 * caseR, caseR + 0.05 * s)), 0.008, 6, LINE);
            }
            case DUAL_CLUTCH -> {
                out.box(v(-0.15 * s, -0.05 * s, caseR * 0.85), v(0.12 * s, 0.08 * s, caseR * 1.35), UNIT);
                for (int i = 0; i < 6; i++) { double x = -0.13 * s + 0.046 * s * i; out.box(v(x - 0.003 * s, -0.045 * s, caseR * 1.35), v(x + 0.003 * s, 0.075 * s, caseR * 1.38), UNIT); }
                out.box(v(0.06 * s, 0.08 * s, caseR * 0.95), v(0.1 * s, 0.1 * s, caseR * 1.2), RUBBER);
                out.tube(java.util.List.of(v(0.08 * s, 0.1 * s, caseR * 1.07), v(0.08 * s, 0.14 * s, caseR * 1.07), v(0.2 * s, 0.17 * s, caseR * 0.9)), 0.008, 6, LINE);
            }
            case CVT -> {
                for (double[] p : new double[][]{{0.0, 0.07, 0.11}, {-0.2, -0.06, 0.12}}) {
                    Vec3d c = v(p[0] * s, p[1] * s, 0);
                    double r = p[2] * s;
                    out.lathe(c.add(Z.scale(caseR * 0.6)), c.subtract(Z.scale(caseR * 0.6)), new double[]{0, 0, 1, 1}, new double[]{0, r, r, 0}, null, 24, color, true);
                    for (int k = 0; k < 8; k++) {
                        double a = 2 * Math.PI * k / 8;
                        Vec3d b = c.add(v(Math.cos(a) * r * 0.9, Math.sin(a) * r * 0.9, caseR * 0.6));
                        out.cylinder(b, b.add(Z.scale(0.008 * s)), 0.006 * s, 0.006 * s, 6, BOLT);
                    }
                }
                out.box(v(-0.32 * s, -caseR * 1.2, -caseR * 0.7), v(0.1 * s, -caseR * 0.8, caseR * 0.7), PAN);
            }
        }
    }

    /** A spur gear's outline (teeth of about a 3 mm module) extruded along x at a centre. */
    private static void gear(PartMesh out, Vec3d centre, double pitchR, double width, int color) {
        double module = 0.003;
        int teeth = Math.max(10, (int) Math.round(2 * pitchR / module));
        out.extrude(Shapes.gear(pitchR - 1.25 * module, pitchR + module, teeth), centre, Vec3d.Z, Vec3d.Y, new Vec3d(-width, 0, 0), color, false);
    }

    // ---------------------------------------------------------------- transfer cases

    /** A transfer case: its case with the input at the front, the rear output behind and the front output offset low to the side. */
    public static EngineModel build(TransferCaseDesign d, String highlight) {
        EngineModel m = new EngineModel(new PartMesh());
        m.fixed.add(mesh(d), Orientation.IDENTITY, Vec3d.ZERO);
        return m;
    }

    /**
     * 2026-11-22 (redrawn in Blender): two cast halves (front and rear) round the input and the chain down to the front
     * output, the split flange with its bolts, the input's seal, the rear output's housing and flange, the front output's,
     * the outputs' studs, the vent, the drain and fill plugs; the shift lever in its boot (part-time or with a low range),
     * an on-demand coupling's control unit, motor and plug.
     */
    public static PartMesh mesh(TransferCaseDesign d) {
        double s = Math.cbrt(d.scale()), hu = 0.13 * s, hv = 0.17 * s, cy = -0.05 * s;
        PartMesh out = new PartMesh();
        out.extrude(rounded(hu, hv, 0.07 * s), v(0.15 * s, cy, 0.01 * s), Z, Y, X.scale(-0.145 * s), CASE, false);
        out.extrude(rounded(hu, hv, 0.07 * s), v(0.005 * s, cy, 0.01 * s), Z, Y, X.scale(-0.155 * s), CASE, false);
        out.extrude(rounded(hu + 0.012 * s, hv + 0.012 * s, 0.075 * s), v(0.009 * s, cy, 0.01 * s), Z, Y, X.scale(-0.018 * s), CASE, false);
        out.cylinder(v(0.15 * s, 0, 0), v(0.18 * s, 0, 0), 0.06 * s, 0.055 * s, 16, CASE);
        out.cylinder(v(0.18 * s, 0, 0), v(0.19 * s, 0, 0), 0.035 * s, 0.035 * s, 14, RUBBER);
        out.cylinder(v(-0.15 * s, 0, 0), v(-0.24 * s, 0, 0), 0.05 * s, 0.04 * s, 16, CASE_DARK);
        out.cylinder(v(-0.24 * s, 0, 0), v(-0.26 * s, 0, 0), 0.055 * s, 0.055 * s, 16, FLANGE);
        Vec3d front = v(0.15 * s, -0.16 * s, 0.07 * s);
        out.cylinder(front.subtract(X.scale(0.01 * s)), front.add(v(0.06 * s, 0, 0)), 0.045 * s, 0.04 * s, 16, CASE_DARK);
        out.cylinder(front.add(v(0.06 * s, 0, 0)), front.add(v(0.08 * s, 0, 0)), 0.05 * s, 0.05 * s, 16, FLANGE);
        out.detail(1);
        for (int k = 0; k < 12; k++) {
            double a = 2 * Math.PI * k / 12;
            Vec3d p = v(0.012 * s, cy + (hv + 0.006 * s) * Math.sin(a), 0.01 * s + (hu + 0.006 * s) * Math.cos(a));
            out.cylinder(p, p.add(X.scale(0.01 * s)), 0.006 * s, 0.006 * s, 6, BOLT);
        }
        for (Vec3d c : new Vec3d[]{v(-0.26 * s, 0, 0), front.add(v(0.08 * s, 0, 0))}) {
            for (int k = 0; k < 4; k++) {
                double a = Math.PI / 4 + Math.PI / 2 * k;
                Vec3d p = c.add(v(0, Math.cos(a), Math.sin(a)).scale(0.038 * s));
                out.cylinder(p, p.add(X.scale(c.x() > 0 ? 0.012 * s : -0.012 * s)), 0.005 * s, 0.005 * s, 6, BOLT);
            }
        }
        out.detail(0);
        out.cylinder(v(-0.05 * s, 0.12 * s, 0.06 * s), v(-0.05 * s, 0.16 * s, 0.06 * s), 0.005 * s, 0.005 * s, 6, PLUG);
        out.cylinder(v(-0.05 * s, 0.16 * s, 0.06 * s), v(-0.05 * s, 0.17 * s, 0.06 * s), 0.01 * s, 0.008 * s, 8, RUBBER);
        for (double y : new double[]{-0.2 * s, -0.05 * s}) { out.cylinder(v(-0.07 * s, y, 0.14 * s), v(-0.07 * s, y, 0.155 * s), 0.012 * s, 0.012 * s, 6, PLUG); }
        if (d.kind() == TransferPart.Kind.PART_TIME || d.lowRatio() > 1) {
            Vec3d tower = v(0, 0.12 * s, 0);
            out.cylinder(tower, tower.add(v(0.012 * s, 0.07 * s, 0)), 0.03 * s, 0.01 * s, 12, RUBBER);
            out.cylinder(tower, tower.add(v(0.04 * s, 0.32 * s, 0)), 0.007, 0.006, 8, SHAFT);
            out.lathe(tower.add(v(0.04 * s, 0.32 * s, 0)), tower.add(v(0.045 * s, 0.37 * s, 0)), new double[]{0, 0, 0.3, 0.7, 1, 1}, new double[]{0, 0.014, 0.022, 0.022, 0.015, 0}, null, 12, KNOB, false);
        }
        if (d.kind() == TransferPart.Kind.ON_DEMAND) {
            out.box(v(-0.08 * s, 0.12 * s, -0.08 * s), v(0.06 * s, 0.2 * s, 0.08 * s), UNIT);
            out.cylinder(v(-0.01 * s, 0.16 * s, 0.08 * s), v(-0.01 * s, 0.16 * s, 0.15 * s), 0.035 * s, 0.035 * s, 14, UNIT);
            out.box(v(0, 0.2 * s, -0.03 * s), v(0.04 * s, 0.23 * s, 0.01 * s), RUBBER);
        }
        return out;
    }

    /** A rounded rectangle's outline (u, v): half sizes hu and hv, corners of radius r, counter-clockwise from the right. */
    private static double[][] rounded(double hu, double hv, double r) {
        double[][] p = new double[20][];
        double[][] c = {{hu - r, hv - r, 0}, {-(hu - r), hv - r, 90}, {-(hu - r), -(hv - r), 180}, {hu - r, -(hv - r), 270}};
        for (int i = 0; i < 4; i++) for (int k = 0; k <= 4; k++) {
            double a = Math.toRadians(c[i][2] + 90.0 * k / 4);
            p[5 * i + k] = new double[]{c[i][0] + r * Math.cos(a), c[i][1] + r * Math.sin(a)};
        }
        return p;
    }
}
