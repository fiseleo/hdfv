package dev.mcmod.hdfv.neoforge;

import dev.mcmod.hdfv.manufacture.Pace;
import dev.mcmod.hdfv.paint.DecalImages;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server's settings (0.7, the user's choice 2026-10-13): the two scales every manufacturing cost goes through. How
 * much metal an ingot or a stock item is (kg; a stock bit is a tenth) and how many ticks an hour of real work takes.
 * Defaults: 10 kg and 1000 ticks (an hour's work in a Minecraft hour). Before a world is loaded the defaults apply.
 * 0.8: energy, the joules of work time a Forge Energy unit is (1000: 1 FE = 1 kJ; a lathe at full load about 33 FE a
 * tick).
 * 0.13: stickers, the pictures players upload (whether they may, how many, how big).
 */
public final class HdfvConfig {
    private HdfvConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue KG_PER_INGOT;
    private static final ModConfigSpec.IntValue TICKS_PER_WORK_HOUR;
    private static final ModConfigSpec.DoubleValue JOULES_PER_FE;
    private static final ModConfigSpec.BooleanValue DECAL_UPLOADS, CONSUMABLES;
    private static final ModConfigSpec.DoubleValue CHIP_RECOVERY;
    private static final ModConfigSpec.IntValue DECAL_PER_PLAYER, DECAL_TOTAL, DECAL_BYTES, DECAL_SIDE;
    private static final ModConfigSpec.BooleanValue AIRPORTS, FLAT_AIRPORTS, TOWER_TICKETS, LARGE_AIRPORTS, MEDIUM_AIRPORTS, SMALL_AIRPORTS;
    /** The stickers' defaults: uploads allowed, 64 pictures a player, 2048 on the server. */
    public static final int DEFAULT_DECALS_PER_PLAYER = 64, DEFAULT_DECALS_TOTAL = 2048;
    /** The default: 1 FE is 1 kJ of work time. */
    public static final double DEFAULT_JOULES_PER_FE = 1000;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Manufacturing: every cost comes from the physics through these two scales").push("manufacturing");
        KG_PER_INGOT = b.comment("Metal in an ingot or a stock item, kg (a stock bit is a tenth of it)")
                .defineInRange("kgPerIngot", Pace.KG_PER_INGOT, 0.1, 1000);
        TICKS_PER_WORK_HOUR = b.comment("Ticks an hour of real work takes at a machine or in an assembly room (1000: an hour's work in a Minecraft hour)")
                .defineInRange("ticksPerWorkHour", (int) Pace.TICKS_PER_WORK_HOUR, 1, 72000);
        CONSUMABLES = b.comment("Whether machines use up tools, wheels, blades, electrodes, wire, gas and rivets (0.17; false: only time and power)")
                .define("consumables", true);
        CHIP_RECOVERY = b.comment("Share of the metal a cut removes that comes back as stock bits (0.23; 0: none)")
                .defineInRange("chipRecovery", dev.mcmod.hdfv.manufacture.Chips.RECOVERY, 0, 1);
        b.pop();
        b.comment("Energy (0.8): machines, pumps and generators count Forge Energy in work time").push("energy");
        JOULES_PER_FE = b.comment("Joules of work an FE unit is (1000: 1 FE = 1 kJ)").defineInRange("joulesPerFE", DEFAULT_JOULES_PER_FE, 1, 1e6);
        b.pop();
        b.comment("Stickers (0.13): pictures players upload for their vehicles, kept in the world's hdfv/decals folder").push("decals");
        DECAL_UPLOADS = b.comment("Whether players may upload pictures (operators can still list and remove them)").define("allowUploads", true);
        DECAL_PER_PLAYER = b.comment("Pictures a player may have uploaded").defineInRange("maxPerPlayer", DEFAULT_DECALS_PER_PLAYER, 0, 4096);
        DECAL_TOTAL = b.comment("Pictures on the server").defineInRange("maxTotal", DEFAULT_DECALS_TOTAL, 0, 65536);
        DECAL_BYTES = b.comment("Largest picture, bytes (a PNG as uploaded)").defineInRange("maxBytes", DecalImages.MAX_BYTES, 1024, 4 << 20);
        DECAL_SIDE = b.comment("Longest side of a picture, pixels").defineInRange("maxSide", DecalImages.MAX_SIDE, 16, 2048);
        b.pop();
        b.comment("Airfields (0.20): control towers and airports").push("airfields");
        AIRPORTS = b.comment("Whether world generation builds airports (in newly generated land; turning it off mid-world can leave half an airport at the edge)")
                .define("generateAirports", true);
        FLAT_AIRPORTS = b.comment("Whether airports are built in flat (superflat) worlds too").define("generateInFlatWorlds", false);
        LARGE_AIRPORTS = b.comment("Whether large airports (0.21: 600-900 m runway, tower, full lights) are planned in land not yet planned").define("largeAirports", true);
        MEDIUM_AIRPORTS = b.comment("Whether medium airports (400-550 m runway, small tower) are planned").define("mediumAirports", true);
        SMALL_AIRPORTS = b.comment("Whether small airfields (250-400 m runway, no tower, no lights) are planned").define("smallAirports", true);
        TOWER_TICKETS = b.comment("Whether a control tower keeps its chunk ticking while an aircraft is within twice its radio range (its lights and control work then)")
                .define("towerTickets", true);
        b.pop();
        SPEC = b.build();
    }

    public static double kgPerIngot() { return SPEC.isLoaded() ? KG_PER_INGOT.get() : Pace.KG_PER_INGOT; }
    public static double ticksPerWorkHour() { return SPEC.isLoaded() ? TICKS_PER_WORK_HOUR.get() : Pace.TICKS_PER_WORK_HOUR; }
    public static double joulesPerFE() { return SPEC.isLoaded() ? JOULES_PER_FE.get() : DEFAULT_JOULES_PER_FE; }
    public static boolean decalUploads() { return !SPEC.isLoaded() || DECAL_UPLOADS.get(); }
    /** Whether machines use up their consumables (0.17). */
    public static boolean consumables() { return !SPEC.isLoaded() || CONSUMABLES.get(); }
    public static double chipRecovery() { return SPEC.isLoaded() ? CHIP_RECOVERY.get() : dev.mcmod.hdfv.manufacture.Chips.RECOVERY; }
    public static int decalsPerPlayer() { return SPEC.isLoaded() ? DECAL_PER_PLAYER.get() : DEFAULT_DECALS_PER_PLAYER; }
    public static int decalsTotal() { return SPEC.isLoaded() ? DECAL_TOTAL.get() : DEFAULT_DECALS_TOTAL; }
    public static int decalBytes() { return SPEC.isLoaded() ? DECAL_BYTES.get() : DecalImages.MAX_BYTES; }
    public static int decalSide() { return SPEC.isLoaded() ? DECAL_SIDE.get() : DecalImages.MAX_SIDE; }
    public static boolean airports() { return !SPEC.isLoaded() || AIRPORTS.get(); }
    public static boolean airportsInFlatWorlds() { return SPEC.isLoaded() && FLAT_AIRPORTS.get(); }
    public static boolean towerTickets() { return !SPEC.isLoaded() || TOWER_TICKETS.get(); }
    /** The airport sizes that may be planned (0.21). */
    public static java.util.Set<dev.mcmod.hdfv.airport.AirportTier> airportTiers() {
        java.util.Set<dev.mcmod.hdfv.airport.AirportTier> out = java.util.EnumSet.noneOf(dev.mcmod.hdfv.airport.AirportTier.class);
        if (!SPEC.isLoaded() || LARGE_AIRPORTS.get()) { out.add(dev.mcmod.hdfv.airport.AirportTier.LARGE); }
        if (!SPEC.isLoaded() || MEDIUM_AIRPORTS.get()) { out.add(dev.mcmod.hdfv.airport.AirportTier.MEDIUM); }
        if (!SPEC.isLoaded() || SMALL_AIRPORTS.get()) { out.add(dev.mcmod.hdfv.airport.AirportTier.SMALL); }
        return out;
    }
    /** Seconds of work a tick is. */
    public static double workSecondsPerTick() { return Pace.workSecondsPerTick(ticksPerWorkHour()); }
}
