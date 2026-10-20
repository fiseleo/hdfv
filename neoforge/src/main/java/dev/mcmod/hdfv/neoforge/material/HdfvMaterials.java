package dev.mcmod.hdfv.neoforge.material;

import com.mojang.serialization.Codec;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
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
import java.util.Map;

/**
 * Ores, metals, chemicals and stock (0.7): the ores of the metals the designs use and vanilla lacks (bauxite,
 * ilmenite, nickel, chromite, magnesite, tin, zinc), their raw items and ingots, coke and what coal tar makes (resin,
 * rubber, carbon fibre) with glass fibre, the stock items machines draw from, and the alloy furnace with its recipes.
 */
public final class HdfvMaterials {
    private HdfvMaterials() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hdfv.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hdfv.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Hdfv.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Hdfv.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Hdfv.MOD_ID);

    /** A stock item's alloy (Alloy.id()). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ALLOY = COMPONENTS.registerComponentType("alloy",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** An ore: its id, raw item, whether it has a deepslate form, and whether it needs an iron pick (else stone). */
    public record Ore(String id, String raw, boolean deepslate, boolean iron) {}

    public static final List<Ore> ORES = List.of(new Ore("bauxite", "bauxite", false, false), new Ore("ilmenite", "raw_ilmenite", true, true),
            new Ore("nickel", "raw_nickel", true, true), new Ore("chromite", "chromite", true, true), new Ore("magnesite", "magnesite", false, false),
            new Ore("tin", "raw_tin", true, false), new Ore("zinc", "raw_zinc", true, false),
            // 0.17: almandine garnet, crushed for the waterjet's abrasive
            new Ore("garnet", "raw_garnet", true, false));

    /** Base metal ingots refined from the ores. */
    public static final List<String> INGOTS = List.of("aluminium_ingot", "titanium_ingot", "nickel_ingot", "chromium_ingot", "magnesium_ingot", "tin_ingot", "zinc_ingot");
    /** Coke and the products of coal tar, and glass fibre. */
    public static final List<String> CHEMICALS = List.of("coke", "coal_tar", "epoxy_resin", "synthetic_rubber", "carbon_fibre", "glass_fibre");

    public static final Map<String, DeferredBlock<Block>> ORE_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> MATERIAL_ITEMS = new LinkedHashMap<>();

    static {
        for (Ore o : ORES) {
            ore(o.id() + "_ore", false);
            if (o.deepslate()) { ore("deepslate_" + o.id() + "_ore", true); }
            if (!MATERIAL_ITEMS.containsKey(o.raw())) { MATERIAL_ITEMS.put(o.raw(), ITEMS.registerSimpleItem(o.raw())); }
        }
        for (String s : INGOTS) { MATERIAL_ITEMS.put(s, ITEMS.registerSimpleItem(s)); }
        for (String s : CHEMICALS) { MATERIAL_ITEMS.put(s, ITEMS.registerSimpleItem(s)); }
    }

    private static void ore(String id, boolean deepslate) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(deepslate ? MapColor.DEEPSLATE : MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(deepslate ? 4.5f : 3.0f, 3.0f).sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE);
        DeferredBlock<Block> b = BLOCKS.register(id, () -> new DropExperienceBlock(UniformInt.of(0, 0), p));
        ORE_BLOCKS.put(id, b);
        ITEMS.registerSimpleBlockItem(id, b);
    }

    public static final DeferredItem<Stock.StockItem> STOCK = ITEMS.registerItem("stock", p -> new Stock.StockItem(p, false), new Item.Properties());
    public static final DeferredItem<Stock.StockItem> STOCK_BIT = ITEMS.registerItem("stock_bit", p -> new Stock.StockItem(p, true), new Item.Properties());

    public static final DeferredBlock<AlloyFurnaceBlock> ALLOY_FURNACE = BLOCKS.registerBlock("alloy_furnace", AlloyFurnaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(3.5f).sound(SoundType.STONE)
                    .lightLevel(s -> s.getValue(AlloyFurnaceBlock.LIT) ? 13 : 0).noOcclusion());
    public static final DeferredItem<BlockItem> ALLOY_FURNACE_ITEM = ITEMS.registerSimpleBlockItem("alloy_furnace", ALLOY_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlloyFurnaceBlockEntity>> ALLOY_FURNACE_ENTITY = BLOCK_ENTITIES.register("alloy_furnace",
            () -> BlockEntityType.Builder.of(AlloyFurnaceBlockEntity::new, ALLOY_FURNACE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<AlloyFurnaceMenu>> ALLOY_FURNACE_MENU = MENUS.register("alloy_furnace",
            () -> IMenuTypeExtension.create(AlloyFurnaceMenu::new));

    public static final DeferredHolder<RecipeType<?>, RecipeType<AlloyingRecipe>> ALLOYING = RECIPE_TYPES.register("alloying",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "alloying")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyingRecipe>> ALLOYING_SERIALIZER = RECIPE_SERIALIZERS.register("alloying",
            AlloyingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<StockRecipes.Split>> STOCK_SPLIT = RECIPE_SERIALIZERS.register("stock_split",
            () -> new SimpleCraftingRecipeSerializer<>(StockRecipes.Split::new));

    // 0.23: machines assembled at the bench, the arc furnace's and the electrolysis cell's recipes
    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblingRecipe>> ASSEMBLING = RECIPE_TYPES.register("assembling",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "assembling")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblingRecipe>> ASSEMBLING_SERIALIZER = RECIPE_SERIALIZERS.register("assembling",
            AssemblingRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ElectricRecipe>> ARC_FURNACE = RECIPE_TYPES.register("electric_furnace",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "electric_furnace")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ElectricRecipe>> ARC_FURNACE_SERIALIZER = RECIPE_SERIALIZERS.register("electric_furnace",
            () -> new ElectricRecipe.Serializer(ElectricRecipe.Machine.ARC_FURNACE));
    public static final DeferredHolder<RecipeType<?>, RecipeType<ElectricRecipe>> ELECTROLYSIS = RECIPE_TYPES.register("electrolysis",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "electrolysis")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ElectricRecipe>> ELECTROLYSIS_SERIALIZER = RECIPE_SERIALIZERS.register("electrolysis",
            () -> new ElectricRecipe.Serializer(ElectricRecipe.Machine.ELECTROLYSIS));

    /** Everything of this package for the creative tab, in order. */
    public static void tab(CreativeModeTab.Output out) {
        out.accept(ALLOY_FURNACE_ITEM.get());
        for (DeferredBlock<Block> b : ORE_BLOCKS.values()) { out.accept(b.get()); }
        for (DeferredItem<Item> i : MATERIAL_ITEMS.values()) { out.accept(i.get()); }
        for (Alloy a : Alloy.values()) { out.accept(Stock.of(a, 1)); }
        for (Alloy a : Alloy.values()) { out.accept(Stock.bits(a, 1)); }
    }

    /** Ids of the items the tab shows here (for tests). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of("alloy_furnace", "stock", "stock_bit"));
        out.addAll(ORE_BLOCKS.keySet());
        out.addAll(MATERIAL_ITEMS.keySet());
        return out;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e) ->
                e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, ALLOY_FURNACE_ENTITY.get(), (be, side) -> be.handler(side)));
    }
}
