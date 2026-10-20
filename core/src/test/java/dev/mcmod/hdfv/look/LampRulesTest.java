package dev.mcmod.hdfv.look;

import dev.mcmod.hdfv.look.LampRules.Inputs;
import dev.mcmod.hdfv.look.LampRules.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The lamps' rules (0.12): the light switch's steps, brake and reversing lights, a beacon's and a strobe's flashes. */
class LampRulesTest {
    private static Inputs at(double lights, double brake, double selector, boolean running, double ticks) { return new Inputs(lights, brake, selector, running, ticks, 0); }

    @Test void theLightSwitchStepsThroughPositionDippedAndMain() {
        for (int lights = 0; lights <= 3; lights++) {
            Inputs in = at(lights, 0, 0, false, 0);
            assertEquals(lights >= 1 ? 1 : 0, LampRules.level(Role.NAV_RED, in));
            assertEquals(lights >= 1 ? 1 : 0, LampRules.level(Role.MASTHEAD, in));
            assertEquals(lights >= 1 ? 0.4f : 0, LampRules.level(Role.TAIL_BRAKE, in));
            assertEquals(lights >= 2 ? 1 : 0, LampRules.level(Role.HEAD_LOW, in));
            assertEquals(lights >= 3 ? 1 : 0, LampRules.level(Role.HEAD_HIGH, in));
        }
    }

    @Test void brakeAndReverseLightsFollowTheDriver() {
        assertEquals(0, LampRules.level(Role.TAIL_BRAKE, at(0, 0.04, 0, false, 0)), "a touch under 5 % shows nothing");
        assertEquals(1, LampRules.level(Role.TAIL_BRAKE, at(0, 0.3, 0, false, 0)), "braking: full");
        assertEquals(1, LampRules.level(Role.TAIL_BRAKE, at(1, 0.3, 0, false, 0)), "over the tail light");
        assertEquals(1, LampRules.level(Role.REVERSE, at(0, 0, -1, false, 0)));
        for (double sel : new double[]{-2, 0, 1, 2}) { assertEquals(0, LampRules.level(Role.REVERSE, at(0, 0, sel, false, 0)), "selector " + sel); }
        assertEquals(1, LampRules.level(Role.PANEL, at(0, 0, 0, true, 0)), "instruments lit with the engine running");
    }

    @Test void theBeaconFlashesSixtyTimesAMinuteAndTheStrobeTwiceEachPeriod() {
        // count the flashes' starts over a minute, a tick at a time
        int beacon = 0, strobe = 0;
        boolean wasBeacon = false, wasStrobe = false;
        for (int tick = 0; tick < 1200; tick++) {
            boolean b = LampRules.level(Role.BEACON, at(0, 0, 0, true, tick)) > 1, s = LampRules.level(Role.STROBE, at(2, 0, 0, false, tick)) > 1;
            if (b && !wasBeacon) { beacon++; }
            if (s && !wasStrobe) { strobe++; }
            wasBeacon = b; wasStrobe = s;
        }
        assertEquals(60, beacon, 1, "within 14 CFR 25.1401's 40 to 100 a minute");
        assertEquals(100, strobe, 2, "a double flash every 1.2 s");
        assertEquals(0, LampRules.level(Role.BEACON, at(0, 0, 0, false, 0)), "off with everything off");
        assertEquals(0, LampRules.level(Role.STROBE, at(1, 0, 0, true, 0)), "strobes from lights 2");
        // a vehicle's own phase moves its flashes, the same on every client
        Inputs a = new Inputs(1, 0, 0, true, 5, 0x1234), b = new Inputs(1, 0, 0, true, 5, 0x1234);
        assertEquals(LampRules.level(Role.BEACON, a), LampRules.level(Role.BEACON, b));
    }

    @Test void indicatorsFlashNinetyTimesAMinuteHalfLit() {
        int flashes = 0, lit = 0;
        boolean was = false;
        for (int t = 0; t < 1200; t++) {   // a minute of ticks
            Inputs in = new Inputs(0, 0, 0, false, t, 77, -1);
            boolean on = LampRules.level(Role.TURN_LEFT, in) > 0;
            assertEquals(0, LampRules.level(Role.TURN_RIGHT, in), "the other side stays dark");
            if (on && !was) { flashes++; }
            if (on) { lit++; }
            was = on;
        }
        assertEquals(90, flashes, 1, "UN ECE R48: 90 +/- 30 a minute");
        assertEquals(0.5, lit / 1200.0, 0.03, "half the time lit");
        Inputs hazards = new Inputs(0, 0, 0, false, 3, 77, 2);
        assertEquals(LampRules.level(Role.TURN_LEFT, hazards), LampRules.level(Role.TURN_RIGHT, hazards), "hazards: both together");
        assertTrue(LampRules.indicating(hazards, true) && LampRules.indicating(hazards, false));
        assertEquals(0, LampRules.level(Role.TURN_LEFT, new Inputs(3, 1, 0, true, 3, 77)), "the inputs before 0.19 have no indicators");
    }
}
