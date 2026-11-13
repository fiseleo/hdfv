package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.propellant.SolidRocket;
import dev.mcmod.aerocore.api.vehicle.DesignException;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A solid rocket booster (固體火箭助推器, 0.10): its grain, casing, nozzle and ignition (AeroCore's SolidRocket: the grain
 * regresses, the chamber pressure balances the gas it makes against the throat, the nozzle expands it to the ambient
 * pressure). It points along the part's +X axis and pushes forward. Its grain is its own tank ("grain": only it burns
 * from it). Input "fire": on its rising edge with propellant in the grain it lights and burns to the end (a solid rocket
 * cannot be stopped); burnt out, it is cast off if so designed, else it waits for a new grain and the next "fire".
 * Internal states: burning, seconds since ignition, the last "fire" input.
 */
public record SolidRocketDesign(String name, GrainDesign grain, RocketCasingDesign casing, RocketNozzleDesign nozzle, BoosterDesign booster) implements PartDesign {
    public static final String TYPE = "hdfv:solid_rocket";
    /** The grain tank's name. */
    public static final String GRAIN_TANK = "grain";

    public SolidRocketDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{grain, casing, nozzle, booster}) { Objects.requireNonNull(o, "component"); }
    }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("grain", grain); m.put("casing", casing); m.put("nozzle", nozzle); m.put("booster", booster);
        return m;
    }

    public SolidRocket.Design cycle() {
        return new SolidRocket.Design(grain.propellantModel(), grain.grain(), nozzle.throatDiameterM(), nozzle.expansionRatio(),
                Math.toRadians(nozzle.divergenceHalfAngleDeg()), nozzle.characteristicVelocityEfficiency());
    }

    public double grainLengthM() { return grain.segmentLengthM() * grain.segments(); }
    public double length() { return grainLengthM() + 2 * casing.wallThicknessM() + nozzle.throatDiameterM() * (1 + Math.sqrt(nozzle.expansionRatio())); }
    public double diameter() { return grain.outerDiameterM() + 2 * casing.wallThicknessM(); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        if (nozzle.throatDiameterM() >= grain.outerDiameterM()) { issues.add(DesignIssue.invalid("nozzle.throatDiameterM", "The throat is narrower than the grain")); return issues; }
        SolidRocket.Performance p;
        try { p = performance(); } catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("cycle", e.getMessage())); return issues; }
        double burst = casing.burstPa(grain.outerDiameterM()), margin = burst / p.maxPressurePa();
        if (margin < 1) { issues.add(DesignIssue.invalid("casing.wallThicknessM", "The casing bursts at " + Math.round(burst / 1e5) / 10.0 + " MPa, under the motor's " + Math.round(p.maxPressurePa() / 1e5) / 10.0 + " MPa")); }
        else if (margin < casing.safetyFactor()) { issues.add(DesignIssue.invalid("casing.safetyFactor", "The casing keeps only " + Math.round(margin * 100) / 100.0 + " times the motor's highest pressure")); }
        DesignIssue.range(issues, "kn", cycle().kn(), 100, 600, "amateur and small motors' Kn (initial burning over throat area)");
        double throatK = 2 / (grain.propellantModel().gamma() + 1) * grain.propellantModel().flameTemperatureK();
        DesignIssue.range(issues, "throatTemperatureK", throatK, 0, nozzle.material().serviceTemperatureK(), "the nozzle's material (it erodes above it: fine for a short burn)");
        return issues;
    }

    private static final Map<String, SolidRocket.Performance> MODELS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, SolidRocket.Performance> eldest) { return size() > 32; }
    });

    /** The motor's burn, computed once per design. */
    public SolidRocket.Performance performance() { return MODELS.computeIfAbsent(Json.write(toJson()), k -> SolidRocket.perform(cycle())); }

    /** The dry mass (casing, nozzle, igniter and mounts), kg: the grain is its tank's. */
    public double dryMassKg() { return casing.massFor(grain.outerDiameterM(), grainLengthM()) + nozzle.massKg() + booster.massFor(grain.propellantKg()); }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new SolidRocketDesign(f.string("name", "booster"), TurbofanDesign.component(f, "grain", GrainDesign::read),
                TurbofanDesign.component(f, "casing", RocketCasingDesign::read), TurbofanDesign.component(f, "nozzle", RocketNozzleDesign::read),
                TurbofanDesign.component(f, "booster", BoosterDesign::read));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new Compiled(this, issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    public static final int STATE_BURNING = 0, STATE_TIME = 1, STATE_FIRE = 2;
    /** A propellant grain item's mass, kg (0.10: grains are loaded by hand into an empty booster). */
    public static final double GRAIN_ITEM_KG = 1;

    /** The grain items a full load takes (its propellant, rounded up). */
    public int grainItems() { return (int) Math.ceil(grain.propellantKg() / GRAIN_ITEM_KG - 1e-9); }
    /** What an idle booster asks of its grain (kg/s): enough to learn whether there is any. */
    static final double TRICKLE = 1e-9;

    private static final java.util.List<String> STRUCTURE = java.util.List.of(dev.mcmod.hdfv.damage.Conditions.STRUCTURE);

    private static final class Compiled implements Part {
        private final SolidRocketDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        private final List<Tank> tanks;
        private final SolidRocket.Performance performance;
        private final Set<String> propellant;
        private final double halfLength, maxThrustN;

        Compiled(SolidRocketDesign design, List<DesignIssue> issues) {
            this.design = design; this.issues = issues;
            double m = design.dryMassKg(), length = design.length(), d = design.diameter();
            mass = MassProperties.of(m, Vec3d.ZERO, InertiaTensor.box(m, new Vec3d(length, d, d)));
            GrainDesign g = design.grain();
            tanks = List.of(new Tank(GRAIN_TANK, g.volumeM3(), Vec3d.ZERO, g.propellant(), g.propellantKg(), true));
            performance = design.performance();
            double most = 0;
            for (int i = 0; i <= 50; i++) { most = Math.max(most, performance.thrustAt(performance.burnTimeS() * i / 51, 101325)); }
            maxThrustN = Math.max(most, 1e-6);
            propellant = Set.of(g.propellant());
            halfLength = length / 2;
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return mass; }
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return issues; }
        @Override public List<Tank> tanks() { return tanks; }
        @Override public List<String> inputs() { return List.of("fire"); }
        @Override public int internalStates() { return 3; }
        /** The flame: thrust over the motor's largest at sea level (0 out; RamjetDesign.SHOWN_FLAME). */
        @Override public int shownValues() { return 1 + Exhaust.COUNT; }
        /** 0.25: its casing (Conditions.STRUCTURE): damaged from outside only; a motor whose casing has failed does not ignite (no bursts are modelled). */
        @Override public java.util.List<String> conditions() { return STRUCTURE; }

        @Override public void loads(PartStep step) {
            double dt = step.dt(), fire = step.input("fire", 0);
            boolean burning = step.internal(STATE_BURNING) > 0.5;
            double t = step.internal(STATE_TIME);
            boolean rising = fire >= 0.5 && step.internal(STATE_FIRE) < 0.5;
            // whether the grain had propellant last step (a trickle is asked while idle, so an empty grain does not light)
            boolean fed = JetRuntime.fed(step);
            if (burning && !fed) { burning = false; }
            if (!burning && rising && fed && !(step.used(0) >= 1)) { burning = true; t = 0; }
            double thrust = 0, flow = 0;
            double pa = JetRuntime.air(step, new Vec3d(halfLength, 0, 0)).flight().pressurePa();
            if (burning) {
                flow = performance.massFlowAt(t);
                thrust = performance.thrustAt(t, pa);
                t += dt;
                if (t >= performance.burnTimeS()) {
                    burning = false;
                    if (design.booster().jettisonWhenBurnt()) { step.jettison(); }
                }
            }
            step.burn(propellant, burning ? Math.max(flow, TRICKLE) : TRICKLE);
            step.force(new AppliedForce(AppliedForce.Kind.THRUST, step.offset(new Vec3d(-halfLength, 0, 0)), step.axis(Vec3d.X).scale(thrust)));
            step.setInternal(STATE_BURNING, burning ? 1 : 0);
            step.setInternal(STATE_TIME, t);
            step.setInternal(STATE_FIRE, fire);
            step.show(RamjetDesign.SHOWN_FLAME, burning ? Math.clamp(thrust / maxThrustN, 0, 1) : 0);
            // 0.11: the exhaust: the propellant's flame temperature, the exit Mach number of the nozzle's expansion and its exit
            // pressure from the chamber's (an over-expanded jet separates at 0.4 of ambient, as SolidRocket.thrust has it)
            var rocket = performance.design();
            double g = rocket.propellant().gamma(), eps = rocket.expansionRatio();
            double exitMach = eps > 1 ? dev.mcmod.aerocore.api.gasdynamics.PerfectGas.machAtAreaRatio(eps, g, true) : 1;
            double pe = performance.pressureAt(Math.min(t, performance.burnTimeS())) / dev.mcmod.aerocore.api.gasdynamics.PerfectGas.totalToStaticPressure(exitMach, g);
            Exhaust.show(step, 1, burning, rocket.propellant().flameTemperatureK(), exitMach, Math.max(pe / Math.max(pa, 1), 0.4));
            step.diagnostic("thrustN", thrust);
            step.diagnostic("propellantKgPerS", flow);
            step.diagnostic("chamberPa", burning ? performance.pressureAt(t) : 0);
            step.diagnostic("burning", burning ? 1 : 0);
            step.diagnostic("burnTimeS", performance.burnTimeS());
        }
    }
}
