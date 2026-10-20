package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.part.FuelTankDesign;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Vehicle fuel (0.8): a body's fuel is in its tanks, the fuel tank part, engines that run their tanks dry. */
class FuelTest {
    static { HdfvParts.register(); }

    private static final double DT = 0.005;
    private static final Vec3d GRAVITY = new Vec3d(0, -9.80665, 0);
    private static final FluidState AIR = Atmosphere.atAltitude(0, Vec3d.ZERO).fluid(), WATER = FluidState.FRESH_WATER_20C;

    /** A body compiled with its fuel in its own mass, as before 0.8 (to compare with). */
    record FuelAsMass(BodyDesign body) implements PartDesign {
        @Override public String type() { return body.type(); }
        @Override public String name() { return body.name(); }
        @Override public List<DesignIssue> check() { return body.check(); }
        @Override public Part compile() { return body.compile(false); }
        @Override public Map<String, Object> parameters() {
            Map<String, Object> p = new LinkedHashMap<>(body.parameters());
            p.put("fuelAsMass", true);   // AeroCore keeps compiled parts by their JSON
            return p;
        }
    }

    @Test void aBodysFuelIsInItsTanks() {
        BodyDesign f = SampleBodies.helicopterFuselage();
        Part p = f.compile();
        assertEquals(1, p.tanks().size());
        Part.Tank t = p.tanks().get(0);
        assertEquals("kerosene", t.fuel());
        assertEquals(1.4, t.capacityM3(), 1e-12);
        assertEquals(1.4 * 800 * 0.5, t.initialKg(), 1e-9, "half full of kerosene");
        assertEquals(f.mass().massKg() - t.initialKg(), p.mass().massKg(), 1e-6);
        // dry and fuel together are what the body weighed with its fuel in it
        MassProperties joint = MassProperties.combine(List.of(p.mass(), MassProperties.point(t.initialKg(), t.centre())));
        assertEquals(f.mass().massKg(), joint.massKg(), 1e-6);
        assertEquals(0, joint.centre().subtract(f.mass().centre()).length(), 1e-9);
        // the fuel is a choice of hdfv's fuels and reads back
        BodyDesign diesel = f.withSettings(dev.mcmod.hdfv.design.RecordEditor.with(f.settings(), "fuel", "diesel"));
        assertEquals(1.4 * 840 * 0.5, diesel.compile().tanks().get(0).initialKg(), 1e-9);
        assertEquals(diesel, dev.mcmod.aerocore.api.vehicle.PartTypes.read(Json.parse(Json.write(diesel.toJson())), "body"));
        assertTrue(dev.mcmod.hdfv.design.RecordEditor.with(f.settings(), "fuel", "water").check().stream().anyMatch(i -> i.parameter().equals("fuel")));
        // no tank volume, no tanks
        assertTrue(SampleBodies.seaplaneFloat().compile().tanks().isEmpty());
    }

    @Test void aFuelTankHoldsItsInsideAndWeighsItsWalls() {
        FuelTankDesign t = FuelTankDesign.car("gasoline");
        Part p = t.compile();
        double inside = 0.597 * 0.397 * 0.247;
        assertEquals(0.97 * inside, p.tanks().get(0).capacityM3(), 1e-12);
        assertEquals(0.97 * inside * 745, p.tanks().get(0).initialKg(), 1e-9, "full of gasoline");
        assertEquals((0.6 * 0.4 * 0.25 - inside) * 7850, p.mass().massKg(), 0.01 * p.mass().massKg(), "1.5 mm steel: about 11 kg");
        assertTrue(new FuelTankDesign("t", 0.2, 0.2, 0.2, 0.1, t.material(), "gasoline", 1).check().stream().anyMatch(i -> i.parameter().equals("wallM")));
        assertTrue(new FuelTankDesign("t", 0.2, 0.2, 0.2, 0.002, t.material(), "crude_oil", 1).check().stream().anyMatch(i -> i.parameter().equals("fuel")));
        // in a blueprint, saved and read back
        VehicleBlueprint boat = SampleBlueprints.engineBoat();
        assertEquals(boat, VehicleBlueprint.fromJsonText(boat.toJsonText()));
        assertEquals(1, boat.compile().tanks().size());
    }

