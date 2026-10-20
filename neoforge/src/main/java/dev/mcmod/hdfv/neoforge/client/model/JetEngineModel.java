package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.part.engine.CompressorDesign;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;

import java.util.ArrayList;
import java.util.List;

import static dev.mcmod.hdfv.neoforge.client.model.Shapes.aboutX;
import static dev.mcmod.hdfv.neoforge.client.model.Shapes.positive;

/**
 * A turbojet drawn from its design, as one looks: a rounded inlet lip (or a ramp intake), the spinner, an axial
 * compressor's drum with a row of twisted rotor blades and stator vanes per stage (the hub rising as the annulus
 * narrows with the pressure ratio), or a centrifugal impeller with backswept blades and splitters before its vaned
 * diffuser; an annular combustor with its liners, dilution holes, fuel nozzles fed from a manifold ring and the
 * igniters; turbine stages of guide vanes and rotor blades on their discs; the exhaust cone and the nozzle (convergent,
 * or opening to its exit area), tinted by heat; flanges, the accessory gearbox with its starter, fuel pump and oil tank.
 * The spool (spinner, compressor and turbine rotors, shaft) turns. Every flow size is the design's. 0.10: with an
 * afterburner, its duct behind the turbine (spray rings and V-gutter flameholders inside, a cooled liner) and a ring of
 * variable nozzle petals; others (the turbo-ramjet) add to the drawing through a Frame.
 * <p>
 * Frame: inlet forward along +x, y up, z starboard. A design screen page names the component drawn highlighted; in a
 * cutaway the cases are see-through.
 */
public final class JetEngineModel {
    private static final int MAX_STAGES = 16, SPINNER = 0xff2e3135, FLANGE = 0xff6d737b, PIPE = 0xffb08d57, GEARBOX = 0xff4a4d52, BLACK = 0xff26282b;

    private final JetEngineDesign j;
    private final String highlight;
    private final boolean cutaway;
    private final PartMesh fixed = new PartMesh(), spool = new PartMesh(), cases = new PartMesh();
    private final EngineModel model = new EngineModel(fixed);

    private final double tip, hub, turbineTip, casing, inletLength, compressorLength, combustorLength, turbineLength, tailpipe, afterburnerLength;
    private final int stages, turbineStages;
    private final boolean axial;
    private final double face, compressorEnd, combustorEnd, turbineEnd, nozzleStart, end, outer;

    private JetEngineModel(JetEngineDesign j, String highlight, boolean cutaway) {
        this.j = j; this.highlight = highlight == null ? "" : highlight; this.cutaway = cutaway;
        CompressorDesign c = j.compressor();
        tip = positive(c.tipDiameterM(), 0.3) / 2;
        hub = Math.clamp(c.hubTipRatio(), 0.05, 0.95) * tip;
        turbineTip = positive(j.turbine().tipDiameterM(), 2 * tip) / 2;
        casing = positive(j.combustor().casingDiameterM(), 2 * tip) / 2;
        stages = Math.clamp(c.stages(), 1, MAX_STAGES);
        turbineStages = Math.clamp(j.turbine().stages(), 1, MAX_STAGES);
        axial = c.kind() == CompressorDesign.Kind.AXIAL;
        inletLength = positive(j.inlet().lengthM(), 0.1);
        compressorLength = 2 * tip * (axial ? 0.06 * stages : 0.4 * stages);
        combustorLength = positive(j.combustor().lengthM(), 0.3);
        turbineLength = 0.1 * 2 * turbineTip * turbineStages;
        tailpipe = positive(j.nozzle().tailpipeLengthM(), 0.2);
        afterburnerLength = j.afterburner().fitted() ? positive(j.afterburner().lengthM(), 0.5) : 0;
        double total = inletLength + compressorLength + combustorLength + turbineLength + afterburnerLength + tailpipe;
        face = total / 2 - inletLength;
        compressorEnd = face - compressorLength;
        combustorEnd = compressorEnd - combustorLength;
        turbineEnd = combustorEnd - turbineLength;
        nozzleStart = turbineEnd - afterburnerLength;
        end = nozzleStart - tailpipe;
        outer = Math.max(Math.max(axial ? tip * 1.04 : tip * 1.4, casing), turbineTip * 1.06);
    }

    public static EngineModel build(JetEngineDesign j, String highlight, boolean cutaway) { return build(j, highlight, cutaway, null); }

    /**
     * Where an engine's parts lie along x (inlet forward), its radii, and the meshes to add to (the cases' mesh is cut
     * in a cutaway), for what another kind of engine draws round this core (0.10: a turbo-ramjet's spike and bypass ducts).
     */
    public record Frame(PartMesh fixed, PartMesh cases, String highlight, double front, double face, double compressorEnd, double combustorEnd,
                        double turbineEnd, double nozzleStart, double end, double tip, double outer, double turbineTip) {
        public int c(String page, int base) { return page.equals(highlight) ? Shapes.HIGHLIGHT : base; }
    }

    public static EngineModel build(JetEngineDesign j, String highlight, boolean cutaway, java.util.function.Consumer<Frame> extra) {
        JetEngineModel m = new JetEngineModel(j, highlight, cutaway);
        m.inlet(); m.compressor(); m.combustor(); m.turbine(); m.afterburner(); m.nozzle(); m.accessories();
        if (extra != null) {
            extra.accept(new Frame(m.fixed, m.casing(), m.highlight, m.face + m.inletLength, m.face, m.compressorEnd, m.combustorEnd, m.turbineEnd, m.nozzleStart, m.end,
                    m.tip, m.outer, m.turbineTip));
        }
        m.model.piece(m.spool, 0, 3, angle -> new EngineModel.Placement(Vec3d.ZERO, aboutX(angle * 3)));
        if (cutaway) { m.model.section(m.cases, Vec3d.ZERO, Vec3d.Z); }   // a half section along the axis
        return m.model;
    }

