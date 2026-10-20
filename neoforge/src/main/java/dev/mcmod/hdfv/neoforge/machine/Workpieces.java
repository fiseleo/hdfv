package dev.mcmod.hdfv.neoforge.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Operation;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Routes;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Workpieces and parts as items (0.7). A workpiece carries which design it is for (a hash of the drawing's JSON and
 * the design's name, for its tooltip), which component of it (the plan's key), and how many of its route's steps are
 * done; its route comes from the drawing in the machine (Plans.of), so the item stays small. A finished part carries
 * the whole design (hdfv:design), as a blueprint does.
 */
public final class Workpieces {
    private Workpieces() {}

    /**
     * A workpiece item's data. 0.17: the route version it was made on (Routes.VERSION; absent in older items: 0.16's
     * routes); a machine moves an older piece onto the current route at the matching step (current) when it takes it.
     */
    public record Data(String hash, String design, String type, String key, int step, int steps, int route) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("hash").forGetter(Data::hash), Codec.STRING.fieldOf("design").forGetter(Data::design),
                Codec.STRING.fieldOf("type").forGetter(Data::type), Codec.STRING.fieldOf("key").forGetter(Data::key),
                Codec.INT.fieldOf("step").forGetter(Data::step), Codec.INT.fieldOf("steps").forGetter(Data::steps),
                Codec.INT.optionalFieldOf("route", 0).forGetter(Data::route)).apply(i, Data::new));
        public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.of((buf, d) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, d.hash()); ByteBufCodecs.STRING_UTF8.encode(buf, d.design()); ByteBufCodecs.STRING_UTF8.encode(buf, d.type());
            ByteBufCodecs.STRING_UTF8.encode(buf, d.key()); ByteBufCodecs.VAR_INT.encode(buf, d.step()); ByteBufCodecs.VAR_INT.encode(buf, d.steps());
            ByteBufCodecs.VAR_INT.encode(buf, d.route());
        }, buf -> new Data(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

        /** A piece on the current routes. */
        public Data(String hash, String design, String type, String key, int step, int steps) { this(hash, design, type, key, step, steps, Routes.VERSION); }

        public boolean finished() { return step >= steps; }
        public Data next() { return new Data(hash, design, type, key, step + 1, steps, route); }

        /** Whether it was made on an older route (it moves onto the current one in a machine). */
        public boolean older() { return route < Routes.VERSION; }

        /** The same piece on the current route of its workpiece (unchanged when it is already on it, or there is none). */
        public Data current(Workpiece w) {
            if (!older() || w == null) { return this; }
            return new Data(hash, design, type, key, Routes.migrate(w, step, steps), w.route().size(), Routes.VERSION);
        }
    }

    /** A short hash of a design's JSON (what workpieces and drawings are matched by). */
    public static String hash(String json) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A blueprint read for the workshop: its design, the hash its workpieces carry and its plan. */
    public record Drawing(PartDesign design, String hash, PartPlan plan) {}

    /** Drawings by their JSON: machines look every tick and screens every frame, so the last few are kept read. */
    private static final java.util.Map<String, Drawing> DRAWINGS = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Drawing> e) { return size() > 32; }
    };

    /** The drawing a stack is (a valid blueprint), or null. */
    public static synchronized Drawing drawingOf(ItemStack s) {
        if (!s.is(HdfvRegistry.BLUEPRINT.get())) { return null; }
        String json = Blueprints.json(s);
        if (json == null) { return null; }
        if (DRAWINGS.containsKey(json)) { return DRAWINGS.get(json); }
        var info = Blueprints.info(json);
        Drawing d = info.design() == null || info.invalid() > 0 ? null
                : new Drawing(info.design(), hash(Designs.write(info.design())), Plans.of(info.design()));
        DRAWINGS.put(json, d);
        return d;
    }

    /** The design a stack is (a valid blueprint's), or null. */
    public static PartDesign drawing(ItemStack s) {
        Drawing d = drawingOf(s);
        return d == null ? null : d.design();
    }

    public static Data data(ItemStack s) { return s.is(HdfvMachines.WORKPIECE.get()) ? s.get(HdfvMachines.WORKPIECE_DATA.get()) : null; }

    /** The plan's workpiece of a key. */
    public static Workpiece piece(PartPlan plan, String key) {
        for (Workpiece w : plan.workpieces()) { if (w.key().equals(key)) { return w; } }
        return null;
    }

    /** A new workpiece of a design's component, its first step done (its primary forming). */
    public static ItemStack start(PartDesign d, Workpiece w, int done) {
        ItemStack s = new ItemStack(HdfvMachines.WORKPIECE.get());
        s.set(HdfvMachines.WORKPIECE_DATA.get(), new Data(hash(Designs.write(d)), d.name(), d.type(), w.key(), done, w.route().size()));
        return s;
    }

    /** The step a workpiece is at (null when finished). */
    public static Operation next(PartPlan plan, Data data) {
        Workpiece w = piece(plan, data.key());
        return w == null || data.step() >= w.route().size() ? null : w.route().get(data.step());
    }

    /** A finished part of a design. */
    public static ItemStack part(PartDesign d) {
        ItemStack s = new ItemStack(HdfvMachines.PART.get());
        s.set(HdfvRegistry.DESIGN.get(), Designs.write(d));
        return s;
    }

    /** The design a finished part is. */
    public static PartDesign partDesign(ItemStack s) {
        if (!s.is(HdfvMachines.PART.get())) { return null; }
        String json = s.get(HdfvRegistry.DESIGN.get());
        if (json == null) { return null; }
        try { return Designs.read(json); } catch (RuntimeException e) { return null; }
    }

    /** The plan of a drawing (cached). */
    public static PartPlan plan(PartDesign d) { return Plans.of(d); }

    /** Every finished workpiece a plan needs, as (key, count). */
    public static List<Workpiece> needs(PartPlan p) { return p.workpieces(); }
}
