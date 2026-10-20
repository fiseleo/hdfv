package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleNetwork;
import dev.mcmod.hdfv.drive.CarKit;
import dev.mcmod.hdfv.drive.DriverInputs;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A car's keys (0.14): while the player drives a vehicle with an axle (CarKit.car), its keys go through DriverInputs
 * to the channels the car reads ("steer", "brake", "handbrake", the selector and paddles or a manual's lever and clutch,
 * the transfer case and the lockers), sent when they change and once a second; what changed is shown on the action
 * bar. Left and right are the movement keys (AeroCore still sends them as "rudder"), back is the brake pedal.
 * <p>
 * The car keys share keys with the flight keys (shift up R and down F with the power lever, drive mode G with the flaps,
 * the lockers B with the reheat) and the handbrake with jumping (space): each group has its own conflict context, active
 * only in its kind of vehicle, so a key moves one of them. The brake and selector keys are FlightControls' own, read here
 * in a car. 0.18: the coupling key J (the flight keys' landing gear in an aircraft) counts "couple" on a tractor.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class DriveControls {
    private DriveControls() {}

    /** The car keys' context: in the game (no screen open), driving a car. */
    public static final IKeyConflictContext CAR = new IKeyConflictContext() {
        @Override public boolean isActive() { return Minecraft.getInstance().screen == null && car(Minecraft.getInstance()) != null; }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this || other == net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL; }
    };
    /** The flight keys' context: in the game, not driving a car. */
    public static final IKeyConflictContext FLIGHT = new IKeyConflictContext() {
        @Override public boolean isActive() { return Minecraft.getInstance().screen == null && car(Minecraft.getInstance()) == null; }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this || other == net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL; }
    };

    private static final String CATEGORY = "key.categories.hdfv";
    static final KeyMapping SHIFT_UP = key("shift_up", GLFW.GLFW_KEY_R), SHIFT_DOWN = key("shift_down", GLFW.GLFW_KEY_F), CLUTCH = key("clutch", GLFW.GLFW_KEY_C),
            HANDBRAKE = key("handbrake", GLFW.GLFW_KEY_SPACE), DRIVE_MODE = key("drive_mode", GLFW.GLFW_KEY_G), DIFF_LOCK = key("diff_lock", GLFW.GLFW_KEY_B),
            RIDE_HEIGHT = key("ride_height", GLFW.GLFW_KEY_H), COUPLE = key("couple", GLFW.GLFW_KEY_J),
            // 0.19: the indicators and the hazards (the arrows are the flight keys only when not in a car)
            INDICATE_LEFT = key("indicate_left", GLFW.GLFW_KEY_LEFT), INDICATE_RIGHT = key("indicate_right", GLFW.GLFW_KEY_RIGHT),
            HAZARD = key("hazard", GLFW.GLFW_KEY_DOWN);

    private static final DriverInputs INPUTS = new DriverInputs();
    private static final Map<VehicleBlueprint, CarKit> KITS = new WeakHashMap<>();
    private static int vehicleId = -1, quiet;
    private static Map<String, Double> last = Map.of();

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.hdfv." + name, CAR, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    @EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {}
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            for (KeyMapping k : new KeyMapping[]{SHIFT_UP, SHIFT_DOWN, CLUTCH, HANDBRAKE, DRIVE_MODE, DIFF_LOCK, RIDE_HEIGHT, COUPLE,
                    INDICATE_LEFT, INDICATE_RIGHT, HAZARD}) { event.register(k); }
        }
    }

    /** What a vehicle has for a driver (cached by its blueprint). */
    public static CarKit kit(PartVehicle v) {
        VehicleBlueprint bp = v.blueprint();
        if (bp == null) { return CarKit.NONE; }
        synchronized (KITS) { return KITS.computeIfAbsent(bp, CarKit::of); }
    }

    /** The car the player drives (the first seat of a vehicle with an axle), or null. */
    public static PartVehicle car(Minecraft minecraft) {
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof PartVehicle v) || v.getFirstPassenger() != minecraft.player) { return null; }
        return kit(v).car() ? v : null;
    }

    /** The channels last sent for the car the player drives (empty when none): the dashboard shows them before the server's come back. */
    public static Map<String, Double> sent(PartVehicle v) { return v.getId() == vehicleId ? last : Map.of(); }

    /** The player's speed in a vehicle, m/s, from the motion the server sends. */
    static double speed(PartVehicle v) { return v.getDeltaMovement().length() * 20; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        PartVehicle v = car(minecraft);
        if (v == null) {
            vehicleId = -1;
            last = Map.of();
            for (KeyMapping k : new KeyMapping[]{SHIFT_UP, SHIFT_DOWN, DRIVE_MODE, DIFF_LOCK, RIDE_HEIGHT, COUPLE, INDICATE_LEFT, INDICATE_RIGHT, HAZARD}) {
                while (k.consumeClick()) { }
            }
            return;
        }
        CarKit kit = kit(v);
        if (v.getId() != vehicleId) {
            vehicleId = v.getId();
            INPUTS.adopt(kit, v.controls());
            last = Map.of();
        }
        boolean free = minecraft.screen == null;
        DriverInputs.Keys keys = new DriverInputs.Keys(free && minecraft.options.keyLeft.isDown(), free && minecraft.options.keyRight.isDown(),
                free && minecraft.options.keyDown.isDown(), free && FlightControls.BRAKE.isDown(), free && HANDBRAKE.isDown(), free && CLUTCH.isDown(),
                clicks(SHIFT_UP), clicks(SHIFT_DOWN), clicks(FlightControls.SELECTOR), clicks(DRIVE_MODE), clicks(DIFF_LOCK), clicks(RIDE_HEIGHT), clicks(COUPLE),
                clicks(INDICATE_LEFT), clicks(INDICATE_RIGHT), clicks(HAZARD), free && FlightControls.HORN.isDown());
        Map<String, Double> now = INPUTS.tick(kit, keys, speed(v), HdfvClientConfig.autoClutch());
        if (!now.equals(last) || ++quiet >= 20) {
            VehicleNetwork.send(v, now);
            last = now;
            quiet = 0;
        }
        DriverInputs.Message said = INPUTS.message();
        if (said != null && minecraft.player != null) { minecraft.player.displayClientMessage(Component.translatable(said.key(), said.args()), true); }
    }

    private static int clicks(KeyMapping k) {
        int n = 0;
        while (k.consumeClick()) { n++; }
        return n;
    }
}
