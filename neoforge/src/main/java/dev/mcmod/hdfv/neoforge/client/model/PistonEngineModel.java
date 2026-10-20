package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.ValveLift;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.part.engine.CoolingDesign;
import dev.mcmod.hdfv.part.engine.CylinderHeadDesign;
import dev.mcmod.hdfv.part.engine.FuelSystemDesign;
import dev.mcmod.hdfv.part.engine.LubricationDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ValvetrainDesign;
import dev.mcmod.hdfv.look.PistonEngineLayout;
import dev.mcmod.hdfv.look.PistonEngineLayout.Cam;
import dev.mcmod.hdfv.look.PistonEngineLayout.Rocker;
import dev.mcmod.hdfv.look.PistonEngineLayout.Valve;

import java.util.ArrayList;
import java.util.List;

import static dev.mcmod.hdfv.neoforge.client.model.Shapes.aboutX;
import static dev.mcmod.hdfv.neoforge.client.model.Shapes.positive;

/**
 * A piston engine drawn from its design, as an engine looks: block with its cylinder bulges, ribs and mounts, head and
 * cam cover, crankshaft with webs, counterweights, flywheel and ring gear, I-beam rods, pistons with ring grooves and
 * a domed or dished crown, tulip valves on coil springs under buckets, cams whose lobes follow the cam's lift law, the
 * timing chain or belt, curved runners into a plenum with its throttle body and filter, injectors on a rail, headers
 * tinted by heat into a collector, sump, oil filter, alternator, starter, the accessory belt, radiator and fan, or the
 * finned barrels of an air-cooled engine. The pieces move with the crank: rods swing and pistons slide by the slider
 * crank, cams turn at half speed and the valves lift by the cam's law, springs shorten. Every size is the design's;
 * the accessories (alternator, starter, pump) are typical sizes scaled with the bore.
 * <p>
 * Frame: crank along +x (the front, where the timing drive is), y up, z starboard. A design screen page names the
 * component drawn highlighted; in a cutaway the casings are see-through and the internals are drawn.
 * <p>
 * 0.14: where things are comes from core's PistonEngineLayout (the frame, the valvetrain's geometry, the intake,
 * exhaust, sump, radiator and accessories): the envelope a body is checked against is built from the same numbers.
 * The intake of an inline engine is compact (runners curving down beside the head, the plenum beside it, an air box by
 * the block); every engine's exhaust leaves its collector by a down pipe.
 */
public final class PistonEngineModel {
    private static final int RUBBER = 0xff1c1d1f, BLACK = 0xff26282b, COVER = 0xff8e2323, PLASTIC = 0xff2d2f33, GASKET = 0xff34363a, BOLT = 0xff5d636b,
            CERAMIC = 0xffefefef, CHAIN = 0xff4a4f55, FILTER = 0xff2a5caa, SPRING = 0xff3a3f45, CARBON = 0xff9a968e,
            GROOVE = 0xff3c4046, RADIATOR_CORE = 0xff3d4249, RADIATOR_TANK = 0xff1f2124, YELLOW = 0xffd8b23a, INJECTOR = 0xff2c4f8a, COIL = 0xff1b1c1e;

    private final PistonEngineDesign e;
    private final String highlight;
    private final boolean cutaway;
    private final PartMesh fixed = new PartMesh(), commonShells = new PartMesh();
    private final PartMesh[] bankShells;
    private final EngineModel model = new EngineModel(fixed);

    // geometry, with nonsense values replaced so that a design being edited still draws (0.14: PistonEngineLayout's)
    private final PistonEngineLayout L;
    private final int per, banks;
    private final double bore, spacing, deck, wall, r, rodLength, gasket, headHeight, half, front, crankcase, s;
    private final double[] bankAngle, bankOffset;
    private final int[] intakeSide;
    private final double top, coverThickness, frontPlane;
    /** The flywheel's ring gear's tip radius (m; the flywheel is drawn at one size). */
    private static final double RING_GEAR = PistonEngineLayout.RING_GEAR;
    /** Where the first bank's exhaust collector ends and which way is out from it (the turbocharger hangs there). */
    private Vec3d collectorEnd = Vec3d.ZERO, collectorOut = Vec3d.Z;
    /** 0.11: where the turbocharger's bearing housing is (its oil feed goes there), null without one; the radiator hoses' ends. */
    private Vec3d turboBearing;
    private final List<Vec3d[]> hoseEnds = new ArrayList<>();

    private PistonEngineModel(PistonEngineDesign e, String highlight, boolean cutaway) {
        this.e = e; this.highlight = highlight == null ? "" : highlight; this.cutaway = cutaway;
        L = PistonEngineLayout.of(e);
        per = L.per; banks = L.banks; bore = L.bore; spacing = L.spacing; deck = L.deck; wall = L.wall; r = L.r; rodLength = L.rodLength; gasket = L.gasket;
        headHeight = L.headHeight; half = L.half; front = L.front; crankcase = L.crankcase; s = L.s;
        bankAngle = L.bankAngle; bankOffset = L.bankOffset; intakeSide = L.intakeSide; top = L.top; coverThickness = L.coverThickness; frontPlane = L.frontPlane;
        bankShells = new PartMesh[banks];
        for (int k = 0; k < banks; k++) { bankShells[k] = new PartMesh(); }
    }

    /** The engine drawn: highlight a page ("block", "piston", ...; null none); a cutaway shows the internals through see-through casings. */
    public static EngineModel build(PistonEngineDesign e, String highlight, boolean cutaway) {
        PistonEngineModel m = new PistonEngineModel(e, highlight, cutaway);
        m.block(); m.head(); m.crank(); m.timing(); m.intake(); m.exhaust(); m.turbo(); m.lubrication(); m.cooling(); m.accessories();
        if (cutaway) { m.reciprocating(); m.valvetrain(); }
        m.plugsAndCoils();
        m.details();
        if (cutaway) {
            // the casings cut along the crank's vertical plane, each bank along its own
            m.model.section(m.commonShells, Vec3d.ZERO, Vec3d.Z);
            for (int b = 0; b < m.banks; b++) { m.model.section(m.bankShells[b], m.at(b, 0, 0, 0), m.bank(b).rotate(Vec3d.Z)); }
        }
        return m.model;
    }

    // ---------------------------------------------------------------- frames and paint

    private int c(String page, int base) { return page.equals(highlight) ? Shapes.HIGHLIGHT : base; }
    /** A casing's colour (cut in half in a cutaway rather than see-through). */
    private int shell(String page, int base) { return c(page, base); }
    /** Where a casing goes: drawn whole, or (in a cutaway) with the common casings or bank b's, to be cut. */
    private PartMesh casing(int b) { return !cutaway ? fixed : b < 0 ? commonShells : bankShells[b]; }
    /** Where what is bolted on outside goes: in a cutaway it is cut with the casings, so the near half is out of the way. */
    private PartMesh outside() { return casing(-1); }

    /** 0.11: a moving piece inside the block or under a cover (EngineModel.inside); left as it is in a cutaway. */
    private PartMesh hidden(PartMesh mesh) { return cutaway ? mesh : EngineModel.inside(mesh); }

    /** A point in bank b's frame (x along the crank, y up the bores, z across) in the engine's frame. */
    private Vec3d at(int b, double x, double y, double z) { return L.at(b, x, y, z); }
    private Orientation bank(int b) { return L.bank(b); }
    private double cylinderX(int i) { return L.cylinderX(i); }
    private double throwAngle(int i) { return L.throwAngle(i); }

    /** Crank angle past this cylinder's firing TDC (rad, 0..4 pi), for the valve lift laws. */
    private double cycleAngle(int b, int i, double crank) {
        double beta = throwAngle(i) + crank - bankAngle[b];
        int k = per % 2 == 0 && per > 1 ? ((i >= per / 2 ? 1 : 0) + b) % 2 : b % 2;
        double t = beta + 2 * Math.PI * k;
        return t - 4 * Math.PI * Math.floor(t / (4 * Math.PI));
    }

    private double pistonPin(double beta) {
        double sb = r * Math.sin(beta);
        return r * Math.cos(beta) + Math.sqrt(Math.max(0, rodLength * rodLength - sb * sb));
    }

    // ---------------------------------------------------------------- block

