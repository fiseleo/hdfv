package dev.mcmod.hdfv.drive;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.ContactSolver;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.GroundContact;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.ground.Ground;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.Coupling;
import dev.mcmod.aerocore.api.vehicle.TestTrack;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.assembly.AssemblyPlan;
import dev.mcmod.hdfv.assembly.RoomRules;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.drive.Gearboxes;
import dev.mcmod.hdfv.part.truck.FifthWheelDesign;
import dev.mcmod.hdfv.part.truck.LandingLegsDesign;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 0.18: the test tractor and low-loader: their jaw and kingpin, coupling's rules and limits, and a tow on the test track. */
class HitchTest {
    static { HdfvParts.register(); }
    static final FluidState AIR = Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
    static final Ground FLAT = Ground.flat(0, GroundContact.Surface.TARMAC);

    @Test void theTestTractorHasAJawAndTheTrailerAKingpinAndLegs() {
        VehicleBlueprint t = TestCars.tractor6x4(), r = TestCars.lowLoader();
        Vehicle tv = t.compile(), rv = r.compile();
        assertTrue(CarKit.of(t).fifthWheel());
        assertFalse(CarKit.of(r).fifthWheel());
        Hitch.Jaw jaw = Hitch.jaw(t, tv);
        assertNotNull(jaw);
        assertEquals(0, jaw.point().subtract(new Vec3d(1.875, 0.3 + 0.03 + 0.2, 0)).length(), 1e-9, "the jaw: " + jaw.point());
        Hitch.Kingpin pin = Hitch.kingpin(r, rv);
        assertNotNull(pin);
        assertEquals(FifthWheelDesign.Kingpin.TWO_INCH, pin.size());
        double share = pin.staticLoadKg() / rv.properties().massKg();
        assertTrue(share > 0.2 && share < 0.6, "the kingpin's share of the trailer: " + share);
        assertNull(Hitch.kingpin(t, tv));
        assertNull(Hitch.jaw(r, rv));
        // two legs, drawn up on the landing gear's channel, their feet held by their brakes
        List<VehicleBlueprint.Entry> legs = r.parts().stream().filter(e -> e.design() instanceof LandingLegsDesign).toList();
        assertEquals(2, legs.size());
        assertEquals(List.of("legs_1", "legs_2"), legs.stream().map(e -> e.mount().attachPoint()).toList());
        for (VehicleBlueprint.Entry e : legs) {
            assertTrue(e.bindings().contains(Binding.of("retract", "landing_gear")), e.bindings().toString());
            assertTrue(e.bindings().contains(new Binding("brake", "brake", 0, 1)), e.bindings().toString());
        }
        // an assembly room has nothing against either
        assertEquals(List.of(), RoomRules.vehicle(AssemblyPlan.of(t)));
        assertEquals(List.of(), RoomRules.vehicle(AssemblyPlan.of(r)));
    }

    @Test void standingAloneTheTrailersPlateIsJustUnderTheFifthWheel() {
        VehicleBlueprint t = TestCars.tractor6x4(), r = TestCars.lowLoader();
        Vehicle tv = t.compile(), rv = r.compile();
        Hitch.Jaw jaw = Hitch.jaw(t, tv);
        Hitch.Kingpin pin = Hitch.kingpin(r, rv);
        // a vehicle on a frame has its origin at the rails' bottom: set down from 0.8 m
        VehicleState ts = TestTrack.settle(tv, FLAT, AIR, new Vec3d(0, 0.8, 0), 0, Map.of("brake", 1.0), 4);
        VehicleState rs = TestTrack.settle(rv, FLAT, AIR, new Vec3d(-20, 0.8, 0), 0, Map.of(), 4);
        double jawY = ts.body().worldPoint(tv.properties(), jaw.point()).y(), plateY = rs.body().worldPoint(rv.properties(), pin.point()).y();
        System.out.printf("hitch heights: jaw %.3f m, the trailer's plate on its legs %.3f m%n", jawY, plateY);
        assertTrue(plateY <= jawY + Hitch.ABOVE_M && plateY >= jawY - Hitch.BELOW_M, "the plate " + plateY + " against the jaw " + jawY);
        // the trailer stands level enough on its legs and tridem
        Vec3d up = rs.body().orientation().rotate(Vec3d.Y);
        assertTrue(up.y() > Math.cos(Math.toRadians(2)), "level: " + up);
    }

