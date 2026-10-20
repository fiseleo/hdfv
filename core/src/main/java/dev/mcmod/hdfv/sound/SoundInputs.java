package dev.mcmod.hdfv.sound;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.acoustics.Surface;

/**
 * What a vehicle's sound is made from (0.19): the state a client knows of it (synced shown values, rotor speeds and
 * control channels, its velocity), and what lies under its wheels. The game implements it from a PartVehicle; tests
 * and the offline bench from a chosen operating point.
 */
public interface SoundInputs {
    /** A part's i-th shown value (0 when it shows nothing there). */
    double shown(String partId, int index);
    /** A rotor's speed (rad/s), by its index in the vehicle's rotors. */
    double rotorSpeed(int rotor);
    /** The input a part reads for a role (its bindings applied to the control channels). */
    double input(String partId, String role);
    /** A control channel (0 when not set). */
    double control(String channel);
    /** The vehicle's velocity, m/s. */
    Vec3d velocity();
    /** What a wheel stands on (0 left or the only one, 1 right). */
    Surface surface(String partId, int wheel);
    /** Whether the road is wet (rain, standing water). */
    boolean wet();

    /** Whether the vehicle's hull is in the water. */
    default boolean inWater() { return false; }
    /** Whether it rains on the vehicle (automatic wipers). */
    default boolean raining() { return wet(); }
    /** The game's time, s (for blinking indicators in step with the lamps). */
    default double time() { return 0; }
    /** The vehicle's own phase seed for blinking (its lamps' seed). */
    default int blinkSeed() { return 0; }
    /** What the body scrapes now, or null. */
    default Scrape scrape() { return null; }

    /** A scrape: the frictional power (W), the sliding speed (m/s) and what it slides on. */
    record Scrape(double powerW, double slideMps, Surface surface) {}
}
