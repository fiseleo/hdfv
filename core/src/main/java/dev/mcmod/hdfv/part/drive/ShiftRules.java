package dev.mcmod.hdfv.part.drive;

import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartAfter;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;
import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.aerocore.api.vehicle.part.TransmissionPart;

import java.util.Arrays;
import java.util.List;

/**
 * hdfv's rules between the driver and the drive's hardware (AeroCore's TransmissionPart and TransferPart): each step
 * they turn the driver's inputs into the hardware's commands (PartStep.override) and let the hardware run. The rules
 * themselves are described on the designs that set them (ShiftScheduleDesign, CvtControlDesign, TransferCaseDesign).
 * A manual box has none: its lever and pedal are the driver's, unless its automatic clutch is on (0.14, "auto_clutch":
 * the pedal for a driver on a keyboard; memory after the hardware's states: whether the clutch has caught, how far it is in).
 *
 * <p>Memory after the hardware's states: the commanded gear, seconds since it changed, the "shift" input last step
 * (to act on a press once), whether a dual-clutch box's clutch has caught. A gearbox shows the selector position
 * after the hardware's three values.
 */
public final class ShiftRules {
    private ShiftRules() {}

    static final int MEMORY = 4;
    /** A dual-clutch box's clutch counts as caught under this slip, rad/s. */
    static final double CAUGHT = 8;
    /**
     * 0.14, a manual's automatic clutch: its memory after the hardware's states (whether the clutch has caught, how far
     * it is let in) and the seconds it takes to let the clutch in after a shift.
     */
    static final int CLUTCH_MEMORY = 2;
    public static final double CLUTCH_IN_S = 0.25;

    /** A part that runs another's hardware under rules: everything but the inputs, memory and loads is the inner part's. */
    private abstract static class Ruled implements Part {
        final PartDesign owner;
        final Part inner;
        final int memory, shown;

        Ruled(PartDesign owner, Part inner, int memory, int shown) { this.owner = owner; this.inner = inner; this.memory = memory; this.shown = shown; }

        @Override public PartDesign design() { return owner; }
        @Override public MassProperties mass() { return inner.mass(); }
        @Override public List<CollisionSphere> collision() { return inner.collision(); }
        @Override public List<DesignIssue> issues() { return inner.issues(); }
        @Override public List<Rotor> rotors() { return inner.rotors(); }
        @Override public List<Link> links() { return inner.links(); }
        @Override public int internalStates() { return inner.internalStates() + memory; }
        @Override public double[] initialInternals() { return Arrays.copyOf(inner.initialInternals(), internalStates()); }
        @Override public int shownValues() { return inner.shownValues() + shown; }
        @Override public void after(PartAfter step) { inner.after(step); }
        @Override public void warmUp() { inner.warmUp(); }
    }

    /** A gearbox under its kind's rules. */
    static Part gearbox(GearboxDesign design, TransmissionPart box) {
        if (design.kind() == Gearboxes.Kind.MANUAL) {
            return new Ruled(design, box, CLUTCH_MEMORY, 1) {
                @Override public List<String> inputs() {
                    List<String> in = new java.util.ArrayList<>(box.inputs());
                    in.addAll(List.of("auto_clutch", "throttle"));
                    return in;
                }
                @Override public void loads(PartStep step) {
                    if (step.input("auto_clutch", 0) > 0.5) { autoClutch(design, box, step); }
                    box.loads(step);
                    step.show(3, Math.round(step.input("gear", 0)));
                }
            };
        }
        return new Ruled(design, box, MEMORY, 1) {
            @Override public List<String> inputs() {
                return switch (design.kind()) {
                    case AUTOMATIC -> List.of("selector", "shift", "throttle");
                    case DUAL_CLUTCH -> List.of("selector", "shift", "throttle", "brake");
                    default -> List.of("selector", "throttle");
                };
            }

            @Override public void loads(PartStep step) {
                int sel = (int) Math.round(Math.clamp(step.input("selector", Gearboxes.DRIVE), -2, 2));
                double throttle = Math.clamp(step.input("throttle", 0), 0, 1);
                if (design.kind() == Gearboxes.Kind.CVT) { cvt(design, box, step, sel, throttle); }
                else { stepped(design, box, step, sel, throttle); }
                step.show(3, sel);
                box.loads(step);
            }
        };
    }

