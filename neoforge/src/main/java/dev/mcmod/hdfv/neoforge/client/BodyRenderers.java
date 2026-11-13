package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Part;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.BodyModel;
import dev.mcmod.hdfv.neoforge.client.model.EngineModel;
import dev.mcmod.hdfv.part.body.BodyDesign;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bodies of modules on AeroCore's part vehicles, drawn from their designs (BodyModel), whole and unmarked; built once per design.
 * And (0.8) fuel tanks: the box with its straps and filler cap; (0.15) chassis frames (ChassisModel).
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BodyRenderers {
    private BodyRenderers() {}

    /** A body's mesh and how its pieces move (BodyModel: doors, controls, wipers), built once per design. */
    private record Built(PartMesh mesh, Map<Integer, java.util.function.Function<Map<String, Double>, EngineModel.Placement>> posed) {}

    private static final Map<BodyDesign, Built> MESHES = Collections.synchronizedMap(new WeakHashMap<>());
    /** Per vehicle: how far its doors are open (0-1) and when that was worked out (ms). */
    private static final Map<PartVehicle, double[]> DOORS = Collections.synchronizedMap(new WeakHashMap<>());
    /** Seconds a door takes to open or shut, and a wiper's sweep up and back. */
    private static final double DOOR_S = 1.2, WIPE_S = 1.3;

    private static Built built(BodyDesign d) {
        return MESHES.computeIfAbsent(d, k -> {
            EngineModel m = BodyModel.build(k, null, false, false);
            return new Built(m.at(0), Map.copyOf(m.posed));
        });
    }

    /** The values a body's pieces are posed from: the controls, the doors eased toward their channel, the wipers' sweep. */
    private static Map<String, Double> values(PartVehicle entity, float partialTick) {
        Map<String, Double> values = new java.util.HashMap<>(entity.controls());
        long now = net.minecraft.Util.getMillis();
        double target = Math.clamp(values.getOrDefault("doors", 0.0), 0, 1);
        double[] door = DOORS.computeIfAbsent(entity, e -> new double[]{target, now});
        double dt = Math.clamp((now - door[1]) / 1000.0, 0, 0.25);
        door[1] = now;
        door[0] += Math.clamp(target - door[0], -dt / DOOR_S, dt / DOOR_S);
        values.put("doors", door[0]);
        // wipers: 1 on, 2 off, 0 automatic (on in the rain)
        long setting = Math.round(values.getOrDefault("wipers", 0.0));
        boolean on = setting == 1 || (setting == 0 && entity.level().isRainingAt(entity.blockPosition().above()));
        double phase = ((entity.level().getGameTime() + partialTick) * 0.05 / WIPE_S) % 1.0;
        values.put("wipe", on ? 0.5 * (1 - Math.cos(2 * Math.PI * phase)) : 0.0);
        return values;
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(BodyDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) { out.add(built((BodyDesign) part.design()).mesh(), Orientation.IDENTITY, Vec3d.ZERO); }

            /** 0.12: a cabin seen through windows a few voxels wide wants a finer occlusion grid than the body's size gives. */
            @Override public MeshBakery.Spec bake(Part part) { return MeshBakery.Spec.WORLD.voxel(0.04); }

            /** 0.12: the lamps by LampRules (head, tail and brake, reversing; beacons; navigation and boat lights). */
            @Override public void lamps(Part part, PartVehicle entity, String partId, float partialTick, java.util.List<dev.mcmod.aerocore.api.mesh.Lamp> lamps,
                                        float[] level) {
                dev.mcmod.hdfv.neoforge.client.model.VehicleLamps.levels(lamps, level, dev.mcmod.hdfv.neoforge.client.model.VehicleLamps.inputs(entity, partialTick));
                // 0.25: a body nine tenths used up has no lamp left whole
                if (entity != null && entity.used(partId, dev.mcmod.hdfv.damage.Conditions.STRUCTURE) >= 0.9) { java.util.Arrays.fill(level, 0f); }
            }

            /** 0.12: the doors, controls and wipers, baked as pieces, each where its pose puts it now. */
            @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers,
                                          int light, Drawn drawn) {
                // 0.13: its stickers' pictures (defined before the flush that draws them)
                if (drawn == null || drawn.level() <= 1) { DecalCache.wantFor(part.design()); }
                if (drawn == null || drawn.pieces() < 2) { return; }
                Built b = MESHES.get((BodyDesign) part.design());
                Map<String, Double> values = values(entity, partialTick);
                for (int p = 1; p < drawn.pieces(); p++) {
                    var posed = b == null ? null : b.posed().get(p);
                    if (posed == null) { layers.draw(drawn.piece(p), stack, light); continue; }
                    EngineModel.Placement place = posed.apply(values);
                    stack.pushPose();
                    stack.translate(place.offset().x(), place.offset().y(), place.offset().z());
                    Orientation r = place.rotation();
                    stack.mulPose(new org.joml.Quaternionf((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w()));
                    layers.draw(drawn.piece(p), stack, light);
                    stack.popPose();
                }
            }
        });
        // 0.15: a chassis's frame (ChassisModel), unlit
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(dev.mcmod.hdfv.part.chassis.ChassisDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                out.add(dev.mcmod.hdfv.neoforge.client.model.ChassisModel.mesh((dev.mcmod.hdfv.part.chassis.ChassisDesign) part.design(), null), Orientation.IDENTITY, Vec3d.ZERO);
            }
        });
        dev.mcmod.hdfv.neoforge.client.model.Looks.register(dev.mcmod.hdfv.part.FuelTankDesign.class, new PartRenderer() {
            /** 0.12: by its kind (PartLooks.tank): a strapped box, or a cylinder with domed ends for hydrogen. */
            @Override public void mesh(Part part, PartMesh out) { PartLooks.tank((dev.mcmod.hdfv.part.FuelTankDesign) part.design(), out); }
        });
    }

}
