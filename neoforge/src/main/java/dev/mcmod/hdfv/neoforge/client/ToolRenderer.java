package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.ToolConfig;
import dev.mcmod.hdfv.manufacture.ToolConfig.Accessory;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The manual machine tools drawn whole (0.9) over their formed blocks (which then draw nothing, the controller too):
 * the lathe's bed, headstock and chuck (turning while it works, the work between centres), its carriage running along
 * the bed and its tailstock; the mill's column, overarm and head over a table that travels; the planer's bed and
 * table running to and fro under its housing and rail; the boring mill's column with its headstock riding it; the
 * drill's column, arm and spindle feeding down. Accessories where their cells are (steady rests, a readout, a dividing
 * head, rotary tables, a power feed) or on what carries them (a tool-post grinder, a quick-change post on the carriage;
 * a side or second tool head on the planer; a facing head on the boring spindle; a multi-spindle head, a jig plate).
 * Sizes in metres in the pattern's frame (PatternFrame); the pattern is matched on the client, again every second.
 * 0.12: each machine's fixed part (bed, column, housing, what stands on them) is baked once for its kind, size and
 * accessories; what moves (chuck, carriage, tables, heads, spindles, the work) is built each frame (MachineMeshes).
 */
final class ToolRenderer {
    private ToolRenderer() {}

    private static final int GREEN = 0xff5f7f6c, DARK_GREEN = 0xff42594b, DARK = 0xff2e3135, STEEL = 0xff8c9196, BRIGHT = 0xffc9ced3, IRON = 0xff4a4f55,
            BRASS = 0xffb08d3c, HEAD = 0xffb8bec4, BLUE = 0xff3a5a8a, LCD = 0xff6fd16b, SCREEN = 0xff2b2f33, WHEEL = 0xffd8d0c0, WORK = 0xffa7adb3,
            JIG = 0xffa7b0b8;

    private static final class Seen { Pattern.Match match; long at = Long.MIN_VALUE / 2; }
    private static final Map<MachineBlockEntity, Seen> SEEN = new WeakHashMap<>();

    private static Pattern.Match match(MachineBlockEntity be) {
        Seen s = SEEN.computeIfAbsent(be, b -> new Seen());
        long now = be.getLevel().getGameTime();
        if (now - s.at >= 20 || now < s.at) {
            s.at = now;
            s.match = MachineBlockEntity.machineAt(be.getLevel(), be.getBlockPos(), be.station);
        }
        return s.match;
    }

    /** A cell's place in the pattern: column, layer, row. */
    private record Local(int col, int layer, int row, String kind) {}

    /** What a machine's fixed part is made of: its kind, facing, size, accessories and where its accessories' cells are. */
    private record Key(Station station, int facing, int size, ToolConfig config, List<Local> cells) {}

    private static final int[][] FRONT = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    /** The pattern places of a match's cells, the controller at (col, layer, row). */
    private static List<Local> locals(Pattern.Match m, net.minecraft.core.BlockPos controller, int col, int layer, int row) {
        int[] f = FRONT[Math.floorMod(m.facing(), 4)];
        int dx = -f[0], dz = -f[1], rx = -dz, rz = dx;
        List<Local> out = new ArrayList<>();
        for (Pattern.Cell c : m.cells()) {
            int wx = c.x() - controller.getX(), wz = c.z() - controller.getZ();
            int ox = wx * rx + wz * rz, oz = wx * dx + wz * dz;
            out.add(new Local(ox + col, c.y() - controller.getY() + layer, oz + row, c.kind()));
        }
        return out;
    }

