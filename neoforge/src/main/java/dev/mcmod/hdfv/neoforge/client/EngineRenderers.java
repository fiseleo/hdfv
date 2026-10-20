package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.model.EngineModel;
import dev.mcmod.hdfv.neoforge.client.model.GasTurbineModel;
import dev.mcmod.hdfv.neoforge.client.model.Looks;
import dev.mcmod.hdfv.neoforge.client.model.JetEngineModel;
import dev.mcmod.hdfv.neoforge.client.model.PistonEngineModel;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The engines on AeroCore's part vehicles, drawn from their designs; the crank or spool turns about the part's +X.
 * 0.11: what moves and shows turns with the engine's rotors (EnginePieces: fans, spinners, first and last rows,
 * pulleys); their exhaust (Exhausts): plumes, and hot pipes and nozzles glowing.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EngineRenderers {
    private EngineRenderers() {}

    /**
     * An engine part's renderer: its model's fixed parts as the static mesh, its moving pieces turning with its rotors
     * (EnginePieces), its plume when it has one, its hot metal glowing (Exhausts).
     */
    private static PartRenderer engine(java.util.function.Function<Part, EngineModel> build, boolean plume, boolean round) {
        return new PartRenderer() {
            @Override public void mesh(Part part, PartMesh out) {
                out.add(EnginePieces.fixed(part, build), Orientation.IDENTITY, Vec3d.ZERO);
                out.add(dev.mcmod.hdfv.neoforge.client.model.StandIn.of(EnginePieces.model(part, build).at(0), round), Orientation.IDENTITY, Vec3d.ZERO);   // far away
            }
            @Override public void animate(Part part, PartVehicle entity, String id, int rotor, float pt, PoseStack stack, PartLayers layers, int light) {
                EnginePieces.draw(part, build, entity, rotor, pt, stack, layers, light);
                if (plume) { Exhausts.plume(part, entity, id, pt, stack, layers); }
            }
            @Override public int glow(Part part, PartVehicle entity, String id, float pt) { return Exhausts.glow(part, entity, id, pt); }
        };
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        Looks.register(PistonEngineDesign.class, engine(p -> PistonEngineModel.build((PistonEngineDesign) p.design(), null, false), false, false));
        Looks.register(JetEngineDesign.class, engine(p -> JetEngineModel.build((JetEngineDesign) p.design(), null, false), true, true));
        Looks.register(TurbofanDesign.class, engine(p -> GasTurbineModel.build((TurbofanDesign) p.design(), null, false), true, true));
        Looks.register(ShaftTurbineDesign.class, engine(p -> GasTurbineModel.build((ShaftTurbineDesign) p.design(), null, false), false, true));
    }
}
