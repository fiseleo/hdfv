package dev.mcmod.hdfv.neoforge.compat;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.material.ElectricRecipe;
import dev.mcmod.hdfv.neoforge.smelting.HdfvSmelting;
import dev.mcmod.hdfv.recipe.Metallurgy;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The electric arc furnace's and the electrolysis cell's recipes in JEI (0.23): up to four inputs, the result and up to
 * two by-products, the energy a batch takes (kWh, from AeroCore's metallurgy) and the work time at the machine's rated
 * power.
 */
final class SmeltingCategory implements IRecipeCategory<ElectricRecipe> {
    static final RecipeType<ElectricRecipe> ARC = RecipeType.create(Hdfv.MOD_ID, "electric_furnace", ElectricRecipe.class);
    static final RecipeType<ElectricRecipe> CELL = RecipeType.create(Hdfv.MOD_ID, "electrolysis", ElectricRecipe.class);
    private final IDrawable icon;
    private final boolean arc;

    SmeltingCategory(IGuiHelper gui, boolean arc) {
        this.arc = arc;
        icon = gui.createDrawableItemStack(new ItemStack(arc ? HdfvSmelting.ARC_FURNACE_ITEM.get() : HdfvSmelting.ELECTROLYSIS_CELL_ITEM.get()));
    }

    @Override public RecipeType<ElectricRecipe> getRecipeType() { return arc ? ARC : CELL; }
    @Override public Component getTitle() { return Component.translatable(arc ? "block.hdfv.arc_furnace" : "block.hdfv.electrolysis_cell"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 140; }
    @Override public int getHeight() { return 56; }

    @Override public void setRecipe(IRecipeLayoutBuilder b, ElectricRecipe r, IFocusGroup focuses) {
        List<SizedIngredient> in = r.ingredients();
        for (int i = 0; i < in.size(); i++) {
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack s : in.get(i).getItems()) { stacks.add(s.copyWithCount(in.get(i).count())); }
            b.addSlot(RecipeIngredientRole.INPUT, 1 + 18 * (i % 2), 1 + 18 * (i / 2)).addItemStacks(stacks).setStandardSlotBackground();
        }
        b.addSlot(RecipeIngredientRole.OUTPUT, 75, 1).addItemStack(r.result()).setOutputSlotBackground();
        for (int k = 0; k < r.byproducts().size(); k++) {
            b.addSlot(RecipeIngredientRole.OUTPUT, 101 + 18 * k, 1).addItemStack(r.byproducts().get(k)).setStandardSlotBackground();
        }
    }

    @Override public void draw(ElectricRecipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "→", 52, 6, 0xff808080, false);
        double kw = (arc ? Metallurgy.ARC_FURNACE_W : Metallurgy.CELL_W) / 1000, hours = r.kwh() / kw;
        g.drawString(font, Component.translatable("hdfv.jei.smelting", String.format(Locale.ROOT, "%.1f", r.kwh()), String.format(Locale.ROOT, "%.0f", kw),
                String.format(Locale.ROOT, "%.2f", hours)), 1, 40, 0xff808080, false);
    }
}
