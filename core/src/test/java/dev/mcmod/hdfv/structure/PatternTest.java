package dev.mcmod.hdfv.structure;

import dev.mcmod.hdfv.manufacture.CncConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Multiblock patterns (0.8): turned four ways, slots with choices, optional cells, empty envelopes, repeated layers; the CNC's modules. */
class PatternTest {
    /** A world of named blocks. */
    static final class Blocks implements Pattern.World {
        final Map<List<Integer>, String> at = new HashMap<>();
        @Override public String kind(int x, int y, int z) { return at.get(List.of(x, y, z)); }
        void put(int x, int y, int z, String kind) { if (kind == null) { at.remove(List.of(x, y, z)); } else { at.put(List.of(x, y, z), kind); } }
        void build(Pattern p, int x, int y, int z, int facing, int repeats) { for (Pattern.Cell c : p.cells(x, y, z, facing, repeats)) { put(c.x(), c.y(), c.z(), c.kind()); } }
        Pattern.Cell find(Pattern p, int facing, char symbol) {
            return p.cells(0, 0, 0, facing, 1).stream().filter(c -> c.symbol() == symbol).findFirst().orElseThrow();
        }
    }

    @Test void theCncFormsFacingEachWay() {
        for (int facing = 0; facing < 4; facing++) {
            Blocks w = new Blocks();
            w.build(Structures.CNC, 10, 64, -5, facing, 1);
            Pattern.Match m = Structures.CNC.match(w, 10, 64, -5, facing);
            assertNotNull(m, "facing " + facing);
            assertEquals(4 * 3 * 3 - 2, m.cells().size(), "every cell but the working envelope");
            assertEquals(CncConfig.VMC_3, Structures.cncConfig(m));
            assertNotNull(Structures.CNC.matchAnyFacing(w, 10, 64, -5, (facing + 1) % 4), "found whichever way it faces");
            assertNull(Structures.CNC.match(w, 10, 64, -5, (facing + 1) % 4), "not when looked for facing another way");
        }
        // facing south, one stands south of it looking north: east is on the right, so the machine runs west and north of its cabinet
        Blocks w = new Blocks();
        w.build(Structures.CNC, 0, 0, 0, 0, 1);
        assertEquals(Structures.GUARD, w.kind(-3, 0, 0));
        assertEquals(Structures.BED, w.kind(0, 0, -2));
        assertEquals(Structures.FRAME, w.kind(-3, 2, -2));
        assertNull(w.kind(1, 0, 0));
        // facing north it runs east and south
        Blocks n = new Blocks();
        n.build(Structures.CNC, 0, 0, 0, 2, 1);
        assertEquals(Structures.GUARD, n.kind(3, 0, 0));
        assertEquals(Structures.BED, n.kind(0, 0, 2));
    }

    @Test void aMissingWrongOrInTheWayBlockBreaksIt() {
        Blocks w = new Blocks();
        w.build(Structures.CNC, 0, 0, 0, 1, 1);
        assertNotNull(Structures.CNC.match(w, 0, 0, 0, 1));
        Pattern.Cell frame = w.find(Structures.CNC, 1, 'F');
        w.put(frame.x(), frame.y(), frame.z(), null);
        assertNull(Structures.CNC.match(w, 0, 0, 0, 1), "a frame missing");
        w.put(frame.x(), frame.y(), frame.z(), "minecraft:stone");
        assertNull(Structures.CNC.match(w, 0, 0, 0, 1), "stone instead");
        w.put(frame.x(), frame.y(), frame.z(), Structures.FRAME);
        Pattern.Cell envelope = w.find(Structures.CNC, 1, '.');
        w.put(envelope.x(), envelope.y(), envelope.z(), Structures.BED);
        assertNull(Structures.CNC.match(w, 0, 0, 0, 1), "something in the working zone");
        w.put(envelope.x(), envelope.y(), envelope.z(), null);
        assertNotNull(Structures.CNC.match(w, 0, 0, 0, 1), "and back");
    }

    @Test void theModulesMakeItsAxes() {
        Blocks w = new Blocks();
        w.build(Structures.CNC, 0, 0, 0, 3, 1);
        java.util.function.BiConsumer<Character, String> set = (symbol, kind) -> { Pattern.Cell c = w.find(Structures.CNC, 3, symbol); w.put(c.x(), c.y(), c.z(), kind); };
        set.accept('W', Structures.ROTARY_TABLE);
        assertEquals(CncConfig.VMC_4, Structures.cncConfig(Structures.CNC.match(w, 0, 0, 0, 3)));
        set.accept('W', Structures.TRUNNION);
        assertEquals(CncConfig.VMC_5, Structures.cncConfig(Structures.CNC.match(w, 0, 0, 0, 3)));
        set.accept('H', Structures.B_AXIS_HEAD);
        assertNull(Structures.cncConfig(Structures.CNC.match(w, 0, 0, 0, 3)), "a B head on a machining centre");
        set.accept('W', Structures.TURNING_SPINDLE);
        assertEquals(CncConfig.MILL_TURN_5, Structures.cncConfig(Structures.CNC.match(w, 0, 0, 0, 3)));
        set.accept('S', Structures.SUB_SPINDLE);
        set.accept('X', Structures.TURRET);
        assertEquals(CncConfig.MILL_TURN_9, Structures.cncConfig(Structures.CNC.match(w, 0, 0, 0, 3)));
        set.accept('R', Structures.Y_TURRET);
        Pattern.Match twelve = Structures.CNC.match(w, 0, 0, 0, 3);
        assertEquals(CncConfig.MILL_TURN_12, Structures.cncConfig(twelve));
        assertEquals(12, Structures.cncConfig(twelve).axes());
        set.accept('W', Structures.BED);
        assertNull(Structures.cncConfig(Structures.CNC.match(w, 0, 0, 0, 3)), "turrets without a turning spindle");
        set.accept('W', "minecraft:dirt");
        assertNull(Structures.CNC.match(w, 0, 0, 0, 3), "not a work holding");
    }

