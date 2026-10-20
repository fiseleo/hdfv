package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Mesh;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.MeshOps;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.manufacture.MachineFrame;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.ToolConfig;
import dev.mcmod.hdfv.manufacture.ToolConfig.Accessory;
import dev.mcmod.hdfv.neoforge.HdfvParticles;
import dev.mcmod.hdfv.neoforge.client.model.Details;
import dev.mcmod.hdfv.neoforge.client.model.MachineKit;
import dev.mcmod.hdfv.neoforge.client.model.MachineKit.Part;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The machining families drawn whole (0.17) over their formed blocks, laid out by their MachineFrame and dressed by
 * their head: the machining kit's parts (MachineKit, made in Blender) where they are detailed, boxes and cylinders for
 * what stretches with the size (beds, tables, tanks, columns, rails). As ToolRenderer: the fixed part is baked once for
 * the machine's kind, facing, size, head, drive and accessories; what moves (wheels, rams, hobs, the gantry, the bow)
 * is built each frame while it works and baked once where it rests otherwise (MachineMeshes). While it works its tool
 * gives off sparks, coolant, chips, mist or smoke, and an EDM's gap, a plasma arc and a laser's spot light up
 * (MachineEffects.tool). Sizes in metres in the pattern's frame (PatternFrame: x right facing the machine, y up, z
 * back); a kit part's Blender +y (the operator's side) faces the front at yaw 0, its +x to the frame's -x.
 */
final class FamilyRenderer {
    private FamilyRenderer() {}

    private static final int STEEL = 0xff8c9196, BRIGHT = 0xffc9ced3, DARK = 0xff2e3135, IRON = 0xff4a4f55, WORK = 0xffa7adb3, SCREEN = 0xff2b2f33,
            LCD = 0xff6fd16b, FLUID = 0xff1f3f5c, WATER = 0xff3d6f8f, SLAT = 0xff55585c, ALU = 0xffc4c8cc, ALU_DARK = 0xffaeb3b8, BRASS = 0xffb08d3c,
            CAST = 0xff5d6066, GRAPHITE = 0xff2a2a2c;

    /** Each family's paint. */
    static int paint(Station s) {
        return switch (s) {
            case GRINDER -> 0xff4a6984; case FINISHER -> 0xff5b6f5a; case GEAR_CUTTER -> 0xff7d6f4e; case RAM_MACHINE -> 0xff4f5d43;
            case SAWING_MACHINE -> 0xffc4822a; case ELECTRO_MACHINE -> 0xffcfd3d6; case CUTTING_TABLE -> 0xffd0a22c; case THREADER -> 0xff53687c;
            default -> 0xffa33a30;
        };
    }

    private static final class Seen { Pattern.Match match; long at = Long.MIN_VALUE / 2, effects = Long.MIN_VALUE / 2; }
    private static final Map<MachineBlockEntity, Seen> SEEN = new WeakHashMap<>();

    /** A cell's place in the pattern (column, layer, row) and its kind. */
    private record Local(int col, int layer, int row, String kind) {}

    /** What a machine's fixed part is made of. */
    private record Key(Station station, int facing, int size, ToolConfig config, List<Local> cells) {}

    private static final Set<String> ACCESSORIES = java.util.Arrays.stream(Accessory.values()).map(a -> a.kind).collect(Collectors.toSet());

    private static final int[][] FRONT = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    /** The accessory cells of a match in the pattern's places, the controller at (col, layer, row). */
    private static List<Local> accessories(Pattern.Match m, net.minecraft.core.BlockPos controller, int col, int layer, int row) {
        int[] f = FRONT[Math.floorMod(m.facing(), 4)];
        int dx = -f[0], dz = -f[1], rx = -dz, rz = dx;
        List<Local> out = new ArrayList<>();
        for (Pattern.Cell c : m.cells()) {
            if (!ACCESSORIES.contains(c.kind())) { continue; }
            int wx = c.x() - controller.getX(), wz = c.z() - controller.getZ();
            out.add(new Local(wx * rx + wz * rz + col, c.y() - controller.getY() + layer, wx * dx + wz * dz + row, c.kind()));
        }
        return out;
    }

    static void render(MachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (be.getLevel() == null) { return; }
        Seen s = SEEN.computeIfAbsent(be, b -> new Seen());
        long now = be.getLevel().getGameTime();
        if (now - s.at >= 20 || now < s.at) { s.at = now; s.match = MachineBlockEntity.machineAt(be.getLevel(), be.getBlockPos(), be.station); }
        Pattern.Match m = s.match;
        if (m == null) { return; }
        ToolConfig cfg = Structures.toolConfig(be.station, m);
        MachineFrame mf = MachineFrame.of(be.station);
        if (cfg == null || cfg.head() == null || mf == null) { return; }
        Draw d = new Draw(new PatternFrame(m.facing(), mf.controllerCol, mf.controllerLayer, mf.controllerRow), paint(be.station));
        int n = cfg.size();
        List<Local> cells = accessories(m, be.getBlockPos(), mf.controllerCol, mf.controllerLayer, mf.controllerRow);
        double t = (now + partialTick) / 20.0;
        boolean working = be.shownWorking();
        Key key = new Key(be.station, m.facing(), n, cfg, cells);
        Supplier<PartMesh> fixed = () -> { PartMesh p = new PartMesh(); fixed(d, p, cfg, cells, n); return p; };
        Supplier<PartMesh> moving = () -> { PartMesh p = new PartMesh(); moving(d, p, cfg, n, t, working); return p; };
        if (!working) { MachineMeshes.drawResting(key, fixed, List.of(key, "rest"), moving, be.getBlockPos(), pose, buffers, light); return; }
        MachineMeshes.draw(key, fixed, moving.get(), be.getBlockPos(), pose, buffers, light);
        Effect e = effect(cfg, n, t);
        if (e == null) { return; }
        Vec3d at = d.f.at(e.x(), e.y(), e.z());
        if (e.light() != null) { MachineEffects.tool(e.light(), at, MachineEffects.toolLevel(e.light(), now, partialTick, be.getBlockPos().hashCode()), pose, buffers); }
        if (s.effects != now) {
            s.effects = now;
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (camera.distanceToSqr(be.getBlockPos().getCenter()) < 48 * 48) {
                particles(be.getLevel().getRandom(), be.getBlockPos().getX() + at.x(), be.getBlockPos().getY() + at.y(), be.getBlockPos().getZ() + at.z(), e.kinds());
            }
        }
    }

    // ---------------------------------------------------------------- drawing in the pattern's frame

    /** The frame with the machine's paint: boxes, cylinders and kit parts. */
    static final class Draw {
        final PatternFrame f;
        final int paint;
        private final Vec3d right;

