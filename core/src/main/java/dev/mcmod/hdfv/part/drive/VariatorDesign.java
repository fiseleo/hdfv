package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.drive.Driveline;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A CVT's variator (無段變速器): a push belt between two pulleys whose ratio runs from the lowest (largest) to the
 * highest (smallest), moving at most ratioRatePerS, a reverse planetary set's ratio, and the belt's torque capacity on
 * the primary pulley at its smallest running radius (Pulles et al. 2005: 2 mu F_ax R / cos beta, mu 0.09-0.105, beta 11
 * degrees; AeroCore Driveline).
 */
public record VariatorDesign(String name, double lowRatio, double highRatio, double reverseRatio, double ratioRatePerS, double frictionCoefficient,
                             double clampForceN, double runningRadiusM, double halfAngleDeg) implements EngineComponent {
    public static final String TYPE = "hdfv:variator";

    public VariatorDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    public double beltNm() { return Driveline.beltCapacity(frictionCoefficient, clampForceN, runningRadiusM, Math.toRadians(halfAngleDeg)); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(lowRatio > 0.3 && lowRatio < 10)) { issues.add(DesignIssue.invalid("lowRatio", "The lowest ratio must be 0.3 to 10")); }
        if (!(highRatio > 0.2 && highRatio < lowRatio)) { issues.add(DesignIssue.invalid("highRatio", "The highest ratio must be below the lowest")); }
        if (!(reverseRatio < -0.2 && reverseRatio > -5)) { issues.add(DesignIssue.invalid("reverseRatio", "The reverse set's ratio must be -0.2 to -5")); }
        Rules.positive(issues, "ratioRatePerS", ratioRatePerS);
        if (!(frictionCoefficient > 0.03 && frictionCoefficient < 0.3)) { issues.add(DesignIssue.invalid("frictionCoefficient", "The friction coefficient must be 0.03 to 0.3")); }
        Rules.positive(issues, "clampForceN", clampForceN);
        if (!(runningRadiusM > 0.01 && runningRadiusM < 0.3)) { issues.add(DesignIssue.invalid("runningRadiusM", "The running radius must be 10 to 300 mm")); }
        if (!(halfAngleDeg > 5 && halfAngleDeg < 20)) { issues.add(DesignIssue.invalid("halfAngleDeg", "The pulleys' half-angle must be 5 to 20 degrees")); }
        if (!issues.isEmpty()) { return issues; }
        DesignIssue.range(issues, "ratioSpread", lowRatio / highRatio, 4, 8, "CVT ratio spreads (Jatco CVT8 7.0, CVT7 7.3; early boxes 5-6)");
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("lowRatio", lowRatio, "highRatio", highRatio, "reverseRatio", reverseRatio, "ratioRatePerS", ratioRatePerS,
                "frictionCoefficient", frictionCoefficient, "clampForceN", clampForceN, "runningRadiusM", runningRadiusM, "halfAngleDeg", halfAngleDeg);
    }

    public static VariatorDesign read(String name, Fields f) {
        return new VariatorDesign(name, f.number("lowRatio", 2.6), f.number("highRatio", 0.38), f.number("reverseRatio", -1.0), f.number("ratioRatePerS", 0.5),
                f.number("frictionCoefficient", 0.095), f.number("clampForceN", 40000), f.number("runningRadiusM", 0.03), f.number("halfAngleDeg", 11));
    }
}
