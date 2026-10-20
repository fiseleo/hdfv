package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassShapes;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.SpherePart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A spherical float (part origin at its centre): as one float of FloatingBodyDynamics -- exact cap buoyancy in air and
 * water with the wave pressure, smooth-sphere standard drag (Clift et al.), added mass C_a rho V_wet with the Morison
 * excitation, exact hemisphere heave diffraction when it pierces a wavy surface, and heave radiation as a point
 * absorber of its waterplane radius (WaveRadiation, together with every other part's points).
 * Mass: a hollow sphere of the given wall, or solid without one.
 */
public record FloatDesign(String name, double radiusM, MassSpec mass) implements PartDesign {
    public static final String TYPE = "hdfv:float";

    public FloatDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mass, "mass");
    }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(radiusM > 0) || !Double.isFinite(radiusM)) { issues.add(DesignIssue.invalid("radiusM", "Radius must be positive")); }
        mass.check(issues, "mass", false);
        return issues;
    }

    @Override public Part compile() {
        DesignException.throwIfInvalid(check());
        return new SpherePart(this, radiusM, mass.resolve(t -> MassShapes.sphere(radiusM, t)));
    }

    @Override public Map<String, Object> parameters() { return Fields.object("radiusM", radiusM, "mass", mass.toJson()); }

    public static PartDesign read(Fields f) {
        return new FloatDesign(f.string("name", "float"), f.number("radiusM"), MassSpec.fromJson(f.child("mass")));
    }
}
