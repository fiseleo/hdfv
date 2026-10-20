package dev.mcmod.hdfv.neoforge.airport;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mcmod.hdfv.airport.AirportLayout;
import dev.mcmod.hdfv.airport.AirportPlanner;
import dev.mcmod.hdfv.airport.AirportTier;
import dev.mcmod.hdfv.airport.Runway;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * /hdfv dev airport (0.20, operators):
 * <ul>
 *   <li>{@code nearest}: the nearest world-generated airport's region, runway and distance (planning the regions round
 *   the player when need be);</li>
 *   <li>{@code goto}: to above that airport's tower;</li>
 *   <li>{@code hash <rx> <rz>}: a region's plan and its layout's digest (the same seed gives the same airport);</li>
 *   <li>{@code stats}: what the level holds (planned airports, towers, fittings, old regions);</li>
 *   <li>{@code build [length]}: an airport laid out where the player stands, its runway along the way they face, its
 *   surface the ground under their feet (QA in an old world, where none is generated): placed a few chunks a tick, then
 *   registered as a generated one.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Hdfv.MOD_ID)
public final class AirportDevCommands {
    private AirportDevCommands() {}

    /** Chunks a dev build places a tick. */
    private static final int CHUNKS_PER_TICK = 6;

    private record Build(ServerLevel level, AirportPlanner.Plan plan, Deque<ChunkPos> left, CommandSourceStack source, int total) {}

