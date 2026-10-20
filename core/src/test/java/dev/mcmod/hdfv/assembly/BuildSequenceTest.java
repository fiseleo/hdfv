package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The order vehicles go together in (0.8). */
class BuildSequenceTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    @Test void theStepsAddUpToTheRoomsWorkAndEveryPartComesOnce() {
        for (Map.Entry<String, VehicleBlueprint> e : SampleLayouts.all().entrySet()) {
            VehicleBlueprint b = e.getValue();
            AssemblyPlan plan = AssemblyPlan.of(b);
            BuildSequence s = BuildSequence.of(b);
            String name = e.getKey();
            // the labour and the cure are the room's
            assertEquals(plan.laborHours() * 3600, s.laborS(), 1e-6 * Math.max(1, s.laborS()), name + " labour");
            assertEquals(plan.cureHours() * 3600, s.cureS(), 1e-6 * Math.max(1, s.cureS()), name + " cure");
            // the spans tile the build from 0 to 1
            double at = 0;
            for (BuildSequence.Step st : s.steps()) {
                assertEquals(at, st.start(), 1e-12, name + " " + st);
                assertTrue(st.end() >= st.start());
                at = st.end();
            }
            assertEquals(1, at, 1e-12, name + " ends at 1");
            // every installed part appears: room-built ones as a skeleton and a skin, the rest hoisted and fitted, once each
            Vehicle v = b.compile();
            Map<String, Integer> seen = new HashMap<>();
            for (BuildSequence.Step st : s.steps()) { if (st.part() != null) { seen.merge(st.part() + " " + st.kind(), 1, Integer::sum); } }
            for (Vehicle.Installed p : v.installed()) {
                boolean room = dev.mcmod.hdfv.manufacture.Plans.of(p.part().design()).roomBuilt();
                for (BuildSequence.Kind k : room ? new BuildSequence.Kind[]{BuildSequence.Kind.SKELETON, BuildSequence.Kind.SKIN}
                        : new BuildSequence.Kind[]{BuildSequence.Kind.HOIST, BuildSequence.Kind.FIT}) {
                    assertEquals(1, seen.getOrDefault(p.id() + " " + k, 0), name + " " + p.id() + " " + k);
                }
                if (!room) {
                    boolean crane = s.steps().stream().filter(st -> p.id().equals(st.part()) && st.kind() == BuildSequence.Kind.HOIST).findFirst().orElseThrow().crane();
                    assertEquals(p.part().mass().massKg() > AssemblyPlan.LIFT_KG, crane, name + " " + p.id() + " crane at " + p.part().mass().massKg() + " kg");
                }
            }
            // the parts go in heaviest first
            double last = Double.POSITIVE_INFINITY;
            for (BuildSequence.Step st : s.steps()) {
                if (st.kind() != BuildSequence.Kind.HOIST) { continue; }
                assertTrue(st.massKg() <= last + 1e-9, name + " heaviest first");
                last = st.massKg();
            }
            assertTrue(s.at(0).kind() == BuildSequence.Kind.SKELETON || s.at(0).kind() == BuildSequence.Kind.HOIST, name + " starts with the structure or the first part");
        }
    }

    @Test void theCraneHookLiftsTravelsAndLowers() {
        Vec3d pick = new Vec3d(0, 0, 0), drop = new Vec3d(6, 1, 2);
        assertEquals(pick, CraneRunway.hook(pick, drop, 2, 0));
        assertEquals(drop.x(), CraneRunway.hook(pick, drop, 2, 1).x(), 1e-12);
        assertEquals(drop.y(), CraneRunway.hook(pick, drop, 2, 1).y(), 1e-12);
        Vec3d mid = CraneRunway.hook(pick, drop, 2, 0.5);
        assertEquals(3, mid.y(), 1e-12, "carried over at the headroom");
        assertEquals(3, mid.x(), 1e-12, "half way across");
        // a 6 m travel at 0.5 m/s, 3 m up at 8 m/min and 2 m down: 12 + 22.5 + 15 s
        assertEquals(12 + 22.5 + 15, CraneRunway.moveS(pick, drop, 2), 1e-9);
    }
}
