package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.PackedMesh;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.part.DrumPart;
import dev.mcmod.aerocore.api.vehicle.part.TrackPart;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.ChassisModel;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import dev.mcmod.hdfv.neoforge.worksite.TowerCraneBlockEntity;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.machine.DrumDesign;
import dev.mcmod.hdfv.part.machine.EngineHoodDesign;
import dev.mcmod.hdfv.part.machine.HookDesign;
import dev.mcmod.hdfv.part.machine.HydraulicMotorDesign;
import dev.mcmod.hdfv.part.machine.HydraulicPumpDesign;
import dev.mcmod.hdfv.part.machine.LinkDesign;
import dev.mcmod.hdfv.part.machine.MachineRules;
import dev.mcmod.hdfv.part.machine.OperatorCabDesign;
import dev.mcmod.hdfv.part.machine.OutriggerDesign;
import dev.mcmod.hdfv.part.machine.TrackDesign;
import dev.mcmod.hdfv.part.machine.WorkToolDesign;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * 0.26: the heavy machinery's parts, in construction-machine yellow with black running gear and steel wear parts. The
 * shapes were laid out in Blender (MCP for Blender, at the samples' sizes) and are built here from each design's own
 * numbers:
 * <ul>
 * <li>Booms, sticks and arms as welded boxes along stations (a mono boom bent at its knee, a stick with its tail behind
 *     the pin for its ram), round pin bosses at the ends, a bracket out to the ram's rod pin, a boom's hydraulic lines
 *     along its top; a lattice boom as four chords and their lacing; a mast as two uprights and their ties.</li>
 * <li>The hydraulic rams of rammed links and tools (not drawn before 0.26.0's looks): each a barrel from its base pin
 *     (on what the part is mounted on) and a chrome rod from its rod pin (on the part), moving pieces placed every frame
 *     from the joint's value, with the base pin's bracket drawn in the mount's frame (a strut square to the mount's
 *     axis: a boom's foot bracket, a stick's lug). Several rams of one joint stand side by side across a link (a boom's
 *     pair) or mirrored across it (steering rams).</li>
 * <li>Buckets on their strike plane (WorkToolDesign.chord): the wrapper as the parabola the capacity is rated on, a
 *     thicker lip, side plates, teeth with adapters, lugs up to the hinge and the ram's pin; clamshells as two half
 *     shells. Blades as a curved moldboard with its cutting edge, end bits, wings (SU, U), ribs and push-arm trunnions.
 *     A dump body with its tail chute, sloped front, canopy, ribs, top rails and sub-frame.</li>
 * <li>Crawler tracks: shoes (triple or single grousers, or a rubber belt's lugs) round the hull of the idler, the
 *     sprocket, the road wheels and the carriers, double-flanged rollers, a toothed sprocket with its final drive, the
 *     track frames and the car body. A roller's drum in its yoke with rubber mounts and scrapers, padfeet on a padfoot
 *     shell. A hook block's side plates and sheaves, its crosshead and a forged hook (single or ramshorn).</li>
 * <li>The tower crane's superstructure (TowerCraneBlockEntity's cab vehicle, boxes told by name): the slewing unit, a
 *     triangular lattice jib with its trolley (a moving piece at the trolley's radius the controller sends), the
 *     counter-jib's walkway, handrails, hoist winch and cabinet, ballast slabs and the glazed cab.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MachineLooks {
    private MachineLooks() {}

    private static final int YELLOW = 0xffe0a82e, BLACK = 0xff1f2023, STEEL = 0xff8c9096, WEAR = 0xff5c5f63, HOSE = 0xff151515, RED = 0xffb3261e,
            DARK = 0xff2b2d31, RUBBER = 0xff18191b, SILVER = 0xffc4c8cc, CHROME = 0xffe2e5e8, CONCRETE = 0xff9c9b95, WHITE = 0xffd9dbdc,
            GLASS = 0x5a283846, CABINET = 0xff8f969c, ALU = 0xffb3b8be;
    private static final Vec3d X = Vec3d.X, Y = Vec3d.Y, Z = Vec3d.Z;

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        Looks.register(TrackDesign.class, mesh((part, out) -> track((TrackDesign) part.design(), out)));
        Looks.register(DrumDesign.class, mesh((part, out) -> drum((DrumDesign) part.design(), out)));
        Looks.register(HydraulicPumpDesign.class, mesh((part, out) -> pump((HydraulicPumpDesign) part.design(), out)));
        Looks.register(HydraulicMotorDesign.class, mesh((part, out) -> motor((HydraulicMotorDesign) part.design(), out)));
        Looks.register(LinkDesign.class, rammed((part, out) -> link((LinkDesign) part.design(), out), part -> rams((LinkDesign) part.design())));
        Looks.register(HookDesign.class, mesh((part, out) -> hook((HookDesign) part.design(), out)));
        Looks.register(OutriggerDesign.class, mesh((part, out) -> outrigger((OutriggerDesign) part.design(), out)));
        Looks.register(WorkToolDesign.class, rammed((part, out) -> tool((WorkToolDesign) part.design(), out), part -> rams((WorkToolDesign) part.design())));
        Looks.register(OperatorCabDesign.class, mesh((part, out) -> operatorCab((OperatorCabDesign) part.design(), out)));
        Looks.register(EngineHoodDesign.class, mesh((part, out) -> engineHood((EngineHoodDesign) part.design(), out)));
    }

    private interface Mesher { void mesh(Part part, PartMesh out); }

    private static PartRenderer mesh(Mesher m) {
        return new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { m.mesh(part, out); out.pens(null, 0); }
        };
    }

    /** A part with rams: its mesh, then the rams' pieces (1 barrel, 2 rod, 3 the base pins' brackets), placed each frame. */
    private static PartRenderer rammed(Mesher m, Function<Part, Rams> solve) {
        // kept by the compiled part (an identity key: a tool's ram geometry is solved once, not every frame)
        Map<Part, Optional<Rams>> cache = Collections.synchronizedMap(new WeakHashMap<>());
        Function<Part, Rams> rams = part -> cache.computeIfAbsent(part, p -> Optional.ofNullable(solve.apply(p))).orElse(null);
        return new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                m.mesh(part, out);
                Rams r = rams.apply(part);
                if (r != null) { ramPieces(r, out); }
                out.pens(null, 0);
            }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                Rams r = rams.apply(part);
                if (r != null) { drawRams(r, entity, partId, stack, layers, light, drawn); }
            }
        };
    }

    private static Vec3d v(double x, double y, double z) { return new Vec3d(x, y, z); }

    // ---------------------------------------------------------------- shared shapes

    /** A welded box along stations {x, centre y, half depth, half width} in the x-y plane. */
    static void section(PartMesh out, double[][] st, int colour) {
        int n = st.length;
        Vec3d[][] rings = new Vec3d[n][];
        for (int i = 0; i < n; i++) {
            double[] a = st[Math.max(i - 1, 0)], b = st[Math.min(i + 1, n - 1)];
            Vec3d t = MeshBuilder.unit(v(b[0] - a[0], b[1] - a[1], 0)), nn = v(-t.y(), t.x(), 0), c = v(st[i][0], st[i][1], 0);
            double hd = st[i][2], hw = st[i][3];
            rings[i] = new Vec3d[]{c.subtract(nn.scale(hd)).subtract(Z.scale(hw)), c.add(nn.scale(hd)).subtract(Z.scale(hw)),
                    c.add(nn.scale(hd)).add(Z.scale(hw)), c.subtract(nn.scale(hd)).add(Z.scale(hw))};
        }
        for (int i = 0; i + 1 < n; i++) { out.hexahedron(concat(rings[i], rings[i + 1]), colour); }
    }

    /** The centre height and half depth of stations at x (linear between them). */
    static double[] at(double[][] st, double x) {
        if (x <= st[0][0]) { return new double[]{st[0][1], st[0][2]}; }
        for (int i = 0; i + 1 < st.length; i++) {
            double[] a = st[i], b = st[i + 1];
            if (x <= b[0]) {
                double f = (x - a[0]) / Math.max(b[0] - a[0], 1e-9);
                return new double[]{a[1] + f * (b[1] - a[1]), a[2] + f * (b[2] - a[2])};
            }
        }
        double[] last = st[st.length - 1];
        return new double[]{last[1], last[2]};
    }

    private static Vec3d[] concat(Vec3d[] a, Vec3d[] b) {
        Vec3d[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    /** An outline in the x-y plane (convex) as a plate from z0 by a depth along z. */
    private static void plate(PartMesh out, double[][] outline, double z0, double depth, int colour) {
        out.extrude(outline, v(0, 0, z0), X, Y, v(0, 0, depth), colour, false);
    }

    /** The convex hull of points (x, y), counter-clockwise (Andrew's monotone chain). */
    static double[][] hull(List<double[]> points) {
        List<double[]> p = new ArrayList<>(points);
        p.sort(Comparator.<double[]>comparingDouble(a -> a[0]).thenComparingDouble(a -> a[1]));
        int n = p.size();
        if (n < 3) { return p.toArray(new double[0][]); }
        double[][] h = new double[2 * n][];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(h[k - 2], h[k - 1], p.get(i)) <= 0) { k--; }
            h[k++] = p.get(i);
        }
        for (int i = n - 2, t = k + 1; i >= 0; i--) {
            while (k >= t && cross(h[k - 2], h[k - 1], p.get(i)) <= 0) { k--; }
            h[k++] = p.get(i);
        }
        return Arrays.copyOf(h, Math.max(k - 1, 0));
    }

    private static double cross(double[] o, double[] a, double[] b) { return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]); }

    /** The rotation whose x is a direction and whose z is as near a reference as it can be (a right-handed set). */
    static Orientation frame(Vec3d xDir, Vec3d zRef) {
        Vec3d x = MeshBuilder.unit(xDir);
        Vec3d z = zRef.subtract(x.scale(x.dot(zRef)));
        z = z.lengthSquared() < 1e-12 ? MeshBuilder.perpendiculars(x)[0] : z.normalized();
        return ChassisModel.orientation(x, z.cross(x), z);
    }

    private static void place(PartLayers layers, PackedMesh mesh, Vec3d at, Orientation turn, PoseStack stack, int light) {
        stack.pushPose();
        stack.translate(at.x(), at.y(), at.z());
        stack.mulPose(new Quaternionf((float) turn.x(), (float) turn.y(), (float) turn.z(), (float) turn.w()));
        layers.draw(mesh, stack, light);
        stack.popPose();
    }

    // ---------------------------------------------------------------- hydraulic rams

    /**
     * A part's rams: each one's base pin (the mount's frame at q = 0) and rod pin (the part's frame), the joint they
     * drive (origin, axis, turning or sliding) and the cylinder.
     */
    record Rams(List<Vec3d[]> pins, Vec3d origin, Vec3d axis, boolean revolute, double bore, double rod, double stroke) {}

    static Rams rams(LinkDesign d) {
        if (d.actuation() != LinkDesign.Actuation.RAM || !d.revolute() || !(d.ramBoreM() > 0) || !(d.ramRodM() > 0) || !(d.ramStrokeM() > 0)) { return null; }
        if (d.linkKind() == LinkDesign.LinkKind.SLEW_RING || d.linkKind() == LinkDesign.LinkKind.LATTICE) { return null; }
        return rams(d.basePin(), d.rodPin(), Vec3d.ZERO, d.axis(), true, d.ramBoreM(), d.ramRodM(), d.ramStrokeM(), d.ramCount(), 0.5 * d.sectionWidthM());
    }

    static Rams rams(WorkToolDesign d) {
        if (!d.rammed()) { return null; }
        WorkToolDesign.RamGeometry g = d.ramGeometry();
        if (g == null || !(d.ramRodM() > 0)) { return null; }
        double half = Math.max(0.3 * d.toolWidthM() - ramOuter(d.ramBoreM()) - 0.03, 0);
        return rams(g.basePin(), g.rodPin(), d.jointOrigin(), d.jointAxis(), !d.prismatic(), d.ramBoreM(), d.ramRodM(), d.ramStrokeM(), d.ramCount(), half);
    }

    /** Rams of a joint: side by side across a turning joint about z (pins on its plane), else mirrored across z, else one. */
    private static Rams rams(Vec3d base, Vec3d rod, Vec3d origin, Vec3d axis, boolean revolute, double bore, double rodD, double stroke, int count, double half) {
        List<Vec3d[]> pins = new ArrayList<>();
        if (count > 1 && Math.abs(base.z()) < 1e-6 && Math.abs(rod.z()) < 1e-6 && Math.abs(axis.z()) > 0.9) {
            double zr = half + ramOuter(bore) + 0.03;
            for (int i = 0; i < count; i++) {
                Vec3d dz = v(0, 0, -zr + 2 * zr * i / (count - 1));
                pins.add(new Vec3d[]{base.add(dz), rod.add(dz)});
            }
        } else {
            pins.add(new Vec3d[]{base, rod});
            if (count > 1 && (Math.abs(base.z()) > 1e-6 || Math.abs(rod.z()) > 1e-6)) { pins.add(new Vec3d[]{v(base.x(), base.y(), -base.z()), v(rod.x(), rod.y(), -rod.z())}); }
        }
        return new Rams(pins, origin, axis, revolute, bore, rodD, stroke);
    }

    static double ramEye(double bore) { return 0.7 * bore; }

    static double ramOuter(double bore) { return 0.5 * bore + Math.max(0.008, 0.12 * bore); }

    /** The pieces: the barrel from its base eye along +x (1), the rod from its eye along +x (2), the base pins' brackets about the first base pin (3). */
    static void ramPieces(Rams r, PartMesh out) {
        double bore = r.bore(), ro = ramOuter(bore), re = ramEye(bore);
        double closed = MachineRules.closedLengthM(bore, r.stroke()), lb = closed - 1.6 * bore - 0.05;
        out.piece(1).finish(Finishes.PAINT_WORN);
        out.cylinder(v(0, 0, -0.55 * bore), v(0, 0, 0.55 * bore), re, re, 14, YELLOW);
        out.cylinder(v(0, 0, 0), v(0.9 * bore, 0, 0), 0.55 * bore, 0.7 * ro, 10, YELLOW);
        out.cylinder(v(0.8 * bore, 0, 0), v(0.8 * bore + 0.05, 0, 0), 1.08 * ro, 1.08 * ro, 16, YELLOW);
        out.cylinder(v(0.8 * bore + 0.05, 0, 0), v(lb - 0.07, 0, 0), ro, ro, 16, YELLOW);
        out.cylinder(v(lb - 0.07, 0, 0), v(lb, 0, 0), 1.1 * ro, 1.1 * ro, 16, YELLOW);
        double p0 = 0.8 * bore + 0.12, p1 = lb - 0.16;
        out.detail(1);
        for (double x : new double[]{p0, p1}) { out.cylinder(v(x, 0.8 * ro, 0), v(x, ro + 0.05, 0), 0.22 * bore, 0.22 * bore, 8, YELLOW); }
        out.finish(Finishes.BRUSHED_STEEL).tube(List.of(v(p0, ro + 0.04, 0), v(p0 + 0.05, ro + 0.05, 0), v(p1 - 0.05, ro + 0.05, 0), v(p1, ro + 0.04, 0)),
                0.12 * bore, null, STEEL, null, 6, false);
        out.detail(0);
        out.piece(2).finish(Finishes.FORGED_STEEL);
        out.cylinder(v(0, 0, -0.5 * bore), v(0, 0, 0.5 * bore), 0.9 * re, 0.9 * re, 14, STEEL);
        out.finish(Finishes.CHROME).cylinder(v(0, 0, 0), v(r.stroke() + 1.6 * bore + 0.2, 0, 0), 0.5 * r.rod(), 0.5 * r.rod(), 12, CHROME);
        out.piece(3);
        Vec3d base0 = r.pins().get(0)[0];
        double zLo = Double.POSITIVE_INFINITY, zHi = Double.NEGATIVE_INFINITY;
        for (Vec3d[] p : r.pins()) {
            Vec3d b = p[0], off = b.subtract(base0);
            zLo = Math.min(zLo, off.z()); zHi = Math.max(zHi, off.z());
            // a strut square to the mount's x axis (into a boom or a stick), or toward the joint where the pin is on that axis
            Vec3d foot = v(b.x(), 0, b.z()), along = foot.subtract(b);
            double reach = along.length();
            Vec3d toward = reach > 1e-6 ? along.scale(1 / reach) : MeshBuilder.unit(r.origin().subtract(b).add(v(1e-9, 0, 0)));
            toward = MeshBuilder.unit(v(toward.x(), toward.y(), 0));
            Vec3d n = v(-toward.y(), toward.x(), 0);
            double length = Math.max(reach, 2.0 * re);
            for (int side = -1; side <= 1; side += 2) {
                double z = off.z() + side * (0.55 * bore + 0.02);
                out.finish(Finishes.PAINT_WORN).cylinder(v(off.x(), off.y(), z), v(off.x(), off.y(), z + side * 0.04), 1.1 * re, 1.1 * re, 14, YELLOW);
                double[][] outline = {{off.x() + n.x() * re, off.y() + n.y() * re}, {off.x() - n.x() * re, off.y() - n.y() * re},
                        {off.x() + toward.x() * length - n.x() * 0.8 * re, off.y() + toward.y() * length - n.y() * 0.8 * re},
                        {off.x() + toward.x() * length + n.x() * 0.8 * re, off.y() + toward.y() * length + n.y() * 0.8 * re}};
                plate(out, outline, z, side * 0.04, YELLOW);
            }
        }
        out.finish(Finishes.FORGED_STEEL).cylinder(v(0, 0, zLo - 0.55 * bore - 0.06), v(0, 0, zHi + 0.55 * bore + 0.06), 0.45 * re, 0.45 * re, 10, STEEL);
        out.piece(0).detail(0);
    }

    /** The rams at the joint's value now: each barrel from its base pin toward its rod pin, each rod back, the brackets with the mount. */
    static void drawRams(Rams r, PartVehicle entity, String partId, PoseStack stack, PartLayers layers, int light, PartRenderer.Drawn drawn) {
        if (drawn == null || drawn.pieces() < 4) { return; }
        double q = joint(entity, partId);
        Orientation back = r.revolute() ? Orientation.rotationVector(r.axis().scale(-q)) : Orientation.IDENTITY;
        Vec3d zRef = r.revolute() ? r.axis() : Z;
        Vec3d first = null;
        for (Vec3d[] p : r.pins()) {
            Vec3d b = r.revolute() ? r.origin().add(back.rotate(p[0].subtract(r.origin()))) : p[0].subtract(r.axis().scale(q));
            if (first == null) { first = b; }
            Vec3d d = p[1].subtract(b);
            if (d.lengthSquared() < 1e-8) { continue; }
            place(layers, drawn.piece(1), b, frame(d, zRef), stack, light);
            place(layers, drawn.piece(2), p[1], frame(d.scale(-1), zRef), stack, light);
        }
        if (first != null) { place(layers, drawn.piece(3), first, back, stack, light); }
    }

    /** A part's joint value on the client (the last the server sent: the rams lag the drawn pose by under a tick). */
    static double joint(PartVehicle entity, String partId) {
        Vehicle vehicle = entity.vehicle();
        if (vehicle == null) { return 0; }
        for (Vehicle.Installed installed : vehicle.installed()) {
            if (!installed.id().equals(partId)) { continue; }
            int j = vehicle.jointOf(installed.index());
            if (j < 0) { return 0; }
            double[] q = entity.pose().values();
            return j < q.length ? q[j] : 0;
        }
        return 0;
    }

    // ---------------------------------------------------------------- links

    /** A box link's stations: a mono boom bent at its knee, a stick with its tail behind the pin, arms, uniform sections. */
    static double[][] stations(LinkDesign d) {
        double l = d.lengthM(), h = d.sectionHeightM(), w = d.sectionWidthM();
        return switch (d.linkKind()) {
            case BOOM -> new double[][]{{0, 0, 0.30 * h, 0.5 * w}, {0.15 * l, 0.045 * l, 0.42 * h, 0.5 * w}, {0.45 * l, 0.09 * l, 0.5 * h, 0.5 * w},
                    {0.72 * l, 0.06 * l, 0.42 * h, 0.46 * w}, {l, 0, 0.28 * h, 0.42 * w}};
            case STICK -> d.rodPinXM() < -0.05
                    ? new double[][]{{d.rodPinXM() - 0.12, 0.6 * d.rodPinYM(), 0.30 * h, 0.44 * w}, {0, 0, 0.5 * h, 0.5 * w}, {0.55 * l, 0, 0.4 * h, 0.45 * w}, {l, 0, 0.25 * h, 0.4 * w}}
                    : new double[][]{{0, 0, 0.5 * h, 0.5 * w}, {0.55 * l, 0, 0.4 * h, 0.45 * w}, {l, 0, 0.25 * h, 0.4 * w}};
            case LOADER_ARM -> new double[][]{{0, 0, 0.35 * h, 0.5 * w}, {0.5 * l, -0.06 * l, 0.5 * h, 0.5 * w}, {l, 0, 0.35 * h, 0.5 * w}};
            case TELESCOPIC -> new double[][]{{0, 0, 0.5 * h, 0.5 * w}, {l, 0, 0.5 * h, 0.5 * w}};
            default -> new double[][]{{0, 0, 0.5 * h, 0.5 * w}, {l, 0, 0.42 * h, 0.42 * w}};
        };
    }

    static void link(LinkDesign d, PartMesh out) {
        double l = d.lengthM(), h = d.sectionHeightM(), w = d.sectionWidthM();
        switch (d.linkKind()) {
            case SLEW_RING -> slewRing(l, h, w, out);
            case LATTICE -> lattice(d, out);
            case MAST -> mast(l, h, w, out);
            case OUTRIGGER_BEAM -> outriggerBeam(l, h, w, out);
            case ARTICULATION -> articulation(l, h, w, out);
            case TELESCOPIC -> telescopic(l, h, w, out);
            default -> boxLink(d, out);
        }
    }

    /**
     * 2026-11-22 (redrawn in Blender): a slewing ring (diameter l, race height h, race width w): the outer race on the
     * undercarriage with its bolt heads, the seal, the turntable plate on top (the house's frame, chamfered at its rim)
     * and the swing drive standing on its right.
     */
    static void slewRing(double l, double h, double w, PartMesh out) {
        double r = l / 2, rt = r + 0.3 * w;
        out.finish(Finishes.MACHINED_STEEL).lathe(v(0, 0, 0), v(0, 0.55 * h, 0), new double[]{0, 0, 1, 1, 0},
                new double[]{r - 0.05 * w, r + 0.5 * w, r + 0.5 * w, r - 0.05 * w, r - 0.05 * w}, null, 48, STEEL, true);
        out.detail(1);
        for (int i = 0; i < 36; i++) {
            double a = 2 * Math.PI * (i + 0.5) / 36;
            Vec3d c = v((r + 0.3 * w) * Math.cos(a), 0.55 * h, (r + 0.3 * w) * Math.sin(a));
            out.cylinder(c, c.add(Y.scale(0.018)), 0.016, 0.016, 6, STEEL);
        }
        out.detail(0).finish(Finishes.RUBBER).cylinder(v(0, 0.55 * h, 0), v(0, 0.6 * h, 0), r + 0.02, r + 0.02, 48, BLACK);
        out.finish(Finishes.PAINT_WORN).lathe(v(0, 0.6 * h, 0), v(0, h, 0), new double[]{0, 0, 0.6, 1, 1}, new double[]{0, rt - 0.03, rt, rt - 0.02, 0}, null, 48, YELLOW, true);
        Vec3d c = v(-0.1 * r, h, 0.72 * r);
        out.finish(Finishes.CAST_IRON).lathe(c, c.add(Y.scale(0.42)), new double[]{0, 0, 0.08, 0.08, 0.55, 0.6, 1, 1},
                new double[]{0, 0.13, 0.13, 0.11, 0.11, 0.08, 0.08, 0}, null, 16, DARK, true);
        out.box(c.add(v(-0.08, 0.36, -0.06)), c.add(v(0.06, 0.46, 0.08)), DARK);
    }

    /**
     * 2026-11-22 (redrawn in Blender): a mast stage (l high along +y, its uprights w apart, h deep): two channel uprights
     * (web outward, flanges in) with their rollers, the bottom, middle and top ties, the crosshead's two chain sheaves and
     * the chains down the front.
     */
    static void mast(double l, double hIn, double w, PartMesh out) {
        double h = Math.max(hIn, 0.08), t = 0.014, fl = Math.max(0.06, 0.6 * h);
        out.finish(Finishes.PAINT_WORN);
        for (double s : new double[]{-1, 1}) {
            double zo = s * w / 2;
            out.box(v(-h / 2, 0, Math.min(zo, zo - s * t)), v(h / 2, l, Math.max(zo, zo - s * t)), YELLOW);
            for (double x : new double[]{-h / 2, h / 2 - t}) { out.box(v(x, 0, Math.min(zo, zo - s * fl)), v(x + t, l, Math.max(zo, zo - s * fl)), YELLOW); }
        }
        out.box(v(-0.4 * h, 0.02, -w / 2), v(0.4 * h, 0.14, w / 2), YELLOW);
        out.box(v(-0.45 * h, l - 0.12, -w / 2), v(0.45 * h, l - 0.02, w / 2), YELLOW);
        out.box(v(-0.3 * h, 0.55 * l - 0.04, -w / 2), v(0.3 * h, 0.55 * l + 0.04, w / 2), YELLOW);
        out.finish(Finishes.MACHINED_STEEL);
        for (double s : new double[]{-1, 1}) {
            for (double y : new double[]{l - 0.18, 0.25}) { out.cylinder(v(0, y, s * w / 2), v(0, y, s * (w / 2 + 0.03)), 0.045, 0.045, 12, STEEL); }
        }
        for (double z : new double[]{-0.22 * w, 0.22 * w}) {
            out.finish(Finishes.MACHINED_STEEL).cylinder(v(0, l + 0.02, z - 0.025), v(0, l + 0.02, z + 0.025), 0.07, 0.07, 16, STEEL);
            out.finish(Finishes.FORGED_STEEL).box(v(h / 2 + 0.02, 0.35 * l, z - 0.02), v(h / 2 + 0.045, l + 0.02, z + 0.02), DARK);
        }
    }

    /**
     * 2026-11-22 (redrawn in Blender): an outrigger beam along +z (l): a box with chamfered edges, black wear pads at its
     * root, the head at its tip with the jack's sleeve, and red and white warning stripes on the head.
     */
    static void outriggerBeam(double l, double h, double w, PartMesh out) {
        double c = 0.15 * Math.min(h, w), hw = 0.6 * w, hh = 0.55 * h;
        double[][] section = {{w / 2, h / 2 - c}, {w / 2 - c, h / 2}, {-w / 2 + c, h / 2}, {-w / 2, h / 2 - c}, {-w / 2, -h / 2 + c}, {-w / 2 + c, -h / 2}, {w / 2 - c, -h / 2}, {w / 2, -h / 2 + c}};
        out.finish(Finishes.PAINT_WORN).extrude(section, Vec3d.ZERO, X, Y, Z.scale(l - 0.3), YELLOW, false);
        out.box(v(-hw, -hh, l - 0.32), v(hw, hh, l), YELLOW);
        out.cylinder(v(0, -hh - 0.04, l - 0.16), v(0, hh + 0.1, l - 0.16), 0.13, 0.13, 20, YELLOW);
        out.finish(Finishes.PLASTIC);
        out.box(v(-0.35 * w, h / 2, 0.02), v(0.35 * w, h / 2 + 0.012, 0.2), BLACK);
        out.box(v(-0.35 * w, -h / 2 - 0.012, 0.02), v(0.35 * w, -h / 2, 0.2), BLACK);
        out.finish(Finishes.PAINT_GLOSS);
        for (int i = 0; i < 5; i++) {
            double za = l - 0.32 + 0.064 * i, zb = za + 0.064;
            for (double s : new double[]{-1, 1}) {
                out.box(v(s * hw - (s < 0 ? 0.004 : 0), -hh + 0.03, za), v(s * hw + (s > 0 ? 0.004 : 0), hh - 0.03, zb), i % 2 == 0 ? RED : WHITE);
            }
        }
    }

    /**
     * 2026-11-22 (redrawn in Blender): a centre hinge's front half (its hinge at the origin, about y; the front frame
     * toward +x): the upper and lower tongues round their pins, the pins and their caps, the front frame's box and the
     * steering rams' rod lugs.
     */
    static void articulation(double l, double h, double w, PartMesh out) {
        double rp = 0.13, t = 0.06;
        double[][] tongue = new double[9][];
        for (int k = 0; k < 7; k++) { double a = Math.PI / 2 + k * Math.PI / 6; tongue[k] = new double[]{rp * Math.cos(a), rp * Math.sin(a)}; }
        tongue[7] = new double[]{l, -0.42 * w};
        tongue[8] = new double[]{l, 0.42 * w};
        out.finish(Finishes.PAINT_WORN);
        for (double y : new double[]{h / 2 - t, -h / 2}) {
            out.extrude(tongue, v(0, y, 0), X, Z, Y.scale(t), YELLOW, false);
            out.cylinder(v(0, y - 0.015, 0), v(0, y + t + 0.015, 0), 0.85 * rp, 0.85 * rp, 16, YELLOW);
        }
        out.box(v(0.2, -h / 2, -w / 2), v(l + 0.02, h / 2, w / 2), YELLOW);
        for (double s : new double[]{-1, 1}) { out.box(v(-0.05, -0.1, s * 0.3 - 0.03), v(0.2, 0.1, s * 0.3 + 0.03), YELLOW); }
        out.finish(Finishes.FORGED_STEEL);
        out.cylinder(v(0, h / 2 + 0.015, 0), v(0, h / 2 + 0.06, 0), 0.07, 0.07, 12, STEEL);
        out.cylinder(v(0, -h / 2 - 0.06, 0), v(0, -h / 2 - 0.015, 0), 0.07, 0.07, 12, STEEL);
        for (double s : new double[]{-1, 1}) { out.cylinder(v(0, 0, s * 0.3 - 0.06), v(0, 0, s * 0.3 + 0.06), 0.045, 0.045, 10, STEEL); }
    }

    /**
     * 2026-11-22 (redrawn in Blender): a telescopic section: a U-bottomed box (flat top, sides down to a rounded bottom),
     * the collar at its nose with the wear pads, the foot's pin bosses (inside the outer section for an inner one).
     */
    static void telescopic(double l, double h, double w, PartMesh out) {
        out.finish(Finishes.PAINT_WORN);
        out.extrude(uSection(w, h, 1), Vec3d.ZERO, Z, Y, X.scale(l - 0.32), YELLOW, false);
        out.extrude(uSection(w, h, 1.08), v(l - 0.32, 0, 0), Z, Y, X.scale(0.32), YELLOW, false);
        for (double s : new double[]{-1, 1}) { out.cylinder(v(0.12, -0.25 * h, s * 0.5 * w), v(0.12, -0.25 * h, s * (0.5 * w + 0.05)), 0.3 * h, 0.3 * h, 16, YELLOW); }
        out.finish(Finishes.PLASTIC).box(v(l - 0.3, 0.54 * h, -0.3 * w), v(l - 0.06, 0.54 * h + 0.015, 0.3 * w), BLACK);
        out.finish(Finishes.FORGED_STEEL).cylinder(v(0.12, -0.25 * h, -0.5 * w - 0.09), v(0.12, -0.25 * h, 0.5 * w + 0.09), 0.1 * h, 0.1 * h, 10, STEEL);
    }

    /** A telescopic section's outline (z, y), grown by a factor: flat top, straight sides, a rounded bottom. */
    private static double[][] uSection(double w, double h, double g) {
        double a = 0.5 * w * g, b = 0.5 * h * g;
        return new double[][]{{a, b}, {-a, b}, {-a, -0.2 * b}, {-0.82 * a, -0.62 * b}, {-0.5 * a, -b}, {0.5 * a, -b}, {0.82 * a, -0.62 * b}, {a, -0.2 * b}};
    }

    /** A welded box link: its section, the pin bosses at the ends, the bracket to its ram's rod pin, a boom's lines. */
    static void boxLink(LinkDesign d, PartMesh out) {
        double l = d.lengthM(), h = d.sectionHeightM(), w = d.sectionWidthM();
        double[][] st = stations(d);
        out.finish(Finishes.PAINT_WORN);
        section(out, st, YELLOW);
        double pin = Math.max(0.04, 0.5 * d.pinDiameterM());
        for (double[] end : new double[][]{{0, w}, {l, 0.84 * w}}) {
            double[] c = at(st, end[0]);
            out.finish(Finishes.PAINT_WORN).cylinder(v(end[0], c[0], -end[1] / 2 - 0.03), v(end[0], c[0], end[1] / 2 + 0.03), c[1], c[1], 16, YELLOW);
            out.finish(Finishes.FORGED_STEEL).cylinder(v(end[0], c[0], -end[1] / 2 - 0.07), v(end[0], c[0], end[1] / 2 + 0.07), pin, pin, 10, STEEL);
        }
        Rams r = rams(d);
        if (r != null) {
            Vec3d rod = d.rodPin();
            double re = ramEye(d.ramBoreM()), across = 0;
            for (Vec3d[] p : r.pins()) { across = Math.max(across, Math.abs(p[1].z()) + 0.55 * d.ramBoreM() + 0.06); }
            double[] c = at(st, rod.x());
            double face = rod.y() < c[0] - c[1] ? c[0] - c[1] + 0.04 : rod.y() > c[0] + c[1] ? c[0] + c[1] - 0.04 : Double.NaN;
            if (!Double.isNaN(face)) {
                double[][] outline = {{rod.x() - 2 * re, face}, {rod.x() + 2 * re, face}, {rod.x() + re, rod.y()}, {rod.x() - re, rod.y()}};
                plate(out, outline, -0.5 * w, w, YELLOW);
            }
            out.finish(Finishes.PAINT_WORN).cylinder(v(rod.x(), rod.y(), -0.5 * w), v(rod.x(), rod.y(), 0.5 * w), 1.1 * re, 1.1 * re, 14, YELLOW);
            out.finish(Finishes.FORGED_STEEL).cylinder(v(rod.x(), rod.y(), -across), v(rod.x(), rod.y(), across), 0.45 * re, 0.45 * re, 10, STEEL);
        }
        if (d.linkKind() == LinkDesign.LinkKind.BOOM) {
            // the stick's and bucket's lines along the top
            out.finish(Finishes.BRUSHED_STEEL).detail(1);
            for (double z : new double[]{-0.22 * w, 0.22 * w}) {
                List<Vec3d> path = new ArrayList<>();
                for (int i = 0; i <= 8; i++) {
                    double x = 0.12 * l + i * 0.76 * l / 8;
                    double[] c = at(st, x);
                    path.add(v(x, c[0] + c[1] + 0.035, z));
                }
                out.tube(path, 0.025, null, STEEL, null, 6, false);
            }
            out.detail(0);
        }
        if (d.linkKind() == LinkDesign.LinkKind.PLACING_BOOM) {
            // 2026-11-22: the delivery line (DN125, 133 mm) along the top on brackets, a clamp coupling every 3 m or so
            double y = 0.42 * h + 0.13;
            out.finish(Finishes.PAINT_WORN);
            for (int i = 0; 0.3 + 1.5 * i < l - 0.3; i++) { out.box(v(0.27 + 1.5 * i, 0.42 * h - 0.01, -0.06), v(0.33 + 1.5 * i, y - 0.04, 0.06), YELLOW); }
            out.finish(Finishes.BRUSHED_STEEL).cylinder(v(0.2, y, 0), v(l - 0.2, y, 0), 0.0665, 0.0665, 14, STEEL);
            int n = Math.max(1, (int) Math.round((l - 0.4) / 3));
            out.finish(Finishes.CAST_IRON);
            for (int i = 0; i <= n; i++) { double x = 0.2 + (l - 0.4) * i / n; out.cylinder(v(x - 0.04, y, 0), v(x + 0.04, y, 0), 0.085, 0.085, 14, DARK); }
        }
        if (d.linkKind() == LinkDesign.LinkKind.PLATFORM_BOOM) {
            // 2026-11-22: the cable and hose carrier along its right side
            double z = 0.5 * w + 0.02;
            out.finish(Finishes.PLASTIC).box(v(0.15 * l, -0.06, z), v(0.9 * l, 0.06, z + 0.07), BLACK);
            for (int i = 0; 0.6 * i <= 0.75 * l; i++) { double x = 0.15 * l + 0.6 * i; out.box(v(x - 0.015, -0.07, z - 0.01), v(x + 0.015, 0.07, z + 0.08), DARK); }
        }
    }

    /** A lattice boom: four chords narrowing to the foot and the head, lacing on every face, the head's sheaves. */
    static void lattice(LinkDesign d, PartMesh out) {
        double l = d.lengthM(), h = d.sectionHeightM(), w = d.sectionWidthM();
        double cr = Math.max(0.03, 0.5 * LinkDesign.CHORD_OD * Math.min(h, w)), lr = Math.max(0.015, LinkDesign.LACING_OD * cr);
        double[][] corners = {{-h / 2, -w / 2}, {h / 2, -w / 2}, {h / 2, w / 2}, {-h / 2, w / 2}};
        out.finish(Finishes.PAINT_GLOSS);
        for (double[] c : corners) {
            out.cylinder(v(0, c[0] * 0.6, c[1] * 0.6), v(0.1 * l, c[0], c[1]), cr, cr, 8, RED);
            out.cylinder(v(0.1 * l, c[0], c[1]), v(0.8 * l, c[0], c[1]), cr, cr, 8, RED);
            out.cylinder(v(0.8 * l, c[0], c[1]), v(l, c[0] * 0.5, c[1] * 0.5), cr, cr, 8, RED);
        }
        int bays = Math.max(2, (int) Math.round(l / Math.max(h, 0.5)));
        for (int i = 0; i < bays; i++) {
            double x0 = l * i / bays, x1 = l * (i + 1) / bays, s0 = taper(x0, l), s1 = taper(x1, l);
            for (int f = 0; f < 4; f++) {
                double[] a = corners[f], b = corners[(f + 1) % 4];
                Vec3d p = i % 2 == 0 ? v(x0, a[0] * s0, a[1] * s0) : v(x0, b[0] * s0, b[1] * s0);
                Vec3d q = i % 2 == 0 ? v(x1, b[0] * s1, b[1] * s1) : v(x1, a[0] * s1, a[1] * s1);
                out.cylinder(p, q, lr, lr, 6, WHITE);
                out.cylinder(v(x0, a[0] * s0, a[1] * s0), v(x0, b[0] * s0, b[1] * s0), lr, lr, 6, WHITE);
            }
        }
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(l, 0, -0.3 * w), v(l, 0, 0.3 * w), 0.35 * h, 0.35 * h, 16, STEEL);
    }

    /** A lattice's section across at x: whole from the foot's tenth to its last fifth, then narrowing to half at the head. */
    private static double taper(double x, double l) {
        if (x < 0.1 * l) { return 0.6 + 0.4 * x / (0.1 * l); }
        return 1 - 0.5 * Math.max(0, x - 0.8 * l) / (0.2 * l);
    }

    // ---------------------------------------------------------------- running gear

    /**
     * Crawler tracks: the shoes round the hull of the idler, sprocket, road wheels and carriers (the idler and sprocket
     * raised so their bottoms meet the run under the rollers), the rollers, the sprocket's teeth and final drive, the
     * track frames and the car body.
     */
    static void track(TrackDesign d, PartMesh out) {
        double half = d.gaugeM() / 2, sw = d.shoeWidthM(), rw = d.roadWheelRadiusM(), ri = d.idlerRadiusM(), rs = d.sprocketRadiusM(), c = d.contactLengthM();
        double xf = 0.5 * c + 0.35 * ri, xr = -(0.5 * c + 0.35 * rs), yi = ri - rw + 0.04, ys = rs - rw + 0.02;
        double top = Math.max(yi + ri, ys + rs) + d.runLiftM(), rc = 0.65 * rw;
        int n = Math.max(d.roadWheels(), 1);
        double[] wheels = new double[n];
        for (int i = 0; i < n; i++) { wheels[i] = -0.5 * c + c * (i + 0.5) / n; }
        double[] carriers = c > 2 ? new double[]{-0.22 * c, 0.22 * c} : new double[]{0};
        List<double[]> circles = new ArrayList<>(List.of(new double[]{xf, yi, ri}, new double[]{xr, ys, rs}));
        for (double x : wheels) { circles.add(new double[]{x, 0, rw}); }
        for (double x : carriers) { circles.add(new double[]{x, top - rc, rc}); }
        double pitch = Math.max(d.pitchM(), 0.03);
        List<double[]> loop = loop(circles, pitch);
        double ds = loopLength(loop) / loop.size();
        boolean rubber = d.shoe() == TrackPart.Shoe.RUBBER;
        double tp = rubber ? 0.035 : 0.02, g = Math.max(d.grouserHeightM(), 0);
        for (double side : new double[]{-1, 1}) {
            double zc = side * half;
            for (int i = 0; i < loop.size(); i++) {
                double[] a = loop.get((i + loop.size() - 1) % loop.size()), b = loop.get((i + 1) % loop.size()), p = loop.get(i);
                Vec3d t = MeshBuilder.unit(v(b[0] - a[0], b[1] - a[1], 0)), nrm = v(t.y(), -t.x(), 0);
                Vec3d at = v(p[0], p[1], zc);
                Orientation turn = frame(t, Z);   // the shoe's length along the loop, its thickness along -nrm (boxes are symmetric)
                if (rubber) {
                    out.finish(Finishes.RUBBER).box(at.add(nrm.scale(tp / 2)), v(0.51 * ds, tp / 2, sw / 2), turn, RUBBER);
                    if (i % 2 == 0 && g > 0) {
                        out.detail(1);
                        for (double s : new double[]{-1, 1}) { out.box(at.add(nrm.scale(tp + g / 2)).add(Z.scale(s * sw * 0.25)), v(0.25 * ds, g / 2, 0.2 * sw), turn, RUBBER); }
                        out.detail(0);
                    }
                    continue;
                }
                out.finish(Finishes.FORGED_STEEL).box(at.add(nrm.scale(tp / 2)), v(0.46 * ds, tp / 2, sw / 2), turn, BLACK);
                if (g > 0) {
                    double[] grousers = g > 0.05 ? new double[]{0} : new double[]{-0.3, 0, 0.3};
                    out.detail(grousers.length > 1 ? 1 : 0);
                    for (double gx : grousers) {
                        out.box(at.add(nrm.scale(tp + g / 2)).add(t.scale(gx * ds)), v(grousers.length > 1 ? 0.06 * ds : 0.1 * ds, g / 2, sw / 2), turn, BLACK);
                    }
                    out.detail(0);
                }
            }
            double rz = 0.25 * sw;
            out.finish(Finishes.CAST_IRON);
            for (double x : wheels) {
                out.cylinder(v(x, 0, zc - rz), v(x, 0, zc + rz), 0.85 * rw, 0.85 * rw, 12, DARK);
                for (double s : new double[]{-1, 1}) { out.cylinder(v(x, 0, zc + s * rz), v(x, 0, zc + s * (rz - 0.03)), rw, rw, 12, DARK); }
            }
            for (double x : carriers) { out.cylinder(v(x, top - rc, zc - 0.8 * rz), v(x, top - rc, zc + 0.8 * rz), rc, rc, 10, DARK); }
            out.cylinder(v(xf, yi, zc - 0.22 * sw), v(xf, yi, zc + 0.22 * sw), 0.95 * ri, 0.95 * ri, 20, DARK);
            out.cylinder(v(xf, yi, zc - 0.06 * sw), v(xf, yi, zc + 0.06 * sw), ri, ri, 20, DARK);
            out.finish(Finishes.MACHINED_STEEL).cylinder(v(xf, yi, zc - 0.3 * sw), v(xf, yi, zc + 0.3 * sw), 0.3 * ri, 0.3 * ri, 10, STEEL);
            // the sprocket: hub, teeth round it, the final drive inboard
            out.finish(Finishes.FORGED_STEEL).cylinder(v(xr, ys, zc - 0.08 * sw), v(xr, ys, zc + 0.08 * sw), 0.78 * rs, 0.78 * rs, 20, STEEL);
            int teeth = Math.max(8, (int) Math.round(2 * Math.PI * rs / pitch));
            Vec3d hub = v(xr, ys, zc);
            for (int k = 0; k < teeth; k++) {
                double angle = 2 * Math.PI * (k + 0.5) / teeth;
                Vec3d rad = v(Math.cos(angle), Math.sin(angle), 0), tg = v(-rad.y(), rad.x(), 0);
                double r0 = 0.72 * rs, r1 = rs + 0.015, w0 = 0.55 * Math.PI * r0 / teeth, w1 = 0.3 * Math.PI * r1 / teeth, hz = 0.05 * sw;
                Vec3d[] cc = new Vec3d[8];
                for (int e = 0; e < 2; e++) {
                    double z = e == 0 ? -hz : hz;
                    cc[4 * e] = hub.add(rad.scale(r0)).subtract(tg.scale(w0)).add(Z.scale(z));
                    cc[4 * e + 1] = hub.add(rad.scale(r0)).add(tg.scale(w0)).add(Z.scale(z));
                    cc[4 * e + 2] = hub.add(rad.scale(r1)).add(tg.scale(w1)).add(Z.scale(z));
                    cc[4 * e + 3] = hub.add(rad.scale(r1)).subtract(tg.scale(w1)).add(Z.scale(z));
                }
                out.hexahedron(cc, STEEL);
            }
            out.finish(Finishes.CAST_IRON).cylinder(v(xr, ys, zc - side * 0.1 * sw), v(xr, ys, zc - side * (0.5 * sw + 0.15)), 0.62 * rs, 0.55 * rs, 16, DARK);
            // the track frame: a box between the idler and the sprocket, narrowing to its top
            double x0 = xr + 0.55 * rs, x1 = xf - 0.55 * ri, fy0 = 0.35 * rw, fy1 = top - 2 * rc - 0.04, fz = 0.3 * sw, fzt = 0.12 * sw;
            out.finish(Finishes.PAINT_WORN).hexahedron(new Vec3d[]{v(x0, fy0, zc - fz), v(x1, fy0, zc - fz), v(x1, fy1, zc - fzt), v(x0, fy1, zc - fzt),
                    v(x0, fy0, zc + fz), v(x1, fy0, zc + fz), v(x1, fy1, zc + fzt), v(x0, fy1, zc + fzt)}, DARK);
        }
        // the car body between the frames
        out.finish(Finishes.PAINT_WORN).box(v(-0.7, 0.05, -half + 0.3 * sw), v(0.7, top - 0.05, half - 0.3 * sw), DARK);
        out.cylinder(v(0, top - 0.05, 0), v(0, top + 0.02, 0), Math.min(0.75, half - 0.3 * sw), Math.min(0.75, half - 0.3 * sw), 24, DARK);
    }

    /** Points every pitch round the convex hull of circles {x, y, r} (counter-clockwise). */
    static List<double[]> loop(List<double[]> circles, double pitch) {
        List<double[]> pts = new ArrayList<>();
        for (double[] c : circles) {
            for (int k = 0; k < 48; k++) { double a = 2 * Math.PI * k / 48; pts.add(new double[]{c[0] + c[2] * Math.cos(a), c[1] + c[2] * Math.sin(a)}); }
        }
        double[][] h = hull(pts);
        double total = 0;
        for (int i = 0; i < h.length; i++) { double[] a = h[i], b = h[(i + 1) % h.length]; total += Math.hypot(b[0] - a[0], b[1] - a[1]); }
        int count = Math.max(8, (int) Math.round(total / pitch));
        double ds = total / count, acc = 0, target = 0;
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < h.length && out.size() < count; i++) {
            double[] a = h[i], b = h[(i + 1) % h.length];
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            while (target <= acc + len && out.size() < count) {
                double f = (target - acc) / Math.max(len, 1e-12);
                out.add(new double[]{a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1])});
                target += ds;
            }
            acc += len;
        }
        return out;
    }

    private static double loopLength(List<double[]> loop) {
        double total = 0;
        for (int i = 0; i < loop.size(); i++) { double[] a = loop.get(i), b = loop.get((i + 1) % loop.size()); total += Math.hypot(b[0] - a[0], b[1] - a[1]); }
        return total;
    }

    /** A roller's drum: its shell (padfeet on a padfoot one), end discs and hubs, the yoke's side plates on rubber mounts, its beams and scrapers. */
    static void drum(DrumDesign d, PartMesh out) {
        double r = d.diameterM() / 2, w = d.widthM();
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(0, 0, -w / 2), v(0, 0, w / 2), r, r, 32, STEEL);
        for (double s : new double[]{-1, 1}) {
            out.finish(Finishes.CAST_IRON);
            out.cylinder(v(0, 0, s * w / 2), v(0, 0, s * (w / 2 + 0.01)), 0.97 * r, 0.97 * r, 32, DARK);
            out.cylinder(v(0, 0, s * (w / 2 + 0.01)), v(0, 0, s * (w / 2 + 0.12)), 0.32 * r, 0.32 * r, 16, DARK);
            double zp = s * (w / 2 + 0.12);
            plate(out.finish(Finishes.PAINT_WORN), new double[][]{{-0.42 * r, -0.3 * r}, {0.42 * r, -0.3 * r}, {0.62 * r, r + 0.3}, {-0.62 * r, r + 0.3}}, zp, s * 0.06, YELLOW);
            out.finish(Finishes.RUBBER);
            for (int k = 0; k < 6; k++) {
                double a = 2 * Math.PI * k / 6;
                Vec3d c = v(0.38 * r * Math.cos(a), 0.38 * r * Math.sin(a), zp);
                out.cylinder(c.subtract(v(0, 0, s * 0.07)), c, 0.06, 0.06, 8, RUBBER);
            }
        }
        out.finish(Finishes.PAINT_WORN);
        for (double x : new double[]{-0.5 * r, 0.5 * r}) { out.box(v(x - 0.14, r + 0.1, -w / 2 - 0.18), v(x + 0.14, r + 0.32, w / 2 + 0.18), YELLOW); }
        out.finish(Finishes.FORGED_STEEL);
        for (double s : new double[]{-1, 1}) { out.box(v(Math.min(s * (r + 0.02), s * (r + 0.05)), 0.1 * r, -w / 2 + 0.05), v(Math.max(s * (r + 0.02), s * (r + 0.05)), 0.45 * r, w / 2 - 0.05), WEAR); }
        if (d.surface() == DrumPart.Shell.PADFOOT) {
            out.finish(Finishes.FORGED_STEEL).detail(1);
            int around = (int) Math.round(2 * Math.PI * r / 0.22), rows = Math.max(2, (int) Math.round(w / 0.2));
            for (int j = 0; j < rows; j++) {
                double z = -w / 2 + w * (j + 0.5) / rows;
                for (int k = 0; k < around; k++) {
                    double a = 2 * Math.PI * (k + (j % 2 == 1 ? 0.5 : 0)) / around;
                    Vec3d rad = v(Math.cos(a), Math.sin(a), 0), tg = v(-Math.sin(a), Math.cos(a), 0);
                    Vec3d c0 = v(0, 0, z).add(rad.scale(r)), c1 = v(0, 0, z).add(rad.scale(r + 0.09));
                    out.hexahedron(new Vec3d[]{c0.subtract(tg.scale(0.06)).subtract(Z.scale(0.05)), c0.add(tg.scale(0.06)).subtract(Z.scale(0.05)),
                            c0.add(tg.scale(0.06)).add(Z.scale(0.05)), c0.subtract(tg.scale(0.06)).add(Z.scale(0.05)),
                            c1.subtract(tg.scale(0.045)).subtract(Z.scale(0.038)), c1.add(tg.scale(0.045)).subtract(Z.scale(0.038)),
                            c1.add(tg.scale(0.045)).add(Z.scale(0.038)), c1.subtract(tg.scale(0.045)).add(Z.scale(0.038))}, STEEL);
                }
            }
            out.detail(0);
        }
    }

    /**
     * 2026-11-22 (redrawn in Blender): an axial piston pump, its size by the cube root of its displacement against the
     * 112 cm^3 sample: the SAE mounting flange at x = 0 with the shaft through it, the barrel housing, the end cap with its
     * ports (the pressure port's flange and hose on the right, the larger suction port on the left), the regulator on top
     * and a charge pump piggybacked on the end cap. A gear pump: its aluminium body between two plates, its ports on its
     * sides.
     */
    static void pump(HydraulicPumpDesign d, PartMesh out) {
        double s = Math.cbrt(Math.max(d.displacementM3(), 1e-6) / 112e-6);
        if (d.pumpKind() == dev.mcmod.hdfv.part.machine.HydraulicUnit.GEAR) {
            out.finish(Finishes.CAST_ALUMINIUM);
            out.box(v(0, -0.05 * s, -0.06 * s), v(0.012 * s, 0.05 * s, 0.06 * s), ALU);
            out.box(v(0.012 * s, -0.045 * s, -0.055 * s), v(0.1 * s, 0.045 * s, 0.055 * s), ALU);
            out.box(v(0.1 * s, -0.05 * s, -0.06 * s), v(0.115 * s, 0.05 * s, 0.06 * s), ALU);
            for (double[] port : new double[][]{{-1, 0.018}, {1, 0.013}}) {
                out.cylinder(v(0.055 * s, 0, port[0] * 0.055 * s), v(0.055 * s, 0, port[0] * 0.075 * s), port[1] * s, port[1] * s, 10, ALU);
            }
            out.finish(Finishes.MACHINED_STEEL).cylinder(v(-0.035 * s, 0.018 * s, 0), v(0, 0.018 * s, 0), 0.009 * s, 0.009 * s, 8, STEEL);
            return;
        }
        double[][] flange = new double[8][];
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 8 + k * Math.PI / 4, r = 0.11 * s / Math.cos(Math.PI / 8);
            flange[k] = new double[]{r * Math.cos(a), r * Math.sin(a)};
        }
        out.finish(Finishes.CAST_IRON).extrude(flange, Vec3d.ZERO, Y, Z, X.scale(0.025 * s), DARK, false);
        out.lathe(v(0.025 * s, 0, 0), v(0.3 * s, 0, 0), new double[]{0, 0, 0.06, 0.9, 1, 1}, new double[]{0, 0.088 * s, 0.098 * s, 0.098 * s, 0.09 * s, 0}, null, 20, DARK, true);
        out.box(v(0.29 * s, -0.1 * s, -0.11 * s), v(0.37 * s, 0.1 * s, 0.11 * s), DARK);
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(-0.07 * s, 0, 0), v(0, 0, 0), 0.02 * s, 0.02 * s, 10, STEEL);
        out.cylinder(v(0.33 * s, 0, 0.11 * s), v(0.33 * s, 0, 0.135 * s), 0.035 * s, 0.035 * s, 12, STEEL);
        out.cylinder(v(0.33 * s, 0, -0.11 * s), v(0.33 * s, 0, -0.14 * s), 0.05 * s, 0.05 * s, 12, STEEL);
        // the regulator on top with its solenoid, the charge pump behind the end cap
        out.finish(Finishes.CAST_ALUMINIUM).box(v(0.13 * s, 0.09 * s, -0.045 * s), v(0.26 * s, 0.15 * s, 0.045 * s), ALU);
        out.box(v(0.37 * s, -0.045 * s, -0.05 * s), v(0.45 * s, 0.045 * s, 0.05 * s), ALU);
        out.finish(Finishes.PLASTIC).cylinder(v(0.195 * s, 0.15 * s, 0), v(0.195 * s, 0.19 * s, 0), 0.018 * s, 0.018 * s, 8, BLACK);
        out.finish(Finishes.BRAIDED_HOSE).tube(List.of(v(0.33 * s, 0, 0.135 * s), v(0.33 * s, 0, 0.2 * s), v(0.33 * s, 0.06 * s, 0.25 * s), v(0.33 * s, 0.2 * s, 0.27 * s)),
                0.018 * s, null, HOSE, null, 8, false);
    }

    static void motor(HydraulicMotorDesign d, PartMesh out) {
        if (d.winch()) { winch(d, out); } else { travelDrive(d, out); }
    }

    /**
     * 2026-11-22 (redrawn in Blender): a motor with its planetary reducer (a crawler's travel drive), sized by the cube
     * root of displacement times reduction against the 110 cm^3, 55:1 sample: the reducer's bolted flange toward -z (the
     * sprocket's side), its stepped housing with the drain and fill plugs, the motor behind it on +z with its brake-valve
     * block and two hoses.
     */
    static void travelDrive(HydraulicMotorDesign d, PartMesh out) {
        double k = Math.cbrt(Math.max(d.displacementM3() * Math.max(d.reductionRatio(), 1), 1e-9) / (110e-6 * 55));
        out.finish(Finishes.CAST_IRON).lathe(v(0, 0, -0.17 * k), v(0, 0, 0.13 * k), new double[]{0, 0, 0.1, 0.1, 0.55, 0.62, 0.62, 1, 1},
                new double[]{0, 0.3 * k, 0.3 * k, 0.27 * k, 0.27 * k, 0.25 * k, 0.23 * k, 0.21 * k, 0}, null, 24, DARK, true);
        out.cylinder(v(0, 0, 0.13 * k), v(0, 0, 0.29 * k), 0.12 * k, 0.11 * k, 16, DARK);
        out.box(v(-0.13 * k, -0.08 * k, 0.24 * k), v(0.1 * k, 0.11 * k, 0.33 * k), DARK);
        out.finish(Finishes.MACHINED_STEEL).detail(1);
        for (int i = 0; i < 16; i++) {
            double a = 2 * Math.PI * i / 16;
            Vec3d c = v(0.285 * k * Math.cos(a), 0.285 * k * Math.sin(a), -0.17 * k);
            out.cylinder(c, c.subtract(Z.scale(0.012 * k)), 0.012 * k, 0.012 * k, 6, STEEL);
        }
        for (double a : new double[]{0.5, 2.6}) {
            Vec3d c = v(0.15 * k * Math.cos(a), 0.15 * k * Math.sin(a), 0.13 * k);
            out.cylinder(c, c.add(Z.scale(0.012 * k)), 0.018 * k, 0.018 * k, 6, STEEL);
        }
        out.detail(0).finish(Finishes.BRAIDED_HOSE);
        for (double x : new double[]{-0.07 * k, 0.04 * k}) {
            out.tube(List.of(v(x, 0.11 * k, 0.29 * k), v(x, 0.17 * k, 0.29 * k), v(x, 0.22 * k, 0.35 * k)), 0.016 * k, null, HOSE, null, 8, false);
        }
    }

    /**
     * 2026-11-22 (redrawn in Blender): a winch about z: the rope wound on the drum (a pack two rope diameters deep, its
     * wraps at detail 1), the flanges, the side frames with their bearing bosses on a base of two channels, the
     * planetary reducer and the motor on +z with its brake-valve block and hoses.
     */
    static void winch(HydraulicMotorDesign d, PartMesh out) {
        double r = d.drumDiameterM() / 2, w = d.drumWidthM(), rope = Math.max(d.ropeDiameterM(), 0.006);
        double rf = 1.45 * r + 2 * rope, pack = r + 2 * rope, zf = w / 2 + 0.06, base = -rf - 0.06;
        out.finish(Finishes.BRUSHED_STEEL).cylinder(v(0, 0, -w / 2), v(0, 0, w / 2), pack, pack, 32, SILVER);
        int n = (int) (w / (1.1 * rope));
        out.detail(1);
        for (int i = 0; i < n; i += 2) {
            double z = -w / 2 + (i + 0.5) * w / n;
            out.tube(MeshBuilder.circle(v(0, 0, z), Z, pack, 24), 0.55 * rope, null, SILVER, null, 6, true);
        }
        out.detail(0).finish(Finishes.PAINT_WORN);
        for (double side : new double[]{-1, 1}) {
            out.cylinder(v(0, 0, side * w / 2), v(0, 0, side * (w / 2 + 0.03)), rf, rf, 32, YELLOW);
            double[][] frame = {{-1.25 * rf, base}, {1.25 * rf, base}, {0.45 * rf, 0.35 * rf}, {0, 0.45 * rf}, {-0.45 * rf, 0.35 * rf}};
            out.extrude(frame, v(0, 0, side * zf), X, Y, Z.scale(side * 0.03), YELLOW, false);
            out.cylinder(v(0, 0, side * (zf + 0.03)), v(0, 0, side * (zf + 0.07)), 0.32 * r, 0.32 * r, 16, YELLOW);
        }
        for (double x : new double[]{-1.1 * rf, 1.1 * rf}) { out.box(v(x - 0.06, base - 0.1, -zf - 0.06), v(x + 0.06, base, zf + 0.06), YELLOW); }
        double z0 = zf + 0.07, zm = z0 + 0.6 * r + 0.12;
        out.finish(Finishes.CAST_IRON).lathe(v(0, 0, z0), v(0, 0, zm), new double[]{0, 0, 0.15, 0.15, 0.8, 1, 1},
                new double[]{0, 0.62 * r, 0.62 * r, 0.55 * r, 0.55 * r, 0.42 * r, 0}, null, 20, DARK, true);
        out.cylinder(v(0, 0, zm), v(0, 0, zm + 0.22), 0.3 * r + 0.05, 0.28 * r + 0.05, 16, DARK);
        out.box(v(-0.12, 0.02, zm + 0.12), v(0.12, 0.3 * r + 0.14, zm + 0.24), DARK);
        out.finish(Finishes.BRAIDED_HOSE);
        for (double x : new double[]{-0.06, 0.06}) {
            out.tube(List.of(v(x, 0.3 * r + 0.14, zm + 0.18), v(x, 0.3 * r + 0.24, zm + 0.18), v(x, 0.3 * r + 0.3, zm + 0.08)), 0.016, null, HOSE, null, 8, false);
        }
    }

    /** A hook block: side plates (chamfered) about its sheaves and their pin, a crosshead, the nut and a forged hook (or a ramshorn's two). */
    static void hook(HookDesign d, PartMesh out) {
        double rope = d.ropeDiameterM(), dia = d.sheaveDiameterM(), lh = d.hookLengthM();
        int n = d.sheaves();
        double block = n > 0 ? 1.2 * dia : 0.3 * lh, sw = HookDesign.SHEAVE_WIDTH * rope + 0.012, stack = Math.max(n, 1) * sw;
        double pt = Math.max(0.02, 0.08 * dia), hx = 0.62 * dia + 0.04, ch = 0.15 * Math.max(dia, 0.2);
        double[][] octagon = {{-hx, -block + ch}, {-hx + ch, -block}, {hx - ch, -block}, {hx, -block + ch}, {hx, -ch}, {hx - ch, 0}, {-hx + ch, 0}, {-hx, -ch}};
        out.finish(Finishes.PAINT_GLOSS);
        for (double s : new double[]{-1, 1}) { plate(out, octagon, s > 0 ? stack / 2 : -stack / 2 - pt, pt, YELLOW); }
        out.finish(Finishes.MACHINED_STEEL);
        for (int i = 0; i < n; i++) {
            double z0 = -stack / 2 + i * sw;
            out.cylinder(v(0, -0.5 * block, z0 + 0.004), v(0, -0.5 * block, z0 + sw - 0.004), dia / 2, dia / 2, 20, STEEL);
        }
        out.finish(Finishes.FORGED_STEEL).cylinder(v(0, -0.5 * block, -stack / 2 - pt - 0.02), v(0, -0.5 * block, stack / 2 + pt + 0.02), 0.12 * dia + 0.01, 0.12 * dia + 0.01, 10, STEEL);
        out.finish(Finishes.PAINT_GLOSS).box(v(-0.5 * hx, -block - 0.14 * lh, -stack / 2 - pt), v(0.5 * hx, -block, stack / 2 + pt), YELLOW);
        double y0 = -block - 0.14 * lh, rs = 0.11 * lh, y1 = y0 - 0.08 * lh, big = 0.3 * lh;
        out.finish(Finishes.FORGED_STEEL).cylinder(v(0, y0, 0), v(0, y1, 0), 1.5 * rs, 1.5 * rs, 12, STEEL);
        double[] prongs = d.hookKind() == HookDesign.HookKind.RAMSHORN ? new double[]{-1, 1} : new double[]{1};
        out.finish(Finishes.PAINT_GLOSS);
        for (double p : prongs) {
            List<Vec3d> path = new ArrayList<>(List.of(v(0, y1, 0), v(0, y1 - 0.3 * lh, 0)));
            List<Double> radii = new ArrayList<>(List.of(rs, rs));
            double cx = p * big, cy = y1 - 0.3 * lh;
            for (int k = 1; k <= 14; k++) {
                double a = p > 0 ? Math.PI + k / 14.0 * 1.3 * Math.PI : -k / 14.0 * 1.3 * Math.PI;
                path.add(v(cx + big * Math.cos(a), cy + big * Math.sin(a), 0));
                radii.add(rs * (1.35 - 0.95 * Math.pow(k / 14.0, 2)));
            }
            out.tube(path, rs, radii.stream().mapToDouble(Double::doubleValue).toArray(), RED, null, 10, false);
        }
    }

    /**
     * A machine's operator cab (OperatorCabDesign, centred on its origin, its front at +x): a dark floor, yellow panels
     * below the sill and at the rear, a roof with a visor; black posts (thicker for a rated frame), the front ones
     * upright to the sill and leaning back above it by frontRakeDeg, sill rails; glass at the front from the floor up and
     * along its lean, on the sides above the sill and at the rear; the door's seams, handle and grab rail on its side,
     * the mirror there; a wiper, work lights and a beacon on the roof, a ROPS/FOPS cab's guard over it; the consoles and
     * joysticks either side of the seat (a seat part of its own).
     */
    static void operatorCab(OperatorCabDesign d, PartMesh out) {
        double l = d.lengthM(), w = d.widthM(), h = d.heightM();
        double x0 = -l / 2, x1 = l / 2, y0 = -h / 2, y1 = h / 2, z0 = -w / 2, z1 = w / 2;
        double sill = y0 + OperatorCabDesign.SILL_SHARE * h, lean = (y1 - sill) * Math.tan(Math.toRadians(d.frontRakeDeg())), xt = x1 - lean, roof = y1 - 0.08;
        double post = d.protection() == OperatorCabDesign.Protection.CAB ? 0.05 : 0.075, ds = d.doorSide() == OperatorCabDesign.DoorSide.LEFT ? -1 : 1;
        out.finish(Finishes.NONSKID).box(v(x0, y0, z0), v(x1, y0 + 0.06, z1), DARK);
        out.finish(Finishes.PAINT_WORN);
        out.box(v(x0, y0 + 0.06, z0), v(x0 + 0.04, sill, z1), YELLOW);
        for (double s : new double[]{-1, 1}) {
            double z = s * w / 2, zin = z - s * 0.04;
            out.box(v(x0, y0 + 0.06, Math.min(z, zin)), v(x1 - post, sill, Math.max(z, zin)), YELLOW);
        }
        out.box(v(x0 - 0.02, roof, z0 - 0.03), v(xt + 0.12, y1, z1 + 0.03), YELLOW);
        // the frame: rear posts, front posts upright then leaning, sill rails, the front's bottom and the rear's sill
        out.finish(Finishes.PAINT_MATTE);
        for (double s : new double[]{-1, 1}) {
            double zc = s * (w / 2 - post / 2);
            out.box(v(x0, y0, zc - post / 2), v(x0 + post, roof, zc + post / 2), BLACK);
            out.cylinder(v(x1 - post / 2, y0, zc), v(x1 - post / 2, sill, zc), post / 2, post / 2, 6, BLACK);
            out.cylinder(v(x1 - post / 2, sill, zc), v(xt - post / 2, roof, zc), post / 2, post / 2, 6, BLACK);
            out.box(v(x0, sill, zc - post / 2), v(x1, sill + 0.04, zc + post / 2), BLACK);
        }
        out.box(v(x1 - 0.05, y0 + 0.06, z0 + post), v(x1, y0 + 0.14, z1 - post), BLACK);
        out.box(v(x0, sill, z0 + post), v(x0 + 0.05, sill + 0.04, z1 - post), BLACK);
        // the door on its side: its seams, its handle, the grab rail ahead of it, the mirror on its arm
        double zd = ds * (w / 2 + 0.005), xd0 = x1 - post - Math.min(0.8, 0.55 * l), xd1 = x1 - post;
        for (double x : new double[]{xd0, xd1 - 0.01}) { out.box(v(x, y0 + 0.08, zd - 0.01), v(x + 0.02, y1 - 0.1, zd + 0.01), BLACK); }
        out.detail(1);
        out.finish(Finishes.BRUSHED_STEEL).box(v(xd0 + 0.05, sill - 0.12, Math.min(zd, zd + 0.03 * ds)), v(xd0 + 0.17, sill - 0.08, Math.max(zd, zd + 0.03 * ds)), STEEL);
        out.finish(Finishes.PAINT_WORN).cylinder(v(x1 + 0.04, y0 + 0.2, ds * (w / 2 - 0.1)), v(x1 + 0.04, sill + 0.4, ds * (w / 2 - 0.1)), 0.016, 0.016, 6, YELLOW);
        out.finish(Finishes.PAINT_MATTE).cylinder(v(x1 - 0.05, sill + 0.3, ds * w / 2), v(x1 + 0.1, sill + 0.4, ds * (w / 2 + 0.15)), 0.012, 0.012, 6, BLACK);
        double zm = ds * (w / 2 + 0.12);
        out.box(v(x1 + 0.08, sill + 0.3, zm - 0.05), v(x1 + 0.11, sill + 0.5, zm + 0.05), BLACK);
        out.box(v(x1 + 0.005 - 0.5 * lean, sill + 0.1, -0.015), v(x1 + 0.02 - 0.5 * lean, y1 - 0.15, 0.015), BLACK);   // the wiper
        out.finish(Finishes.LAMP_CLEAR);
        for (double z : new double[]{-0.3 * w, 0.3 * w}) { out.box(v(xt + 0.02, y1, z - 0.07), v(xt + 0.1, y1 + 0.08, z + 0.07), 0xfff0f3f6); }
        out.finish(Finishes.LAMP_AMBER).cylinder(v(x0 + 0.15, y1, ds * 0.3 * w), v(x0 + 0.15, y1 + 0.1, ds * 0.3 * w), 0.05, 0.045, 10, 0xffe58c12);
        out.detail(0);
        if (d.protection() == OperatorCabDesign.Protection.ROPS_FOPS) {
            // the falling-object guard: two rails along the roof and bars across them
            out.finish(Finishes.FORGED_STEEL);
            out.box(v(x0 + 0.05, y1, z0 + 0.05), v(xt - 0.05, y1 + 0.03, z0 + 0.12), DARK);
            out.box(v(x0 + 0.05, y1, z1 - 0.12), v(xt - 0.05, y1 + 0.03, z1 - 0.05), DARK);
            int bars = Math.max(3, (int) Math.round((xt - x0) / 0.25));
            for (int k = 0; k <= bars; k++) {
                double x = x0 + 0.08 + (xt - x0 - 0.16) * k / bars;
                out.box(v(x - 0.02, y1 + 0.03, z0 + 0.05), v(x + 0.02, y1 + 0.07, z1 - 0.05), DARK);
            }
        }
        // inside: the consoles either side of the seat, a joystick on each
        out.finish(Finishes.DASH_PLASTIC);
        for (double s : new double[]{-1, 1}) {
            out.box(v(x0 + 0.35 * l, y0 + 0.06, s * 0.3 * w - 0.07), v(x0 + 0.75 * l, y0 + 0.55, s * 0.3 * w + 0.07), DARK);
            out.cylinder(v(x0 + 0.65 * l, y0 + 0.55, s * 0.3 * w), v(x0 + 0.65 * l, y0 + 0.72, s * 0.3 * w), 0.015, 0.02, 6, BLACK);
        }
        // the glass: the front (upright below the sill, leaning above), the sides above the sill along the lean, the rear
        double t = 0.02;
        out.finish(Finishes.GLASS).clear(true);
        out.box(v(x1 - t - 0.01, y0 + 0.14, z0 + post), v(x1 - 0.01, sill, z1 - post), GLASS);
        plate(out, new double[][]{{x1 - 0.01, sill}, {x1 - 0.01 - t, sill}, {xt - 0.01 - t, roof}, {xt - 0.01, roof}}, z0 + post, w - 2 * post, GLASS);
        for (double s : new double[]{-1, 1}) {
            plate(out, new double[][]{{x0 + post, sill + 0.04}, {x1 - post, sill + 0.04}, {xt - post, roof}, {x0 + post, roof}}, s * (w / 2 - 0.015) - 0.01, 0.02, GLASS);
        }
        out.box(v(x0 - 0.005, sill + 0.04, z0 + post), v(x0 + 0.015, roof, z1 - post), GLASS);
        out.clear(false);
    }

    /**
     * A machine's engine hood (EngineHoodDesign, centred on its origin, its front at +x, 2026-11-22): yellow slices along
     * its length (the tail's corners rounded in plan, the top falling toward the grille), a dark band along its bottom
     * edge (the deck's), louvres where the radiator breathes (dark panels behind yellow blades), the side doors' seams
     * and latches, the engine lid's seams on top, on a house-sized hood a non-slip walkway on the right with a handrail,
     * the exhaust stack with its flange and rain cap, a work light at the rear.
     */
    static void engineHood(EngineHoodDesign d, PartMesh out) {
        double l = d.lengthM(), w = d.widthM(), h = d.heightM(), r = d.tailRadiusM();
        double x0 = -l / 2, x1 = l / 2, y0 = -h / 2, xs = x0 + r;   // xs: where the sides run straight
        // the shell: slices between stations round the tail's arc (even in angle), then one to the front
        List<Double> st = new ArrayList<>();
        int arc = r > 0.01 ? 8 : 0;
        for (int i = 0; i <= arc; i++) { st.add(x0 + r * (1 - Math.cos(Math.PI / 2 * i / arc))); }
        if (arc == 0) { st.add(x0); }
        st.add(x1);
        out.finish(Finishes.PAINT_WORN);
        for (int i = 0; i + 1 < st.size(); i++) { slice(out, d, st.get(i), st.get(i + 1), y0, 0, 0, YELLOW, false); }
        // the deck's edge along the bottom, a little proud
        out.finish(Finishes.PAINT_MATTE);
        for (int i = 0; i + 1 < st.size(); i++) { slice(out, d, st.get(i), st.get(i + 1), y0, 0.12, 0.012, DARK, true); }
        // louvres: a dark panel a little proud, yellow blades across it
        double edge = 0.012;
        switch (d.grille()) {
            case SIDES -> {
                double ga = xs + 0.1, gb = Math.min(x1 - 0.1, ga + 0.5 * (l - r)), top = y0 + Math.min(d.topAt(ga), d.topAt(gb)) - 0.15, bottom = y0 + 0.2 * h;
                if (gb > ga + 0.1 && top > bottom + 0.1) {
                    for (double s : new double[]{-1, 1}) {
                        double z = s * w / 2;
                        out.finish(Finishes.PAINT_MATTE).box(v(ga, bottom, Math.min(z, z + s * edge)), v(gb, top, Math.max(z, z + s * edge)), BLACK);
                        out.finish(Finishes.PAINT_WORN);
                        for (double y = bottom + 0.03; y + 0.025 < top; y += 0.055) {
                            out.box(v(ga + 0.03, y, Math.min(z, z + s * 0.03)), v(gb - 0.03, y + 0.025, Math.max(z, z + s * 0.03)), YELLOW);
                        }
                    }
                }
            }
            case REAR, FRONT -> {
                boolean rear = d.grille() == EngineHoodDesign.Grille.REAR;
                double x = rear ? x0 : x1, sx = rear ? -1 : 1, half = Math.max(0.1, w / 2 - r - 0.08);
                double top = y0 + d.topAt(x) - 0.12, bottom = y0 + 0.18;
                if (top > bottom + 0.1) {
                    out.finish(Finishes.PAINT_MATTE).box(v(Math.min(x, x + sx * edge), bottom, -half), v(Math.max(x, x + sx * edge), top, half), BLACK);
                    out.finish(Finishes.PAINT_WORN);
                    for (double y = bottom + 0.03; y + 0.025 < top; y += 0.055) {
                        out.box(v(Math.min(x, x + sx * 0.03), y, -half + 0.03), v(Math.max(x, x + sx * 0.03), y + 0.025, half - 0.03), YELLOW);
                    }
                }
            }
        }
        // the side doors: seams between them, a latch on each
        out.detail(1).finish(Finishes.PAINT_MATTE);
        int doors = Math.max(1, (int) Math.round((x1 - xs) / 0.9));
        for (double s : new double[]{-1, 1}) {
            double z = s * w / 2, zo = z + s * 0.006;
            for (int k = 0; k <= doors; k++) {
                double x = Math.max(xs + 0.02, Math.min(x1 - 0.03, xs + (x1 - xs) * k / doors));
                out.box(v(x - 0.008, y0 + 0.14, Math.min(z, zo)), v(x + 0.008, y0 + d.topAt(x) - 0.05, Math.max(z, zo)), BLACK);
                if (k < doors) {
                    double xl = xs + (x1 - xs) * (k + 0.5) / doors, zl = z + s * 0.025;
                    out.finish(Finishes.BRUSHED_STEEL).box(v(xl - 0.05, y0 + d.topAt(xl) - 0.2, Math.min(z, zl)), v(xl + 0.05, y0 + d.topAt(xl) - 0.16, Math.max(z, zl)), STEEL);
                    out.finish(Finishes.PAINT_MATTE);
                }
            }
        }
        // the engine lid on top: its seams (along the slope at its sides, square across at its ends)
        boolean walkway = l >= 1.5 && w >= 1.8;
        double la = x0 + Math.max(r, 0.3) + 0.1, lb = x1 - 0.25, lz0 = -w / 2 + 0.25, lz1 = walkway ? w / 2 - 0.6 : w / 2 - 0.25;
        if (lb > la + 0.2 && lz1 > lz0 + 0.2) {
            for (double z : new double[]{lz0, lz1}) { plate(out, slope(d, la, lb, y0, 0.008), z - 0.008, 0.016, BLACK); }
            for (double x : new double[]{la, lb}) { out.box(v(x - 0.008, y0 + d.topAt(x), lz0), v(x + 0.008, y0 + d.topAt(x) + 0.008, lz1), BLACK); }
        }
        // a house's walkway on the right with its handrail along the edge
        if (walkway) {
            double wa = x0 + Math.max(r, 0.2) + 0.1, wb = x1 - 0.1, wz = w / 2 - 0.1;
            out.finish(Finishes.NONSKID);
            plate(out, slope(d, wa, wb, y0, 0.012), wz - 0.4, 0.4, DARK);
            out.finish(Finishes.PAINT_WORN);
            double rail = 0.45;
            int posts = Math.max(2, (int) Math.round((wb - wa) / 0.8) + 1);
            List<Vec3d> top = new ArrayList<>();
            for (int k = 0; k < posts; k++) {
                double x = wa + 0.05 + (wb - wa - 0.1) * k / (posts - 1), y = y0 + d.topAt(x);
                out.cylinder(v(x, y, w / 2 - 0.04), v(x, y + rail, w / 2 - 0.04), 0.017, 0.017, 6, YELLOW);
                top.add(v(x, y + rail, w / 2 - 0.04));
            }
            out.tube(top, 0.019, 6, YELLOW);
        }
        out.detail(0);
        // the exhaust stack over the engine's middle, right of centre: a flange, the pipe, a rain cap tipped open
        if (d.stackM() > 0) {
            double sx = x1 - 0.35 * l, sz = 0.2 * w, base = y0 + d.topAt(sx), sr = 0.065;
            out.finish(Finishes.CAST_IRON).cylinder(v(sx, base - 0.01, sz), v(sx, base + 0.04, sz), sr + 0.04, sr + 0.04, 12, DARK);
            out.finish(Finishes.PAINT_MATTE).cylinder(v(sx, base, sz), v(sx, base + d.stackM(), sz), sr, sr, 12, BLACK);
            out.detail(1).box(v(sx - sr - 0.02, base + d.stackM() - 0.03, sz - 0.015), v(sx - sr + 0.005, base + d.stackM() + 0.01, sz + 0.015), BLACK);   // the cap's hinge lug
            out.box(v(sx + 0.01, base + d.stackM() + 0.04, sz), v(sr + 0.01, 0.005, sr + 0.01), Orientation.rotationVector(Z.scale(Math.toRadians(25))), DARK);
            out.detail(0);
        }
        // a work light at the rear, over the grille or the tail
        out.detail(1).finish(Finishes.LAMP_CLEAR);
        double ly = y0 + d.topAt(x0);
        out.box(v(x0 + 0.06, ly, -0.08), v(x0 + 0.14, ly + 0.08, 0.08), 0xfff0f3f6);
        out.detail(0).finish(null);
    }

    /** A hood's slice between stations xa and xb: its section (half width and top at each), from its bottom up height (0: the top), grown by grow. */
    private static void slice(PartMesh out, EngineHoodDesign d, double xa, double xb, double y0, double height, double grow, int colour, boolean ends) {
        double ha = d.halfWidthAt(xa + 1e-9) + grow, hb = d.halfWidthAt(xb - 1e-9) + grow;
        double ta = height > 0 ? y0 + height : y0 + d.topAt(xa), tb = height > 0 ? y0 + height : y0 + d.topAt(xb);
        double l = d.lengthM();
        double ea = ends && xa <= -l / 2 + 1e-9 ? xa - grow : xa, eb = ends && xb >= l / 2 - 1e-9 ? xb + grow : xb;
        out.hexahedron(new Vec3d[]{v(ea, y0, -ha), v(ea, y0, ha), v(ea, ta, ha), v(ea, ta, -ha), v(eb, y0, -hb), v(eb, y0, hb), v(eb, tb, hb), v(eb, tb, -hb)}, colour);
    }

    /** An outline (x, y) of a strip lying on a hood's top from xa to xb, thickness t over it (for plate across z). */
    private static double[][] slope(EngineHoodDesign d, double xa, double xb, double y0, double t) {
        double ya = y0 + d.topAt(xa), yb = y0 + d.topAt(xb);
        return new double[][]{{xa, ya}, {xb, yb}, {xb, yb + t}, {xa, ya + t}};
    }

    /**
     * 2026-11-22 (redrawn in Blender): a jack under its beam's tip (the origin): the cap with its hoses' fittings, the
     * barrel with a red and white band, the chrome rod a third out, the ball and the cast float (pad) on it.
     */
    static void outrigger(OutriggerDesign d, PartMesh out) {
        double s = d.strokeM(), pad = d.padDiameterM() / 2, bottom = -0.5 - 0.3 * s;
        out.finish(Finishes.PAINT_WORN);
        out.cylinder(v(0, 0.02, 0), v(0, 0.12, 0), 0.13, 0.13, 20, YELLOW);
        out.cylinder(v(0, -0.5, 0), v(0, 0.02, 0), 0.105, 0.105, 20, YELLOW);
        out.cylinder(v(0, -0.52, 0), v(0, -0.48, 0), 0.115, 0.115, 20, YELLOW);
        out.finish(Finishes.PAINT_GLOSS);
        int[] bands = {RED, WHITE, RED};
        for (int i = 0; i < bands.length; i++) { out.cylinder(v(0, -0.46 + 0.04 * i, 0), v(0, -0.42 + 0.04 * i, 0), 0.107, 0.107, 20, bands[i]); }
        out.finish(Finishes.CHROME).cylinder(v(0, bottom + 0.06, 0), v(0, -0.5, 0), 0.075, 0.075, 16, CHROME);
        out.finish(Finishes.CAST_IRON).lathe(v(0, bottom - 0.06, 0), v(0, bottom + 0.08, 0), new double[]{0, 0, 0.28, 0.5, 0.78, 1, 1},
                new double[]{0, pad, pad, 0.45 * pad, 0.11, 0.09, 0}, null, 24, DARK, true);
        out.finish(Finishes.BRAIDED_HOSE);
        for (double z : new double[]{-0.06, 0.06}) { out.tube(List.of(v(0.1, 0.07, z), v(0.17, 0.07, z), v(0.2, 0.1, z), v(0.2, 0.3, z)), 0.015, null, HOSE, null, 8, false); }
    }

    // ---------------------------------------------------------------- tools

    static void tool(WorkToolDesign d, PartMesh out) {
        double w = Math.max(d.toolWidthM(), 0.1), h = Math.max(d.toolHeightM(), 0.1);
        switch (d.toolKind()) {
            case BACKHOE_BUCKET, TRENCH_BUCKET, LOADER_BUCKET, FACE_SHOVEL_BUCKET, DRAGLINE_BUCKET -> bucket(d, out);
            case CLAMSHELL -> clamshell(d, out);
            case GRAPPLE -> grapple(d, out);
            case DOZER_BLADE, GRADER_MOLDBOARD, SNOW_PLOW -> blade(d, out);
            case DUMP_BODY -> dumpBody(d, out);
            case RIPPER -> ripper(d, out);
            case HYDRAULIC_HAMMER -> hammer(d, out);
            case PILE_HAMMER -> pileHammer(d, out);
            case COMPACTOR_PLATE -> compactor(d, out);
            case QUICK_COUPLER -> coupler(d, out);
            case FORKS -> forks(d, out);
            case WORK_PLATFORM -> platform(d, out);
            case AUGER -> auger(d, out);
            case SPRAY_BAR -> sprayBar(d, out);
            case SEGMENT_ERECTOR -> erector(d, out);
            case SCRAPER_BOWL -> scraperBowl(d, out);
            case PAVER_HOPPER -> paverHopper(d, out);
            case SCREED -> screed(d, out);
            case MILLING_DRUM -> millingDrum(d, out);
            case ROADHEADER_HEAD -> roadheader(d, out);
            case BUCKET_WHEEL -> bucketWheel(d, out);
            case BROOM -> broom(d, out);
            case SNOW_BLOWER -> snowBlower(d, out);
            case CUTTERHEAD -> cutterhead(d, out);
            case TRENCHER_CHAIN -> trencher(d, out);
            case MIXER_DRUM -> mixer(d, out);
            case CONVEYOR -> conveyor(d, out);
            default -> out.finish(Finishes.PAINT_WORN).box(v(-w / 2, -h / 2, -w / 2), v(w / 2, h / 2, w / 2), YELLOW);
        }
    }

    // ---------------------------------------------------------------- 2026-11-22: hanging and handling tools (redrawn in Blender)

    /** An outline (u, v) of half sizes a and b with its corners cut by c, counter-clockwise. */
    static double[][] octagon(double a, double b, double c) {
        return new double[][]{{a, b - c}, {a - c, b}, {-a + c, b}, {-a, b - c}, {-a, -b + c}, {-a + c, -b}, {a - c, -b}, {a, -b + c}};
    }

    /** A bracket's side plate at z0 by a depth: the hull of round bosses about pins {x, y, radius} and a bottom edge half x wide at y. */
    static void lugPlate(PartMesh out, double[][] pins, double bottomY, double halfX, double z0, double depth, int colour) {
        List<double[]> pts = new ArrayList<>();
        for (double[] p : pins) { for (int k = 0; k < 12; k++) { double a = k * Math.PI / 6; pts.add(new double[]{p[0] + p[2] * Math.cos(a), p[1] + p[2] * Math.sin(a)}); } }
        pts.add(new double[]{-halfX, bottomY});
        pts.add(new double[]{halfX, bottomY});
        plate(out, hull(pts), z0, depth, colour);
    }

    /**
     * A ripper about its pin (the origin): the beam, a pocket for each shank with its depth pin, the shank curving down
     * to its tip (pointing forward, +x), narrowing from its chord (the length) and worn steel below, and the tip.
     */
    static void ripper(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), t = Math.max(d.plateM(), 0.03);
        out.finish(Finishes.PAINT_WORN).extrude(octagon(0.22, 0.17, 0.05), v(0, 0.13, -w / 2), X, Y, Z.scale(w), YELLOW, false);
        int n = Math.max(d.teeth(), 1), steps = 10;
        for (int i = 0; i < n; i++) {
            double z = n == 1 ? 0 : -0.4 * w + 0.8 * w * i / (n - 1);
            Vec3d hz = Z.scale(t / 2);
            out.finish(Finishes.PAINT_WORN).box(v(-0.17, -0.12, z - t / 2 - 0.045), v(0.17, 0.45, z + t / 2 + 0.045), YELLOW);
            out.finish(Finishes.FORGED_STEEL).cylinder(v(0.05, 0.3, z - t / 2 - 0.07), v(0.05, 0.3, z + t / 2 + 0.07), 0.035, 0.035, 10, STEEL);
            Vec3d p0 = v(0.05, 0.3, z), p1 = v(-0.05, -0.55 * h, z), p2 = v(0.22, -h + 0.06, z);
            Vec3d[] pts = new Vec3d[steps + 1];
            for (int k = 0; k <= steps; k++) { double s = (double) k / steps; pts[k] = p0.scale((1 - s) * (1 - s)).add(p1.scale(2 * s * (1 - s))).add(p2.scale(s * s)); }
            for (int k = 0; k < steps; k++) {
                Vec3d a = pts[k], b = pts[k + 1];
                Vec3d ta = MeshBuilder.unit(pts[Math.min(k + 1, steps)].subtract(pts[Math.max(k - 1, 0)])), tb = MeshBuilder.unit(pts[Math.min(k + 2, steps)].subtract(pts[k]));
                Vec3d na = v(ta.y(), -ta.x(), 0), nb = v(tb.y(), -tb.x(), 0);
                double da = l * (1 - 0.55 * k / steps), db = l * (1 - 0.55 * (k + 1) / steps);
                Vec3d fa = a.subtract(na.scale(0.5 * da)), fb = b.subtract(nb.scale(0.5 * db)), ba = a.add(na.scale(0.5 * da)), bb = b.add(nb.scale(0.5 * db));
                out.finish(k < 4 ? Finishes.PAINT_WORN : Finishes.FORGED_STEEL).hexahedron(new Vec3d[]{fa.subtract(hz), fb.subtract(hz), bb.subtract(hz), ba.subtract(hz),
                        fa.add(hz), fb.add(hz), bb.add(hz), ba.add(hz)}, k < 4 ? YELLOW : WEAR);
            }
            Vec3d e = pts[steps], te = MeshBuilder.unit(e.subtract(pts[steps - 1])), ne = v(te.y(), -te.x(), 0), tip = e.add(te.scale(0.28));
            double de = 0.45 * l;
            Vec3d hz1 = Z.scale(t / 2 + 0.008), hz2 = Z.scale(t / 4);
            out.finish(Finishes.FORGED_STEEL).hexahedron(new Vec3d[]{e.subtract(ne.scale(0.5 * de)).subtract(hz1), e.add(ne.scale(0.5 * de)).subtract(hz1),
                    tip.add(ne.scale(0.02)).subtract(hz2), tip.subtract(ne.scale(0.02)).subtract(hz2), e.subtract(ne.scale(0.5 * de)).add(hz1),
                    e.add(ne.scale(0.5 * de)).add(hz1), tip.add(ne.scale(0.02)).add(hz2), tip.subtract(ne.scale(0.02)).add(hz2)}, WEAR);
        }
    }

    /** The bracket a hanging tool is pinned by (its pins at the origin and 0.3 m ahead), its plates z0 apart, down to y. */
    private static void bracket(PartMesh out, double bottomY, double halfX, double halfZ) {
        double[][] pins = {{0, 0, 0.09}, {0.3, -0.02, 0.08}};
        out.finish(Finishes.PAINT_WORN);
        lugPlate(out, pins, bottomY, halfX, -halfZ, 0.025, YELLOW);
        lugPlate(out, pins, bottomY, halfX, halfZ - 0.025, 0.025, YELLOW);
        out.finish(Finishes.FORGED_STEEL);
        for (double[] p : pins) { out.cylinder(v(p[0], p[1], -halfZ - 0.04), v(p[0], p[1], halfZ + 0.04), 0.04, 0.04, 10, STEEL); }
    }

    /**
     * A hydraulic breaker below its bracket (the origin): the bracket, the silenced housing (chamfered, an access window
     * a side), the front head with its retaining pins, the chisel to y = -l, and the hoses.
     */
    static void hammer(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), ys = -0.32, yh = -0.72 * l, yf = yh - 0.14, rc = 0.15 * w;
        bracket(out, ys, 0.55 * w, h / 2 - 0.01);
        out.finish(Finishes.PAINT_WORN).box(v(-0.55 * w, ys - 0.04, -h / 2), v(0.55 * w, ys, h / 2), YELLOW);
        out.extrude(octagon(w / 2, h / 2, 0.18 * w), v(0, ys - 0.04, 0), X, Z, Y.scale(yh - ys + 0.04), YELLOW, false);
        out.finish(Finishes.CAST_IRON);
        out.box(v(-0.3 * w, 0.75 * yh, h / 2 - 0.002), v(0.3 * w, 0.35 * yh, h / 2 + 0.004), DARK);
        out.box(v(-0.3 * w, 0.75 * yh, -h / 2 - 0.004), v(0.3 * w, 0.35 * yh, -h / 2 + 0.002), DARK);
        out.extrude(octagon(0.36 * w, 0.36 * h, 0.1 * w), v(0, yh, 0), X, Z, Y.scale(yf - yh), DARK, false);
        out.finish(Finishes.FORGED_STEEL);
        for (double y : new double[]{yh - 0.05, yh - 0.1}) { out.cylinder(v(0, y, -0.38 * h), v(0, y, 0.38 * h), 0.02, 0.02, 8, STEEL); }
        out.cylinder(v(0, yf, 0), v(0, -l + 0.12, 0), rc, rc, 14, WEAR);
        out.cylinder(v(0, -l + 0.12, 0), v(0, -l, 0), rc, 0.01, 14, WEAR);
        out.finish(Finishes.BRAIDED_HOSE);
        for (double z : new double[]{-0.12 * h, 0.12 * h}) {
            out.tube(List.of(v(-0.3 * w, ys + 0.05, z), v(-0.55 * w, ys + 0.1, z), v(-0.6 * w, 0.1, z), v(-0.5 * w, 0.35, z)), 0.02, null, HOSE, null, 8, false);
        }
    }

    /**
     * A hydraulic impact hammer hung in its leader (the origin at its bail): the bail's sheave and cheeks, the chamfered
     * housing with the ram's window, the leader guides on its back (-x), the helmet and its cushion down to y = -l.
     */
    static void pileHammer(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), yt = -0.25, yb = -0.82 * l, yc = yb - 0.6 * (l + yb);
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(0, -0.12, -0.12), v(0, -0.12, 0.12), 0.17, 0.17, 18, STEEL);
        out.finish(Finishes.PAINT_WORN);
        out.box(v(-0.2, -0.3, -0.16), v(0.2, 0.02, -0.13), YELLOW);
        out.box(v(-0.2, -0.3, 0.13), v(0.2, 0.02, 0.16), YELLOW);
        out.extrude(octagon(w / 2, h / 2, 0.15 * w), v(0, yt, 0), X, Z, Y.scale(yb - yt), YELLOW, false);
        out.finish(Finishes.CAST_IRON).box(v(w / 2 - 0.002, 0.85 * yb, -0.25 * h), v(w / 2 + 0.004, 0.55 * yb, 0.25 * h), DARK);
        for (double y0 : new double[]{yt - 0.1, yb + 0.5}) {
            for (double s : new double[]{-1, 1}) {
                out.box(v(-w / 2 - 0.16, y0 - 0.35, s * 0.32 * h - 0.07), v(-w / 2, y0, s * 0.32 * h + 0.07), DARK);
                out.box(v(-w / 2 - 0.2, y0 - 0.35, Math.min(s * 0.32 * h - 0.07 - s * 0.05, s * 0.32 * h + 0.07)), v(-w / 2 - 0.16, y0, Math.max(s * 0.32 * h - 0.07 - s * 0.05, s * 0.32 * h + 0.07)), DARK);
            }
        }
        out.finish(Finishes.PAINT_MATTE).box(v(-0.58 * w, yc, -0.58 * h), v(0.58 * w, yb, 0.58 * h), BLACK);
        out.finish(Finishes.CAST_IRON).box(v(-0.45 * w, -l, -0.45 * h), v(0.45 * w, yc, 0.45 * h), DARK);
        out.finish(Finishes.BRAIDED_HOSE);
        for (double z : new double[]{-0.18 * h, 0.18 * h}) {
            out.tube(List.of(v(0.3 * w, yt - 0.05, z), v(0.45 * w, yt + 0.1, z), v(0.45 * w, 0.4, z), v(0.2, 0.8, z)), 0.035, null, HOSE, null, 8, false);
        }
    }

    /**
     * A plate compactor below its bracket (the origin): the bracket, the upper frame on four rubber mounts, the exciter
     * with its motor and hoses, and the base plate (l along x by w, its bottom at y = -h) with its turned-up ends.
     */
    static void compactor(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), yb = -h;
        out.finish(Finishes.CAST_IRON).box(v(-l / 2 + 0.08, yb, -w / 2), v(l / 2 - 0.08, yb + 0.03, w / 2), DARK);
        for (double s : new double[]{-1, 1}) {
            double a = s * (l / 2 - 0.08), b = s * l / 2;
            out.hexahedron(new Vec3d[]{v(a, yb, -w / 2), v(a, yb + 0.03, -w / 2), v(b, yb + 0.1, -w / 2), v(b - s * 0.03, yb + 0.1, -w / 2),
                    v(a, yb, w / 2), v(a, yb + 0.03, w / 2), v(b, yb + 0.1, w / 2), v(b - s * 0.03, yb + 0.1, w / 2)}, DARK);
        }
        out.box(v(-0.16, yb + 0.03, -0.32 * w), v(0.16, yb + 0.12, 0.32 * w), DARK);
        out.cylinder(v(0, yb + 0.2, -0.36 * w), v(0, yb + 0.2, 0.36 * w), 0.11, 0.11, 16, DARK);
        out.cylinder(v(0, yb + 0.2, 0.36 * w), v(0, yb + 0.2, 0.36 * w + 0.1), 0.07, 0.07, 12, DARK);
        out.finish(Finishes.RUBBER);
        for (double sx : new double[]{-1, 1}) for (double sz : new double[]{-1, 1}) {
            out.cylinder(v(sx * 0.3 * l, yb + 0.12, sz * 0.3 * w), v(sx * 0.3 * l, yb + 0.34, sz * 0.3 * w), 0.05, 0.05, 10, RUBBER);
        }
        out.finish(Finishes.PAINT_WORN).box(v(-0.38 * l, yb + 0.34, -0.4 * w), v(0.38 * l, yb + 0.42, 0.4 * w), YELLOW);
        bracket(out, yb + 0.42, 0.36 * l, 0.22);
        out.finish(Finishes.BRAIDED_HOSE);
        for (double x : new double[]{-0.04, 0.04}) {
            out.tube(List.of(v(x, yb + 0.2, 0.36 * w + 0.1), v(x, yb + 0.28, 0.36 * w + 0.16), v(x - 0.1, yb + 0.5, 0.2)), 0.015, null, HOSE, null, 8, false);
        }
    }

    /**
     * A quick coupler between the stick and a tool: side plates round the stick's pin (the origin) and the link's (the
     * ram's rod pin), the deck, the front hook and the rear latch's wedge underneath (its bottom at y = -h), the latch's
     * cylinder.
     */
    static void coupler(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM();
        Vec3d rod = d.rammed() && d.ramGeometry() != null ? d.ramGeometry().rodPin() : v(0.7 * l, 0.4 * h, 0);
        double[][] pins = {{0, 0, 0.1}, {rod.x(), rod.y(), 0.09}};
        out.finish(Finishes.PAINT_WORN);
        lugPlate(out, pins, -h + 0.1, l / 2, -w / 2, 0.035, YELLOW);
        lugPlate(out, pins, -h + 0.1, l / 2, w / 2 - 0.035, 0.035, YELLOW);
        out.box(v(-l / 2, -h + 0.1, -w / 2), v(l / 2, -h + 0.17, w / 2), YELLOW);
        out.box(v(-0.45 * l, -h, -w / 2 + 0.04), v(-0.25 * l, -h + 0.1, w / 2 - 0.04), YELLOW);
        out.finish(Finishes.CAST_IRON);
        out.cylinder(v(-0.36 * l, -h + 0.06, -w / 2 + 0.04), v(-0.36 * l, -h + 0.06, w / 2 - 0.04), 0.055, 0.055, 12, DARK);
        out.cylinder(v(-0.2 * l, -h + 0.21, 0), v(0.25 * l, -h + 0.21, 0), 0.04, 0.04, 10, DARK);
        out.finish(Finishes.FORGED_STEEL).box(v(0.25 * l, -h, -w / 2 + 0.04), v(0.45 * l, -h + 0.1, w / 2 - 0.04), WEAR);
        for (Vec3d p : new Vec3d[]{Vec3d.ZERO, rod}) { out.cylinder(v(p.x(), p.y(), -w / 2 - 0.06), v(p.x(), p.y(), w / 2 + 0.06), 0.04, 0.04, 10, STEEL); }
    }

    /**
     * A demolition grapple from its head's hinge (the origin): the head and its rotator, and two jaws of curved tines
     * (three on one side, two between them on the other) with their cross bars and steel tips, closed below the hinge.
     */
    static void grapple(WorkToolDesign d, PartMesh out) {
        double b = d.toolLengthM(), h = d.toolHeightM(), w = d.toolWidthM(), t = Math.max(1.5 * d.plateM(), 0.03);
        int steps = 8;
        out.finish(Finishes.PAINT_WORN);
        out.box(v(-0.28 * b, 0, -0.3 * w), v(0.28 * b, 0.32 * h, 0.3 * w), YELLOW);
        out.cylinder(v(0, 0.5 * h, 0), v(0, 0.56 * h, 0), 0.26 * w, 0.26 * w, 18, YELLOW);
        out.finish(Finishes.CAST_IRON).cylinder(v(0, 0.32 * h, 0), v(0, 0.5 * h, 0), 0.22 * w, 0.22 * w, 18, DARK);
        for (double s : new double[]{-1, 1}) {
            double[] zs = s < 0 ? new double[]{-0.38 * w, 0, 0.38 * w} : new double[]{-0.19 * w, 0.19 * w};
            for (double z : zs) {
                List<Vec3d> path = new ArrayList<>();
                double[] radii = new double[steps + 1];
                for (int i = steps; i >= 0; i--) {
                    double a = 0.5 * Math.PI * i / steps;
                    path.add(v(s * 0.49 * b * Math.cos(a), -h * Math.sin(a) + 0.05, z));
                    radii[steps - i] = t * (1.6 - 0.9 * i / steps);
                }
                out.finish(Finishes.PAINT_WORN).tube(path, t, radii, YELLOW, null, 8, false);
                Vec3d tip = path.get(steps);
                out.finish(Finishes.FORGED_STEEL).cylinder(tip, tip.add(v(-s * 0.06, -0.08, 0)), 0.7 * t, 0.01, 8, WEAR);
            }
            out.finish(Finishes.PAINT_WORN);
            for (int i : new int[]{2, 5}) {
                double a = 0.5 * Math.PI * i / steps;
                Vec3d p = v(s * 0.49 * b * Math.cos(a), -h * Math.sin(a) + 0.05, 0);
                out.cylinder(p.subtract(Z.scale(0.42 * w)), p.add(Z.scale(0.42 * w)), 0.6 * t, 0.6 * t, 8, YELLOW);
            }
        }
    }

    /**
     * Pallet forks on their carriage (the origin at its bottom): the carriage's two bars, the load backrest above it, and
     * two forks (a hooked shank and a tine tapering to its tip, l long) a third of the width out.
     */
    static void forks(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), t = Math.max(d.plateM(), 0.04);
        out.finish(Finishes.PAINT_MATTE);
        out.box(v(-0.07, 0.02, -w / 2), v(0, 0.13, w / 2), BLACK);
        out.box(v(-0.07, 0.36, -w / 2), v(0, 0.48, w / 2), BLACK);
        for (int i = 0; i < 6; i++) { double z = -w / 2 + 0.02 + (w - 0.04) * i / 5; out.box(v(-0.06, 0.13, z - 0.02), v(-0.02, h, z + 0.02), BLACK); }
        out.box(v(-0.06, h - 0.05, -w / 2), v(-0.02, h, w / 2), BLACK);
        out.box(v(-0.06, 0.62, -w / 2), v(-0.02, 0.66, w / 2), BLACK);
        out.finish(Finishes.FORGED_STEEL);
        for (double z : new double[]{-w / 3, w / 3}) {
            out.box(v(0, 0, z - 0.06), v(t, 0.5, z + 0.06), DARK);
            out.box(v(-0.09, 0.46, z - 0.06), v(t, 0.52, z + 0.06), DARK);
            out.box(v(-0.09, 0.1, z - 0.06), v(t, 0.15, z + 0.06), DARK);
            out.box(v(0, 0, z - 0.06), v(l - 0.25, t, z + 0.06), DARK);
            out.hexahedron(new Vec3d[]{v(l - 0.25, 0, z - 0.06), v(l - 0.25, t, z - 0.06), v(l, 0.012, z - 0.055), v(l, 0, z - 0.055),
                    v(l - 0.25, 0, z + 0.06), v(l - 0.25, t, z + 0.06), v(l, 0.012, z + 0.055), v(l, 0, z + 0.055)}, DARK);
        }
    }

    /**
     * A work platform's basket (its floor from x 0 to l, w across, its rails h high): the aluminium floor, toe boards,
     * posts, the top and middle rails all round, the control console on the front rail and the rotator's mount behind.
     */
    static void platform(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM();
        out.finish(Finishes.NONSKID).box(v(0, 0, -w / 2), v(l, 0.05, w / 2), ALU);
        out.finish(Finishes.PAINT_GLOSS);
        out.box(v(0, 0.05, -w / 2), v(l, 0.2, -w / 2 + 0.01), YELLOW);
        out.box(v(0, 0.05, w / 2 - 0.01), v(l, 0.2, w / 2), YELLOW);
        out.box(v(0, 0.05, -w / 2), v(0.01, 0.2, w / 2), YELLOW);
        out.box(v(l - 0.01, 0.05, -w / 2), v(l, 0.2, w / 2), YELLOW);
        int nz = Math.max(2, (int) Math.round(w / 0.8));
        for (int i = 0; i <= nz; i++) {
            double z = -w / 2 + 0.02 + (w - 0.04) * i / nz;
            for (double x : new double[]{0.02, l - 0.02}) { out.cylinder(v(x, 0.05, z), v(x, h, z), 0.022, 0.022, 8, YELLOW); }
        }
        for (double y : new double[]{h, 0.55 * h}) {
            Vec3d[] ring = {v(0.02, y, -w / 2 + 0.02), v(l - 0.02, y, -w / 2 + 0.02), v(l - 0.02, y, w / 2 - 0.02), v(0.02, y, w / 2 - 0.02)};
            for (int i = 0; i < 4; i++) { out.cylinder(ring[i], ring[(i + 1) % 4], 0.024, 0.024, 8, YELLOW); }
        }
        out.finish(Finishes.PAINT_WORN).box(v(-0.25, -0.25, -0.22), v(0, 0.45, 0.22), YELLOW);
        out.finish(Finishes.PLASTIC);
        out.box(v(l - 0.02, 0.62 * h, 0.15 * w - 0.2), v(l + 0.14, 0.95 * h, 0.15 * w + 0.2), DARK);
        out.box(v(l + 0.02, 0.95 * h, 0.15 * w - 0.18), v(l + 0.16, 0.97 * h, 0.15 * w + 0.18), BLACK);
    }

    /**
     * A drill auger from its drive (the origin) down to its tip at y = -l: the kelly's square socket, the stem, the flight
     * wound round it (its pitch 0.8 of its diameter), the pilot point and the teeth round its tip.
     */
    static void auger(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), l = d.toolLengthM(), ro = w / 2, rs = Math.max(0.06, 0.18 * w), y0 = -0.4, y1 = -l + 0.12;
        out.finish(Finishes.CAST_IRON).box(v(-0.1, -0.28, -0.1), v(0.1, 0, 0.1), DARK);
        out.finish(Finishes.FORGED_STEEL).cylinder(v(0, -0.28, 0), v(0, -l + 0.05, 0), rs, rs, 12, STEEL);
        double turns = (y0 - y1) / (0.8 * w);
        List<Vec3d> outer = MeshBuilder.helix(v(0, y0, 0), Y.scale(-1), ro, y0 - y1, turns, 16), inner = MeshBuilder.helix(v(0, y0, 0), Y.scale(-1), rs, y0 - y1, turns, 16);
        out.sheet(outer, inner, STEEL, true);
        out.cylinder(v(0, y1, 0), v(0, -l, 0), rs, 0.01, 10, WEAR);
        int n = Math.max(d.teeth(), 2);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            Vec3d c = v(0.8 * ro * Math.cos(a), -l + 0.08, 0.8 * ro * Math.sin(a));
            out.box(c.subtract(v(0.035, 0.05, 0.035)), c.add(v(0.035, 0.03, 0.035)), WEAR);
        }
    }

    /** A spray bar (w across): the pipe and its end caps, a nozzle every 0.3 m pointing down, two brackets, the feed hose from its middle. */
    static void sprayBar(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM();
        int n = Math.max(2, (int) (w / 0.3));
        out.finish(Finishes.BRUSHED_STEEL).cylinder(v(0, 0, -w / 2), v(0, 0, w / 2), 0.03, 0.03, 10, STEEL);
        out.finish(Finishes.CAST_IRON);
        for (double s : new double[]{-1, 1}) { out.cylinder(v(0, 0, s * w / 2), v(0, 0, s * (w / 2 + 0.02)), 0.036, 0.036, 10, DARK); }
        for (double z : new double[]{-0.3 * w, 0.3 * w}) { out.box(v(-0.04, 0, z - 0.02), v(0.04, 0.15, z + 0.02), DARK); }
        out.finish(Finishes.PLASTIC);
        for (int i = 0; i <= n; i++) {
            double z = -w / 2 + 0.1 + (w - 0.2) * i / n;
            out.cylinder(v(0, -0.02, z), v(0, -0.07, z), 0.012, 0.012, 6, BLACK);
            out.cylinder(v(0, -0.07, z), v(0, -0.09, z), 0.008, 0.016, 6, BLACK);
        }
        out.finish(Finishes.BRAIDED_HOSE).tube(List.of(v(0, 0.03, 0), v(0, 0.12, 0), v(-0.05, 0.2, 0), v(-0.15, 0.25, 0)), 0.025, null, HOSE, null, 8, false);
    }

    /**
     * A segment erector's lifting head (up from its base, the origin): the base plate, two guide columns, the cross
     * head, and the gripping plate curved round the tunnel (3 m radius) with its rubber pads, its top at y = h.
     */
    static void erector(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), r = 3.0;
        out.finish(Finishes.PAINT_WORN);
        out.box(v(-l / 2, 0, -w / 2), v(l / 2, 0.12, w / 2), YELLOW);
        for (double s : new double[]{-1, 1}) { out.box(v(-0.12, 0.12, s * 0.4 - 0.1), v(0.12, h - 0.2, s * 0.4 + 0.1), YELLOW); }
        out.box(v(-0.4 * l, h - 0.2, -0.45 * w), v(0.4 * l, h - 0.1, 0.45 * w), YELLOW);
        List<Vec3d> back = new ArrayList<>(), front = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            double z = -w / 2 + w * i / 8, y = h - 0.02 - (r - Math.sqrt(r * r - z * z));
            back.add(v(-l / 2, y, z));
            front.add(v(l / 2, y, z));
        }
        out.finish(Finishes.CAST_IRON).sheet(back, front, DARK, true);
        out.finish(Finishes.RUBBER);
        for (double x : new double[]{-0.25 * l, 0.25 * l}) for (double z : new double[]{-0.3 * w, 0.3 * w}) {
            double y = h - 0.02 - (r - Math.sqrt(r * r - z * z));
            out.cylinder(v(x, y, z), v(x, y + 0.04, z), 0.1, 0.1, 12, RUBBER);
        }
    }

    // ---------------------------------------------------------------- 2026-11-22: rotating tools and bowls (redrawn in Blender)

    /** Picks in a helix along a drum from a to b (alternate ones opposite): each a holder and a pointed bit standing out from the radius there. */
    static void picks(PartMesh out, Vec3d a, Vec3d b, java.util.function.DoubleUnaryOperator radius, int count, double turns, double size) {
        Vec3d u = MeshBuilder.unit(b.subtract(a));
        double length = b.subtract(a).length();
        Vec3d[] e = MeshBuilder.perpendiculars(u);
        for (int i = 0; i < count; i++) {
            double s = (i + 0.5) / count, ang = 2 * Math.PI * turns * s + (i % 2 == 1 ? Math.PI : 0);
            Vec3d n = e[0].scale(Math.cos(ang)).add(e[1].scale(Math.sin(ang))), c = a.add(u.scale(length * s)).add(n.scale(radius.applyAsDouble(s)));
            out.finish(Finishes.CAST_IRON).cylinder(c.subtract(n.scale(0.01)), c.add(n.scale(0.6 * size)), 0.45 * size, 0.35 * size, 6, DARK);
            out.finish(Finishes.FORGED_STEEL).cylinder(c.add(n.scale(0.6 * size)), c.add(n.scale(1.5 * size)), 0.22 * size, 0.02, 6, WEAR);
        }
    }

    /** Points on an arc of a radius about the z axis (degrees from +x toward +y), from one angle to another by a step. */
    private static List<Vec3d> arcZ(double r, double cx, double cy, double fromDeg, double toDeg, double stepDeg) {
        List<Vec3d> out = new ArrayList<>();
        for (double t = fromDeg; t <= toDeg + 1e-9; t += stepDeg) { out.add(v(cx + r * Math.cos(Math.toRadians(t)), cy + r * Math.sin(Math.toRadians(t)), 0)); }
        return out;
    }

    /** The same points moved along z. */
    private static List<Vec3d> shifted(List<Vec3d> points, double z) {
        List<Vec3d> out = new ArrayList<>();
        for (Vec3d p : points) { out.add(p.add(Z.scale(z))); }
        return out;
    }

    /** A sheet across z from -half to +half along a curve in the x-y plane, both faces. */
    private static void band(PartMesh out, List<Vec3d> curve, double half, int colour) { out.sheet(shifted(curve, -half), shifted(curve, half), colour, true); }

    /** An end plate in the x-y plane: the curve's points and more (x, y) corners, at z0 by a depth along z. */
    private static void endPlate(PartMesh out, List<Vec3d> curve, double[][] more, double z0, double depth, int colour) {
        double[][] outline = new double[curve.size() + more.length][];
        for (int i = 0; i < curve.size(); i++) { outline[i] = new double[]{curve.get(i).x(), curve.get(i).y()}; }
        System.arraycopy(more, 0, outline, curve.size(), more.length);
        plate(out, outline, z0, depth, colour);
    }

    /**
     * A milling drum about z (its axis at the origin): the tube with picks in two opposed helices to the middle and the
     * loading paddles there, the hood arching over it with its side plates down to the cut, the moldboard behind with its
     * edge, and the conveyor's mouth ahead.
     */
    static void millingDrum(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), r = d.cutterRadiusM(), rt = r - 0.07, hood = r + 0.12;
        int n = Math.max(d.teeth() / 2, 4);
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(0, 0, -w / 2), v(0, 0, w / 2), rt, rt, 28, STEEL);
        out.detail(1);
        picks(out, v(0, 0, -w / 2 + 0.03), v(0, 0, -0.04), s -> rt, n, 1.6, 0.05);
        picks(out, v(0, 0, w / 2 - 0.03), v(0, 0, 0.04), s -> rt, n, 1.6, 0.05);
        out.detail(0).finish(Finishes.FORGED_STEEL);
        for (double a : new double[]{0, Math.PI}) {
            Vec3d c = v(rt * Math.cos(a), rt * Math.sin(a), 0);
            out.box(c.subtract(v(0.04, 0.04, 0.12)), c.add(v(0.04, 0.04, 0.12)).add(MeshBuilder.unit(c).scale(0.06)), WEAR);
        }
        List<Vec3d> arc = arcZ(hood, 0, 0, -10, 190, 20);
        out.finish(Finishes.PAINT_WORN);
        band(out, arc, w / 2 + 0.02, YELLOW);
        double[][] down = {{-hood, -r + 0.02}, {hood, -r + 0.02}};
        endPlate(out, arc, down, -w / 2 - 0.02, 0.03, YELLOW);
        endPlate(out, arc, down, w / 2 - 0.01, 0.03, YELLOW);
        out.box(v(-hood - 0.03, -r - 0.02, -w / 2), v(-hood, 0.1, w / 2), YELLOW);
        out.finish(Finishes.FORGED_STEEL).box(v(-hood - 0.05, -r - 0.04, -w / 2), v(-hood + 0.01, -r + 0.03, w / 2), WEAR);
        out.finish(Finishes.CAST_IRON).box(v(hood - 0.05, 0.05, -0.32), v(hood + 0.25, 0.3, 0.32), DARK);
    }

    /** A roadheader's transverse heads about z: the gearbox in the middle (and its boom's end behind) and a head each side tapering to its nose, picks round it in a helix. */
    static void roadheader(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), r = d.cutterRadiusM(), wg = 0.24 * w;
        out.finish(Finishes.PAINT_WORN).cylinder(v(0, 0, -wg / 2), v(0, 0, wg / 2), 0.62 * r, 0.62 * r, 20, YELLOW);
        out.box(v(-1.6 * r, -0.35 * r, -wg / 2 + 0.02), v(-0.6 * r, 0.35 * r, wg / 2 - 0.02), YELLOW);
        java.util.function.DoubleUnaryOperator profile = q -> 0.82 * r + 0.08 * r * Math.min(q / 0.4, 1) - Math.max(0, q - 0.4) / 0.6 * 0.4 * r - 0.01;
        for (double s : new double[]{-1, 1}) {
            Vec3d a = v(0, 0, s * wg / 2), b = v(0, 0, s * w / 2);
            out.finish(Finishes.MACHINED_STEEL).lathe(a, b, new double[]{0, 0, 0.05, 0.4, 0.85, 1, 1}, new double[]{0, 0.78 * r, 0.82 * r, 0.9 * r, 0.75 * r, 0.45 * r, 0}, null, 20, STEEL, false);
            out.detail(1);
            picks(out, a.add(Z.scale(s * 0.03)), b.subtract(Z.scale(s * 0.06)), profile, Math.max(d.teeth() / 2, 6), 2.2, 0.06);
            out.detail(0);
        }
    }

    /**
     * A bucket wheel about z (radius r): the hub, two sets of spokes, the box rim (two rings and the ring chute between),
     * and the buckets round it (as many as its teeth), each a wedge with a lip of four teeth facing its turn.
     */
    static void bucketWheel(WorkToolDesign d, PartMesh out) {
        double r = d.cutterRadiusM(), w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), ri = 0.78 * r, ro = 0.86 * r;
        int n = Math.max(d.teeth(), 6);
        out.finish(Finishes.CAST_IRON).cylinder(v(0, 0, -0.45 * w), v(0, 0, 0.45 * w), 0.12 * r, 0.12 * r, 20, DARK);
        out.lathe(v(0, 0, -0.3 * w), v(0, 0, 0.3 * w), new double[]{0, 0, 1, 1, 0}, new double[]{ri, ri + 0.03, ri + 0.03, ri, ri}, null, 48, DARK, true);
        out.finish(Finishes.PAINT_WORN);
        for (int i = 0; i < 8; i++) {
            double a = 2 * Math.PI * (i + 0.5) / 8;
            for (double z : new double[]{-0.3 * w, 0.3 * w}) { out.cylinder(v(0.1 * r * Math.cos(a), 0.1 * r * Math.sin(a), z), v(0.8 * r * Math.cos(a), 0.8 * r * Math.sin(a), z), 0.025 * r, 0.02 * r, 8, YELLOW); }
        }
        for (double[] zz : new double[][]{{-0.5 * w, -0.3 * w}, {0.3 * w, 0.5 * w}}) {
            out.lathe(v(0, 0, zz[0]), v(0, 0, zz[1]), new double[]{0, 0, 1, 1, 0}, new double[]{ri, ro, ro, ri, ri}, null, 48, YELLOW, true);
        }
        Vec3d hz = Z.scale(w / 2);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            Vec3d rad = v(Math.cos(a), Math.sin(a), 0), tan = v(-Math.sin(a), Math.cos(a), 0);
            Vec3d base = rad.scale(ro), lip = rad.scale(r + 0.02), back = base.subtract(tan.scale(0.8 * l));
            Vec3d p0 = base.subtract(tan.scale(0.2 * l)), p1 = lip.add(tan.scale(0.25 * l)), p2 = back.add(rad.scale(0.35 * h)).subtract(tan.scale(0.3 * l));
            out.finish(Finishes.PAINT_WORN).hexahedron(new Vec3d[]{p0.subtract(hz), p1.subtract(hz), p1.add(hz), p0.add(hz), back.subtract(hz), p2.subtract(hz), p2.add(hz), back.add(hz)}, YELLOW);
            out.finish(Finishes.FORGED_STEEL);
            for (int k = 0; k < 4; k++) {
                Vec3d tp = p1.add(v(0, 0, -0.38 * w + 0.76 * w * k / 3));
                out.cylinder(tp, tp.add(tan.scale(0.18)).add(rad.scale(0.04)), 0.045, 0.01, 6, WEAR);
            }
        }
    }

    /** A pickup broom about z: the brush (dark bristles, in rings at detail 1) on its shaft, the hood arching over it with its end plates, the hopper behind and the rubber flap. */
    static void broom(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), r = d.cutterRadiusM(), h = d.toolHeightM(), l = d.toolLengthM(), hood = r + 0.06;
        out.finish(Finishes.RUBBER).cylinder(v(0, 0, -w / 2), v(0, 0, w / 2), r - 0.02, r - 0.02, 20, BLACK);
        out.detail(1);
        for (int i = 0; i < (int) (w / 0.06); i++) { double z = -w / 2 + 0.03 + 0.06 * i; out.cylinder(v(0, 0, z - 0.012), v(0, 0, z + 0.012), r, r, 20, BLACK); }
        out.detail(0).box(v(-hood - 0.02, -r - 0.02, -w / 2), v(-hood + 0.03, -0.6 * r, w / 2), RUBBER);
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(0, 0, -w / 2 - 0.08), v(0, 0, w / 2 + 0.08), 0.05, 0.05, 10, STEEL);
        List<Vec3d> arc = arcZ(hood, 0, 0, -20, 200, 20);
        out.finish(Finishes.PAINT_WORN);
        band(out, arc, w / 2 + 0.02, YELLOW);
        double[][] down = {{-hood, -0.4 * r}};
        endPlate(out, arc, down, -w / 2 - 0.02, 0.02, YELLOW);
        endPlate(out, arc, down, w / 2, 0.02, YELLOW);
        out.box(v(-hood - l, -0.6 * r, -w / 2 - 0.02), v(-hood, h - r, w / 2 + 0.02), YELLOW);
        out.finish(Finishes.CAST_IRON).box(v(-hood - l - 0.02, h - r - 0.02, -w / 2 - 0.04), v(-hood + 0.02, h - r + 0.03, w / 2 + 0.04), DARK);
    }

    /**
     * A snow blower: the housing curving round the auger (open forward, +x) with its end plates and scraper bar, the
     * auger's two opposed ribbons on their shaft, the fan housing behind the middle and the chute rising from it with its
     * deflector.
     */
    static void snowBlower(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), r = d.cutterRadiusM(), h = d.toolHeightM(), hr = 0.5 * h, cy = hr - r - 0.05;
        List<Vec3d> arc = new ArrayList<>();
        for (int t = -90; t <= 90; t += 15) { double a = Math.toRadians(t); arc.add(v(-hr * Math.cos(a) + 0.08, hr * Math.sin(a) + cy, 0)); }
        double yb = arc.get(0).y(), yt = arc.get(arc.size() - 1).y(), xf = arc.get(arc.size() / 2).x();
        out.finish(Finishes.PAINT_WORN);
        band(out, arc, w / 2, YELLOW);
        double[][] front = {{0.25, yt}, {0.25, yb}};
        endPlate(out, arc, front, -w / 2, 0.02, YELLOW);
        endPlate(out, arc, front, w / 2 - 0.02, 0.02, YELLOW);
        out.cylinder(v(xf, 0, 0), v(xf - 0.25, 0, 0), 0.32, 0.32, 18, YELLOW);
        Vec3d top = v(xf - 0.12, 0.28, 0);
        out.tube(List.of(top, top.add(v(0, 0.3, 0)), top.add(v(0.05, 0.55, 0)), top.add(v(0.25, 0.7, 0))), 0.12, null, YELLOW, null, 12, false);
        out.box(top.add(v(0.18, 0.62, -0.13)), top.add(v(0.42, 0.66, 0.13)), YELLOW);
        out.finish(Finishes.FORGED_STEEL).box(v(-0.05, yb - 0.03, -w / 2), v(0.3, yb + 0.01, w / 2), WEAR);
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(0, 0, -w / 2), v(0, 0, w / 2), 0.04, 0.04, 10, STEEL);
        out.finish(Finishes.PAINT_GLOSS);
        for (double s : new double[]{-1, 1}) {
            out.tube(MeshBuilder.helix(v(0, 0, s * (w / 2 - 0.05)), Z.scale(-s), r - 0.03, w / 2 - 0.2, 2.2, 14), 0.03, null, RED, null, 6, false);
        }
    }

    /** The x of a cutterhead's domed face at a radius (the lathe's profile: flat to 0.6 r, falling to the rim). */
    private static double faceX(double rad, double r) {
        if (rad <= 0.6 * r) { return 0.12; }
        if (rad <= 0.96 * r) { return 0.12 - (rad - 0.6 * r) / (0.36 * r) * (0.12 - 0.0456); }
        return 0.0456 - (rad - 0.96 * r) / (0.04 * r) * (0.0456 + 0.035);
    }

    /**
     * A TBM cutterhead about x (its face at x = 0, the head behind toward -x): the domed face, six raised spokes with the
     * muck openings between them, the disc cutters on a spiral (each rolling about its radius) in their housings, the
     * buckets and gauge cutters round the rim, the centre cutter, and the head's shell behind.
     */
    static void cutterhead(WorkToolDesign d, PartMesh out) {
        double r = Math.max(d.cutterRadiusM(), 0.5), l = d.toolLengthM(), rd = d.discDiameterM() / 2;
        out.finish(Finishes.MACHINED_STEEL).lathe(v(-0.5, 0, 0), v(0.12, 0, 0), new double[]{0, 0, 0.75, 0.88, 1, 1}, new double[]{0, r, r, 0.96 * r, 0.6 * r, 0}, null, 48, STEEL, true);
        out.finish(Finishes.PAINT_GLOSS).cylinder(v(-0.5, 0, 0), v(-l, 0, 0), 0.98 * r, 0.98 * r, 48, YELLOW);
        for (int i = 0; i < 6; i++) {
            double a = 2 * Math.PI * i / 6, b = a + Math.PI / 6;
            Vec3d e = v(0, Math.cos(a), Math.sin(a)), t = v(0, -Math.sin(a), Math.cos(a)), eb = v(0, Math.cos(b), Math.sin(b)), tb = v(0, -Math.sin(b), Math.cos(b));
            double x0 = faceX(0.3, r), x1 = faceX(r - 0.05, r);
            out.finish(Finishes.PAINT_GLOSS).hexahedron(new Vec3d[]{v(x0 - 0.02, 0, 0).add(e.scale(0.3)).subtract(t.scale(0.18)), v(x1 - 0.02, 0, 0).add(e.scale(r - 0.05)).subtract(t.scale(0.22)),
                    v(x1 - 0.02, 0, 0).add(e.scale(r - 0.05)).add(t.scale(0.22)), v(x0 - 0.02, 0, 0).add(e.scale(0.3)).add(t.scale(0.18)),
                    v(x0 + 0.1, 0, 0).add(e.scale(0.3)).subtract(t.scale(0.14)), v(x1 + 0.08, 0, 0).add(e.scale(r - 0.05)).subtract(t.scale(0.18)),
                    v(x1 + 0.08, 0, 0).add(e.scale(r - 0.05)).add(t.scale(0.18)), v(x0 + 0.1, 0, 0).add(e.scale(0.3)).add(t.scale(0.14))}, YELLOW);
            double y0 = faceX(0.55 * r, r), y1 = faceX(0.92 * r, r);
            Vec3d c0 = eb.scale(0.55 * r), c1 = eb.scale(0.92 * r);
            out.finish(Finishes.PAINT_MATTE).hexahedron(new Vec3d[]{v(y0 + 0.004, 0, 0).add(c0).subtract(tb.scale(0.12)), v(y1 + 0.004, 0, 0).add(c1).subtract(tb.scale(0.2)),
                    v(y1 + 0.004, 0, 0).add(c1).add(tb.scale(0.2)), v(y0 + 0.004, 0, 0).add(c0).add(tb.scale(0.12)),
                    v(y0 - 0.02, 0, 0).add(c0).subtract(tb.scale(0.12)), v(y1 - 0.02, 0, 0).add(c1).subtract(tb.scale(0.2)),
                    v(y1 - 0.02, 0, 0).add(c1).add(tb.scale(0.2)), v(y0 - 0.02, 0, 0).add(c0).add(tb.scale(0.12))}, BLACK);
        }
        int n = Math.max(d.discs(), 8);
        double golden = Math.PI * (3 - Math.sqrt(5));
        out.detail(1);
        for (int i = 0; i < n; i++) {
            double rad = 0.25 * r + 0.65 * r * i / (n - 1), a = golden * i * 3.7, fx = faceX(rad, r);
            Vec3d e = v(0, Math.cos(a), Math.sin(a)), t = v(0, -Math.sin(a), Math.cos(a)), c = v(fx, 0, 0).add(e.scale(rad));
            out.finish(Finishes.CAST_IRON).box(c.add(v(-0.02, 0, 0)).subtract(e.scale(0.6 * rd)).subtract(t.scale(0.09)), c.add(v(0.07, 0, 0)).add(e.scale(0.6 * rd)).add(t.scale(0.09)), DARK);
            out.finish(Finishes.MACHINED_STEEL).cylinder(c.add(v(0.08, 0, 0)).subtract(e.scale(0.04)), c.add(v(0.08, 0, 0)).add(e.scale(0.04)), rd, rd, 14, SILVER);
        }
        out.detail(0).finish(Finishes.FORGED_STEEL);
        for (int i = 0; i < 12; i++) {
            double a = 2 * Math.PI * (i + 0.5) / 12, fx = faceX(0.96 * r, r);
            Vec3d e = v(0, Math.cos(a), Math.sin(a)), t = v(0, -Math.sin(a), Math.cos(a));
            out.box(v(fx - 0.04, 0, 0).add(e.scale(0.93 * r)).subtract(t.scale(0.15)), v(fx + 0.06, 0, 0).add(e.scale(0.99 * r)).add(t.scale(0.15)), WEAR);
        }
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(0.2, 0, 0), v(0.32, 0, 0), 0.25, 0.2, 12, SILVER);
    }

    /**
     * A trencher's chain boom from its top pivot (the origin) back along -x: the boom frame and its side plates, the drive
     * sprocket at the pivot and the idler at the far end, the chain's two strands round them with a cutter bar and tooth
     * on every link.
     */
    static void trencher(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), l = d.toolLengthM(), rs = Math.max(d.cutterRadiusM(), 0.15), ri = 0.8 * rs, xi = -l + ri;
        out.finish(Finishes.PAINT_WORN).box(v(xi, -0.12, -0.3 * w), v(-0.05, 0.12, 0.3 * w), YELLOW);
        double[][] side = {{0.05, 0.16}, {xi, 0.12}, {xi, -0.12}, {0.05, -0.16}};
        plate(out, side, -0.3 * w, 0.02, YELLOW);
        plate(out, side, 0.3 * w - 0.02, 0.02, YELLOW);
        out.finish(Finishes.CAST_IRON);
        out.cylinder(v(0, 0, -0.42 * w), v(0, 0, 0.42 * w), rs, rs, 18, DARK);
        out.cylinder(v(xi, 0, -0.42 * w), v(xi, 0, 0.42 * w), ri, ri, 18, DARK);
        List<Vec3d> loop = new ArrayList<>(arcZ(1.05 * rs, 0, 0, -90, 90, 15));
        loop.addAll(arcZ(1.05 * ri, xi, 0, 90, 270, 15));
        out.finish(Finishes.FORGED_STEEL);
        for (double s : new double[]{-1, 1}) { out.tube(shifted(loop, s * 0.36 * w), 0.03, null, DARK, null, 6, true); }
        double total = 0;
        for (int i = 0; i < loop.size(); i++) { total += loop.get((i + 1) % loop.size()).subtract(loop.get(i)).length(); }
        int m = Math.max(d.teeth(), 10);
        out.detail(1);
        for (int k = 0; k < m; k++) {
            double s = total * k / m;
            for (int i = 0; i < loop.size(); i++) {
                Vec3d a = loop.get(i), b = loop.get((i + 1) % loop.size());
                double len = b.subtract(a).length();
                if (s > len) { s -= len; continue; }
                Vec3d p = a.add(b.subtract(a).scale(s / Math.max(len, 1e-9))), nrm = MeshBuilder.unit(v(b.y() - a.y(), -(b.x() - a.x()), 0));
                out.box(p.subtract(v(0.03, 0.03, 0.46 * w)), p.add(v(0.03, 0.03, 0.46 * w)), DARK);
                out.cylinder(p.add(nrm.scale(0.02)), p.add(nrm.scale(0.1)), 0.03, 0.008, 6, WEAR);
                break;
            }
        }
        out.detail(0);
    }

    /** A mixer drum's radius at x (front cone, belly, rear cone; l long, r round). */
    private static double mixerRadius(double x, double l, double r) {
        if (x > -0.12 * l) { return Math.max(0.42 * r, Math.min(r, 0.62 * r + 0.38 * r * (0.42 * l - x) / (0.3 * l))); }
        return r - 0.58 * r * (-0.12 * l - x) / (0.3 * l);
    }

    /**
     * A mixer drum about x at its middle (its opening toward -x): the front cone to its drive flange, the belly, the rear
     * cone to the opening and its lip, the roller track round the rear cone, the girth seams and three painted spirals.
     */
    static void mixer(WorkToolDesign d, PartMesh out) {
        double r = Math.max(d.cutterRadiusM(), 0.5), l = d.toolLengthM();
        double[] xs = {0.5 * l, 0.42 * l, 0.12 * l, -0.12 * l, -0.42 * l, -0.5 * l}, rs = {0.45 * r, 0.62 * r, r, r, 0.42 * r, 0.33 * r};
        double[] along = new double[xs.length + 2], radius = new double[xs.length + 2];
        along[xs.length + 1] = 1;
        for (int i = 0; i < xs.length; i++) { along[i + 1] = (0.5 * l - xs[i]) / l; radius[i + 1] = rs[i]; }
        out.finish(Finishes.PAINT_GLOSS).lathe(v(0.5 * l, 0, 0), v(-0.5 * l, 0, 0), along, radius, null, 32, SILVER, true);
        for (double x : new double[]{0.12 * l, -0.12 * l}) { out.cylinder(v(x - 0.01, 0, 0), v(x + 0.01, 0, 0), r + 0.008, r + 0.008, 32, WHITE); }
        out.finish(Finishes.CAST_IRON);
        out.cylinder(v(0.5 * l, 0, 0), v(0.5 * l + 0.12, 0, 0), 0.32 * r, 0.32 * r, 20, DARK);
        out.cylinder(v(-0.5 * l, 0, 0), v(-0.5 * l - 0.06, 0, 0), 0.36 * r, 0.36 * r, 24, DARK);
        double xt = -0.3 * l, rt = mixerRadius(xt, l, r) + 0.04;
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(xt - 0.07, 0, 0), v(xt + 0.07, 0, 0), rt, rt, 32, STEEL);
        out.finish(Finishes.PAINT_GLOSS);
        for (int k = 0; k < 3; k++) {
            List<Vec3d> path = new ArrayList<>();
            for (int i = 0; i <= 24; i++) {
                double x = 0.42 * l - 0.84 * l * i / 24, rr = mixerRadius(x, l, r) + 0.01, th = 2 * Math.PI * k / 3 + 2 * Math.PI * 1.2 * i / 24;
                path.add(v(x, rr * Math.cos(th), rr * Math.sin(th)));
            }
            out.tube(path, 0.03, null, RED, null, 6, false);
        }
    }

    /**
     * A scraper's bowl from its rear hinge forward (+x): the floor, the sides (high at the back, sloping to the edge) with
     * their top rails and the draft arms' trunnion bosses, the cutting edge and its teeth below the front, the apron's
     * curved gate over the mouth, the ejector at the back and its push frame.
     */
    static void scraperBowl(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), t = Math.max(d.plateM(), 0.015);
        double[][] side = {{0, -0.02}, {l + 0.1, -0.15}, {l + 0.1, 0.35 * h}, {0.65 * l, h}, {0, h}};
        out.finish(Finishes.PAINT_WORN);
        out.box(v(0.1, -0.02, -w / 2), v(l, t, w / 2), YELLOW);
        for (double s : new double[]{-1, 1}) {
            plate(out, side, s > 0 ? w / 2 - 0.03 : -w / 2, 0.03, YELLOW);
            out.box(v(0, h - 0.06, s * w / 2 - 0.05), v(0.66 * l, h + 0.02, s * w / 2 + 0.05), YELLOW);
            out.cylinder(v(0.55 * l, 0.5 * h, s * w / 2), v(0.55 * l, 0.5 * h, s * (w / 2 + 0.12)), 0.16, 0.16, 16, YELLOW);
        }
        List<Vec3d> apron = new ArrayList<>();
        for (int a = 0; a <= 100; a += 20) { double q = Math.toRadians(a); apron.add(v(l + 0.12 - 0.5 * h * (1 - Math.cos(q)), 0.28 * h + 0.75 * h * Math.sin(q), 0)); }
        band(out, apron, w / 2 - 0.04, YELLOW);
        out.box(v(0.1, t, -w / 2 + 0.04), v(0.16, 0.92 * h, w / 2 - 0.04), YELLOW);
        out.box(v(-0.2, 0.1, -0.3 * w), v(0.1, 0.8 * h, 0.3 * w), YELLOW);
        out.finish(Finishes.FORGED_STEEL).hexahedron(new Vec3d[]{v(l, -0.02, -w / 2), v(l + 0.12, -0.17, -w / 2), v(l + 0.12, -0.14, -w / 2), v(l, 0.02, -w / 2),
                v(l, -0.02, w / 2), v(l + 0.12, -0.17, w / 2), v(l + 0.12, -0.14, w / 2), v(l, 0.02, w / 2)}, WEAR);
        for (int k = 0; k < 5; k++) { double z = -0.4 * w + 0.2 * w * k; out.cylinder(v(l + 0.1, -0.16, z), v(l + 0.26, -0.2, z), 0.05, 0.01, 6, WEAR); }
    }

    /**
     * A paver's hopper ahead of its discharge (the origin): the floor and its two slat conveyors, the folding wings
     * sloping up and out to the full width with their end plates, the front apron, the bumper and its push rollers.
     */
    static void paverHopper(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), wf = 0.45 * w;
        out.finish(Finishes.CAST_IRON).box(v(0, -0.05, -wf / 2), v(l, 0.02, wf / 2), DARK);
        for (double z : new double[]{-0.25 * wf, 0.25 * wf}) {
            out.finish(Finishes.RUBBER).box(v(0, 0.02, z - 0.2 * wf), v(l, 0.035, z + 0.2 * wf), BLACK);
            out.finish(Finishes.FORGED_STEEL).detail(1);
            for (int k = 0; k < (int) (l / 0.15); k++) { double x = 0.05 + 0.15 * k; out.box(v(x, 0.035, z - 0.2 * wf), v(x + 0.04, 0.06, z + 0.2 * wf), WEAR); }
            out.detail(0);
        }
        out.finish(Finishes.PAINT_WORN);
        for (double s : new double[]{-1, 1}) {
            double a0 = s * wf / 2, a1 = s * w / 2;
            out.hexahedron(new Vec3d[]{v(0, 0.02, a0), v(l, 0.02, a0), v(l, h, a1), v(0, h, a1),
                    v(0, 0.02, a0 + s * 0.02), v(l, 0.02, a0 + s * 0.02), v(l, h, a1 + s * 0.02), v(0, h, a1 + s * 0.02)}, YELLOW);
            out.hexahedron(new Vec3d[]{v(l, 0.02, a0), v(l + 0.03, 0.02, a0), v(l + 0.03, h, a1), v(l, h, a1),
                    v(l, 0.02, a0 + s * 0.01), v(l + 0.03, 0.02, a0 + s * 0.01), v(l + 0.03, h, a1 + s * 0.005), v(l, h, a1 + s * 0.005)}, YELLOW);
        }
        out.box(v(l, -0.05, -wf / 2), v(l + 0.04, 0.35 * h, wf / 2), YELLOW);
        out.finish(Finishes.CAST_IRON).box(v(l + 0.04, 0.05, -0.45 * w), v(l + 0.18, 0.25, 0.45 * w), DARK);
        for (double s : new double[]{-1, 1}) {
            double zc = s * 0.32 * w;
            out.box(v(l + 0.15, 0.1, zc - 0.25), v(l + 0.3, 0.2, zc - 0.22), DARK);
            out.box(v(l + 0.15, 0.1, zc + 0.22), v(l + 0.3, 0.2, zc + 0.25), DARK);
            out.finish(Finishes.RUBBER).cylinder(v(l + 0.3, 0.15, zc - 0.22), v(l + 0.3, 0.15, zc + 0.22), 0.13, 0.13, 16, BLACK);
            out.finish(Finishes.CAST_IRON);
        }
    }

    /**
     * A paver's extending screed (its paving line at x = -l): the main screed's frame and heated plate with its tamper
     * bar, the extensions behind it out to the full paving width, the end gates, and the walkway at the back with its
     * handrail and the control boxes at its ends.
     */
    static void screed(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), zw = w / 2 + d.extensionM();
        out.finish(Finishes.PAINT_WORN).box(v(-l, 0.05, -w / 2), v(0, h, w / 2), YELLOW);
        for (double s : new double[]{-1, 1}) {
            double za = Math.min(s * 0.3 * w, s * zw), zb = Math.max(s * 0.3 * w, s * zw);
            out.box(v(-l - 0.45, 0.05, za), v(-l - 0.05, 0.75 * h, zb), YELLOW);
            out.box(v(-l - 0.5, -0.05, s > 0 ? zw - 0.015 : -zw), v(0.3, 0.55 * h, s > 0 ? zw : -zw + 0.015), YELLOW);
            out.finish(Finishes.MACHINED_STEEL).box(v(-l - 0.45, 0, za), v(-l - 0.03, 0.05, zb), STEEL);
            out.finish(Finishes.PLASTIC).box(v(-l - 1.0, 0.55 * h, Math.min(s * (zw - 0.45), s * (zw - 0.1))), v(-l - 0.75, 0.55 * h + 0.35, Math.max(s * (zw - 0.45), s * (zw - 0.1))), DARK);
            out.finish(Finishes.PAINT_WORN);
        }
        out.finish(Finishes.MACHINED_STEEL).box(v(-l, 0, -w / 2), v(0.02, 0.05, w / 2), STEEL);
        out.finish(Finishes.CAST_IRON).box(v(-0.05, h - 0.12, -w / 2), v(0.08, h, w / 2), DARK);
        out.finish(Finishes.NONSKID).box(v(-l - 1.0, 0.3, -zw), v(-l - 0.5, 0.33, zw), WEAR);
        int posts = Math.max(2, (int) (2 * zw / 0.8));
        out.finish(Finishes.PAINT_GLOSS);
        for (int k = 0; k <= posts; k++) { double z = -zw + 0.05 + (2 * zw - 0.1) * k / posts; out.cylinder(v(-l - 0.98, 0.33, z), v(-l - 0.98, 1.3, z), 0.02, 0.02, 6, YELLOW); }
        out.cylinder(v(-l - 0.98, 1.3, -zw + 0.05), v(-l - 0.98, 1.3, zw - 0.05), 0.022, 0.022, 8, YELLOW);
    }

    /**
     * A conveyor from its tail pulley (the origin) to its head at +x: the two stringers, troughing idlers about every
     * metre (three rolls, the wings at 30 degrees) and the return rolls, the belt troughed on them and flat underneath,
     * the head pulley with its drive, the tail pulley and the skirts at the loading end.
     */
    static void conveyor(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM(), l = d.toolLengthM(), zs = w / 2 + 0.12, rc = 0.06, c = w / 3, wing = Math.toRadians(30);
        double top = -0.04 + rc, lift = Math.sin(wing) * c;
        int n = Math.max(2, (int) (l / 1.0));
        out.finish(Finishes.PAINT_WORN);
        for (double s : new double[]{-1, 1}) {
            out.box(v(0, -h, s * zs - 0.03), v(l, -0.1, s * zs + 0.03), YELLOW);
            out.box(v(0.2, top, s * (w / 2 - 0.05) - 0.01), v(2.0, top + 0.35, s * (w / 2 - 0.05) + 0.01), YELLOW);
        }
        for (int i = 0; i <= n; i++) { double x = 0.3 + (l - 0.6) * i / n; out.box(v(x - 0.03, -0.12, -zs), v(x + 0.03, -0.08, zs), YELLOW); }
        out.finish(Finishes.CAST_IRON).detail(1);
        for (int i = 0; i <= n; i++) {
            double x = 0.3 + (l - 0.6) * i / n;
            out.cylinder(v(x, -0.04, -c / 2), v(x, -0.04, c / 2), rc, rc, 10, DARK);
            for (double s : new double[]{-1, 1}) {
                Vec3d a = v(x, -0.04, s * c / 2), b = a.add(v(0, lift, s * Math.cos(wing) * c));
                out.cylinder(a.add(b.subtract(a).scale(0.05)), b, rc, rc, 10, DARK);
            }
            out.cylinder(v(x, -h + 0.08, -w / 2), v(x, -h + 0.08, w / 2), rc, rc, 10, DARK);
        }
        out.detail(0);
        out.cylinder(v(l, -0.12, -w / 2 - 0.05), v(l, -0.12, w / 2 + 0.05), 0.2, 0.2, 18, DARK);
        out.cylinder(v(l, -0.12, w / 2 + 0.05), v(l, -0.12, w / 2 + 0.4), 0.13, 0.13, 14, DARK);
        out.cylinder(v(0, -0.15, -w / 2 - 0.05), v(0, -0.15, w / 2 + 0.05), 0.17, 0.17, 18, DARK);
        double[][] prof = {{-w / 2, top + lift}, {-c / 2, top}, {c / 2, top}, {w / 2, top + lift}};
        out.finish(Finishes.RUBBER);
        for (int j = 0; j < 3; j++) {
            out.sheet(List.of(v(0, prof[j][1], prof[j][0]), v(l, prof[j][1], prof[j][0])), List.of(v(0, prof[j + 1][1], prof[j + 1][0]), v(l, prof[j + 1][1], prof[j + 1][0])), RUBBER, true);
        }
        out.box(v(0, -h + 0.08 + rc, -w / 2), v(l, -h + 0.08 + rc + 0.012, w / 2), RUBBER);
    }

    /** The profile's samples along a bucket's strike chord. */
    private static final int PROFILE_STEPS = 12;

    /**
     * A bucket on its strike plane: the wrapper along the rated parabola (its lip three plates thick), the side plates,
     * teeth with adapters, and lugs from the wrapper up to the hinge pin and the ram's rod pin.
     */
    static void bucket(WorkToolDesign d, PartMesh out) {
        WorkToolDesign.Chord chord = d.chord();
        Vec3d lip = chord.lip(), up = chord.up(), outward = chord.out();
        double b = d.toolLengthM(), h = d.toolHeightM(), w = d.toolWidthM(), t = Math.max(1.5 * d.plateM(), 0.02), hw = w / 2, ts = Math.max(2 * d.plateM(), 0.025);
        Vec3d[] pts = new Vec3d[PROFILE_STEPS + 1], nrm = new Vec3d[PROFILE_STEPS + 1];
        for (int i = 0; i <= PROFILE_STEPS; i++) {
            double s = (double) i / PROFILE_STEPS;
            pts[i] = lip.add(up.scale(s * b)).subtract(outward.scale(4 * h * s * (1 - s)));
            Vec3d tan = up.scale(b).subtract(outward.scale(4 * h * (1 - 2 * s)));
            Vec3d n = MeshBuilder.unit(tan.cross(Z));
            double into = n.dot(outward.scale(-1));
            if (into < -1e-9 || (Math.abs(into) <= 1e-9 && n.dot(up) * (s - 0.5) < 0)) { n = n.scale(-1); }
            nrm[i] = n;
        }
        for (int i = 0; i < PROFILE_STEPS; i++) {
            double th = t * (i == 0 ? WorkToolDesign.LIP_THICKNESS : 1);
            Vec3d a = pts[i], c = pts[i + 1], oa = a.add(nrm[i].scale(th)), oc = c.add(nrm[i + 1].scale(i == 0 ? th : t));
            Vec3d zi = Z.scale(hw - ts);
            out.finish(i == 0 ? Finishes.FORGED_STEEL : Finishes.PAINT_WORN).hexahedron(new Vec3d[]{a.subtract(zi), c.subtract(zi), oc.subtract(zi), oa.subtract(zi),
                    a.add(zi), c.add(zi), oc.add(zi), oa.add(zi)}, i == 0 ? WEAR : YELLOW);
        }
        double[][] side = new double[PROFILE_STEPS + 1][];
        for (int i = 0; i <= PROFILE_STEPS; i++) {
            Vec3d p = pts[i].add(nrm[i].scale(t)).subtract(lip);
            side[i] = new double[]{p.dot(up), p.dot(outward)};
        }
        for (double z0 : new double[]{-hw, hw - ts}) { out.finish(Finishes.PAINT_WORN).extrude(side, lip.add(v(0, 0, z0)), up, outward, v(0, 0, ts), YELLOW, false); }
        WorkToolDesign.Kind k = d.toolKind();
        Vec3d ahead = k == WorkToolDesign.Kind.BACKHOE_BUCKET || k == WorkToolDesign.Kind.TRENCH_BUCKET ? up.scale(-1)
                : k == WorkToolDesign.Kind.DRAGLINE_BUCKET ? X.scale(-1) : X;
        if (d.teeth() > 0) {
            double tw = WorkToolDesign.TOOTH_SHARE * w / (d.teeth() + 1);
            Vec3d thick = MeshBuilder.unit(ahead.cross(Z));
            if (thick.dot(nrm[0]) < 0) { thick = thick.scale(-1); }
            out.finish(Finishes.FORGED_STEEL).detail(1);
            for (int i = 0; i < d.teeth(); i++) {
                double z = d.teeth() == 1 ? 0 : -0.45 * w + 0.9 * w * i / (d.teeth() - 1);
                Vec3d base = lip.add(v(0, 0, z)).add(nrm[0].scale(1.5 * t)).subtract(ahead.scale(tw));
                Vec3d tip = lip.add(v(0, 0, z)).add(nrm[0].scale(1.2 * t)).add(ahead.scale(WorkToolDesign.TOOTH_PROJECTION * tw));
                out.hexahedron(new Vec3d[]{
                        base.subtract(Z.scale(0.5 * tw)).subtract(thick.scale(0.35 * tw)), base.add(Z.scale(0.5 * tw)).subtract(thick.scale(0.35 * tw)),
                        base.add(Z.scale(0.5 * tw)).add(thick.scale(0.35 * tw)), base.subtract(Z.scale(0.5 * tw)).add(thick.scale(0.35 * tw)),
                        tip.subtract(Z.scale(0.4 * tw)).subtract(thick.scale(0.05 * tw)), tip.add(Z.scale(0.4 * tw)).subtract(thick.scale(0.05 * tw)),
                        tip.add(Z.scale(0.4 * tw)).add(thick.scale(0.05 * tw)), tip.subtract(Z.scale(0.4 * tw)).add(thick.scale(0.05 * tw))}, WEAR);
            }
            out.detail(0);
        }
        if (k == WorkToolDesign.Kind.DRAGLINE_BUCKET) {
            // the arch over the mouth and the hitch's chains' bar
            Vec3d front = lip.add(up.scale(0.1 * b)).add(outward.scale(0.35 * h));
            out.finish(Finishes.FORGED_STEEL).cylinder(front.subtract(Z.scale(hw)), front.add(Z.scale(hw)), 0.05, 0.05, 10, WEAR);
            return;
        }
        // the lugs: from each pin to the two nearest points of the wrapper's outside
        double rb = Math.max(0.05, 0.07 * Math.sqrt(w)), ze = Math.max((k == WorkToolDesign.Kind.LOADER_BUCKET ? 0.3 : 0.18) * w, 0.12);
        Vec3d rod = d.rammed() && d.ramGeometry() != null ? d.ramGeometry().rodPin() : null;
        List<Vec3d> pins = rod == null ? List.of(d.jointOrigin()) : List.of(d.jointOrigin(), rod);
        Vec3d[] outer = new Vec3d[PROFILE_STEPS + 1];
        for (int i = 0; i <= PROFILE_STEPS; i++) { outer[i] = pts[i].add(nrm[i].scale(t)); }
        List<double[][]> lugs = new ArrayList<>();
        for (Vec3d pin : pins) {
            Integer[] order = new Integer[PROFILE_STEPS + 1];
            for (int i = 0; i <= PROFILE_STEPS; i++) { order[i] = i; }
            Arrays.sort(order, Comparator.comparingDouble(i -> outer[i].subtract(pin).length()));
            List<double[]> cloud = new ArrayList<>();
            for (int j = 0; j < 2; j++) {
                int i = order[j];
                cloud.add(new double[]{pts[i].x(), pts[i].y()});
                cloud.add(new double[]{outer[i].x(), outer[i].y()});
            }
            for (int a = 0; a < 8; a++) { cloud.add(new double[]{pin.x() + 1.3 * rb * Math.cos(a * Math.PI / 4), pin.y() + 1.3 * rb * Math.sin(a * Math.PI / 4)}); }
            lugs.add(hull(cloud));
        }
        out.finish(Finishes.PAINT_WORN);
        for (double z0 : new double[]{-ze - 0.03, ze - 0.03}) {
            for (double[][] lug : lugs) { plate(out, lug, z0, 0.06, YELLOW); }
            for (Vec3d pin : pins) { out.cylinder(pin.add(v(0, 0, z0 - 0.02)), pin.add(v(0, 0, z0 + 0.08)), rb, rb, 14, YELLOW); }
        }
        out.finish(Finishes.FORGED_STEEL);
        for (Vec3d pin : pins) { out.cylinder(pin.subtract(Z.scale(ze + 0.07)), pin.add(Z.scale(ze + 0.07)), 0.45 * rb, 0.45 * rb, 10, STEEL); }
    }

    /** A clamshell's or grapple's closed shells: two quarter ellipses meeting below the head, its head above the hinge. */
    static void clamshell(WorkToolDesign d, PartMesh out) {
        double b = d.toolLengthM(), h = d.toolHeightM(), w = d.toolWidthM(), t = Math.max(1.5 * d.plateM(), 0.02);
        int steps = 8;
        out.finish(Finishes.PAINT_WORN);
        for (double s : new double[]{-1, 1}) {
            for (int i = 0; i < steps; i++) {
                double a0 = 0.5 * Math.PI * i / steps, a1 = 0.5 * Math.PI * (i + 1) / steps;
                Vec3d p0 = v(s * 0.5 * b * Math.cos(a0), -h * Math.sin(a0), 0), p1 = v(s * 0.5 * b * Math.cos(a1), -h * Math.sin(a1), 0);
                Vec3d n0 = MeshBuilder.unit(v(s * Math.cos(a0) / (0.5 * b), -Math.sin(a0) / h, 0)), n1 = MeshBuilder.unit(v(s * Math.cos(a1) / (0.5 * b), -Math.sin(a1) / h, 0));
                Vec3d zz = Z.scale(w / 2);
                out.hexahedron(new Vec3d[]{p0.subtract(zz), p1.subtract(zz), p1.add(n1.scale(t)).subtract(zz), p0.add(n0.scale(t)).subtract(zz),
                        p0.add(zz), p1.add(zz), p1.add(n1.scale(t)).add(zz), p0.add(n0.scale(t)).add(zz)}, YELLOW);
            }
            double[][] quarter = new double[steps + 2][];
            for (int i = 0; i <= steps; i++) { double a = 0.5 * Math.PI * i / steps; quarter[i] = new double[]{s * 0.5 * b * Math.cos(a), -h * Math.sin(a)}; }
            quarter[steps + 1] = new double[]{0, 0};
            for (double z0 : new double[]{-w / 2 - t, w / 2}) { plate(out, quarter, z0, t, YELLOW); }
        }
        out.box(v(-0.3 * b, 0, -0.3 * w), v(0.3 * b, 0.35 * h, 0.3 * w), YELLOW);
        out.finish(Finishes.FORGED_STEEL).cylinder(v(0, 0, -0.35 * w), v(0, 0, 0.35 * w), 0.06, 0.06, 10, STEEL);
    }

    /**
     * A blade: the moldboard curved up and back from its cutting edge (the edge a fifth of it, in wear steel), straight
     * across or with its wings angled forward (SU, U), end plates and end bits, ribs behind, a dozer's push-arm trunnions.
     */
    static void blade(WorkToolDesign d, PartMesh out) {
        double w = d.toolWidthM(), h = d.toolHeightM();
        WorkToolDesign.Kind k = d.toolKind();
        Vec3d p1, p2;
        double wing;
        if (k == WorkToolDesign.Kind.GRADER_MOLDBOARD) { p1 = v(-0.5 * h, 0.3 * h, 0); p2 = v(-0.25 * h, h, 0); wing = 0; }
        else if (k == WorkToolDesign.Kind.SNOW_PLOW) { p1 = v(-0.6 * h, 0.4 * h, 0); p2 = v(-0.1 * h, h, 0); wing = 0; }
        else {
            p1 = v(-0.48 * h, 0.38 * h, 0); p2 = v(-0.2 * h, h, 0);
            wing = switch (d.bladeStyle()) { case SU -> WorkToolDesign.WING_SU; case U -> WorkToolDesign.WING_U; default -> 0; };
        }
        int n = 10, edge = 2;
        double tb = Math.max(2 * d.plateM(), 0.04);
        Vec3d[] curve = new Vec3d[n + 1], back = new Vec3d[n + 1];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n;
            curve[i] = p1.scale(2 * u * (1 - u)).add(p2.scale(u * u));
        }
        for (int i = 0; i <= n; i++) {
            Vec3d t = MeshBuilder.unit(curve[Math.min(i + 1, n)].subtract(curve[Math.max(i - 1, 0)]));
            Vec3d nb = v(-t.y(), t.x(), 0);
            back[i] = nb.x() > 0 ? nb.scale(-1) : nb;
        }
        double ws = wing * w, xw = ws * Math.tan(Math.toRadians(25));
        double[][] stations = ws > 0 ? new double[][]{{-w / 2, xw}, {-w / 2 + ws, 0}, {w / 2 - ws, 0}, {w / 2, xw}} : new double[][]{{-w / 2, 0}, {w / 2, 0}};
        for (int s = 0; s + 1 < stations.length; s++) {
            Vec3d o0 = v(stations[s][1], 0, stations[s][0]), o1 = v(stations[s + 1][1], 0, stations[s + 1][0]);
            for (int i = 0; i < n; i++) {
                double ta = tb * (i < edge ? 1.6 : 1);
                Vec3d a = curve[i], c = curve[i + 1], ba = a.add(back[i].scale(ta)), bc = c.add(back[i + 1].scale(tb));
                out.finish(i < edge ? Finishes.FORGED_STEEL : Finishes.PAINT_WORN).hexahedron(new Vec3d[]{a.add(o0), c.add(o0), bc.add(o0), ba.add(o0),
                        a.add(o1), c.add(o1), bc.add(o1), ba.add(o1)}, i < edge ? WEAR : YELLOW);
            }
        }
        // end plates and end bits
        List<double[]> profile = new ArrayList<>();
        for (Vec3d p : curve) { profile.add(new double[]{p.x(), p.y()}); }
        profile.add(new double[]{curve[n].x() - 0.25 * h, curve[n].y()});
        profile.add(new double[]{curve[0].x() - 0.3 * h, 0.1 * h});
        double[][] endPlate = hull(profile);
        for (int e = 0; e < 2; e++) {
            double[] st = e == 0 ? stations[0] : stations[stations.length - 1];
            double sideSign = e == 0 ? -1 : 1, zOut = st[0];
            double[][] moved = new double[endPlate.length][];
            for (int i = 0; i < endPlate.length; i++) { moved[i] = new double[]{endPlate[i][0] + st[1], endPlate[i][1]}; }
            plate(out.finish(Finishes.PAINT_WORN), moved, sideSign > 0 ? zOut - 0.03 : zOut, 0.03, YELLOW);
            out.finish(Finishes.FORGED_STEEL).box(v(st[1] - 0.02, -0.01, sideSign > 0 ? zOut - 0.12 : zOut), v(st[1] + 0.06, 0.18 * h, sideSign > 0 ? zOut : zOut + 0.12), WEAR);
        }
        // ribs behind
        int ribs = w > 2.5 ? 5 : 3;
        out.finish(Finishes.PAINT_WORN);
        for (int j = 0; j < ribs; j++) {
            double z = -0.4 * w + 0.8 * w * j / (ribs - 1);
            for (int i = edge; i < n; i++) {
                Vec3d a = curve[i].add(back[i].scale(tb)).add(Z.scale(z)), c = curve[i + 1].add(back[i + 1].scale(tb)).add(Z.scale(z));
                Vec3d dz = Z.scale(0.05);
                out.hexahedron(new Vec3d[]{a.subtract(dz), c.subtract(dz), c.add(back[i + 1].scale(0.14)).subtract(dz), a.add(back[i].scale(0.14)).subtract(dz),
                        a.add(dz), c.add(dz), c.add(back[i + 1].scale(0.14)).add(dz), a.add(back[i].scale(0.14)).add(dz)}, YELLOW);
            }
        }
        if (k == WorkToolDesign.Kind.DOZER_BLADE) {
            for (double z : new double[]{-0.38 * w, 0.38 * w}) {
                Vec3d c = curve[2].add(back[2].scale(tb + 0.18)).add(Z.scale(z));
                out.finish(Finishes.PAINT_WORN).cylinder(c.subtract(Z.scale(0.12)), c.add(Z.scale(0.12)), 0.09, 0.09, 12, YELLOW);
                out.finish(Finishes.FORGED_STEEL).cylinder(c.subtract(Z.scale(0.16)), c.add(Z.scale(0.16)), 0.04, 0.04, 8, STEEL);
            }
        }
    }

    /** A dump body from its rear hinge: the floor rising to its tail chute, the sloped front, the canopy and its gussets, sides with a top rail and ribs, the sub-frame. */
    static void dumpBody(WorkToolDesign d, PartMesh out) {
        double l = d.toolLengthM(), w = d.toolWidthM(), h = d.toolHeightM(), t = Math.max(2 * d.plateM(), 0.04), hw = w / 2;
        double lc = WorkToolDesign.BODY_CANOPY_LENGTH * l, front = 1.03 * l;
        Vec3d[] floor = {v(0, 0.18 * h, 0), v(0.15 * l, 0, 0), v(l, 0, 0), v(front, 1.1 * h, 0)};
        out.finish(Finishes.PAINT_WORN);
        for (int i = 0; i + 1 < floor.length; i++) {
            Vec3d a = floor[i], b = floor[i + 1], tan = MeshBuilder.unit(b.subtract(a)), nn = v(tan.y(), -tan.x(), 0).scale(t);
            Vec3d zz = Z.scale(hw);
            out.hexahedron(new Vec3d[]{a.subtract(zz), b.subtract(zz), b.add(nn).subtract(zz), a.add(nn).subtract(zz), a.add(zz), b.add(zz), b.add(nn).add(zz), a.add(nn).add(zz)}, YELLOW);
        }
        Vec3d c0 = v(front, 1.1 * h, 0), c1 = v(front + lc, 1.2 * h, 0), up = v(0, t, 0), zz = Z.scale(hw);
        out.hexahedron(new Vec3d[]{c0.subtract(zz), c1.subtract(zz), c1.add(up).subtract(zz), c0.add(up).subtract(zz), c0.add(zz), c1.add(zz), c1.add(up).add(zz), c0.add(up).add(zz)}, YELLOW);
        for (double z : new double[]{-hw, -0.33 * w, 0.33 * w - 0.06, hw - 0.06}) {
            plate(out, new double[][]{{front, 0.55 * h}, {front + 0.9 * lc, 1.19 * h}, {front, 1.12 * h}}, z, 0.06, YELLOW);
        }
        double[][] side = {{0, 0.18 * h}, {0.15 * l, 0}, {l, 0}, {front, 1.1 * h}, {0, h}};
        int ribs = Math.max(2, (int) Math.round(l / 1.5));
        for (double s : new double[]{-1, 1}) {
            plate(out, side, s > 0 ? hw - t : -hw, t, YELLOW);
            double zr = s * (hw + 0.09);
            Vec3d a = v(-0.02, h, zr), b = v(front, 1.1 * h, zr);
            out.hexahedron(new Vec3d[]{a.add(v(0, -0.09, -0.09)), b.add(v(0, -0.09, -0.09)), b.add(v(0, 0.06, -0.09)), a.add(v(0, 0.06, -0.09)),
                    a.add(v(0, -0.09, 0.09)), b.add(v(0, -0.09, 0.09)), b.add(v(0, 0.06, 0.09)), a.add(v(0, 0.06, 0.09))}, YELLOW);
            for (int k = 1; k < ribs; k++) {
                double x = l * k / ribs, ytop = h + 0.1 * h * x / front, ybot = x < 0.15 * l ? 0.18 * h * (1 - x / (0.15 * l)) : 0;
                out.box(v(x - 0.06, ybot - 0.05, s > 0 ? hw : -hw - 0.12), v(x + 0.06, ytop - 0.05, s > 0 ? hw + 0.12 : -hw), YELLOW);
            }
        }
        out.finish(Finishes.CAST_IRON);
        for (double z : new double[]{-0.3 * w, 0.3 * w}) {
            out.box(v(0.05, -0.28, z - 0.1), v(0.95 * l, -t, z + 0.1), DARK);
            out.cylinder(v(0, -0.05, z - 0.14), v(0, -0.05, z + 0.14), 0.12, 0.12, 12, DARK);
        }
    }

    // ---------------------------------------------------------------- the tower crane's superstructure

    /**
     * The tower crane cab vehicle's boxes by name (TowerCraneBlockEntity): true when the box is one of them and was drawn.
     * Each box is centred on its part origin; the jib's trolley is piece 1.
     */
    public static boolean towerCrane(BoxDesign d, PartMesh out) {
        Vec3d s = d.sizeM();
        double l = s.x(), h = s.y(), w = s.z();
        switch (d.name()) {
            case TowerCraneBlockEntity.JIB -> { jib(out, l, h, w); trolley(out.piece(1), h, w); out.piece(0); }
            case TowerCraneBlockEntity.COUNTER_JIB -> counterJib(out, l, h, w);
            case TowerCraneBlockEntity.BALLAST -> ballast(out, l, h, w);
            case TowerCraneBlockEntity.SLEWING_UNIT -> slewingUnit(out, l, h, w);
            case TowerCraneBlockEntity.CABIN -> cabin(out, l, h, w);
            default -> { return false; }
        }
        out.pens(null, 0);
        return true;
    }

    /** The jib's trolley at the radius the crane sends (from the mast's axis, at the jib's root end). */
    public static void towerCraneAnimate(BoxDesign d, PartVehicle entity, PoseStack stack, PartLayers layers, int light, PartRenderer.Drawn drawn) {
        if (!TowerCraneBlockEntity.JIB.equals(d.name()) || drawn == null || drawn.pieces() < 2) { return; }
        Double radius = entity.controls().get(TowerCraneBlockEntity.TROLLEY);
        double r = radius == null ? 8 : radius;
        place(layers, drawn.piece(1), v(r - d.sizeM().x() / 2, 0, 0), Orientation.IDENTITY, stack, light);
    }

    /** A flat-top jib: two bottom chords (the trolley's rails), a top chord, diagonals on both sloping faces, ties across, the tip. */
    static void jib(PartMesh out, double l, double h, double w) {
        double x0 = -l / 2 + TowerCraneBlockEntity.HEAD_M, x1 = l / 2, yb = -h / 2, yt = h / 2, cb = 0.05, ct = 0.045, lr = 0.022;
        out.finish(Finishes.PAINT_GLOSS);
        for (double s : new double[]{-1, 1}) { out.box(v(x0, yb - cb, s * w / 2 - cb), v(x1, yb + cb, s * w / 2 + cb), YELLOW); }
        out.cylinder(v(x0, yt, 0), v(x1 - 0.3, yt, 0), ct, ct, 8, YELLOW);
        int panels = Math.max(2, (int) Math.round((x1 - x0) / h));
        double p = (x1 - x0) / panels;
        for (int i = 0; i < panels; i++) {
            double xa = x0 + i * p, xb = xa + p, xm = 0.5 * (xa + xb);
            for (double s : new double[]{-1, 1}) {
                out.cylinder(v(xa, yb, s * w / 2), v(xm, yt, 0), lr, lr, 6, YELLOW);
                out.cylinder(v(xm, yt, 0), v(xb, yb, s * w / 2), lr, lr, 6, YELLOW);
            }
            out.cylinder(v(xa, yb, -w / 2), v(xa, yb, w / 2), lr, lr, 6, YELLOW);
            out.cylinder(v(xa, yb, -w / 2), v(xb, yb, w / 2), 0.7 * lr, 0.7 * lr, 6, YELLOW);
        }
        out.cylinder(v(x1, yb, -w / 2), v(x1, yb, w / 2), lr, lr, 6, YELLOW);
        plate(out, new double[][]{{-0.3, 0}, {0, 0}, {0, h}, {-0.3, h}}, -0.04, 0.08, YELLOW);
        out.finish(Finishes.LAMP_RED).box(v(x1 - 0.1, yt - 0.1, -0.06), v(x1 + 0.02, yt + 0.06, 0.06), RED);
    }

    /** The trolley on the bottom chords: its frame, four wheels, the two sheaves the hook's rope runs round (piece 1, at x = 0). */
    static void trolley(PartMesh out, double h, double w) {
        double yb = -h / 2;
        out.finish(Finishes.PAINT_GLOSS).box(v(-0.45, yb - 0.32, -w / 2 - 0.12), v(0.45, yb - 0.24, w / 2 + 0.12), YELLOW);
        for (double s : new double[]{-1, 1}) {
            double z = s * (w / 2 + 0.08);
            out.finish(Finishes.PAINT_GLOSS).box(v(-0.45, yb - 0.24, z - 0.04), v(0.45, yb + 0.12, z + 0.04), YELLOW);
            out.finish(Finishes.CAST_IRON);
            for (double x : new double[]{-0.3, 0.3}) { out.cylinder(v(x, yb + 0.09, s * w / 2 - 0.06), v(x, yb + 0.09, s * w / 2 + 0.06), 0.07, 0.07, 10, DARK); }
        }
        out.finish(Finishes.MACHINED_STEEL);
        for (double z : new double[]{-0.12, 0.12}) { out.cylinder(v(0, yb - 0.4, z - 0.03), v(0, yb - 0.4, z + 0.03), 0.13, 0.13, 14, STEEL); }
    }

    /** The counter-jib: two beams and their cross beams, the walkway, handrails, the hoist winch and its motor, the electrics. */
    static void counterJib(PartMesh out, double l, double h, double w) {
        double x0 = -l / 2, x1 = l / 2 - TowerCraneBlockEntity.HEAD_M, yb = -h / 2;
        out.finish(Finishes.PAINT_GLOSS);
        for (double s : new double[]{-1, 1}) {
            out.box(v(x0, yb, s * w / 2 - 0.07), v(x1, yb + 0.32, s * w / 2 + 0.07), YELLOW);
            out.detail(1);
            int posts = Math.max(2, (int) Math.round((x1 - x0) / 1.5));
            double zr = s * (w / 2 + 0.02);
            for (int k = 0; k <= posts; k++) {
                double x = x0 + (x1 - x0) * k / posts;
                out.cylinder(v(x, yb + 0.32, zr), v(x, yb + 1.3, zr), 0.02, 0.02, 6, YELLOW);
            }
            out.cylinder(v(x0, yb + 1.3, zr), v(x1, yb + 1.3, zr), 0.025, 0.025, 6, YELLOW);
            out.cylinder(v(x0, yb + 0.8, zr), v(x1, yb + 0.8, zr), 0.018, 0.018, 6, YELLOW);
            out.detail(0);
        }
        int beams = Math.max(2, (int) Math.round((x1 - x0) / 1.2));
        for (int k = 0; k <= beams; k++) {
            double x = x0 + (x1 - x0) * k / beams;
            out.box(v(x - 0.06, yb + 0.05, -w / 2), v(x + 0.06, yb + 0.25, w / 2), YELLOW);
        }
        out.finish(Finishes.NONSKID).box(v(x0 + 2.2, yb + 0.25, -w / 2 + 0.07), v(x1, yb + 0.29, w / 2 - 0.07), DARK);
        double xw = x1 - 3.0;
        out.finish(Finishes.PAINT_GLOSS).cylinder(v(xw, yb + 0.7, -0.45), v(xw, yb + 0.7, 0.35), 0.32, 0.32, 20, YELLOW);
        for (double z : new double[]{-0.5, 0.4}) { out.box(v(xw - 0.4, yb + 0.29, z - 0.05), v(xw + 0.4, yb + 1.1, z + 0.05), YELLOW); }
        out.finish(Finishes.CAST_IRON).cylinder(v(xw, yb + 0.7, 0.45), v(xw, yb + 0.7, 0.75), 0.22, 0.22, 14, DARK);
        out.box(v(xw - 0.25, yb + 0.45, 0.75), v(xw + 0.25, yb + 0.95, 0.95), DARK);
        out.finish(Finishes.PAINT_GLOSS).box(v(x1 - 1.7, yb + 0.29, -0.7), v(x1 - 1.1, yb + 1.5, -0.2), CABINET);
    }

    /** The ballast: concrete slabs standing side by side with their lifting eyes. */
    static void ballast(PartMesh out, double l, double h, double w) {
        int slabs = Math.max(2, (int) Math.round(l / 0.4));
        double p = l / slabs;
        for (int k = 0; k < slabs; k++) {
            double x = -l / 2 + k * p;
            out.finish(Finishes.BOARD_FORMED_CONCRETE).box(v(x + 0.02, -h / 2, -w / 2), v(x + p - 0.02, h / 2, w / 2), CONCRETE);
            out.finish(Finishes.FORGED_STEEL).detail(1);
            for (double z : new double[]{-0.3 * w, 0.3 * w}) { out.cylinder(v(x + p / 2, h / 2, z), v(x + p / 2, h / 2 + 0.08, z), 0.04, 0.04, 6, STEEL); }
            out.detail(0);
        }
    }

    /** The slewing unit over the mast's top: the slewing ring, a platform, side plates up to the jibs' roots, a top plate, the slew drives. */
    static void slewingUnit(PartMesh out, double l, double h, double w) {
        out.finish(Finishes.MACHINED_STEEL).cylinder(v(0, -h / 2, 0), v(0, -h / 2 + 0.16, 0), 0.62, 0.62, 28, DARK);
        out.finish(Finishes.PAINT_GLOSS).box(v(-l / 2, -h / 2 + 0.16, -w / 2), v(l / 2, -h / 2 + 0.36, w / 2), YELLOW);
        for (double s : new double[]{-1, 1}) {
            plate(out, new double[][]{{-l / 2, -h / 2 + 0.36}, {l / 2, -h / 2 + 0.36}, {l / 2 - 0.2, h / 2}, {-l / 2 + 0.2, h / 2}}, s * (w / 2 - 0.05) - 0.05, 0.1, YELLOW);
        }
        out.box(v(-l / 2 + 0.2, h / 2 - 0.12, -w / 2), v(l / 2 - 0.2, h / 2, w / 2), YELLOW);
        out.finish(Finishes.CAST_IRON);
        for (double x : new double[]{-0.45, 0.45}) { out.cylinder(v(x, -h / 2 + 0.36, 0.45), v(x, -h / 2 + 0.85, 0.45), 0.12, 0.12, 12, DARK); }
    }

    /**
     * The cab, hung under the jib's root and looking along it (as on flat-top cranes, seen from every side): a yellow
     * body and roof with a visor, black corner posts, sill rails and mullions, glass at the front down to the floor,
     * on the sides above the sill and in the floor's front half, a wiper, a beacon, consoles beside the seat, the
     * hangers up to the jib.
     */
    static void cabin(PartMesh out, double l, double h, double w) {
        double x0 = -l / 2, x1 = l / 2, y0 = -h / 2, y1 = h / 2, z0 = -w / 2, z1 = w / 2, f = 0.06, sill = y0 + 0.9, roof = y1 - 0.12;
        // the floor plate under the seat, a frame round the floor window ahead of it
        out.finish(Finishes.NONSKID).box(v(x0, y0, z0), v(0.1, y0 + 0.06, z1), DARK);
        out.box(v(0.1, y0, z0), v(x1, y0 + 0.06, z0 + 0.15), DARK);
        out.box(v(0.1, y0, z1 - 0.15), v(x1, y0 + 0.06, z1), DARK);
        out.box(v(x1 - 0.1, y0, z0 + 0.15), v(x1, y0 + 0.06, z1 - 0.15), DARK);
        out.finish(Finishes.PAINT_GLOSS);
        out.box(v(x0, roof, z0 - 0.03), v(x1 + 0.18, y1, z1 + 0.03), YELLOW);
        out.box(v(x0, y0, z0), v(x0 + 0.08, roof, z1), YELLOW);
        for (double z : new double[]{z0, z1 - 0.04}) { out.box(v(x0 + 0.08, y0 + 0.06, z), v(x1 - f, sill, z + 0.04), YELLOW); }
        for (double x : new double[]{x0 + 0.1, x1 - 0.15}) {
            for (double z : new double[]{z0 + 0.05, z1 - 0.05}) { out.box(v(x - 0.03, y1, z - 0.03), v(x + 0.03, y1 + 0.4, z + 0.03), YELLOW); }
        }
        out.finish(Finishes.PAINT_MATTE);
        for (double x : new double[]{x0 + 0.08, x1 - f}) { for (double z : new double[]{z0, z1 - f}) { out.box(v(x, y0, z), v(x + f, roof, z + f), BLACK); } }
        for (double z : new double[]{z0, z1 - f}) {
            out.box(v(x0 + 0.08, sill, z), v(x1, sill + 0.05, z + f), BLACK);
            out.box(v(0, sill, z), v(f, roof, z + f), BLACK);
        }
        out.box(v(x1 - f, sill, z0), v(x1, sill + 0.05, z1), BLACK);
        out.detail(1).box(v(x1 - 0.02, y1 - 0.5, -0.02), v(x1 + 0.01, roof - 0.02, 0.02), BLACK).detail(0);
        out.finish(Finishes.LAMP_AMBER).box(v(-0.06, y1, -0.06), v(0.06, y1 + 0.1, 0.06), 0xffe58c12);
        out.finish(Finishes.DASH_PLASTIC);
        for (double z : new double[]{-0.3, 0.3}) { out.box(v(-0.15, y0 + 0.06, z - 0.08), v(0.25, y0 + 0.75, z + 0.08), DARK); }
        out.finish(Finishes.GLASS).clear(true);
        out.box(v(x1 - 0.035, y0 + 0.06, z0 + f), v(x1 - 0.015, roof, z1 - f), GLASS);
        for (double z : new double[]{z0 + 0.01, z1 - 0.03}) { out.box(v(x0 + 0.08 + f, sill + 0.05, z), v(x1 - f, roof, z + 0.02), GLASS); }
        out.box(v(0.1, y0 + 0.02, z0 + 0.15), v(x1 - 0.1, y0 + 0.04, z1 - 0.15), GLASS);   // the floor window
        out.clear(false);
    }
}
