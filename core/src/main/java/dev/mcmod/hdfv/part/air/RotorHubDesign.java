package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A helicopter rotor's hub (旋翼槳轂): fully articulated (flap hinges at the offset), teetering (a two-blade rotor rocking
 * on one hinge at the centre: no offset, no spring) or hingeless (the blades' flexure as an equivalent offset hinge and
 * spring); which way it turns seen from above. Mass: given, or a share of the blades' (0.35 articulated, 0.25
 * teetering, 0.3 hingeless; estimates from published rotor head weights).
 */
public record RotorHubDesign(String name, HubType hubType, double hingeOffset, double flapSpringNmPerRad, boolean clockwise, double massGivenKg)
        implements EngineComponent {
    public static final String TYPE = "hdfv:rotor_hub";

    public enum HubType { ARTICULATED, TEETERING, HINGELESS }

    public RotorHubDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(hubType, "hubType"); }

    @Override public String type() { return TYPE; }

    /** The flap hinge's offset (fraction of the radius) the rotor model uses. */
    public double offset() { return hubType == HubType.TEETERING ? 0 : hingeOffset; }
    /** The flap spring (N m/rad) the rotor model uses. */
    public double spring() { return hubType == HubType.TEETERING ? 0 : flapSpringNmPerRad; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(hingeOffset >= 0 && hingeOffset < 0.3)) { issues.add(DesignIssue.invalid("hingeOffset", "The hinge offset must be 0 to 30 % of the radius")); }
        else if (hubType == HubType.ARTICULATED) { DesignIssue.range(issues, "hingeOffset", hingeOffset, 0.02, 0.06, "articulated rotors"); }
        else if (hubType == HubType.HINGELESS) { DesignIssue.range(issues, "hingeOffset", hingeOffset, 0.08, 0.18, "equivalent offsets of hingeless rotors"); }
        EngineComponent.Rules.nonnegative(issues, "flapSpringNmPerRad", flapSpringNmPerRad);
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** The hub's mass for blades of a mass, kg. */
    public double massKg(double bladesKg) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        return switch (hubType) { case ARTICULATED -> 0.35; case TEETERING -> 0.25; case HINGELESS -> 0.3; } * bladesKg;
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("hubType", hubType.name().toLowerCase(), "hingeOffset", hingeOffset, "flapSpringNmPerRad", flapSpringNmPerRad, "clockwise", clockwise,
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static RotorHubDesign read(String name, Fields f) {
        return new RotorHubDesign(name, EngineComponent.Rules.choice(f, "hubType", HubType.class, HubType.ARTICULATED), f.number("hingeOffset", 0.04),
                f.number("flapSpringNmPerRad", 0), f.bool("clockwise", false), EngineComponent.Rules.massOr(f));
    }
}
