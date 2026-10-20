package dev.mcmod.hdfv.neoforge.compat;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.material.AlloyingRecipe;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.Arrays;
import java.util.List;

/** The alloy furnace's recipes in JEI (0.7): up to four inputs, the result and any byproduct, and the time. */
final class AlloyingCategory implements IRecipeCategory<AlloyingRecipe> {
    static final RecipeType<AlloyingRecipe> TYPE = RecipeType.create(Hdfv.MOD_ID, "alloying", AlloyingRecipe.class);
    private final IDrawable icon;

    AlloyingCategory(IGuiHelper gui) { icon = gui.createDrawableItemStack(new ItemStack(HdfvMaterials.ALLOY_FURNACE_ITEM.get())); }

    @Override public RecipeType<AlloyingRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("block.hdfv.alloy_furnace"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 120; }
    @Override public int getHeight() { return 46; }

    @Override public void setRecipe(IRecipeLayoutBuilder b, AlloyingRecipe r, IFocusGroup focuses) {
        List<SizedIngredient> in = r.ingredients();
        for (int i = 0; i < in.size(); i++) {
            b.addSlot(RecipeIngredientRole.INPUT, 1 + 18 * (i % 2), 1 + 18 * (i / 2)).addItemStacks(Arrays.asList(in.get(i).getItems())).setStandardSlotBackground();
        }
        b.addSlot(RecipeIngredientRole.OUTPUT, 75, 1).addItemStack(r.result()).setOutputSlotBackground();
        if (!r.byproduct().isEmpty()) { b.addSlot(RecipeIngredientRole.OUTPUT, 101, 1).addItemStack(r.byproduct()).setStandardSlotBackground(); }
    }

    @Override public void draw(AlloyingRecipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "→", 52, 6, 0xff808080, false);
        g.drawString(font, Component.translatable("hdfv.jei.seconds", String.format(java.util.Locale.ROOT, "%.0f", r.ticks() / 20.0)), 44, 37, 0xff808080, false);
    }
}
