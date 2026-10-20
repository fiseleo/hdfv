package dev.mcmod.hdfv.part.engine;

import dev.mcmod.aerocore.api.engine.Ramjet;
import dev.mcmod.aerocore.api.engine.Reheat;
import dev.mcmod.aerocore.api.engine.Scramjet;
import dev.mcmod.aerocore.api.engine.TurboRamjet;
import dev.mcmod.aerocore.api.engine.Turbojet;
import dev.mcmod.aerocore.api.propellant.SolidRocket;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.PartTypes;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The ramjet family, pulsejets, boosters and afterburners (0.10): the samples against their engines' published numbers. */
class JetFamilyTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    static List<PartDesign> samples() {
        return List.of(SampleJets.bomarcRamjet(), SampleJets.x51Scramjet(), SampleJets.hydrogenScramjet(), SampleJets.argus(), SampleJets.lockwood(),
                SampleJets.knsbMotor(), SampleJets.apcpBooster(), SampleJets.j79(), SampleJets.j58());
    }

    private static void within(double expected, double actual, double share, String what) {
        assertEquals(expected, actual, share * expected, what + ": " + actual + " against " + expected);
    }

    @Test void everySampleIsValidAndReadsBack() {
        for (PartDesign d : samples()) {
            List<DesignIssue> invalid = d.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList();
            assertTrue(invalid.isEmpty(), d.name() + ": " + invalid);
            PartDesign back = PartTypes.read(d.toJson(), "sample");
            assertEquals(d, back, d.name() + " JSON round trip");
        }
    }

    /**
     * The BOMARC-class ramjet: at its Mach 2.8, 18 km design point it gives what the ideal ramjet gives for its air
     * (Mattingly: V0 (sqrt(Tt4/Tt0) - 1) per kg) less its losses; the RJ43-MA-3's published 51 kN (Wikipedia, no
     * condition given) lies in what it gives over the BOMARC's ramjet flight, Mach 2 to 2.8 from sea level to 18 km.
     */
    @Test void theRamjetMatchesTheIdealCycleAndTheRj43() {
        RamjetDesign r = SampleJets.bomarcRamjet();
        Ramjet.Sizing s = r.model();
        Turbojet.Flight design = r.designFlight();
        double ideal = s.point().airflowKgPerS() * design.speedMps() * (Math.sqrt(s.designTemperatureK() / design.totalTemperature()) - 1);
        double share = s.point().thrustN() / ideal;
        assertTrue(share > 0.8 && share < 1.1, "design thrust over ideal " + share);
        double least = Double.POSITIVE_INFINITY, most = 0;
        for (double mach : new double[] {2, 2.4, 2.8}) {
            for (double h : new double[] {0, 6000, 12000, 18000}) {
                Ramjet.Point p = RamjetDesign.point(s, RamjetDesign.flight(mach, h), s.designTemperatureK());
                assertNotNull(p, "Mach " + mach + " at " + h + " m");
                least = Math.min(least, p.thrustN()); most = Math.max(most, p.thrustN());
            }
        }
        assertTrue(least < 51e3 && 51e3 < most, "RJ43 thrust between " + least + " and " + most);
    }

    /** The X-51A carried 120 kg of JP-7 for its 210 s scramjet run at Mach 5 near 20 km (Wikipedia; Edwards AFB, 2010). */
    @Test void theX51BurnsItsFuelInItsRun() {
        ScramjetDesign x = SampleJets.x51Scramjet();
        double start = x.selfStartMach();
        assertTrue(start >= 4 && start <= 5, "its inlet starts by itself after the booster (about Mach 4.5): " + start);
        Scramjet.Map map = x.model();
        ScramjetDesign.Running run = x.run(map, RamjetDesign.flight(5.1, 20000), 1, true, true, false);
        assertTrue(run.lit(), "lit at Mach 5.1");
        assertTrue(run.point().thrustN() > 0, "thrust");
        within(120, run.point().fuelKgPerS() * 210, 0.25, "fuel for 210 s, kg");
    }

    /** The Argus As 014 weighed 170 kg (Wikipedia); its reed valves lasted around half an hour to an hour or so. */
    @Test void theArgusWeighsAndWearsLikeTheV1s() {
        PulsejetDesign a = SampleJets.argus();
        within(170, a.totalMassKg(), 0.25, "Argus mass, kg");
        double hours = a.valve().reedLifeCycles() / 45 / 3600;
        assertTrue(hours > 0.5 && hours < 3, "reed life " + hours + " h");
    }

    /**
     * The boosters: KNSB delivers about 130 s at sea level in amateur motors (Nakka) and APCP about 240 s; the casings
     * hold their largest pressure with their safety factor.
     */
    @Test void theBoostersGiveTheirPropellantsImpulse() {
        SolidRocketDesign k = SampleJets.knsbMotor(), ap = SampleJets.apcpBooster();
        within(130, k.performance().specificImpulse(101325), 0.1, "KNSB Isp, s");
        within(240, ap.performance().specificImpulse(101325), 0.1, "APCP Isp, s");
        for (SolidRocketDesign s : List.of(k, ap)) {
            SolidRocket.Performance p = s.performance();
            assertTrue(s.casing().burstPa(s.grain().outerDiameterM()) > s.casing().safetyFactor() * p.maxPressurePa(), s.name() + " casing");
        }
    }

    /** The J79-GE-17: 1750 kg, 52.8 kN dry, 79.6 kN with afterburner at sea level (Wikipedia). */
    @Test void theJ79WeighsAndPushesLikeOne() {
        JetEngineDesign j = SampleJets.j79();
        JetEngineDesign.Model m = j.model();
        Reheat.Point wet = Reheat.turbojet(m.sizing(), m.sizing().point(), Turbojet.Flight.sea(0), j.afterburner().reheat(), j.afterburner().maxTemperatureK());
        within(1750, j.totalMassKg(), 0.25, "J79 mass, kg");
        within(52.8e3, m.sizing().point().thrustN(), 0.1, "J79 dry thrust, N");
        within(79.6e3, wet.thrustN(), 0.1, "J79 wet thrust, N");
    }

    /** The J58: about 2700 kg (the inlet spike is the airframe's) and about 151 kN with afterburner at sea level (Wikipedia). */
    @Test void theJ58WeighsAndPushesLikeOne() {
        TurboRamjetDesign t = SampleJets.j58();
        TurboRamjet.Point sea = TurboRamjet.at(t.sizing(), Turbojet.Flight.sea(0), t.control().turbineInletK(), t.afterburner().maxTemperatureK());
        within(2700, t.totalMassKg() - t.inlet().massKg(), 0.25, "J58 mass without its inlet, kg");
        within(151e3, sea.thrustN(), 0.15, "J58 wet thrust, N");
    }
}
