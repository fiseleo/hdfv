package dev.mcmod.hdfv.look;

import dev.mcmod.aerocore.api.vehicle.DesignIssue;
import dev.mcmod.aerocore.api.vehicle.Fields;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 0.13: a sticker (痛車貼紙) on a body or a wing: an uploaded image, named by its SHA-256 (64 lower-case hex digits),
 * laid on the skin at a place, widthM wide and aspect times that high (the image's height over its width, so the mesh
 * is built without the image), turned rotationDeg counter-clockwise seen from outside, mirrored left to right (flip),
 * with its mirror image on the other side as well (pair: a body's other side, a mirrored wing's other half, a single
 * wing's (a fin's) other face), and stacked by layer (0 under, 63 on top).
 * <p>
 * Looks only: a design's stickers change nothing it computes (mass, drag, mount points). A design holds at most MAX;
 * read refuses what is not a sticker (a missing or mistyped field, an image that is not a SHA-256), check what is out
 * of range. JSON: {"image", "aspect", "on": "body", "x", "t" | "on": "wing", "eta", "chord", "surface", "side",
 * "widthM", "rotationDeg", "flip", "pair", "layer"}.
 */
public record Sticker(String image, double aspect, Place place, double widthM, double rotationDeg, boolean flip, boolean pair, int layer) {
    /** Stickers on one design, and layers. */
    public static final int MAX = 64, LAYERS = 64;
    public static final double MIN_ASPECT = 0.05, MAX_ASPECT = 20, MIN_WIDTH_M = 0.02, MAX_WIDTH_M = 20;

    /** Where a sticker's middle is. */
    public sealed interface Place permits OnBody, OnWing {}

    /**
     * On a body: x from its rear end (m) and BodySurface's angle t (0 the bottom, up the port side to pi at the top and
     * down the starboard side to 2 pi). A sticker reaching past the body's ends is clipped there.
     */
    public record OnBody(double x, double t) implements Place {}

    /**
     * On a wing (WingSurface): the span fraction eta from the root, the chord fraction from the leading edge, the upper
     * or lower surface, and the half; a single wing (a fin) has one half, so its side means nothing.
     */
    public record OnWing(double eta, double chord, Surface surface, Side side) implements Place {
        public OnWing { Objects.requireNonNull(surface, "surface"); Objects.requireNonNull(side, "side"); }
    }

    public enum Surface { UPPER, LOWER }

    public enum Side {
        PORT, STARBOARD;
        /** AirframeModel's side: -1 port (z < 0), +1 starboard. */
        public int sign() { return this == PORT ? -1 : 1; }
    }

    public Sticker {
        Objects.requireNonNull(image, "image"); Objects.requireNonNull(place, "place");
    }

    /** Whether an image name is a SHA-256 as stored: 64 lower-case hex digits. */
    public static boolean isImage(String image) { return image != null && image.matches("[0-9a-f]{64}"); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = Fields.object("image", image, "aspect", aspect);
        switch (place) {
            case OnBody b -> { m.put("on", "body"); m.put("x", b.x()); m.put("t", b.t()); }
            case OnWing w -> {
                m.put("on", "wing"); m.put("eta", w.eta()); m.put("chord", w.chord());
                m.put("surface", w.surface().name().toLowerCase(Locale.ROOT)); m.put("side", w.side().name().toLowerCase(Locale.ROOT));
            }
        }
        m.putAll(Fields.object("widthM", widthM, "rotationDeg", rotationDeg, "flip", flip, "pair", pair, "layer", layer));
        return m;
    }

    /** A sticker from JSON; IllegalArgumentException for one that is not a sticker (its range is check's). */
    public static Sticker read(Fields f) {
        String image = f.string("image");
        if (!isImage(image)) { throw new IllegalArgumentException(f.path() + ".image: expected a SHA-256 (64 lower-case hex digits)"); }
        String on = f.string("on");
        Place place = switch (on) {
            case "body" -> new OnBody(f.number("x"), f.number("t"));
            case "wing" -> new OnWing(f.number("eta"), f.number("chord"), choice(f, "surface", Surface.class), choice(f, "side", Side.class));
            default -> throw new IllegalArgumentException(f.path() + ".on: expected body or wing, not " + on);
        };
        return new Sticker(image, f.number("aspect"), place, f.number("widthM"), f.number("rotationDeg", 0), f.bool("flip", false), f.bool("pair", false),
                f.integer("layer", 0));
    }

