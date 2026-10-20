package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.InertiaTensor;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.hdfv.part.engine.EngineComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** Helpers shared by the airframe designs: reading components, collecting their issues, box-shaped mass. */
public final class Components {
    private Components() {}

    /** A component written as {"type", "name", "parameters"} (or its parameters alone) under a key. */
    public static <T> T read(Fields f, String key, BiFunction<String, Fields, T> reader) {
        Fields c = f.child(key);
        return reader.apply(c.string("name", key), c.has("parameters") ? c.child("parameters") : c);
    }

    /** Every component's issues under its key. */
    public static List<DesignIssue> issues(Map<String, EngineComponent> components) {
        List<DesignIssue> out = new ArrayList<>();
        for (Map.Entry<String, EngineComponent> e : components.entrySet()) {
            for (DesignIssue i : e.getValue().check()) { out.add(i.under(e.getKey())); }
        }
        return out;
    }

    public static boolean invalid(List<DesignIssue> issues) { return issues.stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID); }

    public static List<DesignIssue> extrapolations(List<DesignIssue> issues) { return issues.stream().filter(i -> i.kind() == DesignIssue.Kind.EXTRAPOLATION).toList(); }

    /** A uniform box of a mass and size at a centre. */
    public static MassProperties box(double massKg, Vec3d centre, Vec3d size) {
        double m = Math.max(massKg, 1e-6);
        return MassProperties.of(m, centre, InertiaTensor.box(m, new Vec3d(Math.max(size.x(), 1e-3), Math.max(size.y(), 1e-3), Math.max(size.z(), 1e-3))));
    }

    public static Map<String, Object> parameters(Map<String, EngineComponent> components) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, EngineComponent> e : components.entrySet()) { m.put(e.getKey(), e.getValue().toJson()); }
        return m;
    }
}
