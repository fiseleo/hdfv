package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.neoforge.machine.MachineMenu;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.List;
import java.util.Locale;

/**
 * A workshop machine's screen (0.7): the drawing, inputs and outputs, what the machine is doing or why it waits, the
 * time left, and for a forming station which piece of the drawing it starts (with buttons to choose). Drawn with fills
 * (no GUI textures), as the furnace's.
 * 0.17: the consumable row and the consumable a job lacks; a family's head and the NC drive in its tool line.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(HdfvMachines.MACHINE_MENU.get(), MachineScreen::new); }

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MachineMenu.WIDTH;
        imageHeight = menu.height;
        inventoryLabelY = menu.playerTop - 11;
    }

    @Override protected void init() {
        super.init();
        if (menu.station != Station.ASSEMBLY_BENCH) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> click(0)).bounds(leftPos + 44, topPos + 62, 14, 14).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> click(1)).bounds(leftPos + 152, topPos + 62, 14, 14).build());
        } else {
            // 0.23: with no drawing the bench builds by recipe: which of the machines its inputs make
            addRenderableWidget(Button.builder(Component.literal("<"), b -> click(0)).bounds(leftPos + imageWidth - 38, topPos + 3, 14, 12).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> click(1)).bounds(leftPos + imageWidth - 22, topPos + 3, 14, 12).build());
        }
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MaterialScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot s : menu.slots) { MaterialScreens.slot(g, leftPos + s.x, topPos + s.y); }
        // progress bar under the inputs
        boolean bench = menu.station == Station.ASSEMBLY_BENCH;
        int bx = leftPos + (bench ? 8 : 44), by = topPos + (bench ? 80 : 40), bw = bench ? 160 : 86;
        g.fill(bx, by, bx + bw, by + 4, MaterialScreens.ARROW_BACK);
        if (menu.total() > 0) { g.fill(bx, by, bx + (int) ((long) bw * menu.progress() / menu.total()), by + 4, 0xff3fbf5f); }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xff404040, false);
        // 0.17: the consumables' row and its label sit just over the inventory, whose own label would read on from it
        if (menu.consumables == 0) { g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff404040, false); }
        boolean bench = menu.station == Station.ASSEMBLY_BENCH;
        int y = bench ? 86 : 46;
        MachineBlockEntity.State st = menu.state();
        Component status = Component.translatable("hdfv.machine.state." + st.name().toLowerCase(Locale.ROOT));
        g.drawString(font, status, 8, y, st == MachineBlockEntity.State.WORKING ? 0xff2f7f3f : 0xff8f2f2f, false);
        if (menu.total() > 0) {
            int s = Math.max(menu.total() - menu.progress(), 0) / 20;
            Component left = Component.translatable("hdfv.machine.time_left", String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60));
            g.drawString(font, left, imageWidth - 8 - font.width(left), y, 0xff404040, false);
        }
        if (menu.consumables > 0) {
            g.drawString(font, Component.translatable("hdfv.machine.consumables"), 8, menu.consumableY + 5, 0xff404040, false);
        }
        if (st == MachineBlockEntity.State.NO_TOOL && menu.missingConsumable() != null) {
            Component need = Component.translatable("hdfv.machine.needs_consumable", Component.translatable("hdfv.consumable." + menu.missingConsumable().id()));
            g.drawString(font, font.plainSubstrByWidth(need.getString(), imageWidth - 16), 8, 56, 0xff8f2f2f, false);
        }
        if (bench) {
            // 0.23: the recipe it builds with no drawing (as the bench chooses: the selected one of those its inputs make)
            if (menu.drawing().isEmpty() && minecraft != null && minecraft.level != null) {
                List<net.minecraft.world.item.ItemStack> in = new java.util.ArrayList<>();
                for (int i = 0; i < menu.inputs; i++) { in.add(menu.getSlot(1 + i).getItem()); }
                var found = MachineBlockEntity.recipes(minecraft.level, new dev.mcmod.hdfv.neoforge.material.AssemblingRecipe.Input(in));
                if (!found.isEmpty()) {
                    var r = found.get(Math.floorMod(menu.selected(), found.size())).value();
                    Component what = Component.translatable("hdfv.machine.builds", r.result().getHoverName(), found.size() > 1
                            ? (Math.floorMod(menu.selected(), found.size()) + 1) + "/" + found.size() : "");
                    String text = font.plainSubstrByWidth(what.getString(), imageWidth - 44 - font.width(title));
                    g.drawString(font, text, imageWidth - 42 - font.width(text), 6, 0xff2f4f7f, false);
                }
            }
            return;
        }
        if (menu.station == Station.CNC && minecraft != null && minecraft.level != null) {
            var cnc = MachineBlockEntity.cncAt(minecraft.level, menu.pos);
            Component axes = cnc == null ? Component.translatable("hdfv.cnc.none")
                    : Component.translatable("hdfv.cnc.axes", Component.translatable("hdfv.cnc." + (cnc.id().startsWith("cnc_") ? "other" : cnc.id())), cnc.axes(), cnc.simultaneous());
            g.drawString(font, font.plainSubstrByWidth(axes.getString(), imageWidth - 16), 8, 78, 0xff2f4f7f, false);
        }
        // a manual machine tool (0.9): its size, drive and accessories, or the accessory its next step needs
        if (dev.mcmod.hdfv.manufacture.ToolConfig.STATIONS.contains(menu.station) && minecraft != null && minecraft.level != null) {
            var m = MachineBlockEntity.machineAt(minecraft.level, menu.pos, menu.station);
            Component line;
            if (st == MachineBlockEntity.State.NEEDS_ACCESSORY && menu.missing() != null) {
                line = Component.translatable("hdfv.machine.needs", Component.translatable("block.hdfv." + menu.missing().kind));
            } else if (m == null || dev.mcmod.hdfv.structure.Structures.toolConfig(menu.station, m) == null) {
                line = Component.translatable(menu.station.family && m != null ? "hdfv.machine.no_head" : "hdfv.machine.state.not_formed");
            } else {
                var cfg = dev.mcmod.hdfv.structure.Structures.toolConfig(menu.station, m);
                net.minecraft.network.chat.MutableComponent acc = Component.empty();
                int k = 0;
                for (var a : cfg.accessories()) { if (k++ > 0) { acc.append(", "); } acc.append(Component.translatable("block.hdfv." + a.kind)); }
                if (k == 0) { acc = Component.translatable("hdfv.machine.no_accessories"); }
                var frame = dev.mcmod.hdfv.manufacture.MachineFrame.of(menu.station);
                boolean high = frame != null && frame.growth == dev.mcmod.hdfv.manufacture.MachineFrame.Growth.UP;
                Component drive = Component.translatable(dev.mcmod.hdfv.structure.Structures.numerical(m) ? "hdfv.drive.numerical"
                        : dev.mcmod.hdfv.structure.Structures.electric(m) ? "hdfv.drive.electric" : "hdfv.drive.shaft");
                line = cfg.head() != null
                        ? Component.translatable(high ? "hdfv.machine.family_high" : "hdfv.machine.family", Component.translatable("block.hdfv." + cfg.head().kind), cfg.size(), drive, acc)
                        : Component.translatable(high ? "hdfv.machine.tool_high" : "hdfv.machine.tool", cfg.size(), drive, acc);
            }
            var rows = font.split(line, imageWidth - 16);
            for (int i = 0; i < rows.size() && i < 2; i++) { g.drawString(font, rows.get(i), 8, 78 + 10 * i, 0xff2f4f7f, false); }
        }
        // which piece a forming station starts
        Workpieces.Drawing d = Workpieces.drawingOf(menu.drawing());
        if (d == null) { return; }
        PartPlan plan = d.plan();
        if (st == MachineBlockEntity.State.NO_JOB) {
            // 0.10: none of the drawing's work is here: where it goes next
            List<net.minecraft.world.item.ItemStack> inputs = new java.util.ArrayList<>();
            for (int i = 0; i < menu.inputs; i++) { inputs.add(menu.getSlot(1 + i).getItem()); }
            MachineBlockEntity.Next next = MachineBlockEntity.whereNext(menu.station, plan, d.hash(), inputs);
            if (!next.stations().isEmpty()) {
                net.minecraft.network.chat.MutableComponent names = Component.empty();
                int k = 0;
                for (Station s : next.stations()) {
                    if (k++ > 0) { names.append(Component.translatable("hdfv.machine.separator")); }
                    var block = HdfvMachines.MACHINES.get(s);
                    names.append(block == null ? Component.literal(s.name()) : block.get().getName());
                }
                Component line = Component.translatable(next.starting() ? "hdfv.machine.starts_at" : "hdfv.machine.next_at", names);
                g.drawString(font, font.plainSubstrByWidth(line.getString(), imageWidth - 16), 8, 56, 0xff8f2f2f, false);
            }
        }
        List<Workpiece> start = MachineBlockEntity.startable(menu.station, plan);
        if (!start.isEmpty()) {
            Workpiece w = start.get(Math.floorMod(menu.selected(), start.size()));
            Component name = Component.translatable(dev.mcmod.hdfv.manufacture.Workpiece.nameKey(w.key()));
            String text = font.plainSubstrByWidth(name.getString() + " ×" + w.count(), 90);
            g.drawString(font, text, 60, 65, 0xff404040, false);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
