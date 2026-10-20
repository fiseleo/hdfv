package dev.mcmod.hdfv.manufacture;

/**
 * Chips back as stock (0.23; the user chose "更真實" and "平衡" for the parts' material): what a cutting step removes from
 * a metal workpiece comes back as stock bits for the furnace, less what is lost (fines in the coolant, grinding swarf
 * too fine to remelt, the oxidised share). Machine shops recycle most of their swarf; RECOVERY (0.8) is a rule, a
 * server setting. Composites, wood and rubber give nothing back. Routes are unchanged: only what the machine hands out.
 */
public final class Chips {
    private Chips() {}

    /** The share of the removed metal that comes back (the default of the server setting chipRecovery). */
    public static final double RECOVERY = 0.8;

    /** Metal a step of a workpiece gives back at a recovery (0..1), kg (a step's removal is per piece). */
    public static double kg(Operation op, Workpiece w, double recovery) {
        if (!op.process().cutting() || !w.alloy().family.metal()) { return 0; }
        return Math.max(op.removedM3(), 0) * w.alloy().density * Math.clamp(recovery, 0, 1);
    }
}
