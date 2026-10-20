package dev.mcmod.hdfv.manufacture;

import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The machine families (0.17): what each head takes and to what grade, what it needs fitted, the NC drive, what it
 * takes in, and how long its work takes (the order of real machines).
 */
class HeadTest {
    static { HdfvParts.register(); }

    private static ToolConfig on(Head h, int size, boolean nc, ToolConfig.Accessory... a) {
        return new ToolConfig(h.station, h, size, a.length == 0 ? Set.of() : EnumSet.copyOf(List.of(a)), nc);
    }

    private static Workpiece piece(String key, Alloy a, Form f, Workpiece.Envelope e, Operation... route) {
        return new Workpiece(key, 1, a, f, 1, 1.5, List.of(route), e);
    }

    @Test void everyFamilyHasHeadsAndTheirKindsAreTheirOwn() {
        Set<String> kinds = new HashSet<>();
        for (Station s : Station.values()) {
            assertEquals(s.family, !Head.of(s).isEmpty(), s + " has heads iff it is a family");
            if (s.family) { assertNotNull(MachineFrame.of(s), s + " has a frame"); assertTrue(ToolConfig.STATIONS.contains(s)); }
        }
        for (Head h : Head.values()) {
            assertTrue(kinds.add(h.kind), "one head a kind: " + h.kind);
            assertNull(ToolConfig.Accessory.ofKind(h.kind), h.kind + " is not an accessory");
            assertEquals(h, Head.ofKind(h.kind));
            assertTrue(h.ratedW() > 0);
        }
        assertEquals(34, Head.values().length);
    }

    @Test void featuresDecideWhichHeadTakesAStep() {
        Workpiece gear = piece("gear", Alloy.ALLOY_STEEL, Form.FORGING, Workpiece.Envelope.turned(0.04, 0.2));
        Operation teeth = Operation.cut(Process.MILL, 1e-5, 0.0162, 0.2, 8, false).on(Operation.AROUND).as(Feature.GEAR_TEETH);
        Operation plainMill = Operation.cut(Process.MILL, 1e-5, 0.0162, 0.2, 8, false).on(Operation.AROUND);
        assertTrue(on(Head.HOBBING, 1, false).can(teeth, gear), "a hob cuts gear teeth");
        assertFalse(on(Head.HOBBING, 1, false).can(plainMill, gear), "but not any milling");
        // the old mill with a dividing head still cuts the same step: the feature is not its business
        assertTrue(ToolConfig.full(Station.MILLING_MACHINE).can(teeth, gear));
        assertEquals(ToolConfig.full(Station.MILLING_MACHINE).can(plainMill, gear), ToolConfig.full(Station.MILLING_MACHINE).can(teeth, gear));
        // a bore's finish to IT4: the honing head; the boring mill (0.7's honing, IT5) does not reach it, the CNC does
        Workpiece block = piece("block", Alloy.GREY_IRON, Form.CASTING, Workpiece.Envelope.box(0.45, 0.2, 0.3));
        Operation hone = Operation.cut(Process.GRIND, 1e-6, 0.2, 0.086, 4, false).on(Operation.TOP).as(Feature.BORE);
        assertTrue(on(Head.HONING, 1, false).can(hone, block));
        assertFalse(ToolConfig.full(Station.BORING_MILL).can(hone, block));
        assertTrue(Station.CNC.can(hone));
        // shapes: the centreless grinder takes plain round work only, the hob outside teeth only, the plasma metal only
        Workpiece pin = piece("pin", Alloy.STEEL, Form.BAR, Workpiece.Envelope.turned(0.06, 0.02));
        Operation round = Operation.cut(Process.GRIND, 1e-7, 0.004, 0.02, 5, false).on(Operation.AROUND);
        assertTrue(on(Head.CENTRELESS_GRINDING, 1, false).can(round, pin));
        assertFalse(on(Head.CENTRELESS_GRINDING, 1, false).can(round.on(Operation.AROUND | Operation.FRONT), pin), "a shoulder at its end");
        assertFalse(on(Head.HOBBING, 1, false).can(teeth.on(Operation.FRONT), gear), "inside teeth: the shaper's");
        assertTrue(on(Head.GEAR_SHAPING, 1, false).can(teeth.on(Operation.FRONT), gear));
        Workpiece ply = piece("rib", Alloy.CFRP, Form.LAMINATE, Workpiece.Envelope.box(0.5, 0.3, 0.004));
        Operation profile = new Operation(Process.PROFILE, 0, 0.006, 1, 0, 0, 0.5, 13, 3, Operation.TOP, 0);
        assertFalse(on(Head.PLASMA, 1, false, ToolConfig.Accessory.FUME_EXTRACTOR).can(profile, ply), "plasma needs a conductor");
        assertTrue(on(Head.WATERJET, 1, false).can(profile, ply), "the waterjet cuts composite");
    }

