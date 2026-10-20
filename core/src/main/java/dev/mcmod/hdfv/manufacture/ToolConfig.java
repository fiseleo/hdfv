package dev.mcmod.hdfv.manufacture;

import java.util.EnumSet;
import java.util.Set;

/**
 * A manual machine tool as built (0.9; the user asked for the lathe, mill, planer, boring mill and drill press as
 * multiblocks whose size limits the work, with accessories that change what they do and how long it takes): its
 * station, its size (bed or table segments, or column courses) and its accessories.
 *
 * <ul>
 * <li>Capacity (capacity, fits): a lathe turns between centres a metre a bed segment and swings 0.5 m; a knee mill's
 *     table travels a metre a segment over a 0.5 by 0.5 m envelope; a double-housing planer takes a metre a bed segment
 *     beyond the first, 1 m between its uprights, 0.9 m under its rail; a horizontal boring mill's work stands a metre a
 *     column course high on a 1.5 m table; a drill press (a radial drill as it grows) takes 0.6 m a column course high,
 *     reaching 0.6 + 0.3 m a course. A turned part goes on a lathe by its length and swing; anything else by its sizes,
 *     largest first, against the machine's.</li>
 * <li>What it needs (lacks): the lathe grinds only with a tool-post grinder, turns a part more than 4 diameters long
 *     only on centres (the tailstock) and more than 12 only with a steady rest too (a slender part springs from the
 *     tool); the mill cuts round a part (splines, gear teeth, flutes) only on a dividing head; the boring mill faces and
 *     bores over 0.25 m only with a facing head.</li>
 * <li>Grades: a digital readout (lathe, mill, boring mill) holds one IT grade finer; a jig plate drills one finer.</li>
 * <li>Setups (setups): one a face the steps are cut from (Operation faces); the lathe turns round and on its front
 *     end in one chucking, its back end needs another; a rotary table (mill, boring mill) or a side head (planer) takes
 *     the four sides in the setup of the first. Each costs a clamping a piece (Setups.clampS).</li>
 * <li>Time (timeFactor): a digital readout saves 15 % (no measuring between passes), a quick-change tool post 40 % of
 *     the setting up, a second tool head on a planer 40 % of the planing, a multi-spindle head drills three holes at a
 *     time, a power feed 30 % of the drilling, a jig plate 15 % (no marking out). Rules of thumb from shop practice.</li>
 * </ul>
 *
 * 0.17 (all the machining machines): a family's machine also has its head (Head: what it does, its power, capacity
 * and setting up), and any manual tool may be driven by an NC drive (nc): it then runs without an operator and holds
 * one grade finer (as a readout does; the two do not add up), saving 15 % of the cutting as a readout does (not twice).
 * The families' accessories and rules:
 * <ul>
 * <li>Grinder: a steady rest for work more than 12 diameters long between centres; a magnetic chuck to surface-grind
 *     iron and steel (else it is clamped in a vice: it needs the chuck); a diamond dresser saves 10 % of the grinding
 *     and halves the wheel's dressing; a digital readout.</li>
 * <li>Gear cutter: an automatic loader halves the clamping. Ram machine: the slotter cuts splines only on a dividing
 *     head. Saw: tube is mitred only in a mitre vise; a bar feeder (saw, threader) cuts the clamping to 0.3 and saves
 *     30 % of the cutting. Electrical machines: an electrode changer does every face in one setup. Cutting table: the
 *     laser and plasma cut only with fume extraction; a bevel head cuts angled edges and holes (five axes).</li>
 * <li>Setups: the saw, cutting table, threader and riveter one; the others one a face (round counting once). The
 *     centreless grinder hardly clamps (0.05 of a clamping: the work feeds through).</li>
 * </ul>
 */
