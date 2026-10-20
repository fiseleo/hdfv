package dev.mcmod.hdfv.manufacture;

/**
 * Where a size-variable machine tool keeps its parts (0.17): which way it grows, its sizes, its controller's and its
 * head's cells in its pattern, and the member symbols. 0.9 wrote these into the structure code, the renderer, the
 * screen, the JEI page and the tests one station at a time; with nine more families they live here.
 *
 * Cells are (column, layer, row) in the pattern (Structures): columns left to right as one faces the machine, layers
 * bottom up, rows front to back. A machine growing across repeats its column 1; one growing up repeats its layer 1.
 */
public enum MachineFrame {
    LATHE(Station.LATHE, Growth.ACROSS, 1, 6, 0, 1, 0, null, "b"),
    MILL(Station.MILLING_MACHINE, Growth.ACROSS, 1, 4, 0, 1, 0, null, "b"),
    PLANER(Station.PLANER, Growth.ACROSS, 1, 7, 1, 1, 0, null, "u"),
    BORING_MILL(Station.BORING_MILL, Growth.UP, 1, 4, 0, 0, 0, null, "K"),
    DRILL_PRESS(Station.DRILL_PRESS, Growth.UP, 1, 3, 0, 0, 0, null, "K"),
    GRINDER(Station.GRINDER, Growth.ACROSS, 1, 4, 0, 1, 0, 'h', "b"),
    FINISHER(Station.FINISHER, Growth.UP, 1, 3, 0, 0, 0, 'h', "K"),
    GEAR_CUTTER(Station.GEAR_CUTTER, Growth.UP, 1, 3, 0, 0, 0, 'h', "K"),
    RAM_MACHINE(Station.RAM_MACHINE, Growth.ACROSS, 1, 3, 0, 1, 0, 'h', "b"),
    SAWING_MACHINE(Station.SAWING_MACHINE, Growth.ACROSS, 1, 3, 0, 1, 0, 'h', "b"),
    ELECTRO_MACHINE(Station.ELECTRO_MACHINE, Growth.ACROSS, 1, 3, 0, 1, 0, 'h', "t"),
    CUTTING_TABLE(Station.CUTTING_TABLE, Growth.ACROSS, 1, 6, 0, 1, 0, 'h', "t"),
    THREADER(Station.THREADER, Growth.FIXED, 1, 1, 0, 1, 0, 'h', "b"),
    RIVETER(Station.RIVETER, Growth.ACROSS, 1, 3, 0, 1, 0, 'h', "b");

    public enum Growth { ACROSS, UP, FIXED }

    public final Station station;
    public final Growth growth;
    public final int minSize, maxSize;
    /** The controller's cell in the pattern. */
    public final int controllerCol, controllerLayer, controllerRow;
    /** The head's symbol (null: a 0.9 tool, no head). */
    public final Character head;
    /** Member symbols a test may take out to make it fall apart. */
    public final String members;

    MachineFrame(Station station, Growth growth, int minSize, int maxSize, int col, int layer, int row, Character head, String members) {
        this.station = station; this.growth = growth; this.minSize = minSize; this.maxSize = maxSize;
        this.controllerCol = col; this.controllerLayer = layer; this.controllerRow = row; this.head = head; this.members = members;
    }

    /** The frame of a station (null: not a size-variable machine tool). */
    public static MachineFrame of(Station s) {
        for (MachineFrame f : values()) { if (f.station == s) { return f; } }
        return null;
    }

    /** Its size from a pattern's match: its length across, its repeats up, or 1. */
    public int size(int length, int repeats) {
        return switch (growth) { case ACROSS -> length; case UP -> repeats; case FIXED -> 1; };
    }
}
