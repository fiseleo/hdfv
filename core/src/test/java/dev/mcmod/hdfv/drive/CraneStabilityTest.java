package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.ground.Terrain;
import dev.mcmod.aerocore.api.lifting.Stability;
import dev.mcmod.aerocore.api.vehicle.MassProperties;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehiclePose;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.machine.LinkDesign;
import dev.mcmod.hdfv.part.machine.SampleMachinery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-11-22: the test crawler crane and dragline stand on their crawlers (the user saw the crawler crane lying on its
 * boom in the world: on the 20 t excavator's tracks with 8 t of counterweight its centre of gravity was 2.36 m ahead of
 * the slewing centre, past the tracks' front end at 1.83 m). Statically over the boom's whole luff and every slewing
 * angle (AeroCore's Stability about the road wheels' outermost hubs, a conservative support), and offline when set down.
 */
class CraneStabilityTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    /** How far the centre of gravity stays inside every tipping line, m. */
    static final double INSIDE_M = 0.5;

    /** The tipping lines: the outermost road wheels' hubs of the crane crawlers (plan: x forward, y to the left). */
    static List<Stability.Point> support() {
        double x = 0.5 * TestMachines.CRANE_TRACKS.contactLengthM(), y = 0.5 * TestMachines.CRANE_TRACKS.gaugeM();
        return List.of(new Stability.Point(x, y), new Stability.Point(-x, y), new Stability.Point(-x, -y), new Stability.Point(x, -y));
    }

    /** The machine's part masses in plan at a pose (the vehicle's z is to the right, the plan's y to the left). */
    static List<Stability.Mass> masses(VehiclePose pose, Vehicle v) {
        List<Stability.Mass> out = new ArrayList<>();
        for (Vehicle.Installed i : v.installed()) {
            MassProperties m = pose.partMass(i.index());
            if (m.massKg() > 0) { out.add(new Stability.Mass(m.massKg(), m.centre().x(), -m.centre().z())); }
        }
        return out;
    }

    @Test void theCranesStandOverTheBoomsWholeLuffAtEverySlewingAngle() {
        LinkDesign boom = SampleMachinery.latticeBoom();
        double x = 0.5 * TestMachines.CRANE_TRACKS.contactLengthM(), y = 0.5 * TestMachines.CRANE_TRACKS.gaugeM();
        for (String name : new String[]{"crawlercrane", "dragline"}) {
            Vehicle v = TestMachines.ALL.get(name).get().compile();
            double[] values = v.restPose().values();
            int swing = -1, luff = -1;
            for (Vehicle.Joint j : v.joints()) {
                if (j.id().equals("swing")) { swing = j.index(); }
                if (j.id().equals("boom")) { luff = j.index(); }
            }
            assertTrue(swing >= 0 && luff >= 0, name + " slews and luffs");
            double least = Double.POSITIVE_INFINITY;
            String where = "";
            for (double luffDeg = boom.lowerDeg(); luffDeg <= boom.upperDeg() + 1e-9; luffDeg += (boom.upperDeg() - boom.lowerDeg()) / 8) {
                for (int slewDeg = 0; slewDeg < 360; slewDeg += 15) {
                    double[] at = values.clone();
                    at[luff] = Math.toRadians(luffDeg);
                    at[swing] = Math.toRadians(slewDeg);
                    VehiclePose pose = v.pose(at);
                    Stability.Moments m = Stability.governing(support(), masses(pose, v));
                    assertTrue(m.marginNm() > 0, name + " tips at a luff of " + luffDeg + " and a slew of " + slewDeg + ": " + m);
                    Vec3d c = pose.mass().centre();
                    double inside = Math.min(x - Math.abs(c.x()), y - Math.abs(c.z()));
                    if (inside < least) { least = inside; where = String.format("luff %.1f, slew %d: centre (%.2f, %.2f)", luffDeg, slewDeg, c.x(), c.z()); }
                }
            }
            System.out.printf("%s: %.0f kg, the centre of gravity at least %.2f m inside its tipping lines (%s)%n", name, v.properties().massKg(), least, where);
            assertTrue(least >= INSIDE_M, name + " keeps its centre of gravity " + INSIDE_M + " m inside its tipping lines: " + least + " m at " + where);
        }
    }

    @Test void theCranesStandStillWhenSetDown() {
        for (String name : new String[]{"crawlercrane", "dragline"}) {
            Vehicle v = TestMachines.ALL.get(name).get().compile();
            VehicleState st = MachineDriveTest.settled(v);
            Vec3d start = st.origin();
            double lowest = 1;
            for (int s = 0; s < 8; s++) {
                MachineDriveTest.run(v, st, Map.of("travel_brake", 1.0, "brake", 1.0), 1, Terrain.NONE);
                lowest = Math.min(lowest, st.body().orientation().rotate(Vec3d.Y).y());
            }
            // the dragline's bucket swings on its free hitch at the boom's tip and rocks it a little: where it is, not how fast
            double moved = Math.hypot(st.origin().x() - start.x(), st.origin().z() - start.z());
            System.out.printf("%s set down: up %.4f at worst, moved %.3f m in 8 s%n", name, lowest, moved);
            assertTrue(lowest > Math.cos(Math.toRadians(3)), name + " stays level on its crawlers: up.y " + lowest);
            assertTrue(moved < 0.1, name + " stays where it was set down: " + moved + " m");
        }
    }
}
