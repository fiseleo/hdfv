package dev.mcmod.hdfv.airport;

import dev.mcmod.aerocore.api.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Runways: names, geometry, finding them from threshold paint, choosing the end in use, their markings. */
class RunwayTest {
    private static final Runway EAST_WEST = new Runway(Runway.Axis.X, 10, 100, 900, 64, 23, "");
    private static final Runway NORTH_SOUTH = new Runway(Runway.Axis.Z, -50, -400, 400, 64, 30, "");

    @Test void endsAreNamedForTheirTakeOffHeading() {
        assertEquals("09", EAST_WEST.designator(Runway.End.LOW));
        assertEquals("27", EAST_WEST.designator(Runway.End.HIGH));
        assertEquals("18", NORTH_SOUTH.designator(Runway.End.LOW), "taking off toward +Z is south");
        assertEquals("36", NORTH_SOUTH.designator(Runway.End.HIGH), "toward -Z is north, 36 not 00");
        assertEquals("09L/27R", new Runway(Runway.Axis.X, 0, 0, 800, 64, 23, "L").name(), "left from one end is right from the other");
        assertEquals(Runway.End.HIGH, EAST_WEST.end("27"));
        assertEquals(800, EAST_WEST.lengthM());
        // geometry: along from each end, offsets to the right of a take-off
        assertEquals(50, EAST_WEST.alongFrom(Runway.End.LOW, 150, 10), 1e-12);
        assertEquals(750, EAST_WEST.alongFrom(Runway.End.HIGH, 150, 10), 1e-12);
        assertEquals(5, EAST_WEST.offsetFrom(Runway.End.LOW, 150, 15), 1e-12, "east-bound: south is right");
        assertEquals(-5, EAST_WEST.offsetFrom(Runway.End.HIGH, 150, 15), 1e-12);
        assertEquals(7, NORTH_SOUTH.offsetFrom(Runway.End.LOW, -57, 0), 1e-12, "south-bound: west is right");
        assertTrue(EAST_WEST.over(500, 21, 0) && !EAST_WEST.over(500, 22, 0) && EAST_WEST.over(500, 22, 1));
        // how far from the pavement (0.21: a tower keeps to runways within 1 km of it)
        assertEquals(0, EAST_WEST.distanceTo(500, 15), 1e-12);
        assertEquals(10, EAST_WEST.distanceTo(500, 31.5), 1e-12, "beside it: from its edge");
        assertEquals(50, EAST_WEST.distanceTo(950, 10), 1e-12, "past its end");
        assertEquals(Math.hypot(30, 40), NORTH_SOUTH.distanceTo(-50 - 15 - 30, 400 + 40), 1e-12, "off a corner");
    }

    /** A threshold row of blocks across a runway end, facing in. */
    private static List<RunwayFinder.Cell> row(int along, int crossFrom, int width, int y, boolean alongX, int facing) {
        List<RunwayFinder.Cell> out = new ArrayList<>();
        for (int c = crossFrom; c < crossFrom + width; c++) {
            out.add(alongX ? new RunwayFinder.Cell(along, y, c, facing, 0) : new RunwayFinder.Cell(c, y, along, 0, facing));
        }
        return out;
    }

    @Test void twoThresholdRowsFacingEachOtherAreARunway() {
        List<RunwayFinder.Cell> cells = new ArrayList<>();
        cells.addAll(row(100, -1, 23, 63, true, 1));     // the west end, facing east
        cells.addAll(row(899, -1, 23, 63, true, -1));    // the east end, facing west
        cells.addAll(row(300, 200, 18, 63, false, 1));   // a lone threshold elsewhere: no runway
        List<Runway> found = RunwayFinder.find(cells);
        assertEquals(1, found.size());
        Runway r = found.get(0);
        assertEquals(Runway.Axis.X, r.axis());
        assertEquals(100, r.low(), 1e-12);
        assertEquals(900, r.high(), 1e-12, "to the outer edge of the far row");
        assertEquals(10.5, r.cross(), 1e-12);
        assertEquals(23, r.widthM(), 1e-12);
        assertEquals(64, r.y(), 1e-12, "the top of the blocks");
        assertEquals("09/27", r.name());
    }

    @Test void parallelRunwaysGetLetters() {
        List<RunwayFinder.Cell> cells = new ArrayList<>();
        cells.addAll(row(0, 0, 20, 63, true, 1));
        cells.addAll(row(799, 0, 20, 63, true, -1));
        cells.addAll(row(0, 300, 20, 63, true, 1));
        cells.addAll(row(799, 300, 20, 63, true, -1));
        List<Runway> found = RunwayFinder.find(cells);
        assertEquals(2, found.size());
        Runway north = found.stream().filter(r -> r.cross() < 100).findFirst().orElseThrow();
        Runway south = found.stream().filter(r -> r.cross() > 100).findFirst().orElseThrow();
        assertEquals("09L/27R", north.name(), "east-bound, the north one is on the left");
        assertEquals("09R/27L", south.name());
        // two airports' runways kilometres apart (or one past the other's end) are not one airfield's: no letters
        List<RunwayFinder.Cell> apart = new ArrayList<>();
        apart.addAll(row(0, 0, 20, 63, true, 1));
        apart.addAll(row(799, 0, 20, 63, true, -1));
        apart.addAll(row(0, 2400, 20, 63, true, 1));
        apart.addAll(row(799, 2400, 20, 63, true, -1));
        apart.addAll(row(3000, 100, 20, 63, true, 1));
        apart.addAll(row(3799, 100, 20, 63, true, -1));
        List<Runway> three = RunwayFinder.find(apart);
        assertEquals(3, three.size());
        for (Runway r : three) { assertEquals("09/27", r.name(), "unlettered: " + r); }
    }