    private static <E extends Enum<E>> E choice(Fields f, String key, Class<E> type) {
        String s = f.string(key);
        for (E e : type.getEnumConstants()) { if (e.name().toLowerCase(Locale.ROOT).equals(s)) { return e; } }
        throw new IllegalArgumentException(f.path() + "." + key + ": unknown value " + s);
    }

    /** A design's stickers under key: none when it has no such key (a design saved before 0.13). */
    public static List<Sticker> list(Fields f, String key) {
        if (!f.has(key)) { return List.of(); }
        List<Sticker> out = new ArrayList<>();
        for (Fields s : f.objects(key)) { out.add(read(s)); }
        return List.copyOf(out);
    }

    public static List<Object> toJson(List<Sticker> stickers) {
        List<Object> out = new ArrayList<>(stickers.size());
        for (Sticker s : stickers) { out.add(s.toJson()); }
        return out;
    }

    /**
     * A design's stickers checked under key ("stickers"; each one "stickers[i].widthM" and so on): at most MAX, each
     * placed on what the design is (a body's by x and t, a wing's by eta and chord) and in range.
     */
    public static void check(List<DesignIssue> issues, String key, List<Sticker> stickers, boolean wing) {
        if (stickers.size() > MAX) { issues.add(DesignIssue.invalid(key, "At most " + MAX + " stickers, not " + stickers.size())); }
        for (int i = 0; i < stickers.size(); i++) { stickers.get(i).check(issues, key + "[" + i + "]", "Sticker " + (i + 1) + ": ", wing); }
    }

    private void check(List<DesignIssue> issues, String path, String which, boolean wing) {
        if (!isImage(image)) { issues.add(DesignIssue.invalid(path + ".image", which + "the image is a SHA-256 (64 lower-case hex digits)")); }
        if (!(aspect >= MIN_ASPECT && aspect <= MAX_ASPECT)) {
            issues.add(DesignIssue.invalid(path + ".aspect", which + "the image's height over its width is " + MIN_ASPECT + " to " + MAX_ASPECT));
        }
        if (!(widthM >= MIN_WIDTH_M && widthM <= MAX_WIDTH_M)) {
            issues.add(DesignIssue.invalid(path + ".widthM", which + "the width is " + MIN_WIDTH_M + " to " + MAX_WIDTH_M + " m"));
        }
        if (!(rotationDeg >= -180 && rotationDeg <= 180)) { issues.add(DesignIssue.invalid(path + ".rotationDeg", which + "the rotation is -180 to 180 deg")); }
        if (layer < 0 || layer >= LAYERS) { issues.add(DesignIssue.invalid(path + ".layer", which + "the layer is 0 to " + (LAYERS - 1))); }
        switch (place) {
            case OnBody b -> {
                if (wing) { issues.add(DesignIssue.invalid(path + ".on", which + "a wing's sticker is placed by eta and chord")); }
                if (!(b.x() >= 0 && Double.isFinite(b.x()))) { issues.add(DesignIssue.invalid(path + ".x", which + "x is measured forward from the body's rear end")); }
                if (!(b.t() >= 0 && b.t() <= 2 * Math.PI)) { issues.add(DesignIssue.invalid(path + ".t", which + "the angle round the body is 0 to 2 pi")); }
            }
            case OnWing w -> {
                if (!wing) { issues.add(DesignIssue.invalid(path + ".on", which + "a body's sticker is placed by x and t")); }
                if (!(w.eta() >= 0 && w.eta() <= 1)) { issues.add(DesignIssue.invalid(path + ".eta", which + "the span fraction is 0 (root) to 1 (tip)")); }
                if (!(w.chord() >= 0 && w.chord() <= 1)) {
                    issues.add(DesignIssue.invalid(path + ".chord", which + "the chord fraction is 0 (leading edge) to 1 (trailing edge)"));
                }
            }
        }
    }
}
