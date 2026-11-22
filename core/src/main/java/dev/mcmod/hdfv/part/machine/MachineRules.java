package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Estimates shared by the heavy-machinery designs (0.26): hydraulic cylinders, displacement machines, planetary
 * reducers, welded box sections and the contact spheres along a link. Rules with their sources; the physics of the
 * parts is AeroCore's (api.hydraulic, api.lifting, the runtime parts in api.vehicle.part).
 */
public final class MachineRules {
    private MachineRules() {}

    public static final double G = 9.80665;
    /** Carbon and low-alloy steel, kg/m^3. */
    public static final double STEEL_KG_PER_M3 = 7850;
    /** Yield of the usual structural plate of undercarriages and frames (EN 10025 S355), Pa. */
    public static final double S355_PA = 355e6;

    // ---------------------------------------------------------------- hydraulic cylinders

    /**
     * A cylinder's dead length (pin to pin when closed, less its stroke: the two eyes, the gland and the piston), m:
     * DEAD_BASE_M + DEAD_BORES x bore. A rule of the order of welded mobile-machinery cylinders' catalogues (closed
     * length about the stroke plus three to four bores); e.g. a 120 mm boom cylinder of 1.335 m stroke closes to 1.85 m.
     */
    public static final double DEAD_BASE_M = 0.15, DEAD_BORES = 3;

    public static double deadLengthM(double boreM) { return DEAD_BASE_M + DEAD_BORES * boreM; }

    /** Pin-to-pin length of a cylinder closed, m: its stroke and its dead length. */
    public static double closedLengthM(double boreM, double strokeM) { return strokeM + deadLengthM(boreM); }

    /**
     * The barrel's wall (m): thin-walled hoop stress at the relief pressure kept to TUBE_SHARE of the honed tube's yield
     * (E355 / St52 cylinder tube, 355 MPa), at least MIN_WALL_M: t = p D / (2 TUBE_SHARE R_e).
     */
    public static final double TUBE_YIELD_PA = 355e6, TUBE_SHARE = 0.5, MIN_WALL_M = 0.004;

    /** The heads, the gland, the piston and the eyes as END_BORES3 x bore^3 of steel (a rule: 27 kg at 120 mm). */
    public static final double END_BORES3 = 2.0;

    public static double barrelWallM(double boreM, double pressurePa) {
        return Math.max(MIN_WALL_M, Math.max(pressurePa, 0) * boreM / (2 * TUBE_SHARE * TUBE_YIELD_PA));
    }

    /**
     * One cylinder's mass, kg: its barrel (the closed length of tube), a solid rod as long as the stroke and half the
     * dead length, and its ends. A 120/85 x 1335 mm boom cylinder at 350 bar comes out at about 170 kg (a 20 t
     * excavator's boom cylinders weigh 130 to 180 kg each).
     */
    public static double cylinderKg(double boreM, double rodM, double strokeM, double pressurePa) {
        double wall = barrelWallM(boreM, pressurePa), length = closedLengthM(boreM, strokeM);
        double barrel = Math.PI * (boreM + wall) * wall * length;
        double rod = Math.PI / 4 * rodM * rodM * (strokeM + 0.5 * deadLengthM(boreM));
        double ends = END_BORES3 * boreM * boreM * boreM;
        return STEEL_KG_PER_M3 * (barrel + rod + ends);
    }

    // ---------------------------------------------------------------- displacement machines

    /**
     * Mass of a displacement machine of a displacement (cm^3 per revolution), kg: a line fitted to catalogue masses.
     * Axial piston pumps (Bosch Rexroth A10VO, open circuit: size 28 about 15 kg, 45 about 21, 71 about 33, 100 about
     * 45, 140 about 60): 4 + 0.42 V. Axial piston motors (Rexroth A2FM: size 56 about 18 kg, 107 about 34, 180 about
     * 64): 3 + 0.33 V. External gear pumps and motors (Rexroth AZPF / AZMF, Casappa: 16 cm^3 about 3 kg): 1.2 + 0.12 V.
     */
    public static double unitKg(HydraulicUnit unit, boolean pump, double displacementCm3) {
        double v = Math.max(displacementCm3, 0);
        return switch (unit) {
            case AXIAL_PISTON -> pump ? 4 + 0.42 * v : 3 + 0.33 * v;
            case GEAR -> 1.2 + 0.12 * v;
        };
    }

    /**
     * Inertia of a displacement machine's rotating group, kg m^2: c V^(5/3), V in cm^3 (similar machines: the rotating
     * mass goes with the size cubed, the radius squared with it to the 2/3). Axial piston c 6.5e-6 (A10VO 45: 0.0033,
     * 140: 0.0242 in the data sheet; the rule gives 0.0037 and 0.0246); gear units a fifth of that (small gears on
     * short shafts).
     */
    public static double unitInertiaKgM2(HydraulicUnit unit, double displacementCm3) {
        double c = unit == HydraulicUnit.AXIAL_PISTON ? 6.5e-6 : 1.3e-6;
        return Math.max(c * Math.pow(Math.max(displacementCm3, 0), 5.0 / 3), 1e-6);
    }

    /**
     * A planetary reducer's mass per N m of its output torque, kg: 4.5 g/(N m) (track drives and slew drives of
     * mobile machinery: Rexroth GFT, Brevini SLW, Bonfiglioli 700T catalogues give some 4 to 6 g per N m).
     */
    public static final double REDUCER_KG_PER_NM = 0.0045;

    // ---------------------------------------------------------------- structure

    /** Mass of a welded box section (two webs, two flanges) of a height, width, plate and length, kg. */
    public static double boxKg(double heightM, double widthM, double plateM, double lengthM, double densityKgPerM3) {
        return 2 * (heightM + widthM) * plateM * lengthM * densityKgPerM3;
    }

    /** At most this many contact spheres along one link. */
    public static final int MAX_SPHERES = 48;

    /**
     * Contact spheres of a radius along a direction from the origin to lengthM, no further apart than the radius (at
     * most MAX_SPHERES, at least two).
     */
    public static List<CollisionSphere> along(Vec3d direction, double lengthM, double radiusM) {
        List<CollisionSphere> out = new ArrayList<>();
        if (!(radiusM > 0) || !(lengthM > 0)) { return out; }
        int n = (int) Math.max(2, Math.min(MAX_SPHERES, Math.ceil(lengthM / radiusM) + 1));
        for (int i = 0; i < n; i++) { out.add(new CollisionSphere(direction.scale(lengthM * i / (n - 1)), radiusM)); }
        return out;
    }

    /** Whether every value is finite. */
    static boolean finite(double... values) {
        for (double v : values) { if (!Double.isFinite(v)) { return false; } }
        return true;
    }
}
