package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.power.ShaftLine;

/** Something on a line shaft (0.8): a prime mover, a machine belted to it, a generator. Its block entity implements it. */
public interface ShaftEnd {
    /** Whether it is on the line now (a machine that drives itself electrically, or a stand without an engine, may not be). */
    default boolean onShaft() { return true; }

    /** Its part this tick at the line's speed (rpm): what it can give, what it asks (W), its inertia. */
    ShaftLine.End lineEnd(double rpm);

    /** An engine stand engaged: the line speed it holds (rpm); NaN otherwise. */
    default double standRpm() { return Double.NaN; }

    /** A stand's rated power, for sharing the line's load among stands (W). */
    default double standRatedW() { return 0; }

    /** The tick's outcome: the line's speed (rpm), what it gave and got (W), and for an engaged stand the load it takes next (W). */
    void settle(double rpm, double givenW, double gotW, double standLoadW);
}
