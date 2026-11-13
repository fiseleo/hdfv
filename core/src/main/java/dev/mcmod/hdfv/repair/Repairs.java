package dev.mcmod.hdfv.repair;

import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.StructureWork;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.part.component.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.25: the rules table of repairs (RepairJob): which of a part's pieces a condition is (its workpieces by key, as the
 * planners name them), which conditions are mended with stock and which with standard components, and how many.
 *
 * <ul>
 * <li>Pieces: tyres "tyre"; brakes the discs or drums and the calipers or shoes; suspension the arms and the steering;
 *     a strut its oleo, spring leg or fork; a clutch the clutch, clutch packs or a CVT's belt; gears the gear sets,
 *     synchronisers, pulleys and chains; an engine's internals its pistons, rods, crank, valves, camshafts and
 *     valvetrain; ancillaries the cooling, intake, exhaust, fuel and ignition pieces (a gas turbine's accessories and
 *     fuel nozzles); a motor its stator and windings; a hot section the pieces of nickel alloys. Structure: a room-built
 *     part's structure works, a bench part's every piece. Others (blades, a tank) and anything a planner names
 *     otherwise: the whole part.</li>
 * <li>Mended with stock of those pieces' alloys: structure, blades, a tank, suspension, a strut, a hot section.</li>
 * <li>Mended with standard components (rules, in whole components): brakes and a clutch relined, a friction lining for
 *     each wheel's brake or each clutch; gears GEAR_BEARINGS ball bearings at used up, in proportion; an engine's
 *     internals two bronze shells a cylinder at used up (main and big-end bearings) and a fastener kit (head bolts and
 *     gaskets); ancillaries copper wire and steel tube, one each per ANCILLARY_KG of the part at used up; a motor
 *     rewound, its windings' mass in copper wire, and two bearings. At least one of each that is needed.</li>
 * </ul>
 */
public final class Repairs {
    private Repairs() {}

    /** Conditions whose worn-out piece is made new rather than the whole part (from Conditions.REPLACE_FROM; tyres always). */
    public static final Set<String> PIECEWISE = Set.of(Conditions.TYRES, Conditions.BRAKES, Conditions.SUSPENSION, Conditions.STRUT, Conditions.CLUTCH);

    /** Conditions mended with stock of their pieces' alloys (the others with standard components). */
    public static final Set<String> STOCK = Set.of(Conditions.STRUCTURE, Conditions.BLADES, Conditions.TANK, Conditions.SUSPENSION, Conditions.STRUT,
            Conditions.HOT_SECTION);

    /** A condition's pieces by workpiece key (the planners' names). */
    static final Map<String, Set<String>> KEYS = Map.of(
            Conditions.TYRES, Set.of("tyre"),
            Conditions.BRAKES, Set.of("brake_disc", "brake_drum", "caliper", "brake_shoes"),
            Conditions.SUSPENSION, Set.of("suspension", "steering"),
            Conditions.STRUT, Set.of("oleo_cylinder", "oleo_piston", "gear_fittings", "spring_leg", "rigid_fork"),
            Conditions.CLUTCH, Set.of("clutch", "clutch_packs", "push_belt"),
            Conditions.GEARS, Set.of("gears", "synchros", "planetary_sets", "pulleys", "transfer_gears", "transfer_chain"),
            Conditions.ENGINE, Set.of("piston", "rod", "crank", "intake_valve", "exhaust_valve", "camshaft", "valvetrain"),
            Conditions.ANCILLARIES, Set.of("radiator", "water_pump", "cooling_shroud", "intake", "exhaust", "fuel_system", "ignition", "oil_pump", "oil_pan",
                    "intercooler", "injector", "accessories", "fuel_nozzles"),
            Conditions.MOTOR, Set.of("stator", "windings"));