    private int c(String page, int base) { return page.equals(highlight) ? Shapes.HIGHLIGHT : base; }
    private int shell(String page, int base) { return c(page, base); }
    /** Where the cases and what wraps round them go: drawn whole, or cut in half in a cutaway. */
    private PartMesh casing() { return cutaway ? cases : fixed; }

    /** A flange round the case at x: a flat ring (0.11) with a circle of studs and nuts on its forward face. */
    private void flange(double x, double radius, String page) {
        flangeAt(casing(), x, radius, shell(page, FLANGE));
    }

    /** A bolted flange: its thickness and bolts from its radius, about one bolt every seven threads round. */
    static void flangeAt(PartMesh out, double x, double radius, int colour) {
        double t = 0.012 * radius + 0.0015;
        out.lathe(new Vec3d(x + t, 0, 0), new Vec3d(x - t, 0, 0), new double[]{0, 0, 1, 1}, new double[]{radius * 0.995, radius * 1.07, radius * 1.07, radius * 0.995},
                null, 48, colour, true);
        double thread = Math.clamp(0.012 * radius, 0.003, 0.012);
        int count = (int) Math.clamp(Math.round(2 * Math.PI * radius * 1.035 / (7 * thread)), 12, 64);
        Details.boltCircle(out, new Vec3d(x + t, 0, 0), Vec3d.X, radius * 1.035, count, thread, DetailKit.Part.HEX_NUT, Math.PI / count);
    }

    // ---------------------------------------------------------------- inlet

    private void inlet() {
        int metal = Shapes.material(j.inlet().material());
        double capture = Math.sqrt(positive(j.inlet().captureAreaM2(), Math.PI * tip * tip) / Math.PI), wall = 0.06 * capture;
        double x0 = face + inletLength;
        if (j.inlet().kind() == Turbojet.Inlet.RAMP) {
            // a rectangular duct of the capture area with its compression ramp, faired into the round case
            double side = Math.sqrt(Math.PI) * capture / 2;
            int colour = shell("inlet", metal);
            casing().box(new Vec3d(face + inletLength * 0.35, -side - wall, -side - wall), new Vec3d(x0, -side, side + wall), colour);
            casing().box(new Vec3d(face + inletLength * 0.35, side, -side - wall), new Vec3d(x0, side + wall, side + wall), colour);
            casing().box(new Vec3d(face + inletLength * 0.35, -side, -side - wall), new Vec3d(x0, side, -side), colour);
            casing().box(new Vec3d(face + inletLength * 0.35, -side, side), new Vec3d(x0, side, side + wall), colour);
            casing().hexahedron(new Vec3d[]{new Vec3d(x0 + 0.3 * side, side, -side), new Vec3d(x0 + 0.3 * side, side, side), new Vec3d(x0 - 0.5 * inletLength, side - 0.5 * side, side),
                    new Vec3d(x0 - 0.5 * inletLength, side - 0.5 * side, -side), new Vec3d(x0 + 0.3 * side, side + wall, -side), new Vec3d(x0 + 0.3 * side, side + wall, side),
                    new Vec3d(x0 - 0.5 * inletLength, side - 0.5 * side + wall, side), new Vec3d(x0 - 0.5 * inletLength, side - 0.5 * side + wall, -side)}, c("inlet", metal));
            casing().cylinder(new Vec3d(face + inletLength * 0.35, 0, 0), new Vec3d(face, 0, 0), side * 1.2, tip * 1.03, 32, colour);
            return;
        }
        // a pitot intake: duct wall swept round, with a rounded lip
        List<double[]> p = new ArrayList<>();   // (x, r) round the wall's section: outer surface forward, the lip, inner surface aft
        double rOut0 = tip * 1.03 + wall, rOut1 = capture + wall;
        p.add(new double[]{face, rOut0});
        p.add(new double[]{x0 - wall, rOut1});
        for (int k = 0; k <= 8; k++) {
            double a = Math.PI / 2 - Math.PI * k / 8;
            p.add(new double[]{x0 - wall / 2 + wall / 2 * Math.cos(a), capture + wall / 2 + wall / 2 * Math.sin(a)});
        }
        p.add(new double[]{x0 - wall, capture});
        p.add(new double[]{face, tip * 1.03});
        p.add(new double[]{face, rOut0});
        lathe(casing(), p, null, 48, shell("inlet", metal), false);
        casing().tube(PartMesh.circle(new Vec3d(x0 - wall / 2, 0, 0), Vec3d.X, capture + wall / 2, 48), wall / 2 * 1.05, null, c("inlet", Shapes.machined(metal)), null, 8, true);
        flange(face, rOut0, "inlet");
    }

    /**
     * A solid of revolution about x from a profile of absolute (x, radius) points. A closed profile (its ends together,
     * or both on the axis) is turned to run clockwise in (x, radius), so its faces point out of the metal whichever way
     * it was written (0.11: written the other way, a thin wall's two faces point at each other: the occlusion bake sees
     * the inside of the wall, darkens the outside in blotches and takes both faces for covered); an open sheet keeps
     * the way it was written.
     */
    static void lathe(PartMesh out, List<double[]> profile, int[] colours, int sides, int colour, boolean sharp) {
        if (insideOut(profile)) {
            profile = new ArrayList<>(profile);
            java.util.Collections.reverse(profile);
            if (colours != null) {
                int[] r = new int[colours.length];
                for (int i = 0; i < r.length; i++) { r[i] = colours[r.length - 1 - i]; }
                colours = r;
            }
        }
        double xmin = Double.MAX_VALUE, xmax = -Double.MAX_VALUE;
        for (double[] q : profile) { xmin = Math.min(xmin, q[0]); xmax = Math.max(xmax, q[0]); }
        if (xmax - xmin < 1e-9) { xmax = xmin + 1e-6; }
        double[] along = new double[profile.size()], radius = new double[profile.size()];
        for (int i = 0; i < profile.size(); i++) { along[i] = (profile.get(i)[0] - xmin) / (xmax - xmin); radius[i] = profile.get(i)[1]; }
        out.lathe(new Vec3d(xmin, 0, 0), new Vec3d(xmax, 0, 0), along, radius, colours, sides, colour, sharp);
    }

