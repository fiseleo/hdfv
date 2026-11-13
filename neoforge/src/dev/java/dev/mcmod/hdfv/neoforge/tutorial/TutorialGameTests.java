package dev.mcmod.hdfv.neoforge.tutorial;

import dev.mcmod.aerocore.api.Vec3d;
import dev.mcmod.hdfv.damage.Conditions;
import dev.mcmod.hdfv.handbook.HandbookText;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.assembly.HdfvAssembly;
import dev.mcmod.hdfv.neoforge.assembly.HdfvVehicle;
import dev.mcmod.hdfv.neoforge.repair.FieldKitItem;
import dev.mcmod.hdfv.neoforge.repair.FieldKits;
import dev.mcmod.hdfv.neoforge.repair.HdfvRepair;
import dev.mcmod.hdfv.neoforge.storage.VehicleCargo;
import dev.mcmod.hdfv.repair.FieldRepair;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The new-player tutorial (0.24): its advancements load, a milestone grants the advancements waiting for it (and only
 * those), a new player gets the handbook once, and what the handbook's pages name exists in the game (items in the
 * registry, recipes on the server; the pictures, entries and languages are core's HandbookTest). 0.25's branches: the
 * repair and cargo milestones and the storeroom grant theirs, and a field kit's use and a loaded boot fire them where
 * the game does (a repair shop's job gives the players near it theirs, Milestones.near: only the milestone is tested,
 * a test's own player is in no level's player list).
 */
@PrefixGameTestTemplate(false)
public final class TutorialGameTests {
    private TutorialGameTests() {}