    /** Ball bearings of a gearbox's or transfer case's overhaul at used up. */
    public static final int GEAR_BEARINGS = 6;
    /** Bronze bearing shells a cylinder of an engine's overhaul at used up. */
    public static final int SHELLS_PER_CYLINDER = 2;
    /** An engine's (or turbine's) mass a length of wire and of tube its ancillaries take at used up, kg. */
    public static final double ANCILLARY_KG = 100;

    /** The workpieces a condition is (empty for a room-built part's structure: its works). */
    public static List<Workpiece> pieces(PartPlan plan, String condition) {
        if (Conditions.STRUCTURE.equals(condition)) { return plan.roomBuilt() ? List.of() : plan.workpieces(); }
        if (Conditions.HOT_SECTION.equals(condition)) {
            List<Workpiece> hot = plan.workpieces().stream().filter(w -> w.alloy().family == Alloy.Family.NICKEL).toList();
            return hot.isEmpty() ? plan.workpieces() : hot;
        }
        Set<String> keys = KEYS.get(condition);
        if (keys == null) { return plan.workpieces(); }
        List<Workpiece> out = plan.workpieces().stream().filter(w -> keys.contains(w.key())).toList();
        // a part whose planner names its pieces otherwise: the whole part (a room-built one has its structure for that)
        return out.isEmpty() && !Conditions.TYRES.equals(condition) && !Conditions.BRAKES.equals(condition) ? plan.workpieces() : out;
    }

    /** The structure works a condition is: a room-built part's structure, or its whole structure where it has no pieces of the condition. */
    public static List<StructureWork> works(PartPlan plan, String condition) {
        if (Conditions.STRUCTURE.equals(condition)) { return plan.structure(); }
        Set<String> keys = KEYS.get(condition);
        if (keys != null && plan.workpieces().stream().noneMatch(w -> keys.contains(w.key())) && !Conditions.TYRES.equals(condition)
                && !Conditions.BRAKES.equals(condition)) {
            return plan.structure();
        }
        return List.of();
    }

    /** The standard components a condition (one of those not mended with stock) takes at so much used. */
    public static Map<Component, Integer> components(Vehicle.Installed part, List<Workpiece> pieces, String condition, double used) {
        Map<Component, Integer> out = new EnumMap<>(Component.class);
        switch (condition) {
            case Conditions.BRAKES -> out.put(Component.FRICTION_LINING, Math.max(1, count(pieces, "brake_disc", "brake_drum")));
            case Conditions.CLUTCH -> out.put(Component.FRICTION_LINING, Math.max(1, count(pieces, "clutch", "clutch_packs", "push_belt")));
            case Conditions.GEARS -> out.put(Component.BALL_BEARING, least(GEAR_BEARINGS * used));
            case Conditions.ENGINE -> {
                int cylinders = count(pieces, "piston");
                out.put(Component.BRONZE_BUSHING, least(SHELLS_PER_CYLINDER * Math.max(cylinders, 1) * used));
                out.put(Component.FASTENER_KIT, 1);
            }
            case Conditions.ANCILLARIES -> {
                double runs = Math.max(1, Math.round(part.part().mass().massKg() / ANCILLARY_KG)) * used;
                out.put(Component.COPPER_WIRE, least(runs));
                out.put(Component.STEEL_TUBE, least(runs));
            }
            case Conditions.MOTOR -> {
                double windings = pieces.stream().filter(w -> w.key().equals("windings")).mapToDouble(w -> w.count() * w.finishedKg()).sum();
                out.put(Component.COPPER_WIRE, Math.max(1, (int) Math.ceil(used * windings / Component.COPPER_WIRE.kg - 1e-9)));
                out.put(Component.BALL_BEARING, 2);
            }
            default -> { }
        }
        return out;
    }

    /** At least one, else the nearest whole number. */
    private static int least(double n) { return Math.max(1, (int) Math.round(n)); }

    private static int count(List<Workpiece> pieces, String... keys) {
        int n = 0;
        for (Workpiece w : pieces) { for (String k : keys) { if (w.key().equals(k)) { n += w.count(); } } }
        return n;
    }
}
