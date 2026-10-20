package dev.mcmod.hdfv.neoforge.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The layout screen's edits (0.7): small requests (add the drawing in a slot, remove a part, attach it to a mount point,
 * nudge or turn it, clear, save to or load from the vehicle blueprint); the server holds the layout and sends it back
 * with the controller's data.
 */
public final class AssemblyNetwork {
    private AssemblyNetwork() {}

    public record EditPayload(BlockPos pos, RoomControllerBlockEntity.Edit edit) implements CustomPacketPayload {
        public static final Type<EditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "layout_edit"));
        public static final StreamCodec<FriendlyByteBuf, EditPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            RoomControllerBlockEntity.Edit e = p.edit();
            buf.writeBlockPos(p.pos());
            buf.writeVarInt(e.op().ordinal());
            buf.writeVarInt(e.slot());
            buf.writeUtf(e.id(), 64);
            buf.writeUtf(e.part(), 64);
            buf.writeUtf(e.point(), 64);
            buf.writeDouble(e.by().x()); buf.writeDouble(e.by().y()); buf.writeDouble(e.by().z());
            buf.writeDouble(e.yawDeg()); buf.writeDouble(e.pitchDeg()); buf.writeDouble(e.rollDeg());
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            RoomControllerBlockEntity.Op[] ops = RoomControllerBlockEntity.Op.values();
            RoomControllerBlockEntity.Op op = ops[Math.floorMod(buf.readVarInt(), ops.length)];
            int slot = buf.readVarInt();
            String id = buf.readUtf(64), part = buf.readUtf(64), point = buf.readUtf(64);
            Vec3d by = new Vec3d(finite(buf.readDouble(), 10), finite(buf.readDouble(), 10), finite(buf.readDouble(), 10));
            return new EditPayload(pos, new RoomControllerBlockEntity.Edit(op, slot, id, part, point, by, finite(buf.readDouble(), 360),
                    finite(buf.readDouble(), 360), finite(buf.readDouble(), 360)));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static double finite(double v, double limit) { return Double.isFinite(v) ? Math.clamp(v, -limit, limit) : 0; }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(EditPayload.TYPE, EditPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof RoomMenu menu) || !menu.pos.equals(payload.pos())) { return; }
            if (!menu.stillValid(player) || !(player.level().getBlockEntity(payload.pos()) instanceof RoomControllerBlockEntity be)) { return; }
            Component refused = be.apply(payload.edit());
            if (refused != null) { player.displayClientMessage(refused, true); }
        });
    }

    /** Client side. */
    public static void send(BlockPos pos, RoomControllerBlockEntity.Edit edit) { PacketDistributor.sendToServer(new EditPayload(pos, edit)); }
}
