package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.damage.VehicleConditions;
import dev.mcmod.hdfv.neoforge.repair.HdfvRepair;
import dev.mcmod.hdfv.neoforge.repair.RepairNetwork;
import dev.mcmod.hdfv.neoforge.repair.RepairShopBlockEntity;
import dev.mcmod.hdfv.neoforge.repair.RepairShopBlockEntity.Edit;
import dev.mcmod.hdfv.neoforge.repair.RepairShopBlockEntity.Op;
import dev.mcmod.hdfv.neoforge.repair.RepairShopMenu;
import dev.mcmod.hdfv.repair.RepairJob;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The repair shop's screen (0.25), drawn with fills as the material screens: on the left the vehicle's parts and a row
 * for each condition (a box to pick it, its bar and what is left of it as the status screen shows them, and what the
 * shop would do: mend, new pieces, a new part, a rebuild; from Conditions.REPLACE_FROM it is never "mend", and the row's
 * tooltip says why); under it "all damaged", "none", scan and the door. On the right: the shop's problems, the job's
 * hours and energy, each thing it needs with what the stores at the room have of it (green when there is enough), what
 * comes back, the progress, start and cancel.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RepairShopScreen extends AbstractContainerScreen<RepairShopMenu> {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(HdfvRepair.MENU.get(), RepairShopScreen::new); }

    private static final int LIST_X = 8, LIST_Y = 18, LIST_W = 200, LIST_H = 168, PART_ROW = 12, ITEM_ROW = 10, BAR = 34;
    private static final int PANEL_X = 214, PANEL_Y = 18, PANEL_W = 138, PANEL_H = 168;
    private static final int TEXT = 0xff404040, LIGHT = 0xffe0e0e0, RED = 0xffff7f6f, GREEN = 0xff5fdf7f, AMBER = 0xffffd040, GREY = 0xffc0c0c0;

    /** A row of the list: a part's name, or one of its conditions. */
    private record Row(String part, String condition, Component text, double used, RepairJob.Kind kind, String reason, String design) {
        boolean header() { return condition == null; }
    }

    private final List<Row> rows = new ArrayList<>();
    private int scroll;

    public RepairShopScreen(RepairShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = RepairShopMenu.WIDTH;
        imageHeight = RepairShopMenu.HEIGHT;
    }

    private RepairShopBlockEntity shop() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof RepairShopBlockEntity be ? be : null;
    }

    private PartVehicle vehicle() {
        RepairShopBlockEntity be = shop();
        return be == null || be.targetId() < 0 || minecraft.level == null ? null : minecraft.level.getEntity(be.targetId()) instanceof PartVehicle v ? v : null;
    }

    private void send(Edit e) { RepairNetwork.send(menu.pos, e); }

    @Override protected void init() {
        super.init();
        int y = topPos + LIST_Y + LIST_H + 6;
        Object[][] left = {{"hdfv.repair_shop.all", Op.ALL}, {"hdfv.repair_shop.none", Op.NONE}, {"hdfv.repair_shop.scan", Op.SCAN}, {"hdfv.repair_shop.door", Op.DOOR}};
        // the first wider: "All damaged" did not fit 49 (the four still span the list's 202)
        int[] widths = {62, 44, 44, 46};
        int x = leftPos + LIST_X;
        for (int i = 0; i < left.length; i++) {
            Op op = (Op) left[i][1];
            addRenderableWidget(Button.builder(Component.translatable((String) left[i][0]), b -> send(Edit.of(op))).bounds(x, y, widths[i], 18).build());
            x += widths[i] + 2;
        }
        addRenderableWidget(Button.builder(Component.translatable("hdfv.repair_shop.start"), b -> send(Edit.of(Op.START))).bounds(leftPos + PANEL_X, y, 68, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("hdfv.repair_shop.cancel"), b -> send(Edit.of(Op.CANCEL))).bounds(leftPos + PANEL_X + 70, y, 68, 18).build());
    }

    @Override protected void containerTick() {
        super.containerTick();
        rows.clear();
        PartVehicle v = vehicle();
        RepairShopBlockEntity be = shop();
        if (v == null || be == null) { return; }
        Map<String, CompoundTag> tasks = new HashMap<>();
        for (Tag t : be.report().getCompound("Job").getList("Tasks", Tag.TAG_COMPOUND)) { tasks.put(((CompoundTag) t).getString("Key"), (CompoundTag) t); }
        for (VehicleConditions.Entry e : VehicleConditions.of(v)) {
            rows.add(new Row(e.id(), null, e.name(), e.worst(), null, "", e.part().part().design().name()));
            for (VehicleConditions.Item i : e.items()) {
                String key = new RepairJob.Pick(e.id(), i.condition()).key();
                CompoundTag t = tasks.get(key);
                RepairJob.Kind kind = t != null ? RepairJob.Kind.valueOf(t.getString("Kind").toUpperCase(Locale.ROOT)) : RepairJob.kindOf(e.part().part().design(), i.condition(), i.used());
                String reason = t != null ? t.getString("Reason") : i.used() >= Conditions.REPLACE_FROM ? RepairJob.WORN_OUT : Conditions.TYRES.equals(i.condition()) && i.used() > 0 ? RepairJob.NEVER_MENDED : "";
                rows.add(new Row(e.id(), i.condition(), i.name(), i.used(), kind, reason, e.part().part().design().name()));
            }
        }
    }

    private int contentHeight() {
        int h = 2;
        for (Row r : rows) { h += r.header() ? PART_ROW : ITEM_ROW; }
        return h;
    }

    /** The row under a point of the list, or null. */
    private Row rowAt(double mx, double my) {
        int lx = (int) mx - leftPos - LIST_X, ly = (int) my - topPos - LIST_Y;
        if (lx < 0 || lx >= LIST_W || ly < 0 || ly >= LIST_H) { return null; }
        int y = 2 - scroll;
        for (Row r : rows) {
            int h = r.header() ? PART_ROW : ITEM_ROW;
            if (ly >= y && ly < y + h) { return r; }
            y += h;
        }
        return null;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        Row r = rowAt(mx, my);
        if (r != null) {
            if (!r.header() && r.kind() != null && !menu.working()) { send(new Edit(Op.TOGGLE, new RepairJob.Pick(r.part(), r.condition()).key())); }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (rowAt(mx, my) != null || mx >= leftPos + LIST_X && mx < leftPos + LIST_X + LIST_W && my >= topPos + LIST_Y && my < topPos + LIST_Y + LIST_H) {
            scroll = Math.clamp(scroll - (int) Math.round(sy * 2 * ITEM_ROW), 0, Math.max(0, contentHeight() - LIST_H));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MaterialScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (int[] b : new int[][]{{LIST_X, LIST_Y, LIST_W, LIST_H}, {PANEL_X, PANEL_Y, PANEL_W, PANEL_H}}) {
            g.fill(leftPos + b[0] - 1, topPos + b[1] - 1, leftPos + b[0] + b[2] + 1, topPos + b[1] + b[3] + 1, 0xff8b8b8b);
            g.fill(leftPos + b[0], topPos + b[1], leftPos + b[0] + b[2], topPos + b[1] + b[3], 0xff2b2b2b);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        list(g);
        refusal(g);
        Row r = rowAt(mouseX, mouseY);
        if (r != null && !r.header() && r.kind() != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("hdfv.repair_shop.kind." + r.kind().name().toLowerCase(Locale.ROOT) + ".tip", r.design()));
            if (!r.reason().isEmpty()) { tip.add(Component.translatable("hdfv.repair_shop.reason." + r.reason(), Math.round(100 * Conditions.REPLACE_FROM))); }
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    /** The parts and their conditions, clipped to the list. */
    private void list(GuiGraphics g) {
        int x = leftPos + LIST_X, top = topPos + LIST_Y;
        RepairShopBlockEntity be = shop();
        g.enableScissor(x, top, x + LIST_W, top + LIST_H);
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable(be != null && be.report().contains("Vehicle") ? "hdfv.status.nothing" : "hdfv.repair_shop.problem.no_vehicle"), x + 3, top + 3, GREY, false);
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() - LIST_H));
        int y = top + 2 - scroll;
        for (Row r : rows) {
            if (r.header()) {
                g.fill(x, y, x + LIST_W, y + PART_ROW - 1, 0xff3a3f46);
                g.drawString(font, font.plainSubstrByWidth(r.text().getString(), LIST_W - 6), x + 3, y + 2, LIGHT, false);
                y += PART_ROW;
                continue;
            }
            boolean picked = be != null && be.picks().contains(new RepairJob.Pick(r.part(), r.condition()).key());
            // the box
            int bx = x + 3;
            g.fill(bx, y + 1, bx + 8, y + 9, r.kind() == null ? 0xff4a4a4a : 0xff9a9a9a);
            g.fill(bx + 1, y + 2, bx + 7, y + 8, 0xff1e1e1e);
            if (picked) { g.fill(bx + 2, y + 3, bx + 6, y + 7, GREEN); }
            g.drawString(font, font.plainSubstrByWidth(r.text().getString(), 58), x + 14, y + 1, r.kind() == null ? 0xff8a8a8a : GREY, false);
            double left = 1 - Math.clamp(r.used(), 0.0, 1.0);
            int colour = r.used() >= Conditions.REPLACE_FROM ? RED : r.used() >= 0.5 ? AMBER : GREEN, cx = x + 74;
            g.fill(cx, y + 2, cx + BAR, y + 8, 0xff555555);
            g.fill(cx, y + 2, cx + (int) Math.round(BAR * left), y + 8, colour);
            g.drawString(font, Math.round(100 * left) + "%", cx + BAR + 3, y + 1, colour, false);
            if (r.kind() != null) {
                Component what = Component.translatable("hdfv.repair_shop.kind." + r.kind().name().toLowerCase(Locale.ROOT));
                g.drawString(font, font.plainSubstrByWidth(what.getString(), LIST_W - 140), x + 138, y + 1, r.kind() == RepairJob.Kind.MEND ? LIGHT : AMBER, false);
            }
            y += ITEM_ROW;
        }
        g.disableScissor();
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        RepairShopBlockEntity be = shop();
        CompoundTag r = be == null ? new CompoundTag() : be.report();
        List<Component> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        if (r.contains("Vehicle")) { lines.add(Component.translatable("hdfv.repair_shop.vehicle", r.getString("Vehicle"))); colours.add(LIGHT); }
        for (Component p : RepairShopBlockEntity.problems(r)) { lines.add(p); colours.add(RED); }
        CompoundTag j = r.getCompound("Job");
        if (menu.working()) {
            lines.add(Component.translatable(menu.starved() ? "hdfv.repair_shop.waiting_power" : "hdfv.repair_shop.working", String.format(Locale.ROOT, "%.1f", menu.permille() / 10.0)));
            colours.add(menu.starved() ? RED : AMBER);
        } else if (j.getDouble("Hours") > 0) {
            double minutes = j.getDouble("Hours") * dev.mcmod.hdfv.neoforge.HdfvConfig.ticksPerWorkHour() / 20 / 60;
            lines.add(Component.translatable("hdfv.repair_shop.job.time", fmt(j.getDouble("Hours")), fmt(minutes), fmt(j.getDouble("KWh")))); colours.add(LIGHT);
            for (Tag t : j.getList("Needs", Tag.TAG_COMPOUND)) {
                CompoundTag n = (CompoundTag) t;
                Component name;
                try { name = Component.Serializer.fromJson(n.getString("Name"), minecraft.level.registryAccess()); } catch (RuntimeException e) { name = null; }
                if (name == null) { continue; }
                boolean enough = n.getDouble("Have") >= n.getDouble("Amount") - 1e-6;
                lines.add(Component.translatable("hdfv.repair_shop.job.need", name, fmt(n.getDouble("Amount")), fmt(n.getDouble("Have"))));
                colours.add(enough ? GREEN : RED);
            }
            if (!j.getString("Salvage").isEmpty()) { lines.add(Component.translatable("hdfv.repair_shop.job.salvage", j.getString("Salvage"))); colours.add(GREY); }
        } else if (r.contains("Vehicle")) {
            lines.add(Component.translatable("hdfv.repair_shop.job.none")); colours.add(GREY);
        }
        lines.add(Component.translatable("hdfv.repair_shop.energy", menu.energy())); colours.add(GREY);
        if (r.contains("Message")) {
            try {
                Component m = Component.Serializer.fromJson(r.getString("Message"), minecraft.level.registryAccess());
                if (m != null) { lines.add(m); colours.add(AMBER); }
            } catch (RuntimeException ignored) { }
        }
        int y = PANEL_Y + 3, bottom = PANEL_Y + PANEL_H;
        for (int i = 0; i < lines.size(); i++) {
            var parts = font.split(lines.get(i), PANEL_W - 6);
            // a line goes in only with room under it for "… and n more" (unless it is the last), so that line fits too
            if (y + 10 * parts.size() + (i == lines.size() - 1 ? 0 : 10) > bottom) {
                g.drawString(font, Component.translatable("hdfv.repair_shop.more", lines.size() - i), PANEL_X + 3, y, GREY, false);
                break;
            }
            for (var part : parts) { g.drawString(font, part, PANEL_X + 3, y, colours.get(i), false); y += 10; }
        }
    }

    /** The last refused action, for five seconds after it: over the bottom of the panel. */
    private void refusal(GuiGraphics g) {
        RepairShopBlockEntity be = shop();
        if (be == null || minecraft == null || minecraft.level == null || !be.report().contains("Refused")) { return; }
        if (minecraft.level.getGameTime() - be.report().getLong("RefusedAt") > 100) { return; }
        Component why;
        try { why = Component.Serializer.fromJson(be.report().getString("Refused"), minecraft.level.registryAccess()); } catch (RuntimeException e) { return; }
        if (why == null) { return; }
        var lines = font.split(why, PANEL_W - 6);
        int n = Math.min(lines.size(), 4), x = leftPos + PANEL_X, y = topPos + PANEL_Y + PANEL_H - 10 * n - 2;
        g.fill(x, y, x + PANEL_W, y + 10 * n + 2, 0xd0300808);
        for (int i = 0; i < n; i++) { g.drawString(font, lines.get(i), x + 3, y + 2 + 10 * i, RED, false); }
    }

    /** A number as the panel shows it: whole ones (a count of tyres) without decimals, others to two below 10. */
    private static String fmt(double v) { return String.format(Locale.ROOT, Math.abs(v) < 10 && Math.abs(v - Math.rint(v)) > 1e-6 ? "%.2f" : "%.0f", v); }

    // ---------------------------------------------------------------- development (AutoShot)

    /** Development: the list's rows as text (a part, then "  [x] condition left% kind"). */
    public List<String> devLines() {
        List<String> out = new ArrayList<>();
        RepairShopBlockEntity be = shop();
        for (Row r : rows) {
            if (r.header()) { out.add(r.text().getString()); continue; }
            boolean picked = be != null && be.picks().contains(new RepairJob.Pick(r.part(), r.condition()).key());
            out.add("  [" + (picked ? "x" : " ") + "] " + r.condition() + " " + Math.round(100 * (1 - r.used())) + "% " + (r.kind() == null ? "-" : r.kind()));
        }
        return out;
    }
}
