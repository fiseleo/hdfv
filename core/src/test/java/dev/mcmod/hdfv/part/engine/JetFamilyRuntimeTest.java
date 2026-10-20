package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.Atmosphere;
import dev.mcmod.aerocore.api.FluidState;
import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.RigidBodyState;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Binding;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.VehicleDynamics;
import dev.mcmod.aerocore.api.vehicle.VehicleEnvironment;
import dev.mcmod.aerocore.api.vehicle.VehicleState;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The 0.10 engines' runtime rules on a stand held at a flight condition: lighting, running, going out, wear, burnout. */
class JetFamilyRuntimeTest {
    static { HdfvParts.register(); }

    private static final double DT = 0.005;

    /** A stand: one engine held at a speed along its axis (+x) in still air at an altitude, its inputs bound to channels of their names. */
    static final class Stand {
        final Vehicle vehicle;
        final VehicleState state;
        final Map<String, Double> controls = new HashMap<>();
        VehicleEnvironment environment;
        Vec3d velocity = Vec3d.ZERO;
        double t;
        VehicleDynamics.Result last;

        Stand(PartDesign d) {
            List<Binding> bindings = d.compile().inputs().stream().map(r -> Binding.of(r, r)).toList();
            vehicle = new VehicleBlueprint("stand", List.of(new VehicleBlueprint.Entry("engine", d, Mount.at(Vec3d.ZERO), bindings)), List.of()).compile();
            vehicle.warmUp();
            state = vehicle.newState(Vec3d.ZERO, Orientation.IDENTITY, DT);
            at(0, 0);
            run(DT);
        }

        Stand at(double mach, double altitudeM) {
            FluidState air = Atmosphere.atAltitude(altitudeM, Vec3d.ZERO).fluid();
            environment = VehicleEnvironment.calm(-1000, FluidState.FRESH_WATER_20C, air);
            velocity = new Vec3d(mach * air.speedOfSoundMps(), 0, 0);
            return this;
        }

        Stand set(String channel, double value) { controls.put(channel, value); return this; }

        Stand run(double seconds) {
            for (int i = 0; i < Math.round(seconds / DT); i++, t += DT) {
                state.setBody(new RigidBodyState(Vec3d.ZERO, velocity, Orientation.IDENTITY, Vec3d.ZERO));
                last = VehicleDynamics.step(vehicle, state, environment, controls, Vec3d.ZERO, List.of(), null, t, DT);
            }
            return this;
        }

        double d(String key) { return last.diagnostics().get("engine").get(key); }
    }

    @Test void theRamjetLightsInItsWindowAndGoesOut() {
        Stand s = new Stand(SampleJets.bomarcRamjet()).set("throttle", 1).set("ignition", 1);
        s.at(0.8, 0).run(0.5);
        assertEquals(0, s.d("lit"), "below its light-up Mach it does not light");
        assertTrue(s.d("thrustN") <= 0);
        s.at(2.0, 10000).run(1);
        assertEquals(1, s.d("lit"), "boosted past Mach 1.5 it lights");
        assertTrue(s.d("thrustN") > 5e3, "thrust " + s.d("thrustN"));
        assertTrue(s.d("fuelKgPerS") > 0);
        s.set("throttle", 0).run(0.5);
        assertEquals(0, s.d("lit"), "the fuel cut, it goes out");
        assertTrue(s.d("thrustN") < 0, "an unlit duct only drags: " + s.d("thrustN"));
        s.set("throttle", 1).at(1.2, 10000).run(0.5);
        assertEquals(0, s.d("lit"), "too slow to light again");
    }

