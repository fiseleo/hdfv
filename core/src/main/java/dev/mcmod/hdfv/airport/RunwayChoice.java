package dev.mcmod.hdfv.airport;

import dev.mcmod.aerocore.api.AirData;
import dev.mcmod.aerocore.api.Vec3d;

import java.util.List;

/**
 * Which runway end is in use (0.20): into the wind. A rule, as towers do it:
 * <ul>
 *   <li>the end with the most headwind (the longest runway when it is a tie within {@link #TIE_MPS});</li>
 *   <li>in calm air (below {@link #CALM_MPS}) the end in use stays;</li>
 *   <li>changed at once when the end in use has more than {@link #TAILWIND_MPS} of tailwind (5 kt, the usual limit
 *   for preferential runways, ICAO Doc 4444 7.2.3); otherwise only when another end has {@link #BETTER_MPS} more
 *   headwind and the end in use has been in use for {@link #DWELL_S} (the wind wanders; a runway change disrupts
 *   traffic).</li>
 * </ul>
 */
public final class RunwayChoice {
    private RunwayChoice() {}

    public static final double CALM_MPS = 1.5, TAILWIND_MPS = 5 * AviationUnits.KNOT_MPS, BETTER_MPS = 2, DWELL_S = 300, TIE_MPS = 0.5;

    /** The end in use since a time (s). */
    public record State(Runway.Use use, double sinceS) {}

    /** The end in use after this moment: the wind (the vector it blows toward, m/s) at the time (s). */
    public static State next(State state, List<Runway> runways, Vec3d windToward, double timeS) {
        if (runways.isEmpty()) { return null; }
        Runway.Use best = best(runways, windToward);
        if (state == null || !runways.contains(state.use().runway())) { return new State(best, timeS); }
        double speed = Math.hypot(windToward.x(), windToward.z());
        if (speed < CALM_MPS || best.equals(state.use())) { return state; }
        double now = headwind(state.use(), windToward), better = headwind(best, windToward);
        if (now < -TAILWIND_MPS) { return new State(best, timeS); }
        if (better - now >= BETTER_MPS && timeS - state.sinceS() >= DWELL_S) { return new State(best, timeS); }
        return state;
    }

    /** The end with the most headwind (ties: the longer runway). */
    public static Runway.Use best(List<Runway> runways, Vec3d windToward) {
        Runway.Use best = null;
        double bestWind = 0;
        for (Runway r : runways) {
            for (Runway.End e : Runway.End.values()) {
                Runway.Use u = new Runway.Use(r, e);
                double h = headwind(u, windToward);
                if (best == null || h > bestWind + TIE_MPS || (Math.abs(h - bestWind) <= TIE_MPS && r.lengthM() > best.runway().lengthM())) {
                    best = u;
                    bestWind = h;
                }
            }
        }
        return best;
    }

    /** Headwind (m/s) on a take-off or landing from an end in use. */
    public static double headwind(Runway.Use use, Vec3d windToward) {
        return AirData.components(windToward, Math.toRadians(use.headingDeg())).headwindMps();
    }

    /** Crosswind (m/s, positive from the right) on an end in use. */
    public static double crosswind(Runway.Use use, Vec3d windToward) {
        return AirData.components(windToward, Math.toRadians(use.headingDeg())).crosswindMps();
    }
}
