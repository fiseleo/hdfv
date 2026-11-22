package dev.mcmod.hdfv.structure;

import dev.mcmod.hdfv.manufacture.CncConfig;
import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.ToolConfig;
import dev.mcmod.hdfv.manufacture.ToolConfig.Accessory;

/**
 * hdfv's multiblocks (0.8).
 *
 * <ul>
 * <li>CNC: 4 wide, 3 deep, 3 high, the control cabinet (the controller) at the front right of the bottom layer. A bed
 *     under a guarded working zone (two cells that must stay empty), a column and frame at the back carrying the
 *     spindle head. Module cells: the work holding (a plain bed, a rotary table, a trunnion or a turning spindle), the
 *     milling head (a spindle head or a B-axis head), the left bay (guard or a sub-spindle), a lower and a right bay
 *     (bed or guard, or a turret, one with Y). Its modules make its CncConfig (cncConfig).</li>
 * <li>Storage tank (STORAGE_TANK).</li>
 * <li>Oil field (P12): the drilling rig and the pump jack.</li>
 * <li>Refinery (P13): the crude distillation column and the process units.</li>
 * <li>Manual machine tools (0.9): lathe, milling machine, planer, boring mill, drill press, each with its drive (an
 *     electric motor seat or a line shaft's pulley seat: exactly one), a size (lathe bed, mill table and planer bed
 *     lengthen across; boring mill and drill columns grow up) and accessory cells; their ToolConfig (toolConfig).</li>
 * </ul>
 */
public final class Structures {
    private Structures() {}

    public static final String BED = "machine_bed", GUARD = "machine_guard", FRAME = "machine_frame", CNC_CONTROLLER = "cnc_machining_centre";
    public static final String SPINDLE_HEAD = "spindle_head", B_AXIS_HEAD = "b_axis_head", ROTARY_TABLE = "rotary_table", TRUNNION = "trunnion",
            TURNING_SPINDLE = "turning_spindle", SUB_SPINDLE = "sub_spindle", TURRET = "turret", Y_TURRET = "y_turret";

    public static final Pattern CNC = Pattern.builder("cnc")
            .layer("GGGC",
                   "BWXB",
                   "BBBB")
            .layer("GGGG",
                   "S..R",
                   "FHFF")
            .layer("GGGG",
                   "GGGG",
                   "FFFF")
            .controller('C', CNC_CONTROLLER)
            .where('G', GUARD).where('B', BED).where('F', FRAME)
            .where('W', String.join("|", BED, ROTARY_TABLE, TRUNNION, TURNING_SPINDLE))
            .where('X', String.join("|", BED, TURRET, Y_TURRET))
            .where('S', String.join("|", GUARD, SUB_SPINDLE))
            .where('R', String.join("|", GUARD, TURRET, Y_TURRET))
            .where('H', String.join("|", SPINDLE_HEAD, B_AXIS_HEAD))
            .build();

