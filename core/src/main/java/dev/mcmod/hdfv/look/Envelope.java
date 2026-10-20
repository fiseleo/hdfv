package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 0.14: the room a part takes, as named boxes in its own frame (an engine's crankcase, head, intake, air box, radiator...),
 * for checking that it fits in a body (assembly Fitting) and for keeping its model inside it (/hdfvdev envelope). inputX
 * and outputX: where along x a drive comes in (a gearbox's bell face) and goes out (an engine's flywheel face, a
 * gearbox's tail flange); NaN where there is none.
 */
public record Envelope(List<Box> boxes, double inputX, double outputX) {
    public Envelope {
        boxes = List.copyOf(boxes);
        if (boxes.isEmpty()) { throw new IllegalArgumentException("An envelope needs a box"); }
    }

    /** A named box, min and max corners. */
    public record Box(String name, Vec3d min, Vec3d max) {
        public Box { Objects.requireNonNull(name, "name"); }
        public Vec3d size() { return max.subtract(min); }
        /** Whether a point is inside (with a margin, m). */
        public boolean contains(Vec3d p, double margin) {
            return p.x() >= min.x() - margin && p.x() <= max.x() + margin && p.y() >= min.y() - margin && p.y() <= max.y() + margin
                    && p.z() >= min.z() - margin && p.z() <= max.z() + margin;
        }
    }

    /** The box round all of it. */
    public Box bounds() {
        double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY}, hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (Box b : boxes) {
            lo[0] = Math.min(lo[0], b.min.x()); lo[1] = Math.min(lo[1], b.min.y()); lo[2] = Math.min(lo[2], b.min.z());
            hi[0] = Math.max(hi[0], b.max.x()); hi[1] = Math.max(hi[1], b.max.y()); hi[2] = Math.max(hi[2], b.max.z());
        }
        return new Box("bounds", new Vec3d(lo[0], lo[1], lo[2]), new Vec3d(hi[0], hi[1], hi[2]));
    }

    /** Whether a point is in one of its boxes (with a margin, m). */
    public boolean contains(Vec3d p, double margin) {
        for (Box b : boxes) { if (b.contains(p, margin)) { return true; } }
        return false;
    }

    /** The same turned (each box the box round its turned corners); inputX and outputX go with x only when the turn keeps x. */
    public Envelope turned(Orientation q) {
        List<Box> out = new ArrayList<>();
        for (Box b : boxes) {
            Points p = new Points();
            for (int k = 0; k < 8; k++) {
                p.add(q.rotate(new Vec3d((k & 1) == 0 ? b.min.x() : b.max.x(), (k & 2) == 0 ? b.min.y() : b.max.y(), (k & 4) == 0 ? b.min.z() : b.max.z())), 0);
            }
            out.add(p.box(b.name));
        }
        boolean alongX = Math.abs(q.rotate(Vec3d.X).x()) > 0.999;
        return new Envelope(out, alongX ? inputX : Double.NaN, alongX ? outputX : Double.NaN);
    }

    /** Collects points (each with a radius round it) into a box. */
    public static final class Points {
        private double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, z0 = Double.POSITIVE_INFINITY;
        private double x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY, z1 = Double.NEGATIVE_INFINITY;

        public Points add(Vec3d p, double radius) {
            x0 = Math.min(x0, p.x() - radius); y0 = Math.min(y0, p.y() - radius); z0 = Math.min(z0, p.z() - radius);
            x1 = Math.max(x1, p.x() + radius); y1 = Math.max(y1, p.y() + radius); z1 = Math.max(z1, p.z() + radius);
            return this;
        }

        public Points addAll(Iterable<Vec3d> ps, double radius) { for (Vec3d p : ps) { add(p, radius); } return this; }

        /** A point with a radius across x only (a disc or a cylinder along x: a pulley, the flywheel, the starter). */
        public Points addAcross(Vec3d p, double radius) {
            x0 = Math.min(x0, p.x()); x1 = Math.max(x1, p.x());
            y0 = Math.min(y0, p.y() - radius); z0 = Math.min(z0, p.z() - radius);
            y1 = Math.max(y1, p.y() + radius); z1 = Math.max(z1, p.z() + radius);
            return this;
        }

        public boolean empty() { return x0 > x1; }

        public Box box(String name) { return new Box(name, new Vec3d(x0, y0, z0), new Vec3d(x1, y1, z1)); }
    }
}
