package dev.mcmod.hdfv.manufacture;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where a step is done (0.7): the game's machines, their processes, the finest IT grade each reaches, spindle power,
 * cutting speed factor and setup time, whether they need an operator and whether they can do complex shapes.
 *
 * <ul>
 * <li>Manual machine tools (an operator at the machine): lathe (turning IT7, tool-post grinding IT6, drilling on the
 *     axis IT11), milling machine (IT8, drilling IT10), shaper-planer (flat faces and keyways IT9), boring mill (boring
 *     IT7, honing IT5, drilling IT10), drill press (IT11, reaming IT8). Typical attainable grades of each process:
 *     Kalpakjian and Schmid fig. 40.5, ISO 286 guidance. High-speed or brazed tools on lighter machines: cutting
 *     speed 0.4 of carbide on a rigid machine.</li>
 * <li>CNC machining centre: turning, milling, boring and drilling (with micro-drilling and EDM for orifices) to IT5,
 *     grinding IT4, flat faces face-milled instead of planed (0.8) IT6, unattended, the
 *     only one for complex shapes (aerofoil blades, impellers, injector orifices, cooling holes).</li>
 * <li>Forming and joining: foundry (casting), forging press (forging, rolling and bending stock), composite oven
 *     (lay-up and cure), tyre vulcanizer, welding station (welding, part-sized), assembly bench, assembly room.</li>
 * <li>0.17 (all the machining machines): nine families, each one multiblock whose head (Head) makes it one machine
 *     or another: grinder, finisher (honing, lapping, polishing, deburring), gear cutter, ram machine (shaper, slotter,
 *     broach), saw, electrical machining (EDM, ECM) and cutting table (laser, plasma, waterjet), both numerically
 *     controlled (nc), threader and riveter. What a family does is what its heads do. 0.16's machines take the new
 *     processes where they really can: a lathe threads with a single point, taps and parts off; a mill taps, deburrs
 *     and mills a plate's profile; a drill press taps and deburrs; a boring mill taps; the CNC taps, thread-mills,
 *     deburrs and profiles; the welding station cuts plate with oxy-fuel and sets rivets by hand.</li>
 * </ul>
 * Electricity (0.8): each draws its base load while it works (coolant pumps, lights, hydraulics idling, fume
 * extraction; baseW, a rule) and its process's power (Plans.job): the spindle motor's input (IE3, ElectricMachine), the
 * foundry's induction furnace (Shop.FURNACE_W at 65 %), the press's billet heater (up to 100 kW at 60 %), the oven,
 * vulcanizer and arc. ratedW is the most it draws; a CNC's comes from its modules (CncConfig), a family's from its heads.
 */
public enum Station {
    FOUNDRY(false, 0, 1, 1800, false, Map.of(Process.CAST, 14)),
    FORGING_PRESS(false, 0, 1, 900, false, Map.of(Process.FORGE, 13, Process.FORM, 12)),
    COMPOSITE_OVEN(false, 0, 1, 900, false, Map.of(Process.LAYUP, 12, Process.CURE, 12)),
    VULCANIZER(false, 0, 1, 600, false, Map.of(Process.VULCANIZE, 12)),
    LATHE(true, 7.5e3, 0.4, 1200, false, Map.of(Process.TURN, 7, Process.GRIND, 6, Process.DRILL, 11, Process.THREAD, 8, Process.TAP, 9, Process.SAW, 13)),
    MILLING_MACHINE(true, 7.5e3, 0.4, 1500, false, Map.of(Process.MILL, 8, Process.DRILL, 10, Process.TAP, 9, Process.DEBURR, 12, Process.PROFILE, 10)),
    PLANER(true, 5e3, 0.3, 900, false, Map.of(Process.PLANE, 9)),
    BORING_MILL(true, 11e3, 0.4, 1800, false, Map.of(Process.BORE, 7, Process.GRIND, 5, Process.DRILL, 10, Process.TAP, 9)),
    DRILL_PRESS(true, 2.2e3, 0.4, 300, false, Map.of(Process.DRILL, 8, Process.TAP, 8, Process.DEBURR, 12)),
    CNC(false, 22e3, 1.0, 900, true, Map.of(Process.TURN, 5, Process.MILL, 5, Process.PLANE, 6, Process.BORE, 5, Process.DRILL, 5, Process.GRIND, 4,
            Process.TAP, 6, Process.THREAD, 6, Process.DEBURR, 8, Process.PROFILE, 8)),
    // 0.23: an oxy-fuel torch on a guide cuts a plate's outline to IT13 (0.17 had 14: it could not cut the plate it was meant to)
    WELDING_STATION(true, 0, 1, 300, false, Map.of(Process.WELD, 12, Process.PROFILE, 13, Process.RIVET, 12)),
    ASSEMBLY_BENCH(true, 0, 1, 300, false, Map.of(Process.ASSEMBLE, 12)),
    ASSEMBLY_ROOM(true, 0, 1, 1800, false, Map.of(Process.WELD, 12, Process.LAYUP, 12, Process.CURE, 12, Process.ASSEMBLE, 12)),
    GRINDER(true, false), FINISHER(true, false), GEAR_CUTTER(true, false), RAM_MACHINE(true, false), SAWING_MACHINE(true, false),
    ELECTRO_MACHINE(false, true), CUTTING_TABLE(false, true), THREADER(true, false), RIVETER(true, false);

