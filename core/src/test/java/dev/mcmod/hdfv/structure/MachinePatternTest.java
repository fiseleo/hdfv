package dev.mcmod.hdfv.structure;

import dev.mcmod.hdfv.manufacture.Head;
import dev.mcmod.hdfv.manufacture.MachineFrame;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.ToolConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The manual machine tools as multiblocks (0.9): every size in every facing, the drive, the accessory cells, a
 * lengthened bed. 0.17: the machine families too (MachineFrame says which way each grows), each head making its machine.
 */
class MachinePatternTest {
    private static boolean across(Station s) { return MachineFrame.of(s).growth != MachineFrame.Growth.UP; }

    /** Builds a machine (optional cells holding their first accessory, a family's head cell its first head). */
    static PatternTest.Blocks build(Station s, int facing, int size) {
        PatternTest.Blocks w = new PatternTest.Blocks();
        Pattern p = Structures.machine(s);
        for (Pattern.Cell c : p.cells(5, 64, 5, facing, across(s) ? 1 : size, across(s) ? size : 1)) { w.put(c.x(), c.y(), c.z(), c.kind()); }
        return w;
    }

    @Test void everyMachineFormsInEverySizeAndFacing() {
        for (Station s : ToolConfig.STATIONS) {
            Pattern p = Structures.machine(s);
            assertNotNull(p, s + " has a pattern");
            for (int facing = 0; facing < 4; facing++) {
                for (int size = ToolConfig.minSize(s); size <= ToolConfig.maxSize(s); size++) {
                    PatternTest.Blocks w = build(s, facing, size);
                    Pattern.Match m = p.match(w, 5, 64, 5, facing);
                    assertNotNull(m, s + " facing " + facing + " size " + size);
                    ToolConfig t = Structures.toolConfig(s, m);
                    assertNotNull(t, s + " is a machine");
                    assertEquals(size, t.size(), s + " size");
                    assertEquals(s.family, t.head() != null, s + " has its head");
                    assertTrue(Structures.electric(m), "the first drive placed is the motor");
                    assertFalse(Structures.numerical(m), "the motor is no NC drive");
                    assertNotNull(p.matchAnyFacing(w, 5, 64, 5, (facing + 2) % 4), "found whichever way it faces");
                }
            }
        }
    }

    @Test void theHeadMakesTheMachineAndTheNcDriveRunsIt() {
        for (Station s : ToolConfig.STATIONS) {
            if (!s.family) { continue; }
            Pattern p = Structures.machine(s);
            PatternTest.Blocks w = build(s, 0, ToolConfig.minSize(s));
            Pattern.Cell head = p.cells(5, 64, 5, 0, 1, 1).stream().filter(c -> c.symbol() == 'h').findFirst().orElseThrow();
            for (Head h : Head.of(s)) {
                w.put(head.x(), head.y(), head.z(), h.kind);
                ToolConfig t = Structures.toolConfig(s, p.match(w, 5, 64, 5, 0));
                assertEquals(h, t.head(), s + " with " + h.kind);
            }
            w.put(head.x(), head.y(), head.z(), null);
            assertNull(p.match(w, 5, 64, 5, 0), s + " without a head is no machine");
            // the NC drive in the drive's cell: an electric, numerically controlled machine without an operator
            w.put(head.x(), head.y(), head.z(), Head.of(s).get(0).kind);
            Pattern.Cell drive = p.cells(5, 64, 5, 0, 1, 1).stream().filter(c -> c.symbol() == 'D').findFirst().orElseThrow();
            w.put(drive.x(), drive.y(), drive.z(), Structures.NC_DRIVE);
            Pattern.Match m = p.match(w, 5, 64, 5, 0);
            assertTrue(Structures.electric(m) && Structures.numerical(m));
            assertFalse(Structures.toolConfig(s, m).manual(), s + " with an NC drive needs no operator");
        }
        // the electrical machines take only an electric drive
        PatternTest.Blocks edm = build(Station.ELECTRO_MACHINE, 0, 1);
        Pattern.Cell drive = Structures.ELECTRO_MACHINE.cells(5, 64, 5, 0, 1, 1).stream().filter(c -> c.symbol() == 'D').findFirst().orElseThrow();
        edm.put(drive.x(), drive.y(), drive.z(), Structures.PULLEY_DRIVE);
        assertNull(Structures.ELECTRO_MACHINE.match(edm, 5, 64, 5, 0), "an EDM is not driven from a line shaft");
        // and a 0.9 lathe on the NC drive keeps its pattern
        PatternTest.Blocks lathe = build(Station.LATHE, 0, 2);
        Pattern.Cell ld = Structures.LATHE.cells(5, 64, 5, 0, 1, 2).stream().filter(c -> c.symbol() == 'D').findFirst().orElseThrow();
        lathe.put(ld.x(), ld.y(), ld.z(), Structures.NC_DRIVE);
        ToolConfig nc = Structures.toolConfig(Station.LATHE, Structures.LATHE.match(lathe, 5, 64, 5, 0));
        assertTrue(nc.nc() && !nc.manual() && nc.head() == null);
    }

