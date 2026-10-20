package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A CVT's control (無段變速控制; hdfv's rule, ShiftRules): it aims the ratio so the engine turns at a target speed
 * between lightRpm (no throttle) and fullRpm (full throttle) by the throttle, within the variator's range and rate
 * (the primary pulley's target is the engine's times the converter's present speed ratio, so the converter's slip does
 * not take the engine off it); the converter locks up above lockupRpm in drive below 85 % throttle.
 */
public record CvtControlDesign(String name, double lightRpm, double fullRpm, double lockupRpm) implements EngineComponent {
    public static final String TYPE = "hdfv:cvt_control";

    public CvtControlDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    public double targetRpm(double throttle) { return lightRpm + Math.clamp(throttle, 0, 1) * (fullRpm - lightRpm); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        Rules.positive(issues, "lightRpm", lightRpm);
        if (!(fullRpm >= lightRpm)) { issues.add(DesignIssue.invalid("fullRpm", "The full-throttle speed must be at or above the light one")); }
        Rules.nonnegative(issues, "lockupRpm", lockupRpm);
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() { return Fields.object("lightRpm", lightRpm, "fullRpm", fullRpm, "lockupRpm", lockupRpm); }

    public static CvtControlDesign read(String name, Fields f) {
        return new CvtControlDesign(name, f.number("lightRpm", 1500), f.number("fullRpm", 5500), f.number("lockupRpm", 1200));
    }
}