public record ToolConfig(Station station, Head head, int size, Set<Accessory> accessories, boolean nc) {
    public enum Accessory {
        TAILSTOCK("lathe_tailstock", Station.LATHE), STEADY_REST("steady_rest", Station.LATHE, Station.GRINDER),
        TOOLPOST_GRINDER("toolpost_grinder", Station.LATHE), QUICK_CHANGE_POST("quick_change_post", Station.LATHE),
        DIVIDING_HEAD("dividing_head", Station.MILLING_MACHINE, Station.RAM_MACHINE), MILL_ROTARY_TABLE("mill_rotary_table", Station.MILLING_MACHINE),
        SECOND_HEAD("second_tool_head", Station.PLANER), SIDE_HEAD("side_head", Station.PLANER),
        BORING_ROTARY_TABLE("boring_rotary_table", Station.BORING_MILL), FACING_HEAD("facing_head", Station.BORING_MILL),
        MULTI_SPINDLE("multi_spindle_head", Station.DRILL_PRESS), POWER_FEED("power_feed", Station.DRILL_PRESS), JIG_PLATE("jig_plate", Station.DRILL_PRESS),
        DIGITAL_READOUT("digital_readout", Station.LATHE, Station.MILLING_MACHINE, Station.BORING_MILL, Station.GRINDER, Station.FINISHER),
        // 0.17
        DIAMOND_DRESSER("diamond_dresser", Station.GRINDER), MAGNETIC_CHUCK("magnetic_chuck", Station.GRINDER),
        AUTO_LOADER("auto_loader", Station.GEAR_CUTTER), BAR_FEEDER("bar_feeder", Station.SAWING_MACHINE, Station.THREADER),
        MITRE_VISE("mitre_vise", Station.SAWING_MACHINE), ELECTRODE_CHANGER("electrode_changer", Station.ELECTRO_MACHINE),
        FUME_EXTRACTOR("fume_extractor", Station.CUTTING_TABLE), BEVEL_HEAD("bevel_head", Station.CUTTING_TABLE);

        private final Set<Station> stations;
        /** Its block's kind (the pattern's name). */
        public final String kind;

        Accessory(String kind, Station... stations) { this.kind = kind; this.stations = EnumSet.copyOf(java.util.List.of(stations)); }

        /** Whether it goes on a station. */
        public boolean fits(Station s) { return stations.contains(s); }

        /** The accessory a block kind is (null: none). */
        public static Accessory ofKind(String kind) {
            for (Accessory a : values()) { if (a.kind.equals(kind)) { return a; } }
            return null;
        }
    }

    /** The manual machine tools and (0.17) the families. */
    public static final Set<Station> STATIONS = EnumSet.of(Station.LATHE, Station.MILLING_MACHINE, Station.PLANER, Station.BORING_MILL, Station.DRILL_PRESS,
            Station.GRINDER, Station.FINISHER, Station.GEAR_CUTTER, Station.RAM_MACHINE, Station.SAWING_MACHINE, Station.ELECTRO_MACHINE,
            Station.CUTTING_TABLE, Station.THREADER, Station.RIVETER);

    /** A lathe's swing, m; a part longer than these many diameters needs the tailstock, the steady rest; a bore that needs the facing head, m. */
    public static final double SWING_M = 0.5, TAILSTOCK_RATIO = 4, STEADY_RATIO = 12, FACING_HEAD_M = 0.25;
    /** An NC drive's base load (the control, servo amplifiers, its cabinet's cooling), W. */
    public static final double NC_W = 1e3;

