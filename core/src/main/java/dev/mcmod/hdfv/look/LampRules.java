package dev.mcmod.hdfv.look;

/**
 * When a vehicle's lamps are lit (0.12): rules, from the driver's channels and the vehicle's state, for each lamp's
 * role. A level is 0 off, 1 on, less than 1 dimmed (a tail light beside its brake light), above 1 a flash.
 * <ul>
 * <li>"lights" 0 off, 1 position lights (tail and navigation lights; a boat's side, masthead and stern lights), 2 also
 *     dipped headlights, 3 main beams too.</li>
 * <li>Navigation lights: an aircraft's red to port, green to starboard (110 degrees from dead ahead each) and white
 *     aft (140 degrees), 14 CFR 23.1385-1397; a boat's sidelights (112.5 degrees each), masthead (225 forward) and
 *     stern (135 aft) lights, COLREGS rule 21. Lit with lights 1 and up.</li>
 * <li>Brake lights while the brake is applied over 5 %; the tail light beside them at 0.4. Reversing lights in R.</li>
 * <li>An anti-collision beacon (red) while an engine runs or the lights are on, flashing 60 times a minute (14 CFR
 *     25.1401 asks 40 to 100); white wingtip strobes with lights 2 and up, a double flash every 1.2 s.</li>
 * <li>Instrument lighting with lights 1 and up or an engine running.</li>
 * <li>0.19: amber turn indicators by the "indicators" channel (-1 left, 1 right, 2 hazards both), flashing 90 times a
 *     minute with half the time on (UN ECE R48 asks 90 +/- 30 a minute, on 30-75 % of it).</li>
 * </ul>
 * Flashes keep time with the game (ticks) from a phase of the vehicle's own (its id's hash): every client sees the
 * same, with nothing sent.
 */
public final class LampRules {
    private LampRules() {}

    /** A lamp's role (stored as its ordinal in AeroCore's Lamp.role). */
    /** 0.18: FOG, a car's front fog lamps (lit with the dipped headlights). New roles only at the end (stored by ordinal). */
    /** 0.19: TURN_LEFT, TURN_RIGHT, amber indicators. */
    public enum Role { HEAD_LOW, HEAD_HIGH, TAIL_BRAKE, REVERSE, NAV_RED, NAV_GREEN, NAV_WHITE, BEACON, STROBE, PANEL, SIDE_RED, SIDE_GREEN, MASTHEAD, STERN, FOG,
        TURN_LEFT, TURN_RIGHT }

    /** The selector's reverse position (ShiftRules: -2 P, -1 R, 0 N, 1 D, 2 M). */
    public static final double REVERSE_SELECTOR = -1;
    /** A beacon's period (s) and flash (s); a strobe's double flash: period, each flash, the gap between them. */
    public static final double BEACON_S = 1.0, BEACON_FLASH_S = 0.12, STROBE_S = 1.2, STROBE_FLASH_S = 0.05, STROBE_GAP_S = 0.15;
    /** A flash's level (a bright blink over a steady light's 1). */
    public static final float FLASH = 2.5f;
    /** 0.19: an indicator's period (s), half of it lit. */
    public static final double TURN_S = 60.0 / 90;

    /**
     * What the lamps go by: the "lights", "brake" and "selector" channels, whether an engine runs, the game's time (ticks,
     * the partial tick added) and the vehicle's phase seed (its id's hash).
     */
    public record Inputs(double lights, double brake, double selector, boolean running, double ticks, int seed, double indicators) {
        /** The inputs before 0.19 (no indicators). */
        public Inputs(double lights, double brake, double selector, boolean running, double ticks, int seed) { this(lights, brake, selector, running, ticks, seed, 0); }
    }

    /** A role's level now. */
    public static float level(Role role, Inputs in) {
        int lights = (int) Math.round(Math.clamp(in.lights(), 0, 3));
        return switch (role) {
            case HEAD_LOW -> lights >= 2 ? 1 : 0;
            case HEAD_HIGH -> lights >= 3 ? 1 : 0;
            case FOG -> lights >= 2 ? 1 : 0;
            case TAIL_BRAKE -> in.brake() > 0.05 ? 1 : lights >= 1 ? 0.4f : 0;
            case REVERSE -> Math.abs(in.selector() - REVERSE_SELECTOR) < 0.5 ? 1 : 0;
            case NAV_RED, NAV_GREEN, NAV_WHITE, SIDE_RED, SIDE_GREEN, MASTHEAD, STERN -> lights >= 1 ? 1 : 0;
            case PANEL -> lights >= 1 || in.running() ? 1 : 0;
            case BEACON -> (in.running() || lights >= 1) && within(in, BEACON_S, 0, BEACON_FLASH_S) ? FLASH : 0;
            case STROBE -> lights >= 2 && (within(in, STROBE_S, 0, STROBE_FLASH_S) || within(in, STROBE_S, STROBE_GAP_S, STROBE_FLASH_S)) ? FLASH : 0;
            case TURN_LEFT -> indicating(in, true) && blinkOn(in) ? 1 : 0;
            case TURN_RIGHT -> indicating(in, false) && blinkOn(in) ? 1 : 0;
        };
    }

    /** Whether a side's indicator is switched on (it may be in the dark half of its flash). */
    public static boolean indicating(Inputs in, boolean left) {
        long v = Math.round(in.indicators());
        return v == 2 || (left ? v == -1 : v == 1);
    }

    /** Whether the indicators' flash is in its lit half now (the relay's click is at each change). */
    public static boolean blinkOn(Inputs in) { return within(in, TURN_S, 0, TURN_S / 2); }

    /** Whether the time (with the vehicle's phase) falls within a flash starting at an offset into each period. */
    static boolean within(Inputs in, double periodS, double offsetS, double flashS) {
        double phase = ((in.seed() & 0xffff) / 65536.0) * periodS;
        double t = in.ticks() * 0.05 + phase - offsetS;
        double into = ((t % periodS) + periodS) % periodS;
        return into < flashS;
    }
}
