package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.earthmoving.Material;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0.26: the loose material a machine carries in bulk (a vehicle's attachment): in each bulk hold (an AeroCore hold whose
 * name starts with "bulk": a bucket's, a dump body's, a hopper's, a mixer's) and heaped ahead of each blade (by part
 * id and tool). It is kept as bank cubic metres of each block form it came from (EarthMaterials.dugForm), so it weighs
 * what it weighed in the ground (the material's bank density) and fills its hold by its loose volume (swell); hot mix
 * keeps its temperature, concrete its maturity. The holds' masses go to AeroCore (PartVehicle.setHold) whenever they
 * change.
 */
public final class BulkCargo {
    /** A heap of loose material: bank m^3 by block id, and for mixes their temperature (K) or maturity (K s). */
    public static final class Load {
        final Map<String, Double> m3 = new LinkedHashMap<>();
        double temperatureK = 288.15, maturityKs = 0;

        public double bankM3() { double v = 0; for (double x : m3.values()) { v += x; } return v; }
        public boolean isEmpty() { return bankM3() < 1e-9; }
        public double massKg() { double kg = 0; for (Map.Entry<String, Double> e : m3.entrySet()) { kg += e.getValue() * material(e.getKey()).bankKgM3(); } return kg; }
        public double looseM3() { double v = 0; for (Map.Entry<String, Double> e : m3.entrySet()) { v += material(e.getKey()).looseM3(e.getValue()); } return v; }
        /** The block form most of it is (what it is poured back as), or null when empty. */
        public String main() {
            String best = null;
            double most = 0;
            for (Map.Entry<String, Double> e : m3.entrySet()) { if (e.getValue() > most) { most = e.getValue(); best = e.getKey(); } }
            return best;
        }
        public void add(String block, double bankM3) { if (bankM3 > 0) { m3.merge(block, bankM3, Double::sum); } }
        /** Takes up to bankM3 (all the forms in proportion); returns what was taken as its own load. */
        public Load take(double bankM3) {
            Load out = new Load();
            double have = bankM3();
            if (!(have > 0) || !(bankM3 > 0)) { return out; }
            double share = Math.min(bankM3 / have, 1);
            for (Map.Entry<String, Double> e : m3.entrySet()) { out.m3.put(e.getKey(), e.getValue() * share); e.setValue(e.getValue() * (1 - share)); }
            m3.values().removeIf(v -> v < 1e-9);
            out.temperatureK = temperatureK; out.maturityKs = maturityKs;
            return out;
        }
        /** Pours another load in (temperature and maturity mixed by mass). */
        public void add(Load other) {
            double a = massKg(), b = other.massKg();
            if (a + b > 0) {
                temperatureK = (temperatureK * a + other.temperatureK * b) / (a + b);
                maturityKs = (maturityKs * a + other.maturityKs * b) / (a + b);
            }
            other.m3.forEach(this::add);
        }
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            CompoundTag m = new CompoundTag();
            m3.forEach(m::putDouble);
            t.put("M3", m);
            t.putDouble("T", temperatureK);
            t.putDouble("Maturity", maturityKs);
            return t;
        }
        static Load load(CompoundTag t) {
            Load l = new Load();
            CompoundTag m = t.getCompound("M3");
            for (String k : m.getAllKeys()) { l.m3.put(k, m.getDouble(k)); }
            l.temperatureK = t.contains("T") ? t.getDouble("T") : 288.15;
            l.maturityKs = t.getDouble("Maturity");
            return l;
        }
    }

    /** By hold id ("part.bulk"). */
    final Map<String, Load> holds = new LinkedHashMap<>();
    /** Heaped ahead of a blade, by "part.tool". */
    final Map<String, Load> heaps = new LinkedHashMap<>();

    public BulkCargo() {}
    public BulkCargo(IAttachmentHolder holder) {}

    public static BulkCargo of(PartVehicle v) { return v.getData(HdfvWorksite.BULK.get()); }

    /** Whether an AeroCore hold carries bulk (its name starts with "bulk"). */
    public static boolean bulk(Vehicle.Hold h) { return h.hold().name().startsWith("bulk"); }

    public Load hold(String id) { return holds.computeIfAbsent(id, k -> new Load()); }
    public Load heap(String key) { return heaps.computeIfAbsent(key, k -> new Load()); }
    public Map<String, Load> holds() { return holds; }
    public Map<String, Load> heaps() { return heaps; }

    /** Sets every bulk hold's mass on the vehicle (AeroCore sees it next step). */
    public void weigh(PartVehicle v) {
        Vehicle vehicle = v.vehicle();
        if (vehicle == null || v.vehicleState() == null) { return; }
        for (int k = 0; k < vehicle.holds().size(); k++) {
            Vehicle.Hold h = vehicle.holds().get(k);
            if (!bulk(h)) { continue; }
            Load l = holds.get(h.id());
            double kg = l == null ? 0 : l.massKg();
            if (Math.abs(v.holdKg(k) - kg) > 1e-6) { v.setHold(k, kg); }
        }
    }

    /** The material of a block form (a carried block id): its earth material, loam for what is not ground. */
    public static Material material(String block) {
        Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(block));


        Material m = EarthMaterials.material(b.defaultBlockState());
        return m == null ? Material.LOAM : m;
    }

    static final class Serializer implements IAttachmentSerializer<CompoundTag, BulkCargo> {
        @Override public BulkCargo read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            BulkCargo c = new BulkCargo();
            CompoundTag h = tag.getCompound("Holds"), p = tag.getCompound("Heaps");
            for (String k : h.getAllKeys()) { c.holds.put(k, Load.load(h.getCompound(k))); }
            for (String k : p.getAllKeys()) { c.heaps.put(k, Load.load(p.getCompound(k))); }
            return c;
        }
        @Override public CompoundTag write(BulkCargo c, HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag(), h = new CompoundTag(), p = new CompoundTag();
            c.holds.forEach((k, l) -> { if (!l.isEmpty()) { h.put(k, l.save()); } });
            c.heaps.forEach((k, l) -> { if (!l.isEmpty()) { p.put(k, l.save()); } });
            tag.put("Holds", h);
            tag.put("Heaps", p);
            return tag;
        }
    }
}
