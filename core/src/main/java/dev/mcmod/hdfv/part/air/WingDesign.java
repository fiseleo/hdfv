package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.aero.Wing;
import dev.mcmod.aerocore.api.aero.WingStructure;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.WingPart;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A wing, tailplane or fin (hdfv:wing) assembled from designed components: root and tip sections, planform, structure,
 * a trailing-edge surface (flap, elevator or rudder) and ailerons. Its root's quarter-chord point is the part origin
 * (x forward, y up, z starboard; a fin is a half wing mounted on its side). In flight it is AeroCore's WingPart:
 * the lifting line on the sections' polars, the surfaces' roles as inputs ("flap", "elevator", "rudder", "aileron",
 * -1..1, positive trailing edge down; an aileron +1 rolls to starboard).
 * <p>
 * Checks: the components', overlapping surfaces, and the structure at the design load (WingStructure): spar cap stress
 * against the box material's yield, web shear against its shear yield (0.58 of tensile, von Mises), tip deflection.
 * Mass: the structure's estimate (caps, webs, skin, ribs) or the given one, as a slab of the mean chord.
 */
public record WingDesign(String name, SectionDesign rootSection, SectionDesign tipSection, PlanformDesign planform, WingStructureDesign structure,
                         WingSurfaceDesign trailingEdge, WingSurfaceDesign aileron) implements PartDesign {
    public static final String TYPE = "hdfv:wing";
    /** Strips per half wing in flight (the bench uses more). */
    public static final int STRIPS = 8;

    public WingDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{rootSection, tipSection, planform, structure, trailingEdge, aileron}) { Objects.requireNonNull(o, "component"); }
    }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("rootSection", rootSection); m.put("tipSection", tipSection); m.put("planform", planform); m.put("structure", structure);
        m.put("trailingEdge", trailingEdge); m.put("aileron", aileron);
        return m;
    }

    public List<Wing.ControlSurface> surfaces() {
        List<Wing.ControlSurface> out = new ArrayList<>();
        for (WingSurfaceDesign s : List.of(trailingEdge, aileron)) { if (s.active()) { out.add(s.surface()); } }
        return out;
    }

    /** The wing for benches (more strips than in flight). */
    public Wing wing(int stripsPerHalf) { return new Wing(planform.planform(), rootSection.airfoil(), tipSection.airfoil(), surfaces(), stripsPerHalf); }

    /** The structure at the design load. */
    public WingStructure.Result structureResult() {
        return WingStructure.analyze(planform.planform(), rootSection.thicknessRatio(), tipSection.thicknessRatio(), structure.box(), structure.materials(),
                structure.designLiftN());
    }

    public double totalMassKg() { return Double.isNaN(structure.massGivenKg()) ? structureResult().massKg() : structure.massGivenKg(); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = Components.issues(components());
        if (Components.invalid(issues)) { return issues; }
        if (trailingEdge.active() && aileron.active() && trailingEdge.fromSpan() < aileron.toSpan() && aileron.fromSpan() < trailingEdge.toSpan()) {
            issues.add(DesignIssue.invalid("aileron.fromSpan", "The ailerons overlap the trailing-edge surface"));
        }
        if (aileron.active() && aileron.role() != WingSurfaceDesign.Role.AILERON && aileron.role() != WingSurfaceDesign.Role.NONE) {
            issues.add(DesignIssue.invalid("aileron.role", "The aileron page holds ailerons (or none)"));
        }
        if (trailingEdge.role() == WingSurfaceDesign.Role.AILERON) {
            issues.add(DesignIssue.invalid("trailingEdge.role", "Ailerons go on the aileron page"));
        }
        if (aileron.active() && !planform.mirrored()) {
            issues.add(DesignIssue.invalid("aileron.role", "A half wing has no other side for its ailerons to work against"));
        }
        double tRoot = rootSection.thicknessRatio(), tTip = tipSection.thicknessRatio();
        if (!(tRoot > 0) || !(tTip > 0)) { return issues; }
        WingStructure.Result r = structureResult();
        double yield = structure.boxMaterial().yieldPa();
        if (r.capStressPa() > yield) {
            issues.add(DesignIssue.invalid("structure.capThicknessM", "The spar caps would yield at the design load (" + Math.round(r.capStressPa() / 1e6) + " MPa)"));
        } else { DesignIssue.range(issues, "capStressOverYield", r.capStressPa() / yield, 0, 1, "ultimate loads kept within yield"); }
        if (r.webShearPa() > 0.58 * yield) {
            issues.add(DesignIssue.invalid("structure.webThicknessM", "The spar webs would yield in shear at the design load (" + Math.round(r.webShearPa() / 1e6) + " MPa)"));
        }
        DesignIssue.range(issues, "tipDeflectionOverSpan", r.tipDeflectionM() / planform.planform().semiSpanM(), 0, 0.15, "wings bending at most about a tenth of their half span at ultimate load");
        return issues;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        // 0.25: the wing's structure can be damaged
        return new WingPart(this, planform.planform(), rootSection.airfoil(), tipSection.airfoil(), surfaces(), STRIPS, mass(), Components.extrapolations(issues))
                .wearing(WingPart.Wearing.typical());
    }

    /** The mass as a slab of the mean chord and mean thickness along the span, at the area's centroid. */
    public MassProperties mass() {
        Wing.Planform p = planform.planform();
        double half = p.semiSpanM(), l = p.taper(), mac = p.meanChordM();
        double y = half * (1 + 2 * l) / (3 * (1 + l));   // spanwise centroid of a half's area
        double sweep = Math.tan(Math.toRadians(p.sweepDeg())), dihedral = Math.tan(Math.toRadians(p.dihedralDeg()));
        double thick = 0.5 * (rootSection.thicknessRatio() + tipSection.thicknessRatio()) * mac;
        Vec3d centre = new Vec3d(-y * sweep - 0.15 * mac, y * dihedral, p.mirrored() ? 0 : y);
        double spanSize = p.mirrored() ? 2 * half : half;
        return Components.box(totalMassKg(), centre, new Vec3d(mac, Math.max(thick, 0.01), spanSize));
    }

    @Override public Map<String, Object> parameters() { return Components.parameters(components()); }

    public static PartDesign read(Fields f) {
        return new WingDesign(f.string("name", "wing"), Components.read(f, "rootSection", SectionDesign::read), Components.read(f, "tipSection", SectionDesign::read),
                Components.read(f, "planform", PlanformDesign::read), Components.read(f, "structure", WingStructureDesign::read),
                Components.read(f, "trailingEdge", WingSurfaceDesign::read), Components.read(f, "aileron", WingSurfaceDesign::read));
    }
}
