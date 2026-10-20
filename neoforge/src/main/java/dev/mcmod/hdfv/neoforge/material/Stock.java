package dev.mcmod.hdfv.neoforge.material;

import dev.mcmod.hdfv.manufacture.Alloy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Material stock (0.7): one item of 10 kg (a stock: an ingot, a billet, a roll of prepreg, a log's timber, a block of
 * rubber) and one of 1 kg (a bit), each carrying the alloy it is (hdfv:alloy). What machines draw their material from.
 */
public final class Stock {
    private Stock() {}

    /** Kilograms an item of stock and of a bit stand for. */
    public static double kg(ItemStack s) {
        if (s.is(HdfvMaterials.STOCK.get())) { return dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() * s.getCount(); }
        if (s.is(HdfvMaterials.STOCK_BIT.get())) { return dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() / 10 * s.getCount(); }
        return 0;
    }

    /** The alloy a stack of stock is, or null. */
    public static Alloy alloy(ItemStack s) {
        String id = s.get(HdfvMaterials.ALLOY.get());
        if (id == null) { return null; }
        try { return Alloy.byId(id); } catch (IllegalArgumentException e) { return null; }
    }

    public static ItemStack of(Alloy a, int count) {
        ItemStack s = new ItemStack(HdfvMaterials.STOCK.get(), count);
        s.set(HdfvMaterials.ALLOY.get(), a.id());
        return s;
    }

    public static ItemStack bits(Alloy a, int count) {
        ItemStack s = new ItemStack(HdfvMaterials.STOCK_BIT.get(), count);
        s.set(HdfvMaterials.ALLOY.get(), a.id());
        return s;
    }

    /** The colour stock of an alloy is drawn in: its family's, lighter or darker by alloy. */
    public static int color(Alloy a) {
        return switch (a) {
            case GREY_IRON -> 0x5f6266; case DUCTILE_IRON -> 0x6d7176; case STEEL -> 0x9aa2ab; case ALLOY_STEEL -> 0x7f8a96; case VALVE_STEEL -> 0x8c8578;
            case STAINLESS_STEEL -> 0xc9d0d6; case ALUMINIUM_CAST -> 0xc2c7cc; case ALUMINIUM_PISTON -> 0xb8bfc6; case ALUMINIUM_2618 -> 0xb0b8c0;
            case ALUMINIUM_7075 -> 0xd6dbe0; case ALUMINIUM_2024 -> 0xe1e5e8; case MAGNESIUM -> 0xa9b3a4; case TITANIUM -> 0x8f8a99;
            case INCONEL_718 -> 0x857b6c; case INCONEL_713C -> 0x7a705f; case CMSX_4 -> 0x6f6676; case BRONZE -> 0xb07a3c; case COPPER -> 0xc96f43;
            case CFRP -> 0x2b2d31; case GFRP -> 0xd8d4c4; case SPRUCE -> 0xc29a62; case OAK -> 0xa57d47; case PLYWOOD -> 0xc9a873; case RUBBER -> 0x232425;
        };
    }

    /** A stock item: named by its alloy. */
    public static final class StockItem extends Item {
        private final boolean bit;

        public StockItem(Properties p, boolean bit) { super(p); this.bit = bit; }

        @Override public Component getName(ItemStack s) {
            Alloy a = alloy(s);
            Component name = Component.translatable(a == null ? "hdfv.alloy.unknown" : "hdfv.alloy." + a.id());
            double kg = dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() / (bit ? 10 : 1);
            return Component.translatable(bit ? "item.hdfv.stock_bit.named" : "item.hdfv.stock.named", name,
                    kg == Math.rint(kg) ? String.valueOf((long) kg) : String.valueOf(kg));
        }

        @Override public void appendHoverText(ItemStack s, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            Alloy a = alloy(s);
            if (a != null) { tooltip.add(Component.translatable("hdfv.stock.family." + a.family.name().toLowerCase(java.util.Locale.ROOT)).withStyle(net.minecraft.ChatFormatting.GRAY)); }
        }
    }
}
