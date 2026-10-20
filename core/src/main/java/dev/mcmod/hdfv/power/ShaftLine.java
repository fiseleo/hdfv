package dev.mcmod.hdfv.power;

import java.util.List;

/**
 * A line shaft's tick (0.8): one speed for the shafts and everything belted to them, in real time (the industry's
 * energy is counted in work time by whoever uses it).
 *
 * <ul>
 * <li>Governed prime movers (motors, steam engines, treadles) give what is asked up to what they have: first the
 *     bearings' friction, then what spins the line up toward its nominal speed within the tick, then the machines.</li>
 * <li>Machines take what is left up to what they ask: short of power their operators feed slower (they work at the
 *     share they get) rather than stall the line; below half the nominal speed they stop (PowerPlant.speedFactor).</li>
 * <li>Engine stands set their own speed (their engines' dynamics, EngineUnit): when any is engaged the line turns at
 *     their speed and they take whatever the others leave.</li>
 * <li>Energy is conserved: given = got + friction + dE/dt, the line's kinetic energy E taking the difference.</li>
 * </ul>
 */
public final class ShaftLine {
    private ShaftLine() {}

    /** An end: power it can give (W; prime movers), power it asks (W; loads), its inertia referred to the line (kg m^2). */
    public record End(double availableW, double demandW, double inertiaKgM2) {}

    /** The tick's result: the line's speed after it (rpm), what each end gave and got (W), the friction (W), the stands' share (W). */
    public record Result(double rpm, double[] givenW, double[] gotW, double frictionW, double standsW) {}

    /** The line's inertia: its shafts and its ends'. */
    public static double inertia(List<End> ends, int shafts) {
        double j = shafts * PowerPlant.shaftInertia();
        for (End e : ends) { j += e.inertiaKgM2(); }
        return Math.max(j, 1e-3);
    }

    /** One tick of dt seconds from a speed; standRpm is the engaged engine stands' line speed (NaN: none). */
    public static Result step(double rpm, List<End> ends, int shafts, double dt, double standRpm) {
        double j = inertia(ends, shafts);
        double friction = shafts * PowerPlant.shaftFrictionW(rpm);
        double asked = 0, available = 0;
        for (End e : ends) { asked += Math.max(e.demandW(), 0); available += Math.max(e.availableW(), 0); }
        double[] given = new double[ends.size()], got = new double[ends.size()];
        if (!Double.isNaN(standRpm)) {
            // the stands hold the speed: the governed movers give what they can of the need, the stands the rest
            double need = asked + friction, give = Math.min(available, need);
            share(ends, given, give, available);
            for (int i = 0; i < got.length; i++) { got[i] = Math.max(ends.get(i).demandW(), 0); }
            return new Result(standRpm, given, got, friction, need - give);
        }
        double w = PowerPlant.omega(rpm), wNominal = PowerPlant.omega(PowerPlant.NOMINAL_RPM);
        double energy = 0.5 * j * w * w, spinUp = w < wNominal ? 0.5 * j * (wNominal * wNominal - w * w) / dt : 0;
        double give = Math.min(available, asked + friction + spinUp);
        share(ends, given, give, available);
        double pool = give + energy / dt;               // what the tick has: the movers' and the line's own energy
        friction = Math.min(friction, pool);
        double toSpeed = Math.min(spinUp, Math.max(0, give - friction));
        double delivered = Math.min(asked, Math.max(0, give - friction - toSpeed));
        double after = Math.max(0, energy + (give - friction - delivered) * dt);
        double fraction = asked > 0 ? delivered / asked : 0;
        for (int i = 0; i < got.length; i++) { got[i] = Math.max(ends.get(i).demandW(), 0) * fraction; }
        double w2 = Math.min(Math.sqrt(2 * after / j), Math.max(w, wNominal));
        return new Result(w2 * 30 / Math.PI, given, got, friction, 0);
    }

    private static void share(List<End> ends, double[] given, double give, double available) {
        for (int i = 0; i < given.length; i++) {
            double a = Math.max(ends.get(i).availableW(), 0);
            given[i] = available > 0 ? give * a / available : 0;
        }
    }
}
