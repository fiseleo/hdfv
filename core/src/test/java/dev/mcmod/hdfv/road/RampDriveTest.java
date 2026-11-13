package dev.mcmod.hdfv.road;

import dev.mcmod.aerocore.api.AppliedForce;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.GroundContact;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.ground.BoxGround;
import dev.mcmod.aerocore.api.ground.Ground;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.TestTrack;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.wheel.SampleWheels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.25: wheels on a road ramp's steps (RampShape) against the smooth slope they stand for, on the offline track: a car
 * pushed up a run with 1.3 times the slope's pull reaches the level above about when it does on the slope, and
 * coasting up the 1:8 run it keeps the speed the slope leaves it.
 * <p>In the world the wheels no longer meet the steps: since AeroCore 0.42 the ramp block is a SlopedGround and a
 * wheel's ray finds the slope itself (RoadRampBlock; the car in the world is RoadGameTests'). The steps remain the
 * block's collision shape, what players and vehicle bodies touch, and what a wheel's ray met before 0.42: a ray on them
 * meets treads and risers, never a tilted face, and coasting up the 1:4 and 1:2 runs loses more than on the slope,
 * unevenly with the speed (the reason for SlopedGround), so that is not held to a number here. This test keeps the two
 * side by side offline: the smooth slope is what the game's wheels now drive on, the steps what they stand for and how
 * far the shape itself was from it. Wheels only: the offline track has no body contact.
 */
class RampDriveTest {
    static final FluidState AIR = dev.mcmod.aerocore.api.Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
    /** Where the ramp's foot is, m along x. */
    static final double FOOT = 12;

    @BeforeAll static void register() { HdfvParts.register(); }

    /** A floor, a run of a ramp's parts rising toward +x from FOOT as the block's steps, and the level above beyond it. */
    static Ground track(int gradient) {
        GroundContact.Surface s = GroundContact.Surface.TARMAC;
        List<BoxGround.Box> boxes = new ArrayList<>();
        boxes.add(new BoxGround.Box(new Vec3d(-50, -1, -5), new Vec3d(200, 0, 5), s));
        for (int part = 0; part < gradient; part++) {
            double[] steps = RampShape.steps(gradient, part);
            for (int i = 0; i < steps.length; i++) {
                double x = FOOT + part + (double) i / RampShape.STEPS;
                boxes.add(new BoxGround.Box(new Vec3d(x, 0, -5), new Vec3d(x + 1.0 / RampShape.STEPS, steps[i], 5), s));
            }
        }
        boxes.add(new BoxGround.Box(new Vec3d(FOOT + gradient, 0, -5), new Vec3d(200, 1, 5), s));
        return new BoxGround(boxes);
    }

    /** The same run as one smooth slope between the floor and the level above (what the steps stand for). */
    static Ground smooth(int gradient) {
        GroundContact.Surface s = GroundContact.Surface.TARMAC;
        Ground floor = Ground.flat(0, s), deck = Ground.flat(1, s), slope = Ground.plane(new Vec3d(FOOT, 0, 0), new Vec3d(-1, gradient, 0), s);
        return (from, direction, maxM) -> {
            Ground.Hit best = null;
            for (Ground g : List.of(floor, slope, deck)) {
                Ground.Hit hit = g.cast(from, direction, maxM);
                if (hit == null) { continue; }
                double x = hit.point().x();
                boolean here = g == floor ? x <= FOOT : g == slope ? x >= FOOT && x <= FOOT + gradient : x >= FOOT + gradient;
                if (here && (best == null || hit.distanceM() < best.distanceM())) { best = hit; }
            }
            return best;
        };
    }

    /** A family car without a drive: a 1100 kg box between the sample sedan's axles (2.65 m wheelbase). */
    static Vehicle car() {
        return new VehicleBlueprint("car", List.of(
                new VehicleBlueprint.Entry("body", new BoxDesign("body", new Vec3d(4.2, 1.0, 1.7), 0, false, 1, MassSpec.given(1100)), Mount.at(new Vec3d(0, 0.25, 0))),
                new VehicleBlueprint.Entry("front", SampleWheels.sedanFront(), Mount.at(new Vec3d(1.19, 0, 0))),
                new VehicleBlueprint.Entry("rear", SampleWheels.sedanRear(), Mount.at(new Vec3d(-1.46, 0, 0)))), List.of()).compile();
    }

