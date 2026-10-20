package dev.mcmod.hdfv.neoforge.design;

import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.client.ClientAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.function.IntFunction;

/** The design screen asks the server to save a design onto a blueprint (the server reads it again: Blueprints.save) and hears how it went. */
public final class DesignNetwork {
    private DesignNetwork() {}

    private static <E extends Enum<E>> StreamCodec<ByteBuf, E> ordinal(E[] values) {
        IntFunction<E> byId = i -> values[Math.floorMod(i, values.length)];
        return ByteBufCodecs.idMapper(byId, Enum::ordinal);
    }

    public record SavePayload(String json, Blueprints.Target target) implements CustomPacketPayload {
        public static final Type<SavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "save_design"));
        public static final StreamCodec<ByteBuf, SavePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(Designs.MAX_JSON), SavePayload::json, ordinal(Blueprints.Target.values()), SavePayload::target, SavePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SavedPayload(Blueprints.Result result) implements CustomPacketPayload {
        public static final Type<SavedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "design_saved"));
        public static final StreamCodec<ByteBuf, SavedPayload> STREAM_CODEC = StreamCodec.composite(
                ordinal(Blueprints.Result.values()), SavedPayload::result, SavedPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SavePayload.TYPE, SavePayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) { return; }
            PacketDistributor.sendToPlayer(player, new SavedPayload(Blueprints.save(player, payload.json(), payload.target())));
        });
        registrar.playToClient(SavedPayload.TYPE, SavedPayload.STREAM_CODEC, (payload, context) -> ClientAccess.saved(payload.result()));
    }

    /** Client side. */
    public static void save(String json, Blueprints.Target target) { PacketDistributor.sendToServer(new SavePayload(json, target)); }
}
