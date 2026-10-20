package dev.mcmod.hdfv.fluid;

import dev.mcmod.hdfv.manufacture.Pace;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Fluids, pipes and pumps (0.8). */
class FluidTest {
    @Test void everyFuelIsAFluid() {
        assertEquals(18, HdfvFluids.all().size());   // 0.10: liquid and 700 bar hydrogen
        for (String f : FuelGrades.fluids()) { assertNotNull(HdfvFluids.of(f), f); }
        for (HdfvFluids.Spec s : HdfvFluids.all()) {
            // hydrogen, liquid or at 700 bar, is the light one
            assertTrue(s.densityKgPerM3() > (s.id().contains("hydrogen") ? 30 : 400) && s.densityKgPerM3() < 1100, s.id());
            assertTrue(s.viscosityPaS() > 0 && s.viscosityPaS() < 1, s.id());
        }
        assertTrue(HdfvFluids.of("heavy_crude_oil").densityKgPerM3() > HdfvFluids.of("crude_oil").densityKgPerM3());
    }

    @Test void thePumpDrivesLessUphillAndLessOfThickOil() {
        double level = Piping.flowM3PerS(HdfvFluids.WATER, 10, 0), up = Piping.flowM3PerS(HdfvFluids.WATER, 10, 30);
        double far = Piping.flowM3PerS(HdfvFluids.WATER, 200, 0), thick = Piping.flowM3PerS(HdfvFluids.of("fuel_oil"), 10, 0);
        System.out.printf("pump: water level %.2f l/s, 30 m up %.2f, 200 m of pipe %.2f; fuel oil %.2f%n", level * 1000, up * 1000, far * 1000, thick * 1000);
        assertTrue(level > 0.005 && level < Piping.PUMP.runoutFlowM3PerS(), "short and level, past its best point");
        assertTrue(up < level && far < level && thick < level);
        assertEquals(0, Piping.flowM3PerS(HdfvFluids.WATER, 10, 70), 0, "beyond its 60 m shutoff head");
        // a tick of work at the default pace moves the flow for 3.6 s
        assertEquals(3.6, Pace.workSecondsPerTick(Pace.TICKS_PER_WORK_HOUR), 1e-12);
        assertEquals(level * 3600, Piping.litresPerTick(level, 3.6), 1e-9);
        // its motor never draws more than it is rated for
        for (double q = 0.0005; q < Piping.PUMP.runoutFlowM3PerS(); q += 0.0005) {
            assertTrue(Piping.pumpW(HdfvFluids.WATER, q) <= dev.mcmod.aerocore.api.power.ElectricMachine.of(Piping.PUMP_MOTOR_W).inputForOutput(Piping.PUMP_MOTOR_W) + 1e-9);
        }
        assertTrue(Piping.pumpW(HdfvFluids.WATER, level) > 1000, "a kilowatt or more");
    }

    @Test void aStorageTankHoldsNineCubicMetresACourse() {
        Map<List<Integer>, String> at = new HashMap<>();
        for (Pattern.Cell c : Structures.STORAGE_TANK.cells(0, 0, 0, 2, 4)) { if (c.kind() != null) { at.put(List.of(c.x(), c.y(), c.z()), c.kind()); } }
        Pattern.Match m = Structures.STORAGE_TANK.match((x, y, z) -> at.get(List.of(x, y, z)), 0, 0, 0, 2);
        assertNotNull(m);
        assertEquals(4, m.repeats());
        assertEquals(45000, Structures.storageLitres(m));
    }
}
