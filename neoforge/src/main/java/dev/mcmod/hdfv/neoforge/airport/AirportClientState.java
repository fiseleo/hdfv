package dev.mcmod.hdfv.neoforge.airport;

import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.airport.Runway;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a client has heard from airfields (0.20), kept in plain maps in a common class (safe to load on a server, as
 * SoundSync's client state is): the towers' words to the vehicle the player is in, the view of the tower screen open,
 * the airfields' fittings and runways and their circuits. The client's HUD, screen and lights read it.
 */
public final class AirportClientState {
    private AirportClientState() {}

    /** A tower's word: when it came (client game time), the tag as sent. */
    public record Heard(long at, CompoundTag status) {}

    /** An airfield as sent: its fittings, runways and circuits' intensities, and a count of changes. */
    public record Airfield(List<Fixture> fixtures, List<Runway> runways, float[] circuits, int version) {}

    private static final Map<Integer, Heard> STATUS = new ConcurrentHashMap<>();
    private static final Map<Integer, List<Heard>> SAID = new ConcurrentHashMap<>();
    private static final Map<BlockPos, CompoundTag> VIEWS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Airfield> AIRFIELDS = new ConcurrentHashMap<>();
    private static volatile long now;
    private static int versions;

    /** The client's game time (set by the client each tick). */
    public static void tick(long gameTime) { now = gameTime; }

    public static long now() { return now; }

    static void status(int entityId, CompoundTag status) {
        Heard h = new Heard(now, status);
        STATUS.put(entityId, h);
        if (!status.getString("Said").equals("REMINDER") && (status.contains("Warning") || status.contains("Instruction") && !status.getString("Instruction").equals("NONE"))) {
            List<Heard> said = SAID.computeIfAbsent(entityId, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
            said.add(h);
            while (said.size() > 6) { said.remove(0); }
        }
    }

    /** The last word a tower sent about a vehicle, or null. */
    public static Heard status(int entityId) { return STATUS.get(entityId); }

    /** The last few things said to a vehicle (oldest first). */
    public static List<Heard> said(int entityId) { return SAID.getOrDefault(entityId, List.of()); }

    static void view(BlockPos pos, CompoundTag view) { VIEWS.put(pos.immutable(), view); }

    /** The last view of a tower's screen, or an empty tag. */
    public static CompoundTag view(BlockPos pos) { return VIEWS.getOrDefault(pos, new CompoundTag()); }

    static void lights(BlockPos tower, CompoundTag t) {
        int[] f = t.getIntArray("Fixtures");
        List<Fixture> fixtures = new ArrayList<>(f.length / 4);
        Fixture.Kind[] kinds = Fixture.Kind.values();
        for (int i = 0; i + 3 < f.length; i += 4) { fixtures.add(new Fixture(kinds[Math.floorMod(f[i], kinds.length)], f[i + 1], f[i + 2], f[i + 3])); }
        List<Runway> runways = new ArrayList<>();
        for (Tag x : t.getList("Runways", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            try {
                runways.add(new Runway(Runway.Axis.valueOf(c.getString("Axis")), c.getDouble("Cross"), c.getDouble("Low"), c.getDouble("High"), c.getDouble("Y"),
                        c.getDouble("Width"), c.getString("Suffix")));
            } catch (IllegalArgumentException ignored) {}
        }
        if (fixtures.isEmpty() && runways.isEmpty()) { AIRFIELDS.remove(tower); }
        else {
            Airfield old = AIRFIELDS.get(tower);
            AIRFIELDS.put(tower.immutable(), new Airfield(List.copyOf(fixtures), List.copyOf(runways), old == null ? new float[0] : old.circuits(), ++versions));
        }
    }

    static void circuits(BlockPos tower, float[] levels) {
        Airfield a = AIRFIELDS.get(tower);
        if (a != null) { AIRFIELDS.put(tower, new Airfield(a.fixtures(), a.runways(), levels, a.version())); }
    }

    public static Map<BlockPos, Airfield> airfields() { return Map.copyOf(AIRFIELDS); }

    /** Forgets everything (logging out). */
    public static void clear() { STATUS.clear(); SAID.clear(); VIEWS.clear(); AIRFIELDS.clear(); }
}
