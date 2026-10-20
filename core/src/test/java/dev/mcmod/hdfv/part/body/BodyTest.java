package dev.mcmod.hdfv.part.body;

import dev.mcmod.aerocore.api.aero.Wing;
import dev.mcmod.aerocore.api.body.BodyAerodynamics;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.part.BodyPart;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.part.HdfvParts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Bodies of modules: the samples, their editing, masses, drag and mount points. */
class BodyTest {
    static { HdfvParts.register(); }

    private static final List<BodyDesign> SAMPLES = List.of(SampleBodies.seaplaneFuselage(), SampleBodies.seaplaneFloat(), SampleBodies.helicopterFuselage(),
            SampleBodies.runabout(), SampleBodies.sedan(), SampleBodies.flyingBoat());

    @Test void samplesAreValidAndSurviveJson() {
        StringBuilder report = new StringBuilder();
        for (BodyDesign b : SAMPLES) {
            List<DesignIssue> issues = b.check();
            assertTrue(issues.stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), b.name() + ": " + issues);
            BodyPart part = (BodyPart) b.compile();
            PartDesign back = Designs.read(Designs.write(b));
            assertEquals(b, back, b.name() + " through JSON");
            BodyShape shape = b.shape();
            double drag = new BodyAerodynamics(shape, b.extraDragAreaM2(shape)).zeroLiftDragAreaM2(30, Wing.Air.SEA_LEVEL);
            report.append(String.format(java.util.Locale.ROOT, "%n%s: %.2f m, structure %.0f kg, loaded %.0f kg, volume %.2f m3, drag area %.3f m2 (C_D %.3f), planing %s, %s",
                    b.name(), shape.lengthM(), b.structureKg(), b.mass().massKg(), shape.particulars().volumeM3(), drag, drag / shape.particulars().frontalAreaM2(),
                    part.planing(), part.mountPoints().keySet()));
        }
        System.out.println(report);
    }

    @Test void modulesAreEditedAsPages() {
        BodyDesign b = SampleBodies.seaplaneFuselage();
        assertEquals(List.of("settings", "module1", "module2", "module3", "module4"), Designs.pages(b));
        PartDesign longer = Designs.set(b, "module3", "lengthM", "4.5");
        assertEquals(4.5, ((BodyDesign) longer).modules().get(2).lengthM(), 1e-12);
        // a rounded section hides the vee's and the cabin's parameters
        List<String> shown = RecordEditor.parameters(b.modules().get(2)).stream().map(RecordEditor.Parameter::name).toList();
        assertTrue(shown.contains("upperExponent") && !shown.contains("deadriseDeg") && !shown.contains("beltFraction"), shown.toString());
        BodyDesign added = b.added(1, BodyModuleDesign.Kind.CARGO);
        assertEquals(5, added.modules().size());
        assertEquals(BodyModuleDesign.Kind.CARGO, added.modules().get(2).kind());
        assertEquals(b.modules().get(1).widthM(), added.modules().get(2).widthM(), 1e-12, "a new module follows the one ahead of it");
        assertEquals(b, added.removed(2));
        assertEquals(b.modules().get(0), b.moved(0, 1).modules().get(1));
        assertEquals(5, b.duplicated(2).modules().size());
        assertEquals(1, b.removed(0).removed(0).removed(0).removed(0).modules().size(), "the last module stays");
    }

    @Test void mountPointsArePlacedOnTheBody() {
        BodyDesign b = SampleBodies.seaplaneFuselage();
        BodyShape shape = b.shape();
        var points = b.mountPoints(shape);
        assertTrue(points.keySet().containsAll(List.of("engine", "wing", "tail", "m1_top", "m4_bottom")), points.keySet().toString());
        // the engine at the nose's tip, the wing on the cockpit's roof, the tail at the rear end
        assertEquals(shape.lengthM(), points.get("engine").position().x(), 1e-9);
        assertEquals(shape.topAt(points.get("wing").position().x()), points.get("wing").position().y(), 1e-9);
        assertEquals(0, points.get("tail").position().x(), 1e-9);
        var heli = SampleBodies.helicopterFuselage().mountPoints(SampleBodies.helicopterFuselage().shape());
        assertTrue(heli.containsKey("mast") && heli.containsKey("tail_rotor"), heli.keySet().toString());
        var car = SampleBodies.sedan().mountPoints(SampleBodies.sedan().shape());
        assertTrue(car.containsKey("front_axle") && car.containsKey("rear_axle"));
        assertTrue(car.get("front_axle").position().x() > car.get("rear_axle").position().x());
        // 0.6: the hood is an engine bay over the front axle (the same place as the old name), the trunk carries the rear axle
        assertEquals(car.get("front_axle").position().x(), car.get("m1_axle").position().x(), 1e-9);
        assertTrue(car.containsKey("m1_engine") && !car.containsKey("m5_engine"), car.keySet().toString());
        int last = SampleBodies.sedan().modules().size();
        assertEquals(car.get("rear_axle").position().x(), car.get("m" + last + "_axle").position().x(), 1e-9);
        // any module may take the engine (front, mid, rear) and an axle, anywhere along it
        BodyDesign rear = (BodyDesign) Designs.set(SampleBodies.sedan(), "module" + last, "engineBay", "true");
        rear = (BodyDesign) Designs.set(rear, "module" + last, "axleAt", "0.6");
        var rearPoints = rear.mountPoints(rear.shape());
        assertTrue(rearPoints.containsKey("m" + last + "_engine"), "a rear engine bay");
        assertEquals(rear.shape().moduleFrontX(last - 1) - 0.6 * rear.modules().get(last - 1).lengthM(), rearPoints.get("m" + last + "_axle").position().x(), 1e-9);
        assertTrue(rear.check().stream().noneMatch(i -> i.kind() == dev.mcmod.aerocore.api.vehicle.DesignIssue.Kind.INVALID), rear.check().toString());
        assertTrue(invalid(Designs.set(rear, "module" + last, "axleAt", "1.5"), "module" + last + ".axleAt"));
        // an old body (no flags in its JSON) gets the kinds' defaults
        assertEquals(SampleBodies.sedan().modules().get(0).engineBay(), true);
    }

    @Test void checksStopBrokenBodies() {
        BodyDesign b = SampleBodies.seaplaneFuselage();
        // too many seats for the cabin's length
        assertTrue(invalid(Designs.set(b, "module3", "seats", "20"), "module3.seats"));
        // a skin too thin for the load
        assertTrue(invalid(Designs.set(b, "module3", "skinM", "0.0001"), "module3.skinM"));
        // a pressurised cabin of thin skin bursts
        assertTrue(invalid(Designs.set(b, "module3", "pressurePa", "300000"), "module3.pressurePa"));
        // more fuel than the module holds (the editor's fuel is in litres)
        assertTrue(invalid(Designs.set(b, "module2", "fuelM3", "20000"), "module2.cargoM3"));
        // a hull bottom too thin for slamming at speed
        assertTrue(invalid(Designs.set(SampleBodies.runabout(), "module2", "skinM", "0.001"), "module2.skinM"));
    }

    @Test void theFloatsCarryTheSeaplaneWithEightyPercentToSpare() {
        // 14 CFR 25.751 (the floats rule): each main float 80 % more buoyancy than its share of the weight; a 2.6 t seaplane
        double volume = SampleBodies.seaplaneFloat().shape().particulars().volumeM3();
        assertTrue(volume * 1000 > 1.8 * 2600 / 2, "a float of " + volume + " m3");
    }

    private static boolean invalid(PartDesign d, String path) {
        return d.check().stream().anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().equals(path));
    }

    @Test void theBenchTowsHeelsAndBlowsOnABody() {
        long t0 = System.nanoTime();
        var boat = dev.mcmod.hdfv.design.Bench.body(SampleBodies.runabout(), 30);
        double seconds = (System.nanoTime() - t0) / 1e9;
        System.out.printf("runabout bench in %.1f s: draft %.2f m%n", seconds, boat.draftM());
        for (var r : boat.water()) { System.out.printf("  %.1f m/s: R %.0f N (air %.0f), trim %.2f, rise %.3f, swing %.2f%s%n", r.speedMps(), r.resistanceN(), r.airN(), r.trimDeg(), r.riseM(), r.trimSwingDeg(), r.lost() ? " LOST" : ""); }
        for (var h : boat.heel()) { System.out.printf("  heel %.0f: GZ %.3f%n", h.heelDeg(), h.gzM()); }
        assertTrue(boat.draftM() > 0.1 && boat.draftM() < 0.6, "draft " + boat.draftM());
        // upright it is stable: GZ rises from zero
        assertEquals(0, boat.heel().get(0).gzM(), 0.005);
        assertTrue(boat.heel().get(1).gzM() > 0 && boat.heel().get(2).gzM() > boat.heel().get(1).gzM(), "GZ " + boat.heel());
        // with its sterndrive aft it planes bow up all the way to its design speed. Savitsky (1964) for it (B 2 m, 19 deg,
        // 1226 kg, LCG 2.5 m): R 1740 N at 9 m/s, 2650 at 14.6, 3520 at 18 (trim 3.7, 2.4, 1.9 deg). The 2D+t model agrees
        // at 9 m/s and runs about 1 deg flatter and up to 1.7 times the resistance by C_V 4: a hull this light
        // (C_Delta 0.15) lies outside the Fridsma range it is checked against (AeroCore PlaningDynamicsTest).
        double weight = boat.massKg() * 9.80665;
        assertEquals(16, boat.water().size(), "runs " + boat.water());
        for (var r : boat.water()) { assertTrue(!r.lost() && r.trimDeg() > 0 && r.resistanceN() > 0 && r.resistanceN() < 0.6 * weight, "at " + r.speedMps() + ": " + r + ", R / Delta " + r.resistanceN() / weight); }
        var nine = boat.water().get(7);
        assertEquals(1740, nine.resistanceN(), 0.15 * 1740, "at 9 m/s against Savitsky: " + nine);
        assertTrue(seconds < 30, "bench in " + seconds + " s");
        var car = dev.mcmod.hdfv.design.Bench.body(SampleBodies.sedan(), 30);
        assertNull(car.water());
        // a notchback's body alone (its effective backlight about 14 deg): wheels and wheel wells add about a third on a real
        // car (Hucho) to the 0.27-0.32 of production sedans
        assertEquals(0.21, car.dragCoefficient(), 0.03);
        var plane = dev.mcmod.hdfv.design.Bench.body(SampleBodies.helicopterFuselage(), 40);
        // drag least near zero incidence, the Munk moment nose-up with the nose up (unstable)
        assertTrue(plane.air().get(10).dragN() < plane.air().get(25).dragN() && plane.air().get(15).momentNm() > plane.air().get(10).momentNm(), "air " + plane.air());
    }

    @Test void paintIsChosenAndChecked() {
        BodyDesign b = SampleBodies.sedan();
        // typed as a colour code (with or without #, any case) and stored as #RRGGBB
        BodyDesign red = (BodyDesign) Designs.set(b, "settings", "paintColor", "b3161c");
        assertEquals("#B3161C", red.settings().paintColor());
        assertThrows(RuntimeException.class, () -> Designs.set(b, "settings", "paintColor", "red"));
        // the body's own colours cannot be blank: the check stops it
        assertTrue(((BodyDesign) Designs.set(b, "settings", "accentColor", "")).check().stream()
                .anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().contains("accentColor")));
        // a module's own colour, or blank for the body's
        BodyDesign twoTone = (BodyDesign) Designs.set(red, "module3", "paintColor", "#F2C230");
        assertEquals("#F2C230", twoTone.modules().get(2).paintColor());
        assertEquals("", ((BodyDesign) Designs.set(twoTone, "module3", "paintColor", "")).modules().get(2).paintColor());
        assertEquals(twoTone, Designs.read(Designs.write(twoTone)), "through JSON");
        // the palette steps round, starting from its first colour for one that is not in it
        assertEquals(RecordEditor.PALETTE.get(1), RecordEditor.nextColor(RecordEditor.PALETTE.get(0)));
        assertEquals(RecordEditor.PALETTE.get(0), RecordEditor.nextColor("#123456"));
        // a design saved before colours existed reads with its purpose's colours
        String old = Designs.write(b).replaceAll(",?\s*\"(paint|accent|lower)Color\"\s*:\s*\"[^\"]*\"", "");
        assertEquals(b, Designs.read(old), "without colours: " + old.substring(0, Math.min(200, old.length())));
    }

    /** 0.13: stickers on a body: a pair on its sides, one on top near the nose, one low on the starboard quarter. */
    private static List<Sticker> stickers(BodyDesign b) {
        double length = b.shape().lengthM();
        return List.of(new Sticker("ab".repeat(32), 0.6, new Sticker.OnBody(0.5 * length, Math.PI / 2), Math.min(2, 0.3 * length), 0, false, true, 0),
                new Sticker("cd".repeat(32), 1, new Sticker.OnBody(0.85 * length, Math.PI), 0.5, 90, false, false, 1),
                new Sticker("ef".repeat(32), 2.5, new Sticker.OnBody(0.2 * length, 1.5 * Math.PI - 0.3), 0.3, -45, true, false, 2));
    }

    private static BodyDesign withStickers(BodyDesign b, List<Sticker> stickers) {
        return (BodyDesign) Designs.withPage(b, BodyDesign.SETTINGS, RecordEditor.with(b.settings(), "stickers", stickers));
    }

    /**
     * 0.12: the looks (paint finish, glass, lettering, stripes, arches; a wheel's finish, a wing's paint) change nothing
     * the parts compute; 0.13: nor do stickers (a body's and a wing's).
     */
    @Test void looksChangeNothingElse() {
        String[][] looks = {{"paintFinish", "pearl"}, {"glassColor", "#4a90c8"}, {"glassOpacity", "0.8"}, {"registrationText", "n-123ab!"},
                {"stripes", "twin"}, {"stripeColor", "F2C230"}, {"archRadiusM", "420"}};
        for (BodyDesign b : SAMPLES) {
            BodyDesign l = b;
            for (String[] p : looks) {
                // the arches are a car's
                if (!p[0].equals("archRadiusM") || l.settings().purpose() == BodySettingsDesign.Purpose.CAR) { l = (BodyDesign) Designs.set(l, "settings", p[0], p[1]); }
            }
            l = withStickers(l, stickers(b));
            assertEquals(3, l.settings().stickers().size());
            assertEquals(Designs.figures(b), Designs.figures(l), b.name());
            assertEquals("N-123AB", l.settings().registrationText());
            assertEquals("#4A90C8", l.settings().glassColor());
            assertTrue(l.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), b.name() + ": " + l.check());
            assertEquals(b.structureKg(), l.structureKg(), 0, b.name());
            assertEquals(b.mass(), l.mass(), b.name());
            assertEquals(b.compile().mass(), l.compile().mass(), b.name());
            BodyShape s0 = b.shape(), s1 = l.shape();
            assertEquals(new BodyAerodynamics(s0, b.extraDragAreaM2(s0)).zeroLiftDragAreaM2(30, Wing.Air.SEA_LEVEL),
                    new BodyAerodynamics(s1, l.extraDragAreaM2(s1)).zeroLiftDragAreaM2(30, Wing.Air.SEA_LEVEL), 0, b.name());
            assertEquals(b.mountPoints(s0), l.mountPoints(s1), b.name());
            assertEquals(l, Designs.read(Designs.write(l)), b.name() + " through JSON");
        }
        // a body saved before 0.12 reads back with the defaults, and without stickers (the key 0.13 writes when it has some)
        BodyDesign sedan = SampleBodies.sedan();
        String old = Designs.write(withStickers(sedan, stickers(sedan))).replaceAll(",?\\s*\"(paintFinish|glassColor|glassOpacity|registrationText|stripes|stripeColor|archRadiusM)\"\\s*:\\s*(\"[^\"]*\"|[-0-9.eE]+)"
                + "|,?\\s*\"stickers\"\\s*:\\s*\\[[^\\]]*\\]", "");
        assertFalse(old.contains("glassOpacity") || old.contains("stickers"), old);
        assertEquals(sedan, Designs.read(old));
        assertEquals(BodySettingsDesign.GLASS_OPACITY, sedan.settings().glassOpacity());
        // what the checks stop
        for (String[] bad : new String[][]{{"glassOpacity", "0"}, {"archRadiusM", "2000"}}) {
            assertTrue(((BodyDesign) Designs.set(sedan, "settings", bad[0], bad[1])).check().stream()
                    .anyMatch(i -> i.kind() == DesignIssue.Kind.INVALID && i.parameter().contains(bad[0])), bad[0]);
        }
        assertThrows(RuntimeException.class, () -> Designs.set(sedan, "settings", "stripeColor", "gold"));
        // a wheel's finish and a wing's paint
        var axle = dev.mcmod.hdfv.part.wheel.SampleWheels.sedanFront();
        var chrome = (dev.mcmod.hdfv.part.wheel.AxleDesign) Designs.set(axle, "wheel", "rimFinish", "chrome");
        assertEquals(dev.mcmod.hdfv.part.wheel.RimDesign.Look.CHROME, chrome.wheel().rimFinish());
        assertEquals(axle.compile().mass(), chrome.compile().mass());
        assertEquals(chrome, Designs.read(Designs.write(chrome)));
        assertEquals(axle, Designs.read(Designs.write(axle).replaceAll(",?\\s*\"rimFinish\"\\s*:\\s*\"[^\"]*\"", "")));
        var wing = dev.mcmod.hdfv.part.air.SampleAirframes.lightWing();
        var red = (dev.mcmod.hdfv.part.air.WingDesign) Designs.set(Designs.set(wing, "structure", "paintColor", "#B3161C"), "structure", "paintFinish", "gloss");
        red = (dev.mcmod.hdfv.part.air.WingDesign) Designs.withPage(red, "structure", RecordEditor.with(red.structure(), "stickers",
                List.of(new Sticker("ab".repeat(32), 0.5, new Sticker.OnWing(0.6, 0.3, Sticker.Surface.UPPER, Sticker.Side.STARBOARD), 1.5, 0, false, true, 0),
                        new Sticker("cd".repeat(32), 1, new Sticker.OnWing(0.9, 0.5, Sticker.Surface.LOWER, Sticker.Side.PORT), 0.6, 180, true, false, 5))));
        assertEquals("#B3161C", red.structure().paintColor());
        assertTrue(red.check().stream().noneMatch(i -> i.kind() == DesignIssue.Kind.INVALID), red.check().toString());
        assertEquals(wing.compile().mass(), red.compile().mass());
        assertEquals(Designs.figures(wing), Designs.figures(red));
        assertEquals(red, Designs.read(Designs.write(red)));
        assertEquals(wing, Designs.read(Designs.write(red).replaceAll(",?\\s*\"paint(Color|Finish)\"\\s*:\\s*\"[^\"]*\"|,?\\s*\"stickers\"\\s*:\\s*\\[[^\\]]*\\]", "")));
    }
}
