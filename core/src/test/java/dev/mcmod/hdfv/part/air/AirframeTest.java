package dev.mcmod.hdfv.part.air;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Wings, air propellers and rotors: their checks and masses, and the sample seaplane and helicopter flying (offline). */
class AirframeTest {
    static { HdfvParts.register(); }
    private static final double DT = 0.005;
    private static final Vec3d GRAVITY = new Vec3d(0, -9.80665, 0);
    private static final FluidState AIR = Atmosphere.atAltitude(0, Vec3d.ZERO).fluid(), WATER = FluidState.FRESH_WATER_20C;

    private static boolean invalid(PartDesign d, String parameter) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().startsWith(parameter));
    }

    @Test void samplesWeighWhatTheirClassesDo() {
        // light aircraft aluminium wings: 8-12 kg/m^2 (Raymer); UH-60 blades about 116 kg each; a 2.59 m three-blade turboprop propeller tens of kg
        WingDesign light = SampleAirframes.lightWing();
        double perArea = light.totalMassKg() / light.planform().planform().areaM2();
        assertTrue(perArea > 6 && perArea < 13, "light wing " + perArea + " kg/m^2");
        assertEquals(116, SampleAirframes.mainRotor().blades().bladeMassKg(), 12);
        double prop = SampleAirframes.turbopropPropeller().totalMassKg();
        assertTrue(prop > 20 && prop < 70, "propeller " + prop + " kg");
        assertEquals(7.5, new dev.mcmod.aerocore.api.aero.HelicopterRotor(SampleAirframes.mainRotor().geometry()).lockNumber(1.225), 1.5);
    }

    @Test void checksStopBrokenAirframes() {
        WingDesign w = SampleAirframes.lightWing();
        // spar caps a tenth as thick yield at the design load
        assertTrue(invalid(Designs.set(w, "structure", "capThicknessM", "0.3"), "structure.capThicknessM"));
        // ailerons overlapping the flaps
        assertTrue(invalid(Designs.set(w, "aileron", "fromSpan", "0.3"), "aileron.fromSpan"));
        // ailerons on a half wing
        assertTrue(invalid(Designs.set(w, "planform", "mirrored", "false"), "aileron.role"));
        // a CST section whose upper surface dips below the lower
        PartDesign cst = Designs.set(w, "rootSection", "family", "cst");
        assertFalse(invalid(cst, "rootSection"), "the fitted CST weights are a good shape");
        assertTrue(invalid(Designs.set(cst, "rootSection", "upper1", "-0.5"), "rootSection.upper1"));
        // a propeller spun until its tips are supersonic standing still
        assertTrue(invalid(Designs.set(SampleAirframes.turbopropPropeller(), "hub", "maxRpm", "4000"), "hub.maxRpm"));
        // a governor needs blades that turn
        assertTrue(invalid(Designs.set(SampleAirframes.fixedPropeller(), "hub", "governor", "constant_speed"), "hub.governor"));
        // a teetering hub carries two blades
        assertTrue(invalid(Designs.set(SampleAirframes.mainRotor(), "hub", "hubType", "teetering"), "hub.hubType"));
        // a hinge outside the root cutout
        assertTrue(invalid(Designs.set(SampleAirframes.mainRotor(), "hub", "hingeOffset", "0.25"), "hub.hingeOffset"));
    }

    @Test void theSeaplaneLeavesTheWater() {
        Vehicle v = SampleBlueprints.seaplane().compile();
        VehicleState st = v.newState(new Vec3d(0, -0.2, 0), Orientation.IDENTITY, DT);
        VehicleEnvironment env = VehicleEnvironment.calm(0, WATER, AIR);
        double airborne = Double.NaN, speedAtLiftOff = 0, rpmSeen = 0;
        Vec3d start = null;
        for (int i = 0; i <= (int) (75 / DT) && Double.isNaN(airborne); i++) {
            double t = i * DT;
            RigidBodyState body = st.body();
            double speed = body.velocity().length();
            double pitchDeg = Math.toDegrees(Math.asin(body.orientation().rotate(Vec3d.X).y()));
            double rollDeg = Math.toDegrees(Math.asin(body.orientation().rotate(Vec3d.Z).y()));
            // a pilot: 5 deg on the step, rotate to 8 deg from 28 m/s, wings level
            double pitch = Math.clamp(0.15 * ((speed > 28 ? 8 : 5) - pitchDeg) - 0.3 * body.angularVelocity(v.properties()).z(), -1, 1);
            VehicleDynamics.Result r = VehicleDynamics.step(v, st, env, Map.of("power", 1.0, "flap", 1.0, "pitch", pitch, "roll", Math.clamp(0.05 * rollDeg, -1, 1)),
                    GRAVITY, List.of(), null, t, DT);
            rpmSeen = Math.max(rpmSeen, r.diagnostics().get("propeller").get("rps") * 60);
            if (start == null && r.diagnostics().get("propeller").get("rps") * 60 > 2100) { start = body.position(); }
            if (v.origin(st.body()).y() > 1.0) { airborne = t; speedAtLiftOff = speed; }
        }
        Vec3d heading = st.body().orientation().rotate(Vec3d.X);
        double headingDeg = Math.toDegrees(Math.atan2(heading.z(), heading.x()));
        System.out.printf("seaplane: airborne at %.1f s, %.1f m/s, after %.0f m of water run, heading %.1f deg%n", airborne, speedAtLiftOff,
                start == null ? Double.NaN : st.body().position().subtract(start).length(), headingDeg);
        assertFalse(Double.isNaN(airborne), "the seaplane never left the water");
        assertEquals(2200, rpmSeen, 50, "the governor holds the propeller near its rated speed");
        // lift-off at 1.0-1.2 times the flapped stall speed (CL about 2 on 35 m^2 at about 2.7 t: 24 m/s)
        assertTrue(speedAtLiftOff > 22 && speedAtLiftOff < 32, "lift-off at " + speedAtLiftOff + " m/s");
        double run = st.body().position().subtract(start).length();
        assertTrue(run < 700, "water run " + run + " m (a Twin Otter on floats, twice the weight on twice the power: about 600 m to 15 m)");
        // hands off the rudder it runs straight (the floats' cross-flow leaves their keels: PlaningDynamics since AeroCore 0.23)
        assertEquals(0, headingDeg, 10, "heading at lift-off");
    }

    /**
     * Starting up free to turn (held only in place, sinking 0.3 m/s as a pad's contact lets it, with a little tumble): the
     * rotor barely turning once fed on its own inflow (AeroCore 0.22's Drees variation at an advance ratio in the tens)
     * and the gyroscopic couple taken explicitly gained energy; either crashed the GameTest server within seconds.
     */
    @Test void theHelicopterStartsUpFreeToTurn() {
        Vehicle v = SampleBlueprints.helicopter().compile();
        VehicleState st = v.newState(new Vec3d(0, 100, 0), Orientation.IDENTITY, DT);
        Vec3d place = st.body().position();
        st.setBody(new RigidBodyState(place, Vec3d.ZERO, Orientation.IDENTITY, v.properties().inertia().apply(new Vec3d(0.05, 0, 0.05))));
        VehicleEnvironment env = VehicleEnvironment.calm(-1000, WATER, AIR);
        double pitchRoll = 0, rpm = 0;
        for (int i = 0; i <= (int) (30 / DT); i++) {
            RigidBodyState held = st.body();
            st.setBody(new RigidBodyState(place, new Vec3d(0, -0.3, 0), held.orientation(), held.angularMomentum()));
            VehicleDynamics.Result r = VehicleDynamics.step(v, st, env, Map.of("power", 1.0), GRAVITY, List.of(), null, i * DT, DT);
            Vec3d w = st.body().orientation().inverse().rotate(st.body().angularVelocity(v.properties()));
            pitchRoll = Math.max(pitchRoll, Math.hypot(w.x(), w.z()));
            rpm = r.diagnostics().get("rotor").get("rpm");
        }
        assertTrue(rpm > 30, "the rotor comes up to speed: " + rpm + " rpm");
        // unheld in yaw it turns against the rotor's torque (on a pad the skids hold it); pitch and roll stay calm
        assertTrue(pitchRoll < 0.3, "pitch and roll rates up to " + pitchRoll + " rad/s");
    }

    @Test void theHelicopterSpinsUpAndHovers() {
        Vehicle v = SampleBlueprints.helicopter().compile();
        assertEquals(1, v.freewheels().size(), "the rotor is joined through a freewheel");
        VehicleState st = v.newState(new Vec3d(0, 100, 0), Orientation.IDENTITY, DT);
        VehicleEnvironment env = VehicleEnvironment.calm(-1000, WATER, AIR);
        Vec3d hold = st.body().position();
        double integral = 0, heightAtRelease = 0, minHeight = 1e9, maxHeight = -1e9, maxTilt = 0, maxYawRate = 0;
        Map<String, Double> rotor = null;
        for (int i = 0; i <= (int) (75 / DT); i++) {
            double t = i * DT;
            boolean held = t < 45;
            if (held) { st.setBody(new RigidBodyState(hold, Vec3d.ZERO, Orientation.IDENTITY, Vec3d.ZERO)); }
            RigidBodyState body = st.body();
            Vec3d w = body.orientation().inverse().rotate(body.angularVelocity(v.properties()));
            double pitchDeg = Math.toDegrees(Math.asin(body.orientation().rotate(Vec3d.X).y()));
            double rollDeg = Math.toDegrees(Math.asin(body.orientation().rotate(Vec3d.Z).y()));
            double vy = body.velocity().y();
            double collective = 0.25;
            if (!held) { integral += -vy * DT; collective = Math.clamp(0.25 + 0.08 * -vy + 0.04 * integral, 0, 1); }
            Map<String, Double> controls = Map.of("power", 1.0, "collective", collective, "pitch", held ? 0 : Math.clamp(-0.05 * pitchDeg, -1, 1),
                    "roll", held ? 0 : Math.clamp(0.05 * rollDeg, -1, 1), "rudder", held ? 0 : Math.clamp(1.5 * w.y(), -1, 1));
            VehicleDynamics.Result r = VehicleDynamics.step(v, st, env, controls, GRAVITY, List.of(), null, t, DT);
            rotor = r.diagnostics().get("rotor");
            if (t >= 45 && heightAtRelease == 0) { heightAtRelease = body.position().y(); }
            if (t > 55) {
                minHeight = Math.min(minHeight, body.position().y()); maxHeight = Math.max(maxHeight, body.position().y());
                maxTilt = Math.max(maxTilt, Math.max(Math.abs(pitchDeg), Math.abs(rollDeg)));
                maxYawRate = Math.max(maxYawRate, Math.abs(w.y()));
            }
        }
        assertEquals(258, rotor.get("rpm"), 8, "the turboshaft's governor holds the rotor at its design speed through the gearbox");
        double weight = (v.properties().massKg() + st.fuelKg()) * 9.80665;   // the dry vehicle and the fuel in its tank (0.8)
        assertEquals(weight, rotor.get("thrustN"), 0.1 * weight, "the rotor carries the weight");
        assertTrue(maxHeight - minHeight < 5, "hover holds its height: " + minHeight + " to " + maxHeight);
        assertTrue(maxTilt < 10, "and stays level: " + maxTilt + " deg");
        assertTrue(maxYawRate < 0.3, "the tail rotor holds the heading: " + maxYawRate + " rad/s");
        double power = rotor.get("powerW");
        assertTrue(power > 300e3 && power < 900e3, "hover power " + power / 1000 + " kW (momentum theory at FM 0.6-0.8: 420-560 kW)");
    }
}