    /**
     * The CNC its modules make, or null when they do not go together (a sub-spindle or turrets without a turning
     * spindle; a B-axis head on a machining centre, whose fifth axis is a trunnion).
     */
    public static CncConfig cncConfig(Pattern.Match m) {
        boolean turning = m.has(TURNING_SPINDLE), bHead = m.has(B_AXIS_HEAD);
        if (bHead && !turning) { return null; }
        int turrets = m.count(TURRET) + m.count(Y_TURRET);
        try {
            return new CncConfig(m.has(ROTARY_TABLE) || m.has(TRUNNION), m.has(TRUNNION) || bHead, turning, m.has(SUB_SPINDLE), turrets, m.has(Y_TURRET));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static final String TANK_WALL = "storage_tank_wall", TANK_ROOF = "storage_tank_roof", TANK_VALVE = "storage_tank_valve";

    /**
     * Storage tank: 5 by 5, a floor, courses of wall round a 3 by 3 inside (which must be empty: the oil is in it), a
     * roof; the valve (the controller) in the middle of the first course's front. 2 to 12 courses, 9 m^3 each.
     */
    public static final Pattern STORAGE_TANK = Pattern.builder("storage_tank")
            .layer("wwwww", "wwwww", "wwwww", "wwwww", "wwwww")
            .layer("wwCww", "w...w", "w...w", "w...w", "wwwww")
            .layer("wwwww", "w...w", "w...w", "w...w", "wwwww")
            .layer("rrrrr", "rrrrr", "rrrrr", "rrrrr", "rrrrr")
            .controller('C', TANK_VALVE).where('w', TANK_WALL).where('r', TANK_ROOF)
            .repeat(2, 2, 1, 11)
            .build();

    /** Litres a formed storage tank holds. */
    public static int storageLitres(Pattern.Match m) { return dev.mcmod.hdfv.fluid.Piping.STORAGE_COURSE_LITRES * (1 + m.repeats()); }

    public static final String TOWER = "control_tower", TOWER_BASE = "tower_base", TOWER_SHAFT = "tower_shaft", TOWER_FLOOR = "tower_cab_floor",
            TOWER_GLASS = "tower_glass", TOWER_ROOF = "tower_roof", TOWER_CONSOLE = "tower_console", TOWER_RADAR = "tower_radar", TOWER_MAST = "tower_mast";

    /**
     * 0.20 control tower (on a 9 x 9 grid, front row first): a two-storey 7 x 7 base with the controller (the duty
     * desk, where a controller villager works) at the middle of its front and a doorway left of it; a 5 x 5 shaft
     * repeated 6 to 56 times (players put their own stairs or ladders inside); the cab: an octagonal floor with a hatch
     * in the middle, two courses of glass round an empty room with an optional console at the front (a player's
     * screen), an octagonal roof; on it an optional radar antenna in the middle and an optional mast (anemometer,
     * obstruction light). 13 to 63 m tall.
     */
    public static final Pattern TOWER_PATTERN = Pattern.builder(TOWER)
            .layer("         ", " BB CBBB ", " B     B ", " B     B ", " B     B ", " B     B ", " B     B ", " BBBBBBB ", "         ")
            .layer("         ", " BB BBBB ", " B     B ", " B     B ", " B     B ", " B     B ", " B     B ", " BBBBBBB ", "         ")
            .layer("         ", "         ", "  SSSSS  ", "  S   S  ", "  S   S  ", "  S   S  ", "  SSSSS  ", "         ", "         ")
            .layer("  FFFFF  ", " FFFFFFF ", "FFFFFFFFF", "FFF   FFF", "FFF   FFF", "FFF   FFF", "FFFFFFFFF", " FFFFFFF ", "  FFFFF  ")
            .layer("  GGGGG  ", " G.....G ", "G...k...G", "G.......G", "G... ...G", "G.......G", "G.......G", " G.....G ", "  GGGGG  ")
            .layer("  GGGGG  ", " G.....G ", "G.......G", "G.......G", "G... ...G", "G.......G", "G.......G", " G.....G ", "  GGGGG  ")
            .layer("  RRRRR  ", " RRRRRRR ", "RRRRRRRRR", "RRRRRRRRR", "RRRRRRRRR", "RRRRRRRRR", "RRRRRRRRR", " RRRRRRR ", "  RRRRR  ")
            .layer("         ", "         ", "  M      ", "         ", "    A    ", "         ", "         ", "         ", "         ")
            .controller('C', TOWER).where('B', TOWER_BASE).where('S', TOWER_SHAFT).where('F', TOWER_FLOOR).where('G', TOWER_GLASS)
            .where('R', TOWER_ROOF).optional('k', TOWER_CONSOLE).optional('A', TOWER_RADAR).optional('M', TOWER_MAST)
            .repeat(2, 2, 6, 56)
            .build();

    /** A formed tower's heights above its base (m): the cab's eyes (standing on its floor) and the radar antenna's top. */
    public record TowerHeights(double eyeM, double antennaM, double roofM) {}

    public static TowerHeights towerHeights(Pattern.Match m) {
        int shaft = m.repeats();
        double floorTop = 2 + shaft + 1, roofTop = floorTop + 2 + 1;
        return new TowerHeights(floorTop + 1.6, roofTop + (m.has(TOWER_RADAR) ? 1 : 0), roofTop);
    }

    public static final String RIG_FLOOR = "rig_floor", DERRICK = "derrick", CROWN_BLOCK = "crown_block", RIG_ROTARY = "rig_rotary", RIG = "drilling_rig";
    public static final String WELLHEAD = "wellhead", JACK_BASE = "jack_base", SAMSON_POST = "samson_post", JACK_CRANK = "jack_crank",
            WALKING_BEAM = "walking_beam", HORSEHEAD = "horsehead", PUMP_JACK = "pump_jack";

    /**
     * Drilling rig: a 3 by 3 rig floor with the drawworks (the controller) in front and the rotary table in the middle,
     * where the well goes down; a derrick of 3 to 6 courses of legs at the corners round an open middle (the drill
     * string's), the crown block on top.
     */
    public static final Pattern DRILLING_RIG = Pattern.builder("drilling_rig")
            .layer("FCF", "FRF", "FFF")
            .layer("D D", " . ", "D D")
            .layer("DDD", "DKD", "DDD")
            .controller('C', RIG).where('F', RIG_FLOOR).where('R', RIG_ROTARY).where('D', DERRICK).where('K', CROWN_BLOCK)
            .repeat(1, 1, 3, 6)
            .build();

    /**
     * Pump jack: in a line, the wellhead, the base and the gearbox and motor (the controller) at the far end; above, the
     * polished rod's room over the wellhead, the samson post and the crank with its counterweights; on top the
     * horsehead over the wellhead and the walking beam.
     */
    public static final Pattern PUMP_JACK_PATTERN = Pattern.builder("pump_jack")
            .layer("WBBBC")
            .layer(". P K")
            .layer(". P  ")
            .layer("HAAA ")
            .controller('C', PUMP_JACK).where('W', WELLHEAD).where('B', JACK_BASE).where('P', SAMSON_POST).where('K', JACK_CRANK)
            .where('H', HORSEHEAD).where('A', WALKING_BEAM)
            .build();

    public static final String FIRED_HEATER = "fired_heater", COLUMN_SECTION = "column_section", COLUMN_TOP = "column_top", VACUUM_COLUMN = "vacuum_column",
            UNIT_OUTLET = "unit_outlet", DISTILLATION = "distillation_column", PROCESS_UNIT = "process_unit";
    public static final String REFORMER = "reformer_reactor", FCC = "fcc_reactor", CRACKER = "cracking_furnace", LUBE = "lube_extractor", POLYMER = "polymer_reactor";
    public static final java.util.List<String> REACTORS = java.util.List.of(REFORMER, FCC, CRACKER, LUBE, POLYMER);

    /**
     * Crude distillation: a column of five sections and a top over the feed tray (the controller), the fired heater
     * beside its foot, an outlet at each draw-off (residue, gas oil, diesel, kerosene, naphtha, LPG, bottom to top),
     * and in the cell over the heater an optional vacuum column (with it the residue is bitumen and the gas oil is
     * drawn).
     */
    public static final Pattern DISTILLATION_COLUMN = Pattern.builder("distillation_column")
            .layer("HCO").layer("VSO").layer(" SO").layer(" SO").layer(" SO").layer(" SO").layer(" T ")
            .controller('C', DISTILLATION).where('H', FIRED_HEATER).optional('V', VACUUM_COLUMN).where('S', COLUMN_SECTION).where('O', UNIT_OUTLET)
            .where('T', COLUMN_TOP)
            .build();

    /**
     * A process unit: three reactor blocks of one kind over the controller (the reformer's, FCC's, cracking furnace's,
     * lube plant's or polymer plant's), six outlets either side.
     */
    public static final Pattern PROCESS = Pattern.builder("process_unit")
            .layer("OCO").layer("ORO").layer("ORO").layer(" R ")
            .controller('C', PROCESS_UNIT).where('O', UNIT_OUTLET).where('R', String.join("|", REACTORS))
            .build();

    /** A process unit's reactor kind (null: its reactor blocks are not all one kind). */
    public static String reactor(Pattern.Match m) {
        for (String r : REACTORS) { if (m.count(r) == 3) { return r; } }
        return null;
    }

    public static final String MOTOR_DRIVE = "motor_drive", PULLEY_DRIVE = "pulley_drive", NC_DRIVE = "nc_drive",
            DRIVES = MOTOR_DRIVE + "|" + PULLEY_DRIVE + "|" + NC_DRIVE, ELECTRIC_DRIVES = MOTOR_DRIVE + "|" + NC_DRIVE;
    public static final String LATHE_BED = "lathe_bed", MILL_TABLE = "mill_table", MILL_HEAD = "mill_head", PLANER_BED = "planer_bed",
            PLANER_UPRIGHT = "planer_upright", CROSS_RAIL = "cross_rail", BORING_TABLE = "boring_table", BORING_COLUMN = "boring_column",
            BORING_HEAD = "boring_head", DRILL_TABLE = "drill_table", DRILL_COLUMN = "drill_column", DRILL_HEAD = "drill_head", DRILL_SPINDLE = "drill_spindle";

    private static String kinds(Accessory... a) { return String.join("|", java.util.Arrays.stream(a).map(x -> x.kind).toList()); }

    /**
     * Lathe, seen from the operator: the headstock (the controller) over its drive at the left, the bed a metre a
     * segment (1 to 6) with a cell over each for a steady rest, tool-post grinder, digital readout or quick-change post,
     * the tailstock's cell over the bed's end.
     */
    public static final Pattern LATHE = Pattern.builder("lathe")
            .layer("Dbb")
            .layer("Cat")
            .controller('C', "lathe").where('D', DRIVES).where('b', LATHE_BED)
            .optional('a', kinds(Accessory.STEADY_REST, Accessory.TOOLPOST_GRINDER, Accessory.DIGITAL_READOUT, Accessory.QUICK_CHANGE_POST))
            .optional('t', Accessory.TAILSTOCK.kind)
            .repeatAcross(1, 1, 1, 6)
            .build();

    /** Milling machine: the column and knee (the controller) over its drive, the head on top, the table a metre a segment (1 to 4) with accessory cells on it. */
    public static final Pattern MILL = Pattern.builder("milling_machine")
            .layer("Db")
            .layer("Ca")
            .layer("H ")
            .controller('C', "milling_machine").where('D', DRIVES).where('b', MILL_TABLE).where('H', MILL_HEAD)
            .optional('a', kinds(Accessory.DIVIDING_HEAD, Accessory.MILL_ROTARY_TABLE, Accessory.DIGITAL_READOUT))
            .repeatAcross(1, 1, 1, 4)
            .build();

    /**
     * Planer (double housing), seen from the side of its bed: the drive at the bed's end, the housing (two uprights,
     * the front one carrying the controls) over the bed's first segment with the cross rail on top and the tool's
     * passage under it, then the bed a segment at a time (1 to 7; it takes a metre more than that). A side head may
     * take the back upright's place, a second tool head the rail's middle.
     */
    public static final Pattern PLANER = Pattern.builder("planer")
            .layer(" u ", "Dhb", " u ")
            .layer(" C ", " . ", " s ")
            .layer(" r ", " x ", " r ")
            .controller('C', "planer").where('D', DRIVES).where('u', PLANER_UPRIGHT).where('h', PLANER_BED).where('b', PLANER_BED)
            .where('s', PLANER_UPRIGHT + "|" + Accessory.SIDE_HEAD.kind).where('r', CROSS_RAIL)
            .where('x', CROSS_RAIL + "|" + Accessory.SECOND_HEAD.kind)
            .repeatAcross(2, 2, 1, 7)
            .build();

    /**
     * Horizontal boring mill: the saddle (the controller) with its drive behind, the table (or a rotary table) beside it
     * and the work's room over it, the column a metre a course (1 to 4) with a digital readout's cell behind, the head on
     * top and a facing head's cell over the work.
     */
    public static final Pattern BORING_MILL = Pattern.builder("boring_mill")
            .layer("Ct", "D ")
            .layer("K.", "a ")
            .layer("Hf", "  ")
            .controller('C', "boring_mill").where('D', DRIVES).where('K', BORING_COLUMN).where('H', BORING_HEAD)
            .where('t', BORING_TABLE + "|" + Accessory.BORING_ROTARY_TABLE.kind)
            .optional('a', Accessory.DIGITAL_READOUT.kind)
            .optional('f', Accessory.FACING_HEAD.kind)
            .repeat(1, 1, 1, 4)
            .build();

    /**
     * Drill press: the base (the controller) with its drive behind, the table (or a jig plate) and the work's room over
     * it, the column a course at a time (1 to 3; a radial drill as it grows) with a power feed's cell behind, the head
     * on top with its spindle (or a multi-spindle head) over the table.
     */
    public static final Pattern DRILL_PRESS = Pattern.builder("drill_press")
            .layer("Ct", "D ")
            .layer("K.", "a ")
            .layer("Hs", "  ")
            .controller('C', "drill_press").where('D', DRIVES).where('K', DRILL_COLUMN).where('H', DRILL_HEAD)
            .where('t', DRILL_TABLE + "|" + Accessory.JIG_PLATE.kind)
            .where('s', DRILL_SPINDLE + "|" + Accessory.MULTI_SPINDLE.kind)
            .optional('a', Accessory.POWER_FEED.kind)
            .repeat(1, 1, 1, 3)
            .build();

    // ---------------------------------------------------------------- 0.17: the machine families (Head)

    public static final String GRINDER_BED = "grinder_bed", FINISHER_TABLE = "finisher_table", FINISHER_COLUMN = "finisher_column", FINISHER_TOP = "finisher_top",
            GEAR_TABLE = "gear_table", GEAR_COLUMN = "gear_column", GEAR_TOP = "gear_top", RAM_BED = "ram_bed", SAW_BED = "saw_bed", EDM_TANK = "edm_tank",
            CUTTING_SLATS = "cutting_slats", THREADER_BASE = "threader_base", RIVETER_FRAME = "riveter_frame";

    private static String heads(Station s) { return String.join("|", Head.of(s).stream().map(h -> h.kind).toList()); }

    /** Grinder, seen from the operator: the control column (the controller) over its drive, the bed a metre a segment (1 to 4) with an accessory cell over each, the head's cell over the bed's end. */
    public static final Pattern GRINDER = Pattern.builder("grinder")
            .layer("Dbb")
            .layer("Cah")
            .controller('C', "grinder").where('D', DRIVES).where('b', GRINDER_BED).where('h', heads(Station.GRINDER))
            .optional('a', kinds(Accessory.DIAMOND_DRESSER, Accessory.MAGNETIC_CHUCK, Accessory.STEADY_REST, Accessory.DIGITAL_READOUT))
            .repeatAcross(1, 1, 1, 4)
            .build();

    /** Finisher: the base (the controller) with its drive behind, the table and the work's room over it, the column a course at a time (1 to 3) with an accessory cell behind, the top with the head's cell over the table. */
    public static final Pattern FINISHER = Pattern.builder("finisher")
            .layer("Ct", "D ")
            .layer("K.", "a ")
            .layer("Th", "  ")
            .controller('C', "finisher").where('D', DRIVES).where('t', FINISHER_TABLE).where('K', FINISHER_COLUMN).where('T', FINISHER_TOP)
            .where('h', heads(Station.FINISHER))
            .optional('a', Accessory.DIGITAL_READOUT.kind)
            .repeat(1, 1, 1, 3)
            .build();

    /** Gear cutter: as the finisher, the work table and column, an automatic loader's cell behind. */
    public static final Pattern GEAR_CUTTER = Pattern.builder("gear_cutter")
            .layer("Ct", "D ")
            .layer("K.", "a ")
            .layer("Th", "  ")
            .controller('C', "gear_cutter").where('D', DRIVES).where('t', GEAR_TABLE).where('K', GEAR_COLUMN).where('T', GEAR_TOP)
            .where('h', heads(Station.GEAR_CUTTER))
            .optional('a', Accessory.AUTO_LOADER.kind)
            .repeat(1, 1, 1, 3)
            .build();

    /** Ram machine (shaper, slotter, broach): the column (the controller) over its drive, the bed a segment of stroke at a time (1 to 3), the head's cell at its end, a dividing head's cell over each segment. */
    public static final Pattern RAM_MACHINE = Pattern.builder("ram_machine")
            .layer("Dbb")
            .layer("Cah")
            .controller('C', "ram_machine").where('D', DRIVES).where('b', RAM_BED).where('h', heads(Station.RAM_MACHINE))
            .optional('a', Accessory.DIVIDING_HEAD.kind)
            .repeatAcross(1, 1, 1, 3)
            .build();

    /** Saw: the control box (the controller) over its drive, the stock bed a metre a segment (1 to 3) with a bar feeder's or mitre vise's cell over each, the head's cell at the bed's end. */
    public static final Pattern SAWING_MACHINE = Pattern.builder("sawing_machine")
            .layer("Dbb")
            .layer("Cah")
            .controller('C', "sawing_machine").where('D', DRIVES).where('b', SAW_BED).where('h', heads(Station.SAWING_MACHINE))
            .optional('a', kinds(Accessory.BAR_FEEDER, Accessory.MITRE_VISE))
            .repeatAcross(1, 1, 1, 3)
            .build();

    /** Electrical machining: the generator (the controller) over its electric drive (a motor seat or the NC drive), the tank a segment at a time (1 to 3) with an electrode changer's cell over each, the head's cell over the tank's end. */
    public static final Pattern ELECTRO_MACHINE = Pattern.builder("electro_machine")
            .layer("Dtt")
            .layer("Cah")
            .controller('C', "electro_machine").where('D', ELECTRIC_DRIVES).where('t', EDM_TANK).where('h', heads(Station.ELECTRO_MACHINE))
            .optional('a', Accessory.ELECTRODE_CHANGER.kind)
            .repeatAcross(1, 1, 1, 3)
            .build();

    /** Cutting table: two rows of slats a metre a segment (1 to 6), the drive in front and a fume extractor's or bevel head's cell behind it, the console (the controller) over the drive and the head's cell over the table's end. */
    public static final Pattern CUTTING_TABLE = Pattern.builder("cutting_table")
            .layer("Dtt", "att")
            .layer("C h", "   ")
            .controller('C', "cutting_table").where('D', DRIVES).where('t', CUTTING_SLATS).where('h', heads(Station.CUTTING_TABLE))
            .optional('a', kinds(Accessory.FUME_EXTRACTOR, Accessory.BEVEL_HEAD))
            .repeatAcross(1, 1, 1, 6)
            .build();

    /** Threader: the controller over its drive, the base beside it with the head's cell on it, a bar feeder's cell at the end. */
    public static final Pattern THREADER = Pattern.builder("threader")
            .layer("Dba")
            .layer("Ch ")
            .controller('C', "threader").where('D', DRIVES).where('b', THREADER_BASE).where('h', heads(Station.THREADER))
            .optional('a', Accessory.BAR_FEEDER.kind)
            .build();

    /** Riveter: the controller over its drive, the frame a segment of throat at a time (1 to 3), the head's cell at its end. */
    public static final Pattern RIVETER = Pattern.builder("riveter")
            .layer("Dbb")
            .layer("C h")
            .controller('C', "riveter").where('D', DRIVES).where('b', RIVETER_FRAME).where('h', heads(Station.RIVETER))
            .repeatAcross(1, 1, 1, 3)
            .build();

    /** A manual machine tool's pattern (null for other stations). */
    public static Pattern machine(Station s) {
        return switch (s) {
            case LATHE -> LATHE; case MILLING_MACHINE -> MILL; case PLANER -> PLANER; case BORING_MILL -> BORING_MILL; case DRILL_PRESS -> DRILL_PRESS;
            case GRINDER -> GRINDER; case FINISHER -> FINISHER; case GEAR_CUTTER -> GEAR_CUTTER; case RAM_MACHINE -> RAM_MACHINE;
            case SAWING_MACHINE -> SAWING_MACHINE; case ELECTRO_MACHINE -> ELECTRO_MACHINE; case CUTTING_TABLE -> CUTTING_TABLE; case THREADER -> THREADER;
            case RIVETER -> RIVETER;
            default -> null;
        };
    }

    /**
     * A formed manual machine tool's configuration: its size (length across, or courses up), the accessories it has,
     * (0.17) its head (a family's; null when it has none or two kinds of head: not formed) and whether its drive is
     * the NC drive. Null when it is not one.
     */
    public static ToolConfig toolConfig(Station s, Pattern.Match m) {
        java.util.Set<Accessory> a = java.util.EnumSet.noneOf(Accessory.class);
        dev.mcmod.hdfv.manufacture.Head head = null;
        for (String kind : m.counts().keySet()) {
            Accessory x = Accessory.ofKind(kind);
            if (x != null && x.fits(s)) { a.add(x); }
            dev.mcmod.hdfv.manufacture.Head h = dev.mcmod.hdfv.manufacture.Head.ofKind(kind);
            if (h != null && h.station == s) {
                if (head != null && head != h) { return null; }
                head = h;
            }
        }
        if (s.family && head == null) { return null; }
        dev.mcmod.hdfv.manufacture.MachineFrame f = dev.mcmod.hdfv.manufacture.MachineFrame.of(s);
        return new ToolConfig(s, s.family ? head : null, f.size(m.length(), m.repeats()), a, numerical(m));
    }

    /** Whether a formed machine is driven by electricity: its motor seat or (0.17) its NC drive (else by its pulley, from a line shaft). */
    public static boolean electric(Pattern.Match m) { return m.has(MOTOR_DRIVE) || m.has(NC_DRIVE); }

    /** Whether a formed machine's drive is the NC drive (0.17): it runs without an operator. */
    public static boolean numerical(Pattern.Match m) { return m.has(NC_DRIVE); }


    // ---------------------------------------------------------------- 0.26: the asphalt and concrete batching plants

    public static final String ASPHALT_PLANT = "asphalt_plant", CONCRETE_PLANT = "concrete_plant";

    /**
     * 0.26: an asphalt plant, 3 by 3 and 3 high of iron blocks (its silo, frame and drum), the controller in the middle of
     * the bottom's front, a blast furnace behind it (the drum's burner), a hopper over the controller (the discharge).
     */
    public static final Pattern ASPHALT_PLANT_PATTERN = Pattern.builder(ASPHALT_PLANT)
            .layer("bCb", "bfb", "bbb")
            .layer("bhb", "bbb", "bbb")
            .layer("bbb", "bbb", "bbb")
            .controller('C', ASPHALT_PLANT).where('b', "minecraft:iron_block").where('f', "minecraft:blast_furnace").where('h', "minecraft:hopper")
            .build();

    /**
     * 0.26: a concrete batching plant, 3 by 3 and 3 high of iron blocks, the controller in the middle of the bottom's
     * front, a cauldron behind the discharge hopper (the mixer's water), the hopper over the controller.
     */
    public static final Pattern CONCRETE_PLANT_PATTERN = Pattern.builder(CONCRETE_PLANT)
            .layer("bCb", "bbb", "bbb")
            .layer("bhb", "bcb", "bbb")
            .layer("bbb", "bbb", "bbb")
            .controller('C', CONCRETE_PLANT).where('b', "minecraft:iron_block").where('c', "minecraft:cauldron|minecraft:water_cauldron").where('h', "minecraft:hopper")
            .build();


    // ---------------------------------------------------------------- 0.26: the tower crane

    public static final String TOWER_CRANE = "tower_crane", CRANE_MAST = "tower_crane_mast", CRANE_TOP = "tower_crane_top";

    /**
     * 0.26: a flat-top tower crane: a 3 by 3 base of smooth stone or concrete with the controller at the middle of its
     * front, a mast of 4 to 60 standard sections over the base's middle, the slewing unit on top (its jib, trolley and
     * counter-jib are drawn and worked by the controller: TowerCraneBlockEntity).
     */
    public static final Pattern TOWER_CRANE_PATTERN = Pattern.builder(TOWER_CRANE)
            .layer("bCb", "bbb", "bbb")
            .layer("   ", " m ", "   ")
            .layer("   ", " t ", "   ")
            .controller('C', TOWER_CRANE).where('b', "minecraft:smooth_stone|hdfv:apron_concrete|minecraft:stone").where('m', CRANE_MAST).where('t', CRANE_TOP)
            .repeat(1, 1, 4, 60)
            .build();

    /** Every pattern by name (for the dev command and tests). */
    public static final java.util.Map<String, Pattern> ALL = java.util.Map.ofEntries(java.util.Map.entry("cnc", CNC), java.util.Map.entry("storage_tank", STORAGE_TANK),
            java.util.Map.entry("drilling_rig", DRILLING_RIG), java.util.Map.entry("pump_jack", PUMP_JACK_PATTERN),
            java.util.Map.entry("distillation_column", DISTILLATION_COLUMN), java.util.Map.entry("process_unit", PROCESS),
            java.util.Map.entry("lathe", LATHE), java.util.Map.entry("milling_machine", MILL), java.util.Map.entry("planer", PLANER),
            java.util.Map.entry("boring_mill", BORING_MILL), java.util.Map.entry("drill_press", DRILL_PRESS),
            java.util.Map.entry("grinder", GRINDER), java.util.Map.entry("finisher", FINISHER), java.util.Map.entry("gear_cutter", GEAR_CUTTER),
            java.util.Map.entry("ram_machine", RAM_MACHINE), java.util.Map.entry("sawing_machine", SAWING_MACHINE),
            java.util.Map.entry("electro_machine", ELECTRO_MACHINE), java.util.Map.entry("cutting_table", CUTTING_TABLE),
            java.util.Map.entry("threader", THREADER), java.util.Map.entry("riveter", RIVETER), java.util.Map.entry(TOWER, TOWER_PATTERN),
            java.util.Map.entry(ASPHALT_PLANT, ASPHALT_PLANT_PATTERN), java.util.Map.entry(CONCRETE_PLANT, CONCRETE_PLANT_PATTERN),
            java.util.Map.entry(TOWER_CRANE, TOWER_CRANE_PATTERN));
}
