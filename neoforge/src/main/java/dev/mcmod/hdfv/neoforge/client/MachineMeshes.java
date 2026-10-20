package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A formed multiblock's drawing (0.12, M3): what does not move (beds, columns, guards, frames) built once for what the
 * machine is made of (a key: its kind, facing, size and modules) and baked by AeroCore's MeshBakery (ambient occlusion,
 * levels of detail by distance, kept on the GPU); what moves (slides, spindles, tables, the walking beam) built each
 * frame from its own few faces. Both finished by colour (Looks) and drawn through PartLayers. Until the bake is done
 * the fixed part is drawn as built. Lit as the brighter of its block and the one above it (a controller that is a
 * whole block is dark inside). The bake is keyed by the built mesh itself, which FIXED holds: MeshBakery keeps its
 * keys weakly, and a key made anew each frame (a string, a record) was let go at every collection and baked again.
 */
final class MachineMeshes {
    private MachineMeshes() {}

    /** The fixed parts built, by key (the most recent 128). */
    private static final Map<Object, PartMesh> FIXED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Object, PartMesh> e) { return size() > 128; }
    };

    /**
     * Draws a machine whose moving parts stand still (it is not working): those too built once for a key of where they
     * stand, and baked.
     */
    static void drawResting(Object key, Supplier<PartMesh> fixed, Object restKey, Supplier<PartMesh> moving, BlockPos at, PoseStack pose, MultiBufferSource buffers, int light) {
        PartMesh still;
        synchronized (FIXED) { still = FIXED.computeIfAbsent(restKey, k -> Looks.finish(moving.get())); }
        draw(key, fixed, null, still, at, pose, buffers, light, 24, 64);
    }

    /** Draws a machine at its block: the fixed part of a key (built by fixed when first seen), the moving part as given. */
    static void draw(Object key, Supplier<PartMesh> fixed, PartMesh moving, BlockPos at, PoseStack pose, MultiBufferSource buffers, int light) {
        draw(key, fixed, moving, null, at, pose, buffers, light, 24, 64);
    }

    /**
     * 0.20: the same with the distances (m) the levels of detail change at (the control tower, 60 m tall and seen from
     * kilometres: 64 and 256), measured to a point of the machine (its middle, not its controller).
     */
    static void draw(Object key, Supplier<PartMesh> fixed, PartMesh moving, BlockPos at, PoseStack pose, MultiBufferSource buffers, int light,
                     double near, double far, net.minecraft.world.phys.Vec3 middle) {
        drawAt(key, fixed, moving, null, at, pose, buffers, light, near, far, middle);
    }

    private static void draw(Object key, Supplier<PartMesh> fixed, PartMesh moving, PartMesh still, BlockPos at, PoseStack pose, MultiBufferSource buffers, int light,
                             double near, double far) {
        drawAt(key, fixed, moving, still, at, pose, buffers, light, near, far, at.getCenter());
    }

    private static void drawAt(Object key, Supplier<PartMesh> fixed, PartMesh moving, PartMesh still, BlockPos at, PoseStack pose, MultiBufferSource buffers, int light,
                               double near, double far, net.minecraft.world.phys.Vec3 middle) {
        PartMesh mesh;
        synchronized (FIXED) { mesh = FIXED.computeIfAbsent(key, k -> Looks.finish(fixed.get())); }
        // a controller that is a whole block has no light inside it: the machine is lit as the air above it, where brighter
        var world = Minecraft.getInstance().level;
        if (world != null) {
            int above = net.minecraft.client.renderer.LevelRenderer.getLightColor(world, at.above());
            light = Math.max(light & 0xffff, above & 0xffff) | Math.max(light >>> 16, above >>> 16) << 16;
        }
        PartLayers layers = new PartLayers().gpu(true).late(true);
        MeshBakery.Baked baked = MeshBakery.get(mesh, () -> mesh, MeshBakery.Spec.WORLD);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = Math.sqrt(camera.distanceToSqr(middle));
        int level = distance < near ? 0 : distance < far ? 1 : 2;
        if (baked != null) { layers.draw(baked.level(level), pose, light); } else { layers.draw(mesh, pose, light); }
        if (moving != null && !moving.quads().isEmpty() && level < 2) { layers.draw(Looks.finish(moving), pose, light); }
        if (still != null && !still.quads().isEmpty() && level < 2) {
            MeshBakery.Baked rest = MeshBakery.get(still, () -> still, MeshBakery.Spec.WORLD);
            if (rest != null) { layers.draw(rest.level(0), pose, light); } else { layers.draw(still, pose, light); }
        }
        layers.flush(buffers);
    }
}
