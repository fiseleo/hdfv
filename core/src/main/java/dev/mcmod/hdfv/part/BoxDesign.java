package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassShapes;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.BoxPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A box-shaped body (cabin, fuselage, pontoon, chassis) centred on the part origin, sides along the part axes.
 * Split into cells^3 equal cells, each sampled at its centre:
 * <ul>
 * <li>Buoyancy when closed: air and water by the cell's wet fraction, the water depth over the cell's height measured
 *     vertically through the cell centre (exact for a level box in still water as cells grow fine).</li>
 * <li>Drag: -1/2 rho C_D A_p(u) |u| u with A_p(u) = sum A_i |u . e_i|, the box's projected area across the relative
 *     flow, shared equally by the cells (so turning is damped too); C_D 1.05 is a cube face-on (Hoerner 1965,
 *     Fluid-Dynamic Drag 3-16). Taken implicitly at the step's start speed, as sphere drag.</li>
 * </ul>
 * Not modelled: added mass, wave radiation and Froude-Krylov beyond the local hydrostatic pressure (a box pontoon
 * in waves is under-damped), lift. Mass: a hollow box of the given wall (outer minus inner box), or solid.
 */
public record BoxDesign(String name, Vec3d sizeM, double dragCoefficient, boolean closed, int cells, MassSpec mass) implements PartDesign {
    public static final String TYPE = "hdfv:box";

    public BoxDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sizeM, "sizeM");
        Objects.requireNonNull(mass, "mass");
    }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(sizeM.x() > 0 && sizeM.y() > 0 && sizeM.z() > 0) || !Double.isFinite(sizeM.x() + sizeM.y() + sizeM.z())) {
            issues.add(DesignIssue.invalid("sizeM", "Every side must be positive"));
        }
        if (!(dragCoefficient >= 0) || !Double.isFinite(dragCoefficient)) { issues.add(DesignIssue.invalid("dragCoefficient", "Drag coefficient must be zero or positive")); }
        if (cells < 1 || cells > 6) { issues.add(DesignIssue.invalid("cells", "Cells per side must be 1 to 6")); }
        mass.check(issues, "mass", false);
        return issues;
    }

    @Override public Part compile() {
        DesignException.throwIfInvalid(check());
        return new BoxPart(this, sizeM, dragCoefficient, closed, cells, mass.resolve(t -> MassShapes.box(sizeM, t)));
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("sizeM", Fields.vector(sizeM), "dragCoefficient", dragCoefficient, "closed", closed, "cells", cells, "mass", mass.toJson());
    }

    public static PartDesign read(Fields f) {
        return new BoxDesign(f.string("name", "box"), f.vec("sizeM"), f.number("dragCoefficient", 1.05), f.bool("closed", true),
                f.integer("cells", 2), MassSpec.fromJson(f.child("mass")));
    }
}
