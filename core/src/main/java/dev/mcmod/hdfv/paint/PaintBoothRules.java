package dev.mcmod.hdfv.paint;

import dev.mcmod.hdfv.assembly.RoomScan;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.13: what a paint booth needs (the user's 「加入烤漆房」). A closed room scanned like an assembly room, with a door the
 * vehicle fits through, at least EXHAUSTS extraction fans in its walls or roof (a booth's air carries the overspray
 * out), at least LIGHTS work lights (painters see the colour), exactly one vehicle inside and that one not still being
 * built, and power at its controller. Problems as keys under hdfv.paint_booth.problem with their arguments.
 */
public final class PaintBoothRules {
    private PaintBoothRules() {}

    public static final int EXHAUSTS = 1, LIGHTS = 2;

    public record Problem(String key, List<String> args) {
        public Problem(String key) { this(key, List.of()); }
    }

    /**
     * A booth as found: the room's scan, the extraction fans and work lights at it, the vehicles in it (with the one's
     * size: across and up, m), whether one is still being built, whether its controller has power.
     */
    public record Booth(RoomScan.Scan scan, int exhausts, int lights, int vehicles, double vehicleWidthM, double vehicleHeightM, boolean building, boolean powered) {}

    public static List<Problem> check(Booth b) {
        List<Problem> out = new ArrayList<>();
        RoomScan.Scan s = b.scan();
        if (!s.closed()) {
            out.add(new Problem("open", List.of(String.valueOf(s.leakX()), String.valueOf(s.leakY()), String.valueOf(s.leakZ()))));
            return out;
        }
        if (s.doors().isEmpty()) { out.add(new Problem("no_door")); }
        else if (b.vehicles() == 1) {
            boolean fits = s.doors().stream().anyMatch(d -> d.widthM() + 1e-9 >= b.vehicleWidthM() && d.heightM() + 1e-9 >= b.vehicleHeightM());
            if (!fits) { out.add(new Problem("door_small", List.of(fmt(b.vehicleWidthM()), fmt(b.vehicleHeightM())))); }
        }
        if (b.exhausts() < EXHAUSTS) { out.add(new Problem("no_exhaust")); }
        if (b.lights() < LIGHTS) { out.add(new Problem("few_lights", List.of(String.valueOf(b.lights()), String.valueOf(LIGHTS)))); }
        if (b.vehicles() == 0) { out.add(new Problem("no_vehicle")); }
        else if (b.vehicles() > 1) { out.add(new Problem("vehicles", List.of(String.valueOf(b.vehicles())))); }
        if (b.building()) { out.add(new Problem("building")); }
        if (!b.powered()) { out.add(new Problem("no_power")); }
        return out;
    }

    private static String fmt(double m) { return String.format(java.util.Locale.ROOT, "%.1f", m); }
}
