package dev.mcmod.hdfv.neoforge.tutorial;

import dev.mcmod.hdfv.handbook.HandbookText;
import dev.mcmod.hdfv.neoforge.Hdfv;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
 * registry, recipes on the server; the pictures, entries and languages are core's HandbookTest).
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
