package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.frame.Frame;
import dev.mcmod.aerocore.api.frame.FrameAnalysis;
import dev.mcmod.aerocore.api.frame.FrameMaterial;
import dev.mcmod.aerocore.api.frame.Section;
import dev.mcmod.hdfv.part.chassis.ChassisDesign;
import dev.mcmod.hdfv.part.chassis.ChassisSettingsDesign;
import dev.mcmod.hdfv.part.chassis.ChassisStationDesign;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * A chassis's members (0.15): the one model its structure (the beam FEM), its 3D model, its mass and its machining are
 * all read from. In the part's frame: x from the frame's rear end, y up from the rails' bottom, z starboard.
 * <ul>
 * <li>Ladder: two rails at the spacing (from the rear's to the front's along the length), their centres half their
 *     height up, raised kickUpM behind kickUpX (rising over the 0.3 m ahead of it); a crossmember at each station; a
 *     diagonal (one, or an X) from a station to the next forward where it is braced.</li>
 * <li>Backbone: one tube of the rails' section on the centreline, an outrigger each side at each station to the
 *     spacing, where the wheels' links and the body bolt on.</li>
 * <li>Tube frame: a ring of tubes at each station (floor tube, two posts, a top tube unless open on top), four tubes
 *     along from ring to ring (two floor rails, two top rails) and the bays braced as their stations say (each side,
 *     the floor, and the top unless open).</li>
 * </ul>
 * The stations' nodes are where they are measured and carried: a ladder's rails, a backbone's outriggers' ends, a tube
 * frame's floor rails.
 */
public final class ChassisLayout {
    /** What a member is: a rail (a backbone's tube, a tube frame's tube along), a crossmember or ring, a brace. */
    public enum Role { RAIL, CROSS, BRACE }

    /** A member's role and the station it belongs to (design index; -1 for a rail). */
    public record Member(Role role, int station) {}

    /** How far the kick-up's ramp runs ahead of kickUpX, m. */
    public static final double KICK_RAMP_M = 0.3;
    /** 0.18: how far a gooseneck's step runs ahead of neckX, m. */
    public static final double NECK_RAMP_M = 0.4;

    public final ChassisDesign design;
    public final Frame frame;
    /** Each member's role, by the frame's member index. */
    public final List<Member> members;
    /** Each station's two nodes (left, right; by design index). */
    public final int[][] stationNodes;
    /** A backbone station's centre node (by design index; -1 for other kinds). */
    public final int[] centreNodes;
    /** The rails' height (m: a ladder's or backbone's section; a tube frame's tube). */
    public final double railHeightM;

    private ChassisLayout(ChassisDesign design, Frame frame, List<Member> members, int[][] stationNodes, int[] centreNodes, double railHeightM) {
        this.design = design; this.frame = frame; this.members = List.copyOf(members); this.stationNodes = stationNodes; this.centreNodes = centreNodes;
        this.railHeightM = railHeightM;
    }

    /** The frame's material for the beam model (G = E / 2.6: nu 0.3). */
    public static FrameMaterial material(ChassisSettingsDesign f) {
        var m = f.material();
        return new FrameMaterial(m.densityKgPerM3(), m.modulusPa(), m.modulusPa() / 2.6, m.yieldPa());
    }

    /** How far a ladder's rails are raised at x by the kick-up (0.18: and the gooseneck), m. */
    public static double lift(ChassisSettingsDesign f, double x) {
        if (f.kind() != ChassisSettingsDesign.Kind.LADDER) { return 0; }
        double kick = 0;
        if (f.kickUpM() > 0) {
            if (x <= f.kickUpX() - KICK_RAMP_M) { kick = f.kickUpM(); }
            else if (x < f.kickUpX()) { kick = f.kickUpM() * (f.kickUpX() - x) / KICK_RAMP_M; }
        }
        if (f.neckM() > 0) {
            if (x >= f.neckX() + NECK_RAMP_M) { kick += f.neckM(); }
            else if (x > f.neckX()) { kick += f.neckM() * (x - f.neckX()) / NECK_RAMP_M; }
        }
        return kick;
    }

