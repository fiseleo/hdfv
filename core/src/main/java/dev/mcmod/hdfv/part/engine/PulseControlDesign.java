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
 * A pulsejet's fuel system (脈衝噴射燃油系統, 0.10): fuel, the most its valve feeds (kg/s), the chamber's mixing time
 * (about its length over the speed of the jets coming in: a few milliseconds), the combustion efficiency, and whether
 * it carries its own starting blower.
 * <p>
 * Rules: throttle opens the fuel valve (0.1 s lag). It starts as the V-1's did on its ramp: with the ignition on, fuel
 * flowing and air blown through it (input "start": the starting air and the spark), or with the ram air of flight
 * (above 60 m/s); it lights if its cycle there runs (the table: AeroCore's Pulsejet.Map). Running, it keeps going as long
 * as the cycle does (enough fuel and not too fast, about Mach 0.6); it goes out with the ignition, when the tanks give
 * less than half, or under water.
 */
public record PulseControlDesign(String name, String fuel, double maxFuelKgPerS, double mixingTimeS, double combustionEfficiency, double massGivenKg)
        implements EngineComponent {
    public static final String TYPE = "hdfv:pulse_control";
    public static final List<String> FUELS = List.of("gasoline", "kerosene", "propane", "methanol", "hydrogen");
    /** Above this airspeed (m/s) the ram air starts it, as starting air would. */
    public static final double AIRSTART_MPS = 60;

    public PulseControlDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(fuel, "fuel"); }

    @Override public String type() { return TYPE; }

    /** The fluids it burns: any of its family (pulsejets take low-grade fuel: the V-1 burned 75-octane gasoline). */
    public Set<String> acceptedFluids() {
        return switch (fuel) {
            case "gasoline" -> Set.of("naphtha", "gasoline", "premium_gasoline", "kerosene");
            case "kerosene" -> Set.of("kerosene", "diesel");
            case "propane" -> Set.of("lpg");
            case "methanol" -> Set.of("methanol");
            case "hydrogen" -> Set.of("liquid_hydrogen", "hydrogen");
            default -> Set.of(fuel);
        };
    }

    public Fuel fuelModel() { return fuel.equals("gasoline") ? Fuel.gasoline(70, 60) : RamControlDesign.model(fuel); }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!FUELS.contains(fuel)) { issues.add(DesignIssue.invalid("fuel", "Fuel must be gasoline, kerosene, propane, methanol or hydrogen")); }
        EngineComponent.Rules.positive(issues, "maxFuelKgPerS", maxFuelKgPerS);
        if (!(mixingTimeS > 2e-4 && mixingTimeS < 0.05)) { issues.add(DesignIssue.invalid("mixingTimeS", "The mixing time is 0.2 to 50 ms")); }
        else { DesignIssue.range(issues, "mixingTimeS", mixingTimeS, 0.002, 0.008, "the pulsejets the model was checked on (Argus 3 ms, hobby valveless 3-6 ms)"); }
        if (!(combustionEfficiency > 0 && combustionEfficiency <= 1)) { issues.add(DesignIssue.invalid("combustionEfficiency", "Efficiency must be in (0, 1]")); }
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Fuel system (pump or pressure tank, nozzles, spark plug), kg: given, or 1 kg plus 10 kg per kg/s of fuel (an estimate). */
    public double massFor() { return Double.isNaN(massGivenKg) ? 1 + 10 * maxFuelKgPerS : massGivenKg; }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("fuel", fuel, "maxFuelKgPerS", maxFuelKgPerS, "mixingTimeS", mixingTimeS, "combustionEfficiency", combustionEfficiency,
                "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static PulseControlDesign read(String name, Fields f) {
        return new PulseControlDesign(name, f.string("fuel", "gasoline"), f.number("maxFuelKgPerS", 0.3), f.number("mixingTimeS", 0.003),
                f.number("combustionEfficiency", 0.9), EngineComponent.Rules.massOr(f));
    }
}
