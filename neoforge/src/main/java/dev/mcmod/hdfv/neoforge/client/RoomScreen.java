package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.Mount;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.assembly.Placing;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.assembly.AssemblyNetwork;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.RoomControllerBlockEntity;
import dev.mcmod.hdfv.neoforge.assembly.RoomControllerBlockEntity.Edit;
import dev.mcmod.hdfv.neoforge.assembly.RoomControllerBlockEntity.Op;
import dev.mcmod.hdfv.neoforge.assembly.RoomMenu;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An assembly room controller's screen (0.7). On the left the drawings (part blueprints and a vehicle blueprint) and the
 * vehicle's parts; on the right two pages. The room: scan it, open or close its door, what the scan found and what is
 * wrong with the room for the vehicle, the vehicle's size and the work it takes. The layout: the vehicle in 3D (drag to
 * turn, scroll to zoom), adding the drawing chosen, removing, attaching to another part's mount point, moving (0.1 m, 1 m
 * with shift) and turning (15 degrees, 90 with shift) the part chosen in the list, saving to and loading from the
 * vehicle blueprint. The server holds the layout; every button sends one edit. Also registers the renderer of the
 * vehicles built in rooms (AeroCore's).
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RoomScreen extends AbstractContainerScreen<RoomMenu> {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(HdfvAssembly.ROOM_MENU.get(), RoomScreen::new); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HdfvAssembly.VEHICLE.get(), HdfvVehicleRenderer::new);
        event.registerBlockEntityRenderer(HdfvAssembly.ROOM_CONTROLLER.get(), RoomControllerRenderer::new);
    }

    private static final int LIST_X = 48, LIST_Y = 18, LIST_W = 122, LIST_H = 132, ROW = 10;
    private static final int PANEL_X = 176, PANEL_Y = 18, PANEL_W = 216, VIEW_H = 122;
    private static boolean layoutPage;
    private final VehiclePreview preview = new VehiclePreview();
    private String selected = "";
    private int drawing, scroll;
    private boolean dragging;
    private String parsedFor;
    private VehicleBlueprint parsed = new VehicleBlueprint("vehicle", List.of(), List.of());
    private Map<String, Map<String, Mount>> points = Map.of();

    public RoomScreen(RoomMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = RoomMenu.WIDTH;
        imageHeight = RoomMenu.HEIGHT;
        inventoryLabelY = RoomMenu.PLAYER_Y - 11;
    }

    private RoomControllerBlockEntity controller() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof RoomControllerBlockEntity be ? be : null;
    }

    private VehicleBlueprint layout() {
        RoomControllerBlockEntity be = controller();
        String json = be == null ? "" : be.layoutJson();
        if (!json.equals(parsedFor)) {
            parsedFor = json;
            parsed = be == null ? parsed : be.layout();
            points = Placing.points(parsed);
            if (parsed.part(selected) == null) { selected = parsed.parts().isEmpty() ? "" : parsed.parts().get(parsed.parts().size() - 1).id(); }
        }
        return parsed;
    }

    @Override protected void init() {
        super.init();
        int x = leftPos + PANEL_X;
        addRenderableWidget(Button.builder(Component.translatable("hdfv.room.page.room"), b -> page(false)).bounds(leftPos + 296, topPos + 3, 46, 12).build());
        addRenderableWidget(Button.builder(Component.translatable("hdfv.room.page.layout"), b -> page(true)).bounds(leftPos + 346, topPos + 3, 46, 12).build());
        if (!layoutPage) {
            String[] keys = {"hdfv.room.scan", "hdfv.room.door", "hdfv.room.start", "hdfv.room.launch", "hdfv.room.dismantle"};
            int[] ids = {RoomMenu.SCAN, RoomMenu.DOOR, RoomMenu.START, RoomMenu.LAUNCH, RoomMenu.DISMANTLE};
            for (int i = 0; i < keys.length; i++) {
                int id = ids[i];
                addRenderableWidget(Button.builder(Component.translatable(keys[i]), b -> click(id)).bounds(x + 44 * i, topPos + 212, 42, 18).build());
            }
            return;
        }
        int y = topPos + PANEL_Y + VIEW_H + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> drawing = Math.floorMod(drawing - 1, RoomControllerBlockEntity.PART_DRAWINGS)).bounds(x, y, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> drawing = Math.floorMod(drawing + 1, RoomControllerBlockEntity.PART_DRAWINGS)).bounds(x + 130, y, 14, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("hdfv.layout.add"), b -> send(new Edit(Op.ADD, drawing, "", "", "", Vec3d.ZERO, 0, 0, 0)))
                .bounds(x + 148, y, 68, 16).build());
        y += 18;
        addRenderableWidget(Button.builder(Component.translatable("hdfv.layout.remove"), b -> send(new Edit(Op.REMOVE, 0, selected, "", "", Vec3d.ZERO, 0, 0, 0)))
                .bounds(x, y, 50, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("hdfv.layout.mount"), b -> nextMount()).bounds(x + 52, y, 112, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("hdfv.layout.free"), b -> send(new Edit(Op.ATTACH, 0, selected, "", "", Vec3d.ZERO, 0, 0, 0)))
                .bounds(x + 166, y, 50, 16).build());
        y += 18;
        String[] moves = {"X-", "X+", "Y-", "Y+", "Z-", "Z+"};
        for (int i = 0; i < moves.length; i++) {
            int k = i;
            addRenderableWidget(Button.builder(Component.literal(moves[i]), b -> nudge(k)).bounds(x + 22 * i, y, 20, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("hdfv.layout.move." + "xyz".charAt(i / 2)))).build());
        }
        String[] turns = {"yaw_left", "yaw_right", "roll"};
        for (int i = 0; i < turns.length; i++) {
            int k = 6 + i;
            addRenderableWidget(Button.builder(Component.translatable("hdfv.layout." + turns[i]), b -> nudge(k)).bounds(x + 132 + 28 * i, y, 26, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("hdfv.layout." + turns[i] + ".tip"))).build());
        }
        y += 18;
        addRenderableWidget(Button.builder(Component.translatable("hdfv.layout.save"), b -> send(Edit.of(Op.SAVE))).bounds(x, y, 70, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("hdfv.layout.load"), b -> send(Edit.of(Op.LOAD))).bounds(x + 73, y, 70, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("hdfv.layout.clear"), b -> send(Edit.of(Op.CLEAR))).bounds(x + 146, y, 70, 16).build());
    }

    /** Development: shows a page of the open room screen (for screenshots). */
    public static void devPage(boolean layout) {
        layoutPage = layout;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof RoomScreen s) { s.rebuildWidgets(); }
    }

    private void page(boolean layout) {
        layoutPage = layout;
        rebuildWidgets();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    }

    private void send(Edit e) { AssemblyNetwork.send(menu.pos, e); }

    /** Moves (0..5) or turns (6, 7: yaw; 8: roll) the chosen part. */
    private void nudge(int k) {
        if (selected.isEmpty()) { return; }
        boolean big = hasShiftDown();
        double step = big ? 1 : 0.1, turn = big ? 90 : 15;
        Vec3d by = switch (k) {
            case 0 -> new Vec3d(-step, 0, 0); case 1 -> new Vec3d(step, 0, 0);
            case 2 -> new Vec3d(0, -step, 0); case 3 -> new Vec3d(0, step, 0);
            case 4 -> new Vec3d(0, 0, -step); case 5 -> new Vec3d(0, 0, step);
            default -> Vec3d.ZERO;
        };
        double yaw = k == 6 ? turn : k == 7 ? -turn : 0, roll = k == 8 ? (big ? 90 : 15) : 0;
        send(new Edit(Op.NUDGE, 0, selected, "", "", by, yaw, 0, roll));
    }

    /** The mount points the chosen part may go on: every other part's (not one that hangs on it). */
    private List<String[]> mounts() {
        List<String[]> out = new ArrayList<>();
        VehicleBlueprint l = layout();
        for (var host : points.entrySet()) {
            if (host.getKey().equals(selected) || hangsOn(l, host.getKey(), selected)) { continue; }
            for (String point : host.getValue().keySet()) { out.add(new String[]{host.getKey(), point}); }
        }
        return out;
    }

    private static boolean hangsOn(VehicleBlueprint l, String a, String b) {
        for (String at = a; at != null; ) {
            if (at.equals(b)) { return true; }
            VehicleBlueprint.Entry e = l.part(at);
            at = e == null ? null : e.mount().attachPart();
        }
        return false;
    }

    private void nextMount() {
        VehicleBlueprint.Entry e = layout().part(selected);
        List<String[]> m = mounts();
        if (e == null || m.isEmpty()) { return; }
        int at = -1;
        for (int i = 0; i < m.size(); i++) { if (m.get(i)[0].equals(e.mount().attachPart()) && m.get(i)[1].equals(e.mount().attachPoint())) { at = i; } }
        String[] next = m.get((at + 1) % m.size());
        send(new Edit(Op.ATTACH, 0, selected, next[0], next[1], Vec3d.ZERO, 0, 0, 0));
    }

    // ---------------------------------------------------------------- input

    @Override public boolean mouseClicked(double mx, double my, int button) {
        int lx = (int) mx - leftPos - LIST_X, ly = (int) my - topPos - LIST_Y;
        if (lx >= 0 && lx < LIST_W && ly >= 0 && ly < LIST_H) {
            int row = ly / ROW + scroll;
            List<VehicleBlueprint.Entry> parts = layout().parts();
            if (row < parts.size()) { selected = parts.get(row).id(); }
            return true;
        }
        if (layoutPage && inView(mx, my)) { dragging = true; return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { preview.drag(dx, dy); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (layoutPage && inView(mx, my)) { preview.zoom(sy); return true; }
        int lx = (int) mx - leftPos - LIST_X, ly = (int) my - topPos - LIST_Y;
        if (lx >= 0 && lx < LIST_W && ly >= 0 && ly < LIST_H) {
            scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, layout().parts().size() - LIST_H / ROW));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private boolean inView(double mx, double my) {
        return mx >= leftPos + PANEL_X && mx < leftPos + PANEL_X + PANEL_W && my >= topPos + PANEL_Y && my < topPos + PANEL_Y + VIEW_H;
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MaterialScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot s : menu.slots) { MaterialScreens.slot(g, leftPos + s.x, topPos + s.y); }
        g.fill(leftPos + LIST_X - 1, topPos + LIST_Y - 1, leftPos + LIST_X + LIST_W + 1, topPos + LIST_Y + LIST_H + 1, 0xff8b8b8b);
        g.fill(leftPos + LIST_X, topPos + LIST_Y, leftPos + LIST_X + LIST_W, topPos + LIST_Y + LIST_H, 0xff2b2b2b);
        if (!layoutPage) {
            g.fill(leftPos + PANEL_X - 1, topPos + PANEL_Y - 1, leftPos + PANEL_X + PANEL_W + 1, topPos + 207, 0xff8b8b8b);
            g.fill(leftPos + PANEL_X, topPos + PANEL_Y, leftPos + PANEL_X + PANEL_W, topPos + 206, 0xff2b2b2b);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (layoutPage) { preview.render(g, font, leftPos + PANEL_X, topPos + PANEL_Y, PANEL_W, VIEW_H, controller() == null ? "" : controller().layoutJson(), selected); }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xff404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff404040, false);
        g.drawString(font, Component.translatable("hdfv.room.drawings"), 8, 20, 0xff404040, false);
        g.drawString(font, Component.translatable("hdfv.room.vehicle_drawing"), 8, RoomMenu.VEHICLE_Y - 10, 0xff404040, false);
        // the parts
        List<VehicleBlueprint.Entry> parts = layout().parts();
        for (int i = scroll, y = LIST_Y + 1; i < parts.size() && y + ROW <= LIST_Y + LIST_H; i++, y += ROW) {
            VehicleBlueprint.Entry e = parts.get(i);
            boolean on = e.id().equals(selected);
            if (on) { g.fill(LIST_X, y - 1, LIST_X + LIST_W, y + ROW - 1, 0xff3f5f7f); }
            g.drawString(font, font.plainSubstrByWidth(e.id(), LIST_W - 4), LIST_X + 2, y, on ? 0xffffffff : 0xffc0c0c0, false);
        }
        if (parts.isEmpty()) { g.drawString(font, Component.translatable("hdfv.layout.none"), LIST_X + 2, LIST_Y + 2, 0xff808080, false); }
        if (layoutPage) { layoutLabels(g); } else { roomLabels(g); }
    }

    private void layoutLabels(GuiGraphics g) {
        int y = PANEL_Y + VIEW_H + 8;
        var d = Workpieces.drawing(menu.slots.get(drawing).getItem());
        String name = (drawing + 1) + ": " + (d == null ? "-" : d.name());
        g.drawString(font, font.plainSubstrByWidth(name, 112), PANEL_X + 17, y, 0xff404040, false);
        VehicleBlueprint.Entry e = layout().part(selected);
        if (e != null) {
            Mount m = e.mount();
            String on = m.attached() ? m.attachPart() + "." + m.attachPoint() : "-";
            String place = String.format(Locale.ROOT, "%s  %.1f %.1f %.1f  %.0f/%.0f/%.0f", on, m.position().x(), m.position().y(), m.position().z(), m.yawDeg(), m.pitchDeg(), m.rollDeg());
            g.drawString(font, font.plainSubstrByWidth(place, PANEL_W), PANEL_X, PANEL_Y + VIEW_H + 4 + 18 * 4 + 2, 0xff404040, false);
        }
        RoomControllerBlockEntity be = controller();
        if (be != null && !be.issues().isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(be.issues().get(0), PANEL_W), PANEL_X, PANEL_Y + VIEW_H + 4 + 18 * 4 + 12, 0xffbf3f2f, false);
        }
    }

    private void roomLabels(GuiGraphics g) {
        RoomControllerBlockEntity be = controller();
        CompoundTag r = be == null ? new CompoundTag() : be.report();
        List<Component> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        if (menu.buildState() == 1) {
            lines.add(Component.translatable("hdfv.build.progress", String.format(Locale.ROOT, "%.1f", menu.permille() / 10.0), menu.crew()));
            colours.add(menu.crew() > 0 ? 0xffffd040 : 0xffff7f6f);
        } else if (menu.buildState() == 2) {
            lines.add(Component.translatable("hdfv.build.done")); colours.add(0xff5fdf7f);
        }
        if (r.isEmpty()) {
            lines.add(Component.translatable("hdfv.room.unscanned")); colours.add(0xffc0c0c0);
        } else {
            if (r.getBoolean("Closed")) {
                int[] s = r.getIntArray("Size");
                lines.add(Component.translatable("hdfv.room.size", s[0], s[2], s[1], s[3])); colours.add(0xffe0e0e0);
                int[] d = r.getIntArray("Door");
                lines.add(d.length == 2 ? Component.translatable("hdfv.room.doors", r.getInt("Doors"), d[0], d[1]) : Component.translatable("hdfv.room.no_doors"));
                colours.add(0xffe0e0e0);
                lines.add(Component.translatable("hdfv.room.crane", String.format(Locale.ROOT, "%.0f", r.getInt("CraneKg") / 1000.0))); colours.add(0xffe0e0e0);
                lines.add(Component.translatable("hdfv.room.stations", r.getInt("Stations"))); colours.add(0xffe0e0e0);
                if (r.getInt("Slipway") > 0) { lines.add(Component.translatable("hdfv.room.slipway", r.getInt("Slipway"))); colours.add(0xffe0e0e0); }
            }
            if (r.contains("Vehicle")) {
                CompoundTag v = r.getCompound("Vehicle");
                int[] s = v.getIntArray("Size");
                lines.add(Component.translatable("hdfv.room.vehicle", Component.translatable("hdfv.room." + v.getString("Room")), s[0] / 10.0, s[1] / 10.0, s[2] / 10.0,
                        v.getInt("Hours"), v.getInt("Heaviest"), v.getInt("Parts"), v.getInt("Pieces")));
                colours.add(0xffa0c8ff);
            }
            var problems = r.getList("Problems", Tag.TAG_COMPOUND);
            if (problems.isEmpty()) { lines.add(Component.translatable("hdfv.room.ready")); colours.add(0xff5fdf7f); }
            for (Tag t : problems) { lines.add(RoomControllerBlockEntity.problem((CompoundTag) t)); colours.add(0xffff7f6f); }
        }
        int y = PANEL_Y + 3;
        for (int i = 0; i < lines.size() && y < 200; i++) {
            for (FormattedCharSequence part : font.split(lines.get(i), PANEL_W - 6)) {
                if (y >= 200) { break; }
                g.drawString(font, part, PANEL_X + 3, y, colours.get(i), false);
                y += 10;
            }
        }
    }
}
