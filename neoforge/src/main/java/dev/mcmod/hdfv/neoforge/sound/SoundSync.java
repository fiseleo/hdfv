package dev.mcmod.hdfv.neoforge.sound;

import dev.mcmod.aerocore.api.acoustics.ProcessNoise;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.sound.MachineSound;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * What hdfv's blocks sound like, told to the players near (0.19). A machine, power unit or plant unit reports its
 * {@link MachineSound.Hum} from its server tick ({@link #report}); it is sent to the players tracking its chunk when it
 * starts, stops, changes process or running, or (at most every half second) when its load, speed or power moves
 * enough ({@link MachineSound.Hum#differs}), so a machine working steadily sends nothing. A player who starts tracking
 * the chunk gets what is sounding there. Short sounds ({@link #event}: a door, a weld, a gusher) go once to the same
 * players. Clients keep what they were told ({@link #heard}) and drop it when the block entity is gone.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID)
public final class SoundSync {
    private SoundSync() {}

    /** The fewest ticks between two sends of a block's changing load or speed. */
    static final int MIN_TICKS = 10;

    public record HumPayload(BlockPos pos, MachineSound.Hum hum) implements CustomPacketPayload {
        public static final Type<HumPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "sound_hum"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HumPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos());
            MachineSound.Hum h = p.hum();
            buf.writeByte(h == null ? -2 : h.process() == null ? -1 : h.process().ordinal());
            if (h == null) { return; }
            buf.writeFloat((float) h.powerW());
            buf.writeByte(h.teeth());
            buf.writeFloat((float) h.revPerS());
            buf.writeFloat((float) h.strokeHz());
            buf.writeByte((int) Math.round(h.load() * 250));
            buf.writeBoolean(h.running());
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            int kind = buf.readByte();
            if (kind == -2) { return new HumPayload(pos, null); }
            ProcessNoise.Process[] all = ProcessNoise.Process.values();
            ProcessNoise.Process process = kind >= 0 && kind < all.length ? all[kind] : null;
            double power = buf.readFloat();
            int teeth = buf.readUnsignedByte();
            double rev = buf.readFloat(), stroke = buf.readFloat(), load = buf.readUnsignedByte() / 250.0;
            boolean running = buf.readBoolean();
            if (kind >= all.length) { return new HumPayload(pos, null); }
            return new HumPayload(pos, new MachineSound.Hum(process, power, teeth, rev, stroke, load, running));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EventPayload(Vec3 at, MachineSound.Event event, float argument) implements CustomPacketPayload {
        public static final Type<EventPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "sound_event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EventPayload> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeDouble(p.at().x); buf.writeDouble(p.at().y); buf.writeDouble(p.at().z);
            buf.writeByte(p.event().ordinal());
            buf.writeFloat(p.argument());
        }, buf -> {
            Vec3 at = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int e = buf.readUnsignedByte();
            MachineSound.Event[] all = MachineSound.Event.values();
            float a = buf.readFloat();
            return new EventPayload(at, all[Math.min(e, all.length - 1)], Float.isFinite(a) ? a : 0);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(HumPayload.TYPE, HumPayload.CODEC, (p, context) -> received(p));
        registrar.playToClient(EventPayload.TYPE, EventPayload.CODEC, (p, context) -> received(p));
    }

    // ---- the server

    private static final class Sent { MachineSound.Hum hum; long tick; int count; }
    /** By level, chunk and block: what was last sent. */
    private static final Map<ResourceKey<Level>, Long2ObjectMap<Long2ObjectMap<Sent>>> SENT = new HashMap<>();
    /** How many hums were sent (GameTests). */
    public static volatile int sends;

    /** Server, from a block entity's tick: what it sounds like now (null: silent). */
    public static void report(BlockEntity be, MachineSound.Hum hum) {
        if (!(be.getLevel() instanceof ServerLevel level) || be.isRemoved()) { return; }
        BlockPos pos = be.getBlockPos();
        long chunkKey = ChunkPos.asLong(pos);
        Long2ObjectMap<Long2ObjectMap<Sent>> chunks = SENT.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
        Long2ObjectMap<Sent> chunk = chunks.get(chunkKey);
        Sent s = chunk == null ? null : chunk.get(pos.asLong());
        if (hum == null) {
            if (s == null) { return; }
            chunk.remove(pos.asLong());
            if (chunk.isEmpty()) { chunks.remove(chunkKey); }
            if (s.hum != null) { send(level, pos, null); }
            return;
        }
        if (chunk == null) { chunk = new Long2ObjectOpenHashMap<>(); chunks.put(chunkKey, chunk); }
        if (s == null) { s = new Sent(); chunk.put(pos.asLong(), s); }
        long now = level.getGameTime();
        MachineSound.Hum was = s.hum;
        boolean discrete = was == null || was.process() != hum.process() || was.running() != hum.running() || was.teeth() != hum.teeth();
        if (discrete || hum.differs(was) && now - s.tick >= MIN_TICKS) {
            s.hum = hum;
            s.tick = now;
            s.count++;
            send(level, pos, hum);
        }
    }

    private static void send(ServerLevel level, BlockPos pos, MachineSound.Hum hum) {
        sends++;
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), new HumPayload(pos, hum));
    }

    /** Server: a short sound at a place, for the players tracking it. */
    public static void event(Level level, Vec3 at, MachineSound.Event event, double argument) {
        if (!(level instanceof ServerLevel server)) { return; }
        EventPayload p = new EventPayload(at, event, (float) argument);
        synchronized (TOLD) {
            TOLD.addLast(p);
            while (TOLD.size() > 64) { TOLD.removeFirst(); }
        }
        PacketDistributor.sendToPlayersTrackingChunk(server, new ChunkPos(BlockPos.containing(at)), p);
    }

    /** The server's latest short sounds (GameTests), oldest first. */
    private static final ArrayDeque<EventPayload> TOLD = new ArrayDeque<>();

    /** The short sounds told within a block of a point lately (GameTests). */
    public static java.util.List<EventPayload> told(Vec3 near) {
        synchronized (TOLD) { return TOLD.stream().filter(p -> p.at().distanceTo(near) < 1.5).toList(); }
    }

    /** How many hums were sent for a block (GameTests: a steady machine sends no more). */
    public static int sendsAt(ServerLevel level, BlockPos pos) {
        var chunks = SENT.get(level.dimension());
        var chunk = chunks == null ? null : chunks.get(ChunkPos.asLong(pos));
        Sent s = chunk == null ? null : chunk.get(pos.asLong());
        return s == null ? 0 : s.count;
    }

    /** What the server last sent for a block (GameTests), or null. */
    public static MachineSound.Hum sent(ServerLevel level, BlockPos pos) {
        var chunks = SENT.get(level.dimension());
        var chunk = chunks == null ? null : chunks.get(ChunkPos.asLong(pos));
        Sent s = chunk == null ? null : chunk.get(pos.asLong());
        return s == null ? null : s.hum;
    }

    @SubscribeEvent static void watched(ChunkWatchEvent.Sent event) {
        var chunks = SENT.get(event.getLevel().dimension());
        Long2ObjectMap<Sent> chunk = chunks == null ? null : chunks.get(event.getPos().toLong());
        if (chunk == null) { return; }
        for (var it = chunk.long2ObjectEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            BlockPos pos = BlockPos.of(e.getLongKey());
            if (event.getLevel().getBlockEntity(pos) == null) { it.remove(); continue; }
            if (e.getValue().hum != null) { PacketDistributor.sendToPlayer(event.getPlayer(), new HumPayload(pos, e.getValue().hum)); }
        }
    }

    @SubscribeEvent static void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var chunks = SENT.get(level.dimension());
            if (chunks != null) { chunks.remove(event.getChunk().getPos().toLong()); }
        }
    }

    @SubscribeEvent static void stopped(ServerStoppedEvent event) { SENT.clear(); }

    // ---- the client (plain maps: safe to load on a server)

    private static final Long2ObjectOpenHashMap<MachineSound.Hum> HEARD = new Long2ObjectOpenHashMap<>();
    private static final ArrayDeque<EventPayload> EVENTS = new ArrayDeque<>();

    private static void received(HumPayload p) {
        if (p.hum() == null) { HEARD.remove(p.pos().asLong()); } else { HEARD.put(p.pos().asLong(), p.hum()); }
    }

    private static void received(EventPayload p) {
        if (EVENTS.size() < 64) { EVENTS.add(p); }
    }

    /** Client: the blocks sounding now as the server told, by position (BlockPos.asLong). */
    public static Long2ObjectOpenHashMap<MachineSound.Hum> heard() { return HEARD; }

    /** Client: the next short sound told, or null. */
    public static EventPayload nextEvent() { return EVENTS.poll(); }

    /** Client: forgets everything (a new level). */
    public static void forget() { HEARD.clear(); EVENTS.clear(); }
}
