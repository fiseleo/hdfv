package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.frame.Frame;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.look.ChassisLayout;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign;
import dev.mcmod.hdfv.part.chassis.ChassisStationDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * A chassis's model (0.15), from its layout (ChassisLayout: the members the beam model is built from, so the model
 * and the structure are one): each member its section's plates along it (a channel's web and flanges open toward the
 * frame's middle, an I beam's, a box's or rectangular tube's four walls; a round tube), crossmembers and braces cut
 * back to the rails' faces; at each axle station the spring hangers under the rails, at each body mount a rubber
 * puck on a bracket outside them, at each engine mount a bracket with its rubber block inside them. The frame's
 * paint colour, chassis black by default; the page's members and fittings lit in the design screen.
 */
public final class ChassisModel {
    private ChassisModel() {}

    static final int PAINT = 0xff1f2226, BRACKET = 0xff34383d, RUBBER = 0xff141516;

    /** The design screen's model: the frame with a page's members lit ("frame": the rails; "stationN": that station's). */
    public static EngineModel build(ChassisDesign d, String highlight) { return EngineModel.of(mesh(d, highlight)); }

    /** The frame's mesh (in the world: highlight null). */
    public static PartMesh mesh(ChassisDesign d, String highlight) {
        PartMesh out = new PartMesh();
        ChassisSettingsDesign f = d.frame();
        ChassisLayout layout = d.analysis().layout();
        Frame frame = layout.frame;
        int paint = f.paintColor() == null || f.paintColor().isEmpty() ? PAINT : RecordEditor.argb(f.paintColor());
        boolean ladder = f.kind() == ChassisSettingsDesign.Kind.LADDER, tubes = f.kind() == ChassisSettingsDesign.Kind.SPACE_FRAME;
        for (int i = 0; i < frame.memberCount(); i++) {
            ChassisLayout.Member role = layout.members.get(i);
            Frame.Member m = frame.member(i);
            boolean rail = role.role() == ChassisLayout.Role.RAIL;
            String page = rail ? ChassisDesign.FRAME : d.listPage(role.station());
            int colour = page.equals(highlight) ? Shapes.HIGHLIGHT : paint;
            Vec3d a = frame.position(m.a()), b = frame.position(m.b());
            if (tubes) {
                double r = f.tubeDiameterM() / 2;
                out.cylinder(a, b, r, r, 10, colour);
                continue;
            }
            ChassisSettingsDesign.RailSection section;
            double h, w, t;
            if (rail) { section = f.railSection(); h = f.railHeightM(); w = f.railWidthM(); t = f.railWallM(); }
            else {
                ChassisStationDesign s = d.stations().get(role.station());
                section = s.crossSection(); h = s.crossHeightM(); w = s.crossWidthM(); t = s.crossWallM();
            }
            // crossmembers and braces end at the rails' faces (a backbone's outriggers at its tube's)
            double trimA = rail ? 0 : f.railWidthM() / 2, trimB = rail || !ladder ? 0 : f.railWidthM() / 2;
            // a ladder's channels open toward the middle
            int mirror = rail && a.z() > 0 ? -1 : 1;
            member(out, frame, i, trimA, trimB, section, h, w, t, mirror, colour);
        }
        // the fittings at the stations
        for (int i = 0; i < d.stations().size(); i++) {
            ChassisStationDesign s = d.stations().get(i);
            boolean lit = d.listPage(i).equals(highlight);
            int bracket = lit ? Shapes.HIGHLIGHT : BRACKET, rubber = lit ? Shapes.HIGHLIGHT : RUBBER;
            for (int side = 0; side < 2; side++) {
                Vec3d at = frame.position(layout.stationNodes[i][side]);
                double out1 = Math.signum(at.z()) == 0 ? (side == 0 ? -1 : 1) : Math.signum(at.z());
                double half = (tubes ? f.tubeDiameterM() : layout.railHeightM) / 2, width = tubes ? f.tubeDiameterM() : f.railWidthM();
                double bottom = at.y() - half, top = at.y() + half;
                switch (s.kind()) {
                    case AXLE -> {
                        if (tubes) { break; }
                        // spring hangers a little ahead of and behind the axle, a bump stop over it
                        for (double dx : new double[]{-0.3, 0.3}) {
                            out.box(new Vec3d(at.x() + dx - 0.025, bottom - 0.09, at.z() - width / 2), new Vec3d(at.x() + dx + 0.025, bottom, at.z() + width / 2), bracket);
                        }
                        out.cylinder(new Vec3d(at.x(), bottom, at.z()), new Vec3d(at.x(), bottom - 0.05, at.z()), 0.03, 0.022, 10, rubber);
                    }
                    case BODY_MOUNT -> {
                        // a shelf outside the rail and the rubber puck on it
                        Vec3d c = new Vec3d(at.x(), top - 0.03, at.z() + out1 * (width / 2 + 0.05));
                        out.box(c.add(new Vec3d(-0.04, -0.03, -0.05)), c.add(new Vec3d(0.04, 0.0, 0.05)), bracket);
                        out.cylinder(c, c.add(new Vec3d(0, 0.03, 0)), 0.035, 0.035, 12, rubber);
                    }
                    case ENGINE_MOUNT -> {
                        // a bracket inside the rail and its rubber block
                        Vec3d c = new Vec3d(at.x(), top, at.z() - out1 * (width / 2 + 0.04));
                        out.box(c.add(new Vec3d(-0.05, -0.08, -0.04)), c.add(new Vec3d(0.05, 0.0, 0.04)), bracket);
                        out.box(c.add(new Vec3d(-0.035, 0.0, -0.03)), c.add(new Vec3d(0.035, 0.05, 0.03)), rubber);
                    }
                    default -> { }
                }
            }
        }
        return out;
    }

