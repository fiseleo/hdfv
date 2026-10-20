package dev.mcmod.hdfv.neoforge.material;

import dev.mcmod.hdfv.manufacture.Alloy;
import dev.mcmod.hdfv.manufacture.PartPlan;
import dev.mcmod.hdfv.manufacture.Plans;
import dev.mcmod.hdfv.manufacture.Station;
import dev.mcmod.hdfv.manufacture.Workpiece;
import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.machine.HdfvMachines;
import dev.mcmod.hdfv.neoforge.machine.MachineBlockEntity;
import dev.mcmod.hdfv.neoforge.machine.MachineGameTests;
import dev.mcmod.hdfv.neoforge.machine.Workpieces;
import dev.mcmod.hdfv.neoforge.smelting.HdfvSmelting;
import dev.mcmod.hdfv.neoforge.smelting.SmelterBlockEntity;
import dev.mcmod.hdfv.part.component.Component;
import dev.mcmod.hdfv.part.component.ComponentDesign;
import dev.mcmod.hdfv.recipe.TechTree;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * The recipes of 0.23 in the game: the bench builds a machine from its standard components with a fitter at it; a
 * machine hands out a component at the end of its drawing's route, its chips as stock bits, and starts only when all it
 * hands out fits; the arc furnace and the cell work their batches on power; hoppers feed the alloy furnace; every hdfv
 * thing the recipes make is on the tech tree.
 */
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {
    private RecipeGameTests() {}

    private static final String BATCH = "recipes";

    private static void tick(MachineBlockEntity be) { MachineBlockEntity.tick(be.getLevel(), be.getBlockPos(), be.getBlockState(), be); }

    private static void put(MachineBlockEntity be, ItemStack... stacks) {
        int slot = MachineBlockEntity.FIRST_INPUT;
        for (ItemStack s : stacks) { be.items().setItem(slot++, s); }
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = BATCH)
    public static void theBenchBuildsTheFirstLatheFromCastingsAndForgings(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, HdfvMachines.MACHINES.get(Station.ASSEMBLY_BENCH).get());
        MachineBlockEntity bench = (MachineBlockEntity) h.getBlockEntity(pos);
        put(bench, HdfvComponents.stack(Component.BED_CASTING, 1), HdfvComponents.stack(Component.FRAME_CASTING, 1),
                HdfvComponents.stack(Component.FORGED_SHAFT, 2), HdfvComponents.stack(Component.CAST_GEAR, 3),
                HdfvComponents.stack(Component.BRONZE_BUSHING, 2), HdfvComponents.stack(Component.STEEL_PLATE, 1), new ItemStack(Items.DIRT, 5));
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        MachineGameTests.work(h, p, bench);
        var lathe0 = HdfvMachines.MACHINES.get(Station.LATHE).get().asItem();
        // one gear short: the parts make smaller things (a tailstock, a drill head), not the lathe
        h.assertTrue(MachineBlockEntity.recipes(h.getLevel(), bench.recipeInput()).stream().noneMatch(r -> r.value().result().is(lathe0)), "One gear short: no lathe");
        bench.items().getItem(MachineBlockEntity.FIRST_INPUT + 3).grow(1);
        // with the gear the lathe takes the most parts: the bench builds it first
        h.assertTrue(MachineBlockEntity.recipes(h.getLevel(), bench.recipeInput()).get(0).value().result().is(lathe0), "The lathe comes first");
        ItemStack lathe = ItemStack.EMPTY;
        for (int i = 0; i < 100_000 && lathe.isEmpty(); i++) {
            tick(bench);
            for (int o = bench.firstOutput(); o < bench.firstOutput() + bench.outputs; o++) {
                if (bench.items().getItem(o).is(HdfvMachines.MACHINES.get(Station.LATHE).get().asItem())) { lathe = bench.items().getItem(o); }
            }
        }
        MachineGameTests.leave(p);
        h.assertTrue(!lathe.isEmpty(), "A lathe came off the bench");
        for (int i = MachineBlockEntity.FIRST_INPUT; i < MachineBlockEntity.FIRST_INPUT + 6; i++) { h.assertTrue(bench.items().getItem(i).isEmpty(), "Its parts used: " + i); }
        h.assertTrue(bench.items().getItem(MachineBlockEntity.FIRST_INPUT + 6).getCount() == 5, "The dirt left alone");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = BATCH)
    public static void aPressRollsAPlateAndStartsOnlyWhenItAndTheLeftoverFit(GameTestHelper h) {
        MachineBlockEntity press = MachineGameTests.machine(h, new BlockPos(2, 2, 2), Station.FORGING_PRESS, ComponentDesign.of(Component.STEEL_PLATE));
        put(press, Stock.of(Alloy.STEEL, 2));
        // both outputs taken but one: the plate and the leftover bits need two
        press.items().setItem(press.firstOutput(), new ItemStack(Items.DIRT));
        tick(press);
        h.assertTrue(press.state() == MachineBlockEntity.State.OUTPUT_FULL, "The leftover would not fit: " + press.state());
        press.items().setItem(press.firstOutput(), ItemStack.EMPTY);
        ItemStack plate = ItemStack.EMPTY;
        int bits = 0;
        for (int i = 0; i < 100_000 && plate.isEmpty(); i++) {
            tick(press);
            for (int o = press.firstOutput(); o < press.firstOutput() + press.outputs; o++) {
                ItemStack s = press.items().getItem(o);
                if (s.is(HdfvComponents.COMPONENTS.get(Component.STEEL_PLATE).get())) { plate = s; }
            }
        }
        for (int o = press.firstOutput(); o < press.firstOutput() + press.outputs; o++) {
            ItemStack s = press.items().getItem(o);
            if (s.is(HdfvMaterials.STOCK_BIT.get())) { bits += s.getCount(); }
        }
        // a 10 kg plate from 11.1 kg of stock (sheet yields 0.9): 12 bits of the 20 taken, 8 back
        h.assertTrue(!plate.isEmpty(), "A steel plate, not a workpiece");
        h.assertTrue(bits == 8, "The leftover came back: " + bits);
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = BATCH)
    public static void aLatheTurnsAShaftAndHandsBackItsChips(GameTestHelper h) {
        ComponentDesign shaft = ComponentDesign.of(Component.TURNED_SHAFT);
        MachineBlockEntity lathe = MachineGameTests.machine(h, new BlockPos(2, 2, 2), Station.LATHE, shaft);
        MachineGameTests.supply(lathe);
        PartPlan plan = Plans.of(shaft);
        Workpiece w = plan.workpieces().get(0);
        put(lathe, Workpieces.start(shaft, w, 1));
        FakePlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        MachineGameTests.work(h, p, lathe);
        ItemStack made = ItemStack.EMPTY;
        for (int i = 0; i < 200_000 && made.isEmpty(); i++) {
            tick(lathe);
            for (int o = lathe.firstOutput(); o < lathe.firstOutput() + lathe.outputs; o++) {
                if (lathe.items().getItem(o).is(HdfvComponents.COMPONENTS.get(Component.TURNED_SHAFT).get())) { made = lathe.items().getItem(o); }
            }
        }
        MachineGameTests.leave(p);
        int bits = 0;
        for (int o = lathe.firstOutput(); o < lathe.firstOutput() + lathe.outputs; o++) {
            ItemStack s = lathe.items().getItem(o);
            if (s.is(HdfvMaterials.STOCK_BIT.get()) && Stock.alloy(s) == Alloy.STEEL) { bits += s.getCount(); }
        }
        // 6 kg of bar to a 3 kg shaft: about 3 kg turned off, 0.8 of it back as two bits (the rest owed to the next)
        h.assertTrue(!made.isEmpty(), "A turned shaft came off the lathe");
        h.assertTrue(bits == 2, "Its chips back as steel bits: " + bits);
        h.succeed();
    }

    private static SmelterBlockEntity smelter(GameTestHelper h, BlockPos pos, boolean arc) {
        h.setBlock(pos.below(), dev.mcmod.hdfv.neoforge.power.HdfvPower.block(dev.mcmod.hdfv.neoforge.power.PowerBlock.Kind.CREATIVE_POWER));
        h.setBlock(pos, arc ? HdfvSmelting.ARC_FURNACE.get() : HdfvSmelting.ELECTROLYSIS_CELL.get());
        return (SmelterBlockEntity) h.getBlockEntity(pos);
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = BATCH, timeoutTicks = 1200)
    public static void theArcFurnaceAndTheCellWorkTheirBatchesOnPower(GameTestHelper h) {
        SmelterBlockEntity arc = smelter(h, new BlockPos(2, 2, 2), true), cell = smelter(h, new BlockPos(5, 2, 2), false);
        arc.items().setItem(0, new ItemStack(Items.IRON_INGOT, 8));
        arc.items().setItem(1, new ItemStack(HdfvMaterials.MATERIAL_ITEMS.get("coke").get(), 1));
        cell.items().setItem(0, new ItemStack(Items.GUNPOWDER, 2));
        cell.items().setItem(1, new ItemStack(Items.REDSTONE, 2));
        h.succeedWhen(() -> {
            ItemStack steel = arc.items().getItem(SmelterBlockEntity.RESULT), ap = cell.items().getItem(SmelterBlockEntity.RESULT);
            h.assertTrue(Stock.alloy(steel) == Alloy.STEEL && steel.getCount() == 8, "Eight steel stock: " + steel);
            h.assertTrue(ap.is(dev.mcmod.hdfv.neoforge.fuel.HdfvFuel.AMMONIUM_PERCHLORATE.get()) && ap.getCount() == 2, "Two of ammonium perchlorate: " + ap);
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = BATCH, timeoutTicks = 1200)
    public static void hoppersFeedTheAlloyFurnaceAndEmptyIt(GameTestHelper h) {
        BlockPos furnace = new BlockPos(3, 3, 3);
        h.setBlock(furnace, HdfvMaterials.ALLOY_FURNACE.get());
        h.setBlock(furnace.above(), Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.DOWN));
        h.setBlock(furnace.east(), Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.WEST));
        h.setBlock(furnace.below(), Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.DOWN));
        HopperBlockEntity top = (HopperBlockEntity) h.getBlockEntity(furnace.above()), side = (HopperBlockEntity) h.getBlockEntity(furnace.east());
        top.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        top.setItem(1, new ItemStack(HdfvMaterials.MATERIAL_ITEMS.get("coke").get(), 1));
        side.setItem(0, new ItemStack(Items.COAL, 4));
        HopperBlockEntity bottom = (HopperBlockEntity) h.getBlockEntity(furnace.below());
        h.succeedWhen(() -> {
            int steel = 0;
            for (int i = 0; i < bottom.getContainerSize(); i++) { if (Stock.alloy(bottom.getItem(i)) == Alloy.STEEL) { steel += bottom.getItem(i).getCount(); } }
            h.assertTrue(steel == 4, "Four steel stock in the hopper below: " + steel);
        });
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = BATCH)
    public static void createsRecipesLoadWithCreate(GameTestHelper h) {
        // the development runs have Create (-Phdfv_create=false leaves it out: then there is nothing to check)
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) { h.succeed(); return; }
        for (String id : java.util.List.of("create/crushing/bauxite_ore", "create/crushing/garnet_ore", "create/crushing/raw_garnet",
                "create/pressing/steel_plate", "create/mixing/bronze")) {
            h.assertTrue(h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id)).isPresent(),
                    id + " loads with Create");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool", batch = BATCH)
    public static void everyThingTheRecipesMakeIsOnTheTechTree(GameTestHelper h) {
        Map<String, TechTree.Stage> s = TechStages.of(h.getLevel().getRecipeManager(), h.getLevel().registryAccess());
        for (Component c : Component.values()) { h.assertTrue(s.containsKey("hdfv:" + c.id()), c + " is reachable"); }
        for (String id : java.util.List.of("hdfv:lathe", "hdfv:milling_machine", "hdfv:cnc_machining_centre", "hdfv:arc_furnace", "hdfv:aluminium_ingot",
                "hdfv:titanium_sponge", "hdfv:control_tower", "hdfv:wire_edm_head")) {
            h.assertTrue(s.containsKey(id), id + " is reachable");
        }
        h.assertTrue(s.get("hdfv:lathe").tier() == 1 && s.get("hdfv:milling_machine").tier() == 2, "The lathe at 1, the mill at 2: " + s.get("hdfv:lathe") + " " + s.get("hdfv:milling_machine"));
        h.assertTrue(s.get("hdfv:cnc_machining_centre").tier() > s.get("hdfv:grinder").tier(), "The CNC after the grinders");
        h.succeed();
    }
}
