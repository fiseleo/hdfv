package dev.mcmod.hdfv.manufacture;

import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.part.component.ComponentDesign;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The standard components (0.23): one workpiece each of the component's mass, a route that needs exactly the machines
 * of its grade (A the foundry and press, B the lathe, drill press and planer, C the mill, boring mill, grinders and
 * gear cutters, D the CNC), and the record of their routes (routes-0.23-components.json, written the first time like
 * routes-0.16.json: half-made components in old worlds were made by these routes).
 */
class ComponentRouteTest {
    static { HdfvParts.register(); }

    private static final Path FILE = Path.of(System.getProperty("hdfv.root", "..")).toAbsolutePath().normalize()
            .resolve("core/src/test/resources/routes-0.23-components.json");

    /** The grade of the machines that do a step (A..D), by the lowest grade station that can. */
    static final Map<Station, Character> GRADE = new EnumMap<>(Station.class);
    static {
        for (Station s : List.of(Station.FOUNDRY, Station.FORGING_PRESS, Station.ASSEMBLY_BENCH)) { GRADE.put(s, 'A'); }
        for (Station s : List.of(Station.LATHE, Station.DRILL_PRESS, Station.PLANER, Station.WELDING_STATION)) { GRADE.put(s, 'B'); }
        for (Station s : List.of(Station.MILLING_MACHINE, Station.BORING_MILL, Station.COMPOSITE_OVEN, Station.VULCANIZER, Station.GRINDER,
                Station.FINISHER, Station.GEAR_CUTTER, Station.RAM_MACHINE, Station.SAWING_MACHINE, Station.THREADER, Station.RIVETER)) { GRADE.put(s, 'C'); }
        for (Station s : List.of(Station.CNC, Station.ELECTRO_MACHINE, Station.CUTTING_TABLE)) { GRADE.put(s, 'D'); }
    }

    /** Whether a station (a manual tool with every accessory, the CNC at its best preset) can do a step of a workpiece. */
    static boolean can(Station s, Operation op, Workpiece w) {
        if (ToolConfig.STATIONS.contains(s)) {
            ToolConfig t = ToolConfig.full(s);
            return op.process().cutting() ? t.fits(w) && t.can(op, w) : s.does(op.process());
        }
        if (s == Station.CNC) { return CncConfig.PRESETS.stream().anyMatch(c -> c.can(op)); }
        return s.does(op.process()) && (!op.process().cutting() || s.can(op, w));
    }

    /** The lowest grade of machine that does a step ('Z' when none does). */
    static char stepGrade(Operation op, Workpiece w) {
        char g = 'Z';
        for (Map.Entry<Station, Character> e : GRADE.entrySet()) {
            if (e.getValue() < g && can(e.getKey(), op, w)) { g = e.getValue(); }
        }
        return g;
    }

    @Test void eachIsOneWorkpieceOfItsMassMadeByTheMachinesOfItsGrade() {
        for (Component c : Component.values()) {
            PartPlan plan = Plans.of(ComponentDesign.of(c));
            assertEquals(1, plan.workpieces().size(), c + ": one workpiece");
            Workpiece w = plan.workpieces().get(0);
            assertEquals(c.id(), w.key(), c + ": its key");
            assertEquals(1, w.count(), c + ": one");
            assertEquals(c.kg, w.finishedKg(), 1e-9, c + ": its mass");
            char grade = 'A';
            for (Operation op : w.route()) {
                char g = stepGrade(op, w);
                assertTrue(g != 'Z', c + ": nothing does " + op.process() + " IT" + op.grade());
                grade = (char) Math.max(grade, g);
            }
            assertEquals(c.grade, grade, c + ": its route needs grade " + grade + " machines");
        }
    }

    @Test void theDrawingsAreSamplesOfTheirOwnKind() {
        for (Component c : Component.values()) {
            ComponentDesign d = ComponentDesign.of(c);
            assertEquals(Designs.componentKind(c), Designs.kindOf(d).id());
            assertEquals(Designs.Category.COMPONENT, Designs.kindOf(d).category());
            assertTrue(Designs.SAMPLES.stream().anyMatch(s -> s.id().equals(c.id()) && s.kind().equals(Designs.componentKind(c))), c + ": a sample");
            assertEquals(d, Designs.read(Designs.write(d)), c + ": its JSON reads back");
            assertEquals(c.kg, d.compile().mass().massKg(), 1e-9);
        }
    }

    private static Map<String, Object> record() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Component c : Component.values()) {
            Workpiece w = Plans.of(ComponentDesign.of(c)).workpieces().get(0);
            List<Object> steps = new ArrayList<>();
            for (Operation op : w.route()) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("p", op.process().name());
                o.put("feature", op.feature().name());
                o.put("removed", op.removedM3());
                o.put("finished", op.finishedM2());
                o.put("holes", op.holes());
                o.put("size", op.sizeM());
                o.put("grade", op.grade());
                o.put("faces", op.faces());
                o.put("fixed", op.fixedS());
                steps.add(o);
            }
            out.put(c.id(), Map.of("stock", w.stockKg(), "steps", steps));
        }
        return out;
    }

    @Test @SuppressWarnings("unchecked")
    void theRoutesAreTheRecordedOnes() throws IOException {
        Map<String, Object> now = record();
        if (!Files.exists(FILE)) {
            Files.writeString(FILE, Json.write(now) + "\n", StandardCharsets.UTF_8);
            return;
        }
        Map<String, Object> was = (Map<String, Object>) Json.parse(Files.readString(FILE, StandardCharsets.UTF_8));
        for (var e : was.entrySet()) {
            assertTrue(now.containsKey(e.getKey()), e.getKey() + " is still a component");
            Map<String, Object> a = (Map<String, Object>) e.getValue(), b = (Map<String, Object>) now.get(e.getKey());
            assertEquals((Double) a.get("stock"), ((Number) b.get("stock")).doubleValue(), 1e-9, e.getKey() + " stock");
            List<Object> sa = (List<Object>) a.get("steps"), sb = (List<Object>) b.get("steps");
            assertEquals(sa.size(), sb.size(), e.getKey() + ": its steps");
            for (int i = 0; i < sa.size(); i++) {
                Map<String, Object> x = (Map<String, Object>) sa.get(i), y = (Map<String, Object>) sb.get(i);
                String at = e.getKey() + " step " + i;
                for (String k : x.keySet()) {
                    Object u = x.get(k), v = y.get(k);
                    if (u instanceof Double du) { assertEquals(du, ((Number) v).doubleValue(), 1e-9 * Math.max(1, Math.abs(du)), at + " " + k); }
                    else { assertEquals(u, v, at + " " + k); }
                }
            }
        }
    }

    @Test void theNamesAreThere() {
        // the workpiece names the machines show (hdfv.workpiece.<key>) and the kinds' names are checked with the rest of
        // the translations (DesignEditorTest, MachineGameTests); here only that the ids are plain
        for (Component c : Component.values()) { assertEquals(c.id(), c.id().toLowerCase(Locale.ROOT)); }
    }
}
