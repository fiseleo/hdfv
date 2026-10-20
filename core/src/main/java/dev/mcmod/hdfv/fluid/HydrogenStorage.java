package dev.mcmod.hdfv.fluid;

import dev.mcmod.aerocore.api.storage.Hydrogen;
import dev.mcmod.aerocore.api.storage.Insulation;

/**
 * Where hdfv keeps hydrogen (0.10; the user chose both liquid hydrogen with boil-off and 700 bar gas): which stores take
 * which form, how fast the liquid boils away in each, and the electricity the plant spends on it. The physics is
 * AeroCore's (storage.Hydrogen, storage.Insulation); the stores and their sizes are hdfv's.
 * <ul>
 * <li>Compressed hydrogen (700 bar) only in high-pressure cylinder banks (and the pipes and outlets of the plant that
 *     moves it); ordinary tanks, the storage tank and cans refuse it.</li>
 * <li>Liquid hydrogen in any tank but a can and a cylinder bank; it boils away by the heat that leaks in: an ordinary
 *     tank block or the storage tank is bare steel (about 25 W/(m^2 K) of frost and free convection: a cubic metre is
 *     gone within the hour), a cryogenic tank block has 50 mm of vacuum multilayer insulation (well under 1 % a day).
 *     A store's surface is taken as a cube's of its volume. The vapour is vented. Pipes hold none (the pumps move it
 *     straight through).</li>
 * <li>Compressing the reformer's hydrogen from its 20 bar to 700 bar costs about 2.4 kWh/kg (isothermal efficiency
 *     0.55; AeroCore's Hydrogen.compressionWorkJPerKg); the liquefier spends 11.9 kWh/kg (a figure of merit of 0.33, the
 *     Leuna plant's) and makes 10 kg an hour (a game's size; the large plants make tonnes).</li>
 * </ul>
 * Rates here are per second of work time (the industry's clock, Pace).
 */
public final class HydrogenStorage {
    private HydrogenStorage() {}

    /** The stores of the game. */
    public enum Store { TANK, STORAGE_TANK, CAN, CRYOGENIC_TANK, CYLINDERS, PIPE }

    public static final double AMBIENT_K = 293.15;
    /** A block's outer surface (a cubic metre), m^2, and the cryogenic tank block's insulation, m. */
    public static final double BLOCK_AREA_M2 = 6, CRYOGENIC_INSULATION_M = 0.05;
    /** The reformer's hydrogen comes off at about 20 bar. */
    public static final double REFORMER_PA = 2e6, COMPRESSOR_EFFICIENCY = 0.55;
    public static final double COMPRESSION_J_PER_KG = Hydrogen.compressionWorkJPerKg(REFORMER_PA, HdfvFluids.HYDROGEN_PA, AMBIENT_K, COMPRESSOR_EFFICIENCY);
    public static final double LIQUEFACTION_J_PER_KG = Hydrogen.liquefactionWorkJPerKg(Hydrogen.LIQUEFIER_FIGURE_OF_MERIT);
    /** What the liquefier makes, kg per hour of work time. */
    public static final double LIQUEFIER_KG_PER_HOUR = 10;

    /** Whether a store takes a fluid. */
    public static boolean takes(Store store, String fluid) {
        if (HdfvFluids.compressedGas(fluid)) { return store == Store.CYLINDERS || store == Store.PIPE; }
        if (store == Store.CYLINDERS) { return false; }
        if (HdfvFluids.cryogenic(fluid)) { return store != Store.CAN; }
        return true;
    }

    /** Liquid boiled off through an area under an insulation, kg/s. */
    public static double boilOffKgPerS(Insulation insulation, double thicknessM, double areaM2) {
        return Hydrogen.boilOffKgPerS(insulation.heatFlux(thicknessM, AMBIENT_K, Hydrogen.BOILING_K) * areaM2);
    }

    /** Liquid hydrogen boiled off a store block holding some, kg/s (a full block's surface; none for a store that cannot hold it). */
    public static double blockBoilOffKgPerS(Store store) { return boilOffKgPerS(store, 1); }

    /** Liquid hydrogen boiled off a store of a volume (m^3; its surface as a cube's, 6 V^(2/3)), kg/s. */
    public static double boilOffKgPerS(Store store, double volumeM3) {
        double area = BLOCK_AREA_M2 * Math.pow(Math.max(volumeM3, 0), 2.0 / 3);
        return switch (store) {
            case TANK, STORAGE_TANK -> boilOffKgPerS(Insulation.NONE, 0, area);
            case CRYOGENIC_TANK -> boilOffKgPerS(Insulation.VACUUM_MLI, CRYOGENIC_INSULATION_M, area);
            case PIPE, CAN, CYLINDERS -> 0;
        };
    }

    /**
     * Whether a vehicle's tank takes a fluid at the pump (0.10): never a part's own store (a booster's grain is loaded
     * as an item); a fuel tank by its kind (ordinary: fuels at ambient conditions; cryogenic: those and liquid hydrogen;
     * high pressure: 700 bar hydrogen only); any other part's tanks (a body's integral ones) fuels at ambient conditions.
     */
    public static boolean tankTakes(dev.mcmod.aerocore.api.vehicle.PartDesign owner, boolean own, String fluid) {
        if (own) { return false; }
        boolean gas = HdfvFluids.compressedGas(fluid), cold = HdfvFluids.cryogenic(fluid);
        if (owner instanceof dev.mcmod.hdfv.part.FuelTankDesign t) {
            return switch (t.kind()) {
                case ORDINARY -> !gas && !cold;
                case CRYOGENIC -> !gas;
                case HIGH_PRESSURE -> gas;
            };
        }
        return !gas && !cold;
    }
}
