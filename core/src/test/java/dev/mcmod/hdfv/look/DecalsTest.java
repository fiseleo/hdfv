package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Mesh;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.air.SampleAirframes;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.air.WingSurfaceDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stickers laid as meshes (0.13): off the skin by their lift, a pair the mirror image, the picture's coordinates within
 * it, a quarter turn trading length for height, cut at the body's ends, a door's share on the door's piece at the
 * nearest level, a wing's on its surface and an aileron's share in the aileron's mesh.
 */
class DecalsTest {
    static { HdfvParts.register(); }

    private static final String IMAGE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final double GAP = 0.004;

    private static Sticker body(double x, double t, double width, double aspect, double rotation, boolean flip, boolean pair, int layer) {
        return new Sticker(IMAGE, aspect, new Sticker.OnBody(x, t), width, rotation, flip, pair, layer);
    }

    private static Vec3d[] corners(MeshBuilder.Quad q) { return new Vec3d[]{q.a(), q.b(), q.c(), q.d()}; }

    private static Vec3d centre(MeshBuilder.Quad q) { return q.a().add(q.b()).add(q.c()).add(q.d()).scale(0.25); }

    @Test void aStickerStandsOffTheSkinByItsLift() {
        BodyDesign d = SampleBodies.sedan();
        BodySurface s = new BodySurface(d.shape());
        BodyLayout layout = new BodyLayout(d, d.shape(), s);
        double x = 0.5 * (s.rear() + s.front());
        for (int layer : new int[]{0, 10, 63}) {
            Mesh m = new Mesh();
            Decals.body(m, s, layout, List.of(body(x, 1.2, 1.4, 0.6, 20, false, true, layer)), GAP);
            assertFalse(m.quads().isEmpty());
            double lift = Decals.LIFT_M + Decals.LAYER_M * layer, worst = 0;
            for (MeshBuilder.Quad q : m.quads()) {
                assertTrue(q.has(MeshBuilder.DECAL), "a decal");
                assertEquals(Finishes.decal(IMAGE), q.finish());
                for (Vec3d p : corners(q)) {
                    BodySurface.Location l = s.locate(p);
                    worst = Math.max(worst, Math.abs(s.point(l.x(), l.t()).subtract(p).length() - lift));
                }
            }
            assertTrue(worst < 1e-3, "layer " + layer + ": off its lift by " + worst + " m");
        }
    }

