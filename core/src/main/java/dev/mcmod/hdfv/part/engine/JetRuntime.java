package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.HullDynamics;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.Gas;
import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.api.vehicle.PartStep;

/**
 * What the two-spool gas turbines (turbofan, turboprop, turboshaft) share at run time: the air at the inlet, the
 * burner's lighting rules (JetEngineDesign's), and the matching solution kept in internal states so the next step
 * starts from it; and their fuel from the vehicle's tanks (0.8): while the ignition is on they ask for what they burn (a
 * trickle while unlit, so dry tanks keep them from lighting) and go out when it does not come.
 */
final class JetRuntime {
    private JetRuntime() {}

    /** Fuel valve lag, s; how long a surge lasts, s; flame-out speed of the core (fraction); fuel per unit speed error (of the design fuel flow). */
    static final double FUEL_LAG = 0.1, SURGE_TIME = 0.6, FLAMEOUT = 0.1, SPEED_GAIN = 1.5;
    /** The starter cuts out at half speed at most; its torque falls to none this far above cut-out. */
    static final double STARTER_CUTOUT = 0.5, STARTER_FREE_RUN = 0.15;
    /** How far the start schedule's speed may run ahead of the core, and fall behind it (fractions of design speed). */
    static final double SCHEDULE_LEAD = 0.05, SCHEDULE_LAG = 0.02;
    /** Share of the flow and power a surging compressor keeps (a rule for the stall's effect). */
    static final double SURGE_SHARE = 0.3;
    /** What an engine asks for while its ignition is on and it burns nothing yet, kg/s (dry tanks then keep it from lighting). */
    static final double PRIME_KG_PER_S = 1e-6;
    /** Length of the matching solution (AeroCore's two-spool unknowns). */
    static final int SOLUTION = 7;

    /**
     * 0.25: a gas turbine's conditions (Part.conditions; rules in damage.EngineWear): "hot_section" wears by creep with the
     * turbine entry temperature against the control's limit (more with reheat), costs thrust and power, and holds no
     * flame at 1; "ancillaries" (fuel control, ignition, oil and electrics) are damaged from outside only, cost power
     * from half used, and at 1 the engine neither runs nor starts.
     */
    static final int HOT_SECTION = 0, ANCILLARIES = 1;
    static final java.util.List<String> CONDITIONS = java.util.List.of(dev.mcmod.hdfv.damage.Conditions.HOT_SECTION, dev.mcmod.hdfv.damage.Conditions.ANCILLARIES);

    /** What its conditions leave an engine this step: the share of its thrust and shaft power, and whether it is dead. */
    record Health(double share, boolean dead) {
        static final Health NEW = new Health(1, false);
    }

    /** The engine's health from its conditions; NEW (and nothing computed) while neither shows yet, so a new engine steps as before 0.25. */
    static Health health(PartStep step) {
        double hot = step.used(HOT_SECTION), ancillaries = step.used(ANCILLARIES);
        boolean tired = hot >= dev.mcmod.hdfv.damage.EngineWear.SHOWS_FROM;
        if (!tired && !(ancillaries > dev.mcmod.hdfv.damage.EngineWear.ANCILLARIES_KNEE)) { return Health.NEW; }
        double share = dev.mcmod.hdfv.damage.EngineWear.ancillaries(ancillaries);
        if (tired) { share *= dev.mcmod.hdfv.damage.EngineWear.hotSection(hot); }
        return new Health(share, hot >= 1 || ancillaries >= 1);
    }

    /** Wears the hot section for a step lit at a turbine entry temperature (pace: DamagePace.wear() as the part was compiled). */
    static void wear(PartStep step, double pace, boolean lit, double ratedK, double turbineInletK, boolean reheat) {
        if (lit && pace > 0) { step.wear(HOT_SECTION, dev.mcmod.hdfv.damage.EngineWear.hotSection(step.dt() * pace, ratedK, turbineInletK, reheat)); }
    }

    /** The flight condition at the inlet (ambient static state, speed into it along the part's +X) and whether the inlet is under water. */
    record Air(Turbojet.Flight flight, boolean drowned) {}

