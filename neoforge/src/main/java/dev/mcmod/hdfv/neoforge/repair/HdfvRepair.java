package dev.mcmod.hdfv.neoforge.repair;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.repair.FieldRepair;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Repairs (0.25, the user 2026-11-12: a repair shop and repairs on the spot; past 80 % a part is replaced): the repair
 * shop's controller, its block entity and screen, and the two hand kits (core repair.FieldRepair). The shop's rules and
 * the job are core's (RepairRules, RepairJob); its assets are tools/repair_assets.py's, its recipes tools/recipes.py's.
 */
public final class HdfvRepair {
    private HdfvRepair() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);

    public static final DeferredBlock<RepairShopBlock> CONTROLLER = BLOCKS.register("repair_shop_controller", () -> new RepairShopBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    static { ITEMS.registerSimpleBlockItem("repair_shop_controller", CONTROLLER); }

    /** The hand kits by kind (each wears out after its Kit.uses). */
    public static final Map<FieldRepair.Kit, DeferredItem<FieldKitItem>> KITS = new EnumMap<>(FieldRepair.Kit.class);
    static {
        for (FieldRepair.Kit k : FieldRepair.Kit.values()) {
            KITS.put(k, ITEMS.registerItem(k.id(), p -> new FieldKitItem(k, p), new Item.Properties().durability(k.uses)));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RepairShopBlockEntity>> SHOP = BLOCK_ENTITIES.register("repair_shop",
            () -> BlockEntityType.Builder.of(RepairShopBlockEntity::new, CONTROLLER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<RepairShopMenu>> MENU = MENUS.register("repair_shop", () -> IMenuTypeExtension.create(RepairShopMenu::new));

    public static DeferredItem<FieldKitItem> kit(FieldRepair.Kit k) { return KITS.get(k); }

    public static void tab(CreativeModeTab.Output out) {
        out.accept(CONTROLLER.get());
        for (DeferredItem<FieldKitItem> k : KITS.values()) { out.accept(k.get()); }
    }

    /** The ids of the blocks and items (each has a recipe, a model, names; the block a drop). */
    public static List<String> ids() {
        List<String> out = new java.util.ArrayList<>(List.of("repair_shop_controller"));
        for (FieldRepair.Kit k : FieldRepair.Kit.values()) { out.add(k.id()); }
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(RepairNetwork::register);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) ->
                e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, SHOP.get(), (be, side) -> be.energy));
        NeoForge.EVENT_BUS.addListener(FieldKits::interact);
    }
}
