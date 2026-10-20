package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.air.SampleAirframes;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stickers (0.13): a body's and a wing's go through JSON and blueprints, a design without them writes 0.12's JSON and
 * an old one reads back with none, the checks stop what is out of range or too many, reading refuses what is not a
 * sticker, and the design screen leaves them out but keeps them.
 */
class StickerTest {
    static { HdfvParts.register(); }

    static final String IMAGE = "0123456789abcdef".repeat(4), OTHER = "f".repeat(64);

    static Sticker onBody(double x, double t) { return new Sticker(IMAGE, 0.75, new Sticker.OnBody(x, t), 1.2, 15, false, true, 0); }
    static Sticker onWing(double eta, double chord, Sticker.Surface surface, Sticker.Side side) {
        return new Sticker(OTHER, 1.5, new Sticker.OnWing(eta, chord, surface, side), 0.8, -30, true, false, 3);
    }

    static BodyDesign withStickers(BodyDesign b, List<Sticker> stickers) {
        return (BodyDesign) Designs.withPage(b, BodyDesign.SETTINGS, RecordEditor.with(b.settings(), "stickers", stickers));
    }
    static WingDesign withStickers(WingDesign w, List<Sticker> stickers) {
        return (WingDesign) Designs.withPage(w, "structure", RecordEditor.with(w.structure(), "stickers", stickers));
    }

    private static List<DesignIssue> invalid(PartDesign d) { return d.check().stream().filter(i -> i.kind() == DesignIssue.Kind.INVALID).toList(); }
    private static boolean invalid(PartDesign d, String path) { return invalid(d).stream().anyMatch(i -> i.parameter().equals(path)); }

    @Test void stickersGoThroughJsonAndBlueprints() {
        BodyDesign sedan = withStickers(SampleBodies.sedan(), List.of(onBody(2.1, 1.3), new Sticker(OTHER, 0.05, new Sticker.OnBody(0, 2 * Math.PI), 20, -180, true, false, 63)));
        assertEquals(List.of(), invalid(sedan));
        String json = Designs.write(sedan);
        assertTrue(json.contains("\"stickers\"") && json.contains("\"on\":\"body\""), json);
        assertEquals(sedan, Designs.read(json), "a body through JSON");
        WingDesign wing = withStickers(SampleAirframes.lightWing(), List.of(onWing(0.4, 0.3, Sticker.Surface.UPPER, Sticker.Side.PORT),
                onWing(1, 0, Sticker.Surface.LOWER, Sticker.Side.STARBOARD)));
        assertEquals(List.of(), invalid(wing));
        assertTrue(Designs.write(wing).contains("\"surface\":\"upper\",\"side\":\"port\""), Designs.write(wing));
        assertEquals(wing, Designs.read(Designs.write(wing)), "a wing through JSON");
        // in a vehicle's blueprint (pretty printed)
        VehicleBlueprint seaplane = SampleBlueprints.seaplane();
        List<VehicleBlueprint.Entry> parts = new ArrayList<>();
        for (VehicleBlueprint.Entry e : seaplane.parts()) {
            PartDesign d = e.design() instanceof BodyDesign b ? withStickers(b, List.of(onBody(1, 4)))
                    : e.design() instanceof WingDesign w ? withStickers(w, List.of(onWing(0.5, 0.5, Sticker.Surface.UPPER, Sticker.Side.STARBOARD))) : e.design();
            parts.add(new VehicleBlueprint.Entry(e.id(), d, e.mount(), e.bindings()));
        }
        VehicleBlueprint painted = new VehicleBlueprint(seaplane.name(), parts, seaplane.connections());
        assertEquals(painted, VehicleBlueprint.fromJsonText(painted.toJsonText()));
        // 64 stickers at full precision stay inside a design's JSON limit
        Random r = new Random(13);
        List<Sticker> many = new ArrayList<>(), manyOnWing = new ArrayList<>();
        for (int i = 0; i < Sticker.MAX; i++) {
            many.add(new Sticker(IMAGE, 0.05 + r.nextDouble(), new Sticker.OnBody(r.nextDouble() * 4, r.nextDouble() * 6), 0.02 + r.nextDouble(), r.nextDouble() * 360 - 180, r.nextBoolean(), r.nextBoolean(), i));
            manyOnWing.add(new Sticker(IMAGE, 0.05 + r.nextDouble(), new Sticker.OnWing(r.nextDouble(), r.nextDouble(), Sticker.Surface.LOWER, Sticker.Side.STARBOARD),
                    0.02 + r.nextDouble(), r.nextDouble() * 360 - 180, r.nextBoolean(), r.nextBoolean(), i));
        }
        for (PartDesign d : List.<PartDesign>of(withStickers(SampleBodies.flyingBoat(), many), withStickers(SampleAirframes.seaplaneWing(), manyOnWing))) {
            String text = Designs.write(d);
            System.out.println(d.name() + " with " + Sticker.MAX + " stickers: " + text.length() + " characters");
            assertTrue(text.length() < Designs.MAX_JSON, d.name() + ": " + text.length());
            assertEquals(d, Designs.read(text));
            assertEquals(List.of(), invalid(d));
        }
    }

