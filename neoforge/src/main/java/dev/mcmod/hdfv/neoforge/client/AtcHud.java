package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.airport.AirportClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * The radio box (0.20), its own GUI layer at the top left (the action bar is FlightControls'), only in an aircraft
 * that has heard a tower in the last 15 s (towers remind every 5 s): who is talking (the tower, or nobody in it), the
 * instruction in force with its runway and place in sequence, the ATIS in short, the last warning for 10 s in red.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AtcHud {
    private AtcHud() {}

    /** How long a tower's word stands without a reminder, a warning shows (ticks). */
    static final long STALE_TICKS = 300, WARNING_TICKS = 200;
    private static final int TEXT = 0xffe8f0ff, DIM = 0xffa0b0c0, WARN = 0xffff5050, BACK = 0x90101820;

    @SubscribeEvent public static void layers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "atc"), AtcHud::render);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) { return; }
        PartVehicle v = AtcControls.aircraft(mc);
        if (v == null) { return; }
        AirportClientState.Heard h = AirportClientState.status(v.getId());
        long now = AirportClientState.now();
        if (h == null || now - h.at() > STALE_TICKS) { return; }
        List<Line> lines = lines(h.status(), now);
        Font font = mc.font;
        int w = 0;
        for (Line l : lines) { w = Math.max(w, font.width(l.text())); }
        int x = 6, y = 6, lh = font.lineHeight + 2;
        g.fill(x - 3, y - 3, x + w + 3, y + lines.size() * lh + 1, BACK);
        for (Line l : lines) { g.drawString(font, l.text(), x, y, l.colour(), false); y += lh; }
    }

    private record Line(Component text, int colour) {}

    private static List<Line> lines(CompoundTag s, long now) {
        List<Line> out = new ArrayList<>();
        boolean manned = s.getBoolean("Manned");
        out.add(new Line(Component.translatable(manned ? "hdfv.atc.hud.tower" : "hdfv.atc.hud.unmanned"), DIM));
        String current = s.contains("Current") ? s.getString("Current") : s.getString("Instruction");
        if (!current.isEmpty() && !current.equals("NONE")) { out.add(new Line(AtcWords.instruction(current, s.getString("Runway"), s.getInt("Sequence")), TEXT)); }
        if (s.contains("Phase")) { out.add(new Line(Component.translatable("hdfv.atc.hud.phase", AtcWords.phase(s.getString("Phase"))), DIM)); }
        if (s.contains("Atis")) {
            CompoundTag a = s.getCompound("Atis");
            out.add(new Line(Component.translatable("hdfv.atc.hud.atis", a.getString("Letter"), a.getString("Runway").isEmpty() ? "--" : a.getString("Runway"),
                    AtcWords.wind(a.getInt("From"), a.getDouble("Wind"), a.getDouble("Gust")), a.getInt("Qnh")), TEXT));
        }
        for (AirportClientState.Heard said : AirportClientState.said(currentVehicle())) {
            if (said.status().contains("Warning") && now - said.at() <= WARNING_TICKS) { out.add(new Line(AtcWords.warning(said.status().getString("Warning")), WARN)); }
        }
        return out;
    }

    private static int currentVehicle() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() != null ? mc.player.getVehicle().getId() : -1;
    }
}
