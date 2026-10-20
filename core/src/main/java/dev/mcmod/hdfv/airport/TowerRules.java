package dev.mcmod.hdfv.airport;

import java.util.List;

/**
 * What a tower sees and what it draws (0.20). Rules for a game's scale, not radio engineering:
 * <ul>
 *   <li>radar range grows with the square root of the antenna's height, as the radio horizon does
 *   (4.12 sqrt(h) km for a standard atmosphere), scaled down to the distances Minecraft keeps loaded: 600 m sqrt(h),
 *   at most 6 km;</li>
 *   <li>the cab sees aircraft on the ground and in the circuit as far as the visibility allows, and farther from higher
 *   up: 1500 m plus 60 m for each metre of eye height, at most the visibility;</li>
 *   <li>power: the radar 2.5 kW, the radios and consoles 0.4 kW, and the lights (AirfieldPower).</li>
 * </ul>
 */
public final class TowerRules {
    private TowerRules() {}

    public static final double RADAR_W = 2_500, CONSOLES_W = 400, MAX_RADAR_M = 6_000;

    public static double radarRangeM(double antennaHeightM) { return Math.min(MAX_RADAR_M, 600 * Math.sqrt(Math.max(1, antennaHeightM))); }

    public static double visualRangeM(double eyeHeightM, double visibilityM) { return Math.min(visibilityM, 1500 + 60 * Math.max(0, eyeHeightM)); }

    /** Full power of each light fitting (W, the lamps of a high-intensity fitting at step 5). */
    public static double fittingW(Fixture.Kind kind) {
        return switch (kind) {
            case RUNWAY_EDGE, THRESHOLD, PAPI -> 200;
            case RUNWAY_CENTRE -> 105;
            case APPROACH -> 300;
            case APPROACH_FLASHER -> 100;
            case TAXI_EDGE, TAXI_CENTRE -> 45;
            case OBSTRUCTION -> 50;
            case BEACON -> 1_000;
        };
    }

    /** The circuit a fitting is switched with (AirfieldLighting's numbering). */
    public static int circuit(Fixture.Kind kind) {
        return switch (kind) {
            case RUNWAY_EDGE, RUNWAY_CENTRE, THRESHOLD -> AirfieldLighting.RUNWAY;
            case APPROACH, APPROACH_FLASHER -> AirfieldLighting.APPROACH;
            case PAPI -> AirfieldLighting.PAPI;
            case TAXI_EDGE, TAXI_CENTRE -> AirfieldLighting.TAXIWAY;
            case OBSTRUCTION -> AirfieldLighting.OBSTRUCTION;
            case BEACON -> AirfieldLighting.BEACON;
        };
    }

    /**
     * The lights' draw (W) with each circuit at a share of full intensity (0 off). A constant-current regulator's lamps
     * give intensity roughly as the cube of the current and draw as its square, so power goes as intensity^(2/3).
     */
    public static double lightingW(List<Fixture> fixtures, double[] circuitIntensity) {
        double w = 0;
        for (Fixture f : fixtures) {
            double i = circuitIntensity[circuit(f.kind())];
            if (i > 0) { w += fittingW(f.kind()) * Math.pow(i, 2.0 / 3); }
        }
        return w;
    }

    /** Hours a store of energy (J) lasts at a load (W); infinite with no load. */
    public static double hours(double storedJ, double loadW) { return loadW <= 0 ? Double.POSITIVE_INFINITY : storedJ / loadW / 3600; }
}