    @Test void theScramjetStartsLightsAndRestartsOnlyWithTheFuelCut() {
        Stand s = new Stand(SampleJets.x51Scramjet()).set("throttle", 1).set("ignition", 1);
        s.at(5, 20000).run(1);
        assertEquals(1, s.d("started"));
        assertEquals(1, s.d("lit"), "the pilot lights the kerosene at Mach 5");
        double lit = s.d("thrustN");
        assertTrue(lit > 0 && s.d("fuelKgPerS") > 0, "thrust " + lit);
        s.set("throttle", 0).run(0.5);
        assertEquals(0, s.d("lit"));
        assertTrue(s.d("thrustN") < lit, "unlit, its duct drags");
        s.set("throttle", 1).at(3, 15000).run(0.5);
        assertEquals(0, s.d("started"), "below its range the inlet unstarts");
        s.at(5, 20000).run(1.5);
        assertEquals(1, s.d("started"), "the control cut the fuel: it started again");
        assertEquals(1, s.d("lit"), "and the fuel came back");
    }

    @Test void theArgusNeedsStartingAirRunsOnItsOwnAndWearsItsReeds() {
        PulsejetDesign a = SampleJets.argus();
        Stand s = new Stand(a).set("throttle", 1).set("ignition", 1);
        s.run(0.5);
        assertEquals(0, s.d("lit"), "at rest it does not start by itself");
        assertTrue(s.d("thrustN") <= 0);
        s.set("start", 1).run(0.3).set("start", 0).run(1);
        assertEquals(1, s.d("lit"), "blown, it runs on after the air is shut");
        double thrust = s.d("thrustN");
        assertEquals(2224, thrust, 0.3 * 2224, "about the Argus's static thrust");
        assertEquals(45, s.d("frequencyHz"), 15);
        // worn reeds: half of the way from worn to broken leaks half the thrust; broken, nothing
        double life = a.valve().reedLifeCycles();
        s.state.setInternal("engine", PulsejetDesign.STATE_CYCLES, 1.0 * life);
        s.run(0.05);
        assertEquals(0.5 * thrust, s.d("thrustN"), 0.1 * thrust);
        s.state.setInternal("engine", PulsejetDesign.STATE_CYCLES, 1.3 * life);
        s.run(0.05);
        assertEquals(0, s.d("lit"));
        s.set("start", 1).run(0.3).set("start", 0).run(0.5);
        assertEquals(0, s.d("lit"), "broken reeds do not start");
        // new reeds
        s.state.setInternal("engine", PulsejetDesign.STATE_CYCLES, 0);
        s.set("start", 1).run(0.3).set("start", 0).run(1);
        assertEquals(1, s.d("lit"));
        assertEquals(thrust, s.d("thrustN"), 0.05 * thrust);
        s.set("throttle", 0).run(1);
        assertEquals(0, s.d("lit"), "the fuel shut, it stops");
        // ram air starts it without the starting air
        Stand moving = new Stand(a).set("throttle", 1).set("ignition", 1).at(0.22, 0).run(1);
        assertEquals(1, moving.d("lit"), "at 75 m/s the ram air starts it");
    }

    @Test void theValvelessPulsejetRunsToo() {
        Stand s = new Stand(SampleJets.lockwood()).set("throttle", 1).set("ignition", 1);
        s.set("start", 1).run(0.3).set("start", 0).run(1);
        assertEquals(1, s.d("lit"));
        assertTrue(s.d("thrustN") > 0, "thrust " + s.d("thrustN"));
        assertTrue(s.d("frequencyHz") > 80, "a short tube rings fast: " + s.d("frequencyHz"));
    }

