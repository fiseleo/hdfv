package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.vehicle.Material;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.Locale;

/**
 * The materials parts are made of, as stock (0.7): each design material maps to one of these, and the game's stock
 * items (bars, plates, sheets, castings) carry it.
 *
 * <ul>
 * <li>Family: what it is smelted from and how it is joined.</li>
 * <li>Specific cutting energy (J/m^3) and cutting speed with carbide tools (m/s; high-speed steel about a third):
 *     Kalpakjian and Schmid tables 21.2 and 22.4, the middle of each range (aluminium 0.4-1.0 J/mm^3, grey iron
 *     1.1-5.4, carbon steel 2.7-9.3, stainless 3-5.2, titanium 3-4.1, nickel alloys 4.8-6.7, copper alloys 1.4-3.2,
 *     magnesium 0.4-0.6).</li>
 * <li>Melting point, latent heat and specific heats for melting a casting's metal (Groover table 10.1 and handbook
 *     values); the sand mould's Chvorinov constant (Groover's example 2.2 s/mm^2; investment shells for the
 *     superalloys run hotter, slower).</li>
 * <li>Deposition rate of its usual arc process (kg/h; Lincoln Electric: MIG on steel 2-5, on aluminium about 2; TIG
 *     0.5-1).</li>
 * </ul>
 */
public enum Alloy {
    GREY_IRON(Family.IRON, 7150, 1.6e9, 2.0, 1473, 272e3, 460, 830, 2.2e6, true, false, 3.0),
    DUCTILE_IRON(Family.IRON, 7100, 2.0e9, 1.8, 1450, 272e3, 460, 830, 2.2e6, true, false, 3.0),
    STEEL(Family.STEEL, 7850, 3.0e9, 3.0, 1773, 272e3, 486, 820, 2.5e6, true, true, 3.5),
    ALLOY_STEEL(Family.STEEL, 7850, 4.0e9, 2.0, 1748, 272e3, 475, 820, 2.5e6, true, true, 3.5),
    VALVE_STEEL(Family.STEEL, 7750, 4.5e9, 1.5, 1700, 270e3, 460, 800, 2.5e6, false, true, 1.0),
    STAINLESS_STEEL(Family.STAINLESS, 8000, 4.0e9, 1.7, 1700, 270e3, 500, 800, 2.5e6, true, true, 0.8),
    ALUMINIUM_CAST(Family.ALUMINIUM, 2680, 0.7e9, 5.0, 888, 389e3, 963, 1180, 1.6e6, true, false, 2.0),
    ALUMINIUM_PISTON(Family.ALUMINIUM, 2700, 0.8e9, 4.5, 845, 389e3, 864, 1180, 1.6e6, true, true, 2.0),
    ALUMINIUM_2618(Family.ALUMINIUM, 2760, 0.8e9, 4.5, 822, 389e3, 875, 1180, 1.6e6, false, true, 2.0),
    ALUMINIUM_7075(Family.ALUMINIUM, 2810, 0.9e9, 4.0, 750, 389e3, 960, 1180, 1.6e6, false, true, 1.5),
    ALUMINIUM_2024(Family.ALUMINIUM, 2780, 0.9e9, 4.0, 775, 389e3, 875, 1180, 1.6e6, false, true, 1.5),
    MAGNESIUM(Family.MAGNESIUM, 1810, 0.45e9, 6.0, 868, 368e3, 1000, 1360, 1.4e6, true, false, 1.5),
    TITANIUM(Family.TITANIUM, 4430, 3.5e9, 0.8, 1928, 365e3, 526, 830, 3.0e6, true, true, 0.6),
    INCONEL_718(Family.NICKEL, 8190, 6.0e9, 0.5, 1609, 298e3, 435, 720, 4.0e6, false, true, 0.6),
    INCONEL_713C(Family.NICKEL, 7910, 6.0e9, 0.4, 1561, 298e3, 420, 720, 4.0e6, true, false, 0.6),
    CMSX_4(Family.NICKEL, 8700, 6.5e9, 0.3, 1653, 298e3, 400, 720, 6.0e6, true, false, 0.6),
    BRONZE(Family.COPPER, 7600, 2.2e9, 4.0, 1223, 200e3, 380, 500, 2.0e6, true, true, 1.5),
    COPPER(Family.COPPER, 8960, 2.5e9, 4.0, 1358, 205e3, 385, 495, 2.0e6, true, true, 1.0),
    CFRP(Family.COMPOSITE, 1550, 1.0e9, 3.0, 0, 0, 1000, 1000, 0, false, false, 0),
    GFRP(Family.COMPOSITE, 1900, 1.0e9, 3.0, 0, 0, 1000, 1000, 0, false, false, 0),
    SPRUCE(Family.WOOD, 430, 0.05e9, 15, 0, 0, 1700, 1700, 0, false, false, 0),
    OAK(Family.WOOD, 750, 0.08e9, 12, 0, 0, 1700, 1700, 0, false, false, 0),
    PLYWOOD(Family.WOOD, 600, 0.07e9, 12, 0, 0, 1700, 1700, 0, false, false, 0),
    RUBBER(Family.RUBBER, 1150, 0.05e9, 5, 0, 0, 1900, 1900, 0, false, false, 0);

