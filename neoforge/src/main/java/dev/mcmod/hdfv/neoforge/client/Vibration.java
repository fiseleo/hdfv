package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.part.engine.PulsejetDesign;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * A running pulsejet shakes whoever rides with it or stands near it (0.10; the user asked for vibration): the view
 * trembles at the engine's firing frequency, by its pressure swing (half a degree at 30 kPa aboard), fading over six
 * blocks for those standing by. Sampled by the frame rate, a 45 Hz buzz shows as a fine, irregular shudder.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class Vibration {
    private Vibration() {}

    private static final double REACH = 6, DEGREES_AT_30_KPA = 0.5;
    private static double amplitude, frequency;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        amplitude = 0;
        if (mc.level == null || mc.player == null) { return; }
        PartVehicle riding = mc.player.getVehicle() instanceof PartVehicle v ? v : null;
        for (PartVehicle v : mc.level.getEntitiesOfClass(PartVehicle.class, mc.player.getBoundingBox().inflate(32))) {
            Vehicle vehicle = v.vehicle();
            if (vehicle == null) { continue; }
            double near = v == riding ? 1 : Math.max(0, 1 - mc.player.distanceTo(v) / REACH);
            if (near <= 0) { continue; }
            for (Vehicle.Installed p : vehicle.installed()) {
                if (!(p.part().design() instanceof PulsejetDesign) || v.shown(p.id(), PulsejetDesign.SHOWN_LIT, 1f) < 0.5) { continue; }
                double a = near * DEGREES_AT_30_KPA * Math.min(2, v.shown(p.id(), PulsejetDesign.SHOWN_AMPLITUDE, 1f) / 30e3);
                if (a > amplitude) { amplitude = a; frequency = v.shown(p.id(), PulsejetDesign.SHOWN_FREQUENCY, 1f); }
            }
        }
    }

    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (amplitude <= 0) { return; }
        double t = Util.getMillis() / 1000.0, w = 2 * Math.PI * frequency * t;
        event.setPitch(event.getPitch() + (float) (amplitude * Math.sin(w)));
        event.setRoll(event.getRoll() + (float) (0.6 * amplitude * Math.sin(1.13 * w + 0.7)));
    }
}