    @Test void aBoosterFiresOnceBurnsItsGrainAndIsReloadedOrCastOff() {
        SolidRocketDesign k = SampleJets.knsbMotor();
        Stand s = new Stand(k);
        double full = s.state.tankKg(0), density = k.grain().propellantModel().densityKgPerM3();
        assertEquals(k.grain().propellantKg(), full, 1e-9, "built full");
        s.run(0.5);
        assertEquals(0, s.d("burning"), "not until it is fired");
        assertEquals(full, s.state.tankKg(0), 1e-6);
        s.set("fire", 1).run(0.1);
        assertEquals(1, s.d("burning"));
        double impulse = 0;
        for (double time = 0; time < k.performance().burnTimeS() + 0.5; time += DT) { s.run(DT); impulse += s.d("thrustN") * DT; }
        assertEquals(0, s.d("burning"), "burnt out");
        assertEquals(0, s.d("thrustN"));
        assertTrue(s.state.tankKg(0) < 0.01 * full, "the grain is gone: " + s.state.tankKg(0));
        assertEquals(k.performance().totalImpulse(101325), impulse, 0.05 * k.performance().totalImpulse(101325), "its impulse");
        assertEquals(List.of(), s.state.takeJettisons(), "a reloadable motor stays");
        s.set("fire", 0).run(0.05).set("fire", 1).run(0.1);
        assertEquals(0, s.d("burning"), "an empty motor does not light");
        s.state.setTank(0, k.grain().propellant(), full, density);   // a new grain
        s.set("fire", 0).run(0.05).set("fire", 1).run(0.1);
        assertEquals(1, s.d("burning"), "reloaded, it fires again");

        SolidRocketDesign ap = SampleJets.apcpBooster();
        Stand b = new Stand(ap).set("fire", 1);
        b.run(ap.performance().burnTimeS() + 0.5);
        assertEquals(0, b.d("burning"));
        assertEquals(List.of("engine"), b.state.takeJettisons(), "the APCP booster asks to be cast off when burnt out");
    }

    /** A copy of a sample that starts in seconds (startRate 10 %), as the world's GameTests use. */
    static JetControlDesign quick(JetControlDesign c) {
        return new JetControlDesign(c.name(), c.fuel(), c.turbineInletK(), c.idleFraction(), c.maxSpeedFraction(), c.accelerationSchedule(), c.surgeReserve(),
                c.starterTorqueNm(), c.lightOffFraction(), 0.1, c.massGivenKg());
    }

    @Test void theAfterburnerLightsAtFullPowerOnly() {
        JetEngineDesign j = SampleJets.j79();
        JetEngineDesign e = new JetEngineDesign(j.name(), j.inlet(), j.compressor(), j.combustor(), j.turbine(), j.nozzle(), quick(j.control()), j.afterburner());
        Stand s = new Stand(e).set("throttle", 1).set("ignition", 1);
        s.run(40);
        assertTrue(s.d("speedFraction") > 0.95, "spooled up: " + s.d("speedFraction"));
        double dry = s.d("thrustN");
        s.set("reheat", 1).run(3);
        assertTrue(s.d("reheatK") > 1800, "lit: " + s.d("reheatK"));
        assertEquals(79.6e3 / 52.8e3, s.d("thrustN") / dry, 0.15, "wet over dry as the J79's");
        s.set("reheat", 0).run(3);
        assertEquals(0, s.d("reheatK"));
        assertEquals(dry, s.d("thrustN"), 0.03 * dry);
        s.set("throttle", 0).run(20).set("reheat", 1).run(2);
        assertEquals(0, s.d("reheatK"), "not at idle");
    }

    @Test void theTurboRamjetBleedsAroundItsCoreFast() {
        TurboRamjetDesign j = SampleJets.j58();
        TurboRamjetDesign e = new TurboRamjetDesign(j.name(), j.inlet(), j.compressor(), j.combustor(), j.turbine(), j.nozzle(), quick(j.control()), j.afterburner(), j.bypass());
        Stand s = new Stand(e).set("throttle", 1).set("ignition", 1);
        s.run(40);
        assertTrue(s.d("speedFraction") > 0.95, "spooled up: " + s.d("speedFraction"));
        assertEquals(0, s.d("bypassKgPerS"), 1e-9, "shut at rest");
        s.set("reheat", 1).run(3);
        double sea = s.d("thrustN");
        assertEquals(151e3, sea, 0.2 * 151e3);
        for (double mach = 0.5; mach <= 3.0001; mach += 0.25) { s.at(mach, Math.min(24000, 8000 * mach)).run(2); }
        assertEquals(1, s.d("lit"), "still burning at Mach 3");
        assertTrue(s.d("bypassKgPerS") > 0.2 * s.d("coreKgPerS"), "bleeding round the core: " + s.d("bypassKgPerS") + " of " + s.d("coreKgPerS"));
        assertTrue(s.d("thrustN") > 0);
    }
}
