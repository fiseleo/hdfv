package dev.mcmod.hdfv.airport;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a planned airport is made of (0.20), worked out from its plan alone so each chunk can place its own part. 0.21:
 * three sizes (AirportTier); the numbers below are the large one's (0.20's), the others' are in their spec.
 * <ul>
 *   <li>a runway strip (the runway and 50 m of grass each side, 30 m past each end), a parallel taxiway 75 m from the
 *   runway's centre line on the plan's side with three links to the runway, an apron off the middle of the taxiway,
 *   a hangar and the control tower beyond the apron, and 150 m of approach-light strip before each end;</li>
 *   <li>all of it levelled to one height; a graded bank {@link #SLOPE_M} wide round it meets the ground (cuts and
 *   fills of up to 24 and 18 m: AirportPlanner);</li>
 *   <li>paint: the runway's (RunwayMarkings), yellow taxiway centre lines and holding position markings;</li>
 *   <li>lights: runway edge every 60 m, centre line every 15 m, threshold and end lights every 3 m, a PAPI on the left
 *   of each landing 300 m in (or a third of the runway), approach bars every 30 m with sequenced flashers on the centre
 *   line, blue taxiway edges every 30 m, the beacon and obstruction lights on the hangar;</li>
 *   <li>windsocks by both ends, holding signs, the tower (shaft courses from the plan), eight batteries and a bed in its
 *   base, its controller villager.</li>
 * </ul>
 * Coordinates are blocks; along/across are the runway's axis and the other one. The plan's surface y is the first air
 * block above pavement: pavement and grass are at y - 1, lights and things stand at y.
 */
public final class AirportLayout {
    private AirportLayout() {}

    public static final int APPROACH_HALF = 10, SLOPE_M = 32;