        Draw(PatternFrame f, int paint) { this.f = f; this.paint = paint; this.right = f.at(1, 0, 0).subtract(f.at(0, 0, 0)); }

        void box(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1, int c) { f.box(m, x0, y0, z0, x1, y1, z1, c); }

        void paint(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1) { f.box(m, x0, y0, z0, x1, y1, z1, paint); }

        void cyl(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1, double r, int c) { f.cylinder(m, x0, y0, z0, x1, y1, z1, r, c); }

        void kit(PartMesh m, Part p, double x, double y, double z, double yaw) { kit(m, p, x, y, z, yaw, 0, 0, 1, true); }

        /**
         * A kit part with its origin at a point: turned about up by yaw, then about its own x by spin (a wheel turning),
         * then about its own back axis by tilt (a bow lowering: its +x end up), scaled.
         */
        void kit(PartMesh m, Part p, double x, double y, double z, double yaw, double spin, double tilt, double scale, boolean fine) {
            kit(m, p, x, y, z, yaw, spin, tilt, new Vec3d(scale, scale, scale), fine);
        }

        void kit(PartMesh m, Part p, double x, double y, double z, double yaw, double spin, double tilt, Vec3d scale, boolean fine) {
            Vec3d up = new Vec3d(0, 1, 0), kx = turn(right.scale(-1), yaw), ky = up, kz = kx.cross(ky);
            if (spin != 0) { ky = ky.scale(Math.cos(spin)).add(kz.scale(Math.sin(spin))); }
            if (tilt != 0) {
                Vec3d x2 = kx.scale(Math.cos(tilt)).add(ky.scale(Math.sin(tilt)));
                ky = ky.scale(Math.cos(tilt)).subtract(kx.scale(Math.sin(tilt)));
                kx = x2;
            }
            Mesh placed = MeshOps.placed(MachineKit.mesh(p, fine, paint), scale, Details.basis(ky, kx), f.at(x, y, z));
            for (MeshBuilder.Quad q : placed.quads()) { m.quads().add(q.withTags(q.tags() & ~MeshBuilder.DETAIL)); }
        }

        /** A digital readout's screen facing the front at z. */
        void readout(PartMesh m, double x0, double y0, double z, double x1, double y1) {
            box(m, x0, y0, z - 0.06, x1, y1, z, SCREEN);
            for (int k = 0; k < 3; k++) {
                double y = y1 - 0.07 - 0.07 * k;
                box(m, x0 + 0.05, y - 0.02, z - 0.07, x0 + 0.05 + (x1 - x0 - 0.1) * (k == 2 ? 0.6 : 0.9), y, z - 0.06, LCD);
            }
        }

        /** The control (a cabinet and pendant; the NC cabinet with an NC drive) standing at y over the controller's cell. */
        void console(PartMesh m, double y, boolean nc) {
            if (nc) { kit(m, Part.NC_CABINET, 0.5, y, 0.5, 0, 0, 0, 0.85, true); return; }
            paint(m, 0.12, y, 0.2, 0.88, y + 0.7, 0.85);
            kit(m, Part.CONTROL_PENDANT, 0.5, y + 0.7, 0.4, 0, 0, 0, 0.8, true);
        }
    }

    private static Vec3d turn(Vec3d v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(v.x() * c + v.z() * s, v.y(), -v.x() * s + v.z() * c);
    }

    /** An angle turning at a rate (rad/s) while it works; still otherwise. */
    private static double spin(double t, boolean working, double rate) { return working ? t * rate % (2 * Math.PI) : 0.3; }

    /** 0..1 to and fro while it works; rest otherwise. */
    private static double swing(boolean working, double t, double rate, double rest) { return working ? 0.5 + 0.5 * Math.sin(t * rate) : rest; }

