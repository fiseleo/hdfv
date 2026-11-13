package dev.mcmod.hdfv.storage;

import java.util.List;
import java.util.Locale;

/**
 * The workshop's storage blocks (0.25): a wooden crate, a steel locker, a parts rack (finished parts, workpieces,
 * standard components and drawings only) and a shipping container. Each is one block holding so many stacks, its
 * screen nine slots wide and ROWS_SHOWN rows at most at a time (the container's 108 slots are two pages of six rows).
 * Their assets are tools/storage_assets.py's, their recipes tools/recipes.py's.
 */
public enum StorageKind {
    STORAGE_CRATE(27, false, true), STEEL_LOCKER(54, false, false), PARTS_RACK(36, true, false), SHIPPING_CONTAINER(108, false, false);

    /** Slots across a screen, and the most rows it shows at a time (a double chest's). */
    public static final int COLUMNS = 9, ROWS_SHOWN = 6;

    /** Stacks it holds. */
    public final int slots;
    /** Whether it takes only parts, workpieces, standard components and drawings (the item tag hdfv:parts_rack). */
    public final boolean partsOnly;
    /** Whether it is wood (an axe's block; the others are steel, a pickaxe's). */
    public final boolean wooden;

    StorageKind(int slots, boolean partsOnly, boolean wooden) {
        this.slots = slots;
        this.partsOnly = partsOnly;
        this.wooden = wooden;
    }

    /** Its id: the block's and the item's. */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** Rows of slots a page of its screen shows. */
    public int rows() { return rows(slots); }

    /** Pages of its screen. */
    public int pages() { return pages(slots); }

    /** Rows a page shows of so many slots: all of them up to ROWS_SHOWN. */
    public static int rows(int slots) { return Math.min(ROWS_SHOWN, (Math.max(slots, 1) + COLUMNS - 1) / COLUMNS); }

    /** Pages so many slots take. */
    public static int pages(int slots) { return (Math.max(slots, 1) + rows(slots) * COLUMNS - 1) / (rows(slots) * COLUMNS); }

    public static StorageKind ofId(String id) {
        for (StorageKind k : values()) { if (k.id().equals(id)) { return k; } }
        return null;
    }

    public static List<String> ids() {
        List<String> out = new java.util.ArrayList<>();
        for (StorageKind k : values()) { out.add(k.id()); }
        return out;
    }
}
