package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.engine.SampleEngines;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The record of 0.16's routes (routes-0.16.json, written once on 0.16's planners at the start of 0.17; H0): every
 * sample design's workpieces, their steps with all their fields and which stations could do each. RouteMigrationTest
 * checks the routes of 0.17 against it: their old steps unchanged, everything that could do them still can.
 * The file is not rewritten: it records what workpieces in old worlds were made by.
 */
final class RouteSnapshot {
    private RouteSnapshot() {}

    private static final Path ROOT = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize();
    static final Path FILE = ROOT.resolve("core/src/test/resources/routes-0.16.json");

    /** The designs whose routes are kept: the samples, the older engine the machine tests make, every part of the sample and test vehicles. */
    static Map<String, PartDesign> designs() {
        Map<String, PartDesign> out = new LinkedHashMap<>();
        for (Designs.Sample s : Designs.SAMPLES) { out.put("sample:" + s.id(), s.design().get()); }
        PartDesign old = Designs.set(SampleEngines.inlineFour(), "fuelSystem", "delivery", "carburettor");
        old = Designs.set(old, "piston", "diameterM", "85.92");
        old = Designs.set(old, "rod", "bigEndBoreM", "48.06");
        out.put("old_four", Designs.set(old, "rod", "smallEndBoreM", "22.02"));
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

    /** The places that can do a step of a workpiece: stations, CNC presets and full manual tools (their names, as 0.16 named them). */
    static List<String> doers(Operation op, Workpiece w) {
        List<String> out = new ArrayList<>();
        for (Station s : Station.values()) {
            if (s.family) { continue; }
            if (ToolConfig.STATIONS.contains(s)) {
                ToolConfig t = ToolConfig.full(s);
                boolean can = op.process().cutting() ? t.fits(w) && t.can(op, w) : s.does(op.process());
                if (can) { out.add(s.name()); }
            } else if (s == Station.CNC) {
                for (int k = 0; k < CncConfig.PRESETS.size(); k++) { if (CncConfig.PRESETS.get(k).can(op)) { out.add("CNC" + k); } }
            } else if (s.does(op.process()) && (!op.process().cutting() || s.can(op))) {
                out.add(s.name());
            }
        }
        return out;
    }
}