    static void render(MachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (be.getLevel() == null) { return; }
        Pattern.Match m = match(be);
        if (m == null) { return; }
        ToolConfig cfg = Structures.toolConfig(be.station, m);
        double t = (be.getLevel().getGameTime() + partialTick) / 20.0;
        boolean working = be.shownWorking();
        List<Local> cells = switch (be.station) {
            case LATHE, MILLING_MACHINE -> locals(m, be.getBlockPos(), 0, 1, 0).stream()
                    .filter(c -> Accessory.STEADY_REST.kind.equals(c.kind()) || Accessory.DIGITAL_READOUT.kind.equals(c.kind())
                            || Accessory.DIVIDING_HEAD.kind.equals(c.kind()) || Accessory.MILL_ROTARY_TABLE.kind.equals(c.kind())).toList();
            default -> List.of();
        };
        int size = be.station == Station.BORING_MILL || be.station == Station.DRILL_PRESS ? m.repeats() : m.length();
        PatternFrame f = switch (be.station) {
            case PLANER -> new PatternFrame(m.facing(), 1, 1, 0);
            case LATHE, MILLING_MACHINE -> new PatternFrame(m.facing(), 0, 1, 0);
            default -> new PatternFrame(m.facing(), 0, 0, 0);
        };
        if (!ToolConfig.STATIONS.contains(be.station)) { return; }
        java.util.function.Supplier<PartMesh> movingAt = () -> {
            PartMesh moving = new PartMesh();
            switch (be.station) {
                case LATHE -> latheMoving(f, moving, cfg, size, t, working);
                case MILLING_MACHINE -> millMoving(f, moving, cells, size, t, working);
                case PLANER -> planerMoving(f, moving, size, t, working);
                case BORING_MILL -> boringMoving(f, moving, cfg, size, t, working);
                case DRILL_PRESS -> drillMoving(f, moving, cfg, size, t, working);
                default -> { }
            }
            return moving;
        };
        Key key = new Key(be.station, m.facing(), size, cfg, cells);
        java.util.function.Supplier<PartMesh> fixedMesh = () -> {
            PartMesh fixed = new PartMesh();
            switch (key.station()) {
                case LATHE -> latheFixed(f, fixed, cfg, cells, size);
                case MILLING_MACHINE -> millFixed(f, fixed, cells, size);
                case PLANER -> planerFixed(f, fixed, cfg, size);
                case BORING_MILL -> boringFixed(f, fixed, cfg, size);
                case DRILL_PRESS -> drillFixed(f, fixed, cfg, size);
                default -> { }
            }
            return fixed;
        };
        // 0.12: standing still while it does not work: its moving parts built and baked once
        if (!working) { MachineMeshes.drawResting(key, fixedMesh, java.util.List.of(key, "rest"), movingAt, be.getBlockPos(), pose, buffers, light); }
        else { MachineMeshes.draw(key, fixedMesh, movingAt.get(), be.getBlockPos(), pose, buffers, light); }
    }

    private static double swing(boolean working, double t, double rate, double rest) { return working ? 0.5 + 0.5 * Math.sin(t * rate) : rest; }

    private static void readout(PatternFrame f, PartMesh m, double x0, double y0, double z, double x1, double y1) {
        f.box(m, x0, y0, z - 0.06, x1, y1, z, SCREEN);
        for (int k = 0; k < 3; k++) {
            double y = y1 - 0.07 - 0.07 * k;
            f.box(m, x0 + 0.05, y - 0.02, z - 0.07, x0 + 0.05 + (x1 - x0 - 0.1) * (k == 2 ? 0.6 : 0.9), y, z - 0.06, LCD);
        }
    }

    // ---------------------------------------------------------------- lathe

    private static final double LATHE_AXIS = 1.42, LATHE_MID = 0.5;

