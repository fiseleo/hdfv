package dev.mcmod.hdfv.petroleum;

import dev.mcmod.aerocore.api.petroleum.Conversion;
import dev.mcmod.aerocore.api.petroleum.CrudeAssay;
import dev.mcmod.aerocore.api.petroleum.Distillation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What hdfv's refinery and chemical plant make (0.8; the user asked for refining and petrochemicals 2026-10-13): each
 * unit's feeds, its products (hdfv's fluids, or items by their kg) and what it burns for its own heat (fuel gas,
 * hydrogen, coke: the rest of the feed), so the products and the burnt add up to the feed (mass is kept), and the
 * electricity its pumps and compressors take (J a kg of feed). Yields are AeroCore's (Conversion, CrudeAssay).
 *
 * <ul>
 * <li>Crude distillation: the gas cut gives LPG (60 %) and fuel gas; the heater burns the fuel gas and, when that is
 *     not enough, fuel oil, Distillation.heaterFuelShare of the crude at 40 MJ/kg and 85 %; naphtha (light and heavy),
 *     kerosene and diesel are drawn off; with the vacuum section the gas oil is drawn and the residue is bitumen,
 *     without it both leave as fuel oil.</li>
 * <li>The reformer (naphtha to 98-octane reformate), FCC (gas oil to gasoline, LPG, light cycle oil into diesel, slurry
 *     into fuel oil), steam crackers (naphtha or LPG to ethylene, propylene, butadiene and aromatics), each burning its
 *     own fuel gas, hydrogen and coke. A lube plant takes half the gas oil to lubricating oil, the rest fuel oil.</li>
 * <li>The polymer plant: polyethylene and polypropylene from their monomers; synthetic rubber (SBR: butadiene and
 *     styrene from the aromatics, 3 to 1); epoxy resin (bisphenol A from the aromatics, epichlorohydrin from propylene);
 *     carbon fibre from propylene by way of acrylonitrile and PAN (half the propylene's mass comes out as fibre).</li>
 * </ul>
 * Electricity: pumps 20 kJ/kg; the reformer's recycle and the FCC's air blower 50 and 80 kJ/kg; a cracker's compressors
 * 0.4 MJ/kg; polymerising 0.5 to 1 MJ/kg (high-pressure polyethylene the most), epoxy 2 MJ/kg, carbonising fibre
 * 25 MJ/kg of feed (typical figures).
 * <p>Operating conditions (0.9; the user asked for the refinery's screens to set them): the column's cut points (where
 * naphtha, kerosene and diesel end; the heater heats the crude to 10 C over the diesel's end), the FCC's riser
 * temperature, the reformer's octane (reformate under 98 is regular gasoline) and the crackers' coil temperature, the
 * yields from AeroCore at the conditions (Conversion.fcc, reformer, steamCracking; CrudeAssay.massBoiledBelow); the
 * nominal conditions give the tables above.</p>
 */
public final class Refining {
    private Refining() {}

    /** A unit's conversion: feeds (fluid to kg a kg of feed), products (fluid or item to kg), burnt (kg), electricity (J a kg of feed). */
    public record Yield(String name, Map<String, Double> feeds, Map<String, Double> products, double burnt, double gridJPerKg) {
        public Yield { feeds = Map.copyOf(feeds); products = Map.copyOf(products); }
        /** Feed in, products and burnt out, a kg of feed: their difference (0 when mass is kept). */
        public double imbalance() {
            double in = 0, out = burnt;
            for (double v : feeds.values()) { in += v; }
            for (double v : products.values()) { out += v; }
            return out - in;
        }

        /** This conversion with a product burnt instead (flared with the fuel gas). */
        public Yield flaring(String product) {
            Double kg = products.get(product);
            if (kg == null) { return this; }
            Map<String, Double> p = new LinkedHashMap<>(products);
            p.remove(product);
            return new Yield(name, feeds, p, burnt + kg, gridJPerKg);
        }

        /** This conversion with a product compressed for storage (0.10: hydrogen to 700 bar), its electricity added. */
        public Yield compressing(String product, double jPerKg) {
            Double kg = products.get(product);
            return kg == null ? this : new Yield(name, feeds, products, burnt, gridJPerKg + kg * jPerKg);
        }
    }

    /**
     * Products that leave a unit only by an outlet routed for them on its screen (0.10): the reformer's hydrogen,
     * compressed to 700 bar on its way (HydrogenStorage.COMPRESSION_J_PER_KG); unrouted it goes to the fuel gas as before
     * 0.10, so a plant built earlier runs as it did. (A steam cracker burns its hydrogen with its methane: its furnace
     * needs both.)
     */
    public static final java.util.Set<String> ROUTED_ONLY = java.util.Set.of("hydrogen");

    /** Items the plant makes and their mass, kg each (as the workshop counts them: two rubbers a 10 kg stock, two fibres and an epoxy a CFRP stock). */
    public static final Map<String, Double> ITEM_KG = Map.of("bitumen", 10.0, "polyethylene", 5.0, "polypropylene", 5.0, "synthetic_rubber", 5.0,
            "epoxy_resin", 4.0, "carbon_fibre", 3.0);

    public static boolean item(String product) { return ITEM_KG.containsKey(product); }

    public static final double HEATER_FUEL_J_PER_KG = 40e6, HEATER_EFFICIENCY = 0.85, LPG_OF_GAS = 0.6, PUMPING_J_PER_KG = 20e3;

    /** A column's cut points (0.9), C: where its naphtha, kerosene and diesel end (the boiling points they are drawn at). */
    public record Cuts(double naphthaEndC, double keroseneEndC, double dieselEndC) {
        public static final double NAPHTHA_MIN = 150, NAPHTHA_MAX = 200, KEROSENE_MIN = 220, KEROSENE_MAX = 270, DIESEL_MIN = 320, DIESEL_MAX = 380;
        /** CrudeAssay's own cuts: the tables above. */
        public static final Cuts NOMINAL = new Cuts(180, 240, 350);

        public Cuts {
            if (!(naphthaEndC >= NAPHTHA_MIN && naphthaEndC <= NAPHTHA_MAX) || !(keroseneEndC >= KEROSENE_MIN && keroseneEndC <= KEROSENE_MAX)
                    || !(dieselEndC >= DIESEL_MIN && dieselEndC <= DIESEL_MAX)) {
                throw new IllegalArgumentException("Cut points: naphtha 150-200, kerosene 220-270, diesel 320-380 C");
            }
        }

        /** The flash zone's temperature, C: 10 over the diesel's end. */
        public double flashC() { return dieselEndC + 10; }
    }

    /** Crude distillation of a crude ("crude_oil" light sweet, "heavy_crude_oil" heavy sour), with or without the vacuum section. */
    public static Yield distillation(String crude, boolean vacuum) {
        CrudeAssay a = crude.equals("heavy_crude_oil") ? CrudeAssay.HEAVY_SOUR : CrudeAssay.LIGHT_SWEET;
        double gas = a.massYield(CrudeAssay.Cut.GAS), heater = Distillation.heaterFuelShare(a, HEATER_FUEL_J_PER_KG, HEATER_EFFICIENCY);
        double fuelGas = (1 - LPG_OF_GAS) * gas, burnt = Math.max(fuelGas, heater);
        double vgo = a.massYield(CrudeAssay.Cut.VACUUM_GAS_OIL), residue = a.massYield(CrudeAssay.Cut.RESIDUE);
        double fromResidue = burnt - fuelGas;   // the fuel oil the heater burns when the fuel gas is short
        Map<String, Double> p = new LinkedHashMap<>();
        p.put("lpg", LPG_OF_GAS * gas);
        p.put("naphtha", a.massYield(CrudeAssay.Cut.LIGHT_NAPHTHA) + a.massYield(CrudeAssay.Cut.HEAVY_NAPHTHA));
        p.put("kerosene", a.massYield(CrudeAssay.Cut.KEROSENE));
        p.put("diesel", a.massYield(CrudeAssay.Cut.DIESEL));
        if (vacuum) {
            p.put("gas_oil", vgo);
            p.put("bitumen", residue - fromResidue);
        } else {
            p.put("fuel_oil", vgo + residue - fromResidue);
        }
        return new Yield(vacuum ? "vacuum_distillation" : "distillation", Map.of(crude, 1.0), p, burnt, PUMPING_J_PER_KG);
    }

    /** The same at a column's cut points (0.9; the nominal ones give the above). */
    public static Yield distillation(String crude, boolean vacuum, Cuts cuts) {
        if (cuts.equals(Cuts.NOMINAL)) { return distillation(crude, vacuum); }
        CrudeAssay a = crude.equals("heavy_crude_oil") ? CrudeAssay.HEAVY_SOUR : CrudeAssay.LIGHT_SWEET;
        double gasEnd = CrudeAssay.Cut.GAS.toC, vgoEnd = CrudeAssay.Cut.VACUUM_GAS_OIL.toC;
        double gas = a.massBoiledBelow(gasEnd), heater = Distillation.heaterFuelShare(a, cuts.flashC(), HEATER_FUEL_J_PER_KG, HEATER_EFFICIENCY);
        double fuelGas = (1 - LPG_OF_GAS) * gas, burnt = Math.max(fuelGas, heater), fromResidue = burnt - fuelGas;
        double vgo = a.massBoiledBelow(vgoEnd) - a.massBoiledBelow(cuts.dieselEndC()), residue = 1 - a.massBoiledBelow(vgoEnd);
        Map<String, Double> p = new LinkedHashMap<>();
        p.put("lpg", LPG_OF_GAS * gas);
        p.put("naphtha", a.massBoiledBelow(cuts.naphthaEndC()) - gas);
        p.put("kerosene", a.massBoiledBelow(cuts.keroseneEndC()) - a.massBoiledBelow(cuts.naphthaEndC()));
        p.put("diesel", a.massBoiledBelow(cuts.dieselEndC()) - a.massBoiledBelow(cuts.keroseneEndC()));
        if (vacuum) {
            p.put("gas_oil", vgo);
            p.put("bitumen", residue - fromResidue);
        } else {
            p.put("fuel_oil", vgo + residue - fromResidue);
        }
        return new Yield(vacuum ? "vacuum_distillation" : "distillation", Map.of(crude, 1.0), p, burnt, PUMPING_J_PER_KG);
    }

    /** The order distillation's products leave by its outlets, bottom to top (an empty name: none). */
    public static List<String> distillationOutlets(boolean vacuum) {
        return vacuum ? List.of("", "gas_oil", "diesel", "kerosene", "naphtha", "lpg") : List.of("fuel_oil", "", "diesel", "kerosene", "naphtha", "lpg");
    }

    private static Yield of(String name, Conversion c, Map<String, String> into, String feed, double gridJPerKg) {
        Map<String, Double> p = new LinkedHashMap<>();
        double burnt = 0;
        for (Map.Entry<String, Double> e : c.yields().entrySet()) {
            String to = into.get(e.getKey());
            if (to == null) { burnt += e.getValue(); } else { p.merge(to, e.getValue(), Double::sum); }
        }
        return new Yield(name, Map.of(feed, 1.0), p, burnt, gridJPerKg);
    }

    private static final Map<String, String> FCC_INTO = Map.of("lpg", "lpg", "gasoline", "gasoline", "light_cycle_oil", "diesel", "slurry", "fuel_oil");
    private static final Map<String, String> NAPHTHA_CRACKER_INTO = Map.of("ethylene", "ethylene", "propylene", "propylene", "butadiene", "butadiene",
            "aromatics", "aromatics", "c4", "lpg", "fuel_oil", "fuel_oil");
    private static final Map<String, String> LPG_CRACKER_INTO = Map.of("ethylene", "ethylene", "propylene", "propylene", "butadiene", "butadiene",
            "aromatics", "aromatics", "c4", "lpg");

    public static final Yield REFORMER = of("reformer", Conversion.REFORMER, Map.of("reformate", "premium_gasoline", "lpg", "lpg", "hydrogen", "hydrogen"), "naphtha", 50e3);
    public static final Yield FCC = of("fcc", Conversion.FCC, FCC_INTO, "gas_oil", 80e3);
    public static final Yield NAPHTHA_CRACKER = of("naphtha_cracker", Conversion.NAPHTHA_CRACKER, NAPHTHA_CRACKER_INTO, "naphtha", 0.4e6);
    public static final Yield LPG_CRACKER = of("lpg_cracker", Conversion.PROPANE_CRACKER, LPG_CRACKER_INTO, "lpg", 0.4e6);

    /** The FCC at a riser outlet temperature, C (0.9; 480-560, FCC at 530). */
    public static Yield fcc(double riserOutletC) {
        return riserOutletC == Conversion.NOMINAL_RISER_C ? FCC : of("fcc", Conversion.fcc(riserOutletC), FCC_INTO, "gas_oil", 80e3);
    }

    /** The reformer to a research octane (0.9; 95-104, REFORMER at 98): reformate of 98 or more is premium gasoline, else regular. */
    public static Yield reformer(double researchOctane) {
        if (researchOctane == Conversion.NOMINAL_OCTANE) { return REFORMER; }
        String into = researchOctane >= 98 ? "premium_gasoline" : "gasoline";
        return of("reformer", Conversion.reformer(researchOctane), Map.of("reformate", into, "lpg", "lpg", "hydrogen", "hydrogen"), "naphtha", 50e3);
    }

    /** A steam cracker on a feed ("naphtha" or "lpg") at a coil outlet temperature, C (0.9; 800-860, the crackers above at 835). */
    public static Yield cracker(String feed, double coilOutletC) {
        boolean naphtha = feed.equals("naphtha");
        if (coilOutletC == Conversion.NOMINAL_COIL_C) { return naphtha ? NAPHTHA_CRACKER : LPG_CRACKER; }
        return naphtha ? of("naphtha_cracker", Conversion.steamCracking("naphtha", coilOutletC), NAPHTHA_CRACKER_INTO, "naphtha", 0.4e6)
                : of("lpg_cracker", Conversion.steamCracking("lpg", coilOutletC), LPG_CRACKER_INTO, "lpg", 0.4e6);
    }
    public static final Yield LUBE = new Yield("lube", Map.of("gas_oil", 1.0), Map.of("lubricating_oil", 0.5, "fuel_oil", 0.45), 0.05, 0.1e6);
    public static final Yield POLYETHYLENE = new Yield("polyethylene", Map.of("ethylene", 1.0), Map.of("polyethylene", 0.98), 0.02, 1.0e6);
    public static final Yield POLYPROPYLENE = new Yield("polypropylene", Map.of("propylene", 1.0), Map.of("polypropylene", 0.98), 0.02, 0.5e6);
    public static final Yield RUBBER = new Yield("synthetic_rubber", Map.of("butadiene", 0.75, "aromatics", 0.25), Map.of("synthetic_rubber", 0.97), 0.03, 0.5e6);
    public static final Yield EPOXY = new Yield("epoxy_resin", Map.of("aromatics", 0.6, "propylene", 0.4), Map.of("epoxy_resin", 0.9), 0.1, 2e6);
    public static final Yield CARBON_FIBRE = new Yield("carbon_fibre", Map.of("propylene", 1.0), Map.of("carbon_fibre", 0.5), 0.5, 25e6);

    /**
     * 0.23: methanol from LPG and water in the reformer's unit (steam reforming and synthesis, AeroCore Methanol: 0.46 kg of
     * propane reacts a kg of methanol, METHANOL_FIRED more is burned firing the reformer, about 30.8 GJ of feed a tonne;
     * the hydrogen left over is a product only when routed, as the reformer's). Methanol had a fuel grade and no maker.
     */
    public static final double METHANOL_FIRED = 0.45;
    public static final Yield METHANOL = methanol();

    private static Yield methanol() {
        double propane = dev.mcmod.aerocore.api.petroleum.Methanol.feedKgPerKg(METHANOL_FIRED), water = dev.mcmod.aerocore.api.petroleum.Methanol.waterKgPerKg();
        double hydrogen = dev.mcmod.aerocore.api.petroleum.Methanol.hydrogenKgPerKg(), feed = propane + water;
        return new Yield("methanol", Map.of("lpg", propane / feed, "water", water / feed), Map.of("methanol", 1 / feed, "hydrogen", hydrogen / feed),
                (feed - 1 - hydrogen) / feed, 0.4e6);
    }

    /** The polymer plant's products, in the order its controller steps through them. */
    public static final List<Yield> POLYMERS = List.of(POLYETHYLENE, POLYPROPYLENE, RUBBER, EPOXY, CARBON_FIBRE);

    /** Every unit's conversions, for the tests and JEI. */
    public static List<Yield> all() {
        return List.of(distillation("crude_oil", false), distillation("crude_oil", true), distillation("heavy_crude_oil", false), distillation("heavy_crude_oil", true),
                REFORMER, METHANOL, FCC, NAPHTHA_CRACKER, LPG_CRACKER, LUBE, POLYETHYLENE, POLYPROPYLENE, RUBBER, EPOXY, CARBON_FIBRE);
    }
}
