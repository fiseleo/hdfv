package dev.mcmod.hdfv.neoforge.paint;

import dev.mcmod.hdfv.look.Sticker;
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
 * The paint booth screen's edits (0.13): a look of a part, a sticker added, changed or removed, the booth's actions
 * (reset, start, cancel, scan, the door). Bounded on the way in (texts, numbers in range, enums by index); the booth
 * checks the rest (PaintBoothBlockEntity.apply: looks alone, the part's own checks).
 */
public final class PaintBoothNetwork {
    private PaintBoothNetwork() {}

    public record EditPayload(BlockPos pos, PaintBoothBlockEntity.Edit edit) implements CustomPacketPayload {
        public static final Type<EditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "paint_booth_edit"));
        public static final StreamCodec<FriendlyByteBuf, EditPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            PaintBoothBlockEntity.Edit e = p.edit();
            buf.writeBlockPos(p.pos());
            buf.writeVarInt(e.op().ordinal());
            buf.writeUtf(e.part(), 64);
            buf.writeUtf(e.page(), 32);
            buf.writeUtf(e.param(), 32);
            buf.writeUtf(e.text(), 64);
            buf.writeVarInt(e.index() + 1);
            buf.writeBoolean(e.sticker() != null);
            if (e.sticker() != null) { write(buf, e.sticker()); }
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            PaintBoothBlockEntity.Op[] ops = PaintBoothBlockEntity.Op.values();
            PaintBoothBlockEntity.Op op = ops[Math.floorMod(buf.readVarInt(), ops.length)];
            String part = buf.readUtf(64), page = buf.readUtf(32), param = buf.readUtf(32), text = buf.readUtf(64);
            int index = buf.readVarInt() - 1;
            Sticker sticker = buf.readBoolean() ? read(buf) : null;
            return new EditPayload(pos, new PaintBoothBlockEntity.Edit(op, part, page, param, text, index, sticker));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void write(FriendlyByteBuf buf, Sticker s) {
        buf.writeUtf(s.image(), 64);
        buf.writeDouble(s.aspect());
        if (s.place() instanceof Sticker.OnWing w) {
            buf.writeBoolean(true);
            buf.writeDouble(w.eta()); buf.writeDouble(w.chord()); buf.writeVarInt(w.surface().ordinal()); buf.writeVarInt(w.side().ordinal());
        } else {
            Sticker.OnBody b = (Sticker.OnBody) s.place();
            buf.writeBoolean(false);
            buf.writeDouble(b.x()); buf.writeDouble(b.t());
        }
        buf.writeDouble(s.widthM()); buf.writeDouble(s.rotationDeg()); buf.writeBoolean(s.flip()); buf.writeBoolean(s.pair()); buf.writeVarInt(s.layer());
    }

    private static Sticker read(FriendlyByteBuf buf) {
        String image = buf.readUtf(64);
        double aspect = finite(buf.readDouble(), Sticker.MAX_ASPECT);
        Sticker.Place place;
        if (buf.readBoolean()) {
            double eta = finite(buf.readDouble(), 1), chord = finite(buf.readDouble(), 1);
            Sticker.Surface[] surfaces = Sticker.Surface.values();
            Sticker.Side[] sides = Sticker.Side.values();
            place = new Sticker.OnWing(eta, chord, surfaces[Math.floorMod(buf.readVarInt(), surfaces.length)], sides[Math.floorMod(buf.readVarInt(), sides.length)]);
        } else {
            place = new Sticker.OnBody(finite(buf.readDouble(), 1000), finite(buf.readDouble(), 10));
        }
        double width = finite(buf.readDouble(), Sticker.MAX_WIDTH_M), rotation = finite(buf.readDouble(), 180);
        boolean flip = buf.readBoolean(), pair = buf.readBoolean();
        int layer = Math.clamp(buf.readVarInt(), 0, Sticker.LAYERS - 1);
        return new Sticker(image, aspect, place, width, rotation, flip, pair, layer);
    }

    private static double finite(double v, double limit) { return Double.isFinite(v) ? Math.clamp(v, -limit, limit) : 0; }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(EditPayload.TYPE, EditPayload.STREAM_CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof PaintBoothMenu menu) || !menu.pos.equals(payload.pos())) { return; }
            if (!menu.stillValid(player) || !(player.level().getBlockEntity(payload.pos()) instanceof PaintBoothBlockEntity be)) { return; }
            Component refused = be.apply(payload.edit(), player);
            if (refused != null) { player.displayClientMessage(refused, true); }
        });
    }

    /** Client side. */
    public static void send(BlockPos pos, PaintBoothBlockEntity.Edit edit) { PacketDistributor.sendToServer(new EditPayload(pos, edit)); }
}
