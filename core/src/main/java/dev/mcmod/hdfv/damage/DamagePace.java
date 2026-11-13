package dev.mcmod.hdfv.damage;

import dev.mcmod.hdfv.manufacture.Pace;

/**
 * 0.25: how fast the game's time wears things. Wear lives are real figures (an engine's hours to overhaul, a lining's
 * volume); the game compresses time as it does for work (Pace: an hour of work in 1000 ticks, 50 real seconds, so 72 to
 * one at the default), and wear and corrosion run on that same compressed clock times the server's factors
 * (HdfvConfig's damage.wearScale and damage.corrosionScale).
 *
 * <p>Parts are compiled in pure Java without the server's settings, so the numbers live here: the game sets them when
 * its server settings load or change (neoforge's VehicleDamage) and the designs read them while compiling (Wearings,
 * EngineWear). AeroCore keeps compiled parts by their design's JSON (PartTypes), so a changed pace reaches the designs
 * compiled after it, in practice after a restart; corrosion (which the game adds itself) follows at once.
 */
public final class DamagePace {
    private DamagePace() {}

    /** A real second of the physics (a tick is 0.05 s). */
    private static final double TICK_S = 0.05;
    /** How many seconds of wear a real second is at the default pace: 3600 s of work in 1000 ticks of 0.05 s, 72. */
    public static final double DEFAULT_COMPRESSION = compression(Pace.TICKS_PER_WORK_HOUR);

    private static volatile double compression = DEFAULT_COMPRESSION, wearScale = 1, corrosionScale = 1;

    /** The time compression of a pace (ticks an hour of work takes). */
    public static double compression(double ticksPerWorkHour) { return 3600 / (Math.max(ticksPerWorkHour, 1e-3) * TICK_S); }

    /** The game's settings: ticks an hour of work takes and the two factors (0: that kind of wear is off). */
    public static void set(double ticksPerWorkHour, double wear, double corrosion) {
        compression = compression(ticksPerWorkHour);
        wearScale = wear >= 0 && Double.isFinite(wear) ? wear : 1;
        corrosionScale = corrosion >= 0 && Double.isFinite(corrosion) ? corrosion : 1;
    }

    /** Back to the defaults (tests). */
    public static void reset() { compression = DEFAULT_COMPRESSION; wearScale = 1; corrosionScale = 1; }

    /** Seconds of wear a real second of use is (72 at the defaults; 0: parts do not wear). */
    public static double wear() { return compression * wearScale; }

    /** Seconds of corrosion a real second of standing is (72 at the defaults; 0: nothing corrodes). */
    public static double corrosion() { return compression * corrosionScale; }

    /** A real life or volume as the game's clock has it: divided by wear() (infinite when wear is off, so nothing wears). */
    public static double paced(double real) {
        double w = wear();
        return w > 0 ? real / w : Double.POSITIVE_INFINITY;
    }
}
