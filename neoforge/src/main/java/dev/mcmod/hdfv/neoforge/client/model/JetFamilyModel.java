package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.SupersonicInlet;
import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.api.propellant.SolidRocket;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.JetInletDesign;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.RamjetDesign;
import dev.mcmod.hdfv.part.engine.ScramjetDesign;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.part.engine.TurboRamjetDesign;

import java.util.ArrayList;
import java.util.List;

import static dev.mcmod.hdfv.neoforge.client.model.JetEngineModel.lathe;
import static dev.mcmod.hdfv.neoforge.client.model.Shapes.aboutX;
import static dev.mcmod.hdfv.neoforge.client.model.Shapes.positive;

/**
 * The 0.10 engines drawn from their designs (the user asked for 3D models of them), each in the jets' frame: inlet
 * forward along +x, y up, z starboard, centred on its length; a page names the component drawn highlighted; in a
 * cutaway the cases are cut in half along the axis.
 * <ul>
 * <li>Ramjet: a translating-spike (cone) inlet ahead of its cowl lip (or ramps in a box, or a pitot lip), the cowl
 *     diffusing to the combustor, a fuel manifold with its injectors, V-gutter flameholder rings inside, the nozzle
 *     converging to its sized throat (and opening again for a convergent-divergent one), tinted by heat.</li>
 * <li>Scramjet: a flat two-ramp forebody over the cowl, the rectangular isolator, the diverging combustor with its
 *     injector struts (cooling passages scored in a fuel-cooled one) and the single expansion ramp nozzle.</li>
 * <li>Pulsejet: the intake (a venturi bell before a valve grid of reed petals, or a valveless one's intake pipe, bent
 *     back alongside the chamber when it faces rearward), the chamber, its taper and the long tailpipe, the spark plug,
 *     mounting bands; the tube tinted by heat as it glows when running.</li>
 * <li>Solid rocket: the casing with its domed closures, the igniter forward, the nozzle (convergence, throat and the
 *     expansion cone) aft, mounting lugs, and a red separation band on a booster cast off when burnt; in a cutaway the
 *     propellant grain: BATES segments with their cores and gaps, a tube, or an end burner.</li>
 * <li>Turbo-ramjet: the turbojet core (JetEngineModel, with its afterburner), the inlet spike ahead of its cowl and the
 *     six bypass ducts from part-way up the compressor round the core into the afterburner.</li>
 * </ul>
 */
public final class JetFamilyModel {
    private JetFamilyModel() {}

    private static final int PIPE = 0xffb08d57, DARK = 0xff2e3135, KNSB = 0xffe3c99a, APCP = 0xffc7b8a0, LINER = 0xff3a3436, RED = 0xffc23a2e;

    /** A component's colour, or the highlight when it is the page shown. */
    private static int c(String highlight, String page, int base) { return page.equals(highlight) ? Shapes.HIGHLIGHT : base; }

    // ---------------------------------------------------------------- ramjet

