package dev.mcmod.hdfv.neoforge.petroleum;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A petroleum block with a screen (0.9; the user asked for the oil industry's blocks to have screens that set them up):
 * what its screen shows (view, sent to the clients watching it every half second and read there as shown), the
 * settings the screen sends (set, checked on the server), and the screen's kind and item slots.
 */
public interface Configurable {
    /** Applies a setting from a screen (server): null, or why it was refused. */
    Component set(String key, double value);

    /** What its screen shows: its settings and what it is doing. */
    CompoundTag view();

    /** On a client: the last view the server sent. */
    CompoundTag shown();

    /** Sends its view to the clients watching it (server). */
    void sendView();

    /** Which screen it has. */
    PetroleumMenu.Kind kind();

    /** The item slots its screen shows (null: none). */
    default IItemHandler menuItems() { return null; }

    /** A refusal: a setting out of its range. */
    static Component outOfRange(String key, double min, double max) {
        return Component.translatable("hdfv.gui.refused", Component.translatable("hdfv.gui.setting." + key.replaceAll("\\..*", "")),
                fmt(min), fmt(max));
    }

    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.2f", v); }
}
