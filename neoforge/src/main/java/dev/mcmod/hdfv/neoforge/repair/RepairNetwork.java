package dev.mcmod.hdfv.neoforge.repair;

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
 * The repair shop screen's actions (0.25): a pick toggled, all damaged or none, the shop's actions. Bounded on the way
 * in (the op by index, the key's length); the shop checks the rest (RepairShopBlockEntity.apply: the part and its
 * condition exist).
 */
public final class RepairNetwork {
    private RepairNetwork() {}

    public record EditPayload(BlockPos pos, RepairShopBlockEntity.Edit edit) implements CustomPacketPayload {
        public static final Type<EditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "repair_shop_edit"));
        public static final StreamCodec<FriendlyByteBuf, EditPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos());
            buf.writeVarInt(p.edit().op().ordinal());
            buf.writeUtf(p.edit().key(), 96);
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            RepairShopBlockEntity.Op[] ops = RepairShopBlockEntity.Op.values();
            RepairShopBlockEntity.Op op = ops[Math.floorMod(buf.readVarInt(), ops.length)];
            return new EditPayload(pos, new RepairShopBlockEntity.Edit(op, buf.readUtf(96)));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(EditPayload.TYPE, EditPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof RepairShopMenu menu) || !menu.pos.equals(payload.pos())) { return; }
            if (!menu.stillValid(player) || !(player.level().getBlockEntity(payload.pos()) instanceof RepairShopBlockEntity be)) { return; }
            Component refused = be.apply(payload.edit(), player);
            if (refused != null) { player.displayClientMessage(refused, true); be.refused(refused); }
        });
    }

    /** Client side. */
    public static void send(BlockPos pos, RepairShopBlockEntity.Edit edit) { PacketDistributor.sendToServer(new EditPayload(pos, edit)); }
}
