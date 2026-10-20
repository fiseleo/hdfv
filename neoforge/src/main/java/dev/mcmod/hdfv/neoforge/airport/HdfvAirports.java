package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock;
import dev.mcmod.hdfv.neoforge.multiblock.MemberBlock;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Airfields (0.20): the control tower and its members, light fittings, pavement and its paint, turf, the windsock,
 * holding signs, apron floodlights; the tower's screen; world-generated airports.
 */
public final class HdfvAirports {
    private HdfvAirports() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Hdfv.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TowerMenu>> TOWER_MENU = MENUS.register("control_tower", () -> IMenuTypeExtension.create(TowerMenu::new));

    private static BlockBehaviour.Properties concrete() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2f, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Supplier<B> make) {
        DeferredBlock<B> b = BLOCKS.register(id, make);
        ITEMS.registerSimpleBlockItem(id, b);
        return b;
    }

    // ---------------------------------------------------------------- the tower

    public static final DeferredBlock<ControllerBlock> TOWER = block(Structures.TOWER, () -> new ControllerBlock(concrete(), Structures.TOWER, HdfvAirports::towerType));
    public static final Map<String, DeferredBlock<? extends MemberBlock>> MEMBERS = new LinkedHashMap<>();

    static {
        MEMBERS.put(Structures.TOWER_BASE, block(Structures.TOWER_BASE, () -> new MemberBlock(concrete(), Structures.TOWER_BASE, true)));
        MEMBERS.put(Structures.TOWER_SHAFT, block(Structures.TOWER_SHAFT, () -> new MemberBlock(concrete(), Structures.TOWER_SHAFT, true)));
        MEMBERS.put(Structures.TOWER_FLOOR, block(Structures.TOWER_FLOOR, () -> new MemberBlock(concrete(), Structures.TOWER_FLOOR, true)));
        MEMBERS.put(Structures.TOWER_GLASS, block(Structures.TOWER_GLASS, () -> new MemberBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                .strength(1f, 6f).sound(SoundType.GLASS).noOcclusion().isViewBlocking((s, l, p) -> false), Structures.TOWER_GLASS, true)));
        MEMBERS.put(Structures.TOWER_ROOF, block(Structures.TOWER_ROOF, () -> new MemberBlock(steel(), Structures.TOWER_ROOF, true)));
        MEMBERS.put(Structures.TOWER_CONSOLE, block(Structures.TOWER_CONSOLE, () -> new AirfieldBlocks.Console(steel().noOcclusion(), Structures.TOWER_CONSOLE)));
        MEMBERS.put(Structures.TOWER_RADAR, block(Structures.TOWER_RADAR, () -> new MemberBlock(steel().noOcclusion(), Structures.TOWER_RADAR, true)));
        MEMBERS.put(Structures.TOWER_MAST, block(Structures.TOWER_MAST, () -> new MemberBlock(steel().noOcclusion(), Structures.TOWER_MAST, true)));
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TowerBlockEntity>> TOWER_ENTITY = BLOCK_ENTITIES.register(Structures.TOWER,
            () -> BlockEntityType.Builder.of(TowerBlockEntity::new, TOWER.get()).build(null));

    private static BlockEntityType<TowerBlockEntity> towerType() { return TOWER_ENTITY.get(); }

    // ---------------------------------------------------------------- the airfield

    public static final Map<Fixture.Kind, DeferredBlock<FixtureBlock>> FIXTURES = new LinkedHashMap<>();

    static {
        for (Fixture.Kind k : Fixture.Kind.values()) {
            FIXTURES.put(k, block(k.id(), () -> new FixtureBlock(steel().strength(1f, 3f).noOcclusion().noCollission().sound(SoundType.LANTERN), k)));
        }
    }

    /** The refinery's asphalt (0.8), paintable since 0.20 (in its tab and lists, not these). */
    public static final DeferredBlock<MarkedSurfaceBlock> ASPHALT = dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.ASPHALT;
    public static final DeferredBlock<MarkedSurfaceBlock> CONCRETE = block("apron_concrete", () -> new MarkedSurfaceBlock(concrete()));
    public static final DeferredBlock<Block> TURF = block("airfield_turf", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6f).sound(SoundType.GRASS)));
    public static final DeferredBlock<AirfieldBlocks.Windsock> WINDSOCK = block("windsock", () -> new AirfieldBlocks.Windsock(steel().noOcclusion()));
    public static final DeferredBlock<AirfieldBlocks.Sign> HOLD_SIGN = block("holding_sign", () -> new AirfieldBlocks.Sign(steel().strength(1f, 3f).noOcclusion()));
    public static final DeferredBlock<AirfieldBlocks.Floodlight> FLOODLIGHT = block("apron_floodlight", () -> new AirfieldBlocks.Floodlight(steel().noOcclusion()
            .lightLevel(s -> s.getValue(AirfieldBlocks.Floodlight.LIT) ? 15 : 0)));

    // ---------------------------------------------------------------- 0.21: inside the tower

    /** A steel grating stair (the spiral up the tower's shaft; climbed like any stair). */
    public static final DeferredBlock<StairBlock> STEEL_STAIRS = block("steel_stairs", () -> new StairBlock(net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(),
            steel().strength(2f, 6f).noOcclusion()));
    public static final DeferredBlock<AirfieldBlocks.Column> STEEL_COLUMN = block("steel_column", () -> new AirfieldBlocks.Column(steel().strength(2f, 6f).noOcclusion()));
    public static final DeferredBlock<Block> STEEL_GRATING = block("steel_grating", () -> new Block(steel().strength(2f, 6f).noOcclusion()));
    /** An interior lamp (a ceiling panel or a wall lamp), lit by its tower while the tower has power. */
    public static final DeferredBlock<AirfieldBlocks.InteriorLamp> INTERIOR_LAMP = block("interior_lamp", () -> new AirfieldBlocks.InteriorLamp(steel()
            .strength(0.6f, 3f).noOcclusion().noCollission().sound(SoundType.LANTERN).lightLevel(s -> s.getValue(AirfieldBlocks.InteriorLamp.LIT) ? 13 : 0)));
    /** A heavy cable laid in the floor under a cover plate: a whole floor block, a cable to the grid. */
    public static final DeferredBlock<dev.mcmod.hdfv.neoforge.power.CableBlock> CABLE_DUCT = block("cable_duct",
            () -> new dev.mcmod.hdfv.neoforge.power.CableBlock(concrete(), dev.mcmod.hdfv.power.PowerPlant.CableKind.HEAVY, true));
    public static final DeferredBlock<Block> TOWER_FLOOR = block("tower_floor", () -> new Block(concrete()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindsockBlockEntity>> WINDSOCK_ENTITY = BLOCK_ENTITIES.register("windsock",
            () -> BlockEntityType.Builder.of(WindsockBlockEntity::new, WINDSOCK.get()).build(null));

    public static final DeferredItem<LinePaintItem> WHITE_PAINT = ITEMS.registerItem("white_line_paint",
            p -> new LinePaintItem(p, MarkedSurfaceBlock.Paint.WHITE), new Item.Properties().durability(256));
    public static final DeferredItem<LinePaintItem> YELLOW_PAINT = ITEMS.registerItem("yellow_line_paint",
            p -> new LinePaintItem(p, MarkedSurfaceBlock.Paint.YELLOW), new Item.Properties().durability(256));
    public static final DeferredItem<LinePaintItem> THRESHOLD_PAINT = ITEMS.registerItem("threshold_paint",
            p -> new LinePaintItem(p, MarkedSurfaceBlock.Paint.THRESHOLD), new Item.Properties().durability(256));

    public static final DeferredHolder<Feature<?>, AirportFeature> AIRPORT_FEATURE = FEATURES.register("airport", AirportFeature::new);

    public static void tab(CreativeModeTab.Output out) {
        out.accept(TOWER.get());
        for (DeferredBlock<? extends MemberBlock> b : MEMBERS.values()) { out.accept(b.get()); }
        for (DeferredBlock<FixtureBlock> b : FIXTURES.values()) { out.accept(b.get()); }
        out.accept(CONCRETE.get());
        out.accept(TURF.get());
        out.accept(WINDSOCK.get());
        out.accept(HOLD_SIGN.get());
        out.accept(FLOODLIGHT.get());
        for (String id : INSIDE) { out.accept(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id))); }
        out.accept(WHITE_PAINT.get());
        out.accept(YELLOW_PAINT.get());
        out.accept(THRESHOLD_PAINT.get());
    }

    /** The ids of the blocks (each has an item, a recipe, a drop and names). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of(Structures.TOWER));
        out.addAll(MEMBERS.keySet());
        for (Fixture.Kind k : Fixture.Kind.values()) { out.add(k.id()); }
        out.addAll(List.of("apron_concrete", "airfield_turf", "windsock", "holding_sign", "apron_floodlight"));
        out.addAll(INSIDE);
        return out;
    }

    /** 0.21's blocks for inside a tower. */
    public static final List<String> INSIDE = List.of("steel_stairs", "steel_column", "steel_grating", "interior_lamp", "cable_duct", "tower_floor");

    /** The ids of the items that are not blocks. */
    public static List<String> itemIds() { return List.of("white_line_paint", "yellow_line_paint", "threshold_paint"); }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        FEATURES.register(modBus);
        modBus.addListener(AirportNetwork::register);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TOWER_ENTITY.get(), (be, side) -> be.energy()));
    }
}
