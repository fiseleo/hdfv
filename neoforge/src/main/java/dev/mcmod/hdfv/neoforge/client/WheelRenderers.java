package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.GearboxModel;
import dev.mcmod.hdfv.neoforge.client.model.WheelModel;
import dev.mcmod.hdfv.neoforge.client.model.WheelRig;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Axles, landing gear, gearboxes and transfer cases on AeroCore's part vehicles, drawn from their designs (WheelModel,
 * GearboxModel). Each wheel goes where the server says (AxlePart's and LegPart's shown values: spin, steer, compression
 * per wheel): turned by its steering, rolled by its spin, at its hub for its compression, the suspension's links drawn
 * to it; an oleo's piston slides with the compression. Wheel meshes are built once per design.
 * <p>
 * 0.12: the corners are WheelRig's rigid pieces, baked once with the part (finishes, occlusion, the GPU cache) and
 * placed each frame; a retractable leg folds by its shown fraction. Until the bake is ready, the 0.11 drawing.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WheelRenderers {
    private WheelRenderers() {}

    /** A design's wheel meshes: what turns and what stays with the knuckle, for the right side and mirrored for the left. */
    private record Wheels(PartMesh turningRight, PartMesh stillRight, PartMesh turningLeft, PartMesh stillLeft) {}

    private static final Map<Object, Wheels> WHEELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, PartMesh> STATIC = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, WheelRig.Built> RIGS = Collections.synchronizedMap(new WeakHashMap<>());

    private static WheelRig.Built rig(Object design) {
        return RIGS.computeIfAbsent(design, k -> k instanceof AxleDesign a ? WheelRig.axle(a) : WheelRig.leg((LandingGearDesign) k));
    }

    /** Draws each baked piece where the rig puts it now. */
    private static void pieces(WheelRig.Built rig, WheelRig.State state, PartRenderer.Drawn drawn, PoseStack stack, PartLayers layers, int light) {
        for (int p = 1; p < drawn.pieces() && p - 1 < rig.motions().size(); p++) {
            WheelRig.Pose pose = rig.pose(p, state);
            stack.pushPose();
            stack.translate(pose.to().x(), pose.to().y(), pose.to().z());
            stack.mulPose(quaternion(pose.turn()));
            if (pose.stretch() != 1) { stack.scale(1, (float) pose.stretch(), 1); }
            stack.mulPose(quaternion(pose.from()));
            stack.translate(-pose.at().x(), -pose.at().y(), -pose.at().z());
            layers.draw(drawn.piece(p), stack, light);
            stack.popPose();
        }
    }

    private static org.joml.Quaternionf quaternion(Orientation r) { return new org.joml.Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()); }

    private static Wheels wheels(AxleDesign d) {
        return WHEELS.computeIfAbsent(d, k -> {
            PartMesh t = WheelModel.nearest(WheelModel.turning(d.tyre(), d.wheel(), d.brake(), null, false)), s = WheelModel.still(d.tyre(), d.wheel(), d.brake(), null, false);
            return new Wheels(t, s, WheelModel.mirroredZ(t), WheelModel.mirroredZ(s));
        });
    }

    private static Wheels wheels(LandingGearDesign d) {
        return WHEELS.computeIfAbsent(d, k -> {
            PartMesh t = WheelModel.nearest(WheelModel.turning(d.tyre(), d.wheel(), d.brake(), null, true)), s = WheelModel.still(d.tyre(), d.wheel(), d.brake(), null, true);
            return new Wheels(t, s, WheelModel.mirroredZ(t), WheelModel.mirroredZ(s));
        });
    }

    /** Draws a mesh at a hub turned by a steering angle about the vertical, then (the turning part) rolled by its spin. */
    private static void wheel(PoseStack stack, VertexConsumer out, int light, Vec3d hub, double steer, double spin, PartMesh still, PartMesh turning) {
        stack.pushPose();
        stack.translate(hub.x(), hub.y(), hub.z());
        stack.mulPose(Axis.YP.rotation((float) steer));
        still.emit(out, stack, light);
        stack.mulPose(Axis.ZP.rotation((float) -spin));
        turning.emit(out, stack, light);
        stack.popPose();
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(AxleDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { out.add(rig(part.design()).mesh(), Orientation.IDENTITY, Vec3d.ZERO); }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                if (drawn == null) { animate(part, entity, partId, firstRotor, partialTick, stack, layers, light); return; }
                double[] c = {entity.shown(partId, 2, partialTick), entity.shown(partId, 5, partialTick)};
                double[] steer = {entity.shown(partId, 1, partialTick), entity.shown(partId, 4, partialTick)};
                double[] spin = {entity.shown(partId, 0, partialTick), entity.shown(partId, 3, partialTick)};
                pieces(rig(part.design()), new WheelRig.State(c, steer, spin, 0), drawn, stack, layers, light);
            }
            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, VertexConsumer out, int light) {
                AxleDesign d = (AxleDesign) part.design();
                Wheels w = wheels(d);
                double[] c = {entity.shown(partId, 2, partialTick), entity.shown(partId, 5, partialTick)};
                for (int i = 0; i < 2; i++) {
                    int side = i == 0 ? -1 : 1;
                    double spin = entity.shown(partId, 3 * i, partialTick), steer = entity.shown(partId, 3 * i + 1, partialTick);
                    WheelModel.corner(d, side, c[i], c[1 - i], steer, null).emit(out, stack, light);
                    wheel(stack, out, light, WheelModel.hub(d, side, c[i]), steer, spin, i == 0 ? w.stillLeft() : w.stillRight(), i == 0 ? w.turningLeft() : w.turningRight());
                }
            }
        });
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(LandingGearDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { out.add(rig(part.design()).mesh(), Orientation.IDENTITY, Vec3d.ZERO); }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                          Drawn drawn) {
                if (drawn == null) { animate(part, entity, partId, firstRotor, partialTick, stack, layers, light); return; }
                int folded = dev.mcmod.aerocore.api.wheel.WheelCorner.SHOWN + dev.mcmod.aerocore.api.wheel.WheelCorner.EXTRA;
                double c = entity.shown(partId, 2, partialTick);
                WheelRig.State st = new WheelRig.State(new double[]{c, c}, new double[]{entity.shown(partId, 1, partialTick), 0},
                        new double[]{entity.shown(partId, 0, partialTick), 0}, Math.clamp(entity.shown(partId, folded, partialTick), 0, 1));
                pieces(rig(part.design()), st, drawn, stack, layers, light);
            }

            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, VertexConsumer out, int light) {
                LandingGearDesign d = (LandingGearDesign) part.design();
                Wheels w = wheels(d);
                double spin = entity.shown(partId, 0, partialTick), steer = entity.shown(partId, 1, partialTick), c = entity.shown(partId, 2, partialTick);
                // a steered or castering leg turns about its strut: the whole leg below the mount with the wheel
                stack.pushPose();
                stack.mulPose(Axis.YP.rotation((float) steer));
                WheelModel.leg(d, c, null).emit(out, stack, light);
                boolean left = d.leg().side().sign < 0;
                wheel(stack, out, light, new Vec3d(0, -d.leg().lengthM() + c, 0), 0, spin, left ? w.stillLeft() : w.stillRight(), left ? w.turningLeft() : w.turningRight());
                stack.popPose();
            }
        });
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(GearboxDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                out.add(STATIC.computeIfAbsent(part.design(), k -> GearboxModel.mesh((GearboxDesign) k)), Orientation.IDENTITY, Vec3d.ZERO);
            }
        });
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(TransferCaseDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                out.add(STATIC.computeIfAbsent(part.design(), k -> GearboxModel.mesh((TransferCaseDesign) k)), Orientation.IDENTITY, Vec3d.ZERO);
            }
        });
    }
}
