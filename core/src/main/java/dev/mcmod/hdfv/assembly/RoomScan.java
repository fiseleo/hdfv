package dev.mcmod.hdfv.assembly;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds an assembly room from its controller (0.7, the user: "組裝室是一個結構", a flexible room): the air inside, flood
 * filled from the cell in front of the controller and bounded by walls, the assembly floor (or a slipway), doors, the
 * crane above and the welding stations at its sides. The world is seen through a Grid, so the rules are tested
 * without a game. One cell is one metre.
 */
public final class RoomScan {
    private RoomScan() {}

    /** What a cell is to a room. */
    public enum Cell {
        /** Open space (air, and anything a person walks through). */
        AIR,
        /** The assembly floor. */
        FLOOR,
        /** Anything else solid: walls, roof, ground. */
        WALL,
        /** A hangar door, roller shutter or dock gate: part of the wall that opens. */
        DOOR,
        /** An overhead crane's rail. */
        CRANE,
        /** A shipyard's slipway (a floor that runs into the water). */
        SLIPWAY,
        WATER,
        CONTROLLER,
        WELDING_STATION,
        /** A chest, barrel or anything holding items: where a room keeps what it builds from. */
        STORAGE
    }

    public interface Grid { Cell at(int x, int y, int z); }

    /** A door: a connected patch of door cells in a wall, its opening and which way it faces out. */
    public record Door(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int outX, int outZ, boolean apronClear, boolean waterOutside,
                       boolean groundOutside) {
        public int widthM() { return outX != 0 ? maxZ - minZ + 1 : maxX - minX + 1; }
        public int heightM() { return maxY - minY + 1; }
    }

    /**
     * The room: whether it closes (else where it leaks), its cells and bounds, how many of its lowest cells stand on no
     * floor, its doors, crane rail cells over it, slipway cells under it, the welding stations and the storage at it
     * (cells as {x, y, z}).
     */
    public record Scan(boolean closed, int leakX, int leakY, int leakZ, int cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                       int floorMissing, List<Door> doors, int craneCells, int slipwayCells, List<int[]> stations, List<int[]> storage) {
        public int lengthX() { return maxX - minX + 1; }
        public int heightY() { return maxY - minY + 1; }
        public int widthZ() { return maxZ - minZ + 1; }
        public int weldingStations() { return stations.size(); }
        /** Whether a point (block coordinates) is inside the room's bounds. */
        public boolean holds(double x, double y, double z) { return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1; }
    }

