package dev.mcmod.hdfv.neoforge.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mcmod.aerocore.api.mesh.Finish;
import dev.mcmod.aerocore.api.mesh.MeshBuilder;
import dev.mcmod.aerocore.api.vehicle.Part;
import dev.mcmod.aerocore.neoforge.client.MeshBakery;
import dev.mcmod.aerocore.neoforge.client.PartLayers;
import dev.mcmod.aerocore.neoforge.client.PartMesh;
import dev.mcmod.aerocore.neoforge.client.PartRenderer;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.look.Finishes;
import dev.mcmod.hdfv.part.engine.EngineMaterial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which finish a model's quad is drawn with (0.11), from the colour it was built in: every engine model colours its
 * pieces from a few named colours (materials, rubber, pipes, paint) through one helper, so the base colour says what a
 * piece is. Quads built with a finish pen keep theirs; the rest get the finish of their colour, the nearest known
 * colour's within a small distance (shades mixed toward it), or one guessed from brightness and saturation. The
 * design screen's highlight is drawn as paint.
 */
public final class Looks {
    private Looks() {}

    /**
     * 0.22: whether beautified shading is on (AeroCore 0.39's client setting): the models' looks that are shading, not
     * shape (the glass's tint and opacity), follow it; meshes are baked again when it flips.
     */
    public static boolean beautify() { return dev.mcmod.aerocore.neoforge.client.Beautify.on(); }

    private static final Map<Integer, Finish> BY_COLOUR = new LinkedHashMap<>();

    private static void put(Finish f, int... colours) { for (int c : colours) { BY_COLOUR.put(c & 0xffffff, f); } }

    static {
        for (EngineMaterial m : EngineMaterial.STANDARD) {
            int c = Shapes.material(m);
            String n = m.name();
            Finish f = n.startsWith("aluminium") || n.startsWith("magnesium") ? Finishes.CAST_ALUMINIUM
                    : n.endsWith("_iron") ? Finishes.CAST_IRON
                    : n.startsWith("titanium") ? Finishes.TITANIUM
                    : n.startsWith("stainless") ? Finishes.STAINLESS
                    : n.startsWith("inconel") ? Finishes.INCONEL
                    : n.equals("cmsx_4") ? Finishes.SINGLE_CRYSTAL
                    : n.equals("valve_steel") ? Finishes.MACHINED_STEEL
                    : n.startsWith("steel") ? Finishes.FORGED_STEEL
                    : n.equals("cfrp") ? Finishes.CARBON_WEAVE
                    : n.equals("gfrp") ? Finishes.GFRP
                    : n.equals("spruce") ? Finishes.PAINT : Finishes.BRUSHED_STEEL;
            BY_COLOUR.putIfAbsent(c & 0xffffff, f);
            BY_COLOUR.putIfAbsent(Shapes.machined(c) & 0xffffff, Finishes.MACHINED_STEEL);
        }
        // piston engines
        put(Finishes.RUBBER, 0xff1c1d1f, 0xff34363a);
        put(Finishes.PLASTIC, 0xff26282b, 0xff2d2f33, 0xff1f2124, 0xff1b1c1e);
        put(Finishes.PAINT, 0xff8e2323, 0xff2a5caa, 0xffa0282a, 0xffd8b23a, 0xff2c4f8a);
        put(Finishes.MACHINED_STEEL, 0xff5d636b, 0xff3c4046);
        put(Finishes.CERAMIC, 0xffefefef);
        put(Finishes.FORGED_STEEL, 0xff4a4f55, 0xff3a3f45);
        put(Finishes.GRAPHITE, 0xff9a968e);
        put(Finishes.BRUSHED_STEEL, 0xff3d4249);
        // gas turbines and the ramjet family
        put(Finishes.PAINT, 0xff2e3135, 0xffd7dadd, 0xffc23a2e);
        put(Finishes.FORGED_STEEL, 0xff6d737b);
        put(Finishes.BRASS, 0xffb08d57);
        put(Finishes.CAST_ALUMINIUM, 0xff4a4d52);
        put(Finishes.BRUSHED_STEEL, 0xff55595e);
        put(Finishes.PROPELLANT, 0xffe3c99a, 0xffc7b8a0);
        put(Finishes.RUBBER, 0xff3a3436);
        // heat tint of an exhaust
        for (double t = 0; t <= 1.0001; t += 0.05) { put(Finishes.INCONEL, Shapes.heat(t)); }
        put(Finishes.PAINT, Shapes.HIGHLIGHT);
    }

