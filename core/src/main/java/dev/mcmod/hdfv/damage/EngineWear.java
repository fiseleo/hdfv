package dev.mcmod.hdfv.damage;

import dev.mcmod.aerocore.api.damage.Wear;

/**
 * 0.25: the rules of hdfv's own engine parts' conditions (Conditions names them): how they wear and what a used share
 * (0 new .. 1 failed) does. Wear under SHOWS_FROM does nothing at all (a run-in engine is as good as new), and the parts
 * branch on that before calling any of this, so a new engine, and one in its first hours, steps to the last bit as
 * before 0.25. Lives are real figures; a part multiplies its step by DamagePace.wear() as read when it was compiled.
 *
 * <ul>
 * <li>A piston engine's "engine" (bores, rings, bearings, valve gear): Wear.running on the mean piston speed squared and
 *     the load, with the hours to overhaul at an ordinary duty; far faster above the rev limit. Worn, it loses
 *     compression and gains friction; at 1 it has seized.</li>
 * <li>Any engine's "ancillaries" (cooling, ignition or injection, electrics, fuel lines): they do not wear by
 *     themselves; a crash, water and fire use them up. Nothing shows up to half; beyond, the engine misfires and its
 *     protection holds it back; at 1 it neither runs nor starts.</li>
 * <li>A gas turbine's "hot_section" (combustor liner, turbine vanes and blades): creep by Larson-Miller on the turbine
 *     entry temperature against the control's limit, the temperature the life is given at; reheat costs more. Worn, it
 *     gives less thrust or power; at 1 it holds no flame.</li>
 * <li>A ramjet's, scramjet's, pulsejet's or booster's "structure" (duct, casing): damaged from outside only; less
 *     thrust, none at 1 (a booster with a damaged casing does not ignite: no bursts are modelled).</li>
 * </ul>
 */
public final class EngineWear {
    private EngineWear() {}

    /** Rule: how used an engine's "engine" or "hot_section" is before it shows at all (the loss there would be a few parts in a hundred thousand). */
    public static final double SHOWS_FROM = 0.01;

    // ----- piston engines -----

    /**
     * Hours a piston engine runs between overhauls at an ordinary duty (REFERENCE_PISTON_MPS and REFERENCE_LOAD): a
     * petrol car engine is good for about 250 000 km at a mean 50 km/h, 5000 h; road diesels for two to three times
     * that (orders of magnitude from service experience; Heywood, Internal Combustion Engine Fundamentals, ch. 13 on
     * wear and its dependence on speed and load).
     */
    public static final double SPARK_OVERHAUL_H = 5000, DIESEL_OVERHAUL_H = 12000;
    /** The duty those hours are for: a mean piston speed of 8 m/s (2800 rpm on an 86 mm stroke) at four tenths of full load. */
    public static final double REFERENCE_PISTON_MPS = 8, REFERENCE_LOAD = 0.4;
    /** An idling engine still wears: the load never counts as less than this. */
    public static final double LEAST_LOAD = 0.1;
    /** Rule: over the rev limit wear climbs with the twelfth power of the overspeed (three times at 10 % over, nine at 20 %). */
    public static final double OVERSPEED_EXPONENT = 12;
    /**
     * Rule: the share of its indicated work a worn-out engine has lost (blow-by, leaking valves), going with the square of
     * the wear; and how much more friction it has, going with the wear. Together about 40 % of the sample inline four's
     * full-load torque near 1 (WearTest), more at part load where friction is most of what it overcomes.
     */
    public static final double COMPRESSION_LOSS = 0.25, FRICTION_RISE = 0.25;
    /** Rule: a seized crank stops in about this time (s). */
    public static final double SEIZE_S = 0.05;

    /**
     * The share of a piston engine's life a time (s, on the game's clock: the step times DamagePace.wear()) uses: Wear.running
     * on mean piston speed squared and load against the life (s) at the reference duty, and the overspeed rule.
     */
    public static double piston(double wearS, double lifeS, double pistonMps, double load, double rpm, double revLimitRpm) {
        if (!(wearS > 0) || !(pistonMps > 0)) { return 0; }
        double wear = Wear.running(wearS, lifeS, pistonMps / REFERENCE_PISTON_MPS, 2, Math.max(Math.min(load, 3), LEAST_LOAD) / REFERENCE_LOAD, 1);
        if (rpm > revLimitRpm && revLimitRpm > 0) { wear *= Math.pow(Math.min(rpm / revLimitRpm, 3), OVERSPEED_EXPONENT); }
        return Double.isFinite(wear) ? Math.min(wear, 1) : 1;
    }