    public static EngineModel ramjet(RamjetDesign r, String highlight, boolean cutaway) {
        String h = highlight == null ? "" : highlight;
        PartMesh fixed = new PartMesh(), cases = new PartMesh(), shell = cutaway ? cases : fixed;
        EngineModel model = new EngineModel(fixed);
        double capture = Math.sqrt(positive(r.inlet().captureAreaM2(), 0.1) / Math.PI), burner = Math.sqrt(positive(r.combustor().areaRatio(), 1.5)) * capture;
        double li = positive(r.inlet().lengthM(), 0.5), lc = positive(r.combustor().lengthM(), 1), ln = positive(r.nozzle().tailpipeLengthM(), 0.3);
        double front = (li + lc + ln) / 2, lipX = front, wall = 0.05 * burner, outer = burner + wall;
        int inletMetal = Shapes.material(r.inlet().material()), burnerMetal = Shapes.material(r.combustor().material()), nozzleMetal = Shapes.material(r.nozzle().material());
        // the inlet: a cone ahead of the lip (its half-angle the design's), ramps in a box, or a pitot lip
        SupersonicInlet.Kind kind = r.inlet().kind();
        if (kind == SupersonicInlet.Kind.CONE) {
            double half = Math.toRadians(Math.clamp(r.inlet().coneHalfAngleDeg(), 5, 40)), body = 0.62 * capture;
            double cone = Math.min(body / Math.tan(half), 0.9 * li);
            lipX = front - 0.85 * cone;
            List<double[]> spike = new ArrayList<>();
            spike.add(new double[]{front, 0});
            spike.add(new double[]{front - cone, body});
            spike.add(new double[]{lipX - 0.35 * li, body * 0.9});
            spike.add(new double[]{lipX - 0.8 * li + 0.85 * cone, 0.02 * body});
            spike.add(new double[]{lipX - 0.8 * li + 0.85 * cone - 0.01, 0});
            lathe(fixed, spike, null, 40, c(h, "inlet", Shapes.machined(inletMetal)), false);
            for (int k = 0; k < 3; k++) {   // struts holding the spike in the cowl
                Orientation turn = aboutX(2 * Math.PI * k / 3 + Math.PI / 6);
                fixed.box(new Vec3d(lipX - 0.45 * li, 0, 0).add(turn.rotate(new Vec3d(0, (body + burner) / 2, 0))), new Vec3d(0.08 * li, (burner - body) / 2, 0.01 * capture),
                        turn, c(h, "inlet", inletMetal));
            }
        } else if (kind == SupersonicInlet.Kind.RAMPS) {
            double side = Math.sqrt(Math.PI) * capture / 2, ramp = 0.6 * li;
            fixed.hexahedron(new Vec3d[]{new Vec3d(front, side, -side), new Vec3d(front, side, side), new Vec3d(front - ramp, -0.2 * side, side), new Vec3d(front - ramp, -0.2 * side, -side),
                    new Vec3d(front, side + wall, -side), new Vec3d(front, side + wall, side), new Vec3d(front - ramp, -0.2 * side + wall, side), new Vec3d(front - ramp, -0.2 * side + wall, -side)},
                    c(h, "inlet", inletMetal));
            lipX = front - 0.3 * li;
        }
        // the cowl: its outer skin to the combustor's case, its inner duct diffusing from the capture to the burner
        List<double[]> cowl = new ArrayList<>();
        double lipR = kind == SupersonicInlet.Kind.PITOT ? capture : capture * 1.02;
        cowl.add(new double[]{lipX, lipR});
        cowl.add(new double[]{lipX - 0.6 * (lipX - (front - li)), outer});
        cowl.add(new double[]{front - li, outer});
        cowl.add(new double[]{front - li, burner});
        cowl.add(new double[]{lipX - 0.5 * (lipX - (front - li)), 0.5 * (capture + burner)});
        cowl.add(new double[]{lipX, lipR * 0.99});
        lathe(shell, cowl, null, 48, c(h, "inlet", inletMetal), false);
        // the combustor's case with its liner, the manifold ring and injectors, the flameholders inside
        double x0 = front - li, x1 = x0 - lc;
        shell.hot(true);   // 0.11: the case and nozzle glow with the flame (PartRenderer.glow)
        shell.lathe(new Vec3d(x0, 0, 0), new Vec3d(x1, 0, 0), new double[]{0, 1, 1, 0, 0}, new double[]{outer, outer, burner, burner, outer},
                new int[]{c(h, "combustor", burnerMetal), c(h, "combustor", Shapes.mix(burnerMetal, Shapes.heat(0.35), 0.4)), c(h, "combustor", LINER), c(h, "combustor", LINER),
                        c(h, "combustor", burnerMetal)}, 48, c(h, "combustor", burnerMetal), true);
        shell.hot(false);
        double ring = x0 - 0.06 * lc;
        fixed.tube(PartMesh.circle(new Vec3d(ring, 0, 0), Vec3d.X, outer * 1.08, 48), 0.03 * burner, null, c(h, "control", PIPE), null, 6, true);
        for (int k = 0; k < 12; k++) {
            double a = 2 * Math.PI * k / 12;
            Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
            fixed.cylinder(new Vec3d(ring, 0, 0).add(dir.scale(outer * 1.08)), new Vec3d(ring, 0, 0).add(dir.scale(0.55 * burner)), 0.012 * burner, 0.012 * burner, 6,
                    c(h, "control", Shapes.machined(PIPE)));
        }
        JetEngineModel.flangeAt(shell, x0, outer, c(h, "combustor", Shapes.darker(burnerMetal, 0.2)));
        JetEngineModel.flangeAt(shell, x1, outer, c(h, "nozzle", Shapes.darker(nozzleMetal, 0.2)));
        for (int k = 0; k < 12; k++) {
            double a = 2 * Math.PI * k / 12;
            Details.place(fixed, DetailKit.Part.AN_FITTING, new Vec3d(ring, 0, 0).add(new Vec3d(0, Math.cos(a), Math.sin(a)).scale(outer)), new Vec3d(0, Math.cos(a), Math.sin(a)),
                    Vec3d.X, 0.035 * burner, Details.SMALL);
        }
        Vec3d igniterDir = new Vec3d(0, Math.cos(2.0), Math.sin(2.0));
        Details.place(fixed, DetailKit.Part.IGNITER, new Vec3d(x0 - 0.22 * lc, 0, 0).add(igniterDir.scale(outer)), igniterDir, Vec3d.X, 0.07 * burner, Details.SMALL);
        for (int k = 0; k < 2; k++) {
            double rr = burner * (0.4 + 0.35 * k), x = x0 - (0.2 + 0.06 * k) * lc;
            fixed.lathe(new Vec3d(x + 0.03 * lc, 0, 0), new Vec3d(x - 0.03 * lc, 0, 0), new double[]{0, 1, 1, 0}, new double[]{rr, rr + 0.07 * burner, rr - 0.07 * burner, rr},
                    null, 40, c(h, "combustor", Shapes.mix(burnerMetal, LINER, 0.5)), true);
        }
        // hangers on top
        for (double x : new double[]{x0 - 0.2 * lc, x1 + 0.2 * lc}) {
            fixed.box(new Vec3d(x, outer + 0.04 * burner, 0), new Vec3d(0.04 * lc, 0.05 * burner, 0.05 * burner), Orientation.IDENTITY, c(h, "combustor", Shapes.darker(burnerMetal, 0.2)));
            for (int k = -1; k <= 1; k += 2) {
                Details.place(fixed, DetailKit.Part.HEX_BOLT, new Vec3d(x + k * 0.025 * lc, outer + 0.09 * burner, 0), Vec3d.Y, Vec3d.X, 0.02 * burner, Details.SMALL);
            }
        }
        // the nozzle to its throat (and out again)
        double throat;
        try { throat = Math.sqrt(r.model().throatAreaM2() / Math.PI); } catch (RuntimeException e) { throat = 0.7 * burner; }
        throat = Math.clamp(throat, 0.1 * burner, burner);
        List<double[]> nz = new ArrayList<>();
        nz.add(new double[]{x1, outer});
        if (r.nozzle().convergentDivergent()) {
            double exit = Math.min(throat * Math.sqrt(Math.max(1, r.nozzle().exitAreaRatio())), outer);
            nz.add(new double[]{x1 - 0.45 * ln, throat + wall});
            nz.add(new double[]{x1 - ln, exit + wall});
            nz.add(new double[]{x1 - ln, exit});
            nz.add(new double[]{x1 - 0.45 * ln, throat});
        } else {
            nz.add(new double[]{x1 - ln, throat + wall});
            nz.add(new double[]{x1 - ln, throat});
        }
        nz.add(new double[]{x1, burner});
        nz.add(new double[]{x1, outer});
        int[] heat = new int[nz.size()];
        for (int k = 0; k < nz.size(); k++) { heat[k] = c(h, "nozzle", Shapes.mix(nozzleMetal, Shapes.heat(0.3 + 0.5 * (x1 - nz.get(k)[0]) / ln), 0.5)); }
        shell.hot(true);
        lathe(shell, nz, heat, 48, heat[0], false);
        shell.hot(false);
        if (cutaway) { model.section(cases, Vec3d.ZERO, Vec3d.Z); }
        return model;
    }

