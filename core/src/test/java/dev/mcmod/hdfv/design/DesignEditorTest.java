package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.engine.EngineOil;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import dev.mcmod.hdfv.part.engine.JetEngineDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class DesignEditorTest {
    static { HdfvParts.register(); }

    @Test void everyParameterOfTheSamplesReadsBackFromItsTextUnchanged() {
        for (Designs.Sample sample : Designs.SAMPLES) {
            PartDesign d = sample.design().get();
            String json = Designs.write(d);
            int count = 0;
            for (String page : Designs.pages(d)) {
                for (RecordEditor.Parameter p : RecordEditor.parameters(Designs.page(d, page))) {
                    PartDesign back = Designs.set(d, page, p.name(), p.text());
                    assertEquals(d, back, sample.id() + " " + page + "." + p.name() + " = " + p.text());
                    count++;
                }
            }
            assertEquals(json, Designs.write(Designs.read(json)), sample.id());
            int least = d instanceof PistonEngineDesign ? 100 : d instanceof dev.mcmod.hdfv.part.air.AirPropellerDesign || d instanceof dev.mcmod.hdfv.part.air.RotorDesign ? 30
                    : d instanceof dev.mcmod.hdfv.part.wheel.LandingGearDesign ? 20
                    : d instanceof dev.mcmod.hdfv.part.drive.GearboxDesign ? 8 : d instanceof dev.mcmod.hdfv.part.drive.TransferCaseDesign ? 5
                    : d instanceof dev.mcmod.hdfv.part.engine.RamjetDesign || d instanceof dev.mcmod.hdfv.part.engine.ScramjetDesign
                      || d instanceof dev.mcmod.hdfv.part.engine.PulsejetDesign || d instanceof dev.mcmod.hdfv.part.engine.SolidRocketDesign ? 15
                    : d instanceof dev.mcmod.hdfv.part.FuelTankDesign ? 8
                    : d instanceof dev.mcmod.hdfv.part.truck.FifthWheelDesign || d instanceof dev.mcmod.hdfv.part.truck.LandingLegsDesign
                      || d instanceof dev.mcmod.hdfv.part.StorageBoxDesign ? 5
                    : d instanceof dev.mcmod.hdfv.part.component.ComponentDesign ? 1 : 40;   // 0.23: a standard component is only which one
            assertTrue(count >= least, sample.id() + ": " + count + " parameters");
        }
    }

    @Test void everySampleIsAWorkingDesign() {
        for (Designs.Sample sample : Designs.SAMPLES) {
            PartDesign d = sample.design().get();
            List<DesignIssue> invalid = d.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList();
            assertTrue(invalid.isEmpty(), sample.id() + ": " + invalid);
        }
    }

    @Test void editsChangeTheDesignAndBadTextIsRefused() {
        PistonEngineDesign e = (PistonEngineDesign) Designs.sample("inline_four");
        // lengths are typed in mm, volumes in cm³, pressures in kPa
        PistonEngineDesign wider = (PistonEngineDesign) Designs.set(e, "block", "boreM", " 90 ");
        assertEquals(0.09, wider.block().boreM());
        assertEquals(4.5e-5, ((PistonEngineDesign) Designs.set(e, "head", "chamberVolumeM3", "45")).head().chamberVolumeM3());
        assertEquals(3.5e5, ((PistonEngineDesign) Designs.set(e, "fuelSystem", "fuelPressurePa", "350")).fuelSystem().fuelPressurePa());
        assertEquals(0.0008, ((PistonEngineDesign) Designs.set(e, "head", "gasketThicknessM", "0.8")).head().gasketThicknessM());
        assertEquals("86", RecordEditor.parameters(e.crank()).stream().filter(p -> p.name().equals("strokeM")).findFirst().orElseThrow().text());
        assertEquals(e.head(), wider.head());
        assertEquals(EngineMaterial.STEEL_1045, ((PistonEngineDesign) Designs.set(e, "crank", "material", "steel_1045")).crank().material());
        assertEquals("VEE", ((PistonEngineDesign) Designs.set(e, "block", "layout", "vee")).block().layout().name());
        assertEquals("methanol", ((PistonEngineDesign) Designs.set(e, "fuelSystem", "fuel", "methanol")).fuelSystem().fuel());
        assertEquals("10W-30", ((PistonEngineDesign) Designs.set(e, "lubrication", "oilGrade", "10W-30")).lubrication().oilGrade());
        assertFalse(((PistonEngineDesign) Designs.set(e, "ignition", "knockControl", "false")).ignition().knockControl());
        // the given mass: a number, or blank for the estimate
        PistonEngineDesign given = (PistonEngineDesign) Designs.set(e, "block", "massGivenKg", "30");
        assertEquals(30, given.block().massKg());
        assertTrue(Double.isNaN(((PistonEngineDesign) Designs.set(given, "block", "massGivenKg", "")).block().massGivenKg()));
        assertEquals("inline four", Designs.named(e, "inline four").name());
        for (String[] bad : new String[][]{{"boreM", "abc"}, {"boreM", "NaN"}, {"boreM", "Infinity"}, {"boreM", ""}, {"boreM", "1e999"}, {"cylinders", "4.5"},
                {"layout", "radial"}, {"material", "unobtainium"}, {"nothing", "1"}}) {
            assertThrows(IllegalArgumentException.class, () -> Designs.set(e, "block", bad[0], bad[1]), bad[0] + " = " + bad[1]);
        }
        assertThrows(IllegalArgumentException.class, () -> Designs.set(e, "ignition", "knockControl", "yes"));
        assertThrows(IllegalArgumentException.class, () -> Designs.set(e, "lubrication", "oilGrade", "SAE 90"));
        assertThrows(IllegalArgumentException.class, () -> Designs.set(e, "turbine", "stages", "1"));
        // a jet's fuels are not a piston engine's
        JetEngineDesign j = (JetEngineDesign) Designs.sample("turbojet");
        assertEquals("propane", ((JetEngineDesign) Designs.set(j, "control", "fuel", "propane")).control().fuel());
        assertThrows(IllegalArgumentException.class, () -> Designs.set(j, "control", "fuel", "isooctane"));
    }

    /** A record with a list of numbers, as a gearbox's ratios. */
    record Gears(String name, double[] forwardRatios, double reverseRatio) {}

    @Test void listsOfNumbersAreEditedAsText() {
        Gears g = new Gears("g", new double[]{3.6, 2.1, 1.4}, -3.5);
        RecordEditor.Parameter p = RecordEditor.parameters(g).get(0);
        assertEquals(RecordEditor.Type.NUMBERS, p.type());
        assertEquals("3.6, 2.1, 1.4", p.text());
        Gears six = RecordEditor.set(g, "forwardRatios", "4.171; 2.34 1.521,1.143, 0.867 0.691");
        org.junit.jupiter.api.Assertions.assertArrayEquals(new double[]{4.171, 2.34, 1.521, 1.143, 0.867, 0.691}, six.forwardRatios(), 0);
        assertThrows(IllegalArgumentException.class, () -> RecordEditor.set(g, "forwardRatios", "3.6, two"));
        assertThrows(IllegalArgumentException.class, () -> RecordEditor.set(g, "forwardRatios", " "));
    }

    @Test void parametersHaveUnitsFromTheirNames() {
        assertEquals("m", RecordEditor.unit("boreM"));
        assertEquals("m³", RecordEditor.unit("chamberVolumeM3"));
        assertEquals("m³/s", RecordEditor.unit("fanFlowM3PerS"));
        assertEquals("kg·m²", RecordEditor.unit("flywheelInertiaKgM2"));
        assertEquals("N/m", RecordEditor.unit("springRateNPerM"));
        assertEquals("N·m", RecordEditor.unit("starterTorqueNm"));
        assertEquals("kg", RecordEditor.unit("massGivenKg"));
        assertEquals("kg/s", RecordEditor.unit("airflowKgPerS"));
        assertEquals("cm³/min", RecordEditor.unit("staticFlowCcPerMin"));
        assertEquals("°C", RecordEditor.unit("thermostatC"));
        assertEquals("K", RecordEditor.unit("turbineInletK"));
        assertEquals("°", RecordEditor.unit("bankAngleDeg"));
        assertEquals("rpm", RecordEditor.unit("revLimitRpm"));
        assertEquals("", RecordEditor.unit("surgeMargin"));
        assertEquals("", RecordEditor.unit("cylinders"));
        assertEquals("", RecordEditor.unit("researchOctane"));
        // shown in units of the size engines have
        assertEquals("mm", RecordEditor.display("boreM").unit());
        assertEquals("cm³", RecordEditor.display("chamberVolumeM3").unit());
        assertEquals("L", RecordEditor.display("oilVolumeM3").unit());
        assertEquals("cm²", RecordEditor.display("captureAreaM2").unit());
        assertEquals("m²", RecordEditor.display("radiatorAreaM2").unit());
        assertEquals("kPa", RecordEditor.display("fuelPressurePa").unit());
        assertEquals("N/mm", RecordEditor.display("springRateNPerM").unit());
        assertEquals("g", RecordEditor.display("followerMassKg").unit());
        assertEquals("kg", RecordEditor.display("massGivenKg").unit());
        assertEquals("rpm", RecordEditor.display("designRpm").unit());
        assertEquals(140, RecordEditor.display("boreM").of(0.14), 1e-9);
    }

    @Test void issuesFindTheirPageAndParameter() {
        PartDesign e = Designs.sample("inline_four");
        assertEquals(new Designs.Place("block", "massGivenKg"), Designs.place(e, DesignIssue.invalid("block.massKg", "m")));
        assertEquals(new Designs.Place("piston", "diameterM"), Designs.place(e, DesignIssue.invalid("piston.diameterM", "m")));
        assertEquals(new Designs.Place("block", "material"), Designs.place(e, DesignIssue.invalid("block.material.density", "m")));
        assertEquals(new Designs.Place("block", null), Designs.place(e, DesignIssue.invalid("block.bridgeM", "m")));
        assertEquals(new Designs.Place(null, "compressionRatio"), Designs.place(e, DesignIssue.invalid("compressionRatio", "m")));
        assertEquals(new Designs.Place("valves", null), Designs.place(e, DesignIssue.invalid("valves", "m")));
    }

    @Test void blueprintsLongerThanTheLimitAreRefused() {
        StringBuilder b = new StringBuilder("{\"type\":\"hdfv:jet_engine\",\"name\":\"");
        while (b.length() < Designs.MAX_JSON) { b.append('x'); }
        b.append("\"}");
        assertThrows(IllegalArgumentException.class, () -> Designs.read(b.toString()));
        assertThrows(IllegalArgumentException.class, () -> Designs.read("{\"type\":\"hdfv:nothing\"}"));
    }

    @Test void theSamplesShowTheirFigures() {
        Map<String, Designs.Figure> piston = byKey(Designs.figures(Designs.sample("inline_four")));
        assertEquals(1.998, piston.get("displacement").value(), 0.005);
        assertEquals(11, piston.get("compressionRatio").value(), 0.05);
        Map<String, Designs.Figure> jet = byKey(Designs.figures(Designs.sample("turbojet")));
        assertEquals(14.28, jet.get("designThrust").value(), 0.2);
        assertEquals("kN", jet.get("designThrust").unit());
    }

    private static Map<String, Designs.Figure> byKey(List<Designs.Figure> figures) {
        Map<String, Designs.Figure> m = new java.util.HashMap<>();
        for (Designs.Figure f : figures) { m.put(f.key(), f); }
        return m;
    }

    /** Every page, parameter, choice and figure of the samples has a name in both languages (the game's lang files). */
    @Test void everyKindStartsAWorkingDesignAndEverySampleKnowsItsKind() {
        for (Designs.Category c : Designs.Category.values()) {
            if (c != Designs.Category.LAND) { assertFalse(Designs.kinds(c).isEmpty(), "category " + c + " has kinds"); }
        }
        for (Designs.Kind k : Designs.KINDS) {
            PartDesign d = Designs.fresh(k.id(), "new " + k.id());
            assertEquals("new " + k.id(), d.name());
            assertTrue(d.check().stream().noneMatch(i -> i.kind() == dev.mcmod.aerocore.api.vehicle.DesignIssue.Kind.INVALID), k.id() + ": " + d.check());
            assertEquals(k.id(), Designs.kindOf(d).id(), "a new design is of its kind");
            d.compile();
        }
        for (Designs.Sample s : Designs.SAMPLES) { assertEquals(s.kind(), Designs.kindOf(s.design().get()).id(), s.id()); }
    }

    @Test void everyNameIsTranslated() throws Exception {
        Path lang = Path.of("..", "neoforge", "src", "main", "resources", "assets", "hdfv", "lang");
        List<String> keys = new ArrayList<>();
        List<PartDesign> designs = new ArrayList<>();
        for (Designs.Sample sample : Designs.SAMPLES) { keys.add("hdfv.sample." + sample.id()); designs.add(sample.design().get()); }
        for (Designs.Category c : Designs.Category.values()) { keys.add("hdfv.category." + c.name().toLowerCase(java.util.Locale.ROOT)); }
        for (Designs.Kind k : Designs.KINDS) { keys.add("hdfv.kind." + k.id()); designs.add(k.fresh().get()); }
        for (PartDesign d : designs) {
            for (Designs.Figure f : Designs.figures(d)) { keys.add("hdfv.figure." + f.key()); }
            for (String page : Designs.pages(d)) {
                // as the design screen looks them up: a listed design's item pages under the list's name ("module",
                // 0.15 "station"), named by their kind
                int item = d instanceof ListedDesign l ? l.listIndex(page) : -1;
                String langPage = item >= 0 ? ((ListedDesign) d).listName() : page;
                keys.add(item >= 0 ? ((ListedDesign) d).kindKey(((ListedDesign) d).itemKind(item)) : "hdfv.page." + page);
                for (RecordEditor.Parameter p : RecordEditor.parameters(Designs.page(d, page))) {
                    keys.add("hdfv.param." + langPage + "." + p.name());
                    if (p.type() == RecordEditor.Type.CHOICE && !p.name().equals("oilGrade")) {
                        // a choice's own name for this parameter, else the shared one
                        for (String c : p.choices()) { keys.add("hdfv.choice." + langPage + "." + p.name() + "." + c + "|hdfv.choice." + c); }
                    }
                }
            }
        }
        assertFalse(EngineOil.GRADES.isEmpty());
        for (String file : List.of("en_us.json", "zh_tw.json")) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) Json.parse(Files.readString(lang.resolve(file)));
            TreeSet<String> missing = new TreeSet<>();
            for (String k : keys) { if (java.util.Arrays.stream(k.split("[|]")).noneMatch(m::containsKey)) { missing.add(k); } }
            assertTrue(missing.isEmpty(), file + " lacks " + missing.size() + ": " + String.join(" ", missing));
        }
    }
}
