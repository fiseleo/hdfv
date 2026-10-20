package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.aero.Airfoil;
import dev.mcmod.hdfv.part.HdfvParts;
import dev.mcmod.hdfv.part.air.PlanformDesign;
import dev.mcmod.hdfv.part.air.SampleAirframes;
import dev.mcmod.hdfv.part.air.SectionDesign;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.air.WingSurfaceDesign;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WingSurface (0.13, moved out of AirframeModel): its points are exactly those the wings were drawn with, a point of
 * the surface locates itself (within a millimetre, leading and trailing edges and both halves included), and its
 * normals point out of the section.
 */
class WingSurfaceTest {
    static { HdfvParts.register(); }

    /** The samples (a mirrored wing, a tailplane, a single swept fin) and a wing swept, raised, twisted and tapered between two different sections. */
    static List<WingDesign> wings() {
        WingDesign light = SampleAirframes.lightWing();
        WingDesign swept = new WingDesign("swept", SectionDesign.naca("root", 0.04, 0.3, 0.15), SectionDesign.naca("tip", 0, 0, 0.09),
                new PlanformDesign("planform", 9, 2.4, 0.9, 28, 6, 2, -4, true), light.structure(), light.trailingEdge(), light.aileron());
        return List.of(light, SampleAirframes.seaplaneWing(), SampleAirframes.tailplane(), SampleAirframes.fin(), swept);
    }

    /** AirframeModel.point as 0.12 drew the wings, frozen here: WingSurface must give exactly these. */
    private static Vec3d drawn(PlanformDesign p, Airfoil root, Airfoil tip, double eta, double half, int side, double s, double surface) {
        double y0 = eta * half;
        double chord = p.rootChordM() + (p.tipChordM() - p.rootChordM()) * eta;
        double twist = Math.toRadians(p.incidenceDeg() + p.twistDeg() * eta);
        double xs = Math.clamp(s, 0, 1);
        double upper = (1 - eta) * root.upperAt(xs) + eta * tip.upperAt(xs), lower = (1 - eta) * root.lowerAt(xs) + eta * tip.lowerAt(xs);
        double h = lower + surface * (upper - lower);
        double dx = (0.25 - xs) * chord, dy = h * chord;
        double x = dx * Math.cos(twist) - dy * Math.sin(twist), y = dx * Math.sin(twist) + dy * Math.cos(twist);
        return new Vec3d(-y0 * Math.tan(Math.toRadians(p.sweepDeg())) + x, y0 * Math.tan(Math.toRadians(p.dihedralDeg())) + y, side * y0);
    }

