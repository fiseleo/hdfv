package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.Fuel;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fuel control of a scramjet (超燃燃油控制, 0.10): fuel (hydrogen or a hydrocarbon), the richest mixture it feeds
 * (equivalence ratio), whether it carries a pilot (an ignition aid: the X-51A's ethylene and TEB start), and the Mach
 * range its table covers.
 * <p>
 * Control rules (not measured): throttle sets the equivalence ratio from none to the richest, with a 0.1 s lag. The
 * inlet starts by itself within Kantrowitz's limit and stays started down to the isentropic one (AeroCore); fuel
 * that would choke the combustor unstarts it, and it restarts only by itself (the fuel cut to none first). While the
 * inlet is unstarted the control cuts the fuel itself (as the X-51A's did), so it starts again as soon as it can and the
 * fuel comes back; after an unstart the control feeds at most nine tenths of the mixture that caused it. Lit: the
 * fuel lights by itself where its ignition delay at the combustor's entry is shorter than its time in the combustor,
 * with a pilot where it is shorter than five times that; it goes out where the delay passes ten times the residence
 * time, with the ignition, when the tanks give less than half, or the throttle closes.
 */
public record ScramControlDesign(String name, String fuel, double maxEquivalenceRatio, boolean pilot, double minMach, double maxMach, double massGivenKg)
        implements EngineComponent {
    public static final String TYPE = "hdfv:scram_control";
    /** How much longer than the residence time the ignition delay may be with a pilot, and before the flame goes out. */
    public static final double PILOT = 5, FLAMEOUT = 10;

    public ScramControlDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(fuel, "fuel"); }

    @Override public String type() { return TYPE; }

    public Set<String> acceptedFluids() { return RamControlDesign.accepted(fuel); }
    public Fuel fuelModel() { return RamControlDesign.model(fuel); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!RamControlDesign.FUELS.contains(fuel)) { issues.add(DesignIssue.invalid("fuel", "Fuel must be kerosene, gasoline, propane, methanol or hydrogen")); }
        if (!(maxEquivalenceRatio > 0 && maxEquivalenceRatio <= 2)) { issues.add(DesignIssue.invalid("maxEquivalenceRatio", "The richest mixture is an equivalence ratio in (0, 2]")); }
        if (!(minMach >= 2 && maxMach > minMach + 0.5 && maxMach <= 20)) { issues.add(DesignIssue.invalid("maxMach", "The table covers Mach 2 to 20, at least half a Mach number wide")); }
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Fuel system (pump or tank pressurisation, injectors, pilot), kg: given, or 3 kg plus 1 kg per kg/s of design airflow (an estimate). */
    public double massFor(double airflowKgPerS) { return Double.isNaN(massGivenKg) ? 3 + airflowKgPerS : massGivenKg; }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("fuel", fuel, "maxEquivalenceRatio", maxEquivalenceRatio, "pilot", pilot, "minMach", minMach, "maxMach", maxMach,
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static ScramControlDesign read(String name, Fields f) {
        return new ScramControlDesign(name, f.string("fuel", "hydrogen"), f.number("maxEquivalenceRatio", 1), f.bool("pilot", false), f.number("minMach", 4),
                f.number("maxMach", 10), EngineComponent.Rules.massOr(f));
    }
}
