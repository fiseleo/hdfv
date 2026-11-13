package dev.mcmod.hdfv.repair;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.StructureWork;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import dev.mcmod.hdfv.refit.RefitJob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.25: what a repair takes in a repair shop (the user 2026-11-12: a repair shop and repairs on the spot; a part damaged
 * past 80 % is not repaired but replaced by a newly made part of the same design). A job is a set of picks, each a
 * condition of a part (Conditions: 0 new .. 1 failed); each picked condition that is used becomes a task of one kind:
 *
 * <ul>
 * <li>MEND (used below Conditions.REPLACE_FROM): back to new in place. Structure-like conditions (structure, blades, a
 *     tank, suspension, a strut, a turbine's hot section) take stock of their own pieces' alloys, MEND_STOCK x used x the
 *     pieces' finished mass (panels and sections cut out and welded in new, arms straightened and reinforced); wear items
 *     take standard components (Repairs: friction linings for brakes and clutches, bearings for gears, bearing shells and
 *     a fastener kit for an engine's internals, copper wire and steel tube for ancillaries, wire and bearings for a
 *     motor). Labour: MEND_LABOUR x used x the hours of making those pieces (Plans.hours: skimming discs, regrinding,
 *     straightening, rewinding), the fitting of what is brought (Plans' assembling rule: a minute a component, 3 s a kg)
 *     and STRIP_HOURS once a part to get at it.</li>
 * <li>NEW_PIECES: the worn piece of a part made new, not the whole part: tyres always (tread is not mended in place; any
 *     wear means new tyres), and from REPLACE_FROM a wheel's brakes (discs or drums, calipers or shoes), its suspension
 *     arms, a landing gear's strut, a gearbox's clutch. The pieces are the part's own workpieces (made on the machines
 *     from the part's blueprint, matched as the assembly room matches them); fitting by the assembling rule, the old
 *     pieces' metal back as salvage.</li>
 * <li>NEW_PART: a part made at a bench (an engine, a gearbox, an axle...) past REPLACE_FROM in anything else: a new part
 *     item of its exact design (equal design JSON, as Staging matches it); taking the old one out and fitting the new
 *     one as the assembly room fits a part, twice (AssemblyPlan.INSTALL_S and INSTALL_S_PER_KG), and setting it up
 *     (RefitJob.ADJUST_HOURS; an axle's alignment ALIGN_HOURS). The vehicle's blueprint does not change.</li>
 * <li>REBUILD: a part built in the room (a body, a chassis, a wing) past REPLACE_FROM is built again where it stands:
 *     its structure works' stock and filler, labour and cure, and its components, as the assembly room builds it, and
 *     the room's install time twice for what is mounted on it.</li>
 * </ul>
 *
 * A part replaced whole (NEW_PART, REBUILD) is new in every condition: its other picks are done with it (WITH_PART)
 * and its unpicked conditions are set back too. What comes off goes back as salvage: half the metal of the old part or
 * pieces as stock (the share a dismantled vehicle gives back, Staging.salvage; composites, wood and rubber give
 * nothing). The shop draws SHOP_KW while working (the refit shop's tools and lifts).
 */
public record RepairJob(List<Task> tasks, List<AssemblyPlan.Need> parts, List<AssemblyPlan.Piece> pieces, Map<Alloy, Double> stockKg,
                        Map<Component, Integer> components, Map<Alloy, Double> salvageKg, double laborHours, double cureHours, boolean welding) {
    public enum Kind { MEND, NEW_PIECES, NEW_PART, REBUILD }

    /** Why a task is what it is: past REPLACE_FROM; tyres (never mended in place); done with its part's replacement. */
    public static final String WORN_OUT = "worn_out", NEVER_MENDED = "never_mended", WITH_PART = "with_part";

    /** A condition of a part chosen for the job. */
    public record Pick(String part, String condition) {
        /** "part/condition" (a part id has no slash; a condition never). */
        public String key() { return part + "/" + condition; }
        public static Pick of(String key) {
            int at = key.lastIndexOf('/');
            return at <= 0 ? null : new Pick(key.substring(0, at), key.substring(at + 1));
        }
    }

    /** How used a part's condition is (the vehicle's state). */
    @FunctionalInterface public interface Used { double of(String part, String condition); }

    /** One picked condition: how used it was when the job was worked out, what is done and why ("" for a plain mend). */
    public record Task(String part, String condition, double used, Kind kind, String reason) {
        /** Whether the whole part is replaced (every condition of it new). */
        public boolean wholePart() { return kind == Kind.NEW_PART || kind == Kind.REBUILD; }
        public Pick pick() { return new Pick(part, condition); }
    }

    /** Rules: the share of a condition's pieces renewed as stock, and of their making as labour, when mended from used up. */
    public static final double MEND_STOCK = 0.3, MEND_LABOUR = 0.3;
    /** Taking a part apart to get at what is mended, and putting it back, h (once a part). */
    public static final double STRIP_HOURS = 0.5;
    /** The share of the old metal that comes back (a dismantled vehicle's, Staging.salvage). */
    public static final double SALVAGE = 0.5;
    /** The shop's power while working, kW (the refit shop's). */
    public static final double SHOP_KW = RefitJob.SHOP_KW;

    public static final RepairJob NONE = new RepairJob(List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), 0, 0, false);

    public RepairJob {
        tasks = List.copyOf(tasks); parts = List.copyOf(parts); pieces = List.copyOf(pieces);
        stockKg = Map.copyOf(stockKg); components = Map.copyOf(components); salvageKg = Map.copyOf(salvageKg);
    }

    public boolean empty() { return tasks.isEmpty(); }

    /** All the hours: the work, then any cure (a rebuilt structure's adhesive or composite). */
    public double hours() { return laborHours + cureHours; }

    /** The energy, kWh: the shop while working. */
    public double kWh() { return SHOP_KW * laborHours; }

    /** The task of a pick, or null. */
    public Task task(Pick p) {
        for (Task t : tasks) { if (t.part().equals(p.part()) && t.condition().equals(p.condition())) { return t; } }
        return null;
    }

    /**
     * What the shop does with a condition of a part of a design at so much used (none below or at 0: null): tyres are
     * always new pieces; from REPLACE_FROM the piece-wise conditions (Repairs.PIECEWISE) new pieces, a room-built part
     * rebuilt, any other part new; else mended.
     */
    public static Kind kindOf(PartDesign design, String condition, double used) {
        if (!(used > 0)) { return null; }
        if (Conditions.TYRES.equals(condition)) { return Kind.NEW_PIECES; }
        if (used < Conditions.REPLACE_FROM) { return Kind.MEND; }
        if (Repairs.PIECEWISE.contains(condition)) { return Kind.NEW_PIECES; }
        return Plans.of(design).roomBuilt() ? Kind.REBUILD : Kind.NEW_PART;
    }

    /** Every used condition of a vehicle, in installed order (the screen's "all damaged"). */
    public static List<Pick> damaged(Vehicle vehicle, Used used) {
        List<Pick> out = new ArrayList<>();
        for (Vehicle.Installed p : vehicle.installed()) {
            for (String c : p.part().conditions()) { if (used.of(p.id(), c) > 0) { out.add(new Pick(p.id(), c)); } }
        }
        return out;
    }

    /** The job of the picks on a vehicle as it is (picks of conditions it does not have, or not used, are left out). */
    public static RepairJob of(Vehicle vehicle, Used used, Collection<Pick> picks) {
        // the tasks, in installed order and each part's condition order
        Set<String> wanted = new LinkedHashSet<>();
        for (Pick p : picks) { wanted.add(p.key()); }
        List<Task> tasks = new ArrayList<>();
        Map<String, Kind> whole = new LinkedHashMap<>();
        for (Vehicle.Installed p : vehicle.installed()) {
            for (String c : p.part().conditions()) {
                if (!wanted.contains(new Pick(p.id(), c).key())) { continue; }
                double u = Math.clamp(used.of(p.id(), c), 0.0, 1.0);
                Kind k = kindOf(p.part().design(), c, u);
                if (k == null) { continue; }
                String reason = Conditions.TYRES.equals(c) ? NEVER_MENDED : u >= Conditions.REPLACE_FROM ? WORN_OUT : "";
                tasks.add(new Task(p.id(), c, u, k, reason));
                if (k == Kind.NEW_PART || k == Kind.REBUILD) { whole.putIfAbsent(p.id(), k); }
            }
        }
        if (tasks.isEmpty()) { return NONE; }
        // a part replaced whole takes its other picks with it
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            Kind k = whole.get(t.part());
            if (k != null && !t.wholePart()) { tasks.set(i, new Task(t.part(), t.condition(), t.used(), k, WITH_PART)); }
        }
        Acc acc = new Acc();
        Set<String> stripped = new LinkedHashSet<>(), replaced = new LinkedHashSet<>();
        for (Task t : tasks) {
            Vehicle.Installed p = vehicle.installed(t.part());
            PartDesign d = p.part().design();
            PartPlan plan = Plans.of(d);
            if (t.wholePart()) {
                if (replaced.add(t.part())) { acc.whole(p, plan, t.kind()); }
                continue;
            }
            if (stripped.add(t.part())) { acc.laborS += 3600 * STRIP_HOURS; }
            if (t.kind() == Kind.NEW_PIECES) { acc.newPieces(d, plan, t.condition()); }
            else { acc.mend(p, plan, t.condition(), t.used()); }
        }
        return new RepairJob(tasks, List.copyOf(acc.parts.values()), acc.pieces, acc.stock, acc.components, acc.salvage, acc.laborS / 3600, acc.cureS / 3600, acc.welding);
    }

    /** The job's tally as it is worked out. */
    private static final class Acc {
        final Map<String, AssemblyPlan.Need> parts = new LinkedHashMap<>();
        final List<AssemblyPlan.Piece> pieces = new ArrayList<>();
        final Map<Alloy, Double> stock = new EnumMap<>(Alloy.class), salvage = new EnumMap<>(Alloy.class);
        final Map<Component, Integer> components = new EnumMap<>(Component.class);
        double laborS, cureS;
        boolean welding;

        void salvage(Alloy a, double kg) { if (a.family.metal() && kg > 0) { salvage.merge(a, SALVAGE * kg, Double::sum); } }

        void component(Component c, int n) {
            if (n <= 0) { return; }
            components.merge(c, n, Integer::sum);
            laborS += n * (RefitJob.PIECE_S + RefitJob.PIECE_S_PER_KG * c.kg);
        }

        /** A part replaced whole: a new part item, or rebuilt in the room. */
        void whole(Vehicle.Installed p, PartPlan plan, Kind kind) {
            PartDesign d = p.part().design();
            double kg = p.part().mass().massKg();
            if (kind == Kind.NEW_PART) {
                String json = Designs.write(d);
                AssemblyPlan.Need was = parts.get(json);
                parts.put(json, new AssemblyPlan.Need(d, was == null ? 1 : was.count() + 1));
                laborS += 2 * (AssemblyPlan.INSTALL_S + AssemblyPlan.INSTALL_S_PER_KG * kg)
                        + 3600 * (d instanceof AxleDesign || d instanceof LandingGearDesign ? RefitJob.ALIGN_HOURS : RefitJob.ADJUST_HOURS);
            } else {
                for (StructureWork s : plan.structure()) {
                    stock.merge(s.alloy(), s.stockKg() + s.fillerKg(), Double::sum);
                    laborS += s.laborS();
                    cureS = Math.max(cureS, s.cureS());
                    welding |= s.seamM() > 0 || s.fasteners() > 0;
                    salvage(s.alloy(), s.finishedKg());
                }
                for (Workpiece w : plan.workpieces()) { pieces.add(new AssemblyPlan.Piece(d, w)); }
                laborS += plan.assembleS() + 2 * AssemblyPlan.INSTALL_S;
            }
            for (Workpiece w : plan.workpieces()) { salvage(w.alloy(), w.count() * w.finishedKg()); }
        }

        /** A condition's pieces made new (the part's own workpieces), fitted, the old ones' metal back. */
        void newPieces(PartDesign d, PartPlan plan, String condition) {
            for (Workpiece w : Repairs.pieces(plan, condition)) {
                pieces.add(new AssemblyPlan.Piece(d, w));
                laborS += w.count() * (RefitJob.PIECE_S + RefitJob.PIECE_S_PER_KG * w.finishedKg());
                salvage(w.alloy(), w.count() * w.finishedKg());
            }
        }

        /** A condition mended in place: its stock or components, and labour by its pieces' making. */
        void mend(Vehicle.Installed p, PartPlan plan, String condition, double used) {
            List<Workpiece> ws = Repairs.pieces(plan, condition);
            List<StructureWork> works = Repairs.works(plan, condition);
            laborS += 3600 * MEND_LABOUR * used * Plans.hours(new PartPlan(plan.type(), ws, plan.assembly(), 0, works));
            if (Repairs.STOCK.contains(condition)) {
                for (Workpiece w : ws) { stock.merge(w.alloy(), MEND_STOCK * used * w.count() * w.finishedKg(), Double::sum); }
                for (StructureWork s : works) {
                    stock.merge(s.alloy(), MEND_STOCK * used * s.finishedKg(), Double::sum);
                    welding |= s.seamM() > 0 || s.fasteners() > 0;
                }
                return;
            }
            Repairs.components(p, ws, condition, used).forEach(this::component);
        }
    }
}
