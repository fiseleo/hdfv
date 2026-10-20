package dev.mcmod.hdfv.structure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 0.20: the control tower's pattern, and the quick count of repeated layers equal to trying every count. */
class TowerPatternTest {
    private static final Pattern T = Structures.TOWER_PATTERN;

    @Test void aTowerFormsAtEveryHeightAndFacing() {
        for (int facing = 0; facing < 4; facing++) {
            for (int shaft : new int[]{6, 7, 20, 56}) {
                PatternTest.Blocks w = new PatternTest.Blocks();
                w.build(T, 100, 64, -40, facing, shaft);
                Pattern.Match m = T.match(w, 100, 64, -40, facing);
                assertNotNull(m, "facing " + facing + ", " + shaft + " shaft courses");
                assertEquals(shaft, m.repeats());
                assertTrue(m.has(Structures.TOWER_RADAR) && m.has(Structures.TOWER_MAST) && m.has(Structures.TOWER_CONSOLE));
                assertEquals(shaft * 16, m.count(Structures.TOWER_SHAFT));
                Structures.TowerHeights h = Structures.towerHeights(m);
                assertEquals(2 + shaft + 1 + 1.6, h.eyeM(), 1e-12);
                assertEquals(2 + shaft + 4 + 1, h.antennaM(), 1e-12);
                assertEquals(T.height(shaft), 2 + shaft + 5, "the top layer");
            }
        }
        PatternTest.Blocks w = new PatternTest.Blocks();
        w.build(T, 0, 0, 0, 2, 5);
        assertNull(T.match(w, 0, 0, 0, 2), "five courses are too few");
    }

    @Test void theCabMustBeEmptyButTheShaftAndBaseNot() {
        PatternTest.Blocks w = new PatternTest.Blocks();
        w.build(T, 0, 0, 0, 0, 8);
        // a ladder inside the shaft and stairs in the base are fine
        Pattern.Cell controller = T.cells(0, 0, 0, 0, 8).stream().filter(c -> c.symbol() == 'C').findFirst().orElseThrow();
        w.put(0, 3, -4, "minecraft:ladder");
        w.put(0, 1, -3, "minecraft:oak_stairs");
        assertNotNull(T.match(w, 0, 0, 0, 0));
        // a block in the cab breaks it
        Pattern.Cell inside = T.cells(0, 0, 0, 0, 8).stream().filter(c -> c.symbol() == '.').findFirst().orElseThrow();
        w.put(inside.x(), inside.y(), inside.z(), "minecraft:stone");
        assertNull(T.match(w, 0, 0, 0, 0));
        w.put(inside.x(), inside.y(), inside.z(), null);
        // the optional modules may go
        for (Pattern.Cell c : T.cells(0, 0, 0, 0, 8)) {
            if (c.symbol() == 'A' || c.symbol() == 'M' || c.symbol() == 'k') { w.put(c.x(), c.y(), c.z(), null); }
        }
        Pattern.Match bare = T.match(w, 0, 0, 0, 0);
        assertNotNull(bare);
        assertFalse(bare.has(Structures.TOWER_RADAR));
        assertEquals(Structures.towerHeights(bare).roofM(), Structures.towerHeights(bare).antennaM(), 1e-12, "no antenna above the roof");
        assertEquals(Structures.TOWER, w.kind(controller.x(), controller.y(), controller.z()));
    }

    /** The quick count must give exactly what trying every count from the most down gives, on whole and broken towers. */
    @Test void theQuickMatchEqualsTheSlowOne() {
        Random random = new Random(20);
        List<Pattern.Cell> all = T.cells(0, 0, 0, 1, 30);
        for (int trial = 0; trial < 300; trial++) {
            PatternTest.Blocks w = new PatternTest.Blocks();
            int shaft = 1 + random.nextInt(40);
            w.build(T, 0, 0, 0, 1, shaft);
            // break a few random cells of a 30-course tower's footprint: remove, replace or fill an empty one
            int breaks = random.nextInt(4);
            for (int b = 0; b < breaks; b++) {
                Pattern.Cell c = all.get(random.nextInt(all.size()));
                switch (random.nextInt(3)) {
                    case 0 -> w.put(c.x(), c.y(), c.z(), null);
                    case 1 -> w.put(c.x(), c.y(), c.z(), "minecraft:stone");
                    default -> w.put(c.x(), c.y(), c.z(), Structures.TOWER_SHAFT);
                }
            }
            Pattern.Match quick = T.match(w, 0, 0, 0, 1), slow = T.matchSlowly(w, 0, 0, 0, 1);
            assertEquals(slow == null, quick == null, "trial " + trial);
            if (slow != null) {
                assertEquals(slow.repeats(), quick.repeats());
                assertEquals(slow.cells(), quick.cells());
                assertEquals(slow.counts(), quick.counts());
            }
        }
        // and on the other repeated patterns
        for (Pattern p : List.of(Structures.STORAGE_TANK, Structures.DRILLING_RIG)) {
            for (int r = p.minRepeats(); r <= p.maxRepeats(); r++) {
                PatternTest.Blocks w = new PatternTest.Blocks();
                w.build(p, 5, 5, 5, 3, r);
                assertEquals(p.matchSlowly(w, 5, 5, 5, 3).repeats(), p.match(w, 5, 5, 5, 3).repeats());
            }
        }
    }
}
