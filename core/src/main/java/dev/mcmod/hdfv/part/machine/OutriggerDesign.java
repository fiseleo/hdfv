package dev.mcmod.hdfv.part.machine;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.LegPart;
import dev.mcmod.aerocore.api.wheel.Brake;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.aerocore.api.wheel.Tyre;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An outrigger jack (hdfv:outrigger, 0.26, 支腿): a hydraulic jack hanging from its mount (a carrier's outrigger point, or
 * an outrigger beam's "tip"), its pad's centre lengthM under the mount let down, drawn up by strokeM in extensionTimeS
 * on "retract" (above 0.5 up, else down), rated for ratedLoadKg on its pad (padDiameterM). Like the landing leg
 * (truck.LandingLegsDesign) it is AeroCore's LegPart with a Telescope: the pad a small stiff "tyre" (PAD_WIDTH of the pad
 * wide, flat, at the tyre model's highest pressure: under a full load it bottoms and holds) held from rolling by a
 * brake bound to 1, on a strut that sinks STATIC_DEFLECTION_M
 * under the rated load at DAMPING_RATIO of critical (a jack's oil column and the pad on firm ground).
 *
 * <p>Its mass: the jack cylinder sized for the rated load at JACK_PRESSURE_PA with JACK_MARGIN (its rod ROD_SHARE of the
 * bore; MachineRules.cylinderKg), the box it slides in (HOUSING_PLATE_M, as long as the jack), and the pad (a ribbed float:
 * PAD_PLATE_M thick, PAD_FILL of a solid disc). An all-terrain crane's jacks of 30 to 50 t weigh some 200 to 300 kg.
 * Ranges (EXTRAPOLATION) from mobile and all-terrain cranes' outriggers (Liebherr, Grove data sheets): 0.5 to 3 m let
 * down, 0.2 to 1.5 m of stroke, 2 to 150 t, pads 0.3 to 1.2 m, at most 2.5 MPa on the pad.
 */
public record OutriggerDesign(String name, double lengthM, double strokeM, double extensionTimeS, double padDiameterM, double ratedLoadKg) implements PartDesign {
    public static final String TYPE = "hdfv:outrigger";

    /** The strut: its sinkage under the rated load (m), its damping's share of critical, its travel (m). */
    public static final double STATIC_DEFLECTION_M = 0.01, DAMPING_RATIO = 0.3, TRAVEL_M = 0.03;
    /** The jack: holding pressure (Pa), margin over the rated load, rod over bore. */
    public static final double JACK_PRESSURE_PA = 300e5, JACK_MARGIN = 1.25, ROD_SHARE = 0.7;
    /** The housing's plate (m) and clearance round the jack (m); the pad's plate (m) and share of a solid disc. */
    public static final double HOUSING_PLATE_M = 0.01, HOUSING_CLEARANCE_M = 0.1, PAD_PLATE_M = 0.03, PAD_FILL = 0.6;
    /** The pad "tyre": width over pad diameter, aspect ratio (low: it bottoms within a few cm), pressure (Pa, the tyre model's most). */
    public static final double PAD_WIDTH = 0.8, PAD_ASPECT = 0.25, PAD_PRESSURE_PA = 2.9e6;
    /** The pads the tyre model takes, m (its section is under 2 m wide). */
    public static final double MIN_PAD_M = 0.15, MAX_PAD_M = 2.4;

    public OutriggerDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    public double jackBoreM() { return Math.sqrt(4 * JACK_MARGIN * ratedLoadKg * MachineRules.G / (Math.PI * JACK_PRESSURE_PA)); }

    public double jackKg() { double b = jackBoreM(); return MachineRules.cylinderKg(b, ROD_SHARE * b, strokeM, JACK_PRESSURE_PA); }

    public double housingKg() {
        double side = jackBoreM() + HOUSING_CLEARANCE_M;
        return MachineRules.boxKg(side, side, HOUSING_PLATE_M, lengthM, MachineRules.STEEL_KG_PER_M3);
    }

    public double padKg() { return MachineRules.STEEL_KG_PER_M3 * PAD_FILL * Math.PI / 4 * padDiameterM * padDiameterM * PAD_PLATE_M; }

    public double massKg() { return jackKg() + housingKg() + padKg(); }

    /** The rated load's pressure on the pad, Pa. */
    public double padPressurePa() { return ratedLoadKg * MachineRules.G / (Math.PI / 4 * padDiameterM * padDiameterM); }

    public MassProperties mass() {
        double side = jackBoreM() + HOUSING_CLEARANCE_M;
        return Components.box(massKg(), new Vec3d(0, -0.5 * lengthM, 0), new Vec3d(side, lengthM, side));
    }

    /** The pad as a stiff tyre of its diameter. */
    public Tyre pad() {
        double w = PAD_WIDTH * padDiameterM, rim = padDiameterM - 2 * w * PAD_ASPECT;
        return new Tyre(new Tyre.Spec(Tyre.Construction.AIRCRAFT, w, PAD_ASPECT, rim, PAD_PRESSURE_PA, PAD_PRESSURE_PA, Tyre.Tread.AIRCRAFT_RIB, 1.2));
    }

    /** The strut's stiffness, N/m: the rated load at STATIC_DEFLECTION_M. */
    public double strutNPerM() { return ratedLoadKg * MachineRules.G / STATIC_DEFLECTION_M; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "lengthM", lengthM);
        EngineComponent.Rules.positive(issues, "strokeM", strokeM);
        EngineComponent.Rules.positive(issues, "extensionTimeS", extensionTimeS);
        EngineComponent.Rules.positive(issues, "ratedLoadKg", ratedLoadKg);
        if (!(padDiameterM >= MIN_PAD_M && padDiameterM <= MAX_PAD_M)) { issues.add(DesignIssue.invalid("padDiameterM", "A pad is 0.15 to 2.4 m across")); }
        if (!issues.isEmpty()) { return issues; }
        if (strokeM > lengthM) { issues.add(DesignIssue.invalid("strokeM", "A jack draws up at most its own length")); return issues; }
        String cranes = "mobile and all-terrain cranes' outriggers (Liebherr, Grove data sheets)";
        DesignIssue.range(issues, "lengthM", lengthM, 0.5, 3, cranes);
        DesignIssue.range(issues, "strokeM", strokeM, 0.2, 1.5, cranes);
        DesignIssue.range(issues, "extensionTimeS", extensionTimeS, 3, 60, cranes);
        DesignIssue.range(issues, "padDiameterM", padDiameterM, 0.3, 1.2, cranes);
        DesignIssue.range(issues, "ratedLoadKg", ratedLoadKg, 2000, 150000, cranes);
        DesignIssue.range(issues, "padPressurePa", padPressurePa(), 0, 2.5e6, cranes + ": pressure on the pad");
        return issues;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        double k = strutNPerM(), c = 2 * DAMPING_RATIO * Math.sqrt(k * ratedLoadKg);
        Suspension strut = new Suspension(new Suspension.Geometry(0, 0, false), Suspension.Spring.coil(k, 0), Suspension.Damper.linear(c, c), TRAVEL_M, 0, 0);
        Tyre pad = pad();
        double hold = Math.max(20000, 0.8 * ratedLoadKg * MachineRules.G * pad.radiusM());
        Brake shoe = new Brake(Brake.Type.DRUM_LEADING_TRAILING, hold, 5, 0.15, 0.02);
        double r = pad.radiusM();
        WheelCorner corner = new WheelCorner(new WheelCorner.Spec(new Vec3d(0, -lengthM, 0), 0, 0, pad, Math.max(0.05, 0.5 * padKg() * r * r), strut, shoe, 0));
        return new LegPart(this, corner, LegPart.Steer.FIXED, 0, 0.1, null, mass(), Components.extrapolations(issues), null,
                new LegPart.Telescope(strokeM, extensionTimeS));
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lengthM", lengthM, "strokeM", strokeM, "extensionTimeS", extensionTimeS, "padDiameterM", padDiameterM, "ratedLoadKg", ratedLoadKg);
    }

    public static PartDesign read(Fields f) {
        OutriggerDesign d = standard();
        return new OutriggerDesign(f.string("name", d.name), f.number("lengthM", d.lengthM), f.number("strokeM", d.strokeM), f.number("extensionTimeS", d.extensionTimeS),
                f.number("padDiameterM", d.padDiameterM), f.number("ratedLoadKg", d.ratedLoadKg));
    }

    /** The standard jack: an all-terrain crane's 40 t jack (SampleMachinery.outrigger). */
    public static OutriggerDesign standard() { return SampleMachinery.outrigger(); }
}
