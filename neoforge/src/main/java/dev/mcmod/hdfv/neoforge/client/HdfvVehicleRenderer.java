package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.client.PartVehicleRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.assembly.BuildSequence;
import dev.mcmod.hdfv.assembly.CraneRunway;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.assembly.RoomControllerBlockEntity;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A vehicle in its room drawn as far as it is built (0.8; BuildSequence): a room-built part's frames, the hull's own
 * section at stations along it, going up from the nose; its skin closing over them from the nose back; the other parts
 * in place once fitted, or swinging in on the crane's hook (or carried in) while hoisted; scaffolding round it all.
 * Assembled, it is drawn as any vehicle (PartVehicleRenderer).
 * <p>
 * 0.12: drawn through AeroCore's bakes: a room-built part's frames (as many as are up) and its skin (in eighths) each
 * baked once from the part's world mesh, its finishes kept; the scaffolding baked once a vehicle; a fitted or hoisted
 * part drawn as the world draws it (its own bake and its moving pieces: wheels, propellers, rotors show while it is
 * built), from the GPU.
 */
public final class HdfvVehicleRenderer extends PartVehicleRenderer {
    private static final int PRIMER = 0xff8a5a3c, POLE = 0xff9aa0a6, PLANK = 0xff9c7a4a;
    /** Frames every this far along a part, m, and half their width; a skin goes on in this many steps. */
    private static final double FRAME_SPACING = 0.6, FRAME_HALF = 0.04, HEADROOM = 2.5;
    private static final int SKIN_STEPS = 8;

    public HdfvVehicleRenderer(EntityRendererProvider.Context c) { super(c); }

    /** A part's world mesh in its own frame, by part (the frames and skin are cut from it). */
    private static final Map<Part, PartMesh> WHOLE = new WeakHashMap<>();
    /** Each vehicle's box (its parts' meshes at their mounts), vehicle frame. */
    private static final Map<Vehicle, Vec3d[]> BOUNDS = new WeakHashMap<>();

    /**
     * The meshes built for a part's stages ("frames 5", "skin 3") and each vehicle's scaffolding, held here: they are
     * MeshBakery's keys, which it keeps weakly (a key made anew each frame was let go and baked again).
     */
    private static final Map<Part, Map<String, PartMesh>> STAGES = new WeakHashMap<>();
    private static final Map<Vehicle, PartMesh> SCAFFOLD = new WeakHashMap<>();

    private static synchronized PartMesh stage(Part part, String what, java.util.function.Supplier<PartMesh> build) {
        return STAGES.computeIfAbsent(part, p -> new java.util.HashMap<>()).computeIfAbsent(what, w -> build.get());
    }

    private static synchronized PartMesh whole(Part part) {
        return WHOLE.computeIfAbsent(part, p -> {
            PartRenderer r = PartRenderer.of(p);
            PartMesh local = new PartMesh();
            if (r != null) { r.mesh(p, local); }
            return local;
        });
    }

