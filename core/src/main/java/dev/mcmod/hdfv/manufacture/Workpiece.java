package dev.mcmod.hdfv.manufacture;

import java.util.List;

/**
 * One kind of manufactured component of a part (0.7): its key (for its item and translation: "block", "crank",
 * "piston"), how many the part needs, its material and starting form, the finished mass and the stock it takes (each,
 * kg), and its route of steps in order; and (0.9) its size, the stock's envelope a machine tool has to take.
 */
public record Workpiece(String key, int count, Alloy alloy, Form form, double finishedKg, double stockKg, List<Operation> route, Envelope envelope) {
    public Workpiece {
        route = List.copyOf(route);
        if (count < 1 || !(finishedKg >= 0) || !(stockKg >= finishedKg - 1e-12)) { throw new IllegalArgumentException("Bad workpiece " + key); }
        if (envelope == null) { envelope = Envelope.estimate(stockKg / alloy.density, false); }
    }

    /**
     * The translation key of a piece's name: a body module's pieces ("m3_skin") share their kind's ("module_skin"),
     * (0.15) a chassis station's ("station2_cross") theirs ("station_cross").
     */
    public static String nameKey(String key) {
        return "hdfv.workpiece." + key.replaceAll("^m\\d+_", "module_").replaceAll("^station\\d+_", "station_");
    }

    /** A workpiece whose size is estimated from its stock (a cube of its volume). */
    public Workpiece(String key, int count, Alloy alloy, Form form, double finishedKg, double stockKg, List<Operation> route) {
        this(key, count, alloy, form, finishedKg, stockKg, route, null);
    }

    /**
     * A workpiece's size (0.9), m: its length, width and height; a turned part's width and height are its diameter
     * (what a lathe swings).
     */
    public record Envelope(double lengthM, double widthM, double heightM, boolean turned) {
        public Envelope {
            if (!(lengthM > 0) || !(widthM > 0) || !(heightM > 0)) { throw new IllegalArgumentException("A size is positive"); }
        }
        public static Envelope box(double lengthM, double widthM, double heightM) { return new Envelope(lengthM, widthM, heightM, false); }
        public static Envelope turned(double lengthM, double diameterM) { return new Envelope(lengthM, diameterM, diameterM, true); }
        /** A turned part's diameter (else its larger cross dimension). */
        public double diameterM() { return Math.max(widthM, heightM); }
        /** The three sizes, largest first. */
        public double[] sorted() {
            double[] d = {lengthM, widthM, heightM};
            java.util.Arrays.sort(d);
            return new double[]{d[2], d[1], d[0]};
        }
        /** An envelope for a volume of stock (m^3) when the planner gives none: a bar three diameters long if turned, else a cube. */
        public static Envelope estimate(double volumeM3, boolean turned) {
            double v = Math.max(volumeM3, 1e-9);
            if (turned) { double d = Math.cbrt(4 * v / (3 * Math.PI)); return turned(3 * d, d); }
            double a = Math.cbrt(v);
            return box(a, a, a);
        }
    }

    /** Whether a manual machine tool can make it (no step needs CNC). */
    public boolean manual() {
        for (Operation op : route) {
            if (!op.process().cutting()) { continue; }
            boolean any = false;
            for (Station s : Station.CUTTING) { any |= s.manual && s.can(op, this); }
            if (!any) { return false; }
        }
        return true;
    }

    /** The steps a CNC has to do because no manual machine can. */
    public List<Operation> cncOnly() {
        return route.stream().filter(op -> op.process().cutting()).filter(op -> {
            for (Station s : Station.CUTTING) { if (s.manual && s.can(op, this)) { return false; } }
            return true;
        }).toList();
    }
}
