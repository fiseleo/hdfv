package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.hdfv.part.engine.CoolingDesign;
import dev.mcmod.hdfv.part.engine.CylinderHeadDesign;
import dev.mcmod.hdfv.part.engine.EngineBlockDesign;
import dev.mcmod.hdfv.part.engine.FuelSystemDesign;
import dev.mcmod.hdfv.part.engine.LubricationDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ValvetrainDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.14: where a piston engine's parts are, from its design: the frame (cylinders, banks, deck, head), the valvetrain's
 * geometry (valves, cams, rockers: what sets the cam cover's height) and the outside (intake, exhaust, sump, radiator,
 * alternator, starter, coils), and from these its envelope (the room it takes). The client's PistonEngineModel draws
 * from these numbers, so the envelope a body is checked against and the engine drawn cannot drift apart
 * (/hdfvdev envelope checks).
 * <p>
 * Frame: crank along +x (the front, where the timing drive is), y up, z starboard; the origin on the crank axis in the
 * middle of the cylinder row. Nonsense values in a design being edited are replaced so it still lays out.
 * <p>
 * The intake of an inline engine is compact, as on most cars (0.14; it climbed 0.22 m over the head to a plenum above
 * the cam cover and a cone filter ahead of the radiator, and stood out of a sedan's hood): its runners curve out from
 * the ports and back down beside the head (a longer runner reaches farther out, not higher), the plenum lies beside
 * the head, an air box beside the block. A vee's or a flat engine's runners keep to the valley.
 */
public final class PistonEngineLayout {
    public static final int MAX_PER_BANK = 16;
    /** The flywheel's ring gear's tip radius (m; the flywheel is drawn at one size). */
    public static final double RING_GEAR = 0.143;

    public final PistonEngineDesign e;
    public final int per, banks;
    public final double bore, spacing, deck, wall, r, rodLength, gasket, headHeight, half, front, crankcase, s;
    public final double[] bankAngle, bankOffset;
    public final int[] intakeSide;
    public final double top, coverThickness, frontPlane;

    private PistonEngineLayout(PistonEngineDesign e) {
        this.e = e;
        EngineBlockDesign b = e.block();
        per = Math.clamp(b.cylindersPerBank(), 1, MAX_PER_BANK);
        bore = positive(b.boreM(), 0.08);
        spacing = Math.max(positive(b.boreSpacingM(), bore * 1.1), bore * 1.02);
        deck = positive(b.deckHeightM(), 0.2);
        wall = positive(b.wallThicknessM(), 0.006);
        r = positive(e.crank().strokeM(), 0.08) / 2;
        rodLength = Math.max(positive(e.rod().lengthM(), 0.14), r * 1.1);
        gasket = Math.max(positive(e.head().gasketThicknessM(), 0.001), 0.0015);
        headHeight = e.head().castingHeight();
        half = (per - 1) * spacing / 2 + bore / 2 + wall + 0.02;
        s = Math.clamp(bore / 0.086, 0.5, 2.5);
        double w = positive(e.rod().bigEndWidthM(), 0.02);
        if (b.layout() == EngineBlockDesign.Layout.INLINE) { bankAngle = new double[]{0}; bankOffset = new double[]{0}; intakeSide = new int[]{-1}; }
        else {
            double a = Math.toRadians(Math.clamp(b.effectiveBankAngle(), 10, 180)) / 2;
            bankAngle = new double[]{-a, a}; bankOffset = new double[]{-w / 2, w / 2}; intakeSide = new int[]{1, -1};   // a vee's intakes in the valley
        }
        banks = bankAngle.length;
        front = half + Math.abs(bankOffset[0]);
        top = deck + gasket + headHeight;
        coverThickness = e.valvetrain().drive() == ValvetrainDesign.Drive.BELT ? 0.010 : 0.014;
        frontPlane = front + coverThickness;
        double width = Math.max(bore / 2 + wall + 0.01, r + 0.05);
        if (banks > 1) { width = Math.max(width, Math.abs(at(0, 0, 0.3 * deck, -(bore / 2 + wall)).z()) + 0.01); }
        crankcase = width;
    }

    public static PistonEngineLayout of(PistonEngineDesign e) { return new PistonEngineLayout(e); }

    public static double positive(double v, double fallback) { return Double.isFinite(v) && v > 0 ? v : fallback; }
    public static Orientation aboutX(double radians) { return Orientation.rotationVector(Vec3d.X.scale(radians)); }

    // ---------------------------------------------------------------- frames

    /** A point in bank b's frame (x along the crank, y up the bores, z across) in the engine's frame. */
    public Vec3d at(int b, double x, double y, double z) { return new Vec3d(x + bankOffset[b], 0, 0).add(aboutX(bankAngle[b]).rotate(new Vec3d(0, y, z))); }
    public Orientation bank(int b) { return aboutX(bankAngle[b]); }
    public double cylinderX(int i) { return (i - (per - 1) / 2.0) * spacing; }
    /** The side (-1 port, +1 starboard) the first bank's intake is on (a vee's: the valley side), for what hangs off the block. */
    public int side() { return intakeSide[0] == 0 ? -1 : intakeSide[0]; }

    /** Crank throw angle of crankpin i (rad): a mirrored (flat-plane) crank for an even number per bank, else evenly spread. */
    public double throwAngle(int i) {
        if (per % 2 == 0 && per > 1) { int k = i < per / 2 ? i : per - 1 - i; return 2 * Math.PI * k / (per / 2.0); }
        return 2 * Math.PI * i / per;
    }

    // ---------------------------------------------------------------- valvetrain

    public double valveAngle() { return Math.toRadians(Math.clamp(e.head().valveAngleDeg(), 0, 90)) / 2; }
    public double valveLength() { return positive(e.valves().lengthM(), 0.1); }
    public double camRadius() { return 0.013 * s; }
    public double camBase() { return 0.017 * s; }

    /** Cam axis position in bank frame for the intake (0) or exhaust (1) side: (y, z). */
    public double[] camPosition(int b, int which) {
        CylinderHeadDesign head = e.head();
        int side = which == 0 ? intakeSide[b] : -intakeSide[b];
        double va = valveAngle(), len = valveLength();
        double tipY = deck + gasket + 0.004 + Math.cos(va) * len, tipZ = side * 0.22 * bore + side * Math.sin(va) * len;
        double follower = 0.018 * s, base = 0.017 * s;
        if (head.layout() == CylinderHeadDesign.Layout.SOHC) { return new double[]{tipY + follower + base + 0.012 * s, 0}; }
        return new double[]{tipY + (follower + base) * Math.cos(va), tipZ + side * (follower + base) * Math.sin(va)};
    }

    /**
     * A valve: its bank and cylinder, intake or exhaust, the side it leans to, where along the crank, its seat (bank frame
     * y, z) and its tilt from the bore axis (rad about x). Twin-cam and single-cam heads put the intake valves on one side
     * of the bore and the exhausts on the other (a pent roof); a pushrod head has them in a row along the crank, leaning
     * towards the pushrods.
     */
    public record Valve(int bank, int cylinder, boolean intake, int side, double x, double seatY, double seatZ, double tilt) {}

    public List<Valve> valves(int b) {
        CylinderHeadDesign head = e.head();
        boolean ohv = head.layout() == CylinderHeadDesign.Layout.OHV;
        double va = valveAngle();
        List<Valve> out = new ArrayList<>();
        for (int i = 0; i < per; i++) {
            for (int which = 0; which < 2; which++) {
                boolean intake = which == 0;
                int side = intake ? intakeSide[b] : -intakeSide[b];
                int count = Math.clamp(intake ? head.intakeValves() : head.exhaustValves(), 0, 5);
                for (int v = 0; v < count; v++) {
                    double seatY = deck + gasket + 0.004;
                    if (ohv) {
                        double x = cylinderX(i) + (intake ? -1 : 1) * 0.22 * bore + (v - (count - 1) / 2.0) * 0.18 * bore;
                        out.add(new Valve(b, i, intake, side, x, seatY, 0, intakeSide[b] * va));
                    } else {
                        double pitch = count > 1 ? Math.min(0.42 * bore, 0.84 * bore / count) : 0;
                        out.add(new Valve(b, i, intake, side, cylinderX(i) + (v - (count - 1) / 2.0) * pitch, seatY, side * 0.22 * bore, side * va));
                    }
                }
            }
        }
        return out;
    }

    public double tipY(Valve v) { return v.seatY() + Math.cos(v.tilt()) * valveLength(); }
    public double tipZ(Valve v) { return v.seatZ() + Math.sin(v.tilt()) * valveLength(); }

    /** A camshaft: in a bank's frame (y, z), or for a pushrod vee's single cam in the valley, in the engine's frame (bank -1). */
    public record Cam(int bank, double y, double z) {}

    public Vec3d camPoint(Cam c, double x) { return c.bank() < 0 ? new Vec3d(x, c.y(), c.z()) : at(c.bank(), x, c.y(), c.z()); }

    /** The engine's camshafts: two per bank over a twin-cam head, one per bank over a single-cam head, one in the block of a pushrod engine. */
    public List<Cam> cams() {
        List<Cam> out = new ArrayList<>();
        CylinderHeadDesign.Layout layout = e.head().layout();
        if (layout == CylinderHeadDesign.Layout.OHV) {
            if (banks == 1) { out.add(new Cam(0, 0.36 * deck, intakeSide[0] * (bore / 2 + wall + 0.022))); }
            else { out.add(new Cam(-1, r * 1.45 + 0.04 * s, 0)); }   // in the valley, clear of the crank's counterweights
            return out;
        }
        for (int b = 0; b < banks; b++) {
            if (layout == CylinderHeadDesign.Layout.DOHC) {
                double[] in = camPosition(b, 0), ex = camPosition(b, 1);
                out.add(new Cam(b, in[0], in[1]));
                out.add(new Cam(b, ex[0], ex[1]));
            } else {
                out.add(new Cam(b, deck + gasket + 0.004 + Math.cos(valveAngle()) * valveLength() + 0.026 * s, 0));
            }
        }
        return out;
    }

    /** The cam a valve runs off. */
    public Cam camOf(List<Cam> cams, Valve v) {
        return switch (e.head().layout()) {
            case OHV -> cams.get(0);
            case SOHC -> cams.get(v.bank());
            case DOHC -> cams.get(2 * v.bank() + (v.intake() ? 0 : 1));
        };
    }

    /** A rocker's pivot and its two ends in the bank frame (y, z): the end on the valve, and the end the cam or pushrod moves. */
    public record Rocker(double[] pivot, double[] valveEnd, double[] inputEnd) {
        public double ratio() { return Math.hypot(valveEnd[0] - pivot[0], valveEnd[1] - pivot[1]) / Math.max(1e-6, Math.hypot(inputEnd[0] - pivot[0], inputEnd[1] - pivot[1])); }
    }

    public Rocker rocker(Valve v, Cam cam) {
        double ty = tipY(v), tz = tipZ(v);
        double[] valveEnd = {ty + 0.003 * s * Math.cos(v.tilt()), tz + 0.003 * s * Math.sin(v.tilt())};
        double[] input;
        double share;
        if (e.head().layout() == CylinderHeadDesign.Layout.SOHC) {
            input = new double[]{cam.y() + camBase() + 0.004 * s, v.side() * 0.007 * s};
            share = 0.45;
        } else {
            // a pushrod cup inboard of the valve, towards the pushrods; arms of about 1.7 : 1
            input = new double[]{ty - 0.004 * s, tz + intakeSide[v.bank()] * 0.045 * s};
            share = 0.37;
        }
        double[] pivot = {input[0] + share * (valveEnd[0] - input[0]) + 0.011 * s, input[1] + share * (valveEnd[1] - input[1])};
        return new Rocker(pivot, valveEnd, input);
    }

    /** The cam cover's height over the head (bank frame). */
    public double coverHeight(int b) {
        double highest = top + 0.035 * s;
        List<Cam> cams = cams();
        double lift = Math.max(positive(e.camshaft().intakeLiftM(), 0.01), positive(e.camshaft().exhaustLiftM(), 0.01));
        for (Cam c : cams) { if (c.bank() == b) { highest = Math.max(highest, c.y() + camBase() + lift + 0.006 * s); } }
        if (e.head().layout() != CylinderHeadDesign.Layout.DOHC) {
            for (Valve v : valves(b)) { highest = Math.max(highest, rocker(v, camOf(cams, v)).pivot()[0] + 0.014 * s); }
        }
        return Math.max(0.035 * s, highest - top + 0.008 * s);
    }

    /** The top of the cam cover, where the coils stand (bank frame y). */
    public double coverTop(int b) { return top + (e.head().layout() == CylinderHeadDesign.Layout.DOHC ? 0.7 : 1) * coverHeight(b); }

    /** Timing sprocket radius on a cam (twice the crank's, so the cams turn at half speed). */
    public double sprocketRadius() {
        double r0 = 0.055 * s;
        if (e.head().layout() == CylinderHeadDesign.Layout.DOHC) { r0 = Math.min(r0, 0.48 * Math.abs(camPosition(0, 0)[1] - camPosition(0, 1)[1])); }
        return Math.max(r0, 0.02);
    }

    /** The timing drive's pulleys seen from the front (y, z, radius): the crank's sprocket, then each cam's. */
    public List<double[]> timingPulleys() {
        List<double[]> out = new ArrayList<>();
        out.add(new double[]{0, 0, sprocketRadius() / 2});
        for (Cam cam : cams()) {
            Vec3d c = camPoint(cam, front - (cam.bank() < 0 ? 0 : bankOffset[cam.bank()]) - 0.004);
            out.add(new double[]{c.y(), c.z(), sprocketRadius()});
        }
        return out;
    }

    // ---------------------------------------------------------------- intake

    public double portY() { return deck + gasket + 0.4 * headHeight; }
    public double runnerRadius() { return positive(e.intake().runnerDiameterM(), 0.04) / 2; }
    public double throttleRadius() { return positive(e.intake().throttleDiameterM(), 0.06) / 2; }
    /** Throttle bodies on every runner (no plenum). */
    public boolean individualThrottles() { return e.intake().throttleBodies() >= e.block().cylinders() && e.block().cylinders() > 1; }
    public boolean carburettor() { return e.fuelSystem().delivery() == FuelSystemDesign.Delivery.CARBURETTOR; }
    /** Whether the intake is the compact one beside the head (an inline engine's). */
    public boolean compactIntake() { return banks == 1; }

    /** A runner's port (bank frame offset out of the head). */
    public Vec3d port(int b, int i) { return at(b, cylinderX(i), portY(), intakeSide[b] * (bore / 2 + wall + 0.01)); }

    /** The way out of bank b's intake side, and up its bores (engine frame unit vectors). */
    public Vec3d out(int b, int side) { return at(b, 0, 0, side).subtract(at(b, 0, 0, 0)); }
    public Vec3d up(int b) { return at(b, 0, 1, 0).subtract(at(b, 0, 0, 0)); }

    /**
     * Runner i of bank b as a path from its port, of the design's length. Compact (inline): a cubic out of the port, over
     * and back down beside the head, ending outboard at the port's height; its rise is capped so it stays under the
     * coils' top, a longer runner reaching farther out. A vee's or flat engine's: out of the port, sweeping up and back
     * over the head into the valley.
     */
    public List<Vec3d> runner(int b, int i) {
        Vec3d port = port(b, i), out = out(b, intakeSide[b]), up = up(b);
        double length = positive(e.intake().runnerLengthM(), 0.3);
        double k = 1;
        List<Vec3d> path = null;
        if (compactIntake()) {
            double rise = Math.max(0.02, coverTop(b) + 0.045 * s - portY() - runnerRadius()), riseMax = rise / (0.75 * 0.55);
            for (int it = 0; it < 6; it++) {
                double ku = Math.min(k, riseMax);
                path = MeshBuilder.bezier(port, port.add(out.scale(0.15 * k)).add(up.scale(0.55 * ku)), port.add(out.scale(0.85 * k)).add(up.scale(0.55 * ku)),
                        port.add(out.scale(0.75 * k)), 16);
                k *= length / Math.max(length(path), 1e-6);
            }
            return path;
        }
        for (int it = 0; it < 4; it++) {
            Vec3d p1 = port.add(out.scale(0.35 * k)), p3 = port.add(out.scale(0.45 * k)).add(up.scale(0.55 * k)), p2 = p3.add(up.scale(-0.35 * k)).add(out.scale(0.1 * k));
            path = MeshBuilder.bezier(port, p1, p2, p3, 16);
            k *= length / Math.max(length(path), 1e-6);
        }
        return path;
    }

    public static double length(List<Vec3d> path) {
        double l = 0;
        for (int i = 0; i + 1 < path.size(); i++) { l += path.get(i + 1).subtract(path.get(i)).length(); }
        return l;
    }

    /** The plenum: along x, its centre, length and radius (where the runners end; an inline engine's a little below their ends). */
    public record Plenum(Vec3d centre, double length, double radius) {
        public Vec3d rear() { return centre.add(new Vec3d(-length / 2, 0, 0)); }
        public Vec3d front() { return centre.add(new Vec3d(length / 2, 0, 0)); }
    }

    public Plenum plenum() {
        Vec3d centre = Vec3d.ZERO;
        int n = 0;
        for (int b = 0; b < banks; b++) {
            for (int i = 0; i < per; i++) { List<Vec3d> p = runner(b, i); centre = centre.add(p.get(p.size() - 1)); n++; }
        }
        centre = centre.scale(1.0 / n);
        double length = (per - 1) * spacing + 2 * runnerRadius() + 0.06;
        double radius = Math.max(Math.sqrt(positive(e.intake().plenumVolumeM3(), 0.002) / (Math.PI * length)), 1.4 * runnerRadius());
        if (compactIntake()) { centre = centre.add(up(0).scale(-0.6 * radius)); }
        return new Plenum(centre, length, radius);
    }

    /**
     * The air box (0.14, for the cone filter that stood ahead of the radiator): about 5 times the displacement, 2 : 1.2 : 1
     * long, high and wide. An inline engine's lies beside the block on the intake side, under its plenum; a vee's or a
     * flat engine's at the port front corner, outboard of the first bank's head.
     */
    public Envelope.Box airBox() {
        Plenum p = plenum();
        double k = Math.cbrt(5 * Math.max(e.displacement(), 1e-4) / 2.4);
        if (compactIntake()) {
            int side = side();
            double inner = side * (crankcase + 0.13 * s + 0.02), outer = inner + side * k;
            double y1 = p.centre().y() - p.radius() - 0.01, y0 = y1 - 1.2 * k;
            double x1 = p.front().x() + 0.013, x0 = x1 - 2 * k;
            return new Envelope.Box("air_box", new Vec3d(x0, y0, Math.min(inner, outer)), new Vec3d(x1, y1, Math.max(inner, outer)));
        }
        double headOut = 0;
        double headJacket = bore / 2 + wall + 0.014;
        for (int c = 0; c < 4; c++) { headOut = Math.max(headOut, -at(0, 0, (c & 1) == 0 ? deck : coverTop(0), (c & 2) == 0 ? -headJacket : headJacket).z()); }
        double inner = -(Math.max(headOut, crankcase + 0.07 * s) + 0.03), outer = inner - k;
        double x1 = front + 0.02, x0 = x1 - 2 * k, y0 = 0.15 * deck, y1 = y0 + 1.2 * k;
        return new Envelope.Box("air_box", new Vec3d(x0, y0, outer), new Vec3d(x1, y1, inner));
    }

    /** The hose from the throttle body down to the air box, and the snorkel from the box forward (toward the radiator). */
    public List<Vec3d> airHose() {
        Plenum p = plenum();
        Envelope.Box box = airBox();
        Vec3d t1 = p.front().add(new Vec3d(0.05, 0, 0));
        if (compactIntake()) {
            Vec3d into = new Vec3d(box.max().x() - 0.04 * s, box.max().y(), 0.5 * (box.min().z() + box.max().z()));
            return MeshBuilder.bezier(t1, t1.add(new Vec3d(0.04, 0, 0)), into.add(new Vec3d(0, 0.06, 0)), into, 10);
        }
        // a vee's: forward off the throttle body, then out and down to the box's inboard face
        Vec3d into = new Vec3d(box.max().x() - 0.06 * s, 0.5 * (box.min().y() + box.max().y()), box.max().z());
        return MeshBuilder.bezier(t1, t1.add(new Vec3d(0.06, 0, 0)), into.add(new Vec3d(0, 0.04, 0.12)), into, 12);
    }

    public List<Vec3d> snorkel() {
        Envelope.Box box = airBox();
        double y = 0.5 * (box.min().y() + box.max().y()), z = 0.5 * (box.min().z() + box.max().z());
        double reach = e.cooling().kind() == CoolingDesign.Kind.LIQUID ? radiatorX() - 0.04 : box.max().x() + 0.08;
        Vec3d a = new Vec3d(box.max().x(), y, z), b = new Vec3d(Math.max(reach, a.x() + 0.02), y, z);
        return List.of(a, b);
    }

    // ---------------------------------------------------------------- exhaust

    public double headerRadius() { return 0.6 * positive(e.valves().exhaustHeadM(), 0.03); }

    /** Where bank b's headers merge into its collector (engine frame). */
    public Vec3d merge(int b) { return at(b, -half * 0.55, 0.05 * deck, -intakeSide[b] * (bore / 2 + wall + 0.01 + 0.11 * s)); }

    /** Header i of bank b, from its port to the collector. */
    public List<Vec3d> header(int b, int i) {
        int side = -intakeSide[b];
        Vec3d out = out(b, side), up = up(b);
        Vec3d port = at(b, cylinderX(i), portY(), side * (bore / 2 + wall + 0.01));
        double spread = (i - (per - 1) / 2.0) * headerRadius() * 0.9;
        Vec3d target = merge(b).add(new Vec3d(0, spread * 0.6, spread * 0.3 * side));
        return MeshBuilder.bezier(port, port.add(out.scale(0.08 * s)), target.add(up.scale(0.12 * s)).add(new Vec3d(0.04, 0, 0)), target, 18);
    }

    public double collectorRadius() { return headerRadius() * Math.sqrt(per) * 0.85; }
    /** The collector's end, where the down pipe leaves (and a turbocharger on the first bank hangs). */
    public Vec3d collectorEnd(int b) { return merge(b).add(new Vec3d(-0.07 * s, -0.02, 0)); }

    /** 0.14: the down pipe from the collector straight down past the sump rail (it ran aft past the flywheel into a gearbox's bell). */
    public List<Vec3d> downPipe(int b) {
        Vec3d c1 = collectorEnd(b), end = new Vec3d(c1.x(), railY() - 0.04, c1.z());
        return MeshBuilder.bezier(c1, c1.add(new Vec3d(-0.02, -0.03, 0)), end.add(new Vec3d(0, 0.04, 0)), end, 10);
    }

    // ---------------------------------------------------------------- oil

    public double railY() { return -(r + 0.043); }
    public boolean drySump() { return e.lubrication().sump() == LubricationDesign.Sump.DRY; }
    /** The pan's shallow part's and its well's depth below the rail. */
    public double sumpShallow() { return drySump() ? 0.035 : 0.05; }
    public double sumpDeep() {
        double length = 2 * front - 0.02, volume = positive(e.lubrication().oilVolumeM3(), 0.004);
        return drySump() ? 0.035 : Math.clamp(volume / (0.45 * length * 1.6 * crankcase) + 0.04, 0.06, 0.3);
    }

    /** The oil filter: its boss on the block, its axis and length and radius. */
    public Vec3d oilFilterBoss() { return new Vec3d(-0.2 * half, 0.05 * deck, side() * crankcase); }
    public Vec3d oilFilterAxis() { return new Vec3d(0, -0.45, side()).normalized(); }

    /** The dipstick's x: between the two middle runners (0.14; at 0.3 of the half length it ran through a runner). */
    public double dipstickX() {
        if (per == 1) { return 0.3 * half; }
        int k = (per - 1) / 2;
        return 0.5 * (cylinderX(k) + cylinderX(k + 1));
    }

    // ---------------------------------------------------------------- cooling

    public boolean liquid() { return e.cooling().kind() == CoolingDesign.Kind.LIQUID; }
    /** The radiator: 1.4 times as wide as high (0.14; it was square, as tall as a sedan's nose), of the design's area. */
    public double radiatorWidth() { double a = Math.clamp(Math.sqrt(positive(e.cooling().radiatorAreaM2(), 0.2)), 0.15, 1.2); return a * Math.sqrt(1.4); }
    public double radiatorHeight() { double a = Math.clamp(Math.sqrt(positive(e.cooling().radiatorAreaM2(), 0.2)), 0.15, 1.2); return a / Math.sqrt(1.4); }
    public double radiatorDepth() { return Math.clamp(positive(e.cooling().radiatorDepthM(), 0.03), 0.01, 0.15); }
    public double radiatorX() { return frontPlane + 0.1 + 0.03 * s; }
    public double radiatorY() { return 0.4 * deck; }
    public double fanRadius() { return 0.42 * Math.min(radiatorWidth(), radiatorHeight()); }
    public Vec3d thermostat() { return at(0, front - 0.02, top - 0.02, 0).add(new Vec3d(0.03, 0, 0)); }
    public Vec3d waterPump() { return new Vec3d(frontPlane + 0.02, 0.45 * deck, 0); }

    // ---------------------------------------------------------------- accessories

    public double beltX() { return frontPlane + 0.012 + 0.012 * s; }
    /** The alternator's front end centre (its body runs 0.12 s back from it), high on the intake side; radius 0.06 s. */
    public Vec3d alternator() { return new Vec3d(front - 0.06 * s, 0.62 * deck, side() * (crankcase + 0.07 * s)); }
    /** The starter by the flywheel (its pinion at the ring gear): an inline engine's on the intake side. */
    public Vec3d starter() {
        int starterSide = banks == 1 ? side() : -side();
        return new Vec3d(-front - 0.03, -0.03, starterSide * (RING_GEAR + 0.04 * s));
    }
    public double flywheelThickness() { return Math.clamp(e.crank().flywheelMassKg() / (7850 * Math.PI * 0.14 * 0.14), 0.006, 0.06); }
    /** The flywheel's face (the rear end of the crank's flange): where a gearbox's bell bolts on. */
    public double flywheelFace() { return -front - 0.012; }

    // ---------------------------------------------------------------- envelope

    /** The room the engine takes, as named boxes (engine frame). */
    public Envelope envelope() {
        List<Envelope.Box> boxes = new ArrayList<>();
        int side = side();
        boolean air = e.cooling().kind() == CoolingDesign.Kind.AIR;
        // crankcase and pan rail, mounts with their rubbers
        boxes.add(new Envelope.Box("crankcase", new Vec3d(-front - 0.004, railY(), -crankcase - 0.006), new Vec3d(front + 0.004, 0.32 * deck, crankcase + 0.006)));
        boxes.add(new Envelope.Box("mounts", new Vec3d(0.15 * half - 0.035 * s, 0.05 * deck - 0.03 * s, -crankcase - 0.07), new Vec3d(0.15 * half + 0.035 * s, 0.05 * deck + 0.03 * s, crankcase + 0.07)));
        // each bank: the block's upper part, the head and cover, the coils; the ports' flanges
        double fins = air ? Math.clamp(positive(e.cooling().finAreaM2(), 0.5) / (e.block().cylinders() * 2 * Math.PI * (bore / 2 + wall) * 0.6 * deck) * 0.004, 0.006, 0.04) : 0;
        for (int b = 0; b < banks; b++) {
            Envelope.Points block = new Envelope.Points(), head = new Envelope.Points(), coils = new Envelope.Points();
            // 0.14 (/hdfvdev envelope): the head's width with the cam cover's ends and the nuts on the ports' studs
            double jacket = bore / 2 + wall + 0.008 + fins, headJacket = bore / 2 + wall + 0.026 + 0.01 * s + (air ? 0.02 : 0);
            for (int k = 0; k < 8; k++) {
                double x = (k & 1) == 0 ? -half - 0.004 : half + 0.004, z = (k & 4) == 0 ? -jacket : jacket;
                block.add(at(b, x, (k & 2) == 0 ? 0.28 * deck : deck + gasket, z), 0);
                head.add(at(b, x, (k & 2) == 0 ? deck : coverTop(b) + 0.014 * s, (k & 4) == 0 ? -headJacket : headJacket), 0);
            }
            boxes.add(block.box("block"));
            boxes.add(head.box("head"));
            double y = coverTop(b) + 0.045 * s, z = 0.55 * (bore / 2 + wall + 0.002);
            for (int i = 0; i < per; i++) {
                coils.add(at(b, cylinderX(i), coverTop(b), 0), 0.026 * s).add(at(b, cylinderX(i), coverTop(b) + 0.048 * s, 0.016 * s), 0.01 * s);
                coils.add(at(b, cylinderX(i), coverTop(b) + 0.042 * s, 0.0476 * s), 0.016 * s);   // the lead's connector, its loop across
            }
            coils.add(at(b, cylinderX(per - 1) + 0.03, y, -intakeSide[b] * z), 0.006 * s).add(at(b, -half - 0.05, y - 0.06, -intakeSide[b] * z), 0.006 * s);
            boxes.add(coils.box("coils"));
        }
        // timing cover and the front: pulleys, belt, damper
        Envelope.Points timing = new Envelope.Points();
        for (double[] p : timingPulleys()) {
            timing.addAcross(new Vec3d(front, p[0], p[1]), p[2] + 0.014).addAcross(new Vec3d(frontPlane, p[0], p[1]), p[2] + 0.014);
        }
        boxes.add(timing.box("timing"));
        Envelope.Points belt = new Envelope.Points();
        double bx = beltX();
        belt.addAcross(new Vec3d(frontPlane, 0, 0), 0.068 * s).addAcross(new Vec3d(frontPlane + 0.006 + 0.03 * s, 0, 0), 0.068 * s).addAcross(new Vec3d(frontPlane + 0.03, 0, 0), 0.068 * s);
        belt.addAcross(new Vec3d(bx + 0.012, alternator().y(), alternator().z()), 0.035 * s).addAcross(new Vec3d(bx - 0.01, 0.3 * deck, -side * 0.06 * s), 0.035 * s)
                .addAcross(new Vec3d(bx + 0.01, 0.3 * deck, -side * 0.06 * s), 0.035 * s);
        if (liquid()) { belt.addAcross(new Vec3d(bx + 0.01, 0.45 * deck, 0), 0.055 * s); }
        boxes.add(belt.box("front"));
        // flywheel and starter, alternator, oil filter, sump
        double fw = flywheelFace();
        boxes.add(new Envelope.Box("flywheel", new Vec3d(fw - flywheelThickness() - 0.012, -RING_GEAR, -RING_GEAR), new Vec3d(fw, RING_GEAR, RING_GEAR)));
        Vec3d st = starter();
        double sr = 0.038 * s;
        boxes.add(new Envelope.Points().addAcross(st.add(new Vec3d(-0.016 * s, 0, 0)), sr).addAcross(st.add(new Vec3d(0.14 * s, 0, 0)), sr)
                .add(st.add(new Vec3d(0.02, sr + 0.02 * s, 0)), 0.025 * s).box("starter"));
        Vec3d alt = alternator();
        boxes.add(new Envelope.Points().addAcross(alt.add(new Vec3d(-0.12 * s, 0, 0)), 0.06 * s).addAcross(new Vec3d(beltX() + 0.022, alt.y(), alt.z()), 0.03 * s)
                .addAcross(alt, 0.06 * s).box("alternator"));
        boxes.add(new Envelope.Points().add(oilFilterBoss(), 0.038 * s).add(oilFilterBoss().add(oilFilterAxis().scale(0.1 * s)), 0.038 * s).box("oil_filter"));
        double rail = railY(), w = crankcase + 0.004;
        boxes.add(new Envelope.Box("sump", new Vec3d(-front + 0.01, rail - sumpDeep() - 0.008, -w), new Vec3d(front - 0.01, rail, w)));
        if (drySump()) {
            double tank = Math.clamp(positive(e.lubrication().oilVolumeM3(), 0.004) / (Math.PI * 0.06 * 0.06), 0.08, 0.6);
            Vec3d base = new Vec3d(-front * 0.4, -r, -(crankcase + 0.1));
            boxes.add(new Envelope.Points().add(base, 0.06).add(base.add(new Vec3d(0, tank, 0)), 0.06).box("oil_tank"));
        }
        double dx = dipstickX(), sy = 0.25 * deck;
        boxes.add(new Envelope.Points().add(new Vec3d(dx, sy, side * (crankcase + 0.004)), 0.004)
                .add(new Vec3d(dx + 0.01, sy + (top - 0.25 * deck + 0.055), side * (crankcase + 0.034)), 0.015).box("dipstick"));
        // 0.14: the lifting eyes on the head's ends (bank 0), the oil pressure and knock sensors on the block
        double eyeJacket = bore / 2 + wall + 0.006;
        Envelope.Points eyes = new Envelope.Points();
        for (int end = -1; end <= 1; end += 2) {
            Vec3d eye = at(0, end * (half + 0.004), deck + gasket + 0.55 * headHeight, -intakeSide[0] * 0.45 * eyeJacket);
            eyes.addAcross(eye, 0.028 * s).addAcross(eye.add(new Vec3d(end * 0.062 * s, 0, 0)), 0.028 * s);   // its ring out along x
        }
        boxes.add(eyes.box("lifting_eyes"));
        int sensorSide = intakeSide[0] == 0 ? -1 : intakeSide[0];
        Envelope.Points sensors = new Envelope.Points();
        for (Vec3d sensor : List.of(new Vec3d(0.1 * half, 0.12 * deck, sensorSide * (crankcase + 0.002)), new Vec3d(-0.05 * half, 0.22 * deck, sensorSide * (crankcase + 0.002)))) {
            sensors.add(sensor, 0.012 * s).add(sensor.add(new Vec3d(0, 0, sensorSide * 0.028 * s)), 0.012 * s);
        }
        boxes.add(sensors.box("sensors"));
        if (liquid()) {
            // the thermostat's sensor, the hoses from the radiator's tanks to the thermostat and the water pump (as the model bends them)
            double x0 = radiatorX(), yc = radiatorY(), wr = radiatorWidth(), hr = radiatorHeight();
            Vec3d upper = new Vec3d(x0, yc + hr * 0.35, -wr / 2 + 0.03), lower = new Vec3d(x0, yc - hr * 0.35, wr / 2 - 0.03), t = thermostat(), pump = waterPump();
            Envelope.Points hoses = new Envelope.Points();
            hoses.addAll(dev.mcmod.aerocore.api.mesh.MeshBuilder.bezier(upper, upper.add(new Vec3d(-0.08, 0, 0)), t.add(new Vec3d(0.12, 0.04, 0)), t, 12), 0.028 * s);
            hoses.addAll(dev.mcmod.aerocore.api.mesh.MeshBuilder.bezier(lower, lower.add(new Vec3d(-0.08, 0, 0)), pump.add(new Vec3d(0.1, -0.08, 0.05)), pump.add(new Vec3d(0, -0.04, 0.03)), 12), 0.028 * s);
            Vec3d sensor = t.add(new Vec3d(0, 0.02, 0.025));
            hoses.add(sensor, 0.012 * s).add(sensor.add(new Vec3d(0, 0.022 * s, 0)), 0.01 * s);
            boxes.add(hoses.box("hoses"));
        }
        // intake: runners, plenum, throttle body, air box and its hoses (or a vee's duct and cone filter)
        Envelope.Points intake = new Envelope.Points();
        for (int b = 0; b < banks; b++) { for (int i = 0; i < per; i++) { intake.addAll(runner(b, i), runnerRadius()); } }
        if (!individualThrottles()) {
            Plenum p = plenum();
            intake.add(p.rear(), p.radius()).add(p.front(), p.radius());
            if (carburettor()) {
                Vec3d topOf = p.centre().add(new Vec3d(0, p.radius(), 0));
                intake.add(topOf.add(new Vec3d(0, 0.11, 0)), 0.12 * s);
            } else {
                intake.add(p.front().add(new Vec3d(0.05, 0, 0)), throttleRadius() * 1.45);
                intake.addAll(airHose(), throttleRadius() * 1.1).addAll(snorkel(), throttleRadius() * 1.1);
                boxes.add(airBox());
            }
        } else {
            for (int b = 0; b < banks; b++) {
                for (int i = 0; i < per; i++) {
                    List<Vec3d> path = runner(b, i);
                    Vec3d end = path.get(path.size() - 1), dir = end.subtract(path.get(path.size() - 2)).normalized();
                    intake.add(end.add(dir.scale(0.11)), throttleRadius() * 1.85);
                }
            }
        }
        boxes.add(intake.box("intake"));
        // exhaust: headers, collector, down pipe
        Envelope.Points exhaust = new Envelope.Points();
        for (int b = 0; b < banks; b++) {
            for (int i = 0; i < per; i++) { exhaust.addAll(header(b, i), headerRadius()); }
            exhaust.add(merge(b), collectorRadius() * 1.15).add(collectorEnd(b), collectorRadius());
            exhaust.addAll(downPipe(b), collectorRadius() * 0.8);
        }
        boxes.add(exhaust.box("exhaust"));
        // radiator with its tanks and cap, the fan in front of the engine
        if (liquid()) {
            double x0 = radiatorX(), yc = radiatorY(), wr = radiatorWidth(), hr = radiatorHeight();
            boxes.add(new Envelope.Box("radiator", new Vec3d(x0 - 0.045 - 0.04 * s, yc - hr / 2 - 0.01, -wr / 2 - 0.05), new Vec3d(x0 + radiatorDepth() + 0.005, yc + hr / 2 + 0.03, wr / 2 + 0.05)));
        }
        if (e.turbo().fitted()) {
            double tip = Math.clamp(e.turbo().compressorTipM(), 0.02, 0.2), wheel = tip / 2;
            Vec3d axis = out(0, -intakeSide[0]).normalized(), t0 = collectorEnd(0).add(axis.scale(0.01)), c1 = t0.add(axis.scale((1.1 + 0.8 + 1.0) * tip + 0.06));
            Envelope.Points turbo = new Envelope.Points().add(t0, 2.8 * wheel).add(c1, 2.8 * wheel);
            if (e.turbo().intercoolerEffectiveness() > 0) {
                double size = Math.sqrt(Math.max(e.turbo().intercoolerEffectiveness(), 0.1)) * 0.3 * s;
                Vec3d cooler = new Vec3d(front + 0.12, 0.25 * deck, 0);
                turbo.add(cooler.add(new Vec3d(-0.03, -0.6 * size, -1.4 * size)), 0).add(cooler.add(new Vec3d(0.03, 0.6 * size, 1.4 * size)), 0);
            }
            boxes.add(turbo.box("turbo"));
        }
        return new Envelope(boxes, Double.NaN, -front);   // a gearbox's bell bolts to the block's rear face (round the flywheel and the starter's pinion)
    }
}