    /** The speed (rpm) the box's input side would turn at in a gear for its output's speed. */
    private static double rpm(GearboxDesign d, double outputRadPerS, int gear) {
        double[] r = d.ratios();
        return Math.abs(outputRadPerS) * r[gear - 1] * 30 / Math.PI;
    }

    private static void stepped(GearboxDesign d, TransmissionPart box, PartStep step, int sel, double throttle) {
        int s0 = TransmissionPart.STATES, n = d.forwardGears();
        ShiftScheduleDesign s = d.control();
        int gear = (int) step.internal(s0), next = gear;
        double since = step.internal(s0 + 1) + step.dt(), lastShift = step.internal(s0 + 2), shift = step.input("shift", 0);
        boolean shifting = box.shifting(step);
        double out = step.rotorSpeed(box.rotors().size() - 1);
        switch (sel) {
            case Gearboxes.PARK -> next = -2;
            case Gearboxes.REVERSE -> next = -1;
            case Gearboxes.NEUTRAL -> next = 0;
            default -> {
                if (next < 1) { next = 1; }
                if (sel == Gearboxes.MANUAL_MODE) {
                    if (shift > 0.5 && lastShift <= 0.5 && next < n) { next++; }
                    else if (shift < -0.5 && lastShift >= -0.5 && next > 1 && rpm(d, out, next - 1) < s.upFullRpm()) { next--; }
                    else if (next > 1 && since > ShiftScheduleDesign.HOLD && !shifting && rpm(d, out, next) < 0.8 * s.downLightRpm()) { next--; }
                } else if (since > ShiftScheduleDesign.HOLD && !shifting) {
                    double up = s.upRpm(throttle), down = s.downRpm(throttle);
                    if (next < n && rpm(d, out, next) > up) { next++; }
                    else if (next > 1 && rpm(d, out, next) < down && rpm(d, out, next - 1) < up) { next--; }
                    else if (throttle > 0.95 && next > 1 && rpm(d, out, next - 1) < 0.9 * s.upFullRpm()) { next--; }
                }
            }
        }
        if (next != gear) { since = 0; }
        step.override("gear", next);
        if (d.kind() == Gearboxes.Kind.AUTOMATIC) {
            boolean lock = next >= s.lockupGear() && rpm(d, out, Math.max(next, 1)) > s.lockupRpm() && !shifting && since > 1.0 && throttle < 0.85;
            step.override("lockup", lock ? 1 : 0);
        } else {
            launch(d, step, next, throttle);
            int pre = next >= 1 ? (throttle > 0.05 || next == 1 ? next + 1 : next - 1) : 1;
            if (pre > n) { pre = n - 1; }
            if (pre < 1 || pre == next) { pre = next == 1 ? 2 : 1; }
            step.override("preselect", Math.min(pre, n));
        }
        step.setInternal(s0, next);
        step.setInternal(s0 + 1, Math.min(since, 1e3));
        step.setInternal(s0 + 2, shift);
    }

    /**
     * 0.14: a manual's automatic clutch, for a driver on a keyboard ("auto_clutch" over 0.5; the "clutch" pedal still
     * presses it further). While the lever is in another gear than the one engaged (a shift, synchronising) the pedal is
     * down; in gear it moves off as a dual-clutch box does (launch: by engine speed from idle to the launch speed the
     * throttle asks, ShiftScheduleDesign's defaults for a manual: 800 and 2200 rpm, no creep), held shut once the clutch
     * has caught until the engine falls under 0.85 of idle (no stall), and let in over CLUTCH_IN_S at most.
     */
    private static void autoClutch(GearboxDesign d, TransmissionPart box, PartStep step) {
        ShiftScheduleDesign s = d.control();
        int s0 = TransmissionPart.STATES;
        int lever = (int) Math.round(Math.clamp(step.input("gear", 0), -1, box.gears())), engaged = box.engaged(step);
        boolean held = step.internal(s0) > 0.5;
        double in = step.internal(s0 + 1), command;
        double engine = step.rotorSpeed(0) * 30 / Math.PI, idle = s.idleRpm(), throttle = Math.clamp(step.input("throttle", 0), 0, 1);
        if (lever == 0) { held = false; command = 0; in = 0; }
        else if (lever != engaged) { held = false; command = 0; in = 0; }
        else {
            double slip = Math.abs(step.rotorSpeed(0) - step.rotorSpeed(1));
            if (held && engine < 0.85 * idle) { held = false; }
            if (!held && slip < CAUGHT && engine > 0.9 * idle) { held = true; }
            if (held) { command = 1; }
            else if (engine < 0.9 * idle) { command = 0; }
            else {
                double target = idle + throttle * (s.launchRpm() - idle);
                double x = Math.clamp((engine - idle) / Math.max(target - idle, 100), 0, 1);
                command = x * x;
            }
        }
        in = command < in ? command : Math.min(command, in + step.dt() / CLUTCH_IN_S);
        step.override("clutch", Math.max(Math.clamp(step.input("clutch", 0), 0, 1), 1 - in));
        step.setInternal(s0, held ? 1 : 0);
        step.setInternal(s0 + 1, in);
    }

