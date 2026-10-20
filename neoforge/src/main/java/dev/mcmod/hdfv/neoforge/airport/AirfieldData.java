package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.hdfv.airport.AirportPlanner;
import dev.mcmod.hdfv.airport.AirportTier;
import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.airport.Runway;
import dev.mcmod.hdfv.airport.RunwayFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A level's airfields (0.20): every light fitting and threshold-painted block placed (each registers itself when
 * placed and goes when removed), the towers, and what world generation planned (whole airports are registered at once,
 * so their lights and runways work before their far ends are generated). Runways are found from the threshold blocks
 * (RunwayFinder) whenever they change. A version counts changes, for clients' copies of the lights.
 * <p>
 * Also the set of the world's region files that existed before airports could generate (taken once, when the world
 * first loads with this version): airports are only planned where none of them lies, so old worlds never get half an
 * airport at the edge of explored land.
 */
public final class AirfieldData extends SavedData {
    private static final String NAME = "hdfv_airfields";
    private static final Factory<AirfieldData> FACTORY = new Factory<>(AirfieldData::new, AirfieldData::load, null);

    private final Map<Long, Fixture.Kind> fixtures = new LinkedHashMap<>();
    private final Map<Long, int[]> thresholds = new HashMap<>();
    private final Set<Long> towers = new HashSet<>();
    private final Map<Long, AirportPlanner.Plan> planned = new LinkedHashMap<>();
    private Set<Long> oldRegions;
    private int plannerVersion;
    private int version;
    private List<Runway> runways;