    /** Station indices by x (rear first). */
    public static List<Integer> byX(ChassisDesign d) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < d.stations().size(); i++) { order.add(i); }
        order.sort(Comparator.comparingDouble(i -> d.stations().get(i).xM()));
        return order;
    }

    public static ChassisLayout of(ChassisDesign d) {
        ChassisSettingsDesign f = d.frame();
        FrameMaterial mat = material(f);
        Frame frame = new Frame();
        List<Member> roles = new ArrayList<>();
        int n = d.stations().size();
        int[][] at = new int[n][2];
        int[] centre = new int[n];
        java.util.Arrays.fill(centre, -1);
        List<Integer> order = byX(d);
        double joint = f.joint().factor;
        switch (f.kind()) {
            case LADDER, BACKBONE -> {
                Section rails = f.rails();
                double h = f.railHeightM();
                boolean ladder = f.kind() == ChassisSettingsDesign.Kind.LADDER;
                TreeSet<Double> xs = new TreeSet<>(List.of(0.0, f.lengthM()));
                for (ChassisStationDesign s : d.stations()) { xs.add(Math.clamp(s.xM(), 0, f.lengthM())); }
                if (ladder && f.kickUpM() > 0) {
                    for (double k : new double[]{f.kickUpX() - KICK_RAMP_M, f.kickUpX()}) { if (k > 0 && k < f.lengthM()) { xs.add(k); } }
                }
                if (ladder && f.neckM() > 0) {
                    for (double k : new double[]{f.neckX(), f.neckX() + NECK_RAMP_M}) { if (k > 0 && k < f.lengthM()) { xs.add(k); } }
                }
                List<Double> list = new ArrayList<>(xs);
                int[] left = new int[list.size()], right = new int[list.size()], middle = new int[list.size()];
                for (int k = 0; k < list.size(); k++) {
                    double x = list.get(k), y = h / 2 + lift(f, x), half = f.spacingAt(x) / 2;
                    if (ladder) {
                        left[k] = frame.node(new Vec3d(x, y, -half));
                        right[k] = frame.node(new Vec3d(x, y, half));
                    } else {
                        middle[k] = frame.node(new Vec3d(x, y, 0));
                    }
                }
                for (int k = 0; k + 1 < list.size(); k++) {
                    if (ladder) {
                        frame.member(left[k], left[k + 1], rails, mat, Vec3d.Y, joint); roles.add(new Member(Role.RAIL, -1));
                        frame.member(right[k], right[k + 1], rails, mat, Vec3d.Y, joint); roles.add(new Member(Role.RAIL, -1));
                    } else {
                        frame.member(middle[k], middle[k + 1], rails, mat, Vec3d.Y, joint); roles.add(new Member(Role.RAIL, -1));
                    }
                }
                for (int i = 0; i < n; i++) {
                    ChassisStationDesign s = d.stations().get(i);
                    int k = list.indexOf(Math.clamp(s.xM(), 0, f.lengthM()));
                    if (ladder) {
                        at[i] = new int[]{left[k], right[k]};
                        frame.member(left[k], right[k], s.cross(), mat, Vec3d.Y, joint); roles.add(new Member(Role.CROSS, i));
                    } else {
                        double x = list.get(k), y = h / 2, half = f.spacingAt(x) / 2;
                        int l = frame.node(new Vec3d(x, y, -half)), r = frame.node(new Vec3d(x, y, half));
                        frame.member(middle[k], l, s.cross(), mat, Vec3d.Y, joint); roles.add(new Member(Role.CROSS, i));
                        frame.member(middle[k], r, s.cross(), mat, Vec3d.Y, joint); roles.add(new Member(Role.CROSS, i));
                        at[i] = new int[]{l, r};
                        centre[i] = middle[k];
                    }
                }
                if (ladder) {
                    for (int o = 0; o + 1 < order.size(); o++) {
                        int i = order.get(o), j = order.get(o + 1);
                        ChassisStationDesign s = d.stations().get(i);
                        if (s.brace() == ChassisStationDesign.Brace.NONE || at[i][0] == at[j][0]) { continue; }
                        frame.member(at[i][0], at[j][1], s.cross(), mat, Vec3d.Y, joint); roles.add(new Member(Role.BRACE, i));
                        if (s.brace() == ChassisStationDesign.Brace.X) { frame.member(at[i][1], at[j][0], s.cross(), mat, Vec3d.Y, joint); roles.add(new Member(Role.BRACE, i)); }
                    }
                }
                return new ChassisLayout(d, frame, roles, at, centre, h);
            }
            default -> {
                Section tube = f.tubes();
                int[][] ring = new int[n][4];
                for (int i = 0; i < n; i++) {
                    ChassisStationDesign s = d.stations().get(i);
                    double x = Math.clamp(s.xM(), 0, f.lengthM()), half = f.spacingAt(x) / 2, top = s.bayTopWidthM() / 2, r = f.tubeDiameterM() / 2;
                    ring[i][0] = frame.node(new Vec3d(x, r, -half));
                    ring[i][1] = frame.node(new Vec3d(x, r, half));
                    ring[i][2] = frame.node(new Vec3d(x, r + s.bayHeightM(), -top));
                    ring[i][3] = frame.node(new Vec3d(x, r + s.bayHeightM(), top));
                    frame.member(ring[i][0], ring[i][1], tube, mat, Vec3d.Y, joint); roles.add(new Member(Role.CROSS, i));
                    frame.member(ring[i][0], ring[i][2], tube, mat, Vec3d.X, joint); roles.add(new Member(Role.CROSS, i));
                    frame.member(ring[i][1], ring[i][3], tube, mat, Vec3d.X, joint); roles.add(new Member(Role.CROSS, i));
                    if (!s.openTop()) { frame.member(ring[i][2], ring[i][3], tube, mat, Vec3d.Y, joint); roles.add(new Member(Role.CROSS, i)); }
                    at[i] = new int[]{ring[i][0], ring[i][1]};
                }
                for (int o = 0; o + 1 < order.size(); o++) {
                    int i = order.get(o), j = order.get(o + 1);
                    for (int c = 0; c < 4; c++) { frame.member(ring[i][c], ring[j][c], tube, mat, Vec3d.Y, joint); roles.add(new Member(Role.RAIL, -1)); }
                    ChassisStationDesign s = d.stations().get(i);
                    if (s.brace() == ChassisStationDesign.Brace.NONE) { continue; }
                    boolean x = s.brace() == ChassisStationDesign.Brace.X, top = !s.openTop() && !d.stations().get(j).openTop();
                    // sides, floor, top: one diagonal each, or an X
                    int[][] faces = {{0, 2}, {1, 3}, {0, 1}, {2, 3}};
                    for (int fi = 0; fi < faces.length; fi++) {
                        if (fi == 3 && !top) { continue; }
                        int a = faces[fi][0], b = faces[fi][1];
                        frame.member(ring[i][a], ring[j][b], tube, mat, Vec3d.Y, joint); roles.add(new Member(Role.BRACE, i));
                        if (x) { frame.member(ring[i][b], ring[j][a], tube, mat, Vec3d.Y, joint); roles.add(new Member(Role.BRACE, i)); }
                    }
                }
                return new ChassisLayout(d, frame, roles, at, centre, f.tubeDiameterM());
            }
        }
    }

    /** Where twist is measured at a station (design index): a backbone's centre, else its two nodes. */
    public FrameAnalysis.Station twistStation(int station) {
        double x = frame.position(stationNodes[station][0]).x();
        return centreNodes[station] >= 0 ? FrameAnalysis.Station.node(x, centreNodes[station]) : FrameAnalysis.Station.pair(x, stationNodes[station][0], stationNodes[station][1]);
    }
}
