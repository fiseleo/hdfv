package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.PropellerModels;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Material;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.PropellerPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A propeller whose hub is at the part origin, pushing along the part's +X when turning positively about it.
 * <ul>
 * <li>Inside the Wageningen B-series range (2 to 7 blades, expanded area ratio 0.30 to 1.05, pitch ratio 0.5 to 1.4;
 *     Oosterveld and van Oossanen 1975): the B-series regression ahead, and astern and windmilling MARIN's measured
 *     four-quadrant data for the three tested screws (B3-65 P/D 1.0, B4-70 P/D 1.0 and 1.4) or blade-element momentum
 *     theory of the same geometry otherwise, joined by 15 degree ramps (WageningenB.fourQuadrant).</li>
 * <li>Outside it: blade-element momentum theory in all quadrants (BladeElementPropeller), reported as an extrapolation
 *     of the B-series. This is also the model for air propellers far from B-series shapes.</li>
 * </ul>
 * Constant-chord blades: chord ratio c/D = (A_E/A_0) pi / (2 Z (1 - h)), h the hub ratio; the BEMT section is the
 * marine fit of TestPlatform.PROPELLER_SECTION. Loads are blended by the disk's immersion in water and air
 * (PropellerSolver; no ventilation of single blades, no hull wake). The fluid torque acts on the rotor; a motor
 * joined by a shaft must supply it, and the vehicle feels it through the shaft train (VehicleDynamics).
 * Mass: blades of mean thickness tau c (tau the thickness ratio) and a hub cylinder 0.3 D long, of the material.
 */
public record PropellerDesign(String name, double diameterM, int blades, double areaRatio, double pitchRatio, double hubRatio,
                              double thicknessRatio, MassSpec mass) implements PartDesign {
    public static final String TYPE = "hdfv:propeller";
    private static final String B_SERIES = "Wageningen B-series (Oosterveld and van Oossanen 1975)";

    public PropellerDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mass, "mass");
    }

    @Override public String type() { return TYPE; }

    /** Constant blade chord over diameter giving the expanded area ratio. */
    public double chordRatio() { return PropellerModels.chordRatio(blades, areaRatio, hubRatio); }

    public boolean inBSeriesRange() { return PropellerModels.inBSeriesRange(blades, areaRatio, pitchRatio); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(diameterM > 0) || !Double.isFinite(diameterM)) { issues.add(DesignIssue.invalid("diameterM", "Diameter must be positive")); }
        if (blades < 2 || blades > 12) { issues.add(DesignIssue.invalid("blades", "Blade count must be 2 to 12")); }
        if (!(areaRatio > 0) || !Double.isFinite(areaRatio)) { issues.add(DesignIssue.invalid("areaRatio", "Area ratio must be positive")); }
        if (!(pitchRatio > 0 && pitchRatio <= 3)) { issues.add(DesignIssue.invalid("pitchRatio", "Pitch ratio must be in (0, 3]")); }
        if (!(hubRatio > 0 && hubRatio < 0.5)) { issues.add(DesignIssue.invalid("hubRatio", "Hub ratio must be in (0, 0.5)")); }
        if (!(thicknessRatio > 0 && thicknessRatio <= 0.5)) { issues.add(DesignIssue.invalid("thicknessRatio", "Blade thickness ratio must be in (0, 0.5]")); }
        if (issues.isEmpty() && chordRatio() > 1) { issues.add(DesignIssue.invalid("areaRatio", "Blades this wide overlap: lower the area ratio or add blades")); }
        mass.check(issues, "mass", false);
        if (issues.isEmpty()) {
            DesignIssue.range(issues, "blades", blades, 2, 7, B_SERIES + "; blade-element theory used");
            DesignIssue.range(issues, "areaRatio", areaRatio, 0.30, 1.05, B_SERIES + "; blade-element theory used");
            DesignIssue.range(issues, "pitchRatio", pitchRatio, 0.5, 1.4, B_SERIES + "; blade-element theory used");
        }
        return issues;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new PropellerPart(this, diameterM, blades, pitchRatio, hubRatio, chordRatio(), () -> PropellerModels.fourQuadrant(blades, areaRatio, pitchRatio, hubRatio),
                mass.resolve(t -> unitMass()), issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList())
                .wearing(PropellerPart.Wearing.typical());   // 0.25: its blades can be damaged
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("diameterM", diameterM, "blades", blades, "areaRatio", areaRatio, "pitchRatio", pitchRatio,
                "hubRatio", hubRatio, "thicknessRatio", thicknessRatio, "mass", mass.toJson());
    }

    public static PartDesign read(Fields f) {
        return new PropellerDesign(f.string("name", "propeller"), f.number("diameterM"), f.integer("blades"), f.number("areaRatio"),
                f.number("pitchRatio"), f.number("hubRatio", 0.18), f.number("thicknessRatio", 0.1),
                f.has("mass") ? MassSpec.fromJson(f.child("mass")) : MassSpec.solid(Material.BRONZE));
    }

    /** Unit-density mass: blades as plates from the hub to the tip, a hub cylinder 0.3 D long. */
    private MassProperties unitMass() {
        double r = diameterM / 2, rh = hubRatio * r, chord = chordRatio() * diameterM, hubLength = 0.3 * diameterM;
        double blade = blades * chord * (r - rh) * thicknessRatio * chord, hub = Math.PI * rh * rh * hubLength;
        // Spin axis: blades as rods from rh to r, hub a solid cylinder. Across: half the spin value for the disk of blades.
        double spin = blade * (r * r * r - rh * rh * rh) / (3 * (r - rh)) + 0.5 * hub * rh * rh;
        double across = 0.5 * blade * (r * r * r - rh * rh * rh) / (3 * (r - rh)) + hub * (3 * rh * rh + hubLength * hubLength) / 12;
        return new MassProperties(blade + hub, Vec3d.ZERO, spin, across, across, 0, 0, 0);
    }
}