    private static void fixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        switch (cfg.station()) {
            case GRINDER -> grinderFixed(d, m, cfg, cells, n);
            case FINISHER -> finisherFixed(d, m, cfg, cells, n);
            case GEAR_CUTTER -> gearFixed(d, m, cfg, cells, n);
            case RAM_MACHINE -> ramFixed(d, m, cfg, cells, n);
            case SAWING_MACHINE -> sawFixed(d, m, cfg, cells, n);
            case ELECTRO_MACHINE -> electroFixed(d, m, cfg, cells, n);
            case CUTTING_TABLE -> tableFixed(d, m, cfg, cells, n);
            case THREADER -> threaderFixed(d, m, cfg, cells);
            case RIVETER -> riveterFixed(d, m, cfg, n);
            default -> { }
        }
    }

    private static void moving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        switch (cfg.station()) {
            case GRINDER -> grinderMoving(d, m, cfg, n, t, w);
            case FINISHER -> finisherMoving(d, m, cfg, n, t, w);
            case GEAR_CUTTER -> gearMoving(d, m, cfg, n, t, w);
            case RAM_MACHINE -> ramMoving(d, m, cfg, n, t, w);
            case SAWING_MACHINE -> sawMoving(d, m, cfg, n, t, w);
            case ELECTRO_MACHINE -> electroMoving(d, m, cfg, n, t, w);
            case CUTTING_TABLE -> tableMoving(d, m, cfg, n, t, w);
            case THREADER -> threaderMoving(d, m, cfg, t, w);
            case RIVETER -> riveterMoving(d, m, cfg, n, t, w);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- grinder: bed 1..n+1 along x, the head over its end

    private static final double G_TOP = 0.95, WORK_Y = 1.25, WORK_Z = 0.26;

    private static void grinderFixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        double end = n + 2.0, top = G_TOP, hx = n + 1.5;
        d.console(m, 1.0, cfg.nc());
        d.paint(m, 1.0, 0, 0.08, end, 0.9, 0.92);
        d.box(m, 1.0, 0.9, 0.15, end, top, 0.85, IRON);
        d.paint(m, 1.0, top, 0.05, end, top + 0.1, 0.1);
        d.paint(m, 1.0, top, 0.9, end, top + 0.3, 0.95);
        switch (cfg.head()) {
            case CYLINDRICAL_GRINDING -> {
                d.kit(m, Part.WHEELHEAD, hx, top, 0.8, 0);
                d.box(m, 1.05, top, 0.16, 1.45, top + 0.1, 0.36, IRON);
                d.box(m, n + 1.55, top, 0.16, n + 1.95, top + 0.1, 0.36, IRON);
                d.kit(m, Part.CENTRE_STOCK, 1.25, top + 0.1, WORK_Z, Math.PI);
                d.kit(m, Part.MOTOR, 1.25, top + 0.1, 0.65, 0, 0, 0, 0.6, true);
                d.kit(m, Part.CENTRE_STOCK, n + 1.8, top + 0.1, WORK_Z, 0);
                d.kit(m, Part.COOLANT_NOZZLE, hx - 0.15, top + 0.55, 0.6, 0);
            }
            case INTERNAL_GRINDING -> {
                d.kit(m, Part.INTERNAL_SPINDLE, n + 1.65, top, 0.5, 0);
                d.paint(m, n + 0.55, top, 0.25, n + 1.02, top + 0.38, 0.75);
                d.kit(m, Part.MOTOR, n + 0.78, top + 0.38, 0.5, 0, 0, 0, 0.6, true);
                d.kit(m, Part.COOLANT_NOZZLE, n + 1.35, top + 0.35, 0.78, 0);
            }
            case SURFACE_GRINDING -> {
                d.paint(m, n + 1.15, top, 0.7, n + 1.85, top + 1.2, 0.92);
                d.paint(m, n + 1.3, 1.15, 0.42, n + 1.7, 1.45, 0.72);
                d.kit(m, Part.MOTOR, n + 1.5, 1.45, 0.58, Math.PI / 2, 0, 0, 0.6, true);
                d.kit(m, Part.COOLANT_NOZZLE, n + 1.3, 1.45, 0.55, 0);
            }
            case CENTRELESS_GRINDING -> {
                d.kit(m, Part.CENTRELESS_FRAME, hx, top, 0.5, 0);
                d.box(m, 1.1, 1.4, 0.44, n + 1.22, 1.47, 0.51, IRON);
                d.kit(m, Part.COOLANT_NOZZLE, hx + 0.2, top + 0.85, 0.75, 0);
            }
            case TOOL_GRINDING -> {
                d.kit(m, Part.TOOL_GRINDER_HEAD, hx, top, 0.75, 0);
                d.box(m, hx - 0.48, top, 0.3, hx - 0.22, 1.19, 0.46, IRON);
                d.box(m, hx + 0.22, top, 0.3, hx + 0.48, 1.19, 0.46, IRON);
                d.kit(m, Part.CENTRE_STOCK, hx - 0.38, 1.19, 0.38, Math.PI, 0, 0, 0.7, true);
                d.kit(m, Part.CENTRE_STOCK, hx + 0.38, 1.19, 0.38, 0, 0, 0, 0.7, true);
            }
            default -> { }
        }
        for (Local c : cells) {
            double x = c.col() + 0.5;
            if (Accessory.DIAMOND_DRESSER.kind.equals(c.kind())) { d.kit(m, Part.DIAMOND_DRESSER, x, top, 0.14, 0); }
            else if (Accessory.MAGNETIC_CHUCK.kind.equals(c.kind()) && cfg.head() != Head.SURFACE_GRINDING) { d.kit(m, Part.MAGNETIC_CHUCK, x, top, 0.6, 0); }
            else if (Accessory.STEADY_REST.kind.equals(c.kind())) {
                d.paint(m, x - 0.05, top, 0.1, x + 0.05, WORK_Y + 0.12, 0.16);
                d.paint(m, x - 0.05, top, 0.36, x + 0.05, WORK_Y + 0.12, 0.42);
                d.paint(m, x - 0.05, WORK_Y + 0.08, 0.1, x + 0.05, WORK_Y + 0.12, 0.42);
                d.box(m, x - 0.03, WORK_Y - 0.03, 0.16, x + 0.03, WORK_Y + 0.03, WORK_Z - 0.04, BRASS);
                d.box(m, x - 0.03, WORK_Y + 0.04, WORK_Z - 0.03, x + 0.03, WORK_Y + 0.08, WORK_Z + 0.03, BRASS);
            } else if (Accessory.DIGITAL_READOUT.kind.equals(c.kind())) { d.readout(m, c.col() + 0.2, 1.3, 0.9, c.col() + 0.8, 1.6); }
        }
    }

    /** The surface grinder's chuck: to and fro under the wheel. */
    private static double chuckX(int n, double t, boolean w) { return n + 1.2 + (w ? 0.3 * Math.sin(t * 1.2) : 0); }

    private static void grinderMoving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        double hx = n + 1.5, top = G_TOP;
        switch (cfg.head()) {
            case CYLINDRICAL_GRINDING -> {
                d.kit(m, Part.GRINDING_WHEEL, hx, WORK_Y, 0.5, 0, spin(t, w, 25), 0, 1, true);
                d.cyl(m, 1.42, WORK_Y, WORK_Z, n + 1.63, WORK_Y, WORK_Z, 0.04, WORK);
                double a = spin(t, w, 3);
                d.box(m, 1.46, WORK_Y + 0.06 * Math.cos(a) - 0.02, WORK_Z + 0.06 * Math.sin(a) - 0.02, 1.5, WORK_Y + 0.06 * Math.cos(a) + 0.02,
                        WORK_Z + 0.06 * Math.sin(a) + 0.02, DARK);
            }
            case INTERNAL_GRINDING -> {
                d.cyl(m, n + 1.02, 1.1, 0.5, n + 1.14, 1.1, 0.5, 0.11, IRON);
                d.cyl(m, n + 1.14, 1.1, 0.5, n + 1.32, 1.1, 0.5, 0.08, WORK);
                double a = spin(t, w, 4);
                for (int k = 0; k < 3; k++) {
                    double q = a + k * 2 * Math.PI / 3, y = 1.1 + 0.09 * Math.cos(q), z = 0.5 + 0.09 * Math.sin(q);
                    d.box(m, n + 1.14, y - 0.025, z - 0.025, n + 1.2, y + 0.025, z + 0.025, BRIGHT);
                }
            }
            case SURFACE_GRINDING -> {
                d.kit(m, Part.GRINDING_WHEEL, hx, 1.25, 0.35, Math.PI / 2, spin(t, w, 25), 0, 1, true);
                double cx = chuckX(n, t, w);
                d.kit(m, Part.MAGNETIC_CHUCK, cx, top, 0.35, 0);
                d.box(m, cx - 0.25, top + 0.08, 0.24, cx + 0.25, top + 0.1, 0.46, WORK);
            }
            case CENTRELESS_GRINDING -> {
                d.kit(m, Part.GRINDING_WHEEL, hx, 1.5, 0.72, 0, spin(t, w, 25), 0, 1, true);
                d.kit(m, Part.REGULATING_WHEEL, hx, 1.5, 0.28, 0, -spin(t, w, 2), 0, 1, true);
                double feed = w ? (t * 0.15) % 0.5 : 0;
                for (double x0 = 1.1 + feed - 0.5; x0 < n + 1.95; x0 += 0.5) {
                    double a = Math.max(1.1, x0), b = Math.min(n + 1.95, x0 + 0.46);
                    if (b > a) { d.cyl(m, a, 1.49, 0.475, b, 1.49, 0.475, 0.02, WORK); }
                }
            }
            case TOOL_GRINDING -> d.kit(m, Part.HOB, hx, 1.33, 0.38, 0, spin(t, w, 0.8), 0, 1.6, true);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- finisher and gear cutter: the table at col 1, the column at col 0, the head at the top

    private static void columnAndArm(Draw d, PartMesh m, int n) {
        double top = n + 1;
        d.paint(m, 0.05, 0, 0.1, 0.95, 0.95, 0.95);
        d.kit(m, Part.CONTROL_PENDANT, 0.5, 0.95, 0.12, 0, 0, 0, 0.7, true);
        d.paint(m, 0.15, 0.95, 0.3, 0.85, top + 0.2, 0.92);
        d.paint(m, 0.1, top + 0.2, 0.3, 1.9, top + 0.75, 0.9);
    }

    private static void finisherFixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        double top = n + 1;
        columnAndArm(d, m, n);
        d.paint(m, 1.05, 0, 0.1, 1.95, 0.75, 0.9);
        d.box(m, 1.1, 0.75, 0.15, 1.9, 0.82, 0.85, IRON);
        switch (cfg.head()) {
            case HONING -> {
                d.paint(m, 1.3, 1.55, 0.72, 1.7, top + 0.2, 0.9);
                d.kit(m, Part.HONING_HEAD, 1.5, 1.55, 0.65, 0);
                d.box(m, 1.25, 0.82, 0.25, 1.75, 1.1, 0.75, CAST);
                d.cyl(m, 1.5, 1.1, 0.5, 1.5, 1.103, 0.5, 0.045, DARK);
            }
            case LAPPING -> {
                d.paint(m, 1.25, top - 0.1, 0.3, 1.75, top + 0.2, 0.75);
                for (int k = 0; k < 3; k++) {
                    double a = Math.PI / 2 + k * 2 * Math.PI / 3, x = 1.5 + 0.2 * Math.cos(a), z = 0.5 + 0.2 * Math.sin(a);
                    d.cyl(m, x, top - 0.1, z, x, 0.96, z, 0.012, STEEL);
                }
            }
            case POLISHING -> {
                d.kit(m, Part.POLISHING_HEAD, 1.5, 0.82, 0.55, 0);
                if (top + 0.2 > 2.1) { d.cyl(m, 1.5, 2.1, 0.7, 1.5, top + 0.2, 0.7, 0.08, d.paint); }
            }
            case DEBURRING -> {
                d.kit(m, Part.VIBRATORY_BOWL, 1.5, 0.82, 0.5, 0);
                d.paint(m, 1.3, top - 0.2, 0.35, 1.7, top + 0.2, 0.75);
                d.cyl(m, 1.5, top - 0.2, 0.45, 1.5, 1.45, 0.4, 0.04, STEEL);
            }
            default -> { }
        }
        for (Local c : cells) { if (Accessory.DIGITAL_READOUT.kind.equals(c.kind())) { d.readout(m, 0.2, c.layer() + 0.3, 0.3, 0.8, c.layer() + 0.6); } }
    }

    /** The honing tool's top: up at rest, stroking down into the bore while it works. */
    private static double honeTop(double t, boolean w) { return 1.2 + 0.25 * swing(w, t, 2.5, 1); }

    private static void finisherMoving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        switch (cfg.head()) {
            case HONING -> {
                double top = honeTop(t, w);
                d.cyl(m, 1.5, 1.55, 0.5, 1.5, top, 0.5, 0.02, STEEL);
                d.kit(m, Part.HONING_TOOL, 1.5, top, 0.5, spin(t, w, 6), 0, 0, 1, true);
            }
            case LAPPING -> {
                d.kit(m, Part.LAPPING_PLATE, 1.5, 0.88, 0.5, spin(t, w, 1.2), 0, 0, 1, true);
                for (int k = 0; k < 3; k++) {
                    double a = Math.PI / 2 + k * 2 * Math.PI / 3;
                    d.kit(m, Part.LAP_RING, 1.5 + 0.2 * Math.cos(a), 0.932, 0.5 + 0.2 * Math.sin(a), spin(t, w, 1.6) + k, 0, 0, 1, false);
                }
            }
            case POLISHING -> {
                d.kit(m, Part.BUFF_WHEEL, 1.2, 1.77, 0.55, 0, spin(t, w, 30), 0, 1, false);
                d.kit(m, Part.BUFF_WHEEL, 1.8, 1.77, 0.55, 0, spin(t, w, 30), 0, 1, false);
            }
            case DEBURRING -> {
                for (int k = 0; k < 6; k++) {
                    double a = (w ? t * 1.2 : 0) + k * Math.PI / 3, x = 1.5 + 0.27 * Math.cos(a), z = 0.5 + 0.27 * Math.sin(a);
                    double y = 1.24 + (w ? 0.015 * Math.sin(t * 25 + k) : 0);
                    d.box(m, x - 0.03, y, z - 0.02, x + 0.03, y + 0.03, z + 0.02, WORK);
                }
            }
            default -> { }
        }
    }

    private static void gearFixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        double top = n + 1;
        columnAndArm(d, m, n);
        d.paint(m, 1.15, 0, 0.1, 1.85, 0.7, 0.6);
        d.cyl(m, 1.5, 0.7, 0.4, 1.5, 0.8, 0.4, 0.3, IRON);
        d.cyl(m, 1.5, 0.8, 0.4, 1.5, 1.05, 0.4, 0.022, STEEL);
        switch (cfg.head()) {
            case HOBBING -> {
                d.kit(m, Part.HOB_SLIDE, 1.5, 0.6, 0.73, 0);
                d.paint(m, 1.3, 1.2, 0.78, 1.7, top + 0.2, 0.92);
            }
            case GEAR_SHAPING, SHAVING -> d.paint(m, 1.35, 1.2, 0.45, 1.65, top + 0.2, 0.78);
            case GEAR_GRINDING -> {
                d.paint(m, 1.25, 0.75, 0.68, 1.75, 1.1, 0.92);
                d.kit(m, Part.MOTOR, 1.5, 1.1, 0.8, 0, 0, 0, 0.6, true);
                d.paint(m, 1.3, 1.3, 0.78, 1.7, top + 0.2, 0.92);
                d.kit(m, Part.COOLANT_NOZZLE, 1.25, 1.1, 0.6, 0);
            }
            case CHAMFERING -> {
                d.paint(m, 1.35, 1.05, 0.45, 1.65, 1.35, 0.75);
                d.paint(m, 1.4, 1.35, 0.55, 1.6, top + 0.2, 0.75);
            }
            default -> { }
        }
        for (Local c : cells) {
            if (!Accessory.AUTO_LOADER.kind.equals(c.kind())) { continue; }
            double y = c.layer();
            d.paint(m, 0.25, y, 1.1, 0.75, y + 0.8, 1.9);
            d.box(m, 0.5, y + 0.65, 1.0, 1.6, y + 0.75, 1.1, STEEL);
            d.box(m, 1.45, y + 0.45, 0.95, 1.6, y + 0.65, 1.15, DARK);
        }
    }

    /** The shaper cutter's stroke: up at rest. */
    private static double shapeStroke(double t, boolean w) { return swing(w, t, 8, 1); }

    private static void gearMoving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        d.kit(m, Part.GEAR_WORK, 1.5, 0.9, 0.4, spin(t, w, 0.5), 0, 0, 1, true);
        switch (cfg.head()) {
            case HOBBING -> d.kit(m, Part.HOB, 1.5, 0.9, 0.53, 0, spin(t, w, 15), 0, 1, true);
            case GEAR_SHAPING -> {
                double y = 0.86 + 0.12 * shapeStroke(t, w);
                d.kit(m, Part.SHAPER_CUTTER, 1.5, y, 0.56, spin(t, w, 0.6), 0, 0, 1, true);
                d.cyl(m, 1.5, y + 0.2, 0.56, 1.5, 1.2, 0.56, 0.025, STEEL);
            }
            case SHAVING -> {
                d.kit(m, Part.GEAR_WORK, 1.5, 0.88, 0.6, 0, 0.26, 0, 0.9, true);
                d.cyl(m, 1.5, 0.95, 0.6, 1.5, 1.2, 0.6, 0.025, STEEL);
            }
            case GEAR_GRINDING -> d.kit(m, Part.GRINDING_WHEEL, 1.5, 0.9, 0.61, 0, spin(t, w, 25), 0, 0.6, true);
            case CHAMFERING -> d.cyl(m, 1.5, 0.96, 0.52, 1.5, 1.05, 0.52, 0.02, BRIGHT);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- ram machine: bed 1..n+1, the head's column at its end

    private static void ramFixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        double end = n + 2.0;
        d.console(m, 1.0, cfg.nc());
        d.paint(m, 1.0, 0, 0.1, end, 0.8, 0.9);
        d.box(m, 1.1, 0.8, 0.15, n + 1.0, 0.95, 0.85, IRON);
        for (int k = 0; k < 3; k++) { d.box(m, 1.1, 0.95, 0.3 + 0.2 * k, n + 1.0, 0.951, 0.32 + 0.2 * k, DARK); }
        switch (cfg.head()) {
            case SHAPER -> {
                d.paint(m, n + 1.05, 0.8, 0.1, n + 1.95, 1.75, 0.9);
                d.kit(m, Part.BAR_VISE, n + 0.5, 0.95, 0.5, 0);
                d.box(m, n + 0.4, 1.11, 0.4, n + 0.6, 1.25, 0.6, WORK);
            }
            case SLOTTER -> {
                d.paint(m, n + 1.05, 0.8, 0.3, n + 1.95, 2.3, 0.95);
                d.paint(m, n + 0.3, 2.0, 0.35, n + 1.95, 2.3, 0.85);
                d.cyl(m, n + 0.6, 0.95, 0.5, n + 0.6, 1.0, 0.5, 0.25, IRON);
                d.cyl(m, n + 0.6, 1.0, 0.5, n + 0.6, 1.1, 0.5, 0.1, WORK);
            }
            case BROACHING -> {
                d.box(m, n + 0.85, 0.95, 0.25, n + 0.95, 1.45, 0.75, IRON);
                d.cyl(m, n + 0.75, 1.2, 0.5, n + 0.85, 1.2, 0.5, 0.1, WORK);
                d.paint(m, n + 1.4, 0.8, 0.2, n + 1.95, 1.6, 0.8);
            }
            default -> { }
        }
        for (Local c : cells) {
            if (!Accessory.DIVIDING_HEAD.kind.equals(c.kind())) { continue; }
            double x = c.col();
            d.paint(m, x + 0.2, 0.95, 0.3, x + 0.55, 1.25, 0.7);
            d.cyl(m, x + 0.55, 1.12, 0.5, x + 0.65, 1.12, 0.5, 0.08, IRON);
            d.cyl(m, x + 0.15, 1.12, 0.5, x + 0.2, 1.12, 0.5, 0.1, BRIGHT);
        }
    }

    private static double ramStroke(double t, boolean w) { return swing(w, t, 4, 0); }

    private static double broachTravel(double t, boolean w) { return w ? (t * 0.3) % 1 : 0; }

    private static void ramMoving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        switch (cfg.head()) {
            case SHAPER -> d.kit(m, Part.SHAPER_RAM, n + 1.24 - 0.4 * ramStroke(t, w), 1.55, 0.5, 0);
            case SLOTTER -> d.kit(m, Part.SLOTTER_RAM, n + 0.6, 2.11 - 0.27 * ramStroke(t, w), 0.45, 0);
            case BROACHING -> {
                double x = n + 0.6 + 0.6 * broachTravel(t, w);
                d.kit(m, Part.BROACH, x, 1.2, 0.5, 0);
                d.cyl(m, x + 0.4, 1.2, 0.5, n + 1.45, 1.2, 0.5, 0.015, STEEL);
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- saw: roller bed 1..n, the saw at col n+1

    private static void sawFixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        d.console(m, 1.0, cfg.nc());
        d.paint(m, 1.0, 0, 0.25, n + 1.0, 0.7, 0.75);
        for (double x = 1.25; x < n + 1.0; x += 0.5) { d.cyl(m, x, 0.78, 0.25, x, 0.78, 0.75, 0.06, STEEL); }
        d.paint(m, n + 1.02, 0, 0.08, n + 1.98, 0.8, 0.92);
        d.kit(m, Part.BAR_VISE, n + 1.5, 0.8, 0.5, 0);
        d.cyl(m, 1.1, 0.88, 0.5, n + 1.75, 0.88, 0.5, 0.04, WORK);
        switch (cfg.head()) {
            case BAND_SAW -> d.box(m, n + 1.85, 0.8, 0.4, n + 1.98, 1.0, 0.6, IRON);
            case COLD_SAW -> {
                d.paint(m, n + 0.8, 0.8, 0.3, n + 0.95, 1.3, 0.5);
                d.kit(m, Part.COOLANT_NOZZLE, n + 1.7, 1.1, 0.75, 0);
            }
            case HACKSAW -> {
                d.paint(m, n + 1.75, 0.8, 0.75, n + 1.9, 1.45, 0.9);
                d.paint(m, n + 1.4, 1.2, 0.75, n + 1.85, 1.28, 0.85);
            }
            default -> { }
        }
        for (Local c : cells) {
            double x = c.col() + 0.5;
            if (Accessory.BAR_FEEDER.kind.equals(c.kind())) {
                d.paint(m, x - 0.2, 0.84, 0.35, x + 0.2, 1.05, 0.65);
                d.box(m, x - 0.5, 0.7, 0.2, x + 0.5, 0.74, 0.25, BRIGHT);
            } else if (Accessory.MITRE_VISE.kind.equals(c.kind())) { d.kit(m, Part.BAR_VISE, x, 0.84, 0.5, Math.PI / 4, 0, 0, 0.8, true); }
        }
    }

    /** How far a cut has gone (0 at its start, 1 through the bar), in a cycle while it works; raised at rest. */
    private static double cut(double t, boolean w) { return w ? (t * 0.12) % 1 : -1; }

    private static void sawMoving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        double c = cut(t, w);
        switch (cfg.head()) {
            case BAND_SAW -> d.kit(m, Part.BAND_SAW_BOW, n + 1.95, 0.95, 0.5, 0, 0, c < 0 ? 0.45 : 0.12 * (1 - c), 1, true);
            case COLD_SAW -> {
                double y = c < 0 ? 1.15 : 1.12 - 0.1 * c;
                d.kit(m, Part.COLD_SAW_HEAD, n + 1.2, y, 0.38, Math.PI);
                d.kit(m, Part.CIRCULAR_SAW_BLADE, n + 1.5, y, 0.5, Math.PI, spin(t, w, 6), 0, 1, true);
            }
            case HACKSAW -> d.kit(m, Part.HACKSAW_FRAME, n + 1.5 + (w ? 0.08 * Math.sin(t * 5) : 0), c < 0 ? 0.98 : 0.92 - 0.08 * c, 0.5, 0);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- electrical machining: the tank 1..n+1, the head over its end

    private static void electroFixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        double end = n + 2.0, hx = n + 1.5;
        d.kit(m, Part.NC_CABINET, 0.5, 1.0, 0.5, 0, 0, 0, 0.85, true);
        d.paint(m, 1.0, 0, 0.05, end, 0.86, 0.95);
        d.box(m, 1.06, 0.86, 0.11, end - 0.06, 0.9, 0.89, cfg.head() == Head.ECM ? DARK : FLUID);
        d.box(m, 1.0, 0.86, 0.05, end, 0.95, 0.11, BRIGHT);
        d.box(m, 1.0, 0.86, 0.89, end, 0.95, 0.95, BRIGHT);
        d.box(m, 1.0, 0.86, 0.11, 1.06, 0.95, 0.89, BRIGHT);
        d.box(m, end - 0.06, 0.86, 0.11, end, 0.95, 0.89, BRIGHT);
        switch (cfg.head()) {
            case SINKER_EDM -> {
                d.box(m, hx - 0.2, 0.9, 0.42, hx + 0.2, 0.98, 0.82, WORK);
                d.paint(m, n + 1.15, 0.95, 0.85, n + 1.85, 2.15, 0.98);
                d.kit(m, Part.EDM_QUILL, hx, 1.55, 0.75, 0);
            }
            case WIRE_EDM -> d.kit(m, Part.WIRE_HEAD, hx, 0.75, 0.97, 0);
            case HOLE_EDM -> {
                d.box(m, hx - 0.2, 0.9, 0.38, hx + 0.2, 0.98, 0.78, WORK);
                d.paint(m, n + 1.15, 0.95, 0.75, n + 1.85, 1.75, 0.98);
                d.kit(m, Part.HOLE_EDM_HEAD, hx, 1.25, 0.7, 0);
            }
            case ECM -> d.kit(m, Part.ECM_CELL, hx, 0.95, 0.55, 0);
            default -> { }
        }
        for (Local c : cells) {
            if (!Accessory.ELECTRODE_CHANGER.kind.equals(c.kind())) { continue; }
            double x = c.col();
            d.paint(m, x + 0.25, 0.95, 0.75, x + 0.75, 1.5, 0.9);
            for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) {
                d.box(m, x + 0.3 + 0.15 * i, 1.1 + 0.2 * j, 0.68, x + 0.4 + 0.15 * i, 1.2 + 0.2 * j, 0.75, GRAPHITE);
            }
        }
    }

    private static double edmFeed(double t, boolean w) { return w ? 0.12 + 0.008 * Math.sin(t * 30) : 0; }

    private static void electroMoving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        double hx = n + 1.5;
        switch (cfg.head()) {
            case SINKER_EDM -> d.kit(m, Part.EDM_RAM, hx, 1.55 - edmFeed(t, w), 0.63, 0);
            case WIRE_EDM -> {
                double dx = w ? 0.1 * Math.sin(t * 0.3) : 0;
                d.box(m, hx - 0.25 + dx, 0.9, 0.35, hx + 0.25 + dx, 1.15, 0.7, WORK);
                d.cyl(m, hx, 0.87, 0.52, hx, 1.37, 0.52, 0.003, BRASS);
            }
            case HOLE_EDM -> d.cyl(m, hx, 1.15, 0.58, hx, w ? 0.99 : 1.06, 0.58, 0.004, BRASS);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- cutting table: slats 1..n+1 by two rows, the gantry over them

    private static void tableFixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        double end = n + 2.0;
        d.kit(m, Part.NC_CABINET, 0.5, 1.0, 0.5, 0, 0, 0, 0.85, true);
        d.paint(m, 1.0, 0, 0.0, end, 0.7, 2.0);
        if (cfg.head() == Head.WATERJET) { d.box(m, 1.05, 0.7, 0.08, end - 0.05, 0.78, 1.92, WATER); }
        for (double x = 1.06; x < end - 0.05; x += 0.12) { d.box(m, x, 0.7, 0.08, x + 0.012, 0.85, 1.92, SLAT); }
        d.box(m, 1.0, 0.7, 0.0, end, 0.95, 0.08, BRIGHT);
        d.box(m, 1.0, 0.7, 1.92, end, 0.95, 2.0, BRIGHT);
        d.box(m, 1.15, 0.85, 0.15, end - 0.15, 0.865, 1.85, STEEL);
        for (Local c : cells) { if (Accessory.FUME_EXTRACTOR.kind.equals(c.kind())) { d.kit(m, Part.FUME_HOOD, 0.5, 0, 1.5, Math.PI / 2); } }
    }

    private static double gantryX(int n, double t, boolean w) { return w ? 1.5 + n * (0.5 + 0.5 * Math.sin(t * 0.35)) : n + 1.5; }

    private static double headZ(double t, boolean w) { return w ? 1.0 + 0.65 * Math.sin(t * 0.8) : 1.0; }

    private static void tableMoving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        double gx = gantryX(n, t, w), hz = headZ(t, w), hx = gx - 0.22;
        d.kit(m, Part.GANTRY_BEAM, gx, 0.95, 1.0, 0, 0, 0, new Vec3d(1, 1, 2.0 / 2.38), true);
        boolean bevel = cfg.has(Accessory.BEVEL_HEAD);
        Part head = switch (cfg.head()) { case PLASMA -> Part.PLASMA_TORCH; case WATERJET -> Part.WATERJET_HEAD; default -> Part.LASER_HEAD; };
        d.kit(m, head, hx, 1.0, hz, -Math.PI / 2, bevel ? 0.3 : 0, 0, 1, true);
        if (bevel) { d.box(m, hx - 0.05, 1.35, hz - 0.12, hx + 0.05, 1.45, hz + 0.12, DARK); }
    }

    // ---------------------------------------------------------------- threader: the base at col 1 with the head on it, a bar feeder at col 2

    private static void threaderFixed(Draw d, PartMesh m, ToolConfig cfg, List<Local> cells) {
        d.console(m, 1.0, cfg.nc());
        d.paint(m, 1.0, 0, 0.1, 2.0, 0.85, 0.9);
        switch (cfg.head()) {
            case THREAD_ROLLING -> {
                d.kit(m, Part.THREAD_ROLLING_HEAD, 1.5, 0.85, 0.5, 0);
                d.box(m, 1.2, 1.2, 0.48, 1.8, 1.22, 0.52, IRON);
            }
            case FLAT_DIE_ROLLING -> {
                d.paint(m, 1.1, 0.85, 0.15, 1.9, 0.95, 0.85);
                d.paint(m, 1.1, 0.95, 0.8, 1.9, 1.2, 0.88);
                d.kit(m, Part.FLAT_DIE, 1.5, 0.95, 0.44, Math.PI);
            }
            case TAPPING -> {
                d.kit(m, Part.TAPPING_ARM, 1.5, 0.85, 0.85, 0, 0, 0, 0.6, true);
                d.box(m, 1.3, 0.85, 0.15, 1.7, 0.98, 0.42, CAST);
            }
            case THREAD_MILLING -> {
                d.paint(m, 1.3, 1.2, 0.35, 1.7, 1.65, 0.8);
                d.kit(m, Part.MOTOR, 1.5, 1.65, 0.58, Math.PI / 2, 0, 0, 0.6, true);
                d.paint(m, 1.42, 1.1, 0.25, 1.58, 1.2, 0.4);
                d.box(m, 1.3, 0.85, 0.15, 1.7, 0.97, 0.45, CAST);
            }
            default -> { }
        }
        for (Local c : cells) {
            if (!Accessory.BAR_FEEDER.kind.equals(c.kind())) { continue; }
            d.paint(m, 2.1, 0, 0.35, 2.9, 0.75, 0.65);
            d.cyl(m, 2.0, 0.85, 0.5, 2.98, 0.85, 0.5, 0.07, STEEL);
        }
    }

    private static void threaderMoving(Draw d, PartMesh m, ToolConfig cfg, double t, boolean w) {
        switch (cfg.head()) {
            case THREAD_ROLLING -> {
                d.kit(m, Part.THREAD_ROLL, 1.5, 1.25, 0.39, 0, spin(t, w, 4), 0, 1, true);
                d.kit(m, Part.THREAD_ROLL, 1.5, 1.25, 0.61, 0, -spin(t, w, 4), 0, 1, true);
                d.cyl(m, 1.3, 1.25, 0.5, 1.7, 1.25, 0.5, 0.02, WORK);
            }
            case FLAT_DIE_ROLLING -> {
                double s = w ? 0.12 * Math.sin(t * 3) : -0.12;
                d.kit(m, Part.FLAT_DIE, 1.5 + s, 0.95, 0.56, 0);
                d.cyl(m, 1.5 + s / 2, 0.95, 0.5, 1.5 + s / 2, 1.01, 0.5, 0.01, WORK);
            }
            case TAPPING -> d.cyl(m, 1.5, 1.09, 0.28, 1.5, w ? 0.96 + 0.04 * Math.abs(Math.sin(t * 2)) : 1.02, 0.28, 0.006, STEEL);
            case THREAD_MILLING -> {
                double a = w ? t * 3 : 0, x = 1.5 + 0.015 * Math.cos(a), z = 0.3 + 0.015 * Math.sin(a);
                d.cyl(m, x, 1.1, z, x, 0.95, z, 0.008, BRIGHT);
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- riveter: the frame 1..n with the work on it, the head at col n+1

    private static void riveterFixed(Draw d, PartMesh m, ToolConfig cfg, int n) {
        double hx = n + 1.5;
        d.console(m, 1.0, cfg.nc());
        d.paint(m, 1.0, 0, 0.1, n + 1.0, 0.75, 0.9);
        d.box(m, 1.1, 0.75, 0.25, n + 1.75, 0.765, 0.75, ALU);
        d.box(m, 1.4, 0.765, 0.3, n + 1.75, 0.78, 0.7, ALU_DARK);
        for (double x = 1.5; x < n + 1.3; x += 0.1) { d.cyl(m, x, 0.78, 0.5, x, 0.787, 0.5, 0.006, ALU); }
        switch (cfg.head()) {
            case SQUEEZE_RIVETER -> {
                d.paint(m, n + 1.2, 0, 0.55, n + 1.8, 0.42, 0.98);
                d.kit(m, Part.C_FRAME_RIVETER, hx, 0.42, 0.95, 0);
            }
            case IMPACT_RIVETER -> {
                d.paint(m, n + 1.75, 0, 0.8, n + 1.9, 2.0, 0.95);
                d.paint(m, n + 1.3, 1.9, 0.25, n + 1.9, 1.98, 0.92);
                d.box(m, n + 1.2, 0.78, 0.48, n + 1.8, 1.6, 0.52, ALU);
                d.cyl(m, hx, 1.65, 0.315, hx, 1.9, 0.315, 0.004, DARK);
            }
            case DRILL_RIVETER -> {
                d.paint(m, n + 1.1, 0, 0.85, n + 1.9, 1.5, 0.98);
                d.paint(m, n + 1.3, 0.84, 0.84, n + 1.7, 1.44, 0.98);
            }
            default -> { }
        }
    }

    private static double squeeze(double t, boolean w) { return swing(w, t, 3, 0); }

    private static void riveterMoving(Draw d, PartMesh m, ToolConfig cfg, int n, double t, boolean w) {
        double hx = n + 1.5;
        switch (cfg.head()) {
            case SQUEEZE_RIVETER -> d.kit(m, Part.RIVET_RAM, hx, 1.04 - 0.15 * squeeze(t, w), 0.5, 0);
            case IMPACT_RIVETER -> d.kit(m, Part.RIVET_GUN, hx, 1.15, 0.315 + (w ? 0.006 * Math.sin(t * 90) : 0), Math.PI);
            case DRILL_RIVETER -> d.kit(m, Part.DRILL_RIVET_HEAD, hx, 0.84 + (w ? 0.02 * squeeze(t, w) : 0.02), 0.7, 0);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- what a working tool gives off

    private static final int SPARKS = 1, COOLANT = 2, CHIPS = 4, MIST = 8, SMOKE = 16, DUST = 32, FALLING_SPARKS = 64;

    /** Where a working head's tool is (pattern frame), what it gives off and which light (null: none). */
    private record Effect(double x, double y, double z, int kinds, String light) {}

    private static Effect effect(ToolConfig cfg, int n, double t) {
        double hx = n + 1.5;
        return switch (cfg.head()) {
            case CYLINDRICAL_GRINDING -> new Effect(hx, WORK_Y, 0.31, SPARKS | COOLANT, null);
            case INTERNAL_GRINDING -> new Effect(n + 1.3, 1.1, 0.5, SPARKS | COOLANT, null);
            case SURFACE_GRINDING -> new Effect(hx, 1.05, 0.35, SPARKS | COOLANT, null);
            case CENTRELESS_GRINDING -> new Effect(hx, 1.5, 0.48, SPARKS | COOLANT, null);
            case TOOL_GRINDING -> new Effect(hx, 1.33, 0.45, SPARKS, null);
            case HONING -> new Effect(1.5, 1.1, 0.5, COOLANT, null);
            case LAPPING -> new Effect(1.5, 0.95, 0.3, 0, null);
            case POLISHING -> new Effect(1.2, 1.62, 0.45, DUST, null);
            case DEBURRING -> null;
            case HOBBING, GEAR_SHAPING, SHAVING -> new Effect(1.5, 0.9, 0.5, CHIPS | COOLANT, null);
            case GEAR_GRINDING -> new Effect(1.5, 0.9, 0.5, SPARKS | COOLANT, null);
            case CHAMFERING -> new Effect(1.5, 0.96, 0.5, CHIPS, null);
            case SHAPER -> new Effect(n + 0.5, 1.25, 0.5, CHIPS, null);
            case SLOTTER -> new Effect(n + 0.6, 1.05, 0.45, CHIPS, null);
            case BROACHING -> new Effect(n + 0.9, 1.2, 0.5, CHIPS, null);
            case BAND_SAW, HACKSAW -> new Effect(hx, 0.9, 0.5, CHIPS, null);
            case COLD_SAW -> new Effect(hx, 0.9, 0.5, CHIPS | COOLANT, null);
            case SINKER_EDM -> new Effect(hx, 0.985, 0.63, 0, "edm");
            case WIRE_EDM -> new Effect(hx, 1.02, 0.52, 0, "edm");
            case HOLE_EDM -> new Effect(hx, 0.985, 0.58, 0, "edm");
            case ECM -> new Effect(hx, 1.75, 0.55, MIST, null);
            case LASER -> new Effect(gantryX(n, t, true) - 0.32, 0.866, headZ(t, true), FALLING_SPARKS, "laser");
            case PLASMA -> new Effect(gantryX(n, t, true) - 0.32, 0.866, headZ(t, true), FALLING_SPARKS | SMOKE, "plasma");
            case WATERJET -> new Effect(gantryX(n, t, true) - 0.32, 0.866, headZ(t, true), MIST, null);
            case THREAD_ROLLING -> new Effect(1.5, 1.25, 0.5, COOLANT, null);
            case TAPPING, THREAD_MILLING -> new Effect(1.5, 0.98, 0.28, CHIPS, null);
            case DRILL_RIVETER -> new Effect(hx - 0.07, 0.79, 0.5, CHIPS, null);
            default -> null;
        };
    }

    private static final int SPARK = 0xffffc070, HOT = 0xffffe0a0, CHIP = 0xffb9bec4, DROP = 0x90d8ecf8, MIST_C = 0x60f2f6fa, SMOKE_C = 0x70606060,
            DUST_C = 0x70d8cfb8;

    /** A tick's worth of what the tool gives off at a point in the world. */
    private static void particles(RandomSource r, double x, double y, double z, int kinds) {
        var level = Minecraft.getInstance().level;
        if (level == null) { return; }
        if ((kinds & SPARKS) != 0) {
            for (int i = 0, n = 3 + r.nextInt(4); i < n; i++) {
                double a = r.nextDouble() * 2 * Math.PI, v = 0.15 + 0.15 * r.nextDouble();
                level.addParticle(new HdfvParticles.Options(HdfvParticles.SPARK.get(), 0.02f + 0.015f * r.nextFloat(), r.nextBoolean() ? SPARK : HOT,
                        0.3f + 0.3f * r.nextFloat()), x, y, z, v * Math.cos(a), 0.05 + 0.1 * r.nextDouble(), v * Math.sin(a));
            }
        }
        if ((kinds & FALLING_SPARKS) != 0) {
            for (int i = 0, n = 2 + r.nextInt(3); i < n; i++) {
                level.addParticle(new HdfvParticles.Options(HdfvParticles.SPARK.get(), 0.02f, r.nextBoolean() ? SPARK : HOT, 0.4f), x, y - 0.02, z,
                        0.03 * r.nextGaussian(), -0.15 - 0.1 * r.nextDouble(), 0.03 * r.nextGaussian());
            }
        }
        if ((kinds & CHIPS) != 0 && r.nextFloat() < 0.6f) {
            level.addParticle(new HdfvParticles.Options(HdfvParticles.SPARK.get(), 0.025f, CHIP, 0.6f), x, y, z,
                    0.05 * r.nextGaussian(), 0.04, 0.05 * r.nextGaussian());
        }
        if ((kinds & COOLANT) != 0 && r.nextFloat() < 0.5f) {
            level.addParticle(new HdfvParticles.Options(HdfvParticles.SMOKE.get(), 0.06f, DROP, 0.6f), x, y, z, 0.02 * r.nextGaussian(), 0.01, 0.02 * r.nextGaussian());
        }
        if ((kinds & MIST) != 0) {
            level.addParticle(new HdfvParticles.Options(HdfvParticles.SMOKE.get(), 0.15f, MIST_C, 1.2f), x, y, z, 0.03 * r.nextGaussian(), 0.02, 0.03 * r.nextGaussian());
        }
        if ((kinds & SMOKE) != 0 && r.nextFloat() < 0.5f) {
            level.addParticle(new HdfvParticles.Options(HdfvParticles.SMOKE.get(), 0.12f, SMOKE_C, 1.5f), x, y + 0.05, z, 0, 0.03, 0);
        }
        if ((kinds & DUST) != 0 && r.nextFloat() < 0.4f) {
            level.addParticle(new HdfvParticles.Options(HdfvParticles.SMOKE.get(), 0.05f, DUST_C, 0.8f), x, y, z, 0.02 * r.nextGaussian(), -0.01, -0.03);
        }
    }
}