    /**
     * 0.11: whether what is drawn next lies deep inside the gas path, where nobody sees it from outside (a compressor's
     * rows behind its first, the turbine's ahead of its last): marked so that a vehicle in the world leaves it out (the
     * open gas path lets the occlusion bake's outside air reach it); a cutaway, which shows it, never marks it.
     */
    private void inside(boolean deep) {
        boolean on = deep && !cutaway;
        fixed.interior(on);
        spool.interior(on);
    }

    /** Whether a closed profile runs counter-clockwise in (x, radius): its normals would point into the metal. */
    static boolean insideOut(List<double[]> p) {
        int n = p.size();
        if (n < 3) { return false; }
        double area = 0, perimeter = 0;
        for (int i = 0; i < n; i++) {
            double[] a = p.get(i), b = p.get((i + 1) % n);
            area += a[0] * b[1] - b[0] * a[1];
            if (i + 1 < n) { perimeter += Math.hypot(b[0] - a[0], b[1] - a[1]); }
        }
        double[] first = p.get(0), last = p.get(n - 1);
        boolean closed = Math.hypot(first[0] - last[0], first[1] - last[1]) < 0.2 * perimeter || (first[1] <= 1e-9 && last[1] <= 1e-9);
        return closed && area > 1e-12 * perimeter * perimeter;
    }

    // ---------------------------------------------------------------- compressor

    private double hubAt(double fraction) {
        double pr = Math.max(1.01, j.compressor().pressureRatio());
        double area = (tip * tip - hub * hub) / Math.pow(Math.pow(pr, fraction), 0.75);
        return Math.sqrt(Math.max(hub * hub, tip * tip - area));
    }

    private void compressor() {
        int metal = Shapes.material(j.compressor().material());
        int blade = c("compressor", Shapes.machined(metal)), vane = c("compressor", Shapes.darker(metal, 0.12)), drum = c("compressor", metal);
        // spinner (turns with the spool)
        double nose = 1.8 * hub;
        List<double[]> spinner = new ArrayList<>();
        for (int k = 0; k <= 10; k++) { double t = k / 10.0; spinner.add(new double[]{face + nose * (1 - t), hub * Math.sqrt(1 - (1 - t) * (1 - t)) * (0.12 + 0.88 * t)}); }
        spinner.add(0, new double[]{face + nose, 0});
        spinner.add(new double[]{face, 0});
        lathe(spool, spinner, null, 32, c("compressor", SPINNER), false);
        if (axial) {
            double stage = compressorLength / stages;
            // inlet guide vanes
            Shapes.bladeRow(fixed, face - 0.06 * stage, hub, tip * 0.99, Shapes.blades(hub, tip, 0.5 * stage, 0.9), 0.5 * stage, 0.5 * stage, Math.toRadians(5), Math.toRadians(-12), 0.05, vane, 3, false);
            List<double[]> drumProfile = new ArrayList<>();
            drumProfile.add(new double[]{face, 0});
            for (int k = 0; k < stages; k++) {
                inside(k >= 1);
                double x0 = face - k * stage, h = hubAt((k + 0.5) / stages);
                drumProfile.add(new double[]{x0, h});
                drumProfile.add(new double[]{x0 - stage, h});
                // rotor and stator rows as dense as a real stage (solidity about one), chords as long as the stage allows
                double rotorChord = 0.55 * stage / Math.cos(Math.toRadians(44)), statorChord = 0.38 * stage / Math.cos(Math.toRadians(30)), h2 = hubAt((k + 0.9) / stages);
                Shapes.bladeRow(spool, x0 - 0.3 * stage, h, tip * 0.985, Shapes.blades(h, tip, rotorChord, 1.1), rotorChord, 0.8 * rotorChord,
                        Math.toRadians(30), Math.toRadians(58), 0.08, blade, 3, false);
                Shapes.bladeRow(fixed, x0 - 0.78 * stage, h2, tip * 0.99, Shapes.blades(h2, tip, statorChord, 1.25), statorChord, 0.9 * statorChord,
                        Math.toRadians(-20), Math.toRadians(-38), 0.08, vane, 3, false);
            }
            inside(false);
            drumProfile.add(new double[]{compressorEnd, 0});
            lathe(spool, drumProfile, null, 32, drum, true);
            int caseColour = shell("compressor", metal);
            casing().lathe(new Vec3d(face, 0, 0), new Vec3d(compressorEnd, 0, 0), new double[]{0, 1, 1, 0, 0}, new double[]{tip * 1.06, tip * 1.06, tip * 1.035, tip * 1.035, tip * 1.06}, null, 48, caseColour, true);
            flange(face - 0.5 * compressorLength, tip * 1.035, "compressor");
            flange(compressorEnd, tip * 1.035, "compressor");
            if (stages >= 5) {
                // 0.11: variable stators (as on a J79): unison rings round the first rows, worked by an actuator along the case
                int rows = Math.min(4, stages / 2);
                Vec3d up = new Vec3d(0, Math.cos(2.4), Math.sin(2.4));
                for (int k = 0; k < rows; k++) {
                    double xr = face - k * stage - 0.78 * stage;
                    casing().tube(PartMesh.circle(new Vec3d(xr, 0, 0), Vec3d.X, tip * 1.1, 48), 0.012 * tip, null, c("compressor", Shapes.machined(0xff8d9196)), null, 6, true);
                    casing().box(new Vec3d(xr, 0, 0).add(up.scale(tip * 1.13)), new Vec3d(0.006 * tip, 0.03 * tip, 0.012 * tip), Shapes.yTo(up), c("compressor", 0xff6d737b));
                }
                // above the rockers (they stand to 1.16 tip): it works a bellcrank there, not through them
                Vec3d a0 = new Vec3d(face - 0.05 * compressorLength, 0, 0).add(up.scale(tip * 1.25)), a1 = new Vec3d(face - (rows - 0.2) * stage, 0, 0).add(up.scale(tip * 1.23));
                Details.between(casing(), DetailKit.Part.ACTUATOR, a0, a1, up, 0.07 * tip, Details.SMALL);
            }
        } else {
            double stage = compressorLength / stages;
            for (int k = 0; k < stages; k++) {
                inside(k >= 1);
                double x0 = face - k * stage, eye = 0.6 * tip, hubEye = Math.max(hub, 0.3 * eye);
                impeller(x0, stage, hubEye, eye, blade, drum);
                // vaned diffuser and the housing round it
                double d0 = tip * 1.04, d1 = tip * 1.35, xd = x0 - 0.68 * stage;
                fixed.lathe(new Vec3d(xd + 0.004, 0, 0), new Vec3d(xd - 0.004, 0, 0), new double[]{0, 0, 1, 1}, new double[]{d0 * 0.95, d1, d1, d0 * 0.95}, null, 40, vane, true);
                for (int v = 0; v < 21; v++) {
                    Orientation turn = aboutX(2 * Math.PI * v / 21);
                    Vec3d mid = new Vec3d(xd - 0.012, 0, 0).add(turn.rotate(new Vec3d(0, (d0 + d1) / 2, 0)));
                    fixed.box(mid, new Vec3d(0.008, (d1 - d0) / 2, 0.0012 + 0.006 * tip), turn.multiply(aboutX(0.6)), vane);
                }
                List<double[]> housing = new ArrayList<>();
                housing.add(new double[]{x0 + 0.002, eye * 1.05});
                housing.add(new double[]{x0 - 0.3 * stage, tip * 0.95});
                housing.add(new double[]{x0 - 0.55 * stage, tip * 1.38});
                housing.add(new double[]{x0 - stage, tip * 1.4});
                housing.add(new double[]{x0 - stage, tip * 1.05});
                housing.add(new double[]{x0 + 0.002, eye * 1.05});
                lathe(casing(), housing, null, 48, shell("compressor", metal), false);
            }
            inside(false);
        }
    }

