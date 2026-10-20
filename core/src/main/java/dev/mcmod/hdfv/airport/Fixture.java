package dev.mcmod.hdfv.airport;

/**
 * A light fitting a player placed on an airfield (0.20): its kind and block. Which colour each side shows, which way it
 * points and when it flashes follow from where it stands (AirfieldLighting); the blocks only say what they are.
 */
public record Fixture(Kind kind, int x, int y, int z) {
    public enum Kind {
        /** Elevated runway edge light: white, yellow toward the far end's last 600 m (or third). */
        RUNWAY_EDGE,
        /** Inset runway centre line light: white, red and white then red toward the far end. */
        RUNWAY_CENTRE,
        /** Threshold and runway end light: green toward the approach, red toward the runway. */
        THRESHOLD,
        /** One unit of a precision approach path indicator. */
        PAPI,
        /** Steady approach light (a bar across the extended centre line). */
        APPROACH,
        /** Sequenced flashing approach light (the rabbit). */
        APPROACH_FLASHER,
        /** Blue taxiway edge light. */
        TAXI_EDGE,
        /** Green taxiway centre line light. */
        TAXI_CENTRE,
        /** Red obstruction light. */
        OBSTRUCTION,
        /** Aerodrome beacon: white and green flashes. */
        BEACON;

        public String id() { return "airfield_" + name().toLowerCase(java.util.Locale.ROOT); }
    }
}
