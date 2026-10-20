package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.JetEngineModel;
import dev.mcmod.hdfv.neoforge.client.model.JetFamilyModel;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import dev.mcmod.hdfv.part.engine.RamjetDesign;
import dev.mcmod.hdfv.part.engine.ScramjetDesign;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.part.engine.TurboRamjetDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The 0.10 engines on part vehicles (JetFamilyModel), and their exhaust (0.11, Exhausts): each burning engine's plume
 * from AeroCore's Plume by what it shows (its gas's temperature, Mach number and pressure at the exit; a booster's,
 * a ramjet's and a scramjet's flame, an afterburner's reheat, a pulsejet's firing at its frequency) and its hot metal
 * glowing. A running pulsejet's model shakes along its axis at its frequency (the user asked for vibration; the
 * riders' view shakes too: Vibration): it is baked once (finishes, occlusion) and drawn each frame where it is.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JetFamilyRenderers {
    private JetFamilyRenderers() {}

    /** A key per pulsejet part for its baked, shaking model (the part's own key holds its empty static mesh). */
    private static final Map<Part, Object> PULSEJETS = new WeakHashMap<>();
    /** Where each engine's flame leaves (x, radius), worked out once. */
    private static final Map<Part, double[]> EXITS = new WeakHashMap<>();

    /** A renderer for an engine whose model is all static: its plume and glow. */
    private static PartRenderer withExhaust(java.util.function.Function<Part, PartMesh> model, boolean round) {
        return new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                PartMesh m = model.apply(part);
                out.add(m, Orientation.IDENTITY, Vec3d.ZERO);
                out.add(dev.mcmod.hdfv.neoforge.client.model.StandIn.of(m, round), Orientation.IDENTITY, Vec3d.ZERO);   // far away
            }
            @Override public void animate(Part part, PartVehicle entity, String id, int rotor, float pt, PoseStack stack, PartLayers layers, int light) {
                Exhausts.plume(part, entity, id, pt, stack, layers);
            }
            @Override public int glow(Part part, PartVehicle entity, String id, float pt) { return Exhausts.glow(part, entity, id, pt); }
        };
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        Looks.register(RamjetDesign.class, withExhaust(p -> JetFamilyModel.ramjet((RamjetDesign) p.design(), null, false).at(0), true));
        Looks.register(ScramjetDesign.class, withExhaust(p -> JetFamilyModel.scramjet((ScramjetDesign) p.design(), null, false).at(0), false));
        Looks.register(SolidRocketDesign.class, withExhaust(p -> JetFamilyModel.solidRocket((SolidRocketDesign) p.design(), null, false).at(0), true));
        // the J58's core turns with its rotor (EnginePieces), as a turbojet's does
        java.util.function.Function<Part, dev.mcmod.hdfv.neoforge.client.model.EngineModel> j58 = p -> JetFamilyModel.turboRamjet((TurboRamjetDesign) p.design(), null, false);
        Looks.register(TurboRamjetDesign.class, new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                out.add(EnginePieces.fixed(part, j58), Orientation.IDENTITY, Vec3d.ZERO);
                out.add(dev.mcmod.hdfv.neoforge.client.model.StandIn.of(EnginePieces.model(part, j58).at(0), true), Orientation.IDENTITY, Vec3d.ZERO);
            }
            @Override public void animate(Part part, PartVehicle entity, String id, int rotor, float pt, PoseStack stack, PartLayers layers, int light) {
                EnginePieces.draw(part, j58, entity, rotor, pt, stack, layers, light);
                Exhausts.plume(part, entity, id, pt, stack, layers);
            }
            @Override public int glow(Part part, PartVehicle entity, String id, float pt) { return Exhausts.glow(part, entity, id, pt); }
        });
        Looks.register(PulsejetDesign.class, new PartRenderer() {
            @Override public void animate(Part part, PartVehicle entity, String id, int rotor, float pt, PoseStack stack, PartLayers layers, int light) {
                Object key;
                synchronized (PULSEJETS) { key = PULSEJETS.computeIfAbsent(part, p -> new Object()); }
                MeshBakery.Baked baked = MeshBakery.get(key, () -> Looks.finish(JetFamilyModel.pulsejet((PulsejetDesign) part.design(), null, false).at(0)), MeshBakery.Spec.WORLD);
                boolean lit = entity.shown(id, PulsejetDesign.SHOWN_LIT, pt) > 0.5;
                double hz = entity.shown(id, PulsejetDesign.SHOWN_FREQUENCY, pt), t = Util.getMillis() / 1000.0, wave = Math.sin(2 * Math.PI * hz * t);
                stack.pushPose();
                if (lit) { stack.translate(0.008 * wave, 0.003 * Math.sin(2 * Math.PI * hz * t * 0.97 + 1), 0); }
                if (baked != null) {
                    layers.draw(baked.level(0), stack, light);
                    int glow = Exhausts.glow(part, entity, id, pt);
                    if ((glow >>> 24) > 0) { layers.glow(baked.level(0), stack, glow, true); }
                }
                stack.popPose();
                Exhausts.plume(part, entity, id, pt, stack, layers);
            }
        });
    }

    /** Where a part's flame leaves: its model's rearmost x, and the radius of its exit there. */
    static double[] exit(Part part) {
        synchronized (EXITS) {
            return EXITS.computeIfAbsent(part, p -> {
                if (p.design() instanceof SolidRocketDesign s) { return JetFamilyModel.rocketExit(s); }
                Vec3d[] b = switch (p.design()) {
                    case RamjetDesign r -> JetFamilyModel.ramjet(r, null, false).bounds();
                    case ScramjetDesign x -> JetFamilyModel.scramjet(x, null, false).bounds();
                    case PulsejetDesign j -> JetFamilyModel.pulsejet(j, null, false).bounds();
                    case TurboRamjetDesign t -> JetFamilyModel.turboRamjet(t, null, false).bounds();
                    case JetEngineDesign j -> JetEngineModel.build(j, null, false).bounds();
                    case TurbofanDesign f -> dev.mcmod.hdfv.neoforge.client.model.GasTurbineModel.build(f, null, false).bounds();
                    default -> null;
                };
                if (b == null) { return new double[]{-0.5, 0.1}; }
                double r = 0.5 * Math.min(b[1].y() - b[0].y(), b[1].z() - b[0].z());
                if (p.design() instanceof PulsejetDesign j) { r = j.tube().tailDiameterM() / 2; }
                return new double[]{b[0].x(), Math.max(0.6 * r, 0.02)};
            });
        }
    }
}