    private void block() {
        int metal = Shapes.material(e.block().material());
        boolean air = e.cooling().kind() == CoolingDesign.Kind.AIR;
        // crankcase with its pan rail, ribs over the main bearing bulkheads and engine mounts
        casing(-1).box(new Vec3d(-front, -(r + 0.035), -crankcase), new Vec3d(front, 0.32 * deck, crankcase), shell("block", metal));
        casing(-1).box(new Vec3d(-front - 0.004, -(r + 0.043), -crankcase - 0.006), new Vec3d(front + 0.004, -(r + 0.035), crankcase + 0.006), shell("block", Shapes.darker(metal, 0.1)));
        int mains = Math.max(2, e.block().mainBearings());
        for (int j = 0; j < mains; j++) {
            double x = -half + 0.02 + (2 * half - 0.04) * j / (mains - 1);
            for (int side = -1; side <= 1; side += 2) {
                casing(-1).box(new Vec3d(x - 0.004, -(r + 0.035), side > 0 ? crankcase : -crankcase - 0.006), new Vec3d(x + 0.004, 0.25 * deck, side > 0 ? crankcase + 0.006 : -crankcase),
                        shell("block", metal));
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            Vec3d mount = new Vec3d(0.15 * half, 0.05 * deck, side * (crankcase + 0.02));
            outside().box(mount, new Vec3d(0.035 * s, 0.03 * s, 0.02), Orientation.IDENTITY, shell("block", metal));
            outside().cylinder(mount.add(new Vec3d(0, 0, side * 0.02)), mount.add(new Vec3d(0, 0, side * 0.05)), 0.028 * s, 0.028 * s, 14, RUBBER);
        }
        // the cylinder banks: a jacket with a bulge over each bore (or finned barrels when air cooled), the deck and its gasket
        for (int b = 0; b < banks; b++) {
            double jacket = bore / 2 + wall + 0.002;
            if (!air) {
                casing(b).box(at(b, 0, 0.64 * deck, 0), new Vec3d(half, 0.36 * deck, jacket), bank(b), shell("block", metal));
                for (int i = 0; i < per; i++) {
                    casing(b).cylinder(at(b, cylinderX(i), 0.3 * deck, 0), at(b, cylinderX(i), deck - 0.006, 0), bore / 2 + wall + 0.008, bore / 2 + wall + 0.008, 24, shell("block", metal));
                }
            } else {
                double fins = Math.clamp(positive(e.cooling().finAreaM2(), 0.5) / (e.block().cylinders() * 2 * Math.PI * (bore / 2 + wall) * 0.6 * deck) * 0.004, 0.006, 0.04);
                for (int i = 0; i < per; i++) {
                    int count = 10;
                    double[] along = new double[count * 4 + 2], radius = new double[count * 4 + 2];
                    along[0] = 0; radius[0] = 0;
                    for (int k = 0; k < count; k++) {
                        double t0 = (k + 0.15) / count, t1 = (k + 0.55) / count;
                        along[1 + 4 * k] = t0; radius[1 + 4 * k] = bore / 2 + wall;
                        along[2 + 4 * k] = t0; radius[2 + 4 * k] = bore / 2 + wall + fins;
                        along[3 + 4 * k] = t1; radius[3 + 4 * k] = bore / 2 + wall + fins;
                        along[4 + 4 * k] = t1; radius[4 + 4 * k] = bore / 2 + wall;
                    }
                    along[count * 4 + 1] = 1; radius[count * 4 + 1] = 0;
                    casing(b).lathe(at(b, cylinderX(i), 0.3 * deck, 0), at(b, cylinderX(i), deck, 0), along, radius, null, 20, shell("cooling", metal), true);
                }
            }
            casing(b).box(at(b, 0, deck + gasket / 2, 0), new Vec3d(half - 0.004, gasket / 2, jacket + 0.004), bank(b), shell("head", GASKET));
        }
    }

    // ---------------------------------------------------------------- head and cam cover

    private double valveAngle() { return L.valveAngle(); }
    private double valveLength() { return L.valveLength(); }
    private double camRadius() { return L.camRadius(); }
    private double[] camPosition(int b, int which) { return L.camPosition(b, which); }

    private void head() {
        CylinderHeadDesign head = e.head();
        int metal = Shapes.material(head.material());
        boolean ohv = head.layout() == CylinderHeadDesign.Layout.OHV, air = e.cooling().kind() == CoolingDesign.Kind.AIR;
        double jacket = bore / 2 + wall + 0.006;
        for (int b = 0; b < banks; b++) {
            double y0 = deck + gasket;
            casing(b).box(at(b, 0, y0 + 0.36 * headHeight, 0), new Vec3d(half + 0.004, 0.36 * headHeight, jacket), bank(b), shell("head", metal));
            casing(b).box(at(b, 0, y0 + 0.86 * headHeight, 0), new Vec3d(half, 0.14 * headHeight, jacket - 0.006), bank(b), shell("head", Shapes.machined(metal)));
            if (air) {
                for (int k = 0; k < 4; k++) {
                    double y = y0 + headHeight * (0.15 + 0.2 * k);
                    casing(b).box(at(b, 0, y, 0), new Vec3d(half + 0.004, 0.003, jacket + 0.02), bank(b), shell("cooling", metal));
                }
            }
            // port flanges
            for (int i = 0; i < per; i++) {
                double x = cylinderX(i);
                double runner = positive(e.intake().runnerDiameterM(), 0.04), header = 1.2 * positive(e.valves().exhaustHeadM(), 0.03);
                casing(b).box(at(b, x, y0 + 0.4 * headHeight, intakeSide[b] * (jacket + 0.004)), new Vec3d(runner / 2 + 0.01, runner / 2 + 0.008, 0.004), bank(b), shell("head", metal));
                casing(b).box(at(b, x, y0 + 0.4 * headHeight, -intakeSide[b] * (jacket + 0.004)), new Vec3d(header / 2 + 0.008, header / 2 + 0.008, 0.004), bank(b), shell("head", metal));
            }
            // cam cover: two humps over the cams of a twin-cam head, one over a single cam, a low rocker cover over pushrods
            double coverHalf = jacket - 0.004, h = coverHeight(b);
            double[][] outline;
            if (head.layout() == CylinderHeadDesign.Layout.DOHC) {
                double zc = Math.abs(camPosition(b, 0)[1]);
                List<double[]> pts = new ArrayList<>();
                pts.add(new double[]{-coverHalf, 0});
                pts.add(new double[]{-coverHalf, 0.55 * h});
                for (int k = 0; k <= 8; k++) { double a = Math.PI - Math.PI * k / 8; pts.add(new double[]{-zc + (coverHalf - zc) * Math.cos(a), 0.55 * h + 0.45 * h * Math.sin(a)}); }
                pts.add(new double[]{0, 0.7 * h});
                for (int k = 0; k <= 8; k++) { double a = Math.PI - Math.PI * k / 8; pts.add(new double[]{zc + (coverHalf - zc) * Math.cos(a), 0.55 * h + 0.45 * h * Math.sin(a)}); }
                pts.add(new double[]{coverHalf, 0});
                outline = pts.toArray(new double[0][]);
            } else {
                double[][] rr = Shapes.roundedRect(coverHalf, h / 2, 0.45 * h, 5);
                outline = new double[rr.length][];
                for (int k = 0; k < rr.length; k++) { outline[k] = new double[]{rr[k][0], rr[k][1] + h / 2}; }
            }
            Orientation turn = bank(b);
            Vec3d origin = at(b, -half + 0.006, top, 0);
            casing(b).extrude(outline, origin, turn.rotate(Vec3d.Z), turn.rotate(Vec3d.Y), new Vec3d(2 * (half - 0.006), 0, 0), shell("head", COVER), true);
            // ribs and the oil filler cap
            for (int k = 0; k < 3; k++) {
                double x = -half * 0.6 + half * 0.6 * k;
                casing(b).box(at(b, x, top + (head.layout() == CylinderHeadDesign.Layout.DOHC ? 0.7 * h : h) + 0.002, 0), new Vec3d(0.003, 0.002, coverHalf * 0.5), turn, shell("head", COVER));
            }
            if (b == 0) {
                Vec3d capBase = at(b, half * 0.7, top + (head.layout() == CylinderHeadDesign.Layout.DOHC ? 0.95 * h : h), intakeSide[b] * 0.4 * coverHalf);
                casing(b).cylinder(capBase, capBase.add(turn.rotate(Vec3d.Y).scale(0.012 * s)), 0.018 * s, 0.016 * s, 16, c("lubrication", BLACK));
            }
        }
    }

    // ---------------------------------------------------------------- crankshaft (one piece, turning)

    private void crank() {
        PartMesh crank = new PartMesh();
        int metal = Shapes.material(e.crank().material()), journal = Shapes.machined(metal);
        int colour = c("crank", metal), journalColour = c("crank", journal);
        double mainRadius = positive(e.crank().mainDiameterM(), 0.05) / 2, pinRadius = positive(e.crank().pinDiameterM(), 0.045) / 2;
        double bigWidth = positive(e.rod().bigEndWidthM(), 0.02), pinWidth = Math.max(positive(e.crank().pinWidthM(), 0.022), banks * bigWidth);
        double mainWidth = positive(e.crank().mainWidthM(), 0.02);
        double web = Math.max(0.35 * pinWidth, (spacing / 2 - pinWidth / 2 - mainWidth / 2) * 0.95);
        double cw = Math.clamp(e.crank().counterweightRatio(), 0, 2);
        if (cutaway) {
            int mains = Math.max(2, e.block().mainBearings());
            for (int j = 0; j < mains; j++) {
                double x = per == 1 ? (j == 0 ? -spacing / 2 : spacing / 2) : cylinderX(0) - spacing / 2 + (per * spacing) * j / (mains - 1);
                crank.cylinder(new Vec3d(x - mainWidth / 2, 0, 0), new Vec3d(x + mainWidth / 2, 0, 0), mainRadius, mainRadius, 20, journalColour);
            }
            double[][] outline = webOutline(r, mainRadius * 1.12, pinRadius * 1.35, cw > 0 ? r * (1.0 + 0.45 * cw) + mainRadius * 0.3 : 0, Math.toRadians(62));
            for (int i = 0; i < per; i++) {
                double x = cylinderX(i), theta = throwAngle(i);
                Orientation turn = aboutX(theta);
                Vec3d pin = turn.rotate(new Vec3d(0, r, 0));
                crank.cylinder(new Vec3d(x - pinWidth / 2, pin.y(), pin.z()), new Vec3d(x + pinWidth / 2, pin.y(), pin.z()), pinRadius, pinRadius, 20, journalColour);
                for (int side = -1; side <= 1; side += 2) {
                    double x0 = x + side * pinWidth / 2, x1 = x + side * (pinWidth / 2 + web);
                    crank.extrude(outline, new Vec3d(Math.min(x0, x1), 0, 0), turn.rotate(Vec3d.Z).scale(-1), turn.rotate(Vec3d.Y), new Vec3d(Math.abs(x1 - x0), 0, 0), colour, false);
                }
            }
            // timing sprocket on the snout
            double sprocket = sprocketRadius() / 2;
            crank.extrude(Shapes.gear(sprocket * 0.88, sprocket, 20), new Vec3d(front - 0.008, 0, 0), Vec3d.Z, Vec3d.Y, new Vec3d(0.007, 0, 0), c("valvetrain", Shapes.material(e.crank().material())), false);
        }
        // the snout, the damper pulley (ribbed for the accessory belt), the rear flange and the flywheel with its ring gear
        crank.cylinder(new Vec3d(front - 0.02, 0, 0), new Vec3d(frontPlane + 0.03, 0, 0), mainRadius * 0.7, mainRadius * 0.7, 16, journalColour);
        double pulley = 0.068 * s, x0 = frontPlane + 0.006;
        int ribs = 6;
        double[] along = new double[ribs * 2 + 4], radius = new double[ribs * 2 + 4];
        along[0] = 0; radius[0] = 0; along[1] = 0; radius[1] = pulley;
        for (int k = 0; k < ribs; k++) { along[2 + 2 * k] = (k + 0.5) / ribs * 0.7 + 0.1; radius[2 + 2 * k] = pulley * 0.96; along[3 + 2 * k] = (k + 1.0) / ribs * 0.7 + 0.1; radius[3 + 2 * k] = pulley; }
        along[ribs * 2 + 2] = 1; radius[ribs * 2 + 2] = pulley * 0.55; along[ribs * 2 + 3] = 1; radius[ribs * 2 + 3] = 0;
        crank.lathe(new Vec3d(x0, 0, 0), new Vec3d(x0 + 0.03 * s, 0, 0), along, radius, null, 32, c("crank", BLACK), false);
        double flywheelThickness = Math.clamp(e.crank().flywheelMassKg() / (7850 * Math.PI * 0.14 * 0.14), 0.006, 0.06), rear = -front - 0.012;
        crank.lathe(new Vec3d(rear, 0, 0), new Vec3d(rear - flywheelThickness, 0, 0), new double[]{0, 0, 0.25, 0.25, 1, 1}, new double[]{0, 0.135, 0.135, 0.05, 0.05, 0},
                null, 40, c("crank", Shapes.material(e.crank().material())), true);
        crank.extrude(Shapes.gear(0.136, RING_GEAR, 110), new Vec3d(rear - 0.011, 0, 0), Vec3d.Z, Vec3d.Y, new Vec3d(0.011, 0, 0), c("crank", Shapes.darker(metal, 0.15)), false);
        Shapes.boltCircle(crank, rear - flywheelThickness - 0.004, 0.035, 6, 0.0045, 0.006, c("crank", BOLT));
        model.piece(hidden(crank), angle -> new EngineModel.Placement(Vec3d.ZERO, aboutX(angle)));
    }

    /** Timing sprocket radius on a cam (twice the crank's, so the cams turn at half speed). */
    private double sprocketRadius() { return L.sprocketRadius(); }

    /**
     * A crank web seen along the crank, the crankpin up (+v): a tapered boss round the main and the pin, and opposite
     * the pin a counterweight sector of a radius and half-angle (none when its radius is 0), as a star-shaped outline.
     */
    private static double[][] webOutline(double throwRadius, double mainBoss, double pinBoss, double counterweight, double halfAngle) {
        int n = 72;
        double[][] out = new double[n][];
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n, du = Math.sin(a), dv = Math.cos(a);
            // furthest t along this ray inside the tapered capsule from (0,0) radius mainBoss to (0,throwRadius) radius pinBoss
            double lo = 0, hi = throwRadius + pinBoss + mainBoss;
            for (int it = 0; it < 30; it++) {
                double t = 0.5 * (lo + hi), u = t * du, v = t * dv;
                double f = Math.clamp(v / throwRadius, 0, 1), dist = Math.hypot(u, v - f * throwRadius), allowed = mainBoss + f * (pinBoss - mainBoss);
                if (dist <= allowed) { lo = t; } else { hi = t; }
            }
            double rho = lo;
            if (counterweight > 0) {
                double off = Math.abs(Math.PI - a);   // angle from straight down
                double edge = Math.toRadians(14);
                if (off < halfAngle) { rho = Math.max(rho, counterweight); }
                else if (off < halfAngle + edge) { double f = (off - halfAngle) / edge; rho = Math.max(rho, counterweight * Math.sqrt(1 - f * f) + mainBoss * (1 - Math.sqrt(1 - f * f))); }
            }
            out[k] = new double[]{rho * du, rho * dv};
        }
        return out;
    }

