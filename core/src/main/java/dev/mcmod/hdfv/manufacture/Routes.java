package dev.mcmod.hdfv.manufacture;

import java.util.ArrayList;
import java.util.List;

/**
 * Moving workpieces made on an older route onto the current one (0.17; the user chose that a half-made workpiece goes on
 * at the matching step of the new route, doing any step inserted after what it has done).
 *
 * The current route is the old one with steps inserted (Operation.added) and features added; the old route is
 * therefore the current one without its inserted steps (legacy()). A piece that had done s old steps has done the
 * old step s - 1, which sits at some index i of the current route: it goes on at i + 1, so the steps inserted after
 * that one are still to do. Nothing is ever inserted before a route's first step (its forming), so a piece always
 * keeps that. A finished piece stays finished (its old count of steps is kept with it).
 *
 * VERSION is the route version new workpieces carry; workpieces without one were made on 0.16's routes (version 0).
 */
public final class Routes {
    private Routes() {}

    public static final int VERSION = 1;

    /** The route a workpiece had before version 1: the current one without its inserted steps, as those steps were. */
    public static List<Operation> legacy(Workpiece w) {
        List<Operation> out = new ArrayList<>();
        for (Operation op : w.route()) { if (!op.added()) { out.add(op.legacy()); } }
        return out;
    }

    /** Where each old step is in the current route: old index -> current index. */
    static int[] places(Workpiece w) {
        int[] at = new int[legacy(w).size()];
        int k = 0;
        for (int i = 0; i < w.route().size(); i++) { if (!w.route().get(i).added()) { at[k++] = i; } }
        return at;
    }

    /**
     * The step a workpiece goes on at in the current route, having done some steps of an old route of a length (the
     * item's own count). A piece that had finished the old route stays finished: the current route's length.
     */
    public static int migrate(Workpiece w, int doneSteps, int oldSteps) {
        int[] at = places(w);
        int n = at.length;
        if (doneSteps <= 0) { return 0; }
        if (doneSteps >= oldSteps) { return w.route().size(); }
        if (n == 0) { return 0; }
        return at[Math.min(doneSteps, n) - 1] + 1;
    }
}
