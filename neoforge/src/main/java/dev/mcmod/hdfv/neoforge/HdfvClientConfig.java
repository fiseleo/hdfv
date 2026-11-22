package dev.mcmod.hdfv.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The player's own settings (0.14, hdfv-client.toml): whether a car's dashboard is drawn, its speed in km/h or mph, and
 * whether a manual box's clutch works by itself (the clutch key always presses it further). Before the file is read
 * the defaults apply: the dashboard on, km/h, the automatic clutch on.
 * <p>
 * 0.19 [sound]: hdfv's own volumes in percent (on top of Minecraft's: vehicles are its Friendly Creatures category,
 * machines its Blocks), for all of hdfv and by group (engines, driving: tyres, wind, water, impacts; controls and
 * warnings; machines; inside a closed cabin); how many vehicles get a live stream (Minecraft has at most 8 streaming
 * channels for everything, two are always left free); and switches for the Doppler shift and propagation delay, the
 * real distance law, room reverb and cabin muffling, sonic booms, and distant vehicles.
 * <p>
 * 0.20 [aviation]: the units a tower's words and the ATIS are shown in (aviation: feet, knots, hectopascals; or metric)
 * and whether what a tower says to the player's aircraft is also written in the chat.
 */
public final class HdfvClientConfig {
    private HdfvClientConfig() {}