    /** A dual-clutch box's driving clutch: launch by engine speed, then held shut. */
    private static void launch(GearboxDesign d, PartStep step, int gear, double throttle) {
        ShiftScheduleDesign s = d.control();
        int s0 = TransmissionPart.STATES;
        double engine = step.rotorSpeed(0) * 30 / Math.PI, idle = s.idleRpm();
        int shaft = gear == 0 || gear == -2 ? 0 : gear < 0 || gear % 2 == 0 ? 2 : 1;
        boolean held = step.internal(s0 + 3) > 0.5;
        double slip = shaft == 0 ? Double.POSITIVE_INFINITY : Math.abs(step.rotorSpeed(0) - step.rotorSpeed(shaft));
        if (held && (engine < 0.85 * idle || shaft == 0)) { held = false; }
        if (!held && shaft != 0 && slip < CAUGHT && engine > 0.9 * idle) { held = true; }
        double command;
        if (held) { command = 1; }
        else if (engine < 0.9 * idle) { command = 0; }
        else {
            double target = idle + throttle * (s.launchRpm() - idle);
            double x = Math.clamp((engine - idle) / Math.max(target - idle, 100), 0, 1);
            boolean braking = step.input("brake", 0) > 0.1 && throttle < 0.02;
            command = (braking ? 0 : s.creepShare()) + (1 - s.creepShare()) * x * x;
        }
        step.override("clutch", command);
        step.setInternal(s0 + 3, held ? 1 : 0);
    }

    private static void cvt(GearboxDesign d, TransmissionPart box, PartStep step, int sel, double throttle) {
        CvtControlDesign c = d.cvtControl();
        double low = d.variator().lowRatio(), high = d.variator().highRatio();
        int mode = sel == Gearboxes.MANUAL_MODE ? Gearboxes.DRIVE : sel;
        double out = Math.abs(step.rotorSpeed(box.rotors().size() - 1)), aim = low;
        double engine = Math.abs(step.rotorSpeed(0)), turbineSpeed = Math.abs(step.rotorSpeed(1));
        // the turbine's target is the engine's times the converter's present speed ratio, so the engine runs at its target
        double nu = engine > 1 ? Math.clamp(turbineSpeed / engine, 0.3, 1) : 1;
        if (mode == Gearboxes.DRIVE && out > 1e-3) { aim = Math.clamp(c.targetRpm(throttle) * Math.PI / 30 * nu / out, high, low); }
        double turbine = turbineSpeed * 30 / Math.PI;
        boolean lock = mode == Gearboxes.DRIVE && turbine > c.lockupRpm() && throttle < 0.85;
        step.override("gear", mode);
        step.override("ratio", aim);
        step.override("lockup", lock ? 1 : 0);
    }

    /** A transfer case under its rule: an on-demand coupling preloaded by the throttle at low speed; the others the driver's. */
    static Part transfer(TransferCaseDesign design, TransferPart inner) {
        if (design.kind() != TransferPart.Kind.ON_DEMAND) {
            return new Ruled(design, inner, 0, 0) {
                @Override public List<String> inputs() { return inner.inputs(); }
                @Override public void loads(PartStep step) { inner.loads(step); }
            };
        }
        return new Ruled(design, inner, 0, 0) {
            @Override public List<String> inputs() { return design.lowRatio() > 1 ? List.of("range", "throttle") : List.of("throttle"); }
            @Override public void loads(PartStep step) {
                double throttle = Math.clamp(step.input("throttle", 0), 0, 1);
                double prop = Math.abs(step.rotorSpeed(design.frontPrimary() ? 2 : 3)) * 30 / Math.PI;
                step.override("couple", prop < design.preloadBelowRpm() ? design.preloadShare() * throttle : 0);
                inner.loads(step);
            }
        };
    }
}