    static Air air(PartStep step, Vec3d inletOffset) {
        Vec3d axis = step.axis(Vec3d.X), point = step.point(inletOffset);
        HullDynamics.Water sample = step.environment().point(point);
        FluidState air = sample.air();
        boolean drowned = sample.hasWater() && point.y() < sample.surfaceY();
        double t0 = air.speedOfSoundMps() * air.speedOfSoundMps() / (1.4 * Gas.AIR.gasConstant());
        double p0 = air.densityKgPerM3() * Gas.AIR.gasConstant() * t0;
        double v0 = Math.max(0, step.velocity(inletOffset).subtract(air.velocity()).dot(axis));
        return new Air(new Turbojet.Flight(t0, p0, v0), drowned);
    }

    /**
     * The burner after this step (0 out, 1 lit with the starter still helping, 2 running on its own) from its state,
     * the ignition switch, water at the inlet and the core's speed (fraction): it lights at the light-off speed, the
     * starter cuts out for good at its cut-out speed, it goes out with the ignition, under water or below 10 %.
     */
    static int burner(double state, double ignition, boolean drowned, double coreSpeed, double lightOff, double cutout) {
        return burner(state, ignition, true, drowned, coreSpeed, lightOff, cutout);
    }

    /** The same, out too while the tanks gave less than half of what it asked for last step. */
    static int burner(double state, double ignition, boolean fed, boolean drowned, double coreSpeed, double lightOff, double cutout) {
        boolean lit = state > 0.5, running = state > 1.5;
        if (ignition < 0.5 || !fed || drowned || (lit && coreSpeed < FLAMEOUT)) { return 0; }
        if (!lit && coreSpeed >= lightOff) { lit = true; }
        if (lit && coreSpeed >= cutout) { running = true; }
        return running && lit ? 2 : lit ? 1 : 0;
    }

    /** Whether the tanks gave what it asked for last step (at least half). */
    static boolean fed(PartStep step) { return step.fuelSupply() >= 0.5; }

    /** Asks the tanks for this step's fuel (kg/s), a trickle while the ignition is on and it burns none. */
    static void burn(PartStep step, java.util.Set<String> fluids, double ignition, double fuelKgPerS) {
        step.burn(fluids, ignition >= 0.5 ? Math.max(fuelKgPerS, PRIME_KG_PER_S) : Math.max(fuelKgPerS, 0));
    }

    /** Starter cut-out for a core idling at a speed (fractions of design): half speed, or nine tenths of idle if lower. */
    static double cutout(double coreIdle) { return Math.min(STARTER_CUTOUT, 0.9 * coreIdle); }

    /** The starter's torque at a core speed: its stall torque falling linearly to none 15 % above cut-out (an air turbine starter's line). */
    static double starter(double stallTorqueNm, double coreSpeed, double cutout) {
        return stallTorqueNm * Math.max(0, 1 - Math.max(0, coreSpeed) / (cutout + STARTER_FREE_RUN));
    }

    /**
     * The start schedule's core speed after a step: rising at the start rate (fraction of design per second) from where
     * it was, never more than 5 % ahead of the core or 2 % behind it; the core's own speed while the burner is out.
     */
    static double schedule(double previous, double coreSpeed, double rate, double dt, boolean lit) {
        if (!lit) { return coreSpeed; }
        return Math.min(Math.max(previous, coreSpeed - SCHEDULE_LAG) + rate * dt, coreSpeed + SCHEDULE_LEAD);
    }

    /** The matching solution kept from internal state first on, or null before the first. */
    static double[] solution(PartStep step, int first) {
        double[] x = new double[SOLUTION];
        for (int i = 0; i < SOLUTION; i++) { x[i] = step.internal(first + i); }
        return x[2] > 0 ? x : null;   // the face flow is positive in any solution
    }

    static void keep(PartStep step, int first, double[] x) {
        for (int i = 0; i < SOLUTION; i++) { step.setInternal(first + i, x == null ? 0 : x[i]); }
    }
}
