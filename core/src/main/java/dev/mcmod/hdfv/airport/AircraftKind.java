package dev.mcmod.hdfv.airport;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.air.RotorDesign;
import dev.mcmod.hdfv.part.air.WingDesign;

/**
 * What a vehicle is to a control tower (0.20): an aeroplane (it has a wing), a rotorcraft (a rotor, and no wing) or
 * ground traffic. Not Room.of: that also puts propeller-driven sleds and air-cushion boats in a hangar.
 */
public enum AircraftKind {
    FIXED_WING, ROTORCRAFT, GROUND;

    public boolean flies() { return this != GROUND; }

    public static AircraftKind of(VehicleBlueprint blueprint) {
        boolean rotor = false;
        for (VehicleBlueprint.Entry e : blueprint.parts()) {
            PartDesign d = e.design();
            if (d instanceof WingDesign) { return FIXED_WING; }
            rotor |= d instanceof RotorDesign;
        }
        return rotor ? ROTORCRAFT : GROUND;
    }
}
