package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.storage.HdfvStorage;
import dev.mcmod.hdfv.neoforge.storage.ItemMass;
import dev.mcmod.hdfv.neoforge.storage.StorageMenu;
import dev.mcmod.hdfv.storage.CargoMass;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * A store's screen (0.25): its slots (a page of them: the shipping container has two, turned with the buttons at the
 * top right or the mouse wheel), the player's inventory, and on the inventory's label line the footer: slots used of
 * all, and the mass of what is in it (with its limit when it has one). Drawn with fills as the machines' (no GUI
 * textures). While it is open every item's tooltip says what it weighs.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(HdfvStorage.MENU.get(), StorageScreen::new); }

    private Button back, forward;

    public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = StorageMenu.WIDTH;
        imageHeight = menu.height;
        inventoryLabelY = menu.playerTop - 11;
    }

    @Override protected void init() {
        super.init();
        if (menu.pages > 1) {
            back = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(leftPos + imageWidth - 38, topPos + 3, 14, 12).build());
            forward = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(leftPos + imageWidth - 22, topPos + 3, 14, 12).build());
            turn(0);
        }
    }

    private void turn(int by) {
        menu.page(menu.page() + by);
        if (back != null) { back.active = menu.page() > 0; }
        if (forward != null) { forward.active = menu.page() < menu.pages - 1; }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.pages > 1 && scrollY != 0) { turn(scrollY < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MaterialScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot s : menu.slots) { if (s.isActive()) { MaterialScreens.slot(g, leftPos + s.x, topPos + s.y); } }
    }

    /** The footer: "12/27 · 340 kg", with a limit "12/27 · 340 / 2,000 kg". */
    static Component footer(StorageMenu menu) {
        String mass = CargoMass.text(menu.massKg());
        return menu.limitKg() > 0 ? Component.translatable("hdfv.storage.footer.limit", menu.usedSlots(), menu.size, mass, CargoMass.text(menu.limitKg()))
                : Component.translatable("hdfv.storage.footer", menu.usedSlots(), menu.size, mass);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int room = imageWidth - 16 - (menu.pages > 1 ? 62 : 0);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), room), titleLabelX, titleLabelY, 0xff404040, false);
        if (menu.pages > 1) {
            Component page = Component.translatable("hdfv.storage.page", menu.page() + 1, menu.pages);
            g.drawString(font, page, imageWidth - 42 - font.width(page), titleLabelY, 0xff404040, false);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xff404040, false);
        Component footer = footer(menu);
        boolean over = menu.limitKg() > 0 && menu.massKg() > menu.limitKg();
        g.drawString(font, footer, imageWidth - 8 - font.width(footer), inventoryLabelY, over ? 0xff8f2f2f : 0xff404040, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** The line an item's tooltip gets while a store is open: "12.5 kg", a stack's "64 × 1 kg = 64 kg". */
    static Component massLine(ItemStack s) {
        double each = ItemMass.kgEach(s);
        return (s.getCount() == 1 ? Component.translatable("hdfv.storage.mass", CargoMass.text(each))
                : Component.translatable("hdfv.storage.mass.stack", s.getCount(), CargoMass.text(each), CargoMass.text(each * s.getCount()))).withStyle(ChatFormatting.GRAY);
    }

    /** What things weigh, on their tooltips, while a store's screen is open (not at other times). */
    @EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
    public static final class Tooltips {
        private Tooltips() {}

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent e) {
            if (e.getItemStack().isEmpty() || !(Minecraft.getInstance().screen instanceof StorageScreen)) { return; }
            e.getToolTip().add(Math.min(1, e.getToolTip().size()), massLine(e.getItemStack()));
        }
    }
}
