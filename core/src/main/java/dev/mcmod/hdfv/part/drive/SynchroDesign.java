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
 * The synchronisers (同步器): friction cones that bring a gear to its shaft's speed before the dog teeth engage. Cone
 * torque (Naunheimer et al. eq. 9.2): cones x friction x axial force x mean diameter over twice the sine of the cone's
 * half-angle (brass on steel mu about 0.1, carbon 0.12; half-angle 6-7.5 degrees; multi-cone rings for the low gears).
 * The axial force is the driver's shift force through the linkage (about 80 N at the knob, a lever ratio of 6-10).
 */
public record SynchroDesign(String name, int cones, double frictionCoefficient, double axialForceN, double meanDiameterM, double halfAngleDeg)
        implements EngineComponent {
    public static final String TYPE = "hdfv:synchro";

    public SynchroDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    /** Cone torque, N m. */
    public double torqueNm() { return Driveline.synchroniserTorque(cones, frictionCoefficient, axialForceN, meanDiameterM, Math.toRadians(halfAngleDeg)); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        Rules.count(issues, "cones", cones, 1, 3);
        if (!(frictionCoefficient > 0.02 && frictionCoefficient < 0.5)) { issues.add(DesignIssue.invalid("frictionCoefficient", "The friction coefficient must be 0.02 to 0.5")); }
        Rules.positive(issues, "axialForceN", axialForceN);
        if (!(meanDiameterM > 0.01 && meanDiameterM < 0.5)) { issues.add(DesignIssue.invalid("meanDiameterM", "The cone must be 10 to 500 mm across")); }
        if (!(halfAngleDeg > 3 && halfAngleDeg < 30)) { issues.add(DesignIssue.invalid("halfAngleDeg", "The cone's half-angle must be 3 to 30 degrees")); }
        if (!issues.isEmpty()) { return issues; }
        DesignIssue.range(issues, "halfAngleDeg", halfAngleDeg, 5.5, 8, "synchroniser cones of 6-7.5 degrees (below they stick, above they slip)");
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("cones", cones, "frictionCoefficient", frictionCoefficient, "axialForceN", axialForceN, "meanDiameterM", meanDiameterM, "halfAngleDeg", halfAngleDeg);
    }

    public static SynchroDesign read(String name, Fields f) {
        return new SynchroDesign(name, f.integer("cones", 1), f.number("frictionCoefficient", 0.1), f.number("axialForceN", 600), f.number("meanDiameterM", 0.07),
                f.number("halfAngleDeg", 6.5));
    }
}
