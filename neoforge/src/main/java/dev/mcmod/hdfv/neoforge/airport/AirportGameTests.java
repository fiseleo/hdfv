package dev.mcmod.hdfv.neoforge.airport;

import com.mojang.authlib.GameProfile;
import dev.mcmod.hdfv.airport.AirfieldLighting;
import dev.mcmod.hdfv.airport.AirportLayout;
import dev.mcmod.hdfv.airport.AirportPlanner;
import dev.mcmod.hdfv.airport.AirportTier;
import dev.mcmod.hdfv.airport.Atc;
import dev.mcmod.hdfv.airport.Fixture;
import dev.mcmod.hdfv.airport.Runway;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
import dev.mcmod.hdfv.neoforge.power.BatteryBlockEntity;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.neoforge.villager.HdfvVillagers;
import dev.mcmod.hdfv.structure.Structures;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Airfields in the world (0.20): the tower forms on its pattern and dissolves when a block goes; unpowered it gives
 * only the ATIS; a generated tower comes with charged batteries that power it (and drain) and its controller villager,
 * who is on duty by its desk and not asleep; at night the lights' circuits come on and the apron floodlights light; a
 * call from someone not driving is refused and only the player in control instructs; every block is made, dropped
 * and named.
 */
@PrefixGameTestTemplate(false)
public final class AirportGameTests {
    private AirportGameTests() {}

    /** A plan whose tower stands at a point (a runway along x, the tower on its +z side facing it, north). */
    static AirportPlanner.Plan planWithTowerAt(BlockPos at, int courses) { return planWithTowerAt(at, courses, AirportTier.LARGE); }

    static AirportPlanner.Plan planWithTowerAt(BlockPos at, int courses, AirportTier tier) {
        // the layout halves with integer division, so a shift is not exact at negative coordinates: correct until it is
        int start = 0, cross = 0;
        for (int i = 0; i < 4; i++) {
            AirportLayout.TowerSite t = AirportLayout.tower(new AirportPlanner.Plan(AirportPlanner.VERSION, 0, 0, Runway.Axis.X, start, cross, 600, at.getY(), 1, courses, 0, tier));
            if (t.x() == at.getX() && t.z() == at.getZ()) { break; }
            start += at.getX() - t.x();
            cross += at.getZ() - t.z();
        }
        return new AirportPlanner.Plan(AirportPlanner.VERSION, 0, 0, Runway.Axis.X, start, cross, 600, at.getY(), 1, courses, 0, tier);
    }

