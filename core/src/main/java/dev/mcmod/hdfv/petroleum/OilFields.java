package dev.mcmod.hdfv.petroleum;

import dev.mcmod.aerocore.api.petroleum.CrudeAssay;
import dev.mcmod.aerocore.api.petroleum.Lift;
import dev.mcmod.aerocore.api.petroleum.Reservoir;

import java.util.Random;

/**
 * Oil fields (0.8; the user asked for a petroleum industry 2026-10-13), worked out from the world's seed so every world
 * has its own and nothing is stored but what has been produced.
 *
 * <ul>
 * <li>The world is cut into REGION-block squares; FIELD_CHANCE of them hold a field, a disc 40 to 150 m across its
 *     radius well inside its square.</li>
 * <li>Each field: 150 to 800 m deep (shallow fields, as the first oil country's), 5 to 30 m of net pay, porosity
 *     0.10 to 0.30, oil saturation 0.60 to 0.85, permeability 20 to 500 mD (log-uniform), light sweet crude (60 %) or
 *     heavy sour; the pressure the water column's (10 kPa a metre) times 0.8 to 1.3 (under- to overpressured), so some fields flow and some must be
 *     pumped from the start; abandonment at 15 % of it; primary recovery 35 % for light oil, 12 % for heavy; the rock
 *     above 30 to 150 MPa (sandstone to limestone). Light oil at reservoir conditions about 3 mPa s and a formation
 *     volume factor 1.25, heavy oil 100 mPa s and 1.05 (Craft and Hawkins; typical values).</li>
 * <li>A well drains the field to a radius of at most 200 m from a 0.1 m bore (AeroCore Reservoir).</li>
 * </ul>
 */
public final class OilFields {
    private OilFields() {}

    public static final int REGION = 512;
    public static final double FIELD_CHANCE = 0.35, WELL_RADIUS_M = 0.1, MAX_DRAINAGE_M = 200;

    /** A field: its id (its region), centre and radius (blocks), depth (m), the reservoir, its crude, the rock's strength (Pa). */
    public record Field(long id, int centreX, int centreZ, double radiusM, double depthM, Reservoir reservoir, boolean light, double rockStrengthPa) {
        public boolean contains(int x, int z) { double dx = x + 0.5 - centreX, dz = z + 0.5 - centreZ; return dx * dx + dz * dz <= radiusM * radiusM; }
        public CrudeAssay crude() { return light ? CrudeAssay.LIGHT_SWEET : CrudeAssay.HEAVY_SOUR; }
        /** The fluid it gives (hdfv's fluid id). */
        public String fluid() { return light ? "crude_oil" : "heavy_crude_oil"; }
        public double oilDensity() { return crude().densityKgM3(); }
        /** A well's productivity index, m^3/s per Pa. */
        public double productivityIndex() { return reservoir.productivityIndex(Math.min(radiusM, MAX_DRAINAGE_M), WELL_RADIUS_M); }
        /** What a well flows by itself after the field has given a volume (m^3), m^3/s (0: it needs pumping). */
        public double naturalRate(double producedM3) { return reservoir.naturalRate(productivityIndex(), depthM, oilDensity(), 0, producedM3); }
        /** What a well flows by itself through a wellhead choke of a bore (m; 0: none) after a volume, m^3/s (0.9; AeroCore Reservoir.chokedRate). */
        public double chokedRate(double chokeBoreM, double producedM3) {
            return chokeBoreM > 0 ? reservoir.chokedRate(productivityIndex(), depthM, oilDensity(), chokeBoreM, producedM3) : naturalRate(producedM3);
        }
        /** What a pumped well can draw at most (the pump holding the bottom hole near empty) after a volume, m^3/s. */
        public double pumpedInflow(double producedM3) { return Reservoir.vogelRate(productivityIndex(), reservoir.pressurePa(producedM3), 0); }
        /** The volume produced by which the field stops flowing by itself, m^3. */
        public double flowsUntilM3() {
            double lift = oilDensity() * 9.81 * depthM, p0 = reservoir.initialPressurePa(), pa = reservoir.abandonmentPressurePa();
            if (lift >= p0) { return 0; }
            return reservoir.recoverableM3() * (p0 - lift) / (p0 - pa);
        }
        /** Seconds of drilling a power (W) makes a metre at with a bit (m) (Teale's specific energy; AeroCore Lift). */
        public double drillSPerM(double powerW, double bitM) { return 1 / Lift.penetrationMPerS(powerW, bitM, rockStrengthPa); }
    }

    /** The field under a block column (null: none). */
    public static Field at(long seed, int x, int z) {
        Field f = field(seed, Math.floorDiv(x, REGION), Math.floorDiv(z, REGION));
        return f != null && f.contains(x, z) ? f : null;
    }

    /** The region key of a block column (a field's id). */
    public static long regionKey(int x, int z) { return key(Math.floorDiv(x, REGION), Math.floorDiv(z, REGION)); }

    static long key(int rx, int rz) { return ((long) rx << 32) ^ (rz & 0xffffffffL); }

    /** A region's field (null: none). */
    public static Field field(long seed, int rx, int rz) {
        Random r = new Random(seed * 0x9E3779B97F4A7C15L ^ key(rx, rz) * 0xC2B2AE3D27D4EB4FL ^ 0x6F696C6669656C64L);
        if (r.nextDouble() >= FIELD_CHANCE) { return null; }
        int cx = rx * REGION + 160 + r.nextInt(REGION - 320), cz = rz * REGION + 160 + r.nextInt(REGION - 320);
        double radius = 40 + 110 * r.nextDouble(), depth = 150 + 650 * r.nextDouble(), pay = 5 + 25 * r.nextDouble();
        double porosity = 0.10 + 0.20 * r.nextDouble(), saturation = 0.60 + 0.25 * r.nextDouble();
        double permeability = Math.exp(Math.log(20) + (Math.log(500) - Math.log(20)) * r.nextDouble());
        boolean light = r.nextDouble() < 0.6;
        double pressure = 1e4 * depth * (0.8 + 0.5 * r.nextDouble()), rock = 30e6 + 120e6 * r.nextDouble();
        return of(key(rx, rz), cx, cz, radius, depth, pay, porosity, saturation, permeability, light, pressure, rock);
    }

    /** A field from its parameters (permeability in mD, pressure in Pa). */
    public static Field of(long id, int cx, int cz, double radiusM, double depthM, double payM, double porosity, double saturation, double permeabilityMd,
                           boolean light, double pressurePa, double rockStrengthPa) {
        Reservoir res = new Reservoir(Math.PI * radiusM * radiusM, payM, porosity, saturation, light ? 1.25 : 1.05, permeabilityMd * Reservoir.MILLIDARCY_M2,
                light ? 3e-3 : 0.1, pressurePa, 0.15 * pressurePa, light ? 0.35 : 0.12);
        return new Field(id, cx, cz, radiusM, depthM, res, light, rockStrengthPa);
    }
}