    private static void latheFixed(PatternFrame f, PartMesh m, ToolConfig cfg, List<Local> cells, int n) {
        double axis = LATHE_AXIS, mid = LATHE_MID, end = n + 2.0;
        // bed on its cabinet legs, its ways, the leadscrew in front
        f.box(m, 1.0, 0, 0.15, end, 0.7, 0.85, GREEN);
        f.box(m, end - 0.9, 0, 0.1, end - 0.1, 0.7, 0.9, DARK_GREEN);
        f.box(m, 1.0, 0.7, 0.2, end, 0.8, 0.33, BRIGHT);
        f.box(m, 1.0, 0.7, 0.67, end, 0.8, 0.8, BRIGHT);
        f.cylinder(m, 1.0, 0.6, 0.08, end - 0.05, 0.6, 0.08, 0.02, STEEL);
        // headstock with its gear cover and levers
        f.box(m, 0.05, 1.0, 0.1, 0.95, 1.85, 0.9, GREEN);
        f.box(m, 0.1, 1.85, 0.15, 0.9, 1.95, 0.85, DARK_GREEN);
        f.box(m, 0.3, 1.5, 0.04, 0.34, 1.75, 0.1, DARK);
        f.box(m, 0.55, 1.45, 0.04, 0.59, 1.7, 0.1, DARK);
        // tailstock and its quill over the bed's end
        if (cfg.has(Accessory.TAILSTOCK)) {
            double tailX = n + 1.3;
            f.box(m, tailX, 0.8, 0.2, tailX + 0.55, 1.3, 0.8, GREEN);
            f.box(m, tailX + 0.05, 1.3, 0.3, tailX + 0.5, axis + 0.12, 0.7, GREEN);
            f.cylinder(m, tailX, axis, mid, tailX - 0.15, axis, mid, 0.05, STEEL);
            f.cylinder(m, tailX + 0.55, axis, mid, tailX + 0.62, axis, mid, 0.14, DARK);
        }
        // steady rests round the work and the readout where their cells are
        for (Local c : cells) {
            if (Accessory.STEADY_REST.kind.equals(c.kind())) {
                double x = c.col() + 0.5;
                f.box(m, x - 0.05, 0.8, 0.22, x + 0.05, axis + 0.25, 0.3, GREEN);
                f.box(m, x - 0.05, 0.8, 0.7, x + 0.05, axis + 0.25, 0.78, GREEN);
                f.box(m, x - 0.05, axis + 0.17, 0.22, x + 0.05, axis + 0.25, 0.78, GREEN);
                f.box(m, x - 0.03, axis - 0.03, 0.3, x + 0.03, axis + 0.03, mid - 0.07, BRASS);
                f.box(m, x - 0.03, axis + 0.07, mid - 0.03, x + 0.03, axis + 0.17, mid + 0.03, BRASS);
            } else if (Accessory.DIGITAL_READOUT.kind.equals(c.kind())) {
                readout(f, m, c.col() + 0.2, 1.3, 0.0, c.col() + 0.8, 1.65);
                f.box(m, c.col() + 0.45, 0.8, 0.02, c.col() + 0.55, 1.3, 0.08, DARK);
            }
        }
    }

    private static void latheMoving(PatternFrame f, PartMesh m, ToolConfig cfg, int n, double t, boolean working) {
        double axis = LATHE_AXIS, mid = LATHE_MID;
        // chuck: three jaws turning with the spindle
        double spin = working ? t * 12 : 0.4;
        f.cylinder(m, 0.95, axis, mid, 1.13, axis, mid, 0.21, STEEL);
        for (int k = 0; k < 3; k++) {
            double q = spin + k * 2 * Math.PI / 3, jy = axis + 0.14 * Math.cos(q), jz = mid + 0.14 * Math.sin(q);
            f.box(m, 1.13, jy - 0.035, jz - 0.035, 1.21, jy + 0.035, jz + 0.035, BRIGHT);
        }
        boolean tail = cfg.has(Accessory.TAILSTOCK);
        double tailX = n + 1.3;
        if (working) { f.cylinder(m, 1.21, axis, mid, tail ? tailX - 0.15 : Math.min(1.21 + 0.7, tailX - 0.2), axis, mid, 0.07, WORK); }
        // carriage running along the bed: saddle, apron, cross slide and tool post (or the quick-change post), the grinder on it
        double cx = 1.25 + (n - 0.25) * swing(working, t, 0.25, 0.15);
        f.box(m, cx, 0.8, 0.1, cx + 0.5, 1.0, 0.9, GREEN);
        f.box(m, cx, 0.45, 0.0, cx + 0.5, 0.95, 0.12, DARK_GREEN);
        f.cylinder(m, cx + 0.1, 0.75, -0.04, cx + 0.1, 0.75, 0.0, 0.08, DARK);
        f.box(m, cx + 0.12, 1.0, 0.15, cx + 0.38, 1.1, 0.62, IRON);
        if (cfg.has(Accessory.QUICK_CHANGE_POST)) {
            f.box(m, cx + 0.15, 1.1, 0.16, cx + 0.35, 1.38, 0.36, STEEL);
            f.box(m, cx + 0.2, 1.38, 0.2, cx + 0.3, 1.42, 0.45, DARK);
        } else {
            f.box(m, cx + 0.18, 1.1, 0.2, cx + 0.32, 1.32, 0.34, IRON);
        }
        f.box(m, cx + 0.22, axis - 0.03, 0.34, cx + 0.27, axis + 0.01, mid - 0.07, BRIGHT);   // the tool, to the work
        if (cfg.has(Accessory.TOOLPOST_GRINDER)) {
            f.cylinder(m, cx + 0.15, axis, 0.78, cx + 0.23, axis, 0.78, 0.13, WHEEL);
            f.box(m, cx + 0.23, axis - 0.08, 0.7, cx + 0.45, axis + 0.08, 0.86, BLUE);
        }
    }

