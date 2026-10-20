package dev.mcmod.hdfv.neoforge.material;

import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.recipe.TechTree;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tech tree of the recipes a game has (0.23): core TechTree over the recipe manager's recipes that make hdfv's
 * things, the fixed ways and the components' routes. Anything not hdfv's is a thing of the world. Worked out again
 * when the recipes change; the items' tooltips show it.
 */
public final class TechStages {
    private TechStages() {}

    private static volatile Map<String, TechTree.Stage> stages = Map.of();
    private static volatile RecipeManager from;

    /** The stages of the hdfv things a recipe manager's recipes reach (worked out once a manager). */
    public static Map<String, TechTree.Stage> of(RecipeManager manager, HolderLookup.Provider registries) {
        if (manager != from) {
            stages = compute(manager, registries);
            from = manager;
        }
        return stages;
    }

    /** An item stack's id as the tree knows it (a stock's with its alloy). */
    public static String id(ItemStack s) {
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        Alloy a = Stock.alloy(s);
        if (a != null) { return s.is(HdfvMaterials.STOCK_BIT.get()) ? TechTree.bit(a) : TechTree.stock(a); }
        return id;
    }

    private static Set<String> ids(Ingredient i) {
        Set<String> out = new LinkedHashSet<>();
        for (ItemStack s : i.getItems()) { out.add(id(s)); }
        return out;
    }

    static Map<String, TechTree.Stage> compute(RecipeManager manager, HolderLookup.Provider registries) {
        List<TechTree.Way> ways = new ArrayList<>();
        Set<String> world = new HashSet<>(TechTree.MINED);
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            Recipe<?> r = holder.value();
            List<String> outputs = new ArrayList<>();
            ItemStack result;
            try {
                result = r.getResultItem(registries);
            } catch (RuntimeException e) {
                continue;
            }
            if (result.isEmpty()) { continue; }
            outputs.add(id(result));
            List<Set<String>> needs = new ArrayList<>();
            String where = TechTree.CRAFTING;
            switch (r) {
                case AlloyingRecipe a -> {
                    for (SizedIngredient s : a.ingredients()) { needs.add(ids(s.ingredient())); }
                    if (!a.byproduct().isEmpty()) { outputs.add(id(a.byproduct())); }
                    needs.add(Set.of("hdfv:alloy_furnace"));
                    where = TechTree.ALLOYING;
                }
                case AssemblingRecipe a -> {
                    for (SizedIngredient s : a.ingredients()) { needs.add(ids(s.ingredient())); }
                    needs.add(Set.of("hdfv:assembly_bench"));
                    where = TechTree.ASSEMBLING;
                }
                case ElectricRecipe e -> {
                    for (SizedIngredient s : e.ingredients()) { needs.add(ids(s.ingredient())); }
                    for (ItemStack b : e.byproducts()) { outputs.add(id(b)); }
                    boolean arc = e.machine() == ElectricRecipe.Machine.ARC_FURNACE;
                    needs.add(Set.of(arc ? "hdfv:arc_furnace" : "hdfv:electrolysis_cell"));
                    needs.add(Set.of(TechTree.POWER));
                    where = arc ? TechTree.ARC : TechTree.CELL;
                }
                default -> {
                    for (Ingredient i : r.getIngredients()) { if (!i.isEmpty()) { needs.add(ids(i)); } }
                    if (r instanceof AbstractCookingRecipe) { where = TechTree.SMELTING; }
                }
            }
            if (outputs.stream().noneMatch(o -> o.startsWith(Hdfv.MOD_ID + ":"))) { continue; }
            for (Set<String> n : needs) { for (String i : n) { if (!i.startsWith(Hdfv.MOD_ID + ":") && !i.startsWith("powered:")) { world.add(i); } } }
            for (String o : outputs) { ways.add(new TechTree.Way(o, where, needs, 0)); }
        }
        ways.addAll(TechTree.fixed());
        ways.addAll(TechTree.components());
        return TechTree.stages(ways, world);
    }
}