    /** The share of its indicated work a piston engine keeps with so much of it used (call with used from SHOWS_FROM). */
    public static double compression(double used) { double u = Math.min(used, 1); return 1 - COMPRESSION_LOSS * u * u; }
    /** What its friction is multiplied by. */
    public static double friction(double used) { return 1 + FRICTION_RISE * Math.min(used, 1); }

    // ----- ancillaries -----

    /** From here the ancillaries' damage shows; rule: the share of its power an engine keeps just short of 1. */
    public static final double ANCILLARIES_KNEE = 0.5, ANCILLARIES_LAST = 0.3;

    /** The share of its power an engine keeps with so much of its ancillaries used (1 up to the knee, then down to ANCILLARIES_LAST; 0 at 1). */
    public static double ancillaries(double used) {
        if (!(used > ANCILLARIES_KNEE)) { return 1; }
        if (used >= 1) { return 0; }
        return 1 - (1 - ANCILLARIES_LAST) * (used - ANCILLARIES_KNEE) / (1 - ANCILLARIES_KNEE);
    }

    /** Whether a condition has failed. */
    public static boolean failed(double used) { return used >= 1; }

    // ----- gas turbines -----

    /**
     * Hours a hot section lasts at the control's turbine entry temperature limit (its take-off rating). Airline engines
     * stay on the wing 10 000 to 20 000 h with one to three per cent of them at take-off power, and the creep life at
     * cruise temperatures is far longer (Larson-Miller: halving every 15 to 20 K), so the take-off hours are what
     * count: 200 to 600 h; military turbojets' hot section inspections came at a few hundred hours. A round 400 h.
     */
    public static final double HOT_SECTION_H = 400;
    /** Rule: a lit afterburner's heat on the nozzle and liner costs the hot section this many times its life. */
    public static final double REHEAT_WEAR = 2;
    /** Rule: the share of its thrust or shaft power a worn-out hot section has lost, going with the square of the wear. */
    public static final double HOT_SECTION_LOSS = 0.35;

    /**
     * The share of a hot section's life a time (s, on the game's clock) at a turbine entry temperature uses: creep
     * against HOT_SECTION_H at the rated temperature (K), and the reheat rule.
     */
    public static double hotSection(double wearS, double ratedK, double turbineInletK, boolean reheat) {
        if (!(wearS > 0) || !(turbineInletK > 300) || !(ratedK > 300)) { return 0; }
        // far over the limit the temperature counts as twice the rating at most: the wear stays finite
        double wear = Wear.creep(wearS, HOT_SECTION_H, ratedK, Math.min(turbineInletK, 2 * ratedK), Wear.LARSON_MILLER_C);
        if (reheat) { wear *= REHEAT_WEAR; }
        return Double.isFinite(wear) ? Math.min(wear, 1) : 1;
    }

    /** The share of its thrust or power a gas turbine keeps with so much of its hot section used (call with used from SHOWS_FROM). */
    public static double hotSection(double used) { double u = Math.min(used, 1); return 1 - HOT_SECTION_LOSS * u * u; }

    // ----- ducts and casings -----

    /** Rule: the share of its thrust a duct engine has lost just short of a failed structure (leaks, a bent inlet). */
    public static final double STRUCTURE_LOSS = 0.5;

    /** The share of its thrust a ramjet, scramjet or pulsejet keeps with so much of its structure used (0 at 1). */
    public static double structure(double used) { return used >= 1 ? 0 : 1 - STRUCTURE_LOSS * Math.max(used, 0); }

    // ----- fuel tanks -----

    /** From here a tank leaks; rule: the share of a full tank a tank just short of 1 loses each second. */
    public static final double TANK_KNEE = 0.5, TANK_LEAK_PER_S = 0.02;

    /** The share of its capacity a damaged tank loses in a second (0 up to the knee, rising with the square to TANK_LEAK_PER_S; all of it at 1). */
    public static double leakPerS(double used) {
        if (!(used > TANK_KNEE)) { return 0; }
        if (used >= 1) { return 1; }
        double x = (used - TANK_KNEE) / (1 - TANK_KNEE);
        return TANK_LEAK_PER_S * x * x;
    }
}
