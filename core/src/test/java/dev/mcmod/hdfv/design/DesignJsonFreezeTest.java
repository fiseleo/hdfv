package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.18: the bodies, chassis and wheels of 0.16's samples and sample and test vehicles write the same JSON as they did
 * (design-json-0.16.txt: Designs.write's hash, recorded before 0.18's car fronts, cab-over cabs and truck chassis).
 * New parameters stay out of a design's JSON at their defaults, so old blueprints, workpieces' hashes and the 0.17
 * route snapshot see the same designs. Other kinds of part are another session's to change.
 */
class DesignJsonFreezeTest {
    static { HdfvParts.register(); }

    static final Set<String> TYPES = Set.of("hdfv:body", "hdfv:chassis", "hdfv:axle", "hdfv:landing_gear");

    static Map<String, PartDesign> designs() {
        Map<String, PartDesign> out = new LinkedHashMap<>();
        for (Designs.Sample s : Designs.SAMPLES) { out.put("sample:" + s.id(), s.design().get()); }
        Map<String, VehicleBlueprint> vehicles = new LinkedHashMap<>();
        vehicles.put("sedan", dev.mcmod.hdfv.part.SampleBlueprints.sedan());
        vehicles.put("seaplane", dev.mcmod.hdfv.part.SampleBlueprints.seaplane());
        vehicles.put("helicopter", dev.mcmod.hdfv.part.SampleBlueprints.helicopter());
        vehicles.put("engine_boat", dev.mcmod.hdfv.part.SampleBlueprints.engineBoat());
        vehicles.put("jet_boat", dev.mcmod.hdfv.part.SampleBlueprints.jetBoat());
        vehicles.put("fan_boat", dev.mcmod.hdfv.part.SampleBlueprints.fanBoat());
        vehicles.put("turbine_boat", dev.mcmod.hdfv.part.SampleBlueprints.turbineBoat());
        vehicles.put("pulsejet_boat", dev.mcmod.hdfv.part.SampleBlueprints.pulsejetBoat());
        vehicles.put("manual_sedan", dev.mcmod.hdfv.drive.TestCars.manualSedan());
        vehicles.put("four_by_four", dev.mcmod.hdfv.drive.TestCars.fourByFour());
        vehicles.put("six_by_six", dev.mcmod.hdfv.drive.TestCars.sixBySix());
        for (var v : vehicles.entrySet()) {
            for (VehicleBlueprint.Entry e : v.getValue().parts()) { out.putIfAbsent("vehicle:" + v.getKey() + ":" + e.id(), e.design()); }
        }
        return out;
    }

    @Test void oldBodiesChassisAndWheelsWriteTheSameJson() throws IOException {
        Map<String, String> recorded = new LinkedHashMap<>();
        try (InputStream in = getClass().getResourceAsStream("/design-json-0.16.txt")) {
            assertNotNull(in, "the record");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String[] kv = line.trim().split(" ");
                if (kv.length == 2) { recorded.put(kv[0], kv[1]); }
            }
        }
        Map<String, PartDesign> now = designs();
        int checked = 0;
        for (var e : recorded.entrySet()) {
            PartDesign d = now.get(e.getKey());
            assertNotNull(d, e.getKey() + " is still there");
            if (!TYPES.contains(d.type())) { continue; }
            assertEquals(e.getValue(), Integer.toHexString(Designs.write(d).hashCode()), e.getKey() + " writes the JSON it wrote in 0.16");
            checked++;
        }
        assertTrue(checked >= 15, "the bodies, chassis and wheels checked: " + checked);
    }
}
