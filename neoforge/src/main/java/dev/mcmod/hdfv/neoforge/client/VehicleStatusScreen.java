package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.neoforge.damage.VehicleConditions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 0.25: a vehicle's status (sneak and use an empty hand on it): its name, mass and fuel, and under a strip of tabs what
 * each tab shows. One tab so far, the condition of its parts: a bar for each thing that wears or is damaged, what is
 * left of it (green, amber from half used, red from Conditions.REPLACE_FROM, where it is marked as to be replaced).
 * Drawn with fills like the material screens; it reads what the client has of the vehicle (AeroCore sends conditions in
 * whole per cent) and looks again twice a second.
 *
 * <p>Later tabs (repairs, cargo: RepairTab, CargoTab) are added with addTab: a factory from the screen's vehicle to a Tab.
 */
public final class VehicleStatusScreen extends Screen {
    /** One tab: its title and how it draws and scrolls inside the area under the strip. */
    public interface Tab {
        Component title();
        /** Called twice a second while the tab is open: read the vehicle again. */
        default void refresh() {}
        void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY);
        /** The wheel over the tab's area (positive: up); true when it used it. */
        default boolean scrolled(double amount) { return false; }
        default boolean clicked(double mouseX, double mouseY, int button) { return false; }
    }

    private static final List<Function<PartVehicle, Tab>> TABS = new ArrayList<>(List.of(ConditionTab::new));

    /** Adds a tab after those there are (once, at client set-up). */
    public static void addTab(Function<PartVehicle, Tab> factory) { TABS.add(factory); }

    static final int WIDTH = 256, HEIGHT = 206, HEAD = 34, STRIP = 14, MARGIN = 8;
    static final int TEXT = 0xff404040, FAINT = 0xff707070, GOOD = 0xff3f9f4f, WORN = 0xffd9a521, BAD = 0xffc93c36, BAR_BACK = 0xff555555, ROW = 0xffb4b4b4;

    private final PartVehicle vehicle;
    private final List<Tab> tabs = new ArrayList<>();
    private int tab, age, left, top;

    public VehicleStatusScreen(PartVehicle vehicle) {
        super(Component.translatable("hdfv.status.title"));
        this.vehicle = vehicle;
        for (Function<PartVehicle, Tab> f : TABS) { tabs.add(f.apply(vehicle)); }
    }

    @Override protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void tick() {
        if (vehicle.isRemoved() || minecraft == null || minecraft.player == null || minecraft.player.distanceToSqr(vehicle) > 24 * 24) { onClose(); return; }
        if (++age % 10 == 0) { tabs.get(tab).refresh(); }
    }

    private static String kg(double kg) { return String.format(Locale.ROOT, kg < 100 ? "%.1f" : "%.0f", kg); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        MaterialScreens.panel(g, left, top, WIDTH, HEIGHT);
        Vehicle compiled = vehicle.vehicle();
        String name = vehicle.blueprint() == null ? "?" : vehicle.blueprint().name();
        g.drawString(font, font.plainSubstrByWidth(name, WIDTH - 2 * MARGIN), left + MARGIN, top + 7, TEXT, false);
        if (compiled != null) {
            double fuel = 0, capacity = 0;
            String what = "";
            for (int k = 0; k < vehicle.tankCount(); k++) {
                if (compiled.tanks().get(k).tank().own()) { continue; }
                fuel += vehicle.tankKg(k);
                String f = vehicle.tankFuel(k).isEmpty() ? compiled.tanks().get(k).tank().fuel() : vehicle.tankFuel(k);
                if (what.isEmpty()) { what = f; }
                capacity += vehicle.tankCapacityM3(k) * dev.mcmod.hdfv.neoforge.fuel.Fuelling.density(f.isEmpty() ? "gasoline" : f);
            }
            double cargo = 0;
            for (int h = 0; h < vehicle.holdCount(); h++) { cargo += vehicle.holdKg(h); }
            g.drawString(font, Component.translatable("hdfv.status.mass", kg(compiled.properties().massKg() + fuel + cargo)), left + MARGIN, top + 20, FAINT, false);
            Component fuelLine = capacity > 0 ? Component.translatable("hdfv.status.fuel", kg(fuel), kg(capacity)) : Component.translatable("hdfv.status.no_fuel");
            g.drawString(font, fuelLine, left + WIDTH - MARGIN - font.width(fuelLine), top + 20, VehicleConditions.leaking(vehicle) ? BAD : FAINT, false);
        }
        // the strip of tabs
        int x = left + MARGIN, y = top + HEAD;
        for (int i = 0; i < tabs.size(); i++) {
            Component title = tabs.get(i).title();
            int w = font.width(title) + 10;
            g.fill(x, y, x + w, y + STRIP, i == tab ? 0xffffffff : ROW);
            g.fill(x, y + STRIP - 1, x + w, y + STRIP, i == tab ? 0xffffffff : MaterialScreens.EDGE_DARK);
            g.drawString(font, title, x + 5, y + 3, TEXT, false);
            x += w + 2;
        }
        int ax = left + MARGIN, ay = top + HEAD + STRIP + 2, aw = WIDTH - 2 * MARGIN, ah = HEIGHT - HEAD - STRIP - 2 - MARGIN;
        g.fill(ax - 1, ay - 1, ax + aw + 1, ay + ah + 1, MaterialScreens.EDGE_DARK);
        g.fill(ax, ay, ax + aw, ay + ah, 0xffe6e6e6);
        g.enableScissor(ax, ay, ax + aw, ay + ah);
        tabs.get(tab).render(g, font, ax, ay, aw, ah, mouseX, mouseY);
        g.disableScissor();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left + MARGIN, y = top + HEAD;
        for (int i = 0; i < tabs.size(); i++) {
            int w = font.width(tabs.get(i).title()) + 10;
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + STRIP) {
                tab = i;
                tabs.get(i).refresh();
                return true;
            }
            x += w + 2;
        }
        return tabs.get(tab).clicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return tabs.get(tab).scrolled(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Development: the open tab's rows as text (screenshots and checks). */
    public List<String> devLines() {
        return tabs.get(tab) instanceof ConditionTab c ? c.lines() : tabs.get(tab) instanceof RepairTab r ? r.lines() : tabs.get(tab) instanceof CargoTab g ? g.lines() : List.of();
    }

    /** The parts' condition: each part's name, then a row for each of its conditions. */
    static final class ConditionTab implements Tab {
        private static final int PART_ROW = 12, ITEM_ROW = 10, BAR = 76;
        private final PartVehicle vehicle;
        private List<VehicleConditions.Entry> entries;
        private final List<Component> names = new ArrayList<>();
        private int scroll;

        ConditionTab(PartVehicle vehicle) {
            this.vehicle = vehicle;
            refresh();
        }

        @Override public Component title() { return Component.translatable("hdfv.status.tab.condition"); }

        @Override public void refresh() {
            entries = VehicleConditions.of(vehicle);
            if (names.size() != entries.size()) {   // the names do not change while the blueprint stands
                names.clear();
                for (VehicleConditions.Entry e : entries) { names.add(e.name()); }
            }
        }

        private int contentHeight() {
            int h = 4;
            for (VehicleConditions.Entry e : entries) { h += PART_ROW + ITEM_ROW * e.items().size() + 3; }
            return h;
        }

        static int colour(double used) { return used >= Conditions.REPLACE_FROM ? BAD : used >= 0.5 ? WORN : GOOD; }

        List<String> lines() {
            List<String> out = new ArrayList<>();
            for (int k = 0; k < entries.size(); k++) {
                out.add(names.get(k).getString());
                for (VehicleConditions.Item i : entries.get(k).items()) {
                    out.add("  " + i.name().getString() + " " + Math.round(100 * (1 - i.used())) + " %" + (i.replace() ? " " + Component.translatable("hdfv.status.replace").getString() : ""));
                }
            }
            return out;
        }

        @Override public void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
            if (entries.isEmpty()) {
                g.drawString(font, Component.translatable("hdfv.status.nothing"), x + 4, y + 4, FAINT, false);
                return;
            }
            scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight() - height));
            int row = y + 3 - scroll;
            for (int k = 0; k < entries.size(); k++) {
                VehicleConditions.Entry e = entries.get(k);
                g.fill(x, row - 1, x + width, row + PART_ROW - 2, ROW);
                g.drawString(font, font.plainSubstrByWidth(names.get(k).getString(), width - 8), x + 4, row + 1, TEXT, false);
                row += PART_ROW;
                for (VehicleConditions.Item i : e.items()) {
                    double leftShare = 1 - Math.clamp(i.used(), 0.0, 1.0);
                    int c = colour(i.used()), bx = x + 92;
                    g.drawString(font, font.plainSubstrByWidth(i.name().getString(), 78), x + 10, row + 1, FAINT, false);
                    g.fill(bx, row + 2, bx + BAR, row + 8, BAR_BACK);
                    g.fill(bx, row + 2, bx + (int) Math.round(BAR * leftShare), row + 8, c);
                    String percent = Math.round(100 * leftShare) + " %";
                    g.drawString(font, percent, bx + BAR + 4, row + 1, c == GOOD ? TEXT : c, false);
                    if (i.replace()) {
                        Component r = Component.translatable("hdfv.status.replace");
                        g.drawString(font, r, x + width - 4 - font.width(r), row + 1, BAD, false);
                    }
                    row += ITEM_ROW;
                }
                row += 3;
            }
            // a scroll mark at the right edge
            int content = contentHeight();
            if (content > height) {
                int bar = Math.max(12, height * height / content), at = y + (height - bar) * scroll / (content - height);
                g.fill(x + width - 2, at, x + width, at + bar, BAR_BACK);
            }
        }

        @Override public boolean scrolled(double amount) {
            scroll -= (int) Math.round(amount * 2 * ITEM_ROW);
            return true;
        }
    }
}
