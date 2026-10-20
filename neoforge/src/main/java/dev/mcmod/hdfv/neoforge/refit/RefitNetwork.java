package dev.mcmod.hdfv.neoforge.refit;

import dev.mcmod.hdfv.look.LampRules;
import dev.mcmod.hdfv.look.LampSpec;
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
 * The refit shop screen's edits (0.16): an adjustable of a part, the scale, a lamp added, changed or removed, the
 * shop's actions. Bounded on the way in (texts, numbers in range, enums by index); the shop checks the rest
 * (RefitShopBlockEntity.apply: Refit's whitelist, the parts' own checks, the same vehicle).
 */
public final class RefitNetwork {
    private RefitNetwork() {}

    public record EditPayload(BlockPos pos, RefitShopBlockEntity.Edit edit) implements CustomPacketPayload {
        public static final Type<EditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "refit_shop_edit"));
        public static final StreamCodec<FriendlyByteBuf, EditPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            RefitShopBlockEntity.Edit e = p.edit();
            buf.writeBlockPos(p.pos());
            buf.writeVarInt(e.op().ordinal());
            buf.writeUtf(e.part(), 64);
            buf.writeUtf(e.page(), 32);
            buf.writeUtf(e.param(), 32);
            buf.writeUtf(e.text(), 128);
            buf.writeVarInt(e.index() + 1);
            buf.writeDouble(e.value());
            buf.writeBoolean(e.lamp() != null);
            if (e.lamp() != null) { write(buf, e.lamp()); }
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            RefitShopBlockEntity.Op[] ops = RefitShopBlockEntity.Op.values();
            RefitShopBlockEntity.Op op = ops[Math.floorMod(buf.readVarInt(), ops.length)];
            String part = buf.readUtf(64), page = buf.readUtf(32), param = buf.readUtf(32), text = buf.readUtf(128);
            int index = Math.clamp(buf.readVarInt() - 1, -1, LampSpec.MAX);
            double value = finite(buf.readDouble(), 10);
            LampSpec lamp = buf.readBoolean() ? read(buf) : null;
            return new EditPayload(pos, new RefitShopBlockEntity.Edit(op, part, page, param, text, index, value, lamp));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void write(FriendlyByteBuf buf, LampSpec l) {
        buf.writeVarInt(l.role().ordinal());
        buf.writeVarInt(l.style().ordinal());
        buf.writeVarInt(l.face().ordinal());
        buf.writeDouble(l.x()); buf.writeDouble(l.t()); buf.writeDouble(l.across()); buf.writeDouble(l.up()); buf.writeDouble(l.standM());
        buf.writeBoolean(l.pair());
        buf.writeDouble(l.sizeM());
        buf.writeUtf(l.lightColor(), 8); buf.writeUtf(l.housingColor(), 8);
        buf.writeDouble(l.yawDeg()); buf.writeDouble(l.pitchDeg());
    }

    private static LampSpec read(FriendlyByteBuf buf) {
        LampRules.Role[] roles = LampRules.Role.values();
        LampSpec.Style[] styles = LampSpec.Style.values();
        LampSpec.Face[] faces = LampSpec.Face.values();
        LampRules.Role role = roles[Math.floorMod(buf.readVarInt(), roles.length)];
        LampSpec.Style style = styles[Math.floorMod(buf.readVarInt(), styles.length)];
        LampSpec.Face face = faces[Math.floorMod(buf.readVarInt(), faces.length)];
        double x = finite(buf.readDouble(), 1000), t = finite(buf.readDouble(), 10), across = finite(buf.readDouble(), 1), up = Math.clamp(finite(buf.readDouble(), 1), 0, 1);
        double stand = Math.clamp(finite(buf.readDouble(), 3), 0, 3);
        boolean pair = buf.readBoolean();
        double size = Math.clamp(finite(buf.readDouble(), LampSpec.MAX_SIZE_M), LampSpec.MIN_SIZE_M, LampSpec.MAX_SIZE_M);
        String light = buf.readUtf(8), housing = buf.readUtf(8);
        double yaw = finite(buf.readDouble(), 60), pitch = finite(buf.readDouble(), 60);
        return new LampSpec(role, style, face, x, t, across, up, stand, pair, size, light, housing, yaw, pitch);
    }

    private static double finite(double v, double limit) { return Double.isFinite(v) ? Math.clamp(v, -limit, limit) : 0; }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(EditPayload.TYPE, EditPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof RefitShopMenu menu) || !menu.pos.equals(payload.pos())) { return; }
            if (!menu.stillValid(player) || !(player.level().getBlockEntity(payload.pos()) instanceof RefitShopBlockEntity be)) { return; }
            Component refused = be.apply(payload.edit(), player);
            if (refused != null) { player.displayClientMessage(refused, true); be.refused(refused); }
        });
    }

    /** Client side. */
    public static void send(BlockPos pos, RefitShopBlockEntity.Edit edit) { PacketDistributor.sendToServer(new EditPayload(pos, edit)); }
}
