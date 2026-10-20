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
        // bell housing, the main case tapering back, the tail and its flange
        casing.lathe(new Vec3d(front, 0, 0), new Vec3d(rear, 0, 0), new double[]{0, 0, 0.04, 0.22, 0.3, 0.75, 0.85, 1, 1},
                new double[]{0, bell, bell * 1.02, bell * 0.8, caseR * 1.1, caseR, 0.6 * caseR, 0.45 * caseR, 0}, null, 32, color, false);
        for (int k = 0; k < 6; k++) {
            double a = 2 * Math.PI * k / 6 + 0.26;
            Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
            casing.box(new Vec3d(front - 0.02 * s, 0, 0).add(dir.scale(bell * 0.98)), new Vec3d(front - 0.02 * s, 0, 0).add(dir.scale(bell * 1.08)).add(new Vec3d(-0.015 * s, 0, 0)),
                    CASE_DARK);
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + Math.PI / 2 * k;
            Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
            casing.box(new Vec3d(0.1 * s, 0, 0).add(dir.scale(caseR * 0.95)).add(new Vec3d(0, 0, -0.006)),
                    new Vec3d(-0.3 * s, 0, 0).add(dir.scale(caseR * 1.08)).add(new Vec3d(0, 0, 0.006)), CASE_DARK);
        }
        Vec3d tail = new Vec3d(rear, 0, 0);
        casing.cylinder(tail, tail.add(new Vec3d(-0.025 * s, 0, 0)), 0.06 * s, 0.06 * s, 16, FLANGE);
        switch (kind) {
            case MANUAL -> {
                int lever = "gears".equals(highlight) ? Shapes.HIGHLIGHT : KNOB;
                Vec3d tower = new Vec3d(-0.18 * s, caseR * 0.95, 0);
                casing.box(tower.add(new Vec3d(-0.05 * s, 0, -0.04 * s)), tower.add(new Vec3d(0.05 * s, 0.06 * s, 0.04 * s)), CASE_DARK);
                casing.cylinder(tower.add(new Vec3d(0, 0.06 * s, 0)), tower.add(new Vec3d(-0.03 * s, 0.3 * s, 0)), 0.008, 0.007, 10, SHAFT);
                casing.add(new PartMesh().sphere(0.025, lever), Orientation.IDENTITY, tower.add(new Vec3d(-0.03 * s, 0.32 * s, 0)));
            }
            case AUTOMATIC -> {
                casing.box(new Vec3d(-0.3 * s, -caseR * 1.25, -caseR * 0.8), new Vec3d(0.12 * s, -caseR * 0.75, caseR * 0.8), PAN);
                // the cooler lines run forward along the case to the bell's face (0.14: they ran 0.6 s on, into the engine)
                for (int z : new int[]{-1, 1}) {
                    casing.tube(PartMesh.bezier(new Vec3d(0.05 * s, -0.02, z * caseR), new Vec3d(0.15 * s, -0.02, z * caseR * 1.6), new Vec3d(0.25 * s, 0.03, z * caseR * 1.6),
                            new Vec3d(0.3 * s, 0.05, z * caseR * 1.3), 10), 0.006, 8, LINE);
                }
            }
            case DUAL_CLUTCH -> casing.box(new Vec3d(-0.15 * s, -0.05 * s, caseR * 0.85), new Vec3d(0.12 * s, 0.08 * s, caseR * 1.35), UNIT);
            case CVT -> {
                casing.lathe(new Vec3d(0.0, 0.07 * s, caseR * 0.6), new Vec3d(0.0, 0.07 * s, -caseR * 0.6), new double[]{0, 0, 1, 1},
                        new double[]{0, 0.11 * s, 0.11 * s, 0}, null, 24, color, true);
                casing.lathe(new Vec3d(-0.2 * s, -0.06 * s, caseR * 0.6), new Vec3d(-0.2 * s, -0.06 * s, -caseR * 0.6), new double[]{0, 0, 1, 1},
                        new double[]{0, 0.12 * s, 0.12 * s, 0}, null, 24, color, true);
                casing.box(new Vec3d(-0.32 * s, -caseR * 1.2, -caseR * 0.7), new Vec3d(0.1 * s, -caseR * 0.8, caseR * 0.7), PAN);
            }
        }
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

    public static PartMesh mesh(TransferCaseDesign d) {
        double s = Math.cbrt(d.scale());
        PartMesh out = new PartMesh();
        out.box(new Vec3d(-0.15 * s, -0.22 * s, -0.12 * s), new Vec3d(0.15 * s, 0.12 * s, 0.14 * s), CASE);
        out.lathe(new Vec3d(0.15 * s, 0, 0), new Vec3d(-0.15 * s, 0, 0), new double[]{0, 0, 1, 1}, new double[]{0, 0.13 * s, 0.13 * s, 0}, null, 24, CASE, true);
        out.cylinder(new Vec3d(-0.15 * s, 0, 0), new Vec3d(-0.24 * s, 0, 0), 0.05 * s, 0.04 * s, 16, CASE_DARK);
        out.cylinder(new Vec3d(-0.24 * s, 0, 0), new Vec3d(-0.26 * s, 0, 0), 0.055 * s, 0.055 * s, 16, FLANGE);
        Vec3d front = new Vec3d(0.15 * s, -0.16 * s, 0.07 * s);
        out.cylinder(front, front.add(new Vec3d(0.06 * s, 0, 0)), 0.045 * s, 0.04 * s, 16, CASE_DARK);
        out.cylinder(front.add(new Vec3d(0.06 * s, 0, 0)), front.add(new Vec3d(0.08 * s, 0, 0)), 0.05 * s, 0.05 * s, 16, FLANGE);
        if (d.kind() == TransferPart.Kind.PART_TIME || d.lowRatio() > 1) {
            Vec3d tower = new Vec3d(0, 0.12 * s, 0);
            out.cylinder(tower, tower.add(new Vec3d(0.04 * s, 0.32 * s, 0)), 0.007, 0.006, 8, SHAFT);
            out.add(new PartMesh().sphere(0.022, KNOB), Orientation.IDENTITY, tower.add(new Vec3d(0.045 * s, 0.34 * s, 0)));
        }
        if (d.kind() == TransferPart.Kind.ON_DEMAND) { out.box(new Vec3d(-0.08 * s, 0.12 * s, -0.08 * s), new Vec3d(0.06 * s, 0.2 * s, 0.08 * s), UNIT); }
        return out;
    }
}
