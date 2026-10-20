package dev.mcmod.hdfv.airport;

import java.util.Random;

/**
 * Where world generation puts airports (0.20), from the world seed alone, so every chunk of an airport computes the
 * same airport without anything stored (as OilFields does for oil fields):
 * <ul>
 *   <li>The world is cut into regions {@link #REGION} blocks square; a region has at most one airport, within its
 *   inner area (a margin of {@link #MARGIN} keeps the whole airport, approach lights included, in the region, so a
 *   chunk needs only its own region's plan).</li>
 *   <li>Up to {@link #CANDIDATES} centres are drawn from Random(seed ^ region key); the first that suits wins. The
 *   runway runs along X or Z, whichever is nearer the world's prevailing wind (WorldWind's synoptic direction), else the
 *   other way, as long as the terrain allows: the longest of 900, 800, 700, 600 m whose ground lets the strip be
 *   levelled.</li>
 *   <li>Suiting means: a biome in #hdfv:has_airport at the centre and along most of the runway (at most
 *   {@link #OTHER_BIOMES} of it in others, a river across it aside); water
 *   on at most {@link #WATER_SHARE} of the levelled ground and no deeper than {@link #FILL_M} (it is filled); the
 *   levelled height (the median of the runway's ground) at most {@link #CUT_M} below or {@link #FILL_M} above the
 *   ground (or a water's bed) anywhere on it; no region of the world generated before airports existed under it; no
 *   settlement (village, outpost) starting within {@link #SETTLEMENT_M} of it.</li>
 * </ul>
 * The approach-light strips are levelled too but checked at twice the cut and fill (they are narrow banks).
 * <p>
 * Tuned in real worlds (0.20, /hdfv dev airport why over a headless server's RCON): Minecraft 1.21's open land is
 * hilly (plains on hills between oceans, forests and meadows; a candidate's quick-look heights 19 to 73 m apart, median
 * 44). The first rules (a 1.4 km strip all levelled, 8 m cut and 6 m fill, no water, 12 candidates on one axis) left no
 * airport in 49 regions; with these (strip ends 30 m, approach strips 150 m, cut 24 m and fill 18 m with banks
 * {@link AirportLayout#SLOPE_M} wide, {@link #OUTLIERS} of the samples beyond them for cave mouths and pits, 48
 * candidates on both axes) three seeds had 20 airports in 75 regions (one in about 35 km^2 of all terrain, sea and
 * mountains included). Each quick-look height is the median of three samples of the probe's rough heights (the
 * preliminary surface, about a tenth of the cost) with {@link #ROUGH_M} of slack; the grid then uses the real ones on
 * world-aligned points (shared by lengths, axes and candidates), the runway's strip first, stopping at the first excess.
 * A region costs 0.3 to 4.4 s of noise on a world generation thread (the probe stops at 30 000 columns).
 * {@link #VERSION} is part of the plan: never change what a version plans (worlds keep their airports' halves in older
 * chunks).
 * <p>
 * 0.21 (version 2): three sizes (AirportTier). Each region first draws, from a random of its own (the candidates' draws
 * are untouched), which sizes it may have: a fifth of the regions large, then medium, then small; a third medium, then
 * small; the rest small only. Each size in turn is tried on every candidate (the larger first, so a region that may have a
 * large airport has one wherever one fits), with its own lengths, width and cut and fill. A large airport fits about a
 * quarter of real regions (0.20's survey), so one in about twenty regions has one: one every 10 to 15 km (closer on wide
 * plains); the medium and small ones fill the rest where the ground allows. The
 * tower's courses come from the same draw as 0.20's, folded into the size's range.
 */
public final class AirportPlanner {
    private AirportPlanner() {}

    public static final int VERSION = 2, REGION = 3072, MARGIN = 650, CANDIDATES = 48;
    /** The large airport's (0.20's) runway width and cut and fill (each size's are in its spec). */
    public static final int WIDTH = 23, CUT_M = 24, FILL_M = 18;
    /** The shares of regions that may have a large airport, and then a medium one (the rest small only). */
    public static final double LARGE_SHARE = 0.2, MEDIUM_SHARE = 0.35;
    /** The share of the levelled ground's samples that may lie outside the cut and fill (cave mouths, pits: filled). */
    public static final double OUTLIERS = 0.04;
    /** The slack the quick look gives a probe's rough heights (m). */
    public static final int ROUGH_M = 10;
    /** The share of the runway's dry samples that may lie in other biomes (a wood, a meadow across it: cleared). */
    public static final double OTHER_BIOMES = 0.4;
    /** The share of the levelled ground's samples that may be water (filled from its bed). */
    public static final double WATER_SHARE = 0.15;
    /** How far apart the samples along and across the levelled ground are (m). */
    static final int STEP = 32;

