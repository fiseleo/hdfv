package dev.mcmod.hdfv.neoforge.material;

import dev.mcmod.hdfv.manufacture.Alloy;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Cutting a stock into ten 1 kg bits in a crafting grid, for any alloy (0.7, special recipe hdfv:stock_split). Bits
 * go back together by remelting ten of them in the alloy furnace (its recipes).
 */
public final class StockRecipes {
    private StockRecipes() {}

    /** One stock alone in the grid: ten bits of its alloy. */
    public static final class Split extends CustomRecipe {
        public Split(CraftingBookCategory category) { super(category); }

        @Override public boolean matches(CraftingInput in, Level level) { return only(in) != null; }
        @Override public ItemStack assemble(CraftingInput in, HolderLookup.Provider registries) {
            Alloy a = only(in);
            return a == null ? ItemStack.EMPTY : Stock.bits(a, 10);
        }
        @Override public boolean canCraftInDimensions(int w, int h) { return w * h >= 1; }
        @Override public RecipeSerializer<?> getSerializer() { return HdfvMaterials.STOCK_SPLIT.get(); }
    }

    /** The alloy when the grid holds exactly one stock and nothing else; null otherwise. */
    static Alloy only(CraftingInput in) {
        Alloy alloy = null;
        int n = 0;
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            if (s.isEmpty()) { continue; }
            if (!s.is(HdfvMaterials.STOCK.get())) { return null; }
            alloy = Stock.alloy(s);
            n++;
        }
        return n == 1 ? alloy : null;
    }
}