    @Test void aPairIsTheMirrorImage() {
        BodyDesign d = SampleBodies.seaplaneFuselage();
        BodySurface s = new BodySurface(d.shape());
        double x = s.rear() + 0.6 * (s.front() - s.rear());
        List<Decals.Laid> laid = Decals.lay(s, List.of(body(x, 1.1, 1.0, 0.8, 25, false, true, 0)));
        assertEquals(2, laid.size());
        List<List<Decals.Corner>> a = laid.get(0).polygons(), b = laid.get(1).polygons();
        assertEquals(a.size(), b.size());
        double worst = 0;
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).size(), b.get(i).size());
            for (int k = 0; k < a.get(i).size(); k++) {
                Decals.Corner p = a.get(i).get(k), q = b.get(i).get(k);
                Vec3d pa = s.point(p.p(), p.q()), pb = s.point(q.p(), q.q());
                worst = Math.max(worst, new Vec3d(pa.x(), pa.y(), -pa.z()).subtract(pb).length());
                assertEquals(p.u(), q.u(), 1e-12, "the same place of the picture");
                assertEquals(p.v(), q.v(), 1e-12);
            }
        }
        assertTrue(worst < 1e-4, "the pair is the sticker mirrored through the middle plane, to " + worst + " m");
    }

    @Test void thePicturesCoordinatesStayWithinIt() {
        BodyDesign d = SampleBodies.sedan();
        BodySurface s = new BodySurface(d.shape());
        BodyLayout layout = new BodyLayout(d, d.shape(), s);
        Mesh m = new Mesh();
        Decals.body(m, s, layout, List.of(body(s.front() - 0.3, Math.PI, 1.5, 1.0, 70, true, false, 2), body(1.8, 1.0, 2.5, 0.5, -40, false, true, 5)), GAP);
        for (MeshBuilder.Quad q : m.quads()) {
            for (float f : q.uv()) { assertTrue(f >= -1e-6 && f <= 1 + 1e-6, "uv " + f); }
        }
    }

    @Test void aQuarterTurnTradesLengthForHeight() {
        BodyDesign d = SampleBodies.seaplaneFuselage();
        BodySurface s = new BodySurface(d.shape());
        double x = s.rear() + 0.55 * (s.front() - s.rear()), t = Math.PI / 2;
        double[] straight = extents(s, Decals.lay(s, List.of(body(x, t, 1.2, 0.5, 0, false, false, 0))).get(0), x, t);
        double[] turned = extents(s, Decals.lay(s, List.of(body(x, t, 1.2, 0.5, 90, false, false, 0))).get(0), x, t);
        assertEquals(1.2, straight[0], 0.06, "along the body: its width");
        assertEquals(0.6, straight[1], 0.06, "round it: its height");
        assertEquals(0.6, turned[0], 0.06, "turned: its height along the body");
        assertEquals(1.2, turned[1], 0.06, "and its width round it");
    }

    /** How far a laid sticker reaches along the body (m) and round it (m, by the arc speed at its middle). */
    private static double[] extents(BodySurface s, Decals.Laid l, double x, double t) {
        double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, t0 = Double.POSITIVE_INFINITY, t1 = Double.NEGATIVE_INFINITY;
        for (List<Decals.Corner> poly : l.polygons()) {
            for (Decals.Corner c : poly) { x0 = Math.min(x0, c.p()); x1 = Math.max(x1, c.p()); t0 = Math.min(t0, c.q()); t1 = Math.max(t1, c.q()); }
        }
        return new double[]{x1 - x0, (t1 - t0) * BodyLayout.speed(s, x, t)};
    }

    @Test void whatReachesPastTheBodyIsCutOff() {
        BodyDesign d = SampleBodies.runabout();
        BodySurface s = new BodySurface(d.shape());
        Decals.Laid l = Decals.lay(s, List.of(body(s.front() - 0.2, Math.PI / 2, 2.0, 0.5, 0, false, false, 0))).get(0);
        double uMin = 1, uMax = 0;
        for (List<Decals.Corner> poly : l.polygons()) {
            for (Decals.Corner c : poly) {
                assertTrue(c.p() <= s.front() + 1e-9 && c.p() >= s.rear() - 1e-9, "on the body: x " + c.p());
                uMin = Math.min(uMin, c.u()); uMax = Math.max(uMax, c.u());
            }
        }
        assertTrue(uMax - uMin < 0.9, "part of the picture went past the bow and was cut: u " + uMin + " to " + uMax);
    }

    @Test void aDoorsShareGoesWithTheDoorAtTheNearestLevel() {
        BodyDesign d = SampleBodies.sedan();
        BodySurface s = new BodySurface(d.shape());
        BodyLayout layout = new BodyLayout(d, d.shape(), s);
        List<BodyLayout.Opening> doors = layout.openings.stream().filter(o -> o.type() == BodyLayout.Type.DOOR).toList();
        assertFalse(doors.isEmpty(), "the sedan has doors");
        BodyLayout.Opening door = doors.get(0);
        double[] middle = door.middle();
        Mesh m = new Mesh();
        // wider than the door: part on the door, part on the body round it
        Decals.body(m, s, layout, List.of(body(middle[0], middle[1], 2.4, 0.4, 0, false, false, 0)), GAP);
        int onDoor = 0, nearBody = 0, far = 0;
        BodyLayout.Opening inner = door.inset(0.02, s);
        for (MeshBuilder.Quad q : m.quads()) {
            BodySurface.Location l = s.locate(centre(q));
            double t = l.t();
            if (q.near() == 1) {
                far++;
                assertEquals(0, q.piece(), "level 1 is whole, on the body");
                continue;
            }
            assertEquals(0, q.near(), "only the nearest two levels");
            assertEquals(3, q.detail(), "level 0 alone");
            if (q.piece() >= 1) {
                // the piece is 1 plus the door's index (a wide sticker may reach the next door too)
                BodyLayout.Opening its = doors.get(q.piece() - 1).inset(GAP, s);
                if (q.piece() == 1) { onDoor++; }
                assertTrue(its.contains(l.x(), t) || near(its, l.x(), t), "a door quad lies on its door's panel: " + l);
            } else {
                assertEquals(0, q.piece());
                nearBody++;
                assertFalse(inner.contains(l.x(), t), "a body quad is not well inside the door: " + l);
            }
        }
        assertTrue(onDoor > 0 && nearBody > 0 && far > 0, onDoor + " on the door, " + nearBody + " on the body, " + far + " far");
    }

    /** Within a centimetre of an opening (a quad's middle at its edge, measured at the surface). */
    private static boolean near(BodyLayout.Opening o, double x, double t) {
        for (double dx = -0.01; dx <= 0.01; dx += 0.005) {
            for (double dt = -0.01; dt <= 0.01; dt += 0.005) { if (o.contains(x + dx, t + dt)) { return true; } }
        }
        return false;
    }

    @Test void aWingStickerLiesOnItsSurfaceAndTheAileronsShareMovesWithIt() {
        WingDesign wing = SampleAirframes.lightWing();
        WingSurfaceDesign aileron = wing.aileron();
        assertTrue(aileron.active(), "the light wing has ailerons");
        WingSurface w = new WingSurface(wing);
        double hinge = 1 - aileron.chordFraction(), eta = 0.5 * (aileron.fromSpan() + aileron.toSpan());
        Sticker st = new Sticker(IMAGE, 0.5, new Sticker.OnWing(eta, hinge, Sticker.Surface.UPPER, Sticker.Side.PORT), 0.8, 0, false, true, 0);
        List<Decals.Laid> laid = Decals.lay(w, List.of(st));
        assertEquals(2, laid.size(), "and its pair on the other half");
        List<Decals.Strip> strips = List.of(new Decals.Strip(-1, aileron.fromSpan(), aileron.toSpan(), hinge), new Decals.Strip(1, aileron.fromSpan(), aileron.toSpan(), hinge));
        Mesh fixed = new Mesh();
        Decals.wingFixed(fixed, w, laid, strips);
        assertFalse(fixed.quads().isEmpty());
        for (MeshBuilder.Quad q : fixed.quads()) {
            WingSurface.Location l = w.locate(centre(q));
            assertEquals(Sticker.Surface.UPPER, l.surface(), "on the upper surface");
            assertTrue(l.chord() < hinge + 1e-3 || l.eta() < aileron.fromSpan() + 1e-3 || l.eta() > aileron.toSpan() - 1e-3, "off the aileron: " + l);
            for (Vec3d p : corners(q)) { assertLifted(w, p); }
        }
        for (Decals.Strip strip : strips) {
            Vec3d shift = w.point(strip.eta0(), hinge, 0.5, strip.side());
            Mesh moving = new Mesh();
            Decals.wingStrip(moving, w, laid, strip, shift);
            assertFalse(moving.quads().isEmpty(), "a share on the aileron of side " + strip.side());
            for (MeshBuilder.Quad q : moving.quads()) {
                WingSurface.Location l = w.locate(centre(q).add(shift));
                assertTrue(l.chord() > hinge - 1e-3, "aft of the hinge: " + l);
                for (Vec3d p : corners(q)) { assertLifted(w, p.add(shift)); }
            }
        }
    }

    private static void assertLifted(WingSurface w, Vec3d p) {
        WingSurface.Location l = w.locate(p);
        double off = w.point(l.eta(), l.chord(), l.surface(), w.side(l.side())).subtract(p).length();
        assertEquals(Decals.LIFT_M, off, 1e-3, "lifted off the wing at " + l);
    }
}
