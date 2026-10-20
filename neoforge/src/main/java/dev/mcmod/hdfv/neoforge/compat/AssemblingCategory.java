package dev.mcmod.hdfv.neoforge.compat;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.material.AssemblingRecipe;
import dev.mcmod.hdfv.manufacture.Station;
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

/** The assembly bench's recipes in JEI (0.23): the components (up to 18, with counts), the machine it makes, the fitting time. */
final class AssemblingCategory implements IRecipeCategory<AssemblingRecipe> {
    static final RecipeType<AssemblingRecipe> TYPE = RecipeType.create(Hdfv.MOD_ID, "assembling", AssemblingRecipe.class);
    private final IDrawable icon;

    AssemblingCategory(IGuiHelper gui) { icon = gui.createDrawableItemStack(new ItemStack(HdfvMachines.MACHINES.get(Station.ASSEMBLY_BENCH).get())); }

    @Override public RecipeType<AssemblingRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("hdfv.jei.assembling"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 150; }
    @Override public int getHeight() { return 64; }

    @Override public void setRecipe(IRecipeLayoutBuilder b, AssemblingRecipe r, IFocusGroup focuses) {
        List<SizedIngredient> in = r.ingredients();
        for (int i = 0; i < in.size(); i++) {
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack s : in.get(i).getItems()) { stacks.add(s.copyWithCount(in.get(i).count())); }
            b.addSlot(RecipeIngredientRole.INPUT, 1 + 18 * (i % 6), 1 + 18 * (i / 6)).addItemStacks(stacks).setStandardSlotBackground();
        }
        b.addSlot(RecipeIngredientRole.OUTPUT, 128, 19).addItemStack(r.result()).setOutputSlotBackground();
    }

    @Override public void draw(AssemblingRecipe r, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "→", 113, 24, 0xff808080, false);
        double h = r.seconds() / 3600.0;
        g.drawString(font, Component.translatable("hdfv.jei.labour", String.format(Locale.ROOT, "%.2f", h)), 1, 56, 0xff808080, false);
    }

}
