package dev.mcmod.hdfv.neoforge.road;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.road.RampShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The road ramps in the world (0.25): each is a block with its recipe, drop and names, shaped as its steps under the
 * slope; a run of them placed piece by piece continues by itself; and a car drives up a 1:4 run onto the level above.
 * The drive in its own batch: the car's engine tables are built first.
 */
@PrefixGameTestTemplate(false)
public final class RoadGameTests {
    private RoadGameTests() {}

    static double floor(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        return h.absolutePos(new BlockPos(0, 1, 0)).getY();
    }

    /** How high a block's shape stands at a place along its run (0 the low edge, 1 the high edge), by a ray down its middle as a wheel's. */
    static double heightAt(VoxelShape shape, Direction facing, double along) {
        double x = 0.5 + facing.getStepX() * (along - 0.5), z = 0.5 + facing.getStepZ() * (along - 0.5);
        BlockHitResult hit = shape.clip(new Vec3(x, 2, z), new Vec3(x, -1, z), BlockPos.ZERO);
        return hit == null ? 0 : hit.getLocation().y;
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = "hdfv_road")
    public static void theRampsAreMadeDroppedNamedAndShaped(GameTestHelper h) throws java.io.IOException {
        floor(h);
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        h.assertTrue(HdfvRoads.ids().equals(RampShape.ids()), "The ramps: " + HdfvRoads.ids());
        BlockPos p = new BlockPos(4, 1, 4);
        for (String id : HdfvRoads.ids()) {
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            h.assertTrue(b instanceof RoadRampBlock, "A ramp " + id);
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            RoadRampBlock ramp = (RoadRampBlock) b;
            int n = ramp.gradient();
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (int part = 0; part < n; part++) {
                    BlockState s = ramp.defaultBlockState().setValue(RoadRampBlock.FACING, facing).setValue(ramp.part(), part);
                    h.setBlock(p, s);
                    var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), null);
                    h.assertTrue(drops.size() == 1 && drops.get(0).is(b.asItem()), id + " drops itself: " + drops);
                    // the steps: within half a step's rise of the slope all along, to half a rise under the part's height at its high edge
                    VoxelShape shape = s.getCollisionShape(h.getLevel(), h.absolutePos(p));
                    double rise = 1.0 / (RampShape.STEPS * n);
                    for (int i = 0; i < 32; i++) {
                        double along = (i + 0.5) / 32, slope = RampShape.height(n, part, along), is = heightAt(shape, facing, along);
                        h.assertTrue(Math.abs(is - slope) <= rise / 2 + 1e-9, id + " " + facing + " part " + part + " at " + along + ": " + is + " for " + slope);
                    }
                    h.assertTrue(Math.abs(shape.max(Direction.Axis.Y) - (RampShape.height(n, part, 1) - rise / 2)) < 1e-9, id + " part " + part + " stands " + shape.max(Direction.Axis.Y));
                    h.assertTrue(!s.isCollisionShapeFullBlock(h.getLevel(), h.absolutePos(p)), id + " is not a whole block");
                }
            }
            // it holds water as a stair does
            h.setBlock(p, ramp.defaultBlockState().setValue(RoadRampBlock.WATERLOGGED, true));
            h.assertTrue(h.getBlockState(p).getFluidState().getType() == Fluids.WATER, id + " waterlogged");
            h.setBlock(p, Blocks.AIR);
        }
        for (String file : List.of("en_us", "zh_tw", "zh_cn")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : HdfvRoads.ids()) { h.assertTrue(lang.has("block.hdfv." + id), file + " lacks block " + id); }
                h.assertTrue(lang.has("hdfv.jei.road_ramp"), file + " lacks the ramps' JEI line");
            }
        }
        h.succeed();
    }

    /** A player looking a way puts a ramp on the block at a place (clicking that block's top). */
    private static void place(GameTestHelper h, Player player, Block ramp, BlockPos on, Direction look) {
        player.setYRot(look.toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ramp));
        BlockPos abs = h.absolutePos(on);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
        player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static void expect(GameTestHelper h, RoadRampBlock ramp, BlockPos pos, Direction facing, int part, String what) {
        BlockState s = h.getBlockState(pos);
        h.assertTrue(s.is(ramp), what + ": a ramp at " + pos + ", not " + s);
        h.assertTrue(s.getValue(RoadRampBlock.FACING) == facing && s.getValue(ramp.part()) == part,
                what + ": facing " + s.getValue(RoadRampBlock.FACING) + " part " + s.getValue(ramp.part()) + ", not " + facing + " part " + part);
    }

    /**
     * Placing a 1:4 run: the first piece rises the way the player looks; the next pieces, put after it, take the next
     * parts whichever way the player looks; a piece a block higher beyond the top starts the next flight the same way
     * round; a piece put before a higher part takes the part below it; a different ramp beside the run starts its own.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", batch = "hdfv_road")
    public static void aRunOfRampsGoesOnByItself(GameTestHelper h) {
        floor(h);
        RoadRampBlock ramp = HdfvRoads.ramp("asphalt", 4).get(), other = HdfvRoads.ramp("concrete", 4).get();
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        int z = 20;
        place(h, player, ramp, new BlockPos(4, 0, z), Direction.EAST);
        expect(h, ramp, new BlockPos(4, 1, z), Direction.EAST, 0, "the first piece");
        place(h, player, ramp, new BlockPos(5, 0, z), Direction.NORTH);
        expect(h, ramp, new BlockPos(5, 1, z), Direction.EAST, 1, "the second, looking across the run");
        place(h, player, ramp, new BlockPos(6, 0, z), Direction.WEST);
        expect(h, ramp, new BlockPos(6, 1, z), Direction.EAST, 2, "the third, looking back down it");
        place(h, player, ramp, new BlockPos(7, 0, z), Direction.EAST);
        expect(h, ramp, new BlockPos(7, 1, z), Direction.EAST, 3, "the top piece");
        // beyond the top at the same level nothing goes on: a new run the way the player looks
        place(h, player, ramp, new BlockPos(8, 0, z), Direction.SOUTH);
        expect(h, ramp, new BlockPos(8, 1, z), Direction.SOUTH, 0, "after the top piece at its level");
        // a block higher beyond the top (on the level's road): the next flight, the same way round
        h.setBlock(new BlockPos(8, 1, z), Blocks.STONE);
        place(h, player, ramp, new BlockPos(8, 1, z), Direction.NORTH);
        expect(h, ramp, new BlockPos(8, 2, z), Direction.EAST, 0, "the next flight");
        // before a higher part: the part below it
        h.setBlock(new BlockPos(14, 1, z), ramp.defaultBlockState().setValue(RoadRampBlock.FACING, Direction.WEST).setValue(ramp.part(), 2));
        place(h, player, ramp, new BlockPos(15, 0, z), Direction.NORTH);
        expect(h, ramp, new BlockPos(15, 1, z), Direction.WEST, 1, "before a higher part");
        // another ramp block beside a run is not its next piece
        place(h, player, other, new BlockPos(16, 0, z), Direction.NORTH);
        expect(h, other, new BlockPos(16, 1, z), Direction.NORTH, 0, "the concrete ramp beside the asphalt run");
        h.succeed();
    }

    static PartVehicle spawn(GameTestHelper h, VehicleBlueprint blueprint, double x, double z, double originY) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new IllegalStateException("Vehicle factory failed"); }
        v.setBlueprint(blueprint, new Vec3d(o.getX() + x, originY, o.getZ() + z), Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /**
     * The sample sedan, facing +x in D with some throttle, before a 1:4 asphalt run (four pieces rising east) onto a
     * deck a block up: it climbs the ramp and, braked on the deck, stands there a block higher than it started, upright.
     * Its wheels drive on the slope itself (RoadRampBlock is AeroCore 0.42's SlopedGround), not on the block's steps.
     */
    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_road_drive")
    public static void aCarDrivesUpARampOntoTheLevelAbove(GameTestHelper h) {
        double floor = floor(h);
        RoadRampBlock ramp = HdfvRoads.ramp("asphalt", 4).get();
        int foot = 12;
        for (int z = 8; z <= 20; z++) {
            for (int part = 0; part < 4; part++) {
                h.setBlock(new BlockPos(foot + part, 1, z), ramp.defaultBlockState().setValue(RoadRampBlock.FACING, Direction.EAST).setValue(ramp.part(), part));
            }
            for (int x = foot + 4; x < 28; x++) { h.setBlock(new BlockPos(x, 1, z), Blocks.STONE); }
        }
        VehicleBlueprint blueprint = SampleBlueprints.sedan();
        blueprint.compile().warmUp();
        PartVehicle v = spawn(h, blueprint, 5, 14, floor + 0.7);
        v.setControls(Map.of("selector", 0.0, "throttle", 0.0, "steer", 0.0, "brake", 1.0));
        double x0 = h.absolutePos(BlockPos.ZERO).getX();
        double[] start = new double[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenIdle(40)
                .thenExecute(() -> {
                    start[0] = v.vehicleState().origin().y();
                    v.setControls(Map.of("selector", 1.0, "throttle", 0.35, "brake", 0.0));
                })
                // all of it on the deck: the origin well past the ramp's top (the rear axle is 1.5 m behind it)
                .thenWaitUntil(() -> {
                    Vec3d at = v.vehicleState().origin();
                    h.assertTrue(at.x() - x0 > foot + 4 + 2.5, "Climbing: at x " + (at.x() - x0) + ", " + (at.y() - start[0]) + " m up, " + v.vehicleState().body().velocity());
                })
                .thenExecute(() -> v.setControls(Map.of("throttle", 0.0, "brake", 1.0)))
                .thenIdle(60)
                .thenExecute(() -> {
                    var body = v.vehicleState().body();
                    Vec3d at = v.vehicleState().origin();
                    h.assertTrue(body.velocity().length() < 0.3, "Stopped on the deck: " + body.velocity());
                    h.assertTrue(Math.abs(at.y() - start[0] - 1) < 0.08, "A block higher than it started: " + (at.y() - start[0]) + " m");
                    h.assertTrue(at.x() - x0 < 27, "Still on the deck: x " + (at.x() - x0));
                    h.assertTrue(body.orientation().rotate(Vec3d.Y).y() > 0.99, "Upright: " + body.orientation().rotate(Vec3d.Y));
                })
                .thenSucceed();
    }
}
