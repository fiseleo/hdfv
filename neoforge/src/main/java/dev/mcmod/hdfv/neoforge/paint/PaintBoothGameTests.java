package dev.mcmod.hdfv.neoforge.paint;

import dev.mcmod.aerocore.api.Orientation;
import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.aerocore.api.vehicle.VehicleBlueprint;
import dev.mcmod.hdfv.look.Sticker;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.paint.Repaint;
import dev.mcmod.hdfv.part.SampleBlueprints;
import dev.mcmod.hdfv.part.body.BodyDesign;
import dev.mcmod.hdfv.part.body.BodySettingsDesign;
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
 * The paint booth (0.13): it says what it lacks; it takes only looks and stickers; a job takes its paint, film and power,
 * keeps the vehicle where it stands and gives it (and the blueprint in the slot) the new looks; a vehicle that leaves
 * stops the job; the booth keeps its vehicle-to-be and slots when saved.
 */
@PrefixGameTestTemplate(false)
public final class PaintBoothGameTests {
    private PaintBoothGameTests() {}

    private static final String IMAGE = "5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5ea5";

    static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) { for (int y = y0; y <= y1; y++) { for (int z = z0; z <= z1; z++) { h.setBlock(new BlockPos(x, y, z), s); } } }
    }

    /**
     * A booth 8 m long, 5 wide and 4 high inside (x 2-9, z 2-6, from y 3), stone all round, a 3 by 3 roller shutter in its
     * +x wall and its controller in the -x wall facing in; with or without its fan (in the roof), lights and power (a
     * creative source behind the controller).
     */
    static BlockPos booth(GameTestHelper h, boolean equipped) {
        fill(h, 0, 1, 0, 15, 9, 10, Blocks.AIR.defaultBlockState());
        fill(h, 0, 1, 0, 15, 2, 10, Blocks.STONE.defaultBlockState());
        fill(h, 1, 2, 1, 10, 7, 7, Blocks.STONE.defaultBlockState());
        fill(h, 2, 3, 2, 9, 6, 6, Blocks.AIR.defaultBlockState());
        fill(h, 10, 3, 3, 10, 5, 5, HdfvAssembly.DOORS.get("roller_shutter").get().defaultBlockState());
        BlockPos c = new BlockPos(1, 4, 4);
        h.setBlock(c, HdfvPaint.CONTROLLER.get().defaultBlockState().setValue(PaintBoothBlock.FACING, Direction.EAST));
        if (equipped) {
            h.setBlock(new BlockPos(6, 7, 4), HdfvPaint.EXHAUST.get().defaultBlockState().setValue(ExhaustBlock.FACING, Direction.DOWN));
            h.setBlock(new BlockPos(2, 3, 2), HdfvAssembly.WORK_LIGHT.get());
            h.setBlock(new BlockPos(2, 3, 6), HdfvAssembly.WORK_LIGHT.get());
            h.setBlock(new BlockPos(0, 4, 4), HdfvPower.POWER.get(PowerBlock.Kind.CREATIVE_POWER).get());
        }
        return c;
    }

    /** The sample sedan, assembled, standing in the booth (its origin is its body's rear end). */
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

    private static List<String> keys(PaintBoothBlockEntity be) {
        return be.report().getList("Problems", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(t -> ((net.minecraft.nbt.CompoundTag) t).getString("Key")).toList();
    }

    private static PartDesign body(VehicleBlueprint b) { return b.parts().stream().filter(e -> e.id().equals("body")).findFirst().orElseThrow().design(); }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_paint")
    public static void aBoothSaysWhatItLacks(GameTestHelper h) {
        BlockPos c = booth(h, false);
        PaintBoothBlockEntity be = (PaintBoothBlockEntity) h.getBlockEntity(c);
        be.scan();
        List<String> lacking = keys(be);
        h.assertTrue(lacking.containsAll(List.of("no_exhaust", "few_lights", "no_vehicle", "no_power")) && !lacking.contains("open") && !lacking.contains("no_door"),
                "A bare booth lacks its fan, lights, vehicle and power: " + lacking);
        booth(h, true);
        HdfvVehicle v = sedan(h);
        h.runAfterDelay(5, () -> {
            be.scan();
            h.assertTrue(keys(be).isEmpty(), "Equipped, with a sedan, nothing is lacking: " + keys(be));
            h.assertTrue(!be.pendingJson().isEmpty() && be.vehicle() == v, "It takes the sedan as the vehicle to work on");
            v.discard();
            h.succeed();
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_paint", timeoutTicks = 300)
    public static void aJobTakesItsMaterialsAndRepaintsTheVehicleWhereItStands(GameTestHelper h) {
        BlockPos c = booth(h, true);
        PaintBoothBlockEntity be = (PaintBoothBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        ItemStack drawing = new ItemStack(HdfvAssembly.VEHICLE_BLUEPRINT.get());
        drawing.set(HdfvAssembly.VEHICLE_LAYOUT.get(), dev.mcmod.hdfv.assembly.SampleLayouts.sedan().toJsonText());
        be.items.setItem(PaintBoothBlockEntity.BLUEPRINT, drawing);
        double[] mass = new double[1];
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    be.scan();
                    h.assertTrue(keys(be).isEmpty(), "Ready: " + keys(be));
                    // a look that is not a look, then the paint and a sticker
                    h.assertTrue(be.apply(new PaintBoothBlockEntity.Edit(PaintBoothBlockEntity.Op.LOOK, "body", "module1", "lengthM", "1.9", -1, null), p) != null,
                            "A module's length is refused");
                    h.assertTrue(be.apply(new PaintBoothBlockEntity.Edit(PaintBoothBlockEntity.Op.LOOK, "body", BodyDesign.SETTINGS, "paintColor", "#B3161C", -1, null), p) == null,
                            "The paint colour is taken");
                    Sticker s = new Sticker(IMAGE, 0.5, new Sticker.OnBody(2.3, 1.45), 1.8, 0, false, true, 0);
                    h.assertTrue(be.apply(new PaintBoothBlockEntity.Edit(PaintBoothBlockEntity.Op.ADD, "body", "", "", "", -1, s), p) == null, "A sticker is taken");
                    // its new body's tables now (as other tests' vehicles do), not on the shared builder other tests' vehicles wait for
                    be.pendingBlueprint().compile().warmUp();
                    h.assertTrue(be.apply(PaintBoothBlockEntity.Edit.of(PaintBoothBlockEntity.Op.START), p) != null, "Without paint cans it does not start");
                    var job = dev.mcmod.hdfv.paint.PaintJob.of(v.blueprint(), be.pendingBlueprint());
                    be.items.setItem(PaintBoothBlockEntity.CANS, new ItemStack(HdfvPaint.PAINT_CAN.get(), job.cans() + 1));
                    be.items.setItem(PaintBoothBlockEntity.FILM, new ItemStack(HdfvPaint.STICKER_FILM.get(), job.films() + 2));
                    mass[0] = v.blueprint().compile().properties().massKg();
                    h.assertTrue(be.apply(PaintBoothBlockEntity.Edit.of(PaintBoothBlockEntity.Op.START), p) == null, "Started");
                    h.assertTrue(be.working && be.items.getItem(PaintBoothBlockEntity.CANS).getCount() == 1 && be.items.getItem(PaintBoothBlockEntity.FILM).getCount() == 2,
                            "The cans and film the job needs are taken");
                    // a short job for the test (the booth's figures are work hours)
                    be.workTotal = 30;
                    be.sprayTotal = 20;
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Working (" + be.workDone + " of " + be.workTotal + ")"))
                .thenExecute(() -> {
                    h.assertTrue(v.isAlive(), "The same vehicle");
                    BodySettingsDesign settings = ((BodyDesign) body(v.blueprint())).settings();
                    h.assertTrue("#B3161C".equals(settings.paintColor()) && settings.stickers().size() == 1, "Repainted, its sticker on: " + settings.paintColor() + ", " + settings.stickers() + " (" + be.report().getString("Message") + ")");
                    h.assertTrue(Math.abs(v.blueprint().compile().properties().massKg() - mass[0]) < 1e-9, "Its mass the same");
                    h.assertTrue(be.energy.getEnergyStored() >= 0, "Power drawn");
                    String layout = be.items.getItem(PaintBoothBlockEntity.BLUEPRINT).get(HdfvAssembly.VEHICLE_LAYOUT.get());
                    BodySettingsDesign drawn = ((BodyDesign) body(VehicleBlueprint.fromJsonText(layout))).settings();
                    h.assertTrue("#B3161C".equals(drawn.paintColor()) && drawn.stickers().size() == 1, "The blueprint in the slot took the looks too");
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_paint", timeoutTicks = 200)
    public static void aVehicleLeavingStopsTheJob(GameTestHelper h) {
        BlockPos c = booth(h, true);
        PaintBoothBlockEntity be = (PaintBoothBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        // its own creative player (0.18): the shared one made creative here let the batch's other job start without paint cans
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("hdfv_paint_creative".getBytes()), "hdfv_paint_creative"));
        p.getAbilities().instabuild = true;   // creative: no materials
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    be.scan();
                    be.apply(new PaintBoothBlockEntity.Edit(PaintBoothBlockEntity.Op.LOOK, "body", BodyDesign.SETTINGS, "paintColor", "#2E7D32", -1, null), p);
                    h.assertTrue(be.apply(PaintBoothBlockEntity.Edit.of(PaintBoothBlockEntity.Op.START), p) == null && be.working, "Started for free in creative mode");
                    p.getAbilities().instabuild = false;
                    // gone from the booth (its physics keeps a teleported vehicle where it was: taken away instead)
                    v.discard();
                })
                .thenWaitUntil(() -> h.assertTrue(!be.working, "Stopped"))
                .thenExecute(() -> h.assertTrue(!"#2E7D32".equals(((BodyDesign) body(v.blueprint())).settings().paintColor()) && be.report().getString("Message").contains("left"),
                        "Not repainted, and the booth says why: " + be.report().getString("Message")))
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = "hdfv_paint")
    public static void theBoothKeepsItsWorkWhenSaved(GameTestHelper h) {
        BlockPos c = booth(h, true);
        PaintBoothBlockEntity be = (PaintBoothBlockEntity) h.getBlockEntity(c);
        HdfvVehicle v = sedan(h);
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        h.runAfterDelay(5, () -> {
            be.scan();
            be.apply(new PaintBoothBlockEntity.Edit(PaintBoothBlockEntity.Op.ADD, "body", "", "", "", -1, new Sticker(IMAGE, 1, new Sticker.OnBody(1, 1), 0.5, 30, true, false, 2)), p);
            be.items.setItem(PaintBoothBlockEntity.CANS, new ItemStack(HdfvPaint.PAINT_CAN.get(), 3));
            var registries = h.getLevel().registryAccess();
            var tag = be.saveWithoutMetadata(registries);
            PaintBoothBlockEntity copy = new PaintBoothBlockEntity(be.getBlockPos(), be.getBlockState());
            copy.loadWithComponents(tag, registries);
            h.assertTrue(copy.pendingJson().equals(be.pendingJson()) && Repaint.count(copy.pendingBlueprint()) == 1, "The vehicle-to-be is kept");
            h.assertTrue(copy.items.getItem(PaintBoothBlockEntity.CANS).getCount() == 3, "And the slots");
            v.discard();
            h.succeed();
        });
    }

    /** 0.14: the paint can and the sticker film are made at a crafting table from what the recipes say. */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void paintAndFilmAreCrafted(GameTestHelper h) {
        var level = h.getLevel();
        java.util.function.Function<String, ItemStack> item = id -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)));
        java.util.function.Function<List<ItemStack>, ItemStack> craft = in -> {
            var input = net.minecraft.world.item.crafting.CraftingInput.of(in.size(), 1, in);
            return level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                    .map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        };
        ItemStack cans = craft.apply(List.of(item.apply("hdfv:epoxy_resin"), item.apply("minecraft:red_dye"), item.apply("minecraft:iron_nugget")));
        h.assertTrue(cans.is(HdfvPaint.PAINT_CAN.get()) && cans.getCount() == 2, "Epoxy, a dye and an iron nugget: two paint cans, not " + cans);
        ItemStack film = craft.apply(List.of(item.apply("hdfv:polyethylene"), item.apply("hdfv:polyethylene")));
        h.assertTrue(film.is(HdfvPaint.STICKER_FILM.get()) && film.getCount() == 4, "Two polyethylene: four sticker films, not " + film);
        h.assertTrue(craft.apply(List.of(item.apply("hdfv:epoxy_resin"), item.apply("minecraft:iron_nugget"))).isEmpty(), "No dye, no paint");
        for (String id : List.of("paint_booth_controller", "paint_booth_exhaust")) {
            h.assertTrue(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id)).isPresent(), "A recipe for the " + id);
        }
        h.succeed();
    }
}