    // ---------------------------------------------------------------- rods and pistons

    private void reciprocating() {
        int rodMetal = Shapes.material(e.rod().material()), pistonMetal = Shapes.material(e.piston().material());
        PartMesh rod = rodMesh(c("rod", rodMetal), c("rod", BOLT));
        PartMesh piston = pistonMesh(pistonMetal);
        double bigWidth = positive(e.rod().bigEndWidthM(), 0.02);
        for (int b = 0; b < banks; b++) {
            for (int i = 0; i < per; i++) {
                final int bb = b, ii = i;
                double x = cylinderX(i), rodX = x + (banks > 1 ? (b == 0 ? -1 : 1) * bigWidth / 2 : 0);
                model.piece(hidden(rod), angle -> {
                    double theta = throwAngle(ii) + angle;
                    Vec3d pin = new Vec3d(rodX, r * Math.cos(theta), r * Math.sin(theta));
                    Vec3d small = at(bb, x, pistonPin(theta - bankAngle[bb]), 0);
                    Vec3d d = small.subtract(pin);
                    return new EngineModel.Placement(pin, aboutX(Math.atan2(d.z(), d.y())));
                });
                model.piece(hidden(piston), angle -> new EngineModel.Placement(at(bb, x, pistonPin(throwAngle(ii) + angle - bankAngle[bb]), 0), bank(bb)));
            }
        }
    }

    /** One rod: big end at the origin, small end at +y by the rod length, eyes along x; I-beam between, cap bolts. */
    private PartMesh rodMesh(int colour, int bolts) {
        PartMesh m = new PartMesh();
        double w = positive(e.rod().bigEndWidthM(), 0.02), bigBore = positive(e.rod().bigEndBoreM(), 0.048), smallBore = positive(e.rod().smallEndBoreM(), 0.022);
        double rb = bigBore / 2 * 1.42, rs = smallBore / 2 * 1.55, l = rodLength;
        m.cylinder(new Vec3d(-w / 2, 0, 0), new Vec3d(w / 2, 0, 0), rb, rb, 24, colour);
        m.cylinder(new Vec3d(-0.4 * w, l, 0), new Vec3d(0.4 * w, l, 0), rs, rs, 20, colour);
        double area = positive(e.rod().beamAreaM2(), 1.5e-4), depth = 0.4 * w;
        double z0 = rb * 0.8, z1 = Math.max(rs * 0.75, Math.min(z0, area / (1.1 * depth) / 2));
        double flange = 0.25;
        // web and two flanges, tapering from the big end to the small end
        m.hexahedron(beam(0, l, 0.35 * depth, z0 * (1 - flange), z1 * (1 - flange), -z0 * (1 - flange), -z1 * (1 - flange)), colour);
        for (int side = -1; side <= 1; side += 2) {
            m.hexahedron(beam(0, l, depth, side * z0, side * z1, side * z0 * (1 - flange), side * z1 * (1 - flange)), colour);
        }
        for (int side = -1; side <= 1; side += 2) {
            double z = side * (bigBore / 2 + 0.0045);
            m.cylinder(new Vec3d(0, -rb * 0.95, z), new Vec3d(0, rb * 0.55, z), 0.0035, 0.0035, 8, bolts);
            m.cylinder(new Vec3d(0, -rb * 1.02, z), new Vec3d(0, -rb * 0.9, z), 0.006, 0.006, 6, bolts);
        }
        return m;
    }

    /** A beam's corners from y0 to y1 along y, a half thickness in x, between z = zA and z = zB at each end. */
    private static Vec3d[] beam(double y0, double y1, double halfX, double zA0, double zA1, double zB0, double zB1) {
        return new Vec3d[]{new Vec3d(-halfX, y0, zB0), new Vec3d(halfX, y0, zB0), new Vec3d(halfX, y0, zA0), new Vec3d(-halfX, y0, zA0),
                new Vec3d(-halfX, y1, zB1), new Vec3d(halfX, y1, zB1), new Vec3d(halfX, y1, zA1), new Vec3d(-halfX, y1, zA1)};
    }

    /** One piston: pin at the origin, crown up +y; ring grooves, a domed or dished crown, the pin through. */
    private PartMesh pistonMesh(int metal) {
        PartMesh m = new PartMesh();
        double R = positive(e.piston().diameterM(), bore) / 2, ch = positive(e.piston().compressionHeightM(), 0.03), skirt = positive(e.piston().skirtLengthM(), 0.035);
        double bottom = -0.7 * skirt, area = Math.PI * R * R, crownVolume = e.piston().crownVolumeM3();
        double cap = Math.clamp(2 * Math.abs(crownVolume) / area, 0, 0.6 * R) * Math.signum(crownVolume);
        List<double[]> profile = new ArrayList<>();   // (y, radius, colour)
        int body = c("piston", metal), groove = c("piston", GROOVE), crown = c("piston", CARBON);
        profile.add(new double[]{bottom, 0, body});
        profile.add(new double[]{bottom, R - 0.0008, body});
        double belt = ch - 0.017 * s;
        profile.add(new double[]{belt, R - 0.0003, body});
        double[] grooves = {0.0145, 0.0095, 0.005};
        for (double g : grooves) {
            double y = ch - g * s;
            profile.add(new double[]{y - 0.0009, R - 0.0003, body});
            profile.add(new double[]{y - 0.0009, R - 0.0022, groove});
            profile.add(new double[]{y + 0.0009, R - 0.0022, groove});
            profile.add(new double[]{y + 0.0009, R - 0.0003, body});
        }
        profile.add(new double[]{ch - 0.0008, R - 0.0003, body});
        profile.add(new double[]{ch, R - 0.0012, crown});
        for (int k = 1; k <= 8; k++) {
            double rr = (R - 0.0012) * (1 - k / 8.0), f = 1 - (rr / R) * (rr / R);
            profile.add(new double[]{ch + cap * f, rr, crown});
        }
        double y0 = bottom, y1 = ch + Math.max(0, cap) + 1e-6;
        double lowest = Math.min(y0, ch + Math.min(0, cap));
        y0 = Math.min(y0, lowest);
        double[] along = new double[profile.size()], radius = new double[profile.size()];
        int[] colours = new int[profile.size()];
        for (int k = 0; k < profile.size(); k++) {
            along[k] = (profile.get(k)[0] - y0) / (y1 - y0); radius[k] = profile.get(k)[1]; colours[k] = (int) (long) profile.get(k)[2];
        }
        m.lathe(new Vec3d(0, y0, 0), new Vec3d(0, y1, 0), along, radius, colours, 28, body, false);
        double pin = positive(e.piston().pinDiameterM(), 0.022) / 2;
        m.cylinder(new Vec3d(-0.8 * R, 0, 0), new Vec3d(0.8 * R, 0, 0), pin, pin, 12, c("piston", Shapes.material(e.rod().material())));
        return m;
    }

    // ---------------------------------------------------------------- valve train

    /**
     * One valve: its bank, cylinder, intake or exhaust, the side of the head it leans to, its position along the
     * crank, its seat (bank frame y, z) and its stem's tilt from the bore axis (rad, about x in the bank frame).
     * Twin-cam and single-cam heads put the intake valves on one side of the bore and the exhausts on the other
     * (a pent roof); a pushrod head has them in a row along the crank, leaning towards the pushrods.
     */
    private List<Valve> valves(int b) { return L.valves(b); }
    private double tipY(Valve v) { return L.tipY(v); }
    private double tipZ(Valve v) { return L.tipZ(v); }
    private double camBase() { return L.camBase(); }
    private Vec3d camPoint(Cam c, double x) { return L.camPoint(c, x); }
    private List<Cam> cams() { return L.cams(); }
    private Cam camOf(List<Cam> cams, Valve v) { return L.camOf(cams, v); }
    private Rocker rocker(Valve v, Cam cam) { return L.rocker(v, cam); }

