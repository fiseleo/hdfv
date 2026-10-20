package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.MeshOps;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.look.Finishes;

import java.util.ArrayList;
import java.util.List;

/**
 * Placing detail-kit parts and small fittings on a model (0.11): bolts round a flange and along a face, clamps along
 * a pipe, harnesses with their connectors, braided lines with hose ends, a kit part stretched between two points.
 * Every size comes from the caller (a design's dimensions); a part is scaled from its reference size. Fasteners are
 * tagged as detail 2 (left out far away), rivets 3 (only close).
 */
public final class Details {
    private Details() {}

    /** The detail level of fasteners and fittings, and of rivets and the like. */
    public static final int SMALL = 2, TINY = 3;

    /** The rotation taking a part's +y to up and its +x as near to along as can be square to up. */
    public static Orientation basis(Vec3d up, Vec3d along) {
        Vec3d y = MeshBuilder.unit(up);
        Vec3d x = along.subtract(y.scale(along.dot(y)));
        if (x.lengthSquared() < 1e-12) { x = MeshBuilder.perpendiculars(y)[0]; }
        x = x.normalized();
        Vec3d z = x.cross(y);
        // the quaternion of the matrix whose columns are x, y, z
        double m00 = x.x(), m01 = y.x(), m02 = z.x(), m10 = x.y(), m11 = y.y(), m12 = z.y(), m20 = x.z(), m21 = y.z(), m22 = z.z();
        double trace = m00 + m11 + m22, w, qx, qy, qz;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1) * 2;
            w = 0.25 * s; qx = (m21 - m12) / s; qy = (m02 - m20) / s; qz = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1 + m00 - m11 - m22) * 2;
            w = (m21 - m12) / s; qx = 0.25 * s; qy = (m01 + m10) / s; qz = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1 + m11 - m00 - m22) * 2;
            w = (m02 - m20) / s; qx = (m01 + m10) / s; qy = 0.25 * s; qz = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1 + m22 - m00 - m11) * 2;
            w = (m10 - m01) / s; qx = (m02 + m20) / s; qy = (m12 + m21) / s; qz = 0.25 * s;
        }
        double n = Math.sqrt(w * w + qx * qx + qy * qy + qz * qz);
        return new Orientation(w / n, qx / n, qy / n, qz / n);
    }

    /** A kit part standing at a point, its axis along up, scaled to a size (of its reference), at a detail level. */
    public static void place(PartMesh out, DetailKit.Part part, Vec3d at, Vec3d up, Vec3d along, double size, int detail) {
        place(out, part, at, up, along, size, detail, true);
    }

    /** The same from the kit's fine or coarse object: fasteners repeated by the dozen use the coarse one. */
    public static void place(PartMesh out, DetailKit.Part part, Vec3d at, Vec3d up, Vec3d along, double size, int detail, boolean fine) {
        double k = size / part.size;
        add(out, MeshOps.placed(DetailKit.mesh(part, fine), new Vec3d(k, k, k), basis(up, along), at), detail);
    }

    /** A kit part stretched from one point to another (its +y), its width scaled to a size: an actuator, a petal. */
    public static void between(PartMesh out, DetailKit.Part part, Vec3d from, Vec3d to, Vec3d side, double width, int detail) {
        Vec3d d = to.subtract(from);
        double length = d.length();
        if (length < 1e-6) { return; }
        double k = width / (part == DetailKit.Part.ACTUATOR ? 0.08 : part == DetailKit.Part.NOZZLE_PETAL ? 0.3 : part.size);
        add(out, MeshOps.placed(DetailKit.mesh(part, true), new Vec3d(k, length / part.size, k), basis(d, side), from), detail);
    }

    private static void add(PartMesh out, MeshBuilder<?> m, int detail) {
        for (MeshBuilder.Quad q : m.quads()) { out.quads().add(q.withTags((q.tags() & ~MeshBuilder.DETAIL) | detail | (out.tags() & ~MeshBuilder.DETAIL))); }
    }

    /**
     * Bolts round a flange: a circle about a centre square to an axis, their heads standing along the axis, of a
     * thread size (m), a bolt or a stud and nut, starting at a phase (rad). A flange has up to 64 of them, so they are
     * the kit's coarse heads (eight faces each; a fine nut is fifty).
     */
    public static void boltCircle(PartMesh out, Vec3d centre, Vec3d axis, double radius, int count, double thread, DetailKit.Part kind, double phase) {
        Vec3d a = MeshBuilder.unit(axis);
        Vec3d[] f = MeshBuilder.perpendiculars(a);
        for (int i = 0; i < count; i++) {
            double t = phase + 2 * Math.PI * i / count;
            Vec3d radial = f[0].scale(Math.cos(t)).add(f[1].scale(Math.sin(t)));
            place(out, kind, centre.add(radial.scale(radius)), a, radial, thread, SMALL, false);
        }
    }

    /** Bolts in a row from one point to another (both ends included), standing along up (coarse heads, as round a flange). */
    public static void boltRow(PartMesh out, Vec3d from, Vec3d to, int count, Vec3d up, double thread, DetailKit.Part kind) {
        Vec3d along = to.subtract(from);
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5 : (double) i / (count - 1);
            place(out, kind, from.add(along.scale(t)), up, along, thread, SMALL, false);
        }
    }

    /** P-clamps along a path holding a pipe of a radius, about every spacing (not at the ends), their tabs toward a direction. */
    public static void clamps(PartMesh out, List<Vec3d> path, double pipeRadius, double spacing, Vec3d toward) {
        double run = 0, next = spacing * 0.6;
        for (int i = 1; i + 1 < path.size(); i++) {
            run += path.get(i).subtract(path.get(i - 1)).length();
            if (run < next) { continue; }
            next += spacing;
            Vec3d tangent = path.get(i + 1).subtract(path.get(i - 1));
            Vec3d down = MeshBuilder.unit(toward.subtract(tangent.scale(toward.dot(tangent) / Math.max(tangent.lengthSquared(), 1e-12))));
            double k = 2 * pipeRadius / DetailKit.Part.P_CLAMP.size;
            // the kit's clamp holds its pipe (along +x) 8 mm above its tab (+y) and 12 mm toward -z: put the pipe on the path
            Vec3d at = path.get(i).add(down.scale(0.008 * k)).subtract(MeshBuilder.unit(tangent.cross(down)).scale(0.012 * k));
            place(out, DetailKit.Part.P_CLAMP, at, down.scale(-1), tangent, 2 * pipeRadius, SMALL);
        }
    }

    /** A braided line along a path, a crimped hose end at each end. */
    public static void braidedLine(PartMesh out, List<Vec3d> path, double radius) {
        var finish = out.finish();
        int tags = out.tags();
        out.finish(Finishes.BRAIDED_HOSE).tube(path, radius, 10, 0xffbfc3c7).pens(finish, tags);
        int n = path.size();
        if (n < 2) { return; }
        Vec3d start = path.get(0), first = path.get(1).subtract(start), end = path.get(n - 1), last = path.get(n - 2).subtract(end);
        double k = radius / 0.0068;
        place(out, DetailKit.Part.HOSE_END, start, first, MeshBuilder.perpendiculars(MeshBuilder.unit(first))[0], DetailKit.Part.HOSE_END.size * k, SMALL);
        place(out, DetailKit.Part.HOSE_END, end, last, MeshBuilder.perpendiculars(MeshBuilder.unit(last))[0], DetailKit.Part.HOSE_END.size * k, SMALL);
    }

    /**
     * A wiring harness: a bundle along a path (sleeved plastic), and from points on it a lead down to each of a list
     * of ends, a connector on each end facing back along its lead.
     */
    public static void harness(PartMesh out, List<Vec3d> trunk, double radius, List<Vec3d> ends, List<Vec3d> endsUp, double connector) {
        var finish = out.finish();
        int tags = out.tags();
        out.finish(Finishes.PLASTIC).tube(trunk, radius, 8, 0xff2a2c2f);
        for (int i = 0; i < ends.size(); i++) {
            Vec3d end = ends.get(i), up = MeshBuilder.unit(endsUp.get(i));
            Vec3d from = nearest(trunk, end);
            Vec3d lift = end.add(up.scale(4 * connector));
            out.tube(MeshBuilder.bezier(from, from.add(lift.subtract(from).scale(0.3)), lift, end.add(up.scale(1.6 * connector)), 6), radius * 0.45, 6, 0xff2a2c2f);
            place(out, DetailKit.Part.CONNECTOR, end.add(up.scale(1.6 * connector)), up.scale(-1), MeshBuilder.perpendiculars(up)[0], connector, SMALL);
        }
        out.pens(finish, tags);
    }

    private static Vec3d nearest(List<Vec3d> path, Vec3d p) {
        Vec3d best = path.get(0);
        double d = Double.MAX_VALUE;
        for (Vec3d v : path) { double e = v.subtract(p).lengthSquared(); if (e < d) { d = e; best = v; } }
        return best;
    }

    /**
     * A path from one point to another round the x axis (a lead or a line running along an engine's case): it turns
     * about the axis the short way, nearer the axis than neither end nor a clearance radius in between, so it cannot
     * pass through a case that both ends lie outside (0.11: a lead drawn as one curve from a plug on the far side cut
     * through the combustor's case).
     */
    public static List<Vec3d> aroundAxis(Vec3d from, Vec3d to, double clearance, int segments) {
        double a0 = Math.atan2(from.z(), from.y()), a1 = Math.atan2(to.z(), to.y());
        double turn = Math.IEEEremainder(a1 - a0, 2 * Math.PI);
        double r0 = Math.hypot(from.y(), from.z()), r1 = Math.hypot(to.y(), to.z());
        List<Vec3d> out = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments, s = t * t * (3 - 2 * t), a = a0 + turn * s, r = r0 + (r1 - r0) * s;
            if (clearance > r) { r += (clearance - r) * Math.sin(Math.PI * t); }
            out.add(new Vec3d(from.x() + (to.x() - from.x()) * s, r * Math.cos(a), r * Math.sin(a)));
        }
        return out;
    }

    /** Points along a straight line, for a harness trunk or a pipe. */
    public static List<Vec3d> line(Vec3d from, Vec3d to, int segments) {
        List<Vec3d> out = new ArrayList<>();
        for (int i = 0; i <= segments; i++) { out.add(from.add(to.subtract(from).scale((double) i / segments))); }
        return out;
    }
}
