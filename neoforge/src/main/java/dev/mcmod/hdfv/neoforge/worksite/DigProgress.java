package dev.mcmod.hdfv.neoforge.worksite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.26: how much of each block the machines' tools have dug (0..1 of its cell), per level, saved with it. A tool's cut
 * takes bank cubic metres from a block; the block goes when all of it is taken. The entry remembers which block it was
 * for: a block changed since (mined, replaced) starts whole again.
 */
public final class DigProgress extends SavedData {
    private static final String NAME = "hdfv_dig_progress";
    /** Entries kept at most (oldest dropped first): a level does not grow without end. */
    public static final int MAX = 1 << 16;

    private record Entry(Block block, double dug) {}
    private final Map<Long, Entry> entries = new java.util.LinkedHashMap<>();

    public static DigProgress of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DigProgress::new, DigProgress::load), NAME);
    }

    /** How much of the block at pos (as it stands) is dug, 0..1. */
    public double dug(BlockPos pos, BlockState s) {
        Entry e = entries.get(pos.asLong());
        return e == null || e.block() != s.getBlock() ? 0 : e.dug();
    }

    /** Adds dug share (of the cell) to the block at pos; returns how much is dug now (1 or more: all of it). */
    public double add(BlockPos pos, BlockState s, double share) {
        long key = pos.asLong();
        Entry e = entries.get(key);
        double now = (e == null || e.block() != s.getBlock() ? 0 : e.dug()) + Math.max(share, 0);
        if (now >= 1) { entries.remove(key); }
        else {
            entries.remove(key);
            entries.put(key, new Entry(s.getBlock(), now));
            if (entries.size() > MAX) { entries.remove(entries.keySet().iterator().next()); }
        }
        setDirty();
        return now;
    }

    /** Forgets the block at pos (it went, or was set anew). */
    public void clear(BlockPos pos) { if (entries.remove(pos.asLong()) != null) { setDirty(); } }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> e : entries.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            t.putString("Block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getValue().block()).toString());
            t.putDouble("Dug", e.getValue().dug());
            list.add(t);
        }
        tag.put("Entries", list);
        return tag;
    }

    private static DigProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        DigProgress d = new DigProgress();
        Map<Long, Entry> read = new HashMap<>();
        for (Tag t : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(c.getString("Block")));
            read.put(c.getLong("Pos"), new Entry(b, c.getDouble("Dug")));
        }
        d.entries.putAll(read);
        return d;
    }
}