    @Test void theOilFieldsMachinesForm() {
        for (int facing = 0; facing < 4; facing++) {
            Blocks w = new Blocks();
            w.build(Structures.DRILLING_RIG, 0, 0, 0, facing, 4);
            Pattern.Match rig = Structures.DRILLING_RIG.match(w, 0, 0, 0, facing);
            assertNotNull(rig, "rig facing " + facing);
            assertEquals(4, rig.repeats());
            assertEquals(1, rig.count(Structures.RIG_ROTARY));
            Blocks j = new Blocks();
            j.build(Structures.PUMP_JACK_PATTERN, 0, 0, 0, facing, 1);
            Pattern.Match jack = Structures.PUMP_JACK_PATTERN.match(j, 0, 0, 0, facing);
            assertNotNull(jack, "jack facing " + facing);
            assertEquals(1, jack.count(Structures.WELLHEAD));
        }
    }

    @Test void theRefineryForms() {
        Blocks w = new Blocks();
        w.build(Structures.DISTILLATION_COLUMN, 0, 0, 0, 1, 1);
        Pattern.Match col = Structures.DISTILLATION_COLUMN.match(w, 0, 0, 0, 1);
        assertNotNull(col);
        assertEquals(6, col.count(Structures.UNIT_OUTLET));
        assertTrue(col.has(Structures.VACUUM_COLUMN), "placed whole it has its vacuum column");
        Pattern.Cell v = w.find(Structures.DISTILLATION_COLUMN, 1, 'V');
        w.put(v.x(), v.y(), v.z(), null);
        assertFalse(Structures.DISTILLATION_COLUMN.match(w, 0, 0, 0, 1).has(Structures.VACUUM_COLUMN), "and without it");
        Blocks u = new Blocks();
        u.build(Structures.PROCESS, 0, 0, 0, 0, 1);
        for (Pattern.Cell c : Structures.PROCESS.cells(0, 0, 0, 0, 1)) { if (c.symbol() == 'R') { u.put(c.x(), c.y(), c.z(), Structures.FCC); } }
        Pattern.Match unit = Structures.PROCESS.match(u, 0, 0, 0, 0);
        assertEquals(Structures.FCC, Structures.reactor(unit));
        Pattern.Cell r = u.find(Structures.PROCESS, 0, 'R');
        u.put(r.x(), r.y(), r.z(), Structures.CRACKER);
        assertNull(Structures.reactor(Structures.PROCESS.match(u, 0, 0, 0, 0)), "mixed reactors");
    }

    @Test void repeatedLayersAndOptionalCells() {
        Pattern column = Pattern.builder("column")
                .layer(" o ", "fCf", " f ")
                .layer(" t ", "t t", " t ")
                .layer(" h ", "h h", " h ")
                .controller('C', "base").where('f', "frame").optional('o', "outlet").where('t', "tray").where('h', "head")
                .repeat(1, 1, 2, 5)
                .build();
        Blocks w = new Blocks();
        w.build(column, 0, 0, 0, 2, 3);
        assertEquals(5, column.height(3));
        Pattern.Match m = column.match(w, 0, 0, 0, 2);
        assertNotNull(m);
        assertEquals(3, m.repeats(), "three tray rings");
        assertEquals(12, m.count("tray"));
        assertTrue(m.has("outlet"));
        Pattern.Cell outlet = w.find(column, 2, 'o');
        w.put(outlet.x(), outlet.y(), outlet.z(), null);
        Pattern.Match without = column.match(w, 0, 0, 0, 2);
        assertNotNull(without, "the outlet is optional");
        assertFalse(without.has("outlet"));
        // one ring is fewer than the least
        Blocks low = new Blocks();
        low.build(column, 0, 0, 0, 2, 2);
        for (Pattern.Cell c : column.cells(0, 0, 0, 2, 2)) { if (c.y() == 2 && "tray".equals(c.kind())) { low.put(c.x(), c.y(), c.z(), "head"); } }
        for (Pattern.Cell c : column.cells(0, 0, 0, 2, 2)) { if (c.y() == 3) { low.put(c.x(), c.y(), c.z(), null); } }
        assertNull(column.match(low, 0, 0, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> Pattern.builder("bad").layer("x").controller('C', "c").build(), "no controller in the layers");
    }
}