    public enum Units { KMH, MPH }

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue HUD, AUTO_CLUTCH;
    private static final ModConfigSpec.EnumValue<Units> UNITS;
    private static final ModConfigSpec.IntValue MASTER, ENGINES, DRIVING, CONTROLS, MACHINES, INTERIOR, STREAMS;
    private static final ModConfigSpec.BooleanValue DOPPLER, DISTANCE, REVERB, BOOMS, FAR;
    private static final ModConfigSpec.EnumValue<dev.mcmod.hdfv.airport.AviationUnits.System> AVIATION_UNITS;
    private static final ModConfigSpec.BooleanValue RADIO_CHAT;
    private static final ModConfigSpec.EnumValue<dev.mcmod.hdfv.drive.WorkInputs.Pattern> PATTERN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Driving (0.14): a car's dashboard and keys").push("driving");
        HUD = b.comment("Draw the dashboard (speed and engine dials, gear, lamps) while driving a car").define("dashboard", true);
        UNITS = b.comment("The speedometer's units").defineEnum("units", Units.KMH);
        AUTO_CLUTCH = b.comment("A manual gearbox's clutch works by itself (the clutch key still presses it)").define("autoClutch", true);
        PATTERN = b.comment("0.26: an excavator's joystick pattern in work mode (ISO: the stick on the left stick, the boom on the right; SAE: swapped)")
                .defineEnum("joystickPattern", dev.mcmod.hdfv.drive.WorkInputs.Pattern.ISO);
        b.pop();
        b.comment("Sound (0.19): hdfv's volumes in percent, on top of Minecraft's categories (vehicles: Friendly Creatures, machines: Blocks)")
                .translation("hdfv.configuration.sound").push("sound");
        MASTER = b.comment("All of hdfv's sounds").translation("hdfv.configuration.sound.master").defineInRange("master", 100, 0, 200);
        ENGINES = b.comment("Engines, propellers, rotors").translation("hdfv.configuration.sound.engines").defineInRange("engines", 100, 0, 200);
        DRIVING = b.comment("Tyres, brakes, wind, water, impacts").translation("hdfv.configuration.sound.driving").defineInRange("driving", 100, 0, 200);
        CONTROLS = b.comment("Horn, doors, indicators, gear changes, warnings").translation("hdfv.configuration.sound.controls").defineInRange("controls", 100, 0, 200);
        MACHINES = b.comment("Machine tools, factories, power, petroleum").translation("hdfv.configuration.sound.machines").defineInRange("machines", 100, 0, 200);
        INTERIOR = b.comment("Everything heard inside a closed cabin").translation("hdfv.configuration.sound.interior").defineInRange("interior", 100, 0, 200);
        STREAMS = b.comment("Vehicles with a live synthesized stream (the rest play simpler loops)").translation("hdfv.configuration.sound.streams").defineInRange("streams", 4, 1, 6);
        DOPPLER = b.comment("Doppler shift and the delay of sound over distance").translation("hdfv.configuration.sound.doppler").define("doppler", true);
        DISTANCE = b.comment("Real distance attenuation and air absorption (else Minecraft's linear fade)").translation("hdfv.configuration.sound.distance").define("distance", true);
        REVERB = b.comment("Room reverb and muffling by walls and cabins (OpenAL EFX)").translation("hdfv.configuration.sound.reverb").define("reverb", true);
        BOOMS = b.comment("Sonic booms").translation("hdfv.configuration.sound.booms").define("booms", true);
        FAR = b.comment("Hear loud vehicles beyond the entity tracking range").translation("hdfv.configuration.sound.far").define("far", true);
        b.pop();
        b.comment("Aviation (0.20): control towers and the ATIS").translation("hdfv.configuration.aviation").push("aviation");
        AVIATION_UNITS = b.comment("Units of the tower's words, the ATIS and the radar (AVIATION: ft, kt, hPa; METRIC: m, km/h, m/s)")
                .translation("hdfv.configuration.aviation.units").defineEnum("units", dev.mcmod.hdfv.airport.AviationUnits.System.AVIATION);
        RADIO_CHAT = b.comment("Also write what a tower says to your aircraft in the chat").translation("hdfv.configuration.aviation.radioChat").define("radioChat", false);
        b.pop();
        SPEC = b.build();
    }

    public static boolean dashboard() { return !SPEC.isLoaded() || HUD.get(); }
    public static boolean mph() { return SPEC.isLoaded() && UNITS.get() == Units.MPH; }
    public static boolean autoClutch() { return !SPEC.isLoaded() || AUTO_CLUTCH.get(); }
    /** 0.26: the excavator joystick pattern of work mode. */
    public static dev.mcmod.hdfv.drive.WorkInputs.Pattern joystickPattern() { return SPEC.isLoaded() ? PATTERN.get() : dev.mcmod.hdfv.drive.WorkInputs.Pattern.ISO; }

    private static double percent(ModConfigSpec.IntValue v) { return SPEC.isLoaded() ? v.get() / 100.0 : 1; }

    /** The volume of a sound group (0-2), hdfv's master included. */
    public static double volume(dev.mcmod.hdfv.sound.VehicleSound.Group group) {
        double g = switch (group) { case ENGINES -> percent(ENGINES); case DRIVING -> percent(DRIVING); case CONTROLS -> percent(CONTROLS); case INTERIOR -> percent(INTERIOR); };
        return group == dev.mcmod.hdfv.sound.VehicleSound.Group.INTERIOR ? g : g * percent(MASTER);
    }
    public static double machinesVolume() { return percent(MACHINES) * percent(MASTER); }
    public static int streams() { return SPEC.isLoaded() ? STREAMS.get() : 4; }
    public static boolean doppler() { return !SPEC.isLoaded() || DOPPLER.get(); }
    public static boolean realDistance() { return !SPEC.isLoaded() || DISTANCE.get(); }
    public static boolean reverb() { return !SPEC.isLoaded() || REVERB.get(); }
    public static boolean booms() { return !SPEC.isLoaded() || BOOMS.get(); }
    public static boolean farSounds() { return !SPEC.isLoaded() || FAR.get(); }
    public static dev.mcmod.hdfv.airport.AviationUnits.System aviationUnits() { return SPEC.isLoaded() ? AVIATION_UNITS.get() : dev.mcmod.hdfv.airport.AviationUnits.System.AVIATION; }
    public static boolean radioChat() { return SPEC.isLoaded() && RADIO_CHAT.get(); }
}
