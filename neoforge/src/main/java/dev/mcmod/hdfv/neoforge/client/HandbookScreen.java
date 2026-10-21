package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.handbook.HandbookText;
import dev.mcmod.hdfv.handbook.HandbookText.Block;
import dev.mcmod.hdfv.handbook.HandbookText.Entry;
import dev.mcmod.hdfv.handbook.HandbookText.Span;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The handbook (0.24, the new-player tutorial): the entries on the left (main line, then branches; ticked when their
 * advancement is done; a search box), the open entry on the right: text with the keys as bound now and item names,
 * item icons (hover for the tooltip; with JEI, a click shows the recipes), pictures, recipes, sample designs in 3D
 * (drag to turn) and links. The pages are HandbookPages'; the ticks come from the server (HdfvTutorial.ProgressPayload).
 */
public final class HandbookScreen extends Screen {
    /** The hdfv advancements the player has done (ids), as the server last said. */
    static final Set<String> DONE = ConcurrentHashMap.newKeySet();
    /** The entry open when the handbook was last closed. */
    private static String last = "";

    private static final int LIST_W = 150, PAD = 8, ROW = 14, LINE = 10, SLOT = 18;
    private static final int PANEL = 0xF01A1D22, PAGE = 0xF0262A31, TEXT = 0xFFE6E6E6, DIM = 0xFF9AA0A8, ACCENT = 0xFF7FD3FF, DONE_TICK = 0xFF67D36B;

    private final String wanted;
    private final Preview preview = new Preview();
    private final Map<String, PartDesign> designs = new HashMap<>();
    private List<Entry> entries = List.of();
    private Entry entry;
    private EditBox search;
    private int px, py, pw, ph;
    private double listScroll, pageScroll;
    private int listHeight, pageHeight;
    private List<Row> rows = List.of();
    private Entry rowsFor;
    private int rowsWidth = -1;
    private ItemStack hovered = ItemStack.EMPTY;
    /** Where the 3D view was drawn this frame (x, y, w, h), for dragging it; null when there is none. */
    private int[] previewAt;
    private boolean dragging;

    public HandbookScreen(String entry) {
        super(Component.translatable("item.hdfv.handbook"));
        this.wanted = entry == null ? "" : entry;
    }

