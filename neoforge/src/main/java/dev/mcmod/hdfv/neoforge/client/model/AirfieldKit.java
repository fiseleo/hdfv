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
 * The airfield kit (0.20): the control tower's and windsock's detailed parts made in Blender
 * (tools/blender/airfield_kit.py) as OBJ in assets/hdfv/models/part/airfield, at their real size (metres), Blender's +y
 * (the tower's front) as -z. Read as MachineKit; the {@code paint} finish takes the colour the renderer gives.
 */
public final class AirfieldKit {
    private AirfieldKit() {}

    /** The parts. */
    public enum Part {
        CAB_CONSOLE, RADAR_ANTENNA, RADAR_PEDESTAL, ANEMOMETER_MAST, ANEMOMETER_CUPS, ENTRANCE_CANOPY, RAILING, WINDSOCK_HEAD,
        // 0.21: inside the tower
        CAB_CONSOLE_SIDE, SUPERVISOR_DESK, CEILING_LIGHT, DISTRIBUTION_BOARD, CABLE_TRAY, NOTICE_BOARD, WALL_CLOCK;

        public String file() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Where the windsock's hoop centre is from its head's swivel (m, the head's frame: x along the sock). */
    public static final double HOOP_X = 0.3, HOOP_Y = -0.12, HOOP_R = 0.36;
    /** The radar pedestal's top (where the antenna's joint sits) and the anemometer cups' hub on the mast (m). */
    public static final double PEDESTAL_TOP = 0.9, CUPS_X = 0.6, CUPS_Y = 3.55;

    private static final Map<String, Integer> COLOURS = Map.ofEntries(
            Map.entry("machined_steel", 0xffb3b8be), Map.entry("brushed_steel", 0xffa9aeb4), Map.entry("stainless", 0xffaeb2b7),
            Map.entry("rubber", 0xff1c1d1f), Map.entry("plastic", 0xff2d2f33), Map.entry("glass", 0xff20384a), Map.entry("chrome", 0xffe2e5e8),
            Map.entry("dash_plastic", 0xff5f646a), Map.entry("fabric", 0xff2c3442), Map.entry("lamp_clear", 0xfff0f3f6), Map.entry("lamp_red", 0xffc2181c),
            Map.entry("leather", 0xffa8794e), Map.entry("paint_matte", 0xffeeeee8));

    private static final Map<String, Mesh> CACHE = new HashMap<>();

    /** A part's fine or coarse mesh with its paint in a colour (shared: copy before changing it). */
    public static synchronized Mesh mesh(Part part, boolean fine, int paint) {
        String key = part.file() + (fine ? "/lod0/" : "/lod1/") + Integer.toHexString(paint);
        Mesh m = CACHE.get(key);
        if (m != null) { return m; }
        ObjReader.Result r = read(part.file(), paint);
        CACHE.put(part.file() + "/lod0/" + Integer.toHexString(paint), r.group("lod0"));
        CACHE.put(part.file() + "/lod1/" + Integer.toHexString(paint), r.group("lod1"));
        return CACHE.get(key);
    }

    private static ObjReader.Result read(String file, int paint) {
        String path = "/assets/hdfv/models/part/airfield/" + file + ".obj";
        try (InputStream in = AirfieldKit.class.getResourceAsStream(path)) {
            if (in == null) { throw new IllegalStateException("missing airfield part " + path); }
            return ObjReader.read(new InputStreamReader(in, StandardCharsets.UTF_8), name -> {
                Finish f = Finishes.ALL.stream().filter(x -> x.id().equals("hdfv:" + name)).findFirst().orElse(Finishes.MACHINED_STEEL);
                return new ObjReader.Material(f, "paint".equals(name) ? paint : COLOURS.getOrDefault(name, 0xffa0a4aa));
            }, 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
