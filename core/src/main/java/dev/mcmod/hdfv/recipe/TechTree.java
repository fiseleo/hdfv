package dev.mcmod.hdfv.recipe;

import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.CncConfig;
import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.manufacture.Operation;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.ToolConfig;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.part.component.ComponentDesign;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The tech tree (0.23; the user chose "科技樹順序"): the stage of every item, worked out from the recipes as they are
 * (so a data pack's changes show) and the standard components' routes.
 *
 * <ul>
 * <li>An item is made by ways: each needs, all together, one of each of its sets of things (ingredients, the machine it
 *     is made at, the power it takes). A way's stage is the latest of the earliest of each set; an item's is its
 *     earliest way's. Things of the world (vanilla items, ores, unknown tags) are stage 0.</li>
 * <li>A standard component goes one stage past the machines its route needs: each step can be done by some machine
 *     as it is built (its controller, its members and a drive, a head of its family, the accessories that step needs,
 *     an NC drive when only that holds the grade, a CNC's modules), so the foundry's castings are stage 1, the lathe
 *     built of them stage 1, the lathe's shafts stage 2...</li>
 * <li>Electricity (POWER) is the generator's stage; the electric drives, the CNC, the welding station, the arc furnace,
 *     the cell, the air separation unit and the refinery need it.</li>
 * </ul>
 * RecipeRulesTest builds the tree from the recipe files and checks every item reachable and the stages in order;
 * the game builds it from its recipe manager for the items' tooltips.
 */
public final class TechTree {
    private TechTree() {}

    /** What mining the mod's ores gives (things of the world, stage 0). */
    public static final Set<String> MINED = Set.of("hdfv:bauxite", "hdfv:chromite", "hdfv:magnesite", "hdfv:raw_ilmenite", "hdfv:raw_nickel",
            "hdfv:raw_tin", "hdfv:raw_zinc", "hdfv:raw_garnet");

    /** Electricity (a pseudo item: the generator's stage). */
    public static final String POWER = "hdfv:power";
    /** Where a way is: its kind (the tooltip's "made at"). */
    public static final String CRAFTING = "crafting", ASSEMBLING = "assembling", ALLOYING = "alloying", ARC = "electric_furnace", CELL = "electrolysis",
            MACHINE = "machine", REFINERY = "refinery", SMELTING = "smelting", WORLD = "world", OTHER = "other";

    /** A way an item is made: where, the sets it needs (one of each), and how many stages it adds (a machined component's 1). */
    public record Way(String output, String where, List<Set<String>> needs, int bump) {
        public Way {
            needs = needs.stream().map(Set::copyOf).toList();
        }
    }

    /** An item's stage and where its earliest way is. */
    public record Stage(int tier, String where) {}

    /** The id of a stock of an alloy, or a stock bit (the items are told apart by their alloy). */
    public static String stock(Alloy a) { return "hdfv:stock#" + a.id(); }

    public static String bit(Alloy a) { return "hdfv:stock_bit#" + a.id(); }

    /** The block a station is (its controller). */
    public static String block(Station s) { return "hdfv:" + (s == Station.CNC ? "cnc_machining_centre" : s.name().toLowerCase(Locale.ROOT)); }

    /** The stage of each item the ways reach (items no way reaches are left out); world things are stage 0. */
    public static Map<String, Stage> stages(List<Way> ways, Set<String> world) {
        Map<String, Stage> out = new HashMap<>();
        for (String w : world) { out.put(w, new Stage(0, WORLD)); }
        boolean changed = true;
        for (int round = 0; changed && round < ways.size() + 2; round++) {
            changed = false;
            for (Way w : ways) {
                int t = 0;
                boolean all = true;
                for (Set<String> need : w.needs()) {
                    int best = Integer.MAX_VALUE;
                    for (String n : need) {
                        Stage s = out.get(n);
                        if (s == null && unknownTag(n)) { s = new Stage(0, WORLD); }
                        if (s != null) { best = Math.min(best, s.tier()); }
                    }
                    if (best == Integer.MAX_VALUE) { all = false; break; }
                    t = Math.max(t, best);
                }
                if (!all) { continue; }
                t += w.bump();
                Stage now = out.get(w.output());
                if (now == null || t < now.tier()) { out.put(w.output(), new Stage(t, w.where())); changed = true; }
            }
        }
        return out;
    }

    /** A tag no item file lists (vanilla's and NeoForge's): a thing of the world. */
    static boolean unknownTag(String id) { return id.startsWith("#"); }

    // ---------------------------------------------------------------------------------------------- the fixed ways

    /** Ways that are not recipes: electricity, the refinery's and the air separation unit's products, splitting stock. */
    public static List<Way> fixed() {
        List<Way> out = new ArrayList<>();
        out.add(new Way(POWER, OTHER, List.of(Set.of("hdfv:generator")), 0));
        Set<String> unit = Set.of("hdfv:process_unit"), column = Set.of("hdfv:distillation_column"), power = Set.of(POWER);
        for (String p : List.of("hdfv:bitumen")) { out.add(new Way(p, REFINERY, List.of(column, Set.of("hdfv:vacuum_column"), power), 0)); }
        for (String p : List.of("hdfv:polyethylene", "hdfv:polypropylene", "hdfv:synthetic_rubber", "hdfv:epoxy_resin", "hdfv:carbon_fibre")) {
            out.add(new Way(p, REFINERY, List.of(column, unit, Set.of("hdfv:polymer_reactor"), Set.of("hdfv:cracking_furnace"), power), 0));
        }
        for (String gas : List.of("hdfv:oxygen_cylinder", "hdfv:nitrogen_cylinder", "hdfv:argon_cylinder")) {
            out.add(new Way(gas, MACHINE, List.of(Set.of("hdfv:empty_gas_cylinder"), Set.of("hdfv:air_separation_unit"), power), 0));
        }
        for (Alloy a : Alloy.values()) { out.add(new Way(bit(a), CRAFTING, List.of(Set.of(stock(a))), 0)); }
        // the electric drives run off the grid
        for (String d : List.of("hdfv:motor_drive", "hdfv:nc_drive")) { out.add(new Way(powered(d), OTHER, List.of(Set.of(d), power), 0)); }
        return out;
    }

    /** A block that needs the grid to work (a pseudo item: the block and electricity). */
    public static String powered(String block) { return "powered:" + block; }

    // ---------------------------------------------------------------------------------------------- the components' routes

    /** The standard components' ways: from their stock, one stage past the machines that do their steps. */
    public static List<Way> components() {
        List<Way> out = new ArrayList<>();
        Map<String, List<Set<String>>> requirements = new HashMap<>();
        for (Component c : Component.values()) {
            PartPlan plan = Plans.of(ComponentDesign.of(c));
            Workpiece w = plan.workpieces().get(0);
            List<Set<String>> needs = new ArrayList<>();
            needs.add(Set.of(stock(w.alloy())));
            for (int i = 0; i < w.route().size(); i++) {
                Set<String> step = new LinkedHashSet<>();
                for (List<Set<String>> req : requirements(w.route().get(i), w)) {
                    String id = "req:" + req.hashCode();
                    if (!requirements.containsKey(id)) {
                        requirements.put(id, req);
                        out.add(new Way(id, MACHINE, req, 0));
                    }
                    step.add(id);
                }
                needs.add(step);
            }
            out.add(new Way("hdfv:" + c.id(), MACHINE, needs, 1));
        }
        return out;
    }

    /** The machines (as built: each a set of things, one of each) that can do a step of a workpiece. */
    public static List<List<Set<String>>> requirements(Operation op, Workpiece w) {
        List<List<Set<String>>> out = new ArrayList<>();
        for (Station s : Station.values()) {
            if (s == Station.ASSEMBLY_ROOM || s == Station.ASSEMBLY_BENCH) { continue; }
            if (s == Station.CNC) {
                for (CncConfig c : CncConfig.PRESETS) { if (c.can(op)) { out.add(cnc(c)); } }
            } else if (ToolConfig.STATIONS.contains(s)) {
                out.addAll(tools(s, op, w));
            } else if (s.does(op.process()) && (!op.process().cutting() || s.can(op, w))) {
                List<Set<String>> req = new ArrayList<>(List.of(Set.of(block(s))));
                if (s == Station.WELDING_STATION) { req.add(Set.of(POWER)); }
                out.add(req);
            }
        }
        return out;
    }

    /** A manual machine tool or a family: each head, accessories and NC drive that do the step, as built from its pattern. */
    private static List<List<Set<String>>> tools(Station s, Operation op, Workpiece w) {
        List<List<Set<String>>> out = new ArrayList<>();
        Pattern p = Structures.machine(s);
        List<ToolConfig.Accessory> fit = new ArrayList<>();
        for (ToolConfig.Accessory a : ToolConfig.Accessory.values()) { if (a.fits(s)) { fit.add(a); } }
        List<Head> heads = s.family ? Head.of(s) : java.util.Collections.singletonList(null);
        for (Head h : heads) {
            for (int mask = 0; mask < 1 << fit.size(); mask++) {
                Set<ToolConfig.Accessory> acc = EnumSet.noneOf(ToolConfig.Accessory.class);
                for (int k = 0; k < fit.size(); k++) { if ((mask >> k & 1) != 0) { acc.add(fit.get(k)); } }
                for (boolean nc : new boolean[]{false, true}) {
                    ToolConfig t = new ToolConfig(s, h, ToolConfig.maxSize(s), acc, nc);
                    boolean can = op.process().cutting() ? t.fits(w) && t.can(op, w) : s.does(op.process());
                    if (!can) { continue; }
                    List<Set<String>> req = new ArrayList<>();
                    req.add(Set.of(block(s)));
                    for (Set<String> slot : slots(p)) {
                        if (h != null && slot.contains("hdfv:" + h.kind)) { req.add(Set.of("hdfv:" + h.kind)); continue; }
                        if (slot.contains("hdfv:" + Structures.MOTOR_DRIVE)) {
                            boolean electric = h != null && h.electricOnly || p == Structures.ELECTRO_MACHINE;
                            req.add(nc ? Set.of(powered("hdfv:" + Structures.NC_DRIVE))
                                    : electric ? Set.of(powered("hdfv:" + Structures.MOTOR_DRIVE), powered("hdfv:" + Structures.NC_DRIVE))
                                    : Set.of("hdfv:" + Structures.PULLEY_DRIVE, powered("hdfv:" + Structures.MOTOR_DRIVE), powered("hdfv:" + Structures.NC_DRIVE)));
                            continue;
                        }
                        req.add(slot);
                    }
                    for (ToolConfig.Accessory a : acc) { req.add(Set.of("hdfv:" + a.kind)); }
                    out.add(req);
                }
            }
        }
        return out;
    }

    /** A CNC of a preset as built: the pattern's base, its modules, electricity. */
    private static List<Set<String>> cnc(CncConfig c) {
        List<Set<String>> req = new ArrayList<>();
        req.add(Set.of(block(Station.CNC)));
        req.add(Set.of("hdfv:" + Structures.GUARD));
        req.add(Set.of("hdfv:" + Structures.BED));
        req.add(Set.of("hdfv:" + Structures.FRAME));
        boolean bHead = c.turningSpindle() && c.tilt();
        req.add(Set.of("hdfv:" + (bHead ? Structures.B_AXIS_HEAD : Structures.SPINDLE_HEAD)));
        if (c.turningSpindle()) { req.add(Set.of("hdfv:" + Structures.TURNING_SPINDLE)); }
        else if (c.tilt()) { req.add(Set.of("hdfv:" + Structures.TRUNNION)); }
        else if (c.rotaryTable()) { req.add(Set.of("hdfv:" + Structures.ROTARY_TABLE)); }
        if (c.subSpindle()) { req.add(Set.of("hdfv:" + Structures.SUB_SPINDLE)); }
        if (c.turrets() > 0) { req.add(Set.of("hdfv:" + Structures.TURRET, "hdfv:" + Structures.Y_TURRET)); }
        if (c.turretY()) { req.add(Set.of("hdfv:" + Structures.Y_TURRET)); }
        req.add(Set.of(POWER));
        return req;
    }

    /** The slots a pattern must fill (its required cells' kinds, each set one slot), less its controller. */
    static List<Set<String>> slots(Pattern p) {
        List<Set<String>> out = new ArrayList<>();
        Set<Character> seen = new HashSet<>();
        for (Pattern.Cell cell : p.cells(0, 0, 0, 0, p.minRepeats(), p.minLength())) {
            if (!seen.add(cell.symbol())) { continue; }
            Pattern.Slot slot = p.slot(cell.symbol());
            if (slot == null || slot.optional()) { continue; }
            Set<String> kinds = new LinkedHashSet<>();
            for (String k : slot.kinds()) { kinds.add("hdfv:" + k); }
            out.add(kinds);
        }
        return out;
    }
}
