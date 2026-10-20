package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.part.engine.CombustorDesign;
import dev.mcmod.hdfv.part.engine.CompressorDesign;
import dev.mcmod.hdfv.part.engine.FanDesign;
import dev.mcmod.hdfv.part.engine.ReductionGearboxDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;

import java.util.ArrayList;
import java.util.List;

import static dev.mcmod.hdfv.neoforge.client.model.JetEngineModel.lathe;
import static dev.mcmod.hdfv.neoforge.client.model.Shapes.aboutX;
import static dev.mcmod.hdfv.neoforge.client.model.Shapes.positive;

/**
 * The two-spool gas turbines drawn from their designs, as they look.
 * <ul>
 * <li>Turbofan: the nacelle with its rounded lip, the spinner and wide-chord fan blades, the outlet guide vanes and
 *   fan frame struts in the bypass duct, the splitter, booster stages, the core's compressor (axial stages, or axial
 *   stages and an impeller), the annular combustor with its fuel nozzles, the core and fan turbines, then separate jets
 *   (the fan nozzle at the nacelle's end, the core nozzle round its plug) or a lobed mixer inside a long nacelle; the
 *   accessory gearbox under the fan case. Fan and core spools turn at their own speeds.</li>
 * <li>Free-turbine shaft engine with a reverse-flow combustor (PT6A-like): the propeller flange and reduction gearbox in
 *   front, the exhaust case with its two stacks, the power and gas generator turbines inside the folded combustor, the
 *   impeller and axial stages, the inlet screen round the rear and the accessory gearbox behind it; the gas flows
 *   forward through the engine.</li>
 * <li>Free-turbine shaft engine with a straight-through combustor (T700-like): the output shaft in front through the
 *   annular inlet's swirl vanes (behind a gearbox, if any), axial stages and an impeller, the combustor, the gas
 *   generator and power turbines, the exhaust diffuser.</li>
 * </ul>
 * Frame: output or inlet forward along +x, y up, z starboard. A design screen page names the component drawn
 * highlighted; in a cutaway the cases are cut in half.
 */
public final class GasTurbineModel {
    private static final int SPINNER = 0xff2e3135, FLANGE = 0xff6d737b, PIPE = 0xffb08d57, GEARBOX = 0xff4a4d52, BLACK = 0xff26282b,
            NACELLE = 0xffd7dadd, SCREEN = 0xff55595e, MAX_STAGES = 12, MAX_BLADES = 90;

    private final String highlight;
    private final boolean cutaway;
    private final PartMesh fixed = new PartMesh(), cases = new PartMesh(), low = new PartMesh(), high = new PartMesh(), output = new PartMesh();
    /** Where the output's axis is (an offset gearbox's output lies above the engine's). */
    private Vec3d outputAxis = Vec3d.ZERO;

    private GasTurbineModel(String highlight, boolean cutaway) { this.highlight = highlight == null ? "" : highlight; this.cutaway = cutaway; }

    private int c(String page, int base) { return page.equals(highlight) ? Shapes.HIGHLIGHT : base; }
    /** Where the cases and what wraps round them go: drawn whole, or cut in half in a cutaway. */
    private PartMesh casing() { return cutaway ? cases : fixed; }
    private static double rad(double degrees) { return Math.toRadians(degrees); }
    /** 0.11: while drawing a compressor that is all inside (a turbofan's core, behind its booster). */
    private boolean deepCompressor;
    /** 0.11: while drawing a combustor under a turbofan's core cowl: its manifold, igniters and exciter are covered (they stood out through the cowl). */
    private boolean cowled;

