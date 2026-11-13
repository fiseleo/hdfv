package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.damage.VehicleConditions;
import dev.mcmod.hdfv.repair.FieldRepair;
import dev.mcmod.hdfv.repair.RepairJob;
import dev.mcmod.hdfv.repair.RepairRules;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The vehicle status screen's repair tab (0.25): for each condition that is used, what puts it right: a field kit on
 * the spot (which, and how far: FieldRepair), the repair shop (mended there), or a replacement (new pieces of the
 * part's design, a new part of its design by name, a rebuild in the room; RepairJob.kindOf); and the wreck rule's
 * warning when a body or chassis is used up. Read from what the client has of the vehicle (whole per cent).
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RepairTab implements VehicleStatusScreen.Tab {
    @SubscribeEvent static void setup(FMLClientSetupEvent e) { e.enqueueWork(() -> VehicleStatusScreen.addTab(RepairTab::new)); }

    private static final int PART_ROW = 12, LINE = 10;

    /** A line: a part's name (bold row), or a condition and what puts it right, in a colour. */
    private record Line(Component text, int colour, boolean header) {}

    private final PartVehicle vehicle;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;

    RepairTab(PartVehicle vehicle) {
        this.vehicle = vehicle;
        refresh();
    }

    @Override public Component title() { return Component.translatable("hdfv.status.tab.repair"); }

    @Override public void refresh() {
        lines.clear();
        boolean wreck = false;
        for (VehicleConditions.Entry e : VehicleConditions.of(vehicle)) {
            PartDesign d = e.part().part().design();
            List<Line> mine = new ArrayList<>();
            for (VehicleConditions.Item i : e.items()) {
                if (!(i.used() > 0)) { continue; }
                if (RepairRules.carrier(d) && Conditions.STRUCTURE.equals(i.condition()) && i.used() >= 1) { wreck = true; }
                mine.add(new Line(Component.translatable("hdfv.repair.tab.row", i.name(), Math.round(100 * (1 - i.used())), what(d, i)), colour(d, i), false));
            }
            if (!mine.isEmpty()) {
                lines.add(new Line(e.name(), VehicleStatusScreen.TEXT, true));
                lines.addAll(mine);
            }
        }
        if (wreck) { lines.add(0, new Line(Component.translatable("hdfv.repair.tab.wreck"), VehicleStatusScreen.BAD, false)); }
    }

    /** What puts a condition right. */
    static Component what(PartDesign d, VehicleConditions.Item i) {
        FieldRepair.Kit kit = FieldRepair.kitFor(i.condition(), i.used());
        if (kit != null) {
            double after = FieldRepair.use(kit, i.condition(), i.used()).after();
            return Component.translatable("hdfv.repair.tab.field", Component.translatable("item.hdfv." + kit.id()), Math.round(100 * (1 - after)));
        }
        RepairJob.Kind k = RepairJob.kindOf(d, i.condition(), i.used());
        if (k == null) { return Component.empty(); }
        return switch (k) {
            case MEND -> Component.translatable("hdfv.repair.tab.mend");
            case NEW_PIECES -> Component.translatable("hdfv.repair.tab.pieces");
            case NEW_PART -> Component.translatable("hdfv.repair.tab.part", d.name());
            case REBUILD -> Component.translatable("hdfv.repair.tab.rebuild");
        };
    }

    private static int colour(PartDesign d, VehicleConditions.Item i) {
        if (FieldRepair.kitFor(i.condition(), i.used()) != null) { return VehicleStatusScreen.GOOD; }
        return RepairJob.kindOf(d, i.condition(), i.used()) == RepairJob.Kind.MEND ? VehicleStatusScreen.WORN : VehicleStatusScreen.BAD;
    }

    /** Development: the tab's lines as text. */
    public List<String> lines() { return lines.stream().map(l -> (l.header() ? "" : "  ") + l.text().getString()).toList(); }

    /** The tab's height: a condition's line wraps (what puts it right, a part named in full, is too long for one). */
    private int contentHeight(Font font, int width) {
        int h = 4;
        for (Line l : lines) { h += l.header() ? PART_ROW : LINE * Math.max(1, font.split(l.text(), width - 14).size()); }
        return h;
    }

    @Override public void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            g.drawString(font, Component.translatable("hdfv.repair.tab.nothing"), x + 4, y + 4, VehicleStatusScreen.FAINT, false);
            return;
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight(font, width) - height));
        int row = y + 3 - scroll;
        for (Line l : lines) {
            if (l.header()) {
                g.fill(x, row - 1, x + width, row + PART_ROW - 2, VehicleStatusScreen.ROW);
                g.drawString(font, font.plainSubstrByWidth(l.text().getString(), width - 8), x + 4, row + 1, l.colour(), false);
                row += PART_ROW;
            } else {
                for (var part : font.split(l.text(), width - 14)) {
                    g.drawString(font, part, x + 10, row + 1, l.colour(), false);
                    row += LINE;
                }
            }
        }
    }

    @Override public boolean scrolled(double amount) {
        scroll -= (int) Math.round(amount * 2 * LINE);
        return true;
    }
}
