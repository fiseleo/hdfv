package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.look.BodySurface;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.look.WingSurface;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.paint.DecalNetwork;
import dev.mcmod.hdfv.neoforge.paint.HdfvPaint;
import dev.mcmod.hdfv.neoforge.paint.PaintBoothBlockEntity;
import dev.mcmod.hdfv.neoforge.paint.PaintBoothBlockEntity.Edit;
import dev.mcmod.hdfv.neoforge.paint.PaintBoothBlockEntity.Op;
import dev.mcmod.hdfv.neoforge.paint.PaintBoothMenu;
import dev.mcmod.hdfv.neoforge.paint.PaintBoothNetwork;
import dev.mcmod.hdfv.paint.Repaint;
import dev.mcmod.hdfv.part.air.WingDesign;
import dev.mcmod.hdfv.part.body.BodyDesign;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The paint booth's screen (0.13), three pages over the vehicle in the booth as it is to be (the controller's pending
 * blueprint, every change one edit the booth checks):
 * <ol>
 *   <li>Paint: the parts that take paint in a list; the chosen part's looks (colours typed or stepped through by their
 *   swatch, finishes and stripes cycled, glass, registration).</li>
 *   <li>Stickers: the player's pictures (uploaded from the system's file dialog or dropped on the screen); a picture
 *   chosen, a click on the view puts it there (on a body or a wing); a sticker chosen, a click moves it; its width, turn,
 *   mirroring, pair and layer by buttons. Dragging turns the view, scrolling zooms.</li>
 *   <li>Start: the booth's problems, the job (paint and film by area, power, hours), start and cancel.</li>
 * </ol>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PaintBoothScreen extends AbstractContainerScreen<PaintBoothMenu> {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(HdfvPaint.MENU.get(), PaintBoothScreen::new); }

    private static final int LIST_X = 8, LIST_Y = 100, LIST_W = 162, LIST_H = 42, ROW = 10;
    private static final int PANEL_X = 176, PANEL_Y = 18, PANEL_W = 216;
    private static final int PAINT_VIEW_H = 96, STICKER_VIEW_H = 112, LOOK_Y = 118, LOOK_ROW = 14, LOOK_ROWS = 7;
    private static final int GALLERY_Y = 134, THUMB = 16, THUMBS = 9, STICKER_ROWS_Y = 172, STICKER_ROWS = 3;
    private static final double DEFAULT_WIDTH_M = 1.0;
    private static final int TEXT = 0xff404040, LIGHT = 0xffe0e0e0, RED = 0xffff7f6f, GREEN = 0xff5fdf7f, AMBER = 0xffffd040;

    public enum Page { PAINT, STICKERS, WORK }
    private static Page page = Page.PAINT;

    private final VehiclePreview preview = new VehiclePreview();
    private String selected = "";
    private int sticker = -1;
    private String armed;
    private int listScroll, lookScroll, galleryScroll, stickerScroll;
    private boolean dragging, moved;
    private String parsedFor, builtFor;
    private VehicleBlueprint parsed;
    private Component status = Component.empty();
    /** A look typed but not sent yet (sent half a second after the last key). */
    private Repaint.Look typedLook;
    private String typedText;
    private long typedAt;

    public PaintBoothScreen(PaintBoothMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PaintBoothMenu.WIDTH;
        imageHeight = PaintBoothMenu.HEIGHT;
        inventoryLabelY = PaintBoothMenu.PLAYER_Y - 11;
    }

    private PaintBoothBlockEntity booth() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof PaintBoothBlockEntity be ? be : null;
    }

    private String json() { PaintBoothBlockEntity be = booth(); return be == null ? "" : be.pendingJson(); }

    /** The vehicle as it is to be (null: none). */
    private VehicleBlueprint vehicle() {
        String json = json();
        if (!json.equals(parsedFor)) {
            parsedFor = json;
            try { parsed = json.isEmpty() ? null : VehicleBlueprint.fromJsonText(json); } catch (RuntimeException e) { parsed = null; }
        }
        return parsed;
    }

    /** The parts the booth changes: bodies, wings, wheels. */
    private List<VehicleBlueprint.Entry> parts() {
        VehicleBlueprint v = vehicle();
        if (v == null) { return List.of(); }
        return v.parts().stream().filter(e -> Repaint.paintable(e.design())).toList();
    }

    private PartDesign design() {
        return parts().stream().filter(e -> e.id().equals(selected)).map(VehicleBlueprint.Entry::design).findFirst().orElse(null);
    }

    private List<Sticker> stickers() { PartDesign d = design(); return d == null ? List.of() : Repaint.stickers(d); }

    private void send(Edit e) { PaintBoothNetwork.send(menu.pos, e); }

    // ---------------------------------------------------------------- widgets

    @Override protected void init() {
        super.init();
        builtFor = json();
        List<VehicleBlueprint.Entry> parts = parts();
        if (parts.stream().noneMatch(e -> e.id().equals(selected))) {
            selected = parts.stream().filter(e -> Repaint.takesStickers(e.design())).map(VehicleBlueprint.Entry::id).findFirst()
                    .orElse(parts.isEmpty() ? "" : parts.get(0).id());
            sticker = -1;
        }
        // the sticker chosen stays chosen across rebuilds (one just added comes with the booth's next copy)
        String[] keys = {"hdfv.paint_booth.page.paint", "hdfv.paint_booth.page.stickers", "hdfv.paint_booth.page.work"};
        for (int i = 0; i < 3; i++) {
            Page p = Page.values()[i];
            Button b = Button.builder(Component.translatable(keys[i]), x -> { page = p; rebuildWidgets(); }).bounds(leftPos + 246 + 50 * i, topPos + 3, 48, 12).build();
            b.active = page != p;
            addRenderableWidget(b);
        }
        switch (page) {
            case PAINT -> paintWidgets();
            case STICKERS -> stickerWidgets();
            case WORK -> workWidgets();
        }
    }

    private void paintWidgets() {
        PartDesign d = design();
        if (d == null) { return; }
        List<Repaint.Look> looks = Repaint.looks(d);
        lookScroll = Math.clamp(lookScroll, 0, Math.max(0, looks.size() - LOOK_ROWS));
        int x = leftPos + PANEL_X + 108, y = topPos + LOOK_Y;
        for (int i = lookScroll; i < looks.size() && i < lookScroll + LOOK_ROWS; i++, y += LOOK_ROW) {
            Repaint.Look look = looks.get(i);
            RecordEditor.Parameter p = Designs.parameters(d, look.page()).stream().filter(q -> q.name().equals(look.param())).findFirst().orElse(null);
            if (p == null) { continue; }
            if (p.type() == RecordEditor.Type.CHOICE) {
                List<String> choices = p.choices();
                String now = p.text();
                addRenderableWidget(CycleButton.<String>builder(c -> DesignScreen.choice(look.page(), p, c))
                        .withValues(choices).withInitialValue(choices.contains(now) ? now : choices.get(0)).displayOnlyValue()
                        .create(x, y, 100, 12, Component.empty(), (b, v) -> send(new Edit(Op.LOOK, selected, look.page(), look.param(), v, -1, null))));
                continue;
            }
            int w = p.type() == RecordEditor.Type.COLOR ? 72 : 100;
            EditBox box = new EditBox(font, x + (p.type() == RecordEditor.Type.COLOR ? 28 : 0), y, w, 12, Component.empty());
            box.setMaxLength(32);
            box.setValue(p.text());
            box.setResponder(text -> { typedLook = look; typedText = text; typedAt = net.minecraft.Util.getMillis(); });
            addRenderableWidget(box);
            if (p.type() == RecordEditor.Type.COLOR) {
                String next = RecordEditor.nextColor(String.valueOf(p.value()));
                addRenderableWidget(Button.builder(Component.empty(), b -> send(new Edit(Op.LOOK, selected, look.page(), look.param(), next, -1, null)))
                        .bounds(x, y, 24, 12).build());
            }
        }
    }

    private void stickerWidgets() {
        int x = leftPos + PANEL_X, y = topPos + GALLERY_Y;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { galleryScroll = Math.max(0, galleryScroll - 1); }).bounds(x, y, 10, THUMB).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> galleryScroll++).bounds(x + 12 + THUMBS * (THUMB + 2), y, 10, THUMB).build());
        addRenderableWidget(Button.builder(Component.translatable("hdfv.paint_booth.upload"), b -> DecalUpload.pick())
                .bounds(x + 24 + THUMBS * (THUMB + 2), y, PANEL_W - 24 - THUMBS * (THUMB + 2), THUMB).build());
        int by = topPos + 216, bw = 23;
        Object[][] buttons = {
                {"hdfv.paint_booth.narrower", (Runnable) () -> change(s -> scaled(s, 1 / 1.15))},
                {"hdfv.paint_booth.wider", (Runnable) () -> change(s -> scaled(s, 1.15))},
                {"hdfv.paint_booth.turn_left", (Runnable) () -> change(s -> turned(s, 15))},
                {"hdfv.paint_booth.turn_right", (Runnable) () -> change(s -> turned(s, -15))},
                {"hdfv.paint_booth.flip", (Runnable) () -> change(s -> new Sticker(s.image(), s.aspect(), s.place(), s.widthM(), s.rotationDeg(), !s.flip(), s.pair(), s.layer()))},
                {"hdfv.paint_booth.pair", (Runnable) () -> change(s -> new Sticker(s.image(), s.aspect(), s.place(), s.widthM(), s.rotationDeg(), s.flip(), !s.pair(), s.layer()))},
                {"hdfv.paint_booth.raise", (Runnable) () -> change(s -> layered(s, 1))},
                {"hdfv.paint_booth.lower", (Runnable) () -> change(s -> layered(s, -1))},
                {"hdfv.paint_booth.remove", (Runnable) () -> { if (sticker >= 0) { send(new Edit(Op.REMOVE, selected, "", "", "", sticker, null)); sticker = -1; } }},
        };
        for (int i = 0; i < buttons.length; i++) {
            Runnable r = (Runnable) buttons[i][1];
            Button b = Button.builder(Component.translatable((String) buttons[i][0]), x2 -> r.run()).bounds(leftPos + PANEL_X + i * (bw + 1), by, bw, 14).build();
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(buttons[i][0] + ".tip")));
            b.active = sticker >= 0;
            addRenderableWidget(b);
        }
        DecalUpload.refresh();
    }

    private void workWidgets() {
        int x = leftPos + PANEL_X, y = topPos + 212;
        Object[][] buttons = {{"hdfv.paint_booth.scan", Op.SCAN}, {"hdfv.paint_booth.door", Op.DOOR}, {"hdfv.paint_booth.reset", Op.RESET},
                {"hdfv.paint_booth.start", Op.START}, {"hdfv.paint_booth.cancel", Op.CANCEL}};
        for (int i = 0; i < buttons.length; i++) {
            Op op = (Op) buttons[i][1];
            addRenderableWidget(Button.builder(Component.translatable((String) buttons[i][0]), b -> send(Edit.of(op))).bounds(x + 43 * i, y, 42, 18).build());
        }
    }

    private interface Change { Sticker of(Sticker s); }

    private void change(Change how) {
        List<Sticker> s = stickers();
        if (sticker < 0 || sticker >= s.size()) { return; }
        send(new Edit(Op.SET, selected, "", "", "", sticker, how.of(s.get(sticker))));
    }

    private static Sticker scaled(Sticker s, double by) {
        return new Sticker(s.image(), s.aspect(), s.place(), Math.clamp(s.widthM() * by, Sticker.MIN_WIDTH_M, Sticker.MAX_WIDTH_M), s.rotationDeg(), s.flip(), s.pair(), s.layer());
    }

    private static Sticker turned(Sticker s, double by) {
        double r = s.rotationDeg() + by;
        r = r > 180 ? r - 360 : r < -180 ? r + 360 : r;
        return new Sticker(s.image(), s.aspect(), s.place(), s.widthM(), r, s.flip(), s.pair(), s.layer());
    }

    private static Sticker layered(Sticker s, int by) {
        return new Sticker(s.image(), s.aspect(), s.place(), s.widthM(), s.rotationDeg(), s.flip(), s.pair(), Math.clamp(s.layer() + by, 0, Sticker.LAYERS - 1));
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (typedLook != null && net.minecraft.Util.getMillis() - typedAt > 500) {
            send(new Edit(Op.LOOK, selected, typedLook.page(), typedLook.param(), typedText, -1, null));
            typedLook = null;
        }
        // the booth's copy changed (an edit taken): the widgets show it, unless a box is being typed in
        if (!json().equals(builtFor) && !(getFocused() instanceof EditBox)) { rebuildWidgets(); }
    }

    // ---------------------------------------------------------------- input

    private int viewHeight() { return page == Page.STICKERS ? STICKER_VIEW_H : PAINT_VIEW_H; }

    private boolean inView(double mx, double my) {
        return page != Page.WORK && mx >= leftPos + PANEL_X && mx < leftPos + PANEL_X + PANEL_W && my >= topPos + PANEL_Y && my < topPos + PANEL_Y + viewHeight();
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        int lx = (int) mx - leftPos - LIST_X, ly = (int) my - topPos - LIST_Y;
        if (lx >= 0 && lx < LIST_W && ly >= 0 && ly < LIST_H) {
            int row = ly / ROW + listScroll;
            List<VehicleBlueprint.Entry> parts = parts();
            if (row < parts.size()) { selected = parts.get(row).id(); sticker = -1; lookScroll = 0; rebuildWidgets(); }
            return true;
        }
        if (page == Page.STICKERS && clickGallery(mx, my)) { return true; }
        if (page == Page.STICKERS && clickStickerRow(mx, my)) { return true; }
        if (inView(mx, my)) { dragging = true; moved = false; return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && !moved && page == Page.STICKERS) { place(mx, my); }
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            if (Math.abs(dx) + Math.abs(dy) > 0.5) { moved = true; }
            preview.drag(dx, dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (inView(mx, my)) { preview.zoom(sy); return true; }
        int lx = (int) mx - leftPos - LIST_X, ly = (int) my - topPos - LIST_Y;
        if (lx >= 0 && lx < LIST_W && ly >= 0 && ly < LIST_H) {
            listScroll = Math.clamp(listScroll - (int) Math.signum(sy), 0, Math.max(0, parts().size() - LIST_H / ROW));
            return true;
        }
        if (page == Page.PAINT && mx >= leftPos + PANEL_X && my >= topPos + LOOK_Y) {
            lookScroll = Math.max(0, lookScroll - (int) Math.signum(sy));
            rebuildWidgets();
            return true;
        }
        if (page == Page.STICKERS && my >= topPos + STICKER_ROWS_Y && my < topPos + STICKER_ROWS_Y + STICKER_ROWS * ROW) {
            stickerScroll = Math.clamp(stickerScroll - (int) Math.signum(sy), 0, Math.max(0, stickers().size() - STICKER_ROWS));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override public void onFilesDrop(List<Path> files) {
        if (DecalUpload.dropped(files)) { status = Component.translatable("hdfv.paint_booth.dropped"); }
    }

    /** The pictures the gallery shows: the player's, then those the vehicle's stickers show. */
    private List<String> gallery() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        DecalNetwork.PicturesPayload mine = DecalUpload.pictures();
        if (mine != null) { for (DecalNetwork.Listed l : mine.pictures()) { out.add(l.sha()); } }
        VehicleBlueprint v = vehicle();
        if (v != null) { for (VehicleBlueprint.Entry e : v.parts()) { for (Sticker s : Repaint.stickers(e.design())) { out.add(s.image()); } } }
        return new ArrayList<>(out);
    }

    private boolean clickGallery(double mx, double my) {
        int x0 = leftPos + PANEL_X + 12, y0 = topPos + GALLERY_Y;
        if (my < y0 || my >= y0 + THUMB || mx < x0 || mx >= x0 + THUMBS * (THUMB + 2)) { return false; }
        int i = (int) ((mx - x0) / (THUMB + 2)) + galleryScroll;
        List<String> g = gallery();
        if (i < g.size()) { armed = g.get(i).equals(armed) ? null : g.get(i); sticker = armed == null ? sticker : -1; rebuildWidgets(); }
        return true;
    }

    private boolean clickStickerRow(double mx, double my) {
        int y0 = topPos + STICKER_ROWS_Y;
        if (mx < leftPos + PANEL_X || mx >= leftPos + PANEL_X + PANEL_W || my < y0 || my >= y0 + STICKER_ROWS * ROW) { return false; }
        int i = (int) ((my - y0) / ROW) + stickerScroll;
        if (i < stickers().size()) { sticker = i == sticker ? -1 : i; armed = null; rebuildWidgets(); }
        return true;
    }

    /** A click on the view: the chosen picture put there, or the chosen sticker moved there (on a body or a wing), or the part chosen. */
    private void place(double mx, double my) {
        VehiclePreview.Hit hit = preview.pick(mx, my, leftPos + PANEL_X, topPos + PANEL_Y, PANEL_W, viewHeight());
        if (hit == null) { return; }
        PartDesign d = hit.design();
        Sticker.Place at = switch (d) {
            case BodyDesign b -> {
                BodySurface.Location l = new BodySurface(b.shape()).locate(hit.local());
                yield new Sticker.OnBody(l.x(), l.t());
            }
            case WingDesign w -> {
                WingSurface.Location l = new WingSurface(w).locate(hit.local());
                yield new Sticker.OnWing(l.eta(), l.chord(), l.surface(), l.side());
            }
            default -> null;
        };
        if (!hit.id().equals(selected) && armed == null) { selected = hit.id(); sticker = -1; rebuildWidgets(); return; }
        if (at == null) { status = Component.translatable("hdfv.paint_booth.no_stickers_here"); return; }
        if (armed != null) {
            selected = hit.id();
            int count = Repaint.stickers(d).size();
            send(new Edit(Op.ADD, hit.id(), "", "", "", -1, new Sticker(armed, aspect(armed), at, DEFAULT_WIDTH_M, 0, false, false, count % Sticker.LAYERS)));
            sticker = count;
            armed = null;
            rebuildWidgets();
        } else if (sticker >= 0) {
            change(s -> new Sticker(s.image(), s.aspect(), at, s.widthM(), s.rotationDeg(), s.flip(), s.pair(), s.layer()));
        }
    }

    /** A picture's height over its width (1 until it is known). */
    private static double aspect(String sha) {
        DecalNetwork.PicturesPayload mine = DecalUpload.pictures();
        if (mine != null) {
            for (DecalNetwork.Listed l : mine.pictures()) { if (l.sha().equals(sha) && l.width() > 0) { return Math.clamp((double) l.height() / l.width(), Sticker.MIN_ASPECT, Sticker.MAX_ASPECT); } }
        }
        int[] size = DecalCache.size(sha);
        return size == null ? 1 : Math.clamp((double) size[1] / size[0], Sticker.MIN_ASPECT, Sticker.MAX_ASPECT);
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MaterialScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot s : menu.slots) { MaterialScreens.slot(g, leftPos + s.x, topPos + s.y); }
        g.fill(leftPos + LIST_X - 1, topPos + LIST_Y - 1, leftPos + LIST_X + LIST_W + 1, topPos + LIST_Y + LIST_H + 1, 0xff8b8b8b);
        g.fill(leftPos + LIST_X, topPos + LIST_Y, leftPos + LIST_X + LIST_W, topPos + LIST_Y + LIST_H, 0xff2b2b2b);
        int bottom = page == Page.WORK ? 207 : page == Page.STICKERS ? 213 : 232;
        int top = page == Page.WORK ? PANEL_Y : PANEL_Y + viewHeight() + 2;
        g.fill(leftPos + PANEL_X - 1, topPos + top - 1, leftPos + PANEL_X + PANEL_W + 1, topPos + bottom + 1, 0xff8b8b8b);
        g.fill(leftPos + PANEL_X, topPos + top, leftPos + PANEL_X + PANEL_W, topPos + bottom, 0xff2b2b2b);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // no part outlined: the outline would cover the paint and stickers (the list shows the part chosen)
        if (page != Page.WORK) { preview.render(g, font, leftPos + PANEL_X, topPos + PANEL_Y, PANEL_W, viewHeight(), json(), ""); }
        if (page == Page.STICKERS) { thumbnails(g); }
        if (page == Page.PAINT) { swatches(g); }
        renderTooltip(g, mouseX, mouseY);
    }

    private void thumbnails(GuiGraphics g) {
        List<String> pictures = gallery();
        galleryScroll = Math.clamp(galleryScroll, 0, Math.max(0, pictures.size() - THUMBS));
        int x = leftPos + PANEL_X + 12, y = topPos + GALLERY_Y;
        for (int i = galleryScroll; i < pictures.size() && i < galleryScroll + THUMBS; i++, x += THUMB + 2) {
            String sha = pictures.get(i);
            DecalCache.want(sha);
            g.fill(x - 1, y - 1, x + THUMB + 1, y + THUMB + 1, sha.equals(armed) ? 0xffffd040 : 0xff8b8b8b);
            g.fill(x, y, x + THUMB, y + THUMB, 0xff505050);
            if (DecalCache.state(sha) == DecalCache.State.SHOWN) { g.blit(DecalCache.texture(sha), x, y, 0, 0, THUMB, THUMB, THUMB, THUMB); }
        }
    }

    private void swatches(GuiGraphics g) {
        PartDesign d = design();
        if (d == null) { return; }
        List<Repaint.Look> looks = Repaint.looks(d);
        int y = topPos + LOOK_Y;
        for (int i = lookScroll; i < looks.size() && i < lookScroll + LOOK_ROWS; i++, y += LOOK_ROW) {
            Object v = Repaint.look(d, looks.get(i));
            if (v instanceof String c && RecordEditor.isColor(c, false)) {
                int x = leftPos + PANEL_X + 108;
                g.fill(x + 3, y + 2, x + 21, y + 10, RecordEditor.argb(c));
            }
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        String[] slots = {"hdfv.paint_booth.slot.cans", "hdfv.paint_booth.slot.film", "hdfv.paint_booth.slot.blueprint"};
        for (int i = 0; i < slots.length; i++) { g.drawString(font, Component.translatable(slots[i]), PaintBoothMenu.SLOT_X + 22, PaintBoothMenu.SLOT_Y + 22 * i + 5, TEXT, false); }
        g.drawString(font, Component.translatable("hdfv.paint_booth.parts"), LIST_X, LIST_Y - 10, TEXT, false);
        List<VehicleBlueprint.Entry> parts = parts();
        for (int i = listScroll, y = LIST_Y + 1; i < parts.size() && y + ROW <= LIST_Y + LIST_H; i++, y += ROW) {
            VehicleBlueprint.Entry e = parts.get(i);
            boolean on = e.id().equals(selected);
            if (on) { g.fill(LIST_X, y - 1, LIST_X + LIST_W, y + ROW - 1, 0xff3f5f7f); }
            int n = Repaint.stickers(e.design()).size();
            g.drawString(font, font.plainSubstrByWidth(e.id() + (n > 0 ? "  (" + n + ")" : ""), LIST_W - 4), LIST_X + 2, y, on ? 0xffffffff : 0xffc0c0c0, false);
        }
        if (parts.isEmpty()) { g.drawString(font, Component.translatable("hdfv.paint_booth.no_vehicle"), LIST_X + 2, LIST_Y + 2, 0xff808080, false); }
        switch (page) {
            case PAINT -> paintLabels(g);
            case STICKERS -> stickerLabels(g);
            case WORK -> workLabels(g);
        }
    }

    private void paintLabels(GuiGraphics g) {
        PartDesign d = design();
        if (d == null) { return; }
        List<Repaint.Look> looks = Repaint.looks(d);
        int y = LOOK_Y + 2;
        for (int i = lookScroll; i < looks.size() && i < lookScroll + LOOK_ROWS; i++, y += LOOK_ROW) {
            Repaint.Look l = looks.get(i);
            String pageKey = l.page().startsWith(BodyDesign.MODULE) ? "module" : l.page();
            Component name = Component.translatable("hdfv.param." + pageKey + "." + l.param());
            if (l.page().startsWith(BodyDesign.MODULE)) { name = Component.literal(l.page().substring(BodyDesign.MODULE.length()) + ": ").append(name); }
            g.drawString(font, font.plainSubstrByWidth(name.getString(), 104), PANEL_X + 2, y, LIGHT, false);
        }
        if (looks.size() > LOOK_ROWS) {
            g.drawString(font, Component.translatable("hdfv.paint_booth.more", lookScroll + 1, Math.min(looks.size(), lookScroll + LOOK_ROWS), looks.size()),
                    PANEL_X + 2, LOOK_Y + LOOK_ROWS * LOOK_ROW + 2, 0xff808080, false);
        }
    }

    private void stickerLabels(GuiGraphics g) {
        int y = GALLERY_Y + THUMB + 3;
        Component upload = DecalUpload.status();
        Component hint = armed != null ? Component.translatable("hdfv.paint_booth.click_to_place")
                : sticker >= 0 ? Component.translatable("hdfv.paint_booth.click_to_move") : Component.translatable("hdfv.paint_booth.pick_picture");
        g.drawString(font, font.plainSubstrByWidth((upload.getString().isEmpty() ? (status.getString().isEmpty() ? hint : status) : upload).getString(), PANEL_W - 4),
                PANEL_X + 2, y, AMBER, false);
        List<Sticker> list = stickers();
        stickerScroll = Math.clamp(stickerScroll, 0, Math.max(0, list.size() - STICKER_ROWS));
        int ry = STICKER_ROWS_Y + 1;
        for (int i = stickerScroll; i < list.size() && i < stickerScroll + STICKER_ROWS; i++, ry += ROW) {
            Sticker s = list.get(i);
            boolean on = i == sticker;
            if (on) { g.fill(PANEL_X, ry - 1, PANEL_X + PANEL_W, ry + ROW - 1, 0xff3f5f7f); }
            String where = s.place() instanceof Sticker.OnWing w ? String.format(Locale.ROOT, "%s %.2f/%.2f", w.surface().name().toLowerCase(Locale.ROOT), w.eta(), w.chord())
                    : String.format(Locale.ROOT, "x %.2f", ((Sticker.OnBody) s.place()).x());
            String line = String.format(Locale.ROOT, "%d  %s  %.2f m  %.0f°%s%s  L%d  %s", i + 1, s.image().substring(0, 6), s.widthM(), s.rotationDeg(),
                    s.flip() ? "  ⇋" : "", s.pair() ? "  ×2" : "", s.layer(), where);
            g.drawString(font, font.plainSubstrByWidth(line, PANEL_W - 4), PANEL_X + 2, ry, on ? 0xffffffff : 0xffc0c0c0, false);
        }
        if (list.isEmpty()) { g.drawString(font, Component.translatable("hdfv.paint_booth.no_stickers"), PANEL_X + 2, ry, 0xff808080, false); }
    }

    private void workLabels(GuiGraphics g) {
        PaintBoothBlockEntity be = booth();
        CompoundTag r = be == null ? new CompoundTag() : be.report();
        List<Component> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        if (r.contains("Vehicle")) { lines.add(Component.translatable("hdfv.paint_booth.vehicle", r.getString("Vehicle"))); colours.add(LIGHT); }
        for (Component p : PaintBoothBlockEntity.problems(r)) { lines.add(p); colours.add(RED); }
        CompoundTag j = r.getCompound("Job");
        double hours = j.getDouble("Hours");
        if (hours > 0) {
            lines.add(Component.translatable("hdfv.paint_booth.job.paint", fmt(j.getDouble("Repaint")), j.getInt("Cans"), count(0))); colours.add(LIGHT);
            lines.add(Component.translatable("hdfv.paint_booth.job.film", fmt(j.getDouble("Film")), j.getInt("Films"), count(1))); colours.add(LIGHT);
            double minutes = hours * dev.mcmod.hdfv.manufacture.Pace.TICKS_PER_WORK_HOUR / 20 / 60;
            lines.add(Component.translatable("hdfv.paint_booth.job.time", fmt(hours), fmt(minutes), fmt(j.getDouble("KWh")))); colours.add(LIGHT);
        } else if (be != null && !be.pendingJson().isEmpty()) {
            lines.add(Component.translatable("hdfv.paint_booth.nothing")); colours.add(0xffc0c0c0);
        }
        lines.add(Component.translatable("hdfv.paint_booth.stickers", j.getInt("Stickers"), Sticker.MAX)); colours.add(0xffc0c0c0);
        if (menu.working()) {
            lines.add(Component.translatable(menu.starved() ? "hdfv.paint_booth.waiting_power" : "hdfv.paint_booth.working", String.format(Locale.ROOT, "%.1f", menu.permille() / 10.0)));
            colours.add(menu.starved() ? RED : AMBER);
        }
        lines.add(Component.translatable("hdfv.paint_booth.energy", menu.energy())); colours.add(0xffc0c0c0);
        if (r.contains("Message")) {
            try {
                Component m = Component.Serializer.fromJson(r.getString("Message"), minecraft.level.registryAccess());
                if (m != null) { lines.add(m); colours.add(GREEN); }
            } catch (RuntimeException ignored) { }
        }
        int y = PANEL_Y + 3;
        for (int i = 0; i < lines.size(); i++) {
            for (var part : font.split(lines.get(i), PANEL_W - 6)) {
                if (y > 200) { break; }
                g.drawString(font, part, PANEL_X + 3, y, colours.get(i), false);
                y += 10;
            }
        }
    }

    private int count(int slot) { return menu.slots.get(slot).getItem().getCount(); }

    // ---------------------------------------------------------------- development (AutoShot: /hdfvdev booth ...)

    /** Shows a page. */
    void devPage(Page p) { page = p; rebuildWidgets(); }

    /** Chooses a picture of the gallery and clicks the view at a fraction of its width and height (as a player would). */
    void devPlace(int picture, double fx, double fy) {
        List<String> g = gallery();
        if (picture < 0 || picture >= g.size()) { return; }
        page = Page.STICKERS;
        armed = g.get(picture);
        place(leftPos + PANEL_X + fx * PANEL_W, topPos + PANEL_Y + fy * STICKER_VIEW_H);
    }

    /** Chooses a part. */
    void devSelect(String part) { selected = part; sticker = -1; rebuildWidgets(); }

    /** Sets a look of the chosen part (as the screen's controls do). */
    void devLook(String page, String param, String text) { send(new Edit(Op.LOOK, selected, page, param, text, -1, null)); }

    /** One of the booth's actions. */
    void devAction(Op op) { send(Edit.of(op)); }

    /** Turns the view. */
    void devView(float yaw, float pitch) { preview.drag(0, 0); preview.devView(yaw, pitch); }

    private static String fmt(double v) { return String.format(Locale.ROOT, v < 10 ? "%.2f" : "%.1f", v); }
}
