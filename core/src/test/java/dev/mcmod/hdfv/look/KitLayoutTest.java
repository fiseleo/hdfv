package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.body.BodyShape;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyKitDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import dev.mcmod.hdfv.part.engine.EngineMaterial;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.16 follow-up (the user's 「車頭的模型很怪」): the kit's pieces on the skin stay on it, whatever the nose. The dam,
 * splitter and lip had been boxes at the floor's height and 1.3 m wide under a nose that rises 22 cm and narrows to
 * 0.8 m; the flares stood 16 cm off its tapering side; the scoop was a flat box on a sloping hood.
 */
class KitLayoutTest {
    static { HdfvParts.register(); }

    static final BodyKitDesign KIT = new BodyKitDesign(BodyKitDesign.PAGE, BodyKitDesign.Bumper.SPORT, 0.05, 0.08, 0.06, 0.04, 10, 0.5, 0.03, 1.5, 0.25, 8, 0.25, 0.15,
            BodyKitDesign.RoofRack.NONE, 0.05, 2, 0.09, EngineMaterial.CFRP, "");

    /** The sample sedan, its nose's bluntness and the hood's length as given, with the kit above. */
    static BodyDesign sedan(double bluntness, double hoodM) {
        BodyDesign d = SampleBodies.sedan();
        d = d.withSettings(RecordEditor.with(d.settings(), "noseBluntness", bluntness));
        d = d.withModule(0, RecordEditor.with(d.modules().get(0), "lengthM", hoodM));
        return d.withKit(KIT);
    }

    /** How far a point stands out of the skin at its own x and height (negative: inside), on its side. */
    static double gap(BodySurface s, Vec3d p) {
        double x = Math.clamp(p.x(), s.rear(), s.front());
        return Math.abs(p.z()) - Math.abs(s.point(x, s.portAngle(x, p.y())).z());
    }

    static List<Vec3d> points(KitLayout k, String role) {
        return k.panels.stream().filter(p -> p.role().equals(role)).flatMap(p -> p.quads().stream()).flatMap(q -> java.util.Arrays.stream(q)).toList();
    }

    @Test void theFrontPiecesHangUnderTheNoseItself() {
        for (double[] nose : new double[][]{{0.55, 1.0}, {0.45, 1.2}, {0.3, 1.0}}) {
            BodyDesign d = sedan(nose[0], nose[1]);
            BodyShape shape = d.shape();
            BodySurface s = new BodySurface(shape);
            KitLayout k = KitLayout.of(d, shape);
            double roll = LampLayout.rollOver(d, shape.stations().get(shape.stations().size() - 1));
            FrontLayout.Face bumperFace = FrontLayout.face(d, s);
            double out = bumperFace == null ? 0 : bumperFace.bumperOut();   // 0.18: the kit hangs from the front's bumper, standing out from the skin
            String what = "nose " + nose[0] + ", hood " + nose[1] + " m: ";
            for (Vec3d p : points(k, "dam")) {
                double x = Math.min(p.x(), s.front());
                assertTrue(p.y() >= s.bottom(x) - KIT.damM() - 0.005, what + "the dam hangs " + KIT.damM() + " m under the nose's edge there: " + p + " under " + s.bottom(x));
                assertTrue(p.x() <= s.front() + roll + out + 1e-9, what + "no further forward than the nose: " + p);
            }
            for (Vec3d p : points(k, "splitter")) {
                double x = Math.min(p.x(), s.front());
                assertTrue(p.x() <= s.front() + roll + out + KIT.splitterM() + 0.005, what + "the splitter stands out its length from the dam: " + p);
                double from = Math.max(x - KIT.splitterM(), s.rear());   // where its edge stands out from
                assertTrue(Math.abs(p.z()) <= 0.5 * s.width(from) + out + KIT.splitterM() + 0.005, what + "and no wider than the nose there: " + p + " width " + s.width(from));
                assertTrue(p.y() >= Math.min(s.bottom(x), s.bottom(from)) - KIT.damM() - 0.015, what + "at the dam's foot: " + p);
            }
            assertFalse(points(k, "lip").isEmpty(), "a sport bumper's lip");
            // the flares on the skin: the lip outM out of it, the face back into it
            for (KitLayout.Panel p : k.panels) {
                if (!p.role().equals("flare")) { continue; }
                for (Vec3d[] q : p.quads()) {
                    for (Vec3d v : q) {
                        double g = gap(s, v);
                        assertTrue(g <= KIT.flareM() + 0.005 && g >= -0.02, what + "a flare's point " + v + " stands " + g + " m out of the skin");
                    }
                }
            }
            // the skirts under the sill's edge, not off it
            for (Vec3d v : points(k, "skirt")) {
                if (v.y() < s.bottom(v.x())) { continue; }
                assertTrue(gap(s, v) < 0.01 && gap(s, v) > -0.04, what + "a skirt's top on the sill (tucked behind it): " + v + " " + gap(s, v));
            }
            // the scoop's base on the hood
            for (KitLayout.Panel p : k.panels) {
                if (!p.role().equals("scoop_mouth")) { continue; }
                for (Vec3d[] q : p.quads()) {
                    assertEquals(KitLayout.top(s, q[0].x(), q[0].z()), q[0].y(), 0.006, what + "the scoop's mouth starts on the hood");
                }
            }
        }
    }

    @Test void theKitStillWeighsAndPullsDown() {
        BodyDesign d = sedan(0.55, 1.0);
        KitLayout k = KitLayout.of(d);
        assertTrue(k.massKg > 3 && k.massKg < 60, k.massKg + " kg");
        assertEquals(4, k.flares.size());
        assertTrue(k.panels.stream().anyMatch(p -> p.role().equals("splitter")));
        assertNotNull(k.aero);
    }
}
