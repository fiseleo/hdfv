package dev.mcmod.hdfv.part.component;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.MassPart;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A standard component's drawing (0.23): which component, nothing else to set. Saved as a blueprint like any design, it
 * tells the machines what to make; the last machine of its route hands out the component's item instead of a finished
 * workpiece. As a part it is only its mass (nobody bolts a loose shaft to a vehicle, but it compiles like the rest).
 */
public record ComponentDesign(String name, Component kind) implements PartDesign {
    public static final String TYPE = "hdfv:component";

    public ComponentDesign {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
    }

    /** The drawing of a component, named by its id. */
    public static ComponentDesign of(Component c) { return new ComponentDesign(c.id(), c); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() { return List.of(); }

    @Override public Part compile() {
        DesignException.throwIfInvalid(check());
        return new MassPart(this, MassProperties.point(kind.kg, Vec3d.ZERO), List.of());
    }

    @Override public Map<String, Object> parameters() { return Fields.object("kind", kind.id()); }

    public static PartDesign read(Fields f) {
        Component c = Component.ofId(f.string("kind", "steel_plate").toLowerCase(Locale.ROOT));
        if (c == null) { throw new IllegalArgumentException("No component " + f.string("kind", "")); }
        return new ComponentDesign(f.string("name", c.id()), c);
    }
}