    @Test void theDriveAndTheAccessoriesAreItsCells() {
        // a 3 m lathe: the motor under the headstock; steady rests over the bed, the tailstock at its end
        PatternTest.Blocks w = build(Station.LATHE, 0, 3);
        Pattern.Match m = Structures.LATHE.match(w, 5, 64, 5, 0);
        assertEquals(Set.of(ToolConfig.Accessory.STEADY_REST, ToolConfig.Accessory.TAILSTOCK), Structures.toolConfig(Station.LATHE, m).accessories());
        Pattern.Cell drive = Structures.LATHE.cells(5, 64, 5, 0, 1, 3).stream().filter(c -> c.symbol() == 'D').findFirst().orElseThrow();
        assertEquals(63, drive.y(), "the drive is under the headstock");
        w.put(drive.x(), drive.y(), drive.z(), Structures.PULLEY_DRIVE);
        m = Structures.LATHE.match(w, 5, 64, 5, 0);
        assertFalse(Structures.electric(m), "a pulley seat: driven from a line shaft");
        w.put(drive.x(), drive.y(), drive.z(), null);
        assertNull(Structures.LATHE.match(w, 5, 64, 5, 0), "no drive, no machine");
        w.put(drive.x(), drive.y(), drive.z(), Structures.PULLEY_DRIVE);
        // accessories are optional: take them away and it is a bare lathe; a grinder and a readout in their place
        for (Pattern.Cell c : Structures.LATHE.cells(5, 64, 5, 0, 1, 3)) {
            if (c.symbol() == 'a' || c.symbol() == 't') { w.put(c.x(), c.y(), c.z(), null); }
        }
        assertEquals(Set.of(), Structures.toolConfig(Station.LATHE, Structures.LATHE.match(w, 5, 64, 5, 0)).accessories());
        var cells = Structures.LATHE.cells(5, 64, 5, 0, 1, 3).stream().filter(c -> c.symbol() == 'a').toList();
        w.put(cells.get(0).x(), cells.get(0).y(), cells.get(0).z(), ToolConfig.Accessory.TOOLPOST_GRINDER.kind);
        w.put(cells.get(2).x(), cells.get(2).y(), cells.get(2).z(), ToolConfig.Accessory.DIGITAL_READOUT.kind);
        assertEquals(Set.of(ToolConfig.Accessory.TOOLPOST_GRINDER, ToolConfig.Accessory.DIGITAL_READOUT),
                Structures.toolConfig(Station.LATHE, Structures.LATHE.match(w, 5, 64, 5, 0)).accessories());
        // a mill's dividing head is not a lathe's: the cell refuses it
        w.put(cells.get(1).x(), cells.get(1).y(), cells.get(1).z(), ToolConfig.Accessory.DIVIDING_HEAD.kind);
        assertNull(Structures.LATHE.match(w, 5, 64, 5, 0));
    }

    @Test void aLongerBedIsALongerLathe() {
        PatternTest.Blocks w = build(Station.LATHE, 1, 2);
        assertEquals(2, Structures.LATHE.match(w, 5, 64, 5, 1).length());
        // lengthen it by building the 4 m lathe over it: the longest that stands is taken
        for (Pattern.Cell c : Structures.LATHE.cells(5, 64, 5, 1, 1, 4)) { w.put(c.x(), c.y(), c.z(), c.kind()); }
        assertEquals(4, Structures.LATHE.match(w, 5, 64, 5, 1).length());
        // a bed segment taken from the middle: the machine stops short of it (a lathe with a gap is no 4 m lathe)
        Pattern.Cell second = Structures.LATHE.cells(5, 64, 5, 1, 1, 4).stream().filter(c -> c.symbol() == 'b').skip(1).findFirst().orElseThrow();
        w.put(second.x(), second.y(), second.z(), null);
        Pattern.Match m = Structures.LATHE.match(w, 5, 64, 5, 1);
        assertTrue(m == null || m.length() < 4);
    }

    @Test void columnsGrowUp() {
        PatternTest.Blocks w = build(Station.BORING_MILL, 3, 3);
        Pattern.Match m = Structures.BORING_MILL.match(w, 5, 64, 5, 3);
        assertEquals(3, Structures.toolConfig(Station.BORING_MILL, m).size());
        // the work's room over the table must be clear
        Pattern.Cell room = Structures.BORING_MILL.cells(5, 64, 5, 3, 3, 1).stream().filter(c -> c.symbol() == '.').findFirst().orElseThrow();
        w.put(room.x(), room.y(), room.z(), "minecraft:stone");
        assertNotEquals(3, Structures.BORING_MILL.match(w, 5, 64, 5, 3) == null ? 0 : Structures.BORING_MILL.match(w, 5, 64, 5, 3).repeats());
    }
}