    /** The chord fractions AirframeModel samples between s0 and s1 (its outline's 21 points). */
    private static List<Double> chordPoints(double s0, double s1) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < 21; i++) { out.add(s0 + (s1 - s0) * 0.5 * (1 - Math.cos(Math.PI * i / 20))); }
        return out;
    }

    @Test void pointsAreThoseTheWingsWereDrawnWith() {
        int n = 0;
        for (WingDesign w : wings()) {
            WingSurface ws = new WingSurface(w);
            PlanformDesign p = w.planform();
            Airfoil root = w.rootSection().airfoil(), tip = w.tipSection().airfoil();
            double half = p.mirrored() ? p.spanM() / 2 : p.spanM();
            TreeSet<Double> etas = new TreeSet<>(List.of(0.3337, 0.999));
            for (int k = 0; k <= 12; k++) { etas.add((double) k / 12); }
            TreeSet<Double> ss = new TreeSet<>(List.of(-0.1, 0.0004, 0.77, 1.2));
            ss.addAll(chordPoints(0, 1));
            for (WingSurfaceDesign c : List.of(w.trailingEdge(), w.aileron())) {
                if (!c.active()) { continue; }
                etas.add(c.fromSpan()); etas.add(c.toSpan());
                ss.addAll(chordPoints(0, 1 - c.chordFraction())); ss.addAll(chordPoints(1 - c.chordFraction(), 1));
            }
            assertArrayEquals(p.mirrored() ? new int[]{-1, 1} : new int[]{1}, ws.sides(), w.name());
            for (int side : ws.sides()) {
                for (double eta : etas) {
                    for (double s : ss) {
                        for (double f : new double[]{0, 0.5, 1}) {
                            assertEquals(drawn(p, root, tip, eta, half, side, s, f), ws.point(eta, s, f, side), w.name() + " at " + eta + ", " + s + ", " + f + ", " + side);
                            n++;
                        }
                    }
                    assertEquals(ws.point(eta, 0.3, 1, side), ws.point(eta, 0.3, Sticker.Surface.UPPER, side));
                    assertEquals(ws.point(eta, 0.3, 0, side), ws.point(eta, 0.3, Sticker.Surface.LOWER, side));
                }
            }
        }
        System.out.println(n + " wing points the same as drawn");
    }

    @Test void aPointOfTheWingLocatesItself() {
        for (WingDesign w : wings()) {
            WingSurface ws = new WingSurface(w);
            double worst = 0;
            String where = "";
            List<Double> ss = new ArrayList<>(List.of(0.0, 0.0002, 0.001, 0.004, 0.996, 1.0));
            for (int i = 0; i <= 40; i++) { ss.add(i / 40.0 + 0.0123 * (i % 3)); }
            for (int side : ws.sides()) {
                for (int k = 0; k <= 16; k++) {
                    double eta = k / 16.0;
                    for (double s : ss) {
                        if (s > 1) { continue; }
                        for (Sticker.Surface surface : Sticker.Surface.values()) {
                            Vec3d p = ws.point(eta, s, surface, side);
                            WingSurface.Location l = ws.locate(p);
                            if (eta > 0) { assertEquals(w.planform().mirrored() && side < 0 ? Sticker.Side.PORT : Sticker.Side.STARBOARD, l.side(), w.name()); }
                            assertTrue(l.eta() >= 0 && l.eta() <= 1 && l.chord() >= 0 && l.chord() <= 1, l.toString());
                            double e = ws.point(l.eta(), l.chord(), l.surface(), ws.side(l.side())).subtract(p).length();
                            if (e > worst) { worst = e; where = eta + ", " + s + ", " + surface + ", " + side + " -> " + l; }
                        }
                    }
                }
            }
            System.out.printf("%s: worst %.2e m (%s)%n", w.name(), worst, where);
            assertTrue(worst < 1e-3, w.name() + ": " + worst + " m at " + where);
        }
    }

    @Test void normalsPointOutOfTheSection() {
        for (WingDesign w : wings()) {
            WingSurface ws = new WingSurface(w);
            for (int side : ws.sides()) {
                for (double eta : new double[]{0, 0.1, 0.5, 0.9, 1}) {
                    for (double s : new double[]{0.02, 0.3, 0.6, 0.95}) {
                        Vec3d mean = ws.point(eta, s, 0.5, side);
                        for (Sticker.Surface surface : Sticker.Surface.values()) {
                            Vec3d n = ws.normal(eta, s, surface, side), at = ws.point(eta, s, surface, side);
                            String what = w.name() + " " + surface + " at " + eta + ", " + s + ", " + side;
                            assertEquals(1, n.length(), 1e-9, what);
                            assertTrue(n.dot(at.subtract(mean)) > 0, what + ": " + n);
                            Vec3d along = ws.point(eta, s + 1e-4, surface, side).subtract(ws.point(eta, s - 1e-4, surface, side)).normalized();
                            assertEquals(0, n.dot(along), 0.02, what + " across the skin");
                        }
                    }
                }
            }
        }
        // a single wing (a fin) has one half: a sticker's side means nothing on it
        WingSurface fin = new WingSurface(SampleAirframes.fin());
        assertEquals(1, fin.side(Sticker.Side.PORT));
        assertEquals(-1, new WingSurface(SampleAirframes.lightWing()).side(Sticker.Side.PORT));
    }
}
