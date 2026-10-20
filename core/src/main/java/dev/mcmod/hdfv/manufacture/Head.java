package dev.mcmod.hdfv.manufacture;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The heads of the machine families (0.17; the user 2026-10-18 chose one multiblock a family whose head block makes it
 * one machine or another): what each does to what grade, its power, base load, setting up, whether it is driven by
 * electricity only, what it uses up and what it takes.
 *
 * <ul>
 * <li>Grades: the usual attainable IT grades of each process (Kalpakjian and Schmid fig. 40.5; gear quality to
 *     ISO 1328 read as the IT grade of the tooth's size): grinding IT5, honing IT4, lapping IT3, hobbing and shaping
 *     IT7, shaving IT6, gear grinding IT4, broaching IT7, wire EDM IT4, sinker EDM IT5, EDM hole drilling IT5 on
 *     orifices, laser cutting IT10, waterjet IT11, plasma IT13, sawing IT11-14, rolled threads IT6. An NC drive or a
 *     digital readout holds one grade finer (not both).</li>
 * <li>Power: spindle (or generator, pump, beam) input of machines of the kind: a cylindrical grinder's 7.5 kW
 *     wheelhead, a 15 kW broach, a 4 kW fibre laser (11 kW at the wall), a 260 A plasma at 150 V, a 30 kW waterjet
 *     pump, an ECM rectifier of 2000 A at 12 V; base loads for coolant, dielectric and electrolyte pumps, filters,
 *     chillers and extraction.</li>
 * <li>Shape rules (fitsShape): centreless grinding takes only plain turned work (round it, no shoulder at its ends,
 *     at most 0.15 m across); internal grinding takes bores (from an end, or a BORE feature); surface grinding flat
 *     faces; hobbing outside teeth only (round it); the slotter inside splines; plasma cuts metal only (it needs a
 *     conductor), the laser metal and wood, the waterjet anything.</li>
 * <li>Capacity (capacity): from the family's size (MachineFrame), see each family's rule below.</li>
 * </ul>
 */
public enum Head {
    // grinder: size = table metres (1-4)
    CYLINDRICAL_GRINDING(Station.GRINDER, "cylindrical_grinding_head", 7.5e3, 800, 900, false, false, Consumable.GRINDING_WHEEL, does(Process.GRIND, 5, Feature.NONE)),
    INTERNAL_GRINDING(Station.GRINDER, "internal_grinding_head", 4e3, 800, 1200, false, false, Consumable.GRINDING_WHEEL, does(Process.GRIND, 5, Feature.NONE, Feature.BORE)),
    SURFACE_GRINDING(Station.GRINDER, "surface_grinding_head", 5.5e3, 800, 600, false, false, Consumable.GRINDING_WHEEL, does(Process.GRIND, 5, Feature.NONE)),
    CENTRELESS_GRINDING(Station.GRINDER, "centreless_grinding_head", 11e3, 1000, 1800, false, false, Consumable.GRINDING_WHEEL, does(Process.GRIND, 5, Feature.NONE)),
    TOOL_GRINDING(Station.GRINDER, "tool_grinding_head", 3e3, 500, 600, true, false, Consumable.GRINDING_WHEEL, does(Process.GRIND, 6, Feature.AEROFOIL)),
    // finisher: size = column courses (1-3)
    HONING(Station.FINISHER, "honing_head", 5.5e3, 600, 600, false, false, Consumable.HONING_STONES, does(Process.GRIND, 4, Feature.BORE)),
    LAPPING(Station.FINISHER, "lapping_head", 2.2e3, 300, 300, false, false, Consumable.LAPPING_COMPOUND, does(Process.LAP, 3, Feature.NONE, Feature.GEAR_TEETH)),
    POLISHING(Station.FINISHER, "polishing_head", 3e3, 300, 120, true, false, Consumable.LAPPING_COMPOUND, does(Process.POLISH, 6, Feature.NONE, Feature.AEROFOIL)),
    DEBURRING(Station.FINISHER, "deburring_head", 1.5e3, 300, 120, false, false, null, does(Process.DEBURR, 12, Feature.NONE)),
    // gear cutter: size = column courses (1-3)
    HOBBING(Station.GEAR_CUTTER, "hobbing_head", 7.5e3, 800, 1800, false, false, Consumable.CUTTING_TOOLS, does(Process.MILL, 7, Feature.GEAR_TEETH, Feature.SPLINE)),
    GEAR_SHAPING(Station.GEAR_CUTTER, "gear_shaping_head", 5.5e3, 800, 1800, false, false, Consumable.CUTTING_TOOLS, does(Process.MILL, 7, Feature.GEAR_TEETH, Feature.SPLINE)),
    SHAVING(Station.GEAR_CUTTER, "shaving_head", 4e3, 800, 1200, false, false, Consumable.CUTTING_TOOLS, does(Process.GRIND, 6, Feature.GEAR_TEETH)),
    GEAR_GRINDING(Station.GEAR_CUTTER, "gear_grinding_head", 11e3, 1200, 2400, false, false, Consumable.GRINDING_WHEEL, does(Process.GRIND, 4, Feature.GEAR_TEETH)),
    CHAMFERING(Station.GEAR_CUTTER, "chamfering_head", 2.2e3, 400, 600, false, false, Consumable.CUTTING_TOOLS, does(Process.DEBURR, 12, Feature.GEAR_TEETH)),
    // ram machine: size = stroke segments (1-3)
    SHAPER(Station.RAM_MACHINE, "shaper_head", 4e3, 300, 600, false, false, Consumable.CUTTING_TOOLS, does(Process.PLANE, 9, Feature.NONE), does(Process.MILL, 10, Feature.SPLINE)),
    SLOTTER(Station.RAM_MACHINE, "slotter_head", 5.5e3, 300, 900, false, false, Consumable.CUTTING_TOOLS, does(Process.MILL, 9, Feature.SPLINE)),
    BROACHING(Station.RAM_MACHINE, "broaching_head", 15e3, 800, 1200, false, false, Consumable.CUTTING_TOOLS,
            does(Process.MILL, 7, Feature.SPLINE, Feature.FIR_TREE), does(Process.BORE, 7, Feature.SPLINE)),
    // saw: size = bed segments (1-3)
    BAND_SAW(Station.SAWING_MACHINE, "band_saw_head", 1.5e3, 200, 120, false, false, Consumable.SAW_BLADE, does(Process.SAW, 13, Feature.NONE)),
    COLD_SAW(Station.SAWING_MACHINE, "cold_saw_head", 3e3, 300, 120, false, false, Consumable.SAW_BLADE, does(Process.SAW, 11, Feature.NONE)),
    HACKSAW(Station.SAWING_MACHINE, "hacksaw_head", 0.75e3, 100, 60, false, false, Consumable.SAW_BLADE, does(Process.SAW, 14, Feature.NONE)),
    // electrical machining: size = tank segments (1-3); numerically controlled, electricity only
    SINKER_EDM(Station.ELECTRO_MACHINE, "sinker_edm_head", 6e3, 1500, 1200, true, true, Consumable.ELECTRODES,
            does(Process.MILL, 5, Feature.FIR_TREE), does(Process.DRILL, 6, Feature.COOLING_HOLE, Feature.ORIFICE)),
    WIRE_EDM(Station.ELECTRO_MACHINE, "wire_edm_head", 10e3, 1500, 900, true, true, Consumable.BRASS_WIRE,
            does(Process.MILL, 4, Feature.FIR_TREE, Feature.SPLINE, Feature.GEAR_TEETH), does(Process.PROFILE, 5, Feature.NONE)),
    HOLE_EDM(Station.ELECTRO_MACHINE, "hole_edm_head", 3e3, 1000, 600, true, true, Consumable.ELECTRODES, does(Process.DRILL, 5, Feature.ORIFICE, Feature.COOLING_HOLE)),
    ECM(Station.ELECTRO_MACHINE, "ecm_head", 24e3, 3000, 1800, true, true, Consumable.ELECTROLYTE, does(Process.MILL, 7, Feature.AEROFOIL), does(Process.DEBURR, 10, Feature.NONE)),
    // cutting table: size = table metres beyond the first (1-6); numerically controlled
    LASER(Station.CUTTING_TABLE, "laser_head", 11.4e3, 2500, 300, true, true, Consumable.ASSIST_GAS,
            does(Process.PROFILE, 10, Feature.NONE), does(Process.DRILL, 8, Feature.COOLING_HOLE)),
    PLASMA(Station.CUTTING_TABLE, "plasma_head", 39e3, 2500, 300, false, true, Consumable.ELECTRODES, does(Process.PROFILE, 13, Feature.NONE)),
    WATERJET(Station.CUTTING_TABLE, "waterjet_head", 30e3, 2000, 300, false, false, Consumable.GARNET, does(Process.PROFILE, 11, Feature.NONE)),
    // threader: size 1
    THREAD_ROLLING(Station.THREADER, "thread_rolling_head", 7.5e3, 300, 600, false, false, Consumable.CUTTING_TOOLS, does(Process.THREAD, 6, Feature.NONE)),
    FLAT_DIE_ROLLING(Station.THREADER, "flat_die_head", 5.5e3, 300, 600, false, false, Consumable.CUTTING_TOOLS, does(Process.THREAD, 7, Feature.NONE)),
    TAPPING(Station.THREADER, "tapping_head", 1.1e3, 100, 120, false, false, Consumable.CUTTING_TOOLS, does(Process.TAP, 7, Feature.NONE)),
    THREAD_MILLING(Station.THREADER, "thread_milling_head", 5.5e3, 400, 900, false, false, Consumable.CUTTING_TOOLS,
            does(Process.THREAD, 6, Feature.NONE), does(Process.TAP, 6, Feature.NONE)),
    // riveter: size = throat segments (1-3)
    SQUEEZE_RIVETER(Station.RIVETER, "squeeze_riveter_head", 3e3, 200, 300, false, false, Consumable.RIVETS, does(Process.RIVET, 12, Feature.NONE)),
    IMPACT_RIVETER(Station.RIVETER, "impact_riveter_head", 0.5e3, 100, 120, false, false, Consumable.RIVETS, does(Process.RIVET, 12, Feature.NONE)),
    DRILL_RIVETER(Station.RIVETER, "drill_riveter_head", 7.5e3, 500, 900, false, false, Consumable.RIVETS,
            does(Process.RIVET, 12, Feature.NONE), does(Process.DRILL, 10, Feature.NONE));

    /** One thing a head does: a process to a grade on steps of these features. */
    public record Ability(Process process, int grade, Set<Feature> features) {}

    private static Ability does(Process p, int grade, Feature... features) {
        return new Ability(p, grade, features.length == 0 ? EnumSet.of(Feature.NONE) : EnumSet.copyOf(List.of(features)));
    }

    public final Station station;
    /** Its block's kind. */
    public final String kind;
    public final double powerW, baseW, setupS;
    /** Whether it can make a complex shape (five axes; the tool grinder only with an NC drive). */
    public final boolean complex;
    /** Whether only electricity drives it (an EDM's generator, a laser, a plasma torch). */
    public final boolean electricOnly;
    /** What it uses up (null: nothing that wears out). */
    public final Consumable uses;
    private final List<Ability> abilities;

    Head(Station station, String kind, double powerW, double baseW, double setupS, boolean complex, boolean electricOnly, Consumable uses, Ability... abilities) {
        this.station = station; this.kind = kind; this.powerW = powerW; this.baseW = baseW; this.setupS = setupS; this.complex = complex;
        this.electricOnly = electricOnly; this.uses = uses; this.abilities = List.of(abilities);
    }

    public List<Ability> abilities() { return abilities; }

    /** The most it draws, W: a motor's input for its power (a generator, beam, torch or pump's power as given), and its base load. */
    public double ratedW() { return (electricOnly ? powerW : CncConfig.input(powerW)) + baseW; }

    /** Whether it is a grinding head (its wheel or stones take the stock by power at grinding's energy). */
    public boolean grinding() { return abilities.stream().anyMatch(a -> a.process() == Process.GRIND); }

    /** The heads of a family. */
    public static List<Head> of(Station s) {
        return java.util.Arrays.stream(values()).filter(h -> h.station == s).toList();
    }

    /** The head a block kind is (null: none). */
    public static Head ofKind(String kind) {
        for (Head h : values()) { if (h.kind.equals(kind)) { return h; } }
        return null;
    }

    /** The finest grade it reaches in a process on a feature (Integer.MAX_VALUE: it cannot). */
    public int bestGrade(Process p, Feature f) {
        int g = Integer.MAX_VALUE;
        for (Ability a : abilities) { if (a.process() == p && a.features().contains(f)) { g = Math.min(g, a.grade()); } }
        return g;
    }

    /** Whether it does a process at all (on any feature). */
    public boolean does(Process p) { return abilities.stream().anyMatch(a -> a.process() == p); }

    /** Whether it can do a step to its grade (one finer when finer), its shape and, for a complex shape, five axes. */
    public boolean can(Operation op, Workpiece w, boolean finer, boolean nc) {
        int g = bestGrade(op.process(), op.feature());
        if (g == Integer.MAX_VALUE || g - (finer ? 1 : 0) > op.grade()) { return false; }
        if (op.complex() && !(complex && (this != TOOL_GRINDING || nc))) { return false; }
        return fitsShape(op, w);
    }

    /** The shape rules: what work each head takes. */
    public boolean fitsShape(Operation op, Workpiece w) {
        int f = op.faces();
        boolean round = (f & Operation.AROUND) != 0, ends = (f & (Operation.FRONT | Operation.BACK)) != 0;
        Workpiece.Envelope e = w == null ? null : w.envelope();
        Alloy.Family family = w == null ? null : w.alloy().family;
        return switch (this) {
            case CYLINDRICAL_GRINDING -> op.feature() != Feature.NONE || round;
            case CENTRELESS_GRINDING -> round && !ends && (e == null || e.turned() && e.diameterM() <= 0.15);
            case INTERNAL_GRINDING -> op.feature() == Feature.BORE || ends;
            case SURFACE_GRINDING -> !round;
            case HOBBING -> round;
            case SLOTTER -> !round;
            case PLASMA -> family == null || family.metal();
            case LASER -> family == null || family.metal() || family == Alloy.Family.WOOD;
            default -> true;
        };
    }

    /**
     * The largest stock it takes on a family of a size, m: length, width, height (a turned part goes by its length and
     * diameter: width and height are the diameter). The rules of each family:
     * grinder: between centres a metre a table segment, 0.4 m swing; internal 0.4 m across and 0.5 long; surface the
     * table's length by 0.5 by 0.4; centreless 0.15 m across, twice the table long (it feeds through); tool grinder
     * 0.4 x 0.3 x 0.3. Finisher: 0.6 m plus 0.4 a course long, 0.8 wide, 0.6 a course high (an engine block on the
     * honing table). Gear cutter: gears 0.3 m across a course plus 0.3, 0.3 m a course long. Ram machine: the shaper
     * and broach 0.4 m a segment plus 0.2 long, 0.4 by 0.4; the slotter 0.5 m a segment across, 0.3 a segment high.
     * Saw: bar stock up to 0.3 (band), 0.12 (cold saw), 0.2 (hacksaw) square, any length a metre a segment plus 2.
     * Electrical: 0.4 m a tank segment plus 0.4, 0.6 by 0.4. Cutting table: plate the table's length (a metre a segment
     * plus one) by 2 m, up to 25 mm thick for the laser, 50 for plasma, 150 for the waterjet. Threader 0.6 m by 0.05
     * across for rolling (0.03 flat dies), 1 by 0.8 by 0.6 for tapping and thread milling. Riveter: 0.5 m a segment
     * plus 0.5 by 0.4 m a segment of throat by 0.4.
     */
    public Workpiece.Envelope capacity(int size) {
        int n = Math.max(size, 1);
        return switch (this) {
            case CYLINDRICAL_GRINDING -> Workpiece.Envelope.turned(n, 0.4);
            case INTERNAL_GRINDING -> Workpiece.Envelope.box(0.5, 0.4, 0.4);
            case SURFACE_GRINDING -> Workpiece.Envelope.box(n, 0.5, 0.4);
            case CENTRELESS_GRINDING -> Workpiece.Envelope.turned(2.0 * n, 0.15);
            case TOOL_GRINDING -> Workpiece.Envelope.box(0.4, 0.3, 0.3);
            case HONING, LAPPING, POLISHING, DEBURRING -> Workpiece.Envelope.box(0.6 + 0.4 * n, 0.8, 0.6 * n);
            case HOBBING, GEAR_SHAPING, SHAVING, GEAR_GRINDING, CHAMFERING -> Workpiece.Envelope.box(0.3 * n, 0.3 * n + 0.3, 0.3 * n + 0.3);
            case SHAPER, BROACHING -> Workpiece.Envelope.box(0.4 * n + 0.2, 0.4, 0.4);
            case SLOTTER -> Workpiece.Envelope.box(0.5 * n, 0.5 * n, 0.3 * n);
            case BAND_SAW -> Workpiece.Envelope.box(n + 2.0, 0.3, 0.3);
            case COLD_SAW -> Workpiece.Envelope.box(n + 2.0, 0.12, 0.12);
            case HACKSAW -> Workpiece.Envelope.box(n + 2.0, 0.2, 0.2);
            case SINKER_EDM, WIRE_EDM, HOLE_EDM, ECM -> Workpiece.Envelope.box(0.4 * n + 0.4, 0.6, 0.4);
            case LASER -> Workpiece.Envelope.box(n + 1.0, 2.0, 0.025);
            case PLASMA -> Workpiece.Envelope.box(n + 1.0, 2.0, 0.05);
            case WATERJET -> Workpiece.Envelope.box(n + 1.0, 2.0, 0.15);
            case THREAD_ROLLING -> Workpiece.Envelope.turned(0.6, 0.05);
            case FLAT_DIE_ROLLING -> Workpiece.Envelope.turned(0.3, 0.03);
            case TAPPING, THREAD_MILLING -> Workpiece.Envelope.box(1.0, 0.8, 0.6);
            case SQUEEZE_RIVETER, IMPACT_RIVETER, DRILL_RIVETER -> Workpiece.Envelope.box(0.5 * n + 0.5, 0.4 * n, 0.4);
        };
    }

    /** Whether a workpiece's stock goes on it at a size (a turned part by its length and diameter; else its sizes, largest first). */
    public boolean fits(Workpiece w, int size) {
        Workpiece.Envelope e = w.envelope(), c = capacity(size);
        if (c.turned()) {
            if (e.turned()) { return e.lengthM() <= c.lengthM() + 1e-9 && e.diameterM() <= c.diameterM() + 1e-9; }
            double[] s = e.sorted();
            return s[0] <= c.lengthM() + 1e-9 && Math.hypot(s[1], s[2]) <= c.diameterM() + 1e-9;
        }
        double[] s = e.sorted(), m = c.sorted();
        return s[0] <= m[0] + 1e-9 && s[1] <= m[1] + 1e-9 && s[2] <= m[2] + 1e-9;
    }
}
