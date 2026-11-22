package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.manufacture.Cutting;
import dev.mcmod.aerocore.api.power.ElectricMachine;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HullDesign;
import dev.mcmod.hdfv.part.PlaningHullDesign;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.drive.GearboxDesign;
import dev.mcmod.hdfv.part.drive.TransferCaseDesign;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.part.engine.ShaftTurbineDesign;
import dev.mcmod.hdfv.part.engine.TurbofanDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a part design is made, and how long each step takes where (0.7).
 *
 * <ul>
 * <li>of(design): its plan (EnginePlanner, PartPlanner, StructurePlanner), cached by the design's JSON.</li>
 * <li>seconds(step, station, alloy): a cutting step's time on a machine: roughing by its power over the alloy's
 *     specific cutting energy (AeroCore Cutting.roughS, drive efficiency 0.8), finishing at its cutting speed with the
 *     feed that leaves the grade's roughness (Ra 0.2 um at IT5 doubling each grade to 12.5 um, nose radius 0.8 mm),
 *     drilling at half the speed and a feed of 1.2 % of the drill, grinding at GRIND_M2_PER_S (half on a manual
 *     machine) in three passes. Other steps take their planned time.</li>
 * <li>batchSeconds: a station's setup plus every piece of a batch.</li>
 * <li>job (0.8): a run of steps on a station: on a CNC its setups (CncConfig.setups, Setups) prepared once a batch
 *     and clamped each piece, the cutting shared by its turrets and sub-spindle; and the electricity: the spindle's
 *     input (ElectricMachine) at full power while roughing and LIGHT_LOAD of it while finishing (60 % grinding, 40 %
 *     drilling), the forming steps' heat (Shop), and the station's base load all the while.</li>
 * </ul>
 */
public final class Plans {
    private Plans() {}

    public static final double DRIVE_EFFICIENCY = 0.8, NOSE_RADIUS = 0.8e-3, GRIND_M2_PER_S = 2e-4, LIGHT_LOAD = 0.3;

