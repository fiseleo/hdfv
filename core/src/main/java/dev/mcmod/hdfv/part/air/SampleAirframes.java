package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.aero.SectionPolar;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

/**
 * Sample airframe designs to start from. Sizes after public figures; the structures are this mod's own sizing to
 * pass their checks (not the real aircraft's).
 * <ul>
 * <li>lightWing: a Cessna 172-class wing (11.0 m span, 1.63 / 1.13 m chords, NACA 2412, slotted flaps).</li>
 * <li>seaplaneWing, tailplane, fin: a single-turboprop floatplane of about 2.7 t (a DHC-3T Turbo Otter-class wing:
 *   17.7 m span, about 35 m^2, set at 5 degrees so it unloads the floats early).</li>
 * <li>turbopropPropeller: three blades of 2.59 m on a constant-speed hub governed at 1,800 to 2,200 rpm (a PT6A
 *   installation's); fixedPropeller: two blades of 1.9 m, fixed pitch (a light aircraft's).</li>
 * <li>mainRotor, tailRotor: UH-60A-class (8.18 m radius, four blades of 0.527 m, -16 deg twist, 258 rpm; tail rotor 1.68
 *   m, 1,190 rpm).</li>
 * </ul>
 */
public final class SampleAirframes {
    private SampleAirframes() {}

    private static WingStructureDesign structure(double capM, double webM, double supportedKg) {
        return new WingStructureDesign("structure", EngineMaterial.ALUMINIUM_2024, EngineMaterial.ALUMINIUM_2024, 0.15, 0.65, capM, webM, 0.4,
                0.0006, 0.4, 0.0006, supportedKg, 3.8, Double.NaN);
    }

    public static WingDesign lightWing() {
        return new WingDesign("light wing", SectionDesign.naca("root", 0.02, 0.4, 0.12), SectionDesign.naca("tip", 0.02, 0.4, 0.12),
                new PlanformDesign("planform", 11.0, 1.63, 1.13, 0, 1.7, 1.5, -3, true), structure(0.003, 0.0015, 1110),
                new WingSurfaceDesign("flap", WingSurfaceDesign.Role.FLAP, SectionPolar.FlapType.SLOTTED, 0.3, 0.05, 0.55, 30, 0),
                new WingSurfaceDesign("aileron", WingSurfaceDesign.Role.AILERON, SectionPolar.FlapType.PLAIN, 0.25, 0.6, 0.95, 15, 20));
    }

    public static WingDesign seaplaneWing() {
        return new WingDesign("seaplane wing", SectionDesign.naca("root", 0.02, 0.4, 0.15), SectionDesign.naca("tip", 0.02, 0.4, 0.12),
                new PlanformDesign("planform", 17.7, 2.2, 1.75, 0, 3, 3, -3, true), structure(0.005, 0.002, 3600),
                new WingSurfaceDesign("flap", WingSurfaceDesign.Role.FLAP, SectionPolar.FlapType.SLOTTED, 0.3, 0.08, 0.6, 30, 0),
                new WingSurfaceDesign("aileron", WingSurfaceDesign.Role.AILERON, SectionPolar.FlapType.PLAIN, 0.25, 0.65, 0.95, 20, 20));
    }

    public static WingDesign tailplane() {
        return new WingDesign("tailplane", SectionDesign.naca("root", 0, 0, 0.12), SectionDesign.naca("tip", 0, 0, 0.12),
                new PlanformDesign("planform", 6.2, 1.3, 0.8, 5, 0, -2, 0, true), structure(0.0015, 0.001, 500),
                new WingSurfaceDesign("elevator", WingSurfaceDesign.Role.ELEVATOR, SectionPolar.FlapType.PLAIN, 0.4, 0.05, 1.0, 20, 25),
                WingSurfaceDesign.none("aileron"));
    }

    public static WingDesign fin() {
        return new WingDesign("fin", SectionDesign.naca("root", 0, 0, 0.12), SectionDesign.naca("tip", 0, 0, 0.12),
                new PlanformDesign("planform", 2.2, 1.9, 1.0, 30, 0, 0, 0, false), structure(0.0015, 0.001, 250),
                new WingSurfaceDesign("rudder", WingSurfaceDesign.Role.RUDDER, SectionPolar.FlapType.PLAIN, 0.35, 0.05, 0.95, 25, 25),
                WingSurfaceDesign.none("aileron"));
    }

    public static AirPropellerDesign turbopropPropeller() {
        return new AirPropellerDesign("turboprop propeller",
                new PropellerBladeDesign("blades", 3, 2.59, 0.15, 0.08, 0.045, 1.1, 0, 0.18, 0.06, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                SectionDesign.naca("section", 0.04, 0.4, 0.12),
                new PropellerHubDesign("hub", -10, 20, PropellerHubDesign.Governor.CONSTANT_SPEED, 1800, 2200, Double.NaN));
    }

    public static AirPropellerDesign fixedPropeller() {
        return new AirPropellerDesign("fixed propeller",
                new PropellerBladeDesign("blades", 2, 1.9, 0.12, 0.09, 0.05, 0.75, 0, 0.2, 0.07, EngineMaterial.ALUMINIUM_7075, Double.NaN),
                SectionDesign.naca("section", 0.04, 0.4, 0.12),
                new PropellerHubDesign("hub", 0, 0, PropellerHubDesign.Governor.NONE, 2700, 2700, Double.NaN));
    }

    public static RotorDesign mainRotor() {
        return new RotorDesign("main rotor",
                new RotorBladeDesign("blades", 4, 8.18, 0.2, 0.527, 0.527, -16, 0.095, 0.095, EngineMaterial.ALUMINIUM_2024, 0.35, Double.NaN),
                SectionDesign.naca("section", 0.009, 0.3, 0.095),
                new RotorHubDesign("hub", RotorHubDesign.HubType.ARTICULATED, 0.0465, 0, false, Double.NaN),
                new RotorControlDesign("controls", 0, 18, 10, 258, 0.4, 0.3, 0));
    }

    public static RotorDesign tailRotor() {
        return new RotorDesign("tail rotor",
                new RotorBladeDesign("blades", 4, 1.68, 0.2, 0.25, 0.25, -18, 0.12, 0.12, EngineMaterial.GFRP, 0.4, Double.NaN),
                SectionDesign.naca("section", 0, 0, 0.12),
                new RotorHubDesign("hub", RotorHubDesign.HubType.HINGELESS, 0.1, 0, false, Double.NaN),
                new RotorControlDesign("controls", -8, 20, 0, 1190, 0, 0, -0.3));
    }
}
