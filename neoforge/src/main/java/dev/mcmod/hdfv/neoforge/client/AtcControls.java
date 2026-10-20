package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.airport.AircraftKind;
import dev.mcmod.hdfv.airport.Atc;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvClientConfig;
import dev.mcmod.hdfv.neoforge.airport.AirportClientState;
import dev.mcmod.hdfv.neoforge.airport.AirportNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The pilot's radio (0.20), keys in the flight context (none of them used by Minecraft or hdfv): M calls the tower
 * (the tower works out whether it is for taxi, take-off or landing; five calls in five seconds with nobody in the tower
 * switch the lights on), comma cancels or says unable, period asks for the ATIS. Only the pilot of an aircraft calls;
 * the server checks that again. When the setting radioChat is on, what the tower says is also written in the chat.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class AtcControls {
    private AtcControls() {}

    private static final String CATEGORY = "key.categories.hdfv";
    static final KeyMapping CALL = key("atc_call", GLFW.GLFW_KEY_M), CANCEL = key("atc_cancel", GLFW.GLFW_KEY_COMMA), ATIS = key("atc_atis", GLFW.GLFW_KEY_PERIOD);
    private static final Map<VehicleBlueprint, AircraftKind> KINDS = new WeakHashMap<>();
    private static long lastHeard = Long.MIN_VALUE;

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.hdfv." + name, DriveControls.FLIGHT, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    @EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {}

        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            event.register(CALL);
            event.register(CANCEL);
            event.register(ATIS);
        }
    }

    /** The aircraft the player is in (any seat), or null. */
    static PartVehicle aircraft(Minecraft minecraft) {
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof PartVehicle v) || v.blueprint() == null) { return null; }
        return KINDS.computeIfAbsent(v.blueprint(), AircraftKind::of).flies() ? v : null;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) { AirportClientState.tick(minecraft.level.getGameTime()); }
        PartVehicle v = aircraft(minecraft);
        boolean pilot = v != null && v.getFirstPassenger() == minecraft.player;
        while (CALL.consumeClick()) { if (pilot) { send(v, Atc.Request.CALL, false); } }
        while (CANCEL.consumeClick()) { if (pilot) { send(v, Atc.Request.CANCEL, false); } }
        while (ATIS.consumeClick()) { if (pilot) { send(v, Atc.Request.CALL, true); } }
        if (v == null || !HdfvClientConfig.radioChat() || minecraft.player == null) { return; }
        AirportClientState.Heard h = AirportClientState.status(v.getId());
        if (h == null || h.at() == lastHeard) { return; }
        lastHeard = h.at();
        Component said = words(h.status());
        if (said != null) { minecraft.player.displayClientMessage(Component.translatable("hdfv.atc.radio", said), false); }
    }

    private static void send(PartVehicle v, Atc.Request request, boolean atis) {
        PacketDistributor.sendToServer(new AirportNetwork.CallPayload(v.getId(), request, atis));
    }

    /** What a tower's word says, or null for the reminder every 5 s (nothing new). */
    static Component words(CompoundTag s) {
        if (s.getString("Said").equals("REMINDER")) { return null; }
        if (s.getString("Said").equals("ATIS") && s.contains("Atis")) { return AtcWords.atis(s.getCompound("Atis")); }
        MutableComponent c = null;
        if (s.contains("Instruction") && !s.getString("Instruction").equals("NONE")) {
            c = Component.empty().append(AtcWords.instruction(s.getString("Instruction"), s.getString("Runway"), s.getInt("Sequence")));
        }
        if (s.contains("Warning")) {
            Component w = AtcWords.warning(s.getString("Warning"));
            c = c == null ? Component.empty().append(w) : c.append(", ").append(w);
        }
        return c;
    }

    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { AirportClientState.clear(); lastHeard = Long.MIN_VALUE; }

    @SubscribeEvent public static void respawned(ClientPlayerNetworkEvent.Clone event) { AirportClientState.clear(); }
}
