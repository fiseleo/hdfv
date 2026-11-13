package dev.mcmod.hdfv.storage;

import java.util.Locale;

/**
 * The rules of an item's mass that need no game (0.25; the game side is neoforge storage.ItemMass, the defaults of
 * other mods' and vanilla's items tools/item_mass.py's data map). The scale is the workshop's: an ingot or a stock is
 * 10 kg (Pace.KG_PER_INGOT), a stock bit 1 kg, coal 1 kg, a log 4 kg, a plank 1 kg (PowerPlant.FuelItem); parts,
 * standard components and intermediates weigh what their designs say.
 */
public final class CargoMass {
    private CargoMass() {}

    /** What nothing else weighs: a block's item, and any other item, kg. */
    public static final double FALLBACK_BLOCK_KG = 10, FALLBACK_ITEM_KG = 1;
    /** How deep items inside items are weighed (a shulker box in a bundle in a shulker box...); deeper contents weigh nothing. */
    public static final int MAX_DEPTH = 4;
    /** The most a store's footer can show, kg (its mass goes to the screen in tenths of a kilogram as two 16 bit halves). */
    public static final double MAX_SHOWN_KG = Integer.MAX_VALUE / 10.0;

    /**
     * A workpiece's mass, kg: its stock when its first step is done, its finished mass when the last is, evenly in
     * between (the chips leave it step by step). Step and steps as Workpieces.Data counts them.
     */
    public static double workpieceKg(double stockKg, double finishedKg, int step, int steps) {
        if (steps <= 1 || step >= steps) { return Math.max(finishedKg, 0); }
        double done = Math.max(step - 1, 0) / (double) (steps - 1);
        return Math.max(stockKg + (finishedKg - stockKg) * done, 0);
    }

    /** A mass in tenths of a kilogram, rounded, within what a footer shows. */
    public static int tenths(double kg) {
        if (!(kg > 0)) { return 0; }
        return (int) Math.min(Math.round(kg * 10), Integer.MAX_VALUE);
    }

    /** The two 16 bit halves a menu's data slots carry (they are sent as shorts), and the tenths back from them. */
    public static int low(int tenths) { return tenths & 0xffff; }
    public static int high(int tenths) { return tenths >>> 16; }
    public static int join(int low, int high) { return (low & 0xffff) | (high & 0xffff) << 16; }

    /** A mass as text: "0.5", "12.5", "64" (a tenth of a kilogram under 100 kg, whole kilograms from there). */
    public static String text(double kg) {
        if (!(kg > 0)) { return "0"; }
        if (kg >= 99.95) { return String.format(Locale.ROOT, "%,d", Math.round(kg)); }
        double tenth = Math.round(kg * 10) / 10.0;
        if (tenth == 0) { return kg >= 0.005 ? String.format(Locale.ROOT, "%.2f", kg) : "0"; }
        return tenth == Math.rint(tenth) ? String.valueOf((long) tenth) : String.format(Locale.ROOT, "%.1f", tenth);
    }
}
