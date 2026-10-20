package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock;
import dev.mcmod.hdfv.neoforge.multiblock.MemberBlock;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** The refinery and chemical plant (0.8): the distillation column, the process units, their outlets; bitumen, plastics and asphalt. */
public final class HdfvRefinery {
    private HdfvRefinery() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Supplier<B> make) {
        DeferredBlock<B> b = BLOCKS.register(id, make);
        ITEMS.registerSimpleBlockItem(id, b);
        return b;
    }

    public static final Map<String, DeferredBlock<MemberBlock>> MEMBERS = new LinkedHashMap<>();

    static {
        List<String> kinds = new ArrayList<>(List.of(Structures.FIRED_HEATER, Structures.COLUMN_SECTION, Structures.COLUMN_TOP, Structures.VACUUM_COLUMN));
        kinds.addAll(Structures.REACTORS);
        for (String k : kinds) { MEMBERS.put(k, block(k, () -> new MemberBlock(steel(), k))); }
    }

    public static final DeferredBlock<UnitOutletBlock> OUTLET = block(Structures.UNIT_OUTLET, () -> new UnitOutletBlock(steel()));
    public static final DeferredBlock<ControllerBlock> COLUMN = block(Structures.DISTILLATION,
            () -> new ControllerBlock(steel(), Structures.DISTILLATION, HdfvRefinery::columnType));
    public static final DeferredBlock<ControllerBlock> UNIT = block(Structures.PROCESS_UNIT, () -> new ControllerBlock(steel(), Structures.PROCESS_UNIT, HdfvRefinery::unitType));
    /** Asphalt (0.8); 0.20: paintable (runway and taxiway lines: airport.MarkedSurfaceBlock). */
    public static final DeferredBlock<dev.mcmod.hdfv.neoforge.airport.MarkedSurfaceBlock> ASPHALT = block("asphalt",
            () -> new dev.mcmod.hdfv.neoforge.airport.MarkedSurfaceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UnitOutletBlockEntity>> OUTLET_ENTITY = BLOCK_ENTITIES.register("unit_outlet",
            () -> BlockEntityType.Builder.of(UnitOutletBlockEntity::new, OUTLET.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryUnitBlockEntity>> COLUMN_ENTITY = BLOCK_ENTITIES.register("distillation_column",
            () -> BlockEntityType.Builder.of(RefineryUnitBlockEntity::column, COLUMN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryUnitBlockEntity>> UNIT_ENTITY = BLOCK_ENTITIES.register("process_unit",
            () -> BlockEntityType.Builder.of(RefineryUnitBlockEntity::unit, UNIT.get()).build(null));

    private static BlockEntityType<RefineryUnitBlockEntity> columnType() { return COLUMN_ENTITY.get(); }
    private static BlockEntityType<RefineryUnitBlockEntity> unitType() { return UNIT_ENTITY.get(); }

    public static final DeferredItem<Item> BITUMEN = ITEMS.registerSimpleItem("bitumen");
    public static final DeferredItem<Item> POLYETHYLENE = ITEMS.registerSimpleItem("polyethylene");
    public static final DeferredItem<Item> POLYPROPYLENE = ITEMS.registerSimpleItem("polypropylene");

    /** The item of a solid product. */
    public static Item product(String name) {
        return switch (name) {
            case "bitumen" -> BITUMEN.get();
            case "polyethylene" -> POLYETHYLENE.get();
            case "polypropylene" -> POLYPROPYLENE.get();
            default -> HdfvMaterials.MATERIAL_ITEMS.get(name).get();
        };
    }

    public static void tab(CreativeModeTab.Output out) {
        out.accept(COLUMN.get());
        out.accept(UNIT.get());
        out.accept(OUTLET.get());
        for (DeferredBlock<MemberBlock> b : MEMBERS.values()) { out.accept(b.get()); }
        out.accept(BITUMEN.get());
        out.accept(POLYETHYLENE.get());
        out.accept(POLYPROPYLENE.get());
        out.accept(ASPHALT.get());
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of(Structures.DISTILLATION, Structures.PROCESS_UNIT, Structures.UNIT_OUTLET, "asphalt"));
        out.addAll(MEMBERS.keySet());
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, OUTLET_ENTITY.get(), (be, side) -> be.handler());
            for (var type : List.of(COLUMN_ENTITY.get(), UNIT_ENTITY.get())) {
                e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> be.feedHandler());
                e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy());
                e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.outputHandler());
            }
        });
    }
}
