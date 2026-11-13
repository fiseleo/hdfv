package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.CollisionSphere;
import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.engine.Scramjet;
import dev.mcmod.aerocore.api.engine.Turbojet;
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

/**
 * A dual-mode scramjet (超燃衝壓引擎, 0.10) assembled from its inlet and isolator, combustor and nozzle, and fuel control
 * (AeroCore's Scramjet, run from its table: Scramjet.Map, built in the background). It points along the part's +X axis;
 * it gives thrust only well above Mach 3, where its inlet starts. Inputs "throttle" 0..1 (the equivalence ratio) and
 * "ignition"; rules in ScramControlDesign. Internal states: started, lit, equivalence ratio, the richest mixture the
 * control feeds after an unstart (0: none yet).
 */
public record ScramjetDesign(String name, ScramInletDesign inlet, ScramCombustorDesign combustor, ScramControlDesign control) implements PartDesign {
    public static final String TYPE = "hdfv:scramjet";
    static final double FUEL_LAG = 0.1;
    /** The equivalence ratio below which the fuel counts as cut (an unstarted inlet restarts). */
    static final double RESTART_PHI = 0.02;

    public ScramjetDesign {
        Objects.requireNonNull(name, "name");
        for (Object o : new Object[]{inlet, combustor, control}) { Objects.requireNonNull(o, "component"); }
    }

    public java.util.Set<String> acceptedFluids() { return control.acceptedFluids(); }

    @Override public String type() { return TYPE; }

    public Map<String, EngineComponent> components() {
        Map<String, EngineComponent> m = new LinkedHashMap<>();
        m.put("inlet", inlet); m.put("combustor", combustor); m.put("control", control);
        return m;
    }

    public Scramjet.Design cycle() {
        return new Scramjet.Design(inlet.captureAreaM2(), inlet.designMach(), inlet.angles(), inlet.throatAreaM2(), inlet.internalContraction(),
                inlet.kineticEnergyEfficiency(), inlet.isolatorLengthOverHeight(), combustor.lengthM(), combustor.areaRatio(), combustor.frictionFactor(),
                combustor.combustionEfficiency(), combustor.nozzleAreaRatio(), combustor.nozzleVelocityCoefficient(), combustor.injectantVelocityRatio(),
                control.fuelModel());
    }

    public double length() { return inlet.lengthM() + inlet.isolatorLengthOverHeight() * Math.sqrt(inlet.throatAreaM2()) + 2 * combustor.lengthM(); }
    public double width() { return inlet.widthM(); }

    /** The ram air's total temperature at the top of its range at 25 km (216.65 K): what the uncooled walls must take. */
    public double hottestRamK() { return 221.65 * (1 + 0.2 * control.maxMach() * control.maxMach()); }

