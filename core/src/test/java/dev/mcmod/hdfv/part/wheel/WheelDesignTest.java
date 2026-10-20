package dev.mcmod.hdfv.part.wheel;

import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.GroundContact;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.ground.Ground;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.MassSpec;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.TestTrack;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.aerocore.api.vehicle.part.AxlePart;
import dev.mcmod.aerocore.api.wheel.Tyre;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.WheelBench;
import dev.mcmod.hdfv.part.BoxDesign;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelDesignTest {
    static final double G = 9.80665;
    static final FluidState AIR = dev.mcmod.aerocore.api.Atmosphere.atAltitude(0, Vec3d.ZERO).fluid();
    static final Ground FLAT = Ground.flat(0, GroundContact.Surface.TARMAC);

    @BeforeAll static void register() { HdfvParts.register(); }

    private static boolean invalid(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(parameter));
    }

    private static List<DesignIssue> invalids(PartDesign d) { return d.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList(); }

    @Test void massesMatchPublishedParts() {
        // 205/55R16 passenger tyres weigh 8.5-9.5 kg; 16 x 6.5 cast aluminium wheels 8-9 kg; a 288 x 25 mm vented disc about 7 kg
        assertEquals(9.0, SampleWheels.carTyre().massKg(), 0.25 * 9.0);
        assertEquals(8.5, new RimDesign("w", 6.5, RimDesign.Material.CAST_ALLOY, 5, Double.NaN).massKg(16 * TyreDesign.INCH), 0.25 * 8.5);
        assertEquals(9.5, new RimDesign("w", 6.5, RimDesign.Material.STEEL, 5, Double.NaN).massKg(16 * TyreDesign.INCH), 0.25 * 9.5);
        assertEquals(7.0, SampleWheels.sedanFront().brake().rotorMassKg(), 0.25 * 7.0);
        // LT265/75R16 all-terrain tyres 21-25 kg; a 6.00-6 aircraft tyre about 4 kg (Goodyear: 6-ply Flight Special II 3.9-4.4 kg)
        assertEquals(23, SampleWheels.offRoadTyre().massKg(), 0.25 * 23);
        assertEquals(4.2, SampleWheels.lightMainGear().tyre().massKg(), 0.3 * 4.2);
        // a family car's front corner (tyre, wheel, brake, strut and arm) is 35-45 kg
        assertEquals(40, SampleWheels.sedanFront().cornerKg(), 0.25 * 40);
    }

    @Test void samplesAreValidAndReadBack() {
        for (PartDesign d : List.of(SampleWheels.sedanFront(), SampleWheels.sedanRear(), SampleWheels.offRoadFront(), SampleWheels.offRoadRear(),
                SampleWheels.lightNoseGear(), SampleWheels.lightMainGear())) {
            assertTrue(invalids(d).isEmpty(), d.name() + ": " + invalids(d));
            assertEquals(d, Designs.read(Designs.write(d)), d.name());
            d.compile();
        }
    }

    @Test void checksCatchWhatCannotBeBuilt() {
        AxleDesign front = SampleWheels.sedanFront();
        assertTrue(invalid(Designs.set(front, "brake", "rotorDiameterM", "380"), "brake.rotorDiameterM"), "a 380 mm disc in a 16 in wheel");
        assertTrue(invalid(Designs.set(front, "wheel", "rimWidthIn", "11"), "wheel.rimWidthIn"), "a 205 tyre on an 11 in rim");
        assertTrue(invalid(Designs.set(front, "suspension", "designLoadKg", "900"), "suspension.designLoadKg"), "900 kg on a car tyre");
        assertTrue(invalid(Designs.set(front, "suspension", "designCompressionM", "250"), "suspension.designCompressionM"));
        assertTrue(invalid(Designs.set(front, "tyre", "pressurePa", "350"), "tyre.pressurePa"), "above the rated pressure");
        assertTrue(invalid(Designs.set(SampleWheels.lightNoseGear(), "strut", "designLoadKg", "900"), "strut.designLoadKg"), "the oleo bottoms standing");
        // hidden parameters: a non-driven axle shows only "driven"; an open differential no Torsen ratio
        assertEquals(List.of("driven", "massGivenKg"), dev.mcmod.hdfv.design.RecordEditor.parameters(DifferentialDesign.none()).stream().map(p -> p.name()).toList());
        assertFalse(dev.mcmod.hdfv.design.RecordEditor.parameters(SampleWheels.offRoadRear().differential()).stream().anyMatch(p -> p.name().equals("biasRatio")));
    }

    @Test void theFamilyCarAxleRidesAndBrakesLikeOne() {
        AxleDesign front = SampleWheels.sedanFront();
        Tyre t = front.tyre().tyre();
        // Milliken: passenger cars 1.0-1.5 Hz, dampers 0.2-0.5 of critical
        assertEquals(1.25, front.rideFrequencyHz(t), 0.25);
        assertEquals(0.35, front.dampingRatio(), 0.15);
        // the front brakes lock a front wheel at its static load (with weight transfer they still can)
        assertTrue(front.lockRatio(t) > 1.2, "lock ratio " + front.lockRatio(t));
        // 288 mm, 57 mm piston at 7 MPa: about 1700 N m
        assertEquals(1700, front.brake().maxTorqueNm(), 100);
        // MacPherson: roll centre a few centimetres up, camber gained in bump
        WheelBench.AxleBench bench = WheelBench.axle(front);
        assertTrue(bench.rollCentreM() > 0 && bench.rollCentreM() < 0.15, "roll centre " + bench.rollCentreM());
        assertTrue(bench.travel().get(20).camberDeg() < bench.travel().get(0).camberDeg(), "camber goes negative in bump");
        // the tyre's peak: 1.0 g near 0.1 slip and 6-8 degrees on dry tarmac
        assertEquals(1.0, bench.tyre().peakBrakingN() / front.cornerLoadN(), 0.1);
        assertEquals(0.12, bench.tyre().peakSlip(), 0.05);
        assertEquals(7, bench.tyre().peakAngleDeg(), 2);
        // fade: ten hard stops heat the discs stop after stop, to a few hundred degrees
        WheelBench.FadeStop first = bench.fade().get(0), last = bench.fade().get(bench.fade().size() - 1);
        assertTrue(last.peakC() > first.peakC() + 100 && last.peakC() < 800, "first " + first.peakC() + " C, last " + last.peakC() + " C");
        assertTrue(last.torqueShare() <= 1 && first.torqueShare() == 1, "fade " + last.torqueShare());
        // a small solid disc in the same test runs hotter and fades (Limpert: lining friction falls from 250 C)
        AxleDesign small = (AxleDesign) Designs.set(Designs.set(front, "brake", "kind", "disc"), "brake", "rotorThicknessM", "10");
        small = (AxleDesign) Designs.set(small, "brake", "rotorDiameterM", "240");
        WheelBench.FadeStop hot = WheelBench.axle(small).fade().get(9);
        assertTrue(hot.peakC() > last.peakC() && hot.torqueShare() < 0.95, "small disc " + hot.peakC() + " C, torque " + hot.torqueShare());
    }

    @Test void solidAxlesKeepTheirCamber() {
        WheelBench.AxleBench bench = WheelBench.axle(SampleWheels.offRoadRear());
        assertEquals(bench.travel().get(0).camberDeg(), bench.travel().get(20).camberDeg(), 1e-9);
    }

    @Test void theNoseOleoTakesAFar23Drop() {
        LandingGearDesign nose = SampleWheels.lightNoseGear();
        double s = nose.strut().staticCompressionM() / nose.strut().stroke();
        assertTrue(s > 0.5 && s < 0.92, "stands at " + s + " of its stroke");
        WheelBench.DropTest drop = WheelBench.drop(nose, WheelBench.FAR23_SINK);
        assertFalse(drop.bottomed(), "bottomed: " + drop.maxStrokeM());
        // light aircraft gear see load factors about 2-3.5 at the limit sink rate
        assertTrue(drop.loadFactor() > 1.5 && drop.loadFactor() < 4, "load factor " + drop.loadFactor());
        WheelBench.DropTest main = WheelBench.drop(SampleWheels.lightMainGear(), WheelBench.FAR23_SINK);
        assertFalse(main.bottomed(), "the spring leg bottomed: " + main.maxStrokeM());
        assertTrue(main.loadFactor() > 1.5 && main.loadFactor() < 4, "main load factor " + main.loadFactor());
    }

    @Test void aFixedLegIsAsItWas() {
        // 0.12: a leg without a retraction time compiles as before: no retraction, the same inputs and internal states
        var fixed = (dev.mcmod.aerocore.api.vehicle.part.LegPart) SampleWheels.lightMainGear().compile();
        assertEquals(null, fixed.retraction());
        assertEquals(List.of("brake"), fixed.inputs());
        var old = new dev.mcmod.aerocore.api.vehicle.part.LegPart(fixed.design(), fixed.wheel(), fixed.steer(), Math.toRadians(30), 0.05,
                SampleWheels.lightMainGear().brake().control(), fixed.mass(), List.of());
        assertEquals(old.internalStates(), fixed.internalStates(), "no state added to a fixed leg (old saves read)");
        assertTrue(Designs.read(Designs.write(SampleWheels.lightMainGear())).equals(SampleWheels.lightMainGear()), "reads back");
    }

    /** The sample main leg made retractable (folding in a way, in 3 s), on a side (centre: a body balances on it alone). */
    private static LandingGearDesign retractable(LegLayoutDesign.Side side, LegLayoutDesign.Retract way) {
        LandingGearDesign d = SampleWheels.lightMainGear();
        LegLayoutDesign l = d.leg();
        return new LandingGearDesign(d.name(), new LegLayoutDesign(l.name(), l.lengthM(), side, l.steer(), l.maxSteerDeg(), l.trailM(), 3.0, way),
                d.tyre(), d.wheel(), d.brake(), d.strut());
    }

    @Test void aRetractableLegStaysDownOnTheGroundAndFoldsInTheAir() {
        var inboard = retractable(LegLayoutDesign.Side.LEFT, LegLayoutDesign.Retract.INBOARD).retraction();
        assertEquals(1, inboard.axis().x(), 1e-12, "a left leg folds inboard about +x");
        assertEquals(-1, retractable(LegLayoutDesign.Side.RIGHT, LegLayoutDesign.Retract.INBOARD).retraction().axis().x(), 1e-12, "a right one about -x");
        LandingGearDesign gear = retractable(LegLayoutDesign.Side.CENTRE, LegLayoutDesign.Retract.AFT);
        var retraction = gear.retraction();
        assertEquals(Math.PI / 2, retraction.angleRad(), 1e-12);
        assertEquals(-1, retraction.axis().z(), 1e-12, "folding aft: about -z");
        // the tyre and wheel 0.25 on their frontal area, the spring bar 1.2 on its: between the two
        assertTrue(retraction.dragCoefficient() > 0.25 && retraction.dragCoefficient() < 1.2, "drag coefficient " + retraction.dragCoefficient());
        Vehicle v = new VehicleBlueprint("gear", List.of(
                new VehicleBlueprint.Entry("body", new BoxDesign("body", new Vec3d(1, 0.4, 1), 0, false, 1, MassSpec.given(480)), Mount.at(new Vec3d(0, 0.3, 0))),
                new VehicleBlueprint.Entry("leg", gear, Mount.ORIGIN, List.of(Binding.of("brake", "brake"), Binding.of("retract", "landing_gear")))), List.of()).compile();
        int fraction = dev.mcmod.aerocore.api.wheel.WheelCorner.SHOWN + dev.mcmod.aerocore.api.wheel.WheelCorner.EXTRA;
        var air = AIR;
        Ground flat = Ground.flat(0, GroundContact.Surface.TARMAC);
        VehicleState st = TestTrack.settle(v, flat, air, new Vec3d(0, 1.3, 0), 0, Map.of(), 2);
        var env = dev.mcmod.aerocore.api.vehicle.VehicleEnvironment.land(flat, air);
        for (int i = 0; i < 400; i++) {
            dev.mcmod.aerocore.api.vehicle.VehicleDynamics.step(v, st, env, Map.of("landing_gear", 1.0), Vec3d.Y.scale(-9.80665), List.of(), null, i * TestTrack.DT, TestTrack.DT);
        }
        assertEquals(0, st.shown("leg", fraction), "with weight on its wheel it stays down (load " + st.shown("leg", fraction - 1) + " kN, at "
                + st.body().position() + ", compression " + st.internal("leg", 0) + ")");
        // high in the air at 50 m/s, held up: it folds in its 3 s
        VehicleState up = v.newState(new Vec3d(0, 60, 0), dev.mcmod.aerocore.api.Orientation.IDENTITY, TestTrack.DT);
        up.setInternal("leg", 0, 0);   // the strut hangs extended (no weight on the wheel)
        up.setBody(new RigidBodyState(up.body().position(), new Vec3d(50, 0, 0), dev.mcmod.aerocore.api.Orientation.IDENTITY, Vec3d.ZERO));
        var lift = List.of(new dev.mcmod.aerocore.api.AppliedForce(dev.mcmod.aerocore.api.AppliedForce.Kind.LIFT, Vec3d.ZERO, Vec3d.Y.scale(v.properties().massKg() * 9.80665)));
        for (int i = 0; i < (int) Math.round(3.1 / TestTrack.DT); i++) {
            dev.mcmod.aerocore.api.vehicle.VehicleDynamics.step(v, up, env, Map.of("landing_gear", 1.0), Vec3d.Y.scale(-9.80665), lift, null, i * TestTrack.DT, TestTrack.DT);
        }
        assertEquals(1, up.shown("leg", fraction), 1e-9, "up in its time");
    }

    /** A family car: a 1100 kg box between the sample front and rear axles (2.65 m wheelbase), its centre of mass 0.55 m up. */
    private static Vehicle car(AxleDesign front, AxleDesign rear) {
        return new VehicleBlueprint("car", List.of(
                new VehicleBlueprint.Entry("body", new BoxDesign("body", new Vec3d(4.2, 1.0, 1.7), 0, false, 1, MassSpec.given(1100)), Mount.at(new Vec3d(0, 0.25, 0))),
                new VehicleBlueprint.Entry("front", front, Mount.at(new Vec3d(1.19, 0, 0)), List.of(Binding.of("brake", "brake"), Binding.of("steer", "steer"))),
                new VehicleBlueprint.Entry("rear", rear, Mount.at(new Vec3d(-1.46, 0, 0)), List.of(Binding.of("brake", "brake")))), List.of()).compile();
    }

    private static void moving(VehicleState st, double speed) {
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

    /** Stops from 25 m/s with the pedal down: distance (m) and the share of the time the front left wheel was locked (slip below -0.5). */
    private static double[] stop(Vehicle v) {
        VehicleState st = TestTrack.settle(v, FLAT, AIR, new Vec3d(0, 0.35, 0), 0, Map.of(), 2);
        moving(st, 25);
        double x0 = st.origin().x();
        List<TestTrack.Sample> trace = TestTrack.drive(v, st, FLAT, AIR, (t, s) -> Map.of("brake", 1.0), 10, s -> s.body().velocity().length() < 0.1, 1);
        long locked = trace.stream().filter(s -> s.speedMps() > 3 && s.diagnostics().get("front").get("leftSlipRatio") < -0.5).count();
        long moving = trace.stream().filter(s -> s.speedMps() > 3).count();
        return new double[]{st.origin().x() - x0, (double) locked / moving};
    }

    @Test void absKeepsTheWheelsTurningAndStopsShorter() {
        AxleDesign front = SampleWheels.sedanFront(), rear = SampleWheels.sedanRear();
        double[] abs = stop(car(front, rear));
        AxleDesign frontLocking = (AxleDesign) Designs.set(front, "brake", "abs", "false"), rearLocking = (AxleDesign) Designs.set(rear, "brake", "abs", "false");
        double[] locking = stop(car(frontLocking, rearLocking));
        assertTrue(abs[1] < 0.1, "with ABS the front wheel was locked " + abs[1] + " of the stop");
        assertTrue(locking[1] > 0.5, "without ABS it locks: " + locking[1]);
        // the peak's 1.0 g against the locked wheel's 0.72: the ABS stop is the shorter; a car stops from 90 km/h in 32-40 m
        assertTrue(abs[0] < locking[0], "ABS " + abs[0] + " m, locked " + locking[0] + " m");
        assertEquals(36, abs[0], 6);
    }
}
