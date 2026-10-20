package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A gearbox's case and rating (變速箱): the input torque it is built for (its gears, shafts and case are sized by it,
 * and its clutch or belt is checked against it), whether its shafts lie across the vehicle (a front-drive transaxle,
 * along -z) or along it (+x), and its mass when given. Mass, unless given (estimates from published dry weights at
 * 300 N m, scaling as the rating to the 0.6): a manual 35 kg (a five-speed for 300 N m: 33-40 kg), an automatic 80 kg
 * with its converter and fluid (ZF 6HP26 for 600 N m: 87 kg), a dual-clutch box 75 kg (DQ250 for 350 N m: 93 kg wet,
 * DQ200 for 250 N m: 70 kg), a CVT 85 kg; 2 kg more for each forward gear past five.
 */
public record GearboxLayoutDesign(String name, double torqueRatingNm, boolean transverse, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:gearbox_case";

    public GearboxLayoutDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    /** The rating over 300 N m, the size the inertias and masses are quoted at. */
    public double scale() { return Math.max(torqueRatingNm, 1) / 300; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!(torqueRatingNm > 5 && torqueRatingNm < 20000)) { issues.add(DesignIssue.invalid("torqueRatingNm", "The rating must be 5 N m to 20 kN m")); }
        Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** The case's mass for a kind with some forward gears, kg. */
    public double massKg(Gearboxes.Kind kind, int gears) {
        if (!Double.isNaN(massGivenKg)) { return massGivenKg; }
        double base = switch (kind) { case MANUAL -> 35; case AUTOMATIC -> 80; case DUAL_CLUTCH -> 75; case CVT -> 85; };
        return base * Math.pow(scale(), 0.6) + 2 * Math.max(gears - 5, 0);
    }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("torqueRatingNm", torqueRatingNm, "transverse", transverse, "massKg", Rules.mass(massGivenKg));
    }

    public static GearboxLayoutDesign read(String name, Fields f) {
        return new GearboxLayoutDesign(name, f.number("torqueRatingNm", 300), f.bool("transverse", false), Rules.massOr(f));
    }
}
