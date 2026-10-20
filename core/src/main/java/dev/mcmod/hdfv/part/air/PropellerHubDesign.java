package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An air propeller's hub (槳轂): the blade setting's fine and coarse stops (degrees added to the blades' angles; equal
 * for a fixed-pitch propeller), and the governor: none (input "pitch" 0..1 moves the blades between the stops) or
 * constant speed (input "rpm" 0..1 sets the target between the lowest and highest governed speed; the governor turns
 * the blades at most GOVERNOR_RATE toward coarse when the propeller is faster than the target, toward fine when
 * slower, in proportion to the error: a flyweight governor's behaviour, a rule). Mass: given, or a share of the
 * blades' (0.6 for a variable-pitch hub with its pitch-change mechanism, 0.15 for a fixed one; estimates).
 */
public record PropellerHubDesign(String name, double fineDeg, double coarseDeg, Governor governor, double minRpm, double maxRpm,
                                 double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:propeller_hub";
    /** The governor's fastest blade movement, deg/s, and the speed error (share of the target) that gives it. */
    public static final double GOVERNOR_RATE = 15, GOVERNOR_FULL_ERROR = 0.05;

    public enum Governor { NONE, CONSTANT_SPEED }

    public PropellerHubDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(governor, "governor"); }

    @Override public String type() { return TYPE; }

    public boolean variable() { return coarseDeg > fineDeg; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(fineDeg >= -30 && fineDeg <= 30)) { issues.add(DesignIssue.invalid("fineDeg", "The fine stop must be within 30 degrees")); }
        if (!(coarseDeg >= fineDeg && coarseDeg <= 60)) { issues.add(DesignIssue.invalid("coarseDeg", "The coarse stop must be at or above the fine one, at most 60 degrees")); }
        if (governor == Governor.CONSTANT_SPEED) {
            if (!variable()) { issues.add(DesignIssue.invalid("governor", "A constant-speed governor needs blades that turn (coarse above fine)")); }
            EngineComponent.Rules.positive(issues, "minRpm", minRpm);
            if (!(maxRpm >= minRpm)) { issues.add(DesignIssue.invalid("maxRpm", "The highest governed speed must be at least the lowest")); }
        }
        if (!(maxRpm > 0)) { issues.add(DesignIssue.invalid("maxRpm", "The rated speed must be positive")); }
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** The hub's mass for blades of a mass, kg. */
    public double massKg(double bladesKg) { return Double.isNaN(massGivenKg) ? (variable() ? 0.6 : 0.15) * bladesKg : massGivenKg; }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("fineDeg", fineDeg, "coarseDeg", coarseDeg, "governor", governor.name().toLowerCase(), "minRpm", minRpm, "maxRpm", maxRpm,
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static PropellerHubDesign read(String name, Fields f) {
        return new PropellerHubDesign(name, f.number("fineDeg", 0), f.number("coarseDeg", 0), EngineComponent.Rules.choice(f, "governor", Governor.class, Governor.NONE),
                f.number("minRpm", 1800), f.number("maxRpm", 2700), EngineComponent.Rules.massOr(f));
    }
}
