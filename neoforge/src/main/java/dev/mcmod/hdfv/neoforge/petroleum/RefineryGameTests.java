package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.neoforge.Hdfv;
import dev.mcmod.hdfv.neoforge.fluid.HdfvFluidRegistry;
import dev.mcmod.hdfv.neoforge.multiblock.HdfvMultiblocks;
import dev.mcmod.hdfv.neoforge.power.HdfvPower;
import dev.mcmod.hdfv.neoforge.power.PowerBlock;
import dev.mcmod.hdfv.petroleum.Refining;
import dev.mcmod.hdfv.structure.Pattern;
import dev.mcmod.hdfv.structure.Structures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** The refinery in a running server (0.8): the column keeps the crude's mass, FCC makes gasoline, the polymer plant rubber and epoxy. Ticked directly. */
@PrefixGameTestTemplate(false)
public final class RefineryGameTests {
    private RefineryGameTests() {}

    static RefineryUnitBlockEntity unit(GameTestHelper h, BlockPos rel, Pattern p, Map<Character, String> choices) {
        BlockPos at = h.absolutePos(rel);
        HdfvMultiblocks.place(h.getLevel(), p, at, Direction.NORTH, 1, choices);
        h.getLevel().setBlock(at.below(), HdfvPower.block(PowerBlock.Kind.CREATIVE_POWER).defaultBlockState(), Block.UPDATE_ALL);
        RefineryUnitBlockEntity be = (RefineryUnitBlockEntity) h.getLevel().getBlockEntity(at);
        be.multiblock().check(h.getLevel());
        return be;
    }

