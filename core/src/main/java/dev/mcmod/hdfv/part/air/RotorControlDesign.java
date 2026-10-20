package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A rotor's controls (操縱): the collective's stops (blade pitch at 0.75 R), the cyclic's travel, the design speed
 * the checks use, and rate dampers that add to the pilot's inputs (a stability augmentation system's rate feedback, a
 * rule): cyclic forward per rad/s of nose-up pitch rate, cyclic left per rad/s of roll rate to starboard, collective
 * (a share of its travel) per rad/s of nose-left yaw rate (for a tail rotor; negative when more pitch yaws the nose left).
 */
public record RotorControlDesign(String name, double minCollectiveDeg, double maxCollectiveDeg, double cyclicDeg, double designRpm,
                                 double pitchDamping, double rollDamping, double yawDamping) implements EngineComponent {
    public static final String TYPE = "hdfv:rotor_control";

    public RotorControlDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(minCollectiveDeg >= -30 && minCollectiveDeg <= 30)) { issues.add(DesignIssue.invalid("minCollectiveDeg", "Collective stops must be within 30 degrees")); }
        if (!(maxCollectiveDeg >= minCollectiveDeg && maxCollectiveDeg <= 35)) { issues.add(DesignIssue.invalid("maxCollectiveDeg", "The upper collective stop must be above the lower, at most 35 degrees")); }
        if (!(cyclicDeg >= 0 && cyclicDeg <= 20)) { issues.add(DesignIssue.invalid("cyclicDeg", "Cyclic travel must be 0 to 20 degrees")); }
        EngineComponent.Rules.positive(issues, "designRpm", designRpm);
        for (double d : new double[]{pitchDamping, rollDamping, yawDamping}) {
            if (!Double.isFinite(d) || Math.abs(d) > 20) { issues.add(DesignIssue.invalid("pitchDamping", "Rate damping gains must be within 20 per rad/s")); break; }
        }
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("minCollectiveDeg", minCollectiveDeg, "maxCollectiveDeg", maxCollectiveDeg, "cyclicDeg", cyclicDeg, "designRpm", designRpm,
                "pitchDamping", pitchDamping, "rollDamping", rollDamping, "yawDamping", yawDamping);
    }

    static RotorControlDesign read(String name, Fields f) {
        return new RotorControlDesign(name, f.number("minCollectiveDeg", 0), f.number("maxCollectiveDeg", 16), f.number("cyclicDeg", 8), f.number("designRpm", 300),
                f.number("pitchDamping", 0), f.number("rollDamping", 0), f.number("yawDamping", 0));
    }
}
