package dev.mcmod.hdfv.part;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.MassPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A point mass at the part origin (ballast, cargo, a battery): mass only, no loads of its own. */
public record BallastDesign(String name, double massKg) implements PartDesign {
    public static final String TYPE = "hdfv:ballast";

    public BallastDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(massKg > 0) || !Double.isFinite(massKg)) { issues.add(DesignIssue.invalid("massKg", "Mass must be positive")); }
        return issues;
    }

    @Override public Part compile() {
        DesignException.throwIfInvalid(check());
        MassProperties mass = MassProperties.point(massKg, Vec3d.ZERO);
        return new MassPart(this, mass, List.of());
    }

    @Override public Map<String, Object> parameters() { return Fields.object("massKg", massKg); }

    public static PartDesign read(Fields f) { return new BallastDesign(f.string("name", "ballast"), f.number("massKg")); }
}