    /** A member's plates: its section's walls (h, w, t) along it, cut back at each end. */
    private static void member(PartMesh out, Frame frame, int member, double trimA, double trimB, ChassisSettingsDesign.RailSection section, double h, double w,
                               double t, int mirror, int colour) {
        Frame.Member m = frame.member(member);
        Vec3d[] axes = frame.axes(member);
        Vec3d a = frame.position(m.a()), b = frame.position(m.b());
        double length = b.subtract(a).length() - trimA - trimB;
        if (length <= 1e-3) { return; }
        Vec3d start = a.add(axes[0].scale(trimA));
        if (section == ChassisSettingsDesign.RailSection.ROUND_TUBE) {
            out.cylinder(start, start.add(axes[0].scale(length)), h / 2, h / 2, 14, colour);
            return;
        }
        Orientation turn = orientation(axes[0], axes[1], axes[2]);
        // plates as (z0, z1, y0, y1) across the member
        List<double[]> plates = new ArrayList<>();
        double hy = h / 2, hz = w / 2;
        switch (section) {
            case CHANNEL -> {
                plates.add(new double[]{-hz, -hz + t, -hy, hy});
                plates.add(new double[]{-hz, hz, hy - t, hy});
                plates.add(new double[]{-hz, hz, -hy, -hy + t});
            }
            case I_BEAM -> {
                plates.add(new double[]{-t / 2, t / 2, -hy + t, hy - t});
                plates.add(new double[]{-hz, hz, hy - t, hy});
                plates.add(new double[]{-hz, hz, -hy, -hy + t});
            }
            default -> {
                plates.add(new double[]{-hz, hz, hy - t, hy});
                plates.add(new double[]{-hz, hz, -hy, -hy + t});
                plates.add(new double[]{-hz, -hz + t, -hy + t, hy - t});
                plates.add(new double[]{hz - t, hz, -hy + t, hy - t});
            }
        }
        for (double[] p : plates) {
            double z0 = mirror * p[0], z1 = mirror * p[1];
            double zc = 0.5 * (z0 + z1), yc = 0.5 * (p[2] + p[3]);
            Vec3d centre = start.add(axes[0].scale(length / 2)).add(axes[1].scale(yc)).add(axes[2].scale(zc));
            out.box(centre, new Vec3d(length / 2, 0.5 * (p[3] - p[2]), 0.5 * Math.abs(z1 - z0)), turn, colour);
        }
    }

    /** The rotation taking the x, y and z axes to three orthonormal ones (a right-handed set). */
    static Orientation orientation(Vec3d x, Vec3d y, Vec3d z) {
        double m00 = x.x(), m10 = x.y(), m20 = x.z(), m01 = y.x(), m11 = y.y(), m21 = y.z(), m02 = z.x(), m12 = z.y(), m22 = z.z();
        double trace = m00 + m11 + m22, qw, qx, qy, qz;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1) * 2;
            qw = 0.25 * s; qx = (m21 - m12) / s; qy = (m02 - m20) / s; qz = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1 + m00 - m11 - m22) * 2;
            qw = (m21 - m12) / s; qx = 0.25 * s; qy = (m01 + m10) / s; qz = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1 + m11 - m00 - m22) * 2;
            qw = (m02 - m20) / s; qx = (m01 + m10) / s; qy = 0.25 * s; qz = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1 + m22 - m00 - m11) * 2;
            qw = (m10 - m01) / s; qx = (m02 + m20) / s; qy = (m12 + m21) / s; qz = 0.25 * s;
        }
        double n = Math.sqrt(qw * qw + qx * qx + qy * qy + qz * qz);
        return new Orientation(qw / n, qx / n, qy / n, qz / n);
    }
}
