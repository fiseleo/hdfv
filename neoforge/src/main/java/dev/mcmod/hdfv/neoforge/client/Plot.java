package dev.mcmod.hdfv.neoforge.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** A small line chart for the design screen's bench: one x axis, a left and an optional right y axis, polylines. */
final class Plot {
    private Plot() {}

    /** A polyline against the left or the right axis. */
    record Series(double[] x, double[] y, int color, boolean right) {}

    private static final int FRAME = 0xFF808080, GRID = 0x30FFFFFF, FILL = 0x50000000;

    static void draw(GuiGraphics g, Font font, int x, int y, int w, int h, double xMin, double xMax, String xLabel,
                     String leftLabel, int leftColor, String rightLabel, int rightColor, List<Series> series) {
        double[] left = range(series, false), right = range(series, true);
        int px = x + 30, py = y + 10, pw = w - 30 - (right == null ? 6 : 30), ph = h - 10 - 11;
        if (pw < 20 || ph < 20) { return; }
        g.fill(x, y, x + w, y + h, FILL);
        for (int i = 1; i < 4; i++) { g.hLine(px, px + pw, py + ph * i / 4, GRID); g.vLine(px + pw * i / 4, py, py + ph, GRID); }
        g.renderOutline(px, py, pw + 1, ph + 1, FRAME);
        // 0.25: two axis titles that overlap over the plot (a half-width chart) use the line's whole width, over the numbers' columns too
        int leftX = px, rightEnd = px + pw;
        if (left != null && right != null && font.width(leftLabel) + font.width(rightLabel) + 4 > pw) { leftX = x + 2; rightEnd = x + w - 2; }
        if (left != null) {
            g.drawString(font, number(left[1]), px - 2 - font.width(number(left[1])), py, leftColor, false);
            g.drawString(font, number(left[0]), px - 2 - font.width(number(left[0])), py + ph - 8, leftColor, false);
            g.drawString(font, leftLabel, leftX, y + 1, leftColor, false);
        }
        if (right != null) {
            g.drawString(font, number(right[1]), px + pw + 3, py, rightColor, false);
            g.drawString(font, number(right[0]), px + pw + 3, py + ph - 8, rightColor, false);
            g.drawString(font, rightLabel, rightEnd - font.width(rightLabel), y + 1, rightColor, false);
        }
        g.drawString(font, number(xMin), px, py + ph + 2, 0xFFC0C0C0, false);
        g.drawString(font, number(xMax), px + pw - font.width(number(xMax)), py + ph + 2, 0xFFC0C0C0, false);
        g.drawString(font, xLabel, px + (pw - font.width(xLabel)) / 2, py + ph + 2, 0xFFC0C0C0, false);
        for (Series s : series) {
            double[] r = s.right() ? right : left;
            if (r == null) { continue; }
            double lastX = Double.NaN, lastY = Double.NaN;
            for (int i = 0; i < s.x().length; i++) {
                if (!Double.isFinite(s.y()[i])) { lastX = Double.NaN; continue; }
                double sx = px + (s.x()[i] - xMin) / (xMax - xMin) * pw, sy = py + ph - (s.y()[i] - r[0]) / (r[1] - r[0]) * ph;
                if (!Double.isNaN(lastX)) { segment(g, lastX, lastY, sx, sy, s.color()); }
                lastX = sx; lastY = sy;
            }
        }
    }

    /** Axis limits (min, max) of the series on one side, rounded out to 1, 2 or 5 times a power of ten; null if none. */
    private static double[] range(List<Series> series, boolean right) {
        double lo = 0, hi = Double.NEGATIVE_INFINITY;
        for (Series s : series) {
            if (s.right() != right) { continue; }
            for (double v : s.y()) { if (Double.isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }
        }
        if (hi == Double.NEGATIVE_INFINITY) { return null; }
        double top = nice(Math.max(hi, 1e-9)), bottom = lo < 0 ? -nice(-lo) : 0;
        return new double[]{bottom, top};
    }

    static double nice(double v) {
        double p = Math.pow(10, Math.floor(Math.log10(v))), m = v / p;
        return (m <= 1 ? 1 : m <= 2 ? 2 : m <= 5 ? 5 : 10) * p;
    }

    private static void segment(GuiGraphics g, double x1, double y1, double x2, double y2, int color) {
        int steps = (int) Math.ceil(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            int x = (int) Math.round(x1 + t * (x2 - x1)), y = (int) Math.round(y1 + t * (y2 - y1));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** Four significant figures without trailing zeros. */
    static String number(double v) {
        if (!Double.isFinite(v)) { return "—"; }
        if (v == 0) { return "0"; }
        return new java.math.BigDecimal(v).round(new java.math.MathContext(4)).stripTrailingZeros().toPlainString();
    }
}
