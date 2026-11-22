package dev.mcmod.hdfv.neoforge;

import dev.mcmod.hdfv.neoforge.design.DesignNetwork;
import dev.mcmod.hdfv.neoforge.vehicle.HdfvVehicleCommands;
import dev.mcmod.hdfv.part.HdfvParts;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** High Degree of Freedom Vehicles: player-designed parts and vehicles on AeroCore's physics. */
@Mod(Hdfv.MOD_ID)
public final class Hdfv {
    public static final String MOD_ID = "hdfv";

    public Hdfv(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, HdfvConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, HdfvClientConfig.SPEC);   // 0.14: the dashboard and the clutch
        // Part types first: AeroCore reads saved vehicles' blueprints through them.
        HdfvParts.register();
        HdfvRegistry.register(modBus);
        dev.mcmod.hdfv.neoforge.material.HdfvMaterials.register(modBus);
        dev.mcmod.hdfv.neoforge.material.HdfvComponents.register(modBus);
        dev.mcmod.hdfv.neoforge.smelting.HdfvSmelting.register(modBus);
        dev.mcmod.hdfv.neoforge.machine.HdfvMachines.register(modBus);
        dev.mcmod.hdfv.neoforge.villager.HdfvVillagers.register(modBus);
        dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly.register(modBus);
        dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks.register(modBus);
        dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.register(modBus);
        dev.mcmod.hdfv.neoforge.power.HdfvPower.register(modBus);
        dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum.register(modBus);
        dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.register(modBus);
        dev.mcmod.hdfv.neoforge.fuel.HdfvFuel.register(modBus);
        dev.mcmod.hdfv.neoforge.paint.HdfvPaint.register(modBus);
        dev.mcmod.hdfv.neoforge.refit.HdfvRefit.register(modBus);
        dev.mcmod.hdfv.neoforge.machining.HdfvMachining.register(modBus);
        dev.mcmod.hdfv.neoforge.airport.HdfvAirports.register(modBus);
        dev.mcmod.hdfv.neoforge.road.HdfvRoads.register(modBus);
        dev.mcmod.hdfv.neoforge.storage.HdfvStorage.register(modBus);
        dev.mcmod.hdfv.neoforge.storage.HdfvCargo.register(modBus);
        dev.mcmod.hdfv.neoforge.worksite.HdfvWorksite.register(modBus);   // 0.26
        dev.mcmod.hdfv.neoforge.repair.HdfvRepair.register(modBus);
        dev.mcmod.hdfv.neoforge.damage.VehicleDamage.register(modBus);
        dev.mcmod.hdfv.neoforge.tutorial.HdfvTutorial.register(modBus);
        modBus.addListener(dev.mcmod.hdfv.neoforge.sound.SoundSync::register);
        dev.mcmod.hdfv.neoforge.sound.FarSignatures.register();
        HdfvParticles.register(modBus);
        modBus.addListener(DesignNetwork::register);
        modBus.addListener(dev.mcmod.hdfv.neoforge.paint.DecalNetwork::register);
        // 0.24: GameTests and /hdfv dev are in the dev source set (development runs only, not in the release JAR)
        NeoForge.EVENT_BUS.addListener(Hdfv::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(MOD_ID).then(HdfvVehicleCommands.tree()).then(dev.mcmod.hdfv.neoforge.tutorial.HdfvTutorial.command())
                .then(dev.mcmod.hdfv.neoforge.paint.DecalCommands.tree()));
    }
}
