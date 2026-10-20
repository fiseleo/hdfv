package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.design.Blueprints;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.neoforge.machine.MachineGameTests;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
import dev.mcmod.hdfv.part.engine.SampleEngines;
import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Power in a running server (0.8), on real game ticks (a network shares each tick once): a cable's rating, a battery
 * running dry, a motor on a line shaft, an engine stand driving a lathe and burning what its engine's map says, a
 * generator feeding a CNC down a cable, a steam engine and a treadle.
 */
@PrefixGameTestTemplate(false)
public final class PowerGameTests {
    private PowerGameTests() {}

    /**
     * A lathe (0.9: a multiblock, 2 m, every accessory it has a cell for) whose drive is at a position, the headstock
     * over it: the pulley seat on a line shaft running up to it, or the motor seat; the old four's drawing and a piece at
     * its first turning step, worked by a fake player.
     */
    static MachineBlockEntity lathe(GameTestHelper h, BlockPos drive, boolean electric) {
        PartDesign four = MachineGameTests.oldFour();
        MachineBlockEntity lathe = MachineGameTests.tool(h, drive.above(), Station.LATHE, 2, electric);
        h.assertTrue(lathe.formed() && h.absolutePos(drive).equals(lathe.drivePos()), "The lathe stands on its drive");
        var seat = (dev.mcmod.hdfv.neoforge.machine.DriveBlockEntity) h.getLevel().getBlockEntity(lathe.drivePos());
        h.assertTrue(seat.machine() == lathe && seat.onShaft() == !electric, "The seat stands for its lathe: " + seat.machine() + " " + seat.onShaft());
        lathe.items().setItem(MachineBlockEntity.DRAWING, Blueprints.of(four));
        PartPlan plan = Plans.of(four);
        for (Workpiece c : plan.workpieces()) {
            for (int i = 0; i < c.route().size(); i++) {
                if (c.route().get(i).process().cutting() && lathe.tool().can(c.route().get(i), c)) {
                    lathe.items().setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, c, i));
                    // an operator of its own (one fake player can stand at only one machine)
                    FakePlayer p = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(drive.toString().getBytes()), "operator"));
                    MachineGameTests.work(h, p, lathe);
                    return lathe;
                }
            }
        }
        throw new IllegalStateException("No turning");
    }

    private static void line(GameTestHelper h, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) { h.setBlock(new BlockPos(x, y, z), HdfvPower.LINE_SHAFT.get()); }
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 40)
    public static void aCableCarriesNoMoreThanItsRating(GameTestHelper h) {
        // a creative source down four metres of light cable to a foundry (whose buffer would take far more)
        h.setBlock(new BlockPos(2, 2, 2), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER));
        for (int x = 3; x <= 6; x++) { h.setBlock(new BlockPos(x, 2, 2), HdfvPower.CABLE.get()); }
        h.setBlock(new BlockPos(7, 2, 2), HdfvMachines.MACHINES.get(Station.FOUNDRY).get());
        MachineBlockEntity foundry = (MachineBlockEntity) h.getBlockEntity(new BlockPos(7, 2, 2));
        double cap = PowerPlant.CableKind.LIGHT.ratedW() * HdfvConfig.workSecondsPerTick() / HdfvConfig.joulesPerFE();
        double loss = PowerPlant.CableKind.LIGHT.lossAtRated(4);
        h.succeedWhen(() -> {
            var net = EnergyNetworks.of(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)));
            h.assertTrue(net.cables == 4 && net.weakest == PowerPlant.CableKind.LIGHT, "Four metres of light cable: " + net.cables);
            h.assertTrue(Math.abs(net.lastCarriedFe() - cap) < 1e-6, "A tick through 6 mm^2 carries its rating: " + net.lastCarriedFe() + " FE of " + cap);
            h.assertTrue(Math.abs(net.lastLostFe() - cap * loss) < 1e-6, "losing I^2 R: " + net.lastLostFe());
            h.assertTrue(foundry.energy().getEnergyStored() > 0, "and the foundry got the rest");
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 400)
    public static void aBatteryRunsALatheUntilItIsFlat(GameTestHelper h) {
        MachineBlockEntity lathe = lathe(h, new BlockPos(2, 2, 2), true);
        h.setBlock(new BlockPos(2, 1, 2), HdfvPower.block(PowerBlock.Kind.BATTERY));
        BatteryBlockEntity battery = (BatteryBlockEntity) h.getBlockEntity(new BlockPos(2, 1, 2));
        battery.setStoredJ(300e3);
        h.succeedWhen(() -> {
            h.assertTrue(lathe.state() == MachineBlockEntity.State.NO_POWER, "Flat: " + lathe.state() + " " + battery.storedJ());
            h.assertTrue(lathe.progress() > 0, "It worked while it lasted: " + lathe.progress());
            h.assertTrue(battery.storedJ() < HdfvConfig.joulesPerFE() / PowerPlant.BATTERY.dischargeEfficiency() + 1, "Drawn down: " + battery.storedJ());
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 400)
    public static void aMotorOnALineShaftTurnsALathe(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 1, 2), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER));
        h.setBlock(new BlockPos(2, 2, 2), HdfvPower.block(PowerBlock.Kind.ELECTRIC_MOTOR));
        line(h, 3, 8, 2, 2);
        MachineBlockEntity lathe = lathe(h, new BlockPos(9, 2, 2), false);
        MotorBlockEntity motor = (MotorBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 2));
        h.succeedWhen(() -> {
            h.assertTrue(Math.abs(lathe.lineRpm() - PowerPlant.NOMINAL_RPM) < 1, "Line up to speed: " + lathe.lineRpm());
            h.assertTrue(lathe.state() == MachineBlockEntity.State.WORKING && lathe.lastRate() > 0.99, "Full power by belt: " + lathe.lastRate());
            h.assertTrue(motor.lastShaftW() > 0, "The motor drives it");
        });
    }

    /**
     * 0.11: an engine stand shows clients its engine, that it runs and its speed (to 50 rpm) for drawing it, and sends
     * nothing more while the engine runs steadily.
     */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 1200, batch = "hdfv_engine_stand")
    public static void anEngineStandShowsItsEngineAndSendsOnlyChanges(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, HdfvPower.block(PowerBlock.Kind.ENGINE_STAND));
        EngineStandBlockEntity stand = (EngineStandBlockEntity) h.getBlockEntity(at);
        h.assertFalse(stand.getUpdateTag(h.getLevel().registryAccess()).contains("engine"), "An empty stand shows no engine");
        stand.install(Workpieces.part(SampleEngines.inlineFour()));
        stand.tank().fill(new FluidStack(HdfvFluidRegistry.fluid("gasoline"), 100), IFluidHandler.FluidAction.EXECUTE);
        stand.awaitReady();
        stand.setRunning(true);
        long[] settled = {-1, 0};   // the tick it settled, and the updates sent by then
        h.succeedWhen(() -> {
            var tag = stand.getUpdateTag(h.getLevel().registryAccess());
            h.assertTrue(tag.contains("engine") && tag.getBoolean("running"), "Shows its engine running: " + tag);
            double rpm = stand.unit().engineRpm(), governed = stand.governorRpm(), shown = tag.getDouble("rpm");
            h.assertTrue(Math.abs(rpm - governed) < 0.005 * governed, "Settling: " + rpm + " of " + governed);
            h.assertTrue(shown == governed, "Its speed to 50 rpm, the governor's once settled: " + shown + " of " + governed);
            if (settled[0] < 0) { settled[0] = h.getTick(); settled[1] = stand.sends(); }
            h.assertTrue(h.getTick() - settled[0] >= 40, "Watching it run steadily");
            h.assertTrue(stand.sends() == settled[1], "A steady engine sends nothing: " + (stand.sends() - settled[1]) + " updates in 40 ticks; last " + stand.lastChange);
        });
    }

    /** 0.19: a running engine stand tells the players near it runs (their clients stream its engine), and that it stopped. */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 1200, batch = "hdfv_engine_stand")
    public static void anEngineStandTellsItRuns(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, HdfvPower.block(PowerBlock.Kind.ENGINE_STAND));
        EngineStandBlockEntity stand = (EngineStandBlockEntity) h.getBlockEntity(at);
        stand.install(Workpieces.part(SampleEngines.inlineFour()));
        stand.tank().fill(new FluidStack(HdfvFluidRegistry.fluid("gasoline"), 100), IFluidHandler.FluidAction.EXECUTE);
        stand.awaitReady();
        BlockPos abs = h.absolutePos(at);
        h.assertTrue(dev.mcmod.hdfv.neoforge.sound.SoundSync.sent(h.getLevel(), abs) == null, "Silent before it starts");
        stand.setRunning(true);
        h.startSequence()
                .thenWaitUntil(() -> {
                    var hum = dev.mcmod.hdfv.neoforge.sound.SoundSync.sent(h.getLevel(), abs);
                    h.assertTrue(hum != null && hum.process() == null && hum.running(), "Tells it runs: " + hum);
                })
                .thenExecute(() -> stand.setRunning(false))
                .thenWaitUntil(() -> h.assertTrue(dev.mcmod.hdfv.neoforge.sound.SoundSync.sent(h.getLevel(), abs) == null, "Silent once stopped"))
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 1200, batch = "hdfv_engine_stand")
    public static void anEngineStandDrivesALatheAndBurnsWhatItsEngineSays(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, HdfvPower.block(PowerBlock.Kind.ENGINE_STAND));
        EngineStandBlockEntity stand = (EngineStandBlockEntity) h.getBlockEntity(at);
        h.assertTrue(stand.install(Workpieces.part(SampleEngines.inlineFour())), "Takes the engine");
        h.assertTrue(stand.tank().fill(new FluidStack(HdfvFluidRegistry.fluid("diesel"), 50), IFluidHandler.FluidAction.EXECUTE) == 0, "Refuses diesel for a petrol engine");
        stand.tank().fill(new FluidStack(HdfvFluidRegistry.fluid("gasoline"), 100), IFluidHandler.FluidAction.EXECUTE);
        stand.awaitReady();
        stand.setRunning(true);
        line(h, 3, 6, 2, 2);
        MachineBlockEntity lathe = lathe(h, new BlockPos(7, 2, 2), false);
        double[] expected = {0};
        double density = HdfvFluidRegistry.spec(HdfvFluidRegistry.fluid("gasoline")).densityKgPerM3();
        h.onEachTick(() -> { if (stand.unit() != null) { expected[0] += stand.unit().fuelKgPerS() * HdfvConfig.workSecondsPerTick() / density * 1000; } });
        h.succeedWhen(() -> {
            h.assertTrue(stand.state() == EngineStandBlockEntity.State.RUNNING, "Running: " + stand.state());
            h.assertTrue(lathe.state() == MachineBlockEntity.State.WORKING && lathe.progress() > 40, "The lathe turns off the engine: " + lathe.progress());
            double rpm = stand.unit().engineRpm(), governed = stand.governorRpm();
            h.assertTrue(Math.abs(rpm - governed) < 0.02 * governed, "Governed: " + rpm + " of " + governed);
            h.assertTrue(Math.abs(stand.burntLitres() - expected[0]) <= 0.01 * expected[0] + 0.05, "Burnt " + stand.burntLitres() + " l; its flow says " + expected[0]);
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 1200, batch = "hdfv_engine_stand")
    public static void aGeneratorOnTheLineFeedsACncDownACable(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at, HdfvPower.block(PowerBlock.Kind.ENGINE_STAND));
        EngineStandBlockEntity stand = (EngineStandBlockEntity) h.getBlockEntity(at);
        stand.install(Workpieces.part(SampleEngines.turboDiesel()));
        stand.tank().fill(new FluidStack(HdfvFluidRegistry.fluid("diesel"), 200), IFluidHandler.FluidAction.EXECUTE);
        stand.awaitReady();
        stand.setRunning(true);
        h.setBlock(new BlockPos(3, 2, 2), HdfvPower.LINE_SHAFT.get());
        h.setBlock(new BlockPos(4, 2, 2), HdfvPower.block(PowerBlock.Kind.GENERATOR));
        for (int x = 5; x <= 8; x++) { h.setBlock(new BlockPos(x, 2, 2), HdfvPower.HEAVY_CABLE.get()); }
        // the CNC's cabinet at the cable's end, the machine running east and south of it
        BlockPos cabinet = h.absolutePos(new BlockPos(9, 2, 2));
        HdfvMultiblocks.place(h.getLevel(), Structures.CNC, cabinet, Direction.NORTH, 1, HdfvMultiblocks.cncModules("mill_turn_12"));
        MachineBlockEntity cnc = (MachineBlockEntity) h.getLevel().getBlockEntity(cabinet);
        PartDesign four = SampleEngines.inlineFour();
        cnc.items().setItem(MachineBlockEntity.DRAWING, Blueprints.of(four));
        dev.mcmod.hdfv.neoforge.machine.MachineGameTests.supply(cnc);
        Workpiece crank = Workpieces.piece(Plans.of(four), "crank");
        cnc.items().setItem(MachineBlockEntity.FIRST_INPUT, Workpieces.start(four, crank, 1));
        GeneratorBlockEntity gen = (GeneratorBlockEntity) h.getBlockEntity(new BlockPos(4, 2, 2));
        h.succeedWhen(() -> {
            h.assertTrue(gen.lastElectricW() > 1000, "Generating: " + gen.lastElectricW());
            h.assertTrue(cnc.state() == MachineBlockEntity.State.WORKING && cnc.progress() > 20, "The CNC works on it: " + cnc.state() + " " + cnc.progress());
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", timeoutTicks = 400)
    public static void aSteamEngineAndATreadleTurnLathes(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 2, 2), HdfvPower.block(PowerBlock.Kind.STEAM_ENGINE));
        SteamEngineBlockEntity steam = (SteamEngineBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 2));
        steam.items().insertItem(0, new ItemStack(Items.COAL, 16), false);
        line(h, 3, 4, 2, 2);
        MachineBlockEntity a = lathe(h, new BlockPos(5, 2, 2), false);
        // a treadle with a villager on it, beside a second lathe
        h.setBlock(new BlockPos(2, 2, 8), HdfvPower.block(PowerBlock.Kind.TREADLE));
        h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 3, 8));
        MachineBlockEntity b = lathe(h, new BlockPos(3, 2, 8), false);
        h.succeedWhen(() -> {
            h.assertTrue(a.state() == MachineBlockEntity.State.WORKING && a.lastRate() > 0.99, "Steam drives a lathe: " + a.lastRate());
            h.assertTrue(steam.burntKg() > 0, "burning coal");
            h.assertTrue(b.state() == MachineBlockEntity.State.WORKING && b.lastRate() > 0 && b.lastRate() < 0.5, "A treadle turns one slowly: " + b.lastRate());
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void powerBlocksAreMadeDroppedAndNamed(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos p = new BlockPos(6, 2, 6);
        for (String id : HdfvPower.ids()) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            if (id.equals("creative_power")) { continue; }
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.setBlock(p, block);
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), h.getLevel().getBlockEntity(h.absolutePos(p)));
            h.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), id + " drops itself: " + drops);
        }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : HdfvPower.ids()) { h.assertTrue(lang.has("block.hdfv." + id), file + " lacks " + id); }
                for (EngineStandBlockEntity.State st : EngineStandBlockEntity.State.values()) {
                    h.assertTrue(lang.has("hdfv.power.stand.state." + st.name().toLowerCase(java.util.Locale.ROOT)), file + " lacks stand state " + st);
                }
                for (String k : List.of("hdfv.power.creative", "hdfv.power.battery", "hdfv.power.generator", "hdfv.power.motor", "hdfv.power.steam",
                        "hdfv.power.treadle", "hdfv.power.stand", "hdfv.machine.state.no_power", "hdfv.machine.state.needs_axes")) {
                    h.assertTrue(lang.has(k), file + " lacks " + k);
                }
            }
        }
        h.succeed();
    }
}
