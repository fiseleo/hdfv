package dev.mcmod.hdfv.neoforge.refit;

import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
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
 * The refit shop (0.16, the user's 「加入載具調整系統 可以有自由外觀和大小」「車燈也是要自由擺放」): its controller, the
 * lamp units its lamps take, its screen.
 */
public final class HdfvRefit {
    private HdfvRefit() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion();
    }

    public static final DeferredBlock<RefitShopBlock> CONTROLLER = BLOCKS.register("refit_shop_controller", () -> new RefitShopBlock(metal()));
    static { ITEMS.registerSimpleBlockItem("refit_shop_controller", CONTROLLER); }
    /** A vehicle lamp: housing, lens and bulb or LEDs, ready to fit (one a lamp; a pair is two). */
    public static final DeferredItem<Item> LAMP_UNIT = ITEMS.registerSimpleItem("lamp_unit");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefitShopBlockEntity>> SHOP = BLOCK_ENTITIES.register("refit_shop",
            () -> BlockEntityType.Builder.of(RefitShopBlockEntity::new, CONTROLLER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<RefitShopMenu>> MENU = MENUS.register("refit_shop", () -> IMenuTypeExtension.create(RefitShopMenu::new));

    public static void tab(CreativeModeTab.Output out) {
        out.accept(CONTROLLER.get());
        out.accept(LAMP_UNIT.get());
    }

    public static List<String> ids() { return List.of("refit_shop_controller", "lamp_unit"); }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(RefitNetwork::register);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, SHOP.get(), (be, side) -> be.energy);
        });
    }
}
