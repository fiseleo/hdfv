package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.MeshOps;
import dev.mcmod.aerocore.neoforge.client.PartMesh;

/**
 * A coarse stand-in for a part seen far away (0.11, MeshBuilder.STAND_IN: drawn alone at the farthest level): the
 * model cut into slices along x, each slice's outline (a body of revolution about x for a round engine, or boxes of
 * its height and width) in the area-weighted colour of the faces in it. A hundred quads or so for an engine of
 * thirty thousand; its finish is the default (a far part's texture is below a pixel).
 */
public final class StandIn {
    private StandIn() {}

    private static final int SLICES = 8, SIDES = 12;

    /** The stand-in of a model: round about x, or boxes. */
    public static PartMesh of(MeshBuilder<?> model, boolean round) {
        PartMesh out = new PartMesh();
        Vec3d[] b = model.bounds();
        if (b == null) { return out; }
        double x0 = b[0].x(), x1 = b[1].x(), dx = Math.max(1e-6, (x1 - x0) / SLICES);
        double[] r = new double[SLICES], ylo = new double[SLICES], yhi = new double[SLICES], zlo = new double[SLICES], zhi = new double[SLICES];
        double[][] colour = new double[SLICES][4];
        java.util.Arrays.fill(ylo, Double.MAX_VALUE); java.util.Arrays.fill(zlo, Double.MAX_VALUE);
        java.util.Arrays.fill(yhi, -Double.MAX_VALUE); java.util.Arrays.fill(zhi, -Double.MAX_VALUE);
        for (MeshBuilder.Quad q : model.quads()) {
            if (q.has(MeshBuilder.INTERIOR) || q.detail() >= 2) { continue; }
            for (int c = 0; c < 4; c++) {
                Vec3d p = q.corner(c);
                int i = Math.clamp((int) ((p.x() - x0) / dx), 0, SLICES - 1);
                r[i] = Math.max(r[i], Math.hypot(p.y(), p.z()));
                ylo[i] = Math.min(ylo[i], p.y()); yhi[i] = Math.max(yhi[i], p.y());
                zlo[i] = Math.min(zlo[i], p.z()); zhi[i] = Math.max(zhi[i], p.z());
            }
            Vec3d centre = q.a().add(q.b()).add(q.c()).add(q.d()).scale(0.25);
            int i = Math.clamp((int) ((centre.x() - x0) / dx), 0, SLICES - 1);
            double area = MeshOps.area(q);
            int col = q.color();
            colour[i][0] += area * ((col >> 16) & 255); colour[i][1] += area * ((col >> 8) & 255); colour[i][2] += area * (col & 255); colour[i][3] += area;
        }
        int[] argb = new int[SLICES];
        for (int i = 0; i < SLICES; i++) {
            double w = Math.max(colour[i][3], 1e-12);
            argb[i] = colour[i][3] > 0 ? 0xff000000 | (int) (colour[i][0] / w) << 16 | (int) (colour[i][1] / w) << 8 | (int) (colour[i][2] / w) : 0xff808080;
            if (ylo[i] > yhi[i]) { ylo[i] = yhi[i] = zlo[i] = zhi[i] = 0; }   // an empty slice
        }
        if (round) {
            // the outline through each slice's middle, closed on the axis at both ends
            double[] along = new double[SLICES + 2], radius = new double[SLICES + 2];
            int[] colours = new int[SLICES + 2];
            along[0] = 0; radius[0] = 0; colours[0] = argb[0];
            for (int i = 0; i < SLICES; i++) { along[i + 1] = (i + 0.5) / SLICES; radius[i + 1] = r[i]; colours[i + 1] = argb[i]; }
            along[SLICES + 1] = 1; radius[SLICES + 1] = 0; colours[SLICES + 1] = argb[SLICES - 1];
            along[1] = 0; along[SLICES] = 1;   // the ends square, not pointed
            out.lathe(new Vec3d(x0, 0, 0), new Vec3d(x1, 0, 0), along, radius, colours, SIDES, argb[0], true);
        } else {
            for (int i = 0; i < SLICES; i++) {
                if (yhi[i] <= ylo[i] || zhi[i] <= zlo[i]) { continue; }
                out.box(new Vec3d(x0 + i * dx, ylo[i], zlo[i]), new Vec3d(x0 + (i + 1) * dx, yhi[i], zhi[i]), argb[i]);
            }
        }
        var quads = out.quads();
        for (int i = 0; i < quads.size(); i++) { quads.set(i, quads.get(i).withTags(quads.get(i).tags() | MeshBuilder.STAND_IN)); }
        return out;
    }
}