    @Test void accessoriesTheHeadsNeed() {
        Workpiece plate = piece("plate", Alloy.STEEL, Form.PLATE, Workpiece.Envelope.box(0.4, 0.3, 0.006));
        Operation flat = Operation.cut(Process.GRIND, 1e-6, 0.12, 0.006, 6, false).on(Operation.TOP);
        assertEquals(ToolConfig.Accessory.MAGNETIC_CHUCK, on(Head.SURFACE_GRINDING, 1, false).lacks(flat, plate));
        assertNull(on(Head.SURFACE_GRINDING, 1, false, ToolConfig.Accessory.MAGNETIC_CHUCK).lacks(flat, plate));
        Operation profile = new Operation(Process.PROFILE, 0, 0.0084, 1, 0, 0, 0.4, 13, 3, Operation.TOP, 0);
        assertEquals(ToolConfig.Accessory.FUME_EXTRACTOR, on(Head.LASER, 1, false).lacks(profile, plate), "a laser cuts under extraction");
        Operation angled = new Operation(Process.PROFILE, 0, 0.0084, 1, 0, 0, 0.4, 13, 5, Operation.TOP, 0);
        assertFalse(on(Head.PLASMA, 1, false, ToolConfig.Accessory.FUME_EXTRACTOR).can(angled, plate), "a bevel needs the bevel head");
        assertTrue(on(Head.PLASMA, 1, false, ToolConfig.Accessory.FUME_EXTRACTOR, ToolConfig.Accessory.BEVEL_HEAD).can(angled, plate));
        Workpiece hub = piece("hub", Alloy.STEEL, Form.FORGING, Workpiece.Envelope.box(0.15, 0.15, 0.1));
        Operation splines = Operation.cut(Process.MILL, 1e-6, 0.01, 0.05, 9, false).on(Operation.FRONT).as(Feature.SPLINE);
        assertEquals(ToolConfig.Accessory.DIVIDING_HEAD, on(Head.SLOTTER, 1, false).lacks(splines, hub));
        Workpiece tube = new Workpiece("tube", 1, Alloy.STEEL, Form.TUBE, 1, 1.1, List.of(), Workpiece.Envelope.box(1.2, 0.04, 0.04));
        Operation cut = new Operation(Process.SAW, 1e-6, 0.0016, 0, 0, 0, 0.04, 13, 3, Operation.FRONT, 0);
        assertEquals(ToolConfig.Accessory.MITRE_VISE, on(Head.BAND_SAW, 1, false).lacks(cut, tube));
        assertThrows(IllegalArgumentException.class, () -> new ToolConfig(Station.GRINDER, null, 1, Set.of(), false), "a family needs its head");
        assertThrows(IllegalArgumentException.class, () -> new ToolConfig(Station.GRINDER, Head.HOBBING, 1, Set.of(), false), "and its own");
        assertThrows(IllegalArgumentException.class, () -> on(Head.LASER, 1, false, ToolConfig.Accessory.MAGNETIC_CHUCK));
    }