    /** A centrifugal impeller: hub curving from the eye to the tip, backswept blades and splitters following it. */
    private void impeller(double x0, double stage, double hubEye, double eye, int blade, int drum) {
        double length = 0.6 * stage;
        int n = 12;
        List<double[]> hubProfile = new ArrayList<>();
        hubProfile.add(new double[]{x0 + 0.002, 0});
        double[][] meridian = new double[n + 1][];
        for (int k = 0; k <= n; k++) {
            double t = (double) k / n, a = Math.PI / 2 * t;
            // a quarter ellipse from axial at the eye to radial at the tip
            double x = x0 - length * Math.sin(a), rr = hubEye + (tip - hubEye) * (1 - Math.cos(a));
            meridian[k] = new double[]{x, rr};
            hubProfile.add(new double[]{x, rr});
        }
        hubProfile.add(new double[]{x0 - length - 0.004, tip});
        hubProfile.add(new double[]{x0 - length - 0.004, 0});
        lathe(spool, hubProfile, null, 40, drum, false);
        int blades = Math.clamp(j.compressor().blades(), 3, 40);
        for (int bIdx = 0; bIdx < 2 * blades; bIdx++) {
            boolean splitter = bIdx % 2 == 1;
            double phi0 = 2 * Math.PI * bIdx / (2 * blades);
            List<Vec3d> root = new ArrayList<>(), edge = new ArrayList<>();
            for (int k = splitter ? n / 2 : 0; k <= n; k++) {
                double t = (double) k / n, x = meridian[k][0], rr = meridian[k][1];
                double height = (eye - hubEye) * (1 - t) + 0.12 * tip * t;   // blade height: the eye's annulus at the inlet, a sliver at the tip
                double sweep = phi0 - 0.45 * t * t, inducer = (1 - t) * (1 - t) * 0.5;
                double a = Math.PI / 2 * t;
                // offset from the hub along the meridian's normal: radially out at the eye, axially forward at the tip
                double nr = Math.cos(a), na = Math.sin(a);
                Vec3d radial = new Vec3d(0, Math.cos(sweep + inducer), Math.sin(sweep + inducer));
                root.add(new Vec3d(x, 0, 0).add(radial.scale(rr)));
                edge.add(new Vec3d(x + na * height, 0, 0).add(radial.scale(rr + nr * height)));
            }
            spool.sheet(root, edge, blade, true);
        }
    }

    // ---------------------------------------------------------------- combustor