    /** The rocker's turn (rad about x) that moves its valve end a lift down the stem. */
    private static double rockerAngle(Rocker k, double tilt, double lift) {
        double vy = k.valveEnd()[0] - k.pivot()[0], vz = k.valveEnd()[1] - k.pivot()[1];
        double along = -vz * Math.cos(tilt) + vy * Math.sin(tilt);   // the valve end's motion per radian, resolved along the stem
        if (Math.abs(along) < 1e-6) { return 0; }
        return -lift / along;
    }

    private double coverHeight(int b) { return L.coverHeight(b); }

    private void valvetrain() {
        CylinderHeadDesign.Layout layout = e.head().layout();
        boolean rockers = layout != CylinderHeadDesign.Layout.DOHC, ohv = layout == CylinderHeadDesign.Layout.OHV;
        ValveLift[] laws = new ValveLift[2];
        try { laws[0] = e.camshaft().intakeLift(0); laws[1] = e.camshaft().exhaustLift(0); }
        catch (IllegalArgumentException ex) { laws[0] = null; laws[1] = null; }
        List<Cam> cams = cams();
        double len = valveLength(), springLength = 0.042 * s, retainer = len - 0.008 * s, springBase = retainer - springLength;
        int camMetal = Shapes.material(e.camshaft().material());
        int follower = layout == CylinderHeadDesign.Layout.DOHC ? (e.valvetrain().mechanism() == ValvetrainDesign.Mechanism.FINGER_FOLLOWER ? 2 : 1) : 0;
        for (int b = 0; b < banks; b++) {
            for (Valve v : valves(b)) {
                final int bb = b, ii = v.cylinder();
                ValveLift law = laws[v.intake() ? 0 : 1];
                double headD = positive(v.intake() ? e.valves().intakeHeadM() : e.valves().exhaustHeadM(), 0.03);
                double stem = positive(v.intake() ? e.valves().intakeStemM() : e.valves().exhaustStemM(), 0.006) / 2;
                int valveColour = c("valves", v.intake() ? Shapes.material(e.valves().intakeMaterial()) : Shapes.mix(Shapes.material(e.valves().exhaustMaterial()), 0xff8a5a3c, 0.45));
                Orientation stemTurn = aboutX(bankAngle[b] + v.tilt());
                Vec3d stemDir = stemTurn.rotate(Vec3d.Y);
                Vec3d seat = at(b, v.x(), v.seatY(), v.seatZ());
                model.piece(hidden(valveMesh(headD / 2, stem, len, valveColour, follower)), angle -> {
                    double lift = law == null ? 0 : law.lift(cycleAngle(bb, ii, angle));
                    return new EngineModel.Placement(seat.subtract(stemDir.scale(lift)), stemTurn);
                });
                model.piece(hidden(springMesh(stem, springLength)), angle -> {
                    double lift = law == null ? 0 : law.lift(cycleAngle(bb, ii, angle));
                    return new EngineModel.Placement(seat.add(stemDir.scale(springBase)), stemTurn, Math.max(0.3, (springLength - lift) / springLength));
                });
                Cam cam = camOf(cams, v);
                double ratio = 1;
                double toFollower;   // the lobe's nose direction at full lift, about x in the engine's frame
                if (!rockers) {
                    toFollower = bankAngle[b] + Math.atan2(-Math.sin(v.tilt()), -Math.cos(v.tilt()));
                } else {
                    Rocker k = rocker(v, cam);
                    ratio = k.ratio();
                    double tilt = v.tilt();
                    Vec3d pivot = at(b, v.x(), k.pivot()[0], k.pivot()[1]);
                    double rockerRatio = ratio;
                    model.piece(hidden(rockerMesh(k)), angle -> {
                        double lift = law == null ? 0 : law.lift(cycleAngle(bb, ii, angle));
                        return new EngineModel.Placement(pivot, aboutX(bankAngle[bb] + rockerAngle(k, tilt, lift)));
                    });
                    if (!ohv) {
                        toFollower = bankAngle[b] + Math.atan2(k.inputEnd()[1] - cam.z(), k.inputEnd()[0] - cam.y());
                    } else {
                        // lifter and pushrod from the cam in the block up to the rocker
                        Vec3d input = at(b, v.x(), k.inputEnd()[0], k.inputEnd()[1]), centre = camPoint(cam, v.x());
                        Vec3d u = new Vec3d(0, input.y() - centre.y(), input.z() - centre.z()).normalized();
                        double lifterLength = 0.05 * s;
                        Vec3d lifterBottom = centre.add(u.scale(camBase() + 0.0005)), lifterTop = lifterBottom.add(u.scale(lifterLength));
                        Vec3d rod = input.subtract(lifterTop);
                        double rodLength = rod.length();
                        Orientation rodTurn = Shapes.yTo(rod), lifterTurn = Shapes.yTo(u);
                        int pushColour = c("valvetrain", Shapes.machined(0xff858b93));
                        PartMesh pushrod = new PartMesh();
                        pushrod.cylinder(Vec3d.ZERO, new Vec3d(0, rodLength, 0), 0.0035 * s, 0.0035 * s, 8, pushColour);
                        pushrod.sphere(0.0045 * s, pushColour);
                        PartMesh lifter = new PartMesh().cylinder(Vec3d.ZERO, new Vec3d(0, lifterLength, 0), 0.0105 * s, 0.0105 * s, 14, pushColour);
                        if (e.valvetrain().rollerFollowers()) {
                            lifter.cylinder(new Vec3d(-0.006 * s, 0.004 * s, 0), new Vec3d(0.006 * s, 0.004 * s, 0), 0.007 * s, 0.007 * s, 12, c("valvetrain", 0xff6d737b));
                        }
                        double r0 = ratio;
                        model.piece(hidden(lifter), angle -> {
                            double lift = law == null ? 0 : law.lift(cycleAngle(bb, ii, angle)) / r0;
                            return new EngineModel.Placement(lifterBottom.add(u.scale(lift)), lifterTurn);
                        });
                        model.piece(hidden(pushrod), angle -> {
                            double lift = law == null ? 0 : law.lift(cycleAngle(bb, ii, angle)) / r0;
                            return new EngineModel.Placement(lifterTop.add(u.scale(lift)), rodTurn);
                        });
                        toFollower = Math.atan2(u.z(), u.y());
                    }
                    // what the rocker turns on: a shaft along the head over a single cam, a stud and pedestal on a pushrod head
                    if (ohv) {
                        fixed.cylinder(at(b, v.x(), top - 0.004, k.pivot()[1]), pivot, 0.0045 * s, 0.0045 * s, 8, c("valvetrain", BOLT));
                    }
                }
                // the lobe, its outline the lift law (divided by a rocker's ratio)
                ValveLift lobeLaw = law == null ? null : new ValveLift(law.centreRad(), law.durationRad(), law.maxLiftM() / ratio, law.fullness());
                double centreAngle = law == null ? 0 : law.centreRad(), nose = toFollower;
                Vec3d centre = camPoint(cam, v.x());
                PartMesh lobe = new PartMesh().extrude(lobeOutline(lobeLaw, camBase()), new Vec3d(-0.007 * s, 0, 0), Vec3d.Z.scale(-1), Vec3d.Y, new Vec3d(0.014 * s, 0, 0),
                        c("camshaft", Shapes.machined(camMetal)), true);
                model.piece(hidden(lobe), angle -> new EngineModel.Placement(centre, aboutX(nose + (cycleAngle(bb, ii, angle) - centreAngle) / 2)));
            }
            if (layout == CylinderHeadDesign.Layout.SOHC) {
                // rocker shafts on either side of the cam
                List<Valve> vs = valves(b);
                for (int side = -1; side <= 1; side += 2) {
                    for (Valve v : vs) {
                        if (v.side() != side) { continue; }
                        double[] p = rocker(v, cams.get(b)).pivot();
                        fixed.cylinder(at(b, -half + 0.01, p[0], p[1]), at(b, half - 0.01, p[0], p[1]), 0.0075 * s, 0.0075 * s, 12, c("valvetrain", Shapes.machined(0xff858b93)));
                        break;
                    }
                }
            }
        }
        camshafts(cams, camMetal);
    }

    /** A valve: face at the origin, stem up +y to its tip; margin, seat face, tulip fillet, keeper groove; its retainer, and a bucket (1) or finger pad (2) on a twin-cam head. */
    private PartMesh valveMesh(double R, double stem, double length, int colour, int follower) {
        double k = R / 0.0175;
        double[][] p = {{0, 0}, {0, R * 0.94}, {0.0012 * k, R}, {0.0028 * k, R}, {0.006 * k, R * 0.84}, {0.013 * k, stem * 2.1}, {0.022 * k, stem * 1.15},
                {0.03 * k, stem}, {length - 0.0045, stem}, {length - 0.004, stem * 0.82}, {length - 0.0022, stem * 0.82}, {length - 0.0017, stem}, {length, stem * 0.9}, {length, 0}};
        double[] along = new double[p.length], radius = new double[p.length];
        for (int i = 0; i < p.length; i++) { along[i] = p[i][0] / length; radius[i] = p[i][1]; }
        PartMesh m = new PartMesh();
        m.lathe(Vec3d.ZERO, new Vec3d(0, length, 0), along, radius, null, 14, colour, false);
        double springR = Math.max(stem * 2.6, 0.0105 * s), retainer = length - 0.008 * s;
        m.cylinder(new Vec3d(0, retainer - 0.003 * s, 0), new Vec3d(0, retainer + 0.0015 * s, 0), stem * 1.3, springR + 0.002, 14, c("valvetrain", Shapes.machined(Shapes.material(e.valves().intakeMaterial()))));
        int followerColour = c("valvetrain", Shapes.machined(0xff8a9097));
        if (follower == 1) {
            double bucket = 0.0145 * s;
            m.lathe(new Vec3d(0, length - 0.004 * s, 0), new Vec3d(0, length + 0.016 * s, 0), new double[]{0, 0, 1, 1}, new double[]{0, bucket, bucket, 0}, null, 18, followerColour, true);
        } else if (follower == 2) {
            m.box(new Vec3d(0, length + 0.003 * s, 0.01 * s), new Vec3d(0.006 * s, 0.0035 * s, 0.018 * s), Orientation.IDENTITY, followerColour);
        }
        return m;
    }

