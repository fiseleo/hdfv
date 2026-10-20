package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A solid rocket's ignition and mounting (點火與掛載, 0.10): whether it is cast off when burnt out (dropping as an item,
 * PartVehicle.castOff), else it stays and takes a new grain (the game's reload); and the igniter, mounts and
 * separation hardware's mass (given, or an estimate: 0.3 kg plus 2 % of the propellant).
 */
public record BoosterDesign(String name, boolean jettisonWhenBurnt, double massGivenKg) implements EngineComponent {
    public static final String TYPE = "hdfv:booster";

    public BoosterDesign { Objects.requireNonNull(name, "name"); }

    @Override public String type() { return TYPE; }

    @Override public List<DesignIssue> check() {
        List<DesignIssue> issues = new ArrayList<>();
        EngineComponent.Rules.mass(issues, massGivenKg);
        return issues;
    }

    public double massFor(double propellantKg) { return Double.isNaN(massGivenKg) ? 0.3 + 0.02 * propellantKg : massGivenKg; }
    @Override public double massKg() { return Double.isNaN(massGivenKg) ? 0 : massGivenKg; }

    @Override public Map<String, Object> parameters() { return Fields.object("jettisonWhenBurnt", jettisonWhenBurnt, "massKg", EngineComponent.Rules.mass(massGivenKg)); }

    static BoosterDesign read(String name, Fields f) { return new BoosterDesign(name, f.bool("jettisonWhenBurnt", false), EngineComponent.Rules.massOr(f)); }
}
