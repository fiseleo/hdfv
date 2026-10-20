package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A petroleum screen's setting (0.9): the block, the setting's key and its value, from the client. The server applies it
 * only for a player with that block's screen open and within reach, and the block checks the value (Configurable.set);
 * a refusal is shown on the player's action bar.
 */
public final class PetroleumNetwork {
    private PetroleumNetwork() {}

    public record SetPayload(BlockPos pos, String key, double value) implements CustomPacketPayload {
        public static final Type<SetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "petroleum_set"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.key(), 32); buf.writeDouble(p.value()); },
                buf -> new SetPayload(buf.readBlockPos(), buf.readUtf(32), finite(buf.readDouble())));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static double finite(double v) { return Double.isFinite(v) ? Math.clamp(v, -1e6, 1e6) : 0; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SetPayload.TYPE, SetPayload.CODEC, PetroleumNetwork::handle);
    }

    private static void handle(SetPayload p, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) { apply(player, p); }
    }

    /** Applies a setting a player sent: false when the player has no screen of that block open within reach. */
    static boolean apply(ServerPlayer player, SetPayload p) {
        if (!(player.containerMenu instanceof PetroleumMenu menu) || !menu.pos.equals(p.pos()) || !menu.stillValid(player)) { return false; }
        if (!(player.level().getBlockEntity(p.pos()) instanceof Configurable c)) { return false; }
        Component refused = c.set(p.key(), p.value());
        if (refused != null) { player.displayClientMessage(refused, true); }
        c.sendView();
        return true;
    }

    /** Sends a setting (client). */
    public static void send(BlockPos pos, String key, double value) { PacketDistributor.sendToServer(new SetPayload(pos, key, value)); }
}