    // ---------------------------------------------------------------- milling machine

    private static void millFixed(PatternFrame f, PartMesh m, List<Local> cells, int n) {
        // column over the drive, the overarm reaching over the table, the head
        f.box(m, 0.1, 1.0, 0.15, 0.9, 2.95, 0.85, GREEN);
        f.box(m, 0.05, 2.35, 0.2, 1.75, 2.9, 0.8, GREEN);
        f.box(m, 1.15, 1.95, 0.25, 1.7, 2.35, 0.75, HEAD);
        // knee and saddle on the base
        f.box(m, 1.0, 0, 0.2, n + 1.0, 0.75, 0.8, GREEN);
        f.box(m, 1.0, 0.75, 0.15, n + 1.0, 0.85, 0.85, DARK_GREEN);
        for (Local c : cells) {
            if (Accessory.DIGITAL_READOUT.kind.equals(c.kind())) { readout(f, m, c.col() + 0.2, 0.3, 0.2, c.col() + 0.8, 0.65); }
        }
    }

    private static void millMoving(PatternFrame f, PartMesh m, List<Local> cells, int n, double t, boolean working) {
        // the spindle feeding, its cutter turning
        double feed = working ? 0.08 * (0.5 + 0.5 * Math.sin(t * 1.7)) : 0, x = 1.42, spin = working ? t * 14 : 0;
        f.cylinder(m, x, 1.95, 0.5, x, 1.68 - feed, 0.5, 0.06, STEEL);
        f.cylinder(m, x, 1.68 - feed, 0.5, x, 1.55 - feed, 0.5, 0.07, BRIGHT);
        for (int k = 0; k < 2; k++) {
            double q = spin + k * Math.PI;
            f.box(m, x + 0.075 * Math.cos(q) - 0.015, 1.56 - feed, 0.5 + 0.075 * Math.sin(q) - 0.015, x + 0.075 * Math.cos(q) + 0.015, 1.67 - feed,
                    0.5 + 0.075 * Math.sin(q) + 0.015, DARK);
        }
        // the table travelling under the cutter with what rides on it
        double dx = working ? 0.35 * Math.sin(t * 0.4) : 0;
        f.box(m, 0.95 + dx, 0.85, 0.1, n + 1.05 + dx, 1.0, 0.9, IRON);
        for (double z : new double[]{0.3, 0.5, 0.7}) { f.box(m, 0.95 + dx, 1.0, z - 0.02, n + 1.05 + dx, 1.005, z + 0.02, DARK); }
        if (working) { f.box(m, x - 0.2, 1.0, 0.35, x + 0.2, 1.22, 0.65, WORK); }
        for (Local c : cells) {
            double cxm = c.col() + 0.5 + dx;
            if (Accessory.DIVIDING_HEAD.kind.equals(c.kind())) {
                f.box(m, cxm - 0.3, 1.0, 0.25, cxm + 0.05, 1.12, 0.75, STEEL);
                f.cylinder(m, cxm - 0.25, 1.3, 0.5, cxm + 0.1, 1.3, 0.5, 0.2, STEEL);
                f.cylinder(m, cxm - 0.32, 1.3, 0.5, cxm - 0.25, 1.3, 0.5, 0.12, DARK);
            } else if (Accessory.MILL_ROTARY_TABLE.kind.equals(c.kind())) {
                double q = working ? t * 0.6 : 0;
                f.cylinder(m, cxm, 1.0, 0.5, cxm, 1.14, 0.5, 0.36, IRON);
                for (int k = 0; k < 4; k++) {
                    double a = q + k * Math.PI / 2;
                    f.box(m, cxm + 0.25 * Math.cos(a) - 0.03, 1.14, 0.5 + 0.25 * Math.sin(a) - 0.03, cxm + 0.25 * Math.cos(a) + 0.03, 1.145,
                            0.5 + 0.25 * Math.sin(a) + 0.03, DARK);
                }
            }
        }
    }

    // ---------------------------------------------------------------- planer

