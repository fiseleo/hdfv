package dev.mcmod.hdfv.neoforge.paint;

import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
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

import java.util.List;

/**
 * The paint booth (0.13, the user's 「加入痛車」「加入烤漆房」): its controller and extraction fan, the paint cans and sticker
 * film it uses, its screen; and the stickers' pictures (DecalNetwork, DecalStore, DecalCommands).
 */
public final class HdfvPaint {
    private HdfvPaint() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion();
    }

    public static final DeferredBlock<PaintBoothBlock> CONTROLLER = BLOCKS.register("paint_booth_controller", () -> new PaintBoothBlock(metal()));
    public static final DeferredBlock<ExhaustBlock> EXHAUST = BLOCKS.register("paint_booth_exhaust", () -> new ExhaustBlock(metal()));
    static {
        ITEMS.registerSimpleBlockItem("paint_booth_controller", CONTROLLER);
        ITEMS.registerSimpleBlockItem("paint_booth_exhaust", EXHAUST);
    }
    /** A litre of paint (any colour: the booth mixes it), and a square metre of printable vinyl film. */
    public static final DeferredItem<Item> PAINT_CAN = ITEMS.registerSimpleItem("paint_can", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> STICKER_FILM = ITEMS.registerSimpleItem("sticker_film");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PaintBoothBlockEntity>> BOOTH = BLOCK_ENTITIES.register("paint_booth",
            () -> BlockEntityType.Builder.of(PaintBoothBlockEntity::new, CONTROLLER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<PaintBoothMenu>> MENU = MENUS.register("paint_booth", () -> IMenuTypeExtension.create(PaintBoothMenu::new));

    public static void tab(CreativeModeTab.Output out) {
        out.accept(CONTROLLER.get());
        out.accept(EXHAUST.get());
        out.accept(PAINT_CAN.get());
        out.accept(STICKER_FILM.get());
    }

    public static List<String> ids() { return List.of("paint_booth_controller", "paint_booth_exhaust", "paint_can", "sticker_film"); }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(PaintBoothNetwork::register);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, BOOTH.get(), (be, side) -> be.energy);
        });
    }
}