    private void combustor() {
        int metal = Shapes.material(j.combustor().material());
        int liner = c("combustor", Shapes.mix(metal, 0xff7a5a8a, 0.35));
        // the case, bulged over the combustor
        List<double[]> caseProfile = new ArrayList<>();
        double r0 = axial ? tip * 1.035 : tip * 1.05;
        caseProfile.add(new double[]{compressorEnd, r0});
        caseProfile.add(new double[]{compressorEnd - 0.12 * combustorLength, casing});
        caseProfile.add(new double[]{combustorEnd + 0.1 * combustorLength, casing});
        caseProfile.add(new double[]{combustorEnd, turbineTip * 1.06});
        caseProfile.add(new double[]{combustorEnd, turbineTip * 1.04});
        caseProfile.add(new double[]{compressorEnd, r0 * 0.98});
        caseProfile.add(new double[]{compressorEnd, r0});
        lathe(casing(), caseProfile, null, 48, shell("combustor", metal), false);
        flange(compressorEnd, r0, "combustor");
        // annular liner: outer and inner walls with the dome, dilution holes
        double outerLiner = 0.82 * casing, innerLiner = Math.max(0.42 * casing, hubAt(1) * 1.05), x0 = compressorEnd - 0.12 * combustorLength, x1 = combustorEnd + 0.06 * combustorLength;
        if (cutaway) {
            List<double[]> lp = new ArrayList<>();
            lp.add(new double[]{x1, outerLiner * 0.95});
            int rings = 5;
            for (int k = rings - 1; k >= 0; k--) {
                double xa = x0 - 0.03 * combustorLength + (x1 - x0 + 0.03 * combustorLength) * (k + 1.0) / rings, xb = x0 - 0.03 * combustorLength + (x1 - x0 + 0.03 * combustorLength) * k / rings;
                double ra = outerLiner * (0.95 + 0.05 * (rings - 1 - k) / (rings - 1.0));
                lp.add(new double[]{xa, ra * 0.985});
                lp.add(new double[]{xb + 0.12 * (xa - xb), ra});
            }
            lp.add(new double[]{x0 - 0.03 * combustorLength, outerLiner});
            lp.add(new double[]{x0 - 0.06 * combustorLength, 0.5 * (outerLiner + innerLiner)});   // the dome
            lp.add(new double[]{x0 - 0.03 * combustorLength, innerLiner});
            lp.add(new double[]{x1, innerLiner * 1.03});
            lp.add(new double[]{x1, outerLiner * 0.95});
            lathe(casing(), lp, null, 40, liner, false);
            for (int row = 0; row < 2; row++) {
                double x = x0 - (0.35 + 0.3 * row) * combustorLength;
                for (int k = 0; k < 18; k++) {
                    double a = 2 * Math.PI * (k + 0.5 * row) / 18;
                    Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
                    Vec3d at = new Vec3d(x, 0, 0).add(dir.scale(outerLiner * 0.99));
                    casing().cylinder(at, at.add(dir.scale(0.01 * casing)), 0.035 * casing, 0.035 * casing, 8, c("combustor", 0xff2b2526));
                }
            }
        }
        // fuel nozzles through the case into the dome, fed from a manifold ring; two igniters
        int nozzles = 12;
        double ringX = x0 + 0.02 * combustorLength, ring = casing * 1.1;
        casing().tube(PartMesh.circle(new Vec3d(ringX, 0, 0), Vec3d.X, ring, 48), 0.022 * casing, null, c("control", PIPE), null, 6, true);
        for (int k = 0; k < nozzles; k++) {
            double a = 2 * Math.PI * k / nozzles;
            Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
            Vec3d outside = new Vec3d(ringX, 0, 0).add(dir.scale(ring)), inside = new Vec3d(x0 - 0.05 * combustorLength, 0, 0).add(dir.scale(0.5 * (outerLiner + innerLiner)));
            casing().cylinder(outside, outside.add(dir.scale(-(ring - casing) * 1.2)), 0.028 * casing, 0.028 * casing, 8, c("control", Shapes.machined(PIPE)));
            Details.place(casing(), DetailKit.Part.AN_FITTING, new Vec3d(ringX, 0, 0).add(dir.scale(casing)), dir, Vec3d.X, 0.04 * casing, Details.SMALL);
            if (cutaway) {
                casing().tube(PartMesh.bezier(outside.add(dir.scale(-(ring - casing))), new Vec3d(ringX, 0, 0).add(dir.scale(outerLiner * 1.05)),
                        inside.add(new Vec3d(0.04 * combustorLength, 0, 0)), inside, 6), 0.016 * casing, 6, c("control", Shapes.machined(PIPE)));
            }
        }
        // 0.11: two igniter plugs, their exciter box under the case and braided leads to them
        Vec3d exciter = new Vec3d(x0 - 0.5 * combustorLength, -casing * 1.22, 0.25 * casing);
        fixed.roundedBox(exciter.subtract(new Vec3d(0.18 * casing, 0.08 * casing, 0.12 * casing)), exciter.add(new Vec3d(0.18 * casing, 0.08 * casing, 0.12 * casing)), 0.02 * casing, 2,
                c("control", 0xff3a4552));
        for (int k = 0; k < 2; k++) {
            double a = Math.PI / 2 + Math.PI * k;
            Vec3d dir = new Vec3d(0, Math.cos(a + 0.3), Math.sin(a + 0.3));
            Vec3d base = new Vec3d(x0 - 0.3 * combustorLength, 0, 0).add(dir.scale(casing));
            Details.place(casing(), DetailKit.Part.IGNITER, base, dir, Vec3d.X, 0.09 * casing, Details.SMALL);
            Vec3d plugEnd = base.add(dir.scale(0.09 * casing / 0.022 * 0.085)), out = exciter.add(new Vec3d(0, 0.08 * casing, (k == 0 ? 0.06 : -0.06) * casing));
            // round the case, not through it, to the exciter below (the far plug's lead used to cut through the case)
            List<Vec3d> lead = new ArrayList<>(List.of(plugEnd));
            lead.addAll(Details.aroundAxis(plugEnd.add(dir.scale(0.25 * casing)), out, 1.3 * casing, 16));
            Details.braidedLine(fixed, lead, 0.018 * casing);
        }
    }

    // ---------------------------------------------------------------- turbine

