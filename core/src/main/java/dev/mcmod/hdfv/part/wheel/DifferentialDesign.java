package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A driven axle's final drive and differential (差速器), or none (driven off: the wheels roll freely).
 *
 * <ul>
 * <li>The final drive's ratio (input over the carrier) and efficiency (a hypoid pair 0.95-0.97, spur or helical
 *     0.98); the input lies along +x (a propeller shaft, a front-engined rear-drive car) or along the axle (transverse:
 *     a transaxle's output, a front-drive car).</li>
 * <li>The differential (AeroCore AxlePart.Differential): open; clutch-pack limited slip (preload and lock factor: the
 *     torque it may move from one side to the other per N m of input); Torsen (torque bias ratio, 2.5-4 usual);
 *     viscous coupling (N m s/rad between the wheels); spool (locked, a go-kart's). Lockable ones lock on the "lock"
 *     input (a locker on an off-roader).</li>
 * <li>The input's inertia (pinion, crown wheel and carrier at the input's speed): 0.004 kg m^2 for the pinion and
 *     shaft plus the crown wheel and carrier's 0.06 over the ratio squared (estimates).</li>
 * <li>Mass (estimates from published units): transverse (in a transaxle) 8 kg, along +x in its own case 25 kg, plus
 *     10 kg of half shafts; a solid axle's housing and tubes instead 35 kg plus 12 kg per metre of track; a limited
 *     slip or Torsen 3 kg more. A solid axle that is not driven still has its beam: 12 kg per metre of track.</li>
 * </ul>
 */
public record DifferentialDesign(String name, boolean driven, double finalRatio, double efficiency, boolean transverse, AxlePart.Differential differential,
                                 double preloadNm, double lockFactor, double biasRatio, double viscousNmsPerRad, boolean lockable, double massGivenKg)
        implements EngineComponent, RecordEditor.Shown {
    public static final String TYPE = "hdfv:differential";

    public DifferentialDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(differential, "differential"); }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        if (parameter.equals("driven")) { return true; }
        if (!driven) { return parameter.equals("massGivenKg"); }
        return switch (parameter) {
            case "preloadNm", "lockFactor" -> differential == AxlePart.Differential.CLUTCH;
            case "biasRatio" -> differential == AxlePart.Differential.TORSEN;
            case "viscousNmsPerRad" -> differential == AxlePart.Differential.VISCOUS;
            case "lockable" -> differential != AxlePart.Differential.SPOOL;
            default -> true;
        };
    }

    /** The input's inertia, kg m^2. */
    public double inputInertiaKgM2() { return 0.004 + 0.06 / (finalRatio * finalRatio); }

    /** AeroCore's drive, or null when not driven. */
    public AxlePart.Drive drive() {
        if (!driven) { return null; }
        return new AxlePart.Drive(finalRatio, efficiency, inputInertiaKgM2(), differential, preloadNm, lockFactor, biasRatio, viscousNmsPerRad,
                lockable && differential != AxlePart.Differential.SPOOL, transverse);
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        Rules.mass(issues, massGivenKg);
        if (!driven) { return issues; }
        if (!(finalRatio > 0.5 && finalRatio < 20)) { issues.add(DesignIssue.invalid("finalRatio", "The final drive ratio must be 0.5 to 20")); }
        if (!(efficiency > 0.5 && efficiency <= 1)) { issues.add(DesignIssue.invalid("efficiency", "The efficiency must be 0.5 to 1")); }
        switch (differential) {
            case CLUTCH -> { Rules.nonnegative(issues, "preloadNm", preloadNm); Rules.nonnegative(issues, "lockFactor", lockFactor); }
            case TORSEN -> { if (!(biasRatio >= 1 && biasRatio <= 10)) { issues.add(DesignIssue.invalid("biasRatio", "The bias ratio must be 1 to 10")); } }
            case VISCOUS -> Rules.positive(issues, "viscousNmsPerRad", viscousNmsPerRad);
            default -> { }
        }
        if (!issues.isEmpty()) { return issues; }
        DesignIssue.range(issues, "finalRatio", finalRatio, 2.5, 5.5, "road cars' final drives (2.5-4.5; off-roaders to 5.4)");
        if (differential == AxlePart.Differential.TORSEN) { DesignIssue.range(issues, "biasRatio", biasRatio, 2, 5, "Torsen T-1 to T-3 bias ratios"); }
        return issues;
    }

    /** The unit's mass for a track (m) and whether the axle is solid, kg. */
    public double massKg(double trackM, boolean solid) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        if (!driven) { return solid ? 12 * trackM : 0; }
        double unit = solid ? 35 + 12 * trackM : (transverse ? 8 : 25) + 10;
        boolean limited = differential == AxlePart.Differential.CLUTCH || differential == AxlePart.Differential.TORSEN;
        return unit + (limited ? 3 : 0);
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("driven", driven, "finalRatio", finalRatio, "efficiency", efficiency, "transverse", transverse, "differential", differential.name().toLowerCase(),
                "preloadNm", preloadNm, "lockFactor", lockFactor, "biasRatio", biasRatio, "viscousNmsPerRad", viscousNmsPerRad, "lockable", lockable,
                "massKg", Rules.mass(massGivenKg));
    }

    public static DifferentialDesign read(String name, Fields f) {
        return new DifferentialDesign(name, f.bool("driven", false), f.number("finalRatio", 3.9), f.number("efficiency", 0.96), f.bool("transverse", false),
                Rules.choice(f, "differential", AxlePart.Differential.class, AxlePart.Differential.OPEN), f.number("preloadNm", 50), f.number("lockFactor", 0.3),
                f.number("biasRatio", 3), f.number("viscousNmsPerRad", 30), f.bool("lockable", false), Rules.massOr(f));
    }

    /** An axle that is not driven. */
    public static DifferentialDesign none() {
        return new DifferentialDesign("differential", false, 3.9, 0.96, false, AxlePart.Differential.OPEN, 50, 0.3, 3, 30, false, Double.NaN);
    }
}
