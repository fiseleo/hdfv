package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.wheel.Suspension;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.part.wheel.AxleDesign;
import dev.mcmod.hdfv.part.wheel.LandingGearDesign;
import dev.mcmod.hdfv.part.wheel.StrutDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * A wheel's corner as rigid pieces (0.12), baked once and placed every frame from the part's shown values, where 0.6
 * to 0.11 built the links again for every frame (no textures, no occlusion, no GPU cache). Placement only, shared by
 * the world and the design screen: each piece is built where it is at rest (an axle at its design compression, a leg
 * at its static one, straight ahead) and moves as the parts it joins do:
 * <ul>
 * <li>with the upright (the hub rising by the compression), and turned by the steering about the kingpin through the
 *     knuckle (the knuckle, the strut's lower half on a MacPherson, the wheel; the wheel rolls by its spin);</li>
 * <li>about a pivot on the body to follow a point that moves (a wishbone, a trailing arm, a swing half shaft, a spring
 *     leg's bar);</li>
 * <li>from one point to another, turned to aim and stretched along its length where it must (a tie rod, a half shaft,
 *     a drop link, a coil spring, a beam axle's half; a damper's body and its rod each keep their length and slide
 *     past each other);</li>
 * <li>a landing-gear leg's pieces turned by its steering about its strut, then all of it folded by its retraction
 *     about its mount (AeroCore's LegPart.Retraction).</li>
 * </ul>
 * The kinematics are the drawing's (the hub rises straight up; a solid axle's beam joins its two hubs), not AeroCore's
 * forces. Links and springs are nearest only (level 0); the knuckles, hubs and wheels farther too.
 * <p>
 * 0.15: an independent axle in the hardpoints mode is drawn from its hardpoints and moved by its kinematic table
 * (AeroCore's, the one its physics uses): the upright and wheel go where the table puts them (its hub path and
 * rotation, so the camber and toe change show), steered about the kingpin through its ball joints (a strut's lower
 * ball and top mount); an arm turns about its pivots to its ball joint, a link and the tie rod run from their body
 * points to their points on the upright, the spring from the body to its seat on the arm or the upright.
 */
public final class WheelRig {
    private WheelRig() {}

    /** What the shown values say now: per side (0 left, 1 right; a leg uses 0) compression, steering, spin; a leg's fraction folded. */
    public record State(double[] compression, double[] steer, double[] spin, double folded) {}

    /** Where a piece goes: x' = to + turn (stretched along y) from (x - at). */
    public record Pose(Vec3d to, Orientation turn, double stretch, Orientation from, Vec3d at) {
        static Pose rigid(Vec3d to, Orientation turn, Vec3d at) { return new Pose(to, turn, 1, Orientation.IDENTITY, at); }

        /** This pose, then turned about the origin (a leg's steering and retraction, about its mount). */
        Pose turned(Orientation r) { return new Pose(r.rotate(to), r.multiply(turn), stretch, from, at); }
    }

    /** A point of the corner as the state moves it. */
    interface Point { Vec3d at(State s); }

    /** A piece's motion. */
    interface Motion { Pose at(State s); }

    /** The rig: one mesh (the fixed part and every piece, at rest), and each piece's motion (piece n's at n - 1). */
    public record Built(PartMesh mesh, List<Motion> motions) {
        public Pose pose(int piece, State s) { return motions.get(piece - 1).at(s); }
    }

    private static final class Builder {
        final PartMesh mesh = new PartMesh();
        final List<Motion> motions = new ArrayList<>();
        final State rest;
        /** 0.15: every piece drawn at rest into one mesh, untagged (the design screen's still picture). */
        final boolean still;

        Builder(State rest) { this(rest, false); }

        Builder(State rest, boolean still) { this.rest = rest; this.still = still; }

        /** A new piece drawn by a function into the mesh at rest, moving as given; near only or every level. */
        void piece(java.util.function.Consumer<PartMesh> draw, Motion motion, boolean near) {
            if (still) { draw.accept(mesh); return; }
            int tags = mesh.tags(), from = mesh.quads().size(), id = motions.size() + 1;
            mesh.piece(id);
            if (near) { mesh.levels(0, 0); }
            draw.accept(mesh);
            mesh.pens(mesh.finish(), tags);
            // 0.22: quads added whole (a wheel's still and turning meshes: MeshBuilder.add keeps each quad's own tags, it
            // does not stamp the pens) take the piece too; they were on piece 0, baked fixed with the body, so in the
            // world a wheel neither turned, steered nor rode up (the drawn path; the unbaked one was right)
            int shift = Integer.numberOfTrailingZeros(dev.mcmod.aerocore.api.mesh.MeshBuilder.PIECE);
            for (int q = from; q < mesh.quads().size(); q++) {
                var k = mesh.quads().get(q);
                if (k.piece() != id) { mesh.quads().set(q, k.withTags((k.tags() & ~dev.mcmod.aerocore.api.mesh.MeshBuilder.PIECE) | (id << shift))); }
            }
            motions.add(motion);
        }

        /** A piece turned about a pivot on the body (an axis through it) to follow a moving point. */
        void arm(java.util.function.Consumer<PartMesh> draw, Vec3d pivot, Vec3d axis, Point end, boolean near) {
            Vec3d a = axis.normalized(), e0 = flat(end.at(rest).subtract(pivot), a);
            piece(draw, s -> {
                Vec3d e1 = flat(end.at(s).subtract(pivot), a);
                double angle = Math.atan2(e0.cross(e1).dot(a), e0.dot(e1));
                return Pose.rigid(pivot, Orientation.rotationVector(a.scale(angle)), pivot);
            }, near);
        }

        /** A piece from one point to another, turned to keep aiming from a at b, stretched to their distance or not. */
        void link(java.util.function.Consumer<PartMesh> draw, Point a, Point b, boolean stretch, boolean near) {
            Vec3d a0 = a.at(rest), d0 = b.at(rest).subtract(a0);
            double l0 = Math.max(d0.length(), 1e-6);
            Vec3d side = side(d0);
            Orientation r0 = Details.basis(d0, side);
            Orientation back = r0.inverse();
            piece(draw, s -> {
                Vec3d a1 = a.at(s), d1 = b.at(s).subtract(a1);
                double l1 = d1.length();
                if (l1 < 1e-6) { return Pose.rigid(a1, Orientation.IDENTITY, a0); }
                return new Pose(a1, Details.basis(d1, side), stretch ? l1 / l0 : 1, back, a0);
            }, near);
        }

        Built built() { return new Built(mesh, List.copyOf(motions)); }
    }

    private static Vec3d flat(Vec3d v, Vec3d axis) { return v.subtract(axis.scale(v.dot(axis))); }

    /** A reference square-ish to a direction, so a link keeps its roll as it turns (x unless it lies along x). */
    private static Vec3d side(Vec3d d) { return Math.abs(d.normalized().x()) < 0.9 ? Vec3d.X : Vec3d.Z; }

    // ---------------------------------------------------------------- axles

    /** An axle's rig: the fixed part (differential, rack, the anti-roll bar's middle), each side's links and wheel. */
    public static Built axle(AxleDesign d) {
        double c0 = d.suspension().designCompressionM();
        State rest = new State(new double[]{c0, c0}, new double[]{0, 0}, new double[]{0, 0}, 0);
        Builder b = new Builder(rest);
        b.mesh.add(WheelModel.fixed(d, null), Orientation.IDENTITY, Vec3d.ZERO);
        PartMesh stillR = WheelModel.still(d.tyre(), d.wheel(), d.brake(), null, false), turningR = WheelModel.turning(d.tyre(), d.wheel(), d.brake(), null, false);
        PartMesh stillL = WheelModel.mirroredZ(stillR), turningL = WheelModel.mirroredZ(turningR);
        for (int side : new int[]{-1, 1}) { corner(b, d, side, side < 0 ? stillL : stillR, side < 0 ? turningL : turningR); }
        return b.built();
    }

    private static int index(int side) { return side < 0 ? 0 : 1; }

    private static void corner(Builder b, AxleDesign d, int side, PartMesh still, PartMesh turning) {
        dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable table = table(d);
        if (table != null) { hardpointCorner(b, d, table, side, still, turning, null); return; }
        int i = index(side), o = 1 - i;
        double c0 = d.suspension().designCompressionM(), half = d.layout().trackM() / 2, y0 = -d.layout().hubDropM(), s = side;
        Vec3d hub0 = WheelModel.hub(d, side, c0);
        double rw = d.wheel().widthM(), zk = s * (half - 0.5 * rw + 0.03 - 0.06);
        Vec3d k0 = new Vec3d(0, hub0.y(), zk);
        Suspension.Type type = d.suspension().layout();
        // the moving points: on the upright (rising with this side's compression), steered about the kingpin through
        // the knuckle, or a share of this side's rise and the other's (along an arm, across a beam)
        java.util.function.Function<Vec3d, Point> up = p -> st -> p.add(new Vec3d(0, st.compression()[i] - c0, 0));
        java.util.function.Function<Vec3d, Point> steered = p -> st -> {
            double dy = st.compression()[i] - c0;
            Vec3d k = k0.add(new Vec3d(0, dy, 0));
            return k.add(Orientation.rotationVector(Vec3d.Y.scale(st.steer()[i])).rotate(p.add(new Vec3d(0, dy, 0)).subtract(k)));
        };
        java.util.function.BiFunction<Vec3d, double[], Point> shared = (p, w) -> st -> p.add(new Vec3d(0, w[0] * (st.compression()[i] - c0) + w[1] * (st.compression()[o] - c0), 0));
        Motion knuckle = st -> {
            double dy = st.compression()[i] - c0;
            Vec3d k = k0.add(new Vec3d(0, dy, 0));
            return Pose.rigid(k, Orientation.rotationVector(Vec3d.Y.scale(st.steer()[i])), k0);
        };
        // the wheel: what stays with the knuckle, and what turns (rolled by its spin about its axle, z)
        b.piece(m -> m.add(still, Orientation.IDENTITY, hub0), knuckle, false);
        b.piece(m -> m.add(turning, Orientation.IDENTITY, hub0), st -> {
            double dy = st.compression()[i] - c0;
            Vec3d k = k0.add(new Vec3d(0, dy, 0));
            Orientation steer = Orientation.rotationVector(Vec3d.Y.scale(st.steer()[i]));
            Vec3d hub = k.add(steer.rotate(hub0.add(new Vec3d(0, dy, 0)).subtract(k)));
            return Pose.rigid(hub, steer.multiply(Orientation.rotationVector(Vec3d.Z.scale(-st.spin()[i]))), hub0);
        }, false);
        if (!type.solid()) {
            b.piece(m -> m.box(new Vec3d(-0.04, hub0.y() - 0.13, zk - 0.03), new Vec3d(0.04, hub0.y() + 0.15, zk + 0.03), WheelModel.KNUCKLE), knuckle, false);
        }
        switch (type) {
            case MACPHERSON -> {
                Vec3d low = k0.add(new Vec3d(0, -0.12, 0)), base = k0.add(new Vec3d(-0.01, 0.12, -s * 0.02)), top = new Vec3d(-0.03, y0 + 0.62, s * (half - 0.24));
                Vec3d f = new Vec3d(0.18, y0 - 0.1, s * (half - 0.42)), r = new Vec3d(-0.2, y0 - 0.1, s * (half - 0.42));
                b.arm(m -> WheelModel.aArm(m, low, f, r, 0.016, WheelModel.ARM), f, r.subtract(f), up.apply(low), true);
                coilover(b, steered.apply(base), st -> top, 0.36, 0.04, 0.075);
            }
            case DOUBLE_WISHBONE, MULTI_LINK -> {
                Vec3d lowK = k0.add(new Vec3d(0, -0.12, 0)), upK = k0.add(new Vec3d(0, 0.14, 0));
                Vec3d lf = new Vec3d(0.2, y0 - 0.1, s * (half - 0.46)), lr = new Vec3d(-0.2, y0 - 0.1, s * (half - 0.46));
                Vec3d uf = new Vec3d(0.14, y0 + 0.2, s * (half - 0.38)), ur = new Vec3d(-0.14, y0 + 0.2, s * (half - 0.38));
                if (type == Suspension.Type.MULTI_LINK) {
                    for (Vec3d p : new Vec3d[]{lf, lr, uf, ur}) {
                        Vec3d joint = p.y() > y0 ? upK : lowK;
                        b.link(m -> m.cylinder(joint, p, 0.011, 0.011, 8, WheelModel.ARM), up.apply(joint), st -> p, true, true);
                    }
                    Vec3d toe = k0.add(new Vec3d(-0.12, -0.04, 0)), toeBody = new Vec3d(-0.24, y0 - 0.02, s * (half - 0.44));
                    b.link(m -> m.cylinder(toe, toeBody, 0.009, 0.009, 8, WheelModel.ARM), steered.apply(toe), st -> toeBody, true, true);
                } else {
                    b.arm(m -> WheelModel.aArm(m, lowK, lf, lr, 0.016, WheelModel.ARM), lf, lr.subtract(lf), up.apply(lowK), true);
                    b.arm(m -> WheelModel.aArm(m, upK, uf, ur, 0.013, WheelModel.ARM), uf, ur.subtract(uf), up.apply(upK), true);
                }
                Vec3d seat = lowK.add(lf.add(lr).scale(0.5).subtract(lowK).scale(0.35));
                coilover(b, shared.apply(seat, new double[]{0.65, 0}), st -> new Vec3d(0, y0 + 0.42, s * (half - 0.4)), 0.3, 0.035, 0.06);
            }
            case TRAILING_ARM, SEMI_TRAILING_ARM -> {
                Vec3d pivot = type == Suspension.Type.TRAILING_ARM ? new Vec3d(0.45, y0 + 0.04, s * (half - 0.2)) : new Vec3d(0.38, y0 + 0.04, s * (half - 0.48));
                Vec3d end = k0.add(new Vec3d(0, -0.02, 0));
                b.arm(m -> WheelModel.beam(m, end, pivot, 0.05, 0.035, WheelModel.ARM), pivot, end.subtract(pivot).cross(Vec3d.Y), up.apply(end), false);
                Vec3d seat = end.add(pivot.subtract(end).scale(0.25));
                coilover(b, shared.apply(seat, new double[]{0.75, 0}), st -> new Vec3d(seat.x() - 0.03, y0 + 0.38, seat.z()), 0.28, 0.035, 0.06);
            }
            case SWING_AXLE -> {
                Vec3d inner = new Vec3d(0, y0, s * 0.09);
                b.arm(m -> m.cylinder(inner, k0, 0.03, 0.035, 12, WheelModel.ARM), inner, Vec3d.X, up.apply(k0), false);
                coilover(b, up.apply(k0.add(new Vec3d(0, 0.06, -s * 0.05))), st -> new Vec3d(0, y0 + 0.42, s * (half - 0.2)), 0.28, 0.035, 0.065);
            }
            case TWIST_BEAM -> {
                Vec3d pivot = new Vec3d(0.45, y0 + 0.04, s * (half - 0.22)), end = k0.add(new Vec3d(0, -0.02, 0));
                b.arm(m -> WheelModel.beam(m, end, pivot, 0.05, 0.035, WheelModel.ARM), pivot, end.subtract(pivot).cross(Vec3d.Y), up.apply(end), false);
                Vec3d onArm = end.add(pivot.subtract(end).scale(0.3)), middle = new Vec3d(onArm.x(), onArm.y(), 0);
                b.link(m -> WheelModel.beam(m, onArm, middle, 0.045, 0.06, WheelModel.ARM), shared.apply(onArm, new double[]{0.7, 0}),
                        shared.apply(middle, new double[]{0.35, 0.35}), true, false);
                coilover(b, up.apply(end.add(new Vec3d(-0.02, 0.04, -s * 0.06))), st -> new Vec3d(-0.05, y0 + 0.36, s * (half - 0.3)), 0.26, 0.035, 0.06);
            }
            case SOLID_PANHARD, SOLID_WATT, SOLID_LEAF -> {
                Vec3d mine = hub0, centre = new Vec3d(0, hub0.y(), 0);
                boolean driven = d.differential().driven();
                b.link(m -> m.cylinder(mine.add(new Vec3d(0, 0, -s * 0.06)), centre, 0.04, 0.045, 14, driven ? WheelModel.HOUSING : WheelModel.ARM),
                        up.apply(mine.add(new Vec3d(0, 0, -s * 0.06))), shared.apply(centre, new double[]{0.5, 0.5}), true, false);
                if (driven && side > 0) {
                    b.piece(m -> m.lathe(centre.add(new Vec3d(-0.13, 0, 0)), centre.add(new Vec3d(0.15, 0, 0)), new double[]{0, 0, 0.2, 0.5, 0.8, 1, 1},
                                    new double[]{0, 0.12, 0.15, 0.16, 0.12, 0.06, 0}, null, 28, WheelModel.HOUSING, false),
                            st -> Pose.rigid(centre.add(new Vec3d(0, 0.5 * (st.compression()[0] + st.compression()[1]) - c0, 0)), Orientation.IDENTITY, centre), false);
                }
                double zs = s * (half - 0.32), share = (half - Math.abs(zs)) / half;
                Vec3d onAxle = new Vec3d(0, hub0.y(), zs);
                double[] w = {1 - 0.5 * share, 0.5 * share};
                if (type == Suspension.Type.SOLID_LEAF) {
                    b.piece(m -> WheelModel.leafPack(m, onAxle, y0, WheelModel.SPRING), st -> {
                        double dy = w[0] * (st.compression()[i] - c0) + w[1] * (st.compression()[o] - c0);
                        return Pose.rigid(onAxle.add(new Vec3d(0, dy, 0)), Orientation.IDENTITY, onAxle);
                    }, true);
                    Vec3d damperTop = new Vec3d(0.16, y0 + 0.3, zs * 0.95), low = onAxle.add(new Vec3d(0.12, 0.02, 0));
                    b.link(m -> m.cylinder(low, damperTop, 0.022, 0.022, 10, WheelModel.DAMPER), shared.apply(low, w), st -> damperTop, true, true);
                } else {
                    coilover(b, shared.apply(onAxle.add(new Vec3d(0, 0.04, 0)), w), st -> new Vec3d(0, y0 + 0.38, zs), 0.3, 0.04, 0.075);
                    Vec3d linkLow = onAxle.add(new Vec3d(0, -0.05, 0)), linkBody = new Vec3d(0.55, y0 - 0.02, zs);
                    b.link(m -> m.cylinder(linkLow, linkBody, 0.016, 0.016, 8, WheelModel.ARM), shared.apply(linkLow, w), st -> linkBody, true, true);
                    if (side > 0) {
                        if (type == Suspension.Type.SOLID_PANHARD) {
                            Vec3d onOther = new Vec3d(-0.08, onAxle.y() + 0.05, -zs), body = new Vec3d(-0.08, y0 + 0.1, zs + s * 0.05);
                            b.link(m -> m.cylinder(onOther, body, 0.015, 0.015, 8, WheelModel.ARM), shared.apply(onOther, new double[]{w[1], w[0]}), st -> body, true, true);
                        } else {
                            Vec3d pivot = new Vec3d(-0.15, hub0.y(), 0);
                            b.piece(m -> m.cylinder(pivot.add(new Vec3d(0, 0.08, 0)), pivot.add(new Vec3d(0, -0.08, 0)), 0.02, 0.02, 8, WheelModel.ARM),
                                    st -> Pose.rigid(pivot.add(new Vec3d(0, 0.5 * (st.compression()[0] + st.compression()[1]) - c0, 0)), Orientation.IDENTITY, pivot), true);
                            for (int k = -1; k <= 1; k += 2) {
                                Vec3d end = pivot.add(new Vec3d(0, 0.08 * k, 0)), body = new Vec3d(-0.15, y0 + 0.08 * k, k > 0 ? -zs : zs);
                                b.link(m -> m.cylinder(end, body, 0.013, 0.013, 8, WheelModel.ARM), shared.apply(end, new double[]{0.5, 0.5}), st -> body, true, true);
                            }
                        }
                    }
                }
            }
        }
        // the half shaft from the differential to the hub, its boots stretching with it
        if (d.differential().driven() && !type.solid()) {
            Vec3d inner = new Vec3d(0, y0, s * (d.differential().transverse() ? 0.1 : 0.14)), outer = new Vec3d(0, hub0.y(), zk - s * 0.02);
            b.link(m -> {
                m.cylinder(inner, outer, 0.014, 0.014, 10, WheelModel.SHAFT);
                Vec3d dir = outer.subtract(inner).normalized();
                m.cylinder(inner, inner.add(dir.scale(0.1)), 0.045, 0.02, 12, WheelModel.BOOT);
                m.cylinder(outer, outer.subtract(dir.scale(0.1)), 0.045, 0.02, 12, WheelModel.BOOT);
            }, st -> inner, up.apply(outer), true, true);
        }
        // the tie rod from the rack to the steering arm, which turns with the wheel
        if (d.layout().steered()) {
            double end = Math.max(half - 0.42, 0.2) + 0.09;
            Vec3d rack = new Vec3d(-0.14, y0 + 0.05, s * end), arm = k0.add(new Vec3d(-0.14, 0.02, -s * 0.03));
            b.link(m -> m.cylinder(rack, arm, 0.011, 0.011, 8, WheelModel.ROD), st -> rack, steered.apply(arm), true, true);
        }
        // the anti-roll bar's arm (on the body) and its drop link to the upright
        if (d.layout().antiRollNPerM() > 0) {
            double x = type.solid() ? -0.25 : 0.18, inner = Math.max(half - 0.45, 0.2);
            Vec3d bend = new Vec3d(x, y0 - 0.06, s * inner), tip = new Vec3d(x - 0.2 * Math.signum(x + 1e-9), y0 - 0.06, s * inner);
            b.mesh.cylinder(bend, tip, 0.012, 0.012, 8, WheelModel.BAR);
            Vec3d low = new Vec3d(tip.x(), hub0.y() - 0.08, s * (inner + 0.03));
            b.link(m -> m.cylinder(tip, low, 0.008, 0.008, 8, WheelModel.ROD), st -> tip, up.apply(low), true, true);
        }
    }

    // ---------------------------------------------------------------- 0.15: hardpoints

    /** The table an axle is drawn from its hardpoints with (null: the simple drawing, also a solid axle's and one whose links bind). */
    static dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable table(AxleDesign d) {
        if (d.hardpoints() == null || d.hardpoints().mode() != dev.mcmod.hdfv.part.wheel.HardpointsDesign.Mode.HARDPOINTS || d.suspension().layout().solid()) { return null; }
        try { return d.kinematics(); }
        catch (RuntimeException e) { return null; }
    }

    /** 0.15: an axle's links on one side at the design compression, drawn from its hardpoints (the design screen); null in the simple mode. */
    public static PartMesh hardpointLinks(AxleDesign d, int side, String highlight) {
        var table = table(d);
        if (table == null) { return null; }
        double c0 = d.suspension().designCompressionM();
        Builder b = new Builder(new State(new double[]{c0, c0}, new double[]{0, 0}, new double[]{0, 0}, 0), true);
        hardpointCorner(b, d, table, side, null, null, highlight);
        return b.mesh;
    }

    private static Vec3d mirror(Vec3d v) { return new Vec3d(v.x(), v.y(), -v.z()); }

    /** A rotation seen in the mirror z to -z. */
    private static Orientation mirror(Orientation q) { return new Orientation(q.w(), -q.x(), -q.y(), q.z()); }

    /**
     * One side's corner from its hardpoints (given for the right-hand wheel), the wheel's pieces (none for the design
     * screen's links) moved by the table, steered about the kingpin when the axle steers.
     */
    private static void hardpointCorner(Builder b, AxleDesign d, dev.mcmod.aerocore.api.wheel.kinematics.KinematicTable t, int side, PartMesh still, PartMesh turning,
                                        String highlight) {
        int i = index(side);
        double c0 = d.suspension().designCompressionM(), y0 = -d.layout().hubDropM(), s = side;
        var hp = t.hardpoints();
        var rest = t.at(c0);
        boolean steered = d.layout().steered();
        int arm = "hardpoints".equals(highlight) ? Shapes.HIGHLIGHT : WheelModel.ARM, knuckleColour = "hardpoints".equals(highlight) ? Shapes.HIGHLIGHT : WheelModel.KNUCKLE;
        java.util.function.Function<Vec3d, Vec3d> at = p -> side < 0 ? mirror(p) : p;
        // the kingpin: through the ball joints (a strut's lower ball and top), else upright through the hub's inner face
        Vec3d kLow, kHigh;
        switch (hp) {
            case dev.mcmod.aerocore.api.wheel.kinematics.Hardpoints.DoubleWishbone w -> { kLow = w.lowerBall(); kHigh = w.upperBall(); }
            case dev.mcmod.aerocore.api.wheel.kinematics.Hardpoints.MacPherson m -> { kLow = m.lowerBall(); kHigh = m.strutTop(); }
            default -> { kLow = hp.hub().add(new Vec3d(0, -0.1, -0.08)); kHigh = kLow.add(Vec3d.Y); }
        }
        Vec3d k0 = at.apply(kLow), hub0 = at.apply(rest.hub());
        // the upright's motion at a state: to the table's pose, then steered about the kingpin through its lower point
        record Upright(Vec3d pivot, Orientation turn) {}
        java.util.function.Function<State, Upright> upright = st -> {
            var now = t.at(st.compression()[i]);
            Orientation rel = now.upright().multiply(rest.upright().inverse());
            Vec3d kRight = now.hub().add(rel.rotate(kLow.subtract(rest.hub())));
            Orientation r = side < 0 ? mirror(rel) : rel;
            Vec3d pivot = at.apply(kRight);
            if (!steered || st.steer()[i] == 0) { return new Upright(pivot, r); }
            Vec3d axis = at.apply(kHigh.subtract(kLow));
            axis = r.rotate(axis).normalized();
            if (axis.y() < 0) { axis = axis.scale(-1); }
            return new Upright(pivot, Orientation.rotationVector(axis.scale(st.steer()[i])).multiply(r));
        };
        // a point fixed to the upright (given for the right-hand wheel) as the state moves it
        java.util.function.Function<Vec3d, Point> on = p -> {
            Vec3d p0 = at.apply(p);
            return st -> { Upright u = upright.apply(st); return u.pivot().add(u.turn().rotate(p0.subtract(k0))); };
        };
        Motion uprightPose = st -> { Upright u = upright.apply(st); return Pose.rigid(u.pivot(), u.turn(), k0); };
        if (still != null) {
            b.piece(m -> m.add(still, Orientation.IDENTITY, hub0), uprightPose, false);
            b.piece(m -> m.add(turning, Orientation.IDENTITY, hub0), st -> {
                Upright u = upright.apply(st);
                Vec3d hub = u.pivot().add(u.turn().rotate(hub0.subtract(k0)));
                return Pose.rigid(hub, u.turn().multiply(Orientation.rotationVector(Vec3d.Z.scale(-st.spin()[i]))), hub0);
            }, false);
        }
        // an arm turning about its pivots to follow its ball joint; a point on it (a spring's seat) goes with it
        record Arm(Vec3d front, Vec3d rear, Vec3d ball) {
            Point point(Vec3d p, Point ball, State rest) {
                Vec3d a = rear.subtract(front).normalized(), e0 = flat(ball.at(rest).subtract(front), a);
                return st -> {
                    Vec3d e1 = flat(ball.at(st).subtract(front), a);
                    double angle = Math.atan2(e0.cross(e1).dot(a), e0.dot(e1));
                    return front.add(Orientation.rotationVector(a.scale(angle)).rotate(p.subtract(front)));
                };
            }
        }
        java.util.function.Function<Arm, Point> armOf = x -> {
            Point ball = on.apply(x.ball());
            Vec3d f = at.apply(x.front()), r = at.apply(x.rear()), j = at.apply(x.ball());
            b.arm(m -> WheelModel.aArm(m, j, f, r, 0.016, arm), f, r.subtract(f), ball, true);
            return ball;
        };
        switch (hp) {
            case dev.mcmod.aerocore.api.wheel.kinematics.Hardpoints.DoubleWishbone w -> {
                Point lower = armOf.apply(new Arm(w.lowerFront(), w.lowerRear(), w.lowerBall()));
                armOf.apply(new Arm(w.upperFront(), w.upperRear(), w.upperBall()));
                b.piece(m -> m.cylinder(at.apply(w.lowerBall()), at.apply(w.upperBall()), 0.03, 0.025, 10, knuckleColour), uprightPose, false);
                tieRod(b, at.apply(w.tieInner()), on.apply(w.tieOuter()), steered);
                Arm la = new Arm(at.apply(w.lowerFront()), at.apply(w.lowerRear()), at.apply(w.lowerBall()));
                Vec3d seat = at.apply(w.springArm()), top = at.apply(w.springBody());
                coilover(b, la.point(seat, lower, b.rest), st -> top, 0.3, 0.035, 0.06);
            }
            case dev.mcmod.aerocore.api.wheel.kinematics.Hardpoints.MacPherson m -> {
                armOf.apply(new Arm(m.lowerFront(), m.lowerRear(), m.lowerBall()));
                b.piece(x -> x.cylinder(at.apply(m.lowerBall()), at.apply(m.strutLower()), 0.03, 0.03, 10, knuckleColour), uprightPose, false);
                tieRod(b, at.apply(m.tieInner()), on.apply(m.tieOuter()), steered);
                Vec3d top = at.apply(m.strutTop());
                coilover(b, on.apply(m.strutLower()), st -> top, 0.36, 0.04, 0.075);
            }
            case dev.mcmod.aerocore.api.wheel.kinematics.Hardpoints.MultiLink m -> {
                Vec3d centre = at.apply(m.hub().add(new Vec3d(0, 0, -0.08)));
                for (var link : m.links()) {
                    Vec3d body = at.apply(link.body()), end = at.apply(link.upright());
                    b.link(x -> x.cylinder(body, end, 0.011, 0.011, 8, arm), st -> body, on.apply(link.upright()), true, true);
                    b.piece(x -> x.cylinder(centre, end, 0.022, 0.018, 8, knuckleColour), uprightPose, false);
                }
                Vec3d top = at.apply(m.springBody());
                coilover(b, on.apply(m.springUpright()), st -> top, 0.3, 0.035, 0.06);
            }
            case dev.mcmod.aerocore.api.wheel.kinematics.Hardpoints.TrailingArm a -> {
                Vec3d pa = at.apply(a.pivotA()), pb = at.apply(a.pivotB()), end = at.apply(a.hub().add(new Vec3d(0, -0.02, -0.08)));
                Point endNow = on.apply(a.hub().add(new Vec3d(0, -0.02, -0.08)));
                b.arm(m -> { WheelModel.beam(m, end, pa, 0.05, 0.035, arm); WheelModel.beam(m, end, pb, 0.045, 0.03, arm); }, pa, pb.subtract(pa), endNow, false);
                Vec3d top = at.apply(a.springBody());
                coilover(b, on.apply(a.springArm()), st -> top, 0.28, 0.035, 0.06);
            }
            default -> { }
        }
        if (still == null) { return; }
        // the half shaft from the differential to the hub
        if (d.differential().driven()) {
            Vec3d inner = new Vec3d(0, y0, s * (d.differential().transverse() ? 0.1 : 0.14));
            Vec3d outerRight = rest.hub().add(new Vec3d(0, 0, -0.1)), outer = at.apply(outerRight);
            b.link(m -> {
                m.cylinder(inner, outer, 0.014, 0.014, 10, WheelModel.SHAFT);
                Vec3d dir = outer.subtract(inner).normalized();
                m.cylinder(inner, inner.add(dir.scale(0.1)), 0.045, 0.02, 12, WheelModel.BOOT);
                m.cylinder(outer, outer.subtract(dir.scale(0.1)), 0.045, 0.02, 12, WheelModel.BOOT);
            }, st -> inner, on.apply(outerRight), true, true);
        }
        // the anti-roll bar's arm and its drop link to the upright
        if (d.layout().antiRollNPerM() > 0) {
            double half = d.layout().trackM() / 2, inner = Math.max(half - 0.45, 0.2);
            Vec3d bend = new Vec3d(0.18, y0 - 0.06, s * inner), tip = new Vec3d(-0.02, y0 - 0.06, s * inner);
            b.mesh.cylinder(bend, tip, 0.012, 0.012, 8, WheelModel.BAR);
            Vec3d lowRight = rest.hub().add(new Vec3d(-0.02, -0.08, -0.12)), low = at.apply(lowRight);
            b.link(m -> m.cylinder(tip, low, 0.008, 0.008, 8, WheelModel.ROD), st -> tip, on.apply(lowRight), true, true);
        }
    }

    /** A tie rod from the rack (on the body) to the steering arm on the upright; none on an axle that does not steer. */
    private static void tieRod(Builder b, Vec3d rack, Point arm, boolean steered) {
        if (!steered) { return; }
        Vec3d a0 = arm.at(b.rest);
        b.link(m -> m.cylinder(rack, a0, 0.011, 0.011, 8, WheelModel.ROD), st -> rack, arm, true, true);
    }

    /**
     * A damper from its lower eye up to the body with a coil spring round its upper part (as WheelModel.coilover), as
     * three pieces: the body and its seat aiming from the eye at the top, the rod and its seat aiming back from the
     * top, the spring stretched between them.
     */
    private static void coilover(Builder b, Point low, Point top, double bodyShare, double bodyR, double coilR) {
        Vec3d l0 = low.at(b.rest), t0 = top.at(b.rest), axis = t0.subtract(l0);
        double len = axis.length();
        if (len < 0.05) { return; }
        Vec3d dir = axis.scale(1 / len);
        Vec3d bodyTop = l0.add(dir.scale(Math.max(len * bodyShare, Math.min(0.2, 0.7 * len))));
        Vec3d seat0 = l0.add(dir.scale(0.3 * len)), seat1 = t0.subtract(dir.scale(0.05 * len));
        b.link(m -> {
            m.cylinder(l0, bodyTop, bodyR, bodyR, 14, WheelModel.DAMPER);
            m.cylinder(seat0, seat0.add(dir.scale(0.008)), coilR * 1.15, coilR * 1.15, 18, WheelModel.DAMPER);
        }, low, top, false, true);
        b.link(m -> {
            m.cylinder(bodyTop.subtract(dir.scale(0.08)), t0, 0.45 * bodyR, 0.45 * bodyR, 10, WheelModel.ROD);
            m.cylinder(seat1.subtract(dir.scale(0.008)), seat1, coilR * 1.15, coilR * 1.15, 18, WheelModel.DAMPER);
        }, top, low, false, true);
        double coilLen = seat1.subtract(seat0).length();
        if (coilLen > 0.03) {
            Point s0 = st -> { Vec3d a = low.at(st), t = top.at(st); return a.add(t.subtract(a).scale(0.3)); };
            Point s1 = st -> { Vec3d a = low.at(st), t = top.at(st); return t.subtract(t.subtract(a).scale(0.05)); };
            b.link(m -> m.tube(PartMesh.helix(seat0, dir, coilR, coilLen, 6, 14), 0.0075, 6, WheelModel.SPRING), s0, s1, true, true);
        }
    }

    // ---------------------------------------------------------------- landing gear

    /**
     * A leg's rig: an oleo's cylinder (with its steering collar), its piston sliding with the compression, the torque
     * links folding between them, the fork or the axle's bar and stub; a spring leg's bar turning about its root to
     * the hub; a rigid fork; the wheel. Everything turned by the steering about the strut and folded by the retraction
     * about the mount.
     */
    public static Built leg(LandingGearDesign d) {
        StrutDesign s = d.strut();
        double c0 = Double.isFinite(s.staticCompressionM()) ? s.staticCompressionM() : 0;
        State rest = new State(new double[]{c0, c0}, new double[]{0, 0}, new double[]{0, 0}, 0);
        Builder b = new Builder(rest);
        // piece 1 is what is fixed to the strut: a steered or retracting leg moves all of itself
        b.motions.add(st -> Pose.rigid(Vec3d.ZERO, Orientation.IDENTITY, Vec3d.ZERO));
        b.mesh.piece(1);
        double len = d.leg().lengthM(), r = Shapes.positive(d.tyre().tyre().radiusM(), 0.2), w = d.tyre().sectionWidthM();
        int side = d.leg().side().sign, inboard = side > 0 ? -1 : 1;
        Vec3d hub0 = new Vec3d(0, -len + c0, 0);
        double zc = side == 0 ? 0 : inboard * (0.5 * w + 0.05);
        Point down = st -> new Vec3d(0, st.compression()[0] - c0, 0);
        Motion moving = st -> Pose.rigid(down.at(st), Orientation.IDENTITY, Vec3d.ZERO);
        switch (s.kind()) {
            case OLEO -> {
                double pr = Math.max(0.5 * s.pistonDiameterM(), 0.012), cr = pr + 0.012, bottom = -(len - r - 0.04 - s.strokeM());
                double crown0 = hub0.y() + r + 0.04;
                b.mesh.cylinder(new Vec3d(0, 0.06, zc), new Vec3d(0, bottom, zc), cr, cr, 20, WheelModel.OLEO);
                b.mesh.cylinder(new Vec3d(0, bottom - 0.004, zc), new Vec3d(0, bottom - 0.02, zc), cr + 0.006, cr + 0.006, 20, WheelModel.DAMPER);
                if (d.leg().steer() != dev.mcmod.aerocore.api.vehicle.part.LegPart.Steer.FIXED) {
                    b.mesh.cylinder(new Vec3d(0, -0.08, zc), new Vec3d(0, -0.12, zc), cr + 0.015, cr + 0.015, 16, WheelModel.DAMPER);
                }
                // the piston from the crown up into the cylinder (its hidden length takes the stroke)
                b.piece(m -> m.cylinder(new Vec3d(0, crown0, zc), new Vec3d(0, bottom + s.strokeM() * 0.9, zc), pr, pr, 18, WheelModel.CHROME), moving, false);
                // the torque links: a scissor from the cylinder's foot to the fork's crown, folding as it strokes
                Vec3d a = new Vec3d(cr, bottom - 0.01, zc);
                double link = 0.55 * Math.max(s.strokeM(), 0.08) + 0.02;
                Point crownPoint = st -> new Vec3d(cr, crown0 + 0.02 + st.compression()[0] - c0, zc);
                Point mid = st -> {
                    Vec3d bb = crownPoint.at(st);
                    double gap = Math.max(bb.y() - a.y(), 0);
                    return new Vec3d(cr + Math.sqrt(Math.max(link * link - 0.25 * gap * gap, 0)), 0.5 * (a.y() + bb.y()), zc);
                };
                Vec3d mid0 = mid.at(rest), b0 = crownPoint.at(rest);
                b.link(m -> m.cylinder(a, mid0, 0.008, 0.008, 6, WheelModel.FORK), st -> a, mid, false, true);
                b.link(m -> m.cylinder(mid0, b0, 0.008, 0.008, 6, WheelModel.FORK), mid, crownPoint, false, true);
                if (side == 0) { b.piece(m -> WheelModel.fork(m, hub0, crown0, w, 0, false), moving, false); }
                else {
                    b.piece(m -> {
                        WheelModel.bar(m, new Vec3d(0, crown0, zc), new Vec3d(0, hub0.y(), zc), 0.05, 0.04, 0.02, 0.018, WheelModel.FORK);
                        m.cylinder(new Vec3d(0, hub0.y(), zc), new Vec3d(0, hub0.y(), 0), 0.018, 0.018, 10, WheelModel.CHROME);
                    }, moving, false);
                }
            }
            case SPRING_STEEL -> {
                Vec3d root = new Vec3d(0, 0.04, inboard * 0.35 * len), end = new Vec3d(0, hub0.y() + 0.04, inboard * (0.5 * w + 0.04));
                Vec3d c1 = root.add(new Vec3d(0, -0.35 * len, 0)), c2 = end.add(new Vec3d(0, 0.25 * len, inboard * 0.1 * len));
                List<Vec3d> path = PartMesh.bezier(root, c1, c2, end, 12);
                b.arm(m -> {
                    for (int k = 0; k + 1 < path.size(); k++) {
                        double f0 = (double) k / (path.size() - 1), f1 = (double) (k + 1) / (path.size() - 1);
                        WheelModel.bar(m, path.get(k), path.get(k + 1), 0.07 - 0.03 * f0, 0.07 - 0.03 * f1, 0.026 - 0.01 * f0, 0.026 - 0.01 * f1, WheelModel.LEG_PAINT);
                    }
                    m.cylinder(end, new Vec3d(0, hub0.y(), 0), 0.018, 0.018, 10, WheelModel.FORK);
                }, root, Vec3d.X, st -> end.add(down.at(st)), false);
            }
            case RIGID -> {
                double crown = hub0.y() + r + 0.04;
                b.mesh.cylinder(new Vec3d(0, 0.04, 0), new Vec3d(0, crown, 0), 0.025, 0.025, 14, WheelModel.FORK);
                b.piece(m -> WheelModel.fork(m, hub0, crown, w, 0, false), moving, false);
            }
        }
        PartMesh still = WheelModel.still(d.tyre(), d.wheel(), d.brake(), null, true), turning = WheelModel.turning(d.tyre(), d.wheel(), d.brake(), null, true);
        if (d.leg().side().sign < 0) { still = WheelModel.mirroredZ(still); turning = WheelModel.mirroredZ(turning); }
        PartMesh stillMesh = still, turningMesh = turning;
        b.piece(m -> m.add(stillMesh, Orientation.IDENTITY, hub0), moving, false);
        b.piece(m -> m.add(turningMesh, Orientation.IDENTITY, hub0), st -> Pose.rigid(hub0.add(down.at(st)), Orientation.rotationVector(Vec3d.Z.scale(-st.spin()[0])), hub0), false);
        // every piece turned with the steering about the strut, then folded with the retraction about the mount
        var retraction = d.retraction();
        List<Motion> turned = new ArrayList<>();
        for (Motion m : b.motions) {
            turned.add(st -> {
                Orientation steer = Orientation.rotationVector(Vec3d.Y.scale(st.steer()[0]));
                Orientation fold = retraction == null ? Orientation.IDENTITY : Orientation.rotationVector(retraction.axis().normalized().scale(retraction.angleRad() * st.folded()));
                return m.at(st).turned(fold.multiply(steer));
            });
        }
        return new Built(b.mesh, List.copyOf(turned));
    }
}