    @Test void theRulesRefuseWhatIsNotUnderTheJaw() {
        VehicleBlueprint t = TestCars.tractor6x4(), r = TestCars.lowLoader();
        Vehicle tv = t.compile(), rv = r.compile();
        Hitch.Jaw jaw = Hitch.jaw(t, tv);
        Hitch.Kingpin pin = Hitch.kingpin(r, rv);
        assertEquals(Hitch.Result.OK, Hitch.check(jaw, pin, new Hitch.Stance(new Vec3d(-0.05, -0.04, 0.02), 0.1, 0.2)));
        assertEquals(Hitch.Result.SIDEWAYS, Hitch.check(jaw, pin, new Hitch.Stance(new Vec3d(0, -0.04, 0.2), 0, 0)));
        assertEquals(Hitch.Result.NOT_UNDER, Hitch.check(jaw, pin, new Hitch.Stance(new Vec3d(-0.5, -0.04, 0), 0, 0)));
        assertEquals(Hitch.Result.TOO_HIGH, Hitch.check(jaw, pin, new Hitch.Stance(new Vec3d(0, 0.05, 0), 0, 0)));
        assertEquals(Hitch.Result.TOO_LOW, Hitch.check(jaw, pin, new Hitch.Stance(new Vec3d(0, -0.2, 0), 0, 0)));
        assertEquals(Hitch.Result.ANGLE, Hitch.check(jaw, pin, new Hitch.Stance(new Vec3d(0, -0.04, 0), Math.toRadians(40), 0)));
        assertEquals(Hitch.Result.TOO_FAST, Hitch.check(jaw, pin, new Hitch.Stance(new Vec3d(0, -0.04, 0), 0, 1)));
        assertEquals(Hitch.Result.NO_FIFTH_WHEEL, Hitch.check(null, pin, new Hitch.Stance(Vec3d.ZERO, 0, 0)));
        assertEquals(Hitch.Result.NO_KINGPIN, Hitch.check(jaw, null, new Hitch.Stance(Vec3d.ZERO, 0, 0)));
        Hitch.Kingpin heavy = new Hitch.Kingpin(pin.chassis(), pin.point(), pin.size(), jaw.design().ratedLoadKg() + 1);
        assertEquals(Hitch.Result.OVERLOAD, Hitch.check(jaw, heavy, new Hitch.Stance(new Vec3d(0, -0.04, 0), 0, 0)));
        Hitch.Kingpin big = new Hitch.Kingpin(pin.chassis(), pin.point(), FifthWheelDesign.Kingpin.THREE_AND_HALF_INCH, pin.staticLoadKg());
        assertEquals(Hitch.Result.KINGPIN_SIZE, Hitch.check(jaw, big, new Hitch.Stance(new Vec3d(0, -0.04, 0), 0, 0)));
        // the coupling: the plate's pitch or less, a right angle of jackknife (the cab is clear of the neck), the channels
        Coupling c = Hitch.coupling(t, tv, jaw, r, rv, pin);
        assertEquals(Math.PI / 2, c.yawLimitRad(), 1e-9, "the cab's back wall " + "clear of the gooseneck's swing");
        assertTrue(c.pitchLimitRad() >= Hitch.MIN_PITCH_RAD - 1e-12 && c.pitchLimitRad() <= Math.toRadians(12) + 1e-12, "pitch " + Math.toDegrees(c.pitchLimitRad()));
        assertEquals(Hitch.FORWARDED, c.forwarded());
        assertTrue(c.rollHeld());
        assertEquals(jaw.point(), c.leaderPoint());
        assertEquals(pin.point(), c.followerPoint());
    }

    @Test void coupledTheTractorPullsTheTrailerAway() {
        VehicleBlueprint t = TestCars.tractor6x4(), r = TestCars.lowLoader();
        Vehicle tv = t.compile(), rv = r.compile();
        Hitch.Jaw jaw = Hitch.jaw(t, tv);
        Hitch.Kingpin pin = Hitch.kingpin(r, rv);
        Coupling c = Hitch.coupling(t, tv, jaw, r, rv, pin);
        VehicleState ts = tv.newState(new Vec3d(0, 0.8, 0), Orientation.IDENTITY, TestTrack.DT);
        VehicleState rs = rv.newState(TestTrack.hitchedOrigin(tv, ts, c, 0), Orientation.IDENTITY, TestTrack.DT);
        List<Vehicle> vs = List.of(tv, rv);
        List<VehicleState> ss = List.of(ts, rs);
        List<ContactSolver.Joint> joints = List.of(new ContactSolver.Joint(0, 1, c));
        // settle hitched, the legs drawn up; then drive off in D
        TestTrack.convoy(vs, ss, joints, FLAT, AIR, (k, time, s) -> k == 0 ? Map.of("brake", 1.0) : Map.of("landing_gear", 1.0), 4, s -> false, 0);
        double start = rs.origin().x();
        List<TestTrack.ConvoySample> samples = TestTrack.convoy(vs, ss, joints, FLAT, AIR,
                (k, time, s) -> k == 0 ? Map.of("throttle", 0.6, "selector", (double) Gearboxes.DRIVE) : Map.of("landing_gear", 1.0), 20, s -> false, 20);
        double worst = 0;
        for (TestTrack.ConvoySample s : samples) { worst = Math.max(worst, s.joints().getFirst().gapM()); }
        double moved = rs.origin().x() - start, speed = ts.body().velocity().x();
        System.out.printf("hitch tow: the trailer went %.1f m, the pair at %.1f km/h; worst gap %.2f mm%n", moved, speed * 3.6, worst * 1000);
        assertTrue(moved > 10, "the trailer is pulled along: " + moved);
        assertEquals(speed, rs.body().velocity().x(), 0.05, "together");
        assertTrue(worst < 0.002, "the kingpin stays in the jaw: " + worst);
        assertTrue(samples.getLast().joints().getFirst().forceN().x() > 0, "the tractor pulls the trailer forward");
    }
}
