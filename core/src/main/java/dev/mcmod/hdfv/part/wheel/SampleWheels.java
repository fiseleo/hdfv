package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.vehicle.part.LegPart;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.aerocore.api.wheel.Tyre;

/**
 * Example axles and landing gear on the scale of real ones (figures in each), starting points for the design screen
 * and the sample vehicles.
 */
public final class SampleWheels {
    private SampleWheels() {}

    /** A 205/55R16 road tyre at 230 kPa (rated 300 kPa), the size of a family car's. */
    public static TyreDesign carTyre() {
        return new TyreDesign("205/55R16", Tyre.Construction.RADIAL, 0.205, 0.55, 16, 230e3, 300e3, Tyre.Tread.ROAD, 1);
    }

    /** An LT265/75R16 all-terrain tyre at 260 kPa (load range E: 550 kPa). */
    public static TyreDesign offRoadTyre() {
        return new TyreDesign("LT265/75R16", Tyre.Construction.RADIAL, 0.265, 0.75, 16, 260e3, 550e3, Tyre.Tread.ALL_TERRAIN, 1);
    }

    /**
     * A family car's front axle (about a 1300 kg car with 60 % on the front): MacPherson struts at 25 N/mm and 355 kg a
     * corner (about 1.25 Hz), 288 x 25 mm vented discs under single 57 mm pistons at 7 MPa, ABS, steered 38 degrees with
     * 80 % Ackermann for a 2.65 m wheelbase, a 20 N/mm anti-roll bar, 1.53 m track; not driven.
     */
    public static AxleDesign sedanFront() {
        return new AxleDesign("family car front axle", new AxleLayoutDesign("layout", 1.53, 0, true, 38, 0.8, 2.65, 20000), carTyre(),
                new RimDesign("16 x 6.5 cast", 6.5, RimDesign.Material.CAST_ALLOY, 5, Double.NaN),
                new BrakeDesign("vented disc", BrakeDesign.Kind.VENTED_DISC, 0.288, 0.025, 0.057, 1, 7e6, 0, true, Double.NaN),
                new SuspensionDesign("MacPherson", Suspension.Type.MACPHERSON, 0.18, 0.11, 25000, 355, 1500, 3500, 0, -1, 20, -0.5, 0.05, Double.NaN),
                DifferentialDesign.none());
    }

    /**
     * The same car's rear axle, driven (rear drive along +x): multi-link at 28 N/mm and 290 kg a corner, 280 x 12 mm solid
     * discs under 38 mm pistons at 6 MPa (proportioned below the front's), ABS and a parking brake of a third, a 3.73
     * clutch-pack limited slip (40 N m preload, 0.25 lock factor), 1.55 m track, an 8 N/mm anti-roll bar.
     */
    public static AxleDesign sedanRear() {
        return new AxleDesign("family car rear axle", new AxleLayoutDesign("layout", 1.55, 0, false, 0, 0, 0, 8000), carTyre(),
                new RimDesign("16 x 6.5 cast", 6.5, RimDesign.Material.CAST_ALLOY, 5, Double.NaN),
                new BrakeDesign("solid disc", BrakeDesign.Kind.DISC, 0.28, 0.012, 0.038, 1, 6e6, 0.35, true, Double.NaN),
                new SuspensionDesign("multi-link", Suspension.Type.MULTI_LINK, 0.18, 0.10, 28000, 290, 1400, 3200, 0, -1, 20, -1.2, 0.15, Double.NaN),
                new DifferentialDesign("limited slip", true, 3.73, 0.96, false, AxlePart.Differential.CLUTCH, 40, 0.25, 3, 30, false, Double.NaN));
    }

    /**
     * An off-roader's front axle (about 2200 kg, 55 % on the front): a solid axle on coil springs (45 N/mm, 535 kg a
     * corner) and a Panhard rod, 250 mm of travel, 300 x 28 mm vented discs under two 48 mm pistons, steered 35 degrees,
     * driven through an open 4.10 differential from a propeller shaft; 1.55 m track, the hubs 50 mm low (lifted).
     */
    public static AxleDesign offRoadFront() {
        return new AxleDesign("off-road front axle", new AxleLayoutDesign("layout", 1.55, 0.05, true, 35, 0.6, 2.8, 0), offRoadTyre(),
                new RimDesign("16 x 7.5 steel", 7.5, RimDesign.Material.STEEL, 6, Double.NaN),
                new BrakeDesign("vented disc", BrakeDesign.Kind.VENTED_DISC, 0.30, 0.028, 0.048, 2, 8e6, 0, false, Double.NaN),
                new SuspensionDesign("coil and Panhard rod", Suspension.Type.SOLID_PANHARD, 0.25, 0.11, 45000, 535, 2500, 5500, 0, -1, 20, 0, 0.1, Double.NaN),
                new DifferentialDesign("open", true, 4.10, 0.95, false, AxlePart.Differential.OPEN, 50, 0.3, 3, 30, false, Double.NaN));
    }

