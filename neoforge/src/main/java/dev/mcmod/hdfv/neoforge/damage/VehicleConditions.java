package dev.mcmod.hdfv.neoforge.damage;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.damage.EngineWear;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.25: a vehicle's parts and how used they are, read on either side (the server's state; on a client what AeroCore
 * last sent, in whole per cent): for the status screen, the dashboard's warning lamps, the admin command and, later,
 * the repairs.
 */
public final class VehicleConditions {
    private VehicleConditions() {}

    /** One condition of a part: its name (Conditions' constants) and how much of it is used, 0 new .. 1 failed. */
    public record Item(String condition, double used) {
        /** Its localized name. */
        public Component name() { return conditionName(condition); }
        /** Whether it is past repair: the part (or that piece of it) must be replaced (Conditions.REPLACE_FROM). */
        public boolean replace() { return used >= Conditions.REPLACE_FROM; }
    }

    /** A part that has conditions: where it is installed, and each condition. */
    public record Entry(Vehicle.Installed part, List<Item> items) {
        public String id() { return part.id(); }
        /** Its localized name: its kind and its design's name ("Piston engine: inline four"). */
        public Component name() { return partName(part); }
        /** The most used of its conditions. */
        public double worst() { double w = 0; for (Item i : items) { w = Math.max(w, i.used()); } return w; }
    }

    public static Component conditionName(String condition) { return Component.translatable(Conditions.key(condition)); }

    /** A part's name: its kind (translated where the design screen knows it, else its type id) and its design's name. */
    public static MutableComponent partName(Vehicle.Installed part) {
        PartDesign d = part.part().design();
        Designs.Kind kind = Designs.kindOf(d);
        MutableComponent what = kind == null ? Component.literal(d.type()) : Component.translatable("hdfv.kind." + kind.id());
        return Component.translatable("hdfv.status.part", what, d.name());
    }

    /** The vehicle's parts that have conditions, in installed order (empty before its blueprint arrives). */
    public static List<Entry> of(PartVehicle v) {
        List<Entry> out = new ArrayList<>();
        Vehicle vehicle = v.vehicle();
        if (vehicle == null) { return out; }
        for (Vehicle.Installed p : vehicle.installed()) {
            List<String> names = p.part().conditions();
            if (names.isEmpty()) { continue; }
            List<Item> items = new ArrayList<>();
            for (String c : names) { items.add(new Item(c, v.used(p.id(), c))); }
            out.add(new Entry(p, items));
        }
        return out;
    }

    /**
     * What a dashboard warns of (thresholds as rules: where a condition starts to show or is about to fail): the engine
     * (engine, hot section, ancillaries or motor past half), the brakes (past 0.8, where their torque starts to fall),
     * a flat tyre (used up), the transmission (gears or clutch past 0.8), the structure (a body or chassis past half)
     * and a fuel leak (a leaking tank with fuel in it).
     */
    public record Warnings(boolean engine, boolean brakes, boolean tyres, boolean transmission, boolean structure, boolean leak) {
        public boolean any() { return engine || brakes || tyres || transmission || structure || leak; }
    }

    public static final double ENGINE_WARNING = 0.5, BRAKE_WARNING = 0.8, TRANSMISSION_WARNING = 0.8, STRUCTURE_WARNING = 0.5;

    public static Warnings warnings(PartVehicle v) {
        boolean engine = false, brakes = false, tyres = false, transmission = false, structure = false;
        Vehicle vehicle = v.vehicle();
        if (vehicle == null || !(v.worstUsed() > 0)) { return new Warnings(false, false, false, false, false, false); }
        for (Vehicle.Installed p : vehicle.installed()) {
            PartDesign d = p.part().design();
            for (String c : p.part().conditions()) {
                double u = v.used(p.id(), c);
                switch (c) {
                    case Conditions.ENGINE, Conditions.HOT_SECTION, Conditions.ANCILLARIES, Conditions.MOTOR -> engine |= u > ENGINE_WARNING;
                    case Conditions.BRAKES -> brakes |= u > BRAKE_WARNING;
                    case Conditions.TYRES -> tyres |= u >= 1;
                    case Conditions.GEARS, Conditions.CLUTCH -> transmission |= u > TRANSMISSION_WARNING;
                    case Conditions.STRUCTURE -> {
                        if (d instanceof BodyDesign || d instanceof ChassisDesign) { structure |= u > STRUCTURE_WARNING; }
                        else if (VehicleDamage.engineKill(p) != null) { engine |= u > ENGINE_WARNING; }
                    }
                    default -> { }
                }
            }
        }
        return new Warnings(engine, brakes, tyres, transmission, structure, leaking(v));
    }

    /** Whether a tank with fuel in it leaks (its tank or the body it is in used past EngineWear.TANK_KNEE). */
    public static boolean leaking(PartVehicle v) {
        Vehicle vehicle = v.vehicle();
        if (vehicle == null) { return false; }
        for (int k = 0; k < vehicle.tanks().size(); k++) {
            if (vehicle.tanks().get(k).tank().own() || !(v.tankKg(k) > 0)) { continue; }
            if (EngineWear.leakPerS(VehicleDamage.tankUsed(v, k)) > 0) { return true; }
        }
        return false;
    }
}
