package dev.mcmod.hdfv.repair;

import dev.mcmod.hdfv.damage.Conditions;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 0.25: repairs on the spot (the user 2026-11-12: a repair shop plus repairs where the vehicle is), with a hand kit held
 * on the vehicle for a few seconds (as eating: the player keeps looking at it). Enough to limp home, not a repair:
 *
 * <ul>
 * <li>The field repair kit: a condition of structure, ancillaries (hoses, wiring, a radiator patched) or a tank (a
 *     leak plugged), by at most STEP a use and never below FLOOR; nothing from Conditions.REPLACE_FROM (that is the
 *     shop's replacement). Brakes, a clutch, gears, an engine's internals, a motor, blades, suspension and struts need
 *     the shop's machines and parts: no kit mends them.</li>
 * <li>The tyre patch kit: a flat tyre (used up: a puncture, a burst) plugged and pumped up to FLOOR in one use; worn
 *     tread cannot be patched (a tyre that is not flat is refused).</li>
 * </ul>
 *
 * A use costs a point of the kit's durability (Kit.uses in all). The kit works on the part nearest the point the player
 * looks at among those it can do anything for; with none, it says why for the nearest one it could have worked on.
 */
public final class FieldRepair {
    private FieldRepair() {}

    /** The most one use mends, and the least used a kit leaves a condition (rules: "enough to limp home"). */
    public static final double STEP = 0.15, FLOOR = 0.5;

    public enum Kit {
        FIELD_REPAIR_KIT(Set.of(Conditions.STRUCTURE, Conditions.ANCILLARIES, Conditions.TANK), 8, 100),
        TYRE_PATCH_KIT(Set.of(Conditions.TYRES), 4, 60);

        /** What it works on, how many uses it has, how long a use takes (ticks of holding it on the vehicle). */
        public final Set<String> conditions;
        public final int uses, ticks;

        Kit(Set<String> conditions, int uses, int ticks) {
            this.conditions = conditions;
            this.uses = uses;
            this.ticks = ticks;
        }

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Why a kit does nothing for a condition: not its kind, past repair in the field, already as good as the field makes it, a tyre that is not flat. */
    public static final String KIND = "kind", REPLACE = "replace", ENOUGH = "enough", NOT_FLAT = "not_flat";

    /** One use on a condition: how used it is after, or the refusal (one of the reasons; after is then the used as it was). */
    public record Outcome(double before, double after, String refusal) {
        public boolean done() { return refusal == null; }
    }

    /** What one use of a kit does to a condition at so much used. */
    public static Outcome use(Kit kit, String condition, double used) {
        if (!kit.conditions.contains(condition)) { return new Outcome(used, used, KIND); }
        if (kit == Kit.TYRE_PATCH_KIT) {
            return used >= 1 - 1e-9 ? new Outcome(used, FLOOR, null) : new Outcome(used, used, NOT_FLAT);
        }
        if (used >= Conditions.REPLACE_FROM) { return new Outcome(used, used, REPLACE); }
        if (used <= FLOOR + 1e-9) { return new Outcome(used, used, ENOUGH); }
        return new Outcome(used, Math.max(FLOOR, used - STEP), null);
    }

    /** A part as the kit finds it: its id, how far its centre is from the look point (m), its conditions and how used each is. */
    public record Candidate(String part, double distanceM, List<String> conditions, List<Double> used) {}

    /** What a use does: the part and condition worked on and the outcome (a refusal when there is nothing it can do: part null when no part has a condition of its kinds). */
    public record Choice(String part, String condition, Outcome outcome) {}

    /**
     * The part and condition a kit works on: of the parts it can mend something on, the nearest, and on it the most used
     * condition it mends; else the refusal for the nearest part with a condition of the kit's kinds (its most used one).
     */
    public static Choice choose(Kit kit, List<Candidate> parts) {
        Choice best = null, refused = null;
        double bestD = Double.MAX_VALUE, refusedD = Double.MAX_VALUE;
        for (Candidate c : parts) {
            Choice mine = null, mineRefused = null;
            for (int i = 0; i < c.conditions().size(); i++) {
                String name = c.conditions().get(i);
                if (!kit.conditions.contains(name)) { continue; }
                Outcome o = use(kit, name, c.used().get(i));
                if (o.done()) {
                    if (mine == null || o.before() > mine.outcome().before()) { mine = new Choice(c.part(), name, o); }
                } else if (mineRefused == null || o.before() > mineRefused.outcome().before()) {
                    mineRefused = new Choice(c.part(), name, o);
                }
            }
            if (mine != null && c.distanceM() < bestD) { best = mine; bestD = c.distanceM(); }
            if (mine == null && mineRefused != null && c.distanceM() < refusedD) { refused = mineRefused; refusedD = c.distanceM(); }
        }
        if (best != null) { return best; }
        return refused != null ? refused : new Choice(null, null, new Outcome(0, 0, KIND));
    }

    /** Whether a kit can do anything for a condition at so much used (the status screen's "on the spot"). */
    public static Kit kitFor(String condition, double used) {
        for (Kit k : Kit.values()) { if (use(k, condition, used).done()) { return k; } }
        return null;
    }
}
