package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.neoforge.multiblock.Multiblocks;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * The CNC drawn whole (0.8) over its formed members (which then draw nothing): bed, column and frame, guards with
 * windows, the milling head travelling in X, Y and Z over the work while it works, and the modules it has: a rotary
 * table turning, a trunnion tilting, a turning spindle and sub-spindle spinning, turrets indexing. The modules are read
 * from the blocks (the pattern matched on the client, looked at again every second). 0.12: what stands still is baked
 * once for its modules and kept on the GPU, what moves is built each frame (MachineMeshes).
 */
public final class CncRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    private static final int BED = 0xff4a4f55, FRAME = 0xff9aa3ab, GUARD = 0xffd8dcdf, STRIPE = 0xff2f6fb5, STEEL = 0xff8c9196, DARK = 0xff2e3135,
            BRIGHT = 0xffc9ced3, GLASS = 0x5578a0c0, CHUCK = 0xff6c7a86, BRASS = 0xffb08d3c, HEAD = 0xffb8bec4;

    public CncRenderer(BlockEntityRendererProvider.Context c) {}

    /** What the renderer last saw of a machine's blocks. */
    private static final class Seen { Pattern.Match match; long at = Long.MIN_VALUE / 2; }   // not MIN_VALUE: now - at would overflow
    private static final java.util.Map<MachineBlockEntity, Seen> SEEN = new java.util.WeakHashMap<>();

    private static Pattern.Match match(MachineBlockEntity be) {
        Seen s = SEEN.computeIfAbsent(be, b -> new Seen());
        long now = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        if (now - s.at >= 20 || now < s.at) {
            s.at = now;
            var pos = be.getBlockPos();
            int facing = be.getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                    ? be.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).get2DDataValue() : 0;
            s.match = Structures.CNC.match(Multiblocks.world(be.getLevel()), pos.getX(), pos.getY(), pos.getZ(), facing);
        }
        return s.match;
    }

    @Override public void render(MachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.station.family) { FamilyRenderer.render(be, partialTick, pose, buffers, light); return; }   // 0.17
        if (dev.mcmod.hdfv.manufacture.ToolConfig.STATIONS.contains(be.station)) { ToolRenderer.render(be, partialTick, pose, buffers, light); return; }   // 0.9
        if (MachineEffects.lit(be.station)) { MachineEffects.machine(be, partialTick, pose, buffers); return; }   // 0.12
        if (be.station != Station.CNC || be.getLevel() == null) { return; }
        Pattern.Match m = match(be);
        if (m == null) { return; }
        double t = (be.getLevel().getGameTime() + partialTick) / 20.0;
        boolean working = be.shownWorking();
        Frame f = new Frame(m.facing());
        // 0.12: the frame, guards and fixtures baked once for its modules; the head, chucks, tables and turrets each frame
        StringBuilder modules = new StringBuilder("cnc:").append(m.facing()).append(':');
        for (String k : new String[]{Structures.B_AXIS_HEAD, Structures.TURNING_SPINDLE, Structures.TRUNNION, Structures.ROTARY_TABLE, Structures.SUB_SPINDLE}) {
            modules.append(m.has(k) ? '1' : '0');
        }
        String key = modules.toString();
        // 0.12: standing still while it does not work: its head, chucks and tables built and baked once
        if (!working) {
            MachineMeshes.drawResting(key, () -> fixed(f, m), key + ":rest", () -> { PartMesh still = new PartMesh(); moving(f, still, m, 0, false); return still; },
                    be.getBlockPos(), pose, buffers, light);
            return;
        }
        PartMesh moving = new PartMesh();
        moving(f, moving, m, t, working);
        MachineMeshes.draw(key, () -> fixed(f, m), moving, be.getBlockPos(), pose, buffers, light);
    }

    /** What does not move: bed, column and frame, guards and windows, the fixtures for its modules. */
    private static PartMesh fixed(Frame f, Pattern.Match m) {
        PartMesh solid = new PartMesh();
        // bed (all but the cabinet's cell), column and frame at the back
        f.box(solid, 0, 0, 0.9, 4, 0.85, 3, BED);
        f.box(solid, 0, 0, 0.1, 3, 0.85, 0.9, BED);
        for (double x = 0.3; x < 3.8; x += 0.9) { f.box(solid, x, 0.85, 1.05, x + 0.08, 0.9, 1.95, DARK); }   // T-slots
        f.box(solid, 0.05, 0, 2.15, 3.95, 3, 2.95, FRAME);
        f.box(solid, 0.15, 2.7, 0.15, 3.85, 3, 2.15, FRAME);                                              // top rails
        f.box(solid, 0.05, 1.0, 2.1, 3.95, 1.08, 2.15, STRIPE);
        // guards: posts, sills, the front windows and the stripe
        for (double x : new double[]{0, 1.45, 2.9}) { f.box(solid, x, 0.85, 0, x + 0.1, 3, 0.12, GUARD); }
        f.box(solid, 0, 0.85, 0, 3.0, 1.15, 0.12, GUARD);
        f.box(solid, 0, 2.75, 0, 3.0, 3.0, 0.12, STRIPE);
        solid.clear(true).finish(dev.mcmod.hdfv.look.Finishes.GLASS);   // the windows: glass, not in the way of the shading
        f.box(solid, 0.1, 1.15, 0.03, 2.9, 2.75, 0.07, GLASS);
        f.box(solid, 0.0, 1.15, 0.2, 0.04, 2.6, 2.0, GLASS);                                              // side window
        solid.clear(false).finish(null);
        f.box(solid, 3.0, 1.0, 0, 4, 3, 0.12, GUARD);                                                    // above the cabinet
        f.box(solid, 0, 0.85, 0.12, 0.06, 3, 2.15, GUARD);
        f.box(solid, 3.94, 0.85, 0.12, 4, 3, 2.15, GUARD);
        // work holding that stands still: the headstock and its bar, a trunnion's cheeks, a vice and its work
        if (m.has(Structures.TURNING_SPINDLE)) {
            f.box(solid, 0.1, 0.85, 1.2, 0.55, 1.6, 1.8, CHUCK);                                          // headstock
            f.cylinder(solid, 0.85, 1.25, 1.5, 2.2, 1.25, 1.5, 0.07, BRIGHT);                              // the bar being turned
        } else if (m.has(Structures.TRUNNION) || m.has(Structures.ROTARY_TABLE)) {
            if (m.has(Structures.TRUNNION)) {
                f.box(solid, 1.05, 0.85, 1.3, 1.2, 1.35, 1.7, FRAME);
                f.box(solid, 2.6, 0.85, 1.3, 2.75, 1.35, 1.7, FRAME);
                f.cylinder(solid, 1.2, 1.2, 1.5, 2.6, 1.2, 1.5, 0.05, DARK);
            }
        } else {
            f.box(solid, 1.2, 0.9, 1.25, 2.6, 1.1, 1.75, BRIGHT);                                         // a vice and the workpiece
            f.box(solid, 1.15, 0.85, 1.2, 1.25, 1.2, 1.8, DARK);
        }
        return solid;
    }

    /** What moves: the milling head travelling in X, Y and Z and its spindle; chucks, the rotary table, turrets. */
    private static void moving(Frame f, PartMesh solid, Pattern.Match m, double t, boolean working) {
        double x = 1.9 + (working ? 0.65 * Math.sin(t * 0.7) : 0), z = 1.5 + (working ? 0.25 * Math.sin(t * 1.1 + 1) : 0);
        double y = 1.75 + (working ? 0.18 * (0.5 + 0.5 * Math.sin(t * 2.3)) : 0.25);
        boolean bHead = m.has(Structures.B_AXIS_HEAD);
        f.box(solid, x - 0.18, 2.45, z - 0.2, x + 0.18, 2.7, 2.15, HEAD);                                // ram
        f.box(solid, x - 0.2, y + 0.25, z - 0.2, x + 0.2, 2.45, z + 0.2, HEAD);                         // head
        if (bHead) { f.cylinder(solid, x - 0.24, y + 0.45, z, x + 0.24, y + 0.45, z, 0.12, STRIPE); }
        double tilt = bHead && working ? 0.35 * Math.sin(t * 0.5) : 0;
        f.cylinder(solid, x, y + 0.25, z, x + Math.sin(tilt) * 0.2, y + 0.25 - Math.cos(tilt) * 0.2, z, 0.08, STEEL);    // spindle nose
        double tx = x + Math.sin(tilt) * 0.2, ty = y + 0.05;
        f.cylinder(solid, tx, ty, z, tx + Math.sin(tilt) * 0.12, ty - Math.cos(tilt) * 0.12, z, 0.025, BRIGHT);            // the tool
        double spin = working ? t * 40 : 0;
        f.cylinder(solid, x - 0.09 * Math.cos(spin), y + 0.2, z - 0.09 * Math.sin(spin), x + 0.09 * Math.cos(spin), y + 0.2, z + 0.09 * Math.sin(spin), 0.015, DARK);
        if (m.has(Structures.TURNING_SPINDLE)) {
            chuck(f, solid, 0.55, 1.25, 1.5, 1, working ? t * 25 : 0, 0.32);
        } else if (m.has(Structures.TRUNNION) || m.has(Structures.ROTARY_TABLE)) {
            boolean trunnion = m.has(Structures.TRUNNION);
            double a = trunnion && working ? 0.4 * Math.sin(t * 0.4) : 0, turn = working ? t * 0.6 : 0;
            double cy = trunnion ? 1.2 : 0.95;
            Vec3d axis = new Vec3d(0, Math.cos(a), Math.sin(a));
            f.cylinder(solid, 1.9, cy, 1.5, 1.9 + axis.x() * 0.08, cy + axis.y() * 0.08, 1.5 + axis.z() * 0.08, 0.42, STEEL);
            for (int k = 0; k < 4; k++) {                                                                // radial slots show it turning
                double q = turn + k * Math.PI / 2;
                f.box(solid, 1.9 + 0.05 * Math.cos(q) - 0.02, cy + 0.08, 1.5 + 0.05 * Math.sin(q) - 0.02,
                        1.9 + 0.35 * Math.cos(q) + 0.02, cy + 0.085, 1.5 + 0.35 * Math.sin(q) + 0.02, DARK);
            }
            f.box(solid, 1.75, cy + 0.08, 1.35, 2.05, cy + 0.3, 1.65, BRIGHT);                            // the workpiece
        }
        if (m.has(Structures.SUB_SPINDLE)) { chuck(f, solid, 0.45, 1.6, 1.5, 1, working ? -t * 25 : 0, 0.24); }
        int turrets = 0;
        for (Pattern.Cell c : m.cells()) {
            boolean turret = Structures.TURRET.equals(c.kind()) || Structures.Y_TURRET.equals(c.kind());
            if (!turret) { continue; }
            double index = working ? Math.floor(t / 2 + turrets) * Math.PI / 4 : 0;
            double cx = turrets == 0 ? 2.5 : 3.45, cy = turrets == 0 ? 1.05 : 1.9;
            turret(f, solid, cx, cy, 1.5, index, Structures.Y_TURRET.equals(c.kind()));
            turrets++;
        }
    }

    private static void chuck(Frame f, PartMesh m, double x, double y, double z, int dir, double angle, double r) {
        f.cylinder(m, x, y, z, x + dir * 0.18, y, z, r, CHUCK);
        for (int k = 0; k < 3; k++) {
            double q = angle + k * 2 * Math.PI / 3;
            double jy = y + 0.6 * r * Math.cos(q), jz = z + 0.6 * r * Math.sin(q);
            f.box(m, x + dir * 0.18, jy - 0.04, jz - 0.04, x + dir * 0.26, jy + 0.04, jz + 0.04, BRIGHT);
        }
    }

    private static void turret(Frame f, PartMesh m, double x, double y, double z, double angle, boolean yAxis) {
        f.cylinder(m, x, y, z, x - 0.15, y, z, 0.3, yAxis ? STRIPE : STEEL);
        for (int k = 0; k < 8; k++) {
            double q = angle + k * Math.PI / 4, ty = y + 0.27 * Math.cos(q), tz = z + 0.27 * Math.sin(q);
            f.box(m, x - 0.22, ty - 0.03, tz - 0.03, x - 0.15, ty + 0.03, tz + 0.03, BRASS);
        }
    }

    /** The pattern's frame (x right, y up, z back from the front; metres from its front left bottom corner) to the cabinet's block. */
    private static final class Frame {
        final double rx, rz, dx, dz;
        Frame(int facing) {
            int[][] front = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
            int[] fr = front[Math.floorMod(facing, 4)];
            dx = -fr[0]; dz = -fr[1];
            rx = -dz; rz = dx;
        }
        Vec3d at(double x, double y, double z) {
            double ox = x - 3.5, oz = z - 0.5;
            return new Vec3d(0.5 + ox * rx + oz * dx, y, 0.5 + ox * rz + oz * dz);
        }
        void box(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1, int colour) {
            Vec3d a = at(x0, y0, z0), b = at(x1, y1, z1);
            m.box(new Vec3d(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                    new Vec3d(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())), colour);
        }
        void cylinder(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1, double r, int colour) {
            m.cylinder(at(x0, y0, z0), at(x1, y1, z1), r, r, 16, colour);
        }
    }

    private static boolean whole(MachineBlockEntity be) { return be.station == Station.CNC || dev.mcmod.hdfv.manufacture.ToolConfig.STATIONS.contains(be.station); }

    @Override public boolean shouldRenderOffScreen(MachineBlockEntity be) { return whole(be); }

    @Override public AABB getRenderBoundingBox(MachineBlockEntity be) {
        return whole(be) ? new AABB(be.getBlockPos()).inflate(be.station == Station.CNC ? 4.5 : 9.5) : new AABB(be.getBlockPos());
    }
}
