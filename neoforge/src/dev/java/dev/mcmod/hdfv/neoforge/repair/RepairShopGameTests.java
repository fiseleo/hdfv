package dev.mcmod.hdfv.neoforge.repair;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.Vehicle;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.neoforge.vehicle.PartVehicle;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvConfig;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.assembly.Staging;
import dev.mcmod.hdfv.neoforge.damage.VehicleDamage;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.neoforge.material.HdfvComponents;
import dev.mcmod.hdfv.neoforge.material.HdfvMaterials;
import dev.mcmod.hdfv.neoforge.material.Stock;
import dev.mcmod.hdfv.neoforge.material.TechStages;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.neoforge.storage.HdfvStorage;
import dev.mcmod.hdfv.neoforge.storage.StorageBlockEntity;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.recipe.TechTree;
import dev.mcmod.hdfv.repair.FieldRepair;
import dev.mcmod.hdfv.repair.RepairJob;
import dev.mcmod.hdfv.storage.StorageKind;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 0.25: repairs in the world. The repair shop (a room as the refit shop's, a storage crate at it): a damaged sedan is
 * repaired (the conditions new, the materials taken, the shop's power drawn, the job's hours); a part past the line is
 * not mended but replaced, once its new part is in the crate (its salvage comes back); missing materials, a rider and
 * a second vehicle are refused; the wreck rule's order; the job is kept when saved. The field kits: a step and no
 * further than half, nothing past the line or of the wrong kind, looking away stops it, the durability; the patch kit
 * on a flat tyre. Their blocks, items, recipes, stages and names. Damage settings off (DamageGameTests set their own):
 * nothing but the tests changes the conditions.
 */
@PrefixGameTestTemplate(false)
public final class RepairShopGameTests {
    private RepairShopGameTests() {}

    @AfterBatch(batch = "hdfv_repair") public static void afterRepair(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_repair_replace") public static void afterReplace(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_repair_wreck") public static void afterWreck(ServerLevel level) { VehicleDamage.override(null); }
    @AfterBatch(batch = "hdfv_repair_field") public static void afterField(ServerLevel level) { VehicleDamage.override(null); }

    static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) { for (int y = y0; y <= y1; y++) { for (int z = z0; z <= z1; z++) { h.setBlock(new BlockPos(x, y, z), s); } } }
    }

    static final BlockPos POWER = new BlockPos(0, 4, 4), CRATE = new BlockPos(9, 3, 6);
    /** As good as new: what a standing car's own wear (an idling engine, a settling wheel) adds in a few ticks is less. */
    static final double NEW = 1e-3;
    /** Settled: well under what the shop calls moving. */
    static final double STILL_MPS = 0.1;
    static final int SETTLE_TICKS = 40;

    /**
     * A shop 8 m long, 5 wide and 4 high inside (x 2-9, z 2-6, from y 3), stone all round, a 3 by 3 roller shutter in its
     * +x wall and its controller in the -x wall facing in, two work lights, a creative power source behind the
     * controller, a welding station and a storage crate (as the refit shop's tests build theirs).
     */
    static BlockPos shop(GameTestHelper h) {
        fill(h, 0, 1, 0, 15, 9, 10, Blocks.AIR.defaultBlockState());
        fill(h, 0, 1, 0, 15, 2, 10, Blocks.STONE.defaultBlockState());
        fill(h, 1, 2, 1, 10, 7, 7, Blocks.STONE.defaultBlockState());
        fill(h, 2, 3, 2, 9, 6, 6, Blocks.AIR.defaultBlockState());
        fill(h, 10, 3, 3, 10, 5, 5, HdfvAssembly.DOORS.get("roller_shutter").get().defaultBlockState());
        BlockPos c = new BlockPos(1, 4, 4);
        h.setBlock(c, HdfvRepair.CONTROLLER.get().defaultBlockState().setValue(RepairShopBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(2, 3, 2), HdfvAssembly.WORK_LIGHT.get());
        h.setBlock(new BlockPos(2, 3, 6), HdfvAssembly.WORK_LIGHT.get());
        h.setBlock(POWER, HdfvPower.POWER.get(PowerBlock.Kind.CREATIVE_POWER).get());
        h.setBlock(new BlockPos(9, 3, 2), dev.mcmod.hdfv.neoforge.machine.HdfvMachines.MACHINES.get(dev.mcmod.hdfv.manufacture.Station.WELDING_STATION).get());
        h.setBlock(CRATE, HdfvStorage.block(StorageKind.STORAGE_CRATE).get().defaultBlockState());
        return c;
    }

    static StorageBlockEntity crate(GameTestHelper h) { return (StorageBlockEntity) h.getBlockEntity(CRATE); }

    /** The sample sedan, assembled, standing in the shop at x 3 (its origin is its body's rear end) or set down elsewhere. */
    static HdfvVehicle sedan(GameTestHelper h, double x, double y, double z) {
        VehicleBlueprint bp = SampleBlueprints.sedan();
        bp.compile().warmUp();
        HdfvVehicle v = HdfvAssembly.VEHICLE.get().create(h.getLevel());
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        v.setBlueprint(bp, new Vec3d(o.getX() + x, o.getY() + y, o.getZ() + z), Orientation.IDENTITY);
        v.setBuilt(1);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    static HdfvVehicle sedan(GameTestHelper h) { return sedan(h, 3.0, 3.6, 4.5); }

    static FakePlayer player(GameTestHelper h, boolean creative) {
        String name = creative ? "hdfv_repair_creative" : "hdfv_repair_survival";
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.getAbilities().instabuild = creative;
        return p;
    }

    private static List<String> keys(RepairShopBlockEntity be) {
        return be.report().getList("Problems", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(t -> ((CompoundTag) t).getString("Key")).toList();
    }

    private static String key(net.minecraft.network.chat.Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    private static RepairShopBlockEntity.Edit op(RepairShopBlockEntity.Op op) { return RepairShopBlockEntity.Edit.of(op); }

    private static RepairShopBlockEntity.Edit pick(String part, String condition) {
        return new RepairShopBlockEntity.Edit(RepairShopBlockEntity.Op.TOGGLE, new RepairJob.Pick(part, condition).key());
    }

    /** How many of an item a container holds (a predicate on the stack). */
    private static int count(StorageBlockEntity c, java.util.function.Predicate<ItemStack> is) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) { if (!c.getItem(i).isEmpty() && is.test(c.getItem(i))) { n += c.getItem(i).getCount(); } }
        return n;
    }

    private static double stockKg(StorageBlockEntity c) {
        double kg = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.is(HdfvMaterials.STOCK.get()) || s.is(HdfvMaterials.STOCK_BIT.get())) { kg += Stock.kg(s); }
        }
        return kg;
    }

    /** A finished workpiece stack of a part's component (as the machines hand them out). */
    private static ItemStack piece(PartDesign d, String key) {
        for (Workpiece w : Plans.of(d).workpieces()) { if (w.key().equals(key)) { return Staging.workpieceOf(d, w); } }
        throw new IllegalArgumentException(key);
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_repair", timeoutTicks = 400)
    public static void aDamagedSedanIsRepairedWithTheCratesMaterials(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.OFF);
        BlockPos c = shop(h);
        RepairShopBlockEntity be = (RepairShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, false);
        StorageBlockEntity crate = crate(h);
        double[] job = new double[3];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables"))
                .thenIdle(SETTLE_TICKS)   // set down a hand's breadth up: it drops onto its springs and bounces first
                .thenWaitUntil(() -> h.assertTrue(v.velocity().length() < STILL_MPS, "Settling: " + v.velocity().length() + " m/s"))
                .thenExecuteAfter(5, () -> {
                    v.setUsed("body", Conditions.STRUCTURE, 0.4);
                    v.setUsed("front_axle", Conditions.BRAKES, 0.3);
                    v.setUsed("rear_axle", Conditions.TYRES, 0.2);
                    be.scan();
                    // picked one by one (all damaged would take the idling engine's first wear too)
                    for (String[] k : new String[][]{{"body", Conditions.STRUCTURE}, {"front_axle", Conditions.BRAKES}, {"rear_axle", Conditions.TYRES}}) {
                        h.assertTrue(be.apply(pick(k[0], k[1]), p) == null, "Picked " + k[0]);
                    }
                    h.assertTrue(be.picks().size() == 3, "Three picked: " + be.picks());
                    RepairJob j = be.job(v);
                    h.assertTrue(j.task(new RepairJob.Pick("body", Conditions.STRUCTURE)).kind() == RepairJob.Kind.MEND
                            && j.task(new RepairJob.Pick("rear_axle", Conditions.TYRES)).kind() == RepairJob.Kind.NEW_PIECES, "Mend the body, new tyres: " + j.tasks());
                    // nothing in the crate yet: the shop says what it lacks
                    var refused = be.apply(op(RepairShopBlockEntity.Op.START), p);
                    h.assertTrue("hdfv.repair_shop.missing".equals(key(refused)) && !be.working, "Refused for its materials: " + refused);
                    // the shell's steel, two linings, the rear axle's two new tyres
                    double steel = j.stockKg().get(Alloy.STEEL);
                    crate.setItem(0, Stock.of(Alloy.STEEL, (int) Math.ceil(steel / HdfvConfig.kgPerIngot()) + 1));
                    job[2] = stockKg(crate) - dev.mcmod.hdfv.manufacture.Pace.nuggets(steel, HdfvConfig.kgPerIngot()) * HdfvConfig.kgPerIngot() / 10;
                    crate.setItem(1, HdfvComponents.stack(Component.FRICTION_LINING, 2));
                    ItemStack tyres = piece(v.blueprint().part("rear_axle").design(), "tyre");
                    tyres.setCount(2);
                    crate.setItem(2, tyres);
                    be.scan();
                    var needs = be.report().getCompound("Job").getList("Needs", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    h.assertTrue(needs.size() == 3 && needs.stream().allMatch(t -> ((CompoundTag) t).getDouble("Have") >= ((CompoundTag) t).getDouble("Amount") - 1e-6),
                            "The crate has what it needs: " + needs);
                    h.assertTrue(be.apply(op(RepairShopBlockEntity.Op.START), p) == null && be.working, "Started: " + keys(be) + " " + be.report());
                    h.assertTrue(count(crate, s -> s.is(HdfvComponents.COMPONENTS.get(Component.FRICTION_LINING).get())) == 0 && count(crate, s -> Workpieces.data(s) != null) == 0,
                            "The linings and tyres taken");
                    h.assertTrue(Math.abs(stockKg(crate) - job[2]) < 1e-6, "The steel taken, its change back as bits: " + stockKg(crate) + " kg left, not " + job[2]);
                    // the job's hours on the work clock, its power at the shop's
                    h.assertTrue(Math.abs(be.workTotal - Math.max(1, Math.ceil(j.hours() * HdfvConfig.ticksPerWorkHour()))) < 1e-9 && j.hours() > 1,
                            "Its hours: " + be.workTotal + " ticks for " + j.hours() + " h");
                    h.assertTrue(Math.abs(be.watts - 1000 * j.kWh() / j.hours()) < 1e-6 && be.watts > 1000, "The shop's power: " + be.watts + " W");
                    job[0] = j.hours();
                    // drawn from the network: cut off, a little in its buffer, it is used and the work waits
                    h.setBlock(POWER, Blocks.STONE);
                    be.energy.set(40);
                })
                .thenExecuteAfter(3, () -> {
                    h.assertTrue(be.energy.getEnergyStored() < 40 && be.starved, "It drew its buffer and waits for power: " + be.energy.getEnergyStored());
                    job[1] = be.workDone;
                    h.setBlock(POWER, HdfvPower.POWER.get(PowerBlock.Kind.CREATIVE_POWER).get());
                })
                .thenExecuteAfter(3, () -> {
                    h.assertTrue(!be.starved && be.workDone > job[1], "Powered again, it works: " + be.workDone);
                    be.workTotal = be.workDone + 20;   // the refit tests' shortened clock
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working (" + be.workDone + " of " + be.workTotal + ")"))
                .thenExecute(() -> {
                    h.assertTrue(v.used("body", Conditions.STRUCTURE) < NEW && v.used("front_axle", Conditions.BRAKES) < NEW && v.used("rear_axle", Conditions.TYRES) < NEW,
                            "All three as new (" + be.report().getString("Message") + ")");
                    h.assertTrue(be.picks().isEmpty() && be.report().getString("Message").contains("hdfv.repair_shop.done"), "Done: " + be.report().getString("Message"));
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_repair_replace", timeoutTicks = 400)
    public static void aGearboxPastTheLineIsReplacedNotMended(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.OFF);
        BlockPos c = shop(h);
        RepairShopBlockEntity be = (RepairShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, false);
        StorageBlockEntity crate = crate(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables"))
                .thenIdle(SETTLE_TICKS)   // set down a hand's breadth up: it drops onto its springs and bounces first
                .thenWaitUntil(() -> h.assertTrue(v.velocity().length() < STILL_MPS, "Settling: " + v.velocity().length() + " m/s"))
                .thenExecuteAfter(5, () -> {
                    v.setUsed("gearbox", Conditions.GEARS, 0.9);
                    v.setUsed("gearbox", Conditions.CLUTCH, 0.3);
                    be.scan();
                    h.assertTrue(be.apply(pick("gearbox", Conditions.GEARS), p) == null, "Picked");
                    RepairJob j = be.job(v);
                    RepairJob.Task gears = j.task(new RepairJob.Pick("gearbox", Conditions.GEARS));
                    h.assertTrue(gears.kind() == RepairJob.Kind.NEW_PART && gears.reason().equals(RepairJob.WORN_OUT) && j.stockKg().isEmpty() && j.components().isEmpty(),
                            "Not mended: a new gearbox: " + j);
                    var refused = be.apply(op(RepairShopBlockEntity.Op.START), p);
                    String gearboxName = v.blueprint().part("gearbox").design().name();
                    var needs = be.report().getCompound("Job").getList("Needs", net.minecraft.nbt.Tag.TAG_COMPOUND);
                    h.assertTrue("hdfv.repair_shop.missing".equals(key(refused)) && needs.size() == 1 && ((CompoundTag) needs.get(0)).getString("Name").contains(gearboxName)
                            && ((CompoundTag) needs.get(0)).getDouble("Amount") == 1 && ((CompoundTag) needs.get(0)).getDouble("Have") == 0, "It needs a gearbox of its design: " + needs);
                    crate.setItem(0, Workpieces.part(v.blueprint().part("gearbox").design()));
                    h.assertTrue(be.apply(op(RepairShopBlockEntity.Op.START), p) == null && be.working, "Started with the new gearbox: " + keys(be));
                    h.assertTrue(crate.getItem(0).isEmpty(), "The gearbox taken");
                    be.workTotal = 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working"))
                .thenExecute(() -> {
                    h.assertTrue(v.used("gearbox", Conditions.GEARS) < NEW && v.used("gearbox", Conditions.CLUTCH) < NEW, "A new gearbox: its clutch new too");
                    double back = stockKg(crate);
                    double metal = 0;
                    for (Workpiece w : Plans.of(v.blueprint().part("gearbox").design()).workpieces()) { if (w.alloy().family.metal()) { metal += w.count() * w.finishedKg(); } }
                    h.assertTrue(back > 0.4 * metal && back <= 0.5 * metal + 1e-6, "The old one's metal back as bits: " + back + " kg of " + metal);
                    h.assertTrue(count(crate, s -> s.is(HdfvMaterials.STOCK_BIT.get()) && Stock.alloy(s) == Alloy.ALUMINIUM_CAST) > 0, "Its case's aluminium among them");
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_repair", timeoutTicks = 300)
    public static void aRiderOrASecondVehicleIsRefused(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.OFF);
        BlockPos c = shop(h);
        RepairShopBlockEntity be = (RepairShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, true);
        Pig pig = EntityType.PIG.create(h.getLevel());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables"))
                .thenIdle(SETTLE_TICKS)   // set down a hand's breadth up: it drops onto its springs and bounces first
                .thenWaitUntil(() -> h.assertTrue(v.velocity().length() < STILL_MPS, "Settling: " + v.velocity().length() + " m/s"))
                .thenExecuteAfter(5, () -> {
                    v.setUsed("body", Conditions.STRUCTURE, 0.2);
                    be.scan();
                    be.apply(op(RepairShopBlockEntity.Op.ALL), p);
                    h.assertTrue(keys(be).isEmpty(), "Ready: " + keys(be));
                    pig.moveTo(v.getX(), v.getY() + 1, v.getZ());
                    h.getLevel().addFreshEntity(pig);
                    h.assertTrue(pig.startRiding(v, true), "The pig gets in");
                    be.scan();
                    h.assertTrue(keys(be).contains("hdfv.repair_shop.problem.riders"), "Someone aboard: " + keys(be));
                    h.assertTrue(be.apply(op(RepairShopBlockEntity.Op.START), p) != null && !be.working, "Not started with a rider");
                    pig.stopRiding();
                    pig.discard();
                    HdfvVehicle second = sedan(h, 3.0, 5.2, 4.5);
                    be.scan();
                    h.assertTrue(keys(be).contains("hdfv.repair_shop.problem.vehicles") && be.picks().isEmpty(), "Two vehicles: " + keys(be));
                    h.assertTrue(be.apply(op(RepairShopBlockEntity.Op.START), p) != null && !be.working, "Not started with two");
                    second.discard();
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_repair_wreck", timeoutTicks = 400)
    public static void aWreckIsRepairedBodyAndEnginesTogether(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.DEFAULT);   // the wreck rule runs once a second
        BlockPos c = shop(h);
        RepairShopBlockEntity be = (RepairShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, true);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables"))
                .thenIdle(SETTLE_TICKS)   // set down a hand's breadth up: it drops onto its springs and bounces first
                .thenWaitUntil(() -> h.assertTrue(v.velocity().length() < STILL_MPS, "Settling: " + v.velocity().length() + " m/s"))
                .thenExecuteAfter(5, () -> v.setUsed("body", Conditions.STRUCTURE, 1))
                .thenWaitUntil(() -> h.assertTrue(v.used("engine", Conditions.ANCILLARIES) >= 1, "The wreck takes the engine's ancillaries"))
                .thenExecute(() -> {
                    be.scan();
                    h.assertTrue(be.apply(pick("engine", Conditions.ANCILLARIES), p) == null, "Picked");
                    h.assertTrue(keys(be).equals(List.of("hdfv.repair_shop.problem.wreck_first")), "The engine alone in a wreck: " + keys(be));
                    h.assertTrue("hdfv.repair_shop.problem.wreck_first".equals(key(be.apply(op(RepairShopBlockEntity.Op.START), p))) && !be.working, "Refused");
                    h.assertTrue(be.apply(pick("body", Conditions.STRUCTURE), p) == null && !keys(be).contains("hdfv.repair_shop.problem.wreck_first"),
                            "With the body it may: " + keys(be));
                    RepairJob j = be.job(v);
                    h.assertTrue(j.task(new RepairJob.Pick("body", Conditions.STRUCTURE)).kind() == RepairJob.Kind.REBUILD
                            && j.task(new RepairJob.Pick("engine", Conditions.ANCILLARIES)).kind() == RepairJob.Kind.NEW_PART, "A new shell and a new engine: " + j.tasks());
                    h.assertTrue(be.apply(op(RepairShopBlockEntity.Op.START), p) == null && be.working, "Started (creative: nothing taken): " + keys(be));
                    be.workTotal = 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working"))
                .thenIdle(45)   // two looks of the damage rules: the wreck rule does not fail the new engine
                .thenExecute(() -> {
                    h.assertTrue(v.used("body", Conditions.STRUCTURE) < NEW && v.used("engine", Conditions.ANCILLARIES) < NEW && v.used("engine", Conditions.ENGINE) < NEW,
                            "Body and engine new, and they stay so: body " + v.used("body", Conditions.STRUCTURE) + ", ancillaries " + v.used("engine", Conditions.ANCILLARIES));
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_repair", timeoutTicks = 400)
    public static void aJobIsKeptWhenTheShopIsSaved(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.OFF);
        BlockPos c = shop(h);
        RepairShopBlockEntity be = (RepairShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, true);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables"))
                .thenIdle(SETTLE_TICKS)   // set down a hand's breadth up: it drops onto its springs and bounces first
                .thenWaitUntil(() -> h.assertTrue(v.velocity().length() < STILL_MPS, "Settling: " + v.velocity().length() + " m/s"))
                .thenExecuteAfter(5, () -> {
                    v.setUsed("rear_axle", Conditions.SUSPENSION, 0.5);
                    v.setUsed("gearbox", Conditions.GEARS, 0.85);
                    be.scan();
                    be.apply(pick("rear_axle", Conditions.SUSPENSION), p);
                    be.apply(pick("gearbox", Conditions.GEARS), p);
                    h.assertTrue(be.apply(op(RepairShopBlockEntity.Op.START), p) == null && be.working, "Started: " + keys(be));
                    be.workDone = 7;
                    var registries = h.getLevel().registryAccess();
                    CompoundTag tag = be.saveWithoutMetadata(registries);
                    RepairShopBlockEntity copy = new RepairShopBlockEntity(be.getBlockPos(), be.getBlockState());
                    copy.loadWithComponents(tag, registries);
                    h.assertTrue(copy.working && copy.workDone == 7 && copy.workTotal == be.workTotal && Math.abs(copy.watts - be.watts) < 1e-9 && copy.free
                            && copy.started.size() == 2 && copy.picks().equals(be.picks()) && copy.salvage.equals(be.salvage), "The job is kept: " + tag);
                    // as when the world is loaded again: the shop as saved, the vehicle found again
                    be.working = false;
                    be.started = new net.minecraft.nbt.ListTag();
                    be.loadWithComponents(tag, registries);
                    h.assertTrue(be.working && be.started.size() == 2, "Read back");
                    be.workTotal = be.workDone + 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working"))
                .thenExecute(() -> {
                    h.assertTrue(v.used("rear_axle", Conditions.SUSPENSION) < NEW && v.used("gearbox", Conditions.GEARS) < NEW, "The saved job finished: " + be.report().getString("Message"));
                    v.discard();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- the field kits

    /** A stone floor over the pad; the height of its top. */
    static double floor(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        return h.absolutePos(new BlockPos(0, 1, 0)).getY();
    }

    /** The player stood beside a part (its centre's height, 2.2 m out on the -z side) looking at the part's centre. */
    static void lookAt(FakePlayer p, PartVehicle v, String part) {
        Vec3d at = FieldKits.centre(v, v.vehicle().installed(part));
        p.moveTo(at.x(), at.y() - p.getEyeHeight(), at.z() - 2.2, 0, 0);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(at.x(), at.y(), at.z()));
    }

    /** One use of the kit in the main hand: begun and finished as holding it would (null when it does not begin: the refusal's key then). */
    static String use(FakePlayer p, PartVehicle v) {
        var why = FieldKits.begin(p, v, InteractionHand.MAIN_HAND);
        if (why != null) { return key(why); }
        if (!p.isUsingItem()) { return "not using"; }
        ItemStack held = p.getMainHandItem();
        ((FieldKitItem) held.getItem()).finishUsingItem(held, p.level(), p);
        p.stopUsingItem();
        return null;
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_repair_field", timeoutTicks = 300)
    public static void aFieldKitMendsAStepNoFurtherThanHalfAndWearsOut(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.OFF);
        double floor = floor(h);
        HdfvVehicle v = sedan(h, 4, floor - h.absolutePos(BlockPos.ZERO).getY() + 0.1, 8);
        FakePlayer p = player(h, false);
        ItemStack kit = new ItemStack(HdfvRepair.kit(FieldRepair.Kit.FIELD_REPAIR_KIT).get());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables"))
                .thenIdle(SETTLE_TICKS)   // set down a hand's breadth up: it drops onto its springs and bounces first
                .thenWaitUntil(() -> h.assertTrue(v.velocity().length() < STILL_MPS, "Settling: " + v.velocity().length() + " m/s"))
                .thenExecuteAfter(5, () -> {
                    p.setItemInHand(InteractionHand.MAIN_HAND, kit);
                    v.setUsed("body", Conditions.STRUCTURE, 0.7);
                    lookAt(p, v, "body");
                    h.assertTrue(FieldKits.lookPoint(p, v) != null, "Looking at the car: eye " + p.getEyePosition() + " looking " + p.getViewVector(1) + " at "
                            + FieldKits.centre(v, v.vehicle().installed("body")) + ", the car's box " + v.getBoundingBox());
                    h.assertTrue(use(p, v) == null, "A use");
                    h.assertTrue(Math.abs(v.used("body", Conditions.STRUCTURE) - 0.55) < 1e-9, "A step: " + v.used("body", Conditions.STRUCTURE));
                    h.assertTrue(kit.getDamageValue() == 1, "A point of the kit: " + kit.getDamageValue());
                    h.assertTrue(use(p, v) == null && Math.abs(v.used("body", Conditions.STRUCTURE) - 0.5) < 1e-9, "Not past half: " + v.used("body", Conditions.STRUCTURE));
                    h.assertTrue("hdfv.repair.field.enough".equals(use(p, v)) && kit.getDamageValue() == 2, "No more in the field, and no wear for nothing");
                    // past the line: the shop's
                    v.setUsed("body", Conditions.STRUCTURE, 0.85);
                    h.assertTrue("hdfv.repair.field.replace".equals(use(p, v)) && Math.abs(v.used("body", Conditions.STRUCTURE) - 0.85) < NEW, "Past repair in the field");
                    // the wrong kind: worn brakes by the front wheel, nothing the kit mends
                    v.setUsed("body", Conditions.STRUCTURE, 0);
                    v.setUsed("front_axle", Conditions.BRAKES, 0.7);
                    lookAt(p, v, "front_axle");
                    h.assertTrue(use(p, v) != null && Math.abs(v.used("front_axle", Conditions.BRAKES) - 0.7) < NEW, "Brakes are the shop's");
                    // looking away stops a use
                    v.setUsed("body", Conditions.STRUCTURE, 0.7);
                    lookAt(p, v, "body");
                    h.assertTrue(FieldKits.begin(p, v, InteractionHand.MAIN_HAND) == null && p.isUsingItem(), "Begun");
                    Vec3 eye = p.getEyePosition();
                    p.lookAt(EntityAnchorArgument.Anchor.EYES, eye.add(eye.subtract(new Vec3(v.getX(), eye.y, v.getZ()))));   // turned round
                    kit.getItem().onUseTick(p.level(), p, kit, 40);
                    h.assertTrue(!p.isUsingItem() && Math.abs(v.used("body", Conditions.STRUCTURE) - 0.7) < NEW, "Looking away stops it");
                    // the last use breaks it
                    lookAt(p, v, "body");
                    kit.setDamageValue(kit.getMaxDamage() - 1);
                    h.assertTrue(kit.getMaxDamage() == FieldRepair.Kit.FIELD_REPAIR_KIT.uses, "Its uses: " + kit.getMaxDamage());
                    h.assertTrue(use(p, v) == null && p.getMainHandItem().isEmpty(), "Worn out after its last use");
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_repair_field", timeoutTicks = 300)
    public static void aPatchKitPlugsAFlatTyre(GameTestHelper h) {
        VehicleDamage.override(VehicleDamage.Settings.OFF);
        double floor = floor(h);
        HdfvVehicle v = sedan(h, 4, floor - h.absolutePos(BlockPos.ZERO).getY() + 0.1, 8);
        FakePlayer p = player(h, true);
        ItemStack kit = new ItemStack(HdfvRepair.kit(FieldRepair.Kit.TYRE_PATCH_KIT).get());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables"))
                .thenIdle(SETTLE_TICKS)   // set down a hand's breadth up: it drops onto its springs and bounces first
                .thenWaitUntil(() -> h.assertTrue(v.velocity().length() < STILL_MPS, "Settling: " + v.velocity().length() + " m/s"))
                .thenExecuteAfter(5, () -> {
                    p.setItemInHand(InteractionHand.MAIN_HAND, kit);
                    v.setUsed("rear_axle", Conditions.TYRES, 1);
                    lookAt(p, v, "rear_axle");
                    h.assertTrue(use(p, v) == null && Math.abs(v.used("rear_axle", Conditions.TYRES) - FieldRepair.FLOOR) < 1e-9, "Plugged and pumped up: " + v.used("rear_axle", Conditions.TYRES));
                    h.assertTrue("hdfv.repair.field.not_flat".equals(use(p, v)), "Worn tread is not patched");
                    h.assertTrue(kit.getDamageValue() == 0, "Creative: no wear");
                    h.assertTrue(kit.getMaxDamage() == FieldRepair.Kit.TYRE_PATCH_KIT.uses && kit.getItem().getUseDuration(kit, p) == FieldRepair.Kit.TYRE_PATCH_KIT.ticks, "Four uses, a few seconds each");
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_repair_field")
    public static void theRepairBlocksAndKitsAreMadeAndNamed(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        CreativeModeTab tab = HdfvRegistry.TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        for (String id : HdfvRepair.ids()) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            h.assertTrue(item != net.minecraft.world.item.Items.AIR, "An item " + id);
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.assertTrue(tab.getDisplayItems().stream().anyMatch(s -> s.is(item)), "The hdfv tab shows the " + id);
        }
        h.assertTrue(recipes.contains("friction_lining") || HdfvComponents.COMPONENTS.containsKey(Component.FRICTION_LINING), "The friction lining is a standard component");
        BlockState state = HdfvRepair.CONTROLLER.get().defaultBlockState();
        h.assertTrue(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), "Mined with a pickaxe");
        h.setBlock(new BlockPos(3, 2, 3), state);
        var drops = net.minecraft.world.level.block.Block.getDrops(h.getBlockState(new BlockPos(3, 2, 3)), h.getLevel(), h.absolutePos(new BlockPos(3, 2, 3)), h.getBlockEntity(new BlockPos(3, 2, 3)));
        h.assertTrue(drops.size() == 1 && drops.get(0).is(HdfvRepair.CONTROLLER.get().asItem()), "It drops itself: " + drops);
        // the tech tree: the shop at the refit shop's stage, the kits early
        Map<String, TechTree.Stage> stages = TechStages.of(h.getLevel().getRecipeManager(), h.getLevel().registryAccess());
        int shop = stages.get("hdfv:repair_shop_controller").tier(), refit = stages.get("hdfv:refit_shop_controller").tier(),
                kit = stages.get("hdfv:field_repair_kit").tier(), patch = stages.get("hdfv:tyre_patch_kit").tier(), lining = stages.get("hdfv:friction_lining").tier();
        org.slf4j.LoggerFactory.getLogger(RepairShopGameTests.class).info("hdfv repair stages: shop {}, refit shop {}, field kit {}, patch kit {}, friction lining {}", shop, refit, kit, patch, lining);
        h.assertTrue(Math.abs(shop - refit) <= 1 && kit <= 2 && patch <= 2 && lining <= shop, "Stages: shop " + shop + ", refit " + refit + ", kits " + kit + " " + patch + ", lining " + lining);
        for (String file : List.of("en_us", "zh_tw", "zh_cn")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                h.assertTrue(lang.has("block.hdfv.repair_shop_controller") && lang.has("hdfv.jei.repair_shop") && lang.has("item.hdfv.friction_lining"), file + " lacks the shop's names");
                for (FieldRepair.Kit k : FieldRepair.Kit.values()) {
                    h.assertTrue(lang.has("item.hdfv." + k.id()) && lang.has("hdfv.jei." + k.id()) && lang.has("hdfv.repair.kit." + k.id() + ".tip"), file + " lacks " + k.id());
                }
                for (String reason : List.of(FieldRepair.REPLACE, FieldRepair.ENOUGH, FieldRepair.NOT_FLAT)) { h.assertTrue(lang.has("hdfv.repair.field." + reason), file + " lacks " + reason); }
                for (RepairJob.Kind k : RepairJob.Kind.values()) {
                    String n = k.name().toLowerCase(java.util.Locale.ROOT);
                    h.assertTrue(lang.has("hdfv.repair_shop.kind." + n) && lang.has("hdfv.repair_shop.kind." + n + ".tip"), file + " lacks " + n);
                }
                for (String reason : List.of(RepairJob.WORN_OUT, RepairJob.NEVER_MENDED, RepairJob.WITH_PART)) { h.assertTrue(lang.has("hdfv.repair_shop.reason." + reason), file + " lacks " + reason); }
                for (String problem : List.of("open", "no_door", "few_lights", "no_vehicle", "vehicles", "building", "no_power", "moving", "riders", "welding_station", "nothing", "wreck_first")) {
                    h.assertTrue(lang.has("hdfv.repair_shop.problem." + problem), file + " lacks the problem " + problem);
                }
            }
        }
        h.succeed();
    }
}
