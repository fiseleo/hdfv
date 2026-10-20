package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.StructureWork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order a vehicle goes together in its room (0.8; the user asked for the assembly rooms to look the part): the
 * structure first, each room-built part's skeleton (frames, 40 % of its labour) and then its skin (60 %), front to back;
 * then the other parts heaviest first, each hoisted (by the overhead crane past AssemblyPlan.LIFT_KG, 25 % of its
 * fitting time; carried in by hand otherwise, 10 %) and fitted; then the cure. Each step's span is its share of the
 * room's labour and cure (AssemblyPlan), so the steps add up to the room's whole work and the build's progress
 * (built 0 to 1) says which step it is in and how far.
 */
public record BuildSequence(List<Step> steps, double laborS, double cureS) {
    public enum Kind { SKELETON, SKIN, HOIST, FIT, CURE }

    /** A step: its kind, the installed part it is of (null for the cure), its span of the build (0 to 1), the part's mass and whether the crane lifts it. */
    public record Step(Kind kind, String part, double start, double end, double massKg, boolean crane) {
        public double within(double built) { return end > start ? Math.clamp((built - start) / (end - start), 0, 1) : 1; }
    }

    public static final double SKELETON_SHARE = 0.4, CRANE_HOIST_SHARE = 0.25, HAND_HOIST_SHARE = 0.1;

    public static BuildSequence of(VehicleBlueprint b) {
        Vehicle v = b.compile();
        record Work(Kind kind, String part, double seconds, double massKg, boolean crane) {}
        List<Work> structure = new ArrayList<>(), parts = new ArrayList<>();
        double cure = 0;
        for (Vehicle.Installed p : v.installed()) {
            var plan = Plans.of(p.part().design());
            double kg = p.part().mass().massKg();
            if (plan.roomBuilt()) {
                double labour = plan.assembleS();
                for (StructureWork s : plan.structure()) { labour += s.laborS(); cure = Math.max(cure, s.cureS()); }
                structure.add(new Work(Kind.SKELETON, p.id(), SKELETON_SHARE * labour, kg, false));
                structure.add(new Work(Kind.SKIN, p.id(), (1 - SKELETON_SHARE) * labour, kg, false));
            } else {
                boolean crane = kg > AssemblyPlan.LIFT_KG;
                double fit = AssemblyPlan.INSTALL_S + AssemblyPlan.INSTALL_S_PER_KG * kg, hoist = (crane ? CRANE_HOIST_SHARE : HAND_HOIST_SHARE) * fit;
                parts.add(new Work(Kind.HOIST, p.id(), hoist, kg, crane));
                parts.add(new Work(Kind.FIT, p.id(), fit - hoist, kg, crane));
            }
        }
        // skeletons before skins (all the frames up, then the plating); the parts heaviest first, each hoisted then fitted
        List<Work> order = new ArrayList<>();
        structure.stream().filter(w -> w.kind() == Kind.SKELETON).forEach(order::add);
        structure.stream().filter(w -> w.kind() == Kind.SKIN).forEach(order::add);
        List<Work> hoists = new ArrayList<>(parts.stream().filter(w -> w.kind() == Kind.HOIST).toList());
        hoists.sort(Comparator.comparingDouble(Work::massKg).reversed());
        for (Work h : hoists) {
            order.add(h);
            parts.stream().filter(w -> w.kind() == Kind.FIT && w.part().equals(h.part())).findFirst().ifPresent(order::add);
        }
        double labour = 0;
        for (Work w : order) { labour += w.seconds(); }
        double total = labour + cure;
        List<Step> steps = new ArrayList<>();
        double at = 0;
        for (Work w : order) {
            double span = total > 0 ? w.seconds() / total : 0;
            steps.add(new Step(w.kind(), w.part(), at, at + span, w.massKg(), w.crane()));
            at += span;
        }
        if (cure > 0) { steps.add(new Step(Kind.CURE, null, at, 1, 0, false)); }
        else if (!steps.isEmpty()) {
            Step last = steps.removeLast();
            steps.add(new Step(last.kind(), last.part(), last.start(), 1, last.massKg(), last.crane()));
        }
        return new BuildSequence(List.copyOf(steps), labour, cure);
    }

    /** The step the build is in (the last when done; null when there are none). */
    public Step at(double built) {
        for (Step s : steps) { if (built < s.end()) { return s; } }
        return steps.isEmpty() ? null : steps.getLast();
    }

    /** How far a part is (0 to 1) in a kind of step: 1 once past it, 0 before it. */
    public double progress(String part, Kind kind, double built) {
        for (Step s : steps) { if (s.kind() == kind && part.equals(s.part())) { return s.within(built); } }
        return kind == Kind.SKELETON || kind == Kind.SKIN ? 1 : 0;
    }

    /** Whether a part is in place (fitting or fitted, or built in the room and begun). */
    public boolean placed(String part, double built) {
        for (Step s : steps) {
            if (part.equals(s.part()) && (s.kind() == Kind.FIT || s.kind() == Kind.SKELETON) && built >= s.start()) { return true; }
        }
        return false;
    }

    /**
     * Where an overhead crane's hook is during a hoist, in the vehicle's frame: lifting the part off the floor at the
     * stores (pick, 2 m up), travelling over to above its mount, and lowering it on (CraneRunway's thirds).
     */
    public static Vec3d hook(Vec3d pick, Vec3d drop, double headroomM, double t) { return CraneRunway.hook(pick, drop, headroomM, t); }
}
