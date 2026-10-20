package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Mesh;
import dev.mcmod.aerocore.api.mesh.MeshOps;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.probe.WorldAir;
import dev.mcmod.hdfv.airport.AviationUnits;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.neoforge.airport.WindsockBlockEntity;
import dev.mcmod.hdfv.neoforge.client.model.AirfieldKit;
import dev.mcmod.hdfv.neoforge.client.model.Details;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A windsock (0.20): the mast above its block (5.5 m in all), the head on its swivel turned downwind, and the sock in
 * five bands of orange and white, from its hoop out along the wind: fully out (level) at 15 kt (ICAO Annex 14,
 * 5.1.1), hanging down when calm, fluttering a little. The wind is AeroCore's at the sock (WorldAir: mean and gust),
 * smoothed here (the sock swings round in about a second and a half). The mast is baked once; the head and sock are
 * built each frame (about 300 faces).
 */
public final class WindsockRenderer implements BlockEntityRenderer<WindsockBlockEntity> {
    public WindsockRenderer(BlockEntityRendererProvider.Context c) {}

    /** The mast's top above the block's foot (m), the sock's bands and their length. */
    static final double TOP = 5.5, BAND = 0.55;
    static final int BANDS = 5;
    private static final int ORANGE = 0xffff6a13, WHITE = 0xfff2f2ee, MAST = 0xfff2f2f2, HEAD = 0xff8a9097;
    private static final double FULL_MPS = 15 * AviationUnits.KNOT_MPS;
    private static final Object MAST_KEY = new Object();

    @Override public void render(WindsockBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) { return; }
        BlockPos pos = be.getBlockPos();
        WorldAir.Air air = WorldAir.at(be.getLevel(), pos.getX() + 0.5, pos.getY() + TOP, pos.getZ() + 0.5);
        Vec3d wind = air.meanWind().add(air.gust());
        double speed = Math.hypot(wind.x(), wind.z());
        double target = Math.atan2(wind.z(), wind.x()), fill = Math.min(1, speed / FULL_MPS);
        // smoothing over the frame time (a frame at 60 fps moves 1.1 % toward the wind's direction, 1.7 % toward its fill)
        double dt = net.minecraft.client.Minecraft.getInstance().getTimer().getRealtimeDeltaTicks() / 20.0;
        if (Double.isNaN(be.shownAngle)) { be.shownAngle = target; be.shownFill = fill; }
        if (speed > 0.3) { be.shownAngle += Math.atan2(Math.sin(target - be.shownAngle), Math.cos(target - be.shownAngle)) * Math.min(1, dt / 1.5); }
        be.shownFill += (fill - be.shownFill) * Math.min(1, dt / 1.0);
        double t = (be.getLevel().getGameTime() + partialTick) / 20.0;
        PartMesh moving = new PartMesh();
        sock(moving, be.shownAngle, be.shownFill, t, pos.hashCode());
        MachineMeshes.draw(MAST_KEY, WindsockRenderer::mast, moving, pos, pose, buffers, light, 24, 96,
                new Vec3(pos.getX() + 0.5, pos.getY() + TOP / 2, pos.getZ() + 0.5));
    }

    private static PartMesh mast() {
        PartMesh m = new PartMesh();
        m.finish(Finishes.PAINT);
        m.cylinder(new Vec3d(0.5, 1, 0.5), new Vec3d(0.5, TOP, 0.5), 0.05, 0.045, 10, MAST);
        // a red band at the top, as a mast near a runway is marked
        m.cylinder(new Vec3d(0.5, TOP - 0.5, 0.5), new Vec3d(0.5, TOP - 0.02, 0.5), 0.052, 0.052, 10, 0xffc8261e);
        return m;
    }

    /** The head and the sock streaming toward an angle (radians from +x toward +z), filled 0-1. */
    static void sock(PartMesh m, double angle, double fill, double t, int seed) {
        Vec3d along = new Vec3d(Math.cos(angle), 0, Math.sin(angle)), up = new Vec3d(0, 1, 0), top = new Vec3d(0.5, TOP, 0.5);
        Mesh head = MeshOps.placed(AirfieldKit.mesh(AirfieldKit.Part.WINDSOCK_HEAD, true, HEAD), new Vec3d(1, 1, 1), Details.basis(up, along), top);
        m.quads().addAll(head.quads());
        Vec3d side = along.cross(up);
        Vec3d at = top.add(along.scale(AirfieldKit.HOOP_X)).add(up.scale(AirfieldKit.HOOP_Y));
        double droop = Math.toRadians(80) * (1 - fill), length = BAND * (0.55 + 0.45 * fill);
        double r = AirfieldKit.HOOP_R;
        m.finish(Finishes.FABRIC);
        for (int i = 0; i < BANDS; i++) {
            // each band hangs a little more than the one before when the wind is light, and flutters about its line
            double down = droop * (0.6 + 0.4 * (i + 1) / BANDS);
            double flutter = 0.06 * fill * Math.sin(t * (7 + i) + seed * 0.37 + i * 1.3);
            Vec3d dir = along.scale(Math.cos(down)).subtract(up.scale(Math.sin(down))).add(side.scale(flutter)).normalized();
            Vec3d next = at.add(dir.scale(length));
            double r1 = AirfieldKit.HOOP_R * (1 - 0.11 * (i + 1)) * (0.75 + 0.25 * fill);
            m.cylinder(at, next, r, r1, 12, i % 2 == 0 ? ORANGE : WHITE);
            at = next;
            r = r1;
        }
    }

    @Override public boolean shouldRenderOffScreen(WindsockBlockEntity be) { return true; }

    @Override public int getViewDistance() { return 192; }

    @Override public AABB getRenderBoundingBox(WindsockBlockEntity be) { return new AABB(be.getBlockPos()).inflate(3.5, 0, 3.5).expandTowards(0, TOP + 0.5, 0); }
}
