package dev.mcmod.hdfv.petroleum;

import dev.mcmod.aerocore.api.petroleum.Lift;

/**
 * Drilling and pumping a well (0.8).
 *
 * <ul>
 * <li>The rig turns a 0.2 m bit with RIG_W (a light rotary rig's 75 kW drawworks and rotary), at Teale's rate for the
 *     field's rock; it runs steel casing down the hole as it goes, CASING_KG_PER_M (2 7/8 in tubing set as casing, a
 *     slim hole: the steel a real well takes, at the game's scale, would be tens of tonnes).</li>
 * <li>A beam pump jack: a 1.75 in plunger, a 64 in stroke, 10 strokes a minute (about 29 m^3 a day; AeroCore Lift),
 *     lifting no more than the field lets in, its motor's power for the lift at 50 %. 0.9: its strokes a minute (4 to
 *     15), stroke (48, 64, 84 or 120 in, the pumping units' sizes) and plunger (1.25 to 2.25 in) are set (Jack).</li>
 * </ul>
 */
public final class Oilwell {
    private Oilwell() {}

    public static final double RIG_W = 75e3, BIT_M = 0.2, CASING_KG_PER_M = 9.7;
    public static final double PLUNGER_M = 0.0445, STROKE_M = 1.63, STROKES_PER_MIN = 10;

    /** A pump jack's settings (0.9): strokes a minute, stroke and plunger (m). */
    public record Jack(double strokesPerMin, double strokeM, double plungerM) {
        public static final double MIN_SPM = 4, MAX_SPM = 15;
        /** The pumping units' strokes (48, 64, 84, 120 in) and plungers (1.25, 1.5, 1.75, 2.25 in), m. */
        public static final double[] STROKES = {1.2192, 1.6256, 2.1336, 3.048}, PLUNGERS = {0.03175, 0.0381, 0.04445, 0.05715};
        public static final Jack NOMINAL = new Jack(STROKES_PER_MIN, STROKE_M, PLUNGER_M);

        public Jack {
            if (!(strokesPerMin >= MIN_SPM && strokesPerMin <= MAX_SPM) || !(strokeM > 0) || !(plungerM > 0)) {
                throw new IllegalArgumentException("A pump jack makes 4 to 15 strokes a minute of a positive stroke and plunger");
            }
        }

        /** What it displaces, m^3/s. */
        public double displacementM3PerS() { return Lift.rodPumpM3PerS(plungerM, strokeM, strokesPerMin); }
    }

    /** What the pump jack displaces, m^3/s. */
    public static double jackDisplacementM3PerS() { return Lift.rodPumpM3PerS(PLUNGER_M, STROKE_M, STROKES_PER_MIN); }

    /** What a pump jack lifts from a field after a volume, m^3/s: its displacement, or the inflow if less. */
    public static double jackRateM3PerS(OilFields.Field f, double producedM3) { return Math.min(jackDisplacementM3PerS(), f.pumpedInflow(producedM3)); }

    /** The same for a jack's settings (0.9). */
    public static double jackRateM3PerS(OilFields.Field f, double producedM3, Jack jack) {
        return Math.min(jack == Jack.NOMINAL ? jackDisplacementM3PerS() : jack.displacementM3PerS(), f.pumpedInflow(producedM3));
    }

    /** The jack's power for a rate from a field, W. */
    public static double jackW(OilFields.Field f, double rateM3PerS) { return Lift.liftPowerW(rateM3PerS, f.oilDensity(), f.depthM(), 0); }

    /** Seconds of drilling a field's depth with the rig. */
    public static double drillS(OilFields.Field f) { return f.depthM() * f.drillSPerM(RIG_W, BIT_M); }
}
