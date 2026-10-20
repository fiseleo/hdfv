package dev.mcmod.hdfv.neoforge.compat;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.fuel.Fuelling;
import dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery;
import dev.mcmod.hdfv.petroleum.Refining;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The refinery's and chemical plant's conversions in JEI (0.8): what 100 kg of feed gives (fluids in litres, solids
 * in items), what the unit burns for its heat and the electricity it takes.
 */
final class RefiningCategory implements IRecipeCategory<Refining.Yield> {
    static final RecipeType<Refining.Yield> TYPE = RecipeType.create(Hdfv.MOD_ID, "refining", Refining.Yield.class);
    /** The basis shown, kg of feed. */
    static final double BASIS_KG = 100;
    private final IDrawable icon;

    RefiningCategory(IGuiHelper gui) { icon = gui.createDrawableItemStack(new ItemStack(HdfvRefinery.COLUMN.get())); }

    @Override public RecipeType<Refining.Yield> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("hdfv.jei.refining"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 150; }
    @Override public int getHeight() { return 66; }

    private static long litres(String fluid, double kg) { return Math.max(1, Math.round(kg / Fuelling.density(fluid) * 1000)); }

    @Override public void setRecipe(IRecipeLayoutBuilder b, Refining.Yield y, IFocusGroup focuses) {
        int i = 0;
        for (Map.Entry<String, Double> f : new TreeMap<>(y.feeds()).entrySet()) {
            b.addSlot(RecipeIngredientRole.INPUT, 1, 11 + 18 * i++).addFluidStack(HdfvFluidRegistry.fluid(f.getKey()), litres(f.getKey(), BASIS_KG * f.getValue()))
                    .setStandardSlotBackground();
        }
        List<Map.Entry<String, Double>> products = new ArrayList<>(y.products().entrySet());
        products.sort(Map.Entry.comparingByValue((a, c) -> Double.compare(c, a)));
        int j = 0;
        for (Map.Entry<String, Double> p : products) {
            int x = 50 + 20 * (j % 5), yy = 11 + 20 * (j / 5);
            double kg = BASIS_KG * p.getValue();
            if (Refining.item(p.getKey())) {
                int n = (int) Math.max(1, Math.round(kg / Refining.ITEM_KG.get(p.getKey())));
                b.addSlot(RecipeIngredientRole.OUTPUT, x, yy).addItemStack(new ItemStack(HdfvRefinery.product(p.getKey()), n)).setOutputSlotBackground();
            } else {
                b.addSlot(RecipeIngredientRole.OUTPUT, x, yy).addFluidStack(HdfvFluidRegistry.fluid(p.getKey()), litres(p.getKey(), kg)).setStandardSlotBackground();
            }
            j++;
        }
    }

    @Override public void draw(Refining.Yield y, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("hdfv.refinery.unit." + y.name()), 1, 0, 0xff404040, false);
        g.drawString(font, "→", 30, 15, 0xff808080, false);
        g.drawString(font, Component.translatable("hdfv.jei.refining.basis", String.format(Locale.ROOT, "%.0f", BASIS_KG),
                String.format(Locale.ROOT, "%.1f", 100 * y.burnt()), String.format(Locale.ROOT, "%.0f", y.gridJPerKg() * 1000 / 3.6e6)), 1, 56, 0xff808080, false);
    }
}