    /** Everything a unit has made, kg: what is in its outlets and its output, and the fractions it still owes. */
    static double made(RefineryUnitBlockEntity be) {
        double kg = 0;
        for (UnitOutletBlockEntity o : be.outlets()) {
            FluidStack f = o.fluid();
            if (!f.isEmpty()) { kg += f.getAmount() * RefineryUnitBlockEntity.density(HdfvFluidRegistry.id(f.getFluid())) / 1000; }
        }
        for (int i = 0; i < be.output().getSlots(); i++) {
            ItemStack s = be.output().getStackInSlot(i);
            for (Map.Entry<String, Double> e : Refining.ITEM_KG.entrySet()) { if (!s.isEmpty() && s.is(HdfvRefinery.product(e.getKey()))) { kg += s.getCount() * e.getValue(); } }
        }
        return kg + be.owedKg();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theColumnSplitsCrudeAndKeepsItsMass(GameTestHelper h) {
        RefineryUnitBlockEntity col = unit(h, new BlockPos(4, 2, 4), Structures.DISTILLATION_COLUMN, Map.of());
        h.assertTrue(col.multiblock().formed() && col.multiblock().match().has(Structures.VACUUM_COLUMN), "The column with its vacuum section");
        int filled = col.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("crude_oil"), 2000), IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(filled == 2000, "Crude in: " + filled);
        for (int i = 0; i < 400 && col.feeds[0].getFluidAmount() > 0; i++) { col.tick(); }
        double crude = (2000 - col.feeds[0].getFluidAmount()) * RefineryUnitBlockEntity.density("crude_oil") / 1000, made = made(col);
        double share = col.burntKg() / col.feedKg();
        h.assertTrue(crude > 1500 && Math.abs(col.feedKg() - crude) <= 0.01 * crude, "Ran " + col.feedKg() + " kg of " + crude);
        h.assertTrue(Math.abs(made + col.burntKg() - crude) <= 0.01 * crude, "Mass kept: made " + made + " + burnt " + col.burntKg() + " of " + crude);
        h.assertTrue(share >= 0.01 && share <= 0.03, "The heater burns 1-2 % of the crude: " + share);
        for (UnitOutletBlockEntity o : col.outlets()) {
            if (o == col.outlets().get(0)) { continue; }   // the residue leaves as bitumen with the vacuum section
            h.assertFalse(o.fluid().isEmpty(), "Every draw-off has its product");
        }
        h.assertTrue(col.output().getStackInSlot(0).is(HdfvRefinery.BITUMEN.get()), "Bitumen from the vacuum section");
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void fccMakesGasolineAndThePolymerPlantRubberAndEpoxy(GameTestHelper h) {
        RefineryUnitBlockEntity fcc = unit(h, new BlockPos(2, 2, 2), Structures.PROCESS, Map.of('R', Structures.FCC));
        fcc.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("gas_oil"), 1000), IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < 200 && fcc.feeds[0].getFluidAmount() > 0; i++) { fcc.tick(); }
        double gasoline = 0, share = Refining.FCC.products().get("gasoline");
        for (UnitOutletBlockEntity o : fcc.outlets()) {
            if (!o.fluid().isEmpty() && HdfvFluidRegistry.id(o.fluid().getFluid()).equals("gasoline")) { gasoline += o.fluid().getAmount() * RefineryUnitBlockEntity.density("gasoline") / 1000; }
        }
        h.assertTrue(fcc.feedKg() > 800, "The gas oil ran: " + fcc.feedKg());
        h.assertTrue(Math.abs(gasoline - share * fcc.feedKg()) <= 0.01 * fcc.feedKg(), "FCC gasoline: " + gasoline + " of " + fcc.feedKg());
        h.assertTrue(share > Refining.distillation("crude_oil", true).products().get("naphtha"), "FCC makes more gasoline than the crude's naphtha: " + share);
        h.assertTrue(Math.abs(made(fcc) + fcc.burntKg() - fcc.feedKg()) <= 0.01 * fcc.feedKg(), "Mass kept: " + made(fcc) + " + " + fcc.burntKg() + " of " + fcc.feedKg());
        // the polymer plant: SBR rubber, then epoxy
        RefineryUnitBlockEntity poly = unit(h, new BlockPos(10, 2, 2), Structures.PROCESS, Map.of('R', Structures.POLYMER));
        poly.setPolymer(Refining.POLYMERS.indexOf(Refining.RUBBER));
        poly.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("butadiene"), 300), IFluidHandler.FluidAction.EXECUTE);
        poly.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("aromatics"), 300), IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < 400; i++) { poly.tick(); }
        h.assertTrue(count(poly, "synthetic_rubber") > 0, "Synthetic rubber: " + count(poly, "synthetic_rubber"));
        poly.setPolymer(Refining.POLYMERS.indexOf(Refining.EPOXY));
        for (var t : poly.feeds) { t.setFluid(FluidStack.EMPTY); }
        poly.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("aromatics"), 300), IFluidHandler.FluidAction.EXECUTE);
        poly.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("propylene"), 300), IFluidHandler.FluidAction.EXECUTE);
        for (int i = 0; i < 400; i++) { poly.tick(); }
        h.assertTrue(count(poly, "epoxy_resin") > 0, "Epoxy resin: " + count(poly, "epoxy_resin"));
        h.succeed();
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theReformersHydrogenIsBurntUnlessRoutedToAnOutlet(GameTestHelper h) {
        RefineryUnitBlockEntity flared = unit(h, new BlockPos(2, 2, 2), Structures.PROCESS, Map.of('R', Structures.REFORMER));
        RefineryUnitBlockEntity routed = unit(h, new BlockPos(10, 2, 2), Structures.PROCESS, Map.of('R', Structures.REFORMER));
        int hydrogen = routed.routable().indexOf(dev.mcmod.hdfv.fluid.HdfvFluids.HYDROGEN);
        h.assertTrue(hydrogen >= 0 && routed.set("route.5", hydrogen) == null, "Hydrogen routed to the last of its six outlets");
        for (RefineryUnitBlockEntity u : List.of(flared, routed)) {
            u.feedHandler().fill(new FluidStack(HdfvFluidRegistry.fluid("naphtha"), 1000), IFluidHandler.FluidAction.EXECUTE);
            for (int i = 0; i < 200 && u.feeds[0].getFluidAmount() > 0; i++) { u.tick(); }
        }
        double feed = flared.feedKg(), share = flared.burntKg() / feed;
        h.assertTrue(feed > 500 && Math.abs(share - (Refining.REFORMER.burnt() + 0.025)) < 0.002, "Unrouted, the hydrogen burns with the fuel gas: " + share);
        double inOutlet = 0;
        for (UnitOutletBlockEntity o : routed.outlets()) {
            if (!o.fluid().isEmpty() && HdfvFluidRegistry.id(o.fluid().getFluid()).equals(dev.mcmod.hdfv.fluid.HdfvFluids.HYDROGEN)) { inOutlet += o.fluid().getAmount(); }
        }
        double kg = inOutlet * dev.mcmod.hdfv.fluid.HdfvFluids.of(dev.mcmod.hdfv.fluid.HdfvFluids.HYDROGEN).densityKgPerM3() / 1000;
        h.assertTrue(Math.abs(kg - 0.025 * routed.feedKg()) <= 0.002 * routed.feedKg() + 0.05, "Routed, 2.5 % of the naphtha as 700 bar hydrogen: " + kg + " of " + routed.feedKg());
        h.assertTrue(Math.abs(routed.burntKg() / routed.feedKg() - Refining.REFORMER.burnt()) < 0.002, "and less burnt");
        h.succeed();
    }

    static int count(RefineryUnitBlockEntity be, String product) {
        int n = 0;
        for (int i = 0; i < be.output().getSlots(); i++) { if (be.output().getStackInSlot(i).is(HdfvRefinery.product(product))) { n += be.output().getStackInSlot(i).getCount(); } }
        return n;
    }

    @GameTest(templateNamespace = Hdfv.MOD_ID, template = "hull_pool")
    public static void theRefinerysBlocksAreMadeDroppedAndNamed(GameTestHelper h) throws java.io.IOException {
        var recipes = h.getLevel().getRecipeManager().getRecipes().stream().filter(r -> r.id().getNamespace().equals(Hdfv.MOD_ID)).map(r -> r.id().getPath()).toList();
        BlockPos p = new BlockPos(6, 2, 6);
        for (String id : HdfvRefinery.ids()) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Hdfv.MOD_ID, id));
            h.assertTrue(recipes.contains(id), "A recipe for the " + id);
            h.setBlock(p, block);
            var drops = Block.getDrops(h.getBlockState(p), h.getLevel(), h.absolutePos(p), h.getLevel().getBlockEntity(h.absolutePos(p)));
            h.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), id + " drops itself: " + drops);
        }
        for (String file : List.of("en_us", "zh_tw")) {
            try (var in = Hdfv.class.getResourceAsStream("/assets/hdfv/lang/" + file + ".json")) {
                var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String id : HdfvRefinery.ids()) { h.assertTrue(lang.has("block.hdfv." + id), file + " lacks " + id); }
                for (String i : List.of("bitumen", "polyethylene", "polypropylene")) { h.assertTrue(lang.has("item.hdfv." + i), file + " lacks " + i); }
                for (var s : RefineryUnitBlockEntity.State.values()) { h.assertTrue(lang.has("hdfv.refinery.state." + s.name().toLowerCase(java.util.Locale.ROOT)), file + " " + s); }
                for (Refining.Yield y : Refining.all()) { h.assertTrue(lang.has("hdfv.refinery.unit." + y.name()), file + " lacks unit " + y.name()); }
                h.assertTrue(lang.has("hdfv.refinery.status"), file + " lacks the status");
            }
        }
        h.succeed();
    }
}
