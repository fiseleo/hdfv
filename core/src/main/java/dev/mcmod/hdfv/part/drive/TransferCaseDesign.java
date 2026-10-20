package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.air.Components;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A transfer case (hdfv:transfer_case, 分動箱): the gearbox's output to the front and rear axles. In the world it is
 * AeroCore's TransferPart. Rotors: "input" (from the gearbox's "output"), "range", "front" and "rear" (shafts to the
 * axles' "input").
 *
 * <ul>
 * <li>Full-time: a centre differential giving the front frontShare of the torque (planetary; 0.5 a bevel), open,
 *     Torsen (bias ratio) or viscous, locking on "lock" when lockable; low range on "range".</li>
 * <li>Part-time: "mode" 0 two-wheel high (rear only), 1 four high (axles locked together), 2 four low (in low range;
 *     the range changes only when nearly stopped).</li>
 * <li>On-demand: the primary axle driven, the other through a multi-plate coupling taking couplingNmsPerRad per rad/s
 *     of slip between them up to couplingMaxNm (Haldex's first generation); hdfv's rule preloads it with preloadShare of
 *     its largest torque times the throttle while the primary propeller shaft turns below preloadBelowRpm (later
 *     generations' launch preload).</li>
 * </ul>
 * Mass, unless given (estimates at a 400 N m rating, scaling as the rating to the 0.6): part-time with its range set
 * 35 kg (NP231: 35 kg), full-time 32 kg, an on-demand coupling 18 kg; a full-time or on-demand case with a low range
 * 8 kg more.
 */
public record TransferCaseDesign(String name, TransferPart.Kind kind, double lowRatio, double efficiency, double frontShare, TransferPart.Centre centre,
                                 double biasRatio, double viscousNmsPerRad, boolean lockable, boolean frontPrimary, double couplingNmsPerRad,
                                 double couplingMaxNm, double preloadShare, double preloadBelowRpm, double torqueRatingNm, boolean transverse, double massGivenKg)
        implements PartDesign, RecordEditor.Shown {
    public static final String TYPE = "hdfv:transfer_case";

    public TransferCaseDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(centre, "centre"); }

    @Override public String type() { return TYPE; }

    @Override public boolean shows(String parameter) {
        return switch (parameter) {
            case "frontShare", "centre", "lockable" -> kind == TransferPart.Kind.FULL_TIME;
            case "biasRatio" -> kind == TransferPart.Kind.FULL_TIME && centre == TransferPart.Centre.TORSEN;
            case "viscousNmsPerRad" -> kind == TransferPart.Kind.FULL_TIME && centre == TransferPart.Centre.VISCOUS;
            case "frontPrimary", "couplingNmsPerRad", "couplingMaxNm", "preloadShare", "preloadBelowRpm" -> kind == TransferPart.Kind.ON_DEMAND;
            default -> true;
        };
    }

    public double scale() { return Math.max(torqueRatingNm, 1) / 400; }

    public TransferPart.Spec spec() {
        double s = scale();
        return new TransferPart.Spec(kind, lowRatio, efficiency, kind == TransferPart.Kind.FULL_TIME ? frontShare : 0.5, centre, biasRatio, viscousNmsPerRad,
                lockable, couplingNmsPerRad, couplingMaxNm, frontPrimary, 0.005 * s, 0.005 * s, 0.004 * s, transverse);
    }

    public double totalMassKg() {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double base = switch (kind) { case PART_TIME -> 35; case FULL_TIME -> 32; case ON_DEMAND -> 18; };
        return base * Math.pow(scale(), 0.6) + (kind != TransferPart.Kind.PART_TIME && lowRatio > 1 ? 8 : 0);
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(lowRatio >= 1 && lowRatio <= 6)) { issues.add(DesignIssue.invalid("lowRatio", "The low range must be 1 (none) to 6")); }
        if (!(efficiency > 0.5 && efficiency <= 1)) { issues.add(DesignIssue.invalid("efficiency", "The efficiency must be 0.5 to 1")); }
        if (!(torqueRatingNm > 5 && torqueRatingNm < 20000)) { issues.add(DesignIssue.invalid("torqueRatingNm", "The rating must be 5 N m to 20 kN m")); }
        EngineComponent.Rules.mass(issues, massGivenKg);
        switch (kind) {
            case FULL_TIME -> {
                if (!(frontShare > 0.05 && frontShare < 0.95)) { issues.add(DesignIssue.invalid("frontShare", "The front's share must be 0.05 to 0.95")); }
                if (centre == TransferPart.Centre.TORSEN && !(biasRatio >= 1 && biasRatio <= 10)) { issues.add(DesignIssue.invalid("biasRatio", "The bias ratio must be 1 to 10")); }
                if (centre == TransferPart.Centre.VISCOUS) { EngineComponent.Rules.positive(issues, "viscousNmsPerRad", viscousNmsPerRad); }
            }
            case ON_DEMAND -> {
                EngineComponent.Rules.nonnegative(issues, "couplingNmsPerRad", couplingNmsPerRad);
                EngineComponent.Rules.positive(issues, "couplingMaxNm", couplingMaxNm);
                if (!(preloadShare >= 0 && preloadShare <= 1)) { issues.add(DesignIssue.invalid("preloadShare", "The preload share must be 0 to 1")); }
                EngineComponent.Rules.nonnegative(issues, "preloadBelowRpm", preloadBelowRpm);
            }
            default -> { }
        }
        if (!issues.isEmpty()) { return issues; }
        if (lowRatio > 1) { DesignIssue.range(issues, "lowRatio", lowRatio, 1.9, 4.3, "transfer case low ranges (NP231 2.72, NV241OR 4.0, Land Cruiser 2.57)"); }
        if (kind == TransferPart.Kind.FULL_TIME) { DesignIssue.range(issues, "frontShare", frontShare, 0.3, 0.5, "full-time splits (Audi quattro 40/60, Land Cruiser 40/60, 50/50 bevel)"); }
        return issues;
    }

    public MassProperties mass() { return Components.box(totalMassKg(), Vec3d.ZERO, new Vec3d(0.4, 0.3, 0.3).scale(Math.cbrt(scale()))); }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return ShiftRules.transfer(this, new TransferPart(this, spec(), mass(), Components.extrapolations(issues)));
    }

    @Override public Map<String, Object> parameters() {
        return Fields.object("kind", kind.name().toLowerCase(), "lowRatio", lowRatio, "efficiency", efficiency, "frontShare", frontShare, "centre", centre.name().toLowerCase(),
                "biasRatio", biasRatio, "viscousNmsPerRad", viscousNmsPerRad, "lockable", lockable, "frontPrimary", frontPrimary, "couplingNmsPerRad", couplingNmsPerRad,
                "couplingMaxNm", couplingMaxNm, "preloadShare", preloadShare, "preloadBelowRpm", preloadBelowRpm, "torqueRatingNm", torqueRatingNm,
                "transverse", transverse, "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    public static PartDesign read(Fields f) {
        return new TransferCaseDesign(f.string("name", "transfer case"), EngineComponent.Rules.choice(f, "kind", TransferPart.Kind.class, TransferPart.Kind.PART_TIME),
                f.number("lowRatio", 2.72), f.number("efficiency", 0.97), f.number("frontShare", 0.4),
                EngineComponent.Rules.choice(f, "centre", TransferPart.Centre.class, TransferPart.Centre.OPEN), f.number("biasRatio", 3), f.number("viscousNmsPerRad", 40),
                f.bool("lockable", false), f.bool("frontPrimary", true), f.number("couplingNmsPerRad", 15), f.number("couplingMaxNm", 1200),
                f.number("preloadShare", 0.3), f.number("preloadBelowRpm", 1500), f.number("torqueRatingNm", 400), f.bool("transverse", false),
                EngineComponent.Rules.massOr(f));
    }
}