    private void turbine() {
        int metal = Shapes.material(j.turbine().material());
        double stage = turbineLength / turbineStages, discHub = 0.58 * turbineTip;
        int vane = c("turbine", Shapes.mix(metal, 0xff4f4566, 0.25)), blade = c("turbine", Shapes.machined(metal)), disc = c("turbine", metal);
        // the shaft from the compressor to the turbine
        double shaft = Math.max(0.06 * tip, 0.004);
        inside(true);
        spool.cylinder(new Vec3d(compressorEnd + 0.002, 0, 0), new Vec3d(turbineEnd, 0, 0), shaft, shaft, 14, c("turbine", Shapes.machined(0xff858b93)));
        for (int k = 0; k < turbineStages; k++) {
            inside(k < turbineStages - 1);   // the last stage shows through the nozzle
            double x0 = combustorEnd - k * stage;
            double vaneChord = 0.36 * stage / Math.cos(Math.toRadians(58)), bladeChord = 0.3 * stage / Math.cos(Math.toRadians(46));
            Shapes.bladeRow(fixed, x0 - 0.22 * stage, discHub, turbineTip, Shapes.blades(discHub, turbineTip, vaneChord, 1.2), vaneChord, 0.9 * vaneChord,
                    Math.toRadians(-62), Math.toRadians(-55), 0.18, vane, 3, false);
            Shapes.bladeRow(spool, x0 - 0.68 * stage, discHub * 1.02, turbineTip * 0.985, Shapes.blades(discHub, turbineTip, bladeChord, 1.35), bladeChord, 0.8 * bladeChord,
                    Math.toRadians(35), Math.toRadians(58), 0.22, blade, 3, false);
            // the disc: a thick hub, a thin web, the rim
            double xd = x0 - 0.68 * stage;
            lathe(spool, List.of(new double[]{xd + 0.18 * stage, 0}, new double[]{xd + 0.18 * stage, shaft * 2.2}, new double[]{xd + 0.06 * stage, discHub * 0.5},
                    new double[]{xd + 0.16 * stage, discHub * 0.98}, new double[]{xd - 0.16 * stage, discHub * 0.98}, new double[]{xd - 0.06 * stage, discHub * 0.5},
                    new double[]{xd - 0.18 * stage, shaft * 2.2}, new double[]{xd - 0.18 * stage, 0}), null, 32, disc, true);
        }
        inside(false);
        casing().lathe(new Vec3d(combustorEnd, 0, 0), new Vec3d(turbineEnd, 0, 0), new double[]{0, 1, 1, 0, 0}, new double[]{turbineTip * 1.08, turbineTip * 1.08, turbineTip * 1.05, turbineTip * 1.05, turbineTip * 1.08}, null, 48, shell("turbine", metal), true);
        flange(combustorEnd, turbineTip * 1.05, "turbine");
        flange(turbineEnd, turbineTip * 1.05, "turbine");
        // 0.11: exhaust gas temperature: thermocouples round the case behind the last stage, their harness ring
        double xe = turbineEnd + 0.25 * stage;
        int probes = 8;
        casing().finish(dev.mcmod.hdfv.look.Finishes.STAINLESS).tube(PartMesh.circle(new Vec3d(xe, 0, 0), Vec3d.X, turbineTip * 1.16, 48), 0.008 * turbineTip, null,
                c("turbine", 0xffaeb2b7), null, 6, true).finish(null);
        for (int k = 0; k < probes; k++) {
            double a = 2 * Math.PI * (k + 0.5) / probes;
            Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
            Details.place(casing(), DetailKit.Part.THERMOCOUPLE, new Vec3d(xe, 0, 0).add(dir.scale(turbineTip * 1.08)), dir, Vec3d.X, 0.035 * turbineTip, Details.SMALL);
        }
    }

    // ---------------------------------------------------------------- afterburner

    /** The reheat duct: a case of the turbine's size with its liner, fuel spray rings and V-gutter flameholder rings inside (seen in a cutaway). */
    private void afterburner() {
        if (afterburnerLength <= 0) { return; }
        int metal = Shapes.material(j.afterburner().material());
        double r = turbineTip * 1.05;
        int[] colours = new int[]{shell("afterburner", Shapes.mix(metal, Shapes.heat(0.25), 0.35)), shell("afterburner", Shapes.mix(metal, Shapes.heat(0.45), 0.45))};
        casing().lathe(new Vec3d(turbineEnd, 0, 0), new Vec3d(nozzleStart, 0, 0), new double[]{0, 1, 1, 0, 0}, new double[]{r * 1.03, r * 1.03, r, r, r * 1.03},
                new int[]{colours[0], colours[1], colours[1], colours[0], colours[0]}, 48, colours[0], true);
        flange(turbineEnd, r, "afterburner");
        flange(nozzleStart, r, "afterburner");
        int gutter = c("afterburner", Shapes.mix(metal, 0xff3a3436, 0.4)), bar = c("control", Shapes.machined(PIPE));
        for (int ring = 0; ring < 2; ring++) {
            double rr = r * (0.45 + 0.3 * ring), x = turbineEnd - (0.35 + 0.08 * ring) * afterburnerLength;
            // a V-gutter ring: two cones meeting at the apex, open downstream
            fixed.lathe(new Vec3d(x + 0.03 * afterburnerLength, 0, 0), new Vec3d(x - 0.03 * afterburnerLength, 0, 0), new double[]{0, 1, 1, 0},
                    new double[]{rr, rr + 0.06 * r, rr - 0.06 * r, rr}, null, 40, gutter, true);
            fixed.tube(PartMesh.circle(new Vec3d(turbineEnd - 0.15 * afterburnerLength, 0, 0), Vec3d.X, rr, 40), 0.012 * r, null, bar, null, 6, true);
        }
        // spray bars through the case to the rings
        for (int k = 0; k < 8; k++) {
            double a = 2 * Math.PI * k / 8;
            Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
            Vec3d at = new Vec3d(turbineEnd - 0.15 * afterburnerLength, 0, 0);
            fixed.cylinder(at.add(dir.scale(0.4 * r)), at.add(dir.scale(r * 1.12)), 0.015 * r, 0.015 * r, 6, bar);
        }
        casing().tube(PartMesh.circle(new Vec3d(turbineEnd - 0.15 * afterburnerLength, 0, 0), Vec3d.X, r * 1.12, 48), 0.02 * r, null, bar, null, 6, true);
    }

    // ---------------------------------------------------------------- nozzle

