package dev.mcmod.hdfv.neoforge.storage;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.storage.StorageKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Storage (0.25): the workshop's storage blocks (core StorageKind: the crate, the steel locker, the parts rack, the
 * shipping container), their one block entity and menu, the mass of items (ItemMass and its data map hdfv:item_mass)
 * and the item tag of what a parts rack takes. Their assets are tools/storage_assets.py's, their recipes
 * tools/recipes.py's, the data map tools/item_mass.py's.
 */
public final class HdfvStorage {
    private HdfvStorage() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);

    /** What a parts rack takes: finished parts, workpieces, standard components, bench assemblies and drawings (data packs may add to it). */
    public static final TagKey<Item> PARTS_RACK = ItemTags.create(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "parts_rack"));

    private static BlockBehaviour.Properties properties(StorageKind kind) {
        if (kind.wooden) {
            return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5f).sound(SoundType.WOOD).ignitedByLava();
        }
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(kind == StorageKind.SHIPPING_CONTAINER ? 5f : 3.5f, 6f).sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    /** The storage blocks by kind. */
    public static final Map<StorageKind, DeferredBlock<StorageBlock>> STORES = new EnumMap<>(StorageKind.class);

    static {
        for (StorageKind kind : StorageKind.values()) {
            DeferredBlock<StorageBlock> b = BLOCKS.register(kind.id(), () -> new StorageBlock(kind, properties(kind)));
            ITEMS.registerSimpleBlockItem(kind.id(), b);
            STORES.put(kind, b);
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageBlockEntity>> ENTITY = BLOCK_ENTITIES.register("storage",
            () -> BlockEntityType.Builder.of(StorageBlockEntity::new, STORES.values().stream().map(DeferredBlock::get).toArray(StorageBlock[]::new)).build(null));
    /** The stores' menu: a storage block's, and any other container's (StorageMenu). */
    public static final DeferredHolder<MenuType<?>, MenuType<StorageMenu>> MENU = MENUS.register("storage", () -> IMenuTypeExtension.create(StorageMenu::new));

    public static DeferredBlock<StorageBlock> block(StorageKind kind) { return STORES.get(kind); }

    public static void tab(CreativeModeTab.Output out) {
        for (DeferredBlock<StorageBlock> b : STORES.values()) { out.accept(b.get()); }
    }

    /** The ids of the blocks (each has an item, a recipe, a drop and names). */
    public static List<String> ids() { return StorageKind.ids(); }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ENTITY.get(), (be, side) -> be.handler()));
        modBus.addListener((RegisterDataMapTypesEvent e) -> e.register(ItemMass.DATA_MAP));
    }
}
