package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.body.SampleBodies;
import dev.mcmod.hdfv.part.drive.SampleGearboxes;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.part.wheel.SampleWheels;

import java.util.List;
import java.util.Map;

/**
 * Vehicles as an assembly room's layout holds them (0.7): parts and where they sit, no shafts or bindings (AutoWiring
 * adds those). The sample aircraft and boats, and a rear-driven sedan of the sample parts: the 2.0 l four in the
 * engine bay, the six-speed automatic behind it, the family car's axles at the body's axle points. 0.14: the engine
 * where an assembly room puts it (Placing.slideToFit: 5 cm behind the bay's point, 0.15 m farther back than it was, a
 * centimetre under the hood), the gearbox's bell on its flywheel face (Placing.behind), and no seat parts: the body seats
 * five (two in front, three behind), and the two seat parts it carried from 0.7 stood in its dashboard.
 */
public final class SampleLayouts {
    private SampleLayouts() {}

    public static Map<String, VehicleBlueprint> all() {
        return Map.of("seaplane", seaplane(), "helicopter", helicopter(), "launch", launch(), "sedan", sedan());
    }

    public static VehicleBlueprint seaplane() { return AutoWiring.stripped(SampleBlueprints.seaplane()); }
    public static VehicleBlueprint helicopter() { return AutoWiring.stripped(SampleBlueprints.helicopter()); }
    public static VehicleBlueprint launch() { return AutoWiring.stripped(SampleBlueprints.launch()); }

    public static VehicleBlueprint sedan() {
        var body = SampleBodies.sedan();
        var engine = SampleEngines.inlineFour();
        var gearbox = SampleGearboxes.sixSpeedAutomatic();
        Mount fitted = Placing.slideToFit(body, "engine_bay", engine);
        return new VehicleBlueprint("sedan", List.of(
                new VehicleBlueprint.Entry("body", body, Mount.ORIGIN),
                new VehicleBlueprint.Entry("engine", engine, Mount.on("body", "engine_bay", fitted)),
                new VehicleBlueprint.Entry("gearbox", gearbox, Mount.on("body", "engine_bay", Placing.behind(engine, fitted, gearbox))),
                new VehicleBlueprint.Entry("front_axle", SampleWheels.sedanFront(), Mount.on("body", "front_axle")),
                new VehicleBlueprint.Entry("rear_axle", SampleWheels.sedanRear(), Mount.on("body", "rear_axle"))),
                List.of());
    }
}
