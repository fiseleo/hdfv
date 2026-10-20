package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.AutoWiring;
import dev.mcmod.hdfv.neoforge.client.model.EngineModel;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * An assembly room's 3D view of the vehicle laid out in it (0.7): each part as the design screen draws it (engines,
 * bodies, wings, wheels...), or as the world draws it, or as its contact spheres, at its mount; the part being edited
 * outlined in yellow. Dragged to turn, scrolled to zoom; it turns by itself when left alone.
 * <p>
 * 0.12: drawn from the same bakes as the design screen (finishes, ambient occlusion; as built until they are done),
 * the bodies without their mount marks and stand-in wheels (the vehicle's own wheels are there).
 */
final class VehiclePreview {
    private static final double SPIN = 8, IDLE = 3;
    private static final int GHOST = 0xff7c8389, MARK = 0x66ffd040;

    private record Placed(String id, EngineModel model, PartMesh mesh, Vec3d at, Orientation rotation, List<CollisionSphere> spheres,
                          dev.mcmod.aerocore.api.vehicle.PartDesign design) {}

    private float yaw = -35, pitch = 25;
    private double zoom = 1;
    private long lastTouch = Long.MIN_VALUE / 2, lastFrame;
    private String builtFor;
    private List<Placed> placed = List.of();
    private Vec3d centre = Vec3d.ZERO;
    private double size = 1;
    private String failure;

    void drag(double dx, double dy) {
        yaw += (float) dx * 1.2f;
        pitch = Math.clamp(pitch + (float) dy * 1.2f, -89, 89);
        lastTouch = Util.getMillis();
    }

    /** 0.13, development: sets the view's turn and tilt (degrees). */
    void devView(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Math.clamp(pitch, -89, 89);
        lastTouch = Util.getMillis();
    }

    void zoom(double steps) {
        zoom = Math.clamp(zoom * Math.pow(1.15, steps), 0.3, 8);
        lastTouch = Util.getMillis();
    }

    /**
     * 0.13: models by design (record equality), kept while the design is: the paint booth's preview is rebuilt on every
     * edit, and only the part edited has a new design (the others keep their models and their bakes).
     */
    private static final java.util.Map<dev.mcmod.aerocore.api.vehicle.PartDesign, java.util.Optional<EngineModel>> MODELS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static EngineModel model(dev.mcmod.aerocore.api.vehicle.PartDesign d) {
        java.util.Optional<EngineModel> m = MODELS.get(d);
        if (m == null) {
            m = java.util.Optional.ofNullable(Preview.model(d, "", false, false));
            MODELS.put(d, m);
        }
        return m.orElse(null);
    }

    private void rebuild(String json) {
        builtFor = json;
        placed = List.of();
        failure = null;
        if (json.isEmpty()) { return; }
        try {
            VehicleBlueprint wired = AutoWiring.wire(VehicleBlueprint.fromJsonText(json));
            Vehicle v = wired.compile();
            List<Placed> out = new ArrayList<>();
            for (Vehicle.Installed in : v.installed()) {
                EngineModel model = null;
                PartMesh mesh = null;
                try { model = model(in.part().design()); } catch (RuntimeException ignored) { model = null; }
                if (model == null) {
                    PartRenderer r = PartRenderer.of(in.part());
                    if (r != null) {
                        mesh = new PartMesh();
                        r.mesh(in.part(), mesh);
                        if (mesh.quads().isEmpty()) { mesh = null; } else { dev.mcmod.hdfv.neoforge.client.model.Looks.finish(mesh); }
                    }
                }
                if (model == null && mesh == null) {
                    mesh = new PartMesh();
                    for (CollisionSphere c : in.part().collision()) {
                        Vec3d h = new Vec3d(c.radiusM(), c.radiusM(), c.radiusM());
                        mesh.box(c.centerLocal().subtract(h), c.centerLocal().add(h), GHOST);
                    }
                }
                out.add(new Placed(in.id(), model, mesh, in.mount().position(), in.rotation(), in.part().collision(), in.part().design()));
            }
            placed = out;
            AssemblyPlan p = AssemblyPlan.of(wired);
            centre = p.min().add(p.max()).scale(0.5);
            size = Math.max(Math.max(p.lengthM(), Math.max(p.widthM(), p.heightM())), 0.5);
        } catch (RuntimeException e) {
            failure = String.valueOf(e.getMessage());
        }
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h, String json, String selected) {
        g.fillGradient(x, y, x + w, y + h, 0xe0353d47, 0xe0101317);
        g.renderOutline(x, y, w, h, 0x40FFFFFF);
        if (!json.equals(builtFor)) { rebuild(json); }
        if (failure != null) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(net.minecraft.network.chat.Component.literal(failure), w - 8);
            for (int i = 0; i < lines.size() && i < 6; i++) { g.drawString(font, lines.get(i), x + 4, y + 4 + 10 * i, 0xffff7f6f, false); }
            return;
        }
        if (placed.isEmpty()) { return; }
        for (Placed p : placed) { DecalCache.wantFor(p.design()); }
        long now = Util.getMillis();
        if (lastFrame != 0 && now - lastTouch > IDLE * 1000) { yaw += (float) (SPIN * (now - lastFrame) / 1000.0); }
        lastFrame = now;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = (float) (Math.min(w, 1.3 * h) * 0.8 / size * zoom);
        pose.translate(x + w / 2.0, y + h / 2.0, 250);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-centre.x(), -centre.y(), -centre.z());
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        // 0.12: each part from its bakes (as the design screen draws it), as built while they are not done
        var layers = new dev.mcmod.aerocore.neoforge.client.PartLayers();
        java.util.List<Placed> unbaked = new ArrayList<>();
        for (Placed p : placed) {
            pose.pushPose();
            place(pose, p);
            if (p.model() != null && !Preview.drawBaked(p.model(), pose, 0, Vec3d.Z, buffers, false)) { unbaked.add(p); }
            if (p.mesh() != null) { layers.draw(p.mesh(), pose, LightTexture.FULL_BRIGHT); }
            pose.popPose();
        }
        layers.flush(buffers);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(PartMesh.TEXTURE));
        for (Placed p : unbaked) {
            pose.pushPose();
            place(pose, p);
            p.model().emit(solid, pose, LightTexture.FULL_BRIGHT, 0, Vec3d.Z, false);
            pose.popPose();
        }
        g.flush();
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(PartMesh.TEXTURE));
        for (Placed p : placed) {
            pose.pushPose();
            place(pose, p);
            if (p.model() != null && unbaked.contains(p)) { p.model().emit(glass, pose, LightTexture.FULL_BRIGHT, 0, Vec3d.Z, true); }
            if (p.id().equals(selected)) {
                PartMesh mark = new PartMesh();
                if (p.spheres().isEmpty()) { mark.box(new Vec3d(-0.15, -0.15, -0.15), new Vec3d(0.15, 0.15, 0.15), MARK); }
                for (CollisionSphere c : p.spheres()) {
                    Vec3d hs = new Vec3d(c.radiusM(), c.radiusM(), c.radiusM()).scale(1.05);
                    mark.box(c.centerLocal().subtract(hs), c.centerLocal().add(hs), MARK);
                }
                mark.emit(glass, pose, LightTexture.FULL_BRIGHT, true);
            }
            pose.popPose();
        }
        g.flush();
        pose.popPose();
        Lighting.setupFor3DItems();
        g.disableScissor();
    }

    /** 0.13: what the view hits at a point of the screen: a part, its design and the point in the part's own frame. */
    record Hit(String id, dev.mcmod.aerocore.api.vehicle.PartDesign design, Vec3d local) {}

    /**
     * 0.13: the part under a point of the view (x, y, w, h as rendered), for placing stickers: the view's transform
     * inverted gives a ray (the view is orthographic, looking down -z of the screen), carried into each part's frame
     * and met with its built mesh's triangles; the nearest hit. Null when nothing is under the point.
     */
    Hit pick(double mx, double my, int x, int y, int w, int h) {
        if (placed.isEmpty()) { return null; }
        float scale = (float) (Math.min(w, 1.3 * h) * 0.8 / size * zoom);
        org.joml.Matrix4f view = new org.joml.Matrix4f().translate((float) (x + w / 2.0), (float) (y + h / 2.0), 250).scale(scale, -scale, scale)
                .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw))
                .translate((float) -centre.x(), (float) -centre.y(), (float) -centre.z());
        org.joml.Matrix4f inverse = view.invert(new org.joml.Matrix4f());
        org.joml.Vector3f a = inverse.transformPosition((float) mx, (float) my, 10000, new org.joml.Vector3f());
        org.joml.Vector3f b = inverse.transformPosition((float) mx, (float) my, -10000, new org.joml.Vector3f());
        Vec3d from = new Vec3d(a.x, a.y, a.z), to = new Vec3d(b.x, b.y, b.z);
        Hit best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Placed p : placed) {
            Orientation back = p.rotation().inverse();
            Vec3d o = back.rotate(from.subtract(p.at())), d = back.rotate(to.subtract(p.at())).subtract(o);
            List<dev.mcmod.aerocore.api.mesh.MeshBuilder.Quad> quads = p.model() != null ? p.model().fixed.quads() : p.mesh() != null ? p.mesh().quads() : List.of();
            for (dev.mcmod.aerocore.api.mesh.MeshBuilder.Quad q : quads) {
                double t = Math.min(ray(o, d, q.a(), q.b(), q.c()), ray(o, d, q.a(), q.c(), q.d()));
                if (t < nearest) { nearest = t; best = new Hit(p.id(), p.design(), o.add(d.scale(t))); }
            }
        }
        return best;
    }

    /** Where a ray o + t d (t from 0 to 1) meets a triangle (Moller-Trumbore), or infinity. */
    private static double ray(Vec3d o, Vec3d d, Vec3d a, Vec3d b, Vec3d c) {
        Vec3d e1 = b.subtract(a), e2 = c.subtract(a), p = d.cross(e2);
        double det = e1.dot(p);
        if (Math.abs(det) < 1e-12) { return Double.POSITIVE_INFINITY; }
        double inv = 1 / det;
        Vec3d s = o.subtract(a);
        double u = s.dot(p) * inv;
        if (u < 0 || u > 1) { return Double.POSITIVE_INFINITY; }
        Vec3d q = s.cross(e1);
        double v = d.dot(q) * inv;
        if (v < 0 || u + v > 1) { return Double.POSITIVE_INFINITY; }
        double t = e2.dot(q) * inv;
        return t >= 0 && t <= 1 ? t : Double.POSITIVE_INFINITY;
    }

    private static void place(PoseStack pose, Placed p) {
        pose.translate(p.at().x(), p.at().y(), p.at().z());
        Orientation r = p.rotation();
        pose.mulPose(new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
    }
}
