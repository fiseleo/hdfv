package dev.mcmod.hdfv.neoforge.refit;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.aerocore.api.vehicle.part.BodyPart;
import dev.mcmod.hdfv.look.LampRules;
import dev.mcmod.hdfv.look.LampSpec;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodyKitDesign;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import dev.mcmod.hdfv.refit.Refit;
import dev.mcmod.hdfv.refit.RefitJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The refit shop (0.16): it says what it lacks; tuning takes only work and power; a whole scale rebuilds the vehicle in
 * place on the floor (and the blueprint in the slot); a kit gives the body its aero; lamps take lamp units from the
 * chests and give the old ones back; a vehicle leaving stops the job; the shop keeps its work when saved.
 */
@PrefixGameTestTemplate(false)
public final class RefitShopGameTests {
    private RefitShopGameTests() {}

    static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) { for (int y = y0; y <= y1; y++) { for (int z = z0; z <= z1; z++) { h.setBlock(new BlockPos(x, y, z), s); } } }
    }

    /**
     * A shop 8 m long, 5 wide and 4 high inside (x 2-9, z 2-6, from y 3), stone all round, a 3 by 3 roller shutter in its
     * +x wall and its controller in the -x wall facing in; with or without its lights, power (a creative source behind
     * the controller), a welding station and a chest.
     */
    static BlockPos shop(GameTestHelper h, boolean equipped) {
        fill(h, 0, 1, 0, 15, 9, 10, Blocks.AIR.defaultBlockState());
        fill(h, 0, 1, 0, 15, 2, 10, Blocks.STONE.defaultBlockState());
        fill(h, 1, 2, 1, 10, 7, 7, Blocks.STONE.defaultBlockState());
        fill(h, 2, 3, 2, 9, 6, 6, Blocks.AIR.defaultBlockState());
        fill(h, 10, 3, 3, 10, 5, 5, HdfvAssembly.DOORS.get("roller_shutter").get().defaultBlockState());
        BlockPos c = new BlockPos(1, 4, 4);
        h.setBlock(c, HdfvRefit.CONTROLLER.get().defaultBlockState().setValue(RefitShopBlock.FACING, Direction.EAST));
        if (equipped) {
            h.setBlock(new BlockPos(2, 3, 2), HdfvAssembly.WORK_LIGHT.get());
            h.setBlock(new BlockPos(2, 3, 6), HdfvAssembly.WORK_LIGHT.get());
            h.setBlock(new BlockPos(0, 4, 4), HdfvPower.POWER.get(PowerBlock.Kind.CREATIVE_POWER).get());
            h.setBlock(new BlockPos(9, 3, 2), dev.mcmod.hdfv.neoforge.machine.HdfvMachines.MACHINES.get(dev.mcmod.hdfv.manufacture.Station.WELDING_STATION).get());
            h.setBlock(new BlockPos(9, 3, 6), Blocks.CHEST);
        }
        return c;
    }

    /** The sample sedan, assembled, standing in the shop (its origin is its body's rear end). */
    static HdfvVehicle sedan(GameTestHelper h) {
        VehicleBlueprint bp = SampleBlueprints.sedan();
        bp.compile().warmUp();
        HdfvVehicle v = HdfvAssembly.VEHICLE.get().create(h.getLevel());
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        v.setBlueprint(bp, new Vec3d(o.getX() + 3.0, o.getY() + 3.6, o.getZ() + 4.5), Orientation.IDENTITY);
        v.setBuilt(1);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** A player of its own for each kind of test (FakePlayerFactory.getMinecraft is one player all tests share, creative or not). */
    static FakePlayer player(GameTestHelper h, boolean creative) {
        String name = creative ? "hdfv_refit_creative" : "hdfv_refit_survival";
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name));
        p.getAbilities().instabuild = creative;
        return p;
    }

    private static List<String> keys(RefitShopBlockEntity be) {
        return be.report().getList("Problems", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(t -> ((net.minecraft.nbt.CompoundTag) t).getString("Key")).toList();
    }

    private static RefitShopBlockEntity.Edit set(String part, String page, String param, String text) {
        return new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.SET, part, page, param, text, -1, 0, null);
    }

    private static RefitShopBlockEntity.Edit op(RefitShopBlockEntity.Op op) { return RefitShopBlockEntity.Edit.of(op); }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_refit")
    public static void aShopSaysWhatItLacks(GameTestHelper h) {
        BlockPos c = shop(h, false);
        RefitShopBlockEntity be = (RefitShopBlockEntity) h.getBlockEntity(c);
        be.scan();
        List<String> lacking = keys(be);
        h.assertTrue(lacking.containsAll(List.of("hdfv.refit_shop.problem.few_lights", "hdfv.refit_shop.problem.no_vehicle", "hdfv.refit_shop.problem.no_power"))
                && !lacking.contains("hdfv.refit_shop.problem.open"), "A bare shop lacks lights, a vehicle and power: " + lacking);
        shop(h, true);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, false);
        h.runAfterDelay(5, () -> {
            be.scan();
            h.assertTrue(keys(be).isEmpty(), "Equipped, with a sedan, nothing is lacking: " + keys(be));
            // a part's insides are the assembly room's; a scale too large for the door is the shop's to say
            h.assertTrue(be.apply(set("engine", "block", "boreM", "95"), p) != null, "An engine's bore is not a refit");
            h.assertTrue(be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.SCALE, "", "", "", "", -1, 1.9, null), p) == null, "Scaled");
            be.pendingBlueprint().compile().warmUp();
            be.scan();
            h.assertTrue(keys(be).contains("hdfv.refit_shop.problem.door_small") || keys(be).contains("hdfv.refit_shop.problem.room_small"),
                    "Nearly twice as big, it does not fit the shop: " + keys(be));
            v.discard();
            h.succeed();
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_refit", timeoutTicks = 300)
    public static void tuningTakesOnlyWorkAndPower(GameTestHelper h) {
        BlockPos c = shop(h, true);
        RefitShopBlockEntity be = (RefitShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, false);
        double[] before = new double[3];
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    be.scan();
                    PistonEngineDesign e = (PistonEngineDesign) v.blueprint().part("engine").design();
                    h.assertTrue(be.apply(set("engine", "ignition", "revLimitRpm", String.valueOf(e.ignition().revLimitRpm() - 500)), p) == null, "A lower rev limit");
                    h.assertTrue(be.apply(set("front_axle", "suspension", "camberDeg", "-1.5"), p) == null, "More camber");
                    be.pendingBlueprint().compile().warmUp();
                    RefitJob job = RefitJob.of(v.blueprint(), be.pendingBlueprint());
                    h.assertTrue(job.pieces().isEmpty() && job.stockKg().isEmpty() && job.adjusted().size() == 2, "Only adjustments: " + job);
                    before[0] = v.vehicleState().tankKg(0);
                    before[1] = v.position().x;
                    before[2] = e.ignition().revLimitRpm();
                    h.assertTrue(be.apply(op(RefitShopBlockEntity.Op.START), p) == null && be.working, "Started with nothing to take: " + be.report());
                    be.workTotal = 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working (" + be.workDone + " of " + be.workTotal + ")"))
                .thenExecute(() -> {
                    PistonEngineDesign e = (PistonEngineDesign) v.blueprint().part("engine").design();
                    h.assertTrue(v.isAlive() && Math.abs(e.ignition().revLimitRpm() - (before[2] - 500)) < 1e-6, "Retuned in place: " + e.ignition().revLimitRpm()
                            + " (" + be.report().getString("Message") + ")");
                    double fuel = v.vehicleState().tankKg(0);
                    h.assertTrue(fuel > 0 && Math.abs(fuel - before[0]) < 0.05, "Its fuel as it was (less what it idled away): " + fuel + " from " + before[0]);
                    h.assertTrue(Math.abs(v.position().x - before[1]) < 0.3, "Where it stood");
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_refit_scale", timeoutTicks = 600)
    public static void aBiggerCarIsRebuiltInPlace(GameTestHelper h) {
        BlockPos c = shop(h, true);
        RefitShopBlockEntity be = (RefitShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, true);   // creative: no materials
        ItemStack drawing = new ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        drawing.set(HdfvAssembly.VEHICLE_LAYOUT.get(), dev.mcmod.hdfv.assembly.SampleLayouts.sedan().toJsonText());
        be.items.setItem(RefitShopBlockEntity.VEHICLE, drawing);
        double[] was = new double[2];
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    be.scan();
                    h.assertTrue(be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.SCALE, "", "", "", "", -1, 1.1, null), p) == null, "Scaled by 1.1");
                    be.pendingBlueprint().compile().warmUp();
                    RefitJob job = RefitJob.of(v.blueprint(), be.pendingBlueprint());
                    h.assertTrue(job.rebuilt().size() == v.blueprint().parts().size(), "Every part rebuilt: " + job.rebuilt());
                    was[0] = v.vehicle().properties().massKg();
                    was[1] = ((BodyDesign) v.blueprint().part("body").design()).shape().lengthM();
                    h.assertTrue(be.apply(op(RefitShopBlockEntity.Op.START), p) == null, "Started: " + keys(be));
                    be.workTotal = 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working"))
                .thenExecuteAfter(5, () -> {
                    double length = ((BodyDesign) v.blueprint().part("body").design()).shape().lengthM();
                    h.assertTrue(Math.abs(length - 1.1 * was[1]) < 1e-6, "The body a tenth longer: " + length + " (" + be.report().getString("Message") + ")");
                    h.assertTrue(v.vehicle().properties().massKg() > was[0] * 1.1, "Heavier: " + v.vehicle().properties().massKg() + " from " + was[0]);
                    double floor = h.absolutePos(BlockPos.ZERO).getY() + 3;
                    h.assertTrue(v.position().y > floor - 0.5, "On the shop's floor, not in it: " + v.position().y);
                    String layout = be.items.getItem(RefitShopBlockEntity.VEHICLE).get(HdfvAssembly.VEHICLE_LAYOUT.get());
                    double drawn = ((BodyDesign) VehicleBlueprint.fromJsonText(layout).part("body").design()).shape().lengthM();
                    h.assertTrue(Math.abs(drawn - length) < 1e-6, "The blueprint in the slot took the refit");
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_refit_kit", timeoutTicks = 300)
    public static void aKitGivesTheBodyItsAero(GameTestHelper h) {
        BlockPos c = shop(h, true);
        RefitShopBlockEntity be = (RefitShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, true);
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    be.scan();
                    for (String[] s : new String[][]{{"wingSpanM", "1400"}, {"diffuserDeg", "10"}, {"damM", "50"}, {"spoilerM", "30"}}) {
                        h.assertTrue(be.apply(set("body", BodyKitDesign.PAGE, s[0], s[1]), p) == null, "Kit " + s[0]);
                    }
                    be.pendingBlueprint().compile().warmUp();
                    h.assertTrue(be.apply(op(RefitShopBlockEntity.Op.START), p) == null, "Started: " + keys(be));
                    be.workTotal = 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working"))
                .thenExecute(() -> {
                    var body = v.vehicle().installed("body").part();
                    h.assertTrue(body instanceof BodyPart b && b.kit() != null, "The body carries its kit's aero (" + be.report().getString("Message") + ")");
                    var loads = ((BodyPart) body).kit().loads(new Vec3d(-50, 0, 0), 1.225, q -> 0.12);
                    h.assertTrue(loads.downforceN() > 500, "Downforce at 180 km/h: " + loads.downforceN());
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_refit", timeoutTicks = 300)
    public static void lampsTakeLampUnitsAndGiveTheOldOnesBack(GameTestHelper h) {
        BlockPos c = shop(h, true);
        RefitShopBlockEntity be = (RefitShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, false);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(new BlockPos(9, 3, 6));
        chest.setItem(0, new ItemStack(HdfvRefit.LAMP_UNIT.get(), 2));
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    be.scan();
                    // a roof light bar, a pair: two lamp units more than the kit's six (0.18: head clusters, fogs, tails)
                    LampSpec bar = new LampSpec(LampRules.Role.HEAD_HIGH, LampSpec.Style.BAR, LampSpec.Face.SIDE, 2.4, Math.PI, 0, 0, 0, true, 0.5, "", "", 0, 0);
                    h.assertTrue(be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.LAMP_ADD, "body", "", "", "", -1, 0, bar), p) == null, "A light bar added");
                    BodyDesign next = (BodyDesign) be.pendingBlueprint().part("body").design();
                    h.assertTrue(next.settings().lamps().size() == 4, "The kit's three pairs and the new one: " + next.settings().lamps());
                    RefitJob job = RefitJob.of(v.blueprint(), be.pendingBlueprint());
                    h.assertTrue(job.lampUnits() == 2 && job.lampsBack() == 0, "Two lamp units: " + job.lampUnits());
                    h.assertTrue(be.apply(op(RefitShopBlockEntity.Op.START), p) == null, "Started with the units in the chest: " + keys(be));
                    h.assertTrue(chest.getItem(0).isEmpty(), "The units taken");
                    be.workTotal = 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working"))
                .thenExecute(() -> {
                    BodyDesign body = (BodyDesign) v.blueprint().part("body").design();
                    h.assertTrue(body.settings().lamps() != null && body.settings().lamps().size() == 4, "The vehicle has its lamps");
                    // back to the kit's: the two units come back
                    h.assertTrue(be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.LAMPS_KIT, "body", "", "", "", -1, 0, null), p) == null, "The kit's again");
                    be.pendingBlueprint().compile().warmUp();
                    h.assertTrue(be.apply(op(RefitShopBlockEntity.Op.START), p) == null, "Started");
                    be.workTotal = 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working"))
                .thenExecute(() -> {
                    int back = 0;
                    for (int i = 0; i < chest.getContainerSize(); i++) { if (chest.getItem(i).is(HdfvRefit.LAMP_UNIT.get())) { back += chest.getItem(i).getCount(); } }
                    h.assertTrue(back == 2, "Two lamp units back in the chest: " + back);
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_refit", timeoutTicks = 200)
    public static void aVehicleLeavingStopsTheJob(GameTestHelper h) {
        BlockPos c = shop(h, true);
        RefitShopBlockEntity be = (RefitShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, true);
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    be.scan();
                    be.apply(set("front_axle", "suspension", "toeDeg", "0.2"), p);
                    h.assertTrue(be.apply(op(RefitShopBlockEntity.Op.START), p) == null && be.working, "Started");
                    v.discard();
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Stopped"))
                .thenExecute(() -> h.assertTrue(be.report().getString("Message").contains("left"), "The shop says why: " + be.report().getString("Message")))
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_refit")
    public static void theShopKeepsItsWorkWhenSavedAndLampsAreCrafted(GameTestHelper h) {
        BlockPos c = shop(h, true);
        RefitShopBlockEntity be = (RefitShopBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = player(h, false);
        h.runAfterDelay(5, () -> {
            be.scan();
            h.assertTrue(be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.SCALE, "", "", "", "", -1, 0.95, null), p) != null,
                    "Smaller, the sedan's cabin has no room left for its fuel tank (its seats keep their size)");
            h.assertTrue(be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.SCALE, "", "", "", "", -1, 1.05, null), p) == null, "Larger it is fine");
            be.items.setItem(RefitShopBlockEntity.BLANKS, new ItemStack(dev.mcmod.hdfv.neoforge.HdfvRegistry.BLANK_BLUEPRINT.get(), 3));
            var registries = h.getLevel().registryAccess();
            var tag = be.saveWithoutMetadata(registries);
            RefitShopBlockEntity copy = new RefitShopBlockEntity(be.getBlockPos(), be.getBlockState());
            copy.loadWithComponents(tag, registries);
            h.assertTrue(copy.pendingJson().equals(be.pendingJson()) && Math.abs(copy.scale() - 1.05) < 1e-12, "The vehicle-to-be and its scale are kept");
            h.assertTrue(copy.items.getItem(RefitShopBlockEntity.BLANKS).getCount() == 3, "And the slots");
            // writing a part's new design takes a blank
            h.assertTrue(be.apply(new RefitShopBlockEntity.Edit(RefitShopBlockEntity.Op.WRITE, "front_axle", "", "", "", -1, 0, null), p) != null
                    && be.items.getItem(RefitShopBlockEntity.BLANKS).getCount() == 2, "A blueprint written");
            var level = h.getLevel();
            java.util.function.Function<String, ItemStack> item = id -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)));
            List<ItemStack> in = List.of(item.apply("minecraft:glass_pane"), item.apply("minecraft:copper_ingot"), item.apply("minecraft:redstone"), item.apply("minecraft:iron_nugget"));
            var input = net.minecraft.world.item.crafting.CraftingInput.of(in.size(), 1, in);
            ItemStack lamps = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                    .map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
            h.assertTrue(lamps.is(HdfvRefit.LAMP_UNIT.get()) && lamps.getCount() == 2, "Glass, copper, redstone and a nugget: two lamp units, not " + lamps);
            h.assertTrue(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "refit_shop_controller")).isPresent(),
                    "A recipe for the controller");
            v.discard();
            h.succeed();
        });
    }
}