    /** What a material is made from and joined as. */
    public enum Family { IRON, STEEL, STAINLESS, ALUMINIUM, MAGNESIUM, TITANIUM, NICKEL, COPPER, COMPOSITE, WOOD, RUBBER;
        public boolean metal() { return ordinal() <= COPPER.ordinal(); }
    }

    public final Family family;
    public final double density, specificEnergy, carbideSpeed, meltingK, latentHeat, solidSpecificHeat, liquidSpecificHeat, mouldConstant;
    public final boolean castable, forgeable;
    public final double depositionKgPerH;

    Alloy(Family family, double density, double specificEnergy, double carbideSpeed, double meltingK, double latentHeat, double solidSpecificHeat,
          double liquidSpecificHeat, double mouldConstant, boolean castable, boolean forgeable, double depositionKgPerH) {
        this.family = family; this.density = density; this.specificEnergy = specificEnergy; this.carbideSpeed = carbideSpeed; this.meltingK = meltingK;
        this.latentHeat = latentHeat; this.solidSpecificHeat = solidSpecificHeat; this.liquidSpecificHeat = liquidSpecificHeat;
        this.mouldConstant = mouldConstant; this.castable = castable; this.forgeable = forgeable; this.depositionKgPerH = depositionKgPerH;
    }

    /** The id the game's items and data carry ("grey_iron", "aluminium_2024"). */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Alloy byId(String id) {
        for (Alloy a : values()) { if (a.id().equals(id)) { return a; } }
        throw new IllegalArgumentException("No alloy " + id);
    }

    /** The stock an engine material is made from; a custom material from the metal of the nearest density. */
    public static Alloy of(EngineMaterial m) {
        return switch (m.name()) {
            case "grey_iron" -> GREY_IRON;
            case "ductile_iron" -> DUCTILE_IRON;
            case "aluminium_a356" -> ALUMINIUM_CAST;
            case "aluminium_4032" -> ALUMINIUM_PISTON;
            case "aluminium_2618" -> ALUMINIUM_2618;
            case "aluminium_7075" -> ALUMINIUM_7075;
            case "aluminium_2024" -> ALUMINIUM_2024;
            case "steel_1045" -> STEEL;
            case "steel_4340" -> ALLOY_STEEL;
            case "valve_steel" -> VALVE_STEEL;
            case "titanium_6al4v" -> TITANIUM;
            case "magnesium_az91" -> MAGNESIUM;
            case "stainless_321" -> STAINLESS_STEEL;
            case "inconel_718" -> INCONEL_718;
            case "inconel_713c" -> INCONEL_713C;
            case "cmsx_4" -> CMSX_4;
            case "spruce" -> SPRUCE;
            case "cfrp" -> CFRP;
            case "gfrp" -> GFRP;
            // 0.10: reeds are cut from alloy-steel strip, liners from a nickel alloy, nozzle throats from carbon (the composite stock)
            case "spring_steel" -> ALLOY_STEEL;
            case "hastelloy_x" -> INCONEL_718;
            case "graphite" -> CFRP;
            default -> nearest(m.densityKgPerM3());
        };
    }

    /** The stock an AeroCore material is made from. */
    public static Alloy of(Material m) {
        return switch (m.name()) {
            case "steel" -> STEEL;
            case "stainless_steel" -> STAINLESS_STEEL;
            case "aluminium" -> ALUMINIUM_CAST;
            case "titanium" -> TITANIUM;
            case "bronze" -> BRONZE;
            case "grp" -> GFRP;
            case "cfrp" -> CFRP;
            case "plywood" -> PLYWOOD;
            case "oak" -> OAK;
            default -> nearest(m.densityKgPerM3());
        };
    }

    /** The metal (iron to copper families) of the nearest density. */
    static Alloy nearest(double density) {
        Alloy best = STEEL;
        for (Alloy a : values()) {
            if (a.family.metal() && Math.abs(a.density - density) < Math.abs(best.density - density)) { best = a; }
        }
        return best;
    }
}
