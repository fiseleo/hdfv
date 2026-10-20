package dev.mcmod.hdfv.neoforge.assembly;

import dev.mcmod.hdfv.assembly.Room;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assembly rooms (0.7): the floor, slipway, crane rail, three kinds of door and the three controllers; the vehicle
 * entity that is built in them.
 */
public final class HdfvAssembly {
    private HdfvAssembly() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hdfv.MOD_ID);

    /** A vehicle blueprint's layout: the vehicle's parts and mounts as JSON (unwired), packed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> VEHICLE_LAYOUT = COMPONENTS.registerComponentType("vehicle",
            b -> b.persistent(Packed.CODEC).networkSynchronized(Packed.STREAM_CODEC).cacheEncoding());
    public static final DeferredItem<VehicleBlueprintItem> VEHICLE_BLUEPRINT = ITEMS.registerItem("vehicle_blueprint", VehicleBlueprintItem::new,
            new net.minecraft.world.item.Item.Properties().stacksTo(1));

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<Block> ASSEMBLY_FLOOR = block("assembly_floor",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2f, 6f).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SLIPWAY = block("slipway",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 3f).sound(SoundType.WOOD)));
    public static final DeferredBlock<Block> CRANE_RAIL = block("crane_rail", () -> new Block(metal()));
    /** A work light (0.8). */
    public static final DeferredBlock<WorkLightBlock> WORK_LIGHT = block("work_light",
            () -> new WorkLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.LANTERN).lightLevel(s -> 15).noOcclusion()));
    public static final Map<String, DeferredBlock<RoomDoorBlock>> DOORS = new LinkedHashMap<>();
    public static final Map<Room, DeferredBlock<RoomControllerBlock>> CONTROLLERS = new LinkedHashMap<>();

    static {
        // curtain and head across the door, px: stacking panels under a housing, a slatted curtain on its coil, a lift gate under its beam
        DOORS.put("hangar_door", block("hangar_door", () -> new RoomDoorBlock(metal().noOcclusion(), 4, 10)));
        DOORS.put("roller_shutter", block("roller_shutter", () -> new RoomDoorBlock(metal().noOcclusion(), 2, 6)));
        DOORS.put("dock_gate", block("dock_gate", () -> new RoomDoorBlock(metal().noOcclusion(), 6, 12)));
        for (Room r : Room.values()) {
            CONTROLLERS.put(r, block(r.name().toLowerCase(Locale.ROOT) + "_controller", () -> new RoomControllerBlock(metal(), r)));
        }
    }

    private static <B extends Block> DeferredBlock<B> block(String id, java.util.function.Supplier<B> make) {
        DeferredBlock<B> b = BLOCKS.register(id, make);
        ITEMS.registerSimpleBlockItem(id, b);
        return b;
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RoomControllerBlockEntity>> ROOM_CONTROLLER = BLOCK_ENTITIES.register("room_controller",
            () -> BlockEntityType.Builder.of(RoomControllerBlockEntity::new, CONTROLLERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<RoomMenu>> ROOM_MENU = MENUS.register("room", () -> IMenuTypeExtension.create(RoomMenu::new));
    public static final DeferredHolder<EntityType<?>, EntityType<HdfvVehicle>> VEHICLE = ENTITIES.register("vehicle",
            () -> EntityType.Builder.<HdfvVehicle>of(HdfvVehicle::new, MobCategory.MISC).sized(1, 1).clientTrackingRange(10).updateInterval(1).fireImmune()
                    .build(Hdfv.MOD_ID + ":vehicle"));

    public static void tab(CreativeModeTab.Output out) {
        out.accept(VEHICLE_BLUEPRINT.get());
        for (var e : dev.mcmod.hdfv.assembly.SampleLayouts.all().entrySet()) {
            net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(VEHICLE_BLUEPRINT.get());
            s.set(VEHICLE_LAYOUT.get(), e.getValue().toJsonText());
            out.accept(s);
        }
        out.accept(ASSEMBLY_FLOOR.get());
        out.accept(SLIPWAY.get());
        out.accept(CRANE_RAIL.get());
        out.accept(WORK_LIGHT.get());
        for (DeferredBlock<RoomDoorBlock> d : DOORS.values()) { out.accept(d.get()); }
        for (DeferredBlock<RoomControllerBlock> c : CONTROLLERS.values()) { out.accept(c.get()); }
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of("vehicle_blueprint", "assembly_floor", "slipway", "crane_rail", "work_light"));
        out.addAll(DOORS.keySet());
        for (Room r : CONTROLLERS.keySet()) { out.add(r.name().toLowerCase(Locale.ROOT) + "_controller"); }
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(AssemblyNetwork::register);
    }
}
