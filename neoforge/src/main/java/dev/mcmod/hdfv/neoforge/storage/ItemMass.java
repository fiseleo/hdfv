package dev.mcmod.hdfv.neoforge.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Consumable;
import dev.mcmod.hdfv.manufacture.GasSupply;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.fuel.HdfvFuel;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.neoforge.machining.HdfvMachining;
import dev.mcmod.hdfv.neoforge.material.HdfvComponents;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.part.engine.SolidRocketDesign;
import dev.mcmod.hdfv.petroleum.Refining;
import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.storage.CargoMass;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * The mass of items (0.25): what a store's screen shows and what cargo weighs. One scale, the workshop's (an ingot or
 * a stock is 10 kg), from three places in turn:
 * <ol>
 * <li>hdfv's own rules, for the items that have one: stock and stock bits, ingots and raw ores (the server's
 *     kgPerIngot); finished parts (the design's compiled mass); workpieces (what they were stamped with when made, by
 *     their step: CargoMass.workpieceKg); standard components and intermediates (Component.kg, Intermediate.kg); coke
 *     (PowerPlant.COKE); the refinery's solids (Refining.ITEM_KG); propellant grains (SolidRocketDesign.GRAIN_ITEM_KG);
 *     gas cylinders (the empty cylinder and what is left of GasSupply's fill).</li>
 * <li>The data map hdfv:item_mass ({"kg": n}; data/hdfv/data_maps/item/item_mass.json, written by tools/item_mass.py):
 *     vanilla's and other mods' items by tag or id, and hdfv's blocks and remaining items from their recipes. Data
 *     packs change or add to it.</li>
 * <li>Else 10 kg for a block's item and 1 kg for any other (CargoMass).</li>
 * </ol>
 * On top of that an item weighs what it holds: the fluid in it (litres times the fluid's density: in hdfv's jerry can
 * 1 mB a litre as hdfv's tanks count, in vanilla's and other mods' containers a bucket is storage.bucketLitres, 10 L by
 * default) and the items in it (a shulker box, a bundle; CargoMass.MAX_DEPTH deep).
 * The server's storage.itemMassScale multiplies everything (0: weightless).
 *
 * <p>The same on the server and (the data map and the config are synced) on the client; no world is needed. Cheap: a
 * map lookup an item, but for parts (compiled once a design, the last PARTS designs kept).
 */
public final class ItemMass {
    private ItemMass() {}

    /** A data map entry: what one of the item weighs, kg. */
    public record Kg(double kg) {
        public static final Codec<Kg> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.doubleRange(0, 1e6).fieldOf("kg").forGetter(Kg::kg)).apply(i, Kg::new));
    }

    /** hdfv:item_mass: an item's mass, kg each (tags allowed; synced to clients for the stores' screens). */
    public static final DataMapType<Item, Kg> DATA_MAP = DataMapType.builder(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "item_mass"), Registries.ITEM, Kg.CODEC)
            .synced(Kg.CODEC, true).build();

    /** Designs whose compiled mass is kept. */
    static final int PARTS = 256;
    private static final Map<String, Double> PART_KG = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Double> e) { return size() > PARTS; }
    };

    /** A whole stack's mass, kg (its count times kgEach). */
    public static double kg(ItemStack s) { return s.isEmpty() ? 0 : kgEach(s) * s.getCount(); }

    /** The mass of one of a stack's items with what it holds, kg, on the server's scale. */
    public static double kgEach(ItemStack s) {
        if (s.isEmpty()) { return 0; }
        double scale = HdfvConfig.itemMassScale();
        return scale <= 0 ? 0 : scale * each(s, 0);
    }

    /** The mass of the stacks of a store (a container's slots), kg. */
    public static double kg(Iterable<ItemStack> stacks) {
        double sum = 0;
        for (ItemStack s : stacks) { sum += kg(s); }
        return sum;
    }

    /** One of a stack's items with its contents, unscaled. */
    private static double each(ItemStack s, int depth) {
        double kg = own(s);
        // the fluid in it: in hdfv's own containers a millibucket is a litre (as its tanks count); in vanilla's and other
        // mods' a bucket is storage.bucketLitres (a real bucket's 10 L, not the cubic metre hdfv's count would make it)
        IFluidHandlerItem fluids = s.getCapability(Capabilities.FluidHandler.ITEM);
        if (fluids != null) {
            double litresPerMb = Hdfv.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace()) ? 1 : HdfvConfig.bucketLitres() / 1000.0;
            for (int i = 0; i < fluids.getTanks(); i++) {
                FluidStack f = fluids.getFluidInTank(i);
                if (!f.isEmpty()) { kg += f.getAmount() * litresPerMb / 1000.0 * HdfvFluidRegistry.spec(f.getFluid()).densityKgPerM3(); }
            }
        }
        // the items in it
        if (depth < CargoMass.MAX_DEPTH) {
            ItemContainerContents box = s.get(DataComponents.CONTAINER);
            if (box != null) { for (ItemStack in : box.nonEmptyItems()) { kg += each(in, depth + 1) * in.getCount(); } }
            BundleContents bundle = s.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) { for (ItemStack in : bundle.items()) { kg += each(in, depth + 1) * in.getCount(); } }
        }
        return kg;
    }

    /** One of a stack's items, empty: hdfv's rule, the data map, the fallback. */
    private static double own(ItemStack s) {
        ToDoubleFunction<ItemStack> rule = Rules.MAP.get(s.getItem());
        if (rule != null) {
            double kg = rule.applyAsDouble(s);
            if (kg >= 0) { return kg; }
        }
        return listed(s.getItem());
    }

    /** What the data map says of an item, else the fallback. */
    private static double listed(Item item) {
        Kg kg = item.builtInRegistryHolder().getData(DATA_MAP);
        if (kg != null) { return kg.kg(); }
        return item instanceof BlockItem ? CargoMass.FALLBACK_BLOCK_KG : CargoMass.FALLBACK_ITEM_KG;
    }

    /** A finished part's mass: its design compiled (negative: no design, or it does not compile). */
    private static double partKg(ItemStack s) {
        String json = s.get(HdfvRegistry.DESIGN.get());
        if (json == null) { return -1; }
        synchronized (PART_KG) {
            Double known = PART_KG.get(json);
            if (known != null) { return known; }
        }
        double kg;
        try {
            PartDesign d = Designs.read(json);
            kg = d.compile().mass().massKg();
            if (!(kg >= 0) || Double.isInfinite(kg)) { kg = -1; }
        } catch (RuntimeException e) {
            kg = -1;
        }
        synchronized (PART_KG) { PART_KG.put(json, kg); }
        return kg;
    }

    /** A gas cylinder: the empty cylinder and the gas left in it (its damage is what was used). */
    private static double cylinderKg(ItemStack s, GasSupply.Product gas) {
        double left = s.getMaxDamage() > 0 ? 1 - (double) s.getDamageValue() / s.getMaxDamage() : 1;
        return listed(HdfvMachining.EMPTY_CYLINDER.get()) + gas.cylinderKg() * Math.clamp(left, 0, 1);
    }

    /** hdfv's own rules by item (a rule answering a negative number leaves the item to the data map). Built on first use: the registries are full by then. */
    private static final class Rules {
        static final Map<Item, ToDoubleFunction<ItemStack>> MAP = build();

        private static Map<Item, ToDoubleFunction<ItemStack>> build() {
            Map<Item, ToDoubleFunction<ItemStack>> m = new IdentityHashMap<>();
            m.put(HdfvMaterials.STOCK.get(), s -> HdfvConfig.kgPerIngot());
            m.put(HdfvMaterials.STOCK_BIT.get(), s -> HdfvConfig.kgPerIngot() / 10);
            for (String id : HdfvMaterials.INGOTS) { m.put(HdfvMaterials.MATERIAL_ITEMS.get(id).get(), s -> HdfvConfig.kgPerIngot()); }
            for (HdfvMaterials.Ore o : HdfvMaterials.ORES) { m.put(HdfvMaterials.MATERIAL_ITEMS.get(o.raw()).get(), s -> HdfvConfig.kgPerIngot()); }
            m.put(HdfvMaterials.MATERIAL_ITEMS.get("coke").get(), s -> PowerPlant.COKE.kg());
            Refining.ITEM_KG.forEach((id, kg) -> {
                Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id)).orElse(null);
                if (item != null) { m.put(item, s -> kg); }
            });
            HdfvComponents.COMPONENTS.forEach((c, item) -> m.put(item.get(), s -> c.kg));
            HdfvComponents.INTERMEDIATES.forEach((i, item) -> m.put(item.get(), s -> i.kg));
            m.put(HdfvMachines.PART.get(), ItemMass::partKg);
            m.put(HdfvMachines.WORKPIECE.get(), s -> { double kg = Workpieces.kg(s); return kg >= 0 ? kg : HdfvConfig.kgPerIngot(); });
            m.put(HdfvFuel.KNSB_GRAIN.get(), s -> SolidRocketDesign.GRAIN_ITEM_KG);
            m.put(HdfvFuel.APCP_GRAIN.get(), s -> SolidRocketDesign.GRAIN_ITEM_KG);
            m.put(HdfvMachining.SUPPLIES.get(Consumable.Supply.OXYGEN).get(), s -> cylinderKg(s, GasSupply.Product.OXYGEN));
            m.put(HdfvMachining.SUPPLIES.get(Consumable.Supply.NITROGEN).get(), s -> cylinderKg(s, GasSupply.Product.NITROGEN));
            m.put(HdfvMachining.SUPPLIES.get(Consumable.Supply.ARGON).get(), s -> cylinderKg(s, GasSupply.Product.ARGON));
            return m;
        }
    }

    /** Whether an item has a rule of hdfv's own (the data map does not list those). */
    public static boolean ruled(Item item) { return Rules.MAP.containsKey(item); }
}