    private static int count(double hub, double tip, double chord, double solidity) { return Math.min(MAX_BLADES, Shapes.blades(hub, tip, chord, solidity)); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /**
     * The model: fixed parts, the cases (sectioned in a cutaway) and three turning sets at their rates (radians per
     * radian of the preview's angle); a flipped model is turned end for end (the gas then flows forward).
     */
    private EngineModel finish(boolean flip, double lowRate, double highRate, double outputRate) { return finish(flip, lowRate, highRate, outputRate, 1); }

    /**
     * 0.11: in the world the high set turns with the part's second rotor; the low set with its first (a turbofan's fan),
     * or through a gearbox of a ratio from it (a shaft engine's first rotor is its output: the power turbine turns
     * ratio times as fast); the output set with the first.
     */
    private EngineModel finish(boolean flip, double lowRate, double highRate, double outputRate, double gear) {
        Orientation f = flip ? Orientation.rotationVector(Vec3d.Y.scale(Math.PI)) : Orientation.IDENTITY;
        EngineModel m = new EngineModel(flip ? new PartMesh().add(fixed, f, Vec3d.ZERO) : fixed);
        m.piece(flip ? new PartMesh().add(low, f, Vec3d.ZERO) : low, 0, lowRate / gear, a -> new EngineModel.Placement(Vec3d.ZERO, aboutX(a * lowRate)));
        m.piece(flip ? new PartMesh().add(high, f, Vec3d.ZERO) : high, 1, highRate, a -> new EngineModel.Placement(Vec3d.ZERO, aboutX(a * highRate)));
        Vec3d axis = outputAxis;
        m.piece(flip ? new PartMesh().add(output, f, Vec3d.ZERO) : output, 0, outputRate, a -> new EngineModel.Placement(axis, aboutX(a * outputRate)));
        if (cutaway) { m.section(flip ? new PartMesh().add(cases, f, Vec3d.ZERO) : cases, Vec3d.ZERO, Vec3d.Z); }
        return m;
    }

    /** A bolted flange (0.11: a flat ring with studs and nuts, as the turbojet's). */
    private void flange(PartMesh out, double x, double radius, int colour) {
        JetEngineModel.flangeAt(out, x, radius, colour);
    }

    // ================================================================ shared components (the gas flows towards -x)

    /** A spinner from its nose at x + length back to x, of a base radius. */
    private void spinner(PartMesh out, double x, double radius, double length, int colour) {
        List<double[]> p = new ArrayList<>();
        p.add(new double[]{x + length, 0});
        for (int k = 0; k <= 10; k++) { double t = k / 10.0; p.add(new double[]{x + length * (1 - t), radius * Math.sqrt(1 - (1 - t) * (1 - t)) * (0.1 + 0.9 * t)}); }
        p.add(new double[]{x, 0});
        lathe(out, p, null, 32, colour, false);
    }

    /**
     * Axial stages from x0 aft over a length: the drum rising from hub0 to hub1 and a rotor and a stator row per stage on
     * it, the tip from tip0 to tip1; the case round them (or none, inside another case).
     */
    /** 0.11: whether what is drawn next lies deep inside (as JetEngineModel.inside: a vehicle in the world leaves it out; never in a cutaway). */
    private void inside(boolean deep) {
        boolean on = deep && !cutaway;
        fixed.interior(on); low.interior(on); high.interior(on); output.interior(on);
    }

    private void axialStages(PartMesh rotor, String page, int metal, double x0, double length, int stages, double tip0, double tip1, double hub0, double hub1,
                             boolean guideVanes, boolean withCase) {
        int blade = c(page, Shapes.machined(metal)), vane = c(page, Shapes.darker(metal, 0.12)), drum = c(page, metal);
        stages = Math.clamp(stages, 1, MAX_STAGES);
        double stage = length / stages;
        if (guideVanes) {
            Shapes.bladeRow(fixed, x0 - 0.06 * stage, hub0, tip0 * 0.99, count(hub0, tip0, 0.5 * stage, 0.9), 0.5 * stage, 0.5 * stage, rad(5), rad(-12), 0.05, vane, 3, false);
        }
        List<double[]> drumProfile = new ArrayList<>();
        drumProfile.add(new double[]{x0, 0});
        for (int k = 0; k < stages; k++) {
            inside(k >= 1 || deepCompressor);
            double xs = x0 - k * stage, t = (k + 0.5) / stages, h = lerp(hub0, hub1, t), tip = lerp(tip0, tip1, t);
            drumProfile.add(new double[]{xs, h});
            drumProfile.add(new double[]{xs - stage, h});
            double rotorChord = 0.55 * stage / Math.cos(rad(44)), statorChord = 0.38 * stage / Math.cos(rad(30));
            Shapes.bladeRow(rotor, xs - 0.3 * stage, h, tip * 0.985, count(h, tip, rotorChord, 1.1), rotorChord, 0.8 * rotorChord, rad(30), rad(58), 0.08, blade, 3, false);
            double h2 = lerp(hub0, hub1, (k + 0.9) / stages), tip2 = lerp(tip0, tip1, (k + 0.9) / stages);
            Shapes.bladeRow(fixed, xs - 0.78 * stage, h2, tip2 * 0.99, count(h2, tip2, statorChord, 1.25), statorChord, 0.9 * statorChord, rad(-20), rad(-38), 0.08, vane, 3, false);
        }
        inside(false);
        drumProfile.add(new double[]{x0 - length, 0});
        lathe(rotor, drumProfile, null, 32, drum, true);
        if (withCase) {
            lathe(casing(), List.of(new double[]{x0, tip0 * 1.06}, new double[]{x0 - length, tip1 * 1.06}, new double[]{x0 - length, tip1 * 1.035},
                    new double[]{x0, tip0 * 1.035}, new double[]{x0, tip0 * 1.06}), null, 48, c(page, metal), true);
        }
    }

    /** A centrifugal impeller from its eye at x0 over a length: hub curving from the eye to the tip, backswept blades and splitters. */
    private void impeller(PartMesh rotor, double x0, double length, double hubEye, double eye, double tip, int blades, int blade, int drum) {
        int n = 12;
        List<double[]> hubProfile = new ArrayList<>();
        hubProfile.add(new double[]{x0 + 0.002, 0});
        double[][] meridian = new double[n + 1][];
        for (int k = 0; k <= n; k++) {
            double a = Math.PI / 2 * k / n;
            meridian[k] = new double[]{x0 - length * Math.sin(a), hubEye + (tip - hubEye) * (1 - Math.cos(a))};
            hubProfile.add(meridian[k]);
        }
        hubProfile.add(new double[]{x0 - length - 0.004, tip});
        hubProfile.add(new double[]{x0 - length - 0.004, 0});
        lathe(rotor, hubProfile, null, 40, drum, false);
        blades = Math.clamp(blades, 3, 40);
        for (int b = 0; b < 2 * blades; b++) {
            boolean splitter = b % 2 == 1;
            double phi0 = 2 * Math.PI * b / (2 * blades);
            List<Vec3d> root = new ArrayList<>(), edge = new ArrayList<>();
            for (int k = splitter ? n / 2 : 0; k <= n; k++) {
                double t = (double) k / n, a = Math.PI / 2 * t, x = meridian[k][0], rr = meridian[k][1];
                double height = (eye - hubEye) * (1 - t) + 0.12 * tip * t, sweep = phi0 - 0.45 * t * t + (1 - t) * (1 - t) * 0.5;
                Vec3d radial = new Vec3d(0, Math.cos(sweep), Math.sin(sweep));
                root.add(new Vec3d(x, 0, 0).add(radial.scale(rr)));
                edge.add(new Vec3d(x + Math.sin(a) * height, 0, 0).add(radial.scale(rr + Math.cos(a) * height)));
            }
            rotor.sheet(root, edge, blade, true);
        }
    }

    /** The vaned diffuser round an impeller's tip at x and the housing from the eye round it. */
    private void diffuserAndHousing(String page, int metal, double x0, double length, double eye, double tip) {
        int vane = c(page, Shapes.darker(metal, 0.12));
        double d0 = tip * 1.04, d1 = tip * 1.35, xd = x0 - 1.13 * length;
        fixed.lathe(new Vec3d(xd + 0.004 * tip, 0, 0), new Vec3d(xd - 0.004 * tip, 0, 0), new double[]{0, 0, 1, 1}, new double[]{d0 * 0.95, d1, d1, d0 * 0.95}, null, 40, vane, true);
        for (int v = 0; v < 21; v++) {
            Orientation turn = aboutX(2 * Math.PI * v / 21);
            fixed.box(new Vec3d(xd - 0.05 * tip, 0, 0).add(turn.rotate(new Vec3d(0, (d0 + d1) / 2, 0))), new Vec3d(0.03 * tip, (d1 - d0) / 2, 0.006 + 0.02 * tip), turn.multiply(aboutX(0.6)), vane);
        }
        lathe(casing(), List.of(new double[]{x0 + 0.002, eye * 1.05}, new double[]{x0 - 0.5 * length, tip * 0.95}, new double[]{x0 - 0.92 * length, tip * 1.38},
                new double[]{x0 - 1.66 * length, tip * 1.4}, new double[]{x0 - 1.66 * length, tip * 1.05}, new double[]{x0 + 0.002, eye * 1.05}), null, 48, c(page, metal), false);
    }

    /** A compressor from x0 over its length (a centrifugal stage takes 0.4 tip diameters): its rotor on the given spool. */
    private void compressor(PartMesh rotor, CompressorDesign cd, double x0, double length, boolean withCase) {
        int metal = Shapes.material(cd.material());
        double tip = positive(cd.tipDiameterM(), 0.2) / 2;
        int blade = c("compressor", Shapes.machined(metal)), drum = c("compressor", metal);
        switch (cd.kind()) {
            case AXIAL -> {
                double hub = Math.clamp(cd.hubTipRatio(), 0.05, 0.95) * tip;
                axialStages(rotor, "compressor", metal, x0, length, cd.stages(), tip, tip, hub, hubAfter(tip, hub, cd.pressureRatio()), false, withCase);
            }
            case CENTRIFUGAL -> {
                int stages = Math.clamp(cd.stages(), 1, 3);
                double stage = length / stages, eye = 0.6 * tip, hubEye = Math.max(Math.clamp(cd.hubTipRatio(), 0.05, 0.95) * tip, 0.3 * eye);
                for (int k = 0; k < stages; k++) {
                    inside(k >= 1 || deepCompressor);
                    impeller(rotor, x0 - k * stage, 0.6 * stage, hubEye, eye, tip, cd.blades(), blade, drum);
                    inside(false);
                    diffuserAndHousing("compressor", metal, x0 - k * stage, 0.6 * stage, eye, tip);
                }
            }
            case AXIAL_CENTRIFUGAL -> {
                double axialTip = positive(cd.axialTipRatio(), 0.6) * tip, hub = Math.clamp(cd.hubTipRatio(), 0.05, 0.95) * axialTip;
                double axialLength = Math.max(0.01, length - 0.4 * 2 * tip), hubEnd = hubAfter(axialTip, hub, Math.sqrt(cd.pressureRatio()));
                axialStages(rotor, "compressor", metal, x0, axialLength, cd.stages(), axialTip, axialTip, hub, hubEnd, false, withCase);
                double xi = x0 - axialLength, stage = length - axialLength;
                inside(true);
                impeller(rotor, xi, 0.6 * stage, hubEnd, axialTip, tip, cd.blades(), blade, drum);
                inside(false);
                diffuserAndHousing("compressor", metal, xi, 0.6 * stage, axialTip, tip);
            }
        }
    }

    /** The hub radius after a compression by a pressure ratio, the annulus shrinking with the density (as JetEngineModel's drum). */
    private static double hubAfter(double tip, double hub, double pressureRatio) {
        double area = (tip * tip - hub * hub) / Math.pow(Math.max(1.01, pressureRatio), 0.75);
        return Math.sqrt(Math.max(hub * hub, tip * tip - area));
    }

    /**
     * A straight-through annular combustor from x0 to x1 inside a case of a radius: liner walls with dilution holes
     * (in a cutaway), the dome, fuel nozzles fed from a manifold ring and two igniters.
     */
    private void combustor(CombustorDesign cd, double x0, double x1, double casingRadius, double inner, double before, double after) {
        int metal = Shapes.material(cd.material()), liner = c("combustor", Shapes.mix(metal, 0xff7a5a8a, 0.35));
        double length = x0 - x1;
        lathe(casing(), List.of(new double[]{x0, before}, new double[]{x0 - 0.12 * length, casingRadius}, new double[]{x1 + 0.1 * length, casingRadius},
                new double[]{x1, after * 1.02}, new double[]{x1, after}, new double[]{x0, before * 0.98}, new double[]{x0, before}), null, 48, c("combustor", metal), false);
        flange(casing(), x0, before, c("combustor", FLANGE));
        double outerLiner = 0.82 * casingRadius, innerLiner = Math.max(0.42 * casingRadius, inner * 1.05), a0 = x0 - 0.12 * length, a1 = x1 + 0.06 * length;
        if (cutaway) {
            lathe(casing(), List.of(new double[]{a1, outerLiner * 0.95}, new double[]{a0, outerLiner}, new double[]{a0 + 0.05 * length, 0.5 * (outerLiner + innerLiner)},
                    new double[]{a0, innerLiner}, new double[]{a1, innerLiner * 1.03}, new double[]{a1, outerLiner * 0.95}), null, 40, liner, false);
            for (int row = 0; row < 2; row++) {
                double x = a0 - (0.3 + 0.3 * row) * length;
                for (int k = 0; k < 18; k++) {
                    double a = 2 * Math.PI * (k + 0.5 * row) / 18;
                    Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a)), at = new Vec3d(x, 0, 0).add(dir.scale(outerLiner * 0.99));
                    casing().cylinder(at, at.add(dir.scale(0.01 * casingRadius)), 0.035 * casingRadius, 0.035 * casingRadius, 8, c("combustor", 0xff2b2526));
                }
            }
        }
        inside(cowled);
        fuelRing(a0 + 0.1 * length, casingRadius, a0, 0.5 * (outerLiner + innerLiner));
        // 0.11: two igniter plugs from the kit, their exciter under the case, braided leads
        Vec3d exciter = new Vec3d(a0 - 0.5 * length, -casingRadius * 1.25, 0.25 * casingRadius);
        fixed.roundedBox(exciter.subtract(new Vec3d(0.18 * casingRadius, 0.08 * casingRadius, 0.12 * casingRadius)),
                exciter.add(new Vec3d(0.18 * casingRadius, 0.08 * casingRadius, 0.12 * casingRadius)), 0.02 * casingRadius, 2, c("control", 0xff3a4552));
        for (int k = 0; k < 2; k++) {
            double a = Math.PI / 2 + Math.PI * k + 0.3;
            Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a)), base = new Vec3d(a0 - 0.3 * length, 0, 0).add(dir.scale(casingRadius));
            Details.place(casing(), DetailKit.Part.IGNITER, base, dir, Vec3d.X, 0.09 * casingRadius, Details.SMALL);
            Vec3d plugEnd = base.add(dir.scale(0.09 * casingRadius / 0.022 * 0.085)), out = exciter.add(new Vec3d(0, 0.08 * casingRadius, (k == 0 ? 0.06 : -0.06) * casingRadius));
            // round the case, not through it, to the exciter below
            List<Vec3d> lead = new ArrayList<>(List.of(plugEnd));
            lead.addAll(Details.aroundAxis(plugEnd.add(dir.scale(0.25 * casingRadius)), out, 1.3 * casingRadius, 16));
            Details.braidedLine(fixed, lead, 0.018 * casingRadius);
        }
        inside(false);
    }

    /** A fuel manifold ring round a case at x with twelve nozzles through it, reaching (in a cutaway) to a radius at the dome's x. */
    private void fuelRing(double ringX, double caseRadius, double domeX, double domeRadius) {
        double ring = caseRadius * 1.1;
        casing().tube(PartMesh.circle(new Vec3d(ringX, 0, 0), Vec3d.X, ring, 48), 0.022 * caseRadius, null, c("control", PIPE), null, 6, true);
        for (int k = 0; k < 12; k++) {
            double a = 2 * Math.PI * k / 12;
            Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a)), out = new Vec3d(ringX, 0, 0).add(dir.scale(ring));
            casing().cylinder(out, out.add(dir.scale(-(ring - caseRadius) * 1.2)), 0.028 * caseRadius, 0.028 * caseRadius, 8, c("control", Shapes.machined(PIPE)));
            Details.place(casing(), DetailKit.Part.AN_FITTING, new Vec3d(ringX, 0, 0).add(dir.scale(caseRadius)), dir, Vec3d.X, 0.04 * caseRadius, Details.SMALL);
            if (cutaway) {
                Vec3d inside = new Vec3d(domeX, 0, 0).add(dir.scale(domeRadius));
                casing().tube(PartMesh.bezier(out.add(dir.scale(-(ring - caseRadius))), new Vec3d(ringX, 0, 0).add(dir.scale(domeRadius * 1.3)),
                        inside.add(new Vec3d(0.3 * caseRadius, 0, 0)), inside, 6), 0.016 * caseRadius, 6, c("control", Shapes.machined(PIPE)));
            }
        }
    }

    /** Turbine stages from x0 aft over a length, tips from tip0 to tip1, discs to the axis: guide vanes fixed, rotors on the spool. */
    private void turbine(PartMesh rotor, String page, int metal, double x0, double length, int stages, double tip0, double tip1, boolean withCase) {
        turbine(rotor, page, metal, x0, length, stages, tip0, tip1, withCase, false);
    }

    /** A turbine; 0.11: its last stage shows from outside (marked deep otherwise, as are its others). */
    private void turbine(PartMesh rotor, String page, int metal, double x0, double length, int stages, double tip0, double tip1, boolean withCase, boolean lastShows) {
        stages = Math.clamp(stages, 1, MAX_STAGES);
        double stage = length / stages;
        int vane = c(page, Shapes.mix(metal, 0xff4f4566, 0.25)), blade = c(page, Shapes.machined(metal)), disc = c(page, metal);
        for (int k = 0; k < stages; k++) {
            inside(!(lastShows && k == stages - 1));
            double xs = x0 - k * stage, tip = lerp(tip0, tip1, (k + 0.5) / stages), discHub = 0.58 * tip;
            double vaneChord = 0.36 * stage / Math.cos(rad(58)), bladeChord = 0.3 * stage / Math.cos(rad(46));
            Shapes.bladeRow(fixed, xs - 0.22 * stage, discHub, tip, count(discHub, tip, vaneChord, 1.2), vaneChord, 0.9 * vaneChord, rad(-62), rad(-55), 0.18, vane, 3, false);
            Shapes.bladeRow(rotor, xs - 0.68 * stage, discHub * 1.02, tip * 0.985, count(discHub, tip, bladeChord, 1.35), bladeChord, 0.8 * bladeChord, rad(35), rad(58), 0.22, blade, 3, false);
            double xd = xs - 0.68 * stage, bore = 0.08 * tip;
            lathe(rotor, List.of(new double[]{xd + 0.18 * stage, bore}, new double[]{xd + 0.06 * stage, discHub * 0.5}, new double[]{xd + 0.16 * stage, discHub * 0.98},
                    new double[]{xd - 0.16 * stage, discHub * 0.98}, new double[]{xd - 0.06 * stage, discHub * 0.5}, new double[]{xd - 0.18 * stage, bore},
                    new double[]{xd + 0.18 * stage, bore}), null, 32, disc, true);
        }
        inside(false);
        if (withCase) {
            lathe(casing(), List.of(new double[]{x0, tip0 * 1.08}, new double[]{x0 - length, tip1 * 1.08}, new double[]{x0 - length, tip1 * 1.05},
                    new double[]{x0, tip0 * 1.05}, new double[]{x0, tip0 * 1.08}), null, 48, c(page, metal), true);
            flange(casing(), x0, tip0 * 1.05, c(page, FLANGE));
            flange(casing(), x0 - length, tip1 * 1.05, c(page, FLANGE));
        }
    }

    /** A shaft along the axis from x0 to x1. */
    private static void shaft(PartMesh out, double x0, double x1, double radius, int colour) {
        out.cylinder(new Vec3d(x0, 0, 0), new Vec3d(x1, 0, 0), radius, radius, 14, colour);
    }

    /** An accessory gearbox below a case at a radius, from x0 to x1, with a starter, a fuel pump and the drive tower. */
    private void accessories(double x0, double x1, double radius, double size) {
        double y = -(radius + size * 1.3), length = Math.max(x0 - x1, 0.05);
        Vec3d centre = new Vec3d((x0 + x1) / 2, y, 0);
        fixed.box(centre, new Vec3d(length / 2, size, 2.5 * size), Orientation.IDENTITY, c("control", GEARBOX));
        fixed.cylinder(new Vec3d(centre.x(), y + size, 0), new Vec3d(centre.x(), -radius * 0.98, 0), 0.4 * size, 0.4 * size, 12, c("control", GEARBOX));
        // the starter and the pump on the forward face (0.11: both were drawn inside the gearbox; aft of it the pump ran
        // into the combustor's case)
        Vec3d starter = new Vec3d(x0, y, 1.3 * size), starterEnd = starter.add(new Vec3d(Math.min(0.8 * length, 6 * size), 0, 0));
        fixed.lathe(starter, starterEnd, new double[]{0, 0, 0.1, 0.9, 1, 1}, new double[]{0, 0.9 * size, size, size, 0.7 * size, 0}, null, 18,
                c("control", BLACK), false);
        Vec3d pump = new Vec3d(x0, y, -1.4 * size), pumpEnd = pump.add(new Vec3d(Math.min(0.5 * length, 3.5 * size), 0, 0));
        fixed.lathe(pump, pumpEnd, new double[]{0, 0, 0.2, 0.8, 1, 1}, new double[]{0, 0.7 * size, 0.8 * size, 0.8 * size, 0.6 * size, 0}, null, 16,
                c("control", Shapes.machined(GEARBOX)), false);
        // 0.11: the pump's fitting, the starter's connector, a data plate on the gearbox
        Details.place(fixed, DetailKit.Part.AN_FITTING, pumpEnd, Vec3d.X, Vec3d.Y, 0.4 * size, Details.SMALL);
        Details.place(fixed, DetailKit.Part.CONNECTOR, starterEnd, Vec3d.X, Vec3d.Y, 0.35 * size, Details.SMALL);
        Details.place(fixed, DetailKit.Part.DATA_PLATE, centre.add(new Vec3d(0, 0, 2.5 * size + 0.0005)), Vec3d.Z, Vec3d.X, 0.6 * size, Details.TINY);
    }

    // ================================================================ turbofan

    public static EngineModel build(TurbofanDesign e, String highlight, boolean cutaway) {
        GasTurbineModel m = new GasTurbineModel(highlight, cutaway);
        m.turbofan(e);
        return m.finish(false, 2, 4, 0);
    }

    private void turbofan(TurbofanDesign e) {
        FanDesign fd = e.fan();
        double r = positive(fd.tipDiameterM(), 1) / 2, hub = Math.clamp(fd.hubTipRatio(), 0.1, 0.8) * r;
        double bypass = Math.max(0, fd.bypassRatio());
        double boosterTip = fd.boosterStages() > 0 ? fd.boosterTipDiameter() / 2 : 0;
        double split = Math.max(Math.sqrt(hub * hub + (r * r - hub * hub) / (1 + bypass)), boosterTip * 1.04);
        CompressorDesign cd = e.compressor();
        double hpcTip = positive(cd.tipDiameterM(), 0.6 * r) / 2;
        double hpcFace = cd.kind() == CompressorDesign.Kind.AXIAL_CENTRIFUGAL ? positive(cd.axialTipRatio(), 0.6) * hpcTip : cd.kind() == CompressorDesign.Kind.CENTRIFUGAL ? 0.6 * hpcTip : hpcTip;
        double casingR = positive(e.combustor().casingDiameterM(), 2 * hpcTip) / 2;
        double hptTip = positive(e.hpTurbine().tipDiameterM(), 2 * hpcTip) / 2, lptTip = positive(e.lpTurbine().tipDiameterM(), 2 * hptTip) / 2;
        // stations along x (the inlet lip forward)
        double inletLength = positive(e.inlet().lengthM(), 0.3 * r), fanLength = 0.25 * 2 * r, boosterLength = 0.06 * 2 * boosterTip * fd.boosterStages();
        double compressorLength = positive(cd.lengthM(), 0.5 * r), combustorLength = positive(e.combustor().addedLengthM(), 0.3 * r);
        double hptLength = 0.1 * 2 * hptTip * Math.max(1, e.hpTurbine().stages()), lptLength = 0.1 * 2 * lptTip * Math.max(1, e.lpTurbine().stages());
        double mixerLength = e.mixer().addedLengthM(), tailpipe = positive(e.nozzle().tailpipeLengthM(), 0.2 * r);
        double total = inletLength + fanLength + boosterLength + compressorLength + combustorLength + hptLength + lptLength + mixerLength + tailpipe;
        double lip = total / 2, face = lip - inletLength, fanEnd = face - fanLength, boosterEnd = fanEnd - boosterLength, hpcEnd = boosterEnd - compressorLength;
        double combEnd = hpcEnd - combustorLength, hptEnd = combEnd - hptLength, lptEnd = hptEnd - lptLength, mixEnd = lptEnd - mixerLength, end = mixEnd - tailpipe;
        boolean mixed = e.mixer().mixes();
        double a8, aBypass;
        try { var s = e.sizing(); a8 = s.nozzleThroatM2(); aBypass = s.bypassThroatM2(); }
        catch (RuntimeException ex) { a8 = 0.3 * Math.PI * lptTip * lptTip; aBypass = 0.5 * Math.PI * (r * r - split * split); }
        int fanMetal = Shapes.material(fd.material()), nacelle = c("inlet", NACELLE);

        // the fan: spinner, wide-chord blades on their disc, outlet guide vanes and frame struts behind it
        spinner(low, face, hub, 1.6 * hub, c("fan", SPINNER));
        lathe(low, List.of(new double[]{face, 0}, new double[]{face, hub}, new double[]{fanEnd + 0.3 * fanLength, hub * 1.02}, new double[]{fanEnd + 0.3 * fanLength, 0}), null, 32, c("fan", fanMetal), true);
        Shapes.bladeRow(low, face - 0.35 * fanLength, hub, r * 0.99, Math.clamp(fd.blades(), 8, 60), 0.16 * r, 0.27 * r, rad(22), rad(63), 0.1, c("fan", Shapes.machined(fanMetal)), 6, true);
        double ogvX = fanEnd + 0.15 * fanLength;
        Shapes.bladeRow(fixed, ogvX, split * 1.06, r, Math.min(80, Shapes.blades(split, r, 0.12 * r, 1.3)), 0.12 * r, 0.14 * r, rad(-12), rad(-18), 0.12, c("fan", Shapes.darker(fanMetal, 0.15)), 3, false);
        for (int k = 0; k < 6; k++) {
            Orientation turn = aboutX(2 * Math.PI * k / 6);
            fixed.box(new Vec3d(ogvX - 0.25 * fanLength, 0, 0).add(turn.rotate(new Vec3d(0, (split + r) / 2, 0))), new Vec3d(0.12 * fanLength, (r - split) / 2, 0.012 * r), turn, c("fan", Shapes.darker(fanMetal, 0.25)));
        }
        // the booster behind the fan's root, inside the splitter, then the duct down to the core compressor
        if (fd.boosterStages() > 0) {
            double bHub = 0.72 * boosterTip;
            axialStages(low, "fan", fanMetal, fanEnd, boosterLength, fd.boosterStages(), boosterTip * 0.98, boosterTip * 0.95, bHub, hubAfter(boosterTip, bHub, fd.boosterRatio()), false, false);
        }
        // the core compressor and combustor on the core spool's side
        deepCompressor = true;
        compressor(high, cd, boosterEnd, compressorLength, false);
        deepCompressor = false;
        cowled = true;
        combustor(e.combustor(), hpcEnd, combEnd, casingR, hubAfter(hpcFace, Math.clamp(cd.hubTipRatio(), 0.05, 0.95) * hpcFace, cd.pressureRatio()), hpcTip * 1.05, hptTip * 1.05);
        cowled = false;
        // turbines and shafts
        turbine(high, "hpTurbine", Shapes.material(e.hpTurbine().material()), combEnd, hptLength, e.hpTurbine().stages(), hptTip, hptTip, false);
        turbine(low, "lpTurbine", Shapes.material(e.lpTurbine().material()), hptEnd, lptLength, e.lpTurbine().stages(), Math.max(hptTip * 1.05, 0.85 * lptTip), lptTip, false, true);
        shaft(high, boosterEnd, hptEnd + 0.2 * hptLength, Math.max(0.1 * hpcFace, 0.004), c("hpTurbine", Shapes.machined(0xff858b93)));
        shaft(low, fanEnd + 0.3 * fanLength, lptEnd, Math.max(0.05 * hpcFace, 0.003), c("lpTurbine", Shapes.machined(0xff9ea2a7)));
        // the core cowl: splitter lip, over the booster and the core, down to the core nozzle (or the mixer)
        double coreOuter = Math.max(Math.max(casingR, hpcTip * 1.4), lptTip * 1.1) * 1.08, coreEndR;
        double coreEnd = mixed ? lptEnd : lptEnd - 0.4 * tailpipe;
        double plug = 0.55 * lptTip, coreExit = Math.sqrt(a8 / Math.PI + plug * plug);
        coreEndR = mixed ? lptTip * 1.08 : coreExit * 1.03;
        int cowl = c("nozzle", Shapes.mix(NACELLE, 0xff8f949a, 0.45));
        lathe(casing(), List.of(new double[]{fanEnd + 0.05 * fanLength, split}, new double[]{fanEnd - 0.3 * boosterLength, split * 1.06}, new double[]{boosterEnd, Math.max(split * 1.05, coreOuter * 0.85)},
                new double[]{hpcEnd, coreOuter}, new double[]{combEnd, coreOuter}, new double[]{coreEnd, coreEndR}, new double[]{coreEnd, coreEndR * 0.97},
                new double[]{lptEnd, lptTip * 1.04}, new double[]{hptEnd, hptTip * 1.06}, new double[]{combEnd, casingR}, new double[]{hpcEnd, hpcTip * 1.05},
                new double[]{boosterEnd, hpcFace * 1.04}, new double[]{fanEnd, boosterTip > 0 ? boosterTip * 1.0 : split * 0.97}, new double[]{fanEnd + 0.05 * fanLength, split}),
                null, 48, cowl, false);
        if (mixed) { mixer(e, lptEnd, mixEnd, coreEndR, lptTip); }
        else {
            // the core nozzle's plug
            int hot = c("nozzle", Shapes.mix(Shapes.material(e.nozzle().material()), Shapes.heat(0.3), 0.45));
            fixed.hot(true);   // 0.11: glows with the core's gas (PartRenderer.glow)
            lathe(fixed, List.of(new double[]{lptEnd, 0}, new double[]{lptEnd, plug}, new double[]{coreEnd, plug * 0.95}, new double[]{coreEnd - 0.6 * tailpipe, 0.05 * plug},
                    new double[]{coreEnd - 0.62 * tailpipe, 0}), null, 32, hot, false);
            fixed.hot(false);
        }
        // the nacelle: the lip, the fan case, the outer cowl to the fan nozzle (or the common nozzle)
        double outer = r * 1.12, lipWall = 0.05 * r;
        double nacelleEnd, exitInner;
        if (mixed) {
            nacelleEnd = end;
            exitInner = Math.sqrt(a8 / Math.PI);
        } else {
            nacelleEnd = Math.min(hpcEnd + 0.2 * compressorLength, boosterEnd);
            double coreAt = Math.max(split * 1.05, coreOuter * 0.85) + (coreOuter - Math.max(split * 1.05, coreOuter * 0.85)) * Math.clamp((boosterEnd - nacelleEnd) / Math.max(compressorLength, 1e-6), 0, 1);
            exitInner = Math.sqrt(aBypass / Math.PI + coreAt * coreAt);
        }
        List<double[]> n = new ArrayList<>();
        n.add(new double[]{nacelleEnd, exitInner * 1.02});
        n.add(new double[]{fanEnd - 0.4 * fanLength, outer});
        n.add(new double[]{face + 0.4 * inletLength, outer});
        for (int k = 0; k <= 8; k++) { double a = Math.PI / 2 - Math.PI * k / 8; n.add(new double[]{lip - lipWall / 2 + lipWall / 2 * Math.cos(a), r * 1.02 + lipWall / 2 + lipWall / 2 * Math.sin(a)}); }
        n.add(new double[]{face, r * 1.01});
        n.add(new double[]{fanEnd, r * 1.01});
        n.add(new double[]{ogvX - 0.5 * fanLength, r * 1.0});
        n.add(new double[]{nacelleEnd, exitInner});
        n.add(new double[]{nacelleEnd, exitInner * 1.02});
        lathe(casing(), n, null, 56, nacelle, false);
        casing().tube(PartMesh.circle(new Vec3d(lip - lipWall / 2, 0, 0), Vec3d.X, r * 1.02 + lipWall / 2, 56), lipWall / 2 * 1.05, null, c("inlet", Shapes.machined(0xffc9ccd0)), null, 8, true);
        // the accessory gearbox under the fan case
        double box = 0.06 * r;
        double ax0 = face - 0.2 * fanLength, ax1 = fanEnd - 0.6 * fanLength;
        Vec3d gb = new Vec3d((ax0 + ax1) / 2, -(outer - 0.02 * r) + 0.0, 0);
        fixed.box(gb.add(new Vec3d(0, -box, 0)), new Vec3d((ax0 - ax1) / 2, box, 3 * box), Orientation.IDENTITY, c("control", GEARBOX));
        // the starter on the gearbox's forward face, under the nacelle (0.11: it lay inside the box and stood out of its bottom)
        double starterR = 0.7 * box;
        Vec3d starter = new Vec3d(ax0, -(outer - 0.02 * r) - box, 1.5 * box), starterEnd = starter.add(new Vec3d(0.35 * (ax0 - ax1), 0, 0));
        fixed.lathe(starter, starterEnd, new double[]{0, 0, 0.1, 0.9, 1, 1}, new double[]{0, 0.9 * starterR, starterR, starterR, 0.7 * starterR, 0}, null, 18, c("control", BLACK), false);
        // the fuel line from the gearbox down the bottom strut of the fan frame and on under the core cowl to the manifold
        // (0.11: it crossed the bypass duct by itself, through the nacelle's wall and the cowl)
        double strutX = ogvX - 0.25 * fanLength, line = Math.min(0.02 * casingR, 0.8 * 0.012 * r), ringX = hpcEnd - 0.02 * combustorLength;
        List<Vec3d> fuelLine = new ArrayList<>(List.of(new Vec3d(strutX, -(outer - 0.02 * r), 0), new Vec3d(strutX, -split * 0.97, 0)));
        fuelLine.addAll(PartMesh.bezier(new Vec3d(strutX, -split * 0.97, 0), new Vec3d(strutX - 0.3 * (strutX - ringX), -0.5 * (split + coreOuter), 0),
                new Vec3d(ringX + 0.1 * (strutX - ringX), -casingR * 1.3, -0.1 * casingR), new Vec3d(ringX, -casingR * 1.1 * Math.cos(0.15), -casingR * 1.1 * Math.sin(0.15)), 12).subList(1, 13));
        inside(true);   // under the cowl and inside the strut
        Details.braidedLine(fixed, fuelLine, line);
        inside(false);
        Details.place(fixed, DetailKit.Part.DATA_PLATE, gb.add(new Vec3d(0, -box, 3 * box + 0.0005)), Vec3d.Z, Vec3d.X, 0.8 * box, Details.TINY);
        Details.place(fixed, DetailKit.Part.CONNECTOR, starterEnd, Vec3d.X, Vec3d.Y, 0.25 * box, Details.SMALL);
    }

    /** A lobed mixer from x0 to x1: the core cowl's end folded into lobes, alternately out into the bypass air and in to the core stream. */
    private void mixer(TurbofanDesign e, double x0, double x1, double outerR, double coreR) {
        int lobes = Math.clamp(e.mixer().lobes(), 4, 40), around = lobes * 8, rings = 8;
        int colour = c("mixer", Shapes.mix(Shapes.material(e.mixer().material()), Shapes.heat(0.4), 0.35));
        double mean = 0.5 * (outerR + coreR), depth = 0.5 * (outerR - coreR) + 0.08 * mean;
        List<List<Vec3d>> rows = new ArrayList<>();
        for (int i = 0; i <= rings; i++) {
            double t = (double) i / rings, x = lerp(x0, x1, t), amplitude = depth * t * t * (3 - 2 * t);
            List<Vec3d> row = new ArrayList<>();
            for (int k = 0; k <= around; k++) {
                double phi = 2 * Math.PI * k / around, rr = mean + amplitude * Math.cos(lobes * phi);
                row.add(new Vec3d(x, rr * Math.cos(phi), rr * Math.sin(phi)));
            }
            rows.add(row);
        }
        for (int i = 0; i < rings; i++) { fixed.sheet(rows.get(i), rows.get(i + 1), colour, true); }
        // the centre body behind the turbine
        lathe(fixed, List.of(new double[]{x0, 0}, new double[]{x0, 0.5 * coreR}, new double[]{x1, 0.3 * coreR}, new double[]{x1 - 0.3 * (x0 - x1), 0}), null, 28, colour, false);
    }

    // ================================================================ turboprop and turboshaft

    public static EngineModel build(ShaftTurbineDesign e, String highlight, boolean cutaway) {
        GasTurbineModel m = new GasTurbineModel(highlight, cutaway);
        boolean reverse = e.combustor().flow() == CombustorDesign.Flow.REVERSE;
        double ratio = Math.max(1, e.gearbox().ratio());
        if (reverse) { m.reverseFlow(e); } else { m.straightThrough(e); }
        return m.finish(reverse, 2.5, 4, 2.5 / Math.min(ratio, 6), ratio);
    }

    /** Lengths and radii of a shaft engine's stations. */
    private record Shaft(double inlet, double compressor, double combustor, double turbine, double powerTurbine, double gearbox, double exhaust,
                         double compressorTip, double casingR, double turbineTip, double ptTip, double gearboxR) {
        static Shaft of(ShaftTurbineDesign e) {
            double compressorTip = positive(e.compressor().tipDiameterM(), 0.2) / 2, turbineTip = positive(e.turbine().tipDiameterM(), 2 * compressorTip) / 2;
            double ptTip = positive(e.powerTurbine().tipDiameterM(), 2 * turbineTip) / 2;
            return new Shaft(positive(e.inlet().lengthM(), 0.5 * compressorTip), positive(e.compressor().lengthM(), compressorTip),
                    positive(e.combustor().addedLengthM(), 0.3 * compressorTip), 0.1 * 2 * turbineTip * Math.max(1, e.turbine().stages()),
                    0.1 * 2 * ptTip * Math.max(1, e.powerTurbine().stages()), e.gearbox().direct() ? 0 : 0.3 * e.gearboxDiameter() * Math.min(e.gearbox().meshes(), 2),
                    positive(e.exhaust().tailpipeLengthM(), 0.5 * ptTip), compressorTip, positive(e.combustor().casingDiameterM(), 2.2 * compressorTip) / 2,
                    turbineTip, ptTip, e.gearbox().direct() ? 0 : Math.max(e.gearboxDiameter() / 2, 0.5 * compressorTip));
        }
    }

    /**
     * A reverse-flow engine, drawn with the gas flowing aft (-x) and turned end for end afterwards: the inlet screen and
     * accessory gearbox at +x, the compressor, the folded combustor round the turbines, the exhaust case with its stacks,
     * the reduction gearbox and the propeller flange at -x.
     */
    private void reverseFlow(ShaftTurbineDesign e) {
        Shaft s = Shaft.of(e);
        CompressorDesign cd = e.compressor();
        double axialTip = cd.kind() == CompressorDesign.Kind.CENTRIFUGAL ? 0.6 * s.compressorTip() : cd.kind() == CompressorDesign.Kind.AXIAL ? s.compressorTip()
                : positive(cd.axialTipRatio(), 0.6) * s.compressorTip();
        double impellerOuter = cd.kind() == CompressorDesign.Kind.AXIAL ? s.compressorTip() * 1.06 : s.compressorTip() * 1.4;
        double accessories = 0.6 * s.compressorTip();
        double total = accessories + s.inlet() + s.compressor() + s.combustor() + s.turbine() + s.powerTurbine() + s.exhaust() + s.gearbox() + 0.05 * s.compressorTip();
        double back = total / 2, face = back - accessories - s.inlet(), compEnd = face - s.compressor(), turbStart = compEnd - s.combustor();
        double ctEnd = turbStart - s.turbine(), ptEnd = ctEnd - s.powerTurbine(), exEnd = ptEnd - s.exhaust(), gbEnd = exEnd - s.gearbox();
        int inletMetal = Shapes.material(e.inlet().material());
        // the accessory gearbox closing the rear, with its pads; the inlet plenum's screen round the air's way in
        double plenum = Math.max(1.35 * axialTip, 0.75 * impellerOuter);
        lathe(fixed, List.of(new double[]{back, 0}, new double[]{back, plenum * 0.9}, new double[]{back - accessories, plenum}, new double[]{back - accessories, 0}), null, 40,
                c("control", GEARBOX), true);
        for (int k = 0; k < 3; k++) {
            Vec3d pad = new Vec3d(back, 0, 0).add(aboutX(2 * Math.PI * k / 3 + 0.4).rotate(new Vec3d(0, 0.5 * plenum, 0)));
            fixed.lathe(pad, pad.add(new Vec3d(0.9 * accessories, 0, 0)), new double[]{0, 0, 0.1, 0.9, 1, 1}, new double[]{0, 0.22 * plenum, 0.25 * plenum, 0.25 * plenum, 0.18 * plenum, 0},
                    null, 16, c("control", k == 0 ? BLACK : Shapes.machined(GEARBOX)), false);
        }
        int screen = c("inlet", SCREEN);
        double sx0 = back - accessories, sx1 = face;
        for (int k = 0; k <= 6; k++) { casing().tube(PartMesh.circle(new Vec3d(lerp(sx0, sx1, k / 6.0), 0, 0), Vec3d.X, plenum, 48), 0.006 * plenum + 0.0008, null, screen, null, 4, true); }
        for (int k = 0; k < 36; k++) {
            Vec3d dir = aboutX(2 * Math.PI * k / 36).rotate(Vec3d.Y);
            casing().cylinder(new Vec3d(sx0, 0, 0).add(dir.scale(plenum)), new Vec3d(sx1, 0, 0).add(dir.scale(plenum)), 0.005 * plenum + 0.0006, 0.005 * plenum + 0.0006, 4, screen);
        }
        lathe(casing(), List.of(new double[]{sx1, plenum * 1.02}, new double[]{sx1, axialTip * 1.06}, new double[]{sx1 - 0.04 * plenum, axialTip * 1.06}, new double[]{sx1 - 0.04 * plenum, plenum * 1.02},
                new double[]{sx1, plenum * 1.02}), null, 40, c("inlet", inletMetal), true);
        // the compressor and the case round it
        compressor(high, cd, face, s.compressor(), true);
        // the combustor folded back round the turbines: outer case, liner, the exit duct turning the gas into the first vanes
        int metal = Shapes.material(e.combustor().material()), liner = c("combustor", Shapes.mix(metal, 0xff7a5a8a, 0.35));
        double caseR = Math.max(s.casingR(), Math.max(impellerOuter, s.ptTip() * 1.3)), linerOuter = 0.9 * caseR, linerInner = Math.max(s.turbineTip(), s.ptTip()) * 1.15;
        double l0 = compEnd + 0.02 * s.compressor(), l1 = ptEnd + 0.2 * s.powerTurbine();
        lathe(casing(), List.of(new double[]{compEnd, impellerOuter}, new double[]{l0 - 0.1 * (l0 - l1), caseR}, new double[]{l1, caseR}, new double[]{l1 - 0.05 * (l0 - l1), caseR * 0.9},
                new double[]{l1 - 0.05 * (l0 - l1), caseR * 0.86}, new double[]{l1, caseR * 0.97}, new double[]{l0 - 0.1 * (l0 - l1), caseR * 0.97}, new double[]{compEnd, impellerOuter * 0.97},
                new double[]{compEnd, impellerOuter}), null, 48, c("combustor", metal), false);
        if (cutaway) {
            lathe(casing(), List.of(new double[]{l0, linerOuter * 0.95}, new double[]{l1 + 0.05 * (l0 - l1), linerOuter}, new double[]{l1 + 0.02 * (l0 - l1), 0.5 * (linerOuter + linerInner)},
                    new double[]{l1 + 0.05 * (l0 - l1), linerInner * 1.08}, new double[]{l0 - 0.02 * (l0 - l1), linerInner * 1.04}, new double[]{l0 + 0.02 * (l0 - l1), s.turbineTip() * 1.2},
                    new double[]{turbStart, s.turbineTip() * 1.05}, new double[]{turbStart, s.turbineTip() * 0.9}, new double[]{l0, linerInner * 0.95}, new double[]{l0, linerOuter * 0.95}),
                    null, 40, liner, false);
        }
        fuelRing(l1 + 0.25 * (l0 - l1), caseR, l1 + 0.12 * (l0 - l1), 0.5 * (linerOuter + linerInner));
        // the turbines: the gas generator's on its spool, the power turbine's on the output's
        turbine(high, "turbine", Shapes.material(e.turbine().material()), turbStart, s.turbine(), e.turbine().stages(), s.turbineTip(), s.turbineTip(), false);
        turbine(low, "powerTurbine", Shapes.material(e.powerTurbine().material()), ctEnd, s.powerTurbine(), e.powerTurbine().stages(), Math.max(s.turbineTip(), 0.9 * s.ptTip()), s.ptTip(), false);
        shaft(high, face, ctEnd + 0.2 * s.turbine(), Math.max(0.12 * axialTip, 0.004), c("turbine", Shapes.machined(0xff858b93)));
        shaft(low, ctEnd, gbEnd + 0.3 * s.gearbox(), Math.max(0.08 * axialTip, 0.003), c("powerTurbine", Shapes.machined(0xff9ea2a7)));
        // the exhaust case round the power turbine's exit and its two stacks, out to the sides and back
        int exhaust = c("exhaust", Shapes.mix(Shapes.material(e.exhaust().material()), Shapes.heat(0.35), 0.5));
        double exR = caseR * 0.95;
        casing().hot(true);   // 0.11: the exhaust case and stacks (PartRenderer.glow)
        fixed.hot(true);
        lathe(casing(), List.of(new double[]{ptEnd, s.ptTip() * 1.08}, new double[]{ptEnd - 0.2 * s.exhaust(), exR}, new double[]{exEnd + 0.1 * s.exhaust(), exR},
                new double[]{exEnd, 0.6 * exR}, new double[]{exEnd, 0.55 * exR}, new double[]{ptEnd, s.ptTip() * 1.04}, new double[]{ptEnd, s.ptTip() * 1.08}), null, 48, exhaust, false);
        for (int side = -1; side <= 1; side += 2) {
            Vec3d from = new Vec3d(0.5 * (ptEnd + exEnd), 0, side * exR * 0.8), to = from.add(new Vec3d(0.9 * s.exhaust() + 0.5 * exR, -0.1 * exR, side * 0.9 * exR));
            fixed.tube(PartMesh.bezier(from, from.add(new Vec3d(0, 0, side * 0.5 * exR)), to.add(new Vec3d(-0.4 * exR, 0, 0)), to, 10), 0.32 * exR, null, exhaust, null, 16, false);
            fixed.tube(PartMesh.circle(to, new Vec3d(1, 0, side * 0.2).normalized(), 0.32 * exR, 24), 0.02 * exR, null, Shapes.darker(exhaust, 0.2), null, 6, true);
        }
        casing().hot(false);
        fixed.hot(false);
        // the reduction gearbox and the propeller flange
        reductionGearbox(e, exEnd, gbEnd, s.gearboxR(), 0.55 * exR, false);
    }

    /**
     * A shaft engine with the gas going straight through, aft (-x): the gearbox and output flange (or the bare output
     * shaft) in front, the annular inlet with swirl vanes round the shaft, compressor, combustor, turbines, the exhaust
     * diffuser with its centre body.
     */
    private void straightThrough(ShaftTurbineDesign e) {
        Shaft s = Shaft.of(e);
        CompressorDesign cd = e.compressor();
        double axialTip = cd.kind() == CompressorDesign.Kind.CENTRIFUGAL ? 0.6 * s.compressorTip() : cd.kind() == CompressorDesign.Kind.AXIAL ? s.compressorTip()
                : positive(cd.axialTipRatio(), 0.6) * s.compressorTip();
        double nose = e.gearbox().direct() ? 0.6 * axialTip : 0;
        double total = nose + s.gearbox() + s.inlet() + s.compressor() + s.combustor() + s.turbine() + s.powerTurbine() + s.exhaust();
        double front = total / 2, gbStart = front - nose, face = gbStart - s.gearbox() - s.inlet(), compEnd = face - s.compressor(), combEnd = compEnd - s.combustor();
        double ggEnd = combEnd - s.turbine(), ptEnd = ggEnd - s.powerTurbine(), end = ptEnd - s.exhaust();
        int inletMetal = Shapes.material(e.inlet().material());
        double hub = Math.clamp(cd.hubTipRatio(), 0.05, 0.95) * axialTip, inletOuter = axialTip * 1.5;
        // the annular inlet: a bellmouth case round the shaft's housing, swirl vanes across the passage
        lathe(casing(), List.of(new double[]{face, axialTip * 1.06}, new double[]{face + 0.6 * s.inlet(), inletOuter}, new double[]{face + s.inlet(), inletOuter * 1.1},
                new double[]{face + s.inlet(), inletOuter * 1.16}, new double[]{face + 0.55 * s.inlet(), inletOuter * 1.06}, new double[]{face, axialTip * 1.1},
                new double[]{face, axialTip * 1.06}), null, 48, c("inlet", inletMetal), false);
        double centre = Math.max(hub * 1.05, 0.35 * axialTip);
        lathe(fixed, List.of(new double[]{face + s.inlet() + 0.001, 0}, new double[]{face + s.inlet(), 0.9 * centre}, new double[]{face + 0.4 * s.inlet(), centre},
                new double[]{face, hub}, new double[]{face, 0}), null, 32, c("inlet", Shapes.darker(inletMetal, 0.1)), false);
        Shapes.bladeRow(fixed, face + 0.6 * s.inlet(), centre * 1.05, inletOuter * 0.95, 14, 0.3 * s.inlet(), 0.35 * s.inlet(), rad(-15), rad(-35), 0.15, c("inlet", Shapes.machined(inletMetal)), 3, true);
        // the compressor, combustor and turbines
        compressor(high, cd, face, s.compressor(), true);
        double impellerOuter = cd.kind() == CompressorDesign.Kind.AXIAL ? s.compressorTip() * 1.06 : s.compressorTip() * 1.4;
        combustor(e.combustor(), compEnd, combEnd, Math.max(s.casingR(), impellerOuter * 0.9), Math.max(0.3 * s.casingR(), s.turbineTip() * 0.6), impellerOuter, s.turbineTip() * 1.05);
        turbine(high, "turbine", Shapes.material(e.turbine().material()), combEnd, s.turbine(), e.turbine().stages(), s.turbineTip(), s.turbineTip(), true);
        turbine(low, "powerTurbine", Shapes.material(e.powerTurbine().material()), ggEnd, s.powerTurbine(), e.powerTurbine().stages(), Math.max(s.turbineTip(), 0.9 * s.ptTip()), s.ptTip(), true);
        shaft(high, face, ggEnd + 0.2 * s.turbine(), Math.max(0.16 * axialTip, 0.004), c("turbine", Shapes.machined(0xff858b93)));
        // the power turbine's shaft forward through the core to the output
        shaft(low, front - nose * 0.3, ptEnd + 0.2 * s.powerTurbine(), Math.max(0.08 * axialTip, 0.003), c("powerTurbine", Shapes.machined(0xff9ea2a7)));
        // the exhaust diffuser and its centre body
        int exhaust = c("exhaust", Shapes.mix(Shapes.material(e.exhaust().material()), Shapes.heat(0.35), 0.5));
        double exitR;
        try { exitR = Math.sqrt(e.sizing().exhaustAreaM2() / Math.PI + Math.pow(0.5 * s.ptTip(), 2)); }
        catch (RuntimeException ex) { exitR = 1.3 * s.ptTip(); }
        exitR = Math.clamp(exitR, s.ptTip(), 1.8 * s.ptTip());
        List<double[]> p = List.of(new double[]{ptEnd, s.ptTip() * 1.08}, new double[]{end, exitR * 1.04}, new double[]{end, exitR}, new double[]{ptEnd, s.ptTip() * 1.04},
                new double[]{ptEnd, s.ptTip() * 1.08});
        lathe(casing(), p, null, 48, exhaust, false);
        lathe(fixed, List.of(new double[]{ptEnd, 0}, new double[]{ptEnd, 0.55 * s.ptTip()}, new double[]{end + 0.3 * s.exhaust(), 0.4 * s.ptTip()}, new double[]{end, 0.05 * s.ptTip()},
                new double[]{end, 0}), null, 28, exhaust, false);
        for (int k = 0; k < 5; k++) {
            Orientation turn = aboutX(2 * Math.PI * k / 5);
            fixed.box(new Vec3d(ptEnd - 0.15 * s.exhaust(), 0, 0).add(turn.rotate(new Vec3d(0, 0.8 * s.ptTip(), 0))), new Vec3d(0.1 * s.exhaust(), 0.27 * s.ptTip(), 0.01 * s.ptTip() + 0.001), turn, exhaust);
        }
        // the gearbox in front (or the bare output flange) and the accessories under the compressor
        if (!e.gearbox().direct()) { reductionGearbox(e, gbStart, face + s.inlet(), s.gearboxR(), 0.5 * s.gearboxR(), e.gearbox().kind() == ReductionGearboxDesign.Kind.OFFSET); }
        else { propFlange(front - 0.3 * nose, 0.6 * axialTip, 0.2 * nose, c("gearbox", Shapes.machined(0xff858b93))); }
        accessories(compEnd + 0.6 * s.compressor(), compEnd, impellerOuter, 0.12 * impellerOuter);
    }

    /**
     * A reduction gearbox from x0 (towards the engine) to x1 (the output side): a case tapering to the output; in line,
     * or (offset) its output above the axis, with the output flange turning on the output's set.
     */
    private void reductionGearbox(ShaftTurbineDesign e, double x0, double x1, double radius, double neck, boolean offset) {
        int metal = c("gearbox", GEARBOX);
        double length = Math.abs(x0 - x1), dir = Math.signum(x1 - x0), up = offset ? 0.6 * radius : 0;
        PartMesh box = new PartMesh();
        List<double[]> p = new ArrayList<>();
        p.add(new double[]{0, 0});
        p.add(new double[]{0, neck});
        p.add(new double[]{0.25 * length, radius});
        p.add(new double[]{0.75 * length, radius * 0.95});
        p.add(new double[]{length, 0.45 * radius});
        p.add(new double[]{length, 0});
        lathe(box, p, null, 40, metal, false);
        fixed.add(box, dir > 0 ? Orientation.IDENTITY : Orientation.rotationVector(Vec3d.Y.scale(Math.PI)), new Vec3d(x0, up, 0));
        if (offset) { fixed.box(new Vec3d(x0 + dir * 0.4 * length, up / 2, 0), new Vec3d(0.3 * length, up / 2, 0.6 * radius), Orientation.IDENTITY, metal); }
        // a bolt circle on the case's joint and the output
        Shapes.boltCircle(fixed, x0 + dir * 0.25 * length, radius * 0.97, 16, 0.03 * radius, 0.02 * radius, Shapes.machined(metal));
        outputAxis = new Vec3d(0, up, 0);   // the flange is drawn about its own axis and placed there
        propFlange(x1 + dir * 0.06 * length, 0.4 * radius, 0.12 * length, c("gearbox", Shapes.machined(0xff858b93)));
    }

    /** The output flange: a disc with its bolts at x, turning with the output. */
    private void propFlange(double x, double radius, double thickness, int colour) {
        output.lathe(new Vec3d(x + thickness, 0, 0), new Vec3d(x - thickness, 0, 0), new double[]{0, 0, 0.3, 0.3, 1, 1}, new double[]{0, 0.4 * radius, 0.4 * radius, radius, radius, 0},
                null, 32, colour, true);
        for (int k = 0; k < 8; k++) {
            Vec3d at = new Vec3d(x - thickness, 0, 0).add(aboutX(2 * Math.PI * k / 8).rotate(new Vec3d(0, 0.72 * radius, 0)));
            output.cylinder(at, at.add(new Vec3d(-0.8 * thickness, 0, 0)), 0.07 * radius, 0.07 * radius, 8, Shapes.machined(colour));
        }
    }
}
