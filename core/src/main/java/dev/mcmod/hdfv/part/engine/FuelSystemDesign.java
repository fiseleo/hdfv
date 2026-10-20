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
 * Fuel supply (供油系統): the fuel (gasoline of given research and motor octane numbers, methanol, propane,
 * isooctane or, 0.8, diesel), how it is delivered (carburettor, port injection or direct injection), injectors per
 * cylinder, fuel pressure (above the manifold for port injection, absolute for direct: the rail), and the relative
 * air-fuel ratio the system targets at full load and at part load (rich at full load cools the charge and resists
 * knock; lambda 1 at part load). A diesel injects directly at 20 MPa or more (pump-line-nozzle systems 30-100 MPa,
 * common rails 100-250 MPa); its full-load lambda is its smoke limit (1.2-2) and it has no part-load target (its load
 * is its fuel).
 */
public record FuelSystemDesign(String name, String fuel, double researchOctane, double motorOctane, Delivery delivery,
                               int injectorsPerCylinder, double fuelPressurePa, double fullLoadLambda, double partLoadLambda,
                               double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:fuel_system";

    public enum Delivery { CARBURETTOR, PORT_INJECTION, DIRECT_INJECTION }

    public FuelSystemDesign { Objects.requireNonNull(name, "name"); Objects.requireNonNull(fuel, "fuel"); Objects.requireNonNull(delivery, "delivery"); }

    @Override public String type() { return TYPE; }

    /** The fuels a design may name. */
    public static final List<String> FUELS = List.of("gasoline", "methanol", "propane", "isooctane", "diesel");

    /** The Fuel this system burns. */
    public Fuel fuelModel() {
        return switch (fuel) {
            case "gasoline" -> Fuel.gasoline(researchOctane, motorOctane);
            case "methanol" -> Fuel.METHANOL;
            case "propane" -> Fuel.PROPANE;
            case "isooctane" -> Fuel.ISOOCTANE;
            case "diesel" -> Fuel.DIESEL;
            default -> throw new IllegalArgumentException("Unknown fuel " + fuel);
        };
    }

    /** Whether the engine fires by compression (0.8). */
    public boolean diesel() { return fuel.equals("diesel"); }

    /** The fluids (FuelGrades) an engine with this system runs on: its fuel's family at no less than its octane, or diesel and kerosene. */
    public Set<String> acceptedFluids() {
        return diesel() ? dev.mcmod.hdfv.fluid.FuelGrades.forDiesel() : dev.mcmod.hdfv.fluid.FuelGrades.forSparkIgnition(fuel, researchOctane);
    }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        if (!FUELS.contains(fuel)) { issues.add(DesignIssue.invalid("fuel", "Fuel must be gasoline, methanol, propane, isooctane or diesel")); }
        if (fuel.equals("gasoline")) {
            EngineComponent.Rules.positive(issues, "researchOctane", researchOctane);
            EngineComponent.Rules.positive(issues, "motorOctane", motorOctane);
            if (motorOctane > researchOctane) { issues.add(DesignIssue.invalid("motorOctane", "A fuel's motor octane number is below its research octane number")); }
            DesignIssue.range(issues, "researchOctane", researchOctane, 80, 102, "pump and racing gasolines");
        }
        if (delivery != Delivery.CARBURETTOR) { EngineComponent.Rules.count(issues, "injectorsPerCylinder", injectorsPerCylinder, 1, 3); }
        EngineComponent.Rules.positive(issues, "fuelPressurePa", fuelPressurePa);
        if (diesel()) {
            if (!(fullLoadLambda > 1 && fullLoadLambda < 5)) { issues.add(DesignIssue.invalid("fullLoadLambda", "A diesel's full-load lambda must be between 1 and 5")); }
            else { DesignIssue.range(issues, "fullLoadLambda", fullLoadLambda, 1.2, 2.0, "smoke limits of direct-injection diesels"); }
            if (delivery != Delivery.DIRECT_INJECTION) { issues.add(DesignIssue.invalid("delivery", "A diesel injects its fuel into the cylinder: direct injection")); }
            else if (fuelPressurePa < 20e6) { issues.add(DesignIssue.invalid("fuelPressurePa", "A diesel's injection needs at least 20 MPa to atomise the fuel")); }
            else { DesignIssue.range(issues, "fuelPressurePa", fuelPressurePa, 30e6, 250e6, "pump-line-nozzle to common-rail injection"); }
        } else {
            for (String k : new String[]{"fullLoadLambda", "partLoadLambda"}) {
                double l = k.startsWith("full") ? fullLoadLambda : partLoadLambda;
                if (!(l > 0.5 && l < 2)) { issues.add(DesignIssue.invalid(k, "Lambda must be between 0.5 and 2")); }
                else { DesignIssue.range(issues, k, l, 0.75, 1.3, "spark-ignition mixtures that burn reliably"); }
            }
            if (delivery == Delivery.DIRECT_INJECTION && fuelPressurePa < 3e6) { issues.add(DesignIssue.invalid("fuelPressurePa", "Direct injection needs at least 3 MPa")); }
        }
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    /** Pump, rail and lines, kg: given, or 3 kg (an estimate). */
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 3 : massGivenKg; }

    @Override public Map<String, Object> parameters() {
        return Fields.object("fuel", fuel, "researchOctane", researchOctane, "motorOctane", motorOctane, "delivery", delivery.name().toLowerCase(),
                "injectorsPerCylinder", injectorsPerCylinder, "fuelPressurePa", fuelPressurePa, "fullLoadLambda", fullLoadLambda,
                "partLoadLambda", partLoadLambda, "massKg", EngineComponent.Rules.mass(massGivenKg));
    }

    static FuelSystemDesign read(String name, Fields f) {
        String fuel = f.string("fuel", "gasoline");
        boolean diesel = fuel.equals("diesel");
        Delivery delivery = EngineComponent.Rules.choice(f, "delivery", Delivery.class, diesel ? Delivery.DIRECT_INJECTION : Delivery.PORT_INJECTION);
        return new FuelSystemDesign(name, fuel, f.number("researchOctane", 95), f.number("motorOctane", 85), delivery,
                f.integer("injectorsPerCylinder", 1), f.number("fuelPressurePa", diesel ? 160e6 : delivery == Delivery.DIRECT_INJECTION ? 15e6 : 3e5),
                f.number("fullLoadLambda", diesel ? 1.4 : 0.88), f.number("partLoadLambda", 1.0), EngineComponent.Rules.massOr(f));
    }
}
