package dev.mcmod.hdfv.drive;

/**
 * A transfer case's setting as the driver picks it with the drive-mode key (0.14): the channel values it sends and
 * the name the dashboard shows. A part-time case's "mode" (0 2H, 1 4H, 2 4L); a full-time or on-demand case's "range"
 * (0 high, 1 low) and a full-time case's centre lock.
 */
public enum DriveMode {
    TWO_HIGH("2H", 0, 0, false), FOUR_HIGH("4H", 1, 0, false), FOUR_LOW("4L", 2, 1, false),
    HIGH("4H", 0, 0, false), HIGH_LOCKED("4H", 0, 0, true), LOW_LOCKED("4L", 0, 1, true), LOW("4L", 0, 1, false),
    AUTO("AUTO", 0, 0, false);

    /** The dashboard's name; the part-time "mode"; the "range" (1 low); whether the centre differential is locked. */
    public final String label;
    public final int mode, range;
    public final boolean centreLocked;

    DriveMode(String label, int mode, int range, boolean centreLocked) { this.label = label; this.mode = mode; this.range = range; this.centreLocked = centreLocked; }

    /** Whether it asks for the low range. */
    public boolean low() { return range == 1 || mode == 2; }
}