    private static void planerFixed(PatternFrame f, PartMesh m, ToolConfig cfg, int n) {
        double end = n + 2.0;
        // bed and ways down the middle row
        f.box(m, 1.0, 0, 1.1, end, 0.55, 1.9, GREEN);
        f.box(m, 1.0, 0.55, 1.2, end, 0.6, 1.3, BRIGHT);
        f.box(m, 1.0, 0.55, 1.7, end, 0.6, 1.8, BRIGHT);
        // the housing: two uprights, the rail across them, the tool head(s), the controls on the front upright
        f.box(m, 1.1, 0, 0.15, 1.9, 2.1, 0.9, GREEN);
        f.box(m, 1.1, 0, 2.1, 1.9, 2.1, 2.85, GREEN);
        f.box(m, 1.15, 2.1, 0.15, 1.85, 2.55, 2.85, GREEN);
        f.box(m, 1.2, 1.25, 1.05, 1.8, 1.45, 1.95, DARK_GREEN);   // the rail's saddle, lowered to the work
        f.box(m, 1.3, 1.45, 1.3, 1.7, 2.1, 1.7, HEAD);
        f.box(m, 1.45, 1.02, 1.45, 1.55, 1.25, 1.55, BRIGHT);
        if (cfg.has(Accessory.SECOND_HEAD)) {
            f.box(m, 1.3, 1.45, 1.75, 1.7, 2.1, 1.95, HEAD);
            f.box(m, 1.45, 1.02, 1.8, 1.55, 1.25, 1.9, BRIGHT);
        }
        if (cfg.has(Accessory.SIDE_HEAD)) {
            f.box(m, 1.3, 0.8, 1.95, 1.7, 1.2, 2.1, HEAD);
            f.box(m, 1.45, 0.95, 1.85, 1.55, 1.05, 1.95, BRIGHT);
        }
        f.box(m, 1.25, 1.0, -0.05, 1.75, 1.45, 0.15, DARK);
        f.box(m, 1.32, 1.3, -0.06, 1.68, 1.38, -0.05, LCD);
    }

    private static void planerMoving(PatternFrame f, PartMesh m, int n, double t, boolean working) {
        // the table running to and fro with its work
        double len = 0.6 * (n + 1), span = (n + 1) - len;
        double dx = span * swing(working, t, 0.6, 0.3);
        f.box(m, 1.05 + dx, 0.6, 1.05, 1.05 + dx + len, 0.78, 1.95, IRON);
        for (double z : new double[]{1.25, 1.5, 1.75}) { f.box(m, 1.05 + dx, 0.78, z - 0.02, 1.05 + dx + len, 0.785, z + 0.02, DARK); }
        if (working) { f.box(m, 1.25 + dx, 0.78, 1.3, 1.25 + dx + 0.6 * len, 1.0, 1.7, WORK); }
    }

    // ---------------------------------------------------------------- boring mill

    private static void boringFixed(PatternFrame f, PartMesh m, ToolConfig cfg, int n) {
        // the saddle, the column a course at a time, the gearbox on top
        f.box(m, 0.05, 0, 0.05, 0.95, 0.9, 0.95, GREEN);
        f.box(m, 0.15, 0.9, 0.15, 0.85, n + 1.0, 0.85, GREEN);
        f.box(m, 0.85, 0.9, 0.3, 0.9, n + 1.0, 0.4, BRIGHT);
        f.box(m, 0.05, n + 1.0, 0.05, 0.95, n + 1.8, 0.95, DARK_GREEN);
        // the table (or a rotary table's base)
        f.box(m, 1.02, 0, 0.05, 1.98, 0.7, 0.95, GREEN);
        if (!cfg.has(Accessory.BORING_ROTARY_TABLE)) { f.box(m, 1.0, 0.7, 0.02, 2.0, 0.84, 0.98, IRON); }
        if (cfg.has(Accessory.DIGITAL_READOUT)) { readout(f, m, 0.2, 1.4, 1.06, 0.8, 1.75); }
    }

