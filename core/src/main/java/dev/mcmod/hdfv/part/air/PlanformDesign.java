package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.aero.Wing;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A wing's planform (平面形狀): plan-view span (tip to tip when mirrored, root to tip for a half wing such as a fin),
 * root and tip chords, quarter-chord sweep, dihedral, the root's incidence and the tip's twist from it (negative washes
 * the tip out). No mass of its own.
 */
public record PlanformDesign(String name, double spanM, double rootChordM, double tipChordM, double sweepDeg, double dihedralDeg,
                             double incidenceDeg, double twistDeg, boolean mirrored) implements EngineComponent {
    public static final String TYPE = "hdfv:planform";
    private static final String LIFTING_LINE = "wings the lifting line was checked on (Helmbold, DATCOM)";

    public PlanformDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    public Wing.Planform planform() { return new Wing.Planform(spanM, rootChordM, tipChordM, sweepDeg, dihedralDeg, incidenceDeg, twistDeg, mirrored); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.positive(issues, "spanM", spanM);
        EngineComponent.Rules.positive(issues, "rootChordM", rootChordM);
        EngineComponent.Rules.positive(issues, "tipChordM", tipChordM);
        if (!(Math.abs(sweepDeg) < 75)) { issues.add(DesignIssue.invalid("sweepDeg", "Sweep must be within 75 degrees")); }
        if (!(Math.abs(dihedralDeg) <= 60)) { issues.add(DesignIssue.invalid("dihedralDeg", "Dihedral must be within 60 degrees")); }
        if (!(Math.abs(incidenceDeg) <= 30)) { issues.add(DesignIssue.invalid("incidenceDeg", "Incidence must be within 30 degrees")); }
        if (!(Math.abs(twistDeg) <= 30)) { issues.add(DesignIssue.invalid("twistDeg", "Twist must be within 30 degrees")); }
        if (issues.isEmpty()) {
            Wing.Planform p = planform();
            DesignIssue.range(issues, "aspectRatio", p.aspectRatio(), 2, 16, LIFTING_LINE);
            DesignIssue.range(issues, "sweepDeg", Math.abs(sweepDeg), 0, 35, LIFTING_LINE + "; tip stall comes early beyond");
            DesignIssue.range(issues, "taper", p.taper(), 0.2, 1, LIFTING_LINE);
        }
        return issues;
    }

    @Override public double massKg() { return 0; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("spanM", spanM, "rootChordM", rootChordM, "tipChordM", tipChordM, "sweepDeg", sweepDeg, "dihedralDeg", dihedralDeg,
                "incidenceDeg", incidenceDeg, "twistDeg", twistDeg, "mirrored", mirrored);
    }

    static PlanformDesign read(String name, Fields f) {
        return new PlanformDesign(name, f.number("spanM"), f.number("rootChordM"), f.number("tipChordM"), f.number("sweepDeg", 0), f.number("dihedralDeg", 0),
                f.number("incidenceDeg", 0), f.number("twistDeg", 0), f.bool("mirrored", true));
    }
}
