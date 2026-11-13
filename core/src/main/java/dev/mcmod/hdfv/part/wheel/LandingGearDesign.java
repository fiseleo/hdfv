package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.LegPart;
import dev.mcmod.aerocore.api.wheel.Tyre;
import dev.mcmod.aerocore.api.wheel.WheelCorner;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A landing-gear leg (hdfv:landing_gear, 起落架): one wheel on its strut, under the mount point (the origin) by the
 * leg's length at full extension. In the world it is AeroCore's LegPart. Inputs: "brake" (bind each main leg's to its
 * own channel for differential braking), "steer" when steerable. Rotor: "wheel".
 * <p>
 * Checks: the components', the tyre on the rim (as an axle's), the brake's room (a disc beside the wheel may reach 30 mm
 * past the rim's diameter, a drum inside it 20 mm short of it), the tyre's load against its rating, the strut fitting in
 * the leg.
 */
public record LandingGearDesign(String name, LegLayoutDesign leg, TyreDesign tyre, RimDesign wheel, BrakeDesign brake, StrutDesign strut) implements PartDesign {
    public static final String TYPE = "hdfv:landing_gear";
    public static final double G = 9.80665;

    public LandingGearDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{leg, tyre, wheel, brake, strut}) { Objects.requireNonNull(o, "component"); }
    }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("leg", leg); m.put("strut", strut); m.put("tyre", tyre); m.put("wheel", wheel); m.put("brake", brake);
        return m;
    }

    public double rimInertiaKgM2() { return wheel.inertiaKgM2(tyre.rimDiameterM()) + brake.rotorInertiaKgM2(); }

    public double totalMassKg() { return tyre.massKg() + wheel.massKg(tyre.rimDiameterM()) + brake.massKg() + strut.massKg(); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = Components.issues(components());
        if (Components.invalid(issues)) { return issues; }
        Tyre t = tyre.tyre();
        double seat = wheel.widthM() / tyre.sectionWidthM();
        if (seat < 0.45 || seat > 1.2) { issues.add(DesignIssue.invalid("wheel.rimWidthIn", "The tyre cannot seat on a rim this wide")); }
        if (brake.kind() != BrakeDesign.Kind.NONE) {
            // an aircraft's disc sits beside the wheel, under the tyre's bead; a drum inside the wheel
            double room = tyre.rimDiameterM() + (brake.kind().drum() ? -0.02 : 0.03);
            if (brake.rotorDiameterM() > room) { issues.add(DesignIssue.invalid("brake.rotorDiameterM", "The brake does not fit inside the wheel")); }
        }
        if (strut.kind() == StrutDesign.Kind.OLEO && leg.lengthM() < strut.strokeM() + 0.1) {
            issues.add(DesignIssue.invalid("leg.lengthM", "The leg is too short for the strut's stroke"));
        }
        double load = strut.designLoadKg() * G;
        if (load > t.maxLoadN()) { issues.add(DesignIssue.invalid("strut.designLoadKg", "The tyre's rated load is " + Math.round(t.maxLoadN()) + " N; this leg carries " + Math.round(load) + " N")); }
        else { DesignIssue.range(issues, "tyreLoad", load / t.nominalLoadN(), 0.2, 1.0, "aircraft tyres stand at or below their rated load"); }
        return issues;
    }

    public MassProperties mass() {
        double r = tyre.tyre().radiusM();
        return Components.box(totalMassKg(), new Vec3d(0, -0.6 * leg.lengthM(), 0), new Vec3d(2 * r, leg.lengthM(), tyre.sectionWidthM()));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        Tyre t = tyre.tyre();
        WheelCorner corner = new WheelCorner(new WheelCorner.Spec(new Vec3d(0, -leg.lengthM(), 0), leg.side().sign, 0, t, rimInertiaKgM2(), strut.suspension(),
                brake.brake(), 0));
        // 0.25: the wheel wears (tyre, brake, strut)
        return new LegPart(this, corner, leg.steer(), Math.toRadians(leg.maxSteerDeg()), leg.trailM(), brake.control(), mass(), Components.extrapolations(issues),
                retraction()).wearing(dev.mcmod.hdfv.damage.Wearings.wheels(tyre, brake, 1, strut.designLoadKg()));
    }

    /**
     * 0.12: a retractable leg's folding (null for a fixed one, as before): its time, its axis and 90 degrees, and its
     * drag while down as the sum of its pieces' drag areas over their frontal area (D/q per frontal area from Raymer,
     * Aircraft Design, table 12.6, Hoerner's data): the tyre and wheel 0.25 on width times diameter, the strut 1.0
     * (a shock strut) or 1.2 (a round bar) on its width times length.
     */
    public dev.mcmod.aerocore.api.vehicle.part.LegPart.Retraction retraction() {
        if (!leg.retractable()) { return null; }
        double tyreArea = tyre.sectionWidthM() * 2 * tyre.tyre().radiusM();
        double strutWidth = switch (strut.kind()) {
            case OLEO -> Math.max(strut.pistonDiameterM(), 0.024) + 0.024;
            case SPRING_STEEL -> 0.026;
            case RIGID -> 0.05;
        };
        double strutArea = strutWidth * leg.lengthM(), strutCd = strut.kind() == StrutDesign.Kind.OLEO ? 1.0 : 1.2;
        double area = tyreArea + strutArea, cd = (0.25 * tyreArea + strutCd * strutArea) / area;
        return new dev.mcmod.aerocore.api.vehicle.part.LegPart.Retraction(leg.retractTimeS(), leg.retractAxis(), Math.PI / 2, area, cd);
    }

    @Override public Map<String, Object> parameters() { return Components.parameters(components()); }

    public static PartDesign read(Fields f) {
        return new LandingGearDesign(f.string("name", "landing gear"), Components.read(f, "leg", LegLayoutDesign::read), Components.read(f, "tyre", TyreDesign::read),
                Components.read(f, "wheel", RimDesign::read), Components.read(f, "brake", BrakeDesign::read), Components.read(f, "strut", StrutDesign::read));
    }
}