    /** A rocker arm about its pivot (the origin, bank frame): an arm to the valve with its pad, an arm to the cam or pushrod with its adjuster. */
    private PartMesh rockerMesh(Rocker k) {
        PartMesh m = new PartMesh();
        int colour = c("valvetrain", 0xff7d848c);
        for (double[] end : new double[][]{k.valveEnd(), k.inputEnd()}) {
            double dy = end[0] - k.pivot()[0], dz = end[1] - k.pivot()[1], l = Math.hypot(dy, dz);
            Vec3d mid = new Vec3d(0, dy / 2, dz / 2);
            m.box(mid, new Vec3d(0.0055 * s, l / 2 + 0.003 * s, 0.0042 * s), aboutX(Math.atan2(dz, dy)), colour);
        }
        m.cylinder(new Vec3d(-0.008 * s, 0, 0), new Vec3d(0.008 * s, 0, 0), 0.0085 * s, 0.0085 * s, 14, colour);
        Vec3d pad = new Vec3d(0, k.valveEnd()[0] - k.pivot()[0], k.valveEnd()[1] - k.pivot()[1]);
        m.cylinder(pad.add(new Vec3d(-0.005 * s, 0, 0)), pad.add(new Vec3d(0.005 * s, 0, 0)), 0.0042 * s, 0.0042 * s, 10, colour);
        Vec3d in = new Vec3d(0, k.inputEnd()[0] - k.pivot()[0], k.inputEnd()[1] - k.pivot()[1]);
        m.cylinder(in.add(new Vec3d(0, 0.007 * s, 0)), in.add(new Vec3d(0, -0.002 * s, 0)), 0.0032 * s, 0.0032 * s, 8, c("valvetrain", BOLT));
        return m;
    }

    /** A valve spring: a coil up +y from its seat (the piece is stretched along y as the valve lifts). */
    private PartMesh springMesh(double stem, double length) {
        double coil = Math.max(stem * 2.6, 0.0105 * s), wire = 0.0019 * s;
        PartMesh m = new PartMesh();
        m.tube(PartMesh.helix(new Vec3d(0, wire, 0), Vec3d.Y, coil, length - 2 * wire, 6.5, 10), wire, null, c("valvetrain", SPRING), null, 5, false);
        m.cylinder(Vec3d.ZERO, new Vec3d(0, 0.0012, 0), coil - wire, coil + wire * 1.6, 14, c("valvetrain", Shapes.machined(SPRING)));
        return m;
    }

    /** The camshafts with their bearing journals, turning at half the crank's speed (the lobes are separate pieces). */
    private void camshafts(List<Cam> cams, int metal) {
        double radius = camRadius();
        int bearings = Math.max(2, e.camshaft().bearingsPerShaft());
        for (Cam cam : cams) {
            double x0 = -half + 0.01, x1 = front - 0.012 - (cam.bank() < 0 ? 0 : bankOffset[cam.bank()]);
            double[] along = new double[bearings * 4 + 2], rad = new double[bearings * 4 + 2];
            along[0] = 0; rad[0] = 0;
            for (int j = 0; j < bearings; j++) {
                double mid = (j + 0.5) / bearings, w = 0.02;
                along[1 + 4 * j] = mid - w; rad[1 + 4 * j] = radius;
                along[2 + 4 * j] = mid - w; rad[2 + 4 * j] = radius * 1.25;
                along[3 + 4 * j] = mid + w; rad[3 + 4 * j] = radius * 1.25;
                along[4 + 4 * j] = mid + w; rad[4 + 4 * j] = radius;
            }
            along[bearings * 4 + 1] = 1; rad[bearings * 4 + 1] = 0;
            PartMesh shaft = new PartMesh().lathe(Vec3d.ZERO, new Vec3d(x1 - x0, 0, 0), along, rad, null, 14, c("camshaft", metal), true);
            Vec3d start = camPoint(cam, x0);
            model.piece(hidden(shaft), angle -> new EngineModel.Placement(start, aboutX(angle / 2)));
        }
    }

    /** A cam lobe seen along the shaft, the nose towards +v: base circle plus the lift law at twice the cam angle. */
    private static double[][] lobeOutline(ValveLift law, double base) {
        int n = 48;
        double[][] out = new double[n][];
        for (int k = 0; k < n; k++) {
            double psi = 2 * Math.PI * k / n, rho = base + (law == null ? 0 : law.lift(law.centreRad() - 2 * psi));   // lift() wraps the cycle itself
            out[k] = new double[]{rho * Math.sin(psi), rho * Math.cos(psi)};
        }
        return out;
    }

    // ---------------------------------------------------------------- timing drive

    private void timing() {
        ValvetrainDesign.Drive drive = e.valvetrain().drive();
        double crankSprocket = sprocketRadius() / 2, camSprocket = sprocketRadius();
        List<double[]> pulleys = new ArrayList<>();
        pulleys.add(new double[]{0, 0, crankSprocket});
        int sprocketColour = c("valvetrain", Shapes.material(e.crank().material()));
        for (Cam cam : cams()) {
            Vec3d centre = camPoint(cam, front - (cam.bank() < 0 ? 0 : bankOffset[cam.bank()]) - 0.004);
            pulleys.add(new double[]{centre.y(), centre.z(), camSprocket});
            if (cutaway) {
                PartMesh sprocket = new PartMesh().extrude(Shapes.gear(camSprocket * 0.9, camSprocket, 40), Vec3d.ZERO, Vec3d.Z, Vec3d.Y, new Vec3d(-0.008, 0, 0), sprocketColour, false);
                sprocket.cylinder(new Vec3d(-0.008, 0, 0), new Vec3d(0.001, 0, 0), camSprocket * 0.35, camSprocket * 0.35, 14, sprocketColour);
                model.piece(hidden(sprocket), angle -> new EngineModel.Placement(centre, aboutX(angle / 2)));
            }
        }
        // the chain or belt (inside the cover: drawn in a cutaway), and the cover itself
        if (cutaway && drive != ValvetrainDesign.Drive.GEAR) {
            List<Vec3d> path = Shapes.belt(front - 0.008, pulleys.toArray(new double[0][]), 24);
            if (drive == ValvetrainDesign.Drive.CHAIN) { fixed.tube(path, 0.0035, null, c("valvetrain", CHAIN), null, 5, true); }
            else { Shapes.flatBelt(fixed, path, 0.02, 0.003, c("valvetrain", RUBBER)); }
        }
        List<double[]> covered = new ArrayList<>();
        for (double[] p : pulleys) { covered.add(new double[]{p[0], p[1], p[2] + 0.014}); }
        List<Vec3d> hull = Shapes.belt(0, covered.toArray(new double[0][]), 16);
        double[][] outline = new double[hull.size()][];
        for (int i = 0; i < hull.size(); i++) { outline[i] = new double[]{hull.get(i).z(), hull.get(i).y()}; }
        casing(-1).extrude(outline, new Vec3d(front, 0, 0), Vec3d.Z, Vec3d.Y, new Vec3d(coverThickness, 0, 0), shell("valvetrain", drive == ValvetrainDesign.Drive.BELT ? PLASTIC : Shapes.material(e.block().material())), false);
    }

    // ---------------------------------------------------------------- intake and fuel

    private void intake() {
        int runnerColour = c("intake", Shapes.material(e.block().material()) == 0xffb9bdc2 ? 0xffa9adb2 : PLASTIC);
        double runnerRadius = L.runnerRadius(), throttleRadius = L.throttleRadius();
        boolean itb = L.individualThrottles(), carburettor = L.carburettor();
        boolean direct = e.fuelSystem().delivery() == FuelSystemDesign.Delivery.DIRECT_INJECTION;
        double jacket = bore / 2 + wall + 0.01;
        List<Vec3d> injectorTops = new ArrayList<>();
        for (int b = 0; b < banks; b++) {
            int side = intakeSide[b];
            Vec3d out = L.out(b, side), up = L.up(b);
            // 0.14: an inline engine's runners curve out of the ports and down beside the head; a vee's sweep into the valley
            for (int i = 0; i < per; i++) {
                List<Vec3d> path = L.runner(b, i);
                outside().tube(path, runnerRadius, null, runnerColour, null, 14, false);
                Vec3d end = path.get(path.size() - 1);
                if (itb) {
                    Vec3d dir = end.subtract(path.get(path.size() - 2)).normalized();
                    outside().cylinder(end, end.add(dir.scale(0.05)), throttleRadius * 1.15, throttleRadius * 1.15, 16, c("intake", 0xffa9adb2));
                    outside().lathe(end.add(dir.scale(0.05)), end.add(dir.scale(0.11)), new double[]{0, 0.4, 0.8, 1, 1}, new double[]{throttleRadius, throttleRadius * 1.15, throttleRadius * 1.5, throttleRadius * 1.85, 0},
                            null, 18, c("intake", Shapes.machined(0xffa9adb2)), false);
                }
                // injector: on the runner by the port, or into the chamber beside the intake port
                if (!carburettor) {
                    Vec3d tip = direct ? at(b, cylinderX(i), deck + gasket + 0.18 * headHeight, side * (jacket - 0.004)) : path.get(2);
                    Vec3d topPoint = direct ? at(b, cylinderX(i), deck + gasket + 0.5 * headHeight, side * (jacket + 0.045))
                            : tip.add(up.scale(0.04)).add(out.scale(-0.01));
                    Vec3d d = topPoint.subtract(tip).normalized();
                    outside().lathe(tip, topPoint, new double[]{0, 0, 0.15, 0.2, 0.6, 0.65, 1, 1}, new double[]{0, 0.0035, 0.0045, 0.0065, 0.0065, 0.0075, 0.0075, 0},
                            null, 10, c("injector", INJECTOR), true);
                    outside().box(topPoint.add(d.scale(0.006)), new Vec3d(0.006, 0.004, 0.006), Shapes.yTo(d), c("injector", BLACK));
                    injectorTops.add(topPoint);
                }
            }
            if (!injectorTops.isEmpty()) {
                Vec3d first = injectorTops.get(b * per), last = injectorTops.get(b * per + per - 1);
                Vec3d lift = up.scale(0.004);
                outside().cylinder(first.add(new Vec3d(-0.025, 0, 0)).add(lift), last.add(new Vec3d(0.02, 0, 0)).add(lift), 0.0085, 0.0085, 10, c("fuelSystem", Shapes.machined(0xffa9adb2)));
                Vec3d feed = last.add(new Vec3d(0.02, 0, 0)).add(lift);
                outside().tube(PartMesh.bezier(feed, feed.add(new Vec3d(0.05, 0, 0)), feed.add(new Vec3d(0.07, -0.05, 0)), feed.add(new Vec3d(0.09, -0.12, 0)), 8), 0.004, 8, c("fuelSystem", RUBBER));
            }
        }
        if (itb) { return; }
        // plenum: a rounded tube along the engine where the runners end, its throttle body, the hose to the air box
        PistonEngineLayout.Plenum plenum = L.plenum();
        Vec3d centre = plenum.centre(), p0 = plenum.rear(), p1 = plenum.front();
        double radius = plenum.radius();
        outside().lathe(p0, p1, new double[]{0, 0.01, 0.04, 0.96, 0.99, 1}, new double[]{0, radius * 0.75, radius, radius, radius * 0.75, 0}, null, 24, runnerColour, false);
        if (carburettor) {
            Vec3d topOf = centre.add(new Vec3d(0, radius, 0));
            outside().box(topOf.add(new Vec3d(0, 0.035, 0)), new Vec3d(0.04, 0.035, 0.035), Orientation.IDENTITY, c("fuelSystem", Shapes.machined(0xffa9adb2)));
            outside().cylinder(topOf.add(new Vec3d(0, 0.07, 0)), topOf.add(new Vec3d(0, 0.11, 0)), 0.12 * s, 0.12 * s, 28, c("intake", BLACK));
            return;
        }
        Vec3d throttle0 = p1, throttle1 = p1.add(new Vec3d(0.05, 0, 0));
        outside().lathe(throttle0, throttle1, new double[]{0, 0, 0.12, 0.12, 0.88, 0.88, 1, 1}, new double[]{0, throttleRadius * 1.45, throttleRadius * 1.45, throttleRadius * 1.12,
                throttleRadius * 1.12, throttleRadius * 1.45, throttleRadius * 1.45, 0}, null, 20, c("intake", 0xffa9adb2), true);
        outside().cylinder(throttle0.add(new Vec3d(0.025, 0, throttleRadius * 1.12)), throttle0.add(new Vec3d(0.025, 0, throttleRadius * 1.12 + 0.012)), 0.018, 0.018, 14, c("intake", Shapes.machined(0xffa9adb2)));
        // 0.14: the hose to the air box (beside the block, or at a vee's front corner), the box with its lid, its snorkel forward
        outside().tube(L.airHose(), throttleRadius * 1.1, 16, c("intake", RUBBER));
        dev.mcmod.hdfv.look.Envelope.Box box = L.airBox();
        Vec3d lo = box.min(), hi = box.max();
        double seam = hi.y() - 0.28 * (hi.y() - lo.y());
        outside().box(lo, new Vec3d(hi.x(), seam, hi.z()), c("intake", PLASTIC));
        outside().box(new Vec3d(lo.x() + 0.004, seam, lo.z() + 0.004), new Vec3d(hi.x() - 0.004, hi.y(), hi.z() - 0.004), c("intake", BLACK));
        for (int k = 0; k < 2; k++) {
            double x = lo.x() + (0.25 + 0.5 * k) * (hi.x() - lo.x());
            outside().box(new Vec3d(x - 0.008, seam - 0.012, lo.z() - 0.003), new Vec3d(x + 0.008, seam + 0.012, lo.z()), c("intake", BLACK));
            outside().box(new Vec3d(x - 0.008, seam - 0.012, hi.z()), new Vec3d(x + 0.008, seam + 0.012, hi.z() + 0.003), c("intake", BLACK));
        }
        List<Vec3d> snorkel = L.snorkel();
        outside().cylinder(snorkel.get(0), snorkel.get(1), throttleRadius * 1.1, throttleRadius * 1.25, 16, c("intake", PLASTIC));
    }

