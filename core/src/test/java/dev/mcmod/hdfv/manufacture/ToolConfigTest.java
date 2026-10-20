package dev.mcmod.hdfv.manufacture;

import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Manual machine tools as built (0.9): size limits the work, accessories change what they do and how long it takes. */
class ToolConfigTest {
    static { HdfvParts.register(); }

    private static ToolConfig bare(Station s, int size) { return new ToolConfig(s, size, Set.of()); }
    private static ToolConfig with(Station s, int size, ToolConfig.Accessory... a) { return new ToolConfig(s, size, EnumSet.copyOf(List.of(a))); }

    /** A steel shaft of a length and diameter, turned and ground. */
    static Workpiece shaft(double lengthM, double diameterM) {
        return new Workpiece("shaft", 1, Alloy.STEEL, Form.BAR, 1, 1.3, List.of(Operation.cut(Process.TURN, 1e-5, 0.01, diameterM, 8, false).on(Operation.AROUND),
                Operation.cut(Process.GRIND, 1e-6, 0.01, diameterM, 6, false).on(Operation.AROUND)), Workpiece.Envelope.turned(lengthM, diameterM));
    }

    @Test void accessoriesDecideWhatALatheCanDo() {
        Workpiece stub = shaft(0.1, 0.05), longShaft = shaft(0.5, 0.05), slender = shaft(0.9, 0.05);
        Operation turn = stub.route().get(0), grind = stub.route().get(1);
        assertTrue(bare(Station.LATHE, 1).can(turn, stub));
        assertEquals(ToolConfig.Accessory.TOOLPOST_GRINDER, bare(Station.LATHE, 1).lacks(grind, stub), "grinding takes the tool-post grinder");
        assertTrue(with(Station.LATHE, 1, ToolConfig.Accessory.TOOLPOST_GRINDER).can(grind, stub));
        // ten diameters long: between centres; eighteen: a steady rest as well
        assertEquals(ToolConfig.Accessory.TAILSTOCK, bare(Station.LATHE, 1).lacks(turn, longShaft));
        assertNull(with(Station.LATHE, 1, ToolConfig.Accessory.TAILSTOCK).lacks(turn, longShaft));
        assertEquals(ToolConfig.Accessory.STEADY_REST, with(Station.LATHE, 1, ToolConfig.Accessory.TAILSTOCK).lacks(turn, slender));
        // a digital readout holds one grade finer
        Operation fine = Operation.cut(Process.TURN, 1e-5, 0.01, 0.05, 6, false);
        assertFalse(bare(Station.LATHE, 1).can(fine, stub));
        assertTrue(with(Station.LATHE, 1, ToolConfig.Accessory.DIGITAL_READOUT).can(fine, stub));
        // the mill cuts round a part only on a dividing head; the boring mill takes a big bore only with a facing head
        Workpiece block = new Workpiece("gear", 1, Alloy.STEEL, Form.FORGING, 1, 1.5, List.of(), Workpiece.Envelope.box(0.2, 0.2, 0.05));
        Operation teeth = Operation.cut(Process.MILL, 1e-5, 0.01, 0.2, 9, false).on(Operation.AROUND);
        assertEquals(ToolConfig.Accessory.DIVIDING_HEAD, bare(Station.MILLING_MACHINE, 1).lacks(teeth, block));
        assertTrue(with(Station.MILLING_MACHINE, 1, ToolConfig.Accessory.DIVIDING_HEAD).can(teeth, block));
        Operation bigBore = Operation.cut(Process.BORE, 1e-5, 0.1, 0.4, 8, false);
        assertEquals(ToolConfig.Accessory.FACING_HEAD, bare(Station.BORING_MILL, 1).lacks(bigBore, block));
        // complex shapes stay the CNC's whatever is fitted
        assertFalse(ToolConfig.full(Station.MILLING_MACHINE).can(Operation.cut(Process.MILL, 1e-5, 0.01, 0.05, 9, true), block));
        assertThrows(IllegalArgumentException.class, () -> with(Station.LATHE, 1, ToolConfig.Accessory.DIVIDING_HEAD));
        assertThrows(IllegalArgumentException.class, () -> bare(Station.LATHE, 7));
    }

