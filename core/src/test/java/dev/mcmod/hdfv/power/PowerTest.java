package dev.mcmod.hdfv.power;

import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The workshop's power (0.8): cables, the grid's share, the line shaft's energy, an engine on a stand. */
class PowerTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    @Test void cablesCarryWhatTheTableSays() {
        assertEquals(24.1e3, PowerPlant.CableKind.LIGHT.ratedW(), 0.5e3);
        assertEquals(131e3, PowerPlant.CableKind.HEAVY.ratedW(), 2e3);
        double loss = PowerPlant.CableKind.LIGHT.lossAtRated(100);
        assertTrue(loss > 0.04 && loss < 0.08, "100 m of 6 mm^2 at 41 A: 3 I^2 R = 1.45 kW, 6 %: " + loss);
        assertTrue(PowerPlant.speedFactor(PowerPlant.NOMINAL_RPM) == 1 && PowerPlant.speedFactor(0.4 * PowerPlant.NOMINAL_RPM) == 0);
        assertEquals(0.75, PowerPlant.speedFactor(0.75 * PowerPlant.NOMINAL_RPM), 1e-12);
    }

    @Test void theGridFeedsMachinesFirstThenBatteriesAndLosesWithTheSquareOfTheLoad() {
        // a generator offering 100, a battery offering 50; a machine asking 120 and a battery asking 40
        List<Grid.Party> producers = List.of(new Grid.Party(100, false), new Grid.Party(50, true));
        List<Grid.Party> consumers = List.of(new Grid.Party(120, false), new Grid.Party(40, true));
        Grid.Flows f = Grid.share(producers, consumers, 1000, 0);
        assertEquals(120, f.got()[0], 1e-9, "the machine gets all it asks");
        assertEquals(0, f.got()[1], 1e-9, "nothing left to charge with");
        assertEquals(100, f.given()[0], 1e-9);
        assertEquals(20, f.given()[1], 1e-9, "the battery makes up the rest");
        // a cable too small
        Grid.Flows small = Grid.share(producers, consumers, 60, 0);
        assertEquals(60, small.totalGot(), 1e-9);
        // losses: 4 % at capacity, carrying half: 1 %
        Grid.Flows lossy = Grid.share(List.of(new Grid.Party(100, false)), List.of(new Grid.Party(50, false)), 100, 0.04);
        assertEquals(50, lossy.totalGiven(), 1e-9);
        assertEquals(50 * 0.99, lossy.totalGot(), 1e-9);
        assertEquals(lossy.totalGiven(), lossy.totalGot() + lossy.lost(), 1e-9, "energy kept");
        // surplus charges the battery
        Grid.Flows surplus = Grid.share(List.of(new Grid.Party(100, false)), List.of(new Grid.Party(30, false), new Grid.Party(50, true)), 1000, 0);
        assertEquals(30, surplus.got()[0], 1e-9);
        assertEquals(50, surplus.got()[1], 1e-9);
    }

    @Test void theLineShaftKeepsItsEnergy() {
        // a 15 kW motor and two machines asking 6 and 5 kW on 20 m of shaft, from rest
        double rpm = 0;
        List<ShaftLine.End> ends = List.of(new ShaftLine.End(15e3, 0, PowerPlant.MOTOR_INERTIA), new ShaftLine.End(0, 6e3, 0.5), new ShaftLine.End(0, 5e3, 0.5));
        double j = ShaftLine.inertia(ends, 20), dt = 0.05;
        for (int i = 0; i < 400; i++) {
            ShaftLine.Result r = ShaftLine.step(rpm, ends, 20, dt, Double.NaN);
            double e0 = 0.5 * j * Math.pow(PowerPlant.omega(rpm), 2), e1 = 0.5 * j * Math.pow(PowerPlant.omega(r.rpm()), 2);
            double given = r.givenW()[0], got = r.gotW()[1] + r.gotW()[2];
            assertEquals(given * dt, got * dt + r.frictionW() * dt + (e1 - e0), 1e-6 * Math.max(1, given * dt), "tick " + i);
            rpm = r.rpm();
        }
        assertEquals(PowerPlant.NOMINAL_RPM, rpm, 1e-6, "governed to its nominal speed");
        // too little power: the line keeps its speed, the machines get the share there is, and still the energy adds up
        List<ShaftLine.End> weak = List.of(new ShaftLine.End(5e3, 0, PowerPlant.MOTOR_INERTIA), new ShaftLine.End(0, 6e3, 0.5), new ShaftLine.End(0, 5e3, 0.5));
        double jw = ShaftLine.inertia(weak, 20);
        for (int i = 0; i < 200; i++) {
            ShaftLine.Result r = ShaftLine.step(rpm, weak, 20, dt, Double.NaN);
            double e0 = 0.5 * jw * Math.pow(PowerPlant.omega(rpm), 2), e1 = 0.5 * jw * Math.pow(PowerPlant.omega(r.rpm()), 2);
            assertEquals(r.givenW()[0] * dt, (r.gotW()[1] + r.gotW()[2] + r.frictionW()) * dt + (e1 - e0), 1e-6 * 5e3 * dt, "short " + i);
            assertEquals(5e3 - r.frictionW(), r.gotW()[1] + r.gotW()[2], 1e-6, "the machines share what is left");
            rpm = r.rpm();
        }
        assertEquals(PowerPlant.NOMINAL_RPM, rpm, 1e-6, "held at speed");
        // a person on a treadle turns a lathe directly (no shafting): it gets 75 W of its 2 kW, and works at that share
        ShaftLine.Result treadle = ShaftLine.step(PowerPlant.NOMINAL_RPM, List.of(new ShaftLine.End(PowerPlant.TREADLE_W, 0, 0.05), new ShaftLine.End(0, 2000, 0.5)), 0, dt, Double.NaN);
        assertEquals(PowerPlant.TREADLE_W, treadle.gotW()[1], 1e-9);
        // from rest it gets the line to speed before the machines take anything
        ShaftLine.Result start = ShaftLine.step(0, ends, 20, dt, Double.NaN);
        assertTrue(start.rpm() > 0 && start.gotW()[1] < 6e3, "spinning up");
    }

    @Test void anEngineOnAStandHoldsItsSpeedBurnsLikeItsMapAndStallsWhenOverloaded() {
        var four = SampleEngines.inlineFour();
        EngineUnit unit = new EngineUnit(four, 3000);
        unit.warmUp();
        unit.setRunning(true);
        unit.run(8, 0);
        assertTrue(unit.firing() && unit.engaged(), "started and engaged: " + unit.engineRpm());
        unit.run(10, 30e3);
        double rpm = unit.engineRpm();
        System.out.printf("stand: 30 kW at %.0f rpm (line %.1f), throttle %.2f, fuel %.2f g/s (%.0f g/kWh)%n", rpm, unit.lineRpm(), unit.throttle(),
                unit.fuelKgPerS() * 1000, unit.fuelKgPerS() / 30e3 * 3.6e9);
        assertEquals(3000, rpm, 0.02 * 3000, "governed within 2 %");
        assertEquals(PowerPlant.NOMINAL_RPM, unit.lineRpm(), 0.02 * PowerPlant.NOMINAL_RPM);
        assertEquals(30e3, unit.powerW(), 0.01 * 30e3);
        double bsfc = unit.fuelKgPerS() / 30e3 * 3.6e9;
        assertTrue(bsfc > 230 && bsfc < 420, "part-load BSFC " + bsfc);
        // the same with 0.005 s steps agrees within 2 % (the game steps 0.025 s)
        EngineUnit fine = new EngineUnit(four, 3000, 0.005);
        fine.setRunning(true);
        fine.run(8, 0);
        fine.run(10, 30e3);
        assertEquals(fine.engineRpm(), rpm, 0.02 * rpm, "speed at 0.025 s against 0.005 s");
        assertEquals(fine.fuelKgPerS(), unit.fuelKgPerS(), 0.02 * fine.fuelKgPerS(), "fuel at 0.025 s against 0.005 s");
        // overloaded far past its 125 kW: it slows and stalls
        unit.run(10, 250e3);
        assertTrue(unit.tripped() && !unit.running() && !unit.firing(), "overloaded, it stalls and trips: " + unit.engineRpm());
    }
}
