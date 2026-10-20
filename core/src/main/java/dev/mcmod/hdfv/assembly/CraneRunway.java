package dev.mcmod.hdfv.assembly;

import dev.mcmod.aerocore.api.Vec3d;

/**
 * An overhead travelling crane's moves (0.8): the bridge along the runway, the trolley across it, the hook up and
 * down. A hoist is a lift at the pick-up, the travel (bridge and trolley together) and a lower onto the mount, each a
 * third of the step, the hook eased in and out of each move (a smoothstep). Speeds of a light shop crane (hoist
 * 8 m/min, trolley 20, bridge 30 m/min; FEM 9.511 classes, typical) give the time a hoist would take with the crane
 * alone (moveS), for the room's sound and the tests.
 */
public final class CraneRunway {
    private CraneRunway() {}

    public static final double HOIST_MPS = 8.0 / 60, TROLLEY_MPS = 20.0 / 60, BRIDGE_MPS = 30.0 / 60;

    private static double ease(double x) { x = Math.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    /** The hook from a pick-up to a drop with a headroom above both (m), at a share t of the hoist. */
    public static Vec3d hook(Vec3d pick, Vec3d drop, double headroomM, double t) {
        double top = Math.max(pick.y(), drop.y()) + headroomM;
        if (t < 1.0 / 3) { return new Vec3d(pick.x(), pick.y() + (top - pick.y()) * ease(3 * t), pick.z()); }
        if (t < 2.0 / 3) {
            double s = ease(3 * t - 1);
            return new Vec3d(pick.x() + (drop.x() - pick.x()) * s, top, pick.z() + (drop.z() - pick.z()) * s);
        }
        return new Vec3d(drop.x(), top + (drop.y() - top) * ease(3 * t - 2), drop.z());
    }

    /** Seconds the crane's own moves take for a hoist (bridge along x, trolley along z, hook up and down). */
    public static double moveS(Vec3d pick, Vec3d drop, double headroomM) {
        double top = Math.max(pick.y(), drop.y()) + headroomM;
        double travel = Math.max(Math.abs(drop.x() - pick.x()) / BRIDGE_MPS, Math.abs(drop.z() - pick.z()) / TROLLEY_MPS);
        return (top - pick.y()) / HOIST_MPS + travel + (top - drop.y()) / HOIST_MPS;
    }
}
