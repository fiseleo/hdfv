package dev.mcmod.hdfv.manufacture;

/**
 * One step of a workpiece's route (0.7).
 *
 * <ul>
 * <li>Cutting steps carry what they do: the volume they remove (m^3), the area they finish (m^2), holes (count,
 *     diameter, depth), the size their tolerance is on (m) and the IT grade it needs; how many axes must move
 *     together to cut the shape (0.8: three for prismatic and turned work, five for a complex shape, aerofoils,
 *     impellers and angled micro-holes, CNC only) and the faces it is cut from (0.8: which sides of the part, or
 *     round it; a CNC's setups come from them, CncConfig.setups). Their time depends on the station (Plans.seconds).</li>
 * <li>Forming, joining and assembling steps carry their time (s), worked out by the planner from the physics
 *     (Chvorinov, heating through, deposition, curing).</li>
 * </ul>
 * Faces: the part lies with its axis (a turned part's axis of revolution, a block's crank axis) along X: FRONT is its
 * +X end, BACK its -X end, TOP and BOTTOM, LEFT and RIGHT the sides; AROUND is its surface of revolution (turned, or
 * features round the axis: radial holes, cam lobes, splines). 0: not yet given (the planner's default for the process).
 *
 * 0.17: a step may make a feature (Feature: a bore honed, gear teeth, splines...) that decides which specialised heads
 * take it (the machines of 0.16 ignore it), and a step inserted into 0.16's routes is marked added (Routes moves old
 * workpieces past them). legacy() is the step as 0.16 had it.
 */
public record Operation(Process process, double removedM3, double finishedM2, int holes, double holeDiameterM, double holeDepthM, double sizeM, int grade,
                        int axes, int faces, double fixedS, Feature feature, boolean added) {
    public static final int TOP = 1, BOTTOM = 2, FRONT = 4, BACK = 8, LEFT = 16, RIGHT = 32, AROUND = 64;
    /** The four sides about the X axis, and all six faces of the box. */
    public static final int SIDES = TOP | BOTTOM | LEFT | RIGHT, BOX = SIDES | FRONT | BACK;
    /** Axes moving together for a plain cut (X, Y, Z), and for a complex shape. */
    public static final int PLAIN_AXES = 3, COMPLEX_AXES = 5;

    public Operation {
        if (process == null) { throw new IllegalArgumentException("No process"); }
        if (feature == null) { feature = Feature.NONE; }
        if (!(removedM3 >= 0) || !(finishedM2 >= 0) || holes < 0 || !(fixedS >= 0) || grade < 1 || axes < 0 || faces < 0 || faces > (BOX | AROUND)) {
            throw new IllegalArgumentException("Bad operation " + process);
        }
    }

    /** A step of no particular feature, as 0.16 had them. */
    public Operation(Process process, double removedM3, double finishedM2, int holes, double holeDiameterM, double holeDepthM, double sizeM, int grade,
                     int axes, int faces, double fixedS) {
        this(process, removedM3, finishedM2, holes, holeDiameterM, holeDepthM, sizeM, grade, axes, faces, fixedS, Feature.NONE, false);
    }

    /** The same step making a feature. */
    public Operation as(Feature f) { return new Operation(process, removedM3, finishedM2, holes, holeDiameterM, holeDepthM, sizeM, grade, axes, faces, fixedS, f, added); }

    /** The same step, inserted into an older route (0.17). */
    public Operation inserted() { return new Operation(process, removedM3, finishedM2, holes, holeDiameterM, holeDepthM, sizeM, grade, axes, faces, fixedS, feature, true); }

    /** The same step with the axes that move together to cut it. */
    public Operation withAxes(int n) { return new Operation(process, removedM3, finishedM2, holes, holeDiameterM, holeDepthM, sizeM, grade, n, faces, fixedS, feature, added); }

    /** The step as 0.16 had it: no feature, not inserted. */
    public Operation legacy() { return new Operation(process, removedM3, finishedM2, holes, holeDiameterM, holeDepthM, sizeM, grade, axes, faces, fixedS); }

    /** The 0.7 form: complex means five axes; the faces left to the planner's default. */
    public Operation(Process process, double removedM3, double finishedM2, int holes, double holeDiameterM, double holeDepthM, double sizeM, int grade,
                     boolean complex, double fixedS) {
        this(process, removedM3, finishedM2, holes, holeDiameterM, holeDepthM, sizeM, grade, process.cutting() ? (complex ? COMPLEX_AXES : PLAIN_AXES) : 0, 0, fixedS);
    }

    /** Whether the shape needs more than three axes moving together (CNC only). */
    public boolean complex() { return axes > PLAIN_AXES; }

    /** The same step cut from these faces. */
    public Operation on(int faces) { return new Operation(process, removedM3, finishedM2, holes, holeDiameterM, holeDepthM, sizeM, grade, axes, faces, fixedS, feature, added); }

    /** A forming, joining or assembling step of a time (s). */
    public static Operation fixed(Process process, double seconds) { return new Operation(process, 0, 0, 0, 0, 0, 0, 12, 0, 0, seconds); }

    /** A cutting step removing a volume and finishing an area to a grade on a size. */
    public static Operation cut(Process process, double removedM3, double finishedM2, double sizeM, int grade, boolean complex) {
        return new Operation(process, removedM3, finishedM2, 0, 0, 0, sizeM, grade, complex, 0);
    }

    /** Drilling holes of a diameter and depth (m) to a grade. */
    public static Operation drill(int holes, double diameterM, double depthM, int grade, boolean complex) {
        return new Operation(Process.DRILL, holes * Math.PI / 4 * diameterM * diameterM * depthM, 0, holes, diameterM, depthM, diameterM, grade, complex, 0);
    }

    /** The faces a step is cut from when the planner gives none: a turned part's turning round it and both ends, its grinding round it, its boring from the front and the rest round it; a prismatic part's from the top. */
    public static int defaultFaces(Process p, boolean turnedPart) {
        if (!p.cutting()) { return 0; }
        if (!turnedPart) { return TOP; }
        return switch (p) {
            case TURN -> AROUND | FRONT | BACK;
            case BORE -> FRONT;
            default -> AROUND;
        };
    }
}