    private static final Map<String, PartPlan> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, PartPlan> e) { return size() > 64; }
    };

    /** The plan of a design (cached by its JSON). */
    public static PartPlan of(PartDesign d) {
        String key = Designs.write(d);
        synchronized (CACHE) {
            PartPlan p = CACHE.get(key);
            if (p != null) { return p; }
        }
        PartPlan p = plan(d);
        synchronized (CACHE) { CACHE.put(key, p); }
        return p;
    }

    static PartPlan plan(PartDesign d) {
        List<Workpiece> pieces = new ArrayList<>();
        List<StructureWork> works = new ArrayList<>();
        Station at = Station.ASSEMBLY_BENCH;
        switch (d) {
            case PistonEngineDesign e -> pieces.addAll(EnginePlanner.piston(e));
            case JetEngineDesign e -> pieces.addAll(EnginePlanner.jet(e));
            case TurbofanDesign e -> pieces.addAll(EnginePlanner.turbofan(e));
            case ShaftTurbineDesign e -> pieces.addAll(EnginePlanner.shaftTurbine(e));
            case dev.mcmod.hdfv.part.engine.RamjetDesign e -> pieces.addAll(EnginePlanner.ramjet(e));
            case dev.mcmod.hdfv.part.engine.ScramjetDesign e -> pieces.addAll(EnginePlanner.scramjet(e));
            case dev.mcmod.hdfv.part.engine.PulsejetDesign e -> pieces.addAll(EnginePlanner.pulsejet(e));
            case dev.mcmod.hdfv.part.engine.SolidRocketDesign e -> pieces.addAll(EnginePlanner.solidRocket(e));
            case dev.mcmod.hdfv.part.engine.TurboRamjetDesign e -> pieces.addAll(EnginePlanner.turboRamjet(e));
            case AxleDesign a -> pieces.addAll(PartPlanner.axle(a));
            case LandingGearDesign l -> pieces.addAll(PartPlanner.landingGear(l));
            case GearboxDesign g -> pieces.addAll(PartPlanner.gearbox(g));
            case TransferCaseDesign t -> pieces.addAll(PartPlanner.transferCase(t));
            case AirPropellerDesign a -> pieces.addAll(PartPlanner.airPropeller(a));
            case RotorDesign r -> pieces.addAll(PartPlanner.rotor(r));
            case BodyDesign b -> { StructurePlanner.body(b, pieces, works); at = Station.ASSEMBLY_ROOM; }
            case dev.mcmod.hdfv.part.chassis.ChassisDesign c -> { StructurePlanner.chassis(c, pieces, works); at = Station.ASSEMBLY_ROOM; }
            case WingDesign w -> { StructurePlanner.wing(w, pieces, works); at = Station.ASSEMBLY_ROOM; }
            case PlaningHullDesign h -> { StructurePlanner.planingHull(h, pieces, works); at = Station.ASSEMBLY_ROOM; }
            case HullDesign h -> { StructurePlanner.displacementHull(h, pieces, works); at = Station.ASSEMBLY_ROOM; }
            case dev.mcmod.hdfv.part.component.ComponentDesign c -> pieces.addAll(ComponentPlanner.plan(c.kind()));
            // 0.25: a container is built in the room as a body is; a roof box or a bed box at the bench (PartPlanner)
            case dev.mcmod.hdfv.part.StorageBoxDesign s when s.kind() == dev.mcmod.hdfv.part.StorageBoxDesign.Kind.CONTAINER -> {
                StructurePlanner.container(s, pieces, works);
                at = Station.ASSEMBLY_ROOM;
            }
            // 0.26: the heavy machinery's parts (MachinePlanner)
            default -> pieces.addAll(dev.mcmod.hdfv.assembly.Room.machinePart(d) ? MachinePlanner.of(d) : PartPlanner.simple(d));
        }
        double kg = 0;
        int count = 0;
        for (Workpiece w : pieces) { kg += w.count() * w.finishedKg(); count += w.count(); }
        // fitting it together: 5 min, a minute a piece, 3 s a kilogram (a rule: an inline four's 70-odd pieces in about 1.5 h)
        double assemble = 300 + 60 * count + 3 * kg;
        return new PartPlan(d.type(), pieces, at, assemble, works);
    }

    /** The roughness a grade's finish leaves, m: 0.2 um at IT5, doubling each grade, at most 12.5 um. */
    public static double roughness(int grade) { return Math.min(0.2e-6 * Math.pow(2, grade - 5), 12.5e-6); }

    /** Time of one step on a station, for one piece, s (infinite where the station cannot do it). A family's and 0.17's new processes need the workpiece (the other seconds). */
    public static double seconds(Operation op, Station s, Alloy a) {
        if (!op.process().cutting()) { return s.does(op.process()) ? op.fixedS() : Double.POSITIVE_INFINITY; }
        if (s.family || !s.can(op)) { return Double.POSITIVE_INFINITY; }
        double x = cuttingSeconds(op, s.powerW, s.speedFactor, s.manual, a);
        return Double.isNaN(x) ? Double.POSITIVE_INFINITY : x;
    }

    /**
     * Time of one step of a workpiece on a station, for one piece, s (infinite where it cannot): 0.16's machines as
     * before (and the new processes they take, Machining.stationSeconds); a family by its fastest head that can (as
     * the largest of its kind, every accessory, an NC drive where it has one).
     */
    public static double seconds(Operation op, Station s, Workpiece w) {
        if (s.family) {
            double best = Double.POSITIVE_INFINITY;
            for (Head h : Head.of(s)) {
                ToolConfig t = ToolConfig.full(h, false);
                if (t.can(op, w)) { best = Math.min(best, Machining.seconds(op, h, w) * t.timeFactor(op)); }
            }
            return best;
        }
        if (!op.process().cutting()) {
            if (!s.does(op.process())) { return Double.POSITIVE_INFINITY; }
            return op.fixedS();
        }
        if (!s.can(op)) { return Double.POSITIVE_INFINITY; }
        return cutSeconds(op, s, w);
    }

    /** A cutting step's time on one of 0.16's stations (whether it can is the caller's: a tool's accessories may let it), s. */
    static double cutSeconds(Operation op, Station s, Workpiece w) {
        double x = Machining.stationSeconds(op, s, w);
        return Double.isNaN(x) ? cuttingSeconds(op, s.powerW, s.speedFactor, s.manual, w.alloy()) : x;
    }

    /** Time of a cutting step at a machine's power (W), speed factor and handling, for one piece, s (whether it can is the caller's). */
    static double cuttingSeconds(Operation op, double powerW, double speedFactor, boolean manual, Alloy a) {
        double v = a.carbideSpeed * speedFactor;
        return switch (op.process()) {
            case DRILL -> Cutting.drillS(op.holes(), op.holeDiameterM(), op.holeDepthM(), 0.5 * v, Math.max(0.012 * op.holeDiameterM(), 2e-5), Math.toRadians(118));
            case GRIND -> op.finishedM2() / (GRIND_M2_PER_S * (manual ? 0.5 : 1)) * 3 + Cutting.roughS(op.removedM3(), powerW, DRIVE_EFFICIENCY, a.specificEnergy * 5, 0);
            default -> Cutting.roughS(op.removedM3(), powerW, DRIVE_EFFICIENCY, a.specificEnergy, 0)
                    + (op.finishedM2() > 0 ? Cutting.finishS(op.finishedM2(), v, Cutting.feedFor(roughness(op.grade()), NOSE_RADIUS), 1) : 0);
        };
    }

    /** Setup and every piece of a batch of a workpiece's step on a station (a family's fastest head), s. */
    public static double batchSeconds(Workpiece w, Operation op, Station s) {
        if (s.family) {
            double best = Double.POSITIVE_INFINITY;
            for (Head h : Head.of(s)) {
                ToolConfig t = ToolConfig.full(h, false);
                if (t.can(op, w)) { best = Math.min(best, h.setupS + w.count() * Machining.seconds(op, h, w) * t.timeFactor(op)); }
            }
            return best;
        }
        double one = seconds(op, s, w);
        return Double.isInfinite(one) ? one : s.setupS + w.count() * one;
    }

    /** The station that does a step soonest for a batch, manual machines only when asked (null: none can). */
    public static Station fastest(Workpiece w, Operation op, boolean manualOnly) {
        Station best = null;
        double t = Double.POSITIVE_INFINITY;
        for (Station s : Station.values()) {
            if (manualOnly && s.nc) { continue; }
            double x = batchSeconds(w, op, s);
            if (x < t) { t = x; best = s; }
        }
        return best;
    }

    /**
     * A run of steps from..from+n of a workpiece on a station: its clampings (CNC; 1 elsewhere), the batch's
     * preparation and each piece's time (s) and electricity (J of working time). Infinite time when the station
     * (a CNC as configured) cannot do a step.
     */
    public record Job(int setups, double prepareS, double pieceS, double prepareJ, double pieceJ) {
        /** Average electrical power over a piece, W. */
        public double pieceW() { return pieceS > 0 && Double.isFinite(pieceS) ? pieceJ / pieceS : 0; }
        public boolean possible() { return Double.isFinite(pieceS); }
    }

    /** The job of steps from..from+n on a station (a CNC as configured; null: the 12-axis mill-turn). */
    public static Job job(Workpiece w, int from, int n, Station s, CncConfig c) {
        CncConfig cnc = c == null ? CncConfig.MILL_TURN_12 : c;
        double cut = 0, energy = 0;
        for (int i = from; i < from + n && i < w.route().size(); i++) {
            Operation op = w.route().get(i);
            double t = s == Station.CNC && op.process().cutting() && !cnc.can(op) ? Double.POSITIVE_INFINITY : seconds(op, s, w);
            if (Double.isInfinite(t)) { return new Job(0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0); }
            cut += t;
            energy += operationJ(w, op, s, cnc, null, t);
        }
        if (s == Station.CNC) {
            int setups = Setups.count(w, from, n, cnc);
            double piece = cut / cnc.parallel() + setups * Setups.clampS(w.stockKg()), prepare = setups * Setups.PREPARE_S;
            return new Job(setups, prepare, piece, prepare * cnc.baseW(), energy + piece * cnc.baseW());
        }
        return new Job(1, s.setupS, cut, s.setupS * s.baseW(), energy + cut * s.baseW());
    }

    /**
     * A run of steps from..from+n of a workpiece on a manual machine tool as built (0.9): its clampings (one a face,
     * fewer with a rotary table or side head), the batch's preparation (less with a quick-change tool post), each
     * piece's cutting (as its accessories speed it) and clampings, and electricity. Infinite when the stock does not
     * fit or it cannot do a step (its process, the grade, a complex shape, an accessory it lacks).
     * 0.17: a family's machine by its head (Machining), its clampings as its head and accessories make them; an NC
     * drive adds its base load.
     */
    public static Job jobOn(Workpiece w, int from, int n, ToolConfig t) {
        Station s = t.station();
        Job impossible = new Job(0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0);
        if (!t.fits(w)) { return impossible; }
        double cut = 0, energy = 0;
        for (int i = from; i < from + n && i < w.route().size(); i++) {
            Operation op = w.route().get(i);
            double sec;
            if (t.head() != null) {
                if (!t.can(op, w)) { return impossible; }
                sec = Machining.seconds(op, t.head(), w) * t.timeFactor(op);
            } else if (op.process().cutting()) {
                if (!t.can(op, w)) { return impossible; }
                sec = cutSeconds(op, s, w) * t.timeFactor(op);
            } else {
                sec = seconds(op, s, w);
            }
            if (!(sec >= 0) || Double.isInfinite(sec)) { return impossible; }
            cut += sec;
            energy += operationJ(w, op, s, null, t.head(), sec);
        }
        int setups = t.setups(Setups.faces(w.route(), from, n));
        double piece = cut + setups * Setups.clampS(w.stockKg()) * t.clampFactor(), prepare = t.setupS() * t.prepareFactor();
        return new Job(setups, prepare, piece, prepare * t.baseW(), energy + piece * t.baseW());
    }

    /**
     * The electricity of one step on one piece taking a time (s), besides the base load, J. A head's (0.17): a motor-driven
     * head's spindle as 0.16's machines (full power roughing, a share finishing); an EDM generator, a laser, a plasma
     * torch and an ECM rectifier at HEAD_DUTY of their power while they work, a waterjet's pump at full.
     */
    static double operationJ(Workpiece w, Operation op, Station s, CncConfig c, Head h, double seconds) {
        Alloy a = w.alloy();
        if (!op.process().cutting()) {
            return switch (op.process()) {
                case CAST -> Shop.meltJ(a, w.stockKg());
                case FORGE -> Shop.heatJ(a, w.stockKg()) + CncConfig.input(Shop.PRESS_W) * Math.min(seconds, 60 * (1 + w.stockKg() / 20));
                case FORM -> Shop.FORM_J_PER_KG * w.stockKg();
                case CURE -> Shop.OVEN_W * Shop.OVEN_DUTY * seconds;
                case VULCANIZE -> Shop.VULCANIZER_W * Shop.VULCANIZER_DUTY * seconds;
                case WELD -> Shop.ARC_W * Shop.HAND_FACTOR * seconds;
                case RIVET -> h != null ? CncConfig.input(h.powerW) * 0.3 * seconds : 0;
                default -> 0;
            };
        }
        if (h != null && h.electricOnly) { return h.powerW * HEAD_DUTY * seconds; }
        if (h == Head.WATERJET) { return CncConfig.input(h.powerW) * seconds; }
        double spindle = h != null ? h.powerW : s == Station.CNC ? c.spindleW() : s.powerW;
        if (!(spindle > 0)) { return 0; }   // the welding station cuts with oxy-fuel: no electricity
        ElectricMachine m = ElectricMachine.of(spindle);
        if (op.process() == Process.DRILL || op.process() == Process.TAP) { return seconds * m.inputForOutput(0.4 * spindle); }
        boolean grinding = op.process() == Process.GRIND || h != null && h.grinding();
        double rough = Math.min(seconds, Cutting.roughS(op.removedM3(), spindle, DRIVE_EFFICIENCY, a.specificEnergy * (grinding ? 5 : 1), 0));
        double light = grinding ? 0.6 : LIGHT_LOAD;
        return rough * m.inputForOutput(spindle) + (seconds - rough) * m.inputForOutput(light * spindle);
    }

    /** Share of its power an EDM generator, laser, plasma torch or ECM rectifier draws while it works. */
    public static final double HEAD_DUTY = 0.8;

    /** Working hours to make a plan's components (each step at its fastest station, manual machines when they can) and fit them together. */
    public static double hours(PartPlan p) {
        double s = p.assembleS();
        for (Workpiece w : p.workpieces()) {
            for (Operation op : w.route()) {
                Station st = fastest(w, op, true);
                if (st == null) { st = fastest(w, op, false); }
                if (st != null) { s += batchSeconds(w, op, st); }
            }
        }
        for (StructureWork sw : p.structure()) { s += sw.laborS(); }
        return s / 3600;
    }
}
