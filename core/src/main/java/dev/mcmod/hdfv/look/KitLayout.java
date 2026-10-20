package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.body.RoadAero;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyKitDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.16: where a car body's kit (BodyKitDesign) goes, from the body's shape (body frame: x from the rear end, y up, z
 * across): the pieces drawn (BodyModel), their area and mass (moulded pieces of the kit's material, KIT_THICKNESS_M, or
 * half that for metal), and the elements AeroCore's RoadAero takes for their air loads. Everything about the kit's
 * geometry is here, so the model, the mass, the manufacture and the physics agree.
 * <ul>
 * <li>Front: an air dam hanging under the nose's lower edge (round its tip and back along its sides to clear of the front
 *   wheels), its splitter standing out from the dam's foot; a sport bumper's chin lip on the same edge (looks); an
 *   off-road bull bar's tubes BULL_BAR_M ahead of the nose.</li>
 * <li>Sides: skirts between the arches under the sill's edge; flares round each arch, standing out from the skin there
 *   and blending back into it (BodyLayout's axles and the arch radius).</li>
 * <li>Rear: the diffuser from the rear end forward under the floor, a lip on the deck's edge, a wing with its trailing
 *   edge over the rear end, on two uprights at a third of its span.</li>
 * <li>Roof: bars across the flat of the roof (or a basket on them), a scoop on the hood's middle rising from flush at
 *   its back to its mouth, exhaust tips under the rear end.</li>
 * </ul>
 * The pieces that follow the body (the dam, splitter, lip, skirts, flares, scoop) are Panels on its skin (BodySurface),
 * so they stay on it whatever the nose's bluntness, the hood's length or the section; the rest are boxes.
 */
public final class KitLayout {
    /** A box-shaped piece: what it is (for its look), its corners. */
    public record Box(String role, Vec3d min, Vec3d max) {
        public double areaM2() {
            Vec3d d = max.subtract(min);
            return 2 * (d.x() * d.y() + d.y() * d.z() + d.z() * d.x());
        }
        public Vec3d centre() { return min.add(max).scale(0.5); }
    }
    /** A flare round an arch: its centre (the hub's height), radius, how far it stands out, which side (-1 port). */
    public record Flare(Vec3d centre, double radiusM, double outM, int side) {}
    /** The wing: its quarter chord's middle, span, chord, angle (downforce), end plates' height, the deck under it. */
    public record Wing(Vec3d quarterChord, double spanM, double chordM, double angleDeg, double endplateM, double deckY) {}
    /** The diffuser: from the rear end to frontX under the floor at floorY, widthM across, its ramp's angle. */
    public record Diffuser(double rearX, double frontX, double floorY, double widthM, double angleDeg) {}
    /** An exhaust tip's mouth and diameter. */
    public record Tip(Vec3d at, double diameterM) {}
    /**
     * A sheet on the body's skin (0.16 follow-up): its quads, corners in order round each (a sheet is seen from both
     * sides). What it is gives its look: dam, splitter, lip, skirt, flare, scoop, scoop_mouth.
     */
    public record Panel(String role, List<Vec3d[]> quads) {
        public double areaM2() {
            double a = 0;
            for (Vec3d[] q : quads) { a += 0.5 * q[2].subtract(q[0]).cross(q[3].subtract(q[1])).length(); }
            return a;
        }
        public Vec3d centre() {
            Vec3d sum = Vec3d.ZERO;
            int n = 0;
            for (Vec3d[] q : quads) { for (Vec3d p : q) { sum = sum.add(p); n++; } }
            return n == 0 ? Vec3d.ZERO : sum.scale(1.0 / n);
        }
    }

    /** The pieces' wall, m (moulded composite or plastic; metal half as thick); a road car's floor height for the wing's ground effect, m. */
    public static final double KIT_THICKNESS_M = 0.003, RIDE_M = 0.15;
    /** The air dam's and splitter's place: under the nose's last FRONT_M; a bull bar BULL_BAR_M ahead of it; roof bars' diameter. */
    public static final double FRONT_M = 0.3, BULL_BAR_M = 0.12, BAR_M = 0.03;

    public final List<Box> boxes;
    public final List<Panel> panels;
    public final List<Flare> flares;
    public final Wing wing;
    public final Diffuser diffuser;
    public final List<Tip> tips;
    public final RoadAero aero;
    public final double areaM2, massKg;
    public final Vec3d centre;

    private KitLayout(List<Box> boxes, List<Panel> panels, List<Flare> flares, Wing wing, Diffuser diffuser, List<Tip> tips, RoadAero aero, double areaM2, double massKg, Vec3d centre) {
        this.boxes = List.copyOf(boxes); this.panels = List.copyOf(panels); this.flares = List.copyOf(flares); this.wing = wing; this.diffuser = diffuser; this.tips = List.copyOf(tips);
        this.aero = aero; this.areaM2 = areaM2; this.massKg = massKg; this.centre = centre;
    }

    public boolean empty() { return boxes.isEmpty() && panels.isEmpty() && flares.isEmpty() && wing == null && diffuser == null && tips.isEmpty(); }

    public static KitLayout of(BodyDesign d) { return of(d, d.shape()); }

    public static KitLayout of(BodyDesign d, BodyShape shape) {
        BodyKitDesign k = d.kit();
        List<Box> boxes = new ArrayList<>();
        List<Panel> panels = new ArrayList<>();
        List<Flare> flares = new ArrayList<>();
        List<Tip> tips = new ArrayList<>();
        List<RoadAero.Element> aero = new ArrayList<>();
        List<double[]> weighed = new ArrayList<>();   // {area, x, y, z}
        if (k.none()) { return new KitLayout(boxes, panels, flares, null, null, tips, null, 0, 0, Vec3d.ZERO); }
        double len = shape.lengthM(), bottom = Double.POSITIVE_INFINITY;
        for (BodyShape.Station st : shape.stations()) { bottom = Math.min(bottom, st.bottomM()); }
        double front = Math.max(len - FRONT_M, 0.5 * len), wFront = shape.widthAt(front), wMid = shape.widthAt(0.5 * len), wRear = shape.widthAt(Math.min(0.3, 0.5 * len));
        BodySurface s = new BodySurface(shape);
        List<Vec3d> axles = BodyLayout.axles(d, shape);
        double r = d.settings().archRadiusM();
        // front: the dam, splitter and lip on the nose's lower edge, round its tip and back to clear of the front wheels
        if (k.damM() > 0 || k.splitterM() > 0 || k.bumper() == BodyKitDesign.Bumper.SPORT) {
            double xBack = axles.isEmpty() ? s.front() - 0.4 : axles.get(0).x() + r + 0.04;
            Edge edge = Edge.of(d, s, Math.clamp(xBack, s.front() - 1.0, s.front() - 0.1));
            // 0.18: under the front's bumper (FrontLayout), which stands out from the skin along the same edge
            FrontLayout.Face nose = FrontLayout.face(d, s);
            Edge e = nose != null && nose.bumper() ? edge.pushed(nose.bumperOut()) : edge;
            if (k.damM() > 0) {
                panels.add(new Panel("dam", e.strip(i -> e.at(i).add(e.out(i).scale(-0.01)).add(Vec3d.Y.scale(0.02)), i -> e.at(i).add(Vec3d.Y.scale(-k.damM())))));
            }
            if (k.splitterM() > 0) {
                double foot = k.damM() > 0 ? k.damM() : 0.004;
                panels.add(new Panel("splitter", e.strip(i -> e.at(i).add(Vec3d.Y.scale(-foot)).add(e.out(i).scale(-0.02)),
                        i -> e.at(i).add(Vec3d.Y.scale(-foot)).add(e.out(i).scale(k.splitterM())))));
                // its front edge, 8 mm thick
                panels.add(new Panel("splitter", e.strip(i -> e.at(i).add(Vec3d.Y.scale(-foot)).add(e.out(i).scale(k.splitterM())),
                        i -> e.at(i).add(Vec3d.Y.scale(-foot - 0.008)).add(e.out(i).scale(k.splitterM())))));
            }
            if (k.bumper() == BodyKitDesign.Bumper.SPORT) {
                panels.add(new Panel("lip", e.strip(i -> e.at(i).add(Vec3d.Y.scale(0.03)).add(e.out(i).scale(-0.005)), i -> e.at(i).add(e.out(i).scale(0.03)).add(Vec3d.Y.scale(-0.012)))));
            }
        }
        double barY = bottom + 0.25 * (shape.topAt(len - 0.1) - bottom);
        if (k.bumper() == BodyKitDesign.Bumper.OFFROAD) {
            FrontLayout.Face nose = FrontLayout.face(d, s);   // 0.18: ahead of the front's rolled edge and its bumper
            double x = len + (nose == null ? 0 : nose.roll() + nose.bumperOut()) + BULL_BAR_M, half = 0.4 * wFront, h = Math.max(0.4, 0.6 * (shape.topAt(len - 0.1) - bottom));
            boxes.add(new Box("bar", new Vec3d(x - 0.03, barY, -half), new Vec3d(x + 0.03, barY + 0.06, half)));
            boxes.add(new Box("bar", new Vec3d(x - 0.03, barY + h - 0.06, -0.6 * half), new Vec3d(x + 0.03, barY + h, 0.6 * half)));
            for (int side : new int[]{-1, 1}) {
                boxes.add(new Box("bar", new Vec3d(x - 0.03, barY, side * 0.6 * half - 0.03), new Vec3d(x + 0.03, barY + h, side * 0.6 * half + 0.03)));
                boxes.add(new Box("bar", new Vec3d(len - 0.05, barY + 0.1, side * 0.6 * half - 0.025), new Vec3d(x, barY + 0.15, side * 0.6 * half + 0.025)));
            }
            aero.add(new RoadAero.Bluff(new Vec3d(x, barY + 0.5 * h, 0), 0.06 * (2 * half + 2 * h) , RoadAero.CD_TUBE));
        }
        // sides: the skirts under the sill's edge between the arches, the flares round the arches on the skin
        double skirtLength = 0;
        if (k.skirtM() > 0 && axles.size() >= 2) {
            double x0 = axles.get(axles.size() - 1).x() + r + 0.02, x1 = axles.get(0).x() - r - 0.02;
            if (x1 > x0) {
                skirtLength = x1 - x0;
                int n = Math.max(2, (int) Math.ceil((x1 - x0) / 0.25));
                for (int side : new int[]{-1, 1}) {
                    List<Vec3d[]> quads = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        double xa = x0 + (x1 - x0) * i / n, xb = x0 + (x1 - x0) * (i + 1) / n;
                        Vec3d ta = sill(s, xa, side), tb = sill(s, xb, side);
                        Vec3d ba = new Vec3d(xa, bottom - k.skirtM(), ta.z()), bb = new Vec3d(xb, bottom - k.skirtM(), tb.z());
                        quads.add(new Vec3d[]{ta.add(Vec3d.Y.scale(0.03)), tb.add(Vec3d.Y.scale(0.03)), bb, ba});
                    }
                    panels.add(new Panel("skirt", quads));
                }
            }
        }
        if (k.flareM() > 0) {
            for (Vec3d a : axles) {
                for (int side : new int[]{-1, 1}) {
                    flares.add(new Flare(new Vec3d(a.x(), a.y(), side * 0.5 * shape.widthAt(a.x())), r, k.flareM(), side));
                    panels.add(new Panel("flare", flare(s, a, r, k.flareM(), side)));
                }
            }
            aero.add(new RoadAero.Bluff(new Vec3d(0.5 * len, bottom + r, 0), 2 * axles.size() * k.flareM() * r, RoadAero.CD_SHIELDED));
        }
        // rear
        Diffuser diffuser = null;
        if (k.diffuser()) {
            double l = Math.min(k.diffuserLengthM(), 0.4 * len);
            diffuser = new Diffuser(0, l, bottom, 0.8 * wRear, k.diffuserDeg());
        }
        double deckX = 0.03, deck = shape.topAt(deckX);
        if (k.spoilerM() > 0) {
            boxes.add(new Box("spoiler", new Vec3d(0, deck, -0.45 * wRear), new Vec3d(0.04, deck + k.spoilerM(), 0.45 * wRear)));
            aero.add(new RoadAero.Fence(new Vec3d(0.02, deck + 0.5 * k.spoilerM(), 0), 0.9 * wRear, k.spoilerM()));
        }
        Wing wing = null;
        if (k.wing()) {
            double c = k.wingChordM(), xq = 0.05 + 0.75 * c, under = shape.topAt(Math.min(xq, len)), y = under + k.wingHeightM();
            wing = new Wing(new Vec3d(xq, y, 0), k.wingSpanM(), c, k.wingAngleDeg(), k.endplateM(), under);
            for (int side : new int[]{-1, 1}) {
                double z = side * k.wingSpanM() / 3;
                boxes.add(new Box("upright", new Vec3d(xq - 0.3 * c, under, z - 0.01), new Vec3d(xq + 0.2 * c, y, z + 0.01)));
            }
            aero.add(new RoadAero.RearWing(wing.quarterChord(), k.wingSpanM(), c, k.wingAngleDeg(), BodyKitDesign.WING_CAMBER, BodyKitDesign.WING_THICKNESS,
                    k.endplateM(), y - bottom + RIDE_M));
        }
        // roof, hood, exhaust
        if (k.roofRack() != BodyKitDesign.RoofRack.NONE) {
            double top = Double.NEGATIVE_INFINITY;
            for (BodyShape.Station st : shape.stations()) { top = Math.max(top, st.topM()); }
            double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY;
            for (BodyShape.Station st : shape.stations()) {
                if (st.topM() > top - 0.04) { x0 = Math.min(x0, st.xM()); x1 = Math.max(x1, st.xM()); }
            }
            if (x1 - x0 < 0.4) { double m = 0.5 * (x0 + x1); x0 = m - 0.2; x1 = m + 0.2; }
            double w = 0.85 * shape.widthAt(0.5 * (x0 + x1)), y = top + 0.06;
            for (double x : new double[]{x0 + 0.1, x1 - 0.1}) {
                boxes.add(new Box("bar", new Vec3d(x - BAR_M / 2, y, -w / 2), new Vec3d(x + BAR_M / 2, y + BAR_M, w / 2)));
                for (int side : new int[]{-1, 1}) { boxes.add(new Box("foot", new Vec3d(x - 0.04, top, side * w / 2 - 0.03), new Vec3d(x + 0.04, y, side * w / 2 + 0.03))); }
            }
            double frontal = 2 * BAR_M * w;
            if (k.roofRack() == BodyKitDesign.RoofRack.BASKET) {
                double bh = 0.15;
                for (int side : new int[]{-1, 1}) {
                    boxes.add(new Box("basket", new Vec3d(x0 + 0.05, y + BAR_M, side * 0.45 * w - 0.015), new Vec3d(x1 - 0.05, y + BAR_M + bh, side * 0.45 * w + 0.015)));
                }
                for (double x : new double[]{x0 + 0.05, x1 - 0.05}) {
                    boxes.add(new Box("basket", new Vec3d(x - 0.015, y + BAR_M, -0.45 * w), new Vec3d(x + 0.015, y + BAR_M + bh, 0.45 * w)));
                }
                boxes.add(new Box("basket_floor", new Vec3d(x0 + 0.05, y + BAR_M, -0.45 * w), new Vec3d(x1 - 0.05, y + BAR_M + 0.01, 0.45 * w)));
                frontal += 0.5 * bh * 0.9 * w;
            }
            aero.add(new RoadAero.Bluff(new Vec3d(0.5 * (x0 + x1), y, 0), frontal, k.roofRack() == BodyKitDesign.RoofRack.BASKET ? RoadAero.CD_BLUFF : RoadAero.CD_TUBE));
        }
        if (k.scoopM() > 0) {
            double hoodX = 0.5 * (shape.moduleFrontX(0) + shape.moduleRearX(0)), hood = shape.topAt(hoodX), w = Math.min(0.5, 0.3 * shape.widthAt(hoodX));
            scoop(s, hoodX - 0.25, hoodX + 0.15, w, k.scoopM(), panels);
            aero.add(new RoadAero.Bluff(new Vec3d(hoodX, hood + 0.5 * k.scoopM(), 0), w * k.scoopM(), RoadAero.CD_SHIELDED));
        }
        for (int i = 0; i < k.exhaustTips(); i++) {
            int n = k.exhaustTips();
            double z = n == 1 ? 0.3 * wRear : (i < (n + 1) / 2 ? -1 : 1) * (0.3 * wRear - (i % ((n + 1) / 2)) * 1.3 * k.exhaustDiameterM());
            tips.add(new Tip(new Vec3d(-0.03, bottom + 0.6 * k.exhaustDiameterM() + 0.04, z), k.exhaustDiameterM()));
        }
        // the floor: what the dam, splitter, skirts and diffuser do to it. The skirts close their share of the sides'
        // gap only between the arches: the wheel arches and overhangs still leak (0.16.1, AeroCore 0.35.1's leak area).
        // A diffuser implies the kit's undertray (smooth; AeroCore takes a road car's, its exhaust tunnel and suspension open).
        if (k.damM() > 0 || k.splitterM() > 0 || k.skirtM() > 0 || diffuser != null) {
            double sealing = k.sealing() * Math.clamp(skirtLength / front, 0, 1);
            aero.add(new RoadAero.Underbody(front, 0, 0.8 * wMid, bottom, diffuser == null ? 0 : diffuser.frontX(), diffuser == null ? 0 : k.diffuserDeg(),
                    sealing, k.damM(), k.splitterM(), k.splitterM() > 0 ? 0.9 * wFront : 0, diffuser != null));
        }
        // area and mass: boxes' surfaces, flares, the wing and its plates, the diffuser and its strakes, the tips
        for (Box b : boxes) { weighed.add(new double[]{b.role().equals("bar") || b.role().equals("basket") || b.role().equals("foot") ? 0.8 * b.areaM2() : 0.5 * b.areaM2(),
                b.centre().x(), b.centre().y(), b.centre().z()}); }
        for (Panel p : panels) { Vec3d c = p.centre(); weighed.add(new double[]{p.areaM2(), c.x(), c.y(), c.z()}); }
        if (wing != null) {
            weighed.add(new double[]{2 * wing.spanM() * wing.chordM() + 2 * 2 * wing.chordM() * wing.endplateM(), wing.quarterChord().x(), wing.quarterChord().y(), 0});
        }
        if (diffuser != null) {
            double l = diffuser.frontX() - diffuser.rearX();
            weighed.add(new double[]{diffuser.widthM() * l * 1.4, 0.5 * l, bottom, 0});
        }
        for (Tip t : tips) { weighed.add(new double[]{Math.PI * t.diameterM() * 0.2, t.at().x(), t.at().y(), t.at().z()}); }
        double area = 0, ax = 0, ay = 0, az = 0;
        for (double[] w : weighed) { area += w[0]; ax += w[0] * w[1]; ay += w[0] * w[2]; az += w[0] * w[3]; }
        double t = k.material().densityKgPerM3() > 3000 ? 0.5 * KIT_THICKNESS_M : KIT_THICKNESS_M;
        double kg = area * t * k.material().densityKgPerM3();
        Vec3d centre = area > 0 ? new Vec3d(ax / area, ay / area, az / area) : Vec3d.ZERO;
        return new KitLayout(boxes, panels, flares, wing, diffuser, tips, aero.isEmpty() ? null : new RoadAero(aero), area, kg, centre);
    }

    /** How far up the skin's rounded lower corner the kit's edge pieces hang from, m (on the corner, not under the floor). */
    static final double EDGE_UP_M = 0.05;
    /** How far a flare reaches back over the skin from the arch's edge, m. */
    static final double FLARE_REACH_M = 0.09;

    /** The skin's point on one side (-1 port) at x and height y. */
    static Vec3d skin(BodySurface s, double x, double y, int side) {
        Vec3d p = s.point(x, s.portAngle(x, y));
        return new Vec3d(p.x(), p.y(), side * Math.abs(p.z()));
    }

    /** The sill's edge on one side at x: the skin EDGE_UP_M up the lower corner. */
    static Vec3d sill(BodySurface s, double x, int side) { return skin(s, x, s.bottom(x) + EDGE_UP_M, side); }

    /**
     * The nose's lower edge, port end to starboard end through the tip: back along each side from xBack on the sill's
     * edge (sill), and across the tip on its rolled cap's lower front (half way round the roll: BodyModel's caps). Each
     * point's outward direction is level and away from the nose's middle.
     */
    record Edge(List<Vec3d> points, List<Vec3d> outs) {
        Vec3d at(int i) { return points.get(i); }
        Vec3d out(int i) { return outs.get(i); }

        static Edge of(BodyDesign d, BodySurface s, double xBack) {
            int last = s.xs.length - 1;
            BodyShape.Station tip = s.stations.get(last);
            double roll = LampLayout.rollOver(d, tip), xTip = s.front();
            int n = 8;
            List<Vec3d> port = new ArrayList<>();
            for (int i = n; i >= 0; i--) {
                double u = (double) i / n, x = xTip - (xTip - xBack) * u * u;   // closer together near the tip
                port.add(sill(s, x, -1));
            }
            double yFace = tip.bottomM() + 0.3 * roll, xFace = xTip + 0.7 * roll;
            double zFace = Math.max(Math.abs(s.point(xTip, s.portAngle(xTip, yFace + EDGE_UP_M)).z()) - 0.3 * roll, 0.02);
            List<Vec3d> points = new ArrayList<>(port);
            int across = 6;
            for (int j = 0; j <= across; j++) {
                double z = -zFace + 2 * zFace * j / across;
                points.add(new Vec3d(xFace, yFace, z));
            }
            for (int i = port.size() - 1; i >= 0; i--) { Vec3d p = port.get(i); points.add(new Vec3d(p.x(), p.y(), -p.z())); }
            List<Vec3d> outs = new ArrayList<>(points.size());
            for (int i = 0; i < points.size(); i++) {
                Vec3d a = points.get(Math.max(i - 1, 0)), b = points.get(Math.min(i + 1, points.size() - 1));
                Vec3d t = new Vec3d(b.x() - a.x(), 0, b.z() - a.z());
                Vec3d o = new Vec3d(t.z(), 0, -t.x());   // level, to the left of the way round (port end to starboard end): outward
                if (o.lengthSquared() < 1e-12) { o = new Vec3d(1, 0, 0); }
                o = o.normalized();
                outs.add(o);
            }
            return new Edge(points, outs);
        }

        /** The same edge moved out by a distance, level (round a bumper standing out from the skin). */
        Edge pushed(double by) {
            List<Vec3d> moved = new ArrayList<>(points.size());
            for (int i = 0; i < points.size(); i++) { moved.add(points.get(i).add(outs.get(i).scale(by))); }
            return new Edge(moved, outs);
        }

        /** The quads between two lines along the edge, each point's place on them given. */
        List<Vec3d[]> strip(java.util.function.IntFunction<Vec3d> upper, java.util.function.IntFunction<Vec3d> lower) {
            List<Vec3d[]> quads = new ArrayList<>();
            for (int i = 0; i + 1 < points.size(); i++) { quads.add(new Vec3d[]{upper.apply(i), upper.apply(i + 1), lower.apply(i + 1), lower.apply(i)}); }
            return quads;
        }
    }

    /**
     * A flare round one arch on one side: on the arch's upper half, its lip standing outM out from the skin at the arch's
     * edge, its face sloping back into the skin FLARE_REACH_M further out, so it grows out of the body there.
     */
    static List<Vec3d[]> flare(BodySurface s, Vec3d axle, double radius, double outM, int side) {
        List<Vec3d[]> quads = new ArrayList<>();
        int n = 14;
        Vec3d[] lip = new Vec3d[n + 1], face = new Vec3d[n + 1], under = new Vec3d[n + 1];
        boolean[] on = new boolean[n + 1];
        for (int k = 0; k <= n; k++) {
            double a = Math.PI * k / n, c = Math.cos(a), sn = Math.sin(a);
            double x0 = axle.x() + (radius + 0.01) * c, y0 = axle.y() + (radius + 0.01) * sn;
            double x1 = axle.x() + (radius + FLARE_REACH_M) * c, y1 = axle.y() + (radius + FLARE_REACH_M) * sn;
            on[k] = x0 > s.rear() && x0 < s.front() && x1 > s.rear() && x1 < s.front() && y0 > s.bottom(x0) + 0.01 && y0 < s.top(x0);
            if (!on[k]) { continue; }
            Vec3d edge = skin(s, x0, y0, side), back = skin(s, x1, y1, side);
            lip[k] = edge.add(new Vec3d(0, 0, side * outM));
            under[k] = edge.add(new Vec3d(0, 0, -side * 0.01));
            face[k] = back.add(new Vec3d(0, 0, -side * 0.005));
        }
        for (int k = 0; k < n; k++) {
            if (!on[k] || !on[k + 1]) { continue; }
            quads.add(new Vec3d[]{lip[k], lip[k + 1], face[k + 1], face[k]});
            quads.add(new Vec3d[]{under[k], under[k + 1], lip[k + 1], lip[k]});
        }
        return quads;
    }

    /** The skin's height on top at x, |z| across (the hood's): the upper quarter of the port side searched. */
    static double top(BodySurface s, double x, double z) {
        double lo = Math.PI / 2, hi = Math.PI;
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (lo + hi);
            if (Math.abs(s.point(x, mid).z()) > Math.abs(z)) { lo = mid; } else { hi = mid; }
        }
        return s.point(x, 0.5 * (lo + hi)).y();
    }

    /** A hood scoop from x0 (flush) to x1 (its mouth, heightM up), widthM across, its base on the hood's skin. */
    static void scoop(BodySurface s, double x0, double x1, double widthM, double heightM, List<Panel> panels) {
        int nx = 8, nz = 6;
        Vec3d[][] base = new Vec3d[nx + 1][nz + 1], top = new Vec3d[nx + 1][nz + 1];
        for (int i = 0; i <= nx; i++) {
            double x = x0 + (x1 - x0) * i / nx, rise = heightM * i / nx;
            for (int j = 0; j <= nz; j++) {
                double z = -0.5 * widthM + widthM * j / nz, y = top(s, x, z);
                // the top's edges rounded down to the base, so the sides grow out of the hood
                double edge = Math.sin(Math.PI * j / nz);
                base[i][j] = new Vec3d(x, y - 0.004, z);
                top[i][j] = new Vec3d(x, y + rise * Math.sqrt(Math.max(edge, 0.25)), z);
            }
        }
        List<Vec3d[]> skin = new ArrayList<>(), mouth = new ArrayList<>();
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) { skin.add(new Vec3d[]{top[i][j], top[i][j + 1], top[i + 1][j + 1], top[i + 1][j]}); }
            skin.add(new Vec3d[]{base[i][0], top[i][0], top[i + 1][0], base[i + 1][0]});
            skin.add(new Vec3d[]{base[i][nz], base[i + 1][nz], top[i + 1][nz], top[i][nz]});
        }
        for (int j = 0; j < nz; j++) { mouth.add(new Vec3d[]{base[nx][j], base[nx][j + 1], top[nx][j + 1], top[nx][j]}); }
        panels.add(new Panel("scoop", skin));
        panels.add(new Panel("scoop_mouth", mouth));
    }
}
