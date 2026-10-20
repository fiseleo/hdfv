package dev.mcmod.hdfv.manufacture;

import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** What machines use up (0.17): tool life, wheels, gas, rivets, in items. */
class WearTest {
    static { HdfvParts.register(); }

    private static Workpiece piece(Alloy a, Workpiece.Envelope e) { return new Workpiece("p", 1, a, Form.BAR, 1, 1.5, List.of(), e); }

    @Test void aCermetEdgeLastsAboutAnHourOnACncAndHighSpeedSteelMuchLess() {
        double cermet = Wear.edgeLifeS(Alloy.STEEL, Wear.Tool.CERMET, 1.0), hss = Wear.edgeLifeS(Alloy.STEEL, Wear.Tool.HSS, 1.0);
        assertTrue(cermet > 3000 && cermet < 4500, "cermet on steel at the CNC's speed: " + cermet);
        assertTrue(hss < cermet / 10, "high-speed steel at the same speed: " + hss);
        // at a manual machine's speed both last for hours (the manual machines are easy on tools)
        assertTrue(Wear.edgeLifeS(Alloy.STEEL, Wear.Tool.CERMET, 0.4) > 20 * 3600);
        // a hard alloy wears an edge sooner at its own (slower) speed? Its speed scales with it: the life is the same
        assertEquals(cermet, Wear.edgeLifeS(Alloy.TITANIUM, Wear.Tool.CERMET, 1.0), 1e-6 * cermet);
    }

    @Test void stepsUseUpWhatTheirMachineUses() {
        Workpiece shaft = piece(Alloy.STEEL, Workpiece.Envelope.turned(0.3, 0.05));
        Operation turn = Operation.cut(Process.TURN, 1e-5, 0.05, 0.05, 7, false).on(Operation.AROUND);
        double hour = Wear.edgeLifeS(Alloy.STEEL, Wear.Tool.CERMET, 1.0);
        Map<Consumable, Double> cnc = Wear.of(turn, shaft, Station.CNC, null, 1.0, hour, Wear.Tool.CERMET);
        assertEquals(1.0 / Consumable.EDGES, cnc.get(Consumable.CUTTING_TOOLS), 1e-9, "an edge's life of cutting: one edge of ten");
        assertEquals(Consumable.CUTTING_TOOLS, Wear.consumable(turn, Station.LATHE, null));
        Operation grind = Operation.cut(Process.GRIND, 1e-6, 0.01, 0.05, 6, false).on(Operation.AROUND);
        assertEquals(Consumable.GRINDING_WHEEL, Wear.consumable(grind, Station.LATHE, null));
        assertEquals(Consumable.HONING_STONES, Wear.consumable(grind, Station.BORING_MILL, null), "0.7's boring mill hones");
        double wheel = Wear.of(grind, shaft, Station.GRINDER, Head.CYLINDRICAL_GRINDING, 0.8, 30, Wear.Tool.CERMET).get(Consumable.GRINDING_WHEEL);
        assertEquals((1e-6 / 60 + Wear.DRESS_M3) / Wear.WHEEL_M3, wheel, 1e-12);
        assertTrue(wheel > 1e-4 && wheel < 1e-2, "a wheel grinds hundreds to thousands of pieces: " + wheel);
        // a minute of laser cutting with nitrogen: about a thirtieth of a 50 litre cylinder
        Operation profile = new Operation(Process.PROFILE, 0, 0.018, 1, 0, 0, 1, 11, 3, Operation.TOP, 0);
        double gas = Wear.of(profile, shaft, Station.CUTTING_TABLE, Head.LASER, 0.8, 60, Wear.Tool.CERMET).get(Consumable.ASSIST_GAS);
        assertEquals(0.0286, gas, 0.001);
        // rivets by the box of a hundred; oxy-fuel at the welding station uses no consumable item
        Operation rivets = new Operation(Process.RIVET, 0, 0, 50, 0.005, 0, 0.005, 12, 0, 0, 1000);
        assertEquals(0.5, Wear.of(rivets, shaft, Station.RIVETER, Head.SQUEEZE_RIVETER, 0.8, 150, Wear.Tool.CERMET).get(Consumable.RIVETS), 1e-12);
        assertNull(Wear.consumable(profile, Station.WELDING_STATION, null));
        assertTrue(Wear.of(profile, shaft, Station.WELDING_STATION, null, 1, 60, Wear.Tool.CERMET).isEmpty());
    }

    @Test void everyHeadsConsumableHasItsItems() {
        for (Head h : Head.values()) {
            if (h.uses == null) { continue; }
            assertTrue(java.util.Arrays.stream(Consumable.Supply.values()).anyMatch(s -> s.supplies == h.uses), h + " can be supplied");
        }
        for (Consumable.Supply s : Consumable.Supply.values()) { assertEquals(s, Consumable.Supply.ofId(s.id)); }
    }
}
