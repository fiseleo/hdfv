package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An axle's layout (車軸佈局): its track (wheel centre to wheel centre), how far below the mount point the hubs sit at
 * the design compression (hubDropM; more lifts the body), steering (largest road-wheel angle, the Ackermann share and
 * the wheelbase it is laid out for) and the anti-roll bar's rate (N/m at the wheels, per metre of one wheel's travel
 * against the other's). Mass: a steering rack and its arms 8 kg, an anti-roll bar 1 kg per 10 N/mm of rate and at least
 * 2 kg (estimates).
 */
public record AxleLayoutDesign(String name, double trackM, double hubDropM, boolean steered, double maxSteerDeg, double ackermann, double wheelbaseM,
                               double antiRollNPerM, double steerRatio, double steerRatioHigh, double steerCrossoverMps) implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:axle_layout";

    public AxleLayoutDesign { Objects.requireNonNull(name, "name"); }

    /** 0.14's layout: steered by the whole steering at every speed. */
    public AxleLayoutDesign(String name, double trackM, double hubDropM, boolean steered, double maxSteerDeg, double ackermann, double wheelbaseM, double antiRollNPerM) {
        this(name, trackM, hubDropM, steered, maxSteerDeg, ackermann, wheelbaseM, antiRollNPerM, 1, 1, 0);
    }

    /**
     * 0.15: whether the steering's share changes or is not the whole (SteerSchedule): a rear axle (wheelbaseM negative:
     * behind the zero-steer line) or a second front axle.
     */
    public boolean scheduled() { return steered && (steerRatio != 1 || (steerCrossoverMps > 0 && steerRatioHigh != steerRatio)); }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "maxSteerDeg", "ackermann", "wheelbaseM", "steerRatio", "steerCrossoverMps" -> steered;
            case "steerRatioHigh" -> steered && steerCrossoverMps > 0;
            default -> true;
        };
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(trackM > 0.2 && trackM < 6)) { issues.add(DesignIssue.invalid("trackM", "The track must be 0.2 to 6 m")); }
        if (!(Math.abs(hubDropM) <= 1)) { issues.add(DesignIssue.invalid("hubDropM", "The hubs must be within 1 m of the mount point")); }
        Rules.nonnegative(issues, "antiRollNPerM", antiRollNPerM);
        if (steered) {
            if (!(maxSteerDeg > 0 && maxSteerDeg <= 60)) { issues.add(DesignIssue.invalid("maxSteerDeg", "The largest steering angle must be 0 to 60 degrees")); }
            if (!(ackermann >= 0 && ackermann <= 1.2)) { issues.add(DesignIssue.invalid("ackermann", "The Ackermann share must be 0 to 1.2")); }
            // 0.15: the distance to the zero-steer line, negative behind it (a steered rear axle)
            if (!(Math.abs(wheelbaseM) > 0.3 && Math.abs(wheelbaseM) < 20)) { issues.add(DesignIssue.invalid("wheelbaseM", "The distance to the zero-steer line must be 0.3 to 20 m either way")); }
            if (!(Math.abs(steerRatio) <= 1)) { issues.add(DesignIssue.invalid("steerRatio", "The axle steers by -1 to 1 times the steering")); }
            if (!(Math.abs(steerRatioHigh) <= 1)) { issues.add(DesignIssue.invalid("steerRatioHigh", "The axle steers by -1 to 1 times the steering")); }
            if (!(steerCrossoverMps >= 0 && steerCrossoverMps <= 60)) { issues.add(DesignIssue.invalid("steerCrossoverMps", "The crossover is 0 (none) to 60 m/s")); }
            if (issues.isEmpty()) { DesignIssue.range(issues, "maxSteerDeg", maxSteerDeg, 25, 45, "cars' road-wheel lock angles (inner wheel)"); }
        }
        return issues;
    }

    @Override public double massKg() { return (steered ? 8 : 0) + (antiRollNPerM > 0 ? Math.max(2, antiRollNPerM / 10000) : 0); }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = Fields.object("trackM", trackM, "hubDropM", hubDropM, "steered", steered, "maxSteerDeg", maxSteerDeg, "ackermann", ackermann,
                "wheelbaseM", wheelbaseM, "antiRollNPerM", antiRollNPerM);
        if (steerRatio != 1 || steerRatioHigh != 1 || steerCrossoverMps != 0) {
            m.putAll(Fields.object("steerRatio", steerRatio, "steerRatioHigh", steerRatioHigh, "steerCrossoverMps", steerCrossoverMps));
        }
        return m;
    }

    public static AxleLayoutDesign read(String name, Fields f) {
        return new AxleLayoutDesign(name, f.number("trackM", 1.5), f.number("hubDropM", 0), f.bool("steered", false), f.number("maxSteerDeg", 35),
                f.number("ackermann", 0.8), f.number("wheelbaseM", 2.6), f.number("antiRollNPerM", 0), f.number("steerRatio", 1), f.number("steerRatioHigh", 1),
                f.number("steerCrossoverMps", 0));
    }
}