    // ---------------------------------------------------------------- exhaust

    private void exhaust() {
        double header = L.headerRadius();
        outside().hot(true);   // 0.11: headers, collectors and pipes glow under load (PartRenderer.glow)
        for (int b = 0; b < banks; b++) {
            Vec3d out = L.out(b, -intakeSide[b]);
            for (int i = 0; i < per; i++) {
                List<Vec3d> path = L.header(b, i);
                int[] colours = new int[path.size()];
                for (int k = 0; k < colours.length; k++) { colours[k] = c("exhaust", Shapes.heat(0.6 * k / (colours.length - 1.0))); }
                outside().tube(path, header, null, colours[0], colours, 12, false);
            }
            // collector cone, then (0.14) the down pipe straight down past the sump rail: it ran aft past the flywheel, where a
            // gearbox's bell housing is
            double collector = L.collectorRadius();
            Vec3d c0 = L.merge(b), c1 = L.collectorEnd(b);
            outside().cylinder(c0, c1, collector * 1.15, collector * 0.8, 16, c("exhaust", Shapes.heat(0.65)));
            if (b == 0) { collectorEnd = c1; collectorOut = out.normalized(); }
            List<Vec3d> pipe = L.downPipe(b);
            int[] colours = new int[pipe.size()];
            for (int k = 0; k < colours.length; k++) { colours[k] = c("exhaust", Shapes.heat(0.7 + 0.3 * k / (colours.length - 1.0))); }
            outside().tube(pipe, collector * 0.8, null, colours[0], colours, 16, false);
            model.exhausts.add(new Vec3d[]{pipe.get(pipe.size() - 1), new Vec3d(0, -1, 0)});   // where the smoke comes out
        }
        outside().hot(false);
    }

    // ---------------------------------------------------------------- turbocharger

    /** A turbocharger on the first bank's collector: the turbine's hot scroll, the bearing housing, the compressor's scroll and inlet, and an intercooler in front. */
    private void turbo() {
        var t = e.turbo();
        if (!t.fitted()) { return; }
        double tip = Math.clamp(t.compressorTipM(), 0.02, 0.2), wheel = tip / 2;
        Vec3d axis = collectorOut, t0 = collectorEnd.add(axis.scale(0.01)), t1 = t0.add(axis.scale(1.1 * tip));
        // turbine scroll: a fat disc round the wheel, glowing like the manifold
        outside().hot(true).lathe(t0, t1, new double[]{0, 0, 0.15, 0.85, 1, 1}, new double[]{0, 2.1 * wheel, 2.5 * wheel, 2.5 * wheel, 2.1 * wheel, 0}, null, 24, c("turbo", Shapes.heat(0.7)), false)
                .hot(false);
        // bearing housing, then the compressor scroll (cast aluminium) and its inlet
        Vec3d h1 = t1.add(axis.scale(0.8 * tip));
        turboBearing = t1.add(axis.scale(0.4 * tip)).add(new Vec3d(0, wheel, 0));
        outside().cylinder(t1, h1, 1.0 * wheel, 1.0 * wheel, 16, c("turbo", Shapes.machined(0xff8d9196)));
        Vec3d c1 = h1.add(axis.scale(1.0 * tip));
        outside().lathe(h1, c1, new double[]{0, 0, 0.2, 0.8, 1, 1}, new double[]{0, 2.4 * wheel, 2.8 * wheel, 2.8 * wheel, 1.6 * wheel, 1.2 * wheel}, null, 24,
                c("turbo", Shapes.machined(0xffb8bcc2)), false);
        outside().cylinder(c1, c1.add(axis.scale(0.06)), 1.25 * wheel, 1.25 * wheel, 18, c("turbo", RUBBER));
        // charge pipe forward and the intercooler across the front
        if (t.intercoolerEffectiveness() > 0) {
            double size = Math.sqrt(Math.max(t.intercoolerEffectiveness(), 0.1)) * 0.3 * s;
            Vec3d cooler = new Vec3d(front + 0.12, 0.25 * deck, 0);
            outside().box(cooler, new Vec3d(0.03, 0.6 * size, 1.4 * size), Orientation.IDENTITY, c("turbo", Shapes.machined(0xff9aa0a6)));
            Vec3d outlet = h1.add(axis.scale(0.5 * tip)).add(new Vec3d(0, 2.6 * wheel, 0));
            outside().tube(PartMesh.bezier(outlet, outlet.add(new Vec3d(0.05, 0.03, 0)), cooler.add(new Vec3d(-0.08, 0, 1.2 * size * Math.signum(axis.z() == 0 ? 1 : axis.z()))),
                    cooler.add(new Vec3d(-0.015, 0, 1.2 * size * Math.signum(axis.z() == 0 ? 1 : axis.z()))), 12), 0.9 * wheel, 14, c("turbo", RUBBER));
        }
    }

    // ---------------------------------------------------------------- oil

    private void lubrication() {
        LubricationDesign oil = e.lubrication();
        int panColour = shell("lubrication", Shapes.material(e.block().material()));
        double railY = L.railY(), length = 2 * front - 0.02, volume = positive(oil.oilVolumeM3(), 0.004);
        boolean dry = L.drySump();
        double shallow = L.sumpShallow(), deep = L.sumpDeep();
        // a pan tapering inwards, with a deeper well at the back
        double w = crankcase + 0.004;
        casing(-1).hexahedron(new Vec3d[]{new Vec3d(-front + 0.01, railY, -w), new Vec3d(front - 0.01, railY, -w), new Vec3d(front - 0.01, railY, w), new Vec3d(-front + 0.01, railY, w),
                new Vec3d(-front + 0.01, railY - shallow, -0.85 * w), new Vec3d(front - 0.02, railY - shallow, -0.85 * w), new Vec3d(front - 0.02, railY - shallow, 0.85 * w),
                new Vec3d(-front + 0.01, railY - shallow, 0.85 * w)}, panColour);
        if (!dry) {
            double x1 = -front + 0.01 + 0.45 * length;
            casing(-1).hexahedron(new Vec3d[]{new Vec3d(-front + 0.01, railY - shallow, -0.85 * w), new Vec3d(x1, railY - shallow, -0.85 * w), new Vec3d(x1, railY - shallow, 0.85 * w),
                    new Vec3d(-front + 0.01, railY - shallow, 0.85 * w), new Vec3d(-front + 0.02, railY - deep, -0.7 * w), new Vec3d(x1 - 0.03, railY - deep, -0.7 * w),
                    new Vec3d(x1 - 0.03, railY - deep, 0.7 * w), new Vec3d(-front + 0.02, railY - deep, 0.7 * w)}, panColour);
            outside().cylinder(new Vec3d(-front + 0.06, railY - deep, 0), new Vec3d(-front + 0.06, railY - deep - 0.008, 0), 0.008, 0.008, 6, c("lubrication", BOLT));
        } else {
            double tank = Math.clamp(volume / (Math.PI * 0.06 * 0.06), 0.08, 0.6);
            Vec3d base = new Vec3d(-front * 0.4, -r, -(crankcase + 0.1));
            outside().lathe(base, base.add(new Vec3d(0, tank, 0)), new double[]{0, 0.04, 0.1, 0.9, 0.96, 1}, new double[]{0, 0.045, 0.06, 0.06, 0.045, 0}, null, 20, c("lubrication", Shapes.machined(0xffa9adb2)), false);
            outside().tube(PartMesh.bezier(base.add(new Vec3d(0, 0.02, 0.05)), base.add(new Vec3d(0, 0, 0.1)), new Vec3d(-front * 0.4, railY - 0.02, -w), new Vec3d(-front * 0.4 + 0.05, railY - 0.02, -w + 0.02), 10),
                    0.008, 8, c("lubrication", RUBBER));
        }
        // the oil filter on the intake side, the dipstick
        int side = intakeSide[0] == 0 ? -1 : intakeSide[0];
        Vec3d boss = L.oilFilterBoss(), axis = L.oilFilterAxis();
        double f = 0.038 * s;
        outside().lathe(boss, boss.add(axis.scale(0.1 * s)), new double[]{0, 0, 0.08, 0.1, 0.85, 0.95, 1, 1}, new double[]{0, f * 0.9, f * 0.9, f, f, f * 0.95, f * 0.8, 0}, null, 20, c("lubrication", FILTER), false);
        Vec3d stick = new Vec3d(L.dipstickX(), 0.25 * deck, side * (crankcase + 0.004));   // 0.14: between the middle runners
        outside().tube(PartMesh.bezier(stick, stick.add(new Vec3d(0, 0.1, side * 0.02)), stick.add(new Vec3d(0, 0.25, side * 0.02)), stick.add(new Vec3d(0.01, top - 0.25 * deck + 0.04, side * 0.03)), 8),
                0.004, 6, c("lubrication", 0xff9a9ea3));
        outside().tube(PartMesh.circle(stick.add(new Vec3d(0.01, top - 0.25 * deck + 0.055, side * 0.03)), Vec3d.X, 0.012, 14), 0.003, null, c("lubrication", YELLOW), null, 6, true);
    }

