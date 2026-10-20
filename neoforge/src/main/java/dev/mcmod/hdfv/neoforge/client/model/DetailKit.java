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
import java.util.Map;

/**
 * hdfv's detail kit (0.11): small parts made in Blender (tools/blender/details.py) as OBJ in
 * assets/hdfv/models/part/detail, each at a reference size (a bolt is M10), standing on y = 0 with its axis along +y,
 * a fine object (lod0) and a coarse one (lod1). Read from the mod's own files (models are built off the render thread)
 * and kept. Materials are finishes; their colours here (the finish textures are neutral detail maps).
 *
 * <p>0.12 adds two kits in their own folders, made the same way: the vehicle kit (tools/blender/vehicle.py: lamps,
 * mirrors, wipers, handles, a damper...) and the interior kit (tools/blender/interior.py: seats, wheels and yokes,
 * pedals, levers, gauges). A vehicle part's {@code paint} is the body's colour, which the code puts on it; lamp lenses
 * are the lamp_* finishes, so the code can find and tag them.
 */
public final class DetailKit {
    private DetailKit() {}

    /** A kit part and the size its reference is (metres: a bolt's thread, a clamp's tube, an actuator's length...). */
    public enum Part {
        HEX_BOLT("hex_bolt", 0.010), HEX_NUT("hex_nut", 0.010), SOCKET_CAP("socket_cap", 0.010), P_CLAMP("p_clamp", 0.010),
        HOSE_CLAMP("hose_clamp", 0.025), AN_FITTING("an_fitting", 0.0095), HOSE_END("hose_end", 0.013), CONNECTOR("connector", 0.019),
        IGNITER("igniter", 0.022), SPARK_PLUG("spark_plug", 0.014), SENSOR("sensor", 0.022), THERMOCOUPLE("thermocouple", 0.008),
        ACTUATOR("actuator", 1.0), ROD_END("rod_end", 0.010), NOZZLE_PETAL("nozzle_petal", 1.0), HINGE("hinge", 0.06),
        RIVET("rivet", 0.005), LIFTING_EYE("lifting_eye", 0.06), MOUNT_LUG("mount_lug", 0.06), SOLENOID("solenoid", 0.045),
        DATA_PLATE("data_plate", 0.08),
        // the vehicle kit (0.12): sizes are a lamp's width, a mirror's housing, a wiper's or a damper's length...
        HEADLAMP_ROUND("vehicle", "headlamp_round", 0.18), HEADLAMP_PROJECTOR("vehicle", "headlamp_projector", 0.26),
        TAIL_LAMP("vehicle", "tail_lamp", 0.28), NAV_LIGHT("vehicle", "nav_light", 0.12), BEACON("vehicle", "beacon", 0.12),
        DOOR_MIRROR("vehicle", "door_mirror", 0.22), WIPER("vehicle", "wiper", 0.55), DOOR_HANDLE("vehicle", "door_handle", 0.13),
        FUEL_FLAP("vehicle", "fuel_flap", 0.10), ANTENNA("vehicle", "antenna", 0.6), PITOT("vehicle", "pitot", 0.15),
        CLEAT("vehicle", "cleat", 0.2), PLATE_FRAME("vehicle", "plate_frame", 0.54), EXHAUST_TIP("vehicle", "exhaust_tip", 0.07),
        DAMPER_BODY("vehicle", "damper_body", 1.0), DAMPER_ROD("vehicle", "damper_rod", 1.0),
        // the interior kit (0.12): a seat's width, a wheel's or a gauge's diameter, a lever's length
        CAR_SEAT("interior", "car_seat", 0.5), BENCH_SEAT("interior", "bench_seat", 1.3), AIRCRAFT_SEAT("interior", "aircraft_seat", 0.48),
        CABIN_SEAT("interior", "cabin_seat", 0.45), STEERING_WHEEL("interior", "steering_wheel", 0.38), YOKE("interior", "yoke", 0.30),
        STICK("interior", "stick", 0.44), COLLECTIVE("interior", "collective", 0.58), PEDAL("interior", "pedal", 0.28),
        RUDDER_PEDALS("interior", "rudder_pedals", 0.24), GEAR_LEVER("interior", "gear_lever", 0.21),
        THROTTLE_QUADRANT("interior", "throttle_quadrant", 0.12), HELM_WHEEL("interior", "helm_wheel", 0.40),
        GAUGE("interior", "gauge", 0.08), BINNACLE("interior", "binnacle", 0.32);

        /** The OBJ under assets/hdfv/models/part, without its extension (folder/name). */
        final String file;
        /** The reference size: a placement's size over it is the scale. */
        public final double size;

        Part(String file, double size) { this("detail", file, size); }

        Part(String folder, String file, double size) { this.file = folder + "/" + file; this.size = size; }
    }

    /** The colour each finish in the kit is drawn in. */
    private static final Map<String, Integer> COLOURS = Map.ofEntries(
            Map.entry("machined_steel", 0xffb3b8be), Map.entry("stainless", 0xffaeb2b7), Map.entry("forged_steel", 0xff6f757d),
            Map.entry("brass", 0xffc8a46a), Map.entry("copper", 0xffb87333), Map.entry("rubber", 0xff1c1d1f), Map.entry("plastic", 0xff2d2f33),
            Map.entry("ceramic", 0xffefefef), Map.entry("cast_aluminium", 0xffb9bdc2), Map.entry("paint", 0xff3a4552),
            Map.entry("braided_hose", 0xffbfc3c7), Map.entry("inconel", 0xff8a8178), Map.entry("cast_iron", 0xff2c2d2f),
            // 0.12: the vehicle and interior kits
            Map.entry("chrome", 0xffe2e5e8), Map.entry("tyre", 0xff1b1c1e), Map.entry("leather", 0xff2e2723), Map.entry("fabric", 0xff3b3e44),
            Map.entry("carpet", 0xff2a2b2e), Map.entry("dash_plastic", 0xff27292c), Map.entry("teak", 0xff9b6b3d),
            Map.entry("lamp_clear", 0xfff0f3f6), Map.entry("lamp_red", 0xffc2181c), Map.entry("lamp_amber", 0xffe58c12));

    private static final Map<String, Mesh> CACHE = new HashMap<>();

    /** A part's fine or coarse mesh (shared: copy before changing it). */
    public static synchronized Mesh mesh(Part part, boolean fine) {
        String key = part.file + (fine ? "/lod0" : "/lod1");
        Mesh m = CACHE.get(key);
        if (m != null) { return m; }
        ObjReader.Result r = read(part.file);
        CACHE.put(part.file + "/lod0", r.group("lod0"));
        CACHE.put(part.file + "/lod1", r.group("lod1"));
        return CACHE.get(key);
    }

    private static ObjReader.Result read(String file) {
        String path = "/assets/hdfv/models/part/" + file + ".obj";
        try (InputStream in = DetailKit.class.getResourceAsStream(path)) {
            if (in == null) { throw new IllegalStateException("missing detail part " + path); }
            return ObjReader.read(new InputStreamReader(in, StandardCharsets.UTF_8), name -> {
                Finish f = Finishes.ALL.stream().filter(x -> x.id().equals("hdfv:" + name)).findFirst().orElse(Finishes.MACHINED_STEEL);
                return new ObjReader.Material(f, COLOURS.getOrDefault(name, 0xffa0a4aa));
            }, 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