    // ---------------------------------------------------------------- scramjet

    public static EngineModel scramjet(ScramjetDesign x, String highlight, boolean cutaway) {
        String h = highlight == null ? "" : highlight;
        PartMesh fixed = new PartMesh(), cases = new PartMesh(), sides = cutaway ? cases : fixed;
        EngineModel model = new EngineModel(fixed);
        double w = positive(x.width(), 0.2), half = w / 2, capture = positive(x.inlet().captureAreaM2(), 0.05) / w, throat = positive(x.inlet().throatAreaM2(), 0.01) / w;
        double li = positive(x.inlet().lengthM(), 1), isolator = Math.max(0, x.inlet().isolatorLengthOverHeight()) * Math.sqrt(positive(x.inlet().throatAreaM2(), 0.01));
        double lc = positive(x.combustor().lengthM(), 0.5), length = li + isolator + 2 * lc, front = length / 2, t = 0.04 * Math.max(capture, throat) + 0.005;
        double lip = front - li, iso = lip - isolator, comb = iso - lc, end = comb - lc;
        double exitH = throat * Math.max(1, x.combustor().areaRatio());
        int inletMetal = Shapes.material(x.inlet().material()), burnerMetal = Shapes.material(x.combustor().material());
        // the forebody's two ramps from the nose down to the throat's height over the cowl
        double rampMid = front - 0.55 * li, midH = throat + 0.45 * (capture - throat);
        int ramp = c(h, "inlet", inletMetal);
        plate(fixed, new Vec3d(front, capture, 0), new Vec3d(rampMid, midH, 0), half, t, ramp);
        plate(fixed, new Vec3d(rampMid, midH, 0), new Vec3d(lip, throat, 0), half, t, c(h, "inlet", Shapes.darker(inletMetal, 0.1)));
        // the upper wall on: the isolator, the combustor, the expansion ramp
        plate(fixed, new Vec3d(lip, throat, 0), new Vec3d(comb, throat, 0), half, t, c(h, "inlet", inletMetal));
        int burnerColour = c(h, "combustor", Shapes.mix(burnerMetal, Shapes.heat(0.4), 0.45));
        fixed.hot(true);
        plate(fixed, new Vec3d(comb, throat, 0), new Vec3d(end, throat + 0.9 * (exitH - throat) + 0.5 * lc * 0.25, 0), half, t, c(h, "combustor", Shapes.mix(burnerMetal, Shapes.heat(0.55), 0.5)));
        // the cowl: its sharp lip, the isolator's floor, the combustor's floor opening away
        plate(fixed, new Vec3d(lip + 0.02 * li, -t, 0), new Vec3d(iso, 0, 0), half, t, c(h, "inlet", Shapes.darker(inletMetal, 0.2)));
        plate(fixed, new Vec3d(iso, 0, 0), new Vec3d(comb, -(exitH - throat), 0), half, t, burnerColour);
        fixed.hot(false);
        // side walls (see-through in a cutaway)
        int side = c(h, "combustor", Shapes.darker(burnerMetal, 0.15));
        for (int s = -1; s <= 1; s += 2) {
            double z = s * (half + t / 2);
            sides.hexahedron(new Vec3d[]{new Vec3d(lip, -t, z - t / 2), new Vec3d(lip, throat + t, z - t / 2), new Vec3d(end, throat + t, z - t / 2), new Vec3d(comb, -(exitH - throat) - t, z - t / 2),
                    new Vec3d(lip, -t, z + t / 2), new Vec3d(lip, throat + t, z + t / 2), new Vec3d(end, throat + t, z + t / 2), new Vec3d(comb, -(exitH - throat) - t, z + t / 2)}, side);
        }
        // injector struts across the duct at the combustor's start; scored cooling passages on a fuel-cooled one
        for (int k = 1; k <= 4; k++) {
            double z = -half + w * k / 5.0;
            fixed.box(new Vec3d(iso - 0.04 * lc, throat / 2, z), new Vec3d(0.04 * lc, throat / 2, 0.006 * w + 0.002), Orientation.IDENTITY, c(h, "control", PIPE));
        }
        if (x.combustor().fuelCooled()) {
            for (int k = 0; k < 10; k++) {
                double z = -half + w * (k + 0.5) / 10;
                fixed.box(new Vec3d((iso + comb) / 2, throat + t + 0.004, z), new Vec3d(lc / 2, 0.003, 0.004 * w + 0.001), Orientation.IDENTITY, c(h, "combustor", PIPE));
            }
        }
        // the fuel line along the top
        List<Vec3d> fuelLine = PartMesh.bezier(new Vec3d(front - 0.2 * li, capture + t, 0), new Vec3d(lip, throat + 3 * t, 0), new Vec3d(iso + 0.2 * lc, throat + 3 * t, 0),
                new Vec3d(iso, throat + t, 0), 10);
        Details.braidedLine(fixed, fuelLine, 0.015 * w + 0.002);
        // 0.11: the side walls bolted to the upper wall and the cowl along their edges
        int count = Math.max(6, (int) Math.round((lip - end) / (0.06 * w + 0.02)));
        for (int s = -1; s <= 1; s += 2) {
            double z = s * (half + t);
            Details.boltRow(sides, new Vec3d(lip - 0.02 * li, throat + 0.5 * t, z), new Vec3d(end + 0.05 * lc, throat + 0.5 * t, z), count, new Vec3d(0, 0, s), 0.02 * w + 0.002,
                    DetailKit.Part.SOCKET_CAP);
            Details.boltRow(sides, new Vec3d(lip - 0.02 * li, -0.5 * t, z), new Vec3d(comb + 0.1 * lc, -0.5 * t - 0.2 * (exitH - throat), z), Math.max(4, count / 2), new Vec3d(0, 0, s),
                    0.02 * w + 0.002, DetailKit.Part.SOCKET_CAP);
        }
        if (cutaway) { model.section(cases, Vec3d.ZERO, Vec3d.Z); }
        return model;
    }

