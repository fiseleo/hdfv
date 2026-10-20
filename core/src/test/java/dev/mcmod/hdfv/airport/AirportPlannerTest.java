package dev.mcmod.hdfv.airport;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/** World-generated airports (0.20; three sizes 0.21): where they go on made-up terrain, and what they are laid out with. */
class AirportPlannerTest {
    /** Terrain from functions: ground height, water surface (the ground's where dry), suitable biome, blocked. */
    record Terrain(IntBinaryOperator ground, IntBinaryOperator water, BiomeTest biome, boolean blockedEverywhere) implements AirportPlanner.TerrainProbe {
        interface BiomeTest { boolean ok(int x, int z); }
        @Override public int surfaceY(int x, int z) { return Math.max(ground.applyAsInt(x, z), water.applyAsInt(x, z)); }
        @Override public int groundY(int x, int z) { return ground.applyAsInt(x, z); }
        @Override public boolean suitableBiome(int x, int z) { return biome.ok(x, z); }
        @Override public boolean blocked(int minX, int minZ, int maxX, int maxZ) { return blockedEverywhere; }
    }

    private static final Terrain PLAINS = new Terrain((x, z) -> 64, (x, z) -> 0, (x, z) -> true, false);
    private static final Set<AirportTier> ALL = EnumSet.allOf(AirportTier.class);

    /** The first region (rx, 0), rx from 0 up, whose sizes start with a tier. */
    private static int regionStartingWith(long seed, AirportTier tier) {
        for (int rx = 0; ; rx++) { if (AirportPlanner.tiers(seed, rx, 0, ALL).get(0) == tier) { return rx; } }
    }

    @Test void flatPlainsGetALargeAirportAlongThePrevailingWind() {
        int rx = regionStartingWith(42, AirportTier.LARGE);
        AirportPlanner.Plan p = AirportPlanner.plan(42, rx, 0, 0.1, PLAINS);
        assertNotNull(p);
        assertEquals(AirportTier.LARGE, p.tier(), "a region that may have a large airport has one where one fits");
        assertEquals(Runway.Axis.X, p.axis(), "the wind blows east: the runway lies east-west");
        assertEquals(900, p.lengthM(), "the longest");
        assertEquals(64, p.surfaceY());
        assertEquals(p, AirportPlanner.plan(42, rx, 0, 0.1, PLAINS), "the same every time");
        assertEquals(Runway.Axis.Z, AirportPlanner.plan(42, rx, 0, Math.PI / 2 + 0.2, PLAINS).axis());
        assertNotEquals(p, AirportPlanner.plan(43, rx, 0, 0.1, PLAINS), "another seed, another place");
        // the whole airport and its slopes in the region
        AirportLayout.Bounds f = AirportLayout.footprint(p);
        int r0x = rx * AirportPlanner.REGION;
        assertTrue(f.minX() >= r0x && f.maxX() < r0x + AirportPlanner.REGION && f.minZ() >= 0 && f.maxZ() < AirportPlanner.REGION, "inside its region: " + f);
        // a size not allowed is not planned (the server settings)
        AirportPlanner.Plan noLarge = AirportPlanner.plan(42, rx, 0, 0.1, PLAINS, EnumSet.of(AirportTier.MEDIUM, AirportTier.SMALL));
        assertEquals(AirportTier.MEDIUM, noLarge.tier());
        assertNull(AirportPlanner.plan(42, regionStartingWith(42, AirportTier.SMALL), 0, 0.1, PLAINS, EnumSet.of(AirportTier.LARGE, AirportTier.MEDIUM)));
    }

