package dev.mcmod.hdfv.manufacture;

import java.util.List;

/**
 * Setups on a CNC (0.8): the faces a job's steps are cut from decide how many clampings it takes on a machine
 * (CncConfig.setups); each is a fixture and its zero prepared once per batch (PREPARE_S, a rule: programs are made with
 * the drawing) and a load, clamp and probe per piece (clampS: two minutes, three seconds more a kilogram for the crane
 * and indicating, a rule).
 */
public final class Setups {
    private Setups() {}

    public static final double PREPARE_S = 600, CLAMP_S = 120, CLAMP_S_PER_KG = 3;

    /** The faces of steps from..from+n of a route, together. */
    public static int faces(List<Operation> route, int from, int n) {
        int f = 0;
        for (int i = from; i < from + n && i < route.size(); i++) { if (route.get(i).process().cutting()) { f |= route.get(i).faces(); } }
        return f;
    }

    /** Clampings of steps from..from+n of a workpiece on a machine. */
    public static int count(Workpiece w, int from, int n, CncConfig c) { return c.setups(faces(w.route(), from, n)); }

    /** Clampings of a workpiece's whole cutting route on a machine (0 when it has none). */
    public static int count(Workpiece w, CncConfig c) { return count(w, 0, w.route().size(), c); }

    /** Load, clamp and probe one piece of a stock mass (kg), s. */
    public static double clampS(double kg) { return CLAMP_S + CLAMP_S_PER_KG * Math.max(kg, 0); }
}
