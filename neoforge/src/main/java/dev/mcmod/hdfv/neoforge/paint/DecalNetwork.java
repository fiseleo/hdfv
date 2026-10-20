package dev.mcmod.hdfv.neoforge.paint;

import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The stickers' pictures over the network (0.13). A client uploads a picture in chunks of at most UP_CHUNK bytes, in
 * order, and hears what became of it; a client that is to draw a picture asks for it by name and gets it in chunks of
 * DOWN_CHUNK (or word that there is none); an operator's removal is sent to everyone. The booth's picture list asks
 * for the player's own pictures and the server's limits. A player has one upload going at a time (a new one replaces
 * it) and at most ASKS requests in ASK_TICKS.
 */
public final class DecalNetwork {
    private DecalNetwork() {}

    // under the 32767 bytes a serverbound custom packet may be (with its header); the other way 1 MiB
    public static final int UP_CHUNK = 30_000, DOWN_CHUNK = 256 * 1024;
    private static final int ASKS = 64, ASK_TICKS = 200, MOST_LISTED = 4096;

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, path); }

    /** Client to server: a chunk of a picture (total its whole size, offset where this chunk goes). */
    public record UploadPayload(String sha, int total, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<UploadPayload> TYPE = new Type<>(id("decal_upload"));
        public static final StreamCodec<FriendlyByteBuf, UploadPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.sha(), 64); buf.writeVarInt(p.total()); buf.writeVarInt(p.offset()); buf.writeByteArray(p.data());
        }, buf -> new UploadPayload(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(UP_CHUNK)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: what became of an upload (refusal -1: taken), with the picture's size. */
    public record UploadedPayload(String sha, int refusal, int width, int height) implements CustomPacketPayload {
        public static final Type<UploadedPayload> TYPE = new Type<>(id("decal_uploaded"));
        public static final StreamCodec<FriendlyByteBuf, UploadedPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.sha(), 64); buf.writeVarInt(p.refusal() + 1); buf.writeVarInt(p.width()); buf.writeVarInt(p.height());
        }, buf -> new UploadedPayload(buf.readUtf(64), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: send me this picture. */
    public record AskPayload(String sha) implements CustomPacketPayload {
        public static final Type<AskPayload> TYPE = new Type<>(id("decal_ask"));
        public static final StreamCodec<FriendlyByteBuf, AskPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.sha(), 64), buf -> new AskPayload(buf.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: a chunk of a picture asked for. */
    public record PicturePayload(String sha, int total, int offset, byte[] data) implements CustomPacketPayload {
        public static final Type<PicturePayload> TYPE = new Type<>(id("decal_picture"));
        public static final StreamCodec<FriendlyByteBuf, PicturePayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.sha(), 64); buf.writeVarInt(p.total()); buf.writeVarInt(p.offset()); buf.writeByteArray(p.data());
        }, buf -> new PicturePayload(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(DOWN_CHUNK)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: there is no such picture (never uploaded, or removed). */
    public record GonePayload(String sha) implements CustomPacketPayload {
        public static final Type<GonePayload> TYPE = new Type<>(id("decal_gone"));
        public static final StreamCodec<FriendlyByteBuf, GonePayload> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.sha(), 64), buf -> new GonePayload(buf.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: my pictures, please. */
    public record ListPayload() implements CustomPacketPayload {
        public static final Type<ListPayload> TYPE = new Type<>(id("decal_list"));
        public static final StreamCodec<FriendlyByteBuf, ListPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {}, buf -> new ListPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A picture in a list: its name and size. */
    public record Listed(String sha, int width, int height) {}

    /** Server to client: the player's pictures, and the server's limits (uploads allowed, longest side, bytes, how many). */
    public record PicturesPayload(List<Listed> pictures, boolean uploads, int maxSide, int maxBytes, int perPlayer) implements CustomPacketPayload {
        public static final Type<PicturesPayload> TYPE = new Type<>(id("decal_pictures"));
        public static final StreamCodec<FriendlyByteBuf, PicturesPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.pictures().size());
            for (Listed l : p.pictures()) { buf.writeUtf(l.sha(), 64); buf.writeVarInt(l.width()); buf.writeVarInt(l.height()); }
            buf.writeBoolean(p.uploads()); buf.writeVarInt(p.maxSide()); buf.writeVarInt(p.maxBytes()); buf.writeVarInt(p.perPlayer());
        }, buf -> {
            int n = Math.min(buf.readVarInt(), MOST_LISTED);
            List<Listed> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) { list.add(new Listed(buf.readUtf(64), buf.readVarInt(), buf.readVarInt())); }
            return new PicturesPayload(list, buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- uploads in progress (server)

    /** A player's upload as it arrives. */
    private static final class Upload {
        final String sha;
        final byte[] bytes;
        int received;
        Upload(String sha, int total) { this.sha = sha; this.bytes = new byte[total]; }
    }

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final Map<UUID, long[]> ASKED = new HashMap<>();

    /**
     * Takes a chunk of a player's upload: null while it is coming, the result once it is all here or refused (a
     * chunk out of order or past the size it said, a size over the limit). A chunk at offset 0 starts the upload over.
     */
    public static DecalStore.Result receive(MinecraftServer server, UUID user, String name, UploadPayload p, DecalStore.Limits limits) {
        if (!Sticker.isImage(p.sha())) { UPLOADS.remove(user); return new DecalStore.Result(p.sha(), DecalStore.Refusal.HASH, 0, 0); }
        if (!limits.uploads()) { UPLOADS.remove(user); return new DecalStore.Result(p.sha(), DecalStore.Refusal.DISABLED, 0, 0); }
        if (p.total() <= 0 || p.total() > limits.maxBytes()) { UPLOADS.remove(user); return new DecalStore.Result(p.sha(), DecalStore.Refusal.TOO_LARGE, 0, 0); }
        Upload u = UPLOADS.get(user);
        if (p.offset() == 0) { u = new Upload(p.sha(), p.total()); UPLOADS.put(user, u); }
        if (u == null || !u.sha.equals(p.sha()) || u.bytes.length != p.total() || p.offset() != u.received || p.offset() + p.data().length > p.total()) {
            UPLOADS.remove(user);
            return new DecalStore.Result(p.sha(), DecalStore.Refusal.NOT_PNG, 0, 0);
        }
        System.arraycopy(p.data(), 0, u.bytes, u.received, p.data().length);
        u.received += p.data().length;
        if (u.received < u.bytes.length) { return null; }
        UPLOADS.remove(user);
        return DecalStore.store(server, user, name, u.sha, u.bytes, limits);
    }

    /** Whether a player may ask for another picture now (ASKS in ASK_TICKS). */
    private static boolean mayAsk(ServerPlayer player) {
        long now = player.server.getTickCount();
        long[] window = ASKED.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
        if (now - window[0] >= ASK_TICKS) { window[0] = now; window[1] = 0; }
        return ++window[1] <= ASKS;
    }

    /** Sends a picture to a player in chunks, or that there is none. */
    static void send(ServerPlayer player, String sha) {
        byte[] png = DecalStore.read(player.server, sha);
        if (png == null) { PacketDistributor.sendToPlayer(player, new GonePayload(sha)); return; }
        for (int offset = 0; offset < png.length; offset += DOWN_CHUNK) {
            int n = Math.min(DOWN_CHUNK, png.length - offset);
            PacketDistributor.sendToPlayer(player, new PicturePayload(sha, png.length, offset, java.util.Arrays.copyOfRange(png, offset, offset + n)));
        }
    }

    /** Tells everyone a picture is gone (an operator removed it). */
    static void gone(String sha) { PacketDistributor.sendToAllPlayers(new GonePayload(sha)); }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(UploadPayload.TYPE, UploadPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) { return; }
            DecalStore.Result r = receive(player.server, player.getUUID(), player.getGameProfile().getName(), payload, DecalStore.Limits.config());
            if (r != null) { PacketDistributor.sendToPlayer(player, new UploadedPayload(r.sha(), r.ok() ? -1 : r.refusal().ordinal(), r.width(), r.height())); }
        });
        registrar.playToServer(AskPayload.TYPE, AskPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && Sticker.isImage(payload.sha()) && mayAsk(player)) { send(player, payload.sha()); }
        });
        registrar.playToServer(ListPayload.TYPE, ListPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) { return; }
            List<Listed> mine = new ArrayList<>();
            for (DecalIndex.Entry e : DecalStore.list(player.server, player.getUUID())) {
                if (mine.size() >= MOST_LISTED) { break; }
                mine.add(new Listed(e.sha(), e.width(), e.height()));
            }
            DecalStore.Limits l = DecalStore.Limits.config();
            PacketDistributor.sendToPlayer(player, new PicturesPayload(mine, l.uploads(), l.maxSide(), l.maxBytes(), l.perPlayer()));
        });
        registrar.playToClient(UploadedPayload.TYPE, UploadedPayload.STREAM_CODEC,
                (payload, context) -> dev.mcmod.hdfv.neoforge.client.DecalUpload.uploaded(payload.sha(), payload.refusal(), payload.width(), payload.height()));
        registrar.playToClient(PicturePayload.TYPE, PicturePayload.STREAM_CODEC,
                (payload, context) -> dev.mcmod.hdfv.neoforge.client.DecalCache.received(payload.sha(), payload.total(), payload.offset(), payload.data()));
        registrar.playToClient(GonePayload.TYPE, GonePayload.STREAM_CODEC, (payload, context) -> dev.mcmod.hdfv.neoforge.client.DecalCache.gone(payload.sha()));
        registrar.playToClient(PicturesPayload.TYPE, PicturesPayload.STREAM_CODEC, (payload, context) -> dev.mcmod.hdfv.neoforge.client.DecalUpload.listed(payload));
    }

    // ---------------------------------------------------------------- client side

    /** Client side: sends a prepared picture in chunks. */
    public static void upload(String sha, byte[] png) {
        for (int offset = 0; offset < png.length; offset += UP_CHUNK) {
            int n = Math.min(UP_CHUNK, png.length - offset);
            PacketDistributor.sendToServer(new UploadPayload(sha, png.length, offset, java.util.Arrays.copyOfRange(png, offset, offset + n)));
        }
    }

    /** Client side: asks for a picture. */
    public static void ask(String sha) { PacketDistributor.sendToServer(new AskPayload(sha)); }

    /** Client side: asks for the player's pictures and the limits. */
    public static void list() { PacketDistributor.sendToServer(new ListPayload()); }
}