    @Test void waterSteepGroundTheWrongBiomeOrSomethingInTheWayGetNone() {
        assertNull(AirportPlanner.plan(42, 0, 0, 0, new Terrain((x, z) -> 50, (x, z) -> 62, (x, z) -> true, false)), "the sea");
        assertNull(AirportPlanner.plan(42, 0, 0, 0, new Terrain((x, z) -> 64 + (x + z) / 10, (x, z) -> 0, (x, z) -> true, false)), "a 14 % slope everywhere");
        assertNull(AirportPlanner.plan(42, 0, 0, 0, new Terrain((x, z) -> 64, (x, z) -> 0, (x, z) -> false, false)), "a forest");
        assertNull(AirportPlanner.plan(42, 0, 0, 0, new Terrain((x, z) -> 64, (x, z) -> 0, (x, z) -> true, true)), "a village or old chunks");
        // a gentle slope along the wind: the longest runway along it does not fit; a shorter one or one across the slope does
        int rx = regionStartingWith(42, AirportTier.LARGE);
        AirportPlanner.Plan gentle = AirportPlanner.plan(42, rx, 0, 0, new Terrain((x, z) -> 64 + Math.floorDiv(x, 15), (x, z) -> 0, (x, z) -> true, false));
        assertNotNull(gentle);
        assertTrue(gentle.lengthM() < 900 || gentle.axis() == Runway.Axis.Z, "shortened or turned to fit: " + gentle.lengthM() + " along " + gentle.axis());
        // shallow water on a little of the ground is filled; deep water is not
        assertNotNull(AirportPlanner.plan(42, rx, 0, 0, new Terrain((x, z) -> 64, (x, z) -> Math.floorMod(x, 400) < 16 ? 66 : 0, (x, z) -> true, false)), "a creek every 400 m");
        // a deep pond in one candidate's way moves the airport to another candidate (a shallow one would be filled)
        for (AirportTier tier : AirportTier.values()) {
            int r = regionStartingWith(7, tier);
            AirportPlanner.Plan dry = AirportPlanner.plan(7, r, 0, 0, PLAINS);
            assertEquals(tier, dry.tier());
            // on the runway's middle (a few deep samples elsewhere on the ground would be outliers, filled)
            int px = (int) dry.runway().centre().x(), pz = (int) dry.runway().centre().z();
            Terrain wet = new Terrain((x, z) -> Math.abs(x - px) < 40 && Math.abs(z - pz) < 40 ? 30 : 64, (x, z) -> Math.abs(x - px) < 40 && Math.abs(z - pz) < 40 ? 63 : 0, (x, z) -> true, false);
            AirportPlanner.Plan moved = AirportPlanner.plan(7, r, 0, 0, wet);
            assertNotNull(moved);
            assertNotEquals(dry.start(), moved.start(), tier.toString());
        }
    }

    @Test void aSettlementKeepsItsDistanceAndTheDiagnosisSaysWhy() {
        AirportPlanner.Plan p = AirportPlanner.plan(42, 0, 0, 0, PLAINS);
        var c = p.runway().centre();
        int vx = (int) c.x(), vz = (int) c.z();
        // a village starting on the runway's middle: that candidate is refused, the next one taken clear of it
        AirportPlanner.TerrainProbe withVillage = new AirportPlanner.TerrainProbe() {
            @Override public int surfaceY(int x, int z) { return PLAINS.surfaceY(x, z); }
            @Override public int groundY(int x, int z) { return PLAINS.groundY(x, z); }
            @Override public boolean suitableBiome(int x, int z) { return true; }
            @Override public boolean blocked(int minX, int minZ, int maxX, int maxZ) { return false; }
            @Override public List<int[]> settlements(int minX, int minZ, int maxX, int maxZ) {
                return vx >= minX && vx <= maxX && vz >= minZ && vz <= maxZ ? List.<int[]>of(new int[]{vx, vz}) : List.of();
            }
        };
        AirportPlanner.Plan q = AirportPlanner.plan(42, 0, 0, 0, withVillage);
        assertNotNull(q, "another candidate");
        assertTrue(AirportLayout.distanceToLevelled(q, vx, vz) >= AirportPlanner.SETTLEMENT_M, "clear of the village");
        List<String> why = AirportPlanner.diagnose(42, 0, 0, 0, withVillage);
        assertTrue(why.get(0).startsWith("sizes "), "the sizes the region may have first: " + why);
        assertTrue(why.get(1).contains("a settlement at " + vx + " " + vz), "the first candidate's reason: " + why);
        assertTrue(why.get(why.size() - 1).contains("fits at y 64"), "the one taken: " + why);
        // the quick look is on what is built: water off the levelled ground (where the bounding box's corners are) does not matter
        AirportLayout.Bounds b = AirportLayout.levelled(p);
        Terrain cornerPonds = new Terrain((x, z) -> 64, (x, z) -> Math.abs(x - b.minX()) < 6 && Math.abs(z - b.minZ()) < 6 ? 66 : 0, (x, z) -> true, false);
        if (!AirportLayout.levelledAt(p, b.minX(), b.minZ())) { assertEquals(p, AirportPlanner.plan(42, 0, 0, 0, cornerPonds), "a pond outside the airport's ground"); }
    }

