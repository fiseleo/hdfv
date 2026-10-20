package dev.mcmod.hdfv.drive;

import dev.mcmod.hdfv.part.drive.Gearboxes;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A car driver's keys turned into control channels (0.14), one client tick at a time; the client reads the keys and
 * sends what comes out (only what the car has, CarKit):
 * <ul>
 * <li>"steer" (+1 left) from left and right: it moves at STEER_RATE a second toward the keys (STEER_RETURN back toward
 *     the middle), and the most it turns falls with speed, to half at STEER_HALF_SPEED (90 km/h).</li>
 * <li>"brake": the back key (a car's brake pedal) or the brake key held.</li>
 * <li>"handbrake": a tap (under HANDBRAKE_HOLD_TICKS) pulls it up or lets it down; held longer it is on while held
 *     and off when let go (for a slide).</li>
 * <li>A selector (automatic, dual-clutch, CVT): the selector key steps P, R, N, D (from M: back to D); shift up or
 *     down in D goes to M with a shift (paddles), in M a shift: "shift" +1 or -1 for PULSE_TICKS, then a tick at 0
 *     (ShiftRules shifts on the rise), presses queued.</li>
 * <li>A manual: the shift keys move the lever ("gear": -1 reverse, 0 neutral, 1..n), into reverse only below
 *     REVERSE_BELOW_MPS; the selector key to neutral; "clutch" while its key is held; "auto_clutch" as the player chose.</li>
 * <li>The drive-mode key steps the transfer case's modes (CarKit.modes); the differential-lock key "diff_lock" round 0,
 *     1 (rear), 2 (front too) as far as the car has lockers; the old shared "lock" with it on cars wired before 0.14.</li>
 * <li>0.15: the ride-height key steps an air-sprung car's level round the design height, high and low: "ride_height"
 *     0, +0.5, -0.5 (the axles read it with 0.5 added, so a car nobody drives stands at its design height).</li>
 * <li>0.18: the coupling key counts up "couple" on a tractor (CarKit.fifthWheel): the server couples or lets go of a
 *     trailer on each new count (a count, not a pulse, so no press is lost between ticks).</li>
 * <li>0.19: "horn" while its key is held; "indicators" -1 left, 1 right, 2 hazard, 0 off: the left and right keys turn
 *     an indicator on and off (the other side's key switches over), the hazard key turns the hazards on and back to
 *     what was before; an indicator goes off by itself when the wheel, having turned past CANCEL_STEER its way,
 *     comes back to the middle (the self-cancelling cam of a real column).</li>
 * </ul>
 * A message (a language key and its arguments) tells what changed, for the action bar.
 */
public final class DriverInputs {
    public static final double TICK = 0.05, STEER_RATE = 3, STEER_RETURN = 5, STEER_HALF_SPEED = 25, REVERSE_BELOW_MPS = 5 / 3.6;
    public static final int HANDBRAKE_HOLD_TICKS = 8, PULSE_TICKS = 2;
    /** 0.18: the coupling count wraps here (a channel's value stays small). */
    public static final int COUPLE_WRAP = 1000;
    /** 0.19: the indicators' values, and how far the wheel turns its way before coming back cancels one. */
    public static final int INDICATE_LEFT = -1, INDICATE_RIGHT = 1, HAZARD = 2;
    public static final double CANCEL_STEER = 0.25;

    /** The keys this tick: those held, and how many times the others were pressed since the last tick. */
    public record Keys(boolean left, boolean right, boolean back, boolean brake, boolean handbrake, boolean clutch,
                       int shiftUp, int shiftDown, int selector, int driveMode, int diffLock, int rideHeight, int couple,
                       int indicateLeft, int indicateRight, int hazard, boolean horn) {
        public static final Keys NONE = new Keys(false, false, false, false, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);

        /** The keys before 0.19 (no indicators or horn). */
        public Keys(boolean left, boolean right, boolean back, boolean brake, boolean handbrake, boolean clutch, int shiftUp, int shiftDown, int selector,
                    int driveMode, int diffLock, int rideHeight, int couple) {
            this(left, right, back, brake, handbrake, clutch, shiftUp, shiftDown, selector, driveMode, diffLock, rideHeight, couple, 0, 0, 0, false);
        }

        /** The keys before 0.18 (no coupling presses). */
        public Keys(boolean left, boolean right, boolean back, boolean brake, boolean handbrake, boolean clutch, int shiftUp, int shiftDown, int selector,
                    int driveMode, int diffLock, int rideHeight) {
            this(left, right, back, brake, handbrake, clutch, shiftUp, shiftDown, selector, driveMode, diffLock, rideHeight, 0);
        }

        /** The keys before 0.15 (no ride-height presses). */
        public Keys(boolean left, boolean right, boolean back, boolean brake, boolean handbrake, boolean clutch, int shiftUp, int shiftDown, int selector,
                    int driveMode, int diffLock) {
            this(left, right, back, brake, handbrake, clutch, shiftUp, shiftDown, selector, driveMode, diffLock, 0, 0);
        }
    }

    /** What the action bar says: a language key and its arguments. */
    public record Message(String key, Object... args) {}

    private double steer;
    private boolean handbrakeOn, handbrakeDown, handbrakeBefore;
    private int handbrakeTicks;
    private int selector, lever, mode, diffLock, ride, couple;
    /** 0.19: the indicators, what the hazards went back to, and whether the wheel has turned the indicated way. */
    private int indicators, beforeHazard;
    private boolean turnedThatWay;
    private int pulse, gap;
    private final ArrayDeque<Integer> shifts = new ArrayDeque<>();
    private Message message;

    /** Starts from a car's controls as the server last synced them (a car newly got into). */
    public void adopt(CarKit kit, Map<String, Double> controls) {
        steer = 0;
        handbrakeOn = controls.getOrDefault("handbrake", 0.0) > 0.5;
        handbrakeDown = false;
        selector = (int) Math.round(controls.getOrDefault("selector", 0.0));
        if (selector == Gearboxes.MANUAL_MODE && !kit.manualMode()) { selector = Gearboxes.DRIVE; }
        lever = (int) Math.round(controls.getOrDefault("gear", 0.0));
        diffLock = (int) Math.min(Math.round(controls.getOrDefault("diff_lock", controls.getOrDefault("lock", 0.0))), kit.diffLocks());
        ride = (int) Math.clamp(Math.round(2 * controls.getOrDefault("ride_height", 0.0)), -1, 1);
        couple = (int) Math.round(controls.getOrDefault("couple", 0.0));
        indicators = (int) Math.round(controls.getOrDefault("indicators", 0.0));
        beforeHazard = 0;
        turnedThatWay = false;
        mode = 0;
        List<DriveMode> modes = kit.modes();
        for (int i = 0; i < modes.size(); i++) {
            DriveMode m = modes.get(i);
            boolean same = switch (kit.transfer()) {
                case PART_TIME -> m.mode == Math.round(controls.getOrDefault("mode", 0.0));
                case FULL_TIME -> m.range == Math.round(controls.getOrDefault("range", 0.0))
                        && m.centreLocked == (kit.centreLock() != null && controls.getOrDefault(kit.centreLock(), 0.0) > 0.5);
                case ON_DEMAND -> m.range == Math.round(controls.getOrDefault("range", 0.0));
            };
            if (same) { mode = i; break; }
        }
        pulse = 0; gap = 0;
        shifts.clear();
        message = null;
    }

    /** The message of the last tick that had one, once. */
    public Message message() {
        Message m = message;
        message = null;
        return m;
    }

    /** The current drive mode (null: the car has none to switch). */
    public DriveMode mode(CarKit kit) {
        List<DriveMode> modes = kit.modes();
        return modes.isEmpty() ? null : modes.get(Math.min(mode, modes.size() - 1));
    }

    /** One tick: the keys, the car's speed (m/s) and whether the player wants the automatic clutch; the channels to send. */
    public Map<String, Double> tick(CarKit kit, Keys k, double speedMps, boolean autoClutch) {
        Map<String, Double> out = new LinkedHashMap<>();
        double v = Math.abs(speedMps);
        steer = steer(steer, k.left(), k.right(), v);
        if (kit.steers()) { out.put("steer", steer); }
        out.put("brake", k.back() || k.brake() ? 1.0 : 0.0);
        // handbrake
        if (k.handbrake() && !handbrakeDown) { handbrakeDown = true; handbrakeTicks = 0; handbrakeBefore = handbrakeOn; handbrakeOn = true; }
        else if (k.handbrake()) { handbrakeTicks++; }
        else if (handbrakeDown) {
            handbrakeDown = false;
            handbrakeOn = handbrakeTicks < HANDBRAKE_HOLD_TICKS && !handbrakeBefore;
            if (kit.handbrake()) { message = new Message(handbrakeOn ? "hdfv.drive.handbrake_on" : "hdfv.drive.handbrake_off"); }
        }
        if (kit.handbrake()) { out.put("handbrake", handbrakeOn ? 1.0 : 0.0); }
        // gears
        if (kit.selector()) { selector(kit, k, out); }
        else if (kit.gearbox() == Gearboxes.Kind.MANUAL) { lever(kit, k, v, autoClutch, out); }
        // the transfer case and the lockers
        List<DriveMode> modes = kit.modes();
        for (int i = 0; i < k.driveMode(); i++) {
            if (modes.isEmpty()) { message = new Message("hdfv.drive.no_modes"); break; }
            mode = (mode + 1) % modes.size();
            message = new Message("hdfv.drive.mode." + modes.get(mode).name().toLowerCase());
        }
        if (!modes.isEmpty()) {
            DriveMode m = modes.get(Math.min(mode, modes.size() - 1));
            if (kit.transfer() == dev.mcmod.aerocore.api.vehicle.part.TransferPart.Kind.PART_TIME) { out.put("mode", (double) m.mode); }
            else { out.put("range", (double) m.range); }
            if (kit.centreLock() != null) { out.put(kit.centreLock(), m.centreLocked ? 1.0 : 0.0); }
        }
        for (int i = 0; i < k.diffLock(); i++) {
            if (kit.diffLocks() == 0) { message = new Message("hdfv.drive.no_locker"); break; }
            diffLock = (diffLock + 1) % (kit.diffLocks() + 1);
            message = new Message("hdfv.drive.diff_lock." + (diffLock == 0 ? "off" : diffLock == 1 && kit.diffLocks() == 1 ? "on" : diffLock == 1 ? "rear" : "both"));
        }
        if (kit.diffLocks() > 0) {
            out.put("diff_lock", (double) diffLock);
            if (kit.lock()) { out.put("lock", diffLock > 0 ? 1.0 : 0.0); }
        }
        // 0.15: the air suspension's level: design, high, low
        for (int i = 0; i < k.rideHeight(); i++) {
            if (!kit.rideHeight()) { message = new Message("hdfv.drive.no_air"); break; }
            ride = ride == 0 ? 1 : ride == 1 ? -1 : 0;
            message = new Message("hdfv.drive.ride_height." + (ride > 0 ? "high" : ride < 0 ? "low" : "normal"));
        }
        if (kit.rideHeight()) { out.put("ride_height", 0.5 * ride); }
        // 0.18: the coupling key, counted
        if (k.couple() > 0 && !kit.fifthWheel()) { message = new Message("hdfv.hitch.no_fifth_wheel"); }
        else if (k.couple() > 0) { couple = (couple + k.couple()) % COUPLE_WRAP; }
        if (kit.fifthWheel()) { out.put("couple", (double) couple); }
        // 0.19: the indicators and the horn
        indicate(k);
        out.put("indicators", (double) indicators);
        out.put("horn", k.horn() ? 1.0 : 0.0);
        return out;
    }

    /** The indicators this tick: the keys, then the wheel's self-cancelling. */
    private void indicate(Keys k) {
        for (int i = 0; i < k.hazard(); i++) {
            if (indicators == HAZARD) { indicators = beforeHazard; }
            else { beforeHazard = indicators; indicators = HAZARD; }
            message = new Message(indicators == HAZARD ? "hdfv.drive.hazard_on" : "hdfv.drive.hazard_off");
        }
        for (int i = 0; i < k.indicateLeft() + k.indicateRight(); i++) {
            int want = i < k.indicateLeft() ? INDICATE_LEFT : INDICATE_RIGHT;
            indicators = indicators == want ? 0 : want;
            turnedThatWay = false;
            message = new Message(indicators == 0 ? "hdfv.drive.indicator_off" : want == INDICATE_LEFT ? "hdfv.drive.indicator_left" : "hdfv.drive.indicator_right");
        }
        if (indicators == INDICATE_LEFT || indicators == INDICATE_RIGHT) {
            double way = indicators == INDICATE_LEFT ? steer : -steer;   // steer +1 is left
            if (way > CANCEL_STEER) { turnedThatWay = true; }
            else if (turnedThatWay && way < 0.05) { indicators = 0; turnedThatWay = false; }
        }
    }

    /** The indicators as they are now (-1, 0, 1, 2). */
    public int indicators() { return indicators; }

    private void selector(CarKit kit, Keys k, Map<String, Double> out) {
        for (int i = 0; i < k.selector(); i++) {
            selector = selector == Gearboxes.MANUAL_MODE ? Gearboxes.DRIVE : selector >= Gearboxes.DRIVE ? Gearboxes.PARK : selector + 1;
            message = new Message("hdfv.flight.selector." + "PRND".charAt(selector + 2));
        }
        for (int i = 0; i < k.shiftUp() + k.shiftDown(); i++) {
            int dir = i < k.shiftUp() ? 1 : -1;
            if (!kit.manualMode()) { message = new Message("hdfv.drive.no_manual_mode"); break; }
            if (selector != Gearboxes.DRIVE && selector != Gearboxes.MANUAL_MODE) { message = new Message("hdfv.drive.shift_in_drive"); break; }
            selector = Gearboxes.MANUAL_MODE;
            shifts.add(dir);
            message = new Message(dir > 0 ? "hdfv.drive.shift_up" : "hdfv.drive.shift_down");
        }
        out.put("selector", (double) selector);
        if (kit.manualMode()) { out.put("shift", (double) shiftPulse()); }
    }

    /** The "shift" channel this tick: a queued press held for PULSE_TICKS, then a tick at 0 before the next. */
    private int shiftPulse() {
        if (pulse != 0) {
            int s = Integer.signum(pulse);
            pulse -= s;
            if (pulse == 0) { gap = 1; }
            return s;
        }
        if (gap > 0) { gap--; return 0; }
        if (!shifts.isEmpty()) {
            int s = shifts.poll();
            pulse = s * (PULSE_TICKS - 1);
            if (pulse == 0) { gap = 1; }
            return s;
        }
        return 0;
    }

    private void lever(CarKit kit, Keys k, double speed, boolean autoClutch, Map<String, Double> out) {
        for (int i = 0; i < k.selector(); i++) { lever = 0; message = new Message("hdfv.drive.gear", "N"); }
        for (int i = 0; i < k.shiftUp(); i++) {
            if (lever < kit.forwardGears()) { lever++; }
            message = new Message("hdfv.drive.gear", gearName(lever));
        }
        for (int i = 0; i < k.shiftDown(); i++) {
            if (lever == 0 && speed > REVERSE_BELOW_MPS) { message = new Message("hdfv.drive.reverse_stop"); break; }
            if (lever > -1) { lever--; }
            message = new Message("hdfv.drive.gear", gearName(lever));
        }
        out.put("gear", (double) lever);
        out.put("clutch", k.clutch() ? 1.0 : 0.0);
        if (kit.autoClutch()) { out.put("auto_clutch", autoClutch ? 1.0 : 0.0); }
    }

    /** The steering a tick later from the left and right keys at a speed (m/s): smooth, and less of it the faster (also an aircraft's nose wheel). */
    public static double steer(double steer, boolean left, boolean right, double speedMps) {
        double v = Math.abs(speedMps), reach = 1 / (1 + (v / STEER_HALF_SPEED) * (v / STEER_HALF_SPEED));
        double aim = ((left ? 1 : 0) - (right ? 1 : 0)) * reach;
        boolean outward = aim != 0 && Math.signum(aim) == Math.signum(steer) && Math.abs(aim) > Math.abs(steer) || steer == 0 && aim != 0;
        double rate = (outward ? STEER_RATE : STEER_RETURN) * TICK;
        double next = Math.abs(aim - steer) <= rate ? aim : steer + Math.signum(aim - steer) * rate;
        return Math.round(next * 1000) / 1000.0;
    }

    /** A manual's lever position as the driver reads it: R, N, 1..n. */
    public static String gearName(int lever) { return lever < 0 ? "R" : lever == 0 ? "N" : Integer.toString(lever); }

    /** The steering this tick (for tests). */
    public double steer() { return steer; }
}
