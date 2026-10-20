package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.SampleBodies;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BodySurface.locate (0.13): on every sample body a point of the surface gives itself back (point, locate, point within
 * a millimetre), at stations, between them and between the rays; a point off the skin (a sticker's 7 mm, where a click
 * on it lands) finds a place at least as near as the one it was raised from.
 */
class BodySurfaceTest {
    static { HdfvParts.register(); }

    private static final List<BodyDesign> BODIES = List.of(SampleBodies.seaplaneFuselage(), SampleBodies.seaplaneFloat(), SampleBodies.helicopterFuselage(),
            SampleBodies.runabout(), SampleBodies.sedan(), SampleBodies.flyingBoat());

    /** Along the body: evenly, and every station itself. */
    private static List<Double> xs(BodySurface s) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i <= 40; i++) { out.add(s.rear() + (s.front() - s.rear()) * i / 40); }
        for (double x : s.xs) { out.add(x); }
        return out;
    }

    @Test void aPointOfTheSurfaceLocatesItself() {
        for (BodyDesign d : BODIES) {
            BodySurface s = new BodySurface(d.shape());
            double worst = 0;
            String where = "";
            int n = 0;
            for (double x : xs(s)) {
                // 97 angles: most between the rays
                for (int k = 0; k < 97; k++) {
                    double t = 2 * Math.PI * k / 97;
                    Vec3d p = s.point(x, t);
                    BodySurface.Location l = s.locate(p);
                    assertTrue(l.t() >= 0 && l.t() < 2 * Math.PI && l.x() >= s.rear() && l.x() <= s.front(), d.name() + ": " + l);
                    double e = s.point(l.x(), l.t()).subtract(p).length();
                    if (e > worst) { worst = e; where = String.format(java.util.Locale.ROOT, "x %.3f t %.3f -> %s", x, t, l); }
                    n++;
                }
            }
            System.out.printf("%s: %d points, worst %.2e m (%s)%n", d.name(), n, worst, where);
            assertTrue(worst < 1e-3, d.name() + ": " + worst + " m at " + where);
        }
    }

    @Test void aPointOffTheSkinFindsTheNearestPlace() {
        for (BodyDesign d : BODIES) {
            BodySurface s = new BodySurface(d.shape());
            double drift = 0;
            for (int i = 1; i < 20; i++) {
                double x = s.rear() + (s.front() - s.rear()) * i / 20;
                for (int k = 0; k < 53; k++) {
                    double t = 2 * Math.PI * k / 53;
                    Vec3d base = s.point(x, t), p = base.add(s.normal(x, t).scale(0.007));
                    BodySurface.Location l = s.locate(p);
                    Vec3d found = s.point(l.x(), l.t());
                    assertTrue(found.subtract(p).length() <= base.subtract(p).length() + 2e-4,
                            d.name() + " at x " + x + ", t " + t + ": " + found.subtract(p).length() + " m from the click, the skin under it " + base.subtract(p).length());
                    drift = Math.max(drift, found.subtract(base).length());
                }
            }
            System.out.printf("%s: 7 mm off the skin, back within %.1f mm of the place under it%n", d.name(), drift * 1000);
        }
    }
}