    /** What world generation knows about the ground, without loading a chunk. */
    public interface TerrainProbe {
        /** The top of the ground or of water (m, the y of the first air block above). */
        int surfaceY(int x, int z);
        /** The top of the ground under any water. */
        int groundY(int x, int z);
        /** A cheap guess at groundY (the quick look; off by up to {@link #ROUGH_M}); groundY itself by default. */
        default int roughGroundY(int x, int z) { return groundY(x, z); }
        /** A cheap guess at surfaceY, as roughGroundY. */
        default int roughSurfaceY(int x, int z) { return surfaceY(x, z); }
        /** Whether airports may stand on the biome there. */
        boolean suitableBiome(int x, int z);
        /** Whether something else is in the way of an airport over that rectangle (old chunks). */
        boolean blocked(int minX, int minZ, int maxX, int maxZ);
        /** Where settlements (villages, outposts) would start in that rectangle (block x and z each); none by default. */
        default java.util.List<int[]> settlements(int minX, int minZ, int maxX, int maxZ) { return java.util.List.of(); }
        /** Whether the probe has taken as many samples as a region may cost (then the region gets no airport). */
        default boolean exhausted() { return false; }
    }

    /** How far a settlement's start must be from the levelled ground (m: a village reaches about this far from it). */
    public static final int SETTLEMENT_M = 80;

    /**
     * An airport: its region, the runway (along X or Z; its LOW end at {@code start} along, its first cross block at
     * {@code cross}, so many metres long and its size's width wide), the levelled surface height (the y of the first air
     * block above the runway), the side of the runway the taxiway and apron are on (+1 or -1 across), the tower's shaft
     * courses (0: none), a seed for its details, its size (0.21; 0.20's plans are large).
     */
    public record Plan(int version, int regionX, int regionZ, Runway.Axis axis, int start, int cross, int lengthM, int surfaceY, int side,
                       int towerCourses, long seed, AirportTier tier) {
        public int widthM() { return tier.spec.widthM(); }
        public Runway runway() { return new Runway(axis, cross + widthM() / 2.0, start, start + lengthM, surfaceY, widthM(), ""); }
    }

    public static long regionKey(int rx, int rz) { return ((long) rx << 32) ^ (rz & 0xFFFFFFFFL); }

    public static int regionOf(int blockCoordinate) { return Math.floorDiv(blockCoordinate, REGION); }

    /**
     * The airport of a region, or null: the seed, the region, the prevailing direction the wind blows toward (radians,
     * WorldWind's convention: from +X toward +Z) and the probe.
     */
    public static Plan plan(long seed, int rx, int rz, double prevailingTowardRad, TerrainProbe probe) {
        return plan(seed, rx, rz, prevailingTowardRad, probe, java.util.EnumSet.allOf(AirportTier.class));
    }

    /** The same, with only some sizes allowed (server settings). */
    public static Plan plan(long seed, int rx, int rz, double prevailingTowardRad, TerrainProbe probe, java.util.Set<AirportTier> allowed) {
        return search(seed, rx, rz, prevailingTowardRad, probe, allowed, null);
    }

    /** The sizes a region may have, in the order tried (its own random: the candidates' draws are untouched), less those not allowed. */
    public static java.util.List<AirportTier> tiers(long seed, int rx, int rz, java.util.Set<AirportTier> allowed) {
        double roll = new Random(seed * 31 ^ regionKey(rx, rz) * 0xC2B2AE3D27D4EB4FL).nextDouble();
        java.util.List<AirportTier> out = new java.util.ArrayList<>(roll < LARGE_SHARE ? java.util.List.of(AirportTier.LARGE, AirportTier.MEDIUM, AirportTier.SMALL)
                : roll < LARGE_SHARE + MEDIUM_SHARE ? java.util.List.of(AirportTier.MEDIUM, AirportTier.SMALL) : java.util.List.of(AirportTier.SMALL));
        out.retainAll(allowed);
        return out;
    }

    /**
     * Why a region has no airport (or where it has one): a line for each candidate, its centre and what stopped it at each
     * length (the dev command /hdfv dev airport why).
     */
    public static java.util.List<String> diagnose(long seed, int rx, int rz, double prevailingTowardRad, TerrainProbe probe) {
        java.util.List<String> out = new java.util.ArrayList<>();
        search(seed, rx, rz, prevailingTowardRad, probe, java.util.EnumSet.allOf(AirportTier.class), out);
        return out;
    }

