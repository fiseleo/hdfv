package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An automatic or dual-clutch box's control (換檔控制; hdfv's rules, ShiftRules): its shift schedule and how it shifts.
 *
 * <ul>
 * <li>Schedule: in D it shifts up when the box's input would turn faster than the up-shift speed and down when slower
 *     than the down-shift speed, both between their light-throttle and full-throttle values by the throttle (a shift
 *     map's lines: early and short at light throttle, late at full); a down-shift is taken only if the lower gear would
 *     turn below the up-shift speed (no hunting). Kick-down: at full throttle it drops a gear while the lower gear stays
 *     under 90 % of the full-throttle up-shift speed. Between shifts at least HOLD seconds.</li>
 * <li>Manual mode (M): the "shift" input steps a gear up or down on each press; it still drops a gear itself rather
 *     than lug below 80 % of the light-throttle down-shift speed.</li>
 * <li>Shift time: the element or clutch handover (AeroCore's hardware).</li>
 * <li>Automatic: the converter locks up in lockupGear and above, above lockupRpm, once a shift is done and below 85 %
 *     throttle.</li>
 * <li>Dual clutch: the driving clutch is closed by the engine's speed while launching (a centrifugal clutch's rule):
 *     from creepShare of its capacity at idleRpm to all of it at the launch speed (idle plus the throttle times
 *     launchRpm above idle), so the engine runs up to about the launch speed and holds there while the car moves off;
 *     once the clutch has caught the shaft it stays shut until the engine falls below 85 % of idle. The other shaft has
 *     the next gear ready (up while accelerating, down while not).</li>
 * </ul>
 */
public record ShiftScheduleDesign(String name, boolean dualClutch, double shiftTimeS, double upLightRpm, double upFullRpm, double downLightRpm,
                                  double downFullRpm, int lockupGear, double lockupRpm, double idleRpm, double launchRpm, double creepShare)
        implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:shift_schedule";
    /** Least time between shifts, s. */
    public static final double HOLD = 0.8;

    public ShiftScheduleDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "dualClutch" -> false;
            case "lockupGear", "lockupRpm" -> !dualClutch;
            case "idleRpm", "launchRpm", "creepShare" -> dualClutch;
            default -> true;
        };
    }

    public double upRpm(double throttle) { return upLightRpm + Math.clamp(throttle, 0, 1) * (upFullRpm - upLightRpm); }
    public double downRpm(double throttle) { return downLightRpm + Math.clamp(throttle, 0, 1) * (downFullRpm - downLightRpm); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(shiftTimeS > 0.02 && shiftTimeS < 3)) { issues.add(DesignIssue.invalid("shiftTimeS", "The shift time must be 0.02 to 3 s")); }
        Rules.positive(issues, "upLightRpm", upLightRpm);
        if (!(upFullRpm >= upLightRpm)) { issues.add(DesignIssue.invalid("upFullRpm", "The full-throttle up-shift must be at or above the light one")); }
        Rules.positive(issues, "downLightRpm", downLightRpm);
        if (!(downFullRpm >= downLightRpm)) { issues.add(DesignIssue.invalid("downFullRpm", "The full-throttle down-shift must be at or above the light one")); }
        if (!(downLightRpm < upLightRpm * 0.85) || !(downFullRpm < upFullRpm * 0.85)) {
            issues.add(DesignIssue.invalid("downLightRpm", "Down-shifts must lie well below up-shifts (at most 85 %), or the box would hunt"));
        }
        if (dualClutch) {
            Rules.positive(issues, "idleRpm", idleRpm);
            if (!(launchRpm > 0)) { issues.add(DesignIssue.invalid("launchRpm", "The launch speed must be above idle")); }
            if (!(creepShare >= 0 && creepShare < 0.5)) { issues.add(DesignIssue.invalid("creepShare", "The creep share must be 0 to 0.5")); }
        } else {
            if (lockupGear < 1) { issues.add(DesignIssue.invalid("lockupGear", "The lock-up gear must be 1 or more")); }
            Rules.nonnegative(issues, "lockupRpm", lockupRpm);
        }
        if (!issues.isEmpty()) { return issues; }
        DesignIssue.range(issues, "shiftTimeS", shiftTimeS, dualClutch ? 0.1 : 0.2, dualClutch ? 0.6 : 1.0,
                dualClutch ? "dual-clutch handovers (DSG about 0.2-0.4 s)" : "automatic shifts (0.3-0.8 s)");
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("dualClutch", dualClutch, "shiftTimeS", shiftTimeS, "upLightRpm", upLightRpm, "upFullRpm", upFullRpm, "downLightRpm", downLightRpm,
                "downFullRpm", downFullRpm, "lockupGear", lockupGear, "lockupRpm", lockupRpm, "idleRpm", idleRpm, "launchRpm", launchRpm, "creepShare", creepShare);
    }

    public static ShiftScheduleDesign read(String name, Fields f) {
        return new ShiftScheduleDesign(name, f.bool("dualClutch", false), f.number("shiftTimeS", 0.5), f.number("upLightRpm", 2000), f.number("upFullRpm", 6000),
                f.number("downLightRpm", 1200), f.number("downFullRpm", 3500), f.integer("lockupGear", 3), f.number("lockupRpm", 1500), f.number("idleRpm", 800),
                f.number("launchRpm", 2200), f.number("creepShare", 0.04));
    }
}
