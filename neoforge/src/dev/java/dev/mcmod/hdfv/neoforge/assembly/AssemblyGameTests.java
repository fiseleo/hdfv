package dev.mcmod.hdfv.neoforge.assembly;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.hdfv.assembly.Room;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.part.SampleBlueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Assembly rooms and the vehicles built in them, in a running server (0.7). */
@PrefixGameTestTemplate(false)
public final class AssemblyGameTests {
    private AssemblyGameTests() {}

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 200)
    public static void anUnassembledVehicleNeitherRunsNorCarriesNorObeys(GameTestHelper h) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        HdfvVehicle v = HdfvAssembly.VEHICLE.get().create(h.getLevel());
        h.assertTrue(v != null, "A vehicle");
        v.setBlueprint(SampleBlueprints.launch(), new Vec3d(o.getX() + 12, o.getY() + 8, o.getZ() + 8), Orientation.IDENTITY);
        v.setBuilt(0.5f);
        h.getLevel().addFreshEntity(v);
        // fake players never ride (NeoForge's FakePlayer refuses), so an armor stand takes the seat
        ArmorStand rider = EntityType.ARMOR_STAND.create(h.getLevel());
        rider.moveTo(o.getX() + 12, o.getY() + 8, o.getZ() + 10);
        h.getLevel().addFreshEntity(rider);
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        double y0 = v.getY();
        h.startSequence()
                .thenExecuteAfter(20, () -> {
                    v.interact(p, InteractionHand.MAIN_HAND);
                    h.assertTrue(!rider.startRiding(v) && !v.canControl(p), "Nobody boards or drives a vehicle half built");
                    h.assertTrue(Math.abs(v.getY() - y0) < 1e-6, "It is not simulated: it hangs where it is built (" + (v.getY() - y0) + ")");
                    v.setBuilt(1);
                })
                .thenWaitUntil(() -> h.assertTrue(v.getY() < y0 - 0.5, "Assembled, it falls: " + (v.getY() - y0)))
                .thenExecute(() -> {
                    h.assertTrue(rider.startRiding(v), "Assembled, it takes a rider");
                    rider.stopRiding();
                    rider.discard();
                    v.discard();
                })
                .thenSucceed();
    }

    /**
     * A car factory 8 m long, 5 wide and 4 high inside (x 2-9, z 2-6, from y 3), on the assembly floor (y 2), with a
     * crane rail down the middle of its roof, a 3 by 3 roller shutter at floor level in its +x wall, a welding station
     * and its controller in the -x wall.
     */
    static BlockPos carFactory(GameTestHelper h) {
        fill(h, 0, 1, 0, 15, 9, 10, Blocks.AIR.defaultBlockState());
        fill(h, 0, 1, 0, 15, 2, 10, Blocks.STONE.defaultBlockState());
        fill(h, 1, 2, 1, 10, 7, 7, Blocks.STONE.defaultBlockState());
        fill(h, 2, 3, 2, 9, 6, 6, Blocks.AIR.defaultBlockState());
        fill(h, 2, 2, 2, 9, 2, 6, HdfvAssembly.ASSEMBLY_FLOOR.get().defaultBlockState());
        fill(h, 2, 7, 4, 9, 7, 4, HdfvAssembly.CRANE_RAIL.get().defaultBlockState());
        fill(h, 10, 3, 3, 10, 5, 5, HdfvAssembly.DOORS.get("roller_shutter").get().defaultBlockState());
        h.setBlock(new BlockPos(2, 3, 2), HdfvMachines.MACHINES.get(Station.WELDING_STATION).get());
        BlockPos c = new BlockPos(1, 3, 4);
        h.setBlock(c, HdfvAssembly.CONTROLLERS.get(Room.CAR_FACTORY).get().defaultBlockState().setValue(RoomControllerBlock.FACING, Direction.EAST));
        return c;
    }

    static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) { for (int y = y0; y <= y1; y++) { for (int z = z0; z <= z1; z++) { h.setBlock(new BlockPos(x, y, z), s); } } }
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aCarFactoryIsFoundAndItsFaultsAreTold(GameTestHelper h) {
        BlockPos c = carFactory(h);
        RoomControllerBlockEntity be = (RoomControllerBlockEntity) h.getBlockEntity(c);
        var scan = be.scan();
        h.assertTrue(scan.closed() && scan.lengthX() == 8 && scan.widthZ() == 5 && scan.heightY() == 4, "The room: " + scan);
        h.assertTrue(scan.cells() == 8 * 5 * 4 - 1, "Its air, less the welding station: " + scan.cells());
        h.assertTrue(scan.doors().size() == 1 && scan.doors().get(0).widthM() == 3 && scan.doors().get(0).heightM() == 3, "Its shutter: " + scan.doors());
        h.assertTrue(scan.craneCells() == 8 && scan.weldingStations() == 1, "Crane and welding station: " + scan);
        h.assertTrue(be.problemKeys().isEmpty(), "Nothing wrong: " + be.problemKeys());
        // the shutter opens and the room is still a room
        h.assertTrue(be.toggleDoor(), "The door opens");
        h.assertTrue(h.getBlockState(new BlockPos(10, 4, 4)).getValue(RoomDoorBlock.OPEN), "Open");
        be.scan();
        h.assertTrue(be.problemKeys().isEmpty(), "An open door is still the room's door: " + be.problemKeys());
        be.toggleDoor();
        // a hole in the wall
        h.setBlock(new BlockPos(5, 4, 1), Blocks.AIR);
        be.scan();
        h.assertTrue(be.problemKeys().equals(List.of("open")), "A hole: " + be.problemKeys());
        h.setBlock(new BlockPos(5, 4, 1), Blocks.STONE);
        // bare ground in the floor
        h.setBlock(new BlockPos(5, 2, 4), Blocks.DIRT);
        be.scan();
        h.assertTrue(be.problemKeys().equals(List.of("floor")), "No floor: " + be.problemKeys());
        // the controller and room blocks drop themselves
        for (Block b : List.of(HdfvAssembly.CONTROLLERS.get(Room.CAR_FACTORY).get(), HdfvAssembly.ASSEMBLY_FLOOR.get(), HdfvAssembly.CRANE_RAIL.get())) {
            var drops = Block.getDrops(b.defaultBlockState(), h.getLevel(), h.absolutePos(c), null);
            h.assertTrue(drops.size() == 1 && drops.get(0).is(b.asItem()), b + " drops itself: " + drops);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aCarIsLaidOutCheckedSavedAndLoaded(GameTestHelper h) {
        BlockPos c = carFactory(h);
        RoomControllerBlockEntity be = (RoomControllerBlockEntity) h.getBlockEntity(c);
        List<dev.mcmod.aerocore.api.vehicle.PartDesign> parts = List.of(dev.mcmod.hdfv.part.body.SampleBodies.sedan(),
                dev.mcmod.hdfv.part.engine.SampleEngines.inlineFour(), dev.mcmod.hdfv.part.drive.SampleGearboxes.sixSpeedAutomatic(),
                dev.mcmod.hdfv.part.wheel.SampleWheels.sedanFront(), dev.mcmod.hdfv.part.wheel.SampleWheels.sedanRear());
        for (int i = 0; i < parts.size(); i++) {
            be.drawings.setItem(i, dev.mcmod.hdfv.neoforge.design.Blueprints.of(parts.get(i)));
            var refused = be.apply(new RoomControllerBlockEntity.Edit(RoomControllerBlockEntity.Op.ADD, i, "", "", "", Vec3d.ZERO, 0, 0, 0));
            h.assertTrue(refused == null, "Added " + parts.get(i).name() + ": " + refused);
        }
        var layout = be.layout();
        h.assertTrue(layout.parts().size() == 5 && "engine_bay".equals(layout.part("piston_engine_1").mount().attachPoint())
                && "rear_axle".equals(layout.part("axle_2").mount().attachPoint()), "Parts in their places: " + layout.parts().stream().map(e -> e.id() + "@" + e.mount().attachPoint()).toList());
        h.assertTrue(be.plan() != null && be.plan().room() == Room.CAR_FACTORY, "A car's plan: " + be.issues());
        h.assertTrue(be.problemKeys().isEmpty(), "The sedan fits the car factory: " + be.problemKeys());
        // the body holds the others
        h.assertTrue(be.apply(new RoomControllerBlockEntity.Edit(RoomControllerBlockEntity.Op.REMOVE, 0, "body_1", "", "", Vec3d.ZERO, 0, 0, 0)) != null, "The body stays");
        // saved onto a vehicle blueprint, loaded by another controller
        be.drawings.setItem(RoomControllerBlockEntity.VEHICLE_DRAWING, new net.minecraft.world.item.ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get()));
        h.assertTrue(be.apply(RoomControllerBlockEntity.Edit.of(RoomControllerBlockEntity.Op.SAVE)) == null, "Saved");
        var saved = be.drawings.getItem(RoomControllerBlockEntity.VEHICLE_DRAWING).copy();
        BlockPos c2 = new BlockPos(14, 3, 9);
        h.setBlock(c2, HdfvAssembly.CONTROLLERS.get(Room.CAR_FACTORY).get());
        RoomControllerBlockEntity other = (RoomControllerBlockEntity) h.getBlockEntity(c2);
        other.drawings.setItem(RoomControllerBlockEntity.VEHICLE_DRAWING, saved);
        h.assertTrue(other.apply(RoomControllerBlockEntity.Edit.of(RoomControllerBlockEntity.Op.LOAD)) == null, "Loaded");
        h.assertTrue(other.layoutJson().equals(be.layoutJson()), "The same layout");
        // a seaplane is not built in a car factory
        var sea = new net.minecraft.world.item.ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        sea.set(HdfvAssembly.VEHICLE_LAYOUT.get(), dev.mcmod.hdfv.assembly.SampleLayouts.seaplane().toJsonText());
        be.drawings.setItem(RoomControllerBlockEntity.VEHICLE_DRAWING, sea);
        be.apply(RoomControllerBlockEntity.Edit.of(RoomControllerBlockEntity.Op.LOAD));
        h.assertTrue(be.problemKeys().contains("wrong_room"), "A seaplane in a car factory: " + be.problemKeys());
        h.succeed();
    }

    /**
     * 0.14: an engine that does not fit under a body's hood (the generator diesel in the sedan's bay) and seat parts in
     * the body's own seats stop a car factory, its page saying which part sticks out through which side and by how much.
     */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aTooBigEngineIsRefused(GameTestHelper h) {
        BlockPos c = carFactory(h);
        RoomControllerBlockEntity be = (RoomControllerBlockEntity) h.getBlockEntity(c);
        List<dev.mcmod.aerocore.api.vehicle.VehicleBlueprint.Entry> parts = new java.util.ArrayList<>(dev.mcmod.hdfv.drive.TestCars.tooBigEngine().parts());
        parts.add(new dev.mcmod.aerocore.api.vehicle.VehicleBlueprint.Entry("jump_seat", new dev.mcmod.hdfv.part.SeatDesign("jump seat", 10),
                dev.mcmod.aerocore.api.vehicle.Mount.on("body", "m3_bottom", dev.mcmod.aerocore.api.vehicle.Mount.at(new Vec3d(0.42, 0.35, -0.39)))));
        var blueprint = new net.minecraft.world.item.ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        blueprint.set(HdfvAssembly.VEHICLE_LAYOUT.get(), new dev.mcmod.aerocore.api.vehicle.VehicleBlueprint("big engine", parts, List.of()).toJsonText());
        be.drawings.setItem(RoomControllerBlockEntity.VEHICLE_DRAWING, blueprint);
        h.assertTrue(be.apply(RoomControllerBlockEntity.Edit.of(RoomControllerBlockEntity.Op.LOAD)) == null, "Loaded");
        h.assertTrue(be.plan() != null, "A car's plan: " + be.issues());
        h.assertTrue(be.problemKeys().equals(List.of("part_does_not_fit", "seat_clash")), "Refused: " + be.problemKeys());
        var refused = be.start();
        h.assertTrue(refused != null && be.building == null, "Not started");
        // the page's message: the part, the side in words, the centimetres
        net.minecraft.nbt.CompoundTag misfit = (net.minecraft.nbt.CompoundTag) be.report().getList("Problems", net.minecraft.nbt.Tag.TAG_COMPOUND).get(0);
        var message = RoomControllerBlockEntity.problem(misfit);
        var contents = (net.minecraft.network.chat.contents.TranslatableContents) message.getContents();
        h.assertTrue(contents.getKey().equals("hdfv.room.problem.part_does_not_fit") && contents.getArgs()[0].equals("engine")
                && contents.getArgs()[1] instanceof net.minecraft.network.chat.Component side
                && ((net.minecraft.network.chat.contents.TranslatableContents) side.getContents()).getKey().startsWith("hdfv.room.side.")
                && Integer.parseInt(String.valueOf(contents.getArgs()[2])) > 0, "The message: " + message.getString());
        // the sample sedan as it is now goes
        var sedan = new net.minecraft.world.item.ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        sedan.set(HdfvAssembly.VEHICLE_LAYOUT.get(), dev.mcmod.hdfv.assembly.SampleLayouts.sedan().toJsonText());
        be.drawings.setItem(RoomControllerBlockEntity.VEHICLE_DRAWING, sedan);
        be.apply(RoomControllerBlockEntity.Edit.of(RoomControllerBlockEntity.Op.LOAD));
        h.assertTrue(be.problemKeys().isEmpty(), "The sample sedan fits: " + be.problemKeys());
        h.succeed();
    }

    /**
     * A shipyard 9 m long, 5 wide and 4 high inside (x 2-10, z 2-6, from y 4) on a slipway (y 3), a crane rail, a 3 by 3
     * dock gate in its +x wall onto a basin 3 m deep (x 12-22), two welding stations and two chests inside, its
     * controller in the -x wall.
     */
    static BlockPos shipyard(GameTestHelper h) {
        fill(h, 0, 0, 0, 23, 9, 15, Blocks.AIR.defaultBlockState());
        fill(h, 0, 0, 0, 23, 0, 15, Blocks.STONE.defaultBlockState());
        fill(h, 11, 1, 0, 23, 3, 10, Blocks.STONE.defaultBlockState());
        fill(h, 12, 1, 1, 22, 3, 9, Blocks.WATER.defaultBlockState());
        fill(h, 1, 1, 1, 11, 8, 7, Blocks.STONE.defaultBlockState());
        fill(h, 2, 4, 2, 10, 7, 6, Blocks.AIR.defaultBlockState());
        fill(h, 2, 3, 2, 10, 3, 6, HdfvAssembly.SLIPWAY.get().defaultBlockState());
        fill(h, 2, 8, 4, 10, 8, 4, HdfvAssembly.CRANE_RAIL.get().defaultBlockState());
        fill(h, 11, 4, 3, 11, 6, 5, HdfvAssembly.DOORS.get("dock_gate").get().defaultBlockState());
        h.setBlock(new BlockPos(2, 4, 2), HdfvMachines.MACHINES.get(Station.WELDING_STATION).get());
        h.setBlock(new BlockPos(2, 4, 6), HdfvMachines.MACHINES.get(Station.WELDING_STATION).get());
        h.setBlock(new BlockPos(10, 4, 2), Blocks.CHEST);
        h.setBlock(new BlockPos(10, 4, 6), Blocks.CHEST);
        BlockPos c = new BlockPos(1, 4, 4);
        h.setBlock(c, HdfvAssembly.CONTROLLERS.get(Room.SHIPYARD).get().defaultBlockState().setValue(RoomControllerBlock.FACING, Direction.EAST));
        return c;
    }

    /** Fills chests with everything a plan needs. */
    static void stock(GameTestHelper h, dev.mcmod.hdfv.assembly.AssemblyPlan p, BlockPos... chests) {
        List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>();
        for (var n : p.parts()) { for (int i = 0; i < n.count(); i++) { items.add(dev.mcmod.hdfv.neoforge.machine.Workpieces.part(n.design())); } }
        for (var piece : p.pieces()) {
            int left = piece.workpiece().count();
            while (left > 0) {
                var s = Staging.workpieceOf(piece.design(), piece.workpiece());
                s.setCount(Math.min(left, s.getMaxStackSize()));
                left -= s.getCount();
                items.add(s);
            }
        }
        p.stockKg().forEach((a, kg) -> {
            int n = (int) Math.ceil(kg / 10 - 1e-9);
            while (n > 0) { int k = Math.min(n, 64); items.add(dev.mcmod.hdfv.neoforge.material.Stock.of(a, k)); n -= k; }
        });
        int slot = 0, chest = 0;
        for (var s : items) {
            var box = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(chests[chest]);
            box.setItem(slot++, s);
            if (slot == box.getContainerSize()) { slot = 0; chest++; }
        }
    }

    static void tickStations(GameTestHelper h, BlockPos... stations) {
        for (BlockPos s : stations) {
            var m = (dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity) h.getBlockEntity(s);
            for (int i = 0; i < 20; i++) { dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity.tick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m); }
        }
    }

    static net.minecraft.world.entity.npc.Villager welder(GameTestHelper h, BlockPos at) {
        var v = h.spawn(EntityType.VILLAGER, at);
        v.setVillagerData(v.getVillagerData().setProfession(dev.mcmod.hdfv.neoforge.villager.HdfvVillagers.WELDER.get()));
        v.setNoAi(true);
        return v;
    }

    // Its own batch: it works through the whole build in one tick, and its launch floats in the basin.
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 400, batch = "hdfv_shipyard")
    public static void aLaunchIsBuiltInAShipyardByWeldersAndGoesDownToTheWater(GameTestHelper h) {
        BlockPos c = shipyard(h);
        RoomControllerBlockEntity be = (RoomControllerBlockEntity) h.getBlockEntity(c);
        var blueprint = new net.minecraft.world.item.ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        blueprint.set(HdfvAssembly.VEHICLE_LAYOUT.get(), dev.mcmod.hdfv.assembly.SampleLayouts.launch().toJsonText());
        be.drawings.setItem(RoomControllerBlockEntity.VEHICLE_DRAWING, blueprint);
        h.assertTrue(be.apply(RoomControllerBlockEntity.Edit.of(RoomControllerBlockEntity.Op.LOAD)) == null, "Loaded the launch");
        var plan = be.plan();
        h.assertTrue(plan != null && plan.room() == Room.SHIPYARD, "A boat's plan: " + be.issues());
        // empty chests: the build cannot start
        var refused = be.start();
        h.assertTrue(refused != null && be.building == null, "Nothing to build from");
        stock(h, plan, new BlockPos(10, 4, 2), new BlockPos(10, 4, 6));
        refused = be.start();
        h.assertTrue(refused == null, "Started: " + (refused == null ? "" : refused.getString()) + " " + be.problemKeys());
        HdfvVehicle v = be.vehicle();
        h.assertTrue(v != null && !v.assembled() && be.scan.holds(v.getX(), v.getY() + 0.5, v.getZ()), "The launch stands in the shipyard, unbuilt");
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(new BlockPos(10, 4, 2));
        for (int i = 0; i < chest.getContainerSize(); i++) {
            h.assertTrue(!chest.getItem(i).is(dev.mcmod.hdfv.neoforge.machine.HdfvMachines.PART.get()), "The parts were taken");
        }
        // nobody works: nothing happens
        for (int i = 0; i < 100; i++) { be.tick(); }
        h.assertTrue(be.workDone == 0 && be.crew == 0, "No crew, no work: " + be.workDone);
        // a welder at one station, then a second at the other: twice the pace
        welder(h, new BlockPos(3, 4, 2));
        tickStations(h, new BlockPos(2, 4, 2), new BlockPos(2, 4, 6));
        long before = be.workDone;
        for (int i = 0; i < 1000; i++) { be.tick(); }
        long one = be.workDone - before;
        welder(h, new BlockPos(3, 4, 6));
        tickStations(h, new BlockPos(2, 4, 2), new BlockPos(2, 4, 6));
        before = be.workDone;
        for (int i = 0; i < 1000; i++) { be.tick(); }
        long two = be.workDone - before;
        h.assertTrue(one > 900 && two > 1.8 * one, "One welder " + one + ", two " + two);
        for (int i = 0; i < 5_000_000 && !be.finished(); i++) { be.tick(); }
        h.assertTrue(be.finished() && v.assembled(), "Assembled after " + plan.laborHours() + " h of work and " + plan.cureHours() + " h of cure");
        h.assertTrue(be.launch() == null, "Rolled out");
        h.assertTrue(h.getBlockState(new BlockPos(11, 4, 4)).getValue(RoomDoorBlock.OPEN), "The gate is opening");
        h.assertTrue(be.launching && be.scan.holds(v.getX(), v.getY() + 0.5, v.getZ()), "It waits inside for the gate (0.8)");
        double gate = h.absolutePos(new BlockPos(11, 4, 4)).getX();
        h.succeedWhen(() -> {
            h.assertTrue(v.getX() > gate + 1, "Out of the gate: " + (v.getX() - gate));
            double surface = h.absolutePos(new BlockPos(0, 4, 0)).getY();
            h.assertTrue(Math.abs(v.getY() - surface) < 1.5 && v.vehicleState() != null, "Afloat: " + (v.getY() - surface));
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 80)
    public static void aDoorLetsThroughOnlyOnceItHasFinishedOpening(GameTestHelper h) {
        BlockPos lo = new BlockPos(3, 2, 3), hi = new BlockPos(3, 3, 3);
        h.setBlock(lo, HdfvAssembly.DOORS.get("roller_shutter").get());
        h.setBlock(hi, HdfvAssembly.DOORS.get("roller_shutter").get());
        RoomDoorBlock.setOpen(h.getLevel(), h.absolutePos(lo), true);
        h.assertTrue(h.getBlockState(lo).getValue(RoomDoorBlock.OPEN) && h.getBlockState(lo).getValue(RoomDoorBlock.PHASE) == 0, "Starting to open");
        // 0.19: the players near hear its motor and chain for its four steps
        var told = dev.mcmod.hdfv.neoforge.sound.SoundSync.told(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(lo)));
        h.assertTrue(told.stream().anyMatch(p -> p.event() == dev.mcmod.hdfv.sound.MachineSound.Event.DOOR && Math.abs(p.argument() - 4 * RoomDoorBlock.STEP_TICKS / 20.0) < 1e-6),
                "The door's sound is told: " + told);
        h.assertFalse(h.getBlockState(lo).getCollisionShape(h.getLevel(), h.absolutePos(lo)).isEmpty(), "Shut until it is open");
        h.runAtTickTime(2 * RoomDoorBlock.STEP_TICKS + 2, () -> {
            int phase = h.getBlockState(hi).getValue(RoomDoorBlock.PHASE);
            h.assertTrue(phase == 2, "Half way: " + phase);
            h.assertFalse(h.getBlockState(hi).getCollisionShape(h.getLevel(), h.absolutePos(hi)).isEmpty(), "Still in the way half open");
            h.assertFalse(RoomDoorBlock.fullyOpen(h.getLevel(), h.absolutePos(lo)), "Not open yet");
        });
        h.runAtTickTime(4 * RoomDoorBlock.STEP_TICKS + 4, () -> {
            h.assertTrue(RoomDoorBlock.fullyOpen(h.getLevel(), h.absolutePos(lo)), "Open");
            h.assertTrue(h.getBlockState(lo).getCollisionShape(h.getLevel(), h.absolutePos(lo)).isEmpty(), "Through it now");
            RoomDoorBlock.setOpen(h.getLevel(), h.absolutePos(hi), false);
        });
        h.runAtTickTime(9 * RoomDoorBlock.STEP_TICKS + 4, () -> {
            h.assertTrue(h.getBlockState(lo).getValue(RoomDoorBlock.PHASE) == 0, "Shut again");
            h.succeed();
        });
    }

    /** A 3 by 3 roller shutter is one curtain: only its top row carries the coil, and the foot rises through the rows into it. */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 80)
    public static void aShutterRollsUpIntoOneCoilAtItsTop(GameTestHelper h) {
        fill(h, 3, 2, 3, 3, 4, 5, HdfvAssembly.DOORS.get("roller_shutter").get().defaultBlockState());
        BlockPos lo = new BlockPos(3, 2, 4), mid = new BlockPos(3, 3, 4), hi = new BlockPos(3, 4, 4);
        for (int z = 3; z <= 5; z++) {
            for (int y = 2; y <= 4; y++) {
                BlockState s = h.getBlockState(new BlockPos(3, y, z));
                h.assertTrue(s.getValue(RoomDoorBlock.TOP) == (y == 4), "Only the top row has the coil: " + y + " " + s);
                h.assertTrue(s.getValue(RoomDoorBlock.AXIS) == Direction.Axis.Z, "It spans z: " + s);
            }
        }
        h.assertTrue(edges(h, lo, mid, hi).equals(List.of(1, 0, 0)), "Shut, the foot on the floor: " + edges(h, lo, mid, hi));
        RoomDoorBlock.setOpen(h.getLevel(), h.absolutePos(mid), true);
        h.runAtTickTime(2 * RoomDoorBlock.STEP_TICKS + 2, () -> {
            h.assertTrue(h.getBlockState(lo).getValue(RoomDoorBlock.PHASE) == 2, "Half way");
            h.assertTrue(edges(h, lo, mid, hi).equals(List.of(5, 2, 0)), "The foot in the middle row: " + edges(h, lo, mid, hi));
            h.assertTrue(h.getBlockState(lo).getShape(h.getLevel(), h.absolutePos(lo)).isEmpty(), "Nothing left in the bottom row");
        });
        h.runAtTickTime(4 * RoomDoorBlock.STEP_TICKS + 4, () -> {
            h.assertTrue(RoomDoorBlock.fullyOpen(h.getLevel(), h.absolutePos(lo)), "Open");
            h.assertTrue(edges(h, lo, mid, hi).equals(List.of(5, 5, 3)), "Rolled up under the coil: " + edges(h, lo, mid, hi));
            h.assertFalse(h.getBlockState(hi).getShape(h.getLevel(), h.absolutePos(hi)).isEmpty(), "The coil stays");
            h.assertTrue(h.getBlockState(hi).getCollisionShape(h.getLevel(), h.absolutePos(hi)).isEmpty(), "but lets through");
            RoomDoorBlock.setOpen(h.getLevel(), h.absolutePos(hi), false);
        });
        h.runAtTickTime(9 * RoomDoorBlock.STEP_TICKS + 4, () -> {
            h.assertTrue(edges(h, lo, mid, hi).equals(List.of(1, 0, 0)), "Shut again: " + edges(h, lo, mid, hi));
            h.succeed();
        });
    }

    static List<Integer> edges(GameTestHelper h, BlockPos... at) {
        List<Integer> out = new java.util.ArrayList<>();
        for (BlockPos p : at) { out.add(h.getBlockState(p).getValue(RoomDoorBlock.EDGE)); }
        return out;
    }

    // Its own batch: it stocks and empties chests and builds a car in one tick.
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_car_factory")
    public static void aCarFactoryListsWhatIsMissingAndTakesACarApart(GameTestHelper h) {
        BlockPos c = carFactory(h);
        h.setBlock(new BlockPos(9, 3, 6), Blocks.CHEST);
        h.setBlock(new BlockPos(9, 3, 2), Blocks.CHEST);
        RoomControllerBlockEntity be = (RoomControllerBlockEntity) h.getBlockEntity(c);
        var blueprint = new net.minecraft.world.item.ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        blueprint.set(HdfvAssembly.VEHICLE_LAYOUT.get(), dev.mcmod.hdfv.assembly.SampleLayouts.sedan().toJsonText());
        be.drawings.setItem(RoomControllerBlockEntity.VEHICLE_DRAWING, blueprint);
        be.apply(RoomControllerBlockEntity.Edit.of(RoomControllerBlockEntity.Op.LOAD));
        var plan = be.plan();
        h.assertTrue(plan != null, "A sedan: " + be.issues());
        var refused = be.start();
        String engineName = dev.mcmod.hdfv.part.engine.SampleEngines.inlineFour().name();
        h.assertTrue(refused != null && refused.getString().contains(engineName), "The missing engine is named: " + (refused == null ? "" : refused.getString()));
        stock(h, plan, new BlockPos(9, 3, 6), new BlockPos(9, 3, 2));
        h.assertTrue(be.start() == null, "Started");
        HdfvVehicle v = be.vehicle();
        h.assertTrue(v != null && v.ready(), "The sedan stands in the factory, its tables not built until it is assembled");
        h.assertTrue(v.tankCount() > 0, "The sedan has tanks");
        for (int k = 0; k < v.tankCount(); k++) { h.assertTrue(v.tankKg(k) == 0 && v.tankFuel(k).isEmpty(), "Its tanks start empty (0.8): " + k + ": " + v.tankKg(k)); }
        h.assertTrue(be.dismantle() == null && v.isRemoved() && be.building == null, "Taken apart");
        boolean engine = false;
        for (BlockPos p : List.of(new BlockPos(9, 3, 6), new BlockPos(9, 3, 2))) {
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(p);
            for (int i = 0; i < chest.getContainerSize(); i++) {
                var d = dev.mcmod.hdfv.neoforge.machine.Workpieces.partDesign(chest.getItem(i));
                engine |= d != null && d.type().equals("hdfv:piston_engine");
            }
        }
        h.assertTrue(engine, "The engine is back in a chest");
        h.succeed();
    }

    // Its own batch: a built sedan rolls out of a car factory (0.14: rolling out filled the emptied tanks again).
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 300, batch = "hdfv_car_rollout")
    public static void aBuiltCarRollsOutWithTheFuelItWasGiven(GameTestHelper h) {
        BlockPos c = carFactory(h);
        h.setBlock(new BlockPos(9, 3, 6), Blocks.CHEST);
        h.setBlock(new BlockPos(9, 3, 2), Blocks.CHEST);
        dev.mcmod.hdfv.part.SampleBlueprints.sedan().compile().warmUp();   // its engine's map now, not on the shared builder
        RoomControllerBlockEntity be = (RoomControllerBlockEntity) h.getBlockEntity(c);
        var blueprint = new net.minecraft.world.item.ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        blueprint.set(HdfvAssembly.VEHICLE_LAYOUT.get(), dev.mcmod.hdfv.assembly.SampleLayouts.sedan().toJsonText());
        be.drawings.setItem(RoomControllerBlockEntity.VEHICLE_DRAWING, blueprint);
        be.apply(RoomControllerBlockEntity.Edit.of(RoomControllerBlockEntity.Op.LOAD));
        stock(h, be.plan(), new BlockPos(9, 3, 6), new BlockPos(9, 3, 2));
        h.assertTrue(be.start() == null, "Started");
        HdfvVehicle v = be.vehicle();
        // the work done at once: the next tick finds it finished
        be.workDone = be.workTotal;
        be.cureDone = be.cureTotal;
        be.tick();
        h.assertTrue(be.finished() && v.assembled(), "Built");
        h.assertTrue(v.tankCount() > 0, "The sedan has tanks");
        double kg = 5;
        h.assertTrue(v.setTank(0, "gasoline", kg, dev.mcmod.hdfv.neoforge.fuel.Fuelling.density("gasoline")) == kg, "Fuelled in the factory");
        h.assertTrue(be.launch() == null && be.launching, "The shutter opens");
        double door = h.absolutePos(new BlockPos(10, 4, 4)).getX();
        h.succeedWhen(() -> {
            h.assertTrue(!be.launching && v.getX() > door + 1, "Out of the door: " + (v.getX() - door));
            // what it was given, less what its idling engine burned (not the design's fill again)
            h.assertTrue(v.tankKg(0) <= kg && v.tankKg(0) > 0.95 * kg && v.tankFuel(0).equals("gasoline"), "The fuel it was given: " + v.tankKg(0));
            for (int k = 1; k < v.tankCount(); k++) { h.assertTrue(v.tankKg(k) == 0 && v.tankFuel(k).isEmpty(), "Still empty: " + k + ": " + v.tankKg(k)); }
            h.assertTrue(v.assembled() && v.vehicleState() != null, "Built and stepping");
        });
    }

    // 0.17: a riveting machine at the room sets the rivets sooner (AssemblyPlan.laborHours(head)); the best one there counts
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aRivetingMachineInTheRoomIsFound(GameTestHelper h) {
        BlockPos c = carFactory(h);
        RoomControllerBlockEntity be = (RoomControllerBlockEntity) h.getBlockEntity(c);
        be.scan();
        h.assertTrue(be.riveter() == null, "No riveter yet: " + be.riveter());
        dev.mcmod.hdfv.neoforge.machine.MachineGameTests.tool(h, new BlockPos(4, 4, 6), Station.RIVETER, 1, "bare+squeeze_riveter", false);
        be.scan();
        h.assertTrue(be.riveter() == dev.mcmod.hdfv.manufacture.Head.SQUEEZE_RIVETER && be.scan().closed(), "A squeeze riveter in the room: " + be.riveter());
        dev.mcmod.hdfv.neoforge.machine.MachineGameTests.tool(h, new BlockPos(5, 4, 2), Station.RIVETER, 1, "bare+drill_riveter", false);
        be.scan();
        h.assertTrue(be.riveter() == dev.mcmod.hdfv.manufacture.Head.DRILL_RIVETER, "The drilling riveter is the quicker: " + be.riveter());
        h.succeed();
    }
}
