package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.petroleum.OilFields;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import java.util.Locale;

/**
 * A geophone survey kit (0.8): used on the ground it tells what a seismic shot would: whether there is a field under
 * it, how deep (to 50 m), which crude, roughly how much it holds (two figures), whether it would flow by itself, and
 * which way and how far its middle is.
 */
public final class GeophoneItem extends Item {
    public GeophoneItem(Properties p) { super(p); }

    @Override public InteractionResult useOn(UseOnContext c) {
        if (!(c.getLevel() instanceof ServerLevel level) || c.getPlayer() == null) { return InteractionResult.sidedSuccess(c.getLevel().isClientSide); }
        var pos = c.getClickedPos();
        OilFields.Field f = OilFieldData.of(level).field(level, pos);
        if (f == null) {
            c.getPlayer().displayClientMessage(Component.translatable("hdfv.geophone.none"), false);
        } else {
            double produced = OilFieldData.of(level).produced(f.id());
            double left = Math.max(0, f.reservoir().recoverableM3() - produced);
            String rounded = String.format(Locale.ROOT, "%.2g", left);
            double dx = f.centreX() - (pos.getX() + 0.5), dz = f.centreZ() - (pos.getZ() + 0.5);
            String way = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
            c.getPlayer().displayClientMessage(Component.translatable("hdfv.geophone.field", Math.round(f.depthM() / 50) * 50,
                    Component.translatable("fluid_type.hdfv." + f.fluid()), rounded,
                    Component.translatable(f.naturalRate(produced) > 0 ? "hdfv.geophone.flows" : "hdfv.geophone.pump"),
                    Math.round(Math.hypot(dx, dz)), Component.translatable("hdfv.geophone." + way)), false);
        }
        return InteractionResult.CONSUME;
    }

    @Override public void appendHoverText(ItemStack s, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("hdfv.geophone.use"));
    }
}
