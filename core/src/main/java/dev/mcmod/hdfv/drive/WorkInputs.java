package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.26: a machine operator's keys turned into the work channels, one client tick at a time. A machine has two modes:
 * drive (the car keys, and the travel motors of a hydrostatic crawler from the movement keys) and work, toggled by the
 * work-mode key. In work mode the movement keys are the left joystick and the arrow keys the right one; what each moves
 * depends on the machine (its bound work channels, {@link Kit}):
 * <ul>
 * <li>A machine that slews and has a stick (an excavator, a backhoe's hoe): ISO 10968's pattern by default, left stick
 *     forward and back the stick (out and in) and sideways the swing, right stick forward and back the boom (down and
 *     up) and sideways the bucket (curl in to the left, dump to the right); the SAE pattern (the player's choice) swaps
 *     the boom and the stick.</li>
 * <li>A crane (it slews and has a hoist, no stick): left stick sideways the swing, forward and back the telescope (out
 *     and in); right stick forward and back the hoist (down and up), sideways the boom (up to the left). A rule after
 *     the usual two-lever cab of mobile cranes.</li>
 * <li>A machine that slews with neither (a work platform's turntable): as a crane, the right stick's forward and back
 *     the boom.</li>
 * <li>Every other machine (loaders, dozers, graders, telehandlers, forklifts, dump bodies): the left stick still
 *     drives (the car keys); the right stick forward and back the first lifting function it has (boom, blade lift,
 *     mast, dump body, hoist, ripper: forward lowers), sideways the first tilting one (bucket, blade tilt, mast tilt,
 *     the attachment: curl back to the left).</li>
 * </ul>
 * The functions left over are picked with the select key and moved with the up and down keys (a toggle: up on, down
 * off). The float key floats the blade (its rams give nothing either way). A held key opens its valve to OPEN_START at
 * once and further at OPEN_RATE a second (the longer it is held the faster the function moves), let go it closes (a
 * spring-centred joystick). The engine idles by itself when no function nor travel has moved for AUTO_IDLE_S (an
 * excavator's auto-idle) on a machine whose engine the keys do not hold (it slews or travels by motors); otherwise
 * "throttle" stays the movement keys'.
 * <p>
 * Channel signs (AutoWiring binds each actuator so): positive raises a boom, a blade, a ripper, a mast, a dump body or
 * the hook, pulls the stick in, curls a bucket in, slews left (counter-clockwise from above), extends a telescope or
 * the outriggers, tilts a blade's left end up, angles its left end forward, tilts a mast back, closes a grapple.
 * "lift" is a second machine's lifting arm (a backhoe loader's loader arm beside its hoe's boom); "jacks" (a toggle)
 * puts the outriggers' jacks down; "hoist_brake" is 1 while the hoist is centred (the winch's spring brake).
 */
public final class WorkInputs {
    public static final double TICK = 0.05, OPEN_START = 0.15, OPEN_RATE = 1.2, AUTO_IDLE_S = 4;
    /** "work_mode": 0 drive, 1 work. */
    public static final String MODE = "work_mode";

    /** What a function is: a joystick axis (-1..1, sprung back) or a toggle (0 / 1). */
    public enum Type { AXIS, TOGGLE }

    /** The work functions and their channels, in the order the right stick and the select key take them. */
    public enum Function {
        BOOM("boom", Type.AXIS), LIFT("lift", Type.AXIS), STICK("stick", Type.AXIS), BUCKET("bucket", Type.AXIS), SWING("swing", Type.AXIS),
        BLADE_LIFT("blade_lift", Type.AXIS), BLADE_TILT("blade_tilt", Type.AXIS), BLADE_ANGLE("blade_angle", Type.AXIS),
        RIPPER("ripper", Type.AXIS), HOIST("hoist", Type.AXIS), TELESCOPE("telescope", Type.AXIS), MAST("mast", Type.AXIS),
        TILT("tilt", Type.AXIS), DUMP("dump", Type.AXIS), AUX("aux", Type.AXIS), ROTATE("rotate", Type.AXIS),
        OUTRIGGERS("outriggers", Type.AXIS), JIB("jib", Type.AXIS), BOWL("bowl", Type.AXIS), APRON("apron", Type.AXIS),
        EJECTOR("ejector", Type.AXIS), SCREED("screed", Type.AXIS), CHUTE("chute", Type.AXIS), ERECTOR("erector", Type.AXIS),
        CUTTER("cutter", Type.TOGGLE), CONVEYOR("conveyor", Type.TOGGLE), VIBRATION("vibration", Type.TOGGLE),
        SPRAY("spray", Type.TOGGLE), HAMMER("hammer", Type.TOGGLE), BROOM("broom", Type.TOGGLE), MIXER("mixer", Type.TOGGLE),
        PUMP("pump", Type.TOGGLE), THRUST("thrust", Type.TOGGLE), JACKS("jacks", Type.TOGGLE), HOOK("hook", Type.TOGGLE);

        public final String channel;
        public final Type type;
        Function(String channel, Type type) { this.channel = channel; this.type = type; }

        public static Function of(String channel) {
            for (Function f : values()) { if (f.channel.equals(channel)) { return f; } }
            return null;
        }
    }

    /** The right stick's lifting and tilting functions in their order of preference, for a machine that does not slew. */
    static final List<Function> LIFTS = List.of(Function.BOOM, Function.LIFT, Function.BLADE_LIFT, Function.MAST, Function.DUMP, Function.HOIST, Function.BOWL, Function.RIPPER, Function.SCREED),
            TILTS = List.of(Function.BUCKET, Function.BLADE_TILT, Function.TILT, Function.APRON, Function.AUX, Function.ROTATE, Function.EJECTOR);

    /** The joystick pattern of an excavator: ISO (the stick on the left stick) or SAE (the boom on the left). */
    public enum Pattern { ISO, SAE }

    /** Which stick axis a function sits on (null: picked with the select key), and with which sign of the key. */
    public record Axis(Function function, int sign) {}

    /**
     * What a machine has for its operator (from its blueprint): the work functions bound, whether it travels by motors
     * on "travel_left" and "travel_right" (a hydrostatic crawler) and whether the blade can float ("float" bound).
     */
    public record Kit(Set<Function> functions, boolean travel, boolean floats) {
        public static final Kit NONE = new Kit(Set.of(), false, false);

        public static Kit of(VehicleBlueprint blueprint) {
            if (blueprint == null) { return NONE; }
            Set<Function> functions = new LinkedHashSet<>();
            boolean left = false, right = false, floats = false;
            for (VehicleBlueprint.Entry e : blueprint.parts()) {
                for (Binding b : e.bindings()) {
                    Function f = Function.of(b.channel());
                    if (f != null) { functions.add(f); }
                    if (b.channel().equals("travel_left")) { left = true; }
                    if (b.channel().equals("travel_right")) { right = true; }
                    if (b.channel().equals("float")) { floats = true; }
                }
            }
            // a hoist has a hook to latch (Cranes: the game reads "hook")
            if (functions.contains(Function.HOIST)) { functions.add(Function.HOOK); }
            Set<Function> ordered = new LinkedHashSet<>();
            for (Function f : Function.values()) { if (functions.contains(f)) { ordered.add(f); } }
            return new Kit(java.util.Collections.unmodifiableSet(ordered), left && right, floats);
        }

        /** Whether it is a machine at all (anything to work or motors to travel on). */
        public boolean machine() { return !functions.isEmpty() || travel; }
        /** Whether it slews. */
        public boolean slews() { return functions.contains(Function.SWING); }
        /** Whether the keys, not the throttle pedal, hold its engine (it slews or travels by motors): auto-idle. */
        public boolean autoIdle() { return slews() || travel; }
        /** Whether in work mode the movement keys are a joystick (it slews), not the car's. */
        public boolean joystickLeft() { return slews(); }
    }

    /**
     * The sticks of a machine in a pattern: leftX, leftY (forward +), rightX (right +), rightY (forward +), each an
     * Axis or null; then the functions the select key goes through.
     */
    public record Layout(Axis leftX, Axis leftY, Axis rightX, Axis rightY, List<Function> selectable) {
        public static Layout of(Kit kit, Pattern pattern) {
            Set<Function> f = kit.functions();
            Axis lx = null, ly = null, rx = null, ry = null;
            if (kit.slews() && f.contains(Function.STICK)) {
                lx = new Axis(Function.SWING, -1);                                  // right on the stick swings right (-)
                Axis stick = new Axis(Function.STICK, -1), boom = f.contains(Function.BOOM) ? new Axis(Function.BOOM, -1) : null;   // forward: out, down
                if (pattern == Pattern.SAE) { ly = boom; ry = stick; } else { ly = stick; ry = boom; }
                rx = f.contains(Function.BUCKET) ? new Axis(Function.BUCKET, -1) : null;                                         // right: dump
            } else if (kit.slews()) {
                lx = new Axis(Function.SWING, -1);
                ly = f.contains(Function.TELESCOPE) ? new Axis(Function.TELESCOPE, 1) : null;
                if (f.contains(Function.HOIST)) {
                    ry = new Axis(Function.HOIST, -1);
                    rx = f.contains(Function.BOOM) ? new Axis(Function.BOOM, -1) : null;
                } else {
                    ry = f.contains(Function.BOOM) ? new Axis(Function.BOOM, -1) : null;
                    rx = f.contains(Function.JIB) ? new Axis(Function.JIB, -1) : f.contains(Function.ROTATE) ? new Axis(Function.ROTATE, -1) : null;
                }
            } else {
                for (Function lift : LIFTS) { if (f.contains(lift)) { ry = new Axis(lift, -1); break; } }
                for (Function tilt : TILTS) { if (f.contains(tilt)) { rx = new Axis(tilt, -1); break; } }
            }
            List<Function> rest = new ArrayList<>();
            for (Function x : f) {
                if (!on(lx, x) && !on(ly, x) && !on(rx, x) && !on(ry, x)) { rest.add(x); }
            }
            return new Layout(lx, ly, rx, ry, List.copyOf(rest));
        }

        private static boolean on(Axis a, Function f) { return a != null && a.function() == f; }
    }

    /** The keys this tick: the sticks' directions held, and the presses of the others since the last tick. */
    public record Keys(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down, boolean rightLeft, boolean rightRight,
                       boolean raise, boolean lower, int select, int mode, int floatToggle) {
        public static final Keys NONE = new Keys(false, false, false, false, false, false, false, false, false, false, 0, 0, 0);
    }

    private boolean work;
    private final Map<Function, Double> values = new LinkedHashMap<>();
    private final Map<Function, Integer> held = new LinkedHashMap<>();
    private int selected;
    private boolean floating;
    private double idle = AUTO_IDLE_S, leftTravel, rightTravel;
    private DriverInputs.Message message;

    /** Starts from a machine's controls as the server last synced them (a machine newly got into): drive mode, toggles as they are. */
    public void adopt(Kit kit, Map<String, Double> controls) {
        work = false;
        values.clear();
        held.clear();
        for (Function f : kit.functions()) {
            values.put(f, f.type == Type.TOGGLE ? (controls.getOrDefault(f.channel, 0.0) > 0.5 ? 1.0 : 0.0) : 0.0);
        }
        selected = 0;
        floating = controls.getOrDefault("float", 0.0) > 0.5;
        idle = AUTO_IDLE_S;
        leftTravel = rightTravel = 0;
        message = null;
    }

    /** Whether the machine is in work mode. */
    public boolean working() { return work; }

    /** The function the select key has picked (null: none to pick). */
    public Function selected(Kit kit, Pattern pattern) {
        List<Function> s = Layout.of(kit, pattern).selectable();
        return s.isEmpty() ? null : s.get(Math.min(selected, s.size() - 1));
    }

    /** The message of the last tick that had one, once. */
    public DriverInputs.Message message() {
        DriverInputs.Message m = message;
        message = null;
        return m;
    }

    /**
     * One tick: the keys, the pattern; the work channels to send (every function the machine has, "work_mode", "float"
     * when it floats, "travel_left"/"travel_right" and "travel_brake" for motor travel, "throttle" when it auto-idles).
     */
    public Map<String, Double> tick(Kit kit, Keys k, Pattern pattern) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (!kit.machine()) { return out; }
        for (int i = 0; i < k.mode(); i++) {
            work = !work;
            message = new DriverInputs.Message(work ? "hdfv.work.mode_work" : "hdfv.work.mode_drive");
        }
        Layout layout = Layout.of(kit, pattern);
        Map<Function, Integer> want = new LinkedHashMap<>();
        int travelForward = 0, travelTurn = 0;
        if (work) {
            if (kit.joystickLeft()) {
                stick(want, layout.leftY(), k.forward(), k.back());
                stick(want, layout.leftX(), k.right(), k.left());
            } else {
                travelForward = (k.forward() ? 1 : 0) - (k.back() ? 1 : 0);
                travelTurn = (k.left() ? 1 : 0) - (k.right() ? 1 : 0);
            }
            stick(want, layout.rightY(), k.up(), k.down());
            stick(want, layout.rightX(), k.rightRight(), k.rightLeft());
            List<Function> s = layout.selectable();
            for (int i = 0; i < k.select(); i++) {
                if (s.isEmpty()) { message = new DriverInputs.Message("hdfv.work.nothing_to_select"); break; }
                selected = (selected + 1) % s.size();
                message = new DriverInputs.Message("hdfv.work.selected", "hdfv.work.function." + s.get(selected).channel);
            }
            if (!s.isEmpty()) {
                Function f = s.get(Math.min(selected, s.size() - 1));
                if (f.type == Type.TOGGLE) {
                    if (k.raise() && values.getOrDefault(f, 0.0) < 0.5) { values.put(f, 1.0); message = new DriverInputs.Message("hdfv.work.on", "hdfv.work.function." + f.channel); }
                    if (k.lower() && values.getOrDefault(f, 0.0) > 0.5) { values.put(f, 0.0); message = new DriverInputs.Message("hdfv.work.off", "hdfv.work.function." + f.channel); }
                } else {
                    int d = (k.raise() ? 1 : 0) - (k.lower() ? 1 : 0);
                    if (d != 0) { want.merge(f, d, Integer::sum); }
                }
            }
            for (int i = 0; i < k.floatToggle(); i++) {
                if (!kit.floats()) { message = new DriverInputs.Message("hdfv.work.no_float"); break; }
                floating = !floating;
                message = new DriverInputs.Message(floating ? "hdfv.work.float_on" : "hdfv.work.float_off");
            }
        } else {
            travelForward = (k.forward() ? 1 : 0) - (k.back() ? 1 : 0);
            travelTurn = (k.left() ? 1 : 0) - (k.right() ? 1 : 0);
        }
        boolean moving = false;
        for (Function f : kit.functions()) {
            if (f.type == Type.TOGGLE) { continue; }
            int d = Integer.signum(want.getOrDefault(f, 0));
            values.put(f, open(values.getOrDefault(f, 0.0), d));
            if (d != 0) { moving = true; }
        }
        for (Function f : kit.functions()) { out.put(f.channel, values.getOrDefault(f, 0.0)); }
        out.put(MODE, work ? 1.0 : 0.0);
        if (kit.floats()) { out.put("float", floating ? 1.0 : 0.0); }
        if (kit.travel()) {
            int dl = Integer.signum(travelForward - travelTurn), dr = Integer.signum(travelForward + travelTurn);
            if (travelForward != 0 && travelTurn != 0) { dl = travelTurn > 0 ? 0 : travelForward; dr = travelTurn > 0 ? travelForward : 0; }
            leftTravel = open(leftTravel, dl);
            rightTravel = open(rightTravel, dr);
            out.put("travel_left", leftTravel);
            out.put("travel_right", rightTravel);
            boolean still = leftTravel == 0 && rightTravel == 0;
            out.put("travel_brake", still ? 1.0 : 0.0);
            if (!still) { moving = true; }
        }
        if (kit.autoIdle()) {
            for (Function f : kit.functions()) { if (f.type == Type.TOGGLE && values.getOrDefault(f, 0.0) > 0.5) { moving = true; } }
            idle = moving ? 0 : idle + TICK;
            out.put("throttle", idle < AUTO_IDLE_S ? 1.0 : 0.0);
        }
        // a winch's spring brake holds the hook while the hoist is centred
        if (kit.functions().contains(Function.HOIST)) { out.put("hoist_brake", values.getOrDefault(Function.HOIST, 0.0) == 0 ? 1.0 : 0.0); }
        return out;
    }

    /** Whether the movement keys are this mapping's, not AeroCore's "throttle" and "rudder" (it auto-idles or works with a left joystick). */
    public boolean ownsMovementKeys(Kit kit) { return kit.autoIdle() || work && kit.joystickLeft(); }

    private static void stick(Map<Function, Integer> want, Axis axis, boolean plus, boolean minus) {
        if (axis == null) { return; }
        int d = ((plus ? 1 : 0) - (minus ? 1 : 0)) * axis.sign();
        if (d != 0) { want.merge(axis.function(), d, Integer::sum); }
    }

    /** A valve a tick later: opened toward a direction (from OPEN_START, at OPEN_RATE), closed at once when let go or reversed. */
    static double open(double value, int direction) {
        if (direction == 0) { return 0; }
        if (Math.signum(value) != direction) { return direction * OPEN_START; }
        double next = Math.min(Math.abs(value) + OPEN_RATE * TICK, 1);
        return Math.round(direction * next * 1000) / 1000.0;
    }
}