    public enum Ground { NONE, TURF, RUNWAY, TAXIWAY, APRON, HANGAR, TOWER }

    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
        public boolean meets(int x0, int z0, int x1, int z1) { return x1 >= minX && x0 <= maxX && z1 >= minZ && z0 <= maxZ; }
    }

    /** A rectangle along [a0, a1) and across [c0, c1) (blocks). */
    record Rect(int a0, int a1, int c0, int c1, Ground ground) {
        boolean contains(int a, int c) { return a >= a0 && a < a1 && c >= c0 && c < c1; }
        double distance(int a, int c) {
            double da = Math.max(Math.max(a0 - a, a - (a1 - 1)), 0), dc = Math.max(Math.max(c0 - c, c - (c1 - 1)), 0);
            return Math.hypot(da, dc);
        }
        Rect grown(int by) { return new Rect(a0 - by, a1 + by, c0 - by, c1 + by, ground); }
        Rect withGround(Ground g) { return new Rect(a0, a1, c0, c1, g); }
    }

    /** Things that are not lights: what, where (y the plan's surface plus), facing (0 south, 1 west, 2 north, 3 east). */
    public enum ThingKind { WINDSOCK, HOLD_SIGN, BATTERY, BED, VILLAGER, FLOODLIGHT, STAIR, COLUMN, GRATING, LAMP, DUCT, FLOOR }

    public record Thing(ThingKind kind, int x, int y, int z, int facing) {}

    /** The tower: its controller block, facing (toward the runway), shaft courses. */
    public record TowerSite(int x, int y, int z, int facing, int courses) {}

    /** The hangar: its floor rectangle (inclusive), its height, the side its door opens (dx, dz). */
    public record HangarSite(int minX, int minZ, int maxX, int maxZ, int height, int doorDx, int doorDz) {}

    // ---------------------------------------------------------------- geometry

    private static int centreCross(AirportPlanner.Plan p) { return p.cross() + p.tier().spec.widthM() / 2; }

    private static int x(AirportPlanner.Plan p, int a, int c) { return p.axis() == Runway.Axis.X ? a : c; }

    private static int z(AirportPlanner.Plan p, int a, int c) { return p.axis() == Runway.Axis.X ? c : a; }

    private static int along(AirportPlanner.Plan p, int x, int z) { return p.axis() == Runway.Axis.X ? x : z; }

    private static int across(AirportPlanner.Plan p, int x, int z) { return p.axis() == Runway.Axis.X ? z : x; }

    /** The rectangle from c to c + side * depth (either way round). */
    private static Rect span(int a0, int a1, int c, int side, int depth, Ground g) {
        return side > 0 ? new Rect(a0, a1, c, c + depth, g) : new Rect(a0, a1, c - depth + 1, c + 1, g);
    }

    /**
     * An airport's levelled rectangles by what they are (0.21; 0.20 took them from a list by index): the taxiway and the
     * tower are null where a tier has none. {@code all} is every rectangle, most important ground first (where they
     * overlap the first wins): the runway, taxiway, links, apron, hangar, tower, then the grass (strip, round the taxiway,
     * apron, hangar and tower), then the approach-light strips (0.20's order for the large airport).
     */
    record Parts(Rect runway, Rect taxi, List<Rect> links, Rect apron, Rect hangar, Rect tower, List<Rect> approaches, List<Rect> all) {}

    private static final Map<AirportPlanner.Plan, Parts> PARTS = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<AirportPlanner.Plan, Parts> e) { return size() > 16; }
    });

    static Parts parts(AirportPlanner.Plan p) { return PARTS.computeIfAbsent(p, AirportLayout::build); }

    /** The levelled rectangles, most important ground first (where they overlap the first wins). */
    static List<Rect> rects(AirportPlanner.Plan p) { return parts(p).all(); }

    /** The across coordinate (blocks) of the apron's middle and of the taxiway's centre line (null: none), for camera spots. */
    public static int apronAcross(AirportPlanner.Plan p) { Rect a = parts(p).apron(); return (a.c0() + a.c1()) / 2; }

    public static int apronAlong(AirportPlanner.Plan p) { Rect a = parts(p).apron(); return (a.a0() + a.a1()) / 2; }

    public static Integer taxiwayAcross(AirportPlanner.Plan p) { Rect t = parts(p).taxi(); return t == null ? null : (t.c0() + t.c1()) / 2; }

    private static Parts build(AirportPlanner.Plan p) {
        AirportTier.Spec t = p.tier().spec;
        int w = t.widthM(), s = p.start(), e = p.start() + p.lengthM(), c0 = p.cross(), c1 = p.cross() + w, cc = centreCross(p), side = p.side();
        int mid = s + p.lengthM() / 2;
        List<Rect> all = new ArrayList<>(), links = new ArrayList<>();
        Rect runway = new Rect(s, e, c0, c1, Ground.RUNWAY);
        all.add(runway);
        int linkFrom = side > 0 ? c1 : c0 - 1;
        Rect taxi = null;
        int apronC;
        if (t.taxiway()) {
            // the taxiway and its links
            int taxiC = cc + side * t.taxiOffset();
            taxi = new Rect(s + t.taxiInset(), e - t.taxiInset(), taxiC - t.taxiWidth() / 2, taxiC - t.taxiWidth() / 2 + t.taxiWidth(), Ground.TAXIWAY);
            all.add(taxi);
            int linkTo = side > 0 ? taxi.c0() : taxi.c1() - 1;
            int[] at = t.links() == 3 ? new int[]{s + t.linkInset(), s + p.lengthM() / 2, e - t.linkInset()} : new int[]{s + t.linkInset(), e - t.linkInset()};
            for (int a : at) {
                int lo = Math.min(linkFrom, linkTo), hi = Math.max(linkFrom, linkTo);
                links.add(new Rect(a - t.linkWidth() / 2, a - t.linkWidth() / 2 + t.linkWidth(), lo, hi + 1, Ground.TAXIWAY));
            }
            apronC = side > 0 ? taxi.c1() : taxi.c0() - 1;
        } else {
            // no taxiway: one link from the runway's edge straight to the apron
            apronC = linkFrom + side * t.apronGap();
            int lo = Math.min(linkFrom, apronC - side), hi = Math.max(linkFrom, apronC - side);
            links.add(new Rect(mid - t.linkWidth() / 2, mid - t.linkWidth() / 2 + t.linkWidth(), lo, hi + 1, Ground.TAXIWAY));
        }
        all.addAll(links);
        // the apron, the hangar and the tower beyond the apron
        Rect apron = span(mid - t.apronLong() / 2, mid + t.apronLong() / 2, apronC, side, t.apronDeep(), Ground.APRON);
        all.add(apron);
        int beyond = side > 0 ? apron.c1() : apron.c0() - 1;
        Rect hangar = span(mid + t.hangarAt(), mid + t.hangarAt() + t.hangarLong(), beyond, side, t.hangarDeep(), Ground.HANGAR);
        all.add(hangar);
        Rect tower = t.tower() ? span(mid + t.towerAt(), mid + t.towerAt() + 9, beyond, side, 9, Ground.TOWER) : null;
        if (tower != null) { all.add(tower); }
        // grass: the runway strip, round the taxiway and apron, the hangar and the tower
        all.add(new Rect(s - t.stripEnd(), e + t.stripEnd(), cc - w / 2 - t.stripSide(), cc + w / 2 + t.stripSide() + 1, Ground.TURF));
        if (taxi != null) { all.add(taxi.grown(6).withGround(Ground.TURF)); }
        all.add(apron.grown(6).withGround(Ground.TURF));
        all.add(span(mid + t.hangarAt() - 6, mid + t.hangarAt() + t.hangarLong() + 6, beyond, side, t.hangarDeep() + 6, Ground.TURF));
        if (tower != null) { all.add(span(mid + t.towerAt() - 6, mid + t.towerAt() + 15, beyond, side, 15, Ground.TURF)); }
        // the approach-light strips
        List<Rect> approaches = new ArrayList<>();
        if (t.approach() > 0) {
            approaches.add(new Rect(s - t.approach(), s, cc - APPROACH_HALF, cc + APPROACH_HALF + 1, Ground.TURF));
            approaches.add(new Rect(e, e + t.approach(), cc - APPROACH_HALF, cc + APPROACH_HALF + 1, Ground.TURF));
        }
        all.addAll(approaches);
        return new Parts(runway, taxi, List.copyOf(links), apron, hangar, tower, List.copyOf(approaches), List.copyOf(all));
    }

    /** The bounds of the levelled ground. */
    public static Bounds levelled(AirportPlanner.Plan p) {
        int a0 = Integer.MAX_VALUE, a1 = Integer.MIN_VALUE, c0 = Integer.MAX_VALUE, c1 = Integer.MIN_VALUE;
        for (Rect r : rects(p)) { a0 = Math.min(a0, r.a0()); a1 = Math.max(a1, r.a1() - 1); c0 = Math.min(c0, r.c0()); c1 = Math.max(c1, r.c1() - 1); }
        return p.axis() == Runway.Axis.X ? new Bounds(a0, c0, a1, c1) : new Bounds(c0, a0, c1, a1);
    }

    /** Everything the airport touches: the levelled ground and its slopes. */
    public static Bounds footprint(AirportPlanner.Plan p) {
        Bounds b = levelled(p);
        return new Bounds(b.minX() - SLOPE_M, b.minZ() - SLOPE_M, b.maxX() + SLOPE_M, b.maxZ() + SLOPE_M);
    }

    public static boolean levelledAt(AirportPlanner.Plan p, int x, int z) { return ground(p, x, z) != Ground.NONE; }

    /** Whether a column is levelled only for the approach lights (planned more loosely: AirportPlanner). */
    public static boolean approachOnly(AirportPlanner.Plan p, int x, int z) {
        int a = along(p, x, z), c = across(p, x, z);
        Parts parts = parts(p);
        for (Rect r : parts.all()) {
            if (parts.approaches().contains(r)) { continue; }
            if (r.contains(a, c)) { return false; }
        }
        for (Rect r : parts.approaches()) { if (r.contains(a, c)) { return true; } }
        return false;
    }

    /** What the ground is at a column (NONE outside the levelled ground). */
    public static Ground ground(AirportPlanner.Plan p, int x, int z) {
        int a = along(p, x, z), c = across(p, x, z);
        for (Rect r : rects(p)) { if (r.contains(a, c)) { return r.ground(); } }
        return Ground.NONE;
    }

    /** Distance (m) from a column to the levelled ground (0 on it): the slopes use it. */
    public static double distanceToLevelled(AirportPlanner.Plan p, int x, int z) {
        int a = along(p, x, z), c = across(p, x, z);
        double d = Double.MAX_VALUE;
        for (Rect r : rects(p)) { d = Math.min(d, r.distance(a, c)); }
        return d;
    }

    /**
     * The height (the first air block) a column is graded to: the plan's on the levelled ground; on the slope, between
     * it and the ground's own height there (smoothstep over {@link #SLOPE_M}); the ground's own beyond.
     */
    public static int gradedY(AirportPlanner.Plan p, int x, int z, int naturalY) {
        double d = distanceToLevelled(p, x, z);
        if (d <= 0) { return p.surfaceY(); }
        if (d >= SLOPE_M) { return naturalY; }
        double t = d / SLOPE_M, s = t * t * (3 - 2 * t);
        return (int) Math.round(p.surfaceY() + (naturalY - p.surfaceY()) * s);
    }

    // ---------------------------------------------------------------- paint

    private static final Map<AirportPlanner.Plan, Map<Long, RunwayMarkings.Mark>> MARKS = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<AirportPlanner.Plan, Map<Long, RunwayMarkings.Mark>> e) { return size() > 8; }
    });

    /** The painted blocks: block (x, z) packed by RunwayMarkings.key to its paint. */
    public static Map<Long, RunwayMarkings.Mark> marks(AirportPlanner.Plan p) { return MARKS.computeIfAbsent(p, AirportLayout::paint); }

    private static Map<Long, RunwayMarkings.Mark> paint(AirportPlanner.Plan p) {
        Map<Long, RunwayMarkings.Mark> out = new HashMap<>(RunwayMarkings.of(p.runway()));
        Parts parts = parts(p);
        Rect taxi = parts.taxi();
        if (taxi != null) {
            int taxiCentre = (taxi.c0() + taxi.c1()) / 2;
            // the taxiway's centre line: half a metre wide (one column of cells) on the block's near half
            for (int a = taxi.a0(); a < taxi.a1(); a++) { yellow(out, p, a, taxiCentre, true); }
        }
        // the links: a centre line, and a holding position (the large airport's 50 m from the runway's centre line: two
        // solid lines, two dashed)
        int cc = centreCross(p), side = p.side();
        for (Rect link : parts.links()) {
            int mid = (link.a0() + link.a1()) / 2;
            for (int c = Math.min(link.c0(), link.c1()); c < link.c1(); c++) { yellowAcross(out, p, mid, c); }
            int hold = cc + side * p.tier().spec.holdM();
            for (int a = link.a0(); a < link.a1(); a++) {
                yellow(out, p, a, hold, true);
                yellow(out, p, a, hold + side, true);
                if (Math.floorMod(a, 3) != 2) { yellow(out, p, a, hold + 2 * side, true); yellow(out, p, a, hold + 3 * side, true); }
            }
        }
        return Map.copyOf(out);
    }

    /** Paints yellow the two cells of a block nearer the smaller across (a line along). */
    private static void yellow(Map<Long, RunwayMarkings.Mark> out, AirportPlanner.Plan p, int a, int c, boolean along) {
        int x = x(p, a, c), z = z(p, a, c);
        int mask = p.axis() == Runway.Axis.X ? 0b0011 : 0b0101;   // cells at the smaller z (along X), the smaller x (along Z)
        add(out, x, z, mask);
    }

    /** Paints yellow the two cells of a block nearer the smaller along (a line across). */
    private static void yellowAcross(Map<Long, RunwayMarkings.Mark> out, AirportPlanner.Plan p, int a, int c) {
        int x = x(p, a, c), z = z(p, a, c);
        add(out, x, z, p.axis() == Runway.Axis.X ? 0b0101 : 0b0011);
    }

    private static void add(Map<Long, RunwayMarkings.Mark> out, int x, int z, int mask) {
        long k = RunwayMarkings.key(x, z);
        RunwayMarkings.Mark old = out.get(k);
        if (old != null && old.paint() != RunwayMarkings.Paint.YELLOW) { return; }
        out.put(k, new RunwayMarkings.Mark(RunwayMarkings.Paint.YELLOW, (old == null ? 0 : old.mask()) | mask, 0, 0));
    }

    // ---------------------------------------------------------------- lights and things

    /** Every light fitting (at the levelled surface). */
    public static List<Fixture> fixtures(AirportPlanner.Plan p) {
        List<Fixture> out = new ArrayList<>();
        AirportTier.Spec t = p.tier().spec;
        if (!t.lit()) { return out; }
        int s = p.start(), e = p.start() + p.lengthM(), c0 = p.cross(), c1 = p.cross() + t.widthM(), cc = centreCross(p), y = p.surfaceY();
        for (int a = s + 30; a < e - 20; a += 60) {
            out.add(fixture(p, Fixture.Kind.RUNWAY_EDGE, a, c0 - 1, y));
            out.add(fixture(p, Fixture.Kind.RUNWAY_EDGE, a, c1, y));
        }
        if (t.centreLights()) {
            for (int a = s + 7; a < e - 7; a += 15) { out.add(fixture(p, Fixture.Kind.RUNWAY_CENTRE, a, cc, y)); }
        }
        for (int c = c0; c < c1; c += 3) {
            out.add(fixture(p, Fixture.Kind.THRESHOLD, s - 1, c, y));
            out.add(fixture(p, Fixture.Kind.THRESHOLD, e, c, y));
        }
        int papiAt = Math.min(t.papiMaxM(), p.lengthM() / 3);
        // left of a landing from LOW (heading +along) is the smaller across along X (north), the larger across along Z (east)
        int leftLow = p.axis() == Runway.Axis.X ? -1 : 1;
        for (int u = 0; u < t.papiUnits(); u++) {
            int off = 15 + 9 * u;
            out.add(fixture(p, Fixture.Kind.PAPI, s + papiAt, leftLow > 0 ? c1 - 1 + off : c0 - off, y));
            out.add(fixture(p, Fixture.Kind.PAPI, e - 1 - papiAt, leftLow > 0 ? c0 - off : c1 - 1 + off, y));
        }
        for (int d = 30; d <= t.approach(); d += 30) {
            for (int k = -2; k <= 2; k++) {
                boolean flasher = k == 0 && d >= 90;
                Fixture.Kind kind = flasher ? Fixture.Kind.APPROACH_FLASHER : Fixture.Kind.APPROACH;
                out.add(fixture(p, kind, s - d, cc + 3 * k, y));
                out.add(fixture(p, kind, e - 1 + d, cc + 3 * k, y));
            }
        }
        Parts parts = parts(p);
        List<Rect> taxiways = new ArrayList<>();
        if (parts.taxi() != null) { taxiways.add(parts.taxi()); }
        taxiways.addAll(parts.links());
        for (Rect r : taxiways) {
            boolean alongTaxi = r == parts.taxi();
            if (alongTaxi) {
                for (int a = r.a0(); a < r.a1(); a += 30) {
                    out.add(fixture(p, Fixture.Kind.TAXI_EDGE, a, r.c0() - 1, y));
                    out.add(fixture(p, Fixture.Kind.TAXI_EDGE, a, r.c1(), y));
                }
            } else {
                for (int c = r.c0(); c < r.c1(); c += 15) {
                    out.add(fixture(p, Fixture.Kind.TAXI_EDGE, r.a0() - 1, c, y));
                    out.add(fixture(p, Fixture.Kind.TAXI_EDGE, r.a1(), c, y));
                }
            }
        }
        HangarSite h = hangar(p);
        int roof = y + h.height();
        out.add(new Fixture(Fixture.Kind.BEACON, (h.minX() + h.maxX()) / 2, roof + 1, (h.minZ() + h.maxZ()) / 2));
        for (int[] corner : new int[][]{{h.minX(), h.minZ()}, {h.maxX(), h.minZ()}, {h.minX(), h.maxZ()}, {h.maxX(), h.maxZ()}}) {
            out.add(new Fixture(Fixture.Kind.OBSTRUCTION, corner[0], roof + 1, corner[1]));
        }
        return out;
    }

    private static Fixture fixture(AirportPlanner.Plan p, Fixture.Kind kind, int a, int c, int y) { return new Fixture(kind, x(p, a, c), y, z(p, a, c)); }

    public static HangarSite hangar(AirportPlanner.Plan p) {
        Rect r = parts(p).hangar();
        int x0 = Math.min(x(p, r.a0(), r.c0()), x(p, r.a1() - 1, r.c1() - 1)), x1 = Math.max(x(p, r.a0(), r.c0()), x(p, r.a1() - 1, r.c1() - 1));
        int z0 = Math.min(z(p, r.a0(), r.c0()), z(p, r.a1() - 1, r.c1() - 1)), z1 = Math.max(z(p, r.a0(), r.c0()), z(p, r.a1() - 1, r.c1() - 1));
        // the door faces back toward the apron (and the runway)
        int dx = p.axis() == Runway.Axis.X ? 0 : -p.side(), dz = p.axis() == Runway.Axis.X ? -p.side() : 0;
        return new HangarSite(x0, z0, x1, z1, p.tier().spec.hangarHigh(), dx, dz);
    }

    /** The tower's controller: the middle of the near (runway) side of its rectangle, facing the runway; null without a tower. */
    public static TowerSite tower(AirportPlanner.Plan p) {
        Rect r = parts(p).tower();
        if (r == null) { return null; }
        int a = (r.a0() + r.a1()) / 2;
        // the controller's row is the second of the pattern's 9: one in from the rectangle's near edge
        int c = p.side() > 0 ? r.c0() + 1 : r.c1() - 2;
        int facing = p.axis() == Runway.Axis.X ? (p.side() > 0 ? 2 : 0) : (p.side() > 0 ? 1 : 3);
        return new TowerSite(x(p, a, c), p.surfaceY(), z(p, a, c), facing, p.towerCourses());
    }

    /** Windsocks, holding signs, apron floodlights and what is in the tower (towerInside). */
    public static List<Thing> things(AirportPlanner.Plan p) {
        List<Thing> out = new ArrayList<>();
        AirportTier.Spec spec = p.tier().spec;
        int s = p.start(), e = p.start() + p.lengthM(), cc = centreCross(p), y = p.surfaceY(), side = p.side();
        // windsocks on the strip on the far side from the apron, near the ends (the large airport's 41 m from the centre line)
        int sockC = cc - side * (spec.widthM() / 2 + spec.stripSide() * 3 / 5), sockA = s + Math.min(60, p.lengthM() / 6);
        out.add(new Thing(ThingKind.WINDSOCK, x(p, sockA, sockC), y, z(p, sockA, sockC), 0));
        if (spec.windsocks() > 1) { out.add(new Thing(ThingKind.WINDSOCK, x(p, e - 61, sockC), y, z(p, e - 61, sockC), 0)); }
        Parts parts = parts(p);
        int facingToRunway = p.axis() == Runway.Axis.X ? (side > 0 ? 2 : 0) : (side > 0 ? 1 : 3);
        if (spec.holdSigns()) {
            for (Rect link : parts.links()) {
                int signC = cc + side * (spec.holdM() - 3);
                out.add(new Thing(ThingKind.HOLD_SIGN, x(p, link.a0() - 2, signC), y, z(p, link.a0() - 2, signC), facingToRunway));
            }
        }
        TowerSite t = tower(p);
        if (t != null) { out.addAll(towerInside(t, spec.batteries())); }
        // floodlights at the apron's outer corners
        Rect apron = parts.apron();
        int outer = side > 0 ? apron.c1() - 1 : apron.c0();
        if (spec.floodlights() > 0) { out.add(new Thing(ThingKind.FLOODLIGHT, x(p, apron.a0(), outer), y, z(p, apron.a0(), outer), facingToRunway)); }
        if (spec.floodlights() > 1) { out.add(new Thing(ThingKind.FLOODLIGHT, x(p, apron.a1() - 1, outer), y, z(p, apron.a1() - 1, outer), facingToRunway)); }
        return out;
    }

    /**
     * The spiral stair's cells round its column (rows and columns as {@link #inside}): the shaft's inside less its middle,
     * in the order the stair climbs. Step k (from 0 on the base's floor) is at layer k in cell
     * {@code STAIR_RING[(TOP_CELL - top + k) mod 8]}, top = 2 + courses (the cab floor's layer): so whatever the height, the
     * top step is at TOP_CELL, the back of the hatch (the cab's renderer leaves its railing open there).
     * <p>
     * Each step faces the way one comes onto it (from the cell before): a corner's step is the straight run carried on, and
     * one turns off its high half onto the next step's low half. Facing the way out of the corner instead, its high half
     * lies across the way in, and a player (0.6 m wide, stepping 0.6 m) meets a whole block's rise: 0.21's first stair
     * could be neither climbed nor come down. A player on a step's low half has its head 3.3 above the step's layer, so
     * a step's cell is clear three layers up (hence the grating stays off the last four steps' cells).
     */
    public static final int[][] STAIR_RING = {{2, -1}, {2, 0}, {2, 1}, {3, 1}, {4, 1}, {4, 0}, {4, -1}, {3, -1}};
    public static final int TOP_CELL = 5;
    /** The stair's column, in the shaft's middle. */
    public static final int[] COLUMN = {3, 0};
    /** The hatch's cells covered with grating at the cab floor's layer (the stair's last four steps' cells stay open). */
    public static final int[] HATCH_COVER = {0, 1, 6, 7};

    /** The ring cell of a stair step (0 the bottom) of a tower of so many courses. */
    public static int stairCell(int courses, int step) { return Math.floorMod(TOP_CELL - (2 + courses) + step, 8); }

    /** The world facing of the step in a ring cell: the way one comes onto it, from the cell before (0 south, 1 west, 2 north, 3 east). */
    public static int stairFacing(TowerSite t, int cell) {
        int[] here = STAIR_RING[cell], before = STAIR_RING[Math.floorMod(cell - 1, 8)];
        return facing(t, here[0] - before[0], here[1] - before[1]);
    }

    /**
     * What is in a tower (0.21): in its base (5 x 5 inside, two blocks high) a lobby along the front (the doorway, the
     * controller villager's place at the desk), an equipment room behind and beside it (the batteries along the back wall
     * and one side, on cable ducts in the floor that run to the controller), a bed in the left back corner (its head, where
     * a villager must get to, toward the lobby), floor tiles,
     * two ceiling lamps; a steel spiral stair round a steel column up the shaft's inside to the cab's floor (see
     * STAIR_RING), wall lamps up the shaft (one every five layers, where no step's headroom is), grating over the rest of
     * the hatch.
     * Thing facings: 0 south, 1 west, 2 north, 3 east, 4 down (a lamp on a ceiling); a stair faces the way it climbs, a
     * wall lamp away from its wall.
     */
    public static List<Thing> towerInside(TowerSite t, int batteries) {
        List<Thing> out = new ArrayList<>();
        int y = t.y(), top = 2 + t.courses();
        java.util.Set<Long> ducts = new java.util.HashSet<>();
        // the equipment room: batteries along the back and the right side, each over a duct
        int[][] cells = batteries >= 8 ? new int[][]{{5, -2}, {5, -1}, {5, 0}, {5, 1}, {5, 2}, {2, 2}, {3, 2}, {4, 2}}
                : new int[][]{{5, -1}, {5, 0}, {5, 1}, {5, 2}};
        for (int i = 0; i < Math.min(batteries, cells.length); i++) {
            int[] w = inside(t, cells[i][0], cells[i][1]);
            out.add(new Thing(ThingKind.BATTERY, w[0], y, w[1], 0));
            ducts.add(key(cells[i][0], cells[i][1]));
        }
        // ducts from under the controller back along the middle, then under each battery's row or column
        for (int row = 0; row <= 5; row++) { ducts.add(key(row, 0)); }
        for (int col = -2; col <= 2; col++) { if (ducts.contains(key(5, col))) { for (int c = Math.min(0, col); c <= Math.max(0, col); c++) { ducts.add(key(5, c)); } } }
        for (int row = 2; row <= 4; row++) { if (ducts.contains(key(row, 2))) { for (int r = row; r <= 5; r++) { ducts.add(key(r, 2)); } } }
        for (int row = 0; row <= 5; row++) {
            for (int col = -2; col <= 2; col++) {
                if (row == 0 && col != 0) { continue; }   // the front wall: only the duct under the controller
                int[] w = inside(t, row, col);
                out.add(new Thing(ducts.contains(key(row, col)) ? ThingKind.DUCT : ThingKind.FLOOR, w[0], y - 1, w[1], 0));
            }
        }
        // the bed in the left back corner, its head toward the lobby: a villager claims a bed only if it can walk to within
        // a block of its head, and toward the back the second step (over the cell beside it) leaves no headroom
        int[] bed = inside(t, 4, -2);
        out.add(new Thing(ThingKind.BED, bed[0], y, bed[1], facing(t, -1, 0)));
        int[] stand = inside(t, 1, 0);
        out.add(new Thing(ThingKind.VILLAGER, stand[0], y, stand[1], 0));
        // two ceiling lamps
        for (int[] c : new int[][]{{1, 1}, {5, 0}}) {
            int[] w = inside(t, c[0], c[1]);
            out.add(new Thing(ThingKind.LAMP, w[0], y + 1, w[1], 4));
        }
        // the stair, its column, the grating round the top
        for (int k = 0; k <= top; k++) {
            int i = stairCell(t.courses(), k);
            int[] here = STAIR_RING[i], w = inside(t, here[0], here[1]);
            out.add(new Thing(ThingKind.STAIR, w[0], y + k, w[1], stairFacing(t, i)));
            int[] col = inside(t, COLUMN[0], COLUMN[1]);
            out.add(new Thing(ThingKind.COLUMN, col[0], y + k, col[1], 0));
        }
        for (int i : HATCH_COVER) {
            int[] w = inside(t, STAIR_RING[i][0], STAIR_RING[i][1]);
            out.add(new Thing(ThingKind.GRATING, w[0], y + top, w[1], 0));
        }
        // wall lamps up the shaft: four layers above a step in its cell (the next step there is four above the lamp)
        for (int level = 5; level <= top - 2; level += 5) {
            int i = stairCell(t.courses(), level - 4);
            int[] here = STAIR_RING[i], w = inside(t, here[0], here[1]);
            int dr = COLUMN[0] - here[0], dc = COLUMN[1] - here[1];
            out.add(new Thing(ThingKind.LAMP, w[0], y + level, w[1], dr != 0 ? facing(t, Integer.signum(dr), 0) : facing(t, 0, Integer.signum(dc))));
        }
        return out;
    }

    private static long key(int row, int col) { return ((long) row << 32) ^ (col & 0xFFFFFFFFL); }

    /** The world facing (0 south, 1 west, 2 north, 3 east) of a step of rows and columns in a tower (as inside). */
    public static int facing(TowerSite t, int drow, int dcol) {
        int[] o = inside(t, 0, 0), d = inside(t, drow, dcol);
        int dx = d[0] - o[0], dz = d[1] - o[1];
        return dz > 0 ? 0 : dx < 0 ? 1 : dz < 0 ? 2 : 3;
    }

    /**
     * A column inside the tower, as world (x, z): rows back from the controller (1 to 5 are the base's inside), columns
     * to the right of someone facing the tower's front (-2 to 2). The shaft's inside is rows 2 to 4, columns -1 to 1.
     */
    public static int[] inside(TowerSite t, int row, int col) {
        int[][] front = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        int fx = front[t.facing()][0], fz = front[t.facing()][1];   // the way the front faces
        int bx = -fx, bz = -fz, rx = -bz, rz = bx;                   // into the tower; the viewer's right
        return new int[]{t.x() + row * bx + col * rx, t.z() + row * bz + col * rz};
    }

    /** A digest of everything a plan lays out (the freeze test: a version's layout never changes). */
    public static long digest(AirportPlanner.Plan p) {
        long h = 1469598103934665603L;
        for (Fixture f : fixtures(p)) { h = mix(h, f.kind().ordinal(), f.x(), f.y(), f.z()); }
        for (Thing t : things(p)) { h = mix(h, t.kind().ordinal(), t.x(), t.y(), t.z() * 4 + t.facing()); }
        return digestGround(p, h);
    }

    /**
     * A digest of what an airport's chunks must agree on whatever generates them: its ground, paint, lights, hangar and
     * tower site (not what stands in the tower's base, which 0.21 furnished anew).
     */
    public static long groundDigest(AirportPlanner.Plan p) {
        long h = 1469598103934665603L;
        for (Fixture f : fixtures(p)) { h = mix(h, f.kind().ordinal(), f.x(), f.y(), f.z()); }
        return digestGround(p, h);
    }

    private static long digestGround(AirportPlanner.Plan p, long h) {
        java.util.TreeMap<Long, RunwayMarkings.Mark> sorted = new java.util.TreeMap<>(marks(p));
        for (Map.Entry<Long, RunwayMarkings.Mark> m : sorted.entrySet()) { h = mix(h, m.getValue().paint().ordinal(), m.getValue().mask(), (int) (m.getKey() >> 32), (int) (long) m.getKey()); }
        Bounds b = footprint(p);
        for (int x = b.minX(); x <= b.maxX(); x += 7) {
            for (int z = b.minZ(); z <= b.maxZ(); z += 7) { h = mix(h, ground(p, x, z).ordinal(), gradedY(p, x, z, p.surfaceY() + 5), x, z); }
        }
        TowerSite t = tower(p);
        HangarSite g = hangar(p);
        if (t != null) { h = mix(h, t.x(), t.z(), t.facing(), t.courses()); }
        return mix(h, g.minX(), g.minZ(), g.maxX(), g.maxZ());
    }

    private static long mix(long h, int a, int b, int c, int d) {
        for (int v : new int[]{a, b, c, d}) { h ^= v; h *= 1099511628211L; }
        return h;
    }
}