    private static void boringMoving(PatternFrame f, PartMesh m, ToolConfig cfg, int n, double t, boolean working) {
        // the headstock riding the column, its spindle feeding (a facing head on it)
        double hy = 1.0 + (n - 0.3) * swing(working, t, 0.2, 0.25), feed = working ? 0.12 * (0.5 + 0.5 * Math.sin(t * 0.9)) : 0, spin = working ? t * 9 : 0;
        f.box(m, 0.05, hy, 0.1, 0.95, hy + 0.6, 0.9, HEAD);
        double sy = hy + 0.3;
        f.cylinder(m, 0.95, sy, 0.5, 1.15 + feed, sy, 0.5, 0.07, STEEL);
        if (cfg.has(Accessory.FACING_HEAD)) {
            f.cylinder(m, 1.1 + feed, sy, 0.5, 1.2 + feed, sy, 0.5, 0.26, HEAD);
            double q = spin;
            f.box(m, 1.2 + feed, sy + 0.2 * Math.cos(q) - 0.03, 0.5 + 0.2 * Math.sin(q) - 0.03, 1.27 + feed, sy + 0.2 * Math.cos(q) + 0.03,
                    0.5 + 0.2 * Math.sin(q) + 0.03, BRIGHT);
        } else {
            f.box(m, 1.15 + feed, sy - 0.015, 0.47, 1.22 + feed, sy + 0.015, 0.53, BRIGHT);
        }
        if (cfg.has(Accessory.BORING_ROTARY_TABLE)) {
            double q = working ? t * 0.3 : 0;
            f.cylinder(m, 1.5, 0.7, 0.5, 1.5, 0.84, 0.5, 0.44, IRON);
            for (int k = 0; k < 4; k++) {
                double a = q + k * Math.PI / 2;
                f.box(m, 1.5 + 0.3 * Math.cos(a) - 0.03, 0.84, 0.5 + 0.3 * Math.sin(a) - 0.03, 1.5 + 0.3 * Math.cos(a) + 0.03, 0.845, 0.5 + 0.3 * Math.sin(a) + 0.03, DARK);
            }
        }
        if (working) { f.box(m, 1.25, 0.84, 0.25, 1.85, 0.84 + Math.min(0.8, 0.5 * n), 0.75, WORK); }
    }

    // ---------------------------------------------------------------- drill press

    private static void drillFixed(PatternFrame f, PartMesh m, ToolConfig cfg, int n) {
        double top = n + 1.0;
        // base, column, the arm with its head and motor reaching over the table
        f.box(m, 0.05, 0, 0.05, 0.95, 0.3, 0.95, GREEN);
        f.cylinder(m, 0.5, 0.3, 0.5, 0.5, top + 0.6, 0.5, 0.14, STEEL);
        f.box(m, 0.15, top, 0.2, 1.85, top + 0.55, 0.8, GREEN);
        f.box(m, 0.2, top + 0.55, 0.25, 0.7, top + 0.85, 0.75, BLUE);
        if (cfg.has(Accessory.POWER_FEED)) { f.box(m, 0.3, top + 0.1, 0.8, 0.7, top + 0.45, 0.95, BLUE); }
        // the table (or a jig plate) on its pedestal
        f.box(m, 1.1, 0, 0.15, 1.9, 0.75, 0.85, GREEN);
        boolean jig = cfg.has(Accessory.JIG_PLATE);
        f.box(m, 1.0, 0.75, 0.05, 2.0, 0.85, 0.95, jig ? JIG : IRON);
        if (jig) { for (double x : new double[]{1.25, 1.5, 1.75}) { for (double z : new double[]{0.25, 0.5, 0.75}) { f.cylinder(m, x, 0.85, z, x, 0.855, z, 0.03, DARK); } } }
        if (cfg.has(Accessory.MULTI_SPINDLE)) { f.box(m, 1.3, top - 0.12, 0.18, 1.7, top, 0.82, HEAD); }
    }

    private static void drillMoving(PatternFrame f, PartMesh m, ToolConfig cfg, int n, double t, boolean working) {
        double top = n + 1.0;
        if (working) { f.box(m, 1.25, 0.85, 0.25, 1.75, 1.05, 0.75, WORK); }
        // the spindle (or three) feeding down
        double feed = working ? 0.25 * (0.5 + 0.5 * Math.sin(t * 1.2)) : 0;
        double[] zs = cfg.has(Accessory.MULTI_SPINDLE) ? new double[]{0.28, 0.5, 0.72} : new double[]{0.5};
        for (double z : zs) {
            f.cylinder(m, 1.5, top, z, 1.5, top - 0.4 - feed, z, 0.05, STEEL);
            f.cylinder(m, 1.5, top - 0.4 - feed, z, 1.5, top - 0.62 - feed, z, 0.015, BRIGHT);
        }
    }
}