    /**
     * The lowest flight Mach number (to a tenth, up to 12) at which the inlet starts by itself (Kantrowitz's limit behind
     * its ramps; AeroCore's Scramjet.started); infinite if it never does.
     */
    public double selfStartMach() {
        Scramjet.Design d = cycle();
        for (int tenth = 15; tenth <= 120; tenth++) { if (Scramjet.started(d, tenth / 10.0, false)) { return tenth / 10.0; } }
        return Double.POSITIVE_INFINITY;
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) {
            for (DesignIssue i : e.getValue().check()) { issues.add(i.under(e.getKey())); }
        }
        if (issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID)) { return issues; }
        double ram = hottestRamK();
        if (ram > inlet.material().serviceTemperatureK()) {
            DesignIssue.range(issues, "rampTemperatureK", ram, 0, inlet.material().serviceTemperatureK(), "the ramps' material (they glow and are cooled by radiation; above it they burn through in time)");
        }
        if (!combustor.wallsHold(ram, control.fuel())) {
            issues.add(DesignIssue.invalid("combustor.fuelCooled", "The combustor's walls cannot take the " + Math.round(ram) + " K ram air at Mach " + control.maxMach()
                    + (combustor.fuelCooled() ? " with a hydrocarbon's heat sink" : " uncooled")));
        }
        try {
            cycle();
            // an inlet that starts only above its range never starts in flight (unless pushed past it)
            DesignIssue.range(issues, "selfStartMach", selfStartMach(), 0, control.maxMach(), "inlets that start by themselves in their range (Kantrowitz)");
        } catch (IllegalArgumentException e) { issues.add(DesignIssue.invalid("cycle", e.getMessage())); }
        return issues;
    }

    /** Mass of the engine, kg. */
    public double totalMassKg() {
        double air = 0.0184 * 7 * 295 * inlet.captureAreaM2();   // a capture's airflow at Mach 7, 30 km (kg/s), for the fuel system
        return inlet.massKg() + combustor.massFor(inlet.throatAreaM2()) + control.massFor(air);
    }

    @Override public Map<String, Object> parameters() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components().entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }

    public static PartDesign read(Fields f) {
        return new ScramjetDesign(f.string("name", "scramjet"), TurbofanDesign.component(f, "inlet", ScramInletDesign::read),
                TurbofanDesign.component(f, "combustor", ScramCombustorDesign::read), TurbofanDesign.component(f, "control", ScramControlDesign::read));
    }

    @Override public Part compile() {
        List<DesignIssue> issues = check();
        DesignException.throwIfInvalid(issues);
        return new Compiled(this, issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList());
    }

    private static final Map<String, Scramjet.Map> MODELS = java.util.Collections.synchronizedMap(new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Scramjet.Map> eldest) { return size() > 16; }
    });

    /** The engine's table over its Mach range (about a third of a second to build), shared by every vehicle and bench that runs it. */
    public Scramjet.Map model() {
        return MODELS.computeIfAbsent(Json.write(toJson()), k -> Scramjet.Map.build(cycle(), control.minMach(), control.maxMach(), control.maxEquivalenceRatio()));
    }

    /** A step of the control: the point the engine runs at, and its started and lit states after it. */
    public record Running(Scramjet.Point point, boolean started, boolean lit) {}

    /**
     * The engine at a flight condition and equivalence ratio, from its states before (the control's rules): the inlet's
     * start, the fuel lighting by its ignition delay, unstart by too much fuel, flame-out.
     */
    public Running run(Scramjet.Map map, Turbojet.Flight flight, double phi, boolean wantLit, boolean wasStarted, boolean wasLit) {
        Scramjet.Design d = map.design();
        double mach = flight.mach();
        // an unstarted inlet starts again (within Kantrowitz's limit) only with the fuel cut
        boolean started = mach >= control.minMach() - 0.5 && Scramjet.started(d, mach, wasStarted) && (wasStarted || phi < RESTART_PHI);
        if (!started) { return new Running(Scramjet.at(d, flight, 0, false, false), false, false); }
        Scramjet.Point unlit = map.at(flight, 0, false);
        if (!wantLit || phi <= 0) { return new Running(unlit, true, false); }
        Scramjet.Point lit = map.at(flight, phi, true);
        if (lit.mode() == Scramjet.Mode.UNSTART) { return new Running(Scramjet.at(d, flight, 0, false, false), false, false); }
        double delay = d.fuel().ignitionDelayS(Math.max(200, lit.combustorEntryK()), Math.max(1, lit.combustorEntryPa())), residence = Math.max(lit.residenceTimeS(), 1e-9);
        double allowed = wasLit ? ScramControlDesign.FLAMEOUT : control.pilot() ? ScramControlDesign.PILOT : 1;
        return delay <= allowed * residence ? new Running(lit, true, true) : new Running(unlit, true, false);
    }

    public static final int STATE_STARTED = 0, STATE_LIT = 1, STATE_PHI = 2, STATE_PHI_LIMIT = 3;

    private static final java.util.List<String> STRUCTURE = java.util.List.of(dev.mcmod.hdfv.damage.Conditions.STRUCTURE);

    private static final class Compiled implements Part {
        private final ScramjetDesign design;
        private final List<DesignIssue> issues;
        private final MassProperties mass;
        private final double halfLength;
        private volatile Scramjet.Map model;

        Compiled(ScramjetDesign design, List<DesignIssue> issues) {
            this.design = design; this.issues = issues;
            double m = design.totalMassKg(), length = design.length(), w = design.width();
            mass = MassProperties.of(m, Vec3d.ZERO, InertiaTensor.box(m, new Vec3d(length, w, w)));
            halfLength = length / 2;
        }

        @Override public PartDesign design() { return design; }
        @Override public MassProperties mass() { return mass; }
        @Override public List<CollisionSphere> collision() { return List.of(); }
        @Override public List<DesignIssue> issues() { return issues; }
        @Override public List<String> inputs() { return List.of("throttle", "ignition"); }
        @Override public int internalStates() { return 4; }
        /** The flame: its mixture over the richest (0 unlit; RamjetDesign.SHOWN_FLAME). */
        @Override public int shownValues() { return 1 + Exhaust.COUNT; }
        /** 0.25: its duct (Conditions.STRUCTURE): damaged from outside only; less thrust, none at 1 (damage.EngineWear). */
        @Override public java.util.List<String> conditions() { return STRUCTURE; }
        @Override public void warmUp() { model(); }

        Scramjet.Map model() {
            Scramjet.Map m = model;
            if (m == null) { synchronized (this) { if (model == null) { model = design.model(); } m = model; } }
            return m;
        }

        @Override public void loads(PartStep step) {
            Scramjet.Map map = model();
            ScramControlDesign c = design.control();
            double dt = step.dt();
            JetRuntime.Air air = JetRuntime.air(step, new Vec3d(halfLength, 0, 0));
            Turbojet.Flight flight = air.flight();
            double throttle = Math.clamp(step.input("throttle", 0), 0, 1), ignition = step.input("ignition", 1);
            double used = step.used(0);   // 0.25: a failed duct holds no flame
            if (used >= 1) { ignition = 0; }
            boolean wasStarted = step.internal(STATE_STARTED) > 0.5;
            double phi = Math.max(0, step.internal(STATE_PHI)), limit = step.internal(STATE_PHI_LIMIT);
            double richest = limit > 0 ? Math.min(limit, c.maxEquivalenceRatio()) : c.maxEquivalenceRatio();
            // an unstarted inlet: the control cuts the fuel until it starts again
            double aim = wasStarted ? throttle * richest : 0;
            phi += (aim - phi) * Math.min(1, dt / FUEL_LAG);
            boolean want = ignition >= 0.5 && JetRuntime.fed(step) && !air.drowned() && throttle > 0;
            Running r = flight.mach() < 1.5 || air.drowned() ? null
                    : design.run(map, flight, phi, want, wasStarted, step.internal(STATE_LIT) > 0.5);
            if (wasStarted && r != null && !r.started() && flight.mach() >= c.minMach() && phi > RESTART_PHI) { limit = 0.9 * phi; }   // fuel unstarted it
            double fuel = r != null && r.lit() ? r.point().fuelKgPerS() : 0;
            JetRuntime.burn(step, c.acceptedFluids(), ignition, fuel);
            double thrust = r == null ? 0 : r.point().thrustN();
            if (used > 0 && thrust > 0) { thrust *= dev.mcmod.hdfv.damage.EngineWear.structure(used); }
            step.force(new AppliedForce(AppliedForce.Kind.THRUST, step.offset(new Vec3d(-halfLength, 0, 0)), step.axis(Vec3d.X).scale(thrust)));
            step.setInternal(STATE_STARTED, r != null && r.started() ? 1 : 0);
            step.setInternal(STATE_LIT, r != null && r.lit() ? 1 : 0);
            step.setInternal(STATE_PHI, phi);
            step.setInternal(STATE_PHI_LIMIT, limit);
            step.show(RamjetDesign.SHOWN_FLAME, r != null && r.lit() ? Math.clamp(phi / c.maxEquivalenceRatio(), 0, 1) : 0);
            // 0.11: the exhaust, estimated for the plume (the map gives no exit state): the combustor's entry heated by the
            // burnt fuel (about 1700 K at a stoichiometric mixture), leaving at about half the flight Mach number from a ramp
            // nozzle that leaves it somewhat under-expanded
            boolean burning = r != null && r.lit();
            Exhaust.show(step, 1, burning, burning ? r.point().combustorEntryK() + 1700 * Math.min(1, phi) : 0, Math.clamp(0.5 * flight.mach(), 1.2, 4), 1.5);
            step.diagnostic("thrustN", thrust);
            step.diagnostic("fuelKgPerS", fuel);
            step.diagnostic("mach", flight.mach());
            step.diagnostic("equivalenceRatio", r != null && r.lit() ? phi : 0);
            step.diagnostic("mode", r == null ? -1 : r.point().mode().ordinal());
            step.diagnostic("started", r != null && r.started() ? 1 : 0);
            step.diagnostic("lit", r != null && r.lit() ? 1 : 0);
            step.diagnostic("combustorEntryK", r == null ? 0 : r.point().combustorEntryK());
            step.diagnostic("combustorEntryMach", r == null ? 0 : r.point().combustorEntryMach());
        }
    }
}