    /**
     * Its rear axle: leaf springs (40 N/mm, 480 kg a corner), 280 x 60 mm leading-trailing drums with 22 mm wheel cylinders
     * and a parking brake of 0.4, an open 4.10 differential with a locker ("lock").
     */
    public static AxleDesign offRoadRear() {
        return new AxleDesign("off-road rear axle", new AxleLayoutDesign("layout", 1.55, 0.05, false, 0, 0, 0, 0), offRoadTyre(),
                new RimDesign("16 x 7.5 steel", 7.5, RimDesign.Material.STEEL, 6, Double.NaN),
                new BrakeDesign("drum", BrakeDesign.Kind.DRUM_LEADING_TRAILING, 0.28, 0.06, 0.022, 1, 8e6, 0.4, false, Double.NaN),
                new SuspensionDesign("leaf springs", Suspension.Type.SOLID_LEAF, 0.24, 0.11, 40000, 480, 2200, 5000, 0, -1, 20, 0, 0, Double.NaN),
                new DifferentialDesign("locker", true, 4.10, 0.95, false, AxlePart.Differential.OPEN, 50, 0.3, 3, 30, true, Double.NaN));
    }

    /**
     * A light aircraft's nose leg (a Cessna 172's scale, 175 kg on it): a 5.00-5 tyre at 214 kPa (31 psi), an oleo of a
     * 55 mm piston, 170 mm stroke, 380 kPa extended (40 psi gauge), compression ratio 4 and a 9 mm orifice, steered 30
     * degrees from the rudder pedals, no brake; 0.55 m long.
     */
    public static LandingGearDesign lightNoseGear() {
        return new LandingGearDesign("light aircraft nose gear", new LegLayoutDesign("leg", 0.55, LegLayoutDesign.Side.CENTRE, LegPart.Steer.STEERABLE, 30, 0.05),
                new TyreDesign("5.00-5", Tyre.Construction.AIRCRAFT, 0.127, 0.92, 5, 214e3, 345e3, Tyre.Tread.AIRCRAFT_RIB, 1),
                new RimDesign("5 in split", 3.5, RimDesign.Material.CAST_ALLOY, 6, Double.NaN), BrakeDesign.none(),
                new StrutDesign("oleo", StrutDesign.Kind.OLEO, 0.17, 175, 0.055, 380e3, 4, 0.009, 4, 60000, 300, Double.NaN));
    }

    /**
     * Its left main leg (490 kg on it): a 6.00-6 tyre at 290 kPa (42 psi), a spring-steel leg of 53 N/mm with 250 mm of
     * deflection, a 170 x 6.35 mm disc beside the wheel under two 35 mm pistons at 4 MPa (toe brakes), 0.9 m long. Its
     * right twin stands on the right side.
     */
    public static LandingGearDesign lightMainGear() {
        return new LandingGearDesign("light aircraft main gear", new LegLayoutDesign("leg", 0.9, LegLayoutDesign.Side.LEFT, LegPart.Steer.FIXED, 30, 0.05),
                new TyreDesign("6.00-6", Tyre.Construction.AIRCRAFT, 0.152, 0.96, 6, 290e3, 345e3, Tyre.Tread.AIRCRAFT_RIB, 1),
                new RimDesign("6 in split", 4.0, RimDesign.Material.CAST_ALLOY, 6, Double.NaN),
                new BrakeDesign("single disc", BrakeDesign.Kind.DISC, 0.17, 0.00635, 0.035, 2, 4e6, 0, false, 2.0),
                new StrutDesign("spring steel", StrutDesign.Kind.SPRING_STEEL, 0.25, 490, 0.05, 411e3, 4, 0.012, 4, 53000, 300, Double.NaN));
    }
}
