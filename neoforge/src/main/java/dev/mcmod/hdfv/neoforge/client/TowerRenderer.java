package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.mesh.Mesh;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.mesh.MeshOps;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.probe.WorldAir;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.neoforge.airport.TowerBlockEntity;
import dev.mcmod.hdfv.neoforge.client.model.AirfieldKit;
import dev.mcmod.hdfv.neoforge.client.model.Details;
import dev.mcmod.hdfv.neoforge.multiblock.Multiblocks;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A formed control tower drawn whole (0.20) over its blocks (the members' own models are empty while formed; the
 * controller, the base's duty desk, keeps its block). In the pattern's frame (PatternFrame: x right facing the tower,
 * y up, z back from its front), sizes in metres:
 * <ul>
 *   <li>the base (two storeys, its doorway left of the desk with a canopy over it, a plinth, a cornice, windows), the
 *   shaft (as many courses as built, a stair window up its front and back, a reveal every three metres), both in
 *   board-formed concrete (a normal map);</li>
 *   <li>the cab: a flared skirt from the shaft out to the octagon, the floor with its hatch, a catwalk with railings
 *   round it, outward-leaning reflective glazing between mullions, the console inside (when the tower has one), the
 *   roof with its eaves; on top the radar (turning while the tower is powered) and the mast (its cups turning with the
 *   wind).</li>
 * </ul>
 * Levels of detail at 64 and 256 m (the cab's inside and the fine kit only near, a stand-in beyond); seen to 384 m;
 * lit as its cab.
 * <p>
 * 0.21, inside: the base painted with a skirting and a ceiling, its windows seen from in there too, a distribution
 * board and cable trays over the batteries, a notice board and a clock; the cab carpeted, consoles round its front and
 * sides, the watch supervisor's desk, downlights, blinds a third down the glass, a railing round the stair's way up
 * through the hatch (where the hatch is covered round it, as a generated tower's is). At night, while the tower has
 * power, its windows glow (lamp lenses: the base's, the stair's, the cab's downlights, screens and dimly its ceiling;
 * the screens by day too), as dark as the sky is (the client's Level.getSkyDarken() is never updated).
 */
public final class TowerRenderer implements BlockEntityRenderer<TowerBlockEntity> {
    public TowerRenderer(BlockEntityRendererProvider.Context c) {}

    private static final int CONCRETE = 0xffbcb9b0, PLINTH = 0xff8e8b84, ROOF = 0xff4b5056, FASCIA = 0xffd9dadb, MULLION = 0xff2c3035,
            DECK = 0xff5a6066, RAIL = 0xff3e444b, GLASS = 0x66a6c4d4, FLOOR = 0xff3d4146, RADAR = 0xffe6e8ea, PEDESTAL = 0xffd0d3d6,
            CANOPY = 0xffcfd1d3, WINDOW = 0xa0587890;
    private static final double NEAR_M = 64, FAR_M = 256;
    /** The radar's turn rate (12 rpm). */
    private static final double RADAR_RAD_S = 2 * Math.PI * 12 / 60;

    private static final class Seen { Pattern.Match match; boolean hatchCover; long at = Long.MIN_VALUE / 2; double radar, cups; long nanos = System.nanoTime(); }
    private static final Map<TowerBlockEntity, Seen> SEEN = new WeakHashMap<>();

    /** What the fixed part is made of. */
    private record Key(int facing, int courses, boolean console, boolean radar, boolean mast, boolean hatchCover) {}

    /** The lenses' meshes (0.21), held by their keys (MeshBakery keeps its keys weakly). */
    private static final Map<Key, PartMesh> LENSES = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, PartMesh> e) { return size() > 64; }
    });
    /** The lenses: the base's windows, the stair's, the cab's downlights, its screens, its ceiling. */
    private static final int BASE_WINDOWS = 0, STAIR_WINDOWS = 1, DOWNLIGHTS = 2, SCREENS = 3, CAB_CEILING = 4;
    private static final int WARM = 0xffffd9a0, DOWNLIGHT = 0xfffff1d6, SCREEN = 0xff7fd7ff, CEILING_GLOW = 0xffb8ae98;
    private static final int WALL_PAINT = 0xffdcd8ce, SKIRT = 0xff5f646a, CEILING = 0xffe9e7e1, CARPET = 0xff47505c, BLIND = 0xff3a3f46,
            BOARD_PAINT = 0xffc9ccce;

    /** The cab's octagon (frame x, z), front edge first, round to the right. */
    private static final double[][] OCTAGON = {{2, 0}, {7, 0}, {9, 2}, {9, 7}, {7, 9}, {2, 9}, {0, 7}, {0, 2}};

    @Override public void render(TowerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) { return; }
        Seen s = SEEN.computeIfAbsent(be, b -> new Seen());
        long now = be.getLevel().getGameTime();
        BlockPos pos = be.getBlockPos();
        if (now - s.at >= 20 || now < s.at) {
            s.at = now;
            var state = be.getBlockState();
            int facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue() : 0;
            s.match = Structures.TOWER_PATTERN.match(Multiblocks.world(be.getLevel()), pos.getX(), pos.getY(), pos.getZ(), facing);
            s.hatchCover = s.match != null && hatchCovered(be, s.match);
        }
        Pattern.Match m = s.match;
        if (m == null) { return; }
        Key key = new Key(m.facing(), m.repeats(), m.has(Structures.TOWER_CONSOLE), m.has(Structures.TOWER_RADAR), m.has(Structures.TOWER_MAST), s.hatchCover);
        PatternFrame f = new PatternFrame(m.facing(), 4, 0, 1);
        Tower t = new Tower(f, key);
        // the moving parts' angles: the radar while powered, the cups with the wind at the mast's top
        long nanos = System.nanoTime();
        double dt = Math.min(0.25, (nanos - s.nanos) * 1e-9);
        s.nanos = nanos;
        if (be.shown().getBoolean("Powered")) { s.radar = (s.radar + RADAR_RAD_S * dt) % (2 * Math.PI); }
        Vec3d mastTop = f.at(2.5, t.r1 + AirfieldKit.CUPS_Y, 2.5);
        double wind = WorldAir.at(be.getLevel(), pos.getX() + mastTop.x(), pos.getY() + mastTop.y(), pos.getZ() + mastTop.z()).meanWind().length();
        s.cups = (s.cups + wind / (3 * 0.14) * dt) % (2 * Math.PI);
        Vec3d middle = f.at(4.5, t.g1 * 0.5, 4.5);
        Vec3 world = new Vec3(pos.getX() + middle.x(), pos.getY() + middle.y(), pos.getZ() + middle.z());
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean fine = camera.distanceToSqr(world) < NEAR_M * NEAR_M;
        PartMesh moving = new PartMesh();
        t.moving(moving, s.radar, s.cups, fine);
        // lit as its cab (inside the glass) or its controller, whichever is brighter
        Vec3d cab = f.at(4.5, t.f1 + 0.5, 4.5);
        int cabLight = LevelRenderer.getLightColor(be.getLevel(), pos.offset((int) Math.floor(cab.x()), (int) Math.floor(cab.y()), (int) Math.floor(cab.z())));
        light = Math.max(light & 0xffff, cabLight & 0xffff) | Math.max(light >>> 16, cabLight >>> 16) << 16;
        MachineMeshes.draw(key, () -> { PartMesh p = new PartMesh(); t.fixed(p); return p; }, moving, pos, pose, buffers, light, NEAR_M, FAR_M, world);
        lenses(be, key, t, pose, buffers, partialTick);
    }

    /** Whether the hatch is covered round the stair's way up (a generated tower's grating, AirportLayout.HATCH_COVER). */
    private static boolean hatchCovered(TowerBlockEntity be, Pattern.Match m) {
        BlockPos pos = be.getBlockPos();
        dev.mcmod.hdfv.airport.AirportLayout.TowerSite site = new dev.mcmod.hdfv.airport.AirportLayout.TowerSite(pos.getX(), pos.getY(), pos.getZ(), m.facing(), m.repeats());
        for (int i : dev.mcmod.hdfv.airport.AirportLayout.HATCH_COVER) {
            int[] c = dev.mcmod.hdfv.airport.AirportLayout.STAIR_RING[i], w = dev.mcmod.hdfv.airport.AirportLayout.inside(site, c[0], c[1]);
            if (!be.getLevel().getBlockState(new BlockPos(w[0], pos.getY() + 2 + m.repeats(), w[1])).is(dev.mcmod.hdfv.neoforge.airport.HdfvAirports.STEEL_GRATING.get())) { return false; }
        }
        return true;
    }

    /** The lit windows, downlights and screens (0.21): their glow by the tower's power and the night. */
    private static void lenses(TowerBlockEntity be, Key key, Tower t, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        if (!be.shown().getBoolean("Powered") || !(be.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level)) { return; }
        // the client's Level.getSkyDarken() is worked out once, on joining (only the server updates it): the sky's own darkness, as FieldLamps
        float dark = Math.clamp((1f - level.getSkyDarken(partialTick)) / 0.8f, 0f, 1f);
        float[] levels = {dark, 0.9f * dark, 0.6f * dark, 0.45f + 0.4f * dark, 0.35f * dark};
        PartMesh lens = LENSES.computeIfAbsent(key, k -> { PartMesh m = new PartMesh(); t.lenses(m); return m; });
        MeshBakery.Baked baked = MeshBakery.get(lens, () -> lens, MeshBakery.Spec.WORLD);
        if (baked == null || baked.lamps().size() < levels.length) { return; }
        PartLayers layers = new PartLayers().gpu(true).late(true);
        layers.lamps(new PartRenderer.Drawn(baked, 0, levels), 0, pose);
        layers.flush(buffers);
    }

    @Override public boolean shouldRenderOffScreen(TowerBlockEntity be) { return true; }

    @Override public int getViewDistance() { return 384; }

    @Override public AABB getRenderBoundingBox(TowerBlockEntity be) {
        BlockPos p = be.getBlockPos();
        // the tallest tower (56 courses) and its mast
        return new AABB(p.getX() - 11, p.getY() - 1, p.getZ() - 11, p.getX() + 12, p.getY() + 72, p.getZ() + 12);
    }

    // ---------------------------------------------------------------- the tower in its frame

    /** One tower's geometry: its frame and heights. */
    static final class Tower {
        final PatternFrame f;
        final Key k;
        /** The shaft's top (the cab floor's underside), the floor's top, the glazing's top, the roof's top (m). */
        final double f0, f1, g1, r1;
        private final Vec3d right;

        Tower(PatternFrame f, Key k) {
            this.f = f;
            this.k = k;
            f0 = 2 + k.courses();
            f1 = f0 + 1;
            g1 = f1 + 2;
            r1 = g1 + 1;
            right = f.at(1, 0, 0).subtract(f.at(0, 0, 0));
        }

        private Vec3d dir(double x, double y, double z) { return f.at(x, y, z).subtract(f.at(0, 0, 0)); }

        /** A quad of frame points, wound to face along a frame normal in the world (the frame is mirrored). */
        void quad(PartMesh m, double[] a, double[] b, double[] c, double[] d, double[] normal, int colour) {
            Vec3d wa = f.at(a[0], a[1], a[2]), wb = f.at(b[0], b[1], b[2]), wc = f.at(c[0], c[1], c[2]), wd = f.at(d[0], d[1], d[2]);
            Vec3d n = dir(normal[0], normal[1], normal[2]).normalized();
            if (wb.subtract(wa).cross(wc.subtract(wa)).dot(n) < 0) { m.quad(wd, wc, wb, wa, colour, n); } else { m.quad(wa, wb, wc, wd, colour, n); }
        }

        /** A quad with its normal from its own corners, flipped to point away from a frame point (outside it). */
        void face(PartMesh m, double[] a, double[] b, double[] c, double[] d, double[] inside, int colour) {
            double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, v = {d[0] - a[0], d[1] - a[1], d[2] - a[2]};
            double[] n = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
            double[] mid = {(a[0] + c[0]) / 2 - inside[0], (a[1] + c[1]) / 2 - inside[1], (a[2] + c[2]) / 2 - inside[2]};
            if (n[0] * mid[0] + n[1] * mid[1] + n[2] * mid[2] < 0) { n = new double[]{-n[0], -n[1], -n[2]}; }
            quad(m, a, b, c, d, n, colour);
        }

        void box(PartMesh m, double x0, double y0, double z0, double x1, double y1, double z1, int colour) { f.box(m, x0, y0, z0, x1, y1, z1, colour); }

        /** A bar of square section between two frame points. */
        void bar(PartMesh m, double[] a, double[] b, double half, int colour) { f.bar(m, a[0], a[1], a[2], b[0], b[1], b[2], half, colour); }

        /** A kit part at a frame point, turned about up by yaw (0: its front to the tower's front), fine or coarse. */
        void kit(PartMesh m, AirfieldKit.Part p, double x, double y, double z, double yaw, boolean fine, int paint) {
            Vec3d kx = turn(right.scale(-1), yaw);
            Mesh placed = MeshOps.placed(AirfieldKit.mesh(p, fine, paint), new Vec3d(1, 1, 1), Details.basis(new Vec3d(0, 1, 0), kx), f.at(x, y, z));
            int levels = fine ? new PartMesh().levels(0, 0).tags() : new PartMesh().levels(1, 1).tags();
            int mask = MeshBuilder.NEAR | MeshBuilder.DETAIL;
            int keep = m.tags() & ~mask;
            for (MeshBuilder.Quad q : placed.quads()) { m.quads().add(q.withTags((q.tags() & ~mask) | (levels & mask) | keep)); }
        }

        /** A kit part both ways: fine near, coarse beyond. */
        void kit(PartMesh m, AirfieldKit.Part p, double x, double y, double z, double yaw, int paint) {
            kit(m, p, x, y, z, yaw, true, paint);
            kit(m, p, x, y, z, yaw, false, paint);
        }

        /** The octagon pushed out (positive) or in (negative) by a distance, at a height. */
        static double[][] octagon(double out, double y) {
            int n = OCTAGON.length;
            double[][] p = new double[n][];
            for (int i = 0; i < n; i++) {
                double[] a = OCTAGON[(i + n - 1) % n], b = OCTAGON[i], c = OCTAGON[(i + 1) % n];
                // the two edges' outward normals (the outline runs clockwise seen from above in the frame: x right, z back)
                double[] n1 = outward(a, b), n2 = outward(b, c);
                double[] bis = {n1[0] + n2[0], n1[1] + n2[1]};
                double scale = out / (1 + n1[0] * n2[0] + n1[1] * n2[1]);
                p[i] = new double[]{b[0] + bis[0] * scale, y, b[1] + bis[1] * scale};
            }
            return p;
        }

        private static double[] outward(double[] a, double[] b) {
            double dx = b[0] - a[0], dz = b[1] - a[1], l = Math.hypot(dx, dz);
            return new double[]{dz / l, -dx / l};
        }

        private static final double[] CENTRE_XZ = {4.5, 4.5};

        /** A flat octagon (a fan of three quads from its first corner) facing up or down. */
        void octagonFace(PartMesh m, double[][] o, boolean up, int colour) {
            double[] n = {0, up ? 1 : -1, 0};
            for (int i = 1; i + 2 < o.length; i += 2) { quad(m, o[0], o[i], o[i + 1], o[i + 2], n, colour); }
        }

        /** The band between two outlines (the same number of corners), facing away from the tower's axis. */
        void band(PartMesh m, double[][] lo, double[][] hi, boolean outward, int colour) {
            for (int i = 0; i < lo.length; i++) {
                int j = (i + 1) % lo.length;
                double[] inside = {CENTRE_XZ[0], (lo[i][1] + hi[i][1]) / 2, CENTRE_XZ[1]};
                if (outward) { face(m, lo[i], lo[j], hi[j], hi[i], inside, colour); }
                else {
                    // facing the axis: "inside" is a point far outside along the edge's outward normal
                    double[] o = outward(new double[]{lo[i][0], lo[i][2]}, new double[]{lo[j][0], lo[j][2]});
                    double[] far = {(lo[i][0] + lo[j][0]) / 2 + 100 * o[0], inside[1], (lo[i][2] + lo[j][2]) / 2 + 100 * o[1]};
                    face(m, lo[i], lo[j], hi[j], hi[i], far, colour);
                }
            }
        }

        /** A flat ring between two outlines at heights (a soffit, a deck), facing up or down. */
        void ring(PartMesh m, double[][] inner, double[][] outer, boolean up, int colour) {
            double[] n = {0, up ? 1 : -1, 0};
            for (int i = 0; i < inner.length; i++) {
                int j = (i + 1) % inner.length;
                quad(m, inner[i], inner[j], outer[j], outer[i], n, colour);
            }
        }

        // ---------------------------------------------------------------- the fixed part

        void fixed(PartMesh m) {
            base(m);
            shaft(m);
            cab(m);
            standIn(m);
        }

        private void concrete(PartMesh m) { m.finish(Finishes.BOARD_FORMED_CONCRETE).clear(false).cabin(false); }

        private void base(PartMesh m) {
            concrete(m);
            // the walls: the front's doorway (x 3-4) open, the desk (x 4-5, its first storey) its own block
            box(m, 1, 0, 1, 3, 2, 2, CONCRETE);
            box(m, 4, 1, 1, 5, 2, 2, CONCRETE);
            box(m, 5, 0, 1, 8, 2, 2, CONCRETE);
            box(m, 1, 0, 7, 8, 2, 8, CONCRETE);
            box(m, 1, 0, 2, 2, 2, 7, CONCRETE);
            box(m, 7, 0, 2, 8, 2, 7, CONCRETE);
            // the plinth and the cornice, a little proud
            m.finish(Finishes.CAST_IRON);
            box(m, 0.95, 0, 0.95, 3, 0.3, 1.05, PLINTH);
            box(m, 5, 0, 0.95, 8.05, 0.3, 1.05, PLINTH);
            box(m, 0.95, 0, 7.95, 8.05, 0.3, 8.05, PLINTH);
            box(m, 0.95, 0, 1.05, 1.05, 0.3, 7.95, PLINTH);
            box(m, 7.95, 0, 1.05, 8.05, 0.3, 7.95, PLINTH);
            m.finish(Finishes.PAINT);
            box(m, 0.92, 1.9, 0.92, 8.08, 2.05, 1.05, FASCIA);
            box(m, 0.92, 1.9, 7.95, 8.08, 2.05, 8.08, FASCIA);
            box(m, 0.92, 1.9, 1.05, 1.05, 2.05, 7.95, FASCIA);
            box(m, 7.95, 1.9, 1.05, 8.08, 2.05, 7.95, FASCIA);
            // the doorway's steel frame
            m.finish(Finishes.FORGED_STEEL);
            box(m, 2.92, 0, 0.94, 3.04, 1.9, 1.06, MULLION);
            box(m, 3.96, 0, 0.94, 4.08, 1.9, 1.06, MULLION);
            box(m, 2.92, 1.84, 0.94, 4.08, 1.9, 1.06, MULLION);
            // windows: two a side, the back's in the middle
            m.finish(Finishes.TOWER_GLASS).clear(true);
            for (double z : new double[]{2.8, 5.2}) {
                quad(m, new double[]{0.994, 0.9, z}, new double[]{0.994, 0.9, z + 1}, new double[]{0.994, 1.6, z + 1}, new double[]{0.994, 1.6, z}, new double[]{-1, 0, 0}, WINDOW);
                quad(m, new double[]{8.006, 0.9, z}, new double[]{8.006, 0.9, z + 1}, new double[]{8.006, 1.6, z + 1}, new double[]{8.006, 1.6, z}, new double[]{1, 0, 0}, WINDOW);
            }
            quad(m, new double[]{3.5, 0.9, 8.006}, new double[]{5.5, 0.9, 8.006}, new double[]{5.5, 1.6, 8.006}, new double[]{3.5, 1.6, 8.006}, new double[]{0, 0, 1}, WINDOW);
            m.clear(false);
            baseInside(m);
            // the canopy over the doorway (its back on the wall, reaching out in front)
            m.finish(null);
            // (its back on the shaft's face, a metre behind the base's: it roofs the base's top and reaches out over the door)
            kit(m, AirfieldKit.Part.ENTRANCE_CANOPY, 3.5, 2.06, 2.0, 0, CANOPY);
        }

        /**
         * 0.21: the base's inside (seen only from in there: near only): paint over a skirting on the walls' inner faces
         * (just off them: the concrete under it is covered and left out), the ceiling round the shaft's opening, the
         * windows from inside, the board, trays, notice board and clock on the walls.
         */
        private void baseInside(PartMesh m) {
            m.levels(0, 0).finish(Finishes.PAINT);
            int from = m.quads().size();
            // the walls' inner faces: left x 2, right x 7, back z 7, front z 2 (less the doorway, x 3-4, and the desk, x 4-5 below 1 m)
            wallPaint(m, new double[]{2.004, 0, 2}, new double[]{2.004, 0, 7}, 2, new double[]{1, 0, 0});
            wallPaint(m, new double[]{6.996, 0, 7}, new double[]{6.996, 0, 2}, 2, new double[]{-1, 0, 0});
            wallPaint(m, new double[]{7, 0, 6.996}, new double[]{2, 0, 6.996}, 2, new double[]{0, 0, -1});
            wallPaint(m, new double[]{2, 0, 2.004}, new double[]{3, 0, 2.004}, 2, new double[]{0, 0, 1});
            wallPaint(m, new double[]{5, 0, 2.004}, new double[]{7, 0, 2.004}, 2, new double[]{0, 0, 1});
            quad(m, new double[]{4, 1, 2.004}, new double[]{5, 1, 2.004}, new double[]{5, 2, 2.004}, new double[]{4, 2, 2.004}, new double[]{0, 0, 1}, WALL_PAINT);
            // the ceiling round the shaft's opening (x and z 3 to 6)
            double c = 1.996;
            for (double[] r : new double[][]{{2, 7, 2, 3}, {2, 7, 6, 7}, {2, 3, 3, 6}, {6, 7, 3, 6}}) {
                quad(m, new double[]{r[0], c, r[2]}, new double[]{r[1], c, r[2]}, new double[]{r[1], c, r[3]}, new double[]{r[0], c, r[3]}, new double[]{0, -1, 0}, CEILING);
            }
            // a room's walls and ceiling meet in concave edges all round: whole, every corner is shaded and the face is dark all over
            split(m, from, 1.0);
            // the windows, from inside
            m.finish(Finishes.TOWER_GLASS).clear(true);
            for (double z : new double[]{2.8, 5.2}) {
                quad(m, new double[]{2.006, 0.9, z}, new double[]{2.006, 0.9, z + 1}, new double[]{2.006, 1.6, z + 1}, new double[]{2.006, 1.6, z}, new double[]{1, 0, 0}, WINDOW);
                quad(m, new double[]{6.994, 0.9, z}, new double[]{6.994, 0.9, z + 1}, new double[]{6.994, 1.6, z + 1}, new double[]{6.994, 1.6, z}, new double[]{-1, 0, 0}, WINDOW);
            }
            quad(m, new double[]{3.5, 0.9, 6.994}, new double[]{5.5, 0.9, 6.994}, new double[]{5.5, 1.6, 6.994}, new double[]{3.5, 1.6, 6.994}, new double[]{0, 0, -1}, WINDOW);
            m.clear(false).finish(null);
            // on the walls: the board over the batteries (right of the back window), trays under the ceiling, a notice board, a clock
            kit(m, AirfieldKit.Part.DISTRIBUTION_BOARD, 6.25, 1.0, 7, 0, true, BOARD_PAINT);
            for (double x = 2.5; x <= 6.5; x += 1) { kit(m, AirfieldKit.Part.CABLE_TRAY, x, 1.8, 7, 0, true, BOARD_PAINT); }
            for (double z = 2.5; z <= 6.5; z += 1) { kit(m, AirfieldKit.Part.CABLE_TRAY, 7, 1.8, z, -Math.PI / 2, true, BOARD_PAINT); }
            kit(m, AirfieldKit.Part.NOTICE_BOARD, 2, 1.0, 4.5, Math.PI / 2, true, BOARD_PAINT);
            kit(m, AirfieldKit.Part.WALL_CLOCK, 6.1, 1.5, 2, Math.PI, true, BOARD_PAINT);
            m.levels(0, 3);
        }

        /** Paint over a skirting on a wall's inner face from a to b (frame points at y 0), so high. */
        private void wallPaint(PartMesh m, double[] a, double[] b, double high, double[] normal) {
            quad(m, new double[]{a[0], 0, a[2]}, new double[]{b[0], 0, b[2]}, new double[]{b[0], 0.12, b[2]}, new double[]{a[0], 0.12, a[2]}, normal, SKIRT);
            quad(m, new double[]{a[0], 0.12, a[2]}, new double[]{b[0], 0.12, b[2]}, new double[]{b[0], high, b[2]}, new double[]{a[0], high, a[2]}, normal, WALL_PAINT);
        }

        private void shaft(PartMesh m) {
            concrete(m);
            int from = m.quads().size();
            box(m, 2, 2, 2, 7, f0, 3, CONCRETE);
            box(m, 2, 2, 6, 7, f0, 7, CONCRETE);
            box(m, 2, 2, 3, 3, f0, 6, CONCRETE);
            box(m, 6, 2, 3, 7, f0, 6, CONCRETE);
            split(m, from, 3);
            // the stair windows up its front and back, a transom every three metres; reveals round it as often
            double top = f0 - 1.5;
            if (top > 3.5) {
                m.finish(Finishes.TOWER_GLASS).clear(true);
                quad(m, new double[]{4.15, 3, 1.994}, new double[]{4.85, 3, 1.994}, new double[]{4.85, top, 1.994}, new double[]{4.15, top, 1.994}, new double[]{0, 0, -1}, WINDOW);
                quad(m, new double[]{4.15, 3, 7.006}, new double[]{4.85, 3, 7.006}, new double[]{4.85, top, 7.006}, new double[]{4.15, top, 7.006}, new double[]{0, 0, 1}, WINDOW);
                m.clear(false).finish(Finishes.FORGED_STEEL);
                box(m, 4.08, 2.95, 1.985, 4.15, top + 0.05, 2.0, MULLION);
                box(m, 4.85, 2.95, 1.985, 4.92, top + 0.05, 2.0, MULLION);
                box(m, 4.08, 2.95, 7.0, 4.15, top + 0.05, 7.015, MULLION);
                box(m, 4.85, 2.95, 7.0, 4.92, top + 0.05, 7.015, MULLION);
                for (double y = 3; y <= top + 1e-6; y += 3) {
                    box(m, 4.08, y - 0.04, 1.985, 4.92, y + 0.04, 2.0, MULLION);
                    box(m, 4.08, y - 0.04, 7.0, 4.92, y + 0.04, 7.015, MULLION);
                }
            }
            m.finish(Finishes.CAST_IRON);
            for (double y = 5; y < f0 - 1; y += 3) {
                box(m, 1.99, y, 1.99, 7.01, y + 0.04, 2.0, PLINTH);
                box(m, 1.99, y, 7.0, 7.01, y + 0.04, 7.01, PLINTH);
                box(m, 1.99, y, 2.0, 2.0, y + 0.04, 7.0, PLINTH);
                box(m, 7.0, y, 2.0, 7.01, y + 0.04, 7.0, PLINTH);
            }
        }

        private void cab(PartMesh m) {
            // the skirt: the shaft (its corners cut) flaring out to the octagon under the floor
            double[][] square = {{3, f0 - 1.6, 2}, {6, f0 - 1.6, 2}, {7, f0 - 1.6, 3}, {7, f0 - 1.6, 6}, {6, f0 - 1.6, 7}, {3, f0 - 1.6, 7}, {2, f0 - 1.6, 6}, {2, f0 - 1.6, 3}};
            double[][] under = octagon(0, f0);
            concrete(m);
            for (int i = 0; i < 8; i++) {
                int j = (i + 1) % 8;
                face(m, square[i], square[j], under[j], under[i], new double[]{4.5, f0 - 3, 4.5}, CONCRETE);
            }
            // the floor's edge and its hatch (a hole over the shaft's inside, x and z 3 to 6)
            double[][] top = octagon(0, f1);
            band(m, under, top, true, CONCRETE);
            // the floor's top is the cab's inside (seen through the glass, kept near however the bake judges it)
            m.finish(Finishes.CARPET).cabin(true);
            int from = m.quads().size();
            floorWithHatch(m, f1, true, CARPET);
            split(m, from, 1.5);
            concrete(m);
            from = m.quads().size();
            floorWithHatch(m, f0, false, CONCRETE);
            split(m, from, 1.5);
            double[][] hatch = {{3, 0, 3}, {6, 0, 3}, {6, 0, 6}, {3, 0, 6}};
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                // the hole's sides face into it: away from a point out past each side (seen from the cab, sides facing
                // out were back faces, and the hole showed the sky through the slab)
                double mx = (hatch[i][0] + hatch[j][0]) / 2, mz = (hatch[i][2] + hatch[j][2]) / 2;
                face(m, new double[]{hatch[i][0], f0, hatch[i][2]}, new double[]{hatch[j][0], f0, hatch[j][2]}, new double[]{hatch[j][0], f1, hatch[j][2]},
                        new double[]{hatch[i][0], f1, hatch[i][2]}, new double[]{mx + 10 * (mx - 4.5), f0 + 0.5, mz + 10 * (mz - 4.5)}, CONCRETE);
            }
            // the catwalk round it and its railings
            m.finish(Finishes.NONSKID);
            double[][] deckIn = octagon(0, f1 - 0.02), deckOut = octagon(0.7, f1 - 0.02), deckLow = octagon(0.7, f1 - 0.2), deckUnder = octagon(0, f1 - 0.2);
            ring(m, deckIn, deckOut, true, DECK);
            ring(m, deckUnder, deckLow, false, DECK);
            band(m, deckLow, deckOut, true, DECK);
            m.finish(null);
            railings(m, octagon(0.66, f1 - 0.02));
            // the glazing: leaning out (its foot 0.45 m in from the floor's edge, its head on it), mullions, sill and head
            double[][] foot = octagon(-0.45, f1), head = octagon(-0.05, g1);
            m.finish(Finishes.TOWER_GLASS).clear(true);
            band(m, foot, head, true, GLASS);
            m.clear(false).finish(Finishes.FORGED_STEEL);
            for (int i = 0; i < 8; i++) {
                int j = (i + 1) % 8;
                bar(m, foot[i], head[i], 0.05, MULLION);
                bar(m, foot[i], foot[j], 0.04, MULLION);
                bar(m, head[i], head[j], 0.05, MULLION);
                double len = Math.hypot(foot[j][0] - foot[i][0], foot[j][2] - foot[i][2]);
                int split = (int) Math.round(len / 1.7);
                for (int s = 1; s < split; s++) {
                    double t = (double) s / split;
                    bar(m, lerp(foot[i], foot[j], t), lerp(head[i], head[j], t), 0.035, MULLION);
                }
            }
            // the inside: the console under the front windows; the ceiling
            if (k.console()) {
                m.cabin(true);
                kit(m, AirfieldKit.Part.CAB_CONSOLE, 4.5, f1, 1.9, 0, true, FASCIA);
                m.cabin(false);
            }
            m.finish(Finishes.PAINT).cabin(true);
            from = m.quads().size();
            octagonFace(m, octagon(-0.05, g1 - 0.02), false, 0xffe4e2dc);
            split(m, from, 1.5);
            cabInside(m);
            m.cabin(false);
            // the roof: eaves out over the catwalk, a fascia, sloping up to its flat top
            double[][] eaveIn = octagon(-0.05, g1), eaveOut = octagon(0.55, g1), fasciaTop = octagon(0.55, g1 + 0.42), topEdge = octagon(-0.6, r1);
            m.finish(Finishes.PAINT);
            ring(m, eaveIn, eaveOut, false, FASCIA);
            band(m, eaveOut, fasciaTop, true, FASCIA);
            m.finish(Finishes.ALUMINIUM_SKIN);
            for (int i = 0; i < 8; i++) {
                int j = (i + 1) % 8;
                face(m, fasciaTop[i], fasciaTop[j], topEdge[j], topEdge[i], new double[]{4.5, g1 - 5, 4.5}, ROOF);
            }
            octagonFace(m, topEdge, true, ROOF);
            // on the roof: the radar's pedestal, the mast
            m.finish(null);
            if (k.radar()) { kit(m, AirfieldKit.Part.RADAR_PEDESTAL, 4.5, r1, 4.5, 0, PEDESTAL); }
            if (k.mast()) { kit(m, AirfieldKit.Part.ANEMOMETER_MAST, 2.5, r1, 2.5, 0, RAIL); }
        }

        /**
         * The quads added since an index split into cells of at most a length. Occlusion is shaded per corner: a shaft
         * wall or a ceiling whose corners all sit in concave edges (the base's top, the skirt, the glazing's mullions)
         * came out dark all over, its open middle having no corner of its own.
         */
        private static void split(PartMesh m, int from, double cell) {
            java.util.List<MeshBuilder.Quad> quads = m.quads(), added = quads.subList(from, quads.size());
            Mesh drawn = new Mesh();
            drawn.quads().addAll(added);
            added.clear();
            quads.addAll(MeshOps.subdivide(drawn, cell).quads());
        }

        /** The cab's downlights (frame x, z). */
        static final double[][] DOWNLIGHT_AT = {{3, 2}, {6, 2}, {1.6, 4.5}, {7.4, 4.5}, {3, 7}, {6, 7}};
        /** The side consoles: frame x, z, yaw (facing their windows: the front diagonals and the sides). */
        static final double[][] SIDE_CONSOLES = {{7.257, 1.742, Math.PI / 4}, {1.742, 1.742, -Math.PI / 4}, {7.95, 4.5, Math.PI / 2}, {1.05, 4.5, -Math.PI / 2}};

        /** 0.21: the cab's furnishing (cabin quads, near only): consoles, the supervisor's desk, downlights, blinds, the hatch's railing. */
        private void cabInside(PartMesh m) {
            m.finish(null);
            for (double[] c : SIDE_CONSOLES) { kit(m, AirfieldKit.Part.CAB_CONSOLE_SIDE, c[0], f1, c[1], c[2], true, FASCIA); }
            kit(m, AirfieldKit.Part.SUPERVISOR_DESK, 1.9, f1, 6.9, 0, true, FASCIA);
            for (double[] d : DOWNLIGHT_AT) { kit(m, AirfieldKit.Part.CEILING_LIGHT, d[0], g1 - 0.02, d[1], 0, true, FASCIA); }
            // blinds a third down the glass, hung clear of it (it leans out), both faces (seen dark from outside)
            m.finish(Finishes.FABRIC);
            double[][] top = octagon(-0.22, g1 - 0.03), bottom = octagon(-0.22, g1 - 0.68);
            for (int i = 0; i < top.length; i++) {
                int j = (i + 1) % top.length;
                double[] in = {CENTRE_XZ[0], g1 - 0.3, CENTRE_XZ[1]};
                face(m, bottom[i], bottom[j], top[j], top[i], new double[]{(bottom[i][0] + bottom[j][0]) / 2 * 2 - in[0], in[1], (bottom[i][2] + bottom[j][2]) / 2 * 2 - in[2]}, BLIND);
                face(m, bottom[i], bottom[j], top[j], top[i], in, BLIND);
            }
            if (k.hatchCover()) {
                // the railing round the stairwell: the last four steps' cells (frame x, z) 5-6 x 3-6 open below the floor and the top
                // step 4-5 x 5-6 at it; open where one steps off the top step the way it climbs (x 4, onto the grating) and to the column
                m.finish(Finishes.FORGED_STEEL);
                double[][] edges = {{5, 3, 6, 3}, {6, 3, 6, 6}, {4, 6, 6, 6}, {5, 3, 5, 5}};
                for (double[] e : edges) {
                    for (double h : new double[]{0.5, 1.0}) { bar(m, new double[]{e[0], f1 + h, e[1]}, new double[]{e[2], f1 + h, e[3]}, 0.02, RAIL); }
                }
                for (double[] post : new double[][]{{5, 3}, {6, 3}, {6, 4.5}, {6, 6}, {5, 6}, {4, 6}, {5, 5}}) {
                    bar(m, new double[]{post[0], f1, post[1]}, new double[]{post[0], f1 + 1.02, post[1]}, 0.025, RAIL);
                }
            }
            m.finish(null);
        }

        /** 0.21: the lenses that glow (a mesh of its own: TowerRenderer.lenses), in the order of the lamps' numbers. */
        void lenses(PartMesh m) {
            // the base's windows, just outside the painted glass
            m.finish(Finishes.LAMP_CLEAR).lamp(BASE_WINDOWS);
            for (double z : new double[]{2.8, 5.2}) {
                quad(m, new double[]{0.99, 0.9, z}, new double[]{0.99, 0.9, z + 1}, new double[]{0.99, 1.6, z + 1}, new double[]{0.99, 1.6, z}, new double[]{-1, 0, 0}, WARM);
                quad(m, new double[]{8.01, 0.9, z}, new double[]{8.01, 0.9, z + 1}, new double[]{8.01, 1.6, z + 1}, new double[]{8.01, 1.6, z}, new double[]{1, 0, 0}, WARM);
            }
            quad(m, new double[]{3.5, 0.9, 8.01}, new double[]{5.5, 0.9, 8.01}, new double[]{5.5, 1.6, 8.01}, new double[]{3.5, 1.6, 8.01}, new double[]{0, 0, 1}, WARM);
            m.addLamp(dev.mcmod.aerocore.api.mesh.Lamp.lens(BASE_WINDOWS, 0, f.at(4.5, 1.25, 4.5), dir(0, 1, 0), 0.5, WARM, -1));
            // the stair's windows up the front and back
            m.lamp(STAIR_WINDOWS);
            double top = f0 - 1.5;
            if (top > 3.5) {
                quad(m, new double[]{4.15, 3, 1.99}, new double[]{4.85, 3, 1.99}, new double[]{4.85, top, 1.99}, new double[]{4.15, top, 1.99}, new double[]{0, 0, -1}, WARM);
                quad(m, new double[]{4.15, 3, 7.01}, new double[]{4.85, 3, 7.01}, new double[]{4.85, top, 7.01}, new double[]{4.15, top, 7.01}, new double[]{0, 0, 1}, WARM);
            }
            m.addLamp(dev.mcmod.aerocore.api.mesh.Lamp.lens(STAIR_WINDOWS, 0, f.at(4.5, (3 + Math.max(top, 3.5)) / 2, 4.5), dir(0, 1, 0), 0.4, WARM, -1));
            // the downlights' lenses
            m.lamp(DOWNLIGHTS);
            for (double[] d : DOWNLIGHT_AT) {
                double y = g1 - 0.045;
                quad(m, new double[]{d[0] - 0.11, y, d[1] - 0.11}, new double[]{d[0] + 0.11, y, d[1] - 0.11}, new double[]{d[0] + 0.11, y, d[1] + 0.11},
                        new double[]{d[0] - 0.11, y, d[1] + 0.11}, new double[]{0, -1, 0}, DOWNLIGHT);
            }
            m.addLamp(dev.mcmod.aerocore.api.mesh.Lamp.lens(DOWNLIGHTS, 0, f.at(4.5, g1 - 0.05, 4.5), dir(0, -1, 0), 0.12, DOWNLIGHT, -1));
            // the screens: the consoles' and the desk's glass
            PartMesh furniture = new PartMesh();
            if (k.console()) { kit(furniture, AirfieldKit.Part.CAB_CONSOLE, 4.5, f1, 1.9, 0, true, FASCIA); }
            for (double[] c : SIDE_CONSOLES) { kit(furniture, AirfieldKit.Part.CAB_CONSOLE_SIDE, c[0], f1, c[1], c[2], true, FASCIA); }
            kit(furniture, AirfieldKit.Part.SUPERVISOR_DESK, 1.9, f1, 6.9, 0, true, FASCIA);
            int screen = m.lamp(SCREENS).tags();
            for (MeshBuilder.Quad q : furniture.quads()) {
                if (q.finish() == Finishes.GLASS) { m.quads().add(q.withTags((q.tags() & ~(MeshBuilder.LAMP | MeshBuilder.LAMP_ID)) | (screen & (MeshBuilder.LAMP | MeshBuilder.LAMP_ID)))); }
            }
            m.addLamp(dev.mcmod.aerocore.api.mesh.Lamp.lens(SCREENS, 0, f.at(4.5, f1 + 1.1, 2.5), dir(0, 0, -1), 0.3, SCREEN, -1));
            // the cab's ceiling, lit by its downlights (dim: a control room is kept dark to see out), seen from below through the glass
            m.lamp(CAB_CEILING);
            octagonFace(m, octagon(-0.06, g1 - 0.03), false, CEILING_GLOW);
            m.addLamp(dev.mcmod.aerocore.api.mesh.Lamp.lens(CAB_CEILING, 0, f.at(4.5, g1 - 0.03, 4.5), dir(0, -1, 0), 0.5, CEILING_GLOW, -1));
            m.lamp(-1).finish(null);
        }

        private static double[] lerp(double[] a, double[] b, double t) {
            return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
        }

        /** The cab's floor at a height, its top or underside: the octagon less the hatch (x and z 3 to 6), as six quads. */
        private void floorWithHatch(PartMesh m, double y, boolean up, int colour) {
            double[] n = {0, up ? 1 : -1, 0};
            quad(m, p(2, y, 0), p(7, y, 0), p(9, y, 2), p(0, y, 2), n, colour);
            quad(m, p(0, y, 2), p(9, y, 2), p(9, y, 3), p(0, y, 3), n, colour);
            quad(m, p(0, y, 3), p(3, y, 3), p(3, y, 6), p(0, y, 6), n, colour);
            quad(m, p(6, y, 3), p(9, y, 3), p(9, y, 6), p(6, y, 6), n, colour);
            quad(m, p(0, y, 6), p(9, y, 6), p(9, y, 7), p(0, y, 7), n, colour);
            quad(m, p(0, y, 7), p(9, y, 7), p(7, y, 9), p(2, y, 9), n, colour);
        }

        private static double[] p(double x, double y, double z) { return new double[]{x, y, z}; }

        /** Railing segments a metre long round an outline (each edge as many as fit, stretched to fill it). */
        private void railings(PartMesh m, double[][] o) {
            for (int i = 0; i < o.length; i++) {
                double[] a = o[i], b = o[(i + 1) % o.length];
                double dx = b[0] - a[0], dz = b[2] - a[2], len = Math.hypot(dx, dz);
                int count = Math.max(1, (int) Math.round(len));
                // the railing's +x (kit) runs to the frame's -x at yaw 0, and a world turn is the opposite turn in the
                // (mirrored) frame: this yaw lays it along the edge
                double yaw = Math.atan2(-dz, -dx);
                for (int s = 0; s < count; s++) {
                    double t = (double) s / count;
                    Vec3d scale = new Vec3d(len / count, 1, 1);
                    railing(m, a[0] + dx * t, a[1], a[2] + dz * t, yaw, scale);
                }
            }
        }

        private void railing(PartMesh m, double x, double y, double z, double yaw, Vec3d scale) {
            Vec3d kx = turn(right.scale(-1), yaw);
            for (boolean fine : new boolean[]{true, false}) {
                Mesh placed = MeshOps.placed(AirfieldKit.mesh(AirfieldKit.Part.RAILING, fine, RAIL), scale, Details.basis(new Vec3d(0, 1, 0), kx), f.at(x, y, z));
                int levels = fine ? new PartMesh().levels(0, 0).tags() : new PartMesh().levels(1, 1).tags();
                int mask = MeshBuilder.NEAR | MeshBuilder.DETAIL;
                for (MeshBuilder.Quad q : placed.quads()) { m.quads().add(q.withTags((q.tags() & ~mask) | (levels & mask))); }
            }
        }

        /** The far stand-in: the base and shaft as boxes, the cab as an octagonal drum, about forty faces. */
        private void standIn(PartMesh m) {
            m.finish(Finishes.BOARD_FORMED_CONCRETE).tag(MeshBuilder.STAND_IN, true);
            box(m, 1, 0, 1, 8, 2, 8, CONCRETE);
            box(m, 2, 2, 2, 7, f0, 7, CONCRETE);
            double[][] lo = octagon(0, f0), hi = octagon(0, g1);
            m.finish(Finishes.TOWER_GLASS);
            band(m, lo, hi, true, 0xff6f8a9a);
            m.finish(Finishes.PAINT);
            octagonFace(m, lo, false, CONCRETE);
            double[][] roofLo = octagon(0.55, g1), roofHi = octagon(0.55, r1);
            band(m, roofLo, roofHi, true, ROOF);
            octagonFace(m, roofHi, true, ROOF);
            m.tag(MeshBuilder.STAND_IN, false);
        }

        // ---------------------------------------------------------------- what moves

        void moving(PartMesh m, double radar, double cups, boolean fine) {
            if (k.radar()) {
                m.finish(null);
                kit(m, AirfieldKit.Part.RADAR_ANTENNA, 4.5, r1 + AirfieldKit.PEDESTAL_TOP, 4.5, radar, fine, RADAR);
            }
            if (k.mast()) {
                // the cups on the mast's +x arm (the kit's +x is the frame's -x)
                kit(m, AirfieldKit.Part.ANEMOMETER_CUPS, 2.5 - AirfieldKit.CUPS_X, r1 + AirfieldKit.CUPS_Y, 2.5, cups, fine, 0xff2d2f33);
            }
        }
    }

    static Vec3d turn(Vec3d v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(v.x() * c + v.z() * s, v.y(), -v.x() * s + v.z() * c);
    }
}