    @Test void theNcDriveRunsAloneAndOneGradeFinerNotTwo() {
        Workpiece shaft = piece("shaft", Alloy.STEEL, Form.BAR, Workpiece.Envelope.turned(0.3, 0.05));
        Operation ground4 = Operation.cut(Process.GRIND, 1e-6, 0.03, 0.05, 4, false).on(Operation.AROUND);
        assertFalse(on(Head.CYLINDRICAL_GRINDING, 1, false).can(ground4, shaft), "IT5 by hand");
        assertTrue(on(Head.CYLINDRICAL_GRINDING, 1, true).can(ground4, shaft), "IT4 under NC");
        assertTrue(on(Head.CYLINDRICAL_GRINDING, 1, false, ToolConfig.Accessory.DIGITAL_READOUT).can(ground4, shaft), "or with a readout");
        Operation ground3 = Operation.cut(Process.GRIND, 1e-6, 0.03, 0.05, 3, false).on(Operation.AROUND);
        assertFalse(on(Head.CYLINDRICAL_GRINDING, 1, true, ToolConfig.Accessory.DIGITAL_READOUT).can(ground3, shaft), "not two finer");
        assertTrue(on(Head.CYLINDRICAL_GRINDING, 1, false).manual() && !on(Head.CYLINDRICAL_GRINDING, 1, true).manual());
        assertFalse(on(Head.WIRE_EDM, 1, false).manual(), "an EDM is numerically controlled");
        assertEquals(ToolConfig.NC_W, on(Head.CYLINDRICAL_GRINDING, 1, true).baseW() - on(Head.CYLINDRICAL_GRINDING, 1, false).baseW(), 1e-9);
        // a 0.9 lathe on the NC drive turns IT6 too, and no complex shape
        Workpiece stub = piece("stub", Alloy.STEEL, Form.BAR, Workpiece.Envelope.turned(0.15, 0.05));
        Operation turn6 = Operation.cut(Process.TURN, 1e-5, 0.01, 0.05, 6, false).on(Operation.AROUND);
        assertTrue(new ToolConfig(Station.LATHE, null, 1, Set.of(), true).can(turn6, stub));
        assertFalse(new ToolConfig(Station.LATHE, null, 1, Set.of(), false).can(turn6, stub));
        // the tool grinder grinds blades (five axes) only under NC
        Workpiece blade = piece("blade", Alloy.INCONEL_718, Form.FORGING, Workpiece.Envelope.box(0.1, 0.04, 0.01));
        Operation aerofoil = Operation.cut(Process.GRIND, 1e-7, 0.01, 0.04, 6, true).as(Feature.AEROFOIL);
        assertFalse(on(Head.TOOL_GRINDING, 1, false).can(aerofoil, blade));
        assertTrue(on(Head.TOOL_GRINDING, 1, true).can(aerofoil, blade));
    }

    @Test void capacityByTheFamilysSize() {
        Workpiece plate = piece("plate", Alloy.STEEL, Form.PLATE, Workpiece.Envelope.box(2.5, 1.2, 0.03));
        assertFalse(on(Head.LASER, 6, false).fits(plate), "30 mm is past a laser's 25");
        assertTrue(on(Head.PLASMA, 2, false).fits(plate), "2.5 m on a 3 m table");
        assertFalse(on(Head.PLASMA, 1, false).fits(plate), "not on a 2 m table");
        Workpiece bar = piece("bar", Alloy.STEEL, Form.BAR, Workpiece.Envelope.turned(0.5, 0.2));
        assertFalse(on(Head.CENTRELESS_GRINDING, 4, false).fits(bar), "0.2 m across past the centreless 0.15");
        assertTrue(on(Head.CYLINDRICAL_GRINDING, 1, false).fits(bar));
        Workpiece ring = piece("ring", Alloy.STEEL, Form.FORGING, Workpiece.Envelope.turned(0.1, 0.8));
        assertFalse(on(Head.HOBBING, 1, false).fits(ring), "an 0.8 m gear on a one-course gear cutter (0.6 m)");
        assertTrue(on(Head.HOBBING, 2, false).fits(ring));
    }