    private static Plan search(long seed, int rx, int rz, double prevailingTowardRad, TerrainProbe probe, java.util.Set<AirportTier> allowed, java.util.List<String> why) {
        Runway.Axis preferred = Math.abs(Math.cos(prevailingTowardRad)) >= Math.abs(Math.sin(prevailingTowardRad)) ? Runway.Axis.X : Runway.Axis.Z;
        Runway.Axis other = preferred == Runway.Axis.X ? Runway.Axis.Z : Runway.Axis.X;
        java.util.List<AirportTier> tiers = tiers(seed, rx, rz, allowed);
        if (why != null) { why.add("sizes " + tiers); }
        for (AirportTier tier : tiers) {
            AirportTier.Spec spec = tier.spec;
            Random random = new Random(seed ^ regionKey(rx, rz) * 0x9E3779B97F4A7C15L);
            for (int k = 0; k < CANDIDATES; k++) {
                if (probe.exhausted()) {
                    if (why != null) { why.add(tier + ": stopped at candidate " + k + ": the region's samples are spent"); }
                    return null;
                }
                int cx = rx * REGION + MARGIN + random.nextInt(REGION - 2 * MARGIN), cz = rz * REGION + MARGIN + random.nextInt(REGION - 2 * MARGIN);
                int side = random.nextBoolean() ? 1 : -1, courses = tier.courses(8 + random.nextInt(9));
                long detail = random.nextLong();
                if (!probe.suitableBiome(cx, cz)) {
                    if (why != null && tier == tiers.get(0)) { why.add(k + " at " + cx + " " + cz + ": the biome"); }
                    continue;
                }
                StringBuilder reasons = new StringBuilder();
                for (Runway.Axis axis : new Runway.Axis[]{preferred, other}) {
                    for (int length : spec.lengths()) {
                        int start = (axis == Runway.Axis.X ? cx : cz) - length / 2, cross = (axis == Runway.Axis.X ? cz : cx) - spec.widthM() / 2;
                        Plan p = new Plan(VERSION, rx, rz, axis, start, cross, length, 0, side, courses, detail, tier);
                        Object y = level(p, probe);
                        if (y instanceof Integer level) {
                            if (why != null) { why.add(tier + " " + k + " at " + cx + " " + cz + ": " + reasons + length + " m along " + axis + " fits at y " + level); }
                            return new Plan(VERSION, rx, rz, axis, start, cross, length, level, side, courses, detail, tier);
                        }
                        reasons.append(axis).append(' ').append(length).append(" m ").append(y).append("; ");
                    }
                }
                if (why != null) { why.add(tier + " " + k + " at " + cx + " " + cz + ": " + reasons); }
            }
        }
        return null;
    }

