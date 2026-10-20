package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.hdfv.neoforge.multiblock.Multiblocks;
import dev.mcmod.hdfv.neoforge.petroleum.PumpJackBlockEntity;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A pump jack drawn whole (0.8) over its formed members: the skid, the samson post, the walking beam rocking on its
 * bearing, the horsehead and the bridle down to the polished rod in the wellhead, the cranks and counterweights turning
 * at its strokes a minute, the pitman arms between them; still unless the server says it pumps.
 * <p>
 * 0.12: the pattern is matched again every second (it was every frame); the skid, post and bearings are baked once a
 * facing, the beam, horsehead, rod, cranks, weights and pitmans built each frame (MachineMeshes); the cranks turn at the
 * strokes a minute the jack is set to (synchronised with the pumping), not the nominal ones.
 */
public final class PumpJackRenderer implements BlockEntityRenderer<PumpJackBlockEntity> {
    private static final int SKID = 0xff3a3d40, POST = 0xff5a6066, BEAM = 0xff2f6f3f, HEAD = 0xff2f6f3f, WEIGHT = 0xff8a2f2a, ROD = 0xffc9ced3, PITMAN = 0xff3a3d40;
    private static final double PX = 2.5, PY = 2.75, Z = 0.5, CX = 3.95, CY = 1.25, CR = 0.45;

    public PumpJackRenderer(BlockEntityRendererProvider.Context c) {}

    private static final class Seen { Pattern.Match match; long at = Long.MIN_VALUE / 2; double crank; long drawnNanos; }
    private static final Map<PumpJackBlockEntity, Seen> SEEN = new WeakHashMap<>();

    @Override public void render(PumpJackBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) { return; }
        var pos = be.getBlockPos();
        int facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue();
        Seen s = SEEN.computeIfAbsent(be, b -> new Seen());
        long now = be.getLevel().getGameTime();
        if (now - s.at >= 20 || now < s.at) {
            s.at = now;
            s.match = Structures.PUMP_JACK_PATTERN.match(Multiblocks.world(be.getLevel()), pos.getX(), pos.getY(), pos.getZ(), facing);
        }
        if (s.match == null) { return; }
        // the crank turned at the strokes shown since the last frame (a change of speed does not jump it)
        long nanos = System.nanoTime();
        double dt = s.drawnNanos == 0 ? 0 : Math.min(0.1, (nanos - s.drawnNanos) * 1e-9);
        s.drawnNanos = nanos;
        if (be.shownPumping()) { s.crank = (s.crank + 2 * Math.PI * be.shownSpm() / 60 * dt) % (2 * Math.PI); }
        PatternFrame f = new PatternFrame(facing, 4, 0, 0);
        String key = "pump_jack:" + facing;
        if (!be.shownPumping()) {
            // 0.12: stopped: the beam and cranks where they stopped, built and baked once (the angle to a hundredth of a radian)
            double crank = Math.round(s.crank * 100) / 100.0;
            MachineMeshes.drawResting(key, () -> fixed(f), key + ":" + crank, () -> { PartMesh still = new PartMesh(); moving(f, still, crank); return still; }, pos, pose, buffers, light);
            return;
        }
        PartMesh moving = new PartMesh();
        moving(f, moving, s.crank);
        MachineMeshes.draw(key, () -> fixed(f), moving, pos, pose, buffers, light);
    }

    /** The skid, the samson post's A-frame and the beam's bearing, the gearbox shaft. */
    private static PartMesh fixed(PatternFrame f) {
        PartMesh mesh = new PartMesh();
        f.box(mesh, 0.9, 0, 0.2, 4.0, 0.3, 0.8, SKID);
        for (double side : new double[]{0.22, 0.78}) {
            f.bar(mesh, 1.9, 0.3, side, PX, PY - 0.05, Z, 0.05, POST);
            f.bar(mesh, 3.1, 0.3, side, PX, PY - 0.05, Z, 0.05, POST);
        }
        f.cylinder(mesh, PX, PY, 0.3, PX, PY, 0.7, 0.07, POST);
        f.cylinder(mesh, CX, CY, 0.05, CX, CY, 0.95, 0.06, POST);
        return mesh;
    }

    /** The walking beam rocking about its bearing with the horsehead and rod, the cranks, weights and pitman arms at a crank angle. */
    private static void moving(PatternFrame f, PartMesh mesh, double crank) {
        double rock = 0.22 * Math.sin(crank);
        double lx = 0.45 - PX, rx = 3.55 - PX;
        double hx = PX + lx * Math.cos(rock), hy = PY + lx * Math.sin(rock), tx = PX + rx * Math.cos(rock), ty = PY + rx * Math.sin(rock);
        f.bar(mesh, hx, hy + 0.08, Z, tx, ty + 0.08, Z, 0.09, BEAM);
        // the horsehead: an arc of plates from the beam's end down
        for (int k = 0; k < 5; k++) {
            double a = rock + Math.PI + (k - 2) * 0.12, r = 0.55;
            double x0 = hx + r * Math.cos(a) * 0.4, y0 = hy + r * Math.sin(a) + 0.1;
            f.box(mesh, x0 - 0.08, y0 - 0.12, Z - 0.12, x0 + 0.02, y0 + 0.12, Z + 0.12, HEAD);
        }
        // bridle and polished rod down into the wellhead (its top at about 0.85 m)
        f.cylinder(mesh, 0.5, hy - 0.45, Z, 0.5, 0.8, Z, 0.025, ROD);
        double pinX = CX + CR * Math.cos(crank), pinY = CY + CR * Math.sin(crank);
        for (double side : new double[]{0.12, 0.88}) {
            f.bar(mesh, CX, CY, side, pinX, pinY, side, 0.06, PITMAN);
            double wx = CX - 0.6 * CR * Math.cos(crank), wy = CY - 0.6 * CR * Math.sin(crank);
            f.box(mesh, wx - 0.22, wy - 0.22, side - 0.06, wx + 0.22, wy + 0.22, side + 0.06, WEIGHT);
            f.bar(mesh, pinX, pinY, side, tx, ty, side, 0.04, PITMAN);
        }
        f.cylinder(mesh, tx, ty, 0.08, tx, ty, 0.92, 0.04, PITMAN);
    }

    @Override public boolean shouldRenderOffScreen(PumpJackBlockEntity be) { return true; }

    @Override public AABB getRenderBoundingBox(PumpJackBlockEntity be) { return new AABB(be.getBlockPos()).inflate(5); }
}
