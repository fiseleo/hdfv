package dev.mcmod.hdfv.neoforge.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/**
 * A 20 l jerry can (0.8): used on something that holds fluid it fills itself from it, or, sneaking, pours itself in
 * (a tank, a storage tank, from 0.8's P14 a vehicle's tank).
 */
public final class JerryCanItem extends Item {
    public JerryCanItem(Properties p) { super(p); }

    /** A can holding a fluid. */
    public static ItemStack filled(Fluid f, int litres) {
        ItemStack s = new ItemStack(HdfvFluidRegistry.JERRY_CAN.get());
        s.set(HdfvFluidRegistry.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(new FluidStack(f, Math.min(litres, HdfvFluidRegistry.JERRY_CAN_LITRES))));
        return s;
    }

    public static FluidStack content(ItemStack s) {
        SimpleFluidContent c = s.get(HdfvFluidRegistry.FLUID_CONTENT.get());
        return c == null ? FluidStack.EMPTY : c.copy();
    }

    @Override public InteractionResult useOn(UseOnContext c) {
        Level level = c.getLevel();
        BlockPos pos = c.getClickedPos();
        IFluidHandler block = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, c.getClickedFace());
        Player player = c.getPlayer();
        if (block == null || player == null) { return InteractionResult.PASS; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        ItemStack held = c.getItemInHand();
        var result = player.isShiftKeyDown() ? FluidUtil.tryEmptyContainer(held, block, Integer.MAX_VALUE, player, true)
                : FluidUtil.tryFillContainer(held, block, Integer.MAX_VALUE, player, true);
        if (result.isSuccess()) { player.setItemInHand(c.getHand(), result.getResult()); }
        return result.isSuccess() ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override public void appendHoverText(ItemStack s, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FluidStack f = content(s);
        tooltip.add(f.isEmpty() ? Component.translatable("hdfv.fluid.can_empty", HdfvFluidRegistry.JERRY_CAN_LITRES)
                : Component.translatable("hdfv.fluid.can", f.getHoverName(), f.getAmount(), HdfvFluidRegistry.JERRY_CAN_LITRES));
        tooltip.add(Component.translatable("hdfv.fluid.can_use"));
    }
}
