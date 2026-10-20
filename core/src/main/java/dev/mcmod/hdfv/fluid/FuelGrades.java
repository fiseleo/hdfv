package dev.mcmod.hdfv.fluid;

import dev.mcmod.aerocore.api.engine.Fuel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The fuels hdfv's fluids are (0.8): each fluid's family, octane numbers and AeroCore Fuel; and which fluids an engine
 * takes. A spark-ignition engine takes its fuel's family at no less than the octane it was designed for (naphtha is
 * a 70-octane gasoline, regular 95/85, premium 98/88); a diesel takes diesel and kerosene; a gas turbine kerosene,
 * diesel and the fuel it was designed for. Gases are kept as liquids under pressure (LPG).
 */
public final class FuelGrades {
    private FuelGrades() {}

    public record Grade(String fluid, String family, double ron, double mon, Fuel fuel) {}

    private static final Map<String, Grade> GRADES = new LinkedHashMap<>();

    private static void put(String fluid, String family, double ron, double mon, Fuel fuel) { GRADES.put(fluid, new Grade(fluid, family, ron, mon, fuel)); }

    static {
        put("naphtha", "gasoline", 70, 66, Fuel.gasoline(70, 66));
        put("gasoline", "gasoline", 95, 85, Fuel.gasoline(95, 85));
        put("premium_gasoline", "gasoline", 98, 88, Fuel.gasoline(98, 88));
        put("kerosene", "kerosene", Fuel.KEROSENE.researchOctane(), Fuel.KEROSENE.motorOctane(), Fuel.KEROSENE);
        put("diesel", "diesel", Fuel.DIESEL.researchOctane(), Fuel.DIESEL.motorOctane(), Fuel.DIESEL);
        put("fuel_oil", "fuel_oil", Fuel.FUEL_OIL.researchOctane(), Fuel.FUEL_OIL.motorOctane(), Fuel.FUEL_OIL);
        put("lpg", "propane", Fuel.PROPANE.researchOctane(), Fuel.PROPANE.motorOctane(), Fuel.PROPANE);
        put("methanol", "methanol", Fuel.METHANOL.researchOctane(), Fuel.METHANOL.motorOctane(), Fuel.METHANOL);
        put(HdfvFluids.LIQUID_HYDROGEN, "hydrogen", Fuel.HYDROGEN.researchOctane(), Fuel.HYDROGEN.motorOctane(), Fuel.HYDROGEN);
        put(HdfvFluids.HYDROGEN, "hydrogen", Fuel.HYDROGEN.researchOctane(), Fuel.HYDROGEN.motorOctane(), Fuel.HYDROGEN);
    }

    /** The grade a fluid is (null: not a fuel). */
    public static Grade of(String fluid) { return GRADES.get(fluid); }

    public static Set<String> fluids() { return GRADES.keySet(); }

    /** The fuels kept as liquids at ambient temperature and pressure (what a body's integral tanks or a can hold): not hydrogen. */
    public static Set<String> ambient() {
        Set<String> out = new LinkedHashSet<>(GRADES.keySet());
        out.removeIf(f -> HdfvFluids.cryogenic(f) || HdfvFluids.compressedGas(f));
        return out;
    }

    /** Fluids a spark-ignition engine designed for a fuel ("gasoline", "methanol", "propane", "isooctane") of a research octane takes. */
    public static Set<String> forSparkIgnition(String fuel, double researchOctane) {
        Set<String> out = new LinkedHashSet<>();
        String family = switch (fuel) { case "gasoline", "isooctane" -> "gasoline"; case "propane" -> "propane"; default -> fuel; };
        double need = fuel.equals("isooctane") ? 98 : fuel.equals("gasoline") ? researchOctane : 0;
        for (Grade g : GRADES.values()) { if (g.family().equals(family) && g.ron() >= need - 1e-9) { out.add(g.fluid()); } }
        if (out.isEmpty() && family.equals("gasoline")) { out.add("premium_gasoline"); }
        return out;
    }

    /** The fluids a part burns (0.8): an engine's; null for a part that burns none. */
    public static Set<String> takenBy(dev.mcmod.aerocore.api.vehicle.PartDesign d) {
        return switch (d) {
            case dev.mcmod.hdfv.part.engine.PistonEngineDesign e -> e.acceptedFluids();
            case dev.mcmod.hdfv.part.engine.JetEngineDesign e -> e.acceptedFluids();
            case dev.mcmod.hdfv.part.engine.TurbofanDesign e -> e.acceptedFluids();
            case dev.mcmod.hdfv.part.engine.ShaftTurbineDesign e -> e.acceptedFluids();
            case dev.mcmod.hdfv.part.engine.RamjetDesign e -> e.acceptedFluids();
            case dev.mcmod.hdfv.part.engine.ScramjetDesign e -> e.acceptedFluids();
            case dev.mcmod.hdfv.part.engine.PulsejetDesign e -> e.acceptedFluids();
            case dev.mcmod.hdfv.part.engine.TurboRamjetDesign e -> e.acceptedFluids();
            default -> null;   // a booster burns its own grain, no fluid
        };
    }

    /** Fluids a compression-ignition engine takes. */
    public static Set<String> forDiesel() { return Set.of("diesel", "kerosene"); }

    private static final java.util.Map<String, Set<String>> TURBINE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Fluids a gas turbine designed for a fuel takes (the same set each time: engines ask every step). */
    public static Set<String> forTurbine(String fuel) { return TURBINE.computeIfAbsent(fuel, FuelGrades::turbine); }

    private static Set<String> turbine(String fuel) {
        Set<String> out = new LinkedHashSet<>(Set.of("kerosene", "diesel"));
        switch (fuel) {
            case "gasoline" -> out.addAll(Set.of("gasoline", "premium_gasoline"));
            case "propane" -> out.add("lpg");
            case "methanol" -> out.add("methanol");
            default -> { }
        }
        return java.util.Collections.unmodifiableSet(out);
    }
}