    @Test void oldDesignsAndBlueprintsHaveNone() {
        // a design without stickers writes no key: 0.12's JSON, read back with none
        for (PartDesign d : List.<PartDesign>of(SampleBodies.sedan(), SampleBodies.runabout(), SampleAirframes.lightWing(), SampleAirframes.fin())) {
            String json = Designs.write(d);
            assertFalse(json.contains("stickers"), json);
            PartDesign back = Designs.read(json);
            assertEquals(d, back);
            List<Sticker> none = back instanceof BodyDesign b ? b.settings().stickers() : ((WingDesign) back).structure().stickers();
            assertEquals(List.of(), none, d.name());
        }
        String text = SampleBlueprints.seaplane().toJsonText();
        assertFalse(text.contains("stickers"));
        int designs = 0;
        for (VehicleBlueprint.Entry e : VehicleBlueprint.fromJsonText(text).parts()) {
            if (e.design() instanceof BodyDesign b) { assertEquals(List.of(), b.settings().stickers(), e.id()); designs++; }
            if (e.design() instanceof WingDesign w) { assertEquals(List.of(), w.structure().stickers(), e.id()); designs++; }
        }
        assertTrue(designs >= 3, "the seaplane's bodies and wings: " + designs);
    }

    @Test void checksStopStickersOutOfRangeOrTooMany() {
        BodyDesign sedan = SampleBodies.sedan();
        Object[][] bad = {
                {new Sticker("not a hash", 1, new Sticker.OnBody(1, 1), 1, 0, false, false, 0), "image"},
                {new Sticker(IMAGE.toUpperCase(), 1, new Sticker.OnBody(1, 1), 1, 0, false, false, 0), "image"},
                {new Sticker(IMAGE, 0, new Sticker.OnBody(1, 1), 1, 0, false, false, 0), "aspect"},
                {new Sticker(IMAGE, 21, new Sticker.OnBody(1, 1), 1, 0, false, false, 0), "aspect"},
                {new Sticker(IMAGE, Double.NaN, new Sticker.OnBody(1, 1), 1, 0, false, false, 0), "aspect"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(1, 1), 0.01, 0, false, false, 0), "widthM"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(1, 1), 20.5, 0, false, false, 0), "widthM"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(1, 1), 1, 181, false, false, 0), "rotationDeg"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(1, 1), 1, -180.5, false, false, 0), "rotationDeg"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(1, 1), 1, 0, false, false, -1), "layer"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(1, 1), 1, 0, false, false, 64), "layer"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(-0.1, 1), 1, 0, false, false, 0), "x"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(Double.POSITIVE_INFINITY, 1), 1, 0, false, false, 0), "x"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(1, -0.01), 1, 0, false, false, 0), "t"},
                {new Sticker(IMAGE, 1, new Sticker.OnBody(1, 6.3), 1, 0, false, false, 0), "t"},
                {onWing(0.5, 0.5, Sticker.Surface.UPPER, Sticker.Side.PORT), "on"},
        };
        for (Object[] b : bad) {
            BodyDesign d = withStickers(sedan, List.of(onBody(1, 1), (Sticker) b[0]));
            assertTrue(invalid(d, "settings.stickers[1]." + b[1]), b[1] + ": " + invalid(d));
            assertEquals(1, invalid(d).size(), b[1] + " alone: " + invalid(d));
            assertEquals(new Designs.Place("settings", null), Designs.place(d, invalid(d).get(0)), "on the settings page");
        }
        WingDesign wing = SampleAirframes.lightWing();
        Object[][] badOnWing = {
                {onWing(-0.01, 0.5, Sticker.Surface.UPPER, Sticker.Side.PORT), "eta"}, {onWing(1.01, 0.5, Sticker.Surface.UPPER, Sticker.Side.PORT), "eta"},
                {onWing(0.5, -0.01, Sticker.Surface.UPPER, Sticker.Side.PORT), "chord"}, {onWing(0.5, 1.01, Sticker.Surface.UPPER, Sticker.Side.PORT), "chord"},
                {onBody(1, 1), "on"},
        };
        for (Object[] b : badOnWing) {
            WingDesign d = withStickers(wing, List.of((Sticker) b[0]));
            assertTrue(invalid(d, "structure.stickers[0]." + b[1]), b[1] + ": " + invalid(d));
        }
        // the limit: 64 on a design
        List<Sticker> full = new ArrayList<>();
        for (int i = 0; i < Sticker.MAX; i++) { full.add(onBody(0.05 * i, 0.09 * i)); }
        assertEquals(List.of(), invalid(withStickers(sedan, full)));
        full.add(onBody(1, 1));
        assertTrue(invalid(withStickers(sedan, full), "settings.stickers"), "65 stickers");
        List<Sticker> fullWing = new ArrayList<>();
        for (int i = 0; i <= Sticker.MAX; i++) { fullWing.add(onWing(i / 65.0, 0.5, Sticker.Surface.UPPER, Sticker.Side.STARBOARD)); }
        assertTrue(invalid(withStickers(wing, fullWing), "structure.stickers"), "65 stickers on a wing");
    }

    @Test void readingRefusesWhatIsNotASticker() {
        String good = Designs.write(withStickers(SampleBodies.sedan(), List.of(onBody(2, 1))));
        assertDoesNotThrow(() -> Designs.read(good));
        String[][] broken = {
                {"\"image\":\"" + IMAGE + "\"", "\"image\":\"" + IMAGE.toUpperCase() + "\""},
                {"\"image\":\"" + IMAGE + "\"", "\"image\":\"../../evil\""},
                {"\"image\":\"" + IMAGE + "\"", "\"image\":\"" + IMAGE.substring(1) + "\""},
                {"\"image\":\"" + IMAGE + "\",", ""},
                {"\"on\":\"body\"", "\"on\":\"tail\""},
                {"\"on\":\"body\"", "\"on\":\"wing\""},
                {"\"widthM\":1.2,", ""},
                {"\"aspect\":0.75", "\"aspect\":\"wide\""},
                {"\"layer\":0", "\"layer\":1.5"},
                {"\"flip\":false", "\"flip\":0"},
        };
        for (String[] b : broken) {
            String json = good.replace(b[0], b[1]);
            assertNotEquals(good, json, b[0]);
            assertThrows(IllegalArgumentException.class, () -> Designs.read(json), b[1]);
        }
        assertThrows(IllegalArgumentException.class, () -> Designs.read(good.replaceFirst("\"stickers\":\\[[^\\]]*\\]", "\"stickers\":3")));
        assertThrows(IllegalArgumentException.class, () -> Designs.read(good.replaceFirst("\"stickers\":\\[[^\\]]*\\]", "\"stickers\":[7]")));
        String wing = Designs.write(withStickers(SampleAirframes.lightWing(), List.of(onWing(0.5, 0.5, Sticker.Surface.UPPER, Sticker.Side.PORT))));
        assertDoesNotThrow(() -> Designs.read(wing));
        for (String[] b : new String[][]{{"\"surface\":\"upper\"", "\"surface\":\"middle\""}, {"\"side\":\"port\"", "\"side\":\"left\""},
                {"\"eta\":0.5,", ""}}) {
            assertThrows(IllegalArgumentException.class, () -> Designs.read(wing.replace(b[0], b[1])), b[1]);
        }
    }

    @Test void theDesignScreenLeavesThemOutAndKeepsThem() {
        BodyDesign sedan = withStickers(SampleBodies.sedan(), List.of(onBody(2, 1)));
        assertTrue(RecordEditor.parameters(sedan.settings()).stream().noneMatch(p -> p.name().equals("stickers")));
        assertThrows(IllegalArgumentException.class, () -> RecordEditor.set(sedan.settings(), "stickers", "[]"));
        // editing another parameter keeps them
        BodyDesign red = (BodyDesign) Designs.set(sedan, "settings", "paintColor", "#B3161C");
        assertEquals(sedan.settings().stickers(), red.settings().stickers());
        WingDesign wing = withStickers(SampleAirframes.lightWing(), List.of(onWing(0.5, 0.5, Sticker.Surface.UPPER, Sticker.Side.PORT)));
        assertTrue(RecordEditor.parameters(wing.structure()).stream().noneMatch(p -> p.name().equals("stickers")));
        assertEquals(wing.structure().stickers(), ((WingDesign) Designs.set(wing, "structure", "paintFinish", "gloss")).structure().stickers());
        // the list is the design's own
        List<Sticker> list = new ArrayList<>(List.of(onBody(1, 1)));
        BodyDesign b = withStickers(SampleBodies.sedan(), list);
        list.clear();
        assertEquals(1, b.settings().stickers().size());
        assertThrows(UnsupportedOperationException.class, () -> b.settings().stickers().clear());
    }
}
