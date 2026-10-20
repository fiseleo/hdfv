package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.RotorBlur;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.AirframeModel;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.air.WingSurfaceDesign;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Wings, propellers and rotors on AeroCore's part vehicles, drawn from their designs (AirframeModel): a wing's skin as its
 * static mesh and its flaps, elevators, rudders and ailerons at the vehicle's inputs; a propeller's blades and a rotor's
 * at their rotor's angle. Meshes are built once per design. 0.11: blades baked with finishes; spinning fast, they blur
 * into a see-through disc (AeroCore's RotorBlur with the propeller-blur finish).
 * <p>
 * 0.12: all baked with the part, as pieces placed every frame: a wing's control surfaces turned about their hinges; a
 * propeller's hub and one blade drawn for each blade, pitched by the propeller's shown blade setting (a governed
 * propeller's changes with its rpm); a rotor's hub, one blade drawn for each, each flapped and pitched at its azimuth
 * by the rotor's shown flapping and pitch (beta = b0 + b1c cos psi + b1s sin psi, psi from aft), and its swashplate
 * raised by the collective and tilted by the cyclic; the blur disc tilted with the tip-path plane.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AirframeRenderers {
    private AirframeRenderers() {}

    private record Surface(WingSurfaceDesign design, int side, PartMesh mesh, Vec3d hinge, Vec3d axis) {}
    private record BuiltWing(PartMesh skin, List<Surface> surfaces) {}

    private static final Map<WingDesign, BuiltWing> WINGS = Collections.synchronizedMap(new WeakHashMap<>());
    /** A key per propeller or rotor design for its baked blades (0.11: finishes, occlusion). */
    private static final Map<Object, Object> SPINNING = Collections.synchronizedMap(new WeakHashMap<>());
    /** The blur's colour: a light grey (a spinning disc reads as a haze of the blades' paint and the light through it). */
    private static final int BLADE = 0xffa9aeb4;

    /**
     * 0.11: the blades at their angle (baked once), fading out as they blur into a disc (RotorBlur): opaque until
     * the disc is half in, then left to the disc.
     */
    private static void spin(Object design, java.util.function.Supplier<PartMesh> blades, double turnsPerSecond, PoseStack stack, PartLayers layers, int light,
                             double angle, Axis axis) {
        if (RotorBlur.amount(turnsPerSecond) >= 0.5) { return; }
        Object key = SPINNING.computeIfAbsent(design, k -> new Object());
        MeshBakery.Baked baked = MeshBakery.get(key, () -> dev.mcmod.hdfv.neoforge.client.model.Looks.finish(blades.get()), MeshBakery.Spec.WORLD);
        stack.pushPose();
        stack.mulPose(axis.rotation((float) angle));
        if (baked != null) { layers.draw(baked.level(0), stack, light); }
        else { blades.get().emit(layers.legacy(), stack, light); }
        stack.popPose();
    }

    private static BuiltWing wing(WingDesign d) {
        return WINGS.computeIfAbsent(d, k -> {
            PartMesh skin = new PartMesh();
            List<Surface> surfaces = new ArrayList<>();
            AirframeModel.wing(k, null, skin, (s, side, mesh, a, b) -> surfaces.add(new Surface(s, side, mesh, a, b.subtract(a).normalized())));
            return new BuiltWing(skin, List.copyOf(surfaces));
        });
    }

    /**
     * 0.13: a mesh's quads added as a piece of a part's mesh. MeshBuilder.add keeps each quad's own tags (it does not
     * stamp the pens), so out.piece(n).add(...) left 0.12's control surfaces, propeller and rotor parts on piece 0:
     * baked fixed (control surfaces where their hinge-relative mesh lies, by the wing's root), and the turning parts drawn
     * twice (fixed, and again by the older path the missing pieces fell back to).
     */
    private static void asPiece(PartMesh out, PartMesh mesh, int piece) {
        int shift = Integer.numberOfTrailingZeros(dev.mcmod.aerocore.api.mesh.MeshBuilder.PIECE);
        for (var q : mesh.quads()) { out.quads().add(q.withTags((q.tags() & ~dev.mcmod.aerocore.api.mesh.MeshBuilder.PIECE) | (piece << shift))); }
        out.lamps().addAll(mesh.lamps());
    }

    private static Quaternionf quaternion(dev.mcmod.aerocore.api.Orientation r) { return new Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()); }

    /** A baked piece drawn turned about a point. */
    private static void turned(PartLayers layers, dev.mcmod.aerocore.api.mesh.PackedMesh mesh, Vec3d about, dev.mcmod.aerocore.api.Orientation turn, PoseStack stack, int light) {
        stack.pushPose();
        stack.translate(about.x(), about.y(), about.z());
        stack.mulPose(quaternion(turn));
        stack.translate(-about.x(), -about.y(), -about.z());
        layers.draw(mesh, stack, light);
        stack.popPose();
    }

    private static dev.mcmod.aerocore.api.Orientation about(Vec3d axis, double angle) { return dev.mcmod.aerocore.api.Orientation.rotationVector(axis.scale(angle)); }

    /** A control surface's deflection now (rad), an aileron the other way on the right. */
    private static double deflection(Surface s, PartVehicle entity, String partId) {
        double input = entity.input(partId, s.design().input());
        return AirframeModel.deflection(s.design(), s.design().role() == WingSurfaceDesign.Role.AILERON && s.side() > 0 ? -input : input);
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(WingDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                BuiltWing w = wing((WingDesign) part.design());
                out.add(w.skin(), dev.mcmod.aerocore.api.Orientation.IDENTITY, Vec3d.ZERO);
                // 0.22: each control surface baked where it is at rest (its hinge-relative mesh moved to its hinge) and
                // turned about its hinge (animate): the bake shades and culls all the pieces as built, and built at their
                // hinges' first ends they lay together by the root, inside the wing once its skin is closed at the cove
                for (int i = 0; i < w.surfaces().size(); i++) {
                    Surface s = w.surfaces().get(i);
                    asPiece(out, new PartMesh().add(s.mesh(), dev.mcmod.aerocore.api.Orientation.IDENTITY, s.hinge()), i + 1);
                }
                // 0.12: a main wing (one with ailerons) carries the navigation lights and strobes at its tips
                if (w.surfaces().stream().anyMatch(s -> s.design().role() == WingSurfaceDesign.Role.AILERON)) {
                    for (int side : new int[]{-1, 1}) {
                        Vec3d tip = null;
                        double extreme = 0;
                        for (var q : w.skin().quads()) {
                            for (Vec3d v : new Vec3d[]{q.a(), q.b(), q.c(), q.d()}) { extreme = Math.max(extreme, v.z() * side); }
                        }
                        for (var q : w.skin().quads()) {
                            for (Vec3d v : new Vec3d[]{q.a(), q.b(), q.c(), q.d()}) {
                                if (v.z() * side > extreme - 0.01 && (tip == null || v.x() > tip.x())) { tip = v; }
                            }
                        }
                        if (tip != null) { dev.mcmod.hdfv.neoforge.client.model.VehicleLamps.wingtip(out, tip.add(new Vec3d(-0.08, 0, 0)), side); }
                    }
                }
            }

            @Override public void lamps(Part part, PartVehicle entity, String partId, float partialTick, List<dev.mcmod.aerocore.api.mesh.Lamp> lamps, float[] level) {
                dev.mcmod.hdfv.neoforge.client.model.VehicleLamps.levels(lamps, level, dev.mcmod.hdfv.neoforge.client.model.VehicleLamps.inputs(entity, partialTick));
            }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                // 0.13: its stickers' pictures (defined before the flush that draws them)
                if (drawn == null || drawn.level() <= 1) { DecalCache.wantFor(part.design()); }
                if (drawn == null) { animate(part, entity, partId, firstRotor, partialTick, stack, layers, light); return; }
                List<Surface> surfaces = wing((WingDesign) part.design()).surfaces();
                for (int i = 0; i < surfaces.size() && i + 1 < drawn.pieces(); i++) {
                    Surface s = surfaces.get(i);
                    turned(layers, drawn.piece(i + 1), s.hinge(), about(s.axis(), deflection(s, entity, partId)), stack, light);
                }
            }
            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, VertexConsumer out, int light) {
                for (Surface s : wing((WingDesign) part.design()).surfaces()) {
                    double input = entity.input(partId, s.design().input());
                    double delta = AirframeModel.deflection(s.design(), s.design().role() == WingSurfaceDesign.Role.AILERON && s.side() > 0 ? -input : input);
                    stack.pushPose();
                    stack.translate(s.hinge().x(), s.hinge().y(), s.hinge().z());
                    stack.mulPose(new Quaternionf().rotateAxis((float) delta, (float) s.axis().x(), (float) s.axis().y(), (float) s.axis().z()));
                    s.mesh().emit(out, stack, light);
                    stack.popPose();
                }
            }
        });
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(AirPropellerDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                AirPropellerDesign d = (AirPropellerDesign) part.design();
                asPiece(out, AirframeModel.propellerHub(d, null), 1);
                asPiece(out, AirframeModel.propellerBlade(d, null, 0), 2);
                out.piece(0);
            }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                if (drawn == null || drawn.pieces() < 3) { animate(part, entity, partId, firstRotor, partialTick, stack, layers, light); return; }
                AirPropellerDesign d = (AirPropellerDesign) part.design();
                var b = d.blades();
                double turns = entity.rotorSpeed(firstRotor) / (2 * Math.PI), r = b.radiusM(), angle = entity.rotorAngle(firstRotor, partialTick);
                double solidity = b.blades() * (b.rootChordRatio() + b.tipChordRatio()) / Math.PI, setting = entity.shown(partId, 0, partialTick);
                if (RotorBlur.amount(turns) < 0.5) {
                    turned(layers, drawn.piece(1), Vec3d.ZERO, about(Vec3d.X, angle), stack, light);
                    for (int k = 0; k < b.blades(); k++) {
                        // turned to its place about the axis, then pitched by the setting about its own span (+Y)
                        turned(layers, drawn.piece(2), Vec3d.ZERO, about(Vec3d.X, angle + 2 * Math.PI * k / b.blades()).multiply(about(Vec3d.Y, setting)), stack, light);
                    }
                }
                RotorBlur.draw(layers, stack, b.hubRatio() * r, r, solidity, BLADE, turns, Finishes.FX_PROP_BLUR, light);
            }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light) {
                AirPropellerDesign d = (AirPropellerDesign) part.design();
                var b = d.blades();
                double turns = entity.rotorSpeed(firstRotor) / (2 * Math.PI), r = b.radiusM();
                double solidity = b.blades() * (b.rootChordRatio() + b.tipChordRatio()) / Math.PI;
                spin(d, () -> AirframeModel.propeller(d, null, 0), turns, stack, layers, light, entity.rotorAngle(firstRotor, partialTick), Axis.XP);
                RotorBlur.draw(layers, stack, b.hubRatio() * r, r, solidity, BLADE, turns, Finishes.FX_PROP_BLUR, light);
            }
        });
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(RotorDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                RotorDesign d = (RotorDesign) part.design();
                out.add(AirframeModel.mast(d, null), dev.mcmod.aerocore.api.Orientation.IDENTITY, Vec3d.ZERO);
                asPiece(out, AirframeModel.rotorHub(d, null), 1);
                asPiece(out, AirframeModel.rotorBlade(d, null), 2);
                asPiece(out, AirframeModel.swashplate(d), 3);
                out.piece(0);
            }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                if (drawn == null || drawn.pieces() < 4) { animate(part, entity, partId, firstRotor, partialTick, stack, layers, light); return; }
                RotorDesign d = (RotorDesign) part.design();
                var b = d.blades();
                double turns = entity.rotorSpeed(firstRotor) / (2 * Math.PI), r = b.radiusM(), angle = entity.rotorAngle(firstRotor, partialTick);
                double solidity = b.blades() * 0.5 * (b.rootChordM() + b.tipChordM()) / (Math.PI * r);
                double sense = d.hub().clockwise() ? -1 : 1, pitchSense = d.hub().clockwise() ? -1 : 1;
                double[] v = new double[6];
                for (int i = 0; i < 6; i++) { v[i] = entity.shown(partId, i, partialTick); }
                // the swashplate: up with the collective, tilted by the cyclic
                Vec3d plate = new Vec3d(0, -0.06 * r, 0);
                stack.pushPose();
                stack.translate(0, 0.6 * v[3] * 0.06 * r, 0);
                turned(layers, drawn.piece(3), plate, about(Vec3d.Z, -v[4]).multiply(about(Vec3d.X, sense * v[5])), stack, light);
                stack.popPose();
                if (RotorBlur.amount(turns) < 0.5) {
                    turned(layers, drawn.piece(1), Vec3d.ZERO, about(Vec3d.Y, sense * angle), stack, light);
                    for (int k = 0; k < b.blades(); k++) {
                        double a = angle + 2 * Math.PI * k / b.blades(), psi = a + Math.PI;
                        double beta = v[0] + v[1] * Math.cos(psi) + v[2] * Math.sin(psi), theta = v[3] + v[4] * Math.cos(psi) + v[5] * Math.sin(psi);
                        var turn = about(Vec3d.Y, sense * a).multiply(about(Vec3d.Z, beta)).multiply(about(Vec3d.X, pitchSense * theta));
                        turned(layers, drawn.piece(2), Vec3d.ZERO, turn, stack, light);
                    }
                }
                // the blur disc in the tip-path plane: through the tips at azimuths 0 and 90 degrees (from aft)
                Vec3d p0 = about(Vec3d.Y, sense * (0 - Math.PI)).rotate(Vec3d.X).scale(r).add(new Vec3d(0, r * v[1], 0));
                Vec3d p90 = about(Vec3d.Y, sense * (0.5 * Math.PI - Math.PI)).rotate(Vec3d.X).scale(r).add(new Vec3d(0, r * v[2], 0));
                Vec3d n = p0.cross(p90);
                if (n.y() < 0) { n = n.scale(-1); }
                stack.pushPose();
                if (n.lengthSquared() > 1e-12) {
                    Vec3d axis = Vec3d.Y.cross(n.normalized());
                    double tilt = Math.asin(Math.min(1, axis.length()));
                    if (tilt > 1e-6) { stack.mulPose(quaternion(about(axis.normalized(), tilt))); }
                }
                stack.translate(0, r * v[0] * 0.5, 0);
                stack.mulPose(Axis.ZP.rotation((float) (Math.PI / 2)));   // RotorBlur's disc is square to x; the rotor's to y
                RotorBlur.draw(layers, stack, b.rootCutout() * r, r, solidity, BLADE, turns, Finishes.FX_PROP_BLUR, light);
                stack.popPose();
            }
            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light) {
                RotorDesign d = (RotorDesign) part.design();
                var b = d.blades();
                double turns = entity.rotorSpeed(firstRotor) / (2 * Math.PI), r = b.radiusM();
                double solidity = b.blades() * 0.5 * (b.rootChordM() + b.tipChordM()) / (Math.PI * r);
                // the rotor's own axis is -Y when it turns clockwise seen from above
                spin(d, () -> AirframeModel.rotor(d, null, Math.toRadians(2.5)), turns, stack, layers, light, entity.rotorAngle(firstRotor, partialTick),
                        d.hub().clockwise() ? Axis.YN : Axis.YP);
                stack.pushPose();
                stack.mulPose(Axis.ZP.rotation((float) (Math.PI / 2)));   // RotorBlur's disc is square to x; the rotor's to y
                RotorBlur.draw(layers, stack, b.rootCutout() * r, r, solidity, BLADE, turns, Finishes.FX_PROP_BLUR, light);
                stack.popPose();
            }
        });
    }
}