    @Test void largeAirportsAreRareAndSmallerOnesFillIn() {
        // biomes in 400 m patches, a third of them plains; gentle hills
        Terrain world = new Terrain((x, z) -> 64 + (int) Math.round(4 * Math.sin(x / 300.0) * Math.cos(z / 260.0)), (x, z) -> 0,
                (x, z) -> Math.floorMod((Math.floorDiv(x, 400) * 73856093) ^ (Math.floorDiv(z, 400) * 19349663), 3) == 0, false);
        Map<AirportTier, Integer> found = new HashMap<>();
        int regions = 0, any = 0;
        for (int rx = -4; rx < 4; rx++) {
            for (int rz = -4; rz < 4; rz++) {
                regions++;
                AirportPlanner.Plan p = AirportPlanner.plan(99, rx, rz, 0.4, world);
                if (p != null) { any++; found.merge(p.tier(), 1, Integer::sum); }
            }
        }
        double perKm2 = any / (regions * Math.pow(AirportPlanner.REGION / 1000.0, 2));
        System.out.printf("%d airports in %d regions %s: one per %.1f km2 (spacing about %.1f km)%n", any, regions, found, 1 / perKm2, Math.sqrt(1 / perKm2));
        assertTrue(any > regions / 3, "most regions with plains have one: " + any);
        assertTrue(found.getOrDefault(AirportTier.LARGE, 0) <= 1.5 * AirportPlanner.LARGE_SHARE * regions, "at most about the share that may: " + found);
        assertTrue(found.getOrDefault(AirportTier.SMALL, 0) + found.getOrDefault(AirportTier.MEDIUM, 0) > found.getOrDefault(AirportTier.LARGE, 0), "most are smaller: " + found);
        // the shares of the draw itself
        int large = 0, medium = 0, n = 4000;
        for (int i = 0; i < n; i++) {
            AirportTier first = AirportPlanner.tiers(5, i, i * 7, ALL).get(0);
            if (first == AirportTier.LARGE) { large++; } else if (first == AirportTier.MEDIUM) { medium++; }
        }
        assertEquals(AirportPlanner.LARGE_SHARE, large / (double) n, 0.03);
        assertEquals(AirportPlanner.MEDIUM_SHARE, medium / (double) n, 0.03);
    }