    @Test void theRunwayInUseFacesTheWind() {
        List<Runway> both = List.of(EAST_WEST, NORTH_SOUTH);
        // wind from the west (blowing east): runway 27 (heading west, into it)
        RunwayChoice.State s = RunwayChoice.next(null, both, new Vec3d(8, 0, 0), 0);
        assertEquals("27", s.use().designator());
        assertEquals(8, RunwayChoice.headwind(s.use(), new Vec3d(8, 0, 0)), 1e-9);
        // the wind veers a little: no change before the dwell, even with a better end
        RunwayChoice.State later = RunwayChoice.next(s, both, new Vec3d(4, 0, -7), 100);
        assertSame(s, later, "only 100 s in use");
        later = RunwayChoice.next(s, both, new Vec3d(4, 0, -7), 400);
        assertEquals("18", later.use().designator(), "from the south-southwest after the dwell: 18 (7 m/s of headwind against 27's 4)");
    }

    @Test void aTailwindChangesTheEndAtOnceAndCalmKeepsIt() {
        List<Runway> one = List.of(EAST_WEST);
        RunwayChoice.State s = RunwayChoice.next(null, one, new Vec3d(6, 0, 0), 0);
        assertEquals("27", s.use().designator());
        assertSame(s, RunwayChoice.next(s, one, new Vec3d(-0.5, 0, 0.3), 10), "calm: keep");
        RunwayChoice.State turned = RunwayChoice.next(s, one, new Vec3d(-6, 0, 0), 20);
        assertEquals("09", turned.use().designator(), "6 m/s of tailwind (over 5 kt): change now");
        assertEquals(20, turned.sinceS());
    }

    @Test void markingsFollowAnnex14AndFindTheRunwayAgain() {
        Runway r = new Runway(Runway.Axis.X, 10.5, 0, 800, 64, 23, "");
        Map<Long, RunwayMarkings.Mark> marks = RunwayMarkings.of(r);
        assertEquals(6, RunwayMarkings.stripes(23));
        assertEquals(250, RunwayMarkings.aimingPointM(800));
        // the threshold patches are what a tower finds the runway by
        List<RunwayFinder.Cell> cells = new ArrayList<>();
        for (Map.Entry<Long, RunwayMarkings.Mark> e : marks.entrySet()) {
            RunwayMarkings.Mark m = e.getValue();
            if (m.paint() == RunwayMarkings.Paint.THRESHOLD) { cells.add(new RunwayFinder.Cell(RunwayMarkings.keyX(e.getKey()), 63, RunwayMarkings.keyZ(e.getKey()), m.dx(), m.dz())); }
        }
        assertEquals(2 * 36 * 23, cells.size(), "36 m by the width at each end");
        List<Runway> found = RunwayFinder.find(cells);
        assertEquals(List.of(r), found, "the markings name the same runway");
        // threshold stripes: across the runway 20 m past the west end, 6 stripes of painted cells
        int runs = 0;
        boolean painted = false;
        for (double z = -1 + 0.25; z < 22; z += 0.5) {
            boolean p = paintedAt(marks, 20.25, z);
            if (p && !painted) { runs++; }
            painted = p;
        }
        assertEquals(6 + 2, runs, "six stripes and the two side stripes");
        // the number 09 reads from the west 48-57 m in, the centre line dashes start after it
        assertTrue(paintedAt(marks, 48.25, 10.5 - 3.75 + 0.25), "the 0's bottom left");
        assertFalse(paintedAt(marks, 50.25, 10.5), "nothing on the centre line beside the digits");
        assertTrue(paintedAt(marks, 80.25, 10.25) && paintedAt(marks, 80.25, 10.75), "a centre line dash, 1 m wide");
        assertFalse(paintedAt(marks, 110.25, 10.25), "a gap");
        // the aiming point 250 m in, each side of the centre line
        assertTrue(paintedAt(marks, 260.25, 10.5 + 4.25) && paintedAt(marks, 260.25, 10.5 - 4.25));
        // all inside the runway
        for (long k : marks.keySet()) {
            assertTrue(r.over(RunwayMarkings.keyX(k) + 0.5, RunwayMarkings.keyZ(k) + 0.5, 0.6), "inside: " + RunwayMarkings.keyX(k) + " " + RunwayMarkings.keyZ(k));
        }
    }

    /** Whether the 0.5 m cell at a point is painted. */
    private static boolean paintedAt(Map<Long, RunwayMarkings.Mark> marks, double x, double z) {
        RunwayMarkings.Mark m = marks.get(RunwayMarkings.key((int) Math.floor(x), (int) Math.floor(z)));
        if (m == null) { return false; }
        int bit = (Math.floorMod((int) Math.floor(x * 2), 2)) | (Math.floorMod((int) Math.floor(z * 2), 2) << 1);
        return (m.mask() & (1 << bit)) != 0;
    }
}
