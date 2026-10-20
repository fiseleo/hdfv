package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.EngineModel;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import dev.mcmod.hdfv.neoforge.client.model.PistonEngineModel;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.neoforge.power.EngineStandBlockEntity;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The engine on an engine stand (0.11): the part on the bed, its front toward the stand's face, baked once
 * (finishes, occlusion), its fan and pulleys turning at the speed the stand shows, its pipes glowing with the load
 * and smoking (a cough when it catches, a diesel's soot near full load; Exhausts).
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EngineStandRenderer implements BlockEntityRenderer<EngineStandBlockEntity> {
    /** An engine as drawn: its design, model, key for its baked fixed parts, and how far its lowest point is below its crank. */
    private record Built(PistonEngineDesign design, EngineModel model, PartMesh drawn, Object key, double lift) {}

    /** By the item on the stand (a new item comes with every change the server sends). */
    private static final Map<ItemStack, Built> BUILT = Collections.synchronizedMap(new WeakHashMap<>());

    public EngineStandRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(HdfvPower.STAND_ENTITY.get(), EngineStandRenderer::new);
    }

    private static Built built(ItemStack engine) {
        return BUILT.computeIfAbsent(engine, stack -> {
            PartDesign d = Workpieces.partDesign(stack);
            if (!(d instanceof PistonEngineDesign p)) { return null; }
            EngineModel m = PistonEngineModel.build(p, null, false);
            Vec3d[] b = m.bounds();
            PartMesh drawn = new PartMesh().add(m.fixed, Orientation.IDENTITY, Vec3d.ZERO).add(dev.mcmod.hdfv.neoforge.client.model.StandIn.of(m.at(0), false), Orientation.IDENTITY, Vec3d.ZERO);
            return new Built(p, m, drawn, new Object(), b == null ? 0 : -b[0].y());
        });
    }

    /** Turns the engine's x (its front) toward the stand's face. */
    private static float yaw(Direction facing) {
        return switch (facing) { case NORTH -> 90; case WEST -> 180; case SOUTH -> 270; default -> 0; };
    }

    @Override public void render(EngineStandBlockEntity stand, float partialTick, PoseStack pose, MultiBufferSource buffers, int blockLight, int overlay) {
        // the stand is a solid block (no light inside it): the engine on top is lit as the air above it
        int light = stand.getLevel() == null ? blockLight : net.minecraft.client.renderer.LevelRenderer.getLightColor(stand.getLevel(), stand.getBlockPos().above());
        ItemStack engine = stand.engine();
        if (engine.isEmpty()) { return; }
        Built b = built(engine);
        if (b == null) { return; }
        // the crank's angle, turned at the speed shown since the last frame (four turns of a cycle kept, for the cams)
        long now = System.nanoTime();
        double dt = stand.drawnNanos == 0 ? 0 : Math.min(0.1, (now - stand.drawnNanos) * 1e-9);
        stand.drawnNanos = now;
        stand.drawnAngle = (stand.drawnAngle + stand.shownRpm() * Math.PI / 30 * dt) % (8 * Math.PI);
        double angle = stand.drawnAngle;
        Direction facing = stand.getBlockState().getValue(PowerBlock.FACING);
        float yaw = yaw(facing);
        pose.pushPose();
        pose.translate(0.5, 1 + b.lift(), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        PartLayers layers = new PartLayers().gpu(true);
        MeshBakery.Baked baked = MeshBakery.get(b.key(), () -> Looks.finish(b.drawn()), MeshBakery.Spec.WORLD);
        // its level of detail by how far it is (as AeroCore's vehicles: near, middle, and the stand-in far away)
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = Math.sqrt(camera.distanceToSqr(stand.getBlockPos().getCenter()));
        int level = distance < 16 ? 0 : distance < 40 ? 1 : 2;
        if (baked != null) {
            var drawn = baked.level(level);
            layers.draw(drawn, pose, light);
            int glow = Exhausts.glowAt("stand" + stand.getBlockPos().asLong(), Exhausts.pistonTarget(b.design(), stand.shownRunning(), stand.shownLoad()));
            if ((glow >>> 24) > 0 && level < 2) { layers.glow(drawn, pose, glow, true); }
        }
        if (level < 2) { EnginePieces.draw(b.model(), 1, r -> angle, pose, layers, light); }
        pose.popPose();
        layers.flush(buffers);
        // the pipes' ends in the world, for the smoke
        Orientation turn = Orientation.rotationVector(Vec3d.Y.scale(Math.toRadians(yaw)));
        Vec3d origin = new Vec3d(stand.getBlockPos().getX() + 0.5, stand.getBlockPos().getY() + 1 + b.lift(), stand.getBlockPos().getZ() + 0.5);
        List<Vec3d[]> outlets = new ArrayList<>();
        for (Vec3d[] o : b.model().exhausts) { outlets.add(new Vec3d[]{origin.add(turn.rotate(o[0])), turn.rotate(o[1])}); }
        Exhausts.standSeen(stand.getBlockPos(), outlets, b.design(), stand.shownRunning(), stand.shownLoad());
    }

    /** The engine stands above its block. */
    @Override public AABB getRenderBoundingBox(EngineStandBlockEntity stand) { return new AABB(stand.getBlockPos()).inflate(1.5); }
}