    @Override protected void init() {
        pw = Math.min(width - 16, 540);
        ph = height - 16;
        px = (width - pw) / 2;
        py = 8;
        search = new EditBox(font, px + 4, py + 4, LIST_W - 8, 14, Component.translatable("hdfv.handbook.search"));
        search.setHint(Component.translatable("hdfv.handbook.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> { listScroll = 0; });
        addRenderableWidget(search);
        entries = HandbookPages.get();
        if (entry == null) {
            String id = !wanted.isEmpty() ? wanted : last;
            entry = entries.stream().filter(e -> e.id().equals(id)).findFirst().orElse(entries.isEmpty() ? null : entries.getFirst());
        }
        rowsWidth = -1;
    }

    @Override public void onClose() {
        if (entry != null) { last = entry.id(); }
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------------------------------------------- the list

    private List<Entry> shown() {
        String q = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) { return entries; }
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) { if (plain(e).toLowerCase(Locale.ROOT).contains(q)) { out.add(e); } }
        return out;
    }

    /** An entry's title and words, for the search. */
    private static String plain(Entry e) {
        StringBuilder b = new StringBuilder(e.title());
        for (Block block : e.blocks()) {
            List<Span> spans = block instanceof HandbookText.Heading h ? h.spans() : block instanceof HandbookText.Paragraph p ? p.spans() : List.of();
            for (Span s : spans) { if (s instanceof HandbookText.Text t) { b.append(' ').append(t.text()); } }
        }
        return b.toString();
    }

    private static boolean done(Entry e) { return !e.advancement().isEmpty() && DONE.contains(e.advancement()); }

    /** The list's rows: section headers (null entries with a title) and entries, top to bottom. */
    private record ListRow(Entry entry, Component header) {}

    private List<ListRow> listRows() {
        List<ListRow> out = new ArrayList<>();
        HandbookText.Section section = null;
        for (Entry e : shown()) {
            if (e.section() != section) {
                section = e.section();
                out.add(new ListRow(null, Component.translatable("hdfv.handbook.section." + section.name().toLowerCase(Locale.ROOT))));
            }
            out.add(new ListRow(e, null));
        }
        return out;
    }

    private int listTop() { return py + 22; }
    private int listBottom() { return py + ph - 4; }

    private void renderList(GuiGraphics g, int mx, int my) {
        List<ListRow> list = listRows();
        listHeight = list.size() * ROW;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listHeight - (listBottom() - listTop()))));
        g.enableScissor(px, listTop(), px + LIST_W, listBottom());
        int y = listTop() - (int) listScroll;
        for (ListRow r : list) {
            if (r.header() != null) {
                g.drawString(font, r.header().copy().withStyle(ChatFormatting.BOLD), px + 6, y + 3, ACCENT);
            } else {
                Entry e = r.entry();
                boolean over = mx >= px && mx < px + LIST_W && my >= y && my < y + ROW && my >= listTop() && my < listBottom();
                if (e == entry) { g.fill(px + 2, y, px + LIST_W - 2, y + ROW, 0x50FFFFFF); }
                else if (over) { g.fill(px + 2, y, px + LIST_W - 2, y + ROW, 0x28FFFFFF); }
                g.pose().pushPose();
                g.pose().translate(px + 6, y + 1, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.renderItem(icon(e.icon()), 0, 0);
                g.pose().popPose();
                int right = px + LIST_W - 6 - (done(e) ? 10 : 0);
                g.drawString(font, font.plainSubstrByWidth(e.title(), right - (px + 22)), px + 22, y + 3, e == entry ? 0xFFFFFFFF : TEXT);
                if (done(e)) { g.drawString(font, "✔", px + LIST_W - 14, y + 3, DONE_TICK); }
            }
            y += ROW;
        }
        g.disableScissor();
    }

    private Entry listAt(double mx, double my) {
        if (mx < px || mx >= px + LIST_W || my < listTop() || my >= listBottom()) { return null; }
        int i = (int) ((my - listTop() + listScroll) / ROW);
        List<ListRow> list = listRows();
        return i >= 0 && i < list.size() ? list.get(i).entry() : null;
    }

    // ---------------------------------------------------------------------------------------------------- the page

    private int pageX() { return px + LIST_W + PAD; }
    private int pageW() { return pw - LIST_W - 2 * PAD; }
    private int pageTop() { return py + 4; }
    private int pageBottom() { return py + ph - 4; }

    /** A laid-out piece of the page: its height; it draws itself at (x, y) and may answer a click there. */
    private abstract class Row {
        final int height;
        Row(int height) { this.height = height; }
        abstract void draw(GuiGraphics g, int x, int y, int w, int mx, int my);
        boolean click(int x, int y, int w, double mx, double my) { return false; }
    }

    private void layout(int w) {
        if (entry == rowsFor && w == rowsWidth) { return; }
        rowsFor = entry;
        rowsWidth = w;
        List<Row> out = new ArrayList<>();
        if (entry == null) { rows = out; return; }
        Component title = Component.literal(entry.title()).withStyle(ChatFormatting.BOLD);
        out.add(new Row(16) {
            @Override void draw(GuiGraphics g, int x, int y, int ww, int mx, int my) {
                g.renderItem(icon(entry.icon()), x, y - 2);
                g.drawString(font, title, x + 20, y + 2, 0xFFFFFFFF);
                if (done(entry)) { g.drawString(font, Component.translatable("hdfv.handbook.done"), x + ww - font.width(Component.translatable("hdfv.handbook.done")), y + 2, DONE_TICK); }
            }
        });
        if (!entry.advancement().isEmpty()) {
            Component goal = Component.translatable("hdfv.handbook.goal",
                    Component.translatable("advancements." + entry.advancement().replace(':', '.').replace('/', '.') + ".title").withStyle(ChatFormatting.YELLOW));
            for (FormattedCharSequence line : font.split(goal, w)) { out.add(text(line, 0, DIM)); }
        }
        out.add(gap(6));
        for (Block b : entry.blocks()) { addBlock(out, b, w); }
        out.add(gap(8));
        int at = entries.indexOf(entry);
        if (at > 0) { out.add(link(entries.get(at - 1), "hdfv.handbook.previous")); }
        if (at >= 0 && at + 1 < entries.size()) { out.add(link(entries.get(at + 1), "hdfv.handbook.next")); }
        rows = out;
    }

    private void addBlock(List<Row> out, Block b, int w) {
        switch (b) {
            case HandbookText.Heading h -> {
                out.add(gap(4));
                for (FormattedCharSequence line : font.split(spans(h.spans()).withStyle(s -> s.withBold(true).withColor(ACCENT)), w)) { out.add(text(line, 0, ACCENT)); }
                out.add(gap(2));
            }
            case HandbookText.Paragraph p -> {
                int indent = p.bullet() ? 10 : 0;
                List<FormattedCharSequence> lines = font.split(spans(p.spans()), w - indent);
                for (int i = 0; i < lines.size(); i++) {
                    boolean first = i == 0;
                    FormattedCharSequence line = lines.get(i);
                    out.add(new Row(LINE) {
                        @Override void draw(GuiGraphics g, int x, int y, int ww, int mx, int my) {
                            if (p.bullet() && first) { g.drawString(font, "•", x + 2, y, ACCENT); }
                            g.drawString(font, line, x + indent, y, TEXT);
                        }
                    });
                }
                out.add(gap(4));
            }
            case HandbookText.Items items -> out.add(itemsRow(items.ids().stream().map(HandbookScreen::icon).toList(), w));
            case HandbookText.Image image -> {
                ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "textures/gui/handbook/" + image.name() + ".png");
                int dw = Math.min(w, 320), dh = dw * 9 / 16;
                out.add(new Row(dh + 6) {
                    @Override void draw(GuiGraphics g, int x, int y, int ww, int mx, int my) {
                        int left = x + (ww - dw) / 2;
                        g.blit(texture, left, y, dw, dh, 0, 0, 320, 180, 320, 180);
                        g.renderOutline(left - 1, y - 1, dw + 2, dh + 2, 0x40FFFFFF);
                    }
                });
            }
            case HandbookText.Recipe r -> out.add(recipeRow(r.id(), w));
            case HandbookText.Design d -> {
                int dw = Math.min(w, 320), dh = dw * 9 / 16;
                out.add(new Row(dh + 6) {
                    @Override void draw(GuiGraphics g, int x, int y, int ww, int mx, int my) {
                        PartDesign design = design(d.sample());
                        int left = x + (ww - dw) / 2;
                        if (design == null) { g.drawString(font, "? " + d.sample(), left, y, DIM); return; }
                        preview.render(g, left, y, dw, dh, design, DesignScreen.OVERVIEW);
                        previewAt = new int[] {left, y, dw, dh};
                    }
                });
            }
            case HandbookText.See s -> {
                Entry target = HandbookPages.find(s.entry());
                if (target != null) { out.add(link(target, "hdfv.handbook.see")); }
            }
        }
    }

    private Row gap(int h) {
        return new Row(h) { @Override void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {} };
    }

    private Row text(FormattedCharSequence line, int indent, int colour) {
        return new Row(LINE) {
            @Override void draw(GuiGraphics g, int x, int y, int w, int mx, int my) { g.drawString(font, line, x + indent, y, colour); }
        };
    }

    /** "see: title", "next: title": opens the entry. */
    private Row link(Entry target, String key) {
        Component label = Component.translatable(key, Component.literal(target.title())).withStyle(ChatFormatting.UNDERLINE);
        return new Row(LINE + 2) {
            @Override void draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
                boolean over = mx >= x && mx < x + font.width(label) && my >= y && my < y + LINE;
                g.drawString(font, label, x, y, over ? 0xFFFFFFFF : ACCENT);
            }
            @Override boolean click(int x, int y, int w, double mx, double my) {
                if (mx < x || mx >= x + font.width(label) || my < y || my >= y + LINE) { return false; }
                open(target);
                return true;
            }
        };
    }

    /** A row of item icons, wrapped; hover for the tooltip, click for the recipes (JEI). */
    private Row itemsRow(List<ItemStack> stacks, int w) {
        int perRow = Math.max(1, w / SLOT), lines = (stacks.size() + perRow - 1) / perRow;
        return new Row(lines * SLOT + 4) {
            @Override void draw(GuiGraphics g, int x, int y, int ww, int mx, int my) {
                for (int i = 0; i < stacks.size(); i++) {
                    int sx = x + (i % perRow) * SLOT, sy = y + (i / perRow) * SLOT;
                    g.fill(sx, sy, sx + 16, sy + 16, 0x30FFFFFF);
                    g.renderItem(stacks.get(i), sx, sy);
                    if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) { hovered = stacks.get(i); }
                }
            }
            @Override boolean click(int x, int y, int ww, double mx, double my) {
                for (int i = 0; i < stacks.size(); i++) {
                    int sx = x + (i % perRow) * SLOT, sy = y + (i / perRow) * SLOT;
                    if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) { return showRecipes(stacks.get(i)); }
                }
                return false;
            }
        };
    }

    /** A recipe: where it is made, its ingredients (a shaped one as its grid; the choices of an ingredient take turns) and what it makes. */
    private Row recipeRow(String id, int w) {
        var level = minecraft.level;
        Recipe<?> recipe = level == null ? null : level.getRecipeManager().byKey(ResourceLocation.parse(id)).map(h -> (Recipe<?>) h.value()).orElse(null);
        if (recipe == null) {
            Component missing = Component.translatable("hdfv.handbook.no_recipe", id).withStyle(ChatFormatting.ITALIC);
            return text(missing.getVisualOrderText(), 0, DIM);
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack result = recipe.getResultItem(level.registryAccess());
        ItemStack station = station(recipe);
        int columns = recipe instanceof ShapedRecipe s ? s.getWidth() : Math.min(ingredients.size(), Math.max(1, (w - 3 * SLOT - 30) / SLOT));
        int lines = recipe instanceof ShapedRecipe s ? s.getHeight() : (ingredients.size() + columns - 1) / columns;
        return new Row(Math.max(1, lines) * SLOT + 6) {
            @Override void draw(GuiGraphics g, int x, int y, int ww, int mx, int my) {
                long turn = Util.getMillis() / 1000;
                g.renderItem(station, x, y);
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) { hovered = station; }
                int gx = x + SLOT + 6;
                for (int i = 0; i < ingredients.size(); i++) {
                    int sx = gx + (i % columns) * SLOT, sy = y + (i / columns) * SLOT;
                    g.fill(sx, sy, sx + 16, sy + 16, 0x30FFFFFF);
                    ItemStack[] choices = ingredients.get(i).getItems();
                    if (choices.length == 0) { continue; }
                    ItemStack shown = choices[(int) (turn % choices.length)];
                    g.renderItem(shown, sx, sy);
                    g.renderItemDecorations(font, shown, sx, sy);
                    if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) { hovered = shown; }
                }
                int ax = gx + columns * SLOT + 4, ay = y + (Math.max(1, lines) * SLOT - 16) / 2;
                g.drawString(font, "→", ax, ay + 4, TEXT);
                g.fill(ax + 12, ay, ax + 28, ay + 16, 0x30FFFFFF);
                g.renderItem(result, ax + 12, ay);
                g.renderItemDecorations(font, result, ax + 12, ay);
                if (mx >= ax + 12 && mx < ax + 28 && my >= ay && my < ay + 16) { hovered = result; }
            }
            @Override boolean click(int x, int y, int ww, double mx, double my) {
                return !hovered.isEmpty() && showRecipes(hovered);
            }
        };
    }

    /** The block a recipe is made at: its type's (the crafting table for crafting). */
    private static ItemStack station(Recipe<?> recipe) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        String at = type == null ? "" : switch (type.toString()) {
            case "hdfv:assembling" -> "hdfv:assembly_bench";
            case "hdfv:alloying" -> "hdfv:alloy_furnace";
            case "hdfv:electric_furnace" -> "hdfv:arc_furnace";
            case "hdfv:electrolysis" -> "hdfv:electrolysis_cell";
            case "minecraft:smelting" -> "minecraft:furnace";
            case "minecraft:blasting" -> "minecraft:blast_furnace";
            default -> "minecraft:crafting_table";
        };
        return icon(at);
    }

    private static ItemStack icon(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) { return new ItemStack(Items.BARRIER); }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private PartDesign design(String sample) {
        return designs.computeIfAbsent(sample, s -> {
            for (Designs.Sample d : Designs.SAMPLES) { if (d.id().equals(s)) { return d.design().get(); } }
            return null;
        });
    }

    /** A line of text from its spans: keys as they are bound now, item names in gold, bold where marked. */
    private static MutableComponent spans(List<Span> spans) {
        MutableComponent out = Component.empty();
        for (Span s : spans) {
            switch (s) {
                case HandbookText.Text t -> out.append(Component.literal(t.text()).withStyle(Style.EMPTY.withBold(t.bold() ? true : null)));
                case HandbookText.Key k -> out.append(Component.keybind(k.mapping()).withStyle(ChatFormatting.YELLOW));
                case HandbookText.ItemName i -> out.append(icon(i.id()).getHoverName().copy().withStyle(ChatFormatting.GOLD));
            }
        }
        return out;
    }

    private void open(Entry e) {
        entry = e;
        pageScroll = 0;
        rowsWidth = -1;
    }

    private boolean showRecipes(ItemStack stack) {
        if (stack.isEmpty() || !net.neoforged.fml.ModList.get().isLoaded("jei")) { return false; }
        return dev.mcmod.hdfv.neoforge.compat.JeiLink.showRecipes(stack);
    }

    // ---------------------------------------------------------------------------------------------------- drawing and input

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderBackground(g, mx, my, partialTick);
        g.fill(px, py, px + LIST_W, py + ph, PANEL);
        g.fill(pageX() - 4, py, px + pw, py + ph, PAGE);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        hovered = ItemStack.EMPTY;
        previewAt = null;
        renderList(g, mx, my);
        int w = pageW();
        layout(w);
        pageHeight = rows.stream().mapToInt(r -> r.height).sum();
        int view = pageBottom() - pageTop();
        pageScroll = Math.max(0, Math.min(pageScroll, Math.max(0, pageHeight - view)));
        g.enableScissor(pageX() - 4, pageTop(), px + pw, pageBottom());
        int y = pageTop() - (int) pageScroll;
        boolean inPage = mx >= pageX() && mx < pageX() + w && my >= pageTop() && my < pageBottom();
        for (Row r : rows) {
            if (y + r.height >= pageTop() && y < pageBottom()) { r.draw(g, pageX(), y, w, inPage ? mx : -1, inPage ? my : -1); }
            y += r.height;
        }
        g.disableScissor();
        if (pageHeight > view) {
            int bar = Math.max(16, view * view / pageHeight), top = pageTop() + (int) ((view - bar) * pageScroll / (pageHeight - view));
            g.fill(px + pw - 3, top, px + pw - 1, top + bar, 0x80FFFFFF);
        }
        if (entries.isEmpty()) { g.drawString(font, Component.translatable("hdfv.handbook.empty"), pageX(), pageTop() + 4, DIM); }
        if (!hovered.isEmpty()) { g.renderTooltip(font, hovered, mx, my); }
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < px + LIST_W) { listScroll -= sy * ROW * 2; }
        else if (previewAt != null && over(previewAt, mx, my)) { preview.zoom(sy); }
        else { pageScroll -= sy * LINE * 3; }
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) { return true; }
        Entry e = listAt(mx, my);
        if (e != null) { open(e); return true; }
        if (button == 0 && previewAt != null && over(previewAt, mx, my)) { dragging = true; return true; }
        if (button == 0 && mx >= pageX() && my >= pageTop() && my < pageBottom()) {
            int y = pageTop() - (int) pageScroll;
            for (Row r : rows) {
                if (my >= y && my < y + r.height && r.click(pageX(), y, pageW(), mx, my)) { return true; }
                y += r.height;
            }
        }
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { preview.drag(dx, dy); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private static boolean over(int[] r, double mx, double my) { return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]; }
}