    static void floor(GameTestHelper h) {
        for (int x = 0; x < 28; x++) { for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); } }
    }

    /** A tower (6 courses, console, radar, mast) placed whole facing north, its desk at (12, 1, 6). */
    static BlockPos tower(GameTestHelper h) {
        floor(h);
        BlockPos c = h.absolutePos(new BlockPos(12, 1, 6));
        HdfvMultiblocks.place(h.getLevel(), Structures.TOWER_PATTERN, c, Direction.NORTH, 6, Map.of());
        return c;
    }

    static TowerBlockEntity at(GameTestHelper h, BlockPos absolute) {
        if (!(h.getLevel().getBlockEntity(absolute) instanceof TowerBlockEntity t)) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("No tower at " + absolute + ": " + h.getLevel().getBlockState(absolute) + ", " + h.getLevel().getBlockEntity(absolute));
        }
        return t;
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport", timeoutTicks = 200)
    public static void aTowerFormsAndDissolves(GameTestHelper h) {
        BlockPos c = tower(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(at(h, c).formed(), "The tower forms"))
                .thenExecute(() -> {
                    Structures.TowerHeights th = at(h, c).heights();
                    // 2 base storeys, 6 courses, the floor: eyes 1.6 m over it; the roof 3 m higher, the radar a metre over that
                    h.assertTrue(Math.abs(th.eyeM() - 10.6) < 1e-9 && Math.abs(th.roofM() - 12) < 1e-9 && Math.abs(th.antennaM() - 13) < 1e-9, "Its heights: " + th);
                    h.assertTrue(at(h, c).hasRadar(), "It has its radar");
                    h.getLevel().setBlock(c.above(4).south(1), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                })
                .thenWaitUntil(() -> h.assertFalse(at(h, c).formed(), "A shaft block taken out: it dissolves"))
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport", timeoutTicks = 300)
    public static void unpoweredItGivesOnlyTheAtis(GameTestHelper h) {
        BlockPos c = tower(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(at(h, c).formed() && at(h, c).atis() != null, "Formed, with its ATIS"))
                .thenIdle(40)
                .thenExecute(() -> {
                    TowerBlockEntity t = at(h, c);
                    h.assertFalse(t.powered(), "No power");
                    for (double i : t.intensities()) { h.assertTrue(i == 0, "Its lights are dark"); }
                    h.assertTrue(t.result().flights().isEmpty(), "No control without power");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport_generated", timeoutTicks = 600)
    public static void aGeneratedTowerHasChargedBatteriesAndItsController(GameTestHelper h) {
        floor(h);
        BlockPos desk = h.absolutePos(new BlockPos(12, 1, 6));
        AirportPlanner.Plan p = planWithTowerAt(desk, 6);
        AirportFeature.placeTower(h.getLevel(), p);
        List<BatteryBlockEntity> batteries = new ArrayList<>();
        for (AirportLayout.Thing t : AirportLayout.things(p)) {
            if (t.kind() == AirportLayout.ThingKind.BATTERY && h.getLevel().getBlockEntity(new BlockPos(t.x(), t.y(), t.z())) instanceof BatteryBlockEntity b) { batteries.add(b); }
        }
        h.assertTrue(batteries.size() == 8, "Eight batteries: " + batteries.size());
        for (BatteryBlockEntity b : batteries) { h.assertTrue(b.storedJ() >= b.capacityJ() - 1, "Charged: " + b.storedJ()); }
        double full = batteries.stream().mapToDouble(BatteryBlockEntity::storedJ).sum();
        List<Villager> villagers = h.getLevel().getEntitiesOfClass(Villager.class, new AABB(desk).inflate(8));
        h.assertTrue(villagers.size() == 1 && villagers.get(0).getVillagerData().getProfession() == HdfvVillagers.TOWER_CONTROLLER.get(), "Its controller: " + villagers);
        h.assertFalse(villagers.get(0).getOffers().isEmpty(), "The controller trades");
        h.assertTrue(h.getLevel().getBlockState(desk.south(3).east(2)).is(Blocks.WHITE_BED) || hasBed(h, desk), "A bed in its base");
        Villager v = villagers.get(0);
        // its brain left out of it: the test sets its job site and puts it to sleep itself
        v.setNoAi(true);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(at(h, desk).formed(), "The tower forms"))
                .thenWaitUntil(() -> h.assertTrue(at(h, desk).powered(), "The batteries power it (through the floor's cable ducts)"))
                .thenWaitUntil(() -> {
                    // 0.21: its inside lamps lit, the cab's light there, the stair from the floor to the cab
                    for (AirportLayout.Thing t : AirportLayout.things(p)) {
                        BlockState s = h.getLevel().getBlockState(new BlockPos(t.x(), t.y(), t.z()));
                        if (t.kind() == AirportLayout.ThingKind.LAMP) { h.assertTrue(s.getValue(AirfieldBlocks.InteriorLamp.LIT), "A lamp lit: " + t); }
                        if (t.kind() == AirportLayout.ThingKind.STAIR) { h.assertTrue(s.is(HdfvAirports.STEEL_STAIRS.get()), "A step: " + t + " " + s); }
                        if (t.kind() == AirportLayout.ThingKind.DUCT) { h.assertTrue(s.is(HdfvAirports.CABLE_DUCT.get()), "A duct: " + t + " " + s); }
                    }
                    int[] mid = AirportLayout.inside(AirportLayout.tower(p), 3, 0);
                    h.assertTrue(h.getLevel().getBlockState(new BlockPos(mid[0], desk.getY() + 2 + 6 + 2, mid[1])).is(Blocks.LIGHT), "The cab's light");
                })
                .thenExecute(() -> v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(h.getLevel().dimension(), desk)))
                .thenWaitUntil(() -> h.assertTrue(at(h, desk).manned(), "Its controller, awake by its desk, is on duty"))
                .thenExecute(() -> {
                    double now = batteries.stream().mapToDouble(BatteryBlockEntity::storedJ).sum();
                    h.assertTrue(now < full, "The batteries drain: " + now + " of " + full);
                    // in its bed (a villager out of bed wakes at once)
                    AirportLayout.Thing bed = AirportLayout.things(p).stream().filter(t -> t.kind() == AirportLayout.ThingKind.BED).findFirst().orElseThrow();
                    v.startSleeping(new BlockPos(bed.x(), bed.y(), bed.z()));
                })
                .thenWaitUntil(() -> h.assertFalse(at(h, desk).manned(), "Asleep, nobody is on duty"))
                .thenExecute(v::stopSleeping)
                .thenSucceed();
    }

    /** 0.21: a medium airport's tower has four batteries and the same stair (Runway.distanceTo, which towers keep their runways to, is in RunwayTest). */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport_generated", timeoutTicks = 400)
    public static void aMediumTowerHasFourBatteriesThatPowerIt(GameTestHelper h) {
        floor(h);
        BlockPos desk = h.absolutePos(new BlockPos(12, 1, 6));
        AirportPlanner.Plan p = planWithTowerAt(desk, 6, AirportTier.MEDIUM);
        AirportFeature.placeTower(h.getLevel(), p);
        long batteries = AirportLayout.things(p).stream().filter(t -> t.kind() == AirportLayout.ThingKind.BATTERY
                && h.getLevel().getBlockEntity(new BlockPos(t.x(), t.y(), t.z())) instanceof BatteryBlockEntity).count();
        h.assertTrue(batteries == 4, "Four batteries: " + batteries);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(at(h, desk).formed() && at(h, desk).powered(), "Formed and powered by them"))
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport")
    public static void aSmallAirfieldHasNoTowerYetPlacesItsWindsock(GameTestHelper h) {
        floor(h);
        BlockPos at = h.absolutePos(new BlockPos(12, 1, 12));
        // a small plan shifted until its windsock stands at that point (the layout halves with integer division)
        int start = 0, cross = 0;
        AirportPlanner.Plan p = null;
        for (int i = 0; i < 4; i++) {
            p = new AirportPlanner.Plan(AirportPlanner.VERSION, 0, 0, Runway.Axis.X, start, cross, 400, at.getY(), 1, 0, 0, AirportTier.SMALL);
            AirportLayout.Thing sock = AirportLayout.things(p).get(0);
            if (sock.x() == at.getX() && sock.z() == at.getZ()) { break; }
            start += at.getX() - sock.x();
            cross += at.getZ() - sock.z();
        }
        h.assertTrue(AirportLayout.tower(p) == null, "A small airfield has no tower");
        // 0.21's first in-game look: every chunk of a small airfield failed (the tower's site was taken as there), its windsock never placed
        AirportFeature.placeTower(h.getLevel(), p);
        AirportFeature.placeNear(h.getLevel(), p, at.getX(), at.getZ(), 2);
        h.assertBlockPresent(HdfvAirports.WINDSOCK.get(), new BlockPos(12, 1, 12));
        h.succeed();
    }

    /**
     * 0.21's first stair could be neither climbed nor come down (a corner's step lay across the way in; the grating met the
     * head over the fourth step from the top): a player body, with its own collision and 0.6 m step, walks from the base's
     * floor up every step onto the cab's floor and back down. Two heights: the bottom step on a straight cell and a corner.
     */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport")
    public static void aPlayerWalksUpTheStairToTheCabAndBackDown(GameTestHelper h) {
        floor(h);
        walksTheStair(h, h.absolutePos(new BlockPos(5, 1, 6)), 6);
        walksTheStair(h, h.absolutePos(new BlockPos(19, 1, 6)), 7);
        h.succeed();
    }

    private static void walksTheStair(GameTestHelper h, BlockPos desk, int courses) {
        AirportPlanner.Plan plan = planWithTowerAt(desk, courses);
        AirportFeature.placeTower(h.getLevel(), plan);
        AirportLayout.TowerSite t = AirportLayout.tower(plan);
        int top = 2 + courses;
        // the way (x, z, the feet's height there): the cell before the bottom step on the base's floor, every step's middle
        // (on its high half), the grating the top step leads onto
        List<double[]> way = new ArrayList<>();
        way.add(middle(t, AirportLayout.STAIR_RING[Math.floorMod(AirportLayout.stairCell(courses, 0) - 1, 8)], t.y()));
        for (int k = 0; k <= top; k++) { way.add(middle(t, AirportLayout.STAIR_RING[AirportLayout.stairCell(courses, k)], t.y() + k + 1)); }
        way.add(middle(t, AirportLayout.STAIR_RING[(AirportLayout.TOP_CELL + 1) % 8], t.y() + top + 1));
        Player p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "hdfv-stair-" + courses));
        p.setPos(way.get(0)[0], way.get(0)[2], way.get(0)[1]);
        for (int i = 0; i < way.size(); i++) { reach(h, p, way.get(i), courses + " courses, up, point " + i); }
        for (int i = way.size() - 1; i >= 0; i--) { reach(h, p, way.get(i), courses + " courses, down, point " + i); }
    }

    /** A tower 0.21.0 generated (steps facing the way out, the grating over the fourth step from the top, the bed the other way) is put right when it runs. */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport_generated", timeoutTicks = 400)
    public static void aTowerGeneratedBy0210IsPutRight(GameTestHelper h) {
        floor(h);
        BlockPos desk = h.absolutePos(new BlockPos(12, 1, 6));
        AirportPlanner.Plan plan = planWithTowerAt(desk, 6, AirportTier.MEDIUM);
        AirportFeature.placeTower(h.getLevel(), plan);
        AirportLayout.TowerSite t = AirportLayout.tower(plan);
        ServerLevel level = h.getLevel();
        int top = 2 + 6;
        // laid as 0.21.0 laid it
        List<BlockPos> steps = new ArrayList<>();
        for (int k = 0; k <= top; k++) {
            int i = AirportLayout.stairCell(6, k);
            int[] here = AirportLayout.STAIR_RING[i], next = AirportLayout.STAIR_RING[(i + 1) % 8], w = AirportLayout.inside(t, here[0], here[1]);
            BlockPos at = new BlockPos(w[0], t.y() + k, w[1]);
            steps.add(at);
            level.setBlock(at, level.getBlockState(at).setValue(StairBlock.FACING,
                    Direction.from2DDataValue(AirportLayout.facing(t, next[0] - here[0], next[1] - here[1]))), Block.UPDATE_CLIENTS);
        }
        int[] g = AirportLayout.inside(t, AirportLayout.STAIR_RING[2][0], AirportLayout.STAIR_RING[2][1]);
        BlockPos grating = new BlockPos(g[0], t.y() + top, g[1]);
        level.setBlock(grating, HdfvAirports.STEEL_GRATING.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        int[] f = AirportLayout.inside(t, 3, -2), hd = AirportLayout.inside(t, 4, -2);
        BlockPos row3 = new BlockPos(f[0], t.y(), f[1]), row4 = new BlockPos(hd[0], t.y(), hd[1]);
        Direction back = Direction.from2DDataValue(AirportLayout.facing(t, 1, 0));
        BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, back);
        level.setBlock(row3, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(row4, bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (int k = 0; k <= top; k++) {
                        Direction want = Direction.from2DDataValue(AirportLayout.stairFacing(t, AirportLayout.stairCell(6, k)));
                        h.assertTrue(level.getBlockState(steps.get(k)).getValue(StairBlock.FACING) == want, "Step " + k + " faces the way in");
                    }
                    h.assertTrue(level.getBlockState(grating).isAir(), "No grating over the fourth step from the top");
                    BlockState three = level.getBlockState(row3), four = level.getBlockState(row4);
                    h.assertTrue(three.is(Blocks.WHITE_BED) && three.getValue(BedBlock.PART) == BedPart.HEAD && three.getValue(BedBlock.FACING) == back.getOpposite()
                            && four.is(Blocks.WHITE_BED) && four.getValue(BedBlock.PART) == BedPart.FOOT, "The bed turned round, its head toward the lobby: " + three + " " + four);
                })
                .thenSucceed();
    }

    private static double[] middle(AirportLayout.TowerSite t, int[] cell, double feet) {
        int[] w = AirportLayout.inside(t, cell[0], cell[1]);
        return new double[]{w[0] + 0.5, w[1] + 0.5, feet};
    }

    /** Walks a player body toward a point (its collision, its step, falling as it goes) and checks it got there, its feet at the height. */
    private static void reach(GameTestHelper h, Player p, double[] to, String what) {
        for (int i = 0; i < 100; i++) {
            double dx = to[0] - p.getX(), dz = to[1] - p.getZ(), d = Math.hypot(dx, dz);
            if (d < 0.02) { break; }
            double s = Math.min(0.12, d) / d;
            p.move(MoverType.SELF, new Vec3(dx * s, -0.25, dz * s));
        }
        for (int i = 0; i < 8; i++) { p.move(MoverType.SELF, new Vec3(0, -0.25, 0)); }
        h.assertTrue(Math.hypot(to[0] - p.getX(), to[1] - p.getZ()) < 0.05 && Math.abs(p.getY() - to[2]) < 0.01,
                String.format("The stair walked (%s): at %.2f %.2f %.2f, meant %.2f %.2f %.2f", what, p.getX(), p.getY(), p.getZ(), to[0], to[2], to[1]));
    }

    private static boolean hasBed(GameTestHelper h, BlockPos desk) {
        for (BlockPos p : BlockPos.betweenClosed(desk.offset(-4, 0, -1), desk.offset(4, 0, 8))) { if (h.getLevel().getBlockState(p).is(Blocks.WHITE_BED)) { return true; } }
        return false;
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport_night", timeoutTicks = 400)
    public static void atNightTheCircuitsAndFloodlightsComeOn(GameTestHelper h) {
        BlockPos c = tower(h);
        // a creative source touching the desk (in front of it, outside the pattern)
        h.setBlock(new BlockPos(12, 1, 5), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER));
        BlockPos flood = h.absolutePos(new BlockPos(3, 1, 3));
        h.getLevel().setBlock(flood, HdfvAirports.FLOODLIGHT.get().defaultBlockState(), Block.UPDATE_ALL);
        h.setDayTime(18000);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(at(h, c).formed() && at(h, c).powered(), "Formed and powered"))
                .thenWaitUntil(() -> h.assertTrue(at(h, c).intensities()[AirfieldLighting.RUNWAY] > 0, "The runway circuit is on at night"))
                .thenWaitUntil(() -> h.assertTrue(h.getLevel().getBlockState(flood).getValue(AirfieldBlocks.Floodlight.LIT), "The apron floodlight is lit"))
                .thenExecute(() -> h.setDayTime(6000))
                .thenWaitUntil(() -> h.assertTrue(at(h, c).intensities()[AirfieldLighting.RUNWAY] == 0, "Off by day"))
                .thenSucceed();
    }

    /** A player at a tower's screen (not logged in): opens the menu as the server would. */
    static final class Viewer extends FakePlayer {
        Viewer(ServerLevel level, String name) { super(level, new GameProfile(UUID.randomUUID(), name)); }

        @Override public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extra) {
            AbstractContainerMenu m = provider == null ? null : provider.createMenu(1, getInventory(), this);
            if (m == null) { return OptionalInt.empty(); }
            if (extra != null) { extra.accept(new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess())); }
            containerMenu = m;
            return OptionalInt.of(1);
        }
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport", timeoutTicks = 200)
    public static void onlyThePlayerInControlInstructsAndStrangersCannotCall(GameTestHelper h) {
        BlockPos c = tower(h);
        Viewer a = new Viewer(h.getLevel(), "hdfv-tower-a"), b = new Viewer(h.getLevel(), "hdfv-tower-b");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(at(h, c).formed(), "Formed"))
                .thenExecute(() -> {
                    TowerBlockEntity t = at(h, c);
                    for (Viewer v : List.of(a, b)) { v.moveTo(c.getX() + 0.5, c.getY(), c.getZ() - 1.5); TowerMenu.open(v, t); }
                    h.assertTrue(a.containerMenu instanceof TowerMenu m && m.pos.equals(c), "The tower's screen opens");
                    UUID nobody = new UUID(0, 0);
                    h.assertTrue(AirportNetwork.apply(a, new AirportNetwork.CommandPayload(c, new AirportNetwork.Command(AirportNetwork.CommandKind.TAKE, nobody, 0))), "Taken");
                    h.assertTrue(a.getUUID().equals(t.holder()), "A holds the tower");
                    h.assertTrue(t.command(b, new AirportNetwork.Command(AirportNetwork.CommandKind.TAKE, nobody, 0)) != null, "B cannot take it from A");
                    h.assertTrue(t.command(b, new AirportNetwork.Command(AirportNetwork.CommandKind.INSTRUCT, UUID.randomUUID(), Atc.Instruction.CLEARED_LAND.ordinal())) != null,
                            "B cannot instruct");
                    h.assertTrue(t.command(a, new AirportNetwork.Command(AirportNetwork.CommandKind.INSTRUCT, UUID.randomUUID(), Atc.Instruction.CLEARED_LAND.ordinal())) == null,
                            "A can");
                    // a command without the screen open, and a pilot's call from someone not driving an aircraft, do nothing
                    Viewer stranger = new Viewer(h.getLevel(), "hdfv-tower-c");
                    h.assertFalse(AirportNetwork.apply(stranger, new AirportNetwork.CommandPayload(c, new AirportNetwork.Command(AirportNetwork.CommandKind.RELEASE, nobody, 0))),
                            "No screen, no command");
                    h.assertFalse(AirportNetwork.accept(h.getLevel(), stranger, new AirportNetwork.CallPayload(stranger.getId(), Atc.Request.CALL, false)), "Not a pilot: refused");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_airport")
    public static void theAirfieldBlocksAreMadeDroppedAndNamed(GameTestHelper h) throws java.io.IOException {
        floor(h);
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos p = new BlockPos(4, 1, 4);
        List<String> blocks = new ArrayList<>(HdfvAirports.ids());
        blocks.add("asphalt");
        for (String id : blocks) {
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            h.assertTrue(b != Blocks.AIR, "A block " + id);
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.setBlock(p, b);
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), h.getLevel().getBlockEntity(h.absolutePos(p)));
            h.assertTrue(drops.stream().anyMatch(s -> s.is(b.asItem())), id + " drops itself: " + drops);
            h.setBlock(p, Blocks.AIR);
        }
        for (String id : HdfvAirports.itemIds()) { h.assertTrue(recipes.contains(id), "A recipe for the " + id); }
        for (Fixture.Kind k : Fixture.Kind.values()) { h.assertTrue(blocks.contains(k.id()), "A fitting " + k); }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : blocks) { h.assertTrue(lang.has("block.hdfv." + id), file + " lacks block " + id); }
                for (String id : HdfvAirports.itemIds()) { h.assertTrue(lang.has("item.hdfv." + id), file + " lacks " + id); }
                for (Atc.Instruction i : Atc.Instruction.values()) { h.assertTrue(lang.has("hdfv.atc.instruction." + i.name().toLowerCase(java.util.Locale.ROOT)), file + " lacks " + i); }
                for (Atc.Warning w : Atc.Warning.values()) { h.assertTrue(lang.has("hdfv.atc.warning." + w.name().toLowerCase(java.util.Locale.ROOT)), file + " lacks " + w); }
                for (Atc.Phase ph : Atc.Phase.values()) { h.assertTrue(lang.has("hdfv.atc.phase." + ph.name().toLowerCase(java.util.Locale.ROOT)), file + " lacks " + ph); }
                for (String k : List.of("entity.minecraft.villager.hdfv.tower_controller", "filled_map.hdfv.airport", "key.hdfv.atc_call", "key.hdfv.atc_cancel",
                        "key.hdfv.atc_atis", "hdfv.configuration.aviation", "hdfv.configuration.generateAirports", "hdfv.atc.atis", "hdfv.tower.take")) {
                    h.assertTrue(lang.has(k), file + " lacks " + k);
                }
            }
        }
        h.succeed();
    }
}
