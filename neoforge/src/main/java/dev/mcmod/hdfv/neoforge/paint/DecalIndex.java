package dev.mcmod.hdfv.neoforge.paint;

import dev.mcmod.hdfv.look.Sticker;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server's record of the stickers' pictures (0.13), in the overworld's data: each picture by its SHA-256, its size,
 * when and by whom it was first uploaded, and every player who uploaded it (a player's quota counts the pictures they
 * are among; a picture whose last uploader is purged is deleted). The pictures themselves are files (DecalStore).
 */
public final class DecalIndex extends SavedData {
    public static final String NAME = "hdfv_decals";

    /** A picture: its name, size in pixels and bytes, first upload (epoch ms, player and name), and its uploaders. */
    public record Entry(String sha, int width, int height, int bytes, long time, UUID first, String firstName, Set<UUID> users) {
        public Entry { users = Collections.unmodifiableSet(new LinkedHashSet<>(users)); }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static DecalIndex of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DecalIndex::new, DecalIndex::load, null), NAME);
    }

    public Entry get(String sha) { return entries.get(sha); }

    public int size() { return entries.size(); }

    /** The pictures a player is among the uploaders of (all of them for null), oldest first. */
    public List<Entry> entries(UUID user) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) { if (user == null || e.users().contains(user)) { out.add(e); } }
        return out;
    }

    public int count(UUID user) {
        int n = 0;
        for (Entry e : entries.values()) { if (e.users().contains(user)) { n++; } }
        return n;
    }

    /** Records an upload: a new picture, or one more uploader of a known one. */
    void add(String sha, int width, int height, int bytes, long time, UUID user, String name) {
        Entry e = entries.get(sha);
        if (e == null) {
            entries.put(sha, new Entry(sha, width, height, bytes, time, user, name, Set.of(user)));
        } else if (!e.users().contains(user)) {
            Set<UUID> users = new LinkedHashSet<>(e.users());
            users.add(user);
            entries.put(sha, new Entry(sha, e.width(), e.height(), e.bytes(), e.time(), e.first(), e.firstName(), users));
        }
        setDirty();
    }

    Entry remove(String sha) {
        Entry e = entries.remove(sha);
        if (e != null) { setDirty(); }
        return e;
    }

    /** Takes a player off every picture's uploaders; returns the pictures left with none (to delete). */
    List<String> drop(UUID user) {
        List<String> orphans = new ArrayList<>();
        for (Entry e : List.copyOf(entries.values())) {
            if (!e.users().contains(user)) { continue; }
            Set<UUID> users = new LinkedHashSet<>(e.users());
            users.remove(user);
            if (users.isEmpty()) { entries.remove(e.sha()); orphans.add(e.sha()); }
            else { entries.put(e.sha(), new Entry(e.sha(), e.width(), e.height(), e.bytes(), e.time(), e.first(), e.firstName(), users)); }
            setDirty();
        }
        return orphans;
    }

    /** The pictures whose names start with a prefix (an operator's shortened name). */
    public List<Entry> starting(String prefix) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) { if (e.sha().startsWith(prefix)) { out.add(e); } }
        return out;
    }

    private static DecalIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        DecalIndex index = new DecalIndex();
        for (Tag t : tag.getList("Pictures", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            String sha = c.getString("Sha");
            if (!Sticker.isImage(sha) || !c.hasUUID("First")) { continue; }
            Set<UUID> users = new LinkedHashSet<>();
            for (Tag u : c.getList("Users", Tag.TAG_INT_ARRAY)) { users.add(NbtUtils.loadUUID(u)); }
            if (users.isEmpty()) { users.add(c.getUUID("First")); }
            index.entries.put(sha, new Entry(sha, c.getInt("Width"), c.getInt("Height"), c.getInt("Bytes"), c.getLong("Time"), c.getUUID("First"), c.getString("Name"), users));
        }
        return index;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("Sha", e.sha());
            c.putInt("Width", e.width());
            c.putInt("Height", e.height());
            c.putInt("Bytes", e.bytes());
            c.putLong("Time", e.time());
            c.putUUID("First", e.first());
            c.putString("Name", e.firstName());
            ListTag users = new ListTag();
            for (UUID u : e.users()) { users.add(new IntArrayTag(UUIDUtil.uuidToIntArray(u))); }
            c.put("Users", users);
            list.add(c);
        }
        tag.put("Pictures", list);
        return tag;
    }
}