    /** Rolling at a speed: the body and every wheel (and a driven axle's input) with it. */
    static void moving(VehicleState st, double speed) {
        RigidBodyState b = st.body();
        st.setBody(new RigidBodyState(b.position(), new Vec3d(speed, 0, 0), b.orientation(), Vec3d.ZERO));
        double re = SampleWheels.carTyre().tyre().effectiveRadiusM(3500);
        for (Vehicle.Installed i : st.vehicle().installed()) {
            if (i.part() instanceof AxlePart a) {
                int first = i.firstRotor() + (a.drive() == null ? 0 : 1);
                for (int k = 0; k < 2; k++) {
                    Vehicle.Rotor r = st.vehicle().rotors().get(first + k);
                    st.setTrainSpeed(r.train(), speed / re / r.ratio());
                }
                if (a.drive() != null) {
                    Vehicle.Rotor input = st.vehicle().rotors().get(i.firstRotor());
                    st.setTrainSpeed(input.train(), a.drive().finalRatio() * speed / re / input.ratio());
                }
            }
        }
    }

    /** What a drive ended as: the height gained (m), the speed (m/s), where along x (m) and after how long (s). */
    record End(double roseM, double speedMps, double x, double timeS) {
        @Override public String toString() { return String.format("%.3f m up, %.2f m/s at x %.1f m after %.2f s", roseM, speedMps, x, timeS); }
    }

    /**
     * From a speed (m/s) along +x over a track, pushed at the centre of mass by a force (N; 0 coasts), until past a place
     * or stopped.
     */
    static End drive(Ground ground, double speed, double forceN, double untilX) {
        Vehicle v = car();
        VehicleState st = TestTrack.settle(v, ground, AIR, new Vec3d(0, 0.35, 0), 0, Map.of(), 2);
        double y0 = st.origin().y(), t = 0;
        moving(st, speed);
        VehicleEnvironment environment = VehicleEnvironment.land(ground, AIR);
        List<AppliedForce> push = forceN == 0 ? List.of() : List.of(new AppliedForce(AppliedForce.Kind.THRUST, Vec3d.ZERO, new Vec3d(forceN, 0, 0)));
        for (int i = 0; i < 30 / TestTrack.DT; i++) {
            VehicleDynamics.step(v, st, environment, Map.of(), Vec3d.Y.scale(-TestTrack.G), push, null, t, TestTrack.DT);
            t += TestTrack.DT;
            if (st.origin().x() > untilX || st.body().velocity().x() < 0.05) { break; }
        }
        return new End(st.origin().y() - y0, st.body().velocity().length(), st.origin().x(), t);
    }

    /** Pushed with 1.3 times the slope's pull (and the tyres' rolling resistance) from 3 m/s, the car climbs the steps about as it climbs the slope. */
    @Test void aPushedCarClimbsTheStepsAsItClimbsTheSlope() {
        double mass = car().properties().massKg();
        for (int gradient : RampShape.GRADIENTS) {
            double end = FOOT + gradient + 8, force = mass * TestTrack.G * (1.3 / Math.hypot(1, gradient) + 0.015);
            End slope = drive(smooth(gradient), 3, force, end), steps = drive(track(gradient), 3, force, end);
            String what = "1:" + gradient + " run pushed with " + Math.round(force) + " N: on the steps " + steps + "; on the slope " + slope;
            System.out.println(what);
            assertTrue(slope.x() > end && steps.x() > end, "over the top and on: " + what);
            // in the slope's time: within 5 % on the 1:8 run, 15 % on the steeper ones (measured 0, 7 and 10 %)
            assertEquals(slope.timeS(), steps.timeS(), (gradient == 8 ? 0.05 : 0.15) * slope.timeS(), what);
            // on the level above (the 1:2 run throws the car off its crest at this push, on the slope as on the steps)
            if (gradient > 2) { assertEquals(1.0, steps.roseM(), 0.03, "a block higher: " + what); }
        }
    }

    /** Coasting up the 1:8 run the steps leave the car the slope's speed. */
    @Test void aCarCoastsUpTheGentlestRunAsUpItsSlope() {
        for (double speed : new double[]{8, 11}) {
            double end = FOOT + 8 + 8;
            End slope = drive(smooth(8), speed, 0, end), steps = drive(track(8), speed, 0, end);
            String what = "1:8 run from " + speed + " m/s: on the steps " + steps + "; on the slope " + slope;
            System.out.println(what);
            assertTrue(steps.x() > end, "over the top and on: " + what);
            assertEquals(1.0, steps.roseM(), 0.03, "a block higher: " + what);
            assertEquals(slope.speedMps(), steps.speedMps(), 0.05 * slope.speedMps(), what);
            // and the slope itself takes the height's energy: v^2 falls by 2 g h and the road's own losses
            assertTrue(slope.speedMps() < Math.sqrt(speed * speed - 2 * TestTrack.G) && slope.speedMps() > 0.9 * Math.sqrt(speed * speed - 2 * TestTrack.G), what);
        }
    }
}
