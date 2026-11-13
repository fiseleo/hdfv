package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.aero.Airfoil;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.part.HullPart;
import dev.mcmod.aerocore.api.vehicle.part.PlaningHullPart;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.DetailKit;
import dev.mcmod.hdfv.neoforge.client.model.Details;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import dev.mcmod.hdfv.part.BallastDesign;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.ControlSurfaceDesign;
import dev.mcmod.hdfv.part.FloatDesign;
import dev.mcmod.hdfv.part.FuelTankDesign;
import dev.mcmod.hdfv.part.HullDesign;
import dev.mcmod.hdfv.part.MotorDesign;
import dev.mcmod.hdfv.part.PlaningHullDesign;
import dev.mcmod.hdfv.part.PropellerDesign;
import dev.mcmod.hdfv.part.SeatDesign;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * hdfv's looks for the parts AeroCore draws as plain shapes by default (0.12; AeroCore 0.30's registerDefault makes
 * these win): a screw propeller of the Wageningen B-series outline (Kuiper's chord table, its pitch and blade area)
 * in bronze, turning as one piece; a rudder of its NACA 00xx section on its stock; a hull's loft with antifouling
 * under the waterline, a rub rail along the sheer and cleats on the deck; a float's sphere with its lifting eye; a
 * seat from the interior kit; a box as a crate with its edges framed; an electric motor's finned frame, end bells and
 * shaft; ballast as a lead block with a lifting eye; a fuel tank by its kind (a strapped box; a cylinder with domed
 * ends for 700 bar or liquid hydrogen, the cryogenic one jacketed). Looks only: the parts' physics are AeroCore's.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PartLooks {
    private PartLooks() {}

    private static final int BRONZE = 0xffc2955a, RUDDER = 0xff3b3f45, STOCK = 0xffb7bcc2, HULL = 0xffd9d6cc, BOTTOM = 0xff9b3b30, RUB_RAIL = 0xff34383d,
            FLOAT = 0xffe0662a, CRATE = 0xff8a6a43, FRAME = 0xff4a4f55, MOTOR = 0xff2f4f7a, MOTOR_END = 0xff5a6068, SHAFT = 0xffc9cdd2, LEAD = 0xff4b4e53,
            TANK = 0xff3a3f45, STRAP = 0xff1e2024, CAP = 0xffc9a227, CYLINDER = 0xff2b2d31, JACKET = 0xffd8dbde;

    /** B-series chord over diameter times blades over expanded area ratio, at r/R 0.2 to 1 (Kuiper 1992, table 2.1). */
    private static final double[] B_CHORD = {1.662, 1.882, 2.050, 2.152, 2.187, 2.144, 1.970, 1.582, 0.0};

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        Looks.register(PropellerDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { out.piece(1).finish(Finishes.BRONZE); screw((PropellerDesign) part.design(), out); out.pens(null, 0); }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                if (drawn == null) { return; }
                stack.pushPose();
                stack.mulPose(Axis.XP.rotation((float) entity.rotorAngle(firstRotor, partialTick)));
                layers.draw(drawn.piece(1), stack, light);
                stack.popPose();
            }
        });
        Looks.register(ControlSurfaceDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { rudder((ControlSurfaceDesign) part.design(), out); }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                if (drawn == null) { return; }
                ControlSurfaceDesign d = (ControlSurfaceDesign) part.design();
                double angle = Math.clamp(entity.input(partId, "deflection"), -1, 1) * Math.toRadians(d.maxDeflectionDeg());
                stack.pushPose();
                stack.mulPose(Axis.YP.rotation((float) angle));
                layers.draw(drawn.piece(1), stack, light);
                stack.popPose();
            }
        });
        Looks.register(HullDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { hull(out, ((HullPart) part).hull(), Double.NaN); }
        });
        Looks.register(PlaningHullDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                PlaningHullPart p = (PlaningHullPart) part;
                hull(out, p.planing().hull(), p.chineBeamM() / 2 * Math.tan(Math.toRadians(p.deadriseDeg())));
            }
        });
        Looks.register(FloatDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                double r = ((FloatDesign) part.design()).radiusM();
                out.finish(Finishes.PAINT).sphere(r, FLOAT);
                out.finish(Finishes.RUBBER).cylinder(new Vec3d(0, -0.04 * r, 0), new Vec3d(0, 0.04 * r, 0), 1.01 * r, 1.01 * r, 32, RUB_RAIL).finish(null);
                Details.place(out, DetailKit.Part.LIFTING_EYE, new Vec3d(0, r * 0.99, 0), Vec3d.Y, Vec3d.X, Math.max(0.04, 0.25 * r), 0);
            }
        });
        Looks.register(SeatDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                // the seat point is its cushion: the kit's (0.31 up) put there
                out.add(DetailKit.mesh(DetailKit.Part.CAR_SEAT, true), Orientation.IDENTITY, new Vec3d(0.03, -0.27, 0));
            }
        });
        Looks.register(BoxDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { crate(out, ((BoxDesign) part.design()).sizeM()); }
        });
        Looks.register(MotorDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { motor(out, part.mass().massKg()); }
        });
        // 0.25: a storage box by its kind (StorageBoxModel: a roof box's clamshell, a ribbed bed box, a corrugated container with doors)
        Looks.register(dev.mcmod.hdfv.part.StorageBoxDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { dev.mcmod.hdfv.neoforge.client.model.StorageBoxModel.mesh((dev.mcmod.hdfv.part.StorageBoxDesign) part.design(), out); }
        });
        Looks.register(BallastDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                // lead (11 340 kg/m3) as a block twice as long as high, a lifting eye on it
                double v = part.mass().massKg() / 11340, h = Math.cbrt(v / 2), l = 2 * h;
                out.finish(Finishes.CAST_IRON).box(new Vec3d(-l / 2, -h / 2, -h / 2), new Vec3d(l / 2, h / 2, h / 2), LEAD).finish(null);
                Details.place(out, DetailKit.Part.LIFTING_EYE, new Vec3d(0, h / 2, 0), Vec3d.Y, Vec3d.X, Math.max(0.03, 0.4 * h), 0);
            }
        });
    }

    /**
     * 0.22: a rounded rectangle's outline (u across, v up) of half sizes hu, hv, corners of radius r, grown by out on
     * every side; four points a corner, counter-clockwise from the right side's middle.
     */
    static double[][] roundedRect(double hu, double hv, double r, double out) {
        double[][] p = new double[20][];
        double[][] centres = {{hu - r, hv - r}, {-(hu - r), hv - r}, {-(hu - r), -(hv - r)}, {hu - r, -(hv - r)}};
        for (int c = 0; c < 4; c++) {
            for (int k = 0; k < 5; k++) {
                double a = Math.PI / 2 * (c + k / 4.0);
                p[5 * c + k] = new double[]{centres[c][0] + (r + out) * Math.cos(a), centres[c][1] + (r + out) * Math.sin(a)};
            }
        }
        return p;
    }

    /** 0.12: a fuel tank by its kind (BodyRenderers draws it with this). */
    static void tank(FuelTankDesign t, PartMesh out) {
        double l = t.lengthM() / 2, h = t.heightM() / 2, w = t.widthM() / 2;
        if (t.kind() == FuelTankDesign.Kind.ORDINARY) {
            // 0.22: a pressed tank of rounded section (corners 0.4 of its smaller half), two straps round it, a filler neck and cap
            double strap = 0.005, rc = 0.4 * Math.min(h, w);
            out.finish(Finishes.PAINT).extrude(roundedRect(w, h, rc, 0), new Vec3d(-l, 0, 0), Vec3d.Z, Vec3d.Y, new Vec3d(2 * l, 0, 0), TANK, true);
            out.finish(Finishes.RUBBER);
            for (double x : new double[]{-0.6 * l, 0.6 * l}) {
                out.extrude(roundedRect(w, h, rc, strap), new Vec3d(x - 0.02, 0, 0), Vec3d.Z, Vec3d.Y, new Vec3d(0.04, 0, 0), STRAP, true);
            }
            out.finish(Finishes.BRUSHED_STEEL).cylinder(new Vec3d(0.7 * l, h - 0.01, 0), new Vec3d(0.7 * l, h + 0.03, 0), 0.034, 0.03, 16, STRAP);
            out.finish(Finishes.BRASS).cylinder(new Vec3d(0.7 * l, h + 0.03, 0), new Vec3d(0.7 * l, h + 0.05, 0), 0.04, 0.036, 16, CAP).finish(null);
            return;
        }
        // a cylinder along the length with domed ends, as wide as the narrower of width and height
        double r = Math.min(w, h);
        boolean cryo = t.kind() == FuelTankDesign.Kind.CRYOGENIC;
        int n = 12;
        double[] along = new double[n + 3], radius = new double[n + 3];
        double straight = Math.max(l - r, 0);
        for (int i = 0; i <= n + 2; i++) {
            double f;
            if (i == 0) { along[i] = 0; radius[i] = 0; continue; }
            if (i == n + 2) { along[i] = 1; radius[i] = 0; continue; }
            // dome, straight, dome: the profile's points spread over the length
            double s = (double) (i - 1) / n;
            double x = -l + s * 2 * l;
            double dx = Math.abs(x) - straight;
            f = dx > 0 ? Math.sqrt(Math.max(0, r * r - dx * dx)) : r;
            along[i] = (x + l) / (2 * l);
            radius[i] = f;
        }
        out.finish(cryo ? Finishes.BRUSHED_STEEL : Finishes.CARBON_WEAVE).lathe(new Vec3d(-l, 0, 0), new Vec3d(l, 0, 0), along, radius, null, 28, cryo ? JACKET : CYLINDER, false);
        out.finish(Finishes.STAINLESS).cylinder(new Vec3d(l * 0.98, 0, 0), new Vec3d(l + 0.06, 0, 0), 0.025, 0.025, 12, STOCK);
        if (cryo) {
            for (double x : new double[]{-0.5 * straight, 0.5 * straight}) { out.cylinder(new Vec3d(x - 0.012, 0, 0), new Vec3d(x + 0.012, 0, 0), r * 1.02, r * 1.02, 28, FRAME); }
        }
        out.finish(null);
    }

    /**
     * A B-series screw about +X: a hub (a cylinder 1.8 hub radii long, its nose rounded) and its blades, each along +Y
     * before it is turned to its place: Kuiper's chord at each radius scaled to the blade area, the chord at the pitch
     * angle there (atan(P/D / (pi r/R))), a thin lens of a section thinning to the tip.
     */
    private static void screw(PropellerDesign d, PartMesh out) {
        double r = d.diameterM() / 2, hub = Math.max(d.hubRatio(), 0.12) * r;
        out.cylinder(new Vec3d(-0.9 * hub, 0, 0), new Vec3d(0.9 * hub, 0, 0), hub, hub * 0.85, 20, BRONZE);
        out.cylinder(new Vec3d(0.9 * hub, 0, 0), new Vec3d(1.5 * hub, 0, 0), hub * 0.85, 0.05 * hub, 20, BRONZE);
        PartMesh blade = new PartMesh().pens(out.finish(), out.tags());
        int stations = 14, chord = 10;
        Vec3d[][] face = new Vec3d[stations + 1][chord + 1], back = new Vec3d[stations + 1][chord + 1];
        double area = d.areaRatio() / Math.max(d.blades(), 1);
        for (int i = 0; i <= stations; i++) {
            double x = hub / r + (0.995 - hub / r) * i / stations;
            double c = d.diameterM() * area * bChord(x) + 0.004, pitch = Math.atan(d.pitchRatio() / (Math.PI * x));
            double t = d.diameterM() * (0.045 - 0.04 * x) * Math.max(d.thicknessRatio() / 0.045, 0.3);
            Vec3d lead = new Vec3d(Math.sin(pitch), 0, Math.cos(pitch)), normal = new Vec3d(Math.cos(pitch), 0, -Math.sin(pitch));
            for (int k = 0; k <= chord; k++) {
                double f = (double) k / chord, h = 0.5 * t * 4 * f * (1 - f);
                Vec3d base = new Vec3d(0, x * r, 0).add(lead.scale((0.4 - f) * c));
                face[i][k] = base.add(normal.scale(h));
                back[i][k] = base.subtract(normal.scale(0.25 * h));
            }
        }
        for (int i = 0; i < stations; i++) {
            double pitch = Math.atan(d.pitchRatio() / (Math.PI * (hub / r + (0.995 - hub / r) * (i + 0.5) / stations)));
            Vec3d normal = new Vec3d(Math.cos(pitch), 0, -Math.sin(pitch));
            for (int k = 0; k < chord; k++) {
                face(blade, face[i][k], face[i][k + 1], face[i + 1][k + 1], face[i + 1][k], normal, BRONZE);
                face(blade, back[i][k], back[i][k + 1], back[i + 1][k + 1], back[i + 1][k], normal.scale(-1), BRONZE);
            }
        }
        for (int b = 0; b < d.blades(); b++) { out.add(blade, Orientation.rotationVector(Vec3d.X.scale(2 * Math.PI * b / d.blades())), Vec3d.ZERO); }
    }

    /** A quad wound to face the way a direction says (vehicles are drawn with back faces culled). */
    private static void face(PartMesh out, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d outward, int colour) {
        Vec3d f = c.subtract(a).cross(d.subtract(b));
        if (f.lengthSquared() < 1e-20) { f = b.subtract(a).cross(c.subtract(a)); }
        if (f.lengthSquared() < 1e-20) { return; }
        if (f.dot(outward) >= 0) { out.quad(a, b, c, d, colour, f); } else { out.quad(a, d, c, b, colour, f.scale(-1)); }
    }

    private static double bChord(double x) {
        double u = Math.clamp((x - 0.2) / 0.1, 0, B_CHORD.length - 1.000001);
        int i = (int) u;
        return B_CHORD[i] + (B_CHORD[i + 1] - B_CHORD[i]) * (u - i);
    }

    /**
     * A rudder: its NACA 00xx section (the design's thickness) from -0.75 to +0.25 of the chord along x, extruded over
     * its span along y, its ends capped (piece 1: it turns), and its stock rising from the quarter chord.
     */
    private static void rudder(ControlSurfaceDesign d, PartMesh out) {
        double c = d.chordM(), span = d.spanM();
        Airfoil foil = Airfoil.naca4(0, 0, Math.clamp(d.thicknessRatio(), 0.04, 0.3));
        int n = 14;
        List<Vec3d> outline = new ArrayList<>();
        for (int k = 0; k <= n; k++) { double f = 0.5 * (1 - Math.cos(Math.PI * k / n)); outline.add(new Vec3d(0.25 * c - f * c, 0, foil.upperAt(f) * c)); }
        for (int k = n - 1; k >= 1; k--) { double f = 0.5 * (1 - Math.cos(Math.PI * k / n)); outline.add(new Vec3d(0.25 * c - f * c, 0, foil.lowerAt(f) * c)); }
        out.piece(1).finish(Finishes.PAINT);
        int m = outline.size();
        Vec3d lo = new Vec3d(0, -span / 2, 0), hi = new Vec3d(0, span / 2, 0);
        Vec3d centre = new Vec3d(-0.2 * c, 0, 0);
        for (int k = 0; k < m; k++) {
            Vec3d a = outline.get(k), b = outline.get((k + 1) % m);
            Vec3d out1 = a.add(b).scale(0.5).subtract(centre);
            face(out, a.add(lo), b.add(lo), b.add(hi), a.add(hi), new Vec3d(out1.x(), 0, out1.z()), RUDDER);
            face(out, centre.add(hi), a.add(hi), b.add(hi), centre.add(hi), Vec3d.Y, RUDDER);
            face(out, centre.add(lo), a.add(lo), b.add(lo), centre.add(lo), Vec3d.Y.scale(-1), RUDDER);
        }
        out.finish(Finishes.STAINLESS).cylinder(hi, hi.add(new Vec3d(0, 0.12 + 0.15 * c, 0)), 0.04 * c + 0.01, 0.04 * c + 0.01, 12, STOCK);
        out.piece(0).finish(null);
    }

    /**
     * A hull's loft (AeroCore's, its bottom coloured under a height), antifouling below and painted above, with a rub
     * rail along its sheer each side (its widest points near the top of each station) and a cleat each side near the
     * bow and the stern.
     */
    private static void hull(PartMesh out, dev.mcmod.aerocore.api.SlenderHull hull, double bottomBelow) {
        PartMesh loft = new PartMesh().loft(hull, bottomBelow);
        for (MeshBuilder.Quad q : loft.quads()) { out.quads().add(q.withFinish(q.color() == BOTTOM || q.color() == MeshBuilder.BOTTOM ? Finishes.ANTIFOULING : Finishes.PAINT)); }
        // the sheer: at each station (x of the loft's points), the highest of the points near its widest
        java.util.TreeMap<Long, List<Vec3d>> stations = new java.util.TreeMap<>();
        for (MeshBuilder.Quad q : loft.quads()) {
            for (Vec3d v : new Vec3d[]{q.a(), q.b(), q.c(), q.d()}) { stations.computeIfAbsent(Math.round(v.x() * 1000), k -> new ArrayList<>()).add(v); }
        }
        if (stations.size() < 2) { return; }
        for (int side : new int[]{-1, 1}) {
            List<Vec3d> rail = new ArrayList<>();
            for (List<Vec3d> pts : stations.values()) {
                double wide = 0;
                for (Vec3d v : pts) { if (v.z() * side > wide) { wide = v.z() * side; } }
                if (wide < 0.05) { continue; }
                Vec3d best = null;
                for (Vec3d v : pts) { if (v.z() * side > 0.85 * wide && (best == null || v.y() > best.y())) { best = v; } }
                if (best != null) { rail.add(best.add(new Vec3d(0, -0.03, side * 0.012))); }
            }
            if (rail.size() >= 2) { out.finish(Finishes.RUBBER).tube(rail, 0.025, 8, RUB_RAIL).finish(null); }
            if (rail.size() >= 4) {
                for (Vec3d at : new Vec3d[]{rail.get(1), rail.get(rail.size() - 2)}) {
                    Details.place(out, DetailKit.Part.CLEAT, new Vec3d(at.x(), at.y() + 0.03, at.z() - side * 0.12), Vec3d.Y, Vec3d.X, 0.2, 0);
                }
            }
        }
    }

    /** A crate: the box (wood) with its edges framed in angle iron. */
    private static void crate(PartMesh out, Vec3d size) {
        Vec3d h = size.scale(0.5);
        out.finish(Finishes.TEAK).box(h.scale(-1), h, CRATE);
        double f = Math.min(0.03, 0.08 * Math.min(size.x(), Math.min(size.y(), size.z())));
        out.finish(Finishes.FORGED_STEEL);
        for (int sy : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) { out.box(new Vec3d(-h.x() - 0.002, sy * h.y() - f, sz * h.z() - f), new Vec3d(h.x() + 0.002, sy * h.y() + f, sz * h.z() + f), FRAME); }
            for (int sx : new int[]{-1, 1}) { out.box(new Vec3d(sx * h.x() - f, sy * h.y() - f, -h.z() - 0.002), new Vec3d(sx * h.x() + f, sy * h.y() + f, h.z() + 0.002), FRAME); }
        }
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) { out.box(new Vec3d(sx * h.x() - f, -h.y() - 0.002, sz * h.z() - f), new Vec3d(sx * h.x() + f, h.y() + 0.002, sz * h.z() + f), FRAME); }
        }
        out.finish(null);
    }

    /** An electric motor of a mass (4000 kg/m3 overall): a finned frame along +X (its shaft), end bells, the shaft, feet. */
    private static void motor(PartMesh out, double massKg) {
        double r = dev.mcmod.hdfv.look.Envelopes.motorRadius(massKg), l = 3 * r;   // 0.14: the envelope's radius
        out.finish(Finishes.PAINT).cylinder(new Vec3d(-l / 2, 0, 0), new Vec3d(l / 2, 0, 0), r, r, 24, MOTOR);
        // cooling fins round the frame
        for (int k = 0; k < 12; k++) {
            double a = 2 * Math.PI * k / 12;
            out.add(new PartMesh().box(new Vec3d(-0.4 * l, r * 0.95, -0.006), new Vec3d(0.4 * l, r * 1.12, 0.006), MOTOR), Orientation.rotationVector(Vec3d.X.scale(a)), Vec3d.ZERO);
        }
        out.finish(Finishes.CAST_ALUMINIUM);
        out.cylinder(new Vec3d(l / 2, 0, 0), new Vec3d(l / 2 + 0.15 * r, 0, 0), r * 0.95, r * 0.6, 24, MOTOR_END);
        out.cylinder(new Vec3d(-l / 2, 0, 0), new Vec3d(-l / 2 - 0.15 * r, 0, 0), r * 0.95, r * 0.6, 24, MOTOR_END);
        out.finish(Finishes.MACHINED_STEEL).cylinder(new Vec3d(l / 2 + 0.15 * r, 0, 0), new Vec3d(l / 2 + 0.6 * r, 0, 0), 0.18 * r, 0.18 * r, 12, SHAFT);
        out.finish(Finishes.PAINT);
        for (int s : new int[]{-1, 1}) { out.box(new Vec3d(-0.4 * l, -r * 1.1, s * 0.7 * r - 0.1 * r), new Vec3d(0.4 * l, -r * 0.8, s * 0.7 * r + 0.1 * r), MOTOR); }
        out.finish(null);
    }
}