    @Test void theSeaplaneFloatsAsItDidWithItsFuelInItsMass() {
        VehicleBlueprint tanked = SampleBlueprints.seaplane();
        List<VehicleBlueprint.Entry> entries = new ArrayList<>();
        for (VehicleBlueprint.Entry e : tanked.parts()) {
            entries.add(e.id().equals("fuselage") ? new VehicleBlueprint.Entry(e.id(), new FuelAsMass((BodyDesign) e.design()), e.mount(), e.bindings()) : e);
        }
        VehicleBlueprint before = new VehicleBlueprint(tanked.name(), entries, tanked.connections());
        Vehicle a = tanked.compile(), b = before.compile();
        assertEquals(1, a.tanks().size());
        assertTrue(b.tanks().isEmpty());
        double fuel = a.tanks().get(0).tank().initialKg();
        assertEquals(b.properties().massKg(), a.properties().massKg() + fuel, 1e-6, "the same mass at the default loading");
        VehicleEnvironment env = VehicleEnvironment.calm(0, WATER, AIR);
        VehicleState sa = a.newState(new Vec3d(0, -0.2, 0), Orientation.IDENTITY, DT), sb = b.newState(new Vec3d(0, -0.2, 0), Orientation.IDENTITY, DT);
        for (int i = 0; i < (int) (20 / DT); i++) {
            VehicleDynamics.step(a, sa, env, Map.of(), GRAVITY, List.of(), null, i * DT, DT);
            VehicleDynamics.step(b, sb, env, Map.of(), GRAVITY, List.of(), null, i * DT, DT);
        }
        double draftA = a.origin(sa.body()).y(), draftB = b.origin(sb.body()).y();
        double trimA = sa.body().orientation().rotate(Vec3d.X).y(), trimB = sb.body().orientation().rotate(Vec3d.X).y();
        System.out.printf("seaplane afloat: origin %.4f m with its tank, %.4f m with its fuel as mass; trim %.5f, %.5f%n", draftA, draftB, trimA, trimB);
        assertEquals(draftB, draftA, 1e-3, "the same draft");
        assertEquals(trimB, trimA, 1e-3, "the same trim");
        assertTrue(sa.tankKg(0) < fuel, "the idling turboprop burns from the tank");
    }

    /** Runs the engine boat at a third throttle: its engine's speed at the end and whether it ran at all. */
    record Run(double rpm, boolean ran, boolean running) {}

    static Run run(Vehicle v, VehicleState st, VehicleEnvironment env, double from, double seconds) {
        Map<String, Double> d = Map.of();
        boolean ran = false;
        for (int i = 0; i < (int) (seconds / DT); i++) {
            d = VehicleDynamics.step(v, st, env, Map.of("throttle", 0.3), GRAVITY, List.of(), null, from + i * DT, DT).diagnostics().get("engine");
            ran |= d.get("running") > 0.5;
        }
        return new Run(d.get("rpm"), ran, d.get("running") > 0.5);
    }

    @Test void anEngineStopsWhenItsTankRunsDryAndWillNotRunOnTheWrongFuel() {
        Vehicle v = SampleBlueprints.engineBoat().compile();
        v.warmUp();
        VehicleEnvironment env = VehicleEnvironment.calm(0, WATER, AIR);
        VehicleState st = v.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
        double full = st.tankKg(0);
        assertEquals("gasoline", st.tankFuel(0));
        Run first = run(v, st, env, 0, 8);
        assertTrue(first.running() && first.rpm() > 600, "it starts and runs: " + first);
        assertTrue(st.tankKg(0) < full, "on the tank's petrol");
        // a nearly dry tank: it runs out and stops (the screw may still turn it while the boat coasts)
        st.setTank(0, "gasoline", 0.002, 745);
        Run dry = run(v, st, env, 8, 10);
        assertEquals(0, st.tankKg(0), 0);
        assertFalse(dry.running(), "stopped dry: " + dry);
        // diesel in a petrol engine: the starter turns it, it never runs, the tank keeps its diesel
        st.setTank(0, "", 0, 840);
        st.setTank(0, "diesel", 30, 840);
        assertFalse(run(v, st, env, 18, 6).ran(), "no running on diesel");
        assertEquals(30, st.tankKg(0), 0);
        // petrol again: it starts
        st.setTank(0, "", 0, 840);
        st.setTank(0, "gasoline", 30, 745);
        Run again = run(v, st, env, 24, 8);
        assertTrue(again.running() && again.rpm() > 600, "it starts again: " + again);
    }

    @Test void aRoomWillNotBuildEnginesWithoutATank() {
        VehicleBlueprint boat = SampleBlueprints.engineBoat();
        assertTrue(dev.mcmod.hdfv.assembly.AssemblyPlan.of(boat).tankless().isEmpty());
        VehicleBlueprint dry = new VehicleBlueprint(boat.name(), boat.parts().stream().filter(e -> !e.id().equals("tank")).toList(), boat.connections());
        assertEquals(List.of("engine"), dev.mcmod.hdfv.assembly.AssemblyPlan.of(dry).tankless());
        // an electric launch burns nothing
        assertTrue(dev.mcmod.hdfv.assembly.AssemblyPlan.of(SampleBlueprints.launch()).tankless().isEmpty());
    }
}
