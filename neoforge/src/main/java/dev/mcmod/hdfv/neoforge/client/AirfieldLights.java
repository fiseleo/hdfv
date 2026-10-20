package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.aerocore.neoforge.client.FieldLamps;
import dev.mcmod.hdfv.airport.AirfieldLighting;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.airport.AirportClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * An airfield's lights on the client (0.20): what the server sent of each airfield near the player (its fittings and
 * runways, AirTraffic) becomes AeroCore's FieldLamps by core AirfieldLighting, once per change; the circuits'
 * intensities the tower sets go to FieldLamps' circuits. FieldLamps draws them to 8 km, beyond the chunks loaded.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class AirfieldLights {
    private AirfieldLights() {}

    /** FieldLamps' owner for an airfield: its tower. */
    record Owner(BlockPos tower) {}

    private record Shown(int version, float[] circuits) {}

    private static final Map<BlockPos, Shown> SHOWN = new HashMap<>();

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!SHOWN.isEmpty()) { SHOWN.keySet().forEach(t -> FieldLamps.remove(new Owner(t))); SHOWN.clear(); }
            return;
        }
        Map<BlockPos, AirportClientState.Airfield> now = AirportClientState.airfields();
        SHOWN.keySet().removeIf(t -> {
            if (now.containsKey(t)) { return false; }
            FieldLamps.remove(new Owner(t));
            return true;
        });
        for (Map.Entry<BlockPos, AirportClientState.Airfield> e : now.entrySet()) {
            Owner owner = new Owner(e.getKey());
            AirportClientState.Airfield a = e.getValue();
            Shown was = SHOWN.get(e.getKey());
            if (was == null || was.version() != a.version()) {
                FieldLamps.put(mc.level.dimension(), owner, AirfieldLighting.lamps(a.runways(), a.fixtures()));
                was = new Shown(a.version(), null);
            }
            if (was.circuits() == null || !Arrays.equals(was.circuits(), a.circuits())) {
                for (int c = 0; c < AirfieldLighting.CIRCUITS; c++) { FieldLamps.circuit(owner, c, c < a.circuits().length ? a.circuits()[c] : 0f); }
                was = new Shown(was.version(), a.circuits().clone());
            }
            SHOWN.put(e.getKey(), was);
        }
    }
}
