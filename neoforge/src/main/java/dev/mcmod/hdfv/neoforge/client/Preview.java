package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.neoforge.client.model.EngineModel;
import dev.mcmod.hdfv.neoforge.client.model.GasTurbineModel;
import dev.mcmod.hdfv.neoforge.client.model.JetEngineModel;
import dev.mcmod.hdfv.neoforge.client.model.PistonEngineModel;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The design screen's 3D view of the design (PistonEngineModel, JetEngineModel) on a studio floor: the page being
 * edited highlighted, the engine running slowly (crank, rods, pistons, cams, valves; a jet's spool), dragged to turn,
 * scrolled to zoom; it turns by itself when left alone.
 */
final class Preview {
    /** Degrees per second when left alone; seconds after a drag before it turns again; crank turns per second. */
    private static final double SPIN = 10, IDLE = 3, CRANK = 0.35;

    boolean cutaway = true;
    /** 0.12: a body's doors drawn open (its pieces posed by EngineModel.posed). */
    boolean doorsOpen;
    private float yaw = -35, pitch = 20;
    private double zoom = 1;
    private long lastTouch = Long.MIN_VALUE / 2, lastFrame;
    private PartDesign modelFor;
    private String modelPage;
    private boolean modelCutaway;
    private EngineModel model;
    private PartMesh floor;
    private Vec3d centre = Vec3d.ZERO;
    private double size = 1;
    private boolean failed;

    /** Whether there is anything to draw for a design. */
    static boolean draws(PartDesign d) {
        return d instanceof PistonEngineDesign || d instanceof JetEngineDesign || d instanceof TurbofanDesign || d instanceof ShaftTurbineDesign
                || d instanceof dev.mcmod.hdfv.part.air.WingDesign || d instanceof dev.mcmod.hdfv.part.air.AirPropellerDesign || d instanceof dev.mcmod.hdfv.part.air.RotorDesign
                || d instanceof dev.mcmod.hdfv.part.body.BodyDesign || d instanceof dev.mcmod.hdfv.part.wheel.AxleDesign
                || d instanceof dev.mcmod.hdfv.part.wheel.LandingGearDesign || d instanceof dev.mcmod.hdfv.part.drive.GearboxDesign
                || d instanceof dev.mcmod.hdfv.part.drive.TransferCaseDesign || d instanceof dev.mcmod.hdfv.part.engine.RamjetDesign
                || d instanceof dev.mcmod.hdfv.part.engine.ScramjetDesign || d instanceof dev.mcmod.hdfv.part.engine.PulsejetDesign
                || d instanceof dev.mcmod.hdfv.part.engine.SolidRocketDesign || d instanceof dev.mcmod.hdfv.part.engine.TurboRamjetDesign
                || d instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign || drawnAsInWorld(d);
    }

    /**
     * 0.12: the parts whose design screen shows them as the world does (their world mesh, from PartLooks and
     * BodyRenderers): floats, hulls, screws, rudders and control surfaces, motors, boxes, ballast, fuel tanks, seats.
     */
    static boolean drawnAsInWorld(PartDesign d) {
        return d instanceof dev.mcmod.hdfv.part.FloatDesign || d instanceof dev.mcmod.hdfv.part.HullDesign || d instanceof dev.mcmod.hdfv.part.PlaningHullDesign
                || d instanceof dev.mcmod.hdfv.part.PropellerDesign || d instanceof dev.mcmod.hdfv.part.ControlSurfaceDesign || d instanceof dev.mcmod.hdfv.part.MotorDesign
                || d instanceof dev.mcmod.hdfv.part.BoxDesign || d instanceof dev.mcmod.hdfv.part.BallastDesign || d instanceof dev.mcmod.hdfv.part.FuelTankDesign
                || d instanceof dev.mcmod.hdfv.part.SeatDesign || d instanceof dev.mcmod.hdfv.part.StorageBoxDesign;
    }

    /** 0.12: a part's world mesh as a model (all of it fixed; its page not highlighted), null where it draws nothing. */
    private static EngineModel asInWorld(PartDesign d) {
        var part = d.compile();
        var r = dev.mcmod.aerocore.neoforge.client.PartRenderer.of(part);
        if (r == null) { return null; }
        PartMesh m = new PartMesh();
        r.mesh(part, m);
        if (m.quads().isEmpty()) { return null; }
        return EngineModel.of(dev.mcmod.hdfv.neoforge.client.model.Looks.finish(m));
    }

    /** The design screen's model of a design (null when there is none: see draws). */
    static EngineModel model(PartDesign d, String page, boolean cutaway) { return model(d, page, cutaway, true); }

    /** 0.12: as model, a body's mount points and family car's wheels marked or not (the assembly room's view leaves them out). */
    static EngineModel model(PartDesign d, String page, boolean cutaway, boolean marks) {
        if (!draws(d)) { return null; }
        return switch (d) {
            case PistonEngineDesign p -> PistonEngineModel.build(p, page, cutaway);
            case TurbofanDesign f -> GasTurbineModel.build(f, page, cutaway);
            case ShaftTurbineDesign t -> GasTurbineModel.build(t, page, cutaway);
            case dev.mcmod.hdfv.part.air.WingDesign w -> dev.mcmod.hdfv.neoforge.client.model.AirframeModel.build(w, page);
            case dev.mcmod.hdfv.part.air.AirPropellerDesign a -> dev.mcmod.hdfv.neoforge.client.model.AirframeModel.build(a, page);
            case dev.mcmod.hdfv.part.air.RotorDesign r -> dev.mcmod.hdfv.neoforge.client.model.AirframeModel.build(r, page);
            case dev.mcmod.hdfv.part.body.BodyDesign b -> dev.mcmod.hdfv.neoforge.client.model.BodyModel.build(b, page, cutaway, marks);
            case dev.mcmod.hdfv.part.wheel.AxleDesign a -> dev.mcmod.hdfv.neoforge.client.model.WheelModel.build(a, page);
            case dev.mcmod.hdfv.part.wheel.LandingGearDesign l -> dev.mcmod.hdfv.neoforge.client.model.WheelModel.build(l, page);
            case dev.mcmod.hdfv.part.drive.GearboxDesign g -> dev.mcmod.hdfv.neoforge.client.model.GearboxModel.build(g, page, cutaway);
            case dev.mcmod.hdfv.part.drive.TransferCaseDesign t -> dev.mcmod.hdfv.neoforge.client.model.GearboxModel.build(t, page);
            case dev.mcmod.hdfv.part.engine.RamjetDesign r -> dev.mcmod.hdfv.neoforge.client.model.JetFamilyModel.ramjet(r, page, cutaway);
            case dev.mcmod.hdfv.part.engine.ScramjetDesign x -> dev.mcmod.hdfv.neoforge.client.model.JetFamilyModel.scramjet(x, page, cutaway);
            case dev.mcmod.hdfv.part.engine.PulsejetDesign p -> dev.mcmod.hdfv.neoforge.client.model.JetFamilyModel.pulsejet(p, page, cutaway);
            case dev.mcmod.hdfv.part.engine.SolidRocketDesign s -> dev.mcmod.hdfv.neoforge.client.model.JetFamilyModel.solidRocket(s, page, cutaway);
            case dev.mcmod.hdfv.part.engine.TurboRamjetDesign t -> dev.mcmod.hdfv.neoforge.client.model.JetFamilyModel.turboRamjet(t, page, cutaway);
            case JetEngineDesign j -> JetEngineModel.build(j, page, cutaway);
            case dev.mcmod.hdfv.part.chassis.ChassisDesign c -> dev.mcmod.hdfv.neoforge.client.model.ChassisModel.build(c, page);
            default -> asInWorld(d);
        };
    }

    /** Development (AutoShot): a fixed view, held (it does not turn by itself until dragged). */
    private boolean held;

    void hold(float yawDeg, float pitchDeg) { yaw = yawDeg; pitch = Math.clamp(pitchDeg, -89, 89); held = true; }

    void zoomTo(double factor) { zoom = Math.clamp(factor, 0.4, 40); }

    /** Development: the view turns about this point (the model's frame) instead of the box centre; null for the centre. */
    private Vec3d focus;

    void focus(Vec3d point) { focus = point; }

    void drag(double dx, double dy) {
        held = false;
        yaw += (float) dx * 1.2f;
        pitch = Math.clamp(pitch + (float) dy * 1.2f, -89, 89);
        lastTouch = Util.getMillis();
    }

    void zoom(double steps) {
        zoom = Math.clamp(zoom * Math.pow(1.15, steps), 0.4, 6);
        lastTouch = Util.getMillis();
    }

    /** Quads drawn per frame (for the development log). */
    int size() { return model == null ? 0 : model.size(); }

    /** Development: the model's box (min, max), for laying a part out on a deck or a stand; null without one. */
    Vec3d[] bounds() { return model == null ? null : model.bounds(); }

    private void rebuild(PartDesign d, String page) {
        modelFor = d;
        modelPage = page;
        modelCutaway = cutaway;
        failed = false;
        try {
            model = model(d, page, cutaway);
            finish(model);
            Vec3d[] b = model.bounds();
            if (b == null) { failed = true; return; }
            centre = b[0].add(b[1]).scale(0.5);
            Vec3d extent = b[1].subtract(b[0]);
            size = Math.max(Math.max(extent.x(), Math.max(extent.y(), extent.z())), 1e-3);
            floor = floor(b, size);
        } catch (RuntimeException e) {
            failed = true;   // a design too broken to draw; its issues say why
        }
    }

    /** A soft shadow and a faint grid on the floor under the engine. */
    private static PartMesh floor(Vec3d[] b, double size) {
        PartMesh m = new PartMesh();
        double y = b[0].y() - 0.004 * size, extent = 0.7 * size;
        double cx = 0.5 * (b[0].x() + b[1].x()), cz = 0.5 * (b[0].z() + b[1].z());
        double[] steps = {0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 5};
        double step = steps[steps.length - 1];
        for (double s : steps) { if (s >= size / 10) { step = s; break; } }
        double line = 0.0015 * size;
        int grid = 0x22ffffff;
        for (double x = Math.floor((cx - extent) / step) * step; x <= cx + extent; x += step) {
            m.box(new Vec3d(x - line, y, cz - extent), new Vec3d(x + line, y + 1e-4, cz + extent), grid);
        }
        for (double z = Math.floor((cz - extent) / step) * step; z <= cz + extent; z += step) {
            m.box(new Vec3d(cx - extent, y, z - line), new Vec3d(cx + extent, y + 1e-4, z + line), grid);
        }
        double rx = 0.5 * (b[1].x() - b[0].x()) * 1.1, rz = 0.5 * (b[1].z() - b[0].z()) * 1.1;
        for (int k = 0; k < 4; k++) {
            double f = 1 - 0.18 * k;
            PartMesh disc = new PartMesh().cylinder(new Vec3d(0, 0, 0), new Vec3d(0, 1e-4, 0), 1, 1, 40, 0x1c000000);
            for (PartMesh.Quad q : disc.quads()) {
                m.quads().add(new PartMesh.Quad(squash(q.a(), rx * f, rz * f, cx, y + 2e-4 * (k + 1), cz), squash(q.b(), rx * f, rz * f, cx, y + 2e-4 * (k + 1), cz),
                        squash(q.c(), rx * f, rz * f, cx, y + 2e-4 * (k + 1), cz), squash(q.d(), rx * f, rz * f, cx, y + 2e-4 * (k + 1), cz), q.color(), Vec3d.Y));
            }
        }
        return m;
    }

    private static Vec3d squash(Vec3d v, double rx, double rz, double cx, double y, double cz) { return new Vec3d(cx + v.x() * rx, y, cz + v.z() * rz); }

    /** 0.11: the model's meshes given finishes by colour (in place). */
    static void finish(EngineModel m) {
        dev.mcmod.hdfv.neoforge.client.model.Looks.finish(m.fixed);
        for (EngineModel.Piece p : m.pieces) { dev.mcmod.hdfv.neoforge.client.model.Looks.finish(p.mesh()); }
        for (EngineModel.Section s : m.sections) {
            dev.mcmod.hdfv.neoforge.client.model.Looks.finish(s.behind());
            dev.mcmod.hdfv.neoforge.client.model.Looks.finish(s.front());
        }
    }

    /**
     * The fixed parts and the cut casings bake with occlusion, leaving out quads that cannot be seen (enclosed, or
     * covered by a surface just in front: two shells drawn at almost one radius flicker in the screen's coarse depth
     * buffer, the covered one shaded dark); the cut halves bake apart from what they enclose, so a cutaway still shows
     * the inside.
     */
    private static final dev.mcmod.aerocore.neoforge.client.MeshBakery.Spec WHOLE = new dev.mcmod.aerocore.neoforge.client.MeshBakery.Spec(null, true, 0, true);
    /** Moving pieces bake without occlusion (many small meshes, rebuilt with each page): quick. */
    private static final dev.mcmod.aerocore.neoforge.client.MeshBakery.Spec PIECE = new dev.mcmod.aerocore.neoforge.client.MeshBakery.Spec(null, false, 0, false);

    private static dev.mcmod.aerocore.neoforge.client.MeshBakery.Baked baked(PartMesh mesh, dev.mcmod.aerocore.neoforge.client.MeshBakery.Spec spec) {
        return dev.mcmod.aerocore.neoforge.client.MeshBakery.get(mesh, () -> mesh, spec);
    }

    /**
     * 0.11: draws the model from its baked meshes (finished textures, ambient occlusion), each moving piece placed
     * as EngineModel.emit places it; false (nothing drawn) until every bake it needs is done.
     */
    static boolean drawBaked(EngineModel m, PoseStack pose, double angle, Vec3d viewer, MultiBufferSource buffers, boolean doorsOpen) {
        boolean ready = true;
        var fixed = baked(m.fixed, WHOLE);
        ready &= fixed != null;
        var halves = new java.util.ArrayList<dev.mcmod.aerocore.neoforge.client.MeshBakery.Baked>();
        for (EngineModel.Section s : m.sections) {
            var behind = baked(s.behind(), WHOLE);
            var front = baked(s.front(), WHOLE);   // both, so turning the view needs no new bake
            ready &= behind != null && front != null;
            halves.add(s.normal().dot(viewer) > 0 ? behind : front);
        }
        var pieces = new java.util.ArrayList<dev.mcmod.aerocore.neoforge.client.MeshBakery.Baked>();
        for (EngineModel.Piece p : m.pieces) { var b = baked(p.mesh(), PIECE); ready &= b != null; pieces.add(b); }
        if (!ready) { return false; }
        int light = LightTexture.FULL_BRIGHT, all = dev.mcmod.aerocore.neoforge.client.PartLayers.ALL;
        var layers = new dev.mcmod.aerocore.neoforge.client.PartLayers();
        // 0.12: every baked piece (a body's doors, its controls), posed (the doors open or shut, the rest at rest)
        java.util.Map<String, Double> values = java.util.Map.of("doors", doorsOpen ? 1.0 : 0.0);
        for (int p = 0; p < fixed.pieceCount(); p++) { drawPiece(layers, fixed.piece(p, 0), m.posed.get(p), values, pose, light); }
        for (var h : halves) { for (int p = 0; p < h.pieceCount(); p++) { drawPiece(layers, h.piece(p, 0), m.posed.get(p), values, pose, light); } }
        for (int i = 0; i < m.pieces.size(); i++) {
            EngineModel.Placement place = m.pieces.get(i).at().apply(angle);
            pose.pushPose();
            pose.translate(place.offset().x(), place.offset().y(), place.offset().z());
            dev.mcmod.aerocore.api.Orientation r = place.rotation();
            pose.mulPose(new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
            if (place.stretch() != 1) { pose.scale(1, (float) place.stretch(), 1); }
            layers.draw(pieces.get(i).level(0), pose, light, 0xffffffff, true, all);
            pose.popPose();
        }
        layers.flush(buffers);
        return true;
    }

    /** A baked piece drawn where its pose puts it (as built without one). */
    private static void drawPiece(dev.mcmod.aerocore.neoforge.client.PartLayers layers, dev.mcmod.aerocore.api.mesh.PackedMesh mesh,
                                  java.util.function.Function<java.util.Map<String, Double>, EngineModel.Placement> posed, java.util.Map<String, Double> values,
                                  PoseStack pose, int light) {
        int all = dev.mcmod.aerocore.neoforge.client.PartLayers.ALL;
        if (posed == null) { layers.draw(mesh, pose, light, 0xffffffff, true, all); return; }
        EngineModel.Placement place = posed.apply(values);
        pose.pushPose();
        pose.translate(place.offset().x(), place.offset().y(), place.offset().z());
        dev.mcmod.aerocore.api.Orientation r = place.rotation();
        pose.mulPose(new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
        layers.draw(mesh, pose, light, 0xffffffff, true, all);
        pose.popPose();
    }

    void render(GuiGraphics g, int x, int y, int w, int h, PartDesign d, String page) {
        g.fillGradient(x, y, x + w, y + h, 0xe0353d47, 0xe0101317);
        g.renderOutline(x, y, w, h, 0x40FFFFFF);
        if (!draws(d)) { return; }
        if (d != modelFor || !page.equals(modelPage) || cutaway != modelCutaway) { rebuild(d, page); }
        if (failed) { return; }
        DecalCache.wantFor(d);
        long now = Util.getMillis();
        if (lastFrame != 0 && now - lastTouch > IDLE * 1000 && !held) { yaw += (float) (SPIN * (now - lastFrame) / 1000.0); }
        lastFrame = now;
        double angle = 2 * Math.PI * CRANK * (now % 1_000_000) / 1000.0;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        PoseStack pose = g.pose();
        pose.pushPose();
        float scale = (float) (Math.min(w, 1.3 * h) * 0.8 / size * zoom);
        pose.translate(x + w / 2.0, y + h / 2.0 + 0.04 * h, 250);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        Vec3d about = focus != null ? focus : centre;
        pose.translate(-about.x(), -about.y(), -about.z());
        // the direction to the viewer in the engine's frame: the cut casings leave out the half on that side
        Quaternionf view = new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
        Vector3f toViewer = view.conjugate(new Quaternionf()).transform(new Vector3f(0, 0, 1));
        Vec3d viewer = new Vec3d(toViewer.x(), toViewer.y(), toViewer.z());
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        if (drawBaked(model, pose, angle, viewer, buffers, doorsOpen)) {
            // 0.11: the baked meshes (finishes, occlusion); the floor over them
            floor.emit(buffers.getBuffer(RenderType.entityTranslucent(PartMesh.TEXTURE)), pose, LightTexture.FULL_BRIGHT, true);
            g.flush();
        } else {
            // while the bakes are not done yet (a frame or a few after a change): the meshes as built
            model.emit(buffers.getBuffer(RenderType.entityCutoutNoCull(PartMesh.TEXTURE)), pose, LightTexture.FULL_BRIGHT, angle, viewer, false);
            g.flush();
            var translucent = buffers.getBuffer(RenderType.entityTranslucent(PartMesh.TEXTURE));
            floor.emit(translucent, pose, LightTexture.FULL_BRIGHT, true);
            model.emit(translucent, pose, LightTexture.FULL_BRIGHT, angle, viewer, true);
            g.flush();
        }
        pose.popPose();
        Lighting.setupFor3DItems();
        g.disableScissor();
    }

}
