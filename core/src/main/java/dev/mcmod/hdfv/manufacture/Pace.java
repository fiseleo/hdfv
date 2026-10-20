package dev.mcmod.hdfv.manufacture;

/**
 * The game's two scales (0.7, the user's choice 2026-10-13): how much a metal ingot weighs (10 kg; a nugget 1 kg) and
 * how a working hour passes (an hour's real work in a Minecraft hour, 1000 ticks). Both are server settings; these
 * are the defaults. Every cost comes from the physics through these two numbers only.
 */
public final class Pace {
    private Pace() {}

    public static final double KG_PER_INGOT = 10, TICKS_PER_WORK_HOUR = 1000;

    /** Seconds of work a tick is at a pace (ticks per hour): 3.6 at the default (0.8: energy, fuel and fluids run on this clock; physics on real time). */
    public static double workSecondsPerTick(double ticksPerHour) { return 3600 / ticksPerHour; }

    /** Ticks a working time takes at a pace (ticks per hour), at least one. */
    public static long ticks(double seconds, double ticksPerHour) { return Math.max(1, Math.round(seconds / 3600 * ticksPerHour)); }

    /** Nuggets (a tenth of an ingot at the default) a mass of stock takes, rounded up. */
    public static int nuggets(double kg, double kgPerIngot) { return (int) Math.ceil(Math.max(kg, 0) / (kgPerIngot / 10) - 1e-9); }
}
