package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.design.RecordEditor;
import dev.mcmod.hdfv.look.BodySurface;
import dev.mcmod.hdfv.look.LampLayout;
import dev.mcmod.hdfv.look.LampRules;
import dev.mcmod.hdfv.look.LampSpec;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.refit.HdfvRefit;
import dev.mcmod.hdfv.neoforge.refit.RefitNetwork;
import dev.mcmod.hdfv.neoforge.refit.RefitShopBlockEntity;
import dev.mcmod.hdfv.neoforge.refit.RefitShopBlockEntity.Edit;
import dev.mcmod.hdfv.neoforge.refit.RefitShopBlockEntity.Op;
import dev.mcmod.hdfv.neoforge.refit.RefitShopMenu;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.refit.Refit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The refit shop's screen (0.16), seven pages over the vehicle in the shop as it is to be (the controller's pending
 * blueprint, every change one edit the shop checks):
 * <ol>
 *   <li>Shape, Size, Kit, Rims, Tuning: the chosen part's adjustables of that kind (Refit.adjustables), typed, cycled or
 *   stepped through; the size page also scales the whole vehicle.</li>
 *   <li>Lamps: the chosen body's lamps (its own, or the kit's to start from); a lamp chosen, a click on the view moves
 *   it there; "add" and a click puts a new one there; its role, style, size, pair, aim and colours by buttons.</li>
 *   <li>Work: the shop's and the vehicle's problems, what changes, what the job takes and gives back, writing a part's
 *   new design onto a blank blueprint, start and cancel.</li>
 * </ol>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RefitShopScreen extends AbstractContainerScreen<RefitShopMenu> {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(HdfvRefit.MENU.get(), RefitShopScreen::new); }

    private static final int LIST_X = 8, LIST_Y = 100, LIST_W = 162, LIST_H = 42, ROW = 10;
    private static final int PANEL_X = 176, PANEL_Y = 18, PANEL_W = 216, VIEW_H = 96, PARAM_Y = 118, PARAM_ROW = 14, PARAM_ROWS = 7;
    private static final int TEXT = 0xff404040, LIGHT = 0xffe0e0e0, RED = 0xffff7f6f, GREEN = 0xff5fdf7f, AMBER = 0xffffd040, GREY = 0xffc0c0c0;

    public enum Page {
        SHAPE(Refit.Category.SHAPE), SIZE(Refit.Category.SIZE), KIT(Refit.Category.KIT), RIM(Refit.Category.RIM), TUNE(Refit.Category.TUNE), LAMPS(null), WORK(null);
        final Refit.Category category;
        Page(Refit.Category c) { category = c; }
    }
    private static Page page = Page.SHAPE;

    private final VehiclePreview preview = new VehiclePreview();
    private String selected = "";
    private int lamp = -1;
    private boolean adding;
    private LampSpec template = new LampSpec(LampRules.Role.HEAD_LOW, LampSpec.Style.ROUND, LampSpec.Face.FRONT, 0, 0, -0.7, 0.55, 0, true, 0.16, "", "", 0, 0);
    private int listScroll, paramScroll;
    private boolean dragging, moved;
    private String parsedFor, builtFor;
    private VehicleBlueprint parsed;
    private Component status = Component.empty();
    private Refit.Adjustable typed;
    private String typedText;
    private long typedAt;

    public RefitShopScreen(RefitShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = RefitShopMenu.WIDTH;
        imageHeight = RefitShopMenu.HEIGHT;
        inventoryLabelY = RefitShopMenu.PLAYER_Y - 11;
    }

    private RefitShopBlockEntity shop() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof RefitShopBlockEntity be ? be : null;
    }

    private String json() { RefitShopBlockEntity be = shop(); return be == null ? "" : be.pendingJson(); }

    private VehicleBlueprint vehicle() {
        String json = json();
        if (!json.equals(parsedFor)) {
            parsedFor = json;
            try { parsed = json.isEmpty() ? null : VehicleBlueprint.fromJsonText(json); } catch (RuntimeException e) { parsed = null; }
        }
        return parsed;
    }

    /** The parts this page changes: those with adjustables of its kind; the bodies for lamps; every refittable one for work. */
    private List<VehicleBlueprint.Entry> parts() {
        VehicleBlueprint v = vehicle();
        if (v == null) { return List.of(); }
        return v.parts().stream().filter(e -> switch (page) {
            case LAMPS -> e.design() instanceof BodyDesign;
            case WORK -> Refit.refittable(e.design());
            default -> !Refit.adjustables(e.design(), page.category).isEmpty();
        }).toList();
    }

    private PartDesign design() {
        VehicleBlueprint v = vehicle();
        VehicleBlueprint.Entry e = v == null ? null : v.part(selected);
        return e == null ? null : e.design();
    }

    private List<LampSpec> lamps() { return design() instanceof BodyDesign b ? Refit.lamps(b) : List.of(); }

    private void send(Edit e) { RefitNetwork.send(menu.pos, e); }

    private Edit edit(Op op, String page, String param, String text, int index, double value, LampSpec l) { return new Edit(op, selected, page, param, text, index, value, l); }

    // ---------------------------------------------------------------- widgets

    @Override protected void init() {
        super.init();
        builtFor = json();
        List<VehicleBlueprint.Entry> parts = parts();
        if (parts.stream().noneMatch(e -> e.id().equals(selected))) { selected = parts.isEmpty() ? "" : parts.get(0).id(); lamp = -1; }
        Page[] pages = Page.values();
        for (int i = 0; i < pages.length; i++) {
            Page p = pages[i];
            Button b = Button.builder(Component.translatable("hdfv.refit_shop.page." + p.name().toLowerCase(Locale.ROOT)), x -> { page = p; paramScroll = 0; rebuildWidgets(); })
                    .bounds(leftPos + PANEL_X + 31 * i, topPos + 3, 30, 12).build();
            b.active = page != p;
            addRenderableWidget(b);
        }
        switch (page) {
            case LAMPS -> lampWidgets();
            case WORK -> workWidgets();
            default -> paramWidgets();
        }
    }

    private List<Refit.Adjustable> adjustables() {
        PartDesign d = design();
        return d == null || page.category == null ? List.of() : Refit.adjustables(d, page.category);
    }

    private int paramRows() { return page == Page.SIZE ? PARAM_ROWS - 1 : PARAM_ROWS; }
    private int paramTop() { return PARAM_Y + (page == Page.SIZE ? PARAM_ROW : 0); }

    private void paramWidgets() {
        if (page == Page.SIZE) {
            RefitShopBlockEntity be = shop();
            double scale = be == null ? 1 : be.scale();
            int x = leftPos + PANEL_X + 108, y = topPos + PARAM_Y;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> send(edit(Op.SCALE, "", "", "", -1, Math.round(scale / 1.05 * 1000) / 1000.0, null)))
                    .bounds(x, y, 14, 12).build());
            EditBox box = new EditBox(font, x + 16, y, 52, 12, Component.empty());
            box.setValue(String.format(Locale.ROOT, "%.3f", scale));
            box.setResponder(t -> { typed = new Refit.Adjustable("", "scale", Refit.Category.SIZE); typedText = t; typedAt = net.minecraft.Util.getMillis(); });
            addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("+"), b -> send(edit(Op.SCALE, "", "", "", -1, Math.round(scale * 1.05 * 1000) / 1000.0, null)))
                    .bounds(x + 70, y, 14, 12).build());
        }
        PartDesign d = design();
        if (d == null) { return; }
        List<Refit.Adjustable> list = adjustables();
        paramScroll = Math.clamp(paramScroll, 0, Math.max(0, list.size() - paramRows()));
        int x = leftPos + PANEL_X + 108, y = topPos + paramTop();
        for (int i = paramScroll; i < list.size() && i < paramScroll + paramRows(); i++, y += PARAM_ROW) {
            Refit.Adjustable a = list.get(i);
            RecordEditor.Parameter p = Designs.parameters(d, a.page()).stream().filter(q -> q.name().equals(a.param())).findFirst().orElse(null);
            if (p == null) { continue; }
            if (p.type() == RecordEditor.Type.CHOICE || p.type() == RecordEditor.Type.SWITCH) {
                List<String> choices = p.type() == RecordEditor.Type.SWITCH ? List.of("true", "false") : p.choices();
                String now = p.text();
                addRenderableWidget(CycleButton.<String>builder(c -> p.type() == RecordEditor.Type.SWITCH ? Component.translatable(c.equals("true") ? "hdfv.screen.yes" : "hdfv.screen.no")
                                : DesignScreen.choice(a.page(), p, c))
                        .withValues(choices).withInitialValue(choices.contains(now) ? now : choices.get(0)).displayOnlyValue()
                        .create(x, y, 100, 12, Component.empty(), (b, v) -> send(edit(Op.SET, a.page(), a.param(), v, -1, 0, null))));
                continue;
            }
            boolean colour = p.type() == RecordEditor.Type.COLOR;
            EditBox box = new EditBox(font, x + (colour ? 28 : 0), y, colour ? 72 : 70, 12, Component.empty());
            box.setMaxLength(p.type() == RecordEditor.Type.NUMBERS ? 120 : 32);
            box.setValue(p.text());
            box.moveCursorToStart(false);   // a long value shows its start, not its last digits
            box.setResponder(text -> { typed = a; typedText = text; typedAt = net.minecraft.Util.getMillis(); });
            addRenderableWidget(box);
            if (colour) {
                String next = RecordEditor.nextColor(String.valueOf(p.value()));
                addRenderableWidget(Button.builder(Component.empty(), b -> send(edit(Op.SET, a.page(), a.param(), next, -1, 0, null))).bounds(x, y, 24, 12).build());
            }
        }
    }

    private void lampWidgets() {
        int x = leftPos + PANEL_X, y = topPos + 186, w = 30;
        Object[][] row1 = {
                {"hdfv.refit_shop.lamp.kit", (Runnable) () -> { send(edit(Op.LAMPS_KIT, "", "", "", -1, 0, null)); lamp = -1; }},
                {"hdfv.refit_shop.lamp.add", (Runnable) () -> { adding = !adding; lamp = -1; rebuildWidgets(); }},
                {"hdfv.refit_shop.lamp.remove", (Runnable) () -> { if (lamp >= 0) { send(edit(Op.LAMP_REMOVE, "", "", "", lamp, 0, null)); lamp = -1; } }},
                {"hdfv.refit_shop.lamp.role", (Runnable) () -> change(l -> new LampSpec(next(l.role(), LampRules.Role.values(), LampRules.Role.PANEL), l.style(), l.face(), l.x(), l.t(), l.across(), l.up(),
                        l.standM(), l.pair(), l.sizeM(), l.lightColor(), l.housingColor(), l.yawDeg(), l.pitchDeg()))},
                {"hdfv.refit_shop.lamp.style", (Runnable) () -> change(l -> new LampSpec(l.role(), next(l.style(), LampSpec.Style.values(), null), l.face(), l.x(), l.t(), l.across(), l.up(),
                        l.standM(), l.pair(), l.sizeM(), l.lightColor(), l.housingColor(), l.yawDeg(), l.pitchDeg()))},
                {"hdfv.refit_shop.lamp.pair", (Runnable) () -> change(l -> new LampSpec(l.role(), l.style(), l.face(), l.x(), l.t(), l.across(), l.up(), l.standM(), !l.pair(), l.sizeM(),
                        l.lightColor(), l.housingColor(), l.yawDeg(), l.pitchDeg()))},
                {"hdfv.refit_shop.lamp.smaller", (Runnable) () -> change(l -> sized(l, 1 / 1.15))},
        };
        Object[][] row2 = {
                {"hdfv.refit_shop.lamp.larger", (Runnable) () -> change(l -> sized(l, 1.15))},
                {"hdfv.refit_shop.lamp.left", (Runnable) () -> change(l -> aimed(l, 5, 0))},
                {"hdfv.refit_shop.lamp.right", (Runnable) () -> change(l -> aimed(l, -5, 0))},
                {"hdfv.refit_shop.lamp.up", (Runnable) () -> change(l -> aimed(l, 0, 5))},
                {"hdfv.refit_shop.lamp.down", (Runnable) () -> change(l -> aimed(l, 0, -5))},
                {"hdfv.refit_shop.lamp.colour", (Runnable) () -> change(l -> new LampSpec(l.role(), l.style(), l.face(), l.x(), l.t(), l.across(), l.up(), l.standM(), l.pair(), l.sizeM(),
                        l.lightColor().isEmpty() ? RecordEditor.PALETTE.get(0) : RecordEditor.nextColor(l.lightColor()), l.housingColor(), l.yawDeg(), l.pitchDeg()))},
                {"hdfv.refit_shop.lamp.housing", (Runnable) () -> change(l -> new LampSpec(l.role(), l.style(), l.face(), l.x(), l.t(), l.across(), l.up(), l.standM(), l.pair(), l.sizeM(),
                        l.lightColor(), l.housingColor().isEmpty() ? RecordEditor.PALETTE.get(1) : RecordEditor.nextColor(l.housingColor()), l.yawDeg(), l.pitchDeg()))},
        };
        int r = 0;
        for (Object[][] row : new Object[][][]{row1, row2}) {
            for (int i = 0; i < row.length; i++) {
                Runnable run = (Runnable) row[i][1];
                String key = (String) row[i][0];
                Button b = Button.builder(Component.translatable(key), x2 -> run.run()).bounds(x + i * (w + 1), y + r * 15, w, 14).build();
                b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(key + ".tip")));
                b.active = key.endsWith(".kit") || key.endsWith(".add") || lamp >= 0 || adding;
                addRenderableWidget(b);
            }
            r++;
        }
    }

    private static <E extends Enum<E>> E next(E now, E[] all, E skip) {
        E n = all[(now.ordinal() + 1) % all.length];
        return n == skip ? all[(n.ordinal() + 1) % all.length] : n;
    }

    private static LampSpec sized(LampSpec l, double by) {
        return new LampSpec(l.role(), l.style(), l.face(), l.x(), l.t(), l.across(), l.up(), l.standM(), l.pair(), Math.clamp(l.sizeM() * by, LampSpec.MIN_SIZE_M, LampSpec.MAX_SIZE_M),
                l.lightColor(), l.housingColor(), l.yawDeg(), l.pitchDeg());
    }

    private static LampSpec aimed(LampSpec l, double yaw, double pitch) {
        return new LampSpec(l.role(), l.style(), l.face(), l.x(), l.t(), l.across(), l.up(), l.standM(), l.pair(), l.sizeM(), l.lightColor(), l.housingColor(),
                Math.clamp(l.yawDeg() + yaw, -60, 60), Math.clamp(l.pitchDeg() + pitch, -60, 60));
    }

    private interface Change { LampSpec of(LampSpec l); }

    /** Changes the chosen lamp (or, while adding, the new lamp's template). */
    private void change(Change how) {
        if (adding) { template = how.of(template); return; }
        List<LampSpec> list = lamps();
        if (lamp < 0 || lamp >= list.size()) { return; }
        send(edit(Op.LAMP_SET, "", "", "", lamp, 0, how.of(list.get(lamp))));
    }

    private void workWidgets() {
        int x = leftPos + PANEL_X, y = topPos + 212;
        Object[][] buttons = {{"hdfv.refit_shop.scan", Op.SCAN}, {"hdfv.refit_shop.door", Op.DOOR}, {"hdfv.refit_shop.reset", Op.RESET},
                {"hdfv.refit_shop.write", Op.WRITE}, {"hdfv.refit_shop.start", Op.START}, {"hdfv.refit_shop.cancel", Op.CANCEL}};
        for (int i = 0; i < buttons.length; i++) {
            Op op = (Op) buttons[i][1];
            Button b = Button.builder(Component.translatable((String) buttons[i][0]), x2 -> send(op == Op.WRITE ? edit(Op.WRITE, "", "", "", -1, 0, null) : Edit.of(op)))
                    .bounds(x + 36 * i, y, 35, 18).build();
            if (op == Op.WRITE) { b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("hdfv.refit_shop.write.tip"))); }
            addRenderableWidget(b);
        }
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (typed != null && net.minecraft.Util.getMillis() - typedAt > 600) {
            if (typed.param().equals("scale")) {
                try { send(edit(Op.SCALE, "", "", "", -1, Double.parseDouble(typedText.trim()), null)); } catch (NumberFormatException ignored) { }
            } else {
                send(edit(Op.SET, typed.page(), typed.param(), typedText, -1, 0, null));
            }
            typed = null;
        }
        if (!json().equals(builtFor) && !(getFocused() instanceof EditBox)) { rebuildWidgets(); }
    }

    // ---------------------------------------------------------------- input

    private boolean inView(double mx, double my) {
        return page != Page.WORK && mx >= leftPos + PANEL_X && mx < leftPos + PANEL_X + PANEL_W && my >= topPos + PANEL_Y && my < topPos + PANEL_Y + VIEW_H;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        int lx = (int) mx - leftPos - LIST_X, ly = (int) my - topPos - LIST_Y;
        if (lx >= 0 && lx < LIST_W && ly >= 0 && ly < LIST_H) {
            int row = ly / ROW + listScroll;
            List<VehicleBlueprint.Entry> parts = parts();
            if (row < parts.size()) { selected = parts.get(row).id(); lamp = -1; paramScroll = 0; rebuildWidgets(); }
            return true;
        }
        if (page == Page.LAMPS && clickLampRow(mx, my)) { return true; }
        if (inView(mx, my)) { dragging = true; moved = false; return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && !moved && page == Page.LAMPS) { placeLamp(mx, my); }
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
        if (page == Page.LAMPS && mx >= leftPos + PANEL_X && my >= topPos + LAMP_ROWS_Y && my < topPos + LAMP_ROWS_Y + LAMP_ROWS * ROW) {
            lampScroll = Math.clamp(lampScroll - (int) Math.signum(sy), 0, Math.max(0, lamps().size() - LAMP_ROWS));
            return true;
        }
        if (page.category != null && mx >= leftPos + PANEL_X && my >= topPos + paramTop()) {
            paramScroll = Math.max(0, paramScroll - (int) Math.signum(sy));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // the lamp page under the view: the hint (118), the lamps (four rows from 130), the count (172), the buttons (186)
    private static final int LAMP_ROWS_Y = 130, LAMP_ROWS = 4;
    private int lampScroll;

    private boolean clickLampRow(double mx, double my) {
        int y0 = topPos + LAMP_ROWS_Y;
        if (mx < leftPos + PANEL_X || mx >= leftPos + PANEL_X + PANEL_W || my < y0 || my >= y0 + LAMP_ROWS * ROW) { return false; }
        int i = (int) ((my - y0) / ROW) + lampScroll;
        if (i < lamps().size()) { lamp = i == lamp ? -1 : i; adding = false; rebuildWidgets(); }
        return true;
    }

    /** A click on the view: the new lamp put there, or the chosen lamp moved there (on the chosen body). */
    private void placeLamp(double mx, double my) {
        VehiclePreview.Hit hit = preview.pick(mx, my, leftPos + PANEL_X, topPos + PANEL_Y, PANEL_W, VIEW_H);
        if (hit == null || !(hit.design() instanceof BodyDesign b)) { status = Component.translatable("hdfv.refit_shop.lamp.not_body"); return; }
        if (!hit.id().equals(selected)) { selected = hit.id(); lamp = -1; rebuildWidgets(); }
        BodySurface s = new BodySurface(b.shape());
        if (adding) {
            send(edit(Op.LAMP_ADD, "", "", "", -1, 0, LampLayout.placeAt(b, s, hit.local(), template)));
            lamp = Refit.lamps(b).size();   // the new one, after the body's own (or the kit's it starts from)
            adding = false;
            rebuildWidgets();
        } else if (lamp >= 0 && lamp < Refit.lamps(b).size()) {
            send(edit(Op.LAMP_SET, "", "", "", lamp, 0, LampLayout.placeAt(b, s, hit.local(), Refit.lamps(b).get(lamp))));
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MaterialScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot s : menu.slots) { MaterialScreens.slot(g, leftPos + s.x, topPos + s.y); }
        g.fill(leftPos + LIST_X - 1, topPos + LIST_Y - 1, leftPos + LIST_X + LIST_W + 1, topPos + LIST_Y + LIST_H + 1, 0xff8b8b8b);
        g.fill(leftPos + LIST_X, topPos + LIST_Y, leftPos + LIST_X + LIST_W, topPos + LIST_Y + LIST_H, 0xff2b2b2b);
        int top = page == Page.WORK ? PANEL_Y : PANEL_Y + VIEW_H + 2, bottom = page == Page.WORK ? 207 : page == Page.LAMPS ? 183 : 232;
        g.fill(leftPos + PANEL_X - 1, topPos + top - 1, leftPos + PANEL_X + PANEL_W + 1, topPos + bottom + 1, 0xff8b8b8b);
        g.fill(leftPos + PANEL_X, topPos + top, leftPos + PANEL_X + PANEL_W, topPos + bottom, 0xff2b2b2b);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // the chosen part lit up, but not a body: it is the whole vehicle and its shape is what the page changes
        if (page != Page.WORK) { preview.render(g, font, leftPos + PANEL_X, topPos + PANEL_Y, PANEL_W, VIEW_H, json(), page == Page.LAMPS || design() instanceof BodyDesign ? "" : selected); }
        if (page.category != null) { swatches(g); }
        refusal(g);
        renderTooltip(g, mouseX, mouseY);
    }

    private void swatches(GuiGraphics g) {
        PartDesign d = design();
        if (d == null) { return; }
        List<Refit.Adjustable> list = adjustables();
        int y = topPos + paramTop();
        for (int i = paramScroll; i < list.size() && i < paramScroll + paramRows(); i++, y += PARAM_ROW) {
            Refit.Adjustable a = list.get(i);
            Object v = Designs.parameters(d, a.page()).stream().filter(q -> q.name().equals(a.param())).map(RecordEditor.Parameter::value).findFirst().orElse(null);
            if (v instanceof String c && RecordEditor.isColor(c, false)) {
                int x = leftPos + PANEL_X + 108;
                g.fill(x + 3, y + 2, x + 21, y + 10, RecordEditor.argb(c));
            }
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        String[] slots = {"hdfv.refit_shop.slot.blanks", "hdfv.refit_shop.slot.vehicle"};
        for (int i = 0; i < slots.length; i++) { g.drawString(font, Component.translatable(slots[i]), RefitShopMenu.SLOT_X + 22, RefitShopMenu.SLOT_Y + 22 * i + 5, TEXT, false); }
        g.drawString(font, Component.translatable("hdfv.refit_shop.parts"), LIST_X, LIST_Y - 10, TEXT, false);
        List<VehicleBlueprint.Entry> parts = parts();
        for (int i = listScroll, y = LIST_Y + 1; i < parts.size() && y + ROW <= LIST_Y + LIST_H; i++, y += ROW) {
            VehicleBlueprint.Entry e = parts.get(i);
            boolean on = e.id().equals(selected);
            if (on) { g.fill(LIST_X, y - 1, LIST_X + LIST_W, y + ROW - 1, 0xff3f5f7f); }
            g.drawString(font, font.plainSubstrByWidth(e.id() + "  " + e.design().name(), LIST_W - 4), LIST_X + 2, y, on ? 0xffffffff : GREY, false);
        }
        if (parts.isEmpty()) { g.drawString(font, Component.translatable("hdfv.refit_shop.no_parts"), LIST_X + 2, LIST_Y + 2, 0xff808080, false); }
        switch (page) {
            case LAMPS -> lampLabels(g);
            case WORK -> workLabels(g);
            default -> paramLabels(g);
        }
    }

    private void paramLabels(GuiGraphics g) {
        if (page == Page.SIZE) { g.drawString(font, Component.translatable("hdfv.refit_shop.scale"), PANEL_X + 2, PARAM_Y + 2, AMBER, false); }
        PartDesign d = design();
        if (d == null) { return; }
        List<Refit.Adjustable> list = adjustables();
        int y = paramTop() + 2;
        for (int i = paramScroll; i < list.size() && i < paramScroll + paramRows(); i++, y += PARAM_ROW) {
            Refit.Adjustable a = list.get(i);
            Component name = DesignScreen.label(a.page(), a.param());
            String prefix = a.page().matches("[a-z]+\\d+") ? a.page().replaceAll("\\D", "") + ": " : "";
            RecordEditor.Parameter p = Designs.parameters(d, a.page()).stream().filter(q -> q.name().equals(a.param())).findFirst().orElse(null);
            String unit = p == null || p.unit().isEmpty() ? "" : " (" + p.unit() + ")";
            g.drawString(font, font.plainSubstrByWidth(prefix + name.getString() + unit, 104), PANEL_X + 2, y, LIGHT, false);
        }
        if (list.size() > paramRows()) {
            g.drawString(font, Component.translatable("hdfv.paint_booth.more", paramScroll + 1, Math.min(list.size(), paramScroll + paramRows()), list.size()),
                    PANEL_X + 2, paramTop() + paramRows() * PARAM_ROW + 2, 0xff808080, false);
        }
    }

    /** The last refused edit, for five seconds after it: over the bottom of the view (or the work page's top). */
    private void refusal(GuiGraphics g) {
        RefitShopBlockEntity be = shop();
        if (be == null || minecraft == null || minecraft.level == null || !be.report().contains("Refused")) { return; }
        if (minecraft.level.getGameTime() - be.report().getLong("RefusedAt") > 100) { return; }
        Component why;
        try { why = Component.Serializer.fromJson(be.report().getString("Refused"), minecraft.level.registryAccess()); } catch (RuntimeException e) { return; }
        if (why == null) { return; }
        var lines = font.split(why, PANEL_W - 6);
        int x = leftPos + PANEL_X, y = topPos + (page == Page.WORK ? 196 : PANEL_Y + VIEW_H) - 10 * Math.min(lines.size(), 2) - 2;
        g.fill(x, y, x + PANEL_W, y + 10 * Math.min(lines.size(), 2) + 2, 0xd0300808);
        for (int i = 0; i < lines.size() && i < 2; i++) { g.drawString(font, lines.get(i), x + 3, y + 2 + 10 * i, RED, false); }
    }

    private void lampLabels(GuiGraphics g) {
        Component hint = adding ? Component.translatable("hdfv.refit_shop.lamp.click_to_add", Component.translatable("hdfv.lamp.role." + template.role().name().toLowerCase(Locale.ROOT)))
                : lamp >= 0 ? Component.translatable("hdfv.refit_shop.lamp.click_to_move") : Component.translatable("hdfv.refit_shop.lamp.pick");
        g.drawString(font, font.plainSubstrByWidth((status.getString().isEmpty() ? hint : status).getString(), PANEL_W - 4), PANEL_X + 2, PANEL_Y + VIEW_H + 4, AMBER, false);
        List<LampSpec> list = lamps();
        PartDesign d = design();
        if (d instanceof BodyDesign b && !b.settings().ownLamps() && !list.isEmpty()) {
            g.drawString(font, Component.translatable("hdfv.refit_shop.lamp.kit_lamps"), PANEL_X + 110, LAMP_ROWS_Y + LAMP_ROWS * ROW + 2, GREY, false);
        }
        if (d instanceof BodyDesign && list.isEmpty()) {
            g.drawString(font, Component.translatable("hdfv.refit_shop.lamp.none"), PANEL_X + 2, LAMP_ROWS_Y + 1, GREY, false);
        }
        lampScroll = Math.clamp(lampScroll, 0, Math.max(0, list.size() - LAMP_ROWS));
        int y = LAMP_ROWS_Y + 1;
        for (int i = lampScroll; i < list.size() && i < lampScroll + LAMP_ROWS; i++, y += ROW) {
            LampSpec l = list.get(i);
            boolean on = i == lamp;
            if (on) { g.fill(PANEL_X, y - 1, PANEL_X + PANEL_W, y + ROW - 1, 0xff3f5f7f); }
            String where = l.face() == LampSpec.Face.SIDE ? String.format(Locale.ROOT, "x %.2f", l.x()) : Component.translatable("hdfv.refit_shop.lamp.face." + l.face().name().toLowerCase(Locale.ROOT)).getString();
            String line = String.format(Locale.ROOT, "%d  %s  %s  %.2f m%s  %s", i + 1, Component.translatable("hdfv.lamp.role." + l.role().name().toLowerCase(Locale.ROOT)).getString(),
                    Component.translatable("hdfv.lamp.style." + l.style().name().toLowerCase(Locale.ROOT)).getString(), l.sizeM(), l.pair() ? "  ×2" : "", where);
            g.drawString(font, font.plainSubstrByWidth(line, PANEL_W - 4), PANEL_X + 2, y, on ? 0xffffffff : GREY, false);
        }
        int count = list.stream().mapToInt(LampSpec::count).sum();
        g.drawString(font, Component.translatable("hdfv.refit_shop.lamp.count", count, LampSpec.MAX), PANEL_X + 2, LAMP_ROWS_Y + LAMP_ROWS * ROW + 2, 0xff808080, false);
    }

    private void workLabels(GuiGraphics g) {
        RefitShopBlockEntity be = shop();
        CompoundTag r = be == null ? new CompoundTag() : be.report();
        List<Component> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        if (r.contains("Vehicle")) { lines.add(Component.translatable("hdfv.refit_shop.vehicle", r.getString("Vehicle"), fmt(r.getDouble("Scale")))); colours.add(LIGHT); }
        for (Component p : RefitShopBlockEntity.problems(r)) { lines.add(p); colours.add(RED); }
        // the totals first (what it costs and what changes), then what the job takes and gives back, as long as it fits
        CompoundTag j = r.getCompound("Job");
        List<Component> needs = new ArrayList<>();
        if (j.getDouble("Hours") > 0) {
            if (!j.getString("Rebuilt").isEmpty()) { lines.add(Component.translatable("hdfv.refit_shop.job.rebuilt", j.getString("Rebuilt"))); colours.add(LIGHT); }
            if (!j.getString("Adjusted").isEmpty()) { lines.add(Component.translatable("hdfv.refit_shop.job.adjusted", j.getString("Adjusted"))); colours.add(LIGHT); }
            double minutes = j.getDouble("Hours") * dev.mcmod.hdfv.manufacture.Pace.TICKS_PER_WORK_HOUR / 20 / 60;
            lines.add(Component.translatable("hdfv.refit_shop.job.time", fmt(j.getDouble("Hours")), fmt(minutes), fmt(j.getDouble("KWh")))); colours.add(LIGHT);
            for (Tag t : j.getList("Needs", Tag.TAG_STRING)) {
                try {
                    Component c = Component.Serializer.fromJson(t.getAsString(), minecraft.level.registryAccess());
                    if (c != null) { needs.add(Component.literal("• ").append(c)); }
                } catch (RuntimeException ignored) { }
            }
            if (j.getInt("Back") > 0 || !j.getString("Refund").isEmpty() || j.getInt("LampsBack") > 0) {
                needs.add(Component.translatable("hdfv.refit_shop.job.back", j.getInt("Back"), j.getString("Refund"), j.getInt("LampsBack")));
            }
        } else if (be != null && !be.pendingJson().isEmpty()) {
            lines.add(Component.translatable("hdfv.refit_shop.nothing")); colours.add(GREY);
        }
        CompoundTag f = r.getCompound("Figures");
        if (f.contains("Mass0")) {
            lines.add(Component.translatable("hdfv.refit_shop.figures", fmt(f.getDouble("Mass0")), fmt(f.getDouble("Mass1")), fmt(f.getDouble("Length0")), fmt(f.getDouble("Length1")),
                    fmt(f.getDouble("Downforce0")), fmt(f.getDouble("Downforce1"))));
            colours.add(0xff9fd7ff);
        }
        if (menu.working()) {
            lines.add(Component.translatable(menu.starved() ? "hdfv.refit_shop.waiting_power" : "hdfv.refit_shop.working", String.format(Locale.ROOT, "%.1f", menu.permille() / 10.0)));
            colours.add(menu.starved() ? RED : AMBER);
        }
        lines.add(Component.translatable("hdfv.refit_shop.energy", menu.energy())); colours.add(GREY);
        if (r.contains("Message")) {
            try {
                Component m = Component.Serializer.fromJson(r.getString("Message"), minecraft.level.registryAccess());
                if (m != null) { lines.add(m); colours.add(GREEN); }
            } catch (RuntimeException ignored) { }
        }
        int y = PANEL_Y + 3, bottom = 200;
        for (int i = 0; i < lines.size(); i++) {
            for (var part : font.split(lines.get(i), PANEL_W - 6)) {
                if (y > bottom) { break; }
                g.drawString(font, part, PANEL_X + 3, y, colours.get(i), false);
                y += 10;
            }
        }
        for (int i = 0; i < needs.size(); i++) {
            var parts = font.split(needs.get(i), PANEL_W - 6);
            if (y + 10 * parts.size() > bottom + 10 || (i < needs.size() - 1 && y + 10 * parts.size() > bottom)) {
                g.drawString(font, Component.translatable("hdfv.refit_shop.more_needs", needs.size() - i), PANEL_X + 3, y, GREY, false);
                break;
            }
            for (var part : parts) { g.drawString(font, part, PANEL_X + 3, y, GREY, false); y += 10; }
        }
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, Math.abs(v) < 10 ? "%.2f" : "%.0f", v); }

    // ---------------------------------------------------------------- development (AutoShot: /hdfvdev refit ...)

    void devPage(Page p) { page = p; paramScroll = 0; rebuildWidgets(); }
    void devSelect(String part) { selected = part; lamp = -1; rebuildWidgets(); }
    void devSet(String pg, String param, String text) { send(edit(Op.SET, pg, param, text, -1, 0, null)); }
    void devScale(double s) { send(edit(Op.SCALE, "", "", "", -1, s, null)); }
    void devAction(Op op) { send(op == Op.WRITE ? edit(Op.WRITE, "", "", "", -1, 0, null) : Edit.of(op)); }
    void devView(float yaw, float pitch) { preview.drag(0, 0); preview.devView(yaw, pitch); }
    void devScroll(int rows) { paramScroll = Math.max(0, rows); rebuildWidgets(); }

    /** A lamp of a role and style added where a click at a fraction of the view lands (as a player would). */
    void devLamp(LampRules.Role role, LampSpec.Style style, boolean pair, double size, double fx, double fy) {
        page = Page.LAMPS;
        template = new LampSpec(role, style, LampSpec.Face.FRONT, 0, 0, 0, 0.5, 0, pair, size, "", "", 0, 0);
        adding = true;
        placeLamp(leftPos + PANEL_X + fx * PANEL_W, topPos + PANEL_Y + fy * VIEW_H);
    }

    void devLampKit() { send(edit(Op.LAMPS_KIT, "", "", "", -1, 0, null)); }
}
