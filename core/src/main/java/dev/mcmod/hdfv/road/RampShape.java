package dev.mcmod.hdfv.road;

import java.util.List;

/**
 * The shape of a road ramp block (0.25): a slope rising one block over 2, 4 or 8 blocks of run, a block a part
 * (part 0 at the foot: part k rises from k / gradient to (k + 1) / gradient of a block across its metre). The block
 * collides as STEPS steps along the run, each a sixteenth of a block long and as high as the slope at its middle
 * (AeroCore's BoxGround.stairs offline): wheels' rays meet the steps and find their edges (WheelCorner), feet walk up
 * them. The model drawn is the smooth wedge.
 *
 * <p>The steps are not rounded to sixteenths of height: a 1/16 m riser is a kerb to a wheel, and on the offline track a
 * car pushed with 1.3 times a 1:4 or a 1:8 slope's pull stalls halfway up such a run, while it climbs these steps in
 * the time it climbs the smooth slope (1:8) or within a tenth of it (1:4, 1:2; RampDriveTest). Steps are still not a
 * slope to AeroCore 0.41's wheels (a ray meets treads and risers, never a tilted face): coasting loses more than the
 * height's energy on the steeper runs.
 */
public final class RampShape {
    private RampShape() {}

    /** Steps along a block's run. */
    public static final int STEPS = 16;
    /** The gradients made: a block of rise over this many of run. */
    public static final List<Integer> GRADIENTS = List.of(2, 4, 8);
    /** The pavements made: hdfv's asphalt and the airfield's apron concrete. */
    public static final List<String> MATERIALS = List.of("asphalt", "concrete");

    /** A ramp block's id: asphalt_ramp_4. */
    public static String id(String material, int gradient) { return material + "_ramp_" + gradient; }

    /** Every ramp block's id, the materials in turn, the steepest first. */
    public static List<String> ids() {
        List<String> out = new java.util.ArrayList<>();
        for (String m : MATERIALS) { for (int g : GRADIENTS) { out.add(id(m, g)); } }
        return out;
    }

    private static void check(int gradient, int part) {
        if (gradient < 1 || part < 0 || part >= gradient) { throw new IllegalArgumentException("Part " + part + " of a 1:" + gradient + " ramp"); }
    }

    /** The slope's height (blocks) at a place along a part's run (0 its low edge, 1 its high edge). */
    public static double height(int gradient, int part, double along) {
        check(gradient, part);
        return (part + along) / gradient;
    }

    /**
     * The heights of a part's steps in blocks, the low end first: the slope's height at each step's middle. Each step
     * (in a part and from one part to the next) is 1 / (STEPS x gradient) of a block above the one before.
     */
    public static double[] steps(int gradient, int part) {
        check(gradient, part);
        double[] out = new double[STEPS];
        for (int i = 0; i < STEPS; i++) { out[i] = height(gradient, part, (i + 0.5) / STEPS); }
        return out;
    }
}
