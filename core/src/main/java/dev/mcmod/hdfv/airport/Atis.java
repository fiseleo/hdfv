package dev.mcmod.hdfv.airport;

/**
 * An aerodrome's automatic terminal information (0.20): the runway in use, the wind (two-minute mean and the highest
 * 3 s gust in ten minutes, reported only when 10 kt or more above the mean), visibility, rain or a thunderstorm,
 * temperature and QNH, under a letter that moves on (A to Z, then A) whenever something significant changes, so a
 * pilot can say which report they have (ICAO Annex 11 4.3.6; the change criteria after Annex 3's special reports).
 * <p>
 * The world's air is the standard atmosphere: QNH is always 1013 hPa and the temperature the ISA's at the field's
 * height (the same air the aircraft fly in). Only numbers: the words are the client's language files.
 */
public record Atis(char letter, String runway, int windFromDeg, double windMps, double gustMps, double visibilityM, boolean rain, boolean thunder,
                   int temperatureC, int qnhHpa) {

    /** The report for these conditions under a letter (wind from: degrees, reported as AviationUnits.windFrom). */
    public static Atis of(char letter, String runway, double windFromDeg, double windMps, double peakGustMps, double visibilityM, boolean rain, boolean thunder,
                          double temperatureK, double qnhPa) {
        return new Atis(letter, runway, AviationUnits.windFrom(windFromDeg, windMps), windMps, AviationUnits.reportedGust(windMps, peakGustMps),
                visibilityM, rain, thunder, AviationUnits.celsius(temperatureK), AviationUnits.hectopascals(qnhPa));
    }

    /** The same report under another letter. */
    public Atis lettered(char l) { return new Atis(l, runway, windFromDeg, windMps, gustMps, visibilityM, rain, thunder, temperatureC, qnhHpa); }

    /** The letter after another (Z wraps to A). */
    public static char after(char letter) { return letter >= 'Z' || letter < 'A' ? 'A' : (char) (letter + 1); }

    /**
     * Whether a pilot holding the old report needs the new one: another runway, rain or thunder starting or stopping,
     * a mean wind change of 10 kt or more, a direction change of 60 degrees or more with 10 kt or more, a gust appearing,
     * going or changing by 10 kt, visibility crossing 800, 1500, 3000 or 5000 m, the QNH or the temperature changing.
     */
    public static boolean significant(Atis old, Atis now) {
        if (old == null) { return true; }
        if (!old.runway.equals(now.runway) || old.rain != now.rain || old.thunder != now.thunder) { return true; }
        double kt10 = 10 * AviationUnits.KNOT_MPS;
        if (Math.abs(old.windMps - now.windMps) >= kt10) { return true; }
        if (Math.max(old.windMps, now.windMps) >= kt10 && old.windFromDeg != 0 && now.windFromDeg != 0) {
            int d = Math.abs(old.windFromDeg - now.windFromDeg) % 360;
            if (Math.min(d, 360 - d) >= 60) { return true; }
        }
        if ((old.gustMps > 0) != (now.gustMps > 0) || Math.abs(old.gustMps - now.gustMps) >= kt10) { return true; }
        if (band(old.visibilityM) != band(now.visibilityM)) { return true; }
        return old.qnhHpa != now.qnhHpa || old.temperatureC != now.temperatureC;
    }

    private static int band(double v) {
        double[] bounds = {800, 1500, 3000, 5000};
        int b = 0;
        while (b < bounds.length && v >= bounds[b]) { b++; }
        return b;
    }

    /** The report now under the right letter: the old letter if nothing significant changed, else the next. */
    public static Atis update(Atis old, Atis now) {
        if (old == null) { return now.lettered('A'); }
        return significant(old, now) ? now.lettered(after(old.letter)) : old;
    }
}