    public ToolConfig {
        if (!STATIONS.contains(station)) { throw new IllegalArgumentException("Not a manual machine tool: " + station); }
        if (station.family != (head != null) || head != null && head.station != station) { throw new IllegalArgumentException(station + " with head " + head); }
        if (size < minSize(station) || size > maxSize(station)) { throw new IllegalArgumentException(station + " size " + size); }
        accessories = accessories.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(accessories));
        for (Accessory a : accessories) { if (!a.fits(station)) { throw new IllegalArgumentException(a + " does not go on a " + station); } }
    }

    /** A 0.9 machine tool, driven by hand. */
    public ToolConfig(Station station, int size, Set<Accessory> accessories) { this(station, null, size, accessories, false); }

    public static int minSize(Station s) { MachineFrame f = MachineFrame.of(s); return f == null ? 1 : f.minSize; }

    public static int maxSize(Station s) { MachineFrame f = MachineFrame.of(s); return f == null ? 1 : f.maxSize; }

    /** The biggest of a 0.9 station (a family: with its first head) with every accessory that goes on it, by hand. */
    public static ToolConfig full(Station s) { return s.family ? full(Head.of(s).get(0), false) : new ToolConfig(s, null, maxSize(s), all(s), false); }

    /** The biggest machine of a head's family with every accessory, by hand or NC. */
    public static ToolConfig full(Head h, boolean nc) { return new ToolConfig(h.station, h, maxSize(h.station), all(h.station), nc); }

    private static Set<Accessory> all(Station s) {
        Set<Accessory> all = EnumSet.noneOf(Accessory.class);
        for (Accessory a : Accessory.values()) { if (a.fits(s)) { all.add(a); } }
        return all;
    }

    public boolean has(Accessory a) { return accessories.contains(a); }

    /** Whether it needs an operator: a manual station without an NC drive. */
    public boolean manual() { return station.manual && !nc; }

    /** Its spindle (generator, beam, torch, pump) power, W. */
    public double powerW() { return head != null ? head.powerW : station.powerW; }

    /** Its cutting speed over carbide on a rigid machine. */
    public double speedFactor() { return head != null ? (head == Head.SHAPER || head == Head.SLOTTER ? 0.3 : 0.8) : station.speedFactor; }

    /** Setting up a batch, s. */
    public double setupS() { return head != null ? head.setupS : station.setupS; }

    /** Base load while working, W (the NC drive's too). */
    public double baseW() { return (head != null ? head.baseW : station.baseW()) + (nc ? NC_W : 0); }

    /** The largest stock it takes, m: length, width, height (a lathe's: between centres and its swing). */
    public Workpiece.Envelope capacity() {
        if (head != null) { return head.capacity(size); }
        return switch (station) {
            case LATHE -> Workpiece.Envelope.turned(size, SWING_M);
            case MILLING_MACHINE -> Workpiece.Envelope.box(size, 0.5, 0.5);
            case PLANER -> Workpiece.Envelope.box(size + 1, 1.0, 0.9);
            case BORING_MILL -> Workpiece.Envelope.box(1.5, 1.5, size);
            case DRILL_PRESS -> Workpiece.Envelope.box(0.6 + 0.3 * size, 0.6 + 0.3 * size, 0.6 * size);
            default -> throw new IllegalStateException();
        };
    }

    /** Whether a workpiece's stock goes on it. */
    public boolean fits(Workpiece w) {
        if (head != null) { return head.fits(w, size); }
        Workpiece.Envelope e = w.envelope(), c = capacity();
        if (station == Station.LATHE) {
            if (e.turned()) { return e.lengthM() <= c.lengthM() + 1e-9 && e.diameterM() <= SWING_M + 1e-9; }
            double[] s = e.sorted();
            return s[0] <= c.lengthM() + 1e-9 && Math.hypot(s[1], s[2]) <= SWING_M + 1e-9;   // on the faceplate it swings its diagonal
        }
        double[] s = e.sorted(), m = c.sorted();
        return s[0] <= m[0] + 1e-9 && s[1] <= m[1] + 1e-9 && s[2] <= m[2] + 1e-9;
    }

    /** Whether it holds a grade finer than its station's: an NC drive or (on the processes it measures) a digital readout; a jig plate on a drill press. */
    private boolean finer(Process p) {
        if (station == Station.DRILL_PRESS && p == Process.DRILL) { return has(Accessory.JIG_PLATE) || nc; }
        if (nc) { return true; }
        if (!has(Accessory.DIGITAL_READOUT)) { return false; }
        return head != null ? p == Process.GRIND || p == Process.LAP : p == Process.TURN || p == Process.MILL || p == Process.BORE;
    }

    /** The finest grade it holds in a process (a family's: on any feature). */
    public int bestGrade(Process p) {
        int g = Integer.MAX_VALUE;
        if (head != null) { for (Feature f : Feature.values()) { g = Math.min(g, head.bestGrade(p, f)); } }
        else { g = station.bestGrade(p); }
        if (g == Integer.MAX_VALUE) { return g; }
        return finer(p) && !station.nc ? g - 1 : g;
    }

    /** The accessory a step of a workpiece needs and it lacks (null: none). */
    public Accessory lacks(Operation op, Workpiece w) {
        Process p = op.process();
        if (head != null) {
            Workpiece.Envelope e = w.envelope();
            double slender = e.turned() ? e.lengthM() / e.diameterM() : 0;
            Alloy.Family fam = w.alloy().family;
            return switch (head) {
                case CYLINDRICAL_GRINDING -> slender > STEADY_RATIO && !has(Accessory.STEADY_REST) ? Accessory.STEADY_REST : null;
                case SURFACE_GRINDING -> (fam == Alloy.Family.IRON || fam == Alloy.Family.STEEL) && !has(Accessory.MAGNETIC_CHUCK) ? Accessory.MAGNETIC_CHUCK : null;
                case SLOTTER -> op.feature() == Feature.SPLINE && !has(Accessory.DIVIDING_HEAD) ? Accessory.DIVIDING_HEAD : null;
                case BAND_SAW, COLD_SAW, HACKSAW -> w.form() == Form.TUBE && !has(Accessory.MITRE_VISE) ? Accessory.MITRE_VISE : null;
                case LASER, PLASMA -> !has(Accessory.FUME_EXTRACTOR) ? Accessory.FUME_EXTRACTOR : op.complex() && !has(Accessory.BEVEL_HEAD) ? Accessory.BEVEL_HEAD : null;
                case WATERJET -> op.complex() && !has(Accessory.BEVEL_HEAD) ? Accessory.BEVEL_HEAD : null;
                default -> null;
            };
        }
        return switch (station) {
            case LATHE -> {
                if (p == Process.GRIND && !has(Accessory.TOOLPOST_GRINDER)) { yield Accessory.TOOLPOST_GRINDER; }
                Workpiece.Envelope e = w.envelope();
                double slender = e.turned() ? e.lengthM() / e.diameterM() : 0;
                if ((p == Process.TURN || p == Process.GRIND) && slender > TAILSTOCK_RATIO && !has(Accessory.TAILSTOCK)) { yield Accessory.TAILSTOCK; }
                if ((p == Process.TURN || p == Process.GRIND) && slender > STEADY_RATIO && !has(Accessory.STEADY_REST)) { yield Accessory.STEADY_REST; }
                yield null;
            }
            case MILLING_MACHINE -> (op.faces() & Operation.AROUND) != 0 && p == Process.MILL && !has(Accessory.DIVIDING_HEAD) ? Accessory.DIVIDING_HEAD : null;
            case BORING_MILL -> p == Process.BORE && op.sizeM() > FACING_HEAD_M && !has(Accessory.FACING_HEAD) ? Accessory.FACING_HEAD : null;
            default -> null;
        };
    }

    /** Whether it can do a step of a workpiece: its process to the grade, its shape (a family's head), no complex shape on a 0.9 tool, nothing lacking. */
    public boolean can(Operation op, Workpiece w) {
        if (head != null) {
            // a bevel head tilts the cutting head: angled edges and holes become plain cuts for the table
            Operation check = op.complex() && has(Accessory.BEVEL_HEAD) ? op.withAxes(Operation.PLAIN_AXES) : op;
            return head.can(check, w, finer(op.process()) && !station.nc, nc || station.nc) && lacks(op, w) == null;
        }
        return station.does(op.process()) && bestGrade(op.process()) <= op.grade() && !op.complex() && lacks(op, w) == null;
    }

    private static final int SIDES = Operation.FRONT | Operation.BACK | Operation.LEFT | Operation.RIGHT;

    /** Clampings for steps cut from these faces (Operation bits), at least one. */
    public int setups(int faces) {
        if (head != null) {
            return switch (station) {
                case SAWING_MACHINE, CUTTING_TABLE, THREADER, RIVETER -> 1;
                case ELECTRO_MACHINE -> has(Accessory.ELECTRODE_CHANGER) ? 1 : Math.max(1, Integer.bitCount(faces & Operation.BOX) + ((faces & Operation.AROUND) != 0 ? 1 : 0));
                default -> Math.max(1, Integer.bitCount(faces & Operation.BOX) + ((faces & Operation.AROUND) != 0 ? 1 : 0)
                        - ((faces & Operation.AROUND) != 0 && (faces & (Operation.FRONT | Operation.BACK)) != 0 ? 1 : 0));
            };
        }
        if (station == Station.LATHE) {
            boolean front = (faces & (Operation.FRONT | Operation.AROUND)) != 0, back = (faces & Operation.BACK) != 0;
            int others = Integer.bitCount(faces & (Operation.TOP | Operation.BOTTOM | Operation.LEFT | Operation.RIGHT));
            return Math.max(1, (front ? 1 : 0) + (back ? 1 : 0) + others);
        }
        int n = Integer.bitCount(faces & (Operation.TOP | Operation.BOTTOM));
        int sides = Integer.bitCount(faces & SIDES);
        boolean together = has(Accessory.MILL_ROTARY_TABLE) || has(Accessory.BORING_ROTARY_TABLE) || has(Accessory.SIDE_HEAD);
        n += together ? Math.min(sides, 1) : sides;
        if ((faces & Operation.AROUND) != 0) { n += 1; }
        if (together && has(Accessory.SIDE_HEAD) && (faces & Operation.TOP) != 0 && sides > 0) { n -= 1; }   // the side head works in the top's setup
        return Math.max(1, n);
    }

    /** Its share of a clamping a piece: the centreless grinder feeds through, a loader or bar feeder loads it. */
    public double clampFactor() {
        if (head == Head.CENTRELESS_GRINDING) { return 0.05; }
        if (has(Accessory.BAR_FEEDER)) { return 0.3; }
        return has(Accessory.AUTO_LOADER) ? 0.5 : 1;
    }

    /** Its share of a step's time (accessories that save some; an NC drive as a readout, not both). */
    public double timeFactor(Operation op) {
        double f = 1;
        Process p = op.process();
        boolean measured = head != null ? p == Process.GRIND || p == Process.LAP : p == Process.TURN || p == Process.MILL || p == Process.BORE;
        if (nc || has(Accessory.DIGITAL_READOUT) && measured) { f *= 0.85; }
        if (has(Accessory.SECOND_HEAD) && p == Process.PLANE) { f *= 0.6; }
        if (p == Process.DRILL && station == Station.DRILL_PRESS) {
            if (has(Accessory.MULTI_SPINDLE) && op.holes() >= 3) { f /= 3; }
            if (has(Accessory.POWER_FEED)) { f *= 0.7; }
            if (has(Accessory.JIG_PLATE)) { f *= 0.85; }
        }
        if (has(Accessory.DIAMOND_DRESSER) && p == Process.GRIND) { f *= 0.9; }
        if (has(Accessory.BAR_FEEDER) && (p == Process.SAW || p == Process.THREAD)) { f *= 0.7; }
        return f;
    }

    /** Its share of the batch's setting up. */
    public double prepareFactor() { return has(Accessory.QUICK_CHANGE_POST) ? 0.6 : 1; }
}
