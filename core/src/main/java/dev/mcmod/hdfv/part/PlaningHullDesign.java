package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.PlaningHull;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassShapes;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.PlaningHullPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A hard-chine planing hull of Fridsma's (1969) series shape (PlaningHull.fridsma): transom at the part origin, keel of
 * the prismatic part at y = 0, length L forward, chine beam B, deadrise beta, depth D. PlaningDynamics gives its loads
 * (Zarnick 2D+t with Wagner wetting, Garme's near-transom correction, ITTC friction, whisker spray), validated against
 * Fridsma's tank tests at 20 deg deadrise; heave radiation from equal-length segments while the transom is wet, faded
 * out with its ventilation. Fridsma tested deadrise 10, 20 and 30 deg and L/B 4 to 7; 2D+t needs speed coefficients
 * C_V above about 2 (it under-predicts trim and resistance before planing). Mass: a thin shell, or a given mass.
 */
public record PlaningHullDesign(String name, double lengthM, double chineBeamM, double deadriseDeg, double depthM, int stations,
                                int radiationSegments, MassSpec mass) implements PartDesign {
    public static final String TYPE = "hdfv:planing_hull";
    private static final String FRIDSMA = "Fridsma 1969 tank series";

    public PlaningHullDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mass, "mass");
    }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        MotorDesign.positive(issues, "lengthM", lengthM);
        MotorDesign.positive(issues, "chineBeamM", chineBeamM);
        MotorDesign.positive(issues, "depthM", depthM);
        if (!(deadriseDeg > 0 && deadriseDeg < 45)) { issues.add(DesignIssue.invalid("deadriseDeg", "Deadrise must be in (0, 45) degrees")); }
        if (lengthM > 0 && chineBeamM > 0 && !(lengthM > 1.5 * chineBeamM)) { issues.add(DesignIssue.invalid("lengthM", "Length must exceed 1.5 beams (the bow takes one beam)")); }
        if (stations < 8 || stations > 200) { issues.add(DesignIssue.invalid("stations", "Use 8 to 200 stations")); }
        if (radiationSegments < 1 || radiationSegments > 16) { issues.add(DesignIssue.invalid("radiationSegments", "Radiation segments must be 1 to 16")); }
        mass.check(issues, "mass", true);
        if (issues.isEmpty()) {
            DesignIssue.range(issues, "deadriseDeg", deadriseDeg, 10, 30, FRIDSMA);
            DesignIssue.range(issues, "lengthBeamRatio", lengthM / chineBeamM, 4, 7, FRIDSMA);
        }
        return issues;
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        PlaningHull planing = PlaningHull.fridsma(lengthM, chineBeamM, Math.toRadians(deadriseDeg), depthM, stations);
        return new PlaningHullPart(this, planing, chineBeamM, deadriseDeg, depthM, radiationSegments,
                mass.resolve(t -> MassShapes.hullShell(planing.hull(), Double.isFinite(t) ? t : 1e-3)),
                issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lengthM", lengthM, "chineBeamM", chineBeamM, "deadriseDeg", deadriseDeg, "depthM", depthM, "stations", stations,
                "radiationSegments", radiationSegments, "mass", mass.toJson());
    }

    public static PartDesign read(Fields f) {
        return new PlaningHullDesign(f.string("name", "planing hull"), f.number("lengthM"), f.number("chineBeamM"), f.number("deadriseDeg"),
                f.number("depthM"), f.integer("stations", 48), f.integer("radiationSegments", 6), MassSpec.fromJson(f.child("mass")));
    }
}
