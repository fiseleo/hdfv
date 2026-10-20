package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.aerocore.neoforge.probe.BodyWorld;
import dev.mcmod.aerocore.neoforge.probe.CoupledBody;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.airport.AirfieldLighting;
import dev.mcmod.hdfv.airport.AircraftKind;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The airfields' upkeep in a level (0.20), every 2 s:
 * <ul>
 *   <li>A tower with an aircraft within twice its radio range keeps its chunk ticking (a short ticket, renewed while
 *   so): an airfield runs for those flying to it, though nobody is beside its tower. Server setting towerTickets. (Not
 *   for players merely within {@link #NEAR_M}: with airports every few kilometres that kept five by five chunks round
 *   every tower within 8 km loaded, and generated them the moment a region was planned.)</li>
 *   <li>Players within {@link #NEAR_M} of a tower get its airfield's fittings and runways when they change and its
 *   circuits' intensities when they change (FieldLamps on their client lights them, far beyond their chunks); players
 *   who move away are told to forget it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID)
public final class AirTraffic {
    private AirTraffic() {}

    /** How near a player must be for an airfield's lights and its tower's ticket (m). */
    public static final double NEAR_M = 8_000;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("hdfv_tower", Comparator.comparingLong(ChunkPos::toLong), 100);

    /** What each player was last sent of each airfield: its data version and circuits. */
    private record Sent(int version, float[] circuits) {}

    private static final Map<UUID, Map<BlockPos, Sent>> SENT = new HashMap<>();

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 40 != 7) { return; }
        AirfieldData data = AirfieldData.of(level);
        java.util.Set<BlockPos> towers = data.towers();
        if (towers.isEmpty() && SENT.isEmpty()) { return; }
        List<ServerPlayer> players = level.players();
        for (BlockPos tower : towers) {
            if (HdfvConfig.towerTickets() && aircraftNear(level, tower)) {
                ChunkPos c = new ChunkPos(tower);
                level.getChunkSource().addRegionTicket(TICKET, c, 2, c);
            }
        }
        for (ServerPlayer p : players) { sync(level, data, p, towers); }
    }

    private static final Map<dev.mcmod.aerocore.api.vehicle.VehicleBlueprint, AircraftKind> KINDS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static boolean aircraftNear(ServerLevel level, BlockPos tower) {
        for (CoupledBody b : BodyWorld.bodies(level)) {
            if (b instanceof PartVehicle v && !v.isRemoved() && v.blueprint() != null && Math.hypot(v.getX() - tower.getX(), v.getZ() - tower.getZ()) <= TowerBlockEntity.RADIO_M * 2
                    && KINDS.computeIfAbsent(v.blueprint(), AircraftKind::of).flies()) { return true; }
        }
        return false;
    }

    private static void sync(ServerLevel level, AirfieldData data, ServerPlayer p, java.util.Set<BlockPos> towers) {
        Map<BlockPos, Sent> mine = SENT.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        for (BlockPos tower : towers) {
            boolean near = Math.hypot(p.getX() - tower.getX(), p.getZ() - tower.getZ()) <= NEAR_M;
            Sent was = mine.get(tower);
            if (!near) {
                if (was != null) { PacketDistributor.sendToPlayer(p, new AirportNetwork.LightsPayload(tower, AirportNetwork.lights(List.of(), List.of()))); mine.remove(tower); }
                continue;
            }
            if (was == null || was.version() != data.version()) {
                // the airfield as the tower owns it (computed without the tower loaded: by distance)
                java.util.List<dev.mcmod.hdfv.airport.Fixture> fixtures = new java.util.ArrayList<>();
                for (var f : data.fixturesNear(tower.getX(), tower.getZ(), TowerBlockEntity.AIRFIELD_M)) {
                    if (TowerBlockEntity.nearestTower(data, f.x(), f.z()).equals(tower)) { fixtures.add(f); }
                }
                java.util.List<dev.mcmod.hdfv.airport.Runway> runways = new java.util.ArrayList<>();
                for (var r : data.runwaysNear(tower.getX(), tower.getZ(), TowerBlockEntity.AIRFIELD_M)) {
                    if (TowerBlockEntity.nearestTower(data, r.centre().x(), r.centre().z()).equals(tower)) { runways.add(r); }
                }
                PacketDistributor.sendToPlayer(p, new AirportNetwork.LightsPayload(tower, AirportNetwork.lights(fixtures, runways)));
                was = new Sent(data.version(), null);
                mine.put(tower, was);
            }
            float[] circuits = new float[AirfieldLighting.CIRCUITS];
            if (level.isLoaded(tower) && level.getBlockEntity(tower) instanceof TowerBlockEntity t) {
                double[] lit = t.intensities();
                for (int i = 0; i < circuits.length; i++) { circuits[i] = (float) lit[i]; }
            }
            if (was.circuits() == null || !Arrays.equals(was.circuits(), circuits)) {
                PacketDistributor.sendToPlayer(p, new AirportNetwork.CircuitsPayload(tower, circuits));
                mine.put(tower, new Sent(was.version(), circuits));
            }
        }
        mine.keySet().removeIf(t -> !towers.contains(t) && sendGone(p, t));
    }

    private static boolean sendGone(ServerPlayer p, BlockPos tower) {
        PacketDistributor.sendToPlayer(p, new AirportNetwork.LightsPayload(tower, AirportNetwork.lights(List.of(), List.of())));
        return true;
    }

    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) { SENT.remove(event.getEntity().getUUID()); }

    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { SENT.remove(event.getEntity().getUUID()); }

    /** A respawned client forgets its airfields (AtcControls): send them again. */
    @SubscribeEvent public static void respawned(PlayerEvent.PlayerRespawnEvent event) { SENT.remove(event.getEntity().getUUID()); }
}