    @Test void eachSizeHangsTogether() {
        for (AirportTier tier : AirportTier.values()) {
            AirportTier.Spec spec = tier.spec;
            AirportPlanner.Plan p = AirportPlanner.plan(42, regionStartingWith(42, tier), 0, 0.1, PLAINS);
            assertEquals(tier, p.tier());
            assertEquals(spec.lengths().get(0), p.lengthM(), tier + ": the longest of its size");
            Runway r = p.runway();
            assertEquals(spec.widthM(), r.widthM(), 1e-9);
            // the paint's threshold patches name the planned runway
            List<RunwayFinder.Cell> cells = new ArrayList<>();
            for (Map.Entry<Long, RunwayMarkings.Mark> e : AirportLayout.marks(p).entrySet()) {
                if (e.getValue().paint() == RunwayMarkings.Paint.THRESHOLD) {
                    cells.add(new RunwayFinder.Cell(RunwayMarkings.keyX(e.getKey()), p.surfaceY() - 1, RunwayMarkings.keyZ(e.getKey()), e.getValue().dx(), e.getValue().dz()));
                }
            }
            assertEquals(List.of(r), RunwayFinder.find(cells), tier.toString());
            // everything stands on levelled ground of the right kind
            List<Fixture> fixtures = AirportLayout.fixtures(p);
            for (Fixture f : fixtures) {
                if (f.kind() == Fixture.Kind.BEACON || f.kind() == Fixture.Kind.OBSTRUCTION) { continue; }
                assertNotEquals(AirportLayout.Ground.NONE, AirportLayout.ground(p, f.x(), f.z()), tier + " " + f);
                assertEquals(p.surfaceY(), f.y());
            }
            for (AirportLayout.Thing t : AirportLayout.things(p)) {
                if (t.kind() == AirportLayout.ThingKind.WINDSOCK || t.kind() == AirportLayout.ThingKind.HOLD_SIGN || t.kind() == AirportLayout.ThingKind.FLOODLIGHT) {
                    assertNotEquals(AirportLayout.Ground.NONE, AirportLayout.ground(p, t.x(), t.z()), tier + " " + t);
                }
            }
            assertEquals(AirportLayout.Ground.RUNWAY, AirportLayout.ground(p, (int) r.centre().x(), (int) r.centre().z()));
            AirportLayout.HangarSite h = AirportLayout.hangar(p);
            assertEquals(AirportLayout.Ground.HANGAR, AirportLayout.ground(p, (h.minX() + h.maxX()) / 2, (h.minZ() + h.maxZ()) / 2));
            AirportLayout.TowerSite t = AirportLayout.tower(p);
            List<AirportLayout.Thing> things = AirportLayout.things(p);
            if (spec.tower()) {
                assertEquals(AirportLayout.Ground.TOWER, AirportLayout.ground(p, t.x(), t.z()));
                assertTrue(t.courses() >= spec.minCourses() && t.courses() <= spec.maxCourses(), tier + " " + t.courses());
                for (dev.mcmod.hdfv.structure.Pattern.Cell c : dev.mcmod.hdfv.structure.Structures.TOWER_PATTERN.cells(t.x(), t.y(), t.z(), t.facing(), t.courses())) {
                    if (c.y() == t.y()) { assertNotEquals(AirportLayout.Ground.NONE, AirportLayout.ground(p, c.x(), c.z()), "the tower's base on levelled ground"); }
                }
                assertTrue(Math.abs(r.offset(t.x() + 0.5, t.z() + 0.5)) > spec.taxiOffset(), "beyond the apron");
            } else {
                assertNull(t, "no tower");
                assertTrue(fixtures.isEmpty(), "no lights without a tower to light them");
            }
            assertEquals(spec.batteries(), things.stream().filter(x -> x.kind() == AirportLayout.ThingKind.BATTERY).count(), tier.toString());
            assertEquals(spec.windsocks(), things.stream().filter(x -> x.kind() == AirportLayout.ThingKind.WINDSOCK).count());
            assertEquals(spec.floodlights(), things.stream().filter(x -> x.kind() == AirportLayout.ThingKind.FLOODLIGHT).count());
            assertEquals(2L * spec.papiUnits(), fixtures.stream().filter(f -> f.kind() == Fixture.Kind.PAPI).count());
            assertEquals(spec.approach() > 0 ? 6 : 0, fixtures.stream().filter(f -> f.kind() == Fixture.Kind.APPROACH_FLASHER).count());
            if (tier == AirportTier.LARGE) { assertEquals(30, fixtures.stream().filter(f -> f.kind() == Fixture.Kind.RUNWAY_EDGE).count()); }
            // the slopes meet the ground: on the levelled ground the plan's height, beyond the slope the ground's own
            AirportLayout.Bounds b = AirportLayout.levelled(p);
            assertEquals(p.surfaceY(), AirportLayout.gradedY(p, (int) r.centre().x(), (int) r.centre().z(), 80));
            assertEquals(80, AirportLayout.gradedY(p, b.minX() - AirportLayout.SLOPE_M - 2, b.minZ() - AirportLayout.SLOPE_M - 2, 80));
            if (!fixtures.isEmpty()) { assertTrue(AirfieldLighting.lamps(List.of(r), fixtures).size() > fixtures.size()); }
        }
    }

