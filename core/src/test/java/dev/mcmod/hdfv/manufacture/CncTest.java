package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CNC axes and setups, and every machine's electricity (0.8). */
class CncTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    private static Workpiece piece(PartPlan p, String key) { return p.workpieces().stream().filter(w -> w.key().equals(key)).findFirst().orElseThrow(); }

    /** The first cutting step of a route, and the count of steps from it. */
    private static int firstCut(Workpiece w) {
        for (int i = 0; i < w.route().size(); i++) { if (w.route().get(i).process().cutting()) { return i; } }
        return -1;
    }
    private static int cuts(Workpiece w, int from) {
        int n = 0;
        while (from + n < w.route().size() && w.route().get(from + n).process().cutting()) { n++; }
        return n;
    }

    @Test void thePresetsHaveTheirAxesAndRatings() {
        assertEquals(List.of(3, 4, 5, 5, 9, 12), CncConfig.PRESETS.stream().map(CncConfig::axes).toList());
        assertEquals(5, CncConfig.VMC_5.simultaneous());
        assertEquals(5, CncConfig.MILL_TURN_12.simultaneous());
        assertEquals(4, CncConfig.VMC_4.simultaneous());
        assertThrows(IllegalArgumentException.class, () -> new CncConfig(true, false, true, false, 0, false), "a rotary table on a mill-turn");
        assertThrows(IllegalArgumentException.class, () -> new CncConfig(false, false, false, true, 0, false), "a sub-spindle on a machining centre");
        assertThrows(IllegalArgumentException.class, () -> new CncConfig(false, true, true, true, 3, false), "three turrets");
        System.out.printf("CNC rated: VMC 3 %.1f kW, VMC 5 %.1f kW, mill-turn 12 %.1f kW%n", CncConfig.VMC_3.ratedW() / 1000, CncConfig.VMC_5.ratedW() / 1000,
                CncConfig.MILL_TURN_12.ratedW() / 1000);
        assertTrue(CncConfig.VMC_3.ratedW() > 24e3 && CncConfig.VMC_3.ratedW() < 32e3);
        assertTrue(CncConfig.MILL_TURN_12.ratedW() > 48e3 && CncConfig.MILL_TURN_12.ratedW() < 65e3);
        for (int i = 1; i < CncConfig.PRESETS.size(); i++) {
            CncConfig a = CncConfig.PRESETS.get(i - 1), b = CncConfig.PRESETS.get(i);
            if (a.turningSpindle() == b.turningSpindle()) { assertTrue(b.ratedW() > a.ratedW(), b.id()); }
        }
        // a lathe at full load: about 33 FE a tick at 1 kJ an FE and 3.6 s a tick (the plan's figure)
        assertEquals(33, Station.LATHE.ratedW() * 3.6 / 1000, 2);
    }

    @Test void bladesAndOrificesNeedFiveAxes() {
        Workpiece blades = piece(Plans.of(SampleEngines.turbojet()), "compressor_blades");
        Operation aerofoil = blades.route().stream().filter(Operation::complex).findFirst().orElseThrow();
        assertEquals(Operation.COMPLEX_AXES, aerofoil.axes());
        assertFalse(CncConfig.VMC_3.can(aerofoil));
        assertFalse(CncConfig.VMC_4.can(aerofoil));
        assertTrue(CncConfig.VMC_5.can(aerofoil));
        assertTrue(CncConfig.MILL_TURN_12.can(aerofoil));
        Workpiece injector = piece(Plans.of(SampleEngines.inlineFour()), "injector");
        Operation orifices = injector.route().stream().filter(Operation::complex).findFirst().orElseThrow();
        assertFalse(CncConfig.VMC_4.can(orifices));
        assertTrue(CncConfig.MILL_TURN_9.can(orifices));
        // and turning needs a turning spindle
        Operation turn = injector.route().stream().filter(o -> o.process() == Process.TURN).findFirst().orElseThrow();
        assertFalse(CncConfig.VMC_5.can(turn));
        assertTrue(CncConfig.MILL_TURN_5.can(turn));
    }

    @Test void theBlockTakesSixClampingsOnThreeAxesAndOneOrTwoOnFive() {
        Workpiece block = piece(Plans.of(SampleEngines.inlineFour()), "block");
        int from = firstCut(block), n = cuts(block, from);
        assertEquals(6, Setups.count(block, from, n, CncConfig.VMC_3));
        assertEquals(3, Setups.count(block, from, n, CncConfig.VMC_4));
        int five = Setups.count(block, from, n, CncConfig.VMC_5);
        assertTrue(five >= 1 && five <= 2, "five axes: " + five);
        Plans.Job three = Plans.job(block, from, n, Station.CNC, CncConfig.VMC_3), fiveAxes = Plans.job(block, from, n, Station.CNC, CncConfig.VMC_5);
        assertTrue(fiveAxes.prepareS() < three.prepareS() && fiveAxes.pieceS() < three.pieceS(), "fewer clampings, less time");
        System.out.printf("block: 3 axes %d setups %.0f + %.0f s, 5 axes %d setups %.0f + %.0f s%n", three.setups(), three.prepareS(), three.pieceS(),
                fiveAxes.setups(), fiveAxes.prepareS(), fiveAxes.pieceS());
    }

    @Test void twelveAxesMakeACrankInOneClampingFasterThanALatheAndADrill() {
        Workpiece crank = piece(Plans.of(SampleEngines.inlineFour()), "crank");
        // 0.17: the journals are superfinished last (lapped on the finisher); the CNC does the cutting before it
        int from = firstCut(crank), n = cuts(crank, from) - 1;
        assertEquals(Process.LAP, crank.route().get(from + n).process());
        assertFalse(Plans.job(crank, from, n + 1, Station.CNC, CncConfig.MILL_TURN_12).possible(), "a CNC does not lap");
        assertTrue(Plans.jobOn(crank, from + n, 1, ToolConfig.full(Head.LAPPING, false)).possible(), "the finisher's lapping head does");
        Plans.Job twelve = Plans.job(crank, from, n, Station.CNC, CncConfig.MILL_TURN_12);
        assertTrue(twelve.possible());
        assertEquals(1, twelve.setups(), "the sub-spindle takes it over: one clamping");
        assertEquals(2, Plans.job(crank, from, n, Station.CNC, CncConfig.MILL_TURN_5).setups(), "without one it is turned round");
        assertFalse(Plans.job(crank, from, n, Station.CNC, CncConfig.VMC_5).possible(), "a machining centre does not turn");
        // the manual way: each step at a manual machine that can, a batch of ten
        double manual = 0, cnc = twelve.prepareS() + 10 * twelve.pieceS();
        for (int i = from; i < from + n; i++) {
            double best = Double.POSITIVE_INFINITY;
            for (Station s : Station.CUTTING) {
                if (!s.manual) { continue; }
                Plans.Job j = Plans.job(crank, i, 1, s, null);
                if (j.possible()) { best = Math.min(best, j.prepareS() + 10 * j.pieceS()); }
            }
            manual += best;
        }
        System.out.printf("crank, ten: manual %.1f h, 12-axis %.1f h%n", manual / 3600, cnc / 3600);
        assertTrue(Double.isFinite(manual) && cnc < manual, "12 axes beat the lathe and the drill");
    }

    @Test void moreModulesNeverMeanMoreClampings() {
        List<List<CncConfig>> chains = List.of(List.of(CncConfig.VMC_3, CncConfig.VMC_4, CncConfig.VMC_5),
                List.of(CncConfig.MILL_TURN_5, CncConfig.MILL_TURN_9, CncConfig.MILL_TURN_12));
        int checked = 0;
        for (PartDesign d : ManufactureTest.everything()) {
            for (Workpiece w : Plans.of(d).workpieces()) {
                int from = firstCut(w);
                if (from < 0) { continue; }
                int n = cuts(w, from);
                for (List<CncConfig> chain : chains) {
                    for (int i = 1; i < chain.size(); i++) {
                        Plans.Job a = Plans.job(w, from, n, Station.CNC, chain.get(i - 1)), b = Plans.job(w, from, n, Station.CNC, chain.get(i));
                        if (!a.possible()) { continue; }
                        assertTrue(b.possible(), d.name() + " " + w.key() + " on " + chain.get(i).id());
                        assertTrue(b.setups() <= a.setups(), d.name() + " " + w.key() + ": " + a.setups() + " -> " + b.setups());
                        assertTrue(b.pieceS() <= a.pieceS() + 1e-9, d.name() + " " + w.key() + " time");
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 50, "checked " + checked);
    }

    @Test void noMachineDrawsMoreThanItsRating() {
        int jobs = 0;
        for (PartDesign d : ManufactureTest.everything()) {
            for (Workpiece w : Plans.of(d).workpieces()) {
                for (int i = 0; i < w.route().size(); i++) {
                    Operation op = w.route().get(i);
                    for (Station s : Station.values()) {
                        if (s == Station.CNC) {
                            for (CncConfig c : CncConfig.PRESETS) {
                                Plans.Job j = Plans.job(w, i, 1, s, c);
                                if (!j.possible()) { continue; }
                                assertTrue(j.pieceW() <= c.ratedW() * (1 + 1e-9), d.name() + " " + w.key() + " " + op.process() + " on " + c.id() + ": " + j.pieceW());
                                assertTrue(j.pieceJ() > 0);
                                jobs++;
                            }
                        } else {
                            boolean doable = op.process().cutting() ? s.can(op) : s.does(op.process());
                            if (!doable) { continue; }
                            Plans.Job j = Plans.job(w, i, 1, s, null);
                            assertTrue(j.pieceW() <= s.ratedW() * (1 + 1e-9), d.name() + " " + w.key() + " " + op.process() + " at " + s + ": " + j.pieceW() + " > " + s.ratedW());
                            assertTrue(j.pieceJ() >= 0 && Double.isFinite(j.pieceJ()));
                            jobs++;
                        }
                    }
                }
            }
        }
        assertTrue(jobs > 500, "jobs " + jobs);
        // casting melts: an iron block's melt, about 0.4 kWh a kilogram at 65 % (coreless induction furnaces: 500-650 kWh a tonne)
        Workpiece block = piece(Plans.of(SampleEngines.inlineFour()), "block");
        Plans.Job cast = Plans.job(block, 0, 1, Station.FOUNDRY, null);
        double kWhPerTonne = (cast.pieceJ() - cast.pieceS() * Station.FOUNDRY.baseW()) / 3.6e6 / block.stockKg() * 1000;
        System.out.printf("melting %s: %.0f kWh a tonne%n", block.alloy(), kWhPerTonne);
        assertTrue(kWhPerTonne > 350 && kWhPerTonne < 900, "melt " + kWhPerTonne);
    }
}
