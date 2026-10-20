package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.gasdynamics.PerfectGas;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;

/**
 * What a burning engine shows of its exhaust, for drawing its plume (0.11): the gas's total temperature leaving the
 * nozzle (K, to 10 K), its Mach number there (to 0.05) and its static pressure over the ambient (to 0.05), three values
 * appended after the part's own shown values (all 0 when nothing burns). Rounded so that a steady engine shows the same
 * numbers tick after tick. They come from the operating point the part's thrust comes from; where an engine's model
 * does not give its exit (a turbofan's nozzles, a turboshaft's stacks, a pulsejet's tail, a scramjet's ramp) the
 * caller estimates it and says how. Only for drawing: nothing reads them back into the physics.
 */
public final class Exhaust {
    private Exhaust() {}

    public static final int COUNT = 3, KELVIN = 0, MACH = 1, PRESSURE = 2;
    /** Hot combustion gas, for an exit Mach number from an exit speed (a display estimate). */
    public static final double GAMMA = 1.33, CP = 1150;

    /** Shows the three values from a part's first exhaust index, rounded; nothing burning shows zeros. */
    public static void show(PartStep step, int first, boolean burning, double kelvin, double mach, double pressureRatio) {
        boolean ok = burning && kelvin > 0 && Double.isFinite(kelvin) && Double.isFinite(mach) && Double.isFinite(pressureRatio);
        step.show(first + KELVIN, ok ? round(kelvin, 10) : 0);
        step.show(first + MACH, ok ? round(Math.clamp(mach, 0, 8), 0.05) : 0);
        step.show(first + PRESSURE, ok ? round(Math.clamp(pressureRatio, 0, 50), 0.05) : 0);
    }

    /** A value to the nearest step. */
    public static double round(double value, double step) { return Math.rint(value / step) * step; }

    /** The Mach number of a gas leaving at a speed (m/s) from a total temperature (K). */
    public static double machFromSpeed(double speedMps, double totalK) {
        double v = Math.max(0, speedMps), staticK = Math.max(100, totalK - v * v / (2 * CP));
        return v / Math.sqrt(GAMMA * 287.05 * staticK);
    }

    /**
     * A convergent nozzle at a total-to-ambient pressure ratio: its exit Mach number and static pressure over ambient
     * (choked above the critical ratio: Mach 1, the rest of the expansion outside).
     */
    public static double[] convergent(double npr) {
        double critical = PerfectGas.totalToStaticPressure(1, GAMMA);
        if (!(npr > 1)) { return new double[]{0, 1}; }
        if (npr >= critical) { return new double[]{1, npr / critical}; }
        return new double[]{PerfectGas.machAtPressureRatio(npr, GAMMA), 1};
    }

    /** Where a design's exhaust values start among its part's shown values; -1 for a part without them. */
    public static int first(PartDesign d) {
        return switch (d) {
            case JetEngineDesign j -> j.afterburner().fitted() ? 2 : 0;
            case TurbofanDesign f -> f.afterburner().fitted() ? 2 : 0;
            case ShaftTurbineDesign s -> 0;
            case RamjetDesign r -> 1;
            case ScramjetDesign s -> 1;
            case SolidRocketDesign s -> 1;
            case TurboRamjetDesign t -> 2;
            case PulsejetDesign p -> 3;
            default -> -1;
        };
    }
}
