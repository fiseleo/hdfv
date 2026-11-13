package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.aero.HelicopterRotor;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;
import dev.mcmod.aerocore.api.vehicle.part.RotorPart;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A helicopter rotor or tail rotor (hdfv:rotor) assembled from designed components: blades, their section, the hub and
 * the controls. Its hub is the part origin and its shaft the part's +Y (mount a tail rotor on its side). In flight it
 * is AeroCore's RotorPart (blade elements on the section's polar, flapping, inflow), the controls' rate dampers added
 * here. Inputs: "collective" 0..1 between its stops, "cyclicPitch" (+1 tilts the disk forward) and "cyclicRoll" (+1 to
 * starboard). Join it to an engine with a freewheel shaft so it can autorotate.
 * <p>
 * Checks: the components', the tip's Mach number at the design speed, the blades' centrifugal root stress there, the
 * Lock number and solidity.
 */
public record RotorDesign(String name, RotorBladeDesign blades, SectionDesign section, RotorHubDesign hub, RotorControlDesign controls) implements PartDesign {
    public static final String TYPE = "hdfv:rotor";

    public RotorDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{blades, section, hub, controls}) { Objects.requireNonNull(o, "component"); }
    }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("blades", blades); m.put("section", section); m.put("hub", hub); m.put("controls", controls);
        return m;
    }

    public HelicopterRotor.Geometry geometry() {
        return new HelicopterRotor.Geometry(blades.blades(), blades.radiusM(), blades.rootCutout(), blades.rootChordM(), blades.tipChordM(), blades.twistDeg(),
                section.airfoil(blades.rootThickness()), section.airfoil(blades.tipThickness()), hub.offset(), blades.bladeMassKg(), hub.spring(), hub.clockwise());
    }

    public double totalMassKg() { double b = blades.massKg(); return b + hub.massKg(b); }
    public double tipMach() { return controls.designRpm() * Math.PI / 30 * blades.radiusM() / 340.3; }
    /** Disk area, m^2. */
    public double diskAreaM2() { return Math.PI * blades.radiusM() * blades.radiusM(); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = Components.issues(components());
        if (Components.invalid(issues)) { return issues; }
        if (!(hub.offset() < blades.rootCutout())) { issues.add(DesignIssue.invalid("hub.hingeOffset", "The hinge must be inside the blades' root cutout")); return issues; }
        if (hub.hubType() == RotorHubDesign.HubType.TEETERING && blades.blades() != 2) { issues.add(DesignIssue.invalid("hub.hubType", "A teetering hub carries two blades")); }
        HelicopterRotor rotor;
        try { rotor = new HelicopterRotor(geometry()); } catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("blades", e.getMessage())); return issues; }
        double mach = tipMach();
        if (mach > 0.95) { issues.add(DesignIssue.invalid("controls.designRpm", "The tips would be supersonic in forward flight")); }
        else { DesignIssue.range(issues, "tipMach", mach, 0.45, 0.72, "helicopter rotors' hover tip Mach numbers"); }
        double stress = blades.rootStress(controls.designRpm()), yield = blades.material().yieldPa();
        if (stress > yield) { issues.add(DesignIssue.invalid("blades.fill", "The blade roots would yield turning at the design speed")); }
        else { DesignIssue.range(issues, "rootStressOverYield", stress / yield, 0, 0.5, "rotor blades kept well below yield in tension (they also bend)"); }
        DesignIssue.range(issues, "lockNumber", rotor.lockNumber(1.225), 3, 12, "rotor blades' Lock numbers (Leishman)");
        DesignIssue.range(issues, "solidity", rotor.geometry().solidity(), 0.03, 0.2, "helicopter main and tail rotors");
        return issues;
    }

    public MassProperties mass() {
        double m = totalMassKg(), r = blades.radiusM(), e = hub.offset() * r;
        double spin = blades.massKg() * (r * r * r - e * e * e) / (3 * (r - e)) + 0.5 * hub.massKg(blades.massKg()) * Math.pow(blades.rootCutout() * r, 2);
        return new MassProperties(m, Vec3d.ZERO, spin / 2, spin, spin / 2, 0, 0, 0);
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        double hubInertia = 0.5 * hub.massKg(blades.massKg()) * Math.pow(blades.rootCutout() * blades.radiusM(), 2);
        RotorPart inner = new RotorPart(this, geometry(), controls.minCollectiveDeg(), controls.maxCollectiveDeg(), controls.cyclicDeg(), hubInertia, mass(),
                Components.extrapolations(issues));
        return new Compiled(this, inner.wearing(RotorPart.Wearing.typical()));   // 0.25: its blades can be damaged
    }

    @Override public Map<String, Object> parameters() { return Components.parameters(components()); }

    public static PartDesign read(Fields f) {
        return new RotorDesign(f.string("name", "rotor"), Components.read(f, "blades", RotorBladeDesign::read), Components.read(f, "section", SectionDesign::read),
                Components.read(f, "hub", RotorHubDesign::read), Components.read(f, "controls", RotorControlDesign::read));
    }

    /** AeroCore's rotor with the controls' rate dampers. */
    private static final class Compiled implements Part {
        private final RotorDesign design;
        private final RotorPart inner;

        Compiled(RotorDesign design, RotorPart inner) { this.design = design; this.inner = inner; }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return inner.mass(); }
        @Override public List<CollisionSphere> collision() { return inner.collision(); }
        @Override public List<DesignIssue> issues() { return inner.issues(); }
        @Override public List<Rotor> rotors() { return inner.rotors(); }
        @Override public List<String> inputs() { return inner.inputs(); }
        @Override public int internalStates() { return inner.internalStates(); }
        /** 0.12 (AeroCore 0.30): the rotor's shown values (blade setting, flapping...) for drawing. */
        @Override public int shownValues() { return inner.shownValues(); }
        /** 0.25: the rotor's condition (blades). */
        @Override public List<String> conditions() { return inner.conditions(); }

        @Override public void loads(PartStep step) {
            RotorControlDesign c = design.controls();
            // body rates in the vehicle frame: roll (+x, to starboard), yaw (+y, nose left), pitch (+z, nose up)
            Vec3d w = step.state().orientation().inverse().rotate(step.angularVelocity());
            double collective = Math.clamp(step.input("collective", 0) + c.yawDamping() * w.y(), 0, 1);
            double pitch = Math.clamp(step.input("cyclicPitch", 0) + c.pitchDamping() * w.z(), -1, 1);
            double roll = Math.clamp(step.input("cyclicRoll", 0) - c.rollDamping() * w.x(), -1, 1);
            double theta = Math.toRadians(c.minCollectiveDeg() + collective * (c.maxCollectiveDeg() - c.minCollectiveDeg()));
            inner.loads(step, HelicopterRotor.Controls.fromSticks(theta, pitch, roll, Math.toRadians(c.cyclicDeg()), design.hub().clockwise()));
        }
    }
}
