package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.storage.CargoNetwork;
import dev.mcmod.hdfv.neoforge.storage.VehicleCargo;
import dev.mcmod.hdfv.storage.CargoMass;
import dev.mcmod.hdfv.storage.CargoRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The vehicle status screen's cargo tab (0.25): each hold (a body module's or a storage box's) with its slots used of
 * its slots (asked of the server every two seconds), the mass in it of what it is rated for (red when over: shown, not
 * enforced) and an Open button, which asks the server to open it (it checks the player is near: VehicleCargo.REACH_M).
 *
 * <p>Also the rider's key: on a vehicle with holds the inventory key opens the hold nearest the rider instead of the
 * player's inventory (the menu has the player's inventory under the hold), as a horse with a chest does.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CargoTab implements VehicleStatusScreen.Tab {
    @SubscribeEvent static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> VehicleStatusScreen.addTab(CargoTab::new));
        NeoForge.EVENT_BUS.addListener(CargoTab::riderKey);
    }

    private static final int ROW = 22, BUTTON_W = 44;
    /** The last slots used the server told, by entity id. */
    private static final Map<Integer, int[]> USED = new java.util.concurrent.ConcurrentHashMap<>();

    static void summary(int entity, int[] used) { USED.put(entity, used.clone()); }

    private final PartVehicle vehicle;
    private int refreshes, scroll, x, y, width;

    CargoTab(PartVehicle vehicle) {
        this.vehicle = vehicle;
        CargoNetwork.query(vehicle.getId());
    }

    @Override public Component title() { return Component.translatable("hdfv.status.tab.cargo"); }

    @Override public void refresh() { if (refreshes++ % 4 == 0) { CargoNetwork.query(vehicle.getId()); } }

    /** One hold as shown. */
    record Row(Component name, int used, int slots, double kg, double limitKg) {
        boolean over() { return CargoRules.overloaded(kg, limitKg); }
    }

    List<Row> rows() {
        List<Row> out = new ArrayList<>();
        Vehicle v = vehicle.vehicle();
        if (v == null) { return out; }
        int[] used = USED.getOrDefault(vehicle.getId(), new int[0]);
        for (int k = 0; k < vehicle.holdCount(); k++) {
            out.add(new Row(VehicleCargo.name(v, k), k < used.length ? used[k] : -1, VehicleCargo.slots(v, k), vehicle.holdKg(k), VehicleCargo.limitKg(v, k)));
        }
        return out;
    }

    /** Development: the tab's rows as text. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (Row r : rows()) { out.add(r.name().getString() + " " + r.used() + "/" + r.slots() + " " + CargoMass.text(r.kg()) + "/" + CargoMass.text(r.limitKg()) + (r.over() ? " !" : "")); }
        return out;
    }

    @Override public void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        this.x = x; this.y = y; this.width = width;
        List<Row> rows = rows();
        if (rows.isEmpty()) {
            int line = y + 4;   // wrapped: the line is wider than the tab
            for (var part : font.split(Component.translatable("hdfv.cargo.tab.none"), width - 8)) {
                g.drawString(font, part, x + 4, line, VehicleStatusScreen.FAINT, false);
                line += 10;
            }
            return;
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() * ROW + 4 - height));
        int row = y + 3 - scroll;
        for (int k = 0; k < rows.size(); k++, row += ROW) {
            Row r = rows.get(k);
            g.fill(x, row - 1, x + width, row + ROW - 3, VehicleStatusScreen.ROW);
            g.drawString(font, font.plainSubstrByWidth(r.name().getString(), width - BUTTON_W - 12), x + 4, row + 1, VehicleStatusScreen.TEXT, false);
            Component slots = r.used() < 0 ? Component.translatable("hdfv.cargo.tab.slots_unknown", r.slots()) : Component.translatable("hdfv.cargo.tab.slots", r.used(), r.slots());
            Component mass = r.limitKg() > 0 ? Component.translatable("hdfv.cargo.tab.mass_limit", CargoMass.text(r.kg()), CargoMass.text(r.limitKg()))
                    : Component.translatable("hdfv.cargo.tab.mass", CargoMass.text(r.kg()));
            g.drawString(font, slots, x + 4, row + 10, VehicleStatusScreen.FAINT, false);
            g.drawString(font, mass, x + 4 + font.width(slots) + 8, row + 10, r.over() ? VehicleStatusScreen.BAD : VehicleStatusScreen.FAINT, false);
            int bx = x + width - BUTTON_W - 4, by = row + 2;
            boolean over = mouseX >= bx && mouseX < bx + BUTTON_W && mouseY >= by && mouseY < by + 14;
            g.fill(bx, by, bx + BUTTON_W, by + 14, over ? 0xffffffff : 0xffd0d0d0);
            g.fill(bx, by + 13, bx + BUTTON_W, by + 14, MaterialScreens.EDGE_DARK);
            Component open = Component.translatable("hdfv.cargo.tab.open");
            g.drawString(font, open, bx + (BUTTON_W - font.width(open)) / 2, by + 3, VehicleStatusScreen.TEXT, false);
        }
    }

    @Override public boolean clicked(double mouseX, double mouseY, int button) {
        int n = vehicle.holdCount(), bx = x + width - BUTTON_W - 4;
        for (int k = 0; k < n; k++) {
            int by = y + 3 - scroll + k * ROW + 2;
            if (mouseX >= bx && mouseX < bx + BUTTON_W && mouseY >= by && mouseY < by + 14) {
                CargoNetwork.open(vehicle.getId(), k);
                return true;
            }
        }
        return false;
    }

    @Override public boolean scrolled(double amount) {
        scroll -= (int) Math.round(amount * ROW);
        return true;
    }

    /** The inventory key on a vehicle with holds opens the nearest hold (before vanilla reads the key this tick). */
    static void riderKey(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !(mc.player.getVehicle() instanceof PartVehicle v) || v.holdCount() == 0) { return; }
        boolean pressed = false;
        while (mc.options.keyInventory.consumeClick()) { pressed = true; }
        if (pressed) { CargoNetwork.open(v.getId(), -1); }
    }
}
