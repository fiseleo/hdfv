package dev.mcmod.hdfv.neoforge.assembly;

import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.Room;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * A vehicle blueprint (0.7, the user: "整車配置…可存成整車藍圖"): a whole vehicle's layout, saved from an assembly
 * room's screen and loaded into another. Blank until saved on.
 */
public final class VehicleBlueprintItem extends Item {
    public VehicleBlueprintItem(Properties p) { super(p); }

    static VehicleBlueprint layout(ItemStack s) {
        String json = s.get(HdfvAssembly.VEHICLE_LAYOUT.get());
        if (json == null || json.isEmpty()) { return null; }
        try { return VehicleBlueprint.fromJsonText(json); } catch (RuntimeException e) { return null; }
    }

    @Override public Component getName(ItemStack s) {
        VehicleBlueprint l = layout(s);
        return l == null ? super.getName(s) : Component.translatable("item.hdfv.vehicle_blueprint.named", l.name());
    }

    @Override public void appendHoverText(ItemStack s, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        VehicleBlueprint l = layout(s);
        if (l == null) { tooltip.add(Component.translatable("hdfv.vehicle_blueprint.blank").withStyle(ChatFormatting.GRAY)); return; }
        tooltip.add(Component.translatable("hdfv.vehicle_blueprint.parts", l.parts().size(),
                Component.translatable("hdfv.room." + Room.of(l).name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GRAY));
    }
}
