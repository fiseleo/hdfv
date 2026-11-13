package dev.mcmod.hdfv.damage;

import dev.mcmod.aerocore.api.damage.Corrosion;

/**
 * 0.25: what standing in the weather, water, fire and blasts do, as shares of a condition to add. Rules with their
 * numbers; where a part stands (its exposure, whether it is under water) is the game's to find (neoforge's
 * VehicleDamage).
 */
public final class Elements {
    private Elements() {}

    // ----- corrosion -----

    /**
     * How much faster than the game's clock metal corrodes (rule): at the default pace a real second is 72 s of work and
     * 720 s of corrosion, a year of rust in about twelve real hours of a vehicle's chunk being loaded. With it a painted
     * steel car body (0.8 mm) standing in the rain loses about 1.2 % a real day, in sea air 3.4 %, inland in dry weather
     * 0.3 %, under a roof next to nothing; a body with its paint gone, in the sea, 4 % an hour (ElementsTest).
     */
    public static final double CORROSION_QUICKENING = 10;
    /** The share of the bare rate a sound paint system lets through (pinholes and edges). */
    public static final double HOLIDAYS = 0.03;
    /** The share of a wall's thickness that can go before the part is no longer fit (thin stressed skin). */
    public static final double ALLOWABLE = 0.3;

    /**
     * The share of a part's structural condition that corrosion uses in a time (real s): the metal's rate where it
     * stands (AeroCore's Corrosion), under a coating as intact as the part is undamaged (1 - used: dents and tears break
     * the paint), over the allowable share of its wall (mm), on the game's corrosion clock (DamagePace.corrosion())
     * times CORROSION_QUICKENING.
     */
    public static double corrosion(Corrosion.Metal metal, Corrosion.Exposure exposure, double used, double wallMm, double realSeconds) {
        if (metal == null || metal == Corrosion.Metal.NONE || !(wallMm > 0) || !(realSeconds > 0)) { return 0; }
        double rate = Corrosion.coated(Corrosion.rateMmPerYear(metal, exposure), 1 - Math.clamp(used, 0.0, 1.0), HOLIDAYS);
        double amount = Corrosion.used(rate, realSeconds * DamagePace.corrosion() * CORROSION_QUICKENING, wallMm, ALLOWABLE);
        return Double.isFinite(amount) ? amount : 0;
    }

    /**
     * Corrosion is added to a condition in steps of this size (the game keeps what is short of a step with the vehicle):
     * a vehicle that has just been built or repaired stays exactly new for hours, and its clients are not told of every
     * millionth.
     */
    public static final double CORROSION_STEP = 0.001;

    // ----- water -----

    /** Rule: the share of an engine's ancillaries or a motor that a second under water uses (wet ignition and electrics: trouble after ten seconds, dead in twenty). */
    public static final double DROWNING_PER_S = 0.05;
    /** Rule: what a running piston engine's "engine" takes when it goes under: water in the cylinders (bent rods; hydrolock). */
    public static final double HYDROLOCK = 0.5;
    /** Rule: how deep (m) the machinery of something that floats may sit under the surface and stay dry, while its hull is sound (structure under HULL_BREACHED). */
    public static final double DRY_HULL_M = 0.5, HULL_BREACHED = 0.5;

    // ----- blasts and blows -----

    /**
     * Energy (J) a blast of vanilla explosion damage leaves in a vehicle: BLAST_J times the damage squared (a blast's
     * energy goes with the square of its impulse, AeroCore's ImpactDamage.blastEnergyJ, and vanilla's damage falls about
     * as the impulse does with distance). Set so a block of TNT touching a car (about 42 points) is a 50 km/h crash's
     * 130 kJ, and one four blocks away (about 12 points) a parking bump's 11 kJ.
     */
    public static final double BLAST_J = 74;
    public static double blastJ(double damage) { return damage > 0 ? Math.min(BLAST_J * damage * damage, 5e6) : 0; }

    /** Energy (J) a blow or a shot of vanilla damage leaves at the part it lands on (rule: 20 J a point, a sword's 7 points a hard blow's 140 J: it scratches a body and bends small things a little; a sword cannot wreck a car). */
    public static final double BLOW_J = 20;
    public static double blowJ(double damage) { return damage > 0 ? Math.min(BLOW_J * damage, 5e3) : 0; }

    /** Rule: one shot in so many that lands by a wheel lets the air out of its tyre. */
    public static final int PUNCTURE_ONE_IN = 6;

    // ----- fire and fuel -----

    /**
     * Fire as vanilla deals it, in points a second: standing in lava (four points every half second), in a fire block,
     * and burning (one point a second, for IGNITED_S after the lava or fire, LAVA_IGNITED_S after lava; water and rain
     * put it out). The game looks itself (VehicleDamage): vehicles' entity types are immune to fire, so vanilla never
     * tells them of it.
     */
    public static final double LAVA_POINTS_PER_S = 8, FIRE_POINTS_PER_S = 1, BURNING_POINTS_PER_S = 1, IGNITED_S = 8, LAVA_IGNITED_S = 15;

    /** Rule: the power of a burst tank's explosion from the fuel aboard (kg): 1 + the cube root, at most 6 (vanilla TNT is 4: about 27 kg). */
    public static double tankBlastPower(double fuelKg) { return fuelKg > 0 ? Math.min(1 + Math.cbrt(fuelKg), 6) : 0; }
    /** The least fuel (kg) that explodes, and what every condition of a vehicle whose tank exploded is used to at least. */
    public static final double LEAST_EXPLOSIVE_KG = 0.5, BURNT_OUT = 0.9;
}
