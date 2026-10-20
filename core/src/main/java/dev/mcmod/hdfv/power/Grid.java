package dev.mcmod.hdfv.power;

import java.util.List;

/**
 * An electric network's share of a tick (0.8): what each producer gives and each consumer gets.
 *
 * <ul>
 * <li>Consumers first, from the generators, then from the stores (batteries) for what the generators lack; then the
 *     stores charge from what the generators have left.</li>
 * <li>All through the network's capacity (its smallest cable over the tick); the line loses lossAtCapacity of what it
 *     carries at full capacity, in proportion to the square of the load (I^2 R): what the consumers get is what the
 *     producers gave less that.</li>
 * <li>Each producer gives, and each consumer gets, in proportion to what it offered or asked.</li>
 * </ul>
 */
public final class Grid {
    private Grid() {}

    /** A producer's offer or a consumer's ask over the tick (FE), and whether it is a store. */
    public record Party(double fe, boolean store) {}

    /** What each producer gave, each consumer got, and the line lost (FE). */
    public record Flows(double[] given, double[] got, double lost) {
        public double totalGiven() { double s = 0; for (double g : given) { s += g; } return s; }
        public double totalGot() { double s = 0; for (double g : got) { s += g; } return s; }
    }

    public static Flows share(List<Party> producers, List<Party> consumers, double capacityFe, double lossAtCapacity) {
        double gen = 0, stores = 0, use = 0, charge = 0;
        for (Party p : producers) { if (p.store()) { stores += Math.max(p.fe(), 0); } else { gen += Math.max(p.fe(), 0); } }
        for (Party c : consumers) { if (c.store()) { charge += Math.max(c.fe(), 0); } else { use += Math.max(c.fe(), 0); } }
        double cap = Math.max(capacityFe, 0);
        double t1 = Math.min(gen, Math.min(use, cap));
        double t2 = Math.min(stores, Math.min(use - t1, cap - t1));
        double t3 = Math.min(gen - t1, Math.min(charge, cap - t1 - t2));
        double carried = t1 + t2 + t3, load = cap > 0 ? carried / cap : 0, lossFraction = Math.clamp(lossAtCapacity * load * load, 0, 1);
        double[] given = new double[producers.size()], got = new double[consumers.size()];
        for (int i = 0; i < given.length; i++) {
            Party p = producers.get(i);
            double o = Math.max(p.fe(), 0);
            given[i] = p.store() ? (stores > 0 ? t2 * o / stores : 0) : (gen > 0 ? (t1 + t3) * o / gen : 0);
        }
        for (int i = 0; i < got.length; i++) {
            Party c = consumers.get(i);
            double a = Math.max(c.fe(), 0);
            got[i] = (1 - lossFraction) * (c.store() ? (charge > 0 ? t3 * a / charge : 0) : (use > 0 ? (t1 + t2) * a / use : 0));
        }
        return new Flows(given, got, lossFraction * carried);
    }
}
