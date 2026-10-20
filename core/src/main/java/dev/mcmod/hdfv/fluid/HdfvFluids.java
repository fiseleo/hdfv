package dev.mcmod.hdfv.fluid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * hdfv's fluids (0.8; the user asked for a petroleum industry 2026-10-13): the crudes, the refinery's products and the
 * petrochemicals, as liquids at about 20 C (the liquefied gases under pressure, ethylene cold), with their density,
 * viscosity and colour. Amounts are litres (the game's millibuckets: a bucket is a cubic metre).
 * <p>
 * Densities and viscosities: typical values (Perry's Chemical Engineers' Handbook; CRC Handbook; Gary, Handwerk and
 * Kaiser for the crudes and cuts: the light sweet crude AeroCore's CrudeAssay calls API 38, the heavy sour API 22).
 * Fuel oil and the heavy crude are pumped warm in practice; their viscosities here are at about 50 C.
 * <p>
 * Hydrogen (0.10): the liquid at its boiling point, 20.37 K (70.85 kg/m^3, 13.3 microPa s; NIST), boiling away in any
 * store as heat leaks in (HydrogenStorage), and the gas compressed to 700 bar at 15 C (AeroCore's Hydrogen.gasDensity,
 * about 40 kg/m^3; 8.9 microPa s), kept only in high-pressure cylinders.
 */
public final class HdfvFluids {
    private HdfvFluids() {}

    /** A fluid: id, density (kg/m^3), dynamic viscosity (Pa s), colour (RGB), kept under pressure or cold. */
    public record Spec(String id, double densityKgPerM3, double viscosityPaS, int colour, boolean pressurised) {}

    /** The two forms of hydrogen (0.10), and the compressed gas's pressure, Pa. */
    public static final String LIQUID_HYDROGEN = "liquid_hydrogen", HYDROGEN = "hydrogen";
    public static final double HYDROGEN_PA = 70e6;

    private static final Map<String, Spec> ALL = new LinkedHashMap<>();

    private static void put(String id, double density, double viscosity, int colour, boolean pressurised) { ALL.put(id, new Spec(id, density, viscosity, colour, pressurised)); }

    static {
        put("crude_oil", 835, 5e-3, 0x2b2118, false);
        put("heavy_crude_oil", 922, 0.25, 0x16120d, false);
        put("lpg", 540, 0.12e-3, 0xe7eef3, true);
        put("naphtha", 700, 0.5e-3, 0xe9e0b8, false);
        put("gasoline", 745, 0.5e-3, 0xe6c25a, false);
        put("premium_gasoline", 750, 0.5e-3, 0xd99a3c, false);
        put("kerosene", 800, 1.3e-3, 0xd8e2f0, false);
        put("diesel", 840, 2.8e-3, 0xc9b14a, false);
        put("gas_oil", 910, 0.05, 0x6b4a1f, false);
        put("fuel_oil", 980, 0.5, 0x1d1a16, false);
        put("lubricating_oil", 870, 0.15, 0xb88a2b, false);
        put("methanol", 792, 0.59e-3, 0xe0f0ff, false);
        put("ethylene", 568, 0.15e-3, 0xf4f7fa, true);
        put("propylene", 514, 0.1e-3, 0xf0f4f2, true);
        put("butadiene", 621, 0.15e-3, 0xf2ece0, true);
        put("aromatics", 870, 0.6e-3, 0xf5e6c8, false);
        put(LIQUID_HYDROGEN, dev.mcmod.aerocore.api.storage.Hydrogen.LIQUID_DENSITY_KG_PER_M3, 13.3e-6, 0xdcefff, true);
        put(HYDROGEN, dev.mcmod.aerocore.api.storage.Hydrogen.gasDensity(HYDROGEN_PA, 288.15), 8.9e-6, 0xf4f8ff, true);
    }

    /** A liquid kept below its boiling point at ambient pressure: it boils off in any store. */
    public static boolean cryogenic(String id) { return LIQUID_HYDROGEN.equals(id); }

    /** A gas kept at hundreds of bar: only high-pressure cylinders hold it. */
    public static boolean compressedGas(String id) { return HYDROGEN.equals(id); }

    /** The storage temperature of a fluid, K (its fluid type's). */
    public static int temperatureK(String id) { return LIQUID_HYDROGEN.equals(id) ? 20 : "ethylene".equals(id) ? 170 : 293; }

    public static Spec of(String id) { return ALL.get(id); }

    public static List<Spec> all() { return List.copyOf(ALL.values()); }

    /** Water (the game's own), for pumping it too. */
    public static final Spec WATER = new Spec("water", 998, 1.0e-3, 0x3f76e4, false);
}
