package dev.mcmod.hdfv.neoforge.design;

import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.neoforge.client.ClientAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A written blueprint: one part design (hdfv:design). Using it opens the design screen on it. */
public final class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) { super(properties); }

    @Override public Component getName(ItemStack stack) {
        String json = stack.get(HdfvRegistry.DESIGN.get());
        if (json == null) { return super.getName(stack); }
        Blueprints.Info info = Blueprints.info(json);
        return info.design() == null ? super.getName(stack) : Component.translatable("item.hdfv.blueprint.named", info.design().name());
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String json = stack.get(HdfvRegistry.DESIGN.get());
        if (json == null) { tooltip.add(Component.translatable("hdfv.blueprint.empty").withStyle(ChatFormatting.GRAY)); return; }
        Blueprints.Info info = Blueprints.info(json);
        if (info.design() == null) { tooltip.add(Component.translatable("hdfv.blueprint.unreadable").withStyle(ChatFormatting.RED)); return; }
        tooltip.add(typeName(info.design().type()).withStyle(ChatFormatting.GRAY));
        if (info.invalid() > 0) { tooltip.add(Component.translatable("hdfv.blueprint.invalid", info.invalid()).withStyle(ChatFormatting.RED)); }
        if (info.extrapolation() > 0) { tooltip.add(Component.translatable("hdfv.blueprint.extrapolation", info.extrapolation()).withStyle(ChatFormatting.YELLOW)); }
        if (info.invalid() == 0 && info.extrapolation() == 0) { tooltip.add(Component.translatable("hdfv.blueprint.ok").withStyle(ChatFormatting.GREEN)); }
    }

    /** "hdfv:piston_engine" as "Piston engine". */
    public static net.minecraft.network.chat.MutableComponent typeName(String type) {
        return Component.translatableWithFallback("hdfv.type." + type.replace(':', '.'), type);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) { ClientAccess.openDesignScreen(player); }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