    private static final List<Build> BUILDS = new ArrayList<>();

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("airport")
                .then(Commands.literal("nearest").executes(c -> nearest(c.getSource(), false)))
                .then(Commands.literal("goto").executes(c -> nearest(c.getSource(), true)))
                .then(Commands.literal("hash").then(Commands.argument("rx", IntegerArgumentType.integer()).then(Commands.argument("rz", IntegerArgumentType.integer())
                        .executes(c -> hash(c.getSource(), IntegerArgumentType.getInteger(c, "rx"), IntegerArgumentType.getInteger(c, "rz"))))))
                .then(Commands.literal("stats").executes(c -> stats(c.getSource())))
                .then(Commands.literal("probe").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(c -> {
                            String s = AirportWorldgen.probeAt(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"));
                            c.getSource().sendSuccess(() -> Component.literal(s), false);
                            return 1;
                        }))))
                .then(Commands.literal("why").executes(c -> why(c.getSource(), AirportPlanner.regionOf((int) Math.floor(c.getSource().getPosition().x)),
                                AirportPlanner.regionOf((int) Math.floor(c.getSource().getPosition().z))))
                        .then(Commands.argument("rx", IntegerArgumentType.integer()).then(Commands.argument("rz", IntegerArgumentType.integer())
                                .executes(c -> why(c.getSource(), IntegerArgumentType.getInteger(c, "rx"), IntegerArgumentType.getInteger(c, "rz"))))))
                .then(Commands.literal("screen").executes(c -> screen(c.getSource())))
                .then(Commands.literal("runways").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(c -> runways(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))))
                .then(Commands.literal("view").then(Commands.argument("spot", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(VIEWS, b))
                        .executes(c -> view(c.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "spot")))))
                .then(buildTree());
    }

    /** {@code build [large|medium|small] [length]} (0.21: a size; a length alone is a large airport's, as in 0.20). */
    private static LiteralArgumentBuilder<CommandSourceStack> buildTree() {
        LiteralArgumentBuilder<CommandSourceStack> b = Commands.literal("build").executes(c -> build(c.getSource(), AirportTier.LARGE, 600))
                .then(Commands.argument("length", IntegerArgumentType.integer(600, 900)).executes(c -> build(c.getSource(), AirportTier.LARGE, IntegerArgumentType.getInteger(c, "length"))));
        for (AirportTier tier : AirportTier.values()) {
            java.util.List<Integer> lengths = tier.spec.lengths();
            int shortest = lengths.get(lengths.size() - 1), longest = lengths.get(0);
            b.then(Commands.literal(tier.name().toLowerCase(java.util.Locale.ROOT)).executes(c -> build(c.getSource(), tier, shortest))
                    .then(Commands.argument("length", IntegerArgumentType.integer(shortest, longest)).executes(c -> build(c.getSource(), tier, IntegerArgumentType.getInteger(c, "length")))));
        }
        return b;
    }

    private static String describe(AirportPlanner.Plan p) {
        var c = p.runway().centre();
        AirportLayout.TowerSite t = AirportLayout.tower(p);
        String tower = t == null ? "no tower" : String.format("tower at %d %d %d (%d courses)", t.x(), t.y(), t.z(), t.courses());
        return String.format("region %d %d: %s, runway %d m along %s centred %.0f %d %.0f, %s", p.regionX(), p.regionZ(), p.tier().name().toLowerCase(java.util.Locale.ROOT),
                p.lengthM(), p.axis(), c.x(), p.surfaceY(), c.z(), tower);
    }

    private static int nearest(CommandSourceStack source, boolean go) {
        ServerLevel level = source.getLevel();
        if (!AirportWorldgen.planning(level)) { source.sendFailure(Component.literal("No airports in this level")); return 0; }
        Vec3 at = source.getPosition();
        AirportPlanner.Plan p = AirportWorldgen.nearest(level, at.x, at.z, 2);
        if (p == null) { source.sendFailure(Component.literal("No airport within two regions (" + 2 * AirportPlanner.REGION + " m)")); return 0; }
        var c = p.runway().centre();
        source.sendSuccess(() -> Component.literal(describe(p) + String.format(", %.0f m away", Math.hypot(c.x() - at.x, c.z() - at.z))), false);
        if (go && source.getEntity() != null) {
            // over the tower, or a small airfield's runway
            AirportLayout.TowerSite t = AirportLayout.tower(p);
            double gx = t != null ? t.x() + 0.5 : c.x(), gz = t != null ? t.z() + 0.5 : c.z();
            source.getEntity().teleportTo(level, gx, p.surfaceY() + 40, gz, java.util.Set.of(), source.getEntity().getYRot(), 30f);
        }
        return 1;
    }

    private static int hash(CommandSourceStack source, int rx, int rz) {
        AirportPlanner.Plan p = AirportWorldgen.planOf(source.getLevel(), rx, rz);
        if (p == null) { source.sendSuccess(() -> Component.literal("region " + rx + " " + rz + ": no airport"), false); return 1; }
        source.sendSuccess(() -> Component.literal(describe(p) + String.format(", layout %016x", AirportLayout.digest(p))), false);
        return 1;
    }

    /** The planner's reasons for a region, candidate by candidate (also written in the log). */
    private static int why(CommandSourceStack source, int rx, int rz) {
        List<String> lines = AirportWorldgen.diagnose(source.getLevel(), rx, rz);
        source.sendSuccess(() -> Component.literal("region " + rx + " " + rz + ":"), false);
        for (String l : lines) {
            source.sendSuccess(() -> Component.literal("  " + l), false);
            com.mojang.logging.LogUtils.getLogger().info("Airports: region {} {} candidate {}", rx, rz, l);
        }
        return 1;
    }

    /** The runways within 5 km of a point: designator, ends, centre, the tower that owns it. */
    private static int runways(CommandSourceStack source, int x, int z) {
        AirfieldData d = AirfieldData.of(source.getLevel());
        for (Runway r : d.runwaysNear(x, z, 5000)) {
            var c = r.centre();
            net.minecraft.core.BlockPos t = TowerBlockEntity.nearestTower(d, c.x(), c.z());
            source.sendSuccess(() -> Component.literal(String.format("%s: %s %.0f..%.0f across %.1f, centre %.0f %.0f, tower %s", r.name(), r.axis(), r.low(), r.high(),
                    r.cross(), c.x(), c.z(), t.toShortString())), false);
        }
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        AirfieldData d = AirfieldData.of(source.getLevel());
        java.util.Map<AirportTier, Long> sizes = new java.util.EnumMap<>(AirportTier.class);
        for (AirportPlanner.Plan p : d.plans().values()) { sizes.merge(p.tier(), 1L, Long::sum); }
        source.sendSuccess(() -> Component.literal(String.format("%d airports planned %s, %d towers, %d fittings, %d runways, %s old region files (planner %d)",
                d.plans().size(), sizes, d.towers().size(), d.fixtures().size(), d.runways().size(), d.oldRegions() == null ? "no list of" : String.valueOf(d.oldRegions().size()),
                d.plannerVersion())), false);
        return 1;
    }

    /** The camera spots of {@code view} (AutoShot plans that work whatever airport the seed gave). */
    static final List<String> VIEWS = List.of("aerial", "tower_far", "tower_close", "cab", "threshold", "apron", "windsock", "desk", "heli",
            "approach2km", "approach4km", "papi_on", "papi_high", "papi_low", "taxiway", "base", "stair", "hatch");

    /**
     * Puts the player at a camera spot of the nearest airport (a plan's coordinates: along and across its runway, the LOW
     * end's approach coming in along +along), looking at what the spot is for.
     */
    private static int view(CommandSourceStack source, String spot) {
        ServerLevel level = source.getLevel();
        if (source.getEntity() == null) { return 0; }
        Vec3 at = source.getPosition();
        // the regions round the player planned once (about a second each), then known
        AirportPlanner.Plan p = AirportWorldgen.planning(level) ? AirportWorldgen.nearest(level, at.x, at.z, 1) : null;
        if (p == null) { source.sendFailure(Component.literal("No airport near")); return 0; }
        double s = p.start(), l = p.lengthM(), cc = p.cross() + p.widthM() / 2.0, side = p.side(), y = p.surfaceY(), mid = s + l / 2;
        AirportLayout.TowerSite t = AirportLayout.tower(p);
        if (t == null && List.of("tower_far", "tower_close", "cab", "desk", "base", "stair", "hatch").contains(spot)) { source.sendFailure(Component.literal("This airport has no tower")); return 0; }
        // without a tower (a small airfield) the hangar stands in for it where a spot looks at it
        AirportLayout.HangarSite hs = AirportLayout.hangar(p);
        int tx = t != null ? t.x() : (hs.minX() + hs.maxX()) / 2, tz = t != null ? t.z() : (hs.minZ() + hs.maxZ()) / 2;
        double ta = p.axis() == Runway.Axis.X ? tx + 0.5 : tz + 0.5, tc = p.axis() == Runway.Axis.X ? tz + 0.5 : tx + 0.5;
        // the cab floor's top (eyes 1.62 m over it), the cab's middle
        double floor = y + 3 + (t != null ? t.courses() : 0), cab = floor + 1, glide = Math.tan(Math.toRadians(3)), papi = s + Math.min(p.tier().spec.papiMaxM(), l / 3);
        double apronC = AirportLayout.apronAcross(p) + 0.5;
        Integer taxi = AirportLayout.taxiwayAcross(p);
        double taxiC = taxi != null ? taxi + 0.5 : cc + side * (p.widthM() / 2.0 + 6);
        double[] eye, look;
        switch (spot) {
            case "aerial" -> {
                // behind the apron, over it to the runway (a large airport's apron is about 160 m out; a small one's nearer), within a render distance of 12
                double out = Math.abs(apronC - cc) + 50, k = Math.min(1, out / 210);
                eye = new double[]{mid - 60 * k, y + 70 * k, cc + side * out}; look = new double[]{mid + 30 * k, y, cc + side * (out - 150 * k)};
            }
            case "tower_far" -> { eye = new double[]{ta + 40, y + 12, tc - side * 140}; look = new double[]{ta, cab, tc + side * 3}; }
            case "tower_close" -> { eye = new double[]{ta + 14, cab + 1, tc - side * 16}; look = new double[]{ta, cab, tc + side * 3}; }
            case "cab" -> { eye = new double[]{ta - 2.5, floor + 1.62, tc + side * 5.5}; look = new double[]{ta + 1, floor + 0.9, tc - side * 2}; }
            case "threshold" -> { eye = new double[]{s - 45, y + 28, cc}; look = new double[]{s + 120, y, cc}; }
            case "apron" -> {
                // from the runway's side of the apron, along it (nearer and lower for the smaller airports')
                double along = AirportLayout.apronAlong(p) + 0.5, k = Math.max(0.5, Math.min(1, Math.abs(apronC - cc) / 123));
                eye = new double[]{along - 75 * k, y + 32 * k, apronC - side * 70 * k}; look = new double[]{along, y, apronC};
            }
            case "windsock" -> {
                AirportLayout.Thing sock = AirportLayout.things(p).get(0);
                double sa = p.axis() == Runway.Axis.X ? sock.x() + 0.5 : sock.z() + 0.5, sc = p.axis() == Runway.Axis.X ? sock.z() + 0.5 : sock.x() + 0.5;
                eye = new double[]{sa - 9, y + 4.5, sc - side * 6}; look = new double[]{sa, y + 5, sc};
            }
            case "desk" -> {
                // inside the base beside the desk (its second row, one column over), facing it
                int[] cell = AirportLayout.inside(t, 2, 1);
                double[] w = {cell[0] + 0.5, y + 1.62, cell[1] + 0.5};
                Vec3 to = new Vec3(t.x() + 0.5, y + 0.5, t.z() + 0.5);
                return teleport(source, level, w, new double[]{to.x, to.y, to.z});
            }
            case "base" -> {
                // 0.21: in the doorway, looking across the lobby at the equipment room (batteries, board, trays)
                return teleport(source, level, insideAt(t, 1, -1, y + 1.62), insideAt(t, 5, 1.5, y + 1.0));
            }
            case "stair" -> {
                // 0.21: up the stairwell, beside the column, looking up the spiral to the hatch
                int top = 2 + t.courses(), k = Math.max(2, top - 7);
                int[] cell = AirportLayout.STAIR_RING[AirportLayout.stairCell(t.courses(), k)];
                return teleport(source, level, insideAt(t, cell[0], cell[1], y + k + 1 + 1.62), insideAt(t, 3, 0, y + top + 1));
            }
            case "hatch" -> {
                // 0.21: in the cab's back left, looking at the stair's way up through the hatch and the consoles beyond
                return teleport(source, level, insideAt(t, 6.3, -2.2, floor + 1.62), insideAt(t, 3.5, 0.5, floor + 0.2));
            }
            case "heli" -> { eye = new double[]{mid, y, apronC}; look = new double[]{mid, y, cc}; }
            case "approach2km" -> { eye = new double[]{s - 2000, y + (2300) * glide + 2, cc}; look = new double[]{papi, y, cc}; }
            case "approach4km" -> { eye = new double[]{s - 4000, y + (4300) * glide + 2, cc}; look = new double[]{papi, y, cc}; }
            case "papi_on" -> { eye = new double[]{papi - 800, y + 800 * glide + 1.6, cc}; look = new double[]{papi, y, cc}; }
            case "papi_high" -> { eye = new double[]{papi - 800, y + 800 * Math.tan(Math.toRadians(4.5)) + 1.6, cc}; look = new double[]{papi, y, cc}; }
            case "papi_low" -> { eye = new double[]{papi - 800, y + 800 * Math.tan(Math.toRadians(1.8)) + 1.6, cc}; look = new double[]{papi, y, cc}; }
            case "taxiway" -> { eye = new double[]{s + 20, y + 6, taxiC}; look = new double[]{s + 200, y, taxiC}; }
            default -> { source.sendFailure(Component.literal("Spots: " + VIEWS)); return 0; }
        }
        boolean alongX = p.axis() == Runway.Axis.X;
        return teleport(source, level, alongX ? new double[]{eye[0], eye[1], eye[2]} : new double[]{eye[2], eye[1], eye[0]},
                alongX ? new double[]{look[0], look[1], look[2]} : new double[]{look[2], look[1], look[0]});
    }

    /** A point inside a tower (rows and columns as AirportLayout.inside, fractions allowed; the cells' middles), at a height. */
    private static double[] insideAt(AirportLayout.TowerSite t, double row, double col, double y) {
        int[] o = AirportLayout.inside(t, 0, 0), r = AirportLayout.inside(t, 1, 0), c = AirportLayout.inside(t, 0, 1);
        return new double[]{o[0] + 0.5 + row * (r[0] - o[0]) + col * (c[0] - o[0]), y, o[1] + 0.5 + row * (r[1] - o[1]) + col * (c[1] - o[1])};
    }

    /** Teleports the source's entity to an eye position (feet 1.62 m lower) looking at a point. */
    private static int teleport(CommandSourceStack source, ServerLevel level, double[] eye, double[] look) {
        double dx = look[0] - eye[0], dy = look[1] - eye[1], dz = look[2] - eye[2];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        source.getEntity().teleportTo(level, eye[0], eye[1] - 1.62, eye[2], java.util.Set.of(), yaw, pitch);
        return 1;
    }

    /** Opens the screen of the nearest formed tower within 64 m for the player (AutoShot: the screen is at its desk's reach, so stand there first). */
    private static int screen(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) { return 0; }
        TowerBlockEntity best = null;
        double d = 64;
        for (net.minecraft.core.BlockPos pos : AirfieldData.of(source.getLevel()).towers()) {
            double dist = Math.sqrt(pos.distToCenterSqr(player.position()));
            if (dist < d && source.getLevel().getBlockEntity(pos) instanceof TowerBlockEntity t && t.formed()) { best = t; d = dist; }
        }
        if (best == null) { source.sendFailure(Component.literal("No formed tower within 64 m")); return 0; }
        TowerMenu.open(player, best);
        return 1;
    }

    private static int build(CommandSourceStack source, AirportTier tier, int length) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        Vec3 look = source.getRotation() == null ? Vec3.ZERO : Vec3.directionFromRotation(0, source.getRotation().y);
        Direction toward = Direction.getNearest(look.x, 0, look.z);
        Runway.Axis axis = toward.getAxis() == Direction.Axis.X ? Runway.Axis.X : Runway.Axis.Z;
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z), y = (int) Math.floor(at.y);
        // the runway starts 80 m ahead of the player, its centre line through them; the taxiway on their right
        int along = axis == Runway.Axis.X ? x : z, across = axis == Runway.Axis.X ? z : x;
        int sign = toward.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        int start = sign > 0 ? along + 80 : along - 80 - length;
        int side = axis == Runway.Axis.X ? sign : -sign;
        AirportPlanner.Plan p = new AirportPlanner.Plan(AirportPlanner.VERSION, AirportPlanner.regionOf(x), AirportPlanner.regionOf(z), axis, start,
                across - tier.spec.widthM() / 2, length, y, side, tier.courses(8), level.getSeed() ^ start, tier);
        AirportLayout.Bounds b = AirportLayout.footprint(p);
        Deque<ChunkPos> chunks = new ArrayDeque<>();
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) { for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) { chunks.add(new ChunkPos(cx, cz)); } }
        BUILDS.add(new Build(level, p, chunks, source, chunks.size()));
        source.sendSuccess(() -> Component.literal("Building " + describe(p) + ": " + chunks.size() + " chunks"), true);
        return 1;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (BUILDS.isEmpty()) { return; }
        Build b = BUILDS.get(0);
        for (int i = 0; i < CHUNKS_PER_TICK && !b.left().isEmpty(); i++) { AirportFeature.slice(b.level(), b.plan(), b.left().poll()); }
        if (b.left().isEmpty()) {
            BUILDS.remove(0);
            AirportWorldgen.register(b.level(), b.plan());
            b.source().sendSuccess(() -> Component.literal("Built " + b.total() + " chunks: " + describe(b.plan())), true);
        }
    }
}
