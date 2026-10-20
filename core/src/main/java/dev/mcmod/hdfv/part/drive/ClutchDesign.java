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
 * A friction clutch (離合器): a manual's dry single plate under its diaphragm spring, a dual-clutch box's wet packs, or
 * an automatic's shift elements. Capacity by uniform wear (AeroCore Driveline): faces x friction x clamp force x mean
 * radius (the inner and outer diameters' mean over two); a single dry plate has two faces at mu 0.25-0.3, a wet pack
 * many at mu 0.1-0.13 (LuK's clutch course: 134/190 mm, 3500 N, mu 0.27 gives 153 N m). Its moment of inertia (the
 * disc's) and mass come with the gearbox's.
 */
public record ClutchDesign(String name, int faces, double frictionCoefficient, double clampForceN, double innerDiameterM, double outerDiameterM)
        implements EngineComponent {
    public static final String TYPE = "hdfv:clutch";

    public ClutchDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    /** Torque capacity, N m. */
    public double capacityNm() { return Driveline.clutchCapacity(faces, frictionCoefficient, clampForceN, innerDiameterM, outerDiameterM); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        Rules.count(issues, "faces", faces, 1, 40);
        if (!(frictionCoefficient > 0.03 && frictionCoefficient < 0.8)) { issues.add(DesignIssue.invalid("frictionCoefficient", "The friction coefficient must be 0.03 to 0.8")); }
        Rules.positive(issues, "clampForceN", clampForceN);
        if (!(outerDiameterM > 0.02 && outerDiameterM < 1)) { issues.add(DesignIssue.invalid("outerDiameterM", "The outer diameter must be 20 mm to 1 m")); }
        if (!(innerDiameterM > 0 && innerDiameterM < outerDiameterM)) { issues.add(DesignIssue.invalid("innerDiameterM", "The inner diameter must be below the outer")); }
        if (!issues.isEmpty()) { return issues; }
        DesignIssue.range(issues, "innerOverOuter", innerDiameterM / outerDiameterM, 0.55, 0.75, "clutch facings (DIN 1435: inner 0.55-0.72 of the outer)");
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("faces", faces, "frictionCoefficient", frictionCoefficient, "clampForceN", clampForceN, "innerDiameterM", innerDiameterM,
                "outerDiameterM", outerDiameterM);
    }

    public static ClutchDesign read(String name, Fields f) {
        return new ClutchDesign(name, f.integer("faces", 2), f.number("frictionCoefficient", 0.27), f.number("clampForceN", 5000), f.number("innerDiameterM", 0.150),
                f.number("outerDiameterM", 0.228));
    }
}
