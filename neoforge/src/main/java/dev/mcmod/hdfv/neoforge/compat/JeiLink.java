package dev.mcmod.hdfv.neoforge.compat;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

/**
 * JEI's recipes from the handbook (0.24): HdfvJeiPlugin hands over JEI's runtime when JEI has started. Only touched
 * when JEI is loaded (the handbook checks the mod list first).
 */
public final class JeiLink {
    private JeiLink() {}

    static volatile IJeiRuntime runtime;

    /** Shows the recipes making an item; false without JEI's runtime. */
    public static boolean showRecipes(ItemStack stack) {
        IJeiRuntime r = runtime;
        if (r == null) { return false; }
        r.getRecipesGui().show(r.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
        return true;
    }
}
