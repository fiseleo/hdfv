package dev.mcmod.hdfv.recipe;

import dev.mcmod.aerocore.api.manufacture.Casting;
import dev.mcmod.aerocore.api.metallurgy.Electrolysis;
import dev.mcmod.aerocore.api.metallurgy.Smelting;
import dev.mcmod.hdfv.manufacture.Alloy;

import java.util.Locale;

/**
 * The arc furnace's and the electrolysis cell's batches (0.23): what each recipe makes and the electricity it takes,
 * from AeroCore's metallurgy (api.metallurgy). tools/recipes.py writes the recipes with these numbers;
 * MetallurgyRecipesTest holds each recipe's kWh to the batch's within 2 %.
 *
 * <ul>
 * <li>The arc furnace is ARC_FURNACE_W (100 kW: a heavy cable's worth), the cell CELL_W (60 kW); a batch takes its
 *     energy over that power, in work time.</li>
 * <li>Steels from iron: liquid steel's heat with a third from oxygen and burners at 65 % (Smelting.steelJPerKg, about
 *     0.40 kWh/kg); the ferroalloys and nickel dissolve in it (their heat is not counted apart).</li>
 * <li>Superalloys (vacuum induction) and titanium (vacuum arc remelting): the alloy's melting heat to 100 K above its
 *     melting point (Casting.meltJ with Alloy's constants) at 65 %, nothing chemical.</li>
 * <li>Ferrosilicon, ferrochrome and titanium slag: Smelting's furnaces (about 8.3, 3.6 and 1.9 kWh/kg).</li>
 * <li>Titanium sponge (Kroll): the reduction gives heat; the retort's holding and the sponge's vacuum distillation are
 *     KROLL_KWH_PER_KG (15 kWh a kg of sponge: a rule; the whole Kroll route runs 30-50 kWh/kg with the magnesium's
 *     electrolysis, which the cell counts apart).</li>
 * <li>Pitch carbon fibre: carbonising takes PITCH_FIBRE_J_PER_KG (25 MJ a kg, as the petrochemical fibre's line in
 *     Refining).</li>
 * <li>The cell: Hall-Heroult aluminium (13.0 kWh/kg), magnesium from its chloride (13.0), ammonium perchlorate by way
 *     of chlorate and perchlorate (8.1 kWh/kg; gunpowder and redstone stand for the salt and the ammonium).</li>
 * </ul>
 */
public final class Metallurgy {
    private Metallurgy() {}

    public static final double ARC_FURNACE_W = 100e3, CELL_W = 60e3;
    public static final double KROLL_KWH_PER_KG = 15, PITCH_FIBRE_J_PER_KG = 25e6, VACUUM_EFFICIENCY = 0.65;
    private static final double J_PER_KWH = 3.6e6;

    /** A batch: its recipe id (under hdfv:), the machine, and its energy (J) by the rules above. */
    public enum Batch {
        STEEL("electric_furnace/steel", true, 80 * Smelting.steelJPerKg()),
        ALLOY_STEEL("electric_furnace/alloy_steel", true, 100 * Smelting.steelJPerKg()),
        STAINLESS_STEEL("electric_furnace/stainless_steel", true, 100 * Smelting.steelJPerKg()),
        VALVE_STEEL("electric_furnace/valve_steel", true, 90 * Smelting.steelJPerKg()),
        INCONEL_718("electric_furnace/inconel_718", true, 90 * vacuumJPerKg(Alloy.INCONEL_718)),
        INCONEL_713C("electric_furnace/inconel_713c", true, 90 * vacuumJPerKg(Alloy.INCONEL_713C)),
        CMSX_4("electric_furnace/cmsx_4", true, 80 * vacuumJPerKg(Alloy.CMSX_4)),
        TITANIUM("electric_furnace/titanium", true, 100 * vacuumJPerKg(Alloy.TITANIUM)),
        TITANIUM_INGOT("electric_furnace/titanium_ingot", true, 10 * vacuumJPerKg(Alloy.TITANIUM)),
        FERROSILICON("electric_furnace/ferrosilicon", true, 10 * Smelting.ferrosiliconJPerKg()),
        FERROCHROME("electric_furnace/ferrochrome", true, 10 * Smelting.ferrochromeJPerKg()),
        TITANIUM_SLAG("electric_furnace/titanium_slag", true, 10 * Smelting.titaniumSlagJPerKg()),
        TITANIUM_SPONGE("electric_furnace/titanium_sponge", true, 30 * KROLL_KWH_PER_KG * J_PER_KWH),
        CARBON_FIBRE("electric_furnace/carbon_fibre", true, 3 * PITCH_FIBRE_J_PER_KG),
        ALUMINIUM("electrolysis/aluminium", false, 10 * Electrolysis.HALL_HEROULT.jPerKg()),
        MAGNESIUM("electrolysis/magnesium", false, 10 * Electrolysis.MAGNESIUM_CHLORIDE.jPerKg()),
        AMMONIUM_PERCHLORATE("electrolysis/ammonium_perchlorate", false,
                Electrolysis.ammoniumPerchlorateJPerKg(Electrolysis.SODIUM_CHLORATE, Electrolysis.SODIUM_PERCHLORATE));

        public final String recipe;
        public final boolean arcFurnace;
        public final double joules;

        Batch(String recipe, boolean arcFurnace, double joules) {
            this.recipe = recipe;
            this.arcFurnace = arcFurnace;
            this.joules = joules;
        }

        public double kwh() { return joules / J_PER_KWH; }

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** An alloy melted under vacuum (induction or arc) to 100 K above its melting point at VACUUM_EFFICIENCY, J/kg. */
    public static double vacuumJPerKg(Alloy a) {
        double melt = Casting.meltJ(1, a.solidSpecificHeat, 293.15, a.meltingK, a.latentHeat, a.liquidSpecificHeat, a.meltingK + 100);
        return Smelting.arcMeltJPerKg(melt, 0, VACUUM_EFFICIENCY);
    }

    /** The batch a recipe id is (null: not one). */
    public static Batch of(String recipe) {
        for (Batch b : Batch.values()) { if (b.recipe.equals(recipe)) { return b; } }
        return null;
    }
}