    /** A plate of a thickness under a line from a to b (in x-y), across the duct's width. */
    private static void plate(PartMesh out, Vec3d a, Vec3d b, double half, double t, int colour) {
        out.hexahedron(new Vec3d[]{new Vec3d(a.x(), a.y(), -half), new Vec3d(a.x(), a.y(), half), new Vec3d(b.x(), b.y(), half), new Vec3d(b.x(), b.y(), -half),
                new Vec3d(a.x(), a.y() + t, -half), new Vec3d(a.x(), a.y() + t, half), new Vec3d(b.x(), b.y() + t, half), new Vec3d(b.x(), b.y() + t, -half)}, colour);
    }

    // ---------------------------------------------------------------- pulsejet

    public static EngineModel pulsejet(PulsejetDesign p, String highlight, boolean cutaway) {
        String h = highlight == null ? "" : highlight;
        PartMesh fixed = new PartMesh(), cases = new PartMesh(), shell = cutaway ? cases : fixed;
        EngineModel model = new EngineModel(fixed);
        var t = p.tube();
        var v = p.valve();
        double rc = positive(t.chamberDiameterM(), 0.3) / 2, rt = positive(t.tailDiameterM(), 0.2) / 2, lc = positive(t.chamberLengthM(), 0.5), lt = positive(t.taperLengthM(), 0.4),
                ltail = positive(t.tailLengthM(), 1), wall = Math.max(positive(t.wallThicknessM(), 0.001), 0.004 * rc);
        double intake = v.valveless() ? positive(v.intakeLengthM(), 0.2) : 0.35 * rc;
        double total = (v.valveless() && v.intakeFacesRear() ? 0 : intake) + lc + lt + ltail, front = total / 2;
        double x0 = front - (v.valveless() && v.intakeFacesRear() ? 0 : intake), x1 = x0 - lc, x2 = x1 - lt, x3 = x2 - ltail;
        int metal = Shapes.material(t.material());
        // the tube: chamber, taper, tailpipe, a slight flare at its end, tinted by its heat
        List<double[]> tube = new ArrayList<>();
        tube.add(new double[]{x0, rc + wall});
        tube.add(new double[]{x1, rc + wall});
        tube.add(new double[]{x2, rt + wall});
        tube.add(new double[]{x3 + 0.04 * ltail, rt + wall});
        tube.add(new double[]{x3, rt * 1.12 + wall});
        tube.add(new double[]{x3, rt * 1.12});
        tube.add(new double[]{x3 + 0.04 * ltail, rt});
        tube.add(new double[]{x2, rt});
        tube.add(new double[]{x1, rc});
        tube.add(new double[]{x0, rc});
        tube.add(new double[]{x0, rc + wall});
        int[] heat = new int[tube.size()];
        for (int k = 0; k < tube.size(); k++) {
            double along = (x0 - tube.get(k)[0]) / Math.max(total, 1e-6);
            heat[k] = c(h, "tube", Shapes.mix(metal, Shapes.heat(0.15 + 0.5 * Math.sin(Math.PI * Math.min(1, along * 1.4))), 0.45));
        }
        shell.hot(true);
        lathe(shell, tube, heat, 40, heat[0], false);
        shell.hot(false);
        // mounting bands and the spark plug
        for (double x : new double[]{x1 + 0.3 * lc, x2 - 0.5 * ltail}) {
            double rr = (x > x1 ? rc : rt) + wall;
            fixed.tube(PartMesh.circle(new Vec3d(x, 0, 0), Vec3d.X, rr * 1.04, 40), 0.03 * rr, null, c(h, "tube", Shapes.darker(metal, 0.3)), null, 6, true);
            fixed.box(new Vec3d(x, rr * 1.15, 0), new Vec3d(0.02 * lc + 0.01, 0.1 * rr, 0.05 * rr + 0.01), Orientation.IDENTITY, c(h, "tube", Shapes.darker(metal, 0.3)));
            Details.place(fixed, DetailKit.Part.HEX_BOLT, new Vec3d(x, rr * 1.25, 0), Vec3d.Y, Vec3d.X, 0.04 * rr + 0.003, Details.SMALL);
        }
        Vec3d plug = new Vec3d(x0 - 0.35 * lc, 0, rc + wall);
        Details.place(fixed, DetailKit.Part.SPARK_PLUG, plug, Vec3d.Z, Vec3d.X, 0.09 * rc + 0.006, Details.SMALL);
        if (!v.valveless()) {
            // the valve head: a venturi bell before the grid; the reeds behind it (a cutaway shows them); the fuel spray bar across the bell
            List<double[]> bell = new ArrayList<>();
            bell.add(new double[]{front, rc * 1.08});
            bell.add(new double[]{front - 0.5 * intake, rc * 0.92});
            bell.add(new double[]{x0, rc + wall});
            bell.add(new double[]{x0, rc});
            bell.add(new double[]{front - 0.5 * intake, rc * 0.88});
            bell.add(new double[]{front, rc * 1.04});
            lathe(shell, bell, null, 40, c(h, "valve", Shapes.machined(metal)), false);
            JetEngineModel.flangeAt(shell, x0, rc + wall, c(h, "valve", Shapes.darker(metal, 0.2)));
            fixed.lathe(new Vec3d(x0 + 0.01 * rc, 0, 0), new Vec3d(x0 - 0.01 * rc, 0, 0), new double[]{0, 0, 1, 1}, new double[]{0, rc, rc, 0}, null, 32, c(h, "valve", DARK), true);
            int reed = c(h, "valve", Shapes.machined(Shapes.material(v.reedMaterial())));
            for (int k = 0; k < 9; k++) {
                double a = 2 * Math.PI * k / 9;
                Orientation turn = aboutX(a);
                fixed.box(new Vec3d(x0 - 0.02 * rc, 0, 0).add(turn.rotate(new Vec3d(0, 0.55 * rc, 0))), new Vec3d(0.004 * rc + 0.001, 0.35 * rc, 0.09 * rc), turn, reed);
            }
            fixed.box(new Vec3d(front - 0.3 * intake, 0, 0), new Vec3d(0.02 * rc, 0.9 * rc, 0.02 * rc), Orientation.IDENTITY, c(h, "control", PIPE));
        } else {
            double ri = positive(v.intakeDiameterM(), 0.05) / 2;
            int pipe = c(h, "valve", Shapes.mix(metal, Shapes.heat(0.3), 0.4));
            if (v.intakeFacesRear()) {
                // out of the chamber's front, round and back alongside the chamber, open to the rear
                Vec3d start = new Vec3d(x0, 0, 0), out = new Vec3d(x0 + 1.4 * rc, 0, 0), over = new Vec3d(x0 + 1.4 * rc, rc + 1.6 * ri + wall, 0);
                Vec3d back = new Vec3d(x0 - Math.max(0, intake - 2 * rc), rc + 1.6 * ri + wall, 0);
                List<Vec3d> path = new ArrayList<>(PartMesh.bezier(start, out, over.add(new Vec3d(0.2 * rc, 0, 0)), over, 12));
                path.add(back);
                shell.tube(path, ri + wall, 16, pipe);
            } else {
                shell.cylinder(new Vec3d(x0, 0, 0), new Vec3d(front, 0, 0), ri + wall, ri * 1.1 + wall, 20, pipe);
            }
            fixed.cylinder(new Vec3d(x0 + 0.02, rc * 0.4, 0), new Vec3d(x0 + 0.02, rc + 0.04, 0), 0.01 * rc + 0.003, 0.01 * rc + 0.003, 6, c(h, "control", PIPE));
        }
        if (cutaway) { model.section(cases, Vec3d.ZERO, Vec3d.Z); }
        return model;
    }

