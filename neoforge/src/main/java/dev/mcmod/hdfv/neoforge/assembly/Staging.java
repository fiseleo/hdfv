package dev.mcmod.hdfv.neoforge.assembly;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.Pace;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.material.Stock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Bringing a vehicle's materials into its assembly room (0.7): the finished parts (by design), the room-built
 * structures' finished workpieces (by design and component) and the stock of their skins and seams (by alloy, kg),
 * found in the room's chests; what is missing, and taking them when nothing is. 0.16: the refit shop's needs too (a
 * refit's new components and stock, its paint and lamps).
 */
public final class Staging {
    private Staging() {}

    /** One kind of item the build needs: what it is called, how many (or kg), which stacks are it, and how much a stack counts. */
    public record Need(Component name, double amount, Predicate<ItemStack> is, java.util.function.ToDoubleFunction<ItemStack> worth, Alloy alloy) {
        /** So many of an item. */
        public static Need items(Component name, int count, net.minecraft.world.item.Item item) { return new Need(name, count, s -> s.is(item), ItemStack::getCount, null); }
    }

    static List<Need> needs(AssemblyPlan p) { return needs(p.parts(), p.pieces(), p.stockKg()); }

    /** The items for finished parts, workpieces and stock (0.16: a refit's, too). */
    public static List<Need> needs(List<AssemblyPlan.Need> parts, List<AssemblyPlan.Piece> pieceList, Map<Alloy, Double> stockKg) {
        List<Need> out = new ArrayList<>();
        for (AssemblyPlan.Need n : parts) {
            String json = Designs.write(n.design());
            out.add(new Need(Component.translatable("item.hdfv.part.named", n.design().name()), n.count(), s -> {
                PartDesign d = Workpieces.partDesign(s);
                return d != null && Designs.write(d).equals(json);
            }, ItemStack::getCount, null));
        }
        Map<String, Integer> pieces = new LinkedHashMap<>();
        Map<String, AssemblyPlan.Piece> named = new LinkedHashMap<>();
        for (AssemblyPlan.Piece piece : pieceList) {
            String key = Workpieces.hash(Designs.write(piece.design())) + "/" + piece.workpiece().key();
            pieces.merge(key, piece.workpiece().count(), Integer::sum);
            named.putIfAbsent(key, piece);
        }
        for (var e : pieces.entrySet()) {
            AssemblyPlan.Piece piece = named.get(e.getKey());
            String hash = e.getKey().substring(0, e.getKey().indexOf('/')), key = piece.workpiece().key();
            out.add(new Need(Component.translatable("item.hdfv.workpiece.named", piece.design().name(),
                    Component.translatable(dev.mcmod.hdfv.manufacture.Workpiece.nameKey(key))), e.getValue(), s -> {
                Workpieces.Data d = Workpieces.data(s);
                return d != null && d.finished() && d.hash().equals(hash) && d.key().equals(key);
            }, ItemStack::getCount, null));
        }
        Map<Alloy, Double> kg = new EnumMap<>(Alloy.class);
        stockKg.forEach((a, v) -> kg.merge(a, v, Double::sum));
        for (var e : kg.entrySet()) {
            Alloy a = e.getKey();
            int bits = Pace.nuggets(e.getValue(), dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot());
            out.add(new Need(Component.translatable("hdfv.staging.kg", Component.translatable("hdfv.alloy." + a.id())), bits * dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() / 10,
                    s -> (s.is(HdfvMaterials.STOCK.get()) || s.is(HdfvMaterials.STOCK_BIT.get())) && Stock.alloy(s) == a, Stock::kg, a));
        }
        return out;
    }

    public static double have(Need n, List<IItemHandler> stores) {
        double sum = 0;
        for (IItemHandler h : stores) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && n.is().test(s)) { sum += n.worth().applyAsDouble(s); }
            }
        }
        return sum;
    }

    /** What the stores lack: a line per need short ("3 × Pistons", "12 kg of steel"). */
    static List<Component> missing(AssemblyPlan p, List<IItemHandler> stores) { return missing(needs(p), stores); }

    /** What the stores lack of a list of needs. */
    public static List<Component> missing(List<Need> needs, List<IItemHandler> stores) {
        List<Component> out = new ArrayList<>();
        for (Need n : needs) {
            double short_ = n.amount() - have(n, stores);
            if (short_ > 1e-6) { out.add(Component.translatable("hdfv.staging.short", fmt(short_), n.name())); }
        }
        return out;
    }

    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v); }

    /** Takes everything a plan needs from the stores (check missing first); change in stock goes back as bits. */
    static void take(AssemblyPlan p, List<IItemHandler> stores) { take(needs(p), stores); }

    /** Takes a list of needs from the stores (check missing first). */
    public static void take(List<Need> needs, List<IItemHandler> stores) {
        for (Need n : needs) {
            if (n.alloy() == null) {
                int left = (int) Math.round(n.amount());
                for (IItemHandler h : stores) {
                    for (int i = 0; i < h.getSlots() && left > 0; i++) {
                        ItemStack s = h.getStackInSlot(i);
                        if (!s.isEmpty() && n.is().test(s)) { left -= h.extractItem(i, left, false).getCount(); }
                    }
                }
                continue;
            }
            // stock: bits first, then whole stock, the change back as bits
            int bits = (int) Math.round(n.amount() / (dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() / 10));
            for (boolean wholes : new boolean[]{false, true}) {
                for (IItemHandler h : stores) {
                    for (int i = 0; i < h.getSlots() && bits > 0; i++) {
                        ItemStack s = h.getStackInSlot(i);
                        if (s.isEmpty() || !n.is().test(s) || s.is(HdfvMaterials.STOCK.get()) != wholes) { continue; }
                        int per = wholes ? 10 : 1, want = (bits + per - 1) / per;
                        int got = h.extractItem(i, want, false).getCount();
                        bits -= got * per;
                    }
                }
            }
            if (bits < 0) { give(Stock.bits(n.alloy(), -bits), stores, null); }
        }
    }

    /** Puts a stack into the stores; what does not fit goes to the overflow (null: lost). */
    public static void give(ItemStack s, List<IItemHandler> stores, java.util.function.Consumer<ItemStack> overflow) {
        ItemStack left = s;
        for (IItemHandler h : stores) {
            if (left.isEmpty()) { return; }
            left = ItemHandlerHelper.insertItemStacked(h, left, false);
        }
        if (!left.isEmpty() && overflow != null) { overflow.accept(left); }
    }

    /** What a dismantled vehicle gives back: its bench-made parts whole, a room-built structure's metal as half its mass in bits. */
    static List<ItemStack> salvage(dev.mcmod.aerocore.api.vehicle.VehicleBlueprint b) {
        List<ItemStack> out = new ArrayList<>();
        for (var e : b.parts()) {
            var plan = dev.mcmod.hdfv.manufacture.Plans.of(e.design());
            if (!plan.roomBuilt()) { out.add(Workpieces.part(e.design())); continue; }
            Map<Alloy, Double> kg = new EnumMap<>(Alloy.class);
            for (var w : plan.structure()) { kg.merge(w.alloy(), 0.5 * w.finishedKg(), Double::sum); }
            for (var w : plan.workpieces()) { kg.merge(w.alloy(), 0.5 * w.finishedKg() * w.count(), Double::sum); }
            kg.forEach((a, v) -> {
                int bits = (int) Math.floor(v / (dev.mcmod.hdfv.neoforge.HdfvConfig.kgPerIngot() / 10));
                while (bits > 0) {
                    int n = Math.min(bits, 64);
                    out.add(Stock.bits(a, n));
                    bits -= n;
                }
            });
        }
        return out;
    }

    public static ItemStack workpieceOf(PartDesign d, dev.mcmod.hdfv.manufacture.Workpiece w) {
        ItemStack s = Workpieces.start(d, w, w.route().size());
        s.setCount(Math.min(w.count(), s.getMaxStackSize()));
        return s;
    }
}
