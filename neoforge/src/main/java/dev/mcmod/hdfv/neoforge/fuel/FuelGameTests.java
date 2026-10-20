package dev.mcmod.hdfv.neoforge.fuel;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.aerocore.neoforge.probe.Wakes;
import dev.mcmod.hdfv.part.HullDesign;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.fluid.JerryCanItem;
import dev.mcmod.hdfv.neoforge.vehicle.VehicleGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** Vehicle fuel in a running server (0.8): the dispenser at 50 l a minute, wrong fuel refused, cans poured in, a jet that runs dry. */
@PrefixGameTestTemplate(false)
public final class FuelGameTests {
    private FuelGameTests() {}

    /** The engine boat on a stone floor, its tank emptied. */
    static PartVehicle parked(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        VehicleBlueprint boat = SampleBlueprints.engineBoat();
        boat.compile().warmUp();   // its engine's map now, not on the shared builder other tests' vehicles wait for
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        v.setBlueprint(boat, new Vec3d(o.getX() + 8, o.getY() + 1.6, o.getZ() + 6), Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        v.setTank(0, "", 0, 1000);
        return v;
    }

    static FuelDispenserBlockEntity dispenser(GameTestHelper h, BlockPos at, String fuel, int litres) {
        h.setBlock(at, HdfvFuel.DISPENSER.get());
        FuelDispenserBlockEntity be = (FuelDispenserBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(at));
        int in = be.handler().fill(new FluidStack(HdfvFluidRegistry.fluid(fuel), litres), IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(in == litres, "Filled the dispenser: " + in);
        return be;
    }

    static double litres(PartVehicle v) { return v.tankKg(0) / Fuelling.density(v.tankFuel(0).isEmpty() ? "gasoline" : v.tankFuel(0)) * 1000; }

    // Its own batch: the engine boat may start on its petrol and shake on the floor.
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 400, batch = "hdfv_fuel_dispenser")
    public static void theDispenserFuelsAParkedVehicleAndRefusesTheWrongFuel(GameTestHelper h) {
        PartVehicle v = parked(h);
        FuelDispenserBlockEntity petrol = dispenser(h, new BlockPos(5, 1, 3), "gasoline", 200);
        FuelDispenserBlockEntity diesel = dispenser(h, new BlockPos(11, 1, 3), "diesel", 200);
        // diesel for a petrol engine: refused, nothing goes in
        diesel.start(v);
        h.assertTrue(diesel.state() == FuelDispenserBlockEntity.State.REFUSED && !diesel.fuelling() && v.tankKg(0) == 0, "Diesel refused: " + diesel.state());
        h.assertTrue(petrol.start(v) != null && petrol.fuelling(), "Fuelling");
        h.runAfterDelay(240, () -> {
            // 12 s at 50 l a minute: 10 l
            double l = litres(v);
            h.assertTrue(Math.abs(l - 10) <= 1.01, "10 l in 12 s: " + l);
            h.assertTrue(v.tankFuel(0).equals("gasoline") && petrol.fluid().getAmount() == 200 - petrol.pumped() && Math.abs(petrol.pumped() - l) < 0.01,
                    "From the dispenser's tank: " + petrol.fluid().getAmount() + " l left, " + petrol.pumped() + " l pumped");
            // a can of petrol poured in, a can of diesel refused
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, JerryCanItem.filled(HdfvFluidRegistry.fluid("diesel"), 20));
            CommonHooks.onInteractEntity(player, v, InteractionHand.MAIN_HAND);
            h.assertTrue(JerryCanItem.content(player.getMainHandItem()).getAmount() == 20 && Math.abs(litres(v) - l) < 0.01, "The diesel can stays full");
            player.setItemInHand(InteractionHand.MAIN_HAND, JerryCanItem.filled(HdfvFluidRegistry.fluid("gasoline"), 20));
            CommonHooks.onInteractEntity(player, v, InteractionHand.MAIN_HAND);
            h.assertTrue(JerryCanItem.content(player.getMainHandItem()).isEmpty() && Math.abs(litres(v) - l - 20) < 0.01, "The petrol can poured in: " + litres(v));
            // used again: it stops
            petrol.use();
            h.assertTrue(!petrol.fuelling(), "Stopped");
            h.succeed();
        });
    }