    @Test void theWorkMustFit() {
        assertTrue(bare(Station.LATHE, 1).fits(shaft(0.9, 0.05)));
        assertFalse(bare(Station.LATHE, 1).fits(shaft(1.3, 0.05)), "1.3 m between centres on a 1 m bed");
        assertTrue(bare(Station.LATHE, 2).fits(shaft(1.3, 0.05)));
        assertFalse(bare(Station.LATHE, 6).fits(shaft(0.3, 0.8)), "an 0.8 m disc does not swing in 0.5 m");
        Workpiece disc = new Workpiece("disc", 1, Alloy.STEEL, Form.FORGING, 1, 1.5, List.of(), Workpiece.Envelope.turned(0.1, 1.2));
        assertTrue(bare(Station.BORING_MILL, 1).fits(disc), "on the boring mill's 1.5 m table");
        Workpiece bedPlate = new Workpiece("plate", 1, Alloy.GREY_IRON, Form.CASTING, 1, 1.5, List.of(), Workpiece.Envelope.box(3.2, 0.8, 0.3));
        assertFalse(bare(Station.PLANER, 1).fits(bedPlate));
        assertTrue(bare(Station.PLANER, 3).fits(bedPlate), "a 4 m planer");
        assertFalse(Plans.jobOn(bedPlate, 0, 0, bare(Station.PLANER, 1)).possible(), "no job when it does not fit");
    }

    @Test void accessoriesSaveSetupsAndTime() {
        int box = Operation.TOP | Operation.BOTTOM | Operation.FRONT | Operation.LEFT;
        assertEquals(4, bare(Station.MILLING_MACHINE, 1).setups(box));
        assertEquals(3, with(Station.MILLING_MACHINE, 1, ToolConfig.Accessory.MILL_ROTARY_TABLE).setups(box), "the sides in one clamping");
        int planed = Operation.TOP | Operation.LEFT | Operation.RIGHT;
        assertEquals(3, bare(Station.PLANER, 1).setups(planed));
        assertEquals(1, with(Station.PLANER, 1, ToolConfig.Accessory.SIDE_HEAD).setups(planed), "the side head works in the top's setup");
        assertEquals(2, bare(Station.LATHE, 1).setups(Operation.AROUND | Operation.FRONT | Operation.BACK), "turned round, then the back end");
        // a 4-cylinder block's planing and a crank's turning, plain and with accessories
        var plan = Plans.of(SampleEngines.inlineFour());
        Workpiece block = plan.workpieces().stream().filter(w -> w.key().equals("block")).findFirst().orElseThrow();
        Workpiece crank = plan.workpieces().stream().filter(w -> w.key().equals("crank")).findFirst().orElseThrow();
        Plans.Job plain = Plans.jobOn(block, 1, 1, bare(Station.PLANER, 1)), twoHeads = Plans.jobOn(block, 1, 1, with(Station.PLANER, 1, ToolConfig.Accessory.SECOND_HEAD));
        assertTrue(plain.possible() && twoHeads.pieceS() < plain.pieceS(), "a second tool head planes faster");
        ToolConfig lathe = with(Station.LATHE, 1, ToolConfig.Accessory.TAILSTOCK), readout = with(Station.LATHE, 1, ToolConfig.Accessory.TAILSTOCK,
                ToolConfig.Accessory.DIGITAL_READOUT, ToolConfig.Accessory.QUICK_CHANGE_POST);
        int turn = 1;
        assertEquals(Process.TURN, crank.route().get(turn).process());
        Plans.Job a = Plans.jobOn(crank, turn, 1, lathe), b = Plans.jobOn(crank, turn, 1, readout);
        assertTrue(a.possible() && b.pieceS() < a.pieceS() && b.prepareS() < a.prepareS(), "a readout and a quick-change post save time");
        // a multi-spindle head drills the block's holes three at a time
        // 0.17: the block is tapped and deburred after its drilling
        int drill = 0;
        while (block.route().get(drill).process() != Process.DRILL) { drill++; }
        assertTrue(Plans.jobOn(block, drill, 1, with(Station.DRILL_PRESS, 1, ToolConfig.Accessory.MULTI_SPINDLE)).pieceS()
                < Plans.jobOn(block, drill, 1, bare(Station.DRILL_PRESS, 1)).pieceS());
    }

    @Test void everySampleIsSizedAndTheFullMachinesMakeWhatTheyMadeBefore() {
        for (var sample : Designs.SAMPLES) {
            for (Workpiece w : Plans.of(sample.design().get()).workpieces()) {
                Workpiece.Envelope e = w.envelope();
                assertTrue(e.lengthM() > 0 && e.widthM() > 0 && e.heightM() > 0, sample + " " + w.key());
            }
        }
        // every step of the 2.0 l four that a manual machine did in 0.7, the full manual machines still do
        for (Workpiece w : Plans.of(SampleEngines.inlineFour()).workpieces()) {
            for (Operation op : w.route()) {
                if (!op.process().cutting()) { continue; }
                boolean before = false;
                for (Station s : ToolConfig.STATIONS) { before |= !s.family && s.can(op); }   // 0.9's machines (0.17's families are HeadTest's)
                if (!before) { continue; }
                boolean any = false;
                for (Station s : ToolConfig.STATIONS) { any |= !s.family && ToolConfig.full(s).fits(w) && ToolConfig.full(s).can(op, w); }
                assertTrue(any, w.key() + " " + op.process() + " IT" + op.grade());
            }
        }
    }
}
