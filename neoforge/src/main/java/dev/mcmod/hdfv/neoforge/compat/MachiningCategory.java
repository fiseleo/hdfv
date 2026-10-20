package dev.mcmod.hdfv.neoforge.compat;

import dev.mcmod.hdfv.manufacture.Consumable;
import dev.mcmod.hdfv.manufacture.Feature;
import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.machining.HdfvMachining;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
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

/**
 * What each machining head does in JEI (0.17): a page a head, its block beside its family's controller, the processes
 * it does and the finest IT grade on each (a process on a feature named as the machine screen names it: "Grinding" on a
 * bore is honing), whether it needs an operator (an NC drive runs it alone) or is numerically controlled, whether it
 * runs on electricity only, and the supplies it uses up.
 */
final class MachiningCategory implements IRecipeCategory<Head> {
    static final RecipeType<Head> TYPE = RecipeType.create(Hdfv.MOD_ID, "machining", Head.class);
    private final IDrawable icon;

    MachiningCategory(IGuiHelper gui) { icon = gui.createDrawableItemStack(new ItemStack(HdfvMachines.MACHINES.get(dev.mcmod.hdfv.manufacture.Station.GRINDER).get())); }

    @Override public RecipeType<Head> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("hdfv.jei.machining"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 160; }
    @Override public int getHeight() { return 96; }

    /** The supplies a head uses (none: nothing wears). */
    static List<Consumable.Supply> supplies(Head h) {
        List<Consumable.Supply> out = new ArrayList<>();
        for (Consumable.Supply s : Consumable.Supply.values()) { if (h.uses != null && s.supplies == h.uses) { out.add(s); } }
        return out;
    }

    @Override public void setRecipe(IRecipeLayoutBuilder b, Head h, IFocusGroup focuses) {
        b.addSlot(RecipeIngredientRole.INPUT, 1, 1).addItemStack(new ItemStack(HdfvMultiblocks.MEMBERS.get(h.kind).get())).setStandardSlotBackground();
        b.addSlot(RecipeIngredientRole.CATALYST, 21, 1).addItemStack(new ItemStack(HdfvMachines.MACHINES.get(h.station).get())).setStandardSlotBackground();
        int i = 0;
        for (Consumable.Supply s : supplies(h)) {
            b.addSlot(RecipeIngredientRole.INPUT, getWidth() - 18 * (i + 1), 1).addItemStack(HdfvMachining.stack(s, 1)).setStandardSlotBackground();
            i++;
        }
    }

    /** A process on a feature, as the machine screen names it. */
    static Component process(dev.mcmod.hdfv.manufacture.Process p, Feature f) {
        String k = "hdfv.process." + p.name().toLowerCase(Locale.ROOT);
        return Component.translatable(f == Feature.NONE ? k : k + "." + f.name().toLowerCase(Locale.ROOT));
    }

    @Override public void draw(Head h, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("block.hdfv." + h.kind), 1, 21, 0xff404040, false);
        int y = 32;
        for (Head.Ability a : h.abilities()) {
            for (Feature f : a.features()) {
                if (y > getHeight() - 20) { break; }
                g.drawString(font, Component.translatable("hdfv.jei.machining.grade", process(a.process(), f), a.grade()), 4, y, 0xff505050, false);
                y += 10;
            }
        }
        Component how = h.station.nc ? Component.translatable("hdfv.jei.machining.nc") : Component.translatable("hdfv.jei.machining.manual");
        if (h.electricOnly) { how = how.copy().append(", ").append(Component.translatable("hdfv.jei.machining.electric")); }
        g.drawString(font, how, 1, getHeight() - 9, 0xff808080, false);
    }
}
