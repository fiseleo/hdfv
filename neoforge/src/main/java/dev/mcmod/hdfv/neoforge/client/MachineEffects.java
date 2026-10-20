package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Lamp;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.neoforge.material.AlloyFurnaceBlock;
import dev.mcmod.hdfv.neoforge.material.AlloyFurnaceBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The machines' light (0.12, M4): a furnace's mouth glowing while it works (the foundry's, the alloy furnace's when
 * lit) and the welding arc flickering blue-white at the torch, as AeroCore lamps (a lens on the opening, a halo seen at
 * night; display only, no block is lit by them beyond the block's own light). Places in the block's pixels as
 * tools/blender/machines.py models them, turned with the block. Their smoke, sparks and steam are the blocks' own
 * (MachineParticles, from animateTick).
 */
public final class MachineEffects {
    private MachineEffects() {}

    /** Orange of a furnace's mouth, the arc's blue-white. */
    static final int FURNACE = 0xffff8a2a, ARC = 0xffcfe2ff;

    private static final java.util.Map<String, PartMesh> LENSES = new java.util.concurrent.ConcurrentHashMap<>();

    /** A lamp of a kind: its lens (a quad in block pixels, front at z0, facing north) and its light. */
    private record Light(double x0, double y0, double x1, double y1, double z, int argb, double radiusM, double sectorDeg) {}

    private static Light light(String kind) {
        return switch (kind) {
            case "foundry" -> new Light(5, 4.4, 11, 8.6, 0.38, FURNACE, 0.14, 170);
            case "alloy_furnace" -> new Light(4, 1.5, 12, 6.5, 0.48, FURNACE, 0.16, 170);
            case "welding_station" -> new Light(9.6, 9.9, 10.4, 10.7, 2.0, ARC, 0.05, 360);
            default -> null;
        };
    }

    /** Which machines have one. */
    static boolean lit(Station s) { return s == Station.FOUNDRY || s == Station.WELDING_STATION; }

    /** Draws a machine's light at a level (0 off; above 1 a flash) in its block, turned to its facing. */
    static void draw(String kind, Direction facing, float level, PoseStack pose, MultiBufferSource buffers) {
        Light l = light(kind);
        if (l == null || !(level > 0.01f)) { return; }
        // keyed by a mesh held here (MeshBakery keeps its keys weakly)
        PartMesh lens = LENSES.computeIfAbsent(kind, k -> mesh(l));
        MeshBakery.Baked baked = MeshBakery.get(lens, () -> lens, MeshBakery.Spec.WORLD);
        if (baked == null || baked.lamps().isEmpty()) { return; }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRotation(facing)));
        pose.translate(-0.5, 0, -0.5);
        PartLayers layers = new PartLayers().gpu(true).late(true);
        layers.lamps(new PartRenderer.Drawn(baked, 0, new float[]{level}), 0, pose);
        layers.flush(buffers);
        pose.popPose();
    }

    /** The lens quad (tagged as lamp 0's) and its point lamp facing out of the front. */
    private static PartMesh mesh(Light l) {
        PartMesh m = new PartMesh();
        double p = 1 / 16.0, z = l.z() * p;
        m.pens(Finishes.LAMP_CLEAR, MeshBuilder.LAMP);
        Vec3d n = new Vec3d(0, 0, -1);
        m.quad(new Vec3d(l.x1() * p, l.y0() * p, z), new Vec3d(l.x0() * p, l.y0() * p, z), new Vec3d(l.x0() * p, l.y1() * p, z), new Vec3d(l.x1() * p, l.y1() * p, z), l.argb(), n);
        Vec3d at = new Vec3d(0.5 * (l.x0() + l.x1()) * p, 0.5 * (l.y0() + l.y1()) * p, z - 0.01);
        m.addLamp(Lamp.point(0, 0, at, n, l.radiusM(), l.argb(), l.sectorDeg(), -1));
        return m;
    }

    /** The blockstate's turn for a facing (north none, east 90, south 180, west 270: clockwise seen from above). */
    static float yRotation(Direction facing) {
        return switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
    }

    /** A furnace's or the arc's level now: a steady glow that breathes a little, an arc that flickers and flashes. */
    static float level(String kind, long gameTime, float partialTick, int seed) {
        double t = (gameTime + partialTick) / 20.0;
        if ("welding_station".equals(kind)) {
            long tick = gameTime + seed;
            double noise = ((tick * 2654435761L) >>> 16 & 1023) / 1023.0;
            return noise < 0.15 ? 0.2f : (float) (0.8 + 0.8 * noise);
        }
        return (float) (0.85 + 0.1 * Math.sin(t * 2.3 + seed) + 0.05 * Math.sin(t * 7.1));
    }

    /** A working machine's light (CncRenderer draws machines; it hands these here). */
    static void machine(MachineBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        if (be.getLevel() == null || !be.shownWorking()) { return; }
        String kind = dev.mcmod.hdfv.neoforge.machine.MachineBlock.id(be.station);
        BlockState s = be.getBlockState();
        Direction facing = s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        draw(kind, facing, level(kind, be.getLevel().getGameTime(), partialTick, be.getBlockPos().hashCode()), pose, buffers);
    }

    // ---------------------------------------------------------------- 0.17: the machining families' tools

    /**
     * A machining family's light at its tool (FamilyRenderer), at a point in the block's frame: an EDM's spark gap
     * (blue-white, flickering), a plasma arc (violet-white), a laser's spot (white, the kerf glowing orange under the sheet).
     */
    static void tool(String kind, Vec3d at, float level, PoseStack pose, MultiBufferSource buffers) {
        if (!(level > 0.01f)) { return; }
        PartMesh lens = LENSES.computeIfAbsent("tool:" + kind, k -> toolLens(kind));
        MeshBakery.Baked baked = MeshBakery.get(lens, () -> lens, MeshBakery.Spec.WORLD);
        if (baked == null || baked.lamps().isEmpty()) { return; }
        pose.pushPose();
        pose.translate(at.x(), at.y(), at.z());
        PartLayers layers = new PartLayers().gpu(true).late(true);
        layers.lamps(new PartRenderer.Drawn(baked, 0, new float[]{level}), 0, pose);
        layers.flush(buffers);
        pose.popPose();
    }

    private static PartMesh toolLens(String kind) {
        int argb = switch (kind) { case "edm" -> 0xffcfe2ff; case "plasma" -> 0xffe6d8ff; default -> 0xfffff2e0; };
        double r = "plasma".equals(kind) ? 0.012 : 0.006;
        PartMesh m = new PartMesh();
        m.pens(Finishes.LAMP_CLEAR, MeshBuilder.LAMP);
        m.box(new Vec3d(-r, 0, -r), new Vec3d(r, 2 * r, r), argb);
        if ("laser".equals(kind)) { m.box(new Vec3d(-0.0015, -0.12, -0.0015), new Vec3d(0.0015, 0.0, 0.0015), 0xffff9a40); }
        if ("plasma".equals(kind)) { m.box(new Vec3d(-0.004, -0.1, -0.004), new Vec3d(0.004, 0.0, 0.004), argb); }
        m.addLamp(Lamp.point(0, 0, new Vec3d(0, r, 0), new Vec3d(0, 1, 0), "plasma".equals(kind) ? 0.1 : 0.05, argb, 360, -1));
        return m;
    }

    /** A tool's light now: an EDM's gap and a plasma arc flicker, a laser's spot is steady. */
    static float toolLevel(String kind, long gameTime, float partialTick, int seed) {
        long tick = gameTime + seed;
        double noise = ((tick * 2654435761L) >>> 16 & 1023) / 1023.0;
        return switch (kind) {
            case "edm" -> noise < 0.25 ? 0.15f : (float) (0.6 + 0.8 * noise);
            case "plasma" -> (float) (1.1 + 0.3 * noise);
            default -> 1f;
        };
    }

    /** The alloy furnace's mouth while it is lit. */
    public static final class Furnace implements BlockEntityRenderer<AlloyFurnaceBlockEntity> {
        public Furnace(BlockEntityRendererProvider.Context c) {}

        @Override public void render(AlloyFurnaceBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            BlockState s = be.getBlockState();
            if (be.getLevel() == null || !s.getValue(AlloyFurnaceBlock.LIT)) { return; }
            draw("alloy_furnace", s.getValue(AlloyFurnaceBlock.FACING), level("alloy_furnace", be.getLevel().getGameTime(), partialTick, be.getBlockPos().hashCode()), pose, buffers);
        }
    }
}
