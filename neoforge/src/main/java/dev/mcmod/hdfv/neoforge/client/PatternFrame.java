package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.neoforge.client.PartMesh;

/**
 * A multiblock pattern's frame (x right as one faces the machine, y up, z back from its front; metres from the
 * pattern's front left bottom corner) mapped onto its controller's block (0.8; the machines' renderers draw in it).
 */
final class PatternFrame {
    private final double rx, rz, dx, dz, cx, cy, cz;

    /** For a facing (0 south, 1 west, 2 north, 3 east) and the controller's cell in the pattern. */
    PatternFrame(int facing, int controllerX, int controllerY, int controllerZ) {
        int[][] front = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        int[] f = front[Math.floorMod(facing, 4)];
        dx = -f[0]; dz = -f[1];
        rx = -dz; rz = dx;
        cx = controllerX + 0.5; cy = controllerY; cz = controllerZ + 0.5;
    }

    Vec3d at(double x, double y, double z) {
        double ox = x - cx, oz = z - cz;
        return new Vec3d(0.5 + ox * rx + oz * dx, y - cy, 0.5 + ox * rz + oz * dz);
    }

    void box(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1, int colour) {
        Vec3d a = at(x0, y0, z0), b = at(x1, y1, z1);
        m.box(new Vec3d(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new Vec3d(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())), colour);
    }

    void cylinder(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1, double r, int colour) {
        m.cylinder(at(x0, y0, z0), at(x1, y1, z1), r, r, 12, colour);
    }

    /** A bar of square section between two points (a rotated box), as a cylinder of four sides. */
    void bar(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1, double half, int colour) {
        m.cylinder(at(x0, y0, z0), at(x1, y1, z1), half * 1.41, half * 1.41, 4, colour);
    }
}