    /** Largest room scanned: across and up from the controller, m; and the depth of a hangar's apron and a shipyard's water, m. */
    public static final int REACH = 64, HEIGHT = 32, APRON = 8, WATER = 3;
    /** Most air a room holds (a 46 m cube); more is open country. */
    public static final int MAX_CELLS = 100_000;

    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Scans the room in front of a controller at (x, y, z) facing (fx, fz) into it. */
    public static Scan scan(Grid g, int x, int y, int z, int fx, int fz) {
        int sx = x + fx, sz = z + fz;
        Set<Long> inside = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        if (g.at(sx, y, sz) == Cell.AIR) { inside.add(key(sx, y, sz)); queue.add(new int[]{sx, y, sz}); }
        int minX = sx, minY = y, minZ = sz, maxX = sx, maxY = y, maxZ = sz;
        Set<Long> doorCells = new HashSet<>(), crane = new HashSet<>(), stations = new HashSet<>(), slipway = new HashSet<>(), storage = new HashSet<>();
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (Math.abs(c[0] - x) > REACH || Math.abs(c[2] - z) > REACH || c[1] - y > HEIGHT || y - c[1] > HEIGHT || inside.size() > MAX_CELLS) {
                return new Scan(false, c[0], c[1], c[2], inside.size(), 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 0, List.of(), List.of());
            }
            minX = Math.min(minX, c[0]); minY = Math.min(minY, c[1]); minZ = Math.min(minZ, c[2]);
            maxX = Math.max(maxX, c[0]); maxY = Math.max(maxY, c[1]); maxZ = Math.max(maxZ, c[2]);
            for (int[] s : SIDES) {
                int nx = c[0] + s[0], ny = c[1] + s[1], nz = c[2] + s[2];
                long k = key(nx, ny, nz);
                if (inside.contains(k)) { continue; }
                Cell cell = g.at(nx, ny, nz);
                switch (cell) {
                    case AIR -> { inside.add(k); queue.add(new int[]{nx, ny, nz}); }
                    case DOOR -> doorCells.add(k);
                    case CRANE -> { if (s[1] == 1) { crane.add(k); } }
                    case WELDING_STATION -> stations.add(k);
                    case STORAGE -> storage.add(k);
                    case SLIPWAY -> { if (s[1] == -1) { slipway.add(k); } }
                    default -> { }
                }
            }
        }
        if (inside.isEmpty()) { return new Scan(false, sx, y, sz, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 0, List.of(), List.of()); }
        // the lowest layer stands on the floor
        int missing = 0;
        for (long k : inside) {
            int cx = x(k), cy = y(k), cz = z(k);
            if (cy != minY) { continue; }
            Cell below = g.at(cx, cy - 1, cz);
            if (below != Cell.FLOOR && below != Cell.SLIPWAY && below != Cell.WELDING_STATION) { missing++; }
        }
        // doors: connected patches of door cells
        List<Door> doors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long d : doorCells) {
            if (!seen.add(d)) { continue; }
            List<Long> patch = new ArrayList<>(List.of(d));
            ArrayDeque<Long> q = new ArrayDeque<>(patch);
            while (!q.isEmpty()) {
                long c = q.poll();
                for (int[] s : SIDES) {
                    long n = key(x(c) + s[0], y(c) + s[1], z(c) + s[2]);
                    if (!seen.contains(n) && g.at(x(n), y(n), z(n)) == Cell.DOOR) { seen.add(n); patch.add(n); q.add(n); }
                }
            }
            doors.add(door(g, patch, inside));
        }
        return new Scan(true, 0, 0, 0, inside.size(), minX, minY, minZ, maxX, maxY, maxZ, missing, doors, crane.size(), slipway.size(), cells(stations), cells(storage));
    }

    private static Door door(Grid g, List<Long> patch, Set<Long> inside) {
        int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
        for (long c : patch) {
            x0 = Math.min(x0, x(c)); y0 = Math.min(y0, y(c)); z0 = Math.min(z0, z(c));
            x1 = Math.max(x1, x(c)); y1 = Math.max(y1, y(c)); z1 = Math.max(z1, z(c));
        }
        // the wall's normal is the thin side; out is the side away from the room
        int outX = 0, outZ = 0;
        if (x1 - x0 <= z1 - z0) { outX = inside.contains(key(x0 - 1, y0, z0)) ? 1 : -1; }
        else { outZ = inside.contains(key(x0, y0, z0 - 1)) ? 1 : -1; }
        int fx = outX > 0 ? x1 : x0, fz = outZ > 0 ? z1 : z0;
        boolean apron = true, water = true, ground = true;
        for (int d = 1; d <= APRON; d++) {
            for (int a = (outX != 0 ? z0 : x0); a <= (outX != 0 ? z1 : x1); a++) {
                int cx = outX != 0 ? fx + outX * d : a, cz = outX != 0 ? a : fz + outZ * d;
                for (int cy = y0; cy <= y1; cy++) { apron &= g.at(cx, cy, cz) == Cell.AIR; }
                if (d <= WATER) {
                    Cell sill = g.at(cx, y0, cz), under = g.at(cx, y0 - 1, cz);
                    water &= sill == Cell.WATER || under == Cell.WATER;
                    ground &= under != Cell.AIR && under != Cell.WATER;
                }
            }
        }
        return new Door(x0, y0, z0, x1, y1, z1, outX, outZ, apron, water, ground);
    }

    private static List<int[]> cells(Set<Long> keys) {
        List<int[]> out = new ArrayList<>();
        for (long k : keys) { out.add(new int[]{x(k), y(k), z(k)}); }
        return out;
    }

    static long key(int x, int y, int z) { return ((long) (x & 0x3ffffff) << 38) | ((long) (z & 0x3ffffff) << 12) | (y & 0xfff); }
    static int x(long k) { return (int) (k >> 38) << 6 >> 6; }
    static int z(long k) { return (int) ((k >> 12) & 0x3ffffff) << 6 >> 6; }
    static int y(long k) { return (int) (k & 0xfff) << 20 >> 20; }
}
