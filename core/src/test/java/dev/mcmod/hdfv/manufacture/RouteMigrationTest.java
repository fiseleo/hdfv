package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.17's routes against 0.16's (RouteSnapshot): the user chose that half-made workpieces go on at the matching step, so
 * every route without its inserted steps must be 0.16's step for step, and whatever could do a step must still can.
 */
class RouteMigrationTest {
    @BeforeAll static void register() { HdfvParts.register(); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshot() throws IOException {
        return (Map<String, Object>) Json.parse(Files.readString(RouteSnapshot.FILE, StandardCharsets.UTF_8));
    }

    private static void near(double expected, double actual, String what) {
        assertEquals(expected, actual, 1e-9 * Math.max(1, Math.abs(expected)), what);
    }

    @Test @SuppressWarnings("unchecked")
    void theOldStepsAreTheSnapshotsAndWhatDidThemStillCan() throws IOException {
        Map<String, Object> snap = snapshot();
        Map<String, PartDesign> designs = RouteSnapshot.designs();
        // every design of 0.16 is still there (samples added since have no old route to keep)
        assertTrue(designs.keySet().containsAll(snap.keySet()), "the snapshot's designs: " + snap.keySet().stream().filter(k -> !designs.containsKey(k)).toList());
        int inserted = 0;
        for (var d : designs.entrySet()) {
            if (!snap.containsKey(d.getKey())) { continue; }
            Map<String, Object> pieces = (Map<String, Object>) ((Map<String, Object>) snap.get(d.getKey())).get("pieces");
            PartPlan plan = Plans.of(d.getValue());
            assertEquals(pieces.keySet(), plan.workpieces().stream().map(Workpiece::key).collect(java.util.stream.Collectors.toSet()), d.getKey() + ": no workpiece added or lost");
            for (Workpiece w : plan.workpieces()) {
                String where = d.getKey() + " " + w.key();
                List<Map<String, Object>> old = (List<Map<String, Object>>) pieces.get(w.key());
                List<Operation> legacy = Routes.legacy(w);
                assertEquals(old.size(), legacy.size(), where + ": its old steps");
                assertFalse(w.route().get(0).added(), where + ": nothing before its first step");
                int k = 0;
                for (Operation op : w.route()) {
                    if (op.added()) {
                        inserted++;
                        continue;
                    }
                    assertTrue(op.process().ordinal() <= Process.ASSEMBLE.ordinal(), where + " step " + k + ": a new process is always inserted");
                    Map<String, Object> o = old.get(k);
                    String at = where + " step " + k;
                    assertEquals(o.get("p"), op.process().name(), at);
                    near((Double) o.get("removed"), op.removedM3(), at + " removed");
                    near((Double) o.get("finished"), op.finishedM2(), at + " finished");
                    assertEquals(((Double) o.get("holes")).intValue(), op.holes(), at + " holes");
                    near((Double) o.get("holeD"), op.holeDiameterM(), at + " hole diameter");
                    near((Double) o.get("holeDepth"), op.holeDepthM(), at + " hole depth");
                    near((Double) o.get("size"), op.sizeM(), at + " size");
                    assertEquals(((Double) o.get("grade")).intValue(), op.grade(), at + " grade");
                    assertEquals(((Double) o.get("axes")).intValue(), op.axes(), at + " axes");
                    assertEquals(((Double) o.get("faces")).intValue(), op.faces(), at + " faces");
                    near((Double) o.get("fixed"), op.fixedS(), at + " time");
                    List<String> now = RouteSnapshot.doers(op, w);
                    assertTrue(now.containsAll((List<String>) o.get("doers")), at + ": " + o.get("doers") + " still do it, now " + now);
                    k++;
                }
            }
        }
        assertTrue(inserted > 300, "the routes gained their steps: " + inserted);
    }

    @Test void oldWorkpiecesGoOnAtTheMatchingStep() {
        PartPlan plan = Plans.of(SampleEngines.inlineFour());
        Workpiece crank = plan.workpieces().stream().filter(w -> w.key().equals("crank")).findFirst().orElseThrow();
        // 0.16: forge, turn, grind, drill; 0.17: forge, turn, grind (journals), drill (oil holes), lap
        assertEquals(List.of(Process.FORGE, Process.TURN, Process.GRIND, Process.DRILL, Process.LAP), crank.route().stream().map(Operation::process).toList());
        assertEquals(1, Routes.migrate(crank, 1, 4), "forged: on to turning");
        assertEquals(3, Routes.migrate(crank, 3, 4), "ground: on to its oil holes");
        assertEquals(5, Routes.migrate(crank, 4, 4), "a finished crank stays finished");
        assertEquals(3, Routes.migrate(crank, 3, 7), "a count that does not match: the steps it did, as far as they go");
        assertEquals(0, Routes.migrate(crank, 0, 4));
        // a spark plug made from bar: the sawing inserted after its forming must be done before turning
        Workpiece plug = plan.workpieces().stream().filter(w -> w.key().equals("spark_plug")).findFirst().orElseThrow();
        assertEquals(List.of(Process.FORM, Process.SAW, Process.TURN, Process.THREAD), plug.route().stream().map(Operation::process).toList());
        assertEquals(1, Routes.migrate(plug, 1, 2), "formed: it is sawn first");
        assertEquals(4, Routes.migrate(plug, 2, 2), "a finished plug stays finished");
        // the block: its bores honed, then tapped and deburred after its drilling
        Workpiece block = plan.workpieces().stream().filter(w -> w.key().equals("block")).findFirst().orElseThrow();
        assertEquals(Feature.BORE, block.route().get(3).feature());
        assertEquals(List.of(Process.CAST, Process.PLANE, Process.BORE, Process.GRIND, Process.BORE, Process.DRILL, Process.TAP, Process.DEBURR),
                block.route().stream().map(Operation::process).toList());
        assertEquals(8, Routes.migrate(block, 6, 6), "the finished block stays finished");
        assertEquals(5, Routes.migrate(block, 5, 6), "bored: on to drilling");
        assertEquals(block.route().get(5).faces(), block.route().get(6).faces(), "tapped from the drilling's faces: no more setups");
    }
}