    /** A tower's inside (0.21): the spiral stair climbs a layer a cell, has its headroom, and ends at the back of the hatch. */
    @Test void theTowersStairBatteriesAndLampsFit() {
        for (int courses : new int[]{6, 7, 8, 13, 16, 56}) {
            for (int facing = 0; facing < 4; facing++) {
                AirportLayout.TowerSite t = new AirportLayout.TowerSite(100, 64, -40, facing, courses);
                List<AirportLayout.Thing> inside = AirportLayout.towerInside(t, 8);
                Map<Long, AirportLayout.Thing> at = new HashMap<>();
                for (AirportLayout.Thing x : inside) { assertNull(at.put(key(x.x(), x.y(), x.z()), x), "one thing a block: " + x); }
                int top = 2 + courses;
                List<AirportLayout.Thing> steps = inside.stream().filter(x -> x.kind() == AirportLayout.ThingKind.STAIR).toList();
                assertEquals(top + 1, steps.size());
                for (int k = 0; k <= top; k++) {
                    AirportLayout.Thing s = steps.get(k);
                    assertEquals(t.y() + k, s.y(), "a layer a step");
                    if (k > 0) {
                        AirportLayout.Thing before = steps.get(k - 1);
                        assertEquals(1, Math.abs(s.x() - before.x()) + Math.abs(s.z() - before.z()), "the next cell round");
                        // facing the way one comes onto it (a corner's step carries the run on; one turns off its high half)
                        int[][] step = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
                        assertEquals(s.x() - before.x(), step[s.facing()][0], "step " + k + " faces the way in");
                        assertEquals(s.z() - before.z(), step[s.facing()][1], "step " + k + " faces the way in");
                    }
                    // three blocks of headroom over every step (a player on its low half reaches 3.3 above its layer), up to the cab
                    for (int up = 1; up <= 3 && k + up <= top; up++) {
                        AirportLayout.Thing over = at.get(key(s.x(), s.y() + up, s.z()));
                        assertTrue(over == null || over.kind() == AirportLayout.ThingKind.LAMP, "headroom over step " + k + ": " + over);
                    }
                    assertNotNull(at.get(key(AirportLayout.inside(t, 3, 0)[0], t.y() + k, AirportLayout.inside(t, 3, 0)[1])), "the column");
                }
                int[] topCell = AirportLayout.inside(t, AirportLayout.STAIR_RING[AirportLayout.TOP_CELL][0], AirportLayout.STAIR_RING[AirportLayout.TOP_CELL][1]);
                assertEquals(topCell[0], steps.get(top).x());
                assertEquals(topCell[1], steps.get(top).z());
                // the steps under the top: their cells open at the cab floor's layer; the rest of the hatch covered
                long gratings = inside.stream().filter(x -> x.kind() == AirportLayout.ThingKind.GRATING && x.y() == t.y() + top).count();
                assertEquals(4, gratings);
                for (int k = top - 3; k < top; k++) { assertNull(at.get(key(steps.get(k).x(), t.y() + top, steps.get(k).z())), "open over step " + k); }
                // every battery on a duct, the ducts joined to the one under the controller
                Set<Long> ducts = new HashSet<>();
                for (AirportLayout.Thing x : inside) { if (x.kind() == AirportLayout.ThingKind.DUCT) { ducts.add(key(x.x(), x.y(), x.z())); } }
                Set<Long> joined = new HashSet<>();
                List<long[]> queue = new ArrayList<>();
                queue.add(new long[]{t.x(), t.y() - 1, t.z()});
                joined.add(key(t.x(), t.y() - 1, t.z()));
                while (!queue.isEmpty()) {
                    long[] c = queue.remove(queue.size() - 1);
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        long k = key((int) c[0] + d[0], (int) c[1], (int) c[2] + d[1]);
                        if (ducts.contains(k) && joined.add(k)) { queue.add(new long[]{c[0] + d[0], c[1], c[2] + d[1]}); }
                    }
                }
                assertEquals(ducts, joined, "one run of ducts from the controller");
                for (AirportLayout.Thing x : inside) {
                    if (x.kind() == AirportLayout.ThingKind.BATTERY) { assertTrue(ducts.contains(key(x.x(), x.y() - 1, x.z())), "on a duct: " + x); }
                    if (x.kind() == AirportLayout.ThingKind.BED || x.kind() == AirportLayout.ThingKind.VILLAGER) { assertEquals(t.y(), x.y()); }
                }
                assertEquals(8, inside.stream().filter(x -> x.kind() == AirportLayout.ThingKind.BATTERY).count());
                assertTrue(inside.stream().filter(x -> x.kind() == AirportLayout.ThingKind.LAMP).count() >= 2 + (top - 2) / 5);
                theBedsHeadIsReachedFromTheDoor(t, inside);
                theBedsHeadIsReachedFromTheDoor(t, AirportLayout.towerInside(t, 4));
            }
        }
        assertEquals(4, AirportLayout.towerInside(new AirportLayout.TowerSite(0, 64, 0, 0, 6), 4).stream().filter(x -> x.kind() == AirportLayout.ThingKind.BATTERY).count());
    }

    /**
     * A villager claims a bed only if its path ends within a block of the bed's head (0.21's first look in game: the
     * controller never claimed the bed, its head boxed in by the wall, a battery's cell and the second step over the
     * cell beside it). Walks the base from the doorway over cells with two blocks of room (a ceiling lamp and the villager standing there let through).
     */
    private static void theBedsHeadIsReachedFromTheDoor(AirportLayout.TowerSite t, List<AirportLayout.Thing> inside) {
        Map<Long, AirportLayout.Thing> at = new HashMap<>();
        for (AirportLayout.Thing x : inside) { at.put(key(x.x(), x.y(), x.z()), x); }
        java.util.function.BiPredicate<Integer, Integer> open = (row, col) -> {
            if (row == 0 ? col != -1 : row < 1 || row > 5 || col < -2 || col > 2) { return false; }   // the doorway, the base's inside
            int[] w = AirportLayout.inside(t, row, col);
            for (int up = 0; up <= 1; up++) {
                AirportLayout.Thing x = at.get(key(w[0], t.y() + up, w[1]));
                if (x != null && x.kind() != AirportLayout.ThingKind.LAMP && x.kind() != AirportLayout.ThingKind.VILLAGER) { return false; }
            }
            return true;
        };
        Set<Long> reached = new HashSet<>();
        List<int[]> queue = new ArrayList<>(List.of(new int[]{0, -1}));
        reached.add(key(0, 0, -1));
        while (!queue.isEmpty()) {
            int[] c = queue.remove(queue.size() - 1);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int r = c[0] + d[0], cc = c[1] + d[1];
                if (open.test(r, cc) && reached.add(key(r, 0, cc))) { queue.add(new int[]{r, cc}); }
            }
        }
        AirportLayout.Thing bed = inside.stream().filter(x -> x.kind() == AirportLayout.ThingKind.BED).findFirst().orElseThrow();
        int[][] step = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};   // facings 0 south, 1 west, 2 north, 3 east
        int hx = bed.x() + step[bed.facing()][0], hz = bed.z() + step[bed.facing()][1];
        boolean near = false;
        for (int row = 0; row <= 5; row++) {
            for (int col = -2; col <= 2; col++) {
                int[] w = AirportLayout.inside(t, row, col);
                if (reached.contains(key(row, 0, col)) && Math.abs(w[0] - hx) + Math.abs(w[1] - hz) == 1) { near = true; }
            }
        }
        assertTrue(near, "a cell next to the bed's head is reached from the doorway (facing " + t.facing() + ", " + t.courses() + " courses)");
        AirportLayout.Thing v = inside.stream().filter(x -> x.kind() == AirportLayout.ThingKind.VILLAGER).findFirst().orElseThrow();
        boolean stands = false;
        for (int row = 1; row <= 5; row++) {
            for (int col = -2; col <= 2; col++) {
                int[] w = AirportLayout.inside(t, row, col);
                if (w[0] == v.x() && w[1] == v.z()) { stands = reached.contains(key(row, 0, col)); }
            }
        }
        assertTrue(stands, "the controller's place is reached from the doorway");
    }

    private static long key(int x, int y, int z) { return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52); }

    /**
     * 0.20's airports, half generated in old worlds, are finished from their saved plans by the code of the day: a large
     * plan's ground, paint, lights, hangar and tower site never change (what stands in the tower's base may).
     */
    @Test void aPlanOf020IsStillLaidOutTheSame() {
        AirportPlanner.Plan x = new AirportPlanner.Plan(1, 3, -2, Runway.Axis.X, 10155, -5321, 900, 64, -1, 13, -4414968807529055727L, AirportTier.LARGE);
        AirportPlanner.Plan z = new AirportPlanner.Plan(1, -5, 7, Runway.Axis.Z, 21771, -14567, 900, 64, -1, 12, 4771862641369038522L, AirportTier.LARGE);
        assertEquals(-2279555038452319870L, AirportLayout.groundDigest(x));
        assertEquals(-5371760998887627541L, AirportLayout.groundDigest(z));
    }

    /** A version's airports never change: the digest of a sample of each size is frozen (change VERSION to change the layout). */
    @Test void aVersionsLayoutIsFrozen() {
        long[] got = new long[3];
        for (AirportTier tier : AirportTier.values()) {
            AirportPlanner.Plan p = AirportPlanner.plan(1234, regionStartingWith(1234, tier), 0, 1.7, PLAINS);
            got[tier.ordinal()] = AirportLayout.digest(p);
        }
        System.out.println("airport layout digests: " + got[0] + "L, " + got[1] + "L, " + got[2] + "L");
        assertArrayEquals(FROZEN, got);
    }

    /**
     * Large, medium, small (VERSION 2). Changed before 0.21 was committed: the base's bed turned round
     * (its head reached), the stair's steps turned to face the way in and the grating off the fourth step from the top
     * (towers generated before that are put right by TowerBlockEntity.repairStair).
     */
    private static final long[] FROZEN = {-8931143386567157696L, -3925550827548994953L, -8564223582259439878L};
}
