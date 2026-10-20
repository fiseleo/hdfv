package dev.mcmod.hdfv.neoforge.fluid;

import dev.mcmod.hdfv.fluid.HdfvFluids;
import dev.mcmod.hdfv.fluid.HydrogenStorage;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.multiblock.MemberBlock;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluids and their plant (0.8): hdfv's fluids (core fluid.HdfvFluids; kept in pipes, tanks and cans, never poured
 * into the world, so no blocks or buckets); pipes, tank blocks, pumps, the storage tank's wall, roof and valve, and the
 * 20 l jerry can. 0.10: liquid and 700 bar hydrogen, the cryogenic tank and the hydrogen cylinder bank (tank blocks of
 * other stores), the hydrogen liquefier; the can takes no hydrogen (core fluid.HydrogenStorage).
 */
public final class HdfvFluidRegistry {
    private HdfvFluidRegistry() {}

    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Hdfv.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Hdfv.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hdfv.MOD_ID);

    public static final Map<String, DeferredHolder<FluidType, FluidType>> FLUID_TYPES = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<Fluid, BaseFlowingFluid.Source>> SOURCES = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<Fluid, BaseFlowingFluid.Flowing>> FLOWING = new LinkedHashMap<>();

    static {
        for (HdfvFluids.Spec spec : HdfvFluids.all()) {
            String id = spec.id();
            DeferredHolder<FluidType, FluidType> type = TYPES.register(id, () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.hdfv." + id).density((int) Math.round(spec.densityKgPerM3()))
                    .viscosity((int) Math.max(1, Math.round(spec.viscosityPaS() * 1e6))).temperature(HdfvFluids.temperatureK(spec.id()))
                    .canSwim(false).canDrown(false).canPushEntity(false)));
            FLUID_TYPES.put(id, type);
            BaseFlowingFluid.Properties[] props = new BaseFlowingFluid.Properties[1];
            DeferredHolder<Fluid, BaseFlowingFluid.Source> source = FLUIDS.register(id, () -> new BaseFlowingFluid.Source(props[0]));
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing = FLUIDS.register(id + "_flowing", () -> new BaseFlowingFluid.Flowing(props[0]));
            props[0] = new BaseFlowingFluid.Properties(type, source, flowing);
            SOURCES.put(id, source);
            FLOWING.put(id, flowing);
        }
    }

    /** A fluid of ours (or water) by id; null if none. */
    public static Fluid fluid(String id) {
        if (id.equals("water")) { return Fluids.WATER; }
        DeferredHolder<Fluid, BaseFlowingFluid.Source> s = SOURCES.get(id);
        return s == null ? null : s.get();
    }

    /** 0.23: other mods' fluids by the common tags (c:diesel, c:crude_oil...), the fluid each is of ours. */
    public static final Map<String, String> COMMON = commonTags();

    private static Map<String, String> commonTags() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("crude_oil", "crude_oil"); m.put("oil", "crude_oil"); m.put("heavy_oil", "heavy_crude_oil"); m.put("lpg", "lpg");
        m.put("naphtha", "naphtha"); m.put("gasoline", "gasoline"); m.put("kerosene", "kerosene"); m.put("diesel", "diesel");
        m.put("fuel_oil", "fuel_oil"); m.put("lubricant", "lubricating_oil"); m.put("methanol", "methanol"); m.put("ethylene", "ethylene");
        m.put("propylene", "propylene"); m.put("hydrogen", HdfvFluids.HYDROGEN);
        return java.util.Collections.unmodifiableMap(m);
    }

    /** A fluid's id ("diesel", "water"; another mod's fluid by a common tag, else by its registry path). */
    public static String id(Fluid f) {
        if (f == Fluids.WATER || f == Fluids.FLOWING_WATER) { return "water"; }
        ResourceLocation key = BuiltInRegistries.FLUID.getKey(f);
        if (!key.getNamespace().equals(Hdfv.MOD_ID)) {
            for (var e : COMMON.entrySet()) {
                if (f.builtInRegistryHolder().is(net.minecraft.tags.FluidTags.create(ResourceLocation.fromNamespaceAndPath("c", e.getKey())))) { return e.getValue(); }
            }
        }
        String path = key.getPath();
        return key.getNamespace().equals(Hdfv.MOD_ID) && path.endsWith("_flowing") ? path.substring(0, path.length() - 8) : path;
    }

    /** A fluid's properties (ours, water's, or water's for another mod's fluid). */
    public static HdfvFluids.Spec spec(Fluid f) {
        HdfvFluids.Spec s = HdfvFluids.of(id(f));
        return s == null ? HdfvFluids.WATER : s;
    }

    // ---------------------------------------------------------------- plant

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, java.util.function.Supplier<B> make) {
        DeferredBlock<B> b = BLOCKS.register(id, make);
        ITEMS.registerSimpleBlockItem(id, b);
        return b;
    }

    public static final DeferredBlock<FluidPipeBlock> PIPE = block("fluid_pipe", () -> new FluidPipeBlock(steel().noOcclusion()));
    // 0.12: tanks, the liquefier and the pump are drawn as models that do not fill their blocks
    public static final DeferredBlock<TankBlock> TANK = block("fluid_tank", () -> new TankBlock(steel().noOcclusion()));
    public static final DeferredBlock<TankBlock> CRYOGENIC_TANK = block("cryogenic_tank", () -> new TankBlock(steel().noOcclusion(), HydrogenStorage.Store.CRYOGENIC_TANK));
    public static final DeferredBlock<TankBlock> HYDROGEN_CYLINDERS = block("hydrogen_cylinders", () -> new TankBlock(steel().noOcclusion(), HydrogenStorage.Store.CYLINDERS));
    public static final DeferredBlock<HydrogenLiquefierBlock> LIQUEFIER = block("hydrogen_liquefier", () -> new HydrogenLiquefierBlock(steel().noOcclusion()));
    public static final DeferredBlock<PumpBlock> PUMP = block("pump", () -> new PumpBlock(steel().noOcclusion()));
    public static final DeferredBlock<MemberBlock> TANK_WALL = block(Structures.TANK_WALL, () -> new MemberBlock(steel(), Structures.TANK_WALL));
    public static final DeferredBlock<MemberBlock> TANK_ROOF = block(Structures.TANK_ROOF, () -> new MemberBlock(steel(), Structures.TANK_ROOF));
    public static final DeferredBlock<StorageValveBlock> TANK_VALVE = block(Structures.TANK_VALVE, () -> new StorageValveBlock(steel()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankBlockEntity>> TANK_ENTITY = BLOCK_ENTITIES.register("fluid_tank",
            () -> BlockEntityType.Builder.of(TankBlockEntity::new, TANK.get(), CRYOGENIC_TANK.get(), HYDROGEN_CYLINDERS.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrogenLiquefierBlockEntity>> LIQUEFIER_ENTITY = BLOCK_ENTITIES.register("hydrogen_liquefier",
            () -> BlockEntityType.Builder.of(HydrogenLiquefierBlockEntity::new, LIQUEFIER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> PUMP_ENTITY = BLOCK_ENTITIES.register("pump",
            () -> BlockEntityType.Builder.of(PumpBlockEntity::new, PUMP.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageValveBlockEntity>> VALVE_ENTITY = BLOCK_ENTITIES.register("storage_tank_valve",
            () -> BlockEntityType.Builder.of(StorageValveBlockEntity::new, TANK_VALVE.get()).build(null));

    /** A container's fluid. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> FLUID_CONTENT = COMPONENTS.registerComponentType("fluid",
            b -> b.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));
    public static final int JERRY_CAN_LITRES = 20;
    public static final DeferredItem<JerryCanItem> JERRY_CAN = ITEMS.registerItem("jerry_can", JerryCanItem::new, new Item.Properties().stacksTo(1));

    public static void tab(CreativeModeTab.Output out) {
        out.accept(PIPE.get());
        out.accept(TANK.get());
        out.accept(PUMP.get());
        out.accept(TANK_WALL.get());
        out.accept(TANK_ROOF.get());
        out.accept(TANK_VALVE.get());
        out.accept(CRYOGENIC_TANK.get());
        out.accept(HYDROGEN_CYLINDERS.get());
        out.accept(LIQUEFIER.get());
        out.accept(JERRY_CAN.get());
        for (String fuel : List.of("gasoline", "diesel", "kerosene")) { out.accept(JerryCanItem.filled(fluid(fuel), JERRY_CAN_LITRES)); }
    }

    public static List<String> ids() {
        return new ArrayList<>(List.of("fluid_pipe", "fluid_tank", "pump", Structures.TANK_WALL, Structures.TANK_ROOF, Structures.TANK_VALVE, "jerry_can",
                "cryogenic_tank", "hydrogen_cylinders", "hydrogen_liquefier"));
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TANK_ENTITY.get(), (be, side) -> be.column());
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, VALVE_ENTITY.get(), (be, side) -> be.handler());
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PUMP_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LIQUEFIER_ENTITY.get(), (be, side) -> be.handler());
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LIQUEFIER_ENTITY.get(), (be, side) -> be.energy());
            // the can holds no hydrogen, liquid or compressed (0.10)
            e.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new FluidHandlerItemStack(FLUID_CONTENT, stack, JERRY_CAN_LITRES) {
                @Override public boolean canFillFluidType(net.neoforged.neoforge.fluids.FluidStack f) {
                    return HydrogenStorage.takes(HydrogenStorage.Store.CAN, id(f.getFluid()));
                }
            }, JERRY_CAN.get());
        });
    }
}
