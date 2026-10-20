package dev.mcmod.hdfv.part.truck;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A semi-trailer's landing leg (hdfv:landing_legs, 0.18, 支撐腳): one telescoping leg (an assembly room puts them in
 * pairs on a chassis's "legs_1", "legs_2"...), its foot lengthM under its mount let down, drawn up by strokeM in
 * crankTimeS on "retract" (the landing gear's channel: 1 up, 0 down). In the world it is AeroCore's LegPart with a
 * Telescope: the foot is a small stiff wheel held by a brake bound to 1 (a sand shoe that does not roll), on a stiff
 * strut. Its rating: the static load a leg takes (kg); its mass: 25 kg and 1.2 kg per tonne it takes.
 */
public record LandingLegsDesign(String name, double lengthM, double strokeM, double crankTimeS, double footDiameterM, double ratedLoadKg) implements PartDesign {
    public static final String TYPE = "hdfv:landing_legs";
    public static final double G = 9.80665;
    /** The foot's width and the share of its diameter that is tyre, m and -; the strut's stiffness, N/m. */
    static final double FOOT_WIDTH_M = 0.2, FOOT_ASPECT = 0.6, STRUT_NPM = 3e6;

    public LandingLegsDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(lengthM >= 0.4 && lengthM <= 2.0)) { issues.add(DesignIssue.invalid("lengthM", "A landing leg reaches 0.4 to 2 m let down")); }
        if (!(strokeM >= 0.1 && strokeM <= 0.8 * lengthM)) { issues.add(DesignIssue.invalid("strokeM", "A leg draws up 0.1 m to 80 % of its length")); }
        if (!(crankTimeS >= 1 && crankTimeS <= 60)) { issues.add(DesignIssue.invalid("crankTimeS", "Cranking takes 1 to 60 s")); }
        if (!(footDiameterM >= 0.3 && footDiameterM <= 0.6)) { issues.add(DesignIssue.invalid("footDiameterM", "A foot is 0.3 to 0.6 m across")); }
        if (!(ratedLoadKg >= 1000 && ratedLoadKg <= 30000)) { issues.add(DesignIssue.invalid("ratedLoadKg", "A leg takes 1 to 30 t")); }
        return issues;
    }

    public double massKg() { return 25 + 1.2 * ratedLoadKg / 1000; }

    public MassProperties mass() {
        return Components.box(massKg(), new Vec3d(0, -0.5 * lengthM, 0), new Vec3d(0.15, lengthM, 0.15));
    }

    /** The foot: a small high-pressure tyre of footDiameterM, stiff. */
    public Tyre foot() {
        double rim = Math.max(0.1, footDiameterM - 2 * FOOT_WIDTH_M * FOOT_ASPECT);
        return new Tyre(new Tyre.Spec(Tyre.Construction.AIRCRAFT, FOOT_WIDTH_M, FOOT_ASPECT, rim, 1.6e6, 1.6e6, Tyre.Tread.AIRCRAFT_RIB, 1.2));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        Suspension strut = new Suspension(new Suspension.Geometry(0, 0, false), Suspension.Spring.coil(STRUT_NPM, 0),
                Suspension.Damper.linear(6e4, 6e4), 0.03, 0, 0);
        Brake shoe = new Brake(Brake.Type.DRUM_LEADING_TRAILING, 20000, 5, 0.15, 0.02);
        WheelCorner corner = new WheelCorner(new WheelCorner.Spec(new Vec3d(0, -lengthM, 0), 0, 0, foot(), 0.05, strut, shoe, 0));
        return new LegPart(this, corner, LegPart.Steer.FIXED, 0, 0.1, null, mass(), Components.extrapolations(issues), null,
                new LegPart.Telescope(strokeM, crankTimeS));
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lengthM", lengthM, "strokeM", strokeM, "crankTimeS", crankTimeS, "footDiameterM", footDiameterM, "ratedLoadKg", ratedLoadKg);
    }

    public static PartDesign read(Fields f) {
        LandingLegsDesign d = standard();
        return new LandingLegsDesign(f.string("name", d.name), f.number("lengthM", d.lengthM), f.number("strokeM", d.strokeM), f.number("crankTimeS", d.crankTimeS),
                f.number("footDiameterM", d.footDiameterM), f.number("ratedLoadKg", d.ratedLoadKg));
    }

    /** A standard leg: 1.05 m let down, drawn up 0.45 m in 6 s, a 0.4 m foot, 12 t (24 t a pair). */
    public static LandingLegsDesign standard() { return new LandingLegsDesign("landing leg", 1.05, 0.45, 6, 0.4, 12000); }
}
