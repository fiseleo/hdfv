package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock;
import dev.mcmod.hdfv.neoforge.multiblock.MemberBlock;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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

/** The oil field (0.8): seeps, the geophone, the drilling rig and its derrick, the wellhead, the pump jack. */
public final class HdfvPetroleum {
    private HdfvPetroleum() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);

    /** The screens of the oil field's, the refinery's and the forecourt's blocks (0.9). */
    public static final DeferredHolder<MenuType<?>, MenuType<PetroleumMenu>> MENU = MENUS.register("petroleum", () -> IMenuTypeExtension.create(PetroleumMenu::new));

    private static BlockBehaviour.Properties steel() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static <B extends Block> DeferredBlock<B> block(String id, Supplier<B> make) {
        DeferredBlock<B> b = BLOCKS.register(id, make);
        ITEMS.registerSimpleBlockItem(id, b);
        return b;
    }

    public static final DeferredBlock<Block> OIL_SEEP = block("oil_seep",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.MUD)));
    public static final DeferredItem<GeophoneItem> GEOPHONE = ITEMS.registerItem("geophone", GeophoneItem::new, new Item.Properties().stacksTo(1));

    /** The rig's and the jack's members, by kind. */
    public static final Map<String, DeferredBlock<MemberBlock>> MEMBERS = new LinkedHashMap<>();

    static {
        for (String k : List.of(Structures.RIG_FLOOR, Structures.DERRICK, Structures.CROWN_BLOCK, Structures.RIG_ROTARY, Structures.JACK_BASE,
                Structures.SAMSON_POST, Structures.JACK_CRANK, Structures.WALKING_BEAM, Structures.HORSEHEAD)) {
            BlockBehaviour.Properties p = k.equals(Structures.DERRICK) ? steel().noOcclusion() : steel();
            boolean jack = List.of(Structures.JACK_BASE, Structures.SAMSON_POST, Structures.JACK_CRANK, Structures.WALKING_BEAM, Structures.HORSEHEAD).contains(k);
            MEMBERS.put(k, block(k, () -> new MemberBlock(p, k, jack)));   // the jack's parts are drawn by its renderer once formed
        }
    }

    public static final DeferredBlock<WellheadBlock> WELLHEAD = block(Structures.WELLHEAD, () -> new WellheadBlock(steel().noOcclusion()));
    public static final DeferredBlock<ControllerBlock> RIG = block(Structures.RIG, () -> new ControllerBlock(steel(), Structures.RIG, HdfvPetroleum::rigType));
    public static final DeferredBlock<ControllerBlock> JACK = block(Structures.PUMP_JACK, () -> new ControllerBlock(steel(), Structures.PUMP_JACK, HdfvPetroleum::jackType));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WellheadBlockEntity>> WELLHEAD_ENTITY = BLOCK_ENTITIES.register("wellhead",
            () -> BlockEntityType.Builder.of(WellheadBlockEntity::new, WELLHEAD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrillingRigBlockEntity>> RIG_ENTITY = BLOCK_ENTITIES.register("drilling_rig",
            () -> BlockEntityType.Builder.of(DrillingRigBlockEntity::new, RIG.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpJackBlockEntity>> JACK_ENTITY = BLOCK_ENTITIES.register("pump_jack",
            () -> BlockEntityType.Builder.of(PumpJackBlockEntity::new, JACK.get()).build(null));

    private static BlockEntityType<DrillingRigBlockEntity> rigType() { return RIG_ENTITY.get(); }
    private static BlockEntityType<PumpJackBlockEntity> jackType() { return JACK_ENTITY.get(); }

    public static final DeferredHolder<Feature<?>, OilSeepFeature> OIL_SEEP_FEATURE = FEATURES.register("oil_seep", OilSeepFeature::new);

    public static void tab(CreativeModeTab.Output out) {
        out.accept(GEOPHONE.get());
        out.accept(OIL_SEEP.get());
        out.accept(RIG.get());
        for (DeferredBlock<MemberBlock> b : MEMBERS.values()) { out.accept(b.get()); }
        out.accept(WELLHEAD.get());
        out.accept(JACK.get());
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of("oil_seep", Structures.RIG, Structures.WELLHEAD, Structures.PUMP_JACK));
        out.addAll(MEMBERS.keySet());
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FEATURES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(PetroleumNetwork::register);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.FluidHandler.BLOCK, WELLHEAD_ENTITY.get(), (be, side) -> be.handler());
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, RIG_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, JACK_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RIG_ENTITY.get(), (be, side) -> be.casing());
        });
    }
}
