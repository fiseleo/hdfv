package dev.mcmod.hdfv.neoforge.worksite;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.aerocore.neoforge.vehicle.VehicleRegistry;
import dev.mcmod.hdfv.drive.TestMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.26: the machines in the world (WorldWork, WorkRules): the test excavator digs the dirt in front of it, the blocks
 * it cut are gone (or partly dug) and its bucket carries the loam as bulk; tipped out it piles the loam back as loose
 * fill. A hammer breaks stone into blasted rock.
 */
@PrefixGameTestTemplate(false)
public final class MachineryGameTests {
    private MachineryGameTests() {}

    /** Three layers of dirt over the 28 m pad, the top's height. */
    static double dirt(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 3; y++) { h.setBlock(new BlockPos(x, y, z), Blocks.DIRT); }
        }
        return h.absolutePos(new BlockPos(0, 4, 0)).getY();
    }

    static PartVehicle spawn(GameTestHelper h, VehicleBlueprint b, Vec3d origin) {
        PartVehicle v = VehicleRegistry.PART_VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new IllegalStateException("Vehicle factory failed"); }
        v.setBlueprint(b, origin, Orientation.IDENTITY);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    static int dirtLeft(GameTestHelper h) {
        int n = 0;
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) for (int y = 1; y <= 3; y++) {
            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) { n++; }
        }
        return n;
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 4000, batch = "hdfv_machinery_dig")
    public static void anExcavatorDigsTheDirtIntoItsBucket(GameTestHelper h) {
        double top = dirt(h);
        VehicleBlueprint b = TestMachines.excavator();
        b.compile().warmUp();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        PartVehicle v = spawn(h, b, new Vec3d(o.getX() + 8, top + 0.9, o.getZ() + 14));
        int before = 28 * 28 * 3;
        Map<String, Double> c = new HashMap<>(Map.of("throttle", 1.0, "travel_brake", 1.0, "work_mode", 1.0));
        int[] tick = {0};
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables still building"))
                .thenExecute(() -> h.onEachTick(() -> {
                    // an operator's digging cycle: stick out, boom down into the ground, crowd and curl along the cut, boom up
                    int t = tick[0]++ % 300;
                    c.put("boom", t < 40 ? 1.0 : t < 80 ? 0.0 : t < 170 ? -1.0 : t < 270 ? -0.5 : 1.0);
                    c.put("stick", t < 40 ? 0.0 : t < 80 ? -1.0 : t < 170 ? 0.0 : t < 270 ? 1.0 : 0.0);
                    c.put("bucket", t < 80 ? -1.0 : t < 170 ? 0.0 : t < 270 ? 1.0 : 0.0);
                    v.setControls(c);
                    if (tick[0] % 20 == 0 && v.vehicleState() != null) {
                        var st = v.vehicleState();
                        com.mojang.logging.LogUtils.getLogger().info("hdfv dig t {} boom {} stick {} bucket {} rate {} share {} p {} ctl {} y {}", tick[0],
                                st.jointValue(st.joint("boom")), st.jointValue(st.joint("stick")), st.jointValue(st.joint("bucket")), st.jointRate(st.joint("boom")),
                                st.hydraulicShare("main"), st.hydraulicPressure("main"), v.controls().get("boom"), st.body().position().y());
                    }
                }))
                .thenWaitUntil(() -> {
                    BulkCargo bulk = BulkCargo.of(v);
                    double m3 = bulk.holds().values().stream().mapToDouble(BulkCargo.Load::bankM3).sum();
                    var st = v.vehicleState();
                    String joints = "boom " + st.jointValue(st.joint("boom")) + ", stick " + st.jointValue(st.joint("stick")) + ", bucket " + st.jointValue(st.joint("bucket"))
                            + "; main share " + st.hydraulicShare("main") + "; at " + st.body().position() + "; dirt left " + dirtLeft(h) + " of " + before;
                    h.assertTrue(m3 > 0.05, "the bucket carries what it dug: " + m3 + " m3 (holds " + bulk.holds().keySet() + "): " + joints);
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 400, batch = "hdfv_machinery_rules")
    public static void aHammerBreaksStoneAndAPlateCompactsFill(GameTestHelper h) {
        BlockPos stone = new BlockPos(5, 1, 5), fill = new BlockPos(9, 1, 9);
        h.setBlock(stone, Blocks.STONE);
        h.setBlock(fill, HdfvWorksite.LOOSE_FILL.get());
        h.setBlock(fill.below(), Blocks.STONE);
        dev.mcmod.hdfv.part.machine.WorkToolDesign hammer = dev.mcmod.hdfv.part.machine.SampleTools.hydraulicHammer();
        BlockPos s = h.absolutePos(stone), f = h.absolutePos(fill);
        h.startSequence()
                .thenExecute(() -> {
                    // blows enough to break a cubic metre of 150 MPa rock (each its share at the hammer's energy)
                    for (int i = 0; i < 20 * 60 * 120 && !h.getLevel().getBlockState(s).is(HdfvWorksite.BLASTED_ROCK.get()); i++) {
                        WorkRules.hammer(h.getLevel(), null, hammer, new Vec3d(s.getX() + 0.5, s.getY() + 1.1, s.getZ() + 0.5), new Vec3d(0, -1, 0));
                    }
                    h.assertTrue(h.getLevel().getBlockState(s).is(HdfvWorksite.BLASTED_ROCK.get()), "the hammer broke the stone: " + h.getLevel().getBlockState(s));
                    for (int i = 0; i < WorkRules.PLATE_PASSES; i++) { WorkRules.compact(h.getLevel(), null, new Vec3d(f.getX() + 0.5, f.getY() + 1.2, f.getZ() + 0.5), 0.5); }
                    h.assertTrue(h.getLevel().getBlockState(f).is(HdfvWorksite.COMPACTED_BASE.get()), "the plate compacted the fill: " + h.getLevel().getBlockState(f));
                })
                .thenSucceed();
    }

    /** Builds a pattern's 3 x 3 x 3 plant with its controller at (x, 1, z) facing -z (its front row at z). */
    static void plant(GameTestHelper h, int x, int z, net.minecraft.world.level.block.Block controller, net.minecraft.world.level.block.Block middle, boolean concrete) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = 0; dz <= 2; dz++) for (int y = 1; y <= 3; y++) { h.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.IRON_BLOCK); }
        h.setBlock(new BlockPos(x, 1, z), controller.defaultBlockState().setValue(dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock.FACING, net.minecraft.core.Direction.NORTH));
        h.setBlock(new BlockPos(x, 2, z), Blocks.HOPPER);
        if (concrete) { h.setBlock(new BlockPos(x, 2, z + 1), Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3)); }
        else { h.setBlock(new BlockPos(x, 1, z + 1), Blocks.BLAST_FURNACE); }
        h.setBlock(new BlockPos(x, 0, z), dev.mcmod.hdfv.neoforge.power.HdfvPower.block(dev.mcmod.hdfv.neoforge.power.PowerBlock.Kind.CREATIVE_POWER));   // under the controller
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 2400, batch = "hdfv_machinery_plant")
    public static void anAsphaltPlantFillsTheDumpTruckInFront(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        plant(h, 14, 14, HdfvWorksite.ASPHALT_PLANT.get(), Blocks.IRON_BLOCK, false);
        BlockPos c = new BlockPos(14, 1, 14);
        VehicleBlueprint b = TestMachines.dumpTruck();
        b.compile().warmUp();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        // the truck across the plant's front, its body under the discharge
        PartVehicle truck = spawn(h, b, new Vec3d(o.getX() + 14 + 2.6, h.absolutePos(c).getY() + 0.9, o.getZ() + 14 - 3.5));
        truck.setControls(Map.of("brake", 1.0, "handbrake", 1.0));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(truck.ready(), "Tables still building"))
                .thenExecute(() -> {
                    PlantBlockEntity plant = (PlantBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(c));
                    h.assertTrue(plant != null, "the plant's controller");
                    plant.items().insertItem(0, new net.minecraft.world.item.ItemStack(Blocks.GRAVEL, 64), false);
                    plant.items().insertItem(1, new net.minecraft.world.item.ItemStack(dev.mcmod.hdfv.neoforge.petroleum.HdfvRefinery.BITUMEN.get(), 8), false);
                    plant.items().insertItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 4), false);
                })
                .thenWaitUntil(() -> {
                    BulkCargo bulk = BulkCargo.of(truck);
                    double m3 = bulk.holds().values().stream().mapToDouble(BulkCargo.Load::bankM3).sum();
                    PlantBlockEntity plant = (PlantBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(c));
                    h.assertTrue(m3 > 0.05, "hot mix in the truck's body: " + m3 + " m3; the plant: " + (plant == null ? null : plant.status().getString()));
                })
                .thenExecute(() -> {
                    BulkCargo.Load l = BulkCargo.of(truck).holds().values().stream().filter(x -> !x.isEmpty()).findFirst().orElseThrow();
                    h.assertTrue(l.main().equals("hdfv:hot_asphalt"), "it is hot mix: " + l.main());
                    h.assertTrue(l.temperatureK > 400, "made hot: " + l.temperatureK + " K");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = "hdfv", template = "helipad", timeoutTicks = 1200, batch = "hdfv_machinery_tower")
    public static void aTowerCraneFormsWithItsCabAndHook(GameTestHelper h) {
        for (int x = 0; x < 28; x++) for (int z = 0; z < 28; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        int x = 14, z = 14;
        for (int dx = -1; dx <= 1; dx++) for (int dz = 0; dz <= 2; dz++) { h.setBlock(new BlockPos(x + dx, 1, z + dz), Blocks.SMOOTH_STONE); }
        h.setBlock(new BlockPos(x, 1, z), HdfvWorksite.TOWER_CRANE.get().defaultBlockState().setValue(dev.mcmod.hdfv.neoforge.multiblock.ControllerBlock.FACING, net.minecraft.core.Direction.NORTH));
        for (int y = 2; y <= 6; y++) { h.setBlock(new BlockPos(x, y, z + 1), HdfvWorksite.CRANE_MAST.get()); }
        h.setBlock(new BlockPos(x, 7, z + 1), HdfvWorksite.CRANE_TOP.get());
        h.setBlock(new BlockPos(x, 0, z), dev.mcmod.hdfv.neoforge.power.HdfvPower.block(dev.mcmod.hdfv.neoforge.power.PowerBlock.Kind.CREATIVE_POWER));   // under the controller
        h.startSequence()
                .thenWaitUntil(() -> {
                    long cabs = h.getLevel().getEntitiesOfClass(PartVehicle.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(40))
                            .stream().filter(v -> v.blueprint() != null && TowerCraneBlockEntity.CAB.equals(v.blueprint().name())).count();
                    long hooks = h.getLevel().getEntitiesOfClass(PartVehicle.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(40))
                            .stream().filter(v -> v.blueprint() != null && "hook".equals(v.blueprint().name())).count();
                    h.assertTrue(cabs == 1 && hooks == 1, "the crane's cab and hook: " + cabs + ", " + hooks);
                    // its cab is current also as a save reads it back (else the controller would make it anew every tick)
                    PartVehicle cab = h.getLevel().getEntitiesOfClass(PartVehicle.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(40))
                            .stream().filter(v -> v.blueprint() != null && TowerCraneBlockEntity.CAB.equals(v.blueprint().name())).findFirst().orElseThrow();
                    var saved = dev.mcmod.aerocore.api.vehicle.VehicleBlueprint.fromJsonText(dev.mcmod.aerocore.api.vehicle.Json.write(cab.blueprint().toJson()));
                    h.assertTrue(TowerCraneBlockEntity.current(saved), "the cab's blueprint, saved and read back, is the current one");
                })
                .thenWaitUntil(() -> {
                    // the hook hangs on its rope from the trolley (it does not fall): its rope pulls
                    PartVehicle hook = h.getLevel().getEntitiesOfClass(PartVehicle.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(40))
                            .stream().filter(v -> v.blueprint() != null && "hook".equals(v.blueprint().name())).findFirst().orElseThrow();
                    var diag = hook.vehicleState() == null ? null : hook.vehicleState().diagnostics().get("hook");
                    double pull = diag == null ? 0 : diag.getOrDefault("tensionN", 0.0);
                    h.assertTrue(pull > 100, "the hook hangs on its rope: " + pull + " N at " + (hook.vehicleState() == null ? null : hook.vehicleState().body().position()));
                })
                .thenSucceed();
    }
}
