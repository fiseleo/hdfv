package dev.mcmod.hdfv.damage;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.damage.Corrosion;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.25: the weather, blasts and blows (Elements) in numbers: how fast a car rusts where it stands, what a block of TNT and a sword do, a tank's explosion. */
class ElementsTest {
    static final double DAY = 86400, HOUR = 3600;

    @BeforeAll static void register() { HdfvParts.register(); }
    @AfterEach void defaults() { DamagePace.reset(); }

    /** A steel car body's 0.8 mm skin, painted and sound, a real day of its chunk being loaded. */
    static double body(Corrosion.Exposure exposure, double used, double seconds) { return Elements.corrosion(Corrosion.Metal.CARBON_STEEL, exposure, used, 0.8, seconds); }

    @Test void aPaintedCarRustsOverDaysAndABareOneInTheSeaOverHours() {
        double rain = body(Corrosion.Exposure.RAIN, 0, DAY), air = body(Corrosion.Exposure.AIR, 0, DAY), dry = body(Corrosion.Exposure.DRY, 0, DAY),
                seaAir = body(Corrosion.Exposure.SEA_AIR, 0, DAY), bareSea = body(Corrosion.Exposure.SEA_WATER, 1, HOUR), dentedSea = body(Corrosion.Exposure.SEA_WATER, 0.5, HOUR);
        System.out.printf("a painted steel car body a real day: in the rain %.2f %%, inland air %.2f %%, sea air %.2f %%, under a roof %.3f %%; in the sea an hour: bare %.1f %%, half damaged %.1f %%%n",
                100 * rain, 100 * air, 100 * seaAir, 100 * dry, 100 * bareSea, 100 * dentedSea);
        // a noticeable few per cent over several real days in the rain
        assertTrue(rain > 0.005 && rain < 0.03, "a day in the rain: " + rain);
        assertTrue(5 * rain > 0.03 && 5 * rain < 0.15, "five days in the rain: " + 5 * rain);
        assertTrue(air < rain && dry < 0.1 * air && seaAir > rain, "under a roof least, by the sea most");
        // bare or damaged, in the sea: within hours
        assertTrue(bareSea > 0.02 && bareSea < 0.2, "bare in the sea an hour: " + bareSea);
        assertTrue(dentedSea > 0.4 * bareSea && dentedSea < bareSea, "half its paint gone: " + dentedSea);
        assertTrue(bareSea > 20 * body(Corrosion.Exposure.SEA_WATER, 0, HOUR), "paint is what keeps it");
        // other metals, thicker walls, composites
        assertTrue(Elements.corrosion(Corrosion.Metal.ALUMINIUM, Corrosion.Exposure.RAIN, 0, 0.8, DAY) < 0.02 * rain);
        assertEquals(rain / 5, Elements.corrosion(Corrosion.Metal.CARBON_STEEL, Corrosion.Exposure.RAIN, 0, 4, DAY), 1e-12);
        assertEquals(0, Elements.corrosion(Corrosion.Metal.NONE, Corrosion.Exposure.SEA_WATER, 1, 1, DAY), 0);
        assertEquals(0, Elements.corrosion(Corrosion.Metal.CARBON_STEEL, Corrosion.Exposure.SEA_WATER, 1, 0, DAY), 0);
        // the server's scale
        DamagePace.set(1000, 1, 0);
        assertEquals(0, body(Corrosion.Exposure.SEA_WATER, 1, DAY), 0);
        DamagePace.set(1000, 1, 3);
        assertEquals(3 * rain, body(Corrosion.Exposure.RAIN, 0, DAY), 1e-12);
    }

    @Test void aBlockOfTntBesideACarIsACrashAndFourBlocksOffABump() {
        Vehicle sedan = SampleBlueprints.sedan().compile();
        double touching = Elements.blastJ(42), away = Elements.blastJ(12), mass = sedan.properties().massKg();
        double crash50 = Impacts.energyJ(mass * 50 / 3.6, 50 / 3.6), crash15 = Impacts.energyJ(mass * 15 / 3.6, 15 / 3.6);
        System.out.printf("a TNT blast: touching %.0f kJ (a 50 km/h crash %.0f kJ), four blocks off %.0f kJ (a 15 km/h bump %.0f kJ)%n", touching / 1e3, crash50 / 1e3, away / 1e3, crash15 / 1e3);
        assertTrue(touching > Impacts.energyJ(mass * 40 / 3.6, 40 / 3.6) && touching < Impacts.energyJ(mass * 60 / 3.6, 60 / 3.6), "touching: a 40 to 60 km/h crash");
        assertTrue(away < 1.5 * crash15, "four blocks off: a bump");
        // beside the car's middle it is the body that takes it
        double body = 0, most = 0;
        for (Impacts.Dealt d : Impacts.hit(sedan, new Vec3d(2.4, 0.6, -1.0), touching)) {
            if (d.partId().equals("body")) { body = d.amount(); }
            most = Math.max(most, d.amount());
        }
        assertTrue(body > 0.15 && body < 0.6, "the body beside the blast: " + body);
        for (Impacts.Dealt d : Impacts.hit(sedan, new Vec3d(2.4, 0.6, -1.0), away)) { assertTrue(d.amount() < 0.05, "four blocks off: " + d); }
        assertEquals(0, Elements.blastJ(0), 0);
        assertTrue(Elements.blastJ(1e9) <= 5e6, "bounded");
    }

    @Test void aSwordCannotWreckACarAndAFuelTanksExplosionGoesWithItsFuel() {
        Vehicle sedan = SampleBlueprints.sedan().compile();
        Vehicle.Installed body = sedan.installed("body"), axle = sedan.installed("front_axle");
        // a sword's 7 points on the body: under a scratch; a thousand of them would not use it up
        assertTrue(Impacts.blow(body, Elements.blowJ(7)).isEmpty(), "a sword on the body: " + Impacts.blow(body, Elements.blowJ(7)));
        assertTrue(1000 * Elements.blowJ(7) / (Conditions.of(body.part()).toughnessJPerKg() * body.part().mass().massKg()) < 5);
        // on an axle it tells a little
        List<Impacts.Dealt> onAxle = Impacts.blow(axle, Elements.blowJ(9));
        assertTrue(!onAxle.isEmpty() && onAxle.get(0).amount() < 0.01, "an arrow on an axle: " + onAxle);
        // the part nearest a point by a front wheel is the front axle, by the nose the engine or the axle, not the body's far centre
        assertEquals("front_axle", Impacts.nearest(sedan, new Vec3d(4.1, 0.3, 0.8)).id());
        assertEquals("rear_axle", Impacts.nearest(sedan, new Vec3d(0.6, 0.3, -0.8)).id());
        assertEquals("body", Impacts.nearest(sedan, new Vec3d(2.2, 1.2, 0)).id());
        // a tank's explosion: a lawnmower's can to a tanker
        assertEquals(0, Elements.tankBlastPower(0), 0);
        assertTrue(Elements.tankBlastPower(1) > 1.9 && Elements.tankBlastPower(40) > 4 && Elements.tankBlastPower(40) < 5, "a car's tank: " + Elements.tankBlastPower(40));
        assertEquals(6, Elements.tankBlastPower(900), 0);
        // a leaking tank: nothing to half, a full tank in under a minute and a half near the end, all at once when holed through
        assertEquals(0, EngineWear.leakPerS(0.4), 0);
        assertTrue(1 / EngineWear.leakPerS(0.99) > 40 && 1 / EngineWear.leakPerS(0.99) < 90 && 1 / EngineWear.leakPerS(0.6) > 600);
    }
}
