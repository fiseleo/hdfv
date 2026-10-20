package dev.mcmod.hdfv.neoforge.client.model;

import dev.mcmod.aerocore.api.mesh.Finish;
import dev.mcmod.aerocore.api.mesh.Mesh;
import dev.mcmod.aerocore.api.mesh.ObjReader;
import dev.mcmod.hdfv.look.Finishes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The machining kit (0.17): the detailed parts of the machine families made in Blender (tools/blender/machining_kit.py)
 * as OBJ in assets/hdfv/models/part/machine, at their real size (metres), Blender's +y (the operator's side) as -z,
 * standing on y = 0 or, if they turn, centred on their axis (see each part's docstring there). Read like DetailKit;
 * the {@code paint} finish takes the machine's own colour, so a part is kept for each colour it is drawn in.
 */
public final class MachineKit {
    private MachineKit() {}

    /** The parts. */
    public enum Part {
        CONTROL_PENDANT, NC_CABINET, MOTOR, COOLANT_NOZZLE, GUARD_WINDOW, WHEELHEAD, GRINDING_WHEEL, REGULATING_WHEEL, CENTRELESS_FRAME,
        INTERNAL_SPINDLE, MAGNETIC_CHUCK, CENTRE_STOCK, DIAMOND_DRESSER, TOOL_GRINDER_HEAD, HONING_HEAD, HONING_TOOL, LAPPING_PLATE, LAP_RING,
        POLISHING_HEAD, BUFF_WHEEL, VIBRATORY_BOWL, HOB_SLIDE, HOB, SHAPER_CUTTER, GEAR_WORK, SHAPER_RAM, SLOTTER_RAM, BROACH, BAND_SAW_BOW,
        CIRCULAR_SAW_BLADE, COLD_SAW_HEAD, HACKSAW_FRAME, BAR_VISE, EDM_QUILL, EDM_RAM, WIRE_HEAD, HOLE_EDM_HEAD, ECM_CELL, GANTRY_BEAM,
        LASER_HEAD, PLASMA_TORCH, WATERJET_HEAD, FUME_HOOD, THREAD_ROLLING_HEAD, THREAD_ROLL, FLAT_DIE, TAPPING_ARM, C_FRAME_RIVETER,
        RIVET_RAM, RIVET_GUN, DRILL_RIVET_HEAD;

        /** The OBJ's name under models/part/machine. */
        public String file() { return name().toLowerCase(Locale.ROOT); }
    }

    /** The colour each finish is drawn in (as DetailKit; paint is the machine's). */
    private static final Map<String, Integer> COLOURS = Map.ofEntries(
            Map.entry("machined_steel", 0xffb3b8be), Map.entry("stainless", 0xffaeb2b7), Map.entry("forged_steel", 0xff6f757d),
            Map.entry("brass", 0xffc8a46a), Map.entry("copper", 0xffb87333), Map.entry("rubber", 0xff1c1d1f), Map.entry("plastic", 0xff2d2f33),
            Map.entry("ceramic", 0xffefefef), Map.entry("cast_iron", 0xff3a3c3f), Map.entry("braided_hose", 0xffbfc3c7),
            Map.entry("graphite", 0xff2a2a2c), Map.entry("fabric", 0xffe8e2d0), Map.entry("glass", 0xff9fb8c4), Map.entry("chrome", 0xffe2e5e8),
            Map.entry("dash_plastic", 0xff27292c), Map.entry("lamp_clear", 0xfff0f3f6), Map.entry("lamp_red", 0xffc2181c),
            Map.entry("lamp_amber", 0xffe58c12));

    private static final Map<String, Mesh> CACHE = new HashMap<>();

    /** A part's fine or coarse mesh in a machine's paint (shared: copy before changing it). */
    public static synchronized Mesh mesh(Part part, boolean fine, int paint) {
        String key = part.file() + (fine ? "/lod0/" : "/lod1/") + Integer.toHexString(paint);
        Mesh m = CACHE.get(key);
        if (m != null) { return m; }
        ObjReader.Result r = read(part.file(), paint);
        String base = part.file();
        CACHE.put(base + "/lod0/" + Integer.toHexString(paint), r.group("lod0"));
        CACHE.put(base + "/lod1/" + Integer.toHexString(paint), r.group("lod1"));
        return CACHE.get(key);
    }

    private static ObjReader.Result read(String file, int paint) {
        String path = "/assets/hdfv/models/part/machine/" + file + ".obj";
        try (InputStream in = MachineKit.class.getResourceAsStream(path)) {
            if (in == null) { throw new IllegalStateException("missing machine part " + path); }
            return ObjReader.read(new InputStreamReader(in, StandardCharsets.UTF_8), name -> {
                Finish f = Finishes.ALL.stream().filter(x -> x.id().equals("hdfv:" + name)).findFirst().orElse(Finishes.MACHINED_STEEL);
                return new ObjReader.Material(f, "paint".equals(name) ? paint : COLOURS.getOrDefault(name, 0xffa0a4aa));
            }, 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
