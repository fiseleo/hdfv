package dev.mcmod.hdfv.neoforge.design;

import com.mojang.serialization.JsonOps;
import dev.mcmod.aerocore.api.vehicle.Json;
import dev.mcmod.aerocore.api.vehicle.PartDesign;
import dev.mcmod.hdfv.design.Designs;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.HdfvRegistry;
import dev.mcmod.hdfv.part.engine.PistonEngineDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Blueprints, the design table and the hdfv creative tab in a running server. */
@PrefixGameTestTemplate(false)
public final class DesignGameTests {
    private DesignGameTests() {}

    private static int count(Player p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) { if (p.getInventory().getItem(i).is(item)) { n += p.getInventory().getItem(i).getCount(); } }
        return n;
    }

    private static ItemStack first(Player p, Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) { if (p.getInventory().getItem(i).is(item)) { return p.getInventory().getItem(i); } }
        return ItemStack.EMPTY;
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void savingUsesABlankBlueprintAndStoresTheDesignAsItWritesItself(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        PartDesign sample = Designs.sample("inline_four");
        String pretty = Json.pretty(sample.toJson());
        h.assertTrue(Blueprints.save(player, pretty, Blueprints.Target.NEW) == Blueprints.Result.NEEDS_BLANK, "A survival player needs a blank blueprint");
        player.getInventory().add(new ItemStack(HdfvRegistry.BLANK_BLUEPRINT.get(), 2));
        h.assertTrue(Blueprints.save(player, "{\"type\":\"hdfv:nothing\"}", Blueprints.Target.NEW) == Blueprints.Result.UNREADABLE, "Unknown types are refused");
        h.assertTrue(Blueprints.save(player, "not json", Blueprints.Target.NEW) == Blueprints.Result.UNREADABLE, "Garbage is refused");
        h.assertTrue(count(player, HdfvRegistry.BLANK_BLUEPRINT.get()) == 2, "A refused save keeps the blank");
        h.assertTrue(Blueprints.save(player, pretty, Blueprints.Target.NEW) == Blueprints.Result.SAVED, "Saved");
        h.assertTrue(count(player, HdfvRegistry.BLANK_BLUEPRINT.get()) == 1, "One blank used");
        ItemStack written = first(player, HdfvRegistry.BLUEPRINT.get());
        h.assertTrue(Designs.write(sample).equals(Blueprints.json(written)), "The blueprint holds the design as it writes itself");
        // overwriting the blueprint in hand
        h.assertTrue(Blueprints.save(player, pretty, Blueprints.Target.MAIN_HAND) == Blueprints.Result.NOT_A_BLUEPRINT, "Nothing in hand to overwrite");
        player.getInventory().selected = 8;   // an empty hotbar slot (the blank blueprint is in the first)
        player.setItemInHand(InteractionHand.MAIN_HAND, written.copy());
        PartDesign wider = Designs.set(sample, "block", "boreM", "87");
        h.assertTrue(Blueprints.save(player, Designs.write(wider), Blueprints.Target.MAIN_HAND) == Blueprints.Result.OVERWRITTEN, "Overwritten");
        h.assertTrue(((PistonEngineDesign) Designs.read(Blueprints.json(player.getMainHandItem()))).block().boreM() == 0.087, "The held blueprint has the new bore");
        h.assertTrue(count(player, HdfvRegistry.BLANK_BLUEPRINT.get()) == 1, "Overwriting uses no blank");
        // creative players need no blank
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;   // as GameType.CREATIVE sets on a real player
        h.assertTrue(Blueprints.save(creative, pretty, Blueprints.Target.NEW) == Blueprints.Result.SAVED, "Creative saves without a blank");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void aBlueprintSurvivesSavingTheItemStack(GameTestHelper h) {
        ItemStack stack = Blueprints.of(Designs.sample("turbojet"));
        var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        ItemStack back = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        h.assertTrue(Blueprints.json(stack).equals(Blueprints.json(back)), "The design comes back");
        h.assertTrue(Blueprints.info(Blueprints.json(back)).invalid() == 0, "The sample is valid");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void recipesLootNamesAndTheCreativeTabAreThere(GameTestHelper h) throws java.io.IOException {
        // 0.7: crafting came with the machines (the user: "等有 CNC 那些的再寫合成"); every recipe of hdfv reads
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).toList();
        long alloying = recipes.stream().filter(r -> r.value().getType() == dev.mcmod.hdfv.neoforge.material.HdfvMaterials.ALLOYING.get()).count();
        h.assertTrue(alloying >= 30, "Alloying recipes: " + alloying);
        h.assertTrue(recipes.stream().anyMatch(r -> r.id().getPath().equals("alloy_furnace")), "The alloy furnace can be crafted");
        // every hdfv block and item has a name in both languages
        for (String file : java.util.List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                h.assertTrue(in != null, "No " + file);
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (Item item : BuiltInRegistries.ITEM) {
                    if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Hdfv.MOD_ID)) { continue; }
                    h.assertTrue(lang.has(item.getDescriptionId()), file + " lacks " + item.getDescriptionId());
                }
            }
        }
        CreativeModeTab tab = HdfvRegistry.TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess()));
        for (Item item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Hdfv.MOD_ID)) { continue; }
            h.assertTrue(tab.getDisplayItems().stream().anyMatch(s -> s.is(item)), "The hdfv tab shows " + item);
        }
        h.assertTrue(tab.getDisplayItems().stream().filter(s -> s.is(HdfvRegistry.BLUEPRINT.get())).count() == Designs.SAMPLES.size(), "One blueprint per sample");
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, HdfvRegistry.DESIGN_TABLE.get());
        var drops = Block.getDrops(h.getBlockState(pos), h.getLevel(), h.absolutePos(pos), null);
        h.assertTrue(drops.size() == 1 && drops.get(0).is(HdfvRegistry.DESIGN_TABLE_ITEM.get()), "The design table drops itself: " + drops);
        // every ore drops its raw item (no silk touch), the furnace itself
        for (var e : dev.mcmod.hdfv.neoforge.material.HdfvMaterials.ORE_BLOCKS.entrySet()) {
            h.setBlock(pos, e.getValue().get());
            var oreDrops = Block.getDrops(h.getBlockState(pos), h.getLevel(), h.absolutePos(pos), null);
            h.assertTrue(!oreDrops.isEmpty() && !oreDrops.get(0).is(e.getValue().get().asItem()), e.getKey() + " drops its ore: " + oreDrops);
        }
        h.setBlock(pos, dev.mcmod.hdfv.neoforge.material.HdfvMaterials.ALLOY_FURNACE.get());
        var furnace = Block.getDrops(h.getBlockState(pos), h.getLevel(), h.absolutePos(pos), null);
        h.assertTrue(furnace.size() == 1 && furnace.get(0).is(dev.mcmod.hdfv.neoforge.material.HdfvMaterials.ALLOY_FURNACE_ITEM.get()), "The furnace drops itself");
        h.succeed();
    }
}
