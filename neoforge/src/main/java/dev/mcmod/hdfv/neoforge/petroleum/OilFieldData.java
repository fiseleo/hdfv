package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.petroleum.OilFields;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * A level's oil fields' records (0.8): how much each field has given (m^3; every well into it shares its pressure), and
 * fields set by hand (an operator's /hdfv dev oilfield, the tests) that stand in for the seed's in their region.
 */
public final class OilFieldData extends SavedData {
    public static final String NAME = "hdfv_oil_fields";

    /** A field set by hand: its parameters, as OilFields.of takes them. */
    public record Forced(int cx, int cz, double radiusM, double depthM, double payM, double porosity, double saturation, double permeabilityMd,
                         boolean light, double pressurePa, double rockStrengthPa) {
        OilFields.Field field() {
            return OilFields.of(OilFields.regionKey(cx, cz), cx, cz, radiusM, depthM, payM, porosity, saturation, permeabilityMd, light, pressurePa, rockStrengthPa);
        }
    }

    private final Map<Long, Double> produced = new HashMap<>();
    private final Map<Long, Forced> forced = new HashMap<>();

    public static OilFieldData of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(OilFieldData::new, OilFieldData::load, null), NAME);
    }

    /** The field under a block (null: none): one set by hand in its region, else the seed's. */
    public OilFields.Field field(ServerLevel level, BlockPos pos) {
        Forced f = forced.get(OilFields.regionKey(pos.getX(), pos.getZ()));
        if (f != null) {
            OilFields.Field field = f.field();
            return field.contains(pos.getX(), pos.getZ()) ? field : null;
        }
        return OilFields.at(level.getSeed(), pos.getX(), pos.getZ());
    }

    public double produced(long field) { return produced.getOrDefault(field, 0.0); }

    public void add(long field, double m3) { if (m3 > 0) { produced.merge(field, m3, Double::sum); setDirty(); } }

    public void setProduced(long field, double m3) { produced.put(field, Math.max(m3, 0)); setDirty(); }

    /** Sets a field by hand in its region (replacing the seed's there); forgets what its region gave. */
    public void force(Forced f) {
        long id = OilFields.regionKey(f.cx(), f.cz());
        forced.put(id, f);
        produced.remove(id);
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        produced.forEach((id, m3) -> { CompoundTag t = new CompoundTag(); t.putLong("Id", id); t.putDouble("M3", m3); list.add(t); });
        tag.put("Produced", list);
        ListTag fs = new ListTag();
        for (Forced f : forced.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("X", f.cx()); t.putInt("Z", f.cz());
            t.putDouble("Radius", f.radiusM()); t.putDouble("Depth", f.depthM()); t.putDouble("Pay", f.payM()); t.putDouble("Porosity", f.porosity());
            t.putDouble("Saturation", f.saturation()); t.putDouble("Permeability", f.permeabilityMd()); t.putBoolean("Light", f.light());
            t.putDouble("Pressure", f.pressurePa()); t.putDouble("Rock", f.rockStrengthPa());
            fs.add(t);
        }
        tag.put("Forced", fs);
        return tag;
    }

    static OilFieldData load(CompoundTag tag, HolderLookup.Provider registries) {
        OilFieldData d = new OilFieldData();
        for (Tag t : tag.getList("Produced", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) t; d.produced.put(c.getLong("Id"), c.getDouble("M3")); }
        for (Tag t : tag.getList("Forced", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Forced f = new Forced(c.getInt("X"), c.getInt("Z"), c.getDouble("Radius"), c.getDouble("Depth"), c.getDouble("Pay"), c.getDouble("Porosity"),
                    c.getDouble("Saturation"), c.getDouble("Permeability"), c.getBoolean("Light"), c.getDouble("Pressure"), c.getDouble("Rock"));
            d.forced.put(OilFields.regionKey(f.cx(), f.cz()), f);
        }
        return d;
    }
}