    // ---------------------------------------------------------------- solid rocket

    public static EngineModel solidRocket(SolidRocketDesign s, String highlight, boolean cutaway) {
        String h = highlight == null ? "" : highlight;
        PartMesh fixed = new PartMesh(), cases = new PartMesh(), shell = cutaway ? cases : fixed;
        EngineModel model = new EngineModel(fixed);
        var g = s.grain();
        var n = s.nozzle();
        double ro = positive(s.diameter(), 0.1) / 2, ri = positive(g.outerDiameterM(), 0.08) / 2, lg = positive(s.grainLengthM(), 0.3);
        double rt = positive(n.throatDiameterM(), 0.02) / 2, re = rt * Math.sqrt(Math.max(1, n.expansionRatio()));
        double cone = (re - rt) / Math.tan(Math.toRadians(Math.clamp(n.divergenceHalfAngleDeg(), 5, 30)));
        double dome = 0.45 * ro, total = dome + lg + dome + 0.8 * rt + cone, front = total / 2;
        double xf = front - dome, xa = xf - lg, xt = xa - dome - 0.8 * rt, xe = xt - cone;
        int casingMetal = Shapes.material(s.casing().material()), nozzleMetal = Shapes.material(n.material());
        // the casing: forward dome, cylinder, aft dome closing to the throat's housing
        List<double[]> body = new ArrayList<>();
        body.add(new double[]{front + 0.02 * ro, 0});
        for (int k = 0; k <= 6; k++) { double a = Math.PI / 2 * k / 6; body.add(new double[]{xf + dome * Math.cos(a), ro * Math.sin(a)}); }
        body.add(new double[]{xa, ro});
        for (int k = 1; k <= 6; k++) { double a = Math.PI / 2 * k / 6; body.add(new double[]{xa - dome * Math.sin(a), Math.max(ro * Math.cos(a), 2.2 * rt)}); }
        body.add(new double[]{xa - dome, 0});
        // 0.11: a motor case is rolled sheet or drawn tube, not a forging: a steel one painted, an aluminium one bare
        var caseFinish = s.casing().material().name().startsWith("aluminium") ? dev.mcmod.hdfv.look.Finishes.BRUSHED_STEEL : dev.mcmod.hdfv.look.Finishes.PAINT;
        var before = shell.finish();
        lathe(shell.finish(caseFinish), body, null, 40, c(h, "casing", casingMetal), false);
        shell.finish(before);
        // the nozzle: convergence to the throat, the cone to the exit
        List<double[]> nz = new ArrayList<>();
        double t = Math.max(0.15 * rt, 0.002);
        nz.add(new double[]{xa - dome + 0.01, 2.2 * rt + t});
        nz.add(new double[]{xt, rt + t});
        nz.add(new double[]{xe, re + t});
        nz.add(new double[]{xe, re});
        nz.add(new double[]{xt, rt});
        nz.add(new double[]{xa - dome + 0.01, 2.0 * rt});
        nz.add(new double[]{xa - dome + 0.01, 2.2 * rt + t});
        fixed.hot(true);
        lathe(fixed, nz, null, 32, c(h, "nozzle", Shapes.mix(nozzleMetal, Shapes.heat(0.25), 0.3)), false);
        fixed.hot(false);
        // the igniter forward, lugs on top, a red separation band on a booster that goes when burnt
        fixed.cylinder(new Vec3d(front, 0, 0), new Vec3d(front + 0.06 * ro + 0.01, 0, 0), 0.18 * ro, 0.12 * ro, 12, c(h, "booster", DARK));
        // 0.11: the closures held by radial pins round the case, the igniter's connector, a plate, the throat's graphite insert
        int pins = (int) Math.clamp(Math.round(2 * Math.PI * ro / (0.08 * ro + 0.02)), 8, 32);
        for (double x : new double[]{xf - 0.03 * lg, xa + 0.03 * lg}) {
            for (int k = 0; k < pins; k++) {
                double a = 2 * Math.PI * (k + 0.5) / pins;
                Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
                Details.place(fixed, DetailKit.Part.SOCKET_CAP, new Vec3d(x, 0, 0).add(dir.scale(ro)), dir, Vec3d.X, 0.035 * ro + 0.002, Details.SMALL, false);
            }
        }
        Details.place(fixed, DetailKit.Part.CONNECTOR, new Vec3d(front + 0.06 * ro + 0.01, 0, 0), Vec3d.X, Vec3d.Y, 0.12 * ro + 0.004, Details.SMALL);
        Details.place(fixed, DetailKit.Part.DATA_PLATE, new Vec3d((xf + xa) / 2, 0, ro + 0.0005), Vec3d.Z, Vec3d.X, Math.min(0.5 * lg, 0.6 * ro), Details.TINY);
        // the insert lines the throat just inside the nozzle's wall, from part way down the convergence into the cone, facing the axis
        double lc = Math.max(0.8 * rt - 0.01, 0.2 * rt), ahead = 0.4 * lc, behind = 0.3 * cone, split = ahead / (ahead + behind);
        fixed.finish(dev.mcmod.hdfv.look.Finishes.GRAPHITE).hot(true).lathe(new Vec3d(xt + ahead, 0, 0), new Vec3d(xt - behind, 0, 0), new double[]{1, split, 0},
                new double[]{0.985 * (rt + 0.3 * (re - rt)), 0.985 * rt, 0.985 * 1.4 * rt}, null, 24, c(h, "nozzle", 0xff3a3a3c), true).finish(null).hot(false);
        for (double x : new double[]{xf - 0.2 * lg, xa + 0.2 * lg}) {
            fixed.box(new Vec3d(x, ro + 0.06 * ro, 0), new Vec3d(0.05 * lg + 0.005, 0.07 * ro, 0.12 * ro), Orientation.IDENTITY, c(h, "booster", Shapes.darker(casingMetal, 0.25)));
        }
        if (s.booster().jettisonWhenBurnt()) {
            fixed.tube(PartMesh.circle(new Vec3d(xf - 0.05 * lg, 0, 0), Vec3d.X, ro * 1.01, 40), 0.03 * ro, null, c(h, "booster", RED), null, 6, true);
        }
        if (cutaway) {
            // the grain, cut: segments with their cores and the gaps between, a tube, or a solid end burner
            int propellant = c(h, "grain", g.propellant().equals("apcp") ? APCP : KNSB);
            double core = g.kind() == SolidRocket.GrainKind.END_BURNER ? 0 : Math.max(positive(g.coreDiameterM(), 0.02) / 2, 0.001);
            int segments = g.kind() == SolidRocket.GrainKind.BATES ? Math.max(1, g.segments()) : 1;
            double gap = segments > 1 ? 0.04 * lg / segments : 0, len = (lg - gap * (segments - 1)) / segments;
            for (int k = 0; k < segments; k++) {
                double a = xf - k * (len + gap), b = a - len;
                cases.lathe(new Vec3d(a, 0, 0), new Vec3d(b, 0, 0), new double[]{0, 0, 1, 1}, new double[]{core, ri, ri, core}, null, 32, propellant, true);
            }
            cases.lathe(new Vec3d(xf, 0, 0), new Vec3d(xa, 0, 0), new double[]{0, 1}, new double[]{ri * 1.01, ri * 1.01}, null, 32, c(h, "casing", LINER), false);
        }
        if (cutaway) { model.section(cases, Vec3d.ZERO, Vec3d.Z); }
        return model;
    }

