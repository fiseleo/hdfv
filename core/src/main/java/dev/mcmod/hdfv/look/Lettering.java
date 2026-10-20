package dev.mcmod.hdfv.look;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lettering (0.12): registrations, names and number plates drawn as decals from the glyph atlas tools/blender/glyphs.py
 * makes (finish Finishes.GLYPHS, cut out where its alpha is low). The atlas is 1024 px square, 8 cells across and 6
 * down, 128 x 170 px each, the glyphs in ORDER; a glyph's capital height is 79.5 % of its cell (6 grid units of 22.5 px)
 * and the cells set side by side space it.
 * <p>
 * Text is capitals A-Z, digits, the hyphen and spaces, at most 10 characters (others are dropped, letters raised).
 * Where it goes and how high are the model's: an aircraft's registration on each side of the rear fuselage, 3 to 12
 * inches high (14 CFR 45.29); a boat's name on each side of the bow and on its transom, at least 3 inches (33 CFR
 * 174.15); a car's plates 520 x 110 mm.
 */
public final class Lettering {
    private Lettering() {}

    public static final String ORDER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-";
    public static final int MAX = 10;
    static final int SIZE = 1024, COLUMNS = 8, CELL_W = 128, CELL_H = 170;
    /** A capital's height as a share of its cell's. */
    public static final double CAP = 135.0 / CELL_H;
    /** A cell's width over its height. */
    public static final double ASPECT = (double) CELL_W / CELL_H;

    /** Text as it is drawn: capitals, what the atlas has, single spaces, trimmed, at most MAX characters. */
    public static String clean(String text) {
        if (text == null) { return ""; }
        StringBuilder b = new StringBuilder();
        for (char ch : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (ORDER.indexOf(ch) >= 0) { b.append(ch); }
            else if (Character.isWhitespace(ch) && b.length() > 0 && b.charAt(b.length() - 1) != ' ') { b.append(' '); }
        }
        String t = b.toString().trim();
        return t.length() > MAX ? t.substring(0, MAX).trim() : t;
    }

    /**
     * A glyph set in a line: its cell from x0 to x1 along the line (in capital heights, the line starting at 0), and its
     * cell's corners in the atlas (u0, v0 top left to u1, v1 bottom right).
     */
    public record Glyph(char ch, double x0, double x1, float u0, float v0, float u1, float v1) {}

    /** A cell's height in capital heights. */
    public static double cellHeight() { return 1 / CAP; }

    /** The line's glyphs (spaces leave a gap). */
    public static List<Glyph> layout(String text) {
        String t = clean(text);
        double w = ASPECT / CAP;
        List<Glyph> out = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) {
            int k = ORDER.indexOf(t.charAt(i));
            if (k < 0) { continue; }
            int col = k % COLUMNS, row = k / COLUMNS;
            out.add(new Glyph(t.charAt(i), i * w, (i + 1) * w, (float) col * CELL_W / SIZE, (float) row * CELL_H / SIZE,
                    (float) (col + 1) * CELL_W / SIZE, (float) (row + 1) * CELL_H / SIZE));
        }
        return out;
    }

    /** A line's length in capital heights. */
    public static double width(String text) { return clean(text).length() * ASPECT / CAP; }
}
