package dev.mcmod.hdfv.neoforge.client.sound;

import com.mojang.logging.LogUtils;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.sound.VehicleSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;

/**
 * Seeing sound without hearing it (0.19, development): {@code /hdfvdev sound hud on} draws what hdfv plays now (each
 * vehicle followed: whether it streams, its level at the listener, the playback gain, the Doppler factor, the delay,
 * inside or out, and its loudest sources), so a screenshot shows it; {@code /hdfvdev sound log on} writes each start
 * and stop to the log.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class SoundDebug {
    private SoundDebug() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    static volatile boolean hud, log;

    static void log(String format, Object... args) {
        if (log) { LOGGER.info("hdfv sound: " + format, args); }
    }

    /** The controls' sounds (horn, relay, brakes, wipers) last sounding, so each start is logged once. */
    private static final java.util.Map<VehicleSound.Source, Boolean> SOUNDING = new java.util.WeakHashMap<>();

    /**
     * With the log on, after a streaming vehicle's mix is balanced: each of its controls' sounds as it starts, with its
     * level and its gain in this listener's mix (0: not heard here, e.g. the relay outside the cabin).
     */
    static void controls(VehicleSounds.Entry e) {
        if (!log || e.sound == null) { return; }
        for (VehicleSound.Source s : e.sound.sources()) {
            if (s.group() != VehicleSound.Group.CONTROLS) { continue; }
            boolean on = s.levelDb() > -90;
            if (on && !Boolean.TRUE.equals(SOUNDING.get(s))) {
                log("{} on {} ({}): {} dB, gain {} {}{}", s.partId(), e.name(), e.id(), Math.round(s.levelDb()), String.format(Locale.ROOT, "%.3f", s.gain()),
                        e.inside ? "inside" : "outside", s.inside() ? " (heard inside only)" : "");
            }
            SOUNDING.put(s, on);
        }
    }

    @SubscribeEvent static void draw(RenderGuiEvent.Post event) {
        if (!hud) { return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) { return; }
        GuiGraphics g = event.getGuiGraphics();
        int y = 4;
        g.drawString(mc.font, String.format(Locale.ROOT, "hdfv sound: %d free streams", VehicleSounds.freeStreams()), 4, y, 0xffffff, true);
        y += 10;
        var room = dev.mcmod.aerocore.neoforge.client.sound.Listening.room();
        var cabin = VehicleSounds.listenerCabin();
        g.drawString(mc.font, String.format(Locale.ROOT, "room: RT60 %.2f s, closed %.0f %%, %.0f m3; EFX %s (%d filtered); cabin %s",
                room.rt60(), 100 * room.enclosure(), room.volumeM3(), dev.mcmod.aerocore.neoforge.client.sound.Efx.available() ? "on" : "off",
                dev.mcmod.aerocore.neoforge.client.sound.Efx.filtered(),
                cabin == dev.mcmod.aerocore.api.acoustics.Transmission.Insulation.NONE ? "none" : String.format(Locale.ROOT, "%.0f dB, hf %.2f", 20 * Math.log10(cabin.gain()), cabin.gainHf())),
                4, y, 0xc0e0ff, true);
        y += 10;
        for (FarVehicles.Far f : FarVehicles.heard()) {
            if (f.loop == null) { continue; }
            g.drawString(mc.font, String.format(Locale.ROOT, "far %d: %.0f dB gain %.3f doppler %.2f at %.0f m", f.key, f.heardDb, f.gain,
                    f.hearing.doppler(), f.hearing.distanceM()), 4, y, 0xffc0ff, true);
            y += 10;
        }
        List<VehicleSounds.Entry> entries = VehicleSounds.entries();
        for (VehicleSounds.Entry e : entries) {
            if (y > mc.getWindow().getGuiScaledHeight() - 20) { break; }
            String line = String.format(Locale.ROOT, "%s %s  %.0f dB  gain %.3f  doppler %.2f  delay %.2f s  %s",
                    e.playing ? "▶" : e.loop != null ? "~" : "·", e.name(), e.heardDb, e.gain,
                    e.hearing == null ? 1 : e.hearing.doppler(), e.hearing == null ? 0 : e.hearing.delayS(), e.inside ? "inside" : String.format(Locale.ROOT, "%.0f m", e.distance));
            g.drawString(mc.font, line, 4, y, e.playing ? 0x9fff9f : 0xa0a0a0, true);
            y += 10;
            if (e.sound != null && e.playing) {
                for (VehicleSound.Source s : e.sound.sources().stream().sorted((a, c) -> Double.compare(c.levelDb(), a.levelDb())).limit(3).toList()) {
                    g.drawString(mc.font, String.format(Locale.ROOT, "   %s %s %.0f dB (%.2f) %s", group(s.group()), s.partId(), s.levelDb(), s.gain(), s.detail()), 4, y, 0xd0d0ff, true);
                    y += 10;
                }
            }
        }
        // machines: the loudest few loops
        List<MachineSounds.Loop> loops = MachineSounds.loops();
        if (!loops.isEmpty()) {
            g.drawString(mc.font, String.format(Locale.ROOT, "machines: %d playing of %d", MachineSounds.playing(), loops.size()), 4, y, 0xffffff, true);
            y += 10;
        }
        for (MachineSounds.Loop l : loops.stream().limit(6).toList()) {
            if (y > mc.getWindow().getGuiScaledHeight() - 20) { break; }
            boolean on = l.live != null && l.live.playing();
            g.drawString(mc.font, String.format(Locale.ROOT, "%s %s %s  %.0f dB  gain %.3f  pitch %.2f  %.0f m  load %.2f",
                    on ? "▶" : "·", l.hum.process().name().toLowerCase(Locale.ROOT), l.pos.toShortString(), l.heardDb, l.gain, l.pitch, l.distance, l.hum.load()), 4, y, on ? 0xffd080 : 0xa0a0a0, true);
            y += 10;
        }
    }

    private static String group(VehicleSound.Group g) {
        return switch (g) { case ENGINES -> "E"; case DRIVING -> "D"; case CONTROLS -> "C"; case INTERIOR -> "I"; };
    }
}