    @Override public void render(PartVehicle entity, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
        if (!(entity instanceof HdfvVehicle hv) || hv.assembled() || entity.vehicle() == null || entity.blueprint() == null) {
            super.render(entity, yaw, partialTick, stack, buffers, light);
            return;
        }
        Vehicle v = entity.vehicle();
        double built = hv.built();
        BuildSequence seq = RoomControllerBlockEntity.sequence(entity.blueprint());
        BuildSequence.Step step = seq.at(built);
        String hoisted = step != null && step.kind() == BuildSequence.Kind.HOIST ? step.part() : null;
        stack.pushPose();
        Orientation q = entity.visualOrientation(partialTick);
        stack.mulPose(new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()));
        Vec3d com = v.properties().centerOfMassLocal();
        stack.translate(-com.x(), -com.y(), -com.z());
        PartLayers layers = new PartLayers().gpu(true).late(true);
        Vec3d[] box = bounds(v);
        int rotor = 0;
        for (Vehicle.Installed in : v.installed()) {
            Part part = in.part();
            PartRenderer r = PartRenderer.of(part);
            int first = rotor;
            rotor += part.rotors().size();
            if (r == null) { continue; }
            if (Plans.of(part.design()).roomBuilt()) {
                double frames = seq.progress(in.id(), BuildSequence.Kind.SKELETON, built), skin = seq.progress(in.id(), BuildSequence.Kind.SKIN, built);
                if (frames <= 0) { continue; }
                stack.pushPose();
                mount(stack, in);
                if (skin >= 1) { drawPart(entity, in, r, first, partialTick, stack, layers, light); }
                else { roomBuilt(part, frames, skin, stack, layers, light); }
                stack.popPose();
            } else if (in.id().equals(hoisted)) {
                // swinging in on the hook from beside the vehicle
                Vec3d drop = in.mount().position(), pick = new Vec3d(drop.x(), box[0].y(), box[1].z() + 1.5);
                Vec3d hook = CraneRunway.hook(pick, drop, HEADROOM, step.within(built));
                stack.pushPose();
                stack.translate(hook.x() - drop.x(), hook.y() - drop.y(), hook.z() - drop.z());
                mount(stack, in);
                drawPart(entity, in, r, first, partialTick, stack, layers, light);
                stack.popPose();
            } else if (seq.placed(in.id(), built)) {
                stack.pushPose();
                mount(stack, in);
                drawPart(entity, in, r, first, partialTick, stack, layers, light);
                stack.popPose();
            }
        }
        PartMesh poles;
        synchronized (SCAFFOLD) { poles = SCAFFOLD.computeIfAbsent(v, k -> scaffold(box)); }
        MeshBakery.Baked scaffold = MeshBakery.get(poles, () -> poles, MeshBakery.Spec.WORLD);
        if (scaffold != null) { layers.draw(scaffold.level(0), stack, light); }
        stack.popPose();
        layers.flush(buffers);
    }

    /** A part as the world draws it: its bake (near) and its moving pieces at rest. */
    private static void drawPart(PartVehicle entity, Vehicle.Installed in, PartRenderer r, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light) {
        Part part = in.part();
        MeshBakery.Baked baked = MeshBakery.get(part, () -> {
            PartMesh local = new PartMesh();
            r.mesh(part, local);
            return local;
        }, r.bake(part));
        PartRenderer.Drawn drawn = null;
        if (baked != null) {
            drawn = new PartRenderer.Drawn(baked, 0);
            if (baked.radius() > 0) { layers.draw(baked.level(0), stack, light); }
        }
        r.animate(part, entity, in.id(), firstRotor, partialTick, stack, layers, light, drawn);
    }

    /** A room-built part going up: its frames (in primer) as far as they are up, the skin (in eighths) from its nose back. */
    private static void roomBuilt(Part part, double frames, double skin, PoseStack stack, PartLayers layers, int light) {
        PartMesh whole = whole(part);
        Vec3d[] b = whole.bounds();
        if (b == null) { return; }
        double x0 = b[0].x(), x1 = b[1].x(), length = Math.max(x1 - x0, 1e-3);
        int stations = Math.max(2, (int) Math.ceil(length / FRAME_SPACING)), up = Math.min(stations, (int) Math.ceil(frames * stations));
        PartMesh frameMesh = stage(part, "frames " + up, () -> {
            PartMesh m = new PartMesh();
            for (int i = 0; i < up; i++) {
                double x = x1 - (i + 0.5) * length / stations;
                PartMesh slab = whole.clipped(new Vec3d(x - FRAME_HALF, 0, 0), new Vec3d(-1, 0, 0)).clipped(new Vec3d(x + FRAME_HALF, 0, 0), Vec3d.X);
                for (PartMesh.Quad q : slab.quads()) { m.quad(q.a(), q.b(), q.c(), q.d(), PRIMER, q.normal()); }
            }
            // the keel, along the bottom
            double keel = x1 - frames * length, cz = 0.5 * (b[0].z() + b[1].z());
            m.box(new Vec3d(Math.min(keel, x1 - 0.1), b[0].y(), cz - 0.05), new Vec3d(x1, b[0].y() + 0.08, cz + 0.05), PRIMER);
            return Looks.finish(m);
        });
        MeshBakery.Baked skeleton = MeshBakery.get(frameMesh, () -> frameMesh, MeshBakery.Spec.WORLD);
        if (skeleton != null) { layers.draw(skeleton.level(0), stack, light); }
        int eighths = (int) Math.floor(skin * SKIN_STEPS);
        if (eighths > 0) {
            PartMesh skinMesh = stage(part, "skin " + eighths, () -> {
                PartMesh m = new PartMesh();
                m.add(whole.clipped(new Vec3d(x1 - eighths * length / SKIN_STEPS, 0, 0), new Vec3d(-1, 0, 0)), Orientation.IDENTITY, Vec3d.ZERO);
                return m;
            });
            MeshBakery.Baked skinned = MeshBakery.get(skinMesh, () -> skinMesh, MeshBakery.Spec.WORLD);
            if (skinned != null) { layers.draw(skinned.level(0), stack, light); }
        }
    }

    private static void mount(PoseStack stack, Vehicle.Installed installed) {
        Vec3d p = installed.mount().position();
        Orientation r = installed.rotation();
        stack.translate(p.x(), p.y(), p.z());
        stack.mulPose(new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
    }

    /** The vehicle's box (all its parts' meshes at their mounts), vehicle frame. */
    static synchronized Vec3d[] bounds(Vehicle v) {
        return BOUNDS.computeIfAbsent(v, k -> {
            Vec3d lo = null, hi = null;
            for (Vehicle.Installed in : k.installed()) {
                PartMesh m = whole(in.part());
                List<PartMesh.Quad> moved = m.quads().stream().map(q -> q.moved(in.rotation(), in.mount().position())).toList();
                Vec3d[] b = PartMesh.bounds(moved);
                if (b == null) { continue; }
                lo = lo == null ? b[0] : new Vec3d(Math.min(lo.x(), b[0].x()), Math.min(lo.y(), b[0].y()), Math.min(lo.z(), b[0].z()));
                hi = hi == null ? b[1] : new Vec3d(Math.max(hi.x(), b[1].x()), Math.max(hi.y(), b[1].y()), Math.max(hi.z(), b[1].z()));
            }
            return lo == null ? new Vec3d[]{Vec3d.ZERO, Vec3d.ZERO} : new Vec3d[]{lo, hi};
        });
    }

    /** Scaffolding along both sides: poles every two metres, rails and a walkway at mid height. */
    private static PartMesh scaffold(Vec3d[] b) {
        PartMesh m = new PartMesh();
        double y0 = b[0].y(), y1 = b[1].y() + 0.5, mid = 0.5 * (y0 + y1);
        for (double side : new double[]{b[0].z() - 0.7, b[1].z() + 0.7}) {
            for (double x = b[0].x() - 0.5; x <= b[1].x() + 0.51; x += 2) {
                m.box(new Vec3d(x - 0.03, y0, side - 0.03), new Vec3d(x + 0.03, y1, side + 0.03), POLE);
            }
            for (double y : new double[]{mid, y1 - 0.1}) {
                m.box(new Vec3d(b[0].x() - 0.5, y - 0.03, side - 0.03), new Vec3d(b[1].x() + 0.5, y + 0.03, side + 0.03), POLE);
            }
            double inner = side < b[0].z() ? side + 0.45 : side - 0.45;
            m.box(new Vec3d(b[0].x() - 0.5, mid - 0.06, Math.min(side, inner)), new Vec3d(b[1].x() + 0.5, mid - 0.03, Math.max(side, inner)), PLANK);
        }
        return Looks.finish(m);
    }
}
