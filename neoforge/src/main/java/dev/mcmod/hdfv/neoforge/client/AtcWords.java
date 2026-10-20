package dev.mcmod.hdfv.neoforge.client;

import dev.mcmod.hdfv.airport.Atc;
import dev.mcmod.hdfv.airport.AviationUnits;
import dev.mcmod.hdfv.neoforge.HdfvClientConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The words of a tower and an ATIS (0.20), built on the client from the numbers the server sent, in the player's
 * language and units (HdfvClientConfig's aviation units): "Runway 27, cleared to land", "Wind 270 degrees 8 knots,
 * gusting 18", "QNH 1013". The keys are hdfv.atc.*.
 */
public final class AtcWords {
    private AtcWords() {}

    static AviationUnits.System units() { return HdfvClientConfig.aviationUnits(); }

    /** An instruction, with its runway and place in sequence where it has them. */
    public static Component instruction(String instruction, String runway, int sequence) {
        Atc.Instruction i;
        try { i = Atc.Instruction.valueOf(instruction); } catch (IllegalArgumentException e) { return Component.literal(instruction); }
        String rwy = runway == null || runway.isEmpty() ? "--" : runway;
        return switch (i) {
            case NONE -> Component.translatable("hdfv.atc.instruction.none");
            case CONTINUE_APPROACH -> Component.translatable("hdfv.atc.instruction.continue_approach", rwy, sequence);
            default -> Component.translatable("hdfv.atc.instruction." + i.name().toLowerCase(java.util.Locale.ROOT), rwy);
        };
    }

    /** A warning. */
    public static Component warning(String warning) {
        try { Atc.Warning.valueOf(warning); } catch (IllegalArgumentException e) { return Component.literal(warning); }
        return Component.translatable("hdfv.atc.warning." + warning.toLowerCase(java.util.Locale.ROOT));
    }

    /** A flight's phase. */
    public static Component phase(String phase) {
        try { Atc.Phase.valueOf(phase); } catch (IllegalArgumentException e) { return Component.literal(phase); }
        return Component.translatable("hdfv.atc.phase." + phase.toLowerCase(java.util.Locale.ROOT));
    }

    /** The wind as said: "calm", "270° 8 kt", "270° 8 kt gusting 18". */
    public static Component wind(int fromDeg, double mps, double gustMps) {
        AviationUnits.System u = units();
        int from = AviationUnits.windFrom(fromDeg, mps);
        if (from == 0) { return Component.translatable("hdfv.atc.wind.calm"); }
        String unit = u == AviationUnits.System.AVIATION ? "hdfv.atc.unit.kt" : "hdfv.atc.unit.mps";
        MutableComponent w = Component.translatable("hdfv.atc.wind", String.format("%03d", from), AviationUnits.windSpeed(mps, u), Component.translatable(unit));
        double gust = AviationUnits.reportedGust(mps, gustMps);
        if (gust > 0) { w.append(Component.translatable("hdfv.atc.wind.gusting", AviationUnits.windSpeed(gust, u))); }
        return w;
    }

    /** A visibility as said: "10 km or more", "5 km", "800 m". */
    public static Component visibility(double m) {
        long v = AviationUnits.visibility(m);
        if (v >= 10_000) { return Component.translatable("hdfv.atc.visibility.ten"); }
        return v >= 5_000 ? Component.translatable("hdfv.atc.visibility.km", v / 1000) : Component.translatable("hdfv.atc.visibility.m", v);
    }

    /** A height above the sea as said (ft or m). */
    public static Component height(double m) {
        AviationUnits.System u = units();
        return Component.translatable(u == AviationUnits.System.AVIATION ? "hdfv.atc.height.ft" : "hdfv.atc.height.m", AviationUnits.height(m, u));
    }

    /** A speed as said (kt or km/h). */
    public static Component speed(double mps) {
        AviationUnits.System u = units();
        return Component.translatable(u == AviationUnits.System.AVIATION ? "hdfv.atc.speed.kt" : "hdfv.atc.speed.kmh", AviationUnits.speed(mps, u));
    }

    /** A distance as said (nautical miles or km), to a tenth. */
    public static Component distance(double m) {
        return units() == AviationUnits.System.AVIATION ? Component.translatable("hdfv.atc.distance.nm", String.format("%.1f", m / 1852))
                : Component.translatable("hdfv.atc.distance.km", String.format("%.1f", m / 1000));
    }

    /** An ATIS as one line: "Information K, runway 27, wind ..., visibility ..., 15 °C, QNH 1013". */
    public static Component atis(CompoundTag a) {
        MutableComponent c = Component.translatable("hdfv.atc.atis", a.getString("Letter"), a.getString("Runway").isEmpty() ? "--" : a.getString("Runway"));
        c.append(", ").append(wind(a.getInt("From"), a.getDouble("Wind"), a.getDouble("Gust")));
        c.append(", ").append(visibility(a.getDouble("Visibility")));
        if (a.getBoolean("Thunder")) { c.append(", ").append(Component.translatable("hdfv.atc.weather.thunder")); }
        else if (a.getBoolean("Rain")) { c.append(", ").append(Component.translatable("hdfv.atc.weather.rain")); }
        c.append(", ").append(Component.translatable("hdfv.atc.temperature", a.getInt("Temperature")));
        c.append(", ").append(Component.translatable("hdfv.atc.qnh", a.getInt("Qnh")));
        return c;
    }
}
