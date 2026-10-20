package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.airport.Atc;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.airport.AirportClientState;
import dev.mcmod.hdfv.neoforge.airport.AirportNetwork;
import dev.mcmod.hdfv.neoforge.airport.HdfvAirports;
import dev.mcmod.hdfv.neoforge.airport.TowerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A control tower's screen (0.20), from the view the tower sends twice a second (TowerView), drawn with fills as the
 * other machine screens. Two pages:
 * <ul>
 *   <li>Traffic: the radar (a plan position indicator, north up, range rings, the sweep, each return with a short
 *   trail, its name, height and ground speed), the strips (each aircraft's phase, instruction, place in sequence,
 *   warnings in red; click one to pick it), take or release control, and with control the instructions for the picked
 *   aircraft.</li>
 *   <li>Field: state and power, the ATIS, the runways and the one in use, each light circuit's step (off, 1 to 5) and
 *   whether the tower sets it by itself, painting the runways' markings, the tower's log.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TowerScreen extends AbstractContainerScreen<TowerMenu> {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(HdfvAirports.TOWER_MENU.get(), TowerScreen::new); }

    private static final int W = 344, H = 226, RADAR = 150;
    private static final int PANEL = 0xffc6c6c6, DARK = 0xff0b1a12, RING = 0xff1f4a2c, SWEEP = 0xff3fdc6a, BLIP = 0xffb8ffc8, TEXT = 0xff404040,
            GOOD = 0xff2f7f3f, BAD = 0xff8f2f2f, PICK = 0xff3050a0;
    private static final Atc.Instruction[] ORDERS = {Atc.Instruction.TAXI_TO_HOLD, Atc.Instruction.HOLD_POSITION, Atc.Instruction.LINE_UP_AND_WAIT,
            Atc.Instruction.CLEARED_TAKEOFF, Atc.Instruction.CONTINUE_APPROACH, Atc.Instruction.CLEARED_LAND, Atc.Instruction.GO_AROUND, Atc.Instruction.TAXI_TO_APRON,
            Atc.Instruction.CANCELLED};
    private static final String[] CIRCUITS = {"runway", "approach", "papi", "taxiway", "obstruction", "beacon"};

    private int page;
    private UUID picked;
    private String layout = "";
    private long lastTime = Long.MIN_VALUE;
    private final Map<UUID, Deque<double[]>> trails = new HashMap<>();

    public TowerScreen(TowerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
    }

    /** Shows a page (/hdfvdev tower page for screenshots). */
    public void devPage(int p) { if (p >= 0 && p < 2) { page = p; rebuildWidgets(); } }

    /** /hdfvdev tower take: takes (or releases) control as the button does. */
    public boolean devTake() {
        CompoundTag v = view();
        send(inControl(v) ? AirportNetwork.CommandKind.RELEASE : AirportNetwork.CommandKind.TAKE, null, 0);
        return true;
    }

    /** /hdfvdev tower pick: picks a strip by its place in the list. */
    public boolean devPick(int i) {
        var flights = view().getList("Flights", Tag.TAG_COMPOUND);
        if (i >= flights.size()) { return false; }
        picked = flights.getCompound(i).getUUID("Id");
        rebuildWidgets();
        return true;
    }

    /** /hdfvdev tower order: an instruction (its name, e.g. cleared_land) to the picked aircraft. */
    public boolean devOrder(String name) {
        if (picked == null) { return false; }
        try {
            send(AirportNetwork.CommandKind.INSTRUCT, picked, Atc.Instruction.valueOf(name.toUpperCase(java.util.Locale.ROOT)).ordinal());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private CompoundTag view() { return AirportClientState.view(menu.pos); }

    private boolean inControl(CompoundTag v) {
        return minecraft != null && minecraft.player != null && v.hasUUID("Holder") && v.getUUID("Holder").equals(minecraft.player.getUUID());
    }

    private void send(AirportNetwork.CommandKind kind, UUID target, int value) {
        PacketDistributor.sendToServer(new AirportNetwork.CommandPayload(menu.pos, new AirportNetwork.Command(kind, target == null ? new UUID(0, 0) : target, value)));
    }

    @Override protected void init() {
        super.init();
        CompoundTag v = view();
        layout = layoutOf(v);
        for (int i = 0; i < 2; i++) {
            int p = i;
            Button b = Button.builder(Component.translatable("hdfv.tower.page." + (i == 0 ? "traffic" : "field")), x -> { page = p; rebuildWidgets(); })
                    .bounds(leftPos + W - 8 - 52 * (2 - i), topPos + 3, 50, 12).build();
            b.active = page != i;
            addRenderableWidget(b);
        }
        if (page == 0) {
            boolean mine = inControl(v), held = v.hasUUID("Holder");
            Button take = Button.builder(Component.translatable(mine ? "hdfv.tower.release" : "hdfv.tower.take"),
                    x -> send(mine ? AirportNetwork.CommandKind.RELEASE : AirportNetwork.CommandKind.TAKE, null, 0)).bounds(leftPos + 8, topPos + H - 20, RADAR, 14).build();
            take.active = mine || !held;
            addRenderableWidget(take);
            int i = 0;
            for (Tag t : v.getList("Flights", Tag.TAG_COMPOUND)) {
                CompoundTag f = (CompoundTag) t;
                if (i >= 7) { break; }
                UUID id = f.getUUID("Id");
                addRenderableWidget(Button.builder(Component.literal(""), x -> { picked = id; rebuildWidgets(); })
                        .bounds(leftPos + RADAR + 14, topPos + 20 + i * 18, W - RADAR - 22, 17).build()).setAlpha(0f);
                i++;
            }
            if (mine && picked != null) {
                int bw = (W - RADAR - 22 - 4) / 3;
                for (int k = 0; k < ORDERS.length; k++) {
                    Atc.Instruction o = ORDERS[k];
                    addRenderableWidget(Button.builder(Component.translatable("hdfv.tower.order." + o.name().toLowerCase(java.util.Locale.ROOT)),
                            x -> send(AirportNetwork.CommandKind.INSTRUCT, picked, o.ordinal()))
                            .bounds(leftPos + RADAR + 14 + (k % 3) * (bw + 2), topPos + H - 52 + (k / 3) * 14, bw, 13).build());
                }
            }
        } else {
            for (int c = 0; c < CIRCUITS.length; c++) {
                int circuit = c, y = topPos + 104 + c * 14;
                int step = v.getIntArray("Steps").length > c ? v.getIntArray("Steps")[c] : 0;
                boolean auto = (v.getInt("Auto") >> c & 1) != 0;
                addRenderableWidget(Button.builder(Component.literal("<"), x -> send(AirportNetwork.CommandKind.CIRCUIT_STEP, new UUID(0, circuit), step - 1))
                        .bounds(leftPos + 90, y, 12, 12).build());
                addRenderableWidget(Button.builder(Component.literal(">"), x -> send(AirportNetwork.CommandKind.CIRCUIT_STEP, new UUID(0, circuit), step + 1))
                        .bounds(leftPos + 134, y, 12, 12).build());
                addRenderableWidget(Button.builder(Component.translatable(auto ? "hdfv.tower.auto_on" : "hdfv.tower.auto_off"),
                        x -> send(AirportNetwork.CommandKind.CIRCUIT_AUTO, new UUID(0, circuit), auto ? 0 : 1)).bounds(leftPos + 150, y, 46, 12).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("hdfv.tower.paint"), x -> send(AirportNetwork.CommandKind.PAINT, null, 0))
                    .bounds(leftPos + 8, topPos + H - 20, 120, 14).build());
        }
    }

    /** What the widgets depend on: rebuilt when it changes. */
    private String layoutOf(CompoundTag v) {
        StringBuilder s = new StringBuilder().append(page).append(inControl(v)).append(v.hasUUID("Holder")).append(picked);
        for (Tag t : v.getList("Flights", Tag.TAG_COMPOUND)) { s.append(((CompoundTag) t).getUUID("Id")); }
        s.append(java.util.Arrays.toString(v.getIntArray("Steps"))).append(v.getInt("Auto"));
        return s.toString();
    }

    @Override public void containerTick() {
        super.containerTick();
        CompoundTag v = view();
        if (!layoutOf(v).equals(layout)) { rebuildWidgets(); }
        long time = v.getLong("Time");
        if (time != lastTime) {
            lastTime = time;
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            for (Tag t : v.getList("Tracks", Tag.TAG_COMPOUND)) {
                CompoundTag r = (CompoundTag) t;
                UUID id = r.getUUID("Id");
                seen.add(id);
                Deque<double[]> trail = trails.computeIfAbsent(id, k -> new ArrayDeque<>());
                trail.addLast(new double[]{r.getDouble("Dx"), r.getDouble("Dz")});
                while (trail.size() > 8) { trail.removeFirst(); }
            }
            trails.keySet().retainAll(seen);
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + W, topPos + H, PANEL);
        g.fill(leftPos, topPos, leftPos + W, topPos + 1, 0xffffffff);
        g.fill(leftPos, topPos + H - 1, leftPos + W, topPos + H, 0xff555555);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, TEXT, false);
        CompoundTag v = view();
        if (page == 0) { traffic(g, v); } else { field(g, v); }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }

    // ---------------------------------------------------------------- traffic

    private void traffic(GuiGraphics g, CompoundTag v) {
        int x0 = 8, y0 = 20, r = RADAR / 2, cx = x0 + r, cy = y0 + r;
        g.fill(x0, y0, x0 + RADAR, y0 + RADAR, DARK);
        double range = Math.max(500, v.getDouble("ZoneM"));
        for (int ring = 1; ring <= 3; ring++) { circle(g, cx, cy, r * ring / 3.0, RING); }
        g.fill(cx, y0, cx + 1, y0 + RADAR, RING);
        g.fill(x0, cy, x0 + RADAR, cy + 1, RING);
        boolean radar = v.getBoolean("Radar") && v.getBoolean("Powered");
        if (radar) {
            double a = (System.currentTimeMillis() % 4000) / 4000.0 * Math.PI * 2;
            for (int k = 0; k < r; k++) { g.fill(cx + (int) Math.round(Math.sin(a) * k), cy - (int) Math.round(Math.cos(a) * k), cx + (int) Math.round(Math.sin(a) * k) + 1, cy - (int) Math.round(Math.cos(a) * k) + 1, SWEEP); }
        }
        // north is -Z: up on the scope
        double scale = r / range;
        for (Tag t : v.getList("Tracks", Tag.TAG_COMPOUND)) {
            CompoundTag tr = (CompoundTag) t;
            UUID id = tr.getUUID("Id");
            int px = cx + (int) Math.round(tr.getDouble("Dx") * scale), py = cy + (int) Math.round(tr.getDouble("Dz") * scale);
            if (Math.hypot(px - cx, py - cy) > r) { continue; }
            Deque<double[]> trail = trails.get(id);
            if (trail != null) {
                int k = 0;
                for (double[] p : trail) {
                    int tx = cx + (int) Math.round(p[0] * scale), ty = cy + (int) Math.round(p[1] * scale);
                    int alpha = 0x30 + 0x18 * k++;
                    g.fill(tx, ty, tx + 1, ty + 1, (Math.min(alpha, 0xc0) << 24) | (BLIP & 0xffffff));
                }
            }
            g.fill(px - 1, py - 1, px + 2, py + 2, id.equals(picked) ? 0xffffff60 : BLIP);
            String label = tr.getString("Name");
            if (label.length() > 10) { label = label.substring(0, 10); }
            g.drawString(font, label, px + 3, py - 8, BLIP, false);
            g.drawString(font, Component.empty().append(AtcWords.height(tr.getDouble("Msl"))).append(" ").append(AtcWords.speed(tr.getDouble("Gs"))), px + 3, py + 1, 0xff80c090, false);
        }
        g.drawString(font, AtcWords.distance(range), x0 + 2, y0 + RADAR - 9, 0xff60a070, false);
        if (!radar) { g.drawString(font, Component.translatable(v.getBoolean("Powered") ? "hdfv.tower.visual" : "hdfv.tower.unpowered"), x0 + 4, y0 + 4, 0xffd0a040, false); }
        // the strips
        int sx = RADAR + 14, sy = 20, sw = W - RADAR - 22;
        boolean mine = inControl(v);
        Component who = v.hasUUID("Holder") ? Component.translatable("hdfv.tower.held_by", v.getString("HolderName"))
                : Component.translatable(v.getBoolean("Manned") ? "hdfv.tower.manned" : "hdfv.tower.unmanned");
        g.drawString(font, who, sx, sy + 7 * 18 + 2, mine ? GOOD : TEXT, false);
        int i = 0;
        ListOf flights = new ListOf(v.getList("Flights", Tag.TAG_COMPOUND));
        if (flights.size() == 0) { g.drawString(font, Component.translatable("hdfv.tower.no_traffic"), sx + 2, sy + 4, TEXT, false); }
        for (CompoundTag f : flights) {
            if (i >= 7) { break; }
            int y = sy + i * 18;
            UUID id = f.getUUID("Id");
            boolean warn = !f.getList("Warnings", Tag.TAG_STRING).isEmpty();
            g.fill(sx, y, sx + sw, y + 17, id.equals(picked) ? 0xffdde4f4 : 0xffeeeeee);
            g.fill(sx, y, sx + 2, y + 17, warn ? BAD : f.getString("Intent").startsWith("ARRIVE") || f.getString("Intent").equals("LAND_HERE") ? PICK : GOOD);
            String name = f.getString("Name");
            g.drawString(font, name.length() > 14 ? name.substring(0, 14) : name, sx + 4, y + 1, TEXT, false);
            g.drawString(font, AtcWords.phase(f.getString("Phase")), sx + 92, y + 1, 0xff606060, false);
            Component order = warn ? AtcWords.warning(f.getList("Warnings", Tag.TAG_STRING).getString(0))
                    : AtcWords.instruction(f.getString("Instruction"), v.getString("InUse"), f.getInt("Sequence"));
            List<FormattedCharSequence> lines = font.split(order, sw - 6);
            if (!lines.isEmpty()) { g.drawString(font, lines.get(0), sx + 4, y + 9, warn ? BAD : TEXT, false); }
            i++;
        }
        if (mine && picked == null && flights.size() > 0) { g.drawString(font, Component.translatable("hdfv.tower.pick"), sx, H - 50, TEXT, false); }
        if (!v.getList("Conflicts", Tag.TAG_COMPOUND).isEmpty()) {
            CompoundTag c = v.getList("Conflicts", Tag.TAG_COMPOUND).getCompound(0);
            g.drawString(font, Component.translatable("hdfv.tower.conflict", c.getString("Reason")), sx, H - 64, BAD, false);
        }
    }

    /** A list tag's compounds as an iterable. */
    private record ListOf(net.minecraft.nbt.ListTag tag) implements Iterable<CompoundTag> {
        int size() { return tag.size(); }
        @Override public java.util.Iterator<CompoundTag> iterator() {
            List<CompoundTag> out = new ArrayList<>();
            for (Tag t : tag) { out.add((CompoundTag) t); }
            return out.iterator();
        }
    }

    private static void circle(GuiGraphics g, int cx, int cy, double r, int colour) {
        int n = (int) Math.max(24, r * 6);
        for (int k = 0; k < n; k++) {
            double a = 2 * Math.PI * k / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }

    // ---------------------------------------------------------------- field

    private void field(GuiGraphics g, CompoundTag v) {
        int x = 8, y = 20;
        Component state = !v.getBoolean("Formed") ? Component.translatable("hdfv.tower.status.unformed")
                : Component.translatable(v.getBoolean("Powered") ? "hdfv.tower.status.powered" : "hdfv.tower.status.unpowered");
        g.drawString(font, state, x, y, v.getBoolean("Powered") ? GOOD : BAD, false);
        g.drawString(font, Component.translatable("hdfv.tower.power", String.format("%.1f", v.getDouble("PowerW") / 1000), v.getInt("Energy"), v.getInt("EnergyMax")), x + 120, y, TEXT, false);
        y += 11;
        if (v.contains("EyeM")) {
            g.drawString(font, Component.translatable("hdfv.tower.heights", AtcWords.height(v.getDouble("EyeM")), AtcWords.distance(v.getDouble("ZoneM")),
                    Component.translatable(v.getBoolean("Radar") ? "hdfv.tower.radar" : "hdfv.tower.no_radar")), x, y, TEXT, false);
        }
        y += 11;
        List<String> runways = new ArrayList<>();
        for (Tag t : v.getList("Runways", Tag.TAG_STRING)) { runways.add(t.getAsString()); }
        g.drawString(font, Component.translatable("hdfv.tower.runways", runways.isEmpty() ? "-" : String.join(", ", runways), v.contains("InUse") ? v.getString("InUse") : "-"), x, y, TEXT, false);
        y += 13;
        if (v.contains("Atis")) {
            for (FormattedCharSequence l : font.split(AtcWords.atis(v.getCompound("Atis")), W - 16)) { g.drawString(font, l, x, y, TEXT, false); y += 10; }
        }
        // the light circuits
        int[] lit = v.getIntArray("Lit"), steps = v.getIntArray("Steps");
        g.drawString(font, Component.translatable("hdfv.tower.lights", v.getInt("Fixtures")), x, 92, TEXT, false);
        for (int c = 0; c < CIRCUITS.length; c++) {
            int yy = 104 + c * 14 + 2;
            g.drawString(font, Component.translatable("hdfv.tower.circuit." + CIRCUITS[c]), x, yy, TEXT, false);
            int step = c < steps.length ? steps[c] : 0;
            g.drawString(font, step == 0 ? Component.translatable("hdfv.tower.off") : Component.literal(String.valueOf(step)), 110, yy, TEXT, false);
            double l = c < lit.length ? lit[c] / 1000.0 : 0;
            g.fill(204, yy, 204 + 40, yy + 7, 0xff707070);
            g.fill(204, yy, 204 + (int) Math.round(40 * Math.min(1, Math.max(0, Math.log10(Math.max(l, 1e-3)) / 3 + 1))), yy + 7, l > 0 ? 0xfff0e080 : 0xff707070);
        }
        // the log
        net.minecraft.nbt.ListTag log = v.getList("Log", Tag.TAG_STRING);
        int ly = 104;
        g.drawString(font, Component.translatable("hdfv.tower.log"), 252, 92, TEXT, false);
        for (int k = Math.max(0, log.size() - 9); k < log.size(); k++) {
            String s = log.getString(k);
            g.drawString(font, font.plainSubstrByWidth(s, W - 252 - 8), 252, ly, 0xff505050, false);
            ly += 10;
        }
    }
}
