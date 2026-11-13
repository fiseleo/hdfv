package dev.mcmod.hdfv.neoforge.storage;

import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 0.25: the cargo screens' packets. To the server: open a hold of a vehicle (hold -1: the rider's inventory key, the
 * hold nearest the player) and ask how many slots each hold uses (the status screen's cargo tab). The server trusts
 * nothing of them but the entity id and the index: the vehicle must be in the player's level, usable, and the player
 * aboard or near (VehicleCargo.open; the question only within STATUS_M, as far as the status screen stays open). To the
 * client: the slots used.
 */
public final class CargoNetwork {
    private CargoNetwork() {}

    /** How near a vehicle a player asking of its holds must be, m (the status screen closes beyond 24 m). */
    public static final double STATUS_M = 32;
    /** The most holds a summary carries. */
    static final int MAX_HOLDS = 64;

    public record OpenPayload(int entity, int hold) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "cargo_open"));
        public static final StreamCodec<FriendlyByteBuf, OpenPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> { buf.writeVarInt(p.entity()); buf.writeVarInt(p.hold()); },
                buf -> new OpenPayload(buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record QueryPayload(int entity) implements CustomPacketPayload {
        public static final Type<QueryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "cargo_query"));
        public static final StreamCodec<FriendlyByteBuf, QueryPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.entity()), buf -> new QueryPayload(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Each hold's used slots, in hold order. */
    public record SummaryPayload(int entity, int[] used) implements CustomPacketPayload {
        public static final Type<SummaryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "cargo_summary"));
        public static final StreamCodec<FriendlyByteBuf, SummaryPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.entity());
            buf.writeVarInt(Math.min(p.used().length, MAX_HOLDS));
            for (int i = 0; i < Math.min(p.used().length, MAX_HOLDS); i++) { buf.writeVarInt(p.used()[i]); }
        }, buf -> {
            int entity = buf.readVarInt(), n = Math.clamp(buf.readVarInt(), 0, MAX_HOLDS);
            int[] used = new int[n];
            for (int i = 0; i < n; i++) { used[i] = buf.readVarInt(); }
            return new SummaryPayload(entity, used);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(OpenPayload.TYPE, OpenPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level().getEntity(payload.entity()) instanceof PartVehicle v)) { return; }
            Component refused = open(player, v, payload.hold());
            if (refused != null) { player.displayClientMessage(refused, true); }
        });
        registrar.playToServer(QueryPayload.TYPE, QueryPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level().getEntity(payload.entity()) instanceof PartVehicle v)) { return; }
            if (player.distanceToSqr(v) > STATUS_M * STATUS_M) { return; }
            PacketDistributor.sendToPlayer(player, new SummaryPayload(v.getId(), used(v)));
        });
        registrar.playToClient(SummaryPayload.TYPE, SummaryPayload.STREAM_CODEC,
                (payload, context) -> dev.mcmod.hdfv.neoforge.client.ClientAccess.cargoSummary(payload.entity(), payload.used()));
    }

    /** Opens a hold for a player (hold -1: the rider's, the nearest hold); why not, or null. */
    public static Component open(ServerPlayer player, PartVehicle v, int hold) {
        if (hold < 0) {
            if (player.getVehicle() != v) { return Component.translatable("hdfv.cargo.too_far"); }
            hold = VehicleCargo.nearest(player, v);
        }
        return VehicleCargo.open(player, v, hold);
    }

    /** Each hold's used slots (none for a vehicle that cannot be used). */
    static int[] used(PartVehicle v) {
        if (!VehicleCargo.usable(v)) { return new int[0]; }
        int[] out = new int[Math.min(v.holdCount(), MAX_HOLDS)];
        for (int k = 0; k < out.length; k++) {
            VehicleCargo.Hold h = VehicleCargo.hold(v, k);
            out[k] = h == null ? 0 : h.used();
        }
        return out;
    }

    /** Client side. */
    public static void open(int entity, int hold) { PacketDistributor.sendToServer(new OpenPayload(entity, hold)); }
    public static void query(int entity) { PacketDistributor.sendToServer(new QueryPayload(entity)); }
}