    // Its own batch, as the one above.
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 400, batch = "hdfv_fuel_amount")
    public static void theDispenserStopsAtTheAmountSetOnItsScreen(GameTestHelper h) {
        PartVehicle v = parked(h);
        FuelDispenserBlockEntity petrol = dispenser(h, new BlockPos(5, 1, 3), "gasoline", 200);
        h.assertTrue(petrol.set("amount", 1) == null && petrol.limitLitres() == 10, "10 litres");
        h.assertTrue(petrol.set("start", 1) == null && petrol.fuelling(), "Started from its screen");
        h.runAfterDelay(300, () -> {
            // 15 s at 50 l a minute would be 12.5 l: it stopped at 10
            h.assertTrue(petrol.state() == FuelDispenserBlockEntity.State.DONE && !petrol.fuelling(), "Done: " + petrol.state());
            h.assertTrue(petrol.pumped() == 10 && Math.abs(litres(v) - 10) < 0.01, "10 l in: " + petrol.pumped() + ", the tank has " + litres(v));
            h.succeed();
        });
    }

    // Its own batch: the jet drives the boat across the pool.
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 500, batch = "hdfv_dry_jet_boat")
    public static void aJetBoatGoesOutWhenItsTankRunsDry(GameTestHelper h) {
        Wakes.synchronous = true;
        double surface = VehicleGameTests.pool(h);
        VehicleBlueprint blueprint = VehicleGameTests.quickStart(SampleBlueprints.jetBoat());
        blueprint.compile().warmUp();
        HullDesign hull = (HullDesign) blueprint.part("hull").design();
        PartVehicle v = VehicleGameTests.spawn(h, blueprint, 4, surface - hull.designParticulars(hull.geometry()).waterlineY());
        v.setControls(Map.of("throttle", 0.0, "rudder", 0.0));
        final int[] dry = {-1};
        final double[] idle = {0};
        final int[] tick = {0};
        h.succeedWhen(() -> {
            tick[0]++;
            if (!v.ready()) { throw new GameTestAssertException("Running lines still building"); }
            Map<String, Double> jet = v.vehicleState().diagnostics().get("jet");
            if (dry[0] < 0) {
                if (jet == null || jet.get("lit") != 1 || jet.get("speedFraction") < 0.5) { throw new GameTestAssertException("Starting: " + jet); }
                h.assertTrue(v.tankFuel(0).equals("kerosene") && v.tankKg(0) > 0, "On the tank's kerosene");
                idle[0] = jet.get("thrustN");
                v.setTank(0, "kerosene", 0.05, 800);   // a few seconds' worth at idle
                dry[0] = tick[0];
                throw new GameTestAssertException("Running dry");
            }
            if (tick[0] - dry[0] < 100) { throw new GameTestAssertException("Waiting"); }
            h.assertTrue(v.tankKg(0) == 0 && v.tankFuel(0).isEmpty(), "The tank ran dry: " + v.tankKg(0));
            // out, its spool running down (the windmilling compressor still pushes a little air through)
            h.assertTrue(jet.get("lit") == 0 && jet.get("fuelKgPerS") == 0 && jet.get("thrustN") < 0.25 * idle[0], "The jet went out: " + jet + ", idle thrust " + idle[0]);
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theDispenserIsMadeDroppedAndNamed(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos p = new BlockPos(4, 2, 4);
        for (String id : HdfvFuel.ids()) {
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.setBlock(p, HdfvFuel.DISPENSER.get());
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), h.getLevel().getBlockEntity(h.absolutePos(p)));
            h.assertTrue(drops.size() == 1 && drops.get(0).is(HdfvFuel.DISPENSER.get().asItem()), id + " drops itself: " + drops);
        }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                List<String> keys = new java.util.ArrayList<>(List.of("block.hdfv.fuel_dispenser", "hdfv.dispenser.status", "hdfv.dispenser.no_vehicle",
                        "hdfv.dispenser.started", "hdfv.dispenser.dry", "hdfv.fuel.not_fuel", "hdfv.fuel.no_tank", "hdfv.fuel.wrong", "hdfv.fuel.other",
                        "hdfv.fuel.full", "hdfv.fuel.poured", "hdfv.hud.fuel", "hdfv.hud.fuel_empty", "hdfv.room.problem.no_tank"));
                for (var s : FuelDispenserBlockEntity.State.values()) { keys.add("hdfv.dispenser.state." + s.name().toLowerCase(java.util.Locale.ROOT)); }
                for (String k : keys) { h.assertTrue(lang.has(k), file + " lacks " + k); }
            }
        }
        h.succeed();
    }
}
