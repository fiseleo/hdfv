package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.airport.AircraftKind;
import dev.mcmod.hdfv.airport.Atc;
import dev.mcmod.hdfv.airport.Atis;
import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.airport.Runway;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The airfield's messages (0.20).
 * <ul>
 *   <li>A pilot's call ({@link CallPayload}): accepted only from the vehicle's driver, of an aircraft, inside a tower's
 *   zone, at most 4 a second; handed to that tower (TowerBlockEntity.request). ATIS calls are answered at once.</li>
 *   <li>A player's command at a tower's screen ({@link CommandPayload}): only with that tower's screen open within
 *   reach; the tower decides the rest (who is in control).</li>
 *   <li>To pilots ({@link StatusPayload}): what the tower said (an instruction or a warning) and the ATIS, to the players
 *   aboard; the tower's state lives on the server only, never in the vehicle's control channels.</li>
 *   <li>To players at a tower's screen ({@link ViewPayload}): its view every half second (radar, strips, ATIS, lights).</li>
 *   <li>To players near an airfield ({@link LightsPayload}, {@link CircuitsPayload}): its fittings and runways when
 *   they change, its circuits' intensities when they change; the client lights them with AeroCore's FieldLamps.</li>
 * </ul>
 */
public final class AirportNetwork {
    private AirportNetwork() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, path); }

    public record CallPayload(int entityId, Atc.Request request, boolean atis) implements CustomPacketPayload {
        public static final Type<CallPayload> TYPE = new Type<>(id("atc_call"));
        public static final StreamCodec<FriendlyByteBuf, CallPayload> CODEC = StreamCodec.of(
                (b, p) -> { b.writeVarInt(p.entityId()); b.writeVarInt(p.request().ordinal()); b.writeBoolean(p.atis()); },
                b -> new CallPayload(b.readVarInt(), Atc.Request.values()[Math.floorMod(b.readVarInt(), Atc.Request.values().length)], b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A command at a tower's screen: take or release control, an instruction to an aircraft, a light circuit's step or auto, paint the runways. */
    public enum CommandKind { TAKE, RELEASE, INSTRUCT, CIRCUIT_STEP, CIRCUIT_AUTO, PAINT }

    public record Command(CommandKind kind, UUID target, int value) {}

    public record CommandPayload(BlockPos pos, Command command) implements CustomPacketPayload {
        public static final Type<CommandPayload> TYPE = new Type<>(id("tower_command"));
        public static final StreamCodec<FriendlyByteBuf, CommandPayload> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.pos()); b.writeVarInt(p.command().kind().ordinal()); b.writeUUID(p.command().target()); b.writeVarInt(p.command().value()); },
                b -> new CommandPayload(b.readBlockPos(), new Command(CommandKind.values()[Math.floorMod(b.readVarInt(), CommandKind.values().length)], b.readUUID(), b.readVarInt())));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A tower's word to a pilot: the vehicle, the instruction (or a warning), runway, number, and the ATIS. */
    public record StatusPayload(int entityId, CompoundTag status) implements CustomPacketPayload {
        public static final Type<StatusPayload> TYPE = new Type<>(id("atc_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StatusPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StatusPayload::entityId, ByteBufCodecs.COMPOUND_TAG, StatusPayload::status, StatusPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ViewPayload(BlockPos pos, CompoundTag view) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(id("tower_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ViewPayload::pos, ByteBufCodecs.COMPOUND_TAG, ViewPayload::view, ViewPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** An airfield's fittings and runways (the tower's position names it); empty lists take it away. */
    public record LightsPayload(BlockPos tower, CompoundTag lights) implements CustomPacketPayload {
        public static final Type<LightsPayload> TYPE = new Type<>(id("airfield_lights"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LightsPayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LightsPayload::tower, ByteBufCodecs.COMPOUND_TAG, LightsPayload::lights, LightsPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CircuitsPayload(BlockPos tower, float[] levels) implements CustomPacketPayload {
        public static final Type<CircuitsPayload> TYPE = new Type<>(id("airfield_circuits"));
        public static final StreamCodec<FriendlyByteBuf, CircuitsPayload> CODEC = StreamCodec.of(
                (b, p) -> { b.writeBlockPos(p.tower()); b.writeVarInt(p.levels().length); for (float f : p.levels()) { b.writeFloat(f); } },
                b -> {
                    BlockPos pos = b.readBlockPos();
                    float[] l = new float[Math.min(16, b.readVarInt())];
                    for (int i = 0; i < l.length; i++) { float f = b.readFloat(); l[i] = Float.isFinite(f) ? Math.clamp(f, 0f, 1f) : 0f; }
                    return new CircuitsPayload(pos, l);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(CallPayload.TYPE, CallPayload.CODEC, AirportNetwork::call);
        r.playToServer(CommandPayload.TYPE, CommandPayload.CODEC, AirportNetwork::command);
        r.playToClient(StatusPayload.TYPE, StatusPayload.CODEC, (p, c) -> AirportClientState.status(p.entityId(), p.status()));
        r.playToClient(ViewPayload.TYPE, ViewPayload.CODEC, (p, c) -> AirportClientState.view(p.pos(), p.view()));
        r.playToClient(LightsPayload.TYPE, LightsPayload.CODEC, (p, c) -> AirportClientState.lights(p.tower(), p.lights()));
        r.playToClient(CircuitsPayload.TYPE, CircuitsPayload.CODEC, (p, c) -> AirportClientState.circuits(p.tower(), p.levels()));
    }

    // ---------------------------------------------------------------- server

    private static final Map<UUID, long[]> CALLS = new HashMap<>();

    private static void call(CallPayload p, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel server)) { return; }
        accept(server, player, p);
    }

    /** Applies a pilot's call: false when refused (not the driver, not an aircraft, too many, no tower in reach). */
    static boolean accept(ServerLevel server, ServerPlayer player, CallPayload p) {
        Entity e = server.getEntity(p.entityId());
        if (!(e instanceof PartVehicle v) || v.getFirstPassenger() != player || v.blueprint() == null || !AircraftKind.of(v.blueprint()).flies()) { return false; }
        long now = server.getGameTime();
        long[] c = CALLS.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
        if (now - c[0] >= 20) { c[0] = now; c[1] = 0; }
        if (++c[1] > 4) { return false; }
        TowerBlockEntity tower = towerFor(server, v);
        if (tower == null) {
            player.displayClientMessage(Component.translatable("hdfv.atc.no_tower"), true);
            return false;
        }
        if (p.atis()) {
            tell(server, tower, new Atc.Transmission(v.getUUID(), Atc.Instruction.NONE, tower.inUse() == null ? "" : tower.inUse().designator(), 0, null), Said.ATIS);
            return true;
        }
        tower.request(v.getUUID(), p.request());
        return true;
    }

    /** The tower whose reach a vehicle is in (the nearest loaded one within its zone, or radio reach). */
    static TowerBlockEntity towerFor(ServerLevel server, PartVehicle v) {
        TowerBlockEntity best = null;
        double d = Double.MAX_VALUE;
        for (BlockPos pos : AirfieldData.of(server).towers()) {
            if (!server.isLoaded(pos) || !(server.getBlockEntity(pos) instanceof TowerBlockEntity t)) { continue; }
            double dist = Math.hypot(v.getX() - pos.getX(), v.getZ() - pos.getZ());
            if (dist <= Math.max(t.zoneM(), TowerBlockEntity.RADIO_M) && dist < d) { best = t; d = dist; }
        }
        return best;
    }

    private static void command(CommandPayload p, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) { return; }
        apply(player, p);
    }

    /** Applies a command from a tower's screen: false without that screen open within reach. */
    static boolean apply(ServerPlayer player, CommandPayload p) {
        if (!(player.containerMenu instanceof TowerMenu menu) || !menu.pos.equals(p.pos()) || !menu.stillValid(player)) { return false; }
        if (!(player.level().getBlockEntity(p.pos()) instanceof TowerBlockEntity tower)) { return false; }
        Component refused = tower.command(player, p.command());
        if (refused != null) { player.displayClientMessage(refused, true); }
        return true;
    }

    /** Whether a status is news (something said), the ATIS a pilot asked for, or the reminder every 5 s. */
    enum Said { NEWS, ATIS, REMINDER }

    /** Sends what a tower said to the players aboard that vehicle. */
    static void tell(ServerLevel server, TowerBlockEntity tower, Atc.Transmission x, Said said) {
        PartVehicle v = TowerBlockEntity.vehicle(server, x.id());
        if (v == null) { return; }
        CompoundTag t = new CompoundTag();
        t.putLong("Tower", tower.getBlockPos().asLong());
        t.putString("Said", said.name());
        if (x.instruction() != null) { t.putString("Instruction", x.instruction().name()); }
        if (x.warning() != null) { t.putString("Warning", x.warning().name()); }
        t.putString("Runway", x.runway() == null ? "" : x.runway());
        t.putInt("Sequence", x.sequence());
        Atc.Flight f = tower.result().flights().stream().filter(g -> g.id().equals(x.id())).findFirst().orElse(null);
        if (f != null) { t.putString("Phase", f.phase().name()); t.putString("Current", f.instruction().name()); }
        t.putBoolean("Manned", tower.manned() || tower.holder() != null);
        Atis a = tower.atis();
        if (a != null) { t.put("Atis", atis(a)); }
        StatusPayload payload = new StatusPayload(v.getId(), t);
        for (Entity passenger : v.getPassengers()) {
            if (passenger instanceof ServerPlayer sp) { PacketDistributor.sendToPlayer(sp, payload); }
        }
    }

    static CompoundTag atis(Atis a) {
        CompoundTag t = new CompoundTag();
        t.putString("Letter", String.valueOf(a.letter()));
        t.putString("Runway", a.runway());
        t.putInt("From", a.windFromDeg());
        t.putDouble("Wind", a.windMps());
        t.putDouble("Gust", a.gustMps());
        t.putDouble("Visibility", a.visibilityM());
        t.putBoolean("Rain", a.rain());
        t.putBoolean("Thunder", a.thunder());
        t.putInt("Temperature", a.temperatureC());
        t.putInt("Qnh", a.qnhHpa());
        return t;
    }

    /** Sends a tower's view to the players with its screen open. */
    static void sendView(ServerLevel server, TowerBlockEntity tower) {
        List<ServerPlayer> watching = server.players().stream().filter(p -> p.containerMenu instanceof TowerMenu m && m.pos.equals(tower.getBlockPos())).toList();
        if (watching.isEmpty()) { return; }
        ViewPayload payload = new ViewPayload(tower.getBlockPos(), TowerView.of(server, tower));
        for (ServerPlayer p : watching) { PacketDistributor.sendToPlayer(p, payload); }
    }

    /** An airfield's fittings and runways as sent. */
    static CompoundTag lights(List<Fixture> fixtures, List<Runway> runways) {
        CompoundTag t = new CompoundTag();
        int[] f = new int[fixtures.size() * 4];
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture x = fixtures.get(i);
            f[4 * i] = x.kind().ordinal(); f[4 * i + 1] = x.x(); f[4 * i + 2] = x.y(); f[4 * i + 3] = x.z();
        }
        t.putIntArray("Fixtures", f);
        ListTag r = new ListTag();
        for (Runway w : runways) {
            CompoundTag c = new CompoundTag();
            c.putString("Axis", w.axis().name());
            c.putDouble("Cross", w.cross()); c.putDouble("Low", w.low()); c.putDouble("High", w.high()); c.putDouble("Y", w.y()); c.putDouble("Width", w.widthM());
            c.putString("Suffix", w.suffix());
            r.add(c);
        }
        t.put("Runways", r);
        return t;
    }
}
