package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** hdfv's fluids drawn (0.8): water's textures in each fluid's colour (in a can's or tank's tooltip, and wherever another mod draws them). */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FluidClient {
    private FluidClient() {}

    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still"),
            FLOWING = ResourceLocation.withDefaultNamespace("block/water_flow");

    @net.neoforged.bus.api.SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent e) {
        for (HdfvFluids.Spec spec : HdfvFluids.all()) {
            int tint = 0xff000000 | spec.colour();
            e.registerFluidType(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() { return STILL; }
                @Override public ResourceLocation getFlowingTexture() { return FLOWING; }
                @Override public int getTintColor() { return tint; }
            }, HdfvFluidRegistry.FLUID_TYPES.get(spec.id()).get());
        }
    }
}
