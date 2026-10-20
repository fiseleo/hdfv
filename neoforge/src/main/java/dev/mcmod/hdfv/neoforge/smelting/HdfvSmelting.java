package dev.mcmod.hdfv.neoforge.smelting;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.material.ElectricRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
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
 * The electric smelting machines (0.23; the user chose "電弧爐＋電解槽"): the electric arc furnace (steels, ferroalloys,
 * superalloys, titanium slag and sponge, pitch carbon fibre) and the electrolysis cell (aluminium, magnesium, ammonium
 * perchlorate). One block entity for both; they glow while they work.
 */
public final class HdfvSmelting {
    private HdfvSmelting() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(4.0f, 6.0f).sound(SoundType.METAL).noOcclusion()
                .lightLevel(s -> s.getValue(SmelterBlock.LIT) ? 12 : 0);
    }

    public static final DeferredBlock<SmelterBlock> ARC_FURNACE = BLOCKS.registerBlock("arc_furnace", p -> new SmelterBlock(ElectricRecipe.Machine.ARC_FURNACE, p), props());
    public static final DeferredBlock<SmelterBlock> ELECTROLYSIS_CELL = BLOCKS.registerBlock("electrolysis_cell", p -> new SmelterBlock(ElectricRecipe.Machine.ELECTROLYSIS, p), props());
    public static final DeferredItem<BlockItem> ARC_FURNACE_ITEM = ITEMS.registerSimpleBlockItem("arc_furnace", ARC_FURNACE);
    public static final DeferredItem<BlockItem> ELECTROLYSIS_CELL_ITEM = ITEMS.registerSimpleBlockItem("electrolysis_cell", ELECTROLYSIS_CELL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelterBlockEntity>> SMELTER_ENTITY = BLOCK_ENTITIES.register("smelter",
            () -> BlockEntityType.Builder.of(SmelterBlockEntity::new, ARC_FURNACE.get(), ELECTROLYSIS_CELL.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<SmelterMenu>> SMELTER_MENU = MENUS.register("smelter", () -> IMenuTypeExtension.create(SmelterMenu::new));

    public static void tab(CreativeModeTab.Output out) {
        out.accept(ARC_FURNACE_ITEM.get());
        out.accept(ELECTROLYSIS_CELL_ITEM.get());
    }

    /** Its blocks' ids (for tests). */
    public static List<String> ids() { return List.of("arc_furnace", "electrolysis_cell"); }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, SMELTER_ENTITY.get(), (be, side) -> be.energy());
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, SMELTER_ENTITY.get(), (be, side) -> be.handler());
        });
    }
}