    /** 0.12: the finishes found for colours not in BY_COLOUR (the search below, done once a colour). */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Finish> NEAREST = new java.util.concurrent.ConcurrentHashMap<>();

    /** The finish of a colour (alpha ignored). */
    public static Finish of(int argb) {
        int rgb = argb & 0xffffff;
        Finish exact = BY_COLOUR.get(rgb);
        if (exact != null) { return exact; }
        return NEAREST.computeIfAbsent(rgb, Looks::nearest);
    }

    /** The finish of the nearest listed colour, else one by brightness and saturation. */
    private static Finish nearest(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        Finish best = null;
        int bestDistance = 14 * 14 * 3;
        for (Map.Entry<Integer, Finish> e : BY_COLOUR.entrySet()) {
            int k = e.getKey(), dr = r - ((k >> 16) & 255), dg = g - ((k >> 8) & 255), db = b - (k & 255);
            int d = dr * dr + dg * dg + db * db;
            if (d < bestDistance) { bestDistance = d; best = e.getValue(); }
        }
        if (best != null) { return best; }
        double max = Math.max(r, Math.max(g, b)) / 255.0, min = Math.min(r, Math.min(g, b)) / 255.0;
        double luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0, saturation = max <= 0 ? 0 : (max - min) / max;
        if (luma < 0.16) { return Finishes.PLASTIC; }
        if (saturation > 0.35) { return Finishes.PAINT; }
        if (luma > 0.78) { return Finishes.MACHINED_STEEL; }
        return Finishes.BRUSHED_STEEL;
    }

    /** A mesh's quads without a finish given the finish of their colour (in place); returns the mesh. */
    public static <M extends MeshBuilder<M>> M finish(M mesh) {
        List<MeshBuilder.Quad> quads = mesh.quads();
        for (int i = 0; i < quads.size(); i++) {
            MeshBuilder.Quad q = quads.get(i);
            if (q.finish() == null) { quads.set(i, q.withFinish(of(q.color()))); }
        }
        return mesh;
    }

    /** Registers an hdfv part renderer whose static mesh gets finishes by colour (see finish). */
    public static void register(Class<?> partOrDesign, PartRenderer renderer) { PartRenderer.register(partOrDesign, new Finished(renderer)); }

    /** A renderer whose static mesh is finished by colour after it is built; everything else as the renderer it wraps. */
    private record Finished(PartRenderer inner) implements PartRenderer {
        @Override public void mesh(Part part, PartMesh out) { inner.mesh(part, out); finish(out); }

        @Override public MeshBakery.Spec bake(Part part) { return inner.bake(part); }

        @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, VertexConsumer out, int light) {
            inner.animate(part, entity, partId, firstRotor, partialTick, stack, out, light);
        }

        @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light) {
            inner.animate(part, entity, partId, firstRotor, partialTick, stack, layers, light);
        }

        @Override public void animate(Part part, PartVehicle entity, String partId, int firstRotor, float partialTick, PoseStack stack, PartLayers layers, int light,
                                      PartRenderer.Drawn drawn) {
            inner.animate(part, entity, partId, firstRotor, partialTick, stack, layers, light, drawn);
        }

        @Override public void lamps(Part part, PartVehicle entity, String partId, float partialTick, List<dev.mcmod.aerocore.api.mesh.Lamp> lamps, float[] level) {
            inner.lamps(part, entity, partId, partialTick, lamps, level);
        }

        @Override public int glow(Part part, PartVehicle entity, String partId, float partialTick) { return inner.glow(part, entity, partId, partialTick); }
    }
}