    public static AirfieldData of(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, NAME); }

    public int version() { return version; }

    private void changed() { version++; runways = null; setDirty(); }

    public void addFixture(BlockPos pos, Fixture.Kind kind) {
        if (fixtures.put(pos.asLong(), kind) != kind) { changed(); }
    }

    public void removeFixture(BlockPos pos) { if (fixtures.remove(pos.asLong()) != null) { changed(); } }

    public void addThreshold(BlockPos pos, int dx, int dz) {
        int[] old = thresholds.put(pos.asLong(), new int[]{dx, dz});
        if (old == null || old[0] != dx || old[1] != dz) { changed(); }
    }

    public void removeThreshold(BlockPos pos) { if (thresholds.remove(pos.asLong()) != null) { changed(); } }

    private final Set<Long> floodlights = new HashSet<>();

    public void addFloodlight(BlockPos pos) { if (floodlights.add(pos.asLong())) { setDirty(); } }

    public void removeFloodlight(BlockPos pos) { if (floodlights.remove(pos.asLong())) { setDirty(); } }

    /** The floodlights within a horizontal distance of a point. */
    public List<BlockPos> floodlightsNear(double x, double z, double radius) {
        List<BlockPos> out = new ArrayList<>();
        for (long f : floodlights) {
            BlockPos p = BlockPos.of(f);
            if (Math.hypot(p.getX() + 0.5 - x, p.getZ() + 0.5 - z) <= radius) { out.add(p); }
        }
        return out;
    }

    public void addTower(BlockPos pos) { if (towers.add(pos.asLong())) { changed(); } }

    public void removeTower(BlockPos pos) { if (towers.remove(pos.asLong())) { changed(); } }

    public Set<BlockPos> towers() {
        Set<BlockPos> out = new HashSet<>();
        for (long t : towers) { out.add(BlockPos.of(t)); }
        return out;
    }

    /** The fittings (a copy), by position. */
    public List<Fixture> fixtures() {
        List<Fixture> out = new ArrayList<>(fixtures.size());
        for (Map.Entry<Long, Fixture.Kind> e : fixtures.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            out.add(new Fixture(e.getValue(), p.getX(), p.getY(), p.getZ()));
        }
        return out;
    }

    /** The fittings within a horizontal distance of a point. */
    public List<Fixture> fixturesNear(double x, double z, double radius) {
        List<Fixture> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Map.Entry<Long, Fixture.Kind> e : fixtures.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            double dx = p.getX() + 0.5 - x, dz = p.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= r2) { out.add(new Fixture(e.getValue(), p.getX(), p.getY(), p.getZ())); }
        }
        return out;
    }

    /** The runways the threshold blocks make (cached until they change). */
    public List<Runway> runways() {
        if (runways == null) {
            List<RunwayFinder.Cell> cells = new ArrayList<>(thresholds.size());
            for (Map.Entry<Long, int[]> e : thresholds.entrySet()) {
                BlockPos p = BlockPos.of(e.getKey());
                cells.add(new RunwayFinder.Cell(p.getX(), p.getY(), p.getZ(), e.getValue()[0], e.getValue()[1]));
            }
            runways = List.copyOf(RunwayFinder.find(cells));
        }
        return runways;
    }

    /** The runways near a point (their middle within a distance). */
    public List<Runway> runwaysNear(double x, double z, double radius) {
        List<Runway> out = new ArrayList<>();
        for (Runway r : runways()) {
            if (Math.hypot(r.centre().x() - x, r.centre().z() - z) <= radius + r.lengthM() / 2) { out.add(r); }
        }
        return out;
    }

    // ---------------------------------------------------------------- world generation

    /** A planned airport, once per region: its fittings, thresholds and tower registered at once. */
    public boolean plannedHere(long regionKey) { return planned.containsKey(regionKey); }

    public void addPlan(AirportPlanner.Plan p, List<Fixture> lights, Map<Long, int[]> thresholdCells, BlockPos tower, List<BlockPos> floods) {
        long key = AirportPlanner.regionKey(p.regionX(), p.regionZ());
        if (planned.containsKey(key)) { return; }
        planned.put(key, p);
        for (Fixture f : lights) { fixtures.put(BlockPos.asLong(f.x(), f.y(), f.z()), f.kind()); }
        thresholds.putAll(thresholdCells);
        if (tower != null) { towers.add(tower.asLong()); }   // 0.21: small airports have none
        for (BlockPos f : floods) { floodlights.add(f.asLong()); }
        changed();
    }

    public Map<Long, AirportPlanner.Plan> plans() { return Map.copyOf(planned); }

    /** The world's region files before airports (null until taken). */
    public Set<Long> oldRegions() { return oldRegions; }

    public void setOldRegions(Set<Long> regions) { oldRegions = Set.copyOf(regions); setDirty(); }

    /**
     * The planner version the old regions were last brought up to (0.21): a newer planner plans differently where 0.20
     * planned none (none is not saved), so the region files there when it first runs are added to the old regions.
     */
    public int plannerVersion() { return plannerVersion; }

    public void setPlannerVersion(int v) { plannerVersion = v; setDirty(); }

    // ---------------------------------------------------------------- saving

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Fixture.Kind> e : fixtures.entrySet()) {
            CompoundTag f = new CompoundTag();
            f.putLong("Pos", e.getKey());
            f.putString("Kind", e.getValue().name());
            list.add(f);
        }
        tag.put("Fixtures", list);
        ListTag th = new ListTag();
        for (Map.Entry<Long, int[]> e : thresholds.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey());
            t.putInt("Dx", e.getValue()[0]);
            t.putInt("Dz", e.getValue()[1]);
            th.add(t);
        }
        tag.put("Thresholds", th);
        tag.put("Towers", new LongArrayTag(towers.stream().mapToLong(Long::longValue).toArray()));
        tag.put("Floodlights", new LongArrayTag(floodlights.stream().mapToLong(Long::longValue).toArray()));
        ListTag plans = new ListTag();
        for (AirportPlanner.Plan p : planned.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("Version", p.version()); c.putInt("Rx", p.regionX()); c.putInt("Rz", p.regionZ()); c.putString("Axis", p.axis().name());
            c.putInt("Start", p.start()); c.putInt("Cross", p.cross()); c.putInt("Length", p.lengthM()); c.putInt("Y", p.surfaceY());
            c.putInt("Side", p.side()); c.putInt("Courses", p.towerCourses()); c.putLong("Seed", p.seed()); c.putString("Tier", p.tier().name());
            plans.add(c);
        }
        tag.put("Plans", plans);
        if (oldRegions != null) { tag.put("OldRegions", new LongArrayTag(oldRegions.stream().mapToLong(Long::longValue).toArray())); }
        tag.putInt("PlannerVersion", plannerVersion);
        return tag;
    }

    private static AirfieldData load(CompoundTag tag, HolderLookup.Provider registries) {
        AirfieldData d = new AirfieldData();
        for (Tag t : tag.getList("Fixtures", Tag.TAG_COMPOUND)) {
            CompoundTag f = (CompoundTag) t;
            try { d.fixtures.put(f.getLong("Pos"), Fixture.Kind.valueOf(f.getString("Kind"))); } catch (IllegalArgumentException ignored) {}
        }
        for (Tag t : tag.getList("Thresholds", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.thresholds.put(c.getLong("Pos"), new int[]{c.getInt("Dx"), c.getInt("Dz")});
        }
        for (long t : tag.getLongArray("Towers")) { d.towers.add(t); }
        for (long t : tag.getLongArray("Floodlights")) { d.floodlights.add(t); }
        for (Tag t : tag.getList("Plans", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            // 0.20's plans have no tier: they are the large airport
            AirportTier tier = AirportTier.LARGE;
            try { if (c.contains("Tier")) { tier = AirportTier.valueOf(c.getString("Tier")); } } catch (IllegalArgumentException ignored) {}
            AirportPlanner.Plan p = new AirportPlanner.Plan(c.getInt("Version"), c.getInt("Rx"), c.getInt("Rz"), Runway.Axis.valueOf(c.getString("Axis")),
                    c.getInt("Start"), c.getInt("Cross"), c.getInt("Length"), c.getInt("Y"), c.getInt("Side"), c.getInt("Courses"), c.getLong("Seed"), tier);
            d.planned.put(AirportPlanner.regionKey(p.regionX(), p.regionZ()), p);
        }
        if (tag.contains("OldRegions")) {
            Set<Long> old = new HashSet<>();
            for (long r : tag.getLongArray("OldRegions")) { old.add(r); }
            d.oldRegions = old;
        }
        // 0.20 saved none: its old regions are 0.20's (planner version 1)
        d.plannerVersion = tag.contains("PlannerVersion") ? tag.getInt("PlannerVersion") : d.oldRegions == null ? 0 : 1;
        return d;
    }
}
