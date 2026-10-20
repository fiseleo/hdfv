package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.petroleum.Conversion;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.fuel.FuelDispenserBlockEntity;
import dev.mcmod.hdfv.neoforge.petroleum.Configurable;
import dev.mcmod.hdfv.neoforge.petroleum.DrillingRigBlockEntity;
import dev.mcmod.hdfv.neoforge.petroleum.HdfvPetroleum;
import dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery;
import dev.mcmod.hdfv.neoforge.petroleum.PetroleumMenu;
import dev.mcmod.hdfv.neoforge.petroleum.PetroleumNetwork;
import dev.mcmod.hdfv.neoforge.petroleum.WellheadBlockEntity;
import dev.mcmod.hdfv.petroleum.Oilwell;
import dev.mcmod.hdfv.petroleum.Refining;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The screen of a petroleum block (0.9): what the block is doing, its settings as rows (a label, arrows either side of
 * the value, or a button for one that turns on and off), and what it reports, all read from the view its block entity
 * last sent. The refinery's column and units have three pages: the process, the yields, the outlets. Drawn with fills
 * (no GUI textures), as the machines' screens.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PetroleumScreen extends AbstractContainerScreen<PetroleumMenu> {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(HdfvPetroleum.MENU.get(), PetroleumScreen::new); }

    private static final int ROW = 13, ROWS_Y = 30;
    private static final int TEXT = 0xff404040, GOOD = 0xff2f7f3f, BAD = 0xff8f2f2f;
    private static final List<String> PAGES = List.of("process", "yields", "outlets");
    /** The FCC's presets: maximum middle distillate, maximum gasoline, maximum olefins. */
    private static final Map<String, Double> FCC_PRESETS = Map.of("diesel", 500.0, "gasoline", 540.0, "olefins", 560.0);

    /**
     * A setting: its label and value, the key it sends with the values the arrows send (or, for a switch, the one its
     * button sends), what it shows to its right; presets replace the arrows with buttons.
     */
    private record Row(Component label, Component value, String key, double less, double more, boolean toggle, boolean active, String extra,
                       List<Map.Entry<String, Double>> presets) {
        static Row step(Component label, Component value, String key, double less, double more) {
            return new Row(label, value, key, less, more, false, true, null, null);
        }
    }

    /** A line of what the block reports, with an optional figure right-aligned. */
    private record Line(Component text, String figure) {}

    private int page;
    private String layout = "";
    private final List<Button> toggles = new ArrayList<>();

    public PetroleumScreen(PetroleumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PetroleumMenu.WIDTH;
        imageHeight = menu.height;
        inventoryLabelX = (PetroleumMenu.WIDTH - 162) / 2 + 1;
        inventoryLabelY = menu.playerTop - 11;
    }

    /** Shows a page (the refinery's screens; /hdfvdev page for screenshots). */
    public void devPage(int p) {
        if (paged() && p >= 0 && p < PAGES.size()) { page = p; rebuildWidgets(); }
    }

    /** Where a row's arrows and value go: the outlets page has short labels and long product names. */
    private int lessX() { return paged() && page == 2 ? 52 : 82; }
    private int valueW() { return paged() && page == 2 ? 112 : 80; }
    private int moreX() { return lessX() + 12 + valueW(); }
    private int extraX() { return moreX() + 18; }

    private boolean paged() { return menu.kind == PetroleumMenu.Kind.COLUMN || menu.kind == PetroleumMenu.Kind.UNIT; }

    private CompoundTag view() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.pos) instanceof Configurable c ? c.shown() : new CompoundTag();
    }

    @Override protected void init() {
        super.init();
        toggles.clear();
        if (paged()) {
            for (int i = 0; i < PAGES.size(); i++) {
                int p = i;
                Button b = Button.builder(Component.translatable("hdfv.gui.page." + PAGES.get(i)), x -> { page = p; rebuildWidgets(); })
                        .bounds(leftPos + imageWidth - 8 - 44 * (PAGES.size() - i), topPos + 3, 42, 12).build();
                b.active = page != i;
                addRenderableWidget(b);
            }
        }
        List<Row> rows = rows(view());
        layout = layoutOf(rows);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = topPos + ROWS_Y + i * ROW - 2, at = i;
            if (r.presets() != null) {
                int w = (moreX() + 12 - lessX()) / r.presets().size();
                for (int k = 0; k < r.presets().size(); k++) {
                    Map.Entry<String, Double> e = r.presets().get(k);
                    addRenderableWidget(Button.builder(Component.translatable("hdfv.gui.preset." + e.getKey()), x -> send(r.key(), e.getValue()))
                            .bounds(leftPos + lessX() + k * w, y, w - 2, 12).build());
                }
            } else if (r.toggle()) {
                Button b = Button.builder(r.value(), x -> click(at, true)).bounds(leftPos + lessX(), y, moreX() + 12 - lessX(), 12).build();
                b.active = r.active();
                toggles.add(b);
                addRenderableWidget(b);
            } else {
                addRenderableWidget(Button.builder(Component.literal("<"), x -> click(at, false)).bounds(leftPos + lessX(), y, 12, 12).build());
                addRenderableWidget(Button.builder(Component.literal(">"), x -> click(at, true)).bounds(leftPos + moreX(), y, 12, 12).build());
            }
        }
    }

    /** What the widgets depend on: rebuilt when it changes (a unit's reactor, the outlets' count). */
    private String layoutOf(List<Row> rows) {
        StringBuilder s = new StringBuilder(page + ":");
        for (Row r : rows) { s.append(r.key()).append(r.toggle() ? "!" : "").append(r.active() ? "" : "-").append(';'); }
        return s.toString();
    }

    private void click(int row, boolean more) {
        List<Row> rows = rows(view());
        if (row >= rows.size()) { return; }
        Row r = rows.get(row);
        send(r.key(), more ? r.more() : r.less());
    }

    private void send(String key, double value) { PetroleumNetwork.send(menu.pos, key, value); }

    @Override protected void containerTick() {
        super.containerTick();
        List<Row> rows = rows(view());
        if (!layoutOf(rows).equals(layout)) { rebuildWidgets(); return; }
        int t = 0;
        for (Row r : rows) { if (r.toggle() && t < toggles.size()) { toggles.get(t++).setMessage(r.value()); } }
    }

    // ---------------------------------------------------------------- the rows and lines of each kind

    private static Component setting(String key) { return Component.translatable("hdfv.gui.setting." + key); }
    private static Component onOff(boolean on) { return Component.translatable(on ? "hdfv.gui.on" : "hdfv.gui.off"); }
    private static String f0(double v) { return String.format(Locale.ROOT, "%.0f", v); }
    private static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private static Row toggle(String key, boolean on, boolean active) {
        return new Row(setting(key), onOff(on), key, on ? 0 : 1, on ? 0 : 1, true, active, null, null);
    }

    private static Row stepped(String key, double now, double step, double min, double max, Component value) {
        return Row.step(setting(key), value, key, clamp(now - step, min, max), clamp(now + step, min, max));
    }

    private static Row indexed(String key, int now, int count, Component value) {
        return Row.step(setting(key), value, key, Math.max(now - 1, 0), Math.min(now + 1, count - 1));
    }

    private static Component degrees(double c) { return Component.translatable("hdfv.gui.celsius", f0(c)); }

    /** A product's name: its fluid's, or its item's. */
    static Component product(String p) {
        if (p.isEmpty()) { return Component.translatable("hdfv.gui.route.none"); }
        if (Refining.item(p)) { return new ItemStack(HdfvRefinery.product(p)).getHoverName(); }
        return new FluidStack(HdfvFluidRegistry.fluid(p), 1).getHoverName();
    }

    private static Component fluidOf(String id) { return id.isEmpty() ? Component.translatable("hdfv.fluid.empty") : product(id); }

    private List<Row> rows(CompoundTag v) {
        List<Row> rows = new ArrayList<>();
        if (v.isEmpty()) { return rows; }
        switch (menu.kind) {
            case COLUMN, UNIT -> {
                if (page == 0) {
                    rows.add(toggle("running", v.getBoolean("Running"), true));
                    double tp = v.getDouble("Throughput");
                    rows.add(stepped("throughput", tp, 0.1, 0.2, 1, Component.literal(f0(tp * 100) + " %")));
                    if (menu.kind == PetroleumMenu.Kind.COLUMN) {
                        rows.add(stepped("naphtha", v.getDouble("Naphtha"), 5, Refining.Cuts.NAPHTHA_MIN, Refining.Cuts.NAPHTHA_MAX, degrees(v.getDouble("Naphtha"))));
                        rows.add(stepped("kerosene", v.getDouble("Kerosene"), 5, Refining.Cuts.KEROSENE_MIN, Refining.Cuts.KEROSENE_MAX, degrees(v.getDouble("Kerosene"))));
                        rows.add(stepped("diesel", v.getDouble("Diesel"), 5, Refining.Cuts.DIESEL_MIN, Refining.Cuts.DIESEL_MAX, degrees(v.getDouble("Diesel"))));
                        boolean has = v.getBoolean("HasVacuum");
                        rows.add(has ? toggle("vacuum", v.getBoolean("VacuumOn"), true)
                                : new Row(setting("vacuum"), Component.translatable("hdfv.gui.no_vacuum"), "vacuum", 0, 0, true, false, null, null));
                    } else {
                        switch (v.getString("Reactor")) {
                            case Structures.FCC -> {
                                rows.add(stepped("riser", v.getDouble("Riser"), 5, Conversion.RISER_MIN_C, Conversion.RISER_MAX_C, degrees(v.getDouble("Riser"))));
                                rows.add(new Row(Component.translatable("hdfv.gui.preset"), Component.empty(), "riser", 0, 0, false, true, null,
                                        FCC_PRESETS.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList()));
                            }
                            case Structures.REFORMER -> rows.add(stepped("octane", v.getDouble("Octane"), 1, Conversion.OCTANE_MIN, Conversion.OCTANE_MAX,
                                    Component.translatable("hdfv.gui.ron", f0(v.getDouble("Octane")))));
                            case Structures.CRACKER -> rows.add(stepped("coil", v.getDouble("Coil"), 5, Conversion.COIL_MIN_C, Conversion.COIL_MAX_C, degrees(v.getDouble("Coil"))));
                            case Structures.POLYMER -> {
                                int i = Math.floorMod(v.getInt("Polymer"), Refining.POLYMERS.size());
                                rows.add(indexed("polymer", i, Refining.POLYMERS.size(), Component.translatable("hdfv.refinery.unit." + Refining.POLYMERS.get(i).name())));
                            }
                            default -> { }
                        }
                    }
                } else if (page == 2) {
                    List<String> names = new ArrayList<>();
                    for (Tag t : v.getList("Routable", Tag.TAG_STRING)) { names.add(t.getAsString()); }
                    var outs = v.getList("Outlets", Tag.TAG_COMPOUND);
                    for (int i = 0; i < outs.size(); i++) {
                        CompoundTag o = outs.getCompound(i);
                        boolean set = o.getBoolean("Set");
                        String route = o.getString("Route");
                        int now = !set ? -2 : route.isEmpty() ? -1 : names.indexOf(route), n = names.size() + 2;
                        Component value = product(route).copy();
                        if (!set) { value = Component.translatable("hdfv.gui.route.auto", value).withStyle(ChatFormatting.DARK_GRAY); }
                        rows.add(new Row(Component.translatable("hdfv.gui.outlet", i + 1), value, "route." + i,
                                Math.floorMod(now + 2 - 1, n) - 2, Math.floorMod(now + 2 + 1, n) - 2, false, true,
                                Component.translatable("hdfv.gui.litres", o.getInt("Litres")).getString(), null));
                    }
                }
            }
            case RIG -> {
                rows.add(toggle("running", v.getBoolean("Running"), !"struck_oil".equals(v.getString("State"))));
                double t = v.getDouble("Target");
                rows.add(stepped("target", t, 50, Math.max(DrillingRigBlockEntity.MIN_TARGET_M, Math.ceil(v.getDouble("Drilled") / 50) * 50), DrillingRigBlockEntity.MAX_TARGET_M,
                        Component.translatable("hdfv.gui.metres", f0(t))));
            }
            case JACK -> {
                rows.add(toggle("running", v.getBoolean("Running"), true));
                rows.add(stepped("spm", v.getDouble("Spm"), 1, Oilwell.Jack.MIN_SPM, Oilwell.Jack.MAX_SPM, Component.translatable("hdfv.gui.spm", f0(v.getDouble("Spm")))));
                int s = v.getInt("Stroke"), p = v.getInt("Plunger");
                rows.add(indexed("stroke", s, Oilwell.Jack.STROKES.length,
                        Component.translatable("hdfv.gui.metres", String.format(Locale.ROOT, "%.2f", Oilwell.Jack.STROKES[Math.floorMod(s, Oilwell.Jack.STROKES.length)]))));
                rows.add(indexed("plunger", p, Oilwell.Jack.PLUNGERS.length,
                        Component.translatable("hdfv.gui.inches", String.format(Locale.ROOT, "%.2f", Oilwell.Jack.PLUNGERS[Math.floorMod(p, Oilwell.Jack.PLUNGERS.length)] / 0.0254))));
            }
            case WELLHEAD -> {
                int c = Math.floorMod(v.getInt("Choke"), WellheadBlockEntity.CHOKES_64THS.length), bore = WellheadBlockEntity.CHOKES_64THS[c];
                rows.add(indexed("choke", c, WellheadBlockEntity.CHOKES_64THS.length,
                        bore == 0 ? Component.translatable("hdfv.gui.choke.open") : Component.translatable("hdfv.gui.choke", bore)));
            }
            case DISPENSER -> {
                int a = Math.floorMod(v.getInt("Amount"), FuelDispenserBlockEntity.AMOUNTS.length), l = FuelDispenserBlockEntity.AMOUNTS[a];
                rows.add(indexed("amount", a, FuelDispenserBlockEntity.AMOUNTS.length,
                        l == 0 ? Component.translatable("hdfv.gui.amount.full") : Component.translatable("hdfv.gui.litres", l)));
                rows.add(toggle("start", v.getBoolean("Fuelling"), true));
            }
        }
        return rows;
    }

    /** What it reports: a left and a right column. */
    private List<List<Line>> lines(CompoundTag v) {
        List<Line> left = new ArrayList<>(), right = new ArrayList<>();
        if (v.isEmpty()) { return List.of(left, right); }
        switch (menu.kind) {
            case COLUMN, UNIT -> {
                Component unit = Component.translatable("hdfv.gui.unit",
                        v.contains("Unit") ? Component.translatable("hdfv.refinery.unit." + v.getString("Unit")) : Component.literal("-"));
                // the process on the first page when its rows leave room (the column's six do not), and over the yields
                if (page == 0 && menu.kind == PetroleumMenu.Kind.UNIT) {
                    left.add(new Line(unit, null));
                } else if (page == 1) {
                    right.add(new Line(unit, null));
                    left.add(new Line(Component.translatable("hdfv.gui.yields").withStyle(ChatFormatting.UNDERLINE), null));
                    CompoundTag y = v.getCompound("Yields");
                    Map<String, Double> sorted = new TreeMap<>();
                    for (String k : y.getAllKeys()) { sorted.put(k, y.getDouble(k)); }
                    sorted.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                            .forEach(e -> left.add(new Line(product(e.getKey()), f1(e.getValue() * 100) + " %")));
                    if (v.contains("Burnt")) { left.add(new Line(Component.translatable("hdfv.gui.burnt"), f1(v.getDouble("Burnt") * 100) + " %")); }
                    for (Tag t : v.getList("Feeds", Tag.TAG_COMPOUND)) {
                        CompoundTag f = (CompoundTag) t;
                        right.add(new Line(Component.translatable("hdfv.gui.feed", fluidOf(f.getString("Fluid"))), f.getInt("Litres") + " L"));
                    }
                    right.add(new Line(Component.translatable("hdfv.gui.capacity"), f0(v.getDouble("CapacityKgPerS") * 3600) + " kg/h"));
                    right.add(new Line(Component.translatable("hdfv.gui.processed"), f0(v.getDouble("FeedKg")) + " kg"));
                    right.add(new Line(Component.translatable("hdfv.gui.burnt_total"), f0(v.getDouble("BurntKg")) + " kg"));
                    right.add(new Line(Component.translatable("hdfv.gui.power"), f1(v.getDouble("PowerW") / 1000) + " kW"));
                }
            }
            case RIG -> {
                left.add(new Line(Component.translatable("hdfv.gui.drilled"), f0(v.getDouble("Drilled")) + " / " + f0(v.getDouble("Target")) + " m"));
                left.add(new Line(Component.translatable("hdfv.gui.casing_steel"), f0(v.getDouble("Steel")) + " kg"));
                right.add(new Line(Component.translatable("hdfv.gui.power"), f1(v.getDouble("PowerW") / 1000) + " kW"));
            }
            case JACK -> {
                left.add(new Line(Component.translatable("hdfv.gui.rate"), f1(v.getDouble("Rate") * 86400) + " m³/d"));
                left.add(new Line(Component.translatable("hdfv.gui.displacement"), f1(v.getDouble("Displacement") * 86400) + " m³/d"));
                if (v.contains("Inflow")) { left.add(new Line(Component.translatable("hdfv.gui.inflow"), f1(v.getDouble("Inflow") * 86400) + " m³/d")); }
                left.add(new Line(Component.translatable("hdfv.gui.power"), f1(v.getDouble("PowerW") / 1000) + " kW"));
                if (v.contains("PressureBar")) {
                    right.add(new Line(Component.translatable("hdfv.gui.pressure"), f0(v.getDouble("PressureBar")) + " bar"));
                    right.add(new Line(Component.translatable("hdfv.gui.depth"), f0(v.getDouble("Depth")) + " m"));
                    right.add(new Line(Component.translatable("hdfv.gui.produced"), f0(v.getDouble("Produced")) + " m³"));
                }
            }
            case WELLHEAD -> {
                left.add(new Line(Component.translatable("hdfv.gui.rate"), f1(v.getDouble("Rate") * 86400) + " m³/d"));
                if (v.contains("Open")) { left.add(new Line(Component.translatable("hdfv.gui.open_flow"), f1(v.getDouble("Open") * 86400) + " m³/d")); }
                left.add(new Line(fluidOf(v.getString("Fluid")), v.getInt("Litres") + " L"));
                if (v.contains("PressureBar")) {
                    right.add(new Line(Component.translatable("hdfv.gui.pressure"), f0(v.getDouble("PressureBar")) + " bar"));
                    right.add(new Line(Component.translatable("hdfv.gui.depth"), f0(v.getDouble("Depth")) + " m"));
                }
            }
            case DISPENSER -> {
                left.add(new Line(fluidOf(v.getString("Fluid")), v.getInt("Litres") + " / " + v.getInt("Capacity") + " L"));
                left.add(new Line(Component.translatable("hdfv.gui.pumped"), v.getInt("Pumped") + " L"));
                if (v.contains("Vehicle")) {
                    right.add(new Line(Component.literal(v.getString("Vehicle")), f0(v.getDouble("VehicleLitres")) + " / " + f0(v.getDouble("VehicleCapacity")) + " L"));
                } else {
                    right.add(new Line(Component.translatable("hdfv.dispenser.no_vehicle", (int) FuelDispenserBlockEntity.REACH_M), null));
                }
            }
        }
        return List.of(left, right);
    }

    // ---------------------------------------------------------------- drawing

    /** A text cut to a width, keeping its style. */
    private net.minecraft.util.FormattedCharSequence fit(Component c, int width) {
        return net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(c, width));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MaterialScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot s : menu.slots) { MaterialScreens.slot(g, leftPos + s.x, topPos + s.y); }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        if (menu.playerTop >= 0) { g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false); }
        CompoundTag v = view();
        String st = v.getString("State");
        String family = switch (menu.kind) { case COLUMN, UNIT -> "refinery"; case RIG -> "rig"; case JACK -> "jack"; case WELLHEAD -> "wellhead"; case DISPENSER -> "dispenser"; };
        if (!st.isEmpty()) {
            boolean good = List.of("running", "drilling", "pumping", "flowing", "pumped", "fuelling", "struck_oil", "done").contains(st);
            g.drawString(font, Component.translatable("hdfv." + family + ".state." + st), 8, 18, good ? GOOD : BAD, false);
        }
        List<Row> rows = rows(v);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = ROWS_Y + i * ROW;
            g.drawString(font, fit(r.label(), lessX() - 12), 8, y, TEXT, false);
            if (!r.toggle() && r.presets() == null) {
                var value = fit(r.value(), valueW() - 4);
                g.drawString(font, value, lessX() + 12 + (valueW() - font.width(value)) / 2, y, TEXT, false);
            }
            if (r.extra() != null) { g.drawString(font, r.extra(), extraX(), y, TEXT, false); }
        }
        List<List<Line>> cols = lines(v);
        int top = ROWS_Y + rows.size() * ROW + (rows.isEmpty() ? 0 : 4), half = (imageWidth - 16) / 2;
        for (int c = 0; c < 2; c++) {
            int x = 8 + c * (half + 6), y = top;
            for (Line l : cols.get(c)) {
                int fw = l.figure() == null ? 0 : font.width(l.figure()) + 4;
                g.drawString(font, fit(l.text(), l.figure() == null ? half : half - fw), x, y, TEXT, false);
                if (l.figure() != null) { g.drawString(font, l.figure(), x + half - 6 - font.width(l.figure()), y, TEXT, false); }
                y += 10;
            }
        }
    }
}
