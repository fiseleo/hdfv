package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.material.TechStages;
import dev.mcmod.hdfv.recipe.TechTree;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** The tech stage on hdfv's items' tooltips (0.23): "科技階段 Tn：在…做", from the recipes this game has (TechStages). */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class TechTooltips {
    private TechTooltips() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        if (s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals(Hdfv.MOD_ID)) { return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { return; }
        TechTree.Stage st = TechStages.of(mc.level.getRecipeManager(), mc.level.registryAccess()).get(TechStages.id(s));
        if (st == null) { return; }   // blueprints, parts and workpieces are not the tree's
        e.getToolTip().add(Component.translatable("hdfv.tier", st.tier(), Component.translatable("hdfv.tier.where." + st.where()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
