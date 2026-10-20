package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.power.ElectricMachine;

/**
 * A CNC machine as its modules make it (0.8; the user's choice 2026-10-13: a multiblock with modules, 3 to 12 axes).
 *
 * <ul>
 * <li>Machining centre (VMC): X, Y and Z, the tool coming from above; a rotary table (A, about the part's X axis)
 *     makes 4 axes; a trunnion tilting it (B) makes 5.</li>
 * <li>Mill-turn: a turning spindle holds the part (its C axis is the rotary axis) under a milling head; the trunnion's
 *     place is taken by the head's B axis (5); a sub-spindle (C2 and its travel W) takes the part over to finish its
 *     back (7); each lower turret adds X and Z (9, 11) and one may have Y (12).</li>
 * </ul>
 * What the axes buy: a shape needing five axes moving together (Operation.axes) is cut only where five move
 * together (simultaneous()); turning needs a turning spindle; every face a part is cut from that the machine cannot
 * reach in one clamping is another setup (setups()): a fixture prepared once per batch and a re-clamping per piece; the
 * turrets and the sub-spindle cut while the main spindle does (parallel()). Spindle 22 kW, sub-spindle 11 kW, live
 * turrets 5.5 kW each, rotary and tilt drives 1.5 kW each, and the controls, hydraulics, coolant and chip conveyor
 * (2 kW plus 0.5 kW an axis): a 3-axis centre's rated input is about 27 kW, a 12-axis mill-turn's about 58 kW (catalogue
 * figures of the kind).
 */
public record CncConfig(boolean rotaryTable, boolean tilt, boolean turningSpindle, boolean subSpindle, int turrets, boolean turretY) {
    public static final CncConfig VMC_3 = new CncConfig(false, false, false, false, 0, false);
    public static final CncConfig VMC_4 = new CncConfig(true, false, false, false, 0, false);
    public static final CncConfig VMC_5 = new CncConfig(true, true, false, false, 0, false);
    public static final CncConfig MILL_TURN_5 = new CncConfig(false, true, true, false, 0, false);
    public static final CncConfig MILL_TURN_9 = new CncConfig(false, true, true, true, 1, false);
    public static final CncConfig MILL_TURN_12 = new CncConfig(false, true, true, true, 2, true);
    /** The presets, fewest axes first within each kind. */
    public static final java.util.List<CncConfig> PRESETS = java.util.List.of(VMC_3, VMC_4, VMC_5, MILL_TURN_5, MILL_TURN_9, MILL_TURN_12);

    public static final double SPINDLE_W = 22e3, SUB_SPINDLE_W = 11e3, TURRET_W = 5.5e3, ROTARY_W = 1.5e3, BASE_W = 2e3, BASE_PER_AXIS_W = 500;

    public CncConfig {
        if (rotaryTable && turningSpindle) { throw new IllegalArgumentException("A mill-turn's spindle is its rotary axis: no rotary table"); }
        if (!turningSpindle && (subSpindle || turrets > 0 || turretY)) { throw new IllegalArgumentException("Sub-spindles and turrets go on a mill-turn"); }
        if (tilt && !turningSpindle && !rotaryTable) { throw new IllegalArgumentException("A trunnion tilts the rotary table"); }
        if (turrets < 0 || turrets > 2) { throw new IllegalArgumentException("At most two turrets"); }
        if (turretY && turrets == 0) { throw new IllegalArgumentException("A Y axis needs a turret"); }
    }

    /** All its axes. */
    public int axes() { return 3 + (rotaryTable ? 1 : 0) + (tilt ? 1 : 0) + (turningSpindle ? 1 : 0) + (subSpindle ? 2 : 0) + 2 * turrets + (turretY ? 1 : 0); }

    /** Axes that move together on one tool. */
    public int simultaneous() { return 3 + (rotaryTable || turningSpindle ? 1 : 0) + (tilt ? 1 : 0); }

    /** Whether it can cut a step: the process (turning and grinding round the part need a turning spindle, other work round it a rotary axis), the grade, the axes moving together. */
    public boolean can(Operation op) {
        if (!op.process().cutting() || !Station.CNC.can(op)) { return false; }
        if (op.axes() > simultaneous()) { return false; }
        boolean around = (op.faces() & Operation.AROUND) != 0;
        if (op.process() == Process.TURN || around && op.process() == Process.GRIND) { return turningSpindle; }
        if (around) { return turningSpindle || rotaryTable; }
        return true;
    }

    /**
     * Clampings to cut a set of faces (Operation faces, or-ed over the steps of a job): a 3-axis centre one a face; a
     * rotary table reaches the four sides at once and each end in another; a trunnion everything but the face it is
     * clamped on (the bottom), which takes a second, or clamps the part on its rotary table as a 4-axis machine does,
     * whichever takes fewer; a mill-turn everything but the chucked back, which the sub-spindle reaches without letting
     * go (else a second).
     */
    public int setups(int faces) {
        faces &= Operation.BOX | Operation.AROUND;
        if (faces == 0) { return 0; }
        boolean around = (faces & Operation.AROUND) != 0;
        int n;
        if (turningSpindle) {
            n = (faces & ~Operation.BACK) != 0 ? 1 : 0;
            if ((faces & Operation.BACK) != 0) { n += subSpindle ? (n == 0 ? 1 : 0) : 1; }
        } else if (rotaryTable) {
            n = (faces & Operation.SIDES) != 0 || around ? 1 : 0;
            n += Integer.bitCount(faces & (Operation.FRONT | Operation.BACK));
            if (tilt) { n = Math.min(n, ((faces & ~Operation.BOTTOM) != 0 ? 1 : 0) + ((faces & Operation.BOTTOM) != 0 ? 1 : 0)); }
        } else {
            n = Integer.bitCount(faces & Operation.BOX) + (around ? 4 : 0);
        }
        return Math.max(n, 1);
    }

    /** How much faster its cutting goes with the turrets and sub-spindle cutting alongside the main spindle (a rule: 40 % a turret, 20 % the sub-spindle). */
    public double parallel() { return 1 + 0.4 * turrets + (subSpindle ? 0.2 : 0); }

    /** The main spindle's power, W. */
    public double spindleW() { return SPINDLE_W; }

    /** Controls, hydraulics, coolant, chip conveyor and the axes held under power while it works, W. */
    public double baseW() { return BASE_W + BASE_PER_AXIS_W * axes(); }

    /** Most electrical power it draws, every motor at its rating (IE3 efficiencies, ElectricMachine), W. */
    public double ratedW() {
        double w = input(SPINDLE_W) + baseW();
        if (subSpindle) { w += input(SUB_SPINDLE_W); }
        w += turrets * input(TURRET_W);
        if (rotaryTable) { w += input(ROTARY_W); }
        if (tilt) { w += input(ROTARY_W); }
        return w;
    }

    static double input(double outputW) { return ElectricMachine.of(outputW).inputForOutput(outputW); }

    /** The modules as a short id ("vmc_5", "mill_turn_12"; others "cnc_" and the axes). */
    public String id() {
        if (equals(VMC_3)) { return "vmc_3"; }
        if (equals(VMC_4)) { return "vmc_4"; }
        if (equals(VMC_5)) { return "vmc_5"; }
        if (equals(MILL_TURN_5)) { return "mill_turn_5"; }
        if (equals(MILL_TURN_9)) { return "mill_turn_9"; }
        if (equals(MILL_TURN_12)) { return "mill_turn_12"; }
        return "cnc_" + axes();
    }
}
