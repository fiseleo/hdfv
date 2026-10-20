package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.power.PowerPlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
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
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** Power (0.8): cables, line shafts, and the batteries, generators, motors, steam engines, treadles, engine stands and creative sources. */
public final class HdfvPower {
    private HdfvPower() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Supplier<B> make) {
        DeferredBlock<B> b = BLOCKS.register(id, make);
        ITEMS.registerSimpleBlockItem(id, b);
        return b;
    }

    public static final DeferredBlock<CableBlock> CABLE = block("cable", () -> new CableBlock(metal().strength(1f).sound(SoundType.WOOL).noOcclusion(), PowerPlant.CableKind.LIGHT));
    public static final DeferredBlock<CableBlock> HEAVY_CABLE = block("heavy_cable", () -> new CableBlock(metal().strength(1.5f).sound(SoundType.WOOL).noOcclusion(), PowerPlant.CableKind.HEAVY));
    public static final DeferredBlock<ShaftBlock> LINE_SHAFT = block("line_shaft", () -> new ShaftBlock(metal().noOcclusion()));
    public static final Map<PowerBlock.Kind, DeferredBlock<PowerBlock>> POWER = new LinkedHashMap<>();

    static {
        for (PowerBlock.Kind k : PowerBlock.Kind.values()) {
            BlockBehaviour.Properties p = k == PowerBlock.Kind.TREADLE ? BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD)
                    : k == PowerBlock.Kind.CREATIVE_POWER ? BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1f, 3600000f).noLootTable()
                    : metal();
            // 0.12: drawn as models that do not fill the block (but the creative source, a full case)
            BlockBehaviour.Properties props = k == PowerBlock.Kind.CREATIVE_POWER ? p : p.noOcclusion();
            POWER.put(k, block(id(k), () -> new PowerBlock(props, k)));
        }
    }

    public static String id(PowerBlock.Kind k) { return k.name().toLowerCase(Locale.ROOT); }

    private static <E extends PowerBlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<E>> entity(String id, BlockEntityType.BlockEntitySupplier<E> make, PowerBlock.Kind k) {
        return BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.of(make, POWER.get(k).get()).build(null));
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY_ENTITY = entity("battery", BatteryBlockEntity::new, PowerBlock.Kind.BATTERY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR_ENTITY = entity("generator", GeneratorBlockEntity::new, PowerBlock.Kind.GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MotorBlockEntity>> MOTOR_ENTITY = entity("electric_motor", MotorBlockEntity::new, PowerBlock.Kind.ELECTRIC_MOTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEngineBlockEntity>> STEAM_ENTITY = entity("steam_engine", SteamEngineBlockEntity::new, PowerBlock.Kind.STEAM_ENGINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TreadleBlockEntity>> TREADLE_ENTITY = entity("treadle", TreadleBlockEntity::new, PowerBlock.Kind.TREADLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineStandBlockEntity>> STAND_ENTITY = entity("engine_stand", EngineStandBlockEntity::new, PowerBlock.Kind.ENGINE_STAND);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativePowerBlockEntity>> CREATIVE_ENTITY = entity("creative_power", CreativePowerBlockEntity::new, PowerBlock.Kind.CREATIVE_POWER);

    public static Block block(PowerBlock.Kind k) { return POWER.get(k).get(); }

    public static void tab(CreativeModeTab.Output out) {
        out.accept(CABLE.get());
        out.accept(HEAVY_CABLE.get());
        out.accept(LINE_SHAFT.get());
        for (DeferredBlock<PowerBlock> b : POWER.values()) { out.accept(b.get()); }
    }

    /** Every block id (creative source last: it has no recipe and drops nothing). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of("cable", "heavy_cable", "line_shaft"));
        for (PowerBlock.Kind k : POWER.keySet()) { out.add(id(k)); }
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BATTERY_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GENERATOR_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MOTOR_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CREATIVE_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, STAND_ENTITY.get(), (be, side) -> be.tank());
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STEAM_ENTITY.get(), (be, side) -> be.items());
        });
    }
}
