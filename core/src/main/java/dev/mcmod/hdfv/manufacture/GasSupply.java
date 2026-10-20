package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.manufacture.AirSeparation;
import dev.mcmod.aerocore.api.manufacture.Gases;

import java.util.Locale;

/**
 * The air separation unit's rules (0.17; the user chose a unit making oxygen, nitrogen and argon for the cutting
 * tables, argon kept for TIG later): what it fills a 50 litre, 200 bar cylinder with and what that costs.
 *
 * <ul>
 * <li>A cylinder holds p V / (R T) of its gas at 15 C (AeroCore Gases): 13.4 kg of oxygen, 11.7 of nitrogen, 16.7 of
 *     argon.</li>
 * <li>Energy a kilogram: the least work of drawing it from air (AeroCore AirSeparation, at 298 K) over a small plant's
 *     second-law efficiency SECOND_LAW (a rule: large cryogenic plants reach 0.1-0.2 of it, a small one the low end),
 *     and compressing it from 1 to 200 bar at COMPRESSOR of the isothermal work: oxygen about 0.47 kWh/kg, nitrogen 0.26,
 *     argon 0.82.</li>
 * <li>It makes OXYGEN_KG_PER_HOUR of oxygen or nitrogen an hour of work, argon at that times its share of air over
 *     oxygen's (0.56 kg/h).</li>
 * </ul>
 */
public final class GasSupply {
    private GasSupply() {}

    public static final double SECOND_LAW = 0.12, COMPRESSOR = 0.6, OXYGEN_KG_PER_HOUR = 10, CYLINDER_M3 = 0.05, CYLINDER_PA = 2e7, CYLINDER_K = 288.15;

    /** The gases it makes. */
    public enum Product {
        OXYGEN(Gases.Gas.OXYGEN, AirSeparation.O2_MOLE, AirSeparation.O2_MASS),
        NITROGEN(Gases.Gas.NITROGEN, AirSeparation.N2_MOLE, AirSeparation.N2_MASS),
        ARGON(Gases.Gas.ARGON, AirSeparation.AR_MOLE, AirSeparation.AR_MASS);

        public final Gases.Gas gas;
        final double moleFraction, massFraction;

        Product(Gases.Gas gas, double moleFraction, double massFraction) { this.gas = gas; this.moleFraction = moleFraction; this.massFraction = massFraction; }

        public String id() { return name().toLowerCase(Locale.ROOT); }

        /** What a cylinder holds, kg. */
        public double cylinderKg() { return Gases.cylinderKg(gas, CYLINDER_M3, CYLINDER_PA, CYLINDER_K, 1); }

        /** Electricity a kilogram, J: separating it and compressing it into the cylinder. */
        public double joulesPerKg() {
            double separate = AirSeparation.plantJPerKg(AirSeparation.minimumJPerKg(moleFraction, gas.molarMass, 298.15), SECOND_LAW);
            return separate + Gases.isothermalJ(gas, 1, 293.15, 1e5, CYLINDER_PA) / COMPRESSOR;
        }

        /** What it makes an hour of work, kg. */
        public double kgPerHour() { return this == ARGON ? OXYGEN_KG_PER_HOUR * massFraction / AirSeparation.O2_MASS : OXYGEN_KG_PER_HOUR; }

        /** The electrical power while making it, W. */
        public double powerW() { return kgPerHour() / 3600 * joulesPerKg(); }

        /** The next one (a unit is switched round them). */
        public Product next() { return values()[(ordinal() + 1) % values().length]; }

        /** The supply item a full cylinder of it is. */
        public Consumable.Supply supply() {
            return switch (this) { case OXYGEN -> Consumable.Supply.OXYGEN; case NITROGEN -> Consumable.Supply.NITROGEN; case ARGON -> Consumable.Supply.ARGON; };
        }
    }
}
