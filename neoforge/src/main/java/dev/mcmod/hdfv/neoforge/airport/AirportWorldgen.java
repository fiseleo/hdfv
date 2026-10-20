package dev.mcmod.hdfv.neoforge.airport;

import com.mojang.logging.LogUtils;
import dev.mcmod.aerocore.neoforge.probe.WorldAir;
import dev.mcmod.hdfv.airport.AirportLayout;
import dev.mcmod.hdfv.airport.AirportPlanner;
import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.airport.RunwayMarkings;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;

/**
 * World generation's side of airports (0.20), for AirportFeature:
 * <ul>
 *   <li>When the overworld loads the first time with airports, the region files it already has (512-block regions) are
 *   written down; no airport is planned over any of them (an old world gets airports only in land never visited, never
 *   half an airport against old chunks). Later loads use that list.</li>
 *   <li>A region's plan (AirportPlanner, from the seed alone and a terrain probe that reads the generator's noise:
 *   heights, biomes, where villages and outposts would start) is made once, by the first chunk that asks, the others
 *   waiting for it; plans the level already holds are used as they are. The regions round players are planned ahead
 *   on a thread of their own, so world generation seldom has to.</li>
 *   <li>A new plan is registered with the level (AirfieldData: its runway and lights, the tower, floodlights) on the
 *   server thread at the next tick, so the airfield works (control, ATIS, distant lights) before its far end has been
 *   generated.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID)
public final class AirportWorldgen {
    private AirportWorldgen() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TagKey<Biome> HAS_AIRPORT = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "has_airport"));
    private static final java.util.regex.Pattern REGION_FILE = java.util.regex.Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    /** What planning needs of a loaded overworld, taken on the server thread when it loads. */
    private record Context(long seed, double prevailingTowardRad, Set<Long> oldRegions, List<Holder<StructureSet>> avoid,
                           Map<Long, CompletableFuture<Optional<AirportPlanner.Plan>>> plans) {}

    private static final Map<ServerLevel, Context> CONTEXTS = new ConcurrentHashMap<>();
    private record Pending(ServerLevel level, AirportPlanner.Plan plan) {}
    private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    @SubscribeEvent public static void loaded(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) { return; }
        AirfieldData data = AirfieldData.of(level);
        if (data.oldRegions() == null) { data.setOldRegions(regionFiles(level)); data.setPlannerVersion(AirportPlanner.VERSION); }
        if (data.plannerVersion() < AirportPlanner.VERSION) {
            // 0.21: a newer planner may plan an airport where an older one planned none (nothing saved): the land generated
            // since is old to it
            Set<Long> old = new HashSet<>(data.oldRegions());
            old.addAll(regionFiles(level));
            data.setOldRegions(old);
            LOGGER.info("Airports: the planner is version {} now (the world's was {}); {} region files are old to it", AirportPlanner.VERSION, data.plannerVersion(), old.size());
            data.setPlannerVersion(AirportPlanner.VERSION);
        }
        List<Holder<StructureSet>> avoid = new ArrayList<>();
        var sets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        sets.getHolder(BuiltinStructureSets.VILLAGES).ifPresent(avoid::add);
        sets.getHolder(BuiltinStructureSets.PILLAGER_OUTPOSTS).ifPresent(avoid::add);
        Map<Long, CompletableFuture<Optional<AirportPlanner.Plan>>> plans = new ConcurrentHashMap<>();
        for (Map.Entry<Long, AirportPlanner.Plan> e : data.plans().entrySet()) { plans.put(e.getKey(), CompletableFuture.completedFuture(Optional.of(e.getValue()))); }
        long seed = level.getSeed();
        CONTEXTS.put(level, new Context(seed, WorldAir.prevailingTowardRad(seed, level.dimension()), Set.copyOf(data.oldRegions()), List.copyOf(avoid), plans));
        if (!data.oldRegions().isEmpty()) { LOGGER.info("Airports: {} region files were there before airports; none is planned over them", data.oldRegions().size()); }
    }

    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) { CONTEXTS.remove(level); PENDING.removeIf(p -> p.level() == level); }
    }

    /** The 512-block regions of a level's region files (packed as AirportPlanner.regionKey). */
    static Set<Long> regionFiles(ServerLevel level) {
        Path dir = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("region");
        Set<Long> out = new HashSet<>();
        if (!Files.isDirectory(dir)) { return out; }
        try (var files = Files.list(dir)) {
            files.forEach(f -> {
                Matcher m = REGION_FILE.matcher(f.getFileName().toString());
                try {
                    if (m.matches() && Files.size(f) > 0) { out.add(AirportPlanner.regionKey(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)))); }
                } catch (IOException ignored) { out.add(AirportPlanner.regionKey(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)))); }
            });
        } catch (IOException e) {
            LOGGER.warn("Airports: could not list {}; treating the world as old everywhere near spawn", dir, e);
            for (int x = -4; x < 4; x++) { for (int z = -4; z < 4; z++) { out.add(AirportPlanner.regionKey(x, z)); } }
        }
        return out;
    }

    /** The airport whose region holds a chunk, or null (planning it when no chunk asked before). */
    static AirportPlanner.Plan plan(WorldGenLevel level, ChunkGenerator generator, ChunkPos chunk) {
        ServerLevel server = level.getLevel();
        Context c = CONTEXTS.get(server);
        if (c == null) { return null; }
        int rx = AirportPlanner.regionOf(chunk.getMiddleBlockX()), rz = AirportPlanner.regionOf(chunk.getMiddleBlockZ());
        return plan(server, c, generator, rx, rz);
    }

    private static AirportPlanner.Plan plan(ServerLevel server, Context c, ChunkGenerator generator, int rx, int rz) {
        long key = AirportPlanner.regionKey(rx, rz);
        CompletableFuture<Optional<AirportPlanner.Plan>> f = c.plans().get(key);
        if (f == null) {
            CompletableFuture<Optional<AirportPlanner.Plan>> mine = new CompletableFuture<>();
            f = c.plans().putIfAbsent(key, mine);
            if (f == null) {
                f = mine;
                long t0 = System.nanoTime();
                try {
                    Probe probe = new Probe(generator, server.getChunkSource().randomState(), server, server.getChunkSource().getGeneratorState(), c);
                    AirportPlanner.Plan p = AirportPlanner.plan(c.seed(), rx, rz, c.prevailingTowardRad(), probe, HdfvConfig.airportTiers());
                    mine.complete(Optional.ofNullable(p));
                    if (p != null) {
                        PENDING.add(new Pending(server, p));
                        LOGGER.info("Airports: region {} {} has a {} one, runway {} m along {} at {} {}, surface y {} ({} samples, {} ms)", rx, rz, p.tier(), p.lengthM(), p.axis(),
                                p.runway().centre().x(), p.runway().centre().z(), p.surfaceY(), probe.samples(), (System.nanoTime() - t0) / 1_000_000);
                    } else {
                        LOGGER.debug("Airports: region {} {} has none ({} samples, {} ms)", rx, rz, probe.samples(), (System.nanoTime() - t0) / 1_000_000);
                    }
                } catch (RuntimeException e) {
                    mine.complete(Optional.empty());
                    throw e;
                }
            }
        }
        return f.join().orElse(null);
    }

    /** A region's plan if it has been planned already (null if not, or none). */
    public static AirportPlanner.Plan planned(ServerLevel level, int rx, int rz) {
        Context c = CONTEXTS.get(level);
        if (c == null) { return null; }
        CompletableFuture<Optional<AirportPlanner.Plan>> f = c.plans().get(AirportPlanner.regionKey(rx, rz));
        return f == null || !f.isDone() ? null : f.join().orElse(null);
    }

    /** The plan of a region, for commands (planning it if need be; null if none). */
    public static AirportPlanner.Plan planOf(ServerLevel level, int rx, int rz) {
        Context c = CONTEXTS.get(level);
        if (c == null) { return null; }
        return plan(level, c, level.getChunkSource().getGenerator(), rx, rz);
    }

    /** Why a region has (or has not) an airport: the planner's reasons, candidate by candidate (the dev command). */
    public static List<String> diagnose(ServerLevel level, int rx, int rz) {
        Context c = CONTEXTS.get(level);
        if (c == null) { return List.of("no airports in this level"); }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Probe probe = new Probe(generator, level.getChunkSource().randomState(), level, level.getChunkSource().getGeneratorState(), c);
        long t0 = System.nanoTime();
        List<String> out = new ArrayList<>(AirportPlanner.diagnose(c.seed(), rx, rz, c.prevailingTowardRad(), probe));
        out.add(probe.samples() + " columns sampled, " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        return out;
    }

    /** The planner's view of a column next to the generated chunk's (the dev command): surface, ground, biome; heightmaps. */
    public static String probeAt(ServerLevel level, int x, int z) {
        Context c = CONTEXTS.get(level);
        if (c == null) { return "no airports in this level"; }
        Probe probe = new Probe(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level, level.getChunkSource().getGeneratorState(), c);
        int s = probe.surfaceY(x, z), g = probe.groundY(x, z);
        String biome = probe.biome(x, g, z).unwrapKey().map(k -> k.location().toString()).orElse("?");
        var chunk = level.getChunk(x >> 4, z >> 4);
        int ws = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1, of = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
        String top = level.getBlockState(new BlockPos(x, ws - 1, z)).getBlock().getDescriptionId();
        return String.format("%d %d: planner surface %d ground %d %s (suits %b); generated surface %d floor %d, top %s", x, z, s, g, biome,
                probe.suitableBiome(x, z), ws, of, top);
    }

    /** The nearest airport to a point within so many regions round it, or null (planning the regions not planned yet). */
    public static AirportPlanner.Plan nearest(ServerLevel level, double x, double z, int regions) { return nearest(level, x, z, regions, true); }

    /**
     * The nearest airport, planning the regions not planned yet only if asked: a region's plan takes about a second, so
     * the server thread (a villager's trade) asks only for the ones planned already (by world generation or before).
     */
    public static AirportPlanner.Plan nearest(ServerLevel level, double x, double z, int regions, boolean plan) {
        int rx = AirportPlanner.regionOf((int) Math.floor(x)), rz = AirportPlanner.regionOf((int) Math.floor(z));
        AirportPlanner.Plan best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= regions; r++) {
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) { continue; }
                    AirportPlanner.Plan p = plan ? planOf(level, rx + i, rz + j) : planned(level, rx + i, rz + j);
                    if (p == null) { continue; }
                    var centre = p.runway().centre();
                    double d = Math.hypot(centre.x() - x, centre.z() - z);
                    if (d < bestD) { bestD = d; best = p; }
                }
            }
            // a region further out cannot be nearer than one found a ring before
            if (best != null && bestD < r * AirportPlanner.REGION) { break; }
        }
        return best;
    }

    /** Whether airports may be planned in a level (an overworld loaded with them). */
    public static boolean planning(ServerLevel level) { return CONTEXTS.containsKey(level); }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (Pending p; (p = PENDING.poll()) != null; ) {
            if (CONTEXTS.containsKey(p.level())) { register(p.level(), p.plan()); }
        }
        if (event.getServer().getTickCount() % AHEAD_TICKS == 0) { ahead(); }
    }

    /** How often (ticks) the regions round players are planned ahead. */
    private static final int AHEAD_TICKS = 100;
    /** The thread regions are planned ahead on: one, at low priority, beside world generation's. */
    private static final java.util.concurrent.ExecutorService AHEAD = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hdfv airport planner");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    /** Regions handed to that thread and not planned yet. */
    private static final Set<Long> QUEUED = ConcurrentHashMap.newKeySet();

    /**
     * Plans the regions round each player (the player's and the eight next to it) on a thread of their own. A region's
     * first chunk otherwise plans it on the world generation thread, which then makes no chunk for the plan's 0.3 to 5 s,
     * and the server waits for the chunks it needs (6 s behind once, flying into a new region): planned ahead, a region
     * a player reaches is usually ready.
     */
    private static void ahead() {
        if (!HdfvConfig.airports()) { return; }
        for (Map.Entry<ServerLevel, Context> e : CONTEXTS.entrySet()) {
            ServerLevel level = e.getKey();
            Context c = e.getValue();
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            if (generator instanceof net.minecraft.world.level.levelgen.FlatLevelSource && !HdfvConfig.airportsInFlatWorlds()) { continue; }
            for (var player : level.players()) {
                int rx = AirportPlanner.regionOf((int) Math.floor(player.getX())), rz = AirportPlanner.regionOf((int) Math.floor(player.getZ()));
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        int ax = rx + i, az = rz + j;
                        long key = AirportPlanner.regionKey(ax, az);
                        if (c.plans().containsKey(key) || !QUEUED.add(key)) { continue; }
                        AHEAD.execute(() -> {
                            try {
                                if (CONTEXTS.get(level) == c) { plan(level, c, generator, ax, az); }
                            } catch (RuntimeException ex) {
                                LOGGER.error("Airport planning failed for region {} {}; no airport there", ax, az, ex);
                            } finally {
                                QUEUED.remove(key);
                            }
                        });
                    }
                }
            }
        }
    }

    /** Writes a planned airport's runway, lights, tower and floodlights into the level's airfield data. */
    static void register(ServerLevel level, AirportPlanner.Plan p) {
        List<Fixture> lights = AirportLayout.fixtures(p);
        Map<Long, int[]> thresholds = new HashMap<>();
        for (Map.Entry<Long, RunwayMarkings.Mark> m : AirportLayout.marks(p).entrySet()) {
            if (m.getValue().paint() != RunwayMarkings.Paint.THRESHOLD) { continue; }
            int x = RunwayMarkings.keyX(m.getKey()), z = RunwayMarkings.keyZ(m.getKey());
            thresholds.put(BlockPos.asLong(x, p.surfaceY() - 1, z), new int[]{m.getValue().dx(), m.getValue().dz()});
        }
        AirportLayout.TowerSite t = AirportLayout.tower(p);
        List<BlockPos> floods = new ArrayList<>();
        for (AirportLayout.Thing th : AirportLayout.things(p)) {
            if (th.kind() == AirportLayout.ThingKind.FLOODLIGHT) { floods.add(new BlockPos(th.x(), th.y(), th.z())); }
        }
        AirfieldData.of(level).addPlan(p, lights, thresholds, t == null ? null : new BlockPos(t.x(), t.y(), t.z()), floods);
    }

    /**
     * The generator's view of the ground: heights and biomes from the noise (pure functions, safe on any thread, the
     * same as the chunks will be but for trees and such, which the feature clears), and what is in the way: old region
     * files, villages and pillager outposts whose start (their placement's chunk, in a biome they take) is near.
     */
    static final class Probe implements AirportPlanner.TerrainProbe {
        private final ChunkGenerator generator;
        private final RandomState random;
        private final LevelHeightAccessor heights;
        private final ChunkGeneratorStructureState structures;
        private final Context context;
        private final Map<Long, int[]> columns = new HashMap<>();
        private int samples;
        /** A noise generator's initial density (no caves, no jaggedness), its range and sea level; null for other generators. */
        private final net.minecraft.world.level.levelgen.DensityFunction initial;
        private final int minY, maxY, seaLevel;

        Probe(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights, ChunkGeneratorStructureState structures, Context context) {
            this.generator = generator;
            this.random = random;
            this.heights = heights;
            this.structures = structures;
            this.context = context;
            if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noise) {
                var settings = noise.generatorSettings().value().noiseSettings();
                initial = random.router().initialDensityWithoutJaggedness();
                minY = Math.max(settings.minY(), heights.getMinBuildHeight());
                maxY = Math.min(settings.minY() + settings.height(), heights.getMaxBuildHeight());
                seaLevel = generator.getSeaLevel();
            } else {
                initial = null;
                minY = maxY = seaLevel = 0;
            }
        }

        /** Vanilla's preliminary surface (NoiseChunk.computePreliminarySurfaceLevel) refined to the metre: the first air above. */
        private int preliminary(int x, int z) {
            int l = maxY - Math.floorMod(maxY - minY, 8);
            while (l >= minY && initial.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, l, z)) <= 0.390625) { l -= 8; }
            if (l < minY) { return minY; }
            for (int k = Math.min(l + 7, maxY - 1); k > l; k--) {
                if (initial.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, k, z)) > 0.390625) { return k + 1; }
            }
            return l + 1;
        }

        int samples() { return samples; }

        /** A region's planning is bounded: about 30 000 columns (a few seconds of noise on a world generation thread). */
        @Override public boolean exhausted() { return samples > 30_000; }

        /** (surface, ground) of a column: the generator's own noise column (about 2 ms: it builds the column). */
        private int[] column(int x, int z) {
            return columns.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> {
                samples++;
                // one noise column for both: the first block that is not air (water counts), the first that blocks motion
                var column = generator.getBaseColumn(x, z, heights, random);
                int top = heights.getMaxBuildHeight() - 1, bottom = heights.getMinBuildHeight(), surface = bottom, ground = bottom;
                for (int y = top; y >= bottom; y--) {
                    var s = column.getBlock(y);
                    if (surface == bottom && !s.isAir()) { surface = y + 1; }
                    if (s.blocksMotion()) { ground = y + 1; break; }
                }
                return new int[]{Math.max(surface, ground), ground};
            });
        }

        private final Map<Long, Integer> rough = new HashMap<>();

        /** The quick look's ground: the preliminary surface (it reads up to about 14 m low against the generated ground). */
        @Override public int roughGroundY(int x, int z) {
            if (initial == null) { return groundY(x, z); }
            return rough.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> preliminary(x, z));
        }

        @Override public int roughSurfaceY(int x, int z) { return initial == null ? surfaceY(x, z) : Math.max(roughGroundY(x, z), seaLevel); }

        @Override public int surfaceY(int x, int z) { return column(x, z)[0]; }

        @Override public int groundY(int x, int z) { return column(x, z)[1]; }

        private Holder<Biome> biome(int x, int y, int z) {
            return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler());
        }

        @Override public boolean suitableBiome(int x, int z) { return biome(x, groundY(x, z), z).is(HAS_AIRPORT); }

        @Override public boolean blocked(int minX, int minZ, int maxX, int maxZ) {
            for (int rx = minX >> 9; rx <= maxX >> 9; rx++) {
                for (int rz = minZ >> 9; rz <= maxZ >> 9; rz++) {
                    if (context.oldRegions().contains(AirportPlanner.regionKey(rx, rz))) { return true; }
                }
            }
            return false;
        }

        @Override public List<int[]> settlements(int minX, int minZ, int maxX, int maxZ) {
            List<int[]> out = new ArrayList<>();
            int cx0 = minX >> 4, cz0 = minZ >> 4, cx1 = maxX >> 4, cz1 = maxZ >> 4;
            for (Holder<StructureSet> set : context.avoid()) {
                if (!(set.value().placement() instanceof RandomSpreadStructurePlacement p)) { continue; }
                int spacing = p.spacing();
                for (int gx = Math.floorDiv(cx0, spacing); gx <= Math.floorDiv(cx1, spacing); gx++) {
                    for (int gz = Math.floorDiv(cz0, spacing); gz <= Math.floorDiv(cz1, spacing); gz++) {
                        ChunkPos c = p.getPotentialStructureChunk(context.seed(), gx * spacing, gz * spacing);
                        if (c.x < cx0 || c.x > cx1 || c.z < cz0 || c.z > cz1 || !p.isStructureChunk(structures, c.x, c.z)) { continue; }
                        int bx = c.getMiddleBlockX(), bz = c.getMiddleBlockZ();
                        Holder<Biome> b = biome(bx, groundY(bx, bz), bz);
                        for (StructureSet.StructureSelectionEntry e : set.value().structures()) {
                            if (e.structure().value().biomes().contains(b)) { out.add(new int[]{bx, bz}); break; }
                        }
                    }
                }
            }
            return out;
        }
    }
}