    /** Where a booster's nozzle exit is (x, its radius), for its flame. */
    public static double[] rocketExit(SolidRocketDesign s) {
        var n = s.nozzle();
        double ro = positive(s.diameter(), 0.1) / 2, lg = positive(s.grainLengthM(), 0.3), rt = positive(n.throatDiameterM(), 0.02) / 2;
        double re = rt * Math.sqrt(Math.max(1, n.expansionRatio())), cone = (re - rt) / Math.tan(Math.toRadians(Math.clamp(n.divergenceHalfAngleDeg(), 5, 30)));
        double dome = 0.45 * ro, total = dome + lg + dome + 0.8 * rt + cone;
        return new double[]{-total / 2, re};
    }

    // ---------------------------------------------------------------- turbo-ramjet

    /** The turbo-ramjet's core as a turbojet with a pitot face of its inlet's capture (JetEngineModel), with its spike and bypass ducts drawn round it. */
    public static EngineModel turboRamjet(TurboRamjetDesign t, String highlight, boolean cutaway) {
        var in = t.inlet();
        JetEngineDesign core = new JetEngineDesign(t.name(), new JetInletDesign("inlet", Turbojet.Inlet.PITOT, positive(in.captureAreaM2(), 0.5), 0.95, positive(in.lengthM(), 1),
                in.material(), Double.NaN), t.compressor(), t.combustor(), t.turbine(), t.nozzle(), t.control(), t.afterburner());
        return JetEngineModel.build(core, highlight, cutaway, f -> {
            int metal = Shapes.material(in.material());
            double capture = Math.sqrt(positive(in.captureAreaM2(), 0.5) / Math.PI), half = Math.toRadians(Math.clamp(in.coneHalfAngleDeg(), 5, 40)), body = 0.55 * capture;
            double cone = Math.min(body / Math.tan(half), 1.2 * (f.front() - f.face()));
            List<double[]> spike = new ArrayList<>();
            spike.add(new double[]{f.front() + 0.8 * cone, 0});
            spike.add(new double[]{f.front() - 0.2 * cone, body});
            spike.add(new double[]{f.face() + 0.1 * (f.front() - f.face()), 0.6 * body});
            spike.add(new double[]{f.face() + 0.02, 0});
            lathe(f.fixed(), spike, null, 40, f.c("inlet", Shapes.machined(metal)), false);
            // six bypass ducts from part-way up the compressor round the core into the afterburner
            int duct = f.c("bypass", Shapes.material(t.bypass().material()));
            double from = f.face() - 0.45 * (f.face() - f.compressorEnd()), to = f.nozzleStart() + 0.6 * (f.turbineEnd() - f.nozzleStart()), r = f.outer() * 1.12, rd = 0.1 * f.tip();
            for (int k = 0; k < 6; k++) {
                double a = 2 * Math.PI * (k + 0.5) / 6;
                Vec3d dir = new Vec3d(0, Math.cos(a), Math.sin(a));
                Vec3d p0 = new Vec3d(from, 0, 0).add(dir.scale(f.tip() * 1.04)), p1 = new Vec3d(from - 0.05, 0, 0).add(dir.scale(r));
                Vec3d p2 = new Vec3d(to + 0.05, 0, 0).add(dir.scale(r)), p3 = new Vec3d(to, 0, 0).add(dir.scale(f.turbineTip() * 1.05));
                List<Vec3d> path = PartMesh.bezier(p0, p1, p2, p3, 16);
                f.fixed().tube(path, rd, 10, duct);
                // 0.11: a bolted flange near each end of the duct
                for (int end = 0; end < 2; end++) {
                    Vec3d at = path.get(end == 0 ? 2 : path.size() - 3), next = path.get(end == 0 ? 3 : path.size() - 4);
                    Vec3d axis = next.subtract(at);
                    f.fixed().lathe(at, at.add(axis.normalized().scale(0.25 * rd)), new double[]{0, 0, 1, 1}, new double[]{rd, rd * 1.35, rd * 1.35, rd}, null, 20, duct, true);
                    Details.boltCircle(f.fixed(), at, axis.scale(-1), rd * 1.2, 8, 0.12 * rd, DetailKit.Part.HEX_NUT, 0);
                }
            }
        });
    }
}