    @Test void timesAreThoseOfRealMachines() {
        // the reference gear (module 3, 40 teeth, 30 mm): hobbed in about 3 minutes, shaved in a minute, ground in about 3
        Workpiece gear = piece("gear", Alloy.STEEL, Form.FORGING, Workpiece.Envelope.turned(0.03, 0.13));
        Operation teeth = Operation.cut(Process.MILL, 0, Machining.GEAR_REF_M2, 0.12, 8, false).on(Operation.AROUND).as(Feature.GEAR_TEETH);
        assertEquals(192, Machining.seconds(teeth, Head.HOBBING, gear), 5);
        Operation finish = Operation.cut(Process.GRIND, 0, Machining.GEAR_REF_M2, 0.12, 6, false).on(Operation.AROUND).as(Feature.GEAR_TEETH);
        assertEquals(60, Machining.seconds(finish, Head.SHAVING, gear), 1);
        assertEquals(192, Machining.seconds(finish, Head.GEAR_GRINDING, gear), 5);
        // honing four 86 mm bores, 50 micrometres of stock: about a minute and a half
        Workpiece block = piece("block", Alloy.GREY_IRON, Form.CASTING, Workpiece.Envelope.box(0.45, 0.2, 0.3));
        double area = 4 * Math.PI * 0.086 * 0.086;
        Operation hone = Operation.cut(Process.GRIND, area * 50e-6, area, 0.086, 5, false).on(Operation.TOP).as(Feature.BORE);
        assertEquals(4 * (25 + 15), Machining.seconds(hone, Head.HONING, block), 1);
        // sawing a 100 mm steel bar on a band saw: minutes, a cold saw faster on what it takes, a hack saw slower
        Workpiece bar = piece("bar", Alloy.STEEL, Form.BAR, Workpiece.Envelope.turned(1.0, 0.1));
        Operation saw = new Operation(Process.SAW, 0, 0, 0, 0, 0, 0.1, 13, 3, Operation.FRONT, 0);
        double band = Machining.seconds(saw, Head.BAND_SAW, bar), cold = Machining.seconds(saw, Head.COLD_SAW, bar), hack = Machining.seconds(saw, Head.HACKSAW, bar);
        assertTrue(band > 60 && band < 600, "band saw " + band);
        assertTrue(cold < band && hack > band, "cold " + cold + " hack " + hack);
        // a laser cuts 6 mm steel at metres a minute, plasma faster, the waterjet slower
        Workpiece plate = piece("plate", Alloy.STEEL, Form.PLATE, Workpiece.Envelope.box(1.0, 0.5, 0.006));
        Operation profile = new Operation(Process.PROFILE, 0, 3.0 * 0.006, 1, 0, 0, 1.0, 11, 3, Operation.TOP, 0);
        double laser = Machining.seconds(profile, Head.LASER, plate), plasma = Machining.seconds(profile, Head.PLASMA, plate), water = Machining.seconds(profile, Head.WATERJET, plate);
        assertTrue(laser > 10 && laser < 300, "laser " + laser);
        assertTrue(plasma < laser && water > laser, "plasma " + plasma + " waterjet " + water);
        // four 0.2 mm orifices 1 mm deep by EDM: seconds; tapping 20 M10 holes: about two minutes
        Workpiece injector = piece("injector", Alloy.STAINLESS_STEEL, Form.BAR, Workpiece.Envelope.turned(0.05, 0.01));
        Operation orifices = Operation.drill(4, 0.0002, 0.001, 5, true).as(Feature.ORIFICE);
        double edm = Machining.seconds(orifices, Head.HOLE_EDM, injector);
        assertTrue(edm > 5 && edm < 120, "EDM drilling " + edm);
        Operation tap = new Operation(Process.TAP, 0, 0, 20, 0.010, 0.02, 0.01, 8, 3, Operation.TOP, 0);
        assertEquals(20 * 7.24, Machining.seconds(tap, Head.TAPPING, block), 20 * 2);
        // the families' fastest heads plan a step; a family is never quicker than it can be
        assertTrue(Double.isFinite(Plans.batchSeconds(gear, teeth, Station.GEAR_CUTTER)));
        assertTrue(Double.isInfinite(Plans.batchSeconds(gear, teeth.as(Feature.AEROFOIL), Station.GEAR_CUTTER)));
    }

    @Test void aJobOnAHeadClampsAsItShould() {
        Workpiece valve = new Workpiece("valve", 40, Alloy.VALVE_STEEL, Form.FORGING, 0.05, 0.07,
                List.of(Operation.cut(Process.GRIND, 1e-7, 0.0025, 0.008, 5, false).on(Operation.AROUND)), Workpiece.Envelope.turned(0.09, 0.008));   // 11 diameters: no steady rest
        Plans.Job centreless = Plans.jobOn(valve, 0, 1, on(Head.CENTRELESS_GRINDING, 1, false));
        Plans.Job between = Plans.jobOn(valve, 0, 1, on(Head.CYLINDRICAL_GRINDING, 1, false));
        assertTrue(centreless.possible() && between.possible());
        assertTrue(centreless.pieceS() < between.pieceS() / 3, "centreless " + centreless.pieceS() + " between centres " + between.pieceS());
        // an NC drive: 15 % quicker cutting and its base load
        Plans.Job nc = Plans.jobOn(valve, 0, 1, on(Head.CYLINDRICAL_GRINDING, 1, true));
        assertTrue(nc.pieceS() < between.pieceS() && nc.pieceW() > 0);
        // a step a head cannot do: no job
        assertFalse(Plans.jobOn(valve, 0, 1, on(Head.HOBBING, 1, false)).possible());
    }
}