    /**
     * The levelled height for a plan's ground, or why it cannot be levelled there (a string): a few heights on what is
     * built first (the runway's ends and middle, the apron, the tower), then the whole levelled ground on a grid (no water,
     * the runway's biomes, within {@link #CUT_M} and {@link #FILL_M} of the runway's median), old chunks over its
     * footprint, settlements starting within {@link #SETTLEMENT_M} of it.
     */
    static Object level(Plan p, TerrainProbe probe) {
        AirportLayout.Bounds b = AirportLayout.levelled(p);
        Runway r = p.runway();
        int cut = p.tier().spec.cutM(), fill = p.tier().spec.fillM();
        AirportLayout.TowerSite tower = AirportLayout.tower(p);
        AirportLayout.Rect apron = AirportLayout.parts(p).apron();
        int am = (apron.a0() + apron.a1()) / 2, ac = (apron.c0() + apron.c1()) / 2;
        AirportLayout.HangarSite hangar = AirportLayout.hangar(p);
        int[] last = tower != null ? new int[]{tower.x(), tower.z()} : new int[]{(hangar.minX() + hangar.maxX()) / 2, (hangar.minZ() + hangar.maxZ()) / 2};
        int cc = (int) Math.floor(r.cross());
        int[][] coarse = {at(p, p.start() + 10, cc), at(p, p.start() + p.lengthM() - 10, cc), at(p, p.start() + p.lengthM() / 2, cc), at(p, am, ac), last};
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int[] along = p.axis() == Runway.Axis.X ? new int[]{12, 0} : new int[]{0, 12};
        for (int[] c : coarse) {
            // the median of the point and two 12 m either side along the runway: one cave mouth or pit does not count
            int[] gs = new int[3], depth = new int[3];
            for (int k = -1; k <= 1; k++) {
                int x = c[0] + k * along[0], z = c[1] + k * along[1];
                gs[k + 1] = probe.roughGroundY(x, z);
                depth[k + 1] = probe.roughSurfaceY(x, z) - gs[k + 1];
            }
            java.util.Arrays.sort(gs);
            java.util.Arrays.sort(depth);
            if (depth[1] > fill + ROUGH_M) { return "deep water"; }
            lo = Math.min(lo, gs[1]); hi = Math.max(hi, gs[1]);
        }
        if (hi - lo > cut + fill + ROUGH_M) { return "uneven (" + (hi - lo) + " m)"; }
        // the levelled ground on a grid on world-aligned points (samples shared by the lengths, axes and candidates):
        // first the runway's strip (its biomes, its median the levelled height), then the rest, stopping at the first
        // excess of water or of ground beyond the cut and fill
        java.util.List<int[]> main = new java.util.ArrayList<>(), approach = new java.util.ArrayList<>(), strip = new java.util.ArrayList<>();
        for (int x = Math.floorDiv(b.minX(), STEP) * STEP; x <= b.maxX(); x += STEP) {
            for (int z = Math.floorDiv(b.minZ(), STEP) * STEP; z <= b.maxZ(); z += STEP) {
                if (!AirportLayout.levelledAt(p, x, z)) { continue; }
                if (AirportLayout.approachOnly(p, x, z)) { approach.add(new int[]{x, z}); }
                else if (r.over(x + 0.5, z + 0.5, 20)) { strip.add(new int[]{x, z}); }
                else { main.add(new int[]{x, z}); }
            }
        }
        if (strip.isEmpty()) { return "no runway"; }
        int count = strip.size() + main.size(), allowedWet = (int) (WATER_SHARE * count), allowedOut = (int) (OUTLIERS * count);
        int wet = 0, other = 0, allowedOther = (int) (OTHER_BIOMES * strip.size());
        int[] stripHeights = new int[strip.size()];
        for (int i = 0; i < strip.size(); i++) {
            int x = strip.get(i)[0], z = strip.get(i)[1], sy = probe.surfaceY(x, z), g = probe.groundY(x, z);
            if (sy == g && !probe.suitableBiome(x, z) && ++other > allowedOther) { return "the runway's biome"; }
            if (sy != g && ++wet > allowedWet) { return "water"; }
            stripHeights[i] = g;
        }
        int[] sorted = stripHeights.clone();
        java.util.Arrays.sort(sorted);
        int y = sorted[sorted.length / 2], outside = 0;
        for (int i = 0; i < strip.size(); i++) {
            int x = strip.get(i)[0], z = strip.get(i)[1], g = stripHeights[i];
            if ((probe.surfaceY(x, z) - g > fill || g - y > cut || y - g > fill) && ++outside > allowedOut) { return "cut and fill on the runway"; }
        }
        for (int[] c : main) {
            int sy = probe.surfaceY(c[0], c[1]), g = probe.groundY(c[0], c[1]);
            if (sy != g && ++wet > allowedWet) { return "water"; }
            if ((sy - g > fill || g - y > cut || y - g > fill) && ++outside > allowedOut) { return "cut and fill"; }
        }
        int allowedApproach = (int) (2 * OUTLIERS * approach.size()), outsideApproach = 0;
        for (int[] c : approach) {
            int sy = probe.surfaceY(c[0], c[1]), g = probe.groundY(c[0], c[1]);
            if ((sy - g > 2 * fill || g - y > 2 * cut || y - g > 2 * fill) && ++outsideApproach > allowedApproach) { return "the approach's cut and fill"; }
        }
        AirportLayout.Bounds f = AirportLayout.footprint(p);
        if (probe.blocked(f.minX() - 16, f.minZ() - 16, f.maxX() + 16, f.maxZ() + 16)) { return "old chunks"; }
        for (int[] s : probe.settlements(b.minX() - SETTLEMENT_M, b.minZ() - SETTLEMENT_M, b.maxX() + SETTLEMENT_M, b.maxZ() + SETTLEMENT_M)) {
            if (AirportLayout.distanceToLevelled(p, s[0], s[1]) < SETTLEMENT_M) { return "a settlement at " + s[0] + " " + s[1]; }
        }
        return y;
    }

    /** A plan's (along, across) as block (x, z). */
    private static int[] at(Plan p, int along, int across) { return p.axis() == Runway.Axis.X ? new int[]{along, across} : new int[]{across, along}; }
}
