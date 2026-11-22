package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.WorldTerrain;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * 0.26: the work site: the blocks machines leave behind (loose fill, blasted rock, compacted base, hot asphalt that a
 * roller finishes, fresh concrete that cures, reclaimed asphalt, piles, tunnel lining), the bulk material vehicles
 * carry (BulkCargo), what blocks are to their tools (EarthMaterials, given to AeroCore's WorldTerrain) and the world
 * work of each tick (WorldWork). Assets are tools/machinery_assets.py's, recipes tools/recipes.py's.
 */
public final class HdfvWorksite {
    private HdfvWorksite() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Hdfv.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);

    /** What a vehicle carries in bulk. */
    public static final Supplier<AttachmentType<BulkCargo>> BULK = ATTACHMENTS.register("bulk_cargo",
            () -> AttachmentType.builder(() -> new BulkCargo()).serialize(new BulkCargo.Serializer()).build());

    private static BlockBehaviour.Properties soil(MapColor c, float strength) {
        return BlockBehaviour.Properties.of().mapColor(c).instrument(NoteBlockInstrument.SNARE).strength(strength).sound(SoundType.GRAVEL);
    }
    private static BlockBehaviour.Properties stone(MapColor c, float strength) {
        return BlockBehaviour.Properties.of().mapColor(c).instrument(NoteBlockInstrument.BASEDRUM).strength(strength, 6f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    /** Dug and dumped soil: it slides like gravel (it has no cohesion left). */
    public static final DeferredBlock<ColoredFallingBlock> LOOSE_FILL = BLOCKS.register("loose_fill",
            () -> new ColoredFallingBlock(new ColorRGBA(0xFF7A5A3C), soil(MapColor.DIRT, 0.5f)));
    /** Broken rock: what a ripper, a hammer or a blast leaves, what a shovel digs. */
    public static final DeferredBlock<ColoredFallingBlock> BLASTED_ROCK = BLOCKS.register("blasted_rock",
            () -> new ColoredFallingBlock(new ColorRGBA(0xFF7D7D7D), soil(MapColor.STONE, 1.2f).sound(SoundType.STONE)));
    /** Fill rolled to its Proctor density: a road's base. */
    public static final DeferredBlock<Block> COMPACTED_BASE = BLOCKS.register("compacted_base", () -> new Block(soil(MapColor.TERRACOTTA_BROWN, 1.4f)));
    /** Hot mix laid by a paver: rolled while hot it is asphalt; cold before it is rolled enough it crumbles. */
    public static final DeferredBlock<HotAsphaltBlock> HOT_ASPHALT = BLOCKS.register("hot_asphalt",
            () -> new HotAsphaltBlock(stone(MapColor.COLOR_BLACK, 0.8f).randomTicks()));
    /** Concrete placed by a pump or a chute: it sets and cures into concrete pavement. */
    public static final DeferredBlock<FreshConcreteBlock> FRESH_CONCRETE = BLOCKS.register("fresh_concrete",
            () -> new FreshConcreteBlock(soil(MapColor.COLOR_LIGHT_GRAY, 0.6f).sound(SoundType.MUD).randomTicks()));
    /** Milled asphalt (RAP): crumbly, goes back to the asphalt plant. */
    public static final DeferredBlock<ColoredFallingBlock> RECLAIMED_ASPHALT = BLOCKS.register("reclaimed_asphalt",
            () -> new ColoredFallingBlock(new ColorRGBA(0xFF2C2C2E), soil(MapColor.COLOR_BLACK, 0.6f)));
    /** A driven pile (precast concrete). */
    public static final DeferredBlock<Block> PILE = BLOCKS.register("pile", () -> new Block(stone(MapColor.COLOR_LIGHT_GRAY, 4f)));
    /** A tunnel's lining segment. */
    public static final DeferredBlock<Block> TUNNEL_LINING = BLOCKS.register("tunnel_lining", () -> new Block(stone(MapColor.COLOR_GRAY, 5f)));

    // the batching plants' controllers (multiblocks: Structures.ASPHALT_PLANT_PATTERN, CONCRETE_PLANT_PATTERN)
    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }
    public static final DeferredBlock<dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock> ASPHALT_PLANT = BLOCKS.register("asphalt_plant_controller",
            () -> new dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock(steel(), dev.mcmod.hdfv.structure.Structures.ASPHALT_PLANT, HdfvWorksite::asphaltType));
    public static final DeferredBlock<dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock> CONCRETE_PLANT = BLOCKS.register("concrete_plant_controller",
            () -> new dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock(steel(), dev.mcmod.hdfv.structure.Structures.CONCRETE_PLANT, HdfvWorksite::concreteType));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<PlantBlockEntity>> ASPHALT_PLANT_ENTITY = BLOCK_ENTITIES.register("asphalt_plant",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(PlantBlockEntity::asphalt, ASPHALT_PLANT.get()).build(null));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<PlantBlockEntity>> CONCRETE_PLANT_ENTITY = BLOCK_ENTITIES.register("concrete_plant",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(PlantBlockEntity::concrete, CONCRETE_PLANT.get()).build(null));
    private static net.minecraft.world.level.block.entity.BlockEntityType<PlantBlockEntity> asphaltType() { return ASPHALT_PLANT_ENTITY.get(); }

    // the tower crane (Structures.TOWER_CRANE_PATTERN): its mast sections, its slewing unit, its controller at the base
    public static final DeferredBlock<dev.mcmod.hdfv.neoforge.multiblock.MemberBlock> CRANE_MAST = BLOCKS.register(dev.mcmod.hdfv.structure.Structures.CRANE_MAST,
            () -> new dev.mcmod.hdfv.neoforge.multiblock.MemberBlock(steel().noOcclusion(), dev.mcmod.hdfv.structure.Structures.CRANE_MAST, true));
    public static final DeferredBlock<dev.mcmod.hdfv.neoforge.multiblock.MemberBlock> CRANE_TOP = BLOCKS.register(dev.mcmod.hdfv.structure.Structures.CRANE_TOP,
            () -> new dev.mcmod.hdfv.neoforge.multiblock.MemberBlock(steel(), dev.mcmod.hdfv.structure.Structures.CRANE_TOP, true));
    public static final DeferredBlock<dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock> TOWER_CRANE = BLOCKS.register("tower_crane_controller",
            () -> new dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock(steel(), dev.mcmod.hdfv.structure.Structures.TOWER_CRANE, HdfvWorksite::towerCraneType));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,
            net.minecraft.world.level.block.entity.BlockEntityType<TowerCraneBlockEntity>> TOWER_CRANE_ENTITY = BLOCK_ENTITIES.register("tower_crane",
            () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(TowerCraneBlockEntity::new, TOWER_CRANE.get()).build(null));
    private static net.minecraft.world.level.block.entity.BlockEntityType<TowerCraneBlockEntity> towerCraneType() { return TOWER_CRANE_ENTITY.get(); }
    private static net.minecraft.world.level.block.entity.BlockEntityType<PlantBlockEntity> concreteType() { return CONCRETE_PLANT_ENTITY.get(); }

    static {
        ITEMS.registerSimpleBlockItem("asphalt_plant_controller", ASPHALT_PLANT);
        ITEMS.registerSimpleBlockItem("concrete_plant_controller", CONCRETE_PLANT);
        ITEMS.registerSimpleBlockItem(dev.mcmod.hdfv.structure.Structures.CRANE_MAST, CRANE_MAST);
        ITEMS.registerSimpleBlockItem(dev.mcmod.hdfv.structure.Structures.CRANE_TOP, CRANE_TOP);
        ITEMS.registerSimpleBlockItem("tower_crane_controller", TOWER_CRANE);
        for (String id : List.of("loose_fill", "blasted_rock", "compacted_base", "hot_asphalt", "fresh_concrete", "reclaimed_asphalt", "pile", "tunnel_lining")) {
            ITEMS.registerSimpleBlockItem(id, () -> BLOCKS.getEntries().stream().filter(e -> e.getId().getPath().equals(id)).findFirst().orElseThrow().get());
        }
    }

    /** The ids of the blocks (each has an item, a drop and names). */
    public static List<String> ids() { return List.of("loose_fill", "blasted_rock", "compacted_base", "hot_asphalt", "fresh_concrete", "reclaimed_asphalt", "pile", "tunnel_lining"); }

    public static void tab(CreativeModeTab.Output out) {
        out.accept(LOOSE_FILL.get()); out.accept(BLASTED_ROCK.get()); out.accept(COMPACTED_BASE.get()); out.accept(HOT_ASPHALT.get());
        out.accept(FRESH_CONCRETE.get()); out.accept(RECLAIMED_ASPHALT.get()); out.accept(PILE.get()); out.accept(TUNNEL_LINING.get());
        out.accept(ASPHALT_PLANT.get()); out.accept(CONCRETE_PLANT.get());
        out.accept(TOWER_CRANE.get()); out.accept(CRANE_MAST.get()); out.accept(CRANE_TOP.get());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> {
            for (var type : List.of(ASPHALT_PLANT_ENTITY.get(), CONCRETE_PLANT_ENTITY.get())) {
                e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.items());
                e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy());
            }
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, TOWER_CRANE_ENTITY.get(), (be, side) -> be.energy());
        });
        modBus.addListener((FMLCommonSetupEvent e) -> WorldTerrain.register(EarthMaterials::resolve));
        NeoForge.EVENT_BUS.addListener((PartVehicle.Stepped e) -> { WorldWork.stepped(e.vehicle()); Cranes.stepped(e.vehicle()); });
    }
}
