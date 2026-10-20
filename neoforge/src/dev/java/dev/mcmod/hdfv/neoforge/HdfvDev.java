package dev.mcmod.hdfv.neoforge;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * The development side of hdfv (0.24): the GameTests and /hdfv dev live in the dev source set, which only the
 * development runs load (the release JAR carries none of them, nor the GameTests' structures). /hdfv dev joins the
 * /hdfv tree Hdfv registers (Brigadier merges nodes of the same name). The client's /hdfvdev registers itself
 * (client/DevCommands).
 */
public final class HdfvDev {
    private HdfvDev() {}

    @EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class GameTests {
        private GameTests() {}

        /** Only the GameTest run sets hdfv.gametests. */
        @SubscribeEvent static void register(RegisterGameTestsEvent event) {
            if (!Boolean.getBoolean("hdfv.gametests")) { return; }
            event.register(dev.mcmod.hdfv.neoforge.vehicle.VehicleGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.vehicle.AircraftGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.design.DesignGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.material.MaterialGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.material.RecipeGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.machine.MachineGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.assembly.AssemblyGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.multiblock.MultiblockGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.fluid.FluidGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.fluid.HydrogenGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.power.PowerGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.multiblock.CncGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.petroleum.PetroleumGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.petroleum.RefineryGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.petroleum.PetroleumScreenGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.fuel.FuelGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.fuel.ServicingGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.machine.ToolGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.machine.MachiningGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.paint.DecalGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.paint.PaintBoothGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.refit.RefitShopGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.vehicle.DriveGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.vehicle.ChassisGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.truck.TruckGameTests.class);
            event.register(dev.mcmod.hdfv.neoforge.airport.AirportGameTests.class);
        }
    }

    @EventBusSubscriber(modid = Hdfv.MOD_ID)
    public static final class DevServerCommands {
        private DevServerCommands() {}

        @SubscribeEvent static void register(RegisterCommandsEvent event) {
            event.getDispatcher().register(Commands.literal(Hdfv.MOD_ID).then(dev.mcmod.hdfv.neoforge.assembly.AssemblyDevCommands.tree()));
        }
    }
}
