package dev.mcmod.hdfv.damage;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.damage.Corrosion;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartTypes;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.vehicle.part.TransferPart;
import dev.mcmod.aerocore.api.vehicle.part.TransmissionPart;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.drive.TestCars;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.air.SampleAirframes;
import dev.mcmod.hdfv.part.drive.SampleGearboxes;
import dev.mcmod.hdfv.part.wheel.SteerSchedule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: the table of conditions (Conditions) and what a hit does by it (Impacts): every part type is in it or out of it
 * on purpose; the sample sedan against a rigid wall at three speeds (the calibration of the toughness factors); what a
 * crash does to those aboard; parts that wrap AeroCore's name its conditions; and a vehicle saved before 0.25 reads back
 * with its parts' states and everything new.
 */
class ConditionsTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    /** The part types that have conditions, and those that have none on purpose. */
    static final Set<String> WITH = Set.of("hdfv:axle", "hdfv:landing_gear", "hdfv:manual_gearbox", "hdfv:automatic", "hdfv:dual_clutch", "hdfv:cvt", "hdfv:transfer_case",
            "hdfv:electric_motor", "hdfv:wing", "hdfv:control_surface", "hdfv:air_propeller", "hdfv:rotor", "hdfv:propeller", "hdfv:body", "hdfv:chassis",
            "hdfv:piston_engine", "hdfv:jet_engine", "hdfv:turbofan", "hdfv:turboprop", "hdfv:turboshaft", "hdfv:turbo_ramjet", "hdfv:ramjet", "hdfv:scramjet",
            "hdfv:pulsejet", "hdfv:solid_rocket", "hdfv:fuel_tank", "hdfv:storage_box");
    static final Set<String> WITHOUT = Set.of("hdfv:float", "hdfv:hull", "hdfv:planing_hull", "hdfv:box", "hdfv:ballast", "hdfv:seat", "hdfv:fifth_wheel",
            "hdfv:landing_legs", "hdfv:component");

    /** Every design the game offers or the samples carry, by type. */
    static Map<String, List<PartDesign>> designs() {
        Map<String, List<PartDesign>> out = new LinkedHashMap<>();
        List<PartDesign> all = new ArrayList<>();
        for (Designs.Kind k : Designs.KINDS) { all.add(k.fresh().get()); }
        for (Designs.Sample s : Designs.SAMPLES) { all.add(s.design().get()); }
        List<VehicleBlueprint> vehicles = new ArrayList<>(SampleBlueprints.all().values());
        vehicles.addAll(List.of(TestCars.manualSedan(), TestCars.fourByFour(), TestCars.sixBySix(), TestCars.tractor6x4(), TestCars.lowLoader(), TestCars.rearSteerSedan()));
        for (VehicleBlueprint b : vehicles) { for (VehicleBlueprint.Entry e : b.parts()) { all.add(e.design()); } }
        for (PartDesign d : all) { out.computeIfAbsent(d.type(), t -> new ArrayList<>()).add(d); }
        return out;
    }

    @Test void everyPartTypeIsInTheTableOrOutOfItOnPurpose() {
        Map<String, List<PartDesign>> designs = designs();
        Set<String> missing = new TreeSet<>(PartTypes.types());
        missing.removeIf(t -> !t.startsWith("hdfv:"));
        for (String type : missing) { assertTrue(WITH.contains(type) != WITHOUT.contains(type), type + " is neither listed as having conditions nor as having none"); }
        missing.removeAll(designs.keySet());
        assertTrue(missing.isEmpty(), "no design of these types was looked at: " + missing);
        int parts = 0;
        for (Map.Entry<String, List<PartDesign>> e : designs.entrySet()) {
            for (PartDesign d : e.getValue()) {
                if (d.check().stream().anyMatch(i -> i.kind() == dev.mcmod.aerocore.api.vehicle.DesignIssue.Kind.INVALID)) { continue; }
                Part p = PartTypes.compile(d);
                Conditions.Profile profile = Conditions.of(p);
                String what = e.getKey() + " " + d.name();
                if (WITHOUT.contains(e.getKey())) {
                    assertTrue(p.conditions().isEmpty(), what + " has conditions: " + p.conditions());
                    assertNull(profile, what);
                    continue;
                }
                parts++;
                assertFalse(p.conditions().isEmpty(), what + " has no conditions");
                assertEquals(p.conditions().size(), Set.copyOf(p.conditions()).size(), what + " names a condition twice");
                for (String c : p.conditions()) { assertTrue(Conditions.NAMES.contains(c), what + ": " + c + " has no name"); }
                assertNotNull(profile, what + " is not in the table");
                assertFalse(profile.crash().isEmpty(), what + ": a crash reaches nothing");
                double most = 0;
                for (Conditions.Hit h : profile.crash()) {
                    assertTrue(p.conditions().contains(h.condition()), what + ": a crash reaches " + h.condition() + ", which it does not have");
                    assertTrue(h.factor() > 0 && h.factor() <= 1, what + " " + h);
                    most = Math.max(most, h.factor());
                }
                assertEquals(1, most, 0, what + ": the weakest condition takes the part's damage whole");
                assertTrue(profile.toughnessJPerKg() > 50 && profile.toughnessJPerKg() < 50_000, what + " toughness " + profile.toughnessJPerKg());
                assertTrue(profile.reachM() >= Conditions.MIN_REACH_M && profile.reachM() < 40, what + " reach " + profile.reachM());
                assertNotNull(profile.material(), what);
                if (profile.corrodes() != null) {
                    assertTrue(p.conditions().contains(profile.corrodes()), what + " corrodes " + profile.corrodes());
                    assertTrue(profile.wallMm() > 0.05 && profile.wallMm() < 100, what + " wall " + profile.wallMm() + " mm");
                    assertTrue(profile.metal() != Corrosion.Metal.NONE, what);
                } else {
                    assertEquals(Corrosion.Metal.NONE, profile.metal(), what);
                }
            }
        }
        assertTrue(parts > 60, "designs with conditions looked at: " + parts);
        for (String c : Conditions.FIRE.keySet()) { assertTrue(Conditions.NAMES.contains(c), c); }
    }

    /** What a wall crash of the sedan at a speed (km/h; a dead stop on its nose) adds to each "part.condition". */
    static Map<String, Double> wall(Vehicle sedan, double kmh) {
        double speed = kmh / 3.6, mass = sedan.properties().massKg(), nose = -1e9;
        for (dev.mcmod.aerocore.api.CollisionSphere s : sedan.collision()) { nose = Math.max(nose, s.centerLocal().x() + s.radiusM()); }
        Map<String, Double> out = new HashMap<>();
        for (Impacts.Dealt d : Impacts.crash(sedan, new Vec3d(nose, 0.5, 0), mass * speed, speed, 1)) { out.merge(d.partId() + "." + d.condition(), d.amount(), Double::sum); }
        return out;
    }

    /**
     * The calibration (the user 2026-11-12: realistic by default): the sample sedan stopping dead against a rigid wall,
     * head on. At 15 km/h (a parking bump, the insurers' low-speed test) nothing loses 3 %; at 50 km/h (the full-width
     * barrier test) a quarter to a third of the body is used, the front suspension is clearly damaged and the engine's
     * ancillaries so far gone that it is not fit to drive far; at 100 km/h the car is a write-off.
     */
    @Test void theSedanAgainstAWall() {
        Vehicle sedan = SampleBlueprints.sedan().compile();
        Map<String, Double> slow = wall(sedan, 15), test = wall(sedan, 50), fast = wall(sedan, 100);
        System.out.println("sedan, " + Math.round(sedan.properties().massKg()) + " kg, against a wall at 15 km/h: " + new java.util.TreeMap<>(slow));
        System.out.println("at 50 km/h: " + new java.util.TreeMap<>(test));
        System.out.println("at 100 km/h: " + new java.util.TreeMap<>(fast));
        for (Map.Entry<String, Double> e : slow.entrySet()) { assertTrue(e.getValue() < 0.03, "15 km/h: " + e); }
        assertTrue(wall(sedan, 7.9).isEmpty(), "under the elastic speed nothing");
        double body = test.get("body.structure");
        assertTrue(body > 0.20 && body < 0.35, "50 km/h, body: " + body);
        assertTrue(test.get("engine.ancillaries") >= 0.40 && test.get("engine.ancillaries") < 0.6, "50 km/h, engine ancillaries: " + test.get("engine.ancillaries"));
        assertTrue(test.get("front_axle.suspension") > 0.2 && test.get("front_axle.suspension") < 0.5, "50 km/h, front suspension: " + test.get("front_axle.suspension"));
        assertTrue(test.get("engine.engine") < 0.5 * test.get("engine.ancillaries"), "the block takes less than what is bolted to it");
        assertTrue(test.get("rear_axle.suspension") < 0.05, "50 km/h, rear suspension: " + test.get("rear_axle.suspension"));
        assertTrue(test.getOrDefault("gearbox.gears", 0.0) < 0.02, "50 km/h, gears: " + test.get("gearbox.gears"));
        assertTrue(fast.get("body.structure") > Conditions.REPLACE_FROM, "100 km/h, body: " + fast.get("body.structure"));
        assertTrue(fast.get("engine.ancillaries") >= 1 && fast.get("front_axle.suspension") >= 1, "100 km/h: " + fast);
        // a hit from behind harms the back, not the engine in front
        Map<String, Double> rear = new HashMap<>();
        for (Impacts.Dealt d : Impacts.crash(sedan, new Vec3d(0, 0.5, 0), sedan.properties().massKg() * 50 / 3.6, 50 / 3.6, 1)) { rear.put(d.partId() + "." + d.condition(), d.amount()); }
        assertTrue(rear.get("rear_axle.suspension") > 5 * rear.getOrDefault("front_axle.suspension", 1e-9) && rear.getOrDefault("engine.ancillaries", 0.0) < 0.05, "from behind: " + rear);
        // the server's scale: half the energy, half the damage; none at 0
        double halved = 0;
        for (Impacts.Dealt d : Impacts.crash(sedan, new Vec3d(4.9, 0.5, 0), sedan.properties().massKg() * 50 / 3.6, 50 / 3.6, 0.5)) { if (d.partId().equals("body")) { halved = d.amount(); } }
        assertEquals(0.5, halved / wallBody(sedan), 0.02);
        assertTrue(Impacts.crash(sedan, new Vec3d(4.9, 0.5, 0), 1e5, 30, 0).isEmpty());
    }

    private static double wallBody(Vehicle sedan) {
        for (Impacts.Dealt d : Impacts.crash(sedan, new Vec3d(4.9, 0.5, 0), sedan.properties().massKg() * 50 / 3.6, 50 / 3.6, 1)) { if (d.partId().equals("body")) { return d.amount(); } }
        return Double.NaN;
    }

    /** Health lost aboard: nothing in a bump, a few points in the 50 km/h test, all but dead at 100 km/h; a used-up body protects less. */
    @Test void aCrashHurtsThoseAboardByTheirChangeOfVelocity() {
        double m = 1400;
        assertEquals(0, Impacts.injury(m * 15 / 3.6, m, 0), 0, "15 km/h");
        double test = Impacts.injury(m * 50 / 3.6, m, 0.26), fast = Impacts.injury(m * 100 / 3.6, m, 1);
        System.out.println("health lost aboard: 50 km/h " + test + ", 100 km/h " + fast + ", 30 km/h " + Impacts.injury(m * 30 / 3.6, m, 0.1));
        assertTrue(test > 2 && test < 6, "50 km/h: " + test);
        assertTrue(fast > 17 && fast <= 20, "100 km/h: " + fast);
        assertTrue(Impacts.injury(m * 70 / 3.6, m, 1) > 1.5 * Impacts.injury(m * 70 / 3.6, m, 0), "a body used up lets it all through");
        // a rebound's impulse counts: bouncing back at half the speed is a greater change of velocity
        assertTrue(Impacts.injury(1.5 * m * 50 / 3.6, m, 0) > Impacts.injury(m * 50 / 3.6, m, 0));
        assertEquals(0, Impacts.injury(0, m, 0), 0);
    }

    @Test void partsThatWrapAeroCoresNameItsConditions() {
        // a speed-scheduled axle
        Part rear = null;
        for (Vehicle.Installed i : TestCars.rearSteerSedan().compile().installed()) { if (i.part() instanceof SteerSchedule) { rear = i.part(); } }
        assertNotNull(rear, "the rear-steer sedan has a scheduled axle");
        assertEquals(List.of(AxlePart.TYRES, AxlePart.BRAKES, AxlePart.SUSPENSION), rear.conditions());
        assertNotNull(SteerSchedule.axleOf(rear).wearing());
        // the four gearboxes and the transfer cases under their rules
        for (PartDesign g : List.of(SampleGearboxes.fiveSpeedManual(), SampleGearboxes.sixSpeedAutomatic(), SampleGearboxes.sevenSpeedDualClutch(), SampleGearboxes.cvt())) {
            assertEquals(List.of(TransmissionPart.GEARS, TransmissionPart.CLUTCH), g.compile().conditions(), g.name());
        }
        assertEquals(List.of(TransferPart.GEARS), SampleGearboxes.partTime().compile().conditions());
        assertEquals(List.of(TransferPart.GEARS), SampleGearboxes.onDemand().compile().conditions());
        // a governed propeller and a rotor
        assertEquals(List.of(Conditions.BLADES), SampleAirframes.turbopropPropeller().compile().conditions());
        assertEquals(List.of(Conditions.BLADES), SampleAirframes.fixedPropeller().compile().conditions());
        assertEquals(List.of(Conditions.BLADES), SampleAirframes.mainRotor().compile().conditions());
    }

    /** A state as 0.24 saved it: no "condition" and no "holds", the parts' states in a list of the vehicle's length. */
    @Test void aSaveFromBeforeReadsBackWithItsPartsStatesAndEverythingNew() {
        // the parts keep the states and shown values they had in 0.24 (AeroCore drops every saved state when the count changes)
        Map<String, String> counts = new java.util.TreeMap<>();
        for (Map.Entry<String, VehicleBlueprint> e : SampleBlueprints.all().entrySet()) {
            Vehicle v = e.getValue().compile();
            counts.put(e.getKey(), v.internals() + "/" + v.shownValues());
            VehicleState st = v.newState(new Vec3d(3, 70, -2), Orientation.IDENTITY, 0.005);
            // every part's states made unlike a new vehicle's, and everything worn
            double x = 0.25;
            for (Vehicle.Installed i : v.installed()) {
                for (int k = 0; k < i.part().internalStates(); k++) { st.setInternal(i.id(), k, x += 0.125); }
                for (int k = 0; k < i.part().conditions().size(); k++) { st.setUsed(i.id(), k, 0.4); }
            }
            Map<String, Object> saved = new LinkedHashMap<>(st.toJson());
            assertEquals(v.conditions() > 0, saved.containsKey("condition"), e.getKey());
            List<?> internals = (List<?>) saved.get("internals");
            assertEquals(v.internals(), internals.size());
            saved.remove("condition");
            saved.remove("holds");
            VehicleState back = VehicleState.fromJson(v, Json.parse(Json.write(saved)), 0.005);
            x = 0.25;
            for (Vehicle.Installed i : v.installed()) {
                for (int k = 0; k < i.part().internalStates(); k++) { assertEquals(x += 0.125, back.internal(i.id(), k), 0, e.getKey() + " " + i.id() + " state " + k); }
                for (String c : i.part().conditions()) { assertEquals(0, back.used(i.id(), c), 0, e.getKey() + " " + i.id() + "." + c); }
            }
            assertEquals(0, back.worstUsed(), 0);
            // and a new vehicle's save is as 0.24 wrote it: no condition key
            assertFalse(v.newState(Vec3d.ZERO, Orientation.IDENTITY, 0.005).toJson().containsKey("condition"), e.getKey());
            // what is worn comes back by part and name
            VehicleState worn = VehicleState.fromJson(v, Json.parse(Json.write(st.toJson())), 0.005);
            assertEquals(v.conditions() > 0 ? 0.4 : 0, worn.worstUsed(), 0, e.getKey());
        }
        assertEquals(COUNTS_024, counts.toString(), "the sample vehicles' internal states / shown values");
    }

    /**
     * The sample vehicles' internal states / shown values as hdfv 0.24 had them (0.25's conditions added none: they were
     * counted with the conditions in place and no part's internalStates() or shownValues() touched).
     */
    static final String COUNTS_024 = "{boosterstand=6/8, cruiser=0/0, engineboat=4/3, fanboat=11/3, helicopter=20/17, jetboat=4/3, jetstand=5/5, launch=0/0, platform=0/0, pulsejetboat=3/6, seaplane=53/4, sedan=41/28, turbineboat=12/3}";
}
