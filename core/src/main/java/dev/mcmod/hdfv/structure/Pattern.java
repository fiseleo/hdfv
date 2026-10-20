package dev.mcmod.hdfv.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A multiblock's shape (0.8; the user asked for multiblock CNC machines 2026-10-13): pure Java, as RoomScan is, so
 * it is tested without a game.
 *
 * <ul>
 * <li>Layers bottom to top; each layer's rows from the front (the controller's face) back; each row's characters
 *     left to right as one sees the machine from the front.</li>
 * <li>A legend gives each character its slot: the kinds of block it takes ("machine_bed|rotary_table": any of them)
 *     and whether it may be left empty (an optional module). ' ' takes anything; '.' must be empty (a working
 *     envelope). One character is the controller.</li>
 * <li>A range of layers above the controller may repeat between a least and a most count (a column's trays, a
 *     tank's rings); a match takes the most that are there.</li>
 * <li>(0.9) A range of columns may repeat across, left to right, in every row of every layer (a lathe's bed, a planer's
 *     table): a machine of a length. The controller is not in it. A match takes the longest that is there.</li>
 * <li>Four facings: 0 south, 1 west, 2 north, 3 east (the way the controller's front faces, Minecraft's horizontal
 *     index), the pattern turned about the controller.</li>
 * </ul>
 */
public final class Pattern {
    /** What a cell takes: kinds (any of), empty allowed. */
    public record Slot(Set<String> kinds, boolean optional) {
        public Slot { kinds = Set.copyOf(kinds); }
        public static Slot of(String kinds, boolean optional) { return new Slot(Set.of(kinds.split("\\|")), optional); }
        public boolean takes(String kind) { return kind == null ? optional : kinds.contains(kind); }
    }

    /** What is at a block: its kind (a pattern's name for it), null for an empty cell (air), anything else for another block. */
    public interface World { String kind(int x, int y, int z); }

    /** A cell in the world: its position, symbol, slot, and the kind found or to place. */
    public record Cell(int x, int y, int z, char symbol, String kind) {}

    /**
     * A formed pattern: its facing, repeats (layers), the cells holding blocks (the controller's too), how many of each
     * kind, and (0.9) its length (the columns' repeats across; 1 when nothing repeats across).
     */
    public record Match(int facing, int repeats, List<Cell> cells, Map<String, Integer> counts, int length) {
        public Match { cells = List.copyOf(cells); counts = Map.copyOf(counts); }
        public Match(int facing, int repeats, List<Cell> cells, Map<String, Integer> counts) { this(facing, repeats, cells, counts, 1); }
        public int count(String kind) { return counts.getOrDefault(kind, 0); }
        public boolean has(String kind) { return count(kind) > 0; }
    }

    private final String name;
    private final List<String[]> layers;
    private final Map<Character, Slot> legend;
    private final Map<Character, String> declared;
    private final char controller;
    private final int cx, cy, cz, repeatFrom, repeatTo, minRepeats, maxRepeats, acrossFrom, acrossTo, minLength, maxLength;

    private Pattern(String name, List<String[]> layers, Map<Character, Slot> legend, Map<Character, String> declared, char controller,
                    int repeatFrom, int repeatTo, int minRepeats, int maxRepeats, int acrossFrom, int acrossTo, int minLength, int maxLength) {
        this.name = name; this.layers = List.copyOf(layers); this.legend = Map.copyOf(legend); this.declared = Map.copyOf(declared);
        this.controller = controller; this.repeatFrom = repeatFrom; this.repeatTo = repeatTo; this.minRepeats = minRepeats; this.maxRepeats = maxRepeats;
        this.acrossFrom = acrossFrom; this.acrossTo = acrossTo; this.minLength = minLength; this.maxLength = maxLength;
        int[] at = null;
        for (int y = 0; y < layers.size(); y++) {
            String[] rows = layers.get(y);
            for (int z = 0; z < rows.length; z++) {
                for (int x = 0; x < rows[z].length(); x++) {
                    char c = rows[z].charAt(x);
                    if (c == controller) {
                        if (at != null) { throw new IllegalArgumentException(name + ": two controllers"); }
                        at = new int[]{x, y, z};
                    } else if (c != ' ' && c != '.' && !legend.containsKey(c)) { throw new IllegalArgumentException(name + ": no slot for '" + c + "'"); }
                }
            }
        }
        if (at == null) { throw new IllegalArgumentException(name + ": no controller"); }
        cx = at[0]; cy = at[1]; cz = at[2];
        if (repeatTo >= repeatFrom && repeatFrom <= cy) { throw new IllegalArgumentException(name + ": the repeated layers must be above the controller"); }
        if (acrossTo >= acrossFrom && cx >= acrossFrom && cx <= acrossTo) { throw new IllegalArgumentException(name + ": the controller is not in the repeated columns"); }
    }

    public String name() { return name; }
    public int layers() { return layers.size(); }
    public int minRepeats() { return minRepeats; }
    public int maxRepeats() { return maxRepeats; }
    /** The fewest and most lengths (columns' repeats across; 1 when nothing repeats across). */
    public int minLength() { return acrossTo < acrossFrom ? 1 : minLength; }
    public int maxLength() { return acrossTo < acrossFrom ? 1 : maxLength; }
    /** The kind of block a symbol stands for when placed (the first named; the controller's). */
    public String kindOf(char symbol) { return declared.get(symbol); }
    public Slot slot(char symbol) { return legend.get(symbol); }

    /** Unit vectors of the four facings (x, z). */
    private static final int[][] FRONT = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    /** The layer index of a built layer with a number of repeats. */
    private int sourceLayer(int built, int repeats) {
        if (repeatTo < repeatFrom || built < repeatFrom) { return built; }
        int span = repeatTo - repeatFrom + 1;
        if (built < repeatFrom + span * repeats) { return repeatFrom + (built - repeatFrom) % span; }
        return built - span * (repeats - 1);
    }

    /** A row as built with a length: the repeated columns that many times. */
    private String row(String row, int length) {
        if (acrossTo < acrossFrom || row.length() <= acrossFrom) { return row; }
        String span = row.substring(acrossFrom, Math.min(acrossTo + 1, row.length()));
        return row.substring(0, acrossFrom) + span.repeat(length) + (row.length() > acrossTo + 1 ? row.substring(acrossTo + 1) : "");
    }

    /** The controller's column with a length. */
    private int controllerColumn(int length) { return acrossTo < acrossFrom || cx < acrossFrom ? cx : cx + (acrossTo - acrossFrom + 1) * (length - 1); }

    /** Layers built with a number of repeats. */
    public int height(int repeats) { return repeatTo < repeatFrom ? layers.size() : layers.size() + (repeatTo - repeatFrom + 1) * (repeats - 1); }

    /**
     * Every cell of the pattern but the don't-cares, in the world: the controller at (x, y, z) facing a way, with a
     * number of repeats (1 when nothing repeats); kinds are the symbols' first kinds (the controller's its own).
     */
    public List<Cell> cells(int x, int y, int z, int facing, int repeats) { return cells(x, y, z, facing, repeats, 1); }

    /** The same, with a length (0.9: the columns' repeats across). */
    public List<Cell> cells(int x, int y, int z, int facing, int repeats, int length) { return cells(x, y, z, facing, repeats, length, 0, Integer.MAX_VALUE); }

    /** The cells of the built layers from..to (to excluded). */
    private List<Cell> cells(int x, int y, int z, int facing, int repeats, int length, int fromLayer, int toLayer) {
        int[] f = FRONT[Math.floorMod(facing, 4)];
        int ccx = controllerColumn(length);
        int dx = -f[0], dz = -f[1];   // from the viewer in front into the machine
        int rx = -dz, rz = dx;        // the viewer's right
        List<Cell> out = new ArrayList<>();
        int top = Math.min(height(repeats), toLayer);
        for (int by = Math.max(0, fromLayer); by < top; by++) {
            String[] rows = layers.get(sourceLayer(by, repeats));
            for (int lz = 0; lz < rows.length; lz++) {
                String built = row(rows[lz], length);
                for (int lx = 0; lx < built.length(); lx++) {
                    char c = built.charAt(lx);
                    if (c == ' ') { continue; }
                    int ox = lx - ccx, oz = lz - cz;
                    out.add(new Cell(x + ox * rx + oz * dx, y + by - cy, z + ox * rz + oz * dz, c, c == '.' ? null : declared.get(c)));
                }
            }
        }
        return out;
    }

    /**
     * The pattern formed round a controller at a position facing a way (the most repeats that are there), or null.
     * 0.20: with repeated layers it first counts how many repeat blocks stand one on another from the bottom and tries
     * only counts up to that (a count above it cannot match), so a tall tower is not matched once per possible height.
     */
    public Match match(World w, int x, int y, int z, int facing) {
        int most = repeatTo < repeatFrom ? 1 : maxRepeats, least = repeatTo < repeatFrom ? 1 : minRepeats;
        for (int length = maxLength(); length >= minLength(); length--) {
            int top = repeatTo < repeatFrom ? most : Math.min(most, repeatsStanding(w, x, y, z, facing, length, most));
            for (int r = top; r >= least; r--) {
                Match m = matchExactly(w, x, y, z, facing, r, length);
                if (m != null) { return m; }
            }
        }
        return null;
    }

    /** The brute-force match (every count from the most down): what {@link #match} must equal (tests). */
    Match matchSlowly(World w, int x, int y, int z, int facing) {
        int most = repeatTo < repeatFrom ? 1 : maxRepeats, least = repeatTo < repeatFrom ? 1 : minRepeats;
        for (int length = maxLength(); length >= minLength(); length--) {
            for (int r = most; r >= least; r--) {
                Match m = matchExactly(w, x, y, z, facing, r, length);
                if (m != null) { return m; }
            }
        }
        return null;
    }

    /** How many repeat blocks match one on another from the first (up to most), looking at their layers only. */
    private int repeatsStanding(World w, int x, int y, int z, int facing, int length, int most) {
        int span = repeatTo - repeatFrom + 1;
        for (int k = 0; k < most; k++) {
            for (Cell c : cells(x, y, z, facing, most, length, repeatFrom + k * span, repeatFrom + (k + 1) * span)) {
                if (!takes(c, w.kind(c.x(), c.y(), c.z()))) { return k; }
            }
        }
        return most;
    }

    /** Whether a cell takes what is there ('.' must be empty; the controller its own kind; a slot its kinds). */
    private boolean takes(Cell c, String kind) {
        if (c.symbol() == '.') { return kind == null; }
        return c.symbol() == controller ? Objects.equals(kind, declared.get(controller)) : legend.get(c.symbol()).takes(kind);
    }

    private Match matchExactly(World w, int x, int y, int z, int facing, int repeats, int length) {
        List<Cell> found = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Cell c : cells(x, y, z, facing, repeats, length)) {
            String kind = w.kind(c.x(), c.y(), c.z());
            if (!takes(c, kind)) { return null; }
            if (c.symbol() == '.') { continue; }
            if (kind != null) {
                found.add(new Cell(c.x(), c.y(), c.z(), c.symbol(), kind));
                counts.merge(kind, 1, Integer::sum);
            }
        }
        return new Match(facing, repeats, found, counts, length);
    }

    /** The pattern formed round a controller at a position, any facing (the given one first), or null. */
    public Match matchAnyFacing(World w, int x, int y, int z, int preferred) {
        for (int k = 0; k < 4; k++) {
            Match m = match(w, x, y, z, Math.floorMod(preferred + k, 4));
            if (m != null) { return m; }
        }
        return null;
    }

    public static Builder builder(String name) { return new Builder(name); }

    public static final class Builder {
        private final String name;
        private final List<String[]> layers = new ArrayList<>();
        private final Map<Character, Slot> legend = new LinkedHashMap<>();
        private final Map<Character, String> declared = new LinkedHashMap<>();
        private char controller;
        private int repeatFrom = 0, repeatTo = -1, minRepeats = 1, maxRepeats = 1, acrossFrom = 0, acrossTo = -1, minLength = 1, maxLength = 1;

        Builder(String name) { this.name = name; }

        /** A layer (the next one up): its rows from the front back. */
        public Builder layer(String... rows) { layers.add(Arrays.copyOf(rows, rows.length)); return this; }
        /** A symbol taking any of the kinds ("a|b"). */
        public Builder where(char symbol, String kinds) { legend.put(symbol, Slot.of(kinds, false)); declared.put(symbol, kinds.split("\\|")[0]); return this; }
        /** A symbol taking any of the kinds or nothing. */
        public Builder optional(char symbol, String kinds) { legend.put(symbol, Slot.of(kinds, true)); declared.put(symbol, kinds.split("\\|")[0]); return this; }
        /** The controller's symbol and kind. */
        public Builder controller(char symbol, String kind) { controller = symbol; declared.put(symbol, kind); return this; }
        /** Layers from..to (bottom 0) repeating between a least and a most count. */
        public Builder repeat(int from, int to, int least, int most) {
            if (to < from || least < 1 || most < least) { throw new IllegalArgumentException("Bad repeat"); }
            repeatFrom = from; repeatTo = to; minRepeats = least; maxRepeats = most;
            return this;
        }
        /** Columns from..to (left 0, as seen from the front) repeating across between a least and a most count (0.9). */
        public Builder repeatAcross(int from, int to, int least, int most) {
            if (to < from || from < 0 || least < 1 || most < least) { throw new IllegalArgumentException("Bad repeat across"); }
            acrossFrom = from; acrossTo = to; minLength = least; maxLength = most;
            return this;
        }
        public Pattern build() {
            return new Pattern(name, layers, legend, declared, controller, repeatFrom, repeatTo, minRepeats, maxRepeats, acrossFrom, acrossTo, minLength, maxLength);
        }
    }
}
