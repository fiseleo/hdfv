package dev.mcmod.hdfv.neoforge.material;

import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Ores, the alloy furnace and stock in a running server (0.7). */
@PrefixGameTestTemplate(false)
public final class MaterialGameTests {
    private MaterialGameTests() {}

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 800)
    public static void theAlloyFurnaceMakesSteelFromIronAndCoke(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, HdfvMaterials.ALLOY_FURNACE.get());
        AlloyFurnaceBlockEntity be = (AlloyFurnaceBlockEntity) h.getBlockEntity(pos);
        be.items.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        be.items.setItem(1, new ItemStack(HdfvMaterials.MATERIAL_ITEMS.get("coke").get(), 1));
        be.items.setItem(AlloyFurnaceBlockEntity.FUEL, new ItemStack(Items.COAL, 1));
        h.succeedWhen(() -> {
            ItemStack out = be.items.getItem(AlloyFurnaceBlockEntity.RESULT);
            h.assertTrue(out.is(HdfvMaterials.STOCK.get()) && out.getCount() == 4 && Stock.alloy(out) == Alloy.STEEL, "Four steel stock: " + out);
            h.assertTrue(be.items.getItem(0).isEmpty() && be.items.getItem(1).isEmpty(), "Inputs used");
            h.assertTrue(Stock.kg(out) == 40, "40 kg");
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void stockSplitsIntoBitsAndWrongInputsDoNothing(GameTestHelper h) {
        var level = h.getLevel();
        CraftingInput grid = CraftingInput.of(1, 1, List.of(Stock.of(Alloy.TITANIUM, 1)));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        h.assertTrue(recipe.isPresent(), "A stock splits");
        ItemStack bits = recipe.get().value().assemble(grid, level.registryAccess());
        h.assertTrue(bits.is(HdfvMaterials.STOCK_BIT.get()) && bits.getCount() == 10 && Stock.alloy(bits) == Alloy.TITANIUM, "Ten titanium bits: " + bits);
        // ten bits remelt into one stock
        var remelt = level.getRecipeManager().getRecipeFor(HdfvMaterials.ALLOYING.get(),
                new AlloyingRecipe.Input(List.of(bits, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)), level);
        h.assertTrue(remelt.isPresent() && Stock.alloy(remelt.get().value().result()) == Alloy.TITANIUM, "Bits remelt");
        // iron with no coke is no recipe; an extra item spoils one
        h.assertTrue(level.getRecipeManager().getRecipeFor(HdfvMaterials.ALLOYING.get(),
                new AlloyingRecipe.Input(List.of(new ItemStack(Items.IRON_INGOT, 4), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)), level).isEmpty(), "No steel without coke");
        h.assertTrue(level.getRecipeManager().getRecipeFor(HdfvMaterials.ALLOYING.get(), new AlloyingRecipe.Input(List.of(new ItemStack(Items.IRON_INGOT, 4),
                new ItemStack(HdfvMaterials.MATERIAL_ITEMS.get("coke").get()), new ItemStack(Items.DIRT), ItemStack.EMPTY)), level).isEmpty(), "Dirt spoils the charge");
        h.succeed();
    }
}
