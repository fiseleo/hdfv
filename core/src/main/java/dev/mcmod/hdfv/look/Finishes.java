package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.mesh.Finish;

import java.util.List;

/**
 * hdfv's surface finishes (0.11): the keys mesh code builds with (MeshBuilder.finish) and the length each texture
 * repeats over. The textures are baked in Blender (tools/blender/textures.py from finishes_spec.json, which has the
 * same ids and tiles) into assets/hdfv/textures/part, listed in assets/hdfv/finishes.json with gloss and metalness;
 * FinishAssetsTest keeps the three in step. A texture is a neutral detail map: the part's vertex colour gives its
 * colour.
 */
public final class Finishes {
    private Finishes() {}

    public static final Finish BRUSHED_STEEL = of("brushed_steel", 0.2), STAINLESS = of("stainless", 0.15), MACHINED_STEEL = of("machined_steel", 0.08),
            CAST_IRON = of("cast_iron", 0.3), CAST_ALUMINIUM = of("cast_aluminium", 0.3), FORGED_STEEL = of("forged_steel", 0.25),
            TITANIUM = of("titanium", 0.2), INCONEL = of("inconel", 0.2), SINGLE_CRYSTAL = of("single_crystal", 0.05),
            COPPER = of("copper", 0.15), BRASS = of("brass", 0.15), BRONZE = of("bronze", 0.2),
            CARBON_WEAVE = of("carbon_weave", 0.03), GFRP = of("gfrp", 0.15), RUBBER = of("rubber", 0.08),
            PAINT = of("paint", 0.5), PAINT_WORN = of("paint_worn", 0.4), GRAPHITE = of("graphite", 0.06),
            CERAMIC = of("ceramic", 0.1), BRAIDED_HOSE = of("braided_hose", 0.012), PLASTIC = of("plastic", 0.1),
            PROPELLANT = of("propellant", 0.06), GLASS = of("glass", 0.5);

    /**
     * 0.12, vehicles: lacquered, metallic, pearl and matte paints; chrome; tyres; seats and trim (leather, fabric,
     * carpet, a dashboard's grained plastic); a boat's teak, antifouling and non-skid deck; an aircraft's aluminium
     * skin; lamp lenses (their colour from the vertex colour, as every finish's).
     */
    public static final Finish PAINT_GLOSS = of("paint_gloss", 0.5), PAINT_METALLIC = of("paint_metallic", 0.25), PAINT_PEARL = of("paint_pearl", 0.25),
            PAINT_MATTE = of("paint_matte", 0.5), CHROME = of("chrome", 0.3), TYRE = of("tyre", 0.05), LEATHER = of("leather", 0.12),
            FABRIC = of("fabric", 0.02), CARPET = of("carpet", 0.05), DASH_PLASTIC = of("dash_plastic", 0.04), TEAK = of("teak", 1.0),
            ANTIFOULING = of("antifouling", 0.3), NONSKID = of("nonskid", 0.1), ALUMINIUM_SKIN = of("aluminium_skin", 1.0),
            LAMP_CLEAR = of("lamp_clear", 0.1), LAMP_RED = of("lamp_red", 0.1), LAMP_AMBER = of("lamp_amber", 0.1);

    /**
     * 0.20, the control tower: board-formed concrete (its relief in a normal map, AeroCore 0.38's opt-in shading) and
     * the cab's reflective glass (the sky on top of what shows through; far away drawn opaque at 0.8 brightness).
     */
    public static final Finish BOARD_FORMED_CONCRETE = of("board_formed_concrete", 1.2), TOWER_GLASS = of("tower_glass", 1.0);

    public static final List<Finish> ALL = List.of(BRUSHED_STEEL, STAINLESS, MACHINED_STEEL, CAST_IRON, CAST_ALUMINIUM, FORGED_STEEL, TITANIUM, INCONEL,
            SINGLE_CRYSTAL, COPPER, BRASS, BRONZE, CARBON_WEAVE, GFRP, RUBBER, PAINT, PAINT_WORN, GRAPHITE, CERAMIC, BRAIDED_HOSE, PLASTIC, PROPELLANT, GLASS,
            PAINT_GLOSS, PAINT_METALLIC, PAINT_PEARL, PAINT_MATTE, CHROME, TYRE, LEATHER, FABRIC, CARPET, DASH_PLASTIC, TEAK, ANTIFOULING, NONSKID,
            ALUMINIUM_SKIN, LAMP_CLEAR, LAMP_RED, LAMP_AMBER, BOARD_FORMED_CONCRETE, TOWER_GLASS);

    /**
     * 0.12: lettering (registrations, names, plates): an atlas of glyphs made by tools/blender/glyphs.py, drawn cut out
     * where its alpha is low (not tuned to the part colours' brightness, so not in ALL). Its tile is one: texture
     * coordinates are the atlas's own (see Lettering).
     */
    public static final Finish GLYPHS = of("glyphs", 1.0);

    /**
     * 0.11: effect textures made by tools/blender/fx.py (not tuned to the part colours' brightness, so not in ALL): a
     * plume's flame noise, scrolled down the plume, and a fast propeller's blurred disc.
     */
    public static final Finish FX_FLAME = of("fx_flame", 0.5), FX_PROP_BLUR = of("fx_prop_blur", 0.3);

    /**
     * 0.13: a sticker's picture (an uploaded image, by its SHA-256): defined in the game when the picture arrives
     * (PartFinishes.define), not in finishes.json. Its tile is one: texture coordinates are the picture's own.
     */
    public static Finish decal(String image) { return new Finish(DECAL_PREFIX + image, 1.0); }

    /** The id prefix of stickers' pictures. */
    public static final String DECAL_PREFIX = "hdfv:decal/";

    /** The picture a finish id names (a sticker's), or null. */
    public static String decalImage(String finishId) {
        if (finishId == null || !finishId.startsWith(DECAL_PREFIX)) { return null; }
        String image = finishId.substring(DECAL_PREFIX.length());
        return Sticker.isImage(image) ? image : null;
    }

    private static Finish of(String name, double tileM) { return new Finish("hdfv:" + name, tileM); }
}
