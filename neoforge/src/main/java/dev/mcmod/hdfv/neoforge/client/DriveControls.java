package dev.mcmod.hdfv.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleNetwork;
import dev.mcmod.hdfv.drive.CarKit;
import dev.mcmod.hdfv.drive.DriverInputs;
import dev.mcmod.hdfv.drive.WorkInputs;
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
 * <p>
 * 0.26: a machine (tracks, drums or work functions, WorkInputs.Kit) also has work mode, toggled by the work-mode key
 * N: then the work keys' context WORK is active instead of CAR (the arrows are the right joystick, R and F move the
 * picked function, X picks it, space floats the blade), the movement keys are the left joystick of a machine that
 * slews (AeroCore's own mapping of them kept quiet) and still drive any other. WorkInputs' channels go with the car's.
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID, value = Dist.CLIENT)
public final class DriveControls {
    private DriveControls() {}

    /** The car keys' context: in the game (no screen open), driving a car, not in work mode. */
    public static final IKeyConflictContext CAR = new IKeyConflictContext() {
        @Override public boolean isActive() { return Minecraft.getInstance().screen == null && car(Minecraft.getInstance()) != null && !working(); }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this || other == net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL; }
    };
    /** The flight keys' context: in the game, not driving a car. */
    public static final IKeyConflictContext FLIGHT = new IKeyConflictContext() {
        @Override public boolean isActive() { return Minecraft.getInstance().screen == null && car(Minecraft.getInstance()) == null; }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this || other == net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL; }
    };
    /** 0.26: the work keys' context: in the game, driving a machine in work mode. */
    public static final IKeyConflictContext WORK = new IKeyConflictContext() {
        @Override public boolean isActive() { return Minecraft.getInstance().screen == null && car(Minecraft.getInstance()) != null && working(); }
        @Override public boolean conflicts(IKeyConflictContext other) { return other == this || other == net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL; }
    };
    /** 0.26: the work-mode key's context: driving a machine, in either mode. */
    public static final IKeyConflictContext MACHINE = new IKeyConflictContext() {
        @Override public boolean isActive() {
            Minecraft m = Minecraft.getInstance();
            PartVehicle v = m.screen == null ? car(m) : null;
            return v != null && machine(v).machine();
        }
        @Override public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == CAR || other == WORK || other == net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL;
        }
    };

    private static final String CATEGORY = "key.categories.hdfv";
    static final KeyMapping SHIFT_UP = key("shift_up", GLFW.GLFW_KEY_R), SHIFT_DOWN = key("shift_down", GLFW.GLFW_KEY_F), CLUTCH = key("clutch", GLFW.GLFW_KEY_C),
            HANDBRAKE = key("handbrake", GLFW.GLFW_KEY_SPACE), DRIVE_MODE = key("drive_mode", GLFW.GLFW_KEY_G), DIFF_LOCK = key("diff_lock", GLFW.GLFW_KEY_B),
            RIDE_HEIGHT = key("ride_height", GLFW.GLFW_KEY_H), COUPLE = key("couple", GLFW.GLFW_KEY_J),
            // 0.19: the indicators and the hazards (the arrows are the flight keys only when not in a car)
            INDICATE_LEFT = key("indicate_left", GLFW.GLFW_KEY_LEFT), INDICATE_RIGHT = key("indicate_right", GLFW.GLFW_KEY_RIGHT),
            HAZARD = key("hazard", GLFW.GLFW_KEY_DOWN);
    // 0.26: the work keys (the right joystick on the arrows, the picked function on R and F)
    static final KeyMapping WORK_MODE = new KeyMapping("key.hdfv.work_mode", MACHINE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY),
            WORK_UP = work("work_stick_forward", GLFW.GLFW_KEY_UP), WORK_DOWN = work("work_stick_back", GLFW.GLFW_KEY_DOWN),
            WORK_LEFT = work("work_stick_left", GLFW.GLFW_KEY_LEFT), WORK_RIGHT = work("work_stick_right", GLFW.GLFW_KEY_RIGHT),
            WORK_RAISE = work("work_raise", GLFW.GLFW_KEY_R), WORK_LOWER = work("work_lower", GLFW.GLFW_KEY_F),
            WORK_SELECT = work("work_select", GLFW.GLFW_KEY_X), WORK_FLOAT = work("work_float", GLFW.GLFW_KEY_SPACE);

    private static final DriverInputs INPUTS = new DriverInputs();
    private static final Map<VehicleBlueprint, CarKit> KITS = new WeakHashMap<>();
    private static final WorkInputs WORK_INPUTS = new WorkInputs();
    private static final Map<VehicleBlueprint, WorkInputs.Kit> MACHINES = new WeakHashMap<>();
    private static int vehicleId = -1, quiet;
    private static Map<String, Double> last = Map.of();

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.hdfv." + name, CAR, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    private static KeyMapping work(String name, int glfw) {
        return new KeyMapping("key.hdfv." + name, WORK, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    static {
        // a machine that slews (or travels by motors) has its movement keys from WorkInputs, not AeroCore's "throttle" and "rudder"
        dev.mcmod.aerocore.neoforge.client.VehicleControls.suppress(v -> v.getId() == vehicleId && WORK_INPUTS.ownsMovementKeys(machine(v)));
    }

    @EventBusSubscriber(modid = Hdfv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {}
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            for (KeyMapping k : new KeyMapping[]{SHIFT_UP, SHIFT_DOWN, CLUTCH, HANDBRAKE, DRIVE_MODE, DIFF_LOCK, RIDE_HEIGHT, COUPLE,
                    INDICATE_LEFT, INDICATE_RIGHT, HAZARD, WORK_MODE, WORK_UP, WORK_DOWN, WORK_LEFT, WORK_RIGHT, WORK_RAISE, WORK_LOWER,
                    WORK_SELECT, WORK_FLOAT}) { event.register(k); }
        }
    }

    /** What a vehicle has for a driver (cached by its blueprint). */
    public static CarKit kit(PartVehicle v) {
        VehicleBlueprint bp = v.blueprint();
        if (bp == null) { return CarKit.NONE; }
        synchronized (KITS) { return KITS.computeIfAbsent(bp, CarKit::of); }
    }

    /** 0.26: a tower crane cab's keys: slew, trolley (on the telescope's axis), hoist and the hook latch. */
    static final WorkInputs.Kit TOWER_CAB = new WorkInputs.Kit(new java.util.LinkedHashSet<>(java.util.List.of(WorkInputs.Function.SWING,
            WorkInputs.Function.HOIST, WorkInputs.Function.TELESCOPE, WorkInputs.Function.HOOK)), false, false);

    /** 0.26: what a vehicle has for an operator (cached by its blueprint). */
    public static WorkInputs.Kit machine(PartVehicle v) {
        VehicleBlueprint bp = v.blueprint();
        if (bp == null) { return WorkInputs.Kit.NONE; }
        // a tower crane's cab: its controller reads the crane's channels (TowerCraneBlockEntity), no part of the cab does
        if (dev.mcmod.hdfv.neoforge.worksite.TowerCraneBlockEntity.CAB.equals(bp.name())) { return TOWER_CAB; }
        synchronized (MACHINES) { return MACHINES.computeIfAbsent(bp, WorkInputs.Kit::of); }
    }

    /** 0.26: whether the machine the player drives is in work mode. */
    public static boolean working() { return vehicleId >= 0 && WORK_INPUTS.working(); }

    /** The car the player drives (the first seat of a vehicle with an axle), or null. */
    public static PartVehicle car(Minecraft minecraft) {
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof PartVehicle v) || v.getFirstPassenger() != minecraft.player) { return null; }
        return kit(v).car() || machine(v).machine() ? v : null;   // 0.26: a machine with no wheels (a tower crane's cab) too
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
            for (KeyMapping k : new KeyMapping[]{SHIFT_UP, SHIFT_DOWN, DRIVE_MODE, DIFF_LOCK, RIDE_HEIGHT, COUPLE, INDICATE_LEFT, INDICATE_RIGHT, HAZARD,
                    WORK_MODE, WORK_SELECT, WORK_FLOAT}) {
                while (k.consumeClick()) { }
            }
            return;
        }
        CarKit kit = kit(v);
        WorkInputs.Kit machine = machine(v);
        if (v.getId() != vehicleId) {
            vehicleId = v.getId();
            INPUTS.adopt(kit, v.controls());
            WORK_INPUTS.adopt(machine, v.controls());
            last = Map.of();
        }
        boolean free = minecraft.screen == null;
        var o = minecraft.options;
        // 0.26: in work mode a machine that slews takes the movement keys as its left joystick, so the car sees none of them
        boolean carKeys = !(WORK_INPUTS.working() && machine.joystickLeft());
        DriverInputs.Keys keys = new DriverInputs.Keys(free && carKeys && o.keyLeft.isDown(), free && carKeys && o.keyRight.isDown(),
                free && carKeys && o.keyDown.isDown(), free && FlightControls.BRAKE.isDown(), free && HANDBRAKE.isDown(), free && CLUTCH.isDown(),
                clicks(SHIFT_UP), clicks(SHIFT_DOWN), clicks(FlightControls.SELECTOR), clicks(DRIVE_MODE), clicks(DIFF_LOCK), clicks(RIDE_HEIGHT), clicks(COUPLE),
                clicks(INDICATE_LEFT), clicks(INDICATE_RIGHT), clicks(HAZARD), free && FlightControls.HORN.isDown());
        Map<String, Double> now = new java.util.LinkedHashMap<>(INPUTS.tick(kit, keys, speed(v), HdfvClientConfig.autoClutch()));
        if (machine.machine()) {
            WorkInputs.Keys work = new WorkInputs.Keys(free && o.keyUp.isDown(), free && o.keyDown.isDown(), free && o.keyLeft.isDown(), free && o.keyRight.isDown(),
                    free && WORK_UP.isDown(), free && WORK_DOWN.isDown(), free && WORK_LEFT.isDown(), free && WORK_RIGHT.isDown(),
                    free && WORK_RAISE.isDown(), free && WORK_LOWER.isDown(), clicks(WORK_SELECT), clicks(WORK_MODE), clicks(WORK_FLOAT));
            now.putAll(WORK_INPUTS.tick(machine, work, HdfvClientConfig.joystickPattern()));
            DriverInputs.Message said = WORK_INPUTS.message();
            if (said != null && minecraft.player != null) { minecraft.player.displayClientMessage(workMessage(said), true); }
        }
        if (!now.equals(last) || ++quiet >= 20) {
            VehicleNetwork.send(v, now);
            last = now;
            quiet = 0;
        }
        DriverInputs.Message said = INPUTS.message();
        if (said != null && minecraft.player != null) { minecraft.player.displayClientMessage(Component.translatable(said.key(), said.args()), true); }
    }

    /** A work message: its arguments that are language keys (a function's name) translated. */
    private static Component workMessage(DriverInputs.Message m) {
        Object[] args = m.args().clone();
        for (int i = 0; i < args.length; i++) { if (args[i] instanceof String x && x.startsWith("hdfv.")) { args[i] = Component.translatable(x); } }
        return Component.translatable(m.key(), args);
    }

    private static int clicks(KeyMapping k) {
        int n = 0;
        while (k.consumeClick()) { n++; }
        return n;
    }
}
