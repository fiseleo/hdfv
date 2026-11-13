package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.aero.AirPropeller;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;
import dev.mcmod.aerocore.api.vehicle.part.AirPropellerPart;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An air propeller (hdfv:air_propeller) assembled from designed components: blades, their section, the hub. Its hub
 * is the part origin, pushing along +X when turning positively about it (a shaft from an engine's output along +X).
 * In flight it is AeroCore's AirPropellerPart (blade-element momentum theory on the section's polar), with the hub's
 * governor as a rule here (PropellerHubDesign). Inputs: "rpm" with a constant-speed governor (unbound: the highest
 * governed speed), otherwise "pitch". Internal state: the governed blade setting (rad).
 * <p>
 * Checks: the components', the tip's Mach number turning at the hub's rated speed, the blade root's centrifugal stress
 * there, solidity and activity factor.
 */
public record AirPropellerDesign(String name, PropellerBladeDesign blades, SectionDesign section, PropellerHubDesign hub) implements PartDesign {
    public static final String TYPE = "hdfv:air_propeller";

    public AirPropellerDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{blades, section, hub}) { Objects.requireNonNull(o, "component"); }
    }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("blades", blades); m.put("section", section); m.put("hub", hub);
        return m;
    }

    public AirPropeller.Geometry geometry() {
        return new AirPropeller.Geometry(blades.blades(), blades.diameterM(), blades.hubRatio(), blades.rootChordRatio(), blades.tipChordRatio(),
                blades.pitchRatio(), blades.twistDeg(), section.airfoil(blades.rootThickness()), section.airfoil(blades.tipThickness()));
    }

    public double totalMassKg() { double b = blades.massKg(); return b + hub.massKg(b); }
    /** Static tip Mach number at the rated speed (sea-level sound speed). */
    public double tipMach() { return Math.PI * hub.maxRpm() / 60 * blades.diameterM() / 340.3; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = Components.issues(components());
        if (Components.invalid(issues)) { return issues; }
        AirPropeller.Geometry g;
        try { g = geometry(); } catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("blades", e.getMessage())); return issues; }
        double mach = tipMach();
        if (mach > 1.1) { issues.add(DesignIssue.invalid("hub.maxRpm", "The tips would pass Mach " + Math.round(mach * 100) / 100.0 + " standing still")); }
        else { DesignIssue.range(issues, "tipMach", mach, 0, 0.9, "propellers keep their tips below about Mach 0.9 (compressibility losses and noise)"); }
        double stress = blades.rootStress(hub.maxRpm()), yield = blades.material().yieldPa();
        if (stress > yield) { issues.add(DesignIssue.invalid("blades.rootThickness", "The blade roots would yield turning at the rated speed")); }
        else { DesignIssue.range(issues, "rootStressOverFatigue", stress / blades.material().fatiguePa(), 0, 1, "blades kept below their material's fatigue strength"); }
        DesignIssue.range(issues, "solidity", g.solidity(), 0.03, 0.25, "propellers of NACA Report 640 and later");
        DesignIssue.range(issues, "activityFactor", g.activityFactor(), 60, 220, "propeller activity factors (Hamilton Standard)");
        return issues;
    }

    public MassProperties mass() {
        double m = totalMassKg(), spin = blades.spinInertia() + 0.5 * hub.massKg(blades.massKg()) * Math.pow(blades.hubRatio() * blades.radiusM(), 2);
        return new MassProperties(m, Vec3d.ZERO, spin, spin / 2, spin / 2, 0, 0, 0);
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        // 0.25: its blades can be damaged
        return new Compiled(this, new AirPropellerPart(this, geometry(), hub.fineDeg(), hub.coarseDeg(), mass(), Components.extrapolations(issues))
                .wearing(AirPropellerPart.Wearing.typical()));
    }

    @Override public Map<String, Object> parameters() { return Components.parameters(components()); }

    public static PartDesign read(Fields f) {
        return new AirPropellerDesign(f.string("name", "propeller"), Components.read(f, "blades", PropellerBladeDesign::read),
                Components.read(f, "section", SectionDesign::read), Components.read(f, "hub", PropellerHubDesign::read));
    }

    /** AeroCore's propeller with the hub's governor. */
    private static final class Compiled implements Part {
        private final AirPropellerDesign design;
        private final AirPropellerPart inner;
        private final boolean governed;

        Compiled(AirPropellerDesign design, AirPropellerPart inner) {
            this.design = design; this.inner = inner;
            this.governed = design.hub().governor() == PropellerHubDesign.Governor.CONSTANT_SPEED;
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return inner.mass(); }
        @Override public List<CollisionSphere> collision() { return inner.collision(); }
        @Override public List<DesignIssue> issues() { return inner.issues(); }
        @Override public List<Rotor> rotors() { return inner.rotors(); }
        @Override public List<String> inputs() { return governed ? List.of("rpm") : inner.inputs(); }
        @Override public boolean early() { return true; }
        @Override public void warmUp() { inner.warmUp(); }
        @Override public int internalStates() { return governed ? 1 : 0; }
        /** 0.12 (AeroCore 0.30): the propeller's shown blade setting, for drawing. */
        @Override public int shownValues() { return inner.shownValues(); }
        /** 0.25: the propeller's condition (blades). */
        @Override public List<String> conditions() { return inner.conditions(); }
        @Override public double[] initialInternals() { return governed ? new double[]{Math.toRadians(design.hub().fineDeg())} : new double[0]; }

        @Override public void loads(PartStep step) {
            if (!governed) { inner.loads(step); return; }
            PropellerHubDesign h = design.hub();
            double target = h.minRpm() + Math.clamp(step.input("rpm", 1), 0, 1) * (h.maxRpm() - h.minRpm());
            double rpm = step.rotorSpeed(0) * 30 / Math.PI;
            double error = (rpm - target) / target;
            double rate = Math.toRadians(PropellerHubDesign.GOVERNOR_RATE) * Math.clamp(error / PropellerHubDesign.GOVERNOR_FULL_ERROR, -1, 1);
            double setting = Math.clamp(step.internal(0) + rate * step.dt(), Math.toRadians(h.fineDeg()), Math.toRadians(h.coarseDeg()));
            step.setInternal(0, setting);
            inner.loads(step, setting);
            step.diagnostic("targetRpm", target);
        }
    }
}