    private void nozzle() {
        int metal = Shapes.material(j.nozzle().material());
        double throat;
        try { throat = Math.sqrt(j.sizing().nozzleThroatM2() / Math.PI); }
        catch (RuntimeException ex) { throat = 0.6 * turbineTip; }
        throat = Math.clamp(throat, 0.05 * turbineTip, 1.5 * turbineTip);
        double start = turbineTip * 1.05;
        boolean cd = j.nozzle().convergentDivergent(), petalled = afterburnerLength > 0;
        // 0.11: an afterburning engine's converging section is its petals: the shell under them is a straight cone
        // (the rounded one stood above the petals' chord and hid them), the petals from their hinge line to the throat
        double hinge = nozzleStart - 0.15 * tailpipe, petalEnd = cd ? nozzleStart - 0.7 * tailpipe : end;
        List<double[]> p = new ArrayList<>();
        p.add(new double[]{nozzleStart, start});
        if (cd) {
            double exit = throat * Math.sqrt(Math.max(1, j.nozzle().exitAreaRatio()));
            p.add(petalled ? new double[]{hinge, start * 0.97} : new double[]{nozzleStart - 0.35 * tailpipe, start * 0.98});
            p.add(new double[]{nozzleStart - 0.7 * tailpipe, throat * (petalled ? 1.0 : 1.04)});
            p.add(new double[]{end, exit * 1.04});
            p.add(new double[]{end, exit});
            p.add(new double[]{nozzleStart - 0.7 * tailpipe, throat * (petalled ? 0.97 : 1)});
            if (petalled) { p.add(new double[]{hinge, start * 0.93}); }
        } else if (petalled) {
            p.add(new double[]{hinge, start * 0.97});
            p.add(new double[]{end, throat});
            p.add(new double[]{end, throat * 0.97});
            p.add(new double[]{hinge, start * 0.93});
        } else {
            p.add(new double[]{nozzleStart - 0.4 * tailpipe, start * 0.98});
            p.add(new double[]{end, throat * 1.04});
            p.add(new double[]{end, throat});
            p.add(new double[]{nozzleStart - 0.4 * tailpipe, start * 0.94});
        }
        p.add(new double[]{nozzleStart, start * 0.96});
        p.add(new double[]{nozzleStart, start});
        int[] colours = new int[p.size()];
        for (int k = 0; k < p.size(); k++) { colours[k] = shell("nozzle", Shapes.mix(metal, Shapes.heat(0.1 + 0.6 * (nozzleStart - p.get(k)[0]) / Math.max(tailpipe, 1e-6)), 0.55)); }
        casing().hot(true);   // 0.11: the nozzle glows with the gas through it (PartRenderer.glow)
        lathe(casing(), p, colours, 48, colours[0], false);
        if (afterburnerLength > 0) {
            // the variable nozzle's petals over the converging section, overlapping like scales
            int petals = 16;
            double x0 = hinge, x1 = petalEnd;
            // a kit petal bows 0.06 of its width scale toward the axis at its tip: its tip is set out by that much
            double r0 = start * 1.0, width = 2 * Math.PI * r0 / petals * 1.12, r1 = throat * 1.03 + 0.06 * width / 0.3;
            for (int k = 0; k < petals; k++) {
                Orientation turn = aboutX(2 * Math.PI * (k + 0.5) / petals);
                // every other petal stands out by a little more than a plate's thickness (0.012 of the width scale), so neighbours
                // overlap like scales instead of cutting through each other where their edges meet
                double lift = k % 2 == 1 ? 0.02 * width / 0.3 : 0;
                Vec3d from = new Vec3d(x0, 0, 0).add(turn.rotate(new Vec3d(0, r0 + lift, 0))), to = new Vec3d(x1, 0, 0).add(turn.rotate(new Vec3d(0, r1 + lift, 0)));
                // 0.11: a petal from the kit (a curved plate with its rib and hinge knuckle), overlapping its neighbours a little
                Details.between(casing(), DetailKit.Part.NOZZLE_PETAL, from, to, turn.rotate(Vec3d.Z), width, 0);
            }
            casing().hot(false);
            // the sync ring round the petals' hinges and the four actuators that move it
            casing().tube(PartMesh.circle(new Vec3d(x0 + 0.03 * tailpipe, 0, 0), Vec3d.X, start * 1.14, 48), 0.03 * start, null, c("afterburner", Shapes.machined(0xff8d9196)), null, 6, true);
            for (int k = 0; k < 4; k++) {
                Orientation turn = aboutX(2 * Math.PI * (k + 0.125) / 4);
                Vec3d a = new Vec3d(nozzleStart + 0.4 * Math.max(afterburnerLength, tailpipe), 0, 0).add(turn.rotate(new Vec3d(0, start * 1.16, 0)));
                Vec3d b = new Vec3d(x0 + 0.03 * tailpipe, 0, 0).add(turn.rotate(new Vec3d(0, start * 1.15, 0)));
                Details.between(casing(), DetailKit.Part.ACTUATOR, a, b, turn.rotate(Vec3d.Z), 0.12 * start, Details.SMALL);
            }
        }
        casing().hot(false);
        // tail cone on rear frame struts
        double cone = 0.58 * turbineTip;
        fixed.hot(true);
        lathe(fixed, List.of(new double[]{turbineEnd, 0}, new double[]{turbineEnd, cone}, new double[]{turbineEnd - 0.25 * tailpipe, cone * 0.85},
                new double[]{turbineEnd - 0.6 * tailpipe, 0.08 * cone}, new double[]{turbineEnd - 0.62 * tailpipe, 0}), null, 32, c("nozzle", Shapes.mix(metal, Shapes.heat(0.3), 0.4)), false);
        fixed.hot(false);
        for (int k = 0; k < 6; k++) {
            Orientation turn = aboutX(2 * Math.PI * k / 6);
            fixed.box(new Vec3d(turbineEnd - 0.08 * tailpipe, 0, 0).add(turn.rotate(new Vec3d(0, (cone + start) / 2, 0))), new Vec3d(0.06 * tailpipe, (start - cone) / 2, 0.002 + 0.01 * turbineTip),
                    turn, c("nozzle", metal));
        }
    }

