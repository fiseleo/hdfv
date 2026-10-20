package dev.mcmod.hdfv.neoforge;

import com.mojang.serialization.Codec;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.neoforge.design.BlueprintItem;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.design.DesignTableBlock;
import dev.mcmod.hdfv.neoforge.design.DesignTabletItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** hdfv's blocks, items, item data and its own creative tab (every hdfv block and item is there, and only there). */
public final class HdfvRegistry {
    private HdfvRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Hdfv.MOD_ID);

    /** A blueprint's design: the part design's JSON (Designs.write), at most Designs.MAX_JSON characters. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DESIGN = COMPONENTS.registerComponentType("design",
            b -> b.persistent(Codec.string(0, Designs.MAX_JSON)).networkSynchronized(ByteBufCodecs.stringUtf8(Designs.MAX_JSON)).cacheEncoding());

    public static final DeferredBlock<DesignTableBlock> DESIGN_TABLE = BLOCKS.registerBlock("design_table", DesignTableBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> DESIGN_TABLE_ITEM = ITEMS.registerSimpleBlockItem("design_table", DESIGN_TABLE);
    public static final DeferredItem<DesignTabletItem> DESIGN_TABLET = ITEMS.registerItem("design_tablet", DesignTabletItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BLANK_BLUEPRINT = ITEMS.registerSimpleItem("blank_blueprint");
    public static final DeferredItem<BlueprintItem> BLUEPRINT = ITEMS.registerItem("blueprint", BlueprintItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("hdfv", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hdfv"))
            .icon(() -> new ItemStack(DESIGN_TABLE_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(DESIGN_TABLE_ITEM.get());
                output.accept(DESIGN_TABLET.get());
                output.accept(BLANK_BLUEPRINT.get());
                for (Designs.Sample s : Designs.SAMPLES) { output.accept(Blueprints.of(s.design().get())); }
                dev.mcmod.hdfv.neoforge.material.HdfvMaterials.tab(output);
                dev.mcmod.hdfv.neoforge.material.HdfvComponents.tab(output);
                dev.mcmod.hdfv.neoforge.smelting.HdfvSmelting.tab(output);
                dev.mcmod.hdfv.neoforge.machine.HdfvMachines.tab(output);
                dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks.tab(output);
                dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry.tab(output);
                dev.mcmod.hdfv.neoforge.power.HdfvPower.tab(output);
                dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum.tab(output);
                dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.tab(output);
                dev.mcmod.hdfv.neoforge.fuel.HdfvFuel.tab(output);
                dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly.tab(output);
                dev.mcmod.hdfv.neoforge.paint.HdfvPaint.tab(output);
                dev.mcmod.hdfv.neoforge.refit.HdfvRefit.tab(output);
                dev.mcmod.hdfv.neoforge.machining.HdfvMachining.tab(output);
                dev.mcmod.hdfv.neoforge.airport.HdfvAirports.tab(output);
            })
            .build());

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
    }
}
