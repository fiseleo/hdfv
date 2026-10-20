package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.fuel.Fuelling;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** The fuel gauge (0.8): left of the hotbar while riding a vehicle with tanks, litres aboard of the tanks' brim and the fuel. */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FuelGauge {
    private FuelGauge() {}

    private static final int WIDTH = 100;

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "fuel_gauge"), FuelGauge::render);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !(mc.player.getVehicle() instanceof PartVehicle v) || v.tankCount() == 0) { return; }
        if (DashboardHud.shown(mc)) { return; }   // 0.14: a car's driver has the fuel on the dashboard
        double litres = Fuelling.litres(v), capacity = Fuelling.capacityLitres(v);
        String fuel = "";
        for (int k = 0; k < v.tankCount() && fuel.isEmpty(); k++) { fuel = v.tankFuel(k); }
        double share = capacity > 0 ? Math.clamp(litres / capacity, 0, 1) : 0;
        int x = Math.max(4, g.guiWidth() / 2 - 91 - WIDTH - 12), y = g.guiHeight() - 12;
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + 5, 0xC0000000);
        int colour = share > 0.25 ? 0xFF3FB950 : share > 0.1 ? 0xFFE3B341 : 0xFFE5534B;
        g.fill(x, y, x + (int) Math.round(WIDTH * share), y + 4, colour);
        Component text = Component.translatable("hdfv.hud.fuel", Math.round(litres), Math.round(capacity),
                fuel.isEmpty() ? Component.translatable("hdfv.hud.fuel_empty") : Fuelling.name(fuel));
        g.drawString(mc.font, text, x, y - 10, share > 0.1 ? 0xFFFFFFFF : 0xFFE5534B, true);
    }
}
