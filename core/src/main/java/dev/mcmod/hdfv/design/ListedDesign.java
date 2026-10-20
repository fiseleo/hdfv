package dev.mcmod.hdfv.design;

import dev.mcmod.aerocore.api.vehicle.PartDesign;

import java.util.List;
import java.util.Set;

/**
 * 0.15: a design made of a list of items the player adds, moves, copies and removes in the design screen (a body's
 * modules, a chassis's stations), each item a page named after the list ("module1", "station2": numbered from 1), of a
 * kind chosen when it is added. The screen's list tools work on this; the item pages' parameters are translated under
 * the list's name (hdfv.param.module.*), its tools under hdfv.screen.&lt;list&gt;_add, _kind, _up, _down, _copy, _remove.
 */
public interface ListedDesign extends PartDesign {
    /** The lists' names (the item pages' prefixes). */
    Set<String> LISTS = Set.of("module", "station");

    /** The list's name: its item pages' prefix ("module"). */
    String listName();
    /** How many items. */
    int items();
    /** The fewest items the design keeps (remove stops there). */
    default int minItems() { return 1; }
    /** The kinds an item can be. */
    List<? extends Enum<?>> itemKinds();
    /** An item's kind. */
    Enum<?> itemKind(int index);
    /** The translation key of a kind's name ("hdfv.choice.module.kind.hood"). */
    default String kindKey(Enum<?> kind) { return "hdfv.choice." + listName() + ".kind." + kind.name().toLowerCase(java.util.Locale.ROOT); }

    /** A new item of a kind after the one at index (-1: first). */
    ListedDesign added(int after, Enum<?> kind);
    /** Without the item at index (as it is at the fewest). */
    ListedDesign removed(int index);
    /** The item at index moved by some places (as it is beyond the ends). */
    ListedDesign moved(int index, int by);
    /** With a copy of the item at index after it. */
    ListedDesign duplicated(int index);
    /** With a page (the settings or an item) replaced. */
    ListedDesign withPage(String page, Record value);

    /** An item's page ("module3"). */
    default String listPage(int index) { return listName() + (index + 1); }

    /** The item index of a page of this list, or -1. */
    default int listIndex(String page) { return index(listName(), page); }

    /** The item index of a page named after a list ("station2" in "station": 1), or -1. */
    static int index(String list, String page) {
        if (page == null || !page.startsWith(list) || page.length() == list.length()) { return -1; }
        try { return Integer.parseInt(page.substring(list.length())) - 1; } catch (NumberFormatException e) { return -1; }
    }

    /** The list a page is an item of ("station2": "station"), or null. */
    static String listOf(String page) {
        for (String list : LISTS) { if (index(list, page) >= 0) { return list; } }
        return null;
    }
}
