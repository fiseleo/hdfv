package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.material.AlloyFurnaceMenu;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.material.Stock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * The material screens (0.7): the alloy furnace's, drawn with fills like the design screen (no GUI textures), and the
 * stock items tinted by their alloy.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MaterialScreens {
    private MaterialScreens() {}

    static final int PANEL = 0xffc6c6c6, EDGE_LIGHT = 0xffffffff, EDGE_DARK = 0xff555555, SLOT = 0xff8b8b8b, FLAME = 0xffff7a1a, ARROW = 0xffffffff, ARROW_BACK = 0xff8b8b8b;

    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(HdfvMaterials.ALLOY_FURNACE_MENU.get(), AlloyFurnaceScreen::new);
        event.register(dev.mcmod.hdfv.neoforge.smelting.HdfvSmelting.SMELTER_MENU.get(), SmelterScreen::new);
    }

    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> {
            if (tint != 0) { return -1; }
            var a = Stock.alloy(stack);
            return a == null ? -1 : 0xff000000 | Stock.color(a);
        }, HdfvMaterials.STOCK.get(), HdfvMaterials.STOCK_BIT.get());
    }

    /** A vanilla-looking panel: a raised grey box, sunken slots under every slot. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w - 1, y + 1, EDGE_LIGHT);
        g.fill(x, y, x + 1, y + h - 1, EDGE_LIGHT);
        g.fill(x + 1, y + h - 1, x + w, y + h, EDGE_DARK);
        g.fill(x + w - 1, y + 1, x + w, y + h, EDGE_DARK);
    }

    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, EDGE_DARK);
        g.fill(x, y, x + 18, y + 18, EDGE_LIGHT);
        g.fill(x, y, x + 16, y + 16, SLOT);
    }

    public static final class AlloyFurnaceScreen extends AbstractContainerScreen<AlloyFurnaceMenu> {
        public AlloyFurnaceScreen(AlloyFurnaceMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            imageWidth = 176;
            imageHeight = 166;
            inventoryLabelY = imageHeight - 94;
        }

        @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            int x = leftPos, y = topPos;
            panel(g, x, y, imageWidth, imageHeight);
            for (Slot s : menu.slots) { slot(g, x + s.x, y + s.y); }
            // flame between the inputs and the fuel
            int fx = x + AlloyFurnaceMenu.FUEL_X + 2, fy = y + AlloyFurnaceMenu.FUEL_Y - 16;
            g.fill(fx, fy, fx + 13, fy + 13, ARROW_BACK);
            if (menu.burnMax() > 0 && menu.burn() > 0) {
                int h = Math.max(1, 13 * menu.burn() / menu.burnMax());
                g.fill(fx, fy + 13 - h, fx + 13, fy + 13, FLAME);
            }
            // the progress arrow toward the outputs
            int ax = x + 72, ay = y + 38;
            g.fill(ax, ay, ax + 30, ay + 8, ARROW_BACK);
            if (menu.total() > 0) { g.fill(ax, ay, ax + 30 * menu.progress() / menu.total(), ay + 8, ARROW); }
        }

        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            renderTooltip(g, mouseX, mouseY);
        }
    }

    /** 0.23: the arc furnace's and the electrolysis cell's screen: inputs, the progress arrow, the outputs, its state, the batch's energy and its draw. */
    public static final class SmelterScreen extends AbstractContainerScreen<dev.mcmod.hdfv.neoforge.smelting.SmelterMenu> {
        public SmelterScreen(dev.mcmod.hdfv.neoforge.smelting.SmelterMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            imageWidth = 176;
            imageHeight = 166;
            inventoryLabelY = imageHeight - 94;
        }

        @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
            int x = leftPos, y = topPos;
            panel(g, x, y, imageWidth, imageHeight);
            for (Slot s : menu.slots) { slot(g, x + s.x, y + s.y); }
            int ax = x + 72, ay = y + 30;
            g.fill(ax, ay, ax + 30, ay + 8, ARROW_BACK);
            g.fill(ax, ay, ax + 30 * menu.progress() / 1000, ay + 8, ARROW);
            // the draw: a bar under the arrow, as a share of the rated power
            g.fill(ax, ay + 12, ax + 30, ay + 15, ARROW_BACK);
            g.fill(ax, ay + 12, ax + 30 * menu.drawn() / 1000, ay + 15, FLAME);
        }

        @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            var st = menu.state();
            Component status = Component.translatable("hdfv.smelter.state." + st.name().toLowerCase(java.util.Locale.ROOT));
            g.drawString(font, font.plainSubstrByWidth(status.getString(), 100), 8, 62, st == dev.mcmod.hdfv.neoforge.smelting.SmelterBlockEntity.State.WORKING ? 0xff2f7f3f : 0xff8f2f2f, false);
            if (menu.batchKwh() > 0) {
                Component e = Component.translatable("hdfv.smelter.energy", String.format(java.util.Locale.ROOT, "%.1f", menu.batchKwh()));
                g.drawString(font, e, imageWidth - 8 - font.width(e), 62, 0xff404040, false);
            }
        }

        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            renderTooltip(g, mouseX, mouseY);
        }
    }
}