    private static AdvancementHolder advancement(GameTestHelper h, String path) {
        AdvancementHolder a = h.getLevel().getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, "tutorial/" + path));
        if (a == null) { throw new AssertionError("no advancement hdfv:tutorial/" + path); }
        return a;
    }

    private static boolean done(ServerPlayer p, AdvancementHolder a) { return p.getAdvancements().getOrStartProgress(a).isDone(); }

    /**
     * A player of the test's own that never joins: a mock player joining the server runs every mod's join handlers
     * (theirs send packets its connection cannot take), and NeoForge's fake players have advancements that never load.
     */
    private static ServerPlayer player(GameTestHelper h) {
        return new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "hdfv_tutorial"),
                net.minecraft.server.level.ClientInformation.createDefault());
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theTutorialsAdvancementsLoad(GameTestHelper h) {
        List<AdvancementHolder> tutorial = h.getLevel().getServer().getAdvancements().getAllAdvancements().stream()
                .filter(a -> a.id().getNamespace().equals(Hdfv.MOD_ID) && a.id().getPath().startsWith("tutorial/")).toList();
        h.assertTrue(tutorial.size() >= 35, "the tutorial's advancements load (" + tutorial.size() + ")");
        for (AdvancementHolder a : tutorial) {
            h.assertTrue(a.value().display().isPresent(), a.id() + " shows in the tab");
            h.assertTrue(a.id().getPath().equals("tutorial/root") || a.value().parent().isPresent(), a.id() + " hangs in the tree");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aMilestoneGrantsWhatWaitsForIt(GameTestHelper h) {
        ServerPlayer p = player(h);
        AdvancementHolder design = advancement(h, "design"), engine = advancement(h, "engine_design"), road = advancement(h, "on_the_road");
        Milestones.reach(p, "design_saved", "wing");
        h.assertTrue(done(p, design), "any saved design");
        h.assertFalse(done(p, engine), "a wing is not an engine");
        Milestones.reach(p, "design_saved", "engine");
        h.assertTrue(done(p, engine), "an engine design");
        Milestones.reach(p, "driven", "car", 500);
        h.assertFalse(done(p, road), "half a kilometre is not one");
        Milestones.reach(p, "driven", "boat", 1500);
        h.assertFalse(done(p, road), "a boat is not a car");
        Milestones.reach(p, "driven", "car", 1000);
        h.assertTrue(done(p, road), "a kilometre in a car");
        h.succeed();
    }

    /**
     * 0.25: a repair shop's job (kind shop) is a first repair but not one on the spot, a kit's (field) is both; a loaded
     * hold is Loaded Up; a storage block in the inventory the storeroom (vanilla's inventory_changed, fired as a joined
     * player's inventory listener would fire it).
     */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theRepairAndCargoMilestonesGrantTheirAdvancements(GameTestHelper h) {
        ServerPlayer shop = player(h), field = player(h), p = player(h);
        AdvancementHolder repaired = advancement(h, "repaired"), onTheSpot = advancement(h, "field_repaired"), loaded = advancement(h, "loaded"),
                storeroom = advancement(h, "storeroom");
        Milestones.reach(shop, "repaired", "shop");
        h.assertTrue(done(shop, repaired), "a repair shop's job is a repair");
        h.assertFalse(done(shop, onTheSpot), "a shop is not the spot");
        Milestones.reach(field, "repaired", "field");
        h.assertTrue(done(field, repaired) && done(field, onTheSpot), "a kit's repair is both");
        h.assertFalse(done(p, loaded), "nothing loaded yet");
        Milestones.reach(p, "loaded");
        h.assertTrue(done(p, loaded), "a loaded hold");
        h.assertFalse(done(p, storeroom), "no storage block yet");
        ItemStack locker = new ItemStack(dev.mcmod.hdfv.neoforge.storage.HdfvStorage.block(dev.mcmod.hdfv.storage.StorageKind.STEEL_LOCKER).get());
        p.getInventory().add(locker.copy());
        net.minecraft.advancements.CriteriaTriggers.INVENTORY_CHANGED.trigger(p, p.getInventory(), locker);
        h.assertTrue(done(p, storeroom), "a steel locker in the inventory");
        h.succeed();
    }

    /**
     * Where the game fires them, on a sample sedan: a field kit's use on its damaged body (FieldKits.begin and finish, as
     * holding the kit to the end does; the item's own finish would tell the player, who has no connection) gives the
     * first repair and the one on the spot; its boot opened and closed with nothing put in gives nothing, closed with
     * three ingots more Loaded Up (VehicleCargo.Hold's startOpen and stopOpen, as the hold's menu calls them).
     */
    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "helipad", batch = "hdfv_tutorial_sites", timeoutTicks = 600)
    public static void aKitsRepairAndALoadedBootGrantTheirAdvancements(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) { h.setBlock(new BlockPos(x, 0, z), Blocks.STONE); }
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        double floor = h.absolutePos(new BlockPos(0, 1, 0)).getY();
        dev.mcmod.aerocore.api.vehicle.VehicleBlueprint bp = dev.mcmod.hdfv.part.SampleBlueprints.sedan();
        bp.compile().warmUp();
        HdfvVehicle v = HdfvAssembly.VEHICLE.get().create(h.getLevel());
        if (v == null) { throw new AssertionError("no vehicle"); }
        v.setBlueprint(bp, new Vec3d(o.getX() + 4, floor + 0.1, o.getZ() + 8), dev.mcmod.aerocore.api.Orientation.IDENTITY);
        v.setBuilt(1);
        h.getLevel().addFreshEntity(v);
        ServerPlayer p = player(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(v.ready(), "Tables"))
                .thenIdle(40)   // set down a hand's breadth up: it drops onto its springs and bounces first
                .thenWaitUntil(() -> h.assertTrue(v.velocity().length() < 0.1, "Settling: " + v.velocity().length() + " m/s"))
                .thenExecuteAfter(5, () -> {
                    AdvancementHolder repaired = advancement(h, "repaired"), onTheSpot = advancement(h, "field_repaired"), loaded = advancement(h, "loaded");
                    ItemStack kit = new ItemStack(HdfvRepair.kit(FieldRepair.Kit.FIELD_REPAIR_KIT).get());
                    p.setItemInHand(InteractionHand.MAIN_HAND, kit);
                    v.setUsed("body", Conditions.STRUCTURE, 0.7);
                    Vec3d at = FieldKits.centre(v, v.vehicle().installed("body"));
                    // 2.2 m out on the -z side at the height of the body's centre, looking level toward +z, at it (yaw 0; not lookAt,
                    // which sends the player a packet)
                    p.moveTo(at.x(), at.y() - p.getEyeHeight(), at.z() - 2.2, 0, 0);
                    h.assertTrue(FieldKits.lookPoint(p, v) != null, "Looking at the car from " + p.getEyePosition() + ", its box " + v.getBoundingBox());
                    h.assertFalse(done(p, repaired), "Not repaired yet");
                    h.assertTrue(FieldKits.begin(p, v, InteractionHand.MAIN_HAND) == null && p.isUsingItem(), "The kit is begun on the body");
                    FieldKits.finish(p, kit, (FieldKitItem) kit.getItem());
                    p.stopUsingItem();
                    h.assertTrue(v.used("body", Conditions.STRUCTURE) < 0.6, "A step mended: " + v.used("body", Conditions.STRUCTURE));
                    h.assertTrue(done(p, repaired) && done(p, onTheSpot), "A kit's repair: the first repair and the one on the spot");
                    VehicleCargo.Hold boot = VehicleCargo.hold(v, 0);
                    h.assertTrue(boot != null, "The boot is a hold");
                    boot.startOpen(p);
                    boot.stopOpen(p);
                    h.assertFalse(done(p, loaded), "Opened and closed with nothing put in");
                    boot.startOpen(p);
                    boot.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
                    boot.stopOpen(p);
                    h.assertTrue(done(p, loaded), "Closed with three ingots more");
                    VehicleCargo.unload(v, s -> {});
                    v.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aNewPlayerGetsTheHandbookOnce(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertFalse(p.getData(HdfvTutorial.DATA).handbookGiven(), "a new player has had no handbook");
        HdfvTutorial.loggedIn(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(p.getData(HdfvTutorial.DATA).handbookGiven(), "the handbook is marked given");
        h.assertTrue(p.getInventory().countItem(HdfvTutorial.HANDBOOK.get()) == 1, "the new player has the handbook");
        HdfvTutorial.loggedIn(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(p.getInventory().countItem(HdfvTutorial.HANDBOOK.get()) == 1, "joining again gives no second one");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theHandbookNamesRealItemsAndRecipes(GameTestHelper h) throws Exception {
        Path pages = ModList.get().getModFileById(Hdfv.MOD_ID).getFile().findResource("assets", Hdfv.MOD_ID, "handbook", "en_us");
        var recipes = h.getLevel().getServer().getRecipeManager();
        int read = 0;
        try (Stream<Path> files = Files.list(pages)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                HandbookText.Entry e = HandbookText.parse(Files.readString(f, StandardCharsets.UTF_8));
                read++;
                item(h, e.icon(), e.id());
                for (HandbookText.Block b : e.blocks()) {
                    if (b instanceof HandbookText.Items i) { i.ids().forEach(id -> item(h, id, e.id())); }
                    if (b instanceof HandbookText.Recipe r) { h.assertTrue(recipes.byKey(ResourceLocation.parse(r.id())).isPresent(), e.id() + ": recipe " + r.id()); }
                    List<HandbookText.Span> spans = b instanceof HandbookText.Paragraph p ? p.spans() : b instanceof HandbookText.Heading g ? g.spans() : List.of();
                    for (HandbookText.Span s : spans) { if (s instanceof HandbookText.ItemName n) { item(h, n.id(), e.id()); } }
                }
            }
        }
        h.assertTrue(read >= 20, "the pages are read (" + read + ")");
        h.succeed();
    }

    private static void item(GameTestHelper h, String id, String entry) {
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)), entry + ": item " + id);
    }
}
