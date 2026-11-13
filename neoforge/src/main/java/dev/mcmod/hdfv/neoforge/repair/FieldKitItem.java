package dev.mcmod.hdfv.neoforge.repair;

import dev.mcmod.hdfv.repair.FieldRepair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A hand kit for repairs on the spot (0.25; FieldRepair.Kit): held on a vehicle for the kit's ticks (FieldKits starts
 * it from the right-click on the vehicle); it wears out after its uses (its durability).
 */
public final class FieldKitItem extends Item {
    public final FieldRepair.Kit kit;

    public FieldKitItem(FieldRepair.Kit kit, Properties p) {
        super(p);
        this.kit = kit;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return kit.ticks; }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BRUSH; }

    /** Server: the player keeps looking at the vehicle within reach, or the use stops; a tool's sound now and then. */
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof Player p)) { return; }
        if (remaining % 5 == 0 && !FieldKits.stillAt(p)) {
            FieldKits.stop(p);
            p.stopUsingItem();
            p.displayClientMessage(Component.translatable("hdfv.repair.field.away"), true);
            return;
        }
        if (remaining % 20 == 10) { level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.4f, 1.2f); }
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player p) { p.displayClientMessage(FieldKits.finish(p, stack, this), true); }
        return stack;
    }

    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide && entity instanceof Player p) { FieldKits.stop(p); }
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("hdfv.repair.kit." + kit.id() + ".tip").withStyle(ChatFormatting.GRAY));
    }
}
