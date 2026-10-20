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
 * Fuel control of a ramjet (衝壓燃油控制, 0.10): fuel, the design point (Mach number, altitude, combustor temperature:
 * where the nozzle's throat is sized so the inlet runs a margin supercritical), that margin, the leanest mixture its
 * flameholders keep alight (equivalence ratio), and the slowest flight its flame lights at (Mach number).
 * <p>
 * Control rules (a schedule, not measured): throttle sets the combustor temperature from 300 K over the ram air's total
 * temperature to the combustor's largest; the fuel follows with a 0.2 s lag. With the ignition on and fuel coming it
 * lights at or above the light-off Mach number; it goes out with the ignition, when the tanks give less than half, under
 * water, below the light-off Mach number less a tenth, or when the mixture falls below the lean limit. Hydrogen burns
 * as liquid or compressed hydrogen.
 */
public record RamControlDesign(String name, String fuel, double designMach, double designAltitudeM, double designTemperatureK, double supercriticalMargin,
                               double leanLimit, double lightMach, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:ram_control";
    public static final List<String> FUELS = List.of("kerosene", "gasoline", "propane", "methanol", "hydrogen");

    public RamControlDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(fuel, "fuel"); }

    @Override public String type() { return TYPE; }

    /** The fluids it burns: a gas turbine's set for hydrocarbons; liquid and compressed hydrogen for hydrogen. */
    public Set<String> acceptedFluids() { return accepted(fuel); }

    static Set<String> accepted(String fuel) {
        return fuel.equals("hydrogen") ? Set.of("liquid_hydrogen", "hydrogen") : dev.mcmod.hdfv.fluid.FuelGrades.forTurbine(fuel);
    }

    public Fuel fuelModel() { return model(fuel); }

    static Fuel model(String fuel) {
        return switch (fuel) {
            case "kerosene" -> Fuel.KEROSENE;
            case "gasoline" -> Fuel.gasoline(95, 85);
            case "methanol" -> Fuel.METHANOL;
            case "propane" -> Fuel.PROPANE;
            case "hydrogen" -> Fuel.HYDROGEN;
            default -> throw new IllegalArgumentException("Unknown fuel " + fuel);
        };
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!FUELS.contains(fuel)) { issues.add(DesignIssue.invalid("fuel", "Fuel must be kerosene, gasoline, propane, methanol or hydrogen")); }
        if (!(designMach > 1 && designMach <= 8)) { issues.add(DesignIssue.invalid("designMach", "A ramjet is designed for Mach 1 to 8")); }
        else { DesignIssue.range(issues, "designMach", designMach, 1.8, 4.5, "ramjets in service (BOMARC, Bloodhound, Meteor)"); }
        if (!(designAltitudeM >= 0 && designAltitudeM <= 32000)) { issues.add(DesignIssue.invalid("designAltitudeM", "The design altitude is 0 to 32 km")); }
        if (!(designTemperatureK > 800 && designTemperatureK < 2800)) { issues.add(DesignIssue.invalid("designTemperatureK", "The combustor runs at 800 to 2800 K")); }
        if (!(supercriticalMargin >= 1 && supercriticalMargin <= 1.5)) { issues.add(DesignIssue.invalid("supercriticalMargin", "The margin is 1 to 1.5")); }
        if (!(leanLimit > 0 && leanLimit < 1)) { issues.add(DesignIssue.invalid("leanLimit", "The lean limit is an equivalence ratio in (0, 1)")); }
        else { DesignIssue.range(issues, "leanLimit", leanLimit, 0.25, 0.5, "V-gutter flameholders' lean blowout"); }
        if (!(lightMach >= 0 && lightMach < designMach)) { issues.add(DesignIssue.invalid("lightMach", "It must light below its design Mach number")); }
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Fuel system (pump, regulator, igniter), kg: given, or 2 kg plus 0.5 kg per kg/s of design airflow (an estimate). */
    public double massFor(double airflowKgPerS) { return Double.isNaN(massGivenKg) ? 2 + 0.5 * airflowKgPerS : massGivenKg; }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("fuel", fuel, "designMach", designMach, "designAltitudeM", designAltitudeM, "designTemperatureK", designTemperatureK,
                "supercriticalMargin", supercriticalMargin, "leanLimit", leanLimit, "lightMach", lightMach, "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static RamControlDesign read(String name, Fields f) {
        return new RamControlDesign(name, f.string("fuel", "kerosene"), f.number("designMach", 2.5), f.number("designAltitudeM", 15000),
                f.number("designTemperatureK", 2000), f.number("supercriticalMargin", 1.05), f.number("leanLimit", 0.35), f.number("lightMach", 1.2),
                EngineComponent.Rules.massOr(f));
    }
}
