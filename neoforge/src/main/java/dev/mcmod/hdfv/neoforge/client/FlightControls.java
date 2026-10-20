package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleNetwork;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/**
 * hdfv's flight keys while the player drives a part vehicle, beside AeroCore's movement keys ("throttle", "rudder"):
 * "pitch" (+1 nose up) and "roll" (+1 to starboard) while held; the levers "power", "collective" (each moving at half
 * its travel a second while held, staying where left) and "flap" (stepped 0, half, full). 0.10: "reheat" (the
 * afterburner, toggled on and off), "fire" (a solid booster's igniter, 1 while held: it lights on the press) and
 * "start" (a pulsejet's starting air, 1 while held). Sent when they change and once a second; the levers are shown on
 * the action bar while they move.
 * <p>
 * 0.12: "lights" (0 off, 1 position, 2 dipped, 3 main beams; the key steps round), "landing_gear" (0 down, 1 up: a
 * retractable leg's), "doors" (0 shut, 1 open), "wipers" (0 automatic in the rain, 1 on, 2 off) toggled or stepped;
 * the most basic driving keys, "brake" (1 while held) and "selector" (P, R, N, D round: -2, -1, 0, 1, as ShiftRules
 * reads it). Each change is shown on the action bar. The keys default to ones vanilla leaves free.
 * <p>
 * 0.14: in a car (a vehicle with an axle) DriveControls has the driving keys, the brake and selector included; here only
 * the lights, doors and wipers. The flight-only keys are in the flight context (DriveControls.FLIGHT), so the car keys
 * can share theirs. In other vehicles with landing gear the back key also brakes the wheels, and the left and right
 * keys steer a steerable nose wheel ("steer", smooth as a car's).
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class FlightControls {
    private FlightControls() {}

    private static final String CATEGORY = "key.categories.hdfv";
    static final KeyMapping PITCH_UP = flight("pitch_up", GLFW.GLFW_KEY_DOWN), PITCH_DOWN = flight("pitch_down", GLFW.GLFW_KEY_UP),
            ROLL_LEFT = flight("roll_left", GLFW.GLFW_KEY_LEFT), ROLL_RIGHT = flight("roll_right", GLFW.GLFW_KEY_RIGHT),
            POWER_UP = flight("power_up", GLFW.GLFW_KEY_R), POWER_DOWN = flight("power_down", GLFW.GLFW_KEY_F),
            COLLECTIVE_UP = flight("collective_up", GLFW.GLFW_KEY_Y), COLLECTIVE_DOWN = flight("collective_down", GLFW.GLFW_KEY_H),
            FLAPS = flight("flaps", GLFW.GLFW_KEY_G),
            REHEAT = flight("reheat", GLFW.GLFW_KEY_B), FIRE = flight("fire", GLFW.GLFW_KEY_X), AIR_START = flight("air_start", GLFW.GLFW_KEY_N),
            LIGHTS = key("lights", GLFW.GLFW_KEY_K), GEAR = flight("landing_gear", GLFW.GLFW_KEY_J), DOORS = key("doors", GLFW.GLFW_KEY_O),
            WIPERS = key("wipers", GLFW.GLFW_KEY_I), BRAKE = key("brake", GLFW.GLFW_KEY_V), SELECTOR = key("selector", GLFW.GLFW_KEY_Z),
            HORN = key("horn", GLFW.GLFW_KEY_U);   // 0.19: every vehicle (a car's is sent by DriveControls)
    /** Lever travel per second while a key is held. */
    private static final double LEVER_RATE = 0.5, TICK = 0.05;

    private static double power, collective, flap, reheat, lights, gear, doors, wipers, selector, steer;
    /** The selector's positions in the order the key steps through them: P, R, N, D. */
    private static final double[] SELECTOR_STEPS = {-2, -1, 0, 1};
    private static int vehicleId = -1, quiet;
    private static Map<String, Double> last = Map.of();

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.hdfv." + name, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    /** 0.14: a key only for vehicles that are not cars (DriveControls.FLIGHT). */
    private static KeyMapping flight(String name, int glfw) {
        return new KeyMapping("key.hdfv." + name, DriveControls.FLIGHT, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    @EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {}
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            for (KeyMapping k : new KeyMapping[]{PITCH_UP, PITCH_DOWN, ROLL_LEFT, ROLL_RIGHT, POWER_UP, POWER_DOWN, COLLECTIVE_UP, COLLECTIVE_DOWN, FLAPS,
                    REHEAT, FIRE, AIR_START, LIGHTS, GEAR, DOORS, WIPERS, BRAKE, SELECTOR, HORN}) {
                event.register(k);
            }
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof PartVehicle vehicle) || vehicle.getFirstPassenger() != minecraft.player) {
            vehicleId = -1;
            last = Map.of();
            return;
        }
        if (vehicle.getId() != vehicleId) {
            // a new vehicle: start from its own levers as the server last synced them
            vehicleId = vehicle.getId();
            power = vehicle.controls().getOrDefault("power", 0.0);
            collective = vehicle.controls().getOrDefault("collective", 0.0);
            flap = vehicle.controls().getOrDefault("flap", 0.0);
            reheat = vehicle.controls().getOrDefault("reheat", 0.0);
            lights = vehicle.controls().getOrDefault("lights", 0.0);
            gear = vehicle.controls().getOrDefault("landing_gear", 0.0);
            doors = vehicle.controls().getOrDefault("doors", 0.0);
            wipers = vehicle.controls().getOrDefault("wipers", 0.0);
            selector = vehicle.controls().getOrDefault("selector", 0.0);
            steer = 0;
        }
        Component said = null;
        while (LIGHTS.consumeClick()) { lights = (Math.round(lights) + 1) % 4; said = Component.translatable("hdfv.flight.lights." + Math.round(lights)); }
        while (DOORS.consumeClick()) { doors = doors > 0.5 ? 0 : 1; said = Component.translatable(doors > 0.5 ? "hdfv.flight.doors_open" : "hdfv.flight.doors_shut"); }
        while (WIPERS.consumeClick()) { wipers = (Math.round(wipers) + 1) % 3; said = Component.translatable("hdfv.flight.wipers." + Math.round(wipers)); }
        if (DriveControls.kit(vehicle).car()) {
            // 0.14: a car's driving keys are DriveControls'; the flight keys sharing theirs are let go
            for (KeyMapping k : new KeyMapping[]{GEAR, FLAPS, REHEAT}) { while (k.consumeClick()) { } }
            Map<String, Double> now = Map.of("lights", lights, "doors", doors, "wipers", wipers);
            if (!now.equals(last) || ++quiet >= 20) {
                VehicleNetwork.send(vehicle, now);
                last = now;
                quiet = 0;
            }
            if (said != null) { minecraft.player.displayClientMessage(said, true); }
            return;
        }
        while (GEAR.consumeClick()) { gear = gear > 0.5 ? 0 : 1; said = Component.translatable(gear > 0.5 ? "hdfv.flight.gear_up" : "hdfv.flight.gear_down"); }
        while (SELECTOR.consumeClick()) {
            int at = 0;
            for (int i = 0; i < SELECTOR_STEPS.length; i++) { if (Math.abs(SELECTOR_STEPS[i] - selector) < 0.5) { at = i; } }
            selector = SELECTOR_STEPS[(at + 1) % SELECTOR_STEPS.length];
            said = Component.translatable("hdfv.flight.selector." + "PRND".charAt((at + 1) % SELECTOR_STEPS.length));
        }
        double before = power + collective + flap;
        power = lever(power, POWER_UP, POWER_DOWN);
        collective = lever(collective, COLLECTIVE_UP, COLLECTIVE_DOWN);
        while (FLAPS.consumeClick()) { flap = flap >= 0.99 ? 0 : flap + 0.5; }
        boolean reheatToggled = false;
        while (REHEAT.consumeClick()) { reheat = reheat > 0.5 ? 0 : 1; reheatToggled = true; }
        double pitch = (PITCH_UP.isDown() ? 1 : 0) - (PITCH_DOWN.isDown() ? 1 : 0);
        double roll = (ROLL_RIGHT.isDown() ? 1 : 0) - (ROLL_LEFT.isDown() ? 1 : 0);
        // 0.14: on landing gear the back key brakes too, and left and right steer a steerable nose wheel
        boolean free = minecraft.screen == null, legs = legs(vehicle);
        steer = dev.mcmod.hdfv.drive.DriverInputs.steer(steer, free && minecraft.options.keyLeft.isDown(), free && minecraft.options.keyRight.isDown(), DriveControls.speed(vehicle));
        Map<String, Double> now = new java.util.HashMap<>(Map.ofEntries(Map.entry("pitch", pitch), Map.entry("roll", roll), Map.entry("power", power),
                Map.entry("collective", collective), Map.entry("flap", flap), Map.entry("reheat", reheat), Map.entry("fire", FIRE.isDown() ? 1.0 : 0.0),
                Map.entry("start", AIR_START.isDown() ? 1.0 : 0.0), Map.entry("lights", lights), Map.entry("landing_gear", gear), Map.entry("doors", doors),
                Map.entry("wipers", wipers), Map.entry("brake", BRAKE.isDown() || legs && free && minecraft.options.keyDown.isDown() ? 1.0 : 0.0),
                Map.entry("selector", selector), Map.entry("horn", free && HORN.isDown() ? 1.0 : 0.0)));
        if (steers(vehicle)) { now.put("steer", steer); }
        if (!now.equals(last) || ++quiet >= 20) {
            VehicleNetwork.send(vehicle, now);
            last = now;
            quiet = 0;
        }
        if (said != null) {
            minecraft.player.displayClientMessage(said, true);
        } else if (reheatToggled) {
            minecraft.player.displayClientMessage(Component.translatable(reheat > 0.5 ? "hdfv.flight.reheat_on" : "hdfv.flight.reheat_off"), true);
        } else if (power + collective + flap != before) {
            minecraft.player.displayClientMessage(Component.translatable("hdfv.flight.status", Math.round(power * 100), Math.round(collective * 100), Math.round(flap * 100)), true);
        }
    }

    /** Whether a vehicle stands on landing gear (0.14: the back key brakes it). */
    private static boolean legs(PartVehicle v) {
        return v.blueprint() != null && v.blueprint().parts().stream().anyMatch(e -> e.design() instanceof dev.mcmod.hdfv.part.wheel.LandingGearDesign);
    }

    /** Whether something on it reads "steer" (a steerable nose wheel). */
    private static boolean steers(PartVehicle v) {
        return v.blueprint() != null && v.blueprint().parts().stream().anyMatch(e -> e.bindings().stream().anyMatch(b -> b.channel().equals("steer")));
    }

    private static double lever(double value, KeyMapping up, KeyMapping down) {
        double v = value + LEVER_RATE * TICK * ((up.isDown() ? 1 : 0) - (down.isDown() ? 1 : 0));
        return Math.clamp(Math.round(v * 100) / 100.0, 0, 1);
    }
}
