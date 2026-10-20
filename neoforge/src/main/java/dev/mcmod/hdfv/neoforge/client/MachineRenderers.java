package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Block entity renderers (0.8): the CNC and the pump jack drawn whole; 0.12 the machines' light (MachineEffects). */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MachineRenderers {
    private MachineRenderers() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(HdfvMachines.MACHINE_ENTITY.get(), CncRenderer::new);
        e.registerBlockEntityRenderer(dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum.JACK_ENTITY.get(), PumpJackRenderer::new);
        e.registerBlockEntityRenderer(dev.mcmod.hdfv.neoforge.material.HdfvMaterials.ALLOY_FURNACE_ENTITY.get(), MachineEffects.Furnace::new);
        // 0.20: the control tower and the windsock
        e.registerBlockEntityRenderer(dev.mcmod.hdfv.neoforge.airport.HdfvAirports.TOWER_ENTITY.get(), TowerRenderer::new);
        e.registerBlockEntityRenderer(dev.mcmod.hdfv.neoforge.airport.HdfvAirports.WINDSOCK_ENTITY.get(), WindsockRenderer::new);
    }
}
