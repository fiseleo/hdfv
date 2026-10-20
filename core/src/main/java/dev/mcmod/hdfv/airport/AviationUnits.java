package dev.mcmod.hdfv.airport;

/**
 * Numbers as a tower and a pilot say them (0.20): heights in feet, speeds in knots, pressure in hectopascals, wind as
 * the bearing it blows from to the nearest 10 degrees (360 for north, 0 when calm) and its speed; or, by the client's
 * setting, metres, kilometres an hour and metres a second. The words around the numbers are in the language files.
 */
public final class AviationUnits {
    private AviationUnits() {}

    public enum System { AVIATION, METRIC }

    public static final double FOOT_M = 0.3048, KNOT_MPS = 1852.0 / 3600;
    /** Wind below this (m/s, 1 kt) is reported calm (ICAO Annex 3, 4.1.5). */
    public static final double CALM_MPS = 0.5 * KNOT_MPS + 1e-9;

    public static double feet(double m) { return m / FOOT_M; }

    public static double knots(double mps) { return mps / KNOT_MPS; }

    /** A height to report: whole feet rounded to 10 (aviation), or whole metres. */
    public static long height(double m, System s) { return s == System.AVIATION ? Math.round(feet(m) / 10) * 10 : Math.round(m); }

    /** A speed to report: knots, or km/h. */
    public static long speed(double mps, System s) { return s == System.AVIATION ? Math.round(knots(mps)) : Math.round(mps * 3.6); }

    /** A wind speed to report: knots, or m/s. */
    public static long windSpeed(double mps, System s) { return s == System.AVIATION ? Math.round(knots(mps)) : Math.round(mps); }

    /** The bearing a wind is reported from: to the nearest 10 degrees, 360 for north, 0 when calm. */
    public static int windFrom(double fromDeg, double speedMps) {
        if (speedMps < CALM_MPS) { return 0; }
        int d = (int) Math.round(((fromDeg % 360) + 360) % 360 / 10.0) * 10;
        return d == 0 ? 360 : d;
    }

    /** A gust is reported when it exceeds the mean by 10 kt or more (ICAO Annex 3, 4.1.5.2 c); else 0. */
    public static double reportedGust(double meanMps, double gustMps) { return gustMps - meanMps >= 10 * KNOT_MPS ? gustMps : 0; }

    /** Pressure to report: whole hectopascals, rounded down (ICAO Annex 3, 4.7: QNH is rounded down). */
    public static int hectopascals(double pa) { return (int) Math.floor(pa / 100 + 1e-9); }

    /** Temperature to report: whole degrees Celsius. */
    public static int celsius(double kelvin) { return (int) Math.round(kelvin - 273.15); }

    /** Visibility to report: metres to the nearest 100 below 5 km, kilometres up to 10 km, then 10 km ("10 km or more"). */
    public static long visibility(double m) {
        if (m < 5_000) { return Math.round(m / 100) * 100; }
        return Math.min(10_000, Math.round(m / 1000) * 1000);
    }

    /** A compass heading to say: 1 to 360 (never 0). */
    public static int heading(double deg) {
        int d = (int) Math.round(((deg % 360) + 360) % 360);
        return d == 0 ? 360 : d;
    }
}
