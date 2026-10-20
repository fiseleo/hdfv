package dev.mcmod.hdfv.airport;

/**
 * How a tower spaces departures (0.20), simplified from ICAO Doc 4444 5.8 (wake turbulence) for a game's scale: the
 * next take-off waits until the one before is airborne and either {@link #DEPARTURE_DISTANCE_M} away or
 * {@link #DEPARTURE_S} after it began its roll; two minutes behind a heavier category (a medium or light behind a heavy,
 * a light behind a medium). Arrivals: a landing clearance only when the one before has left the runway.
 */
public final class Separation {
    private Separation() {}

    public static final double DEPARTURE_S = 60, WAKE_S = 120, DEPARTURE_DISTANCE_M = 1_500;

    /** Seconds the next departure waits after the one before began its take-off. */
    public static double departureInterval(WakeCategory before, WakeCategory next) {
        return before.ordinal() > next.ordinal() ? WAKE_S : DEPARTURE_S;
    }

    /** Whether the wake rule alone asks for the time interval (a distance cannot stand in for it). */
    public static boolean wakeHolds(WakeCategory before, WakeCategory next) { return before.ordinal() > next.ordinal(); }
}