    // ---------------------------------------------------------------- accessories

    private void accessories() {
        double box = 0.12 * outer;
        double y = -(outer + box * 1.3);
        double x0 = face - 0.1 * compressorLength, x1 = compressorEnd - 0.05 * combustorLength;
        double length = Math.max(x0 - x1, 0.05);
        // gearbox with the starter, fuel pump and oil tank on it; the drive tower up to the case
        Vec3d centre = new Vec3d((x0 + x1) / 2, y, 0);
        fixed.box(centre, new Vec3d(length / 2, box, 2.5 * box), Orientation.IDENTITY, c("control", GEARBOX));
        fixed.cylinder(new Vec3d((x0 + x1) / 2, y + box, 0), new Vec3d((x0 + x1) / 2, -outer * 0.98, 0), 0.4 * box, 0.4 * box, 12, c("control", GEARBOX));
        // the starter and the fuel pump on the forward face, of their own sizes (0.11: the pump was drawn inside the gearbox,
        // and both were as long as the gearbox)
        Vec3d starter = new Vec3d(x0, y, 1.3 * box), starterEnd = starter.add(new Vec3d(Math.min(0.9 * length, 6 * box), 0, 0));
        fixed.lathe(starter, starterEnd, new double[]{0, 0, 0.1, 0.9, 1, 1}, new double[]{0, 0.9 * box, box, box, 0.7 * box, 0}, null, 18, c("control", BLACK), false);
        Vec3d pump = new Vec3d(x0, y, -1.4 * box), pumpEnd = pump.add(new Vec3d(Math.min(0.5 * length, 3.5 * box), 0, 0));
        fixed.lathe(pump, pumpEnd, new double[]{0, 0, 0.2, 0.8, 1, 1}, new double[]{0, 0.7 * box, 0.8 * box, 0.8 * box, 0.6 * box, 0}, null, 16,
                c("control", Shapes.machined(GEARBOX)), false);
        // the oil tank high on the combustor's case, behind the fuel manifold (0.11: the manifold ring ran through it, and low
        // on the case an igniter's lead runs round to the exciter)
        double tankR = 1.1 * box, tankAt = Math.max(1.06 * outer, casing + 1.2 * tankR), tankA = Math.toRadians(-40), tankEnd = compressorEnd - 0.16 * combustorLength;
        Vec3d tank = new Vec3d(tankEnd - 0.4 * combustorLength, tankAt * Math.cos(tankA), tankAt * Math.sin(tankA));
        fixed.lathe(tank, tank.add(new Vec3d(0.4 * combustorLength, 0, 0)), new double[]{0, 0.05, 0.15, 0.85, 0.95, 1}, new double[]{0, 0.7 * box, tankR, tankR, 0.7 * box, 0},
                null, 18, c("control", Shapes.machined(0xff9ea2a7)), false);
        // fuel line from the pump's fitting aft along the gearbox's side and up to the manifold ring (0.11: it ended short
        // of the ring, inside its radius, next to the oil tank)
        double ringX = compressorEnd - 0.1 * combustorLength, lineZ = -2.9 * box;
        double ringA = Math.asin(Math.clamp(-lineZ / (1.1 * casing), 0, 0.6));
        Vec3d fitting = new Vec3d(x0 + 1.2 * box, y, -2.2 * box), along0 = new Vec3d(x0 + 0.2 * box, y, lineZ), along1 = new Vec3d(x1 - 0.6 * box, y, lineZ);
        Vec3d to = new Vec3d(ringX, -Math.cos(ringA) * casing * 1.1, -Math.sin(ringA) * casing * 1.1), away = new Vec3d(0, -Math.cos(ringA), -Math.sin(ringA));
        List<Vec3d> fuelLine = new ArrayList<>(PartMesh.bezier(fitting, fitting.add(new Vec3d(0, 0, -0.5 * box)), along0.add(new Vec3d(0.6 * box, 0, 0)), along0, 6));
        fuelLine.addAll(Details.line(along0, along1, 8).subList(1, 9));
        fuelLine.addAll(PartMesh.bezier(along1, along1.add(new Vec3d(-0.5 * Math.max(along1.x() - ringX, 2 * box), 0, 0)), to.add(away.scale(0.3 * casing)), to, 10).subList(1, 11));
        Details.braidedLine(fixed, fuelLine, 0.025 * casing);
        Details.clamps(fixed, fuelLine, 0.027 * casing, 0.6 * casing, Vec3d.Z);
        Details.place(fixed, DetailKit.Part.AN_FITTING, fitting.add(new Vec3d(0, 0, 0.6 * box)), Vec3d.Z.scale(-1), Vec3d.X, 0.05 * box / 0.12 * 0.12, Details.SMALL);
        Details.place(fixed, DetailKit.Part.DATA_PLATE, centre.add(new Vec3d(0, 0, 2.5 * box + 0.0005)), Vec3d.Z, Vec3d.X, 0.6 * box, Details.TINY);
        Details.place(fixed, DetailKit.Part.CONNECTOR, starterEnd, Vec3d.X, Vec3d.Y, 0.35 * box, Details.SMALL);
        // trunnion mounts on the sides
        for (int side = -1; side <= 1; side += 2) {
            Vec3d at = new Vec3d(compressorEnd + 0.02 * compressorLength, 0, side * outer);
            fixed.cylinder(at, at.add(new Vec3d(0, 0, side * 0.2 * outer)), 0.12 * outer, 0.12 * outer, 14, c("combustor", FLANGE));
            Details.boltCircle(fixed, at, new Vec3d(0, 0, side), 0.17 * outer, 6, Math.clamp(0.03 * outer, 0.004, 0.016), DetailKit.Part.HEX_BOLT, 0);
        }
    }
}
