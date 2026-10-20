package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.aerocore.api.AirData;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.probe.WorldAir;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleTracks;
import dev.mcmod.hdfv.airport.AircraftKind;
import dev.mcmod.hdfv.airport.AirfieldLighting;
import dev.mcmod.hdfv.airport.Atc;
import dev.mcmod.hdfv.airport.Atis;
import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.airport.Runway;
import dev.mcmod.hdfv.airport.RunwayChoice;
import dev.mcmod.hdfv.airport.TowerRules;
import dev.mcmod.hdfv.airport.WakeCategory;
import dev.mcmod.hdfv.neoforge.multiblock.ControllerBlockEntity;
import dev.mcmod.hdfv.neoforge.multiblock.Screened;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A control tower (0.20): the controller of the tower multiblock, its base's duty desk.
 * <ul>
 *   <li>Power: the consoles and radios {@link TowerRules#CONSOLES_W}, the radar {@link TowerRules#RADAR_W} when it has
 *   one, and the airfield lights it switches (TowerRules.lightingW), all from its energy buffer (cables, batteries).
 *   Unpowered (or not formed) it gives only the ATIS; lights and radar are dark.</li>
 *   <li>Its airfield: the runways and light fittings nearer to it than to another tower, within
 *   {@link #AIRFIELD_M}.</li>
 *   <li>The ATIS every 5 s (the wind at 10 m over the runway, the highest 3 s gust in 10 minutes, visibility,
 *   temperature, QNH), the runway in use by RunwayChoice.</li>
 *   <li>The traffic rules (core Atc) twice a second while powered, with what it sees: the vehicles within its radar
 *   range (or, without a radar, what the cab sees), whether a controller villager is on duty (awake, this desk its job
 *   site, within 8 blocks of it) or a player has taken control at its screen.</li>
 *   <li>Its instructions and warnings go to the pilots (AirportNetwork); its view to the players at its screen.</li>
 * </ul>
 */
public final class TowerBlockEntity extends ControllerBlockEntity implements Screened {
    /** How far a tower's airfield reaches (m). */
    public static final double AIRFIELD_M = 2_500;
    /** How far the radios reach for a pilot's call without radar (m, at least). */
    public static final double RADIO_M = 3_000;
    /**
     * How near its runways must be (m, to the runway's nearest point; 0.21): a towerless small airfield a couple of
     * kilometres off is not this tower's.
     */
    public static final double RUNWAY_M = 1_000;
    /** An interior lamp's draw (W, 0.21) and the cab's light (a vanilla light block's level). */
    public static final double LAMP_W = 20;
    public static final int CAB_LIGHT = 12;

    private final int[] steps = {3, 3, 3, 3, 5, 5};
    private final boolean[] auto = {true, true, true, true, true, true};
    private final Atc atc = new Atc();
    private RunwayChoice.State runway;
    private Atis atis;
    private double peakGust;
    private boolean powered, manned, shownPowered;
    /** The interior lamps found inside it (0.21) and where it put the cab's light (null: none). */
    private int interiorLamps;
    /** Whether what is inside it has been looked at since it was loaded (repairInside). */
    private boolean insideChecked;
    private BlockPos cabLight;
    private UUID holder;
    private long clock;
    private Atc.Result result = new Atc.Result(List.of(), List.of(), List.of());
    private List<VehicleTracks.Track> tracks = List.of();
    private final Deque<String> log = new ArrayDeque<>();
    private final Map<UUID, Atc.Request> requests = new LinkedHashMap<>();
    private final List<Atc.Command> commands = new ArrayList<>();
    private final Map<UUID, Boolean> seen = new HashMap<>();
    private final Map<UUID, Long> told = new HashMap<>();
    private double pilotLightsUntilS = Double.NEGATIVE_INFINITY;
    private final Map<UUID, Deque<Double>> clicks = new HashMap<>();
    private static final Map<VehicleBlueprint, AircraftKind> KINDS = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public TowerBlockEntity(BlockPos pos, BlockState s) {
        super(HdfvAirports.TOWER_ENTITY.get(), pos, s, Structures.TOWER_PATTERN, TowerRules.CONSOLES_W + TowerRules.RADAR_W + 30_000);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            AirfieldData.of(server).addTower(worldPosition);
            // a generated tower comes with batteries and cable: networks found before its chunk loaded did not have it
            dev.mcmod.hdfv.neoforge.power.EnergyNetworks.changed(server);
        }
    }

    @Override protected void broken(ServerLevel level) { AirfieldData.of(level).removeTower(worldPosition); cabLight(level, null); }

    // ---------------------------------------------------------------- what it is

    public boolean formed() { return multiblock.formed(); }

    public boolean powered() { return powered; }

    /** The power it drew last tick (W). */
    public double lastPowerW() { return lastW; }

    public boolean manned() { return manned; }

    public Atis atis() { return atis; }

    public Runway.Use inUse() { return runway == null ? null : runway.use(); }

    public Atc.Result result() { return result; }

    public List<VehicleTracks.Track> tracks() { return tracks; }

    public List<String> log() { return List.copyOf(log); }

    /** The formed pattern's heights, or null. */
    public Structures.TowerHeights heights() {
        Pattern.Match m = multiblock.match();
        return m == null ? null : Structures.towerHeights(m);
    }

    public boolean hasRadar() { Pattern.Match m = multiblock.match(); return m != null && m.has(Structures.TOWER_RADAR); }

    /** How far it controls (m): its radar's range, or what the cab sees. */
    public double zoneM() {
        Structures.TowerHeights h = heights();
        if (h == null) { return 0; }
        double visibility = level == null ? WorldAir.CLEAR_M : WorldAir.visibilityM(level);
        return hasRadar() ? TowerRules.radarRangeM(h.antennaM()) : TowerRules.visualRangeM(h.eyeM(), visibility);
    }

    /** The runways of its airfield. */
    public List<Runway> runways(ServerLevel server) {
        List<Runway> out = new ArrayList<>();
        AirfieldData data = AirfieldData.of(server);
        for (Runway r : data.runwaysNear(worldPosition.getX(), worldPosition.getZ(), AIRFIELD_M)) {
            if (r.distanceTo(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5) <= RUNWAY_M
                    && nearestTower(data, r.centre().x(), r.centre().z()).equals(worldPosition)) { out.add(r); }
        }
        return out;
    }

    /** The light fittings of its airfield (nearer to it than to any other tower). */
    public List<Fixture> fixtures(ServerLevel server) {
        AirfieldData data = AirfieldData.of(server);
        List<Fixture> out = new ArrayList<>();
        for (Fixture f : data.fixturesNear(worldPosition.getX(), worldPosition.getZ(), AIRFIELD_M)) {
            if (nearestTower(data, f.x(), f.z()).equals(worldPosition)) { out.add(f); }
        }
        return out;
    }

    static BlockPos nearestTower(AirfieldData data, double x, double z) {
        BlockPos best = null;
        double d = Double.MAX_VALUE;
        for (BlockPos t : data.towers()) {
            double e = Math.hypot(t.getX() - x, t.getZ() - z);
            if (e < d) { d = e; best = t; }
        }
        return best == null ? BlockPos.ZERO : best;
    }

    // ---------------------------------------------------------------- lights

    /** A circuit's step (0 off to 5 full) and whether it switches itself (on in the dark or poor visibility). */
    public int step(int circuit) { return steps[circuit]; }

    public boolean auto(int circuit) { return auto[circuit]; }

    /** The share of full intensity each circuit shines at now (0 when the tower is unpowered). */
    public double[] intensities() {
        double[] out = new double[AirfieldLighting.CIRCUITS];
        if (!powered || level == null) { return out; }
        boolean dark = level.getSkyDarken() >= 4 || WorldAir.visibilityM(level) < 5_000;
        boolean pilots = !manned && holder == null && pilotLightsUntilS > level.getGameTime() * 0.05;
        for (int i = 0; i < out.length; i++) {
            boolean on = auto[i] ? dark || pilots : steps[i] > 0;
            out[i] = on ? AirfieldLighting.STEPS[auto[i] && steps[i] == 0 ? 3 : steps[i]] : 0;
        }
        return out;
    }

    // ---------------------------------------------------------------- the tick

    @Override protected void work(ServerLevel server, boolean formed) {
        clock++;
        if (!formed) {
            powered = false;
            if (clock % 100 == 0) { updateAtis(server); }
            if (clock % 40 == 0) { cabLight(server, null); }
            return;
        }
        if (clock % 20 == 0) { manned = onDuty(server); checkHolder(server); }
        double watts = TowerRules.CONSOLES_W + (hasRadar() ? TowerRules.RADAR_W : 0) + TowerRules.lightingW(fixtures(server), litFor(server)) + interiorLamps * LAMP_W;
        powered = draw(watts) > 0.99;
        if (powered != shownPowered) { shownPowered = powered; sendView(); }
        if (clock % 100 == 0 || atis == null) { updateAtis(server); }
        if (powered && clock % 10 == 0) { control(server); }
        if (clock % 40 == 0) { floodlights(server); inside(server); }
        if (clock % 10 == 0) { AirportNetwork.sendView(server, this); }
    }

    /** The intensities the lights draw power at (as if powered: what they would draw). */
    private double[] litFor(ServerLevel server) {
        boolean was = powered;
        powered = true;
        double[] out = intensities();
        powered = was;
        return out;
    }

    private boolean onDuty(ServerLevel server) {
        GlobalPos desk = GlobalPos.of(server.dimension(), worldPosition);
        for (Villager v : server.getEntitiesOfClass(Villager.class, new AABB(worldPosition).inflate(8))) {
            if (!v.isAlive() || v.isSleeping() || v.isBaby() || v.getVillagerData().getProfession() != dev.mcmod.hdfv.neoforge.villager.HdfvVillagers.TOWER_CONTROLLER.get()) { continue; }
            if (v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(desk::equals).orElse(false)) { return true; }
        }
        return false;
    }

    private void updateAtis(ServerLevel server) {
        List<Runway> rws = runways(server);
        Vec3d at = rws.isEmpty() ? new Vec3d(worldPosition.getX() + 0.5, worldPosition.getY() + 10, worldPosition.getZ() + 0.5)
                : rws.get(0).centre().add(new Vec3d(0, 10, 0));
        WorldAir.Air air = WorldAir.at(server, at.x(), at.y(), at.z());
        if (clock % 600 == 0 || atis == null) { peakGust = WorldAir.peakGustMps(server, at.x(), at.y(), at.z(), 600, 3); }
        double t = server.getGameTime() * 0.05;
        runway = RunwayChoice.next(runway, rws, air.meanWind(), t);
        double wind = Math.hypot(air.meanWind().x(), air.meanWind().z());
        Atis now = Atis.of('A', runway == null ? "" : runway.use().designator(), AirData.bearingFromDeg(air.meanWind()), wind, peakGust,
                air.visibilityM(), server.isRaining(), server.isThundering(), air.temperatureK(), AirData.qnhPa(air.pressurePa(), air.altitudeM()));
        Atis next = Atis.update(atis, now);
        if (next != atis && atis != null && next.letter() != atis.letter()) { say("atis " + next.letter()); }
        atis = next;
        setChanged();
    }

    private void control(ServerLevel server) {
        Structures.TowerHeights h = heights();
        if (h == null) { return; }
        double zone = zoneM(), visual = TowerRules.visualRangeM(h.eyeM(), WorldAir.visibilityM(server));
        // an aircraft is the nearest tower's (two towers' zones overlap: both talked to the same pilot, each with its own ATIS)
        AirfieldData data = AirfieldData.of(server);
        tracks = VehicleTracks.near(server, new Vec3d(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5), zone).stream()
                .filter(t -> nearestTower(data, t.position().x(), t.position().z()).equals(worldPosition)).toList();
        Vec3 eye = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + h.eyeM(), worldPosition.getZ() + 0.5);
        List<Atc.Traffic> traffic = new ArrayList<>();
        for (VehicleTracks.Track t : tracks) {
            AircraftKind kind = KINDS.computeIfAbsent(t.blueprint(), AircraftKind::of);
            double height = Double.isNaN(t.heightAboveGroundM()) ? 100 : t.heightAboveGroundM();
            boolean ground = t.touching() || height <= 3.5 && Math.abs(t.velocity().y()) < 2;
            boolean sees = seen.computeIfAbsent(t.id(), k -> true);
            if (clock % 40 == 0) {
                Vec3 to = new Vec3(t.position().x(), t.position().y() + 0.5, t.position().z());
                sees = to.distanceTo(eye) <= visual && server.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
                seen.put(t.id(), sees);
            }
            traffic.add(new Atc.Traffic(t.id(), t.blueprint().name(), kind, WakeCategory.of(t.massKg()), t.position(), t.velocity(), height, ground, sees));
        }
        Map<UUID, Atc.Request> asked = new LinkedHashMap<>(requests);
        requests.clear();
        List<Atc.Command> ordered = new ArrayList<>(commands);
        commands.clear();
        Vec3d wind = WorldAir.at(server, worldPosition.getX() + 0.5, worldPosition.getY() + 10, worldPosition.getZ() + 0.5).meanWind();
        result = atc.step(new Atc.Snapshot(server.getGameTime() * 0.05, inUse(), runways(server), manned, holder != null, wind, traffic), asked, ordered);
        for (Atc.Transmission x : result.transmissions()) {
            AirportNetwork.tell(server, this, x, AirportNetwork.Said.NEWS);
            told.put(x.id(), clock);
            say(describe(x));
        }
        // a heartbeat every 5 s to everyone the tower controls, so a pilot's display stays current and clears when out of reach
        for (Atc.Flight f : result.flights()) {
            if (f.kind().flies() && clock - told.getOrDefault(f.id(), Long.MIN_VALUE / 2) >= 100) {
                AirportNetwork.tell(server, this, new Atc.Transmission(f.id(), f.instruction(), f.runway(), f.sequence(), null), AirportNetwork.Said.REMINDER);
                told.put(f.id(), clock);
            }
        }
        seen.keySet().retainAll(tracks.stream().map(VehicleTracks.Track::id).toList());
    }

    private void say(String line) {
        log.addFirst(String.format(java.util.Locale.ROOT, "%05d %s", (int) (clock / 20 % 100000), line));
        while (log.size() > 12) { log.removeLast(); }
    }

    private String describe(Atc.Transmission x) {
        String who = tracks.stream().filter(t -> t.id().equals(x.id())).map(t -> t.blueprint().name()).findFirst().orElse(x.id().toString().substring(0, 8));
        return who + " " + (x.warning() != null ? "! " + x.warning().name().toLowerCase(java.util.Locale.ROOT)
                : x.instruction().name().toLowerCase(java.util.Locale.ROOT) + (x.sequence() > 0 ? " #" + x.sequence() : "") + " " + x.runway());
    }

    /**
     * Its interior lamps (0.21: in the base, two blocks high and 5 x 5 inside, and up the shaft's 3 x 3 inside) lit while
     * it has power; and the cab's light: a vanilla light block in the upper glass course's middle (a cell the pattern
     * leaves open), there while it has power (the cab cannot hold a lamp of its own: the pattern wants it empty).
     */
    private void inside(ServerLevel server) {
        Pattern.Match m = multiblock.match();
        if (m == null) { interiorLamps = 0; cabLight(server, null); return; }
        dev.mcmod.hdfv.airport.AirportLayout.TowerSite site = new dev.mcmod.hdfv.airport.AirportLayout.TowerSite(worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), m.facing(), m.repeats());
        if (!insideChecked) { insideChecked = repairInside(server, site); }
        int lamps = 0;
        for (int layer = 0; layer <= 1 + m.repeats(); layer++) {
            boolean base = layer < 2;
            for (int row = base ? 1 : 2; row <= (base ? 5 : 4); row++) {
                for (int col = base ? -2 : -1; col <= (base ? 2 : 1); col++) {
                    int[] w = dev.mcmod.hdfv.airport.AirportLayout.inside(site, row, col);
                    BlockPos p = new BlockPos(w[0], site.y() + layer, w[1]);
                    if (!server.isLoaded(p)) { continue; }
                    BlockState s = server.getBlockState(p);
                    if (!(s.getBlock() instanceof AirfieldBlocks.InteriorLamp)) { continue; }
                    lamps++;
                    if (s.getValue(AirfieldBlocks.InteriorLamp.LIT) != powered) {
                        server.setBlock(p, s.setValue(AirfieldBlocks.InteriorLamp.LIT, powered), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        interiorLamps = lamps;
        int[] mid = dev.mcmod.hdfv.airport.AirportLayout.inside(site, 3, 0);
        cabLight(server, powered ? new BlockPos(mid[0], site.y() + 2 + m.repeats() + 2, mid[1]) : null);
    }

    /**
     * Puts right what 0.21.0 generated inside it, once a load. Its stair's steps faced the way out of their cells, so a
     * corner's step lay across the way in (a whole block's rise), and the grating over the fourth step from the top met
     * the head of anyone on the steps: it could be neither climbed nor come down (AirportLayout.STAIR_RING). Its bed's
     * head was boxed in, so the controller never claimed it (AirportLayout.towerInside). Only steel steps where the layout
     * has them are turned, the grating taken away only where the top step is one, and only a bed lying the old way turned
     * round (not while someone sleeps in it). False while a cell of it is not loaded or the bed is in use (it tries again).
     */
    private boolean repairInside(ServerLevel server, dev.mcmod.hdfv.airport.AirportLayout.TowerSite site) {
        int top = 2 + site.courses();
        BlockPos[] steps = new BlockPos[top + 1];
        for (int k = 0; k <= top; k++) {
            int[] c = dev.mcmod.hdfv.airport.AirportLayout.STAIR_RING[dev.mcmod.hdfv.airport.AirportLayout.stairCell(site.courses(), k)];
            int[] w = dev.mcmod.hdfv.airport.AirportLayout.inside(site, c[0], c[1]);
            steps[k] = new BlockPos(w[0], site.y() + k, w[1]);
            if (!server.isLoaded(steps[k])) { return false; }
        }
        int fixed = 0;
        for (int k = 0; k <= top; k++) {
            BlockState s = server.getBlockState(steps[k]);
            if (!s.is(HdfvAirports.STEEL_STAIRS.get())) { continue; }
            net.minecraft.core.Direction want = net.minecraft.core.Direction.from2DDataValue(
                    dev.mcmod.hdfv.airport.AirportLayout.stairFacing(site, dev.mcmod.hdfv.airport.AirportLayout.stairCell(site.courses(), k)));
            if (s.getValue(net.minecraft.world.level.block.StairBlock.FACING) != want) {
                server.setBlock(steps[k], s.setValue(net.minecraft.world.level.block.StairBlock.FACING, want)
                        .setValue(net.minecraft.world.level.block.StairBlock.SHAPE, net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                fixed++;
            }
        }
        BlockPos over = steps[top - 3].atY(site.y() + top);
        if (server.getBlockState(steps[top]).is(HdfvAirports.STEEL_STAIRS.get()) && server.isLoaded(over) && server.getBlockState(over).is(HdfvAirports.STEEL_GRATING.get())) {
            server.setBlock(over, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            fixed++;
        }
        // the bed: 0.21.0's foot at row 3 and head at row 4 (column -2), now the other way round
        int[] foot = dev.mcmod.hdfv.airport.AirportLayout.inside(site, 3, -2), head = dev.mcmod.hdfv.airport.AirportLayout.inside(site, 4, -2);
        BlockPos oldFoot = new BlockPos(foot[0], site.y(), foot[1]), oldHead = new BlockPos(head[0], site.y(), head[1]);
        if (!server.isLoaded(oldFoot) || !server.isLoaded(oldHead)) { return false; }
        BlockState f = server.getBlockState(oldFoot), hd = server.getBlockState(oldHead);
        net.minecraft.core.Direction back = net.minecraft.core.Direction.from2DDataValue(dev.mcmod.hdfv.airport.AirportLayout.facing(site, 1, 0));
        if (f.getBlock() instanceof net.minecraft.world.level.block.BedBlock && hd.is(f.getBlock())
                && f.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT
                && hd.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
                && f.getValue(net.minecraft.world.level.block.BedBlock.FACING) == back) {
            if (f.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED) || hd.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) { return false; }
            BlockState bed = f.getBlock().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, back.getOpposite());
            // both halves at once, without their neighbours' updates (a bed half without its other half breaks)
            int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
            server.setBlock(oldHead, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), flags);
            server.setBlock(oldFoot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), flags);
            fixed += 2;
        }
        if (fixed > 0) { com.mojang.logging.LogUtils.getLogger().info("Tower at {}: what 0.21.0 generated inside it put right ({} blocks)", worldPosition.toShortString(), fixed); }
        return true;
    }

    /** Puts the cab's light at a place (null: takes it away), only into air and only taking a light block it put. */
    private void cabLight(ServerLevel server, BlockPos at) {
        if (cabLight != null && !cabLight.equals(at) && server.isLoaded(cabLight) && server.getBlockState(cabLight).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
            server.setBlock(cabLight, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            cabLight = null;
            setChanged();
        }
        if (at != null && !at.equals(cabLight) && server.isLoaded(at) && server.getBlockState(at).isAir()) {
            server.setBlock(at, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, CAB_LIGHT),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            cabLight = at;
            setChanged();
        }
    }

    private void floodlights(ServerLevel server) {
        boolean on = intensities()[AirfieldLighting.TAXIWAY] > 0;
        int changed = 0;
        for (BlockPos p : AirfieldData.of(server).floodlightsNear(worldPosition.getX(), worldPosition.getZ(), AIRFIELD_M)) {
            if (changed >= 16 || !server.isLoaded(p)) { continue; }
            BlockState s = server.getBlockState(p);
            if (s.getBlock() instanceof AirfieldBlocks.Floodlight && s.getValue(AirfieldBlocks.Floodlight.LIT) != on
                    && nearestTower(AirfieldData.of(server), p.getX(), p.getZ()).equals(worldPosition)) {
                server.setBlock(p, s.setValue(AirfieldBlocks.Floodlight.LIT, on), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                changed++;
            }
        }
    }

    // ---------------------------------------------------------------- requests and the screen

    /** A pilot's call (AirportNetwork has checked the pilot); with nobody in the tower, three calls in 5 s turn the lights on for 15 min. */
    void request(UUID vehicle, Atc.Request r) {
        requests.put(vehicle, r);
        if (r != Atc.Request.CALL || level == null) { return; }
        double now = level.getGameTime() * 0.05;
        Deque<Double> c = clicks.computeIfAbsent(vehicle, k -> new ArrayDeque<>());
        c.addLast(now);
        while (!c.isEmpty() && now - c.peekFirst() > 5) { c.removeFirst(); }
        if (c.size() >= 3 && !manned && holder == null) { pilotLightsUntilS = now + 900; c.clear(); say("pilot lights"); }
    }

    /** A player's command at the screen (AirportNetwork has checked the menu). */
    Component command(ServerPlayer player, AirportNetwork.Command c) {
        switch (c.kind()) {
            case TAKE -> {
                if (holder != null && !holder.equals(player.getUUID())) { return Component.translatable("hdfv.tower.taken"); }
                holder = player.getUUID();
                say(player.getGameProfile().getName() + " takes control");
            }
            case RELEASE -> { if (player.getUUID().equals(holder)) { holder = null; say("control released"); } }
            case INSTRUCT -> {
                if (!player.getUUID().equals(holder)) { return Component.translatable("hdfv.tower.not_in_control"); }
                Atc.Instruction[] all = Atc.Instruction.values();
                commands.add(new Atc.Command(c.target(), all[Math.floorMod(c.value(), all.length)]));
            }
            case CIRCUIT_STEP -> { steps[Math.floorMod(c.target().getLeastSignificantBits(), steps.length)] = Math.clamp(c.value(), 0, 5); setChanged(); }
            case CIRCUIT_AUTO -> { auto[Math.floorMod(c.target().getLeastSignificantBits(), auto.length)] = c.value() != 0; setChanged(); }
            case PAINT -> { return paintRunways(player); }
        }
        return null;
    }

    /** The player in control, or null. */
    public UUID holder() { return holder; }

    private void checkHolder(ServerLevel server) {
        if (holder == null) { return; }
        ServerPlayer p = server.getServer().getPlayerList().getPlayer(holder);
        if (p == null || !(p.containerMenu instanceof TowerMenu m) || !m.pos.equals(worldPosition) || !m.stillValid(p)) { holder = null; say("control released"); }
    }

    /** Paints the runways of its airfield where they are pavement already (the markings of RunwayMarkings). */
    private Component paintRunways(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) { return null; }
        int painted = 0;
        for (Runway r : runways(server)) {
            for (Map.Entry<Long, dev.mcmod.hdfv.airport.RunwayMarkings.Mark> e : dev.mcmod.hdfv.airport.RunwayMarkings.of(r).entrySet()) {
                int x = dev.mcmod.hdfv.airport.RunwayMarkings.keyX(e.getKey()), z = dev.mcmod.hdfv.airport.RunwayMarkings.keyZ(e.getKey());
                BlockPos p = new BlockPos(x, (int) Math.round(r.y()) - 1, z);
                if (!server.isLoaded(p)) { continue; }
                BlockState s = server.getBlockState(p);
                if (s.getBlock() instanceof MarkedSurfaceBlock m) {
                    BlockState next = m.marked(e.getValue());
                    if (next != s) { server.setBlock(p, next, net.minecraft.world.level.block.Block.UPDATE_CLIENTS); painted++; }
                }
            }
        }
        return Component.translatable("hdfv.tower.painted", painted);
    }

    @Override public void openScreen(ServerPlayer player) { TowerMenu.open(player, this); }

    /** What the clients near it see (the renderer: the radar turns while powered). */
    @Override public CompoundTag view() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Powered", powered);
        return t;
    }

    @Override public Component status() {
        if (!formed()) { return Component.translatable("hdfv.tower.status.unformed"); }
        return Component.translatable(powered ? (manned ? "hdfv.tower.status.manned" : "hdfv.tower.status.unmanned") : "hdfv.tower.status.unpowered");
    }

    @Override protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("Steps", steps);
        int a = 0;
        for (int i = 0; i < auto.length; i++) { if (auto[i]) { a |= 1 << i; } }
        tag.putInt("Auto", a);
        if (atis != null) { tag.putString("AtisLetter", String.valueOf(atis.letter())); }
        if (cabLight != null) { tag.putLong("CabLight", cabLight.asLong()); }
    }

    @Override protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        int[] s = tag.getIntArray("Steps");
        for (int i = 0; i < Math.min(s.length, steps.length); i++) { steps[i] = Math.clamp(s[i], 0, 5); }
        if (tag.contains("Auto")) { int a = tag.getInt("Auto"); for (int i = 0; i < auto.length; i++) { auto[i] = (a & (1 << i)) != 0; } }
        cabLight = tag.contains("CabLight") ? BlockPos.of(tag.getLong("CabLight")) : null;
    }

    /** The vehicle entity for an id (server), or null. */
    static PartVehicle vehicle(ServerLevel server, UUID id) {
        Entity e = server.getEntity(id);
        return e instanceof PartVehicle v ? v : null;
    }
}
