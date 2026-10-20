package dev.mcmod.hdfv.neoforge.design;

import dev.mcmod.hdfv.neoforge.client.ClientAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The design tablet: the design screen anywhere, with a blueprint from the other hand if there is one. */
public final class DesignTabletItem extends Item {
    public DesignTabletItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) { ClientAccess.openDesignScreen(player); }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