    // ---------------------------------------------------------------- cooling

    private void cooling() {
        if (!L.liquid()) { return; }
        // 0.14: 1.4 times as wide as high (PistonEngineLayout), so it sits under a car's hood
        double w = L.radiatorWidth(), h = L.radiatorHeight(), depth = L.radiatorDepth();
        double x0 = L.radiatorX(), yc = L.radiatorY();
        // core with its tubes, the side tanks, cap, fan in its shroud, hoses
        int core = shell("cooling", RADIATOR_CORE), tank = shell("cooling", RADIATOR_TANK);
        outside().box(new Vec3d(x0, yc - h / 2, -w / 2), new Vec3d(x0 + depth, yc + h / 2, w / 2), core);
        int tubes = 18;
        for (int k = 0; k < tubes; k++) {
            double y = yc - h / 2 + h * (k + 0.5) / tubes;
            outside().box(new Vec3d(x0 + depth + 0.0005, y - 0.0025, -w / 2), new Vec3d(x0 + depth + 0.0015, y + 0.0025, w / 2), shell("cooling", Shapes.machined(RADIATOR_CORE)));
        }
        for (int side = -1; side <= 1; side += 2) {
            outside().box(new Vec3d(x0 - 0.005, yc - h / 2 - 0.01, side > 0 ? w / 2 : -w / 2 - 0.05), new Vec3d(x0 + depth + 0.005, yc + h / 2 + 0.01, side > 0 ? w / 2 + 0.05 : -w / 2), tank);
        }
        Vec3d cap = new Vec3d(x0 + depth / 2, yc + h / 2 + 0.01, w / 2 + 0.025);
        outside().cylinder(cap, cap.add(new Vec3d(0, 0.02, 0)), 0.02, 0.02, 14, c("cooling", Shapes.machined(0xffa9adb2)));
        if (e.cooling().fanFlowM3PerS() > 0) {
            double fan = L.fanRadius();
            Vec3d hub = new Vec3d(x0 - 0.045, yc, 0);
            outside().tube(PartMesh.circle(hub, Vec3d.X, fan + 0.012, 40), 0.008, null, shell("cooling", PLASTIC), null, 8, true);
            PartMesh blades = new PartMesh();
            Shapes.bladeRow(blades, 0, 0.04 * s, fan, 7, 0.07 * s, 0.11 * s, Math.toRadians(35), Math.toRadians(55), 0.12, c("cooling", PLASTIC));
            blades.cylinder(new Vec3d(-0.02, 0, 0), new Vec3d(0.02, 0, 0), 0.045 * s, 0.045 * s, 16, c("cooling", BLACK));
            model.piece(blades, angle -> new EngineModel.Placement(hub, aboutX(angle * 0.8)));
        }
        Vec3d upper = new Vec3d(x0, yc + h * 0.35, -w / 2 + 0.03), lower = new Vec3d(x0, yc - h * 0.35, w / 2 - 0.03);
        Vec3d thermostat = L.thermostat(), pump = L.waterPump();
        List<Vec3d> upperHose = PartMesh.bezier(upper, upper.add(new Vec3d(-0.08, 0, 0)), thermostat.add(new Vec3d(0.12, 0.04, 0)), thermostat, 12);
        List<Vec3d> lowerHose = PartMesh.bezier(lower, lower.add(new Vec3d(-0.08, 0, 0)), pump.add(new Vec3d(0.1, -0.08, 0.05)), pump.add(new Vec3d(0, -0.04, 0.03)), 12);
        outside().tube(upperHose, 0.018 * s, 12, c("cooling", RUBBER));
        outside().tube(lowerHose, 0.018 * s, 12, c("cooling", RUBBER));
        for (List<Vec3d> hose : List.of(upperHose, lowerHose)) {
            hoseEnds.add(new Vec3d[]{hose.get(1), hose.get(0)});
            hoseEnds.add(new Vec3d[]{hose.get(hose.size() - 2), hose.get(hose.size() - 1)});
        }
        outside().lathe(new Vec3d(front, 0.45 * deck, 0), pump.add(new Vec3d(0.015, 0, 0)), new double[]{0, 0, 0.6, 0.7, 1, 1}, new double[]{0, 0.05 * s, 0.05 * s, 0.03 * s, 0.03 * s, 0}, null, 20,
                c("cooling", Shapes.material(e.block().material())), true);
    }

    // ---------------------------------------------------------------- accessories: alternator, starter, belt; plugs and coils

    private void accessories() {
        int side = intakeSide[0] == 0 ? -1 : intakeSide[0];
        double beltX = L.beltX();
        // alternator high on the intake side, its pulley on the belt plane
        Vec3d alternator = L.alternator();
        double ar = 0.06 * s;
        double[] along = new double[14], radius = new double[14];
        along[0] = 0; radius[0] = 0;
        for (int k = 0; k < 6; k++) { along[1 + 2 * k] = 0.1 + 0.13 * k; radius[1 + 2 * k] = ar; along[2 + 2 * k] = 0.1 + 0.13 * k + 0.065; radius[2 + 2 * k] = ar * 0.93; }
        along[13] = 1; radius[13] = 0;
        outside().lathe(alternator.add(new Vec3d(-0.12 * s, 0, 0)), alternator, along, radius, null, 20, c("ignition", 0xffb4b8bd), false);
        outside().cylinder(alternator, new Vec3d(beltX + 0.012, alternator.y(), alternator.z()), 0.008, 0.008, 8, c("ignition", Shapes.machined(0xff858b93)));
        PartMesh altPulley = new PartMesh().cylinder(new Vec3d(-0.01, 0, 0), new Vec3d(0.01, 0, 0), 0.03 * s, 0.03 * s, 18, c("ignition", BLACK));
        Vec3d altPulleyAt = new Vec3d(beltX, alternator.y(), alternator.z());
        model.piece(altPulley, angle -> new EngineModel.Placement(altPulleyAt, aboutX(angle * 2.3)));
        // water pump pulley and an idler, then the belt round crank, pump, alternator and idler
        List<double[]> pulleys = new ArrayList<>();
        pulleys.add(new double[]{0, 0, 0.068 * s});
        pulleys.add(new double[]{alternator.y(), alternator.z(), 0.03 * s});
        Vec3d idler = new Vec3d(beltX, 0.3 * deck, -side * 0.06 * s);
        pulleys.add(new double[]{idler.y(), idler.z(), 0.03 * s});
        outside().cylinder(idler.add(new Vec3d(-0.01, 0, 0)), idler.add(new Vec3d(0.01, 0, 0)), 0.03 * s, 0.03 * s, 16, c("ignition", Shapes.machined(0xff858b93)));
        if (e.cooling().kind() == CoolingDesign.Kind.LIQUID) {
            pulleys.add(new double[]{0.45 * deck, 0, 0.05 * s});
            PartMesh pumpPulley = new PartMesh().cylinder(new Vec3d(-0.01, 0, 0), new Vec3d(0.01, 0, 0), 0.05 * s, 0.05 * s, 22, c("cooling", Shapes.machined(0xff858b93)));
            Vec3d at = new Vec3d(beltX, 0.45 * deck, 0);
            model.piece(pumpPulley, angle -> new EngineModel.Placement(at, aboutX(angle * 1.36)));
        }
        Shapes.flatBelt(fixed, Shapes.belt(beltX, pulleys.toArray(new double[0][]), 20), 0.02, 0.004, c("ignition", RUBBER));
        // starter by the flywheel, its pinion at the ring gear, the solenoid on top; an inline engine's on the intake side
        // (0.11: on the exhaust side the collector and its pipe ran through it)
        Vec3d starter = L.starter();
        double sr = 0.038 * s;
        outside().lathe(starter, starter.add(new Vec3d(0.14 * s, 0, 0)), new double[]{0, 0, 0.1, 0.12, 0.9, 1, 1}, new double[]{0, sr * 0.7, sr * 0.7, sr, sr, sr * 0.8, 0}, null, 20, c("ignition", BLACK), false);
        Vec3d solenoid = starter.add(new Vec3d(0.02, sr + 0.02 * s, 0));
        Details.place(outside(), DetailKit.Part.SOLENOID, solenoid, Vec3d.X, Vec3d.Y, 0.04 * s, 0);
        // 0.11: the pinion on the starter's end at the flywheel, meshing with the ring gear
        outside().extrude(Shapes.gear(0.0105 * s, 0.0135 * s, 9), starter, Vec3d.Z, Vec3d.Y, new Vec3d(-0.016 * s, 0, 0), c("ignition", Shapes.machined(0xff858b93)), false);
    }

