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
 */
public enum Room {
    HANGAR, SHIPYARD, CAR_FACTORY;

    /** The room a blueprint is built in: anything that flies (wings, rotors, air propellers, an aircraft's body) needs a hangar; a car's body or axles a car factory; the rest floats. */
    public static Room of(VehicleBlueprint b) {
        boolean car = false;
        for (VehicleBlueprint.Entry e : b.parts()) {
            PartDesign d = e.design();
            if (d instanceof WingDesign || d instanceof RotorDesign || d instanceof AirPropellerDesign
                    || d instanceof BodyDesign body && body.settings().purpose() == BodySettingsDesign.Purpose.AIRCRAFT) {
                return HANGAR;
            }
            car |= d instanceof AxleDesign || d instanceof dev.mcmod.hdfv.part.chassis.ChassisDesign
                    || d instanceof BodyDesign body && body.settings().purpose() == BodySettingsDesign.Purpose.CAR;
        }
        return car ? CAR_FACTORY : SHIPYARD;
    }
}