    /** Whether it advances only with an operator at it (a family's manual heads; an NC drive takes the operator's place). */
    public final boolean manual;
    /** Spindle power, W (cutting machines; a family's are its heads'). */
    public final double powerW;
    /** Cutting speed over carbide on a rigid machine. */
    public final double speedFactor;
    /** Setting up a batch: fixturing, tools, the first piece, s. */
    public final double setupS;
    /** Whether it can make complex shapes. */
    public final boolean complex;
    /** Whether it is numerically controlled (0.17: the CNC, the electrical machines, the cutting table). */
    public final boolean nc;
    /** Whether it is a family of heads (0.17). */
    public final boolean family;
    private final Map<Process, Integer> grades;

    Station(boolean manual, double powerW, double speedFactor, double setupS, boolean complex, Map<Process, Integer> grades) {
        this.manual = manual; this.powerW = powerW; this.speedFactor = speedFactor; this.setupS = setupS; this.complex = complex;
        this.grades = new EnumMap<>(grades);
        this.nc = complex;
        this.family = false;
    }

    /** A family: manual or numerically controlled; what it does comes from its heads. */
    Station(boolean manual, boolean nc) {
        this.manual = manual; this.powerW = 0; this.speedFactor = 1; this.setupS = 0; this.complex = nc;
        this.grades = new EnumMap<>(Process.class);
        this.nc = nc;
        this.family = true;
    }

    public boolean does(Process p) {
        if (family) { return Head.of(this).stream().anyMatch(h -> h.does(p)); }
        return grades.containsKey(p);
    }

    /** The finest IT grade it reaches in a process (Integer.MAX_VALUE when it cannot do it); a family's best head on any feature. */
    public int bestGrade(Process p) {
        if (family) {
            int g = Integer.MAX_VALUE;
            for (Head h : Head.of(this)) { for (Feature f : Feature.values()) { g = Math.min(g, h.bestGrade(p, f)); } }
            return g;
        }
        return grades.getOrDefault(p, Integer.MAX_VALUE);
    }

    /** Whether it can do an operation: the process, to the grade, and a complex shape only on CNC; a family when one of its heads can (any work). */
    public boolean can(Operation op) { return can(op, null); }

    /** Whether it can do an operation of a workpiece (a family's heads by the work's shape; null: any work). */
    public boolean can(Operation op, Workpiece w) {
        if (family) {
            for (Head h : Head.of(this)) { if (h.can(op, w, false, nc)) { return true; } }
            return false;
        }
        return does(op.process()) && bestGrade(op.process()) <= op.grade() && (!op.complex() || complex);
    }

    /** Electrical base load while working, W (a CNC's: CncConfig.baseW; a family's: its heads'). */
    public double baseW() {
        return switch (this) {
            case FOUNDRY -> 3e3; case FORGING_PRESS -> 4e3; case COMPOSITE_OVEN, VULCANIZER, ASSEMBLY_BENCH -> 300; case WELDING_STATION -> 200;
            case LATHE -> 400; case MILLING_MACHINE -> 500; case PLANER -> 300; case BORING_MILL -> 600; case DRILL_PRESS -> 150;
            case CNC -> CncConfig.VMC_3.baseW(); case ASSEMBLY_ROOM -> 2e3;
            case GRINDER, FINISHER, GEAR_CUTTER, RAM_MACHINE, SAWING_MACHINE, ELECTRO_MACHINE, CUTTING_TABLE, THREADER, RIVETER ->
                    Head.of(this).stream().mapToDouble(h -> h.baseW).max().orElse(0);
        };
    }

    /** Most electrical power it draws, W (a CNC's: CncConfig.ratedW; a family's: its hungriest head's). */
    public double ratedW() {
        return switch (this) {
            case FOUNDRY -> Shop.FURNACE_W / Shop.FURNACE_EFFICIENCY + baseW();
            case FORGING_PRESS -> Shop.HEATER_W / Shop.HEATER_EFFICIENCY + CncConfig.input(Shop.PRESS_W) + baseW();
            case COMPOSITE_OVEN -> Shop.OVEN_W + baseW();
            case VULCANIZER -> Shop.VULCANIZER_W + baseW();
            case WELDING_STATION -> Shop.ARC_W + baseW();
            case ASSEMBLY_BENCH -> 1e3;
            case ASSEMBLY_ROOM -> 20e3;
            case CNC -> CncConfig.VMC_3.ratedW();
            case GRINDER, FINISHER, GEAR_CUTTER, RAM_MACHINE, SAWING_MACHINE, ELECTRO_MACHINE, CUTTING_TABLE, THREADER, RIVETER ->
                    Head.of(this).stream().mapToDouble(Head::ratedW).max().orElse(0) + ToolConfig.NC_W;
            default -> CncConfig.input(powerW) + baseW();
        };
    }

    /** The manual machine tools, the CNC and (0.17) the families: where a cutting operation can go. */
    public static final Station[] CUTTING = {LATHE, MILLING_MACHINE, PLANER, BORING_MILL, DRILL_PRESS, CNC,
            GRINDER, FINISHER, GEAR_CUTTER, RAM_MACHINE, SAWING_MACHINE, ELECTRO_MACHINE, CUTTING_TABLE, THREADER, RIVETER};
}
