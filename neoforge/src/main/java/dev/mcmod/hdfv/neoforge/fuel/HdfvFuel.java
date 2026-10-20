package dev.mcmod.hdfv.neoforge.fuel;

import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Vehicle fuel (0.8): the fuel dispenser; cans poured into vehicles (Fuelling). 0.10: solid propellant (ammonium
 * perchlorate from gunpowder and redstone in the alloy furnace; KNSB grains from gunpowder as saltpetre and sugar; APCP
 * grains from ammonium perchlorate, aluminium and synthetic rubber), loaded into boosters by hand, and reed sets for
 * pulsejets (Servicing); parts cast off drop as items.
 */
public final class HdfvFuel {
    private HdfvFuel() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);

    public static final DeferredBlock<FuelDispenserBlock> DISPENSER = BLOCKS.register("fuel_dispenser", () -> new FuelDispenserBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    static { ITEMS.registerSimpleBlockItem("fuel_dispenser", DISPENSER); }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelDispenserBlockEntity>> DISPENSER_ENTITY = BLOCK_ENTITIES.register("fuel_dispenser",
            () -> BlockEntityType.Builder.of(FuelDispenserBlockEntity::new, DISPENSER.get()).build(null));

    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> AMMONIUM_PERCHLORATE = ITEMS.registerSimpleItem("ammonium_perchlorate"),
            KNSB_GRAIN = ITEMS.registerSimpleItem("knsb_grain"), APCP_GRAIN = ITEMS.registerSimpleItem("apcp_grain"), REED_SET = ITEMS.registerSimpleItem("reed_set");

    public static void tab(CreativeModeTab.Output out) {
        out.accept(DISPENSER.get());
        for (var i : List.of(AMMONIUM_PERCHLORATE, KNSB_GRAIN, APCP_GRAIN, REED_SET)) { out.accept(i.get()); }
    }

    public static List<String> ids() { return List.of("fuel_dispenser"); }

    /** The 0.10 items and the recipes that make them (recipe path). */
    public static java.util.Map<String, String> items() {
        return java.util.Map.of("ammonium_perchlorate", "electrolysis/ammonium_perchlorate", "knsb_grain", "alloying/knsb_grain", "apcp_grain", "alloying/apcp_grain",
                "reed_set", "reed_set");
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DISPENSER_ENTITY.get(), (be, side) -> be.handler()));
        NeoForge.EVENT_BUS.addListener(Fuelling::interact);
        NeoForge.EVENT_BUS.addListener(Servicing::interact);
        NeoForge.EVENT_BUS.addListener(Servicing::jettisoned);
    }
}
