package dev.mcmod.hdfv.look;

import java.util.Locale;

/**
 * What a wheel leaves on the ground and throws up (0.12), by the ground under it: display rules, not physics (the
 * tyre's forces are AeroCore's whatever the block). From the wheel's slip speed (m/s, its contact patch sliding over
 * the ground: AeroCore's shown value), its load (N) and its speed over the ground (m/s):
 * <ul>
 * <li>hard ground (asphalt, concrete, stone, wood): tyre smoke once the patch slides faster than SMOKE_SLIP (a burnout,
 * a locked wheel, a drift: the tread heats until it smokes), black marks from MARK_SLIP; wet, spray behind the wheel
 * from SPRAY_SPEED and no smoke;</li>
 * <li>gravel, sand, dirt and grass: the block's dust (and its particles) from DUST_SPEED or any slide, ruts where it is
 * loaded (grass only where it slides);</li>
 * <li>mud and clay: thrown mud, deep ruts;</li>
 * <li>snow: powder thrown up, grey ruts;</li>
 * <li>ice: nothing (it neither marks nor smokes).</li>
 * </ul>
 * Rates are particles a second at full load (LOAD_N; less loaded, fewer); the client keeps to its particle budget.
 */
public final class GroundEffects {
    private GroundEffects() {}

    /** The ground under a wheel, by what it throws and keeps. */
    public enum Ground { HARD, GRAVEL, SAND, DIRT, GRASS, MUD, SNOW, ICE, NONE }

    /** Slip speeds (m/s) from which hard ground smokes and marks; speed (m/s) from which a wet road sprays and soft ground dusts. */
    public static final double SMOKE_SLIP = 3, MARK_SLIP = 2, SPRAY_SPEED = 8, DUST_SPEED = 3;
    /** A wheel's load at which the rates are full, N; under MIN_LOAD_N it is off the ground (nothing). */
    public static final double LOAD_N = 3000, MIN_LOAD_N = 150;

    /**
     * What a wheel does now: particles a second of smoke, dust (the block's own and a cloud), mud, snow and spray, and
     * its mark (ARGB, alpha its darkness; 0 none) and how long that lasts (s).
     */
    public record Effect(double smoke, double dust, double mud, double snow, double spray, int markArgb, double markLifeS) {
        public static final Effect NONE = new Effect(0, 0, 0, 0, 0, 0, 0);

        public boolean any() { return smoke > 0 || dust > 0 || mud > 0 || snow > 0 || spray > 0 || markArgb != 0; }
    }

    /** The ground of a block by its id ("minecraft:sand"): hdfv's asphalt and anything hard by default. */
    public static Ground ground(String blockId) {
        String id = blockId == null ? "" : blockId.toLowerCase(Locale.ROOT);
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (name.isEmpty() || name.equals("air") || name.endsWith("_air") || name.equals("water") || name.equals("lava")) { return Ground.NONE; }
        if (name.contains("ice")) { return Ground.ICE; }
        if (name.contains("snow")) { return Ground.SNOW; }
        if (name.equals("mud") || name.contains("muddy") || name.equals("clay") || name.startsWith("soul_s")) { return Ground.MUD; }
        if (name.contains("gravel")) { return Ground.GRAVEL; }
        if (name.contains("sand") && !name.contains("sandstone")) { return Ground.SAND; }
        if (name.equals("grass_block") || name.equals("moss_block") || name.equals("mycelium")) { return Ground.GRASS; }
        if (name.contains("dirt") || name.equals("podzol") || name.equals("farmland") || name.equals("dirt_path") || name.equals("rooted_dirt")) { return Ground.DIRT; }
        return Ground.HARD;
    }

    /** What a wheel on some ground (wet: rain on it) does at a slip speed, load and speed. */
    public static Effect of(Ground g, boolean wet, double slipMps, double loadN, double speedMps) {
        if (g == Ground.NONE || g == Ground.ICE || !(loadN >= MIN_LOAD_N)) { return Effect.NONE; }
        double load = Math.min(1, loadN / LOAD_N), slip = Math.max(0, slipMps), speed = Math.abs(speedMps);
        return switch (g) {
            case HARD -> {
                if (wet) {
                    yield new Effect(0, 0, 0, 0, 30 * ramp(speed, SPRAY_SPEED, 30) * load + 20 * ramp(slip, SMOKE_SLIP, 12) * load, 0, 0);
                }
                double smoke = 40 * ramp(slip, SMOKE_SLIP, 15) * load;
                int alpha = (int) Math.round(150 * ramp(slip, MARK_SLIP, 10) * (0.5 + 0.5 * load));
                yield new Effect(smoke, 0, 0, 0, 0, alpha > 0 ? alpha << 24 | 0x101012 : 0, 300);
            }
            case GRAVEL, SAND, DIRT -> {
                double dust = (wet ? 0.3 : 1) * (20 * ramp(speed, DUST_SPEED, 20) + 30 * ramp(slip, 0.5, 8)) * load;
                int rut = g == Ground.SAND ? 0x3a2e1c : g == Ground.GRAVEL ? 0x2a2622 : 0x2e2216;
                int alpha = (int) Math.round((g == Ground.GRAVEL ? 70 : 100) * (0.4 + 0.6 * load) * (wet ? 1.3 : 1));
                yield new Effect(0, dust, wet ? 6 * ramp(slip, 0.5, 6) * load : 0, 0, 0, Math.min(alpha, 255) << 24 | rut, 120);
            }
            case GRASS -> {
                double dust = (wet ? 0 : 1) * 12 * ramp(slip, 1, 8) * load;
                int alpha = (int) Math.round(90 * ramp(slip, MARK_SLIP, 8));
                yield new Effect(0, dust, wet ? 10 * ramp(slip, 1, 6) * load : 0, 0, 0, alpha > 0 ? alpha << 24 | 0x2c2416 : 0, 90);
            }
            case MUD -> new Effect(0, 0, 30 * Math.min(1, ramp(slip, 0.3, 8) + 0.6 * ramp(speed, 2, 15)) * load, 0, 0, (int) Math.round(140 * (0.5 + 0.5 * load)) << 24 | 0x241a10, 180);
            case SNOW -> new Effect(0, 0, 0, (25 * ramp(speed, 2, 15) + 30 * ramp(slip, 0.5, 8)) * load, 0, (int) Math.round(80 * (0.5 + 0.5 * load)) << 24 | 0x6a6e74, 300);
            case ICE, NONE -> Effect.NONE;
        };
    }

    /** 0 at or below from, rising to 1 at to. */
    static double ramp(double v, double from, double to) { return Math.clamp((v - from) / (to - from), 0, 1); }
}
