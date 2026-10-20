package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.assembly.BuildSequence;
import dev.mcmod.hdfv.assembly.CraneRunway;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.assembly.RoomControllerBlock;
import dev.mcmod.hdfv.neoforge.assembly.RoomControllerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * An assembly room's controller drawn (0.8): its console's screen showing the build's progress, and the room's
 * overhead crane, if it has one: the bridge girder across the room on its end trucks, the trolley, the rope and the
 * hook, following the part being hoisted (BuildSequence, CraneRunway, as the vehicle's renderer swings it), parked at
 * the end of its runway otherwise.
 */
public final class RoomControllerRenderer implements BlockEntityRenderer<RoomControllerBlockEntity> {
    private static final int GIRDER = 0xffd9a521, TRUCK = 0xff3a3d40, ROPE = 0xff2a2c30, HOOK = 0xffd9a521, SCREEN = 0xff10161c, BAR = 0xff3fbf5f,
            BAR_BACK = 0xff2a3a2a;

    public RoomControllerRenderer(BlockEntityRendererProvider.Context c) {}

    @Override public void render(RoomControllerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        PartMesh m = new PartMesh();
        screen(be, m);
        int[] box = be.shownBox();
        if (be.shownCrane() && box.length == 6 && be.getLevel() != null) { crane(be, box, m, partialTick); }
        m.emit(buffers.getBuffer(RenderType.entityCutoutNoCull(PartMesh.TEXTURE)), pose, light, false);
    }

    /** The console's screen: a bar of the build's progress on the face toward the room's front. */
    private static void screen(RoomControllerBlockEntity be, PartMesh m) {
        Direction f = be.getBlockState().getValue(RoomControllerBlock.FACING);
        float built = be.shownBuilt();
        // the screen 2 mm proud of the block's front face, 12 by 6 pixels, 4 pixels from the top
        double out = 0.5 + 0.502;
        Vec3d n = new Vec3d(f.getStepX(), 0, f.getStepZ()), across = new Vec3d(-f.getStepZ(), 0, f.getStepX());
        Vec3d c = new Vec3d(0.5, 0.62, 0.5).add(n.scale(out - 0.5));
        quad(m, c, across, 0.38, 0.2, SCREEN, n);
        if (be.shownState() > 0) {
            Vec3d left = c.add(across.scale(-0.32)).add(n.scale(0.001));
            quad(m, c.add(n.scale(0.001)), across, 0.32, 0.05, BAR_BACK, n);
            double w = 0.64 * built;
            quad(m, left.add(across.scale(w / 2)).add(n.scale(0.001)), across, w / 2, 0.05, be.shownState() == 2 ? 0xff5fa8ff : BAR, n);
        }
    }

    private static void quad(PartMesh m, Vec3d c, Vec3d across, double halfW, double halfH, int colour, Vec3d normal) {
        Vec3d u = across.scale(halfW), v = new Vec3d(0, halfH, 0);
        m.quad(c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v), colour, normal);
    }

    /** The crane: over the room's box, its runway along the longer side; the hook over the hoisted part or parked. */
    private static void crane(RoomControllerBlockEntity be, int[] box, PartMesh m, float partialTick) {
        double minX = box[0], minY = box[1], minZ = box[2], maxX = box[3] + 1, maxY = box[4] + 1, maxZ = box[5] + 1;
        boolean alongX = maxX - minX >= maxZ - minZ;
        double rail = maxY - 0.15, girderY = rail - 0.45;
        Vec3d hook = null;
        for (HdfvVehicle v : be.getLevel().getEntitiesOfClass(HdfvVehicle.class, new AABB(minX, minY, minZ, maxX, maxY, maxZ))) {
            hook = hookOf(v, partialTick);
            if (hook != null) { break; }
        }
        Vec3d base = new Vec3d(be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ());
        double along = hook != null ? (alongX ? hook.x() : hook.z()) : (alongX ? minX + 1 : minZ + 1);
        double across = hook != null ? (alongX ? hook.z() : hook.x()) : (alongX ? 0.5 * (minZ + maxZ) : 0.5 * (minX + maxX));
        double a0 = alongX ? minZ : minX, a1 = alongX ? maxZ : maxX;
        // the girder and the end trucks
        box(m, base, alongX, along - 0.2, girderY, a0 + 0.1, along + 0.2, girderY + 0.4, a1 - 0.1, GIRDER);
        box(m, base, alongX, along - 0.6, rail - 0.25, a0, along + 0.6, rail, a0 + 0.3, TRUCK);
        box(m, base, alongX, along - 0.6, rail - 0.25, a1 - 0.3, along + 0.6, rail, a1, TRUCK);
        // the trolley, the rope and the hook
        box(m, base, alongX, along - 0.3, girderY + 0.35, across - 0.35, along + 0.3, girderY + 0.65, across + 0.35, TRUCK);
        double hookY = hook != null ? hook.y() + 0.35 : girderY - 0.8;
        box(m, base, alongX, along - 0.02, hookY + 0.2, across - 0.02, along + 0.02, girderY, across + 0.02, ROPE);
        box(m, base, alongX, along - 0.12, hookY, across - 0.08, along + 0.12, hookY + 0.22, across + 0.08, HOOK);
    }

    /** A box in the room's along/across frame, drawn relative to the controller. */
    private static void box(PartMesh m, Vec3d base, boolean alongX, double l0, double y0, double c0, double l1, double y1, double c1, int colour) {
        Vec3d a = alongX ? new Vec3d(l0, y0, c0) : new Vec3d(c0, y0, l0), b = alongX ? new Vec3d(l1, y1, c1) : new Vec3d(c1, y1, l1);
        m.box(new Vec3d(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())).subtract(base),
                new Vec3d(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())).subtract(base), colour);
    }

    /** The hook's place in the world while a vehicle's part hangs on the crane (null otherwise). */
    static Vec3d hookOf(HdfvVehicle v, float partialTick) {
        Vehicle vehicle = v.vehicle();
        if (v.assembled() || vehicle == null || v.blueprint() == null) { return null; }
        BuildSequence seq = RoomControllerBlockEntity.sequence(v.blueprint());
        BuildSequence.Step step = seq.at(v.built());
        if (step == null || step.kind() != BuildSequence.Kind.HOIST || !step.crane() || step.part() == null) { return null; }
        Vehicle.Installed in = vehicle.installed(step.part());
        if (in == null) { return null; }
        Vec3d[] b = HdfvVehicleRenderer.bounds(vehicle);
        Vec3d drop = in.mount().position(), pick = new Vec3d(drop.x(), b[0].y(), b[1].z() + 1.5);
        Vec3d local = CraneRunway.hook(pick, drop, 2.5, step.within(v.built()));
        Orientation q = v.visualOrientation(partialTick);
        Vec3d com = vehicle.properties().centerOfMassLocal();
        var at = v.getPosition(partialTick);
        Vec3d p = new Vec3d(at.x, at.y, at.z);
        return q.rotate(local.subtract(com)).add(p);
    }

    @Override public boolean shouldRenderOffScreen(RoomControllerBlockEntity be) { return true; }

    @Override public AABB getRenderBoundingBox(RoomControllerBlockEntity be) {
        int[] b = be.shownBox();
        return b.length == 6 ? new AABB(b[0] - 1, b[1] - 1, b[2] - 1, b[3] + 2, b[4] + 2, b[5] + 2).minmax(new AABB(be.getBlockPos())) : new AABB(be.getBlockPos());
    }

    @Override public int getViewDistance() { return 96; }
}
