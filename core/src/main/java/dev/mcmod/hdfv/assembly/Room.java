package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.air.AirPropellerDesign;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodySettingsDesign;
import dev.mcmod.hdfv.part.wheel.AxleDesign;

/**
 * Where a vehicle is put together (0.7, the user: "飛機、船、車要不同組裝室"): an aircraft in a hangar, a boat in a
 * shipyard, a car in a car factory. A seaplane is an aircraft: its wings make it one.
 * <p>
 * 0.26 (the user: "新的露天重機組裝場"): a machine (any part of dev.mcmod.hdfv.part.machine: tracks, drums, links, work
 * tools, hooks, hydraulics) is built in the open heavy yard: a floor with the sky over it and a gantry crane's rails or
 * a crane parked beside it for the heavy parts. A small one (at most SMALL_MACHINE_KG, compact equipment: a mini
 * excavator, a skid steer, a backhoe loader) may also be built in a car factory, and a car in the yard.
 */
public enum Room {
    HANGAR, SHIPYARD, CAR_FACTORY, HEAVY_YARD;

    /** 0.26: a machine this light may be built in a car factory too (compact equipment's usual upper class, kg: a rule). */
    public static final double SMALL_MACHINE_KG = 10_000;

    /**
     * The room a blueprint is built in: anything that flies (wings, rotors, air propellers, an aircraft's body) needs a
     * hangar; (0.26) a machine's parts the heavy yard; a car's body or axles a car factory; the rest floats.
     */
    public static Room of(VehicleBlueprint b) {
        boolean car = false, machine = false;
        for (VehicleBlueprint.Entry e : b.parts()) {
            PartDesign d = e.design();
            if (d instanceof WingDesign || d instanceof RotorDesign || d instanceof AirPropellerDesign
                    || d instanceof BodyDesign body && body.settings().purpose() == BodySettingsDesign.Purpose.AIRCRAFT) {
                return HANGAR;
            }
            car |= d instanceof AxleDesign || d instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign
                    || d instanceof BodyDesign body && body.settings().purpose() == BodySettingsDesign.Purpose.CAR;
            machine |= machinePart(d);
        }
        return machine ? HEAVY_YARD : car ? CAR_FACTORY : SHIPYARD;
    }

    /** 0.26: whether a design is a machine's part (its package, dev.mcmod.hdfv.part.machine). */
    public static boolean machinePart(PartDesign d) { return d.getClass().getPackageName().equals("dev.mcmod.hdfv.part.machine"); }

    /** 0.26: whether this room builds a vehicle planned for a room (a small machine in a car factory, a car in the yard; massKg the vehicle's). */
    public boolean builds(Room planned, double massKg) {
        return this == planned || this == CAR_FACTORY && planned == HEAVY_YARD && massKg <= SMALL_MACHINE_KG || this == HEAVY_YARD && planned == CAR_FACTORY;
    }
}