    private void plugsAndCoils() {
        CylinderHeadDesign head = e.head();
        int plugs = Math.clamp(head.sparkPlugs(), 0, 4);
        double thread = positive(e.sparkPlug().threadDiameterM(), 0.012) / 2, offset = Math.max(0, head.plugOffsetM());
        boolean ohv = head.layout() == CylinderHeadDesign.Layout.OHV;
        for (int b = 0; b < banks; b++) {
            double coverTop = top + (head.layout() == CylinderHeadDesign.Layout.DOHC ? 0.7 : 1) * coverHeight(b);
            for (int i = 0; i < per; i++) {
                for (int k = 0; k < plugs; k++) {
                    double angle = 2 * Math.PI * k / Math.max(plugs, 1), x = cylinderX(i) + offset * Math.cos(angle), z = offset * Math.sin(angle);
                    double lean = 0;
                    if (ohv) { z -= intakeSide[b] * 0.3 * bore; lean = -intakeSide[b] * Math.toRadians(25); }
                    if (cutaway) {
                        Vec3d tip = at(b, x, deck + gasket + 0.002, z), up = aboutX(bankAngle[b] + lean).rotate(Vec3d.Y);
                        double plugLength = 0.09 * s;
                        outside().lathe(tip, tip.add(up.scale(plugLength)), new double[]{0, 0, 0.22, 0.22, 0.3, 0.3, 0.42, 0.42, 0.95, 1, 1},
                                new double[]{0, thread * 0.4, thread, thread * 1.45, thread * 1.45, thread * 1.2, thread * 1.2, thread * 0.85, thread * 0.75, thread * 0.4, 0},
                                new int[]{BOLT, BOLT, BOLT, BOLT, BOLT, CERAMIC, CERAMIC, CERAMIC, CERAMIC, CERAMIC, CERAMIC}, 10, c("sparkPlug", CERAMIC), true);
                    }
                    // coil on plug, standing on the cover
                    Vec3d coil = at(b, x, coverTop + 0.022 * s, z);
                    outside().box(coil, new Vec3d(0.014 * s, 0.022 * s, 0.014 * s), bank(b), c("ignition", COIL));
                    outside().box(coil.add(at(b, 0, 0.02 * s, 0.016 * s).subtract(at(b, 0, 0, 0))), new Vec3d(0.008 * s, 0.006 * s, 0.01 * s), bank(b), c("ignition", 0xff3a3d42));
                }
            }
        }
    }

    // ---------------------------------------------------------------- 0.11: details from the kit

    /**
     * Fasteners, sensors, the harness and fittings an engine carries outside, sized from the bore (s): the cam cover's
     * screws, the sump's bolts, studs and nuts at the ports, lifting eyes, a data plate, bolts in the mounts, the coils'
     * harness, oil pressure, knock, crank position and coolant sensors, hose clamps, and a turbocharger's braided oil feed.
     */
    private void details() {
        int side = intakeSide[0] == 0 ? -1 : intakeSide[0];
        double jacket = bore / 2 + wall + 0.006, coverHalf = jacket - 0.004, portY = deck + gasket + 0.4 * headHeight;
        boolean dohc = e.head().layout() == CylinderHeadDesign.Layout.DOHC;
        for (int b = 0; b < banks; b++) {
            Vec3d up = bank(b).rotate(Vec3d.Y), across = bank(b).rotate(Vec3d.Z);
            double h = coverHeight(b), coverTop = top + (dohc ? 0.7 : 1) * h;
            // cam cover screws: down the middle between the coils of a twin-cam cover, else two rows on its top
            if (dohc) {
                for (int i = 0; i <= per; i++) {
                    double x = i == 0 ? -half + 0.03 : i == per ? half - 0.03 : (cylinderX(i - 1) + cylinderX(i)) / 2;
                    Details.place(casing(b), DetailKit.Part.SOCKET_CAP, at(b, x, coverTop, 0), up, Vec3d.X, 0.006 * s, Details.SMALL);
                }
            } else {
                for (int row = -1; row <= 1; row += 2) {
                    Details.boltRow(casing(b), at(b, -half + 0.03, coverTop, row * 0.6 * coverHalf), at(b, half - 0.03, coverTop, row * 0.6 * coverHalf), Math.max(3, per + 1), up, 0.006 * s,
                            DetailKit.Part.SOCKET_CAP);
                }
            }
            // studs and nuts at both ports of every cylinder
            double runner = positive(e.intake().runnerDiameterM(), 0.04), header = 0.6 * positive(e.valves().exhaustHeadM(), 0.03);
            for (int i = 0; i < per; i++) {
                for (int k = -1; k <= 1; k += 2) {
                    int ex = -intakeSide[b], in = intakeSide[b];
                    Details.place(outside(), DetailKit.Part.HEX_NUT, at(b, cylinderX(i) + k * (header + 0.008), portY, ex * (bore / 2 + wall + 0.014)), across.scale(ex), up, 0.008 * s, Details.SMALL);
                    Details.place(outside(), DetailKit.Part.HEX_NUT, at(b, cylinderX(i) + k * (runner / 2 + 0.008), portY, in * (bore / 2 + wall + 0.014)), across.scale(in), up, 0.007 * s, Details.SMALL);
                }
            }
            // the coils' harness along the cover, a lead and connector to each coil
            int plugs = Math.clamp(e.head().sparkPlugs(), 0, 4);
            if (plugs > 0) {
                double z = -intakeSide[b] * 0.55 * coverHalf, y = coverTop + 0.045 * s;
                // 0.14: from the front coil back and down the back of the head (it ran out past the front, over the radiator)
                List<Vec3d> trunk = new ArrayList<>(Details.line(at(b, cylinderX(per - 1) + 0.03, y, z), at(b, -half - 0.01, y, z), Math.max(2, per * 2)));
                trunk.add(at(b, -half - 0.05, y - 0.06, z));
                List<Vec3d> ends = new ArrayList<>(), ups = new ArrayList<>();
                for (int i = 0; i < per; i++) {
                    ends.add(at(b, cylinderX(i), coverTop + 0.042 * s, 0.03 * s));
                    ups.add(across);
                }
                Details.harness(outside(), trunk, 0.006 * s, ends, ups, 0.011 * s);
            }
        }
        // the sump's bolts along both rails, pointing down
        double railY = -(r + 0.043), w = crankcase + 0.004;
        int count = Math.max(4, (int) Math.round(2 * front / (0.09 * s)));
        for (int row = -1; row <= 1; row += 2) {
            Details.boltRow(casing(-1), new Vec3d(-front + 0.03, railY, row * (w - 0.006)), new Vec3d(front - 0.03, railY, row * (w - 0.006)), count, Vec3d.Y.scale(-1), 0.006 * s,
                    DetailKit.Part.HEX_BOLT);
        }
        // lifting eyes on the head's ends, a data plate on the block, bolts in the mounts
        Vec3d upHead = bank(0).rotate(Vec3d.Y);
        for (int end = -1; end <= 1; end += 2) {
            Details.place(outside(), DetailKit.Part.LIFTING_EYE, at(0, end * (half + 0.004), deck + gasket + 0.55 * headHeight, -intakeSide[0] * 0.45 * jacket), Vec3d.X.scale(end), upHead, 0.05 * s, Details.SMALL);
        }
        Details.place(outside(), DetailKit.Part.DATA_PLATE, new Vec3d(-0.4 * half, 0.12 * deck, -side * (crankcase + 0.0005)), Vec3d.Z.scale(-side), Vec3d.X, 0.07 * s, Details.TINY);
        for (int k = -1; k <= 1; k += 2) {
            for (int dx = -1; dx <= 1; dx += 2) {
                Details.place(outside(), DetailKit.Part.HEX_BOLT, new Vec3d(0.15 * half + dx * 0.02 * s, 0.05 * deck, k * (crankcase + 0.04)), Vec3d.Z.scale(k), Vec3d.X, 0.008 * s, Details.SMALL);
            }
        }
        // sensors: oil pressure and knock on the block, crank position at the front, coolant at the thermostat
        Details.place(outside(), DetailKit.Part.SENSOR, new Vec3d(0.1 * half, 0.12 * deck, side * (crankcase + 0.002)), Vec3d.Z.scale(side), Vec3d.Y, 0.018 * s, Details.SMALL);
        Details.place(outside(), DetailKit.Part.SENSOR, new Vec3d(-0.05 * half, 0.22 * deck, side * (crankcase + 0.002)), Vec3d.Z.scale(side), Vec3d.Y, 0.014 * s, Details.SMALL);
        Details.place(outside(), DetailKit.Part.SENSOR, new Vec3d(frontPlane + 0.002, -0.03, -side * 0.085 * s), Vec3d.X, Vec3d.Y, 0.012 * s, Details.SMALL);
        if (e.cooling().kind() == CoolingDesign.Kind.LIQUID) {
            Vec3d thermostat = at(0, front - 0.02, top - 0.02, 0).add(new Vec3d(0.03, 0, 0));
            Details.place(outside(), DetailKit.Part.SENSOR, thermostat.add(new Vec3d(0, 0.02, 0.025)), Vec3d.Y, Vec3d.X, 0.011 * s, Details.SMALL);
        }
        // clamps on the radiator hoses' ends
        for (Vec3d[] end : hoseEnds) {
            Vec3d dir = end[1].subtract(end[0]);
            Details.place(outside(), DetailKit.Part.HOSE_CLAMP, end[1].subtract(MeshBuilderAlias.unit(dir).scale(0.012 * s)), dir, Vec3d.Y, 2.1 * 0.018 * s, Details.SMALL);
        }
        // a turbocharger's oil feed: a braided line from the block to its bearing housing
        if (turboBearing != null) {
            Vec3d from = new Vec3d(0.25 * half, 0.3 * deck, collectorOut.z() >= 0 ? crankcase + 0.01 : -(crankcase + 0.01));
            List<Vec3d> path = PartMesh.bezier(from, from.add(new Vec3d(0, 0.05, collectorOut.z() >= 0 ? 0.04 : -0.04)), turboBearing.add(new Vec3d(0, 0.08, 0)), turboBearing, 12);
            Details.braidedLine(outside(), path, 0.004 * s);
            Details.clamps(outside(), path, 0.0045 * s, 0.12 * s, Vec3d.Y.scale(-1));
        }
    }

    /** MeshBuilder's static helpers by a short name (the model already imports another Vec3d user). */
    private static final class MeshBuilderAlias {
        static Vec3d unit(Vec3d v) { return dev.mcmod.aerocore.api.mesh.MeshBuilder.unit(v); }
    }
}
