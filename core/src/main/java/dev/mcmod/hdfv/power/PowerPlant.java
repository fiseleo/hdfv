package dev.mcmod.hdfv.power;

import dev.mcmod.aerocore.api.power.Battery;
import dev.mcmod.aerocore.api.power.Belt;
import dev.mcmod.aerocore.api.power.Cable;
import dev.mcmod.aerocore.api.power.ElectricMachine;
import dev.mcmod.aerocore.api.power.HumanPower;
import dev.mcmod.aerocore.api.power.Shafting;
import dev.mcmod.aerocore.api.power.SolidFuel;
import dev.mcmod.aerocore.api.power.SteamEngine;

/**
 * The workshop's power (0.8; the user's choice 2026-10-13: electricity and line shafts both). Everything comes from
 * AeroCore's api.power; these are hdfv's choices of size.
 *
 * <ul>
 * <li>Electricity: 400 V three-phase at power factor 0.85; a light cable is 6 mm^2 copper (41 A, about 24 kW), a
 *     heavy one 95 mm^2 (223 A, about 131 kW) (IEC 60364-5-52); a cable block is a metre. A network carries what its
 *     smallest cable does, losing I^2 R in all its metres.</li>
 * <li>Line shafts: 60 mm steel, turning at NOMINAL_RPM (mill shafting ran 120 to 300 rpm), a metre a block; bearings
 *     take their friction; machines on the line take their power through a leather belt (96 %), working at the
 *     line's speed over nominal and stopping below half of it.</li>
 * <li>Prime movers: a generator (50 kW, IE3), an electric motor (15 kW), the mill steam engine (15 kW, AeroCore's
 *     SteamEngine.MILL burning coal, charcoal, coke or wood), a treadle (a person's legs, 75 W), an engine stand
 *     (EngineUnit); a nickel-iron battery bank of 50 kWh (C/5: 10 kW).</li>
 * </ul>
 */
public final class PowerPlant {
    private PowerPlant() {}

    public static final double LINE_VOLTS = 400, POWER_FACTOR = 0.85;

    public enum CableKind {
        LIGHT(6), HEAVY(95);
        public final double mm2;
        CableKind(double mm2) { this.mm2 = mm2; }
        /** Most power it carries, W. */
        public double ratedW() { return Cable.powerW(mm2, LINE_VOLTS, POWER_FACTOR); }
        /** Fraction of the power lost in a length (m) at its rating (three conductors). */
        public double lossAtRated(double lengthM) { return Cable.lossW(Cable.ampacityA(mm2), lengthM, mm2) / ratedW(); }
    }

    public static final double SHAFT_DIAMETER_M = 0.06, NOMINAL_RPM = 250, STOP_FRACTION = 0.5;

    public static double omega(double rpm) { return rpm * Math.PI / 30; }

    /** A shaft block's inertia, kg m^2. */
    public static double shaftInertia() { return Shafting.inertiaPerM(SHAFT_DIAMETER_M); }

    /** A shaft block's bearing friction at a speed: its own weight on the journal, W. */
    public static double shaftFrictionW(double rpm) {
        return Shafting.bearingLossW(Shafting.kgPerM(SHAFT_DIAMETER_M) * 9.80665, SHAFT_DIAMETER_M, omega(Math.abs(rpm)));
    }

    /** Most power the line carries at a speed, W. */
    public static double shaftCapacityW(double rpm) { return Shafting.capacityW(SHAFT_DIAMETER_M, omega(Math.abs(rpm))); }

    /** How fast a belt-driven machine works at a line speed: 0 below half nominal, then in proportion, 1 at nominal. */
    public static double speedFactor(double rpm) {
        double x = rpm / NOMINAL_RPM;
        return x < STOP_FRACTION ? 0 : Math.min(1, x);
    }

    /** The line's power for a machine whose electric drive would draw a power (its motor's efficiency, then the belt), W. */
    public static double lineDemandW(double electricW, double motorRatedW) {
        double mech = electricW * ElectricMachine.of(Math.max(motorRatedW, 750)).efficiency(Math.min(electricW, motorRatedW));
        return mech / Belt.EFFICIENCY;
    }

    public static final double GENERATOR_W = 50e3, MOTOR_W = 15e3, STEAM_W = 15e3, TREADLE_W = HumanPower.LEGS_W;
    public static final ElectricMachine GENERATOR = ElectricMachine.of(GENERATOR_W), MOTOR = ElectricMachine.of(MOTOR_W);
    public static final SteamEngine STEAM = SteamEngine.MILL;
    public static final Battery BATTERY = Battery.nickelIron(50);
    /** Inertia of a prime mover's or a machine's rotating parts referred to the line, kg m^2 (estimates). */
    public static final double MACHINE_INERTIA = 0.5, MOTOR_INERTIA = 0.3, STEAM_FLYWHEEL_INERTIA = 60, GENERATOR_INERTIA = 1.5;

    /** A solid fuel item: the fuel and its mass, kg (a coal or charcoal lump 1 kg, a block of coal 9, a log 4, a plank 1, a stick 0.1: the game's own proportions). */
    public record FuelItem(SolidFuel fuel, double kg) {
        public double heatJ() { return fuel.heatingValueJPerKg() * kg; }
    }
    public static final FuelItem COAL = new FuelItem(SolidFuel.COAL, 1), CHARCOAL = new FuelItem(SolidFuel.CHARCOAL, 1),
            COAL_BLOCK = new FuelItem(SolidFuel.COAL, 9), LOG = new FuelItem(SolidFuel.WOOD, 4), PLANK = new FuelItem(SolidFuel.WOOD, 1),
            STICK = new FuelItem(SolidFuel.WOOD, 0.1),
            // 0.23: coke (2 coal give a 1 kg lump of coke and the tar), and any coke tagged c:coal_coke
            COKE = new FuelItem(SolidFuel.COKE, 1);

    /** Heat a fuel-fired furnace puts into its work over the fuel's heat: a crucible furnace 25 %, an oven or vulcanizer's steam 50 % (typical). */
    public static final double FOUNDRY_FUEL_EFFICIENCY = 0.25, OVEN_FUEL_EFFICIENCY = 0.5;
}
